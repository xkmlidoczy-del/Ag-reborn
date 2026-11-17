package cn.academy.port.client.canon;

import cn.academy.port.VisualTier;
import cn.academy.port.VisualTierConfig;
import cn.academy.port.canon.CanonExpansionCombatA;
import cn.academy.port.client.canon.CanonFx.Canvas;
import cn.academy.port.macro.HitVolumes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Modern procedural 3D VFX primitives for Academy Generation Reborn (a3.65).
 *
 * <p>Combines Toaru scientific calculation circuits, per-tick heightmap-snapped terrain shockwave crowns,
 * analytical Fresnel grazing shells, oriented OBB hexagonal forcefield lattices, recursive midpoint-displacement
 * fractal lightning, and multi-helix Mach shock beams. Every outer silhouette is locked 1:1 to the authoritative
 * server hit volume ({@link HitVolumes}) and draws through {@link Canvas} so Vanilla and Iris shaderpacks render
 * identically without custom G-buffer pollution.
 */
final class AgModernVfx {
    private static final float[] WHITE = {0.97f, 0.99f, 1.0f};
    private static final float[] STEEL = {0.06f, 0.082f, 0.14f};

    private record GridCol(int hx, int hz, int refBlockY) {}
    private static final Map<GridCol, Double> GROUND_CACHE = new HashMap<>(512);
    private static ClientLevel cachedLevel;
    private static long cachedTick = Long.MIN_VALUE;

    private AgModernVfx() {}

    static void clearCache() {
        GROUND_CACHE.clear();
        cachedLevel = null;
        cachedTick = Long.MIN_VALUE;
    }

    /** Quartic ease-out: reaches 99% of exact server hit radius in ~3 ticks so telegraphs are never misleading. */
    static double snapEase(double t) {
        double s = Math.clamp(t, 0.0, 1.0);
        double inv = 1.0 - s;
        return 1.0 - inv * inv * inv * inv;
    }

    /** Per-tick cached terrain surface Y near {@code refY}, falling back to {@code refY} in mid-air or unloaded chunks. */
    static double groundY(double x, double refY, double z) {
        var mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return refY;
        long tick = level.getGameTime();
        if (level != cachedLevel || tick != cachedTick || GROUND_CACHE.size() >= 1024) {
            GROUND_CACHE.clear();
            cachedLevel = level;
            cachedTick = tick;
        }
        int hx = (int) Math.floor(x * 2.0);
        int hz = (int) Math.floor(z * 2.0);
        int ry = (int) Math.floor(refY);
        var key = new GridCol(hx, hz, ry);
        Double hit = GROUND_CACHE.get(key);
        if (hit != null) return hit;
        double resolved = resolveGroundY(level, x, refY, z, ry);
        GROUND_CACHE.put(key, resolved);
        return resolved;
    }

    private static double resolveGroundY(ClientLevel level, double x, double refY, double z, int ry) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return refY;
        var mutable = new BlockPos.MutableBlockPos();
        for (int dy = 2; dy >= -5; dy--) {
            mutable.set(bx, ry + dy, bz);
            var state = level.getBlockState(mutable);
            if (state.isAir()) continue;
            var shape = state.getCollisionShape(level, mutable);
            if (shape.isEmpty()) continue;
            double top = mutable.getY() + shape.max(Direction.Axis.Y);
            mutable.set(bx, ry + dy + 1, bz);
            if (level.getBlockState(mutable).getCollisionShape(level, mutable).isEmpty() && Math.abs(top - refY) <= 4.5) {
                return top;
            }
        }
        return refY;
    }

    /** Category palette accent pair: [0] primary neon/pastel, [1] secondary harmonic tint. */
    static float[][] categoryPalette(String category) {
        return switch (category == null ? "" : category) {
            case "electromaster" -> new float[][]{{0.36f, 0.82f, 1.0f}, {0.68f, 0.94f, 1.0f}};
            case "meltdowner" -> new float[][]{{0.28f, 1.0f, 0.34f}, {0.78f, 1.0f, 0.62f}};
            case "vecmanip" -> new float[][]{{0.68f, 0.78f, 1.0f}, {1.0f, 0.44f, 0.78f}};
            case "teleporter" -> new float[][]{{0.66f, 0.46f, 1.0f}, {0.42f, 0.88f, 1.0f}};
            case "mental_out" -> new float[][]{{1.0f, 0.56f, 0.84f}, {0.82f, 0.64f, 1.0f}};
            case "aero_hand" -> new float[][]{{0.54f, 1.0f, 0.88f}, {0.90f, 1.0f, 0.98f}};
            case "float_dial" -> new float[][]{{0.68f, 0.96f, 0.78f}, {0.82f, 0.76f, 1.0f}};
            case "paper_psychokinesis" -> new float[][]{{1.0f, 0.91f, 0.68f}, {1.0f, 0.97f, 0.88f}};
            case "liquid_shadow" -> new float[][]{{0.36f, 0.50f, 0.82f}, {0.72f, 0.82f, 1.0f}};
            case "hydro_hand" -> new float[][]{{0.32f, 0.80f, 1.0f}, {0.86f, 0.97f, 1.0f}};
            case "dark_matter" -> new float[][]{{0.94f, 0.88f, 1.0f}, {0.76f, 0.86f, 1.0f}};
            case "nitrogen_armor" -> new float[][]{{0.64f, 0.88f, 1.0f}, {0.92f, 0.98f, 1.0f}};
            default -> new float[][]{{0.62f, 0.90f, 1.0f}, {0.92f, 0.98f, 1.0f}};
        };
    }

    /**
     * Procedural Toaru scientific calculation circuit in the plane perpendicular to {@code normal}.
     * Outer perimeter locks strictly to {@code 1.00 * radius}.
     */
    static void calculationArray(Canvas c, Vec3 centre, Vec3 normal, double radius, String category,
                                 double age, double alpha, int detail) {
        if (radius <= 0.15 || alpha <= 0.008 || VisualTierConfig.tier() == VisualTier.OFF) return;
        HitVolumes.Basis basis = planeBasis(normal);
        Vec3 ax = basis.right(), ay = basis.up();
        float[][] pal = categoryPalette(category);
        float[] main = pal[0], sub = pal[1];
        double deploy = snapEase(age / 3.0);
        double R = radius * deploy;
        int ringSegs = Math.clamp(detail, 18, 48);
        double stroke = Math.clamp(R * 0.022, 0.045, 0.24);
        double rot = age * 0.045;

        // 1. Outer authoritative boundary double-vernier ring (1.00 R and 0.93 R).
        planarRing(c, centre, ax, ay, R, rot, ringSegs, stroke, main, alpha * 0.88, true);
        if (VisualTierConfig.tier() != VisualTier.LOW && R >= 1.2) {
            planarRing(c, centre, ax, ay, R * 0.92, -rot * 0.7, Math.max(16, ringSegs * 3 / 4), stroke * 0.65, sub, alpha * 0.56, false);
        }

        // 2. Calibrated vernier degree ticks around the perimeter.
        int ticks = VisualTierConfig.tier() == VisualTier.CINEMATIC ? 24 : 12;
        if (R >= 1.0) {
            for (int i = 0; i < ticks; i++) {
                double a = rot * 0.6 + i * (Math.TAU / ticks);
                boolean major = (i % 6) == 0;
                double inner = R * (major ? 0.82 : 0.88);
                Vec3 dir = ax.scale(Math.cos(a)).add(ay.scale(Math.sin(a)));
                strokeLine(c, centre.add(dir.scale(inner)), centre.add(dir.scale(R)),
                        stroke * (major ? 0.95 : 0.55), major ? WHITE : main, alpha * (major ? 0.85 : 0.52));
            }
        }

        if (VisualTierConfig.tier() == VisualTier.LOW) return;

        // 3. Category-specific inscribed scientific geometry (counter-rotating inner harmonic ring + motif).
        double innerR = R * 0.64;
        planarRing(c, centre, ax, ay, innerR, -rot * 1.35, Math.max(12, ringSegs / 2), stroke * 0.62, sub, alpha * 0.62, false);

        switch (category == null ? "" : category) {
            case "electromaster" -> {
                inscribedPolygon(c, centre, ax, ay, innerR, rot * 0.9, 3, stroke * 0.72, main, alpha * 0.70);
                inscribedPolygon(c, centre, ax, ay, innerR, rot * 0.9 + Math.PI / 3.0, 3, stroke * 0.72, sub, alpha * 0.65);
            }
            case "meltdowner" -> {
                inscribedPolygon(c, centre, ax, ay, innerR * 1.08, -rot * 0.8, 8, stroke * 0.75, main, alpha * 0.72);
                for (int i = 0; i < 4; i++) {
                    double a = rot * 1.2 + i * Math.PI * 0.5;
                    Vec3 d = ax.scale(Math.cos(a)).add(ay.scale(Math.sin(a)));
                    strokeLine(c, centre.add(d.scale(R * 0.24)), centre.add(d.scale(innerR)), stroke * 0.85, WHITE, alpha * 0.78);
                }
            }
            case "vecmanip" -> {
                inscribedPolygon(c, centre, ax, ay, innerR * 1.06, rot * 1.1, 3, stroke * 0.82, main, alpha * 0.76);
                for (int i = 0; i < 3; i++) {
                    double a = rot * 1.1 + i * (Math.TAU / 3.0);
                    Vec3 tip = centre.add(ax.scale(Math.cos(a) * R * 0.84)).add(ay.scale(Math.sin(a) * R * 0.84));
                    Vec3 tang = ax.scale(-Math.sin(a)).add(ay.scale(Math.cos(a)));
                    Vec3 rad = ax.scale(Math.cos(a)).add(ay.scale(Math.sin(a)));
                    strokeLine(c, tip.subtract(rad.scale(R * 0.14)).add(tang.scale(R * 0.08)), tip, stroke * 0.8, WHITE, alpha * 0.85);
                    strokeLine(c, tip.subtract(rad.scale(R * 0.14)).subtract(tang.scale(R * 0.08)), tip, stroke * 0.8, WHITE, alpha * 0.85);
                }
            }
            case "teleporter" -> {
                inscribedPolygon(c, centre, ax, ay, innerR * 1.05, rot * 0.75, 4, stroke * 0.75, main, alpha * 0.74);
                inscribedPolygon(c, centre, ax, ay, innerR * 1.05, -rot * 0.75 + Math.PI * 0.25, 4, stroke * 0.75, sub, alpha * 0.74);
            }
            case "mental_out" -> {
                starPolygon(c, centre, ax, ay, innerR * 1.06, rot * 0.65, 5, 2, stroke * 0.68, main, alpha * 0.68);
                c.neuralNode(centre, Math.clamp(R * 0.14, 0.24, 1.4), main, (float) (alpha * 0.82));
            }
            case "aero_hand", "hydro_hand" -> {
                for (int arm = 0; arm < 4; arm++) {
                    var spiral = new ArrayList<Vec3>(8);
                    double base = -rot * 1.6 + arm * Math.PI * 0.5;
                    for (int s = 0; s <= 7; s++) {
                        double u = s / 7.0;
                        double ang = base + u * 1.85;
                        double sr = R * (0.16 + 0.72 * u);
                        spiral.add(centre.add(ax.scale(Math.cos(ang) * sr)).add(ay.scale(Math.sin(ang) * sr)));
                    }
                    c.ribbon("streak", true, spiral, u -> stroke * (0.45 + 0.75 * u), u -> alpha * 0.66 * Math.sin(Math.PI * u), main, 1, 0);
                }
            }
            case "dark_matter" -> {
                starPolygon(c, centre, ax, ay, innerR * 1.12, rot * 0.5, 6, 2, stroke * 0.74, WHITE, alpha * 0.74);
                inscribedPolygon(c, centre, ax, ay, R * 0.32, -rot, 4, stroke * 0.70, main, alpha * 0.72);
            }
            case "nitrogen_armor" -> {
                inscribedPolygon(c, centre, ax, ay, innerR, rot * 0.4, 6, stroke * 0.72, sub, alpha * 0.74);
                for (int i = 0; i < 6; i++) {
                    double a = rot * 0.4 + i * (Math.PI / 3.0);
                    Vec3 d = ax.scale(Math.cos(a)).add(ay.scale(Math.sin(a)));
                    strokeLine(c, centre.add(d.scale(R * 0.2)), centre.add(d.scale(innerR)), stroke * 0.62, WHITE, alpha * 0.68);
                }
            }
            default -> starPolygon(c, centre, ax, ay, innerR * 1.04, rot * 0.7, 8, 3, stroke * 0.66, main, alpha * 0.66);
        }
    }

    /**
     * Terrain-conforming ground perimeter ring + optional outward-leaning cantilevered shockwave crown
     * locked strictly to {@code 1.00 * radius}.
     */
    static void terrainShockCrown(Canvas c, Vec3 centre, double radius, float[] rgb, double age, double alpha,
                                  int segments, boolean crown) {
        if (radius <= 0.2 || alpha <= 0.008 || VisualTierConfig.tier() == VisualTier.OFF) return;
        int n = Math.clamp(segments, 16, 56);
        var pts = new ArrayList<Vec3>(n + 1);
        for (int i = 0; i <= n; i++) {
            double a = (i % n) * (Math.TAU / n);
            double x = centre.x + Math.cos(a) * radius;
            double z = centre.z + Math.sin(a) * radius;
            double y = groundY(x, centre.y, z) + 0.075;
            pts.add(new Vec3(x, y, z));
        }
        double width = Math.clamp(radius * 0.024, 0.08, 0.34);
        c.ribbon("streak_ink", false, pts, u -> width * 1.45, u -> alpha * 0.66, STEEL, 1, 0);
        c.ribbon("streak", true, pts, u -> width * 0.82, u -> alpha * 0.92, rgb, 2, -age * 0.06);
        c.ribbon("streak", true, pts, u -> width * 0.32, u -> alpha * 0.85, WHITE, 2, -age * 0.06);

        if (!crown || VisualTierConfig.tier() == VisualTier.LOW) return;
        int fins = Math.min(20, Math.max(8, n / 2));
        double crownH = Math.clamp(radius * 0.16, 0.45, 2.8) * (0.55 + 0.45 * Math.sin(Math.min(1.0, age / 10.0) * Math.PI));
        if (crownH <= 0.08) return;
        for (int i = 0; i < fins; i++) {
            double a0 = i * (Math.TAU / fins) + age * 0.02;
            double a1 = a0 + (Math.TAU / fins) * 0.68;
            Vec3 d0 = new Vec3(Math.cos(a0), 0, Math.sin(a0));
            Vec3 d1 = new Vec3(Math.cos(a1), 0, Math.sin(a1));
            double x0 = centre.x + d0.x * radius, z0 = centre.z + d0.z * radius;
            double x1 = centre.x + d1.x * radius, z1 = centre.z + d1.z * radius;
            Vec3 b0 = new Vec3(x0, groundY(x0, centre.y, z0) + 0.06, z0);
            Vec3 b1 = new Vec3(x1, groundY(x1, centre.y, z1) + 0.06, z1);
            // Lean slightly INWARD so the outermost footprint never exceeds 1.00 * actualRadius!
            Vec3 t0 = b0.subtract(d0.scale(crownH * 0.18)).add(0, crownH, 0);
            Vec3 t1 = b1.subtract(d1.scale(crownH * 0.18)).add(0, crownH * 0.82, 0);
            c.quad("cinema/filament", true, b0, b1, t1, t0, 0, 1, 1, 0, rgb[0], rgb[1], rgb[2], (float) (alpha * 0.52));
        }
    }

    /**
     * Analytical Fresnel grazing dome/ellipsoid: nearly invisible (<2%) straight-on so players inside see clearly,
     * with a crisp glowing silhouette rim at {@code 1.00 * rx / ry} in both Vanilla and Iris shaderpacks.
     */
    static void fresnelDome(Canvas c, Vec3 centre, double rx, double ry, float[] rgb, double age, double alpha,
                            int latSteps, int lonSteps) {
        if (rx <= 0.15 || ry <= 0.15 || alpha <= 0.008 || VisualTierConfig.tier() == VisualTier.OFF) return;
        int lat = Math.clamp(latSteps, 5, 12);
        int lon = Math.clamp(lonSteps, 10, 24);
        double spin = age * 0.018;
        for (int i = 0; i < lat; i++) {
            double th0 = Math.PI * i / lat;
            double th1 = Math.PI * (i + 1) / lat;
            for (int k = 0; k < lon; k++) {
                double ph0 = spin + Math.TAU * k / lon;
                double ph1 = spin + Math.TAU * (k + 1) / lon;
                Vec3 n00 = sph(th0, ph0), n10 = sph(th1, ph0), n11 = sph(th1, ph1), n01 = sph(th0, ph1);
                Vec3 nm = n00.add(n10).add(n11).add(n01).normalize();
                Vec3 mid = centre.add(nm.x * rx, nm.y * ry, nm.z * rx);
                Vec3 toCam = c.cam.subtract(mid);
                double dist = toCam.length();
                if (dist < 0.35) continue;
                Vec3 surfNormal = new Vec3(nm.x / rx, nm.y / ry, nm.z / rx).normalize();
                double ndotv = surfNormal.dot(toCam.scale(1.0 / dist));
                // Front-facing hemisphere relative to viewer avoids double-stacking back + front rims.
                if (ndotv <= 0.0) continue;
                double fresnel = Math.pow(1.0 - Math.min(1.0, ndotv), 3.1);
                double band = 0.82 + 0.18 * Math.sin(th0 * 6.0 - age * 0.18 + k * 0.5);
                float quadAlpha = (float) (alpha * (0.016 + 0.92 * fresnel) * band);
                if (quadAlpha <= 0.004f) continue;
                Vec3 p00 = centre.add(n00.x * rx, n00.y * ry, n00.z * rx);
                Vec3 p10 = centre.add(n10.x * rx, n10.y * ry, n10.z * rx);
                Vec3 p11 = centre.add(n11.x * rx, n11.y * ry, n11.z * rx);
                Vec3 p01 = centre.add(n01.x * rx, n01.y * ry, n01.z * rx);
                c.quad("glow", true, p00, p10, p11, p01, 0, 0, 1, 1, rgb[0], rgb[1], rgb[2], quadAlpha);
            }
        }
        // Equatorial + twin rotating meridian geodesic wireframes for unmistakable 3D spatial readability.
        double wireW = Math.clamp(rx * 0.016, 0.04, 0.18);
        planarRing(c, centre, new Vec3(1, 0, 0), new Vec3(0, 0, 1), rx, spin, lon, wireW, rgb, alpha * 0.62, false);
        if (VisualTierConfig.tier() != VisualTier.LOW) {
            Vec3 m1 = new Vec3(Math.cos(spin * 1.4), 0, Math.sin(spin * 1.4));
            ellipseRing(c, centre, m1, new Vec3(0, 1, 0), rx, ry, lon, wireW * 0.78, WHITE, alpha * 0.42);
        }
    }

    /**
     * Oriented 3D hexagonal forcefield wall matching {@link HitVolumes#frontalBarrierSpec} and {@link HitVolumes#obbBasis} 1:1.
     */
    static void obbForcefield(Canvas c, Vec3 centre, Vec3 aimAxis, float fallbackYaw, String skill,
                              float[] rgb, double age, double alpha) {
        if (alpha <= 0.008 || VisualTierConfig.tier() == VisualTier.OFF) return;
        double halfW = CanonExpansionCombatA.barrierHalfWidth(skill);
        var spec = HitVolumes.frontalBarrierSpec(skill, halfW);
        var basis = HitVolumes.obbBasis(aimAxis, fallbackYaw);
        Vec3[] corners = HitVolumes.obbCorners(centre, basis, spec);
        double edgeW = Math.clamp(halfW * 0.025, 0.038, 0.09);

        // 1. 12 oriented OBB edges + high-intensity corner brackets.
        for (int i = 0; i < 8; i++) {
            for (int bit : new int[]{1, 2, 4}) {
                if ((i & bit) == 0) {
                    Vec3 a = corners[i], b = corners[i | bit];
                    strokeLine(c, a, b, edgeW, rgb, alpha * 0.78);
                    strokeLine(c, a, a.lerp(b, 0.22), edgeW * 1.35, WHITE, alpha * 0.92);
                    strokeLine(c, b, b.lerp(a, 0.22), edgeW * 1.35, WHITE, alpha * 0.92);
                }
            }
        }

        // 2. Front hexagonal honeycomb forcefield face + scanning energy wave.
        Vec3 frontCentre = centre.add(basis.forward().scale(spec.halfDepth()));
        c.plane("glow", true, frontCentre, basis.right().scale(spec.halfWidth()), basis.up().scale(spec.halfHeight()),
                rgb, (float) (alpha * 0.20));

        int cols = Math.clamp((int) Math.round(spec.halfWidth() * 2.2), 3, 7);
        int rows = Math.clamp((int) Math.round(spec.halfHeight() * 2.0), 2, 5);
        double hexR = Math.min(spec.halfWidth() / (cols + 0.6), spec.halfHeight() / (rows + 0.6)) * 0.92;
        double scanY = Math.sin(age * 0.16) * spec.halfHeight() * 0.85;
        for (int r = 0; r < rows; r++) {
            for (int col = 0; col < cols; col++) {
                double u = (col + 0.5 + (r & 1) * 0.5 - cols * 0.5) / (cols * 0.52) * spec.halfWidth() * 0.82;
                double v = (r + 0.5 - rows * 0.5) / (rows * 0.52) * spec.halfHeight() * 0.80;
                if (Math.abs(u) + hexR > spec.halfWidth() || Math.abs(v) + hexR > spec.halfHeight()) continue;
                double pulse = 0.35 + 0.65 * Math.exp(-Math.pow((v - scanY) / Math.max(0.25, hexR * 1.4), 2.0));
                Vec3 cellPos = frontCentre.add(basis.right().scale(u)).add(basis.up().scale(v));
                inscribedPolygon(c, cellPos, basis.right(), basis.up(), hexR, 0.0, 6, edgeW * 0.55,
                        pulse > 0.72 ? WHITE : rgb, alpha * 0.58 * pulse);
            }
        }
    }

    /**
     * Recursive midpoint-displacement jagged lightning arc with 2-tick anime keyframe hold,
     * dark-steel contrast backing, colored plasma sheath, white core, and forked secondary branches.
     */
    static void fractalArc(Canvas c, Vec3 start, Vec3 end, int subdivisions, long seed, double jitterScale,
                           double width, float[] rgb, double alpha, boolean branches) {
        double len = start.distanceTo(end);
        if (len < 0.08 || alpha <= 0.008 || VisualTierConfig.tier() == VisualTier.OFF) return;
        int depth = Math.clamp(subdivisions, 2, 5);
        int count = 1 << depth;
        Vec3[] pts = new Vec3[count + 1];
        pts[0] = start;
        pts[count] = end;
        HitVolumes.Basis basis = HitVolumes.obbBasis(end.subtract(start), 0f);
        Random rng = new Random(seed * 6364136223846793005L + 1442695040888963407L);
        subdivide(pts, 0, count, jitterScale * Math.min(len * 0.22, 2.8), basis.right(), basis.up(), rng);

        List<Vec3> mainPath = List.of(pts);
        c.ribbon("streak_ink", false, mainPath, u -> width * (1.55 - 0.35 * u), u -> alpha * 0.72, STEEL, 1, 0);
        c.ribbon("cinema/thunder_channel", true, mainPath, u -> width * (1.15 - 0.25 * u), u -> alpha * 0.86, rgb, Math.max(1, len / 6.0), 0);
        c.ribbon("streak", true, mainPath, u -> width * 0.38, u -> alpha * 0.96, WHITE, 1, 0);

        if (!branches || VisualTierConfig.tier() == VisualTier.LOW || count < 8) return;
        int forks = VisualTierConfig.tier() == VisualTier.CINEMATIC ? 3 : 2;
        Vec3 dir = basis.forward();
        for (int f = 0; f < forks; f++) {
            int idx = Math.clamp(2 + rng.nextInt(count - 3), 1, count - 2);
            Vec3 root = pts[idx];
            double branchLen = len * (0.22 + 0.18 * rng.nextDouble());
            double ang = rng.nextDouble() * Math.TAU;
            Vec3 spread = basis.right().scale(Math.cos(ang)).add(basis.up().scale(Math.sin(ang)));
            Vec3 tip = root.add(dir.scale(branchLen * 0.72)).add(spread.scale(branchLen * 0.48));
            Vec3[] sub = new Vec3[5];
            sub[0] = root;
            sub[4] = tip;
            subdivide(sub, 0, 4, jitterScale * 0.45 * Math.min(branchLen, 1.8), basis.right(), basis.up(), rng);
            List<Vec3> forkPath = List.of(sub);
            c.ribbon("cinema/thunder_channel", true, forkPath, u -> width * 0.62 * (1.0 - 0.7 * u), u -> alpha * 0.68 * (1.0 - 0.5 * u), rgb, 1, 0);
            c.ribbon("streak", true, forkPath, u -> width * 0.22 * (1.0 - 0.7 * u), u -> alpha * 0.80 * (1.0 - 0.5 * u), WHITE, 1, 0);
        }
    }

    private static void subdivide(Vec3[] pts, int i0, int i1, double amp, Vec3 right, Vec3 up, Random rng) {
        int mid = (i0 + i1) >>> 1;
        if (mid == i0) return;
        double rx = (rng.nextDouble() - 0.5) * 2.0 * amp;
        double ry = (rng.nextDouble() - 0.5) * 2.0 * amp;
        pts[mid] = pts[i0].lerp(pts[i1], 0.5).add(right.scale(rx)).add(up.scale(ry));
        subdivide(pts, i0, mid, amp * 0.54, right, up, rng);
        subdivide(pts, mid, i1, amp * 0.54, right, up, rng);
    }

    /**
     * Multi-helix plasma ribbons + marching Mach shock rings + muzzle calculation array for grand beams.
     * Outer Mach shock rings and helix envelope are strictly bounded by {@code 1.00 * hitRadius}.
     */
    static void grandBeamHelices(Canvas c, Vec3 from, Vec3 to, double hitRadius, String category,
                                 float[] rgb, double age, double alpha, int detail) {
        Vec3 span = to.subtract(from);
        double len = span.length();
        if (len < 0.4 || hitRadius <= 0.08 || alpha <= 0.008 || VisualTierConfig.tier() == VisualTier.OFF) return;
        HitVolumes.Basis basis = HitVolumes.obbBasis(span, 0f);
        Vec3 dir = basis.forward(), right = basis.right(), up = basis.up();
        double R = hitRadius;

        // 1. Muzzle calculation array at beam origin (clamped so first-person view stays clean).
        double nearFade = Math.clamp((from.distanceTo(c.cam) - 0.9) / 2.0, 0.22, 1.0);
        calculationArray(c, from.add(dir.scale(0.55)), dir, Math.min(R * 0.95, 2.4), category, age, alpha * 0.82 * nearFade, Math.min(28, detail));

        // 2. Counter-rotating double/triple plasma helices wrapped at 0.72 * hitRadius -> 0.92 * hitRadius.
        int strands = VisualTierConfig.tier() == VisualTier.CINEMATIC && R >= 1.2 ? 3 : 2;
        int steps = Math.clamp((int) Math.ceil(len * 1.4), 10, VisualTierConfig.tier() == VisualTier.CINEMATIC ? 44 : 26);
        double turns = Math.max(1.5, len / Math.max(3.2, R * 3.0));
        for (int s = 0; s < strands; s++) {
            double sign = (s % 2 == 0) ? 1.0 : -1.0;
            double phase0 = s * (Math.TAU / strands) + sign * age * 0.34;
            var helix = new ArrayList<Vec3>(steps + 1);
            for (int i = 0; i <= steps; i++) {
                double u = i / (double) steps;
                double taper = Math.min(1.0, u * 6.0);
                double hr = R * (0.25 + 0.63 * taper);
                double ang = phase0 + sign * u * turns * Math.TAU;
                helix.add(from.add(dir.scale(u * len))
                        .add(right.scale(Math.cos(ang) * hr))
                        .add(up.scale(Math.sin(ang) * hr)));
            }
            double w = Math.clamp(R * 0.095, 0.045, 0.26);
            c.ribbon("cinema/filament", true, helix, u -> w * (0.55 + 0.45 * Math.sin(Math.PI * u)), u -> alpha * 0.72, rgb, Math.max(2, len / 5.0), -age * 0.14);
            c.ribbon("streak", true, helix, u -> w * 0.34, u -> alpha * 0.85, WHITE, 1, 0);
        }

        // 3. Marching Mach shockwave rings locked to 1.00 * hitRadius along the beam corridor + terminal impact ring.
        if (VisualTierConfig.tier() != VisualTier.LOW) {
            int rings = Math.clamp((int) (len / Math.max(4.5, R * 2.6)), 2, 7);
            double ringStroke = Math.clamp(R * 0.055, 0.05, 0.18);
            for (int k = 0; k < rings; k++) {
                double u = ((k + (age * 0.14) % 1.0) / rings);
                if (u < 0.04 || u > 0.98) continue;
                Vec3 ringPos = from.add(dir.scale(u * len));
                double env = Math.sin(Math.PI * u);
                planarRing(c, ringPos, right, up, R, age * 0.08 + k, 18, ringStroke, k % 2 == 0 ? rgb : WHITE, alpha * 0.64 * env, false);
            }
        }
        planarRing(c, to.subtract(dir.scale(0.05)), right, up, R, -age * 0.12, 22, Math.clamp(R * 0.075, 0.06, 0.22), WHITE, alpha * 0.84, true);
    }

    private static HitVolumes.Basis planeBasis(Vec3 normal) {
        Vec3 n = normal != null && normal.lengthSqr() > 1e-8 ? normal.normalize() : new Vec3(0, 1, 0);
        return HitVolumes.obbBasis(n, 0f);
    }

    static void planarRing(Canvas c, Vec3 centre, Vec3 ax, Vec3 ay, double r, double phase, int steps,
                           double width, float[] rgb, double alpha, boolean inkBacking) {
        int n = Math.max(8, steps);
        var pts = new ArrayList<Vec3>(n + 1);
        for (int i = 0; i <= n; i++) {
            double a = phase + (i % n) * (Math.TAU / n);
            pts.add(centre.add(ax.scale(Math.cos(a) * r)).add(ay.scale(Math.sin(a) * r)));
        }
        if (inkBacking) c.ribbon("streak_ink", false, pts, u -> width * 1.38, u -> alpha * 0.60, STEEL, 1, 0);
        c.ribbon("streak", true, pts, u -> width * 0.78, u -> alpha, rgb, 1, 0);
    }

    private static void ellipseRing(Canvas c, Vec3 centre, Vec3 ax, Vec3 ay, double rx, double ry, int steps,
                                    double width, float[] rgb, double alpha) {
        int n = Math.max(10, steps);
        var pts = new ArrayList<Vec3>(n + 1);
        for (int i = 0; i <= n; i++) {
            double a = (i % n) * (Math.TAU / n);
            pts.add(centre.add(ax.scale(Math.cos(a) * rx)).add(ay.scale(Math.sin(a) * ry)));
        }
        c.ribbon("streak", true, pts, u -> width, u -> alpha, rgb, 1, 0);
    }

    private static void inscribedPolygon(Canvas c, Vec3 centre, Vec3 ax, Vec3 ay, double r, double phase, int vertices,
                                         double width, float[] rgb, double alpha) {
        var pts = new ArrayList<Vec3>(vertices + 1);
        for (int i = 0; i <= vertices; i++) {
            double a = phase + (i % vertices) * (Math.TAU / vertices);
            pts.add(centre.add(ax.scale(Math.cos(a) * r)).add(ay.scale(Math.sin(a) * r)));
        }
        c.ribbon("streak", true, pts, u -> width, u -> alpha, rgb, 1, 0);
    }

    private static void starPolygon(Canvas c, Vec3 centre, Vec3 ax, Vec3 ay, double r, double phase, int points, int step,
                                    double width, float[] rgb, double alpha) {
        int gcd = gcd(points, step);
        int loopLen = points / gcd;
        for (int comp = 0; comp < gcd; comp++) {
            var pts = new ArrayList<Vec3>(loopLen + 1);
            for (int i = 0; i <= loopLen; i++) {
                int idx = (comp + i * step) % points;
                double a = phase + idx * (Math.TAU / points);
                pts.add(centre.add(ax.scale(Math.cos(a) * r)).add(ay.scale(Math.sin(a) * r)));
            }
            c.ribbon("streak", true, pts, u -> width, u -> alpha, rgb, 1, 0);
        }
    }

    private static int gcd(int a, int b) {
        while (b != 0) { int t = a % b; a = b; b = t; }
        return Math.max(1, a);
    }

    private static void strokeLine(Canvas c, Vec3 a, Vec3 b, double width, float[] rgb, double alpha) {
        List<Vec3> path = List.of(a, b);
        c.ribbon("streak_ink", false, path, u -> width * 1.35, u -> alpha * 0.58, STEEL, 1, 0);
        c.ribbon("streak", true, path, u -> width * 0.78, u -> alpha, rgb, 1, 0);
    }

    private static Vec3 sph(double theta, double phi) {
        double st = Math.sin(theta);
        return new Vec3(st * Math.cos(phi), Math.cos(theta), st * Math.sin(phi));
    }
}
