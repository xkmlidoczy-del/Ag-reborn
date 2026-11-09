package cn.academy.port.client;

import cn.academy.port.AcademyCraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Additive client-only registrations; no existing solar/developer registration method is replaced. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClassicPortableRendering {
    @SubscribeEvent public static void models(ModelEvent.ModifyBakingResult event){
        var id=ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath("academy","portable_developer"));
        var original=event.getModels().get(id);if(original!=null)event.getModels().put(id,new ClassicPortableBakedModel(original));
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){event.registerReloadListener(ClassicPortableModels.INSTANCE);}
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event){
        event.registerItem(new IClientItemExtensions(){
            private ClassicPortableRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new ClassicPortableRenderer();return renderer;}
            @Override public boolean shouldBobAsEntity(net.minecraft.world.item.ItemStack stack){return true;}
        },AcademyCraft.DEVELOPER.get());
    }
    private ClassicPortableRendering(){}
}
