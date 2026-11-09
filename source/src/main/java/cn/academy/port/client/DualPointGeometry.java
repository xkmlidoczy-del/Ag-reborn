package cn.academy.port.client;

import java.util.ArrayList;
import java.util.List;

/** NEW211 narrow Meltdowner geometry. All paths retain their real server hand origins. */
public final class DualPointGeometry {
    public static final double MAX_RANGE = cn.academy.port.highpower.core.DualPointRules.RANGE, NEAR_DEPTH = .20, MAX_HALF_WIDTH = .036, CENTER_CLEAR = .022;
    public static final double EDGE_WIDTH = .032, CORE_WIDTH = .009, PREVIEW_WIDTH = .002;
    public static final int CORE = 0xD8F8D8, EDGE = 0x176B35, PREVIEW = 0x63AC78;
    public static final int MAX_PARTICLES = 32, FLASH_TICKS = 6;
    private static final double EPS = 1e-8;
    private DualPointGeometry() {}
    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public Point plus(Point p) { return new Point(x + p.x, y + p.y, z + p.z); }
        public Point minus(Point p) { return new Point(x - p.x, y - p.y, z - p.z); }
        public Point scale(double n) { return new Point(x * n, y * n, z * n); }
        public double dot(Point p) { return x * p.x + y * p.y + z * p.z; }
        public Point cross(Point p) { return new Point(y * p.z - z * p.y, z * p.x - x * p.z, x * p.y - y * p.x); }
        public double length() { return Math.sqrt(dot(this)); }
        public Point lerp(Point p, double t) { return plus(p.minus(this).scale(t)); }
    }
    public record Segment(Point start, Point end) {}
    public record Basis(Point forward, Point side, Point up) {}
    public record Ribbon(Point start, Point end, Point side, double halfWidth) {}
    public static boolean validPoint(Point p) {
        return p != null && p.finite() && Math.abs(p.x) <= 30_000_000 && Math.abs(p.z) <= 30_000_000 && Math.abs(p.y) <= 4096;
    }
    public static boolean validDirection(Point p) { return p != null && p.finite() && Math.abs(p.length() - 1) <= 1e-5; }
    public static boolean validEndpoints(Point a, Point b) { return validPoint(a) && validPoint(b) && a.minus(b).length() <= MAX_RANGE + 1e-6; }
    public static Basis basis(Point axis) {
        if (axis == null || !axis.finite() || axis.length() < EPS) return null;
        Point forward = axis.scale(1 / axis.length());
        Point side = forward.cross(Math.abs(forward.y) > .9 ? new Point(1, 0, 0) : new Point(0, 1, 0));
        side = side.scale(1 / side.length()); return new Basis(forward, side, side.cross(forward));
    }
    /** Camera projection helper for regression witnesses; never used as hit authority. */
    public static double projectedArea(Ribbon ribbon, Point look, Point cameraUp) {
        if (ribbon == null || cameraUp == null || !cameraUp.finite()) return 0;
        Basis basis = basis(look); if (basis == null) return 0;
        Point side = basis.forward.cross(cameraUp); if (side.length() < EPS) return 0;
        side = side.scale(1 / side.length()); Point up = side.cross(basis.forward);
        Point[] points = {ribbon.start.plus(ribbon.side), ribbon.start.minus(ribbon.side), ribbon.end.minus(ribbon.side), ribbon.end.plus(ribbon.side)};
        double sum = 0;
        for (int i = 0; i < points.length; i++) {
            Point a = points[i], b = points[(i + 1) % points.length];
            double za = a.dot(basis.forward), zb = b.dot(basis.forward);
            if (za <= 0 || zb <= 0) return 0;
            sum += a.dot(side) / za * b.dot(up) / zb - b.dot(side) / zb * a.dot(up) / za;
        }
        return Math.abs(sum) * .5;
    }
    /** The original small-ray blend-in/tile/blend-out split, bounded inside native endpoints. */
    public record BeamPart(Segment segment, int texture) {}
    public static List<BeamPart> beam(Point a, Point b) {
        if (!validEndpoints(a, b) || a.minus(b).length() < EPS) return List.of();
        double fraction = Math.min(.2, .3 / a.minus(b).length());
        Point head = a.lerp(b, fraction), tail = a.lerp(b, 1 - fraction);
        return List.of(new BeamPart(new Segment(a, head), 0), new BeamPart(new Segment(head, tail), 1), new BeamPart(new Segment(tail, b), 2));
    }
    public static final int MARK_RADIUS = 10, MARK_ARM = 4, MARK_MARGIN = 24;
    public record ScreenMark(double x, double y) {}
    public record ScreenStroke(int x1, int y1, int x2, int y2) {}
    /** Clip-space receipt projection. The caller supplies strict current block LOS and the true hand range. */
    public static ScreenMark projectMark(double x, double y, double z, double w, double sourceDistanceSquared,
                                         int width, int height, boolean lineOfSight) {
        if (!lineOfSight || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(w)
                || !Double.isFinite(sourceDistanceSquared) || sourceDistanceSquared < 0 || sourceDistanceSquared > MAX_RANGE * MAX_RANGE
                || w <= NEAR_DEPTH || width <= MARK_MARGIN * 2 || height <= MARK_MARGIN * 2) return null;
        double nx = x / w, ny = y / w, nz = z / w;
        if (nx < -1 || nx > 1 || ny < -1 || ny > 1 || nz < -1 || nz > 1) return null;
        double sx = (nx + 1) * width / 2, sy = (1 - ny) * height / 2;
        // Never clamp an offscreen endpoint into view or draw a partly clipped preparatory label.
        if (sx < MARK_MARGIN || sx > width - MARK_MARGIN || sy < MARK_MARGIN || sy > height - MARK_MARGIN) return null;
        return new ScreenMark(sx, sy);
    }
    /** Four hollow corners in GUI pixels. Their interior leaves the vanilla crosshair unobscured at every GUI scale. */
    public static List<ScreenStroke> screenMark(ScreenMark mark) {
        if (mark == null || !Double.isFinite(mark.x) || !Double.isFinite(mark.y)) return List.of();
        int x = (int)Math.round(mark.x), y = (int)Math.round(mark.y);
        List<ScreenStroke> out = new ArrayList<>(8);
        for (int sx : new int[]{-1, 1}) for (int sy : new int[]{-1, 1}) {
            int cx = x + sx * MARK_RADIUS, cy = y + sy * MARK_RADIUS;
            out.add(new ScreenStroke(Math.min(cx, cx - sx * MARK_ARM), cy, Math.max(cx, cx - sx * MARK_ARM) + 1, cy + 1));
            out.add(new ScreenStroke(cx, Math.min(cy, cy - sy * MARK_ARM), cx + 1, Math.max(cy, cy - sy * MARK_ARM) + 1));
        }
        return List.copyOf(out);
    }
    /** Short local sparks are emitted only for authoritative damage or absorption receipts. */
    public static List<Segment> impact(Point end, Point direction, int age, boolean authoritativeHit) {
        Basis b = basis(direction);
        if (!authoritativeHit || !validPoint(end) || b == null || age < 0 || age >= FLASH_TICKS) return List.of();
        double radius = .045 + age * .006;
        List<Segment> out = new ArrayList<>(4);
        for (Point axis : List.of(b.side, b.up)) for (int sign : new int[]{-1, 1})
            out.add(new Segment(end.plus(axis.scale(sign * radius * .4)), end.plus(axis.scale(sign * radius))));
        return List.copyOf(out);
    }
    /** One unit per emitted quad, shared by every visible context in a frame. */
    public static final class Budget {
        private int remaining = MAX_PARTICLES;
        public boolean take() { if (remaining == 0) return false; remaining--; return true; }
        public boolean reserve(int count) {
            if (count < 0 || count > remaining) return false;
            remaining -= count; return true;
        }
        public int used() { return MAX_PARTICLES - remaining; }
    }
    public static Segment nearClip(Point a, Point b, Point forward, double width) {
        Basis basis = basis(forward);
        if (a == null || b == null || !a.finite() || !b.finite() || a.length() > 256 || b.length() > 256 || basis == null
                || !Double.isFinite(width) || width <= 0 || width > MAX_HALF_WIDTH || a.minus(b).length() > MAX_RANGE + 1e-6) return null;
        double za = a.dot(basis.forward), zb = b.dot(basis.forward), near = NEAR_DEPTH + width;
        if (za < near && zb < near) return null;
        if (za < near) a = a.lerp(b, (near - za) / (zb - za)); else if (zb < near) b = a.lerp(b, (near - za) / (zb - za));
        return a.minus(b).length() < EPS ? null : new Segment(a, b);
    }
    /** Expand the aiming cone by the full ribbon half-width, preserving a genuinely empty center. */
    public static List<Segment> clearCenter(Segment segment, Point forward, Point cameraUp, double width) {
        if (segment == null || forward == null || cameraUp == null || !forward.finite() || !cameraUp.finite()
                || !Double.isFinite(width) || width < 0 || width > MAX_HALF_WIDTH) return List.of();
        Basis fallback = basis(forward); if (fallback == null) return List.of();
        Point f = fallback.forward, side = f.cross(cameraUp);
        if (side.length() < EPS) side = fallback.side; else side = side.scale(1 / side.length()); Point up = side.cross(f);
        Point a = segment.start, b = segment.end; double enter = 0, exit = 1;
        for (Point plane : List.of(side.minus(f.scale(CENTER_CLEAR)), side.scale(-1).minus(f.scale(CENTER_CLEAR)),
                up.minus(f.scale(CENTER_CLEAR)), up.scale(-1).minus(f.scale(CENTER_CLEAR)))) {
            double va = a.dot(plane) - width, vb = b.dot(plane) - width, delta = vb - va;
            if (Math.abs(delta) < EPS) { if (va > 0) return List.of(segment); continue; }
            double crossing = -va / delta; if (delta > 0) exit = Math.min(exit, crossing); else enter = Math.max(enter, crossing);
            if (enter >= exit) return List.of(segment);
        }
        List<Segment> result = new ArrayList<>(2);
        if (enter > EPS) result.add(new Segment(a, a.lerp(b, Math.min(1, enter))));
        if (exit < 1 - EPS) result.add(new Segment(a.lerp(b, Math.max(0, exit)), b));
        return List.copyOf(result);
    }
    public static Ribbon ribbon(Segment segment, Point forward, double width) {
        if (segment == null || !Double.isFinite(width) || width <= 0 || width > MAX_HALF_WIDTH) return null;
        Basis basis = basis(forward); if (basis == null) return null;
        Point direction = segment.end.minus(segment.start); if (direction.length() < EPS) return null;
        Point side = direction.cross(segment.start.scale(-1));
        if (side.length() < EPS) side = direction.cross(basis.up); if (side.length() < EPS) side = basis.side;
        double actual = Math.min(width, Math.min(segment.start.dot(basis.forward), segment.end.dot(basis.forward)) * .012);
        return actual <= 0 ? null : new Ribbon(segment.start, segment.end, side.scale(actual / side.length()), actual);
    }
}
