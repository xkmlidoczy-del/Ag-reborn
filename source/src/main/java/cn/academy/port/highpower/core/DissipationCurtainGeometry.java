package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** NEW242: three finite chord rectangles, shared by native contact admission and rendering. */
public final class DissipationCurtainGeometry {
    public static final int FACET_COUNT = 3, STARTUP_TICKS = SkillTuning.integer("directional_dissipation_curtain", "startup_ticks");
    public static final double RADIUS = SkillTuning.number("directional_dissipation_curtain", "radius"), HEIGHT = SkillTuning.number("directional_dissipation_curtain", "height"), THICKNESS = .04, FACET_SPAN_DEGREES = 36,
            FACET_SPACING_DEGREES = 40, HALF_WIDTH = RADIUS * Math.sin(Math.toRadians(18)),
            CENTER_DISTANCE = RADIUS * Math.cos(Math.toRadians(18));
    private static final double EPS = 1e-9, WORLD_LIMIT = 30_000_001;
    private static final ContinuousHighPowerGrowth.Curve ACTIVATION = ContinuousHighPowerGrowth.curve("8", "4", "2", ".5", ".05");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN = ContinuousHighPowerGrowth.curve("60", "30", "16", "10", "1");

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
    public record Contact(int plateIndex, double fraction, Vector point) { public int facetIndex() { return plateIndex; } }
    private DissipationCurtainGeometry() {}

    public static double activationCost(BigDecimal q) { return ACTIVATION.value(q).doubleValue(); }
    public static double sustainCost(BigDecimal q) { return 1; }
    public static int cooldownTicks(BigDecimal q) { return ContinuousHighPowerGrowth.ceilCooldownTicks(COOLDOWN.value(q)); }
    public static Pose pose(Vector origin, Vector facing) {
        if (origin == null || facing == null || !origin.finite() || !facing.finite() || facing.length() < .5) return null;
        Vector axis = facing.scale(1 / facing.length());
        double horizontal = Math.hypot(axis.x, axis.z);
        Vector right = horizontal > EPS ? new Vector(axis.z / horizontal, 0, -axis.x / horizontal) : new Vector(1, 0, 0);
        return new Pose(origin, axis, right, axis.cross(right));
    }
    public static double facetAngle(int index) { checkIndex(index); return Math.toRadians((index - 1) * FACET_SPACING_DEGREES); }
    public static Vector facetNormal(Pose pose, int index) {
        checkPose(pose, index); double angle = facetAngle(index);
        return pose.axis.scale(Math.cos(angle)).add(pose.right.scale(Math.sin(angle)));
    }
    public static Vector facetRight(Pose pose, int index) {
        checkPose(pose, index); double angle = facetAngle(index);
        return pose.right.scale(Math.cos(angle)).subtract(pose.axis.scale(Math.sin(angle)));
    }
    /** Front faces are radius-six chords; thickness extends inward, never outside the end vertices. */
    public static Vector facetCenter(Pose pose, int index) { return pose.center.add(facetNormal(pose, index).scale(CENTER_DISTANCE)); }
    public static List<Vector> vertices(Pose pose, int index) {
        Vector center = facetCenter(pose, index), right = facetRight(pose, index).scale(HALF_WIDTH), up = pose.up.scale(HEIGHT / 2);
        return List.of(center.subtract(right).subtract(up), center.add(right).subtract(up), center.add(right).add(up), center.subtract(right).add(up));
    }
    public static boolean contains(Pose pose, int index, Vector point) {
        if (pose == null || point == null || !point.finite() || index < 0 || index >= FACET_COUNT) return false;
        Vector local = point.subtract(facetCenter(pose, index));
        double depth = local.dot(facetNormal(pose, index));
        return depth <= EPS && depth >= -THICKNESS - EPS
                && Math.abs(local.dot(facetRight(pose, index))) <= HALF_WIDTH + EPS
                && Math.abs(local.dot(pose.up)) <= HEIGHT / 2 + EPS;
    }
    /** A cone test is deliberately insufficient: only a strict outside-to-inside front-plane crossing counts. */
    public static Optional<Contact> crossing(Pose pose, Vector start, Vector end, int liveMask) {
        if (pose == null || start == null || end == null || !start.finite() || !end.finite() || liveMask < 0 || liveMask > 7) return Optional.empty();
        Vector movement = end.subtract(start);
        if (!movement.finite() || movement.length() > 128 || movement.length() <= EPS) return Optional.empty();
        Contact nearest = null;
        for (int index = 0; index < FACET_COUNT; index++) {
            if ((liveMask & (1 << index)) == 0) continue;
            Vector normal = facetNormal(pose, index), center = facetCenter(pose, index);
            double front = start.subtract(center).dot(normal), approach = -movement.dot(normal);
            if (front <= EPS || approach <= EPS) continue;
            double t = front / approach;
            if (!(t > EPS && t < 1 - EPS)) continue;
            Vector point = start.add(movement.scale(t)), local = point.subtract(center);
            if (Math.abs(local.dot(facetRight(pose, index))) > HALF_WIDTH + EPS || Math.abs(local.dot(pose.up)) > HEIGHT / 2 + EPS) continue;
            if (nearest == null || t < nearest.fraction) nearest = new Contact(index, t, point);
        }
        return Optional.ofNullable(nearest);
    }
    private static void checkIndex(int index) { if (index < 0 || index >= FACET_COUNT) throw new IllegalArgumentException("Invalid curtain facet index"); }
    private static void checkPose(Pose pose, int index) { if (pose == null) throw new IllegalArgumentException("Invalid curtain pose"); checkIndex(index); }
}
