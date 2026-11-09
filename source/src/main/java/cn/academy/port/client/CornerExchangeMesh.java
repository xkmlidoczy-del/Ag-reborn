package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;

/** Full standing coordinate boxes and three-stroke facing arrows; no portal surface or predicted arrival. */
public final class CornerExchangeMesh {
    public static final int BOX_LINES = 12, ARROW_LINES = 3, SUCCESS_TICKS = 7, MAX_PARTICLES = 32;
    public static final double CULL_DISTANCE = 64;
    private CornerExchangeMesh() {}
    public static Box markBounds(Point p, Box template, Point destination) {
        if (p == null || !p.finite()) return null;
        if (template != null && template.body() && destination != null && destination.finite())
            return SafeLandingProbeMesh.sourceBounds(template, p, destination);
        return new Box(p.x()-.3, p.y(), p.z()-.3, p.x()+.3, p.y()+1.8, p.z()+.3);
    }
    public static List<SafeLandingProbeMesh.Segment> boxLines(Box b) {
        if (b == null || !b.body()) return List.of();
        var lines = new ArrayList<SafeLandingProbeMesh.Segment>(BOX_LINES);
        for (int mask = 0; mask < 8; mask++) for (int axis = 0; axis < 3; axis++) if ((mask & (1 << axis)) == 0) {
            var a = corner(b, mask); var c = corner(b, mask | (1 << axis));
            lines.add(new SafeLandingProbeMesh.Segment(a, c));
        }
        return List.copyOf(lines);
    }
    public static List<SafeLandingProbeMesh.Segment> directionArrow(Box b, double yawDegrees) {
        if (b == null || !b.body() || !Double.isFinite(yawDegrees)) return List.of();
        double yaw = Math.toRadians(yawDegrees), dx = -Math.sin(yaw), dz = Math.cos(yaw);
        double x = (b.minX()+b.maxX())/2, y = b.minY()+.10, z = (b.minZ()+b.maxZ())/2;
        var from = new Point(x-dx*.18, y, z-dz*.18); var tip = new Point(x+dx*.62, y, z+dz*.62);
        return List.of(new SafeLandingProbeMesh.Segment(from, tip),
                new SafeLandingProbeMesh.Segment(tip, new Point(x+dx*.39+dz*.14,y,z+dz*.39-dx*.14)),
                new SafeLandingProbeMesh.Segment(tip, new Point(x+dx*.39-dz*.14,y,z+dz*.39+dx*.14)));
    }
    public static int frame(int age) { return Math.max(0, Math.min(SUCCESS_TICKS-1, age)); }
    public static int alpha(int age) { return age<0 || age>=SUCCESS_TICKS ? 0 : 190*(SUCCESS_TICKS-age)/SUCCESS_TICKS; }
    public static boolean insideCamera(Box b, Point camera) {
        return b != null && b.body() && camera != null && camera.finite()
                && camera.x()>=b.minX()-.12 && camera.x()<=b.maxX()+.12
                && camera.y()>=b.minY()-.12 && camera.y()<=b.maxY()+.12
                && camera.z()>=b.minZ()-.12 && camera.z()<=b.maxZ()+.12;
    }
    private static Point corner(Box b, int mask) {
        return new Point((mask&1)==0?b.minX():b.maxX(),(mask&2)==0?b.minY():b.maxY(),(mask&4)==0?b.minZ():b.maxZ());
    }
}
