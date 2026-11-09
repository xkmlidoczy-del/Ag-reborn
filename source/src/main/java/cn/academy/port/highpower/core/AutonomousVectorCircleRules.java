package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.util.Objects;

/** NEW452: an eight-metre, three-dimensional forward sector using only existing 2S balances. */
public final class AutonomousVectorCircleRules {
    public static final int WINDOW_TICKS = 20, MAX_ABSORPTIONS = SkillTuning.integer("autonomous_vector_circle", "max_targets"), MAX_HITS_PER_TICK = 4;
    public static final int PAYMENT_PERIOD = VectorParryRules.PAYMENT_PERIOD;
    public static final int RESTART_FENCE = VectorParryRules.RESTART_FENCE;
    public static final double RANGE = SkillTuning.number("autonomous_vector_circle", "reach");
    public static final double FRONT_COSINE = Math.cos(Math.toRadians(80));
    private static final BigDecimal FOUR = BigDecimal.valueOf(4);
    private static final BigDecimal SIX = BigDecimal.valueOf(6);
    private static final BigDecimal EIGHT = BigDecimal.valueOf(8);
    private static final BigDecimal RANGE_SQUARED = BigDecimal.valueOf(RANGE * RANGE);

    private AutonomousVectorCircleRules() {}

    public record Result(ExactCombatState state, SharedShieldReserveShares shares, BigDecimal absorbed) {}

    /**
     * Native mitigation has already run. Debit base S, lower reserve, then upper reserve;
     * the returned state already contains the one residual-HP settlement. No refill,
     * redistribution, capacity increase or second generic shield pass is authorized.
     */
    public static Result absorb(ExactCombatState before, SharedShieldReserveShares shares, BigDecimal mitigated) {
        Objects.requireNonNull(before, "before");
        shares = Objects.requireNonNull(shares, "shared shield reserve").validate(before);
        mitigated = CombatAmounts.requireAmount(mitigated, "autonomous vector circle mitigated damage");
        if (before.isDead() || mitigated.signum() == 0)
            return new Result(before.withShieldEnabled(false), shares, BigDecimal.ZERO);
        BigDecimal basePaid = mitigated.min(before.shield());
        BigDecimal reservePaid = mitigated.subtract(basePaid).min(shares.total());
        BigDecimal absorbed = basePaid.add(reservePaid);
        BigDecimal healthPaid = mitigated.subtract(absorbed).min(before.health());
        ExactCombatState next = new ExactCombatState(before.health().subtract(healthPaid), before.maxHealth(),
                before.shield().subtract(basePaid), before.maxShield(), false);
        return new Result(next, shares.debit(reservePaid, true).validate(next), absorbed);
    }

    /** Inclusive eight-metre sphere; boundary comparisons use the exact represented world coordinates. */
    public static boolean inRange(VectorParryRules.Point owner, VectorParryRules.Point source) {
        if (!finite(owner) || !finite(source)) return false;
        double x = source.x() - owner.x(), y = source.y() - owner.y(), z = source.z() - owner.z();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > RANGE || Math.abs(y) > RANGE || Math.abs(z) > RANGE) return false;
        double squared = x * x + y * y + z * z;
        double uncertainty = 64 * Math.ulp(Math.max(squared, RANGE * RANGE));
        if (Math.abs(squared - RANGE * RANGE) > uncertainty) return squared < RANGE * RANGE;
        return square(difference(source.x(), owner.x())).add(square(difference(source.y(), owner.y())))
                .add(square(difference(source.z(), owner.z()))).compareTo(RANGE_SQUARED) <= 0;
    }

    /** Inclusive three-dimensional 160-degree sector from the owner toward a melee origin. */
    public static boolean front(VectorParryRules.Point owner, VectorParryRules.Point facing,
                                VectorParryRules.Point source) {
        if (!finite(owner) || !finite(facing) || !finite(source)) return false;
        VectorParryRules.Point direction = new VectorParryRules.Point(source.x() - owner.x(),
                source.y() - owner.y(), source.z() - owner.z());
        if (!direction.finite()) return false;
        int ordinary = ordinaryFront(facing, direction);
        if (ordinary != 0) return ordinary > 0;
        return exactFront(new BigDecimal(facing.x()), new BigDecimal(facing.y()), new BigDecimal(facing.z()),
                difference(source.x(), owner.x()), difference(source.y(), owner.y()), difference(source.z(), owner.z()));
    }

    /**
     * For a projectile this direction points back along its actual swept segment toward
     * the incoming source (A-B). Its position relative to the owner cannot replace it.
     */
    public static boolean frontDirection(VectorParryRules.Point facing, VectorParryRules.Point incomingTowardSource) {
        if (!finite(facing) || !finite(incomingTowardSource)) return false;
        int ordinary = ordinaryFront(facing, incomingTowardSource);
        if (ordinary != 0) return ordinary > 0;
        return exactFront(new BigDecimal(facing.x()), new BigDecimal(facing.y()), new BigDecimal(facing.z()),
                new BigDecimal(incomingTowardSource.x()), new BigDecimal(incomingTowardSource.y()),
                new BigDecimal(incomingTowardSource.z()));
    }

    /** -1 outside, 1 inside; 0 requests an exact comparison, never an angular tolerance. */
    private static int ordinaryFront(VectorParryRules.Point facing, VectorParryRules.Point direction) {
        double facingScale = Math.max(Math.max(Math.abs(facing.x()), Math.abs(facing.y())), Math.abs(facing.z()));
        double directionScale = Math.max(Math.max(Math.abs(direction.x()), Math.abs(direction.y())), Math.abs(direction.z()));
        if (facingScale == 0 || directionScale == 0) return -1;
        double fx = facing.x() / facingScale, fy = facing.y() / facingScale, fz = facing.z() / facingScale;
        double dx = direction.x() / directionScale, dy = direction.y() / directionScale, dz = direction.z() / directionScale;
        double dot = fx * dx + fy * dy + fz * dz;
        if (dot <= 0) return -1;
        double left = dot * dot;
        double right = (fx * fx + fy * fy + fz * fz) * (dx * dx + dy * dy + dz * dz)
                * FRONT_COSINE * FRONT_COSINE;
        double uncertainty = 64 * Math.ulp(Math.max(left, right));
        if (Math.abs(left - right) <= uncertainty) return 0;
        return left > right ? 1 : -1;
    }

    private static boolean exactFront(BigDecimal fx, BigDecimal fy, BigDecimal fz,
                                      BigDecimal dx, BigDecimal dy, BigDecimal dz) {
        BigDecimal dot = fx.multiply(dx).add(fy.multiply(dy)).add(fz.multiply(dz));
        if (dot.signum() <= 0) return false;
        BigDecimal numerator = square(dot);
        BigDecimal denominator = square(fx).add(square(fy)).add(square(fz))
                .multiply(square(dx).add(square(dy)).add(square(dz)));
        if (numerator.multiply(FOUR).compareTo(denominator) >= 0) return true;
        // With 0 < u = cos²(theta) < 1/4, the sign of
        // 64u³ - 96u² + 36u - 1 uniquely distinguishes theta <= 80 degrees.
        // This follows from cos(3*80 degrees) = -1/2 and avoids any rounded
        // trigonometric boundary, epsilon, division, square root or MathContext.
        BigDecimal factor = denominator.multiply(SIX).subtract(numerator.multiply(EIGHT));
        return numerator.multiply(square(factor)).compareTo(denominator.multiply(square(denominator))) >= 0;
    }

    private static boolean finite(VectorParryRules.Point point) { return point != null && point.finite(); }
    private static BigDecimal square(BigDecimal value) { return value.multiply(value); }
    private static BigDecimal difference(double left, double right) { return new BigDecimal(left).subtract(new BigDecimal(right)); }

    /**
     * Eight remaining native-tick clocks, newest first, with zero for unused slots.
     * Persist across holds, reconnects, dimensions and categories. Only genuine player
     * ticks may age them; wall clocks and game-time jumps cannot renew this quota.
     * The controller separately owns the physical-tick admissions fence because a hit
     * before Post retains 20 remaining ticks through that Post, while its old tick's
     * admission count must not block the next tick. No timestamp/reset API exists here.
     */
    public record Quota(int firstRemaining, int secondRemaining, int thirdRemaining, int fourthRemaining,
                        int fifthRemaining, int sixthRemaining, int seventhRemaining, int eighthRemaining) {
        /** Only for genuinely new state, never a fallback for corrupt persisted clocks. */
        public static final Quota EMPTY = new Quota(0, 0, 0, 0, 0, 0, 0, 0);

        public Quota {
            if (firstRemaining > WINDOW_TICKS || eighthRemaining < 0
                    || firstRemaining < secondRemaining || secondRemaining < thirdRemaining
                    || thirdRemaining < fourthRemaining || fourthRemaining < fifthRemaining
                    || fifthRemaining < sixthRemaining || sixthRemaining < seventhRemaining
                    || seventhRemaining < eighthRemaining)
                throw new IllegalArgumentException("Invalid autonomous vector circle quota clocks");
        }

        public static Quota restore(int[] remainingTicks) {
            Objects.requireNonNull(remainingTicks, "autonomous vector circle quota clocks");
            if (remainingTicks.length != MAX_ABSORPTIONS)
                throw new IllegalArgumentException("Autonomous vector circle requires exactly eight clocks");
            return new Quota(remainingTicks[0], remainingTicks[1], remainingTicks[2], remainingTicks[3],
                    remainingTicks[4], remainingTicks[5], remainingTicks[6], remainingTicks[7]);
        }

        /** Defensive serialization snapshot; the caller cannot mutate this quota. */
        public int[] remainingTicks() {
            return new int[] {firstRemaining, secondRemaining, thirdRemaining, fourthRemaining,
                    fifthRemaining, sixthRemaining, seventhRemaining, eighthRemaining};
        }

        /** Rolling-window availability only; admission must also check the physical tick fence. */
        public boolean available() { return eighthRemaining == 0; }

        public boolean available(int admissionsThisPhysicalTick) {
            checkAdmissions(admissionsThisPhysicalTick);
            return available() && admissionsThisPhysicalTick < MAX_HITS_PER_TICK;
        }

        /** Call exactly once per actual native player tick, including while inactive. */
        public Quota nativeTick() {
            if (firstRemaining == 0) return this;
            return new Quota(age(firstRemaining), age(secondRemaining), age(thirdRemaining), age(fourthRemaining),
                    age(fifthRemaining), age(sixthRemaining), age(seventhRemaining), age(eighthRemaining));
        }

        /**
         * Count only positive exact absorption. The supplied admissions count is the
         * controller's count BEFORE this hit. Commit both this result and the incremented
         * physical-tick fence before callbacks; neither can be reset by a new hold.
         */
        public Quota afterAbsorption(BigDecimal absorbed, int admissionsThisPhysicalTick) {
            absorbed = CombatAmounts.requireAmount(absorbed, "autonomous vector circle absorbed damage");
            checkAdmissions(admissionsThisPhysicalTick);
            if (absorbed.signum() == 0) return this;
            if (!available(admissionsThisPhysicalTick))
                throw new IllegalStateException("Autonomous vector circle quota exhausted");
            return new Quota(WINDOW_TICKS, firstRemaining, secondRemaining, thirdRemaining,
                    fourthRemaining, fifthRemaining, sixthRemaining, seventhRemaining);
        }

        private static void checkAdmissions(int count) {
            if (count < 0 || count > MAX_HITS_PER_TICK)
                throw new IllegalArgumentException("Invalid autonomous vector circle physical-tick admissions");
        }
        private static int age(int remaining) { return Math.max(0, remaining - 1); }
    }

    public static BigDecimal theoreticalActivationCost(BigDecimal q) { return VectorParryRules.theoreticalActivationCost(q); }
    public static double activationCost(BigDecimal q) { return VectorParryRules.activationCost(q); }
    public static BigDecimal theoreticalSustainCost(BigDecimal q) { return VectorParryRules.theoreticalSustainCost(q); }
    public static double sustainCost(BigDecimal q) { return VectorParryRules.sustainCost(q); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return VectorParryRules.theoreticalCooldown(q); }
    public static int cooldownTicks(BigDecimal q) { return VectorParryRules.cooldownTicks(q); }
}
