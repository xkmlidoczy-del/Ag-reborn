package cn.academy.port.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Opt-in, client-only material inspection of one immutable, accepted native FIRE receipt.
 * This never creates an ability context, extends a live flash, confirms damage, or sends input.
 * Enable only in an isolated test client with -Dacademy.foldedMirrorMaterialPreview=true.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class FoldedMirrorMaterialPreview {
    private static final boolean ENABLED = Boolean.getBoolean("academy.foldedMirrorMaterialPreview");
    private static final String LABEL = "STAGED IN-ENGINE MATERIAL PREVIEW";
    private static FoldedMirrorState.Frame captured;
    private static ClientLevel capturedWorld;
    private static Entity capturedPlayer;
    private static Object capturedConnection;
    private static long capturedSession;
    private static int capturedPreset;
    private static long capturedPresetRevision;
    private static boolean active;
    private static boolean baseline;
    private static int selectedAge = 1;

    private FoldedMirrorMaterialPreview() {}

    /** Called only after the production receiver has authenticated and accepted the receipt. */
    static void accepted(FoldedMirrorState.Frame receipt, boolean local) {
        if (!ENABLED || active || !local || receipt.phase() != FoldedMirrorState.FIRE || receipt.path().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        captured = receipt; // Frame defensively copies both lists; geometry records are immutable.
        capturedWorld = mc.level;
        capturedPlayer = mc.player;
        capturedConnection = mc.getConnection();
        capturedSession = AcademyClient.inputSessionEpoch();
        capturedPreset = AcademyClient.state.presets.current();
        capturedPresetRevision = AcademyClient.state.presets.revision();
        selectedAge = 1;
        baseline = false;
        // At most two plates and three path segments; log once per capture, never per tick/frame.
        // Deliberately omit receipt identity, UUID, player name and every gameplay/reward field.
        com.mojang.logging.LogUtils.getLogger().info(
                "NEW221 STAGED receipt ownerFeet={} playerPos=({},{},{}) yaw={} pitch={} plates={} path={}",
                receipt.ownerFeet(), mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot(),
                receipt.plates(), receipt.path());
    }

    static void clear() {
        captured = null;
        capturedWorld = null;
        capturedPlayer = null;
        capturedConnection = null;
        capturedSession = 0;
        capturedPreset = 0;
        capturedPresetRevision = 0;
        active = false;
        selectedAge = 1;
        baseline = false;
    }

    private static boolean available() {
        if (!ENABLED || captured == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != capturedWorld || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                || !AcademyClient.sameInputSessionEpoch(capturedSession) || !FoldedMirrorEffects.materialPreviewStateEligible()
                || AcademyClient.state.presets.current() != capturedPreset || AcademyClient.state.presets.revision() != capturedPresetRevision) {
            clear();
            return false;
        }
        if (!FoldedMirrorEffects.localEligible()) {
            active = false; // Menu/chat/focus/camera changes require a deliberate F8 after returning.
            return false;
        }
        return true;
    }

    /** A staged frame is presentation data only and is never returned to the ability controller. */
    static FoldedMirrorState.Frame activeFrame() {
        return available() && active ? captured : null;
    }

    /** Manual material phase only: live Flash.age and its six-tick lifetime are never changed. */
    static int phaseAge() { return selectedAge; }

    /** A second staged renderer, never a change to the live production material selection. */
    static boolean baselineSelected() { return activeFrame() != null && baseline; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (ENABLED) available(); // Validate even when hidden; UI suppression keeps only the immutable receipt.
    }

    @SubscribeEvent public static void key(InputEvent.Key event) {
        if (!ENABLED || event.getAction() != GLFW.GLFW_PRESS
                || event.getKey() != GLFW.GLFW_KEY_F8 && event.getKey() != GLFW.GLFW_KEY_F9 && event.getKey() != GLFW.GLFW_KEY_F10
                || !available()) return;
        if (event.getKey() == GLFW.GLFW_KEY_F8) active = !active;
        else if (active && event.getKey() == GLFW.GLFW_KEY_F9) selectedAge = (selectedAge + 1) % FoldedMirrorState.FLASH_TICKS;
        else if (active) baseline = !baseline;
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        if (activeFrame() == null) return;
        Minecraft mc = Minecraft.getInstance();
        var graphics = event.getGuiGraphics();
        String renderer = (baseline ? "STAGED BASELINE (.22)" : "STAGED CANDIDATE") + " | fixed age " + selectedAge;
        String detail = "Historical cast | no damage | F8 exit / F9 phase / F10 renderer";
        // Deliberately independent of hideGui: every F1 capture must still disclose that it is staged.
        int width = Math.max(mc.font.width(LABEL), Math.max(mc.font.width(renderer), mc.font.width(detail)));
        graphics.fill(4, 4, width + 12, 39, 0xCE101A16);
        graphics.drawString(mc.font, LABEL, 8, 8, 0xFFFFC765, true);
        graphics.drawString(mc.font, renderer, 8, 18, 0xFFE4EEE8, true);
        graphics.drawString(mc.font, detail, 8, 28, 0xFFE4EEE8, true);
    }
}
