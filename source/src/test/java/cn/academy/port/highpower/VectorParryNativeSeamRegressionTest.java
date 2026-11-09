package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeVectorParryMixinPlugin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** CPU bytecode admission/dispatch controls; native combat itself is covered by GameTests. */
public final class VectorParryNativeSeamRegressionTest {
    private static final String LIVING = "net.minecraft.world.entity.LivingEntity";
    private static final String MIXIN = "cn.academy.port.highpower.mixin.LivingEntityVectorParryMixin";
    private static final String BRIDGE = "cn.academy.port.highpower.VectorParryNativeBridge";
    private static final String HURT = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z";
    private static final String WRAP = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final String WRAP_METHOD = "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;";
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(!NativeVectorParryMixinPlugin.isReviewedNativeTurnSupported(), "precision unavailable before transformation");
        Map<String, ClassNode> classes = new HashMap<>();
        for (String key : NativeVectorParryMixinPlugin.reviewedMethods()) {
            String owner = key.substring(0, key.indexOf('#'));
            if (!classes.containsKey(owner)) classes.put(owner, node(owner));
        }
        check(NativeVectorParryMixinPlugin.supported(classes), "pinned native source/bytecode admitted");
        check(!NativeVectorParryMixinPlugin.supported(null), "missing native evidence refused");
        for (String key : NativeVectorParryMixinPlugin.reviewedMethods()) {
            int split = key.indexOf('#'); String owner = key.substring(0, split), selector = key.substring(split + 1);
            mutate(classes, owner, type -> type.methods.remove(method(type, selector)), "absent method " + key);
            mutate(classes, owner, type -> type.methods.add(copy(method(type, selector))), "duplicate method " + key);
            mutate(classes, owner, type -> method(type, selector).access ^= Opcodes.ACC_STATIC, "changed staticness " + key);
            mutate(classes, owner, type -> method(type, selector).instructions.insert(new InsnNode(Opcodes.NOP)), "changed code " + key);
        }
        for (String owner : classes.keySet()) {
            Map<String, ClassNode> changed = new HashMap<>(classes); changed.remove(owner);
            check(!NativeVectorParryMixinPlugin.supported(changed), "absent class " + owner);
        }
        ClassNode living = classes.get(LIVING);
        check(NativeVectorParryMixinPlugin.nativeSitesSupported(living), "exact two native call sites");
        for (String selector : List.of(HURT, "knockback(DDD)V")) {
            String target = selector.equals(HURT) ? "knockback" : "onLivingKnockBack";
            for (boolean duplicate : List.of(false, true)) {
                ClassNode changed = copy(living); MethodNode method = method(changed, selector); MethodInsnNode call = call(method, target);
                if (duplicate) method.instructions.insert(call, call.clone(new HashMap<>())); else method.instructions.remove(call);
                check(!NativeVectorParryMixinPlugin.nativeSitesSupported(changed), "missing/duplicate " + target + " rejected");
            }
        }

        ClassNode mixin = node(MIXIN);
        check(hooksSupported(mixin), "compiled wrappers have narrow descriptors and exact counts");
        for (String suffix : List.of("academy$vectorParryKnockback", "academy$vectorParryEvent")) {
            for (String count : List.of("require", "expect", "allow")) {
                ClassNode changed = copy(mixin); put(annotation(byName(changed, suffix), WRAP), count, 0);
                check(!hooksSupported(changed), "relaxed " + suffix + " " + count + " rejected");
            }
            ClassNode changed = copy(mixin); put(annotation(byName(changed, suffix), WRAP), "method", List.of("hurt"));
            check(!hooksSupported(changed), "broad native selector rejected");
        }
        ClassNode installed = installedFixture(living, mixin);
        check(NativeVectorParryMixinPlugin.installedCallbacksSupported(installed), "constructed installed witness admitted");
        check(!NativeVectorParryMixinPlugin.installedCallbacksSupported(living), "raw native class not installed witness");
        for (String suffix : List.of("academy$vectorParryKnockback", "academy$vectorParryEvent", "academy$vectorParryHurt", "academy$vectorParryNativeSeam")) {
            ClassNode changed = copy(installed); changed.methods.removeIf(m -> m.name.equals(suffix));
            check(!NativeVectorParryMixinPlugin.installedCallbacksSupported(changed), "absent installed " + suffix + " rejected");
        }
        ClassNode badAccess = copy(installed); byName(badAccess, "academy$vectorParryNativeSeam").instructions.clear();
        check(!NativeVectorParryMixinPlugin.installedCallbacksSupported(badAccess), "witness missing admission check rejected");
        ClassNode badHelper = copy(installed); call(byName(badHelper, "academy$vectorParryEvent"), "nativeEvent").desc = "()V";
        check(!NativeVectorParryMixinPlugin.installedCallbacksSupported(badHelper), "wrong bridge signature rejected");
        ClassNode noInterface = copy(installed); noInterface.interfaces.clear();
        check(!NativeVectorParryMixinPlugin.installedCallbacksSupported(noInterface), "absent access witness rejected");
        ClassNode doubled = copy(installed); MethodNode hurt = method(doubled, HURT); MethodInsnNode hook = call(hurt, "academy$vectorParryKnockback");
        hurt.instructions.insert(hook, hook.clone(new HashMap<>()));
        check(!NativeVectorParryMixinPlugin.installedCallbacksSupported(doubled), "duplicate installed call rejected");

        finalWitnessTimingControls(living, mixin, installed);
        callReceiptControls();
        bridgeControls(node(BRIDGE));
        if (args.length > 0) {
            ClassNode transformed = new ClassNode(); new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(transformed, 0);
            check(NativeVectorParryMixinPlugin.installedCallbacksSupported(transformed), "real exported native transformation accepted");
        }
        System.out.println("Vector parry native seam regression: " + checks + " checks passed");
    }

    private static void finalWitnessTimingControls(ClassNode living, ClassNode mixin, ClassNode installed) throws Exception {
        // A deliberately controlled lifecycle fixture proves timing, not a live transformation.
        NativeVectorParryMixinPlugin plugin = new NativeVectorParryMixinPlugin();
        var admitted = NativeVectorParryMixinPlugin.class.getDeclaredField("admitted"); admitted.setAccessible(true); admitted.setBoolean(plugin, true);
        plugin.preApply(LIVING, living, MIXIN, null);
        ClassNode mergedOnly = copy(living); mergedOnly.interfaces.addAll(mixin.interfaces);
        for (MethodNode method : mixin.methods) if (method.name.startsWith("academy$")) mergedOnly.methods.add(copy(method));
        plugin.postApply(LIVING, mergedOnly, MIXIN, null);
        check(!NativeVectorParryMixinPlugin.isReviewedNativeTurnSupported(), "merged-only postApply never enables precision");
        Class<?> witnessType = Class.forName("cn.academy.port.highpower.mixin.NativeVectorParryMixinPlugin$FinalWitness");
        var constructor = witnessType.getDeclaredConstructor(NativeVectorParryMixinPlugin.class); constructor.setAccessible(true);
        var witness = (org.spongepowered.asm.mixin.transformer.ext.IExtension)constructor.newInstance(plugin);
        witness.export(null, LIVING, false, mergedOnly);
        check(!NativeVectorParryMixinPlugin.isReviewedNativeTurnSupported(), "final merged-only class remains unavailable");
        witness.export(null, LIVING, false, installed);
        check(NativeVectorParryMixinPlugin.isReviewedNativeTurnSupported(), "only final installed class enables precision without debug export");
        plugin.postApply(LIVING, mergedOnly, MIXIN, null);
        check(!NativeVectorParryMixinPlugin.isReviewedNativeTurnSupported(), "another pending transformation is never admitted early");
        ClassNode invalidFinal = copy(installed); method(invalidFinal, "knockback(DDD)V").instructions.remove(call(method(invalidFinal, "knockback(DDD)V"), "academy$vectorParryEvent"));
        witness.export(null, LIVING, false, invalidFinal);
        check(!NativeVectorParryMixinPlugin.isReviewedNativeTurnSupported(), "invalid final call sites remain disabled");
        admitted.setBoolean(plugin, false);
        witness.export(null, LIVING, false, installed);
        check(!NativeVectorParryMixinPlugin.isReviewedNativeTurnSupported(), "installed class cannot bypass native fingerprint admission");
    }

    private static void callReceiptControls() {
        Object receiver = new Object();
        VectorParryNativeCall receipt = new VectorParryNativeCall(receiver, .4, 1, 2);
        check(!receipt.unchanged(receiver, .4F, 1, 2), "undispatched receipt cannot modify an event");
        check(receipt.claim(receiver, .4F, 1, 2), "native double strength's float conversion matches");
        check(receipt.unchanged(receiver, .4F, 1, 2), "nonreentrant outer event retains its own claim");
        check(!receipt.unchanged(receiver, .5F, 1, 2), "listener strength change vetoes turn");
        check(!receipt.unchanged(receiver, .4F, -1, 2), "listener X change vetoes turn");
        check(!receipt.unchanged(receiver, .4F, 1, -2), "listener Z change vetoes turn");
        check(!receipt.unchanged(new Object(), .4F, 1, 2), "foreign receiver vetoes turn");
        receipt.invalidate();
        check(!receipt.valid() && !receipt.unchanged(receiver, .4F, 1, 2), "reentrant hurt permanently retires receipt");
        for (Object nestedReceiver : List.of(receiver, new Object())) {
            VectorParryNativeCall nested = new VectorParryNativeCall(receiver, .4, 1, 2);
            check(nested.claim(receiver, .4F, 1, 2), "outer dispatch claimed before nested callback");
            check(!nested.claim(nestedReceiver, .4F, 1, 2), "nested callback cannot claim pending outer receipt");
            check(!nested.valid() && !nested.unchanged(receiver, .4F, 1, 2), "same/foreign-entity nested knockback vetoes outer turn");
            check(!nested.claim(receiver, .4F, 1, 2), "reentry veto cannot revive the outer receipt");
        }
        VectorParryNativeCall mismatch = new VectorParryNativeCall(receiver, .4, 1, 2);
        check(!mismatch.claim(new Object(), .4F, 1, 2), "wrong dispatch rejected");
        check(!mismatch.claim(receiver, .4F, 1, 2), "failed dispatch cannot be stolen later");
        VectorParryNativeCall invalidated = new VectorParryNativeCall(receiver, .4, 1, 2); invalidated.invalidate();
        check(!invalidated.claim(receiver, .4F, 1, 2), "invalidated before dispatch rejected");
        VectorParryNativeCall signed = new VectorParryNativeCall(receiver, .4, 0.0, 1);
        check(signed.claim(receiver, .4F, 0, 1) && !signed.unchanged(receiver, .4F, -0.0, 1), "bit-level changed operand rejected");
    }

    private static void bridgeControls(ClassNode bridge) {
        MethodNode event = byName(bridge, "nativeEvent");
        check(event.instructions.indexOf(call(event, "claim")) < event.instructions.indexOf(call(event, "call")), "dispatch claimed before subscriber invocation");
        check(event.instructions.indexOf(call(event, "call")) < event.instructions.indexOf(call(event, "allowNativeTurn")), "controller revalidated after subscribers");
        check(event.instructions.indexOf(call(event, "commitNativeTurn")) < event.instructions.indexOf(call(event, "setRatioX")), "one-use commit precedes ratio edit");
        for (MethodNode method : bridge.methods) for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call) {
            check(!List.of("setDeltaMovement", "push", "setStrength", "setHealth", "setAbsorptionAmount").contains(call.name), "bridge has no movement/strength/damage rewrite");
        }
        MethodNode nativeCall = byName(bridge, "nativeKnockback");
        check(nativeCall.tryCatchBlocks.stream().anyMatch(t -> t.type == null), "native exception finally cleanup present");
        check(nativeCall.instructions.indexOf(call(nativeCall, "remove")) < nativeCall.instructions.indexOf(call(nativeCall, "finishNativeTurn")), "ThreadLocal removed before completion callback");
        check(byName(bridge, "nativeHurt").tryCatchBlocks.isEmpty(), "hurt wrapper preserves native exception");
    }

    private static boolean hooksSupported(ClassNode mixin) {
        MethodNode hurt = byName(mixin, "academy$vectorParryHurt");
        if (!List.of(HURT).equals(value(annotation(hurt, WRAP_METHOD), "method"))) return false;
        for (String suffix : List.of("academy$vectorParryKnockback", "academy$vectorParryEvent")) {
            AnnotationNode wrap = annotation(byName(mixin, suffix), WRAP);
            boolean damage = suffix.endsWith("Knockback");
            if (wrap == null || !List.of(damage ? HURT : "knockback(DDD)V").equals(value(wrap, "method"))) return false;
            for (String count : List.of("require", "expect", "allow")) if (!Integer.valueOf(1).equals(value(wrap, count))) return false;
            Object rawAt = value(wrap, "at");
            AnnotationNode at = rawAt instanceof AnnotationNode a ? a : rawAt instanceof List<?> list && list.size() == 1 ? (AnnotationNode)list.getFirst() : null;
            String target = damage ? "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"
                    : "Lnet/neoforged/neoforge/common/CommonHooks;onLivingKnockBack(Lnet/minecraft/world/entity/LivingEntity;FDD)Lnet/neoforged/neoforge/event/entity/living/LivingKnockBackEvent;";
            if (!"INVOKE".equals(value(at, "value")) || !target.equals(value(at, "target"))) return false;
        }
        return true;
    }

    /** Synthetic predicate fixture only; this is not represented as a live native transformation. */
    private static ClassNode installedFixture(ClassNode living, ClassNode mixin) {
        ClassNode result = copy(living); result.interfaces.addAll(mixin.interfaces);
        for (MethodNode method : mixin.methods) if (method.name.startsWith("academy$")) result.methods.add(copy(method));
        for (String selector : List.of(HURT, "knockback(DDD)V")) {
            MethodNode target = method(result, selector); boolean hurt = selector.equals(HURT);
            MethodInsnNode original = call(target, hurt ? "knockback" : "onLivingKnockBack");
            MethodNode hook = byName(result, hurt ? "academy$vectorParryKnockback" : "academy$vectorParryEvent");
            target.instructions.set(original, new MethodInsnNode(Opcodes.INVOKESPECIAL, result.name, hook.name, hook.desc, false));
        }
        MethodNode hurtGuard = byName(result, "academy$vectorParryHurt");
        method(result, HURT).instructions.insert(new MethodInsnNode(Opcodes.INVOKESPECIAL, result.name, hurtGuard.name, hurtGuard.desc, false));
        return result;
    }
    private static void mutate(Map<String, ClassNode> source, String owner, Consumer<ClassNode> mutation, String why) {
        Map<String, ClassNode> changed = new HashMap<>(source); ClassNode type = copy(source.get(owner)); changed.put(owner, type); mutation.accept(type);
        check(!NativeVectorParryMixinPlugin.supported(changed), why + " fails closed");
    }
    private static ClassNode node(String owner) throws Exception {
        try (var stream = VectorParryNativeSeamRegressionTest.class.getResourceAsStream("/" + owner.replace('.', '/') + ".class")) {
            if (stream == null) throw new AssertionError("Missing class " + owner);
            ClassNode result = new ClassNode(); new ClassReader(stream).accept(result, 0); return result;
        }
    }
    private static MethodNode method(ClassNode type, String selector) { return type.methods.stream().filter(m -> (m.name + m.desc).equals(selector)).findFirst().orElseThrow(); }
    private static MethodNode byName(ClassNode type, String name) { return type.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(); }
    private static MethodInsnNode call(MethodNode method, String name) { for (AbstractInsnNode node : method.instructions) if (node instanceof MethodInsnNode call && call.name.equals(name)) return call; throw new AssertionError("Missing " + name); }
    private static ClassNode copy(ClassNode source) { ClassNode copy = new ClassNode(); source.accept(copy); return copy; }
    private static MethodNode copy(MethodNode source) { MethodNode copy = new MethodNode(source.access, source.name, source.desc, source.signature, source.exceptions.toArray(String[]::new)); source.accept(copy); return copy; }
    private static AnnotationNode annotation(MethodNode method, String descriptor) { List<AnnotationNode> all = new ArrayList<>(); if (method.visibleAnnotations != null) all.addAll(method.visibleAnnotations); if (method.invisibleAnnotations != null) all.addAll(method.invisibleAnnotations); return all.stream().filter(a -> a.desc.equals(descriptor)).findFirst().orElse(null); }
    private static Object value(AnnotationNode node, String key) { if (node == null || node.values == null) return null; for (int i = 0; i < node.values.size(); i += 2) if (key.equals(node.values.get(i))) return node.values.get(i + 1); return null; }
    private static void put(AnnotationNode node, String key, Object value) { for (int i = 0; i < node.values.size(); i += 2) if (key.equals(node.values.get(i))) { node.values.set(i + 1, value); return; } node.values.add(key); node.values.add(value); }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
