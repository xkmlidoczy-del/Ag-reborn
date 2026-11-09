package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.util.Objects;

/** NEW442: a complete 1:1 debit from base S and the fixed lower reserve share. */
public final class MultiAxisReflectionRules {
    public static final int FACET_COUNT = 4, MAX_REFLECTIONS = SkillTuning.integer("multi_axis_reflection", "max_targets"), QUOTA_TICKS = 20;
    public static final int PAYMENT_PERIOD = VectorParryRules.PAYMENT_PERIOD;
    public static final int RESTART_FENCE = VectorParryRules.RESTART_FENCE;
    public static final double MAX_OWNER_DISTANCE = SkillTuning.number("multi_axis_reflection", "reach"), MAX_ARROW_SPEED = 8, MAX_ARROW_BASE_DAMAGE = 64;

    private MultiAxisReflectionRules() {}

    public record Result(ExactCombatState state, SharedShieldReserveShares shares,
                         BigDecimal spent, boolean accepted) {}

    public static BigDecimal capacity(ExactCombatState base) {
        return DissipationCurtainShieldRules.capacity(base);
    }

    public static BigDecimal available(ExactCombatState base, SharedShieldReserveShares shares) {
        return DissipationCurtainShieldRules.available(base, shares);
    }

    /** Refusal never spends a partial amount, changes HP, or touches the upper share. */
    public static Result reflect(ExactCombatState base, SharedShieldReserveShares shares, BigDecimal incoming) {
        Result refused = new Result(base, shares, BigDecimal.ZERO, false);
        if (base == null || shares == null || incoming == null || base.isDead()) return refused;
        try {
            incoming = CombatAmounts.requireAmount(incoming, "multi-axis native arrow damage");
            if (incoming.signum() == 0 || incoming.compareTo(available(base, shares)) > 0) return refused;
            var paid = DissipationCurtainShieldRules.absorb(base, shares, incoming, false);
            if (paid.remaining().signum() != 0 || paid.spent().compareTo(incoming) != 0)
                return refused;
            return new Result(paid.state(), paid.shares(), paid.spent(), true);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return refused;
        }
    }

    /** Pinned AbstractArrow.onHitEntity noncritical amount; the weapon branch casts baseDamage too. */
    public static BigDecimal nativeArrowDamage(double speed, double baseDamage, boolean weaponPresent) {
        if (!Double.isFinite(speed) || !Double.isFinite(baseDamage) || speed < 0 || baseDamage < 0)
            throw new IllegalArgumentException("Invalid native arrow damage inputs");
        if (speed == 0 || baseDamage == 0) return BigDecimal.ZERO;
        double nativeSpeed = (double) (float) speed;
        double nativeBase = weaponPresent ? (double) (float) baseDamage : baseDamage;
        double product = nativeSpeed * nativeBase;
        double clamped = Math.max(0, Math.min((double) Integer.MAX_VALUE, product));
        return BigDecimal.valueOf((long) Math.ceil(clamped));
    }

    /** Four newest-first remaining clocks, plus a persisted one-per-facet physical-tick fence. */
    public record Quota(int firstRemaining, int secondRemaining, int thirdRemaining,
                        int fourthRemaining, int usedFacetMask) {
        public static final Quota EMPTY = new Quota(0, 0, 0, 0, 0);

        public Quota {
            if (firstRemaining > QUOTA_TICKS || fourthRemaining < 0
                    || firstRemaining < secondRemaining || secondRemaining < thirdRemaining
                    || thirdRemaining < fourthRemaining || usedFacetMask < 0 || usedFacetMask > 15)
                throw new IllegalArgumentException("Invalid multi-axis quota clocks or facet mask");
        }

        public static Quota empty() { return EMPTY; }

        /** Malformed persistence must refuse activation, never fall back to an empty quota. */
        public static Quota restore(int[] remainingTicks, int usedFacetMask) {
            Objects.requireNonNull(remainingTicks, "multi-axis quota clocks");
            if (remainingTicks.length != MAX_REFLECTIONS)
                throw new IllegalArgumentException("Multi-axis quota requires four clocks");
            return new Quota(remainingTicks[0], remainingTicks[1], remainingTicks[2], remainingTicks[3], usedFacetMask);
        }

        public int[] remainingTicks() {
            return new int[] {firstRemaining, secondRemaining, thirdRemaining, fourthRemaining};
        }

        public boolean canConsume(int facet) {
            return facet >= 0 && facet < FACET_COUNT && fourthRemaining == 0
                    && (usedFacetMask & (1 << facet)) == 0;
        }

        public Quota consume(int facet) {
            if (!canConsume(facet)) throw new IllegalStateException("Multi-axis reflection quota unavailable");
            return new Quota(QUOTA_TICKS, firstRemaining, secondRemaining, thirdRemaining,
                    usedFacetMask | (1 << facet));
        }

        /** Only the controller's guarded real player tick may call this, including while inactive. */
        public Quota nativeTick() {
            return new Quota(age(firstRemaining), age(secondRemaining), age(thirdRemaining), age(fourthRemaining), 0);
        }

        private static int age(int value) { return Math.max(0, value - 1); }
    }

    public static BigDecimal theoreticalActivationCost(BigDecimal q) { return VectorParryRules.theoreticalActivationCost(q); }
    public static double activationCost(BigDecimal q) { return VectorParryRules.activationCost(q); }
    public static BigDecimal theoreticalSustainCost(BigDecimal q) { return VectorParryRules.theoreticalSustainCost(q); }
    public static double sustainCost(BigDecimal q) { return VectorParryRules.sustainCost(q); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return VectorParryRules.theoreticalCooldown(q); }
    public static int cooldownTicks(BigDecimal q) { return VectorParryRules.cooldownTicks(q); }
    public static boolean clock(long now) { return VectorParryRules.clock(now); }
}
