package cn.academy.port.highpower.mixin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Bytecode-only, NEW133-only admission for the pinned 1.21.1 / NeoForge 21.1.252
 * ItemEntity merge path. No Minecraft or Academy gameplay classes are loaded here.
 */
public final class NativeItemMergeMixinPlugin implements IMixinConfigPlugin {
    private static final String ITEM = "net/minecraft/world/entity/item/ItemEntity";
    private static final String STACK = "Lnet/minecraft/world/item/ItemStack;";
    private static final String ENTITY = "L" + ITEM + ";";
    private static final String MERGE = "(" + ENTITY + STACK + ENTITY + STACK + ")V";
    private static final String CALLBACK = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String HANDLER = "(" + ENTITY + STACK + ENTITY + STACK + "L" + CALLBACK + ";)V";
    private static final String ACCESS = "cn/academy/port/highpower/NativeItemMergeAccess";
    private static final String VERSION = "academy$nativeItemMergeContractVersion";
    private static final String HELPER = "cn/academy/port/highpower/MagneticItemProvenance";
    private static final String MIXIN = "cn.academy.port.highpower.mixin.ItemEntityProvenanceMixin";

    // These hashes include method names, descriptors, access flags, every semantic
    // instruction, constants, bootstrap handles and exact branch destinations.
    private static final Map<String, String> REVIEWED = reviewed();
    private static Map<String, String> reviewed() {
        var hashes = new LinkedHashMap<String, String>();
        hashes.put("merge" + MERGE, "95a52f40e6e9c01dbdf9a0db1c8dacab7d8e3ef5302e5e3f62bc9eec18b6cbe7");
        hashes.put("merge(" + ENTITY + STACK + STACK + ")V", "861f089f3111fb9ca5f1dbfc97aec57c506dd7e3b62e1568dd9d57e24d1a8c26");
        hashes.put("merge(" + STACK + STACK + "I)" + STACK, "31608ab4559dcb257c9f0ce73202a58d7b861ed0d17a57551a0f02e79d690539");
        hashes.put("tryToMerge(" + ENTITY + ")V", "d42842a03f8604f572e9549c37b6902fb74c3dffc15ceb568089167e926af0ba");
        hashes.put("areMergable(" + STACK + STACK + ")Z", "c33b51592a1f57995c90999217ecb3acbee143fe09ab9d9a6cb86574013d9fd7");
        hashes.put("isMergable()Z", "9224990d3523b6574695494152a0ddebf5c69bdaca2676c6db569d6c0dd6b4fb");
        hashes.put("mergeWithNeighbours()V", "0d6a46983977b4725da289eca4e56daa0e202ad0ceadc4bf9381f8bf3d0c1468");
        hashes.put("lambda$mergeWithNeighbours$0(" + ENTITY + ")Z", "21d4e3e3d85fe7e150fdf9d1e110d19456dd31992928bb2202fa70f826bbe871");
        hashes.put("getItem()" + STACK, "30fbf1599f175f6808b11e5dede54e74e7556dc61be0d1b2a27e5158af03361e");
        hashes.put("setItem(" + STACK + ")V", "5a39892a84b732d54ab8d80aae070c5a856e0a75db961430dcc0aa9d84909b3d");
        return Map.copyOf(hashes);
    }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!ITEM.replace('/', '.').equals(targetClassName) || !MIXIN.equals(mixinClassName)) return false;
        try {
            boolean admitted = supportedItem(MixinService.getService().getBytecodeProvider().getClassNode(targetClassName));
            if (!admitted) warn("Native ItemEntity merge differs from the reviewed 1.21.1/21.1.252 seam; NEW133 stays unavailable");
            return admitted;
        } catch (Exception | LinkageError unavailable) {
            warn("Cannot verify native ItemEntity merge; NEW133 stays unavailable: " + unavailable);
            return false;
        }
    }

    public static boolean supportedItem(ClassNode item) { return supported(item, false); }

    private static boolean supported(ClassNode item, boolean applied) {
        if (item == null || !ITEM.equals(item.name) || !"net/minecraft/world/entity/Entity".equals(item.superName)
                || !field(item, "age", "I", Opcodes.ACC_PRIVATE)
                || !field(item, "pickupDelay", "I", Opcodes.ACC_PRIVATE)
                || !field(item, "target", "Ljava/util/UUID;", Opcodes.ACC_PRIVATE)
                || !field(item, "DATA_ITEM", "Lnet/minecraft/network/syncher/EntityDataAccessor;",
                          Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) return false;
        for (var expected : REVIEWED.entrySet()) {
            String key = expected.getKey();
            int split = key.indexOf('(');
            MethodNode method = method(item, key.substring(0, split), key.substring(split));
            if (method == null) return false;
            int skip = applied && key.equals("merge" + MERGE) ? injectedPrefixLength(item, method) : 0;
            if (skip < 0 || !expected.getValue().equals(fingerprint(method, skip))) return false;
        }
        return true;
    }

    /** Verifies the transformed seam, rather than trusting a marker or an injection annotation. */
    public static boolean supportedAppliedItem(ClassNode item) {
        if (item == null || item.interfaces == null || item.interfaces.stream().filter(ACCESS::equals).count() != 1) return false;
        MethodNode version = method(item, VERSION, "()I");
        if (version == null || version.access != Opcodes.ACC_PUBLIC || !version.tryCatchBlocks.isEmpty()) return false;
        List<AbstractInsnNode> body = instructions(version);
        if (body.size() != 2 || body.get(0).getOpcode() != Opcodes.ICONST_0 || body.get(1).getOpcode() != Opcodes.IRETURN) return false;
        return supported(item, true);
    }

    /** Returns the exact non-cancelling Mixin HEAD prefix size, or refuses it. */
    private static int injectedPrefixLength(ClassNode item, MethodNode merge) {
        List<AbstractInsnNode> code = instructions(merge);
        if (code.size() < 7) return -1;
        for (int i = 0; i < 4; i++) if (!load(code.get(i), i)) return -1;
        // The pinned sponge-mixin 0.15.2 / Mixin 0.8.7 optimizes an unused
        // CallbackInfo argument to null. The exact handler below never reads it.
        if (code.get(4).getOpcode() != Opcodes.ACONST_NULL
                || !(code.get(5) instanceof MethodInsnNode invocation) || invocation.getOpcode() != Opcodes.INVOKESTATIC
                || invocation.itf || !ITEM.equals(invocation.owner) || !HANDLER.equals(invocation.desc)) return -1;
        MethodNode handler = method(item, invocation.name, invocation.desc);
        if (handler == null || handler.access != (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)
                || !handler.tryCatchBlocks.isEmpty()) return -1;
        List<AbstractInsnNode> body = instructions(handler);
        if (body.size() != 6) return -1;
        for (int i = 0; i < 4; i++) if (!load(body.get(i), i)) return -1;
        if (!call(body.get(4), Opcodes.INVOKESTATIC, HELPER, "beforeMerge", MERGE)
                || body.get(5).getOpcode() != Opcodes.RETURN) return -1;
        return 6;
    }

    @Override public void postApply(String name, ClassNode target, String mixin, IMixinInfo info) {
        if (!ITEM.replace('/', '.').equals(name) || !MIXIN.equals(mixin)) return;
        boolean admitted = supportedAppliedItem(target);
        MethodNode version = method(target, VERSION, "()I");
        if (version != null && version.access == Opcodes.ACC_PUBLIC) {
            // No runtime/plugin class dependency: publish a bytecode constant only.
            version.instructions.clear();
            version.instructions.add(new InsnNode(admitted ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
            version.instructions.add(new InsnNode(Opcodes.IRETURN));
            version.tryCatchBlocks.clear();
            if (version.localVariables != null) version.localVariables.clear();
            version.visibleLocalVariableAnnotations = null;
            version.invisibleLocalVariableAnnotations = null;
            version.maxStack = 1;
            version.maxLocals = 1;
        }
        if (!admitted) warn("Applied ItemEntity provenance hook did not match the reviewed non-cancelling HEAD contract; NEW133 stays unavailable");
    }

    private static boolean field(ClassNode owner, String name, String descriptor, int access) {
        List<FieldNode> found = owner.fields.stream().filter(f -> name.equals(f.name)).toList();
        return found.size() == 1 && descriptor.equals(found.getFirst().desc) && found.getFirst().access == access;
    }
    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        if (owner == null) return null;
        List<MethodNode> found = owner.methods.stream().filter(m -> name.equals(m.name) && descriptor.equals(m.desc)).toList();
        return found.size() == 1 ? found.getFirst() : null;
    }
    private static List<AbstractInsnNode> instructions(MethodNode method) {
        var code = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode node : method.instructions) if (node.getOpcode() >= 0) code.add(node);
        return code;
    }
    private static boolean load(AbstractInsnNode node, int slot) {
        return node instanceof VarInsnNode variable && variable.getOpcode() == Opcodes.ALOAD && variable.var == slot;
    }
    private static boolean call(AbstractInsnNode node, int opcode, String owner, String name, String descriptor) {
        return node instanceof MethodInsnNode invocation && invocation.getOpcode() == opcode && !invocation.itf
                && owner.equals(invocation.owner) && name.equals(invocation.name) && descriptor.equals(invocation.desc);
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
    private static void warn(String message) { System.getLogger(NativeItemMergeMixinPlugin.class.getName()).log(System.Logger.Level.WARNING, message); }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
}
