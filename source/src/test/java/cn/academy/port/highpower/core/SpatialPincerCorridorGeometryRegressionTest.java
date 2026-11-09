package cn.academy.port.highpower.core;

/** Closed 64m physical route and entrance-A tests, independent of any loading world API. */
public final class SpatialPincerCorridorGeometryRegressionTest {
    private static int checks;
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    public static void main(String[] args) {
        for (double source : new double[]{0, .125, .75, 1, 16, 32, 48, 63, 64}) {
            double flight = 64 - source;
            check(SpatialPincerCorridorRules.path(source, flight), "exact total64m includes both distances");
            check(!SpatialPincerCorridorRules.path(source, flight + .000001), "flight cannot exceed remaining route budget");
            check(!SpatialPincerCorridorRules.path(source + .000001, flight), "source leg cannot exceed remaining route budget");
        }
        check(SpatialPincerCorridorRules.path(0, 0) && SpatialPincerCorridorRules.path(32.125, 31.875), "zero and fractional route lengths stay finite");
        check(!SpatialPincerCorridorRules.path(0, Math.nextUp(64d)) && !SpatialPincerCorridorRules.path(Math.nextUp(64d), 0),
                "one representable step beyond absolute boundary is refused");
        check(!SpatialPincerCorridorRules.path(-Double.MIN_VALUE, 0) && !SpatialPincerCorridorRules.path(0, -Double.MIN_VALUE),
                "negative segment cannot subtract from path budget");
        for (double horizontal : new double[]{0, .1, .5, .75}) for (double vertical : new double[]{-.125, -.1, 0, .1, .125})
            check(SpatialPincerCorridorRules.nearEntrance(horizontal, vertical), "entrance A uses separate horizontal and vertical bounds");
        check(!SpatialPincerCorridorRules.nearEntrance(Math.nextUp(.75), 0), "next horizontal value outside A is refused");
        check(!SpatialPincerCorridorRules.nearEntrance(0, Math.nextUp(.125))
                && !SpatialPincerCorridorRules.nearEntrance(0, Math.nextDown(-.125)), "both vertical faces are exact");
        check(!SpatialPincerCorridorRules.nearEntrance(-Double.MIN_VALUE, 0), "negative horizontal distance is not a valid radial measure");
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE}) {
            check(!SpatialPincerCorridorRules.path(bad, 0) && !SpatialPincerCorridorRules.path(0, bad), "invalid or unbounded path refuses");
            check(!SpatialPincerCorridorRules.nearEntrance(bad, 0) && !SpatialPincerCorridorRules.nearEntrance(0, bad), "invalid or unbounded A entry refuses");
        }
        check(SpatialPincerCorridorRules.ORIGIN_HEIGHT == 1.2 && SpatialPincerCorridorRules.SPEED == 1.6
                && SpatialPincerCorridorRules.RANGE == 64, "frozen launch geometry retains provisional native constants");
        double cone = Math.cos(Math.toRadians(12));
        check(SpatialPincerCorridorRules.inCone(cone, 64), "exact 12 degree half-angle and 64m center boundary included");
        check(!SpatialPincerCorridorRules.inCone(Math.nextDown(cone), 64), "next representable outside cone excluded");
        check(!SpatialPincerCorridorRules.inCone(1, Math.nextUp(64d)), "next representable target beyond64m excluded");
        check(SpatialPincerCorridorRules.inCone(1, .001) && !SpatialPincerCorridorRules.inCone(-1, 1)
                && !SpatialPincerCorridorRules.inCone(1, 0), "finite positive forward target centers only");
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            check(!SpatialPincerCorridorRules.inCone(bad, 1) && !SpatialPincerCorridorRules.inCone(1, bad), "invalid cone inputs refuse");
        System.out.println("SpatialPincerCorridorGeometryRegressionTest: " + checks + " checks passed");
    }
}
