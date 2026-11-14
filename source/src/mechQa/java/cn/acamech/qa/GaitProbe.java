package cn.acamech.qa;

import cn.acamech.Footwork;
import cn.acamech.MechEntity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static cn.acamech.qa.NativeRun.require;

/** Checks the rendered joints, in world coordinates, after the real entity renderer ran. */
final class GaitProbe {
    private Field rigField;
    private Method anchor, pivot;
    private final SoleSample[] last = new SoleSample[2];
    private final Range hip = new Range(), knee = new Range(), elbow = new Range();
    private final Range reach = new Range();
    private int lockedPairs;
    private double worstDrift, worstGroundError;
    private double distanceError, worstAirLift;
    private int plantedSamples;

    void sample(Minecraft game, MechEntity mech, float partial) throws ReflectiveOperationException {
        if (mech.feet() == null || mech.footing(partial) < .995f) return;
        var renderer = game.getEntityRenderDispatcher().getRenderer(mech);
        if (rigField == null) {
            rigField = renderer.getClass().getDeclaredField("rig");
            rigField.setAccessible(true);
            var type = rigField.getType();
            anchor = type.getDeclaredMethod("anchor", String.class, float.class, float.class, float.class);
            pivot = type.getDeclaredMethod("pivot", String.class);
            anchor.setAccessible(true);
            pivot.setAccessible(true);
        }
        Object rig = rigField.get(renderer);
        float yaw = Mth.rotLerp(partial, mech.yRotO, mech.getYRot());
        Vec3 body = mech.getPosition(partial);
        for (int i = 0; i < 2; i++) {
            String side = i == 0 ? "L" : "R";
            String boot = "foot." + side;
            double soleHeight = -((Vec3) pivot.invoke(rig, boot)).y;
            Vec3 renderedSole = anchor(rig, boot, (float) soleHeight).multiply(1, -1, -1)
                    .yRot(-yaw * Mth.DEG_TO_RAD).add(body);
            Footwork.Sole goal = mech.sole(i == 0, partial);
            boolean down = mech.feet().swingSide() != (i == 0 ? -1 : 1) && Math.abs(goal.pitch()) < .001;
            if (down) {
                var sample = new SoleSample(goal.point(), renderedSole);
                double error = sample.point.distanceTo(sample.anchor);
                distanceError += error;
                worstGroundError = Math.max(worstGroundError, error);
                plantedSamples++;
                if (last[i] != null && sample.anchor.distanceToSqr(last[i].anchor) < .00000001) {
                    worstDrift = Math.max(worstDrift, sample.point.distanceTo(last[i].point));
                    lockedPairs++;
                }
                last[i] = sample;
            } else {
                last[i] = null;
                worstAirLift = Math.max(worstAirLift, renderedSole.y - body.y);
            }
        }
        reach.accept(anchor(rig, "foot.L", 0).z - anchor(rig, "foot.R", 0).z);
        hip.accept(length(rig, "hip_ram_l"));
        knee.accept(length(rig, "knee_ram_l"));
        elbow.accept(length(rig, "elbow_ram_l"));
    }

    Map<String, Object> results(String chassis) {
        require(lockedPairs > 40 && plantedSamples > 60, "continuous foothold samples " + chassis + ": " + lockedPairs);
        require(distanceError / plantedSamples < .035 && worstDrift < .13 && worstGroundError < .18,
                "rendered sole grip " + chassis + ": mean=" + distanceError / plantedSamples
                        + ", drift=" + worstDrift + ", max=" + worstGroundError);
        require(hip.span() > .10 && knee.span() > .08 && elbow.span() > .09, "running piston travel " + chassis
                + ": hip=" + hip.span() + ", knee=" + knee.span() + ", elbow=" + elbow.span());
        return Map.of("continuous_planted_pairs", lockedPairs, "planted_samples", plantedSamples,
                "max_frame_slide_blocks", mm(worstDrift), "max_target_error_blocks", mm(worstGroundError),
                "mean_target_error_blocks", mm(distanceError / plantedSamples), "peak_lift_blocks", mm(worstAirLift),
                "peak_boot_split_blocks", mm(reach.span() * .5), "hip_travel_blocks", mm(hip.span()),
                "knee_travel_blocks", mm(knee.span()), "elbow_travel_blocks", mm(elbow.span()));
    }

    private Vec3 anchor(Object rig, String name, float y) throws ReflectiveOperationException {
        return (Vec3) anchor.invoke(rig, name, 0f, y, 0f);
    }

    private double length(Object rig, String ram) throws ReflectiveOperationException {
        return anchor(rig, ram + ".body", 0).distanceTo(anchor(rig, ram + ".pin", 0));
    }

    private static double mm(double blocks) { return Math.round(blocks * 1000) / 1000d; }
    private record SoleSample(Vec3 anchor, Vec3 point) {}

    private static final class Range {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        void accept(double value) { min = Math.min(min, value); max = Math.max(max, value); }
        double span() { return max - min; }
    }
}
