package cn.aca.omnitrix.client;

import cn.aca.omnitrix.AscalonService;
import cn.aca.omnitrix.Omnitrix;
import cn.aca.omnitrix.PursuitBody;
import cn.aca.omnitrix.Wrist.Stage;
import cn.aca.omnitrix.WristNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityEvent;
import org.lwjgl.glfw.GLFW;

@Mod(value=Omnitrix.ID,dist=Dist.CLIENT)
public final class OmnitrixClient {
    private static final String CATEGORY="key.categories.aca_omnitrix";
    public static final KeyMapping WRIST=key("raise",GLFW.GLFW_KEY_Y);
    public static final KeyMapping PRIMARY=key("primary",GLFW.GLFW_KEY_U);
    public static final KeyMapping SECONDARY=key("secondary",GLFW.GLFW_KEY_I);
    public static final KeyMapping FLICK=key("flick",GLFW.GLFW_KEY_O);
    public static final KeyMapping ASCALON_ARMOR=key("armor_toggle",GLFW.GLFW_KEY_M);
    private static int held;
    private static int ascalonPrimaryHeld;

    public OmnitrixClient(IEventBus modBus){
        WristNetwork.client=new ClientState();
        modBus.addListener(OmnitrixClient::keys);
        modBus.addListener(OmnitrixClient::entities);
        modBus.addListener(OmnitrixClient::layers);
        modBus.addListener(OmnitrixClient::hud);
        modBus.addListener(OmnitrixClient::reload);
        modBus.addListener(OmnitrixClient::clientExtensions);
        modBus.addListener(StarfieldShader::register);
        modBus.addListener(CosmicVfxShader::register);
        modBus.addListener(AscalonSurfaceShader::register);
        var game=NeoForge.EVENT_BUS;
        game.addListener(OmnitrixClient::tick);
        game.addListener(ClientState::hold);
        game.addListener(OmnitrixClient::size);
        game.addListener(OmnitrixClient::joining);
        game.addListener(OmnitrixClient::clear);
        game.addListener(OmnitrixClient::movement);
        game.addListener(EventPriority.HIGHEST,OmnitrixClient::preventActions);
        game.addListener(EventPriority.LOW,WatchRender::morph);
        game.addListener(WristVisuals::world);
        game.addListener(WristPost::render);
        game.addListener(WristPost::camera);
        game.addListener(WristPost::fov);
        game.addListener(OmnitrixClient::resistance);
        game.addListener(OmnitrixClient::resistanceMouse);
    }

    private static KeyMapping key(String p,int code){
        return new KeyMapping("key.aca_omnitrix."+p,KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,code,CATEGORY);
    }

    private static void keys(RegisterKeyMappingsEvent e){
        for(var key:new KeyMapping[]{WRIST,PRIMARY,SECONDARY,FLICK,ASCALON_ARMOR}) e.register(key);
    }

    private static void clientExtensions(RegisterClientExtensionsEvent e) {
        e.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return AscalonRenderer.itemRenderer();
            }
        }, Omnitrix.ASCALON.get());
    }

    private static void entities(EntityRenderersEvent.RegisterRenderers e){
        e.registerEntityRenderer(Omnitrix.PURSUER.get(),WatchRender.Invisible::new);
        e.registerEntityRenderer(Omnitrix.PULSE.get(),PulseRenderer::new);
        e.registerEntityRenderer(Omnitrix.ASCALON_SLASH.get(),AscalonRenderer.SlashEntityRenderer::new);
    }

    private static void layers(EntityRenderersEvent.AddLayers e){
        for(var variant:e.getSkins()) {
            if(e.getSkin(variant) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new WatchRender.Layer(renderer));
                renderer.addLayer(new AscalonRenderer.ArmorLayer(renderer));
            }
        }
    }

    private static void hud(RegisterGuiLayersEvent e){
        e.registerAbove(VanillaGuiLayers.HOTBAR,Omnitrix.id("status"),WristVisuals::hud);
    }

    private static void reload(RegisterClientReloadListenersEvent e){
        e.registerReloadListener((ResourceManagerReloadListener)(ResourceManager ignored)-> {
            Rig.reloadCelestialRigs();
            CosmicVfxShader.preloadTextures(Minecraft.getInstance().getTextureManager());
            WristPost.reload();
        });
    }

    private static void clear(ClientPlayerNetworkEvent.LoggingOut event){ClientState.clear();held=0;ascalonPrimaryHeld=0;}

    private static void size(EntityEvent.Size e){
        if(e.getEntity() instanceof Player p && p.level().isClientSide){
            var view=ClientState.get(p);
            if(view!=null&&view.s().morphed())e.setNewSize(view.s().chosen().dimensions(view.s().watch()).scale(p.getScale()));
        }
    }

    private static boolean controlled(){
        var v=ClientState.self();return v!=null&&v.s().controlled();
    }

    private static void movement(MovementInputUpdateEvent e){
        if(!controlled())return;
        var input=e.getInput();
        input.leftImpulse=0;input.forwardImpulse=0;
        input.up=false;input.down=false;input.left=false;input.right=false;
        input.jumping=false;input.shiftKeyDown=false;
    }

    private static void preventActions(InputEvent.InteractionKeyMappingTriggered e){
        if(controlled()){
            e.setSwingHand(false);
            e.setCanceled(true);
            return;
        }
        var mc = Minecraft.getInstance();
        if (e.isAttack() && mc.player != null && AscalonService.holdingAscalon(mc.player)) {
            WristNetwork.ask(WristNetwork.ASCALON_SWING, 0);
        }
    }

    private static void resistance(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) return;
        if (Minecraft.getInstance().options.keyShift.matches(event.getKey(), event.getScanCode())) sendResistance();
    }

    private static void resistanceMouse(InputEvent.MouseButton.Post event) {
        if (event.getAction() == GLFW.GLFW_PRESS && Minecraft.getInstance().screen == null
                && Minecraft.getInstance().options.keyShift.matchesMouse(event.getButton())) sendResistance();
    }

    private static void sendResistance() {
        var view = ClientState.self();
        if (view != null && view.s().phase() == Stage.LOCKING) WristNetwork.ask(WristNetwork.RESIST, 0);
    }

    private static void tick(ClientTickEvent.Post e){
        ClientState.tick();
        WristVisuals.tick();
        var mc=Minecraft.getInstance();
        var view=ClientState.self();
        if(mc.player==null||mc.screen!=null){
            if (ascalonPrimaryHeld > 0) {
                WristNetwork.ask(WristNetwork.ASCALON_CHARGE, 0);
                ascalonPrimaryHeld = 0;
            }
            held=0;
            clearKeys();
            return;
        }
        if(ASCALON_ARMOR.consumeClick()){
            WristNetwork.ask(WristNetwork.ASCALON_TOGGLE,0);
        }
        if(view!=null&&view.s().controlled()){
            ascalonPrimaryHeld=0;
            held=0;
            clearKeys();
            return;
        }
        int shift=(net.minecraft.client.gui.screens.Screen.hasShiftDown()||mc.options.keyShift.isDown())?1:0;
        boolean clicked=WRIST.consumeClick();
        if(view!=null&&view.s().phase()==Stage.ACTIVE){
            ascalonPrimaryHeld=0;
            if(WRIST.isDown()){
                if(++held==10)WristNetwork.ask(WristNetwork.REVERT,0);
            }else held=0;
            if(PRIMARY.consumeClick())WristNetwork.ask(WristNetwork.PRIMARY,shift);
            if(SECONDARY.consumeClick())WristNetwork.ask(WristNetwork.SECONDARY,shift);
            if(FLICK.consumeClick())WristNetwork.ask(WristNetwork.FLICK,shift);
        }else{
            held=0;
            var asc = ClientState.selfAscalon();
            boolean ascalonReady = (asc != null && asc.s().armored()) || AscalonService.holdingAscalon(mc.player);
            if(ascalonReady){
                if (shift != 0) {
                    if (ascalonPrimaryHeld > 0) {
                        WristNetwork.ask(WristNetwork.ASCALON_CHARGE, 0);
                        ascalonPrimaryHeld = 0;
                    }
                    if (PRIMARY.consumeClick()) WristNetwork.ask(WristNetwork.ASCALON_PRIMARY, 1);
                } else if (PRIMARY.isDown()) {
                    while (PRIMARY.consumeClick()) {}
                    if (ascalonPrimaryHeld == 0) {
                        WristNetwork.ask(WristNetwork.ASCALON_CHARGE, 1);
                    }
                    ascalonPrimaryHeld++;
                } else {
                    if (ascalonPrimaryHeld > 0) {
                        WristNetwork.ask(WristNetwork.ASCALON_PRIMARY, ascalonPrimaryHeld << 1);
                        ascalonPrimaryHeld = 0;
                    } else if (PRIMARY.consumeClick()) {
                        WristNetwork.ask(WristNetwork.ASCALON_PRIMARY, 0);
                    }
                }
                if(SECONDARY.consumeClick())WristNetwork.ask(WristNetwork.ASCALON_SECONDARY,shift);
                if(FLICK.consumeClick())WristNetwork.ask(WristNetwork.ASCALON_SYZYGY,shift);
            }else{
                ascalonPrimaryHeld=0;
                while(PRIMARY.consumeClick()){}
                while(SECONDARY.consumeClick()){}
                while(FLICK.consumeClick()){}
            }
            if(view!=null&&clicked&&!(mc.player.isPassenger()&&!(mc.player.getVehicle() instanceof PursuitBody))
                    &&view.s().phase()!=Stage.ARMING&&view.s().phase()!=Stage.BINDING&&view.s().phase()!=Stage.REVERTING
                    &&view.s().phase()!=Stage.LOCKING){
                boolean shiftOpen=net.minecraft.client.gui.screens.Screen.hasShiftDown();
                WristNetwork.ask(WristNetwork.OPEN,0);
                mc.setScreen(new WristScreen(view.s().watch(),shiftOpen));
            }
        }
    }

    private static void clearKeys(){
        while(WRIST.consumeClick()){}
        while(PRIMARY.consumeClick()){}
        while(SECONDARY.consumeClick()){}
        while(FLICK.consumeClick()){}
        while(ASCALON_ARMOR.consumeClick()){}
    }

    private static void joining(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide && e.getEntity() instanceof Player p && ClientState.get(p) != null) p.refreshDimensions();
    }
}
