package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import static cn.academy.port.highpower.core.ScatterPlateGeometry.*;

public final class ScatterPlateGeometryRegressionTest {
    private static int checks;
    public static void main(String[] args) {
        Pose p = pose(v(0, 0, 0), v(0, 0, 1));
        check(p != null && p.right().dot(v(1, 0, 0)) == 1 && p.up().dot(v(0, 1, 0)) == 1, "orthonormal forward pose");
        for (int i = 0; i < 3; i++) {
            double x = (i - 1) * SPACING;
            var hit = crossing(p, v(x, 0, 3), v(x, 0, 0), 7).orElseThrow();
            check(hit.plateIndex() == i && Math.abs(hit.point().z() - 1.02) < 1e-12, "actual front face index/point");
            check(crossing(p, v(x, RADIUS_Y + 1e-6, 3), v(x, RADIUS_Y + 1e-6, 0), 7).isEmpty(), "outside vertical edge");
            check(crossing(p, v(x + RADIUS_X, 0, 3), v(x + RADIUS_X, 0, 0), 7).isPresent(), "exact lateral boundary");
            check(crossing(p, v(x, RADIUS_Y, 3), v(x, RADIUS_Y, 0), 7).isPresent(), "exact vertical boundary");
            check(crossing(p, v(x, 0, 3), v(x, 0, 0), 7 ^ (1 << i)).isEmpty(), "broken gap remains empty");
        }
        check(crossing(p, v(.4, 0, 3), v(.4, 0, 0), 7).isEmpty(), "real gap between disks");
        check(crossing(p, v(0, 0, -3), v(0, 0, 3), 7).isEmpty(), "rear never catches");
        check(crossing(p, v(0, 0, .9), v(0, 0, 0), 7).isEmpty(), "inside plate/body bypass");
        check(crossing(p, v(-3, 0, 1), v(3, 0, 1), 7).isEmpty(), "lateral slab crossing is not frontal coverage");
        check(crossing(p, v(0, 0, 3), v(0, 0, 1.02), 7).isEmpty(), "intersection strictly before segment endpoint");
        check(crossing(p, v(0, 0, 3), v(0, 0, 1.2), 7).isEmpty(), "wall or body before plate");
        check(crossing(p, v(.25, .4, 3), v(.25, .4, 0), 7).isEmpty(), "ellipse corners never use rectangular coverage");
        check(crossing(p, v(0, 0, 1.04), v(0, 0, 1), 7).isPresent(), "slow step through front face");
        check(crossing(p, v(Double.NaN, 0, 3), v(0, 0, 0), 7).isEmpty(), "nonfinite rejected");
        check(crossing(p, v(0, 0, 130), v(0, 0, 0), 7).isEmpty(), "bounded segment work");
        for (int pitch = -90; pitch <= 90; pitch++) {
            double a = Math.toRadians(pitch); Pose tilted = pose(v(5, 9, 4), v(0, Math.sin(a), Math.cos(a)));
            check(Math.abs(tilted.axis().dot(tilted.right())) < 1e-10 && Math.abs(tilted.axis().dot(tilted.up())) < 1e-10, "tilted basis perpendicular");
            var c = plateCenter(tilted, 1);
            check(crossing(tilted, c.add(tilted.axis().scale(2)), c.subtract(tilted.axis()), 7).orElseThrow().plateIndex() == 1, "all native look pitches supported");
        }
        for (int x = -120; x <= 120; x++) for (int y = -60; y <= 60; y++) {
            double xx = x / 100.0, yy = y / 100.0; boolean expected = false;
            for (int i = 0; i < 3; i++) { double dx = (xx - (i - 1) * SPACING) / RADIUS_X, dy = yy / RADIUS_Y; expected |= dx * dx + dy * dy <= 1 + 1e-12; }
            check(crossing(p, v(xx, yy, 3), v(xx, yy, 0), 7).isPresent() == expected, "full disk/gap grid");
        }
        double[] activation = {4, 2, 1, .3, .05}, sustain = {1, .5, .2, .05, .01}; int[] cooldown = {10, 8, 6, 4, 2};
        for (int q = 1; q <= 5; q++) {
            BigDecimal at = BigDecimal.valueOf(q);
            check(activationCost(at) == activation[q - 1] && sustainCost(at) == sustain[q - 1] && cooldownTicks(at) == cooldown[q - 1], "approved CP/cooldown anchors");
        }
        check(activationCost(new BigDecimal("1.5")) < 4 && activationCost(new BigDecimal("1.5")) > 2, "smooth effective growth interpolation");
        System.out.println("Scatter plate geometry: " + checks + " checks passed");
    }
    private static Vector v(double x, double y, double z) { return new Vector(x, y, z); }
    private static void check(boolean condition, String why) { checks++; if (!condition) throw new AssertionError(why); }
}
