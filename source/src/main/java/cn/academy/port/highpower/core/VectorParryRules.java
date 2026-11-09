package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.util.Objects;

/** NEW412: existing base S pays 1:1, followed by HP; all native limits are provisional. */
public final class VectorParryRules {
    public static final int MAX_HITS_PER_TICK = 4, RESTART_FENCE = 4, PAYMENT_PERIOD = 20, PRECISION_TICKS = SkillTuning.integer("vector_parry", "precise_ticks");
    public static final double FRONT_COSINE = .5;
    private static final ContinuousHighPowerGrowth.Curve ACTIVATION = ContinuousHighPowerGrowth.curve("2", "1", ".4", ".1", ".02");
    private static final ContinuousHighPowerGrowth.Curve SUSTAIN = ContinuousHighPowerGrowth.curve(".25", ".125", ".05", ".0125", ".0025");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN = ContinuousHighPowerGrowth.curve("10", "5", "3", "2", "1");

    private VectorParryRules() {}

    public record Result(ExactCombatState state, BigDecimal absorbed, BigDecimal prevented) {
        public Result(ExactCombatState state, BigDecimal absorbed) { this(state, absorbed, BigDecimal.ZERO); }
    }
    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }
    /** Only horizontal components are exposed, so a turn cannot replace vertical velocity. */
    public record Horizontal(double x, double z) {}

    /** Already mitigated native damage: commit this final state, without a second HP pass. */
    public static Result absorb(ExactCombatState before, BigDecimal mitigated) {
        Objects.requireNonNull(before, "before");
        mitigated = CombatAmounts.requireAmount(mitigated, "vector parry mitigated damage");
        if (before.isDead() || mitigated.signum() == 0)
            return new Result(before.withShieldEnabled(false), BigDecimal.ZERO);
        BigDecimal absorbed = mitigated.min(before.shield());
        BigDecimal healthLoss = mitigated.subtract(absorbed).min(before.health());
        return new Result(new ExactCombatState(before.health().subtract(healthLoss), before.maxHealth(),
                before.shield().subtract(absorbed), before.maxShield(), false), absorbed);
    }

    /** The native adapter has already established this skill's front/source/quota checks. */
    public static Result absorb(ExactCombatState before, BigDecimal mitigated, boolean categoryShield) {
        if (!categoryShield || before.isDead() || before.shield().signum() == 0) return absorb(before, mitigated);
        var reduction = SpecialistShieldEfficiencyRules.reduce(mitigated, SpecialistShieldEfficiencyRules.DIRECTIONAL_RETAINED);
        var paid = absorb(before, reduction.damage());
        return new Result(paid.state(), paid.absorbed(), reduction.prevented());
    }

    /** Inclusive horizontal 120-degree sector; finite, nonzero horizontal vectors are required. */
    public static boolean front(Point owner, Point facing, Point source) {
        if (owner == null || facing == null || source == null
                || !owner.finite() || !facing.finite() || !source.finite()) return false;
        double x = source.x - owner.x, z = source.z - owner.z;
        double sourceScale = Math.max(Math.abs(x), Math.abs(z));
        double facingScale = Math.max(Math.abs(facing.x), Math.abs(facing.z));
        if (!Double.isFinite(sourceScale) || sourceScale == 0 || facingScale == 0) return false;
        // Scaling before products avoids overflow and accepts even finite subnormal vectors.
        double dx = x / sourceScale, dz = z / sourceScale;
        double fx = facing.x / facingScale, fz = facing.z / facingScale;
        double dot = dx * fx + dz * fz;
        if (dot <= 0) return false;
        double left = 4 * dot * dot;
        double right = (dx * dx + dz * dz) * (fx * fx + fz * fz);
        double uncertainty = 64 * Math.ulp(Math.max(left, right));
        if (Math.abs(left - right) > uncertainty) return left > right;
        // Near the boundary compare the represented binary vectors exactly. This is not an
        // outward angular epsilon: even the next representable point outside is rejected.
        BigDecimal bx = new BigDecimal(x), bz = new BigDecimal(z);
        BigDecimal bfx = new BigDecimal(facing.x), bfz = new BigDecimal(facing.z);
        BigDecimal exactDot = bx.multiply(bfx).add(bz.multiply(bfz));
        if (exactDot.signum() <= 0) return false;
        BigDecimal sourceSquared = bx.multiply(bx).add(bz.multiply(bz));
        BigDecimal facingSquared = bfx.multiply(bfx).add(bfz.multiply(bfz));
        return exactDot.multiply(exactDot).multiply(BigDecimal.valueOf(4))
                .compareTo(sourceSquared.multiply(facingSquared)) >= 0;
    }

    /** Minecraft +Z look has rightward impulse -X; crouching selects the opposite side. */
    public static Horizontal desiredImpulse(Point facing, boolean crouching) {
        if (facing == null || !facing.finite()) return null;
        double scale = Math.max(Math.abs(facing.x), Math.abs(facing.z));
        if (scale == 0) return null;
        double x = facing.x / scale, z = facing.z / scale;
        double length = Math.hypot(x, z), side = crouching ? -1 : 1;
        return new Horizontal(-z / length * side, x / length * side);
    }

    /** Native knockback subtracts its normalized ratios; preserve strength and vertical behavior. */
    public static Horizontal knockbackRatios(Point facing, boolean crouching) {
        Horizontal impulse = desiredImpulse(facing, crouching);
        return impulse == null ? null : new Horizontal(-impulse.x, -impulse.z);
    }

    public static BigDecimal theoreticalActivationCost(BigDecimal q) { return ACTIVATION.value(q); }
    public static double activationCost(BigDecimal q) { return theoreticalActivationCost(q).doubleValue(); }
    public static BigDecimal theoreticalSustainCost(BigDecimal q) { return SUSTAIN.value(q); }
    public static double sustainCost(BigDecimal q) { return theoreticalSustainCost(q).doubleValue(); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return COOLDOWN.value(q); }
    public static int cooldownTicks(BigDecimal q) {
        return Math.max(RESTART_FENCE, ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(q)));
    }

    /** Leave room for the longest payment/cooldown scheduling addition. */
    public static boolean clock(long now) { return now >= 0 && now <= Long.MAX_VALUE - PAYMENT_PERIOD; }
    /** Ages 0, 1 and 2 only. The caller owns the once-per-hold turn token. */
    public static boolean precise(long start, long now) {
        return clock(start) && clock(now) && now >= start && now - start < PRECISION_TICKS;
    }
}
