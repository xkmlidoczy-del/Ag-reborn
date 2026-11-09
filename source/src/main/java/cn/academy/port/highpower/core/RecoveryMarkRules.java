package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW323 bounded physical recovery; q changes economics, never custody or range. */
public final class RecoveryMarkRules {
    public static final double RANGE = SkillTuning.number("recovery_mark", "reach");
    public static final int MARK_LIFE_TICKS = SkillTuning.integer("recovery_mark", "duration_ticks"), REQUEST_INTERVAL_TICKS = 8;
    private static final ContinuousHighPowerGrowth.Curve COST =
            ContinuousHighPowerGrowth.curve("6", "3", "1.5", ".375", ".025");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN =
            ContinuousHighPowerGrowth.curve("40", "20", "10", "4", "1");

    private RecoveryMarkRules() {}
    public static double cpCost(BigDecimal q) { return COST.value(q).doubleValue(); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return COOLDOWN.value(q); }
    public static int cooldownTicks(BigDecimal q) {
        return ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(q));
    }
    public static boolean clock(long tick) { return tick >= 0 && tick <= Long.MAX_VALUE - MARK_LIFE_TICKS; }
    public static boolean fresh(long issued, long now) {
        return clock(issued) && clock(now) && now >= issued && now - issued < MARK_LIFE_TICKS;
    }
    public static boolean ready(long previous, long now, int fence) {
        return clock(now) && fence > 0 && fence <= MARK_LIFE_TICKS
                && (previous == -1 || clock(previous) && now >= previous && now - previous >= fence);
    }
}
