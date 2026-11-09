package cn.academy.port.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Relay-local render types freeze one material/fallback choice for the complete visible group. */
final class RemoteScatterRelayRenderTypes extends RenderType {
    private record Key(ResourceLocation texture, boolean additive, boolean cull, boolean enhanced) {}
    private static final Map<Key, RenderType> TYPES = new HashMap<>();
    private static final TransparencyStateShard SOFT_ADDITIVE = new TransparencyStateShard("academy_remote_relay_soft_additive", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
    }, () -> { RenderSystem.disableBlend(); RenderSystem.defaultBlendFunc(); });
    private RemoteScatterRelayRenderTypes() { super("unused", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, false, () -> {}, () -> {}); }
    static RenderType type(ResourceLocation texture, boolean additive, boolean cull, boolean enhanced) {
        return TYPES.computeIfAbsent(new Key(texture, additive, cull, enhanced), key -> RenderType.create(
                "academy_remote_relay_" + key.texture().getPath() + "_" + key.additive() + "_" + key.cull() + "_" + key.enhanced(),
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, false,
                CompositeState.builder().setShaderState(new ShaderStateShard(key.enhanced() ? FoldedMirrorMaterial::get : GameRenderer::getPositionTexColorShader))
                        .setTextureState(new TextureStateShard(key.texture(), true, false))
                        .setTransparencyState(key.additive() ? SOFT_ADDITIVE : TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setCullState(key.cull() ? CULL : NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                        .setOutputState(PARTICLES_TARGET).createCompositeState(false)));
    }
}
