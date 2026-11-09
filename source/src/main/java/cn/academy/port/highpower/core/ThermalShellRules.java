package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** NEW222: one shared-shield debit and a bounded, conserved outward damage budget. */
public final class ThermalShellRules {
    public static final int MAX_TARGETS = SkillTuning.integer("thermal_shell", "max_targets"), SCAN_LIMIT = 64, PULSE_INTERVAL = 15, PAYMENT_PERIOD = 20, MASTERY_INTERVAL = 20;
    public static final double RANGE = SkillTuning.number("thermal_shell", "radius"), SHELL_RADIUS = .68;
    private static final BigDecimal PULSE_FACTOR = new BigDecimal(".05");
    private static final ContinuousHighPowerGrowth.Curve ACTIVATION = ContinuousHighPowerGrowth.curve("2", "2", "1", ".2", ".02");
    private static final ContinuousHighPowerGrowth.Curve SUSTAIN = ContinuousHighPowerGrowth.curve(".25", ".25", ".1", ".02", ".002");
    private static final ContinuousHighPowerGrowth.Curve PULSE = ContinuousHighPowerGrowth.curve("1", "1", ".5", ".1", ".01");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN = ContinuousHighPowerGrowth.curve("10", "10", "6", "3", "1");
    private ThermalShellRules() {}
    public record Result(ExactCombatState state, BigDecimal absorbed) {}
    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }
    /** Input has already passed native shield/armor/effect/absorption decisions. */
    public static Result absorb(ExactCombatState before, BigDecimal mitigated) {
        Objects.requireNonNull(before, "before"); CombatAmounts.requireAmount(mitigated, "thermal shell mitigated damage");
        if (before.isDead() || mitigated.signum() == 0) return new Result(before.withShieldEnabled(false), BigDecimal.ZERO);
        BigDecimal absorbed = mitigated.min(before.shield());
        BigDecimal healthLoss = mitigated.subtract(absorbed).min(before.health());
        return new Result(new ExactCombatState(before.health().subtract(healthLoss), before.maxHealth(),
                before.shield().subtract(absorbed), before.maxShield(), false), absorbed);
    }
    /** This is one TOTAL budget; adding targets never multiplies outgoing damage. */
    public static BigDecimal pulseBudget(BigDecimal growthDamage, BigDecimal committedAbsorption) {
        CombatAmounts.requireAmount(growthDamage, "growth damage"); CombatAmounts.requireAmount(committedAbsorption, "committed absorption");
        return CombatAmounts.requireAmount(growthDamage.multiply(PULSE_FACTOR).min(committedAbsorption), "thermal pulse budget");
    }
    /** Equal finite-decimal shares; the final frozen slot owns the deterministic remainder. */
    public static List<BigDecimal> shares(BigDecimal total, int targets) {
        total = CombatAmounts.requireAmount(total, "thermal pulse total");
        if (targets < 1 || targets > MAX_TARGETS) throw new IllegalArgumentException("thermal target count");
        int scale = Math.min(CombatAmounts.MAX_ABSOLUTE_SCALE, Math.max(40, total.scale()));
        BigDecimal regular = total.divide(BigDecimal.valueOf(targets), scale, RoundingMode.DOWN).stripTrailingZeros();
        List<BigDecimal> result = new ArrayList<>(targets);
        for (int i = 1; i < targets; i++) result.add(regular);
        result.add(total.subtract(regular.multiply(BigDecimal.valueOf(targets - 1L))));
        return List.copyOf(result);
    }
    public static boolean inside(Point point, Point center) {
        return point != null && center != null && point.finite() && center.finite()
                && Math.hypot(Math.hypot(point.x - center.x, point.z - center.z), point.y - center.y) <= RANGE;
    }
    public static boolean clock(long now) { return now >= 0 && now <= Long.MAX_VALUE - PAYMENT_PERIOD; }
    public static boolean ready(long until, long now) { return clock(now) && until >= 0 && now >= until; }
    public static double activationCost(BigDecimal q) { return ACTIVATION.value(q).doubleValue(); }
    public static double sustainCost(BigDecimal q) { return SUSTAIN.value(q).doubleValue(); }
    public static double pulseCost(BigDecimal q) { return PULSE.value(q).doubleValue(); }
    public static BigDecimal cooldown(BigDecimal q) { return COOLDOWN.value(q); }
    public static int cooldownTicks(BigDecimal q) { return ContinuousHighPowerGrowth.ceilCooldownTicks(cooldown(q)); }
}
