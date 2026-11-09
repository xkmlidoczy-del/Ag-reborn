package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** NEW423: one exact half-D budget, at most three frozen objects, and provisional native economics. */
public final class YawPalmRules {
    public static final double RANGE = SkillTuning.number("yaw_palm", "reach"), RADIUS = .035, KNOCKBACK = (double).35F, ITEM_IMPULSE = .25, MAX_HORIZONTAL_SPEED = .5, MAX_LIVING_VERTICAL_SPEED = 1;
    public static final int MAX_TARGETS = SkillTuning.integer("yaw_palm", "max_targets"), SCAN_LIMIT = 64, CAST_FENCE = 4, VISUAL_TICKS = 12;
    private static final ContinuousHighPowerGrowth.Curve CP = ContinuousHighPowerGrowth.curve("10", "5", "2", ".5", ".1");
    private static final ContinuousHighPowerGrowth.Curve CD = ContinuousHighPowerGrowth.curve("10", "5", "3", "2", "1");
    private YawPalmRules() {}

    public static double cpCost(BigDecimal q) { return CP.value(q).doubleValue(); }
    public static int cooldownTicks(BigDecimal q) { return Math.max(CAST_FENCE, ContinuousHighPowerGrowth.ceilCooldownTicks(CD.value(q))); }
    public static BigDecimal damage(BigDecimal d) {
        if (d == null || d.signum() <= 0) return BigDecimal.ZERO;
        if (d.scale() < -CombatAmounts.MAX_ABSOLUTE_SCALE || d.scale() > CombatAmounts.MAX_ABSOLUTE_SCALE
                || d.precision() > CombatAmounts.MAX_DECIMAL_PRECISION) throw new IllegalArgumentException("Unbounded NEW423 damage");
        // The source D is first bounded; an unrepresentable exact half refuses rather than rounding.
        BigDecimal half = d.min(CombatAmounts.CAP).multiply(new BigDecimal(".5"));
        return CombatAmounts.requireAmount(half.stripTrailingZeros(), "NEW423 whole damage");
    }
    /** Items never divide the hostile budget; the last hostile owns the fixed decimal remainder. */
    public static List<BigDecimal> shares(BigDecimal d, int hostiles) {
        if (hostiles < 0 || hostiles > MAX_TARGETS) throw new IllegalArgumentException("Invalid NEW423 hostile count");
        if (hostiles == 0) return List.of();
        BigDecimal whole = damage(d);
        BigDecimal base = whole.divide(BigDecimal.valueOf(hostiles), CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.DOWN).stripTrailingZeros();
        if (base.signum() == 0) base = BigDecimal.ZERO;
        java.util.ArrayList<BigDecimal> result = new java.util.ArrayList<>(hostiles);
        for (int i = 0; i < hostiles - 1; i++) result.add(base);
        result.add(whole.subtract(base.multiply(BigDecimal.valueOf(hostiles - 1))));
        return List.copyOf(result);
    }
    public static Point side(float yaw, boolean crouch) {
        if (!Float.isFinite(yaw)) return null;
        double angle = Math.toRadians(yaw), sign = crouch ? 1 : -1;
        double x = sign * Math.cos(angle), z = sign * Math.sin(angle), length = Math.sqrt(x*x+z*z);
        return new Point(x/length, 0, z/length);
    }
    public static boolean velocity(Point value) {
        return value != null && value.finite();
    }
    public static boolean boundedHorizontal(Point value) {
        return velocity(value) && value.x*value.x+value.z*value.z <= MAX_HORIZONTAL_SPEED*MAX_HORIZONTAL_SPEED;
    }
    /** Reject an unaffordable additive impulse; never normalize, clamp or erase existing item momentum. */
    public static Point itemResult(Point before, Point side) {
        if (!velocity(before) || !unitSide(side)) return null;
        Point after = new Point(before.x+side.x*ITEM_IMPULSE, before.y, before.z+side.z*ITEM_IMPULSE);
        return boundedHorizontal(after) ? after : null;
    }
    /** Exact reviewed native knockback math, including native grounded Y; no invented Y reset. */
    public static Point nativeResult(Point before, Point side, double strength, double resistance, boolean grounded) {
        if (!velocity(before) || Math.abs(before.y) > MAX_LIVING_VERTICAL_SPEED || !unitSide(side) || !Double.isFinite(strength) || strength < 0 || strength > KNOCKBACK
                || !Double.isFinite(resistance) || resistance < 0 || resistance > 1) return null;
        double effective = strength*(1-resistance);
        Point after = effective <= 0 ? before : new Point(before.x/2+side.x*effective,
                grounded ? Math.min(.4D, before.y/2+effective) : before.y, before.z/2+side.z*effective);
        return boundedHorizontal(after) && Math.abs(after.y) <= MAX_LIVING_VERTICAL_SPEED ? after : null;
    }
    private static boolean unitSide(Point side) {
        return side != null && side.finite() && side.y == 0 && Math.abs(side.x*side.x+side.z*side.z-1) <= 1e-12;
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
    /** Never extend a world-coordinate-rounded endpoint beyond the actual six-metre path. */
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
