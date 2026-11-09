package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;

/** NEW313 collision-box corners and bounded cosmetic marks; no trajectory or client movement. */
public final class SafeLandingProbeMesh {
    public static final int CORNER_LINES = 24, SUCCESS_TICKS = 7, MAX_PARTICLES = 32;
    public static final double CULL_DISTANCE = 64;
    public record Segment(Point from, Point to) {}
    public record Particle(Point position, double size) {}
    public record Vertex(Point position, float u, float v) {}
    public record Quad(Vertex a, Vertex b, Vertex c, Vertex d) {}
    private SafeLandingProbeMesh() {}
    public static List<Segment> corners(Box b) {
        if (b == null || !b.body()) return List.of();
        double length = Math.min(.18, Math.min(b.maxX() - b.minX(), Math.min(b.maxY() - b.minY(), b.maxZ() - b.minZ())) * .3);
        var lines = new ArrayList<Segment>(CORNER_LINES);
        for (int mask = 0; mask < 8; mask++) {
            double x = (mask & 1) == 0 ? b.minX() : b.maxX();
            double y = (mask & 2) == 0 ? b.minY() : b.maxY();
            double z = (mask & 4) == 0 ? b.minZ() : b.maxZ();
            var at = new Point(x, y, z);
            lines.add(new Segment(at, new Point(x + ((mask & 1) == 0 ? length : -length), y, z)));
            lines.add(new Segment(at, new Point(x, y + ((mask & 2) == 0 ? length : -length), z)));
            lines.add(new Segment(at, new Point(x, y, z + ((mask & 4) == 0 ? length : -length))));
        }
        return List.copyOf(lines);
    }
    public static int frame(int age) { return Math.max(0, Math.min(SUCCESS_TICKS - 1, age)); }
    public static int alpha(int age) { return age < 0 || age >= SUCCESS_TICKS ? 0 : 160 * (SUCCESS_TICKS - age) / SUCCESS_TICKS; }
    public static Box sourceBounds(Box destinationBounds, Point source, Point destination) {
        if (destinationBounds == null || !destinationBounds.body() || source == null || destination == null
                || !source.finite() || !destination.finite()) return null;
        double x = source.x() - destination.x(), y = source.y() - destination.y(), z = source.z() - destination.z();
        var box = new Box(destinationBounds.minX() + x, destinationBounds.minY() + y, destinationBounds.minZ() + z,
                destinationBounds.maxX() + x, destinationBounds.maxY() + y, destinationBounds.maxZ() + z);
        return box.body() ? box : null;
    }
    /** The original tp_mark is a 64x32 holographic biped atlas, never a captured player skin. */
    public static List<Quad> biped(Box bounds) {
        if (bounds == null || !bounds.body()) return List.of();
        var result = new ArrayList<Quad>(36);
        // Six body pieces fit the actual receipt bounds. Inflated classic headwear is omitted.
        for (int i = 0; i < 6; i++) for (var q : ClassicTeleporterTimeline.mesh(ClassicTeleporterTimeline.BIPED.get(i)))
            result.add(new Quad(vertex(q.a(), bounds), vertex(q.b(), bounds), vertex(q.c(), bounds), vertex(q.d(), bounds)));
        return List.copyOf(result);
    }
    private static Vertex vertex(ClassicTeleporterTimeline.Vertex v, Box b) {
        double x = (b.minX() + b.maxX()) / 2 + v.x() / 16 * (b.maxX() - b.minX());
        double y = b.minY() + (24 - v.y()) / 32 * (b.maxY() - b.minY());
        double z = (b.minZ() + b.maxZ()) / 2 + v.z() / 8 * (b.maxZ() - b.minZ());
        return new Vertex(new Point(x, y, z), v.u(), v.v());
    }
    /** Sixteen deterministic sparks at each real endpoint, at most thirty-two in total. */
    public static List<Particle> particles(Point source, Point destination, int age) {
        if (source == null || destination == null || !source.finite() || !destination.finite()
                || alpha(age) == 0) return List.of();
        var result = new ArrayList<Particle>(MAX_PARTICLES);
        for (var point : List.of(source, destination)) for (int i = 0; i < MAX_PARTICLES / 2; i++) {
            double angle = i * Math.PI * 2 / 16, radius = .12 + age * .012;
            var at = new Point(point.x() + Math.cos(angle) * radius, point.y() + .2 + (i % 4) * .25 + age * .018,
                    point.z() + Math.sin(angle) * radius);
            if (at.finite()) result.add(new Particle(at, .035));
        }
        return List.copyOf(result);
    }
}
