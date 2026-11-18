package cn.aca.omnitrix.client;

import cn.aca.omnitrix.Omnitrix;
import cn.aca.omnitrix.Pulse;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class PulseRenderer extends EntityRenderer<Pulse> {
    private static final ResourceLocation TEXTURE = Omnitrix.id("textures/rig/omnitrix.png");
    private record Sample(Vec3 pos, long tick) {}
    private static final Map<Pulse, ArrayDeque<Sample>> HISTORY = new WeakHashMap<>();

    PulseRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(Pulse entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean star = entity.celestial();
        if (star) CosmicVfxShader.clock(partialTick);

        long now = mc.level.getGameTime();
        Vec3 currentWorld = entity.getPosition(partialTick);
        var trail = HISTORY.computeIfAbsent(entity, k -> new ArrayDeque<>());
        if (trail.isEmpty() || trail.getLast().pos.distanceToSqr(currentWorld) > .015) {
            trail.addLast(new Sample(currentWorld, now));
            while (trail.size() > (ClientState.reduced() ? 5 : 11)) trail.removeFirst();
        }
        trail.removeIf(s -> now - s.tick > 10);

        int tint = star ? switch (entity.variant()) {
            case FIRE -> 0xffe8b4ff;
            case CRYSTAL -> 0xff9ee8ff;
            case SLIME -> 0xff95ffd4;
        } : switch (entity.variant()) {
            case FIRE -> 0xffff9e44;
            case CRYSTAL -> 0xff8ef2eb;
            case SLIME -> 0xff9cf56e;
        };

        Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
        poseStack.pushPose();
        // Convert from entity-local space to world-offset space so history trail renders smoothly
        poseStack.translate(-currentWorld.x, -currentWorld.y, -currentWorld.z);

        var points = new ArrayList<Vec3>(trail.size() + 1);
        for (Sample s : trail) points.add(s.pos);
        points.add(currentWorld);
        if (points.size() >= 2) {
            double width = switch (entity.variant()) {
                case FIRE -> star ? .19 : .14;
                case CRYSTAL -> star ? .14 : .10;
                case SLIME -> star ? .16 : .12;
            };
            VfxPrimitives.polylineRibbon(poseStack, bufferSource, points, cam, width,
                    DialDraw.alpha(tint, .90f), star, !star);
            if (star) {
                VfxPrimitives.polylineRibbon(poseStack, bufferSource, points, cam, width * .42,
                        0xe6ffffff, false, true);
            }
        }

        // Glowing 3D head core along velocity vector
        Vec3 vel = entity.getDeltaMovement();
        Vec3 dir = vel.lengthSqr() > 1e-5 ? vel.normalize() : new Vec3(0, 0, 1);
        double headLen = entity.variant() == Pulse.Variant.CRYSTAL ? .42 : .28;
        Vec3 back = currentWorld.subtract(dir.scale(headLen * .55));
        Vec3 tip = currentWorld.add(dir.scale(headLen * .65));
        VfxPrimitives.flowRibbon(poseStack, bufferSource, back, tip, cam,
                .13, entity.variant() == Pulse.Variant.CRYSTAL ? .02 : .11,
                DialDraw.alpha(tint, .96f), star, false, 1.0f);
        VfxPrimitives.flowRibbon(poseStack, bufferSource, back, tip, cam,
                .065, .025, 0xf2ffffff, false, true, 1.0f);

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(Pulse entity) {
        return TEXTURE;
    }
}
