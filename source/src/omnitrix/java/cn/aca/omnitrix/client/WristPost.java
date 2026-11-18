package cn.aca.omnitrix.client;

import cn.aca.omnitrix.Abilities;
import cn.aca.omnitrix.AscalonService;
import cn.aca.omnitrix.Form;
import cn.aca.omnitrix.Kind;
import cn.aca.omnitrix.Omnitrix;
import cn.aca.omnitrix.Wrist.Stage;
import cn.aca.omnitrix.WristNetwork;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL30;

final class WristPost {
    private record Field(UUID actor, Vec3 center, long until) {}
    private static long shakeTick = Long.MIN_VALUE;
    private static float shakeStrength;
    private static long syzygyFlashTick = Long.MIN_VALUE;
    private static float syzygyFlashPeak;

    // VFX 2.0 Screen-Space Shockwave Refraction & Sakuga Impact Frame State
    private static long refractTick = Long.MIN_VALUE;
    private static float refractPeak;
    private static float refractDuration = 12f;
    private static Vec3 refractWorldFocus;
    private static long sakugaTick = Long.MIN_VALUE;
    private static float sakugaPeak;
    private static float smoothedFovBoost;

    private static PostChain chain;
    private static RenderTarget target;
    private static int width, height;
    private static boolean unavailable;

    private WristPost() {}

    private static void triggerShockRefraction(long now, Vec3 worldFocus, float strength, float durationTicks) {
        refractTick = now;
        refractPeak = Math.max(refractPeak * .35f, strength);
        refractDuration = durationTicks;
        refractWorldFocus = worldFocus;
    }

    static void effect(WristNetwork.Cue cue, Player viewer, long now) {
        double distSqr = cue.to().distanceToSqr(viewer.position());
        boolean selfCast = cue.actor().equals(viewer.getUUID());

        // VFX 2.0 Ascalon Sakuga Impact Frames + Screen-Space Air-Density Shockwave Refraction!
        if (!ClientState.reduced() && (selfCast || distSqr < 56 * 56)) {
            float falloff = selfCast ? 1f : (float) Math.max(0.0, 1.0 - Math.sqrt(distSqr) / 56.0);
            switch (cue.action()) {
                case WristNetwork.ASCALON_SYZYGY_CUE -> {
                    syzygyFlashTick = now;
                    syzygyFlashPeak = (cue.beat() > 0 ? 1.0f : .78f) * falloff;
                    sakugaTick = now;
                    sakugaPeak = 1.0f * falloff;
                    triggerShockRefraction(now, cue.from().add(0, 1.4, 0), 1.0f * falloff, 26f);
                    shakeTick = now;
                    shakeStrength = Math.max(shakeStrength * .5f, 2.35f * falloff);
                    return;
                }
                case WristNetwork.ASCALON_MACH_CUE -> {
                    int tier = Math.max(1, cue.beat());
                    triggerShockRefraction(now, cue.from(), (0.34f + tier * 0.22f) * falloff, 11f + tier * 3f);
                    if (tier >= 3 && selfCast) {
                        sakugaTick = now;
                        sakugaPeak = 0.78f;
                        syzygyFlashTick = now;
                        syzygyFlashPeak = Math.max(syzygyFlashPeak * .5f, 0.44f);
                    }
                    shakeTick = now;
                    shakeStrength = Math.max(shakeStrength * .5f, (.26f + tier * .24f) * falloff);
                    return;
                }
                case WristNetwork.ASCALON_SWING_CUE -> {
                    // Beat 4 = Parry Counter-Flash, Beat 5 = Charged Astral Iaido, Beat 3 = Finisher
                    if (cue.beat() == 4 || cue.beat() == 5) {
                        sakugaTick = now;
                        sakugaPeak = (cue.beat() == 5 ? 0.92f : 0.86f) * falloff;
                        triggerShockRefraction(now, cue.from().lerp(cue.to(), 0.35), 0.88f * falloff, 16f);
                        syzygyFlashTick = now;
                        syzygyFlashPeak = Math.max(syzygyFlashPeak * .5f, 0.48f * falloff);
                        shakeTick = now;
                        shakeStrength = Math.max(shakeStrength * .5f, 1.35f * falloff);
                        return;
                    } else if (cue.beat() == 3 || cue.hitEntity()) {
                        triggerShockRefraction(now, cue.to(), 0.42f * falloff, 10f);
                    }
                }
                case WristNetwork.ASCALON_RIFT_CUE, WristNetwork.ASCALON_TEMPEST_CUE -> {
                    boolean stage2 = cue.beat() == 1;
                    triggerShockRefraction(now, stage2 ? cue.to() : cue.from().lerp(cue.to(), .5),
                            (stage2 ? 0.82f : 0.56f) * falloff, stage2 ? 18f : 13f);
                    if (stage2) {
                        sakugaTick = now;
                        sakugaPeak = 0.72f * falloff;
                    }
                    syzygyFlashTick = now;
                    syzygyFlashPeak = Math.max(syzygyFlashPeak * .5f, (stage2 ? .48f : .30f) * falloff);
                    shakeTick = now;
                    shakeStrength = Math.max(shakeStrength * .5f, (stage2 ? 1.25f : .78f) * falloff);
                    return;
                }
                case WristNetwork.ASCALON_ARMOR_CUE -> {
                    if (cue.beat() == 1) {
                        triggerShockRefraction(now, cue.from().add(0, 1.1, 0), 0.62f * falloff, 15f);
                        shakeTick = now;
                        shakeStrength = Math.max(shakeStrength * .5f, 0.95f * falloff);
                        return;
                    }
                }
                case WristNetwork.ASCALON_PARRY_CUE -> {
                    triggerShockRefraction(now, cue.to(), (cue.beat() == 2 ? 0.68f : 0.38f) * falloff, 11f);
                    if (cue.beat() == 2 && selfCast) {
                        sakugaTick = now;
                        sakugaPeak = 0.68f;
                    }
                }
                default -> {}
            }
        }

        // Damped hit-confirm camera punch ONLY on confirmed server hits / ground slams
        if (!ClientState.reduced() && (cue.hitEntity() || cue.hitGround())) {
            if (selfCast || distSqr < 22 * 22) {
                float falloff = selfCast ? 1f : (float) Math.max(0, 1.0 - Math.sqrt(distSqr) / 22.0);
                int rawForm = cue.action() % 32;
                float base = (cue.action() == Abilities.SECONDARY + Form.ALIEN_X.ordinal()
                        || cue.action() == WristNetwork.SINGULARITY_CUE
                        || cue.action() == WristNetwork.EXTINCTION_CUE) ? 2.35f
                        : cue.action() == WristNetwork.ASCALON_CLEAVE_HIT_CUE ? .82f
                        : cue.action() == Abilities.SECONDARY + Form.FOUR_ARMS.ordinal() ? 1.35f
                        : rawForm == Form.FOUR_ARMS.ordinal() ? .62f
                        : .35f;
                shakeTick = now;
                shakeStrength = Math.max(shakeStrength * .5f, base * falloff);
            }
        }
    }

    static void reset() {
        shakeTick = Long.MIN_VALUE;
        shakeStrength = 0;
        syzygyFlashTick = Long.MIN_VALUE;
        syzygyFlashPeak = 0;
        refractTick = Long.MIN_VALUE;
        refractPeak = 0;
        refractWorldFocus = null;
        sakugaTick = Long.MIN_VALUE;
        sakugaPeak = 0;
        smoothedFovBoost = 0f;
    }

    static void reload() {
        Runnable clear = () -> {
            if (chain != null) chain.close();
            chain = null;
            unavailable = false;
        };
        if (RenderSystem.isOnRenderThread()) clear.run();
        else RenderSystem.recordRenderCall(clear::run);
    }

    static float impact(ClientState.View view, float partial) {
        if (view.s().phase() != Stage.ARMING) return 0;
        float key = switch (view.s().watch()) {
            case CLASSIC -> 6;
            case CARNITRIX -> 8;
            case CELESTIAL -> 38;
        };
        float t = view.age(partial) - key;
        return t < 0 || t > 4 ? 0 : (float) Math.sin(t * Math.PI / 4);
    }

    private static float hitShake(long now, float partial) {
        if (shakeTick == Long.MIN_VALUE) return 0;
        float elapsed = (now - shakeTick) + partial;
        if (elapsed < 0 || elapsed > 5.0f) return 0;
        return shakeStrength * (1f - elapsed / 5.0f) * Mth.sin(elapsed * 2.6f);
    }

    private static float syzygyWave(long now, float partial) {
        if (syzygyFlashTick == Long.MIN_VALUE) return 0;
        float elapsed = (now - syzygyFlashTick) + partial;
        float dur = syzygyFlashPeak > .7f ? 28f : 12f;
        if (elapsed < 0 || elapsed > dur) return 0;
        return syzygyFlashPeak * (1f - elapsed / dur);
    }

    /** Sakuga pulse: brief 0.45-tick vacuum dip -> razor 1.4-tick positive impact frame (~38ms)! */
    private static float sakugaPulse(long now, float partial, ClientState.AscalonView asc) {
        float chargeImplosion = 0f;
        if (asc != null) {
            float iaido = asc.iaidoChargeProgress(partial);
            if (iaido > 0.12f) {
                chargeImplosion = -0.44f * iaido;
            }
        }
        if (sakugaTick != Long.MIN_VALUE) {
            float elapsed = (now - sakugaTick) + partial;
            if (elapsed >= 0f && elapsed < 0.38f) {
                return -0.52f * sakugaPeak;
            }
            if (elapsed >= 0.38f && elapsed <= 1.85f) {
                float p = (elapsed - 0.38f) / 1.47f;
                return sakugaPeak * (1f - p * p);
            }
        }
        return chargeImplosion;
    }

    static void camera(ViewportEvent.ComputeCameraAngles event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || ClientState.reduced() || mc.getCameraEntity() != mc.player) return;
        float partial = (float) event.getPartialTick();
        float scale = mc.options.screenEffectScale().get().floatValue();
        var view = ClientState.self();
        float bump = view == null ? 0 : impact(view, partial);
        if (bump > 0 && view != null) {
            boolean carni = view.s().watch() == Kind.CARNITRIX;
            event.setPitch(event.getPitch() + bump * (carni ? 2.4f : 1.55f) * scale);
            event.setRoll(event.getRoll() + Mth.sin(view.age(partial) * 2) * bump * (carni ? 2.1f : 1.05f) * scale);
        }
        float combatShake = hitShake(mc.level.getGameTime(), partial);
        if (Math.abs(combatShake) > .01f) {
            event.setPitch(event.getPitch() + combatShake * scale);
            event.setRoll(event.getRoll() + combatShake * .58f * scale);
        }
        if (mc.player != null) {
            float flightBank = AscalonRenderer.cameraBankRoll(mc.player, partial);
            if (Math.abs(flightBank) > .02f) {
                event.setRoll(event.getRoll() + flightBank * .28f * scale);
            }
        }
    }

    static void fov(ViewportEvent.ComputeFov event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || ClientState.reduced() || mc.getCameraEntity() != mc.player) return;
        float partial = (float) event.getPartialTick();
        float fovScale = mc.options.fovEffectScale().get().floatValue();
        float targetDelta = 0f;

        // Pre-burst Sakuga tension zoom-in during Iaido sheath charge!
        var asc = ClientState.selfAscalon();
        if (asc != null) {
            float iaido = asc.iaidoChargeProgress(partial);
            if (iaido > 0.08f) {
                targetDelta -= iaido * 4.2f * fovScale;
            }
        }
        if (AscalonService.isViltrumFlightActive(mc.player) && !mc.player.onGround()) {
            float mult = AscalonRenderer.playerFlightMultiplier(mc.player, partial);
            float machRatio = Mth.clamp((mult - 1.0f) / 8.0f, 0f, 1f);
            targetDelta += machRatio * 12.0f * fovScale;
        }
        smoothedFovBoost = Mth.lerp(.18f, smoothedFovBoost, targetDelta);
        if (Math.abs(smoothedFovBoost) > .04f) {
            event.setFOV(event.getFOV() + smoothedFovBoost);
        }
    }

    private static float[] projectWorldToScreenUv(RenderLevelStageEvent event, Vec3 camera, Vec3 worldPos) {
        Vec3 rel = worldPos.subtract(camera);
        Vector4f clip = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0f);
        clip.mul(event.getModelViewMatrix());
        clip.mul(event.getProjectionMatrix());
        if (clip.w <= 0.05f) return new float[]{0.5f, 0.48f};
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        return new float[]{
                Mth.clamp(ndcX * 0.5f + 0.5f, 0.12f, 0.88f),
                Mth.clamp(ndcY * 0.5f + 0.5f, 0.12f, 0.88f)
        };
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || unavailable) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        long now = mc.level.getGameTime();
        Field field = null;
        Vec3 camera = event.getCamera().getPosition();
        for (var candidate : ClientState.scenes()) {
            var scene = candidate.scene();
            if (scene.effect() == WristNetwork.STASIS_SCENE && candidate.until() > now
                    && scene.center().distanceToSqr(camera) < 72 * 72
                    && (field == null || scene.center().distanceToSqr(camera) < field.center.distanceToSqr(camera))) {
                field = new Field(scene.actor(), scene.center(), candidate.until());
            }
        }
        var view = ClientState.self();
        var asc = ClientState.selfAscalon();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float screenScale = mc.options.screenEffectScale().get().floatValue();
        float hit = view == null || ClientState.reduced() ? 0 : impact(view, partial);
        if (!ClientState.reduced()) {
            hit = Math.max(hit, Math.min(.32f, Math.abs(hitShake(now, partial)) * .16f));
        }
        float red = 0;
        if (view != null && view.s().watch() == Kind.CARNITRIX) {
            if (view.s().controlled()) red = .56f + (ClientState.reduced() ? 0 : .29f * hit);
            else if (view.s().phase() == Stage.LOCKING) red = .28f;
        }

        float ascalonAura = 0f;
        float bloomBoost = 0f;
        float refractStrength = 0f;
        float refractRing = 0.25f;
        float focusX = 0.50f, focusY = 0.48f;
        float sakuga = 0f;

        if (!ClientState.reduced()) {
            boolean armored = asc != null && asc.s().armored();
            boolean holding = AscalonService.holdingAscalon(mc.player);
            boolean guarding = holding && mc.player.isUsingItem();
            if (armored || holding) {
                bloomBoost = armored ? 0.52f : 0.34f;
            }
            if (guarding) {
                ascalonAura = armored ? .52f : .38f;
                bloomBoost = Math.max(bloomBoost, 0.65f);
            }
            if (asc != null && asc.skySyzygyActive(partial)) {
                ascalonAura = Math.max(ascalonAura, .68f);
                bloomBoost = Math.max(bloomBoost, 0.92f);
            }
            if (armored && AscalonService.isViltrumFlightActive(mc.player) && !mc.player.onGround()) {
                float mult = AscalonRenderer.playerFlightMultiplier(mc.player, partial);
                float machRatio = Mth.clamp((mult - 2.0f) / 7.0f, 0f, 1f);
                if (machRatio > .02f) {
                    ascalonAura = Math.max(ascalonAura, machRatio * .46f);
                    bloomBoost = Math.max(bloomBoost, 0.55f + machRatio * 0.40f);
                    // Continuous subtle Prandtl-Glauert air-density slipstream refraction ring around Paladin at >= 4.2x speed!
                    if (mult >= 4.2f) {
                        refractStrength = (mult - 4.2f) / 4.8f * 0.28f;
                        refractRing = 0.22f + 0.04f * Mth.sin((now + partial) * 0.45f);
                    }
                }
            }

            if (refractTick != Long.MIN_VALUE) {
                float elapsed = (now - refractTick) + partial;
                if (elapsed >= 0f && elapsed <= refractDuration) {
                    float p = elapsed / refractDuration;
                    // Fast Ease-Out Quart shockwave ring expansion
                    float ease = 1f - (1f - p) * (1f - p) * (1f - p);
                    float transientStr = refractPeak * (1f - p);
                    if (transientStr > refractStrength) {
                        refractStrength = transientStr;
                        refractRing = 0.04f + ease * 0.92f;
                        if (refractWorldFocus != null) {
                            float[] uv = projectWorldToScreenUv(event, camera, refractWorldFocus);
                            focusX = uv[0];
                            focusY = uv[1];
                        }
                    }
                    bloomBoost = Math.max(bloomBoost, 0.72f * (1f - p));
                }
            }
            sakuga = sakugaPulse(now, partial, asc) * screenScale;
        }

        float syzygy = ClientState.reduced() ? 0f : syzygyWave(now, partial) * screenScale;
        ascalonAura *= screenScale;
        refractStrength *= screenScale;
        bloomBoost *= Math.max(0.35f, screenScale);

        if (hit <= .002f && red == 0f && field == null && ascalonAura <= .002f && syzygy <= .002f
                && refractStrength <= .002f && Math.abs(sakuga) <= .005f && bloomBoost <= .005f) {
            return;
        }
        RenderTarget main = mc.getMainRenderTarget();
        try {
            prepare(mc, main);
            chain.setUniform("Impact", hit * screenScale);
            chain.setUniform("Blood", red);
            float seconds = (now % 480 + partial) / 20f;
            chain.setUniform("PulseTime", ClientState.reduced() ? 0 : seconds);
            chain.setUniform("Freeze", field == null ? 0
                    : Mth.clamp((field.until - now) / 9f, 0, 1));
            chain.setUniform("AscalonAura", ascalonAura);
            chain.setUniform("SyzygyFlash", syzygy);
            chain.setUniform("RefractStrength", refractStrength);
            chain.setUniform("RefractRing", refractRing);
            chain.setUniform("FocusUvX", focusX);
            chain.setUniform("FocusUvY", focusY);
            chain.setUniform("SakugaFlash", sakuga);
            chain.setUniform("BloomBoost", bloomBoost);
            if (field != null) uniforms(event, field, camera, partial);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            chain.process(event.getPartialTick().getGameTimeDeltaTicks());
            RenderTarget output = chain.getTempTarget("wrist_color");
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, output.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height,
                    GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
        } catch (Exception ex) {
            LogUtils.getLogger().warn("Could not render the Omnitrix post effect", ex);
            unavailable = true;
        } finally {
            main.bindWrite(true);
            RenderSystem.enableDepthTest();
        }
    }

    private static void prepare(Minecraft mc, RenderTarget main) throws java.io.IOException {
        if (chain == null || target != main) {
            if (chain != null) chain.close();
            chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, Omnitrix.id("shaders/post/wrist.json"));
            target = main;
            width = 0;
        }
        if (width != main.width || height != main.height) {
            width = main.width;
            height = main.height;
            chain.resize(width, height);
        }
    }

    private static void uniforms(RenderLevelStageEvent event, Field field, Vec3 camera, float partial) {
        Matrix4f inverse = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()).invert();
        float[] values = inverse.get(new float[16]);
        for (int i = 0; i < 16; i++) chain.setUniform("Inv" + i, values[i]);
        Vec3 center = field.center.subtract(camera);
        chain.setUniform("CenterX", (float) center.x);
        chain.setUniform("CenterY", (float) center.y);
        chain.setUniform("CenterZ", (float) center.z);
        var caster = Minecraft.getInstance().level.getPlayerByUUID(field.actor);
        Vec3 focus = caster == null ? center : caster.getPosition(partial).subtract(camera);
        chain.setUniform("CasterX", (float) focus.x);
        chain.setUniform("CasterY", (float) focus.y);
        chain.setUniform("CasterZ", (float) focus.z);
    }
}
