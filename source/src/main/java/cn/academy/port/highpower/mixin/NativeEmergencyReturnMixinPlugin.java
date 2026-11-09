package cn.academy.port.highpower.mixin;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Startup-only symbolic admission of the exact 1.21.1/21.1.252 movement cycle.
 * Missing seams disable only the optional NEW342 witness; no native tick is replaced. */
public final class NativeEmergencyReturnMixinPlugin implements IMixinConfigPlugin {
    private static final String CONNECTION = "net.minecraft.server.network.ServerGamePacketListenerImpl";
    private static final String SERVER_PLAYER = "net.minecraft.server.level.ServerPlayer";
    private static final String PLAYER = "net.minecraft.world.entity.player.Player";
    private static final String LIVING = "net.minecraft.world.entity.LivingEntity";
    private static final String ENTITY = "net.minecraft.world.entity.Entity";
    private static final String VEC = "Lnet/minecraft/world/phys/Vec3;";
    private static final Map<String, String> REVIEWED = Map.ofEntries(
            Map.entry(CONNECTION + "#tick()V", "923095de5c928570ea52c1adb0f81d33a20e945373159649fb61953887bcccbd"),
            Map.entry(CONNECTION + "#resetPosition()V", "4ef61b776b0bcf5cffa9354d14b1d3f36b14dc33980d8241f0840a67190c2a81"),
            Map.entry(SERVER_PLAYER + "#doTick()V", "62c05b8881bc5671f54afa0d6d04cd7a3ed18bd2bb024cb1282ba3b94a22b25c"),
            Map.entry(SERVER_PLAYER + "#travel(" + VEC + ")V", "0fe745707c7b8868382c4ac014d88d0b0b9df9d6477348820af1116c297bb5f3"),
            Map.entry(PLAYER + "#tick()V", "a7884ef1bafb6d06cc3cfb5e84f1b1205ddce59f1ab3b9a0ab1381008e35f475"),
            Map.entry(PLAYER + "#aiStep()V", "779ff6b6b015381f59f124e24ac66ee70d748fc289e94e97cd176adab56f658a"),
            Map.entry(PLAYER + "#travel(" + VEC + ")V", "a109a7569f79337a87f49e8d5e354b3c79e1fdb3ae992590024e58caf99483e0"),
            Map.entry(LIVING + "#aiStep()V", "5716974960f79562d3d82e79f9f1f45e90127f88a35f215aa0f4fd584e023384"),
            Map.entry(LIVING + "#travel(" + VEC + ")V", "3efd116f0628c3e3e38226166652ba00f227f10565415b0f153bf3802e299496"),
            Map.entry(LIVING + "#handleRelativeFrictionAndCalculateMovement(" + VEC + "F)" + VEC, "4cef0197387465a74f43395e078ad316e923651d5214484c09e1d7151e04d359"),
            Map.entry(LIVING + "#getDefaultGravity()D", "4b26bb2e37a3dc52881880908a753759f1329c50aa3ff0664785b62edc1f88a1"),
            Map.entry(ENTITY + "#move(Lnet/minecraft/world/entity/MoverType;" + VEC + ")V", "6534944796ba8604a0fe826ac28bcc3eb2392d2f0ba4b9be0d7e99ea7c3aeac4"),
            Map.entry(ENTITY + "#getBlockSpeedFactor()F", "003fadf350854cb94364c7734f8dbd8fd48caadd04387431669cede8c05f654b"),
            Map.entry(ENTITY + "#getGravity()D", "89f1790cafc4578d0304cab749a50afb8e43c03456e5a8c251ea66ae636a5f8f"),
            Map.entry(ENTITY + "#absMoveTo(DDDFF)V", "34ad6d3636d75f5e85f6d64f898c981b3fdf94eebace683e6080b1b50efaee05"),
            Map.entry(ENTITY + "#absMoveTo(DDD)V", "5c7d30627faf62cddd5b11c2a37e1fabc4e0344232c4e3bb929ca2721ac86142"));

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!(target.equals(CONNECTION) && mixin.endsWith(".ServerConnectionEmergencyReturnMixin")
                || target.equals(LIVING) && mixin.endsWith(".LivingEntityEmergencyReturnMotionMixin"))) return false;
        try {
            Map<String, ClassNode> classes = new HashMap<>();
            for (String key : REVIEWED.keySet()) {
                String name = key.substring(0, key.indexOf('#'));
                if (!classes.containsKey(name)) classes.put(name, MixinService.getService().getBytecodeProvider().getClassNode(name));
            }
            boolean accepted = supported(classes);
            if (!accepted) System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                    "Native connection movement differs from reviewed build; NEW342 evolved-motion witness stays unavailable");
            return accepted;
        } catch (Exception unavailable) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING, "Cannot verify NEW342 connection movement seam: " + unavailable);
            return false;
        }
    }
    public static Set<String> reviewedMethods() { return REVIEWED.keySet(); }
    public static boolean supported(Map<String, ClassNode> classes) {
        if (classes == null) return false;
        for (var expected : REVIEWED.entrySet()) {
            String key = expected.getKey(); int split = key.indexOf('#'), descriptor = key.indexOf('(', split);
            ClassNode type = classes.get(key.substring(0, split));
            if (type == null || !expected.getValue().equals(fingerprint(type, key.substring(split + 1, descriptor), key.substring(descriptor)))) return false;
        }
        return field(classes.get(LIVING), "jumping", "Z") && field(classes.get(LIVING), "lerpSteps", "I")
                && field(classes.get(ENTITY), "stuckSpeedMultiplier", VEC);
    }
    private static boolean field(ClassNode type, String name, String descriptor) {
        return type != null && type.fields.stream().filter(f -> f.name.equals(name) && f.desc.equals(descriptor)
                && (f.access & Opcodes.ACC_STATIC) == 0).count() == 1;
    }
    public static String fingerprint(ClassNode type, String name, String descriptor) {
        if (type == null || type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).count() != 1) return "unreviewed";
        MethodNode method = type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst().orElseThrow();
        int visibility = name.equals("getDefaultGravity") || name.equals("getBlockSpeedFactor") ? Opcodes.ACC_PROTECTED : Opcodes.ACC_PUBLIC;
        if ((method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != visibility)
            return "unreviewed";
        if (!type.name.equals(SERVER_PLAYER.replace('.', '/')) || !name.equals("doTick") || !descriptor.equals("()V"))
            return NativeScatterPlateMixinPlugin.fingerprint(type, name, descriptor);
        if (method.access != Opcodes.ACC_PUBLIC || method.tryCatchBlocks.size() != 1) return "unreviewed";
        Map<AbstractInsnNode, Integer> indices = new IdentityHashMap<>(); int count = 0;
        for (var node : method.instructions) { indices.put(node, count); if (node.getOpcode() >= 0) count++; }
        var catcher = method.tryCatchBlocks.getFirst();
        if (count != 264 || !Integer.valueOf(0).equals(indices.get(catcher.start))
                || !Integer.valueOf(245).equals(indices.get(catcher.end)) || !Integer.valueOf(246).equals(indices.get(catcher.handler))
                || !"java/lang/Throwable".equals(catcher.type)) return "unreviewed";
        MethodNode copy = new MethodNode(); method.accept(copy); copy.tryCatchBlocks.clear();
        // MethodNode.accept copies code, not the MethodVisitor's identity metadata.
        copy.name = method.name; copy.desc = method.desc; copy.access = method.access;
        ClassNode wrapper = new ClassNode(); wrapper.name = type.name; wrapper.methods.add(copy);
        return NativeScatterPlateMixinPlugin.fingerprint(wrapper, name, descriptor);
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
}
