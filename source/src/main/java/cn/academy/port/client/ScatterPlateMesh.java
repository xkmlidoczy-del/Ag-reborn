package cn.academy.port.client;

import cn.academy.port.highpower.core.ScatterPlateGeometry;
import cn.academy.port.highpower.core.ScatterPlateGeometry.Pose;
import cn.academy.port.highpower.core.ScatterPlateGeometry.Vector;
import java.util.ArrayList;
import java.util.List;

/** True thin elliptical meshes built from the same pose and dimensions used by server crossings. */
public final class ScatterPlateMesh {
    public static final int SEGMENTS = 24, MAX_PARTICLES = 32, SPAWNED_PARTICLES = 0;
    public static final int MAX_MESH_QUADS = 600, CORE = 0xD8F8D8, EDGE = 0x176B35, INNER = 0xA3DCAC;
    public static final double NEAR_DEPTH = .06;
    public static final int FILL = 0, RIM = 1, LIGHT = 2, SIDE = 3, BREAK = 4, CONTACT = 5;
    public record Face(Vector a, Vector b, Vector c, Vector d, int color, int alpha, int plate, int kind) {
        public List<Vector> vertices() { return List.of(a, b, c, d); }
    }
    private ScatterPlateMesh() {}
    public static Vector add(Vector a, Vector b) { return new Vector(a.x() + b.x(), a.y() + b.y(), a.z() + b.z()); }
    public static Vector subtract(Vector a, Vector b) { return new Vector(a.x() - b.x(), a.y() - b.y(), a.z() - b.z()); }
    public static Vector scale(Vector a, double n) { return new Vector(a.x() * n, a.y() * n, a.z() * n); }
    public static double dot(Vector a, Vector b) { return a.x() * b.x() + a.y() * b.y() + a.z() * b.z(); }
    public static double length(Vector a) { return Math.sqrt(dot(a, a)); }
    private static Vector lerp(Vector a, Vector b, double n) { return add(a, scale(subtract(b, a), n)); }
    public static boolean validPoint(Vector p) {
        return p != null && Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z())
                && Math.abs(p.x()) <= 30_000_000 && Math.abs(p.z()) <= 30_000_000 && Math.abs(p.y()) <= 4096;
    }
    public static boolean validDirection(Vector p) { return validPoint(p) && Math.abs(length(p) - 1) < 1e-5; }
    private static Vector ellipse(Pose p, Vector center, double angle, double radius, double depth) {
        return add(add(add(center, scale(p.right(), Math.cos(angle) * ScatterPlateGeometry.RADIUS_X * radius)),
                scale(p.up(), Math.sin(angle) * ScatterPlateGeometry.RADIUS_Y * radius)), scale(p.axis(), depth));
    }
    /** Never expands the server ellipse. Rims sit inside it and depth is the exact .04 m slab. */
    public static List<Face> plate(Pose pose, int plate, boolean live, int hitAge) {
        if (pose == null || plate < 0 || plate >= 3 || !validPoint(pose.center()) || !validDirection(pose.axis())) return List.of();
        boolean flash = hitAge >= 0 && hitAge < ScatterPlateState.HIT_TICKS;
        if (!live && !flash) return List.of();
        Vector center = ScatterPlateGeometry.plateCenter(pose, plate);
        List<Face> faces = new ArrayList<>(SEGMENTS * 4);
        int energy = flash ? 170 * (ScatterPlateState.HIT_TICKS - hitAge) / ScatterPlateState.HIT_TICKS : 0;
        double half = ScatterPlateGeometry.THICKNESS / 2;
        for (int i = 0; i < SEGMENTS; i++) {
            double a = 2 * Math.PI * i / SEGMENTS, b = 2 * Math.PI * (i + 1) / SEGMENTS;
            Vector oa = ellipse(pose, center, a, 1, 0), ob = ellipse(pose, center, b, 1, 0);
            Vector ia = ellipse(pose, center, a, .945, 0), ib = ellipse(pose, center, b, .945, 0);
            if (!live) {
                // Four short rim fragments signal this receipt's break; there is no plate face across the gap.
                if (i % 6 == 0) faces.add(new Face(oa, ob, ib, ia, CORE, energy, plate, BREAK));
                continue;
            }
            faces.add(new Face(center, ia, ib, center, INNER, 15 + energy / 10, plate, FILL));
            faces.add(new Face(oa, ob, ib, ia, EDGE, 190, plate, RIM));
            Vector la = ellipse(pose, center, a, .925, 0), lb = ellipse(pose, center, b, .925, 0);
            faces.add(new Face(ia, ib, lb, la, flash ? CORE : INNER, Math.min(255, 158 + energy), plate, LIGHT));
            faces.add(new Face(ellipse(pose, center, a, 1, -half), ellipse(pose, center, b, 1, -half),
                    ellipse(pose, center, b, 1, half), ellipse(pose, center, a, 1, half), EDGE, 100, plate, SIDE));
        }
        return List.copyOf(faces);
    }
    /** Historical crossing coordinates may move away from the caster; never invent a new contact location. */
    public static boolean contactNear(Pose pose, int plate, Vector point) {
        if (pose == null || plate < 0 || plate >= 3 || !validPoint(point)) return false;
        Vector delta = subtract(point, ScatterPlateGeometry.plateCenter(pose, plate));
        double x = dot(delta, pose.right()) / ScatterPlateGeometry.RADIUS_X;
        double y = dot(delta, pose.up()) / ScatterPlateGeometry.RADIUS_Y;
        return Math.abs(dot(delta, pose.axis())) <= ScatterPlateGeometry.THICKNESS / 2 + .035 && x * x + y * y <= 1.04;
    }
    public static List<Face> contact(Pose pose, int plate, Vector point, int age) {
        if (age < 0 || age >= ScatterPlateState.HIT_TICKS || !contactNear(pose, plate, point)) return List.of();
        List<Face> result = new ArrayList<>(4); double radius = .022 + age * .004;
        for (Vector direction : List.of(pose.right(), pose.up())) for (int sign : new int[]{-1, 1}) {
            Vector start = add(point, scale(direction, sign * radius * .45)), end = add(point, scale(direction, sign * radius));
            Vector width = scale(direction == pose.right() ? pose.up() : pose.right(), .003);
            result.add(new Face(add(start, width), subtract(start, width), subtract(end, width), add(end, width),
                    CORE, 230 * (ScatterPlateState.HIT_TICKS - age) / ScatterPlateState.HIT_TICKS, plate, CONTACT));
        }
        return List.copyOf(result);
    }
    /** Sutherland-Hodgman near-plane clipping retains each real silhouette; no aiming-cone cutout. */
    public static List<Face> nearClip(Face face, Vector camera, Vector look) {
        if (face == null || !validPoint(camera) || !validDirection(look)) return List.of();
        List<Vector> input = face.vertices(); List<Vector> output = new ArrayList<>(6);
        Vector previous = input.getLast(); double prior = dot(subtract(previous, camera), look) - NEAR_DEPTH;
        for (Vector current : input) {
            if (!validPoint(current)) return List.of();
            double depth = dot(subtract(current, camera), look) - NEAR_DEPTH;
            if ((depth >= 0) != (prior >= 0)) output.add(lerp(previous, current, prior / (prior - depth)));
            if (depth >= 0) output.add(current);
            previous = current; prior = depth;
        }
        if (output.size() < 3) return List.of();
        if (output.size() == 4) return List.of(new Face(output.get(0), output.get(1), output.get(2), output.get(3), face.color, face.alpha, face.plate, face.kind));
        List<Face> clipped = new ArrayList<>(3);
        for (int i = 1; i < output.size() - 1; i++) clipped.add(new Face(output.get(0), output.get(i), output.get(i + 1), output.get(0), face.color, face.alpha, face.plate, face.kind));
        return List.copyOf(clipped);
    }
    /** Per-controller post-clipping cap; actual particle systems are never allocated. */
    public static final class Budget {
        private int used;
        public boolean take() { if (used >= MAX_MESH_QUADS) return false; used++; return true; }
        public int used() { return used; }
    }
}
