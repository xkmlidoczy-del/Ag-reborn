package cn.academy.port.client;

import cn.academy.port.ClassicHudConfig;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL20;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Optional RGB-only material, admitted only after validation of one resource generation. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class LightShieldVfx2Material {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/mdshield.png");
    private static final String TEXTURE_SHA256 = "5a0cf5f61bea467687820983efb76cfd14f68abbc38667e091b6b0f27965c5dc";
    static final long FLOW_PERIOD_MILLIS = 6000L;
    private static final float SWIRL_GAIN = .28F, MEDIUM_FLOW = .18F;
    private static volatile long generation, resourceGeneration;
    private static volatile Ready ready;
    private static long reportedFailure = Long.MIN_VALUE;
    private record Ready(long generation, ShaderInstance shader, Uniform gain, Uniform flow, Uniform phase) {}
    private LightShieldVfx2Material() {}

    @SubscribeEvent public static void register(RegisterShadersEvent event) {
        final long current = ++generation;
        ready = null;
        resourceGeneration = 0;
        if (forcedFallback()) return;
        ShaderInstance candidate = null;
        boolean submitted = false;
        try {
            // The shader event provider includes this reload's textures as well as cached shaders.
            // Validation here avoids depending on custom reload-listener ordering.
            byte[] bytes;
            try (var stream = event.getResourceProvider().getResourceOrThrow(TEXTURE).open()) {
                bytes = stream.readAllBytes();
            }
            if (!TEXTURE_SHA256.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))
                throw new IllegalArgumentException("Light Shield VFX2 requires the original mdshield artwork");
            try (var image = NativeImage.read(new ByteArrayInputStream(bytes))) {
                if (image.getWidth() != 512 || image.getHeight() != 512)
                    throw new IllegalArgumentException("Light Shield mdshield must be 512x512");
            }
            if (current != generation) return;
            resourceGeneration = current;
            candidate = new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath("academy", "light_shield_vfx2"), DefaultVertexFormat.POSITION_TEX_COLOR);
            final Ready validated = validate(current, candidate);
            event.registerShader(candidate, value -> {
                if (current != generation || current != resourceGeneration || forcedFallback()) return;
                try {
                    if (value != validated.shader()) throw new IllegalStateException("Light Shield shader callback changed candidate");
                    Ready loaded = validate(current, value);
                    if (current == generation && current == resourceGeneration) ready = loaded;
                } catch (RuntimeException failure) {
                    report(current, failure);
                }
            });
            submitted = true; // Resource manager owns the registered shader from here onward.
        } catch (Exception failure) {
            if (candidate != null && !submitted) candidate.close();
            report(current, failure);
        }
    }

    private static Ready validate(long current, ShaderInstance shader) {
        if (shader == null || shader.getId() <= 0 || GL20.glGetProgrami(shader.getId(), GL20.GL_LINK_STATUS) == 0)
            throw new IllegalStateException("Light Shield VFX2 shader is not linked");
        for (String name : new String[]{"ModelViewMat", "ProjMat", "ColorModulator", "SwirlGain", "FlowAmount", "VisualPhase"})
            if (shader.getUniform(name) == null || GL20.glGetUniformLocation(shader.getId(), name) < 0)
                throw new IllegalStateException("Light Shield VFX2 missing active uniform " + name);
        if (GL20.glGetUniformLocation(shader.getId(), "Sampler0") < 0)
            throw new IllegalStateException("Light Shield VFX2 missing original texture sampler");
        return new Ready(current, shader, shader.getUniform("SwirlGain"), shader.getUniform("FlowAmount"), shader.getUniform("VisualPhase"));
    }

    /** The caller immediately flushes this quad, so no queued draw can inherit another phase. */
    static boolean bind(ClassicHudConfig.Vfx2Quality quality, long phaseMillis) {
        if (quality == null || quality == ClassicHudConfig.Vfx2Quality.OFF || phaseMillis < 0 || !available()) return false;
        Ready current = ready;
        if (current == null) return false;
        try {
            Ready checked = validate(current.generation(), current.shader());
            if (checked.gain() != current.gain() || checked.flow() != current.flow() || checked.phase() != current.phase())
                throw new IllegalStateException("Light Shield VFX2 uniform generation changed");
            current.gain().set(SWIRL_GAIN);
            current.flow().set(quality == ClassicHudConfig.Vfx2Quality.MEDIUM ? MEDIUM_FLOW : 0F);
            // Modulo precedes float conversion: every nonnegative long stays finite, even near MAX_VALUE.
            current.phase().set(quality == ClassicHudConfig.Vfx2Quality.MEDIUM ? (phaseMillis % FLOW_PERIOD_MILLIS) / (float)FLOW_PERIOD_MILLIS : 0F);
            return current == ready && current.generation() == generation && current.generation() == resourceGeneration && !forcedFallback();
        } catch (RuntimeException failure) {
            if (ready == current) ready = null;
            report(current.generation(), failure);
            return false;
        }
    }

    public static boolean available() {
        Ready current = ready;
        return current != null && current.generation() == generation && current.generation() == resourceGeneration && !forcedFallback();
    }
    public static long generation() { return generation; }
    static ShaderInstance shader() {
        Ready current = ready;
        return current != null && current.generation() == generation && current.generation() == resourceGeneration && !forcedFallback()
                ? current.shader() : ClassicMeltdownerStarterEffects.CutoffShader.get();
    }
    private static boolean forcedFallback() { return Boolean.getBoolean("academy.vfx2.lightShieldForceFallback"); }
    private static void report(long current, Exception failure) {
        if (reportedFailure == current) return;
        reportedFailure = current;
        LogUtils.getLogger().warn("Light Shield VFX2 material unavailable; original material retained", failure);
    }
}
