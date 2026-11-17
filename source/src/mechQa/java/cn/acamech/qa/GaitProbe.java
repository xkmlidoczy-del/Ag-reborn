package cn.acamech.qa;

import cn.acamech.Footwork;
import cn.acamech.MechEntity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
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
    private int plantedSamples, splitSamples, terrainChecks;
    private double maxSplit, maxPenetration;
    private String penetrationAt = "", driftAt = "";
    private final Map<String, Stage> stages = new LinkedHashMap<>();

    void sample(Minecraft game, MechEntity mech, float partial, String stageName) throws ReflectiveOperationException {
        if (mech.feet() == null || mech.footing(partial) < .995f) return;
        Stage stage = stages.computeIfAbsent(stageName, ignored -> new Stage());
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
        stage.frames++;
        stage.bodyY.accept(body.y);
        stage.bodyZ.accept(body.z);
        boolean leftPlanted = mech.sole(true, partial).supported();
        boolean rightPlanted = mech.sole(false, partial).supported();
        double split = Math.abs(mech.sole(true, partial).point().y - mech.sole(false, partial).point().y);
        if (leftPlanted && rightPlanted) stage.maxSplit = Math.max(stage.maxSplit, split);
        if (leftPlanted && rightPlanted && split > .29 && mech.driveSpeed() > .08) {
            splitSamples++;
            maxSplit = Math.max(maxSplit, split);
        }
        for (int i = 0; i < 2; i++) {
            String side = i == 0 ? "L" : "R";
            String boot = "foot." + side;
            double soleHeight = -((Vec3) pivot.invoke(rig, boot)).y;
            Vec3 renderedSole = anchor(rig, boot, (float) soleHeight).multiply(1, -1, -1)
                    .yRot(-yaw * Mth.DEG_TO_RAD).add(body);
            Footwork.Sole goal = mech.sole(i == 0, partial);
            boolean down = goal.supported();
            inspectTread(mech, rig, boot, (float) soleHeight, yaw, body, down, partial, stage);
            if (down) {
                var sample = new SoleSample(goal.point(), renderedSole);
                double error = sample.point.distanceTo(sample.anchor);
                stage.plantedSamples++;
                stage.maxTargetError = Math.max(stage.maxTargetError, error);
                distanceError += error;
                if (error > worstGroundError) {
                    worstGroundError = error;
                    String thigh = "thigh." + side, shin = "shin." + side;
                    Vec3 hipAt = anchor(rig, thigh, 0).multiply(1, -1, -1)
                            .yRot(-yaw * Mth.DEG_TO_RAD).add(body);
                    Vec3 ankleAt = goal.point().add(0, -((Vec3) pivot.invoke(rig, boot)).y / 16, 0);
                    Vec3 thighRest = (Vec3) pivot.invoke(rig, thigh);
                    Vec3 shinRest = (Vec3) pivot.invoke(rig, shin);
                    Vec3 footRest = (Vec3) pivot.invoke(rig, boot);
                    double upper = thighRest.distanceTo(shinRest) / 16;
                    double lower = shinRest.distanceTo(footRest) / 16;
                    driftAt = boot + " rendered " + renderedSole + " expected " + goal.point() + " body " + body
                            + " left " + mech.feet().left().point() + " right " + mech.feet().right().point()
                            + " phase " + mech.phase(partial) + " brake " + mech.braking(partial)
                            + " hip=" + hipAt + ", desired ankle=" + ankleAt + ", length=" + hipAt.distanceTo(ankleAt)
                            + ", kinematic min=" + (Math.abs(upper - lower) + .025) + ", max=" + ((upper + lower) * .998);
                }
                plantedSamples++;
                if (last[i] != null && sample.anchor.distanceToSqr(last[i].anchor) < .00000001) {
                    double drift = sample.point.distanceTo(last[i].point);
                    worstDrift = Math.max(worstDrift, drift);
                    stage.maxDrift = Math.max(stage.maxDrift, drift);
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
                        + ", drift=" + worstDrift + ", max=" + worstGroundError + ": " + driftAt
                        + "; maximum tread penetration=" + maxPenetration + " at " + penetrationAt);
        require(hip.span() > .10 && knee.span() > .08 && elbow.span() > .09, "running piston travel " + chassis
                + ": hip=" + hip.span() + ", knee=" + knee.span() + ", elbow=" + elbow.span());
        require(splitSamples > 2 && maxSplit >= .49, "both boots supported at different heights " + chassis + " " + splitSamples);
        require(terrainChecks > 300 && maxPenetration < .115,
                "shoe clears step edges " + chassis + ": penetration=" + maxPenetration + " at " + penetrationAt);
        for (String name : new String[]{"weight_run", "gait_down", "gait_turn", "gait_brake", "gait_resume",
                "lip_approach", "lip_brake", "lip_resume"}) {
            Stage stage = stages.get(name);
            require(stage != null && stage.frames > 3 && stage.plantedSamples > 3,
                    "rendered terrain stage sampled " + chassis + " " + name);
            require(stage.maxPenetration < .115 && stage.maxTargetError < .18 && stage.maxDrift < .13,
                    "rendered terrain stage grip " + chassis + " " + name + ": " + stage.values());
        }
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("continuous_planted_pairs", lockedPairs);
        result.put("planted_samples", plantedSamples);
        result.put("split_height_frames_while_moving", splitSamples);
        result.put("max_split_height_blocks", mm(maxSplit));
        result.put("tread_samples", terrainChecks);
        result.put("max_tread_penetration_blocks", mm(maxPenetration));
        result.put("max_frame_slide_blocks", mm(worstDrift));
        result.put("max_target_error_blocks", mm(worstGroundError));
        result.put("mean_target_error_blocks", mm(distanceError / plantedSamples));
        result.put("max_target_error_detail", driftAt);
        result.put("max_tread_penetration_detail", penetrationAt);
        result.put("peak_lift_blocks", mm(worstAirLift));
        result.put("peak_boot_split_blocks", mm(reach.span() * .5));
        result.put("hip_travel_blocks", mm(hip.span()));
        result.put("knee_travel_blocks", mm(knee.span()));
        result.put("elbow_travel_blocks", mm(elbow.span()));
        Map<String, Object> stageResults = new LinkedHashMap<>();
        stages.forEach((name, stage) -> stageResults.put(name, stage.values()));
        result.put("stages", stageResults);
        return result;
    }

    private void inspectTread(MechEntity mech, Object rig, String foot, float soleHeight, float yaw, Vec3 origin,
                              boolean planted, float partial, Stage stage) throws ReflectiveOperationException {
        var boot = mech.chassis().boot;
        for (int side = -1; side <= 1; side++) {
            float x = (float) (side * boot.halfWidth() * 15.3);
            for (int front = -1; front <= 1; front++) {
                float z = (float) (front * (front > 0 ? boot.heel() : boot.toe()) * 15.3);
                var point = ((Vec3) anchor.invoke(rig, foot, x, soleHeight, z)).multiply(1, -1, -1)
                        .yRot(-yaw * Mth.DEG_TO_RAD).add(origin);
                var hit = mech.level().clip(new ClipContext(point.add(0, 1.05, 0), point.add(0, -.07, 0),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mech));
                terrainChecks++;
                stage.treadSamples++;
                if (hit.getType() == HitResult.Type.BLOCK) {
                    stage.maxPenetration = Math.max(stage.maxPenetration, hit.getLocation().y - point.y);
                }
                if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().y - point.y > maxPenetration) {
                    maxPenetration = hit.getLocation().y - point.y;
                    var blockHit = (net.minecraft.world.phys.BlockHitResult) hit;
                    var pos = blockHit.getBlockPos();
                    var goal = mech.sole(foot.endsWith(".L"), partial);
                    penetrationAt = foot + " " + point + ", planted=" + planted + ", phase=" + mech.phase(partial)
                            + ", partial=" + partial + ", body=" + origin + ", bodyYaw=" + yaw
                            + ", goal=" + goal + ", block=" + pos + " " + mech.level().getBlockState(pos)
                            + ", hit=" + hit.getLocation() + ", treadSample=" + side + "/" + front;
                }
            }
        }
    }

    private Vec3 anchor(Object rig, String name, float y) throws ReflectiveOperationException {
        return (Vec3) anchor.invoke(rig, name, 0f, y, 0f);
    }

    private double length(Object rig, String ram) throws ReflectiveOperationException {
        return anchor(rig, ram + ".body", 0).distanceTo(anchor(rig, ram + ".pin", 0));
    }

    private static double mm(double blocks) { return Math.round(blocks * 1000) / 1000d; }
    private record SoleSample(Vec3 anchor, Vec3 point) {}

    private static final class Stage {
        int frames, plantedSamples, treadSamples;
        double maxTargetError, maxDrift, maxPenetration, maxSplit;
        final Range bodyY = new Range(), bodyZ = new Range();

        Map<String, Object> values() {
            var result = new LinkedHashMap<String, Object>();
            result.put("rendered_frames", frames);
            result.put("planted_samples", plantedSamples);
            result.put("tread_samples", treadSamples);
            result.put("max_target_error_blocks", mm(maxTargetError));
            result.put("max_frame_slide_blocks", mm(maxDrift));
            result.put("max_tread_penetration_blocks", mm(maxPenetration));
            result.put("max_supported_height_difference_blocks", mm(maxSplit));
            result.put("body_y_min", mm(bodyY.min));
            result.put("body_y_max", mm(bodyY.max));
            result.put("body_z_min", mm(bodyZ.min));
            result.put("body_z_max", mm(bodyZ.max));
            return result;
        }
    }

    private static final class Range {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        void accept(double value) { min = Math.min(min, value); max = Math.max(max, value); }
        double span() { return max - min; }
    }
}
