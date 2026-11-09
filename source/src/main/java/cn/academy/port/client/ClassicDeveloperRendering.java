package cn.academy.port.client;

import cn.academy.port.machine.MachineDevelopers;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Called from AcademyClient.Registration's existing Dist.CLIENT mod-bus subscriber. */
public final class ClassicDeveloperRendering {
    private ClassicDeveloperRendering() {}
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MachineDevelopers.BLOCK_ENTITY.get(), ClassicDeveloperRenderer::new);
    }
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(ClassicDeveloperModels.INSTANCE);
    }
}
