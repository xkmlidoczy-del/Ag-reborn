package cn.academy.port.client;

import java.util.ArrayList;
import java.util.List;

/** Dedicated NEW151 ribbons: true server endpoints, warm rail axes and separate fine blue-white branches. */
public final class RailThunderArrayGeometry {
    public static final double MAX_RANGE = 96, NEAR_DEPTH = .20, MAX_HALF_WIDTH = .055, CENTER_CLEAR = .022;
    public static final int MAX_AXES = 3, MAX_SEGMENTS = 16, MAX_BRANCH_SEGMENTS = 24, MAX_PARTICLES = 128;
    public static final int ORANGE = 0xECAA5D, IVORY = 0xF1F0DE, BLUE = 0x6FAEF2, BLUE_WHITE = 0xDAF3FF;
    public static final double PREVIEW_WIDTH = .0025, RAIL_HALO_WIDTH = .045, RAIL_CORE_WIDTH = .008, BRANCH_WIDTH = .003;
    public static final int PREVIEW_ALPHA = 58, EMISSION_ALPHA = 235;
    private static final double EPS = 1e-8;
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
    /** texture 0/1/2 selects the original railgun blend-in/tile/blend-out asset. */
    public record RailPart(Segment segment, int texture) {}
    private RailThunderArrayGeometry() {}
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
    /** The established virtual wrist prevents eye-origin rays collapsing to zero-area first-person quads.
     * The caller keeps the server origin for permission and LOS; this never changes an accepted endpoint.
     */
    public static Point firstPersonOrigin(Point origin, Point target, Point camera, Point look, Point cameraUp, boolean rightHanded) {
        if (!validEndpoints(origin, target) || !validPoint(camera) || origin.minus(camera).length() > 1.25
                || cameraUp == null || !cameraUp.finite()) return origin;
        Basis basis = basis(look); if (basis == null) return origin;
        Point side = basis.forward.cross(cameraUp);
        if (side.length() < EPS) return origin;
        side = side.scale(1 / side.length()); Point up = side.cross(basis.forward);
        // forward cross camera-up is screen-right; positive lateral displacement therefore means the right hand.
        Point wrist = camera.plus(basis.forward.scale(.65)).plus(side.scale(rightHanded ? .32 : -.32)).plus(up.scale(-.29));
        // A cosmetic launch must not produce a longer-than-range line or manufacture a path behind the eye.
        if (!validEndpoints(wrist, target) || target.minus(camera).dot(basis.forward) <= NEAR_DEPTH + MAX_HALF_WIDTH) return origin;
        return wrist;
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
    /** End caps never extend past the accepted server-clipped endpoints. */
    public static List<RailPart> rail(Point a, Point b) {
        if (!validEndpoints(a, b) || a.minus(b).length() < EPS) return List.of();
        double fraction = Math.min(.2, .7 / a.minus(b).length());
        Point head = a.lerp(b, fraction), tail = a.lerp(b, 1 - fraction);
        return List.of(new RailPart(new Segment(a, head), 0), new RailPart(new Segment(head, tail), 1), new RailPart(new Segment(tail, b), 2));
    }
    /** Tiny non-planar jitter follows an accepted corridor or target segment, never a sphere. */
    public static List<Segment> branch(Point a, Point b, long token, int frame) {
        if (!validEndpoints(a, b)) return List.of(); Basis basis = basis(b.minus(a)); if (basis == null) return List.of();
        double length = a.minus(b).length(), amplitude = Math.min(.045, length * .035);
        int count = Math.max(2, Math.min(MAX_BRANCH_SEGMENTS, (int) Math.ceil(length / 1.5)));
        List<Segment> out = new ArrayList<>(count); Point previous = a;
        for (int i = 1; i <= count; i++) {
            double t = (double) i / count, envelope = Math.sin(t * Math.PI);
            Point next = i == count ? b : a.lerp(b, t).plus(basis.side.scale(noise(token, frame, i) * amplitude * envelope))
                    .plus(basis.up.scale(noise(token ^ 0x31651L, frame, i) * amplitude * .7 * envelope));
            out.add(new Segment(previous, next)); previous = next;
        }
        return List.copyOf(out);
    }
    /** At most two side sparks per confirmed ray, restricted to that exact accepted path. */
    public static List<Segment> sideBranches(Point a, Point b, long token, int frame) {
        if (!validEndpoints(a, b)) return List.of(); Basis basis = basis(b.minus(a)); if (basis == null) return List.of();
        double length = a.minus(b).length(), offset = Math.min(.13, length * .02);
        List<Segment> out = new ArrayList<>();
        for (int sign : new int[] {-1, 1}) {
            Point start = a.lerp(b, sign < 0 ? .35 : .66), end = start.plus(basis.side.scale(offset * sign)).plus(basis.up.scale(offset * .35));
            out.addAll(branch(start, end, token + sign, frame));
        }
        return List.copyOf(out);
    }
    public static List<Segment> hit(Point p, Point direction, int age) {
        Basis basis = basis(direction); if (!validPoint(p) || basis == null || age < 0 || age >= RailThunderArrayState.FLASH_TICKS) return List.of();
        double size = .075 + age * .012; List<Segment> out = new ArrayList<>(4);
        for (Point axis : List.of(basis.side, basis.up)) for (int sign : new int[] {-1, 1})
            out.add(new Segment(p.plus(axis.scale(sign * size * .4)), p.plus(axis.scale(sign * size))));
        return List.copyOf(out);
    }
    private static double noise(long token, int frame, int i) {
        long v = token * 0x9E3779B97F4A7C15L + frame * 0x632BE59BD9B4E019L + i * 0x94D049BB133111EBL;
        v = (v ^ (v >>> 30)) * 0xBF58476D1CE4E5B9L; v = (v ^ (v >>> 27)) * 0x94D049BB133111EBL;
        return ((v ^ (v >>> 31)) >>> 11) * 0x1.0p-53 * 2 - 1;
    }
    public static Segment nearClip(Point a, Point b, Point forward, double width) {
        Basis basis = basis(forward);
        if (a == null || b == null || !a.finite() || !b.finite() || a.length() > 256 || b.length() > 256 || basis == null
                || !Double.isFinite(width) || width <= 0 || width > MAX_HALF_WIDTH || a.minus(b).length() > MAX_RANGE + .3) return null;
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
