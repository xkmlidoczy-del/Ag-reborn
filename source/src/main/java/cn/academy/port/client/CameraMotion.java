package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.VisualTierConfig;
import cn.academy.port.highpower.core.CastCameraTimeline;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Category-styled LV4/LV5 camera motion, shared by classic and new skills. Rendering only, never player aim. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class CameraMotion {
    public static final int MIN_LEVEL = CastCameraTimeline.MIN_LEVEL;
    private static final CastCameraTimeline TIMELINE = new CastCameraTimeline();
    private static Object world, player, connection;
    private CameraMotion() {}

    /** Clear on owner/world/connection replacement, death, GUI or disabled motion, including between tick and render. */
    private static boolean sync() {
        var mc = Minecraft.getInstance();
        if (world != mc.level || player != mc.player || connection != mc.getConnection()) {
            TIMELINE.clear(); world = mc.level; player = mc.player; connection = mc.getConnection();
        }
        if (mc.level == null || mc.player == null || mc.getConnection() == null || !mc.player.isAlive()
                || mc.player.isSpectator() || !AcademyClient.state.isActivated() || mc.screen != null || VisualTierConfig.shake() <= 0) {
            TIMELINE.clear(); return false;
        }
        return true;
    }
    public static void cast(String skill, boolean holds) {
        if (!sync() || skill == null) return;
        String category = AcademyClient.state.category;
        int level = SkillCatalog.find(category, skill).map(SkillCatalog.Skill::level).orElse(0);
        TIMELINE.cast(skill, category, level, VisualTierConfig.shake(), holds);
    }
    public static void release(String skill) { if (sync()) TIMELINE.release(skill); }
    public static void cancel(String skill) { TIMELINE.cancel(skill); }
    public static void clear() { TIMELINE.clear(); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (sync() && !Minecraft.getInstance().isPaused()) TIMELINE.tick();
    }
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov event) {
        if (!sync() || !event.usedConfiguredFov()) return;
        event.setFOV(event.getFOV() * TIMELINE.sample(event.getPartialTick()).fov());
    }
    @SubscribeEvent public static void angles(ViewportEvent.ComputeCameraAngles event) {
        if (!sync()) return;
        var view = TIMELINE.sample(event.getPartialTick());
        event.setPitch((float) (event.getPitch() + view.pitch()));
        event.setRoll((float) (event.getRoll() + view.roll()));
    }
}
