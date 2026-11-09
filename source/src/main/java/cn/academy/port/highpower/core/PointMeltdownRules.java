package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW233's single-surface range, bounded preview and consecutive real-server-tick focus. */
public final class PointMeltdownRules {
    public static final double MAX_RANGE = SkillTuning.number("point_meltdown", "reach");
    public static final int PREVIEW_TICKS = 100, FOCUS_TICKS = SkillTuning.integer("point_meltdown", "focus_ticks"), PREVIEW_INTERVAL = 2;

    private PointMeltdownRules() {}

    /** Reuse NEW213's exact continuous curves, including its engine-boundary rounding. */
    public static double cost(BigDecimal position) { return BeamEtchingRules.cost(position); }
    public static BigDecimal theoreticalCooldown(BigDecimal position) { return BeamEtchingRules.theoreticalCooldown(position); }
    public static int cooldown(BigDecimal position) { return BeamEtchingRules.cooldown(position); }

    public static boolean clock(long tick) { return tick >= 0 && tick <= Long.MAX_VALUE - PREVIEW_TICKS - FOCUS_TICKS - 1; }
    public static boolean expired(long start, long tick) {
        return !clock(start) || !clock(tick) || tick < start || tick - start >= PREVIEW_TICKS;
    }
    public static boolean ready(long previous, long tick, int interval) {
        return clock(tick) && interval > 0
                && (previous == -1 || clock(previous) && tick >= previous && tick - previous >= interval);
    }

    /** Release starts at zero; only the next actual server tick can earn one focus tick. */
    public record Focus(long started, long lastTick, int ticks) {
        public Focus {
            if (!clock(started) || !clock(lastTick) || lastTick < started || ticks < 0 || ticks > FOCUS_TICKS
                    || lastTick - started != ticks) throw new IllegalArgumentException("Invalid NEW233 focus clock");
        }
        public static Focus initial(long releaseTick) { return new Focus(releaseTick, releaseTick, 0); }
        public boolean complete() { return ticks == FOCUS_TICKS; }
        /** Missing or reversed ticks retire the focus rather than inventing elapsed work. */
        public boolean current(long now) {
            return clock(now) && (now == lastTick || !complete() && now > lastTick && now - lastTick == 1);
        }
        public Focus advance(long now) {
            if (!current(now)) throw new IllegalStateException("NEW233 focus requires consecutive server ticks");
            return now == lastTick ? this : new Focus(started, now, ticks + 1);
        }
    }

    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public double distanceSquared(Point other) { return (x-other.x)*(x-other.x)+(y-other.y)*(y-other.y)+(z-other.z)*(z-other.z); }
    }
    public static boolean within(Point origin, Point endpoint) {
        return origin != null && endpoint != null && origin.finite() && endpoint.finite()
                && origin.distanceSquared(endpoint) <= MAX_RANGE * MAX_RANGE;
    }
    /** Round world-coordinate addition inward; never grant a range epsilon beyond 36 m. */
    public static Point endpoint(Point origin, Point direction) {
        if (origin == null || direction == null || !origin.finite() || !direction.finite()
                || Math.abs(direction.x*direction.x + direction.y*direction.y + direction.z*direction.z - 1) > 1e-6) return null;
        Point end = new Point(origin.x + direction.x*MAX_RANGE, origin.y + direction.y*MAX_RANGE, origin.z + direction.z*MAX_RANGE);
        for (int i=0; i<=8; i++) {
            if (within(origin,end) && origin.distanceSquared(end)>0) return end;
            end = new Point(Math.nextAfter(end.x,origin.x),Math.nextAfter(end.y,origin.y),Math.nextAfter(end.z,origin.z));
        }
        return null;
    }
}
