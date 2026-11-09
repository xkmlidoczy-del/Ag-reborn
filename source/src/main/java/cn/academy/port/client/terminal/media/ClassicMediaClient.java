package cn.academy.port.client.terminal.media;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import cn.academy.port.ClassicHudConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** MediaApp.onStart modern entry point. Loaded only by the client distribution. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class ClassicMediaClient {
    static ShaderInstance fontShader;private static final MediaFont HUD_FONT=new MediaFont();private static MediaPlayback.Info hudInfo;private static long lastHudUpdate;
    private ClassicMediaClient(){}
    public static void openScreen(){Minecraft mc=Minecraft.getInstance();if(mc.player==null)return;MediaBackend.instance().refresh();mc.setScreen(new ClassicMediaScreen());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){MediaBackend.instance().tick();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){MediaBackend.instance().resetPlayer();hudInfo=null;HUD_FONT.release();}
    @SubscribeEvent public static void clonePlayer(ClientPlayerNetworkEvent.Clone event){MediaBackend.instance().resetPlayer();hudInfo=null;}
    private static void renderHud(GuiGraphics graphics){Minecraft mc=Minecraft.getInstance();var backend=MediaBackend.instance();var current=backend.current();if(current==null||mc.player==null||mc.options.hideGui){hudInfo=null;return;}
        long now=System.nanoTime();if(hudInfo==null||hudInfo.media()!=current.media()||now-lastHudUpdate>500_000_000L){hudInfo=current;lastHudUpdate=now;}var info=hudInfo;HUD_FONT.use(ClassicHudConfig.FONT.get());var layout=MediaLayout.hud();var original=layout.element("base").box();var position=hudPosition();var base=new MediaLayout.Box(position[0],position[1],original.width(),original.height(),1,"LEFT","TOP");
        drawHud(graphics,base,info,backend.name(info.media()),info.displayTime());
    }
    public static double[] hudPosition(){var mc=Minecraft.getInstance();var b=MediaLayout.hud().element("base").box();return new double[]{mc.getWindow().getGuiScaledWidth()-b.width()+ClassicHudConfig.MEDIA_X.get(),mc.getWindow().getGuiScaledHeight()-b.height()+ClassicHudConfig.MEDIA_Y.get()};}
    public static void setHudPosition(Double x,Double y){var mc=Minecraft.getInstance();var b=MediaLayout.hud().element("base").box();ClassicHudConfig.MEDIA_X.set(x-mc.getWindow().getGuiScaledWidth()+b.width());ClassicHudConfig.MEDIA_Y.set(y-mc.getWindow().getGuiScaledHeight()+b.height());}
    public static void renderHudPreview(GuiGraphics graphics,double x,double y){HUD_FONT.use(ClassicHudConfig.FONT.get());var original=MediaLayout.hud().element("base").box();var current=MediaBackend.instance().current();drawHud(graphics,new MediaLayout.Box(x,y,original.width(),original.height(),1,"LEFT","TOP"),current,current==null?"Media Player":MediaBackend.instance().name(current.media()),current==null?"00:00":current.displayTime());}
    private static void drawHud(GuiGraphics graphics,MediaLayout.Box base,MediaPlayback.Info info,String title,String time){var layout=MediaLayout.hud();
        var canvas=new MediaCanvas(graphics);canvas.begin();graphics.pose().pushPose();graphics.pose().translate(base.x(),base.y(),0);
        var progressBase=layout.box("base/progress_base",base.width(),base.height());canvas.fill(progressBase.x(),progressBase.y(),progressBase.width(),progressBase.height(),new MediaCanvas.Color(0,0,0,.2));
        var progress=layout.box("base/progress",base.width(),base.height());canvas.fill(progress.x(),progress.y(),progress.width()*(info==null?.5:info.progress()),progress.height(),new MediaCanvas.Color(1,1,1,.8));
        drawHudText(canvas,layout,"title",title,base.width(),base.height());drawHudText(canvas,layout,"time",time,base.width(),base.height());graphics.pose().popPose();canvas.end();
    }
    private static void drawHudText(MediaCanvas canvas,MediaLayout layout,String name,String value,double w,double h){var e=layout.element("base/"+name);var b=e.box().resolved(w,h);String text=value;while(!text.isEmpty()&&HUD_FONT.width(text,e.fontSize(),false)>b.width())text=text.substring(0,text.offsetByCodePoints(text.length(),-1));HUD_FONT.draw(canvas,text,b.x(),b.y()+b.height()-e.fontSize(),e.fontSize(),new MediaCanvas.Color(1,1,1,e.alpha()),false,false);}
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration(){}
        @SubscribeEvent public static void audioReload(SoundEngineLoadEvent event){Minecraft.getInstance().execute(()->MediaBackend.instance().audioReloaded());}
        @SubscribeEvent public static void layers(RegisterGuiLayersEvent event){event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("academy","classic_media"),(graphics,partial)->renderHud(graphics));}
        @SubscribeEvent public static void shaders(RegisterShadersEvent event){fontShader=null;try{event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("academy","media_font"),DefaultVertexFormat.POSITION_TEX_COLOR),shader->fontShader=shader);}catch(IOException e){LogUtils.getLogger().warn("Classic media font shader unavailable; standard text shader fallback",e);}}
    }
}
