package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Double-space dashed plans and small separate confirmed endpoints; native item renders are untouched. */
public final class SpatialRelayMesh {
    public static final int SUCCESS_TICKS = 7, MAX_MARKERS = 3, MAX_DECOR = 64,
            MAX_PLANNED_STROKES = 48, CONFIRMED_STROKES = 8, CONFIRMED_QUADS = 8;
    public static final double CULL_DISTANCE = 64;
    public record Line(Point from, Point to) {}
    private record Route(Point from, Point to) {}
    private SpatialRelayMesh() {}
    public static List<Line> marker(Point p) {
        if (p == null || !p.finite()) return List.of();
        return square(p,.22);
    }
    public static List<Line> endpoint(Point p) {
        if (p == null || !p.finite()) return List.of();
        return square(p,.32);
    }
    private static List<Line> square(Point p, double radius) {
        var a = new Point(p.x()-radius,p.y()+.025,p.z()-radius);
        var b = new Point(p.x()+radius,p.y()+.025,p.z()-radius);
        var c = new Point(p.x()+radius,p.y()+.025,p.z()+radius);
        var d = new Point(p.x()-radius,p.y()+.025,p.z()+radius);
        return List.of(new Line(a,b),new Line(b,c),new Line(c,d),new Line(d,a));
    }
    /** Common A→B/B→C routes share strokes; every distinct leg retains its actual endpoints. */
    public static List<Line> planned(List<SpatialRelayState.Leg> legs) {
        if (legs == null || legs.size() > SpatialRelayState.MAX_LEGS) return List.of();
        var routes = new LinkedHashSet<Route>();
        for (var leg : legs) if (leg != null && leg.from() != null && leg.to() != null && leg.from().finite() && leg.to().finite()
                && !leg.from().equals(leg.to())) routes.add(new Route(leg.from(),leg.to()));
        if (routes.isEmpty()) return List.of();
        int each = Math.max(1, MAX_PLANNED_STROKES / routes.size());
        var result = new ArrayList<Line>(MAX_PLANNED_STROKES);
        for (var route : routes) {
            int count = Math.max(1,Math.min(each,(int)Math.ceil(Math.sqrt(route.from.distanceSquared(route.to))/.9)));
            for (int i=0;i<count;i++) {
                // Every stroke has a visible gap. A lone short leg remains dashed at both ends.
                double start = (i+.12)/count, end = (i+.68)/count;
                result.add(new Line(lerp(route.from,route.to,start),lerp(route.from,route.to,end)));
            }
        }
        return List.copyOf(result);
    }
    public static int frame(int age) { return Math.max(0,Math.min(SUCCESS_TICKS-1,age)); }
    public static int alpha(int age) { return age < 0 || age >= SUCCESS_TICKS ? 0 : 210*(SUCCESS_TICKS-age)/SUCCESS_TICKS; }
    public static Point relative(Point world, Point camera) {
        return world == null || camera == null || !world.finite() || !camera.finite() ? null
                : new Point(world.x()-camera.x(),world.y()-camera.y(),world.z()-camera.z());
    }
    private static Point lerp(Point a,Point b,double t) { return new Point(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t); }
}
