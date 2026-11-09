package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGeometry.Segment;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Billboard;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import java.util.ArrayList;
import java.util.List;

/**
 * CINEMATIC presentation for NEW443 wind pressure lift: counter-rotating helical streamlines climbing the lift column,
 * beads riding them, pressure rings pulsing out from the anchor, rising dust and a soft lens glow. White-gray vector
 * palette. Pure; the column is vertical (leaning with the drift) at the existing 6 m height and the existing 6 m footprint and nothing here is a floor, shield or safe descent.
 */
public final class WindPressureLiftDecor {
    public static final int MAX_QUADS = 700;
    private static final int WHITE = 0xF2F6F9, GREY = 0xB4C0CB;
    private static final double HEIGHT = 6;

    private WindPressureLiftDecor() {}

    private static Point cross(Point a, Point b) { return a.cross(b); }

    public static FxLib.Parts fx(Point anchor, Point direction, int age, double time, VisualTier tier) {
        if (tier == null || tier.layers < 3 || anchor == null || !anchor.finite() || direction == null || !direction.finite() || direction.length() < 1e-6 || !Double.isFinite(time)) return FxLib.Parts.EMPTY;
        // The state's direction is the horizontal drift; the lift column itself is vertical, leaning slightly with the drift.
        Point lean = new Point(0, 1, 0).plus(direction.scale(.12 / direction.length()));
        Point axis = lean.scale(1 / lean.length());
        Point ref = Math.abs(axis.y()) > .9 ? new Point(1, 0, 0) : new Point(0, 1, 0);
        Point side = cross(axis, ref); side = side.scale(1 / side.length());
        Point up = cross(side, axis);
        double w = tier.widthScale, ramp = Math.min(1, .3 + age / 6.0);
        FxLib.Builder out = new FxLib.Builder();
        List<Stroke> strokes = new ArrayList<>();
        List<Billboard> beads = new ArrayList<>();
        int helices = 3, steps = 18;
        for (int h = 0; h < helices; h++) {
            double phase = h * Math.PI * 2 / helices, spin = (h % 2 == 0 ? 1 : -1) * time * .16;
            Point prev = null;
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps, radius = (.45 + .5 * Math.sin(Math.PI * t)) * w * .55, a = phase + spin + t * Math.PI * 3.2;
                Point at = anchor.plus(axis.scale(t * HEIGHT * ramp)).plus(side.scale(Math.cos(a) * radius)).plus(up.scale(Math.sin(a) * radius));
                if (prev != null && at.minus(prev).length() > 1e-6) {
                    double fade = Math.sin(Math.PI * (i - .5) / steps);
                    strokes.add(new Stroke(new Segment(prev, at), .06 * w, GREY, FxLib.alpha(110 * tier.glow, .3 + .7 * fade), RailThunderArrayGrandGeometry.ARC_GLOW));
                    strokes.add(new Stroke(new Segment(prev, at), .012, WHITE, FxLib.alpha(210, .3 + .7 * fade), RailThunderArrayGrandGeometry.WHITE));
                }
                prev = at;
            }
            double travel = ((time * (.02 + .004 * h) + h * .31) % 1 + 1) % 1, a = phase + spin + travel * Math.PI * 3.2, radius = (.45 + .5 * Math.sin(Math.PI * travel)) * w * .55;
            Point bead = anchor.plus(axis.scale(travel * HEIGHT * ramp)).plus(side.scale(Math.cos(a) * radius)).plus(up.scale(Math.sin(a) * radius));
            beads.add(new Billboard(bead, .22 * w, WHITE, FxLib.alpha(235 * tier.glow, Math.sin(Math.PI * travel)), RailThunderArrayGrandGeometry.RADIAL, 0));
            beads.add(new Billboard(bead, .6 * w, GREY, FxLib.alpha(90 * tier.glow, Math.sin(Math.PI * travel)), RailThunderArrayGrandGeometry.RADIAL, 0));
        }
        out.add(new FxLib.Parts(List.copyOf(strokes), List.copyOf(beads), List.of()));
        double pulse = (time % 50) / 50;
        out.add(FxLib.groundGlow(anchor.plus(new Point(0, .03, 0)), 2.4 * w, GREY, 70, (.3 + pulse * 3.2) * w, 150 * Math.sin(Math.PI * pulse), tier));
        out.add(FxLib.embers(anchor, 1.3 * w, HEIGHT * .8, 443L, time, 26, WHITE, GREY, tier));
        out.add(FxLib.lensStreak(anchor.plus(axis.scale(.4)), 2.8 * w, WHITE, 100, ramp, tier));
        return out.build();
    }
}
