package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW253's provisional continuous costs, immutable lifetime and single total damage budget. */
public final class RemoteScatterRelayRules {
    public static final int LIFETIME = SkillTuning.integer("remote_scatter_relay", "duration_ticks"), MAX_NODES = 4, MAX_SEGMENTS = 5;
    public static final int HOLD_LIMIT = 100, RECALL_TICKS = 12, PREVIEW_INTERVAL = 4,
            ATTEMPT_INTERVAL = 1, EMPTY_INTERVAL = 4, SCAN_LIMIT = 64;
    public static final double PLACE_RANGE = 32, EXIT_RANGE = 32, LEG_RANGE = SkillTuning.number("remote_scatter_relay", "leg_reach"),
            RANGE = 32, TOTAL_RANGE = SkillTuning.number("remote_scatter_relay", "reach"), RADIUS = .035;
    private static final BigDecimal DAMAGE_MULTIPLIER = new BigDecimal("2.4");
    private static final ContinuousHighPowerGrowth.Curve PLACE =
            ContinuousHighPowerGrowth.curve("4", "2", "1", ".2", ".01");
    private static final ContinuousHighPowerGrowth.Curve SHOT =
            ContinuousHighPowerGrowth.curve("80", "40", "20", "4", ".2");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN =
            ContinuousHighPowerGrowth.curve("80", "50", "30", "15", "1");

    private RemoteScatterRelayRules() {}

    /** Charge once for each successfully committed node; node count never affects shot damage. */
    public static BigDecimal theoreticalPlacementCost(BigDecimal q) { return PLACE.value(q); }
    public static double placementCost(BigDecimal q) { return theoreticalPlacementCost(q).doubleValue(); }
    public static BigDecimal theoreticalShotCost(BigDecimal q) { return SHOT.value(q); }
    public static double shotCost(BigDecimal q) { return theoreticalShotCost(q).doubleValue(); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return COOLDOWN.value(q); }
    public static int cooldown(BigDecimal q) {
        return ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(q));
    }
    public static int cooldownTicks(BigDecimal q) { return cooldown(q); }

    /** One shot owns this entire budget, regardless of how many nodes or legs it traverses. */
    public static BigDecimal damage(BigDecimal d) {
        return CombatAmounts.requirePositiveAmount(d, "NEW253 D").multiply(DAMAGE_MULTIPLIER).min(CombatAmounts.CAP);
    }

    public static boolean clock(long tick) { return tick >= 0 && tick <= Long.MAX_VALUE - LIFETIME - 1; }
    public static boolean expired(long start, long now) { return expired(start, now, LIFETIME); }
    public static boolean expired(long start, long now, int duration) {
        return duration < 0 || duration > LIFETIME || !clock(start) || !clock(now)
                || now < start || now - start >= duration;
    }
    public static boolean ready(long before, long now, int interval) {
        return interval > 0 && clock(now) && (before == -1
                || clock(before) && now >= before && now - before >= interval);
    }

    /** Frozen first-commit clock: appending or reorienting nodes cannot replace or extend it. */
    public record Lifetime(long startTick) {
        public Lifetime {
            if (!clock(startTick)) throw new IllegalArgumentException("Invalid remote relay start tick");
        }
        public long expiresAt() { return startTick + LIFETIME; }
        public boolean expired(long now) { return RemoteScatterRelayRules.expired(startTick, now); }
        public int remainingTicks(long now) { return expired(now) ? 0 : (int) (expiresAt() - now); }
    }
}
