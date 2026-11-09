package cn.academy.port.client.vfx2;

import cn.academy.port.client.ClassicVectorFinalShader;
import com.mojang.blaze3d.shaders.Uniform;
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
import org.joml.Vector4f;
import org.lwjgl.opengl.GL20;

import java.util.List;

/** Independent optional plasma material; geometry and camera-space ownership stay with the caller. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PlasmaVfx2Material extends RenderType {
    public static final int MAX_BALLS = 16;
    public static final long FLOW_PERIOD_MILLIS = 12_000L;
    private static final ResourceLocation ORIGINAL_GLOW = ResourceLocation.fromNamespaceAndPath(
            "academy", "textures/effects/glow_circle.png");
    private static volatile long generation;
    private static volatile Ready loaded;
    private static long reportedFailure = Long.MIN_VALUE;
    private static final RenderType BODY = create("academy_vfx2_plasma_body",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, true,
            CompositeState.builder().setShaderState(new ShaderStateShard(PlasmaVfx2Material::shader))
                    .setTextureState(new TextureStateShard(ORIGINAL_GLOW, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP)
                    .setOverlayState(NO_OVERLAY).setOutputState(PARTICLES_TARGET).createCompositeState(false));

    private record Ready(long generation, ShaderInstance shader, Uniform count, Uniform alpha,
                         Uniform phase, Uniform charge, Uniform ready, Uniform launch, Uniform[] balls) {}

    private PlasmaVfx2Material() {
        super("unused", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
                4096, false, true, () -> {}, () -> {});
    }

    @SubscribeEvent public static void register(RegisterShadersEvent event) {
        final long current = ++generation;
        loaded = null;
        if (forcedFallback()) return;
        ShaderInstance candidate = null;
        boolean submitted = false;
        try {
            candidate = new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath("academy", "vfx2_plasma_body"),
                    DefaultVertexFormat.POSITION_TEX_COLOR);
            final Ready checked = validate(current, candidate);
            event.registerShader(candidate, value -> {
                if (current != generation || forcedFallback()) return;
                try {
                    if (value != checked.shader()) throw new IllegalStateException("Plasma shader candidate changed");
                    Ready accepted = validate(current, value);
                    if (current == generation && !forcedFallback()) loaded = accepted;
                } catch (RuntimeException failure) {
                    report(current, failure);
                }
            });
            submitted = true; // Registered shader lifetime belongs to the resource manager.
        } catch (Exception failure) {
            if (candidate != null && !submitted) {
                try { candidate.close(); } catch (RuntimeException closeFailure) { failure.addSuppressed(closeFailure); }
            }
            report(current, failure);
        }
    }

    private static Ready validate(long current, ShaderInstance shader) {
        if (shader == null || shader.getId() <= 0 || GL20.glGetProgrami(shader.getId(), GL20.GL_LINK_STATUS) == 0)
            throw new IllegalStateException("Plasma VFX2 shader is not linked");
        required(shader, "ModelViewMat");
        required(shader, "ProjMat");
        Uniform[] balls = new Uniform[MAX_BALLS];
        for (int i = 0; i < MAX_BALLS; i++) balls[i] = required(shader, "ball" + i);
        return new Ready(current, shader, required(shader, "ballCount"), required(shader, "alpha"),
                required(shader, "VisualPhase"), required(shader, "Charge"), required(shader, "Ready"),
                required(shader, "Launch"), balls);
    }

    private static Uniform required(ShaderInstance shader, String name) {
        Uniform uniform = shader.getUniform(name);
        if (uniform == null || GL20.glGetUniformLocation(shader.getId(), name) < 0)
            throw new IllegalStateException("Plasma VFX2 missing active uniform " + name);
        return uniform;
    }

    public static boolean available() {
        Ready current = loaded;
        return current != null && current.generation() == generation && !forcedFallback();
    }

    public static long generation() { return generation; }
    public static RenderType renderType() { return BODY; }

    /**
     * Bind only after admission, then immediately flush this body's render type. The list uses
     * ClassicPlasmaCameraSpace's coordinates; the original upload's intentional Z flip is retained.
     * A false result means the caller must use the original material before submitting any vertex.
     */
    public static boolean set(float alpha, List<Vector4f> cameraSpaceBalls, long phaseMillis,
                              float charge, float ready, float launch) {
        Ready current = loaded;
        if (current == null || current.generation() != generation || forcedFallback()) return false;
        if (cameraSpaceBalls == null || phaseMillis < 0 || !Float.isFinite(alpha)
                || !Float.isFinite(charge) || !Float.isFinite(ready) || !Float.isFinite(launch)) return false;
        try {
            int count = Math.min(MAX_BALLS, cameraSpaceBalls.size());
            // Reject malformed geometry before touching uniforms; one bad instance cannot poison others.
            for (int i = 0; i < count; i++) {
                Vector4f ball = cameraSpaceBalls.get(i);
                if (ball == null || !Float.isFinite(ball.x) || !Float.isFinite(ball.y)
                        || !Float.isFinite(ball.z) || !Float.isFinite(ball.w) || ball.w < 0F) return false;
            }
            if (current.shader().getId() <= 0) throw new IllegalStateException("Plasma VFX2 shader was closed");
            current.count().set(count);
            current.alpha().set(unit(alpha));
            // Reduce before float conversion, keeping long-running sessions finite and seamless.
            current.phase().set((phaseMillis % FLOW_PERIOD_MILLIS) / (float) FLOW_PERIOD_MILLIS);
            current.charge().set(unit(charge));
            current.ready().set(unit(ready));
            current.launch().set(unit(launch));
            for (int i = 0; i < MAX_BALLS; i++) {
                if (i < count) {
                    Vector4f ball = cameraSpaceBalls.get(i);
                    current.balls()[i].set(ball.x, ball.y, -ball.z, ball.w);
                } else current.balls()[i].set(0F, 0F, 0F, 0F);
            }
            return current == loaded && current.generation() == generation && !forcedFallback();
        } catch (RuntimeException failure) {
            if (loaded == current) loaded = null;
            report(current.generation(), failure);
            return false;
        }
    }

    private static float unit(float value) { return Math.max(0F, Math.min(1F, value)); }
    private static ShaderInstance shader() {
        Ready current = loaded;
        return current != null && current.generation() == generation && !forcedFallback()
                ? current.shader() : ClassicVectorFinalShader.get();
    }
    private static boolean forcedFallback() {
        return Boolean.getBoolean("academy.vfx2.forceFallback")
                || Boolean.getBoolean("academy.vfx2.plasmaForceFallback");
    }
    private static void report(long current, Exception failure) {
        if (reportedFailure == current) return;
        reportedFailure = current;
        LogUtils.getLogger().warn("Plasma VFX2 material unavailable; original material retained", failure);
    }
}
