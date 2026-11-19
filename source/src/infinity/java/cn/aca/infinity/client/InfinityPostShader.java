package cn.aca.infinity.client;

import cn.aca.infinity.AcaInfinity;
import cn.aca.infinity.InfinityNetwork;
import cn.aca.infinity.SanctuaryShipEntity;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class InfinityPostShader {
    private static PostChain chain;
    private static RenderTarget boundTarget;
    private static int cachedW, cachedH;
    private static boolean unavailable;

    private static long refractStartTick = Long.MIN_VALUE;
    private static float refractPeak = 0f;
    private static float refractDuration = 16f;
    private static Vec3 refractWorldFocus = null;
    private static long sakugaStartTick = Long.MIN_VALUE;
    private static float sakugaPeak = 0f;
    private static float chromaPeak = 0f;
    private static float smoothedBloom = 0f;

    private static Vec3 activeTimeCenter = null;
    private static long activeTimeStart = Long.MIN_VALUE;
    private static long activeTimeUntil = Long.MIN_VALUE;
    private static float activeTimeRadius = 18.0f;

    private static float smoothedFovOffset = 0f;
    private static long fovImpulseTick = Long.MIN_VALUE;
    private static float fovImpulsePeak = 0f;

    private InfinityPostShader() {}

    public static void reset() {
        refractStartTick = Long.MIN_VALUE;
        refractPeak = 0f;
        refractWorldFocus = null;
        sakugaStartTick = Long.MIN_VALUE;
        sakugaPeak = 0f;
        chromaPeak = 0f;
        smoothedBloom = 0f;
        activeTimeCenter = null;
        activeTimeStart = Long.MIN_VALUE;
        activeTimeUntil = Long.MIN_VALUE;
        smoothedFovOffset = 0f;
        fovImpulseTick = Long.MIN_VALUE;
        fovImpulsePeak = 0f;
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
                LogUtils.getLogger().warn("Could not prepare aca_infinity:infinity_post PostChain", ex);
            } finally {
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFb);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFb);
                RenderSystem.viewport(vp[0], vp[1], vp[2], vp[3]);
            }
        };
        if (RenderSystem.isOnRenderThread()) task.run();
        else RenderSystem.recordRenderCall(task::run);
    }

    public static void onCue(InfinityNetwork.VfxCuePayload cue, Player viewer, long now) {
        boolean self = cue.actor().equals(viewer.getUUID());
        double distSqr = cue.from().distanceToSqr(viewer.position());
        float falloff = self ? 1.0f : (float) Math.max(0.0, 1.0 - Math.sqrt(distSqr) / 64.0);
        if (falloff <= 0.04f && cue.cueId() != InfinityNetwork.CUE_TIME_DOME) return;

        switch (cue.cueId()) {
            case InfinityNetwork.CUE_SNAP -> {
                triggerRefraction(now, cue.from(), 1.0f * falloff, 28f, 0.95f * falloff, 0.90f * falloff);
                triggerFovKick(now, 6.5f * falloff);
            }
            case InfinityNetwork.CUE_TIME_DOME -> {
                if (distSqr <= 72 * 72) {
                    activeTimeCenter = cue.from();
                    activeTimeStart = now;
                    activeTimeUntil = now + Math.max(40, cue.extra());
                    activeTimeRadius = 18.0f;
                    triggerRefraction(now, cue.from().add(0, 1.0, 0), 0.72f * Math.max(0.35f, falloff), 18f, 0.42f * falloff, 0.55f * falloff);
                }
            }
            case InfinityNetwork.CUE_COMBO_SINGULARITY, InfinityNetwork.CUE_SPACE_DECOY_BURST -> {
                Vec3 focus = cue.cueId() == InfinityNetwork.CUE_SPACE_DECOY_BURST ? cue.from() : cue.to();
                triggerRefraction(now, focus, 0.88f * falloff, 18f, 0.78f * falloff, 0.72f * falloff);
                triggerFovKick(now, -4.2f * falloff);
            }
            case InfinityNetwork.CUE_COMBO_TEMPORAL_RECKONING -> {
                triggerRefraction(now, cue.from(), 0.86f * falloff, 18f, 0.80f * falloff, 0.72f * falloff);
                triggerFovKick(now, 5.2f * falloff);
            }
            case InfinityNetwork.CUE_COMBO_PUBLIC_EXEC -> {
                triggerRefraction(now, cue.from(), 0.68f * falloff, 15f, 0.55f * falloff, 0.48f * falloff);
            }
            case InfinityNetwork.CUE_POWER_WRATH, InfinityNetwork.CUE_POWER_PUNCH -> {
                Vec3 focus = cue.cueId() == InfinityNetwork.CUE_POWER_PUNCH ? cue.from().lerp(cue.to(), 0.45) : cue.from();
                triggerRefraction(now, focus, 0.68f * falloff, 15f, 0.54f * falloff, 0.46f * falloff);
                triggerFovKick(now, 3.8f * falloff);
            }
            case InfinityNetwork.CUE_SPACE_WARP, InfinityNetwork.CUE_REALITY_WAVE -> {
                triggerRefraction(now, cue.to(), 0.48f * falloff, 12f, 0.0f, 0.35f * falloff);
                if (self && cue.cueId() == InfinityNetwork.CUE_SPACE_WARP) triggerFovKick(now, 4.0f);
            }
            case InfinityNetwork.CUE_SHIP_CANNON, InfinityNetwork.CUE_SHIP_SNAP_COLLAPSE, InfinityNetwork.CUE_SHIP_RAIN_FIRE -> {
                Vec3 focus = cue.cueId() == InfinityNetwork.CUE_SHIP_SNAP_COLLAPSE ? cue.from() : cue.to();
                float str = cue.cueId() == InfinityNetwork.CUE_SHIP_SNAP_COLLAPSE ? 0.92f : 0.58f;
                triggerRefraction(now, focus, str * falloff, 20f, str * 0.72f * falloff, 0.62f * falloff);
            }
            default -> {}
        }
    }

    private static void triggerRefraction(long now, Vec3 worldFocus, float strength, float duration, float sakuga, float chroma) {
        refractStartTick = now;
        refractPeak = Math.max(refractPeak * 0.35f, strength);
        refractDuration = duration;
        refractWorldFocus = worldFocus;
        chromaPeak = Math.max(chromaPeak * 0.35f, chroma);
        if (sakuga > 0.05f) {
            sakugaStartTick = now;
            sakugaPeak = Math.max(sakugaPeak * 0.35f, sakuga);
        }
    }

    private static void triggerFovKick(long now, float degrees) {
        fovImpulseTick = now;
        fovImpulsePeak = degrees;
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float partial = (float) event.getPartialTick();
        float fovScale = mc.options.fovEffectScale().get().floatValue();
        if (fovScale <= 0.01f) return;

        long cNow = ClientCache.clientTicks();
        float target = 0f;
        if (fovImpulseTick != Long.MIN_VALUE) {
            float age = (cNow - fovImpulseTick) + partial;
            if (age >= 0f && age <= 9f) {
                target += fovImpulsePeak * Mth.sin((age / 9f) * (float) Math.PI) * fovScale;
            }
        }
        if (mc.player.getVehicle() instanceof SanctuaryShipEntity ship) {
            float spd = Math.abs(ship.flightSpeed());
            target += Mth.clamp((spd - 0.35f) * 8.5f, 0f, 10.5f) * fovScale;
        }
        smoothedFovOffset = Mth.lerp(0.24f, smoothedFovOffset, target);
        if (Math.abs(smoothedFovOffset) > 0.04f) {
            event.setFOV(event.getFOV() + smoothedFovOffset);
        }
    }

    private static float computeSakugaPulse(long cNow, float partial) {
        if (sakugaStartTick == Long.MIN_VALUE) return 0f;
        float elapsed = (cNow - sakugaStartTick) + partial;
        if (elapsed >= 0f && elapsed < 0.42f) {
            return -0.50f * sakugaPeak;
        }
        if (elapsed >= 0.42f && elapsed <= 2.1f) {
            float p = (elapsed - 0.42f) / 1.68f;
            return sakugaPeak * (1f - p * p);
        }
        return 0f;
    }

    private static float[] projectWorldToUv(RenderLevelStageEvent event, Vec3 cam, Vec3 worldPos) {
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
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;

        float screenScale = mc.options.screenEffectScale().get().floatValue();
        if (screenScale <= 0.02f) return;

        long cNow = ClientCache.clientTicks();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();

        float refractStr = 0f;
        float refractRing = 0.2f;
        float focusX = 0.5f, focusY = 0.5f;
        float chroma = 0f;

        if (refractStartTick != Long.MIN_VALUE) {
            float elapsed = (cNow - refractStartTick) + partial;
            if (elapsed >= 0f && elapsed <= refractDuration) {
                float p = elapsed / refractDuration;
                float ease = 1f - (1f - p) * (1f - p) * (1f - p);
                refractStr = refractPeak * (1f - p) * screenScale;
                refractRing = 0.04f + ease * 0.92f;
                chroma = chromaPeak * (1f - p) * screenScale;
                if (refractWorldFocus != null) {
                    float[] uv = projectWorldToUv(event, cam, refractWorldFocus);
                    focusX = uv[0];
                    focusY = uv[1];
                }
            }
        }

        float sakuga = computeSakugaPulse(cNow, partial) * screenScale;

        float timeFieldStr = 0f;
        if (activeTimeCenter != null && activeTimeUntil > cNow && activeTimeCenter.distanceToSqr(cam) < 76 * 76) {
            float inAlpha = Mth.clamp(((cNow - activeTimeStart) + partial) / 6f, 0f, 1f);
            float outAlpha = Mth.clamp(((activeTimeUntil - cNow) - partial) / 10f, 0f, 1f);
            timeFieldStr = Math.min(inAlpha, outAlpha) * screenScale;
        }

        // Soft Highlight Bloom activates smoothly whenever VFX cues, wheel hologram, or gauntlet resonance are active
        boolean hasLiveCues = !ClientCache.activeCues().isEmpty();
        var self = ClientCache.self();
        boolean wearingOrShip = (self != null && self.snapshot().equipped()) || (mc.player.getVehicle() instanceof SanctuaryShipEntity);
        float targetBloom = 0f;
        if (hasLiveCues) {
            targetBloom = Math.max(0.48f, refractStr * 0.72f) * screenScale;
        } else if (InfinityWheelScreen.isOpen()) {
            targetBloom = 0.36f * screenScale;
        } else if (wearingOrShip) {
            targetBloom = 0.22f * screenScale;
        }
        smoothedBloom = Mth.lerp(0.18f, smoothedBloom, targetBloom);

        if (refractStr <= 0.002f && Math.abs(sakuga) <= 0.005f && chroma <= 0.002f
                && timeFieldStr <= 0.004f && smoothedBloom <= 0.012f) {
            return;
        }

        RenderTarget main = mc.getMainRenderTarget();
        try {
            ensureChain(mc, main);
            float pulseSecs = (Math.floorMod(cNow, 960L) + partial) / 20f;
            chain.setUniform("PulseTime", pulseSecs);
            chain.setUniform("RefractStrength", refractStr);
            chain.setUniform("RefractRing", refractRing);
            chain.setUniform("FocusUvX", focusX);
            chain.setUniform("FocusUvY", focusY);
            chain.setUniform("SakugaFlash", sakuga);
            chain.setUniform("ChromaticBoost", chroma);
            chain.setUniform("BloomStrength", smoothedBloom);
            chain.setUniform("TimeFieldStrength", timeFieldStr);
            chain.setUniform("TimeFieldRadius", activeTimeRadius);

            if (timeFieldStr > 0.004f && activeTimeCenter != null) {
                Matrix4f inv = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()).invert();
                float[] m = inv.get(new float[16]);
                for (int i = 0; i < 16; i++) chain.setUniform("Inv" + i, m[i]);
                Vec3 relC = activeTimeCenter.subtract(cam);
                chain.setUniform("FieldCenterX", (float) relC.x);
                chain.setUniform("FieldCenterY", (float) relC.y);
                chain.setUniform("FieldCenterZ", (float) relC.z);
            }

            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            chain.process(event.getPartialTick().getGameTimeDeltaTicks());
            RenderTarget out = chain.getTempTarget("infinity_color");
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, out.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, main.width, main.height,
                    GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
        } catch (Exception ex) {
            LogUtils.getLogger().warn("Disabling aca_infinity:infinity_post after runtime exception", ex);
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
                    AcaInfinity.id("shaders/post/infinity_post.json"));
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
