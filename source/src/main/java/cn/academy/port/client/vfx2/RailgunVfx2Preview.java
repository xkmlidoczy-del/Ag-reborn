package cn.academy.port.client.vfx2;

import cn.academy.port.client.AcademyClient;
import cn.academy.port.client.ClassicEffectTimeline;
import cn.academy.port.client.ClassicEffects.RailgunMaterialSample;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Explicitly staged, singleplayer-only inspection of one historical accepted beam.
 * Enable in an isolated test JVM with -Dacademy.vfx2.materialPreview=true.
 * No packets, entities, ability input, damage, new random draws, or live lifetime changes.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class RailgunVfx2Preview {
    private static final boolean ENABLED = Boolean.getBoolean("academy.vfx2.materialPreview");
    private static final long[] AGES = ENABLED ? new long[]{150, 400, 1200, 2000, 2400} : null;
    private static final String LABEL = "STAGED IN-ENGINE MATERIAL PREVIEW";
    private static Object pendingBeam;
    private static Geometry pendingGeometry;
    private static RailgunMaterialSample captured;
    private static ClientLevel capturedWorld;
    private static Entity capturedPlayer;
    private static Object capturedConnection;
    private static long capturedSession, capturedPresetRevision, observedClock;
    private static int capturedPreset;
    private static String capturedCategory;
    private static boolean active, classic;
    private static int selectedAge;

    private RailgunVfx2Preview() {}

    private record Geometry(Vec3 origin, Vec3 direction, double length, long created) {}

    /** Only the validated, same-world addRailgun callback supplies this identity. */
    public static void accepted(Object beam, Vec3 origin, Vec3 direction, double length, long created) {
        if (!ENABLED) return; // No sample, geometry record, or pending reference with the option off.
        validState();
        Minecraft mc = Minecraft.getInstance();
        if (pendingBeam != null || captured != null || beam == null || !eligible(mc)
                || !finite(origin) || !finite(direction) || !Double.isFinite(length) || length <= 0 || length > 256
                || !Double.isFinite(direction.lengthSqr()) || direction.lengthSqr() < 1.0E-12) return;
        pendingBeam = beam;
        pendingGeometry = new Geometry(origin, direction, length, created);
        capturedWorld = mc.level;
        capturedPlayer = mc.player;
        capturedConnection = mc.getConnection();
        capturedSession = AcademyClient.inputSessionEpoch();
        capturedPreset = AcademyClient.state.presets.current();
        capturedPresetRevision = AcademyClient.state.presets.revision();
        capturedCategory = AcademyClient.state.category;
        observedClock = created;
        selectedAge = 0;
        classic = false;
        active = false;
    }

    /** Called after the natural production emission, never to generate a material state. */
    public static boolean wantsSample(Object beam, long age) {
        return ENABLED && validState() && pendingBeam == beam && captured == null
                && age >= AGES[0] && age < ClassicEffectTimeline.RAILGUN_LIFETIME_MS;
    }

    public static void freeze(Object beam, RailgunMaterialSample sample) {
        if (!ENABLED || !validState() || pendingBeam != beam || sample == null) return;
        if (!sample.finite() || sample.origin() != pendingGeometry.origin || sample.direction() != pendingGeometry.direction
                || sample.length() != pendingGeometry.length || sample.created() != pendingGeometry.created) {
            clear();
            return;
        }
        captured = sample;
        pendingBeam = null;
        pendingGeometry = null;
    }

    /** Pending admission cannot survive the original pause-aware 2500 ms lifetime. */
    public static void advanceClock(long clockMillis) {
        if (!ENABLED) return;
        observedClock = clockMillis;
        if (!validState()) return;
        if (pendingGeometry != null && clockMillis - pendingGeometry.created >= ClassicEffectTimeline.RAILGUN_LIFETIME_MS) clear();
    }

    public static void clear() {
        pendingBeam = null;
        pendingGeometry = null;
        captured = null;
        capturedWorld = null;
        capturedPlayer = null;
        capturedConnection = null;
        capturedCategory = null;
        capturedSession = capturedPresetRevision = observedClock = 0;
        capturedPreset = selectedAge = 0;
        active = classic = false;
    }

    private static boolean eligible(Minecraft mc) {
        return mc.hasSingleplayerServer() && mc.level != null && mc.player != null && mc.getConnection() != null
                && mc.player.isAlive() && !mc.player.isRemoved() && !mc.player.isSpectator()
                && AcademyClient.state != null && "electromaster".equals(AcademyClient.state.category)
                && AcademyClient.sameInputSessionEpoch(AcademyClient.inputSessionEpoch());
    }

    private static boolean validState() {
        if (!ENABLED || pendingBeam == null && captured == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!eligible(mc) || mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                || !AcademyClient.sameInputSessionEpoch(capturedSession) || !capturedCategory.equals(AcademyClient.state.category)
                || AcademyClient.state.presets.current() != capturedPreset
                || AcademyClient.state.presets.revision() != capturedPresetRevision) {
            clear();
            return false;
        }
        return true;
    }

    private static boolean visibleState() {
        if (!validState() || captured == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || !mc.isWindowActive() || mc.getCameraEntity() != mc.player) {
            active = false; // Resume deliberately with F8 after a menu/focus/camera interruption.
            return false;
        }
        return true;
    }

    public static RailgunMaterialSample activeSample() {
        return ENABLED && visibleState() && active
                && observedClock - captured.created() >= ClassicEffectTimeline.RAILGUN_LIFETIME_MS ? captured : null;
    }
    public static long phaseAge() { return ENABLED ? AGES[selectedAge] : 150; }
    public static boolean classicSelected() { return classic; }
    public static boolean selectedAge(long age) {
        return age == 150 || age == 400 || age == 1200 || age == 2000 || age == 2400;
    }

    private static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) { if (ENABLED) visibleState(); }

    @SubscribeEvent public static void key(InputEvent.Key event) {
        if (!ENABLED || event.getAction() != GLFW.GLFW_PRESS
                || event.getKey() != GLFW.GLFW_KEY_F8 && event.getKey() != GLFW.GLFW_KEY_F9 && event.getKey() != GLFW.GLFW_KEY_F10
                || !visibleState()) return;
        if (event.getKey() == GLFW.GLFW_KEY_F8) active = !active;
        else if (active && event.getKey() == GLFW.GLFW_KEY_F9) selectedAge = (selectedAge + 1) % AGES.length;
        else if (active) classic = !classic;
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        if (activeSample() == null) return;
        Minecraft mc = Minecraft.getInstance();
        var graphics = event.getGuiGraphics();
        String phase = "Historical accepted beam | fixed age " + phaseAge() + " ms | "
                + (classic ? "CLASSIC" : "CLASSIC + VFX2 (" + (RailgunVfx2.maxInstances() > 0 ? "available" : "fallback") + ")");
        String detail = "NO GAMEPLAY | frozen sample | F8 exit / F9 phase / F10 material";
        // F1 must retain this disclosure: no hideGui condition is used here.
        int width = Math.max(mc.font.width(LABEL), Math.max(mc.font.width(phase), mc.font.width(detail)));
        graphics.fill(4, 4, width + 12, 40, 0xE010151B);
        graphics.drawString(mc.font, LABEL, 8, 8, 0xFFFFC765, true);
        graphics.drawString(mc.font, phase, 8, 18, 0xFFE4EEE8, true);
        graphics.drawString(mc.font, detail, 8, 28, 0xFFE4EEE8, true);
    }

    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Reloads {
        private Reloads() {}
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            if (ENABLED) event.registerReloadListener((ResourceManagerReloadListener) resources -> clear());
        }
    }
}
