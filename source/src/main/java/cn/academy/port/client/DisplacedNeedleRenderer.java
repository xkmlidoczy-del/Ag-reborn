package cn.academy.port.client;

import cn.academy.port.AcademyCraft;
import cn.academy.port.highpower.DisplacedNeedleEntity;
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

/** Only the actual tracked NEW311 medium is drawn, using the original academy:needle item model. */
public final class DisplacedNeedleRenderer extends EntityRenderer<DisplacedNeedleEntity> {
    private final net.minecraft.client.renderer.entity.ItemRenderer items;
    public DisplacedNeedleRenderer(EntityRendererProvider.Context context) {
        super(context); items = context.getItemRenderer(); shadowRadius = 0;
    }
    @Override public void render(DisplacedNeedleEntity needle, float yaw, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int light) {
        if (!needle.isAdmitted()) return;
        poses.pushPose();
        try {
            // EntityRenderDispatcher already translated to native/interpolated entity coordinates.
            // Rotate the thin original item along native arrow yaw/pitch; no artwork changes its position.
            poses.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, needle.yRotO, needle.getYRot()) - 90));
            poses.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, needle.xRotO, needle.getXRot()) - 45));
            poses.scale(.55F, .55F, .55F);
            items.renderStatic(AcademyCraft.NEEDLE.get().getDefaultInstance(), ItemDisplayContext.NONE,
                    light, OverlayTexture.NO_OVERLAY, poses, buffers, needle.level(), needle.getId());
        } finally { poses.popPose(); }
        super.render(needle, yaw, partialTick, poses, buffers, light);
    }
    @Override public ResourceLocation getTextureLocation(DisplacedNeedleEntity needle) { return TextureAtlas.LOCATION_BLOCKS; }
}
