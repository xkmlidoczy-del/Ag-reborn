package cn.academy.port.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** NEW221 only: filtered original artwork, explicit terrain occlusion, no depth writes or global bloom. */
final class FoldedMirrorRenderTypes extends RenderType {
    private record Key(ResourceLocation texture, boolean additive, boolean cull) {}
    private static final Map<Key, RenderType> TYPES = new HashMap<>();
    private static final TransparencyStateShard SOFT_ADDITIVE = new TransparencyStateShard("academy_folded_mirror_soft_additive", () -> {
        RenderSystem.enableBlend();
        // Keep the target alpha intact while adding bounded RGB light to the already-rendered scene.
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
    }, () -> { RenderSystem.disableBlend(); RenderSystem.defaultBlendFunc(); });

    private FoldedMirrorRenderTypes() { super("unused", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, false, () -> {}, () -> {}); }

    static RenderType type(ResourceLocation texture, boolean additive) {
        return type(texture, additive, false);
    }
    static RenderType type(ResourceLocation texture, boolean additive, boolean cull) {
        return TYPES.computeIfAbsent(new Key(texture, additive, cull), key -> RenderType.create("academy_folded_mirror_" + key.texture().getPath() + "_" + key.additive() + "_" + key.cull(),
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, false,
                CompositeState.builder().setShaderState(new ShaderStateShard(FoldedMirrorMaterial::get))
                        .setTextureState(new TextureStateShard(key.texture(), true, false))
                        .setTransparencyState(key.additive() ? SOFT_ADDITIVE : TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setCullState(key.cull() ? CULL : NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                        .setOutputState(PARTICLES_TARGET).createCompositeState(false)));
    }
}
