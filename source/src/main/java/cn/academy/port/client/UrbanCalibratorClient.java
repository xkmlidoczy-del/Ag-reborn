package cn.academy.port.client;

import cn.academy.port.story.UrbanCalibrator;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.IronGolem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only renderer; the server boss class never links client rendering types. */
@EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class UrbanCalibratorClient {
    private UrbanCalibratorClient() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(UrbanCalibrator.BOSS.get(),Renderer::new);
    }
    private static final class Renderer extends IronGolemRenderer {
        private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("academy","textures/entity/urban_calibrator.png");
        Renderer(EntityRendererProvider.Context context){super(context);}
        @Override public ResourceLocation getTextureLocation(IronGolem entity){return TEXTURE;}
    }
}
