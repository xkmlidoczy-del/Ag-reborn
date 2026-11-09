package cn.academy.port.highpower.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Bytecode-only admission; never loads a Minecraft or Academy gameplay class during transformation. */
public final class NativeCooldownMixinPlugin implements IMixinConfigPlugin {
    private static final String ENTITY = "net/minecraft/world/entity/LivingEntity";
    private static final String PLAYER = "net/minecraft/world/entity/player/Player";
    private static final String CONTAINER = "net/neoforged/neoforge/common/damagesource/DamageContainer";
    private static final String REDUCTION = CONTAINER + "$Reduction";
    private static final String HOOKS = "net/neoforged/neoforge/common/CommonHooks";
    private static final String DAMAGE = "Lnet/minecraft/world/damagesource/DamageSource;F";

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!(targetClassName.equals(ENTITY.replace('/', '.')) && mixinClassName.endsWith(".LivingEntityCooldownMixin")
                || targetClassName.equals(PLAYER.replace('/', '.')) && mixinClassName.endsWith(".PlayerDamageCommitMixin"))) return false;
        try {
            var bytecode = MixinService.getService().getBytecodeProvider();
            ClassNode entity = bytecode.getClassNode(ENTITY.replace('/', '.'));
            ClassNode player = bytecode.getClassNode(PLAYER.replace('/', '.'));
            boolean supported = supportedEntity(entity) && containerDrivenDamage(player);
            if (!supported) System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                    "Native cooldown/damage-commit bytecode differs from the reviewed 1.21.1/21.1.252 seams; high-power adapter stays unavailable");
            return supported;
        } catch (Exception unavailable) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                    "Cannot verify the native cooldown seam; high-power adapter stays unavailable: " + unavailable.toString());
            return false;
        }
    }

    /** Validates the exact first lastHurt read used by finite-float <= comparison, not a durability or residual read. */
    public static boolean supportedEntity(ClassNode entity) {
        MethodNode hurt = method(entity, "hurt", "(" + DAMAGE + ")Z");
        if (hurt == null || !containerDrivenDamage(entity)) return false;
        int reads = 0;
        for (AbstractInsnNode instruction : hurt.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                    && field.owner.equals(ENTITY) && field.name.equals("lastHurt") && field.desc.equals("F")) {
                reads++;
                if (reads == 1) {
                    AbstractInsnNode comparison = next(field), branch = next(comparison);
                    AbstractInsnNode owner = previous(field), input = previous(owner);
                    if (!(owner instanceof VarInsnNode self) || self.getOpcode() != Opcodes.ALOAD || self.var != 0
                            || !(input instanceof VarInsnNode amount) || amount.getOpcode() != Opcodes.FLOAD || amount.var != 2
                            || comparison == null || comparison.getOpcode() != Opcodes.FCMPG
                            || !(branch instanceof JumpInsnNode) || branch.getOpcode() != Opcodes.IFGT) return false;
                }
            }
        }
        return reads == 3;
    }

    // Pin both container-driven arithmetic and the exact second setReduction invocation.
    // An ordinal alone cannot certify that every absorption modifier precedes the injected veto.
    public static boolean containerDrivenDamage(ClassNode type) {
        MethodNode method = method(type, "actuallyHurt", "(" + DAMAGE + ")V");
        if (method == null) return false;
        MethodInsnNode pre = null, post = null;
        var reductions = new java.util.ArrayList<MethodInsnNode>();
        int absorptionWrites = 0, healthWrites = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.FLOAD && variable.var == 2) return false;
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.owner.equals(HOOKS) && call.name.equals("onLivingDamagePre")) {
                if (pre != null || !invocation(call, Opcodes.INVOKESTATIC, HOOKS, "onLivingDamagePre", "(L" + ENTITY + ";L" + CONTAINER + ";)F")) return false;
                pre = call;
            }
            if (call.owner.equals(HOOKS) && call.name.equals("onLivingDamagePost")) {
                if (post != null || !invocation(call, Opcodes.INVOKESTATIC, HOOKS, "onLivingDamagePost", "(L" + ENTITY + ";L" + CONTAINER + ";)V")) return false;
                post = call;
            }
            if (call.owner.equals(CONTAINER) && call.name.equals("setReduction")) {
                if (!invocation(call, Opcodes.INVOKEVIRTUAL, CONTAINER, "setReduction", "(L" + REDUCTION + ";F)V")) return false;
                reductions.add(call);
            }
            if (call.name.equals("setAbsorptionAmount") || call.name.equals("setHealth")) {
                if (call.getOpcode() != Opcodes.INVOKEVIRTUAL || !nativeOwner(call.owner, type.name)
                        || !call.desc.equals("(F)V") || reductions.size() != 2 || post != null) return false;
                if (call.name.equals("setAbsorptionAmount")) absorptionWrites++; else healthWrites++;
            }
        }
        if (pre == null || post == null || reductions.size() != 2 || absorptionWrites != 1 || healthWrites != 1) return false;
        MethodInsnNode armor = reductions.get(0), absorption = reductions.get(1);
        if (!(method.instructions.indexOf(armor) < method.instructions.indexOf(pre)
                && method.instructions.indexOf(pre) < method.instructions.indexOf(absorption)
                && method.instructions.indexOf(absorption) < method.instructions.indexOf(post))) return false;
        int armorEnums = 0;
        for (AbstractInsnNode node = method.instructions.getFirst(); node != armor; node = node.getNext()) {
            if (node instanceof FieldInsnNode field && field.owner.equals(REDUCTION)) {
                if (!enumRead(field, "ARMOR")) return false;
                armorEnums++;
            }
        }
        return armorEnums == 1 && absorptionArguments(type, pre, absorption);
    }

    /** Exact Pre-return local, receiver, ABSORPTION and Math.min(getAbsorptionAmount(), damage). */
    private static boolean absorptionArguments(ClassNode type, MethodInsnNode pre, MethodInsnNode absorption) {
        AbstractInsnNode minimum = previous(absorption), damage = previous(minimum), pool = previous(damage);
        AbstractInsnNode self = previous(pool), kind = previous(self), cast = previous(kind);
        AbstractInsnNode peek = previous(cast), stack = previous(peek), receiver = previous(stack);
        AbstractInsnNode stored = next(pre);
        return invocation(minimum, Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(FF)F")
                && stored instanceof VarInsnNode store && store.getOpcode() == Opcodes.FSTORE && store.var > 2
                && damage instanceof VarInsnNode load && load.getOpcode() == Opcodes.FLOAD && load.var == store.var
                && pool instanceof MethodInsnNode getter && getter.getOpcode() == Opcodes.INVOKEVIRTUAL
                && nativeOwner(getter.owner, type.name) && getter.name.equals("getAbsorptionAmount") && getter.desc.equals("()F")
                && self instanceof VarInsnNode owner && owner.getOpcode() == Opcodes.ALOAD && owner.var == 0
                && enumRead(kind, "ABSORPTION")
                && cast instanceof TypeInsnNode typeCast && typeCast.getOpcode() == Opcodes.CHECKCAST && typeCast.desc.equals(CONTAINER)
                && invocation(peek, Opcodes.INVOKEVIRTUAL, "java/util/Stack", "peek", "()Ljava/lang/Object;")
                && stack instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                && nativeOwner(field.owner, type.name) && field.name.equals("damageContainers") && field.desc.equals("Ljava/util/Stack;")
                && receiver instanceof VarInsnNode object && object.getOpcode() == Opcodes.ALOAD && object.var == 0
                && next(stored) == receiver;
    }
    private static boolean enumRead(AbstractInsnNode node, String name) {
        return node instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                && field.owner.equals(REDUCTION) && field.name.equals(name) && field.desc.equals("L" + REDUCTION + ";");
    }
    private static boolean nativeOwner(String owner, String type) { return owner.equals(ENTITY) || owner.equals(type); }
    private static boolean invocation(AbstractInsnNode node, int opcode, String owner, String name, String descriptor) {
        return node instanceof MethodInsnNode call && call.getOpcode() == opcode && call.owner.equals(owner)
                && call.name.equals(name) && call.desc.equals(descriptor);
    }

    private static MethodNode method(ClassNode type, String name, String descriptor) {
        return type.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElse(null);
    }
    private static AbstractInsnNode next(AbstractInsnNode node) {
        if (node == null) return null;
        do { node = node.getNext(); } while (node != null && node.getOpcode() < 0);
        return node;
    }
    private static AbstractInsnNode previous(AbstractInsnNode node) {
        if (node == null) return null;
        do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0);
        return node;
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
}
