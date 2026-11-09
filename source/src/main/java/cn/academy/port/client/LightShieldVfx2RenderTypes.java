package cn.academy.port.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Matches the original mdshield state, including nearest filtering and depth writes. */
final class LightShieldVfx2RenderTypes extends RenderType {
    static final RenderType SHIELD = create("academy_light_shield_vfx2", DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, false,
            CompositeState.builder().setShaderState(new ShaderStateShard(LightShieldVfx2Material::shader))
                    .setTextureState(new TextureStateShard(LightShieldVfx2Material.TEXTURE, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_DEPTH_WRITE).setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP)
                    .setOverlayState(NO_OVERLAY).setOutputState(PARTICLES_TARGET).createCompositeState(false));
    private LightShieldVfx2RenderTypes() {
        super("unused", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, false, () -> {}, () -> {});
    }
}
