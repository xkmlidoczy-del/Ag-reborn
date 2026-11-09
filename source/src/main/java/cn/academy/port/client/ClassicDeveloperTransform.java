/* Source RenderBlockMulti/RenderBlockMultiModel transforms, adapted under GPLv3. */
package cn.academy.port.client;

/** Pure exact quarter-turn geometry shared by rendering bounds and headless tests. */
public final class ClassicDeveloperTransform {
    public static final float SCALE = .5f;
    public static final float MODEL_Y_ROTATION = 180;
    public static final double PIVOT_X = .5, PIVOT_Y = 0, PIVOT_Z = .5;
    public enum Facing {
        NORTH(180), SOUTH(0), WEST(-90), EAST(90);
        private final float blockRotation;
        Facing(float blockRotation) { this.blockRotation = blockRotation; }
        public float blockRotation() { return blockRotation; }
    }
    public record Point(double x, double y, double z) {}
    private ClassicDeveloperTransform() {}

    public static Point worldPoint(double x, double y, double z, Facing facing) {
        x *= SCALE; y *= SCALE; z *= SCALE;
        return switch (facing) {
            case NORTH -> new Point(PIVOT_X + x, y, PIVOT_Z + z);
            case SOUTH -> new Point(PIVOT_X - x, y, PIVOT_Z - z);
            case WEST -> new Point(PIVOT_X + z, y, PIVOT_Z - x);
            case EAST -> new Point(PIVOT_X - z, y, PIVOT_Z + x);
        };
    }

    public static ClassicDeveloperObj.Bounds worldBounds(ClassicDeveloperObj.Bounds source, Facing facing) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (double x : new double[]{source.minX(), source.maxX()})
            for (double y : new double[]{source.minY(), source.maxY()})
                for (double z : new double[]{source.minZ(), source.maxZ()}) {
                    Point point = worldPoint(x, y, z, facing);
                    minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
                    maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
                }
        return new ClassicDeveloperObj.Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
