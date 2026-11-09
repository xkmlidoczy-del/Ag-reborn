/* AcademyCraft RenderSolarGen and LambdaLib BlockMulti transform, GPLv3. See NOTICE. */
package cn.academy.port.client;

public final class ClassicSolarModelTransform {
    public static final float SCALE = .014f, MODEL_Y_ROTATION = 90;
    private ClassicSolarModelTransform() {}
    public static ClassicDeveloperTransform.Point worldPoint(double x, double y, double z, ClassicDeveloperTransform.Facing facing) {
        x *= SCALE; y *= SCALE; z *= SCALE;
        // R_y(BlockMulti rotation + RenderSolarGen90), pivot (.5,0,.5).
        return switch (facing) {
            case NORTH -> new ClassicDeveloperTransform.Point(.5 - z, y, .5 + x);
            case SOUTH -> new ClassicDeveloperTransform.Point(.5 + z, y, .5 - x);
            case WEST -> new ClassicDeveloperTransform.Point(.5 + x, y, .5 + z);
            case EAST -> new ClassicDeveloperTransform.Point(.5 - x, y, .5 - z);
        };
    }
    public static ClassicDeveloperObj.Bounds worldBounds(ClassicDeveloperObj.Bounds bounds, ClassicDeveloperTransform.Facing facing) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (double x : new double[]{bounds.minX(), bounds.maxX()}) for (double y : new double[]{bounds.minY(), bounds.maxY()}) for (double z : new double[]{bounds.minZ(), bounds.maxZ()}) {
            var point = worldPoint(x, y, z, facing);
            minX = Math.min(minX, point.x()); minY = Math.min(minY, point.y()); minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x()); maxY = Math.max(maxY, point.y()); maxZ = Math.max(maxZ, point.z());
        }
        return new ClassicDeveloperObj.Bounds(minX,minY,minZ,maxX,maxY,maxZ);
    }
}
