package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW322 fixed physical/lifetime fences and continuous successful-return economics; no mastery. */
public final class ReturnMarkRules {
    public static final double RANGE = SkillTuning.number("return_mark", "reach");
    public static final int MARK_LIFE_TICKS = SkillTuning.integer("return_mark", "duration_ticks"), WARNING_TICKS = 4, REQUEST_INTERVAL_TICKS = 8,
            MOVEMENT_FENCE_TICKS = 8;
    // The q=1 anchor completes the curve; classic progression still owns the LV2 unlock gate.
    private static final ContinuousHighPowerGrowth.Curve COST =
            ContinuousHighPowerGrowth.curve("12", "6", "3", ".75", ".05");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN =
            ContinuousHighPowerGrowth.curve("80", "40", "20", "8", "1");

    private ReturnMarkRules() {}

    public static double cpCost(BigDecimal q) { return COST.value(q).doubleValue(); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return COOLDOWN.value(q); }
    public static int cooldownTicks(BigDecimal q) {
        return ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(q));
    }
    public static boolean clock(long tick) { return tick >= 0 && tick <= Long.MAX_VALUE - MARK_LIFE_TICKS; }
    public static boolean fresh(long issued, long now) {
        return clock(issued) && clock(now) && now >= issued && now - issued < MARK_LIFE_TICKS;
    }
    /** Only -1 means an empty receipt; future/corrupt receipts fail closed. */
    public static boolean ready(long previous, long now, int fence) {
        return clock(now) && fence > 0 && fence <= MARK_LIFE_TICKS
                && (previous == -1 || clock(previous) && now >= previous && now - previous >= fence);
    }
    public static boolean groundedVerticalMotion(double velocity) {
        return SafeLandingProbeRules.groundedVerticalMotion(velocity);
    }
}
