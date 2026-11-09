package cn.academy.port.qa;

import cn.academy.port.client.DecorSnap;
import cn.academy.port.client.ElectroDecor;
import cn.academy.port.client.VisualDecorRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** QA-only: draws a staged electromaster-skill snapshot on the first armor stand through the production decoration. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ElectroQaFixture {
    private static ElectroDecor.Skill skill;
    private static int skillLevel, fixedAge = -1;
    private static double casterLevel = 5;
    private static Vec3 at;

    private ElectroQaFixture() {}

    /** {@code el:ground:5:6} = skill, caster level, optional fixed age (burst ticks left etc.); {@code el:off} clears it. */
    static void stage(Minecraft mc, String arg) {
        String[] f = arg.split(":");
        if (f[0].equals("off")) { skill = null; return; }
        skill = ElectroDecor.Skill.valueOf(f[0].toUpperCase());
        casterLevel = f.length > 1 ? Double.parseDouble(f[1]) : 5;
        fixedAge = f.length > 2 ? Integer.parseInt(f[2]) : -1;
        skillLevel = switch (skill) { case TETHER -> 1; case CIRCUIT, GROUND, DECOY -> 2; case LANCE, STEP, PULL -> 3; case NET, BUFFER -> 4; case ARC, BRACER -> 1; };
        at = null;
        for (var e : mc.level.entitiesForRendering()) if (e.getType() == net.minecraft.world.entity.EntityType.ARMOR_STAND) { at = e.position(); break; }
    }

    private static double[] v(double x, double y, double z) { return new double[] {at.x + x, at.y + y, at.z + z}; }
    private static double[] dir(double x, double y, double z) { return new double[] {x, y, z}; }

    private static DecorSnap snap(int age) {
        List<double[]> p = new ArrayList<>();
        switch (skill) {
            case TETHER -> { p.add(v(0, 1.3, -4)); p.add(v(8, 3, 10)); p.add(dir(0, 0, -1)); return DecorSnap.of(true, age, 1, 7, p, 1, 0); }
            case CIRCUIT -> { p.add(v(-5, .1, 2)); p.add(v(6, .1, 9)); return DecorSnap.of(true, age, 1, 7, p, 1, 60); }
            case GROUND -> { p.add(v(0, 0, 0)); return DecorSnap.of(true, age, 1, 7, p, 1, 40, age); }
            case DECOY -> { p.add(v(-3, 0, -2)); p.add(v(5, 0, 6)); return DecorSnap.of(true, age, 1, 7, p, 1, 60); }
            case LANCE -> { p.add(v(0, 0, -2)); for (int i = 0; i < 3; i++) { p.add(v(-2 + 2 * i, 1.5 + i * .3, 0)); p.add(v(-2 + 2 * i, 1.8 + i * .3, 7 + i)); } return DecorSnap.of(true, age, 1, 7, p, 1, 20, 1, 1, 2, 3); }
            case STEP -> { p.add(v(-6, 0, 0)); p.add(v(6, 0, 3)); p.add(dir(.3, .7, .4)); return DecorSnap.of(true, age, 1, 7, p, 1, 40, 1); }
            case PULL -> { p.add(v(0, 1.4, -2)); p.add(v(-5, .2, 6)); p.add(v(4, .2, 8)); p.add(v(1, .2, 11)); p.add(v(-2, .2, 3)); return DecorSnap.of(true, age, 1, 7, p, 1); }
            case NET -> { p.add(v(0, 0, -3)); p.add(v(0, 0, 6)); p.add(v(-6, 0, 8)); p.add(v(6, 0, 9)); p.add(v(-3, 0, 12)); p.add(v(4, 0, 14)); return DecorSnap.of(true, age, 1, 7, p, 1, 20, 4, 1, 1, 1, 1); }
            case ARC -> { p.add(v(0, 1.5, -5)); p.add(v(0, 1.3, 7)); return DecorSnap.of(false, age, 0, 7, p, age, 12); }
            case BRACER -> { p.add(v(0, 1.2, 0)); p.add(dir(-.8, 0, .6)); p.add(v(-1.6, 1.4, 1.6)); return DecorSnap.of(true, 0, 0, 7, p, age, 1); }
            default -> { p.add(v(0, 0, 0)); p.add(dir(0, 0, -1)); p.add(v(0, 1.5, -1.3)); return DecorSnap.of(true, age, 1, 7, p, 1); }
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (skill == null || at == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int age = fixedAge >= 0 ? fixedAge : (int) (mc.level.getGameTime() % 14);
        VisualDecorRenderer.electroFixture(event, skill, skillLevel, snap(age), casterLevel);
    }
}
