/* Classic no-cull/depth-write materials translated to current render API. See NOTICE. */
package cn.academy.port.client;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
final class ClassicVectorFinalRenderTypes extends RenderType {
 private ClassicVectorFinalRenderTypes(String n,VertexFormat f,VertexFormat.Mode m,int b,boolean c,boolean s,Runnable a,Runnable z){super(n,f,m,b,c,s,a,z);}
 private static final DepthTestStateShard NO_DEPTH=new DepthTestStateShard("academy_reflection_no_depth",519){@Override public void setupRenderState(){RenderSystem.disableDepthTest();}@Override public void clearRenderState(){RenderSystem.enableDepthTest();}};
 static final RenderType WAVE=make("reflection_wave","glow_circle",false,true),TORNADO=make("plasma_tornado","tornado_ring",false,false),PLASMA=make("plasma_body","glow_circle",true,false);
 private static RenderType make(String name,String texture,boolean plasma,boolean wave){var b=CompositeState.builder().setShaderState(new ShaderStateShard(plasma?ClassicVectorFinalShader::get:ClassicSkillAlphaShader::get)).setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("academy","textures/effects/"+texture+".png"),false,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET);if(wave)b.setDepthTestState(NO_DEPTH);return create("academy_vector_final_"+name,DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,65536,false,true,b.createCompositeState(false));}
}
