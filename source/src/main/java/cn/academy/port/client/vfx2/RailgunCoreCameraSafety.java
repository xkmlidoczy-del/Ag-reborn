package cn.academy.port.client.vfx2;

import java.util.Collection;
import java.util.IdentityHashMap;

/** Pure admission fence for an optional inner material; it never owns geometry or a clock. */
public final class RailgunCoreCameraSafety {
    public static final double NEAR_PLANE_MARGIN = .20;
    public static final double MIN_AXIS_CLEARANCE = .50;
    public static final double REENTRY_GAP = .10;
    public static final double INNER_HEAD_FIX = .98;
    public static final int MAX_IDENTITIES = 65;
    public static final double UNIT_NORM_TOLERANCE = 1.0E-6;

    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }

    private final IdentityHashMap<Object, Boolean> safe = new IdentityHashMap<>();
    private final int capacity;

    public RailgunCoreCameraSafety(int capacity) {
        if (capacity < 1 || capacity > MAX_IDENTITIES) throw new IllegalArgumentException("Invalid camera fence capacity");
        this.capacity = capacity;
    }

    /**
     * Conservative ranking check. A rejection records unsafe once; a pass does not change state.
     * Thus the exact post-wiggle check is the only operation that can admit/re-admit a material.
     */
    public boolean candidate(Object identity, Point origin, Point direction, double length,
                             double radius, Point camera) {
        boolean result = decision(identity, origin, direction, length, radius, camera);
        if (!result) remember(identity, false);
        return result;
    }

    /** Commit using the actual visible length and post-wiggle .09 * widthScale radius. */
    public boolean allow(Object identity, Point origin, Point direction, double length,
                         double radius, Point camera) {
        boolean result = decision(identity, origin, direction, length, radius, camera);
        remember(identity, result);
        return result;
    }

    private boolean decision(Object identity, Point origin, Point direction, double length,
                             double radius, Point camera) {
        if (identity == null || !safe.containsKey(identity) && safe.size() >= capacity) return false;
        double distance = axisDistance(origin, direction, length, radius, camera);
        if (!Double.isFinite(distance)) return false;
        double clearance = Math.max(MIN_AXIS_CLEARANCE, radius + NEAR_PLANE_MARGIN);
        if (!Boolean.TRUE.equals(safe.get(identity))) clearance += REENTRY_GAP;
        return distance > clearance;
    }

    /**
     * Conservative capsule containing every original inner-cylinder vertex, including all caps.
     * The original radius/headFix equations give start x=.02r..r and end x=L..L+.98r.
     * Its body joins x=r to x=L, including early phases where L is smaller than r.
     * Subtract camera/origin before projection; never form a far-world endpoint or use floats.
     */
    public static double axisDistance(Point origin, Point direction, double length,
                                      double radius, Point camera) {
        if (origin == null || direction == null || camera == null || !origin.finite()
                || !direction.finite() || !camera.finite() || !Double.isFinite(length) || length <= 0
                || !Double.isFinite(radius) || radius <= 0) return Double.NaN;
        double norm = Math.hypot(direction.x, Math.hypot(direction.y, direction.z));
        if (!Double.isFinite(norm) || Math.abs(norm - 1) > UNIT_NORM_TOLERANCE) return Double.NaN;
        double ax = direction.x / norm, ay = direction.y / norm, az = direction.z / norm;
        double x = camera.x - origin.x, y = camera.y - origin.y, z = camera.z - origin.z;
        double offset = radius * (1 - INNER_HEAD_FIX);
        // Original vertices multiply the supplied direction. Retain its tiny accepted
        // unit-norm roundoff in axial distances instead of silently changing geometry.
        double minimum = Math.min(offset, length) * norm;
        double maximum = Math.max(radius, length + radius - offset) * norm;
        double projected = x * ax + y * ay + z * az;
        if (!Double.isFinite(projected) || !Double.isFinite(maximum)) return Double.NaN;
        double along = Math.max(minimum, Math.min(maximum, projected));
        return Math.hypot(x - ax * along, Math.hypot(y - ay * along, z - az * along));
    }

    private void remember(Object identity, boolean value) {
        if (identity != null && (safe.containsKey(identity) || safe.size() < capacity)) safe.put(identity, value);
    }

    public void retain(Collection<?> live, Object historicalSample) {
        safe.keySet().removeIf(identity -> {
            if (identity == historicalSample) return false;
            for (Object candidate : live) if (candidate == identity) return false;
            return true;
        });
    }

    public int trackedIdentities() { return safe.size(); }
    public void clear() { safe.clear(); }
}
