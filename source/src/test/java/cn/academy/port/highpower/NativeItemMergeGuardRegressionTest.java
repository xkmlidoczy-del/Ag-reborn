package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeItemMergeMixinPlugin;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Reads actual pinned native and compiled mixin bytecode without bootstrapping Minecraft. */
public final class NativeItemMergeGuardRegressionTest {
    private static final String ITEM = "net/minecraft/world/entity/item/ItemEntity";
    private static final String STACK = "Lnet/minecraft/world/item/ItemStack;";
    private static final String ENTITY = "L" + ITEM + ";";
    private static final String MERGE = "(" + ENTITY + STACK + ENTITY + STACK + ")V";
    private static final String CALLBACK = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String HANDLER = "(" + ENTITY + STACK + ENTITY + STACK + "L" + CALLBACK + ";)V";
    private static final String ACCESS = "cn/academy/port/highpower/NativeItemMergeAccess";
    private static final String VERSION = "academy$nativeItemMergeContractVersion";
    private static final String MIXIN = "cn/academy/port/highpower/mixin/ItemEntityProvenanceMixin";
    private static final List<String> REVIEWED = List.of("merge" + MERGE,
            "merge(" + ENTITY + STACK + STACK + ")V", "merge(" + STACK + STACK + "I)" + STACK,
            "tryToMerge(" + ENTITY + ")V", "areMergable(" + STACK + STACK + ")Z", "isMergable()Z",
            "mergeWithNeighbours()V", "lambda$mergeWithNeighbours$0(" + ENTITY + ")Z",
            "getItem()" + STACK, "setItem(" + STACK + ")V");
    private static byte[] nativeBytes;
    private static byte[] mixinBytes;
    private static int checks;

    public static void main(String[] args) throws IOException {
        nativeBytes = resource(ITEM);
        mixinBytes = resource(MIXIN);
        check(NativeItemMergeMixinPlugin.supportedItem(nativeNode()), "actual pinned ItemEntity accepted");
        check(!NativeItemMergeMixinPlugin.supportedItem(null), "null native class refused");
        check(!NativeItemMergeMixinPlugin.supportedItem(new ClassNode()), "missing native class refused");
        nativeMutations();
        compiledMixinContract();
        appliedContract();
        System.out.println("PASS " + checks + " native item-merge bytecode, applied-capability and compiled-mixin checks");
    }

    private static void nativeMutations() {
        ClassNode changed = nativeNode();
        changed.name += "Other";
        refused(changed, "wrong native owner");
        changed = nativeNode();
        changed.superName = "java/lang/Object";
        refused(changed, "changed native superclass");
        for (String name : List.of("age", "pickupDelay", "target", "DATA_ITEM")) {
            changed = nativeNode();
            changed.fields.removeIf(f -> f.name.equals(name));
            refused(changed, "missing field " + name);
            changed = nativeNode();
            changed.fields.stream().filter(f -> f.name.equals(name)).findFirst().orElseThrow().desc = "J";
            refused(changed, "changed field type " + name);
            changed = nativeNode();
            changed.fields.stream().filter(f -> f.name.equals(name)).findFirst().orElseThrow().access ^= Opcodes.ACC_STATIC;
            refused(changed, "changed field staticness " + name);
            changed = nativeNode();
            FieldNode field = changed.fields.stream().filter(f -> f.name.equals(name)).findFirst().orElseThrow();
            changed.fields.add(new FieldNode(field.access, field.name, field.desc, null, null));
            refused(changed, "duplicate field " + name);
        }
        for (String key : REVIEWED) {
            MethodNode original = method(nativeNode(), key);
            int count = instructions(original).size();
            for (int i = 0; i < count; i++) {
                changed = nativeNode();
                MethodNode mutant = method(changed, key);
                mutant.instructions.set(instructions(mutant).get(i), new InsnNode(Opcodes.NOP));
                refused(changed, key + " instruction " + i);
            }
            changed = nativeNode();
            method(changed, key).desc += "I";
            refused(changed, "wrong descriptor " + key);
            changed = nativeNode();
            method(changed, key).access ^= Opcodes.ACC_STATIC;
            refused(changed, "wrong staticness " + key);
            changed = nativeNode();
            method(changed, key).access |= Opcodes.ACC_NATIVE;
            refused(changed, "native replacement " + key);
            changed = nativeNode();
            changed.methods.remove(method(changed, key));
            refused(changed, "missing method " + key);
            changed = nativeNode();
            changed.methods.add(copy(method(changed, key)));
            refused(changed, "duplicate method " + key);
            changed = nativeNode();
            method(changed, key).instructions.insert(new InsnNode(Opcodes.NOP));
            refused(changed, "additional instruction " + key);
            changed = nativeNode();
            MethodNode guarded = method(changed, key);
            guarded.tryCatchBlocks.add(new TryCatchBlockNode(new LabelNode(), new LabelNode(), new LabelNode(), "java/lang/Throwable"));
            refused(changed, "exception handler " + key);
            changed = nativeNode();
            method(changed, key).instructions.insert(new LabelNode());
            check(NativeItemMergeMixinPlugin.supportedItem(changed), "debug-only labels accepted " + key);
        }
        changed = nativeNode();
        MethodNode dispatcher = method(changed, "tryToMerge(" + ENTITY + ")V");
        MethodInsnNode dispatch = instructions(dispatcher).stream().filter(n -> n instanceof MethodInsnNode m && m.name.equals("merge"))
                .map(n -> (MethodInsnNode)n).findFirst().orElseThrow();
        dispatch.desc = "(" + ENTITY + STACK + STACK + ")V";
        refused(changed, "dispatcher cannot bypass four-argument hook");
        changed = nativeNode();
        JumpInsnNode branch = instructions(method(changed, "tryToMerge(" + ENTITY + ")V")).stream()
                .filter(n -> n instanceof JumpInsnNode).map(n -> (JumpInsnNode)n).findFirst().orElseThrow();
        branch.label = new LabelNode();
        refused(changed, "branch destination outside method");
        changed = nativeNode();
        InvokeDynamicInsnNode dynamic = instructions(method(changed, "mergeWithNeighbours()V")).stream()
                .filter(n -> n instanceof InvokeDynamicInsnNode).map(n -> (InvokeDynamicInsnNode)n).findFirst().orElseThrow();
        dynamic.bsmArgs[1] = new Handle(Opcodes.H_INVOKEVIRTUAL, ITEM, "differentPredicate", "(" + ENTITY + ")Z", false);
        refused(changed, "changed neighbour predicate bootstrap argument");
    }

    private static void compiledMixinContract() {
        ClassNode mixin = node(mixinBytes);
        AnnotationNode target = annotation(mixin.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        check(target != null, "compiled mixin annotation exists");
        check(List.of(Type.getObjectType(ITEM)).equals(value(target, "value")), "only ItemEntity is targeted");
        check(mixin.interfaces.equals(List.of(ACCESS)), "compiled marker is exact");
        MethodNode handler = method(mixin, "academy$beforeNativeMerge" + HANDLER);
        check(handler.access == (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC), "static handler receives native arguments");
        AnnotationNode inject = annotation(handler.visibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        if (inject == null) inject = annotation(handler.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        check(inject != null, "compiled injection annotation exists");
        check(List.of("merge" + MERGE).equals(value(inject, "method")), "exact four-argument native overload is selected");
        check(Boolean.FALSE.equals(value(inject, "cancellable")), "explicitly non-cancelling injection");
        for (String limit : List.of("require", "expect", "allow")) check(Integer.valueOf(1).equals(value(inject, limit)), "one and only one injection: " + limit);
        Object atValue = value(inject, "at");
        check(atValue instanceof List<?> points && points.size() == 1, "single injection point");
        AnnotationNode at = (AnnotationNode)((List<?>)atValue).getFirst();
        check("HEAD".equals(value(at, "value")), "compiled hook is HEAD");
        check(value(at, "shift") == null, "HEAD is not shifted");
        List<AbstractInsnNode> body = instructions(handler);
        check(body.size() == 6, "handler has only argument loads, provenance call and return");
        for (int i = 0; i < 4; i++) check(body.get(i) instanceof VarInsnNode v && v.getOpcode() == Opcodes.ALOAD && v.var == i,
                "unaltered native argument " + i);
        check(body.get(4) instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                && call.owner.equals("cn/academy/port/highpower/MagneticItemProvenance") && call.name.equals("beforeMerge")
                && call.desc.equals(MERGE) && !call.itf, "direct precise helper call");
        check(body.get(5).getOpcode() == Opcodes.RETURN, "unconditional normal handler return");
        check(version(mixin) == 0, "compiled marker is unavailable until postApply validates actual hook");
    }

    private static void appliedContract() {
        NativeItemMergeMixinPlugin plugin = new NativeItemMergeMixinPlugin();
        check(!plugin.shouldApplyMixin("net.minecraft.world.entity.LivingEntity", MIXIN.replace('/', '.')), "other feature seam is untouched");
        check(!plugin.shouldApplyMixin(ITEM.replace('/', '.'), "other.Mixin"), "unrelated mixin is refused");
        check(!NativeItemMergeMixinPlugin.supportedAppliedItem(nativeNode()), "native bytes alone are not capability");
        ClassNode applied = appliedNode();
        check(NativeItemMergeMixinPlugin.supportedAppliedItem(applied), "exact transformed HEAD fixture accepted");
        check(!NativeItemMergeMixinPlugin.supportedItem(applied), "transformed bytes cannot impersonate unmodified admission");
        plugin.postApply(ITEM.replace('/', '.'), applied, MIXIN.replace('/', '.'), null);
        check(version(applied) == 1, "postApply publishes version only for verified applied seam");
        ClassNode markerOnly = nativeNode();
        markerOnly.interfaces.add(ACCESS);
        markerOnly.methods.add(copy(method(node(mixinBytes), VERSION + "()I")));
        check(!NativeItemMergeMixinPlugin.supportedAppliedItem(markerOnly), "marker-only fake refused");
        plugin.postApply(ITEM.replace('/', '.'), markerOnly, MIXIN.replace('/', '.'), null);
        check(version(markerOnly) == 0, "marker-only fake remains unavailable");

        // Exhaustively mutate every actual applied instruction, including callback argument,
        // forwarded argument identities, native tail, dispatcher and helper chain.
        var keys = new ArrayList<>(REVIEWED);
        keys.add("handler$test$academy$beforeNativeMerge" + HANDLER);
        keys.add(VERSION + "()I");
        for (String key : keys) {
            int count = instructions(method(appliedNode(), key)).size();
            for (int i = 0; i < count; i++) {
                ClassNode changed = appliedNode();
                MethodNode mutant = method(changed, key);
                mutant.instructions.set(instructions(mutant).get(i), new InsnNode(Opcodes.NOP));
                check(!NativeItemMergeMixinPlugin.supportedAppliedItem(changed), "changed applied instruction refused: " + key + " " + i);
            }
        }
        applied = appliedNode();
        method(applied, "merge" + MERGE).instructions.insert(new InsnNode(Opcodes.NOP));
        plugin.postApply(ITEM.replace('/', '.'), applied, MIXIN.replace('/', '.'), null);
        check(version(applied) == 0, "non-HEAD or extra hook leaves NEW133 unavailable");
        applied = appliedNode();
        MethodNode merge = method(applied, "merge" + MERGE);
        merge.instructions.set(instructions(merge).get(4), new VarInsnNode(Opcodes.ALOAD, 0));
        check(!NativeItemMergeMixinPlugin.supportedAppliedItem(applied), "non-null callback argument rejected");
        applied = appliedNode();
        MethodNode alteredHandler = method(applied, "handler$test$academy$beforeNativeMerge" + HANDLER);
        alteredHandler.instructions.insert(new VarInsnNode(Opcodes.ALOAD, 4));
        alteredHandler.instructions.insert(alteredHandler.instructions.getFirst(),
                new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CALLBACK, "cancel", "()V", false));
        check(!NativeItemMergeMixinPlugin.supportedAppliedItem(applied), "handler cannot inspect or cancel callback");
        applied = appliedNode();
        ((VarInsnNode)instructions(method(applied, "merge" + MERGE)).get(2)).var = 0;
        check(!NativeItemMergeMixinPlugin.supportedAppliedItem(applied), "source/destination alias rejected");
        applied = appliedNode();
        method(applied, "handler$test$academy$beforeNativeMerge" + HANDLER).access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        check(!NativeItemMergeMixinPlugin.supportedAppliedItem(applied), "changed handler access rejected");
        applied = appliedNode();
        method(applied, "merge" + MERGE).instructions.insert(new LabelNode());
        check(NativeItemMergeMixinPlugin.supportedAppliedItem(applied), "transformed debug labels harmless");
        applied = appliedNode();
        applied.interfaces.remove(ACCESS);
        check(!NativeItemMergeMixinPlugin.supportedAppliedItem(applied), "missing capability interface rejected");
        applied = appliedNode();
        applied.methods.remove(method(applied, VERSION + "()I"));
        check(!NativeItemMergeMixinPlugin.supportedAppliedItem(applied), "missing version method rejected");
    }

    /** The exact callback shape emitted by the pinned non-cancelling Mixin @Inject. */
    private static ClassNode appliedNode() {
        ClassNode item = nativeNode();
        ClassNode mixin = node(mixinBytes);
        item.interfaces.add(ACCESS);
        item.methods.add(copy(method(mixin, VERSION + "()I")));
        MethodNode handler = copy(method(mixin, "academy$beforeNativeMerge" + HANDLER));
        handler.name = "handler$test$academy$beforeNativeMerge";
        item.methods.add(handler);
        InsnList prefix = new InsnList();
        for (int i = 0; i < 4; i++) prefix.add(new VarInsnNode(Opcodes.ALOAD, i));
        // Pinned sponge-mixin skips allocating a callback the static handler never reads.
        prefix.add(new InsnNode(Opcodes.ACONST_NULL));
        prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ITEM, handler.name, HANDLER, false));
        method(item, "merge" + MERGE).instructions.insert(prefix);
        return item;
    }

    private static int version(ClassNode item) {
        List<AbstractInsnNode> code = instructions(method(item, VERSION + "()I"));
        check(code.size() == 2 && code.get(1).getOpcode() == Opcodes.IRETURN, "version method remains a constant return");
        return code.getFirst().getOpcode() - Opcodes.ICONST_0;
    }
    private static byte[] resource(String name) throws IOException {
        try (var stream = NativeItemMergeGuardRegressionTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (stream == null) throw new IOException("Required reviewed or compiled class missing: " + name);
            return stream.readAllBytes();
        }
    }
    private static ClassNode nativeNode() { return node(nativeBytes); }
    private static ClassNode node(byte[] bytes) { var node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }
    private static MethodNode method(ClassNode owner, String key) {
        return owner.methods.stream().filter(m -> (m.name + m.desc).equals(key)).findFirst().orElseThrow();
    }
    private static MethodNode copy(MethodNode method) {
        MethodNode copy = new MethodNode(method.access, method.name, method.desc, method.signature, method.exceptions.toArray(String[]::new));
        method.accept(copy); return copy;
    }
    private static List<AbstractInsnNode> instructions(MethodNode method) {
        var instructions = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode node : method.instructions) if (node.getOpcode() >= 0) instructions.add(node);
        return instructions;
    }
    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        return annotations == null ? null : annotations.stream().filter(a -> a.desc.equals(descriptor)).findFirst().orElse(null);
    }
    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
        return null;
    }
    private static void refused(ClassNode item, String description) { check(!NativeItemMergeMixinPlugin.supportedItem(item), description + " refused"); }
    private static void check(boolean condition, String description) { checks++; if (!condition) throw new AssertionError(description); }
}
