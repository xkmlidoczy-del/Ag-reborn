package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW342 fixed escape boundaries and continuous manual economics; no mastery. */
public final class EmergencyReturnRules {
    public static final double RANGE = SkillTuning.number("emergency_return", "reach");
    public static final int MARK_LIFE_TICKS = SkillTuning.integer("emergency_return", "duration_ticks"), AUTOMATIC_FENCE_TICKS = 1200,
            REQUEST_INTERVAL_TICKS = 8, MOVEMENT_FENCE_TICKS = 8;
    private static final ContinuousHighPowerGrowth.Curve COST =
            ContinuousHighPowerGrowth.curve("8", "4", "2", ".5", ".05");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN =
            ContinuousHighPowerGrowth.curve("60", "30", "16", "10", "1");

    private EmergencyReturnRules() {}

    public static double cpCost(BigDecimal q) { return COST.value(q).doubleValue(); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return COOLDOWN.value(q); }
    public static int cooldownTicks(BigDecimal q) {
        return ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(q));
    }

    /** Fixed fraction of certified base capacity S, never current balance or any reserve. */
    public static BigDecimal automaticCost(BigDecimal maxShield) {
        BigDecimal capacity = CombatAmounts.requirePositiveAmount(maxShield, "emergency-return base capacity");
        return CombatAmounts.requirePositiveAmount(capacity.divide(BigDecimal.TEN), "emergency-return base cost");
    }

    /** Pure all-or-nothing base proposal. This method authenticates no damage or movement. */
    public static ExactCombatState automaticSpend(ExactCombatState base) {
        if (base == null || base.isDead() || base.shieldEnabled()) return null;
        try { return base.spendShield(automaticCost(base.maxShield())); }
        catch (IllegalArgumentException | IllegalStateException invalid) { return null; }
    }

    public static boolean clock(long tick) { return tick >= 0 && tick <= Long.MAX_VALUE - MARK_LIFE_TICKS; }
    public static boolean fresh(long issued, long now) {
        return clock(issued) && clock(now) && now >= issued && now - issued < MARK_LIFE_TICKS;
    }
    /** Only -1 denotes an absent receipt; corrupted/future values remain unavailable. */
    public static boolean ready(long previous, long now, int fence) {
        return clock(now) && fence > 0 && fence <= MARK_LIFE_TICKS
                && (previous == -1 || clock(previous) && now >= previous && now - previous >= fence);
    }
    public static boolean groundedVerticalMotion(double velocity) {
        return SafeLandingProbeRules.groundedVerticalMotion(velocity);
    }
}
