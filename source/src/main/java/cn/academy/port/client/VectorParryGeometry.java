package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;

/** NEW412 open forward contours and receipt-confirmed local force glyphs; no physical collision mesh. */
public final class VectorParryGeometry {
    public static final int MAX_CONTROLLERS = 4, MAX_VERTICES = 1024, MAX_PARTICLES = 0;
    public static final int ACTIVE_STROKES = 16, MAX_TURN_STROKES = 7, MATERIAL_LAYERS = 2;
    public static final int MAX_CONTROLLER_VERTICES = (ACTIVE_STROKES + MAX_TURN_STROKES) * 8 + 4;
    public static final double CULL_DISTANCE = 64, NEAR_PLANE = .08, HALF_ANGLE = Math.PI / 3;
    public static final double WEDGE_RADIUS = .90;
    private static final Point UP = new Point(0, 1, 0);
    public enum Role { WEDGE, HAND, TURN }
    public record Stroke(Point from, Point to, double width, int alpha, Role role) {}
    public record Ring(Point a, Point b, Point c, Point d, int alpha) {}
    public record Profile(double width, int gray, int alpha, float minV, float maxV) {}
    public record Shape(List<Stroke> strokes, List<Ring> rings) {
        public Shape { strokes = List.copyOf(strokes); rings = List.copyOf(rings); }
        public int vertexCount() { return strokes.size() * MATERIAL_LAYERS * 4 + rings.size() * 4; }
    }
    private VectorParryGeometry() {}
    public static boolean validPoint(Point p) {
        return p != null && p.finite() && Math.abs(p.x()) <= 30_000_000 && Math.abs(p.z()) <= 30_000_000 && Math.abs(p.y()) <= 4096;
    }
    public static boolean unit(Point p) { return p != null && p.finite() && Math.abs(dot(p, p) - 1) <= .002; }
    private static Point horizontal(Point p) {
        if (p == null || !p.finite()) return null;
        double length = Math.hypot(p.x(), p.z());
        return length == 0 ? null : new Point(p.x() / length, 0, p.z() / length);
    }
    public static Shape shape(VectorParryState.Controller controller, double partial) {
        if (controller == null || !VectorParryState.valid(controller.frame()) || controller.frame().phase() == VectorParryState.ENDED
                || !Double.isFinite(partial) || partial < 0 || partial >= 1 || controller.quiet() >= VectorParryState.QUIET_TICKS)
            return new Shape(List.of(), List.of());
        var frame = controller.frame(); Point forward = horizontal(frame.facing());
        if (forward == null) return new Shape(List.of(), List.of());
        Point side = new Point(-forward.z(), 0, forward.x());
        Point center = add(frame.point(), new Point(0, 1.10, 0));
        var strokes = new ArrayList<Stroke>(ACTIVE_STROKES + MAX_TURN_STROKES);
        var rings = new ArrayList<Ring>(1);
        // An open 120-degree rail stays entirely in front of the owner. Nothing closes behind the body.
        int activeAlpha = 142 + (int)Math.round(12 * Math.sin((controller.animationTick() + partial) * Math.PI / 30));
        for (int i = 0; i < 10; i++) {
            Point a = wedge(center, forward, side, WEDGE_RADIUS, -HALF_ANGLE + 2 * HALF_ANGLE * i / 10);
            Point b = wedge(center, forward, side, WEDGE_RADIUS, -HALF_ANGLE + 2 * HALF_ANGLE * (i + 1) / 10);
            add(strokes, new Stroke(a, b, .030, activeAlpha, Role.WEDGE));
        }
        // Short radial boundary marks, not shield walls, make the two unprotected sides legible.
        for (int sign : new int[]{-1, 1}) add(strokes, new Stroke(
                wedge(center, forward, side, .72, sign * HALF_ANGLE),
                wedge(center, forward, side, WEDGE_RADIUS, sign * HALF_ANGLE), .025, activeAlpha, Role.WEDGE));
        Point palm = add(center, add(scale(forward, .30), add(scale(side, -.22), new Point(0, -.13, 0))));
        for (int sign : new int[]{-1, 1}) {
            Point wrist = add(palm, add(scale(forward, -.12), scale(side, sign * .065)));
            Point knuckle = add(palm, scale(side, sign * .065));
            add(strokes, new Stroke(wrist, knuckle, .020, 112, Role.HAND));
            add(strokes, new Stroke(knuckle, add(knuckle, scale(forward, .27)), .022, 128, Role.HAND));
        }
        // Only ABSORBED means positive S absorption. A keepalive cannot move or refresh this ring.
        var absorption = controller.absorption(); double absorptionAge = controller.absorptionAge() + partial;
        if (sameReceipt(controller, absorption, VectorParryState.ABSORBED) && absorptionAge < VectorParryState.CONTACT_TICKS) {
            Point normal = unit(absorption.incoming()) ? absorption.incoming() : horizontal(absorption.facing());
            Point right = normalized(cross(normal, Math.abs(normal.y()) > .98 ? new Point(0, 0, 1) : UP));
            Point top = normalized(cross(right, normal));
            double t = absorptionAge / VectorParryState.CONTACT_TICKS;
            right = scale(right, .27 - .14 * t); top = scale(top, .19 - .09 * t);
            Point at = absorption.impact();
            Ring ring = new Ring(subtract(subtract(at, right), top), add(subtract(at, right), top),
                    add(add(at, right), top), subtract(add(at, right), top), contactAlpha(absorptionAge));
            if (validPoint(ring.a) && validPoint(ring.b) && validPoint(ring.c) && validPoint(ring.d)) rings.add(ring);
        }
        // A local turn glyph is possible only after an actual TURNED receipt, never from preciseTicks.
        var turn = controller.turn(); double turnAge = controller.turnAge() + partial;
        if (sameReceipt(controller, turn, VectorParryState.TURNED) && turnAge < VectorParryState.CONTACT_TICKS)
            turned(strokes, turn, contactAlpha(turnAge));
        return new Shape(strokes, rings);
    }
    private static boolean sameReceipt(VectorParryState.Controller c, VectorParryState.Frame f, int phase) {
        return f != null && f.id().equals(c.id()) && f.phase() == phase && VectorParryState.valid(f);
    }
    private static void turned(List<Stroke> strokes, VectorParryState.Frame f, int alpha) {
        Point at = add(f.point(), new Point(0, 1.31, 0));
        Point direction = f.turn(), side = new Point(-direction.z(), 0, direction.x());
        Point join = add(at, scale(direction, .15)), tip = add(at, scale(direction, .46));
        Point incoming = horizontal(f.incoming());
        if (incoming != null) {
            Point begin = subtract(at, scale(incoming, .26)), previous = begin;
            // A short bent mark at the defender's body denotes a force change, never a projectile path.
            for (int i = 1; i <= 4; i++) {
                double t = i / 4.0;
                Point next = add(add(scale(begin, (1 - t) * (1 - t)), scale(at, 2 * (1 - t) * t)), scale(join, t * t));
                add(strokes, new Stroke(previous, next, .036, alpha, Role.TURN)); previous = next;
            }
        }
        add(strokes, new Stroke(join, tip, .036, alpha, Role.TURN));
        for (int sign : new int[]{-1, 1}) add(strokes, new Stroke(tip,
                add(add(tip, scale(direction, -.13)), scale(side, sign * .09)), .031, alpha, Role.TURN));
    }
    private static void add(List<Stroke> strokes, Stroke stroke) {
        if (validPoint(stroke.from) && validPoint(stroke.to)) strokes.add(stroke);
    }
    public static int contactAlpha(double age) {
        return !Double.isFinite(age) || age < 0 || age >= VectorParryState.CONTACT_TICKS ? 0
                : (int)Math.round(220 * (1 - age / VectorParryState.CONTACT_TICKS));
    }
    public static Profile profile(Stroke stroke, boolean core) {
        return new Profile(stroke.width * (core ? 1 : 2.3), core ? 237 : 181,
                (int)Math.round(stroke.alpha * (core ? 1 : .36)), core ? 11f / 32 : 0, core ? 21f / 32 : 1);
    }
    /** Use camera-relative doubles all the way through clipping and expansion, then cast each final corner. */
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
    /** Whole-ring rejection avoids an eye-crossing textured quad without extra vertices. */
    public static boolean ringInFront(Ring ring, Point camera, Point forward) {
        return ring != null && unit(forward) && dot(relative(ring.a, camera), forward) > NEAR_PLANE
                && dot(relative(ring.b, camera), forward) > NEAR_PLANE && dot(relative(ring.c, camera), forward) > NEAR_PLANE
                && dot(relative(ring.d, camera), forward) > NEAR_PLANE;
    }
    public static final class Budget {
        private int used;
        public boolean take(int vertices) {
            if (vertices <= 0 || vertices > MAX_VERTICES - used) return false;
            used += vertices; return true;
        }
        public int used() { return used; }
    }
    private static Point wedge(Point center, Point forward, Point side, double radius, double angle) {
        return add(center, add(scale(forward, Math.cos(angle) * radius), scale(side, Math.sin(angle) * radius)));
    }
    private static Point normalized(Point p) { return scale(p, 1 / Math.sqrt(dot(p, p))); }
    private static Point add(Point a, Point b) { return new Point(a.x() + b.x(), a.y() + b.y(), a.z() + b.z()); }
    private static Point subtract(Point a, Point b) { return new Point(a.x() - b.x(), a.y() - b.y(), a.z() - b.z()); }
    private static Point scale(Point p, double s) { return new Point(p.x() * s, p.y() * s, p.z() * s); }
    private static Point lerp(Point a, Point b, double t) { return add(a, scale(subtract(b, a), t)); }
    private static double dot(Point a, Point b) { return a.x() * b.x() + a.y() * b.y() + a.z() * b.z(); }
    private static Point cross(Point a, Point b) { return new Point(a.y() * b.z() - a.z() * b.y(), a.z() * b.x() - a.x() * b.z(), a.x() * b.y() - a.y() * b.x()); }
}
