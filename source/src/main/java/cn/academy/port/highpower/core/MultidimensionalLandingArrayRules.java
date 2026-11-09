package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** NEW351 closed six-anchor physical array; none of these limits apply to another skill. */
public final class MultidimensionalLandingArrayRules {
    public static final double RANGE = SkillTuning.number("multidimensional_landing_array", "reach"), ORIGIN_HEIGHT = 1.2, SPEED = 1.6;
    public static final double CONE_HALF_ANGLE_DEGREES = 12;
    public static final int POINTS = 6, NEEDLES = 6, MAX_TARGETS = SkillTuning.integer("multidimensional_landing_array", "max_targets"), MAX_NEEDLES = 16,
            CANDIDATE_LIMIT = 64, MAX_IN_FLIGHT = 6;
    public static final int PREPARATION_TICKS = SkillTuning.integer("multidimensional_landing_array", "duration_ticks"), LAUNCH_INTERVAL = 4, CAST_FENCE = 20,
            REQUEST_FENCE = 4, FLIGHT_TICKS = 40, ROUND_TICKS = 160;
    private static final ContinuousHighPowerGrowth.Curve CP = ContinuousHighPowerGrowth.curve("24", "12", "6", "1.5", ".15");
    private static final ContinuousHighPowerGrowth.Curve CD = ContinuousHighPowerGrowth.curve("120", "60", "30", "10", "1");
    private MultidimensionalLandingArrayRules() {}

    public static double cpCost(BigDecimal q) { return CP.value(q).doubleValue(); }
    public static int cooldownTicks(BigDecimal q) { return ContinuousHighPowerGrowth.ceilCooldownTicks(CD.value(q)); }
    public static BigDecimal damageTotal(BigDecimal d) {
        return CombatAmounts.requirePositiveAmount(d, "NEW351 D").multiply(BigDecimal.valueOf(4)).min(CombatAmounts.CAP);
    }
    /** Frozen whole-round budget is partitioned once; a failed original loses its share. */
    public static BigDecimal damageShare(BigDecimal total, int slot, int needles) {
        launchTick(slot, needles);
        total = CombatAmounts.requirePositiveAmount(total, "NEW351 budget");
        BigDecimal share = total.divide(BigDecimal.valueOf(needles), CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.DOWN);
        // Decimal underflow cannot create zero-value authorized attack slots.
        CombatAmounts.requirePositiveAmount(share, "NEW351 share");
        return slot == needles - 1 ? total.subtract(share.multiply(BigDecimal.valueOf(needles - 1))) : share;
    }
    public static BigDecimal damageShare(BigDecimal total, int slot) { return damageShare(total, slot, NEEDLES); }
    public static double cpShare(double total, int slot, int needles) {
        launchTick(slot, needles);
        if (!Double.isFinite(total) || total <= 0) throw new IllegalArgumentException("NEW351 CP budget");
        double share = total / needles;
        if (!Double.isFinite(share) || share <= 0) throw new IllegalArgumentException("NEW351 CP share");
        if (slot != needles - 1) return share;
        double previous = 0;
        for (int i = 0; i < needles - 1; i++) previous += share;
        double residual = total - previous;
        if (!Double.isFinite(residual) || residual <= 0) throw new IllegalArgumentException("NEW351 CP residual");
        return residual;
    }
    public static double cpShare(double total, int slot) { return cpShare(total, slot, NEEDLES); }
    public static int needleCount(int targets) {
        if (targets < 1 || targets > MAX_TARGETS) throw new IllegalArgumentException("NEW351 target count");
        return Math.max(NEEDLES, targets);
    }
    public static int launchTick(int slot, int needles) {
        if (needles < NEEDLES || needles > MAX_NEEDLES || slot < 0 || slot >= needles)
            throw new IllegalArgumentException("NEW351 slot");
        return slot * LAUNCH_INTERVAL;
    }
    public static int launchTick(int slot) { return launchTick(slot, NEEDLES); }
    public static int sourceSlot(int slot, int targets) { launchTick(slot, needleCount(targets)); return slot % POINTS; }
    public static int targetSlot(int slot, int targets) { launchTick(slot, needleCount(targets)); return slot % targets; }
    /** Cumulative path includes eye-to-mark plus lift to the real origin, then actual travelled distance. */
    public static boolean path(double sourceDistance, double actualFlight) {
        return Double.isFinite(sourceDistance) && Double.isFinite(actualFlight)
                && sourceDistance >= 0 && actualFlight >= 0 && sourceDistance + actualFlight <= RANGE;
    }
    public static boolean path(double eyeToMark, double markToOrigin, double actualFlight) {
        return Double.isFinite(eyeToMark) && Double.isFinite(markToOrigin) && eyeToMark >= 0 && markToOrigin >= 0
                && path(eyeToMark + markToOrigin, actualFlight);
    }
    public static boolean inCone(double cosine, double distance) {
        return Double.isFinite(cosine) && Double.isFinite(distance) && distance > 0 && distance <= RANGE
                && cosine >= Math.cos(Math.toRadians(CONE_HALF_ANGLE_DEGREES)) && cosine <= 1.000000000000001;
    }
    /** A stalled scheduler schedules its next original from now, never emits a catch-up batch. */
    public static boolean emissionDue(long now, long roundStart, long previousEmission, int inFlight) {
        return now >= roundStart && now - roundStart < ROUND_TICKS && inFlight >= 0 && inFlight < MAX_IN_FLIGHT
                && (previousEmission == Long.MIN_VALUE || now >= previousEmission && now - previousEmission >= LAUNCH_INTERVAL);
    }
}
