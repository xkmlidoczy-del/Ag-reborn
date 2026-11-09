package cn.academy.port.client;

import java.util.ArrayList;
import java.util.List;

/** NEW433 owner-local flow and coarse screen-edge compass cues: 384 + 128 vertices, no source projection. */
public final class CirculationListeningMesh {
    public static final int MAX_CONTEXTS = 1, MAX_VERTICES = 512, MAX_PARTICLES = 0;
    public static final int ARC_SEGMENTS = 4, MATERIAL_LAYERS = 2;
    public static final int MAX_STROKES = CirculationListeningState.MAX_CUES * (ARC_SEGMENTS + 2);
    public static final int MAX_CONTEXT_VERTICES = MAX_STROKES * MATERIAL_LAYERS * 4;
    public static final int MAX_HUD_VERTICES = CirculationListeningState.MAX_CUES * 2 * MATERIAL_LAYERS * 4;
    public static final int HUD_TOP_INSET = 44, HUD_BOTTOM_RESERVE = 72, HUD_SIDE_INSET = 22;
    public static final double CULL_DISTANCE = 8, NEAR_PLANE = .08;
    public enum Role { FLOW, ORIENTATION }
    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public double distanceSquared(Point p) { return dot(subtract(this, p), subtract(this, p)); }
    }
    public record Stroke(Point from, Point to, double width, int alpha, Role role) {}
    public record Profile(double width, int gray, int alpha, float minV, float maxV) {}
    public record Shape(List<Stroke> strokes) {
        public Shape { strokes = List.copyOf(strokes); }
        public int vertexCount() { return strokes.size() * MATERIAL_LAYERS * 4; }
    }
    public record ScreenPoint(double x, double y) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y); }
    }
    /** Front and side chevrons face their compass edge; rear chevrons explicitly point down on the lower lane. */
    public record HudMarker(ScreenPoint tip, ScreenPoint left, ScreenPoint right, int alpha, boolean rear) {
        public int vertexCount() { return 2 * MATERIAL_LAYERS * 4; }
    }
    public record HudShape(List<HudMarker> markers) {
        public HudShape { markers = List.copyOf(markers); }
        public int vertexCount() { return markers.size() * 2 * MATERIAL_LAYERS * 4; }
    }
    private CirculationListeningMesh() {}
    public static boolean validPoint(Point p) {
        return p != null && p.finite() && Math.abs(p.x) <= 30_000_000
                && Math.abs(p.y) <= 4096 && Math.abs(p.z) <= 30_000_000;
    }
    public static boolean unit(Point p) { return p != null && p.finite() && Math.abs(dot(p, p) - 1) <= .002; }
    public static boolean horizontalUnit(Point p) { return unit(p) && Math.abs(p.y) <= .001; }
    /** Every short arrow is owner-local; only its world-facing sector comes from the server. */
    public static Shape shape(CirculationListeningState.Controller controller, Point bodyEye, double partial) {
        if (controller == null || !CirculationListeningState.valid(controller.frame()) || !validPoint(bodyEye)
                || controller.frame().phase() != CirculationListeningState.ACTIVE || controller.quiet() < 0
                || controller.quiet() >= CirculationListeningState.QUIET_TICKS || controller.cueAge() < 0
                || controller.cueAge() >= CirculationListeningState.CUE_TICKS || !Double.isFinite(partial)
                || partial < 0 || partial >= 1) return new Shape(List.of());
        double fade = 1 - (controller.cueAge() + partial) / CirculationListeningState.CUE_TICKS;
        var strokes = new ArrayList<Stroke>(MAX_STROKES);
        for (var cue : controller.frame().cues()) {
            Point direction = direction(cue.azimuth()), tangent = new Point(direction.z, 0, -direction.x);
            double height = -.15 + cue.elevation() * .38;
            double width = .026 + cue.strength() * .009;
            int alpha = (int)Math.round((130 + 34 * cue.strength()) * fade);
            // Curved airflow leads out toward the heard sector. Neither distance nor a target position is encoded.
            for (int i = 0; i < ARC_SEGMENTS; i++) {
                double a = (double)i / ARC_SEGMENTS, b = (double)(i + 1) / ARC_SEGMENTS;
                addStroke(strokes, flow(bodyEye, direction, tangent, height, a),
                        flow(bodyEye, direction, tangent, height, b), width, alpha, Role.FLOW);
            }
            Point tip = flow(bodyEye, direction, tangent, height, 1);
            Point base = add(tip, scale(direction, -.13));
            addStroke(strokes, add(base, scale(tangent, .085)), tip, width, alpha, Role.ORIENTATION);
            addStroke(strokes, add(base, scale(tangent, -.085)), tip, width, alpha, Role.ORIENTATION);
        }
        return new Shape(strokes);
    }
    private static Point flow(Point center, Point direction, Point tangent, double height, double t) {
        return add(center, add(scale(direction, .90 + .38 * t),
                add(scale(tangent, -.15 * (1 - t) * (1 - t)), new Point(0, height, 0))));
    }
    /** World bin 0 is +Z, 4 is +X; yaw never mutates authoritative sectors. */
    public static Point direction(int azimuth) {
        double angle = Math.PI * 2 * azimuth / 16;
        return new Point(Math.sin(angle), 0, Math.cos(angle));
    }
    /** Signed camera-relative horizontal angle, useful for consistent off-screen direction cues. */
    public static double relativeAzimuth(int azimuth, Point viewForward) {
        if (azimuth < 0 || azimuth >= 16 || viewForward == null || !viewForward.finite()
                || viewForward.x * viewForward.x + viewForward.z * viewForward.z < 1e-12) return Double.NaN;
        double difference = Math.PI * 2 * azimuth / 16 - Math.atan2(viewForward.x, viewForward.z);
        return Math.atan2(Math.sin(difference), Math.cos(difference));
    }
    /** This is a coarse heading compass, never a projection of a hidden source's position or distance. */
    public static HudShape hudShape(CirculationListeningState.Controller controller, int width, int height,
                                    Point viewForward, double partial) {
        if (controller == null || !CirculationListeningState.valid(controller.frame())
                || controller.frame().phase() != CirculationListeningState.ACTIVE
                || controller.quiet() < 0 || controller.quiet() >= CirculationListeningState.QUIET_TICKS
                || controller.cueAge() < 0 || controller.cueAge() >= CirculationListeningState.CUE_TICKS
                || !Double.isFinite(partial) || partial < 0 || partial >= 1
                || width < 112 || height < HUD_TOP_INSET + HUD_BOTTOM_RESERVE + 32
                || width > 16384 || height > 16384 || !Double.isFinite(relativeAzimuth(0, viewForward)))
            return new HudShape(List.of());
        double left = HUD_SIDE_INSET, right = width - HUD_SIDE_INSET;
        double top = HUD_TOP_INSET, bottom = height - HUD_BOTTOM_RESERVE;
        double centerX = width * .5, centerY = (top + bottom) * .5;
        double halfX = (right - left) * .5, halfY = (bottom - top) * .5;
        double fade = 1 - (controller.cueAge() + partial) / CirculationListeningState.CUE_TICKS;
        var markers = new ArrayList<HudMarker>(CirculationListeningState.MAX_CUES);
        for (var cue : controller.frame().cues()) {
            double heading = relativeAzimuth(cue.azimuth(), viewForward);
            double sx = Math.sin(heading), sy = -Math.cos(heading);
            boolean rear = Math.cos(heading) < -1e-8;
            double x, y, dx, dy;
            if (rear) {
                // Lower compass lane and downward V identify rear, including diagonally rearward sectors.
                x = centerX + sx * halfX; y = bottom - (cue.elevation() + 1) * 3;
                dx = 0; dy = 1;
            } else {
                double edge = Math.min(Math.abs(sx) < 1e-8 ? Double.POSITIVE_INFINITY : halfX / Math.abs(sx),
                        Math.abs(sy) < 1e-8 ? Double.POSITIVE_INFINITY : halfY / Math.abs(sy));
                x = centerX + sx * edge; y = centerY + sy * edge;
                // Three close lanes preserve only the server's coarse above/level/below classification.
                y = Math.max(top, Math.min(bottom, y - cue.elevation() * 5));
                dx = sx; dy = sy;
            }
            ScreenPoint tip = new ScreenPoint(x, y);
            ScreenPoint a = new ScreenPoint(x - dx * 9 - dy * 5, y - dy * 9 + dx * 5);
            ScreenPoint b = new ScreenPoint(x - dx * 9 + dy * 5, y - dy * 9 - dx * 5);
            int alpha = (int)Math.round((130 + 34 * cue.strength()) * fade);
            if (alpha > 0 && hudPointSafe(tip, width, height) && hudPointSafe(a, width, height) && hudPointSafe(b, width, height))
                markers.add(new HudMarker(tip, a, b, alpha, rear));
        }
        return new HudShape(markers);
    }
    /** Include the two-pixel soft stroke extrusion in the viewport check. */
    public static boolean hudPointSafe(ScreenPoint point, int width, int height) {
        return point != null && point.finite() && point.x >= 4 && point.x <= width - 4
                && point.y >= 24 && point.y <= height - 60;
    }
    public static Profile profile(Stroke stroke, boolean core) {
        return new Profile(stroke.width * (core ? 1 : 2.3), core ? 241 : 164,
                (int)Math.round(stroke.alpha * (core ? 1 : .32)), core ? 11f / 32 : 0, core ? 21f / 32 : 1);
    }
    /** Subtract camera origin in double precision before clipping, extrusion and float submission. */
    public static Point relative(Point world, Point camera) { return subtract(world, camera); }
    public static Stroke clipNear(Stroke stroke, Point forward, double halfWidth) {
        if (stroke == null || stroke.from == null || stroke.to == null || !stroke.from.finite() || !stroke.to.finite()
                || !unit(forward) || !Double.isFinite(halfWidth) || halfWidth < 0 || halfWidth > 1
                || !Double.isFinite(stroke.width) || stroke.width <= 0 || stroke.width > 1
                || stroke.alpha <= 0 || stroke.alpha > 255) return null;
        double plane = NEAR_PLANE + halfWidth, a = dot(stroke.from, forward), b = dot(stroke.to, forward);
        if (a <= plane && b <= plane) return null;
        Point from = stroke.from, to = stroke.to;
        if (a < plane) from = lerp(from, to, (plane - a) / (b - a));
        else if (b < plane) to = lerp(from, to, (plane - a) / (b - a));
        return from.distanceSquared(to) <= 1e-16 ? null : new Stroke(from, to, stroke.width, stroke.alpha, stroke.role);
    }
    public static final class Budget {
        private int used;
        public boolean take(int vertices) {
            if (vertices <= 0 || vertices > MAX_VERTICES - used) return false;
            used += vertices; return true;
        }
        public int used() { return used; }
    }
    private static void addStroke(List<Stroke> out, Point a, Point b, double width, int alpha, Role role) {
        if (validPoint(a) && validPoint(b) && width > 0 && alpha > 0)
            out.add(new Stroke(a, b, width, alpha, role));
    }
    private static Point add(Point a, Point b) { return new Point(a.x + b.x, a.y + b.y, a.z + b.z); }
    private static Point subtract(Point a, Point b) { return new Point(a.x - b.x, a.y - b.y, a.z - b.z); }
    private static Point scale(Point a, double s) { return new Point(a.x * s, a.y * s, a.z * s); }
    private static Point lerp(Point a, Point b, double t) { return add(a, scale(subtract(b, a), t)); }
    private static double dot(Point a, Point b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
}
