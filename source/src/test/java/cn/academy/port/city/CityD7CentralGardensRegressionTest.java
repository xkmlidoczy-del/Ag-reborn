package cn.academy.port.city;

/** Verify the garden paths and their separation from the existing D7 route. */
public final class CityD7CentralGardensRegressionTest {
    public static void main(String[] args) {
        int[][] centers = {{-38, -35}, {-38, 68}, {54, -55}, {54, 55}};
        for (long seed : new long[]{0L, 20261103L}) for (int[] center : centers) {
            for (int dx = -9; dx <= 9; dx++) for (int dz = -9; dz <= 9; dz++) {
                int x = center[0] + dx, z = center[1] + dz;
                var garden = CityD7CentralGardens.at(seed, x, z, CityPlan.sample(seed, x, z));
                check(garden != null, "missing garden column at " + x + "," + z);
                check(CitySampleRoute.at(x, z) == null && CityStreetProps.at(seed, x, z) == null
                        && CityDistrictLandmarks.at(seed, x, z) == null && CityTemplateSites.at(seed, x, z) == null,
                        "garden conflicts with protected feature");
                if (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) {
                    check("academy:plaza_tile".equals(garden.block(-1)) && garden.block(0) == null
                            && garden.block(1) == null && garden.block(2) == null, "garden path clearance");
                }
            }
            var lamp = CityD7CentralGardens.at(seed, center[0] + 3, center[1] + 3,
                    CityPlan.sample(seed, center[0] + 3, center[1] + 3));
            check("academy:pillar_white".equals(lamp.block(3))
                    && "minecraft:lantern[hanging=false,waterlogged=false]".equals(lamp.block(4)), "supported garden lantern");
            var tree = CityD7CentralGardens.at(seed, center[0] + 6, center[1] + 6,
                    CityPlan.sample(seed, center[0] + 6, center[1] + 6));
            check(tree.block(0).endsWith("_log") && tree.block(5).contains("_leaves"), "garden tree");
        }
        System.out.println("PASS four D7 plaza gardens, crossings, trees and supported lanterns");
    }
    private static void check(boolean value, String detail) { if (!value) throw new AssertionError(detail); }
}
