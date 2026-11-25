package cn.aca.infinity.client;

import cn.aca.infinity.AcaInfinity;
import cn.aca.infinity.GauntletData;
import cn.aca.infinity.SanctuaryBeaconItem;
import cn.aca.infinity.Stone;
import cn.aca.infinity.StoneItem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;

public final class GauntletRender {
    private static final Map<String, Stone> SOCKET_BONES = socketBones();
    private static BlockEntityWithoutLevelRenderer itemRenderer;
    private static int lastSeenSelected = -1;
    private static long switchInspectTick = -100L;

    private GauntletRender() {}

    private static Map<String, Stone> socketBones() {
        Map<String, Stone> bones = new HashMap<>();
        for (Stone stone : Stone.values()) {
            bones.put("gem_" + stone.id, stone);
            bones.put("vein_" + stone.id, stone);
        }
        return Map.copyOf(bones);
    }

    public static InfinityRig.PoseProvider stoneCrystalPose(Stone stone, float spinDeg, float pulseScale) {
        String coreId = "stone_" + stone.id;
        String facetId = coreId + "_facet";
        return boneId -> {
            if (boneId.equals("root")) return InfinityRig.BonePose.IDENTITY;
            if (boneId.equals(coreId)) {
                return new InfinityRig.BonePose(0f, 0f, 0f, 12f, spinDeg, 8f, pulseScale, -1);
            }
            if (boneId.equals(facetId)) {
                return new InfinityRig.BonePose(0f, 0f, 0f, 0f, -spinDeg * 0.45f, 0f, 1.0f, -1);
            }
            return InfinityRig.BonePose.HIDDEN;
        };
    }

    public static InfinityRig.PoseProvider gauntletPose(GauntletData data, boolean snapping, float snapProgress) {
        return gauntletPose(data, snapping, snapProgress, 0f, 0f);
    }

    public static InfinityRig.PoseProvider gauntletPose(GauntletData data, boolean snapping, float snapProgress,
                                                        float castProgress, float switchPulse) {
        float pinch = Mth.sin(Mth.clamp(snapProgress, 0f, 1f) * (float) Math.PI);
        float cast = Mth.sin(Mth.clamp(castProgress, 0f, 1f) * (float) Math.PI);
        float inspect = Mth.sin(switchPulse * (float) Math.PI);
        return boneId -> {
            Stone stone = SOCKET_BONES.get(boneId);
            if (stone != null) return data.has(stone) ? InfinityRig.BonePose.IDENTITY : InfinityRig.BonePose.HIDDEN;
            if (snapping) {
                return switch (boneId) {
                    case "thumb" -> InfinityRig.BonePose.rot(-22f * pinch, -3f * pinch, 55.5f * pinch);
                    case "thumb_tip" -> InfinityRig.BonePose.rot(9.5f * pinch, -3f * pinch, -3.5f * pinch);
                    case "finger_middle" -> InfinityRig.BonePose.rot(-32f * pinch, 0f, 4.7f * pinch);
                    case "finger_middle_mid" -> InfinityRig.BonePose.rot(-58f * pinch, 0f, 0f);
                    case "finger_middle_tip" -> InfinityRig.BonePose.rot(-31f * pinch, 0f, 0f);
                    case "finger_ring", "finger_pinky" -> InfinityRig.BonePose.rot(-47f * pinch, 0f, 0f);
                    case "finger_ring_mid", "finger_pinky_mid" -> InfinityRig.BonePose.rot(-52f * pinch, 0f, 0f);
                    case "finger_index" -> InfinityRig.BonePose.rot(7f * pinch, 0f, -3f * pinch);
                    default -> InfinityRig.BonePose.IDENTITY;
                };
            }
            if (castProgress > 0.01f) {
                return switch (boneId) {
                    case "finger_index", "finger_middle", "finger_ring", "finger_pinky" ->
                            InfinityRig.BonePose.rot(-18f * cast, 0f, 0f);
                    case "finger_index_mid", "finger_middle_mid", "finger_ring_mid", "finger_pinky_mid" ->
                            InfinityRig.BonePose.rot(-14f * cast, 0f, 0f);
                    case "thumb" -> InfinityRig.BonePose.rot(-12f * cast, -6f * cast, 12f * cast);
                    default -> InfinityRig.BonePose.IDENTITY;
                };
            }
            if (switchPulse > 0.01f) {
                if (boneId.startsWith("finger_") && !boneId.contains("_mid") && !boneId.contains("_tip")) {
                    return InfinityRig.BonePose.rot(-8f * inspect, 0f, 0f);
                }
            }
            return InfinityRig.BonePose.IDENTITY;
        };
    }

    public static BlockEntityWithoutLevelRenderer customItemRenderer() {
        if (itemRenderer == null) {
            var mc = Minecraft.getInstance();
            itemRenderer = new BlockEntityWithoutLevelRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels()) {
                @Override
                public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                                         MultiBufferSource buffers, int light, int overlay) {
                    float time = (mc.level != null ? mc.level.getGameTime() : 0L)
                            + mc.getTimer().getGameTimeDeltaPartialTick(false);
                    boolean isGui = ctx == ItemDisplayContext.GUI;
                    boolean isGround = ctx == ItemDisplayContext.GROUND;
                    boolean isFixed = ctx == ItemDisplayContext.FIXED;
                    int renderLight = isGui ? LightTexture.FULL_BRIGHT : light;

                    if (stack.getItem() instanceof StoneItem stoneItem) {
                        Stone st = stoneItem.stone;
                        pose.pushPose();
                        float bob = isGround ? Mth.sin(time * 0.10f) * 0.09f : (isGui ? Mth.sin(time * 0.08f) * 0.025f : 0f);
                        pose.translate(0.5, 0.50 + bob, 0.5);
                        float spin = isGui ? time * 1.6f : (isFixed ? time * 1.2f : time * 2.4f);
                        float scale = isGui ? 1.28f : (isFixed ? 1.18f : 0.92f);
                        float pulse = 1.0f + 0.045f * Mth.sin(time * 0.14f + st.ordinal());
                        InfinityRig.get("stone_crystal").render(pose, buffers,
                                stoneCrystalPose(st, spin, pulse), scale, -1, LightTexture.FULL_BRIGHT, overlay);
                        pose.popPose();
                        return;
                    }

                    if (stack.getItem() instanceof SanctuaryBeaconItem beaconItem) {
                        pose.pushPose();
                        float bob = isGround ? Mth.sin(time * 0.09f) * 0.08f : Mth.sin(time * 0.07f) * 0.02f;
                        pose.translate(0.5, 0.36 + bob, 0.5);
                        InfinityRig.rotate(pose, isGui ? 16f : 10f, time * (isGui ? 1.3f : 2.1f), 0f);
                        int holoTint = beaconItem.hostile ? 0xd8ff78a8 : 0xd89ae2ff;
                        float shipScale = isGui ? 0.135f : (isFixed ? 0.15f : 0.115f);
                        InfinityRig.get("sanctuary_ship").render(pose, buffers,
                                InfinityRig.STILL, shipScale, holoTint, LightTexture.FULL_BRIGHT, overlay);
                        pose.popPose();
                        return;
                    }

                    GauntletData data = stack.getOrDefault(AcaInfinity.GAUNTLET_DATA.get(), GauntletData.EMPTY);
                    pose.pushPose();
                    float bob = isGround ? Mth.sin(time * 0.08f) * 0.07f : 0f;
                    pose.translate(0.5, 0.48 + bob, 0.5);
                    if (isGround) {
                        InfinityRig.rotate(pose, 8f, time * 1.8f, 0f);
                    } else if (isFixed) {
                        InfinityRig.rotate(pose, 0f, -8f, 0f);
                    } else if (isGui) {
                        InfinityRig.rotate(pose, 0f, Mth.sin(time * 0.05f) * 4f, 0f);
                    }
                    boolean rightHand = ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                            || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
                    InfinityRig.get("infinity_gauntlet").render(pose, buffers,
                            gauntletPose(data, false, 0f), isGui || isFixed ? 1.01f : 1.12f, -1, renderLight, overlay, rightHand);
                    pose.popPose();
                }
            };
        }
        return itemRenderer;
    }

    public static final class WearerLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public WearerLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partial, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (player.isInvisible()) return;
            var view = ClientCache.getWearer(player);
            if (view == null || !view.snapshot().equipped()) return;

            long now = player.level().getGameTime();
            long est = view.estimatedServerNow(now);
            float snapAge = (est - view.snapshot().snapAnimAt()) + partial;
            float castAge = (est - view.snapshot().castAnimAt()) + partial;
            boolean snapping = snapAge >= 0f && snapAge <= 18f;
            float snapProg = snapping ? snapAge / 18f : 0f;
            float castProg = (castAge >= 0f && castAge <= 12f) ? castAge / 12f : 0f;

            boolean leftArm = player.getMainArm() == HumanoidArm.RIGHT;
            var armPart = leftArm ? getParentModel().leftArm : getParentModel().rightArm;

            pose.pushPose();
            armPart.translateAndRotate(pose);
            // PlayerModel +Y runs down the arm. Face the gems outward and point both thumbs forward (-Z).
            float armCenter = player.getSkin().model() == PlayerSkin.Model.SLIM ? 0.5f : 1f;
            pose.translate((leftArm ? armCenter : -armCenter) / 16f, 7.95f / 16f, 0f);
            InfinityRig.rotate(pose, 0f, leftArm ? 90f : -90f, 0f);
            pose.scale(0.90f, 0.65f, 1.13f);
            InfinityRig.get("infinity_gauntlet").render(pose, buffers,
                    gauntletPose(view.data(), snapping, snapProg, castProg, 0f),
                    1f, -1, light, OverlayTexture.NO_OVERLAY, !leftArm);
            pose.popPose();
        }
    }

    public static void onRenderHand(RenderHandEvent event) {
        var mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isSpectator()) return;
        if (player.getVehicle() instanceof cn.aca.infinity.SanctuaryShipEntity) {
            event.setCanceled(true);
            return;
        }
        if (player.isPassenger()) return;
        var view = ClientCache.self();
        if (view == null || !view.snapshot().equipped()) return;

        if (event.getHand() != net.minecraft.world.InteractionHand.OFF_HAND) {
            if (InfinityWheelScreen.isOpen() && event.getItemStack().isEmpty()) {
                event.setCanceled(true);
            }
            return;
        }

        long now = player.level().getGameTime();
        long est = view.estimatedServerNow(now);
        float pt = event.getPartialTick();
        int curSel = view.data().selectedOrdinal();
        if (lastSeenSelected != curSel) {
            if (lastSeenSelected >= 0) switchInspectTick = now;
            lastSeenSelected = curSel;
        }

        float snapAge = (est - view.snapshot().snapAnimAt()) + pt;
        float castAge = (est - view.snapshot().castAnimAt()) + pt;
        float switchAge = (now - switchInspectTick) + pt;
        boolean snapping = snapAge >= 0f && snapAge <= 20f;
        boolean casting = castAge >= 0f && castAge <= 12f;
        float switchPulse = (switchAge >= 0f && switchAge <= 10f) ? (1f - switchAge / 10f) : 0f;
        boolean wheel = InfinityWheelScreen.isOpen();

        if (!wheel && !snapping && !casting && !event.getItemStack().isEmpty()) {
            return;
        }
        if (wheel || snapping || casting) {
            event.setCanceled(true);
        }

        boolean left = player.getMainArm() == HumanoidArm.RIGHT;
        int sign = left ? -1 : 1;
        float time = player.tickCount + pt;

        float gaitPos = player.walkAnimation.position(pt) * 0.6662f;
        float gaitSpd = player.walkAnimation.speed(pt);
        float bobX = Mth.cos(gaitPos) * gaitSpd * 0.020f;
        float bobY = -Math.abs(Mth.sin(gaitPos)) * gaitSpd * 0.024f;
        float breathY = Mth.sin(time * 0.095f) * 0.006f;

        float lift = wheel ? InfinityWheelScreen.openProgress(pt)
                : snapping ? Mth.sin(Math.min(1f, snapAge / 20f) * (float) Math.PI)
                : casting ? Mth.sin(Math.min(1f, castAge / 12f) * (float) Math.PI)
                : switchPulse * 0.38f;
        float inspectTwist = Mth.sin(switchPulse * (float) Math.PI) * 18f;

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        if (wheel) {
            // Keep the fingertips below the projected stone fan.
            pose.translate(
                    sign * (0.49f - 0.04f * lift) + bobX * 0.20f,
                    -0.50f + 0.065f * lift + breathY,
                    -0.97f - 0.03f * lift);
            InfinityRig.rotate(pose,
                    -34f + 20f * lift,
                    sign * (12f + 3f * lift),
                    sign * (7f + 9f * lift));
        } else {
            pose.translate(
                    sign * (0.50f - 0.06f * lift) + bobX,
                    -0.45f + 0.07f * lift + bobY + breathY,
                    -0.97f - 0.035f * lift);
            InfinityRig.rotate(pose,
                    -34f + 18f * lift + inspectTwist * 0.4f,
                    sign * (12f + inspectTwist),
                    sign * (7f + inspectTwist * 0.35f));
        }
        InfinityRig.get("infinity_gauntlet").render(pose, event.getMultiBufferSource(),
                gauntletPose(view.data(), snapping, snapping ? snapAge / 20f : 0f,
                        casting ? castAge / 12f : 0f, switchPulse),
                wheel ? 0.72f : 0.76f, -1, (snapping || wheel) ? LightTexture.FULL_BRIGHT : event.getPackedLight(), OverlayTexture.NO_OVERLAY, !left);
        pose.popPose();
    }
}
