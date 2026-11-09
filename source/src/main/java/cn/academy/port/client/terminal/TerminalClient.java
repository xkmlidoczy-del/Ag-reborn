/* AcademyCraft1.0.7 TerminalUI/TerminalInstalledEvent client adaptation. GPLv3. */
package cn.academy.port.client.terminal;

import cn.academy.port.client.ClassicDeveloperScreen;
import cn.academy.port.client.ClassicTerminalDrawing;
import cn.academy.port.terminal.TerminalNetwork;
import cn.academy.port.terminal.TerminalState;
import cn.academy.port.terminal.TerminalStorage;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.glfw.GLFW;

/** All app activations are server-authorized; this class owns only the local presentation. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class TerminalClient {
    public static final KeyMapping OPEN=new KeyMapping("ac.settings.prop.open_data_terminal",GLFW.GLFW_KEY_LEFT_ALT,"key.categories.academy");
    private static final TerminalTimeline.ReleaseKey KEY=new TerminalTimeline.ReleaseKey();
    private static TerminalState state=new TerminalState();
    private static final TerminalClientLifecycle.Session SESSION=new TerminalClientLifecycle.Session();
    private static final TerminalClock CLOCK=new TerminalClock();
    private static TerminalHud terminal;
    private static FrequencyTransmitter frequency;
    private static long installation=-1;
    private static ClassicTerminalDrawing installDrawing;
    private TerminalClient(){}
    public static void registerEvents(IEventBus bus){bus.addListener(TerminalClient::registerKeys);}
    public static void registerKeys(RegisterKeyMappingsEvent event){event.register(OPEN);}
    public static void receive(TerminalState snapshot,String effect){
        session();
        if(snapshot==null)return;state=snapshot;
        if(effect.equals("terminal_inst")||effect.equals("install")||effect.equals("terminal_install")){/* AuxGui source begins active time on its first drawn frame. */installation=-2;}
        else if(effect.equals("open")||effect.equals("terminal_open"))toggle();
        if(terminal!=null)terminal.update(state.installedApps());
    }
    public static void receive(CompoundTag data){
        session();
        switch(data.getString("kind")){
            case "terminal_frequency" -> {if(frequency!=null)frequency.receive(data.getString("action"),net.minecraft.core.BlockPos.of(data.getLong("pos")),net.minecraft.core.BlockPos.of(data.getLong("selected")),data.getBoolean("accepted"),data.getString("name"));}
            case "terminal_urban_location" -> {if(Minecraft.getInstance().screen instanceof UrbanStoryScreen screen)screen.location(data);}
            case "terminal_app" -> {receive(TerminalStorage.decode(data),"");activate(data.getString("app"),data);}
            case "terminal_installed" -> {receive(TerminalStorage.decode(data),"");if(Minecraft.getInstance().player!=null)net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new cn.academy.port.terminal.TerminalInstalledEvent(Minecraft.getInstance().player));}
            case "terminal_app_installed" -> {receive(TerminalStorage.decode(data),"");if(Minecraft.getInstance().player!=null)net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new cn.academy.port.terminal.AppInstalledEvent(Minecraft.getInstance().player,data.getString("app")));}
            case "terminal_install_effect" -> receive(TerminalStorage.decode(data),"install");
            case "terminal_open" -> receive(TerminalStorage.decode(data),"open");
            default -> receive(TerminalStorage.decode(data),data.getString("effect"));
        }
    }
    public static void openTerminal(){TerminalNetwork.requestOpen();}
    private static void toggle(){if(terminal!=null){terminal.release();terminal=null;}else if(frequency==null&&state.terminalInstalled()&&Minecraft.getInstance().screen==null)terminal=new TerminalHud(state.installedApps());}
    static void requestApp(String app){if(state.terminalInstalled()&&state.isInstalled(app))TerminalNetwork.requestApp(app);}
    private static void activate(String app,CompoundTag data){
        if(!state.terminalInstalled()||!state.isInstalled(app))return;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;
        switch(app){
            case "settings" -> mc.setScreen(new TerminalSettingsScreen());
            case "skill_tree" -> mc.setScreen(new ClassicDeveloperScreen(true));
            case "media_player" -> cn.academy.port.client.terminal.media.ClassicMediaClient.openScreen();
            case "urban_communication" -> mc.setScreen(new UrbanStoryScreen(data));
            case "freq_transmitter" -> {if(terminal!=null){terminal.release();terminal=null;}frequency=new FrequencyTransmitter();}
            default -> {}
        }
    }
    public static void clear(){if(terminal!=null)terminal.release();terminal=null;if(frequency!=null)frequency.close();frequency=null;installation=-1;if(installDrawing!=null)installDrawing.release();installDrawing=null;state=new TerminalState();KEY.replace(physicalDown());}
    private static void session(){Minecraft mc=Minecraft.getInstance();if(SESSION.synchronize(mc.level,mc.player))clear();}
    static long gameTime(){return CLOCK.update(Util.getMillis(),Minecraft.getInstance().isPaused());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        session();Minecraft mc=Minecraft.getInstance();boolean allowed=mc.player!=null&&mc.level!=null&&mc.screen==null&&frequency==null;
        if(KEY.update(physicalDown(),allowed))openTerminal();while(OPEN.consumeClick()){}
        long now=gameTime();if(frequency!=null&&frequency.tick(now)){frequency.close();frequency=null;}
    }
    private static boolean physicalDown(){var mc=Minecraft.getInstance();var key=OPEN.getKey();if(key.getType()==com.mojang.blaze3d.platform.InputConstants.Type.MOUSE)return key.getValue()>=0&&GLFW.glfwGetMouseButton(mc.getWindow().getWindow(),key.getValue())==GLFW.GLFW_PRESS;if(key.getType()==com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM)return key.getValue()>=0&&com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow().getWindow(),key.getValue());return OPEN.isDown();}
    @SubscribeEvent public static void turn(CalculatePlayerTurnEvent event){if(terminal!=null&&Minecraft.getInstance().screen==null){
        var mc=Minecraft.getInstance();
        if(Boolean.getBoolean("academy.visual.qa")&&(mc.mouseHandler.getXVelocity()!=0||mc.mouseHandler.getYVelocity()!=0))
            com.mojang.logging.LogUtils.getLogger().info("QA terminal turn velocity=({},{}), grabbed={}",mc.mouseHandler.getXVelocity(),mc.mouseHandler.getYVelocity(),mc.mouseHandler.isMouseGrabbed());
        terminal.captureMouse(mc.mouseHandler.getXVelocity(),mc.mouseHandler.getYVelocity());
        /* NeoForge computes raw*.6f+.2f. This gives an exact zero. */event.setMouseSensitivity(-((double).2F)/.6F);event.setCinematicCameraEnabled(false);
    }}
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Pre event){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null)return;
        if(Boolean.getBoolean("academy.visual.qa")&&terminal!=null)
            com.mojang.logging.LogUtils.getLogger().info("QA terminal mouse callback button={}, action={}, raw=({},{})",event.getButton(),event.getAction(),mc.mouseHandler.xpos(),mc.mouseHandler.ypos());
        if(terminal!=null&&event.getButton()==GLFW.GLFW_MOUSE_BUTTON_LEFT){event.setCanceled(true);if(event.getAction()==GLFW.GLFW_RELEASE)terminal.click();}
        else if(frequency!=null&&(event.getButton()==GLFW.GLFW_MOUSE_BUTTON_LEFT||event.getButton()==GLFW.GLFW_MOUSE_BUTTON_RIGHT)){event.setCanceled(true);if(event.getButton()==GLFW.GLFW_MOUSE_BUTTON_RIGHT&&event.getAction()==GLFW.GLFW_PRESS)frequency.click();}
    }
    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered event){if((terminal!=null&&event.isAttack())||frequency!=null){event.setCanceled(true);event.setSwingHand(false);}}
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        session();Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        if(terminal!=null)terminal.render(event.getGuiGraphics());
        if(frequency!=null)frequency.render(event.getGuiGraphics());
        if(installation>=0||installation==-2)renderInstall(event.getGuiGraphics());
    }
    private static void renderInstall(GuiGraphics graphics){
        long now=gameTime();if(installation==-2)installation=now;long elapsed=now-installation;var frame=TerminalTimeline.install(elapsed);
        if(frame.complete()){installation=-1;openTerminal();Minecraft.getInstance().player.displayClientMessage(Component.translatable("ac.terminal.key_hint",OPEN.getTranslatedKeyMessage()),false);if(installDrawing!=null)installDrawing.release();installDrawing=null;return;}
        if(installDrawing==null)installDrawing=new ClassicTerminalDrawing();var d=installDrawing;d.begin(graphics);
        try{double a=frame.alpha();d.fill(164,203,150,9,0x3c3c3c,a*120/255d);d.fill(165.5,204.5,147,6,0xffffff,a*150/255d);d.fill(166,205,146,5,0x1e1e1e,a*200/255d);d.fill(166.5,205.5,145*frame.progress(),4,0xffffff,a*200/255d);d.fill(164,195,40,8,0x3c3c3c,a*120/255d);d.text(Component.translatable("ac.gui.terminal.installing").getString(),164,194,10,0xffffff,.1+.9*a,0);}finally{d.end();}
    }
}
