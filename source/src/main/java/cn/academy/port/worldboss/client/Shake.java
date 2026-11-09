package cn.academy.port.worldboss.client;

import cn.academy.port.VisualTierConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Local camera decoration. Uses ACA's existing cameraShake option; includes the original cinematic FOV impulse without changing player aim. */
public final class Shake {
    private record Source(Vec3 at, float amplitude, float radius, float start, float duration) {}
    private static final List<Source> SOURCES = new ArrayList<>();
    private static float fovPunch,punchStart=-1;

    public static void add(double x, double y, double z, float amplitude, int ticks, float radius) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(amplitude) || !Float.isFinite(radius) || ticks <= 0 || radius <= 0) return;
        SOURCES.add(new Source(new Vec3(x, y, z), Math.max(0,amplitude),
                radius, gt(), ticks));
    }

    public static float gt() {
        var mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
    }

    private static float amplitude(Vec3 camera, float now) {
        float sum = 0;
        for (Iterator<Source> it = SOURCES.iterator(); it.hasNext();) {
            Source source = it.next();
            float age = now - source.start;
            if (age > source.duration || age < -2) { it.remove(); continue; }
            if (age < 0) continue;
            float envelope = (float) Math.pow(1 - age / source.duration, 1.6);
            float distance = (float) camera.distanceTo(source.at);
            float falloff = distance >= source.radius ? 0 : (float) Math.pow(1 - distance / source.radius, 1.3);
            sum += source.amplitude * envelope * falloff;
        }
        return Math.min(sum, 1.6f) * (float) VisualTierConfig.shake();
    }

    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused() || mc.screen != null) return;
        float now = gt();
        float strength = amplitude(event.getCamera().getPosition(), now);
        if (strength < .002f) return;
        event.setPitch(event.getPitch() + strength * 2.4f * (float) Math.sin(now * 2.9f));
        event.setYaw(event.getYaw() + strength * 2 * (float) Math.sin(now * 2.1f + 1.3f));
        event.setRoll(event.getRoll() + strength * 3.2f * (float) Math.sin(now * 3.7f + 2.2f));
    }

    /** Original decaying FOV impact, used by the emergence stomp at 3.75 seconds. */
    public static void punch(float degrees) {punchStart=gt();fovPunch=degrees;}
    public static void fov(ViewportEvent.ComputeFov event) {
        if(punchStart<0)return;
        float age=gt()-punchStart;
        if(age>14||age<0){punchStart=-1;return;}
        event.setFOV(event.getFOV()+fovPunch*VisualTierConfig.shake()*Math.exp(-age*.28)*Math.cos(age*.6));
    }
    static void clear() { SOURCES.clear();punchStart=-1;fovPunch=0; }
    private Shake() {}
}
