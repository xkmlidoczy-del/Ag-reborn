/* AcademyCraft1.0.7 GuiAbilityInterferer client adapter. GPLv3; see NOTICE. */
package cn.academy.port.client;
import cn.academy.port.interferer.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
@EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClassicInterfererClient {
    private ClassicInterfererClient(){}
    @SubscribeEvent public static void menus(RegisterMenuScreensEvent event){event.register(ClassicAbilityInterferers.MENU.get(),ClassicInterfererScreen::new);}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){ClassicInterfererNetwork.clientReceiver=snapshot->{var mc=Minecraft.getInstance();if(mc.player!=null&&mc.player.containerMenu instanceof ClassicAbilityInterfererMenu menu&&menu.containerId==snapshot.menuId()&&menu.token().equals(snapshot.token())){menu.acceptSnapshot(snapshot);if(mc.screen instanceof ClassicInterfererScreen screen)screen.accept(snapshot);}};ClassicInterfererNetwork.clientTileReceiver=snapshot->{var mc=Minecraft.getInstance();if(mc.level!=null&&mc.level.hasChunkAt(snapshot.pos())&&mc.level.getBlockEntity(snapshot.pos()) instanceof ClassicAbilityInterfererBlockEntity tile){tile.acceptPresentation(snapshot.range(),snapshot.enabled(),snapshot.names());mc.level.setBlock(snapshot.pos(),tile.getBlockState().setValue(ClassicAbilityInterfererBlock.ENABLED,snapshot.enabled()),net.minecraft.world.level.block.Block.UPDATE_CLIENTS);}};}
}
