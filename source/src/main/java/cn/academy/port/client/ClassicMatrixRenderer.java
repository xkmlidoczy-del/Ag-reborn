/* AcademyCraft1.0.7 RenderMatrix / LambdaLib BlockMulti transform, GPLv3. See NOTICE. */
package cn.academy.port.client;

import cn.academy.port.wireless.ClassicWirelessMatrixBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** Source Main+Core and exactly three animated Shield instances; duplicate OBJ groups never emitted. */
public final class ClassicMatrixRenderer implements BlockEntityRenderer<ClassicWirelessMatrixBlockEntity> {
    public ClassicMatrixRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(ClassicWirelessMatrixBlockEntity matrix, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int light, int overlay) {
        var model = ClassicWirelessModels.INSTANCE.matrix();
        if (!matrix.isOrigin() || model == null) return;
        var facing = ClassicWirelessVisualRules.OriginalFacing.valueOf(matrix.facing().name());
        poses.pushPose();
        try {
            poses.translate(facing.pivotX(), 0, facing.pivotZ());
            poses.mulPose(Axis.YP.rotationDegrees(facing.rotation()));
            var vertices = buffers.getBuffer(RenderType.entityCutout(ClassicWirelessModels.MATRIX_TEXTURE));
            ClassicDeveloperRenderer.emitMesh(model.groups().get("Main"), poses.last(), vertices, light, overlay);
            ClassicDeveloperRenderer.emitMesh(model.groups().get("Core"), poses.last(), vertices, light, overlay);
            long millis = ClassicWirelessClock.millis();
            for (int i = 0; i < ClassicWirelessVisualRules.shieldCount(matrix.plateCount()); i++) {
                var shield = ClassicWirelessVisualRules.shield(millis, i);
                poses.pushPose();
                try {
                    poses.translate(0, shield.height(), 0);
                    poses.mulPose(Axis.YP.rotationDegrees((float)shield.rotation()));
                    ClassicDeveloperRenderer.emitMesh(model.groups().get("Shield"), poses.last(), vertices, light, overlay);
                } finally { poses.popPose(); }
            }
        } finally { poses.popPose(); }
    }
    @Override public AABB getRenderBoundingBox(ClassicWirelessMatrixBlockEntity matrix) {
        var model = ClassicWirelessModels.INSTANCE.matrix();
        if (!matrix.isOrigin() || model == null) return new AABB(matrix.getBlockPos());
        double radius = 0, minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (String group : List.of("Main", "Core", "Shield")) {
            var mesh = model.groups().get(group);
            for (var triangle : mesh.triangles()) for (var vertex : List.of(triangle.a(),triangle.b(),triangle.c())) {
                radius = Math.max(radius, Math.hypot(vertex.x(),vertex.z()));
                minY = Math.min(minY, vertex.y() - (group.equals("Shield") ? .1 : 0));
                maxY = Math.max(maxY, vertex.y() + (group.equals("Shield") ? .1 : 0));
            }
        }
        var facing = ClassicWirelessVisualRules.OriginalFacing.valueOf(matrix.facing().name());
        return new AABB(facing.pivotX()-radius,minY,facing.pivotZ()-radius,
                facing.pivotX()+radius,maxY,facing.pivotZ()+radius).move(matrix.getBlockPos()).inflate(.01);
    }
}
