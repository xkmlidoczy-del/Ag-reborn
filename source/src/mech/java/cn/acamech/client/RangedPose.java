package cn.acamech.client;

import cn.acamech.MechEntity;
import cn.acamech.RangedWeapon;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class RangedPose {
    private static final String[] SIDES = {".L", ".R"};
    private static final Vec3 FRONT = new Vec3(0, 0, -1);

    private RangedPose() {}

    static void apply(BoxRig rig, MechEntity mech, float age) {
        var weapon = mech.chassis().ranged;
        float lock = weight(weapon, age);
        float deploy = AttackPose.ease(age / (weapon.windup * .69f)) * lock;
        float recoil = recoil(weapon, age);
        switch (weapon) {
            case PULSE -> {
                rig.offset("root", 0, 5.3f * deploy, recoil * 2.4f);
                rig.rotate("pelvis", 0, 0, -.025f * deploy);
                rig.rotate("chest", .105f * deploy - .14f * recoil, 0, 0);
                rig.rotate("head", -.15f * deploy, -.25f * mech.rangedYaw() * Mth.DEG_TO_RAD * lock, 0);
                for (String side : SIDES) {
                    float sign = side.endsWith("L") ? 1 : -1;
                    rig.rotate("thigh" + side, -.30f * deploy, 0, -.07f * sign * deploy);
                    rig.rotate("shin" + side, .46f * deploy, 0, 0);
                    rig.rotate("foot" + side, -.16f * deploy, 0, 0);
                    rig.rotate("upper_arm" + side, -.29f * deploy, 0, -.18f * sign * deploy);
                    rig.rotate("forearm" + side, -.88f * deploy, 0, .22f * sign * deploy);
                }
                emitter(rig, "pulse_emitter", mech, lock, deploy, recoil, 8f, 5.1f);
            }
            case NEEDLE -> {
                rig.offset("root", 0, 2.75f * deploy, recoil * 1.2f);
                rig.rotate("pelvis", .056f * deploy, .07f * deploy, .022f * deploy);
                rig.rotate("chest", .10f * deploy, -.26f * deploy, -.105f * deploy);
                rig.rotate("head", -.07f * deploy, .22f * deploy - .44f * mech.rangedYaw() * Mth.DEG_TO_RAD * lock, .045f * deploy);
                rig.rotate("shoulder.R", -.33f * deploy, -.11f * deploy, .06f * deploy);
                rig.rotate("upper_arm.R", -1.04f * deploy + .18f * recoil, .11f * deploy, .20f * deploy);
                rig.rotate("forearm.R", -.51f * deploy - .18f * recoil, .06f * deploy, 0);
                rig.rotate("hand.R", .31f * deploy, 0, 0);
                rig.rotate("upper_arm.L", -.52f * deploy, -.07f * deploy, -.09f * deploy);
                rig.rotate("forearm.L", -.98f * deploy, 0, .21f * deploy);
                rig.rotate("thigh.L", -.19f * deploy, 0, 0);
                rig.rotate("shin.L", .28f * deploy, 0, 0);
                rig.rotate("foot.L", -.09f * deploy, 0, 0);
                emitter(rig, "needle_emitter", mech, lock, deploy, recoil, 4f, 2.6f);
            }
            case CONVERGENCE -> {
                rig.offset("root", 0, 3.5f * deploy, recoil * 1.4f);
                rig.rotate("chest", -.075f * deploy - .092f * recoil, 0, 0);
                rig.rotate("head", .12f * deploy, -.36f * mech.rangedYaw() * Mth.DEG_TO_RAD * lock, 0);
                for (String side : SIDES) {
                    float sign = side.endsWith("L") ? 1 : -1;
                    rig.rotate("wing_root" + side, -.21f * deploy, sign * (.59f * deploy - .12f * recoil), -.26f * sign * deploy);
                    rig.rotate("wing_outer" + side, .155f * deploy, sign * (.28f * deploy + .10f * recoil), -.17f * sign * deploy);
                    for (int i = 1; i <= 5; i++) {
                        float index = i;
                        rig.rotate("feather_0" + i + side, -deploy * index * .016f, -deploy * sign * .063f * (index - 3), -.054f * index * sign * deploy);
                    }
                    rig.rotate("upper_arm" + side, -.16f * deploy, 0, -.22f * sign * deploy);
                    rig.rotate("forearm" + side, -.39f * deploy, 0, .11f * sign * deploy);
                    rig.rotate("thigh" + side, -.24f * deploy, 0, -.04f * sign * deploy);
                    rig.rotate("shin" + side, .32f * deploy, 0, 0);
                    rig.rotate("foot" + side, -.08f * deploy, 0, 0);
                    emitter(rig, "wing_emitter" + side, mech, lock, deploy, recoil, 4.6f, 3.4f);
                }
            }
        }
    }

    private static void emitter(BoxRig rig, String name, MechEntity mech, float lock, float deploy,
                                float recoil, float extension, float setback) {
        // Keep the barrel pointing toward the pilot's aim while its physical mount settles.
        float yaw = -mech.rangedYaw() * Mth.DEG_TO_RAD;
        float pitch = mech.rangedPitch() * Mth.DEG_TO_RAD;
        Vec3 desired = FRONT.xRot(pitch).yRot(-yaw);
        String sleeve = name + ".sleeve";
        Vec3 local = rig.toLocalDirection(name, desired);
        float alignedPitch = (float) Math.atan2(local.y, Math.hypot(local.x, local.z));
        float alignedYaw = (float) Math.atan2(-local.x, -local.z);
        rig.set(name, alignedPitch * lock, alignedYaw * lock, 0);
        rig.offset(sleeve, 0, 0, -extension * deploy + setback * recoil);
        rig.set(name + ".gate.L", 0, -1.49f * deploy, 0);
        rig.set(name + ".gate.R", 0, 1.49f * deploy, 0);
    }

    static float weight(RangedWeapon weapon, float age) {
        if (age < 0) return 0;
        return AttackPose.ease(age / 3f) * (1 - AttackPose.ease((age - weapon.windup - 2) / (RangedWeapon.RECOVERY - 2f)));
    }

    private static float recoil(RangedWeapon weapon, float age) {
        return age >= weapon.windup ? 1 - AttackPose.ease((age - weapon.windup) / 4.5f) : 0;
    }

    static void chargeEffects(AttackEffects.Pen pen, BoxRig rig, RangedWeapon weapon, float age) {
        float fill = Mth.clamp(age / weapon.windup, 0f, 1f);
        switch (weapon) {
            case PULSE -> charge(pen, rig, weapon, "pulse_emitter.sleeve", 10, 1.08f, fill, age, 0);
            case NEEDLE -> charge(pen, rig, weapon, "needle_emitter.sleeve", 6.3f, .47f, fill, age, 0);
            case CONVERGENCE -> {
                charge(pen, rig, weapon, "wing_emitter.L.sleeve", 7.3f, .61f, fill, age, 0);
                charge(pen, rig, weapon, "wing_emitter.R.sleeve", 7.3f, .61f, fill, age, 4);
            }
        }
    }

    private static void charge(AttackEffects.Pen pen, BoxRig rig, RangedWeapon weapon, String bone,
                               float depth, float radius, float fill, float age, float phase) {
        Vec3 at = rig.anchor(bone, 0, 0, -depth);
        Vec3 axis = at.subtract(rig.anchor(bone, 0, 0, 0)).normalize();
        if (axis.lengthSqr() < .01) axis = FRONT;
        if (age < weapon.windup) RangedEffects.charge(pen, at, axis, weapon, fill, age + phase, radius);
        else RangedEffects.discharge(pen, at, axis, weapon, age - weapon.windup, radius);
    }
}
