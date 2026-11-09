/* AcademyCraft HandlerRender modern RenderType state. See NOTICE. */
package cn.academy.port.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Unlit/fog-free texture shader, legacy alpha blend, explicit disabled depth and culling. */
final class ClassicMineRenderType extends RenderType {
    private ClassicMineRenderType(){super("unused",DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,4096,false,false,()->{},()->{});}
    // IMPORTANT: MC1.21.1 NO_DEPTH_TEST (function519) is a NO-OP, so using it alone
    // would inherit enabled depth and break through-wall MineDetect. Explicit state is required.
    private static final DepthTestStateShard THROUGH_WALL=new DepthTestStateShard("academy_no_depth",519) {
        @Override public void setupRenderState(){RenderSystem.disableDepthTest();}
        @Override public void clearRenderState(){RenderSystem.enableDepthTest();}
    };
    private static final TransparencyStateShard LEGACY_ALPHA=new TransparencyStateShard("academy_mine_alpha",()->{
        RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    },()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
    static final RenderType TYPE=RenderType.create("academy_mine_detect",DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS,65536,false,false,CompositeState.builder()
                    // Stock position_tex_color has no fog/light uniforms; confirmed in cached1.21.1 shaders.
                    .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                    .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("academy",ClassicMineVisual.TEXTURE),false,false))
                    .setTransparencyState(LEGACY_ALPHA).setDepthTestState(THROUGH_WALL)
                    .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                    .setWriteMaskState(COLOR_WRITE).setOutputState(MAIN_TARGET).createCompositeState(false));
}
