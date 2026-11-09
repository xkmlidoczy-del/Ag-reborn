package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** NEW231's real-tick limits and decimal release-time balance snapshot. */
public final class DisintegrationSweepRules {
    public static final int CHARGE_TICKS = 8, SWEEP_TICKS = 10, HOLD_TICKS = 100,
            MAX_TARGETS = SkillTuning.integer("disintegration_sweep", "max_targets"), SCAN_LIMIT = 64, MAX_CELLS = 16, ATTEMPTS_PER_TICK = 2;
    public static final double RANGE = SkillTuning.number("disintegration_sweep", "reach"), RADIUS = .035, MAX_ORIGIN_SHIFT = .25;
    private static final ContinuousHighPowerGrowth.Curve ATTACK = ContinuousHighPowerGrowth.curve("10", "10", "10", "2", ".1");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN = ContinuousHighPowerGrowth.curve("40", "40", "40", "10", "1");
    private DisintegrationSweepRules() {}

    public static BigDecimal theoreticalAttackCost(BigDecimal q) { return ATTACK.value(q); }
    public static double attackCost(BigDecimal q) { return theoreticalAttackCost(q).doubleValue(); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return COOLDOWN.value(q); }
    public static int cooldown(BigDecimal q) { return ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(q)); }
    public static double miningCost(BigDecimal q) { return BeamEtchingRules.cost(q); }
    public static BigDecimal theoreticalMiningCooldown(BigDecimal q) { return BeamEtchingRules.theoreticalCooldown(q); }
    public static int miningCooldown(BigDecimal q) { return BeamEtchingRules.cooldown(q); }
    public static BigDecimal damage(BigDecimal d) {
        return CombatAmounts.requirePositiveAmount(d, "NEW231 D").multiply(BigDecimal.TWO).min(CombatAmounts.CAP);
    }

    /**
     * The caller freezes target identities in angle/distance/entity-id order first. Every share
     * remains assigned to that identity, including zero, blocked and canceled shares. The last
     * identity receives the deterministic decimal remainder; no later event may repartition it.
     */
    public static List<BigDecimal> shares(BigDecimal total, int count) {
        total = CombatAmounts.requireAmount(total, "NEW231 whole-sweep allocation");
        if (count < 0 || count > MAX_TARGETS) throw new IllegalArgumentException("NEW231 target count");
        if (count == 0) return List.of();
        BigDecimal each = total.divide(BigDecimal.valueOf(count), CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.DOWN);
        List<BigDecimal> result = new ArrayList<>(count);
        for (int i = 0; i < count - 1; i++) result.add(CombatAmounts.requireAmount(each, "NEW231 target share"));
        result.add(CombatAmounts.requireAmount(total.subtract(each.multiply(BigDecimal.valueOf(count - 1))), "NEW231 final share"));
        return List.copyOf(result);
    }

    public record Paid(BigDecimal position, BigDecimal wholeDamage, BigDecimal attackCost,
                       BigDecimal theoreticalCooldown, int cooldownTicks,
                       double miningCost, int miningCooldownTicks) {}

    /** Evaluate once at release, before payment. Growth during the sweep cannot alter it. */
    public static Paid freeze(BigDecimal q, BigDecimal d) {
        q = ContinuousHighPowerGrowth.requirePosition(q);
        BigDecimal cd = theoreticalCooldown(q);
        return new Paid(q, damage(d), theoreticalAttackCost(q), cd,
                ContinuousHighPowerGrowth.ceilCooldownTicks(cd), miningCost(q), miningCooldown(q));
    }

    public static boolean clock(long tick) { return tick >= 0 && tick <= Long.MAX_VALUE - HOLD_TICKS - SWEEP_TICKS - 1; }
    public static boolean expired(long start, long now, int duration) {
        return duration < 0 || !clock(start) || !clock(now) || now < start || now - start >= duration;
    }
    public static boolean charged(long start, long now) {
        return clock(start) && clock(now) && now >= start && now - start >= CHARGE_TICKS && now - start < HOLD_TICKS;
    }
    public static boolean ready(long previous, long now, int interval) {
        return interval > 0 && clock(now) && (previous == -1 || clock(previous) && now >= previous && now - previous >= interval);
    }
    /** Closed angular intervals meet without gaps; a consumed identity must never be hit twice. */
    public static double angleAt(int elapsedTicks) {
        if (elapsedTicks < 0 || elapsedTicks > SWEEP_TICKS) throw new IllegalArgumentException("NEW231 sweep tick");
        if (elapsedTicks == 0) return -DisintegrationSweepGeometry.HALF_ANGLE;
        if (elapsedTicks == SWEEP_TICKS) return DisintegrationSweepGeometry.HALF_ANGLE;
        return -DisintegrationSweepGeometry.HALF_ANGLE + 2 * DisintegrationSweepGeometry.HALF_ANGLE * elapsedTicks / SWEEP_TICKS;
    }
}
