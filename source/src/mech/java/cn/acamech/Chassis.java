package cn.acamech;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Blocks, blocks/tick, game ticks, and integer charge units. */
public enum Chassis {
    ATLAS("atlas", 4.75f, 6f, .226, .035, 1.05f, 3.2f, 160, 1200, 16, 5.0,
            3.56, .60, 11.5f, 2.04, 4.65, 1.49, .23f, 19.467, 0, 7f, .35, .62, .36f, MechAttack.HYDRAULIC, RangedWeapon.PULSE),
    AETHER("aether", 2.20f, 4.75f, .345, .067, 1.05f, 5.5f, 88, 820, 8, 3.5,
            2.49, .25, 9.0f, 1.75, 4.28, 1.44, .38f, 10.857, -2.171, 4f, .42, .97, .48f, MechAttack.SWEEP, RangedWeapon.NEEDLE),
    SERAPH("seraph", 3.70f, 5.40f, .267, .048, 1.05f, 4.0f, 112, 1000, 12, 4.2,
            2.96, .34, 12.5f, 1.91, 4.50, 1.46, .29f, 10.8, -1.944, 5f, .38, .79, .39f, MechAttack.WINGS, RangedWeapon.CONVERGENCE);

    public final String id;
    public final float width, height, step, turn, camera, turnResponse;
    public final double speed, acceleration, reach, seat, seatForward, stride, runStride, runMultiplier;
    // Neutral boot pivot, measured parallel to the soles in the editable rig.
    final double bootSpacing, bootForward, walkLift, runLift;
    final float toeOut, liftPeak;
    public final int hull, energy, power;
    public final MechAttack attack;
    public final RangedWeapon ranged;

    Chassis(String id, float width, float height, double speed, double acceleration, float step,
            float turn, int hull, int energy, int power, double reach,
            double seat, double seatForward, float camera, double stride, double runStride, double runMultiplier,
            float turnResponse, double bootSpacingPixels, double bootForwardPixels, float toeOut,
            double walkLift, double runLift, float liftPeak, MechAttack attack, RangedWeapon ranged) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.speed = speed;
        this.acceleration = acceleration;
        this.step = step;
        this.turn = turn;
        this.hull = hull;
        this.energy = energy;
        this.power = power;
        this.reach = reach;
        this.seat = seat;
        this.seatForward = seatForward;
        this.camera = camera;
        this.stride = stride;
        this.runStride = runStride;
        this.runMultiplier = runMultiplier;
        this.turnResponse = turnResponse;
        this.bootSpacing = bootSpacingPixels / 16;
        this.bootForward = bootForwardPixels / 16;
        this.toeOut = toeOut;
        this.walkLift = walkLift;
        this.runLift = runLift;
        this.liftPeak = liftPeak;
        this.attack = attack;
        this.ranged = ranged;
    }

    /** Distance covered in one two-step cycle at the current walk/run blend. */
    public double stride(float run) { return stride + (runStride - stride) * run; }

    double swingLift(float run) { return Mth.lerp(run, walkLift, runLift); }

    public Component displayName() {
        return Component.translatable("entity.acamech." + id);
    }
}
