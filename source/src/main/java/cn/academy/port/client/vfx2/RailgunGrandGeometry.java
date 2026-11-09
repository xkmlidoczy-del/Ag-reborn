package cn.academy.port.client.vfx2;

import cn.academy.port.client.vfx2.RailgunOverlayGeometry.Vec;
import cn.academy.port.client.vfx2.RailgunOverlayGeometry.Vertex;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Pure camera-facing amber shoulders for the caller's existing classic Railgun beam.
 * The caller owns its visible length, width and pause-aware clock. This adds no cap,
 * hit cue, extension, core replacement, entity reference or independent lifecycle.
 * Camera subtraction happens in doubles before interpolation or any float conversion.
 */
public final class RailgunGrandGeometry {
    public static final double LIFETIME_MILLIS = 2500.0;
    public static final double ORIGINAL_OUTER_CORE_RADIUS = .13;
    public static final double MAX_HALF_WIDTH = .60;
    public static final double SWELL_MILLIS = 300.0;
    public static final double MAX_SWELL = 1.20;
    public static final double START_TAPER = .35;
    public static final double END_TAPER = .70;
    public static final double NEAR_PLANE = .15;
    public static final double NEAR_FADE = .30;
    public static final int MAX_QUADS = 12;
    public static final int MAX_VERTICES = MAX_QUADS * 4;
    private static final double MIN_LENGTH = 1.0E-6;
    private static final double BASIS_EPSILON = 1.0E-8;
    private static final double CLIP_ROUNDOFF_MARGIN = 1.0E-9;

    public enum Layer {
        INNER(2.0, 250, 201, 132, .42),
        OUTER(3.0, 236, 170, 93, .18);

        private final double radiusScale;
        private final int red, green, blue;
        private final double baseAlpha;

        Layer(double radiusScale, int red, int green, int blue, double baseAlpha) {
            this.radiusScale = radiusScale;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.baseAlpha = baseAlpha;
        }

        public double radiusScale() { return radiusScale; }
        public int red() { return red; }
        public int green() { return green; }
        public int blue() { return blue; }
        public double baseAlpha() { return baseAlpha; }
    }

    /** u follows the original full interval; v=0..1 supplies transverse softness exactly once. */
    public record Quad(Layer layer, Vertex a, Vertex b, Vertex c, Vertex d) {}

    private RailgunGrandGeometry() {}

    public static List<Quad> buildAlong(Vec startWorld, Vec direction, double visibleLength,
                                        Vec cameraWorld, Vec cameraForward,
                                        double ageMillis, double widthScale) {
        if (!validPoint(startWorld) || !validPoint(cameraWorld)
                || !Double.isFinite(visibleLength) || visibleLength < MIN_LENGTH
                || !Double.isFinite(ageMillis) || ageMillis < 0 || ageMillis >= LIFETIME_MILLIS
                || !Double.isFinite(widthScale) || widthScale <= 0) return List.of();
        Vec axis = unit(direction);
        Vec forward = unit(cameraForward);
        if (axis == null || forward == null) return List.of();
        Vec start = startWorld.subtract(cameraWorld);
        if (!start.finite()) return List.of();
        Vec end = start.add(axis.scale(visibleLength));
        if (!end.finite()) return List.of();

        double swell = 1 + (MAX_SWELL - 1) * (1 - smooth(ageMillis / SWELL_MILLIS));
        double innerWidth = halfWidth(Layer.INNER, widthScale, swell);
        double outerWidth = halfWidth(Layer.OUTER, widthScale, swell);
        if (innerWidth <= 0 || outerWidth <= 0) return List.of();
        // This plane protects every corner even when side points partly toward the camera.
        double safeDepth = NEAR_PLANE + outerWidth + CLIP_ROUNDOFF_MARGIN;
        double startDepth = start.dot(forward), endDepth = end.dot(forward);
        double depthSlope = axis.dot(forward);
        if (!Double.isFinite(startDepth) || !Double.isFinite(endDepth)) return List.of();
        if (startDepth < safeDepth && endDepth < safeDepth) return List.of();
        double from = 0, to = visibleLength;
        if (startDepth < safeDepth) from = (safeDepth - startDepth) / depthSlope;
        if (endDepth < safeDepth) to = (safeDepth - startDepth) / depthSlope;
        from = Math.max(0, from);
        to = Math.min(visibleLength, to);
        if (!Double.isFinite(from) || !Double.isFinite(to) || to - from < MIN_LENGTH) return List.of();

        Vec midpoint = start.add(axis.scale(from + (to - from) * .5));
        Vec side = facingSide(axis, midpoint);
        if (side == null) return List.of();
        // Two taper samples per physical end plus one near-fade knee: at most seven stations.
        TreeSet<Double> stations = new TreeSet<>();
        stations.add(from);
        stations.add(to);
        station(stations, START_TAPER * .5, from, to);
        station(stations, START_TAPER, from, to);
        station(stations, visibleLength - END_TAPER, from, to);
        station(stations, visibleLength - END_TAPER * .5, from, to);
        if (depthSlope != 0) station(stations,
                (safeDepth + NEAR_FADE - startDepth) / depthSlope, from, to);
        // Tiny/fully tapered visible intervals still need one interior alpha sample. With
        // only the two endpoints both values can be zero despite a nonzero interior envelope.
        if (stations.size() == 2) station(stations, from + (to - from) * .5, from, to);
        List<Double> distances = List.copyOf(stations);
        double timeFade = Math.min(1, (LIFETIME_MILLIS - ageMillis) / 1000.0);
        List<Quad> result = new ArrayList<>(MAX_QUADS);
        // Outer first, then the brighter inner shoulder, for ordinary alpha blending.
        for (Layer layer : new Layer[] {Layer.OUTER, Layer.INNER}) {
            double width = layer == Layer.INNER ? innerWidth : outerWidth;
            for (int i = 1; i < distances.size(); i++) {
                double d0 = distances.get(i - 1), d1 = distances.get(i);
                Vec p0 = start.add(axis.scale(d0)), p1 = start.add(axis.scale(d1));
                double a0 = alpha(layer, p0, forward, d0, visibleLength, safeDepth, timeFade);
                double a1 = alpha(layer, p1, forward, d1, visibleLength, safeDepth, timeFade);
                double u0 = d0 / visibleLength, u1 = d1 / visibleLength;
                Vec offset = side.scale(width);
                result.add(new Quad(layer,
                        new Vertex(p0.add(offset), u0, 0, a0),
                        new Vertex(p1.add(offset), u1, 0, a1),
                        new Vertex(p1.subtract(offset), u1, 1, a1),
                        new Vertex(p0.subtract(offset), u0, 1, a0)));
            }
        }
        // Arithmetic outside useful world limits fails closed, including rounded near-plane clips.
        for (Quad quad : result) for (Vertex vertex : List.of(quad.a, quad.b, quad.c, quad.d)) {
            if (!validVertex(vertex, forward)) return List.of();
        }
        return List.copyOf(result);
    }

    private static double halfWidth(Layer layer, double widthScale, double swell) {
        return Math.min(MAX_HALF_WIDTH, ORIGINAL_OUTER_CORE_RADIUS * layer.radiusScale * widthScale * swell);
    }

    private static double alpha(Layer layer, Vec point, Vec forward, double distance,
                                double length, double safeDepth, double timeFade) {
        double physicalTaper = smooth(distance / START_TAPER) * smooth((length - distance) / END_TAPER);
        double nearFade = smooth((point.dot(forward) - safeDepth) / NEAR_FADE);
        // The fragment owns cross-width falloff. Both transverse edges have the same alpha.
        return layer.baseAlpha * timeFade * physicalTaper * nearFade;
    }

    private static void station(TreeSet<Double> stations, double distance, double from, double to) {
        if (Double.isFinite(distance) && distance > from && distance < to) stations.add(distance);
    }

    private static Vec facingSide(Vec axis, Vec midpoint) {
        Vec towardCamera = unit(midpoint);
        Vec cross = towardCamera == null ? null : axis.cross(towardCamera.scale(-1));
        if (cross != null && cross.length() >= BASIS_EPSILON) return unit(cross);
        // Least-aligned-axis fallback is deterministic even for parallel/on-axis camera views.
        double x = Math.abs(axis.x()), y = Math.abs(axis.y()), z = Math.abs(axis.z());
        Vec reference = x <= y && x <= z ? new Vec(1, 0, 0)
                : y <= z ? new Vec(0, 1, 0) : new Vec(0, 0, 1);
        return unit(axis.cross(reference));
    }

    private static Vec unit(Vec value) {
        if (!validPoint(value)) return null;
        double largest = Math.max(Math.abs(value.x()), Math.max(Math.abs(value.y()), Math.abs(value.z())));
        if (largest == 0) return null;
        Vec scaled = new Vec(value.x() / largest, value.y() / largest, value.z() / largest);
        double length = scaled.length();
        return scaled.scale(1 / length);
    }

    private static double smooth(double value) {
        double t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }

    private static boolean validPoint(Vec value) { return value != null && value.finite(); }

    private static boolean validVertex(Vertex vertex, Vec forward) {
        return validPoint(vertex.position()) && vertex.position().dot(forward) >= NEAR_PLANE
                && Double.isFinite(vertex.u()) && vertex.u() >= 0 && vertex.u() <= 1
                && Double.isFinite(vertex.v()) && vertex.v() >= 0 && vertex.v() <= 1
                && Double.isFinite(vertex.alpha()) && vertex.alpha() >= 0 && vertex.alpha() <= 1;
    }
}
