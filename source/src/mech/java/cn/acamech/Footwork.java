package cn.acamech;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Chooses where the boots meet the ground. Once down, a boot keeps its world position and heading. */
public final class Footwork {
    public record Sole(Vec3 point, float yaw, float pitch) {
        private static final StreamCodec<ByteBuf, Sole> CODEC = StreamCodec.composite(
                MechMod.POSITION, Sole::point, ByteBufCodecs.FLOAT, Sole::yaw,
                ByteBufCodecs.FLOAT, Sole::pitch, Sole::new);

        public Sole lerp(Sole next, float t) {
            return new Sole(point.lerp(next.point, t), Mth.rotLerp(t, yaw, next.yaw), Mth.lerp(t, pitch, next.pitch));
        }
    }

    /** swingSide is -1 for left, +1 for right and zero when both boots are down. */
    public record Frame(Sole left, Sole right, int swingSide) {
        static final StreamCodec<ByteBuf, Optional<Frame>> CODEC = ByteBufCodecs.optional(StreamCodec.composite(
                Sole.CODEC, Frame::left, Sole.CODEC, Frame::right,
                ByteBufCodecs.VAR_INT, Frame::swingSide, Frame::new));
    }

    private Sole left, right, swingStart, swingGoal;
    private Vec3 previousBody;
    private float previousYaw;
    private int nextSide = -1, swingSide;
    private float progress, cycle;
    private boolean stopping;
    private boolean initialized;

    Frame update(MechEntity mech, Vec3 before, float speed, float turn, float run, boolean stepping, boolean coast) {
        Vec3 body = mech.position();
        if (!stepping) {
            initialized = false;
            return null;
        }
        if (!initialized || previousBody.distanceToSqr(body) > 4
                || Math.abs(Mth.wrapDegrees(mech.getYRot() - previousYaw)) > 30) {
            left = neutral(mech, body, mech.getYRot(), -1, 0);
            right = neutral(mech, body, mech.getYRot(), 1, 0);
            nextSide = -1;
            swingSide = 0;
            cycle = 0;
            initialized = true;
            stopping = false;
        }
        previousBody = body;
        previousYaw = mech.getYRot();
        double moved = before.subtract(body).horizontalDistance();
        double pivotTravel = Math.abs(turn) * Mth.DEG_TO_RAD * mech.chassis().bootSpacing * 1.24;
        double travel = Math.max(moved, pivotTravel) + .48 * Math.min(moved, pivotTravel);
        double stepLength = mech.chassis().stride(run) * .5;
        boolean moving = travel > .008;
        if (swingSide == 0) {
            if (!moving || coast && stopping) return frame();
            swingSide = nextSide;
            swingStart = sole(swingSide);
            stopping = coast;
            progress = 0;
            swingGoal = null;
        }
        if (stopping && !coast) {
            swingStart = sole(swingSide);
            swingGoal = null;
            stopping = false;
            progress = 0;
        } else if (coast && !stopping) {
            // Finish this swing from its current position. The other boot stays on the ground.
            swingStart = sole(swingSide);
            progress = 0;
            swingGoal = null;
            stopping = true;
        }
        float delta = stopping ? (mech.chassis() == Chassis.ATLAS ? .195f : .25f)
                : (float) Mth.clamp(travel / stepLength, .04, .36);
        if (!moving) delta = Math.max(delta, .20f);
        progress = Math.min(1f, progress + delta);
        float end = swingSide < 0 ? .5f : 1f;
        cycle = Math.min(end, Math.max(cycle + delta * .5f, end - (.5f - .5f * progress)));
        if (swingGoal == null || progress < .88f || stopping && progress < .97f) {
            Sole predicted = stopping ? brakeGoal(mech, speed, turn) : movingGoal(mech, speed, turn, progress, stepLength, travel);
            swingGoal = swingGoal == null ? predicted : swingGoal.lerp(predicted, .56f);
        }

        float swingRun = stopping ? .12f : run;
        double ease = smooth(progress);
        double clearance = mech.chassis().swingLift(swingRun);
        double lift = stopping ? Math.sin(Math.PI * progress) * .105
                : clearance * liftCurve(progress, mech.chassis().liftPeak);
        Vec3 at = swingStart.point.lerp(swingGoal.point, ease).add(0, lift, 0);
        float ankle = Mth.lerp((float) ease, swingStart.pitch, 0f)
                + Mth.sin(progress * (float) Math.TAU) * (.14f + swingRun * .21f);
        setSole(swingSide, new Sole(at, Mth.rotLerp((float) ease, swingStart.yaw, swingGoal.yaw), ankle));
        if (progress >= 1) {
            int planted = swingSide;
            setSole(planted, swingGoal);
            swingSide = 0;
            nextSide = -planted;
            cycle = planted < 0 ? .5f : 0f;
            mech.touchDown(swingGoal.point, run, stopping);
        }
        return frame();
    }

    float cycle() { return cycle == 1f ? 0f : cycle; }

    private Sole movingGoal(MechEntity mech, float speed, float turn, float fraction, double step, double travel) {
        double ticksPerStep = Mth.clamp(step / Math.max(travel, .064), 2.9, 11.0);
        double time = ticksPerStep * (1 - fraction);
        // Lead the chassis down its arc from the current heading. Faster turns shorten the reach.
        double yawLead = turn * (time + ticksPerStep * .27);
        float yaw = mech.getYRot();
        Vec3 at = mech.position().add(forward(yaw + (float) (yawLead * .48)).scale(speed * time));
        float plantYaw = yaw + (float) yawLead;
        double strideReach = Math.copySign(Math.min(step * .48, Math.abs(speed) * ticksPerStep * .53), speed);
        strideReach *= 1 - .27 * Mth.clamp(Math.abs(turn) / mech.chassis().turn, 0, 1);
        return ground(mech, neutral(mech, at, plantYaw, swingSide, strideReach));
    }

    private Sole brakeGoal(MechEntity mech, float speed, float turn) {
        double lead = Math.copySign(Math.min(.50, speed * speed / (2.4 * mech.chassis().acceleration)), speed);
        return ground(mech, neutral(mech, mech.position(), mech.getYRot() + turn, swingSide, lead + .07));
    }

    private static Sole neutral(MechEntity mech, Vec3 at, float yaw, int side, double reach) {
        var chassis = mech.chassis();
        Vec3 point = at.add(new Vec3(-side * chassis.bootSpacing, 0, chassis.bootForward + reach).yRot(-yaw * Mth.DEG_TO_RAD));
        return new Sole(point, yaw + side * chassis.toeOut, 0);
    }

    private static Sole ground(MechEntity mech, Sole goal) {
        double y = mech.getY();
        Vec3 sample = new Vec3(goal.point.x, y, goal.point.z);
        var hit = mech.level().clip(new ClipContext(sample.add(0, .85, 0), sample.add(0, -.95, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mech));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return new Sole(hit.getLocation(), goal.yaw, 0);
        }
        // The vehicle's collision box reaches a ledge before an individual boot does.
        return new Sole(sample, goal.yaw, 0);
    }

    private Frame frame() { return new Frame(left, right, swingSide); }

    private Sole sole(int side) { return side < 0 ? left : right; }

    private void setSole(int side, Sole value) {
        if (side < 0) left = value; else right = value;
    }

    private static Vec3 forward(float yaw) {
        return new Vec3(0, 0, 1).yRot(-yaw * Mth.DEG_TO_RAD);
    }

    private static double liftCurve(float t, float peak) {
        return t < peak ? smooth(t / peak) : 1 - smooth((t - peak) / (1 - peak));
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }
}
