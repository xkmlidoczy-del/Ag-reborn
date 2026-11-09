package cn.academy.port.client;
import cn.academy.port.cat.ClassicCatEngines;
import cn.academy.port.display.ClassicDisplayItems;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.*;
@EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClassicMiscClient {
    private ClassicMiscClient(){}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(ClassicCatEngines.TILE.get(),ClassicCatRenderer::new);}
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event){event.registerItem(new IClientItemExtensions(){private ClassicAchievementIconRenderer renderer;@Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new ClassicAchievementIconRenderer();return renderer;}},ClassicDisplayItems.ACHIEVEMENT_ICON.get());}
}
