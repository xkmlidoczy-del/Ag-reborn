package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeScatterPlateMixinPlugin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Actual native bytecode and production adapter checks; runtime body/contact cases use GameTests. */
public final class AutonomousVectorArrowNativeSeamRegressionTest {
    private static final String ROOT = "cn/academy/port/highpower/", RECEIPT = ROOT + "AutonomousVectorArrowReceipts";
    private static final String ARROW = "net/minecraft/world/entity/projectile/AbstractArrow";
    private static final String OPERATION = "com/llamalad7/mixinextras/injector/wrapoperation/Operation";
    private static int checks;
    public static void main(String[] args) throws Exception {
        Map<String, ClassNode> nativeClasses = new HashMap<>();
        for (String key : NativeScatterPlateMixinPlugin.reviewedMethods()) {
            String name = key.substring(0, key.indexOf('#'));
            if (!nativeClasses.containsKey(name)) nativeClasses.put(name, node(name.replace('.', '/')));
        }
        check(NativeScatterPlateMixinPlugin.supported(nativeClasses), "same independently pinned native arrow path");
        ClassNode nativeArrow = nativeClasses.get(ARROW.replace('/', '.'));
        MethodNode body = method(nativeArrow, "onHitEntity");
        check(calls(body, "net/minecraft/world/entity/Entity", "hurt") == 1, "exactly one original native hurt operand");
        MethodNode tick = method(nativeArrow, "tick");
        check(calls(tick, ARROW, "findHitEntity") == 1, "single original find-hit seam");
        check(calls(tick, "net/neoforged/neoforge/event/EventHooks", "onProjectileImpact") == 1, "single original impact-event seam");
        ClassNode util = nativeClasses.get("net.minecraft.world.entity.projectile.ProjectileUtil");
        String overload = "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/EntityHitResult;";
        MethodNode defaultPadding = util.methods.stream().filter(method -> method.name.equals("getEntityHitResult") && method.desc.equals(overload)).findFirst().orElseThrow();
        var paddingOperations = new java.util.ArrayList<org.objectweb.asm.tree.AbstractInsnNode>();
        for (var operation : defaultPadding.instructions) if (operation.getOpcode() >= 0) paddingOperations.add(operation);
        check(defaultPadding.tryCatchBlocks.isEmpty() && paddingOperations.size() == 9, "original six-argument body-padding overload");
        for (int i = 0; i < 6; i++) check(paddingOperations.get(i) instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == i, "native sweep argument forwarded " + i);
        check(paddingOperations.get(6) instanceof LdcInsnNode padding && Float.valueOf(.3F).equals(padding.cst), "native body padding is exact 0.3F");
        check(paddingOperations.get(7) instanceof MethodInsnNode call && call.owner.equals(util.name) && call.name.equals("getEntityHitResult")
                && call.desc.equals(overload.replace("Predicate;)", "Predicate;F)")) && paddingOperations.get(8).getOpcode() == Opcodes.ARETURN, "native padded result forwarded unchanged");
        check(NativeScatterPlateMixinPlugin.autonomousVectorCirclePaddingSupported(util), "NEW452-only runtime padding fingerprint");
        LdcInsnNode padding = (LdcInsnNode) paddingOperations.get(6); Object originalPadding = padding.cst; padding.cst = .5F;
        check(!NativeScatterPlateMixinPlugin.autonomousVectorCirclePaddingSupported(util), "changed native padding refuses NEW452");
        check(NativeScatterPlateMixinPlugin.supported(nativeClasses), "NEW452 padding failure leaves earlier skill eligibility unchanged");
        padding.cst = originalPadding;
        check(NativeScatterPlateMixinPlugin.autonomousVectorCirclePaddingSupported(util), "restored exact padding proof");
        check(!NativeScatterPlateMixinPlugin.autonomousVectorCirclePaddingSupported(null), "missing native padding proof refuses");
        String utilName = util.name; util.name = "unreviewed/ProjectileUtil";
        check(!NativeScatterPlateMixinPlugin.autonomousVectorCirclePaddingSupported(util), "wrong padding owner refuses"); util.name = utilName;
        ClassNode mixin = node(ROOT + "mixin/AbstractArrowScatterPlateMixin");
        MethodNode observed = method(mixin, "academy$observeClippedSegment");
        check(first(observed, OPERATION, "call") < first(observed, RECEIPT, "observe"), "receipt observes original native result");
        check(calls(observed, OPERATION, "call") == 1 && calls(observed, RECEIPT, "observe") == 1, "finder and observer each called once");
        MethodNode impact = method(mixin, "academy$consumeBeforeImpactCallbacks");
        check(first(impact, RECEIPT, "beforeImpact") < first(impact, OPERATION, "call"), "snapshot checked before arbitrary impact callbacks");
        check(first(impact, RECEIPT, "afterImpactEvent") > first(impact, OPERATION, "call"), "cancellation checked after original impact callbacks");
        check(!impact.tryCatchBlocks.isEmpty(), "original impact exception/finally cleanup retained");
        for (String[] seam : new String[][] {
                {"academy$observeNativeTick", "beginTick", "endTick"},
                {"academy$nativeImpactWindow", "beginImpact", "endImpact"},
                {"academy$nativeBodyHurtWindow", "beginHurt", "endHurt"}}) {
            MethodNode host = method(mixin, seam[0]);
            check(calls(host, RECEIPT, seam[1]) == 1 && calls(host, RECEIPT, seam[2]) >= 1, "bounded " + seam[0] + " scope");
            check(!host.tryCatchBlocks.isEmpty(), "exception cleanup for " + seam[0]);
        }
        for (String name : List.of("academy$observeClippedSegment", "academy$consumeBeforeImpactCallbacks", "academy$nativeImpactWindow", "academy$nativeBodyHurtWindow")) {
            AnnotationNode annotation = annotation(method(mixin, name), "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
            check(Integer.valueOf(1).equals(value(annotation, "require")) && Integer.valueOf(1).equals(value(annotation, "expect"))
                    && Integer.valueOf(1).equals(value(annotation, "allow")), "exact retained wrapper cardinality " + name);
        }
        for (String[] target : new String[][] {
                {"academy$observeClippedSegment", "L" + ARROW + ";findHitEntity(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/EntityHitResult;"},
                {"academy$consumeBeforeImpactCallbacks", "Lnet/neoforged/neoforge/event/EventHooks;onProjectileImpact(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/world/phys/HitResult;)Z"},
                {"academy$nativeImpactWindow", "L" + ARROW + ";hitTargetOrDeflectSelf(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/entity/projectile/ProjectileDeflection;"},
                {"academy$nativeBodyHurtWindow", "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"}})
            check(hasValue(annotation(method(mixin, target[0]), "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;"), target[1]), "exact original wrapped operand " + target[0]);
        ClassNode owner = node(ROOT + "mixin/ProjectileScatterPlateMixin");
        for (String name : List.of("academy$clearOnOwnerWrite", "academy$clearOnDeflection"))
            check(calls(method(owner, name), RECEIPT, "ownerChanged") == 1, "same-owner write/deflection invalidation " + name);

        ClassNode receipt = node(RECEIPT), capture = node(RECEIPT + "$Capture"), scope = node(RECEIPT + "$Tick");
        check(method(receipt, "capture").desc.equals("(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/damagesource/DamageSource;)L" + RECEIPT + "$Capture;"), "controller API source-bound capture");
        check(method(capture, "from").desc.equals("()Lnet/minecraft/world/phys/Vec3;")
                && method(capture, "contact").desc.equals("()Lnet/minecraft/world/phys/Vec3;"), "controller receives actual native endpoints");
        check(method(capture, "valid").desc.equals("(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/damagesource/DamageSource;)Z"), "same player/source live-window validation");
        for (ClassNode opaque : List.of(capture, scope)) {
            check((method(opaque, "<init>").access & Opcodes.ACC_PRIVATE) != 0, "opaque constructor " + opaque.name);
            check(opaque.fields.stream().allMatch(field -> (field.access & Opcodes.ACC_PRIVATE) != 0), "opaque state " + opaque.name);
        }
        for (String adapter : List.of("beginTick", "observe", "beforeImpact", "afterImpactEvent", "beginImpact", "beginHurt"))
            check(calls(method(receipt, adapter), RECEIPT, "nativeCaller") == 1, "public " + adapter + " cannot manufacture native authority");
        Set<String> mutations = Set.of("setPos", "moveTo", "setDeltaMovement", "setOwner", "setBaseDamage", "setPierceLevel", "hurt", "discard", "deflect", "hitTargetOrDeflectSelf");
        for (ClassNode type : List.of(receipt, capture, scope, node(RECEIPT + "$Snapshot"), node(RECEIPT + "$Defender")))
            for (MethodNode method : type.methods) for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) {
                check(!call.owner.startsWith("java/lang/reflect/"), "no reflective native fabrication");
                check(!(call.owner.startsWith("net/minecraft/") && mutations.contains(call.name)), "read-only native observer " + call.name);
            }
        String source = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/AutonomousVectorArrowReceipts.java"));
        check(source.contains("start.equals(arrow.position())") && source.contains("tick.from = start"), "A is original native finder argument at current arrow position");
        check(source.contains("defender.bounds.inflate((double) .3F)") && source.contains("body.clip(start, end)")
                && !source.contains("result.getLocation()"), "B is first inflated body entry, never native entity feet");
        check(source.contains("body.contains(start)") && source.contains("!inRange(player.position(), start)")
                && source.contains("AutonomousVectorCircleRules.inRange"), "outside-body and exact owner-distance admission");
        check(source.contains("segment.dot(velocity) > 0") && source.contains("segment.cross(velocity).lengthSqr()"), "actual segment direction aligned to original velocity");
        check(source.contains("bodies, SCAN_LIMIT + 1") && source.contains("bodies.size() > SCAN_LIMIT")
                && !source.contains("getAllEntities") && !source.contains("getEntities()."), "bounded local route scan with overflow refusal");
        check(source.contains("DisplacedProjectionWorld.volume") && source.contains("ClipContext.Block.COLLIDER") && source.contains("bounds.contains(start)"), "loaded route, solid and origin-overlap blockers");
        check(source.contains("tick.snapshot.frozen()") && source.contains("tick.defender.frozen()") && source.contains("WINDOWS.get(player) == this"), "frozen projectile/player and exact window rechecks");
        check(source.contains("window.claimed = true") && source.contains("TICKS.remove(tick.arrow, tick)") && source.contains("WINDOWS.remove(window.tick.defender.player, window)"), "one-use proof and deterministic cleanup");
        for (String foreign : List.of("MultiAxisReflectionArrowBridge", "TrajectoryReturnArrowBridge", "ShortInterceptionArrowBridge", "MagneticDomainArrows", "MagneticArrowSeparation", "IronSandLance", "ElectromasterPrototype", "ElectromasterChargeDecoy", "MagneticGroundStep"))
            check(source.contains(foreign + ".leased(arrow)"), "foreign arrow exclusion " + foreign);
        check(source.contains("academy:trajectory_return_used") && source.contains("academy:short_interception_used"), "permanent foreign markers refuse");
        check(!source.contains("shooter.position().distanceTo") && !source.contains("shooter.position().distanceToSqr"), "no shooter-distance restriction");
        System.out.println("NEW452 native arrow seams: " + checks + " checks passed; native collision behavior is covered separately by GameTests");
    }
    private static ClassNode node(String name) throws Exception {
        try (var input = AutonomousVectorArrowNativeSeamRegressionTest.class.getResourceAsStream("/" + name + ".class")) {
            if (input == null) throw new AssertionError("Missing class " + name);
            ClassNode result = new ClassNode(); new ClassReader(input).accept(result, 0); return result;
        }
    }
    private static MethodNode method(ClassNode type, String name) { return type.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static long calls(MethodNode method, String owner, String name) {
        long count = 0; for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) count++; return count;
    }
    private static int first(MethodNode method, String owner, String name) {
        int index = 0; for (var instruction : method.instructions) { if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return index; index++; }
        throw new AssertionError("Missing " + owner + "." + name + " in " + method.name);
    }
    private static AnnotationNode annotation(MethodNode method, String descriptor) {
        if (method.visibleAnnotations != null) for (var annotation : method.visibleAnnotations) if (annotation.desc.equals(descriptor)) return annotation;
        if (method.invisibleAnnotations != null) for (var annotation : method.invisibleAnnotations) if (annotation.desc.equals(descriptor)) return annotation;
        throw new AssertionError("Missing annotation " + descriptor + " on " + method.name);
    }
    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2) if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }
    private static boolean hasValue(Object value, String expected) {
        if (expected.equals(value)) return true;
        if (value instanceof AnnotationNode annotation) return hasValue(annotation.values, expected);
        if (value instanceof List<?> values) return values.stream().anyMatch(item -> hasValue(item, expected));
        return false;
    }
    private static void check(boolean condition, String why) { if (!condition) throw new AssertionError(why); checks++; }
}
