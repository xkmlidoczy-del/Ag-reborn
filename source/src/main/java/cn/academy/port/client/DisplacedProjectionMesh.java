package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;

/** Small coordinate marks around the actual needle endpoints, without a substitute projectile. */
public final class DisplacedProjectionMesh {
    public static final int SUCCESS_TICKS = 7, MAX_PARTICLES = 32;
    public static final double CULL_DISTANCE = 64, MARK_RADIUS = .19;
    private DisplacedProjectionMesh() {}
    public static int frame(int age) { return Math.max(0, Math.min(SUCCESS_TICKS - 1, age)); }
    public static int alpha(int age) { return age < 0 || age >= SUCCESS_TICKS ? 0 : 150 * (SUCCESS_TICKS - age) / SUCCESS_TICKS; }
    public static Box bounds(Point point) {
        return point == null || !point.finite() ? null : new Box(point.x() - MARK_RADIUS, point.y() - MARK_RADIUS,
                point.z() - MARK_RADIUS, point.x() + MARK_RADIUS, point.y() + MARK_RADIUS, point.z() + MARK_RADIUS);
    }
    public static List<SafeLandingProbeMesh.Segment> corners(Point point) { return SafeLandingProbeMesh.corners(bounds(point)); }
    /** Thirty-two small gray original-texture fragments in total, anchored to the confirmed endpoints. */
    public static List<SafeLandingProbeMesh.Particle> particles(Point source, Point arrival, int age) {
        if (source == null || arrival == null || !source.finite() || !arrival.finite() || alpha(age) == 0) return List.of();
        var result = new ArrayList<SafeLandingProbeMesh.Particle>(MAX_PARTICLES);
        for (Point endpoint : List.of(source, arrival)) for (int i = 0; i < MAX_PARTICLES / 2; i++) {
            double angle = i * Math.PI / 8, radius = .105 + .006 * age;
            result.add(new SafeLandingProbeMesh.Particle(new Point(endpoint.x() + Math.cos(angle) * radius,
                    endpoint.y() + ((i % 4) - 1.5) * .052 + age * .005, endpoint.z() + Math.sin(angle) * radius), .018));
        }
        return List.copyOf(result);
    }
}
