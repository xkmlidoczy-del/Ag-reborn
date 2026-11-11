package cn.academy.port.client.city;

import cn.academy.port.city.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class CityInteriorClient {
    private static final java.util.LinkedHashSet<Long> CLOSED=new java.util.LinkedHashSet<>();
    private static void closed(long token){CLOSED.add(token);if(CLOSED.size()>64)CLOSED.remove(CLOSED.iterator().next());}
    @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->CityElevatorNetwork.clientReceiver=v->{var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||CLOSED.contains(v.token()))return;if(mc.screen instanceof LiftScreen s&&s.view.controller().equals(v.controller()))s.update(v);else mc.setScreen(new LiftScreen(v));});}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(CityInteriorContent.SEAT.get(),EmptySeat::new);e.registerEntityRenderer(CityInteriorContent.CABIN.get(),CabinRenderer::new);}
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT)
    public static final class Controls {
        private static java.util.UUID lastSeat;
        @SubscribeEvent public static void seatedFacing(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){var p=Minecraft.getInstance().player;if(p!=null&&p.getVehicle()instanceof CitySeatEntity seat){if(!seat.getUUID().equals(lastSeat)){lastSeat=seat.getUUID();float yaw=seat.getYRot();p.setYRot(yaw);p.yRotO=yaw;p.setXRot(0);p.xRotO=0;p.setYHeadRot(yaw);p.yHeadRotO=yaw;p.yBodyRot=p.yBodyRotO=yaw;}}else lastSeat=null;}
        @SubscribeEvent public static void movement(net.neoforged.neoforge.client.event.MovementInputUpdateEvent e){if(e.getEntity().getVehicle()instanceof CityElevatorEntity cabin){var i=e.getInput();i.forwardImpulse=0;i.leftImpulse=0;i.jumping=false;i.up=i.down=i.left=i.right=false;if(cabin.phaseValue()!=CityElevatorController.Phase.DWELL.ordinal()||cabin.door()!=12)i.shiftKeyDown=false;}}
        @SubscribeEvent public static void use(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered e){var mc=Minecraft.getInstance();if(e.isUseItem()&&mc.screen==null&&mc.player!=null&&mc.player.getVehicle()instanceof CityElevatorEntity cabin){PacketDistributor.sendToServer(new CityElevatorNetwork.Request(cabin.controller(),0,CityElevatorNetwork.OPEN,-1));e.setCanceled(true);e.setSwingHand(false);}}
    }
    public static final class EmptySeat extends EntityRenderer<CitySeatEntity>{
        EmptySeat(EntityRendererProvider.Context c){super(c);}
        @Override public ResourceLocation getTextureLocation(CitySeatEntity e){return TextureAtlas.LOCATION_BLOCKS;}
    }
    public static final class CabinRenderer extends EntityRenderer<CityElevatorEntity>{
        CabinRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=.8F;}
        @Override public ResourceLocation getTextureLocation(CityElevatorEntity e){return TextureAtlas.LOCATION_BLOCKS;}
        private void part(PoseStack p,MultiBufferSource b,int light,String material,double x,double y,double z,double w,double h,double d){
            p.pushPose();p.translate(x,y,z);p.scale((float)w,(float)h,(float)d);Minecraft.getInstance().getBlockRenderer().renderSingleBlock(material.startsWith("interior:")?CityInteriorContent.state(material.substring(9)):CityBlocks.block(material).defaultBlockState(),p,b,light,OverlayTexture.NO_OVERLAY);p.popPose();
        }
        @Override public void render(CityElevatorEntity e,float yaw,float partial,PoseStack p,MultiBufferSource b,int light){
            part(p,b,light,"composite_floor",-.95,-.12,-.95,1.9,.12,1.9);
            part(p,b,light,"interior:elevator_shell",-.95,0,.88,1.9,1.05,.07);
            part(p,b,light,"curtain_glass_clear",-.86,1.05,.90,1.72,1.28,.045);
            part(p,b,light,"interior:elevator_shell",-.95,2.33,.88,1.9,.47,.07);
            part(p,b,light,"interior:elevator_shell",-.95,0,-.95,.07,2.8,1.9);
            part(p,b,light,"interior:elevator_shell",.88,0,-.95,.07,2.8,1.9);
            part(p,b,light,"white_panel_wall",-.95,2.72,-.95,1.9,.08,1.9);
            part(p,b,15728880,"neon_cyan",-.85,2.68,-.7,1.7,.025,.06);
            part(p,b,15728880,"neon_cyan",-.85,2.68,.64,1.7,.025,.06);
            part(p,b,light,"dark_panel_wall",-.94,0,-.97,1.88,.08,.08);
            part(p,b,light,"dark_panel_wall",-.94,2.35,-.97,1.88,.37,.08);
            double slide=e.door()/12.0*.88;
            part(p,b,light,"interior:elevator_shell",-.88-slide,.08,-.96,.87,2.27,.06);
            part(p,b,light,"interior:elevator_shell",.01+slide,.08,-.96,.87,2.27,.06);
            part(p,b,light,"curtain_glass_teal",-.80-slide,1.05,-.97,.70,.9,.035);
            part(p,b,light,"curtain_glass_teal",.09+slide,1.05,-.97,.70,.9,.035);
            // Matching handrails and skirting remain sub-block geometry, not additional entities.
            part(p,b,light,"brushed_steel",-.82,.85,.78,1.64,.045,.05);
            part(p,b,light,"brushed_steel",-.81,.85,-.65,.045,.045,1.4);
            part(p,b,light,"brushed_steel",.76,.85,-.65,.045,.045,1.4);
            part(p,b,light,"dark_panel_wall",-.86,.03,.80,1.72,.08,.045);
            p.pushPose();p.translate(-.25,2.48,-.865);p.scale(.009F,-.009F,.009F);
            Minecraft.getInstance().font.drawInBatch("F"+(e.displayFloor()+1)+(e.phaseValue()==4?"  ↕":""),0,0,0xffa8f0eb,false,p.last().pose(),b,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0,15728880);p.popPose();
            super.render(e,yaw,partial,p,b,light);
        }
    }
    public static final class LiftScreen extends Screen {
        CityElevatorNetwork.View view;int page;
        LiftScreen(CityElevatorNetwork.View v){super(Component.translatable("city.elevator.title"));view=v;}
        void update(CityElevatorNetwork.View v){boolean changed=view.riding()!=v.riding();if(view.token()!=v.token())closed(view.token());view=v;if(changed){clearWidgets();init();}}
        private void send(int action,int floor){PacketDistributor.sendToServer(new CityElevatorNetwork.Request(view.controller(),view.token(),action,floor));}
        @Override protected void init(){
            int x=(width-280)/2,y=(height-270)/2;int[] floors=view.floors();
            for(int i=0;i<12;i++){int f=page*12+i;if(f>=floors.length)break;final int target=f;
                var button=Button.builder(Component.literal("F"+(f+1)),b->send(CityElevatorNetwork.SELECT,target)).bounds(x+18+(i%3)*82,y+61+(i/3)*25,76,20).build();button.active=view.riding();addRenderableWidget(button);
            }
            var prev=Button.builder(Component.literal("◀"),b->{if(page>0){page--;clearWidgets();init();}}).bounds(x+18,y+167,40,20).build();prev.active=page>0;addRenderableWidget(prev);
            var next=Button.builder(Component.literal("▶"),b->{if((page+1)*12<floors.length){page++;clearWidgets();init();}}).bounds(x+222,y+167,40,20).build();next.active=(page+1)*12<floors.length;addRenderableWidget(next);
            if(!view.riding()){
                addRenderableWidget(Button.builder(Component.translatable("city.elevator.call"),b->send(CityElevatorNetwork.CALL,view.panel())).bounds(x+18,y+198,116,20).build());
                addRenderableWidget(Button.builder(Component.translatable("city.elevator.board"),b->send(CityElevatorNetwork.BOARD,view.panel())).bounds(x+146,y+198,116,20).build());
            }else{
                addRenderableWidget(Button.builder(Component.translatable("city.elevator.exit"),b->send(CityElevatorNetwork.EXIT,-1)).bounds(x+18,y+198,116,20).build());
                addRenderableWidget(Button.builder(Component.translatable("city.elevator.emergency"),b->send(CityElevatorNetwork.EMERGENCY,-1)).bounds(x+146,y+198,116,20).build());
            }
            addRenderableWidget(Button.builder(Component.translatable("city.elevator.close"),b->onClose()).bounds(x+82,y+235,116,20).build());
        }
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){closed(view.token());send(CityElevatorNetwork.CLOSE,-1);super.onClose();}
        @Override public void removed(){closed(view.token());super.removed();}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            int x=(width-280)/2,y=(height-270)/2;g.fill(0,0,width,height,0x55061018);g.fill(x,y,x+280,y+270,0xf0162935);g.fill(x,y,x+280,y+3,0xff80d5db);
            g.drawCenteredString(font,title,x+140,y+14,0xffe8f5f5);
            String current=view.current()<0?"↕":"F"+(view.current()+1);String status=Component.translatable("city.elevator.phase."+Math.max(0,Math.min(5,view.phase()))).getString();
            g.drawCenteredString(font,Component.literal(current+"  ·  "+status),x+140,y+34,0xff9ed8df);
            g.drawCenteredString(font,Component.translatable(view.riding()?"city.elevator.choose":"city.elevator.capacity"),x+140,y+49,0xffb9c9ce);
            if(!view.failure().isEmpty())g.drawCenteredString(font,Component.translatable("city.elevator.reason."+view.failure()),x+140,y+222,0xffffb99d);
            g.drawCenteredString(font,Component.literal((page+1)+" / "+((view.floors().length+11)/12)),x+140,y+173,0xffb9c9ce);
            super.render(g,mx,my,partial);
        }
    }
}
