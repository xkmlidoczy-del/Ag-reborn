package cn.aca.infinity.client;

import cn.aca.infinity.AcaInfinity;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.GameRenderer;
import java.lang.invoke.MethodHandles;
import java.util.function.BooleanSupplier;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

public final class InfinityCosmicShader {
    public static final int MODE_DARK_SHEATH = 0;
    public static final int MODE_SILK_FILAMENT = 1;
    public static final int MODE_ARMILLARY_RING = 2;
    public static final int MODE_CRESCENT_ARC = 3;
    public static final int MODE_VORONOI_SHATTER = 4;
    public static final int MODE_FRESNEL_SHELL = 5;

    private static final int PHASE_TICKS = 96 * 20;
    private static final ResourceLocation FLOW_TEX = AcaInfinity.id("textures/vfx/cosmic_flow.png");
    private static ShaderInstance shader;

    private static final BooleanSupplier PACK = shaderPackQuery();
    private static boolean plainGeometry;
    private static final RenderType PACK_GLOW = RenderType.create("aca_infinity:pack_glow",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET).createCompositeState(false));

    private static BooleanSupplier shaderPackQuery() {
        try {
            var api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object iris = api.getMethod("getInstance").invoke(null);
            var query = MethodHandles.publicLookup().unreflect(api.getMethod("isShaderPackInUse")).bindTo(iris);
            return () -> {
                try { return (boolean) query.invokeExact(); }
                catch (Throwable unavailable) { return true; }
            };
        } catch (ClassNotFoundException absent) {
            return () -> false;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return () -> true;
        }
    }

    static boolean shaderPackActive() {
        return PACK.getAsBoolean();
    }

    private static final RenderStateShard.TransparencyStateShard ADDITIVE_ALPHA = new RenderStateShard.TransparencyStateShard(
            "aca_infinity_additive", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    private static final RenderType VOID_SHEATH = RenderType.create(
            "aca_infinity:void_sheath",
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
            "aca_infinity:cosmic_glow",
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

    private InfinityCosmicShader() {}

    public static boolean available() {
        return shader != null && !plainGeometry;
    }

    public static RenderType voidSheath() {
        return available() ? VOID_SHEATH : PACK_GLOW;
    }

    public static RenderType additiveGlow() {
        return available() ? ADDITIVE_GLOW : PACK_GLOW;
    }

    public static void updateUniforms(float partial) {
        plainGeometry = shaderPackActive();
        if (!available()) return;
        var mc = Minecraft.getInstance();
        long tick = mc.level == null ? 0L : Math.floorMod(mc.level.getGameTime(), PHASE_TICKS);
        float seconds = (tick + partial) / 20.0f;
        var timeUni = shader.getUniform("CosmicTime");
        if (timeUni != null) timeUni.set(seconds);
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
                    new ShaderInstance(event.getResourceProvider(), AcaInfinity.id("infinity_cosmic"), DefaultVertexFormat.NEW_ENTITY),
                    s -> shader = s
            );
        } catch (IOException ex) {
            LogUtils.getLogger().warn("Failed to load aca_infinity:infinity_cosmic shader, using fallback", ex);
        }
    }
}
