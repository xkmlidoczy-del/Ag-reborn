package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW-132 prototype bounds. Eight metres is powered travel, never a forced stop of real inertia. */
public final class MagneticGroundStepRules {
    public static final double RANGE = SkillTuning.number("magnetic_ground_step", "base_distance"), ACCELERATION = .08, MAX_SPEED = SkillTuning.number("magnetic_ground_step", "max_speed"), MAX_OBSERVED_STEP = .8;
    public static final double MAX_ARROW_SPEED = 3, DEFLECTION_ANGLE = Math.PI / 4;
    public static final int MAX_TICKS = 40, QUOTA_TICKS = 40, SCAN_LIMIT = 16, MAX_CONTROLLERS = 1;
    private static final ContinuousHighPowerGrowth.Curve COST = ContinuousHighPowerGrowth.curve("8", "8", "8", "4", "1");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN = ContinuousHighPowerGrowth.curve("40", "40", "40", "10", "1");
    public record Vector(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public double horizontalLength() { return Math.hypot(x, z); }
    }
    private MagneticGroundStepRules() {}
    public static double cpCost(BigDecimal position) { return COST.value(position).doubleValue(); }
    public static BigDecimal theoreticalCooldown(BigDecimal position) { return COOLDOWN.value(position); }
    public static int cooldownTicks(BigDecimal position) { return ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(position)); }
    public static BigDecimal shieldFee(BigDecimal maximum) {
        return CombatAmounts.requirePositiveAmount(maximum, "step shield capacity").multiply(new BigDecimal("0.01"));
    }
    /** Powered travel of one cast: the original RANGE scaled by the growth position (see MobilityScale). Speed limits are unchanged. */
    public static double range(BigDecimal position) { return RANGE * MobilityScale.reach(position); }
    /** Largest travel any level can receive; the client uses it only as a sanity bound, the server budget is authoritative. */
    public static final double MAX_RANGE = RANGE * MobilityScale.MAX_REACH;
    public static boolean expired(long started, long now) { return now < started || now - started >= MAX_TICKS; }
    /** Add only a forward horizontal component; never clamp/rewrite pre-existing external inertia. */
    public static Vector assist(Vector velocity, Vector direction, double travelled) {
        if (velocity == null || direction == null || !velocity.finite() || !direction.finite()
                || !Double.isFinite(travelled) || travelled < 0 || travelled >= RANGE
                || velocity.y > 1e-7 || velocity.y < -.15 || Math.abs(direction.y) > 1e-10
                || Math.abs(direction.horizontalLength() - 1) > 1e-8 || velocity.horizontalLength() > MAX_SPEED + 1e-10) return null;
        double remaining = RANGE - travelled;
        // Reaching the bound retires without braking. Existing momentum belongs to native physics.
        if (velocity.horizontalLength() > remaining + 1e-10) return null;
        double dot = velocity.x * direction.x + velocity.z * direction.z;
        if (dot < 0) return null; // Even small opposing knockback is not cancelled by the controller.
        double maximum = Math.min(MAX_SPEED, remaining);
        double discriminant = dot * dot + maximum * maximum - velocity.horizontalLength() * velocity.horizontalLength();
        double addition = Math.min(ACCELERATION, Math.max(0, -dot + Math.sqrt(Math.max(0, discriminant))));
        return new Vector(velocity.x + direction.x * addition, velocity.y, velocity.z + direction.z * addition);
    }
    /** Exactly one horizontal side turn; keep vertical velocity and total projectile speed unchanged. */
    public static Vector deflect(Vector velocity, int side) {
        if (velocity == null || !velocity.finite() || side != -1 && side != 1
                || velocity.horizontalLength() < .03 || Math.hypot(velocity.horizontalLength(), velocity.y) > MAX_ARROW_SPEED) return null;
        double c = Math.cos(DEFLECTION_ANGLE), s = Math.sin(DEFLECTION_ANGLE) * side;
        return new Vector(velocity.x * c - velocity.z * s, velocity.y, velocity.x * s + velocity.z * c);
    }
}
