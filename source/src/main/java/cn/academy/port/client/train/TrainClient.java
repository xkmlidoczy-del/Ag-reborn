package cn.academy.port.client.train;

import cn.academy.port.train.TrainContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TrainClient {
    private TrainClient() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TrainContent.CAR.get(), TrainCarRenderer::new);
    }
}
