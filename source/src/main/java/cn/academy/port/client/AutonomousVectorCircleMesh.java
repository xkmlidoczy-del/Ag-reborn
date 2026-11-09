package cn.academy.port.client;

import java.util.ArrayList;
import java.util.List;

/** NEW452 sparse, open 160-degree forward cap. Geometry never claims reflection or full protection. */
public final class AutonomousVectorCircleMesh {
    public static final int MAX_CONTEXTS = 4, MAX_VERTICES = 4096, MAX_PARTICLES = 0, MATERIAL_LAYERS = 2;
    /** Presentation-only multipliers; the authenticated centerlines and physical quota marks are unchanged. */
    public static final double CORE_WIDTH_SCALE = .55, SOFT_WIDTH_SCALE = 2.0, SOFT_ALPHA_SCALE = .22;
    public static final int ARC_SEGMENTS = 12, FLOW_SEGMENTS = 10, COMPRESSION_SEGMENTS = 12;
    public static final int MAX_STROKES = 4 * ARC_SEGMENTS + 4 * FLOW_SEGMENTS + 8 + COMPRESSION_SEGMENTS;
    public static final int MAX_CONTEXT_VERTICES = MAX_STROKES * MATERIAL_LAYERS * 4 + 4;
    public static final double CULL_DISTANCE = 64, NEAR_PLANE = .08, HALF_ANGLE = Math.toRadians(80);
    private static final Shape EMPTY = new Shape(List.of(), List.of());
    public enum Role { BOUNDARY, FLOW, QUOTA_READY, QUOTA_USED, COMPRESSION }
    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public double distanceSquared(Point p) { return dot(subtract(this, p), subtract(this, p)); }
    }
    public record Stroke(Point from, Point to, double width, int alpha, Role role) {}
    public record Wave(List<Point> corners, int alpha) { public Wave { corners = List.copyOf(corners); } }
    public record Profile(double width, int gray, int alpha, float minV, float maxV) {}
    public record Shape(List<Stroke> strokes, List<Wave> waves) {
        public Shape { strokes = List.copyOf(strokes); waves = List.copyOf(waves); }
        public int vertexCount() { return strokes.size() * MATERIAL_LAYERS * 4 + waves.size() * 4; }
        public int simpleVertexCount() { return strokes.size() * 4; }
    }
    /** Material or budget failure selects a complete simple group, including every controller's base indicator. */
    public record Group(List<Shape> shapes, boolean enhanced, int vertexCount) {
        public Group { shapes = List.copyOf(shapes); }
    }
    private record Basis(Point forward, Point right, Point up) {}
    private AutonomousVectorCircleMesh() {}
    public static boolean validPoint(Point p) {
        return p != null && p.finite() && Math.abs(p.x) <= 30_000_000
                && Math.abs(p.y) <= 4096 && Math.abs(p.z) <= 30_000_000;
    }
    public static boolean unit(Point p) { return p != null && p.finite() && Math.abs(dot(p, p) - 1) <= .002; }
    private static Basis basis(Point forward) {
        Point reference = Math.abs(forward.y) < .98 ? new Point(0, 1, 0) : new Point(0, 0, 1);
        Point right = normalize(cross(reference, forward));
        return new Basis(forward, right, normalize(cross(forward, right)));
    }
    public static Shape shape(AutonomousVectorCircleState.Controller controller, Point feet, double partial) {
        return shape(controller, feet, partial, false);
    }
    public static Shape base(AutonomousVectorCircleState.Controller controller, Point feet, double partial) {
        return shape(controller, feet, partial, true);
    }
    private static Shape shape(AutonomousVectorCircleState.Controller c, Point feet, double partial, boolean simple) {
        if (c == null || !AutonomousVectorCircleState.valid(c.frame()) || !validPoint(feet)
                || c.frame().phase() == AutonomousVectorCircleState.ENDED || c.quiet() < 0
                || c.quiet() >= AutonomousVectorCircleState.QUIET_TICKS || !Double.isFinite(partial)
                || partial < 0 || partial >= 1) return EMPTY;
        var f = c.frame(); Basis basis = basis(f.facing());
        Point center = add(feet, new Point(0, 1, 0));
        boolean ready = f.ready() && f.remainingHits() > 0;
        int alpha = ready ? 176 : 72;
        var strokes = new ArrayList<Stroke>(MAX_STROKES);
        var waves = new ArrayList<Wave>(1);
        // Four separated outer arcs expose real gaps, and all lie inside the 80-degree half-cone.
        // There is no rear shell or broad translucent surface.
        int segments = simple ? 3 : ARC_SEGMENTS;
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            double start = Math.toRadians(15 + quadrant * 90);
            for (int i = 0; i < segments; i++) {
                double a = start + Math.PI / 3 * i / segments, b = start + Math.PI / 3 * (i + 1) / segments;
                addStroke(strokes, cap(center, basis, 1.12, HALF_ANGLE, a),
                        cap(center, basis, 1.12, HALF_ANGLE, b), .037, alpha, Role.BOUNDARY);
            }
        }
        if (!simple) for (int meridian = 0; meridian < 4; meridian++) {
            double azimuth = Math.PI / 4 + meridian * Math.PI / 2;
            for (int i = 0; i < FLOW_SEGMENTS; i++) {
                double a = Math.toRadians(32 + 48.0 * i / FLOW_SEGMENTS);
                double b = Math.toRadians(32 + 48.0 * (i + 1) / FLOW_SEGMENTS);
                // Animated brightness stays on fixed sector geometry; it never suggests a changed projectile path.
                double phase = (c.age() + partial) * .10 - i * .52 - meridian * .7;
                int flowAlpha = (int)Math.round(alpha * (.40 + .45 * (.5 + .5 * Math.cos(phase))));
                addStroke(strokes, cap(center, basis, 1.10, a, azimuth), cap(center, basis, 1.10, b, azimuth),
                        .031, flowAlpha, Role.FLOW);
            }
        }
        // Eight physical admission marks persist. Only receipts can replenish them.
        for (int i = 0; i < 8; i++) {
            boolean available = i < f.remainingHits();
            // A compact row inside the front sector remains readable in ordinary first-person view.
            // It is below the eye line, leaving the central target clear; it is not a range boundary.
            Point anchor = add(center, add(scale(basis.forward, 1.12), scale(basis.right, (i - 3.5) * .13)));
            addStroke(strokes, add(anchor, scale(basis.up, .23)), add(anchor, scale(basis.up, .33)), .045,
                    available ? ready ? 224 : 125 : 64, available ? Role.QUOTA_READY : Role.QUOTA_USED);
        }
        var hit = c.absorption();
        if (hit != null && hit.id().equals(f.id()) && hit.phase() == AutonomousVectorCircleState.ABSORBED
                && c.absorptionAge() >= 0 && c.absorptionAge() < AutonomousVectorCircleState.ABSORPTION_TICKS) {
            double age = (c.absorptionAge() + partial) / AutonomousVectorCircleState.ABSORPTION_TICKS;
            int hitAlpha = (int)Math.round(225 * (1 - age));
            // Shrinking local pressure marks stay at the actual contact, never at the 8 m source position.
            Point normal = normalize(subtract(hit.source(), hit.contact()));
            if (!unit(normal)) normal = hit.facing();
            Basis contactBasis = basis(normal);
            double radius = .11 + .22 * (1 - age);
            int hitSegments = simple ? 6 : COMPRESSION_SEGMENTS;
            for (int i = 0; i < hitSegments; i++) {
                double a = 2 * Math.PI * i / hitSegments, b = a + Math.PI / hitSegments;
                addStroke(strokes, ring(hit.contact(), contactBasis, radius, a), ring(hit.contact(), contactBasis, radius, b),
                        .041, hitAlpha, Role.COMPRESSION);
            }
            if (!simple) {
                // Original vector-wave glow_circle material, compact and inward-compressing.
                double r = radius * 1.25;
                Point right = scale(contactBasis.right, r), up = scale(contactBasis.up, r);
                waves.add(new Wave(List.of(subtract(subtract(hit.contact(), right), up),
                        add(subtract(hit.contact(), right), up), add(add(hit.contact(), right), up),
                        subtract(add(hit.contact(), right), up)), (int)Math.round(hitAlpha * .43)));
            }
        }
        return new Shape(strokes, waves);
    }
    public static Group group(List<Shape> enhanced, List<Shape> base, boolean materialsReady) {
        return group(enhanced, base, materialsReady, MAX_VERTICES);
    }
    /** Atomic scene selection, with a bounded simple base reserved for all four admitted owners. */
    public static Group group(List<Shape> enhanced, List<Shape> base, boolean materialsReady, int ceiling) {
        if (enhanced == null || base == null || enhanced.size() != base.size() || enhanced.size() > MAX_CONTEXTS
                || ceiling <= 0 || ceiling > MAX_VERTICES) return new Group(List.of(), false, 0);
        long full = enhanced.stream().mapToLong(Shape::vertexCount).sum();
        if (materialsReady && full <= ceiling && enhanced.stream().allMatch(s -> s.vertexCount() <= MAX_CONTEXT_VERTICES))
            return new Group(enhanced, true, (int)full);
        long basic = base.stream().mapToLong(Shape::simpleVertexCount).sum();
        return basic <= ceiling ? new Group(base, false, (int)basic) : new Group(List.of(), false, 0);
    }
    /** Original complete material profile; the default/OFF route retains this exact presentation. */
    public static Profile profile(Stroke stroke, boolean core) {
        int gray = stroke.role == Role.QUOTA_USED ? 168 : 241;
        return new Profile(stroke.width * (core ? 1 : 2.3), core ? gray : 159,
                (int)Math.round(stroke.alpha * (core ? 1 : .30)), core ? 11f / 32 : 0, core ? 21f / 32 : 1);
    }
    public static Profile profile(Stroke stroke, boolean core, boolean softMaterial) {
        if (!softMaterial) return profile(stroke, core);
        int gray = stroke.role == Role.QUOTA_USED ? 168 : 241;
        return new Profile(stroke.width * (core ? CORE_WIDTH_SCALE : SOFT_WIDTH_SCALE), core ? gray : 159,
                (int)Math.round(stroke.alpha * (core ? 1 : SOFT_ALPHA_SCALE)), core ? 11f / 32 : 0, core ? 21f / 32 : 1);
    }
    public static Point relative(Point world, Point camera) { return subtract(world, camera); }
    public static double nearFade(Point relative) {
        if (relative == null || !relative.finite()) return 0;
        return Math.max(0, Math.min(1, (Math.sqrt(dot(relative, relative)) - .13) / .40));
    }
    /** Keep a clear first-person aiming corridor, including pitched-up/down views and contact cues. */
    public static boolean outsideAim(Stroke s, Point forward) {
        if (s == null || !unit(forward)) return false;
        Point delta = subtract(s.to, s.from);
        double depthA = dot(s.from, forward), depthD = dot(delta, forward);
        Point lateralA = subtract(s.from, scale(forward, depthA));
        Point lateralD = subtract(delta, scale(forward, depthD));
        double guardA = .17 * depthA + s.width * 1.2, guardD = .17 * depthD;
        double quadratic = dot(lateralD, lateralD) - guardD * guardD;
        double linear = 2 * (dot(lateralA, lateralD) - guardA * guardD);
        double constant = dot(lateralA, lateralA) - guardA * guardA;
        if (depthA <= 0 || depthA + depthD <= 0) return false; // Production calls after near clipping.
        if (constant <= 0 || quadratic + linear + constant <= 0) return false;
        double t = quadratic > 0 ? Math.max(0, Math.min(1, -linear / (2 * quadratic))) : 0;
        return quadratic * t * t + linear * t + constant > 0;
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
    public static boolean waveVisible(Wave wave, Point camera, Point forward, boolean firstPerson) {
        if (wave == null || wave.corners.size() != 4 || !validPoint(camera) || !unit(forward)) return false;
        for (int i = 0; i < 4; i++) {
            Point a = relative(wave.corners.get(i), camera), b = relative(wave.corners.get((i + 1) % 4), camera);
            if (!a.finite() || dot(a, forward) <= NEAR_PLANE + .02
                    || firstPerson && !outsideAim(new Stroke(a, b, .02, 1, Role.COMPRESSION), forward)) return false;
        }
        // Suppress quads spanning the central aim even if their edges happen to surround it.
        Point midpoint = relative(scale(add(add(wave.corners.get(0), wave.corners.get(1)),
                add(wave.corners.get(2), wave.corners.get(3))), .25), camera);
        return !firstPerson || outsideAim(new Stroke(midpoint, midpoint, .4, 1, Role.COMPRESSION), forward);
    }
    private static void addStroke(List<Stroke> out, Point a, Point b, double width, int alpha, Role role) {
        if (validPoint(a) && validPoint(b) && width > 0 && alpha > 0 && a.distanceSquared(b) > 1e-16)
            out.add(new Stroke(a, b, width, alpha, role));
    }
    private static Point cap(Point center, Basis basis, double radius, double polar, double azimuth) {
        return add(center, add(scale(basis.forward, radius * Math.cos(polar)),
                scale(add(scale(basis.right, Math.cos(azimuth)), scale(basis.up, Math.sin(azimuth))), radius * Math.sin(polar))));
    }
    private static Point ring(Point center, Basis basis, double radius, double angle) {
        return add(center, add(scale(basis.right, radius * Math.cos(angle)), scale(basis.up, radius * Math.sin(angle))));
    }
    private static Point normalize(Point p) { double n = Math.sqrt(dot(p, p)); return n < 1e-9 ? new Point(0, 0, 0) : scale(p, 1 / n); }
    private static Point cross(Point a, Point b) { return new Point(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x); }
    private static Point add(Point a, Point b) { return new Point(a.x + b.x, a.y + b.y, a.z + b.z); }
    private static Point subtract(Point a, Point b) { return new Point(a.x - b.x, a.y - b.y, a.z - b.z); }
    private static Point scale(Point a, double s) { return new Point(a.x * s, a.y * s, a.z * s); }
    private static Point lerp(Point a, Point b, double t) { return add(a, scale(subtract(b, a), t)); }
    private static double dot(Point a, Point b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
}
