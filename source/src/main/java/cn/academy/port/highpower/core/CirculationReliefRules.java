package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.util.Objects;

/** NEW432: exact base-S relief in the two horizontal side sectors. */
public final class CirculationReliefRules {
    public static final int WINDOW_TICKS = 20, MAX_ABSORPTIONS = 4;
    public static final int PAYMENT_PERIOD = VectorParryRules.PAYMENT_PERIOD;
    public static final int RESTART_FENCE = VectorParryRules.RESTART_FENCE;
    public static final double GROUND_SINK_RANGE = SkillTuning.number("circulation_relief", "reach");

    private CirculationReliefRules() {}

    /** Existing base S pays 1:1, then HP; the returned state already applies residual damage. */
    public static VectorParryRules.Result absorb(ExactCombatState before, BigDecimal mitigated) {
        return VectorParryRules.absorb(before, mitigated);
    }

    /** Same exact reduction as parry, admitted only by this skill's separate side/ground/quota capture. */
    public static VectorParryRules.Result absorb(ExactCombatState before, BigDecimal mitigated, boolean categoryShield) {
        return VectorParryRules.absorb(before, mitigated, categoryShield);
    }

    /**
     * Inclusive horizontal angles 60..120 and 240..300 relative to forward. Ground sink
     * range is a separate native probe constraint, not a distance limit on the attacker.
     */
    public static boolean side(VectorParryRules.Point owner, VectorParryRules.Point facing,
                               VectorParryRules.Point source) {
        if (owner == null || facing == null || source == null
                || !owner.finite() || !facing.finite() || !source.finite()) return false;
        double x = source.x() - owner.x(), z = source.z() - owner.z();
        double sourceScale = Math.max(Math.abs(x), Math.abs(z));
        double facingScale = Math.max(Math.abs(facing.x()), Math.abs(facing.z()));
        if (!Double.isFinite(sourceScale) || sourceScale == 0 || facingScale == 0) return false;
        // Normalize before multiplying so finite subnormal and very large vectors work.
        double dx = x / sourceScale, dz = z / sourceScale;
        double fx = facing.x() / facingScale, fz = facing.z() / facingScale;
        double dot = dx * fx + dz * fz;
        double left = 4 * dot * dot;
        double right = (dx * dx + dz * dz) * (fx * fx + fz * fz);
        double uncertainty = 64 * Math.ulp(Math.max(left, right));
        if (Math.abs(left - right) > uncertainty) return left < right;
        // Compare represented binary values exactly near either boundary. No angular
        // epsilon may admit the next representable vector on the front/rear side.
        BigDecimal bx = new BigDecimal(x), bz = new BigDecimal(z);
        BigDecimal bfx = new BigDecimal(facing.x()), bfz = new BigDecimal(facing.z());
        BigDecimal exactDot = bx.multiply(bfx).add(bz.multiply(bfz));
        BigDecimal sourceSquared = bx.multiply(bx).add(bz.multiply(bz));
        BigDecimal facingSquared = bfx.multiply(bfx).add(bfz.multiply(bfz));
        return exactDot.multiply(exactDot).multiply(BigDecimal.valueOf(4))
                .compareTo(sourceSquared.multiply(facingSquared)) <= 0;
    }

    /**
     * Four remaining native-tick clocks, newest first, with zero for an unused slot.
     * A positive absorption occupies a slot for exactly the next 20 actual player ticks.
     * Persist this record across activation, logout, dimension, and category changes.
     * There is deliberately no wall-clock, native-clock-delta, or reset operation.
     */
    public record Quota(int firstRemaining, int secondRemaining, int thirdRemaining, int fourthRemaining) {
        /** Only use for genuinely new state, never as a fallback for malformed saved state. */
        public static final Quota EMPTY = new Quota(0, 0, 0, 0);

        public Quota {
            if (firstRemaining > WINDOW_TICKS || fourthRemaining < 0
                    || firstRemaining < secondRemaining || secondRemaining < thirdRemaining
                    || thirdRemaining < fourthRemaining) {
                throw new IllegalArgumentException("Invalid circulation relief quota clocks");
            }
        }

        /** Strict canonical persistence model: corrupt or absent data cannot mint quota. */
        public static Quota restore(int[] remainingTicks) {
            Objects.requireNonNull(remainingTicks, "circulation relief quota clocks");
            if (remainingTicks.length != MAX_ABSORPTIONS) {
                throw new IllegalArgumentException("Circulation relief quota requires exactly four clocks");
            }
            return new Quota(remainingTicks[0], remainingTicks[1], remainingTicks[2], remainingTicks[3]);
        }

        /** Defensive serialization snapshot; callers cannot modify this immutable quota. */
        public int[] remainingTicks() {
            return new int[] {firstRemaining, secondRemaining, thirdRemaining, fourthRemaining};
        }

        public boolean available() { return fourthRemaining == 0; }

        /** Call exactly once per actual native player tick, including while inactive. */
        public Quota nativeTick() {
            if (firstRemaining == 0) return this;
            return new Quota(age(firstRemaining), age(secondRemaining),
                    age(thirdRemaining), age(fourthRemaining));
        }

        /** Charge only positive exact absorption; commit this result before callbacks. */
        public Quota afterAbsorption(BigDecimal absorbed) {
            absorbed = CombatAmounts.requireAmount(absorbed, "circulation relief absorbed damage");
            if (absorbed.signum() == 0) return this;
            if (!available()) throw new IllegalStateException("Circulation relief quota exhausted");
            return new Quota(WINDOW_TICKS, firstRemaining, secondRemaining, thirdRemaining);
        }

        private static int age(int remaining) { return Math.max(0, remaining - 1); }
    }

    public static BigDecimal theoreticalActivationCost(BigDecimal q) {
        return VectorParryRules.theoreticalActivationCost(q);
    }
    public static double activationCost(BigDecimal q) { return VectorParryRules.activationCost(q); }
    public static BigDecimal theoreticalSustainCost(BigDecimal q) {
        return VectorParryRules.theoreticalSustainCost(q);
    }
    public static double sustainCost(BigDecimal q) { return VectorParryRules.sustainCost(q); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) {
        return VectorParryRules.theoreticalCooldown(q);
    }
    public static int cooldownTicks(BigDecimal q) { return VectorParryRules.cooldownTicks(q); }
}
