package cn.academy.port.client;

import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Plate;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.ArrayList;
import java.util.List;

/** Fixed physical ellipses and receipt-only narrow rays. Camera transforms never reposition a mirror or ray. */
public final class FoldedMirrorBaselineMesh {
    public static final int ELLIPSE_STEPS = 24, MAX_VISIBLE_CONTEXTS = 4, MAX_MESH_QUADS = 1536, SPAWNED_PARTICLES = 0;
    public static final int CORE = 0xE6FFF0, EDGE = 0x155D34, GREEN = 0x8AE2A3, PREVIEW = 0x77A98B;
    public static final int FILL = 0, RIM = 1, SIDE = 2, GHOST = 3, PLANNED = 4, BEAM_EDGE = 5, BEAM_CORE = 6, REFRACTION = 7;
    public static final double NEAR_DEPTH = .08, RAY_NEAR_DEPTH = .20, MAX_ANGULAR_HALF_WIDTH = .002;
    private static final double EPS = 1e-8;
    public record Face(Vector a, Vector b, Vector c, Vector d, int color, int alpha, int kind, int texture) {
        public List<Vector> vertices() { return List.of(a, b, c, d); }
    }
    private FoldedMirrorBaselineMesh() {}
    private static Vector lerp(Vector a, Vector b, double t) { return a.add(b.subtract(a).scale(t)); }
    private static Vector ellipse(Plate plate, double angle, double scale, double depth) {
        return plate.center().add(FoldedMirrorGeometry.perimeterPoint(plate, angle).subtract(plate.center()).scale(scale))
                .add(plate.normal().scale(depth));
    }
    /** No disk bridges the gap between plates. Every face stays inside its own exact .45/.65/.04 slab. */
    public static List<Face> plate(Plate plate, boolean ghost) {
        if (plate == null) return List.of();
        List<Face> out = new ArrayList<>(ELLIPSE_STEPS * 4);
        for (int i = 0; i < ELLIPSE_STEPS; i++) {
            if (ghost && i % 2 != 0) continue;
            double a = Math.PI * 2 * i / ELLIPSE_STEPS, b = Math.PI * 2 * (i + 1) / ELLIPSE_STEPS;
            Vector oa = ellipse(plate, a, 1, 0), ob = ellipse(plate, b, 1, 0);
            Vector ia = ellipse(plate, a, .945, 0), ib = ellipse(plate, b, .945, 0);
            if (ghost) {
                out.add(new Face(oa, ob, ib, ia, PREVIEW, 160, GHOST, -1));
                continue;
            }
            out.add(new Face(plate.center(), ia, ib, plate.center(), GREEN, 16, FILL, -1));
            out.add(new Face(oa, ob, ib, ia, EDGE, 205, RIM, -1));
            out.add(new Face(ia, ib, ellipse(plate, b, .925, 0), ellipse(plate, a, .925, 0), GREEN, 175, RIM, -1));
            double half = FoldedMirrorGeometry.THICKNESS / 2;
            out.add(new Face(ellipse(plate, a, 1, -half), ellipse(plate, b, 1, -half),
                    ellipse(plate, b, 1, half), ellipse(plate, a, 1, half), EDGE, 100, SIDE, -1));
        }
        return List.copyOf(out);
    }
    /** The same packet geometry is dotted until a paid FIRE receipt arrives; it never produces impact effects. */
    public static List<Face> path(FoldedMirrorState.Frame frame, Vector camera, Vector look, int age) {
        if (!FoldedMirrorState.valid(frame) || !validCamera(camera, look) || frame.phase() == FoldedMirrorState.ENDED) return List.of();
        boolean confirmed = frame.phase() == FoldedMirrorState.FIRE;
        if (confirmed && (age < 0 || age >= FoldedMirrorState.FLASH_TICKS)) return List.of();
        List<Face> out = new ArrayList<>();
        for (int i = 0; i < frame.path().size(); i++) {
            var s = frame.path().get(i);
            Vector delta = s.end().subtract(s.start()); double length = delta.length();
            Vector side = side(s.start(), s.end(), camera, look); if (side == null) continue;
            if (!confirmed) {
                int steps = (int)Math.ceil(length / .65);
                for (int dash = 0; dash < steps; dash++) {
                    double a = dash * .65 / length, b = Math.min(1, (dash * .65 + .20) / length);
                    ray(out, lerp(s.start(), s.end(), a), lerp(s.start(), s.end(), b), side, camera, look,
                            .006, .006, PREVIEW, 140, PLANNED, -1);
                }
                continue;
            }
            int alpha = 245 * (FoldedMirrorState.FLASH_TICKS - age) / FoldedMirrorState.FLASH_TICKS;
            double blend = Math.min(.24 / length, .25);
            double[] cut = {0, blend, 1 - blend, 1};
            boolean entranceFold = i > 0, exitFold = i + 1 < frame.path().size();
            for (int part = 0; part < 3; part++) {
                Vector a = lerp(s.start(), s.end(), cut[part]), b = lerp(s.start(), s.end(), cut[part + 1]);
                double wa = entranceFold && part == 0 ? .32 : 1, wb = exitFold && part == 2 ? .32 : 1;
                ray(out, a, b, side, camera, look, FoldedMirrorGeometry.BEAM_RADIUS * wa,
                        FoldedMirrorGeometry.BEAM_RADIUS * wb, EDGE, alpha, BEAM_EDGE, -1);
                ray(out, a, b, side, camera, look, .014 * wa, .014 * wb, GREEN, alpha, BEAM_CORE, part);
                ray(out, a, b, side, camera, look, .006 * wa, .006 * wb, CORE, alpha, BEAM_CORE, -1);
            }
            if (exitFold) {
                Plate plate = FoldedMirrorState.reflectionPlate(frame.plates(), s, frame.path().get(i + 1));
                if (plate != null) out.addAll(refraction(plate, s.end(), camera, look, alpha));
            }
        }
        return List.copyOf(out);
    }
    /** Short sharp green arcs sit on the real refraction point; there is no target hit spark API. */
    private static List<Face> refraction(Plate plate, Vector point, Vector camera, Vector look, int alpha) {
        List<Face> out = new ArrayList<>(8); var basis = FoldedMirrorGeometry.frame(plate);
        for (int i = 0; i < 8; i++) {
            double a = -.9 + i * .225, b = a + .225;
            Vector from = point.add(basis.right().scale(Math.cos(a) * .052)).add(basis.up().scale(Math.sin(a) * .052));
            Vector to = point.add(basis.right().scale(Math.cos(b) * .052)).add(basis.up().scale(Math.sin(b) * .052));
            if (!inside(plate, from) || !inside(plate, to)) continue;
            Vector side = side(from, to, camera, look);
            if (side != null) ray(out, from, to, side, camera, look, .004, .004, GREEN, alpha, REFRACTION, -1);
        }
        return out;
    }
    private static boolean inside(Plate plate, Vector point) {
        var basis = FoldedMirrorGeometry.frame(plate); Vector delta = point.subtract(plate.center());
        return Math.hypot(delta.dot(basis.right()) / FoldedMirrorGeometry.RADIUS_X,
                delta.dot(basis.up()) / FoldedMirrorGeometry.RADIUS_Y) <= 1;
    }
    private static boolean validCamera(Vector camera, Vector look) { return camera != null && camera.finite() && FoldedMirrorState.direction(look); }
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
    /** Shared post-clipping cap for all visible owners and texture batches. */
    public static final class Budget {
        private int used;
        public boolean take() { if (used >= MAX_MESH_QUADS) return false; used++; return true; }
        public int used() { return used; }
    }
}
