package cn.academy.port.client;

import cn.academy.port.client.BeamEtchingMesh.Box;
import cn.academy.port.client.BeamEtchingMesh.Mark;
import cn.academy.port.client.BeamEtchingMesh.Point;
import cn.academy.port.client.BeamEtchingMesh.Quad;
import java.util.ArrayList;
import java.util.List;

/** NEW233 a thin original MD ray converging on a small, exact native face. All geometry is world-space. */
public final class PointMeltdownMesh {
    public static final double MAX_RANGE = 36, FACE_OFFSET = .003, NEAR_DEPTH = .08, RAY_NEAR_DEPTH = .20;
    public static final double RAY_WIDTH = .012, CORE_WIDTH = .0035, MAX_RAY_ANGULAR_HALF_WIDTH = .002;
    public static final double RING_START_RADIUS = .18, RING_END_RADIUS = .045, SHOULDER_RADIUS = .085;
    public static final double NEAR_FADE_START = .55, NEAR_FADE_END = 1.40;
    public static final int CORE = 0xE7FFE9, GREEN = 0x72E79B, SHOULDER = 0x309E5B;
    public static final int RING_SEGMENTS = 24, FLASH_TICKS = 6, MAX_CONTEXTS = 4, MAX_MESH_QUADS = 512, SPAWNED_PARTICLES = 0;
    private static final double EPS = 1e-7;
    private PointMeltdownMesh() {}

    public static boolean validSource(Point origin, Mark mark) {
        return BeamEtchingMesh.validPoint(origin) && BeamEtchingMesh.validMark(mark)
                && origin.subtract(mark.point()).length() <= MAX_RANGE + EPS
                && origin.subtract(mark.point()).dot(BeamEtchingMesh.normal(mark.face())) >= -EPS;
    }
    public static double ringRadius(int focusTicks) {
        double progress = Math.clamp(focusTicks / 10.0, 0, 1);
        return RING_START_RADIUS + (RING_END_RADIUS - RING_START_RADIUS) * progress;
    }
    /** The endpoint fades out as a camera approaches instead of becoming a screen-sized target panel. */
    public static double nearFade(Point point, Point camera) {
        if (!BeamEtchingMesh.validPoint(point) || !BeamEtchingMesh.validPoint(camera)) return 0;
        double t = Math.clamp((point.subtract(camera).length() - NEAR_FADE_START) / (NEAR_FADE_END - NEAR_FADE_START), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Focus amount comes from elapsed real server ticks. This function never advances it itself. */
    public static List<Quad> focal(Mark mark, Box surface, int focusTicks, boolean focusing, boolean confirmed, int age, Point camera) {
        if (!BeamEtchingMesh.validMark(mark) || surface == null || BeamEtchingMesh.surface(mark, List.of(surface)) == null
                || focusTicks < 0 || focusTicks > 10 || age < 0 || confirmed && age >= FLASH_TICKS) return List.of();
        double opacity = nearFade(mark.point(), camera) * (confirmed ? (FLASH_TICKS - age) / (double) FLASH_TICKS : 1);
        if (opacity <= 0) return List.of();
        Point normal = BeamEtchingMesh.normal(mark.face());
        Point u = mark.face() < 4 ? new Point(1, 0, 0) : new Point(0, 0, 1), v = normal.cross(u);
        Point center = mark.point().add(normal.scale(FACE_OFFSET));
        List<Quad> out = new ArrayList<>(84);
        double progress = focusing ? focusTicks / 10.0 : 0;
        // Distinct thin annulus, transparent heat shoulder and tiny luminous core; no filled target-size disk.
        double radius = confirmed ? RING_END_RADIUS + .035 * age / FLASH_TICKS : ringRadius(focusing ? focusTicks : 0);
        annulus(out, mark, surface, center, u, v, radius - .004, radius + .004, GREEN, (int)((focusing || confirmed ? 215 : 125) * opacity), false);
        if (focusing || confirmed) {
            annulus(out, mark, surface, center, u, v, .026, SHOULDER_RADIUS, SHOULDER, (int)((confirmed ? 62 : 22 + 22 * progress) * opacity), false);
            annulus(out, mark, surface, center, u, v, .012, .026, GREEN, (int)((confirmed ? 165 : 75 + 60 * progress) * opacity), false);
        }
        annulus(out, mark, surface, center, u, v, 0, confirmed ? .025 : focusing ? .009 + .009 * progress : .006,
                CORE, (int)((confirmed ? 255 : focusing ? 215 : 130) * opacity), true);
        return List.copyOf(out);
    }

    private static void annulus(List<Quad> out, Mark mark, Box surface, Point center, Point u, Point v,
                                double inner, double outer, int color, int alpha, boolean disk) {
        int segments = disk ? 12 : RING_SEGMENTS;
        for (int i = 0; i < segments; i++) {
            double a = Math.PI * 2 * i / segments, b = Math.PI * 2 * (i + 1) / segments;
            Point da = u.scale(Math.cos(a)).add(v.scale(Math.sin(a))), db = u.scale(Math.cos(b)).add(v.scale(Math.sin(b)));
            Quad quad = new Quad(center.add(da.scale(inner)), center.add(da.scale(outer)),
                    center.add(db.scale(outer)), center.add(db.scale(inner)), color, alpha, -1);
            out.addAll(clipSurface(quad, mark, surface));
        }
    }

    /** Both layers and all three original texture sections follow only the receipt's actual source-to-face segment. */
    public static List<Quad> ray(Point origin, Mark mark, Point camera, Point look, boolean focusing, boolean confirmed, int age) {
        if (!validSource(origin, mark) || !BeamEtchingMesh.validPoint(camera) || !BeamEtchingMesh.validDirection(look)
                || age < 0 || confirmed && age >= FLASH_TICKS) return List.of();
        Point end = mark.point(), direction = end.subtract(origin);
        if (direction.length() < EPS) return List.of();
        Point side = direction.cross(camera.subtract(origin));
        if (side.length() < EPS) side = direction.cross(Math.abs(look.y()) > .9 ? new Point(1, 0, 0) : new Point(0, 1, 0));
        if (side.length() < EPS) return List.of();
        side = side.scale(1 / side.length());
        double fade = confirmed ? (FLASH_TICKS - age) / (double) FLASH_TICKS : 1;
        int alpha = (int)((confirmed ? 235 : focusing ? 165 : 48) * fade);
        List<Quad> out = new ArrayList<>(5);
        rayPart(out, origin, end, side, camera, look, RAY_WIDTH, SHOULDER, alpha / 3, -1);
        double blend = Math.min(.2, .25 / direction.length());
        Point a = origin.lerp(end, blend), b = origin.lerp(end, 1 - blend);
        rayPart(out, origin, a, side, camera, look, RAY_WIDTH * .75, 0xFFFFFF, alpha, 0);
        rayPart(out, a, b, side, camera, look, RAY_WIDTH * .75, 0xFFFFFF, alpha, 1);
        rayPart(out, b, end, side, camera, look, RAY_WIDTH * .75, 0xFFFFFF, alpha, 2);
        rayPart(out, origin, end, side, camera, look, CORE_WIDTH, CORE, alpha, -1);
        return List.copyOf(out);
    }

    /** Clip the true centerline before expansion; projectively bounded width prevents an eye-origin wedge. */
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
    private static double projectiveWidth(Point relative, Point side, Point look, double physicalWidth) {
        double z = relative.dot(look), sideDepth = side.dot(look), limit = MAX_RAY_ANGULAR_HALF_WIDTH * physicalWidth / RAY_WIDTH;
        double numerator = side.scale(z).subtract(relative.scale(sideDepth)).length();
        double divisor = numerator + limit * z * Math.abs(sideDepth);
        return divisor <= EPS ? physicalWidth : Math.min(physicalWidth, limit * z * z / divisor);
    }

    /** Clip to the actual slab/stair/partial-block face; the receipt cannot grow a fictitious full cube. */
    private static List<Quad> clipSurface(Quad quad, Mark mark, Box surface) {
        List<Point> points = quad.vertices();
        int normalAxis = mark.face() < 2 ? 1 : mark.face() < 4 ? 2 : 0;
        for (int a = 0; a < 3; a++) if (a != normalAxis) {
            final int axis = a;
            points = clip(points, p -> p.axis(axis) - surface.min().axis(axis));
            points = clip(points, p -> surface.max().axis(axis) - p.axis(axis));
        }
        return quads(points, quad);
    }
    private static List<Point> clip(List<Point> points, java.util.function.ToDoubleFunction<Point> distance) {
        if (points.size() < 3) return List.of();
        List<Point> out = new ArrayList<>(6); Point previous = points.getLast(); double before = distance.applyAsDouble(previous);
        for (Point point : points) {
            double now = distance.applyAsDouble(point);
            if ((now >= 0) != (before >= 0)) out.add(previous.lerp(point, before / (before - now)));
            if (now >= 0) out.add(point);
            previous = point; before = now;
        }
        return out;
    }
    private static List<Quad> quads(List<Point> points, Quad parent) {
        if (points.size() < 3) return List.of();
        if (points.size() == 4) return List.of(new Quad(points.get(0), points.get(1), points.get(2), points.get(3), parent.color(), parent.alpha(), parent.texture()));
        List<Quad> result = new ArrayList<>(5);
        for (int i = 1; i < points.size() - 1; i++) result.add(new Quad(points.get(0), points.get(i), points.get(i + 1), points.get(0), parent.color(), parent.alpha(), parent.texture()));
        return List.copyOf(result);
    }
    /** Shared by every context, including historical confirmed flashes, after polygon clipping. */
    public static final class Budget {
        private int used;
        public boolean take() { if (used >= MAX_MESH_QUADS) return false; used++; return true; }
        public int used() { return used; }
        public int vertices() { return used * 4; }
    }
}
