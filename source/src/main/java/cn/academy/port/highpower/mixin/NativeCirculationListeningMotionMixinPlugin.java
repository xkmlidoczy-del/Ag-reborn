package cn.academy.port.highpower.mixin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

/** Optional NEW433 observer. Changed/missing hooks disable sensing, never native movement. */
public final class NativeCirculationListeningMotionMixinPlugin implements IMixinConfigPlugin {
    private static final String ENTITY = "net.minecraft.world.entity.Entity", ARROW = "net.minecraft.world.entity.projectile.AbstractArrow";
    private static final String BRIDGE = "cn/academy/port/highpower/CirculationListeningMotionProof";
    private static final String ACCESS = "cn/academy/port/highpower/NativeCirculationListeningMotionAccess";
    private static final String STATE = "L" + BRIDGE + "$State;";
    private static final String MOVE = "(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V";
    private static final String OP = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
    private static final String CI = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
    private static final String E = "Lnet/minecraft/world/entity/Entity;", A = "Lnet/minecraft/world/entity/projectile/AbstractArrow;";
    private static final Map<String, String> MIXINS = Map.of(ENTITY, "cn.academy.port.highpower.mixin.EntityCirculationListeningMotionMixin",
            ARROW, "cn.academy.port.highpower.mixin.ArrowCirculationListeningMotionMixin");
    private static final Map<String, String> REVIEWED = Map.of(
            ENTITY + "#move" + MOVE, "6534944796ba8604a0fe826ac28bcc3eb2392d2f0ba4b9be0d7e99ea7c3aeac4",
            ENTITY + "#setPos(DDD)V", "2aa444aabd0da6c926fa528591be5d8ef19e15242a5cf2926140b17f2b6174c8",
            ENTITY + "#setPosRaw(DDD)V", "6f5d7932820edff438a6f35c7b83e35fd85d983eaad5d494b5eebbf8f0705b7e",
            ARROW + "#tick()V", "f24175a2fcf9005585ea3bddc4eccbde089c1d3e5f25d1fd3fe15831202a30f9");
    private static volatile boolean enabled, failed;
    private boolean checked, admitted, witnessRegistered;
    private final Set<String> sites = new HashSet<>(), merged = new HashSet<>(), installed = new HashSet<>();
    public static boolean available() { return enabled && !failed; }
    public static void disable() { failed = true; enabled = false; }
    public static Set<String> reviewedMethods() { return REVIEWED.keySet(); }
    public static boolean supported(Map<String, ClassNode> classes) {
        if (classes == null) return false;
        for (var entry : REVIEWED.entrySet()) {
            int hash = entry.getKey().indexOf('#'), descriptor = entry.getKey().indexOf('(', hash);
            ClassNode node = classes.get(entry.getKey().substring(0, hash));
            String name = entry.getKey().substring(hash + 1, descriptor), desc = entry.getKey().substring(descriptor);
            if (node == null || node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).count() != 1) return false;
            MethodNode method = method(node, name, desc);
            if (method.access != (Opcodes.ACC_PUBLIC | (name.equals("setPosRaw") ? Opcodes.ACC_FINAL : 0))
                    || !entry.getValue().equals(NativeScatterPlateMixinPlugin.fingerprint(node, name, desc))) return false;
        }
        ClassNode entity = classes.get(ENTITY);
        return entity.fields.stream().filter(f -> f.name.equals("position") && f.desc.equals("Lnet/minecraft/world/phys/Vec3;")
                && f.access == Opcodes.ACC_PRIVATE).count() == 1 && nativeSitesSupported(entity) && nativeSitesSupported(classes.get(ARROW));
    }
    public static boolean nativeSitesSupported(ClassNode node) {
        if (node == null) return false;
        if (node.name.equals(ENTITY.replace('.', '/'))) return count(method(node, "move", MOVE), node.name, "setPos", "(DDD)V") == 2
                && count(method(node, "setPos", "(DDD)V"), node.name, "setPosRaw", "(DDD)V") == 1;
        return node.name.equals(ARROW.replace('.', '/')) && count(method(node, "tick", "()V"), node.name, "setPos", "(DDD)V") == 1;
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!mixin.equals(MIXINS.get(target)) || !witnessRegistered) return false;
        if (!checked) {
            checked = true;
            try {
                Map<String, ClassNode> classes = new HashMap<>();
                for (String key : REVIEWED.keySet()) {
                    String owner = key.substring(0, key.indexOf('#'));
                    if (!classes.containsKey(owner)) classes.put(owner, MixinService.getService().getBytecodeProvider().getClassNode(owner));
                }
                admitted = supported(classes);
            } catch (Exception | LinkageError failure) { admitted = false; }
            if (!admitted) warn("NEW433 motion observer differs from reviewed 1.21.1/21.1.252; sensing stays unavailable");
        }
        return admitted;
    }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        enabled = false; installed.remove(target); merged.remove(target);
        if (admitted && mixin.equals(MIXINS.get(target)) && nativeSitesSupported(node)) sites.add(target); else sites.remove(target);
    }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (sites.contains(target) && mixin.equals(MIXINS.get(target)) && mergedSupported(node)) merged.add(target); else merged.remove(target);
        enabled = false; // WrapOperation is installed after plugin postApply.
    }
    // suffix, callback descriptor, bridge method, bridge descriptor, native host descriptor
    private static List<String[]> hooks(String owner) {
        if (owner.equals(ENTITY.replace('.', '/'))) return List.of(
                new String[]{"academy$listeningRaw", "(DDD" + CI + ")V", "beforeRaw", "(" + E + "DDD)V", "(DDD)V"},
                new String[]{"academy$listeningPhysical", "(" + E + "DDD" + OP + ")V", "physicalSet", "(" + E + "DDD" + OP + ")V", MOVE});
        if (owner.equals(ARROW.replace('.', '/'))) return java.util.Collections.singletonList(
                new String[]{"academy$listeningArrow", "(" + A + "DDD" + OP + ")V", "physicalSet", "(" + E + "DDD" + OP + ")V", "()V"});
        return List.of();
    }
    private static MethodNode callback(ClassNode node, String[] hook) {
        var methods = node.methods.stream().filter(m -> m.name.endsWith(hook[0])).toList();
        if (methods.size() != 1) return null;
        MethodNode callback = methods.getFirst();
        return callback.desc.equals(hook[1]) && callback.access == Opcodes.ACC_PRIVATE
                && count(callback, BRIDGE, hook[2], hook[3]) == 1 ? callback : null;
    }
    private static boolean mergedSupported(ClassNode node) {
        if (node == null || hooks(node.name).isEmpty()) return false;
        for (String[] hook : hooks(node.name)) if (callback(node, hook) == null) return false;
        if (node.name.equals(ENTITY.replace('.', '/'))) {
            MethodNode get = method(node, "academy$circulationListeningMotionState", "()" + STATE);
            MethodNode set = method(node, "academy$circulationListeningMotionState", "(" + STATE + ")V");
            if (!node.interfaces.contains(ACCESS) || get == null || set == null || get.access != Opcodes.ACC_PUBLIC || set.access != Opcodes.ACC_PUBLIC) return false;
            var fields = node.fields.stream().filter(f -> f.desc.equals(STATE) && f.access == Opcodes.ACC_PRIVATE).toList();
            if (fields.size() != 1) return false;
            return fieldCount(get, Opcodes.GETFIELD, node.name, fields.getFirst().name, STATE) == 1
                    && fieldCount(set, Opcodes.PUTFIELD, node.name, fields.getFirst().name, STATE) == 1;
        }
        return true;
    }
    public static boolean installedCallbacksSupported(ClassNode node) {
        if (!mergedSupported(node)) return false;
        for (String[] hook : hooks(node.name)) {
            MethodNode callback = callback(node, hook);
            long calls = node.methods.stream().filter(m -> m.desc.equals(hook[4]))
                    .mapToLong(m -> count(m, node.name, callback.name, callback.desc)).sum();
            if (calls != 1) return false;
        }
        if (node.name.equals(ENTITY.replace('.', '/'))) {
            MethodNode move = method(node, "move", MOVE), raw = method(node, "setPosRaw", "(DDD)V");
            MethodNode physical = callback(node, hooks(node.name).get(1)), observer = callback(node, hooks(node.name).get(0));
            if (move == null || raw == null || count(move, node.name, "setPos", "(DDD)V") != 1
                    || index(move, node.name, "setPos", "(DDD)V") >= index(move, node.name, physical.name, physical.desc)) return false;
            int at = index(raw, node.name, observer.name, observer.desc), firstPositionRead = Integer.MAX_VALUE;
            for (int i = 0; i < raw.instructions.size(); i++) if (raw.instructions.get(i) instanceof FieldInsnNode field
                    && field.owner.equals(node.name) && field.name.equals("position")) { firstPositionRead = i; break; }
            return at >= 0 && at < firstPositionRead;
        }
        return node.methods.stream().filter(m -> m.desc.equals("()V")).mapToLong(m -> count(m, node.name, "setPos", "(DDD)V")).sum() == 0;
    }
    private static int fieldCount(MethodNode method, int opcode, String owner, String name, String desc) {
        int n = 0; for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof FieldInsnNode field
                && field.getOpcode() == opcode && field.owner.equals(owner) && field.name.equals(name) && field.desc.equals(desc)) n++; return n;
    }
    private static int index(MethodNode method, String owner, String name, String desc) {
        if (method == null) return -1;
        for (int i = 0; i < method.instructions.size(); i++) if (method.instructions.get(i) instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) return i; return -1;
    }
    private static int count(MethodNode method, String owner, String name, String desc) {
        if (method == null) return 0; int n = 0;
        for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) n++; return n;
    }
    private static MethodNode method(ClassNode node, String name, String desc) {
        return node == null ? null : node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElse(null);
    }
    @Override public void onLoad(String mixinPackage) {
        enabled = failed = false;
        try {
            Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
                extensions.add(new FinalWitness()); witnessRegistered = true;
            }
        } catch (RuntimeException | LinkageError failure) { warn("Cannot register NEW433 final motion witness"); }
    }
    private final class FinalWitness implements IExtension {
        public boolean checkActive(MixinEnvironment environment) { return true; }
        public void preApply(ITargetClassContext context) {}
        public void postApply(ITargetClassContext context) {}
        public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
            String target = name.replace('/', '.'); if (!MIXINS.containsKey(target)) return;
            if (admitted && sites.contains(target) && merged.contains(target) && installedCallbacksSupported(node)) installed.add(target);
            else { installed.remove(target); warn("NEW433 final position observer missing for " + target); }
            enabled = !failed && installed.containsAll(MIXINS.keySet());
        }
    }
    private static void warn(String message) { System.getLogger(NativeCirculationListeningMotionMixinPlugin.class.getName()).log(System.Logger.Level.WARNING, message); }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
}
