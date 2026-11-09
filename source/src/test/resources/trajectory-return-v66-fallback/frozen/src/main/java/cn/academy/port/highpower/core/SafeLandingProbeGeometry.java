package cn.academy.port.highpower.core;

/** Pure exact body translation and finite scan bounds; no point/head-only clearance shortcuts. */
public final class SafeLandingProbeGeometry {
    public static final double SUPPORT_DEPTH = .02, EPSILON = 1e-7, MAX_BODY_EDGE = 4;
    public static final int MAX_CELLS = 1024, MAX_READS = 4096, MAX_SHAPE_BOXES = 1024, MAX_ENTITIES = 64;
    private static final double COORDINATE_LIMIT = 30_000_016;

    public record Point(double x, double y, double z) {
        public boolean finite() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                    && Math.abs(x) <= COORDINATE_LIMIT && Math.abs(y) <= COORDINATE_LIMIT
                    && Math.abs(z) <= COORDINATE_LIMIT;
        }
        public double distanceSquared(Point other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public boolean finite() {
            return new Point(minX, minY, minZ).finite() && new Point(maxX, maxY, maxZ).finite()
                    && minX < maxX && minY < maxY && minZ < maxZ;
        }
        public boolean body() {
            return finite() && maxX - minX <= MAX_BODY_EDGE && maxY - minY <= MAX_BODY_EDGE
                    && maxZ - minZ <= MAX_BODY_EDGE;
        }
    }
    public record Cells(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int count) {}

    private SafeLandingProbeGeometry() {}
    public static boolean withinRange(Point from, Point to) {
        return from != null && to != null && from.finite() && to.finite()
                && from.distanceSquared(to) <= SafeLandingProbeRules.RANGE * SafeLandingProbeRules.RANGE;
    }
    public static Box translatedBody(Box body, Point source, Point destination) {
        if (body == null || !body.body() || source == null || destination == null
                || !withinRange(source, destination)) throw new IllegalArgumentException("Invalid NEW313 body translation");
        double dx = destination.x - source.x, dy = destination.y - source.y, dz = destination.z - source.z;
        Box result = new Box(body.minX + dx, body.minY + dy, body.minZ + dz,
                body.maxX + dx, body.maxY + dy, body.maxZ + dz);
        if (!result.body()) throw new IllegalArgumentException("Invalid translated NEW313 body");
        return result;
    }
    /** The complete current footprint needs support, including its edges. */
    public static Box support(Box body) {
        if (body == null || !body.body()) throw new IllegalArgumentException("Invalid NEW313 support footprint");
        return new Box(body.minX, body.minY - SUPPORT_DEPTH, body.minZ, body.maxX, body.minY, body.maxZ);
    }
    /** Closed native-shape apron: at most one block around a small collision query. */
    public static Cells collisionCells(Box box) {
        if (box == null || !box.body()) throw new IllegalArgumentException("Invalid NEW313 collision query");
        return cells((int)Math.floor(box.minX - EPSILON) - 1, (int)Math.floor(box.minY - EPSILON) - 1,
                (int)Math.floor(box.minZ - EPSILON) - 1, (int)Math.floor(box.maxX + EPSILON) + 1,
                (int)Math.floor(box.maxY + EPSILON) + 1, (int)Math.floor(box.maxZ + EPSILON) + 1);
    }
    public static Cells cells(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long x = (long)maxX - minX + 1, y = (long)maxY - minY + 1, z = (long)maxZ - minZ + 1;
        if (x <= 0 || y <= 0 || z <= 0 || x > MAX_CELLS || y > MAX_CELLS || z > MAX_CELLS
                || x * y * z > MAX_CELLS) throw new IllegalArgumentException("NEW313 scan exceeds cell budget");
        return new Cells(minX, minY, minZ, maxX, maxY, maxZ, (int)(x * y * z));
    }
}
