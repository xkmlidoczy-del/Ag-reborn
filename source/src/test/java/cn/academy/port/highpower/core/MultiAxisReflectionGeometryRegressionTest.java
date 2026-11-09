package cn.academy.port.highpower.core;

import java.util.Random;

import static cn.academy.port.highpower.core.MultiAxisReflectionGeometry.*;

/** CPU-only true-segment, open-gap, exact-boundary and translation checks. */
public final class MultiAxisReflectionGeometryRegressionTest {
    private static int checks;

    public static void main(String[] args) {
        dimensionsAndFacing();
        boundariesAndRange();
        angularGaps();
        translatedCrossings();
        nearestCrossing();
        reverseVelocity();
        System.out.println("MultiAxisReflectionGeometryRegressionTest passed " + checks + " checks");
    }

    private static void dimensionsAndFacing() {
        Pose pose = pose(v(0, 0, 0), v(0, 100, 1));
        check(FACET_COUNT == 4 && RADIUS == 1.25 && WIDTH == .85 && HEIGHT == 2.6, "approved physical dimensions");
        check(pose.axis().equals(v(0, 0, 1)) && pose.up().equals(v(0, 1, 0)), "pitch cannot tilt the upright faces");
        for (int index = 0; index < FACET_COUNT; index++) {
            near(Math.toDegrees(facetAngle(index)), -60 + 40 * index, 1e-13, "fixed yaw offsets and stable facet ordering");
            Vector center = facetCenter(pose, index), normal = facetNormal(pose, index);
            near(center.length(), RADIUS, 5e-16, "facet center sits at radius 1.25");
            var corners = vertices(pose, index);
            near(corners.get(0).subtract(corners.get(1)).length(), WIDTH, 5e-16, "finite width remains .85m");
            near(corners.get(1).subtract(corners.get(2)).length(), HEIGHT, 5e-16, "finite height remains 2.6m");
            check(corners.stream().allMatch(point -> Math.abs(point.y()) == HEIGHT / 2), "all facets share vertical up");
            Contact hit = crossing(pose, center.add(normal), center.subtract(normal), 1 << index).orElseThrow();
            check(hit.facetIndex() == index, "center crossing hits its own finite facet");
            near(hit.fraction(), .5, 5e-16, "crossing fraction remains on the observed segment");
            near(hit.point().subtract(center).length(), 0, 5e-16, "world hit point lies on its face");
            check(crossing(pose, center.subtract(normal), center.add(normal), 1 << index).isEmpty(), "back-to-front exit is not inbound");
            check(crossing(pose, center.add(normal), center.add(normal.scale(.1)), 1 << index).isEmpty(), "short segment cannot invent a continuation");
            check(crossing(pose, center.add(normal), center.subtract(normal), 15 ^ (1 << index)).isEmpty(), "disabled facet is a real opening");
        }
        check(pose(v(0, 0, 0), v(0, 1, 0)) == null && pose(v(0, 0, 0), v(0, 0, 0)) == null,
                "vertical or zero facing does not fabricate a yaw");
        check(pose(v(0, 0, 0), v(Double.MIN_VALUE, 0, Double.MIN_VALUE)) != null,
                "finite subnormal horizontal direction normalizes safely");
        check(pose(v(0, 0, 0), v(Double.MAX_VALUE, 0, Double.MAX_VALUE)) != null,
                "finite huge horizontal direction normalizes safely");
        for (Vector invalid : new Vector[] {null, v(Double.NaN, 0, 0), v(0, Double.POSITIVE_INFINITY, 0), v(0, 0, Double.NEGATIVE_INFINITY)}) {
            check(pose(invalid, v(0, 0, 1)) == null && pose(v(0, 0, 0), invalid) == null, "invalid pose refuses");
            check(crossing(pose, invalid, v(0, 0, 0)).isEmpty() && crossing(pose, v(0, 0, 0), invalid).isEmpty(), "invalid endpoint refuses");
        }
    }

    private static void boundariesAndRange() {
        // Align facet 1 exactly with +Z so nextUp is a geometric excess, not a trigonometric artifact.
        double angle = Math.toRadians(20);
        Pose aligned = pose(v(0, 0, 0), v(Math.nextDown(Math.sin(angle)), 0, Math.cos(angle)));
        check(facetNormal(aligned, 1).equals(v(0, 0, 1)), "boundary oracle has an exact cardinal face");
        for (double side : new double[] {-1, 1}) {
            double boundary = side * HALF_WIDTH;
            check(crossing(aligned, v(boundary, 0, 2), v(boundary, 0, 1), 2).isPresent(), "inclusive exact horizontal edge");
            double inside = side * Math.nextDown(HALF_WIDTH), outside = side * Math.nextUp(HALF_WIDTH);
            check(crossing(aligned, v(inside, 0, 2), v(inside, 0, 1), 2).isPresent(), "one-ULP horizontal inward point accepts");
            check(crossing(aligned, v(outside, 0, 2), v(outside, 0, 1), 2).isEmpty(), "one-ULP horizontal excess cannot use an epsilon");
            boundary = side * HEIGHT / 2;
            check(crossing(aligned, v(0, boundary, 2), v(0, boundary, 1), 2).isPresent(), "inclusive exact vertical edge");
            outside = side * Math.nextUp(HEIGHT / 2);
            check(crossing(aligned, v(0, outside, 2), v(0, outside, 1), 2).isEmpty(), "one-ULP vertical excess rejects");
        }
        check(crossing(aligned, v(HALF_WIDTH, HEIGHT / 2, 2), v(HALF_WIDTH, HEIGHT / 2, 1), 2).isPresent(), "inclusive corner");
        check(crossing(aligned, v(0, 0, 2), v(0, 0, RADIUS), 2).isEmpty(), "endpoint-only touch does not cross");
        check(crossing(aligned, v(0, 0, RADIUS), v(0, 0, 1), 2).isEmpty(), "starting on the face does not re-admit");
        check(crossing(aligned, v(-1, 0, RADIUS), v(1, 0, RADIUS), 2).isEmpty(), "coplanar segment does not cross");
        check(crossing(aligned, v(0, 0, Math.nextUp(RADIUS)), v(0, 0, Math.nextDown(RADIUS)), 2).isPresent(),
                "one-ULP strict crossing is not suppressed by an epsilon");
        check(crossing(aligned, v(0, 0, 6), v(0, 0, -2), 2).isPresent(), "actual crossing can use an 8m segment with source beyond owner range");
        check(crossing(aligned, v(0, 0, Math.nextUp(6.0)), v(0, 0, -2), 2).isEmpty(), "one-ULP segment over 8m refuses");
        check(crossing(aligned, v(0, 0, 2), v(0, 0, 2), 2).isEmpty(), "zero motion refuses");
        check(crossing(aligned, v(0, 0, 2), v(0, 0, 1), 0).isEmpty(), "zero live mask never reflects");
        check(crossing(aligned, v(0, 0, 2), v(0, 0, 1), 16).isEmpty(), "unknown mask bits refuse");
        for (Vector boundary : new Vector[] {v(5, 0, 0), v(0, -5, 0), v(0, 0, 5), v(3, 4, 0)})
            check(withinOwnerRange(v(0, 0, 0), boundary), "inclusive exact owner hard range");
        check(!withinOwnerRange(v(0, 0, 0), v(Math.nextUp(5.0), 0, 0)), "one-ULP range excess rejects");
        check(!withinOwnerRange(v(0, 0, 0), v(3, Math.nextUp(4.0), 0)), "diagonal range excess rejects exactly");
        Vector translated = v(29_999_999, 100, -29_999_999);
        check(withinOwnerRange(translated, translated.add(v(3, 4, 0))), "large translation preserves exact 3-4-5 range");
        check(!withinOwnerRange(translated, v(translated.x() + 3, Math.nextUp(translated.y() + 4), translated.z())),
                "large translation cannot widen range");
        check(!withinOwnerRange(v(-Double.MAX_VALUE, 0, 0), v(Double.MAX_VALUE, 0, 0)), "overflow-sized displacement refuses");
        Pose rotated = pose(v(0, 0, 0), v(.7498489428078781, 0, -.6616090711061235));
        Vector edgeStart = v(-.02902110414754963, 0, -2.289603191715555);
        Vector edgeEnd = v(.36707047894882805, 0, -.3292176536643775);
        check(crossing(rotated, edgeStart, edgeEnd, 8).isEmpty(),
                "represented normal length above one must not widen a rotated face by an ULP");
        check(crossing(rotated, v(Math.nextDown(edgeStart.x()), 0, edgeStart.z()),
                v(Math.nextDown(edgeEnd.x()), 0, edgeEnd.z()), 8).isPresent(),
                "adjacent inward rotated boundary remains accepted");
    }

    private static void angularGaps() {
        Pose pose = pose(v(0, 0, 0), v(0, 0, 1));
        double halfSpan = Math.toDegrees(Math.atan(HALF_WIDTH / RADIUS));
        for (int tenth = -1800; tenth <= 1800; tenth++) {
            double degrees = tenth / 10.0, radians = Math.toRadians(degrees);
            Vector source = v(Math.sin(radians) * 4, 0, Math.cos(radians) * 4);
            boolean expected = false;
            for (int index = 0; index < 4; index++) expected |= Math.abs(degrees - (-60 + index * 40)) <= halfSpan;
            check(crossing(pose, source, v(0, 0, 0)).isPresent() == expected, "full radial finite-facet/gap sweep " + degrees);
        }
        for (double yaw : new double[] {-40, 0, 40, -90, 90, 180}) {
            double radians = Math.toRadians(yaw);
            check(crossing(pose, v(Math.sin(radians) * 4, 0, Math.cos(radians) * 4), v(0, 0, 0)).isEmpty(), "real gaps and rear stay open");
        }
        check(crossing(pose, v(0, 4, 0), v(0, -4, 0)).isEmpty(), "top-to-bottom ray has no invented horizontal face");
    }

    private static void translatedCrossings() {
        Random random = new Random(44271);
        for (int i = 0; i < 100; i++) {
            double yaw = random.nextDouble() * Math.PI * 2;
            Vector facing = v(Math.sin(yaw), random.nextDouble() * 100, Math.cos(yaw));
            Vector origin = v(29_999_000 + random.nextDouble() * 100, random.nextDouble() * 500,
                    -29_999_000 + random.nextDouble() * 100);
            Pose pose = pose(origin, facing);
            for (int index = 0; index < 4; index++) {
                Vector center = facetCenter(pose, index), normal = facetNormal(pose, index), right = facetRight(pose, index);
                check(crossing(pose, center.add(normal), center.subtract(normal), 1 << index).isPresent(), "translated center crossing accepts");
                Vector inside = center.add(right.scale(HALF_WIDTH - 1e-6));
                Vector outside = center.add(right.scale(HALF_WIDTH + 1e-6));
                check(crossing(pose, inside.add(normal), inside.subtract(normal), 1 << index).isPresent(), "translated inward horizontal edge accepts");
                check(crossing(pose, outside.add(normal), outside.subtract(normal), 1 << index).isEmpty(), "translated outward horizontal edge refuses");
                Vector high = center.add(v(0, HEIGHT / 2 + 1e-6, 0));
                check(crossing(pose, high.add(normal), high.subtract(normal), 1 << index).isEmpty(), "translated upward edge refuses");
            }
        }
    }

    private static void nearestCrossing() {
        Pose pose = pose(v(0, 0, 0), v(0, 0, 1));
        Random random = new Random(44221);
        int multiple = 0;
        for (int sample = 0; sample < 2500; sample++) {
            Vector from = v(random.nextDouble() * 6 - 3, 0, random.nextDouble() * 4 - 1);
            Vector to = v(random.nextDouble() * 6 - 3, 0, random.nextDouble() * 4 - 1);
            Contact expected = null;
            int count = 0;
            for (int facet = 0; facet < 4; facet++) {
                var single = crossing(pose, from, to, 1 << facet);
                if (single.isEmpty()) continue;
                count++;
                Contact hit = single.orElseThrow();
                if (expected == null || hit.fraction() < expected.fraction()) expected = hit;
            }
            if (count > 1) multiple++;
            var actual = crossing(pose, from, to);
            check(actual.isPresent() == (expected != null), "combined mask retains exactly valid individual crossings");
            if (expected != null) check(actual.orElseThrow().facetIndex() == expected.facetIndex(), "nearest crossing wins with fixed index iteration");
        }
        // Disjoint facets on this convex shell may have at most one inward crossing per segment.
        check(multiple == 0, "gaps keep facet planes from overlapping into duplicate inbound contacts");
    }

    private static void reverseVelocity() {
        for (Vector original : new Vector[] {v(1, -2, 3), v(Double.MIN_VALUE, 0, -Double.MIN_VALUE),
                v(8, 0, 0), v(-0.0, 1e100, -1e100), v(Double.MAX_VALUE, 0, Double.MAX_VALUE)}) {
            Vector reversed = reverse(original);
            check(reversed.x() == -original.x() && reversed.y() == -original.y() && reversed.z() == -original.z(), "reversal is componentwise -v");
            check(Double.doubleToRawLongBits(reversed.x()) == (Double.doubleToRawLongBits(original.x()) ^ Long.MIN_VALUE), "X sign flips without magnitude rounding");
            check(reverse(reversed).equals(original), "two reversals restore every bit including signed zero");
            check(reversed.length() == original.length(), "speed cannot increase through reversal");
        }
        check(reverse(null) == null && reverse(v(Double.NaN, 0, 0)) == null, "invalid velocity cannot create reflected motion");
    }

    private static Vector v(double x, double y, double z) { return new Vector(x, y, z); }
    private static void near(double value, double expected, double tolerance, String why) { check(Math.abs(value - expected) <= tolerance, why); }
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
}
