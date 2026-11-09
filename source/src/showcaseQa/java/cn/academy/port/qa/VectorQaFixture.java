package cn.academy.port.qa;

import cn.academy.port.client.DecorSnap;
import cn.academy.port.client.VectorDecor;
import cn.academy.port.client.VisualDecorRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** QA-only: draws a staged vector-skill snapshot on the first armor stand through the production decoration. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class VectorQaFixture {
    private static VectorDecor.Skill skill;
    private static int skillLevel, fixedAge = -1;
    private static double casterLevel = 5;
    private static Vec3 at;

    private VectorQaFixture() {}

    /** {@code vec:strike:5:6} = skill, caster level, optional fixed animation age; {@code vec:off} clears it. */
    static void stage(Minecraft mc, String arg) {
        String[] f = arg.split(":");
        if (f[0].equals("off")) { skill = null; return; }
        skill = VectorDecor.Skill.valueOf(f[0].toUpperCase());
        casterLevel = f.length > 1 ? Double.parseDouble(f[1]) : 5;
        fixedAge = f.length > 2 ? Integer.parseInt(f[2]) : -1;
        skillLevel = switch (skill) { case STRIKE, PARRY, BRAKE -> 1; case ANCHOR, PALM -> 2; case RELIEF, LISTEN -> 3; case CAGE, REFLECT -> 4; case CIRCLE -> 5; };
        at = null;
        for (var e : mc.level.entitiesForRendering()) if (e.getType() == net.minecraft.world.entity.EntityType.ARMOR_STAND) { at = e.position(); break; }
    }

    private static double[] dir(double x, double y, double z) { return new double[] {x, y, z}; }
    private static double[] v(Vec3 base, double x, double y, double z) { return new double[] {base.x + x, base.y + y, base.z + z}; }

    private static DecorSnap snap(int age, long tick) {
        Vec3 o = at;
        List<double[]> p = new ArrayList<>();
        switch (skill) {
            case STRIKE -> { p.add(v(o, 0, 1.3, -4)); p.add(v(o, 0, 1.3, 10)); p.add(v(o, 0, 1.3, 8)); p.add(v(o, 2.5, 1.0, 10)); return DecorSnap.of(true, age, 0, 7, p, 0); }
            case PARRY -> { p.add(v(o, 0, 0, 0)); p.add(dir(0, 0, -1)); p.add(v(o, 0, 1.4, -1.1)); p.add(dir(.5, .2, -.85)); p.add(dir(-.5, .1, -.85)); return DecorSnap.of(true, age, 0, 7, p, 1, 0, age, age); }
            case BRAKE -> { p.add(v(o, 0, .05, 0)); return DecorSnap.of(true, age, 1, 7, p); }
            case ANCHOR -> { p.add(v(o, 0, 0, 0)); p.add(dir(0, 0, -1)); return DecorSnap.of(true, age, 1, 7, p, 3, 1, age, -1); }
            case PALM -> { p.add(v(o, 0, 1.3, -3)); p.add(v(o, 2, 1.3, 3)); p.add(dir(1, 0, 0)); p.add(v(o, -2.5, 1.3, 5)); p.add(dir(-1, 0, .2)); return DecorSnap.of(true, age, 0, 7, p, 0); }
            case RELIEF -> { p.add(v(o, 0, 0, 0)); p.add(dir(0, 0, -1)); p.add(v(o, 0, .05, 0)); p.add(v(o, 0, 1.2, 0)); return DecorSnap.of(true, age, 1, 7, p, 3, 1, age); }
            case LISTEN -> { p.add(v(o, 0, 0, 0)); return DecorSnap.of(true, age, 0, 7, p, 0, 4, 20, 0, 3, 0, 110, 10, 2, 1, 200, 5, 1, 0, 300, 0, 3, 1); }
            case CAGE -> { p.add(v(o, 0, 0, 0)); p.add(v(o, 5, 1.5, 1)); return DecorSnap.of(true, age, 0, 7, p, 5, tick % 20, 1, 0, 0); }
            case REFLECT -> {
                p.add(v(o, 0, 0, 0)); p.add(dir(0, 0, -1)); p.add(v(o, 1.5, 1.2, 0)); p.add(v(o, 6, 1.2, 12));
                List<double[]> trace = List.of(v(o, 1.5, 1.2, 0), v(o, 5, 1.5, 4), v(o, 2, 1.4, 8), v(o, 6, 1.2, 12));
                return new DecorSnap(true, age, 0, 7, p, new double[] {15, age}, List.of(trace));
            }
            default -> { p.add(v(o, 0, 0, 0)); p.add(dir(0, 0, -1)); p.add(v(o, 0, 3, 8)); p.add(v(o, 3, 1.4, 5)); return DecorSnap.of(true, age, 0, 7, p, 1, 3, 0, age); }
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (skill == null || at == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long tick = mc.level.getGameTime();
        int age = fixedAge >= 0 ? fixedAge : (int) (tick % 14);
        VisualDecorRenderer.vectorFixture(event, skill, skillLevel, snap(age, tick), casterLevel);
    }
}
