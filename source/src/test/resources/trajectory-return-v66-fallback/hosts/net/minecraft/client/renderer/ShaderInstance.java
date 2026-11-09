package net.minecraft.client.renderer;
import net.minecraft.resources.ResourceLocation;import net.minecraft.server.packs.resources.ResourceProvider;import com.mojang.blaze3d.vertex.VertexFormat;import com.mojang.blaze3d.shaders.Uniform;
/** Shader identity and uniform boundary only; no GPU/link/compiler behavior is claimed. */
public final class ShaderInstance {public final String source;public ShaderInstance(){source="position-tex-color-shader";}public ShaderInstance(ResourceProvider resources,ResourceLocation name,VertexFormat format)throws java.io.IOException{source=name.toString();}public Uniform getUniform(String name){return new Uniform(name);}public int getId(){return 1;}public void close(){}}
