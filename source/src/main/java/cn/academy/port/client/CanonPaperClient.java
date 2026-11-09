package cn.academy.port.client;

import cn.academy.port.canon.CanonPaperContent;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class CanonPaperClient {
    private CanonPaperClient() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(CanonPaperContent.PLANE.get(),PlaneRenderer::new);
    }
    private static final class PlaneRenderer extends ThrownItemRenderer<CanonPaperContent.PaperPlane> {
        PlaneRenderer(EntityRendererProvider.Context context){super(context);}
    }
}
