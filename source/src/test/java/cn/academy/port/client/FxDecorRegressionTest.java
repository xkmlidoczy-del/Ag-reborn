package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Billboard;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Decal;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Quad;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Pure witnesses for FxLib and the per-effect CINEMATIC decorations: inertness below layer 3, determinism, bounds, budgets. */
public final class FxDecorRegressionTest {
    private static final Point CAMERA = new Point(0, 0, 0), LOOK = new Point(0, 0, 1), UP = new Point(0, 1, 0);
    private static final VisualTier CIN = VisualTier.CINEMATIC;
    private static int checks;
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    private static Point p(double x, double y, double z) { return new Point(x, y, z); }

    public static void main(String[] args) {
        inertBelowCinematic();
        fxLibBlocks();
        shear();
        landing();
        zeroGap();
        canopy();
        wind();
        meltdown();
        trajectory();
        randomized();
        System.out.println("PASS " + checks + " FxLib and per-effect CINEMATIC decorations: inert below layer 3, determinism, bounds, budgets, projection");
    }

    private static void validate(FxLib.Parts parts, VisualTier tier, int budget, String what) {
        check(parts != null, what + " parts");
        for (Stroke s : parts.strokes()) {
            check(s.halfWidth() > 0 && s.halfWidth() <= RailThunderArrayGrandGeometry.maxHalfWidth(tier) + 1e-9, what + " stroke width bounded");
            check(s.alpha() >= 1 && s.alpha() <= 255 && s.rgb() >= 0 && s.rgb() <= 0xFFFFFF, what + " stroke colour valid");
            check(s.segment().start().finite() && s.segment().end().finite(), what + " stroke finite");
        }
        for (Billboard b : parts.sprites()) {
            check(b.radius() > 0 && b.radius() <= RailThunderArrayGrandGeometry.maxSpriteRadius(tier) * 2, what + " sprite radius sane before the projection clamp: " + b.radius());
            check(b.alpha() >= 1 && b.alpha() <= 255 && b.center().finite(), what + " sprite valid");
        }
        for (Decal d : parts.decals()) check(d.radius() > 0 && d.radius() <= 64 && d.alpha() >= 1 && d.alpha() <= 255, what + " decal valid");
        var quads = VisualKit.projectParts(parts, CAMERA, LOOK, UP, tier, budget);
        check(quads.size() <= budget, what + " trimmed to budget");
        for (Quad q : quads) check(q.a().finite() && q.b().finite() && q.c().finite() && q.d().finite() && q.alpha() >= 1 && q.alpha() <= 255, what + " quad valid");
    }

    private static void inertBelowCinematic() {
        for (VisualTier tier : new VisualTier[] {null, VisualTier.OFF, VisualTier.LOW}) {
            check(FxLib.sparkShower(p(0, 0, 10), p(0, 1, 0), 1, 2, 10, 5, 1, 9, 10, 0xFFFFFF, 0xFF0000, tier).strokes().isEmpty(), tier + " sparks inert");
            check(FxLib.embers(p(0, 0, 10), 2, 3, 1, 5, 10, 0xFFFFFF, 0xFF0000, tier).sprites().isEmpty(), tier + " embers inert");
            check(FxLib.spokes(p(0, 0, 10), 1, 1, 8, 8, 2, 0xFFFFFF, 200, tier).sprites().isEmpty(), tier + " spokes inert");
            check(FxLib.lensStreak(p(0, 0, 10), 3, 0xFFFFFF, 200, 1, tier).sprites().isEmpty(), tier + " streak inert");
            check(FxLib.groundGlow(p(0, 0, 10), 2, 0xFFFFFF, 100, 1, 100, tier).decals().isEmpty(), tier + " glow inert");
            check(FxLib.dispersion(p(0, 0, 10), p(0, 0, 20), .05, 100, tier).strokes().isEmpty(), tier + " dispersion inert");
        }
        // The per-effect decorations need layer 3 (CINEMATIC); STANDARD keeps the primitives but not these stacks.
        for (VisualTier tier : new VisualTier[] {null, VisualTier.OFF, VisualTier.LOW, VisualTier.STANDARD}) {
            check(ShearWindbladeDecor.fx(null, null, 0, tier).strokes().isEmpty(), tier + " shear inert");
            check(LandingArrayDecor.cue(p(0, 0, 10), true, 1, 7, 5, tier).strokes().isEmpty(), tier + " landing inert");
            check(ZeroGapArmorDecor.fx(p(0, 0, 10), List.of(p(0, 1, 10)), List.of(), 1, 5, tier).sprites().isEmpty(), tier + " zero-gap inert");
            check(WindPressureLiftDecor.fx(p(0, 0, 10), p(1, 0, 0), 3, 5, tier).strokes().isEmpty(), tier + " wind inert");
            check(PointMeltdownDecor.fx(List.of(new PointMeltdownDecor.Beam(p(0, 1, 0), p(0, 1, 10), 5, true, false, 0)), 1, 5, tier).strokes().isEmpty(), tier + " meltdown inert");
            check(TrajectoryReturnDecor.fx(List.of(), 1, 5, tier).strokes().isEmpty(), tier + " trajectory inert");
        }
    }

    private static void fxLibBlocks() {
        FxLib.Parts sparks = FxLib.sparkShower(p(0, 0, 12), p(0, 1, 0), 99, 3, 40, 12, 1.2, 9, 16, 0xFFFFFF, 0xFF5000, CIN);
        check(!sparks.strokes().isEmpty() && !sparks.sprites().isEmpty(), "sparks emit streaks and heads");
        check(sparks.equals(FxLib.sparkShower(p(0, 0, 12), p(0, 1, 0), 99, 3, 40, 12, 1.2, 9, 16, 0xFFFFFF, 0xFF5000, CIN)), "sparks deterministic");
        check(!sparks.equals(FxLib.sparkShower(p(0, 0, 12), p(0, 1, 0), 100, 3, 40, 12, 1.2, 9, 16, 0xFFFFFF, 0xFF5000, CIN)), "seed changes sparks");
        check(FxLib.sparkShower(p(0, 0, 12), p(0, 1, 0), 99, 100, 40, 12, 1.2, 9, 16, 0xFFFFFF, 0xFF5000, CIN).strokes().isEmpty(), "sparks die after their life");
        check(FxLib.sparkShower(p(0, 0, 12), p(0, 0, 0), 99, 3, 40, 12, 1.2, 9, 16, 0xFFFFFF, 0xFF5000, CIN).strokes().isEmpty(), "zero axis refused");
        check(FxLib.sparkShower(p(0, 0, 12), p(0, 1, 0), 99, -1, 40, 12, 1.2, 9, 16, 0xFFFFFF, 0xFF5000, CIN).strokes().isEmpty(), "negative age refused");
        check(FxLib.sparkShower(p(Double.NaN, 0, 12), p(0, 1, 0), 99, 1, 40, 12, 1.2, 9, 16, 0xFFFFFF, 0xFF5000, CIN).strokes().isEmpty(), "NaN origin refused");
        validate(sparks, CIN, 400, "sparks");
        // Cooling ramp: young sparks are whiter than old ones.
        check(FxLib.cooling(0xFF8000, 0x800000, 0) == 0xFFFFF0 && FxLib.cooling(0xFF8000, 0x800000, 1) == 0x800000, "cooling endpoints");
        FxLib.Parts embers = FxLib.embers(p(0, 0, 10), 3, 4, 7, 10, 30, 0xFFE0A0, 0xD04000, CIN);
        check(embers.sprites().size() == 30, "one sprite per ember");
        check(embers.equals(FxLib.embers(p(0, 0, 10), 3, 4, 7, 10, 30, 0xFFE0A0, 0xD04000, CIN)), "embers deterministic");
        check(!embers.equals(FxLib.embers(p(0, 0, 10), 3, 4, 7, 11, 30, 0xFFE0A0, 0xD04000, CIN)), "embers move with time");
        for (Billboard b : embers.sprites()) check(b.center().y() >= -1e-9 && b.center().y() <= 4 + 1e-9, "embers stay within the column height");
        validate(embers, CIN, 100, "embers");
        FxLib.Parts spokes = FxLib.spokes(p(0, 1, 12), 5, 2, 8, 14, 2.5, 0xFFFFFF, 200, CIN);
        check(spokes.sprites().size() == 14, "one sprite per spoke");
        check(FxLib.spokes(p(0, 1, 12), 5, 8, 8, 14, 2.5, 0xFFFFFF, 200, CIN).sprites().isEmpty(), "spokes end with their life");
        validate(spokes, CIN, 100, "spokes");
        validate(FxLib.lensStreak(p(0, 1, 12), 4, 0xFFC080, 150, .8, CIN), CIN, 10, "lens streak");
        FxLib.Parts glow = FxLib.groundGlow(p(0, 0, 12), 3, 0x88CCFF, 90, 2, 120, CIN);
        check(glow.decals().size() == 2 && FxLib.groundGlow(p(0, 0, 12), 3, 0x88CCFF, 90, 0, 0, CIN).decals().size() == 1, "pool plus optional ring");
        validate(glow, CIN, 10, "ground glow");
        validate(FxLib.dispersion(p(0, 1, 8), p(2, 1, 30), .06, 120, CIN), CIN, 20, "dispersion");
        check(FxLib.dispersion(p(0, 1, 8), p(0, 1, 8), .06, 120, CIN).strokes().isEmpty(), "degenerate path refused");
        // Decal geometry: a ground decal in front of the camera projects to one quad, one behind it to none.
        Decal front = new Decal(p(0, -1, 10), p(1, 0, 0), p(0, 0, 1), 2, 0xFFFFFF, 100, RailThunderArrayGrandGeometry.RADIAL);
        check(RailThunderArrayGrandGeometry.projectDecal(front, CAMERA, LOOK, UP, CIN).size() == 1, "decal ahead projects");
        Decal behind = new Decal(p(0, -1, -10), p(1, 0, 0), p(0, 0, 1), 2, 0xFFFFFF, 100, RailThunderArrayGrandGeometry.RADIAL);
        check(RailThunderArrayGrandGeometry.projectDecal(behind, CAMERA, LOOK, UP, CIN).isEmpty(), "decal behind refused");
        check(RailThunderArrayGrandGeometry.projectDecal(null, CAMERA, LOOK, UP, CIN).isEmpty(), "null decal refused");
        check(RailThunderArrayGrandGeometry.projectDecal(new Decal(p(0, -1, 10), p(1, 0, 0), p(0, 0, 1), 0, 0xFFFFFF, 100, 5), CAMERA, LOOK, UP, CIN).isEmpty(), "zero-radius decal refused");
        // Sprite stretch bounds are enforced.
        check(RailThunderArrayGrandGeometry.projectBillboard(new Billboard(p(0, 0, 10), 1, 0xFFFFFF, 100, 5, 0, 100), CAMERA, LOOK, UP, CIN).isEmpty(), "absurd stretch refused");
        // An oversized sprite is clamped to the tier ceiling, never dropped or allowed to grow past it.
        Billboard huge = new Billboard(p(0, 0, 40), 500, 0xFFFFFF, 100, 5, 0);
        var clamped = RailThunderArrayGrandGeometry.projectBillboard(huge, CAMERA, LOOK, UP, CIN);
        check(clamped.size() == 1 && Math.abs(clamped.get(0).d().x() - clamped.get(0).a().x()) <= 2 * RailThunderArrayGrandGeometry.maxSpriteRadius(CIN) + 1e-6, "oversized sprite clamps to the ceiling");
        check(RailThunderArrayGrandGeometry.projectBillboard(new Billboard(p(0, 0, 10), 1, 0xFFFFFF, 100, 5, 0, 8), CAMERA, LOOK, UP, CIN).size() == 1, "valid stretch projects");
    }

    private static ShearWindbladeState.Controller shearCast() {
        var id = new ShearWindbladeState.Identity(1, new java.util.UUID(1, 2), 1, 1, 1);
        java.util.function.Function<Double, java.util.List<cn.academy.port.highpower.core.ShearWindbladeGeometry.Point>> line = z -> {
            var list = new java.util.ArrayList<cn.academy.port.highpower.core.ShearWindbladeGeometry.Point>();
            for (int i = 0; i <= cn.academy.port.highpower.core.ShearWindbladeGeometry.CHORDS; i++) list.add(new cn.academy.port.highpower.core.ShearWindbladeGeometry.Point(-4 + i, 1, z));
            return list;
        };
        var front = new ShearWindbladeState.Front(0, line.apply(8.0), line.apply(9.0), ShearWindbladeState.TRAVELLING);
        return new ShearWindbladeState.Controller(id, false, false, new cn.academy.port.highpower.core.ShearWindbladeGeometry.Point(0, 1, 0), List.of(front), List.of(), 1, 1);
    }

    private static void shear() {
        try {
            var cast = shearCast();
            var shape = ShearWindbladeRenderGeometry.shape(cast, .5);
            check(!shape.strokes().isEmpty(), "shear fixture produces a shape");
            FxLib.Parts parts = ShearWindbladeDecor.fx(shape, cast, 20, CIN);
            check(!parts.strokes().isEmpty() && !parts.sprites().isEmpty(), "shear layers present");
            check(parts.equals(ShearWindbladeDecor.fx(shape, cast, 20, CIN)), "shear deterministic");
            validate(parts, CIN, ShearWindbladeDecor.MAX_QUADS, "shear");
        } catch (RuntimeException unexpected) { throw new AssertionError("shear fixture must build", unexpected); }
    }

    private static void landing() {
        for (boolean contact : new boolean[] {false, true}) for (int age = 0; age < 7; age++) {
            FxLib.Parts parts = LandingArrayDecor.cue(p(2, 64, 12), contact, age, 7, 40, CIN);
            check(!parts.strokes().isEmpty() && !parts.sprites().isEmpty() && !parts.decals().isEmpty(), "landing layers present");
            validate(parts, CIN, LandingArrayDecor.MAX_QUADS, "landing");
        }
        check(LandingArrayDecor.cue(p(2, 64, 12), true, 7, 7, 40, CIN).strokes().isEmpty(), "landing ends with its life");
        check(LandingArrayDecor.cue(p(2, 64, 12), true, 3, 7, 40, CIN).equals(LandingArrayDecor.cue(p(2, 64, 12), true, 3, 7, 40, CIN)), "landing deterministic");
    }

    private static void zeroGap() {
        FxLib.Parts parts = ZeroGapArmorDecor.fx(p(0, 0, 8), List.of(p(1, 1, 8), p(-1, 1, 8), p(0, 1, 9)),
                List.of(new ZeroGapArmorDecor.Contact(p(1, 1.2, 8), p(0, 0, 1), 2)), 252, 30, CIN);
        check(!parts.sprites().isEmpty() && !parts.decals().isEmpty(), "zero-gap layers present");
        validate(parts, CIN, ZeroGapArmorDecor.MAX_QUADS, "zero-gap");
        check(ZeroGapArmorDecor.fx(p(0, 0, 8), List.of(), List.of(new ZeroGapArmorDecor.Contact(p(1, 1, 8), p(0, 0, 1), 9)), 1, 1, CIN).sprites().size()
                < ZeroGapArmorDecor.fx(p(0, 0, 8), List.of(), List.of(new ZeroGapArmorDecor.Contact(p(1, 1, 8), p(0, 0, 1), 1)), 1, 1, CIN).sprites().size(), "expired contact adds nothing");
    }

    private static void canopy() {
        var camera = new Vector(0, 0, 0);
        var look = new Vector(0, 0, 1);
        var source = new Vector(0, 2, 8);
        var contact = new Vector(5, 2, 30);
        var id = new MulticoreCanopyState.Identity(1, new UUID(1, 2), 1, 1, 251);
        var cores = List.of(new MulticoreCanopyState.Core(0, source, false));
        var ray = new MulticoreCanopyState.Segment(0, 0, source, contact, MulticoreCanopyState.DAMAGED);
        var fire = new MulticoreCanopyState.Frame(id, 2, MulticoreCanopyState.FIRE, MulticoreCanopyState.DAMAGED,
                30, camera, cores, List.of(), List.of(ray), false, 1, 0);
        var faces = MulticoreCanopyEmissionGeometry.emission(fire, camera, look, 2);
        check(!faces.isEmpty() && faces.size() <= MulticoreCanopyEmissionGeometry.MAX_QUADS_PER_RAY, "canopy accepted ray has bounded heat layers");
        check(faces.equals(MulticoreCanopyEmissionGeometry.emission(fire, camera, look, 2)), "canopy receipt art deterministic");
        check(faces.stream().anyMatch(f -> f.color() == MulticoreCanopyEmissionGeometry.HOT_FILAMENT)
                && faces.stream().anyMatch(f -> f.color() == MulticoreCanopyEmissionGeometry.EMBER), "canopy accepted contact has a warm lane and embers");
        Vector delta = contact.subtract(source);
        for (var face : faces) {
            check(face.alpha() >= 1 && face.alpha() <= 255, "canopy face alpha bounded");
            for (Vector corner : face.vertices()) {
                double along = corner.subtract(source).dot(delta);
                check(corner.finite() && along >= -1e-8 && along <= delta.dot(delta) + 1e-8,
                        "canopy face stays inside the accepted endpoint planes");
            }
        }

        var budget = new MulticoreCanopyMesh.Budget();
        check(budget.reserve(MulticoreCanopyMesh.MAX_MESH_QUADS - 1), "canopy budgets ordinary geometry before optional art");
        check(MulticoreCanopyCrownBudget.append(List.of(), List.of(faces), camera, look, budget, 1).isEmpty()
                && budget.used() == MulticoreCanopyMesh.MAX_MESH_QUADS - 1, "canopy ornament cannot consume the label reserve");
        check(MulticoreCanopyEmissionGeometry.emission(fire, camera, look, MulticoreCanopyState.FLASH_TICKS).isEmpty(),
                "expired canopy receipt adds nothing");
        var armed = new MulticoreCanopyState.Frame(id, 1, MulticoreCanopyState.ARMED, MulticoreCanopyState.PREPARED,
                31, camera, cores, List.of(), List.of(), false, 0, 0);
        check(MulticoreCanopyEmissionGeometry.emission(armed, camera, look, 0).isEmpty(),
                "non-firing canopy receipt cannot draw a heat lane");
        // The compiled V62 differential verifies the actual material-off and
        // unavailable cases. This renderer no longer uses the old tier gate.
    }

    private static void wind() {
        for (int age = 0; age < 8; age++) validate(WindPressureLiftDecor.fx(p(1, 0, 10), p(1, 0, 0), age, 33, CIN), CIN, WindPressureLiftDecor.MAX_QUADS, "wind");
        FxLib.Parts parts = WindPressureLiftDecor.fx(p(1, 0, 10), p(0, 0, 1), 6, 33, CIN);
        double maxY = 0;
        for (Stroke s : parts.strokes()) maxY = Math.max(maxY, Math.max(s.segment().start().y(), s.segment().end().y()));
        check(maxY <= 6.5, "wind column stays within the existing 6 m height");
        check(WindPressureLiftDecor.fx(p(1, 0, 10), p(0, 0, 0), 6, 33, CIN).strokes().isEmpty(), "zero drift refused");
    }

    private static void meltdown() {
        var focusing = new PointMeltdownDecor.Beam(p(0, 1, 0), p(0, 1, 12), 8, true, false, 0);
        var finished = new PointMeltdownDecor.Beam(p(0, 1, 0), p(3, 1, 12), 10, false, true, 3);
        FxLib.Parts parts = PointMeltdownDecor.fx(List.of(focusing, finished), 233, 17, CIN);
        check(!parts.strokes().isEmpty() && !parts.sprites().isEmpty(), "meltdown layers present");
        validate(parts, CIN, PointMeltdownDecor.MAX_QUADS, "meltdown");
        check(PointMeltdownDecor.fx(List.of(new PointMeltdownDecor.Beam(p(0, 1, 0), p(0, 1, 0), 8, true, false, 0)), 1, 1, CIN).strokes().isEmpty(), "degenerate beam refused");
    }

    private static void trajectory() {
        var trail = new TrajectoryReturnDecor.Trail(List.of(new Point[] {p(0, 1, 4), p(0, 1, 12)}, new Point[] {p(0, 1, 12), p(1, 2, 14)}), p(0, 1, 12), p(0, 1, 8), p(0, 0, -1), true, 3);
        FxLib.Parts parts = TrajectoryReturnDecor.fx(List.of(trail), 431, 12, CIN);
        check(!parts.strokes().isEmpty() && !parts.sprites().isEmpty(), "trajectory layers present");
        validate(parts, CIN, TrajectoryReturnDecor.MAX_QUADS, "trajectory");
        check(TrajectoryReturnDecor.fx(List.of(), 1, 1, CIN).strokes().isEmpty(), "no trails, no decoration");
    }

    private static void randomized() {
        Random random = new Random(20261017L);
        for (int i = 0; i < 3000; i++) {
            Point a = p(random.nextGaussian() * 20, random.nextGaussian() * 6, random.nextGaussian() * 20 + 10), b = a.plus(p(random.nextGaussian() * 10, random.nextGaussian() * 4, random.nextGaussian() * 10));
            double time = random.nextDouble() * 1200;
            validate(FxLib.sparkShower(a, b.minus(a), random.nextLong(), random.nextDouble() * 20, 1 + random.nextInt(60), 1 + random.nextDouble() * 15, random.nextDouble() * 1.5, random.nextDouble() * 12, 5 + random.nextDouble() * 20, 0xFFFFFF, 0xFF4000, CIN), CIN, 500, "random sparks");
            validate(FxLib.embers(a, .5 + random.nextDouble() * 10, 1 + random.nextDouble() * 9, random.nextLong(), time, 1 + random.nextInt(80), 0xFFFFFF, 0xFF4000, CIN), CIN, 200, "random embers");
            validate(WindPressureLiftDecor.fx(a, p(random.nextGaussian(), 0, random.nextGaussian()), random.nextInt(10), time, CIN), CIN, WindPressureLiftDecor.MAX_QUADS, "random wind");
            validate(LandingArrayDecor.cue(a, random.nextBoolean(), random.nextInt(7), 7, time, CIN), CIN, LandingArrayDecor.MAX_QUADS, "random landing");
        }
    }
}
