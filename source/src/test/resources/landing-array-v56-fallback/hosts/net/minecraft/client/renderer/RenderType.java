package net.minecraft.client.renderer;
import java.util.*;import java.util.function.Supplier;import net.minecraft.resources.ResourceLocation;import com.mojang.blaze3d.vertex.*;
public class RenderType {
 public final String name;public Runnable setup,clear;public String description;
 protected static final Shard TRANSLUCENT_TRANSPARENCY=new Shard("translucent"),NO_CULL=new Shard("no-cull"),NO_LIGHTMAP=new Shard("no-lightmap"),NO_OVERLAY=new Shard("no-overlay"),COLOR_WRITE=new Shard("color-only"),PARTICLES_TARGET=new Shard("particles-target"),MAIN_TARGET=new Shard("main-target"),LEQUAL_DEPTH_TEST=new Shard("lequal"),VIEW_OFFSET_Z_LAYERING=new Shard("view-offset");
 public RenderType(String n,VertexFormat f,VertexFormat.Mode m,int size,boolean c,boolean sort,Runnable s,Runnable e){name=n;setup=s;clear=e;description=n+":"+f+":"+m+":"+size+":"+c+":"+sort;}
 public static RenderType create(String n,VertexFormat f,VertexFormat.Mode m,int size,boolean c,boolean sort,CompositeState state){RenderType t=new RenderType(n,f,m,size,c,sort,()->state.run(true),()->state.run(false));t.description+=":"+state;return t;}
 public String toString(){return description;}
 public static class Shard {final String name;Runnable setup=()->{},clear=()->{};public Shard(String n){name=n;}public String toString(){return name;}}
 public static class LineStateShard extends Shard{public LineStateShard(OptionalDouble d){super("line-width="+d);}}
 public static class ShaderStateShard extends Shard {private final Supplier<ShaderInstance> supplier;public ShaderStateShard(Supplier<ShaderInstance> s){super("position-tex-color-shader");supplier=s;}public String toString(){ShaderInstance selected=supplier.get();return selected==null?"shader=null":selected.source;}}
 public static class TextureStateShard extends Shard {public TextureStateShard(ResourceLocation r,boolean a,boolean b){super("texture="+r+","+a+","+b);}}
 public static class TransparencyStateShard extends Shard {public TransparencyStateShard(String n,Runnable a,Runnable b){super(n);setup=a;clear=b;}}
 public static class CompositeState {
  final LinkedHashMap<String,Shard> s;CompositeState(LinkedHashMap<String,Shard> v){s=new LinkedHashMap<>(v);}public static Builder builder(){return new Builder();}public String toString(){return s.toString();}void run(boolean enter){for(Shard v:s.values())(enter?v.setup:v.clear).run();}
  public static class Builder {final LinkedHashMap<String,Shard>s=new LinkedHashMap<>();Builder put(String k,Shard v){s.put(k,v);return this;}
   public Builder setLineState(Shard v){return put("line",v);}public Builder setShaderState(Shard v){return put("shader",v);}public Builder setTextureState(Shard v){return put("texture",v);}public Builder setTransparencyState(Shard v){return put("blend",v);}public Builder setDepthTestState(Shard v){return put("depth",v);}public Builder setCullState(Shard v){return put("cull",v);}public Builder setLightmapState(Shard v){return put("lightmap",v);}public Builder setOverlayState(Shard v){return put("overlay",v);}public Builder setWriteMaskState(Shard v){return put("write",v);}public Builder setOutputState(Shard v){return put("target",v);}public Builder setLayeringState(Shard v){return put("layer",v);}public CompositeState createCompositeState(boolean outline){return new CompositeState(s);}}
 }
}
