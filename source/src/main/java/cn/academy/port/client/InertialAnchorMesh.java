package cn.academy.port.client;

import java.util.ArrayList;
import java.util.List;

/** NEW422 foot-level vector notation, never a collision volume, shield or predicted impact. */
public final class InertialAnchorMesh {
    public static final int MAX_CONTEXTS = 4, MAX_VERTICES = 2048, MAX_PARTICLES = 0;
    public static final int ARC_SEGMENTS = 8, LANDING_SEGMENTS = 20, MATERIAL_LAYERS = 2;
    public static final int MAX_STROKES = 2 * ARC_SEGMENTS + 4 * 3 + 4 + LANDING_SEGMENTS;
    public static final int MAX_CONTEXT_VERTICES = MAX_STROKES * MATERIAL_LAYERS * 4;
    public static final double CULL_DISTANCE = 64, NEAR_PLANE = .08;
    public enum Role { ANCHOR, DOWN, PRESSURE, LANDING }
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
    private InertialAnchorMesh() {}
    public static boolean validPoint(Point p) {
        return p != null && p.finite() && Math.abs(p.x) <= 30_000_000
                && Math.abs(p.y) <= 4096 && Math.abs(p.z) <= 30_000_000;
    }
    public static boolean unit(Point p) { return p != null && p.finite() && Math.abs(dot(p, p) - 1) <= .002; }
    public static boolean horizontalUnit(Point p) { return unit(p) && Math.abs(p.y) <= .001; }
    /** Body-relative stance follows walking/falling; success contacts remain in receipt world space. */
    public static Shape shape(InertialAnchorState.Controller controller, Point bodyFeet, boolean bodyGrounded, double partial) {
        if (controller == null || !InertialAnchorState.valid(controller.frame()) || !validPoint(bodyFeet)
                || controller.frame().phase() == InertialAnchorState.ENDED || controller.quiet() < 0
                || controller.quiet() >= InertialAnchorState.QUIET_TICKS || !Double.isFinite(partial)
                || partial < 0 || partial >= 1) return new Shape(List.of());
        var frame = controller.frame();
        Point forward = horizontal(frame.facing()), right = new Point(forward.z, 0, -forward.x);
        // Ground state never comes from a projected floor. Airborne symbols stay visibly above the feet.
        boolean grounded = frame.grounded() && bodyGrounded;
        double height = grounded ? .035 : .19, radius = grounded ? .43 : .34;
        double start = Math.toRadians(grounded ? 20 : 45), end = Math.PI - start;
        int alpha = grounded ? 174 : 87;
        var strokes = new ArrayList<Stroke>(MAX_STROKES);
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < ARC_SEGMENTS; i++) {
                double a = start + (end - start) * i / ARC_SEGMENTS + side * Math.PI;
                double b = start + (end - start) * (i + 1) / ARC_SEGMENTS + side * Math.PI;
                addStroke(strokes, radial(bodyFeet, forward, right, radius, height, a),
                        radial(bodyFeet, forward, right, radius, height, b), .022, alpha, Role.ANCHOR);
            }
        }
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 4 + i * Math.PI / 2;
            Point tip = radial(bodyFeet, forward, right, radius * .9, grounded ? .075 : .23, angle);
            double length = grounded ? .27 : .15;
            addStroke(strokes, add(tip, new Point(0, length, 0)), tip, .019, alpha, Role.DOWN);
            Point wing = scale(right, .037);
            addStroke(strokes, add(add(tip, wing), new Point(0, .053, 0)), tip, .014, alpha, Role.DOWN);
            addStroke(strokes, add(subtract(tip, wing), new Point(0, .053, 0)), tip, .014, alpha, Role.DOWN);
        }
        var pressure = controller.pressure();
        if (pressure != null && pressure.id().equals(frame.id()) && pressure.phase() == InertialAnchorState.KNOCKBACK
                && controller.pressureAge() >= 0 && controller.pressureAge() < InertialAnchorState.PRESSURE_TICKS) {
            double fade = 1 - (controller.pressureAge() + partial) / InertialAnchorState.PRESSURE_TICKS;
            Point towardContact = subtract(pressure.contact(), pressure.point());
            towardContact = new Point(towardContact.x, 0, towardContact.z);
            double distance = Math.sqrt(dot(towardContact, towardContact));
            Point toward = distance > 1e-7 ? scale(towardContact, 1 / distance) : horizontal(pressure.facing());
            Point across = new Point(toward.z, 0, -toward.x);
            for (int i = 0; i < 4; i++) {
                Point center = add(bodyFeet, scale(across, (i - 1.5) * .09));
                Point from = add(add(center, scale(toward, .35 + .21 * fade)), new Point(0, .30 + .28 * fade, 0));
                Point to = add(add(center, scale(toward, .16)), new Point(0, .095, 0));
                addStroke(strokes, from, to, .023, (int)Math.round(214 * fade), Role.PRESSURE);
            }
        }
        var landing = controller.landing();
        if (landing != null && landing.id().equals(frame.id()) && landing.phase() == InertialAnchorState.LANDED
                && controller.landingAge() >= 0 && controller.landingAge() < InertialAnchorState.LANDING_TICKS) {
            double fade = 1 - (controller.landingAge() + partial) / InertialAnchorState.LANDING_TICKS;
            double ringRadius = .46 + .12 * (1 - fade);
            Point landingForward = horizontal(landing.facing());
            Point landingRight = new Point(landingForward.z, 0, -landingForward.x);
            for (int i = 0; i < LANDING_SEGMENTS; i++) {
                double a = 2 * Math.PI * i / LANDING_SEGMENTS, b = 2 * Math.PI * (i + 1) / LANDING_SEGMENTS;
                addStroke(strokes, radial(landing.contact(), landingForward, landingRight, ringRadius, .022, a),
                        radial(landing.contact(), landingForward, landingRight, ringRadius, .022, b),
                        .023 * fade, (int)Math.round(156 * fade), Role.LANDING);
            }
        }
        return new Shape(strokes);
    }
    public static Profile profile(Stroke stroke, boolean core) {
        return new Profile(stroke.width * (core ? 1 : 2.3), core ? 238 : 174,
                (int)Math.round(stroke.alpha * (core ? 1 : .35)), core ? 11f / 32 : 0, core ? 21f / 32 : 1);
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
        if (validPoint(a) && validPoint(b) && width > 0 && alpha > 0 && out.size() < MAX_STROKES)
            out.add(new Stroke(a, b, width, alpha, role));
    }
    private static Point radial(Point center, Point forward, Point right, double radius, double height, double angle) {
        return add(add(center, scale(forward, radius * Math.cos(angle))),
                add(scale(right, radius * Math.sin(angle)), new Point(0, height, 0)));
    }
    /** Pitch is valid receipt data; foot notation stays horizontal even while looking straight up. */
    private static Point horizontal(Point facing) {
        double length = Math.hypot(facing.x, facing.z);
        return length > 1e-7 ? new Point(facing.x / length, 0, facing.z / length) : new Point(0, 0, 1);
    }
    private static Point add(Point a, Point b) { return new Point(a.x + b.x, a.y + b.y, a.z + b.z); }
    private static Point subtract(Point a, Point b) { return new Point(a.x - b.x, a.y - b.y, a.z - b.z); }
    private static Point scale(Point a, double s) { return new Point(a.x * s, a.y * s, a.z * s); }
    private static Point lerp(Point a, Point b, double t) { return add(a, scale(subtract(b, a), t)); }
    private static double dot(Point a, Point b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
}
