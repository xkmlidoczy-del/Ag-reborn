package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.util.*;

/** NEW133 first mode: bounded velocity assistance to already-owned native dropped stacks. */
public final class MagneticItemPullRules {
    public static final double RANGE = SkillTuning.number("magnetic_item_pull", "reach"), MAX_SPEED = .35, ACCELERATION = .12, ARRIVAL = .65;
    public static final int MAX_TARGETS = SkillTuning.integer("magnetic_item_pull", "max_targets"), MAX_TICKS = SkillTuning.integer("magnetic_item_pull", "duration_ticks");
    private static final ContinuousHighPowerGrowth.Curve COST = ContinuousHighPowerGrowth.curve("8", "8", "8", "4", "1");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN = ContinuousHighPowerGrowth.curve("40", "40", "40", "10", "1");
    public record Vector(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public double length() { return Math.hypot(Math.hypot(x, y), z); }
    }
    private MagneticItemPullRules() {}
    public static double cpCost(BigDecimal position) { return COST.value(position).doubleValue(); }
    public static BigDecimal theoreticalCooldown(BigDecimal position) { return COOLDOWN.value(position); }
    public static int cooldownTicks(BigDecimal position) { return ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(position)); }
    public static boolean expired(long started, long now) { return now < started || now - started >= MAX_TICKS; }
    /** Deduplicate before server lookup; no input can make more than sixteen world queries. */
    public static List<UUID> candidates(List<UUID> candidates) {
        if (candidates == null || candidates.size() > MAX_TARGETS) return null;
        LinkedHashSet<UUID> unique = new LinkedHashSet<>();
        for (UUID candidate : candidates) { if (candidate == null) return null; unique.add(candidate); }
        return List.copyOf(unique);
    }
    /** Add a small toward-hand impulse; refuse external overspeed instead of replacing or clamping it. */
    public static Vector pull(Vector velocity, Vector toward) {
        if (velocity == null || toward == null || !velocity.finite() || !toward.finite()) return null;
        double speed = velocity.length(), distance = toward.length();
        if (speed > MAX_SPEED + 1e-10 || distance <= ARRIVAL || distance > RANGE) return null;
        double x = toward.x / distance, y = toward.y / distance, z = toward.z / distance;
        double dot = velocity.x * x + velocity.y * y + velocity.z * z;
        double discriminant = dot * dot + MAX_SPEED * MAX_SPEED - speed * speed;
        double impulse = Math.min(ACCELERATION, Math.max(0, -dot + Math.sqrt(Math.max(0, discriminant))));
        // Arrival is only retirement. The native entity retains momentum/pickup delay/physics.
        impulse = Math.min(impulse, Math.max(0, distance - ARRIVAL - Math.max(0, dot)));
        return new Vector(velocity.x + x * impulse, velocity.y + y * impulse, velocity.z + z * impulse);
    }
}
