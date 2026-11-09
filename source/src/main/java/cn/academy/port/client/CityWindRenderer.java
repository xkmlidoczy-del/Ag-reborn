package cn.academy.port.client;

import cn.academy.port.city.CityWindContent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Reuses the established wind generator mesh; only its rotor moves. */
public final class CityWindRenderer implements BlockEntityRenderer<CityWindContent.HeadEntity> {
    public CityWindRenderer(BlockEntityRendererProvider.Context context) {}

    public static float angle(long gameTime, float partialTick, BlockPos pos) {
        long phase = Math.floorMod(pos.getX() * 31L + pos.getZ() * 17L, 240L);
        return ((gameTime + phase) % 240L + partialTick) * 1.5f;
    }

    @Override
    public void render(CityWindContent.HeadEntity head, float partial, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        var main = ClassicWindModels.INSTANCE.mesh("main");
        var fan = ClassicWindModels.INSTANCE.mesh("fan");
        if (main == null || fan == null || head.getLevel() == null) return;
        poses.pushPose();
        try {
            poses.translate(0.5, 0, 0.6);
            ClassicDeveloperRenderer.emitMesh(main, poses.last(),
                    buffers.getBuffer(RenderType.entityCutout(ClassicWindModels.texture("main"))), light, overlay);
            poses.translate(0, 0.5, 0.82);
            poses.mulPose(Axis.ZP.rotationDegrees(-angle(head.getLevel().getGameTime(), partial, head.getBlockPos())));
            ClassicDeveloperRenderer.emitMesh(fan, poses.last(),
                    buffers.getBuffer(RenderType.entityCutout(ClassicWindModels.texture("fan"))), light, overlay);
        } finally {
            poses.popPose();
        }
    }

    @Override public boolean shouldRenderOffScreen(CityWindContent.HeadEntity head) { return true; }
    @Override public int getViewDistance() { return 160; }
    @Override public AABB getRenderBoundingBox(CityWindContent.HeadEntity head) {
        return new AABB(head.getBlockPos()).inflate(8);
    }
}
