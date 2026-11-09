package cn.academy.port.client.vfx2;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Fragment-only MD eye relief; material state and draw order remain classic. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MdNearEyeMaterial extends RenderType {
    private static final ResourceLocation WHITE = texture("port/white");
    private static final Set<ResourceLocation> GLOW_TEXTURES = Set.of(
            texture("effects/mdray/blend_in"), texture("effects/mdray/tile"),
            texture("effects/mdray/blend_out"));
    // Only the three MD boards and the white cylinder can enter this cache.
    private static final Map<ResourceLocation, Cached> TYPES = new HashMap<>();
    private static volatile Ready ready;
    private static volatile long generation;
    private static long reportedFailure = Long.MIN_VALUE;

    private record Ready(long generation, ShaderInstance glow, ShaderInstance core) {}
    private record Cached(RenderType original, RenderType replacement) {}

    private MdNearEyeMaterial(String name, DrawState state) {
        super(name, DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
                4096, false, false, state::setup, state::clear);
    }

    @SubscribeEvent
    public static void shaders(RegisterShadersEvent event) {
        long current = ++generation;
        ready = null;
        TYPES.clear();
        if (forcedFallback()) return;
        ShaderInstance glow = null, core = null;
        boolean glowRegistered = false, coreRegistered = false;
        Registration registration = null;
        try {
            glow = load(event, "md_near_eye_glow");
            core = load(event, "md_near_eye_core");
            registration = new Registration(new Ready(current, glow, core));
            Registration pending = registration;
            event.registerShader(glow, value -> pending.complete(false, value));
            glowRegistered = true;
            event.registerShader(core, value -> pending.complete(true, value));
            coreRegistered = true;
        } catch (Exception failure) {
            if (registration != null) registration.failed = true;
            ready = null;
            // The reload manager owns candidates once registration succeeds.
            if (!glowRegistered) close(glow);
            if (!coreRegistered) close(core);
            report(failure);
        }
    }

    private static ShaderInstance load(RegisterShadersEvent event, String name) throws IOException {
        ShaderInstance candidate = new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("academy", name), DefaultVertexFormat.POSITION_TEX_COLOR);
        try {
            verify(candidate);
            return candidate;
        } catch (RuntimeException failure) {
            close(candidate);
            throw failure;
        }
    }

    private static void verify(ShaderInstance shader) {
        verifyProgram(shader);
        for (String name : new String[]{"ModelViewMat", "ProjMat", "ColorModulator", "FadeEnd"}) {
            if (shader.getUniform(name) == null || GL20.glGetUniformLocation(shader.getId(), name) < 0)
                throw new IllegalStateException("MD near-eye shader lacks active uniform " + name);
        }
        if (GL20.glGetUniformLocation(shader.getId(), "Sampler0") < 0)
            throw new IllegalStateException("MD near-eye shader lacks its texture sampler");
    }

    private static void verifyProgram(ShaderInstance shader) {
        // ShaderInstance.close() deletes its GL program but retains programId.
        // A bound program awaiting deletion still passes glIsProgram, hence the
        // separate delete-status guard before touching its closed uniforms.
        if (shader == null || !GL20.glIsProgram(shader.getId())
                || GL20.glGetProgrami(shader.getId(), GL20.GL_DELETE_STATUS) != 0
                || GL20.glGetProgrami(shader.getId(), GL20.GL_LINK_STATUS) == 0)
            throw new IllegalStateException("MD near-eye shader program is no longer live and linked");
    }

    private static final class Registration {
        final Ready candidate;
        boolean glowComplete, coreComplete, failed;

        Registration(Ready candidate) { this.candidate = candidate; }

        void complete(boolean core, ShaderInstance value) {
            if (failed || candidate.generation != generation || forcedFallback()) return;
            try {
                if (value != (core ? candidate.core : candidate.glow))
                    throw new IllegalStateException("MD near-eye shader callback changed its candidate");
                verify(value);
                if (core) coreComplete = true;
                else glowComplete = true;
                if (glowComplete && coreComplete) ready = candidate;
            } catch (RuntimeException failure) {
                failed = true;
                ready = null;
                report(failure);
            }
        }
    }

    public static boolean available() {
        Ready value = ready;
        return value != null && value.generation == generation && !forcedFallback();
    }

    public static long generation() { return generation; }

    /** Never substitutes a partial shader pair or a texture outside the MD beam. */
    public static RenderType choose(ResourceLocation texture, boolean core, RenderType original) {
        if (original == null || texture == null
                || !(core ? WHITE.equals(texture) : GLOW_TEXTURES.contains(texture)) || !available())
            return original;
        Ready current = ready;
        try {
            Cached cached = TYPES.get(texture);
            if (cached == null || cached.original != original) {
                RenderType material = type(texture, core, current);
                RenderType replacement = new MdNearEyeMaterial("academy_md_near_eye_" + texture.getPath(),
                        new DrawState(original, material, current));
                cached = new Cached(original, replacement);
                TYPES.put(texture, cached);
            }
            return ready == current && available() ? cached.replacement : original;
        } catch (RuntimeException failure) {
            ready = null;
            report(failure);
            return original;
        }
    }

    private static RenderType type(ResourceLocation texture, boolean core, Ready shaders) {
        // Literal ClassicMeltdownerBeamEffects.Material state: no sorting, original
        // texture filtering, core cull, both depth writes, same output and blending.
        return create("academy_md_near_eye_" + (core ? "core_" : "glow_") + texture.getPath(),
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, false,
                CompositeState.builder().setShaderState(new ShaderStateShard(() -> core ? shaders.core : shaders.glow))
                        .setTextureState(new TextureStateShard(texture, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(core ? CULL : NO_CULL)
                        .setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                        .setWriteMaskState(COLOR_DEPTH_WRITE).setOutputState(PARTICLES_TARGET)
                        .createCompositeState(false));
    }

    /** A queued batch surviving reload still draws using its exact original type. */
    private static final class DrawState {
        final RenderType original, enhanced;
        final Ready shaders;
        RenderType active;

        DrawState(RenderType original, RenderType enhanced, Ready shaders) {
            this.original = original;
            this.enhanced = enhanced;
            this.shaders = shaders;
        }

        void setup() {
            active = original;
            if (ready == shaders && available()) {
                try {
                    // Check both at the last safe boundary: shader application
                    // happens after RenderType setup, outside this fallback.
                    verifyProgram(shaders.glow);
                    verifyProgram(shaders.core);
                    enhanced.setupRenderState();
                    active = enhanced;
                    return;
                } catch (RuntimeException failure) {
                    ready = null;
                    try { enhanced.clearRenderState(); }
                    catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                    report(failure);
                }
            }
            original.setupRenderState();
        }

        void clear() {
            RenderType previous = active;
            active = null;
            if (previous != null) previous.clearRenderState();
        }
    }

    private static boolean forcedFallback() {
        return Boolean.getBoolean("academy.vfx2.mdNearEyeForceFallback")
                || Boolean.getBoolean("academy.vfx2.forceFallback");
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png");
    }

    private static void close(ShaderInstance shader) {
        if (shader != null) try { shader.close(); }
        catch (RuntimeException failure) { report(failure); }
    }

    private static void report(Exception failure) {
        if (reportedFailure != generation) {
            reportedFailure = generation;
            LogUtils.getLogger().warn("MD near-eye material unavailable; classic retained", failure);
        }
    }
}
