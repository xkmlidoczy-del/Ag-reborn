package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.util.Objects;

/** NEW142 pure shared-pool transaction and explicitly provisional geometry/balance bounds. */
public final class PotentialBufferRules {
    public static final int MAX_TARGETS = SkillTuning.integer("potential_buffer", "max_targets"), SCAN_LIMIT = 64, PULSE_INTERVAL = 20, MASTERY_INTERVAL = 20;
    public static final double RANGE = SkillTuning.number("potential_buffer", "radius"), FRONT_COSINE = .5, KNOCKBACK = .35;
    private static final ContinuousHighPowerGrowth.Curve COST = ContinuousHighPowerGrowth.curve("3", "3", "3", "3", "0.1");
    private PotentialBufferRules() {}
    public record Result(ExactCombatState state, BigDecimal absorbed) {}
    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }
    /** Already mitigated damage only: no refund, capacity edit, second absorption or HP recovery. */
    public static Result absorb(ExactCombatState before, BigDecimal damage) {
        Objects.requireNonNull(before, "before"); CombatAmounts.requireAmount(damage, "potential buffer damage");
        if (before.isDead() || damage.signum() == 0) return new Result(before.withShieldEnabled(false), BigDecimal.ZERO);
        BigDecimal absorbed = damage.min(before.shield());
        BigDecimal remaining = damage.subtract(absorbed);
        return new Result(new ExactCombatState(before.health().subtract(remaining.min(before.health())), before.maxHealth(),
                before.shield().subtract(absorbed), before.maxShield(), false), absorbed);
    }
    /** Horizontal 120-degree front sector, including the boundary, with no originless/vertical shortcut. */
    public static boolean front(Point owner, Point facing, Point source) {
        if (owner == null || facing == null || source == null || !owner.finite() || !facing.finite() || !source.finite()) return false;
        double x = source.x - owner.x, z = source.z - owner.z;
        if (!Double.isFinite(x) || !Double.isFinite(z)) return false;
        double length = Math.hypot(x, z), look = Math.hypot(facing.x, facing.z);
        if (!Double.isFinite(length) || !Double.isFinite(look) || length <= 1e-6 || look <= 1e-6) return false;
        return x / length * (facing.x / look) + z / length * (facing.z / look) >= FRONT_COSINE;
    }
    public static boolean inside(Point point, Point center) {
        if (point == null || center == null || !point.finite() || !center.finite()) return false;
        return Math.hypot(Math.hypot(point.x - center.x, point.z - center.z), point.y - center.y) <= RANGE;
    }
    public static boolean clock(long now) { return now >= 0 && now <= Long.MAX_VALUE - PULSE_INTERVAL; }
    public static boolean ready(long until, long now) { return clock(now) && until >= 0 && now >= until; }
    public static double pulseCost(BigDecimal position) { return COST.value(position).doubleValue(); }
}
