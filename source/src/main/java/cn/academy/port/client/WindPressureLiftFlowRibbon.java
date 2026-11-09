package cn.academy.port.client;

import cn.academy.port.client.WindPressureLiftFlowMesh.Segment;
import cn.academy.port.client.WindPressureLiftFlowMesh.Shape;
import cn.academy.port.client.WindPressureLiftMesh.Point;
import java.util.ArrayList;
import java.util.List;

/** Shared-sample ribbon shoulders and clipping, kept independent of the game and rendering APIs. */
public final class WindPressureLiftFlowRibbon {
    /** The centers are already camera-relative doubles; adjacent packet chords reuse their sample sides. */
    public record Draw(Segment segment, Point sideFrom, Point sideTo) {}
    /** Segment carries the clipped centers, scaled widths, U coordinates and endpoint alpha. */
    public record Quad(Segment segment, Point aMinus, Point aPlus, Point bPlus, Point bMinus) {}
    private static final int PACKET_SEGMENTS = WindPressureLiftFlowMesh.RIBBON_SEGMENTS;
    private static final int FLOW_SEGMENTS = WindPressureLiftFlowMesh.RIBBONS * PACKET_SEGMENTS;
    private static final Point X = new Point(1, 0, 0), Y = new Point(0, 1, 0), Z = new Point(0, 0, 1);

    private WindPressureLiftFlowRibbon() {}

    public static List<Draw> build(Shape shape, Point camera, Point fallbackUnit) {
        if (shape == null || !finite(camera) || !unit(fallbackUnit)
                || shape.segments().size() != FLOW_SEGMENTS + WindPressureLiftFlowMesh.GROUND_SEGMENTS) return List.of();
        for (Segment segment : shape.segments()) if (!valid(segment)) return List.of();
        var draws = new ArrayList<Draw>(shape.segments().size());
        for (int packet = 0; packet < WindPressureLiftFlowMesh.RIBBONS; packet++) {
            int start = packet * PACKET_SEGMENTS;
            Point[] samples = new Point[PACKET_SEGMENTS + 1], sides = new Point[samples.length];
            samples[0] = subtract(shape.segments().get(start).a(), camera);
            for (int step = 0; step < PACKET_SEGMENTS; step++) {
                Segment source = shape.segments().get(start + step);
                if (step > 0 && !joined(shape.segments().get(start + step - 1), source)) return List.of();
                samples[step + 1] = subtract(source.b(), camera);
            }
            for (int sample = 0; sample < samples.length; sample++) {
                if (!finite(samples[sample])) return List.of();
                Point tangent = normalize(subtract(samples[Math.min(sample + 1, PACKET_SEGMENTS)], samples[Math.max(0, sample - 1)]));
                if (tangent == null) return List.of();
                sides[sample] = side(tangent, samples[sample], fallbackUnit, sample == 0 ? null : sides[sample - 1]);
            }
            for (int step = 0; step < PACKET_SEGMENTS; step++) {
                Segment source = shape.segments().get(start + step);
                draws.add(new Draw(withPoints(source, samples[step], samples[step + 1]), sides[step], sides[step + 1]));
            }
        }
        // Floor arcs are independent open segments, never joined around the circumference.
        for (int i = FLOW_SEGMENTS; i < shape.segments().size(); i++) {
            Segment source = shape.segments().get(i);
            Point a = subtract(source.a(), camera), b = subtract(source.b(), camera);
            Point tangent = normalize(subtract(b, a));
            if (!finite(a) || !finite(b) || tangent == null) return List.of();
            Point sideA = side(tangent, a, fallbackUnit, null), sideB = side(tangent, b, fallbackUnit, sideA);
            draws.add(new Draw(withPoints(source, a, b), sideA, sideB));
        }
        return List.copyOf(draws);
    }

    /** Clip the actual side offsets with the centers, so a cut does not widen or rotate the shoulder. */
    public static Quad clip(Draw draw, Point forwardUnit, double scale) {
        if (draw == null || !valid(draw.segment) || !unit(draw.sideFrom) || !unit(draw.sideTo)
                || !unit(forwardUnit) || !Double.isFinite(scale) || scale <= 0 || scale > 1) return null;
        Segment source = draw.segment;
        double widthA = source.widthFrom() * scale, widthB = source.widthTo() * scale;
        Point a = source.a(), b = source.b(), sideA = multiply(draw.sideFrom, widthA * .5), sideB = multiply(draw.sideTo, widthB * .5);
        double alphaA = source.alphaFrom(), alphaB = source.alphaTo(), uA = source.uFrom(), uB = source.uTo();
        double plane = WindPressureLiftMesh.NEAR_PLANE + Math.max(widthA, widthB) * .5;
        double depthA = dot(a, forwardUnit), depthB = dot(b, forwardUnit);
        if (!Double.isFinite(depthA) || !Double.isFinite(depthB) || depthA < plane && depthB < plane) return null;
        if (depthA < plane || depthB < plane) {
            double t = (plane - depthA) / (depthB - depthA);
            Point center = lerp(a, b, t), offset = lerp(sideA, sideB, t);
            double width = lerp(widthA, widthB, t), alpha = lerp(alphaA, alphaB, t), u = lerp(uA, uB, t);
            if (depthA < plane) { a = center; sideA = offset; widthA = width; alphaA = alpha; uA = u; }
            else { b = center; sideB = offset; widthB = width; alphaB = alpha; uB = u; }
        }
        Point aMinus = subtract(a, sideA), aPlus = add(a, sideA), bPlus = add(b, sideB), bMinus = subtract(b, sideB);
        if (!finite(aMinus) || !finite(aPlus) || !finite(bPlus) || !finite(bMinus)) return null;
        Segment clipped = new Segment(a, b, widthA, widthB, (int) Math.round(alphaA), (int) Math.round(alphaB), uA, uB);
        return new Quad(clipped, aMinus, aPlus, bPlus, bMinus);
    }

    private static Point side(Point tangent, Point sample, Point fallback, Point previous) {
        Point eye = normalize(multiply(sample, -1));
        Point candidate = eye == null ? null : cross(tangent, eye);
        if (candidate == null || dot(candidate, candidate) <= 1e-12) {
            candidate = subtract(fallback, multiply(tangent, dot(fallback, tangent)));
            if (dot(candidate, candidate) <= 1e-12) {
                Point axis = Math.abs(tangent.x()) <= Math.abs(tangent.y()) && Math.abs(tangent.x()) <= Math.abs(tangent.z())
                        ? X : Math.abs(tangent.y()) <= Math.abs(tangent.z()) ? Y : Z;
                candidate = cross(tangent, axis);
            }
        }
        candidate = normalize(candidate);
        return previous != null && dot(candidate, previous) < 0 ? multiply(candidate, -1) : candidate;
    }

    private static boolean joined(Segment a, Segment b) {
        return a.b().equals(b.a()) && a.widthTo() == b.widthFrom() && a.alphaTo() == b.alphaFrom() && a.uTo() == b.uFrom();
    }
    private static Segment withPoints(Segment s, Point a, Point b) {
        return new Segment(a, b, s.widthFrom(), s.widthTo(), s.alphaFrom(), s.alphaTo(), s.uFrom(), s.uTo());
    }
    private static boolean valid(Segment s) {
        return s != null && finite(s.a()) && finite(s.b()) && Double.isFinite(s.widthFrom()) && Double.isFinite(s.widthTo())
                && s.widthFrom() >= 0 && s.widthFrom() <= WindPressureLiftFlowMesh.MAX_WIDTH
                && s.widthTo() >= 0 && s.widthTo() <= WindPressureLiftFlowMesh.MAX_WIDTH
                && s.alphaFrom() >= 0 && s.alphaFrom() <= 255 && s.alphaTo() >= 0 && s.alphaTo() <= 255
                && Double.isFinite(s.uFrom()) && s.uFrom() >= 0 && s.uFrom() <= 1
                && Double.isFinite(s.uTo()) && s.uTo() >= 0 && s.uTo() <= 1;
    }
    private static boolean finite(Point p) { return p != null && p.finite(); }
    private static boolean unit(Point p) { return finite(p) && Math.abs(dot(p, p) - 1) <= 1e-6; }
    private static Point normalize(Point p) {
        if (!finite(p)) return null;
        double largest = Math.max(Math.abs(p.x()), Math.max(Math.abs(p.y()), Math.abs(p.z())));
        if (largest == 0) return null;
        Point scaled = new Point(p.x() / largest, p.y() / largest, p.z() / largest);
        return multiply(scaled, 1 / Math.sqrt(dot(scaled, scaled)));
    }
    private static Point add(Point a, Point b) { return new Point(a.x() + b.x(), a.y() + b.y(), a.z() + b.z()); }
    private static Point subtract(Point a, Point b) { return new Point(a.x() - b.x(), a.y() - b.y(), a.z() - b.z()); }
    private static Point multiply(Point p, double scale) { return new Point(p.x() * scale, p.y() * scale, p.z() * scale); }
    private static double dot(Point a, Point b) { return a.x() * b.x() + a.y() * b.y() + a.z() * b.z(); }
    private static Point cross(Point a, Point b) {
        return new Point(a.y() * b.z() - a.z() * b.y(), a.z() * b.x() - a.x() * b.z(), a.x() * b.y() - a.y() * b.x());
    }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static Point lerp(Point a, Point b, double t) {
        return new Point(lerp(a.x(), b.x(), t), lerp(a.y(), b.y(), t), lerp(a.z(), b.z(), t));
    }
}
