package cn.academy.port.client;

import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGeometry.Segment;
import java.util.ArrayList;
import java.util.List;

/** Cosmetic material on NEW152's existing six circulation paths; never a gameplay boundary. */
public final class MagneticDomainFlowGeometry {
    public static final int PATHS = 6, SEGMENTS_PER_PATH = 4, MATERIAL_LAYERS = 2;
    public static final int PRECLIP_QUADS = PATHS * SEGMENTS_PER_PATH * MATERIAL_LAYERS;
    public static final int MAX_QUADS_PER_CONTROLLER = MagneticDomainGeometry.MAX_QUADS;
    public static final int MAX_VISIBLE_CONTROLLERS = 4, MAX_PARTICLES = 0;
    public static final int MAX_VERTICES = MAX_QUADS_PER_CONTROLLER * MAX_VISIBLE_CONTROLLERS * 4;
    public static final int CLOCK_TICKS = 1200, HIGHLIGHT_TICKS = 120;
    /** Half-widths: the complete decorative footprints are at most .070 m and .016 m. */
    public static final double SHOULDER_HALF_WIDTH = .035, CORE_HALF_WIDTH = .008;
    private static final double EPS = 1e-8;

    public enum Layer { SHOULDER, CORE }
    public record FlowSegment(Segment segment, int path, double startT, double endT) {}
    public record Profile(double halfWidth, int rgb, int alpha, float minV, float maxV) {}
    /** Points and offsets are camera-relative doubles. Profiles retain the original path coordinates. */
    public record FlowRibbon(Point start, Point end, Point startSide, Point endSide,
                             double startT, double endT, Profile startProfile, Profile endProfile) {}

    private MagneticDomainFlowGeometry() {}

    /** Uses the existing bounded tick clock only; paused frames never consume render interpolation. */
    public static double time(int frame, double partial, boolean paused) {
        double tick = Math.floorMod(frame, CLOCK_TICKS);
        if (paused) return tick;
        if (!Double.isFinite(partial)) return Double.NaN;
        return wrap(tick + Math.max(0, Math.min(1, partial)));
    }

    /** Exactly the existing positions at integer ticks, with the same motion evaluated between ticks. */
    public static List<FlowSegment> segments(Point center, double frame) {
        if (!RailThunderArrayGeometry.validPoint(center) || !Double.isFinite(frame)) return List.of();
        List<FlowSegment> lines = new ArrayList<>(PATHS * SEGMENTS_PER_PATH);
        double phase = wrap(frame) * Math.PI / 120;
        for (int band = 0; band < 2; band++) {
            double radius = band == 0 ? 3.2 : 3.8, height = band == 0 ? -.1 : 1.3;
            for (int arc = 0; arc < 3; arc++) {
                double start = arc * Math.PI * 2 / 3 + phase * (band == 0 ? 1 : -1) + band * .47;
                for (int step = 0; step < SEGMENTS_PER_PATH; step++) {
                    double a = start + step * Math.PI / 15, b = a + Math.PI / 15;
                    Point from = center.plus(new Point(Math.sin(a) * radius, height + .08 * Math.sin(a * 3 + phase), Math.cos(a) * radius));
                    Point to = center.plus(new Point(Math.sin(b) * radius, height + .08 * Math.sin(b * 3 + phase), Math.cos(b) * radius));
                    lines.add(new FlowSegment(new Segment(from, to), band * 3 + arc,
                            step / (double) SEGMENTS_PER_PATH, (step + 1) / (double) SEGMENTS_PER_PATH));
                }
            }
        }
        return List.copyOf(lines);
    }

    /**
     * The five existing path vertices carry one tapered envelope and a broad, six-second lobe.
     * Interpolation is piecewise linear, matching the existing four quads rather than adding geometry.
     * A clipped point therefore retains precisely the envelope of its original, uncut segment.
     */
    public static Profile profile(int path, double pathT, double frame, Layer layer) {
        if (path < 0 || path >= PATHS || !Double.isFinite(pathT) || pathT < 0 || pathT > 1
                || !Double.isFinite(frame) || layer == null) return null;
        double position = pathT * SEGMENTS_PER_PATH;
        int node = Math.min(SEGMENTS_PER_PATH - 1, (int) Math.floor(position));
        double blend = position - node, a = node / (double) SEGMENTS_PER_PATH, b = (node + 1) / (double) SEGMENTS_PER_PATH;
        double taperA = taper(a), taperB = taper(b);
        double halfWidth = halfWidth(layer) * lerp(taperA, taperB, blend);
        double phase = wrap(frame) / HIGHLIGHT_TICKS;
        int peak = layer == Layer.CORE ? MagneticDomainGeometry.CORE_ALPHA : MagneticDomainGeometry.FLOW_ALPHA;
        int alphaA = (int) Math.round(peak * endpointFade(a) * highlight(path, a, phase));
        int alphaB = (int) Math.round(peak * endpointFade(b) * highlight(path, b, phase));
        int alpha = (int) Math.round(lerp(alphaA, alphaB, blend));
        return new Profile(halfWidth, layer == Layer.CORE ? MagneticDomainGeometry.WHITE : MagneticDomainGeometry.FLOW_BLUE,
                alpha, layer == Layer.CORE ? 14f / 32 : 8f / 32, layer == Layer.CORE ? 18f / 32 : 24f / 32);
    }

    /**
     * At most two quads after near-plane and aim exclusion. The caller applies world visibility first
     * and takes its shared controller budget immediately before emitting each returned quad.
     */
    public static List<FlowRibbon> ribbons(FlowSegment flow, Point camera, Point look, Point up, double frame, Layer layer) {
        if (!valid(flow) || !RailThunderArrayGeometry.validPoint(camera) || !Double.isFinite(frame) || layer == null
                || RailThunderArrayGeometry.basis(look) == null || up == null || !up.finite()) return List.of();
        Point a = flow.segment.start().minus(camera), b = flow.segment.end().minus(camera);
        double maxWidth = halfWidth(layer);
        Segment near = RailThunderArrayGeometry.nearClip(a, b, look, maxWidth);
        List<FlowRibbon> result = new ArrayList<>(2);
        Point forward = RailThunderArrayGeometry.basis(look).forward();
        for (Segment part : RailThunderArrayGeometry.clearCenter(near, look, up, maxWidth)) {
            var ribbon = RailThunderArrayGeometry.ribbon(part, look, maxWidth);
            if (ribbon == null || !ribbon.side().finite() || ribbon.halfWidth() <= 0) continue;
            double startT = pathCoordinate(flow, a, b, part.start()), endT = pathCoordinate(flow, a, b, part.end());
            Profile start = profile(flow.path, startT, frame, layer), end = profile(flow.path, endT, frame, layer);
            if (start == null || end == null || start.alpha == 0 && end.alpha == 0) continue;
            Point unitSide = ribbon.side().scale(1 / ribbon.halfWidth());
            // Keep the existing near-eye angular cap at each endpoint; neither clipping nor taper widens it.
            double startWidth = Math.min(start.halfWidth, part.start().dot(forward) * .012);
            double endWidth = Math.min(end.halfWidth, part.end().dot(forward) * .012);
            Point startSide = unitSide.scale(startWidth), endSide = unitSide.scale(endWidth);
            if (!startSide.finite() || !endSide.finite()) continue;
            result.add(new FlowRibbon(part.start(), part.end(), startSide, endSide, startT, endT, start, end));
        }
        return List.copyOf(result);
    }

    private static boolean valid(FlowSegment flow) {
        return flow != null && flow.segment != null && RailThunderArrayGeometry.validEndpoints(flow.segment.start(), flow.segment.end())
                && flow.segment.start().minus(flow.segment.end()).length() >= EPS
                && flow.path >= 0 && flow.path < PATHS && Double.isFinite(flow.startT) && Double.isFinite(flow.endT)
                && flow.startT >= 0 && flow.endT <= 1 && flow.startT < flow.endT
                && flow.endT - flow.startT <= 1.0 / SEGMENTS_PER_PATH + EPS;
    }

    private static double pathCoordinate(FlowSegment flow, Point originalA, Point originalB, Point clipped) {
        Point delta = originalB.minus(originalA);
        double fraction = clipped.minus(originalA).dot(delta) / delta.dot(delta);
        return lerp(flow.startT, flow.endT, Math.max(0, Math.min(1, fraction)));
    }

    private static double halfWidth(Layer layer) { return layer == Layer.CORE ? CORE_HALF_WIDTH : SHOULDER_HALF_WIDTH; }
    // Width supplies the interior taper; alpha fades only the true ends, avoiding double attenuation.
    private static double endpointFade(double t) { return t <= 0 || t >= 1 ? 0 : 1; }
    private static double taper(double t) { return t <= 0 || t >= 1 ? 0 : Math.sin(Math.PI * t); }
    private static double highlight(int path, double t, double phase) {
        return .68 + .32 * (.5 + .5 * Math.cos(2 * Math.PI * (t - phase + path / (double) PATHS)));
    }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double wrap(double frame) { double value = frame % CLOCK_TICKS; return value < 0 ? value + CLOCK_TICKS : value; }
}
