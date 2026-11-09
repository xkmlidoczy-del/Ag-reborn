/* RenderDeveloperNormal/Advanced and LambdaLib multiblock semantics, GPLv3 adaptation. */
package cn.academy.port.client;

import cn.academy.port.develop.DeveloperType;
import cn.academy.port.machine.MachineDeveloperBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Real source OBJ rendering at the sole multiblock origin, with complete model frustum bounds. */
public final class ClassicDeveloperRenderer implements BlockEntityRenderer<MachineDeveloperBlockEntity> {
    public ClassicDeveloperRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(MachineDeveloperBlockEntity blockEntity, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!blockEntity.isOrigin()) return;
        DeveloperType type = blockEntity.developerType();
        if (type == DeveloperType.PORTABLE) return;
        ClassicDeveloperObj.Mesh mesh = ClassicDeveloperModels.INSTANCE.mesh(type);
        if (mesh == null) return;
        var facing = facing(blockEntity.facing());
        poses.pushPose();
        try {
            // BlockMulti pivot-offset + cardinal rotation-center are always (0.5, 0, 0.5).
            poses.translate(ClassicDeveloperTransform.PIVOT_X, ClassicDeveloperTransform.PIVOT_Y, ClassicDeveloperTransform.PIVOT_Z);
            poses.mulPose(Axis.YP.rotationDegrees(facing.blockRotation()));
            poses.mulPose(Axis.YP.rotationDegrees(ClassicDeveloperTransform.MODEL_Y_ROTATION));
            poses.scale(ClassicDeveloperTransform.SCALE, ClassicDeveloperTransform.SCALE, ClassicDeveloperTransform.SCALE);
            var texture = ClassicDeveloperModels.texture(type);
            // Normal explicitly disables culling. Advanced also uses source-alpha blending.
            RenderType renderType = type == DeveloperType.ADVANCED
                    ? RenderType.entityTranslucent(texture)
                    : RenderType.entityCutoutNoCull(texture);
            VertexConsumer vertices = buffers.getBuffer(renderType);
            emitMesh(mesh, poses.last(), vertices, packedLight, packedOverlay);
        } finally {
            poses.popPose();
        }
    }

    static void emitMesh(ClassicDeveloperObj.Mesh mesh, PoseStack.Pose pose, VertexConsumer vertices,
                         int packedLight, int packedOverlay) {
        for (var triangle : mesh.triangles()) {
            emit(triangle.a(), pose, vertices, packedLight, packedOverlay);
            emit(triangle.b(), pose, vertices, packedLight, packedOverlay);
            emit(triangle.c(), pose, vertices, packedLight, packedOverlay);
            // Vanilla entity buffers are quads. A repeated third vertex gives one real triangle,
            // plus one zero-area triangle, preserving topology and translucent sorting support.
            emit(triangle.c(), pose, vertices, packedLight, packedOverlay);
        }
    }

    private static void emit(ClassicDeveloperObj.Vertex vertex, PoseStack.Pose pose, VertexConsumer vertices,
                             int light, int overlay) {
        vertices.addVertex(pose, vertex.x(), vertex.y(), vertex.z()).setColor(255, 255, 255, 255)
                .setUv(vertex.u(), vertex.v()).setOverlay(overlay).setLight(light)
                .setNormal(pose, vertex.nx(), vertex.ny(), vertex.nz());
    }

    @Override public AABB getRenderBoundingBox(MachineDeveloperBlockEntity blockEntity) {
        var position = blockEntity.getBlockPos();
        if (!blockEntity.isOrigin()) return new AABB(position);
        ClassicDeveloperObj.Mesh mesh = ClassicDeveloperModels.INSTANCE.mesh(blockEntity.developerType());
        if (mesh == null) return new AABB(position);
        var bounds = ClassicDeveloperTransform.worldBounds(mesh.bounds(), facing(blockEntity.facing()));
        return new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ())
                .move(position).inflate(.01);
    }

    private static ClassicDeveloperTransform.Facing facing(Direction direction) {
        return switch (direction) {
            case SOUTH -> ClassicDeveloperTransform.Facing.SOUTH;
            case WEST -> ClassicDeveloperTransform.Facing.WEST;
            case EAST -> ClassicDeveloperTransform.Facing.EAST;
            default -> ClassicDeveloperTransform.Facing.NORTH;
        };
    }
}
