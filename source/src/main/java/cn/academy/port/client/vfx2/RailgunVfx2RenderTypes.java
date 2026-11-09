package cn.academy.port.client.vfx2;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Scoped optional layers: additive shoulders and a continuous alpha-blended inner material. */
final class RailgunVfx2RenderTypes extends RenderType {
    private static final TransparencyStateShard LIGHT = new TransparencyStateShard("academy_railgun_vfx2_light", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
    }, () -> { RenderSystem.disableBlend(); RenderSystem.defaultBlendFunc(); });
    static final RenderType HALO = type(RailgunVfx2Material.HALO);
    static final RenderType CORE = type(RailgunVfx2Material.CORE);
    static final RenderType INNER_CORE = create("academy_railgun_vfx2_inner_core", DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, false,
            CompositeState.builder().setShaderState(new ShaderStateShard(RailgunVfx2Material::coreShader))
                    .setTextureState(new TextureStateShard(RailgunVfx2Material.NOISE, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP)
                    .setOverlayState(NO_OVERLAY).setOutputState(PARTICLES_TARGET).createCompositeState(false));
    private RailgunVfx2RenderTypes() { super("unused", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, false, ()->{}, ()->{}); }
    private static RenderType type(ResourceLocation texture) {
        return create("academy_railgun_vfx2_"+texture.getPath(),DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,4096,false,false,
                CompositeState.builder().setShaderState(new ShaderStateShard(RailgunVfx2Material::shader))
                .setTextureState(new TextureStateShard(texture,true,false)).setTransparencyState(LIGHT)
                .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL)
                .setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY).setOutputState(PARTICLES_TARGET).createCompositeState(false));
    }
}
