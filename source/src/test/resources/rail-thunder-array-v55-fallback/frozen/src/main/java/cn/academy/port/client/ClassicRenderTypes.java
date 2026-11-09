package cn.academy.port.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Modern unlit, alpha-blended buffers. No legacy GL calls or legacy shaders. */
final class ClassicRenderTypes extends RenderType {
    private static final Map<ResourceLocation, RenderType> WORLD = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> HAND = new HashMap<>();

    private ClassicRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode,
                               int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                               Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    static RenderType world(ResourceLocation texture) {
        return WORLD.computeIfAbsent(texture, key -> create(key, true));
    }

    static RenderType hand(ResourceLocation texture) {
        return HAND.computeIfAbsent(texture, key -> create(key, false));
    }

    private static RenderType create(ResourceLocation texture, boolean world) {
        CompositeState state = CompositeState.builder()
                .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                .setTextureState(new TextureStateShard(texture, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(NO_LIGHTMAP)
                .setOverlayState(NO_OVERLAY)
                .setWriteMaskState(COLOR_WRITE)
                .setOutputState(world ? PARTICLES_TARGET : MAIN_TARGET)
                .createCompositeState(false);
        return RenderType.create("academy_classic_" + (world ? "world" : "hand"),
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
                4096, false, true, state);
    }
}
