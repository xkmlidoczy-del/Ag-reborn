package cn.academy.port.highpower.core;

import java.math.BigDecimal;

/**
 * NEW443's fixed diameter-six, height-six cylinder. Every point and bound uses
 * double-precision world coordinates relative to one immutable activation base.
 * This geometry has no camera, tracking-center, collision-world or native types.
 */
public final class WindPressureLiftGeometry {
    public static final double RADIUS = 3, DIAMETER = 6, HEIGHT = 6, WORLD_LIMIT = 30_000_000;
    private static final BigDecimal RADIUS_SQUARED = BigDecimal.valueOf(9);

    private WindPressureLiftGeometry() {}

    public record Point(double x, double y, double z) {
        public boolean finite() {
            return coordinate(x) && coordinate(y) && coordinate(z);
        }
    }

    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public boolean finite() {
            return coordinate(minX) && coordinate(minY) && coordinate(minZ)
                    && coordinate(maxX) && coordinate(maxY) && coordinate(maxZ)
                    && minX < maxX && minY < maxY && minZ < maxZ;
        }
    }

    /** Base is the activation's feet-height center, never the eye or camera. */
    public record Frame(Point base) {
        public Frame {
            if (base == null || !base.finite() || !boxAt(base).finite())
                throw new IllegalArgumentException("Invalid NEW443 fixed column");
        }
    }

    public static Frame frame(Point base) { return new Frame(base); }

    /** Broad-phase outer bounds; their square corners are outside the cylinder. */
    public static Box bounds(Frame frame) {
        if (frame == null) throw new IllegalArgumentException("Missing NEW443 column");
        return boxAt(frame.base);
    }

    /** Inclusive exact full-body containment, including all four horizontal corners. */
    public static boolean contains(Frame frame, Box box) {
        if (frame == null || box == null || !box.finite()) return false;
        Point base = frame.base;
        if (box.minY < base.y || box.maxY > base.y + HEIGHT) return false;
        return insideDisk(box.minX, box.minZ, base) && insideDisk(box.minX, box.maxZ, base)
                && insideDisk(box.maxX, box.minZ, base) && insideDisk(box.maxX, box.maxZ, base);
    }

    /**
     * Conservative full-body swept AABB. The adapter must check this whole volume
     * for loaded chunks, world border and native collisions before applying motion.
     * It is deliberately not a center ray or an endpoint-only collision query.
     */
    public static Box sweptBox(Box box, WindPressureLiftRules.Velocity delta) {
        return delta == null ? null : sweptBox(box, delta.x(), delta.y(), delta.z());
    }

    public static Box sweptBox(Box box, double dx, double dy, double dz) {
        if (box == null || !box.finite() || !Double.isFinite(dx)
                || !Double.isFinite(dy) || !Double.isFinite(dz)) return null;
        Box swept = new Box(box.minX + Math.min(0, dx), box.minY + Math.min(0, dy),
                box.minZ + Math.min(0, dz), box.maxX + Math.max(0, dx),
                box.maxY + Math.max(0, dy), box.maxZ + Math.max(0, dz));
        return swept.finite() ? swept : null;
    }

    /** Conservatively includes every translated body during a straight native step. */
    public static boolean containsSweep(Frame frame, Box box, WindPressureLiftRules.Velocity delta) {
        return contains(frame, sweptBox(box, delta));
    }

    public static boolean containsSweep(Frame frame, Box box, double dx, double dy, double dz) {
        return contains(frame, sweptBox(box, dx, dy, dz));
    }

    private static boolean insideDisk(double x, double z, Point base) {
        double dx = x - base.x, dz = z - base.z;
        double squared = dx * dx + dz * dz;
        double uncertainty = 32 * Math.ulp(Math.max(9, squared));
        if (Math.abs(squared - 9) > uncertainty) return squared < 9;
        // There is no epsilon apron. Compare the exact represented world values
        // when binary subtraction or multiplication could blur the boundary.
        BigDecimal exactX = new BigDecimal(x).subtract(new BigDecimal(base.x));
        BigDecimal exactZ = new BigDecimal(z).subtract(new BigDecimal(base.z));
        return exactX.multiply(exactX).add(exactZ.multiply(exactZ)).compareTo(RADIUS_SQUARED) <= 0;
    }

    private static Box boxAt(Point base) {
        return new Box(base.x - RADIUS, base.y, base.z - RADIUS,
                base.x + RADIUS, base.y + HEIGHT, base.z + RADIUS);
    }

    private static boolean coordinate(double value) {
        return Double.isFinite(value) && Math.abs(value) <= WORLD_LIMIT;
    }
}
