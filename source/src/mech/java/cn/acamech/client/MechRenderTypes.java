package cn.acamech.client;

import cn.acamech.MechMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

final class MechRenderTypes extends RenderStateShard {
    private static ShaderInstance alloyShader;
    private static ShaderInstance haloShader;
    private static ShaderInstance plasmaShader;
    private static Object irisInstance;
    private static java.lang.reflect.Method irisShaderPackInUse;
    private static java.lang.reflect.Method irisShadowPass;
    private static boolean irisResolved;

    private static void resolveIris() {
        if (irisResolved) return;
        irisResolved = true;
        try {
            var api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            irisInstance = api.getMethod("getInstance").invoke(null);
            irisShaderPackInUse = api.getMethod("isShaderPackInUse");
            irisShadowPass = api.getMethod("isRenderingShadowPass");
        } catch (Throwable ignored) {
            irisInstance = null;
        }
    }

    static boolean shaderPackActive() {
        resolveIris();
        if (irisInstance == null || irisShaderPackInUse == null) return false;
        try { return Boolean.TRUE.equals(irisShaderPackInUse.invoke(irisInstance)); }
        catch (Throwable ignored) { return true; }
    }

    static boolean isShadowPass() {
        resolveIris();
        if (irisInstance == null || irisShadowPass == null) return false;
        try { return Boolean.TRUE.equals(irisShadowPass.invoke(irisInstance)); }
        catch (Throwable ignored) { return false; }
    }

    static final RenderType PLASMA = RenderType.create("acamech_plasma", DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, false, RenderType.CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shaderPackActive() || plasmaShader == null
                            ? net.minecraft.client.renderer.GameRenderer.getPositionTexColorShader() : plasmaShader))
                    .setTextureState(new TextureStateShard(MechMod.id("textures/fx/plasma_v045.png"), true, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));
    static final RenderType HALO = RenderType.create("acamech_halo", DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS, 2048, false, true, RenderType.CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shaderPackActive() || haloShader == null
                            ? net.minecraft.client.renderer.GameRenderer.getPositionTexColorShader() : haloShader))
                    .setTextureState(new TextureStateShard(MechMod.id("textures/fx/plasma_v045.png"), false, false))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));
    static final RenderType STRIKE_LIGHT = RenderType.create("acamech_strike_light", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES, 4096, false, false, RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private MechRenderTypes() {
        super("acamech_material", () -> {}, () -> {});
    }

    static RenderType alloy(ResourceLocation texture, ResourceLocation finish) {
        if (shaderPackActive() || alloyShader == null) return RenderType.entityCutoutNoCull(texture);
        return RenderType.create("acamech_alloy", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                4096, false, false, RenderType.CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> alloyShader))
                        .setTextureState(MultiTextureStateShard.builder()
                                .add(texture, false, false).add(finish, false, false).build())
                        .setLightmapState(LIGHTMAP)
                        .setCullState(NO_CULL)
                        .createCompositeState(true));
    }

    static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), MechMod.id("alloy"),
                    DefaultVertexFormat.NEW_ENTITY), shader -> alloyShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), MechMod.id("halo"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), shader -> haloShader = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), MechMod.id("plasma"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), shader -> plasmaShader = shader);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load ACA Mech material shaders", e);
        }
    }
}
