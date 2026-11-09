package cn.academy.port.city;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/** District coverage, crossings and conflicts for the shared public-space generator. */
public final class CityNeighborhoodSpacesRegressionTest {
    public static void main(String[] args) {
        long seed = 20261103L;
        Map<Integer, Integer> counts = new HashMap<>();
        var uses = EnumSet.noneOf(CityDistrictZoning.Use.class);
        for (int row = 0; row < 5; row++) for (int col = 0; col < 5; col++) {
            int left = -2000 + col * 800, north = -2000 + row * 800;
            int d = CityPlan.district(left + 400, north + 400);
            if (d < 1 || d > 20 || d == 15) continue;
            for (int bx = 0; bx < 12; bx++) for (int bz = 0; bz < 12; bz++) {
                int ox = left + 24 + bx * 64, oz = north + 24 + bz * 64;
                var center = CityPlan.sample(seed, ox + 24, oz + 24);
                var site = CityNeighborhoodSpaces.at(seed, ox + 24, oz + 24, center);
                if (site == null) continue;
                counts.merge(d, 1, Integer::sum); uses.add(site.use());
                check(CityDistrictLandmarks.at(seed, ox + 24, oz + 24) == null
                                && CityTemplateSites.at(seed, ox + 24, oz + 24) == null
                                && !CityAirportTransit.overlapsLot(ox, oz, 48, 48), "public space conflicts with reserved lot");
                for (int n = 0; n < 48; n++) for (int k = 22; k <= 24; k++) {
                    crossing(seed, ox + k, oz + n);
                    crossing(seed, ox + n, oz + k);
                }
                var light = CityNeighborhoodSpaces.at(seed, ox + 28, oz + 19, CityPlan.sample(seed, ox + 28, oz + 19));
                check(light != null && "academy:pillar_white".equals(light.block(3))
                        && "minecraft:lantern[hanging=false,waterlogged=false]".equals(light.block(4)), "supported space lighting");
                if (site.use() == CityDistrictZoning.Use.INDUSTRIAL || site.use() == CityDistrictZoning.Use.SCIENCE) {
                    for (int a = 8; a <= 39; a++) for (int b = 8; b <= 39; b++) {
                        var part = CityNeighborhoodSpaces.at(seed, ox + a, oz + b, CityPlan.sample(seed, ox + a, oz + b));
                        for (int y = 3; y <= 7; y++) {
                            String block = part.block(y);
                            check(block == null || !block.contains("_leaves"), "industrial equipment must not grow disconnected foliage");
                        }
                    }
                }
                if (site.use() == CityDistrictZoning.Use.SPORTS) {
                    for (int a = 5; a <= 19; a++) for (int b = 5; b <= 20; b++) {
                        var part = CityNeighborhoodSpaces.at(seed, ox + a, oz + b, CityPlan.sample(seed, ox + a, oz + b));
                        String floor = part.block(-1);
                        check(("minecraft:white_concrete".equals(floor) || "minecraft:green_concrete".equals(floor))
                                && part.block(0) == null && part.block(1) == null, "sports court must remain contiguous and unobstructed");
                    }
                }
            }
        }
        for (int d = 1; d <= 20; d++) if (d != 15) check(counts.getOrDefault(d, 0) > 0, "no public-space sample in D" + d);
        check(uses.containsAll(EnumSet.allOf(CityDistrictZoning.Use.class)), "some zoning types lack a public-space sample: " + uses);
        System.out.println("PASS 19 districts, public-space samples=" + counts + " types=" + uses);
    }
    private static void crossing(long seed, int x, int z) {
        var site = CityNeighborhoodSpaces.at(seed, x, z, CityPlan.sample(seed, x, z));
        check(site != null && "academy:plaza_tile".equals(site.block(-1))
                        && site.block(0) == null && site.block(1) == null && site.block(2) == null,
                "public-space crossing obstructed at " + x + "," + z);
        check(CitySampleRoute.at(x, z) == null && CityTransit.overlay(x, z) == null
                        && CityDistrictLandmarks.at(seed, x, z) == null && CityTemplateSites.at(seed, x, z) == null,
                "crossing overlaps protected route or structure");
    }
    private static void check(boolean value, String detail) { if (!value) throw new AssertionError(detail); }
}
