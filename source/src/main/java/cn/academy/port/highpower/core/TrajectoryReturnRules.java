package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW431 exact per-arrow 2D, one whole-cast debit and independent physical limits. */
public final class TrajectoryReturnRules {
    public static final double RANGE = SkillTuning.number("trajectory_return", "reach"), SELECTION_RADIUS = .75, AIR_HAMMER_RADIUS = .035, MAX_SPEED = 8;
    public static final int MAX_ARROWS = SkillTuning.integer("trajectory_return", "max_targets"), SCAN_LIMIT = 64, FLIGHT_TICKS = 20, QUOTA_TICKS = 20, CAST_FENCE = 4;
    private static final ContinuousHighPowerGrowth.Curve CP = ContinuousHighPowerGrowth.curve("18", "9", "4.5", "1.125", ".1125");
    private static final ContinuousHighPowerGrowth.Curve CD = ContinuousHighPowerGrowth.curve("60", "30", "16", "6", "1");
    private TrajectoryReturnRules() {}
    public static double cpCost(BigDecimal q) { return CP.value(q).doubleValue(); }
    public static int cooldownTicks(BigDecimal q) { return ContinuousHighPowerGrowth.ceilCooldownTicks(CD.value(q)); }
    public static BigDecimal damage(BigDecimal d) {
        if (d == null || d.signum() <= 0) return BigDecimal.ZERO;
        if (d.scale() < -CombatAmounts.MAX_ABSOLUTE_SCALE || d.scale() > CombatAmounts.MAX_ABSOLUTE_SCALE
                || d.precision() > CombatAmounts.MAX_DECIMAL_PRECISION) throw new IllegalArgumentException("Unbounded NEW431 damage");
        return CombatAmounts.requireAmount(d.multiply(BigDecimal.TWO).min(CombatAmounts.CAP), "NEW431 per-arrow damage");
    }
    public static boolean flight(double traveled) { return Double.isFinite(traveled) && traveled >= 0 && traveled <= RANGE; }
    public static boolean clock(long now) { return now >= 0 && now <= Long.MAX_VALUE - 12000; }
    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(x) <= 30_000_000 && Math.abs(y) <= 30_000_000 && Math.abs(z) <= 30_000_000; }
        public double distanceSquared(Point p) { return (x-p.x)*(x-p.x) + (y-p.y)*(y-p.y) + (z-p.z)*(z-p.z); }
    }
    public static boolean within(Point start, Point end) {
        return start != null && end != null && start.finite() && end.finite() && start.distanceSquared(end) <= RANGE*RANGE;
    }
    public static boolean corridor(Point start, Point unit, Point candidate) {
        if (start == null || unit == null || candidate == null || !start.finite() || !unit.finite() || !candidate.finite()
                || !within(start,candidate)) return false;
        double length = unit.x*unit.x + unit.y*unit.y + unit.z*unit.z;
        if (Math.abs(length-1) > 1e-6) return false;
        double x=candidate.x-start.x, y=candidate.y-start.y, z=candidate.z-start.z;
        double along=x*unit.x+y*unit.y+z*unit.z;
        if (along < 0 || along > RANGE) return false;
        x-=along*unit.x; y-=along*unit.y; z-=along*unit.z;
        return x*x+y*y+z*z <= SELECTION_RADIUS*SELECTION_RADIUS;
    }
    /** Floating world-coordinate rounding must never lengthen the approved ray. */
    public static Point endpoint(Point start, Point unit) {
        if (start == null || unit == null || !start.finite() || !unit.finite()
                || Math.abs(unit.x*unit.x+unit.y*unit.y+unit.z*unit.z-1)>1e-6) return null;
        Point end = new Point(start.x+unit.x*RANGE,start.y+unit.y*RANGE,start.z+unit.z*RANGE);
        for (int i=0;i<=8;i++) {
            if (within(start,end) && start.distanceSquared(end)>0) return end;
            end=new Point(Math.nextAfter(end.x,start.x),Math.nextAfter(end.y,start.y),Math.nextAfter(end.z,start.z));
        }
        return null;
    }
}
