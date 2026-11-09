package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.Objects;

/** NEW152's two literal, independent tranches. No efficiency multiplier or change to base S. */
public final class MagneticDomainShieldRules {
    private MagneticDomainShieldRules() {}
    public record Result(ExactCombatState state, BigDecimal reserve, BigDecimal absorbed) {}
    public record Recovery(ExactCombatState state, BigDecimal reserve) {}
    public record Spend(ExactCombatState state, BigDecimal reserve) {}

    public static BigDecimal validate(ExactCombatState base, BigDecimal reserve) {
        Objects.requireNonNull(base, "base");
        BigDecimal checked = CombatAmounts.requireAmount(reserve, "domain reserve");
        if (base.maxShield().add(base.maxShield()).compareTo(CombatAmounts.CAP) > 0
                || checked.compareTo(base.maxShield()) > 0)
            throw new IllegalArgumentException("Domain tranches exceed certified capacity");
        return checked;
    }

    /** Native armor, Resistance, enchantments and absorption have already run exactly once. */
    public static Result absorb(ExactCombatState base, BigDecimal reserve, BigDecimal damage) {
        reserve = validate(base, reserve);
        damage = CombatAmounts.requireAmount(damage, "domain damage");
        if (base.isDead() || damage.signum() == 0)
            return new Result(base.withShieldEnabled(false), reserve, BigDecimal.ZERO);
        BigDecimal basePaid = damage.min(base.shield());
        BigDecimal reservePaid = damage.subtract(basePaid).min(reserve);
        BigDecimal absorbed = basePaid.add(reservePaid);
        BigDecimal healthPaid = damage.subtract(absorbed).min(base.health());
        return new Result(new ExactCombatState(base.health().subtract(healthPaid), base.maxHealth(),
                base.shield().subtract(basePaid), base.maxShield(), false), reserve.subtract(reservePaid), absorbed);
    }

    /** A single pre-existing budget fills base first; never a second reserve regeneration budget. */
    public static Recovery recover(ExactCombatState base, BigDecimal reserve, BigDecimal budget) {
        reserve = validate(base, reserve);
        budget = CombatAmounts.requireAmount(budget, "domain recovery budget");
        if (base.isDead()) return new Recovery(base.withShieldEnabled(false), reserve);
        BigDecimal baseRecovered = budget.min(base.maxShield().subtract(base.shield()));
        BigDecimal reserveRecovered = budget.subtract(baseRecovered).min(base.maxShield().subtract(reserve));
        return new Recovery(base.regenerateShield(baseRecovered).withShieldEnabled(false), reserve.add(reserveRecovered));
    }

    /** All-or-nothing explicit domain-only cost, never HP damage or an ordinary skill's pool. */
    public static Spend spend(ExactCombatState base, BigDecimal reserve, BigDecimal cost) {
        reserve = validate(base, reserve);
        cost = CombatAmounts.requireAmount(cost, "domain cost");
        if (base.isDead() || cost.compareTo(base.shield().add(reserve)) > 0) return null;
        BigDecimal basePaid = cost.min(base.shield());
        return new Spend(base.spendShield(basePaid).withShieldEnabled(false), reserve.subtract(cost.subtract(basePaid)));
    }
}
