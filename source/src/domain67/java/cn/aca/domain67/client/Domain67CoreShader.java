package cn.aca.domain67.client;

import cn.aca.domain67.AcaDomain67;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

public final class Domain67CoreShader {
    public static final int MODE_OUTER_SHELL = 0;
    public static final int MODE_INNER_DOME = 1;
    public static final int MODE_SPATIAL_RIFT = 2;
    public static final int MODE_BLACK_FLASH_LOG = 3;
    public static final int MODE_BROADCAST_RING = 4;
    public static final int MODE_SAUCE_WAVE = 5;

    private static final ResourceLocation FLOW_TEX = AcaDomain67.id("textures/vfx/domain67_flow.png");
    private static final ResourceLocation PANORAMA_TEX = AcaDomain67.id("textures/vfx/domain67_panorama.png");
    private static final ResourceLocation WHITE_TEX = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static ShaderInstance shader;

    private static final RenderType FALLBACK_TRANSLUCENT = RenderType.entityTranslucent(FLOW_TEX);

    private static final RenderStateShard.TransparencyStateShard ADDITIVE_ALPHA = new RenderStateShard.TransparencyStateShard(
            "aca_domain67_additive", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    /** Panorama Sky-Dome RenderType: writes COLOR ONLY (no depth write!) so DepthSampler still sees real floor & furniture! */
    private static final RenderType PANORAMA_DOME = RenderType.create(
            "aca_domain67:panorama_dome",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            4096,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEntityTranslucentShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(PANORAMA_TEX, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    /** Solid Architectural RenderType for 3D Restaurant 67 booths, counter, floor tiles, bowls & chandeliers! */
    private static final RenderType SOLID_ARCH = RenderType.create(
            "aca_domain67:solid_arch",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            8192,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEntityCutoutNoCullShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(WHITE_TEX, false, false))
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));

    private static final RenderType BARRIER_SHELL = RenderType.create(
            "aca_domain67:barrier_shell",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            4096,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> shader))
                    .setTextureState(new RenderStateShard.TextureStateShard(FLOW_TEX, true, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private static final RenderType ADDITIVE_GLOW = RenderType.create(
            "aca_domain67:additive_glow",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            4096,
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

    private Domain67CoreShader() {}

    public static boolean available() {
        return shader != null;
    }

    public static RenderType panoramaDome() {
        return PANORAMA_DOME;
    }

    public static RenderType solidArch() {
        return SOLID_ARCH;
    }

    public static RenderType barrierShell() {
        return shader != null ? BARRIER_SHELL : FALLBACK_TRANSLUCENT;
    }

    public static RenderType additiveGlow() {
        return shader != null ? ADDITIVE_GLOW : RenderType.lightning();
    }

    public static void updateUniforms(float partial) {
        if (shader == null) return;
        var mc = Minecraft.getInstance();
        long tick = mc.level == null ? 0L : Math.floorMod(mc.level.getGameTime(), 1920L);
        var timeUni = shader.getUniform("DomainTime");
        if (timeUni != null) timeUni.set((tick + partial) / 20.0f);
        var redUni = shader.getUniform("ReducedMotion");
        if (redUni != null) {
            float scale = mc.options.screenEffectScale().get().floatValue();
            redUni.set(scale < 0.25f ? 1.0f : 0.0f);
        }
    }

    public static void register(RegisterShadersEvent event) {
        shader = null;
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), AcaDomain67.id("domain67_core"), DefaultVertexFormat.NEW_ENTITY),
                    s -> shader = s
            );
        } catch (IOException ex) {
            LogUtils.getLogger().warn("Failed to load aca_domain67:domain67_core shader; using fallback.", ex);
        }
    }
}
