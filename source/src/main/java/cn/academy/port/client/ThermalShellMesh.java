package cn.academy.port.client;

import java.util.ArrayList;
import java.util.List;

/** NEW222's four broken close-body rings. All output is cosmetic, thin, finite world geometry. */
public final class ThermalShellMesh {
    public static final int RINGS = 4, DASHES = 8, ARC_STEPS = 3;
    public static final int MAX_SEGMENTS = 96, MAX_MESH_QUADS = 256, GRAINS_PER_HIT = 8, SPAWNED_PARTICLES = 0;
    public static final int CORE = 0xE5FFE8, GREEN = 0x8EEA9B;
    public static final double SHELL_RADIUS = .68, PULSE_RANGE = cn.academy.port.highpower.core.ThermalShellRules.RANGE, NEAR_DEPTH = .12, MAX_HALF_WIDTH = .008;
    public static final double ARC_ANGLE = .34;
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
    public record Segment(Point start, Point end, int band) {}
    public record Basis(Point forward, Point side, Point up) {}
    public record Ribbon(Point start, Point end, Point side, double halfWidth) {}
    public record Grain(Point point, double size) {}
    private ThermalShellMesh() {}
    public static boolean validPoint(Point p) {
        return p != null && p.finite() && Math.abs(p.x) <= 30_000_000 && Math.abs(p.z) <= 30_000_000 && Math.abs(p.y) <= 4096;
    }
    public static boolean validDirection(Point p) { return p != null && p.finite() && Math.abs(p.length() - 1) <= .001; }
    public static Basis basis(Point direction) {
        if (direction == null || !direction.finite() || direction.length() < EPS) return null;
        Point forward = direction.scale(1 / direction.length());
        Point side = forward.cross(Math.abs(forward.y) > .9 ? new Point(1, 0, 0) : new Point(0, 1, 0));
        side = side.scale(1 / side.length());
        return new Basis(forward, side, side.cross(forward));
    }
    /** Eight visual gaps per ring remain open from every view; these cosmetic rings grant no protection. */
    public static List<Segment> rings(Point center, Point facing, long token, int frame) {
        if (!validPoint(center) || !validDirection(facing) || token <= 0 || frame < 0) return List.of();
        double yaw = Math.hypot(facing.x, facing.z) < EPS ? 0 : Math.atan2(facing.x, facing.z);
        List<Segment> result = new ArrayList<>(MAX_SEGMENTS);
        for (int ring = 0; ring < RINGS; ring++) {
            double height = (ring - 1.5) * .4;
            // A tiny shared turn reads as heat shimmer while preserving the visible gaps.
            double shimmer = Math.sin(frame * .05 + ring * .7 + token % 31) * .025;
            for (int dash = 0; dash < DASHES; dash++) {
                double start = yaw + dash * Math.PI / 4 + (Math.PI / 4 - ARC_ANGLE) / 2 + shimmer;
                for (int step = 0; step < ARC_STEPS; step++) {
                    double a = start + ARC_ANGLE * step / ARC_STEPS, b = start + ARC_ANGLE * (step + 1) / ARC_STEPS;
                    result.add(new Segment(cylinder(center, a, height), cylinder(center, b, height), ring));
                }
            }
        }
        return List.copyOf(result);
    }
    private static Point cylinder(Point center, double angle, double height) {
        return center.plus(new Point(Math.sin(angle) * SHELL_RADIUS, height, Math.cos(angle) * SHELL_RADIUS));
    }
    /** Short contact-local spokes are created only from the server's committed absorption receipt. */
    public static List<Segment> hit(Point impact, Point outward, long salt, int age) {
        Basis basis = basis(outward);
        if (!validPoint(impact) || basis == null || age < 0 || age >= ThermalShellState.HIT_TICKS) return List.of();
        List<Segment> result = new ArrayList<>(8);
        for (int arm = 0; arm < 8; arm++) {
            double angle = arm * Math.PI / 4 + noise(salt, arm) * .10;
            Point tangent = basis.side.scale(Math.cos(angle)).plus(basis.up.scale(Math.sin(angle)));
            double length = .075 + (noise(salt, arm + 11) + 1) * .012;
            result.add(new Segment(impact.plus(tangent.scale(.012)), impact.plus(tangent.scale(length)), -1));
        }
        return List.copyOf(result);
    }
    public static List<Grain> grains(Point impact, Point outward, long salt, int age) {
        Basis basis = basis(outward);
        if (!validPoint(impact) || basis == null || age < 0 || age >= ThermalShellState.HIT_TICKS) return List.of();
        List<Grain> result = new ArrayList<>(GRAINS_PER_HIT);
        for (int i = 0; i < GRAINS_PER_HIT; i++) {
            double angle = i * 2.3999632297 + noise(salt, i), radius = .025 + age * .019;
            result.add(new Grain(impact.plus(basis.side.scale(Math.cos(angle) * radius)).plus(basis.up.scale(Math.sin(angle) * radius)), .004));
        }
        return List.copyOf(result);
    }
    public static double pulseRadius(int age) {
        return age < 0 || age >= ThermalShellState.PULSE_TICKS ? 0
                : SHELL_RADIUS + (PULSE_RANGE - SHELL_RADIUS) * age / (ThermalShellState.PULSE_TICKS - 1.0);
    }
    /** Sparse outward fragments, never a filled wall, target-hit claim, or terrain destruction cue. */
    public static List<Segment> pulse(Point center, int age) {
        double radius = pulseRadius(age);
        if (!validPoint(center) || radius == 0) return List.of();
        List<Segment> result = new ArrayList<>(32);
        for (int band = 0; band < 2; band++) {
            double latitude = band == 0 ? -.20 : .20;
            for (int dash = 0; dash < 8; dash++) {
                double angle = dash * Math.PI / 4 + band * .12;
                for (int step = 0; step < 2; step++)
                    result.add(new Segment(sphere(center, radius, angle + step * .06, latitude),
                            sphere(center, radius, angle + (step + 1) * .06, latitude), -2));
            }
        }
        return List.copyOf(result);
    }
    private static Point sphere(Point center, double radius, double longitude, double latitude) {
        return center.plus(new Point(Math.sin(longitude) * Math.cos(latitude) * radius,
                Math.sin(latitude) * radius, Math.cos(longitude) * Math.cos(latitude) * radius));
    }
    private static double noise(long salt, int index) {
        long value = salt ^ index * 0x9E3779B97F4A7C15L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return ((value ^ (value >>> 31)) >>> 11) * 0x1.0p-53 * 2 - 1;
    }
    /** Near-plane clipping never relocates geometry or fattens it to a minimum screen width. */
    public static Segment nearClip(Point a, Point b, Point forward, double width) {
        Basis basis = basis(forward);
        if (a == null || b == null || !a.finite() || !b.finite() || a.length() > 160 || b.length() > 160 || basis == null
                || !Double.isFinite(width) || width <= 0 || width > MAX_HALF_WIDTH || a.minus(b).length() > .35) return null;
        double za = a.dot(basis.forward), zb = b.dot(basis.forward), near = NEAR_DEPTH + width;
        if (za < near && zb < near) return null;
        if (za < near) a = a.lerp(b, (near - za) / (zb - za));
        else if (zb < near) b = a.lerp(b, (near - za) / (zb - za));
        return a.minus(b).length() < EPS ? null : new Segment(a, b, -3);
    }
    public static Ribbon ribbon(Segment segment, Point forward, double width) {
        Basis basis = basis(forward);
        if (segment == null || basis == null || !Double.isFinite(width) || width <= 0 || width > MAX_HALF_WIDTH) return null;
        Point a = segment.start, b = segment.end;
        if (a == null || b == null || !a.finite() || !b.finite() || a.minus(b).length() > .35) return null;
        double depth = Math.min(a.dot(basis.forward), b.dot(basis.forward));
        if (depth < NEAR_DEPTH || a.minus(b).length() < EPS) return null;
        Point direction = b.minus(a), side = direction.cross(a.scale(-1));
        if (side.length() < EPS) side = direction.cross(basis.up);
        if (side.length() < EPS) side = basis.side;
        // Perspective foreshortening at a ring edge must only shrink the true ribbon. Bounding
        // transverse/depth also protects off-axis near-plane edges from becoming camera wedges.
        double actual = width;
        for (Point endpoint : List.of(a, b)) {
            double z = endpoint.dot(basis.forward);
            double transverse = endpoint.minus(basis.forward.scale(z)).length();
            actual = Math.min(actual, z * .0075 / (1 + transverse / z));
        }
        return new Ribbon(a, b, side.scale(actual / side.length()), actual);
    }
    /** Each visible controller owns a strict post-clipping geometry budget and no particle entities. */
    public static final class Budget {
        private int used;
        public boolean take() { if (used >= MAX_MESH_QUADS) return false; used++; return true; }
        public int used() { return used; }
    }
}
