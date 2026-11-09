package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;

/** Six standing plans, at most sixty strokes; only server-confirmed moves use the classic seven frames. */
public final class TacticalCoordinateMesh {
    public static final int MAX_OUTLINES = 6, STROKES_PER_OUTLINE = 10, SUCCESS_TICKS = 7;
    public static final double CULL_DISTANCE = 64;
    public record Line(Point from, Point to) {}
    private TacticalCoordinateMesh() {}
    public static List<Line> outline(Point feet, float yaw) {
        if (feet == null || !feet.finite() || !Float.isFinite(yaw)) return List.of();
        // Head, spine, shoulders, arms and two separate legs form one stationary human outline.
        double[][] vertices = {{0,1.83},{-.16,1.67},{0,1.51},{.16,1.67},{0,1.35},{0,.77},
                {-.27,1.35},{.27,1.35},{-.38,.93},{.38,.93},{-.20,.035},{.20,.035}};
        int[][] edges = {{0,1},{1,2},{2,3},{3,0},{2,5},{6,7},{6,8},{7,9},{5,10},{5,11}};
        double radians = Math.toRadians(yaw), rightX = Math.cos(radians), rightZ = Math.sin(radians);
        var points = new Point[vertices.length];
        for (int i = 0; i < vertices.length; i++) points[i] = new Point(feet.x() + vertices[i][0] * rightX, feet.y() + vertices[i][1], feet.z() + vertices[i][0] * rightZ);
        var result = new ArrayList<Line>(STROKES_PER_OUTLINE);
        for (int[] edge : edges) result.add(new Line(points[edge[0]], points[edge[1]]));
        return List.copyOf(result);
    }
    public static List<Line> endpoint(Point feet) {
        if (feet == null || !feet.finite()) return List.of();
        var a = new Point(feet.x()-.32,feet.y()+.025,feet.z()-.32);
        var b = new Point(feet.x()+.32,feet.y()+.025,feet.z()-.32);
        var c = new Point(feet.x()+.32,feet.y()+.025,feet.z()+.32);
        var d = new Point(feet.x()-.32,feet.y()+.025,feet.z()+.32);
        return List.of(new Line(a,b),new Line(b,c),new Line(c,d),new Line(d,a));
    }
    public static Point relative(Point point, Point camera) {
        return point == null || camera == null || !point.finite() || !camera.finite() ? null
                : new Point(point.x()-camera.x(),point.y()-camera.y(),point.z()-camera.z());
    }
    public static int frame(int age) { return Math.max(0,Math.min(SUCCESS_TICKS-1,age)); }
    public static int alpha(int age) { return age < 0 || age >= SUCCESS_TICKS ? 0 : 210*(SUCCESS_TICKS-age)/SUCCESS_TICKS; }
}
