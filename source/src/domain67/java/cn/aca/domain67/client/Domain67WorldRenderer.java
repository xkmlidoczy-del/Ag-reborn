package cn.aca.domain67.client;

import cn.aca.domain67.AcaDomain67;
import cn.aca.domain67.CursedLogEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/**
 * ACA-Domain67 v1.2.0 World & Prominent Iron-Bound Cursed Log Renderer:
 * - Unmistakable High-Contrast Cursed Logs (`滚木`):
 *   - 24-Sided Deep-Roasted Walnut Trunk + 12 Sculpted 3D Dark-Bark Ridges (`#331908`)
 *   - High-Contrast Sawed Timber Growth-Ring End Caps (`#F5D8A8` pine sapwood + 6 concentric dark heartwood rings + wood cracks)
 *   - 3 Heavy Blackened-Iron Hoops (`#221E1B`) Studded with 36 Gleaming Raised 3D Gold Rivets (`#FFE078`)
 *   - 24 Forged Silver-Steel & Crimson-Gold Spikes + Ground-Contact Shockwave Ring when rolling across the floor!
 * - 4 Giant Golden-Chained Enforcement Logs ("悬顶镇店金链滚木") suspended high under the restaurant arches (`y = +7.4m`)!
 * - Compact, non-obstructive VFX portals (`1.25m` hollow gold rings) so sightlines stay 100% clear!
 */
public final class Domain67WorldRenderer {
    private static final ResourceLocation CURSED_LOG_TEX = AcaDomain67.id("textures/entity/cursed_log.png");

    private Domain67WorldRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Domain67CoreShader.updateUniforms(partial);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        long now = Domain67ClientCache.ticks();
        float time = now + partial;

        for (var dom : Domain67ClientCache.domains().values()) {
            Vec3 center = new Vec3(dom.cx, dom.cy, dom.cz);
            if (center.distanceToSqr(cam) > 220.0 * 220.0) continue;

            boolean inside = cam.distanceToSqr(center) <= (dom.maxRadius + 0.8) * (dom.maxRadius + 0.8);
            float radFactor = dom.currentRadiusFactor(now, partial);
            int curtainPhase = dom.exteriorCurtainPhase(now, partial);
            float rot = time * 1.4f;

            pose.pushPose();
            pose.translate(center.x - cam.x, center.y - cam.y, center.z - cam.z);

            if (inside) {
                if (radFactor > 0.04f) {
                    renderModernLuxuryStageFurniture(pose, buf, dom, now, partial, dom.maxRadius * radFactor, time);
                    // Suspended logs switch render types while drawing bark. Start the tables in the current batch.
                    VertexConsumer tables = buf.getBuffer(Domain67CoreShader.solidArch());

                    for (var diner : dom.diners) {
                        if (diner.finished()) continue;
                        float dinerRise = dom.propRiseFactor(now, partial, 0.22f);
                        if (dinerRise <= 0.02f) continue;
                        pose.pushPose();
                        pose.translate(diner.x() - dom.cx,
                                (diner.y() - dom.cy) - (1f - dinerRise) * 1.25f,
                                diner.z() - dom.cz);
                        float remainFrac = Mth.clamp(1.0f - diner.progress(), 0.14f, 1.0f);
                        drawModernDinerGourmetTable(pose, tables, remainFrac, time);
                        pose.popPose();
                    }
                    buf.endBatch(Domain67CoreShader.solidArch());
                }

                // Subtle Floor Seal & Overhead Chandelier Light Halos
                VertexConsumer glow = buf.getBuffer(Domain67CoreShader.additiveGlow());
                pose.pushPose();
                pose.translate(0.0, 0.05, 0.0);
                pose.mulPose(Axis.YP.rotationDegrees(rot));
                float circleScale = dom.isCollapsing() ? (1.0f - dom.collapseProgress(now, partial) * 0.8f) : 1.0f;
                drawRing(pose, glow, 2.8f * circleScale, 3.25f * circleScale, 40,
                        Domain67CoreShader.MODE_BROADCAST_RING, 0, 1.0f, 0.88f, 0.46f, 0.55f);
                pose.popPose();

                float chandRise = dom.propRiseFactor(now, partial, 0.14f);
                if (chandRise > 0.05f) {
                    pose.pushPose();
                    pose.translate(0.0, 11.2f * chandRise, 0.0);
                    pose.mulPose(Axis.YP.rotationDegrees(-rot * 0.6f));
                    drawRing(pose, glow, 4.35f * chandRise, 4.78f * chandRise, 40,
                            Domain67CoreShader.MODE_BROADCAST_RING, 0, 1.0f, 0.90f, 0.52f, 0.65f);
                    pose.popPose();
                }
            } else {
                float r = dom.maxRadius * Math.max(0.82f, radFactor);
                if (radFactor > 0.02f) {
                    VertexConsumer shellVc = buf.getBuffer(Domain67CoreShader.barrierShell());
                    drawCurtainHemisphere(pose, shellVc, r, 40, 22, Domain67CoreShader.MODE_OUTER_SHELL, curtainPhase);

                    VertexConsumer glow = buf.getBuffer(Domain67CoreShader.additiveGlow());
                    pose.pushPose();
                    pose.translate(0.0, r + 0.2, 0.0);
                    pose.mulPose(Axis.YP.rotationDegrees(rot * 1.8f));
                    drawRing(pose, glow, 2.4f, 3.5f, 32, Domain67CoreShader.MODE_BROADCAST_RING, 0,
                            1.0f, 0.88f, 0.42f, 0.88f);
                    pose.popPose();
                }
            }

            pose.popPose();

            // Minimalist Crosshair-Targeted Single Diner Progress Pill (zero screen clutter!)
            if (inside && !dom.isCollapsing()) {
                Domain67SpatialUi.renderTargetedDinerMicroPill(mc, pose, buf, cam, dom, partial);
            }
        }

        // Compact, Clean Rift Rings & Ground Waves (never blocking center FOV!)
        VertexConsumer glow = buf.getBuffer(Domain67CoreShader.additiveGlow());
        for (var rift : Domain67ClientCache.rifts()) {
            float age = (now - rift.startTick()) + partial;
            float p = Mth.clamp(age / Math.max(1f, rift.duration()), 0f, 1f);
            int phaseInt = (int) (p * 1000f);

            pose.pushPose();
            pose.translate(rift.pos().x - cam.x, rift.pos().y - cam.y, rift.pos().z - cam.z);
            if (rift.mode() == Domain67ClientCache.RIFT_MODE_BROADCAST) {
                float ringR = 1.6f + p * 28.0f;
                drawRing(pose, glow, ringR - 0.8f, ringR, 44, Domain67CoreShader.MODE_BROADCAST_RING, phaseInt,
                        1.0f, 0.86f, 0.38f, (1.0f - p) * 0.65f);
            } else if (rift.mode() == Domain67ClientCache.RIFT_MODE_SAUCE_WAVE) {
                float waveR = 1.4f + p * 20.0f;
                drawRing(pose, glow, waveR - 1.4f, waveR, 40, Domain67CoreShader.MODE_SAUCE_WAVE, phaseInt,
                        1.0f, 0.84f, 0.32f, (1.0f - p * 0.85f) * 0.65f);
            } else {
                pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
                float portalR = 1.35f * Mth.sin(p * (float) Math.PI);
                drawQuad(pose, glow, portalR, Domain67CoreShader.MODE_SPATIAL_RIFT, phaseInt,
                        1.0f, 0.84f, 0.38f, 0.62f);
            }
            pose.popPose();
        }

        buf.endBatch();
    }

    private static void renderModernLuxuryStageFurniture(PoseStack pose, MultiBufferSource.BufferSource buf,
                                                         Domain67ClientCache.ClientDomainView dom,
                                                         long now, float partial, float activeR, float time) {
        VertexConsumer arch = buf.getBuffer(Domain67CoreShader.solidArch());
        // 1. Eight Symmetric Modern Warm-Ivory & Honey-Oak Curved Booth Sets
        float[] boothZs = {-10.5f, -3.5f, 3.5f, 10.5f};
        for (int side = -1; side <= 1; side += 2) {
            float bx = side * 9.0f;
            for (float bz : boothZs) {
                float dist = Mth.sqrt(bx * bx + bz * bz);
                float distRatio = Mth.clamp(dist / dom.maxRadius, 0f, 1f);
                float rise = dom.propRiseFactor(now, partial, distRatio);
                if (rise <= 0.02f || dist > activeR - 1.2f) continue;

                pose.pushPose();
                pose.translate(bx, -(1.0f - rise) * 2.1f, bz);
                drawModernLuxuryBooth(pose, arch, side, time);
                pose.popPose();
            }
        }

        // 2. Grand Open-Kitchen White-Marble & Honey-Oak Chef Island + Sculpted 3D "67" Monument (z = +16.8m)
        float barRise = dom.propRiseFactor(now, partial, 0.52f);
        if (barRise > 0.02f && activeR > 16.5f) {
            pose.pushPose();
            pose.translate(0.0, -(1.0f - barRise) * 4.5f, 16.8f);

            Domain67ItemRenderer.drawBox(pose, arch, -5.8f, 0.0f, -2.0f, 5.8f, 0.20f, 2.2f, 0.98f, 0.96f, 0.91f);
            Domain67ItemRenderer.drawBox(pose, arch, -5.86f, 0.18f, -2.06f, 5.86f, 0.24f, 2.26f, 0.94f, 0.77f, 0.34f);
            Domain67ItemRenderer.drawBox(pose, arch, -4.9f, 0.24f, -1.05f, 4.9f, 1.28f, 0.55f, 0.84f, 0.66f, 0.43f);
            Domain67ItemRenderer.drawBox(pose, arch, -4.96f, 1.16f, -1.10f, 4.96f, 1.24f, 0.60f, 1.00f, 0.90f, 0.54f);
            Domain67ItemRenderer.drawBox(pose, arch, -5.15f, 1.28f, -1.22f, 5.15f, 1.42f, 0.72f, 0.99f, 0.98f, 0.95f);
            Domain67ItemRenderer.drawBox(pose, arch, -5.18f, 1.38f, -1.25f, 5.18f, 1.43f, 0.75f, 0.94f, 0.78f, 0.35f);

            for (float sx : new float[]{-3.3f, -1.1f, 1.1f, 3.3f}) {
                Domain67ItemRenderer.drawOctagonalFrustum(pose, arch, 0.06f, 0.05f, 0.24f, 0.86f, 8, sx, -1.61f,
                        0.93f, 0.75f, 0.33f);
                Domain67ItemRenderer.drawOctagonalFrustum(pose, arch, 0.27f, 0.28f, 0.86f, 0.98f, 12, sx, -1.61f,
                        0.99f, 0.96f, 0.89f);
            }

            for (float px : new float[]{-4.6f, 4.6f}) {
                Domain67ItemRenderer.drawBox(pose, arch, px - 0.32f, 0.24f, 1.10f, px + 0.32f, 6.2f, 1.72f,
                        0.98f, 0.95f, 0.89f);
                Domain67ItemRenderer.drawBox(pose, arch, px - 0.09f, 0.60f, 1.06f, px + 0.09f, 5.8f, 1.76f,
                        1.00f, 0.90f, 0.54f);
            }
            Domain67ItemRenderer.drawBox(pose, arch, -4.95f, 6.0f, 1.05f, 4.95f, 6.45f, 1.78f, 0.94f, 0.77f, 0.34f);

            pose.pushPose();
            pose.translate(0.0f, 4.15f, 1.38f);
            pose.mulPose(Axis.YP.rotationDegrees(180f));
            float monumentBob = Mth.sin(time * 0.08f) * 0.04f;
            float monumentTilt = Mth.cos(time * 0.08f) * 3.0f;
            Domain67ItemRenderer.drawSculpted67Pair(pose, arch, 2.85f, monumentBob, monumentTilt);
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(90f));
            pose.mulPose(Axis.YP.rotationDegrees(time * 0.7f));
            Domain67ItemRenderer.drawOctagonal3dRing(pose, arch, 2.05f, 0.05f, 0.05f, 28, 0.95f, 0.79f, 0.36f);
            pose.popPose();
            pose.popPose();

            pose.popPose();
        }

        // 3. Two Signature Gourmet Showcases
        if (barRise > 0.05f && activeR > 15.0f) {
            for (int side : new int[]{-1, 1}) {
                pose.pushPose();
                pose.translate(side * 5.6f, -(1.0f - barRise) * 3.0f, 13.6f);
                Domain67ItemRenderer.drawOctagonalFrustum(pose, arch, 0.78f, 0.60f, 0.0f, 1.08f, 14,
                        0.98f, 0.95f, 0.89f);
                Domain67ItemRenderer.drawOctagonal3dRing(pose, arch, 0.64f, 0.045f, 0.04f, 14, 1.08f, 0.94f, 0.77f, 0.34f);

                pose.translate(0.0f, 1.24f + Mth.sin(time * 0.09f + side) * 0.06f, 0.0f);
                pose.mulPose(Axis.YP.rotationDegrees(time * 1.2f * side));
                pose.scale(1.75f, 1.75f, 1.75f);
                Domain67ItemRenderer.drawGourmetNoodleBowlAssembly(pose, arch, 1.0f, time);
                pose.popPose();
            }
        }

        // 4. FOUR GIANT GOLDEN-CHAINED ENFORCEMENT CURSED LOGS ("悬顶镇店金链巨型滚木") at y = +7.3m!
        // Immediately visible & iconic inside the dining hall without blocking horizontal eye-level sightlines!
        float logSentRise = dom.propRiseFactor(now, partial, 0.58f);
        if (logSentRise > 0.05f && activeR > 17.5f) {
            float[][] sentCoords = {{-13.2f, -7.5f, 32f}, {13.2f, -7.5f, -32f}, {-13.2f, 7.5f, 148f}, {13.2f, 7.5f, -148f}};
            for (int idx = 0; idx < sentCoords.length; idx++) {
                float sx = sentCoords[idx][0];
                float sz = sentCoords[idx][1];
                float yaw = sentCoords[idx][2];
                float sway = Mth.sin(time * 0.07f + idx * 1.5f) * 3.5f;

                pose.pushPose();
                pose.translate(sx, 7.3f * logSentRise + (1.0f - logSentRise) * 6.0f, sz);
                pose.mulPose(Axis.YP.rotationDegrees(yaw));
                pose.mulPose(Axis.ZP.rotationDegrees(sway));

                // Twin gleaming golden restraint chains up to the 15.5m vault ceiling!
                for (float cxOff : new float[]{-1.15f, 1.15f}) {
                    Domain67ItemRenderer.drawBox(pose, arch, cxOff - 0.055f, 0.8f, -0.055f,
                            cxOff + 0.055f, 8.2f, 0.055f, 0.94f, 0.76f, 0.32f);
                    Domain67ItemRenderer.drawOctagonal3dRing(pose, arch, 0.92f, 0.06f, 0.06f, 14, 0f,
                            0.94f, 0.76f, 0.32f);
                }

                // Render the unmistakable dark-walnut, sawed-end-cap, black-iron-hooped Giant Enforcement Cursed Log!
                drawProminentCursedLogMesh(pose, buf, 1.65f, time * 0.45f, false);
                // Re-fetch solidArch buffer after bark pass inside drawProminentCursedLogMesh
                arch = buf.getBuffer(Domain67CoreShader.solidArch());
                pose.popPose();
            }
        }

        // 5. Twelve Fluted Warm-Ivory & Brushed-Gold Perimeter Columns at r = 24.5m
        if (activeR > 23.5f) {
            for (int i = 0; i < 12; i++) {
                double ang = (Math.PI * 2.0 * i) / 12.0 + Math.PI / 12.0;
                float px = (float) (Math.cos(ang) * 24.5);
                float pz = (float) (Math.sin(ang) * 24.5);
                float colRise = dom.propRiseFactor(now, partial, 0.76f);
                if (colRise <= 0.02f) continue;

                pose.pushPose();
                pose.translate(px, -(1.0f - colRise) * 12.0f, pz);
                pose.mulPose(Axis.YP.rotationDegrees((float) -Math.toDegrees(ang)));
                Domain67ItemRenderer.drawBox(pose, arch, -0.78f, 0.0f, -0.78f, 0.78f, 0.65f, 0.78f, 0.94f, 0.77f, 0.34f);
                Domain67ItemRenderer.drawOctagonalFrustum(pose, arch, 0.62f, 0.58f, 0.65f, 14.8f, 16, 0.99f, 0.97f, 0.92f);
                Domain67ItemRenderer.drawOctagonal3dRing(pose, arch, 0.66f, 0.06f, 0.14f, 16, 7.35f, 0.94f, 0.78f, 0.35f);
                Domain67ItemRenderer.drawBox(pose, arch, -0.84f, 14.8f, -0.84f, 0.84f, 15.5f, 0.84f, 0.95f, 0.79f, 0.36f);
                pose.popPose();
            }
        }

        // 6. Overhead 3-Tier Modern Crystal & Champagne-Gold Halo Chandelier (y = +11.2m)
        float chandRise = dom.propRiseFactor(now, partial, 0.14f);
        if (chandRise > 0.05f) {
            pose.pushPose();
            pose.translate(0.0, 11.2f * chandRise + (1.0f - chandRise) * 5.0f, 0.0);
            Domain67ItemRenderer.drawBox(pose, arch, -0.10f, -1.6f, -0.10f, 0.10f, 8.2f, 0.10f, 0.95f, 0.79f, 0.35f);
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(time * 0.35f));
            Domain67ItemRenderer.drawOctagonal3dRing(pose, arch, 4.6f, 0.10f, 0.12f, 32, 0.96f, 0.81f, 0.37f);
            Domain67ItemRenderer.drawOctagonal3dRing(pose, arch, 4.46f, 0.06f, 0.18f, 32, 1.00f, 0.95f, 0.74f);
            pose.popPose();

            pose.pushPose();
            pose.translate(0.0f, -0.85f, 0.0f);
            pose.mulPose(Axis.YP.rotationDegrees(-time * 0.55f));
            Domain67ItemRenderer.drawOctagonal3dRing(pose, arch, 3.0f, 0.09f, 0.11f, 28, 0.96f, 0.81f, 0.37f);
            Domain67ItemRenderer.drawOctagonal3dRing(pose, arch, 2.88f, 0.05f, 0.16f, 28, 1.00f, 0.95f, 0.74f);
            pose.popPose();

            pose.pushPose();
            pose.translate(0.0f, -1.55f, 0.0f);
            pose.mulPose(Axis.YP.rotationDegrees(time * 0.85f));
            Domain67ItemRenderer.drawOctagonal3dRing(pose, arch, 1.6f, 0.08f, 0.10f, 20, 1.00f, 0.90f, 0.52f);
            pose.popPose();

            pose.popPose();
        }
    }

    private static void drawModernLuxuryBooth(PoseStack pose, VertexConsumer arch, int side, float time) {
        Domain67ItemRenderer.drawOctagonal3dRing(pose, arch, 0.36f, 0.06f, 0.03f, 14, 0.03f, 0.94f, 0.77f, 0.34f);
        Domain67ItemRenderer.drawOctagonalFrustum(pose, arch, 0.09f, 0.08f, 0.06f, 0.78f, 10, 0.92f, 0.75f, 0.33f);
        Domain67ItemRenderer.drawBox(pose, arch, -1.18f, 0.78f, -0.74f, 1.18f, 0.88f, 0.74f, 0.99f, 0.98f, 0.95f);
        Domain67ItemRenderer.drawBox(pose, arch, -1.20f, 0.84f, -0.76f, 1.20f, 0.87f, 0.76f, 0.94f, 0.78f, 0.35f);

        float lx = side * 0.76f;
        Domain67ItemRenderer.drawOctagonalFrustum(pose, arch, 0.05f, 0.04f, 0.88f, 1.18f, 10, lx, 0f,
                0.95f, 0.79f, 0.35f);
        Domain67ItemRenderer.drawOctagonalFrustum(pose, arch, 0.22f, 0.08f, 1.16f, 1.35f, 14, lx, 0f,
                1.00f, 0.91f, 0.58f);

        pose.pushPose();
        pose.translate(-side * 0.22f, 0.88f, 0.0f);
        pose.scale(0.68f, 0.68f, 0.68f);
        Domain67ItemRenderer.drawGourmetNoodleBowlAssembly(pose, arch, 0.92f, time);
        pose.popPose();

        for (int dir : new int[]{-1, 1}) {
            float sz = dir * 1.48f;
            float backZ = sz + dir * 0.38f;
            Domain67ItemRenderer.drawBox(pose, arch, -1.20f, 0.0f, sz - 0.42f, 1.20f, 0.34f, sz + 0.42f,
                    0.82f, 0.64f, 0.41f);
            Domain67ItemRenderer.drawBox(pose, arch, -1.22f, 0.34f, sz - 0.44f, 1.22f, 0.54f, sz + 0.44f,
                    0.99f, 0.96f, 0.89f);
            Domain67ItemRenderer.drawBox(pose, arch, -1.20f, 0.50f, backZ - 0.15f, 1.20f, 1.44f, backZ + 0.15f,
                    0.97f, 0.93f, 0.84f);
            Domain67ItemRenderer.drawBox(pose, arch, -1.23f, 1.44f, backZ - 0.17f, 1.23f, 1.52f, backZ + 0.17f,
                    0.94f, 0.78f, 0.35f);
        }
    }

    private static void drawModernDinerGourmetTable(PoseStack pose, VertexConsumer arch, float remainFrac, float time) {
        Domain67ItemRenderer.drawOctagonal3dRing(pose, arch, 0.36f, 0.07f, 0.035f, 16, 0.035f, 0.94f, 0.77f, 0.34f);
        Domain67ItemRenderer.drawOctagonalFrustum(pose, arch, 0.085f, 0.075f, 0.06f, 0.78f, 12, 0.91f, 0.74f, 0.32f);
        Domain67ItemRenderer.drawOctagonalFrustum(pose, arch, 0.76f, 0.80f, 0.78f, 0.85f, 18, 0.94f, 0.78f, 0.35f);
        Domain67ItemRenderer.drawOctagonalFrustum(pose, arch, 0.77f, 0.77f, 0.85f, 0.89f, 18, 0.99f, 0.98f, 0.95f);

        pose.pushPose();
        pose.translate(0.0f, 0.89f, 0.0f);
        pose.scale(1.06f, 1.06f, 1.06f);
        Domain67ItemRenderer.drawGourmetNoodleBowlAssembly(pose, arch, remainFrac, time);
        pose.popPose();
    }

    /**
     * Unmistakable High-Contrast Giant Cursed Log Renderer ("高对比实心年轮黑铁金箍滚木"):
     * - Deep-Roasted Dark Walnut Bark + 12 3D Longitudinal Bark Ridges (stands out vividly against ivory marble!)
     * - Bright Pine-Cream Concentric Growth-Ring End Caps + Radial Wood Cracks
     * - 3 Heavy Blackened-Iron Hoops Studded with 36 Raised 3D Gold Rivets
     * - 24 Forged Silver-Steel & Crimson-Gold Conical Spikes + Rolling Floor Shockwave!
     */
    public static final class CursedLogRenderer extends EntityRenderer<CursedLogEntity> {
        public CursedLogRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public ResourceLocation getTextureLocation(CursedLogEntity entity) {
            return CURSED_LOG_TEX;
        }

        @Override
        public void render(CursedLogEntity entity, float entityYaw, float partialTick,
                           PoseStack pose, MultiBufferSource buffer, int packedLight) {
            float sc = entity.getLogScale();
            boolean mega = entity.getVariant() == CursedLogEntity.VARIANT_MEGA_BLACK_FLASH;
            boolean grounded = entity.isGroundedRolling();
            float spin = (entity.tickCount + partialTick) * (mega ? 28f : 44f);
            float trunkR = 0.52f * sc;

            pose.pushPose();
            pose.translate(0.0, trunkR + 0.04f, 0.0);
            pose.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));

            drawProminentCursedLogMesh(pose, buffer, sc, spin, mega);

            // Ground-rolling shockwave ring + aerodynamic crimson-gold slipstream so moving logs catch the eye instantly!
            VertexConsumer aura = buffer.getBuffer(Domain67CoreShader.additiveGlow());
            if (grounded) {
                pose.pushPose();
                pose.translate(0.0, -trunkR + 0.03f, 0.0);
                drawRing(pose, aura, trunkR * 0.9f, trunkR * 1.95f, 28,
                        mega ? Domain67CoreShader.MODE_BLACK_FLASH_LOG : Domain67CoreShader.MODE_BROADCAST_RING,
                        (entity.tickCount * 45) % 1000,
                        1.0f, mega ? 0.36f : 0.82f, 0.26f, 0.72f);
                pose.popPose();
            }

            for (int t = 1; t <= 2; t++) {
                pose.pushPose();
                pose.translate(0.0, 0.0, -0.62 * sc * t);
                pose.mulPose(Axis.XP.rotationDegrees(90f));
                float ringR = trunkR * (1.30f - t * 0.14f);
                drawRing(pose, aura, ringR * 0.80f, ringR, 24,
                        mega ? Domain67CoreShader.MODE_BLACK_FLASH_LOG : Domain67CoreShader.MODE_BROADCAST_RING,
                        t * 240,
                        1.0f, mega ? 0.32f : 0.80f, 0.24f, 0.52f - t * 0.15f);
                pose.popPose();
            }

            pose.popPose();
            super.render(entity, entityYaw, partialTick, pose, buffer, packedLight);
        }
    }

    public static void drawProminentCursedLogMesh(PoseStack pose, MultiBufferSource buffer,
                                                  float sc, float spinDeg, boolean mega) {
        float trunkR = 0.52f * sc;
        float trunkLen = 2.68f * sc;
        float halfLen = trunkLen * 0.5f;

        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(spinDeg));

        // 1. 24-Sided Rich Roasted Dark-Walnut Textured Trunk
        VertexConsumer barkVc = buffer.getBuffer(RenderType.entityCutoutNoCull(CURSED_LOG_TEX));
        drawDarkWalnutTexturedCylinder(pose, barkVc, trunkR, trunkLen, 24);

        VertexConsumer solidVc = buffer.getBuffer(Domain67CoreShader.solidArch());

        // 2. 12 Sculpted 3D Longitudinal Dark-Bark Ridges along the trunk for unmistakable wooden timber texture!
        for (int b = 0; b < 12; b++) {
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees(b * 30.0f));
            float ridgeH = (b % 2 == 0) ? 0.032f * sc : 0.022f * sc;
            Domain67ItemRenderer.drawBox(pose, solidVc,
                    -halfLen * 0.97f, trunkR - 0.01f * sc, -0.042f * sc,
                    halfLen * 0.97f, trunkR + ridgeH, 0.042f * sc,
                    0.22f, 0.11f, 0.05f);
            pose.popPose();
        }

        // 3. High-Contrast Sawed Timber Concentric Growth-Ring End Caps ("亮色木芯6层年轮+十字木裂纹")
        drawHighContrastGrowthRingCap(pose, solidVc, -halfLen, trunkR, -1f);
        drawHighContrastGrowthRingCap(pose, solidVc, halfLen, trunkR, 1f);

        // 4. 3 Heavy Forged Blackened-Iron Hoops ("黑铁重箍") + 12 Gleaming Raised 3D Gold Rivets per Hoop!
        for (float hx : new float[]{-0.88f * sc, 0.0f, 0.88f * sc}) {
            drawBlackIronRivetedHoop(pose, solidVc, hx, trunkR + 0.036f * sc, 0.105f * sc, sc, mega);
        }

        // 5. 24 Forged Silver-Steel & Crimson/Gold-Tipped Spikes in 3 staggered rings
        float[] ringXs = {-0.88f * sc, 0.0f, 0.88f * sc};
        for (int rIdx = 0; rIdx < ringXs.length; rIdx++) {
            float rx = ringXs[rIdx];
            float phaseDeg = rIdx * 22.5f;
            for (int k = 0; k < 8; k++) {
                pose.pushPose();
                pose.translate(rx, 0.0f, 0.0f);
                pose.mulPose(Axis.XP.rotationDegrees(k * 45.0f + phaseDeg));
                pose.translate(0.0f, trunkR + 0.035f * sc, 0.0f);
                drawForgedSteelSpike(pose, solidVc, 0.105f * sc, 0.42f * sc, mega);
                pose.popPose();
            }
        }

        pose.popPose();
    }

    private static void drawHighContrastGrowthRingCap(PoseStack pose, VertexConsumer vc,
                                                      float xPos, float radius, float nx) {
        Matrix4f mat = pose.last().pose();
        int segs = 24;
        // 6 High-Contrast Concentric Wood Rings: Dark Bark -> Bright Cream Pine Sapwood -> Dark Ring -> Warm Heartwood -> Dark Ring -> Pith
        float[] radii = {radius * 1.02f, radius * 0.90f, radius * 0.72f, radius * 0.64f, radius * 0.42f, radius * 0.34f, 0.0f};
        float[][] cols = {
                {0.20f, 0.10f, 0.04f}, // Outer dark bark rim
                {0.95f, 0.82f, 0.58f}, // Bright sawed pine sapwood (huge visual contrast!)
                {0.46f, 0.25f, 0.11f}, // Dark growth ring
                {0.86f, 0.67f, 0.42f}, // Golden-tan heartwood
                {0.40f, 0.21f, 0.09f}, // Inner dark growth ring
                {0.72f, 0.50f, 0.28f}  // Central heartwood pith
        };
        for (int b = 0; b < 6; b++) {
            float rOut = radii[b];
            float rIn = radii[b + 1];
            float cr = cols[b][0], cg = cols[b][1], cb = cols[b][2];
            for (int i = 0; i < segs; i++) {
                float th0 = (float) (Math.PI * 2.0 * i / segs);
                float th1 = (float) (Math.PI * 2.0 * (i + 1) / segs);
                float y0o = Mth.cos(th0) * rOut, z0o = Mth.sin(th0) * rOut;
                float y1o = Mth.cos(th1) * rOut, z1o = Mth.sin(th1) * rOut;
                float y0i = Mth.cos(th0) * rIn, z0i = Mth.sin(th0) * rIn;
                float y1i = Mth.cos(th1) * rIn, z1i = Mth.sin(th1) * rIn;
                Domain67ItemRenderer.putLit(mat, vc, xPos, y0i, z0i, cr, cg, cb, nx, 0f, 0f);
                Domain67ItemRenderer.putLit(mat, vc, xPos, y0o, z0o, cr, cg, cb, nx, 0f, 0f);
                Domain67ItemRenderer.putLit(mat, vc, xPos, y1o, z1o, cr, cg, cb, nx, 0f, 0f);
                Domain67ItemRenderer.putLit(mat, vc, xPos, y1i, z1i, cr, cg, cb, nx, 0f, 0f);
            }
        }
        // Radial natural drying wood crack on end-grain
        float eps = nx * 0.008f;
        Domain67ItemRenderer.drawBox(pose, vc, xPos + eps - 0.004f, -radius * 0.78f, -0.018f,
                xPos + eps + 0.004f, radius * 0.65f, 0.018f, 0.18f, 0.09f, 0.04f);
    }

    private static void drawBlackIronRivetedHoop(PoseStack pose, VertexConsumer vc,
                                                 float centerX, float radius, float halfWidth, float sc, boolean mega) {
        Matrix4f mat = pose.last().pose();
        int segs = 24;
        float x0 = centerX - halfWidth;
        float x1 = centerX + halfWidth;
        // Heavy blackened forged iron band + thin gold border trims
        for (int i = 0; i < segs; i++) {
            float th0 = (float) (Math.PI * 2.0 * i / segs);
            float th1 = (float) (Math.PI * 2.0 * (i + 1) / segs);
            float c0 = Mth.cos(th0), s0 = Mth.sin(th0);
            float c1 = Mth.cos(th1), s1 = Mth.sin(th1);
            Domain67ItemRenderer.putLit(mat, vc, x0, radius * c0, radius * s0, 0.16f, 0.14f, 0.13f, 0f, c0, s0);
            Domain67ItemRenderer.putLit(mat, vc, x1, radius * c0, radius * s0, 0.16f, 0.14f, 0.13f, 0f, c0, s0);
            Domain67ItemRenderer.putLit(mat, vc, x1, radius * c1, radius * s1, 0.16f, 0.14f, 0.13f, 0f, c1, s1);
            Domain67ItemRenderer.putLit(mat, vc, x0, radius * c1, radius * s1, 0.16f, 0.14f, 0.13f, 0f, c1, s1);
        }
        // 12 Gleaming Raised 3D Gold Rivets around the iron hoop
        for (int r = 0; r < 12; r++) {
            pose.pushPose();
            pose.translate(centerX, 0f, 0f);
            pose.mulPose(Axis.XP.rotationDegrees(r * 30.0f + 15.0f));
            float rvR = mega ? 1.0f : 0.96f;
            float rvG = mega ? 0.52f : 0.80f;
            float rvB = mega ? 0.24f : 0.34f;
            Domain67ItemRenderer.drawBox(pose, vc, -0.032f * sc, radius - 0.005f, -0.032f * sc,
                    0.032f * sc, radius + 0.028f * sc, 0.032f * sc, rvR, rvG, rvB);
            pose.popPose();
        }
    }

    private static void drawForgedSteelSpike(PoseStack pose, VertexConsumer vc,
                                             float baseR, float height, boolean mega) {
        Domain67ItemRenderer.drawOctagonalFrustum(pose, vc, baseR * 1.30f, baseR * 1.05f, -0.02f, 0.055f, 8,
                0.94f, 0.77f, 0.33f);
        Matrix4f mat = pose.last().pose();
        int segs = 8;
        float tipR = mega ? 1.00f : 0.94f;
        float tipG = mega ? 0.30f : 0.95f;
        float tipB = mega ? 0.22f : 0.98f;
        for (int i = 0; i < segs; i++) {
            float th0 = (float) (Math.PI * 2.0 * i / segs);
            float th1 = (float) (Math.PI * 2.0 * (i + 1) / segs);
            float c0 = Mth.cos(th0), s0 = Mth.sin(th0);
            float c1 = Mth.cos(th1), s1 = Mth.sin(th1);
            Domain67ItemRenderer.putLit(mat, vc, c0 * baseR, 0.05f, s0 * baseR, 0.56f, 0.58f, 0.62f, c0, 0.35f, s0);
            Domain67ItemRenderer.putLit(mat, vc, c1 * baseR, 0.05f, s1 * baseR, 0.56f, 0.58f, 0.62f, c1, 0.35f, s1);
            Domain67ItemRenderer.putLit(mat, vc, 0f, height, 0f, tipR, tipG, tipB, 0f, 1f, 0f);
            Domain67ItemRenderer.putLit(mat, vc, 0f, height, 0f, tipR, tipG, tipB, 0f, 1f, 0f);
        }
    }

    private static void drawDarkWalnutTexturedCylinder(PoseStack pose, VertexConsumer vc,
                                                       float radius, float length, int segs) {
        Matrix4f mat = pose.last().pose();
        float half = length * 0.5f;
        for (int i = 0; i < segs; i++) {
            float u0 = (float) i / segs;
            float u1 = (float) (i + 1) / segs;
            float th0 = u0 * (float) (Math.PI * 2.0);
            float th1 = u1 * (float) (Math.PI * 2.0);
            float c0 = Mth.cos(th0), s0 = Mth.sin(th0);
            float c1 = Mth.cos(th1), s1 = Mth.sin(th1);
            float sh = 0.58f + 0.28f * Math.max(0f, c0);
            putStdVert(mat, vc, -half, radius * c0, radius * s0, u0, 0f, 0.68f * sh, 0.44f * sh, 0.26f * sh, 1f, 0f, c0, s0);
            putStdVert(mat, vc, half, radius * c0, radius * s0, u0, 1f, 0.68f * sh, 0.44f * sh, 0.26f * sh, 1f, 0f, c0, s0);
            putStdVert(mat, vc, half, radius * c1, radius * s1, u1, 1f, 0.68f * sh, 0.44f * sh, 0.26f * sh, 1f, 0f, c1, s1);
            putStdVert(mat, vc, -half, radius * c1, radius * s1, u1, 0f, 0.68f * sh, 0.44f * sh, 0.26f * sh, 1f, 0f, c1, s1);
        }
    }

    private static void drawCurtainHemisphere(PoseStack pose, VertexConsumer vc, float radius, int segs, int rings,
                                              int mode, int phase) {
        Matrix4f mat = pose.last().pose();
        for (int iy = 0; iy < rings; iy++) {
            float v0 = (float) iy / rings;
            float v1 = (float) (iy + 1) / rings;
            float phi0 = Mth.lerp(v0, -0.12f, (float) (Math.PI * 0.5));
            float phi1 = Mth.lerp(v1, -0.12f, (float) (Math.PI * 0.5));
            float y0 = Mth.sin(phi0) * radius, r0 = Mth.cos(phi0) * radius;
            float y1 = Mth.sin(phi1) * radius, r1 = Mth.cos(phi1) * radius;
            for (int ix = 0; ix < segs; ix++) {
                float u0 = (float) ix / segs;
                float u1 = (float) (ix + 1) / segs;
                float th0 = u0 * (float) (Math.PI * 2.0);
                float th1 = u1 * (float) (Math.PI * 2.0);
                float c0 = Mth.cos(th0), s0 = Mth.sin(th0);
                float c1 = Mth.cos(th1), s1 = Mth.sin(th1);
                putVert(mat, vc, r0 * c0, y0, r0 * s0, u0, v0, mode, phase, 1f, 1f, 1f, 1f, c0, Mth.sin(phi0), s0);
                putVert(mat, vc, r0 * c1, y0, r0 * s1, u1, v0, mode, phase, 1f, 1f, 1f, 1f, c1, Mth.sin(phi0), s1);
                putVert(mat, vc, r1 * c1, y1, r1 * s1, u1, v1, mode, phase, 1f, 1f, 1f, 1f, c1, Mth.sin(phi1), s1);
                putVert(mat, vc, r1 * c0, y1, r1 * s0, u0, v1, mode, phase, 1f, 1f, 1f, 1f, c0, Mth.sin(phi1), s0);
            }
        }
    }

    public static void onRenderHand(RenderHandEvent event) {
        float p = Domain67ClientCache.handSignProgress(event.getPartialTick());
        if (p < 0f) return;
        float env = Mth.sin(p * (float) Math.PI);
        float side = event.getHand() == InteractionHand.MAIN_HAND ? 1f : -1f;
        float osc = Mth.sin(p * (float) Math.PI * 6.0f) * side;
        PoseStack pose = event.getPoseStack();
        pose.translate(-0.14f * side * env, 0.08f * env + osc * 0.10f * env, -0.18f * env);
        pose.mulPose(Axis.XP.rotationDegrees(-22f * env + osc * 14f * env));
        pose.mulPose(Axis.ZP.rotationDegrees(16f * side * env));
    }

    public static void onCameraShake(ViewportEvent.ComputeCameraAngles event) {
        float amt = Domain67ClientCache.cameraShakeAmount((float) event.getPartialTick());
        if (amt <= 0.01f) return;
        float t = Domain67ClientCache.ticks() + (float) event.getPartialTick();
        event.setYaw(event.getYaw() + Mth.sin(t * 3.1f) * amt * 1.3f);
        event.setPitch(event.getPitch() + Mth.cos(t * 3.6f) * amt * 1.0f);
        event.setRoll(event.getRoll() + Mth.sin(t * 2.4f) * amt * 1.6f);
    }

    private static void drawRing(PoseStack pose, VertexConsumer vc, float rIn, float rOut, int segs,
                                 int mode, int phase, float r, float g, float b, float a) {
        Matrix4f mat = pose.last().pose();
        for (int i = 0; i < segs; i++) {
            float u0 = (float) i / segs;
            float u1 = (float) (i + 1) / segs;
            float th0 = u0 * (float) (Math.PI * 2.0);
            float th1 = u1 * (float) (Math.PI * 2.0);
            float c0 = Mth.cos(th0), s0 = Mth.sin(th0);
            float c1 = Mth.cos(th1), s1 = Mth.sin(th1);
            putVert(mat, vc, rIn * c0, 0f, rIn * s0, u0, 0f, mode, phase, r, g, b, a, 0f, 1f, 0f);
            putVert(mat, vc, rOut * c0, 0f, rOut * s0, u0, 1f, mode, phase, r, g, b, a, 0f, 1f, 0f);
            putVert(mat, vc, rOut * c1, 0f, rOut * s1, u1, 1f, mode, phase, r, g, b, a, 0f, 1f, 0f);
            putVert(mat, vc, rIn * c1, 0f, rIn * s1, u1, 0f, mode, phase, r, g, b, a, 0f, 1f, 0f);
        }
    }

    private static void drawQuad(PoseStack pose, VertexConsumer vc, float half,
                                 int mode, int phase, float r, float g, float b, float a) {
        Matrix4f mat = pose.last().pose();
        putVert(mat, vc, -half, -half, 0f, 0f, 0f, mode, phase, r, g, b, a, 0f, 0f, 1f);
        putVert(mat, vc, half, -half, 0f, 1f, 0f, mode, phase, r, g, b, a, 0f, 0f, 1f);
        putVert(mat, vc, half, half, 0f, 1f, 1f, mode, phase, r, g, b, a, 0f, 0f, 1f);
        putVert(mat, vc, -half, half, 0f, 0f, 1f, mode, phase, r, g, b, a, 0f, 0f, 1f);
    }

    private static void putStdVert(Matrix4f mat, VertexConsumer vc, float x, float y, float z,
                                   float u, float v, float r, float g, float b, float a,
                                   float nx, float ny, float nz) {
        vc.addVertex(mat, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(nx, ny, nz);
    }

    private static void putVert(Matrix4f mat, VertexConsumer vc, float x, float y, float z,
                                float u, float v, int mode, int phase,
                                float r, float g, float b, float a,
                                float nx, float ny, float nz) {
        vc.addVertex(mat, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.pack(mode, phase))
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(nx, ny, nz);
    }
}
