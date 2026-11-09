package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGeometry.Segment;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import java.util.ArrayList;
import java.util.List;

/**
 * CINEMATIC presentation for the shear windblade fronts: pressure wakes streaming behind every chord, RGB-dispersed
 * leading edges, dust motes torn off the front and a wind-cut spark spray per blade. Pure; built from the already
 * interpolated {@link ShearWindbladeRenderGeometry.Shape}; never a damage or contact region.
 */
public final class ShearWindbladeDecor {
    public static final int MAX_QUADS = 700;
    private static final int GREY = 0xEAF2F8, COOL = 0x8EA6BC;

    private ShearWindbladeDecor() {}

    private static Point p(cn.academy.port.highpower.core.ShearWindbladeGeometry.Point q) { return new Point(q.x(), q.y(), q.z()); }

    public static FxLib.Parts fx(ShearWindbladeRenderGeometry.Shape shape, ShearWindbladeState.Controller cast, double time, VisualTier tier) {
        if (tier == null || tier.layers < 3 || shape == null || cast == null || !Double.isFinite(time)) return FxLib.Parts.EMPTY;
        double w = tier.widthScale;
        FxLib.Builder out = new FxLib.Builder();
        List<Stroke> wake = new ArrayList<>();
        for (var line : shape.strokes()) {
            Point a = p(line.from()), b = p(line.to()), mid = a.lerp(b, .5);
            Point travel = null;
            for (var front : cast.fronts()) if (front.index() == line.blade()) {
                Point before = p(front.previous().get(Math.min(line.chord(), front.previous().size() - 1)));
                Point now = p(front.current().get(Math.min(line.chord(), front.current().size() - 1)));
                Point d = now.minus(before);
                if (d.length() > 1e-6) travel = d.scale(1 / d.length());
            }
            if (travel == null) continue;
            double fade = Math.max(.05, line.alpha() / 238.0);
            // Pressure wake: a long tapered streak from the front back along the path it just swept.
            Point tail = mid.minus(travel.scale(3.4 * w));
            addStroke(wake, mid, tail, .30 * w, GREY, (int) Math.round(90 * tier.glow * fade), RailThunderArrayGrandGeometry.GLOW_ADD);
            addStroke(wake, a, b, .035 * w, 0xFFFFFF, (int) Math.round(210 * fade), RailThunderArrayGrandGeometry.GLOW_ADD);
            // Dust torn off the front: two motes per chord drifting backward with the wake.
            for (int k = 0; k < 2; k++) {
                double phase = ((time * .05 + line.chord() * .21 + k * .5 + line.blade() * .13) % 1 + 1) % 1;
                Point at = mid.minus(travel.scale(phase * 4.0 * w)).plus(new Point(0, Math.sin(time * .2 + line.chord() + k) * .25, 0));
                out.add(new FxLib.Parts(List.of(), List.of(new RailThunderArrayGrandGeometry.Billboard(at, (.10 + .06 * k) * w, COOL,
                        FxLib.alpha(190 * tier.glow, fade * Math.sin(Math.PI * phase)), RailThunderArrayGrandGeometry.RADIAL, 0)), List.of()));
            }
        }
        out.add(new FxLib.Parts(List.copyOf(wake), List.of(), List.of()));
        for (var front : cast.fronts()) {
            if (front.current().size() < 3) continue;
            Point first = p(front.current().get(0)), last = p(front.current().get(front.current().size() - 1));
            int mid = front.current().size() / 2;
            Point center = p(front.current().get(mid)), before = p(front.previous().get(Math.min(mid, front.previous().size() - 1)));
            Point d = center.minus(before);
            Point axis = d.length() > 1e-6 ? d.scale(1 / d.length()) : new Point(0, 0, 1);
            long seed = cast.id().token() * 31 + front.index();
            out.add(FxLib.dispersion(first, last, .05 * w, 95, tier));
            out.add(FxLib.sparkShower(center, axis, seed, Math.max(0, cast.age() % 20), 22, 9, .7, 0, 12, 0xFFFFFF, COOL, tier));
            out.add(FxLib.lensStreak(center, 2.6 * w, GREY, 110, 1, tier));
            out.add(FxLib.spokes(first, seed ^ 5, Math.max(0, cast.age() % 16), 16, 6, 1.1 * w, GREY, 120, tier));
            out.add(FxLib.spokes(last, seed ^ 9, Math.max(0, cast.age() % 16), 16, 6, 1.1 * w, GREY, 120, tier));
        }
        return out.build();
    }

    private static void addStroke(List<Stroke> out, Point from, Point to, double width, int rgb, int alpha, int texture) {
        if (alpha < 1 || !RailThunderArrayGeometry.validEndpoints(from, to) || to.minus(from).length() < 1e-6) return;
        out.add(new Stroke(new Segment(from, to), width, rgb, Math.min(255, alpha), texture));
    }
}
