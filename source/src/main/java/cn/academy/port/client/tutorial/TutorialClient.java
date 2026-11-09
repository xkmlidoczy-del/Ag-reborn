package cn.academy.port.client.tutorial;

import cn.academy.port.client.AcademyClient;
import cn.academy.port.tutorial.ClassicTutorials;
import cn.academy.port.tutorial.TutorialActivatedEvent;
import cn.academy.port.tutorial.TutorialState;
import cn.academy.port.tutorial.TutorialStorage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.io.IOException;

/** Direct item/app guide entry point, with server-authored research state and stable Misaka identity. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class TutorialClient {
    static ShaderInstance fontShader;
    private static TutorialState state;
    private TutorialClient(){}
    public static void open(){AcademyClient.request("tutorial_open","");}
    public static void open(CompoundTag snapshot){receive(snapshot);Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.setScreen(new ClassicTutorialScreen(state));}
    public static void receive(CompoundTag snapshot){
        state=TutorialStorage.decode(snapshot);Minecraft mc=Minecraft.getInstance();
        if(mc.screen instanceof ClassicTutorialScreen screen)screen.receive(state);
        String activated=snapshot.getString("tutorial");
        if(mc.player!=null&&ClassicTutorials.knownPage(activated))NeoForge.EVENT_BUS.post(new TutorialActivatedEvent(mc.player,ClassicTutorials.page(activated)));
    }
    public static void clear(){state=null;TutorialNotifications.clear();}
    @SubscribeEvent public static void shaders(RegisterShadersEvent event){
        fontShader=null;
        try{event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("academy","tutorial_font"),DefaultVertexFormat.POSITION_TEX_COLOR),s->fontShader=s);}
        catch(IOException exception){LogUtils.getLogger().warn("Classic guide font shader unavailable",exception);}
    }
}
