package cn.academy.port.highpower.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * NEW421's shared, finite polygonal front and authoritative swept-chord geometry.
 * No clock, camera, Minecraft classes, spokes, or filled angular sector are involved.
 * A chord's radius is the declared axis-aligned Minkowski apron around a native box;
 * it is not an extra numerical tolerance. Narrow-phase comparisons are closed and exact
 * double comparisons. World coordinates are subtracted before doing local arithmetic.
 */
public final class ShearWindbladeGeometry {
    public static final int CHORDS = 8;
    public static final double HALF_WIDTH = 1.2, BOW = .4, RADIUS = .035, RANGE = 24;
    public static final double WORLD_LIMIT = 30_000_000;
    /** One common translation limit preserves the front instead of clamping its vertices. */
    public static final double MAX_TRANSLATION = Math.nextDown(
            Math.sqrt((RANGE - RADIUS) * (RANGE - RADIUS) - HALF_WIDTH * HALF_WIDTH) - BOW);

    public record Point(double x, double y, double z) {
        public boolean finite() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                    && Math.abs(x) <= WORLD_LIMIT && Math.abs(y) <= WORLD_LIMIT && Math.abs(z) <= WORLD_LIMIT;
        }
        public double distanceSquared(Point other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public boolean finite() {
            return new Point(minX, minY, minZ).finite() && new Point(maxX, maxY, maxZ).finite()
                    && minX < maxX && minY < maxY && minZ < maxZ;
        }
    }

    /** Immutable cast-time basis. Plane 0 is horizontal; plane 1 is vertical. */
    public record Frame(Point origin, Point forward, Point horizontal, Point vertical) {
        public Frame {
            if (!valid(origin) || !unit(forward) || !unit(horizontal) || !unit(vertical)
                    || Math.abs(dot(forward, horizontal)) > 1e-12
                    || Math.abs(dot(forward, vertical)) > 1e-12
                    || Math.abs(dot(horizontal, vertical)) > 1e-12)
                throw new IllegalArgumentException("Invalid NEW421 frozen basis");
        }
    }

    /** time is swept travel [0,1]; along is the actual chord coordinate [0,1]. */
    public record Contact(double time, double along, int chord, Point point, Point previousPoint) {}
    private record Parameter(double u, double t) {}

    private ShearWindbladeGeometry() {}

    /** yawRadians is captured with look and is used only at the vertical poles. */
    public static Frame frame(Point origin, Point capturedLook, double yawRadians) {
        if (!valid(origin) || !valid(capturedLook) || !Double.isFinite(yawRadians))
            throw new IllegalArgumentException("Invalid NEW421 cast frame");
        Point forward = normalized(capturedLook);
        Point horizontal = new Point(forward.z, 0, -forward.x);
        if (horizontal.x == 0 && horizontal.z == 0)
            horizontal = new Point(Math.cos(yawRadians), 0, Math.sin(yawRadians));
        horizontal = normalized(horizontal);
        return new Frame(origin, forward, horizontal, normalized(cross(forward, horizontal)));
    }

    /** Exactly the nine authoritative vertices, in stable left-to-right order. */
    public static List<Point> front(Frame frame, int plane, double distance) {
        validateSweep(frame, plane, distance, distance);
        var result = new ArrayList<Point>(CHORDS + 1);
        for (int i = 0; i <= CHORDS; i++) {
            Point point = add(frame.origin, localVertex(frame, plane, distance, i));
            if (!valid(point)) throw new IllegalArgumentException("NEW421 front outside world bounds");
            result.add(point);
        }
        return List.copyOf(result);
    }

    /** Broad phase only; rounding outwards cannot enlarge narrow-phase contact. */
    public static Box sweptBounds(Frame frame, int plane, double fromDistance, double toDistance) {
        validateSweep(frame, plane, fromDistance, toDistance);
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (double distance : new double[]{fromDistance, toDistance}) for (int i = 0; i <= CHORDS; i++) {
            Point local = localVertex(frame, plane, distance, i);
            minX = Math.min(minX, local.x); minY = Math.min(minY, local.y); minZ = Math.min(minZ, local.z);
            maxX = Math.max(maxX, local.x); maxY = Math.max(maxY, local.y); maxZ = Math.max(maxZ, local.z);
        }
        Box result = new Box(Math.nextDown(frame.origin.x + (minX - RADIUS)),
                Math.nextDown(frame.origin.y + (minY - RADIUS)),
                Math.nextDown(frame.origin.z + (minZ - RADIUS)),
                Math.nextUp(frame.origin.x + (maxX + RADIUS)),
                Math.nextUp(frame.origin.y + (maxY + RADIUS)),
                Math.nextUp(frame.origin.z + (maxZ + RADIUS)));
        if (!result.finite()) throw new IllegalArgumentException("NEW421 sweep outside world bounds");
        return result;
    }

    /**
     * Earliest contact with any translated chord. Equal times prefer the lower chord
     * index, then its lower along value. Invalid input is a closed failure, not a hit.
     */
    public static Optional<Contact> earliestContact(Frame frame, int plane, double fromDistance,
            double toDistance, Box box) {
        if (box == null || !box.finite()) return Optional.empty();
        try { sweptBounds(frame, plane, fromDistance, toDistance); }
        catch (IllegalArgumentException invalid) { return Optional.empty(); }
        Point delta = scale(frame.forward, toDistance - fromDistance);
        Contact earliest = null;
        for (int chord = 0; chord < CHORDS; chord++) {
            Point a = localVertex(frame, plane, fromDistance, chord);
            Point b = localVertex(frame, plane, fromDistance, chord + 1);
            Contact contact = clippedChord(frame.origin, a, b, delta, box, chord);
            if (contact != null && (earliest == null || before(contact, earliest))) earliest = contact;
        }
        return Optional.ofNullable(earliest);
    }

    /** Independent finite chord primitive; zero travel is a valid static segment query. */
    public static Optional<Contact> sweepChord(Point a, Point b, Point translation, Box box) {
        if (!valid(a) || !valid(b) || !valid(translation) || box == null || !box.finite()
                || a.distanceSquared(b) == 0 || !valid(add(a, translation)) || !valid(add(b, translation)))
            return Optional.empty();
        return Optional.ofNullable(clippedChord(a, new Point(0, 0, 0), subtract(b, a), translation, box, 0));
    }

    private static Contact clippedChord(Point origin, Point a, Point b, Point delta, Box box, int chord) {
        Point side = subtract(b, a);
        if (dot(side, side) == 0) return null;
        // Subtract the shared origin first: never form a float world coordinate or an
        // inflated world-space face before reducing to small relative coordinates.
        double[] low = { (box.minX - origin.x) - a.x - RADIUS,
                (box.minY - origin.y) - a.y - RADIUS, (box.minZ - origin.z) - a.z - RADIUS };
        double[] high = { (box.maxX - origin.x) - a.x + RADIUS,
                (box.maxY - origin.y) - a.y + RADIUS, (box.maxZ - origin.z) - a.z + RADIUS };
        double[] u = {side.x, side.y, side.z}, t = {delta.x, delta.y, delta.z};
        List<Parameter> polygon = List.of(new Parameter(0, 0), new Parameter(1, 0),
                new Parameter(1, 1), new Parameter(0, 1));
        for (int axis = 0; axis < 3 && !polygon.isEmpty(); axis++) {
            polygon = clip(polygon, u[axis], t[axis], high[axis]);
            polygon = clip(polygon, -u[axis], -t[axis], -low[axis]);
        }
        if (polygon.isEmpty()) return null;
        // Every final answer must still satisfy ALL six original half-planes. An
        // intersection rounded by one clip is not automatically inside earlier clips.
        Contact first = null;
        double centerU = 0, centerT = 0;
        for (Parameter candidate : polygon) {
            centerU += candidate.u / polygon.size(); centerT += candidate.t / polygon.size();
            Contact verified = verifiedContact(candidate, origin, a, side, delta, box, chord, low, high, u, t);
            if (verified != null && (first == null || before(verified, first))) first = verified;
        }
        Parameter center = new Parameter(centerU, centerT);
        Contact anchor = verifiedContact(center, origin, a, side, delta, box, chord, low, high, u, t);
        if (anchor == null) anchor = first;
        if (anchor == null) return null;
        if (first == null || before(anchor, first)) first = anchor;
        for (Parameter candidate : polygon) {
            if (candidate.t > first.time) continue;
            Contact verified = verifiedContact(candidate, origin, a, side, delta, box, chord, low, high, u, t);
            if (verified == null) {
                // Approach an already verified interior point, then bisect towards
                // the boundary. This corrects representational rounding inwards;
                // it never relaxes a face, radius, range, or parameter inequality.
                double outside = 0, inside = 1;
                Contact inward = anchor;
                for (int iteration = 0; iteration < 64; iteration++) {
                    double fraction = (outside + inside) * .5;
                    if (fraction == outside || fraction == inside) break;
                    Parameter probe = new Parameter(Math.fma(fraction, anchor.along - candidate.u, candidate.u),
                            Math.fma(fraction, anchor.time - candidate.t, candidate.t));
                    Contact checked = verifiedContact(probe, origin, a, side, delta, box, chord, low, high, u, t);
                    if (checked == null) outside = fraction;
                    else { inside = fraction; inward = checked; }
                }
                verified = inward;
            }
            if (before(verified, first)) first = verified;
        }
        return first;
    }

    private static Contact verifiedContact(Parameter p, Point origin, Point a, Point side, Point delta,
            Box box, int chord, double[] low, double[] high, double[] u, double[] t) {
        if (!Double.isFinite(p.u) || !Double.isFinite(p.t) || p.u < 0 || p.u > 1 || p.t < 0 || p.t > 1)
            return null;
        for (int axis = 0; axis < 3; axis++)
            if (value(p, u[axis], t[axis], high[axis]) < 0 || value(p, -u[axis], -t[axis], -low[axis]) < 0)
                return null;
        Point previous = add(origin, at(a, side, delta, p.u, 0));
        Point point = add(origin, at(a, side, delta, p.u, p.t));
        if (!valid(previous) || !valid(point)) return null;
        Point relative = subtract(point, origin);
        // Verify the representable reported world point as well as its parameters.
        if (relative.x < (box.minX - origin.x) - RADIUS || relative.x > (box.maxX - origin.x) + RADIUS
                || relative.y < (box.minY - origin.y) - RADIUS || relative.y > (box.maxY - origin.y) + RADIUS
                || relative.z < (box.minZ - origin.z) - RADIUS || relative.z > (box.maxZ - origin.z) + RADIUS)
            return null;
        return new Contact(p.t, p.u, chord, point, previous);
    }

    private static Point at(Point a, Point side, Point delta, double u, double t) {
        return new Point(Math.fma(side.x, u, Math.fma(delta.x, t, a.x)),
                Math.fma(side.y, u, Math.fma(delta.y, t, a.y)),
                Math.fma(side.z, u, Math.fma(delta.z, t, a.z)));
    }

    /** Clip the closed unit (u,t) square by a*u+b*t <= c, without epsilon expansion. */
    private static List<Parameter> clip(List<Parameter> input, double a, double b, double c) {
        if (input.isEmpty()) return input;
        var output = new ArrayList<Parameter>(input.size() + 1);
        Parameter previous = input.getLast();
        double previousValue = value(previous, a, b, c);
        for (Parameter current : input) {
            double currentValue = value(current, a, b, c);
            if ((previousValue >= 0) != (currentValue >= 0)) {
                double ratio = previousValue / (previousValue - currentValue);
                output.add(new Parameter(Math.clamp(Math.fma(ratio, current.u - previous.u, previous.u), 0, 1),
                        Math.clamp(Math.fma(ratio, current.t - previous.t, previous.t), 0, 1)));
            }
            if (currentValue >= 0) output.add(current);
            previous = current;
            previousValue = currentValue;
        }
        return output;
    }

    private static double value(Parameter p, double a, double b, double c) {
        return Math.fma(-a, p.u, Math.fma(-b, p.t, c));
    }
    private static boolean before(Contact a, Contact b) {
        return a.time < b.time || (a.time == b.time && (a.chord < b.chord
                || (a.chord == b.chord && a.along < b.along)));
    }
    private static Point localVertex(Frame frame, int plane, double distance, int vertex) {
        double lateral = HALF_WIDTH * (2.0 * vertex / CHORDS - 1);
        double ratio = lateral / HALF_WIDTH;
        double forward = distance + BOW * (1 - ratio * ratio);
        return add(scale(frame.forward, forward), scale(plane == 0 ? frame.horizontal : frame.vertical, lateral));
    }
    private static void validateSweep(Frame frame, int plane, double from, double to) {
        if (frame == null || (plane != 0 && plane != 1) || !Double.isFinite(from) || !Double.isFinite(to)
                || from < 0 || to < from || to > MAX_TRANSLATION)
            throw new IllegalArgumentException("Invalid NEW421 finite sweep");
    }
    private static boolean valid(Point p) { return p != null && p.finite(); }
    private static boolean unit(Point p) { return valid(p) && Math.abs(dot(p, p) - 1) <= 1e-12; }
    private static Point normalized(Point p) {
        double length = Math.hypot(Math.hypot(p.x, p.y), p.z);
        if (!Double.isFinite(length) || length == 0) throw new IllegalArgumentException("Degenerate NEW421 direction");
        return new Point(p.x / length, p.y / length, p.z / length);
    }
    private static Point add(Point a, Point b) { return new Point(a.x + b.x, a.y + b.y, a.z + b.z); }
    private static Point subtract(Point a, Point b) { return new Point(a.x - b.x, a.y - b.y, a.z - b.z); }
    private static Point scale(Point a, double scale) { return new Point(a.x * scale, a.y * scale, a.z * scale); }
    private static double dot(Point a, Point b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
    private static Point cross(Point a, Point b) {
        return new Point(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
    }
}
