package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeBrakingTouchMixinPlugin;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** CPU checks for the independent NEW422 seam; actual accepted-packet contact remains a native GameTest. */
public final class InertialAnchorLandingReceiptRegressionTest {
    private static final String LIVING = "net/minecraft/world/entity/LivingEntity";
    private static final String PROOF = "cn/academy/port/highpower/BrakingTouchLandingProof";
    private static final String CAUSE = "(FFLnet/minecraft/world/damagesource/DamageSource;)Z";
    private static final String HURT = "(Lnet/minecraft/world/damagesource/DamageSource;F)Z";
    private static int checks;
    public static void main(String[] args) throws Exception {
        check(!NativeBrakingTouchMixinPlugin.isReviewedLandingHurtSupported(), "NEW422 unavailable without final witness");
        ClassNode raw = node(LIVING), mixin = node("cn/academy/port/highpower/mixin/LivingEntityBrakingTouchMixin");
        check(NativeBrakingTouchMixinPlugin.nativeLandingHurtSiteSupported(raw), "reviewed unique native fall hurt admitted");
        ClassNode duplicate = copy(raw); MethodNode duplicateBody = method(duplicate, "causeFallDamage", CAUSE);
        duplicateBody.instructions.insert(call(duplicateBody, "hurt"), call(duplicateBody, "hurt").clone(new HashMap<>()));
        check(!NativeBrakingTouchMixinPlugin.nativeLandingHurtSiteSupported(duplicate), "duplicate native hurt refused");
        ClassNode wrongOpcode = copy(raw);
        call(method(wrongOpcode, "causeFallDamage", CAUSE), "hurt").setOpcode(Opcodes.INVOKESPECIAL);
        check(!NativeBrakingTouchMixinPlugin.nativeLandingHurtSiteSupported(wrongOpcode), "changed native dispatch refused");
        ClassNode installed = fixture(raw, mixin);
        check(NativeBrakingTouchMixinPlugin.installedCallbacksSupported(installed), "NEW413 still installed");
        check(NativeBrakingTouchMixinPlugin.installedLandingHurtCallbackSupported(installed), "NEW422 installed callsite witnessed");
        ClassNode absent = copy(installed); absent.methods.removeIf(m -> m.name.equals("anchor$landingHurt"));
        check(!NativeBrakingTouchMixinPlugin.installedLandingHurtCallbackSupported(absent), "missing NEW422 callback refused");
        check(NativeBrakingTouchMixinPlugin.installedCallbacksSupported(absent), "missing NEW422 callback preserves NEW413");
        ClassNode uninstalled = copy(installed); MethodNode body = method(uninstalled, "causeFallDamage", CAUSE);
        body.instructions.set(call(body, "anchor$landingHurt"), new MethodInsnNode(Opcodes.INVOKEVIRTUAL, LIVING, "hurt", HURT, false));
        check(!NativeBrakingTouchMixinPlugin.installedLandingHurtCallbackSupported(uninstalled), "merged-only callback refused");
        check(NativeBrakingTouchMixinPlugin.installedCallbacksSupported(uninstalled), "uninstalled NEW422 preserves NEW413");
        ClassNode doubled = copy(installed); body = method(doubled, "causeFallDamage", CAUSE);
        body.instructions.insert(call(body, "anchor$landingHurt"), call(body, "anchor$landingHurt").clone(new HashMap<>()));
        check(!NativeBrakingTouchMixinPlugin.installedLandingHurtCallbackSupported(doubled), "duplicate final wrapper refused");
        ClassNode wrongBridge = copy(installed);
        call(byName(wrongBridge, "anchor$landingHurt"), "nativeLandingHurt").desc = "()Z";
        check(!NativeBrakingTouchMixinPlugin.installedLandingHurtCallbackSupported(wrongBridge), "wrong bridge signature refused");
        ClassNode reordered = copy(installed); body = method(reordered, "causeFallDamage", CAUSE);
        MethodInsnNode hurt = call(body, "anchor$landingHurt"); body.instructions.remove(hurt); body.instructions.insert(hurt);
        check(!NativeBrakingTouchMixinPlugin.installedLandingHurtCallbackSupported(reordered), "hurt before positive native calculation refused");
        guards(node(PROOF), node(PROOF + "$LandingHurt"));
        originalArguments();
        failureIsolation();
        if (args.length > 0) {
            ClassNode real = new ClassNode();
            new ClassReader(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[0], LIVING + ".class"))).accept(real, 0);
            check(NativeBrakingTouchMixinPlugin.installedLandingHurtCallbackSupported(real), "real exported NEW422 transformation");
        }
        System.out.println("Inertial anchor landing receipt: " + checks + " checks passed");
    }
    private static void guards(ClassNode proof, ClassNode receipt) {
        MethodNode nativeHurt = byName(proof, "nativeLandingHurt");
        for (String gate : List.of("claimed", "window", "positiveEvent", "hurtSeen", "landingAttributes"))
            check(field(nativeHurt, gate), "native admission requires " + gate);
        for (String gate : List.of("unchanged", "fall", "isFinite", "landingHurtAvailable", "active", "landingPhysicalUnchanged"))
            check(hasCall(nativeHurt, gate), "native admission checks " + gate);
        check(nativeHurt.tryCatchBlocks.stream().anyMatch(t -> t.type == null), "receipt always closes on native unwind");
        check(hasCall(byName(proof, "landingHurtAvailable"), "available"), "actual Post proof participates in availability");
        MethodNode physical = byName(proof, "landingPhysicalUnchanged");
        check(hasCall(physical, "physicalBasic") && !hasCall(physical, "basic"), "temporary native HP zero uses separate physical check");
        for (String preserved : List.of("inAccepted", "inBlock", "inCause", "pose", "position", "body", "velocity", "fall", "effects", "landingAttributes"))
            check(field(physical, preserved), "receipt physical snapshot preserves " + preserved);
        check(hasCall(physical, "supportUnchanged"), "receipt preserves support");
        check(hasCall(byName(proof, "basic"), "isAlive"), "NEW413 still requires alive player");
        MethodNode contact = byName(nodeUnchecked(PROOF + "$Contact"), "unchanged");
        for (String exact : List.of("health", "absorption", "hurt")) check(field(contact, exact), "NEW413 retains exact " + exact);
        MethodNode bind = byName(proof, "bindLandingHurt");
        for (String state : List.of("bound", "closed", "poisoned")) check(field(bind, state), "binding enforces " + state);
        check(hasCall(bind, "unchanged"), "binding rechecks unchanged pre-hurt snapshot");
        MethodNode valid = byName(receipt, "valid");
        for (String state : List.of("bound", "claimed", "closed", "poisoned")) check(field(valid, state), "valid enforces " + state);
        check(receipt.methods.stream().filter(m -> m.name.equals("<init>")).allMatch(m -> Modifier.isPrivate(m.access)), "receipt has no public constructor");
        check(hasCall(byName(receipt, "claim"), "valid"), "claim consumes only valid receipt");
    }
    private static void originalArguments() {
        for (float amount : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY, 5}) {
            int[] calls = {0};
            boolean result = BrakingTouchLandingProof.nativeLandingHurt(null, null, amount, supplied -> {
                calls[0]++;
                check(supplied.length == 3 && supplied[0] == null && supplied[1] == null
                        && Float.floatToRawIntBits((Float)supplied[2]) == Float.floatToRawIntBits(amount), "original hurt receives same three arguments");
                return true;
            });
            check(result && calls[0] == 1, "unscoped native hurt called exactly once");
        }
        RuntimeException expected = new RuntimeException("native throw"); int[] calls = {0};
        try {
            BrakingTouchLandingProof.nativeLandingHurt(null, null, 3, supplied -> { calls[0]++; throw expected; });
            throw new AssertionError("native exception missing");
        } catch (RuntimeException actual) { check(actual == expected && calls[0] == 1, "native exception preserved without retry"); }
        check(BrakingTouchLandingProof.bindLandingHurt(null, null) == null, "no generic FALL receipt outside native scope");
    }
    private static void failureIsolation() throws Exception {
        var base = NativeBrakingTouchMixinPlugin.class.getDeclaredField("reviewedLandingSupported"); base.setAccessible(true);
        var anchor = NativeBrakingTouchMixinPlugin.class.getDeclaredField("reviewedLandingHurtSupported"); anchor.setAccessible(true);
        var failed = NativeBrakingTouchMixinPlugin.class.getDeclaredField("landingHurtFailed"); failed.setAccessible(true);
        boolean oldBase = base.getBoolean(null), oldAnchor = anchor.getBoolean(null), oldFailed = failed.getBoolean(null);
        try {
            base.setBoolean(null, true); anchor.setBoolean(null, true); failed.setBoolean(null, false);
            check(NativeBrakingTouchMixinPlugin.isReviewedLandingHurtSupported(), "independent availability can enable NEW422");
            NativeBrakingTouchMixinPlugin.disableLandingHurtAfterCallbackFailure(new IllegalStateException("expected test failure"));
            check(!NativeBrakingTouchMixinPlugin.isReviewedLandingHurtSupported(), "NEW422 failure disables NEW422");
            check(NativeBrakingTouchMixinPlugin.isReviewedLandingSupported(), "NEW422 failure preserves NEW413");
        } finally {
            base.setBoolean(null, oldBase); anchor.setBoolean(null, oldAnchor); failed.setBoolean(null, oldFailed);
        }
    }
    private static ClassNode fixture(ClassNode raw, ClassNode mixin) {
        ClassNode result = copy(raw);
        for (MethodNode m : mixin.methods) if (m.name.startsWith("academy$") || m.name.startsWith("anchor$")) result.methods.add(copy(m));
        MethodNode body = method(result, "causeFallDamage", CAUSE);
        replace(result, body, "onLivingFall", "academy$brakingEvent"); replace(result, body, "hurt", "anchor$landingHurt");
        MethodNode cause = byName(result, "academy$brakingCause");
        body.instructions.insert(new MethodInsnNode(Opcodes.INVOKESPECIAL, result.name, cause.name, cause.desc, false));
        return result;
    }
    private static void replace(ClassNode owner, MethodNode body, String old, String replacement) {
        MethodNode hook = byName(owner, replacement);
        body.instructions.set(call(body, old), new MethodInsnNode(Opcodes.INVOKESPECIAL, owner.name, hook.name, hook.desc, false));
    }
    private static boolean field(MethodNode m, String name) { for (AbstractInsnNode i : m.instructions) if (i instanceof FieldInsnNode f && f.name.equals(name)) return true; return false; }
    private static boolean hasCall(MethodNode m, String name) { for (AbstractInsnNode i : m.instructions) if (i instanceof MethodInsnNode c && c.name.equals(name)) return true; return false; }
    private static MethodInsnNode call(MethodNode m, String name) { for (AbstractInsnNode i : m.instructions) if (i instanceof MethodInsnNode c && c.name.equals(name)) return c; throw new AssertionError("Missing " + name); }
    private static MethodNode byName(ClassNode n, String name) { return n.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(); }
    private static MethodNode method(ClassNode n, String name, String desc) { return n.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElseThrow(); }
    private static ClassNode copy(ClassNode n) { ClassNode result = new ClassNode(); n.accept(result); return result; }
    private static MethodNode copy(MethodNode m) { MethodNode result = new MethodNode(m.access,m.name,m.desc,m.signature,m.exceptions.toArray(String[]::new)); m.accept(result); return result; }
    private static ClassNode node(String name) throws Exception { try (var stream = InertialAnchorLandingReceiptRegressionTest.class.getResourceAsStream("/" + name + ".class")) { if (stream == null) throw new AssertionError("Missing " + name); ClassNode n = new ClassNode(); new ClassReader(stream).accept(n,0); return n; } }
    private static ClassNode nodeUnchecked(String name) { try { return node(name); } catch (Exception failure) { throw new AssertionError(failure); } }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
