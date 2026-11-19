package cn.academy.port.client.canon;

import cn.academy.port.VisualTier;
import cn.academy.port.VisualTierConfig;
import cn.academy.port.client.canon.CanonFx.Canvas;
import cn.academy.port.macro.MacroSkillRules;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Iris / Oculus shader-pack 3D surrogate renderer.
 *
 * <p>When an external shader pack is active ({@code ShaderPackState.active()}) or custom FX4/volume shaders are disabled,
 * screen-copy refraction ({@code REFRACT}/{@code DISPERSE}) and ray-marched HDR volumes ({@link AgVolumeRenderer}) cannot
 * blit the G-buffer safely. Instead of dropping those layers to {@code null}, this class renders multi-layer 3D chromatic
 * prism shells, Mach shock lenses, and volumetric light-cel domes/pillars through ordinary {@code POSITION_TEX_COLOR}
 * batches so every skill retains full visual impact under any Iris shader pack.
 */
final class CanonIrisFallback {
    private static final float[] PRISM_CYAN = {0.50f, 0.92f, 1.0f};
    private static final float[] PRISM_ROSE = {1.0f, 0.58f, 0.90f};
    private static final float[] PRISM_GOLD = {1.0f, 0.92f, 0.68f};
    private static final float[] CEL_WHITE = {0.97f, 0.99f, 1.0f};

    private CanonIrisFallback() {}

    /**
     * Surrogate for {@code Canvas.refract} and {@code REFRACT}/{@code DISPERSE} quads when custom scene-copy shaders are off.
     */
    static void refractSurrogate(Canvas c, boolean dispersion, Vec3 centre, double size,
                                 float strength, float[] tint, double phaseSeed) {
        if (size <= 0.05 || strength <= 0.01f || VisualTierConfig.tier() == VisualTier.OFF) return;
        double alpha = Math.clamp(strength * 0.78, 0.02, 0.85);
        double stroke = Math.clamp(size * 0.055, 0.028, 0.16);
        int segs = size >= 2.2 ? 24 : 16;

        if (dispersion) {
            // SH5 Chromatic Dispersion Surrogate: 3 slightly offset RGB/CMY prism rings + 3D crystal core glint.
            Vec3 offC = c.right.scale(size * 0.035).add(c.up.scale(size * 0.02));
            Vec3 offR = c.right.scale(-size * 0.035).subtract(c.up.scale(size * 0.02));
            AgModernVfx.planarRing(c, centre, c.right, c.up, size * 0.92, phaseSeed, segs, stroke * 0.85, tint, alpha * 0.52, true);
            AgModernVfx.planarRing(c, centre.add(offC), c.right, c.up, size * 0.86, -phaseSeed * 1.2, segs, stroke * 0.62, PRISM_CYAN, alpha * 0.64, false);
            AgModernVfx.planarRing(c, centre.add(offR), c.right, c.up, size * 0.80, phaseSeed * 0.9 + 0.8, segs, stroke * 0.58, PRISM_ROSE, alpha * 0.58, false);
            c.sprite("sparkle", true, centre, size * 0.42, phaseSeed * 0.7, PRISM_GOLD, (float) (alpha * 0.55));
        } else {
            // SH1 Air / Water Refraction Lens Surrogate: crisp Mach shock lens + soft grazing halo.
            AgModernVfx.planarRing(c, centre, c.right, c.up, size * 0.94, phaseSeed, segs, stroke * 0.78, tint, alpha * 0.56, true);
            if (VisualTierConfig.tier() != VisualTier.LOW && size >= 0.45) {
                AgModernVfx.planarRing(c, centre, c.right, c.up, size * 0.66, -phaseSeed * 1.35, Math.max(12, segs - 4),
                        stroke * 0.48, CEL_WHITE, alpha * 0.42, false);
            }
            c.sprite("ring2", true, centre, size * 0.88, -phaseSeed * 0.5, tint, (float) (alpha * 0.28));
        }
    }

    /**
     * Surrogate for {@link AgVolumeRenderer} ray-marched HDR volumes when Iris shaders are active.
     */
    static void volumeSurrogate(Canvas c, MacroVfxClient.VolumeSample sample, float[] categoryColor) {
        if (sample == null || sample.opacity() <= 0.01 || VisualTierConfig.tier() == VisualTier.OFF) return;
        var v = sample.event();
        var profile = MacroSkillRules.find(v.skill());
        if (profile == null) return;

        double r = Math.max(0.6, sample.radius());
        double age = sample.age();
        double alpha = Math.clamp(sample.opacity() * 0.82, 0.02, 0.88);
        Vec3 centre = sample.centre();
        boolean isLine = v.shape() == MacroSkillRules.Shape.LINE && v.from().distanceToSqr(v.to()) > 1.0;

        if (isLine) {
            c.energyStream(v.from(), v.to(), Math.clamp(r * 0.42, 0.18, 1.35), categoryColor, alpha * 0.78);
            AgModernVfx.grandBeamHelices(c, v.from(), v.to(), Math.max(0.45, r), profile.category(), categoryColor, age, alpha * 0.68, 24);
            return;
        }

        // 1. Grazing Fresnel dome locked to exact volume radius (crystal-clear interior, glowing rim under Iris).
        double domeH = Math.max(2.0, AgEffectDesign.height(profile, (float) r));
        AgModernVfx.fresnelDome(c, centre, r, Math.min(r * 1.15, domeH * 0.75), categoryColor, age, alpha * 0.66, 8, 18);

        // 2. Central multi-layer vertical light-cel column so large 32-64m domains have unmistakable vertical presence.
        double pillarH = Math.clamp(r * 0.58, 3.5, 22.0);
        double pillarW = Math.clamp(r * 0.085, 0.35, 2.4);
        Vec3 top = centre.add(0, pillarH, 0);
        List<Vec3> axisPath = List.of(centre.add(0, 0.1, 0), top);
        c.ribbon("streak_ink", false, axisPath, u -> pillarW * 1.45 * (1.0 - 0.45 * u), u -> alpha * 0.42, Canon3dMeshKit.DARK_SHEATH, 1, 0);
        c.ribbon("cinema/filament", true, axisPath, u -> pillarW * (1.0 - 0.40 * u), u -> alpha * 0.68 * (1.0 - 0.35 * u), categoryColor, 3, -age * 0.08);
        c.ribbon("streak", true, axisPath, u -> pillarW * 0.36 * (1.0 - 0.45 * u), u -> alpha * 0.76 * (1.0 - 0.4 * u), CEL_WHITE, 1, 0);
    }
}
