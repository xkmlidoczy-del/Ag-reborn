package cn.academy.port.client;
import cn.academy.port.terminal.TerminalModule;
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
/** Inventory generated icon is retained; physical contexts use the original custom mesh. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class TerminalInstallerRendering {
    @SubscribeEvent public static void models(ModelEvent.ModifyBakingResult event){var id=ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath("academy","terminal_installer"));var original=event.getModels().get(id);if(original!=null)event.getModels().put(id,new TerminalInstallerBakedModel(original));}
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){event.registerReloadListener(TerminalInstallerModels.INSTANCE);}
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event){event.registerItem(new IClientItemExtensions(){private TerminalInstallerRenderer renderer;@Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new TerminalInstallerRenderer();return renderer;}@Override public boolean shouldBobAsEntity(net.minecraft.world.item.ItemStack stack){return true;}},TerminalModule.INSTALLER.get());}
    private TerminalInstallerRendering(){}
}
