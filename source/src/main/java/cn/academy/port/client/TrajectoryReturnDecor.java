package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGeometry.Segment;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Billboard;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import java.util.ArrayList;
import java.util.List;

/**
 * CINEMATIC presentation for NEW431 trajectory return: every reversed projectile trail becomes a dispersed, tapered
 * afterimage ribbon, the reversal point flares with a freeze-frame star and shock ring, and returning projectiles shed
 * white-gray sparks. Pure; trails and reversal points come from authenticated receipts and nothing here is a hit region.
 */
public final class TrajectoryReturnDecor {
    public static final int MAX_QUADS = 700;
    public record Trail(List<Point[]> segments, Point reversal, Point position, Point velocity, boolean reversed, double reversedAge) {}
    private static final int WHITE = 0xF4F8FB, GREY = 0xA7B6C4;

    private TrajectoryReturnDecor() {}

    public static FxLib.Parts fx(List<Trail> trails, long seed, double time, VisualTier tier) {
        if (tier == null || tier.layers < 3 || !Double.isFinite(time)) return FxLib.Parts.EMPTY;
        double w = tier.widthScale;
        FxLib.Builder out = new FxLib.Builder();
        for (int t = 0; t < trails.size(); t++) {
            Trail trail = trails.get(t);
            if (trail == null) continue;
            long ts = seed * 29 + t;
            List<Stroke> strokes = new ArrayList<>();
            int n = trail.segments.size();
            for (int i = 0; i < n; i++) {
                Point[] seg = trail.segments.get(i);
                if (seg == null || !RailThunderArrayGeometry.validEndpoints(seg[0], seg[1]) || seg[1].minus(seg[0]).length() < 1e-6) continue;
                double age = n <= 1 ? 1 : (i + 1) / (double) n;   // newer segments (later in the list) burn brighter
                strokes.add(new Stroke(new Segment(seg[0], seg[1]), .22 * w * (.4 + .6 * age), GREY, FxLib.alpha(100 * tier.glow, age), RailThunderArrayGrandGeometry.ARC_GLOW));
                strokes.add(new Stroke(new Segment(seg[0], seg[1]), .02 * w, WHITE, FxLib.alpha(215, age), RailThunderArrayGrandGeometry.WHITE));
                out.add(FxLib.dispersion(seg[0], seg[1], .04 * w, 85 * age, tier));
            }
            out.add(new FxLib.Parts(List.copyOf(strokes), List.of(), List.of()));
            if (trail.reversed && trail.reversal != null && trail.reversal.finite()) {
                double age = Math.max(0, trail.reversedAge), fade = Math.max(.05, 1 - age / 12);
                out.add(new FxLib.Parts(List.of(), List.of(
                        new Billboard(trail.reversal, .7 * w, WHITE, FxLib.alpha(235 * tier.glow, fade), RailThunderArrayGrandGeometry.RADIAL, 0),
                        new Billboard(trail.reversal, 1.5 * w, GREY, FxLib.alpha(170 * tier.glow, fade), RailThunderArrayGrandGeometry.STAR, age * .25),
                        new Billboard(trail.reversal, (.3 + age * .35) * w * 1.6, WHITE, FxLib.alpha(150 * tier.glow, fade), RailThunderArrayGrandGeometry.RING, 0)), List.of()));
                out.add(FxLib.spokes(trail.reversal, ts ^ 0x431, age, 12, 12, 2.2 * w, WHITE, 190, tier));
            }
            if (trail.position != null && trail.position.finite() && trail.velocity != null && trail.velocity.length() > 1e-6) {
                Point axis = trail.velocity.scale(-1 / trail.velocity.length());
                out.add(FxLib.sparkShower(trail.position, axis, ts ^ 0x5E, time % 12, 12, 6, .6, 4, 10, WHITE, GREY, tier));
            }
        }
        return out.build();
    }
}
