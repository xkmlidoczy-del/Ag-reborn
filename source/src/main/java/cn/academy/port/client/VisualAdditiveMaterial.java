package cn.academy.port.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Alpha-weighted additive light (soft shader, so faint fragments are never alpha-discarded) (blooms, flares, rings, arcs, sparks) for the shared decoration kit; no depth writes. */
final class VisualAdditiveMaterial extends RenderType {
    private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
    private VisualAdditiveMaterial() { super("unused", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, true, () -> {}, () -> {}); }

    static RenderType additive(ResourceLocation texture) {
        return TYPES.computeIfAbsent(texture, t -> create("academy_visual_additive", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, true,
                CompositeState.builder().setShaderState(new ShaderStateShard(() -> RailThunderArraySoftMaterial.shaderOrNull() != null ? RailThunderArraySoftMaterial.shaderOrNull() : GameRenderer.getPositionTexColorShader()))
                        .setTextureState(new TextureStateShard(t, true, false)).setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                        .setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
    }
}
