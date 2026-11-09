package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeCirculationListeningMotionMixinPlugin;
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

/** CPU proof of fail-closed native admission, final dispatch and bounded passive receipts.
 * Native world/packet integration is exercised by the separate NEW433 GameTests. */
public final class CirculationListeningNativeSeamRegressionTest {
    private static final String PREFIX = "cn.academy.port.highpower.mixin.";
    private static final String ENTITY = "net.minecraft.world.entity.Entity", ARROW = "net.minecraft.world.entity.projectile.AbstractArrow";
    private static final String MOVE = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V";
    private static final Map<String, String> MIXINS = Map.of(ENTITY, "EntityCirculationListeningMotionMixin", ARROW, "ArrowCirculationListeningMotionMixin");
    private static int checks;
    public static void main(String[] args) throws Exception {
        check(!NativeCirculationListeningMotionMixinPlugin.available(), "sensing unavailable before final hooks");
        Map<String, ClassNode> raw = Map.of(ENTITY, node(ENTITY), ARROW, node(ARROW));
        check(NativeCirculationListeningMotionMixinPlugin.supported(raw), "pinned native position paths admitted");
        check(!NativeCirculationListeningMotionMixinPlugin.supported(null), "missing classes refused");
        for (String key : NativeCirculationListeningMotionMixinPlugin.reviewedMethods()) {
            int split = key.indexOf('#'); String owner = key.substring(0, split), selector = key.substring(split + 1);
            mutate(raw, owner, n -> n.methods.remove(method(n, selector)), "missing method");
            mutate(raw, owner, n -> n.methods.add(copy(method(n, selector))), "duplicate method");
            mutate(raw, owner, n -> method(n, selector).access ^= Opcodes.ACC_STATIC, "changed access");
            mutate(raw, owner, n -> method(n, selector).instructions.insert(new InsnNode(Opcodes.NOP)), "changed instructions");
        }
        Map<String, ClassNode> finalNodes = new HashMap<>();
        for (String owner : List.of(ENTITY, ARROW)) {
            ClassNode mixin = node(PREFIX + MIXINS.get(owner)), fixture = installed(raw.get(owner), mixin);
            finalNodes.put(owner, fixture);
            check(NativeCirculationListeningMotionMixinPlugin.nativeSitesSupported(raw.get(owner)), "native exact site count");
            check(!NativeCirculationListeningMotionMixinPlugin.installedCallbacksSupported(raw.get(owner)), "untransformed native class has no proof");
            check(NativeCirculationListeningMotionMixinPlugin.installedCallbacksSupported(fixture), "installed callback fixture");
            for (MethodNode callback : mixin.methods) if (callback.name.startsWith("academy$listening")) {
                ClassNode missing = copy(fixture); missing.methods.removeIf(m -> m.name.equals(callback.name));
                check(!NativeCirculationListeningMotionMixinPlugin.installedCallbacksSupported(missing), "missing callback refused");
                ClassNode wrong = copy(fixture); MethodNode changed = byName(wrong, callback.name);
                call(changed, callback.name.endsWith("Raw") ? "beforeRaw" : "physicalSet").desc = "()V";
                check(!NativeCirculationListeningMotionMixinPlugin.installedCallbacksSupported(wrong), "wrong bridge descriptor refused");
            }
        }
        ClassNode wrongOrdinal = copy(finalNodes.get(ENTITY)); MethodNode move = method(wrongOrdinal, MOVE);
        MethodInsnNode untouched = call(move, "setPos"), physical = call(move, "academy$listeningPhysical");
        AbstractInsnNode place = new InsnNode(Opcodes.NOP); move.instructions.set(untouched, place);
        move.instructions.set(physical, untouched); move.instructions.set(place, physical);
        check(!NativeCirculationListeningMotionMixinPlugin.installedCallbacksSupported(wrongOrdinal), "noPhysics ordinal never admitted");
        ClassNode late = copy(finalNodes.get(ENTITY)); MethodNode writer = method(late, "setPosRaw(DDD)V");
        MethodInsnNode observer = call(writer, "academy$listeningRaw"); writer.instructions.remove(observer); writer.instructions.add(observer);
        check(!NativeCirculationListeningMotionMixinPlugin.installedCallbacksSupported(late), "observer after position write refused");
        finalTiming(raw, finalNodes);
        stateChecks(); originalCallChecks(); observerChecks(node("cn.academy.port.highpower.CirculationListeningMotionProof"));
        if (args.length > 0) for (String owner : List.of(ENTITY, ARROW)) {
            ClassNode real = new ClassNode(); new ClassReader(Files.readAllBytes(Path.of(args[0], owner.replace('.', '/') + ".class"))).accept(real, 0);
            check(NativeCirculationListeningMotionMixinPlugin.installedCallbacksSupported(real), "real final exported hook " + owner);
        }
        System.out.println("Circulation listening native seam: " + checks + " checks passed");
    }
    private static void stateChecks() {
        CirculationListeningMotionProof.stopped(null);
        Object owner = new Object(); var state = new CirculationListeningMotionProof.State();
        check(state.add(owner) && state.add(owner), "watch token idempotent");
        check(CirculationListeningMotionProof.watchReferenceCount() == 1 && CirculationListeningMotionProof.watchedEntityCount() == 1, "one state and lease");
        check(state.snapshot().physicalWrites() == 0, "fresh watch has no physical evidence");
        state.observe(1, 2, 3, 1, 2, 3); check(state.snapshot().discontinuity() == 0, "raw no-op does not invalidate");
        check(state.begin(2, 2, 3), "physical permit entered"); state.observe(1, 2, 3, 2, 2, 3); state.end(true);
        check(state.snapshot().physicalWrites() == 1 && state.snapshot().discontinuity() == 0, "one physical write attested");
        state.observe(2, 2, 3, 2.01, 2, 3); state.observe(2.01, 2, 3, 2, 2, 3);
        check(state.snapshot().discontinuity() == 2, "small teleport and return both invalidate");
        check(state.begin(3, 2, 3), "new permit"); state.observe(2, 2, 3, 2.5, 2, 3); state.observe(2.5, 2, 3, 3, 2, 3); state.end(true);
        check(state.snapshot().physicalWrites() == 1 && state.snapshot().discontinuity() == 4, "mismatched write poisons remaining permit");
        check(state.begin(4, 2, 3) && !state.begin(4, 2, 3), "recursive physical operation refused");
        state.observe(3, 2, 3, 4, 2, 3); state.end(true);
        check(state.snapshot().physicalWrites() == 1 && state.snapshot().discontinuity() == 6, "recursive write cannot consume parent permit");
        check(state.begin(5, 2, 3), "exception permit"); state.observe(4, 2, 3, 5, 2, 3); state.end(false);
        check(state.snapshot().discontinuity() == 7, "native exception invalidates history");
        check(state.begin(6, 2, 3), "finally cleaned after exception"); state.observe(5, 2, 3, 6, 2, 3); state.end(true);
        check(state.snapshot().physicalWrites() == 3, "later clean native write can establish fresh evidence");
        check(state.remove(owner) && state.snapshot() == null, "last owner releases state");
        check(CirculationListeningMotionProof.watchReferenceCount() == 0 && CirculationListeningMotionProof.watchedEntityCount() == 0, "complete release");
        var all = new ArrayList<CirculationListeningMotionProof.State>();
        for (int i = 0; i < 1024; i++) { var next = new CirculationListeningMotionProof.State(); check(next.add(owner), "global slot"); all.add(next); }
        check(!new CirculationListeningMotionProof.State().add(owner), "global cap rejects before allocation is retained");
        for (var entry : all) entry.remove(owner);
        var shared = new CirculationListeningMotionProof.State(); var owners = new ArrayList<Object>();
        for (int i = 0; i < 64; i++) { Object token = new Object(); owners.add(token); check(shared.add(token), "shared owner lease"); }
        check(!shared.add(new Object()), "per-entity owner cap");
        for (Object token : owners) shared.remove(token);
        for (int i = 0; i < 100_000; i++) {
            var cycle = new CirculationListeningMotionProof.State(); if (!cycle.add(owner) || !cycle.remove(owner)) throw new AssertionError("lifecycle leak");
        }
        check(CirculationListeningMotionProof.watchReferenceCount() == 0 && CirculationListeningMotionProof.watchedEntityCount() == 0, "100k lifecycle operations retain no tracked states");
        var stale = new CirculationListeningMotionProof.State(); stale.add(owner); CirculationListeningMotionProof.stopped(null);
        check(stale.snapshot() == null && stale.remove(owner), "old-server leases expire without global entity traversal");
        check(CirculationListeningMotionProof.watchReferenceCount() == 0, "stale release cannot underflow new session counter");
    }
    private static void originalCallChecks() {
        int[] calls = {0}; CirculationListeningMotionProof.physicalSet(null, 1, 2, 3, values -> { calls[0]++; return null; });
        check(calls[0] == 1, "unwatched original called exactly once");
        RuntimeException expected = new RuntimeException("native failure");
        try { CirculationListeningMotionProof.physicalSet(null, 1, 2, 3, values -> { calls[0]++; throw expected; }); throw new AssertionError("swallowed native exception"); }
        catch (RuntimeException actual) { check(actual == expected && calls[0] == 2, "native failure preserved without retry"); }
    }
    private static void observerChecks(ClassNode node) {
        for (MethodNode method : node.methods) for (AbstractInsnNode i : method.instructions) if (i instanceof MethodInsnNode c)
            check(!List.of("getEntities", "getChunk", "getChunkNow", "clip", "setPos", "setPosRaw", "setDeltaMovement", "teleport", "hurt").contains(c.name), "observer has no world scans or physics mutations");
        MethodNode physical = byName(node, "physicalSet"); long calls = 0;
        for (AbstractInsnNode i : physical.instructions) if (i instanceof MethodInsnNode c && c.name.equals("call")) calls++;
        check(calls == 1 && physical.tryCatchBlocks.stream().anyMatch(t -> t.type == null), "one original call and finally cleanup");
        check(node.fields.stream().noneMatch(f -> f.desc.contains("Map;") || f.desc.contains("Set;") || f.desc.contains("List;")), "no global entity or owner collections");
    }
    private static void finalTiming(Map<String, ClassNode> raw, Map<String, ClassNode> finalNodes) throws Exception {
        var plugin = new NativeCirculationListeningMotionMixinPlugin(); var admitted = plugin.getClass().getDeclaredField("admitted"); admitted.setAccessible(true); admitted.setBoolean(plugin, true);
        Class<?> type = Class.forName(PREFIX + "NativeCirculationListeningMotionMixinPlugin$FinalWitness"); var constructor = type.getDeclaredConstructor(plugin.getClass()); constructor.setAccessible(true);
        var witness = (org.spongepowered.asm.mixin.transformer.ext.IExtension) constructor.newInstance(plugin);
        int count = 0;
        for (String owner : List.of(ENTITY, ARROW)) {
            String mixin = PREFIX + MIXINS.get(owner); plugin.preApply(owner, raw.get(owner), mixin, null); ClassNode merged = merged(raw.get(owner), node(mixin));
            plugin.postApply(owner, merged, mixin, null); check(!NativeCirculationListeningMotionMixinPlugin.available(), "merge alone cannot enable sensing");
            witness.export(null, owner, false, merged); check(!NativeCirculationListeningMotionMixinPlugin.available(), "uninstalled final callback refused");
            witness.export(null, owner, false, finalNodes.get(owner)); check(NativeCirculationListeningMotionMixinPlugin.available() == (++count == 2), "both final native classes required");
        }
        witness.export(null, ARROW, false, raw.get(ARROW)); check(!NativeCirculationListeningMotionMixinPlugin.available(), "final hook loss revokes sensing");
    }
    private static ClassNode merged(ClassNode raw, ClassNode mixin) {
        ClassNode result = copy(raw); result.interfaces.addAll(mixin.interfaces);
        for (FieldNode f : mixin.fields) result.fields.add(new FieldNode(f.access, f.name, f.desc, f.signature, f.value));
        for (MethodNode m : mixin.methods) if (m.name.startsWith("academy$")) {
            MethodNode copied = copy(m);
            for (AbstractInsnNode i : copied.instructions) if (i instanceof FieldInsnNode f && f.owner.equals(mixin.name)) f.owner = result.name;
            result.methods.add(copied);
        }
        return result;
    }
    private static ClassNode installed(ClassNode raw, ClassNode mixin) {
        ClassNode result = merged(raw, mixin);
        if (result.name.equals(ENTITY.replace('.', '/'))) {
            MethodNode move = method(result, MOVE); int ordinal = 0;
            for (AbstractInsnNode i : move.instructions.toArray()) if (i instanceof MethodInsnNode c && c.name.equals("setPos") && ordinal++ == 1) replace(result, move, c, "academy$listeningPhysical");
            MethodNode rawWrite = method(result, "setPosRaw(DDD)V"), hook = byName(result, "academy$listeningRaw");
            rawWrite.instructions.insert(new MethodInsnNode(Opcodes.INVOKESPECIAL, result.name, hook.name, hook.desc, false));
        } else replace(result, method(result, "tick()V"), call(method(result, "tick()V"), "setPos"), "academy$listeningArrow");
        return result;
    }
    private static void replace(ClassNode owner, MethodNode body, MethodInsnNode before, String after) { MethodNode hook = byName(owner, after); body.instructions.set(before, new MethodInsnNode(Opcodes.INVOKESPECIAL, owner.name, hook.name, hook.desc, false)); }
    private static void mutate(Map<String, ClassNode> original, String owner, Consumer<ClassNode> mutation, String why) { var changed = new HashMap<>(original); ClassNode copy = copy(original.get(owner)); changed.put(owner, copy); mutation.accept(copy); check(!NativeCirculationListeningMotionMixinPlugin.supported(changed), why); }
    private static ClassNode node(String owner) throws Exception { try (var in = CirculationListeningNativeSeamRegressionTest.class.getResourceAsStream("/" + owner.replace('.', '/') + ".class")) { if (in == null) throw new AssertionError(owner); ClassNode n = new ClassNode(); new ClassReader(in).accept(n, 0); return n; } }
    private static MethodNode method(ClassNode n, String selector) { return n.methods.stream().filter(m -> (m.name + m.desc).equals(selector)).findFirst().orElseThrow(); }
    private static MethodNode byName(ClassNode n, String name) { return n.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(); }
    private static MethodInsnNode call(MethodNode m, String name) { for (AbstractInsnNode i : m.instructions) if (i instanceof MethodInsnNode c && c.name.equals(name)) return c; throw new AssertionError(name); }
    private static ClassNode copy(ClassNode source) { ClassNode n = new ClassNode(); source.accept(n); return n; }
    private static MethodNode copy(MethodNode source) { MethodNode n = new MethodNode(source.access, source.name, source.desc, source.signature, source.exceptions.toArray(String[]::new)); source.accept(n); return n; }
    private static void check(boolean yes, String message) { checks++; if (!yes) throw new AssertionError(message); }
}
