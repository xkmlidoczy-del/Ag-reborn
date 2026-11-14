package cn.acamech.client;

import cn.acamech.Chassis;
import cn.acamech.RangedWeapon;
import net.minecraft.world.phys.Vec3;

/** Hinges are separate cuboid groups; a fired weapon gets a short kick and a slower return. */
final class ArmorMotion {
    private static final String[] SIDES = {".L", ".R"};
    private static final int STEAM = 0xb3d0da, WHITE = 0xe8f9ff;

    private ArmorMotion() {}

    static void apply(BoxRig rig, Chassis mech, float strikeAge, float rangedAge, int strikeResult, int rangedResult) {
        boolean melee = strikeAge >= 0;
        if (!melee && rangedAge < 0) return;
        int contact = melee ? mech.attack.contactTick : mech.ranged.windup;
        float age = melee ? strikeAge : rangedAge;
        float time = age - contact;
        boolean fired = melee || rangedResult != RangedWeapon.ABORTED;
        int finish = melee ? mech.attack.duration : contact + RangedWeapon.RECOVERY;
        float charge = ease(age / Math.min(6f, contact - .5f)) * (1 - ease((time - 1.1f) / (melee ? 6 : 5)));
        float kick = fired ? recoil(time, mech == Chassis.ATLAS ? 8.9f : mech == Chassis.AETHER ? 5.4f : 7.3f) : 0;
        if (melee && strikeResult == 0) kick *= .44f;
        float relief = fired ? vent(time) : 0;
        float remaining = ease((finish - age) / 2.7f);
        float hatch = (.22f * charge + .92f * relief) * remaining;
        for (String side : SIDES) {
            int sign = side.equals(".L") ? 1 : -1;
            float field = charge * (mech == Chassis.AETHER ? (sign < 0 ? 1f : .31f)
                    : melee && mech == Chassis.ATLAS && sign < 0 ? .38f : 1f);
            float shutter = (field + relief * .26f) * remaining;
            switch (mech) {
                case ATLAS -> {
                    rig.set("field_shutter" + side, shutter * 1.13f, 0, 0);
                    rig.set("heat_door" + side, hatch * 1.24f, 0, 0);
                    rig.rotate("shoulder" + side, -.11f * kick, 0, -.068f * kick * sign);
                    if (melee && sign > 0) {
                        rig.rotate("forearm.L", -.37f * kick, 0, .13f * kick);
                        rig.rotate("upper_arm.L", .14f * kick, 0, 0);
                    }
                }
                case AETHER -> {
                    rig.set("field_shutter" + side, 0, sign * .99f * shutter, 0);
                    rig.set("heat_door" + side, 0, -sign * 1.34f * hatch, 0);
                    if (sign < 0 && melee) {
                        rig.rotate("forearm.R", -.19f * kick, .25f * kick, 0);
                        rig.rotate("shoulder.R", .19f * kick, .16f * kick, 0);
                    }
                }
                case SERAPH -> {
                    rig.set("field_shutter" + side, 0, 0, -sign * 1.15f * shutter);
                    rig.set("heat_door" + side, 0, sign * 1.26f * hatch, 0);
                    rig.rotate("wing_root" + side, -.17f * kick, -sign * .095f * kick, 0);
                    rig.rotate("wing_outer" + side, .20f * kick, sign * .18f * kick, 0);
                }
            }
        }
        // In a shot the emitter already moves on its own slide. Keep its aim transform intact.
        if (melee) {
            float mass = mech == Chassis.ATLAS ? 1 : mech == Chassis.AETHER ? .46f : .71f;
            rig.offset("root", 0, 1.8f * kick * mass, 2.6f * kick * mass);
            rig.rotate("chest", -.20f * kick * mass, mech == Chassis.AETHER ? .15f * kick : 0, 0);
            rig.rotate("head", .125f * kick * mass, 0, 0);
        }
    }

    static void exhaust(AttackEffects.Pen pen, BoxRig rig, Chassis mech, float strikeAge, float rangedAge, int rangedResult) {
        if (strikeAge < 0 && (rangedAge < 0 || rangedResult == RangedWeapon.ABORTED)) return;
        float dt = strikeAge >= 0 ? strikeAge - mech.attack.contactTick : rangedAge - mech.ranged.windup;
        float flow = vent(dt);
        if (flow <= 0) return;
        for (String side : SIDES) {
            String outlet = "cool_mount" + side;
            float sideSign = side.equals(".L") ? 1 : -1;
            Vec3 origin = rig.anchor(outlet, 0, 0, 2.5f);
            Vec3 direction = rig.anchor(outlet, sideSign * (mech == Chassis.ATLAS ? 1.1f : 4.5f), -3, 13)
                    .subtract(origin).normalize();
            Vec3 hot = origin.add(direction.scale(.29 + .18 * flow));
            var axes = RangedEffects.Frame.perpendicular(direction);
            double spread = mech == Chassis.ATLAS ? .34 : .21;
            float opacity = flow * (mech == Chassis.ATLAS ? .74f : .66f);
            pen.beam(origin, hot, axes.x(), spread, WHITE, opacity);
            pen.beam(origin, hot, axes.y(), spread * .82, mech.attack.color, opacity);
            for (int i = 0; i < 5; i++) {
                float t = (dt * .236f + i * .197f) % 1;
                double angle = i * 2.39996;
                Vec3 across = axes.x().scale(Math.cos(angle)).add(axes.y().scale(Math.sin(angle)));
                Vec3 start = origin.add(direction.scale(.08 + t * 1.02)).add(across.scale(t * spread));
                Vec3 end = start.add(direction.scale(.21 + t * .36)).add(across.scale(.14 + t * .2));
                pen.beam(start, end, across, spread * (.22 + t * .46), i % 3 == 1 ? mech.attack.color : STEAM,
                        flow * (1 - t) * .57f);
            }
        }
    }

    private static float vent(float t) {
        return ease((t - 1.05f) / 2.3f) * (1 - ease((t - 4) / 4.7f));
    }

    private static float recoil(float t, float length) {
        if (t < .25f || t >= length) return 0;
        return ease((t - .25f) / 1.05f) * (1 - ease((t - 1.3f) / (length - 1.3f)))
                - .16f * ease((t - length * .54f) / 1.25f) * (1 - ease((t - length * .82f) / (length * .18f)));
    }

    private static float ease(float x) { return AttackPose.ease(x); }
}
