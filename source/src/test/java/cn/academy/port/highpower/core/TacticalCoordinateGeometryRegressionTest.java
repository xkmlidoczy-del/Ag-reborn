package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.Random;

/** Actual box extents, 3D strict-contact boundaries, bounded work and an independent SAT oracle. */
public final class TacticalCoordinateGeometryRegressionTest {
    private static int checks;
    private static void check(boolean result, String message) {
        checks++; if (!result) throw new AssertionError(message);
    }
    private static void rejects(Runnable action, String message) {
        boolean refused = false;
        try { action.run(); } catch (IllegalArgumentException expected) { refused = true; }
        check(refused, message);
    }
    private static Box body(Point p) {
        // Dyadic extents retain exact translation and tangency even at the world border.
        return new Box(p.x() - .25, p.y(), p.z() - .25, p.x() + .25, p.y() + 1.75, p.z() + .25);
    }
    private static Box move(Box b, double x, double y, double z) {
        return new Box(b.minX() + x, b.minY() + y, b.minZ() + z,
                b.maxX() + x, b.maxY() + y, b.maxZ() + z);
    }
    private static Point move(Point p, double x, double y, double z) {
        return new Point(p.x() + x, p.y() + y, p.z() + z);
    }
    private static boolean overlaps(Box a, Box b) {
        return a.maxX() > b.minX() && a.minX() < b.maxX()
                && a.maxY() > b.minY() && a.minY() < b.maxY()
                && a.maxZ() > b.minZ() && a.minZ() < b.maxZ();
    }

    public static void main(String[] args) {
        Point source = new Point(0, 64, 0), destination = new Point(64, 64, 0);
        var straight = TacticalCoordinateGeometry.sweep(body(source), source, destination);
        check(straight.destination().equals(destination), "frozen mark retained exactly at full range");
        check(straight.destinationBody().equals(move(body(source), 64, 0, 0)), "every body face translated");
        check(straight.bounds().equals(new Box(-.25, 64, -.25, 64.25, 65.75, .25)), "full swept broad phase");
        check(straight.intersects(new Box(12, 64.5, -.1, 12.1, 65, .1)), "middle wall blocks with clear endpoints");
        check(straight.intersects(new Box(12, 64.5, .24, 12.1, 65, .5)), "shoulder collision despite clear centerline");
        check(!straight.intersects(new Box(12, 64.5, .25, 12.1, 65, .5)), "shoulder-only tangent passes");
        check(straight.intersects(new Box(12, 64.5, Math.nextDown(.25), 12.1, 65, .5)), "one-ulp shoulder penetration blocks");
        check(!straight.intersects(new Box(12, 64.5, Math.nextUp(.25), 12.1, 65, .5)), "one-ulp outside shoulder passes");
        check(straight.intersects(new Box(12, Math.nextDown(65.75), -.1, 12.1, 66, .1)), "one-ulp roof penetration blocks");
        check(!straight.intersects(new Box(12, 65.75, -.1, 12.1, 66, .1)), "roof-only tangent passes");
        check(!straight.intersects(new Box(-1, 63, -1, 41, 64, 1)), "ordinary supporting floor is tangent");
        check(!straight.intersects(new Box(64.25, 64.5, -.1, 65, 65, .1)), "endpoint-only contact passes");
        check(straight.intersects(new Box(Math.nextDown(64.25), 64.5, -.1, 65, 65, .1)), "one-ulp endpoint penetration blocks");
        check(!straight.intersects(new Box(-1, 64.5, -.1, -.25, 65, .1)), "source-only tangent moving away passes");
        check(straight.intersects(new Box(12, 64.5, -.1, Math.nextUp(12d), 65, .1)), "thin native shape cannot be skipped");

        Box crouchedBody = new Box(-.25, 64, -.25, .25, 65.25, .25);
        var crouched = TacticalCoordinateGeometry.sweep(crouchedBody, source, destination);
        check(crouched.destinationBody().maxY() == 65.25, "pure kernel retains supplied body height");
        check(!crouched.intersects(new Box(12, 65.25, -.1, 12.1, 66, .1)), "native wrapper must supply and verify the full standing body");
        Point upper = new Point(8, 68, 8);
        var diagonal = TacticalCoordinateGeometry.sweep(body(source), source, upper);
        check(diagonal.destinationBody().equals(body(upper)), "different floor height translates the whole body");
        Box corner = new Box(.5, 64.5, 6, 1, 65, 7);
        check(overlaps(diagonal.bounds(), corner) && !diagonal.intersects(corner), "3D envelope corner is not swept volume");
        check(diagonal.intersects(new Box(3.9, 67, 3.9, 4.1, 68, 4.1)), "ascending route catches intermediate roof");
        check(diagonal.intersects(new Box(3.9, 66.1, 3.9, 4.1, 66.2, 4.1)), "ascending route catches intermediate floor obstruction");
        check(!diagonal.intersects(new Box(3.9, 64, 3.9, 4.1, 65, 4.1)), "cleared lower obstacle is not projected up");
        var descending = TacticalCoordinateGeometry.sweep(body(upper), upper, source);
        check(descending.intersects(new Box(3.9, 67, 3.9, 4.1, 68, 4.1)), "descending path also checks roof");
        check(!descending.intersects(corner), "reverse sweep does not fill diagonal corner");
        var vertical = TacticalCoordinateGeometry.sweep(body(source), source, new Point(0, 128, 0));
        check(vertical.intersects(new Box(-.1, 75, -.1, .1, 75.125, .1)), "pure vertical sweep intersects a thin roof");
        check(!vertical.intersects(new Box(.25, 75, -.1, .5, 75.125, .1)), "vertical wall tangency passes");

        // Isolated diagonal edge/corner contact has an empty strict overlap-time interval.
        Point diagonalEnd = new Point(8, 64, 8);
        var flatDiagonal = TacticalCoordinateGeometry.sweep(body(source), source, diagonalEnd);
        Box edgeTouch = new Box(1.25, 64.25, .5, 1.5, 65, .75);
        check(!flatDiagonal.intersects(edgeTouch), "isolated diagonal edge touch passes");
        check(flatDiagonal.intersects(new Box(1.25, 64.25, .5, 1.5, 65, Math.nextUp(.75))),
                "one-ulp penetration after diagonal edge contact blocks");
        Point cubeSource = new Point(0, 0, 0), cubeEnd = new Point(2, 2, 2);
        Box cube = new Box(0, 0, 0, .5, .5, .5);
        var cubeSweep = TacticalCoordinateGeometry.sweep(cube, cubeSource, cubeEnd);
        check(!cubeSweep.intersects(new Box(1.5, .5, 1.5, 2, 1, 2)), "three-dimensional isolated corner touch passes");
        check(cubeSweep.intersects(new Box(1.5, .5, 1.5, 2, Math.nextUp(1d), 2)),
                "three-dimensional corner penetration blocks");

        var stationary = TacticalCoordinateGeometry.sweep(body(source), source, source);
        check(stationary.destinationBody().equals(body(source)) && stationary.bounds().equals(body(source)),
                "zero translation is an unchanged stationary body query");
        check(stationary.intersects(new Box(-.1, 64.1, -.1, .1, 65, .1)), "stationary overlap blocks");
        check(!stationary.intersects(new Box(.25, 64.1, -.1, .5, 65, .1)), "stationary tangent passes");

        check(TacticalCoordinateGeometry.withinRange(source, destination), "exact 64-meter boundary admits");
        check(!TacticalCoordinateGeometry.withinRange(source, new Point(Math.nextUp(64d), 64, 0)), "next representable out-of-range endpoint refuses");
        check(TacticalCoordinateGeometry.withinRange(cubeSource, new Point(0, 0, 64)), "range includes full vertical/horizontal axes");
        check(!TacticalCoordinateGeometry.withinRange(cubeSource, new Point(52, 39, 0)), "Euclidean range includes height");
        check(!TacticalCoordinateGeometry.withinRange(null, destination)
                && !TacticalCoordinateGeometry.withinRange(source, new Point(Double.NaN, 64, 0)), "invalid endpoint rejects");
        rejects(() -> TacticalCoordinateGeometry.sweep(body(source), source, new Point(64.001, 64, 0)), "sweep enforces its own range");
        rejects(() -> TacticalCoordinateGeometry.sweep(body(source), source, new Point(0, Double.POSITIVE_INFINITY, 0)), "infinite endpoint refuses");
        rejects(() -> TacticalCoordinateGeometry.sweep(body(source), new Point(Double.NaN, 64, 0), destination), "nonfinite source refuses");
        rejects(() -> TacticalCoordinateGeometry.sweep(new Box(0, 64, 0, 0, 65, 1), source, destination), "zero-volume body refuses");
        rejects(() -> TacticalCoordinateGeometry.sweep(new Box(0, 64, 0, 5, 65, 1), source, destination), "oversized body refuses");
        rejects(() -> TacticalCoordinateGeometry.sweep(new Box(1, 64, 0, 0, 65, 1), source, destination), "inverted body refuses");
        rejects(() -> TacticalCoordinateGeometry.sweep(null, source, destination), "missing body refuses");
        rejects(() -> straight.intersects(new Box(0, 64, 0, Double.NaN, 65, 1)), "invalid obstacle refuses");
        rejects(() -> straight.intersects(new Box(0, 64, 0, 1, 64, 1)), "zero-volume obstacle refuses");
        rejects(() -> straight.intersects(null), "missing obstacle refuses");
        Point edge = new Point(30_000_015, 64, 0);
        rejects(() -> TacticalCoordinateGeometry.sweep(body(edge), edge, new Point(30_000_016, 64, 0)),
                "full destination body must remain in admitted coordinate bounds");
        rejects(() -> TacticalCoordinateGeometry.sweep(body(edge), edge, new Point(30_000_017, 64, 0)),
                "endpoint outside admitted coordinate bounds refuses");

        var cells = TacticalCoordinateGeometry.cells(-8, -8, -256, 7, 7, 255);
        check(cells.count() == 131072, "exact inclusive cell budget admits");
        rejects(() -> TacticalCoordinateGeometry.cells(-8, -8, -256, 8, 7, 255), "one over-budget dimension refuses");
        rejects(() -> TacticalCoordinateGeometry.cells(0, 0, 0, 131072, 0, 0), "one cell over budget refuses");
        rejects(() -> TacticalCoordinateGeometry.cells(Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 1, 1), "overflow-sized scan refuses");
        rejects(() -> TacticalCoordinateGeometry.cells(1, 0, 0, 0, 0, 0), "empty reversed scan refuses");
        Box largeBody = new Box(-2, 64, -2, 2, 68, 2);
        double component = Math.nextDown(64 / Math.sqrt(3));
        Point fullDiagonal = new Point(component, 64 + component, component);
        check(TacticalCoordinateGeometry.sweep(body(source), source, fullDiagonal).cells().count() <= TacticalCoordinateGeometry.MAX_CELLS,
                "full-range ordinary 3D diagonal fits dedicated bounded work");
        check(TacticalCoordinateGeometry.sweep(largeBody, source, fullDiagonal).cells().count() <= TacticalCoordinateGeometry.MAX_CELLS,
                "maximum admitted body also fits full-range 3D diagonal budget");
        // Every historical capability must retain its own range after the new wrapper is added.
        check(!SafeLandingProbeGeometry.withinRange(source, new Point(12.001, 64, 0)), "NEW313 retains12m");
        check(!ReturnMarkGeometry.withinRange(source, new Point(ReturnMarkRules.RANGE + .001, 64, 0)), "NEW322 keeps its independent configured distance");
        check(!CornerExchangeGeometry.withinRange(source, new Point(24.001, 64, 0)), "NEW333 retains24m");
        check(CrossSwapGeometry.withinRange(source, new Point(32, 64, 0)), "NEW321 admits32m");
        check(!CrossSwapGeometry.withinRange(source, new Point(Math.nextUp(32d), 64, 0)), "NEW321 retains32m");
        check(TacticalCoordinateGeometry.withinRange(source, new Point(63.99, 64, 0)), "NEW353 admits above every historical cap");
        // Native float dimensions require exact-contact behavior as well as dyadic fixtures.
        double halfWidth = (double)(.6F / 2), standingHeight = (double)1.8F;
        Box nativeBody = new Box(-halfWidth, 64, -halfWidth, halfWidth, 64 + standingHeight, halfWidth);
        var nativeSweep = TacticalCoordinateGeometry.sweep(nativeBody, source, destination);
        check(!nativeSweep.intersects(new Box(32, 64.1, halfWidth, 33, 65, 1)), "native shoulder tangent passes beyond32m");
        check(nativeSweep.intersects(new Box(32, 64.1, Math.nextDown(halfWidth), 33, 65, 1)), "native shoulder one-ulp penetration blocks beyond32m");
        check(!nativeSweep.intersects(new Box(32, 64 + standingHeight, -.1, 33, 67, .1)), "native head tangent passes beyond32m");
        check(nativeSweep.intersects(new Box(32, Math.nextDown(64 + standingHeight), -.1, 33, 67, .1)), "native head one-ulp penetration blocks beyond32m");
        check(stationary.cells().minX() == -2 && stationary.cells().maxX() == 1
                && stationary.cells().minY() == 62 && stationary.cells().maxY() == 66,
                "native-shape apron uses closed floored negative/whole-number edges");

        // Shift exactly representable complete fixtures, not just endpoints, near both world borders.
        Box[] fixtures = {corner, edgeTouch, new Box(3.875, 67, 3.875, 4.125, 68, 4.125),
                new Box(3.875, 64, 3.875, 4.125, 65, 4.125), new Box(7.875, 68, 7.875, 8.125, 69, 8.125)};
        for (double shift : new double[] {-29_999_984, 29_999_984}) {
            Point farSource = move(source, shift, 0, shift), farDestination = move(upper, shift, 0, shift);
            var farSweep = TacticalCoordinateGeometry.sweep(move(body(source), shift, 0, shift), farSource, farDestination);
            check(farSweep.destination().equals(farDestination), "world-border destination remains frozen");
            check(farSweep.destinationBody().equals(move(diagonal.destinationBody(), shift, 0, shift)), "world-border full-body translation invariant");
            check(farSweep.cells().count() == diagonal.cells().count(), "integer translation retains broad-phase work");
            for (Box fixture : fixtures)
                check(farSweep.intersects(move(fixture, shift, 0, shift)) == diagonal.intersects(fixture),
                        "world-border collision invariant under exact source translation");
            var farFlat = TacticalCoordinateGeometry.sweep(body(farSource), farSource, move(diagonalEnd, shift, 0, shift));
            check(!farFlat.intersects(move(edgeTouch, shift, 0, shift)), "world-border exact diagonal tangency retained");
        }

        check(!EmergencyReturnGeometry.withinRange(source, new Point(40.001, 64, 0)), "NEW342 retains40m");
        // Double squared distance rounds this just-outside diagonal to4096; exact range must refuse.
        check(!TacticalCoordinateGeometry.withinRange(new Point(0,0,0), new Point(64, Math.scalb(1d,-28), 0)),
                "sub-ulp squared-distance excess cannot widen64m");
        Random random = new Random(353);
        for (int i = 0; i < 8000; i++) {
            // Dyadic fixtures make reverse-path and translated-body equality exact.
            Point to = new Point((random.nextInt(297) - 148) / 4d, 64 + (random.nextInt(297) - 148) / 4d,
                    (random.nextInt(297) - 148) / 4d);
            if (!TacticalCoordinateGeometry.withinRange(source, to)) { i--; continue; }
            var sweep = TacticalCoordinateGeometry.sweep(body(source), source, to);
            double x = (random.nextInt(329) - 164) / 4d, y = 36 + random.nextInt(225) / 4d,
                    z = (random.nextInt(329) - 164) / 4d;
            Box obstacle = new Box(x, y, z, x + .125 + random.nextInt(13) / 4d,
                    y + .125 + random.nextInt(13) / 4d, z + .125 + random.nextInt(13) / 4d);
            boolean hit = sweep.intersects(obstacle);
            check(hit == sat(body(source), source, to, obstacle), "3D analytic interval agrees with independent convex SAT oracle");
            check(hit == TacticalCoordinateGeometry.sweep(sweep.destinationBody(), to, source).intersects(obstacle),
                    "reversing identical physical path preserves collisions");
            check(sweep.cells().count() <= TacticalCoordinateGeometry.MAX_CELLS, "every admitted scan retains bounded work");
            if (hit) check(overlaps(sweep.bounds(), obstacle), "narrow phase remains contained by broad phase");
        }
        System.out.println("TacticalCoordinateGeometryRegressionTest passed " + checks + " checks");
    }

    /** A box swept along a segment is a zonotope with coordinate and segment-cross-edge SAT axes. */
    private static boolean sat(Box body, Point source, Point destination, Box obstacle) {
        double dx = destination.x() - source.x(), dy = destination.y() - source.y(), dz = destination.z() - source.z();
        double[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {0, dz, -dy}, {-dz, 0, dx}, {dy, -dx, 0}};
        for (double[] axis : axes) {
            if (axis[0] == 0 && axis[1] == 0 && axis[2] == 0) continue;
            double sweptMin = Double.POSITIVE_INFINITY, sweptMax = Double.NEGATIVE_INFINITY,
                    solidMin = Double.POSITIVE_INFINITY, solidMax = Double.NEGATIVE_INFINITY;
            for (double x : new double[] {body.minX(), body.maxX()})
                for (double y : new double[] {body.minY(), body.maxY()})
                    for (double z : new double[] {body.minZ(), body.maxZ()}) {
                        double projection = (x - source.x()) * axis[0] + (y - source.y()) * axis[1] + (z - source.z()) * axis[2];
                        double moved = projection + dx * axis[0] + dy * axis[1] + dz * axis[2];
                        sweptMin = Math.min(sweptMin, Math.min(projection, moved));
                        sweptMax = Math.max(sweptMax, Math.max(projection, moved));
                    }
            for (double x : new double[] {obstacle.minX(), obstacle.maxX()})
                for (double y : new double[] {obstacle.minY(), obstacle.maxY()})
                    for (double z : new double[] {obstacle.minZ(), obstacle.maxZ()}) {
                        double projection = (x - source.x()) * axis[0] + (y - source.y()) * axis[1] + (z - source.z()) * axis[2];
                        solidMin = Math.min(solidMin, projection); solidMax = Math.max(solidMax, projection);
                    }
            if (sweptMax <= solidMin || solidMax <= sweptMin) return false;
        }
        return true;
    }
}
