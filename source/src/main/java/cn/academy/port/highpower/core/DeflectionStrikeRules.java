package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** NEW411: one exact 1D budget, at most two frozen bodies, and provisional native economics. */
public final class DeflectionStrikeRules {
    public static final double RANGE = SkillTuning.number("deflection_strike", "reach"), RADIUS = .035, KNOCKBACK = .35;
    public static final int MAX_TARGETS = SkillTuning.integer("deflection_strike", "max_targets"), SCAN_LIMIT = 64, CAST_FENCE = 4, VISUAL_TICKS = 12;
    private static final ContinuousHighPowerGrowth.Curve CP = ContinuousHighPowerGrowth.curve("10", "5", "2", ".5", ".1");
    private static final ContinuousHighPowerGrowth.Curve CD = ContinuousHighPowerGrowth.curve("10", "5", "3", "2", "1");
    private DeflectionStrikeRules() {}

    public static double cpCost(BigDecimal q) { return CP.value(q).doubleValue(); }
    public static int cooldownTicks(BigDecimal q) { return Math.max(CAST_FENCE, ContinuousHighPowerGrowth.ceilCooldownTicks(CD.value(q))); }
    public static BigDecimal damage(BigDecimal d) {
        if (d == null || d.signum() <= 0) return BigDecimal.ZERO;
        if (d.scale() < -CombatAmounts.MAX_ABSOLUTE_SCALE || d.scale() > CombatAmounts.MAX_ABSOLUTE_SCALE
                || d.precision() > CombatAmounts.MAX_DECIMAL_PRECISION) throw new IllegalArgumentException("Unbounded NEW411 damage");
        return CombatAmounts.requireAmount(d.min(CombatAmounts.CAP), "NEW411 whole damage");
    }
    /** Only the last slot owns any decimal remainder. A discarded slot is never redistributed. */
    public static List<BigDecimal> shares(BigDecimal d, int count, boolean crouch) {
        if (count < 0 || count > MAX_TARGETS) throw new IllegalArgumentException("Invalid NEW411 target count");
        if (count == 0) return List.of();
        BigDecimal whole = crouch ? BigDecimal.ZERO : damage(d);
        if (count == 1) return List.of(whole);
        BigDecimal first = whole.divide(BigDecimal.valueOf(2), CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.DOWN).stripTrailingZeros();
        return List.of(first.signum() == 0 ? BigDecimal.ZERO : first, whole.subtract(first));
    }
    public static boolean clock(long now) { return now >= 0 && now <= Long.MAX_VALUE - 12000; }
    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(x) <= 30_000_000 && Math.abs(y) <= 30_000_000 && Math.abs(z) <= 30_000_000; }
        public double distanceSquared(Point p) { return (x-p.x)*(x-p.x) + (y-p.y)*(y-p.y) + (z-p.z)*(z-p.z); }
    }
    public static boolean within(Point start, Point end) {
        return start != null && end != null && start.finite() && end.finite() && start.distanceSquared(end) <= RANGE * RANGE;
    }
    /** Never extend a world-coordinate-rounded endpoint beyond the actual eight-metre path. */
    public static Point endpoint(Point start, Point unit) {
        if (start == null || unit == null || !start.finite() || !unit.finite()) return null;
        double length = unit.x*unit.x + unit.y*unit.y + unit.z*unit.z;
        if (Math.abs(length - 1) > 1e-6) return null;
        Point end = new Point(start.x + unit.x*RANGE, start.y + unit.y*RANGE, start.z + unit.z*RANGE);
        for (int i=0; i<=8; i++) {
            if (within(start, end) && start.distanceSquared(end) > 0) return end;
            end = new Point(Math.nextAfter(end.x, start.x), Math.nextAfter(end.y, start.y), Math.nextAfter(end.z, start.z));
        }
        return null;
    }
}
