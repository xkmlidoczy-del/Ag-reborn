/* AcademyCraft 1.0.7 RenderPhaseGen. GPLv3; see NOTICE. */
package cn.academy.port.client;

import cn.academy.port.phasegen.ClassicPhaseGeneratorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** The original unscaled, unrotated mesh and exactly five fluid-driven texture states. */
public final class ClassicPhaseGeneratorRenderer implements BlockEntityRenderer<ClassicPhaseGeneratorBlockEntity> {
    public ClassicPhaseGeneratorRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(ClassicPhaseGeneratorBlockEntity generator, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int light, int overlay) {
        var mesh = ClassicPhaseGeneratorModel.INSTANCE.mesh();
        if (mesh == null) return;
        poses.pushPose();
        try {
            poses.translate(ClassicPhaseGeneratorVisualRules.PIVOT_X,
                    ClassicPhaseGeneratorVisualRules.PIVOT_Y, ClassicPhaseGeneratorVisualRules.PIVOT_Z);
            var texture = ClassicPhaseGeneratorModel.texture(generator.liquid(), generator.getTankSize());
            // RenderPhaseGen leaves normal OpenGL face culling intact; no animation or facing is added.
            ClassicDeveloperRenderer.emitMesh(mesh, poses.last(),
                    buffers.getBuffer(RenderType.entityCutout(texture)), light, overlay);
        } finally {
            poses.popPose();
        }
    }
    @Override public AABB getRenderBoundingBox(ClassicPhaseGeneratorBlockEntity generator) {
        var mesh = ClassicPhaseGeneratorModel.INSTANCE.mesh();
        if (mesh == null) return new AABB(generator.getBlockPos());
        var b = ClassicPhaseGeneratorVisualRules.worldBounds(mesh.bounds());
        return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())
                .move(generator.getBlockPos()).inflate(.01);
    }
}
