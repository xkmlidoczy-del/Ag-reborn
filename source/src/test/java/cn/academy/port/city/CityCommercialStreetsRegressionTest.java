package cn.academy.port.city;

import java.util.HashSet;
import java.util.Set;

/** Verify pedestrian clearances and generated street coverage, not just a list of sample coordinates. */
public final class CityCommercialStreetsRegressionTest {
    public static void main(String[] args) {
        for (long seed : new long[]{20261103L, 42L, 0L}) {
            int posts = 0, seats = 0, lights = 0, planters = 0;
            Set<Integer> rows = new HashSet<>(), cols = new HashSet<>();
            for (int z = 400; z < 1200; z++) for (int x = -400; x < 400; x++) {
                var sample = CityPlan.sample(seed, x, z);
                var prop = CityCommercialStreets.at(seed, x, z, sample);
                if (prop == null) continue;
                check(sample.district() == 15 && sample.kind() == CityPlan.Kind.SIDEWALK, "prop outside D15 sidewalk");
                check(CitySampleRoute.at(x, z) == null && CityStreetProps.at(seed, x, z) == null
                        && CityNeighborhoodProps.at(seed, x, z) == null && CityTransit.overlay(x, z) == null,
                        "prop obstructs an existing route, station or light");
                check(CityTemplateSites.at(seed, x, z) == null && CityDistrictLandmarks.at(seed, x, z) == null,
                        "prop overlaps an authored building");
                if (prop.block(0) == null) {
                    check(prop.block(1) == null && "academy:ceiling_light".equals(prop.block(4))
                            && "academy:steel_slab[type=bottom]".equals(prop.block(5)), "lamp arm must leave headroom and support its light");
                    continue;
                }
                int ru = Math.floorMod(x + CityPlan.HALF, CityPlan.CELL) - CityPlan.ARTERIAL_HALF;
                int rw = Math.floorMod(z + CityPlan.HALF, CityPlan.CELL) - CityPlan.ARTERIAL_HALF;
                ru %= CityPlan.BLOCK;
                rw %= CityPlan.BLOCK;
                if (ru == 11 || ru == 60) {
                    int step = ru == 11 ? -1 : 1;
                    for (int n = 1; n <= 2; n++) check(clear(seed, x + n * step, z), "two sidewalk lanes must remain open");
                } else {
                    int step = rw == 11 ? -1 : 1;
                    for (int n = 1; n <= 2; n++) check(clear(seed, x, z + n * step), "two sidewalk lanes must remain open");
                }
                String ground = prop.block(0);
                if (ground.contains("stairs")) seats++;
                else if (ground.startsWith("minecraft:potted_")) planters++;
                else if ("academy:pillar_dark".equals(ground)) {
                    int ax = x + (ru == 11 ? -1 : ru == 60 ? 1 : 0);
                    int az = z + (rw == 11 ? -1 : rw == 60 ? 1 : 0);
                    var arm = CityCommercialStreets.at(seed, ax, az, CityPlan.sample(seed, ax, az));
                    check(arm != null && "academy:ceiling_light".equals(arm.block(4))
                            && "academy:steel_slab[type=bottom]".equals(prop.block(5)), "lamp requires a supported overhanging light at " + x + "," + z + " arm=" + ax + "," + az + " value=" + arm);
                    lights++;
                }
                else if ("academy:led_screen".equals(prop.block(3))) posts++;
                cols.add(Math.floorDiv(x + 400, 64)); rows.add(Math.floorDiv(z - 400, 64));
            }
            check(lights > 150 && seats > 250 && posts > 150 && planters > 150,
                    "commercial core is too sparsely furnished: " + lights + "," + seats + "," + posts + "," + planters);
            check(rows.size() >= 8 && cols.size() >= 8, "commercial street coverage too local");
            System.out.println("PASS D15 seed=" + seed + " lamps=" + lights + " seat blocks=" + seats
                    + " signs=" + posts + " planters=" + planters + " grid=" + cols.size() + "x" + rows.size());
        }
    }
    private static boolean clear(long seed, int x, int z) {
        var c = CityCommercialStreets.at(seed, x, z, CityPlan.sample(seed, x, z));
        return c == null || c.block(0) == null && c.block(1) == null && c.block(2) == null;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
