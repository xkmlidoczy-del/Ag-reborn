package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.highpower.NewSkillRuntime;
import cn.academy.port.highpower.core.PulseShieldRules;
import java.util.List;
import java.util.Random;

/** Pure witnesses for the presentation of the seven new skills: every kind and phase draws, stays bounded, and refuses bad input. */
public final class NewSkillFxRegressionTest {
    private static final Point CAMERA = new Point(0, 0, 0), LOOK = new Point(0, 0, 1), UP = new Point(0, 1, 0), CASTER = new Point(1, 0, 12);
    private static int checks;
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    private static Point p(double x, double y, double z) { return new Point(x, y, z); }

    public static void main(String[] args) throws ReflectiveOperationException {
        int[] phases = {NewSkillRuntime.START, NewSkillRuntime.DETECT, NewSkillRuntime.APPLY, NewSkillRuntime.PLACED, NewSkillRuntime.RIDE, NewSkillRuntime.ARMED,
                NewSkillRuntime.DIVERT, NewSkillRuntime.JUMP, NewSkillRuntime.LIFT, NewSkillRuntime.RELEASE};
        List<Point> marks = List.of(p(2, 1, 18), p(-3, 1, 22), p(5, 2, 26));
        for (var kind : NewSkillRuntime.Kind.values())
            for (int phase : phases)
                for (VisualTier tier : new VisualTier[] {null, VisualTier.OFF, VisualTier.LOW, VisualTier.STANDARD, VisualTier.CINEMATIC})
                    for (double age = 0; age < NewSkillFx.LIFE; age += 3.5) {
                        var cue = new NewSkillFx.Cue(kind.ordinal(), phase, age, p(2, 1, 20), p(0, 0, 1), marks, 7);
                        var parts = NewSkillFx.build(cue, CASTER, 40, tier);
                        var effective = NewSkillFx.effective(tier);
                        var quads = VisualKit.projectParts(parts, CAMERA, LOOK, UP, effective, NewSkillFx.MAX_QUADS);
                        check(quads.size() <= NewSkillFx.MAX_QUADS, "within the quad budget");
                        for (var s : parts.strokes()) check(s.halfWidth() > 0 && s.alpha() >= 1 && s.alpha() <= 255 && s.segment().start().finite() && s.segment().end().finite(), "stroke valid");
                        for (var b : parts.sprites()) check(b.radius() > 0 && b.alpha() >= 1 && b.alpha() <= 255 && b.center().finite(), "sprite valid");
                        for (var q : quads) check(q.a().finite() && q.alpha() >= 1 && q.alpha() <= 255, "quad valid");
                    }
        check(NewSkillFx.effective(null) == VisualTier.STANDARD && NewSkillFx.effective(VisualTier.OFF) == VisualTier.STANDARD && NewSkillFx.effective(VisualTier.CINEMATIC) == VisualTier.CINEMATIC, "skills always show a cue; OFF/LOW fall back to STANDARD");
        // Each skill actually draws something in its main phase.
        int[][] main = {{0, NewSkillRuntime.APPLY}, {1, NewSkillRuntime.PLACED}, {2, NewSkillRuntime.START}, {3, NewSkillRuntime.ARMED}, {4, NewSkillRuntime.JUMP}, {5, NewSkillRuntime.RELEASE}, {6, NewSkillRuntime.ARMED}};
        for (int[] m : main) {
            var parts = NewSkillFx.build(new NewSkillFx.Cue(m[0], m[1], 2, p(2, 1, 20), p(0, 0, 1), List.of(p(0, 0, 14), p(10, 0, 14), p(20, 0, 20)), 3), CASTER, 10, VisualTier.CINEMATIC);
            check(!parts.strokes().isEmpty() || !parts.sprites().isEmpty() || !parts.decals().isEmpty(), "kind " + m[0] + " draws in phase " + m[1]);
        }
        var good = new NewSkillFx.Cue(0, NewSkillRuntime.APPLY, 2, p(2, 1, 20), p(0, 0, 1), marks, 7);
        check(NewSkillFx.build(good, CASTER, 1, VisualTier.CINEMATIC).equals(NewSkillFx.build(good, CASTER, 1, VisualTier.CINEMATIC)), "deterministic");
        check(NewSkillFx.build(null, CASTER, 1, VisualTier.CINEMATIC).strokes().isEmpty(), "null cue draws nothing");
        check(NewSkillFx.build(good, null, 1, VisualTier.CINEMATIC).strokes().isEmpty(), "no caster, no cue");
        check(NewSkillFx.build(new NewSkillFx.Cue(99, 0, 1, p(0, 0, 5), p(0, 0, 1), marks, 1), CASTER, 1, VisualTier.CINEMATIC).strokes().isEmpty(), "unknown kind draws nothing");
        check(NewSkillFx.build(new NewSkillFx.Cue(0, 0, NewSkillFx.LIFE, p(0, 0, 5), p(0, 0, 1), marks, 1), CASTER, 1, VisualTier.CINEMATIC).strokes().isEmpty(), "an expired cue draws nothing");
        check(NewSkillFx.build(new NewSkillFx.Cue(0, 0, Double.NaN, p(0, 0, 5), p(0, 0, 1), marks, 1), CASTER, 1, VisualTier.CINEMATIC).strokes().isEmpty(), "NaN age draws nothing");
        check(NewSkillFx.build(new NewSkillFx.Cue(0, 0, 1, p(Double.NaN, 0, 5), p(0, 0, 1), marks, 1), CASTER, 1, VisualTier.CINEMATIC).strokes().isEmpty(), "NaN point draws nothing");
        // Randomised receipts never break an invariant.
        Random random = new Random(2026);
        var kinds = NewSkillRuntime.Kind.values();
        for (int i = 0; i < 4000; i++) {
            var list = new java.util.ArrayList<Point>();
            for (int k = random.nextInt(8); k > 0; k--) list.add(p(random.nextGaussian() * 20, random.nextGaussian() * 4, random.nextGaussian() * 20 + 12));
            var cue = new NewSkillFx.Cue(random.nextInt(kinds.length), phases[random.nextInt(phases.length)], random.nextDouble() * NewSkillFx.LIFE,
                    p(random.nextGaussian() * 20, random.nextGaussian() * 4, random.nextGaussian() * 20 + 12), p(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()), list, random.nextLong());
            var parts = NewSkillFx.build(cue, p(random.nextGaussian() * 10, 0, random.nextGaussian() * 10 + 12), random.nextDouble() * 1200, VisualTier.CINEMATIC);
            for (var q : VisualKit.projectParts(parts, CAMERA, LOOK, UP, VisualTier.CINEMATIC, NewSkillFx.MAX_QUADS)) check(q.a().finite() && q.alpha() >= 1 && q.alpha() <= 255, "random quad valid");
        }
        // The wire visual validates its own bounds.
        var dim = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        var ok = new NewSkillRuntime.Visual(0, 1, new java.util.UUID(1, 2), 5, 1, 1, 1, dim, NewSkillRuntime.APPLY, net.minecraft.world.phys.Vec3.ZERO, net.minecraft.world.phys.Vec3.ZERO, List.of(), 0);
        check(ok.kind() == 0, "valid visual accepted");
        for (int bad = 0; bad < 6; bad++) {
            final int b = bad;
            try {
                new NewSkillRuntime.Visual(b == 0 ? 99 : 0, b == 1 ? -1 : 1, new java.util.UUID(1, 2), b == 2 ? 0 : 5, 1, 1, 1, dim, b == 3 ? 99 : NewSkillRuntime.APPLY,
                        b == 4 ? new net.minecraft.world.phys.Vec3(Double.NaN, 0, 0) : net.minecraft.world.phys.Vec3.ZERO, net.minecraft.world.phys.Vec3.ZERO,
                        b == 5 ? java.util.Collections.nCopies(17, net.minecraft.world.phys.Vec3.ZERO) : List.of(), 0);
                throw new AssertionError("invalid visual accepted: " + b);
            } catch (IllegalArgumentException expected) { checks++; }
        }
        checkAcceptedGeometry();
        checkDominionOrdering(dim);
        System.out.println("PASS " + checks + " new-skill presentation: every kind/phase bounded, deterministic, refuses invalid receipts");
    }

    private static FxLib.Parts accepted(NewSkillRuntime.Kind kind, int phase, Point point, Point direction, List<Point> marks) {
        return NewSkillFx.build(new NewSkillFx.Cue(kind.ordinal(), phase, 2, point, direction, marks, 1), CASTER, 3, VisualTier.STANDARD);
    }

    private static void checkAcceptedGeometry() {
        var eye = p(8, 1.62, 20);
        var target = p(12, 1, 24);
        var axis = p(.6, 0, .8);
        var sweep = accepted(NewSkillRuntime.Kind.PULSE_SHIELD, NewSkillRuntime.DETECT, eye, axis, List.of(target));
        check(sweep.decals().stream().anyMatch(d -> d.radius() == PulseShieldRules.RANGE && d.center().x() == eye.x()
                && d.center().z() == eye.z()), "pulse sweep is centered at the receipt with the real radius");
        var discharge = accepted(NewSkillRuntime.Kind.PULSE_SHIELD, NewSkillRuntime.APPLY, eye, axis, List.of(target));
        check(discharge.decals().isEmpty() && !discharge.strokes().isEmpty(), "discharge uses selected targets without repeating the sweep");

        var departure = p(0, 4, 20);
        var arrival = p(0, 8, 62);
        var folds = accepted(NewSkillRuntime.Kind.PHASE_CORRIDOR, NewSkillRuntime.JUMP, arrival, axis, List.of(departure, arrival));
        check(folds.strokes().size() == 16 && folds.decals().isEmpty(), "both genuine endpoints get folded brackets");
        for (var line : folds.strokes())
            check(line.segment().end().minus(line.segment().start()).length() < 2, "the two endpoints are never joined");

        var split = accepted(NewSkillRuntime.Kind.IMPULSE_SPLIT, NewSkillRuntime.DIVERT, target, axis, List.of());
        check(split.strokes().size() == 2 && split.strokes().get(0).segment().equals(split.strokes().get(1).segment()),
                "diverted projectile gets one direction drawn in two materials");
        var motion = split.strokes().getFirst().segment();
        check(motion.start().equals(target) && motion.end().minus(target).cross(axis).length() < 1e-10
                && motion.end().minus(target).dot(axis) > 0, "divert effect follows the reported axis");
        check(accepted(NewSkillRuntime.Kind.IMPULSE_SPLIT, NewSkillRuntime.DIVERT, target, p(0, 0, 0), List.of())
                .strokes().isEmpty(), "zero movement cannot invent a direction");

        var lift = accepted(NewSkillRuntime.Kind.ZENITH_DOMINION, NewSkillRuntime.LIFT, eye, p(0, 0, 0), List.of(target));
        check(lift.decals().isEmpty() && lift.strokes().size() == 4 && lift.sprites().getFirst().center().equals(target),
                "lift compresses locally around the accepted target");
        var release = accepted(NewSkillRuntime.Kind.ZENITH_DOMINION, NewSkillRuntime.RELEASE, eye, axis, List.of(target));
        var vector = release.strokes().getFirst().segment();
        check(release.decals().isEmpty() && vector.start().equals(target) && vector.end().y() < target.y()
                && vector.end().x() > target.x() && vector.end().z() > target.z(),
                "release streak goes outward and down from the actual target");
        check(accepted(NewSkillRuntime.Kind.ZENITH_DOMINION, NewSkillRuntime.RELEASE, eye, axis, List.of())
                .strokes().isEmpty(), "an empty release has no fabricated impact");
    }

    private static void checkDominionOrdering(net.minecraft.resources.ResourceLocation dimension) throws ReflectiveOperationException {
        var method = NewSkillEffects.class.getDeclaredMethod("acceptDominion", NewSkillRuntime.Visual.class);
        method.setAccessible(true);
        int kind = NewSkillRuntime.Kind.ZENITH_DOMINION.ordinal();
        var owner = new java.util.UUID(0, 451);
        java.util.function.IntFunction<NewSkillRuntime.Visual> receipt = phase -> new NewSkillRuntime.Visual(
                kind, 7, owner, 42, phase + 1, 2, 1, dimension, phase,
                net.minecraft.world.phys.Vec3.ZERO, net.minecraft.world.phys.Vec3.ZERO, List.of(), 1);
        NewSkillEffects.clear();
        check((boolean) method.invoke(null, receipt.apply(NewSkillRuntime.START)), "first dominion visual admitted");
        check((boolean) method.invoke(null, receipt.apply(NewSkillRuntime.LIFT)), "new lift replaces earlier presentation");
        check(!(boolean) method.invoke(null, receipt.apply(NewSkillRuntime.LIFT)), "duplicate lift cannot restart visuals");
        check((boolean) method.invoke(null, receipt.apply(NewSkillRuntime.RELEASE)), "release admitted");
        var reordered = new NewSkillRuntime.Visual(kind, 7, owner, 42, 30, 2, 1, dimension, NewSkillRuntime.LIFT,
                net.minecraft.world.phys.Vec3.ZERO, net.minecraft.world.phys.Vec3.ZERO, List.of(), 1);
        check(!(boolean) method.invoke(null, reordered), "terminal receipt blocks even a newer-sequence stale lift");
        check((boolean) method.invoke(null, receipt.apply(NewSkillRuntime.END)), "same-token end remains valid after release");
        var later = new NewSkillRuntime.Visual(kind, 7, owner, 43, 1, 3, 1, dimension, NewSkillRuntime.START,
                net.minecraft.world.phys.Vec3.ZERO, net.minecraft.world.phys.Vec3.ZERO, List.of(), 2);
        check((boolean) method.invoke(null, later), "a distinct newer cast can start");
        check(!(boolean) method.invoke(null, receipt.apply(NewSkillRuntime.RELEASE)), "old token cannot replace later cast");
        NewSkillEffects.clear();
    }
}
