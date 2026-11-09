package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** NEW341 three fixed sources and up to eight frozen targets. Prices and timings are provisional. */
public final class SpatialPincerCorridorRules {
    public static final double RANGE = SkillTuning.number("spatial_pincer_corridor", "reach"), ORIGIN_HEIGHT = 1.2, SPEED = 1.6;
    public static final double ENTRANCE_HORIZONTAL = .75, ENTRANCE_VERTICAL = .125;
    public static final int POINTS = 3, MAX_TARGETS = SkillTuning.integer("spatial_pincer_corridor", "max_targets"), MAX_NEEDLES = 8, CANDIDATE_LIMIT = 64;
    public static final double CONE_HALF_ANGLE_DEGREES = 12;
    public static final int PREPARATION_TICKS = SkillTuning.integer("spatial_pincer_corridor", "duration_ticks"), FLIGHT_TICKS = 40, CAST_FENCE = 8,
            REQUEST_FENCE = 4, NEEDLES = 3, LAUNCH_INTERVAL = 4, CLEAR_HOLD_TICKS = 6,
            INVITATION_TICKS = 200;
    private static final ContinuousHighPowerGrowth.Curve CP = ContinuousHighPowerGrowth.curve("16", "8", "4", "1", ".1");
    private static final ContinuousHighPowerGrowth.Curve CD = ContinuousHighPowerGrowth.curve("80", "40", "20", "8", "1");
    private static final ContinuousHighPowerGrowth.Curve TRANSFER_CP = ContinuousHighPowerGrowth.curve("8", "4", "2", ".5", ".05");
    private static final ContinuousHighPowerGrowth.Curve TRANSFER_CD = ContinuousHighPowerGrowth.curve("60", "30", "16", "10", "1");
    private SpatialPincerCorridorRules() {}
    public static double cpCost(BigDecimal q) { return CP.value(q).doubleValue(); }
    public static int cooldownTicks(BigDecimal q) { return ContinuousHighPowerGrowth.ceilCooldownTicks(CD.value(q)); }
    public static double transferCpCost(BigDecimal q) { return TRANSFER_CP.value(q).doubleValue(); }
    public static int transferCooldownTicks(BigDecimal q) { return ContinuousHighPowerGrowth.ceilCooldownTicks(TRANSFER_CD.value(q)); }
    public static BigDecimal damageTotal(BigDecimal d) {
        return CombatAmounts.requirePositiveAmount(d, "NEW341 D").multiply(new BigDecimal("2.8")).min(CombatAmounts.CAP);
    }
    /** Legacy three-shot overload; geometric marks and maximum custody slots are separate bounds. */
    public static BigDecimal damageShare(BigDecimal total, int slot) { return damageShare(total, slot, POINTS); }
    /** Fixed shares conserve the frozen whole budget; a missed share is never reassigned. */
    public static BigDecimal damageShare(BigDecimal total, int slot, int needles) {
        launchTick(slot, needles); total = CombatAmounts.requireAmount(total, "NEW341 budget");
        BigDecimal share = total.divide(BigDecimal.valueOf(needles), CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.DOWN);
        return slot == needles - 1 ? total.subtract(share.multiply(BigDecimal.valueOf(needles - 1))) : share;
    }
    public static double cpShare(double total, int slot) { return cpShare(total, slot, POINTS); }
    /** The last slot owns the exact residual of the same ordered additions used by native payments. */
    public static double cpShare(double total, int slot, int needles) {
        launchTick(slot, needles);
        if (!Double.isFinite(total) || total <= 0) throw new IllegalArgumentException("NEW341 CP budget");
        double share = total / needles;
        if (slot != needles - 1) return share;
        double previous = 0;
        for (int i = 0; i < needles - 1; i++) previous += share;
        return total - previous;
    }
    public static int launchTick(int slot) { return launchTick(slot, POINTS); }
    public static int launchTick(int slot, int needles) {
        if (needles < POINTS || needles > MAX_NEEDLES || slot < 0 || slot >= needles)
            throw new IllegalArgumentException("NEW341 slot");
        return slot * LAUNCH_INTERVAL;
    }
    public static int needleCount(int targets) {
        if (targets < 1 || targets > MAX_TARGETS) throw new IllegalArgumentException("NEW341 target count");
        return Math.max(POINTS, targets);
    }
    public static int sourceSlot(int slot, int targets) { launchTick(slot, needleCount(targets)); return slot % POINTS; }
    public static int targetSlot(int slot, int targets) { launchTick(slot, needleCount(targets)); return slot % targets; }
    /** Dot product is from normalized server look and normalized eye-to-target-center direction. */
    public static boolean inCone(double cosine, double distance) {
        return Double.isFinite(cosine) && Double.isFinite(distance) && distance > 0 && distance <= RANGE
                && cosine >= Math.cos(Math.toRadians(CONE_HALF_ANGLE_DEGREES)) && cosine <= 1.000000000000001;
    }
    public static boolean path(double casterToOrigin, double actualFlight) {
        return Double.isFinite(casterToOrigin) && Double.isFinite(actualFlight)
                && casterToOrigin >= 0 && actualFlight >= 0 && casterToOrigin + actualFlight <= RANGE;
    }
    public static boolean nearEntrance(double horizontalDistance, double verticalOffset) {
        return Double.isFinite(horizontalDistance) && Double.isFinite(verticalOffset)
                && horizontalDistance >= 0 && horizontalDistance <= ENTRANCE_HORIZONTAL
                && Math.abs(verticalOffset) <= ENTRANCE_VERTICAL;
    }
}
