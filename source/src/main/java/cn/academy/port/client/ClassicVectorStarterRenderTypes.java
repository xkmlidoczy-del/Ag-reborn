/* AcademyCraft1.0.7 WaveEffect/ParabolaEffect material adaptation, GPLv3; see NOTICE. */
package cn.academy.port.client;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
/** Unlit white/textured SRC_ALPHA blend; waves explicitly disable depth, trajectory retains depth test. */
final class ClassicVectorStarterRenderTypes extends RenderType {
    private ClassicVectorStarterRenderTypes(String n,VertexFormat f,VertexFormat.Mode m,int b,boolean c,boolean s,Runnable a,Runnable z){super(n,f,m,b,c,s,a,z);}
    private static final DepthTestStateShard NO_DEPTH = new DepthTestStateShard("academy_vector_wave_no_depth",519) {
        @Override public void setupRenderState(){RenderSystem.disableDepthTest();}
        @Override public void clearRenderState(){RenderSystem.enableDepthTest();}
    };
    static final RenderType PATH=make("path","glow_line",false),WAVE=make("wave","glow_circle",true);
    private static RenderType make(String n,String t,boolean wave){
        var builder=CompositeState.builder().setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("academy","textures/effects/"+t+".png"),false,false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP)
                .setOverlayState(NO_OVERLAY).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET);
        if(wave)builder.setDepthTestState(NO_DEPTH);
        return RenderType.create("academy_vector_"+n,DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,4096,false,true,builder.createCompositeState(false));
    }
}
