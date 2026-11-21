package cn.aca.infinity.client;

import cn.aca.infinity.InfinityNetwork;
import cn.aca.infinity.SanctuaryShipEntity;
import cn.aca.infinity.Stone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class CosmicVfxRenderer {
    private record HukouCandidate(Entity entity, ClientCache.MarkView mv,
                                  double rx, double ry, double rz, double distSqr, double viewDot) {}
    private record ScreenRect(float cx, float cy, float halfW, float halfH) {
        boolean overlaps(float ox, float oy, float ow, float oh) {
            return Math.abs(cx - ox) < (halfW + ow) && Math.abs(cy - oy) < (halfH + oh);
        }
    }

    private static MultiBufferSource.BufferSource effectBuffers;

    private CosmicVfxRenderer() {}

    public static void onRenderNameplate(RenderNameTagEvent event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var mv = ClientCache.getMark(event.getEntity());
        if (mv != null && mv.hasHukou(mc.level.getGameTime())) {
            event.setCanRender(net.neoforged.neoforge.common.util.TriState.FALSE);
        }
    }

    public static void onCameraShake(ViewportEvent.ComputeCameraAngles event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float partial = (float) event.getPartialTick();
        long cNow = ClientCache.clientTicks();
        float shake = ClientCache.currentShake(cNow, partial);
        if (shake > 0.008f) {
            float scale = mc.options.screenEffectScale().get().floatValue();
            float t = (cNow + partial) * 2.8f;
            event.setPitch(event.getPitch() + (Mth.sin(t) * 0.72f + Mth.cos(t * 1.7f) * 0.28f) * shake * scale);
            event.setRoll(event.getRoll() + Mth.cos(t * 1.35f) * shake * 0.65f * scale);
        }
    }

    public static void renderScreenOverlay(GuiGraphics g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        long cNow = ClientCache.clientTicks();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float screenScale = mc.options.screenEffectScale().get().floatValue();
        if (screenScale <= 0.01f) return;

        long snapTick = ClientCache.snapFlashTick();
        float age = (cNow - snapTick) + partial;
        if (age >= 0f && age <= 32f) {
            int w = g.guiWidth();
            int h = g.guiHeight();
            if (age < 8f) {
                float p = 1f - age / 8f;
                int flashAlpha = Math.round(p * p * 135f * screenScale);
                g.fill(0, 0, w, h, (flashAlpha << 24) | 0xfff8e7);
            }
            float rimP = 1f - age / 32f;
            int rimA = Math.round(rimP * 84f * screenScale);
            int[] cols = {Stone.SPACE.color, Stone.REALITY.color, Stone.POWER.color,
                    Stone.MIND.color, Stone.TIME.color, Stone.SOUL.color};
            int cTop = (rimA << 24) | (cols[((int) age) % 6] & 0xffffff);
            int cBot = (rimA << 24) | (cols[(((int) age) + 3) % 6] & 0xffffff);
            g.fillGradient(0, 0, w, 18, cTop, 0x00000000);
            g.fillGradient(0, h - 18, w, h, 0x00000000, cBot);
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !ClientCache.hasWorldEffects()) return;
        if (effectBuffers == null) effectBuffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        var modelView = RenderSystem.getModelViewStack();
        float[] color = RenderSystem.getShaderColor().clone();
        modelView.pushMatrix();
        try {
            modelView.set(event.getModelViewMatrix());
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            renderEffects(mc, event, effectBuffers);
        } finally {
            try {
                effectBuffers.endBatch();
            } finally {
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            }
        }
    }

    private static void renderEffects(Minecraft mc, RenderLevelStageEvent event, MultiBufferSource.BufferSource buffers) {
        long now = mc.level.getGameTime();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float time = now + partial;
        InfinityCosmicShader.updateUniforms(partial);

        Vec3 cam = event.getCamera().getPosition();
        Vec3 camLook = new Vec3(event.getCamera().getLookVector());
        PoseStack pose = event.getPoseStack();

        List<HukouCandidate> hukouList = new ArrayList<>();
        List<HukouCandidate> gawkList = new ArrayList<>();
        List<HukouCandidate> puppetList = new ArrayList<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!entity.isAlive()) continue;
            double distSqr = entity.distanceToSqr(cam);
            if (distSqr > 64 * 64) continue;
            var mv = ClientCache.getMark(entity);
            if (mv == null) continue;

            double ex = Mth.lerp(partial, entity.xOld, entity.getX()) - cam.x;
            double ey = Mth.lerp(partial, entity.yOld, entity.getY()) - cam.y;
            double ez = Mth.lerp(partial, entity.zOld, entity.getZ()) - cam.z;

            if (mv.hasHukou(now)) {
                Vec3 toCenter = new Vec3(ex, ey + entity.getBbHeight() * 0.65, ez);
                double len = Math.max(1e-4, toCenter.length());
                double dot = toCenter.scale(1.0 / len).dot(camLook);
                hukouList.add(new HukouCandidate(entity, mv, ex, ey, ez, distSqr, dot));
            }
            if (mv.isTimeFrozen(now)) {
                pose.pushPose();
                pose.translate(ex, ey + entity.getBbHeight() * 0.48, ez);
                InfinityVfxPrimitives.voronoiShatterCrown(pose, buffers, 5, 0.82f, 0.24f, 0.55f,
                        0xd82eff8a, time * 0.8f, 0.08f);
                if (mv.frozenDamagePool() > 0.5f) {
                    float debtScale = Math.min(1.65f, 1.02f + mv.frozenDamagePool() * 0.006f);
                    InfinityVfxPrimitives.armillaryRing(pose, buffers, debtScale, debtScale + 0.22f, 24, 0xeeffd666, 30f, 0.0f);
                }
                pose.popPose();
            }
            if (mv.isSpaceTethered(now)) {
                pose.pushPose();
                pose.translate(ex, ey + entity.getBbHeight() * 0.45, ez);
                InfinityVfxPrimitives.voronoiShatterCrown(pose, buffers, 4, 0.72f, 0.20f, 0.45f,
                        0xd83ca8ff, -time * 4.5f, 0.14f);
                pose.popPose();
            }
            if (mv.isCrystalSnared(now)) {
                pose.pushPose();
                pose.translate(ex, ey + 0.02, ez);
                float cr = Math.max(0.45f, entity.getBbWidth() * 0.55f);
                InfinityVfxPrimitives.eruptingCrystalCluster(pose, buffers, 6, cr, 0.95f, 0.14f, 22f,
                        0xeeff385c, entity.getId() * 29f, 0.06f);
                pose.popPose();
            }
            if (mv.isMindControlled(now)) {
                pose.pushPose();
                pose.translate(ex, ey + entity.getBbHeight() + 0.36, ez);
                InfinityVfxPrimitives.puppetCrown(pose, buffers, Math.max(0.48f, entity.getBbWidth() * 0.62f), time);
                pose.popPose();
                puppetList.add(new HukouCandidate(entity, mv, ex, ey, ez, distSqr, 0.0));
            } else if (mv.isStandingAttention(now)) {
                pose.pushPose();
                pose.translate(ex, ey + entity.getBbHeight() + 0.24, ez);
                InfinityVfxPrimitives.armillaryRing(pose, buffers, 0.42f, 0.64f, 24, 0xeeffd63a, time * 4.0f, 0.08f);
                pose.popPose();
            } else if (mv.isGawking(now) && !mv.hasHukou(now)) {
                gawkList.add(new HukouCandidate(entity, mv, ex, ey, ez, distSqr, 0.0));
            }
        }

        List<ScreenRect> placedRects = new ArrayList<>();
        if (!hukouList.isEmpty()) {
            Entity primaryFocus = selectPrimaryFocus(hukouList);
            hukouList.sort(Comparator.comparingDouble(HukouCandidate::distSqr));

            for (HukouCandidate hc : hukouList) {
                boolean isPrimary = (hc.entity == primaryFocus) || (hc.entity instanceof SanctuaryShipEntity);
                renderDeOverlappedHukouPlaque(mc, event, pose, buffers, hc, isPrimary, placedRects, now, partial);
            }
        }
        for (HukouCandidate pc : puppetList) {
            renderSmallOverheadBadge(mc, event, pose, buffers, pc.rx, pc.ry + pc.entity.getBbHeight() + 0.68, pc.rz,
                    "✦ 精神傀儡 · 倒戈中", 0xffffea78, placedRects);
        }
        for (HukouCandidate gc : gawkList) {
            renderSmallOverheadBadge(mc, event, pose, buffers, gc.rx, gc.ry + gc.entity.getBbHeight() + 0.46, gc.rz,
                    "？吃瓜围观", 0xffffe580, placedRects);
        }

        long cNow = ClientCache.clientTicks();
        for (var active : ClientCache.activeCues()) {
            var cue = active.cue();
            float age = (cNow - active.startClientTick()) + partial;
            float p = Mth.clamp(age / active.durationTicks(), 0f, 1f);
            renderCueVfx4(pose, buffers, cam, cue, age, p);
        }

    }

    private static Entity selectPrimaryFocus(List<HukouCandidate> list) {
        HukouCandidate best = null;
        double bestScore = -999.0;
        for (HukouCandidate c : list) {
            if (c.viewDot < 0.72) continue;
            double dist = Math.sqrt(c.distSqr);
            double score = c.viewDot * 4.0 - dist * 0.035;
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        if (best != null) return best.entity;
        return list.stream().min(Comparator.comparingDouble(HukouCandidate::distSqr)).map(HukouCandidate::entity).orElse(null);
    }

    private static float[] projectRelToNdc(RenderLevelStageEvent event, float rx, float ry, float rz) {
        Vector4f v = new Vector4f(rx, ry, rz, 1.0f);
        v.mul(event.getModelViewMatrix());
        v.mul(event.getProjectionMatrix());
        if (v.w <= 0.05f) return null;
        return new float[]{v.x / v.w, v.y / v.w};
    }

    private static void renderDeOverlappedHukouPlaque(Minecraft mc, RenderLevelStageEvent event,
                                                      PoseStack pose, MultiBufferSource buffers,
                                                      HukouCandidate hc, boolean fullDetail,
                                                      List<ScreenRect> placedRects, long now, float partial) {
        Entity entity = hc.entity;
        ClientCache.MarkView mv = hc.mv;
        Font font = mc.font;
        boolean ship = entity instanceof SanctuaryShipEntity;

        float baseOffset = entity.getBbHeight() + (ship ? 2.3f : (fullDetail ? 0.76f : 0.64f))
                + Mth.sin((now + partial) * 0.11f + entity.getId()) * 0.035f;
        float rectHalfW = ship ? 0.30f : (fullDetail ? 0.25f : 0.17f);
        float rectHalfH = ship ? 0.14f : (fullDetail ? 0.155f : 0.065f);

        float chosenY = (float) hc.ry + baseOffset;
        for (int tier = 0; tier < 6; tier++) {
            float testY = (float) hc.ry + baseOffset + tier * (ship ? 1.2f : 0.60f);
            float[] ndc = projectRelToNdc(event, (float) hc.rx, testY, (float) hc.rz);
            if (ndc == null) {
                chosenY = testY;
                break;
            }
            boolean collision = false;
            for (ScreenRect sr : placedRects) {
                if (sr.overlaps(ndc[0], ndc[1], rectHalfW, rectHalfH)) {
                    collision = true;
                    break;
                }
            }
            if (!collision || tier == 5) {
                chosenY = testY;
                placedRects.add(new ScreenRect(ndc[0], ndc[1], rectHalfW, rectHalfH));
                break;
            }
        }

        pose.pushPose();
        pose.translate(hc.rx, chosenY, hc.rz);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());

        if (fullDetail) {
            float textScale = ship ? 0.064f : 0.0235f;
            pose.scale(textScale, -textScale, textScale);
            Matrix4f mat = pose.last().pose();

            String statusBadge = mv.isAshamed(now)
                    ? "【户籍核验 · 社死禁伤】"
                    : mv.isParentSlowed(now)
                    ? "【已叫家长 · 转身逃跑】"
                    : "【教务处 · 户籍核验】";
            String mainLine = "父：" + mv.mark().fatherName() + "   ✦   母：" + mv.mark().motherName();

            int w1 = font.width(statusBadge);
            int w2 = font.width(mainLine);
            int halfW = Math.max(w1, w2) / 2 + 9;
            int topY = -15;
            int botY = 14;

            VertexConsumer guiVc = buffers.getBuffer(RenderType.textBackgroundSeeThrough());
            fillQuad(guiVc, mat, -halfW, topY, halfW, botY, -0.03f, 0xb8090c16);
            fillQuad(guiVc, mat, -halfW, topY, halfW, topY + 1.2f, -0.02f, 0xffff9428);

            float hpRatio = 1.0f;
            if (entity instanceof LivingEntity le && le.getMaxHealth() > 0f) {
                hpRatio = Mth.clamp(le.getHealth() / le.getMaxHealth(), 0f, 1f);
            } else if (entity instanceof SanctuaryShipEntity se) {
                hpRatio = Mth.clamp(se.hull() / (float) SanctuaryShipEntity.MAX_HULL, 0f, 1f);
            }
            int barW = (halfW - 6) * 2;
            fillQuad(guiVc, mat, -halfW + 6, 10.0f, halfW - 6, 11.8f, -0.015f, 0x901a2234);
            fillQuad(guiVc, mat, -halfW + 6, 10.0f, -halfW + 6 + barW * hpRatio, 11.8f, -0.01f,
                    mv.isAshamed(now) ? 0xffff5c6c : 0xffffa63b);

            int badgeColor = mv.isAshamed(now) ? 0xffff7878 : 0xffffcf70;
            font.drawInBatch(statusBadge, -w1 / 2f, -12.0f, badgeColor, false, mat, buffers,
                    Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
            font.drawInBatch(mainLine, -w2 / 2f, -1.5f, 0xfffff6de, true, mat, buffers,
                    Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        } else {
            float pillScale = 0.0185f;
            pose.scale(pillScale, -pillScale, pillScale);
            Matrix4f mat = pose.last().pose();

            String dotPrefix = mv.isAshamed(now) ? "● " : "◆ ";
            String compact = dotPrefix + "父:" + mv.mark().fatherName() + " · 母:" + mv.mark().motherName();
            int cw = font.width(compact);
            int halfW = cw / 2 + 5;

            VertexConsumer guiVc = buffers.getBuffer(RenderType.textBackgroundSeeThrough());
            fillQuad(guiVc, mat, -halfW, -2.5f, halfW, 9.8f, -0.02f, 0x980b0e18);
            font.drawInBatch(compact, -cw / 2f, 0f, 0xfff5ead0, true, mat, buffers,
                    Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        }

        pose.popPose();
    }

    private static void renderSmallOverheadBadge(Minecraft mc, RenderLevelStageEvent event,
                                                 PoseStack pose, MultiBufferSource buffers,
                                                 double rx, double ry, double rz, String label, int color,
                                                 List<ScreenRect> placedRects) {
        float chosenY = (float) ry;
        float rectHalfW = 0.13f;
        float rectHalfH = 0.055f;
        for (int tier = 0; tier < 5; tier++) {
            float testY = (float) ry + tier * 0.50f;
            float[] ndc = projectRelToNdc(event, (float) rx, testY, (float) rz);
            if (ndc == null) break;
            boolean collision = false;
            for (ScreenRect sr : placedRects) {
                if (sr.overlaps(ndc[0], ndc[1], rectHalfW, rectHalfH)) {
                    collision = true;
                    break;
                }
            }
            if (!collision || tier == 4) {
                chosenY = testY;
                placedRects.add(new ScreenRect(ndc[0], ndc[1], rectHalfW, rectHalfH));
                break;
            }
        }
        Font font = mc.font;
        pose.pushPose();
        pose.translate(rx, chosenY, rz);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.0185f, -0.0185f, 0.0185f);
        Matrix4f mat = pose.last().pose();
        int w = font.width(label);
        font.drawInBatch(label, -w / 2f, 0f, color, true, mat, buffers,
                Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /** V1 Next-Gen VFX 4.0 Choreography (Curved Spline Whips + Voronoi Space-Shatter + 3D Crystal Spires + Mach Cones). */
    private static void renderCueVfx4(PoseStack pose, MultiBufferSource buffers, Vec3 cam,
                                      InfinityNetwork.VfxCuePayload cue, float age, float p) {
        float fade = 1f - p;
        float erode = p * p;
        switch (cue.cueId()) {
            case InfinityNetwork.CUE_SPACE_WARP, InfinityNetwork.CUE_SPACE_ESCAPE -> {
                Vec3 a = cue.from().add(0, 1.0, 0).subtract(cam);
                Vec3 b = cue.to().add(0, 1.0, 0).subtract(cam);
                pose.pushPose();
                pose.translate(a.x, a.y - 0.85, a.z);
                InfinityVfxPrimitives.voronoiShatterCrown(pose, buffers, 8, 1.55f, 0.34f, 1.25f,
                        StarDialDraw.withAlpha(Stone.SPACE.color, fade * 0.94f), age * 7f, erode);
                pose.popPose();

                pose.pushPose();
                pose.translate(b.x, b.y - 0.85, b.z);
                InfinityVfxPrimitives.voronoiShatterCrown(pose, buffers, 10, 1.95f, 0.40f, 1.45f,
                        StarDialDraw.withAlpha(Stone.SPACE.coreBright, fade * 0.95f), -age * 7f, erode * 0.82f);
                pose.popPose();

                InfinityVfxPrimitives.catmullSplineWhip(pose, buffers, a, b, 4, 1.35f, 0.16f * fade,
                        StarDialDraw.withAlpha(Stone.SPACE.darkSheath | 0xff000000, fade * 0.86f),
                        StarDialDraw.withAlpha(Stone.SPACE.coreBright, fade * 0.95f), age, erode);
            }
            case InfinityNetwork.CUE_SPACE_DECOY_BURST -> {
                Vec3 c = cue.from().subtract(cam);
                float r = 7.6f * (1f - (1f - p) * (1f - p));
                pose.pushPose();
                pose.translate(c.x, c.y + 0.35, c.z);
                InfinityVfxPrimitives.voronoiShatterCrown(pose, buffers, 14, Math.max(0.8f, r * 0.78f),
                        0.48f * fade + 0.15f, 2.2f, StarDialDraw.withAlpha(Stone.SPACE.coreBright, fade * 0.96f), age * 9f, erode * 0.85f);
                InfinityVfxPrimitives.volumetricShockDome(pose, buffers, Math.max(0.6f, r * 0.55f), 5, 22,
                        StarDialDraw.withAlpha(Stone.SPACE.color, fade * 0.75f), -age * 6f, erode);
                InfinityVfxPrimitives.crescentShockRing(pose, buffers, Math.max(0.2f, r - 1.25f), r, 38,
                        StarDialDraw.withAlpha(Stone.SPACE.color, fade * 0.95f), age * 8f, erode);
                pose.popPose();
            }
            case InfinityNetwork.CUE_REALITY_WAVE -> {
                Vec3 a = cue.from().subtract(cam);
                Vec3 center = cue.to().subtract(cam);
                float r = 5.4f * (1f - (1f - p) * (1f - p));
                InfinityVfxPrimitives.catmullSplineWhip(pose, buffers, a, center.add(0, 0.7, 0), 3, 0.95f, 0.14f * fade,
                        StarDialDraw.withAlpha(Stone.REALITY.darkSheath | 0xff000000, fade * 0.84f),
                        StarDialDraw.withAlpha(Stone.REALITY.color, fade * 0.94f), age * 1.2f, erode);
                pose.pushPose();
                pose.translate(center.x, center.y + 0.05, center.z);
                float grow = Mth.sin(Math.min(1f, p * 1.35f) * (float) Math.PI * 0.5f);
                InfinityVfxPrimitives.eruptingCrystalCluster(pose, buffers, 10, Math.max(0.8f, r * 0.62f),
                        2.15f * grow, 0.24f, 26f, StarDialDraw.withAlpha(Stone.REALITY.color, fade * 0.95f), 15f, erode * 0.85f);
                InfinityVfxPrimitives.crescentShockRing(pose, buffers, Math.max(0.15f, r - 1.05f), r, 36,
                        StarDialDraw.withAlpha(Stone.REALITY.coreBright, fade * 0.92f), age * 5f, erode);
                pose.popPose();
            }
            case InfinityNetwork.CUE_REALITY_CONFISCATE -> {
                Vec3 center = cue.from().subtract(cam);
                float r = 13.5f * (1f - (1f - p) * (1f - p));
                pose.pushPose();
                pose.translate(center.x, center.y + 0.25, center.z);
                InfinityVfxPrimitives.crescentShockRing(pose, buffers, Math.max(0.2f, r - 1.15f), r, 40,
                        StarDialDraw.withAlpha(Stone.REALITY.color, fade * 0.94f), age * 5.5f, erode);
                InfinityVfxPrimitives.voronoiShatterCrown(pose, buffers, 10, Math.max(1.2f, r * 0.55f),
                        0.35f * fade, 1.8f, StarDialDraw.withAlpha(Stone.REALITY.coreBright, fade * 0.90f), -age * 4f, erode);
                pose.popPose();
            }
            case InfinityNetwork.CUE_REALITY_BUTTERFLY_HEAL -> {
                Vec3 from = cue.from().subtract(cam);
                Vec3 to = cue.to().subtract(cam);
                InfinityVfxPrimitives.crimsonButterflySwarm(pose, buffers, from, to, 6, p, age,
                        StarDialDraw.withAlpha(0xffff5277, 0.95f * (1f - p * 0.4f)));
            }
            case InfinityNetwork.CUE_POWER_PUNCH -> {
                Vec3 a = cue.from().subtract(cam);
                Vec3 b = cue.to().subtract(cam);
                InfinityVfxPrimitives.machVaporCones(pose, buffers, a, b, 3, 1.45f * (0.45f + 0.55f * p),
                        StarDialDraw.withAlpha(Stone.POWER.coreBright, fade * 0.92f), age, erode * 0.82f);
                InfinityVfxPrimitives.catmullSplineWhip(pose, buffers, a, b, 4, 0.85f, 0.22f * fade,
                        StarDialDraw.withAlpha(Stone.POWER.darkSheath | 0xff000000, fade * 0.90f),
                        StarDialDraw.withAlpha(Stone.POWER.color, fade * 0.98f), age * 1.6f, erode * 0.85f);
                pose.pushPose();
                pose.translate(b.x, b.y - 0.3, b.z);
                InfinityVfxPrimitives.voronoiShatterCrown(pose, buffers, 8, 1.6f * p + 0.4f, 0.32f * fade, 1.3f,
                        StarDialDraw.withAlpha(Stone.POWER.coreBright, fade * 0.94f), age * 8f, erode);
                pose.popPose();
            }
            case InfinityNetwork.CUE_POWER_WRATH -> {
                Vec3 c = cue.from().subtract(cam);
                float r = 13.5f * (1f - (1f - p) * (1f - p));
                pose.pushPose();
                pose.translate(c.x, c.y + 0.08, c.z);
                InfinityVfxPrimitives.eruptingCrystalCluster(pose, buffers, 12, Math.max(1.2f, r * 0.58f),
                        2.65f * fade + 0.5f, 0.28f, 30f, StarDialDraw.withAlpha(Stone.POWER.color, fade * 0.95f), 0f, erode);
                InfinityVfxPrimitives.volumetricShockDome(pose, buffers, Math.max(0.8f, r * 0.48f), 5, 22,
                        StarDialDraw.withAlpha(Stone.POWER.coreBright, fade * 0.68f), -age * 4f, erode);
                InfinityVfxPrimitives.crescentShockRing(pose, buffers, Math.max(0.2f, r - 1.45f), r, 40,
                        StarDialDraw.withAlpha(Stone.POWER.color, fade * 0.95f), age * 6f, erode);
                pose.popPose();
            }
            case InfinityNetwork.CUE_MIND_BEAM -> {
                Vec3 a = cue.from().subtract(cam);
                Vec3 b = cue.to().subtract(cam);
                InfinityVfxPrimitives.catmullSplineWhip(pose, buffers, a, b, 4, 0.92f, 0.14f * (0.35f + 0.65f * fade),
                        StarDialDraw.withAlpha(Stone.MIND.darkSheath | 0xff000000, fade * 0.85f),
                        StarDialDraw.withAlpha(Stone.MIND.color, fade * 0.96f), age * 1.5f, erode * 0.78f);
                for (int n = 0; n < 2; n++) {
                    InfinityVfxPrimitives.branchingNeuralArc(pose, buffers, a, b, 0.48f, 0.065f * fade,
                            StarDialDraw.withAlpha(Stone.MIND.coreBright, fade * 0.90f),
                            n * 31 + ((int) age / 2), erode * 0.75f);
                }
                pose.pushPose();
                pose.translate(b.x, b.y + 0.3, b.z);
                InfinityVfxPrimitives.puppetCrown(pose, buffers, 0.95f * (0.5f + 0.5f * p), age * 2f);
                pose.popPose();
            }
            case InfinityNetwork.CUE_MIND_ROLLCALL -> {
                Vec3 c = cue.from().subtract(cam);
                float r = 14.0f * Mth.sqrt(p);
                pose.pushPose();
                pose.translate(c.x, c.y + 0.18, c.z);
                InfinityVfxPrimitives.crescentShockRing(pose, buffers, Math.max(0.2f, r - 0.95f), r, 38,
                        StarDialDraw.withAlpha(Stone.MIND.color, fade * 0.92f), age * 4.5f, erode);
                InfinityVfxPrimitives.voronoiShatterCrown(pose, buffers, 8, Math.max(1.0f, r * 0.65f),
                        0.32f * fade, 2.4f * fade, StarDialDraw.withAlpha(Stone.MIND.coreBright, fade * 0.88f), age * 4f, erode);
                pose.popPose();
            }
            case InfinityNetwork.CUE_TIME_DOME -> {
                Vec3 c = cue.from().subtract(cam);
                float alpha = p < 0.06f ? p / 0.06f : p > 0.90f ? (1f - p) / 0.10f : 1f;
                float ringErode = p > 0.88f ? (p - 0.88f) / 0.12f : 0f;
                pose.pushPose();
                pose.translate(c.x, c.y + 0.15, c.z);
                InfinityVfxPrimitives.minkowskiLightCone(pose, buffers, 7.2f, 5.8f, 32,
                        StarDialDraw.withAlpha(Stone.TIME.coreBright, 0.52f * alpha),
                        StarDialDraw.withAlpha(Stone.TIME.color, 0.42f * alpha),
                        age * 1.4f, ringErode);
                InfinityVfxPrimitives.voronoiShatterCrown(pose, buffers, 12, 16.5f, 0.65f, 3.8f,
                        StarDialDraw.withAlpha(Stone.TIME.coreBright, 0.68f * alpha), age * 0.6f, ringErode);
                InfinityVfxPrimitives.armillaryRing(pose, buffers, 17.1f, 18.0f, 56,
                        StarDialDraw.withAlpha(Stone.TIME.color, 0.88f * alpha), age * 1.1f, ringErode);
                pose.pushPose();
                pose.mulPose(Axis.XP.rotationDegrees(22f));
                InfinityVfxPrimitives.armillaryRing(pose, buffers, 11.4f, 12.1f, 36,
                        StarDialDraw.withAlpha(Stone.TIME.coreBright, 0.78f * alpha), -age * 1.8f, ringErode);
                pose.popPose();
                pose.popPose();
            }
            case InfinityNetwork.CUE_TIME_REWIND -> {
                Vec3 a = cue.from().add(0, 0.9, 0).subtract(cam);
                Vec3 b = cue.to().add(0, 0.9, 0).subtract(cam);
                InfinityVfxPrimitives.catmullSplineWhip(pose, buffers, a, b, 3, 1.05f, 0.15f * fade,
                        StarDialDraw.withAlpha(Stone.TIME.darkSheath | 0xff000000, fade * 0.82f),
                        StarDialDraw.withAlpha(Stone.TIME.coreBright, fade * 0.95f), -age * 1.4f, erode);
            }
            case InfinityNetwork.CUE_SOUL_HUKOU, InfinityNetwork.CUE_SOUL_SAVE -> {
                Vec3 a = cue.from().subtract(cam);
                Vec3 b = cue.to().subtract(cam);
                InfinityVfxPrimitives.catmullSplineWhip(pose, buffers, a, b, 3, 0.88f, 0.14f * fade,
                        StarDialDraw.withAlpha(Stone.SOUL.darkSheath | 0xff000000, fade * 0.84f),
                        StarDialDraw.withAlpha(Stone.SOUL.coreBright, fade * 0.95f), age * 1.3f, erode);
                pose.pushPose();
                pose.translate(b.x, b.y, b.z);
                float r = 2.05f * Mth.sqrt(p);
                InfinityVfxPrimitives.crescentShockRing(pose, buffers, Math.max(0.15f, r - 0.55f), r, 28,
                        StarDialDraw.withAlpha(Stone.SOUL.color, fade * 0.92f), age * 5.5f, erode);
                InfinityVfxPrimitives.floatingPolyShards(pose, buffers, 6, 1.3f, 0.20f * fade, 1.6f * p + 0.4f,
                        StarDialDraw.withAlpha(Stone.SOUL.coreBright, fade * 0.92f), age, erode);
                pose.popPose();
            }
            case InfinityNetwork.CUE_COMBO_PUBLIC_EXEC -> {
                Vec3 c = cue.from().subtract(cam);
                float r = 12.4f * (1f - (1f - p) * (1f - p));
                pose.pushPose();
                pose.translate(c.x, c.y + 0.25, c.z);
                InfinityVfxPrimitives.crescentShockRing(pose, buffers, Math.max(0.2f, r - 1.25f), r, 40,
                        StarDialDraw.withAlpha(Stone.SOUL.color, fade * 0.95f), age * 6.5f, erode);
                InfinityVfxPrimitives.voronoiShatterCrown(pose, buffers, 10, Math.max(1.0f, r * 0.68f),
                        0.38f * fade, 2.0f, StarDialDraw.withAlpha(Stone.MIND.coreBright, fade * 0.92f), -age * 7f, erode);
                pose.popPose();
            }
            case InfinityNetwork.CUE_COMBO_SINGULARITY -> {
                Vec3 anchor = cue.to().subtract(cam);
                float r = p < 0.35f ? 8.5f * (1f - p / 0.35f) : 9.8f * ((p - 0.35f) / 0.65f);
                pose.pushPose();
                pose.translate(anchor.x, anchor.y + 0.8, anchor.z);
                InfinityVfxPrimitives.volumetricShockDome(pose, buffers, Math.max(0.6f, r * 0.56f), 5, 22,
                        StarDialDraw.withAlpha(p < 0.35f ? Stone.SPACE.color : Stone.POWER.color, fade * 0.72f), age * 7f, erode);
                InfinityVfxPrimitives.voronoiShatterCrown(pose, buffers, 14, Math.max(0.8f, r * 0.75f),
                        0.45f * fade, 2.2f, StarDialDraw.withAlpha(Stone.POWER.coreBright, fade * 0.95f), age * 9f, erode * 0.85f);
                InfinityVfxPrimitives.crescentShockRing(pose, buffers, Math.max(0.15f, r - 1.15f), Math.max(0.4f, r), 38,
                        StarDialDraw.withAlpha(p < 0.35f ? Stone.SPACE.color : Stone.POWER.color, fade * 0.96f), age * 9f, erode * 0.85f);
                pose.popPose();
            }
            case InfinityNetwork.CUE_COMBO_TEMPORAL_RECKONING -> {
                Vec3 c = cue.from().subtract(cam);
                float r = 5.6f * (1f - (1f - p) * (1f - p));
                pose.pushPose();
                pose.translate(c.x, c.y, c.z);
                InfinityVfxPrimitives.voronoiShatterCrown(pose, buffers, 12, Math.max(0.8f, r * 0.82f),
                        0.42f * fade, 2.1f, StarDialDraw.withAlpha(Stone.TIME.coreBright, fade * 0.96f), age * 10f, erode);
                InfinityVfxPrimitives.crescentShockRing(pose, buffers, Math.max(0.15f, r - 0.95f), r, 34,
                        StarDialDraw.withAlpha(0xffffdf75, fade * 0.96f), age * 8f, erode);
                pose.popPose();
            }
            case InfinityNetwork.CUE_SNAP -> {
                Vec3 c = cue.from().subtract(cam);
                Stone[] stones = Stone.values();
                pose.pushPose();
                pose.translate(c.x, c.y, c.z);
                InfinityVfxPrimitives.minkowskiLightCone(pose, buffers, 16.0f * p + 1.2f, 14.0f * p + 1.5f, 36,
                        StarDialDraw.withAlpha(0xffffe288, fade * 0.72f),
                        StarDialDraw.withAlpha(Stone.POWER.color, fade * 0.62f), age * 4.5f, erode * 0.85f);
                InfinityVfxPrimitives.voronoiShatterCrown(pose, buffers, 18, 12.5f * p + 1.5f,
                        0.68f * fade + 0.2f, 4.5f * p + 0.8f, StarDialDraw.withAlpha(0xfffff0b8, fade * 0.95f), age * 6f, erode * 0.8f);
                for (int i = 0; i < stones.length; i++) {
                    float ri = (7.5f + i * 5.8f) * (1f - (1f - p) * (1f - p));
                    InfinityVfxPrimitives.crescentShockRing(pose, buffers, Math.max(0.2f, ri - 1.15f), ri, 44,
                            StarDialDraw.withAlpha(stones[i].color, fade * 0.92f), age * (3.2f + i), erode * 0.85f);
                }
                pose.popPose();
            }
            case InfinityNetwork.CUE_SHIP_CANNON -> {
                Vec3 a = cue.from().subtract(cam);
                Vec3 b = cue.to().subtract(cam);
                InfinityVfxPrimitives.machVaporCones(pose, buffers, a, b, 2, 1.3f,
                        StarDialDraw.withAlpha(0xffff9ec8, fade * 0.88f), age, erode * 0.8f);
                InfinityVfxPrimitives.catmullSplineWhip(pose, buffers, a, b, 3, 0.75f, 0.36f * (0.35f + 0.65f * fade),
                        StarDialDraw.withAlpha(0xff2b0718, fade * 0.92f),
                        StarDialDraw.withAlpha(0xfff24898, fade), age * 1.8f, erode * 0.8f);
            }
            case InfinityNetwork.CUE_SHIP_RAIN_FIRE -> {
                Vec3 c = cue.to().subtract(cam);
                float r = 11.5f * (1f - (1f - p) * (1f - p));
                pose.pushPose();
                pose.translate(c.x, c.y + 0.15, c.z);
                InfinityVfxPrimitives.eruptingCrystalCluster(pose, buffers, 8, Math.max(1.0f, r * 0.52f),
                        2.2f * fade + 0.4f, 0.26f, 28f, StarDialDraw.withAlpha(0xfff24898, fade * 0.92f), age * 4f, erode);
                InfinityVfxPrimitives.crescentShockRing(pose, buffers, Math.max(0.2f, r - 1.4f), r, 40,
                        StarDialDraw.withAlpha(0xfff24898, fade * 0.96f), age * 7f, erode);
                InfinityVfxPrimitives.volumetricShockDome(pose, buffers, r * 0.44f, 4, 18,
                        StarDialDraw.withAlpha(0xffff78a8, fade * 0.65f), -age * 5f, erode);
                pose.popPose();
            }
            case InfinityNetwork.CUE_SHIP_WARP -> {
                Vec3 a = cue.from().subtract(cam);
                Vec3 b = cue.to().subtract(cam);
                InfinityVfxPrimitives.catmullSplineWhip(pose, buffers, a, b, 4, 2.2f, 0.34f * fade,
                        StarDialDraw.withAlpha(0xff0b1c36, fade * 0.88f),
                        StarDialDraw.withAlpha(0xff7ce8ff, fade * 0.94f), age * 1.4f, erode);
                pose.pushPose();
                pose.translate(b.x, b.y, b.z);
                InfinityVfxPrimitives.voronoiShatterCrown(pose, buffers, 12, 3.8f, 0.55f * fade, 2.2f,
                        StarDialDraw.withAlpha(0xffb8f2ff, fade * 0.94f), -age * 8f, erode);
                pose.popPose();
            }
            case InfinityNetwork.CUE_SHIP_SNAP_COLLAPSE -> {
                Vec3 c = cue.from().subtract(cam);
                pose.pushPose();
                pose.translate(c.x, c.y + 1.5, c.z);
                float r = 18.5f * (1f - (1f - p) * (1f - p));
                InfinityVfxPrimitives.voronoiShatterCrown(pose, buffers, 16, Math.max(1.5f, r * 0.68f),
                        0.65f * fade, 3.8f, StarDialDraw.withAlpha(0xffffd880, fade * 0.94f), age * 7f, erode);
                InfinityVfxPrimitives.crescentShockRing(pose, buffers, Math.max(0.3f, r - 1.6f), r, 42,
                        StarDialDraw.withAlpha(0xffffb84d, fade * 0.95f), age * 7f, erode);
                pose.popPose();
            }
            default -> {}
        }
    }

    private static void fillQuad(VertexConsumer vc, Matrix4f mat, float x0, float y0, float x1, float y1, float z, int argb) {
        vc.addVertex(mat, x0, y1, z).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(mat, x1, y1, z).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(mat, x1, y0, z).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
        vc.addVertex(mat, x0, y0, z).setColor(argb).setLight(LightTexture.FULL_BRIGHT);
    }
}
