package cn.academy.port.client;

import cn.academy.port.canon.CanonLiquidEntities;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Dark humanoid silhouette; in-game presentation still requires client visual review. */
@EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class CanonLiquidClient {
    private CanonLiquidClient() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CanonLiquidEntities.SHADOW.get(),ShadowRenderer::new);
    }
    private static final class ShadowRenderer extends ArmorStandRenderer {
        private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/black_concrete.png");
        ShadowRenderer(EntityRendererProvider.Context context){super(context);}
        @Override public ResourceLocation getTextureLocation(ArmorStand ignored){return TEXTURE;}
        @Override public void render(ArmorStand body,float yaw,float partial,com.mojang.blaze3d.vertex.PoseStack pose,
                net.minecraft.client.renderer.MultiBufferSource buffers,int light){
            super.render(body,yaw,partial,pose,buffers,light);
            if(!(body instanceof CanonLiquidEntities.Shadow shadow)||!shadow.hasObservationModule())return;
            pose.pushPose();
            try{
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180-yaw));pose.translate(0,1.65,-.26);pose.scale(.3f,.3f,.3f);
                net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(CanonLiquidEntities.OBSERVATION_MODULE.toStack(),
                        net.minecraft.world.item.ItemDisplayContext.FIXED,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                        pose,buffers,body.level(),body.getId());
            }finally{pose.popPose();}
        }
    }
}
