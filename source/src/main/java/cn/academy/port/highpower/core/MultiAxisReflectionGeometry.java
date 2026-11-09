package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Optional;

/** Four finite upright faces; admission uses exact predicates over the represented doubles. */
public final class MultiAxisReflectionGeometry {
    public static final int FACET_COUNT = 4;
    public static final double RADIUS = 1.25, WIDTH = .85, HALF_WIDTH = WIDTH / 2, HEIGHT = 2.6;
    public static final double MAX_OWNER_DISTANCE = 5, MAX_SEGMENT_LENGTH = 8;
    private static final MathContext OUTPUT_PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal HALF_WIDTH_EXACT = exact(HALF_WIDTH), HALF_HEIGHT_EXACT = exact(HEIGHT / 2);
    private static final BigDecimal RANGE_SQUARED = BigDecimal.valueOf(25), SEGMENT_SQUARED = BigDecimal.valueOf(64);

    private MultiAxisReflectionGeometry() {}

    public record Vector(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public Vector add(Vector v) { return new Vector(x + v.x, y + v.y, z + v.z); }
        public Vector subtract(Vector v) { return new Vector(x - v.x, y - v.y, z - v.z); }
        public Vector scale(double scale) { return new Vector(x * scale, y * scale, z * scale); }
        public double dot(Vector v) { return x * v.x + y * v.y + z * v.z; }
        public double length() { return Math.hypot(Math.hypot(x, y), z); }
    }

    public static final class Pose {
        private final Vector center, axis, right;
        private Pose(Vector center, Vector axis) {
            this.center = center;
            this.axis = axis;
            this.right = new Vector(axis.z, 0, -axis.x);
        }
        public Vector center() { return center; }
        public Vector axis() { return axis; }
        public Vector right() { return right; }
        public Vector up() { return new Vector(0, 1, 0); }
    }

    public record Contact(int facetIndex, double fraction, Vector point) {}

    /** Pitch never tilts a facet; a missing horizontal direction cannot invent a yaw. */
    public static Pose pose(Vector bodyCenter, Vector facing) {
        if (bodyCenter == null || facing == null || !bodyCenter.finite() || !facing.finite()) return null;
        Vector axis = horizontalUnit(facing);
        return axis == null ? null : new Pose(bodyCenter, axis);
    }

    public static double facetAngle(int index) {
        checkIndex(index);
        return Math.toRadians(-60 + 40 * index);
    }

    public static Vector facetNormal(Pose pose, int index) {
        checkPose(pose, index);
        double angle = facetAngle(index);
        return horizontalUnit(pose.axis.scale(Math.cos(angle)).add(pose.right.scale(Math.sin(angle))));
    }

    public static Vector facetRight(Pose pose, int index) {
        Vector normal = facetNormal(pose, index);
        return new Vector(normal.z, 0, -normal.x);
    }

    public static Vector facetCenter(Pose pose, int index) {
        return pose.center.add(facetOffset(pose, index));
    }

    public static List<Vector> vertices(Pose pose, int index) {
        Vector offset = facetOffset(pose, index), side = facetRight(pose, index).scale(HALF_WIDTH);
        Vector up = new Vector(0, HEIGHT / 2, 0);
        // Add the world translation last, consistently with the exact local admission frame.
        return List.of(pose.center.add(offset.subtract(side).subtract(up)),
                pose.center.add(offset.add(side).subtract(up)),
                pose.center.add(offset.add(side).add(up)),
                pose.center.add(offset.subtract(side).add(up)));
    }

    /** A separate live-position check is required by the native adapter before changing an arrow. */
    public static boolean withinOwnerRange(Vector owner, Vector position) {
        if (owner == null || position == null || !owner.finite() || !position.finite()) return false;
        return ExactVector.of(position).subtract(ExactVector.of(owner)).squared().compareTo(RANGE_SQUARED) <= 0;
    }

    public static Optional<Contact> crossing(Pose pose, Vector start, Vector end) {
        return crossing(pose, start, end, 15);
    }

    /** Strict front-to-back crossing; a tangent, coplanar segment, or endpoint touch is insufficient. */
    public static Optional<Contact> crossing(Pose pose, Vector start, Vector end, int liveMask) {
        if (pose == null || start == null || end == null || !start.finite() || !end.finite()
                || liveMask < 0 || liveMask > 15) return Optional.empty();
        ExactVector worldStart = ExactVector.of(start), owner = ExactVector.of(pose.center);
        ExactVector motion = ExactVector.of(end).subtract(worldStart);
        BigDecimal lengthSquared = motion.squared();
        if (lengthSquared.signum() == 0 || lengthSquared.compareTo(SEGMENT_SQUARED) > 0) return Optional.empty();
        ExactVector ownerStart = worldStart.subtract(owner);
        BigDecimal nearestNumerator = null, nearestDenominator = null;
        int nearestFacet = -1;
        for (int index = 0; index < FACET_COUNT; index++) {
            if ((liveMask & (1 << index)) == 0) continue;
            ExactVector normal = ExactVector.of(facetNormal(pose, index));
            ExactVector right = ExactVector.of(facetRight(pose, index));
            ExactVector localStart = ownerStart.subtract(ExactVector.of(facetOffset(pose, index)));
            BigDecimal front = localStart.dot(normal), back = localStart.add(motion).dot(normal);
            if (front.signum() <= 0 || back.signum() >= 0) continue;
            BigDecimal denominator = front.subtract(back);
            ExactVector localNumerator = localStart.scale(denominator).add(motion.scale(front));
            BigDecimal lateral = localNumerator.dot(right);
            BigDecimal horizontalLimitSquared = HALF_WIDTH_EXACT.multiply(HALF_WIDTH_EXACT)
                    .multiply(right.squared()).multiply(denominator.multiply(denominator));
            if (lateral.multiply(lateral).compareTo(horizontalLimitSquared) > 0
                    || localNumerator.y.abs().compareTo(HALF_HEIGHT_EXACT.multiply(denominator)) > 0) continue;
            ExactVector ownerNumerator = ownerStart.scale(denominator).add(motion.scale(front));
            if (ownerNumerator.squared().compareTo(RANGE_SQUARED.multiply(denominator.multiply(denominator))) > 0) continue;
            // Cross-multiplication preserves exact nearest ordering; equal fractions keep the lower index.
            if (nearestFacet >= 0 && front.multiply(nearestDenominator)
                    .compareTo(nearestNumerator.multiply(denominator)) >= 0) continue;
            nearestFacet = index;
            nearestNumerator = front;
            nearestDenominator = denominator;
        }
        if (nearestFacet < 0) return Optional.empty();
        ExactVector worldNumerator = worldStart.scale(nearestDenominator).add(motion.scale(nearestNumerator));
        return Optional.of(new Contact(nearestFacet,
                nearestNumerator.divide(nearestDenominator, OUTPUT_PRECISION).doubleValue(),
                worldNumerator.divide(nearestDenominator)));
    }

    /** Exact sign reversal preserves every finite component and cannot add speed or a new path. */
    public static Vector reverse(Vector velocity) {
        return velocity == null || !velocity.finite() ? null : new Vector(-velocity.x, -velocity.y, -velocity.z);
    }

    private static Vector facetOffset(Pose pose, int index) { return facetNormal(pose, index).scale(RADIUS); }
    private static Vector horizontalUnit(Vector vector) {
        double scale = Math.max(Math.abs(vector.x), Math.abs(vector.z));
        if (scale == 0) return null;
        double x = vector.x / scale, z = vector.z / scale, length = Math.hypot(x, z);
        return new Vector(x / length, 0, z / length);
    }
    private static void checkIndex(int index) {
        if (index < 0 || index >= FACET_COUNT) throw new IllegalArgumentException("Invalid multi-axis facet index");
    }
    private static void checkPose(Pose pose, int index) {
        if (pose == null) throw new IllegalArgumentException("Missing multi-axis pose");
        checkIndex(index);
    }
    private static BigDecimal exact(double value) { return new BigDecimal(value); }

    private record ExactVector(BigDecimal x, BigDecimal y, BigDecimal z) {
        static ExactVector of(Vector vector) { return new ExactVector(exact(vector.x), exact(vector.y), exact(vector.z)); }
        ExactVector add(ExactVector v) { return new ExactVector(x.add(v.x), y.add(v.y), z.add(v.z)); }
        ExactVector subtract(ExactVector v) { return new ExactVector(x.subtract(v.x), y.subtract(v.y), z.subtract(v.z)); }
        ExactVector scale(BigDecimal scale) { return new ExactVector(x.multiply(scale), y.multiply(scale), z.multiply(scale)); }
        BigDecimal dot(ExactVector v) { return x.multiply(v.x).add(y.multiply(v.y)).add(z.multiply(v.z)); }
        BigDecimal squared() { return dot(this); }
        Vector divide(BigDecimal denominator) {
            return new Vector(x.divide(denominator, OUTPUT_PRECISION).doubleValue(),
                    y.divide(denominator, OUTPUT_PRECISION).doubleValue(),
                    z.divide(denominator, OUTPUT_PRECISION).doubleValue());
        }
    }
}
