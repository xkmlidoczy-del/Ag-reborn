package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGeometry.Segment;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Billboard;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import java.util.ArrayList;
import java.util.List;

/**
 * CINEMATIC presentation for NEW233 point meltdown: the focus beam gets a tapered additive glow and RGB dispersion, the
 * heat point pulses with halo and spokes and sheds rising molten motes and glints, and a completed melt bursts. Original
 * green-white palette. Pure; never a mining target, removal or damage region.
 */
public final class PointMeltdownDecor {
    public static final int MAX_QUADS = 700;
    public record Beam(Point origin, Point target, int focusTicks, boolean focusing, boolean finished, double age) {}
    private static final int CORE = 0xD8F8D8, GREEN = 0x6AF26A, DEEP = 0x1FA54A, HOT = 0xFFE9A0;

    private PointMeltdownDecor() {}

    public static FxLib.Parts fx(List<Beam> beams, long seed, double time, VisualTier tier) {
        if (tier == null || tier.layers < 3 || !Double.isFinite(time)) return FxLib.Parts.EMPTY;
        double w = tier.widthScale;
        FxLib.Builder out = new FxLib.Builder();
        for (int i = 0; i < beams.size(); i++) {
            Beam b = beams.get(i);
            if (b == null || b.origin == null || b.target == null || !RailThunderArrayGeometry.validEndpoints(b.origin, b.target) || b.target.minus(b.origin).length() < 1e-6) continue;
            double ramp = b.finished ? Math.max(.05, 1 - b.age / 10) : Math.min(1, .35 + b.focusTicks / 20.0), pulse = .75 + .25 * Math.sin(time * .5 + i);
            long bs = seed * 13 + i;
            out.add(new FxLib.Parts(List.of(new Stroke(new Segment(b.origin, b.target), .22 * w, GREEN, FxLib.alpha(105 * tier.glow, ramp), RailThunderArrayGrandGeometry.GLOW_ADD),
                    new Stroke(new Segment(b.origin, b.target), .03 * w, 0xFFFFFF, FxLib.alpha(210, ramp), RailThunderArrayGrandGeometry.GLOW_ADD)), List.of(), List.of()));
            out.add(FxLib.dispersion(b.origin, b.target, .035 * w, 95 * ramp, tier));
            Point dir = b.target.minus(b.origin); dir = dir.scale(1 / dir.length());
            double heat = b.finished ? 1 : Math.min(1, b.focusTicks / 12.0);
            List<Billboard> glow = List.of(
                    new Billboard(b.target, (.5 + .6 * heat) * w, GREEN, FxLib.alpha(110 * tier.glow, ramp * pulse), RailThunderArrayGrandGeometry.RADIAL, 0),
                    new Billboard(b.target, (.22 + .3 * heat) * w, HOT, FxLib.alpha(235 * tier.glow, ramp * pulse), RailThunderArrayGrandGeometry.RADIAL, 0),
                    new Billboard(b.target, (.9 + 1.1 * heat) * w, CORE, FxLib.alpha(150 * tier.glow, ramp), RailThunderArrayGrandGeometry.STAR, time * .05));
            out.add(new FxLib.Parts(List.of(), glow, List.of()));
            out.add(FxLib.embers(b.target, .6 * w, 2.2, bs ^ 0x233, time, 14, HOT, DEEP, tier));
            if (b.focusing || b.finished) {
                double sparkAge = b.finished ? b.age : time % 14;
                out.add(FxLib.sparkShower(b.target, dir.scale(-1), bs ^ 0x66, sparkAge, b.finished ? 40 : 14, b.finished ? 11 : 6, 1.2, 9, 14, HOT, DEEP, tier));
                if (b.finished) out.add(FxLib.spokes(b.target, bs ^ 0x77, b.age, 10, 16, 2.6 * w, CORE, 210, tier));
            }
            out.add(FxLib.lensStreak(b.origin, 1.6 * w, GREEN, 90, ramp, tier));
        }
        return out.build();
    }
}
