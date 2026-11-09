package cn.academy.port.client;
import cn.academy.port.wind.ClassicWindGenerators;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
@EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClassicWindClient {
    private ClassicWindClient(){}
    @SubscribeEvent public static void menus(RegisterMenuScreensEvent event){event.register(ClassicWindGenerators.MENU.get(),ClassicWindScreen::new);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(ClassicWindGenerators.BASE_TILE.get(),ClassicWindRenderer::new);event.registerBlockEntityRenderer(ClassicWindGenerators.MAIN_TILE.get(),ClassicWindRenderer::new);event.registerBlockEntityRenderer(ClassicWindGenerators.PILLAR_TILE.get(),ClassicWindRenderer::new);}
    @SubscribeEvent public static void resources(RegisterClientReloadListenersEvent event){event.registerReloadListener(ClassicWindModels.INSTANCE);}
}
