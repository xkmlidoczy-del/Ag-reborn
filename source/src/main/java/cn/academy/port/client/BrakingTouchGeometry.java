package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;

/** NEW413 shrinking actual-velocity strokes; a ground ring exists only for a confirmed landing. */
public final class BrakingTouchGeometry {
    public static final int MAX_CONTROLLERS = 4, MAX_VERTICES = 1024, MAX_PARTICLES = 0;
    public static final int MAX_STROKES = 6, MATERIAL_LAYERS = 2, MAX_CONTROLLER_VERTICES = MAX_STROKES * 8 + 4;
    public static final double CULL_DISTANCE = 64, NEAR_PLANE = .08;
    public enum Role { VELOCITY }
    public record Stroke(Point from, Point to, double width, int alpha, Role role) {}
    public record Ring(Point a, Point b, Point c, Point d, int alpha) {}
    public record Profile(double width, int gray, int alpha, float minV, float maxV) {}
    public record Shape(List<Stroke> strokes, List<Ring> rings) {
        public Shape { strokes = List.copyOf(strokes); rings = List.copyOf(rings); }
        public int vertexCount() { return strokes.size() * MATERIAL_LAYERS * 4 + rings.size() * 4; }
    }
    private BrakingTouchGeometry() {}
    public static boolean validPoint(Point p) {
        return p != null && p.finite() && Math.abs(p.x()) <= 30_000_000 && Math.abs(p.z()) <= 30_000_000 && Math.abs(p.y()) <= 4096;
    }
    public static boolean unit(Point p) { return p != null && p.finite() && Math.abs(dot(p, p) - 1) <= .002; }
    public static Shape shape(BrakingTouchState.Controller controller, double partial) {
        if (controller == null || !BrakingTouchState.valid(controller.frame()) || !Double.isFinite(partial)
                || partial < 0 || partial >= 1 || controller.age() < 0 || controller.age() >= BrakingTouchState.LIFE_TICKS
                || controller.frame().phase() != BrakingTouchState.BRAKED && controller.frame().phase() != BrakingTouchState.LANDED)
            return new Shape(List.of(), List.of());
        var frame = controller.frame();
        double speed = Math.sqrt(dot(frame.before(), frame.before()));
        Point direction = scale(frame.before(), 1 / speed);
        Point right = normalized(cross(direction, Math.abs(direction.y()) > .98 ? new Point(0, 0, 1) : new Point(0, 1, 0)));
        Point up = normalized(cross(right, direction));
        double fade = 1 - (controller.age() + partial) / BrakingTouchState.LIFE_TICKS;
        int alpha = (int)Math.round(210 * fade);
        double length = Math.min(.65, .18 + speed * .18) * fade;
        var strokes = new ArrayList<Stroke>(MAX_STROKES);
        // Only BRAKED carries an actual before/after velocity pair. Landing never implies velocity was set to zero.
        for (int i = 0; frame.phase() == BrakingTouchState.BRAKED && i < MAX_STROKES; i++) {
            double angle = i * Math.PI / 3;
            Point center = add(frame.point(), add(scale(right, Math.cos(angle) * .18 * fade), scale(up, Math.sin(angle) * .18 * fade)));
            Point from = add(center, scale(direction, -length));
            if (validPoint(from) && validPoint(center)) strokes.add(new Stroke(from, center, .018 * fade, alpha, Role.VELOCITY));
        }
        var rings = new ArrayList<Ring>(1);
        if (frame.phase() == BrakingTouchState.LANDED) {
            // The receipt's point is the genuine native contact point, never a raycast guess while falling.
            double radius = .52 + .12 * (1 - fade), y = frame.point().y() + .018;
            Point a = new Point(frame.point().x() - radius, y, frame.point().z() - radius);
            Point b = new Point(frame.point().x() - radius, y, frame.point().z() + radius);
            Point c = new Point(frame.point().x() + radius, y, frame.point().z() + radius);
            Point d = new Point(frame.point().x() + radius, y, frame.point().z() - radius);
            if (validPoint(a) && validPoint(b) && validPoint(c) && validPoint(d)) rings.add(new Ring(a, b, c, d, (int)Math.round(145 * fade)));
        }
        return new Shape(strokes, rings);
    }
    public static Profile profile(Stroke stroke, boolean core) {
        return new Profile(stroke.width * (core ? 1 : 2.3), core ? 237 : 181,
                (int)Math.round(stroke.alpha * (core ? 1 : .36)), core ? 11f / 32 : 0, core ? 21f / 32 : 1);
    }
    /** Camera-relative doubles precede clipping, quad expansion and the final float conversion. */
    public static Point relative(Point world, Point camera) { return subtract(world, camera); }
    public static Stroke clipNear(Stroke stroke, Point forward, double halfWidth) {
        if (stroke == null || !stroke.from.finite() || !stroke.to.finite() || !unit(forward)
                || !Double.isFinite(halfWidth) || halfWidth < 0) return null;
        double plane = NEAR_PLANE + halfWidth, a = dot(stroke.from, forward), b = dot(stroke.to, forward);
        if (a <= plane && b <= plane) return null;
        Point from = stroke.from, to = stroke.to;
        if (a < plane) from = lerp(from, to, (plane - a) / (b - a));
        else if (b < plane) to = lerp(from, to, (plane - a) / (b - a));
        return new Stroke(from, to, stroke.width, stroke.alpha, stroke.role);
    }
    public static boolean ringInFront(Ring ring, Point camera, Point forward) {
        return ring != null && camera != null && camera.finite() && unit(forward)
                && dot(relative(ring.a, camera), forward) > NEAR_PLANE && dot(relative(ring.b, camera), forward) > NEAR_PLANE
                && dot(relative(ring.c, camera), forward) > NEAR_PLANE && dot(relative(ring.d, camera), forward) > NEAR_PLANE;
    }
    public static final class Budget {
        private int used;
        public boolean take(int vertices) {
            if (vertices <= 0 || vertices > MAX_VERTICES - used) return false;
            used += vertices; return true;
        }
        public int used() { return used; }
    }
    private static Point normalized(Point p) { return scale(p, 1 / Math.sqrt(dot(p, p))); }
    private static Point add(Point a, Point b) { return new Point(a.x() + b.x(), a.y() + b.y(), a.z() + b.z()); }
    private static Point subtract(Point a, Point b) { return new Point(a.x() - b.x(), a.y() - b.y(), a.z() - b.z()); }
    private static Point scale(Point p, double s) { return new Point(p.x() * s, p.y() * s, p.z() * s); }
    private static Point lerp(Point a, Point b, double t) { return add(a, scale(subtract(b, a), t)); }
    private static double dot(Point a, Point b) { return a.x() * b.x() + a.y() * b.y() + a.z() * b.z(); }
    private static Point cross(Point a, Point b) { return new Point(a.y() * b.z() - a.z() * b.y(), a.z() * b.x() - a.x() * b.z(), a.x() * b.y() - a.y() * b.x()); }
}
