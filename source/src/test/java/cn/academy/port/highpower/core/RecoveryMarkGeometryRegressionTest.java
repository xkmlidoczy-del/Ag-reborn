package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;

/** Native needle extents, exact radial fences, shallow exit proof, and strict later obstacles. */
public final class RecoveryMarkGeometryRegressionTest {
    private static int checks;
    private static void check(boolean valid, String why) {
        checks++; if (!valid) throw new AssertionError(why);
    }
    private static Box body(Point p) { return new Box(p.x() - .125, p.y(), p.z() - .125, p.x() + .125, p.y() + .25, p.z() + .125); }
    private static boolean exit(Point source, Point destination, Box wall) {
        return RecoveryMarkGeometry.shallowOutwardExit(body(source), source, destination, wall);
    }
    public static void main(String[] args) {
        double range = RecoveryMarkRules.RANGE;
        Point zero = new Point(0, 64, 0), atRange = new Point(range, 64, 0);
        check(RecoveryMarkGeometry.withinRange(zero, atRange), "exact axial boundary admits");
        check(!RecoveryMarkGeometry.withinRange(zero, new Point(Math.nextUp(range), 64, 0)), "one ulp beyond the boundary refuses");
        check(!RecoveryMarkGeometry.withinRange(zero, new Point(range, 64, Double.MIN_VALUE)), "subnormal orthogonal distance cannot round back to the boundary");
        check(RecoveryMarkGeometry.withinRange(zero, new Point(0, 64 + range, 0)), "vertical range consumes same budget");
        check(!RecoveryMarkGeometry.withinRange(zero, new Point(range * .9, 64 + range * .5, 0)), "three dimensional Euclidean fence");
        check(!RecoveryMarkGeometry.withinRange(null, zero) && !RecoveryMarkGeometry.withinRange(zero, new Point(Double.NaN, 64, 0)), "invalid points fail closed");
        check(!RecoveryMarkGeometry.withinRange(zero, new Point(Double.POSITIVE_INFINITY, 64, 0)), "infinity fails closed");
        check(RecoveryMarkGeometry.pairwiseRange(zero, new Point(-range / 2, 64, 0), new Point(range / 2, 64, 0)), "all three exact pairwise bounds");
        check(!RecoveryMarkGeometry.pairwiseRange(zero, new Point(-range, 64, 0), atRange), "two radial endpoints cannot double the relocation budget");
        check(!RecoveryMarkGeometry.pairwiseRange(atRange, new Point(range - 1, 64, 0), new Point(-1, 64, 0)), "current player must stay near frozen anchor");
        check(RecoveryMarkGeometry.needleBody(body(zero)) && !RecoveryMarkGeometry.needleBody(new Box(0, 0, 0, .5, .25, .25)), "native quarter-block body retained");
        Box borderTouch = new Box(0, 64, 0, .25, 64.25, .25);
        check(RecoveryMarkGeometry.insideBorder(borderTouch, 0, 0, .25, .25), "exact border face contact admits");
        check(!RecoveryMarkGeometry.insideBorder(borderTouch, 0, 0, Math.nextDown(.25), .25), "one-ulp max-face border penetration refuses");
        check(!RecoveryMarkGeometry.insideBorder(borderTouch, Double.MIN_VALUE, 0, .25, .25), "subnormal min-face border penetration refuses");
        check(!RecoveryMarkGeometry.insideBorder(borderTouch, 0, 0, .25, Double.NaN), "nonfinite border refuses");

        Box wall = new Box(0, 63, -1, 1, 66, 1);
        Point source = new Point(-.0625, 64.125, 0), back = new Point(-4, 64, 0);
        check(exit(source, back, wall), "shallow ordinary embedded arrow pulls outward and downward");
        check(!exit(source, new Point(4, 64, 0), wall), "same original block never grants traversal through its opposite face");
        check(!exit(source, new Point(-.0625, 64, 4), wall), "parallel travel cannot slide through the source wall");
        check(!exit(source, new Point(-.07, 64, 4), wall), "arbitrarily shallow retreat cannot mask long wall sliding");
        check(!exit(new Point(.25, 64.125, 0), back, wall), "body centered deep inside solid refuses");
        check(!exit(new Point(0, 64.125, 0), back, wall), "exact center-on-face ambiguity refuses");
        check(!exit(new Point(-.125, 64.125, 0), back, wall), "pure source tangency needs no exception");
        Box thin = new Box(0, 63, -1, Math.nextUp(0d), 66, 1);
        check(exit(source, back, thin), "ultrathin original box still supports only outward withdrawal");
        check(!exit(source, new Point(4, 64, 0), thin), "ultrathin source cannot be skipped inward");
        check(!exit(new Point(-.0625, 64.125, -.0625), new Point(-4, 64, -4), new Box(0, 63, 0, 1, 66, 1)), "corner with two outside faces is ambiguous");
        check(!exit(source, new Point(-4, 64, 1), new Box(0, 63, -.1, 1, 66, 1)), "growing tangential overlap refuses");
        check(!exit(source, source, wall), "stationary embedded source refuses");
        check(!exit(source, new Point(-.125, 64.125, 0), wall), "endpoint exactly at exit plane refuses conservative source exception");
        check(exit(source, new Point(Math.nextDown(-.125), 64.125, 0), wall), "next binary value beyond exit admits");

        var sweep = ReturnMarkGeometry.sweep(body(source), source, back);
        check(sweep.intersects(wall), "ordinary sweep detects original embedding before narrow waiver");
        check(sweep.intersects(new Box(-2, 64, -.2, -1.9, 64.5, .2)), "later adjacent wall remains blocking");
        check(sweep.intersects(new Box(-2, 64, Math.nextDown(.125), -1.9, 64.5, .3)), "one ulp shoulder intersection remains blocking");
        check(!sweep.intersects(new Box(-2, 64, .125, -1.9, 64.5, .3)), "exact shoulder tangency remains clear");
        check(sweep.intersects(new Box(-2, 64, -.2, Math.nextUp(-2d), 64.5, .2)), "thin later obstruction cannot be sampled away");
        check(!exit(source, back, new Box(-2, 64, -.2, -1.9, 64.5, .2)), "later obstacle can never receive a source exemption");
        // Native multipart/concave shapes are rejected by RecoveryMarkWorld before this
        // primitive; every remaining component would independently block the strict sweep.
        Box secondComponent = new Box(-.5, 64, -.2, -.4, 64.5, .2);
        check(sweep.intersects(secondComponent) && !exit(source, back, secondComponent), "second concave component cannot become a second exit waiver");
        Point borderSource = new Point(29_999_998.9375, 64.125, 0), borderBack = new Point(29_999_995, 64, 0);
        check(exit(borderSource, borderBack, new Box(29_999_999, 63, -1, 30_000_000, 66, 1)), "exact dyadic exit proof survives world-border scale");
        for (int i = 1; i <= 1000; i++) {
            double penetration = i / 8192d;
            Point s = new Point(-.125 + penetration, 64.125, 0);
            check(exit(s, back, wall), "outward positive shallow penetration " + i);
            check(!exit(s, new Point(4, 64, 0), wall), "inward positive shallow penetration " + i);
        }
        System.out.println("RecoveryMarkGeometryRegressionTest passed " + checks + " checks");
    }
}
