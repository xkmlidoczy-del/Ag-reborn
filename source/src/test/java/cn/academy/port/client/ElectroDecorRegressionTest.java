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

/** Pure witnesses for the eleven electromaster decorations: inert below layer 3, determinism, bounds, budgets, hostile input, distinct identity, power scaling. */
public final class ElectroDecorRegressionTest {
    private static int checks;
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    private static final Point CAMERA = new Point(-12, 66, 0), LOOK = new Point(1, -.1, 0), UP = new Point(0, 1, 0), ORIGIN = new Point(0, 64, 0);

    private static double[] v(double x, double y, double z) { return new double[] {x, 64 + y, z}; }
    private static double[] raw(double x, double y, double z) { return new double[] {x, y, z}; }

    private static DecorSnap canonical(ElectroDecor.Skill skill, int age) {
        List<double[]> p = new ArrayList<>();
        return switch (skill) {
            case TETHER -> { p.add(v(0, 1.3, -4)); p.add(v(8, 3, 10)); p.add(raw(0, 0, -1)); yield DecorSnap.of(true, age, 1, 9, p, 1, 0); }
            case CIRCUIT -> { p.add(v(-5, .1, 2)); p.add(v(6, .1, 9)); yield DecorSnap.of(true, age, 1, 9, p, 1, 60); }
            case GROUND -> { p.add(v(0, 0, 0)); yield DecorSnap.of(true, age, 1, 9, p, 1, 40, Math.max(1, age)); }
            case DECOY -> { p.add(v(-3, 0, -2)); p.add(v(5, 0, 6)); yield DecorSnap.of(true, age, 1, 9, p, 1, 60); }
            case LANCE -> { p.add(v(0, 0, -2)); for (int i = 0; i < 3; i++) { p.add(v(-2 + 2 * i, 1.5, 0)); p.add(v(-2 + 2 * i, 1.8, 7 + i)); } yield DecorSnap.of(true, age, 1, 9, p, 1, 20, 1, 1, 2, 3); }
            case STEP -> { p.add(v(-6, 0, 0)); p.add(v(6, 0, 3)); p.add(raw(.3, .7, .4)); yield DecorSnap.of(true, age, 1, 9, p, 1, 40, 1); }
            case PULL -> { p.add(v(0, 1.4, -2)); p.add(v(-5, .2, 6)); p.add(v(4, .2, 8)); p.add(v(1, .2, 11)); yield DecorSnap.of(true, age, 1, 9, p, 1); }
            case NET -> { p.add(v(0, 0, -3)); p.add(v(0, 0, 6)); p.add(v(-6, 0, 8)); p.add(v(6, 0, 9)); p.add(v(-3, 0, 12)); yield DecorSnap.of(true, age, 1, 9, p, 1, 20, 3, 1, 1, 1); }
            case ARC -> { p.add(v(0, 1.5, -5)); p.add(v(0, 1.3, 7)); yield DecorSnap.of(false, age, 0, 9, p, age, 12); }
            case BRACER -> { p.add(v(0, 1.2, 0)); p.add(raw(-.8, 0, .6)); p.add(v(-1.6, 1.4, 1.6)); yield DecorSnap.of(true, 0, 0, 9, p, age, 1); }
            case BUFFER -> { p.add(v(0, 0, 0)); p.add(raw(0, 0, -1)); p.add(v(0, 1.5, -1.3)); yield DecorSnap.of(true, age, 1, 9, p, 1); }
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
        for (Decal d : parts.decals()) check(d.radius() > 0 && d.radius() <= 64 && d.alpha() >= 1 && d.alpha() <= 255, what + " decal valid: " + d.radius());
        int budget = ElectroDecor.MAX_QUADS * 3;
        var quads = VisualKit.projectParts(parts, CAMERA, LOOK, UP, tier, budget);
        check(quads.size() <= budget, what + " trimmed to budget");
        for (Quad q : quads) check(q.a().finite() && q.b().finite() && q.c().finite() && q.d().finite() && q.alpha() >= 1 && q.alpha() <= 255, what + " quad valid");
    }

    private static String signature(FxLib.Parts parts) {
        double extent = 0, sum = 0;
        for (Stroke s : parts.strokes()) { extent = Math.max(extent, Math.max(s.segment().start().minus(ORIGIN).length(), s.segment().end().minus(ORIGIN).length())); sum += s.rgb() % 7 + s.alpha() % 5; }
        return parts.strokes().size() + "/" + parts.sprites().size() + "/" + parts.decals().size() + "/" + Math.round(extent * 10) + "/" + Math.round(sum);
    }

    private static double extent(FxLib.Parts parts) {
        double e = 0;
        for (Stroke s : parts.strokes()) e = Math.max(e, Math.max(s.segment().start().minus(ORIGIN).length(), s.segment().end().minus(ORIGIN).length()));
        for (Billboard b : parts.sprites()) e = Math.max(e, b.center().minus(ORIGIN).length() + b.radius());
        for (Decal d : parts.decals()) e = Math.max(e, d.center().minus(ORIGIN).length() + d.radius());
        return e;
    }

    private static double[] garbage(Random r) {
        int n = r.nextInt(6);
        double[] a = new double[n];
        for (int i = 0; i < n; i++) a[i] = switch (r.nextInt(8)) { case 0 -> Double.NaN; case 1 -> Double.POSITIVE_INFINITY; case 2 -> -1e30; case 3 -> 0; default -> (r.nextDouble() - .5) * 200; };
        return a;
    }

    public static void main(String[] args) {
        for (ElectroDecor.Skill skill : ElectroDecor.Skill.values()) {
            DecorSnap snap = canonical(skill, 4);
            for (VisualTier tier : new VisualTier[] {null, VisualTier.OFF, VisualTier.LOW, VisualTier.STANDARD}) {
                var parts = ElectroDecor.fx(skill, snap, 10, .8, tier);
                check(parts.strokes().isEmpty() && parts.sprites().isEmpty() && parts.decals().isEmpty(), skill + " inert at " + tier);
            }
            VisualTier cin = VisualTier.CINEMATIC;
            check(ElectroDecor.fx(null, snap, 10, .8, cin).strokes().isEmpty(), "null skill inert");
            check(ElectroDecor.fx(skill, null, 10, .8, cin).strokes().isEmpty(), skill + " null snapshot inert");
            check(ElectroDecor.fx(skill, snap, Double.NaN, .8, cin).strokes().isEmpty(), skill + " NaN clock refused");
            check(ElectroDecor.fx(skill, snap, 10, Double.NaN, cin).strokes().isEmpty(), skill + " NaN power refused");
        }
        Set<String> identities = new HashSet<>();
        for (ElectroDecor.Skill skill : ElectroDecor.Skill.values()) {
            for (int age = 0; age < 14; age++) for (double power : new double[] {.2, .5, 1}) for (double time : new double[] {0, 17.3, 400.9}) {
                DecorSnap snap = canonical(skill, age);
                var a = ElectroDecor.fx(skill, snap, time, power, VisualTier.CINEMATIC);
                var b = ElectroDecor.fx(skill, snap, time, power, VisualTier.CINEMATIC);
                check(a.equals(b), skill + " deterministic");
                check(!a.strokes().isEmpty() || !a.sprites().isEmpty() || !a.decals().isEmpty(), skill + " draws at age " + age);
                validate(a, VisualTier.CINEMATIC, skill + "@" + age);
            }
            identities.add(signature(ElectroDecor.fx(skill, canonical(skill, 5), 20, .8, VisualTier.CINEMATIC)));
        }
        check(identities.size() == ElectroDecor.Skill.values().length, "every skill has its own visual identity (" + identities.size() + " distinct)");
        Random random = new Random(113);
        for (int i = 0; i < 6000; i++) {
            ElectroDecor.Skill skill = ElectroDecor.Skill.values()[random.nextInt(ElectroDecor.Skill.values().length)];
            List<double[]> points = new ArrayList<>();
            for (int k = random.nextInt(9); k > 0; k--) points.add(random.nextInt(10) == 0 ? DecorSnap.NONE : garbage(random));
            DecorSnap snap = new DecorSnap(random.nextBoolean(), random.nextInt(400) - 20, random.nextInt(5), random.nextLong(), points, garbage(random), List.of());
            validate(ElectroDecor.fx(skill, snap, random.nextDouble() * 5000, random.nextDouble() * 3 - 1, VisualTier.CINEMATIC), VisualTier.CINEMATIC, skill + " hostile");
        }
        for (ElectroDecor.Skill skill : ElectroDecor.Skill.values()) {
            double lo = extent(ElectroDecor.fx(skill, canonical(skill, 5), 20, ElectroDecor.power(1, 1), VisualTier.CINEMATIC));
            double hi = extent(ElectroDecor.fx(skill, canonical(skill, 5), 20, ElectroDecor.power(5, 5), VisualTier.CINEMATIC));
            check(hi >= lo - 1e-9, skill + " grows with power: " + lo + " -> " + hi);
        }
        System.out.println("PASS " + checks + " electromaster decorations: inert below layer 3, determinism, bounds, budgets, hostile input, identity, power scaling");
    }
}
