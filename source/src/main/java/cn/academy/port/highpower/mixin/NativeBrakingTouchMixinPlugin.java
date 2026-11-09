package cn.academy.port.highpower.mixin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

/** Optional NEW413 landing admission. Changed native movement/fall code disables only this mode. */
public final class NativeBrakingTouchMixinPlugin implements IMixinConfigPlugin {
    private static final String CONNECTION = "net.minecraft.server.network.ServerGamePacketListenerImpl";
    private static final String SERVER_PLAYER = "net.minecraft.server.level.ServerPlayer";
    private static final String PLAYER = "net.minecraft.world.entity.player.Player";
    private static final String ENTITY = "net.minecraft.world.entity.Entity";
    private static final String LIVING = "net.minecraft.world.entity.LivingEntity";
    private static final String BLOCK = "net.minecraft.world.level.block.Block";
    private static final String HOOKS = "net.neoforged.neoforge.common.CommonHooks";
    private static final String BRIDGE = "cn/academy/port/highpower/BrakingTouchLandingProof";
    private static final String OP = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
    private static final String PACKET = "(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V";
    private static final String MOVE = "(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V";
    private static final String CHECK = "(DZLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V";
    private static final String FALL_ON = "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;F)V";
    private static final String CAUSE = "(FFLnet/minecraft/world/damagesource/DamageSource;)Z";
    private static final String EVENT = "(Lnet/minecraft/world/entity/LivingEntity;FF)[F";
    private static final String HURT = "(Lnet/minecraft/world/damagesource/DamageSource;F)Z";
    private static final Map<String,String> MIXINS = Map.of(
            CONNECTION, "cn.academy.port.highpower.mixin.ServerConnectionBrakingTouchMixin",
            ENTITY, "cn.academy.port.highpower.mixin.EntityBrakingTouchMixin",
            LIVING, "cn.academy.port.highpower.mixin.LivingEntityBrakingTouchMixin");
    // Source and symbolic bytecode reviewed together against 1.21.1 / NeoForge 21.1.252.
    private static final Map<String,String> REVIEWED = Map.ofEntries(
            Map.entry(CONNECTION + "#handleMovePlayer" + PACKET, "8c5a85fa854beedb7ba481d49ae7efc3d854a1006a44ad35f0d0268b5cbb05a3"),
            Map.entry(SERVER_PLAYER + "#doCheckFallDamage(DDDZ)V", "bf3ed97b8637fb7dcb784e789e647f29142938a202b8d0a5c871493d465adf7e"),
            Map.entry(SERVER_PLAYER + "#checkFallDamage" + CHECK, "b400700218e7ca1293bf4bc6cb3f6ca55e2b238febcbe7d583ff0368df601234"),
            Map.entry(LIVING + "#checkFallDamage" + CHECK, "d3033985c0ff54b43fb7e6e90307b4bcc965eaceab18ce9f002d0c9c1c8a2274"),
            Map.entry(ENTITY + "#checkFallDamage" + CHECK, "55585e0809461ec7f6b1bb7ddc5bf13a7afc4a049570e7bf8c681dcc221acb90"),
            Map.entry(ENTITY + "#move" + MOVE, "6534944796ba8604a0fe826ac28bcc3eb2392d2f0ba4b9be0d7e99ea7c3aeac4"),
            Map.entry(ENTITY + "#checkSupportingBlock(ZLnet/minecraft/world/phys/Vec3;)V", "bd2bb37cbcc238a12debc2edb6112c06b3212d384641d3f3d5df36b092ca5922"),
            Map.entry(ENTITY + "#getOnPos(F)Lnet/minecraft/core/BlockPos;", "d4b140d7ea7dd26892203a157c8d3ff7b8f97cc81a01b19d7caf4633a4884719"),
            Map.entry("net.minecraft.world.level.CollisionGetter#findSupportingBlock(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/Optional;", "334e3cb32936a70d4509ea8154566eb5704f55d9ea5a9afe240cf05224a81459"),
            Map.entry(BLOCK + "#fallOn" + FALL_ON, "112a6029f89a89f6babefc8cbbb874a772a79105f8ab9ce6b1324c42918315a9"),
            Map.entry(PLAYER + "#causeFallDamage" + CAUSE, "392dd3153023de115b62e537e5474bbf6fbc9a0bdeb06ca5d6cf5802bb71ae3c"),
            Map.entry(LIVING + "#causeFallDamage" + CAUSE, "fdf14bec1e82ad8eb5ae5ec45876ca363628adaee7da9581e05db8566a6838a1"),
            Map.entry(LIVING + "#calculateFallDamage(FF)I", "d272466b17ae2cae8c5a0fa7f15425c997d5a8d286e243fea3327b9fb2a35cfe"),
            Map.entry(HOOKS + "#onLivingFall" + EVENT, "5914273fec6ee6104c69a2667c13fe1550918f3c0f2cdf64751e5fa0cbfe4d31"),
            Map.entry("net.neoforged.neoforge.event.entity.living.LivingFallEvent#<init>(Lnet/minecraft/world/entity/LivingEntity;FF)V", "becfdac92b6a539327b818b6f5683a4c42e2bad4b83c73f148188adc51fdaae1"),
            Map.entry("net.neoforged.neoforge.event.entity.living.LivingFallEvent#getDistance()F", "dcc4ab626be91aa318784c2547c4276dedb3f153fe9241635e849b4a0ed4ff47"),
            Map.entry("net.neoforged.neoforge.event.entity.living.LivingFallEvent#getDamageMultiplier()F", "ed0ccae96e6da340115e132a984f8ed1faf358ca000e33d1c8aaf68671074640"),
            Map.entry("net.neoforged.neoforge.event.entity.living.LivingFallEvent#setDistance(F)V", "6fbd2606795c26c9ec029597c9a5d626d41d07639b16bdb549f4d6211f199db8"),
            Map.entry("net.neoforged.neoforge.event.entity.living.LivingFallEvent#setDamageMultiplier(F)V", "95ae21339ae604788863dd901748be7f3f7923c2da13a07c75f165e8b2d320dc"));
    private static volatile boolean reviewedLandingSupported, failed;
    private static volatile boolean reviewedLandingHurtSupported, landingHurtFailed;
    private boolean admissionChecked, admitted, witnessRegistered;
    private boolean landingHurtSite, landingHurtMerged, landingHurtInstalled;
    private final Set<String> sites = new HashSet<>(), merged = new HashSet<>(), installed = new HashSet<>();

    public static boolean isReviewedLandingSupported() { return reviewedLandingSupported && !failed; }
    public static boolean isReviewedLandingHurtSupported() {
        return isReviewedLandingSupported() && reviewedLandingHurtSupported && !landingHurtFailed;
    }
    public static void disableLandingHurtAfterCallbackFailure(Throwable failure) {
        landingHurtFailed = true; reviewedLandingHurtSupported = false;
        warn("NEW422 landing hurt observer failed; NEW413 and ordinary native falls remain unchanged: " + failure);
    }
    public static void disableAfterCallbackFailure(Throwable failure) {
        failed = true; reviewedLandingSupported = false; warn("NEW413 landing observer failed; ordinary native falls remain unchanged: " + failure);
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!mixin.equals(MIXINS.get(target)) || !witnessRegistered) return false;
        if (!admissionChecked) {
            admissionChecked = true;
            try {
                Map<String,ClassNode> classes = new HashMap<>();
                for (String key : REVIEWED.keySet()) {
                    String owner = key.substring(0, key.indexOf('#'));
                    if (!classes.containsKey(owner)) classes.put(owner, MixinService.getService().getBytecodeProvider().getClassNode(owner));
                }
                admitted = supported(classes);
            } catch (Exception | LinkageError unavailable) { warn("Cannot review NEW413 native landing: " + unavailable); }
            if (!admitted) warn("Native movement/fall code differs from reviewed 1.21.1/21.1.252; NEW413 landing is unavailable");
        }
        return admitted;
    }
    public static Set<String> reviewedMethods() { return REVIEWED.keySet(); }
    public static boolean supported(Map<String,ClassNode> classes) {
        if (classes == null) return false;
        for (var expected : REVIEWED.entrySet()) {
            String key = expected.getKey(); int split = key.indexOf('#'), descriptor = key.indexOf('(', split);
            String owner = key.substring(0, split); ClassNode node = classes.get(owner);
            if (node == null || !owner.replace('.', '/').equals(node.name)
                    || !expected.getValue().equals(fingerprint(node, key.substring(split + 1, descriptor), key.substring(descriptor)))) return false;
        }
        return MIXINS.keySet().stream().allMatch(owner -> nativeSitesSupported(classes.get(owner)));
    }
    public static String fingerprint(ClassNode node, String name, String descriptor) {
        if (node == null || node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).count() != 1) return "unreviewed";
        int access = Set.of("checkFallDamage", "checkSupportingBlock", "getOnPos", "calculateFallDamage").contains(name) ? Opcodes.ACC_PROTECTED : Opcodes.ACC_PUBLIC;
        if (node.name.equals(HOOKS.replace('.', '/'))) access |= Opcodes.ACC_STATIC;
        MethodNode method = method(node, name, descriptor);
        if (method.access != access) return "unreviewed";
        return NativeScatterPlateMixinPlugin.fingerprint(node, name, descriptor);
    }
    public static boolean nativeSitesSupported(ClassNode node) {
        if (node == null) return false;
        if (node.name.equals(CONNECTION.replace('.', '/'))) {
            MethodNode packet = method(node, "handleMovePlayer", PACKET);
            return count(packet, SERVER_PLAYER.replace('.', '/'), "move", MOVE) == 1
                    && count(packet, SERVER_PLAYER.replace('.', '/'), "doCheckFallDamage", "(DDDZ)V") == 2;
        }
        if (node.name.equals(ENTITY.replace('.', '/')))
            return count(method(node, "checkFallDamage", CHECK), BLOCK.replace('.', '/'), "fallOn", FALL_ON) == 1;
        if (node.name.equals(LIVING.replace('.', '/')))
            return count(method(node, "causeFallDamage", CAUSE), HOOKS.replace('.', '/'), "onLivingFall", EVENT) == 1
                    && count(method(node, "causeFallDamage", CAUSE), node.name, "calculateFallDamage", "(FF)I") == 1;
        return false;
    }
    /** Separate NEW422 admission; the already reviewed cause method must contain its one native hurt. */
    public static boolean nativeLandingHurtSiteSupported(ClassNode node) {
        if (node == null || !node.name.equals(LIVING.replace('.', '/'))) return false;
        MethodNode cause = method(node, "causeFallDamage", CAUSE);
        if (cause == null || !REVIEWED.get(LIVING + "#causeFallDamage" + CAUSE).equals(fingerprint(node, "causeFallDamage", CAUSE))
                || count(cause, node.name, "hurt", HURT) != 1) return false;
        int event = index(cause, HOOKS.replace('.', '/'), "onLivingFall", EVENT);
        int calculate = index(cause, node.name, "calculateFallDamage", "(FF)I");
        int hurt = index(cause, node.name, "hurt", HURT);
        return event >= 0 && calculate > event && hurt > calculate
                && ((MethodInsnNode)cause.instructions.get(hurt)).getOpcode() == Opcodes.INVOKEVIRTUAL;
    }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        installed.remove(target); merged.remove(target); reviewedLandingSupported = false;
        if (admitted && mixin.equals(MIXINS.get(target)) && nativeSitesSupported(node)) sites.add(target); else sites.remove(target);
        if (LIVING.equals(target)) {
            landingHurtMerged = false; landingHurtInstalled = false; reviewedLandingHurtSupported = false;
            landingHurtSite = admitted && mixin.equals(MIXINS.get(target)) && nativeLandingHurtSiteSupported(node);
        }
    }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (sites.contains(target) && mixin.equals(MIXINS.get(target)) && mergedCallbacksSupported(node)) merged.add(target); else merged.remove(target);
        reviewedLandingSupported = false; // MixinExtras installs the actual call sites in a later extension.
        if (LIVING.equals(target)) {
            landingHurtMerged = landingHurtSite && mixin.equals(MIXINS.get(target)) && callback(node, landingHurtHook()) != null;
            reviewedLandingHurtSupported = false;
        }
    }
    // String tuples avoid lazily loading helper classes from Mixin's restricted package.
    // Tuple fields: suffix, helper, callback descriptor, bridge descriptor, native host descriptor.
    private static List<String[]> hooks(String owner) {
        String c = "L" + CONNECTION.replace('.', '/') + ";", p = "L" + SERVER_PLAYER.replace('.', '/') + ";";
        String living = "L" + LIVING.replace('.', '/') + ";";
        if (owner.equals(CONNECTION.replace('.', '/'))) return List.of(
                new String[]{"academy$brakingPacket", "nativePacket", "(" + PACKET.substring(1,PACKET.length()-2) + OP + ")V", "(" + c + PACKET.substring(1,PACKET.length()-2) + OP + ")V", PACKET},
                new String[]{"academy$brakingMove", "nativeMove", "(" + p + MOVE.substring(1,MOVE.length()-2) + OP + ")V", "(" + c + p + MOVE.substring(1,MOVE.length()-2) + OP + ")V", PACKET},
                new String[]{"academy$brakingAcceptedFall", "nativeAcceptedFall", "(" + p + "DDDZ" + OP + ")V", "(" + c + p + "DDDZ" + OP + ")V", PACKET});
        if (owner.equals(ENTITY.replace('.', '/'))) {
            String args = "L" + BLOCK.replace('.', '/') + ";" + FALL_ON.substring(1,FALL_ON.length()-2);
            return java.util.Collections.singletonList(new String[]{"academy$brakingFallOn", "nativeFallOn", "(" + args + OP + ")V", "(" + args + OP + ")V", CHECK});
        }
        if (owner.equals(LIVING.replace('.', '/'))) return List.of(
                new String[]{"academy$brakingCause", "nativeCause", "(" + CAUSE.substring(1,CAUSE.length()-2) + OP + ")Z", "(" + living + CAUSE.substring(1,CAUSE.length()-2) + OP + ")Z", CAUSE},
                new String[]{"academy$brakingEvent", "nativeEvent", "(" + living + "FF" + OP + ")[F", "(" + living + "FF" + OP + ")[F", CAUSE});
        return List.of();
    }
    private static MethodNode callback(ClassNode node, String[] hook) {
        List<MethodNode> methods = node.methods.stream().filter(m -> m.name.endsWith(hook[0])).toList();
        if (methods.size() != 1) return null;
        MethodNode method = methods.getFirst();
        if (!method.desc.equals(hook[2]) || (method.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) != Opcodes.ACC_PRIVATE) return null;
        int calls = 0;
        for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals(BRIDGE)) {
            if (!call.name.equals(hook[1]) || !call.desc.equals(hook[3]) || call.getOpcode() != Opcodes.INVOKESTATIC) return null;
            calls++;
        }
        return calls == 1 ? method : null;
    }
    private static String[] landingHurtHook() {
        String descriptor = "(L" + LIVING.replace('.', '/') + ";" + HURT.substring(1,HURT.length()-2) + OP + ")Z";
        return new String[]{"anchor$landingHurt", "nativeLandingHurt", descriptor, descriptor, CAUSE};
    }
    /** Requires the new callback's exact bridge and actual final callsite, independently of the NEW413 witness. */
    public static boolean installedLandingHurtCallbackSupported(ClassNode node) {
        if (node == null || !node.name.equals(LIVING.replace('.', '/')) || !installedCallbacksSupported(node)) return false;
        MethodNode hurt = callback(node, landingHurtHook());
        MethodNode event = callback(node, hooks(node.name).get(1));
        if (hurt == null || event == null) return false;
        long calls = node.methods.stream().filter(m -> m.desc.equals(CAUSE))
                .mapToLong(m -> count(m, node.name, hurt.name, hurt.desc)).sum();
        long unwrapped = node.methods.stream().filter(m -> m.desc.equals(CAUSE))
                .mapToLong(m -> count(m, node.name, "hurt", HURT)).sum();
        if (calls != 1 || unwrapped != 0) return false;
        for (MethodNode body : node.methods) if (body.desc.equals(CAUSE)) {
            int eventAt = index(body, node.name, event.name, event.desc);
            int calculateAt = index(body, node.name, "calculateFallDamage", "(FF)I");
            int hurtAt = index(body, node.name, hurt.name, hurt.desc);
            if (eventAt >= 0 && calculateAt > eventAt && hurtAt > calculateAt) return true;
        }
        return false;
    }
    private static boolean mergedCallbacksSupported(ClassNode node) {
        if (node == null || hooks(node.name).isEmpty()) return false;
        for (String[] hook : hooks(node.name)) if (callback(node, hook) == null) return false;
        if (node.name.equals(ENTITY.replace('.', '/'))) {
            MethodNode witness = method(node, "academy$brakingLandingSeam", "()Z");
            return node.interfaces.contains(BRIDGE + "$Access") && witness != null && witness.access == Opcodes.ACC_PUBLIC
                    && count(witness, "cn/academy/port/highpower/mixin/NativeBrakingTouchMixinPlugin", "isReviewedLandingSupported", "()Z") == 1;
        }
        return true;
    }
    public static boolean installedCallbacksSupported(ClassNode node) {
        if (!mergedCallbacksSupported(node)) return false;
        for (String[] hook : hooks(node.name)) {
            MethodNode callback = callback(node, hook);
            long calls = node.methods.stream().filter(m -> m.desc.equals(hook[4]))
                    .mapToLong(m -> count(m, node.name, callback.name, callback.desc)).sum();
            if (calls != 1) return false;
        }
        if (node.name.equals(CONNECTION.replace('.', '/'))) {
            // Only accepted ordinal zero is adapted; the correction branch still has its native fall call.
            long corrections = node.methods.stream().filter(m -> m.desc.equals(PACKET))
                    .mapToLong(m -> count(m, SERVER_PLAYER.replace('.', '/'), "doCheckFallDamage", "(DDDZ)V")).sum();
            if (corrections != 1) return false;
            MethodNode move = callback(node, hooks(node.name).get(1));
            MethodNode accepted = callback(node, hooks(node.name).get(2));
            boolean ordered = false;
            for (MethodNode body : node.methods) if (body.desc.equals(PACKET)) {
                int moveAt = index(body, node.name, move.name, move.desc);
                int acceptedAt = index(body, node.name, accepted.name, accepted.desc);
                int correctionAt = index(body, SERVER_PLAYER.replace('.', '/'), "doCheckFallDamage", "(DDDZ)V");
                if (moveAt >= 0 && acceptedAt > moveAt && correctionAt > acceptedAt) ordered = true;
            }
            if (!ordered) return false;
        }
        return true;
    }
    private static int index(MethodNode method, String owner, String name, String descriptor) {
        for (int i = 0; i < method.instructions.size(); i++) if (method.instructions.get(i) instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor)) return i;
        return -1;
    }
    private static int count(MethodNode method, String owner, String name, String descriptor) {
        if (method == null) return 0; int count = 0;
        for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor)) count++;
        return count;
    }
    private static MethodNode method(ClassNode node, String name, String desc) {
        return node == null ? null : node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElse(null);
    }
    @Override public void onLoad(String mixinPackage) {
        reviewedLandingSupported = false; failed = false;
        reviewedLandingHurtSupported = false; landingHurtFailed = false;
        try {
            Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
                extensions.add(new FinalWitness()); witnessRegistered = true;
            }
        } catch (RuntimeException | LinkageError failure) { warn("Cannot register NEW413 final witness: " + failure); }
    }
    private final class FinalWitness implements IExtension {
        public boolean checkActive(MixinEnvironment environment) { return true; }
        public void preApply(ITargetClassContext context) {}
        public void postApply(ITargetClassContext context) {}
        public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
            String target = name.replace('/', '.'); if (!MIXINS.containsKey(target)) return;
            if (admitted && sites.contains(target) && merged.contains(target) && installedCallbacksSupported(node)) installed.add(target);
            else { installed.remove(target); warn("NEW413 final native wrapper missing for " + target + "; landing remains unavailable"); }
            reviewedLandingSupported = !failed && installed.containsAll(MIXINS.keySet());
            if (LIVING.equals(target)) {
                landingHurtInstalled = landingHurtSite && landingHurtMerged && installedLandingHurtCallbackSupported(node);
                if (!landingHurtInstalled) warn("NEW422 final native fall hurt wrapper missing; NEW413 admission is unchanged");
            }
            reviewedLandingHurtSupported = !landingHurtFailed && landingHurtInstalled && reviewedLandingSupported;
        }
    }
    private static void warn(String message) { System.getLogger(NativeBrakingTouchMixinPlugin.class.getName()).log(System.Logger.Level.WARNING, message); }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
}
