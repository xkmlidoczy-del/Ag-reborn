package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Category-mode specialist reductions after native mitigation; never spends or creates a resource. */
public final class SpecialistShieldEfficiencyRules {
    public static final BigDecimal DIRECTIONAL_RETAINED = new BigDecimal(".75");
    public static final BigDecimal LANDING_RETAINED = new BigDecimal(".5");
    public record Reduction(BigDecimal damage, BigDecimal prevented) {}
    private SpecialistShieldEfficiencyRules() {}
    public static Reduction reduce(BigDecimal damage, BigDecimal retained) {
        damage = CombatAmounts.requireAmount(damage, "specialist incoming damage");
        if (retained == null || retained.signum() <= 0 || retained.compareTo(BigDecimal.ONE) > 0)
            throw new IllegalArgumentException("specialist retained share must be positive and at most one");
        BigDecimal effective = damage.multiply(retained);
        // At the ledger's finest decimal quantum, round damage UP, so reduction never exceeds its stated share.
        if (effective.scale() > CombatAmounts.MAX_ABSOLUTE_SCALE)
            effective = effective.setScale(CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.CEILING);
        effective = CombatAmounts.requireAmount(effective.stripTrailingZeros(), "specialist effective damage");
        return new Reduction(effective, CombatAmounts.requireAmount(damage.subtract(effective), "specialist prevented damage"));
    }
}
