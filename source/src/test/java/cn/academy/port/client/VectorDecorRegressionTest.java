package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Billboard;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Decal;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Quad;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Pure witnesses for the ten vector-skill decorations: inert below layer 3, determinism, bounds, budgets, hostile input, distinct identity, power scaling. */
public final class VectorDecorRegressionTest {
    private static int checks;
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    private static final Point CAMERA = new Point(0, 66, -12), LOOK = new Point(0, -.1, 1), UP = new Point(0, 1, 0);

    private static double[] v(double x, double y, double z) { return new double[] {x, y, z}; }

    /** A well-formed snapshot for each skill, in the layout documented by its Effects accessor. */
    private static DecorSnap canonical(VectorDecor.Skill skill, int age) {
        List<double[]> p = new ArrayList<>();
        return switch (skill) {
            case STRIKE -> { p.add(v(0, 65, -3)); p.add(v(0, 65, 12)); p.add(v(0, 65, 10)); yield DecorSnap.of(true, age, 0, 9, p, 0); }
            case PARRY -> { p.add(v(0, 64, 0)); p.add(v(0, 0, -1)); p.add(v(0, 65.4, -1)); p.add(v(.5, .2, -.85)); p.add(v(-.5, .1, -.85)); yield DecorSnap.of(true, age, 0, 9, p, 1, 0, age, age); }
            case BRAKE -> { p.add(v(0, 64.05, 0)); yield DecorSnap.of(true, age, 1, 9, p); }
            case ANCHOR -> { p.add(v(0, 64, 0)); p.add(v(0, 0, -1)); yield DecorSnap.of(true, age, 1, 9, p, 3, 1, age, -1); }
            case PALM -> { p.add(v(0, 65, -3)); p.add(v(2, 65, 3)); p.add(v(1, 0, 0)); yield DecorSnap.of(true, age, 0, 9, p, 0); }
            case RELIEF -> { p.add(v(0, 64, 0)); p.add(v(0, 0, -1)); p.add(v(0, 64.05, 0)); p.add(v(0, 65, 0)); yield DecorSnap.of(true, age, 1, 9, p, 3, 1, age); }
            case LISTEN -> { p.add(v(0, 64, 0)); yield DecorSnap.of(true, age, 0, 9, p, 0, 2, 20, 0, 3, 0, 110, 10, 2, 1); }
            case CAGE -> { p.add(v(0, 64, 0)); p.add(v(5, 65.5, 1)); yield DecorSnap.of(true, age, 0, 9, p, 5, 3, 1, 0, 0); }
            case REFLECT -> { p.add(v(0, 64, 0)); p.add(v(0, 0, -1)); p.add(v(1.5, 65, 0)); p.add(v(6, 65, 12));
                yield new DecorSnap(true, age, 0, 9, p, new double[] {15, age}, List.of(List.of(v(1.5, 65, 0), v(5, 65.5, 4), v(2, 65.4, 8)))); }
            case CIRCLE -> { p.add(v(0, 64, 0)); p.add(v(0, 0, -1)); p.add(v(0, 67, 8)); p.add(v(3, 65.4, 5)); yield DecorSnap.of(true, age, 0, 9, p, 1, 3, 0, age); }
        };
    }

    private static void validate(FxLib.Parts parts, VisualTier tier, String what) {
        check(parts != null, what + " parts");
        for (Stroke s : parts.strokes()) {
            check(s.halfWidth() > 0 && s.halfWidth() <= RailThunderArrayGrandGeometry.maxHalfWidth(tier) + 1e-9, what + " stroke width bounded");
            check(s.alpha() >= 1 && s.alpha() <= 255 && s.rgb() >= 0 && s.rgb() <= 0xFFFFFF, what + " stroke colour valid");
            check(s.segment().start().finite() && s.segment().end().finite(), what + " stroke finite");
        }
        for (Billboard b : parts.sprites()) {
            check(b.radius() > 0 && b.radius() <= RailThunderArrayGrandGeometry.maxSpriteRadius(tier) * 2, what + " sprite radius sane: " + b.radius());
            check(b.alpha() >= 1 && b.alpha() <= 255 && b.center().finite(), what + " sprite valid");
        }
        for (Decal d : parts.decals()) check(d.radius() > 0 && d.radius() <= 64 && d.alpha() >= 1 && d.alpha() <= 255, what + " decal valid");
        int budget = VectorDecor.MAX_QUADS * 3;
        var quads = VisualKit.projectParts(parts, CAMERA, LOOK, UP, tier, budget);
        check(quads.size() <= budget, what + " trimmed to budget");
        for (Quad q : quads) check(q.a().finite() && q.b().finite() && q.c().finite() && q.d().finite() && q.alpha() >= 1 && q.alpha() <= 255, what + " quad valid");
    }

    private static String signature(FxLib.Parts parts) {
        double extent = 0, sum = 0;
        for (Stroke s : parts.strokes()) { extent = Math.max(extent, Math.max(s.segment().start().minus(new Point(0, 64, 0)).length(), s.segment().end().minus(new Point(0, 64, 0)).length())); sum += s.rgb() % 7 + s.alpha() % 5; }
        return parts.strokes().size() + "/" + parts.sprites().size() + "/" + parts.decals().size() + "/" + Math.round(extent * 10) + "/" + Math.round(sum);
    }

    private static double[] garbage(Random r) {
        int n = r.nextInt(6);
        double[] a = new double[n];
        for (int i = 0; i < n; i++) a[i] = switch (r.nextInt(8)) { case 0 -> Double.NaN; case 1 -> Double.POSITIVE_INFINITY; case 2 -> -1e30; case 3 -> 0; default -> (r.nextDouble() - .5) * 200; };
        return a;
    }

    public static void main(String[] args) {
        // Below CINEMATIC (layer 3), and for bad inputs, nothing is produced.
        for (VectorDecor.Skill skill : VectorDecor.Skill.values()) {
            DecorSnap snap = canonical(skill, 4);
            for (VisualTier tier : new VisualTier[] {null, VisualTier.OFF, VisualTier.LOW, VisualTier.STANDARD}) {
                var parts = VectorDecor.fx(skill, snap, 10, .8, tier);
                check(parts.strokes().isEmpty() && parts.sprites().isEmpty() && parts.decals().isEmpty(), skill + " inert at " + tier);
            }
            VisualTier cin = VisualTier.CINEMATIC;
            check(VectorDecor.fx(null, snap, 10, .8, cin).strokes().isEmpty(), "null skill inert");
            check(VectorDecor.fx(skill, null, 10, .8, cin).strokes().isEmpty(), skill + " null snapshot inert");
            check(VectorDecor.fx(skill, snap, Double.NaN, .8, cin).strokes().isEmpty(), skill + " NaN clock refused");
            check(VectorDecor.fx(skill, snap, 10, Double.NaN, cin).strokes().isEmpty(), skill + " NaN power refused");
            check(VectorDecor.fx(skill, snap, Double.POSITIVE_INFINITY, .8, cin).strokes().isEmpty(), skill + " infinite clock refused");
        }
        // Every skill draws something on CINEMATIC, deterministically, within bounds, for every age and power.
        Set<String> identities = new HashSet<>();
        for (VectorDecor.Skill skill : VectorDecor.Skill.values()) {
            for (int age = 0; age < 20; age++) for (double power : new double[] {.2, .5, 1}) {
                for (double time : new double[] {0, 17.3, 400.9}) {
                    DecorSnap snap = canonical(skill, age);
                    var a = VectorDecor.fx(skill, snap, time, power, VisualTier.CINEMATIC);
                    var b = VectorDecor.fx(skill, snap, time, power, VisualTier.CINEMATIC);
                    check(a.equals(b), skill + " deterministic");
                    check(!a.strokes().isEmpty() || !a.sprites().isEmpty() || !a.decals().isEmpty(), skill + " draws at age " + age);
                    validate(a, VisualTier.CINEMATIC, skill + "@" + age);
                }
            }
            identities.add(signature(VectorDecor.fx(skill, canonical(skill, 5), 20, .8, VisualTier.CINEMATIC)));
        }
        check(identities.size() == VectorDecor.Skill.values().length, "every skill has its own visual identity (" + identities.size() + " distinct)");
        // Hostile snapshots (NaN, huge, missing points and numbers) never throw and never emit invalid geometry.
        Random random = new Random(411);
        for (int i = 0; i < 6000; i++) {
            VectorDecor.Skill skill = VectorDecor.Skill.values()[random.nextInt(VectorDecor.Skill.values().length)];
            List<double[]> points = new ArrayList<>();
            for (int k = random.nextInt(7); k > 0; k--) points.add(random.nextInt(10) == 0 ? DecorSnap.NONE : garbage(random));
            List<List<double[]>> lines = new ArrayList<>();
            for (int k = random.nextInt(3); k > 0; k--) { List<double[]> line = new ArrayList<>(); for (int j = random.nextInt(6); j > 0; j--) line.add(garbage(random)); lines.add(line); }
            DecorSnap snap = new DecorSnap(random.nextBoolean(), random.nextInt(400) - 20, random.nextInt(5), random.nextLong(), points, garbage(random), lines);
            var parts = VectorDecor.fx(skill, snap, random.nextDouble() * 5000, random.nextDouble() * 3 - 1, VisualTier.CINEMATIC);
            validate(parts, VisualTier.CINEMATIC, skill + " hostile");
        }
        // Stronger skill levels are never smaller or sparser than weaker ones.
        for (VectorDecor.Skill skill : VectorDecor.Skill.values()) {
            double lo = extent(VectorDecor.fx(skill, canonical(skill, 5), 20, VectorDecor.power(1, 1), VisualTier.CINEMATIC));
            double hi = extent(VectorDecor.fx(skill, canonical(skill, 5), 20, VectorDecor.power(5, 5), VisualTier.CINEMATIC));
            check(hi >= lo - 1e-9, skill + " grows with power: " + lo + " -> " + hi);
        }
        check(VectorDecor.power(1, 1) >= .2 && VectorDecor.power(5, 5) <= 1 && VectorDecor.power(5, 5) > VectorDecor.power(1, 1), "power range");
        check(VectorDecor.power(3, Double.NaN) > 0 && VectorDecor.power(99, 99) <= 1 && VectorDecor.power(-4, -4) >= .2, "power clamps hostile levels");
        System.out.println("PASS " + checks + " vector-skill decorations: inert below layer 3, determinism, bounds, budgets, hostile input, identity, power scaling");
    }

    private static double extent(FxLib.Parts parts) {
        double e = 0;
        for (Stroke s : parts.strokes()) e = Math.max(e, Math.max(s.segment().start().minus(new Point(0, 64, 0)).length(), s.segment().end().minus(new Point(0, 64, 0)).length()));
        for (Billboard b : parts.sprites()) e = Math.max(e, b.center().minus(new Point(0, 64, 0)).length());
        return e;
    }
}
