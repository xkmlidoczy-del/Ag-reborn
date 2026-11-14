package cn.acamech.client;

import cn.acamech.MechMod.RangedEffect;
import cn.acamech.MechMod.RangedRay;
import cn.acamech.RangedWeapon;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class RangedEffects {
    private static final int LIFETIME = 20, WHITE = 0xe9ffff, AMBER = 0xffa451;
    private static final Vec3 UP = new Vec3(0, 1, 0), SIDE = new Vec3(1, 0, 0), FORWARD = new Vec3(0, 0, 1);
    private static final ArrayDeque<Flash> flashes = new ArrayDeque<>();
    private static Level activeLevel;

    private RangedEffects() {}

    public static void receive(RangedEffect effect, Level level) {
        useLevel(level);
        if (flashes.size() >= 64) flashes.removeFirst();
        flashes.addLast(new Flash(effect, level.getGameTime()));
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var game = Minecraft.getInstance();
        useLevel(game.level);
        if (game.level == null || flashes.isEmpty()) return;
        long tick = game.level.getGameTime();
        while (!flashes.isEmpty() && tick - flashes.getFirst().received >= LIFETIME) flashes.removeFirst();
        if (flashes.isEmpty()) return;
        Vec3 eye = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-eye.x, -eye.y, -eye.z);
        var batch = game.renderBuffers().bufferSource();
        var pen = new AttackEffects.Pen(pose.last(), batch.getBuffer(MechRenderTypes.STRIKE_LIGHT));
        for (var flash : flashes) {
            float age = tick - flash.received + event.getPartialTick().getGameTimeDeltaPartialTick(false);
            RangedWeapon weapon = flash.effect.weapon();
            for (var ray : flash.effect.rays()) {
                Vec3 direction = ray.end().subtract(ray.start()).normalize();
                if (weapon != RangedWeapon.PULSE) beam(pen, ray, direction, weapon, age);
                if (ray.contact() != RangedWeapon.MISS) impact(pen, ray.end(), direction, weapon, age, ray.contact() > 0);
            }
        }
        pose.popPose();
        batch.endBatch(MechRenderTypes.STRIKE_LIGHT);
    }

    private static void useLevel(Level level) {
        if (level == activeLevel) return;
        flashes.clear();
        activeLevel = level;
    }

    private static void beam(AttackEffects.Pen pen, RangedRay ray, Vec3 direction, RangedWeapon weapon, float age) {
        float life = weapon == RangedWeapon.NEEDLE ? 7.4f : 12f;
        float fade = 1 - AttackPose.ease(age / life);
        if (fade == 0) return;
        Frame axes = Frame.perpendicular(direction);
        double width = weapon == RangedWeapon.NEEDLE ? .045 : .105;
        Vec3 end = ray.end().subtract(direction.scale(.018));
        float core = fade * (weapon == RangedWeapon.NEEDLE ? .81f : .62f);
        pen.beam(ray.start(), end, axes.x, width, WHITE, core);
        pen.beam(ray.start(), end, axes.y, width, WHITE, core);
        pen.beam(ray.start(), end, axes.x, width * 3, weapon.color, fade * .45f);
        pen.beam(ray.start(), end, axes.y, width * 3, weapon.color, fade * .45f);
        if (weapon == RangedWeapon.CONVERGENCE) {
            for (int i = 1; i <= 6; i++) {
                double along = (i + (age * .20 % 1)) / 7.5;
                Vec3 a = ray.start().lerp(end, along);
                pen.ring(a, axes.x, axes.y, .26 + .18 * (1 - along), .045, 6,
                        i * .52 - age * .24, weapon.color, fade * .69f);
            }
        } else {
            for (int i = 0; i < 5; i++) {
                double along = (i * .20 + age * .112) % 1;
                Vec3 from = ray.start().lerp(end, along).add(axes.y.scale(.055));
                Vec3 to = ray.start().lerp(end, Math.min(1, along + .037)).add(axes.y.scale(.055));
                pen.beam(from, to, axes.x, .046, WHITE, fade * .55f);
            }
        }
        double collar = weapon == RangedWeapon.NEEDLE ? .19 : .31;
        pen.ring(ray.start().add(direction.scale(.42 + age * .1)), axes.x, axes.y, collar + age * .024,
                .058, 6, age * .35, weapon.color, fade * .75f);
        if (age < 5) pen.vent(ray.start().subtract(direction.scale(.16)), direction.scale(-1),
                weapon.color, age, fade * .39f);
    }

    private static void impact(AttackEffects.Pen pen, Vec3 point, Vec3 direction, RangedWeapon weapon, float age, boolean hit) {
        float t = Mth.clamp(age / LIFETIME, 0, 1);
        float fade = (1 - t) * (1 - t);
        Frame axes = Frame.perpendicular(direction);
        Vec3 center = point.subtract(direction.scale(.09));
        int tint = hit ? weapon.color : AMBER;
        double growth = 1 - (1 - t) * (1 - t);
        double reach = weapon == RangedWeapon.PULSE ? 2.85 : weapon == RangedWeapon.CONVERGENCE ? 1.65 : 1.2;
        double radius = (weapon == RangedWeapon.PULSE ? .83 : .36) + growth * reach;
        pen.ring(center, axes.x, axes.y, radius, .14 * fade + .04, 6, Math.PI / 6 + t * .65, tint, fade);
        pen.ring(center.subtract(direction.scale(.12 + growth * .2)), axes.x, axes.y, radius * .63, .072,
                6, -age * .22, WHITE, fade * .73f);
        if (weapon == RangedWeapon.PULSE && hit) {
            pen.ring(center.add(0, -Math.min(.90, .58 + age * .03), 0), SIDE, FORWARD, radius, .11, 8,
                    Math.PI / 8, tint, fade * .86f);
            pen.ring(center.add(0, -.94, 0), SIDE, FORWARD, .14 + radius * 1.32, .08, 8,
                    Math.PI / 8, tint, fade * .39f);
        }
        if (hit) {
            float displaced = 1 - AttackPose.ease((age - 1) / 6.2f);
            pen.ring(center.add(direction.scale(.18 + .46 * growth)), axes.x, axes.y, radius * 1.18,
                    .065 + .085 * displaced, 10, .16, AMBER, displaced * .75f);
            for (int i = 0; i < 7; i++) {
                double theta = (i + .5) * Math.TAU / 7;
                Vec3 edge = center.add(axes.x.scale(Math.cos(theta) * radius * .71))
                        .add(axes.y.scale(Math.sin(theta) * radius * .71));
                pen.beam(edge, edge.subtract(direction.scale(.26 + .31 * growth)),
                        axes.x, .093, i % 2 == 0 ? AMBER : WHITE, displaced * .82f);
            }
        }
        float flash = 1 - AttackPose.ease(age / 4.5f);
        pen.beam(center.add(axes.y.scale(-.46)), center.add(axes.y.scale(.46)), axes.x, .23, WHITE, flash * .88f);
        pen.beam(center.add(axes.x.scale(-.34)), center.add(axes.x.scale(.34)), axes.y, .17, tint, flash * .75f);
        int sparks = weapon == RangedWeapon.PULSE ? 18 : 11;
        for (int i = 0; i < sparks; i++) {
            double theta = i * 2.3999632;
            Vec3 outward = axes.x.scale(Math.cos(theta)).add(axes.y.scale(Math.sin(theta)));
            Vec3 tip = center.add(outward.scale(.20 + growth * (.5 + reach * (i % 4) / 5)))
                    .subtract(direction.scale(t * .85)).add(0, .19 * t - .91 * t * t, 0);
            Vec3 stem = tip.lerp(center, .29);
            pen.beam(stem, tip, direction, .038 + .035 * fade, i % 4 == 0 ? WHITE : tint, fade);
        }
    }

    static void charge(AttackEffects.Pen pen, Vec3 at, Vec3 direction, RangedWeapon weapon, float energy, float time, float barrel) {
        float gain = AttackPose.ease(energy);
        if (gain <= 0) return;
        Frame axes = Frame.perpendicular(direction);
        double radius = barrel * (.62 + .27 * (1 - energy));
        Vec3 front = at.add(direction.scale(.14));
        pen.ring(front, axes.x, axes.y, radius, .066, 6, time * .22, weapon.color, gain * .86f);
        pen.ring(front.add(direction.scale(.18)), axes.x, axes.y, radius * .74, .045, 6, -time * .33, WHITE, gain * .59f);
        if (energy > .54f) {
            double tight = radius * (.38 + .14 * Math.sin(time * .72));
            pen.ring(front.add(direction.scale(.06)), axes.x, axes.y, tight, .042, 6, time * .36, WHITE, gain * .70f);
        }
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + time * .12;
            Vec3 side = axes.x.scale(Math.cos(a)).add(axes.y.scale(Math.sin(a)));
            double distance = .19 + .8 * (1 - ((time * .082 + i * .167) % 1));
            Vec3 tip = front.add(side.scale(radius)).subtract(direction.scale(distance));
            pen.beam(tip.subtract(direction.scale(.22)), tip, side, .074, weapon.color, gain);
        }
    }

    static void discharge(AttackEffects.Pen pen, Vec3 at, Vec3 axis, RangedWeapon weapon, float age, float barrel) {
        if (age < 0 || age >= 6.5f) return;
        float fade = 1 - AttackPose.ease(age / 6.5f);
        var axes = Frame.perpendicular(axis);
        pen.ring(at.add(axis.scale(.34 + age * .09)), axes.x, axes.y, barrel * (.67 + age * .12),
                .105, 6, 0, weapon.color, fade * .93f);
        pen.vent(at.subtract(axis.scale(.42)), axis.scale(-1), weapon.color, age, fade * .66f);
    }

    record Frame(Vec3 x, Vec3 y) {
        static Frame perpendicular(Vec3 direction) {
            Vec3 x = direction.cross(UP).normalize();
            if (x.lengthSqr() < .01) x = SIDE;
            Vec3 y = x.cross(direction).normalize();
            if (y.lengthSqr() < .01) y = UP;
            return new Frame(x, y);
        }
    }

    private record Flash(RangedEffect effect, long received) {}
}
