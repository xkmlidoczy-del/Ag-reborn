package cn.academy.port.client.vfx2;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless, visual-only auxiliary geometry for an already accepted classic Railgun beam.
 * The caller supplies the classic beam's current visible endpoints, width and pause-aware age.
 * Positions remain doubles and are camera-relative before any renderer converts them to floats.
 * This class neither owns beams nor advances their clock, range, gameplay or lifetime.
 */
public final class RailgunOverlayGeometry {
    public static final double LIFETIME_MILLIS = 2500.0;
    public static final double HALO_HALF_WIDTH = 1.3;
    public static final double MAX_HALO_HALF_WIDTH = 1.6;
    public static final double HALO_ALPHA = .16;
    public static final double FILAMENT_HALF_WIDTH = .028;
    public static final double MAX_FILAMENT_HALF_WIDTH = .05;
    public static final double FILAMENT_ALPHA = .13;
    public static final int MAX_FILAMENT_SEGMENTS = 6;
    public static final double MAX_FILAMENT_LENGTH = 6.0;
    /** Bound the transverse size close to the camera without changing the beam's endpoints. */
    public static final double MAX_ANGULAR_HALF_WIDTH = .055;
    private static final double MIN_LENGTH = 1.0E-6;
    private static final double BASIS_EPSILON = 1.0E-8;
    private static final double TWO_PI = Math.PI * 2.0;

    public enum Quality {
        OFF(0, 0), LOW(12, 0), MEDIUM(24, 2);

        private final int haloSegments;
        private final int filaments;

        Quality(int haloSegments, int filaments) {
            this.haloSegments = haloSegments;
            this.filaments = filaments;
        }

        public int haloSegments() { return haloSegments; }
        public int filaments() { return filaments; }
        public int maxQuads() { return haloSegments + filaments * MAX_FILAMENT_SEGMENTS; }
        public int maxVertices() { return maxQuads() * 4; }
    }

    public enum Layer { HALO, FILAMENT }

    public record Vec(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public Vec add(Vec other) { return new Vec(x + other.x, y + other.y, z + other.z); }
        public Vec subtract(Vec other) { return new Vec(x - other.x, y - other.y, z - other.z); }
        public Vec scale(double value) { return new Vec(x * value, y * value, z * value); }
        public double dot(Vec other) { return x * other.x + y * other.y + z * other.z; }
        public Vec cross(Vec other) {
            return new Vec(y * other.z - z * other.y, z * other.x - x * other.z,
                    x * other.y - y * other.x);
        }
        public double length() { return Math.hypot(x, Math.hypot(y, z)); }
    }

    /** u covers the supplied beam/filament once; v covers its full transverse texture once. */
    public record Vertex(Vec position, double u, double v, double alpha) {}
    public record Quad(Layer layer, Vertex a, Vertex b, Vertex c, Vertex d) {}

    private RailgunOverlayGeometry() {}

    /** No cap, hit marker, inferred endpoint or geometry beyond the supplied start/end interval. */
    public static List<Quad> build(Vec startWorld, Vec endWorld, Vec cameraWorld,
                                   double ageMillis, double widthScale, long seed, Quality quality) {
        if (!validPoint(startWorld) || !validPoint(endWorld) || !validPoint(cameraWorld)) return List.of();
        // Subtract the camera before interpolation, width offsets or any downstream float conversion.
        Vec relativeStart = startWorld.subtract(cameraWorld);
        Vec relativeEnd = endWorld.subtract(cameraWorld);
        Vec delta = relativeEnd.subtract(relativeStart);
        return buildRelative(relativeStart, delta, ageMillis, widthScale, seed, quality);
    }

    /**
     * Adapter for a classic beam stored as origin/direction/visible length. It avoids first forming
     * a far-world endpoint and losing thin offsets, and rejects negative rather than reversing length.
     * visibleLength must be the caller's existing classic length, including its classic growth scale.
     */
    public static List<Quad> buildAlong(Vec startWorld, Vec direction, double visibleLength,
                                        Vec cameraWorld, double ageMillis, double widthScale,
                                        long seed, Quality quality) {
        if (!validPoint(startWorld) || !validPoint(cameraWorld) || !Double.isFinite(visibleLength)
                || visibleLength < MIN_LENGTH) return List.of();
        Vec axis = unit(direction);
        if (axis == null) return List.of();
        return buildRelative(startWorld.subtract(cameraWorld), axis.scale(visibleLength),
                ageMillis, widthScale, seed, quality);
    }

    private static List<Quad> buildRelative(Vec start, Vec delta, double ageMillis,
                                            double widthScale, long seed, Quality quality) {
        if (quality == null || quality == Quality.OFF || !validPoint(start) || !validPoint(delta)
                || !Double.isFinite(ageMillis) || ageMillis < 0 || ageMillis >= LIFETIME_MILLIS
                || !Double.isFinite(widthScale) || widthScale <= 0) return List.of();
        double length = delta.length();
        if (!Double.isFinite(length) || length < MIN_LENGTH) return List.of();
        Vec axis = unit(delta);
        Vec end = start.add(delta);
        Vec midpoint = start.add(delta.scale(.5));
        if (axis == null || !end.finite() || !midpoint.finite()) return List.of();
        Vec side = facingSide(axis, midpoint);
        // The classic clock fades out over its last second; no independent time or tail is created.
        double fade = Math.min(1.0, (LIFETIME_MILLIS - ageMillis) / 1000.0);
        double haloWidth = Math.min(MAX_HALO_HALF_WIDTH, HALO_HALF_WIDTH * widthScale);
        // Segment count depends on length only within a fixed per-quality ceiling.
        int segments = cappedSegments(length, 4.0, quality.haloSegments());
        List<Quad> output = new ArrayList<>(quality.maxQuads());
        double phase = unitSeed(seed) * TWO_PI + ageMillis / 900.0;
        for (int segment = 0; segment < segments; segment++) {
            double t0 = segment / (double) segments;
            double t1 = (segment + 1) / (double) segments;
            Vec p0 = start.add(delta.scale(t0));
            Vec p1 = start.add(delta.scale(t1));
            double w0 = angularWidth(p0, haloWidth * (.96 + .04 * Math.sin(phase + t0 * TWO_PI)));
            double w1 = angularWidth(p1, haloWidth * (.96 + .04 * Math.sin(phase + t1 * TWO_PI)));
            // The supplied PNG owns transverse softness: alpha is not attenuated again across v.
            ribbon(output, Layer.HALO, p0, p1, side, w0, w1, t0, t1, HALO_ALPHA * fade);
        }
        for (int filament = 0; filament < quality.filaments(); filament++) {
            filament(output, start, delta, side, length, widthScale, ageMillis, fade, seed, filament);
        }
        // Fail closed for arithmetic outside normal world limits; never send a non-finite vertex.
        for (Quad quad : output) {
            if (!validVertex(quad.a) || !validVertex(quad.b) || !validVertex(quad.c)
                    || !validVertex(quad.d)) return List.of();
        }
        return List.copyOf(output);
    }

    private static void filament(List<Quad> output, Vec start, Vec delta, Vec side, double length,
                                  double widthScale, double ageMillis, double fade, long seed, int index) {
        // Two seeded short bands move along the existing interval. This is a sampled function of
        // age, not a spawn/emission loop, so paused frames produce exactly identical geometry.
        double flow = fract(unitSeed(seed ^ (0x9E3779B97F4A7C15L * (index + 1L))) + ageMillis / 1900.0);
        double span = Math.min(.18, MAX_FILAMENT_LENGTH / length);
        double t0 = Math.max(0, flow - span * .5);
        double t1 = Math.min(1, flow + span * .5);
        if (t1 <= t0) return;
        int segments = cappedSegments((t1 - t0) * length, 1.0, MAX_FILAMENT_SEGMENTS);
        double halfWidth = Math.min(MAX_FILAMENT_HALF_WIDTH, FILAMENT_HALF_WIDTH * widthScale);
        double offset = Math.min(.42, .24 * widthScale) * (index == 0 ? 1 : -1);
        double alpha = FILAMENT_ALPHA * fade * Math.sin(Math.PI * flow);
        for (int segment = 0; segment < segments; segment++) {
            double u0 = segment / (double) segments;
            double u1 = (segment + 1) / (double) segments;
            Vec p0 = filamentPoint(start, delta, side, t0, t1, u0, offset);
            Vec p1 = filamentPoint(start, delta, side, t0, t1, u1, offset);
            double w0 = angularWidth(p0, halfWidth);
            double w1 = angularWidth(p1, halfWidth);
            ribbon(output, Layer.FILAMENT, p0, p1, side, w0, w1, u0, u1, alpha);
        }
    }

    private static Vec filamentPoint(Vec start, Vec delta, Vec side, double t0, double t1,
                                       double u, double offset) {
        Vec point = start.add(delta.scale(t0 + (t1 - t0) * u));
        // Radial displacement also obeys the camera angular bound; it never moves along the axis.
        double radial = Math.copySign(Math.min(Math.abs(offset), point.length() * MAX_ANGULAR_HALF_WIDTH), offset);
        return point.add(side.scale(radial * Math.sin(Math.PI * u)));
    }

    private static void ribbon(List<Quad> output, Layer layer, Vec p0, Vec p1, Vec side,
                                 double w0, double w1, double u0, double u1, double alpha) {
        output.add(new Quad(layer,
                new Vertex(p0.add(side.scale(w0)), u0, 0, alpha),
                new Vertex(p1.add(side.scale(w1)), u1, 0, alpha),
                new Vertex(p1.subtract(side.scale(w1)), u1, 1, alpha),
                new Vertex(p0.subtract(side.scale(w0)), u0, 1, alpha)));
    }

    private static double angularWidth(Vec point, double halfWidth) {
        return Math.min(halfWidth, point.length() * MAX_ANGULAR_HALF_WIDTH);
    }

    private static int cappedSegments(double length, double segmentLength, int maximum) {
        // Clamp before converting to int, including arbitrarily large but finite accepted lengths.
        return (int) Math.max(1.0, Math.min(maximum, Math.ceil(length / segmentLength)));
    }

    private static Vec facingSide(Vec axis, Vec midpoint) {
        Vec towardCamera = unit(midpoint);
        Vec side = towardCamera == null ? null : unit(axis.cross(towardCamera.scale(-1)));
        if (side != null) return side;
        // Choose the least aligned world axis when the camera is on, or looking along, the beam.
        double x = Math.abs(axis.x), y = Math.abs(axis.y), z = Math.abs(axis.z);
        Vec reference = x <= y && x <= z ? new Vec(1, 0, 0)
                : y <= z ? new Vec(0, 1, 0) : new Vec(0, 0, 1);
        return unit(axis.cross(reference));
    }

    private static Vec unit(Vec value) {
        if (!validPoint(value)) return null;
        double length = value.length();
        if (!Double.isFinite(length) || length < BASIS_EPSILON) return null;
        return value.scale(1.0 / length);
    }

    private static boolean validPoint(Vec point) { return point != null && point.finite(); }
    private static boolean validVertex(Vertex vertex) {
        return validPoint(vertex.position) && Double.isFinite(vertex.u) && Double.isFinite(vertex.v)
                && Double.isFinite(vertex.alpha) && vertex.u >= 0 && vertex.u <= 1
                && vertex.v >= 0 && vertex.v <= 1 && vertex.alpha >= 0 && vertex.alpha <= 1;
    }

    private static double fract(double value) { return value - Math.floor(value); }
    private static double unitSeed(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }
}
