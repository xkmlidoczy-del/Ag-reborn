package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW213's bounded preparation and global, real-server-tick work allowance. */
public final class BeamEtchingRules {
    public static final double MAX_RANGE = SkillTuning.number("beam_etching", "reach");
    public static final int MAX_MARKS = SkillTuning.integer("beam_etching", "max_targets"), HOLD_TICKS = 100, PREVIEW_INTERVAL = 4,
            ATTEMPTS_PER_TICK = 2, EXPERIENCE_INTERVAL = 20;
    private static final ContinuousHighPowerGrowth.Curve CP = ContinuousHighPowerGrowth.curve("1", ".5", ".2", ".05", ".005");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN = ContinuousHighPowerGrowth.curve("10", "8", "6", "4", "1");
    private BeamEtchingRules() {}

    public static double cost(BigDecimal position) { return CP.value(position).doubleValue(); }
    public static BigDecimal theoreticalCooldown(BigDecimal position) { return COOLDOWN.value(position); }
    public static int cooldown(BigDecimal position) { return ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(position)); }
    public static boolean clock(long tick) { return tick >= 0 && tick <= Long.MAX_VALUE - HOLD_TICKS - 1; }
    public static boolean expired(long start, long tick) { return !clock(start) || !clock(tick) || tick < start || tick - start >= HOLD_TICKS; }
    public static boolean ready(long previous, long tick, int interval) {
        return clock(tick) && interval > 0 && (previous == -1 || clock(previous) && tick >= previous && tick - previous >= interval);
    }
    /** Stored per player, not per cast; abort, relog and same-tick recasts cannot refill it. */
    public record Timing(long observed, long previewAt, long acquireAt, long attemptAt, int attempts, long stopUntil) {
        public Timing {
            if (!clock(observed) || !past(previewAt, observed) || !past(acquireAt, observed) || !past(attemptAt, observed)
                    || attempts < 0 || attempts > ATTEMPTS_PER_TICK || (attemptAt == -1) != (attempts == 0)
                    || stopUntil < 0 || stopUntil > observed + 10) throw new IllegalArgumentException("Invalid NEW213 timing fence");
        }
        public static Timing initial(long now) { return new Timing(now, -1, -1, -1, 0, 0); }
        public boolean current(long now) { return clock(now) && now >= observed; }
        public boolean previewReady(long now) { return current(now) && ready(previewAt, now, PREVIEW_INTERVAL); }
        public boolean acquireReady(long now) { return current(now) && ready(acquireAt, now, 1); }
        public boolean attemptReady(long now) { return current(now) && (attemptAt != now || attempts < ATTEMPTS_PER_TICK); }
        public Timing preview(long now) { if (!previewReady(now)) throw new IllegalStateException("NEW213 preview cadence"); return new Timing(now, now, acquireAt, attemptAt, attempts, stopUntil); }
        public Timing acquire(long now) { if (!acquireReady(now)) throw new IllegalStateException("NEW213 acquisition cadence"); return new Timing(now, previewAt, now, attemptAt, attempts, stopUntil); }
        public Timing attempt(long now) { if (!attemptReady(now)) throw new IllegalStateException("NEW213 attempt quota"); return new Timing(now, previewAt, acquireAt, now, attemptAt == now ? attempts + 1 : 1, stopUntil); }
        public Timing finish(long now, int cooldown) {
            if (!current(now) || cooldown < 1 || cooldown > 10) throw new IllegalStateException("NEW213 finish clock");
            return new Timing(now, previewAt, acquireAt, attemptAt, attempts, Math.max(stopUntil, now + cooldown));
        }
        private static boolean past(long value, long observed) { return value == -1 || clock(value) && value <= observed; }
    }

    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public double distanceSquared(Point other) { return (x-other.x)*(x-other.x)+(y-other.y)*(y-other.y)+(z-other.z)*(z-other.z); }
    }
    public static boolean within(Point origin, Point endpoint) {
        return origin != null && endpoint != null && origin.finite() && endpoint.finite()
                && origin.distanceSquared(endpoint) <= MAX_RANGE * MAX_RANGE;
    }
    /** Round coordinate addition inward; never widen the admitted 12 m sphere. */
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
