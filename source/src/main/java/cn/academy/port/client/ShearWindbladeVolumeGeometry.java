package cn.academy.port.client;

import cn.academy.port.highpower.core.ShearWindbladeGeometry;
import cn.academy.port.highpower.core.ShearWindbladeGeometry.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional NEW421 pressure volume around an already accepted eight-chord receipt.
 * Every width here is decorative; the authoritative .035m hit apron is untouched.
 * No time, predicted front, particle, collision, terrain or impact input exists here.
 */
public final class ShearWindbladeVolumeGeometry {
    public static final int SIDES = 6, QUADS_PER_CHORD = 8, MAX_CONTROLLERS = 4;
    public static final int MAX_FRONTS = 2, MAX_PARTICLES = 0, AFTERIMAGES = 0;
    public static final int MAX_CONTROLLER_VERTICES = MAX_FRONTS * ShearWindbladeGeometry.CHORDS * QUADS_PER_CHORD * 4;
    public static final int MAX_SCENE_VERTICES = MAX_CONTROLLERS * MAX_CONTROLLER_VERTICES;
    public static final double MAX_THICKNESS = .22, SHOULDER_WIDTH = .38, CORE_WIDTH = .070;
    private static final double EPSILON_SQUARED = 1e-20, RANGE_SQUARED = 24 * 24;
    private static final double MIN_V = 11.0 / 32, MAX_V = 21.0 / 32;
    private static final int[] SIDE_GRAY = {244, 212, 163, 146, 186, 228};
    private static final double[] SIDE_OPACITY = {.38, .32, .23, .20, .27, .35};
    private static final Mesh EMPTY = new Mesh(List.of(), 0);

    public enum Material { SIDE, SOFT, CORE }
    /** Position has already had the camera subtracted, while still a double. */
    public record Vertex(Point position, double u, double v) {}
    public record Quad(Material material, int blade, int chord, int gray, int alpha, List<Vertex> vertices) {
        public Quad { vertices = List.copyOf(vertices); }
    }
    public record Mesh(List<Quad> quads, int frontCount) {
        public Mesh { quads = List.copyOf(quads); }
        public int vertexCount() { return quads.size() * 4; }
    }
    private record Section(Point[] ring, Point softLow, Point softHigh, Point coreLow, Point coreHigh, double u) {}
    private ShearWindbladeVolumeGeometry() {}

    /**
     * Eight quads per certified chord: six shared-section sides, one soft shoulder,
     * and the retained .070m camera-facing core. There are no extra tip caps.
     * An invalid/incomplete input rejects the entire enhanced mesh; the caller may
     * use the certified classic renderer. The caller also owns whole-mesh near-eye
     * fallback, depth testing, and final double-to-float conversion.
     */
    public static Mesh mesh(ShearWindbladeRenderGeometry.Shape shape, Point origin, Point camera, Point viewRight) {
        if (shape == null || !valid(origin) || !valid(camera) || !valid(viewRight)
                || lengthSquared(viewRight) < EPSILON_SQUARED || shape.strokes().isEmpty()
                || shape.strokes().size() > MAX_FRONTS * ShearWindbladeGeometry.CHORDS) return EMPTY;
        var strokes = new ShearWindbladeRenderGeometry.Stroke[MAX_FRONTS][ShearWindbladeGeometry.CHORDS];
        for (var stroke : shape.strokes()) {
            if (stroke == null || stroke.blade() < 0 || stroke.blade() >= MAX_FRONTS
                    || stroke.chord() < 0 || stroke.chord() >= ShearWindbladeGeometry.CHORDS
                    || stroke.alpha() <= 0 || stroke.alpha() > 255 || !valid(stroke.from()) || !valid(stroke.to())
                    || stroke.from().distanceSquared(origin) > RANGE_SQUARED
                    || stroke.to().distanceSquared(origin) > RANGE_SQUARED
                    || strokes[stroke.blade()][stroke.chord()] != null) return EMPTY;
            strokes[stroke.blade()][stroke.chord()] = stroke;
        }
        var quads = new ArrayList<Quad>(MAX_CONTROLLER_VERTICES / 4);
        int fronts = 0;
        for (int blade = 0; blade < MAX_FRONTS; blade++) {
            var lines = strokes[blade];
            int count = 0;
            for (var line : lines) if (line != null) count++;
            if (count == 0) continue;
            if (count != ShearWindbladeGeometry.CHORDS) return EMPTY;
            for (int i = 1; i < lines.length; i++)
                if (!lines[i - 1].to().equals(lines[i].from())) return EMPTY;
            Section[] sections = sections(lines, origin, camera, viewRight);
            if (sections == null) return EMPTY;
            fronts++;
            for (int chord = 0; chord < lines.length; chord++) {
                Section a = sections[chord], b = sections[chord + 1];
                int alpha = lines[chord].alpha();
                for (int side = 0; side < SIDES; side++) {
                    int next = (side + 1) % SIDES;
                    quads.add(quad(Material.SIDE, blade, chord, SIDE_GRAY[side], opacity(alpha, SIDE_OPACITY[side]),
                            a.ring[side], a.ring[next], b.ring[next], b.ring[side], a.u, b.u, MIN_V, MAX_V));
                }
                quads.add(quad(Material.SOFT, blade, chord, 186, opacity(alpha, .24),
                        a.softLow, a.softHigh, b.softHigh, b.softLow, a.u, b.u, 0, 1));
                quads.add(quad(Material.CORE, blade, chord, 245, alpha,
                        a.coreLow, a.coreHigh, b.coreHigh, b.coreLow, a.u, b.u, MIN_V, MAX_V));
            }
        }
        return new Mesh(quads, fronts);
    }

    private static Section[] sections(ShearWindbladeRenderGeometry.Stroke[] lines, Point origin, Point camera, Point viewRight) {
        int last = ShearWindbladeGeometry.CHORDS;
        Point[] world = new Point[last + 1], local = new Point[last + 1], relative = new Point[last + 1];
        double[] arc = new double[last + 1];
        for (int i = 0; i <= last; i++) {
            world[i] = i == last ? lines[last - 1].to() : lines[i].from();
            // Reduce coordinates before taking midpoints, tangents, lengths or offsets.
            local[i] = subtract(world[i], origin);
            relative[i] = subtract(world[i], camera);
            if (i > 0) arc[i] = arc[i - 1] + Math.sqrt(local[i].distanceSquared(local[i - 1]));
        }
        if (!Double.isFinite(arc[last]) || arc[last] < 1e-10) return null;
        Point lateral = normalized(subtract(local[last], local[0]));
        if (lateral == null) lateral = firstDirection(local);
        if (lateral == null) return null;
        Point middle = scale(add(local[0], local[last]), .5);
        Point bow = subtract(local[last / 2], middle);
        Point forward = normalized(subtract(bow, scale(lateral, dot(bow, lateral))));
        if (forward == null) forward = perpendicular(lateral);
        Point normal = normalized(cross(lateral, forward));
        if (normal == null) return null;
        double travel = Math.max(0, dot(middle, forward));
        var result = new Section[last + 1];
        Point previousFacing = null;
        for (int i = 0; i <= last; i++) {
            Point tangent = tangent(local, i, lateral);
            Point trail = normalized(cross(tangent, normal));
            if (trail == null) return null;
            if (dot(trail, forward) > 0) trail = scale(trail, -1);
            // These two axes belong to the actual blade plane, never the camera.
            double taper = i == 0 || i == last ? 0 : Math.sin(Math.PI * arc[i] / arc[last]);
            double depth = Math.min(1, travel / MAX_THICKNESS);
            Point[] offsets = new Point[SIDES];
            double ringScale = 1;
            for (int side = 0; side < SIDES; side++) {
                double angle = side * Math.PI * 2 / SIDES;
                offsets[side] = add(scale(trail, MAX_THICKNESS * .5 * taper * depth * (1 - Math.cos(angle))),
                        scale(normal, MAX_THICKNESS * .5 * taper * Math.sin(angle)));
                ringScale = Math.min(ringScale, boundedScale(local[i], world[i], offsets[side]));
            }
            Point[] ring = new Point[SIDES];
            for (int side = 0; side < SIDES; side++) ring[side] = add(relative[i], scale(offsets[side], ringScale));

            Point facing = normalized(cross(tangent, scale(relative[i], -1)));
            if (facing == null) facing = normalized(subtract(viewRight, scale(tangent, dot(viewRight, tangent))));
            if (facing == null) facing = perpendicular(tangent);
            if (previousFacing != null && dot(facing, previousFacing) < 0) facing = scale(facing, -1);
            previousFacing = facing;
            Point coreOffset = scale(facing, CORE_WIDTH * .5);
            // The complete classic core width is retained; never silently narrow it.
            if (boundedScale(local[i], world[i], coreOffset) < 1
                    || boundedScale(local[i], world[i], scale(coreOffset, -1)) < 1) return null;
            Point coreLow = subtract(relative[i], coreOffset), coreHigh = add(relative[i], coreOffset);

            // A camera-facing shoulder can lean forward. Move that decorative sheet
            // back just enough to keep its leading edge at the certified contour,
            // then shorten it when a blocked/short front has little actual travel.
            double halfSoft = SHOULDER_WIDTH * .5 * taper;
            double forwardExtent = Math.abs(dot(facing, forward));
            if (forwardExtent > 1e-12) halfSoft = Math.min(halfSoft, travel / (2 * forwardExtent));
            Point softCenterOffset = scale(forward, -halfSoft * forwardExtent);
            Point softLowOffset = subtract(softCenterOffset, scale(facing, halfSoft));
            Point softHighOffset = add(softCenterOffset, scale(facing, halfSoft));
            double softScale = Math.min(boundedScale(local[i], world[i], softLowOffset),
                    boundedScale(local[i], world[i], softHighOffset));
            result[i] = new Section(ring, add(relative[i], scale(softLowOffset, softScale)),
                    add(relative[i], scale(softHighOffset, softScale)), coreLow, coreHigh, arc[i] / arc[last]);
        }
        return result;
    }

    /** Keep an offset inside both the 24m sphere and representable world borders. */
    private static double boundedScale(Point local, Point world, Point offset) {
        double result = 1, squared = lengthSquared(offset), along = dot(local, offset);
        double room = RANGE_SQUARED - lengthSquared(local);
        if (room < 0) return 0;
        if (squared > 0 && squared + 2 * along > room) {
            double root = Math.sqrt(along * along + squared * room);
            // The rationalized positive root avoids cancellation at the range edge.
            result = along >= 0 ? root + along == 0 ? 0 : room / (root + along) : (root - along) / squared;
            result = Math.max(0, Math.min(1, Math.nextDown(result)));
        }
        result = Math.min(result, borderScale(world.x(), offset.x()));
        result = Math.min(result, borderScale(world.y(), offset.y()));
        result = Math.min(result, borderScale(world.z(), offset.z()));
        return Math.max(0, result);
    }
    private static double borderScale(double value, double offset) {
        if (offset == 0) return 1;
        return Math.min(1, (ShearWindbladeGeometry.WORLD_LIMIT - (offset > 0 ? value : -value)) / Math.abs(offset));
    }
    private static Point tangent(Point[] points, int at, Point fallback) {
        Point before = null, after = null;
        for (int i = at - 1; i >= 0 && before == null; i--) before = normalized(subtract(points[at], points[i]));
        for (int i = at + 1; i < points.length && after == null; i++) after = normalized(subtract(points[i], points[at]));
        Point sum = before == null ? after : after == null ? before : normalized(add(before, after));
        return sum == null ? before == null ? fallback : before : sum;
    }
    private static Point firstDirection(Point[] points) {
        for (int i = 1; i < points.length; i++) {
            Point direction = normalized(subtract(points[i], points[0]));
            if (direction != null) return direction;
        }
        return null;
    }
    private static Point perpendicular(Point tangent) {
        Point axis = Math.abs(tangent.x()) <= Math.abs(tangent.y()) && Math.abs(tangent.x()) <= Math.abs(tangent.z())
                ? new Point(1, 0, 0) : Math.abs(tangent.y()) <= Math.abs(tangent.z()) ? new Point(0, 1, 0) : new Point(0, 0, 1);
        return normalized(cross(tangent, axis));
    }
    private static Quad quad(Material material, int blade, int chord, int gray, int alpha,
            Point a, Point b, Point c, Point d, double fromU, double toU, double minV, double maxV) {
        return new Quad(material, blade, chord, gray, alpha, List.of(new Vertex(a, fromU, maxV),
                new Vertex(b, fromU, minV), new Vertex(c, toU, minV), new Vertex(d, toU, maxV)));
    }
    private static int opacity(int alpha, double factor) { return (int)Math.round(alpha * factor); }
    private static boolean valid(Point point) { return point != null && point.finite(); }
    private static Point normalized(Point point) {
        double squared = lengthSquared(point);
        return !Double.isFinite(squared) || squared < EPSILON_SQUARED ? null : scale(point, 1 / Math.sqrt(squared));
    }
    private static Point add(Point a, Point b) { return new Point(a.x() + b.x(), a.y() + b.y(), a.z() + b.z()); }
    private static Point subtract(Point a, Point b) { return new Point(a.x() - b.x(), a.y() - b.y(), a.z() - b.z()); }
    private static Point scale(Point p, double value) { return new Point(p.x() * value, p.y() * value, p.z() * value); }
    private static double dot(Point a, Point b) { return a.x() * b.x() + a.y() * b.y() + a.z() * b.z(); }
    private static double lengthSquared(Point p) { return dot(p, p); }
    private static Point cross(Point a, Point b) {
        return new Point(a.y() * b.z() - a.z() * b.y(), a.z() * b.x() - a.x() * b.z(), a.x() * b.y() - a.y() * b.x());
    }
}
