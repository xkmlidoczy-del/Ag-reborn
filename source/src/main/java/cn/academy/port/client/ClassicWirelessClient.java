package cn.academy.port.client;

import cn.academy.port.wireless.ClassicWirelessDevices;
import cn.academy.port.wireless.ClassicWirelessMenu;
import cn.academy.port.wireless.ClassicWirelessProtocol;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Dist-isolated bridge; network common classes never depend on Minecraft client types. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClassicWirelessClient {
    private static final UUID OPEN_TOKEN=new UUID(0,0);
    private ClassicWirelessClient() {}
    @SubscribeEvent public static void menus(RegisterMenuScreensEvent event){
        event.<ClassicWirelessMenu,net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<ClassicWirelessMenu>>register(ClassicWirelessDevices.MENU.get(),(menu,inventory,title)->menu.standalone()?new ClassicEnergyBridgeScreen(menu,inventory,title):new ClassicWirelessScreen(menu,inventory,title));
        ClassicWirelessProtocol.clientReceiver=snapshot->{
            var player=Minecraft.getInstance().player;
            if(player!=null&&player.containerMenu instanceof ClassicWirelessMenu menu
                    &&menu.containerId==snapshot.menuId()&&menu.token().equals(snapshot.token()))
                {menu.acceptSnapshot(snapshot.data());
                if(Minecraft.getInstance().screen instanceof ClassicWirelessScreen screen&&screen.getMenu()==menu)screen.serverSnapshot(snapshot.data());
            }
        };
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(ClassicWirelessDevices.MATRIX_TILE.get(),ClassicMatrixRenderer::new);}
    @SubscribeEvent public static void resources(RegisterClientReloadListenersEvent event){event.registerReloadListener(ClassicWirelessModels.INSTANCE);}
    public static void openWindGenerator(int menuId,BlockPos origin){PacketDistributor.sendToServer(new ClassicWirelessProtocol.Request(menuId,OPEN_TOKEN,"open_windgen",origin,"",""));}
    public static void openPhaseGenerator(int menuId,BlockPos origin){PacketDistributor.sendToServer(new ClassicWirelessProtocol.Request(menuId,OPEN_TOKEN,"open_phasegen",origin,"",""));}
    public static void openSolar(int menuId,BlockPos origin){PacketDistributor.sendToServer(new ClassicWirelessProtocol.Request(menuId,OPEN_TOKEN,"open_solar",origin,"",""));}
    public static void openReceiver(int menuId,BlockPos origin){PacketDistributor.sendToServer(new ClassicWirelessProtocol.Request(menuId,OPEN_TOKEN,"open_receiver",origin,"",""));}
    public static void openMachine(BlockPos origin,String session){
        var player=Minecraft.getInstance().player;if(player==null||origin==null||session.isEmpty())return;
        PacketDistributor.sendToServer(new ClassicWirelessProtocol.Request(player.containerMenu.containerId,OPEN_TOKEN,"open_machine",origin,session,""));
    }
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT)
    public static final class Ticks {
        private Ticks(){}
        @SubscribeEvent public static void tick(ClientTickEvent.Post event){ClassicWirelessClock.millis();}
    }
}
