package cn.academy.port.client;

import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.client.FoldedMirrorMesh.Face;
import cn.academy.port.client.FoldedMirrorMesh.Uv;
import cn.academy.port.client.FoldedMirrorMesh.Material;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** NEW251 original core artwork centered on fixed receipts, and scoped NEW221 small-ray volumes. */
public final class MulticoreCanopyMesh {
    public static final int MAX_VISIBLE_CONTEXTS = 4, MAX_MESH_QUADS = 1536, SPAWNED_PARTICLES = 0;
    public static final int BEAM_EDGE = 5, BEAM_CORE = 6, HIT_GLINT = 7, BEAM_HALO = 8;
    public static final int ORB = 10, ORB_GLOW = 11, RIM = 12, PLANNED = 13;
    public static final int WHITE_TEXTURE = -1, ORB_TEXTURE = 4, GLOW_TEXTURE = 9;
    public static final int CORE = 0xE6FFF0, GREEN = 0x8AE2A3, PREVIEW = 0x77A98B;
    public static final int TUBE_SIDES = 8;
    public static final double ORB_HALF_SIZE = .19, GLOW_HALF_SIZE = .30, RIM_RADIUS = .235;
    public static final double RAY_NEAR_DEPTH = .20, MAX_ANGULAR_HALF_WIDTH = .006;
    public static final double HALO_RADIUS = .10, HALO_ANGULAR_HALF_WIDTH = .010;
    public static final double CORE_RADIUS = .020, CORE_ANGULAR_HALF_WIDTH = .0045;
    private static final double EPS = 1e-8;
    private MulticoreCanopyMesh() {}
    private static Vector lerp(Vector a, Vector b, double t) { return a.add(b.subtract(a).scale(t)); }
    private static boolean validCamera(Vector camera, Vector look) { return camera != null && camera.finite() && MulticoreCanopyState.direction(look); }
    private static Vector right(Vector look) {
        return FoldedMirrorGeometry.unit(look.cross(Math.abs(look.y()) > .9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0))).orElseThrow();
    }
    /** The old asymmetric billboard is intentionally not reused: UV (.5,.5) is the actual core center. */
    public static List<Face> core(MulticoreCanopyState.Core core, Vector camera, Vector look, float phase, float emissionPower) {
        if (core == null || !core.center().finite() || !validCamera(camera, look) || !core.available() && emissionPower <= 0) return List.of();
        List<Face> out = new ArrayList<>(26);
        Vector right = right(look), up = right.cross(look);
        float power = Math.clamp(emissionPower, 0, 1);
        int frame = Math.floorMod((int)Math.floor(phase / 2) + core.id(), 5);
        // Both original textures keep white vertex modulation. Only a proven emitted core gets peak alpha.
        out.add(board(core.center(), right, up, GLOW_HALF_SIZE, 92 + (int)(110 * power), ORB_GLOW, GLOW_TEXTURE));
        out.add(board(core.center(), right, up, ORB_HALF_SIZE, 204 + (int)(51 * power), ORB, ORB_TEXTURE + frame));
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24, b = Math.PI * 2 * (i + 1) / 24;
            Vector oa = rim(core.center(), right, up, a, RIM_RADIUS), ob = rim(core.center(), right, up, b, RIM_RADIUS);
            Vector ia = rim(core.center(), right, up, a, RIM_RADIUS - .004), ib = rim(core.center(), right, up, b, RIM_RADIUS - .004);
            out.add(new Face(oa, ob, ib, ia, GREEN, 35 + (int)(90 * power), RIM, WHITE_TEXTURE));
        }
        return List.copyOf(out);
    }
    private static Vector rim(Vector center, Vector right, Vector up, double angle, double radius) {
        return center.add(right.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
    }
    private static Face board(Vector center, Vector right, Vector up, double size, int alpha, int kind, int texture) {
        Vector x = right.scale(size), y = up.scale(size);
        return new Face(center.subtract(x).subtract(y), center.subtract(x).add(y), center.add(x).add(y), center.add(x).subtract(y),
                0xFFFFFF, alpha, kind, texture);
    }
    /** Status alone never lights all held cores: only core IDs from a recent authenticated path qualify. */
    public static float emissionPower(MulticoreCanopyState.Frame receipt, int coreId, float age) {
        if (receipt == null || receipt.phase() != MulticoreCanopyState.FIRE || age < 0 || age >= MulticoreCanopyState.FLASH_TICKS) return 0;
        return receipt.path().stream().anyMatch(s -> s.coreId() == coreId) ? 1 - age / MulticoreCanopyState.FLASH_TICKS : 0;
    }
    public record SourceTail(MulticoreCanopyState.Core core, float opacity) {}
    /** ENDED can retain only the sources of already-received FIRE paths, on their original flash clock. */
    public static List<SourceTail> terminalSources(MulticoreCanopyState.Frame receipt, float age) {
        if (!MulticoreCanopyState.valid(receipt) || receipt.phase() != MulticoreCanopyState.FIRE
                || !Float.isFinite(age) || age < 0 || age >= MulticoreCanopyState.FLASH_TICKS) return List.of();
        float opacity = 1 - age / MulticoreCanopyState.FLASH_TICKS;
        return receipt.cores().stream().filter(core -> receipt.path().stream().anyMatch(ray -> ray.coreId() == core.id()))
                .map(core -> new SourceTail(core, opacity)).toList();
    }
    /** This is a fading emission source, never a restored live core, target or controller lease. */
    public static List<Face> sourceTail(SourceTail tail, Vector camera, Vector look, float phase) {
        if (tail == null || tail.core() == null || !Float.isFinite(tail.opacity()) || tail.opacity() <= 0 || tail.opacity() > 1) return List.of();
        return core(tail.core(), camera, look, phase, tail.opacity()).stream().map(face ->
                new Face(face.a(), face.b(), face.c(), face.d(), face.color(), (int)(face.alpha() * tail.opacity()),
                        face.kind(), face.texture(), face.uvA(), face.uvB(), face.uvC(), face.uvD(), face.material())).toList();
    }
    /** Preview is intentionally dashed and subdued. It conveys steering only; no hit or beam is inferred. */
    public static List<Face> steering(Vector from, Vector to, Vector camera, Vector look) {
        if (from == null || to == null || !from.finite() || !to.finite() || !validCamera(camera, look)) return List.of();
        Vector delta = to.subtract(from); double length = delta.length();
        if (length < EPS || length > 96) return List.of();
        Vector side = side(from, to, camera, look); if (side == null) return List.of();
        List<Face> out = new ArrayList<>();
        // Maximum 80 short segments per steering line, including the original exact endpoints.
        double stride = Math.max(.8, length / 80);
        for (double start = 0; start < length; start += stride)
            ray(out, lerp(from, to, start / length), lerp(from, to, Math.min(length, start + stride * .24) / length),
                    side, camera, look, .004, .004, PREVIEW, 68, PLANNED, WHITE_TEXTURE);
        return List.copyOf(out);
    }
    /** Harmless dashed brackets use only a received last-visible point, with no invented target association. */
    public static List<Face> target(MulticoreCanopyState.Target target, Vector camera, Vector look) {
        if (target == null || !target.point().finite() || !validCamera(camera, look)) return List.of();
        List<Face> out = new ArrayList<>(8); Vector right = right(look), up = right.cross(look);
        int alpha = target.status() == MulticoreCanopyState.LOST ? 42 : 78;
        for (int x : new int[]{-1, 1}) for (int y : new int[]{-1, 1}) {
            if (target.status() == MulticoreCanopyState.LOST && x == y) continue;
            Vector corner = target.point().add(right.scale(x * .16)).add(up.scale(y * .16));
            for (Vector axis : List.of(right.scale(-x), up.scale(-y))) {
                Vector end = corner.add(axis.scale(.045)), side = side(corner, end, camera, look);
                if (side != null) ray(out, corner, end, side, camera, look, .003, .003, PREVIEW, alpha, PLANNED, WHITE_TEXTURE);
            }
        }
        return List.copyOf(out);
    }
    /** Closed eight-sided green-white volumes and original blend-in/tile/blend-out only on exact emitted rays. */
    public static List<Face> path(MulticoreCanopyState.Frame receipt, Vector camera, Vector look, float age) {
        if (!MulticoreCanopyState.valid(receipt) || receipt.phase() != MulticoreCanopyState.FIRE || !validCamera(camera, look)
                || age < 0 || age >= MulticoreCanopyState.FLASH_TICKS) return List.of();
        List<Face> out = new ArrayList<>();
        for (var s : receipt.path()) {
            double length = s.end().subtract(s.start()).length();
            Vector side = side(s.start(), s.end(), camera, look); if (side == null) continue;
            int alpha = (int)(245 * (1 - age / MulticoreCanopyState.FLASH_TICKS));
            double blend = Math.min(.24 / length, .25); double[] cut = {0, blend, 1 - blend, 1};
            for (int part = 0; part < 3; part++) {
                Vector a = lerp(s.start(), s.end(), cut[part]), b = lerp(s.start(), s.end(), cut[part + 1]);
                ray(out, a, b, side, camera, look, HALO_RADIUS, HALO_RADIUS, 0xFFFFFF, alpha * 128 / 245, BEAM_HALO, part);
                tube(out, a, b, camera, look, CORE_RADIUS, CORE_RADIUS, CORE, alpha * 230 / 245, BEAM_CORE,
                        part == 0, part == 2, cut[part] * length, cut[part + 1] * length);
                tube(out, a, b, camera, look, FoldedMirrorGeometry.BEAM_RADIUS, FoldedMirrorGeometry.BEAM_RADIUS,
                        0x6AF26A, alpha * 50 / 245, BEAM_EDGE, part == 0, part == 2, cut[part] * length, cut[part + 1] * length);
            }
            // Actual native damage, rather than intent/LOS/terrain/shield absorption, earns this tiny glint.
            if (s.status() == MulticoreCanopyState.DAMAGED) {
                Vector right = right(look), up = right.cross(look);
                for (Vector axis : List.of(right, up)) {
                    Vector a = s.end().subtract(axis.scale(.065)), b = s.end().add(axis.scale(.065));
                    Vector cross = side(a, b, camera, look);
                    if (cross != null) ray(out, a, b, cross, camera, look, .003, .003, CORE, alpha, HIT_GLINT, WHITE_TEXTURE);
                }
            }
        }
        return out.stream().map(face -> face.withMaterial(Material.phase(age))).toList();
    }
    private static Vector side(Vector from, Vector to, Vector camera, Vector look) {
        Vector axis = to.subtract(from), side = axis.cross(camera.subtract(from));
        if (side.length() < EPS) side = axis.cross(Math.abs(look.y()) > .9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0));
        return FoldedMirrorGeometry.unit(side).orElse(null);
    }
    private static void tube(List<Face> out, Vector a, Vector b, Vector camera, Vector look,
                             double radiusA, double radiusB, int rgb, int alpha, int kind, boolean capA, boolean capB, double uA, double uB) {
        Vector axis = FoldedMirrorGeometry.unit(b.subtract(a)).orElse(null); if (axis == null) return;
        Vector right = FoldedMirrorGeometry.unit(axis.cross(Math.abs(axis.y()) > .9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0))).orElseThrow();
        Vector up = axis.cross(right);
        double za = a.subtract(camera).dot(look), zb = b.subtract(camera).dot(look);
        if (za < RAY_NEAR_DEPTH && zb < RAY_NEAR_DEPTH) return;
        if (za < RAY_NEAR_DEPTH) {
            double t = (RAY_NEAR_DEPTH - za) / (zb - za); a = lerp(a, b, t); radiusA += (radiusB - radiusA) * t; uA += (uB - uA) * t; capA = false;
        } else if (zb < RAY_NEAR_DEPTH) {
            double t = (RAY_NEAR_DEPTH - za) / (zb - za); b = lerp(a, b, t); radiusB = radiusA + (radiusB - radiusA) * t; uB = uA + (uB - uA) * t; capB = false;
        }
        if (a.subtract(b).length() < EPS) return;
        Vector[] first = ring(a, right, up, camera, look, radiusA, kind), last = ring(b, right, up, camera, look, radiusB, kind);
        for (int i = 0; i < TUBE_SIDES; i++) {
            int next = (i + 1) % TUBE_SIDES;
            out.add(new Face(first[i], first[next], last[next], last[i], rgb, alpha, kind, WHITE_TEXTURE,
                    tubeUv(first[i], a, camera, axis, uA), tubeUv(first[next], a, camera, axis, uA),
                    tubeUv(last[next], b, camera, axis, uB), tubeUv(last[i], b, camera, axis, uB)));
        }
        // Four quads cover the eight triangular cap sectors; all vertices lie on the authentic endpoint plane.
        for (int i = 0; i < TUBE_SIDES; i += 2) {
            Uv startCap = new Uv(uA, 1), endCap = new Uv(uB, 1);
            if (capA) out.add(new Face(a, first[(i + 2) % TUBE_SIDES], first[i + 1], first[i], rgb, alpha, kind, WHITE_TEXTURE, startCap, startCap, startCap, startCap));
            if (capB) out.add(new Face(b, last[i], last[i + 1], last[(i + 2) % TUBE_SIDES], rgb, alpha, kind, WHITE_TEXTURE, endCap, endCap, endCap, endCap));
        }
    }
    private static Uv tubeUv(Vector point, Vector center, Vector camera, Vector axis, double u) {
        Vector normal = FoldedMirrorGeometry.unit(point.subtract(center)).orElse(new Vector(0, 1, 0));
        Vector toward = camera.subtract(center);
        Vector view = FoldedMirrorGeometry.unit(toward.subtract(axis.scale(toward.dot(axis)))).orElse(normal);
        return new Uv(u, .5 + .5 * Math.clamp(normal.dot(view), -1, 1));
    }
    private static Vector[] ring(Vector center, Vector right, Vector up, Vector camera, Vector look, double radius, int kind) {
        Vector[] directions = new Vector[TUBE_SIDES], points = new Vector[TUBE_SIDES];
        double bounded = radius;
        for (int i = 0; i < TUBE_SIDES; i++) {
            double angle = Math.PI * 2 * i / TUBE_SIDES;
            directions[i] = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            bounded = Math.min(bounded, projectiveWidth(center.subtract(camera), directions[i], look, radius, kind));
        }
        // One shared radius keeps a circular world-space section; camera clipping never turns it into paired ribbons.
        for (int i = 0; i < TUBE_SIDES; i++) points[i] = center.add(directions[i].scale(bounded));
        return points;
    }
    /** Clip the authentic centerline before expanding width, including historic source positions after strafing. */
    private static void ray(List<Face> out, Vector a, Vector b, Vector side, Vector camera, Vector look,
                            double widthA, double widthB, int rgb, int alpha, int kind, int texture) {
        double za = a.subtract(camera).dot(look), zb = b.subtract(camera).dot(look);
        double uA = 0, uB = 1;
        if (za < RAY_NEAR_DEPTH && zb < RAY_NEAR_DEPTH) return;
        if (za < RAY_NEAR_DEPTH) {
            double t = (RAY_NEAR_DEPTH - za) / (zb - za); a = lerp(a, b, t); widthA += (widthB - widthA) * t; uA = t;
        } else if (zb < RAY_NEAR_DEPTH) {
            double t = (RAY_NEAR_DEPTH - za) / (zb - za); b = lerp(a, b, t); widthB = widthA + (widthB - widthA) * t; uB = t;
        }
        if (a.subtract(b).length() < EPS) return;
        Vector wa = side.scale(projectiveWidth(a.subtract(camera), side, look, widthA, kind));
        Vector wb = side.scale(projectiveWidth(b.subtract(camera), side, look, widthB, kind));
        out.add(new Face(a.add(wa), a.subtract(wa), b.subtract(wb), b.add(wb), rgb, alpha, kind, texture,
                new Uv(uA, 1), new Uv(uA, 0), new Uv(uB, 0), new Uv(uB, 1)));
    }
    public static double angularLimit(int kind) {
        return kind == BEAM_HALO ? HALO_ANGULAR_HALF_WIDTH : kind == BEAM_CORE ? CORE_ANGULAR_HALF_WIDTH : MAX_ANGULAR_HALF_WIDTH;
    }
    private static double projectiveWidth(Vector relative, Vector side, Vector look, double width, int kind) {
        double z = relative.dot(look), depth = side.dot(look);
        double radius = kind == BEAM_HALO ? HALO_RADIUS : kind == BEAM_CORE ? CORE_RADIUS : FoldedMirrorGeometry.BEAM_RADIUS;
        double angular = angularLimit(kind) * width / radius;
        double numerator = side.scale(z).subtract(relative.scale(depth)).length();
        double denominator = numerator + angular * z * Math.abs(depth);
        return denominator <= EPS ? width : Math.min(width, angular * z * z / denominator);
    }
    /** Orb/halo art passes through the existing scoped shader; it never enters the shield or beam branches. */
    public static Face materialFace(Face face) {
        int materialKind = face.kind() == BEAM_CORE || face.kind() == BEAM_EDGE || face.kind() == BEAM_HALO ? face.kind() : 3;
        return new Face(face.a(), face.b(), face.c(), face.d(), face.color(), face.alpha(), materialKind, face.texture(),
                face.uvA(), face.uvB(), face.uvC(), face.uvD(), face.material());
    }
    public static List<Face> ordered(List<Face> faces, Vector camera, Vector look, Budget budget) {
        List<Face> clipped = new ArrayList<>();
        faces.stream().filter(f -> f.alpha() > 0).sorted(Comparator.comparingInt(MulticoreCanopyMesh::priority)).forEach(face -> {
            for (Face part : FoldedMirrorMesh.nearClip(face, camera, look)) if (budget.take()) clipped.add(part);
        });
        clipped.sort(Comparator.comparingInt(MulticoreCanopyMesh::layer)
                .thenComparing(Comparator.comparingDouble((Face f) -> f.a().add(f.b()).add(f.c()).add(f.d()).scale(.25).subtract(camera).length()).reversed()));
        return List.copyOf(clipped);
    }
    private static int priority(Face face) {
        return switch (face.kind()) { case BEAM_CORE -> 0; case BEAM_EDGE, HIT_GLINT -> 1; case ORB -> 2;
            case BEAM_HALO -> 3; case ORB_GLOW -> 4; case PLANNED -> 5; default -> 6; };
    }
    public static int layer(Face face) {
        return switch (face.kind()) { case PLANNED -> 0; case ORB_GLOW -> 1; case ORB -> 2; case RIM -> 3;
            case BEAM_HALO -> 4; case BEAM_EDGE -> 5; case BEAM_CORE -> 6; case HIT_GLINT -> 7; default -> 0; };
    }
    public static boolean additive(Face face) { return face.kind() == BEAM_HALO || face.kind() == RIM || face.kind() == HIT_GLINT; }
    public static boolean culled(Face face) { return face.kind() == BEAM_CORE || face.kind() == BEAM_EDGE; }
    /** The one global frame budget includes conservative native-font label reservations after geometry. */
    public static final class Budget {
        private int used;
        public boolean take() { return reserve(1); }
        public boolean reserve(int quads) { if (quads < 0 || used + quads > MAX_MESH_QUADS) return false; used += quads; return true; }
        public int used() { return used; }
    }
}
