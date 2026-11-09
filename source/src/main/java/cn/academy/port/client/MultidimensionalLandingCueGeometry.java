package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * NEW351 optional, local coordinate-fold decoration. It takes one authenticated receipt point,
 * never a target or trajectory. Eligibility, loaded terrain, visibility, materials and the shared
 * frame budget belong to the render owner; this helper has no clock, particles or game state.
 */
public final class MultidimensionalLandingCueGeometry {
    public static final int LIFE_TICKS = 7, CONTOUR_SEGMENTS = 12, MATERIAL_LAYERS = 3;
    public static final int MAX_STROKES = CONTOUR_SEGMENTS * MATERIAL_LAYERS;
    public static final int MAX_VERTICES = MAX_STROKES * 4, MAX_SCENE_VERTICES = 2048, MAX_PARTICLES = 0;
    public static final double MAX_RADIUS = .7, NEAR_PLANE = .08;
    public static final double MIN_CORE_HALF_WIDTH = .022, MAX_CORE_HALF_WIDTH = .025;
    public static final double MAX_SHOULDER_HALF_WIDTH = .060;
    private static final double EPSILON = 1e-10;
    private static final Point X = new Point(1, 0, 0), Y = new Point(0, 1, 0), Z = new Point(0, 0, 1);

    public enum Phase { EARLY, MID, FADE, EXPIRED }
    /** All three are gray-white; the owner maps these roles to original, already available textures. */
    public enum Role { GLOW, CORE, ATLAS }
    public record Stroke(Point from, Point to, double halfWidth, int rgb, int alpha, Role role) {}
    public record Shape(List<Stroke> strokes, Phase phase) {
        public Shape { strokes = List.copyOf(strokes); }
        public int vertexCount() { return strokes.size() * 4; }
    }
    /** Camera-relative doubles: cast only when submitting these already prepared vertices. */
    public record Quad(Point a, Point b, Point c, Point d, int rgb, int alpha, Role role) {}

    private MultidimensionalLandingCueGeometry() {}

    public static Phase phase(double age) {
        if (!Double.isFinite(age) || age < 0 || age >= LIFE_TICKS) return Phase.EXPIRED;
        return age < 2 ? Phase.EARLY : age < 4.5 ? Phase.MID : Phase.FADE;
    }

    /** The caller's integer clock already stops on pause; ignore a moving render fraction then. */
    public static double age(int age, double partial, boolean paused) {
        if (age < 0 || age >= LIFE_TICKS || !Double.isFinite(partial)) return Double.NaN;
        return age + (paused ? 0 : Math.max(0, Math.min(Math.nextDown(1.0), partial)));
    }

    public static Shape shape(Point at, boolean contact, int age, double partial, boolean paused) {
        return shape(at, contact, age(age, partial, paused));
    }

    public static Shape shape(Point at, boolean contact, double age) {
        Phase phase = phase(age);
        if (!receiptPoint(at) || phase == Phase.EXPIRED) return new Shape(List.of(), Phase.EXPIRED);
        var strokes = new ArrayList<Stroke>(MAX_STROKES);
        double fold = smooth(age / 4.5);
        double fade = smooth((age - 3.5) / 3.5);
        double alpha = (.80 + .20 * smooth(age / .8)) * (1 - fade);
        if (contact) fragments(strokes, at, fold, fade, alpha);
        else panels(strokes, at, fold, fade, alpha);
        return new Shape(strokes, phase);
    }

    private static void panels(List<Stroke> strokes, Point at, double fold, double fade, double alpha) {
        // Four open, upright folded panel edges. There is deliberately no outer closing edge,
        // filled face, ring, central crosshair, traveling center or source-to-contact connection.
        double outer = .46 - .20 * fold, inner = .19 - .12 * fold;
        double height = .28 - .10 * fold, pleat = .12 - .07 * fold;
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI * (.25 + .5 * i);
            Point radial = new Point(Math.cos(angle), 0, Math.sin(angle));
            Point tangent = new Point(-radial.z(), 0, radial.x());
            Point a = offset(at, radial, outer, tangent, pleat, height);
            Point b = offset(at, radial, inner, tangent, 0, height * .68);
            Point c = offset(at, radial, inner, tangent, 0, -height * .68);
            Point d = offset(at, radial, outer, tangent, -pleat, -height);
            layers(strokes, a, b, fade, alpha);
            layers(strokes, b, c, fade, alpha);
            layers(strokes, c, d, fade, alpha);
        }
    }

    private static void fragments(List<Stroke> strokes, Point at, double fold, double fade, double alpha) {
        // An uneven, compressed set of six short coordinate fragments. They separate and shorten
        // in place, without the circular symmetry or continuous perimeter of a force/portal ring.
        double[] xs = {-1, -.35, .55, 1, .15, -.70};
        double[] ys = {.45, -.90, .90, -.35, .10, .95};
        double spread = .075 + .26 * fold, length = .125 - .045 * fold;
        for (int i = 0; i < xs.length; i++) {
            double sign = i % 2 == 0 ? 1 : -1;
            Point a = add(at, new Point(xs[i] * spread, ys[i] * spread * .70, sign * (.026 + .07 * fold)));
            Point b = add(a, new Point(sign * length, .018 * sign, -sign * .035));
            Point c = add(b, new Point(sign * .012, -sign * length * .52, sign * .018));
            layers(strokes, a, b, fade, alpha);
            layers(strokes, b, c, fade, alpha);
        }
    }

    private static void layers(List<Stroke> strokes, Point a, Point b, double fade, double alpha) {
        // Widths below are half widths: the readable core stays .044-.050 m across its life.
        double core = MAX_CORE_HALF_WIDTH - (MAX_CORE_HALF_WIDTH - MIN_CORE_HALF_WIDTH) * fade;
        strokes.add(new Stroke(a, b, MAX_SHOULDER_HALF_WIDTH * (1 - .12 * fade), 0xBBC1C1,
                (int)Math.round(64 * alpha), Role.GLOW));
        strokes.add(new Stroke(a, b, core, 0xEEF0F0, (int)Math.round(232 * alpha), Role.CORE));
        strokes.add(new Stroke(a, b, core * 1.15, 0xDFE3E3, (int)Math.round(154 * alpha), Role.ATLAS));
    }

    /**
     * Prepare the complete optional cue before writing any buffer. Any invalid or near-plane
     * stroke refuses the entire batch, so the caller can render its complete original cue instead.
     * Conservative full-width clipping avoids both partial silhouettes and new clip-end caps.
     */
    public static List<Quad> prepare(Shape shape, Point camera, Point forward) {
        Point view = unit(forward);
        if (shape == null || shape.phase == null || shape.phase == Phase.EXPIRED || shape.strokes.isEmpty()
                || shape.strokes.size() > MAX_STROKES || !worldPoint(camera) || view == null) return List.of();
        var quads = new ArrayList<Quad>(shape.strokes.size());
        for (Stroke stroke : shape.strokes) {
            if (!valid(stroke)) return List.of();
            Point a = relative(stroke.from, camera), b = relative(stroke.to, camera);
            Point direction = unit(subtract(b, a));
            if (direction == null || Math.min(dot(a, view), dot(b, view)) <= NEAR_PLANE + stroke.halfWidth)
                return List.of();
            // Build a facing side from the short relative vectors, never large world floats.
            Point eye = unit(scale(add(a, b), -.5));
            Point side = eye == null ? null : unit(cross(direction, eye));
            if (side == null) side = unit(cross(direction, view));
            if (side == null) side = unit(cross(direction, leastAligned(direction)));
            if (side == null) return List.of();
            side = scale(side, stroke.halfWidth);
            Quad quad = new Quad(subtract(a, side), add(a, side), add(b, side), subtract(b, side),
                    stroke.rgb, stroke.alpha, stroke.role);
            for (Point vertex : List.of(quad.a, quad.b, quad.c, quad.d))
                if (!finite(vertex) || dot(vertex, view) <= NEAR_PLANE) return List.of();
            quads.add(quad);
        }
        return List.copyOf(quads);
    }

    /** Keep subtraction in double; world-border fractions must survive the float render boundary. */
    public static Point relative(Point world, Point camera) {
        if (!finite(world) || !finite(camera)) return null;
        Point relative = subtract(world, camera);
        return finite(relative) ? relative : null;
    }

    private static boolean valid(Stroke stroke) {
        return stroke != null && worldPoint(stroke.from) && worldPoint(stroke.to)
                && Double.isFinite(stroke.halfWidth) && stroke.halfWidth > 0
                && stroke.halfWidth <= MAX_SHOULDER_HALF_WIDTH && stroke.rgb >= 0 && stroke.rgb <= 0xFFFFFF
                && stroke.alpha >= 0 && stroke.alpha <= 255 && stroke.role != null;
    }
    private static boolean worldPoint(Point p) {
        return finite(p) && Math.abs(p.x()) <= 30_000_002 && Math.abs(p.z()) <= 30_000_002
                && Math.abs(p.y()) <= 1_000_001;
    }
    private static boolean receiptPoint(Point p) {
        return worldPoint(p) && Math.abs(p.x()) <= 30_000_001 && Math.abs(p.z()) <= 30_000_001
                && Math.abs(p.y()) <= 1_000_000;
    }
    private static boolean finite(Point p) {
        return p != null && Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z());
    }
    private static Point leastAligned(Point p) {
        return Math.abs(p.x()) <= Math.abs(p.y()) && Math.abs(p.x()) <= Math.abs(p.z()) ? X
                : Math.abs(p.y()) <= Math.abs(p.z()) ? Y : Z;
    }
    private static Point unit(Point p) {
        if (!finite(p)) return null;
        double max = Math.max(Math.abs(p.x()), Math.max(Math.abs(p.y()), Math.abs(p.z())));
        if (max < EPSILON) return null;
        Point scaled = scale(p, 1 / max);
        double length = Math.sqrt(dot(scaled, scaled));
        return finite(scaled) && Double.isFinite(length) && length > 0 ? scale(scaled, 1 / length) : null;
    }
    private static double smooth(double value) {
        double x = Math.max(0, Math.min(1, value));
        return x * x * (3 - 2 * x);
    }
    private static Point offset(Point at, Point radial, double r, Point tangent, double t, double y) {
        return add(at, add(add(scale(radial, r), scale(tangent, t)), new Point(0, y, 0)));
    }
    private static Point add(Point a, Point b) { return new Point(a.x() + b.x(), a.y() + b.y(), a.z() + b.z()); }
    private static Point subtract(Point a, Point b) { return new Point(a.x() - b.x(), a.y() - b.y(), a.z() - b.z()); }
    private static Point scale(Point p, double scale) { return new Point(p.x() * scale, p.y() * scale, p.z() * scale); }
    private static double dot(Point a, Point b) { return a.x() * b.x() + a.y() * b.y() + a.z() * b.z(); }
    private static Point cross(Point a, Point b) {
        return new Point(a.y() * b.z() - a.z() * b.y(), a.z() * b.x() - a.x() * b.z(), a.x() * b.y() - a.y() * b.x());
    }
}
