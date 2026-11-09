package net.minecraft.client.renderer;public class ShaderInstance {
 public static int next=100;public static final java.util.Map<Integer,ShaderInstance> programs=new java.util.HashMap<>();
 public static String failName="",missing="";public final int id=++next;public final String name;public boolean closed,deletePending,linked=true;
 public final java.util.Map<String,com.mojang.blaze3d.shaders.Uniform> uniforms=new java.util.HashMap<>();
 public ShaderInstance(String name){this.name=name;programs.put(id,this);for(String n:new String[]{"ModelViewMat","ProjMat","ColorModulator","FadeEnd","SwirlGain","FlowAmount","VisualPhase"})uniforms.put(n,new com.mojang.blaze3d.shaders.Uniform());}
 public ShaderInstance(net.minecraft.server.packs.resources.ResourceProvider p,net.minecraft.resources.ResourceLocation name,com.mojang.blaze3d.vertex.VertexFormat format)throws java.io.IOException{this(name.getPath());if(this.name.equals(failName))throw new java.io.IOException("injected shader constructor failure");}
 public int getId(){return id;}public com.mojang.blaze3d.shaders.Uniform getUniform(String n){return n.equals(missing)?null:uniforms.get(n);}public void close(){closed=true;}
}
