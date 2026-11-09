package cn.academy.port.qa;

import cn.academy.port.client.DecorSnap;
import cn.academy.port.client.TeleportDecor;
import cn.academy.port.client.VisualDecorRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** QA-only: draws a staged teleporter-skill snapshot around the first armor stand through the production decoration. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class TeleportQaFixture {
    private static TeleportDecor.Skill skill;
    private static int skillLevel, phase, fixedAge = -1;
    private static Vec3 at;

    private TeleportQaFixture() {}

    /** {@code tp:coordinate:1:6} = skill, phase, optional fixed age; {@code tp:off} clears it. */
    static void stage(Minecraft mc, String arg) {
        String[] f = arg.split(":");
        if (f[0].equals("off")) { skill = null; return; }
        skill = TeleportDecor.Skill.valueOf(f[0].toUpperCase());
        phase = f.length > 1 ? Integer.parseInt(f[1]) : 1;
        fixedAge = f.length > 2 ? Integer.parseInt(f[2]) : -1;
        skillLevel = switch (skill) { case PROJECTION, SIDESTEP, PROBE -> 1; case SUBSTITUTE, RETURN, RECOVERY -> 2; case INTERCEPT, CORNER -> 3; case PINCER, EMERGENCY, RELAY -> 4; case COORDINATE -> 5; };
        at = null;
        for (var e : mc.level.entitiesForRendering()) if (e.getType() == net.minecraft.world.entity.EntityType.ARMOR_STAND) { at = e.position(); break; }
    }

    private static double[] v(double x, double y, double z) { return new double[] {at.x + x, at.y + y, at.z + z}; }

    static DecorSnap snap(TeleportDecor.Skill skill, int phase, int age, Vec3 base) {
        at = base;
        List<double[]> p = new ArrayList<>();
        switch (skill) {
            case PROJECTION -> { p.add(v(0, 0, -5)); p.add(v(0, 0, 5)); p.add(v(0, 0, 6)); }
            case SIDESTEP -> { p.add(v(0, 0, -3)); p.add(v(0, 0, 3)); }
            case PROBE -> { p.add(v(0, 6, 0)); p.add(v(0, 0, 0)); return DecorSnap.of(true, age, phase, 7, p, phase == 2 ? 0 : 1); }
            case SUBSTITUTE -> { p.add(v(0, 0, -5)); p.add(v(0, 0, 5)); p.add(v(0, 0, -5)); p.add(v(0, 0, 5)); p.add(v(0, 0, 5)); return DecorSnap.of(true, age, phase, 7, p, 2, 40); }
            case RETURN, EMERGENCY -> { p.add(v(0, 0, -6)); p.add(v(0, 0, 4)); return DecorSnap.of(true, age, phase, 7, p, 120); }
            case RECOVERY -> { p.add(v(0, 0, -6)); p.add(v(0, 0, 3)); p.add(v(-3, 0, -2)); p.add(v(3, 0, -3)); return DecorSnap.of(true, age, phase, 7, p, 120); }
            case INTERCEPT -> { p.add(v(0, 1, 8)); p.add(v(0, 1.2, -4)); p.add(v(0, 1.2, 4)); }
            case CORNER -> { p.add(v(0, 0, -5)); p.add(v(0, 0, 5)); p.add(v(-2, 1, -3)); p.add(v(2, 1.5, 4)); return DecorSnap.of(true, age, phase, 7, p, 60, 0); }
            case PINCER -> { p.add(v(0, 1, -6)); p.add(v(0, 1, 6)); p.add(v(0, 4, 0)); p.add(v(0, 0, -6)); p.add(v(0, 0, 0)); p.add(v(0, 1, 0)); p.add(v(0, 1, 2)); return DecorSnap.of(true, age, phase, 7, p, 3, 60); }
            case RELAY -> { p.add(v(0, 0, -6)); p.add(v(0, 0, 0)); p.add(v(0, 0, 6)); p.add(v(0, 1, -6)); p.add(v(0, 1, 0)); p.add(v(0, 1, 0)); p.add(v(0, 1, 6)); return DecorSnap.of(true, age, phase, 7, p, 2, 1, 0); }
            case COORDINATE -> { p.add(v(0, 0, -8)); p.add(v(0, 0, 3)); }
        }
        return DecorSnap.of(true, age, phase, 7, p, 1);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (skill == null || at == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int age = fixedAge >= 0 ? fixedAge : (int) (mc.level.getGameTime() % TeleportDecor.TAIL_TICKS);
        VisualDecorRenderer.teleportFixture(event, skill, skillLevel, snap(skill, phase, age, at), 5);
    }
}
