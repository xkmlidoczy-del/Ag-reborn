package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.List;

/** NEW312 reuses original seven-frame biped atlas and exact endpoint outlines, never a portal. */
public final class PhaseSidestepMesh {
    public static final int SUCCESS_TICKS = 7, MAX_PARTICLES = 32;
    public static final double CULL_DISTANCE = 64;
    private PhaseSidestepMesh() {}
    public static int frame(int age) { return SafeLandingProbeMesh.frame(age); }
    public static int alpha(int age) { return SafeLandingProbeMesh.alpha(age); }
    public static Box sourceBounds(Box bounds, Point source, Point destination) { return SafeLandingProbeMesh.sourceBounds(bounds, source, destination); }
    public static List<SafeLandingProbeMesh.Segment> corners(Box bounds) { return SafeLandingProbeMesh.corners(bounds); }
    public static List<SafeLandingProbeMesh.Quad> biped(Box bounds) { return SafeLandingProbeMesh.biped(bounds); }
    public static List<SafeLandingProbeMesh.Particle> particles(Point source, Point destination, int age) {
        return SafeLandingProbeMesh.particles(source, destination, age);
    }
}
