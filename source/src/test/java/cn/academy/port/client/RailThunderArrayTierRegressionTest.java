package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Billboard;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Quad;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Pure witnesses for the STANDARD/CINEMATIC decorative layers: frozen LOW/OFF, bounds, determinism and projection. */
public final class RailThunderArrayTierRegressionTest {
    private static final Point CAMERA = new Point(0, 0, 0), LOOK = new Point(0, 0, 1), UP = new Point(0, 1, 0);
    private static int checks;

    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    private static Point p(double x, double y, double z) { return new Point(x, y, z); }

    public static void main(String[] args) {
        frozenTiers();
        boundedLayers();
        determinismAndLifetime();
        projection();
        randomizedInvariants();
        System.out.println("PASS " + checks + " NEW151 tier layers: frozen LOW/OFF, bounded widths/budgets, determinism, projection");
    }

    private static void frozenTiers() {
        Point from = p(0, 0, 10), to = p(0, 0, 30);
        for (VisualTier tier : new VisualTier[] {VisualTier.OFF, VisualTier.LOW}) {
            check(RailThunderArrayGrandGeometry.amplify(from, to, 0, 7, 2, false, true, tier).isEmpty(), tier + " adds no strokes");
            check(RailThunderArrayGrandGeometry.billboards(from, to, 0, 7, 2, false, true, tier).isEmpty(), tier + " adds no sprites");
            check(RailThunderArrayGrandGeometry.maxStrokes(tier) == RailThunderArrayGrandGeometry.MAX_STROKES_PER_CONTROLLER, tier + " stroke cap frozen");
            check(RailThunderArrayGrandGeometry.maxQuads(tier) == RailThunderArrayGrandGeometry.MAX_QUADS_PER_CONTROLLER, tier + " quad cap frozen");
            check(RailThunderArrayGrandGeometry.maxGlobalQuads(tier) == RailThunderArrayGrandGeometry.MAX_QUADS_GLOBAL, tier + " global cap frozen");
            check(RailThunderArrayGrandGeometry.maxHalfWidth(tier) == RailThunderArrayGrandGeometry.MAX_HALF_WIDTH, tier + " width ceiling frozen");
        }
        check(RailThunderArrayGrandGeometry.amplify(from, to, 0, 7, 2, false, true, null).isEmpty(), "null tier is inert");
        // The frozen overload must equal the tier-aware one at LOW for classic strokes.
        List<Stroke> classic = RailThunderArrayGrandGeometry.emission(from, to, 0, 7, 2, false, true, false);
        for (Stroke stroke : classic)
            check(RailThunderArrayGrandGeometry.project(stroke, CAMERA, LOOK, UP)
                    .equals(RailThunderArrayGrandGeometry.project(stroke, CAMERA, LOOK, UP, VisualTier.LOW)), "LOW projection is bit-identical");
    }

    private static void boundedLayers() {
        Point from = p(1, -1, 6), to = p(-3, 4, 40);
        for (VisualTier tier : new VisualTier[] {VisualTier.STANDARD, VisualTier.CINEMATIC}) {
            double limit = RailThunderArrayGrandGeometry.maxHalfWidth(tier);
            check(limit > RailThunderArrayGrandGeometry.MAX_HALF_WIDTH, tier + " widens the ceiling");
            check(RailThunderArrayGrandGeometry.maxStrokes(tier) == RailThunderArrayGrandGeometry.MAX_STROKES_PER_CONTROLLER * tier.budgetScale, tier + " scaled stroke cap");
            for (double age : new double[] {0, .5, 1, 2.9, 3, 4.99}) for (boolean hit : new boolean[] {false, true}) for (boolean hold : new boolean[] {false, true}) {
                List<Stroke> strokes = RailThunderArrayGrandGeometry.amplify(from, to, 1, 99, age, hold, hit, tier);
                check(!strokes.isEmpty() && strokes.size() <= 110, tier + " extra strokes stay bounded per path");
                for (Stroke stroke : strokes) {
                    check(stroke.halfWidth() > 0 && stroke.halfWidth() <= limit + 1e-12, "stroke width within tier ceiling");
                    check(stroke.alpha() >= 1 && stroke.alpha() <= 255 && stroke.rgb() >= 0 && stroke.rgb() <= 0xFFFFFF, "stroke colour/alpha valid");
                    check(stroke.texture() >= RailThunderArrayGrandGeometry.WHITE && stroke.texture() <= RailThunderArrayGrandGeometry.LAST_TEXTURE
                            && !(stroke.texture() >= RailThunderArrayGrandGeometry.RADIAL && stroke.texture() <= RailThunderArrayGrandGeometry.RING), "strokes use line materials only");
                }
                List<Billboard> sprites = RailThunderArrayGrandGeometry.billboards(from, to, 1, 99, age, hold, hit, tier);
                check(sprites.size() >= 3 && sprites.size() <= 12, tier + " sprite count bounded");
                for (Billboard sprite : sprites) {
                    check(sprite.radius() > 0 && sprite.radius() <= RailThunderArrayGrandGeometry.maxSpriteRadius(tier), "sprite radius bounded");
                    check(RailThunderArrayGrandGeometry.sprite(sprite.texture()), "sprites use sprite materials");
                    check(sprite.alpha() >= 1 && sprite.alpha() <= 255, "sprite alpha valid");
                }
            }
            // Cinematic adds the spark/burst group on top of standard.
        }
        check(RailThunderArrayGrandGeometry.amplify(from, to, 1, 99, 2, false, true, VisualTier.CINEMATIC).size()
                > RailThunderArrayGrandGeometry.amplify(from, to, 1, 99, 2, false, true, VisualTier.STANDARD).size(), "CINEMATIC layers exceed STANDARD");
        check(RailThunderArrayGrandGeometry.billboards(from, to, 1, 99, 2, false, true, VisualTier.CINEMATIC).size()
                > RailThunderArrayGrandGeometry.billboards(from, to, 1, 99, 2, false, true, VisualTier.STANDARD).size(), "CINEMATIC sprites exceed STANDARD");
        check(RailThunderArrayGrandGeometry.billboards(from, to, 1, 99, 2, false, true, VisualTier.STANDARD).size()
                > RailThunderArrayGrandGeometry.billboards(from, to, 1, 99, 2, false, false, VisualTier.STANDARD).size(), "contact adds sprites only on HIT");
    }

    private static void determinismAndLifetime() {
        Point from = p(0, 0, 8), to = p(5, 1, 33);
        for (VisualTier tier : new VisualTier[] {VisualTier.STANDARD, VisualTier.CINEMATIC}) {
            check(RailThunderArrayGrandGeometry.amplify(from, to, 2, 5, 1.7, false, true, tier)
                    .equals(RailThunderArrayGrandGeometry.amplify(from, to, 2, 5, 1.7, false, true, tier)), "strokes deterministic");
            check(RailThunderArrayGrandGeometry.billboards(from, to, 2, 5, 1.7, false, true, tier)
                    .equals(RailThunderArrayGrandGeometry.billboards(from, to, 2, 5, 1.7, false, true, tier)), "sprites deterministic");
            check(!RailThunderArrayGrandGeometry.amplify(from, to, 2, 5, 1.7, false, true, tier)
                    .equals(RailThunderArrayGrandGeometry.amplify(from, to, 2, 6, 1.7, false, true, tier)) || tier.layers < 2, "token reseeds sparks");
            for (double age : new double[] {5, 5.01, 100, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
                check(RailThunderArrayGrandGeometry.amplify(from, to, 2, 5, age, false, true, tier).isEmpty(), "no strokes outside the five-tick life: " + age);
                check(RailThunderArrayGrandGeometry.billboards(from, to, 2, 5, age, false, true, tier).isEmpty(), "no sprites outside the five-tick life: " + age);
            }
            check(RailThunderArrayGrandGeometry.amplify(from, from, 2, 5, 1, false, true, tier).isEmpty(), "degenerate path rejected");
            check(RailThunderArrayGrandGeometry.amplify(from, to, 99, 5, 1, false, true, tier).isEmpty(), "bad axis rejected");
        }
    }

    private static void projection() {
        Point from = p(2, 0, 12), to = p(-2, 1, 40);
        for (VisualTier tier : new VisualTier[] {VisualTier.STANDARD, VisualTier.CINEMATIC}) {
            int quads = 0;
            for (Stroke stroke : RailThunderArrayGrandGeometry.amplify(from, to, 0, 3, 2, false, true, tier))
                for (Quad quad : RailThunderArrayGrandGeometry.project(stroke, CAMERA, LOOK, UP, tier)) {
                    quads++;
                    check(quad.minV() >= 0 && quad.maxV() <= 1 && quad.minV() <= quad.maxV(), "line UV fractions in range");
                    for (Point corner : List.of(quad.a(), quad.b(), quad.c(), quad.d()))
                        check(corner.finite() && corner.z() >= RailThunderArrayGrandGeometry.NEAR_DEPTH, "corner in front of near plane");
                }
            check(quads > 0, tier + " strokes produce quads");
            for (Billboard sprite : RailThunderArrayGrandGeometry.billboards(from, to, 0, 3, 2, false, true, tier)) {
                List<Quad> out = RailThunderArrayGrandGeometry.projectBillboard(sprite, CAMERA, LOOK, UP, tier);
                check(out.size() == 1, "sprite projects to exactly one quad");
                Quad quad = out.get(0);
                check(quad.minV() == 0 && quad.maxV() == 1 && quad.texture() == sprite.texture(), "sprite uses full UV and its material");
                check(quad.alpha() >= 1 && quad.alpha() <= sprite.alpha(), "reticle calming never brightens");
            }
        }
        // Behind the camera or invalid sprites are refused whole.
        Billboard behind = new Billboard(p(0, 0, -5), 1, 0xFFFFFF, 200, RailThunderArrayGrandGeometry.RADIAL, 0);
        check(RailThunderArrayGrandGeometry.projectBillboard(behind, CAMERA, LOOK, UP, VisualTier.STANDARD).isEmpty(), "behind camera refused");
        Billboard near = new Billboard(p(0, 0, .1), 1, 0xFFFFFF, 200, RailThunderArrayGrandGeometry.RADIAL, 0);
        check(RailThunderArrayGrandGeometry.projectBillboard(near, CAMERA, LOOK, UP, VisualTier.STANDARD).isEmpty(), "inside near plane refused");
        for (Billboard bad : List.of(new Billboard(p(0, 0, 10), 0, 0xFFFFFF, 200, 5, 0), new Billboard(p(0, 0, 10), 1, -1, 200, 5, 0),
                new Billboard(p(0, 0, 10), 1, 0xFFFFFF, 0, 5, 0), new Billboard(p(0, 0, 10), 1, 0xFFFFFF, 200, 2, 0),
                new Billboard(p(0, 0, 10), 1, 0xFFFFFF, 200, 5, Double.NaN), new Billboard(p(Double.NaN, 0, 10), 1, 0xFFFFFF, 200, 5, 0)))
            check(RailThunderArrayGrandGeometry.projectBillboard(bad, CAMERA, LOOK, UP, VisualTier.STANDARD).isEmpty(), "invalid sprite refused");
        check(RailThunderArrayGrandGeometry.projectBillboard(null, CAMERA, LOOK, UP, VisualTier.STANDARD).isEmpty(), "null sprite refused");
        // Every sprite generated late in the life (largest expanding ring) must still be projectable, never silently dropped.
        for (VisualTier tier : new VisualTier[] {VisualTier.STANDARD, VisualTier.CINEMATIC})
            for (double age : new double[] {0, 1, 2, 3, 4, 4.99})
                for (Billboard sprite : RailThunderArrayGrandGeometry.billboards(from, to, 0, 3, age, false, true, tier))
                    check(RailThunderArrayGrandGeometry.projectBillboard(sprite, CAMERA, LOOK, UP, tier).size() == 1, "generated sprite is projectable: " + tier + " age " + age);
        // A sprite centred on the view axis is calmed (reticle readability) but still shown.
        Billboard centred = new Billboard(p(0, 0, 20), 1, 0xFFFFFF, 200, 5, 0);
        int calm = RailThunderArrayGrandGeometry.projectBillboard(centred, CAMERA, LOOK, UP, VisualTier.STANDARD).get(0).alpha();
        Billboard offset = new Billboard(p(6, 0, 20), 1, 0xFFFFFF, 200, 5, 0);
        int open = RailThunderArrayGrandGeometry.projectBillboard(offset, CAMERA, LOOK, UP, VisualTier.STANDARD).get(0).alpha();
        check(calm < open && calm >= (int) Math.round(200 * .35) - 1, "view-axis sprite is calmed, not removed");
    }

    private static void randomizedInvariants() {
        Random random = new Random(20261017L);
        for (int i = 0; i < 4000; i++) {
            Point from = p(random.nextGaussian() * 30, random.nextGaussian() * 10, random.nextGaussian() * 30);
            Point to = from.plus(p(random.nextGaussian() * 40, random.nextGaussian() * 15, random.nextGaussian() * 40));
            VisualTier tier = random.nextBoolean() ? VisualTier.STANDARD : VisualTier.CINEMATIC;
            double age = random.nextDouble() * 5;
            Point look = unit(p(random.nextGaussian(), random.nextGaussian() * .3, random.nextGaussian()));
            List<Stroke> strokes = new ArrayList<>(RailThunderArrayGrandGeometry.amplify(from, to, random.nextInt(6), random.nextLong(), age, random.nextBoolean(), random.nextBoolean(), tier));
            check(strokes.size() <= 110, "stroke count bounded under random paths");
            for (Stroke stroke : strokes)
                for (Quad quad : RailThunderArrayGrandGeometry.project(stroke, CAMERA, look, UP, tier)) {
                    check(quad.a().finite() && quad.b().finite() && quad.c().finite() && quad.d().finite(), "projected corners finite");
                    check(quad.alpha() >= 1 && quad.alpha() <= 255, "projected alpha valid");
                }
            for (Billboard sprite : RailThunderArrayGrandGeometry.billboards(from, to, random.nextInt(6), random.nextLong(), age, random.nextBoolean(), random.nextBoolean(), tier))
                check(RailThunderArrayGrandGeometry.projectBillboard(sprite, CAMERA, look, UP, tier).size() <= 1, "at most one quad per sprite");
        }
    }

    private static Point unit(Point v) { double l = v.length(); return p(v.x() / l, v.y() / l, v.z() / l); }
}
