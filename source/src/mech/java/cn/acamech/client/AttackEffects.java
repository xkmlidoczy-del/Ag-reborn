package cn.acamech.client;

import cn.acamech.MechAttack;
import cn.acamech.MechEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.function.DoubleConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Full-bright joint trails and depth-tested contact effects. */
final class AttackEffects {
    private static final int CORE = 0xe7fcff, AMBER = 0xffb563;
    private static final Vec3 RIGHT = new Vec3(1, 0, 0), UP = new Vec3(0, 1, 0), FRONT = new Vec3(0, 0, -1);
    private static final String[] SIDES = {".L", ".R"};

    private AttackEffects() {}

    static void motion(PoseStack stack, VertexConsumer vertices, BoxRig rig, MechAttack attack,
                       float age, DoubleConsumer samplePose) {
        var pen = new Pen(stack.last(), vertices);
        float contact = age - attack.contactTick;
        switch (attack) {
            case HYDRAULIC -> {
                Vec3 fist = rig.anchor("hand.L", 0, 3, -4);
                Vec3 axis = fist.subtract(rig.anchor("forearm.L", 0, 3, 0)).normalize();
                Vec3 across = axis.cross(UP).normalize();
                if (across.lengthSqr() < .01) across = RIGHT;
                Vec3 vertical = across.cross(axis).normalize();
                if (contact <= 0) {
                    float charge = AttackPose.ease(age / attack.contactTick);
                    pen.ring(fist, across, vertical, .70 - charge * .20, .065, 6, age * .14, attack.color, .65f * charge);
                    pen.ring(fist.add(axis.scale(.11)), across, vertical, .38 + charge * .18, .034, 6, -age * .13, CORE, .45f * charge);
                    for (int i = 0; i < 4; i++) {
                        Vec3 rail = polar(fist, across, vertical, .50, (i + .5) * Math.PI / 2);
                        pen.beam(rail.add(axis.scale(-.60)), rail.add(axis.scale(.16)), vertical, .075, attack.color, .54f * charge);
                    }
                    pen.ring(fist.add(axis.scale(-.37)), across, vertical, .46 + charge * .07,
                            .053, 6, -age * .24, attack.color, charge * .65f);
                }
                if (contact > -.5f) {
                    float fade = 1 - AttackPose.ease((contact + .5f) / 7.5f);
                    float time = Math.max(0, contact);
                    Vec3 end = fist.add(axis.scale(.55 + time * .105));
                    pen.ring(end, across, vertical, .56 + time * .115, .13, 6, 0, attack.color, fade);
                    pen.ring(end.subtract(axis.scale(.22)), across, vertical, .61 + time * .062, .081,
                            8, Math.PI / 8, CORE, fade * .6f);
                    pen.beam(fist, end, vertical, .21, CORE, fade);
                    pen.beam(fist, end, across, .25, attack.color, .68f * fade);
                    pen.vent(fist.add(axis.scale(-.75)), axis.scale(-1), attack.color, time, fade * .57f);
                }
            }
            case SWEEP, WINGS -> {
                float start = attack == MechAttack.SWEEP ? 2.25f : 6f;
                float end = attack == MechAttack.SWEEP ? 12 : 18;
                if (age < start || age > end) return;
                if (attack == MechAttack.WINGS && contact < .6f) {
                    float energize = AttackPose.ease((age - start) / 2.8f) * .86f;
                    for (String side : SIDES) {
                        int sign = side.equals(".L") ? 1 : -1;
                        for (int i : new int[]{2, 4}) {
                            Vec3 heel = rig.anchor("feather_0" + i + side, sign * 6, 0, -2);
                            Vec3 tip = rig.anchor("feather_0" + i + side, sign * 22, 0, -2);
                            pen.beam(heel, tip, FRONT, .12, attack.color, energize);
                        }
                    }
                }
                float gain = .86f * AttackPose.ease((age - start) * .7f)
                        * (1 - AttackPose.ease((age - end + 3.2f) / 3.2f));
                float history = Math.max(start, age - (attack == MechAttack.SWEEP ? 4.2f : 5.6f));
                Blade previous = null, previousRight = null;
                final int steps = 8;
                for (int i = 0; i <= steps; i++) {
                    float mix = i / (float) steps;
                    if (i != steps) samplePose.accept(Mth.lerp(mix, history, age));
                    else samplePose.accept(age);
                    float fromAlpha = i == 0 ? 0 : gain * (i - 1) / steps;
                    float toAlpha = gain * mix;
                    if (attack == MechAttack.SWEEP) {
                        var blade = wrist(rig);
                        if (previous != null) pen.trail(previous, blade, attack.color, fromAlpha, toAlpha, i % 3 != 0);
                        previous = blade;
                    } else {
                        var left = wing(rig, ".L", 1);
                        var right = wing(rig, ".R", -1);
                        if (previous != null) {
                            pen.trail(previous, left, attack.color, fromAlpha, toAlpha, i % 3 != 0);
                            pen.trail(previousRight, right, attack.color, fromAlpha, toAlpha, i % 3 != 0);
                        }
                        previous = left;
                        previousRight = right;
                    }
                }
            }
        }
    }

    private static Blade wrist(BoxRig rig) {
        Vec3 wrist = rig.anchor("hand.R", 0, 4, -1);
        Vec3 elbow = rig.anchor("forearm.R", 0, 2, 0);
        return new Blade(wrist, wrist.add(wrist.subtract(elbow).normalize().scale(.95)).add(0, .32, 0));
    }

    private static Blade wing(BoxRig rig, String side, int sign) {
        Vec3 heel = rig.anchor("feather_03" + side, sign * 9, 0, 1);
        Vec3 tip = rig.anchor("feather_03" + side, sign * 25, .5f, 1.4f);
        return new Blade(heel, tip.add(tip.subtract(heel).normalize().scale(.48)));
    }

    static void impacts(PoseStack stack, VertexConsumer vertices, MechEntity mech, float partial) {
        float age = mech.strikeImpactAge(partial);
        if (age >= MechAttack.IMPACT_LIFE) return;
        Vec3 base = mech.getPosition(partial);
        for (Vec3 point : mech.strikeImpacts()) {
            Vec3 offset = point.subtract(base);
            stack.pushPose();
            stack.translate(offset.x, offset.y, offset.z);
            stack.mulPose(Axis.YP.rotationDegrees(-mech.strikeImpactYaw()));
            contact(new Pen(stack.last(), vertices), mech.chassis().attack, age);
            stack.popPose();
        }
    }

    private static void contact(Pen pen, MechAttack attack, float age) {
        float t = Mth.clamp(age / MechAttack.IMPACT_LIFE, 0f, 1f);
        float fade = (1 - t) * (1 - t);
        double spread = 1 - (1 - t) * (1 - t);
        int color = attack.color;
        switch (attack) {
            case HYDRAULIC -> {
                pen.ring(Vec3.ZERO, RIGHT, UP, .58 + 1.0 * spread, .15 - .1 * t, 6, 0, color, fade);
                pen.ring(new Vec3(0, 0, .30 * t), RIGHT, UP, .31 + .69 * spread, .08, 6, Math.PI / 6, CORE, fade * .72f);
                pen.ring(new Vec3(0, -1.12, 0), RIGHT, FRONT, .54 + 1.56 * spread, .08, 8,
                        Math.PI / 8, color, fade * .75f);
                pen.ring(new Vec3(0, -1.07, 0), RIGHT, FRONT, .90 + 1.94 * spread, .084, 8,
                        Math.PI / 8, color, fade * .42f);
            }
            case SWEEP -> {
                var flat = new Vec3(0, .46, .89);
                pen.ring(Vec3.ZERO, RIGHT, flat, .50 + 1.18 * spread, .11, 8, .2 + age * .06, color, fade);
                pen.beam(new Vec3(-1.22, -.39, -.22), new Vec3(1.24, .49, .50), UP, .27, CORE, fade);
                pen.beam(new Vec3(-1.2, -.65, .05), new Vec3(1.19, .19, .65), UP, .14, color, fade * .7f);
                pen.ring(new Vec3(0, .13, .34), RIGHT, flat, .33 + 1.45 * spread, .05, 8, -.13,
                        CORE, fade * .44f);
            }
            case WINGS -> {
                double height = 1.01 + spread * .59;
                pen.beam(new Vec3(-1.04, -height * .67, .17), new Vec3(1.03, height, -.25), RIGHT, .34, color, fade);
                pen.beam(new Vec3(1.04, -height * .67, .23), new Vec3(-1.03, height, -.17), RIGHT, .34, color, fade);
                pen.ring(new Vec3(0, .10, .06), RIGHT, UP, .37 + 1.10 * spread, .07, 6, Math.PI / 6, color, fade * .8f);
            }
        }
        float pressure = (1 - AttackPose.ease(age / 5f));
        double size = attack == MechAttack.HYDRAULIC ? 1.13 : attack == MechAttack.SWEEP ? .70 : .93;
        pen.ring(new Vec3(0, .02, .22 + t * .64), RIGHT, UP, size * (.42 + 1.23 * spread),
                .082 + .074 * pressure, 10, .12, AMBER, pressure * .91f);
        pen.beam(new Vec3(-size * .68, .05, -.24), new Vec3(size * .65, .11, .33), UP, .34,
                CORE, pressure * .90f);
        for (int i = 0; i < 8; i++) {
            double x = Math.cos(i * 2.39996), y = Math.sin(i * 2.39996);
            Vec3 fragment = new Vec3(x * (.36 + spread * size * 1.72),
                    y * (.35 + spread * .94) - .83 * t * t, -.11 + t * (1.27 + (i % 3) * .44));
            pen.beam(fragment.add(-x * .15, -y * .07, -.23), fragment, UP, .052,
                    i % 3 == 1 ? CORE : AMBER, fade * .83f);
        }
        pen.beam(new Vec3(0, -.44, -.06), new Vec3(0, .61, .05), RIGHT, .39, CORE, fade * fade);
        pen.beam(new Vec3(-.42, .08, .02), new Vec3(.42, .08, .02), UP, .30, CORE, fade * fade);
        for (int i = 0; i < (attack == MechAttack.HYDRAULIC ? 17 : 12); i++) {
            double angle = i * 2.39996323;
            double radial = .20 + spread * (i % 3 == 0 ? 1.96 : 1.38);
            double y = Math.sin(angle) * radial + .46 * t - 1.02 * t * t;
            Vec3 tip = new Vec3(Math.cos(angle) * radial, y, (.32 + .28 * (i % 4)) * spread);
            Vec3 tail = tip.scale(.70 + (i % 4) * .034).add(0, -.035, -.12);
            pen.beam(tail, tip, Math.abs(Math.sin(angle)) > .55 ? RIGHT : UP, .058,
                    i % 4 == 0 ? AMBER : color, fade * .91f);
        }
    }

    private static Vec3 polar(Vec3 center, Vec3 x, Vec3 y, double r, double angle) {
        return center.add(x.scale(Math.cos(angle) * r)).add(y.scale(Math.sin(angle) * r));
    }

    private record Blade(Vec3 heel, Vec3 tip) {}

    record Pen(PoseStack.Pose pose, VertexConsumer out) {
        void ring(Vec3 center, Vec3 x, Vec3 y, double r, double width, int sides, double spin, int tint, float fade) {
            if (fade <= 0) return;
            for (int i = 0; i < sides; i++) {
                double a = spin + i * Math.TAU / sides;
                double b = a + Math.TAU / sides;
                double gap = i % 2 == 0 ? .031 : .091;
                band(polar(center, x, y, r, a + gap), polar(center, x, y, r, b - gap),
                        polar(center, x, y, r - width, a + gap), polar(center, x, y, r - width, b - gap),
                        argb(tint, fade * .80f), argb(tint, fade * .80f), argb(tint, 0), argb(tint, 0));
                band(polar(center, x, y, r, a + gap), polar(center, x, y, r, b - gap),
                        polar(center, x, y, r + width, a + gap), polar(center, x, y, r + width, b - gap),
                        argb(CORE, fade * .60f), argb(CORE, fade * .60f), argb(tint, 0), argb(tint, 0));
            }
        }

        void beam(Vec3 start, Vec3 end, Vec3 axis, double width, int tint, float fade) {
            if (fade <= 0) return;
            int clear = argb(tint, 0), bright = argb(tint, fade);
            for (int sign = -1; sign <= 1; sign += 2) {
                Vec3 edge = start.add(axis.scale(width * sign));
                band(start, end, edge, end.add(axis.scale(width * sign * .24)), bright, bright, clear, clear);
            }
        }

        void trail(Blade a, Blade b, int tint, float aFade, float bFade, boolean stripe) {
            Vec3 aRim = a.heel.lerp(a.tip, .88), bRim = b.heel.lerp(b.tip, .88);
            band(a.heel, b.heel, aRim, bRim, argb(tint, 0), argb(tint, 0),
                    argb(tint, aFade * .48f), argb(tint, bFade * .48f));
            band(aRim, bRim, a.tip, b.tip, argb(tint, aFade * .63f), argb(tint, bFade * .63f),
                    argb(CORE, aFade), argb(CORE, bFade));
            band(a.tip, b.tip, a.heel.lerp(a.tip, 1.12), b.heel.lerp(b.tip, 1.12),
                    argb(CORE, aFade), argb(CORE, bFade), argb(tint, 0), argb(tint, 0));
            if (stripe) {
                band(a.heel.lerp(a.tip, .57), b.heel.lerp(b.tip, .57),
                        a.heel.lerp(a.tip, .64), b.heel.lerp(b.tip, .64),
                        argb(tint, aFade * .42f), argb(tint, bFade * .42f), argb(tint, 0), argb(tint, 0));
            }
        }

        void vent(Vec3 center, Vec3 direction, int color, float age, float opacity) {
            var frame = RangedEffects.Frame.perpendicular(direction);
            for (int i = 0; i < 6; i++) {
                double angle = i * Math.PI / 3;
                Vec3 outward = frame.x().scale(Math.cos(angle)).add(frame.y().scale(Math.sin(angle)));
                Vec3 end = center.add(outward.scale(.25 + age * .105)).add(direction.scale(.12 + age * .14));
                beam(center.add(outward.scale(.20)), end, outward, .042, i % 3 == 0 ? AMBER : color, opacity);
            }
        }

        private void band(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd) {
            vertex(a, ca); vertex(b, cb); vertex(c, cc);
            vertex(c, cc); vertex(b, cb); vertex(d, cd);
        }

        private void vertex(Vec3 p, int color) {
            out.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color);
        }

        private static int argb(int color, float a) {
            return (Mth.clamp(Math.round(a * 255), 0, 255) << 24) | color;
        }
    }
}
