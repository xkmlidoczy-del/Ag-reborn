package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Analytic trajectory boundaries, native-box-shaped obstacles and a separate convex SAT oracle. */
public final class PhaseSidestepGeometryRegressionTest {
    private static int checks;
    private static void check(boolean result, String message) {
        checks++; if (!result) throw new AssertionError(message);
    }
    private static void rejects(Runnable action, String message) {
        boolean refused = false;
        try { action.run(); } catch (IllegalArgumentException expected) { refused = true; }
        check(refused, message);
    }
    private static boolean near(double a, double b) { return Math.abs(a - b) <= 1e-8; }
    private static Box body(Point p) {
        return new Box(p.x() - .3, p.y(), p.z() - .3, p.x() + .3, p.y() + 1.8, p.z() + .3);
    }
    public static void main(String[] args) throws Exception {
        Point source = new Point(0, 64, 0);
        double[][] cardinals = {{0, -3, 0}, {90, 0, -3}, {180, 3, 0}, {270, 0, 3},
                {-90, 0, 3}, {-180, 3, 0}, {360, -3, 0}, {450, 0, -3}};
        for (double[] cardinal : cardinals) for (boolean left : new boolean[] {false, true}) {
            Point p = PhaseSidestepGeometry.destination(source, cardinal[0], left);
            double sign = left ? -1 : 1;
            check(p.x() == cardinal[1] * sign && p.z() == cardinal[2] * sign && p.y() == 64,
                    "cardinal selected side is exact, including fixed zero axis");
        }
        for (int yaw = -720; yaw <= 720; yaw++) {
            Point right = PhaseSidestepGeometry.destination(source, yaw + .125, false);
            Point left = PhaseSidestepGeometry.destination(source, yaw + .125, true);
            check(near(source.distanceSquared(right), 9) && near(source.distanceSquared(left), 9), "exact three-meter diagonal magnitude");
            check(near(right.x(), -left.x()) && near(right.z(), -left.z()) && right.y() == 64 && left.y() == 64,
                    "side is opposite and no vertical displacement is introduced");
        }
        // A non-cardinal yaw has a fixed horizontal endpoint. Native +/-90 pitch acceptance
        // is covered in runtime tests; pitch and look vectors are not inputs to this API.
        Point endpoint37 = PhaseSidestepGeometry.destination(source, 37, false);
        check(near(endpoint37.x(), -2.3959065301418785) && near(endpoint37.z(), -1.8054450694561448)
                && endpoint37.y() == 64, "non-cardinal server yaw determines the full horizontal endpoint");
        var right = PhaseSidestepGeometry.sweep(body(source), source, 0, false);
        check(right.intersects(new Box(-1.6, 64.2, -.1, -1.4, 65, .1)), "middle wall blocks even when destination is clear");
        check(right.intersects(new Box(-1.6, 64.2, .29, -1.4, 65, .6)), "shoulder blocks despite clear center line");
        check(!right.intersects(new Box(-1.6, 64.2, .3, -1.4, 65, .6)), "exact shoulder tangent is clear");
        check(!right.intersects(new Box(-1.6, 64.2, Math.nextUp(.3), -1.4, 65, .6)), "one-ulp outside shoulder is clear");
        check(right.intersects(new Box(-1.6, 64.2, Math.nextDown(.3), -1.4, 65, .6)), "one-ulp inside shoulder blocks");
        check(right.intersects(new Box(-1.6, 65.799, -.1, -1.4, 66, .1)), "ceiling catches full current body height");
        check(!right.intersects(new Box(-1.6, 65.8, -.1, -1.4, 66, .1)), "exact ceiling tangent is clear");
        check(!right.intersects(new Box(-2, 63, -.5, -1, 64, .5)), "same-height floor is tangent, not a corridor collision");
        check(!right.intersects(new Box(-4, 64, -.1, -3.3, 65, .1)), "endpoint-only contact is not volume overlap");
        check(right.intersects(new Box(-4, 64, -.1, Math.nextUp(-3.3), 65, .1)), "one-ulp endpoint penetration blocks");
        check(right.intersects(new Box(-1.500000001, 64.5, -.1, -1.5, 65, .1)), "arbitrarily thin native shape is not skipped by stepping");
        check(!right.intersects(new Box(.4, 64, -.1, 1, 65, .1)), "selected right side never checks a reverse route");
        var crouched = PhaseSidestepGeometry.sweep(new Box(-.3, 64, -.3, .3, 65.5, .3), source, 0, false);
        check(!crouched.intersects(new Box(-1.6, 65.5, -.1, -1.4, 66, .1)), "actual crouched height is retained");
        var diagonal = PhaseSidestepGeometry.sweep(body(source), source, -135, false);
        Box corner = new Box(-.1, 64.2, 1.8, .1, 65, 2);
        check(overlaps(diagonal.bounds(), corner) && !diagonal.intersects(corner), "broad-phase corner is not filled swept space");
        check(diagonal.intersects(new Box(.9, 64.2, 1, 1.1, 65, 1.2)), "diagonal swept body blocks an actual native box");
        // Exactly representable geometry makes diagonal corner tangency a strict interval boundary.
        Point far = new Point(1_000_000, 64, 1_000_000);
        Box farBody = new Box(far.x() - .25, 64, far.z() - .25, far.x() + .25, 65.8, far.z() + .25);
        var tangent = PhaseSidestepGeometry.sweep(farBody, far, -135, false);
        check(!tangent.intersects(new Box(far.x() + 1.25, 64, far.z() + .5, far.x() + 1.5, 65, far.z() + .75)),
                "diagonal isolated corner touch is clear");
        check(tangent.intersects(new Box(far.x() + 1.25, 64, far.z() + .5, far.x() + 1.5, 65, Math.nextUp(far.z() + .75))),
                "one-ulp diagonal corner penetration blocks");
        for (double coordinate : new double[] {-29_999_900, 29_999_900}) for (double yaw : new double[] {0, 45, 90, -135}) {
            Point large = new Point(coordinate, 64, coordinate);
            var sweep = PhaseSidestepGeometry.sweep(body(large), large, yaw, false);
            check(Math.abs(large.distanceSquared(sweep.destination()) - 9) < 5e-8, "large-coordinate exact move within binary representation");
            double x = (large.x() + sweep.destination().x()) / 2;
            double z = (large.z() + sweep.destination().z()) / 2;
            check(sweep.intersects(new Box(x - .01, 64.1, z - .01, x + .01, 65, z + .01)), "large-coordinate middle obstacle blocks");
            check(sweep.cells().count() <= SafeLandingProbeGeometry.MAX_CELLS, "large-coordinate scan remains bounded");
        }
        rejects(() -> PhaseSidestepGeometry.destination(source, Double.NaN, false), "nonfinite yaw refuses");
        rejects(() -> PhaseSidestepGeometry.destination(new Point(Double.POSITIVE_INFINITY, 64, 0), 0, false), "nonfinite source refuses");
        rejects(() -> PhaseSidestepGeometry.destination(new Point(30_000_015, 64, 0), 0, true), "destination beyond coordinate bounds refuses");
        rejects(() -> PhaseSidestepGeometry.sweep(new Box(0, 64, 0, 5, 66, 1), source, 0, false), "oversized body refuses");
        rejects(() -> right.intersects(new Box(0, 64, 0, Double.NaN, 65, 1)), "invalid native box refuses");

        Random random = new Random(312);
        for (int i = 0; i < 5000; i++) {
            double yaw = random.nextDouble() * 360;
            boolean left = random.nextBoolean();
            var sweep = PhaseSidestepGeometry.sweep(body(source), source, yaw, left);
            double x = random.nextDouble() * 8 - 4, z = random.nextDouble() * 8 - 4;
            Box obstacle = new Box(x, 63.5 + random.nextDouble() * 2.5, z,
                    x + .01 + random.nextDouble(), 67, z + .01 + random.nextDouble());
            check(sweep.intersects(obstacle) == sat(body(source), source, sweep.destination(), obstacle),
                    "analytic time interval agrees with independent convex SAT oracle");
        }
        String nativeSource = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/SafeLandingProbeWorld.java"));
        check(nativeSource.contains("private PhaseCandidate(ServerPlayer player, boolean left)"), "capture has no public arbitrary endpoint constructor");
        check(nativeSource.contains("source_position_changed") && nativeSource.contains("source_yaw_changed"), "source and yaw freeze are explicit");
        check(nativeSource.contains("local.forAllBoxes") && nativeSource.contains("sweep.intersects(nativeBox)"), "native collision uses actual shape boxes");
        check(nativeSource.contains("sweep.intersects(box(new AABB(pos))) && !view.getFluidState(pos).isEmpty()"),
                "fluid policy uses actual swept cells, including guarded load and permission access");
        check(!nativeSource.contains("player.move(") && !nativeSource.contains("collideBoundingBox(")
                && !nativeSource.contains("maxUpStep("), "validation cannot slide, step or apply native movement");
        System.out.println("PhaseSidestepGeometryRegressionTest passed " + checks + " checks");
    }

    private static boolean overlaps(Box a, Box b) {
        return a.maxX() > b.minX() && a.minX() < b.maxX() && a.maxY() > b.minY() && a.minY() < b.maxY()
                && a.maxZ() > b.minZ() && a.minZ() < b.maxZ();
    }
    /** Minkowski sum of the source rectangle and segment has x/z and segment-normal SAT axes. */
    private static boolean sat(Box body, Point source, Point destination, Box obstacle) {
        if (body.maxY() <= obstacle.minY() || body.minY() >= obstacle.maxY()) return false;
        double dx = destination.x() - source.x(), dz = destination.z() - source.z();
        for (double[] axis : new double[][] {{1, 0}, {0, 1}, {dz, -dx}}) {
            double sweptMin = Double.POSITIVE_INFINITY, sweptMax = Double.NEGATIVE_INFINITY;
            double solidMin = Double.POSITIVE_INFINITY, solidMax = Double.NEGATIVE_INFINITY;
            for (double x : new double[] {body.minX(), body.maxX()}) for (double z : new double[] {body.minZ(), body.maxZ()}) {
                double projection = x * axis[0] + z * axis[1];
                double moved = (x + dx) * axis[0] + (z + dz) * axis[1];
                sweptMin = Math.min(sweptMin, Math.min(projection, moved));
                sweptMax = Math.max(sweptMax, Math.max(projection, moved));
            }
            for (double x : new double[] {obstacle.minX(), obstacle.maxX()}) for (double z : new double[] {obstacle.minZ(), obstacle.maxZ()}) {
                double projection = x * axis[0] + z * axis[1];
                solidMin = Math.min(solidMin, projection); solidMax = Math.max(solidMax, projection);
            }
            if (sweptMax <= solidMin || solidMax <= sweptMin) return false;
        }
        return true;
    }
}
