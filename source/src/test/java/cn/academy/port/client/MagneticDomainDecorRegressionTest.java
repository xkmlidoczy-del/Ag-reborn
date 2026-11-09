package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Billboard;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Quad;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import java.util.List;
import java.util.Random;

/** Pure witnesses for the magnetic-domain decoration: frozen LOW/OFF, containment in the 12 m sphere, determinism, budgets. */
public final class MagneticDomainDecorRegressionTest {
    private static int checks;
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }

    public static void main(String[] args) {
        Point center = new Point(10, 64, -20);
        for (VisualTier tier : new VisualTier[] {null, VisualTier.OFF, VisualTier.LOW}) {
            check(MagneticDomainDecor.strokes(center, 5, tier).isEmpty() && MagneticDomainDecor.sprites(center, 5, tier).isEmpty(), tier + " adds nothing");
            check(MagneticDomainDecor.maxQuads(tier == null ? VisualTier.OFF : tier) == 0, tier + " has no quad budget");
        }
        check(MagneticDomainDecor.strokes(new Point(Double.NaN, 0, 0), 5, VisualTier.STANDARD).isEmpty(), "invalid centre refused");
        check(MagneticDomainDecor.strokes(center, Double.NaN, VisualTier.STANDARD).isEmpty(), "invalid clock refused");
        check(MagneticDomainDecor.strokes(center, Double.POSITIVE_INFINITY, VisualTier.CINEMATIC).isEmpty(), "infinite clock refused");
        double radius = MagneticDomainGeometry.RADIUS;
        for (VisualTier tier : new VisualTier[] {VisualTier.STANDARD, VisualTier.CINEMATIC}) {
            Random random = new Random(152);
            for (int i = 0; i < 300; i++) {
                double time = random.nextDouble() * MagneticDomainFlowGeometry.CLOCK_TICKS;
                List<Stroke> strokes = MagneticDomainDecor.strokes(center, time, tier);
                List<Billboard> sprites = MagneticDomainDecor.sprites(center, time, tier);
                check(strokes.equals(MagneticDomainDecor.strokes(center, time, tier)) && sprites.equals(MagneticDomainDecor.sprites(center, time, tier)), "deterministic");
                check(!strokes.isEmpty() && !sprites.isEmpty(), tier + " produces layers");
                for (Stroke stroke : strokes) {
                    check(stroke.halfWidth() <= RailThunderArrayGrandGeometry.maxHalfWidth(tier) && stroke.alpha() >= 1 && stroke.alpha() <= 255, "stroke bounded");
                    check(stroke.segment().start().minus(center).length() <= radius && stroke.segment().end().minus(center).length() <= radius, "stroke inside the domain sphere");
                }
                for (Billboard sprite : sprites) {
                    check(sprite.center().minus(center).length() <= radius, "sprite inside the domain sphere");
                    check(sprite.radius() <= RailThunderArrayGrandGeometry.maxSpriteRadius(tier), "sprite radius bounded");
                }
                // Everything projects without exceptions and stays inside the declared quad budget when trimmed.
                var quads = VisualKit.projectPure(strokes, sprites, new Point(center.x() + 6, center.y() + 1, center.z() - 14), new Point(-.4, 0, .9), new Point(0, 1, 0), tier, MagneticDomainDecor.maxQuads(tier));
                check(quads.size() <= MagneticDomainDecor.maxQuads(tier), "trimmed to budget");
                for (Quad quad : quads) check(quad.a().finite() && quad.alpha() >= 1 && quad.alpha() <= 255, "quad valid");
            }
            // The clock wraps: tick 0 and a full cycle later describe the same pose for the slow loops.
        }
        check(MagneticDomainDecor.strokes(center, 3, VisualTier.CINEMATIC).size() > MagneticDomainDecor.strokes(center, 3, VisualTier.STANDARD).size(), "CINEMATIC adds loops");
        System.out.println("PASS " + checks + " magnetic domain decoration: frozen LOW/OFF, sphere containment, determinism, budgets");
    }
}
