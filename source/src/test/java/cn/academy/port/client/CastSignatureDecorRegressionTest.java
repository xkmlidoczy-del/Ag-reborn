package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.highpower.core.CastPose;
import java.util.HashSet;

/** Headless geometry witnesses. These do not establish in-game appearance or subjective sufficiency. */
public final class CastSignatureDecorRegressionTest {
    private static int checks;
    private static void check(boolean ok, String detail) { checks++; if (!ok) throw new AssertionError(detail); }
    private static int size(FxLib.Parts p) { return p.strokes().size() + p.sprites().size() + p.decals().size(); }
    public static void main(String[] args) {
        Point feet = new Point(0, 0, 0), hand = new Point(.3, 1.2, .5), look = new Point(0, 0, 1);
        var categoryShapes = new HashSet<String>();
        for (String category : new String[] {"electromaster", "meltdowner", "teleporter", "vecmanip"}) {
            int low = 0, high = 0;
            var shapes = new HashSet<String>();
            for (var action : CastPose.Archetype.values()) {
                var base = CastSignatureDecor.fx("test", action, category, 1, feet, hand, look, 12, 2, 17, 30, VisualTier.STANDARD);
                var large = CastSignatureDecor.fx("test", action, category, 5, feet, hand, look, 12, 2, 17, 30, VisualTier.CINEMATIC);
                low += size(base); high += size(large); shapes.add(base.toString());
                check(size(base) > 0 && size(large) > 0 && size(large) < 500, category + "/" + action + " meaningful bounded geometry");
                check(size(CastSignatureDecor.fx("test", action, category, 5, feet, hand, look, 30, 14, 17, 30, VisualTier.CINEMATIC)) == 0, "release expires");
                check(size(CastSignatureDecor.fx("test", action, category, 5, feet, hand, look, 12, -1, 17, 30, VisualTier.OFF)) == 0, "OFF honored");
                for (var stroke : large.strokes()) check(stroke.segment().start().finite() && stroke.segment().end().finite(), "finite stroke");
            }
            check(high > low, category + " advanced geometry is richer");
            check(shapes.size() >= 10, category + " action shapes differ");
            categoryShapes.add(CastSignatureDecor.fx("test", CastPose.Archetype.AIM, category, 5, feet, hand, look, 12, 2, 17, 30, VisualTier.CINEMATIC).toString());
        }
        check(categoryShapes.size() == 4, "four category identities differ");
        check(size(CastSignatureDecor.fx("test", CastPose.Archetype.AIM, "teleporter", 5, feet, hand, look, Double.NaN, 0, 17, 30, VisualTier.CINEMATIC)) == 0, "bad time is silent");
        System.out.println("CastSignatureDecorRegressionTest: " + checks + " checks passed");
    }
}
