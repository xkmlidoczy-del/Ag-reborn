package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import static cn.academy.port.highpower.core.DissipationCurtainGeometry.*;

public final class DissipationCurtainGeometryRegressionTest {
    private static int checks;
    public static void main(String[] args) {
        Pose p = pose(v(0, 0, 0), v(0, 0, 1));
        check(p != null && p.right().dot(v(1, 0, 0)) == 1 && p.up().dot(v(0, 1, 0)) == 1, "server forward basis");
        check(STARTUP_TICKS == 6 && RADIUS == 6 && HEIGHT == 3 && THICKNESS == .04, "approved dimensions and startup");
        for (int i = 0; i < 3; i++) {
            Vector center = facetCenter(p, i), normal = facetNormal(p, i), right = facetRight(p, i);
            var hit = crossing(p, center.add(normal.scale(2)), center.subtract(normal), 7).orElseThrow();
            check(hit.plateIndex() == i && hit.point().subtract(center).length() < 1e-12, "actual plane/facet point");
            check(crossing(p, center.add(normal.scale(2)), center.subtract(normal), 7 ^ (1 << i)).isEmpty(), "disabled facet is a real opening");
            check(crossing(p, center.subtract(normal.scale(.01)), center.subtract(normal), 7).isEmpty(), "inside slab does not re-admit");
            check(crossing(p, center.subtract(normal), center.add(normal.scale(2)), 7).isEmpty(), "outward motion bypasses");
            check(crossing(p, center.add(normal.scale(2)), center, 7).isEmpty(), "endpoint-only crossing bypasses");
            check(crossing(p, center.add(normal.scale(2)), center.add(normal.scale(.01)), 7).isEmpty(), "short clipped path bypasses");
            for (Vector corner : vertices(p, i)) {
                Vector radial = corner.subtract(p.up().scale(corner.dot(p.up())));
                check(Math.abs(radial.length() - 6) < 1e-12, "chord vertices stay exactly radius six");
                check(Math.abs(corner.dot(p.up())) == 1.5, "corners retain three-metre height");
                check(contains(p, i, corner), "finite face contains exact corner");
                check(crossing(p, corner.add(normal), corner.subtract(normal), 1 << i).isPresent(), "corner has rectangular coverage");
            }
            Vector top = center.add(p.up().scale(1.50001)), side = center.add(right.scale(HALF_WIDTH + .00001));
            check(crossing(p, top.add(normal), top.subtract(normal), 1 << i).isEmpty(), "vertical excess bypasses");
            check(crossing(p, side.add(normal), side.subtract(normal), 1 << i).isEmpty(), "horizontal excess bypasses");
            check(contains(p, i, center.subtract(normal.scale(.04))), "inward back plane contained");
            check(!contains(p, i, center.add(normal.scale(.00001))), "no thickness protrusion outside front plane");
        }
        // Rays from an angular cone still fail if they travel through a real four-degree gap.
        for (int tenth = -1800; tenth <= 1800; tenth++) {
            double degrees = tenth / 10.0, angle = Math.toRadians(degrees);
            Vector outward = v(Math.sin(angle), 0, Math.cos(angle));
            boolean expected = degrees >= -58 && degrees <= -22 || degrees >= -18 && degrees <= 18 || degrees >= 22 && degrees <= 58;
            check(crossing(p, outward.scale(8), v(0, 0, 0), 7).isPresent() == expected, "full 360-degree finite chord/gap sweep at " + degrees);
        }
        for (int pitch = -90; pitch <= 90; pitch += 3) for (int yaw = 0; yaw < 360; yaw += 15) {
            double a = Math.toRadians(pitch), b = Math.toRadians(yaw);
            Pose tilted = pose(v(5, 9, 4), v(Math.cos(a) * Math.sin(b), Math.sin(a), Math.cos(a) * Math.cos(b)));
            check(Math.abs(tilted.axis().dot(tilted.right())) < 1e-10 && Math.abs(tilted.axis().dot(tilted.up())) < 1e-10, "all-look orthonormal basis");
            for (int i = 0; i < 3; i++) {
                Vector center = facetCenter(tilted, i), normal = facetNormal(tilted, i);
                check(crossing(tilted, center.add(normal), center.subtract(normal), 7).orElseThrow().plateIndex() == i, "server yaw/pitch rotates same finite rectangle");
            }
        }
        check(crossing(p, v(0, 0, 4), v(0, 0, 0), 7).isEmpty(), "attack begins behind curtain");
        check(crossing(p, v(0, 0, -8), v(0, 0, 0), 7).isEmpty(), "rear starts bypass");
        check(crossing(p, v(-8, 0, CENTER_DISTANCE), v(8, 0, CENTER_DISTANCE), 2).isEmpty(), "parallel plane motion bypasses");
        check(crossing(p, v(0, 0, 130), v(0, 0, 0), 7).isEmpty(), "bounded segment length");
        check(crossing(p, v(Double.NaN, 0, 8), v(0, 0, 0), 7).isEmpty(), "nonfinite rejected");
        check(pose(v(0, 0, 0), v(0, 0, 0)) == null, "degenerate look rejected");
        double[] activation = {8, 4, 2, .5, .05}; int[] cooldown = {60, 30, 16, 10, 1};
        for (int q = 1; q <= 5; q++) {
            BigDecimal at = BigDecimal.valueOf(q);
            check(activationCost(at) == activation[q - 1] && sustainCost(at) == 1 && cooldownTicks(at) == cooldown[q - 1], "approved continuous anchors");
        }
        check(activationCost(new BigDecimal("4.5")) < .5 && activationCost(new BigDecimal("4.5")) > .05, "fractional Q changes activation continuously");
        check(cooldownTicks(new BigDecimal("4.5")) < 10 && cooldownTicks(new BigDecimal("4.5")) > 1, "fractional Q changes rounded cooldown");
        System.out.println("Dissipation curtain geometry: " + checks + " checks passed");
    }
    private static Vector v(double x, double y, double z) { return new Vector(x, y, z); }
    private static void check(boolean condition, String why) { checks++; if (!condition) throw new AssertionError(why); }
}
