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
        CosmicVfxShader.clock(partialTick);

        long now = mc.level.getGameTime();
        float time = now + partialTick;
        Vec3 currentWorld = entity.getPosition(partialTick);
        var trail = HISTORY.computeIfAbsent(entity, k -> new ArrayDeque<>());
        if (trail.isEmpty() || trail.getLast().pos.distanceToSqr(currentWorld) > .012) {
            trail.addLast(new Sample(currentWorld, now));
            while (trail.size() > (ClientState.reduced() ? 6 : 13)) trail.removeFirst();
        }
        trail.removeIf(s -> now - s.tick > 11);

        int tint = star ? switch (entity.variant()) {
            case FIRE -> 0xfff0b8ff;
            case CRYSTAL -> 0xff9cf0ff;
            case SLIME -> 0xff98ffd8;
        } : switch (entity.variant()) {
            case FIRE -> 0xffff9a3d;
            case CRYSTAL -> 0xff7ef7ee;
            case SLIME -> 0xff9af768;
        };
        int hotCore = switch (entity.variant()) {
            case FIRE -> star ? 0xfffff2ff : 0xfffff5dc;
            case CRYSTAL -> 0xfff0ffff;
            case SLIME -> 0xfff0ffe4;
        };

        Vec3 cam = this.entityRenderDispatcher.camera.getPosition();
        poseStack.pushPose();
        // Convert from entity-local space to world-offset space so history trail renders smoothly
        poseStack.translate(-currentWorld.x, -currentWorld.y, -currentWorld.z);

        var points = new ArrayList<Vec3>(trail.size() + 1);
        for (Sample s : trail) points.add(s.pos);
        points.add(currentWorld);

        double baseWidth = switch (entity.variant()) {
            case FIRE -> star ? .21 : .165;
            case CRYSTAL -> star ? .15 : .115;
            case SLIME -> star ? .175 : .135;
        };

        if (points.size() >= 2) {
            // Dual-pass high-contrast trail: abyssal void sheath + FBM-eroded incandescent core!
            VfxPrimitives.lusterPolyline(poseStack, bufferSource, points, cam, baseWidth, DialDraw.alpha(tint, .94f));
            if (!ClientState.reduced() && points.size() >= 4 && entity.variant() == Pulse.Variant.FIRE) {
                // Spiraling twin solar/nebula ember filaments around fireball wake
                var spiral = new ArrayList<Vec3>(points.size());
                for (int i = 0; i < points.size(); i++) {
                    Vec3 p = points.get(i);
                    Vec3 tang = i + 1 < points.size() ? points.get(i + 1).subtract(p) : p.subtract(points.get(i - 1));
                    tang = tang.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : tang.normalize();
                    Vec3 ref = Math.abs(tang.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
                    Vec3 u = tang.cross(ref).normalize();
                    Vec3 v = tang.cross(u).normalize();
                    double ang = time * .75 + i * .85;
                    double rad = baseWidth * .85 * ((double) i / points.size());
                    spiral.add(p.add(u.scale(Math.cos(ang) * rad)).add(v.scale(Math.sin(ang) * rad)));
                }
                VfxPrimitives.polylineRibbon(poseStack, bufferSource, spiral, cam, baseWidth * .34,
                        DialDraw.alpha(hotCore, .86f), star, true);
            }
        }

        // High-contrast 3D projectile warhead along velocity vector
        Vec3 vel = entity.getDeltaMovement();
        Vec3 dir = vel.lengthSqr() > 1e-5 ? vel.normalize() : new Vec3(0, 0, 1);
        Vec3 ref = Math.abs(dir.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = dir.cross(ref).normalize();

        switch (entity.variant()) {
            case FIRE -> {
                Vec3 back = currentWorld.subtract(dir.scale(.24));
                Vec3 tip = currentWorld.add(dir.scale(.26));
                VfxPrimitives.lusterRibbonEroded(poseStack, bufferSource, back, tip, cam,
                        .17, .14, DialDraw.alpha(tint, .98f), 1.2f, .08f, true);
                VfxPrimitives.flowRibbon(poseStack, bufferSource, back.lerp(tip, .2), tip, cam,
                        .085, .04, DialDraw.alpha(hotCore, .96f), false, true, 1.0f);
                if (!ClientState.reduced()) {
                    VfxPrimitives.bowShockWave(poseStack, bufferSource, tip, dir, right, cam,
                            star ? .28 : .21, .11, DialDraw.alpha(tint, .84f));
                }
            }
            case CRYSTAL -> {
                Vec3 back = currentWorld.subtract(dir.scale(.30));
                Vec3 tip = currentWorld.add(dir.scale(.36));
                VfxPrimitives.lusterRibbonEroded(poseStack, bufferSource, back, tip, cam,
                        .135, .018, DialDraw.alpha(tint, .98f), 1.0f, .04f, true);
                VfxPrimitives.speedStreak(poseStack, bufferSource, back, tip.add(dir.scale(.08)), cam,
                        .062, DialDraw.alpha(hotCore, .96f), .05f);
                if (!ClientState.reduced()) {
                    // Faceted diamond cross-glint at crystal tip
                    Vec3 glintSpan = right.scale(.18);
                    VfxPrimitives.speedStreak(poseStack, bufferSource,
                            currentWorld.subtract(glintSpan), currentWorld.add(glintSpan), cam,
                            .032, DialDraw.alpha(hotCore, .82f), .15f);
                }
            }
            case SLIME -> {
                double pulse = 1.0 + .18 * Mth.sin(time * .85f + entity.getId());
                Vec3 back = currentWorld.subtract(dir.scale(.22));
                Vec3 tip = currentWorld.add(dir.scale(.22));
                VfxPrimitives.lusterRibbonEroded(poseStack, bufferSource, back, tip, cam,
                        .15 * pulse, .12 * pulse, DialDraw.alpha(tint, .96f), 1.1f, .12f, true);
                VfxPrimitives.flowRibbon(poseStack, bufferSource, back.lerp(tip, .25), tip, cam,
                        .072 * pulse, .05 * pulse, DialDraw.alpha(hotCore, .92f), false, true, 1.0f);
            }
        }

        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(Pulse entity) {
        return TEXTURE;
    }
}
