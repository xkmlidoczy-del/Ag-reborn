package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW353 fixed physical limits and inert saved-template validation; no movement authority. */
public final class TacticalCoordinateRules {
    public static final int SCHEMA_VERSION = 1, MAX_TEMPLATES = SkillTuning.integer("tactical_coordinate_formation", "max_templates"), INVITE_TICKS = SkillTuning.integer("tactical_coordinate_formation", "duration_ticks"),
            REQUEST_INTERVAL_TICKS = 8, MOVEMENT_FENCE_TICKS = 8, MAX_NAME_LENGTH = 32;
    public static final double RANGE = SkillTuning.number("tactical_coordinate_formation", "reach");
    public static final int INVITATION_TICKS = INVITE_TICKS;
    public static final double MAX_DISTANCE = RANGE;
    private static final double COORDINATE_LIMIT = 30_000_016;
    private static final ContinuousHighPowerGrowth.Curve COST =
            ContinuousHighPowerGrowth.curve("8", "4", "2", ".5", ".05");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN =
            ContinuousHighPowerGrowth.curve("60", "30", "16", "10", "1");

    private TacticalCoordinateRules() {}

    public static double cpCost(BigDecimal q) { return COST.value(q).doubleValue(); }
    public static double cpCost(double q) { return cpCost(BigDecimal.valueOf(q)); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return COOLDOWN.value(q); }
    public static int cooldownTicks(BigDecimal q) {
        return ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(q));
    }
    public static int cooldownTicks(double q) { return cooldownTicks(BigDecimal.valueOf(q)); }
    public static boolean validCount(int count) { return count >= 0 && count <= MAX_TEMPLATES; }
    public static boolean validRevision(long revision) { return revision > 0 && revision < Long.MAX_VALUE; }
    public static boolean validName(String name) {
        return name != null && name.length() <= MAX_NAME_LENGTH
                && name.codePoints().noneMatch(Character::isISOControl);
    }
    public static boolean validCoordinates(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(x) <= COORDINATE_LIMIT && Math.abs(z) <= COORDINATE_LIMIT && Math.abs(y) <= 4096;
    }
    public static boolean validYaw(float yaw) { return Float.isFinite(yaw) && yaw >= -180 && yaw < 180; }
    public static boolean validPitch(float pitch) { return Float.isFinite(pitch) && pitch >= -90 && pitch <= 90; }
    public static float normalizeYaw(float yaw) { return (float)normalizeYaw((double)yaw); }
    public static double normalizeYaw(double yaw) {
        if (!Double.isFinite(yaw)) throw new IllegalArgumentException("Nonfinite tactical heading");
        double normalized = yaw % 360;
        if (normalized >= 180) normalized -= 360;
        if (normalized < -180) normalized += 360;
        return normalized == 0 ? 0 : normalized;
    }
    public static boolean clock(long tick) { return tick >= 0 && tick <= Long.MAX_VALUE - INVITE_TICKS; }
    public static boolean fresh(long issued, long now) {
        return clock(issued) && clock(now) && now >= issued && now - issued < INVITE_TICKS;
    }
    public static boolean ready(long previous, long now, int fence) {
        return clock(now) && fence > 0 && fence <= INVITE_TICKS
                && (previous == -1 || clock(previous) && now >= previous && now - previous >= fence);
    }
}
