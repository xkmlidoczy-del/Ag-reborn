package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW312 fixed physical move and continuous successful-cast economics; no mastery award. */
public final class PhaseSidestepRules {
    public static final double DISTANCE = SkillTuning.number("phase_sidestep", "base_distance");
    /** Longest sidestep any level can take (see MobilityScale); bounds the wire format and the visual, never the authoritative budget. */
    public static final double MAX_DISTANCE = DISTANCE * MobilityScale.MAX_REACH;
    /** The sidestep distance of a growth position: 3 m at LV1, scaled up to MAX_DISTANCE at LV5. */
    public static double distance(BigDecimal q) { return DISTANCE * MobilityScale.reach(q); }
    public static final int REQUEST_INTERVAL_TICKS = 4, MOVEMENT_FENCE_TICKS = 8;
    private static final ContinuousHighPowerGrowth.Curve COST =
            ContinuousHighPowerGrowth.curve("8", "4", "2", ".5", ".05");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN =
            ContinuousHighPowerGrowth.curve("40", "20", "10", "4", "1");

    private PhaseSidestepRules() {}
    public static double cpCost(BigDecimal q) { return COST.value(q).doubleValue(); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return COOLDOWN.value(q); }
    public static int cooldownTicks(BigDecimal q) {
        return ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(q));
    }
    public static boolean clock(long tick) { return SafeLandingProbeRules.clock(tick); }
    /** Same clock and corrupt-receipt policy as the movement fence shared with NEW313. */
    public static boolean ready(long previous, long now, int fence) {
        return SafeLandingProbeRules.ready(previous, now, fence);
    }
}
