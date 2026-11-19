package cn.academy.port.client.canon;

import cn.academy.port.VisualTier;
import cn.academy.port.VisualTierConfig;
import cn.academy.port.client.canon.CanonFx.Canvas;
import cn.academy.port.client.canon.CanonFx.View;
import cn.academy.port.macro.HitVolumes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * FX5 3D procedural light-cel & prism mesh kit.
 *
 * <p>Follows real-time VFX value hierarchy (Dark-Sheath substrate + saturated chromatic body + crisp cel highlight spine)
 * so silhouettes stay sharp against daylight skies, snow, and Iris shader bloom without washing out to flat white.
 * Pure client presentation on {@link Canvas}; never mutates gameplay state.
 */
final class Canon3dMeshKit {
    static final float[] DARK_SHEATH = {0.055f, 0.078f, 0.145f};
    static final float[] INK_SHEATH = {0.075f, 0.052f, 0.135f};
    static final float[] WARM_SHEATH = {0.18f, 0.11f, 0.045f};
    static final float[] CEL_IVORY = {0.98f, 0.99f, 1.0f};
    static final float[] PRISM_CYAN = {0.54f, 0.93f, 1.0f};
    static final float[] PRISM_ROSE = {1.0f, 0.64f, 0.92f};
    static final float[] PRISM_GOLD = {1.0f, 0.86f, 0.42f};

    private Canon3dMeshKit() {}

    /**
     * Three-stage nonlinear hit envelope:
     * 1. Snap-in burst (0..14%): quartic ease-out to full peak in ~2 ticks.
     * 2. Sustain plateau (14..48%): crisp silhouette hold with subtle harmonic breath.
     * 3. Breathable afterglow (48..100%): smooth power-curve decay with lingering prism tail.
     */
    static double nonlinearHitEnvelope(double age, double life) {
        double span = Math.max(1.0, life);
        double u = Math.clamp(age / span, 0.0, 1.0);
        if (u < 0.14) {
            double t = Math.clamp((age + 0.45) / Math.max(1.2, span * 0.14), 0.0, 1.0);
            double inv = 1.0 - t;
            return 1.0 - inv * inv * inv * inv;
        }
        if (u < 0.48) {
            double p = (u - 0.14) / 0.34;
            return 1.0 - 0.08 * p;
        }
        double tail = (u - 0.48) / 0.52;
        return 0.92 * Math.pow(Math.max(0.0, 1.0 - tail), 2.15);
    }

    /**
     * 3D curved crescent slash ribbon with Dark-Sheath substrate, saturated body, twin prismatic rim, and cel core spine.
     */
    static void slashArc3D(Canvas c, Vec3 centre, Vec3 forward, Vec3 swingPlaneSide, double radius,
                           double sweepRadians, double progress01, double bladeWidth,
                           float[] mainRgb, float[] rimRgb, double alpha, boolean darkInkStyle) {
        if (radius <= 0.12 || bladeWidth <= 0.01 || alpha <= 0.006 || VisualTierConfig.tier() == VisualTier.OFF) return;
        Vec3 fwd = forward.lengthSqr() > 1e-6 ? forward.normalize() : new Vec3(0, 0, 1);
        Vec3 side = swingPlaneSide.subtract(fwd.scale(swingPlaneSide.dot(fwd)));
        if (side.lengthSqr() < 1e-6) side = fwd.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 normal = fwd.cross(side).normalize();

        int steps = VisualTierConfig.tier() == VisualTier.CINEMATIC ? 20 : 14;
        double head = AgModernVfx.snapEase(Math.clamp(progress01 * 1.35, 0.0, 1.0));
        double tail = Math.clamp((progress01 - 0.28) / 0.72, 0.0, 0.82);
        if (head - tail <= 0.04) return;

        var outer = new ArrayList<Vec3>(steps + 1);
        var innerChroma = new ArrayList<Vec3>(steps + 1);
        double halfSweep = sweepRadians * 0.5;
        for (int i = 0; i <= steps; i++) {
            double u = tail + (head - tail) * (i / (double) steps);
            double ang = -halfSweep + sweepRadians * u;
            double belly = 0.88 + 0.12 * Math.sin(Math.PI * u);
            double camber = Math.sin(Math.PI * u) * bladeWidth * 0.22;
            Vec3 radial = fwd.scale(Math.cos(ang)).add(side.scale(Math.sin(ang)));
            outer.add(centre.add(radial.scale(radius * belly)).add(normal.scale(camber)));
            innerChroma.add(centre.add(radial.scale(radius * belly * 0.86)).subtract(normal.scale(camber * 0.5)));
        }

        float[] sheath = darkInkStyle ? INK_SHEATH : DARK_SHEATH;
        // Layer 1: Dark-Sheath contrast backing (prevents daylight / shader bloom washout).
        c.ribbon("streak_ink", false, outer,
                u -> bladeWidth * 1.52 * Math.pow(Math.sin(Math.PI * u), 0.72) * (0.65 + 0.35 * u),
                u -> alpha * (darkInkStyle ? 0.88 : 0.68), sheath, 1, 0);

        // Layer 2: Saturated elemental / chromatic body + secondary harmonic inner arc.
        if (darkInkStyle) {
            c.ribbon4("fx4:ls_wet_ribbon", CanonFx4.Mode.ALPHA, outer,
                    u -> bladeWidth * 1.08 * Math.pow(Math.sin(Math.PI * u), 0.68),
                    u -> alpha * 0.94, mainRgb, 2, -progress01 * 0.5);
            c.ribbon("streak", true, innerChroma,
                    u -> bladeWidth * 0.42 * Math.sin(Math.PI * u),
                    u -> alpha * 0.62, rimRgb, 1, 0);
        } else {
            c.ribbon4("fx4:common_speedline", CanonFx4.Mode.ADD, outer,
                    u -> bladeWidth * 1.05 * Math.pow(Math.sin(Math.PI * u), 0.70),
                    u -> alpha * 0.88, mainRgb, 2, -progress01 * 0.6);
            c.ribbon("cinema/filament", true, innerChroma,
                    u -> bladeWidth * 0.62 * Math.sin(Math.PI * u),
                    u -> alpha * 0.64, rimRgb, 2, -progress01 * 0.4);
            // Layer 3: Crisp cel ivory highlight spine.
            c.ribbon("streak", true, outer,
                    u -> bladeWidth * 0.30 * Math.pow(Math.sin(Math.PI * u), 0.85),
                    u -> alpha * 0.94, CEL_IVORY, 1, 0);
        }

        // Leading slash tip star glint.
        Vec3 tip = outer.getLast();
        c.sprite("sparkle", !darkInkStyle, tip, bladeWidth * 0.95, progress01 * 2.4,
                darkInkStyle ? rimRgb : CEL_IVORY, (float) (alpha * 0.88));
    }

    /**
     * 3D bevelled crystal prism / spear along {@code base -> tip} with faceted shading and Dark-Sheath ridge lines.
     */
    static void facetedPrism(Canvas c, Vec3 base, Vec3 tip, double radius, int sides, double spin,
                             float[] primaryRgb, float[] secondaryRgb, double alpha, boolean additiveCore) {
        Vec3 span = tip.subtract(base);
        double len = span.length();
        if (len < 0.06 || radius <= 0.015 || alpha <= 0.006 || VisualTierConfig.tier() == VisualTier.OFF) return;
        int n = Math.clamp(sides, 4, 8);
        HitVolumes.Basis basis = HitVolumes.obbBasis(span, 0f);
        Vec3 axis = basis.forward(), right = basis.right(), up = basis.up();
        Vec3 waist = base.lerp(tip, 0.32);
        Vec3 tailApex = base.subtract(axis.scale(Math.min(len * 0.22, radius * 1.1)));

        Vec3[] ring = new Vec3[n];
        for (int k = 0; k < n; k++) {
            double a = spin + k * (Math.TAU / n);
            ring[k] = waist.add(right.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius));
        }

        float[] tile = Fx4Atlas.get("dm_shard_atlas").cell(2);
        float um = (tile[0] + tile[2]) * 0.5f, vm = (tile[1] + tile[3]) * 0.5f;
        float[] uv = {um - 0.02f, vm - 0.02f, um + 0.02f, vm + 0.02f};

        for (int k = 0; k < n; k++) {
            Vec3 r0 = ring[k], r1 = ring[(k + 1) % n];
            float[] tint = (k % 2 == 0) ? primaryRgb : secondaryRgb;
            float crownShade = (k % 2 == 0) ? 1.0f : 0.82f;
            float pavShade = (k % 2 == 0) ? 0.78f : 0.64f;
            c.fx4Quad("fx4:dm_shard_atlas", CanonFx4.Mode.ALPHA,
                    tip, r0, r1, tip, uv,
                    tint[0] * crownShade, tint[1] * crownShade, tint[2] * crownShade, (float) (alpha * 0.92));
            c.fx4Quad("fx4:dm_shard_atlas", CanonFx4.Mode.ALPHA,
                    tailApex, r1, r0, tailApex, uv,
                    tint[0] * pavShade, tint[1] * pavShade, tint[2] * pavShade, (float) (alpha * 0.86));
        }

        // Crisp ridge highlight & dark contrast spine along the central axis.
        List<Vec3> spine = List.of(tailApex, tip);
        c.ribbon("streak_ink", false, spine, u -> radius * 0.85 * Math.sin(Math.PI * u), u -> alpha * 0.48, DARK_SHEATH, 1, 0);
        if (additiveCore) {
            c.ribbon("streak", true, spine, u -> radius * 0.42 * Math.sin(Math.PI * u), u -> alpha * 0.86, CEL_IVORY, 1, 0);
        }
    }

    /**
     * 3D geodesic hexagonal forcefield dome / shell around {@code centre} with grazing Fresnel visibility.
     */
    static void hexPressureDome(Canvas c, Vec3 centre, double rx, double ry, float[] rgb, double age, double alpha, int rings) {
        if (rx <= 0.18 || ry <= 0.18 || alpha <= 0.008 || VisualTierConfig.tier() == VisualTier.OFF) return;
        AgModernVfx.fresnelDome(c, centre, rx, ry, rgb, age, alpha * 0.78, Math.clamp(rings + 3, 6, 10), 16);
        if (VisualTierConfig.tier() == VisualTier.LOW) return;

        int bands = Math.clamp(rings, 2, 4);
        double hexScale = Math.clamp(rx * 0.18, 0.14, 0.85);
        double stroke = Math.clamp(rx * 0.018, 0.032, 0.11);
        for (int b = 0; b < bands; b++) {
            double elev = (0.18 + 0.56 * (b / (double) Math.max(1, bands - 1))) * (Math.PI * 0.5);
            double ringR = Math.cos(elev) * rx;
            double y = Math.sin(elev) * ry;
            int cells = Math.clamp(6 + b * 2, 6, 12);
            for (int k = 0; k < cells; k++) {
                double ang = age * 0.022 * (b % 2 == 0 ? 1 : -1) + k * (Math.TAU / cells) + (b & 1) * 0.26;
                Vec3 normal = new Vec3(Math.cos(ang) * Math.cos(elev), Math.sin(elev), Math.sin(ang) * Math.cos(elev)).normalize();
                Vec3 cellCentre = centre.add(Math.cos(ang) * ringR, y, Math.sin(ang) * ringR);
                Vec3 toCam = c.cam.subtract(cellCentre);
                double dist = toCam.length();
                if (dist < 0.4) continue;
                double ndotv = normal.dot(toCam.scale(1.0 / dist));
                if (ndotv <= -0.05) continue;
                double fresnel = 0.24 + 0.76 * Math.pow(1.0 - Math.clamp(ndotv, 0.0, 1.0), 1.8);
                double pulse = 0.72 + 0.28 * Math.sin(age * 0.18 + k * 0.9 + b);
                HitVolumes.Basis tang = HitVolumes.obbBasis(normal, 0f);
                hexCell(c, cellCentre, tang.right(), tang.up(), hexScale, stroke,
                        pulse > 0.9 ? CEL_IVORY : rgb, alpha * 0.62 * fresnel * pulse);
            }
        }
    }

    private static void hexCell(Canvas c, Vec3 centre, Vec3 ax, Vec3 ay, double r, double width, float[] rgb, double alpha) {
        var pts = new ArrayList<Vec3>(7);
        for (int i = 0; i <= 6; i++) {
            double a = (i % 6) * (Math.TAU / 6.0);
            pts.add(centre.add(ax.scale(Math.cos(a) * r)).add(ay.scale(Math.sin(a) * r)));
        }
        c.ribbon("streak", true, pts, u -> width, u -> alpha, rgb, 1, 0);
    }

    /**
     * Breathable dual counter-rotating calculation rings with Dark-Sheath backing and floating vernier nodes.
     */
    static void dualCalcRings(Canvas c, Vec3 centre, Vec3 normal, double radius, String category,
                              double age, double alpha, boolean verticalOffset) {
        if (radius <= 0.18 || alpha <= 0.008 || VisualTierConfig.tier() == VisualTier.OFF) return;
        Vec3 nrm = normal != null && normal.lengthSqr() > 1e-6 ? normal.normalize() : new Vec3(0, 1, 0);
        AgModernVfx.calculationArray(c, centre, nrm, radius, category, age, alpha * 0.85, 28);
        if (verticalOffset && VisualTierConfig.tier() != VisualTier.LOW) {
            float[][] pal = AgModernVfx.categoryPalette(category);
            HitVolumes.Basis basis = HitVolumes.obbBasis(nrm, 0f);
            Vec3 upper = centre.add(nrm.scale(Math.clamp(radius * 0.24, 0.28, 1.35)));
            double stroke = Math.clamp(radius * 0.018, 0.038, 0.16);
            AgModernVfx.planarRing(c, upper, basis.right(), basis.up(), radius * 0.72, -age * 0.065,
                    22, stroke, pal[1], alpha * 0.64, true);
        }
    }

    /**
     * 3D shockwave crown combining terrain-snapped Dark-Sheath perimeter, expanding Mach ring, and angled prism fins.
     */
    static void shockwaveCrown3D(Canvas c, Vec3 centre, double radius, float[] rgb, double age, double alpha, int segments) {
        if (radius <= 0.22 || alpha <= 0.008 || VisualTierConfig.tier() == VisualTier.OFF) return;
        AgModernVfx.terrainShockCrown(c, centre, radius, rgb, age, alpha, segments, true);
        if (VisualTierConfig.tier() == VisualTier.LOW) return;
        double waveP = Math.clamp(age / 12.0, 0.0, 1.0);
        if (waveP < 1.0) {
            double innerR = radius * (0.35 + 0.62 * AgModernVfx.snapEase(waveP));
            AgModernVfx.planarRing(c, centre.add(0, 0.18 + 0.35 * waveP, 0),
                    new Vec3(1, 0, 0), new Vec3(0, 0, 1), innerR, -age * 0.09,
                    Math.max(16, segments * 2 / 3), Math.clamp(radius * 0.02, 0.05, 0.22),
                    CEL_IVORY, alpha * 0.72 * (1.0 - waveP), false);
        }
    }

    /** Draw the accent only when the originating server controller confirmed the auxiliary effect. */
    static void drawAuxBoostAccent(Canvas c, View v, double e, double t) {
        if (e <= 0.01 || v.phase() == 3 || VisualTierConfig.tier() == VisualTier.OFF) return;
        if (!v.boosted()) return;

        Vec3 body = v.anchor().add(0, 0.08, 0);
        switch (v.category()) {
            case "dark_matter" -> {
                // Prismatic Rose-Cyan double halo around the caster/target.
                AgModernVfx.planarRing(c, v.anchor().add(0, 1.15, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                        0.95 + 0.06 * Math.sin(t * 0.15), t * 0.08, 20, 0.055, PRISM_ROSE, e * 0.72, true);
                AgModernVfx.planarRing(c, v.anchor().add(0, 1.22, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                        0.82, -t * 0.11, 18, 0.042, PRISM_CYAN, e * 0.68, false);
            }
            case "nitrogen_armor" -> {
                // Cryo-canister hexagonal frost seal under feet + rising cold crown.
                AgModernVfx.planarRing(c, body, new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                        1.05, t * 0.06, 6, 0.065, PRISM_CYAN, e * 0.76, true);
            }
            case "aero_hand" -> {
                // Pressure-chip twin Mach rings at launch origin.
                Vec3 f = CanonFxScripts.forward(v);
                HitVolumes.Basis b = HitVolumes.obbBasis(f, v.yaw());
                AgModernVfx.planarRing(c, v.origin().add(f.scale(0.35)), b.right(), b.up(),
                        0.68, t * 0.14, 18, 0.05, new float[]{0.62f, 1.0f, 0.90f}, e * 0.75, true);
            }
            case "hydro_hand" -> {
                // Water-flask portable vortex ring around caster waist/hand when channeling from flask.
                AgModernVfx.planarRing(c, v.anchor().add(0, 0.92, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                        0.76, -t * 0.16, 20, 0.052, new float[]{0.36f, 0.84f, 1.0f}, e * 0.74, true);
            }
            case "mental_out" -> {
                // Mental-remote golden orbital star-circuit around the target/hand.
                Vec3 at = v.endpoint().distanceToSqr(v.origin()) > 0.5 ? v.endpoint().add(0, 0.45, 0) : v.origin();
                AgModernVfx.planarRing(c, at, c.right, c.up, 0.58, t * 0.12, 16, 0.042, PRISM_GOLD, e * 0.78, true);
            }
            case "float_dial" -> {
                // Buoyancy-float emerald-lilac beacon spire at the field/marker anchor.
                Vec3 anchor = v.rangeCentre() != null ? v.rangeCentre() : v.endpoint();
                List<Vec3> spire = List.of(anchor, anchor.add(0, 2.4, 0));
                c.ribbon("streak_ink", false, spire, u -> 0.11 * (1.0 - 0.5 * u), u -> e * 0.52, DARK_SHEATH, 1, 0);
                c.ribbon("streak", true, spire, u -> 0.055 * (1.0 - 0.5 * u), u -> e * 0.82, new float[]{0.72f, 1.0f, 0.86f}, 1, 0);
            }
            case "paper_psychokinesis" -> {
                // Origami cinnabar-gold seal ring around the caster's shoulders.
                AgModernVfx.planarRing(c, v.anchor().add(0, 1.28, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                        0.74, t * 0.09, 16, 0.045, new float[]{0.96f, 0.42f, 0.32f}, e * 0.72, true);
            }
            default -> {}
        }
    }
}
