package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGeometry.Segment;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import java.util.ArrayList;
import java.util.List;

/**
 * CINEMATIC coordinate folds for confirmed NEW351 publications and contacts. Two orthogonal frames close on the exact point.
 * Gray-white original palette, no portal or purple. Pure; never a needle path, teleport destination or damage region.
 */
public final class LandingArrayDecor {
    public static final int MAX_QUADS = 520;
    private static final int WHITE = 0xF4F7FA, GREY = 0xA9B3BD;

    private LandingArrayDecor() {}

    public static FxLib.Parts cue(Point at, boolean contact, int age, int life, double time, VisualTier tier) {
        if (tier == null || tier.layers < 3 || at == null || !at.finite() || age < 0 || life <= 0 || age >= life || !Double.isFinite(time)) return FxLib.Parts.EMPTY;
        double fade = 1 - (double) age / life;
        double close = 1 - Math.min(1, age / 5.0);
        double half = (contact ? .62 : .46) + .32 * close;
        double height = (contact ? 1.7 : 1.1) - .48 * (1 - close);
        Point mid = at.plus(new Point(0, height * .5 + .08, 0));
        List<Stroke> folds = new ArrayList<>();
        for (Point axis : List.of(new Point(1, 0, 0), new Point(0, 0, 1))) {
            for (int side : new int[]{-1, 1}) {
                Point outer = mid.plus(axis.scale(side * half));
                Point low = outer.plus(new Point(0, -height * .5, 0));
                Point high = outer.plus(new Point(0, height * .5, 0));
                Point in = axis.scale(-side * half * .52);
                int alpha = FxLib.alpha(contact ? 225 : 175, fade);
                folds.add(new Stroke(new Segment(low, high), .022, WHITE, alpha, RailThunderArrayGrandGeometry.GLOW_ADD));
                folds.add(new Stroke(new Segment(low, low.plus(in)), .017, GREY, alpha, RailThunderArrayGrandGeometry.GLOW_ADD));
                folds.add(new Stroke(new Segment(high, high.plus(in)), .017, GREY, alpha, RailThunderArrayGrandGeometry.GLOW_ADD));
            }
        }
        FxLib.Builder out = new FxLib.Builder();
        out.add(new FxLib.Parts(List.copyOf(folds),
                List.of(new RailThunderArrayGrandGeometry.Billboard(mid, .18 + .14 * close, WHITE,
                        FxLib.alpha(contact ? 195 : 120, fade), RailThunderArrayGrandGeometry.STAR, 0)), List.of()));
        out.add(FxLib.groundGlow(at.plus(new Point(0, .024, 0)), .35 + half, GREY,
                90 * fade, .4 + half, 75 * fade, tier));
        return out.build();
    }
}
