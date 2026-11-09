package cn.academy.port.client;

import cn.academy.port.client.BeamEtchingMesh.Box;
import cn.academy.port.client.BeamEtchingMesh.Mark;
import cn.academy.port.client.BeamEtchingMesh.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Optional NEW233 material geometry. Receipts own focus and success; the supplied clock is cosmetic only. */
public final class PointMeltdownFocusMesh {
    public static final double MAX_RANGE = 36, FACE_OFFSET = .003, NEAR_DEPTH = .08, RAY_NEAR_DEPTH = .20;
    public static final double RING_START_RADIUS = .24, RING_END_RADIUS = .08;
    public static final double HEAT_START_RADIUS = .04, HEAT_END_RADIUS = .075, SHOULDER_RADIUS = .16;
    /** Physical widths are full widths; projection is independently limited on either side of the centerline. */
    public static final double RAY_CORE_WIDTH = .025, RAY_SHOULDER_WIDTH = .045, MAX_RAY_ANGULAR_HALF_WIDTH = .006;
    public static final double NEAR_FADE_START = .55, NEAR_FADE_END = 1.40;
    public static final int CORE = 0xE7FFE9, GREEN = 0x72E79B, WHITE = 0xFFFFFF;
    public static final int FLASH_TICKS = 6, ARC_COUNT = 3, ARC_SEGMENTS = 8, DISK_SEGMENTS = 12;
    public static final int MAX_PRECLIP_QUADS = 79, MAX_CLIPPED_QUADS = MAX_PRECLIP_QUADS * 7, SPAWNED_PARTICLES = 0;
    private static final double EPS = 1e-7, AREA_EPS_SQUARED = 1e-20, TWO_PI = Math.PI * 2;

    public record Vertex(Point point, double u, double v) {
        private Vertex lerp(Vertex other, double amount) {
            return new Vertex(point.lerp(other.point, amount), u + (other.u - u) * amount, v + (other.v - v) * amount);
        }
    }
    /** Textures: -1 white, 0 mdball/glow, 1..3 small-ray in/tile/out, 4 mdball/0, 5 glow_line. */
    public record Face(Vertex a, Vertex b, Vertex c, Vertex d, int rgb, int alpha, int texture) {
        public List<Vertex> vertices() { return List.of(a, b, c, d); }
    }
    private PointMeltdownFocusMesh() {}

    /** Outer arc envelope, controlled exclusively by the real 0..10 server focus value. */
    public static double ringRadius(int focusTicks) {
        double progress = Math.clamp(focusTicks / 10.0, 0, 1);
        return RING_START_RADIUS + (RING_END_RADIUS - RING_START_RADIUS) * progress;
    }
    public static double heatRadius(int focusTicks) {
        double progress = Math.clamp(focusTicks / 10.0, 0, 1);
        return HEAT_START_RADIUS + (HEAT_END_RADIUS - HEAT_START_RADIUS) * progress;
    }
    public static double nearFade(Point point, Point camera) {
        if (!BeamEtchingMesh.validPoint(point) || !BeamEtchingMesh.validPoint(camera)) return 0;
        double t = Math.clamp((point.subtract(camera).length() - NEAR_FADE_START) / (NEAR_FADE_END - NEAR_FADE_START), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /**
     * The supplied native face and actual source-to-hit segment are never replaced by a camera-facing target or hand origin.
     * Every returned polygon is already clipped, including its interpolated material coordinates. A canceled context is not built.
     */
    public static List<Face> build(Point origin, Mark mark, Box surface, Point camera, Point look,
                                   int focusTicks, boolean focusing, boolean confirmed, int age, double visualTicks) {
        if (!PointMeltdownMesh.validSource(origin, mark) || !BeamEtchingMesh.validPoint(camera)
                || !BeamEtchingMesh.validDirection(look) || surface == null
                || BeamEtchingMesh.surface(mark, List.of(surface)) == null
                || !Double.isFinite(visualTicks) || focusTicks < 0 || focusTicks > 10 || age < 0
                || confirmed && age >= FLASH_TICKS) return List.of();
        double opacity = nearFade(mark.point(), camera) * (confirmed ? (FLASH_TICKS - age) / (double)FLASH_TICKS : 1);
        if (opacity <= 0) return List.of();
        List<Face> out = new ArrayList<>(MAX_PRECLIP_QUADS);
        focal(out, mark, surface, camera, look, focusTicks, focusing, confirmed, age, visualTicks, opacity);
        // Success consists only of a short face-local heat flash and three outward broken arcs.
        if (!confirmed) ray(out, origin, mark.point(), camera, look, focusing, visualTicks, opacity);
        return List.copyOf(out);
    }

    private static void focal(List<Face> out, Mark mark, Box surface, Point camera, Point look, int focusTicks,
                              boolean focusing, boolean confirmed, int age, double visualTicks, double opacity) {
        Point normal = BeamEtchingMesh.normal(mark.face());
        Point u = mark.face() < 4 ? new Point(1, 0, 0) : new Point(0, 0, 1), v = normal.cross(u);
        Point center = mark.point().add(normal.scale(FACE_OFFSET));
        double progress = focusing ? focusTicks / 10.0 : 0;
        double rotation = (visualTicks % 720) * .018;
        double envelope = confirmed ? RING_END_RADIUS + .09 * age / FLASH_TICKS : ringRadius(focusing ? focusTicks : 0);
        double arcHalfWidth = .007, radius = envelope - arcHalfWidth;
        int shoulderAlpha = alpha(confirmed ? 100 : focusing ? 65 : 45, opacity);
        int coreAlpha = alpha(confirmed ? 255 : focusing ? 225 : 175, opacity);
        // Complete each material layer together so four contexts do not alternate materials once per arc segment.
        for (int layer = 0; layer < 2; layer++) for (int arc = 0; arc < ARC_COUNT; arc++) {
            double start = rotation + TWO_PI * arc / ARC_COUNT;
            double sweep = Math.toRadians(80);
            for (int segment = 0; segment < ARC_SEGMENTS; segment++) {
                double a = start + sweep * segment / ARC_SEGMENTS, b = start + sweep * (segment + 1) / ARC_SEGMENTS;
                Point da = u.scale(Math.cos(a)).add(v.scale(Math.sin(a))), db = u.scale(Math.cos(b)).add(v.scale(Math.sin(b)));
                // glow_line fades across V, so each arc keeps a fine bright line inside a genuinely soft shoulder.
                double halfWidth = layer == 0 ? arcHalfWidth : .004;
                surfaceFace(out, arcFace(center, da, db, radius - halfWidth, radius + halfWidth,
                        layer == 0 ? GREEN : CORE, layer == 0 ? shoulderAlpha : coreAlpha, layer == 0 ? 5 : -1), mark, surface, camera, look);
            }
        }
        if (focusing || confirmed) {
            double shoulder = confirmed ? SHOULDER_RADIUS : .10 + .06 * progress;
            double heat = confirmed ? HEAT_END_RADIUS + .015 * age / FLASH_TICKS : heatRadius(focusTicks);
            // Circular fans constrain even transparent texture corners to the stated local radius.
            disk(out, mark, surface, camera, look, center, u, v, shoulder, alpha(confirmed ? 110 : 45 + 40 * progress, opacity), 0);
            disk(out, mark, surface, camera, look, center, u, v, heat, alpha(confirmed ? 255 : 185 + 50 * progress, opacity), 4);
        }
    }

    private static Face arcFace(Point center, Point a, Point b, double inner, double outer, int rgb, int alpha, int texture) {
        return new Face(new Vertex(center.add(a.scale(outer)), 0, 0), new Vertex(center.add(a.scale(inner)), 0, 1),
                new Vertex(center.add(b.scale(inner)), 1, 1), new Vertex(center.add(b.scale(outer)), 1, 0), rgb, alpha, texture);
    }
    private static void disk(List<Face> out, Mark mark, Box surface, Point camera, Point look, Point center,
                             Point u, Point v, double radius, int alpha, int texture) {
        Vertex middle = new Vertex(center, .5, .5);
        for (int i = 0; i < DISK_SEGMENTS; i++) {
            double a = TWO_PI * i / DISK_SEGMENTS, b = TWO_PI * (i + 1) / DISK_SEGMENTS;
            Vertex va = diskVertex(center, u, v, radius, a), vb = diskVertex(center, u, v, radius, b);
            surfaceFace(out, new Face(middle, va, vb, middle, WHITE, alpha, texture), mark, surface, camera, look);
        }
    }
    private static Vertex diskVertex(Point center, Point u, Point v, double radius, double angle) {
        double x = Math.cos(angle), y = Math.sin(angle);
        return new Vertex(center.add(u.scale(radius * x)).add(v.scale(radius * y)), .5 + .5 * x, .5 + .5 * y);
    }

    private static void ray(List<Face> out, Point origin, Point end, Point camera, Point look,
                            boolean focusing, double visualTicks, double opacity) {
        Point direction = end.subtract(origin);
        double length = direction.length();
        if (length < EPS) return;
        Point side = direction.cross(camera.subtract(origin));
        if (side.length() < EPS) side = direction.cross(Math.abs(direction.y() / length) > .9 ? new Point(1, 0, 0) : new Point(0, 1, 0));
        if (side.length() < EPS) return;
        side = side.scale(1 / side.length());
        rayPart(out, origin, end, side, camera, look, RAY_SHOULDER_WIDTH / 2, GREEN, alpha(focusing ? 70 : 28, opacity), 5);
        double blend = Math.min(.2, .25 / length);
        Point a = origin.lerp(end, blend), b = origin.lerp(end, 1 - blend);
        int rayAlpha = alpha(focusing ? 185 : 78, opacity);
        rayPart(out, origin, a, side, camera, look, RAY_CORE_WIDTH / 2, WHITE, rayAlpha, 1);
        rayPart(out, a, b, side, camera, look, RAY_CORE_WIDTH / 2, WHITE, rayAlpha, 2);
        rayPart(out, b, end, side, camera, look, RAY_CORE_WIDTH / 2, WHITE, rayAlpha, 3);
        rayPart(out, origin, end, side, camera, look, .0025, CORE, alpha(focusing ? 145 : 55, opacity), -1);
        if (focusing) {
            // Exactly two short packets move along the same bounded segment. Their clock cannot change focus or confirm success.
            double cycle = ((visualTicks % 14) + 14) % 14 / 14;
            double packetLength = Math.min(.65, length * .18), room = length - packetLength;
            for (int packet = 0; packet < 2; packet++) {
                double phase = (cycle + packet * .5) % 1;
                double from = phase * room / length, to = from + packetLength / length;
                rayPart(out, origin.lerp(end, from), origin.lerp(end, to), side, camera, look,
                        RAY_CORE_WIDTH / 2, WHITE, alpha(225, opacity), 0);
            }
        }
    }

    /** Clip the true segment first, preserving original longitudinal UV, then expand within a projective half-width bound. */
    private static void rayPart(List<Face> out, Point start, Point end, Point side, Point camera, Point look,
                                double halfWidth, int rgb, int alpha, int texture) {
        if (alpha <= 0) return;
        double za = start.subtract(camera).dot(look), zb = end.subtract(camera).dot(look), from = 0, to = 1;
        if (za < RAY_NEAR_DEPTH && zb < RAY_NEAR_DEPTH) return;
        if (za < RAY_NEAR_DEPTH) from = (RAY_NEAR_DEPTH - za) / (zb - za);
        else if (zb < RAY_NEAR_DEPTH) to = (RAY_NEAR_DEPTH - za) / (zb - za);
        Point a = start.lerp(end, from), b = start.lerp(end, to);
        if (a.subtract(b).length() < EPS) return;
        Point wa = side.scale(projectiveWidth(a.subtract(camera), side, look, halfWidth));
        Point wb = side.scale(projectiveWidth(b.subtract(camera), side, look, halfWidth));
        Face face = new Face(new Vertex(a.add(wa), from, 0), new Vertex(a.subtract(wa), from, 1),
                new Vertex(b.subtract(wb), to, 1), new Vertex(b.add(wb), to, 0), rgb, alpha, texture);
        emit(out, clip(face.vertices(), p -> p.subtract(camera).dot(look) - RAY_NEAR_DEPTH), face);
    }
    private static double projectiveWidth(Point relative, Point side, Point look, double physicalHalfWidth) {
        double z = relative.dot(look), sideDepth = side.dot(look);
        double limit = MAX_RAY_ANGULAR_HALF_WIDTH * physicalHalfWidth / (RAY_SHOULDER_WIDTH / 2);
        double numerator = side.scale(z).subtract(relative.scale(sideDepth)).length();
        double divisor = numerator + limit * z * Math.abs(sideDepth);
        return divisor <= EPS ? physicalHalfWidth : Math.min(physicalHalfWidth, limit * z * z / divisor);
    }

    private static void surfaceFace(List<Face> out, Face face, Mark mark, Box surface, Point camera, Point look) {
        if (face.alpha <= 0) return;
        List<Vertex> polygon = face.vertices();
        int normalAxis = mark.face() < 2 ? 1 : mark.face() < 4 ? 2 : 0;
        for (int a = 0; a < 3; a++) if (a != normalAxis) {
            final int axis = a;
            polygon = clip(polygon, p -> p.axis(axis) - surface.min().axis(axis));
            polygon = clip(polygon, p -> surface.max().axis(axis) - p.axis(axis));
        }
        emit(out, clip(polygon, p -> p.subtract(camera).dot(look) - NEAR_DEPTH), face);
    }
    /** Sutherland-Hodgman intersections interpolate the complete vertex, never restart its UV at a cut edge. */
    private static List<Vertex> clip(List<Vertex> points, ToDoubleFunction<Point> distance) {
        if (points.size() < 3) return List.of();
        List<Vertex> out = new ArrayList<>(points.size() + 1);
        Vertex previous = points.getLast();
        double before = distance.applyAsDouble(previous.point);
        for (Vertex vertex : points) {
            double now = distance.applyAsDouble(vertex.point);
            if ((now >= 0) != (before >= 0)) out.add(previous.lerp(vertex, before / (before - now)));
            if (now >= 0) out.add(vertex);
            previous = vertex; before = now;
        }
        return out;
    }
    private static void emit(List<Face> out, List<Vertex> polygon, Face material) {
        List<Vertex> points = new ArrayList<>(polygon.size());
        for (Vertex vertex : polygon) {
            if (!BeamEtchingMesh.validPoint(vertex.point) || !Double.isFinite(vertex.u) || !Double.isFinite(vertex.v)) return;
            if (points.isEmpty() || !same(points.getLast(), vertex)) points.add(vertex);
        }
        if (points.size() > 1 && same(points.getFirst(), points.getLast())) points.removeLast();
        if (points.size() < 3) return;
        if (points.size() == 4 && nonzero(points.get(0), points.get(1), points.get(2))) {
            out.add(new Face(points.get(0), points.get(1), points.get(2), points.get(3), material.rgb, material.alpha, material.texture));
        } else for (int i = 1; i < points.size() - 1; i++) {
            if (nonzero(points.get(0), points.get(i), points.get(i + 1)))
                out.add(new Face(points.get(0), points.get(i), points.get(i + 1), points.get(0), material.rgb, material.alpha, material.texture));
        }
    }
    private static boolean same(Vertex a, Vertex b) {
        return a.point.subtract(b.point).length() < 1e-10 && Math.abs(a.u - b.u) < 1e-10 && Math.abs(a.v - b.v) < 1e-10;
    }
    private static boolean nonzero(Vertex a, Vertex b, Vertex c) {
        Point cross = b.point.subtract(a.point).cross(c.point.subtract(a.point));
        return cross.dot(cross) > AREA_EPS_SQUARED;
    }
    private static int alpha(double strength, double opacity) { return (int)Math.clamp(Math.round(strength * opacity), 0, 255); }
}
