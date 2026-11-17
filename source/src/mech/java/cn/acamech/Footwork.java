package cn.acamech;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Plans steps from the actual terrain. A planted boot keeps its world position and heading. */
public final class Footwork {
    private static final double[] FORE_OFFSETS = {0, .34, -.34, .68, -.68};
    private static final double[] SIDE_OFFSETS = {0, -.28, .28};
    private static final double[] ARC_SAMPLES = {.28, .55, .78};

    public record Sole(Vec3 point, float yaw, float pitch, boolean supported) {
        private static final StreamCodec<ByteBuf, Sole> CODEC = StreamCodec.composite(
                MechMod.POSITION, Sole::point, ByteBufCodecs.FLOAT, Sole::yaw,
                ByteBufCodecs.FLOAT, Sole::pitch, ByteBufCodecs.BOOL, Sole::supported, Sole::new);

        public Sole lerp(Sole next, float t) {
            if (equals(next)) return this;
            if (t <= 0) return this;
            if (t >= 1) return next;
            return new Sole(point.lerp(next.point, t), Mth.rotLerp(t, yaw, next.yaw), Mth.lerp(t, pitch, next.pitch), false);
        }
    }

    /** swingSide is -1 for left, +1 for right and zero in double support. */
    public record Frame(Sole left, Sole right, int swingSide, float incline, float descent) {
        static final StreamCodec<ByteBuf, Optional<Frame>> CODEC = ByteBufCodecs.optional(StreamCodec.composite(
                Sole.CODEC, Frame::left, Sole.CODEC, Frame::right, ByteBufCodecs.VAR_INT, Frame::swingSide,
                ByteBufCodecs.FLOAT, Frame::incline, ByteBufCodecs.FLOAT, Frame::descent, Frame::new));
    }

    private Sole left, right, swingStart, swingGoal;
    private Vec3 previousBody;
    private float previousYaw;
    private int nextSide = -1, swingSide, settleTicks;
    private float progress, cycle, incline, descent;
    private boolean stopping;

    Frame update(MechEntity mech, Vec3 before, float speed, float turn, float run, boolean stepping, boolean coast) {
        Vec3 body = mech.position();
        if (!stepping) {
            previousBody = null;
            return null;
        }
        var terrain = new BootTerrain(mech);
        if (previousBody == null || previousBody.distanceToSqr(body) > 4
                || Math.abs(Mth.wrapDegrees(mech.getYRot() - previousYaw)) > 30) {
            left = foothold(mech, terrain, neutral(mech, body, mech.getYRot(), -1, 0));
            right = foothold(mech, terrain, neutral(mech, body, mech.getYRot(), 1, 0));
            nextSide = right.supported ? -1 : 1;
            swingSide = settleTicks = 0;
            cycle = nextSide < 0 ? 0f : .5f;
            stopping = false;
            incline = descent = 0;
        }
        previousBody = body;
        previousYaw = mech.getYRot();

        double dy = body.y - before.y;
        double moved = before.subtract(body).horizontalDistance();
        double pivotTravel = Math.abs(turn) * Mth.DEG_TO_RAD * mech.chassis().bootSpacing * 1.24;
        double travel = Math.max(moved, pivotTravel) + .48 * Math.min(moved, pivotTravel);
        double stepLength = mech.chassis().stride(run) * .5;
        boolean changingLevel = Math.abs(dy) > .12 || Math.abs(incline) > .13
                || Math.abs(left.point.y - right.point.y) > .29 && left.supported && right.supported;
        // Budget room for one double-support tick within the step's travel at running speed.
        if (changingLevel) stepLength -= Math.min(travel, stepLength * .30);
        boolean moving = travel > .008;
        descent = Math.max(descent * .79f, Mth.clamp((float) (-dy * 1.45), 0, 1));
        if (descent < .004f) descent = 0;
        if (swingSide == 0) {
            if (settleTicks > 0) {
                settleTicks--;
                return frame();
            }
            if (!moving || coast && stopping) {
                incline = Mth.approach(incline, 0, .10f);
                return frame();
            }
            swingSide = nextSide;
            stopping = coast;
            startSwing();
        }
        if (coast != stopping) {
            // Replan only the raised foot, keeping the other on its original contact.
            stopping = coast;
            startSwing();
        }

        float delta = stopping ? (mech.chassis() == Chassis.ATLAS ? .195f : .25f)
                : (float) Mth.clamp(travel / stepLength, .04, .35);
        if (!moving) delta = Math.max(delta, .20f);
        progress = Math.min(1f, progress + delta);
        float end = swingSide < 0 ? .5f : 1f;
        cycle = Math.min(end, cycle + delta * .5f);
        if (swingGoal == null || !swingGoal.supported || progress < (stopping ? .96f : .82f)) {
            Sole aim = stopping ? brakeGoal(mech, speed, turn) : movingGoal(mech, speed, turn, progress, stepLength, travel);
            Sole blended = swingGoal == null ? aim : swingGoal.lerp(aim, .58f);
            swingGoal = foothold(mech, terrain, blended);
        }

        double heightChange = swingGoal.point.y - swingStart.point.y;
        float grade = Mth.clamp((float) (heightChange * 1.22), -1, 1);
        incline = Mth.lerp(.32f, incline, grade);
        Sole next = swingPose(mech, terrain, run);
        setSole(swingSide, next);
        if (progress >= 1) {
            int planted = swingSide;
            setSole(planted, swingGoal);
            swingSide = 0;
            nextSide = -planted;
            cycle = planted < 0 ? .5f : 0f;
            if (swingGoal.supported) {
                Sole other = sole(nextSide);
                if (other.supported && Math.abs(other.point.y - swingGoal.point.y) > .25) {
                    // Keep one full interpolation interval of double support when changing levels.
                    settleTicks = 1;
                }
                descent = Math.max(descent, (float) Mth.clamp(-heightChange * .90, 0, 1));
                mech.touchDown(swingGoal.point, run, stopping || heightChange < -.25);
            }
        }
        return frame();
    }

    float cycle() { return cycle == 1f ? 0f : cycle; }

    private void startSwing() {
        swingStart = sole(swingSide);
        swingGoal = null;
        progress = 0;
    }

    private Sole swingPose(MechEntity mech, BootTerrain terrain, float run) {
        if (progress >= 1) return swingGoal;
        Vec3 start = swingStart.point;
        Vec3 goal = swingGoal.point;
        float yaw = Mth.rotLerp(progress, swingStart.yaw, swingGoal.yaw);
        double crest = Math.max(start.y, goal.y);
        for (double at : ARC_SAMPLES) {
            Vec3 across = start.lerp(goal, at);
            double height = terrain.height(across, yaw, crest - .05, crest + .76);
            if (height <= Math.max(start.y, goal.y) + 1.05) crest = Math.max(crest, height);
        }

        boolean uneven = Math.abs(start.y - goal.y) > .09 || crest > Math.max(start.y, goal.y) + .09;
        double ease = uneven ? ease((progress - .13f) / .72f) : ease(progress);
        Vec3 at = start.lerp(goal, ease);
        double y = at.y;
        if (uneven) {
            // Lift before crossing the lip, carry over it, then lower onto the target tread.
            y = progress < .59f ? Mth.lerp(ease(progress / .36f), start.y, crest)
                    : Mth.lerp(ease((progress - .59f) / .41f), crest, goal.y);
        }
        double lift = stopping ? Math.sin(Math.PI * progress) * .13
                : mech.chassis().swingLift(run) * liftCurve(progress, mech.chassis().liftPeak);
        float pitch = Mth.lerp((float) ease, swingStart.pitch, 0)
                + Mth.sin(progress * (float) Math.TAU) * (uneven ? .13f : .15f + run * .20f);
        Vec3 ground = new Vec3(at.x, y + lift, at.z);
        return new Sole(new Vec3(at.x, terrain.clearSwing(ground, yaw, pitch), at.z), yaw, pitch, false);
    }

    private Sole movingGoal(MechEntity mech, float speed, float turn, float fraction, double step, double travel) {
        double ticksPerStep = Mth.clamp(step / Math.max(travel, .064), 2.9, 11);
        double time = ticksPerStep * (1 - fraction);
        double yawLead = turn * (time + ticksPerStep * .27);
        float yaw = mech.getYRot();
        Vec3 at = mech.position().add(forward(yaw + (float) (yawLead * .48)).scale(speed * time));
        double reach = Math.copySign(Math.min(step * .48, Math.abs(speed) * ticksPerStep * .53), speed);
        reach *= 1 - .27 * Mth.clamp(Math.abs(turn) / mech.chassis().turn, 0, 1);
        return neutral(mech, at, yaw + (float) yawLead, swingSide, reach);
    }

    private Sole brakeGoal(MechEntity mech, float speed, float turn) {
        double lead = Math.copySign(Math.min(.50, speed * speed / (2.4 * mech.chassis().acceleration)), speed);
        return neutral(mech, mech.position(), mech.getYRot() + turn, swingSide, lead + .07);
    }

    private static Sole neutral(MechEntity mech, Vec3 at, float yaw, int side, double reach) {
        var chassis = mech.chassis();
        Vec3 point = at.add(new Vec3(-side * chassis.bootSpacing, 0, chassis.bootForward + reach).yRot(-yaw * Mth.DEG_TO_RAD));
        return new Sole(point, yaw + side * chassis.toeOut, 0, false);
    }

    private static Sole foothold(MechEntity mech, BootTerrain terrain, Sole aim) {
        double bodyY = mech.getY();
        double below = bodyY - 1.11, above = bodyY + mech.maxUpStep() + .015;
        Vec3 at = aim.point;
        var direct = terrain.contact(at, aim.yaw, below, above);
        if (direct != null && direct.coverage() >= .66) return supported(aim, at, direct);

        Sole best = direct == null ? null : supported(aim, at, direct);
        double bestScore = direct == null ? -.40 : direct.coverage();
        Vec3 front = forward(aim.yaw);
        Vec3 across = new Vec3(front.z, 0, -front.x);
        for (double f : FORE_OFFSETS) {
            for (double side : SIDE_OFFSETS) {
                if (f == 0 && side == 0) continue;
                Vec3 point = at.add(front.scale(f)).add(across.scale(side));
                var contact = terrain.contact(point, aim.yaw, below, above);
                if (contact == null) continue;
                double score = contact.coverage() - Math.hypot(f, side) * .46;
                if (score > bestScore) {
                    bestScore = score;
                    best = supported(aim, point, contact);
                }
            }
        }
        if (best != null && bestScore > -.10) return best;
        // The collision box can stand on a ledge even if there is no tread under one boot.
        return new Sole(new Vec3(at.x, bodyY + .12, at.z), aim.yaw, 0, false);
    }

    private static Sole supported(Sole aim, Vec3 at, BootTerrain.Contact contact) {
        return new Sole(new Vec3(at.x, contact.height(), at.z), aim.yaw, 0, true);
    }

    private Frame frame() { return new Frame(left, right, swingSide, incline, descent); }

    private Sole sole(int side) { return side < 0 ? left : right; }

    private void setSole(int side, Sole value) {
        if (side < 0) left = value; else right = value;
    }

    private static Vec3 forward(float yaw) {
        return new Vec3(0, 0, 1).yRot(-yaw * Mth.DEG_TO_RAD);
    }

    private static double liftCurve(float t, float peak) {
        return t < peak ? ease(t / peak) : 1 - ease((t - peak) / (1 - peak));
    }

    private static double ease(double t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
