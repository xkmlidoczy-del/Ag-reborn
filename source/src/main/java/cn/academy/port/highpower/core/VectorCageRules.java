package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** NEW441: four real compression sweeps share one immutable whole-cast budget. */
public final class VectorCageRules {
    public static final int PULSES = 4, MAX_TARGETS = SkillTuning.integer("vector_cage", "max_targets"), SCAN_LIMIT = 64,
            PREVIEW_TICKS = SkillTuning.integer("vector_cage", "duration_ticks"), REQUEST_FENCE = 5, LIFETIME = SkillTuning.integer("vector_cage", "active_ticks"),
            WARMUP_TICKS = 6, SHRINK_TICKS = 6, PULSE_INTERVAL = 20, TARGET_FENCE = 20;
    private static final ContinuousHighPowerGrowth.Curve CP =
            ContinuousHighPowerGrowth.curve("24", "12", "6", "2", ".1");
    private static final ContinuousHighPowerGrowth.Curve CD =
            ContinuousHighPowerGrowth.curve("40", "40", "40", "40", "4");

    private VectorCageRules() {}

    /** Whole-cast CP, independent of frozen target count and pulse count. */
    public static double cpCost(BigDecimal q) { return CP.value(q).doubleValue(); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return CD.value(q); }
    public static int cooldownTicks(BigDecimal q) {
        return ContinuousHighPowerGrowth.ceilCooldownTicks(CD.value(q));
    }

    /** Crouching chooses a .5D whole budget; normal mode chooses 2.5D. */
    public static BigDecimal damage(BigDecimal d, boolean lowMode) {
        BigDecimal whole = CombatAmounts.requirePositiveAmount(d, "NEW441 D")
                .multiply(new BigDecimal(lowMode ? ".5" : "2.5")).min(CombatAmounts.CAP);
        // The minimum supported decimal unit can acquire one extra place on multiplication.
        // Discard only that unrepresentable sub-unit, never round the budget upwards.
        if (whole.scale() > CombatAmounts.MAX_ABSOLUTE_SCALE)
            whole = whole.setScale(CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.DOWN);
        return CombatAmounts.requireAmount(whole, "NEW441 whole damage");
    }

    /**
     * Fixed pulse-major 4*N slots. Only the very last original slot receives the
     * decimal remainder. A miss, refusal, dead target, or skipped tick loses its slot.
     */
    public static List<BigDecimal> shares(BigDecimal d, boolean lowMode, int targets) {
        int count = slots(targets);
        BigDecimal whole = damage(d, lowMode);
        BigDecimal ordinary = ordinaryShare(whole, count);
        var result = new ArrayList<BigDecimal>(count);
        for (int i = 0; i < count - 1; i++) result.add(ordinary);
        result.add(CombatAmounts.requireAmount(
                whole.subtract(ordinary.multiply(BigDecimal.valueOf(count - 1))), "NEW441 last share"));
        return List.copyOf(result);
    }

    /** Access to the same fixed partition when the caller has already frozen the whole budget. */
    public static BigDecimal damageShare(BigDecimal total, int pulse, int target, int targets) {
        int count = slots(targets);
        pulseAge(pulse);
        if (target < 0 || target >= targets) throw new IllegalArgumentException("Invalid NEW441 target slot");
        BigDecimal whole = CombatAmounts.requireAmount(total, "NEW441 frozen budget");
        BigDecimal ordinary = ordinaryShare(whole, count);
        return pulse == PULSES - 1 && target == targets - 1
                ? CombatAmounts.requireAmount(whole.subtract(ordinary.multiply(BigDecimal.valueOf(count - 1))),
                        "NEW441 last share")
                : ordinary;
    }

    public static int pulseAge(int pulse) {
        if (pulse < 0 || pulse >= PULSES) throw new IllegalArgumentException("Invalid NEW441 pulse");
        return WARMUP_TICKS + pulse * PULSE_INTERVAL;
    }

    /** Exact starts only. A late tick never catches up an earlier pulse. */
    public static int pulseIndex(long age) {
        int pulse = sweepIndex(age);
        return pulse >= 0 && age == pulseAge(pulse) ? pulse : -1;
    }

    /** Active real-tick windows: 6..11, 26..31, 46..51, and 66..71. */
    public static int sweepIndex(long age) {
        if (age < WARMUP_TICKS || age >= LIFETIME) return -1;
        long sinceWarmup = age - WARMUP_TICKS;
        int pulse = (int) (sinceWarmup / PULSE_INTERVAL);
        return pulse < PULSES && sinceWarmup % PULSE_INTERVAL < SHRINK_TICKS ? pulse : -1;
    }

    public static int sweepStep(long age) {
        int pulse = sweepIndex(age);
        return pulse < 0 ? -1 : (int) (age - pulseAge(pulse));
    }

    public static double fromHalfWidth(long age) { return stepHalfWidth(requireStep(age)); }
    public static double toHalfWidth(long age) { return stepHalfWidth(requireStep(age) + 1); }

    /** Visual end-of-tick position; idle phases retain the preceding inner face. */
    public static double halfWidth(long age) {
        if (age < 0 || age >= LIFETIME) throw new IllegalArgumentException("Invalid NEW441 age");
        if (age < WARMUP_TICKS) return VectorCageGeometry.HALF_WIDTH;
        return sweepIndex(age) < 0 ? VectorCageGeometry.MIN_HALF_WIDTH : toHalfWidth(age);
    }

    public static boolean clock(long tick) { return tick >= 0 && tick <= Long.MAX_VALUE - 12000; }
    public static boolean ready(long before, long now, int interval) {
        return interval > 0 && clock(now)
                && (before == -1 || clock(before) && now >= before && now - before >= interval);
    }

    private static int slots(int targets) {
        if (targets < 1 || targets > MAX_TARGETS) throw new IllegalArgumentException("Invalid NEW441 target count");
        return PULSES * targets;
    }
    private static BigDecimal ordinaryShare(BigDecimal whole, int count) {
        return CombatAmounts.requireAmount(whole.divide(BigDecimal.valueOf(count),
                CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.DOWN), "NEW441 share");
    }
    private static int requireStep(long age) {
        int step = sweepStep(age);
        if (step < 0) throw new IllegalArgumentException("NEW441 age is outside a real compression sweep");
        return step;
    }
    private static double stepHalfWidth(int step) {
        return VectorCageGeometry.HALF_WIDTH - (VectorCageGeometry.HALF_WIDTH
                - VectorCageGeometry.MIN_HALF_WIDTH) * step / SHRINK_TICKS;
    }
}
