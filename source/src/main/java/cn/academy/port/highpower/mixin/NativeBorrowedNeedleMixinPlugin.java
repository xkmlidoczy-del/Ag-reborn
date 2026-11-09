package cn.academy.port.highpower.mixin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** NEW351 admission of the reviewed 1.21.1 / NeoForge 21.1.252 native death path.
 * This optional adapter never replaces, cancels, or broadens ordinary death handling. */
public final class NativeBorrowedNeedleMixinPlugin implements IMixinConfigPlugin {
    private static final String PLAYER = "net.minecraft.world.entity.player.Player";
    private static final String SERVER_PLAYER = "net.minecraft.server.level.ServerPlayer";
    private static final String LIVING = "net.minecraft.world.entity.LivingEntity";
    private static final String INVENTORY = "net.minecraft.world.entity.player.Inventory";
    private static final String ENTITY = "net.minecraft.world.entity.Entity";
    private static final String MIXIN = "cn.academy.port.highpower.mixin.PlayerBorrowedNeedleDeathMixin";
    private static final String ESCROW = "cn/academy/port/highpower/BorrowedNeedleEscrow";
    private static final String CALLBACK = "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private static final String DROP = "(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;";
    private static final String DEATH_LOOT = "(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V";
    // Source and executable bytecode were reviewed together. Do not refresh these on a mismatch.
    private static final Map<String, String> REVIEWED = Map.ofEntries(
            Map.entry(PLAYER + "#dropEquipment()V", "1617234dc0c35ca56f12723ae98413c6cfccc9c013d39d79e12bd5c8e962ea59"),
            Map.entry(PLAYER + "#drop" + DROP, "84003939e475a8e95f23269ffc9638ee1c0f7734c333546c2df49acadbf06dc3"),
            Map.entry(SERVER_PLAYER + "#die(Lnet/minecraft/world/damagesource/DamageSource;)V", "a4052d53750f51e08c4f7612a1cee7c04482b298dd3db266a4ef73ad1f0f0335"),
            Map.entry(SERVER_PLAYER + "#drop" + DROP, "ecfbaa826675923e36cf3eb792ed14f2b0841089e76dc58f328af482a4710b21"),
            Map.entry(LIVING + "#dropAllDeathLoot" + DEATH_LOOT, "8917be8f02903081d53b6dfa9e052e1ebbff3102aea9b65b2ae4080984d7b127"),
            Map.entry(LIVING + "#dropEquipment()V", "11590e4911f7721a11d19b144eba638074906a86cc4bbfbea6cb2e8377f387a0"),
            Map.entry(INVENTORY + "#dropAll()V", "08ce8b12b3d32fd2e119290a4a5d5a89dd49f8960bfcce9c19ea107f28b56058"),
            Map.entry(ENTITY + "#captureDrops()Ljava/util/Collection;", "fd3580bcb7336d93c3c7dad640da721a25e730a029ae355ea75d14068bc53e28"),
            Map.entry(ENTITY + "#captureDrops(Ljava/util/Collection;)Ljava/util/Collection;", "3de69fd5dc47018c005e9360ff4310e782dc93f2dc31366d24602337b7ed31dd"));
    private static volatile boolean reviewedDeathDropSupported;
    private boolean nativeReviewed;
    private boolean preApplyReviewed;

    /** Borrowing is unavailable until both native review and actual callback installation succeed. */
    public static boolean isReviewedDeathDropSupported() { return reviewedDeathDropSupported; }

    /** A broken optional callback must not prevent the remainder of vanilla death from running. */
    public static void disableAfterCallbackFailure(Throwable failure) {
        reviewedDeathDropSupported = false;
        warn("NEW351 death adapter failed; further borrowing is disabled: " + failure);
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!PLAYER.equals(target) || !MIXIN.equals(mixin)) return false;
        reviewedDeathDropSupported = false;
        nativeReviewed = false;
        try {
            Map<String, ClassNode> classes = new HashMap<>();
            for (String key : REVIEWED.keySet()) {
                String name = key.substring(0, key.indexOf('#'));
                if (!classes.containsKey(name)) classes.put(name, MixinService.getService().getBytecodeProvider().getClassNode(name));
            }
            nativeReviewed = supported(classes);
            if (!nativeReviewed) warn("Native death path differs from reviewed 1.21.1/21.1.252; NEW351 borrowing is disabled");
            return nativeReviewed;
        } catch (Exception | LinkageError unavailable) {
            warn("Cannot verify NEW351 native death path; borrowing is disabled: " + unavailable);
            return false;
        }
    }

    public static Set<String> reviewedMethods() { return REVIEWED.keySet(); }
    public static boolean supported(Map<String, ClassNode> classes) {
        if (classes == null) return false;
        for (var expected : REVIEWED.entrySet()) {
            String key = expected.getKey(); int split = key.indexOf('#'), descriptor = key.indexOf('(', split);
            String owner = key.substring(0, split);
            ClassNode type = classes.get(owner);
            if (type == null || !owner.replace('.', '/').equals(type.name)
                    || !expected.getValue().equals(fingerprint(type, key.substring(split + 1, descriptor), key.substring(descriptor)))) return false;
        }
        // A new override would bypass this one reviewed Player hook.
        return method(classes.get(SERVER_PLAYER), "dropEquipment", "()V") == null
                && method(classes.get(PLAYER), "dropAllDeathLoot", DEATH_LOOT) == null
                && method(classes.get(SERVER_PLAYER), "dropAllDeathLoot", DEATH_LOOT) == null;
    }

    public static String fingerprint(ClassNode type, String name, String descriptor) {
        if (type == null || type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).count() != 1) return "unreviewed";
        MethodNode method = method(type, name, descriptor);
        int expected = name.equals("dropEquipment") || name.equals("dropAllDeathLoot") ? Opcodes.ACC_PROTECTED : Opcodes.ACC_PUBLIC;
        if (method.access != expected) return "unreviewed";
        return NativeScatterPlateMixinPlugin.fingerprint(type, name, descriptor);
    }

    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        preApplyReviewed = nativeReviewed && PLAYER.equals(target) && MIXIN.equals(mixin)
                && REVIEWED.get(PLAYER + "#dropEquipment()V").equals(fingerprint(node, "dropEquipment", "()V"));
        if (!preApplyReviewed) reviewedDeathDropSupported = false;
    }

    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        reviewedDeathDropSupported = preApplyReviewed && PLAYER.equals(target) && MIXIN.equals(mixin) && installedCallbacksSupported(node);
        if (!reviewedDeathDropSupported) warn("NEW351 exact death callbacks were not installed; borrowing is disabled");
    }

    /** Verify the transformed method, not merely matching annotation text or pre-transform seams. */
    public static boolean installedCallbacksSupported(ClassNode type) {
        if (type == null || !PLAYER.replace('.', '/').equals(type.name)) return false;
        MethodNode drop = method(type, "dropEquipment", "()V");
        if (drop == null || type.methods.stream().filter(m -> m.name.equals("dropEquipment") && m.desc.equals("()V")).count() != 1
                || drop.access != Opcodes.ACC_PROTECTED || !drop.tryCatchBlocks.isEmpty()) return false;
        MethodNode begin = callback(type, "academy$borrowedNeedleBeginDeath", "beginNativeDeath");
        MethodNode transfer = callback(type, "academy$borrowedNeedleDeathDrop", "deathDrop");
        if (begin == null || transfer == null) return false;
        // The native sequence remains intact; HEAD retires the permit, and only the native
        // !keepInventory branch reaches the transfer immediately before Inventory.dropAll.
        List<String> expected = List.of(type.name + "#" + begin.name + CALLBACK,
                LIVING.replace('.', '/') + "#dropEquipment()V", type.name + "#level()Lnet/minecraft/world/level/Level;",
                "net/minecraft/world/level/Level#getGameRules()Lnet/minecraft/world/level/GameRules;",
                "net/minecraft/world/level/GameRules#getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z",
                type.name + "#destroyVanishingCursedItems()V", type.name + "#" + transfer.name + CALLBACK,
                INVENTORY.replace('.', '/') + "#dropAll()V");
        java.util.ArrayList<String> actual = new java.util.ArrayList<>();
        for (AbstractInsnNode instruction : drop.instructions) {
            if (!(instruction instanceof MethodInsnNode call)) continue;
            if (call.owner.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfo")
                    && call.name.equals("<init>") && call.desc.equals("(Ljava/lang/String;Z)V")) continue;
            actual.add(call.owner + "#" + call.name + call.desc);
        }
        if (!expected.equals(actual)) return false;
        MethodInsnNode condition = null, transferCall = null, nativeDrop = null;
        JumpInsnNode branch = null;
        for (AbstractInsnNode instruction : drop.instructions) {
            if (instruction instanceof JumpInsnNode jump) {
                if (branch != null || jump.getOpcode() != Opcodes.IFNE) return false;
                branch = jump;
            } else if (instruction instanceof MethodInsnNode call) {
                if (call.name.equals("getBoolean")) condition = call;
                if (call.name.equals(transfer.name) && call.owner.equals(type.name)) transferCall = call;
                if (call.name.equals("dropAll") && call.owner.equals(INVENTORY.replace('.', '/'))) nativeDrop = call;
            }
        }
        // The pinned Mixin implementation inserts this callback before the native receiver
        // GETFIELD, not between GETFIELD and INVOKEVIRTUAL. Review that exact three-opcode
        // tail; accepting arbitrary intervening instructions would widen the death seam.
        AbstractInsnNode receiver = nextCode(transferCall);
        return branch != null && nextCode(condition) == branch
                && receiver instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                && field.owner.equals(type.name) && field.name.equals("inventory")
                && field.desc.equals("Lnet/minecraft/world/entity/player/Inventory;")
                && nextCode(receiver) == nativeDrop
                && drop.instructions.indexOf(branch.label) > drop.instructions.indexOf(nativeDrop)
                && nextCode(branch.label) != null && nextCode(branch.label).getOpcode() == Opcodes.RETURN;
    }

    private static AbstractInsnNode nextCode(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        do { instruction = instruction.getNext(); } while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }

    private static MethodNode callback(ClassNode type, String suffix, String helper) {
        List<MethodNode> matches = type.methods.stream().filter(m -> m.name.endsWith(suffix) && m.desc.equals(CALLBACK)).toList();
        if (matches.size() != 1) return null;
        MethodNode result = matches.getFirst();
        if ((result.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) != Opcodes.ACC_PRIVATE) return null;
        long calls = 0;
        for (AbstractInsnNode instruction : result.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(ESCROW)) {
                if (!call.name.equals(helper) || !call.desc.equals("(Lnet/minecraft/server/level/ServerPlayer;)V") || call.getOpcode() != Opcodes.INVOKESTATIC) return null;
                calls++;
            }
        }
        return calls == 1 ? result : null;
    }
    private static MethodNode method(ClassNode type, String name, String descriptor) {
        return type == null ? null : type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst().orElse(null);
    }
    private static void warn(String message) {
        System.getLogger(NativeBorrowedNeedleMixinPlugin.class.getName()).log(System.Logger.Level.WARNING, message);
    }
    @Override public void onLoad(String mixinPackage) { reviewedDeathDropSupported = false; }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
}
