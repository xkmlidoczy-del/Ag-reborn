package cn.academy.port.client;

import cn.academy.port.highpower.core.ShearWindbladeGeometry.Point;
import java.util.ArrayList;
import java.util.List;

/** Two materials on the certified blade contour. Their soft envelope is never damage geometry. */
public final class ShearWindbladeRenderGeometry {
    public static final int MAX_CONTROLLERS = 4, MAX_FRONT_STROKES = 16, MATERIAL_LAYERS = 2;
    public static final int SCENE_VERTEX_BUDGET = 2048, MAX_SCENE_VERTICES = MAX_CONTROLLERS * MAX_FRONT_STROKES * MATERIAL_LAYERS * 4;
    public static final int MAX_PARTICLES = 0, AFTERIMAGES = 0;
    public static final double CORE_WIDTH = .070, SHOULDER_WIDTH = .150, CULL_DISTANCE = 64, NEAR_PLANE = .08;
    public record Stroke(Point from, Point to, int blade, int chord, int alpha) {}
    public record Profile(double width, int alpha, int gray, float minV, float maxV) {}
    public record Shape(List<Stroke> strokes) {
        public Shape { strokes = List.copyOf(strokes); }
        public int vertexCount(boolean shoulder) { return strokes.size() * (shoulder ? 8 : 4); }
    }
    private ShearWindbladeRenderGeometry() {}

    /** Interpolation stops exactly at the latest receipt. No age-dependent future position exists. */
    public static Shape shape(ShearWindbladeState.Controller cast, double partial) {
        if (cast == null || !Double.isFinite(partial) || cast.age() < 0 || cast.age() >= ShearWindbladeState.LIFE_TICKS
                || cast.fronts().size() > 2) return new Shape(List.of());
        double t = Math.max(0, Math.min(1, cast.receiptAge() - 1 + partial));
        double stale = Math.max(0, Math.min(1, (cast.receiptAge() - 2 + partial) / 3));
        var lines = new ArrayList<Stroke>(MAX_FRONT_STROKES);
        for (var front : cast.fronts()) {
            // A stopped front resolves at its certified blocker, never beyond it.
            double fade = front.status() == ShearWindbladeState.TRAVELLING ? 1 - stale : 1 - .65 * t;
            int alpha = (int)Math.round(238 * fade);
            for (int chord = 0; chord < cn.academy.port.highpower.core.ShearWindbladeGeometry.CHORDS; chord++) {
                Point a = lerp(front.previous().get(chord), front.current().get(chord), t);
                Point b = lerp(front.previous().get(chord + 1), front.current().get(chord + 1), t);
                lines.add(new Stroke(a, b, front.index(), chord, alpha));
            }
        }
        return new Shape(lines);
    }
    public static Profile profile(Stroke stroke, boolean core) {
        return core ? new Profile(CORE_WIDTH, stroke.alpha(), 245, 11f / 32, 21f / 32)
                : new Profile(SHOULDER_WIDTH, (int)Math.round(stroke.alpha() * .30), 188, 0, 1);
    }
    /** Whole-front fallback: discard the soft material before any of the eight core chords. */
    public static boolean canUseShoulder(int coreVertices, int remaining) { return coreVertices >= 0 && coreVertices * 2 <= remaining; }
    public static Point relative(Point world, Point camera) { return subtract(world, camera); }
    public static double nearEyeFade(double distance) {
        if (!Double.isFinite(distance)) return 0;
        double t = Math.max(0, Math.min(1, (distance - .18) / .42));
        return t * t * (3 - 2 * t);
    }
    public static Stroke clipNear(Stroke relative, Point forward, double halfWidth) {
        double plane = NEAR_PLANE + Math.max(0, halfWidth);
        double a = dot(relative.from, forward), b = dot(relative.to, forward);
        if (a <= plane && b <= plane) return null;
        Point from = relative.from, to = relative.to;
        if (a < plane) from = lerp(from, to, (plane - a) / (b - a));
        else if (b < plane) to = lerp(from, to, (plane - a) / (b - a));
        return new Stroke(from, to, relative.blade, relative.chord, relative.alpha);
    }
    private static Point lerp(Point a, Point b, double t) { return new Point(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t, a.z() + (b.z() - a.z()) * t); }
    private static Point subtract(Point a, Point b) { return new Point(a.x() - b.x(), a.y() - b.y(), a.z() - b.z()); }
    private static double dot(Point a, Point b) { return a.x() * b.x() + a.y() * b.y() + a.z() * b.z(); }
}
