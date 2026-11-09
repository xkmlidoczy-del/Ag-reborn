package cn.academy.port.highpower.client;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Read-only admission of the raw 1.21.1 / NeoForge 21.1.252 client travel dependencies.
 * This is an ASM proof helper, not a Mixin plugin or permission to apply assistance.
 * It neither loads Minecraft classes nor installs hooks, and accepts no transformed class.
 * Final wrapper/override admission is a separate, additional obligation.
 */
public final class WindPressureLiftTravelAdmission {
    public static final String MINECRAFT = "net/minecraft/client/Minecraft";
    public static final String TIMER = "net/minecraft/client/DeltaTracker$Timer";
    public static final String LOCAL_PLAYER = "net/minecraft/client/player/LocalPlayer";
    public static final String ABSTRACT_CLIENT_PLAYER = "net/minecraft/client/player/AbstractClientPlayer";
    public static final String CLIENT_LEVEL = "net/minecraft/client/multiplayer/ClientLevel";
    public static final String LEVEL = "net/minecraft/world/level/Level";
    public static final String ENTITY_TICK_LIST = "net/minecraft/world/level/entity/EntityTickList";
    public static final String PLAYER = "net/minecraft/world/entity/player/Player";
    public static final String LIVING_ENTITY = "net/minecraft/world/entity/LivingEntity";
    public static final String ENTITY = "net/minecraft/world/entity/Entity";
    public static final String VECTOR_GETTER_DESC = "()Lnet/minecraft/world/phys/Vec3;";
    public static final String RUN_TICK = "runTick", RUN_TICK_DESC = "(Z)V";
    public static final String ADVANCE_TIME = "advanceTime", ADVANCE_TIME_DESC = "(JZ)I";
    public static final String TICK = "tick", AI_STEP = "aiStep", VOID_DESC = "()V";
    public static final String TRAVEL = "travel", TRAVEL_DESC = "(Lnet/minecraft/world/phys/Vec3;)V";
    public static final String ENTITY_DESC = "(Lnet/minecraft/world/entity/Entity;)V";
    public static final String GUARD_ENTITY_TICK_DESC = "(Ljava/util/function/Consumer;Lnet/minecraft/world/entity/Entity;)V";
    public static final String FOR_EACH_DESC = "(Ljava/util/function/Consumer;)V";

    // Frozen from ClassReader(...).accept(node, 0) over the selected merged jar, then
    // ClassWriter(0). Unlike the upstream archive/class SHA-256s these are ASM tree
    // serialization fingerprints. The regression test pins the original bytes as well.
    private static final Map<String, String> CLASSES = Map.ofEntries(
            Map.entry(MINECRAFT, "2caecd93e07ff01e3a3f3a1aa532173d638c40dd3249f645937b900d998e55a8"),
            Map.entry(TIMER, "720c72f71d38651e2d2a8d535dd4ea92d219093eca76447b1b0ef6423dd42f8e"),
            Map.entry(LOCAL_PLAYER, "745b1da02755450aed2bfb465df91adf7d679057621a99b21387da71b2bd2f79"),
            Map.entry(ABSTRACT_CLIENT_PLAYER, "cef0aebc2d0b316b218579e3239fc843a2ca47de6c88cf26104525a995c8ea51"),
            Map.entry(CLIENT_LEVEL, "69f8e01fd74d1fb72a43df32d593c392dc93a31b8c23a7bf9c0f783a2bbc236e"),
            Map.entry(LEVEL, "275691261daf1b42da3b5ed82f46847aee33f253db42e2d2dfc47685d2b2cbf4"),
            Map.entry(ENTITY_TICK_LIST, "1174332d61774edec9e8d1d89c5cd5b4028cf5ae5ee7e8da8314495f73adfb00"),
            Map.entry(PLAYER, "a92e6bfb80610b4409fdeabd9093f08427a9ccd3d9c50ffdb48f47260f4720b3"),
            Map.entry(LIVING_ENTITY, "8c2f4deae6e0d5343882d41fede9381b17dcf0ee9e05f74f539210b1e58242ab"),
            Map.entry(ENTITY, "efd6bcb21d683d42e5b215bcb8211e23b40bbfca401f88008d7a8b1260b6155d"));

    private static final Map<String, String> METHODS = Map.ofEntries(
            Map.entry(MINECRAFT + "#runTick(Z)V", "a3150f308aac509f10aa14856667507c7b0493c87ba64db3e23a79befd1d99a4"),
            Map.entry(MINECRAFT + "#tick()V", "c838ecb003a22c0841aeee1ca368819ed5a9cc171fbeb684cccd1df89b329f4a"),
            Map.entry(TIMER + "#advanceTime(JZ)I", "ff91f3852331dcdb7dd03909f62352bbbe3293a6941e5199a895be10eaaf651e"),
            Map.entry(TIMER + "#advanceGameTime(J)I", "3341a2daff9769f50a1a175f75ab6777e12c5cda54115265ce552e33e396e4f0"),
            Map.entry(LOCAL_PLAYER + "#tick()V", "c64e4882e6789cf99a8f20c02c9929c324182871d65fa35f0fb07ff7d44f6c97"),
            Map.entry(LOCAL_PLAYER + "#aiStep()V", "f3bf4db1d4fbe4acce1d0c57249c1a65bc26c0e7de9519fef750da782c4d0dcb"),
            Map.entry(LOCAL_PLAYER + "#serverAiStep()V", "8da12bc5a3cea9787fb8406d1d91055c929b4cd8a0c48703fc204b08a2017676"),
            Map.entry(ABSTRACT_CLIENT_PLAYER + "#tick()V", "aaf70e3e9e31f9fdb836b6a3cabd195df3d627602ed9d55b603c21364047335e"),
            Map.entry(CLIENT_LEVEL + "#tickEntities()V", "93eeff006121eee36b304241c47535653c086530dc13b82add98212ecb580992"),
            Map.entry(CLIENT_LEVEL + "#tickNonPassenger" + ENTITY_DESC, "011bb266bf426c6da39de28f5378c68de458d8c89edc7606d281428c6e0a3d66"),
            Map.entry(CLIENT_LEVEL + "#lambda$tickEntities$4" + ENTITY_DESC, "5fa9e007f495b357be75957db1d8b28e7b37a4edd32322f93b9d76f7a738eac1"),
            Map.entry(LEVEL + "#guardEntityTick" + GUARD_ENTITY_TICK_DESC, "f6e72baf590b5f144e7815f37cc47d7385a8564c8f1f6008e68269dfffbc99ff"),
            Map.entry(ENTITY_TICK_LIST + "#forEach" + FOR_EACH_DESC, "10745e2beed6c5de7234adb9751d66fd0a1b29c2ed903787d1bf4f8ceb69642e"),
            Map.entry(PLAYER + "#tick()V", "b44ac8bad665913f911e4935150e0d3f3c951e07410a7eff076c319791d8a385"),
            Map.entry(PLAYER + "#aiStep()V", "2e1063b73d1fb46c5356f3cb245516e709c7c29a437ad0e452e676bcb69de1cd"),
            Map.entry(PLAYER + "#serverAiStep()V", "a3687f3f7822583246e2e6eda02874bdb1525786e1f40c9df7fcb422b0a0ea91"),
            Map.entry(PLAYER + "#travel" + TRAVEL_DESC, "eb7efe055fb17ab526984020100fb764da733ceb0605f893ee27b0f08ba6bd26"),
            Map.entry(LIVING_ENTITY + "#tick()V", "fdd70d67f013bbadce4825a21666122dd9c6e45d36908138effe3d8094c8dff3"),
            Map.entry(LIVING_ENTITY + "#aiStep()V", "296d75207a798a52a1d2fe10f716cf31b8ec296206d2792f1ca7a5399cf37189"),
            Map.entry(LIVING_ENTITY + "#travel" + TRAVEL_DESC, "06c662a5b2c7540f01a59a7a0f7a20da8865d972b3684a36ac7893938b78dd20"),
            Map.entry(LIVING_ENTITY + "#handleRelativeFrictionAndCalculateMovement(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;", "07885ec3762fa3b1b5c0885522c947ce31ec5a6e33139605ca43c0e982e7e930"),
            Map.entry(ENTITY + "#position" + VECTOR_GETTER_DESC, "f10e5b0eccc32a3bbc788268b6af261d912e4a07448c718c46dfb2d9e82bb66a"),
            Map.entry(ENTITY + "#getDeltaMovement" + VECTOR_GETTER_DESC, "2329393dc70039d383861f4730775542890fef1c5c0f2d0280be869a80bb8e16"),
            Map.entry(ENTITY + "#setDeltaMovement" + TRAVEL_DESC, "e282f423683bfbb9313cc3e71dad68647c99d922e49d04e5ad1224796b5a5247"));

    private WindPressureLiftTravelAdmission() {}

    /** Required keys are JVM internal names (slashes), never reflected Class identities. */
    public static Set<String> requiredClasses() { return CLASSES.keySet(); }
    static String expectedClassFingerprint(String owner) { return CLASSES.getOrDefault(owner,"unreviewed"); }
    public static Set<String> reviewedMethods() { return METHODS.keySet(); }

    public static boolean supported(Map<String, ClassNode> classes) {
        if (classes == null) return false;
        for (String owner : requiredClasses()) {
            ClassNode node = classes.get(owner);
            if (node == null || !owner.equals(node.name) || !classSupported(node)) return false;
        }
        for (String key : reviewedMethods()) {
            int hash = key.indexOf('#'), descriptor = key.indexOf('(', hash);
            if (!nativeMethodSupported(classes.get(key.substring(0, hash)),
                    key.substring(hash + 1, descriptor), key.substring(descriptor))) return false;
        }
        for (String owner : Set.of(LOCAL_PLAYER, ABSTRACT_CLIENT_PLAYER, PLAYER, LIVING_ENTITY))
            if (!inheritsPlainStateAccess(classes.get(owner))) return false;
        return runTickSupported(classes.get(MINECRAFT)) && localPlayerSupported(classes.get(LOCAL_PLAYER))
                && PLAYER.equals(classes.get(ABSTRACT_CLIENT_PLAYER).superName)
                && classes.get(ABSTRACT_CLIENT_PLAYER).methods.stream().noneMatch(m -> m.name.equals(TRAVEL) && m.desc.equals(TRAVEL_DESC))
                && LIVING_ENTITY.equals(classes.get(PLAYER).superName);
    }

    /** The exact LocalPlayer chain must reach Entity's pinned callback-free getters/setter. */
    public static boolean inheritsPlainStateAccess(ClassNode node) {
        return node != null && Set.of(LOCAL_PLAYER, ABSTRACT_CLIENT_PLAYER, PLAYER, LIVING_ENTITY).contains(node.name)
                && node.methods.stream().noneMatch(m -> (m.name.equals("position") || m.name.equals("getDeltaMovement")) && m.desc.equals(VECTOR_GETTER_DESC)
                || m.name.equals("setDeltaMovement") && m.desc.equals(TRAVEL_DESC));
    }

    public static boolean classSupported(ClassNode node) {
        return node != null && CLASSES.containsKey(node.name) && CLASSES.get(node.name).equals(classFingerprint(node));
    }

    public static boolean nativeMethodSupported(ClassNode node, String name, String descriptor) {
        if (node == null) return false;
        String expected = METHODS.get(node.name + "#" + name + descriptor);
        return expected != null && expected.equals(fingerprint(node, name, descriptor));
    }

    public static boolean localPlayerSupported(ClassNode node) {
        return node != null && LOCAL_PLAYER.equals(node.name) && ABSTRACT_CLIENT_PLAYER.equals(node.superName)
                && node.methods.stream().noneMatch(m -> m.name.equals(TRAVEL) && m.desc.equals(TRAVEL_DESC))
                && nativeMethodSupported(node, AI_STEP, VOID_DESC) && nativeMethodSupported(node, TICK, VOID_DESC)
                && nativeMethodSupported(node, "serverAiStep", VOID_DESC);
    }

    public static boolean runTickSupported(ClassNode node) {
        return node != null && MINECRAFT.equals(node.name) && runTickLoopSupported(node)
                && nativeMethodSupported(node, RUN_TICK, RUN_TICK_DESC);
    }

    /**
     * Explicit native data-flow witness, independent of the complete method fingerprint.
     * The unique Timer result is stored in slot 3; the unchanged boolean gates the batch;
     * slot 4 runs from zero while it is below min(10, slot 3), with one tick per iteration.
     * Full method/class fingerprints additionally reject all other edits and control-flow changes.
     */
    public static boolean runTickLoopSupported(ClassNode node) {
        if (node == null || !MINECRAFT.equals(node.name)) return false;
        MethodNode method = uniqueMethod(node, RUN_TICK, RUN_TICK_DESC);
        if (method == null || method.access != Opcodes.ACC_PRIVATE) return false;
        List<AbstractInsnNode> code = operations(method);
        int advance = uniqueCall(code, TIMER, ADVANCE_TIME);
        int bound = uniqueCall(code, "java/lang/Math", "min");
        int tick = uniqueCall(code, MINECRAFT, TICK);
        int tasks = uniqueCall(code, MINECRAFT, "runAllTasks");
        if (advance < 4 || advance + 3 >= code.size() || bound < 5 || bound + 1 >= code.size()
                || tick <= bound || tick + 3 >= code.size() || tasks <= advance || tasks >= bound - 5) return false;
        if (!call(code.get(advance), Opcodes.INVOKEVIRTUAL, TIMER, ADVANCE_TIME, ADVANCE_TIME_DESC)
                || !variable(code.get(advance - 4), Opcodes.ALOAD, 0)
                || !(code.get(advance - 3) instanceof FieldInsnNode timer) || timer.getOpcode() != Opcodes.GETFIELD
                || !timer.owner.equals(MINECRAFT) || !timer.name.equals("timer") || !timer.desc.equals("L" + TIMER + ";")
                || !call(code.get(advance - 2), Opcodes.INVOKESTATIC, "net/minecraft/Util", "getMillis", "()J")
                || !variable(code.get(advance - 1), Opcodes.ILOAD, 1)
                || !variable(code.get(advance + 1), Opcodes.ISTORE, 3)
                || !variable(code.get(advance + 2), Opcodes.ILOAD, 1)
                || !(code.get(advance + 3) instanceof JumpInsnNode gate) || gate.getOpcode() != Opcodes.IFEQ
                || !call(code.get(tasks), Opcodes.INVOKEVIRTUAL, MINECRAFT, "runAllTasks", VOID_DESC)
                || code.get(bound - 5).getOpcode() != Opcodes.ICONST_0
                || !variable(code.get(bound - 4), Opcodes.ISTORE, 4)
                || !variable(code.get(bound - 3), Opcodes.ILOAD, 4)
                || !(code.get(bound - 2) instanceof IntInsnNode cap) || cap.getOpcode() != Opcodes.BIPUSH || cap.operand != 10
                || !variable(code.get(bound - 1), Opcodes.ILOAD, 3)
                || !call(code.get(bound), Opcodes.INVOKESTATIC, "java/lang/Math", "min", "(II)I")
                || !(code.get(bound + 1) instanceof JumpInsnNode exit) || exit.getOpcode() != Opcodes.IF_ICMPGE
                || !variable(code.get(tick - 1), Opcodes.ALOAD, 0)
                || !call(code.get(tick), Opcodes.INVOKEVIRTUAL, MINECRAFT, TICK, VOID_DESC)
                || !(code.get(tick + 1) instanceof IincInsnNode increment) || increment.var != 4 || increment.incr != 1
                || !(code.get(tick + 2) instanceof JumpInsnNode back) || back.getOpcode() != Opcodes.GOTO
                || nextOperation(back.label) != code.get(bound - 3)
                || nextOperation(exit.label) != code.get(tick + 3)
                || code.indexOf(nextOperation(gate.label)) <= tick + 3) return false;
        for (int i = advance + 2; i <= tick + 2; i++) {
            AbstractInsnNode instruction = code.get(i);
            if (instruction instanceof VarInsnNode variable && variable.var == 3 && variable.getOpcode() == Opcodes.ISTORE
                    || instruction instanceof IincInsnNode sourceIncrement && sourceIncrement.var == 3) return false;
        }
        return true;
    }

    /** Exact ASM class-tree serialization; source/debug metadata is deliberately retained. */
    public static String classFingerprint(ClassNode node) {
        if (node == null) return "unreviewed";
        try {
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            return sha256(writer.toByteArray());
        } catch (RuntimeException invalid) { return "unreviewed"; }
    }

    /** Exact unique method, including access, descriptors, instructions, handlers and maxima. */
    public static String fingerprint(ClassNode node, String name, String descriptor) {
        if (node == null) return "unreviewed";
        try {
            MethodNode original = uniqueMethod(node, name, descriptor);
            if (original == null) return "unreviewed";
            MethodNode copy = new MethodNode(Opcodes.ASM9, original.access, original.name, original.desc,
                    original.signature, original.exceptions.toArray(String[]::new));
            original.accept(copy);
            for (AbstractInsnNode instruction : copy.instructions.toArray())
                if (instruction instanceof FrameNode || instruction instanceof LineNumberNode) copy.instructions.remove(instruction);
            copy.localVariables = null;
            copy.visibleLocalVariableAnnotations = null;
            copy.invisibleLocalVariableAnnotations = null;
            ClassWriter writer = new ClassWriter(0);
            writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "WindPressureLiftNativeMethodWitness", null, "java/lang/Object", null);
            copy.accept(writer);
            writer.visitEnd();
            return sha256(writer.toByteArray());
        } catch (RuntimeException invalid) { return "unreviewed"; }
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }

    private static MethodNode uniqueMethod(ClassNode node, String name, String descriptor) {
        List<MethodNode> matches = node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static List<AbstractInsnNode> operations(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() >= 0) result.add(instruction);
        return result;
    }

    private static int uniqueCall(List<AbstractInsnNode> code, String owner, String name) {
        int result = -1;
        for (int i = 0; i < code.size(); i++) if (code.get(i) instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name)) {
            if (result >= 0) return -1;
            result = i;
        }
        return result;
    }

    private static boolean call(AbstractInsnNode instruction, int opcode, String owner, String name, String descriptor) {
        return instruction instanceof MethodInsnNode call && call.getOpcode() == opcode && call.owner.equals(owner)
                && call.name.equals(name) && call.desc.equals(descriptor) && !call.itf;
    }

    private static boolean variable(AbstractInsnNode instruction, int opcode, int slot) {
        return instruction instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == slot;
    }

    private static AbstractInsnNode nextOperation(AbstractInsnNode instruction) {
        while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getNext();
        return instruction;
    }
}
