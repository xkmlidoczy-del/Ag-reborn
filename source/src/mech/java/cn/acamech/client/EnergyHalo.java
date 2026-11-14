package cn.acamech.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** A soft, depth-tested light around the lens of a selected model cuboid. */
final class EnergyHalo {
    private static final float PIXEL = 1.0f / 16.0f;
    private final List<ModelPart> jointChain;
    private final float x, y, z, width, height, pitch, yaw, roll, surface;
    private final boolean rear, upright;
    private final Style style;

    EnergyHalo(List<ModelPart> jointChain, float[] localCenter, float[] size, float[] angles, Style style) {
        this.jointChain = jointChain;
        this.style = style;
        this.rear = style == Style.WING_BACK;
        this.upright = size[1] > size[0] * 1.25f;
        x = localCenter[0] * PIXEL;
        y = localCenter[1] * PIXEL;
        z = localCenter[2] * PIXEL;
        width = Math.max(size[0] * .5f + style.border, style.minRadius) * PIXEL;
        height = Math.max(size[1] * .5f + style.border, style.minRadius) * PIXEL;
        pitch = angles[0];
        yaw = angles[1];
        roll = angles[2];
        surface = (size[2] * .5f + .10f) * PIXEL * (rear ? 1 : -1);
    }

    void render(PoseStack stack, VertexConsumer vertices, int tint, float strength, float time) {
        stack.pushPose();
        for (ModelPart joint : jointChain) joint.translateAndRotate(stack);
        stack.translate(x, y, z);
        if (pitch != 0 || yaw != 0 || roll != 0) {
            stack.mulPose(new org.joml.Quaternionf().rotationZYX(roll, yaw, pitch));
        }
        stack.translate(0, 0, surface);
        float pulse = .93f + .07f * Mth.sin(time * .14f);
        if (style == Style.CORE || style == Style.SMALL_CORE) {
            pulse = .92f + .08f * Mth.sin(time * .22f);
        }
        int opacity = Mth.clamp(Math.round(255 * style.strength * strength * pulse), 0, 255);
        int color = tint | (opacity << 24);
        PoseStack.Pose pose = stack.last();
        if (rear) {
            vertex(pose, vertices, -width, -height, 0, 0, color);
            vertex(pose, vertices, width, -height, 1, 0, color);
            vertex(pose, vertices, width, height, 1, 1, color);
            vertex(pose, vertices, -width, height, 0, 1, color);
        } else {
            vertex(pose, vertices, -width, -height, 0, 0, color);
            vertex(pose, vertices, -width, height, 0, 1, color);
            vertex(pose, vertices, width, height, 1, 1, color);
            vertex(pose, vertices, width, -height, 1, 0, color);
        }
        stack.popPose();
    }

    private void vertex(PoseStack.Pose pose, VertexConsumer vertices, float px, float py, float u, float v, int color) {
        vertices.addVertex(pose, px, py, 0).setUv(upright ? v : u, upright ? u : v).setColor(color);
    }

    enum Style {
        CORE(11f, 11f, .58f),
        SMALL_CORE(6f, 6.5f, .47f),
        SOFT(3.65f, 3.7f, .34f),
        WING(3.6f, 3.8f, .31f),
        WING_BACK(3.6f, 3.8f, .31f);

        private final float border, minRadius, strength;

        Style(float border, float minRadius, float strength) {
            this.border = border;
            this.minRadius = minRadius;
            this.strength = strength;
        }
    }
}
