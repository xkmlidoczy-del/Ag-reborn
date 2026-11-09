package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * NEW422's provisional exact base-S transactions and native balance limits.
 * The adapter owns the toggle, grounded/front/event/resistance checks, movement modifier,
 * admission count and hold generation. A proposal is not permission to apply a native impulse.
 */
public final class InertialAnchorRules {
    public static final int MAX_HITS_PER_TICK = 4, RESTART_FENCE = 4, PAYMENT_PERIOD = 20;
    private static final BigDecimal KNOCKBACK_FEE_RATIO = new BigDecimal(".01");

    private InertialAnchorRules() {}

    public record Result(ExactCombatState state, BigDecimal absorbed, BigDecimal prevented) {
        public Result(ExactCombatState state, BigDecimal absorbed) { this(state, absorbed, BigDecimal.ZERO); }
    }

    /**
     * An immutable, all-or-nothing debit paired with half this native call's base strength.
     * This does not assert that the final velocity, resistance or vertical lift is halved.
     */
    public static final class KnockbackPlan {
        private final ExactCombatState before, state;
        private final BigDecimal fee;
        private final double originalStrength, reducedStrength;

        private KnockbackPlan(ExactCombatState before, ExactCombatState state, BigDecimal fee,
                              double originalStrength, double reducedStrength) {
            this.before = before;
            this.state = state;
            this.fee = fee;
            this.originalStrength = originalStrength;
            this.reducedStrength = reducedStrength;
        }

        public ExactCombatState before() { return before; }
        public ExactCombatState state() { return state; }
        public BigDecimal fee() { return fee; }
        public double originalStrength() { return originalStrength; }
        public double reducedStrength() { return reducedStrength; }

        /**
         * Native reads may reconstruct equal immutable snapshots. The adapter must separately
         * check its hold generation, reentrancy token and actual native field witnesses.
         */
        public boolean matches(ExactCombatState current, double strength) {
            return before.equals(current)
                    && Double.doubleToLongBits(originalStrength) == Double.doubleToLongBits(strength);
        }
    }

    /** Exact one percent of maximum base S; an unsupported decimal quantum is never rounded. */
    public static BigDecimal knockbackFee(BigDecimal maxShield) {
        BigDecimal maximum = CombatAmounts.requireAmount(maxShield, "inertial anchor maximum base S");
        return CombatAmounts.requireAmount(maximum.multiply(KNOCKBACK_FEE_RATIO), "inertial anchor fee");
    }

    /**
     * Called only after the uncanceled native event and a positive resistance-adjusted impulse
     * have been established. Refusal changes nothing: no partial fee, reserve borrowing or fill.
     * A positive exact fee and representable positive half-strength are required for admission.
     */
    public static KnockbackPlan planKnockback(ExactCombatState before, double nativeStrength) {
        Objects.requireNonNull(before, "before");
        if (before.isDead() || !Double.isFinite(nativeStrength) || nativeStrength <= 0) return null;
        double reduced = nativeStrength * .5;
        // Odd subnormal quanta cannot be halved exactly; never round one into a free/extra effect.
        if (!Double.isFinite(reduced) || reduced <= 0 || reduced * 2 != nativeStrength) return null;
        final BigDecimal fee;
        try {
            fee = knockbackFee(before.maxShield());
        } catch (IllegalArgumentException unsupportedFee) {
            return null;
        }
        if (fee.signum() == 0 || before.shield().compareTo(fee) < 0) return null;
        return new KnockbackPlan(before, before.spendShield(fee), fee, nativeStrength, reduced);
    }

    /**
     * Already mitigated grounded fall damage uses existing base S 1:1, then HP exactly once.
     * Commit the complete resulting state; the native adapter must not run a second HP pass.
     */
    public static Result absorbFall(ExactCombatState before, BigDecimal mitigated) {
        VectorParryRules.Result absorbed = VectorParryRules.absorb(before, mitigated);
        return new Result(absorbed.state(), absorbed.absorbed());
    }

    /** Only a certified normal grounded landing gets this category-mode benefit. */
    public static Result absorbFall(ExactCombatState before, BigDecimal mitigated, boolean categoryShield) {
        if (!categoryShield || before.isDead() || before.shield().signum() == 0) return absorbFall(before, mitigated);
        var reduction = SpecialistShieldEfficiencyRules.reduce(mitigated, SpecialistShieldEfficiencyRules.LANDING_RETAINED);
        var paid = VectorParryRules.absorb(before, reduction.damage());
        return new Result(paid.state(), paid.absorbed(), reduction.prevented());
    }

    /** Reuses NEW412's exact represented-vector horizontal 120-degree boundary. */
    public static boolean front(VectorParryRules.Point owner, VectorParryRules.Point facing,
                                VectorParryRules.Point source) {
        return VectorParryRules.front(owner, facing, source);
    }

    public static BigDecimal theoreticalActivationCost(BigDecimal q) { return VectorParryRules.theoreticalActivationCost(q); }
    public static double activationCost(BigDecimal q) { return VectorParryRules.activationCost(q); }
    public static BigDecimal theoreticalSustainCost(BigDecimal q) { return VectorParryRules.theoreticalSustainCost(q); }
    public static double sustainCost(BigDecimal q) { return VectorParryRules.sustainCost(q); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return VectorParryRules.theoreticalCooldown(q); }
    public static int cooldownTicks(BigDecimal q) { return VectorParryRules.cooldownTicks(q); }
    public static boolean clock(long now) { return VectorParryRules.clock(now); }
}
