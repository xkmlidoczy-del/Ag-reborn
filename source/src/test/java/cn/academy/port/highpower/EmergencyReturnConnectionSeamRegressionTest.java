package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeEmergencyReturnMixinPlugin;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Static native/mixin evidence only. Transformed native GameTests must prove the live cycle. */
public final class EmergencyReturnConnectionSeamRegressionTest {
    private static final String CONNECTION = "net.minecraft.server.network.ServerGamePacketListenerImpl";
    private static final String PLAYER = "net.minecraft.server.level.ServerPlayer";
    private static final String LIVING = "net.minecraft.world.entity.LivingEntity";
    private static final String ENTITY = "net.minecraft.world.entity.Entity";
    private static final String MIXIN = "cn.academy.port.highpower.mixin.ServerConnectionEmergencyReturnMixin";
    private static final String HELPER = "cn/academy/port/highpower/EmergencyReturnConnectionCycle";
    private static final String OPERATION = "com/llamalad7/mixinextras/injector/wrapoperation/Operation";
    private static final String WRAP = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final String WRAP_METHOD = "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;";
    private static final String DO_TICK = "Lnet/minecraft/server/level/ServerPlayer;doTick()V";
    private static final String RESTORE = "Lnet/minecraft/server/level/ServerPlayer;absMoveTo(DDDFF)V";
    private static int checks;

    public static void main(String[] args) throws Exception {
        Map<String, ClassNode> nativeClasses = new HashMap<>();
        for (String key : NativeEmergencyReturnMixinPlugin.reviewedMethods()) {
            String owner = key.substring(0, key.indexOf('#'));
            if (!nativeClasses.containsKey(owner)) nativeClasses.put(owner, node(owner));
        }
        check(NativeEmergencyReturnMixinPlugin.supported(nativeClasses), "actual pinned native connection/movement methods accepted");
        check(nativeOrder(method(nativeClasses.get(CONNECTION), "tick()V")), "one native doTick followed by one native accepted-position restore");
        for (String owner : nativeClasses.keySet()) {
            Map<String, ClassNode> absent = new HashMap<>(nativeClasses); absent.remove(owner);
            check(!NativeEmergencyReturnMixinPlugin.supported(absent), "missing native class closes witness: " + owner);
            absent.put(owner, null);
            check(!NativeEmergencyReturnMixinPlugin.supported(absent), "null native class closes witness: " + owner);
        }
        for (String key : NativeEmergencyReturnMixinPlugin.reviewedMethods()) {
            int split = key.indexOf('#'); String owner = key.substring(0, split), selector = key.substring(split + 1);
            mutate(nativeClasses, owner, changed -> method(changed, selector).instructions.insert(new InsnNode(Opcodes.NOP)), "changed native instructions " + key);
            mutate(nativeClasses, owner, changed -> changed.methods.remove(method(changed, selector)), "missing native method " + key);
            mutate(nativeClasses, owner, changed -> changed.methods.add(copy(method(changed, selector))), "duplicate native method " + key);
            mutate(nativeClasses, owner, changed -> method(changed, selector).access ^= Opcodes.ACC_STATIC, "changed native staticness " + key);
        }
        mutate(nativeClasses, CONNECTION, changed -> {
            MethodNode tick = method(changed, "tick()V"); tick.instructions.remove(nativeCall(tick, "doTick", "()V"));
        }, "missing native doTick");
        mutate(nativeClasses, CONNECTION, changed -> {
            MethodNode tick = method(changed, "tick()V"); MethodInsnNode call = nativeCall(tick, "doTick", "()V");
            tick.instructions.insert(call, call.clone(new HashMap<>()));
        }, "duplicate native doTick");
        mutate(nativeClasses, CONNECTION, changed -> {
            MethodNode tick = method(changed, "tick()V"); MethodInsnNode restore = nativeCall(tick, "absMoveTo", "(DDDFF)V");
            tick.instructions.remove(restore); tick.instructions.insertBefore(nativeCall(tick, "doTick", "()V"), restore);
            check(!nativeOrder(tick), "reordered native calls are not the accepted cycle");
        }, "restore before native doTick");
        mutate(nativeClasses, CONNECTION, changed -> {
            MethodNode tick = method(changed, "tick()V"); MethodInsnNode call = nativeCall(tick, "absMoveTo", "(DDDFF)V");
            tick.instructions.insert(call, call.clone(new HashMap<>()));
        }, "duplicate native restore");
        for (int mutation = 0; mutation < 6; mutation++) {
            final int variant = mutation;
            mutate(nativeClasses, PLAYER, changed -> {
                MethodNode tick = method(changed, "doTick()V"); TryCatchBlockNode handler = tick.tryCatchBlocks.getFirst();
                switch (variant) {
                    case 0 -> handler.type = "java/lang/Exception";
                    case 1 -> handler.start = handler.end;
                    case 2 -> handler.end = handler.handler;
                    case 3 -> handler.handler = handler.start;
                    case 4 -> tick.tryCatchBlocks.clear();
                    default -> tick.tryCatchBlocks.add(new TryCatchBlockNode(handler.start, handler.end, handler.handler, handler.type));
                }
            }, "native doTick exception table mutation " + variant);
        }
        for (String[] field : new String[][]{{LIVING, "jumping"}, {LIVING, "lerpSteps"}, {ENTITY, "stuckSpeedMultiplier"}}) {
            mutate(nativeClasses, field[0], changed -> changed.fields.removeIf(f -> f.name.equals(field[1])), "missing read-only movement field " + field[1]);
            mutate(nativeClasses, field[0], changed -> changed.fields.stream().filter(f -> f.name.equals(field[1])).findFirst().orElseThrow().desc = "J", "changed movement field descriptor " + field[1]);
        }
        check(NativeEmergencyReturnMixinPlugin.supported(nativeClasses), "all native mutation controls leave original evidence intact");

        ClassNode mixin = node(MIXIN);
        check(wrappersSupported(mixin), "compiled wrappers preserve exact original arguments once and clean up on normal/throwing paths");
        for (MethodNode original : mixin.methods) {
            if (annotation(original, WRAP) == null && annotation(original, WRAP_METHOD) == null) continue;
            String selector = original.name + original.desc;
            mutateMixin(mixin, selector, changed -> {
                MethodInsnNode call = originalCall(changed); changed.instructions.insert(call, call.clone(new HashMap<>()));
            }, "duplicate original invocation " + original.name);
            mutateMixin(mixin, selector, changed -> changed.instructions.remove(originalCall(changed)), "missing original invocation " + original.name);
            mutateMixin(mixin, selector, changed -> changed.tryCatchBlocks.clear(), "missing finally guard " + original.name);
            mutateMixin(mixin, selector, changed -> {
                for (AbstractInsnNode instruction : changed.instructions.toArray())
                    if (instruction instanceof MethodInsnNode call && call.owner.equals(HELPER)) changed.instructions.set(call, new InsnNode(Opcodes.NOP));
            }, "missing witness cleanup " + original.name);
            if (annotation(original, WRAP) != null) {
                for (String count : List.of("require", "expect", "allow"))
                    mutateMixin(mixin, selector, changed -> put(annotation(changed, WRAP), count, 0), "relaxed target count " + count + " " + original.name);
                mutateMixin(mixin, selector, changed -> put(singleAt(annotation(changed, WRAP)), "target", DO_TICK.equals(target(changed)) ? RESTORE : DO_TICK), "wrong target " + original.name);
                mutateMixin(mixin, selector, changed -> put(annotation(changed, WRAP), "at", List.of()), "missing invocation selector " + original.name);
                mutateMixin(mixin, selector, changed -> {
                    AnnotationNode wrap = annotation(changed, WRAP); AnnotationNode at = singleAt(wrap);
                    put(wrap, "at", List.of(at, at));
                }, "duplicate invocation selector " + original.name);
                mutateMixin(mixin, selector, changed -> {
                    List<AbstractInsnNode> instructions = code(changed); int at = instructions.indexOf(originalCall(changed));
                    for (int i = at - 1; i >= 0; i--) if (instructions.get(i) instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == 1) { load.var++; return; }
                    throw new AssertionError("Missing original receiver load in " + original.name);
                }, "changed native receiver " + original.name);
            }
        }
        System.out.println("Emergency return connection seams: " + checks + " static checks passed; live transformed behavior requires native GameTests");
    }

    private static void mutate(Map<String, ClassNode> original, String owner, Consumer<ClassNode> mutation, String why) {
        Map<String, ClassNode> changed = new HashMap<>(original); ClassNode target = copy(original.get(owner));
        changed.put(owner, target); mutation.accept(target);
        check(!NativeEmergencyReturnMixinPlugin.supported(changed), why + " fails closed");
    }
    private static void mutateMixin(ClassNode original, String selector, Consumer<MethodNode> mutation, String why) {
        ClassNode changed = copy(original); mutation.accept(method(changed, selector));
        check(!wrappersSupported(changed), why + " rejected by static wrapper contract");
    }
    private static boolean nativeOrder(MethodNode method) {
        int tick = -1, restore = -1, ticks = 0, restores = 0, index = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(PLAYER.replace('.', '/'))) {
                if (call.name.equals("doTick") && call.desc.equals("()V")) { tick = index; ticks++; }
                if (call.name.equals("absMoveTo") && call.desc.equals("(DDDFF)V")) { restore = index; restores++; }
            }
            if (instruction.getOpcode() >= 0) index++;
        }
        return ticks == 1 && restores == 1 && tick < restore;
    }
    private static boolean wrappersSupported(ClassNode mixin) {
        try {
            int whole = 0, operations = 0; Set<String> targets = new HashSet<>();
            for (MethodNode method : mixin.methods) {
                AnnotationNode wrap = annotation(method, WRAP), full = annotation(method, WRAP_METHOD);
                if (wrap == null && full == null) continue;
                if ((wrap != null && full != null) || (method.access & Opcodes.ACC_STATIC) != 0) return false;
                AnnotationNode annotation = wrap == null ? full : wrap;
                if (!List.of("tick()V").equals(value(annotation, "method"))) return false;
                if (wrap == null) whole++;
                else {
                    operations++;
                    for (String count : List.of("require", "expect", "allow")) if (!Integer.valueOf(1).equals(value(wrap, count))) return false;
                    AnnotationNode at = singleAt(wrap);
                    if (at == null) return false;
                    if (!"INVOKE".equals(value(at, "value")) || value(at, "shift") != null || !targets.add((String) value(at, "target"))) return false;
                }
                if (!wrapperBodySupported(method, wrap != null)) return false;
            }
            return whole == 1 && operations == 2 && targets.equals(Set.of(DO_TICK, RESTORE));
        } catch (RuntimeException invalid) { return false; }
    }
    private static boolean wrapperBodySupported(MethodNode method, boolean operationWrapper) {
        List<AbstractInsnNode> instructions = code(method);
        List<MethodInsnNode> originals = instructions.stream().filter(EmergencyReturnConnectionSeamRegressionTest::isOriginalCall).map(i -> (MethodInsnNode) i).toList();
        if (originals.size() != 1 || !unchangedArguments(method, originals.getFirst(), operationWrapper)) return false;
        int originalIndex = instructions.indexOf(originals.getFirst());
        for (AbstractInsnNode instruction : instructions) {
            if (instruction instanceof FieldInsnNode field && (field.getOpcode() == Opcodes.PUTFIELD || field.getOpcode() == Opcodes.PUTSTATIC) && field.owner.startsWith("net/minecraft/")) return false;
            if (instruction instanceof MethodInsnNode call && call.owner.startsWith("net/minecraft/") && (call.name.startsWith("set") || call.name.equals("move") || call.name.equals("absMoveTo") || call.name.equals("doTick") || call.name.equals("hurt"))) return false;
        }
        Map<AbstractInsnNode, Integer> offsets = offsets(method);
        for (TryCatchBlockNode handler : method.tryCatchBlocks) {
            if (handler.type != null || offsets.get(handler.start) > originalIndex || offsets.get(handler.end) <= originalIndex) continue;
            int handlerIndex = offsets.get(handler.handler);
            if (handlerIndex >= instructions.size() || !(instructions.get(handlerIndex) instanceof VarInsnNode stored) || stored.getOpcode() != Opcodes.ASTORE) continue;
            Set<String> cleanup = new HashSet<>();
            for (int i = handlerIndex; i < instructions.size(); i++) {
                if (instructions.get(i) instanceof MethodInsnNode call && call.owner.equals(HELPER)) cleanup.add(call.name + call.desc);
            }
            if (cleanup.isEmpty()) continue;
            if (flow(instructions, offsets, 0, 0, originalIndex, cleanup, -1, new HashSet<>())
                    && flow(instructions, offsets, handlerIndex, 1, originalIndex, cleanup, stored.var, new HashSet<>())) return true;
        }
        return false;
    }
    /** Both normal and exceptional control flow must reach cleanup; original exceptions must be rethrown. */
    private static boolean flow(List<AbstractInsnNode> code, Map<AbstractInsnNode, Integer> offsets, int index, int state,
                                int original, Set<String> cleanup, int exceptionLocal, Set<String> visited) {
        if (index < 0 || index >= code.size() || !visited.add(index + ":" + state)) return false;
        AbstractInsnNode instruction = code.get(index); int opcode = instruction.getOpcode();
        if (index == original) { if ((state & 1) != 0) return false; state |= 1; }
        if (instruction instanceof MethodInsnNode call && call.owner.equals(HELPER) && cleanup.contains(call.name + call.desc) && (state & 1) != 0) state |= 2;
        if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) return exceptionLocal < 0 && state == 3;
        if (opcode == Opcodes.ATHROW) return exceptionLocal >= 0 && state == 3 && index > 0
                && code.get(index - 1) instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == exceptionLocal;
        if (instruction instanceof JumpInsnNode jump) {
            if (!flow(code, offsets, offsets.get(jump.label), state, original, cleanup, exceptionLocal, new HashSet<>(visited))) return false;
            if (opcode == Opcodes.GOTO) return true;
            if (opcode == Opcodes.JSR) return false;
        }
        if (instruction instanceof TableSwitchInsnNode || instruction instanceof LookupSwitchInsnNode) return false;
        return flow(code, offsets, index + 1, state, original, cleanup, exceptionLocal, visited);
    }
    private static boolean unchangedArguments(MethodNode method, MethodInsnNode original, boolean operationWrapper) {
        Type[] args = Type.getArgumentTypes(method.desc);
        if (args.length == 0 || !args[args.length - 1].getDescriptor().equals("L" + OPERATION + ";")) return false;
        int count = args.length - 1, operationLocal = 1;
        for (int i = 0; i < count; i++) operationLocal += args[i].getSize();
        if (!operationWrapper && count != 0) return false;
        if (operationWrapper && !args[0].getDescriptor().equals("Lnet/minecraft/server/level/ServerPlayer;")) return false;
        List<String> expected = new ArrayList<>(); expected.add("V:" + Opcodes.ALOAD + ":" + operationLocal); expected.add(integer(count));
        expected.add("T:" + Opcodes.ANEWARRAY + ":java/lang/Object"); int local = 1;
        for (int i = 0; i < count; i++) {
            Type type = args[i]; expected.add("I:" + Opcodes.DUP); expected.add(integer(i));
            expected.add("V:" + type.getOpcode(Opcodes.ILOAD) + ":" + local); local += type.getSize();
            if (type.getSort() == Type.DOUBLE) expected.add("M:" + Opcodes.INVOKESTATIC + ":java/lang/Double.valueOf(D)Ljava/lang/Double;");
            else if (type.getSort() == Type.FLOAT) expected.add("M:" + Opcodes.INVOKESTATIC + ":java/lang/Float.valueOf(F)Ljava/lang/Float;");
            else if (type.getSort() != Type.OBJECT && type.getSort() != Type.ARRAY) return false;
            expected.add("I:" + Opcodes.AASTORE);
        }
        List<AbstractInsnNode> code = code(method); int end = code.indexOf(original), start = end - expected.size();
        if (start < 0) return false;
        for (int i = 0; i < expected.size(); i++) if (!expected.get(i).equals(token(code.get(start + i)))) return false;
        return true;
    }
    private static String integer(int value) { return value <= 5 ? "I:" + (Opcodes.ICONST_0 + value) : "N:" + Opcodes.BIPUSH + ":" + value; }
    private static String token(AbstractInsnNode instruction) {
        if (instruction instanceof VarInsnNode n) return "V:" + n.getOpcode() + ":" + n.var;
        if (instruction instanceof TypeInsnNode n) return "T:" + n.getOpcode() + ":" + n.desc;
        if (instruction instanceof MethodInsnNode n) return "M:" + n.getOpcode() + ":" + n.owner + "." + n.name + n.desc;
        if (instruction instanceof IntInsnNode n) return "N:" + n.getOpcode() + ":" + n.operand;
        return "I:" + instruction.getOpcode();
    }
    private static Map<AbstractInsnNode, Integer> offsets(MethodNode method) {
        Map<AbstractInsnNode, Integer> result = new IdentityHashMap<>(); int index = 0;
        for (AbstractInsnNode instruction : method.instructions) { result.put(instruction, index); if (instruction.getOpcode() >= 0) index++; }
        return result;
    }
    private static boolean isOriginalCall(AbstractInsnNode instruction) { return instruction instanceof MethodInsnNode call && call.owner.equals(OPERATION) && call.name.equals("call") && call.desc.equals("([Ljava/lang/Object;)Ljava/lang/Object;"); }
    private static MethodInsnNode originalCall(MethodNode method) { return (MethodInsnNode) code(method).stream().filter(EmergencyReturnConnectionSeamRegressionTest::isOriginalCall).findFirst().orElseThrow(); }
    private static MethodInsnNode nativeCall(MethodNode method, String name, String descriptor) {
        return (MethodInsnNode) code(method).stream().filter(i -> i instanceof MethodInsnNode call && call.owner.equals(PLAYER.replace('.', '/')) && call.name.equals(name) && call.desc.equals(descriptor)).findFirst().orElseThrow();
    }
    private static String target(MethodNode method) { return (String) value(singleAt(annotation(method, WRAP)), "target"); }
    /** WrapOperation declares At[], even when source syntax supplies one @At. */
    private static AnnotationNode singleAt(AnnotationNode wrap) {
        Object at = value(wrap, "at");
        return at instanceof List<?> list && list.size() == 1 && list.getFirst() instanceof AnnotationNode node ? node : null;
    }
    private static List<AbstractInsnNode> code(MethodNode method) { return Arrays.stream(method.instructions.toArray()).filter(i -> i.getOpcode() >= 0).toList(); }
    private static MethodNode method(ClassNode owner, String selector) { return owner.methods.stream().filter(m -> (m.name + m.desc).equals(selector)).findFirst().orElseThrow(); }
    private static ClassNode node(String name) throws IOException {
        try (var in = EmergencyReturnConnectionSeamRegressionTest.class.getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            if (in == null) throw new AssertionError("Missing class " + name); ClassNode node = new ClassNode(); new ClassReader(in).accept(node, 0); return node;
        }
    }
    private static ClassNode copy(ClassNode original) { ClassNode copy = new ClassNode(); original.accept(copy); return copy; }
    private static MethodNode copy(MethodNode original) { MethodNode copy = new MethodNode(original.access, original.name, original.desc, original.signature, original.exceptions.toArray(String[]::new)); original.accept(copy); return copy; }
    private static AnnotationNode annotation(MethodNode method, String descriptor) {
        for (List<AnnotationNode> annotations : Arrays.asList(method.visibleAnnotations, method.invisibleAnnotations))
            if (annotations != null) for (AnnotationNode annotation : annotations) if (annotation.desc.equals(descriptor)) return annotation;
        return null;
    }
    private static Object value(AnnotationNode annotation, String key) {
        if (annotation == null || annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        return null;
    }
    private static void put(AnnotationNode annotation, String key, Object value) {
        for (int i = 0; i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(key)) { annotation.values.set(i + 1, value); return; }
        annotation.values.add(key); annotation.values.add(value);
    }
    private static void check(boolean condition, String description) { if (!condition) throw new AssertionError(description); checks++; }
}
