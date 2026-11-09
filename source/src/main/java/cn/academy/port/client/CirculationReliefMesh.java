package cn.academy.port.client;

import java.util.ArrayList;
import java.util.List;

/** NEW432 open side streams. Only a server absorption receipt draws descent to its real ground sink. */
public final class CirculationReliefMesh {
    public static final int MAX_CONTEXTS = 4, MAX_VERTICES = 2048, MAX_PARTICLES = 0;
    public static final int ARC_SEGMENTS = 12, DESCENT_SEGMENTS = 12, MATERIAL_LAYERS = 2;
    public static final int MAX_STROKES = 2 * ARC_SEGMENTS + 4 + DESCENT_SEGMENTS;
    public static final int MAX_CONTEXT_VERTICES = MAX_STROKES * MATERIAL_LAYERS * 4;
    public static final double CULL_DISTANCE = 64, NEAR_PLANE = .08;
    public enum Role { SIDE, QUOTA_READY, QUOTA_USED, DESCENT }
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
    private CirculationReliefMesh() {}
    public static boolean validPoint(Point p) {
        return p != null && p.finite() && Math.abs(p.x) <= 30_000_000
                && Math.abs(p.y) <= 4096 && Math.abs(p.z) <= 30_000_000;
    }
    public static boolean unit(Point p) { return p != null && p.finite() && Math.abs(dot(p, p) - 1) <= .002; }
    public static boolean horizontalUnit(Point p) { return unit(p) && Math.abs(p.y) <= .001; }
    /** Persistent arcs follow the body; brief success streams stay at the server's immutable sink. */
    public static Shape shape(CirculationReliefState.Controller controller, Point bodyFeet, double partial) {
        if (controller == null || !CirculationReliefState.valid(controller.frame()) || !validPoint(bodyFeet)
                || controller.frame().phase() == CirculationReliefState.ENDED || controller.quiet() < 0
                || controller.quiet() >= CirculationReliefState.QUIET_TICKS || !Double.isFinite(partial)
                || partial < 0 || partial >= 1) return new Shape(List.of());
        var frame = controller.frame();
        Point forward = frame.facing(), right = new Point(forward.z, 0, -forward.x);
        boolean ready = frame.grounded() && frame.remainingHits() > 0;
        int alpha = ready ? 192 : 74;
        double radius = .78, height = .87;
        var strokes = new ArrayList<Stroke>(MAX_STROKES);
        // Exactly two disjoint sixty-degree sectors. The large front and rear openings remain empty.
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < ARC_SEGMENTS; i++) {
                double a = Math.PI / 3 + Math.PI / 3 * i / ARC_SEGMENTS + side * Math.PI;
                double b = Math.PI / 3 + Math.PI / 3 * (i + 1) / ARC_SEGMENTS + side * Math.PI;
                addStroke(strokes, radial(bodyFeet, forward, right, radius, height, a),
                        radial(bodyFeet, forward, right, radius, height, b), .046, alpha, Role.SIDE);
            }
        }
        // All four marks remain visible. Their fill derives exclusively from the latest server allowance.
        for (int i = 0; i < 4; i++) {
            double angle = Math.toRadians(77 + (i % 2) * 26 + (i / 2) * 180);
            boolean available = i < frame.remainingHits();
            addStroke(strokes, radial(bodyFeet, forward, right, radius + .075, height - .09, angle),
                    radial(bodyFeet, forward, right, radius + .075, height + .09, angle),
                    .042, available ? (ready ? 224 : 128) : 58, available ? Role.QUOTA_READY : Role.QUOTA_USED);
        }
        var absorption = controller.absorption();
        if (absorption != null && absorption.id().equals(frame.id()) && absorption.phase() == CirculationReliefState.ABSORBED
                && absorption.grounded() && controller.absorptionAge() >= 0
                && controller.absorptionAge() < CirculationReliefState.ABSORPTION_TICKS) {
            double fade = 1 - (controller.absorptionAge() + partial) / CirculationReliefState.ABSORPTION_TICKS;
            Point streamForward = absorption.facing(), streamRight = new Point(streamForward.z, 0, -streamForward.x);
            Point incoming = subtract(absorption.contact(), absorption.point());
            double across = dot(incoming, streamRight), ahead = dot(incoming, streamForward);
            double angle = Math.atan2(across, ahead);
            if (angle < 0) angle += Math.PI * 2;
            double startAngle = across >= 0 ? Math.PI / 3 : Math.PI * 4 / 3;
            angle = Math.max(startAngle, Math.min(startAngle + Math.PI / 3, angle));
            Point start = radial(absorption.point(), streamForward, streamRight, radius, height, angle);
            // Only the side that really absorbed this hit conducts it; the opposite arc does not flash.
            for (int i = 0; i < DESCENT_SEGMENTS; i++) {
                double a = (double)i / DESCENT_SEGMENTS, b = (double)(i + 1) / DESCENT_SEGMENTS;
                addStroke(strokes, descent(start, absorption.groundSink(), a), descent(start, absorption.groundSink(), b),
                        .052, (int)Math.round(230 * fade), Role.DESCENT);
            }
        }
        return new Shape(strokes);
    }
    /** The curved route is strictly descending and ends at the received solid-ground point. */
    private static Point descent(Point start, Point sink, double t) {
        double bend = t * t;
        return new Point(start.x + (sink.x - start.x) * bend,
                start.y + (sink.y - start.y) * t, start.z + (sink.z - start.z) * bend);
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
    private static Point radial(Point center, Point forward, Point right, double radius, double height, double angle) {
        return add(add(center, scale(forward, radius * Math.cos(angle))),
                add(scale(right, radius * Math.sin(angle)), new Point(0, height, 0)));
    }
    private static Point add(Point a, Point b) { return new Point(a.x + b.x, a.y + b.y, a.z + b.z); }
    private static Point subtract(Point a, Point b) { return new Point(a.x - b.x, a.y - b.y, a.z - b.z); }
    private static Point scale(Point a, double s) { return new Point(a.x * s, a.y * s, a.z * s); }
    private static Point lerp(Point a, Point b, double t) { return add(a, scale(subtract(b, a), t)); }
    private static double dot(Point a, Point b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
}
