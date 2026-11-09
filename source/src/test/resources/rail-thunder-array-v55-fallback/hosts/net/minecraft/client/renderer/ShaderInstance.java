package net.minecraft.client.renderer;
import java.io.*;import net.minecraft.server.packs.resources.ResourceManager;import net.minecraft.resources.ResourceLocation;import com.mojang.blaze3d.vertex.VertexFormat;
public class ShaderInstance {public static boolean failCompile;public static int attempts,created,closed;private int id;public String source="position-tex-color-shader";
 public ShaderInstance(){id=0;}
 public ShaderInstance(ResourceManager provider,ResourceLocation name,VertexFormat format)throws IOException {attempts++;if(failCompile)throw new IOException("controlled shader compilation failure");for(String extension:new String[]{"json","vsh","fsh"})try(var in=provider.getResourceOrThrow(ResourceLocation.fromNamespaceAndPath(name.namespace(),"shaders/core/"+name.path()+"."+extension)).open()){if(in.readAllBytes().length==0)throw new IOException("empty shader resource");}id=++created;source="shader="+name;}
 public int getId(){return id;}public void close(){closed++;}}
