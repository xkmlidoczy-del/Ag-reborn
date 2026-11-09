package cn.academy.port.client;

import cn.academy.port.ClassicHudConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Default-off, singleplayer material inspection of one naturally rendered local shield at hold tick60.
 * No accepted ability context, defense, input, packet, sound, particle, or live timing is created.
 * Enable only in an isolated client with -Dacademy.vfx2.lightShieldMaterialPreview=true.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class LightShieldMaterialPreview {
    private static final boolean ENABLED = Boolean.getBoolean("academy.vfx2.lightShieldMaterialPreview");
    private static final String LABEL = "STAGED IN-ENGINE MATERIAL PREVIEW";
    private static Sample captured;
    private static Sample pending;
    private static List<Particle> pendingParticles;
    private static List<String> pendingCorners;
    private static int expectedParticles;
    private static boolean captureFrameAllowed, particlesVisible;
    private static ClientLevel capturedWorld;
    private static Entity capturedPlayer;
    private static Object capturedConnection;
    private static long capturedSession, capturedPresetRevision, capturedResourceGeneration;
    private static int capturedPreset;
    private static boolean active, classic;

    private LightShieldMaterialPreview() {}

    /** All fields are immutable presentation data; this is never an ability context. */
    record Sample(Vec3 position, float yaw, float pitch, float rotation,
                  ClassicMeltdownerStarterTimeline.ShieldFrame frame,
                  ClassicMeltdownerStarterTimeline.Quad quad, int ticks, long phaseMillis, List<Particle> particles) {
        Sample { particles = List.copyOf(particles); }
    }
    record CameraRotation(float x, float y, float z, float w) {
        Quaternionf quaternion() { return new Quaternionf(x, y, z, w); }
    }
    record Particle(Vec3 position, float size, double alpha, CameraRotation cameraRotation) {}

    /** Bounded snapshot admission; existing particles remain owned by the original renderer. */
    static void beginCaptureFrame(int existingParticles) {
        if (!ENABLED) return;
        pending = null;
        pendingParticles = null;
        pendingCorners = null;
        expectedParticles = existingParticles;
        captureFrameAllowed = captured == null && existingParticles >= 0 && existingParticles <= 64;
    }

    /** The renderer calls this only after emission of its shown+valid local production quad. */
    static boolean wantsSample(Entity caster, int ticks) {
        if (!ENABLED) return false;
        if (captured != null) validState();
        Minecraft mc = Minecraft.getInstance();
        return captureFrameAllowed && captured == null && pending == null && caster == mc.player && ticks == 60 && eligible(mc) && visible(mc);
    }

    static void freeze(Entity caster, int ticks, Vec3 position, float yaw, float pitch, float rotation,
                       ClassicMeltdownerStarterTimeline.ShieldFrame frame, long phaseMillis, Matrix4f emittedPose) {
        if (!wantsSample(caster, ticks) || position == null || frame == null || phaseMillis < 0
                || !Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(rotation)
                || frame.size() != ClassicMeltdownerStarterTimeline.shieldFrame(30).size()) return;
        Minecraft mc = Minecraft.getInstance();
        pending = new Sample(position, yaw, pitch, rotation, frame,
                ClassicMeltdownerStarterTimeline.billboard(frame.size(), true), ticks, phaseMillis, List.of());
        pendingParticles = new ArrayList<>(expectedParticles);
        pendingCorners = projectedCorners(pending.quad(), emittedPose);
    }

    static boolean wantsParticle() { return ENABLED && pending != null && pendingParticles != null; }

    /** Called after each existing native particle quad was emitted, never to make a new particle. */
    static void freezeParticle(Vec3 position, float size, double alpha, Quaternionf rotation) {
        if (!wantsParticle()) return;
        if (pendingParticles.size() >= 64 || position == null || rotation == null
                || !Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
                || !Float.isFinite(size) || size <= 0 || !Double.isFinite(alpha) || alpha < 0 || alpha > 1
                || !Float.isFinite(rotation.x) || !Float.isFinite(rotation.y) || !Float.isFinite(rotation.z) || !Float.isFinite(rotation.w)) {
            pending = null;
            pendingParticles = null;
            return;
        }
        pendingParticles.add(new Particle(position, size, alpha, new CameraRotation(rotation.x, rotation.y, rotation.z, rotation.w)));
    }

    /** Commit only after original shield and every original particle quad were actually written. */
    static void finishCaptureFrame() {
        if (!ENABLED || pending == null || pendingParticles == null || pendingParticles.size() != expectedParticles) return;
        Minecraft mc = Minecraft.getInstance();
        if (!eligible(mc) || !visible(mc)) return;
        captured = new Sample(pending.position(), pending.yaw(), pending.pitch(), pending.rotation(), pending.frame(),
                pending.quad(), pending.ticks(), pending.phaseMillis(), pendingParticles);
        capturedWorld = mc.level;
        capturedPlayer = mc.player;
        capturedConnection = mc.getConnection();
        capturedSession = AcademyClient.inputSessionEpoch();
        capturedPreset = AcademyClient.state.presets.current();
        capturedPresetRevision = AcademyClient.state.presets.revision();
        capturedResourceGeneration = LightShieldVfx2Material.generation();
        active = false;
        classic = true;
        particlesVisible = true;
        LogUtils.getLogger().info("LIGHT_SHIELD STAGED composition capture position={} yaw={} pitch={} rotation={} frame={} holdTick={} frozenClockMs={} projectedNdcCorners={} historicalParticles={}",
                captured.position(), captured.yaw(), captured.pitch(), captured.rotation(), captured.frame(), captured.ticks(),
                captured.phaseMillis(), pendingCorners, captured.particles());
        pending = null;
        pendingParticles = null;
        pendingCorners = null;
        captureFrameAllowed = false;
    }

    /** Diagnostic projection from the same CPU pose and shader matrices used by this draw. */
    private static List<String> projectedCorners(ClassicMeltdownerStarterTimeline.Quad quad, Matrix4f emittedPose) {
        if (emittedPose == null) return List.of("unavailable");
        Matrix4f transform = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(emittedPose);
        List<String> result = new ArrayList<>(4);
        for (var vertex : List.of(quad.a(), quad.b(), quad.c(), quad.d())) {
            var point = vertex.point();
            Vector4f clip = new Vector4f((float)point.x(), (float)point.y(), (float)point.z(), 1).mul(transform);
            if (!Float.isFinite(clip.x) || !Float.isFinite(clip.y) || !Float.isFinite(clip.z) || !Float.isFinite(clip.w) || Math.abs(clip.w) < 1E-7F) result.add("unavailable");
            else result.add("(" + clip.x / clip.w + "," + clip.y / clip.w + "," + clip.z / clip.w + ")");
        }
        return List.copyOf(result);
    }

    static void clear() {
        captured = pending = null;
        pendingParticles = null;
        pendingCorners = null;
        expectedParticles = 0;
        captureFrameAllowed = particlesVisible = false;
        capturedWorld = null;
        capturedPlayer = null;
        capturedConnection = null;
        capturedSession = capturedPresetRevision = capturedResourceGeneration = 0;
        capturedPreset = 0;
        active = classic = false;
    }

    private static boolean eligible(Minecraft mc) {
        return mc.hasSingleplayerServer() && mc.level != null && mc.player != null && mc.getConnection() != null
                && mc.player.isAlive() && !mc.player.isRemoved() && !mc.player.isSpectator()
                && AcademyClient.state != null && "meltdowner".equals(AcademyClient.state.category);
    }

    private static boolean validState() {
        if (!ENABLED || captured == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!eligible(mc) || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                || !AcademyClient.sameInputSessionEpoch(capturedSession)
                || AcademyClient.state.presets.current() != capturedPreset
                || AcademyClient.state.presets.revision() != capturedPresetRevision
                || LightShieldVfx2Material.generation() != capturedResourceGeneration) {
            clear();
            return false;
        }
        return true;
    }

    private static boolean visible(Minecraft mc) {
        return mc.screen == null && mc.isWindowActive() && mc.getCameraEntity() == mc.player;
    }

    private static boolean visibleState() {
        if (!validState()) return false;
        if (!visible(Minecraft.getInstance()) || ClassicMeltdownerStarterEffects.lightShieldMaterialPreviewHoldActive()) {
            active = false; // Return deliberately with F8 after menus, focus changes, or another hold.
            return false;
        }
        return true;
    }

    static Sample activeSample() { return visibleState() && active ? captured : null; }
    static boolean particlesSelected() { return particlesVisible; }

    /** Uses the exact production LOW binder/render type; the live client quality is never changed. */
    static RenderType materialOrClassic(RenderType original) {
        if (classic || captured == null) return original;
        try {
            if (LightShieldVfx2Material.bind(ClassicHudConfig.Vfx2Quality.LOW, captured.phaseMillis())
                    && LightShieldVfx2Material.available()) return LightShieldVfx2RenderTypes.SHIELD;
        } catch (RuntimeException failure) {
            return original;
        }
        return original;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) { if (ENABLED) visibleState(); }

    @SubscribeEvent public static void key(InputEvent.Key event) {
        if (!ENABLED || event.getAction() != GLFW.GLFW_PRESS
                || event.getKey() != GLFW.GLFW_KEY_F8 && event.getKey() != GLFW.GLFW_KEY_F9 && event.getKey() != GLFW.GLFW_KEY_F10
                || !visibleState()) return;
        if (event.getKey() == GLFW.GLFW_KEY_F8) active = !active;
        else if (active && event.getKey() == GLFW.GLFW_KEY_F9) particlesVisible = !particlesVisible;
        else if (active) classic = !classic;
    }

    @SubscribeEvent(priority = EventPriority.LOW) public static void render(RenderLevelStageEvent event) {
        if (!ENABLED || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Sample sample = activeSample();
        if (sample != null) ClassicMeltdownerStarterEffects.renderLightShieldMaterialPreview(sample,
                event.getPoseStack(), event.getCamera().getPosition());
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        Sample sample = activeSample();
        if (sample == null) return;
        Minecraft mc = Minecraft.getInstance();
        var graphics = event.getGuiGraphics();
        String material = (classic ? "OFF original" : "LOW candidate" + (LightShieldVfx2Material.available() ? "" : " (fallback)"))
                + " | frozen LOW (no flow) | clock " + sample.phaseMillis() + " ms | hold tick " + sample.ticks();
        String detail = "Historical particles " + (particlesVisible ? "ON" : "OFF") + " (" + sample.particles().size() + ") | NO DEFENSE | F8 exit / F9 particles / F10 material";
        // F1 must retain disclosure that this historical geometry has no defensive effect.
        int width = Math.max(mc.font.width(LABEL), Math.max(mc.font.width(material), mc.font.width(detail)));
        graphics.fill(4, 4, width + 12, 39, 0xE0101A16);
        graphics.drawString(mc.font, LABEL, 8, 8, 0xFFFFC765, true);
        graphics.drawString(mc.font, material, 8, 18, 0xFFE4EEE8, true);
        graphics.drawString(mc.font, detail, 8, 28, 0xFFE4EEE8, true);
    }
}
