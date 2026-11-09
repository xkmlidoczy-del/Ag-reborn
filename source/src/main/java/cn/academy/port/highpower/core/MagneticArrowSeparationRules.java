package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.*;

/** NEW133 arrow mode: a bounded outward rotation, never an acceleration or reflected shot. */
public final class MagneticArrowSeparationRules {
    public static final int PULSE_TICKS = 20, QUOTA_TICKS = 20, MAX_ARROWS = 4, SCAN_LIMIT = 64;
    public static final double RANGE = 24, RADIUS = 2, MAX_SPEED = 3, MIN_HORIZONTAL_SPEED = .03;
    public static final double TURN_PER_TICK = .10, MAX_TURN = Math.PI / 4;
    public record Vector(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public Vector add(Vector o) { return new Vector(x + o.x, y + o.y, z + o.z); }
        public Vector subtract(Vector o) { return new Vector(x - o.x, y - o.y, z - o.z); }
        public double dot(Vector o) { return x * o.x + y * o.y + z * o.z; }
        public double lengthSquared() { return dot(this); }
    }
    public record Turn(Vector velocity, int side, double angle) {}
    public record Admission(UUID arrow, long tick) {}
    private MagneticArrowSeparationRules() {}
    public static double cpCost(BigDecimal q) { return MagneticItemPullRules.cpCost(q); }
    public static int cooldownTicks(BigDecimal q) { return MagneticItemPullRules.cooldownTicks(q); }
    public static boolean clock(long now) { return now >= 0 && now <= Long.MAX_VALUE - QUOTA_TICKS; }
    public static boolean expired(long started, long now) { return !clock(started) || !clock(now) || now < started || now - started >= PULSE_TICKS; }
    public static boolean velocity(Vector v) {
        return v != null && v.finite() && Math.hypot(v.x, v.z) >= MIN_HORIZONTAL_SPEED && v.lengthSquared() <= MAX_SPEED * MAX_SPEED;
    }
    /** Axis is the unit, full 3-D look captured at release. No point behind the caster is admitted. */
    public static boolean inCorridor(Vector at, Vector origin, Vector axis) {
        if (at == null || origin == null || axis == null || !at.finite() || !origin.finite() || !unit(axis)) return false;
        Vector relative = at.subtract(origin); double along = relative.dot(axis);
        return along >= 0 && along <= RANGE && relative.lengthSquared() <= RANGE * RANGE
                && radialSquared(relative, axis) <= RADIUS * RADIUS;
    }
    private static boolean unit(Vector axis) { return axis.finite() && Math.abs(axis.lengthSquared() - 1) <= 1e-8; }
    private static double radialSquared(Vector relative, Vector axis) { double along = relative.dot(axis); return Math.max(0, relative.lengthSquared() - along * along); }
    /** Choose the outward side once. Each successive write must remain outward and away from the shooter. */
    public static Turn turn(Vector velocity, Vector at, Vector origin, Vector axis, Vector shooter, int side, double accumulated, UUID identity) {
        if (!velocity(velocity) || !inCorridor(at, origin, axis) || shooter == null || !shooter.finite() || identity == null
                || !Double.isFinite(accumulated) || accumulated < 0 || accumulated >= MAX_TURN || side < -1 || side > 1
                || velocity.dot(origin.subtract(at)) <= 0 || velocity.dot(shooter.subtract(at)) > 1e-10) return null;
        double angle = Math.min(TURN_PER_TICK, MAX_TURN - accumulated);
        if (angle < 1e-9) return null;
        Vector left = rotate(velocity, angle), right = rotate(velocity, -angle);
        if (left == null || right == null) return null;
        if (side == 0) {
            double a = radialSquared(at.add(left).subtract(origin), axis), b = radialSquared(at.add(right).subtract(origin), axis);
            side = Math.abs(a - b) > 1e-12 ? (a > b ? 1 : -1)
                    : ((identity.getLeastSignificantBits() ^ identity.getMostSignificantBits()) & 1) == 0 ? 1 : -1;
        }
        Vector changed = side > 0 ? left : right;
        double before = radialSquared(at.add(velocity).subtract(origin), axis), after = radialSquared(at.add(changed).subtract(origin), axis);
        if (after <= before + 1e-12 || changed.dot(shooter.subtract(at)) > 1e-10) return null;
        return new Turn(changed, side, angle);
    }
    private static Vector rotate(Vector v, double angle) {
        double sin = Math.sin(angle), cos = Math.cos(angle);
        Vector changed = new Vector(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
        // IEEE rotation can round upward. Native Arrow damage uses ceil((float)length * baseDamage),
        // so an epsilon-only conservation check is insufficient at a rounding threshold. Keep Y
        // bit-exact, and move only X/Z inward by the smallest representable steps until both the
        // native evaluation order and horizontal squared speed are no greater than before.
        // This is a bounded ULP concession, never gameplay-scale damping or a speed clamp.
        double horizontal = v.x * v.x + v.z * v.z, total = v.lengthSquared();
        for (int steps = 0; steps <= 8; steps++) {
            if (changed.x * changed.x + changed.z * changed.z <= horizontal && changed.lengthSquared() <= total) return changed;
            changed = new Vector(Math.nextAfter(changed.x, 0), v.y, Math.nextAfter(changed.z, 0));
        }
        return null;
    }
    /** Strict persistent rolling-window semantics; malformed/future/backwards records never grant capacity. */
    public static List<Admission> window(List<Admission> source, long observed, long now) {
        if (!clock(now) || !clock(observed) || observed > now || source == null || source.size() > MAX_ARROWS) return null;
        Set<UUID> unique = new HashSet<>(); List<Admission> active = new ArrayList<>(); long previous = -1;
        for (Admission admission : source) {
            if (admission == null || admission.arrow == null || !clock(admission.tick) || admission.tick > observed
                    || admission.tick < previous || !unique.add(admission.arrow)) return null;
            previous = admission.tick;
            if (now - admission.tick < QUOTA_TICKS) active.add(admission);
        }
        return List.copyOf(active);
    }
}
