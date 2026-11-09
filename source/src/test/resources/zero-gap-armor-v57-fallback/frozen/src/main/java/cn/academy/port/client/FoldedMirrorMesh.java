package cn.academy.port.client;

import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Plate;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Fixed physical ellipses and receipt-only narrow rays. Camera transforms never reposition a mirror or ray. */
public final class FoldedMirrorMesh {
    public static final int ELLIPSE_STEPS = 48, TUBE_SIDES = 8, MAX_VISIBLE_CONTEXTS = 4, MAX_MESH_QUADS = 1536, SPAWNED_PARTICLES = 0;
    public static final int CORE = 0xE6FFF0, EDGE = 0x155D34, GREEN = 0x8AE2A3, PREVIEW = 0x77A98B;
    public static final int FILL = 0, RIM = 1, SIDE = 2, GHOST = 3, PLANNED = 4, BEAM_EDGE = 5, BEAM_CORE = 6, REFRACTION = 7, BEAM_HALO = 8;
    public static final int WHITE_TEXTURE = -1, SHIELD_TEXTURE = 3;
    public static final double NEAR_DEPTH = .08, RAY_NEAR_DEPTH = .20, MAX_ANGULAR_HALF_WIDTH = .006;
    /** The faint halo is decoration, never a collision radius. The physical ray remains exactly .035. */
    public static final double HALO_RADIUS = .10, HALO_ANGULAR_HALF_WIDTH = .010;
    public static final double CORE_RADIUS = .020, CORE_ANGULAR_HALF_WIDTH = .0045;
    private static final double EPS = 1e-8;
    public record Uv(double u, double v) {
        private Uv lerp(Uv other, double t) { return new Uv(u + (other.u - u) * t, v + (other.v - v) * t); }
    }
    /** Presentation-only state. No wall clock or unconfirmed impact can enter a material. */
    public record Material(float phase, float contactU, float contactV, float contactPower) {
        public static final Material STILL = new Material(0, .5f, .5f, 0);
        public static Material phase(float phase) { return new Material(phase, .5f, .5f, 0); }
    }
    public record Face(Vector a, Vector b, Vector c, Vector d, int color, int alpha, int kind, int texture,
                       Uv uvA, Uv uvB, Uv uvC, Uv uvD, Material material) {
        public Face(Vector a, Vector b, Vector c, Vector d, int color, int alpha, int kind, int texture,
                    Uv uvA, Uv uvB, Uv uvC, Uv uvD) {
            this(a, b, c, d, color, alpha, kind, texture, uvA, uvB, uvC, uvD, Material.STILL);
        }
        public Face(Vector a, Vector b, Vector c, Vector d, int color, int alpha, int kind, int texture) {
            this(a, b, c, d, color, alpha, kind, texture, new Uv(0, 1), new Uv(0, 0), new Uv(1, 0), new Uv(1, 1));
        }
        public Face withMaterial(Material value) { return new Face(a, b, c, d, color, alpha, kind, texture, uvA, uvB, uvC, uvD, value); }
        public List<Vector> vertices() { return List.of(a, b, c, d); }
        public List<Uv> uvs() { return List.of(uvA, uvB, uvC, uvD); }
    }
    private FoldedMirrorMesh() {}
    private static Vector lerp(Vector a, Vector b, double t) { return a.add(b.subtract(a).scale(t)); }
    private static Vector ellipse(Plate plate, double angle, double scale, double depth) {
        return plate.center().add(FoldedMirrorGeometry.perimeterPoint(plate, angle).subtract(plate.center()).scale(scale))
                .add(plate.normal().scale(depth));
    }
    /** No disk bridges the gap between plates. Every face stays inside its own exact .45/.65/.04 slab. */
    public static List<Face> plate(Plate plate, boolean ghost) {
        if (plate == null) return List.of();
        List<Face> out = new ArrayList<>(ELLIPSE_STEPS * 3);
        for (int i = 0; i < ELLIPSE_STEPS; i++) {
            if (ghost && i % 4 >= 2) continue;
            double a = Math.PI * 2 * i / ELLIPSE_STEPS, b = Math.PI * 2 * (i + 1) / ELLIPSE_STEPS;
            Vector oa = ellipse(plate, a, 1, 0), ob = ellipse(plate, b, 1, 0);
            Vector ia = ellipse(plate, a, .978, 0), ib = ellipse(plate, b, .978, 0);
            if (ghost) {
                out.add(new Face(oa, ob, ib, ia, PREVIEW, 160, GHOST, -1));
                continue;
            }
            // One continuous original swirl across the real ellipse; triangles never restart its UVs.
            Uv center = new Uv(.5, .5);
            // The original texture already caps alpha at 115/255. Preserve its full-white source modulation.
            out.add(new Face(plate.center(), ia, ib, plate.center(), 0xFFFFFF, 255, FILL, SHIELD_TEXTURE,
                    center, shieldUv(a, .978), shieldUv(b, .978), center));
            double glint = Math.pow(Math.max(0, Math.cos((a + b) / 2 - 1.05)), 8);
            out.add(new Face(oa, ob, ib, ia, glint > .45 ? CORE : GREEN, 72 + (int)(glint * 118), RIM, WHITE_TEXTURE));
            double half = FoldedMirrorGeometry.THICKNESS / 2;
            out.add(new Face(ellipse(plate, a, 1, -half), ellipse(plate, b, 1, -half),
                    ellipse(plate, b, 1, half), ellipse(plate, a, 1, half), EDGE, 82, SIDE, WHITE_TEXTURE));
        }
        return List.copyOf(out);
    }
    public static List<Face> plate(Plate plate, boolean ghost, float phase, Vector contact, float contactPower) {
        Material material = Material.phase(phase);
        if (!ghost && plate != null && contact != null && inside(plate, contact) && contactPower > 0) {
            var basis = FoldedMirrorGeometry.frame(plate); Vector local = contact.subtract(plate.center());
            material = new Material(phase, (float)(.5 + .43 * local.dot(basis.right()) / FoldedMirrorGeometry.RADIUS_X),
                    (float)(.5 - .43 * local.dot(basis.up()) / FoldedMirrorGeometry.RADIUS_Y), Math.clamp(contactPower, 0, 1));
        }
        Material finalMaterial = material;
        return plate(plate, ghost).stream().map(face -> face.withMaterial(finalMaterial)).toList();
    }
    private static Uv shieldUv(double angle, double scale) {
        // Fit the source image's roughly .075/.925 visible disk to the physical inner rim.
        return new Uv(.5 + Math.cos(angle) * .43 * scale, .5 - Math.sin(angle) * .43 * scale);
    }
    /** The same packet geometry is dotted until a paid FIRE receipt arrives; it never produces impact effects. */
    public static List<Face> path(FoldedMirrorState.Frame frame, Vector camera, Vector look, int age) {
        return path(frame, camera, look, (float)age);
    }
    public static List<Face> path(FoldedMirrorState.Frame frame, Vector camera, Vector look, float age) {
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
            int alpha = (int)(245 * (FoldedMirrorState.FLASH_TICKS - age) / FoldedMirrorState.FLASH_TICKS);
            double blend = Math.min(.24 / length, .25);
            double[] cut = {0, blend, 1 - blend, 1};
            boolean entranceFold = i > 0, exitFold = i + 1 < frame.path().size();
            for (int part = 0; part < 3; part++) {
                Vector a = lerp(s.start(), s.end(), cut[part]), b = lerp(s.start(), s.end(), cut[part + 1]);
                double wa = entranceFold && part == 0 ? .32 : 1, wb = exitFold && part == 2 ? .32 : 1;
                ray(out, a, b, side, camera, look, HALO_RADIUS * wa, HALO_RADIUS * wb,
                        0xFFFFFF, alpha * 128 / 245, BEAM_HALO, part);
                // Classic small rays pair a bright solid inner cylinder with a faint green outer cylinder.
                // These eight-sided volumes stay inside the exact receipt endpoints instead of extending a rounded head.
                tube(out, a, b, camera, look, CORE_RADIUS * wa, CORE_RADIUS * wb,
                        CORE, alpha * 230 / 245, BEAM_CORE, part == 0, part == 2, cut[part] * length, cut[part + 1] * length);
                tube(out, a, b, camera, look, FoldedMirrorGeometry.BEAM_RADIUS * wa,
                        FoldedMirrorGeometry.BEAM_RADIUS * wb, 0x6AF26A, alpha * 50 / 245, BEAM_EDGE, part == 0, part == 2, cut[part] * length, cut[part + 1] * length);
            }
            if (exitFold) {
                Plate plate = FoldedMirrorState.reflectionPlate(frame.plates(), s, frame.path().get(i + 1));
                if (plate != null) out.addAll(refraction(plate, s.end(), camera, look, alpha));
            }
        }
        Material material = Material.phase(age);
        return out.stream().map(face -> face.withMaterial(material)).toList();
    }
    /** A real cross-section in the ray's world-space frame, with finite caps and no billboard-only white center. */
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
    /** Short green-white glints sit on the real refraction point; there is no target hit spark API. */
    private static List<Face> refraction(Plate plate, Vector point, Vector camera, Vector look, int alpha) {
        List<Face> out = new ArrayList<>(8); var basis = FoldedMirrorGeometry.frame(plate);
        for (int i = 0; i < 8; i++) {
            double a = -.9 + i * .225, b = a + .225;
            Vector from = point.add(basis.right().scale(Math.cos(a) * .052)).add(basis.up().scale(Math.sin(a) * .052));
            Vector to = point.add(basis.right().scale(Math.cos(b) * .052)).add(basis.up().scale(Math.sin(b) * .052));
            if (!inside(plate, from) || !inside(plate, to)) continue;
            Vector side = side(from, to, camera, look);
            if (side != null) ray(out, from, to, side, camera, look, .004, .004, CORE, alpha, REFRACTION, WHITE_TEXTURE);
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
    /** Ordinary polygon clipping keeps real thin silhouettes; no billboard replacement, camera hole or x-ray disk. */
    public static List<Face> nearClip(Face face, Vector camera, Vector look) {
        if (face == null || !validCamera(camera, look) || face.vertices().stream().anyMatch(p -> p == null || !p.finite())) return List.of();
        List<Vector> result = new ArrayList<>(6); List<Uv> tex = new ArrayList<>(6);
        Vector previous = face.d(); Uv priorUv = face.uvD(); int index = 0;
        double prior = previous.subtract(camera).dot(look) - NEAR_DEPTH;
        for (Vector point : face.vertices()) {
            Uv uv = face.uvs().get(index++);
            double depth = point.subtract(camera).dot(look) - NEAR_DEPTH;
            if ((depth >= 0) != (prior >= 0)) {
                double t = prior / (prior - depth); result.add(lerp(previous, point, t)); tex.add(priorUv.lerp(uv, t));
            }
            if (depth >= 0) { result.add(point); tex.add(uv); }
            previous = point; prior = depth; priorUv = uv;
        }
        if (result.size() < 3) return List.of();
        if (result.size() == 4) return List.of(new Face(result.get(0), result.get(1), result.get(2), result.get(3), face.color, face.alpha, face.kind, face.texture,
                tex.get(0), tex.get(1), tex.get(2), tex.get(3), face.material));
        List<Face> out = new ArrayList<>(3);
        for (int i = 1; i < result.size() - 1; i++) out.add(new Face(result.get(0), result.get(i), result.get(i + 1), result.get(0), face.color, face.alpha, face.kind, face.texture,
                tex.get(0), tex.get(i), tex.get(i + 1), tex.get(0), face.material));
        return List.copyOf(out);
    }
    /** Budget real paths before decorative surfaces, then blend surfaces before halo, core and contact glints. */
    public static List<Face> ordered(List<Face> faces, Vector camera, Vector look, Budget budget) {
        List<Face> clipped = new ArrayList<>();
        faces.stream().filter(f -> f.alpha() > 0).sorted(Comparator.comparingInt(FoldedMirrorMesh::priority)).forEach(face -> {
            for (Face part : nearClip(face, camera, look)) if (budget.take()) clipped.add(part);
        });
        clipped.sort(Comparator.comparingInt(FoldedMirrorMesh::layer)
                .thenComparing(Comparator.comparingDouble((Face f) -> f.a().add(f.b()).add(f.c()).add(f.d()).scale(.25).subtract(camera).length()).reversed()));
        return List.copyOf(clipped);
    }
    private static int priority(Face face) {
        return switch (face.kind()) { case BEAM_CORE, REFRACTION, PLANNED -> 0; case BEAM_EDGE -> 2; case BEAM_HALO, SIDE -> 3; default -> 1; };
    }
    public static int layer(Face face) {
        return switch (face.kind()) { case SIDE -> 0; case FILL -> 1; case GHOST, PLANNED -> 2; case BEAM_HALO -> 3;
            case BEAM_EDGE -> 4; case BEAM_CORE -> 5; case RIM -> 6; case REFRACTION -> 7; default -> throw new IllegalArgumentException("Unknown mirror layer"); };
    }
    public static boolean additive(Face face) { return face.kind() == BEAM_HALO || face.kind() == RIM || face.kind() == REFRACTION; }
    public static boolean culled(Face face) { return face.kind() == BEAM_CORE || face.kind() == BEAM_EDGE; }
    /** Shared post-clipping cap for all visible owners and texture batches. */
    public static final class Budget {
        private int used;
        public boolean take() { if (used >= MAX_MESH_QUADS) return false; used++; return true; }
        public int used() { return used; }
    }
}
