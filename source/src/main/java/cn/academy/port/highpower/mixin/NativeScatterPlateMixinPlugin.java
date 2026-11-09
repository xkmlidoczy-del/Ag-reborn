package cn.academy.port.highpower.mixin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Exact symbolic native-method fingerprints, before a gameplay class is loaded. */
public final class NativeScatterPlateMixinPlugin implements IMixinConfigPlugin {
    private static volatile boolean multiAxisArrowApplied, multiAxisOwnerApplied;
    private static volatile boolean autonomousVectorCirclePadding;
    private static volatile boolean windPressureLiftTeleportApplied;
    private static final String LIFT_MARKER = "cn/academy/port/highpower/NativeWindPressureLiftTeleportAccess";
    private static final String TELEPORT = "(DDDFFLjava/util/Set;)V";
    private static final String CALLBACK = "(DDDFFLjava/util/Set;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    public static boolean windPressureLiftTeleportAvailable() { return windPressureLiftTeleportApplied; }
    public static boolean multiAxisReflectionAvailable() { return multiAxisArrowApplied && multiAxisOwnerApplied; }
    /** NEW452 alone also certifies the overload which supplies native 0.3F body padding. */
    public static boolean autonomousVectorCircleAvailable() { return multiAxisReflectionAvailable() && autonomousVectorCirclePadding; }
    private static final String PROJECTILE_UTIL = "net.minecraft.world.entity.projectile.ProjectileUtil";
    private static final String DEFAULT_BODY_PADDING = "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/EntityHitResult;";
    private static final String REVIEWED_BODY_PADDING = "a2326224cc715b6a9ba63c187b1991725ddb5e153a70bedb07af6e08742951e5";
    private static final String ARROW = "net.minecraft.world.entity.projectile.AbstractArrow";
    private static final String PLAIN_ARROW = "net.minecraft.world.entity.projectile.Arrow";
    private static final String PROJECTILE = "net.minecraft.world.entity.projectile.Projectile";
    private static final String CONNECTION = "net.minecraft.server.network.ServerGamePacketListenerImpl";
    private static final Map<String, String> REVIEWED = Map.ofEntries(
            Map.entry(PLAIN_ARROW + "#tick()V", "fad3f47795922581c2c6927233394ee7add009889ff2d4c4340df726af7bb7e5"),
            Map.entry(PLAIN_ARROW + "#doPostHurtEffects(Lnet/minecraft/world/entity/LivingEntity;)V", "7592664032324b74c5c606797349a9143ffb0ba29d9fba9e852d44192b407e88"),
            Map.entry(ARROW + "#onHitBlock(Lnet/minecraft/world/phys/BlockHitResult;)V", "b37a478fd2f3b306d0580b2a17984bdc456a40b3069359196e78f1ecdaf54169"),
            Map.entry(PROJECTILE + "#tick()V", "162bb3e4528cebbb80e1686d0c2d3442d061be5ec7956d7b0ff3ae822ab89003"),
            Map.entry(PROJECTILE + "#getOwner()Lnet/minecraft/world/entity/Entity;", "8b313c35407fa7958351c65e2c4a7213f9423ccbff1423fa99a1dcad667193e2"),
            Map.entry(ARROW + "#tick()V", "f24175a2fcf9005585ea3bddc4eccbde089c1d3e5f25d1fd3fe15831202a30f9"),
            Map.entry(ARROW + "#findHitEntity(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/EntityHitResult;", "747d72886ae38d69b0747367481dc98f209f84acf0a00b072cef5d0fee5191c0"),
            Map.entry(ARROW + "#onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V", "c0f373b9f0cf13de2e9874ce0ab643908276677d306b937eac0dd6c3af2b4ee9"),
            Map.entry(PROJECTILE + "#setOwner(Lnet/minecraft/world/entity/Entity;)V", "7ef77416132d54968bee89eeb64b3eb7cbb085784b10cf2c0d93220ff8531f24"),
            Map.entry(PROJECTILE + "#hitTargetOrDeflectSelf(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/entity/projectile/ProjectileDeflection;", "ed7e6c767d5e933c7f21861d3d71b4a30cfeb542a32c06ab94d6e39381234fd6"),
            Map.entry(PROJECTILE + "#deflect(Lnet/minecraft/world/entity/projectile/ProjectileDeflection;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Z)Z", "6e868cf6a9433e5d336993a02b0b6e9662aab7222360f504310334f972170197"),
            Map.entry("net.minecraft.world.entity.projectile.ProjectileUtil#getEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;F)Lnet/minecraft/world/phys/EntityHitResult;", "6bdeccfb0ff5b9a9d71966c5ad204ca40359cc79b2007ba11181faf45e26a3db"),
            Map.entry(CONNECTION + "#teleport(DDDFFLjava/util/Set;)V", "f5eed4f721a01dba7e8c490b616a25c2e56609de153c1d755c34516f0b09512f"));
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!Set.of(ARROW, PROJECTILE, CONNECTION).contains(targetClassName)) return false;
        autonomousVectorCirclePadding = false;
        try {
            Map<String, ClassNode> classes = new HashMap<>();
            for (String key : REVIEWED.keySet()) {
                String type = key.substring(0, key.indexOf('#'));
                if (!classes.containsKey(type)) classes.put(type, MixinService.getService().getBytecodeProvider().getClassNode(type));
            }
            boolean admitted = supported(classes);
            autonomousVectorCirclePadding = admitted && autonomousVectorCirclePaddingSupported(classes.get(PROJECTILE_UTIL));
            if (!admitted) System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING, "Native arrow path differs from reviewed 1.21.1/21.1.252; NEW212/NEW331 arrow evidence stays unavailable");
            return admitted;
        } catch (Exception unavailable) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING, "Cannot verify NEW212/NEW331 native arrow seam: " + unavailable);
            return false;
        }
    }
    public static boolean supported(Map<String, ClassNode> classes) {
        for (var entry : REVIEWED.entrySet()) {
            int split = entry.getKey().indexOf('#'), descriptor = entry.getKey().indexOf('(', split);
            ClassNode type = classes.get(entry.getKey().substring(0, split));
            if (type == null || !entry.getValue().equals(fingerprint(type, entry.getKey().substring(split + 1, descriptor), entry.getKey().substring(descriptor)))) return false;
        }
        ClassNode arrow = classes.get(ARROW), projectile = classes.get(PROJECTILE);
        return field(arrow, "life", "I") && field(arrow, "inGroundTime", "I")
                && field(arrow, "shakeTime", "I") && field(arrow, "pickup", "Lnet/minecraft/world/entity/projectile/AbstractArrow$Pickup;")
                && field(projectile, "ownerUUID", "Ljava/util/UUID;") && field(projectile, "cachedOwner", "Lnet/minecraft/world/entity/Entity;") && field(projectile, "leftOwner", "Z");
    }
    /** Independent read-only proof; the retained REVIEWED map and prior eligibility stay unchanged. */
    public static boolean autonomousVectorCirclePaddingSupported(ClassNode type) {
        return type != null && PROJECTILE_UTIL.replace('.', '/').equals(type.name)
                && REVIEWED_BODY_PADDING.equals(fingerprint(type, "getEntityHitResult", DEFAULT_BODY_PADDING));
    }
    public static Set<String> reviewedMethods() { return REVIEWED.keySet(); }
    private static boolean field(ClassNode type, String name, String descriptor) { return type.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(descriptor)); }
    public static String fingerprint(ClassNode type, String name, String descriptor) {
        MethodNode method = type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst().orElse(null);
        if (method == null || !method.tryCatchBlocks.isEmpty()) return "unreviewed";
        Map<AbstractInsnNode, Integer> indices = new IdentityHashMap<>(); int index = 0;
        for (AbstractInsnNode node : method.instructions) { indices.put(node, index); if (node.getOpcode() >= 0) index++; }
        StringBuilder symbolic = new StringBuilder(method.desc).append('|');
        for (AbstractInsnNode node : method.instructions) {
            if (node.getOpcode() < 0) continue;
            symbolic.append(node.getOpcode()).append(':');
            if (node instanceof VarInsnNode n) symbolic.append(n.var);
            else if (node instanceof IntInsnNode n) symbolic.append(n.operand);
            else if (node instanceof TypeInsnNode n) symbolic.append(n.desc);
            else if (node instanceof FieldInsnNode n) symbolic.append(n.owner).append('.').append(n.name).append(n.desc);
            else if (node instanceof MethodInsnNode n) symbolic.append(n.owner).append('.').append(n.name).append(n.desc).append(n.itf);
            else if (node instanceof JumpInsnNode n) symbolic.append(indices.get(n.label));
            else if (node instanceof LdcInsnNode n) symbolic.append(n.cst.getClass().getName()).append('=').append(n.cst);
            else if (node instanceof IincInsnNode n) symbolic.append(n.var).append(',').append(n.incr);
            else if (node instanceof InvokeDynamicInsnNode n) symbolic.append(n.name).append(n.desc).append(n.bsm).append(Arrays.toString(n.bsmArgs));
            else if (node instanceof TableSwitchInsnNode n) { symbolic.append(n.min).append(',').append(n.max).append(',').append(indices.get(n.dflt)); for (var label : n.labels) symbolic.append(',').append(indices.get(label)); }
            else if (node instanceof LookupSwitchInsnNode n) { symbolic.append(n.keys).append(',').append(indices.get(n.dflt)); for (var label : n.labels) symbolic.append(',').append(indices.get(label)); }
            else if (!(node instanceof InsnNode)) return "unreviewed";
            symbolic.append(';');
        }
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(symbolic.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode target, String mixin, IMixinInfo info) {
        if (name.equals(CONNECTION) && mixin.endsWith(".ServerConnectionScatterPlateMixin")) windPressureLiftTeleportApplied = false;
    }
    @Override public void postApply(String name, ClassNode target, String mixin, IMixinInfo info) {
        if (name.equals(ARROW) && mixin.endsWith(".AbstractArrowScatterPlateMixin")) multiAxisArrowApplied = true;
        if (name.equals(PROJECTILE) && mixin.endsWith(".ProjectileScatterPlateMixin")) multiAxisOwnerApplied = true;
        if (name.equals(CONNECTION) && mixin.endsWith(".ServerConnectionScatterPlateMixin")) {
            windPressureLiftTeleportApplied = windPressureLiftTeleportInstalled(target);
            if (!windPressureLiftTeleportApplied) System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                    "NEW443 native teleport invalidation witness unavailable; wind assistance stays unavailable");
        }
    }

    /** Read-only transformed proof: exact four-abort callback, actual HEAD invocation, unchanged native tail.
     * This gate belongs only to NEW443 and never changes any earlier arrow/shield admission decision. */
    public static boolean windPressureLiftTeleportInstalled(ClassNode target) {
        if (target == null || !CONNECTION.replace('.', '/').equals(target.name) || !target.interfaces.contains(LIFT_MARKER)) return false;
        var hosts = target.methods.stream().filter(m -> m.name.equals("teleport") && m.desc.equals(TELEPORT)).toList();
        var callbacks = target.methods.stream().filter(m -> m.name.endsWith("academy$clearBeforeTeleport")).toList();
        if (hosts.size() != 1 || callbacks.size() != 1) return false;
        MethodNode host = hosts.getFirst(), callback = callbacks.getFirst();
        if (host.access != Opcodes.ACC_PUBLIC || !host.tryCatchBlocks.isEmpty() || callback.access != Opcodes.ACC_PRIVATE
                || !callback.desc.equals(CALLBACK) || !callback.tryCatchBlocks.isEmpty()) return false;
        List<AbstractInsnNode> body = operations(callback);
        List<String> controllers = List.of("ScatterPlates", "DissipationCurtain", "ThermalShell", "WindPressureLift");
        if (body.size() != controllers.size() * 3 + 1 || body.getLast().getOpcode() != Opcodes.RETURN) return false;
        for (int i = 0; i < controllers.size(); i++) {
            if (!variable(body.get(i * 3), Opcodes.ALOAD, 0)
                    || !(body.get(i * 3 + 1) instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETFIELD
                    || !field.owner.equals(target.name) || !field.name.equals("player") || !field.desc.equals("Lnet/minecraft/server/level/ServerPlayer;")
                    || !(body.get(i * 3 + 2) instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC || call.itf
                    || !call.owner.equals("cn/academy/port/highpower/" + controllers.get(i)) || !call.name.equals("abort")
                    || !call.desc.equals("(Lnet/minecraft/server/level/ServerPlayer;)V")) return false;
        }
        List<AbstractInsnNode> instructions = operations(host);
        int[] opcodes = {Opcodes.ALOAD, Opcodes.DLOAD, Opcodes.DLOAD, Opcodes.DLOAD, Opcodes.FLOAD, Opcodes.FLOAD, Opcodes.ALOAD};
        int[] slots = {0, 1, 3, 5, 7, 8, 9};
        if (instructions.size() < 10) return false;
        for (int i = 0; i < slots.length; i++) if (!variable(instructions.get(i), opcodes[i], slots[i])) return false;
        int invocation;
        if (instructions.get(7).getOpcode() == Opcodes.ACONST_NULL) invocation = 8; // Mixin's unused CallbackInfo optimization.
        else {
            if (instructions.size() < 14 || !(instructions.get(7) instanceof TypeInsnNode type) || type.getOpcode() != Opcodes.NEW
                    || !type.desc.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfo")
                    || instructions.get(8).getOpcode() != Opcodes.DUP
                    || !(instructions.get(9) instanceof LdcInsnNode literal) || !"teleport".equals(literal.cst)
                    || instructions.get(10).getOpcode() != Opcodes.ICONST_0
                    || !(instructions.get(11) instanceof MethodInsnNode constructor) || constructor.getOpcode() != Opcodes.INVOKESPECIAL
                    || !constructor.owner.equals(type.desc) || !constructor.name.equals("<init>")
                    || !constructor.desc.equals("(Ljava/lang/String;Z)V") || constructor.itf) return false;
            invocation = 12;
        }
        if (!(instructions.get(invocation) instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESPECIAL
                || !call.owner.equals(target.name) || !call.name.equals(callback.name) || !call.desc.equals(CALLBACK) || call.itf) return false;
        long actualCalls = instructions.stream().filter(n -> n instanceof MethodInsnNode m && m.owner.equals(target.name)
                && m.name.equals(callback.name) && m.desc.equals(CALLBACK)).count();
        if (actualCalls != 1) return false;
        // Clone for the comparison: the transformer target is never edited by its own witness.
        MethodNode tail = new MethodNode(host.access, host.name, host.desc, host.signature, host.exceptions.toArray(String[]::new));
        host.accept(tail); List<AbstractInsnNode> copied = operations(tail);
        for (int i = 0; i <= invocation; i++) tail.instructions.remove(copied.get(i));
        ClassNode nativeTail = new ClassNode(); nativeTail.methods.add(tail);
        return REVIEWED.get(CONNECTION + "#teleport" + TELEPORT).equals(fingerprint(nativeTail, "teleport", TELEPORT));
    }
    private static List<AbstractInsnNode> operations(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode node : method.instructions) if (node.getOpcode() >= 0) result.add(node);
        return result;
    }
    private static boolean variable(AbstractInsnNode node, int opcode, int slot) {
        return node instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == slot;
    }
}
