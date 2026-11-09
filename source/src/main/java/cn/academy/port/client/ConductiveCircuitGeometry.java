package cn.academy.port.client;

import java.util.ArrayList;
import java.util.List;

/** Pure NEW-121 geometry: every vertex remains on the authoritative horizontal surface. */
public final class ConductiveCircuitGeometry {
    public static final double MAX_LENGTH = cn.academy.port.highpower.core.ConductiveCircuitRules.MAX_LENGTH, NEAR_DEPTH = .20, MAX_ANGULAR_HALF_WIDTH = .018;
    public static final double MAX_HALF_WIDTH = .025, JAGGED_OFFSET = .09;
    private static final double EPSILON = 1e-7;

    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public Point plus(Point p) { return new Point(x + p.x, y + p.y, z + p.z); }
        public Point minus(Point p) { return new Point(x - p.x, y - p.y, z - p.z); }
        public Point scale(double factor) { return new Point(x * factor, y * factor, z * factor); }
        public double dot(Point p) { return x * p.x + y * p.y + z * p.z; }
        public double length() { return Math.sqrt(dot(this)); }
        public Point lerp(Point p, double t) { return plus(p.minus(this).scale(t)); }
    }
    public record Segment(Point start, Point end) {}
    public record Ribbon(Point start, Point end, Point side, double halfWidth) {}

    private ConductiveCircuitGeometry() {}

    /** The server sends the surface lift. Do not replace A with the player's hand or eye. */
    public static boolean validEndpoints(Point start, Point end) {
        if (start == null || end == null || !start.finite() || !end.finite()
                || Math.abs(start.y - end.y) > EPSILON) return false;
        if (Math.abs(start.x) > 30_000_000 || Math.abs(start.z) > 30_000_000
                || Math.abs(end.x) > 30_000_000 || Math.abs(end.z) > 30_000_000
                || Math.abs(start.y) > 4096 || Math.abs(end.y) > 4096) return false;
        return start.minus(end).length() <= MAX_LENGTH + EPSILON;
    }

    /** Anchor-only placement is a small cross. Resting A-B is genuinely dashed. */
    public static List<Segment> segments(Point start, Point end, boolean energized, int frame, long token) {
        if (!validEndpoints(start, end)) return List.of();
        double length = start.minus(end).length();
        var result = new ArrayList<Segment>();
        if (length < EPSILON) {
            if (!energized) anchor(result, start);
            return List.copyOf(result);
        }
        if (energized) {
            Point direction = end.minus(start).scale(1 / length);
            Point side = new Point(-direction.z, 0, direction.x);
            int count = Math.max(2, (int) Math.ceil(length / .35));
            Point previous = start;
            for (int i = 1; i <= count; i++) {
                double t = (double) i / count;
                // Deterministic bounded planar displacement; endpoints are always exact A and B.
                double jitter = i == count ? 0 : noise(token, frame, i) * JAGGED_OFFSET * Math.sin(t * Math.PI);
                Point next = i == count ? end : start.lerp(end, t).plus(side.scale(jitter));
                result.add(new Segment(previous, next)); previous = next;
            }
        } else {
            Point direction = end.minus(start).scale(1 / length);
            for (double at = 0; at < length; at += .32)
                result.add(new Segment(start.plus(direction.scale(at)), start.plus(direction.scale(Math.min(length, at + .17)))));
            anchor(result, start); anchor(result, end);
        }
        return List.copyOf(result);
    }

    private static void anchor(List<Segment> out, Point point) {
        out.add(new Segment(point.plus(new Point(-.13, 0, 0)), point.plus(new Point(.13, 0, 0))));
        out.add(new Segment(point.plus(new Point(0, 0, -.13)), point.plus(new Point(0, 0, .13))));
    }

    private static double noise(long token, int frame, int index) {
        long value = token ^ (long) frame * 0x9E3779B97F4A7C15L ^ (long) index * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return ((value >>> 11) * 0x1.0p-53) * 2 - 1;
    }

    /**
     * Camera-relative full-width clipping. The ribbon side is horizontal rather than billboarded,
     * so the final four vertices stay grounded even when the camera looks vertically down.
     */
    public static Ribbon clip(Point start, Point end, Point cameraForward, double halfWidth) {
        if (start == null || end == null || cameraForward == null || !start.finite() || !end.finite()
                || !cameraForward.finite() || Math.abs(start.y - end.y) > EPSILON
                || !Double.isFinite(halfWidth) || halfWidth <= 0 || halfWidth > MAX_HALF_WIDTH) return null;
        double forwardLength = cameraForward.length();
        if (!Double.isFinite(forwardLength) || forwardLength < EPSILON) return null;
        Point forward = cameraForward.scale(1 / forwardLength), delta = end.minus(start);
        double length = delta.length();
        if (!Double.isFinite(length) || length < EPSILON || length > MAX_LENGTH + .5) return null;
        Point side = new Point(-delta.z / length, 0, delta.x / length);
        double a = start.dot(forward), b = end.dot(forward), near = NEAR_DEPTH + halfWidth;
        if (a < near && b < near) return null;
        if (a < near) start = start.lerp(end, (near - a) / (b - a));
        else if (b < near) end = start.lerp(end, (near - a) / (b - a));
        if (start.minus(end).length() < EPSILON) return null;
        double width = Math.min(halfWidth, Math.min(start.dot(forward), end.dot(forward)) * MAX_ANGULAR_HALF_WIDTH);
        return new Ribbon(start, end, side.scale(width), width);
    }
}
