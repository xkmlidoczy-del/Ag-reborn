package cn.academy.port.city;

import cn.academy.port.train.NorthSouthLine;
import cn.academy.port.train.TrainLine;
import java.util.HashSet;

/** Station geometry, grade-separated crossing and timetable in both directions. */
public final class CityNorthSouthTransitRegressionTest {
    public static void main(String[] args) {
        long seed = 20261103L;
        var stops = new HashSet<String>();
        for (double t = 0; t < NorthSouthLine.CYCLE; t += .25) {
            var s = NorthSouthLine.at(t);
            if (s.boarding()) stops.add(s.station() + ":" + s.dir());
            check(s.z() >= -800.001 && s.z() <= 1000.001 && s.speed() >= 0 && s.speed() <= NorthSouthLine.VMAX + .001,
                    "invalid position or speed at " + t);
        }
        check(stops.equals(java.util.Set.of("0:1", "1:1", "2:1", "3:-1", "2:-1", "1:-1")), "both directions and termini: " + stops);
        for (int station = 0; station < 4; station++) {
            int center = NorthSouthLine.STATIONS.get(station).z();
            check(NorthSouthLine.at(NorthSouthLine.dwellStart(station, true) + 2).boarding(), "station opens");
            for (int side : new int[]{-3, 3}) for (int role = 0; role < 3; role++) for (int dz = -2; dz <= 2; dz++) {
                int z = center + (int) Math.round((role - 1) * TrainLine.CAR_PITCH) + dz;
                check(block(seed, 150 + side, z, 45).startsWith("academy:platform_gate"), "lower gate");
                check(block(seed, 150 + side, z, 46).startsWith("academy:platform_gate"), "upper gate");
            }
            for (int dz = 51; dz <= 95; dz++) for (int side : new int[]{-8, -7, -6, 6, 7, 8}) {
                int x = 150 + side, y = 95 - dz;
                check(block(seed, x, center + dz, y).startsWith("minecraft:quartz_stairs"), "station stairs");
                check(CityTemplateSites.at(seed, x, center + dz) == null, "stairs clear of template");
            }
            for (int side : new int[]{-9, 9})
                check("minecraft:quartz_slab[type=bottom]".equals(block(seed, 150 + side, center + 95, 0)), "side stair landing");
            for (int side : new int[]{-9, 9}) for (int role = 0; role < 3; role++) {
                int z = center + (int) Math.round((role - 1) * TrainLine.CAR_PITCH);
                check("academy:led_screen".equals(block(seed, 150 + side, z, 45)), "boarding screen");
            }
        }
        check("academy:reinforced_concrete".equals(block(seed, 150, 400, 43)), "upper structure above east-west rail");
        check("academy:brushed_steel".equals(block(seed, 150, 400, 44)), "upper rail");
        check(CityTransit.overlay(150, 400) != null, "lower rail remains present");
        for (int x = -68; x <= 142; x++) for (int z = 494; z <= 496; z++) {
            check(CityTemplateSites.at(seed, x, z) == null && CityNeighborhoodProps.at(seed, x, z) == null,
                    "central pedestrian transfer blocked by building or pole");
            var o = CityNorthSouthTransit.overlay(seed, x, z);
            check(o != null, "central transfer must have a continuous floor");
            if (x < 142) check("academy:plaza_tile".equals(block(seed, x, z, -1)), "transfer paving");
        }
        int crossing = 0;
        for (int x = -68; x <= 137; x++) for (int z = 96; z <= 100; z++) {
            String floor = block(seed, x, z, -1);
            check(!floor.isEmpty() && "minecraft:air".equals(block(seed, x, z, 0))
                    && "minecraft:air".equals(block(seed, x, z, 2)), "campus walkway headroom");
            check(CityTemplateSites.at(seed, x, z) == null && CityNeighborhoodProps.at(seed, x, z) == null
                    && CityStreetProps.at(seed, x, z) == null && CityDistrictLandmarks.at(seed, x, z) == null,
                    "campus walkway conflicts with higher-priority feature at " + x + "," + z);
            if ("minecraft:white_concrete".equals(floor)) crossing++;
        }
        check(crossing > 0, "campus walkway needs marked road crossings");
        for (var stop : NorthSouthLine.STATIONS) for (int x = 132; x <= 137; x++) for (int dz = 96; dz <= 100; dz++) {
            int z = stop.z() + dz;
            check(!block(seed, x, z, -1).isEmpty() && "minecraft:air".equals(block(seed, x, z, 0))
                    && CityNeighborhoodProps.at(seed, x, z) == null && CityTemplateSites.at(seed, x, z) == null,
                    "station footpath to existing sidewalk");
        }
        check("minecraft:lantern[hanging=false,waterlogged=false]".equals(block(seed, 16, 94, 4))
                && "minecraft:oak_stairs[facing=south]".equals(block(seed, 32, 93, 0)), "campus lights and benches");
        int d15Crossings = 0;
        for (int x = -68; x <= 100; x++) for (int z = 695; z <= 699; z++) {
            check(CityTemplateSites.at(seed, x, z) == null && CityNeighborhoodProps.at(seed, x, z) == null
                    && CityStreetProps.at(seed, x, z) == null && CityDistrictLandmarks.at(seed, x, z) == null,
                    "D15 landmark approach conflict at " + x + "," + z);
            check(!block(seed, x, z, -1).isEmpty() && "minecraft:air".equals(block(seed, x, z, 0)), "D15 landmark approach");
            if ("minecraft:white_concrete".equals(block(seed, x, z, -1))) d15Crossings++;
        }
        for (int x = 96; x <= 100; x++) for (int z = 695; z <= 1100; z++) {
            var sample = CityPlan.sample(seed, x, z);
            check(CityTemplateSites.at(seed, x, z) == null && CityNeighborhoodProps.at(seed, x, z) == null
                    && CityCommercialStreets.at(seed, x, z, sample) == null && CityDistrictLandmarks.at(seed, x, z) == null,
                    "D15 north-south path conflict at " + x + "," + z);
            check(!block(seed, x, z, -1).isEmpty() && "minecraft:air".equals(block(seed, x, z, 0)), "D15 north-south path");
            if ("minecraft:white_concrete".equals(block(seed, x, z, -1))) d15Crossings++;
        }
        for (int x = 96; x <= 142; x++) for (int z = 1096; z <= 1100; z++)
            check(!block(seed, x, z, -1).isEmpty() && "minecraft:air".equals(block(seed, x, z, 0)), "D15 station approach");
        check(d15Crossings > 0, "D15 path needs marked road crossings");
        System.out.println("PASS four north-south stations, bidirectional cycle, gates, stairs, campus and D15 access");
    }
    private static String block(long seed, int x, int z, int y) {
        var over = CityNorthSouthTransit.overlay(seed, x, z);
        if (over != null) for (int i = over.length - 1; i >= 0; i--) if (over[i].y() == y) return over[i].block();
        return "";
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
