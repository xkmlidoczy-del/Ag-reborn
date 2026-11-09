package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.math.MathContext;

/** Pure NEW-122 transaction. Native mitigation/absorption has already run exactly once. */
public final class GroundingReturnRules {
    public static final int DURATION_TICKS = SkillTuning.integer("grounding_return", "duration_ticks"), PRECISE_TICKS = SkillTuning.integer("grounding_return", "precise_ticks"), COOLDOWN_TICKS = 60;
    public static final double MOVEMENT_TOLERANCE = .025;
    private static final BigDecimal REFUND_FRACTION = new BigDecimal("0.20");
    private GroundingReturnRules() {}

    public static boolean active(long started, long now) { return now >= started && now - started < DURATION_TICKS; }
    public static boolean precise(long started, long now) { return now >= started && now - started < PRECISE_TICKS; }
    public record Result(ExactCombatState state, BigDecimal absorbed, BigDecimal refund,
                         BigDecimal spent, boolean precisionUsed, double knockbackFactor) {}

    /** Refund follows absorption, never protects the remainder of this same hit or heals HP/CP. */
    public static Result absorb(ExactCombatState before, BigDecimal damage, BigDecimal capturedCapacity,
                                BigDecimal spent, boolean precisionUsed, boolean precise) {
        CombatAmounts.requireAmount(damage, "grounding damage");
        CombatAmounts.requireAmount(capturedCapacity, "captured grounding capacity");
        CombatAmounts.requireAmount(spent, "grounding activation spend");
        if (before.isDead() || damage.signum() == 0) return new Result(before.withShieldEnabled(false),
                BigDecimal.ZERO, BigDecimal.ZERO, spent, precisionUsed, 1);
        BigDecimal absorbed = damage.min(before.shield());
        BigDecimal totalSpent = spent.add(absorbed);
        // Only this capped entitlement is needed after the one precision decision; never grows without bound.
        BigDecimal trackedSpent = totalSpent.min(capturedCapacity);
        boolean usePrecision = !precisionUsed && precise && absorbed.signum() > 0;
        BigDecimal refund = usePrecision ? capturedCapacity.multiply(REFUND_FRACTION).min(totalSpent) : BigDecimal.ZERO;
        BigDecimal shield = before.shield().subtract(absorbed);
        refund = refund.min(before.maxShield().subtract(shield));
        BigDecimal remainder = damage.subtract(absorbed);
        var after = new ExactCombatState(before.health().subtract(remainder.min(before.health())), before.maxHealth(),
                shield.add(refund), before.maxShield(), false);
        double factor = absorbed.signum() == 0 ? 1 : 1 - .5 * absorbed.divide(damage, MathContext.DECIMAL128).doubleValue();
        return new Result(after, absorbed, refund, trackedSpent, precisionUsed || usePrecision, Math.max(.5, Math.min(1, factor)));
    }
}
