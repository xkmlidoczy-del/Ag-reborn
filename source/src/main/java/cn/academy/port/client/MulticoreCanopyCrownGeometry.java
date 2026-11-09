package cn.academy.port.client;

import cn.academy.port.client.FoldedMirrorMesh.Face;
import cn.academy.port.client.FoldedMirrorMesh.Material;
import cn.academy.port.client.FoldedMirrorMesh.Uv;
import cn.academy.port.client.MulticoreCanopyState.Core;
import cn.academy.port.client.MulticoreCanopyState.Frame;
import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Optional NEW251 source petals. Receipts establish the pose; the camera only hides or fades decoration. */
public final class MulticoreCanopyCrownGeometry {
    public static final int CURVE_STEPS = 8, MAX_QUADS_PER_CONTEXT = 96, SPAWNED_PARTICLES = 0;
    public static final double OUTER_RADIUS = 1.975, DEPTH = 1.2, MAX_HALF_WIDTH = .39;
    public static final double TANGENTIAL_BEND = .36, TIP_HALF_WIDTH = .025;
    public static final double SOURCE_FADE_START = 2, SOURCE_FADE_END = 5.5, NEAR_SOURCE_OPACITY = .16;
    public static final double NEAR_DEPTH = .20, NEAR_FADE_END = .95, AIM_HALF_ANGLE = .024;
    private static final double FIT_TOLERANCE = 2e-6;
    private static final double[][] OFFSETS = {{-.9, 0}, {-.45, .65}, {.45, .65}, {.9, 0}, {.45, -.65}, {-.45, -.65}};

    /** A right-handed, frozen source pose recovered from the indexed, actually received core centers. */
    public record Basis(Vector center, Vector right, Vector up, Vector forward) {}

    private MulticoreCanopyCrownGeometry() {}

    /**
     * At least three non-collinear indexed centers must prove the original layout. Missing or unavailable
     * cores can never create a sector. An unavailable core's previously received position may still prove
     * the frozen orientation. ownerFeet, camera direction and current player rotation are never pose inputs.
     */
    public static Optional<Basis> basis(Frame frame) {
        if (!MulticoreCanopyState.valid(frame) || frame.cores().size() < 3) return Optional.empty();
        List<Core> cores = frame.cores();
        Core anchor = cores.getFirst();
        double[] a = OFFSETS[anchor.id()];
        for (int j = 1; j < cores.size() - 1; j++) for (int k = j + 1; k < cores.size(); k++) {
            Core second = cores.get(j), third = cores.get(k);
            double x1 = OFFSETS[second.id()][0] - a[0], y1 = OFFSETS[second.id()][1] - a[1];
            double x2 = OFFSETS[third.id()][0] - a[0], y2 = OFFSETS[third.id()][1] - a[1];
            double determinant = x1 * y2 - x2 * y1;
            if (Math.abs(determinant) < 1e-9) continue;
            Vector firstDelta = second.center().subtract(anchor.center());
            Vector secondDelta = third.center().subtract(anchor.center());
            Vector right = firstDelta.scale(y2).subtract(secondDelta.scale(y1)).scale(1 / determinant);
            Vector up = secondDelta.scale(x1).subtract(firstDelta.scale(x2)).scale(1 / determinant);
            if (!right.finite() || !up.finite() || Math.abs(right.length() - 1) > FIT_TOLERANCE
                    || Math.abs(up.length() - 1) > FIT_TOLERANCE || Math.abs(right.dot(up)) > FIT_TOLERANCE)
                return Optional.empty();
            right = FoldedMirrorGeometry.unit(right).orElseThrow();
            up = FoldedMirrorGeometry.unit(up.subtract(right.scale(up.dot(right)))).orElseThrow();
            Vector forward = right.cross(up);
            Vector center = anchor.center().subtract(right.scale(a[0])).subtract(up.scale(a[1]));
            if (!center.finite()) return Optional.empty();
            for (Core core : cores) {
                double[] offset = OFFSETS[core.id()];
                Vector expected = center.add(right.scale(offset[0])).add(up.scale(offset[1]));
                if (expected.subtract(core.center()).length() > FIT_TOLERANCE) return Optional.empty();
            }
            return Optional.of(new Basis(center, right, up, forward));
        }
        return Optional.empty();
    }

    /**
     * Produces world-space doubles; the caller subtracts the camera in double precision before float upload.
     * sourceAgeTicks is the existing pause-aware marker age plus its paused-aware render fraction, bounded
     * to the 240-tick source life. remainingTicks and quietTicks are the CURRENT accepted source lease,
     * not the latest packet's original lease. No receipt refresh, warning, pause, or emission restarts age.
     *
     * DEPLOY draws only received available cores; ARMED holds them. WARNING advances received warningTicks
     * by the nonpaused quietTicks elapsed since receipt, bounded at zero. Its petals brighten and contract
     * toward their own actual cores, stopping at 55% of their original offsets. It never restarts a
     * twelve-tick timer or predicts a firing core. HELD/FIRE retain only still-available
     * sectors. ENDED, expired/stale leases, malformed input and unproven poses produce no crown or tail.
     * Existing authenticated FIRE geometry and original orb artwork remain the caller's responsibility.
     *
     * Every returned corner is beyond .20m depth. The existing .08m near clip therefore cannot multiply
     * these faces. At most 96 quads per context, no particles, no caps, disks, spokes, or closed perimeter.
     */
    public static List<Face> source(Frame frame, Vector camera, Vector look, float sourceAgeTicks,
                                    int remainingTicks, int quietTicks) {
        if (!MulticoreCanopyState.valid(frame) || frame.phase() == MulticoreCanopyState.ENDED
                || camera == null || !camera.finite() || !MulticoreCanopyState.direction(look)
                || !Float.isFinite(sourceAgeTicks) || sourceAgeTicks < 0 || sourceAgeTicks >= MulticoreCanopyState.LIFE_TICKS
                || remainingTicks <= 0 || remainingTicks > frame.remainingTicks()
                || quietTicks < 0 || quietTicks >= MulticoreCanopyState.QUIET_TICKS) return List.of();
        Basis pose = basis(frame).orElse(null);
        if (pose == null || pose.center().subtract(camera).length() > 128) return List.of();
        Vector viewRight = FoldedMirrorGeometry.unit(look.cross(Math.abs(look.y()) > .9
                ? new Vector(1, 0, 0) : new Vector(0, 1, 0))).orElseThrow();
        Vector viewUp = viewRight.cross(look);
        float materialAge = sourceAgeTicks % 64;
        int warningRemaining = Math.max(0, frame.warningTicks() - quietTicks);
        double warning = frame.phase() == MulticoreCanopyState.WARNING ? (12 - warningRemaining) / 12.0 : 0;
        double contraction = 1 - .45 * warning;
        double breath = .96 + .04 * Math.sin(sourceAgeTicks * Math.PI / 16);
        double distanceFade = Math.clamp((pose.center().subtract(camera).length() - SOURCE_FADE_START)
                / (SOURCE_FADE_END - SOURCE_FADE_START), 0, 1);
        distanceFade = distanceFade * distanceFade * (3 - 2 * distanceFade);
        // Large nearby petals remain translucent around the owner's view. Their world curve is unchanged.
        double sourceOpacity = NEAR_SOURCE_OPACITY + (1 - NEAR_SOURCE_OPACITY) * distanceFade;
        double power = (frame.phase() == MulticoreCanopyState.DEPLOY ? .80 : 1) * breath * sourceOpacity;
        int bodyAlpha = (int)((116 + 48 * warning) * power);
        int spineAlpha = (int)((112 + 36 * warning) * power);
        List<Face> out = new ArrayList<>(MAX_QUADS_PER_CONTEXT);
        for (Core core : frame.cores()) {
            if (!core.available()) continue;
            double[] offset = OFFSETS[core.id()];
            double rootRadius = Math.hypot(offset[0], offset[1]);
            Vector radial = pose.right().scale(offset[0] / rootRadius).add(pose.up().scale(offset[1] / rootRadius));
            Vector tangent = pose.forward().cross(radial);
            for (int step = 0; step < CURVE_STEPS; step++) {
                double from = step / (double)CURVE_STEPS, to = (step + 1) / (double)CURVE_STEPS;
                // Wide green energy and a narrower soft ivory ridge share the same curved source petal.
                add(out, core, pose, radial, tangent, rootRadius, from, to, 1, contraction, bodyAlpha,
                        MulticoreCanopyMesh.GREEN, MulticoreCanopyMesh.ORB_GLOW, MulticoreCanopyMesh.GLOW_TEXTURE,
                        materialAge, camera, look, viewRight, viewUp);
                add(out, core, pose, radial, tangent, rootRadius, from, to, .13, contraction, spineAlpha,
                        MulticoreCanopyMesh.CORE, MulticoreCanopyMesh.RIM, MulticoreCanopyMesh.GLOW_TEXTURE,
                        materialAge, camera, look, viewRight, viewUp);
            }
        }
        return List.copyOf(out);
    }

    private static Vector edge(Core core, Basis pose, Vector radial, Vector tangent, double rootRadius,
                               double t, double side, double widthScale, double contraction) {
        double angle = t * Math.PI / 2;
        double radius = (OUTER_RADIUS - rootRadius) * Math.sin(angle);
        double width = (.13 * (1 - t) + TIP_HALF_WIDTH * t + .31 * Math.sin(t * Math.PI)) * widthScale;
        double bend = TANGENTIAL_BEND * Math.sin(t * Math.PI);
        // Tangential bow reads as a curved energy sweep from the front as well as the 1.2m-deep side.
        // The bend starts and ends at zero; the root remains on the core and the tip tapers to .05m.
        // Only the received WARNING countdown contracts decorative offsets toward each exact source.
        return core.center().add(radial.scale(radius * contraction)).add(tangent.scale((bend + side * width) * contraction))
                .subtract(pose.forward().scale(DEPTH * (1 - Math.cos(angle)) * contraction));
    }

    private static void add(List<Face> out, Core core, Basis pose, Vector radial, Vector tangent,
                            double rootRadius, double from, double to, double widthScale, double contraction, int alpha,
                            int color, int kind, int texture, float phase,
                            Vector camera, Vector look, Vector viewRight, Vector viewUp) {
        Vector a = edge(core, pose, radial, tangent, rootRadius, from, -1, widthScale, contraction);
        Vector b = edge(core, pose, radial, tangent, rootRadius, from, 1, widthScale, contraction);
        Vector c = edge(core, pose, radial, tangent, rootRadius, to, 1, widthScale, contraction);
        Vector d = edge(core, pose, radial, tangent, rootRadius, to, -1, widthScale, contraction);
        double minDepth = Double.POSITIVE_INFINITY, minDistance = Double.POSITIVE_INFINITY;
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (Vector point : List.of(a, b, c, d)) {
            if (!point.finite()) return;
            Vector relative = point.subtract(camera);
            double depth = relative.dot(look);
            if (depth <= NEAR_DEPTH) return;
            minDepth = Math.min(minDepth, depth);
            minDistance = Math.min(minDistance, relative.length());
            double x = relative.dot(viewRight) / depth, y = relative.dot(viewUp) / depth;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        // Conservatively omit a whole decorative segment if its projected bounds could cover the aim.
        // This changes visibility only; it never bends or rotates the frozen source toward the camera.
        if (minX <= AIM_HALF_ANGLE && maxX >= -AIM_HALF_ANGLE
                && minY <= AIM_HALF_ANGLE && maxY >= -AIM_HALF_ANGLE) return;
        double fade = Math.clamp((Math.min(minDepth, minDistance) - NEAR_DEPTH)
                / (NEAR_FADE_END - NEAR_DEPTH), 0, 1);
        fade = fade * fade * (3 - 2 * fade);
        int visibleAlpha = (int)(alpha * fade);
        if (visibleAlpha < 4) return;
        // Both layers sample the original glow's transparent transverse edges. The ridge samples its
        // central longitudinal band, keeping continuity without turning into an opaque white plank.
        double uFrom = kind == MulticoreCanopyMesh.RIM ? .18 + .64 * from : from;
        double uTo = kind == MulticoreCanopyMesh.RIM ? .18 + .64 * to : to;
        out.add(new Face(a, b, c, d, color, visibleAlpha, kind, texture,
                new Uv(uFrom, 0), new Uv(uFrom, 1), new Uv(uTo, 1), new Uv(uTo, 0), Material.phase(phase)));
    }
}
