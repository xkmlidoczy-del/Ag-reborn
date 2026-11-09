package cn.academy.port.fleet.client;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import java.util.*;
/** Shared immutable local-space geometry; per-node matrices stay dynamic. */
public final class VehicleGpu {
 public static ShaderInstance shader;
 private static final java.util.function.BooleanSupplier IRIS=irisProbe();
 private static long irisChecked=Long.MIN_VALUE;private static boolean shaderPack;
 private static java.util.function.BooleanSupplier irisProbe(){
  try{var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");var instance=api.getMethod("getInstance").invoke(null);var enabled=api.getMethod("isShaderPackInUse");
   return ()->{try{return (Boolean)enabled.invoke(instance);}catch(ReflectiveOperationException incompatible){return true;}};
  }catch(ClassNotFoundException absent){return ()->false;}catch(ReflectiveOperationException incompatible){return ()->true;}
 }
 public static boolean shaderPackActive(){long now=net.minecraft.Util.getMillis();if(irisChecked==Long.MIN_VALUE||now-irisChecked>100){shaderPack=IRIS.getAsBoolean();irisChecked=now;}return shaderPack;}
 private static final LinkedHashMap<String,VertexBuffer> CACHE=new LinkedHashMap<>(64,.75f,true);
 public static void clear(){var old=new ArrayList<>(CACHE.values());CACHE.clear();if(RenderSystem.isOnRenderThread())old.forEach(VertexBuffer::close);else RenderSystem.recordRenderCall(()->old.forEach(VertexBuffer::close));}
 private static VertexBuffer create(AcmMeshReader.Part part,int light){
  var result=new VertexBuffer(VertexBuffer.Usage.STATIC);
  try(var memory=new ByteBufferBuilder(Math.max(4096,part.triangles()*144))){
   var builder=new BufferBuilder(memory,VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);float[] a=part.values();
   for(int tri=0;tri<part.triangles();tri++)for(int corner=0;corner<4;corner++){int i=(tri*3+Math.min(corner,2))*8;builder.addVertex(a[i],a[i+1],a[i+2]).setColor(255,255,255,255).setUv(a[i+6],a[i+7]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(a[i+3],a[i+4],a[i+5]);}
   result.bind();try(var data=builder.buildOrThrow()){result.upload(data);}finally{VertexBuffer.unbind();}
  }
  return result;
 }
 public static boolean draw(String mesh,AcmMeshReader.Part part,PoseStack ps,ResourceLocation texture,int light){
  // Iris needs its own extended vertex attributes and render pass. Let the normal
  // entity buffer build those attributes; feeding the private VBO hides the shell.
  if(shader==null||shaderPackActive())return false;String key=mesh+":"+part.name()+":"+light;var buffer=CACHE.get(key);if(buffer==null){buffer=create(part,light);CACHE.put(key,buffer);if(CACHE.size()>256){var it=CACHE.entrySet().iterator();it.next().getValue().close();it.remove();}}
  var type=RenderType.entityCutoutNoCull(texture);type.setupRenderState();RenderSystem.setShader(()->shader);shader.safeGetUniform("PoseNormalMat").set(ps.last().normal());
  buffer.bind();buffer.drawWithShader(new Matrix4f(RenderSystem.getModelViewMatrix()).mul(ps.last().pose()),RenderSystem.getProjectionMatrix(),shader);VertexBuffer.unbind();type.clearRenderState();return true;
 }
}
