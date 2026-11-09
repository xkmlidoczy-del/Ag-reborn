package cn.academy.port.client;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
/** Source phase overlays use alpha blending, no culling, no depth test and no depth writes. */
final class ClassicPhaseRenderTypes extends RenderType {
    private static final Map<ResourceLocation,RenderType> TYPES=new HashMap<>();
    private ClassicPhaseRenderTypes(String name,VertexFormat format,VertexFormat.Mode mode,int size,boolean crumbling,boolean sorting,Runnable setup,Runnable clear){super(name,format,mode,size,crumbling,sorting,setup,clear);}
    // Stock1.21.1 NO_DEPTH_TEST (function519) is a no-op. Legacy overlays explicitly
    // disabled depth because all three layers can lie below the opaque black fluid base.
    private static final DepthTestStateShard LEGACY_NO_DEPTH=new DepthTestStateShard("academy_phase_no_depth",519){
        @Override public void setupRenderState(){RenderSystem.disableDepthTest();}
        @Override public void clearRenderState(){RenderSystem.enableDepthTest();}
    };
    static RenderType layer(ResourceLocation texture){return TYPES.computeIfAbsent(texture,key->RenderType.create("academy_phase_layer",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,256,false,true,CompositeState.builder().setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader)).setTextureState(new TextureStateShard(key,false,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setDepthTestState(LEGACY_NO_DEPTH).setWriteMaskState(COLOR_WRITE).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY).createCompositeState(false)));}
}
