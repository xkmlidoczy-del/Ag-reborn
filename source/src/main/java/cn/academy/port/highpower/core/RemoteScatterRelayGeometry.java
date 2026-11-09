package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** NEW253's ordered finite relay nodes. Normals describe the display, never a reflection law. */
public final class RemoteScatterRelayGeometry {
    public static final int MAX_NODES = 4, MAX_SEGMENTS = 5;
    public static final double MAX_LEG_LENGTH = 32, MAX_LENGTH = 160;
    public static final double RADIUS_X = .45, RADIUS_Y = .65, THICKNESS = .04, BEAM_RADIUS = .035;
    public static final double WORLD_LIMIT = 30_000_000;
    private static final BigDecimal LEG_SQUARED = BigDecimal.valueOf(1024);
    private static final double UNIT_TOLERANCE = 1e-10;

    private RemoteScatterRelayGeometry() {}

    public record Vector(double x, double y, double z) {
        public boolean finite() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                    && Math.abs(x) <= WORLD_LIMIT && Math.abs(y) <= WORLD_LIMIT && Math.abs(z) <= WORLD_LIMIT;
        }
        public Vector add(Vector other) { return new Vector(x + other.x, y + other.y, z + other.z); }
        public Vector subtract(Vector other) { return new Vector(x - other.x, y - other.y, z - other.z); }
        public Vector scale(double factor) { return new Vector(x * factor, y * factor, z * factor); }
        public double dot(Vector other) { return x * other.x + y * other.y + z * other.z; }
        public Vector cross(Vector other) {
            return new Vector(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
        }
        public double length() { return Math.hypot(Math.hypot(x, y), z); }
    }

    /** Index is its fixed zero-based order, independent of subsequent caster movement. */
    public record Node(int index, Vector center, Vector normal) {
        public Node {
            if (index < 0 || index >= MAX_NODES || !valid(center) || !isUnit(normal))
                throw new IllegalArgumentException("Invalid fixed remote relay node");
            Vector extent = extent(frameFor(normal));
            if (!insideWorld(center, extent))
                throw new IllegalArgumentException("Remote relay node volume exceeds world bounds");
        }
    }

    /** Retains the supplied native endpoints exactly; it never applies an origin offset. */
    public record Segment(Vector start, Vector end) {
        public Segment {
            if (!positiveLeg(start, end)) throw new IllegalArgumentException("Invalid remote relay leg");
        }
        public double length() { return end.subtract(start).length(); }
        public Vector direction() { return unit(end.subtract(start)).orElseThrow(); }
    }

    public record Frame(Vector normal, Vector right, Vector up) {}
    public record Bounds(Vector min, Vector max) {}

    /** Only route() can construct this immutable validated firing snapshot. */
    public static final class Route {
        private final Vector hand, endpoint;
        private final List<Node> nodes;
        private final List<Segment> segments;
        private final double totalLength;

        private Route(Vector hand, List<Node> nodes, Vector endpoint, List<Segment> segments) {
            this.hand = hand;
            this.nodes = List.copyOf(nodes);
            this.endpoint = endpoint;
            this.segments = List.copyOf(segments);
            // Informational only: exact per-leg predicates already establish the total bound.
            this.totalLength = segments.stream().mapToDouble(Segment::length).sum();
        }
        public Vector hand() { return hand; }
        public List<Node> nodes() { return nodes; }
        public Vector endpoint() { return endpoint; }
        public List<Segment> segments() { return segments; }
        public double totalLength() { return totalLength; }
    }

    /**
     * Actual hand -> node 0 -> ... -> fixed endpoint, with no shortcut or implicit extra leg.
     * Every exact represented leg is <=32 and there are at most five, which proves the exact
     * total is <=160 without rounding a sum of square roots at the admission boundary.
     * Native collision and loaded-chunk checks must still cover each complete segment.
     */
    public static Optional<Route> route(Vector hand, List<Node> nodes, Vector endpoint) {
        if (!valid(hand) || !valid(endpoint) || nodes == null || nodes.isEmpty() || nodes.size() > MAX_NODES)
            return Optional.empty();
        List<Node> snapshot = new ArrayList<>(nodes);
        List<Segment> segments = new ArrayList<>(snapshot.size() + 1);
        Vector previous = hand;
        for (int index = 0; index < snapshot.size(); index++) {
            Node node = snapshot.get(index);
            if (node == null || node.index != index || !positiveLeg(previous, node.center)) return Optional.empty();
            segments.add(new Segment(previous, node.center));
            previous = node.center;
        }
        if (!positiveLeg(previous, endpoint)) return Optional.empty();
        segments.add(new Segment(previous, endpoint));
        return Optional.of(new Route(hand, snapshot, endpoint, segments));
    }

    public static boolean withinRange(Vector start, Vector end) {
        return valid(start) && valid(end) && squaredDistance(start, end).compareTo(LEG_SQUARED) <= 0;
    }

    /** A positive subnormal displacement remains positive; there is no epsilon acceptance apron. */
    public static boolean positiveLeg(Vector start, Vector end) {
        if (!valid(start) || !valid(end)) return false;
        BigDecimal squared = squaredDistance(start, end);
        return squared.signum() > 0 && squared.compareTo(LEG_SQUARED) <= 0;
    }

    /** Scaling first prevents underflow when normalizing tiny, nonzero native displacements. */
    public static Optional<Vector> unit(Vector vector) {
        if (!valid(vector)) return Optional.empty();
        double scale = Math.max(Math.abs(vector.x), Math.max(Math.abs(vector.y), Math.abs(vector.z)));
        if (scale == 0) return Optional.empty();
        double x = vector.x / scale, y = vector.y / scale, z = vector.z / scale;
        double length = Math.hypot(Math.hypot(x, y), z);
        Vector result = new Vector(x / length, y / length, z / length);
        return isUnit(result) ? Optional.of(result) : Optional.empty();
    }

    /**
     * Incoming/outgoing are propagation vectors. A straight relay gets a face perpendicular
     * to its path; other angles use a stable bisector solely for presentation. No incidence,
     * front-face or specular-reflection restriction grants or rejects a relay route.
     */
    public static Optional<Vector> normalFor(Vector incoming, Vector outgoing) {
        Optional<Vector> first = unit(incoming), second = unit(outgoing);
        if (first.isEmpty() || second.isEmpty()) return Optional.empty();
        return Optional.of(unit(first.get().subtract(second.get())).orElse(first.get()));
    }

    public static Frame frame(Node node) {
        if (node == null) throw new IllegalArgumentException("Missing remote relay node");
        return frameFor(node.normal);
    }

    public static Vector perimeterPoint(Node node, double angle) {
        if (!Double.isFinite(angle)) throw new IllegalArgumentException("Non-finite relay perimeter angle");
        Frame frame = frame(node);
        return node.center.add(frame.right.scale(RADIUS_X * Math.cos(angle)))
                .add(frame.up.scale(RADIUS_Y * Math.sin(angle)));
    }

    public static Bounds volumeBounds(Node node) {
        Vector extent = extent(frame(node));
        return new Bounds(node.center.subtract(extent), node.center.add(extent));
    }

    private static boolean valid(Vector vector) { return vector != null && vector.finite(); }
    private static boolean isUnit(Vector vector) {
        return valid(vector) && Math.abs(vector.length() - 1) <= UNIT_TOLERANCE;
    }
    private static BigDecimal squaredDistance(Vector start, Vector end) {
        BigDecimal x = exact(end.x).subtract(exact(start.x));
        BigDecimal y = exact(end.y).subtract(exact(start.y));
        BigDecimal z = exact(end.z).subtract(exact(start.z));
        return x.multiply(x).add(y.multiply(y)).add(z.multiply(z));
    }
    private static BigDecimal exact(double value) { return new BigDecimal(value); }
    private static Frame frameFor(Vector normal) {
        double horizontal = Math.hypot(normal.x, normal.z);
        Vector right = horizontal > 0 ? new Vector(normal.z / horizontal, 0, -normal.x / horizontal)
                : new Vector(1, 0, 0);
        return new Frame(normal, right, normal.cross(right));
    }
    private static Vector extent(Frame frame) {
        return new Vector(extentAxis(frame.right.x, frame.up.x, frame.normal.x),
                extentAxis(frame.right.y, frame.up.y, frame.normal.y),
                extentAxis(frame.right.z, frame.up.z, frame.normal.z));
    }
    private static double extentAxis(double right, double up, double normal) {
        return Math.hypot(RADIUS_X * right, RADIUS_Y * up) + THICKNESS / 2 * Math.abs(normal);
    }
    private static boolean insideWorld(Vector center, Vector extent) {
        BigDecimal limit = BigDecimal.valueOf((long) WORLD_LIMIT);
        return exact(center.x).abs().add(exact(extent.x)).compareTo(limit) <= 0
                && exact(center.y).abs().add(exact(extent.y)).compareTo(limit) <= 0
                && exact(center.z).abs().add(exact(extent.z)).compareTo(limit) <= 0;
    }
}
