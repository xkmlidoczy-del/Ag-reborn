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

/** Source radial-mask comparison and alpha-masked source skill icon on modern GLSL150. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClassicDeveloperShaders {
    static ShaderInstance radial,icon;
    @SubscribeEvent public static void register(RegisterShadersEvent event){
        radial=null;icon=null;
        try{
            event.registerShader(new ShaderInstance(event.getResourceProvider(),id("classic_developer_radial"),DefaultVertexFormat.POSITION_TEX_COLOR),s->radial=s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),id("classic_developer_icon"),DefaultVertexFormat.POSITION_TEX_COLOR),s->icon=s);
        }catch(IOException error){LogUtils.getLogger().warn("Classic developer shader unavailable; partial radial progress will be omitted",error);}
    }
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("academy",path);}
    private ClassicDeveloperShaders(){}
}
