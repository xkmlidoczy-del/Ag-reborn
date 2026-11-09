package cn.academy.port.client.vfx2;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.lwjgl.opengl.GL20;

/** Optional scoped materials. Every admission check precedes any classic replacement. */
@EventBusSubscriber(modid="academy", value=Dist.CLIENT, bus=EventBusSubscriber.Bus.MOD)
public final class RailgunVfx2Material {
    public static final ResourceLocation HALO = texture("beam_halo_strip");
    public static final ResourceLocation CORE = texture("beam_core_strip");
    public static final ResourceLocation NOISE = texture("loopable_noise");
    private static ShaderInstance shader;
    private static ShaderInstance coreShader;
    private static long shaderGeneration;
    private static volatile boolean texturesReady;
    private static volatile boolean noiseReady;
    private RailgunVfx2Material() {}
    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/vfx2/" + name + ".png");
    }
    @SubscribeEvent public static void resources(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> {
            texturesReady = false;
            noiseReady = false;
            try {
                for (var location : new ResourceLocation[]{HALO, CORE}) {
                    try (var stream = resources.getResourceOrThrow(location).open(); var image = NativeImage.read(stream)) {
                        if (image.getWidth() != 512 || image.getHeight() != 64)
                            throw new IllegalArgumentException("Railgun auxiliary strip must be 512x64: " + location);
                    }
                }
                texturesReady = true;
            } catch (Exception failure) {
                LogUtils.getLogger().warn("Railgun VFX2 textures unavailable; classic material retained", failure);
            }
            try (var stream = resources.getResourceOrThrow(NOISE).open(); var image = NativeImage.read(stream)) {
                if (image.getWidth() != 256 || image.getHeight() != 256)
                    throw new IllegalArgumentException("Railgun core noise must be 256x256: " + NOISE);
                noiseReady = true;
            } catch (Exception failure) {
                LogUtils.getLogger().warn("Railgun VFX2 core noise unavailable; classic inner material retained", failure);
            }
        });
    }
    @SubscribeEvent public static void shaders(RegisterShadersEvent event) {
        final long generation = ++shaderGeneration;
        shader = null;
        coreShader = null;
        if (Boolean.getBoolean("academy.vfx2.forceFallback")) return;
        register(event, generation, "railgun_vfx2", false);
        register(event, generation, "railgun_vfx2_core", true);
    }
    private static void register(RegisterShadersEvent event, long generation, String name, boolean innerCore) {
        try {
            var candidate = new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath("academy", name), DefaultVertexFormat.POSITION_TEX_COLOR);
            if (GL20.glGetProgrami(candidate.getId(), GL20.GL_LINK_STATUS) == 0) {
                LogUtils.getLogger().warn("Railgun VFX2 shader {} failed to link; classic retained: {}", name, GL20.glGetProgramInfoLog(candidate.getId()));
                candidate.close();
                return;
            }
            event.registerShader(candidate, value -> {
                if (generation != shaderGeneration) return;
                if (innerCore) coreShader = value;
                else shader = value;
                LogUtils.getLogger().info("Railgun VFX2 scoped material loaded: {}", name);
            });
        } catch (Exception failure) {
            LogUtils.getLogger().warn("Railgun VFX2 shader {} unavailable; classic material retained", name, failure);
        }
    }
    public static boolean available() { return shader != null && texturesReady; }
    public static boolean coreAvailable() { return available() && noiseReady && coreShader != null; }
    public static long coreGeneration() { return shaderGeneration; }
    static ShaderInstance shader() { return shader == null ? GameRenderer.getPositionTexColorShader() : shader; }
    static ShaderInstance coreShader() { return coreShader == null ? GameRenderer.getPositionTexColorShader() : coreShader; }
}
