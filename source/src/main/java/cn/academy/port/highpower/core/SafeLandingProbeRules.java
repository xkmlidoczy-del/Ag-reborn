package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW313: fixed physical fences and smoothly varying successful-confirm economics. */
public final class SafeLandingProbeRules {
    public static final double RANGE = SkillTuning.number("safe_landing_probe", "reach"), MIN_MASTERY_DISTANCE = 3;
    public static final int PREVIEW_TICKS = SkillTuning.integer("safe_landing_probe", "duration_ticks"), PREVIEW_INTERVAL_TICKS = 4,
            MOVEMENT_FENCE_TICKS = 8, MASTERY_INTERVAL_TICKS = 20;
    private static final ContinuousHighPowerGrowth.Curve COST =
            ContinuousHighPowerGrowth.curve("8", "4", "2", ".5", ".05");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN =
            ContinuousHighPowerGrowth.curve("40", "20", "10", "4", "1");

    private SafeLandingProbeRules() {}
    public static double cpCost(BigDecimal q) { return COST.value(q).doubleValue(); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return COOLDOWN.value(q); }
    public static int cooldownTicks(BigDecimal q) {
        return ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(q));
    }
    public static boolean clock(long tick) { return tick >= 0 && tick <= Long.MAX_VALUE - PREVIEW_TICKS; }
    public static boolean fresh(long issued, long now) {
        return clock(issued) && clock(now) && now >= issued && now - issued < PREVIEW_TICKS;
    }
    /** -1 is the only empty receipt; future and corrupt timestamps fail closed. */
    public static boolean ready(long previous, long now, int fence) {
        return clock(now) && fence > 0 && fence <= PREVIEW_TICKS
                && (previous == -1 || clock(previous) && now >= previous && now - previous >= fence);
    }
    public static boolean productiveMastery(double distance, long previous, long now) {
        return Double.isFinite(distance) && distance >= MIN_MASTERY_DISTANCE && distance <= RANGE
                && ready(previous, now, MASTERY_INTERVAL_TICKS);
    }
    /** A grounded native player can retain the ordinary negative gravity residue. */
    public static boolean groundedVerticalMotion(double velocity) {
        return Double.isFinite(velocity) && velocity >= -.15 && velocity <= 1e-5;
    }
}
