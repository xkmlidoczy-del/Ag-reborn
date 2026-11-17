package cn.acamech.client;

import cn.acamech.BootTerrain;
import cn.acamech.Chassis;
import cn.acamech.MechEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Drives the pelvis and upper body, then solves the hips, knees and ankles against the planted soles. */
final class LocomotionPose {
    private final RunPose style;
    private final Leg left, right;

    LocomotionPose(BoxRig rig, Chassis chassis) {
        left = new Leg(rig, ".L", 1);
        right = new Leg(rig, ".R", -1);
        // ATLAS drives from the hips. AETHER leads with its elbows and SERAPH uses its wing roots for balance.
        style = switch (chassis) {
            case ATLAS -> new RunPose(1.24f, 5.4f, .18f, .148f, .079f, .67f, .22f, .52f, .26f);
            case AETHER -> new RunPose(.82f, 3.5f, .31f, .085f, .052f, .68f, .67f, .82f, .38f);
            case SERAPH -> new RunPose(1.02f, 4.4f, .20f, .12f, .096f, .52f, .37f, .65f, .31f);
        };
    }

    void apply(BoxRig rig, MechEntity mech, float partial, float gait, float brace, float flight, float sprint, float attack) {
        float cycle = mech.phase(partial);
        float turn = mech.turning(partial);
        float load = mech.driveLoad(partial) * (1 - flight);
        float run = mech.run(partial);
        float moving = gait * (1 - flight);
        float braking = mech.braking(partial);
        float landing = mech.landing(partial);
        float incline = mech.incline(partial) * (1 - attack) * (1 - flight);
        float downhill = mech.descentLoad(partial) * (1 - attack) * (1 - flight);
        float unload = downhill * (style.mass + .13f);
        float t = cycle * (float) Math.TAU;
        float plantPhase = Mth.frac(2 * cycle);
        float impact = (1 - AttackPose.ease(plantPhase / .39f)) * moving;
        float transfer = Mth.sin(t) * moving;
        float hips = Mth.cos(t - .23f) * moving * (.42f + .58f * run);

        float compression = moving * (style.suspension * (.51f + .49f * run) + impact * style.mass * 2.15f);
        compression += landing * 7.0f * style.mass + Math.abs(load) * (1 - .64f * moving) * 2.6f * style.mass;
        compression += braking * style.mass * 4.4f + brace * 2f + unload * 5.1f;
        float pitch = moving * (.067f + run * style.lean) + load * .12f + braking * .23f * style.mass;
        pitch += landing * .21f * style.mass + brace * .081f - flight * .16f + sprint * .06f;
        pitch += Math.max(0, incline) * .15f - Math.max(0, -incline) * .12f - downhill * .094f;
        float lateral = transfer * style.hipShift * 16 * (.42f + .58f * run) - turn * run * .86f;
        rig.offset("root", lateral, 0, -load * 1.05f - braking * 2.25f);
        rig.set("pelvis", load * .055f + braking * .08f + incline * .092f, -turn * (.095f + run * .072f) + hips * style.hipYaw,
                -transfer * (.054f + style.hipShift * run) + turn * .09f * moving);
        rig.set("chest", pitch + impact * .061f, turn * .225f - hips * (style.hipYaw + .12f),
                turn * (.07f + run * .34f) + transfer * .095f);
        rig.set("head", -.60f * pitch - .045f * impact, turn * .13f + hips * .091f, -turn * .17f - transfer * .075f);

        float planted = mech.footing(partial) * (1 - attack) * (1 - flight * .84f);
        BootTerrain terrain = planted > 0 && attack == 0 ? new BootTerrain(mech) : null;
        PoseTarget leftTarget = left.target(mech, partial, planted, terrain);
        PoseTarget rightTarget = right.target(mech, partial, planted, terrain);
        Vec3 adjustment = left.shiftTowardBoot(rig, leftTarget).add(right.shiftTowardBoot(rig, rightTarget));
        if (adjustment.horizontalDistanceSqr() > 1e-6) {
            double shift = Math.min(adjustment.horizontalDistance(), .54) * 16 * planted;
            Vec3 direction = adjustment.normalize().scale(shift);
            rig.offset("root", (float) direction.x, 0, (float) direction.z);
        }
        HeightRange leftHeight = left.hipRange(rig, leftTarget);
        HeightRange rightHeight = right.hipRange(rig, rightTarget);
        float minDrop = Math.max(leftHeight.min, rightHeight.min);
        float maxDrop = Math.min(leftHeight.max, rightHeight.max);
        // Keep both legs in reach when the body passes the stair nose before the rear boot leaves its tread.
        float clearance = Mth.clamp(compression, minDrop, Math.max(minDrop, maxDrop));
        rig.offset("root", 0, Mth.lerp(planted, compression, clearance), 0);
        // A horizontal and a vertical reach check in isolation cannot handle a long rear
        // stance on a step. Project the pelvis into both legs' actual 3-D reach shells
        // before solving the joints. The inner bound also prevents over-folding the
        // planted leg while the other boot lifts above a stair nose.
        Vec3 leftHip = rig.anchor(left.thigh, 0, 0, 0);
        Vec3 rightHip = rig.anchor(right.thigh, 0, 0, 0);
        Vec3 correction = Vec3.ZERO;
        for (int i = 0; i < 24; i++) {
            Vec3 before = correction;
            if (leftTarget != null && rightTarget != null && leftTarget.supported && !rightTarget.supported) {
                correction = right.reachCorrection(rightHip, rightTarget, correction);
                correction = left.reachCorrection(leftHip, leftTarget, correction);
            } else {
                correction = left.reachCorrection(leftHip, leftTarget, correction);
                correction = right.reachCorrection(rightHip, rightTarget, correction);
            }
            if (correction.distanceToSqr(before) < 1e-12) break;
        }
        rig.offset("root", (float) (correction.x * 16 * planted),
                (float) (correction.y * 16 * planted), (float) (correction.z * 16 * planted));
        left.pose(rig, leftTarget, planted, brace, flight, landing);
        right.pose(rig, rightTarget, planted, brace, flight, landing);

        float direction = mech.walkingSign();
        float armPower = Math.max(moving, Math.abs(turn) * .42f);
        left.arm(rig, t, armPower, direction, turn, run, braking, flight, brace, landing);
        right.arm(rig, t + (float) Math.PI, armPower, direction, turn, run, braking, flight, brace, landing);
        if (mech.chassis() == Chassis.SERAPH) wings(rig, turn, flight, run, braking + downhill * .63f, landing, t, moving);
    }

    private void wings(BoxRig rig, float turn, float flight, float run, float braking, float landing, float t, float weight) {
        for (String side : new String[]{".L", ".R"}) {
            int sign = side.equals(".L") ? 1 : -1;
            float counter = (Mth.sin(t - .31f) * .106f + Mth.cos(2 * t) * .049f * run) * sign * weight;
            float banking = turn * (.14f + .34f * run);
            float flare = braking * .29f + landing * .23f;
            rig.set("wing_root" + side, -flight * .18f - counter + landing * .14f,
                    sign * (.38f - .57f * flight + .22f * run) - banking,
                    sign * (-.15f + .44f * flight + flare) - banking * .52f - counter);
            rig.set("wing_outer" + side, counter * .55f, sign * (.14f - .41f * flight + .09f * run),
                    sign * (flight * .10f - flare * .76f) + banking * .4f);
            for (int i = 1; i <= 5; i++) {
                float lag = Mth.sin(t - .17f * i) * weight * run * .018f;
                rig.set("feather_0" + i + side, lag, sign * (flight * (i - 3) * .04f - flare * i * .039f),
                        sign * (flight * i * -.033f + lag + banking * .019f * i));
            }
        }
    }

    private record RunPose(float mass, float suspension, float lean, float hipYaw, float hipShift,
                           float armDrive, float elbowDrive, float elbowTuck, float shoulderLag) {}
    private record PoseTarget(Vec3 ankle, Quaternionf rotation, boolean supported) {}
    private record HeightRange(float min, float max) {
        static final HeightRange FREE = new HeightRange(-5, 26);
    }

    private final class Leg {
        private final String thigh, shin, foot, shoulder, upper, lower, hand;
        private final int sign;
        private final Vec3 hipInPelvis, restAnkle, bindUpper, bindShin;
        private final double upperLength, shinLength;

        private Leg(BoxRig rig, String side, int sign) {
            this.sign = sign;
            thigh = "thigh" + side;
            shin = "shin" + side;
            foot = "foot" + side;
            shoulder = "shoulder" + side;
            upper = "upper_arm" + side;
            lower = "forearm" + side;
            hand = "hand" + side;
            hipInPelvis = rig.pivot(thigh).subtract(rig.pivot("pelvis")).scale(1.0 / 16);
            restAnkle = rig.pivot(foot).scale(1.0 / 16);
            bindUpper = rig.pivot(shin).subtract(rig.pivot(thigh)).scale(1.0 / 16);
            bindShin = rig.pivot(foot).subtract(rig.pivot(shin)).scale(1.0 / 16);
            upperLength = bindUpper.length();
            shinLength = bindShin.length();
        }

        private PoseTarget target(MechEntity mech, float partial, float planted, BootTerrain terrain) {
            var target = mech.sole(sign > 0, partial);
            if (target == null || planted <= 0) return null;
            float yaw = Mth.rotLerp(partial, mech.yRotO, mech.getYRot());
            Vec3 point = target.point();
            if (!target.supported() && terrain != null) {
                point = new Vec3(point.x, terrain.clearSwing(point, target.yaw(), target.pitch()), point.z);
            }
            Vec3 offset = point.subtract(mech.getPosition(partial)).yRot(yaw * Mth.DEG_TO_RAD);
            Vec3 sole = offset.multiply(1, -1, -1);
            Quaternionf flat = new Quaternionf().rotationYXZ(
                    Mth.wrapDegrees(target.yaw() - yaw) * Mth.DEG_TO_RAD, target.pitch(), 0);
            // The animated joint sits above the flat face of the tread.
            Vec3 ankle = sole.add(new Vec3(flat.transform(0, (float) restAnkle.y, 0, new Vector3f())));
            return new PoseTarget(ankle, flat, target.supported());
        }

        private Vec3 shiftTowardBoot(BoxRig rig, PoseTarget target) {
            if (target == null || !target.supported) return Vec3.ZERO;
            Vec3 gap = target.ankle.subtract(rig.anchor(thigh, 0, 0, 0));
            double maxReach = (upperLength + shinLength) * .97;
            double extension = gap.horizontalDistance() - maxReach * .88;
            if (extension <= 0) return Vec3.ZERO;
            return gap.multiply(1, 0, 1).normalize().scale(extension);
        }

        private Vec3 reachCorrection(Vec3 hip, PoseTarget target, Vec3 current) {
            if (target == null) return current;
            Vec3 gap = target.ankle.subtract(hip.add(current));
            double length = gap.length();
            double reach = (upperLength + shinLength) * .984;
            double fold = Math.abs(upperLength - shinLength) + .04;
            if (length > reach) return current.add(gap.scale((length - reach) / length));
            if (length < 1e-8) return current.add(0, -fold, 0);
            return length < fold ? current.add(gap.scale((length - fold) / length)) : current;
        }

        private HeightRange hipRange(BoxRig rig, PoseTarget target) {
            if (target == null) return HeightRange.FREE;
            Vec3 offset = target.ankle.subtract(rig.anchor(thigh, 0, 0, 0));
            double reach = (upperLength + shinLength) * .984;
            double fold = Math.abs(upperLength - shinLength) + .19;
            double horizontal = offset.horizontalDistanceSqr();
            float low = (float) ((offset.y - Math.sqrt(Math.max(.05, reach * reach - horizontal))) * 16);
            float high = (float) ((offset.y - Math.sqrt(Math.max(.04, fold * fold - horizontal))) * 16);
            return new HeightRange(Mth.clamp(low, -5, 30), Mth.clamp(high, -5, 30));
        }

        private void pose(BoxRig rig, PoseTarget target, float planted, float brace, float flight, float landing) {
            float hip = -.16f * brace + .12f * flight - .20f * landing;
            float knee = .38f * brace + .41f * flight + .32f * landing;
            rig.set(thigh, hip, 0, .064f * landing * sign);
            rig.set(shin, knee, 0, 0);
            rig.set(foot, -hip - knee, 0, -.064f * landing * sign);
            if (target == null) return;

            var parent = rig.parentTransform(thigh);
            var inv = new Matrix4f(parent).invert();
            Vec3 desired = new Vec3(inv.transformPosition(target.ankle.toVector3f())).subtract(hipInPelvis);
            Vec3 axis = desired.lengthSqr() < .001 ? new Vec3(0, 1, 0) : desired.normalize();
            double length = Mth.clamp(desired.length(), Math.abs(upperLength - shinLength) + .025, (upperLength + shinLength) * .998);
            // Choose the forward-facing solution to the triangle formed by the thigh, shin and ankle.
            Vec3 pole = new Vec3(0, 0, -1).add(axis.scale(axis.z)).normalize();
            if (pole.lengthSqr() < .001) pole = new Vec3(0, -1, 0).add(axis.scale(axis.y)).normalize();
            double along = (length * length + upperLength * upperLength - shinLength * shinLength) / (2 * length);
            Vec3 upperVector = axis.scale(along).add(pole.scale(Math.sqrt(Math.max(0, upperLength * upperLength - along * along))));
            Vec3 shinVector = axis.scale(length).subtract(upperVector);
            Quaternionf upperRotation = new Quaternionf().rotationTo(bindUpper.toVector3f(), upperVector.toVector3f());
            Quaternionf shinRotation = new Quaternionf().rotationTo(bindShin.toVector3f(),
                    upperRotation.transformInverse(shinVector.toVector3f()));
            Quaternionf ankleRotation = parent.getNormalizedRotation(new Quaternionf()).mul(upperRotation).mul(shinRotation)
                    .invert().mul(target.rotation);

            rig.orient(thigh, new Quaternionf().rotationXYZ(hip, 0, .064f * landing * sign).slerp(upperRotation, planted));
            rig.orient(shin, new Quaternionf().rotationX(knee).slerp(shinRotation, planted));
            rig.orient(foot, new Quaternionf().rotationXYZ(-hip - knee, 0, -.064f * landing * sign).slerp(ankleRotation, planted));
        }

        private void arm(BoxRig rig, float t, float movement, float direction, float turn, float run, float braking,
                         float flight, float brace, float landing) {
            float drive = Mth.cos(t - style.shoulderLag) * movement * direction;
            float lag = Mth.cos(t - style.shoulderLag - .49f) * movement * direction;
            float pull = Math.max(0, lag) * run;
            float brakeReach = braking * (.24f + .14f * style.mass);
            rig.set(shoulder, -drive * (.061f + run * .17f) - brakeReach * .34f, -turn * .14f + sign * pull * .08f,
                    sign * (.038f + .09f * run + .10f * flight + .064f * braking));
            rig.set(upper, -(.28f + style.armDrive * run) * drive - .62f * brace - .24f * flight - brakeReach + .18f * landing,
                    turn * .09f, sign * (.031f + .065f * run + .11f * flight + landing * .09f));
            rig.set(lower, -.12f * movement - style.elbowTuck * run - style.elbowDrive * pull
                    - .92f * brace - .14f * flight - landing * .28f + braking * .34f, 0, sign * .07f * pull);
            rig.set(hand, -.106f * lag * (.5f + run) + .092f * braking, 0, 0);
        }
    }
}
