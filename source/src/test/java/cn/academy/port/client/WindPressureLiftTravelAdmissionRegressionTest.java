package cn.academy.port.client;

import static cn.academy.port.highpower.client.WindPressureLiftTravelAdmission.*;

import cn.academy.port.highpower.client.WindPressureLiftTravelAdmission;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Pure destructive controls against the actual untransformed native archive, without
 * linking any Minecraft class, starting Mixin, invoking Gradle, or writing the archive.
 *
 * Derivation of the frozen admission constants (2026-10-14): select the merged archive
 * below, first check its SHA-256 and the ten raw class-entry SHA-256s below, parse each
 * entry with ClassReader.accept(node, 0), and record classFingerprint(node) and
 * fingerprint(node, name, descriptor) for the explicit reviewedMethods() set. This was
 * compiled/run with .tools/jdk-21.0.12.1+1 and cached ASM/asm-tree 9.10.1, with all output
 * under /tmp. Class fingerprints preserve the whole original tree. Method fingerprints
 * retain access, name, descriptors, signature, exceptions, code, handlers and maxima;
 * only frame/line/local-debug nodes are omitted from the separate method serialization.
 * These are new independent constants, never replacements for existing proof hashes.
 *
 * Supplying an alternative jar is a negative test; mismatching native bytes fail before
 * admission. No switch exists to derive, rewrite or accept new hashes during this test.
 */
public final class WindPressureLiftTravelAdmissionRegressionTest {
    private static final String JAR_SHA256 = "8c94c9cbe4846ddff2070355d68e26ed3d0bb8ec92b5ee34da5780384de16146";
    private static final Map<String, String> RAW = Map.ofEntries(
            Map.entry(MINECRAFT, "495f5dc97d07476e23835fcdd034019a020a58b980f7c764d4953ce4ed16d283"),
            Map.entry(TIMER, "80f450da74c2a332fe3ccd963dbaa8a43f70f5c2f8df9af8437007d2d83d8050"),
            Map.entry(LOCAL_PLAYER, "eb654ae20e435ef1433eafcf208d43b3844b92925fdf1080c33bad117d19f038"),
            Map.entry(ABSTRACT_CLIENT_PLAYER, "ff0dcdd2ee4c51d61f09f0d5a58c96fdb7fa4bc58d6b345a2e2a49541b2c0146"),
            Map.entry(CLIENT_LEVEL, "75ba059841ae6e711a83a686665ffd7980f028887af2b965f5a86380603973cd"),
            Map.entry(LEVEL, "b1537f7158047988447618650612f84596030e86952cb888dd91e70ba4214518"),
            Map.entry(ENTITY_TICK_LIST, "99d802fcaac08dafa860d1e47ce519a0362bc4bef4262098d2922d8446365efd"),
            Map.entry(PLAYER, "277a886c7267e82baa4378282e3c892f16338c0a6a779f1cf361a0af7d7b09fd"),
            Map.entry(LIVING_ENTITY, "52130dfb8dc4b5c48ec1ef12bf19b40ff29f6e38fc25b02b94202553f75ce5f4"),
            Map.entry(ENTITY, "4f65b7d5d4628e9f10a6a23e5a0f68814520d8019f4bc001924cd752c00eed84"));
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path jarPath = args.length == 0 ? Path.of("build/moddev/artifacts/neoforge-21.1.252-merged.jar") : Path.of(args[0]);
        check(JAR_SHA256.equals(digest(jarPath)), "selected unmodified merged archive SHA-256");
        Map<String, ClassNode> raw = load(jarPath);
        Map<String, String> originals = new HashMap<>();
        raw.forEach((owner, node) -> originals.put(owner, classFingerprint(node)));
        check(requiredClasses().equals(RAW.keySet()), "exact ten required native classes");
        check(reviewedMethods().size() == 24, "explicit reviewed native method set");
        check(supported(raw), "actual pinned native archive admitted");
        check(runTickLoopSupported(raw.get(MINECRAFT)), "actual Timer result reaches the reviewed min(10,i) tick loop");
        check(runTickSupported(raw.get(MINECRAFT)), "exact runTick fingerprint and loop admitted");
        check(localPlayerSupported(raw.get(LOCAL_PLAYER)), "native LocalPlayer inheritance and aiStep admitted");
        check(!supported(null) && !supported(Map.of()), "absent dependency maps refused");
        check(!classSupported(null) && !runTickSupported(null) && !runTickLoopSupported(null)
                && !localPlayerSupported(null) && !nativeMethodSupported(null, TICK, VOID_DESC), "absent single classes refused");
        check(fingerprint(null, TICK, VOID_DESC).equals("unreviewed"), "missing fingerprint cannot masquerade as a digest");
        check(!nativeMethodSupported(raw.get(MINECRAFT), "unreviewedMethod", VOID_DESC), "unreviewed method cannot be admitted");
        for (String owner : new TreeSet<>(requiredClasses())) classControls(raw, owner);
        for (String key : new TreeSet<>(reviewedMethods())) methodControls(raw, key);
        runTickControls(raw);
        travelControls(raw);
        stateAccessControls(raw);
        pureHelper();
        for (String owner : originals.keySet()) check(originals.get(owner).equals(classFingerprint(raw.get(owner))),
                "all admission and mutation tests leave the original tree unchanged: " + owner);
        check(supported(raw), "native originals still admitted after all destructive copies");
        check(JAR_SHA256.equals(digest(jarPath)), "shared native archive remains byte-for-byte unchanged");
        System.out.println("PASS WindPressureLiftTravelAdmissionRegressionTest: " + checks
                + " checks; ten raw classes and 24 native methods pinned; no runtime hook or transformed-wrapper claim");
    }

    private static Map<String, ClassNode> load(Path jarPath) throws Exception {
        Map<String, ClassNode> result = new HashMap<>();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            for (String owner : new TreeSet<>(RAW.keySet())) {
                var entry = jar.getJarEntry(owner + ".class");
                check(entry != null, "required raw archive entry: " + owner);
                byte[] bytes;
                try (InputStream input = jar.getInputStream(entry)) { bytes = input.readAllBytes(); }
                check(RAW.get(owner).equals(sha256(bytes)), "frozen raw class SHA-256: " + owner);
                ClassNode node = new ClassNode();
                new ClassReader(bytes).accept(node, 0);
                result.put(owner, node);
            }
        }
        return result;
    }

    private static void classControls(Map<String, ClassNode> raw, String owner) {
        check(classSupported(raw.get(owner)), "exact class tree admitted: " + owner);
        Map<String, ClassNode> absent = new HashMap<>(raw);
        absent.remove(owner);
        check(!supported(absent), "missing dependency refused: " + owner);
        absent.put(owner, null);
        check(!supported(absent), "null dependency refused: " + owner);
        corruptClass(raw, owner, node -> node.name += "$Other", "wrong class identity");
        corruptClass(raw, owner, node -> node.superName = "unreviewed/Parent", "changed superclass");
        corruptClass(raw, owner, node -> node.access ^= Opcodes.ACC_FINAL, "changed class access");
        corruptClass(raw, owner, node -> node.interfaces.add("unreviewed/Interface"), "added interface");
        corruptClass(raw, owner, node -> node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "unreviewedState", "I", null, null)), "added native field");
        corruptClass(raw, owner, node -> node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "unreviewedWrapper", VOID_DESC, null, null)), "added native wrapper");
    }

    private static void methodControls(Map<String, ClassNode> raw, String key) {
        int hash = key.indexOf('#'), descriptor = key.indexOf('(', hash);
        String owner = key.substring(0, hash), name = key.substring(hash + 1, descriptor), desc = key.substring(descriptor);
        check(nativeMethodSupported(raw.get(owner), name, desc), "exact native method: " + key);
        corruptMethod(raw, owner, name, desc, method -> method.instructions.insert(new InsnNode(Opcodes.NOP)), "extra native instruction");
        corruptMethod(raw, owner, name, desc, method -> method.access ^= Opcodes.ACC_SYNCHRONIZED, "native access changed");
        corruptMethod(raw, owner, name, desc, method -> method.maxStack++, "native stack limit changed");
        corruptMethod(raw, owner, name, desc, method -> {
            if (!method.tryCatchBlocks.isEmpty()) method.tryCatchBlocks.getFirst().type =
                    "java/lang/Throwable".equals(method.tryCatchBlocks.getFirst().type) ? "java/lang/Exception" : "java/lang/Throwable";
            else {
                LabelNode start = new LabelNode(), end = new LabelNode();
                method.instructions.insert(start);
                method.instructions.add(end);
                method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, start, "java/lang/Throwable"));
            }
        }, "exception handler changed");
        ClassNode missing = copy(raw.get(owner));
        missing.methods.removeIf(method -> method.name.equals(name) && method.desc.equals(desc));
        check(!nativeMethodSupported(missing, name, desc), "missing native method refused: " + key);
        ClassNode duplicate = copy(raw.get(owner));
        duplicate.methods.add(copy(method(duplicate, name, desc)));
        check(!nativeMethodSupported(duplicate, name, desc), "duplicate native method refused: " + key);
    }

    private static void runTickControls(Map<String, ClassNode> raw) {
        corruptLoop(raw, node -> call(runTick(node), TIMER, ADVANCE_TIME).desc = "(J)I", "Timer descriptor");
        corruptLoop(raw, node -> call(runTick(node), TIMER, ADVANCE_TIME).owner = "unreviewed/Timer", "Timer owner");
        corruptLoop(raw, node -> call(runTick(node), TIMER, ADVANCE_TIME).setOpcode(Opcodes.INVOKESTATIC), "Timer opcode");
        corruptLoop(raw, node -> call(runTick(node), TIMER, ADVANCE_TIME).itf = true, "Timer interface dispatch");
        corruptLoop(raw, node -> {
            MethodNode method = runTick(node);
            MethodInsnNode advance = call(method, TIMER, ADVANCE_TIME);
            method.instructions.insertBefore(advance, copy(advance));
        }, "duplicate Timer invocation");
        corruptLoop(raw, node -> ((VarInsnNode)next(call(runTick(node), TIMER, ADVANCE_TIME))).var = 6, "Timer result store slot");
        corruptLoop(raw, node -> ((VarInsnNode)previous(call(runTick(node), TIMER, ADVANCE_TIME))).var = 2, "Timer boolean argument");
        corruptLoop(raw, node -> ((VarInsnNode)next(next(call(runTick(node), TIMER, ADVANCE_TIME)))).var = 2, "batch gate boolean");
        corruptLoop(raw, node -> ((JumpInsnNode)next(next(next(call(runTick(node), TIMER, ADVANCE_TIME))))).setOpcode(Opcodes.IFNE), "batch gate predicate");
        corruptLoop(raw, node -> ((IntInsnNode)previous(previous(call(runTick(node), "java/lang/Math", "min")))).operand = 11, "loop cap");
        corruptLoop(raw, node -> ((VarInsnNode)previous(call(runTick(node), "java/lang/Math", "min"))).var = 5, "min source local");
        corruptLoop(raw, node -> call(runTick(node), "java/lang/Math", "min").name = "max", "min replaced with max");
        corruptLoop(raw, node -> ((JumpInsnNode)next(call(runTick(node), "java/lang/Math", "min"))).setOpcode(Opcodes.IF_ICMPGT), "loop branch predicate");
        corruptLoop(raw, node -> ((VarInsnNode)previous(previous(previous(call(runTick(node), "java/lang/Math", "min"))))).var = 5, "loop index load");
        corruptLoop(raw, node -> {
            MethodNode method = runTick(node);
            MethodInsnNode min = call(method, "java/lang/Math", "min");
            AbstractInsnNode zero = previous(previous(previous(previous(previous(min)))));
            method.instructions.set(zero, new InsnNode(Opcodes.ICONST_1));
        }, "loop initial index");
        corruptLoop(raw, node -> ((IincInsnNode)next(call(runTick(node), MINECRAFT, TICK))).incr = 2, "loop increment");
        corruptLoop(raw, node -> ((IincInsnNode)next(call(runTick(node), MINECRAFT, TICK))).var = 3, "loop increment writes clock result");
        corruptLoop(raw, node -> {
            MethodNode method = runTick(node);
            method.instructions.insertBefore(call(method, MINECRAFT, "runAllTasks"), new IincInsnNode(3, 1));
        }, "clock result modified before min");
        corruptLoop(raw, node -> {
            MethodNode method = runTick(node);
            LabelNode target = new LabelNode();
            method.instructions.insertBefore(call(method, MINECRAFT, TICK), target);
            ((JumpInsnNode)next(next(call(method, MINECRAFT, TICK)))).label = target;
        }, "loop back edge target");
        corruptLoop(raw, node -> {
            MethodNode method = runTick(node);
            JumpInsnNode back = (JumpInsnNode)next(next(call(method, MINECRAFT, TICK)));
            ((JumpInsnNode)next(call(method, "java/lang/Math", "min"))).label = back.label;
        }, "loop exit target");
        corruptLoop(raw, node -> call(runTick(node), MINECRAFT, TICK).desc = "(Z)V", "tick descriptor");
        corruptLoop(raw, node -> ((VarInsnNode)previous(call(runTick(node), MINECRAFT, TICK))).var = 2, "tick receiver");
        corruptLoop(raw, node -> call(runTick(node), MINECRAFT, TICK).setOpcode(Opcodes.INVOKESPECIAL), "tick dispatch");
        corruptLoop(raw, node -> {
            MethodNode method = runTick(node);
            method.instructions.remove(call(method, MINECRAFT, TICK));
        }, "missing tick invocation");
        corruptLoop(raw, node -> {
            MethodNode method = runTick(node);
            MethodInsnNode tick = call(method, MINECRAFT, TICK);
            method.instructions.insert(tick, copy(tick));
        }, "duplicate tick invocation");
        corruptLoop(raw, node -> {
            MethodNode method = runTick(node);
            MethodInsnNode tick = call(method, MINECRAFT, TICK);
            method.instructions.remove(tick);
            method.instructions.insertBefore(call(method, TIMER, ADVANCE_TIME), tick);
        }, "tick moved before clock planning");
        corruptLoop(raw, node -> {
            MethodNode method = runTick(node);
            MethodInsnNode tasks = call(method, MINECRAFT, "runAllTasks");
            method.instructions.remove(tasks);
            method.instructions.insert(call(method, MINECRAFT, TICK), tasks);
        }, "queued tasks moved after tick");
        ClassNode changed = copy(raw.get(MINECRAFT));
        runTick(changed).instructions.insert(new InsnNode(Opcodes.NOP));
        check(runTickLoopSupported(changed), "isolated loop shape is not mistaken for the entire method proof");
        check(!runTickSupported(changed), "unchanged loop cannot admit an otherwise changed runTick body");
    }

    private static void travelControls(Map<String, ClassNode> raw) {
        check(raw.get(LOCAL_PLAYER).methods.stream().noneMatch(m -> m.name.equals(TRAVEL) && m.desc.equals(TRAVEL_DESC)),
                "raw LocalPlayer has no declared travel(Vec3)");
        check(raw.get(ABSTRACT_CLIENT_PLAYER).methods.stream().noneMatch(m -> m.name.equals(TRAVEL) && m.desc.equals(TRAVEL_DESC)),
                "raw AbstractClientPlayer inherits Player.travel(Vec3)");
        ClassNode override = copy(raw.get(LOCAL_PLAYER));
        override.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, TRAVEL, TRAVEL_DESC, null, null));
        check(!localPlayerSupported(override), "preexisting LocalPlayer travel override refused");
        check(!supported(replacing(raw, LOCAL_PLAYER, override)), "raw gate does not certify a newly added travel override");
        ClassNode superclass = copy(raw.get(LOCAL_PLAYER));
        superclass.superName = PLAYER;
        check(!localPlayerSupported(superclass), "LocalPlayer direct superclass substitution refused");
        corruptClass(raw, ABSTRACT_CLIENT_PLAYER, node -> node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, TRAVEL, TRAVEL_DESC, null, null)),
                "new intermediate travel implementation");
        corruptClass(raw, LOCAL_PLAYER, node -> node.fields.stream().filter(f -> f.name.equals("autoJumpTime")).findFirst().orElseThrow().access = Opcodes.ACC_PUBLIC,
                "native private autoJumpTime widened");
        corruptMethod(raw, LOCAL_PLAYER, AI_STEP, VOID_DESC,
                method -> call(method, ABSTRACT_CLIENT_PLAYER, AI_STEP).owner = PLAYER, "LocalPlayer superclass aiStep caller changed");
        corruptMethod(raw, LIVING_ENTITY, AI_STEP, VOID_DESC,
                method -> call(method, LIVING_ENTITY, TRAVEL).owner = PLAYER, "shared LivingEntity travel caller changed");
        corruptMethod(raw, LIVING_ENTITY, AI_STEP, VOID_DESC,
                method -> call(method, LIVING_ENTITY, TRAVEL).desc = VOID_DESC, "shared native travel argument removed");
        corruptMethod(raw, PLAYER, TRAVEL, TRAVEL_DESC,
                method -> call(method, LIVING_ENTITY, TRAVEL).owner = LOCAL_PLAYER, "inherited travel delegate changed");
        corruptMethod(raw, LOCAL_PLAYER, AI_STEP, VOID_DESC, method -> {
            FieldInsnNode field = instructions(method).stream().filter(i -> i instanceof FieldInsnNode f
                    && f.getOpcode() == Opcodes.PUTFIELD && f.name.equals("autoJumpTime")).map(i -> (FieldInsnNode)i).findFirst().orElseThrow();
            field.name = "unreviewedJumpWrite";
        }, "native auto-jump field write changed");
    }

    private static void pureHelper() throws Exception {
        ClassNode helper = new ClassNode();
        try (InputStream input = WindPressureLiftTravelAdmissionRegressionTest.class.getClassLoader().getResourceAsStream(
                WindPressureLiftTravelAdmission.class.getName().replace('.', '/') + ".class")) {
            if (input == null) throw new AssertionError("missing compiled proof helper");
            new ClassReader(input).accept(helper, 0);
        }
        check(helper.interfaces.isEmpty(), "proof helper installs no Mixin/runtime interface");
        check(helper.fields.stream().allMatch(f -> (f.access & Opcodes.ACC_FINAL) != 0), "proof helper contains no mutable hook state");
        for (MethodNode method : helper.methods) for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) check(!call.owner.startsWith("net/minecraft/")
                    && !call.owner.startsWith("org/spongepowered/")
                    && !(call.owner.equals("java/lang/Class") && call.name.equals("forName"))
                    && !call.owner.equals("java/lang/ClassLoader"), "ASM helper does not load or invoke native/runtime hook classes");
            if (instruction instanceof FieldInsnNode field) check(!field.owner.startsWith("net/minecraft/"), "ASM helper performs no native field read/write");
        }
    }

    private static void stateAccessControls(Map<String, ClassNode> raw) {
        for (String owner : List.of(LOCAL_PLAYER, ABSTRACT_CLIENT_PLAYER, PLAYER, LIVING_ENTITY)) {
            check(inheritsPlainStateAccess(raw.get(owner)), "native state access inherited without callback: " + owner);
            for (String name : List.of("position", "getDeltaMovement", "setDeltaMovement")) {
                ClassNode changed = copy(raw.get(owner));
                changed.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, name, name.equals("setDeltaMovement") ? TRAVEL_DESC : VECTOR_GETTER_DESC, null, null));
                check(!inheritsPlainStateAccess(changed), "added state accessor override refused: " + owner + "#" + name);
                check(!supported(replacing(raw, owner, changed)), "complete state accessor inheritance admission refused: " + owner + "#" + name);
            }
        }
        corruptMethod(raw, ENTITY, "setDeltaMovement", TRAVEL_DESC,
                method -> method.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "unreviewed/Callback", "run", VOID_DESC, false)),
                "setter callback insertion");
        corruptMethod(raw, ENTITY, "setDeltaMovement", TRAVEL_DESC, method -> {
            FieldInsnNode write = instructions(method).stream().filter(i -> i instanceof FieldInsnNode f && f.getOpcode() == Opcodes.PUTFIELD)
                    .map(i -> (FieldInsnNode)i).findFirst().orElseThrow();
            method.instructions.insert(write, new FieldInsnNode(Opcodes.PUTFIELD, write.owner, write.name, write.desc));
        }, "setter duplicate write");
    }

    private static void corruptClass(Map<String, ClassNode> raw, String owner, Consumer<ClassNode> mutation, String label) {
        ClassNode changed = copy(raw.get(owner));
        mutation.accept(changed);
        check(!classSupported(changed), label + " rejected by exact class proof: " + owner);
        check(!supported(replacing(raw, owner, changed)), label + " refused by complete admission: " + owner);
    }

    private static void corruptMethod(Map<String, ClassNode> raw, String owner, String name, String desc,
            Consumer<MethodNode> mutation, String label) {
        ClassNode changed = copy(raw.get(owner));
        mutation.accept(method(changed, name, desc));
        check(!nativeMethodSupported(changed, name, desc), label + " rejected by method fingerprint: " + owner + "#" + name);
        check(!supported(replacing(raw, owner, changed)), label + " refused by complete admission: " + owner + "#" + name);
    }

    private static void corruptLoop(Map<String, ClassNode> raw, Consumer<ClassNode> mutation, String label) {
        ClassNode changed = copy(raw.get(MINECRAFT));
        mutation.accept(changed);
        check(!runTickLoopSupported(changed), label + " rejected by independent loop witness");
        check(!runTickSupported(changed), label + " rejected by complete runTick proof");
        check(!supported(replacing(raw, MINECRAFT, changed)), label + " refused by complete admission");
    }

    private static Map<String, ClassNode> replacing(Map<String, ClassNode> raw, String owner, ClassNode changed) {
        Map<String, ClassNode> result = new HashMap<>(raw); result.put(owner, changed); return result;
    }
    private static ClassNode copy(ClassNode source) { ClassNode result = new ClassNode(); source.accept(result); return result; }
    private static MethodNode copy(MethodNode source) {
        MethodNode result = new MethodNode(Opcodes.ASM9, source.access, source.name, source.desc, source.signature, source.exceptions.toArray(String[]::new));
        source.accept(result); return result;
    }
    private static MethodInsnNode copy(MethodInsnNode source) { return new MethodInsnNode(source.getOpcode(), source.owner, source.name, source.desc, source.itf); }
    private static MethodNode method(ClassNode node, String name, String desc) { return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElseThrow(); }
    private static MethodNode runTick(ClassNode node) { return method(node, RUN_TICK, RUN_TICK_DESC); }
    private static List<AbstractInsnNode> instructions(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>(); for (AbstractInsnNode node : method.instructions) if (node.getOpcode() >= 0) result.add(node); return result;
    }
    private static MethodInsnNode call(MethodNode method, String owner, String name) {
        return instructions(method).stream().filter(i -> i instanceof MethodInsnNode m && m.owner.equals(owner) && m.name.equals(name)).map(i -> (MethodInsnNode)i).findFirst().orElseThrow();
    }
    private static AbstractInsnNode next(AbstractInsnNode instruction) {
        do { instruction = instruction.getNext(); } while (instruction != null && instruction.getOpcode() < 0); return instruction;
    }
    private static AbstractInsnNode previous(AbstractInsnNode instruction) {
        do { instruction = instruction.getPrevious(); } while (instruction != null && instruction.getOpcode() < 0); return instruction;
    }
    private static String digest(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) { byte[] block = new byte[65536]; int size; while ((size = input.read(block)) >= 0) digest.update(block, 0, size); }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static String sha256(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void check(boolean condition, String label) { checks++; if (!condition) throw new AssertionError(label); }
}
