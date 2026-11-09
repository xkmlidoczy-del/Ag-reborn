/* Original plasma_body GLSL adaptation to core profile. GPLv3; see NOTICE. */
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
@EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClassicVectorFinalShader {
 private static ShaderInstance plasma;private ClassicVectorFinalShader(){}
 @SubscribeEvent public static void register(RegisterShadersEvent e){plasma=null;try{e.registerShader(new ShaderInstance(e.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("academy","classic_plasma_body"),DefaultVertexFormat.POSITION_TEX_COLOR),s->plasma=s);}catch(IOException ex){LogUtils.getLogger().error("Classic plasma ray-march shader could not load",ex);}}
 public static ShaderInstance get(){return plasma;}
 public static void set(float alpha,java.util.List<org.joml.Vector4f> balls){if(plasma==null)return;var count=plasma.getUniform("ballCount");if(count!=null)count.set(balls.size());var opacity=plasma.getUniform("alpha");if(opacity!=null)opacity.set(alpha);for(int i=0;i<16;i++){var u=plasma.getUniform("ball"+i);var b=i<balls.size()?balls.get(i):new org.joml.Vector4f();if(u!=null)u.set(b.x,b.y,-b.z,b.w);}}
}
