package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.util.Optional;

/** NEW212's three finite elliptical front faces. Geometry never creates a solid world object. */
public final class ScatterPlateGeometry {
    public static final double RADIUS_X = SkillTuning.number("scatter_plates", "horizontal_radius"), RADIUS_Y = SkillTuning.number("scatter_plates", "vertical_radius"), THICKNESS = .04, FORWARD = 1, SPACING = .8;
    private static final double EPS = 1e-9, WORLD_LIMIT = 30_000_001;
    private static final ContinuousHighPowerGrowth.Curve ACTIVATION = ContinuousHighPowerGrowth.curve("4", "2", "1", ".3", ".05");
    private static final ContinuousHighPowerGrowth.Curve SUSTAIN = ContinuousHighPowerGrowth.curve("1", ".5", ".2", ".05", ".01");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN = ContinuousHighPowerGrowth.curve("10", "8", "6", "4", "2");
    public record Vector(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Math.abs(x) <= WORLD_LIMIT && Math.abs(y) <= WORLD_LIMIT && Math.abs(z) <= WORLD_LIMIT; }
        public Vector add(Vector v) { return new Vector(x + v.x, y + v.y, z + v.z); }
        public Vector subtract(Vector v) { return new Vector(x - v.x, y - v.y, z - v.z); }
        public Vector scale(double s) { return new Vector(x * s, y * s, z * s); }
        public double dot(Vector v) { return x * v.x + y * v.y + z * v.z; }
        public Vector cross(Vector v) { return new Vector(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x); }
        public double length() { return Math.hypot(Math.hypot(x, y), z); }
    }
    public record Pose(Vector center, Vector axis, Vector right, Vector up) {}
    public record Contact(int plateIndex, double fraction, Vector point) {}
    private ScatterPlateGeometry() {}
    public static double activationCost(BigDecimal q) { return ACTIVATION.value(q).doubleValue(); }
    public static double sustainCost(BigDecimal q) { return SUSTAIN.value(q).doubleValue(); }
    public static int cooldownTicks(BigDecimal q) { return ContinuousHighPowerGrowth.ceilCooldownTicks(COOLDOWN.value(q)); }
    public static Pose pose(Vector origin, Vector facing) {
        if (origin == null || facing == null || !origin.finite() || !facing.finite() || facing.length() < .5) return null;
        Vector axis = facing.scale(1 / facing.length());
        double horizontal = Math.hypot(axis.x, axis.z);
        Vector right = horizontal > EPS ? new Vector(axis.z / horizontal, 0, -axis.x / horizontal) : new Vector(1, 0, 0);
        return new Pose(origin, axis, right, axis.cross(right));
    }
    public static Vector plateCenter(Pose pose, int index) {
        if (pose == null || index < 0 || index > 2) throw new IllegalArgumentException("Invalid plate pose/index");
        return pose.center.add(pose.axis.scale(FORWARD)).add(pose.right.scale((index - 1) * SPACING));
    }
    /** Start must be outside the front slab; side, rear, close-inside and end-only contacts grant nothing. */
    public static Optional<Contact> crossing(Pose pose, Vector start, Vector end, int liveMask) {
        if (pose == null || start == null || end == null || !start.finite() || !end.finite() || liveMask < 0 || liveMask > 7) return Optional.empty();
        Vector movement = end.subtract(start);
        if (!movement.finite() || movement.length() > 128 || movement.length() <= EPS) return Optional.empty();
        double approach = -movement.dot(pose.axis);
        if (approach <= EPS) return Optional.empty();
        Contact nearest = null;
        for (int index = 0; index < 3; index++) {
            if ((liveMask & (1 << index)) == 0) continue;
            Vector center = plateCenter(pose, index);
            double front = start.subtract(center).dot(pose.axis);
            if (front <= THICKNESS / 2 + EPS) continue;
            double t = (front - THICKNESS / 2) / approach;
            if (!(t > EPS && t < 1 - EPS)) continue;
            Vector point = start.add(movement.scale(t)), local = point.subtract(center);
            double x = local.dot(pose.right) / RADIUS_X, y = local.dot(pose.up) / RADIUS_Y;
            if (x * x + y * y > 1 + 1e-12) continue;
            if (nearest == null || t < nearest.fraction) nearest = new Contact(index, t, point);
        }
        return Optional.ofNullable(nearest);
    }
}
