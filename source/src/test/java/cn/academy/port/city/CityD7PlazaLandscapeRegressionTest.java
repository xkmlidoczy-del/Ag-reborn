package cn.academy.port.city;

/** Preserve plaza gardens and routes while extending the surrounding green space. */
public final class CityD7PlazaLandscapeRegressionTest {
    public static void main(String[] args) {
        long seed = 20261103L;
        int grass = 0, path = 0;
        for (int x = 30; x <= 90; x++) for (int z = -90; z <= 90; z++) {
            if (Math.abs(z) < 27) continue;
            var sample = CityPlan.sample(seed, x, z);
            var garden = CityD7CentralGardens.at(seed, x, z, sample);
            var park = CityD7PlazaLandscape.at(seed, x, z, sample);
            if (garden != null) { check(park == null, "landscape overlaps a garden"); continue; }
            check(park != null && CitySampleRoute.at(x, z) == null && CityStreetProps.at(seed, x, z) == null
                    && CityDistrictLandmarks.at(seed, x, z) == null, "landscape conflicts with a landmark or route");
            String floor = park.block(-1);
            if ("minecraft:grass_block".equals(floor)) grass++;
            if ((x >= 53 && x <= 55) || (Math.abs(z) >= 54 && Math.abs(z) <= 56)) {
                check("academy:plaza_tile".equals(floor) && park.block(0) == null && park.block(2) == null,
                        "landscape path obstructed at " + x + "," + z);
                path++;
            }
        }
        for (int[] p : new int[][]{{36,-34},{36,-74},{73,-34},{75,-75},{85,-50},{85,-85},
                {36,34},{36,74},{73,34},{75,75},{85,50},{85,85}}) {
            var tree = CityD7PlazaLandscape.at(seed, p[0], p[1], CityPlan.sample(seed, p[0], p[1]));
            check(tree != null && tree.block(0).endsWith("_log") && tree.block(5).contains("_leaves"), "supported landscape tree");
        }
        for (long variant : new long[]{0L, seed}) {
            int westGrass = 0, westPath = 0;
            for (int x = -95; x <= -77; x++) for (int z = 2; z <= 82; z++) {
                var sample = CityPlan.sample(variant, x, z);
                var park = CityD7PlazaLandscape.at(variant, x, z, sample);
                boolean protectedColumn = CitySampleRoute.at(x, z) != null || CityStreetProps.at(variant, x, z) != null
                        || CityDistrictLandmarks.at(variant, x, z) != null
                        || CityD7CentralGardens.at(variant, x, z, sample) != null
                        || CityNorthSouthTransit.overlapsLot(x, z, 1, 1);
                if (protectedColumn || sample.kind() != CityPlan.Kind.PLAZA) {
                    check(park == null, "west landscape overlaps protected column " + x + "," + z);
                    continue;
                }
                check(park != null, "missing west landscape " + x + "," + z);
                if ("minecraft:grass_block".equals(park.block(-1))) westGrass++;
                if (x >= -83 && x <= -82) {
                    check("academy:plaza_tile".equals(park.block(-1)) && park.block(0) == null
                            && park.block(2) == null, "west promenade blocked " + x + "," + z);
                    westPath++;
                }
            }
            for (int[] p : new int[][]{{-91,9},{-78,9},{-91,29},{-78,29},
                    {-91,62},{-78,62},{-91,75},{-78,75}}) {
                var tree = CityD7PlazaLandscape.at(variant, p[0], p[1], CityPlan.sample(variant, p[0], p[1]));
                check(tree != null && tree.block(0).endsWith("_log") && tree.block(5).contains("_leaves"),
                        "west tree missing at " + p[0] + "," + p[1]);
            }
            for (int z : new int[]{12,30,45,60,78}) {
                var lamp = CityD7PlazaLandscape.at(variant, -84, z, CityPlan.sample(variant, -84, z));
                check(lamp != null && "academy:pillar_white".equals(lamp.block(3))
                        && "minecraft:lantern[hanging=false,waterlogged=false]".equals(lamp.block(4)),
                        "west lantern lacks support at " + z);
            }
            check(westGrass > 800 && westPath >= 150, "west landscape coverage " + westGrass + ", " + westPath);
        }
        check(grass > 5000 && path > 300, "insufficient green or connecting paths: " + grass + ", " + path);
        System.out.println("PASS D7 lawns, west promenade, trees and unobstructed garden connections; east grass=" + grass);
    }
    private static void check(boolean value, String detail) { if (!value) throw new AssertionError(detail); }
}
