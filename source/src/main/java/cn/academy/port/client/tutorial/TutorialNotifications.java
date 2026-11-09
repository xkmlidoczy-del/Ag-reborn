/* AcademyCraft 1.0.7 NotifyUI.onAcquiredKnowledge visual adaptation. GPLv3. */
package cn.academy.port.client.tutorial;

import cn.academy.port.ClassicHudConfig;
import cn.academy.port.tutorial.TutorialActivatedEvent;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** The source replaces an earlier notification with the most recently activated tutorial. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class TutorialNotifications {
    private static final TutorialFont FONT=new TutorialFont();
    private static String lastTitle;
    private static long lastFrame;
    private static double elapsed;
    private TutorialNotifications(){}
    @SubscribeEvent public static void acquired(TutorialActivatedEvent event){
        Minecraft mc=Minecraft.getInstance();if(event.getEntity()!=mc.player)return;
        String language=TutorialDocument.language(mc.getLanguageManager().getSelected());String raw=read(language,event.tutorial.id());if(raw==null)raw=read("en_us",event.tutorial.id());
        try{lastTitle=TutorialDocument.parse(raw==null?TutorialDocument.UNKNOWN:raw).title().strip();}catch(IllegalArgumentException ignored){lastTitle="UNKNOWN";}
        elapsed=0;lastFrame=Util.getMillis();
    }
    private static String read(String language,String id){try(var stream=Minecraft.getInstance().getResourceManager().open(ResourceLocation.fromNamespaceAndPath("academy","tutorials/"+language+"/"+id+".md"))){return new String(stream.readAllBytes(),StandardCharsets.UTF_8);}catch(IOException ignored){return null;}}
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        if(lastTitle==null)return;Minecraft mc=Minecraft.getInstance();long now=Util.getMillis();if(!mc.isPaused())elapsed+=Math.max(0,now-lastFrame);lastFrame=now;
        var frame=TutorialNotificationTimeline.frame(elapsed);if(!frame.visible()){lastTitle=null;return;}
        if(mc.player==null||mc.options.hideGui)return;
        FONT.use(ClassicHudConfig.SPEC.isLoaded()?ClassicHudConfig.FONT.get():"Microsoft YaHei");var graphics=event.getGuiGraphics();var canvas=new TutorialCanvas(graphics);canvas.begin();graphics.pose().pushPose();
        try{
            graphics.pose().translate(ClassicHudConfig.SPEC.isLoaded()?ClassicHudConfig.NOTIFICATION_X.get():0,ClassicHudConfig.SPEC.isLoaded()?ClassicHudConfig.NOTIFICATION_Y.get():15,0);graphics.pose().scale(.25f,.25f,1);
            canvas.rect(TutorialCanvas.texture("guis/notification/back"),0,0,517,170,new TutorialCanvas.Color(1,1,1,frame.background()));
            canvas.rect(TutorialCanvas.texture("tutorial/update_notify"),frame.iconX(),42,83,83,new TutorialCanvas.Color(1,1,1,frame.icon()));
            if(frame.text()>0){var color=new TutorialCanvas.Color(1,1,1,frame.text());FONT.draw(canvas,Component.translatable("ac.tutorial.update").getString(),137,32,38,color,TutorialMarkdown.Style.NORMAL);FONT.draw(canvas,lastTitle,137,81,54,color,TutorialMarkdown.Style.NORMAL);}
        }finally{graphics.pose().popPose();canvas.end();}
    }
    static void clear(){lastTitle=null;elapsed=0;lastFrame=0;FONT.release();}
}
