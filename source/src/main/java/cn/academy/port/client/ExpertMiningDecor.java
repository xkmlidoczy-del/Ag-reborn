package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGeometry.Segment;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import java.util.ArrayList;
import java.util.List;

/** Expert-only beam focusing, not a second beam, impact marker, mining range or hit region. */
public final class ExpertMiningDecor {
    public static final int MAX_CASTS = 4, STROKES_PER_CAST = 118, QUADS_PER_CAST = STROKES_PER_CAST * 2,
            MAX_QUADS = MAX_CASTS * QUADS_PER_CAST;
    public record Ray(String skill, long token, boolean shown, boolean active, Point origin, Point direction, double length, double age) {}
    private ExpertMiningDecor() {}

    public static FxLib.Parts fx(Ray ray, double time, VisualTier tier) {
        if (ray == null || tier == null || !tier.enhanced() || !"mine_ray_expert".equals(ray.skill)
                || ray.token <= 0 || !ray.shown || !ray.active || ray.origin == null || !ray.origin.finite()
                || ray.direction == null || !ray.direction.finite() || !Double.isFinite(ray.length)
                || ray.length < 1.2 || ray.length > 15.001 || !Double.isFinite(ray.age) || ray.age < 0
                || !Double.isFinite(time)) return FxLib.Parts.EMPTY;
        double n = ray.direction.length();
        if (!Double.isFinite(n) || n < .999 || n > 1.001) return FxLib.Parts.EMPTY;
        Point axis = ray.direction.scale(1 / n), reference = Math.abs(axis.y()) > .9 ? new Point(1, 0, 0) : new Point(0, 1, 0);
        Point side = axis.cross(reference); side = side.scale(1 / side.length());
        Point up = side.cross(axis);
        double end = Math.min(4.8, ray.length * .85), start = Math.min(1.1, end * .3);
        double fade = Math.min(1, ray.age / 4), phase = time * .08;
        var strokes = new ArrayList<Stroke>(STROKES_PER_CAST);
        for (int layer = 0; layer < 3; layer++) {
            double at = start + (end - start) * (.18 + .32 * layer), radius = .20 + .075 * layer;
            Point center = ray.origin.plus(axis.scale(at));
            for (int segment = 0; segment < 12; segment++) {
                if (segment % 4 == 3) continue; // Open rings preserve the target and distinguish focusing from a solid shield.
                double a = Math.PI * 2 * segment / 12 + phase * (layer % 2 == 0 ? 1 : -1);
                double b = a + Math.PI * 2 * .76 / 12;
                line(strokes, ring(center, side, up, radius, a), ring(center, side, up, radius, b), fade, tier, true);
            }
        }
        for (int strand = 0; strand < 2; strand++) {
            Point previous = null;
            for (int i = 0; i <= 16; i++) {
                double t = i / 16.0, at = start + (end - start) * t;
                double angle = phase * 1.8 + t * Math.PI * 2.5 + strand * Math.PI;
                Point next = ring(ray.origin.plus(axis.scale(at)), side, up, .105 + .04 * Math.sin(t * Math.PI), angle);
                if (previous != null) line(strokes, previous, next, fade * .75, tier, false);
                previous = next;
            }
        }
        return new FxLib.Parts(List.copyOf(strokes), List.of(), List.of());
    }
    private static Point ring(Point center, Point side, Point up, double radius, double angle) {
        return center.plus(side.scale(radius * Math.cos(angle))).plus(up.scale(radius * Math.sin(angle)));
    }
    private static void line(List<Stroke> out, Point a, Point b, double fade, VisualTier tier, boolean ring) {
        if (fade <= 0) return;
        double width = ring ? .016 : .009;
        // Keep bounded brightness/width at both tiers so the existing core and ore remain readable.
        out.add(new Stroke(new Segment(a, b), width * 3, 0x50E879, FxLib.alpha(70 * tier.glow, fade), RailThunderArrayGrandGeometry.ARC_GLOW));
        out.add(new Stroke(new Segment(a, b), width, 0xB6FFAE, FxLib.alpha(ring ? 185 : 130, fade), RailThunderArrayGrandGeometry.WHITE));
    }
}
