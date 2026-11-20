package cn.aca.omnitrix.client;

import cn.aca.omnitrix.Omnitrix;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

public final class CosmicVfxShader {
    private static ShaderInstance shader;
    private static final int TICKS_PER_SECOND = 20;
    private static final int PHASE_TICKS = 96 * TICKS_PER_SECOND;
    private static final ResourceLocation FLOW_TEX = Omnitrix.id("textures/vfx/ascalon_flow_atlas.png");
    private static final ResourceLocation SKILL_TEX = Omnitrix.id("textures/vfx/skill_materials.png");
    private static final RenderType FALLBACK = RenderType.entityTranslucent(Omnitrix.id("textures/vfx/skill_fallback.png"));

    private static final RenderStateShard.TransparencyStateShard ADDITIVE_ALPHA = new RenderStateShard.TransparencyStateShard(
            "aca_cosmic_additive", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    /** Translucent pass: renders Celestialsapien nebulae AND Ascalon Mode-4 Abyssal Star-Void Dark Contrast Sheaths! */
    private static final RenderType NEBULA = translucent("aca_omnitrix:cosmic_nebula", FLOW_TEX);
    private static final RenderType MATERIAL = translucent("aca_omnitrix:skill_material", SKILL_TEX);

    private static RenderType translucent(String name, ResourceLocation texture) {
        return RenderType.create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 2048, false, true,
                RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> shader))
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
    }

    /** Additive pass: renders razor-sharp eroded filaments, asymmetric blade edges, astrolabes & speed streaks! */
    private static final RenderType GLOW = RenderType.create(
            "aca_omnitrix:cosmic_glow",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            2048,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> shader))
                    .setTextureState(new RenderStateShard.TextureStateShard(FLOW_TEX, true, false))
                    .setTransparencyState(ADDITIVE_ALPHA)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private CosmicVfxShader() {}

    static MultiBufferSource.BufferSource createWorldBuffers() {
        // Layered meshes interleave sheath and glow vertices. Each layer needs its own
        // storage so requesting another layer cannot finish a consumer still in use.
        var layers = new LinkedHashMap<RenderType, ByteBufferBuilder>();
        layers.put(NEBULA, new ByteBufferBuilder(64 * 1024));
        layers.put(MATERIAL, new ByteBufferBuilder(32 * 1024));
        layers.put(GLOW, new ByteBufferBuilder(128 * 1024));
        layers.put(FALLBACK, new ByteBufferBuilder(64 * 1024));
        return MultiBufferSource.immediateWithBuffers(layers, new ByteBufferBuilder(64 * 1024));
    }

    static boolean available() { return shader != null; }

    static void preloadTextures(TextureManager textures) {
        textures.getTexture(FLOW_TEX);
        textures.getTexture(SKILL_TEX);
    }

    public static RenderType nebula() {
        return shader != null ? NEBULA : FALLBACK;
    }

    public static RenderType voidSheath() {
        return nebula();
    }

    public static RenderType glow() {
        return shader != null ? GLOW : FALLBACK;
    }

    static RenderType material() { return shader != null ? MATERIAL : FALLBACK; }

    static void clock(float partial) {
        long tick = Math.floorMod(ClientState.clientTicks(), PHASE_TICKS);
        float seconds = (tick + partial) / TICKS_PER_SECOND;
        if (shader != null) {
            var timeUniform = shader.getUniform("StarfieldSeconds");
            if (timeUniform != null) timeUniform.set(seconds);
            var reducedUniform = shader.getUniform("ReducedMotion");
            if (reducedUniform != null) reducedUniform.set(ClientState.reduced() ? 1.0f : 0.0f);
        }
    }

    public static void register(RegisterShadersEvent event) {
        shader = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Omnitrix.id("cosmic_vfx"),
                    DefaultVertexFormat.NEW_ENTITY), s -> shader = s);
        } catch (IOException e) {
            com.mojang.logging.LogUtils.getLogger().warn("Using textured Omnitrix VFX fallback", e);
        }
    }
}
