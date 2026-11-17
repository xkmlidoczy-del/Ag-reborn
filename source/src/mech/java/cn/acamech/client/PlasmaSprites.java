package cn.acamech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Draws the ImageGen masks in world space. Age and tint come from the existing combat effects. */
final class PlasmaSprites {
    private static final float INSET = .5f / 512f;
    private static final float V_EDGE = .5f / 256f;

    private PlasmaSprites() {}

    static void ring(Pen pen, Vec3 center, RangedEffects.Frame axes, double radius, float spin, int tint, float glow) {
        pen.stamp(0, center, axes, radius * 1.36, radius * 1.36, spin, tint, glow);
    }

    static void impact(Pen pen, Vec3 center, Vec3 towardsEye, double size, double stretch,
                       float twist, int tint, float age, float duration, float strength) {
        float t = Mth.clamp(age / duration, 0, 1);
        if (t >= 1 || age < 0) return;
        float envelope = (1 - t) * (1 - t);
        double growth = .79 + .84 * AttackPose.ease(t);
        var axes = RangedEffects.Frame.perpendicular(towardsEye.normalize());
        pen.stamp(1, center, axes, size * growth * stretch, size * growth,
                twist + age * .067f, tint, envelope * strength);
    }

    record Pen(PoseStack.Pose pose, VertexConsumer out) {
        private void stamp(int cell, Vec3 center, RangedEffects.Frame axes, double x, double y,
                           float spin, int tint, float glow) {
            if (glow <= .003f) return;
            double sine = Math.sin(spin), cosine = Math.cos(spin);
            Vec3 right = axes.x().scale(cosine * x).add(axes.y().scale(sine * x));
            Vec3 up = axes.y().scale(cosine * y).subtract(axes.x().scale(sine * y));
            float u0 = cell * .5f + INSET, u1 = (cell + 1) * .5f - INSET;
            int argb = Mth.clamp(Math.round(glow * 255), 0, 255) << 24 | tint & 0xffffff;
            vertex(center.subtract(right).add(up), u0, V_EDGE, argb);
            vertex(center.subtract(right).subtract(up), u0, 1 - V_EDGE, argb);
            vertex(center.add(right).subtract(up), u1, 1 - V_EDGE, argb);
            vertex(center.add(right).add(up), u1, V_EDGE, argb);
        }

        private void vertex(Vec3 point, float u, float v, int argb) {
            out.addVertex(pose, (float) point.x, (float) point.y, (float) point.z).setUv(u, v).setColor(argb);
        }
    }
}
