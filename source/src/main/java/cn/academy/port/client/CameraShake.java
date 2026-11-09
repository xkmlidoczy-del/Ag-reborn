package cn.academy.port.client;

import cn.academy.port.VisualTierConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Short decaying camera shake for strong skills. View-only: it perturbs the rendered camera angles and never the player's
 * aim, movement, hits or any server state; the strength is scaled by the {@code cameraShake} setting (0 disables it).
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class CameraShake {
    private static final double MAX_DEGREES = 1.6;
    private static double strength;
    private static int ticks, total, seed;

    private CameraShake() {}

    /** Starts (or strengthens) a shake; {@code power} is 0..1 and {@code duration} in ticks (capped). */
    public static void impulse(double power, int duration) {
        double scale = VisualTierConfig.shake();
        if (scale <= 0 || !(power > 0) || !Double.isFinite(power) || duration <= 0) return;
        double next = Math.min(1, power) * scale;
        if (next >= strength * remaining()) { strength = next; total = Math.min(duration, 24); ticks = total; seed++; }
    }

    private static double remaining() { return total <= 0 ? 0 : Math.max(0, (double) ticks / total); }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (ticks > 0 && !net.minecraft.client.Minecraft.getInstance().isPaused()) ticks--;
        if (ticks == 0) strength = 0;
    }

    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event) {
        if (strength <= 0 || ticks <= 0) return;
        double decay = Math.pow(remaining(), 1.5), t = (total - ticks + event.getPartialTick()) * 1.7 + seed * 13.7;
        double amp = MAX_DEGREES * strength * decay;
        event.setYaw((float) (event.getYaw() + Math.sin(t * 2.3) * amp * .6));
        event.setPitch((float) (event.getPitch() + Math.sin(t * 3.1 + 1.3) * amp));
        event.setRoll((float) (event.getRoll() + Math.sin(t * 1.9 + .4) * amp * .8));
    }
}
