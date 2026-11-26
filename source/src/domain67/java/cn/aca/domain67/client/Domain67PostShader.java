package cn.aca.domain67.client;

import cn.aca.domain67.AcaDomain67;
import cn.aca.domain67.Domain67Manager;
import cn.aca.domain67.Domain67Network;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * ACA-Domain67 v1.1.0 Warm Ivory-Gold Full-World Reality-Marble Post-Processing & Celestial Fog Takeover:
 * - Hijacks vanilla sky/fog colors to Warm Sunlit Champagne-Ivory (#FCF0D6) when inside the 64m Domain
 * - Drives the 320m 3D World-Space SDF Raymarched Modern Luxury Banquet Hall (`domain67_post.fsh`)
 */
public final class Domain67PostShader {
    private static PostChain chain;
    private static RenderTarget boundTarget;
    private static int cachedW, cachedH;
    private static boolean unavailable;

    private static boolean irisChecked = false;
    private static Object irisInstance = null;
    private static Method irisInUseMethod = null;

    private static long impactStartTick = Long.MIN_VALUE;
    private static float impactPeak = 0f;
    private static boolean impactThreeBeat = false;
    private static long shockStartTick = Long.MIN_VALUE;
    private static float shockPeak = 0f;
    private static float shockDuration = 22f;
    private static Vec3 shockFocus = null;
    private static float chromaPeak = 0f;
    private static float smoothedBloom = 0f;

    private static long fovKickTick = Long.MIN_VALUE;
    private static float fovKickPeak = 0f;
    private static boolean fovThreeBeat = false;
    private static float smoothedFov = 0f;

    private Domain67PostShader() {}

    public static boolean isIrisShaderActive() {
        if (!irisChecked) {
            irisChecked = true;
            if (ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus")) {
                try {
                    Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    irisInstance = api.getMethod("getInstance").invoke(null);
                    irisInUseMethod = api.getMethod("isShaderPackInUse");
                } catch (Throwable ignored) {
                    irisInstance = null;
                    irisInUseMethod = null;
                }
            }
        }
        if (irisInstance != null && irisInUseMethod != null) {
            try {
                return Boolean.TRUE.equals(irisInUseMethod.invoke(irisInstance));
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static void reset() {
        impactStartTick = Long.MIN_VALUE;
        impactPeak = 0f;
        impactThreeBeat = false;
        shockStartTick = Long.MIN_VALUE;
        shockPeak = 0f;
        shockFocus = null;
        chromaPeak = 0f;
        smoothedBloom = 0f;
        fovKickTick = Long.MIN_VALUE;
        fovKickPeak = 0f;
        fovThreeBeat = false;
        smoothedFov = 0f;
    }

    public static void reload() {
        Runnable task = () -> {
            if (chain != null) chain.close();
            chain = null;
            unavailable = false;
            var mc = Minecraft.getInstance();
            var main = mc.getMainRenderTarget();
            if (main == null || main.width <= 0 || main.height <= 0) return;

            int readFb = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int drawFb = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int[] vp = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
            try {
                ensureChain(mc, main);
            } catch (Exception ex) {
                unavailable = true;
                LogUtils.getLogger().warn("Could not prepare aca_domain67:domain67_post PostChain", ex);
            } finally {
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFb);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFb);
                RenderSystem.viewport(vp[0], vp[1], vp[2], vp[3]);
            }
        };
        if (RenderSystem.isOnRenderThread()) task.run();
        else RenderSystem.recordRenderCall(task::run);
    }

    public static void onCue(Domain67Network.VfxCuePayload cue, Player viewer, long now) {
        double distSqr = cue.from().distanceToSqr(viewer.position());
        float falloff = cue.actor().equals(viewer.getUUID()) ? 1.0f : (float) Math.max(0.0, 1.0 - Math.sqrt(distSqr) / 96.0);
        if (falloff <= 0.05f) return;

        switch (cue.cueId()) {
            case Domain67Network.CUE_DOMAIN_EXPAND -> {
                triggerImpact(now, 1.0f * falloff, true);
                triggerShock(now + 12L, cue.from().add(0, 1.2, 0), 0.95f * falloff, 24f, 0.85f * falloff);
                triggerFov(now, -9.5f * falloff, true);
            }
            case Domain67Network.CUE_DOMAIN_CLOSE -> {
                triggerShock(now + 8L, cue.from().add(0, 1.2, 0), 0.85f * falloff, 20f, 0.72f * falloff);
                triggerFov(now, 6.5f * falloff, false);
            }
            case Domain67Network.CUE_MEGA_LOG_IMPACT -> {
                triggerImpact(now, 0.92f * falloff, false);
                triggerShock(now, cue.from(), 0.98f * falloff, 18f, 0.88f * falloff);
                triggerFov(now, 6.8f * falloff, false);
            }
            case Domain67Network.CUE_LOG_BARRAGE -> {
                triggerShock(now, cue.to(), 0.74f * falloff, 16f, 0.60f * falloff);
                triggerFov(now, 4.0f * falloff, false);
            }
            case Domain67Network.CUE_GUARD_LOG -> {
                triggerShock(now, cue.from(), 0.58f * falloff, 12f, 0.45f * falloff);
            }
            case Domain67Network.CUE_CIRCUS_BROADCAST, Domain67Network.CUE_BENTO_CAMEO -> {
                triggerShock(now, cue.from(), 0.50f * falloff, 14f, 0.36f * falloff);
            }
            default -> {}
        }
    }

    private static void triggerImpact(long now, float strength, boolean threeBeat) {
        impactStartTick = now;
        impactPeak = Math.max(impactPeak * 0.35f, strength);
        impactThreeBeat = threeBeat;
    }

    private static void triggerShock(long now, Vec3 focus, float strength, float dur, float chroma) {
        shockStartTick = now;
        shockPeak = Math.max(shockPeak * 0.35f, strength);
        shockDuration = dur;
        shockFocus = focus;
        chromaPeak = Math.max(chromaPeak * 0.35f, chroma);
    }

    private static void triggerFov(long now, float deg, boolean threeBeat) {
        fovKickTick = now;
        fovKickPeak = deg;
        fovThreeBeat = threeBeat;
    }

    public static float currentImpactFlashForGui(float partial) {
        return Math.max(0f, computeImpactPulse(Domain67ClientCache.ticks(), partial));
    }

    /** Hijacks Vanilla Sky & Horizon Fog Color to Warm Sunlit Champagne-Ivory inside Restaurant 67! */
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 cam = event.getCamera().getPosition();
        var dom = Domain67ClientCache.primaryDomain(cam);
        if (dom == null) return;

        long now = Domain67ClientCache.ticks();
        float partial = (float) event.getPartialTick();
        double distSq = cam.distanceToSqr(dom.cx, dom.cy, dom.cz);
        if (distSq > (dom.maxRadius + 1.2) * (dom.maxRadius + 1.2)) return;

        float blend = Mth.clamp(dom.currentRadiusFactor(now, partial) * (1.0f - dom.collapseProgress(now, partial)), 0f, 1f);
        if (blend > 0.01f) {
            event.setRed(Mth.lerp(blend, event.getRed(), 0.99f));
            event.setGreen(Mth.lerp(blend, event.getGreen(), 0.95f));
            event.setBlue(Mth.lerp(blend, event.getBlue(), 0.86f));
        }
    }

    /** Pushes vanilla terrain fog out across the expanded 64m arena so stage props stay crystal-clear! */
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 cam = event.getCamera().getPosition();
        var dom = Domain67ClientCache.primaryDomain(cam);
        if (dom == null) return;

        double distSq = cam.distanceToSqr(dom.cx, dom.cy, dom.cz);
        if (distSq <= (dom.maxRadius + 1.2) * (dom.maxRadius + 1.2) && !dom.isCollapsing()) {
            event.setNearPlaneDistance(76.0f);
            event.setFarPlaneDistance(280.0f);
            event.setCanceled(true);
        }
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float fovScale = mc.options.fovEffectScale().get().floatValue();
        if (fovScale <= 0.01f) return;

        float target = 0f;
        if (fovKickTick != Long.MIN_VALUE) {
            float age = (Domain67ClientCache.ticks() - fovKickTick) + (float) event.getPartialTick();
            if (fovThreeBeat) {
                if (age >= 0f && age < 9f) {
                    target = fovKickPeak * (age / 9f) * fovScale;
                } else if (age >= 9f && age < 14.5f) {
                    target = fovKickPeak * fovScale;
                } else if (age >= 14.5f && age <= 26f) {
                    float p = (age - 14.5f) / 11.5f;
                    target = (-fovKickPeak * 0.80f) * Mth.sin(p * (float) Math.PI) * fovScale;
                }
            } else if (age >= 0f && age <= 10f) {
                target = fovKickPeak * Mth.sin((age / 10f) * (float) Math.PI) * fovScale;
            }
        }
        smoothedFov = Mth.lerp(0.26f, smoothedFov, target);
        if (Math.abs(smoothedFov) > 0.04f) {
            event.setFOV(event.getFOV() + smoothedFov);
        }
    }

    private static float computeImpactPulse(long cNow, float partial) {
        if (impactStartTick == Long.MIN_VALUE) return 0f;
        float elapsed = (cNow - impactStartTick) + partial;
        if (impactThreeBeat) {
            if (elapsed >= 0f && elapsed < 9.0f) {
                return -0.92f * impactPeak * (elapsed / 9.0f);
            }
            if (elapsed >= 9.0f && elapsed <= 14.5f) {
                float p = (elapsed - 9.0f) / 5.5f;
                return impactPeak * (1f - p * p * 0.55f);
            }
            if (elapsed > 14.5f && elapsed <= 17.5f) {
                return impactPeak * 0.45f * (1f - (elapsed - 14.5f) / 3.0f);
            }
            return 0f;
        }
        if (elapsed >= 0f && elapsed < 1.2f) return -0.55f * impactPeak * (elapsed / 1.2f);
        if (elapsed >= 1.2f && elapsed <= 5.2f) {
            float p = (elapsed - 1.2f) / 4.0f;
            return impactPeak * (1f - p * p);
        }
        return 0f;
    }

    private static float[] projectUv(RenderLevelStageEvent event, Vec3 cam, Vec3 worldPos) {
        Vec3 rel = worldPos.subtract(cam);
        Vector4f clip = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0f);
        clip.mul(event.getModelViewMatrix());
        clip.mul(event.getProjectionMatrix());
        if (clip.w <= 0.05f) return new float[]{0.5f, 0.5f};
        return new float[]{
                Mth.clamp(clip.x / clip.w * 0.5f + 0.5f, 0.10f, 0.90f),
                Mth.clamp(clip.y / clip.w * 0.5f + 0.5f, 0.10f, 0.90f)
        };
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || unavailable) return;
        if (isIrisShaderActive()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        float screenScale = mc.options.screenEffectScale().get().floatValue();
        if (screenScale <= 0.02f) return;

        long cNow = Domain67ClientCache.ticks();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();

        float shockStr = 0f, shockRing = 0.15f, focusX = 0.5f, focusY = 0.5f, chroma = 0f;
        if (shockStartTick != Long.MIN_VALUE) {
            float elapsed = (cNow - shockStartTick) + partial;
            if (elapsed >= 0f && elapsed <= shockDuration) {
                float p = elapsed / shockDuration;
                float ease = 1f - (1f - p) * (1f - p) * (1f - p);
                shockStr = shockPeak * (1f - p) * screenScale;
                shockRing = 0.04f + ease * 0.92f;
                chroma = chromaPeak * (1f - p) * screenScale;
                if (shockFocus != null) {
                    float[] uv = projectUv(event, cam, shockFocus);
                    focusX = uv[0];
                    focusY = uv[1];
                }
            }
        }

        float impact = computeImpactPulse(cNow, partial) * screenScale;

        var dom = Domain67ClientCache.primaryDomain(cam);
        float domStr = 0f;
        float waveRadius = 0f;
        float shatter = 0f;
        float viewerInside = 0f;
        float urgency = 0f;
        if (dom != null) {
            double viewerDistSq = cam.distanceToSqr(dom.cx, dom.cy, dom.cz);
            boolean inside = viewerDistSq <= (dom.maxRadius + 0.8) * (dom.maxRadius + 0.8);
            viewerInside = inside ? 1.0f : 0.0f;
            waveRadius = dom.worldWaveRadius(cNow, partial);
            shatter = dom.shatterFactor(cNow, partial);
            if (waveRadius > 0.05f && inside) {
                domStr = screenScale;
            }
            if (!dom.isCollapsing() && inside && dom.serverTicksRemaining <= 60) {
                float pulse = 0.5f + 0.5f * Mth.sin((cNow + partial) * 0.45f);
                urgency = pulse * (1f - dom.serverTicksRemaining / 60f) * screenScale;
            }
        }

        float targetBloom = (dom != null && viewerInside > 0.5f) ? 0.38f * screenScale : (shockStr > 0.02f ? 0.40f * screenScale : 0f);
        smoothedBloom = Mth.lerp(0.16f, smoothedBloom, targetBloom);

        if (shockStr <= 0.002f && Math.abs(impact) <= 0.005f && chroma <= 0.002f
                && domStr <= 0.004f && smoothedBloom <= 0.01f && urgency <= 0.004f) {
            return;
        }

        RenderTarget main = mc.getMainRenderTarget();
        try {
            ensureChain(mc, main);
            float pulseSecs = (Math.floorMod(cNow, 960L) + partial) / 20f;
            chain.setUniform("PulseTime", pulseSecs);
            chain.setUniform("ImpactFrame", impact);
            chain.setUniform("ShockStrength", shockStr);
            chain.setUniform("ShockRing", shockRing);
            chain.setUniform("FocusUvX", focusX);
            chain.setUniform("FocusUvY", focusY);
            chain.setUniform("ChromaticBoost", chroma);
            chain.setUniform("BloomStrength", smoothedBloom);
            chain.setUniform("DomainStrength", domStr);
            chain.setUniform("DomainRadius", waveRadius);
            chain.setUniform("StageRadius", (float) Domain67Manager.STAGE_BLOCK_RADIUS + 0.45f);
            chain.setUniform("ShatterProgress", shatter);
            chain.setUniform("ViewerInside", viewerInside);
            chain.setUniform("UrgencyPulse", urgency);

            if (domStr > 0.004f && dom != null) {
                Matrix4f inv = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()).invert();
                float[] m = inv.get(new float[16]);
                for (int i = 0; i < 16; i++) chain.setUniform("Inv" + i, m[i]);
                Vec3 relC = new Vec3(dom.cx, dom.cy, dom.cz).subtract(cam);
                chain.setUniform("FieldCenterX", (float) relC.x);
                chain.setUniform("FieldCenterY", (float) relC.y);
                chain.setUniform("FieldCenterZ", (float) relC.z);
            }

            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            chain.process(event.getPartialTick().getGameTimeDeltaTicks());
            RenderTarget out = chain.getTempTarget("domain67_color");
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, out.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        } catch (Exception ex) {
            LogUtils.getLogger().warn("Disabling aca_domain67:domain67_post after runtime exception", ex);
            unavailable = true;
        } finally {
            main.bindWrite(true);
            RenderSystem.enableDepthTest();
        }
    }

    private static void ensureChain(Minecraft mc, RenderTarget main) throws IOException {
        if (chain == null || boundTarget != main) {
            if (chain != null) chain.close();
            chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main,
                    AcaDomain67.id("shaders/post/domain67_post.json"));
            boundTarget = main;
            cachedW = 0;
            cachedH = 0;
        }
        if (cachedW != main.width || cachedH != main.height) {
            cachedW = main.width;
            cachedH = main.height;
            chain.resize(cachedW, cachedH);
        }
    }
}
