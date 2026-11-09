package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW331: per-shot workbook damage; provisional economics and independent physical budgets. */
public final class ShortInterceptionRules {
    public static final double RANGE = SkillTuning.number("short_interception", "reach"), SELECTION_RADIUS = .75, EXIT_DISTANCE = 2, NEEDLE_SPEED = 1.5;
    public static final int MAX_ARROWS = SkillTuning.integer("short_interception", "max_targets"), SCAN_LIMIT = 64, FLIGHT_TICKS = 20,
            QUOTA_TICKS = 20, CAST_FENCE = 8, REQUEST_FENCE = 4, MARK_TICKS = SkillTuning.integer("short_interception", "duration_ticks"), HOLD_CLEAR = 6;
    private static final ContinuousHighPowerGrowth.Curve CP = ContinuousHighPowerGrowth.curve("18", "9", "4.5", "1.125", ".1125");
    private static final ContinuousHighPowerGrowth.Curve CD = ContinuousHighPowerGrowth.curve("60", "30", "16", "6", "1");
    private ShortInterceptionRules() {}
    public static double cpCost(BigDecimal q) { return CP.value(q).doubleValue(); }
    public static int cooldownTicks(BigDecimal q) { return ContinuousHighPowerGrowth.ceilCooldownTicks(CD.value(q)); }
    public static BigDecimal damage(BigDecimal d) {
        return d == null || d.signum() <= 0 ? BigDecimal.ZERO : d.multiply(new BigDecimal("1.8")).min(new BigDecimal("1e20"));
    }
    public static double offset(int index, int count) {
        if (count < 1 || count > MAX_ARROWS || index < 0 || index >= count) throw new IllegalArgumentException("Invalid interception slot");
        return (index - (count - 1) / 2.0) * .3;
    }
    public static boolean path(double toEntry, double transfer, double flight) {
        return Double.isFinite(toEntry) && Double.isFinite(transfer) && Double.isFinite(flight)
                && toEntry >= 0 && transfer >= 0 && flight > 0 && toEntry + transfer + flight <= RANGE;
    }
}
