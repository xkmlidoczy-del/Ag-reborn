package cn.academy.port.client;

import java.util.ArrayList;
import java.util.List;

/** Exact face-attached cut marks and native source rays; no camera-dependent target invention. */
public final class BeamEtchingMesh {
    public static final double MAX_RANGE = 12, HALF_CUT = .22, FACE_OFFSET = .0025, NEAR_DEPTH = .08;
    public static final double EDGE_WIDTH = .012, CORE_WIDTH = .0035, RAY_WIDTH = .009;
    public static final double RAY_NEAR_DEPTH = .20, MAX_RAY_ANGULAR_HALF_WIDTH = .002;
    public static final int CORE = 0xD8F8D8, EDGE = 0x176B35, PREVIEW = 0xA3DCAC;
    public static final int MAX_MARKS = 16, MAX_SHAPE_BOXES = 32, MAX_PARTICLES = 64, SPAWNED_PARTICLES = 0;
    public static final int MAX_MESH_QUADS = 512, MAX_VISIBLE_CONTEXTS = 4, FLASH_TICKS = 6;
    private static final double EPS = 1e-7;
    public record Point(double x, double y, double z) {
        public Point add(Point p) { return new Point(x + p.x, y + p.y, z + p.z); }
        public Point subtract(Point p) { return new Point(x - p.x, y - p.y, z - p.z); }
        public Point scale(double f) { return new Point(x * f, y * f, z * f); }
        public double dot(Point p) { return x * p.x + y * p.y + z * p.z; }
        public Point cross(Point p) { return new Point(y * p.z - z * p.y, z * p.x - x * p.z, x * p.y - y * p.x); }
        public double length() { return Math.sqrt(dot(this)); }
        public Point lerp(Point p, double t) { return add(p.subtract(this).scale(t)); }
        public double axis(int axis) { return axis == 0 ? x : axis == 1 ? y : z; }
    }
    public record Block(int x, int y, int z) {}
    public record Chunk(int x, int z) {}
    /** Face values deliberately match native Direction's DOWN/UP/NORTH/SOUTH/WEST/EAST ordinal. */
    public record Mark(Block block, Point point, int face) {}
    public record Box(Point min, Point max) {}
    public record Segment(Point start, Point end) {}
    public record Quad(Point a, Point b, Point c, Point d, int color, int alpha, int texture) {
        public List<Point> vertices() { return List.of(a, b, c, d); }
    }
    private BeamEtchingMesh() {}
    public static boolean validPoint(Point p) {
        return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z)
                && Math.abs(p.x) <= 30_000_000 && Math.abs(p.z) <= 30_000_000 && Math.abs(p.y) <= 4096;
    }
    public static boolean validDirection(Point p) { return validPoint(p) && Math.abs(p.length() - 1) <= EPS; }
    public static boolean validMark(Mark m) {
        if (m == null || m.block == null || !validPoint(m.point) || m.face < 0 || m.face > 5) return false;
        return m.point.x >= m.block.x - EPS && m.point.x <= m.block.x + 1 + EPS
                && m.point.y >= m.block.y - EPS && m.point.y <= m.block.y + 1 + EPS
                && m.point.z >= m.block.z - EPS && m.point.z <= m.block.z + 1 + EPS;
    }
    public static boolean validSource(Point origin, Mark m) {
        return validPoint(origin) && validMark(m) && origin.subtract(m.point).length() <= MAX_RANGE + EPS
                && origin.subtract(m.point).dot(normal(m.face)) >= -EPS;
    }
    public static Point normal(int face) {
        return switch (face) {
            case 0 -> new Point(0, -1, 0); case 1 -> new Point(0, 1, 0);
            case 2 -> new Point(0, 0, -1); case 3 -> new Point(0, 0, 1);
            case 4 -> new Point(-1, 0, 0); case 5 -> new Point(1, 0, 0);
            default -> throw new IllegalArgumentException("Native face required");
        };
    }
    /** Exact finite chunk corridor, including corner grazes that fixed-distance samples could skip. */
    public static List<Chunk> crossedChunks(Point from, Point to) {
        if (!validPoint(from) || !validPoint(to) || from.subtract(to).length() > 64) return List.of();
        int minX = (int)Math.floor((Math.min(from.x, to.x) - EPS) / 16), maxX = (int)Math.floor((Math.max(from.x, to.x) + EPS) / 16);
        int minZ = (int)Math.floor((Math.min(from.z, to.z) - EPS) / 16), maxZ = (int)Math.floor((Math.max(from.z, to.z) + EPS) / 16);
        List<Chunk> out = new ArrayList<>(12);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            double enter = 0, exit = 1;
            for (int a : new int[]{0, 2}) {
                double start = from.axis(a), delta = to.axis(a) - start, min = (a == 0 ? x : z) * 16.0 - EPS, max = min + 16 + 2 * EPS;
                if (Math.abs(delta) < EPS) { if (start < min || start > max) { enter = 1; exit = 0; break; } }
                else {
                    double t1 = (min - start) / delta, t2 = (max - start) / delta;
                    enter = Math.max(enter, Math.min(t1, t2)); exit = Math.min(exit, Math.max(t1, t2));
                }
            }
            if (enter <= exit) out.add(new Chunk(x, z));
        }
        return List.copyOf(out);
    }
    private static int axis(int face) { return face < 2 ? 1 : face < 4 ? 2 : 0; }
    private static Point tangent(int face) { return face < 4 ? new Point(1, 0, 0) : new Point(0, 0, 1); }
    /** Choose a real native outline face containing the actual hit, including slabs and partial blocks. */
    public static Box surface(Mark m, List<Box> boxes) {
        if (!validMark(m) || boxes == null || boxes.size() > MAX_SHAPE_BOXES) return null;
        int axis = axis(m.face); Box result = null; double area = -1;
        for (Box box : boxes) {
            if (box == null || !validPoint(box.min) || !validPoint(box.max)) continue;
            double surface = (m.face & 1) == 0 ? box.min.axis(axis) : box.max.axis(axis);
            if (Math.abs(surface - m.point.axis(axis)) > EPS) continue;
            double candidate = 1; boolean contains = true;
            for (int a = 0; a < 3; a++) {
                if (box.min.axis(a) > box.max.axis(a)) { contains = false; break; }
                if (a == axis) continue;
                if (m.point.axis(a) < box.min.axis(a) - EPS || m.point.axis(a) > box.max.axis(a) + EPS) contains = false;
                candidate *= box.max.axis(a) - box.min.axis(a);
            }
            if (contains && candidate > area) { result = box; area = candidate; }
        }
        return result;
    }
    /** Dashed preparatory cuts become solid only on a confirmed native removal receipt. */
    public static List<Quad> cut(Mark mark, Box surface, boolean confirmed, int age) {
        if (!validMark(mark) || surface == null || surface(mark, List.of(surface)) == null
                || age < 0 || confirmed && age >= FLASH_TICKS) return List.of();
        Point normal = normal(mark.face), u = tangent(mark.face), v = normal.cross(u);
        Point direction = u.add(v).scale(1 / Math.sqrt(2)), side = normal.cross(direction);
        Point center = mark.point.add(normal.scale(FACE_OFFSET));
        List<Quad> out = new ArrayList<>(12);
        double[][] intervals = confirmed ? new double[][]{{-1, 1}} : new double[][]{{-1, -.52}, {-.24, .24}, {.52, 1}};
        int alpha = confirmed ? 255 * (FLASH_TICKS - age) / FLASH_TICKS : 205;
        for (double[] interval : intervals) {
            Point a = center.add(direction.scale(interval[0] * HALF_CUT)), b = center.add(direction.scale(interval[1] * HALF_CUT));
            for (int layer = 0; layer < 2; layer++) {
                double width = layer == 0 ? EDGE_WIDTH : CORE_WIDTH;
                Point offset = side.scale(width);
                Quad quad = new Quad(a.add(offset), a.subtract(offset), b.subtract(offset), b.add(offset),
                        layer == 0 ? EDGE : confirmed ? CORE : PREVIEW, alpha, -1);
                out.addAll(clipSurface(quad, mark, surface));
            }
        }
        return List.copyOf(out);
    }
    /** A cut is clipped to its selected native shape face and never enlarged to a full cube. */
    private static List<Quad> clipSurface(Quad quad, Mark mark, Box box) {
        List<Point> points = quad.vertices(); int normalAxis = axis(mark.face);
        for (int a = 0; a < 3; a++) if (a != normalAxis) {
            final int axis = a;
            points = clip(points, p -> p.axis(axis) - box.min.axis(axis));
            points = clip(points, p -> box.max.axis(axis) - p.axis(axis));
        }
        return quads(points, quad);
    }
    public static List<Quad> confirmedRay(Point origin, Mark mark, Point camera, Point look, int age) {
        if (!validSource(origin, mark) || !validPoint(camera) || !validDirection(look) || age < 0 || age >= FLASH_TICKS) return List.of();
        Point end = mark.point, direction = end.subtract(origin);
        if (direction.length() < EPS) return List.of();
        Point side = direction.cross(camera.subtract(origin));
        if (side.length() < EPS) side = direction.cross(Math.abs(look.y) > .9 ? new Point(1, 0, 0) : new Point(0, 1, 0));
        if (side.length() < EPS) return List.of();
        side = side.scale(1 / side.length());
        int alpha = 240 * (FLASH_TICKS - age) / FLASH_TICKS;
        List<Quad> out = new ArrayList<>(5);
        rayPart(out, origin, end, side, camera, look, RAY_WIDTH, EDGE, alpha, -1);
        double blend = Math.min(.2, .25 / direction.length());
        Point a = origin.lerp(end, blend), b = origin.lerp(end, 1 - blend);
        rayPart(out, origin, a, side, camera, look, RAY_WIDTH * .8, 0xFFFFFF, alpha, 0);
        rayPart(out, a, b, side, camera, look, RAY_WIDTH * .8, 0xFFFFFF, alpha, 1);
        rayPart(out, b, end, side, camera, look, RAY_WIDTH * .8, 0xFFFFFF, alpha, 2);
        rayPart(out, origin, end, side, camera, look, CORE_WIDTH, CORE, alpha, -1);
        return List.copyOf(out);
    }
    /** Clip the real centerline first. Expanding an eye-origin ribbon before clipping makes a giant perspective wedge. */
    private static void rayPart(List<Quad> out, Point a, Point b, Point side, Point camera, Point look,
                                double width, int color, int alpha, int texture) {
        double za = a.subtract(camera).dot(look), zb = b.subtract(camera).dot(look);
        if (za < RAY_NEAR_DEPTH && zb < RAY_NEAR_DEPTH) return;
        if (za < RAY_NEAR_DEPTH) a = a.lerp(b, (RAY_NEAR_DEPTH - za) / (zb - za));
        else if (zb < RAY_NEAR_DEPTH) b = a.lerp(b, (RAY_NEAR_DEPTH - za) / (zb - za));
        if (a.subtract(b).length() < EPS) return;
        Point wa = side.scale(projectiveWidth(a.subtract(camera), side, look, width));
        Point wb = side.scale(projectiveWidth(b.subtract(camera), side, look, width));
        out.add(new Quad(a.add(wa), a.subtract(wa), b.subtract(wb), b.add(wb), color, alpha, texture));
    }
    /** Bound true perspective displacement, including tilted ribbon sides and a strafing camera's historical source. */
    private static double projectiveWidth(Point relative, Point side, Point look, double physicalWidth) {
        double z = relative.dot(look), sideDepth = side.dot(look);
        double limit = MAX_RAY_ANGULAR_HALF_WIDTH * physicalWidth / RAY_WIDTH;
        double numerator = side.scale(z).subtract(relative.scale(sideDepth)).length();
        double divisor = numerator + limit * z * Math.abs(sideDepth);
        return divisor <= EPS ? physicalWidth : Math.min(physicalWidth, limit * z * z / divisor);
    }
    /** Clip each actual polygon before emitting; nothing behind the camera is projected as a marker. */
    public static List<Quad> nearClip(Quad quad, Point camera, Point look) {
        if (quad == null || !validPoint(camera) || !validDirection(look) || quad.vertices().stream().anyMatch(p -> !validPoint(p))) return List.of();
        return quads(clip(quad.vertices(), p -> p.subtract(camera).dot(look) - NEAR_DEPTH), quad);
    }
    private static List<Point> clip(List<Point> points, java.util.function.ToDoubleFunction<Point> distance) {
        if (points.size() < 3) return List.of();
        List<Point> out = new ArrayList<>(6); Point prior = points.getLast(); double priorDistance = distance.applyAsDouble(prior);
        for (Point point : points) {
            double d = distance.applyAsDouble(point);
            if ((d >= 0) != (priorDistance >= 0)) out.add(prior.lerp(point, priorDistance / (priorDistance - d)));
            if (d >= 0) out.add(point);
            prior = point; priorDistance = d;
        }
        return out;
    }
    private static List<Quad> quads(List<Point> points, Quad parent) {
        if (points.size() < 3) return List.of();
        if (points.size() == 4) return List.of(new Quad(points.get(0), points.get(1), points.get(2), points.get(3), parent.color, parent.alpha, parent.texture));
        List<Quad> result = new ArrayList<>(5);
        for (int i = 1; i < points.size() - 1; i++) result.add(new Quad(points.get(0), points.get(i), points.get(i + 1), points.get(0), parent.color, parent.alpha, parent.texture));
        return List.copyOf(result);
    }
    /** Global post-clipping frame ceiling, shared by all visible casters and all texture batches. */
    public static final class Budget {
        private int used;
        public boolean take() { if (used >= MAX_MESH_QUADS) return false; used++; return true; }
        public int used() { return used; }
    }
}
