package cn.academy.port.highpower.mixin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Bytecode-only admission of the actual 1.21.1 / NeoForge 21.1.252 native harvest chain. */
public final class NativeMiningMixinPlugin implements IMixinConfigPlugin {
    public static final String OWNER = "net/minecraft/server/level/ServerPlayerGameMode";
    private static final String ACCESS = "cn/academy/port/highpower/NativeMiningAccess";
    private static final String HELPER = "cn/academy/port/highpower/BeamEtchingMining";
    private static final String MIXIN = "cn.academy.port.highpower.mixin.ServerPlayerGameModeMiningMixin";
    private static final String VERSION = "academy$nativeMiningContractVersion";
    private static final String POS = "Lnet/minecraft/core/BlockPos;", STATE = "Lnet/minecraft/world/level/block/state/BlockState;",
            LEVEL = "Lnet/minecraft/world/level/Level;", PLAYER = "Lnet/minecraft/world/entity/player/Player;",
            FLUID = "Lnet/minecraft/world/level/material/FluidState;", STACK = "Lnet/minecraft/world/item/ItemStack;";
    private static final String BREAK = "(" + LEVEL + "Lnet/minecraft/world/level/GameType;Lnet/minecraft/server/level/ServerPlayer;" + POS + STATE + ")Lnet/neoforged/neoforge/event/level/BlockEvent$BreakEvent;";
    private static final String MINE = "(" + LEVEL + STATE + POS + PLAYER + ")V";
    private static final String REMOVE = "(" + LEVEL + POS + PLAYER + "Z" + FLUID + ")Z";
    private record Redirect(String helper, String descriptor, String owner, String name, String nativeDescriptor, int opcode) {}
    private static final List<Redirect> REDIRECTS = List.of(
            new Redirect("nativeBreak", BREAK, "net/neoforged/neoforge/common/CommonHooks", "fireBlockBreak", BREAK, Opcodes.INVOKESTATIC),
            new Redirect("nativeMine", "(" + STACK + MINE.substring(1), "net/minecraft/world/item/ItemStack", "mineBlock", MINE, Opcodes.INVOKEVIRTUAL),
            new Redirect("nativeRemove", "(" + STATE + REMOVE.substring(1), "net/minecraft/world/level/block/state/BlockState", "onDestroyedByPlayer", REMOVE, Opcodes.INVOKEVIRTUAL));
    private static final Map<String, Map<String, String>> REVIEWED = Map.ofEntries(
            Map.entry(OWNER, Map.of("destroyBlock(" + POS + ")Z", "51d8806fd2edac1c822e4223d2663de9c0b5f530501a59eb46c33c5f6581c8d8",
                    "removeBlock(" + POS + STATE + "Z)Z", "d2dbe5d0312945f5c9a8bd6293b0ae80abae14d610520b37d336cacfa80f096f")),
            // Level commits the cell before neighbour notification: this ordering is the
            // evidence boundary for RuntimeException after actual removal, before boolean return.
            Map.entry("net/minecraft/world/level/Level", Map.of(
                    "removeBlock(" + POS + "Z)Z", "8be5d709fd0ada534b877231cd97300652803c0c6ada02c78b6bd108971d34b0",
                    "setBlock(" + POS + STATE + "I)Z", "76fbce18240f2675358c70be2aecf1ef69f48515603c50eee6a0324b28d79df1",
                    "setBlock(" + POS + STATE + "II)Z", "9799ab094c6af17c353c1f224f84ab8bcb4692cf4189e6957720a5a8f884326c",
                    "markAndNotifyBlock(" + POS + "Lnet/minecraft/world/level/chunk/LevelChunk;" + STATE + STATE + "II)V", "fd9eb12d3f60f7836fd7953bf98f7b9678d92c0d4eba42478cc4920eba3811ee")),
            Map.entry("net/minecraft/server/level/ServerLevel", Map.of(
                    "blockUpdated(" + POS + "Lnet/minecraft/world/level/block/Block;)V", "9274e24009ee25c83ad0ffe3f01af64760dff028781428be8eb0b79d6f7dcc52",
                    "updateNeighborsAt(" + POS + "Lnet/minecraft/world/level/block/Block;)V", "49894e596af4541139956fc61357c5dce6811540b4e91ec76a5d0fdd878664a1")),
            Map.entry("net/neoforged/neoforge/common/CommonHooks", Map.of("fireBlockBreak" + BREAK, "2770e0c326a24eea5c056708bc95ac251dd608f2330d879095bfd4c21767ec30")),
            Map.entry("net/minecraft/world/item/ItemStack", Map.of("mineBlock" + MINE, "9de82cd6dc719885278102030f83378965e94d263c3af40391b00e738060ee03")),
            Map.entry("net/minecraft/world/item/Item", Map.of("mineBlock(" + STACK + LEVEL + STATE + POS + "Lnet/minecraft/world/entity/LivingEntity;)Z", "a404385794e0f8006cf8a29170b53a64a0281ed2d4c9802df00f6af81bdbfb8f")),
            Map.entry("net/minecraft/world/level/block/Block", Map.of(
                    "playerWillDestroy(" + LEVEL + POS + STATE + PLAYER + ")" + STATE, "ce0bd75a55c5644516d0b94118a56ba37fd3e7b7e64a3dec8f33542fff2beee5",
                    "playerDestroy(" + LEVEL + PLAYER + POS + STATE + "Lnet/minecraft/world/level/block/entity/BlockEntity;" + STACK + ")V", "cb578d5f61aa5e2de366ddb4579de7a100792e0684f5fef3502376e0d597551c",
                    "dropResources(" + STATE + LEVEL + POS + "Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;" + STACK + ")V", "47f7a0787862b11f78b6d5c1c48319e5eee2bf00a274eb1d120914eaed985324")),
            Map.entry("net/neoforged/neoforge/common/extensions/IBlockStateExtension", Map.of(
                    "onDestroyedByPlayer" + REMOVE, "b9828a7d54fddc66bfd1ad7f3c4917739cda9a54c8cb0a7959b5cae23d6b8135",
                    "canHarvestBlock(Lnet/minecraft/world/level/BlockGetter;" + POS + PLAYER + ")Z", "1092c8e2e0ab35aa75c7ac5d29bf7cfe14d5ab1f7687911314973711f48d2d09")),
            Map.entry("net/neoforged/neoforge/common/extensions/IBlockExtension", Map.of(
                    "onDestroyedByPlayer(" + STATE + REMOVE.substring(1), "ebd9c501446efa62a160bcb6f9d3d0c38873b684c811c8cad1f43bd7c3ddff84",
                    "canHarvestBlock(" + STATE + "Lnet/minecraft/world/level/BlockGetter;" + POS + PLAYER + ")Z", "d2a03e24015649869d4c952654a002fad14c39259954f5a03ae833929e937847")));

    public static Map<String, Map<String, String>> reviewedMethods() { return REVIEWED; }
    public static boolean supportedClass(ClassNode node) {
        if (node == null || !REVIEWED.containsKey(node.name)) return false;
        if (OWNER.equals(node.name) && (!"java/lang/Object".equals(node.superName)
                || !field(node, "level", "Lnet/minecraft/server/level/ServerLevel;", Opcodes.ACC_PROTECTED)
                || !field(node, "player", "Lnet/minecraft/server/level/ServerPlayer;", Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL)
                || !field(node, "gameModeForPlayer", "Lnet/minecraft/world/level/GameType;", Opcodes.ACC_PRIVATE))) return false;
        for (var entry : REVIEWED.get(node.name).entrySet()) {
            String key = entry.getKey(); int split = key.indexOf('(');
            if (!entry.getValue().equals(fingerprint(node, key.substring(0, split), key.substring(split)))) return false;
        }
        return true;
    }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!OWNER.replace('/', '.').equals(target) || !MIXIN.equals(mixin)) return false;
        try {
            for (String owner : REVIEWED.keySet()) if (!supportedClass(MixinService.getService().getBytecodeProvider().getClassNode(owner.replace('/', '.')))) {
                warn("Native harvest differs from reviewed 1.21.1/21.1.252: " + owner + "; NEW213 stays unavailable"); return false;
            }
            return true;
        } catch (Exception | LinkageError failure) { warn("Cannot verify native mining seam: " + failure); return false; }
    }
    /** Restore only exact verified redirects in a copy, then recheck every original instruction. */
    public static boolean supportedApplied(ClassNode target) {
        if (target == null || !OWNER.equals(target.name) || target.interfaces.stream().filter(ACCESS::equals).count() != 1) return false;
        MethodNode version = method(target, VERSION, "()I");
        if (version == null || version.access != Opcodes.ACC_PUBLIC || !version.tryCatchBlocks.isEmpty()) return false;
        List<AbstractInsnNode> marker = instructions(version);
        if (marker.size() != 2 || marker.get(0).getOpcode() != Opcodes.ICONST_0 || marker.get(1).getOpcode() != Opcodes.IRETURN) return false;
        ClassNode restored = new ClassNode(); target.accept(restored);
        int[] counts = new int[REDIRECTS.size()];
        Set<String> handlers = new HashSet<>();
        for (String key : REVIEWED.get(OWNER).keySet()) {
            int split = key.indexOf('('); MethodNode nativeMethod = method(restored, key.substring(0, split), key.substring(split));
            if (nativeMethod == null) return false;
            for (AbstractInsnNode node : nativeMethod.instructions.toArray()) if (node instanceof MethodInsnNode invocation && OWNER.equals(invocation.owner)) {
                for (int i = 0; i < REDIRECTS.size(); i++) {
                    Redirect redirect = REDIRECTS.get(i);
                    if (!redirect.descriptor.equals(invocation.desc)) continue;
                    if (invocation.getOpcode() != Opcodes.INVOKESTATIC || invocation.itf
                            || !handler(target, invocation.name, redirect) || !handlers.add(invocation.name + invocation.desc)) return false;
                    if (!stripReviewedSpill(nativeMethod, invocation, redirect)) return false;
                    counts[i]++;
                    nativeMethod.instructions.set(invocation, new MethodInsnNode(redirect.opcode, redirect.owner, redirect.name, redirect.nativeDescriptor, false));
                }
            }
        }
        for (int count : counts) if (count != 1) return false;
        return supportedClass(restored);
    }
    /** Pinned Mixin0.8.7 spills/reloads arguments into these exact fresh native-local slots. */
    private static boolean stripReviewedSpill(MethodNode method, MethodInsnNode call, Redirect redirect) {
        int base = method.name.equals("destroyBlock") ? 11 : method.name.equals("removeBlock") ? 5 : -1;
        Type[] args = Type.getArgumentTypes(redirect.descriptor);
        if (base < 0 || method.maxLocals != base + args.length + 1) return false;
        List<AbstractInsnNode> prefix = new ArrayList<>();
        AbstractInsnNode cursor = call;
        for (int i = 0; i < args.length * 2; i++) {
            cursor = cursor.getPrevious();
            if (!(cursor instanceof VarInsnNode)) return false;
            prefix.addFirst(cursor);
        }
        for (int i = 0; i < args.length; i++) {
            int reversed = args.length - 1 - i;
            VarInsnNode store = (VarInsnNode) prefix.get(i), load = (VarInsnNode) prefix.get(args.length + i);
            if (store.var != base + reversed || store.getOpcode() != args[reversed].getOpcode(Opcodes.ISTORE)
                    || load.var != base + i || load.getOpcode() != args[i].getOpcode(Opcodes.ILOAD)) return false;
        }
        for (AbstractInsnNode insn : prefix) method.instructions.remove(insn);
        return true;
    }
    private static boolean handler(ClassNode owner, String name, Redirect redirect) {
        MethodNode handler = method(owner, name, redirect.descriptor);
        if (handler == null || handler.access != (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC) || !handler.tryCatchBlocks.isEmpty()) return false;
        List<AbstractInsnNode> body = instructions(handler);
        Type[] arguments = Type.getArgumentTypes(redirect.descriptor);
        if (body.size() != arguments.length + 2) return false;
        int slot = 0;
        for (int i = 0; i < arguments.length; i++) {
            if (!(body.get(i) instanceof VarInsnNode load) || load.var != slot || load.getOpcode() != arguments[i].getOpcode(Opcodes.ILOAD)) return false;
            slot += arguments[i].getSize();
        }
        if (!(body.get(arguments.length) instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESTATIC || call.itf
                || !HELPER.equals(call.owner) || !redirect.helper.equals(call.name) || !redirect.descriptor.equals(call.desc)) return false;
        return body.getLast().getOpcode() == Type.getReturnType(redirect.descriptor).getOpcode(Opcodes.IRETURN);
    }
    @Override public void postApply(String targetName, ClassNode target, String mixin, IMixinInfo info) {
        if (!OWNER.replace('/', '.').equals(targetName) || !MIXIN.equals(mixin)) return;
        boolean supported = supportedApplied(target);
        MethodNode version = method(target, VERSION, "()I");
        if (version != null) {
            version.instructions.clear(); version.instructions.add(new InsnNode(supported ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
            version.instructions.add(new InsnNode(Opcodes.IRETURN)); version.tryCatchBlocks.clear();
            if (version.localVariables != null) version.localVariables.clear();
            version.visibleLocalVariableAnnotations = null; version.invisibleLocalVariableAnnotations = null;
            version.maxStack = 1; version.maxLocals = 1;
        }
        if (!supported) warn("Applied mining seam failed exact three-redirect verification; NEW213 stays unavailable");
    }
    private static boolean field(ClassNode owner, String name, String desc, int access) {
        var fields = owner.fields.stream().filter(f -> name.equals(f.name)).toList();
        return fields.size() == 1 && fields.getFirst().desc.equals(desc) && fields.getFirst().access == access;
    }
    private static MethodNode method(ClassNode owner, String name, String desc) {
        if (owner == null) return null;
        var methods = owner.methods.stream().filter(m -> name.equals(m.name) && desc.equals(m.desc)).toList();
        return methods.size() == 1 ? methods.getFirst() : null;
    }
    private static List<AbstractInsnNode> instructions(MethodNode method) {
        List<AbstractInsnNode> instructions = new ArrayList<>();
        for (AbstractInsnNode node : method.instructions) if (node.getOpcode() >= 0) instructions.add(node);
        return instructions;
    }
    /** Stable symbolic fingerprint, independent of constant-pool ordering, labels and debug metadata. */
    public static String fingerprint(ClassNode item, String name, String descriptor) {
        return fingerprint(method(item, name, descriptor), 0);
    }
    private static String fingerprint(MethodNode method, int skip) {
        if (method == null || !method.tryCatchBlocks.isEmpty()
                || (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) return "unreviewed";
        Map<AbstractInsnNode, Integer> indices = new IdentityHashMap<>();
        int index = -skip;
        for (AbstractInsnNode node : method.instructions) { indices.put(node, index); if (node.getOpcode() >= 0) index++; }
        StringBuilder symbolic = new StringBuilder(method.name).append(method.desc).append('|').append(method.access).append('|');
        int seen = 0;
        for (AbstractInsnNode node : method.instructions) {
            if (node.getOpcode() < 0) continue;
            if (seen++ < skip) continue;
            symbolic.append(node.getOpcode()).append(':');
            if (node instanceof VarInsnNode n) symbolic.append(n.var);
            else if (node instanceof IntInsnNode n) symbolic.append(n.operand);
            else if (node instanceof TypeInsnNode n) symbolic.append(n.desc);
            else if (node instanceof FieldInsnNode n) symbolic.append(n.owner).append('.').append(n.name).append(n.desc);
            else if (node instanceof MethodInsnNode n) symbolic.append(n.owner).append('.').append(n.name).append(n.desc).append(n.itf);
            else if (node instanceof JumpInsnNode n) {
                Integer destination = indices.get(n.label);
                if (destination == null || destination < 0) return "unreviewed";
                symbolic.append(destination);
            } else if (node instanceof LdcInsnNode n) {
                if (!constant(symbolic, n.cst)) return "unreviewed";
            } else if (node instanceof IincInsnNode n) symbolic.append(n.var).append(',').append(n.incr);
            else if (node instanceof InvokeDynamicInsnNode n) {
                symbolic.append(n.name).append(n.desc);
                if (!constant(symbolic, n.bsm)) return "unreviewed";
                symbolic.append(n.bsmArgs.length).append('[');
                for (Object argument : n.bsmArgs) if (!constant(symbolic, argument)) return "unreviewed";
                symbolic.append(']');
            } else if (!(node instanceof InsnNode)) return "unreviewed";
            symbolic.append(';');
        }
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(symbolic.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static boolean constant(StringBuilder symbolic, Object constant) {
        String value;
        if (constant instanceof Handle handle) value = handle.getTag() + ":" + handle.getOwner() + "." + handle.getName() + handle.getDesc() + ":" + handle.isInterface();
        else if (constant instanceof Type type) value = type.getDescriptor();
        else if (constant instanceof String || constant instanceof Integer || constant instanceof Long
                || constant instanceof Float || constant instanceof Double) value = constant.toString();
        else return false;
        symbolic.append(constant.getClass().getName()).append(':').append(value.length()).append(':').append(value);
        return true;
    }
    private static void warn(String text) { System.getLogger(NativeMiningMixinPlugin.class.getName()).log(System.Logger.Level.WARNING, text); }
    @Override public void onLoad(String packageName) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
}
