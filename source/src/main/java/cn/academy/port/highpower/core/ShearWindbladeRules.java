package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** NEW421: one whole 1.5D budget and two fronts, never two independent damage budgets. */
public final class ShearWindbladeRules {
    public static final int MAX_TARGETS = SkillTuning.integer("shear_windblade", "max_targets"), SCAN_LIMIT = 64, CAST_FENCE = 4,
            SECOND_DELAY = 2, LIFETIME = SkillTuning.integer("shear_windblade", "duration_ticks"), BLADES = 2;
    public static final double STEP = SkillTuning.number("shear_windblade", "speed");
    private static final ContinuousHighPowerGrowth.Curve CP = ContinuousHighPowerGrowth.curve("8", "8", "3", ".5", ".05");
    private static final ContinuousHighPowerGrowth.Curve CD = ContinuousHighPowerGrowth.curve("30", "30", "15", "5", "1");
    private ShearWindbladeRules() {}
    public static double cpCost(BigDecimal q) { return CP.value(q).doubleValue(); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return CD.value(q); }
    /** These anchors are native ticks. The independent physical fence is not a UI cooldown. */
    public static int cooldownTicks(BigDecimal q) { return ContinuousHighPowerGrowth.ceilCooldownTicks(CD.value(q)); }
    public static BigDecimal damage(BigDecimal d) {
        BigDecimal whole = CombatAmounts.requirePositiveAmount(d, "NEW421 D").multiply(new BigDecimal("1.5")).min(CombatAmounts.CAP);
        // A legal minimum-scale D can gain one decimal place at 1.5x. Discard only
        // that unrepresentable sub-unit, never enlarge the supported exact budget.
        if (whole.scale() > CombatAmounts.MAX_ABSOLUTE_SCALE) whole = whole.setScale(CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.DOWN);
        return CombatAmounts.requireAmount(whole, "NEW421 whole damage");
    }
    /** Fixed exact partition. Only the final original slot receives the remainder; none is reassigned. */
    public static List<BigDecimal> shares(BigDecimal d, int count) {
        if (count < 0 || count > MAX_TARGETS) throw new IllegalArgumentException("Invalid NEW421 target count");
        if (count == 0) return List.of();
        BigDecimal whole = damage(d);
        BigDecimal ordinary = whole.divide(BigDecimal.valueOf(count), CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.DOWN);
        ordinary = ordinary.signum() == 0 ? BigDecimal.ZERO : ordinary.stripTrailingZeros();
        List<BigDecimal> result = new ArrayList<>(count);
        for (int i = 0; i < count - 1; i++) result.add(ordinary);
        result.add(whole.subtract(ordinary.multiply(BigDecimal.valueOf(count - 1))));
        return List.copyOf(result);
    }
    public static boolean clock(long tick) { return tick >= 0 && tick <= Long.MAX_VALUE - 12000; }
    public static boolean ready(long before, long now, int interval) {
        return clock(now) && (before == -1 || clock(before) && now >= before && now - before >= interval);
    }
}
