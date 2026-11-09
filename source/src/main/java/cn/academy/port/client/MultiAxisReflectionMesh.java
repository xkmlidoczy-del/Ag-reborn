package cn.academy.port.client;

import cn.academy.port.highpower.core.MultiAxisReflectionGeometry;
import cn.academy.port.highpower.core.MultiAxisReflectionGeometry.Vector;
import java.util.ArrayList;
import java.util.List;

/** Four finite, disjoint pressure planes. Reflection and contact cues use actual received positions. */
public final class MultiAxisReflectionMesh {
    public static final int MAX_CONTEXTS = 4, MAX_VERTICES = 2048, MAX_PARTICLES = 0, MATERIAL_LAYERS = 2;
    public static final int MAX_STROKES = 16 + 8 + 4 + MultiAxisReflectionState.MAX_TRACES * (2 + MultiAxisReflectionState.MAX_TRACE_POINTS - 1) + 3;
    public static final int MAX_CONTEXT_VERTICES = MAX_STROKES * MATERIAL_LAYERS * 4 + 4 * 4;
    public static final double CULL_DISTANCE = 64, NEAR_PLANE = .08;
    public enum Role { EDGE_READY, EDGE_UNAVAILABLE, VECTOR, QUOTA_READY, QUOTA_USED, REFLECT, TRAIL, CONTACT }
    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public double distanceSquared(Point p) { return dot(subtract(this, p), subtract(this, p)); }
    }
    public record Stroke(Point from, Point to, double width, int alpha, Role role) {}
    public record Panel(List<Point> corners, int alpha, int facet, boolean available) {
        public Panel { corners = List.copyOf(corners); }
    }
    public record Profile(double width, int gray, int alpha, float minV, float maxV) {}
    public record Shape(List<Stroke> strokes, List<Panel> panels) {
        public Shape { strokes = List.copyOf(strokes); panels = List.copyOf(panels); }
        public int vertexCount() { return strokes.size() * MATERIAL_LAYERS * 4 + panels.size() * 4; }
    }
    private MultiAxisReflectionMesh() {}
    public static boolean validPoint(Point p) {
        return p != null && p.finite() && Math.abs(p.x) <= 30_000_000 && Math.abs(p.y) <= 4096 && Math.abs(p.z) <= 30_000_000;
    }
    public static boolean unit(Point p) { return p != null && p.finite() && Math.abs(dot(p, p) - 1) <= .002; }
    public static boolean horizontalUnit(Point p) { return unit(p) && Math.abs(p.y) <= .001; }
    public static Shape shape(MultiAxisReflectionState.Controller c, Point bodyCenter, double partial) {
        if (c == null || !MultiAxisReflectionState.valid(c.frame()) || !validPoint(bodyCenter)
                || c.frame().phase() == MultiAxisReflectionState.ENDED || c.quiet() < 0
                || c.quiet() >= MultiAxisReflectionState.QUIET_TICKS || !Double.isFinite(partial) || partial < 0 || partial >= 1)
            return new Shape(List.of(), List.of());
        var frame = c.frame();
        var pose = MultiAxisReflectionGeometry.pose(vector(bodyCenter), vector(frame.facing()));
        if (pose == null) return new Shape(List.of(), List.of());
        boolean operating = frame.phase() != MultiAxisReflectionState.STARTUP;
        var strokes = new ArrayList<Stroke>(MAX_STROKES);
        var panels = new ArrayList<Panel>(4);
        for (int facet = 0; facet < 4; facet++) {
            boolean ready = operating && frame.remainingHits() > 0 && (frame.liveMask() & (1 << facet)) != 0;
            List<Point> corners = MultiAxisReflectionGeometry.vertices(pose, facet).stream().map(MultiAxisReflectionMesh::point).toList();
            panels.add(new Panel(corners, ready ? 12 : 3, facet, ready));
            for (int edge = 0; edge < 4; edge++)
                addStroke(strokes, corners.get(edge), corners.get((edge + 1) % 4), ready ? .032 : .024,
                        ready ? 192 : 58, ready ? Role.EDGE_READY : Role.EDGE_UNAVAILABLE);
            // Sparse two-stroke direction marks remain inside each plane; no links bridge the real gaps.
            Point center = point(MultiAxisReflectionGeometry.facetCenter(pose, facet));
            Point right = point(MultiAxisReflectionGeometry.facetRight(pose, facet));
            Point tip = add(center, new Point(0, .73, 0));
            addStroke(strokes, add(add(tip, scale(right, -.12)), new Point(0, -.12, 0)), tip, .032, ready ? 155 : 43, Role.VECTOR);
            addStroke(strokes, tip, add(add(tip, scale(right, .12)), new Point(0, -.12, 0)), .032, ready ? 155 : 43, Role.VECTOR);
            boolean quota = facet < frame.remainingHits();
            Point quotaCenter = add(center, new Point(0, -.78, 0));
            addStroke(strokes, add(quotaCenter, scale(right, -.11)), add(quotaCenter, scale(right, .11)), .046,
                    quota ? operating ? 225 : 104 : 51, quota ? Role.QUOTA_READY : Role.QUOTA_USED);
        }
        Point forward = frame.facing(), right = new Point(forward.z, 0, -forward.x), up = new Point(0, 1, 0);
        for (var trace : c.traces()) {
            if (trace.age() < 0 || trace.age() >= MultiAxisReflectionState.TRACE_TICKS) continue;
            if (trace.age() < MultiAxisReflectionState.FLASH_TICKS) {
                double fade = 1 - (trace.age() + partial) / MultiAxisReflectionState.FLASH_TICKS;
                // Native body-contact reflection has no impact at the distant geometry intersection.
                cross(strokes, trace.reflectionPoint(), right, up, .15, .047, (int)(230 * fade), Role.REFLECT);
            }
            int alpha = (int)(126 * (1 - (trace.age() + partial) / MultiAxisReflectionState.TRACE_TICKS));
            for (int i = 1; i < trace.points().size(); i++)
                addStroke(strokes, trace.points().get(i - 1), trace.points().get(i), .027, alpha, Role.TRAIL);
        }
        if (c.contact() != null && c.contact().phase() == MultiAxisReflectionState.CONTACT
                && c.contactAge() >= 0 && c.contactAge() < MultiAxisReflectionState.CONTACT_TICKS) {
            Point contact = c.contact().arrowPoint();
            int alpha = (int)(208 * (1 - (c.contactAge() + partial) / MultiAxisReflectionState.CONTACT_TICKS));
            // A distinct compact three-ray cue is reserved for a server-confirmed HP/absorption loss.
            for (int i = 0; i < 3; i++) {
                double angle = Math.PI * 2 * i / 3;
                Point direction = add(scale(right, Math.cos(angle)), scale(up, Math.sin(angle)));
                addStroke(strokes, add(contact, scale(direction, .06)), add(contact, scale(direction, .23)), .035, alpha, Role.CONTACT);
            }
        }
        Shape result = new Shape(strokes, panels);
        return result.vertexCount() <= MAX_CONTEXT_VERTICES ? result : new Shape(List.of(), List.of());
    }
    private static void cross(List<Stroke> strokes, Point center, Point right, Point up, double radius, double width, int alpha, Role role) {
        addStroke(strokes, add(center, scale(right, -radius)), add(center, scale(right, radius)), width, alpha, role);
        addStroke(strokes, add(center, scale(up, -radius)), add(center, scale(up, radius)), width, alpha, role);
    }
    public static Profile profile(Stroke stroke, boolean core) {
        int gray = stroke.role == Role.EDGE_UNAVAILABLE || stroke.role == Role.QUOTA_USED ? 169 : 243;
        boolean edge = stroke.role == Role.EDGE_READY || stroke.role == Role.EDGE_UNAVAILABLE;
        // The finite facets are separated by only ~5.6 cm: even soft shoulders must leave that gap open.
        double softWidth = edge ? Math.min(.045, stroke.width * 2.4) : stroke.width * 2.4;
        return new Profile(core ? stroke.width : softWidth, core ? gray : 154,
                (int)Math.round(stroke.alpha * (core ? 1 : .28)), core ? 11f / 32 : 0, core ? 21f / 32 : 1);
    }
    public static Point relative(Point world, Point camera) { return subtract(world, camera); }
    /** Gentle distance fade prevents pressure surfaces and edge shoulders filling the near-eye view. */
    public static double nearFade(Point relative) {
        if (relative == null || !relative.finite()) return 0;
        double distance = Math.sqrt(dot(relative, relative));
        return Math.max(0, Math.min(1, (distance - .13) / .42));
    }
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
    /** Whole panels intersecting the eye plane disappear; they cannot become an opaque near-plane slab. */
    public static boolean panelVisible(Panel panel, Point camera, Point forward) {
        return panel != null && panel.corners.size() == 4 && validPoint(camera) && unit(forward)
                && panel.corners.stream().allMatch(p -> validPoint(p) && dot(relative(p, camera), forward) > NEAR_PLANE + .02);
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
        if (validPoint(a) && validPoint(b) && width > 0 && alpha > 0 && a.distanceSquared(b) > 1e-16)
            out.add(new Stroke(a, b, width, alpha, role));
    }
    private static Vector vector(Point p) { return new Vector(p.x, p.y, p.z); }
    private static Point point(Vector p) { return new Point(p.x(), p.y(), p.z()); }
    private static Point add(Point a, Point b) { return new Point(a.x + b.x, a.y + b.y, a.z + b.z); }
    private static Point subtract(Point a, Point b) { return new Point(a.x - b.x, a.y - b.y, a.z - b.z); }
    private static Point scale(Point a, double s) { return new Point(a.x * s, a.y * s, a.z * s); }
    private static Point lerp(Point a, Point b, double t) { return add(a, scale(subtract(b, a), t)); }
    private static double dot(Point a, Point b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
}
