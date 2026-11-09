package cn.academy.port.client;

import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.ArrayList;
import java.util.List;

/** NEW231 true 3D hollow bounds and narrow receipt-only sweep rays; no filled sector or terrain explosion. */
public final class DisintegrationSweepMesh {
    public static final int MAX_VISIBLE_CONTEXTS = 4, MAX_MESH_QUADS = 1536, SPAWNED_PARTICLES = 0;
    public static final int CORE = 0xE6FFF0, EDGE = 0x155D34, GREEN = 0x8AE2A3, PREVIEW = 0x77A98B;
    public static final int WARNING = 0, BEAM_EDGE = 1, BEAM_CORE = 2, TRAIL = 3, HIT_STAR = 4;
    public static final double NEAR_DEPTH = .08, RAY_NEAR_DEPTH = .20, MAX_ANGULAR_HALF_WIDTH = .002;
    private static final double EPS = 1e-8;
    public record Face(Vector a, Vector b, Vector c, Vector d, int color, int alpha, int kind, int texture) {
        public List<Vector> vertices() { return List.of(a, b, c, d); }
    }
    private DisintegrationSweepMesh() {}
    private static Vector lerp(Vector a, Vector b, double t) { return a.add(b.subtract(a).scale(t)); }
    /** Two separated dotted boundary rays and the outer arc leave the entire fan interior transparent. */
    public static List<Face> warning(DisintegrationSweepState.Frame f, Vector camera, Vector look) {
        if (!DisintegrationSweepState.valid(f) || f.phase() == DisintegrationSweepState.ENDED || !validCamera(camera, look)) return List.of();
        List<Face> out = new ArrayList<>();
        for (double angle : new double[]{-DisintegrationSweepState.HALF_ANGLE, DisintegrationSweepState.HALF_ANGLE}) {
            Vector direction = DisintegrationSweepState.ray(f, angle);
            for (int i = 1; i < 48; i += 2) line(out, f.origin().add(direction.scale(i)), f.origin().add(direction.scale(i + .45)), camera, look, .008, PREVIEW, 100, WARNING, -1);
        }
        for (int i = 0; i < 24; i += 2) {
            double a = -DisintegrationSweepState.HALF_ANGLE + 2 * DisintegrationSweepState.HALF_ANGLE * i / 24;
            double b = -DisintegrationSweepState.HALF_ANGLE + 2 * DisintegrationSweepState.HALF_ANGLE * (i + 1) / 24;
            line(out, f.origin().add(DisintegrationSweepState.ray(f, a).scale(48)), f.origin().add(DisintegrationSweepState.ray(f, b).scale(48)), camera, look, .01, PREVIEW, 100, WARNING, -1);
        }
        return List.copyOf(out);
    }
    /** Every luminous core and fading trail follows a finite emitted ray in the native receipt. */
    public static List<Face> path(DisintegrationSweepState.Frame f, Vector camera, Vector look, int age) {
        return receipt(f, camera, look, age, false);
    }
    /** Completion only fades received geometry; it cannot restore the warning plane or a live beam core. */
    public static List<Face> afterglow(DisintegrationSweepState.Afterglow tail, Vector camera, Vector look) {
        return tail == null ? List.of() : receipt(tail.frame(), camera, look, tail.age(), true);
    }
    private static List<Face> receipt(DisintegrationSweepState.Frame f, Vector camera, Vector look, int age, boolean terminal) {
        if (!DisintegrationSweepState.valid(f) || f.phase() != DisintegrationSweepState.SWEEPING || !validCamera(camera, look)
                || age < 0 || age >= DisintegrationSweepState.FLASH_TICKS) return List.of();
        List<Face> out = new ArrayList<>();
        for (var s : f.path()) {
            double length = s.end().subtract(s.start()).length();
            // Native client Post may run between receipt delivery and the first render.
            if (terminal || age > 1) {
                line(out, s.start(), s.end(), camera, look, .005, GREEN, (terminal ? 60 : 80) * (4 - age) / 4, TRAIL, 1);
            } else {
                double blend = Math.min(.24 / length, .25); double[] cut = {0, blend, 1 - blend, 1};
                for (int part = 0; part < 3; part++) {
                    Vector a = lerp(s.start(), s.end(), cut[part]), b = lerp(s.start(), s.end(), cut[part + 1]);
                    line(out, a, b, camera, look, .035, EDGE, 220, BEAM_EDGE, -1);
                    line(out, a, b, camera, look, .014, GREEN, 245, BEAM_CORE, part);
                    line(out, a, b, camera, look, .006, CORE, 245, BEAM_CORE, -1);
                }
            }
            // Only actual native entity damage earns a small local star; misses, shield, refusal and terrain never do.
            if (!f.mining() && s.status() == DisintegrationSweepState.DAMAGED && age < (terminal ? 4 : 3)) {
                int alpha = terminal ? 140 * (4 - age) / 4 : 210 * (3 - age) / 3;
                Vector normal = f.forward().cross(f.across());
                for (Vector axis : List.of(f.forward(), f.across(), normal)) {
                    line(out, s.end().subtract(axis.scale(.055)), s.end().add(axis.scale(.055)), camera, look, .005, GREEN, alpha, HIT_STAR, -1);
                    line(out, s.end().subtract(axis.scale(.025)), s.end().add(axis.scale(.025)), camera, look, .002, CORE, alpha, HIT_STAR, -1);
                }
            }
        }
        return List.copyOf(out);
    }
    private static void line(List<Face> out, Vector a, Vector b, Vector camera, Vector look, double width, int rgb, int alpha, int kind, int texture) {
        Vector side = side(a, b, camera, look); if (side != null) ray(out, a, b, side, camera, look, width, width, rgb, alpha, kind, texture);
    }
    private static boolean validCamera(Vector camera, Vector look) { return camera != null && camera.finite() && DisintegrationSweepState.direction(look); }
    private static Vector side(Vector from, Vector to, Vector camera, Vector look) {
        Vector axis = to.subtract(from), side = axis.cross(camera.subtract(from));
        if (side.length() < EPS) side = axis.cross(Math.abs(look.y()) > .9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0));
        return FoldedMirrorGeometry.unit(side).orElse(null);
    }
    /** Clip the authentic centerline before expanding width, including historic source positions after strafing. */
    private static void ray(List<Face> out, Vector a, Vector b, Vector side, Vector camera, Vector look,
                            double widthA, double widthB, int rgb, int alpha, int kind, int texture) {
        double za = a.subtract(camera).dot(look), zb = b.subtract(camera).dot(look);
        if (za < RAY_NEAR_DEPTH && zb < RAY_NEAR_DEPTH) return;
        if (za < RAY_NEAR_DEPTH) {
            double t = (RAY_NEAR_DEPTH - za) / (zb - za); a = lerp(a, b, t); widthA += (widthB - widthA) * t;
        } else if (zb < RAY_NEAR_DEPTH) {
            double t = (RAY_NEAR_DEPTH - za) / (zb - za); b = lerp(a, b, t); widthB = widthA + (widthB - widthA) * t;
        }
        if (a.subtract(b).length() < EPS) return;
        Vector wa = side.scale(projectiveWidth(a.subtract(camera), side, look, widthA));
        Vector wb = side.scale(projectiveWidth(b.subtract(camera), side, look, widthB));
        out.add(new Face(a.add(wa), a.subtract(wa), b.subtract(wb), b.add(wb), rgb, alpha, kind, texture));
    }
    private static double projectiveWidth(Vector relative, Vector side, Vector look, double width) {
        double z = relative.dot(look), depth = side.dot(look);
        double angular = MAX_ANGULAR_HALF_WIDTH * width / FoldedMirrorGeometry.BEAM_RADIUS;
        double numerator = side.scale(z).subtract(relative.scale(depth)).length();
        double denominator = numerator + angular * z * Math.abs(depth);
        return denominator <= EPS ? width : Math.min(width, angular * z * z / denominator);
    }
    /** Ordinary polygon clipping keeps real thin silhouettes; no billboard replacement, camera hole or x-ray disk. */
    public static List<Face> nearClip(Face face, Vector camera, Vector look) {
        if (face == null || !validCamera(camera, look) || face.vertices().stream().anyMatch(p -> p == null || !p.finite())) return List.of();
        List<Vector> result = new ArrayList<>(6); Vector previous = face.d();
        double prior = previous.subtract(camera).dot(look) - NEAR_DEPTH;
        for (Vector point : face.vertices()) {
            double depth = point.subtract(camera).dot(look) - NEAR_DEPTH;
            if ((depth >= 0) != (prior >= 0)) result.add(lerp(previous, point, prior / (prior - depth)));
            if (depth >= 0) result.add(point);
            previous = point; prior = depth;
        }
        if (result.size() < 3) return List.of();
        if (result.size() == 4) return List.of(new Face(result.get(0), result.get(1), result.get(2), result.get(3), face.color, face.alpha, face.kind, face.texture));
        List<Face> out = new ArrayList<>(3);
        for (int i = 1; i < result.size() - 1; i++) out.add(new Face(result.get(0), result.get(i), result.get(i + 1), result.get(0), face.color, face.alpha, face.kind, face.texture));
        return List.copyOf(out);
    }
    /** One finite post-clipping cap covers every owner, core, trail, warning and hit star. */
    public static final class Budget {
        private int used;
        public boolean take() { if (used >= MAX_MESH_QUADS) return false; used++; return true; }
        public int used() { return used; }
    }
}
