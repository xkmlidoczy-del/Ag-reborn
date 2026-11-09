package cn.academy.port.client;

import cn.academy.port.client.WindPressureLiftMesh.Point;
import cn.academy.port.highpower.core.WindPressureLiftRules.Mode;
import java.util.ArrayList;
import java.util.List;

/** Pure optional airflow geometry. Receipt state and movement authority remain with their existing owners. */
public final class WindPressureLiftFlowMesh {
    public static final int RIBBONS = 4, RIBBON_SEGMENTS = 8, GROUND_SEGMENTS = 12, CYCLE_TICKS = 56;
    public static final int MAX_CONTEXTS = WindPressureLiftMesh.MAX_CONTEXTS,
            MAX_VERTICES = WindPressureLiftMesh.MAX_VERTICES,
            MAX_CONTEXT_VERTICES = WindPressureLiftMesh.MAX_CONTEXT_VERTICES,
            MAX_PARTICLES = 0;
    public static final double RADIUS = 3, HEIGHT = 6, MAX_WIDTH = .54, CORE_WIDTH_SCALE = .28;
    private static final double TAU = Math.PI * 2;
    private static final double[] PHASE_OFFSETS = {.07, .31, .56, .81};
    private static final double[] RADII = {1.70, 2.04, 1.87, 2.22};
    private static final double[] LENGTHS = {2.45, 2.75, 2.30, 2.60};

    /** Widths are full soft-pass widths; the renderer scales these for its narrow core pass. */
    public record Segment(Point a, Point b, double widthFrom, double widthTo, int alphaFrom, int alphaTo,
                          double uFrom, double uTo) {}
    public record Shape(List<Segment> segments) {
        public Shape { segments = List.copyOf(segments); }
        /** Four vertices for each of the core and soft passes, including zero-alpha tips. */
        public int vertexCount() { return segments.size() * 8; }
    }
    private record Sample(Point point, double width, int alpha) {}
    private static final Shape EMPTY = new Shape(List.of());

    private WindPressureLiftFlowMesh() {}

    public static Shape shape(WindPressureLiftState.Controller controller, double partial) {
        if (controller == null || !WindPressureLiftState.valid(controller.frame())
                || controller.frame().terminal() || controller.age() < 0 || controller.quiet() < 0
                || controller.quiet() >= WindPressureLiftState.QUIET_TICKS
                || !Double.isFinite(partial) || partial < 0 || partial >= 1) return EMPTY;
        var frame = controller.frame();
        var segments = new ArrayList<Segment>(RIBBONS * RIBBON_SEGMENTS + GROUND_SEGMENTS);
        // Reduce the integer age first so the fractional phase also survives very large ages.
        double clock = ((controller.age() % CYCLE_TICKS) + partial) / CYCLE_TICKS;
        for (int ribbon = 0; ribbon < RIBBONS; ribbon++) {
            double phase = fraction(clock + PHASE_OFFSETS[ribbon]);
            Sample previous = sample(frame, ribbon, phase, 0);
            for (int step = 1; step <= RIBBON_SEGMENTS; step++) {
                Sample next = sample(frame, ribbon, phase, step / (double) RIBBON_SEGMENTS);
                segments.add(new Segment(previous.point, next.point, previous.width, next.width,
                        previous.alpha, next.alpha, (step - 1) / (double) RIBBON_SEGMENTS,
                        step / (double) RIBBON_SEGMENTS));
                previous = next;
            }
        }
        // Short separated arcs occupy under half the circumference. The fourfold pattern makes
        // this quarter-turn continuous as a set at clock wrap, without a closed or filled floor.
        for (int arc = 0; arc < 16; arc++) if (arc % 4 != 3) {
            double rotation = clock * TAU / 4;
            double angle = TAU * arc / 16 + rotation, end = TAU * (arc + .58) / 16 + rotation;
            Point a = add(frame.anchor(), new Point(Math.cos(angle) * 2.95, .045, Math.sin(angle) * 2.95));
            Point b = add(frame.anchor(), new Point(Math.cos(end) * 2.95, .045, Math.sin(end) * 2.95));
            segments.add(new Segment(a, b, .07, .035, 100, 35, 0, 1));
        }
        return new Shape(segments);
    }

    private static Sample sample(WindPressureLiftState.Frame frame, int ribbon, double phase, double t) {
        double length = LENGTHS[ribbon];
        // Each short packet travels upward, fading before its reset instead of drawing a wraparound line.
        double y = .10 + (5.70 - length) * phase + length * t;
        double angle = ribbon * TAU / RIBBONS + .88 * (t - .5)
                + .27 * Math.sin(Math.PI * t + ribbon * .63) + .52 * phase;
        double radius = RADII[ribbon] + .12 * Math.sin(Math.PI * t + ribbon * .81)
                + .045 * Math.cos(TAU * phase + t * 2.1);
        double bend = frame.mode() == Mode.SIDE ? .32 * Math.pow(y / 5.8, 2) : 0;
        Point local = new Point(Math.cos(angle) * radius + frame.direction().x() * bend, y,
                Math.sin(angle) * radius + frame.direction().z() * bend);
        // Exact zero tips prevent a line cap, while the sine shoulder keeps both passes tapered.
        double taper = t <= 0 || t >= 1 ? 0 : Math.sin(Math.PI * t);
        double visibility = smooth(Math.min(phase, 1 - phase) / .11);
        double width = (.42 + ribbon * .04) * taper;
        int alpha = (int) Math.round((190 + ribbon * 6) * taper * visibility);
        return new Sample(add(frame.anchor(), local), width, alpha);
    }

    /** Camera-relative clipping interpolates the taper and opacity rather than creating a bright cut cap. */
    public static Segment clipNear(Segment segment, Point forward, double halfWidth) {
        if (!valid(segment) || forward == null || !forward.finite()
                || Math.abs(dot(forward, forward) - 1) > .002
                || !Double.isFinite(halfWidth) || halfWidth < 0 || halfWidth > 1) return null;
        double plane = WindPressureLiftMesh.NEAR_PLANE + halfWidth;
        double a = dot(segment.a, forward), b = dot(segment.b, forward);
        if (!Double.isFinite(a) || !Double.isFinite(b) || a < plane && b < plane) return null;
        if (a >= plane && b >= plane) return segment;
        double amount = (plane - a) / (b - a);
        Point crossing = lerp(segment.a, segment.b, amount);
        if (!crossing.finite()) return null;
        double width = lerp(segment.widthFrom, segment.widthTo, amount);
        int alpha = (int) Math.round(lerp(segment.alphaFrom, segment.alphaTo, amount));
        double u = lerp(segment.uFrom, segment.uTo, amount);
        return a < plane
                ? new Segment(crossing, segment.b, width, segment.widthTo, alpha, segment.alphaTo, u, segment.uTo)
                : new Segment(segment.a, crossing, segment.widthFrom, width, segment.alphaFrom, alpha, segment.uFrom, u);
    }

    private static boolean valid(Segment segment) {
        return segment != null && segment.a != null && segment.a.finite() && segment.b != null && segment.b.finite()
                && Double.isFinite(segment.widthFrom) && Double.isFinite(segment.widthTo)
                && segment.widthFrom >= 0 && segment.widthFrom <= MAX_WIDTH
                && segment.widthTo >= 0 && segment.widthTo <= MAX_WIDTH
                && segment.alphaFrom >= 0 && segment.alphaFrom <= 255
                && segment.alphaTo >= 0 && segment.alphaTo <= 255
                && Double.isFinite(segment.uFrom) && Double.isFinite(segment.uTo)
                && segment.uFrom >= 0 && segment.uFrom <= 1 && segment.uTo >= 0 && segment.uTo <= 1;
    }

    private static double fraction(double value) { return value - Math.floor(value); }
    private static double smooth(double value) { double t = Math.max(0, Math.min(1, value)); return t * t * (3 - 2 * t); }
    private static Point add(Point a, Point b) { return new Point(a.x() + b.x(), a.y() + b.y(), a.z() + b.z()); }
    private static double dot(Point a, Point b) { return a.x() * b.x() + a.y() * b.y() + a.z() * b.z(); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static Point lerp(Point a, Point b, double t) {
        return new Point(lerp(a.x(), b.x(), t), lerp(a.y(), b.y(), t), lerp(a.z(), b.z(), t));
    }
}
