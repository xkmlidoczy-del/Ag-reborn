package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW333 fixed physical timing and smooth successful-swap economics. */
public final class CornerExchangeRules {
    public static final double RANGE = SkillTuning.number("corner_exchange", "reach"), NEAR_HORIZONTAL = .75, NEAR_VERTICAL = .125;
    public static final int MARK_LIFE_TICKS = SkillTuning.integer("corner_exchange", "duration_ticks"), PREPARATION_TICKS = MARK_LIFE_TICKS,
            WARNING_TICKS = 3, HOLD_CLEAR_TICKS = 6, REQUEST_INTERVAL_TICKS = 4,
            MOVEMENT_FENCE_TICKS = 8;
    private static final ContinuousHighPowerGrowth.Curve COST =
            ContinuousHighPowerGrowth.curve("8", "4", "2", ".5", ".05");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN =
            ContinuousHighPowerGrowth.curve("40", "20", "10", "4", "1");
    private CornerExchangeRules() {}
    public static double cpCost(BigDecimal q) { return COST.value(q).doubleValue(); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return COOLDOWN.value(q); }
    public static int cooldownTicks(BigDecimal q) {
        return ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(q));
    }
    /** Only release classifies a tap; crossing the hold boundary is exclusively a clear. */
    public static boolean tapRelease(int heldTicks) { return heldTicks >= 0 && heldTicks < HOLD_CLEAR_TICKS; }
    public static boolean holdClears(int heldTicks) { return heldTicks >= HOLD_CLEAR_TICKS; }
    public static boolean withinRange(double distance) {
        return Double.isFinite(distance) && distance >= 0 && distance <= RANGE;
    }
    public static boolean near(double horizontal, double vertical) {
        return Double.isFinite(horizontal) && horizontal >= 0 && horizontal <= NEAR_HORIZONTAL
                && Double.isFinite(vertical) && Math.abs(vertical) <= NEAR_VERTICAL;
    }
    public static float commitYaw(float current, boolean reversed) {
        if (!Float.isFinite(current)) throw new IllegalArgumentException("Invalid corner facing");
        // Reduce before adding so even a finite extreme float cannot round away the half-turn.
        return reversed ? (float) Math.IEEEremainder(Math.IEEEremainder((double) current, 360) + 180, 360) : current;
    }
    public static boolean groundedVerticalMotion(double velocity) {
        return SafeLandingProbeRules.groundedVerticalMotion(velocity);
    }
}
