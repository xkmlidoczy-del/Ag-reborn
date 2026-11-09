package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.util.Arrays;

/** NEW232 physical limits are independent of the smoothly varying economic curves. */
public final class BeamStepRules {
    public static final double MAX_ADDITION = SkillTuning.number("beam_step", "max_vertical_addition"), MAX_RESULT_Y = .36;
    public static final int FENCE_TICKS = 8, WINDOW_TICKS = SkillTuning.integer("beam_step", "duration_ticks"), MAX_GRANTS = SkillTuning.integer("beam_step", "max_targets"), GRANT_TTL = 2, PLATE_TTL = 20;
    private static final ContinuousHighPowerGrowth.Curve COST = ContinuousHighPowerGrowth.curve("8", "4", "2", ".5", ".05");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN = ContinuousHighPowerGrowth.curve("60", "30", "16", "10", "1");
    private BeamStepRules() {}
    public static double cpCost(BigDecimal q) { return COST.value(q).doubleValue(); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return COOLDOWN.value(q); }
    public static int cooldownTicks(BigDecimal q) { return ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(q)); }
    public static boolean clock(long tick) { return tick >= 0 && tick <= Long.MAX_VALUE - WINDOW_TICKS; }
    /** NaN means refusal; a faster fall is softened but never replaced by a fixed upward speed. */
    public static double assistedY(double current) {
        if (!Double.isFinite(current) || current >= MAX_RESULT_Y) return Double.NaN;
        double result = Math.min(MAX_RESULT_Y, current + Math.min(MAX_ADDITION, MAX_RESULT_Y - current));
        if (result - current > MAX_ADDITION) result = Math.nextDown(result);
        return result > current ? result : Double.NaN;
    }
    public static boolean fresh(long issued, long expires, long now) {
        return clock(issued) && expires == issued + GRANT_TTL && now >= issued && now < expires;
    }
    /** Reject malformed/future records instead of silently turning them into a fresh budget. */
    public static boolean quotaReady(long[] grants, long now) {
        if (!clock(now) || grants == null || grants.length > MAX_GRANTS) return false;
        long previous = -1; int live = 0;
        for (long at : grants) {
            if (!clock(at) || at > now || at <= previous) return false;
            if (now - at < FENCE_TICKS) return false;
            if (now - at < WINDOW_TICKS) live++;
            previous = at;
        }
        return live < MAX_GRANTS;
    }
    public static long[] append(long[] grants, long now) {
        if (!quotaReady(grants, now)) throw new IllegalArgumentException("Beam step quota unavailable");
        long[] result = new long[MAX_GRANTS]; int size = 0;
        for (long at : grants) if (now - at < WINDOW_TICKS) result[size++] = at;
        result[size++] = now;
        return Arrays.copyOf(result, size);
    }
}
