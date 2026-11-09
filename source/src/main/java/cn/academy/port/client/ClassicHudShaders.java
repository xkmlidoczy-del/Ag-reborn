package cn.academy.port.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/** GLSL 150 equivalents of the two classic GLSL 120 fragment shaders and LambdaLib ShaderMono, plus its no-alpha-test font pass. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClassicHudShaders {
    static ShaderInstance cp,overload,mono,font;
    private ClassicHudShaders(){}
    @SubscribeEvent public static void register(RegisterShadersEvent event){
        cp=null;overload=null;mono=null;font=null;
        try{
            event.registerShader(new ShaderInstance(event.getResourceProvider(),id("classic_cpbar"),DefaultVertexFormat.POSITION_TEX_COLOR),s->cp=s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),id("classic_overload"),DefaultVertexFormat.POSITION_TEX_COLOR),s->overload=s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),id("classic_mono"),DefaultVertexFormat.POSITION_TEX_COLOR),s->mono=s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),id("classic_font"),DefaultVertexFormat.POSITION_TEX_COLOR),s->font=s);
        }catch(IOException exception){LogUtils.getLogger().warn("Classic Academy HUD shader loading failed; original texture-only fallback will be used",exception);}
    }
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("academy",path);}
}
