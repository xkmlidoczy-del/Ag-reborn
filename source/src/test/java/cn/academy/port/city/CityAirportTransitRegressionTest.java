package cn.academy.port.city;

import cn.academy.port.train.AirportLine;
import cn.academy.port.train.TrainLine;
import java.util.Arrays;

/** Track continuity and conflict checks on the actual column-generation functions. */
public final class CityAirportTransitRegressionTest {
    public static void main(String[] args) {
        long seed = 20261103L;
        for (double d = -40; d <= AirportLine.LENGTH + 40; d += .5) {
            var p = AirportLine.point(d);
            int x = (int) Math.round(p.x()), z = (int) Math.round(p.z());
            check(has(seed, x, z, 24, "academy:brushed_steel"), "track gap at " + x + "," + z);
            check(CityTemplateSites.at(seed, x, z) == null, "track intersects a template");
            check(CityPlan.sample(seed, x, z).kind() != CityPlan.Kind.BUILDING, "track intersects a generated tower");
            check(CityDistrictLandmarks.at(seed, x, z) == null || z == -1790, "track intersects landmark");
            check(CityTransit.overlay(x, z) == null, "airport line intersects existing line");
        }
        for (int role = 0; role < 3; role++) for (int side : new int[]{-1, 1}) {
            int offset = (int) Math.round((role - 1) * TrainLine.CAR_PITCH);
            check(has(seed, 1040 + side * 9, 300 - offset, 25, "academy:led_screen"), "interchange boarding panel");
            check(has(seed, 1560 + offset, -1790 + side * 9, 25, "academy:led_screen"), "airport boarding panel");
            for (int lane = 6; lane <= 8; lane++) {
                check(!has(seed, 1040 + side * lane, 300 - offset, 25, "academy:led_screen"), "interchange stair approach blocked");
                check(!has(seed, 1560 + offset, -1790 + side * lane, 25, "academy:led_screen"), "airport stair approach blocked");
            }
            check(hasPrefix(seed, 1040 + side * 3, 300 - offset, 25, "academy:platform_gate["), "interchange gate");
            check(hasPrefix(seed, 1560 + offset, -1790 + side * 3, 25, "academy:platform_gate["), "airport gate");
        }
        for (int n = 0; n <= 24; n++) {
            check(has(seed, 1046, 375 - n, n, "minecraft:quartz_stairs[facing=north,half=bottom]"), "interchange stair");
            check(has(seed, 1485 + n, -1783, n, "minecraft:quartz_stairs[facing=east,half=bottom]"), "airport stair");
        }
        for (int x = 927; x <= 1032; x++) check(has(seed, x, 375, -1, "academy:plaza_tile"), "transfer walkway gap");
        check(has(seed, 925, 391, 0, "minecraft:quartz_slab[type=bottom]"), "step-free approach to old station stairs");
        check(has(seed, 1030, 300, 25, "minecraft:iron_bars"), "interchange platform edge guard");
        check(has(seed, 1560, -1800, 25, "minecraft:iron_bars"), "airport platform edge guard");
        java.util.Set<Integer> groveDistricts = new java.util.HashSet<>();
        for (int z = -1704; z <= 296; z += 8) for (int x = 960; x <= 1120; x += 8) {
            var sample = CityPlan.sample(seed, x, z);
            var grove = CityAirportCorridor.at(seed, x, z, sample);
            if (grove == null || !grove.block(0).endsWith("_log")) continue;
            check(sample.kind() == CityPlan.Kind.PARK && !CityAirportTransit.reserved(x, z), "grove must remain outside the track");
            check(CityDistrictLandmarks.at(seed, x, z) == null && CityTemplateSites.at(seed, x, z) == null,
                    "grove intersects an authored structure");
            groveDistricts.add(sample.district());
        }
        check(groveDistricts.containsAll(java.util.Set.of(4, 6, 18)), "airport corridor needs groves through D4, D6 and D18: " + groveDistricts);
        System.out.println("PASS airport guideway, station gates, stairs, transfer footpath and building clearance");
    }
    private static boolean has(long seed, int x, int z, int y, String s) {
        var c = CityAirportTransit.overlay(seed, x, z);
        return c != null && Arrays.stream(c).anyMatch(v -> v.y() == y && v.block().equals(s));
    }
    private static boolean hasPrefix(long seed, int x, int z, int y, String s) {
        var c = CityAirportTransit.overlay(seed, x, z);
        return c != null && Arrays.stream(c).anyMatch(v -> v.y() == y && v.block().startsWith(s));
    }
    private static void check(boolean value, String msg) { if (!value) throw new AssertionError(msg); }
}
