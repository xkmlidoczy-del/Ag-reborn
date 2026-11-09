package cn.academy.port.client;

import cn.academy.port.AcademyCraft;
import cn.academy.port.highpower.BorrowedNeedleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/** Original needle model for an admitted physical lease; this does not own an inventory item. */
public final class BorrowedNeedleRenderer extends EntityRenderer<BorrowedNeedleEntity> {
    private final net.minecraft.client.renderer.entity.ItemRenderer items;
    public BorrowedNeedleRenderer(EntityRendererProvider.Context context) {
        super(context); items = context.getItemRenderer(); shadowRadius = 0;
    }
    @Override public void render(BorrowedNeedleEntity needle, float yaw, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int light) {
        if (!needle.isAdmitted()) return;
        poses.pushPose();
        try {
            // Native dispatcher already uses camera-relative/interpolated coordinates.
            poses.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, needle.yRotO, needle.getYRot()) - 90));
            poses.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, needle.xRotO, needle.getXRot()) - 45));
            poses.scale(.55F, .55F, .55F);
            items.renderStatic(AcademyCraft.NEEDLE.get().getDefaultInstance(), ItemDisplayContext.NONE,
                    light, OverlayTexture.NO_OVERLAY, poses, buffers, needle.level(), needle.getId());
        } finally { poses.popPose(); }
        super.render(needle, yaw, partialTick, poses, buffers, light);
    }
    @Override public ResourceLocation getTextureLocation(BorrowedNeedleEntity needle) { return TextureAtlas.LOCATION_BLOCKS; }
}
