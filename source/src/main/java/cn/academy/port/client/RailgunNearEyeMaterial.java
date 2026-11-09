package cn.academy.port.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Original scoped world state and textures, with optional fragment-only near-eye alpha. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class RailgunNearEyeMaterial extends RenderType {
    private static final Map<ResourceLocation, RenderType> GLOW = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> CORE = new HashMap<>();
    private static ShaderInstance glowShader, coreShader;
    private static long generation, reportedFailure = Long.MIN_VALUE;

    private RailgunNearEyeMaterial() {
        super("unused", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
                4096, false, true, () -> {}, () -> {});
    }

    @SubscribeEvent public static void shaders(RegisterShadersEvent event) {
        long current = ++generation;
        glowShader = coreShader = null;
        if (Boolean.getBoolean("academy.vfx2.forceFallback")
                || Boolean.getBoolean("academy.railgun.nearEye.forceFallback")) return;
        register(event, current, false);
        register(event, current, true);
    }

    private static void register(RegisterShadersEvent event, long current, boolean core) {
        String name = "railgun_near_eye_" + (core ? "core" : "glow");
        try {
            ShaderInstance candidate = new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath("academy", name), DefaultVertexFormat.POSITION_TEX_COLOR);
            if (GL20.glGetProgrami(candidate.getId(), GL20.GL_LINK_STATUS) == 0) {
                candidate.close();
                LogUtils.getLogger().warn("Railgun near-eye shader {} failed to link; classic retained", name);
                return;
            }
            if (candidate.getUniform("FadeEnd") == null || GL20.glGetUniformLocation(candidate.getId(), "FadeEnd") < 0) {
                candidate.close();
                LogUtils.getLogger().warn("Railgun near-eye shader {} lacks its depth range; classic retained", name);
                return;
            }
            event.registerShader(candidate, value -> {
                if (current != generation) return;
                if (core) coreShader = value;
                else glowShader = value;
            });
        } catch (Exception failure) {
            LogUtils.getLogger().warn("Railgun near-eye shader {} unavailable; classic retained", name, failure);
        }
    }

    public static boolean available() { return glowShader != null && coreShader != null; }

    /** Acquire before replacing anything; callers acquire their classic buffer on null. */
    static VertexConsumer prepare(ResourceLocation texture, boolean core,
                                  MultiBufferSource buffers, Set<RenderType> used) {
        if (!available()) return null;
        long current = generation;
        try {
            RenderType type = (core ? CORE : GLOW).computeIfAbsent(texture, key -> type(key, core));
            VertexConsumer target = buffers.getBuffer(type);
            if (current != generation || !available()) return null;
            used.add(type);
            return target;
        } catch (RuntimeException failure) {
            if (reportedFailure != generation) {
                reportedFailure = generation;
                LogUtils.getLogger().warn("Railgun near-eye buffer unavailable; classic retained", failure);
            }
            return null;
        }
    }

    private static ShaderInstance shader(boolean core) {
        ShaderInstance value = core ? coreShader : glowShader;
        return available() ? value : GameRenderer.getPositionTexColorShader();
    }

    private static RenderType type(ResourceLocation texture, boolean core) {
        return create("academy_railgun_near_eye_" + (core ? "core_" : "glow_") + texture.getPath(),
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, true,
                CompositeState.builder().setShaderState(new ShaderStateShard(() -> shader(core)))
                        .setTextureState(new TextureStateShard(texture, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                        .setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                        .setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET)
                        .createCompositeState(false));
    }
}
