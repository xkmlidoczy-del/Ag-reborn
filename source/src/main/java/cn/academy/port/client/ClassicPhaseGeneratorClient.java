package cn.academy.port.client;

import cn.academy.port.phasegen.ClassicPhaseGenerators;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Dist-isolated registration: the common phase generator never loads Minecraft client classes. */
@EventBusSubscriber(modid="academy", value=Dist.CLIENT, bus=EventBusSubscriber.Bus.MOD)
public final class ClassicPhaseGeneratorClient {
    private ClassicPhaseGeneratorClient() {}
    @SubscribeEvent public static void menus(RegisterMenuScreensEvent event) {
        event.register(ClassicPhaseGenerators.MENU.get(), ClassicPhaseGeneratorScreen::new);
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ClassicPhaseGenerators.TILE.get(), ClassicPhaseGeneratorRenderer::new);
    }
    @SubscribeEvent public static void resources(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(ClassicPhaseGeneratorModel.INSTANCE);
    }
}
