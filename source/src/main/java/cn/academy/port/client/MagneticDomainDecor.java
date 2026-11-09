package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGeometry.Segment;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Billboard;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import java.util.ArrayList;
import java.util.List;

/**
 * STANDARD/CINEMATIC decoration for the magnetic domain: dipole field loops through the caster (the field made
 * visible), a rotating footprint ring on the true boundary radius, and beads riding the loops. Pure and
 * deterministic in (centre, tick clock, tier); never a boundary, hit or defense region. Everything stays inside RADIUS.
 */
public final class MagneticDomainDecor {
    public static final int MAX_QUADS_STANDARD = 224, MAX_QUADS_CINEMATIC = 900;
    private static final int LOOP_STEPS = 12, RING_STEPS = 40;
    /** Loops stop this far (radians) from the vertical axis so converging lines never wash out the caster. */
    static final double POLE = .45;

    private MagneticDomainDecor() {}

    public static int maxQuads(VisualTier tier) { return tier == VisualTier.CINEMATIC ? MAX_QUADS_CINEMATIC : tier == VisualTier.STANDARD ? MAX_QUADS_STANDARD : 0; }

    /** Loop shells (metres) and meridians per shell for a tier. */
    static double[] shells(VisualTier tier) { return tier == VisualTier.CINEMATIC ? new double[] {4.5, 8.5} : new double[] {8.0}; }
    static int meridians(VisualTier tier) { return tier == VisualTier.CINEMATIC ? 6 : 5; }

    /** Position on a dipole loop r = L sin^2(theta) in the meridian plane at azimuth phi, measured from the vertical axis. */
    static Point loopPoint(Point center, double shell, double theta, double phi) {
        double r = shell * Math.pow(Math.sin(theta), 2);
        return center.plus(new Point(r * Math.sin(theta) * Math.cos(phi), r * Math.cos(theta), r * Math.sin(theta) * Math.sin(phi)));
    }

    public static List<Stroke> strokes(Point center, double time, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !RailThunderArrayGeometry.validPoint(center) || !Double.isFinite(time)) return List.of();
        double w = tier.widthScale, glow = tier.glow, turn = time * .0105;
        List<Stroke> out = new ArrayList<>(512);
        // Footprint ring where the 12 m sphere meets the ground one metre below the chest: dashed core over a soft band.
        double ringRadius = Math.sqrt(MagneticDomainGeometry.RADIUS * MagneticDomainGeometry.RADIUS - 1) - .1, footY = -.97;
        for (int i = 0; i < RING_STEPS; i++) {
            double a = i * Math.PI * 2 / RING_STEPS + time * .004, b = a + Math.PI * 2 / RING_STEPS;
            Point from = center.plus(new Point(Math.sin(a) * ringRadius, footY, Math.cos(a) * ringRadius));
            Point to = center.plus(new Point(Math.sin(b) * ringRadius, footY, Math.cos(b) * ringRadius));
            add(out, from, to, .10 * w, MagneticDomainGeometry.BLUE, (int) Math.round(64 * glow), RailThunderArrayGrandGeometry.ARC_GLOW);
            if (i % 2 == 0) add(out, from, to, .02, MagneticDomainGeometry.WHITE, 175, RailThunderArrayGrandGeometry.WHITE);
        }
        double[] shells = shells(tier);
        int meridians = meridians(tier);
        for (int s = 0; s < shells.length; s++) {
            for (int k = 0; k < meridians; k++) {
                double phi = k * Math.PI * 2 / meridians + (s == 0 ? 1 : -1) * turn + s * .31;
                for (int i = 0; i < LOOP_STEPS; i++) {
                    double t0 = POLE + (Math.PI - 2 * POLE) * i / LOOP_STEPS, t1 = POLE + (Math.PI - 2 * POLE) * (i + 1) / LOOP_STEPS;
                    Point a = loopPoint(center, shells[s], t0, phi), b = loopPoint(center, shells[s], t1, phi);
                    double fade = .25 + .75 * Math.sin(Math.PI * (i + .5) / LOOP_STEPS);
                    add(out, a, b, .07 * w, s == 0 ? MagneticDomainGeometry.BLUE : MagneticDomainGeometry.FLOW_BLUE,
                            (int) Math.round(96 * glow * fade), RailThunderArrayGrandGeometry.ARC_GLOW);
                    add(out, a, b, .011, MagneticDomainGeometry.WHITE, (int) Math.round(200 * fade), RailThunderArrayGrandGeometry.WHITE);
                }
            }
        }
        return List.copyOf(out);
    }

    public static List<Billboard> sprites(Point center, double time, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !RailThunderArrayGeometry.validPoint(center) || !Double.isFinite(time)) return List.of();
        double w = tier.widthScale, glow = tier.glow, turn = time * .0105;
        List<Billboard> out = new ArrayList<>(16);
        double[] shells = shells(tier);
        int meridians = meridians(tier);
        for (int s = 0; s < shells.length; s++)
            for (int k = 0; k < meridians; k++) {
                double phi = k * Math.PI * 2 / meridians + (s == 0 ? 1 : -1) * turn + s * .31;
                double travel = ((time * (.011 + .003 * s) + k * .173 + s * .41) % 1 + 1) % 1;
                double theta = POLE + (Math.PI - 2 * POLE) * travel;
                Point at = loopPoint(center, shells[s], theta, phi);
                out.add(new Billboard(at, .40 * w, MagneticDomainGeometry.WHITE, (int) Math.round(235 * glow), RailThunderArrayGrandGeometry.RADIAL, 0));
                out.add(new Billboard(at, .95 * w, MagneticDomainGeometry.BLUE, (int) Math.round(120 * glow), RailThunderArrayGrandGeometry.RADIAL, 0));
            }
        // Caster aura: a slow-spinning star at the chest anchors the field.
        out.add(new Billboard(center, .9 * w, MagneticDomainGeometry.WHITE, (int) Math.round(105 * glow), RailThunderArrayGrandGeometry.STAR, time * .02));
        return List.copyOf(out);
    }

    /** Particle-style layers: drifting motes through the field, a lit footprint pool with a travelling pulse ring, and bursts for receipts. */
    public static FxLib.Parts fx(Point center, double time, VisualTier tier, Point hit, int hitAge, Point refract, int refractAge) {
        if (tier == null || tier.layers < 3 || !RailThunderArrayGeometry.validPoint(center) || !Double.isFinite(time)) return FxLib.Parts.EMPTY;
        FxLib.Builder out = new FxLib.Builder();
        out.add(FxLib.embers(center.plus(new Point(0, -.9, 0)), 10.5, 9, 152L, time, 90, 0x9FD8FF, 0x2F78D0, tier));
        double footY = -.97, pulse = (time % 80) / 80;
        Point foot = center.plus(new Point(0, footY, 0));
        out.add(FxLib.groundGlow(foot, 11.8, MagneticDomainGeometry.FLOW_BLUE, 70, 1 + pulse * 10.9, 150 * Math.sin(Math.PI * pulse), tier));
        out.add(FxLib.lensStreak(center, 4.2 * tier.widthScale, MagneticDomainGeometry.BLUE, 120, 1, tier));
        if (hit != null && hitAge >= 0 && hitAge < MagneticDomainState.HIT_TICKS) {
            out.add(FxLib.spokes(hit, 7777, hitAge, MagneticDomainState.HIT_TICKS, 16, 2.6 * tier.widthScale, MagneticDomainGeometry.WHITE, 210, tier));
            out.add(FxLib.sparkShower(hit, hit.minus(center), 4242, hitAge, 36, 11, 1.3, 9, 14, MagneticDomainGeometry.WHITE, MagneticDomainGeometry.FLOW_BLUE, tier));
        }
        if (refract != null && refractAge >= 0 && refractAge < MagneticDomainState.REFRACT_TICKS)
            out.add(FxLib.spokes(refract, 8888, refractAge, MagneticDomainState.REFRACT_TICKS, 12, 2.2 * tier.widthScale, MagneticDomainGeometry.BLUE, 190, tier));
        return out.build();
    }

    private static void add(List<Stroke> out, Point from, Point to, double width, int rgb, int alpha, int texture) {
        if (alpha < 1) alpha = 1;
        Stroke stroke = new Stroke(new Segment(from, to), width, rgb, Math.min(255, alpha), texture);
        if (RailThunderArrayGeometry.validEndpoints(from, to) && to.minus(from).length() > 1e-8) out.add(stroke);
    }
}
