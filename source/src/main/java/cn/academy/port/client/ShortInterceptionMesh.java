package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;

/** Small, separate rectangular sections. No strip or flight trail ever joins entry to exit. */
public final class ShortInterceptionMesh {
    public static final int SUCCESS_TICKS = 7, MAX_GROUPS = 4, LINES_PER_ENDPOINT = 7,
            MAX_ENDPOINT_LINES = MAX_GROUPS * 2 * LINES_PER_ENDPOINT;
    public static final double HALF_WIDTH = .28, HALF_HEIGHT = .38, CULL_DISTANCE = 64;
    public record Line(Point from, Point to) {}
    private ShortInterceptionMesh() {}

    /** The plane is normal to the actual incoming/outgoing velocity, including vertical shots. */
    public static List<Line> endpoint(Point center, Point velocity, boolean entry) {
        if (center == null || !center.finite()) return List.of();
        Point direction = unit(velocity);
        if (direction == null) return List.of();
        Point axis = Math.abs(direction.y()) > .94 ? new Point(1, 0, 0) : new Point(0, 1, 0);
        Point right = unit(cross(direction, axis)), up = unit(cross(right, direction));
        var corners = new Point[4];
        for (int i = 0; i < 4; i++) corners[i] = offset(offset(center, right, (i == 0 || i == 3 ? -1 : 1) * HALF_WIDTH),
                up, (i < 2 ? -1 : 1) * HALF_HEIGHT);
        var result = new ArrayList<Line>(LINES_PER_ENDPOINT);
        for (int i = 0; i < 4; i++) result.add(new Line(corners[i], corners[(i + 1) % 4]));
        // Three short strokes occupy only this endpoint's own side of the section.
        double start = entry ? -.48 : .12, end = entry ? -.12 : .48;
        Point from = offset(center, direction, start), tip = offset(center, direction, end);
        result.add(new Line(from, tip));
        result.add(new Line(tip, offset(offset(tip, direction, -.10), right, .065)));
        result.add(new Line(tip, offset(offset(tip, direction, -.10), right, -.065)));
        return List.copyOf(result);
    }
    public static List<Line> target(Point point) {
        if (point == null || !point.finite()) return List.of();
        var result = new ArrayList<Line>(6);
        for (Point axis : List.of(new Point(1,0,0),new Point(0,1,0),new Point(0,0,1))) {
            result.add(new Line(offset(point,axis,-.18),offset(point,axis,-.07)));
            result.add(new Line(offset(point,axis,.07),offset(point,axis,.18)));
        }
        return List.copyOf(result);
    }
    public static int frame(int age) { return Math.max(0, Math.min(SUCCESS_TICKS - 1, age)); }
    public static int alpha(int age) { return age < 0 || age >= SUCCESS_TICKS ? 0 : 210 * (SUCCESS_TICKS - age) / SUCCESS_TICKS; }
    public static Point relative(Point world, Point camera) {
        return world == null || camera == null || !world.finite() || !camera.finite() ? null
                : new Point(world.x()-camera.x(),world.y()-camera.y(),world.z()-camera.z());
    }
    private static Point unit(Point p) {
        if (p == null || !p.finite()) return null;
        double length = Math.sqrt(p.x()*p.x()+p.y()*p.y()+p.z()*p.z());
        return length < 1e-8 || !Double.isFinite(length) ? null : new Point(p.x()/length,p.y()/length,p.z()/length);
    }
    private static Point cross(Point a, Point b) { return new Point(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x()); }
    private static Point offset(Point p, Point direction, double distance) { return new Point(p.x()+direction.x()*distance,p.y()+direction.y()*distance,p.z()+direction.z()*distance); }
}
