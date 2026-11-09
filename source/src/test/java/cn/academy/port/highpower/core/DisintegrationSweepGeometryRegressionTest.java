package cn.academy.port.highpower.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import cn.academy.port.highpower.core.FoldedMirrorGeometry.Bounds;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import static cn.academy.port.highpower.core.DisintegrationSweepGeometry.*;

/** Standalone, Minecraft-free continuous fan and bounded-query regression. */
public final class DisintegrationSweepGeometryRegressionTest {
    private static int checks;
    private static final Vector ZERO = v(0, 0, 0);
    private static final FrozenPlane PLANE = new FrozenPlane(ZERO, v(0, 0, 1), v(1, 0, 0));
    public static void main(String[] args) {
        analyticSection(); rangeAndPlaneBorders(); translations(); bases(); queryBoundsAndTies(); randomizedOracle(); rotatedTranslatedOracle(); inwardEndpoint(); malformed();
        DisintegrationSweepNativeRoundingRegressionTest.main(new String[0]);
        System.out.println("Disintegration sweep geometry: " + checks + " checks passed");
    }

    private static void analyticSection() {
        Bounds box = box(1, -.1, 10, 2, .1, 11);
        var contact = firstContact(PLANE, ZERO, box, -HALF_ANGLE, HALF_ANGLE).orElseThrow();
        double expected = Math.atan2(1 - BEAM_RADIUS, 11 + BEAM_RADIUS);
        near(contact.angle(), expected, 2e-15, "earliest exact section corner angle");
        near(contact.distance(), Math.hypot(1 - BEAM_RADIUS, 11 + BEAM_RADIUS), 1e-12, "corner radial distance");
        var interval = contactIntervals(PLANE, ZERO, box, -HALF_ANGLE, HALF_ANGLE).getFirst();
        near(interval.from(), expected, 2e-15, "interval lower extreme");
        near(interval.to(), Math.atan2(2 + BEAM_RADIUS, 10 - BEAM_RADIUS), 2e-15, "interval upper extreme");
        check(contactAt(PLANE, ZERO, box, interval.from()).isPresent(), "lower interval endpoint has a real ray");
        check(contactAt(PLANE, ZERO, box, interval.to()).isPresent(), "upper interval endpoint has a real ray");
        check(firstContact(PLANE, ZERO, box, -.4, 0).isEmpty(), "disjoint tick angle interval never hits");
        var clipped = firstContact(PLANE, ZERO, box, .1, .2).orElseThrow();
        near(clipped.angle(), .1, 0, "already intersected body contacts at tick interval start");

        // A 0.001-degree interval falls between any coarse visual rays; continuous geometry finds it.
        double theta = .1732050807568877;
        Vector center = direction(PLANE, theta).scale(30);
        Bounds tiny = box(center.x(), -.01, center.z(), center.x(), .01, center.z());
        check(firstContact(PLANE, ZERO, tiny, theta - .00001, theta + .00001).isPresent(), "continuous sub-ray-width body contact");
        check(contactIntervals(PLANE, ZERO, tiny, theta - .00001, theta + .00001).size() == 1, "convex interval is bounded");
        check(firstContact(PLANE, ZERO, box(-1, -1, -5, 1, 1, -3), -HALF_ANGLE, HALF_ANGLE).isEmpty(), "behind release plane misses");
        var containsHand = firstContact(PLANE, ZERO, box(-1, -1, -1, 1, 1, 1), -HALF_ANGLE, HALF_ANGLE).orElseThrow();
        check(containsHand.distance() == 0 && containsHand.angle() == -HALF_ANGLE, "hand inside body is closed zero-distance contact");
    }

    private static void rangeAndPlaneBorders() {
        Bounds tangent = box(0, 0, MAX_RANGE + BEAM_RADIUS, 0, 0, MAX_RANGE + BEAM_RADIUS);
        var hit = firstContact(PLANE, ZERO, tangent, -HALF_ANGLE, HALF_ANGLE).orElseThrow();
        near(hit.angle(), 0, 1e-14, "range circle tangent retains the single contact ray");
        near(hit.distance(), MAX_RANGE, 1e-12, "range tangent does not extend range");
        Bounds beyond = box(-.001, 0, MAX_RANGE + BEAM_RADIUS + 1e-8, .001, 0, MAX_RANGE + BEAM_RADIUS + 1e-8);
        check(firstContact(PLANE, ZERO, beyond, -HALF_ANGLE, HALF_ANGLE).isEmpty(), "beyond outer circle misses");
        check(firstContact(PLANE, ZERO, box(0, BEAM_RADIUS, 10, 0, BEAM_RADIUS, 10), -HALF_ANGLE, HALF_ANGLE).isPresent(), "closed AABB plane tangent admitted");
        check(firstContact(PLANE, ZERO, box(0, Math.nextUp(BEAM_RADIUS), 10, 0, Math.nextUp(BEAM_RADIUS), 10), -HALF_ANGLE, HALF_ANGLE).isEmpty(), "one representable step outside plane radius misses");
        for (double sign : new double[]{-1, 1}) {
            Vector edge = direction(PLANE, sign * HALF_ANGLE).scale(20);
            Bounds boundary = box(edge.x(), -.1, edge.z(), edge.x(), .1, edge.z());
            check(contactAt(PLANE, ZERO, boundary, sign * HALF_ANGLE).isPresent(), "closed fifty-degree fan border contacts");
            check(firstContact(PLANE, ZERO, boundary, sign < 0 ? -HALF_ANGLE : HALF_ANGLE, sign < 0 ? -HALF_ANGLE : HALF_ANGLE).isPresent(), "zero-width angular interval contacts");
        }
        check(!withinFrozenFootprint(PLANE, v(0, 0, Math.nextUp(MAX_RANGE))), "frozen range never epsilon-expanded");
        check(!withinFrozenFootprint(PLANE, v(0, Math.nextUp(BEAM_RADIUS), 20)), "frozen plane slab never epsilon-expanded");
    }

    private static void translations() {
        Vector forwardHand = v(0, 0, .25);
        check(permittedOrigin(PLANE, forwardHand), "exact quarter-meter origin leash");
        check(!permittedOrigin(PLANE, v(0, 0, Math.nextUp(.25))), "origin leash rejects next representable displacement");
        check(firstContact(PLANE, forwardHand, box(-.01, 0, 48.1, .01, 0, 48.1), -HALF_ANGLE, HALF_ANGLE).isEmpty(), "moving forward cannot extend frozen range");
        check(firstContact(PLANE, v(0, 0, -.25), box(-.01, 0, 47.9, .01, 0, 47.9), -HALF_ANGLE, HALF_ANGLE).isEmpty(), "each actual ray also retains its forty-eight-meter cap");
        var shifted = firstContact(PLANE, v(.2, 0, 0), box(-.1, -.1, 12, .1, .1, 13), -HALF_ANGLE, HALF_ANGLE).orElseThrow();
        near(shifted.point().subtract(v(.2, 0, 0)).length(), shifted.distance(), 1e-12, "contact starts at actual translated hand");
        check(withinFrozenFootprint(PLANE, shifted.point()), "translated contact still belongs to original fan");
        check(firstContact(PLANE, v(0, .1, 0), box(-1, -1, 10, 1, 1, 11), -HALF_ANGLE, HALF_ANGLE).isEmpty(), "parallel translated ray cannot widen original plane slab");
        check(firstContact(PLANE, v(0, .02, 0), box(-1, -1, 10, 1, 1, 11), -HALF_ANGLE, HALF_ANGLE).isPresent(), "admitted normal drift uses actual ray plane");
        Vector distant = v(29_999_000, 28_000_000, -29_999_000);
        FrozenPlane far = new FrozenPlane(distant, PLANE.forward(), PLANE.across());
        Bounds source = box(1, -.1, 10, 2, .1, 11);
        Bounds target = new Bounds(source.min().add(distant), source.max().add(distant));
        var farContact = firstContact(far, distant, target, -HALF_ANGLE, HALF_ANGLE).orElseThrow();
        near(farContact.angle(), firstContact(PLANE, ZERO, source, -HALF_ANGLE, HALF_ANGLE).orElseThrow().angle(), 1e-9, "large-coordinate contact uses bounded relative arithmetic");
        check(withinFrozenFootprint(far, farContact.point()), "large-coordinate real contact stays in frozen footprint");
    }

    private static void bases() {
        for (double yaw : new double[]{0, .3, 1.7, -2.9}) for (double pitch : new double[]{-Math.PI / 2, -.4, 0, .8, Math.PI / 2}) {
            Vector look = v(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
            for (boolean vertical : new boolean[]{false, true}) {
                FrozenPlane plane = freeze(ZERO, look, yaw, vertical).orElseThrow();
                near(plane.forward().dot(plane.across()), 0, 1e-14, "basis orthogonal at every look pitch");
                near(plane.across().length(), 1, 1e-14, "basis unit across at every look pitch");
                near(direction(plane, .2).length(), 1, 1e-14, "sweep direction remains unit");
            }
            FrozenPlane singular = freeze(ZERO, v(0, -1, 0), yaw, false).orElseThrow();
            near(singular.across().subtract(v(-Math.cos(yaw), 0, -Math.sin(yaw))).length(), 0, 1e-14, "exact vertical look has deterministic release-yaw right");
        }
        var horizontal = freeze(ZERO, v(0, 0, 1), 0, false).orElseThrow();
        var vertical = freeze(ZERO, v(0, 0, 1), 0, true).orElseThrow();
        check(horizontal.across().equals(v(-1, 0, 0)), "normal mode freezes true aim-right");
        near(vertical.across().subtract(v(0, 1, 0)).length(), 0, 0, "crouch mode freezes true aim-up");
    }

    private static void queryBoundsAndTies() {
        Random random = new Random(231_48);
        for (int i = 0; i < 100; i++) {
            Vector look = FoldedMirrorGeometry.unit(v(random.nextDouble() - .5, random.nextDouble() - .5, random.nextDouble() - .5)).orElseThrow();
            var plane = freeze(v(100, 70, 200), look, .4, i % 2 == 0).orElseThrow();
            Bounds bounds = queryBounds(plane);
            for (int j = 0; j <= 100; j++) {
                Vector point = plane.origin().add(direction(plane, -HALF_ANGLE + 2 * HALF_ANGLE * j / 100).scale(MAX_RANGE));
                for (double sign : new double[]{-1, 1}) {
                    Vector core = point.add(plane.normal().scale(sign * BEAM_RADIUS));
                    check(inside(bounds, core), "analytic native query bounds enclose every plane orientation and arc support");
                }
            }
        }
        check(segmentQueryBounds(ZERO, v(0, 0, .25)).isPresent(), "native prefix admits exact quarter meter");
        check(segmentQueryBounds(ZERO, v(0, 0, Math.nextUp(.25))).isEmpty(), "native prefix refuses longer reads");
        var query = segmentQueryBounds(ZERO, v(0, 0, .25)).orElseThrow();
        check(query.min().x() < -BEAM_RADIUS && query.max().z() > .25 + BEAM_RADIUS, "native queries include closed shape tangent via outward ULP");
        Bounds box = box(1, -1, 10, 2, 1, 11);
        var hit = firstContact(PLANE, ZERO, box, -HALF_ANGLE, HALF_ANGLE).orElseThrow();
        List<Candidate> candidates = new ArrayList<>(List.of(new Candidate(9, box, hit), new Candidate(2, box, hit)));
        candidates.sort(CONTACT_ORDER);
        check(candidates.getFirst().identity() == 2, "actual angle and distance tie resolves to native id");
    }

    /** Independent dense rays are an oracle only; production never samples them. */
    private static void randomizedOracle() {
        Random random = new Random(231);
        for (int i = 0; i < 500; i++) {
            double centerAngle = (random.nextDouble() - .5) * 1.2, distance = .5 + random.nextDouble() * 52;
            Vector center = directionUnbounded(centerAngle).scale(distance);
            double width = .02 + random.nextDouble() * 2, height = .02 + random.nextDouble() * .2;
            Bounds box = box(center.x() - width, -.03 - height, center.z() - width, center.x() + width, .03 + height, center.z() + width);
            var intervals = contactIntervals(PLANE, ZERO, box, -HALF_ANGLE, HALF_ANGLE);
            for (int j = 0; j <= 400; j++) {
                double theta = -HALF_ANGLE + 2 * HALF_ANGLE * j / 400;
                boolean expected = slabOracle(box, theta);
                boolean actual = contactAt(PLANE, ZERO, box, theta).isPresent();
                check(actual == expected, "independent ray/AABB slab oracle agrees");
                if (expected) check(!intervals.isEmpty() && theta >= intervals.getFirst().from() - 1e-13 && theta <= intervals.getFirst().to() + 1e-13,
                        "every independent admitted ray lies inside analytic continuous interval");
            }
            if (!intervals.isEmpty()) {
                var interval = intervals.getFirst();
                check(slabOracle(box, interval.from()), "analytic interval lower endpoint independently touches native AABB");
                check(slabOracle(box, interval.to()), "analytic interval upper endpoint independently touches native AABB");
            }
        }
    }

    private static boolean slabOracle(Bounds box, double theta) {
        Vector direction = directionUnbounded(theta);
        double lo = 0, hi = 48;
        double[] min = {box.min().x() - BEAM_RADIUS, box.min().y() - BEAM_RADIUS, box.min().z() - BEAM_RADIUS};
        double[] max = {box.max().x() + BEAM_RADIUS, box.max().y() + BEAM_RADIUS, box.max().z() + BEAM_RADIUS};
        double[] dirs = {direction.x(), direction.y(), direction.z()};
        for (int axis = 0; axis < 3; axis++) {
            if (dirs[axis] == 0) { if (min[axis] > 0 || max[axis] < 0) return false; continue; }
            double a = min[axis] / dirs[axis], b = max[axis] / dirs[axis];
            lo = Math.max(lo, Math.min(a, b)); hi = Math.min(hi, Math.max(a, b));
        }
        return lo <= hi;
    }

    private static void rotatedTranslatedOracle() {
        // Reduced case: near-axis-parallel clipping used to discard its earliest vertex because
        // 64 nextAfter steps could not repair the accumulated plane-projection roundoff.
        FrozenPlane reduced = new FrozenPlane(ZERO, v(-.003607054054396283, .6564148291908584, -.7543915171708819),
                v(.003138551603878363, .7544001405211537, .6564073258849977));
        Vector reducedHand = v(.004963177794384583, -.013570868373493618, -.009658425420947053);
        Bounds reducedBody = box(-.1327029445959031, 1.909965826195184, -1.9431625593509698,
                .11530667172521694, 2.157975442516304, -1.6951529430298498);
        var reducedInterval = contactIntervals(reduced, reducedHand, reducedBody, -HALF_ANGLE, HALF_ANGLE).getFirst();
        check(reducedInterval.from() < .052359877559829904 && reducedInterval.to() > .052359877559829904,
                "reduced nearly parallel AABB counterexample retains earliest continuous interval");
        Random random = new Random(231_035);
        for (int i = 0; i < 300; i++) {
            Vector origin = i % 3 == 0 ? v(29_999_000, 28_000_000, -29_999_000) : ZERO;
            Vector look = FoldedMirrorGeometry.unit(v(random.nextDouble() - .5, random.nextDouble() - .5, random.nextDouble() - .5)).orElseThrow();
            var plane = freeze(origin, look, .7, i % 2 == 0).orElseThrow();
            Vector hand = origin.add(plane.forward().scale((random.nextDouble() - .5) * .3))
                    .add(plane.across().scale((random.nextDouble() - .5) * .3)).add(plane.normal().scale((random.nextDouble() - .5) * .04));
            double theta = (random.nextDouble() - .5) * .8, range = 1 + random.nextDouble() * 49;
            Vector center = origin.add(direction(plane, theta).scale(range));
            double size = .05 + random.nextDouble() * 1.3;
            Bounds body = new Bounds(center.subtract(v(size, size, size)), center.add(v(size, size, size)));
            var intervals = contactIntervals(plane, hand, body, -HALF_ANGLE, HALF_ANGLE);
            for (int j = 1; j < 150; j++) {
                double angle = -HALF_ANGLE + 2 * HALF_ANGLE * j / 150;
                boolean expected = worldSlabOracle(plane, hand, body, angle);
                check(contactAt(plane, hand, body, angle).isPresent() == expected, "rotated translated native ray/sphere oracle agrees");
                if (expected) check(!intervals.isEmpty() && angle >= intervals.getFirst().from() - 1e-12 && angle <= intervals.getFirst().to() + 1e-12,
                        "rotated translated analytic interval contains every native oracle hit: case=" + i + ", angle=" + angle + ", intervals=" + intervals + ", plane=" + plane + ", hand=" + hand + ", body=" + body);
            }
        }
    }

    private static boolean worldSlabOracle(FrozenPlane plane, Vector hand, Bounds bounds, double angle) {
        Vector d = direction(plane, angle), delta = hand.subtract(plane.origin());
        double near = 0, far = MAX_RANGE;
        double[] min = {bounds.min().x() - BEAM_RADIUS, bounds.min().y() - BEAM_RADIUS, bounds.min().z() - BEAM_RADIUS};
        double[] max = {bounds.max().x() + BEAM_RADIUS, bounds.max().y() + BEAM_RADIUS, bounds.max().z() + BEAM_RADIUS};
        double[] origin = {hand.x(), hand.y(), hand.z()}, dir = {d.x(), d.y(), d.z()};
        for (int axis = 0; axis < 3; axis++) {
            if (dir[axis] == 0) { if (origin[axis] < min[axis] || origin[axis] > max[axis]) return false; continue; }
            double a = (min[axis] - origin[axis]) / dir[axis], b = (max[axis] - origin[axis]) / dir[axis];
            near = Math.max(near, Math.min(a, b)); far = Math.min(far, Math.max(a, b));
        }
        for (double sign : new double[]{-1, 1}) {
            Vector inward = plane.forward().scale(Math.sin(HALF_ANGLE)).add(plane.across().scale(sign * Math.cos(HALF_ANGLE)));
            double value = delta.dot(inward), slope = d.dot(inward);
            if (slope == 0) { if (value < 0) return false; }
            else if (slope > 0) near = Math.max(near, -value / slope);
            else far = Math.min(far, -value / slope);
        }
        double aa = d.dot(d), bb = delta.dot(d), cc = delta.dot(delta) - MAX_RANGE * MAX_RANGE;
        double root = Math.sqrt(bb * bb - aa * cc);
        near = Math.max(near, (-bb - root) / aa); far = Math.min(far, (-bb + root) / aa);
        return near <= far && Math.abs(delta.dot(plane.normal())) <= BEAM_RADIUS;
    }

    private static void inwardEndpoint() {
        near(rayEndpoint(PLANE, ZERO, 0).orElseThrow().z(), MAX_RANGE, 0, "unchanged origin retains exact center-ray reach");
        Vector forwardHand = v(0, 0, .25);
        Vector forwardEnd = rayEndpoint(PLANE, forwardHand, 0).orElseThrow();
        near(forwardEnd.z(), MAX_RANGE, 0, "translated hand endpoint clips to original range");
        near(forwardEnd.subtract(forwardHand).length(), 47.75, 0, "translation never adds a quarter meter of reach");
        check(rayEndpoint(PLANE, v(0, 0, -.01), 0).isEmpty(), "small backward hand shift refuses an out-of-footprint prefix");
        check(rayEndpoint(PLANE, v(.01, 0, 0), 0).isEmpty(), "small lateral hand shift refuses an out-of-footprint prefix");
        check(rayEndpoint(PLANE, v(0, .04, 0), 0).isEmpty(), "small normal hand shift beyond fixed core slab is refused");
        check(rayEndpoint(PLANE, v(0, 0, .251), 0).isEmpty(), "otherwise safe forward hand still obeys quarter-meter leash");
        Vector normalHand = v(0, .02, 0), normalEnd = rayEndpoint(PLANE, normalHand, 0).orElseThrow();
        check(normalEnd.y() == normalHand.y() && withinFrozenFootprint(PLANE, normalEnd), "admitted parallel plane starts at real hand and clips sphere");
        Random random = new Random(231_250);
        int returned = 0;
        for (int i = 0; i < 500; i++) {
            Vector origin = i % 2 == 0 ? v(29_999_000, 28_000_000, -29_999_000) : ZERO;
            var plane = freeze(origin, v(random.nextDouble() - .5, random.nextDouble() - .5, random.nextDouble() - .5), .3, i % 3 == 0).orElseThrow();
            Vector hand = origin.add(plane.forward().scale(.12)).add(plane.across().scale(.01)).add(plane.normal().scale(.01));
            double angle = (random.nextDouble() - .5) * 2 * HALF_ANGLE;
            var endpoint = rayEndpoint(plane, hand, angle);
            if (endpoint.isEmpty()) continue;
            returned++;
            check(withinFrozenFootprint(plane, endpoint.get()), "returned distant-world endpoint is actually inside frozen footprint");
            check(endpoint.get().subtract(hand).length() <= MAX_RANGE, "returned endpoint obeys actual-hand range without epsilon");
            for (int j = 0; j <= 8; j++) {
                Vector point = hand.add(endpoint.get().subtract(hand).scale(j / 8.0));
                check(withinFrozenFootprint(plane, point), "entire checked native prefix lies in original convex footprint");
            }
        }
        check(returned > 490, "bounded inward rounding preserves ordinary admissible translated rays");
    }

    private static void malformed() {
        Bounds box = box(0, 0, 2, 1, 1, 3);
        check(freeze(ZERO, ZERO, 0, false).isEmpty(), "zero look rejected");
        check(freeze(ZERO, v(0, 0, 1), Double.NaN, false).isEmpty(), "nonfinite yaw rejected");
        check(firstContact(PLANE, ZERO, box, -HALF_ANGLE, Math.nextUp(HALF_ANGLE)).isEmpty(), "oversized angle interval rejected");
        check(firstContact(PLANE, ZERO, box, .1, -.1).isEmpty(), "reversed interval rejected");
        check(firstContact(PLANE, v(Double.NaN, 0, 0), box, -HALF_ANGLE, HALF_ANGLE).isEmpty(), "nonfinite hand rejected");
        check(contactIntervals(PLANE, ZERO, new Bounds(v(1, 1, 1), ZERO), -HALF_ANGLE, HALF_ANGLE).isEmpty(), "reversed bounds rejected");
        try { new FrozenPlane(ZERO, v(0, 0, 1), v(0, 0, 1)); throw new AssertionError("parallel basis accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }

    private static Vector directionUnbounded(double angle) { return v(Math.sin(angle), 0, Math.cos(angle)); }
    private static boolean inside(Bounds bounds, Vector point) { return point.x() >= bounds.min().x() && point.x() <= bounds.max().x() && point.y() >= bounds.min().y() && point.y() <= bounds.max().y() && point.z() >= bounds.min().z() && point.z() <= bounds.max().z(); }
    private static Bounds box(double x, double y, double z, double xx, double yy, double zz) { return new Bounds(v(x, y, z), v(xx, yy, zz)); }
    private static Vector v(double x, double y, double z) { return new Vector(x, y, z); }
    private static void near(double actual, double expected, double tolerance, String why) { check(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance, why + ": " + actual + " versus " + expected); }
    private static void check(boolean okay, String why) { checks++; if (!okay) throw new AssertionError(why); }
}
