package cn.academy.biohum.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

/** The material keeps its hue over bright terrain. A separate pass adds emission at the edges. */
final class SkillRenderType extends RenderType {
    static RenderType material(ResourceLocation texture, boolean emissive) {
        return create(emissive?"biohum_skill_glow":"biohum_skill_material",
                DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,
                32768,false,!emissive,CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new TextureStateShard(texture,true,false))
                        .setTransparencyState(emissive?LIGHTNING_TRANSPARENCY:TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOutputState(PARTICLES_TARGET)
                        .createCompositeState(false));
    }

    private SkillRenderType() {
        super("unused",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,
                256,false,false,()->{},()->{});
    }
}
