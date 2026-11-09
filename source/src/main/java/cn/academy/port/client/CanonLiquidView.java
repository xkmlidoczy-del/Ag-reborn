package cn.academy.port.client;

import cn.academy.port.canon.CanonLiquidEntities;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.lwjgl.glfw.GLFW;

/** A local camera lease, admitted only by the server-synchronized owner of a live body. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class CanonLiquidView {
    private static final KeyMapping VIEW=new KeyMapping("key.academy.liquid_view",GLFW.GLFW_KEY_B,"key.categories.academy");
    private static CanonLiquidEntities.Shadow body;
    private static ClientLevel world;
    private CanonLiquidView() {}

    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event){event.register(VIEW);}
    }

    private static boolean valid(Minecraft mc,CanonLiquidEntities.Shadow shadow) {
        return mc.player!=null && mc.level!=null && mc.getConnection()!=null && mc.player.isAlive() && !mc.player.isRemoved()
                && !mc.player.isSpectator() && !mc.player.isPassenger() && !mc.player.isSleeping()
                && shadow!=null && shadow.hasObservationModule() && shadow.level()==mc.level && !shadow.isRemoved() && shadow.isAlive()
                && mc.level.getEntity(shadow.getId())==shadow && shadow.controlOwner().filter(mc.player.getUUID()::equals).isPresent()
                && shadow.distanceToSqr(mc.player)<=24*24;
    }
    private static boolean active(Minecraft mc){return CanonLiquidViewRules.hideFirstPersonHands(
            body!=null,body!=null && mc.getCameraEntity()==body,valid(mc,body));}
    /** Do not overwrite a different mod's subsequent camera selection. */
    private static void clear(Minecraft mc) {
        if(body!=null && mc.getCameraEntity()==body)mc.setCameraEntity(mc.player);
        body=null;world=null;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(body!=null && CanonLiquidViewRules.lease(mc.getCameraEntity()==body,world==mc.level,valid(mc,body),false)
                !=CanonLiquidViewRules.LeaseAction.KEEP)clear(mc);
        while(VIEW.consumeClick()) {
            if(mc.screen!=null)continue;
            if(body!=null){clear(mc);continue;}
            if(mc.player==null || mc.level==null || mc.getCameraEntity()!=mc.player)continue;
            var matches=mc.level.getEntitiesOfClass(CanonLiquidEntities.Shadow.class,mc.player.getBoundingBox().inflate(24),candidate->valid(mc,candidate));
            if(!CanonLiquidViewRules.mayEnter(mc.getCameraEntity()==mc.player,mc.screen!=null,matches.size()))continue;
            body=matches.getFirst();world=mc.level;mc.setCameraEntity(body);
        }
        if(active(mc)) {
            // Vanilla suppresses LocalPlayer.sendPosition while the camera is another entity.
            // Keep the same server-validated position/look stream so the shadow follows live aim,
            // and normal player physics, falling and corrections remain synchronized.
            var p=mc.player;
            if(Double.isFinite(p.getX()) && Double.isFinite(p.getY()) && Double.isFinite(p.getZ())
                    && Float.isFinite(p.getYRot()) && Float.isFinite(p.getXRot()))
                mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot(),p.onGround()));
            else clear(mc);
        }
    }
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc=Minecraft.getInstance();
        if(active(mc) && event.getCamera().getEntity()==body){
            float partial=(float)event.getPartialTick();
            event.setYaw(mc.player.getViewYRot(partial));event.setPitch(mc.player.getViewXRot(partial));
        }
    }
    @SubscribeEvent public static void movement(MovementInputUpdateEvent event) {
        Minecraft mc=Minecraft.getInstance();if(!active(mc)||event.getEntity()!=mc.player)return;
        var input=event.getInput();input.forwardImpulse=0;input.leftImpulse=0;
        input.up=false;input.down=false;input.left=false;input.right=false;input.jumping=false;
        mc.player.xxa=0;mc.player.zza=0;
        // Sneak remains available: the authoritative server uses it to pause the body.
    }
    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        if(active(Minecraft.getInstance())){event.setCanceled(true);event.setSwingHand(false);}
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void renderHand(RenderHandEvent event){if(active(Minecraft.getInstance()))event.setCanceled(true);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear(Minecraft.getInstance());}
}
