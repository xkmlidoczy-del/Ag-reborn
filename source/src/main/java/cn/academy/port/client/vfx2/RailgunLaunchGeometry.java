package cn.academy.port.client.vfx2;

import cn.academy.port.client.vfx2.RailgunOverlayGeometry.Vec;
import cn.academy.port.client.vfx2.RailgunOverlayGeometry.Vertex;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure, bounded launch decoration for an already accepted classic Railgun axis.
 * The caller owns visible length, width and the original pause-aware 2500 ms clock.
 * There is no impact, endpoint cap, inferred range, emission or independent lifecycle.
 * World-camera subtraction stays in doubles before interpolation or float conversion.
 */
public final class RailgunLaunchGeometry {
    public static final double LIFETIME_MILLIS = 2500;
    public static final double MAX_ENVELOPE_LENGTH = 12;
    public static final double MAX_TRAIL_LENGTH = 18;
    public static final double INITIAL_ENVELOPE_RADIUS = .35;
    public static final double MAX_DECORATIVE_RADIUS = 1.6;
    public static final double ENVELOPE_EXPAND_MILLIS = 300;
    public static final double MAX_TRAIL_HALF_WIDTH = .10;
    public static final double MAX_TRAIL_CENTER_OFFSET = 1.10;
    public static final double SECOND_TRAIL_LENGTH_SCALE = .8;
    public static final double SECOND_TRAIL_OFFSET_SCALE = .78;
    public static final double SECOND_TRAIL_WIDTH_SCALE = .85;
    public static final double TRAIL_SEPARATE_MILLIS = 500;
    public static final double SHORT_LENGTH = 6;
    public static final double MAX_DEPTH_RADIUS_RATIO = .20;
    public static final double NEAR_PLANE = .15;
    public static final double NEAR_FADE = .30;
    public static final int ENVELOPE_QUADS = 4;
    public static final int TRAIL_QUADS = 4;
    public static final int MAX_QUADS = ENVELOPE_QUADS + 2 * TRAIL_QUADS;
    public static final int MAX_VERTICES = MAX_QUADS * 4;
    private static final double MIN_LENGTH = 1e-6;
    private static final double BASIS_EPSILON = 1e-8;
    private static final double CLIP_ROUNDOFF_MARGIN = 1e-9;

    public enum Layer {
        ENVELOPE(250, 201, 132, .42, 0),
        TRAIL(236, 170, 93, .24, 1);

        private final int red, green, blue, profile;
        private final double baseAlpha;

        Layer(int red, int green, int blue, double baseAlpha, int profile) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.baseAlpha = baseAlpha;
            this.profile = profile;
        }

        public int red() { return red; }
        public int green() { return green; }
        public int blue() { return blue; }
        public double baseAlpha() { return baseAlpha; }
        /** Shader profile: protected envelope=0, ordinary narrow trail=1. */
        public int profile() { return profile; }
    }

    /** u retains original longitudinal distance; the renderer supplies layer.profile separately. */
    public record Quad(Layer layer, Vertex a, Vertex b, Vertex c, Vertex d) {}

    private record Clip(double from, double to, double radiusScale, double safeDepth) {}

    private RailgunLaunchGeometry() {}

    public static List<Quad> buildAlong(Vec startWorld, Vec direction, double visibleLength,
                                        Vec cameraWorld, Vec cameraForward,
                                        double ageMillis, double widthScale) {
        if (!validPoint(startWorld) || !validPoint(cameraWorld)
                || !Double.isFinite(visibleLength) || visibleLength < MIN_LENGTH
                || !Double.isFinite(ageMillis) || ageMillis < 0 || ageMillis >= LIFETIME_MILLIS
                || !Double.isFinite(widthScale) || widthScale <= 0) return List.of();
        Vec axis = unit(direction), forward = unit(cameraForward);
        if (axis == null || forward == null) return List.of();
        Vec start = startWorld.subtract(cameraWorld);
        if (!start.finite()) return List.of();
        // Never form a world endpoint, or even an unnecessary full accepted-range endpoint.
        Vec midpoint = start.add(axis.scale(Math.min(visibleLength, MAX_TRAIL_LENGTH) * .5));
        if (!midpoint.finite()) return List.of();
        Vec side = facingSide(axis, midpoint);
        if (side == null) return List.of();

        double shortScale = Math.min(1, visibleLength / SHORT_LENGTH);
        double classicFade = Math.min(1, (LIFETIME_MILLIS - ageMillis) / 1000);
        double envelopeAlpha = smooth(ageMillis / 80)
                * (1 - smooth((ageMillis - 400) / 700)) * classicFade;
        double trailAlpha = smooth((ageMillis - 120) / 180)
                * (1 - smooth((ageMillis - 1300) / 600)) * classicFade;
        if (envelopeAlpha <= 0 && trailAlpha <= 0) return List.of();

        List<Quad> result = new ArrayList<>(MAX_QUADS);
        if (envelopeAlpha > 0) {
            double radius = Math.min(MAX_DECORATIVE_RADIUS,
                    (INITIAL_ENVELOPE_RADIUS + (MAX_DECORATIVE_RADIUS - INITIAL_ENVELOPE_RADIUS)
                            * smooth(ageMillis / ENVELOPE_EXPAND_MILLIS)) * widthScale) * shortScale;
            component(result, Layer.ENVELOPE, start, axis, side, forward, visibleLength,
                    Math.min(visibleLength, MAX_ENVELOPE_LENGTH), radius, 0,
                    envelopeAlpha, ENVELOPE_QUADS);
        }
        if (trailAlpha > 0) {
            double width = Math.min(MAX_TRAIL_HALF_WIDTH, MAX_TRAIL_HALF_WIDTH * widthScale) * shortScale;
            double offset = Math.min(MAX_TRAIL_CENTER_OFFSET, MAX_TRAIL_CENTER_OFFSET * widthScale)
                    * shortScale * smooth(ageMillis / TRAIL_SEPARATE_MILLIS);
            List<Quad> trails = new ArrayList<>(2 * TRAIL_QUADS);
            component(trails, Layer.TRAIL, start, axis, side, forward, visibleLength,
                    Math.min(visibleLength, MAX_TRAIL_LENGTH), width, offset,
                    trailAlpha, TRAIL_QUADS);
            component(trails, Layer.TRAIL, start, axis, side, forward, visibleLength,
                    Math.min(visibleLength, MAX_TRAIL_LENGTH * SECOND_TRAIL_LENGTH_SCALE),
                    width * SECOND_TRAIL_WIDTH_SCALE, -offset * SECOND_TRAIL_OFFSET_SCALE,
                    trailAlpha, TRAIL_QUADS);
            // Keep the pair whole even when the shorter local span is completely clipped away.
            if (trails.size() == 2 * TRAIL_QUADS) result.addAll(trails);
        }
        // Complete components are generated within the fixed budget, never truncated to fit it.
        if (result.size() > MAX_QUADS) return List.of();
        for (Quad quad : result) for (Vertex vertex : List.of(quad.a, quad.b, quad.c, quad.d))
            if (!validVertex(vertex, forward)) return List.of();
        return List.copyOf(result);
    }

    private static void component(List<Quad> output, Layer layer, Vec start, Vec axis, Vec side,
                                  Vec forward, double visibleLength, double span,
                                  double halfWidth, double centerOffset, double timeAlpha, int segments) {
        double radialExtent = halfWidth + Math.abs(centerOffset);
        if (radialExtent <= 0) return;
        Clip clip = clip(start, axis, forward, span, radialExtent);
        if (clip == null) return;
        // One shared station object is reused by neighboring quads, so joins are exact.
        Vertex[] positive = new Vertex[segments + 1], negative = new Vertex[segments + 1];
        boolean visible = false;
        for (int i = 0; i <= segments; i++) {
            double distance = i == segments ? clip.to : clip.from + (clip.to - clip.from) * i / segments;
            Vec point = start.add(axis.scale(distance));
            double t = distance / span;
            double taper = layer == Layer.ENVELOPE ? envelopeTaper(distance, span) : trailWidth(t);
            double width = halfWidth * clip.radiusScale * taper;
            // The trails open away from the axis and dissipate there instead of closing into an eye.
            double offset = centerOffset * clip.radiusScale * smooth(t);
            Vec center = point.add(side.scale(offset));
            double nearAlpha = smooth((point.dot(forward) - clip.safeDepth) / NEAR_FADE);
            double longitudinalAlpha = layer == Layer.ENVELOPE ? 1 : trailAlpha(t);
            double alpha = layer.baseAlpha * timeAlpha * nearAlpha * longitudinalAlpha;
            visible |= alpha > 0 && width > 0;
            double u = distance / visibleLength;
            positive[i] = new Vertex(center.add(side.scale(width)), u, 0, alpha);
            negative[i] = new Vertex(center.subtract(side.scale(width)), u, 1, alpha);
        }
        if (!visible) return;
        for (int i = 0; i < segments; i++)
            output.add(new Quad(layer, positive[i], positive[i + 1], negative[i + 1], negative[i]));
    }

    private static Clip clip(Vec start, Vec axis, Vec forward, double span, double radialExtent) {
        double startDepth = start.dot(forward), slope = axis.dot(forward);
        double endDepth = start.add(axis.scale(span)).dot(forward);
        if (!Double.isFinite(startDepth) || !Double.isFinite(endDepth) || !Double.isFinite(slope)) return null;
        if (Math.max(startDepth, endDepth) <= NEAR_PLANE) return null;
        // The nearest possible visible axis depth bounds the entire component, including offsets.
        // Crossing intervals use the near plane as a conservative positive-depth lower bound.
        double minimumPositiveDepth = Math.max(NEAR_PLANE, Math.min(startDepth, endDepth));
        double cappedRadius = Math.min(MAX_DECORATIVE_RADIUS,
                Math.min(radialExtent, MAX_DEPTH_RADIUS_RATIO * minimumPositiveDepth));
        double safeDepth = NEAR_PLANE + cappedRadius + CLIP_ROUNDOFF_MARGIN;
        if (startDepth < safeDepth && endDepth < safeDepth) return null;
        double from = 0, to = span;
        if (startDepth < safeDepth) from = (safeDepth - startDepth) / slope;
        if (endDepth < safeDepth) to = (safeDepth - startDepth) / slope;
        from = Math.max(0, from);
        to = Math.min(span, to);
        if (!Double.isFinite(from) || !Double.isFinite(to) || to - from < MIN_LENGTH) return null;
        return new Clip(from, to, cappedRadius / radialExtent, safeDepth);
    }

    private static double envelopeTaper(double distance, double span) {
        double riseLength = Math.min(1.2, span * .2);
        double widestUntil = Math.min(4, span * .6);
        return smooth(distance / riseLength) * smooth((span - distance) / (span - widestUntil));
    }

    private static double trailWidth(double t) {
        return smooth(t / .2) * (1 - smooth((t - .5) / .5));
    }

    private static double trailAlpha(double t) {
        return smooth(t / .2) * (1 - smooth((t - .35) / .65));
    }

    private static Vec facingSide(Vec axis, Vec midpoint) {
        Vec towardCamera = unit(midpoint);
        Vec cross = towardCamera == null ? null : axis.cross(towardCamera.scale(-1));
        if (cross != null && cross.length() >= BASIS_EPSILON) return unit(cross);
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
        return scaled.scale(1 / scaled.length());
    }

    private static double smooth(double value) {
        double t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }

    private static boolean validPoint(Vec value) { return value != null && value.finite(); }

    private static boolean validVertex(Vertex vertex, Vec forward) {
        return validPoint(vertex.position()) && vertex.position().dot(forward) > NEAR_PLANE
                && Double.isFinite(vertex.u()) && vertex.u() >= 0 && vertex.u() <= 1
                && Double.isFinite(vertex.v()) && vertex.v() >= 0 && vertex.v() <= 1
                && Double.isFinite(vertex.alpha()) && vertex.alpha() >= 0 && vertex.alpha() <= 1;
    }
}
