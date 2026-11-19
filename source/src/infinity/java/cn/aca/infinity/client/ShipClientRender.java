package cn.aca.infinity.client;

import cn.aca.infinity.AcaInfinity;
import cn.aca.infinity.InfinityNetwork;
import cn.aca.infinity.SanctuaryShipEntity;
import cn.aca.infinity.ShipOrdnanceEntity;
import cn.aca.infinity.StoneAbilities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class ShipClientRender {
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    private static boolean triggerCannon;
    private static boolean triggerMissile;
    // 0 = 3A Holographic Cockpit (FP), 1 = 14.5m Cinematic Rear (TP), 2 = 22m Orbital Tactical (TP)
    private static int cameraMode = 1;

    private static final Matrix4f LAST_MV = new Matrix4f();
    private static final Matrix4f LAST_PROJ = new Matrix4f();
    private static Vec3 lastCamPos = Vec3.ZERO;
    private static boolean hasMatrices = false;

    private ShipClientRender() {}

    public static void queueWeaponTrigger(boolean cannon, boolean missile) {
        if (cannon) triggerCannon = true;
        if (missile) triggerMissile = true;
    }

    public static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() instanceof SanctuaryShipEntity) {
            event.setDistance(cameraMode == 2 ? 22.0f : 14.5f);
        }
    }

    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (!(mc.player.getVehicle() instanceof SanctuaryShipEntity)) return;

        ResourceLocation name = event.getName();
        if (name.equals(VanillaGuiLayers.CROSSHAIR)
                || name.equals(VanillaGuiLayers.HOTBAR)
                || name.equals(VanillaGuiLayers.PLAYER_HEALTH)
                || name.equals(VanillaGuiLayers.ARMOR_LEVEL)
                || name.equals(VanillaGuiLayers.FOOD_LEVEL)
                || name.equals(VanillaGuiLayers.VEHICLE_HEALTH)
                || name.equals(VanillaGuiLayers.AIR_LEVEL)
                || name.equals(VanillaGuiLayers.EXPERIENCE_BAR)
                || name.equals(VanillaGuiLayers.EXPERIENCE_LEVEL)
                || name.equals(VanillaGuiLayers.JUMP_METER)
                || name.equals(VanillaGuiLayers.SELECTED_ITEM_NAME)) {
            event.setCanceled(true);
        }
    }

    public static void onCaptureMatrices(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        LAST_MV.set(event.getModelViewMatrix());
        LAST_PROJ.set(event.getProjectionMatrix());
        lastCamPos = event.getCamera().getPosition();
        hasMatrices = true;
    }

    public static void tickPilotControls(Minecraft mc, SanctuaryShipEntity ship) {
        int bits = 0;
        if (mc.options.keyUp.isDown()) bits |= SanctuaryShipEntity.IN_FWD;
        if (mc.options.keyDown.isDown()) bits |= SanctuaryShipEntity.IN_BACK;
        if (mc.options.keyLeft.isDown()) bits |= SanctuaryShipEntity.IN_LEFT;
        if (mc.options.keyRight.isDown()) bits |= SanctuaryShipEntity.IN_RIGHT;
        if (mc.options.keyJump.isDown()) bits |= SanctuaryShipEntity.IN_ASCEND;
        if (InfinityClient.KEY_SHIP_DESCEND.isDown()) bits |= SanctuaryShipEntity.IN_DESCEND;
        if (mc.options.keySprint.isDown()) bits |= SanctuaryShipEntity.IN_BOOST;

        while (InfinityClient.KEY_SHIP_SHIELD.consumeClick()) bits |= SanctuaryShipEntity.IN_SHIELD_TOGGLE;
        while (InfinityClient.KEY_SHIP_RAIN_FIRE.consumeClick()) bits |= SanctuaryShipEntity.IN_RAIN_FIRE;
        while (InfinityClient.KEY_SHIP_ESCORT.consumeClick()) bits |= SanctuaryShipEntity.IN_ESCORT_TOGGLE;
        while (InfinityClient.KEY_SHIP_WARP.consumeClick()) bits |= SanctuaryShipEntity.IN_WARP;
        while (InfinityClient.KEY_SHIP_CAMERA.consumeClick()) {
            cameraMode = (cameraMode + 1) % 3;
            mc.options.setCameraType(cameraMode == 0 ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK);
            String key = cameraMode == 0 ? "msg.aca_infinity.ship_cam_cockpit"
                    : cameraMode == 1 ? "msg.aca_infinity.ship_cam_rear" : "msg.aca_infinity.ship_cam_orbital";
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable(key), true);
            }
        }

        if (triggerCannon || mc.options.keyAttack.isDown()) {
            bits |= SanctuaryShipEntity.IN_CANNON;
            triggerCannon = false;
        }
        if (triggerMissile || mc.options.keyUse.isDown()) {
            bits |= SanctuaryShipEntity.IN_MISSILE;
            triggerMissile = false;
        }
        InfinityNetwork.sendToServer(InfinityNetwork.ACT_SHIP_CONTROL, bits, ship.getId());
    }

    public static void renderBridgeHud(GuiGraphics g, DeltaTracker delta, Minecraft mc, SanctuaryShipEntity ship) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        Font font = mc.font;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float time = ship.tickCount + partial;
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        float cx = w * 0.5f;
        float cy = h * 0.5f;

        boolean shieldOn = ship.isShieldActive();
        boolean escortOn = ship.isEscortActive();
        float hullRatio = Mth.clamp(ship.hull() / (float) SanctuaryShipEntity.MAX_HULL, 0f, 1f);
        float energyRatio = Mth.clamp(ship.energy() / (float) SanctuaryShipEntity.MAX_ENERGY, 0f, 1f);
        float cannonReady = 1f - Mth.clamp(ship.cannonCooldown() / 18f, 0f, 1f);
        float missileReady = 1f - Mth.clamp(ship.missileCooldown() / 36f, 0f, 1f);
        float rainReady = 1f - Mth.clamp(ship.rainFireCooldown() / 140f, 0f, 1f);
        float warpReady = 1f - Mth.clamp(ship.warpCooldown() / 100f, 0f, 1f);

        int goldMain = 0xffdfb868;
        int goldBright = 0xfff8e2a6;
        int cyanBright = 0xffb8f2ff;

        if (firstPerson) {
            g.fillGradient(0, 0, w, 24, 0x26081c30, 0x00081c30);
            g.fillGradient(0, h - 24, w, h, 0x00081c30, 0x38050b16);
            int holoRib = 0x4066d8ff;
            int holoGold = 0x48dfb868;
            StarDialDraw.line(g, 0, h * 0.20f, w * 0.11f, 0, 1.1f, holoRib);
            StarDialDraw.line(g, w, h * 0.20f, w * 0.89f, 0, 1.1f, holoRib);
            StarDialDraw.line(g, 0, h * 0.82f, w * 0.09f, h, 1.0f, holoGold);
            StarDialDraw.line(g, w, h * 0.82f, w * 0.91f, h, 1.0f, holoGold);
        } else {
            g.fillGradient(0, 0, w, 20, 0x3204070f, 0x0004070f);
            g.fillGradient(0, h - 22, w, h, 0x0004070f, 0x4404070f);
        }

        // ==================== 1. TOP FRAMELESS HOLOGRAPHIC HEADING TAPE (ZERO BLACK BOX) ====================
        StarDialDraw.line(g, cx - 108f, 15.5f, cx + 108f, 15.5f, 0.8f, 0x50dfb868);
        String spdStr = String.format("SPD %03.0f", Math.abs(ship.flightSpeed()) * 84f);
        String altStr = String.format("ALT %+.0fm", ship.getY());
        boolean boosting = Math.abs(ship.flightSpeed()) > 0.96f;
        g.drawString(font, spdStr, Math.round(cx - 154f), 6, boosting ? cyanBright : 0xccbfd4f2, true);
        g.drawString(font, altStr, Math.round(cx + 114f), 6, 0xccbfd4f2, true);

        float heading = Mth.positiveModulo(Mth.rotLerp(partial, ship.yRotO, ship.getYRot()), 360f);
        String[] cardinals = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        for (int deg = -75; deg <= 75; deg += 15) {
            int worldDeg = Math.floorMod(Math.round(heading / 15f) * 15 + deg, 360);
            float deltaDeg = Mth.wrapDegrees(worldDeg - heading);
            if (Math.abs(deltaDeg) > 74f) continue;
            float tx = cx + deltaDeg * 1.32f;
            boolean major = (worldDeg % 45) == 0;
            StarDialDraw.line(g, tx, 12.2f, tx, major ? 15.2f : 14.0f,
                    major ? 1.1f : 0.75f, major ? 0xccdfb868 : 0x669ab0cc);
            if (major) {
                String lbl = cardinals[(worldDeg / 45) % 8];
                g.drawString(font, lbl, Math.round(tx - font.width(lbl) * 0.5f), 3,
                        lbl.equals("N") ? 0xffff8a70 : 0xdde8d8b4, true);
            }
        }
        StarDialDraw.diamond(g, cx, 15.5f, 2.2f, 2.2f, shieldOn ? cyanBright : goldBright, 0);

        // ==================== 2. 95PX WIDE-CLEARANCE RETICLE & ULTRA-THIN TELEMETRY ARCS ====================
        Entity lockedTarget = findAndRenderTargetLock(g, font, mc, ship, partial, time);

        int hullArcCol = hullRatio < 0.30f ? 0xccff525c : 0xb8dfb868;
        int hullTipCol = hullRatio < 0.30f ? 0xffffb3b8 : goldBright;
        StarDialDraw.segmentedArc(g, cx, cy, 94.5f, 96.4f,
                156f * DEG_TO_RAD, 204f * DEG_TO_RAD, 10, hullRatio,
                hullArcCol, hullTipCol, 0x261a2438);
        StarDialDraw.segmentedArc(g, cx, cy, 94.5f, 96.4f,
                24f * DEG_TO_RAD, -24f * DEG_TO_RAD, 10, energyRatio,
                0xb84ec4ff, cyanBright, 0x261a2438);

        if (shieldOn) {
            float pulse = 0.52f + 0.24f * Mth.sin(time * 0.22f);
            StarDialDraw.ring(g, cx, cy, 98.2f, 99.3f, -26f * DEG_TO_RAD, 26f * DEG_TO_RAD,
                    StarDialDraw.withAlpha(0xff7ce8ff, pulse));
            StarDialDraw.ring(g, cx, cy, 98.2f, 99.3f, 154f * DEG_TO_RAD, 206f * DEG_TO_RAD,
                    StarDialDraw.withAlpha(0xff7ce8ff, pulse));
        }

        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-ship.bankRoll()));
        float pitchShift = Mth.clamp(-Mth.lerp(partial, ship.xRotO, ship.getXRot()) * 0.45f, -14f, 14f);
        int horCol = 0x58d8c28c;
        StarDialDraw.line(g, -68f, pitchShift, -48f, pitchShift, 0.9f, horCol);
        StarDialDraw.line(g, 48f, pitchShift, 68f, pitchShift, 0.9f, horCol);
        pose.popPose();

        float recoilSpread = (1f - cannonReady) * 5.5f;
        float retR = 9.5f + recoilSpread;
        int retAccent = lockedTarget != null ? 0xffff5878 : (shieldOn ? cyanBright : goldBright);
        for (int q = 0; q < 4; q++) {
            float baseA = (45f + q * 90f) * DEG_TO_RAD;
            StarDialDraw.ring(g, cx, cy, retR, retR + 1.15f, baseA - 0.36f, baseA + 0.36f, retAccent);
        }
        StarDialDraw.diamond(g, cx, cy, 1.4f, 1.4f, 0xeeffffff, 0);

        // ==================== 3. SLEEK 8PX BOTTOM-EDGE HOLOGRAPHIC TELEMETRY RIBBON ====================
        float ribW = 368f;
        float ribX = cx - ribW * 0.5f;
        float ribY = h - 13f;
        StarDialDraw.line(g, ribX, ribY + 6.5f, ribX + ribW, ribY + 6.5f, 0.9f, 0x66dfb868);
        StarDialDraw.skewedCells(g, ribX, ribY + 1.5f, 118f, 3.8f,
                10, hullRatio, 2.0f, hullArcCol, hullTipCol, 0x44151e30);
        StarDialDraw.skewedCells(g, ribX + ribW - 118f, ribY + 1.5f, 118f, 3.8f,
                10, energyRatio, -2.0f, 0xcc4ec4ff, cyanBright, 0x44151e30);
        StarDialDraw.diamond(g, cx, ribY + 3.2f, 4.2f, 4.2f, shieldOn ? cyanBright : goldMain, 0);

        String leftTele = String.format("HP %d  %s[R]盾 %s[B]护航",
                ship.hull(), shieldOn ? "◆" : "◇", escortOn ? "◆" : "◇");
        String rightTele = (cannonReady >= 0.99f ? "●主炮 " : "○主炮 ")
                + (missileReady >= 0.99f ? "●导弹 " : "○导弹 ")
                + (rainReady >= 0.99f ? "✦[V]火雨 " : "[V]" + (int) Math.ceil(ship.rainFireCooldown() / 20.0) + "s ")
                + (warpReady >= 0.99f ? "✦[X]跃迁" : "[X]" + (int) Math.ceil(ship.warpCooldown() / 20.0) + "s");
        g.drawString(font, leftTele, Math.round(ribX), Math.round(ribY - 9f), 0xdde8dec6, true);
        g.drawString(font, rightTele, Math.round(ribX + ribW - font.width(rightTele)), Math.round(ribY - 9f), 0xddcce2ff, true);
        g.flush();
    }

    private static Entity findAndRenderTargetLock(GuiGraphics g, Font font, Minecraft mc,
                                                  SanctuaryShipEntity ship, float partial, float time) {
        if (!hasMatrices || mc.level == null || mc.player == null) return null;
        Vec3 eye = mc.player.getEyePosition(partial);
        Vec3 look = mc.player.getViewVector(partial);

        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, ship.getBoundingBox().inflate(68.0), LivingEntity::isAlive)) {
            if (e == mc.player || e.getVehicle() == ship) continue;
            if (StoneAbilities.isFriendlyTo(mc.player, e) && !(e instanceof Enemy)) continue;
            Vec3 center = e.getBoundingBox().getCenter();
            Vec3 to = center.subtract(eye);
            double dist = to.length();
            if (dist < 2.0 || dist > 68.0) continue;
            double dot = to.scale(1.0 / dist).dot(look);
            if (dot < 0.84) continue;
            double score = dist * (1.25 - dot);
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        if (best == null) return null;

        double tx = Mth.lerp(partial, best.xOld, best.getX()) - lastCamPos.x;
        double ty = Mth.lerp(partial, best.yOld, best.getY()) + best.getBbHeight() * 0.55 - lastCamPos.y;
        double tz = Mth.lerp(partial, best.zOld, best.getZ()) - lastCamPos.z;

        Vector4f clip = new Vector4f((float) tx, (float) ty, (float) tz, 1.0f);
        clip.mul(LAST_MV);
        clip.mul(LAST_PROJ);
        if (clip.w <= 0.05f) return null;

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        if (Math.abs(ndcX) > 0.92f || Math.abs(ndcY) > 0.92f) return null;

        float sx = (ndcX * 0.5f + 0.5f) * g.guiWidth();
        float sy = (0.5f - ndcY * 0.5f) * g.guiHeight();

        var pose = g.pose();
        pose.pushPose();
        pose.translate(sx, sy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(time * 3.2f));
        StarDialDraw.diamond(g, 0, 0, 10.5f, 10.5f, 0x16ff4070, 0xffff5882);
        pose.popPose();

        float br = 12f;
        int lockCol = 0xffffcc68;
        StarDialDraw.line(g, sx - br, sy - br, sx - br + 4.0f, sy - br, 1.0f, lockCol);
        StarDialDraw.line(g, sx - br, sy - br, sx - br, sy - br + 4.0f, 1.0f, lockCol);
        StarDialDraw.line(g, sx + br - 4.0f, sy - br, sx + br, sy - br, 1.0f, lockCol);
        StarDialDraw.line(g, sx + br, sy - br, sx + br, sy - br + 4.0f, 1.0f, lockCol);
        StarDialDraw.line(g, sx - br, sy + br - 4.0f, sx - br, sy + br, 1.0f, lockCol);
        StarDialDraw.line(g, sx - br, sy + br, sx - br + 4.0f, sy + br, 1.0f, lockCol);
        StarDialDraw.line(g, sx + br, sy + br - 4.0f, sx + br, sy + br, 1.0f, lockCol);
        StarDialDraw.line(g, sx + br - 4.0f, sy + br, sx + br, sy + br, 1.0f, lockCol);

        if (best instanceof LivingEntity le) {
            int pct = Math.round(Mth.clamp(le.getHealth() / Math.max(1f, le.getMaxHealth()), 0f, 1f) * 100f);
            String tag = best.getDisplayName().getString() + " · " + pct + "%";
            g.drawCenteredString(font, tag, Math.round(sx), Math.round(sy - br - 10f), 0xffffd88a);
        }
        return best;
    }

    public static final class ShipRenderer extends EntityRenderer<SanctuaryShipEntity> {
        public ShipRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.shadowRadius = 4.2f;
        }

        @Override
        public void render(SanctuaryShipEntity ship, float entityYaw, float partial, PoseStack pose,
                           MultiBufferSource buffers, int packedLight) {
            InfinityCosmicShader.updateUniforms(partial);
            var mc = Minecraft.getInstance();
            boolean hideCitadelForFp = mc.player != null
                    && mc.player.getVehicle() == ship
                    && mc.options.getCameraType().isFirstPerson();

            float yaw = Mth.rotLerp(partial, ship.yRotO, ship.getYRot());
            float pitch = Mth.lerp(partial, ship.xRotO, ship.getXRot());
            float roll = ship.bankRoll();
            int colTicks = ship.collapseTicks();
            float time = ship.tickCount + partial;

            float collapseProgress = colTicks > 0 ? Mth.clamp((colTicks + partial) / 36f, 0f, 1f) : 0f;
            int hullColor = colTicks > 0
                    ? StarDialDraw.withAlpha(0xffffc880, 1f - collapseProgress)
                    : -1;
            float scale = 1.65f * (1f - collapseProgress * 0.22f);

            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.ZP.rotationDegrees(roll));

            float wingFlex = Mth.sin(time * 0.08f) * 1.6f + Mth.clamp(Math.abs(ship.flightSpeed()) * 2.8f, 0f, 2.5f);
            InfinityRig.PoseProvider anim = boneId -> {
                if (hideCitadelForFp && (boneId.equals("bridge_citadel")
                        || boneId.equals("citadel_horn_port") || boneId.equals("citadel_horn_starboard"))) {
                    return InfinityRig.BonePose.HIDDEN;
                }
                return switch (boneId) {
                    case "wing_port_outer" -> InfinityRig.BonePose.rot(0f, 0f, wingFlex);
                    case "wing_starboard_outer" -> InfinityRig.BonePose.rot(0f, 0f, -wingFlex);
                    default -> InfinityRig.BonePose.IDENTITY;
                };
            };

            InfinityRig.get("sanctuary_ship").render(pose, buffers, anim, scale, hullColor,
                    packedLight, OverlayTexture.NO_OVERLAY);

            if (colTicks == 0) {
                float spd = Math.abs(ship.flightSpeed());
                float plumeLen = 2.4f + Mth.clamp(spd * 6.0f, 0f, 7.5f);
                int ionCol = ship.isHostileShip() ? 0xd8ff4d94 : 0xd868b4ff;
                InfinityVfxPrimitives.silkSlipstream(pose, buffers,
                        new Vec3(-0.32 * scale, 1.12 * scale, -2.1 * scale),
                        new Vec3(-0.32 * scale, 1.12 * scale, (-2.1 - plumeLen) * scale),
                        0.26f, 0.04f, ionCol, 0.20f);
                InfinityVfxPrimitives.silkSlipstream(pose, buffers,
                        new Vec3(0.32 * scale, 1.12 * scale, -2.1 * scale),
                        new Vec3(0.32 * scale, 1.12 * scale, (-2.1 - plumeLen) * scale),
                        0.26f, 0.04f, ionCol, 0.20f);

                if (spd > 0.03f || ship.isVehicle()) {
                    float tipLen = 3.0f + Mth.clamp(spd * 8.5f, 0f, 10.0f);
                    InfinityVfxPrimitives.silkSlipstream(pose, buffers,
                            new Vec3(-5.35 * scale, 1.18 * scale, -1.1 * scale),
                            new Vec3(-5.55 * scale, 1.18 * scale, (-1.1 - tipLen) * scale),
                            0.13f, 0.02f, 0xd09be6ff, 0.28f);
                    InfinityVfxPrimitives.silkSlipstream(pose, buffers,
                            new Vec3(5.35 * scale, 1.18 * scale, -1.1 * scale),
                            new Vec3(5.55 * scale, 1.18 * scale, (-1.1 - tipLen) * scale),
                            0.13f, 0.02f, 0xd09be6ff, 0.28f);
                }

                if (ship.isShieldActive()) {
                    pose.pushPose();
                    pose.translate(0, 1.85, 0);
                    for (int ring = 0; ring < 3; ring++) {
                        pose.pushPose();
                        pose.mulPose(Axis.YP.rotationDegrees(time * 2.0f + ring * 60f));
                        pose.mulPose(Axis.XP.rotationDegrees(20f + ring * 52f));
                        InfinityVfxPrimitives.armillaryRing(pose, buffers, 6.45f, 7.05f, 40,
                                0xb866d8ff, time * 1.4f, 0.08f);
                        pose.popPose();
                    }
                    pose.popPose();
                }
            }

            pose.popPose();
            super.render(ship, entityYaw, partial, pose, buffers, packedLight);
        }

        @Override
        public ResourceLocation getTextureLocation(SanctuaryShipEntity entity) {
            return AcaInfinity.id("textures/rig/sanctuary_ship.png");
        }
    }

    public static final class OrdnanceRenderer extends EntityRenderer<ShipOrdnanceEntity> {
        public OrdnanceRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public void render(ShipOrdnanceEntity entity, float yaw, float partial, PoseStack pose,
                           MultiBufferSource buffers, int light) {
            InfinityCosmicShader.updateUniforms(partial);
            Vec3 vel = entity.getDeltaMovement();
            Vec3 tail = vel.lengthSqr() > 1e-4 ? vel.normalize().scale(-1.35) : new Vec3(0, 0, -1.0);
            pose.pushPose();
            pose.translate(0, 0.18, 0);
            InfinityVfxPrimitives.dualPassBeam(pose, buffers, Vec3.ZERO, tail, 0.18f,
                    0xd82b0718, 0xfff54ca0, 0.18f);
            pose.popPose();
        }

        @Override
        public ResourceLocation getTextureLocation(ShipOrdnanceEntity entity) {
            return AcaInfinity.id("textures/rig/sanctuary_ship.png");
        }
    }
}
