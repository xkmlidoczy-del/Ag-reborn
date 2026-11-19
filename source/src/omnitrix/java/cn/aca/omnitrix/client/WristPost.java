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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

final class WristPost {
    private record Field(UUID actor, Vec3 center, long until) {}
    private static long shakeTick = Long.MIN_VALUE;
    private static float shakeTrauma;
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
    private static long lastArmingRefractBegan = Long.MIN_VALUE;

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

    private static void addTrauma(long now, float impulse) {
        float decayed = currentTrauma(now, 0f);
        shakeTick = now;
        shakeTrauma = Math.min(1.55f, Math.max(decayed + impulse * .65f, impulse));
    }

    static void effect(WristNetwork.Cue cue, Player viewer, long now) {
        double distSqr = cue.to().distanceToSqr(viewer.position());
        boolean selfCast = cue.actor().equals(viewer.getUUID());

        // 1. Ascalon Sakuga Impact Frames + Screen-Space Air-Density Shockwave Refraction
        if (!ClientState.reduced() && (selfCast || distSqr < 56 * 56)) {
            float falloff = selfCast ? 1f : (float) Math.max(0.0, 1.0 - Math.sqrt(distSqr) / 56.0);
            switch (cue.action()) {
                case WristNetwork.ASCALON_SYZYGY_CUE -> {
                    syzygyFlashTick = now;
                    syzygyFlashPeak = (cue.beat() > 0 ? 1.0f : .78f) * falloff;
                    sakugaTick = now;
                    sakugaPeak = 1.0f * falloff;
                    triggerShockRefraction(now, cue.from().add(0, 1.4, 0), 1.0f * falloff, 26f);
                    addTrauma(now, 1.45f * falloff);
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
                    addTrauma(now, (.32f + tier * .22f) * falloff);
                    return;
                }
                case WristNetwork.ASCALON_SWING_CUE -> {
                    if (cue.beat() == 4 || cue.beat() == 5) {
                        sakugaTick = now;
                        sakugaPeak = (cue.beat() == 5 ? 0.92f : 0.86f) * falloff;
                        triggerShockRefraction(now, cue.from().lerp(cue.to(), 0.35), 0.88f * falloff, 16f);
                        syzygyFlashTick = now;
                        syzygyFlashPeak = Math.max(syzygyFlashPeak * .5f, 0.48f * falloff);
                        addTrauma(now, 1.12f * falloff);
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
                    addTrauma(now, (stage2 ? 1.08f : .76f) * falloff);
                    return;
                }
                case WristNetwork.ASCALON_ARMOR_CUE -> {
                    if (cue.beat() == 1) {
                        triggerShockRefraction(now, cue.from().add(0, 1.1, 0), 0.62f * falloff, 15f);
                        addTrauma(now, .88f * falloff);
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
                case WristNetwork.SINGULARITY_CUE, WristNetwork.EXTINCTION_CUE, WristNetwork.REWRITE_CUE -> {
                    sakugaTick = now;
                    sakugaPeak = .88f * falloff;
                    syzygyFlashTick = now;
                    syzygyFlashPeak = Math.max(syzygyFlashPeak * .5f, .55f * falloff);
                    triggerShockRefraction(now, cue.to(), .92f * falloff, 20f);
                    addTrauma(now, 1.42f * falloff);
                    return;
                }
                case WristNetwork.CRYSTAL_SHELL_BREAK_CUE -> {
                    triggerShockRefraction(now, cue.from().add(0, 1.0, 0), .52f * falloff, 12f);
                    addTrauma(now, .68f * falloff);
                    return;
                }
                default -> {}
            }
        }

        // 2. Omnitrix Heavy Skill Air-Density Shockwave Refraction & Squared-Trauma Camera Punch
        if (!ClientState.reduced() && (selfCast || distSqr < 32 * 32)) {
            float falloff = selfCast ? 1f : (float) Math.max(0.0, 1.0 - Math.sqrt(distSqr) / 32.0);
            int rawAction = cue.action();
            Form form = rawAction < WristNetwork.FAILSAFE_CUE ? Form.at(rawAction % 32) : null;
            boolean secondary = rawAction >= Abilities.SECONDARY && rawAction < Abilities.AUTOMATED;

            if (form == Form.ALIEN_X) {
                triggerShockRefraction(now, cue.to(), (secondary ? .88f : .68f) * falloff, secondary ? 18f : 15f);
                if (secondary) {
                    sakugaTick = now;
                    sakugaPeak = .76f * falloff;
                }
            } else if (form == Form.FOUR_ARMS && (secondary || cue.beat() >= 2)) {
                triggerShockRefraction(now, cue.to(), (secondary ? .74f : .50f) * falloff, secondary ? 15f : 11f);
                if (secondary && cue.hitEntity() && selfCast) {
                    sakugaTick = now;
                    sakugaPeak = .56f;
                }
            } else if (form == Form.HEATBLAST && secondary && cue.linked()) {
                triggerShockRefraction(now, cue.to(), .52f * falloff, 12f);
            } else if (form == Form.XLR8 && !secondary && selfCast) {
                triggerShockRefraction(now, cue.from().lerp(cue.to(), .6), .38f, 9f);
            }
        }

        // 3. Damped squared-trauma hit-confirm camera punch ONLY on confirmed server hits / ground slams
        if (!ClientState.reduced() && (cue.hitEntity() || cue.hitGround())) {
            if (selfCast || distSqr < 24 * 24) {
                float falloff = selfCast ? 1f : (float) Math.max(0, 1.0 - Math.sqrt(distSqr) / 24.0);
                int rawForm = cue.action() % 32;
                float traumaImpulse = (cue.action() == Abilities.SECONDARY + Form.ALIEN_X.ordinal()) ? 1.38f
                        : cue.action() == WristNetwork.ASCALON_CLEAVE_HIT_CUE ? .82f
                        : cue.action() == Abilities.SECONDARY + Form.FOUR_ARMS.ordinal() ? 1.12f
                        : rawForm == Form.FOUR_ARMS.ordinal() ? (cue.beat() >= 2 ? .88f : .68f)
                        : rawForm == Form.RIPJAWS.ordinal() || rawForm == Form.WILDMUTT.ordinal() ? .62f
                        : .46f;
                addTrauma(now, traumaImpulse * falloff);
            }
        }
    }

    static void reset() {
        shakeTick = Long.MIN_VALUE;
        shakeTrauma = 0;
        syzygyFlashTick = Long.MIN_VALUE;
        syzygyFlashPeak = 0;
        refractTick = Long.MIN_VALUE;
        refractPeak = 0;
        refractWorldFocus = null;
        sakugaTick = Long.MIN_VALUE;
        sakugaPeak = 0;
        smoothedFovBoost = 0f;
        lastArmingRefractBegan = Long.MIN_VALUE;
    }

    static void reload() {
        Runnable clear = () -> {
            if (chain != null) chain.close();
            chain = null;
            unavailable = false;
            var mc = Minecraft.getInstance();
            var main = mc.getMainRenderTarget();
            if (main == null || main.width == 0 || main.height == 0) return;

            int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            try {
                prepare(mc, main);
            } catch (Exception ex) {
                unavailable = true;
                LogUtils.getLogger().warn("Could not prepare the Omnitrix post effect", ex);
            } finally {
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
                RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            }
        };
        if (RenderSystem.isOnRenderThread()) clear.run();
        else RenderSystem.recordRenderCall(clear::run);
    }

    static float impact(ClientState.View view, float partial) {
        if (view.s().phase() != Stage.ARMING) return 0;
        float key = switch (view.s().watch()) {
            case CLASSIC -> 5.8f;
            case CARNITRIX -> 8f;
            case CELESTIAL -> 38f;
        };
        float t = view.age(partial) - key;
        return t < 0 || t > 4.4f ? 0 : (float) Math.sin(t * Math.PI / 4.4f);
    }

    private static float currentTrauma(long now, float partial) {
        if (shakeTick == Long.MIN_VALUE) return 0f;
        float elapsed = (now - shakeTick) + partial;
        if (elapsed < 0 || elapsed > 7.5f) return 0f;
        return Math.max(0f, shakeTrauma * (1f - elapsed / 7.5f));
    }

    /** Non-linear squared-trauma camera shake: sharp crisp punch at t=0, silky organic settle! */
    private static float hitShake(long now, float partial) {
        float tr = currentTrauma(now, partial);
        if (tr <= .005f) return 0f;
        float elapsed = (now - shakeTick) + partial;
        float mag = tr * tr;
        return mag * (Mth.sin(elapsed * 2.9f) * .72f + Mth.cos(elapsed * 4.7f) * .28f);
    }

    private static float syzygyWave(long now, float partial) {
        if (syzygyFlashTick == Long.MIN_VALUE) return 0;
        float elapsed = (now - syzygyFlashTick) + partial;
        float dur = syzygyFlashPeak > .7f ? 28f : 12f;
        if (elapsed < 0 || elapsed > dur) return 0;
        return syzygyFlashPeak * (1f - elapsed / dur);
    }

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
            event.setPitch(event.getPitch() + bump * (carni ? 2.4f : 1.75f) * scale);
            event.setRoll(event.getRoll() + Mth.sin(view.age(partial) * 2.2f) * bump * (carni ? 2.1f : 1.15f) * scale);
        }
        long now = mc.level.getGameTime();
        float combatShake = hitShake(now, partial);
        if (Math.abs(combatShake) > .008f) {
            float elapsed = (now - shakeTick) + partial;
            event.setPitch(event.getPitch() + combatShake * 1.15f * scale);
            event.setYaw(event.getYaw() + combatShake * Mth.sin(elapsed * 3.7f) * .45f * scale);
            event.setRoll(event.getRoll() + combatShake * .68f * scale);
        }
        if (mc.player != null) {
            float flightBank = AscalonRenderer.cameraBankRoll(mc.player, partial);
            if (Math.abs(flightBank) > .02f) {
                event.setRoll(event.getRoll() + flightBank * .28f * scale);
            }
            // Subtle aerodynamic bank roll for XLR8 high-speed turns!
            if (view != null && view.s().morphed() && view.s().chosen() == Form.XLR8 && mc.player.isSprinting()) {
                float yawDelta = Mth.wrapDegrees(mc.player.getViewYRot(partial) - mc.player.yRotO);
                event.setRoll(event.getRoll() + Mth.clamp(-yawDelta * .24f, -3.5f, 3.5f) * scale);
            }
        }
    }

    static void fov(ViewportEvent.ComputeFov event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || ClientState.reduced() || mc.getCameraEntity() != mc.player) return;
        float partial = (float) event.getPartialTick();
        float fovScale = mc.options.fovEffectScale().get().floatValue();
        float targetDelta = 0f;

        // 1. Ascalon Iaido tension & Viltrumite flight FOV
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

        // 2. Omnitrix Transformation Slam Compression -> Burst & Alien Speed/Skill FOV Juice!
        var view = ClientState.self();
        if (view != null) {
            Stage phase = view.s().phase();
            float age = view.age(partial);
            if (phase == Stage.ARMING) {
                float key = view.s().watch() == Kind.CELESTIAL ? 38f : 5.8f;
                if (age >= key - 3.2f && age < key) {
                    // Pre-slam anticipation zoom-in tension
                    float c = (age - (key - 3.2f)) / 3.2f;
                    targetDelta -= c * 3.0f * fovScale;
                } else if (age >= key && age < key + 8.5f) {
                    // Transformation DNA burst FOV kick!
                    float p = (age - key) / 8.5f;
                    targetDelta += (float) Math.sin(p * Math.PI) * 6.2f * fovScale;
                }
            } else if (view.s().morphed()) {
                Form form = view.s().chosen();
                boolean star = view.s().watch() == Kind.CELESTIAL;
                double horizSpd = mc.player.getDeltaMovement().horizontalDistance();
                var perf = view.performance(partial);

                if (form == Form.XLR8) {
                    float spdRatio = Mth.clamp((float) ((horizSpd - .18) / (star ? 1.45 : 1.15)), 0f, 1f);
                    targetDelta += spdRatio * (star ? 10.5f : 8.2f) * fovScale;
                    if (perf != null && perf.action() >= Abilities.SECONDARY) {
                        // Instant drift-brake G-force FOV snap!
                        targetDelta -= 3.6f * fovScale;
                    }
                } else if (form == Form.STINKFLY || form == Form.WILDMUTT || form == Form.RIPJAWS) {
                    float lungeRatio = Mth.clamp((float) ((horizSpd - .24) / .85), 0f, 1f);
                    targetDelta += lungeRatio * 5.8f * fovScale;
                } else if ((form == Form.FOUR_ARMS || form == Form.ALIEN_X) && perf != null) {
                    double now = view.now(partial);
                    if (perf.action() >= Abilities.SECONDARY && now < perf.impact()) {
                        // Heavy slam / supernova windup tension!
                        float gather = (float) Mth.clamp((now - perf.start()) / Math.max(1.0, perf.impact() - perf.start()), 0.0, 1.0);
                        targetDelta -= gather * 3.2f * fovScale;
                    } else if (now >= perf.impact() && now < perf.impact() + 5.0) {
                        targetDelta += (float) (1.0 - (now - perf.impact()) / 5.0) * 4.5f * fovScale;
                    }
                }
            }
        }

        smoothedFovBoost = Mth.lerp(.22f, smoothedFovBoost, targetDelta);
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

        // Trigger crisp screen-space shockwave refraction right at the watch-slam transformation flash!
        if (view != null && !ClientState.reduced() && view.s().phase() == Stage.ARMING) {
            float key = view.s().watch() == Kind.CELESTIAL ? 38f : 5.8f;
            float age = view.age(partial);
            if (age >= key && age < key + 2f && lastArmingRefractBegan != view.s().began()) {
                lastArmingRefractBegan = view.s().began();
                triggerShockRefraction(now, mc.player.getEyePosition().add(mc.player.getLookAngle().scale(1.2)),
                        view.s().watch() == Kind.CELESTIAL ? .72f : .48f, 13f);
            }
        }

        if (!ClientState.reduced()) {
            hit = Math.max(hit, Math.min(.34f, Math.abs(hitShake(now, partial)) * .18f));
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
            float seconds = (ClientState.clientTicks() % 480 + partial) / 20f;
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
