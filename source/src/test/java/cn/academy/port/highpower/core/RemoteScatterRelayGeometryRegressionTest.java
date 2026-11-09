package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.RemoteScatterRelayGeometry.Node;
import cn.academy.port.highpower.core.RemoteScatterRelayGeometry.Route;
import cn.academy.port.highpower.core.RemoteScatterRelayGeometry.Segment;
import cn.academy.port.highpower.core.RemoteScatterRelayGeometry.Vector;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static cn.academy.port.highpower.core.RemoteScatterRelayGeometry.*;

/** Exact boundaries and immutable full-path snapshots, independent of Minecraft and NEW221. */
public final class RemoteScatterRelayGeometryRegressionTest {
    private static int checks;
    private static final Vector ORIGIN = new Vector(0, 0, 0), X = new Vector(1, 0, 0);

    public static void main(String[] args) {
        orderedFullRoute();
        exactDistanceBoundaries();
        arbitraryAngles();
        finitePlate();
        malformedRoutes();
        independentDistanceOracle();
        System.out.println("PASS " + checks + " NEW253 exact ordered relay geometry checks");
    }

    private static void orderedFullRoute() {
        check(MAX_NODES == 4 && MAX_SEGMENTS == 5 && MAX_LEG_LENGTH == 32 && MAX_LENGTH == 160,
                "one to four nodes and at most five bounded legs");
        for (int count = 1; count <= 4; count++) {
            List<Node> nodes = new ArrayList<>();
            for (int index = 0; index < count; index++) nodes.add(node(index, (index + 1) * 32));
            Vector endpoint = new Vector((count + 1) * 32, 0, 0);
            Route route = route(ORIGIN, nodes, endpoint).orElseThrow();
            check(route.segments().size() == count + 1 && route.nodes().size() == count, "every paid node supplies exactly one relay");
            check(route.totalLength() == (count + 1) * 32, "all complete actual lengths retained");
            check(route.hand().equals(ORIGIN) && route.endpoint().equals(endpoint), "actual hand and fixed endpoint retained");
            for (int index = 0; index <= count; index++) {
                Segment leg = route.segments().get(index);
                check(leg.start().equals(index == 0 ? ORIGIN : nodes.get(index - 1).center()), "leg begins at exact preceding position");
                check(leg.end().equals(index == count ? endpoint : nodes.get(index).center()), "leg ends at exact next position");
                check(leg.length() == 32 && leg.direction().equals(X), "no native origin offset or skipped interval");
            }
            nodes.clear();
            check(route.nodes().size() == count && route.segments().size() == count + 1, "caller edits cannot mutate committed route");
            rejected(() -> route.nodes().clear(), "route node snapshot is immutable");
            rejected(() -> route.segments().clear(), "route segment snapshot is immutable");
        }
        List<Node> fixed = List.of(node(0, 32), node(1, 64));
        Vector endpoint = new Vector(96, 0, 0);
        check(route(ORIGIN, fixed, endpoint).isPresent(), "initial hand path admits");
        check(route(new Vector(-Double.MIN_VALUE, 0, 0), fixed, endpoint).isEmpty(),
                "firing revalidates actual moved hand instead of old eye or placement source");
        check(route(new Vector(1, 0, 0), fixed, endpoint).orElseThrow().nodes().equals(fixed),
                "moving hand never recenters fixed nodes");
    }

    private static void exactDistanceBoundaries() {
        check(withinRange(ORIGIN, new Vector(32, 0, 0)), "closed exact 32m limit");
        check(!withinRange(ORIGIN, new Vector(Math.nextUp(32.0), 0, 0)), "one ULP outside limit refuses");
        check(!withinRange(ORIGIN, new Vector(32, Double.MIN_VALUE, 0)), "orthogonal subnormal excess cannot underflow away");
        check(!withinRange(new Vector(-Double.MIN_VALUE, 0, 0), new Vector(32, 0, 0)),
                "exact subtraction cannot round an outside distance inward");
        check(withinRange(ORIGIN, new Vector(Math.nextDown(32.0), Double.MIN_VALUE, 0)), "representable strict clearance admits");
        Vector roundedOutward = new Vector(-16.05583830319775, 25.34706552897484, -11.12368308841201);
        check(roundedOutward.dot(roundedOutward) > 1024 && withinRange(ORIGIN, roundedOutward)
                        && route(ORIGIN, List.of(new Node(0, roundedOutward, X)), roundedOutward.add(X)).isPresent(),
                "exact inside-limit route admits even when floating squared distance rounds outside 32m");
        check(withinRange(ORIGIN, ORIGIN) && !positiveLeg(ORIGIN, ORIGIN), "range and nondegenerate leg have separate predicates");
        check(positiveLeg(ORIGIN, new Vector(Double.MIN_VALUE, 0, 0)), "positive subnormal segment remains nonzero");
        check(new Segment(ORIGIN, new Vector(Double.MIN_VALUE, 0, 0)).direction().equals(X),
                "tiny positive leg yields stable unit direction");
        check(!withinRange(ORIGIN, new Vector(24, 24, 0)), "full 3D leg bound forbids diagonal overflow");
        for (double base : new double[] {8192.125, 1_000_000.125, 29_999_950.125, -29_999_950.125}) {
            Vector start = new Vector(base, 64.25, -base), end = new Vector(base + 32, 64.25, -base);
            check(withinRange(start, end), "exact 32m retained at distant world coordinates");
            check(!withinRange(start, new Vector(Math.nextUp(end.x()), end.y(), end.z())), "far-coordinate one ULP excess refuses");
            check(!withinRange(start, new Vector(end.x(), Math.nextUp(end.y()), end.z())), "far-coordinate orthogonal excess refuses");
        }
        check(!withinRange(new Vector(-WORLD_LIMIT, 0, 0), new Vector(WORLD_LIMIT, 0, 0)),
                "opposite world extremes cannot overflow squared-distance admission");
    }

    private static void arbitraryAngles() {
        check(normalFor(X, X).orElseThrow().equals(X), "straight relay has a safe perpendicular display plane");
        check(normalFor(X, X.scale(-1)).orElseThrow().equals(X), "180 degree return has a finite nondegenerate display plane");
        for (Vector outgoing : List.of(new Vector(1, 1, 0), new Vector(0, 0, 1), new Vector(-1, 2, 0),
                new Vector(0, 1, 0), new Vector(1, Double.MIN_VALUE, 0))) {
            Vector normal = normalFor(X, outgoing).orElseThrow();
            check(normal.finite() && Math.abs(normal.length() - 1) < 1e-12, "arbitrary angle derives a finite unit display normal");
            Node bend = new Node(0, new Vector(10, 0, 0), normal);
            Vector endpoint = bend.center().add(unit(outgoing).orElseThrow().scale(5));
            check(route(ORIGIN, List.of(bend), endpoint).isPresent(), "relay admission imposes no specular or grazing law");
        }
        check(normalFor(new Vector(Double.MIN_VALUE, 0, 0), new Vector(0, Double.MIN_VALUE, 0)).isPresent(),
                "subnormal incoming and outgoing directions can derive a stable normal");
        Node unrelatedDisplay = new Node(0, new Vector(10, 0, 0), new Vector(0, 1, 0));
        check(route(ORIGIN, List.of(unrelatedDisplay), new Vector(20, 0, 0)).isPresent(),
                "display normal cannot redirect or reject the fixed center-to-center route");
        check(normalFor(ORIGIN, X).isEmpty() && normalFor(X, ORIGIN).isEmpty(), "missing directions cannot invent a normal");
    }

    private static void finitePlate() {
        check(RADIUS_X == .45 && RADIUS_Y == .65 && THICKNESS == .04, "finite node retains NEW221 visual dimensions independently");
        for (Vector normal : List.of(X, new Vector(0, 1, 0), new Vector(0, -1, 0),
                unit(new Vector(1, 2, 3)).orElseThrow())) {
            Node node = new Node(0, new Vector(100, 64, 100), normal);
            Frame frame = frame(node);
            check(Math.abs(frame.right().length() - 1) < 1e-12 && Math.abs(frame.up().length() - 1) < 1e-12,
                    "normal including vertical poles yields finite orthonormal display basis");
            check(Math.abs(frame.right().dot(frame.up())) < 1e-12 && Math.abs(normal.dot(frame.right())) < 1e-12,
                    "display axes are perpendicular");
            Bounds bounds = volumeBounds(node);
            for (int angle = 0; angle < 360; angle++) {
                Vector point = perimeterPoint(node, Math.toRadians(angle));
                check(point.finite() && point.x() >= Math.nextDown(bounds.min().x()) && point.x() <= Math.nextUp(bounds.max().x())
                        && point.y() >= Math.nextDown(bounds.min().y()) && point.y() <= Math.nextUp(bounds.max().y())
                        && point.z() >= Math.nextDown(bounds.min().z()) && point.z() <= Math.nextUp(bounds.max().z()),
                        "visible finite ellipse lies in conservative node volume");
            }
        }
        check(new Node(0, new Vector(29_999_999, 64, -29_999_999), X).center().finite(), "whole plate fits at extreme world coordinate");
        rejected(() -> new Node(0, new Vector(WORLD_LIMIT, 0, 0), X), "finite center cannot authorize slab outside world");
        rejected(() -> new Node(0, new Vector(0, WORLD_LIMIT, 0), X), "finite center cannot authorize ellipse outside world");
        rejected(() -> perimeterPoint(node(0, 1), Double.NaN), "nonfinite perimeter parameter refuses");
    }

    private static void malformedRoutes() {
        Vector endpoint = new Vector(20, 0, 0);
        check(route(ORIGIN, List.of(), endpoint).isEmpty(), "zero-node shortcut denied");
        check(route(ORIGIN, null, endpoint).isEmpty(), "missing node collection denied");
        check(route(ORIGIN, List.of(node(0, 1), node(1, 2), node(2, 3), node(3, 4), node(0, 5)), endpoint).isEmpty(), "fifth node denied");
        check(route(ORIGIN, List.of(node(1, 10)), endpoint).isEmpty(), "missing first node denied");
        check(route(ORIGIN, List.of(node(0, 5), node(2, 10)), endpoint).isEmpty(), "gap in fixed node indices denied");
        check(route(ORIGIN, List.of(node(1, 5), node(0, 10)), endpoint).isEmpty(), "reordered nodes denied");
        check(route(ORIGIN, List.of(node(0, 5), node(0, 10)), endpoint).isEmpty(), "duplicate node index denied");
        check(route(ORIGIN, Arrays.asList(node(0, 5), null), endpoint).isEmpty(), "null node denied");
        check(route(ORIGIN, List.of(node(0, 0)), endpoint).isEmpty(), "hand-to-node zero leg denied");
        check(route(ORIGIN, List.of(node(0, 5), node(1, 5)), endpoint).isEmpty(), "zero interior leg denied");
        check(route(ORIGIN, List.of(node(0, 20)), endpoint).isEmpty(), "zero endpoint leg denied");
        check(route(ORIGIN, List.of(node(0, 33)), new Vector(34, 0, 0)).isEmpty(), "first leg independently bounded");
        check(route(ORIGIN, List.of(node(0, 1), node(1, 34)), new Vector(35, 0, 0)).isEmpty(), "interior leg independently bounded");
        check(route(ORIGIN, List.of(node(0, 1)), new Vector(34, 0, 0)).isEmpty(), "endpoint leg independently bounded");
        for (Vector invalid : Arrays.asList(null, new Vector(Double.NaN, 0, 0), new Vector(0, Double.POSITIVE_INFINITY, 0),
                new Vector(0, 0, Double.NEGATIVE_INFINITY), new Vector(Math.nextUp(WORLD_LIMIT), 0, 0),
                new Vector(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE))) {
            check(!withinRange(ORIGIN, invalid) && !positiveLeg(invalid, ORIGIN), "invalid native coordinates fail closed");
            check(route(invalid, List.of(node(0, 10)), endpoint).isEmpty(), "invalid firing hand fails closed");
            check(route(ORIGIN, List.of(node(0, 10)), invalid).isEmpty(), "invalid fixed endpoint fails closed");
            check(unit(invalid).isEmpty() && normalFor(invalid, X).isEmpty(), "invalid display direction fails closed");
        }
        rejected(() -> new Node(-1, ORIGIN, X), "negative index denied");
        rejected(() -> new Node(4, ORIGIN, X), "fifth fixed index denied");
        rejected(() -> new Node(0, ORIGIN, ORIGIN), "zero normal denied");
        rejected(() -> new Node(0, ORIGIN, X.scale(2)), "nonunit normal denied");
        rejected(() -> new Segment(ORIGIN, ORIGIN), "direct segment construction rejects degeneracy");
        rejected(() -> new Segment(ORIGIN, new Vector(33, 0, 0)), "direct segment construction rejects excessive length");
    }

    private static void independentDistanceOracle() {
        Random random = new Random(253253);
        for (int index = 0; index < 4000; index++) {
            double base = index % 2 == 0 ? 0 : 29_999_950.125;
            Vector a = new Vector(base, 64.25, -base);
            Vector b = new Vector(base + random.nextDouble() * 64 - 32, 64.25 + random.nextDouble() * 64 - 32,
                    -base + random.nextDouble() * 64 - 32);
            BigDecimal sum = BigDecimal.ZERO;
            double[] left = {a.x(), a.y(), a.z()}, right = {b.x(), b.y(), b.z()};
            for (int axis = 0; axis < 3; axis++) sum = sum.add(new BigDecimal(left[axis]).subtract(new BigDecimal(right[axis])).pow(2));
            boolean expected = sum.signum() > 0 && sum.compareTo(BigDecimal.valueOf(1024)) <= 0;
            check(positiveLeg(a, b) == expected, "distance agrees with independent exact represented-coordinate oracle");
        }
    }

    private static Node node(int index, double x) { return new Node(index, new Vector(x, 0, 0), X); }
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    private static void rejected(Runnable action, String reason) {
        try { action.run(); throw new AssertionError(reason); }
        catch (IllegalArgumentException | UnsupportedOperationException expected) { checks++; }
    }
}
