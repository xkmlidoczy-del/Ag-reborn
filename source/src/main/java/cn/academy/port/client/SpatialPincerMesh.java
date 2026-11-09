package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;

/** Three ground points, eight private frozen targets and eight original seven-frame receipts; no particles. */
public final class SpatialPincerMesh {
    public static final int MAX_MARKERS = 3, MAX_TARGET_MARKERS = 8, MARK_STROKES = 8, TARGET_STROKES = 4,
            MAX_ROUTES = 1, MAX_CUES = 8, SUCCESS_TICKS = 7;
    public static final int MAX_DECOR_PRIMITIVES = MAX_MARKERS * MARK_STROKES + MAX_TARGET_MARKERS * TARGET_STROKES
            + MAX_ROUTES + MAX_CUES * 2 * 8;
    public static final double CULL_DISTANCE = 64;
    public record Line(Point from, Point to) {}
    private SpatialPincerMesh() {}
    public static List<Line> marker(Point feet) {
        if (feet == null || !feet.finite()) return List.of();
        var lines = new ArrayList<Line>(MARK_STROKES);
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            var corner = new Point(feet.x() + x * .34, feet.y() + .03, feet.z() + z * .34);
            lines.add(new Line(corner, new Point(feet.x() + x * .17, feet.y() + .03, corner.z())));
            lines.add(new Line(corner, new Point(corner.x(), feet.y() + .03, feet.z() + z * .17)));
        }
        return List.copyOf(lines);
    }
    public static List<Line> endpoint(Point point) {
        if (point == null || !point.finite()) return List.of();
        double r = .2;
        var a = new Point(point.x() - r, point.y() + .035, point.z() - r);
        var b = new Point(point.x() + r, point.y() + .035, point.z() - r);
        var c = new Point(point.x() + r, point.y() + .035, point.z() + r);
        var d = new Point(point.x() - r, point.y() + .035, point.z() + r);
        return List.of(new Line(a, b), new Line(b, c), new Line(c, d), new Line(d, a));
    }
    public static List<Line> route(Point source, Point destination) {
        if (source == null || destination == null || !source.finite() || !destination.finite() || source.equals(destination)) return List.of();
        return List.of(new Line(new Point(source.x(), source.y() + .045, source.z()), new Point(destination.x(), destination.y() + .045, destination.z())));
    }
    public static Point relative(Point point, Point camera) {
        return point == null || camera == null || !point.finite() || !camera.finite() ? null
                : new Point(point.x() - camera.x(), point.y() - camera.y(), point.z() - camera.z());
    }
    public static int frame(int age) { return Math.max(0, Math.min(SUCCESS_TICKS - 1, age)); }
    public static int alpha(int age) { return age < 0 || age >= SUCCESS_TICKS ? 0 : 210 * (SUCCESS_TICKS - age) / SUCCESS_TICKS; }
}
