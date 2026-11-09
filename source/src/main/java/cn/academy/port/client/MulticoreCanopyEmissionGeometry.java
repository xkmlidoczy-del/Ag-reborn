package cn.academy.port.client;

import cn.academy.port.client.FoldedMirrorMesh.Face;
import cn.academy.port.client.FoldedMirrorMesh.Material;
import cn.academy.port.client.FoldedMirrorMesh.Uv;
import cn.academy.port.client.MulticoreCanopyState.Frame;
import cn.academy.port.client.MulticoreCanopyState.Segment;
import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.ArrayList;
import java.util.List;

/** Optional NEW251 emission artwork. The received small ray remains the only physical centerline. */
public final class MulticoreCanopyEmissionGeometry {
    public static final int MAX_QUADS_PER_RAY = 10, MAX_QUADS_PER_CONTEXT = MAX_QUADS_PER_RAY * MulticoreCanopyState.MAX_CORES, SPAWNED_PARTICLES = 0;
    public static final double MAX_HALF_WIDTH = .35, MAX_ANGULAR_HALF_WIDTH = .028;
    public static final float SOURCE_FLASH_TICKS = 2;
    static final int HOT_FILAMENT = 0xFFC164, EMBER = 0xFFA660;
    private static final double NEAR_FADE_END = .90;
    private static final Vector X = new Vector(1, 0, 0), Y = new Vector(0, 1, 0);

    private MulticoreCanopyEmissionGeometry() {}

    /**
     * Only an accepted FIRE receipt and its original pause-aware age belong here. Authentication,
     * cancellation and replay fences remain in MulticoreCanopyState; this pure function never accepts
     * or refreshes a receipt. Repeated calls with the same age produce the same faces.
     *
     * All positions remain world-space doubles. Shoulder sections stay on the exact received segment,
     * and source/contact details stay inside its endpoint planes. Short paths
     * reduce both width and opacity. Nearby decoration fades independently of the ordinary classic ray.
     * The caller retains that ray, clips these raw faces with FoldedMirrorMesh's existing .08m plane,
     * and charges the actual post-clip faces to the existing shared budget.
     */
    public static List<Face> emission(Frame frame, Vector camera, Vector look, float age) {
        if (!MulticoreCanopyState.valid(frame) || frame.phase() != MulticoreCanopyState.FIRE
                || camera == null || !camera.finite() || !MulticoreCanopyState.direction(look)
                || !Float.isFinite(age) || age < 0 || age >= MulticoreCanopyState.FLASH_TICKS)
            return List.of();
        List<Face> out = new ArrayList<>(MAX_QUADS_PER_CONTEXT);
        double life = 1 - age / MulticoreCanopyState.FLASH_TICKS;
        for (Segment ray : frame.path()) {
            // State.valid proves a real coreId match and start.equals(core.center), even after use.
            Vector delta = ray.end().subtract(ray.start());
            double length = delta.length();
            Vector axis = FoldedMirrorGeometry.unit(delta).orElseThrow();
            Vector side = FoldedMirrorGeometry.unit(axis.cross(look)).orElse(null);
            if (side == null) side = FoldedMirrorGeometry.unit(axis.cross(Math.abs(axis.y()) > .9 ? X : Y)).orElseThrow();
            Vector up = axis.cross(side);
            double startDepth = ray.start().subtract(camera).dot(look), endDepth = ray.end().subtract(camera).dot(look);
            if (Math.max(startDepth, endDepth) <= FoldedMirrorMesh.NEAR_DEPTH) continue;
            double shortScale = Math.min(1, length / 1.25);
            double opacity = life * smooth(shortScale) * (ray.status() == MulticoreCanopyState.REFUSED ? .5 : 1);
            double width = Math.min(MAX_HALF_WIDTH, length * .14);
            // Heat travels within this lane on its own receipt clock. Sequential casts use the
            // actual separate FIRE packets; a simultaneous volley is never delayed or interpolated.
            double crest = .5 + .10 * age;
            Vector middle = ray.start().add(delta.scale(crest));
            shoulder(out, ray.start(), middle, side, width * .22, width, 0, crest,
                    opacity, age, camera, look);
            shoulder(out, middle, ray.end(), side, width, width * .22, crest, 1,
                    opacity, age, camera, look);

            double sourceLife = Math.max(0, 1 - age / SOURCE_FLASH_TICKS);
            double sourceSize = .23 * shortScale;
            plane(out, ray.start(), side, up, sourceSize, sourceSize, 0, 0,
                    MulticoreCanopyMesh.GREEN, 126 * sourceLife * sourceLife * smooth(shortScale),
                    MulticoreCanopyMesh.ORB_GLOW, MulticoreCanopyMesh.GLOW_TEXTURE, age, camera, look);
            plane(out, ray.start(), side, up, sourceSize * .58, sourceSize * .10, 0, 0,
                    MulticoreCanopyMesh.CORE, 176 * sourceLife * sourceLife * smooth(shortScale),
                    MulticoreCanopyMesh.RIM, MulticoreCanopyMesh.WHITE_TEXTURE, age, camera, look);

            // Global status, target status, intent and an available core never imply contact or HP loss.
            if (ray.status() == MulticoreCanopyState.DAMAGED) {
                double size = .16 * shortScale;
                plane(out, ray.end(), side, up, size, size * .065, 0, 0,
                        MulticoreCanopyMesh.CORE, 224 * opacity, MulticoreCanopyMesh.HIT_GLINT,
                        MulticoreCanopyMesh.WHITE_TEXTURE, age, camera, look);
                plane(out, ray.end(), side, up, size * .065, size, 0, 0,
                        MulticoreCanopyMesh.CORE, 224 * opacity, MulticoreCanopyMesh.HIT_GLINT,
                        MulticoreCanopyMesh.WHITE_TEXTURE, age, camera, look);
                embers(out, ray.end(), axis, side, up, length, shortScale, opacity, age, camera, look);
            } else if (ray.status() == MulticoreCanopyState.ABSORBED) {
                double split = (.045 + age * .012) * shortScale;
                for (int sign : new int[] {-1, 1})
                    plane(out, ray.end(), side, up, .022 * shortScale, .075 * shortScale,
                            sign * split, 0, MulticoreCanopyMesh.GREEN, 112 * opacity * life,
                            MulticoreCanopyMesh.RIM, MulticoreCanopyMesh.GLOW_TEXTURE, age, camera, look);
            }
        }
        return List.copyOf(out);
    }

    private static void shoulder(List<Face> out, Vector a, Vector b, Vector side, double widthA, double widthB,
                                 double uA, double uB, double opacity, float age, Vector camera, Vector look) {
        double fade = nearFade(distanceToSegment(camera, a, b) - Math.max(widthA, widthB));
        double wa = Math.min(widthA, Math.max(0, a.subtract(camera).dot(look)) * MAX_ANGULAR_HALF_WIDTH);
        double wb = Math.min(widthB, Math.max(0, b.subtract(camera).dot(look)) * MAX_ANGULAR_HALF_WIDTH);
        ribbon(out, a, b, side, wa, wb, uA, uB, MulticoreCanopyMesh.GREEN, 96 * opacity * fade, age,
                MulticoreCanopyMesh.BEAM_HALO, MulticoreCanopyMesh.GLOW_TEXTURE);
        // The halo shader paints its own green RGB. The contact material preserves this warm tint.
        ribbon(out, a, b, side, wa * .29, wb * .29, uA, uB, HOT_FILAMENT, 144 * opacity * fade, age,
                MulticoreCanopyMesh.HIT_GLINT, MulticoreCanopyMesh.WHITE_TEXTURE);
    }

    private static void ribbon(List<Face> out, Vector a, Vector b, Vector side, double widthA, double widthB,
                               double uA, double uB, int color, double alpha, float age, int kind, int texture) {
        Vector wa = side.scale(widthA), wb = side.scale(widthB);
        add(out, new Face(a.add(wa), a.subtract(wa), b.subtract(wb), b.add(wb), color, (int)alpha,
                kind, texture, new Uv(uA, 1), new Uv(uA, 0), new Uv(uB, 0), new Uv(uB, 1), Material.phase(age)));
    }

    /** Two cooling streaks recoil toward the real source from a confirmed damage contact. */
    private static void embers(List<Face> out, Vector endpoint, Vector axis, Vector side, Vector up,
                               double length, double scale, double opacity, float age, Vector camera, Vector look) {
        double retreat = Math.min(length * .40, .28 + .14 * age);
        Vector width = side.scale(.020 * scale);
        Vector origin = endpoint.subtract(axis.scale(retreat * .12));
        for (int sign : new int[] {-1, 1}) {
            Vector tip = endpoint.subtract(axis.scale(retreat)).add(side.scale(sign * (.09 + .035 * age) * scale))
                    .add(up.scale(.06 * age * scale));
            double depth = Math.min(origin.subtract(camera).dot(look), tip.subtract(camera).dot(look));
            double distance = distanceToSegment(camera, origin, tip) - width.length();
            double fade = nearFade(Math.min(depth, distance));
            Vector narrow = width.scale(.15);
            add(out, new Face(origin.add(width), origin.subtract(width), tip.subtract(narrow), tip.add(narrow),
                    EMBER, (int)(204 * opacity * fade), MulticoreCanopyMesh.HIT_GLINT,
                    MulticoreCanopyMesh.WHITE_TEXTURE).withMaterial(Material.phase(age)));
        }
    }

    /** Source and contact faces share the actual endpoint plane; a split is transverse only. */
    private static void plane(List<Face> out, Vector endpoint, Vector side, Vector up, double width, double height,
                              double offsetX, double offsetY, int color, double alpha, int kind, int texture,
                              float age, Vector camera, Vector look) {
        Vector center = endpoint.add(side.scale(offsetX)).add(up.scale(offsetY));
        Vector x = side.scale(width), y = up.scale(height);
        Vector a = center.subtract(x).subtract(y), b = center.subtract(x).add(y);
        Vector c = center.add(x).add(y), d = center.add(x).subtract(y);
        double depth = Math.min(Math.min(a.subtract(camera).dot(look), b.subtract(camera).dot(look)),
                Math.min(c.subtract(camera).dot(look), d.subtract(camera).dot(look)));
        double fade = nearFade(Math.min(depth, center.subtract(camera).length() - Math.hypot(width, height)));
        add(out, new Face(a, b, c, d, color, (int)(alpha * fade), kind, texture).withMaterial(Material.phase(age)));
    }

    private static void add(List<Face> out, Face face) {
        if (face.alpha() > 0 && face.vertices().stream().allMatch(Vector::finite)) out.add(face);
    }

    private static double distanceToSegment(Vector point, Vector a, Vector b) {
        Vector delta = b.subtract(a);
        double length = delta.length();
        if (!(length > 0)) return point.subtract(a).length();
        Vector axis = FoldedMirrorGeometry.unit(delta).orElse(null);
        if (axis == null) return point.subtract(a).length();
        double distance = Math.clamp(point.subtract(a).dot(axis), 0, length);
        return point.subtract(a.add(axis.scale(distance))).length();
    }

    private static double nearFade(double distance) {
        return smooth((distance - FoldedMirrorMesh.NEAR_DEPTH) / (NEAR_FADE_END - FoldedMirrorMesh.NEAR_DEPTH));
    }

    private static double smooth(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
