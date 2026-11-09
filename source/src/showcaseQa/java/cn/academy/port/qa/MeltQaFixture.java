package cn.academy.port.qa;

import cn.academy.port.client.DecorSnap;
import cn.academy.port.client.MeltDecor;
import cn.academy.port.client.VisualDecorRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** QA-only: draws a staged meltdowner-skill snapshot around the first armor stand through the production decoration. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MeltQaFixture {
    private static MeltDecor.Skill skill;
    private static int skillLevel;
    private static Vec3 at;

    private MeltQaFixture() {}

    /** {@code md:sweep} stages a skill; {@code md:off} clears it. */
    static void stage(Minecraft mc, String arg) {
        if (arg.equals("off")) { skill = null; return; }
        skill = MeltDecor.Skill.valueOf(arg.toUpperCase());
        skillLevel = switch (skill) { case DUAL, PLATES, ETCH -> 1; case MIRROR, SHELL, LOCATOR -> 2; case SWEEP, STEP -> 3; case LAMINATE, CURTAIN -> 4; case RELAY -> 5; };
        at = null;
        for (var e : mc.level.entitiesForRendering()) if (e.getType() == net.minecraft.world.entity.EntityType.ARMOR_STAND) { at = e.position(); break; }
    }

    private static double[] v(Vec3 o, double x, double y, double z) { return new double[] {o.x + x, o.y + y, o.z + z}; }
    private static double[] d(double x, double y, double z) { return new double[] {x, y, z}; }

    /** Canonical snapshot layouts (shared with the regression test through the same shape). */
    static DecorSnap snap(MeltDecor.Skill skill, Vec3 o) {
        List<double[]> p = new ArrayList<>();
        switch (skill) {
            case DUAL -> { p.add(v(o, 0, 1.4, -6)); p.add(v(o, 0, 1.2, 8)); return DecorSnap.of(true, 0, 0, 7, p, 1, 20); }
            case PLATES -> { p.add(v(o, 0, 1.4, 0)); p.add(d(1, 0, 0)); p.add(v(o, 0, 1.2, 6)); p.add(v(o, 0, 1, 7)); return DecorSnap.of(true, 0, 0, 7, p, 7, 1); }
            case ETCH -> { p.add(v(o, 0, 0, -3)); for (int i = 0; i < 5; i++) p.add(v(o, -4 + 2 * i, .05, 4 + (i % 2))); return DecorSnap.of(true, 0, 0, 7, p, 3, 0, 40); }
            case MIRROR, LOCATOR, LAMINATE, RELAY -> {
                p.add(v(o, 0, 0, -6)); p.add(v(o, 3, 1.5, 3)); p.add(d(0, 0, -1));
                p.add(v(o, 0, 1.5, 0)); p.add(d(0, 0, -1)); p.add(v(o, -4, 1.5, 4)); p.add(d(1, 0, -1)); p.add(v(o, 4, 2, 8)); p.add(d(-1, 0, -1));
                List<double[]> path = List.of(v(o, 0, 1.4, -6), v(o, 0, 1.5, 0), v(o, -4, 1.5, 4), v(o, 4, 2, 8), v(o, 6, 1.2, 12));
                return new DecorSnap(true, 0, 2, 7, p, new double[] {4, 20, 30, 1}, List.of(path));
            }
            case SHELL -> { p.add(v(o, 0, 0, 0)); p.add(d(0, 0, -1)); p.add(v(o, 0, 1.6, -1.6)); return DecorSnap.of(true, 0, 0, 7, p); }
            case SWEEP -> { p.add(v(o, 0, 0, -8)); p.add(v(o, 0, 1.4, -8)); p.add(d(0, 0, 1)); p.add(d(1, 0, 0)); p.add(v(o, 2, 0, 4)); p.add(v(o, -3, 0, 6));
                return new DecorSnap(true, 0, 1, 7, p, new double[] {4, 20, -.43, .2, 0}, List.of(List.of(v(o, 0, 1.4, -8), v(o, 3, 1.2, 8)))); }
            case STEP -> { p.add(v(o, 0, 1.2, 0)); return DecorSnap.of(true, 10, 0, 7, p); }
            case CURTAIN -> { p.add(v(o, 0, 0, 0)); p.add(d(0, 0, -1)); p.add(v(o, .8, 1.8, 0)); return DecorSnap.of(true, 0, 1, 7, p, 7, 1); }
        }
        return DecorSnap.of(true, 0, 0, 7, p);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (skill == null || at == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        VisualDecorRenderer.meltFixture(event, skill, skillLevel, snap(skill, at), 5);
    }
}
