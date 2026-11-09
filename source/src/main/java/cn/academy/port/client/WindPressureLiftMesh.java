package cn.academy.port.client;

import cn.academy.port.highpower.core.WindPressureLiftRules.Mode;
import java.util.ArrayList;
import java.util.List;

/** Sparse open strokes only: the fixed six metre cylinder is neither a platform nor a solid wall. */
public final class WindPressureLiftMesh {
    public static final int MAX_CONTEXTS = 4, MAX_VERTICES = 2048, MAX_CONTEXT_VERTICES = 512, MAX_PARTICLES = 0;
    public static final double CULL_DISTANCE = 64, NEAR_PLANE = .08;
    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public double distanceSquared(Point p) { Point d = subtract(this, p); return dot(d, d); }
    }
    public record Stroke(Point from, Point to, double width, int alpha) {}
    public record Shape(List<Stroke> strokes) {
        public Shape { strokes = List.copyOf(strokes); }
        public int vertexCount() { return strokes.size() * 8; }
    }
    public static final class Budget {
        private int used;
        public boolean take(int vertices) {
            if (vertices <= 0 || vertices > MAX_CONTEXT_VERTICES || used > MAX_VERTICES - vertices) return false;
            used += vertices; return true;
        }
        public int used() { return used; }
    }
    private WindPressureLiftMesh() {}
    public static boolean validPoint(Point p) {
        return p != null && p.finite() && Math.abs(p.x) <= 30_000_000 && Math.abs(p.z) <= 30_000_000 && Math.abs(p.y) <= 4096;
    }
    public static boolean horizontalUnit(Point p) { return p != null && p.finite() && Math.abs(p.y) <= 1e-8 && Math.abs(dot(p, p) - 1) <= 1e-6; }
    public static Shape shape(WindPressureLiftState.Controller c, double partial) {
        if (c == null || !WindPressureLiftState.valid(c.frame()) || c.frame().terminal()
                || c.quiet() >= WindPressureLiftState.QUIET_TICKS || !Double.isFinite(partial) || partial < 0 || partial >= 1)
            return new Shape(List.of());
        var strokes = new ArrayList<Stroke>(44); var f = c.frame(); Point base = f.anchor();
        // Deliberate quarter gaps keep the foot boundary open; nothing is filled inside it.
        for (int i = 0; i < 16; i++) if (i % 4 != 3) {
            double a = 2 * Math.PI * i / 16, b = 2 * Math.PI * (i + .72) / 16;
            strokes.add(new Stroke(add(base, new Point(Math.cos(a) * 2.97, .035, Math.sin(a) * 2.97)),
                    add(base, new Point(Math.cos(b) * 2.97, .035, Math.sin(b) * 2.97)), .018, 116));
        }
        double phase = ((c.age() + partial) % 36) / 36;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4, radius = i % 2 == 0 ? 1.55 : 2.2;
            double x = Math.cos(angle) * radius, z = Math.sin(angle) * radius;
            for (int j = 0; j < 4; j++) {
                double y = .12 + ((j / 4.0 + phase + i * .071) % 1) * 5.05;
                double top = Math.min(5.88, y + .55);
                double bend0 = f.mode() == Mode.SIDE ? Math.pow(y / 6, 2) * .60 : 0;
                double bend1 = f.mode() == Mode.SIDE ? Math.pow(top / 6, 2) * .60 : 0;
                Point a = new Point(x + f.direction().x * bend0, y, z + f.direction().z * bend0);
                Point b = new Point(x + f.direction().x * bend1, top, z + f.direction().z * bend1);
                strokes.add(new Stroke(add(base, a), add(base, b), .023, 146));
            }
        }
        return new Shape(strokes);
    }
    public static Point relative(Point world, Point camera) { return subtract(world, camera); }
    public static double nearFade(Point p) { return p == null || !p.finite() ? 0 : Math.max(0, Math.min(1, (Math.sqrt(dot(p, p)) - .16) / .44)); }
    public static Stroke clipNear(Stroke s, Point forward, double halfWidth) {
        if (s == null || s.from == null || s.to == null || !s.from.finite() || !s.to.finite()
                || forward == null || !forward.finite() || Math.abs(dot(forward, forward) - 1) > .002
                || !Double.isFinite(halfWidth) || halfWidth < 0 || halfWidth > 1) return null;
        double plane = NEAR_PLANE + halfWidth, a = dot(s.from, forward), b = dot(s.to, forward);
        if (a < plane && b < plane) return null;
        Point from = s.from, to = s.to;
        if (a < plane) from = add(from, scale(subtract(to, from), (plane - a) / (b - a)));
        else if (b < plane) to = add(from, scale(subtract(to, from), (plane - a) / (b - a)));
        return new Stroke(from, to, s.width, s.alpha);
    }
    private static Point add(Point a, Point b) { return new Point(a.x+b.x,a.y+b.y,a.z+b.z); }
    private static Point subtract(Point a, Point b) { return new Point(a.x-b.x,a.y-b.y,a.z-b.z); }
    private static Point scale(Point p, double n) { return new Point(p.x*n,p.y*n,p.z*n); }
    private static double dot(Point a, Point b) { return a.x*b.x+a.y*b.y+a.z*b.z; }
}
