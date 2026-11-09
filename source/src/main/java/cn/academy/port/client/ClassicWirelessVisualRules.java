/* AcademyCraft1.0.7 BlockNode, GuiNode, TechUI and RenderMatrix; GPLv3. See NOTICE. */
package cn.academy.port.client;

/** Pure source-derived render/animation policy; does not infer authoritative links on the client. */
public final class ClassicWirelessVisualRules {
    public enum OriginalFacing {
        NORTH(180, 1, 1), SOUTH(0, 0, 0), WEST(-90, 1, 0), EAST(90, 0, 1);
        private final float rotation;
        private final double pivotX, pivotZ;
        OriginalFacing(float rotation, double pivotX, double pivotZ) { this.rotation = rotation; this.pivotX = pivotX; this.pivotZ = pivotZ; }
        public float rotation() { return rotation; }
        public double pivotX() { return pivotX; }
        public double pivotZ() { return pivotZ; }
    }
    public record Point(double x, double y, double z) {}
    public record Shield(double rotation, double height) {}
    private ClassicWirelessVisualRules() {}
    public static int nodeEnergyLevel(double energy, double maximum) {
        if (!Double.isFinite(energy) || !Double.isFinite(maximum) || maximum <= 0) return 0;
        return (int)Math.max(0, Math.min(4, Math.round(4 * energy / maximum)));
    }
    public static int nodeTop(boolean enabled) { return enabled ? 1 : 0; }
    public static int shieldCount(int plateCount) { return plateCount == 3 ? 3 : 0; }
    public static Shield shield(long gameMillis, int index) {
        if (index < 0 || index >= 3) throw new IllegalArgumentException("shield index must be0..2");
        return new Shield((gameMillis / 20.0) % 360 + 120 * index,
                .1 * Math.sin(gameMillis / 900.0 + 40 * index));
    }
    public static Point matrixPoint(double x, double y, double z, OriginalFacing facing) {
        return switch (facing) {
            case NORTH -> new Point(1 - x, y, 1 - z);
            case SOUTH -> new Point(x, y, z);
            case WEST -> new Point(1 - z, y, x);
            case EAST -> new Point(z, y, 1 - x);
        };
    }
    public static ClassicDeveloperObj.Bounds matrixBounds(ClassicDeveloperObj.Bounds bounds, OriginalFacing facing) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (double x : new double[]{bounds.minX(), bounds.maxX()}) for (double y : new double[]{bounds.minY(), bounds.maxY()})
            for (double z : new double[]{bounds.minZ(), bounds.maxZ()}) {
                Point p = matrixPoint(x,y,z,facing);
                minX = Math.min(minX,p.x()); minY = Math.min(minY,p.y()); minZ = Math.min(minZ,p.z());
                maxX = Math.max(maxX,p.x()); maxY = Math.max(maxY,p.y()); maxZ = Math.max(maxZ,p.z());
            }
        return new ClassicDeveloperObj.Bounds(minX,minY,minZ,maxX,maxY,maxZ);
    }
    public static int nodeAnimationFrame(boolean linked, long elapsedMillis) {
        long elapsed = Math.max(0, elapsedMillis);
        return linked ? (int)((elapsed / 800) % 8) : 8 + (int)((elapsed / 3000) % 2);
    }
    public static double breatheAlpha(long gameMillis) { return .675 + (1 + Math.sin(gameMillis / 800.0)) * .5 * .175; }
    public static double histogramFraction(double amount, double maximum) {
        if (!Double.isFinite(amount) || !Double.isFinite(maximum) || maximum <= 0) return .03;
        return Math.max(.03, Math.min(1,amount/maximum));
    }
}
