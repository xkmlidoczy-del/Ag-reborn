package cn.academy.port.client;

import cn.academy.port.solar.ClassicSolarGenerators;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Client-only bus bridge: common solar registration never names a client class. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClassicSolarClient {
    private ClassicSolarClient() {}
    @SubscribeEvent public static void menus(RegisterMenuScreensEvent event) { event.register(ClassicSolarGenerators.MENU.get(), ClassicSolarScreen::new); }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) { event.registerBlockEntityRenderer(ClassicSolarGenerators.TILE.get(), ClassicSolarRenderer::new); }
    @SubscribeEvent public static void resources(RegisterClientReloadListenersEvent event) { event.registerReloadListener(ClassicSolarModel.INSTANCE); }
}
