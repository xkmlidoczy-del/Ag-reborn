package cn.acamech.client;

import cn.acamech.MechMod;
import cn.acamech.PulseBolt;
import cn.acamech.RangedWeapon;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

final class PulseRenderer extends EntityRenderer<PulseBolt> {
    PulseRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(PulseBolt bolt, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
        Vec3 axis = bolt.getDeltaMovement().normalize();
        if (axis.lengthSqr() < .01) return;
        float age = bolt.tickCount + partial;
        var axes = RangedEffects.Frame.perpendicular(axis);
        Vec3 front = axis.scale(.62);
        Vec3 stern = axis.scale(-Math.min(3.1, .5 + age * .19));
        int color = RangedWeapon.PULSE.color;
        var pen = new AttackEffects.Pen(stack.last(), buffers.getBuffer(MechRenderTypes.STRIKE_LIGHT));
        pen.beam(stern, front, axes.x(), .45, color, .66f);
        pen.beam(stern, front, axes.y(), .45, color, .66f);
        pen.beam(stern.scale(.51), front, axes.x(), .18, 0xeeffff, .98f);
        pen.beam(stern.scale(.51), front, axes.y(), .18, 0xeeffff, .98f);
        pen.ring(axis.scale(.26), axes.x(), axes.y(), .62, .12, 6, age * .25, color, .94f);
        pen.ring(axis.scale(-.58), axes.x(), axes.y(), .39, .071, 6, -age * .32, color, .77f);
        pen.ring(axis.scale(-1.21), axes.x(), axes.y(), .25, .045, 6, age * .29, color, .59f);
    }

    @Override public ResourceLocation getTextureLocation(PulseBolt bolt) {
        return MechMod.id("textures/entity/atlas_glow.png");
    }
}
