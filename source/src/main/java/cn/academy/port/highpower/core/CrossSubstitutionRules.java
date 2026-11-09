package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW321 shared economics, exact self-swap bounds and immutable three-needle schedule. */
public final class CrossSubstitutionRules {
    public static final double RANGE = SkillTuning.number("cross_substitution", "reach"), ORIGIN_HEIGHT = 1.2, SPEED = 1.5;
    public static final double SWAP_NEAR_HORIZONTAL = .75, SWAP_NEAR_VERTICAL = .125;
    public static final int PREPARATION_TICKS = SkillTuning.integer("cross_substitution", "duration_ticks"), FLIGHT_TICKS = 20, CAST_FENCE = 8,
            REQUEST_FENCE = 4, NEEDLES = SkillTuning.integer("cross_substitution", "max_targets"), LAUNCH_INTERVAL = 4;
    private static final ContinuousHighPowerGrowth.Curve CP =
            ContinuousHighPowerGrowth.curve("12", "6", "3", ".75", ".075");
    private static final ContinuousHighPowerGrowth.Curve CD =
            ContinuousHighPowerGrowth.curve("60", "30", "16", "6", "1");
    private CrossSubstitutionRules() {}
    public static double cpCost(BigDecimal q) { return CP.value(q).doubleValue(); }
    public static double needleCpCost(BigDecimal q) { return cpCost(q) / NEEDLES; }
    /** Freeze total once; the last committed needle owns only the fixed rounding residual. */
    public static double cpShare(double total, int slot) {
        launchTick(slot);
        if (!Double.isFinite(total) || total <= 0) throw new IllegalArgumentException("Invalid cross CP total");
        double third = total / NEEDLES;
        return slot == 2 ? total - (third + third) : third;
    }
    public static int cooldownTicks(BigDecimal q) { return ContinuousHighPowerGrowth.ceilCooldownTicks(CD.value(q)); }
    public static BigDecimal damage(BigDecimal d) {
        return d == null || d.signum() <= 0 ? BigDecimal.ZERO
                : d.multiply(new BigDecimal(".5")).min(new BigDecimal("1e20"));
    }
    public static int launchTick(int slot) {
        if (slot < 0 || slot >= NEEDLES) throw new IllegalArgumentException("Invalid cross needle slot");
        return slot * LAUNCH_INTERVAL;
    }
    public static boolean usesA(int slot) {
        launchTick(slot); return slot != 1;
    }
    public static boolean path(double casterToOrigin, double actualFlight) {
        return Double.isFinite(casterToOrigin) && Double.isFinite(actualFlight)
                && casterToOrigin >= 0 && actualFlight >= 0 && casterToOrigin + actualFlight <= RANGE;
    }
    /** Feet must be near exactly one mark; callers decide exclusivity across A and B. */
    public static boolean nearSwapPoint(double horizontalDistance, double verticalOffset) {
        return Double.isFinite(horizontalDistance) && Double.isFinite(verticalOffset)
                && horizontalDistance >= 0 && horizontalDistance <= SWAP_NEAR_HORIZONTAL
                && Math.abs(verticalOffset) <= SWAP_NEAR_VERTICAL;
    }
    /** A self-swap pays the whole CP curve once; its exact destination is at most 32m away. */
    public static boolean swapDistance(double distance) {
        return Double.isFinite(distance) && distance >= 0 && distance <= RANGE;
    }
}
