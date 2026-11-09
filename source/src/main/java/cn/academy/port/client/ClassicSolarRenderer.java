/* Original AcademyCraft RenderSolarGen, GPLv3. See NOTICE. */
package cn.academy.port.client;

import cn.academy.port.solar.ClassicSolarBlock;
import cn.academy.port.solar.ClassicSolarBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

public final class ClassicSolarRenderer implements BlockEntityRenderer<ClassicSolarBlockEntity> {
    public ClassicSolarRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(ClassicSolarBlockEntity solar, float partialTick, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        var mesh = ClassicSolarModel.INSTANCE.mesh(); if (mesh == null) return;
        var facing = ClassicDeveloperTransform.Facing.valueOf(solar.getBlockState().getValue(ClassicSolarBlock.FACING).name());
        poses.pushPose();
        try {
            poses.translate(.5, 0, .5);
            poses.mulPose(Axis.YP.rotationDegrees(facing.blockRotation()));
            poses.mulPose(Axis.YP.rotationDegrees(ClassicSolarModelTransform.MODEL_Y_ROTATION));
            poses.scale(ClassicSolarModelTransform.SCALE, ClassicSolarModelTransform.SCALE, ClassicSolarModelTransform.SCALE);
            ClassicDeveloperRenderer.emitMesh(mesh, poses.last(), buffers.getBuffer(RenderType.entityCutoutNoCull(ClassicSolarModel.TEXTURE)), light, overlay);
        } finally { poses.popPose(); }
    }
    @Override public AABB getRenderBoundingBox(ClassicSolarBlockEntity solar) {
        var mesh = ClassicSolarModel.INSTANCE.mesh(); if (mesh == null) return new AABB(solar.getBlockPos());
        var bounds = ClassicSolarModelTransform.worldBounds(mesh.bounds(), ClassicDeveloperTransform.Facing.valueOf(solar.getBlockState().getValue(ClassicSolarBlock.FACING).name()));
        return new AABB(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ()).move(solar.getBlockPos()).inflate(.01);
    }
}
