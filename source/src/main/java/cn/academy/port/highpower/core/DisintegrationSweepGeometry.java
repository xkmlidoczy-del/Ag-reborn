package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import cn.academy.port.highpower.core.FoldedMirrorGeometry.Bounds;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;

/**
 * Continuous NEW231 geometry. AABB/plane sections are clipped by exact half-planes and two
 * range circles. Angular extrema come from polygon vertices, circle intersections and circle
 * tangencies, never a selection of visual rays. Minecraft still owns nearest solid/body contact.
 *
 * All narrow-phase work is relative to the actual hand, avoiding cancellation of large world
 * coordinates in dot products. No geometric epsilon expands the admitted sector or range.
 * Outward representable rounding occurs only in broad-phase native query bounds.
 */
public final class DisintegrationSweepGeometry {
    public static final double MAX_RANGE = 48, BEAM_RADIUS = .035, MAX_ORIGIN_SHIFT = .25;
    public static final double HALF_ANGLE = Math.toRadians(25);
    private static final double SIN = Math.sin(HALF_ANGLE), COS = Math.cos(HALF_ANGLE);
    private static final double UNIT_TOLERANCE = 1e-10;

    private DisintegrationSweepGeometry() {}

    public record FrozenPlane(Vector origin, Vector forward, Vector across) {
        public FrozenPlane {
            if (!valid(origin) || !unit(forward) || !unit(across) || Math.abs(forward.dot(across)) > UNIT_TOLERANCE)
                throw new IllegalArgumentException("Invalid NEW231 frozen plane");
        }
        public Vector normal() { return forward.cross(across); }
    }
    public record Contact(double angle, double distance, Vector point, Vector direction) {}
    public record AngleInterval(double from, double to) {
        public AngleInterval {
            if (!angle(from) || !angle(to) || from > to) throw new IllegalArgumentException("Invalid NEW231 angular interval");
        }
    }
    /** The caller supplies native identity; angular/distance ties use the stable native id. */
    public record Candidate(int identity, Bounds bounds, Contact contact) {}
    public static final Comparator<Candidate> CONTACT_ORDER = Comparator
            .comparingDouble((Candidate value) -> value.contact.angle)
            .thenComparingDouble(value -> value.contact.distance).thenComparingInt(Candidate::identity);

    /**
     * Minecraft yaw is supplied in radians. Away from vertical look, right = forward x world-up;
     * at the exact singularity the release yaw determines right. Vertical mode uses right x forward.
     */
    public static Optional<FrozenPlane> freeze(Vector origin, Vector look, double yawRadians, boolean vertical) {
        if (!valid(origin) || !Double.isFinite(yawRadians)) return Optional.empty();
        Optional<Vector> normalized = FoldedMirrorGeometry.unit(look);
        if (normalized.isEmpty()) return Optional.empty();
        Vector f = normalized.get();
        Vector right = FoldedMirrorGeometry.unit(new Vector(-f.z(), 0, f.x())).orElseGet(
                () -> new Vector(-Math.cos(yawRadians), 0, -Math.sin(yawRadians)));
        Vector across = vertical ? FoldedMirrorGeometry.unit(right.cross(f)).orElse(null) : right;
        if (across == null) return Optional.empty();
        return Optional.of(new FrozenPlane(origin, f, across));
    }

    public static Vector direction(FrozenPlane plane, double angle) {
        if (plane == null || !angle(angle)) throw new IllegalArgumentException("Invalid NEW231 direction");
        return plane.forward.scale(Math.cos(angle)).add(plane.across.scale(Math.sin(angle)));
    }

    public static boolean permittedOrigin(FrozenPlane plane, Vector hand) {
        return plane != null && valid(hand) && hand.subtract(plane.origin).length() <= MAX_ORIGIN_SHIFT;
    }

    /**
     * Centerline footprint in the ORIGINAL release frame. Normal drift is restricted to the
     * original .035m slab, in addition to the .25m real-hand leash. Neither origin nor endpoint is
     * projected, steered, or snapped. The controller must also test its actual native hit point.
     */
    public static boolean withinFrozenFootprint(FrozenPlane plane, Vector point) {
        if (plane == null || !valid(point)) return false;
        Vector delta = point.subtract(plane.origin);
        double f = delta.dot(plane.forward), a = delta.dot(plane.across), n = delta.dot(plane.normal());
        return delta.length() <= MAX_RANGE && Math.abs(n) <= BEAM_RADIUS
                && f * SIN - a * COS >= 0 && f * SIN + a * COS >= 0;
    }

    /** Native lookup volume only. Membership in this AABB never grants a collision. */
    public static Bounds queryBounds(FrozenPlane plane) {
        if (plane == null) throw new IllegalArgumentException("Missing NEW231 plane");
        double[] low = new double[3], high = new double[3];
        Vector n = plane.normal();
        for (int axis = 0; axis < 3; axis++) {
            double f = component(plane.forward, axis), a = component(plane.across, axis);
            double lower = Math.min(0, Math.min(f * COS - a * SIN, f * COS + a * SIN));
            double upper = Math.max(0, Math.max(f * COS - a * SIN, f * COS + a * SIN));
            double critical = Math.atan2(a, f);
            for (int k = -1; k <= 1; k++) {
                double t = critical + k * Math.PI;
                if (t >= -HALF_ANGLE && t <= HALF_ANGLE) {
                    double support = f * Math.cos(t) + a * Math.sin(t);
                    lower = Math.min(lower, support); upper = Math.max(upper, support);
                }
            }
            // One radius for a shifted admissible centerline slab, one for inflated native AABBs.
            double margin = BEAM_RADIUS * (1 + Math.abs(component(n, axis)));
            low[axis] = Math.nextDown(component(plane.origin, axis) + MAX_RANGE * lower - margin);
            high[axis] = Math.nextUp(component(plane.origin, axis) + MAX_RANGE * upper + margin);
        }
        return new Bounds(vector(low), vector(high));
    }

    /** Short-prefix query used BEFORE any native world or entity read. */
    public static Optional<Bounds> segmentQueryBounds(Vector start, Vector end) {
        if (!valid(start) || !valid(end) || start.subtract(end).length() > .25) return Optional.empty();
        return Optional.of(new Bounds(new Vector(Math.nextDown(Math.min(start.x(), end.x()) - BEAM_RADIUS),
                Math.nextDown(Math.min(start.y(), end.y()) - BEAM_RADIUS), Math.nextDown(Math.min(start.z(), end.z()) - BEAM_RADIUS)),
                new Vector(Math.nextUp(Math.max(start.x(), end.x()) + BEAM_RADIUS),
                Math.nextUp(Math.max(start.y(), end.y()) + BEAM_RADIUS), Math.nextUp(Math.max(start.z(), end.z()) + BEAM_RADIUS))));
    }

    public static Optional<Contact> contactAt(FrozenPlane plane, Vector hand, Bounds body, double angle) {
        Region region = region(plane, hand, body, angle, angle);
        return region == null ? Optional.empty() : rayContact(region, angle);
    }

    /**
     * Farthest permitted native-ray endpoint. The entire segment starts at the REAL hand and
     * stays in the original convex footprint; an out-of-footprint hand is refused, never moved.
     * Final world-coordinate addition is rounded inward before any native query may use it.
     */
    public static Optional<Vector> rayEndpoint(FrozenPlane plane, Vector hand, double angle) {
        if (!withinFrozenFootprint(plane, hand)) return Optional.empty();
        Region region = region(plane, hand, queryBounds(plane), angle, angle);
        if (region == null) return Optional.empty();
        double[] interval = rayInterval(region, angle);
        if (interval == null) return Optional.empty();
        Vector end = hand.add(direction(plane, angle).scale(interval[1]));
        for (int i = 0; i < 64; i++) {
            if (withinFrozenFootprint(plane, end) && end.subtract(hand).length() <= MAX_RANGE)
                return Optional.of(end);
            end = new Vector(Math.nextAfter(end.x(), hand.x()), Math.nextAfter(end.y(), hand.y()), Math.nextAfter(end.z(), hand.z()));
        }
        return Optional.empty();
    }

    /** Earliest true centerline ray touching this AABB section in the closed swept interval. */
    public static Optional<Contact> firstContact(FrozenPlane plane, Vector hand, Bounds body, double fromAngle, double toAngle) {
        Region region = region(plane, hand, body, fromAngle, toAngle);
        if (region == null) return Optional.empty();
        List<AngleInterval> intervals = intervals(region);
        if (intervals.isEmpty()) return Optional.empty();
        return rayContact(region, intervals.getFirst().from);
    }

    /** Convex section of a <180-degree fan has at most one angular interval. */
    public static List<AngleInterval> contactIntervals(FrozenPlane plane, Vector hand, Bounds body, double fromAngle, double toAngle) {
        Region region = region(plane, hand, body, fromAngle, toAngle);
        return region == null ? List.of() : intervals(region);
    }

    /** A candidate only, for a native miss at an analytically admitted boundary. Repeated
     * angle nextUp calls can produce the same world endpoint millions of times at large
     * coordinates. The coordinate-ULP budget covers representable origin, endpoint and
     * short-prefix rounding; it never changes any body, cone, plane or range constraint.
     * Every proposed angle must still pass contactAt, rayEndpoint and the unchanged native
     * nearest-body/solid trace. Degenerate intervals or insufficient precision fail closed.
     */
    public static OptionalDouble nativeInteriorCandidate(FrozenPlane plane, Vector hand, Bounds body,
                                                         Contact boundary, double toAngle, int retry) {
        if (boundary == null || retry < 0 || retry >= 4 || !bounds(body) || !permittedOrigin(plane, hand)
                || !angle(boundary.angle) || !angle(toAngle) || boundary.angle >= toAngle
                || !(boundary.distance > 0) || !Double.isFinite(boundary.distance)) return OptionalDouble.empty();
        List<AngleInterval> intervals = contactIntervals(plane, hand, body, boundary.angle, toAngle);
        AngleInterval containing = null;
        for (var interval : intervals) if (interval.from <= boundary.angle && interval.to > boundary.angle) { containing = interval; break; }
        if (containing == null) return OptionalDouble.empty();
        double coordinateUlp = 0;
        for (int axis = 0; axis < 3; axis++) {
            // Bounds the rounding scale of every endpoint on this at-most48m native ray.
            coordinateUlp = Math.max(coordinateUlp, Math.ulp(Math.abs(component(hand, axis)) + MAX_RANGE));
            coordinateUlp = Math.max(coordinateUlp, Math.ulp(component(body.min(), axis)));
            coordinateUlp = Math.max(coordinateUlp, Math.ulp(component(body.max(), axis)));
        }
        double uncertainty = Math.scalb(coordinateUlp, 3 + retry);
        if (!Double.isFinite(uncertainty) || !(boundary.distance > uncertainty)) return OptionalDouble.empty();
        double step = Math.atan2(uncertainty, boundary.distance);
        double midpoint = boundary.angle + (containing.to - boundary.angle) / 2;
        double candidate = Math.min(boundary.angle + step, midpoint);
        // No representable strict interior means no verified replacement ray exists here.
        if (!(candidate > boundary.angle) || !(candidate < containing.to) || candidate > toAngle
                || contactAt(plane, hand, body, candidate).isEmpty()) return OptionalDouble.empty();
        return OptionalDouble.of(candidate);
    }

    private record Point(double x, double y) {
        Point add(Point p) { return new Point(x + p.x, y + p.y); }
        Point scale(double scale) { return new Point(x * scale, y * scale); }
        Point subtract(Point p) { return new Point(x - p.x, y - p.y); }
        double dot(Point p) { return x * p.x + y * p.y; }
        double norm() { return Math.hypot(x, y); }
    }
    private record Line(double x, double y, double limit) {
        double at(Point p) { return x * p.x + y * p.y - limit; }
    }
    private record Circle(Point center, double radius) {}
    private record Region(FrozenPlane plane, Vector hand, List<Line> lines, List<Circle> circles, double from, double to) {}

    private static Region region(FrozenPlane plane, Vector hand, Bounds box, double from, double to) {
        if (!permittedOrigin(plane, hand) || !bounds(box) || !angle(from) || !angle(to) || from > to) return null;
        Vector delta = hand.subtract(plane.origin);
        double normal = delta.dot(plane.normal());
        if (Math.abs(normal) > BEAM_RADIUS) return null;
        List<Line> lines = new ArrayList<>(10);
        for (int i = 0; i < 3; i++) {
            double f = component(plane.forward, i), a = component(plane.across, i), h = component(hand, i);
            lines.add(new Line(f, a, component(box.max(), i) + BEAM_RADIUS - h));
            lines.add(new Line(-f, -a, h - (component(box.min(), i) - BEAM_RADIUS)));
        }
        double df = delta.dot(plane.forward), da = delta.dot(plane.across);
        lines.add(new Line(-SIN, COS, df * SIN - da * COS));
        lines.add(new Line(-SIN, -COS, df * SIN + da * COS));
        lines.add(new Line(Math.sin(from), -Math.cos(from), 0));
        lines.add(new Line(-Math.sin(to), Math.cos(to), 0));
        return new Region(plane, hand, List.copyOf(lines),
                List.of(new Circle(new Point(0, 0), MAX_RANGE),
                        new Circle(new Point(-df, -da), Math.sqrt(MAX_RANGE * MAX_RANGE - normal * normal))), from, to);
    }

    private static Optional<Contact> rayContact(Region r, double angle) {
        double[] interval = rayInterval(r, angle);
        if (interval == null) return Optional.empty();
        double near = interval[0];
        Vector direction = direction(r.plane, angle), point = r.hand.add(direction.scale(near));
        if (!valid(point)) return Optional.empty();
        return Optional.of(new Contact(angle, near == 0 ? 0 : near, point, direction));
    }

    private static double[] rayInterval(Region r, double angle) {
        double x = Math.cos(angle), y = Math.sin(angle), near = 0, far = MAX_RANGE;
        for (Line line : r.lines) {
            double slope = line.x * x + line.y * y;
            if (slope == 0) { if (line.limit < 0) return null; }
            else if (slope > 0) far = Math.min(far, line.limit / slope);
            else near = Math.max(near, line.limit / slope);
            if (near > far) return null;
        }
        for (Circle circle : r.circles) {
            double projected = circle.center.x * x + circle.center.y * y;
            double cross = circle.center.x * y - circle.center.y * x;
            double square = circle.radius * circle.radius - cross * cross;
            if (square < 0) return null;
            double root = Math.sqrt(square);
            near = Math.max(near, projected - root); far = Math.min(far, projected + root);
            if (near > far) return null;
        }
        return near >= 0 && near <= MAX_RANGE ? new double[]{near, far} : null;
    }

    private static List<AngleInterval> intervals(Region r) {
        Optional<Contact> lower = rayContact(r, r.from), upper = rayContact(r, r.to);
        if (lower.isPresent() && upper.isPresent()) return List.of(new AngleInterval(r.from, r.to));
        List<Point> polygon = new ArrayList<>(List.of(new Point(-MAX_RANGE, -MAX_RANGE), new Point(MAX_RANGE, -MAX_RANGE),
                new Point(MAX_RANGE, MAX_RANGE), new Point(-MAX_RANGE, MAX_RANGE)));
        for (Line line : r.lines) {
            polygon = clip(polygon, line);
            if (polygon.isEmpty()) return List.of();
        }
        List<Double> angles = new ArrayList<>();
        if (lower.isPresent()) angles.add(r.from);
        if (upper.isPresent()) angles.add(r.to);
        for (int i = 0; i < polygon.size(); i++) {
            Point p = polygon.get(i), q = polygon.get((i + 1) % polygon.size());
            addAngle(r, p, angles);
            for (Circle circle : r.circles) {
                for (Point hit : circleLine(p, q, circle)) addAngle(r, hit, angles);
            }
        }
        for (Point p : circleCircle(r.circles.get(0), r.circles.get(1))) addAngle(r, p, angles);
        for (Circle circle : r.circles) {
            double distance = circle.center.norm();
            if (distance > circle.radius) {
                double base = Math.atan2(circle.center.y, circle.center.x), spread = Math.asin(circle.radius / distance);
                addAngle(r, new Point(Math.cos(base - spread), Math.sin(base - spread)).scale(Math.sqrt(distance * distance - circle.radius * circle.radius)), angles);
                addAngle(r, new Point(Math.cos(base + spread), Math.sin(base + spread)).scale(Math.sqrt(distance * distance - circle.radius * circle.radius)), angles);
            }
        }
        if (angles.isEmpty()) return List.of();
        angles.sort(Double::compare);
        double from = angles.getFirst(), to = angles.getLast();
        // Polygon projection and atan2 reconstruction can leave a boundary ray a few ULPs
        // outside its real face, especially for a nearly parallel AABB edge. Find one actual
        // admitted ray, then solve each closed boundary only INWARD from the analytic extrema.
        double seed = from + (to - from) / 2;
        if (rayContact(r, seed).isEmpty()) {
            seed = Double.NaN;
            for (double candidate : angles) if (rayContact(r, candidate).isPresent()) { seed = candidate; break; }
        }
        if (!Double.isFinite(seed)) return List.of();
        double adjustedFrom = admittedAngle(r, from, seed), adjustedTo = admittedAngle(r, to, seed);
        if (Double.isNaN(adjustedFrom) || Double.isNaN(adjustedTo) || adjustedFrom > adjustedTo) return List.of();
        return List.of(new AngleInterval(adjustedFrom, adjustedTo));
    }

    private static double admittedAngle(Region r, double angle, double toward) {
        if (rayContact(r, angle).isPresent()) return angle;
        if (rayContact(r, toward).isEmpty()) return Double.NaN;
        // This is bracketed boundary refinement of an analytically known convex interval,
        // not an angular sampling approximation: the returned endpoint is an admitted ray.
        for (int i = 0; i < 64; i++) {
            double middle = angle + (toward - angle) / 2;
            if (middle == angle || middle == toward) break;
            if (rayContact(r, middle).isPresent()) toward = middle; else angle = middle;
        }
        return toward;
    }

    private static void addAngle(Region r, Point point, List<Double> angles) {
        if (point.norm() == 0) {
            if (rayContact(r, r.from).isPresent()) angles.add(r.from);
            if (rayContact(r, r.to).isPresent()) angles.add(r.to);
            return;
        }
        double angle = Math.atan2(point.y, point.x);
        if (angle < r.from || angle > r.to) return;
        // Verify with the native-compatible ray interval, including both range caps. A computed
        // boundary witness may round outward; neighboring interior angles are checked below.
        if (rayContact(r, angle).isPresent()) { angles.add(angle); return; }
        // This tolerance ONLY retains a numerical boundary witness for inward refinement.
        // It never admits a contact or expands its range: final endpoints pass rayInterval.
        double uncertainty = 128 * Math.ulp(MAX_RANGE);
        for (Line line : r.lines) if (line.at(point) > uncertainty) return;
        for (Circle circle : r.circles) if (point.subtract(circle.center).norm() - circle.radius > uncertainty) return;
        angles.add(angle);
    }

    private static List<Point> clip(List<Point> input, Line line) {
        if (input.isEmpty()) return input;
        List<Point> result = new ArrayList<>(input.size() + 1);
        Point before = input.getLast(); double previous = line.at(before);
        for (Point point : input) {
            double now = line.at(point);
            if ((previous <= 0) != (now <= 0)) {
                double fraction = previous / (previous - now);
                Point intersection = before.add(point.subtract(before).scale(fraction));
                // Reconstruct the dominant coordinate from the actual face equation. In
                // particular a z=48 tangent must not become z=nextDown(48) by interpolation.
                intersection = Math.abs(line.x) >= Math.abs(line.y)
                        ? new Point((line.limit - line.y * intersection.y) / line.x, intersection.y)
                        : new Point(intersection.x, (line.limit - line.x * intersection.x) / line.y);
                result.add(intersection);
            }
            if (now <= 0) result.add(point);
            before = point; previous = now;
        }
        return result;
    }

    private static List<Point> circleLine(Point start, Point end, Circle circle) {
        Point delta = end.subtract(start), relative = start.subtract(circle.center);
        double aa = delta.dot(delta);
        if (aa == 0) return List.of();
        double bb = relative.dot(delta), cc = relative.dot(relative) - circle.radius * circle.radius;
        double discriminant = bb * bb - aa * cc;
        if (Math.abs(discriminant) <= 16 * Math.ulp(Math.abs(bb * bb)
                + aa * (relative.dot(relative) + circle.radius * circle.radius))) {
            // The usual quadratic loses the entire discriminant for a circle tangent. Recompute
            // this rare predicate exactly from its bounded binary-double inputs, not with epsilon.
            BigDecimal dx = decimal(delta.x), dy = decimal(delta.y), rx = decimal(relative.x), ry = decimal(relative.y);
            BigDecimal exactA = dx.multiply(dx).add(dy.multiply(dy));
            BigDecimal exactB = rx.multiply(dx).add(ry.multiply(dy));
            BigDecimal radius = decimal(circle.radius);
            BigDecimal exactC = rx.multiply(rx).add(ry.multiply(ry)).subtract(radius.multiply(radius));
            discriminant = exactB.multiply(exactB).subtract(exactA.multiply(exactC)).doubleValue();
        }
        if (discriminant < 0) return List.of();
        if (discriminant == 0) {
            double tangent = -bb / aa;
            return tangent >= 0 && tangent <= 1 ? List.of(start.add(delta.scale(tangent))) : List.of();
        }
        double root = Math.sqrt(discriminant);
        List<Point> points = new ArrayList<>(2);
        // Stable quadratic roots retain the small root when an endpoint is near a circle.
        double q = -bb - Math.copySign(root, bb);
        double first = q / aa, second = q == 0 ? -bb / aa : cc / q;
        if (first >= 0 && first <= 1) points.add(start.add(delta.scale(first)));
        if (second >= 0 && second <= 1 && second != first) points.add(start.add(delta.scale(second)));
        return points;
    }

    private static List<Point> circleCircle(Circle first, Circle second) {
        Point difference = second.center.subtract(first.center); double distance = difference.norm();
        if (distance == 0 || distance > first.radius + second.radius || distance < Math.abs(first.radius - second.radius)) return List.of();
        double along = (first.radius * first.radius - second.radius * second.radius + distance * distance) / (2 * distance);
        double square = first.radius * first.radius - along * along;
        if (square < 0) return List.of();
        Point direction = difference.scale(1 / distance), middle = first.center.add(direction.scale(along));
        Point offset = new Point(-direction.y, direction.x).scale(Math.sqrt(square));
        return square == 0 ? List.of(middle) : List.of(middle.add(offset), middle.subtract(offset));
    }

    private static boolean valid(Vector vector) { return vector != null && vector.finite(); }
    private static boolean unit(Vector vector) { return valid(vector) && Math.abs(vector.length() - 1) <= UNIT_TOLERANCE; }
    private static boolean angle(double angle) { return Double.isFinite(angle) && angle >= -HALF_ANGLE && angle <= HALF_ANGLE; }
    private static boolean bounds(Bounds bounds) {
        return bounds != null && valid(bounds.min()) && valid(bounds.max()) && bounds.min().x() <= bounds.max().x()
                && bounds.min().y() <= bounds.max().y() && bounds.min().z() <= bounds.max().z();
    }
    private static double component(Vector vector, int axis) { return axis == 0 ? vector.x() : axis == 1 ? vector.y() : vector.z(); }
    private static Vector vector(double[] values) { return new Vector(values[0], values[1], values[2]); }
    private static BigDecimal decimal(double value) { return new BigDecimal(value); }
}
