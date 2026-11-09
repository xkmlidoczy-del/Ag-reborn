/* AcademyCraft1.0.7 Magnetic Hook client-only registration. GPLv3; see NOTICE. */
package cn.academy.port.client;

import cn.academy.port.hook.ClassicHooks;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.*;

@EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClassicHookClient {
    @SubscribeEvent public static void entities(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(ClassicHooks.ENTITY.get(),ClassicHookRenderer::new);}
    @SubscribeEvent public static void models(ModelEvent.ModifyBakingResult event){var id=ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath("academy","maghook"));var original=event.getModels().get(id);if(original!=null)event.getModels().put(id,new ClassicPortableBakedModel(original));}
    @SubscribeEvent public static void resources(RegisterClientReloadListenersEvent event){event.registerReloadListener(ClassicHookModels.INSTANCE);}
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event){event.registerItem(new IClientItemExtensions(){private ClassicHookItemRenderer renderer;@Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new ClassicHookItemRenderer();return renderer;}@Override public boolean shouldBobAsEntity(net.minecraft.world.item.ItemStack stack){return true;}},ClassicHooks.MAGHOOK.get());}
    private ClassicHookClient(){}
}
