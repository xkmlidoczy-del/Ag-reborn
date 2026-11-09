package cn.academy.port.city;

import cn.academy.port.train.NorthSouthLine;
import cn.academy.port.train.TrainLine;
import java.util.ArrayList;
import java.util.List;

/** Elevated stations and their at-grade pedestrian connections. */
public final class CityNorthSouthTransit {
    private CityNorthSouthTransit() {}
    private static boolean intersects(int x, int z, int w, int d, int a, int b, int aw, int ad) {
        return x < a + aw && x + w > a && z < b + ad && z + d > b;
    }
    static boolean overlapsLot(int x, int z, int width, int depth) {
        if (intersects(x, z, width, depth, 136, -850, 29, 1901)
                || intersects(x, z, width, depth, -68, 494, 220, 3)
                || intersects(x, z, width, depth, -68, 93, 206, 11)
                || intersects(x, z, width, depth, -68, 695, 169, 5)
                || intersects(x, z, width, depth, 94, 695, 9, 406)
                || intersects(x, z, width, depth, 96, 1096, 47, 5)) return true;
        for (var station : NorthSouthLine.STATIONS)
            if (intersects(x, z, width, depth, 135, station.z() - 50, 31, 151)
                    || intersects(x, z, width, depth, 132, station.z() + 96, 6, 5)) return true;
        return false;
    }
    public static CityTransit.Over[] overlay(long seed, int x, int z) {
        if (!overlapsLot(x, z, 1, 1)) return null;
        int cross = x - NorthSouthLine.X;
        int station = NorthSouthLine.stationNear(z, 50);
        int along = station < 0 ? 0 : z - NorthSouthLine.STATIONS.get(station).z();
        boolean platform = station >= 0 && Math.abs(cross) <= 10;
        boolean line = z >= -850 && z <= 1050 && Math.abs(cross) <= 4;
        List<CityTransit.Over> out = new ArrayList<>();
        boolean transfer = x >= -68 && x <= 151 && z >= 494 && z <= 496;
        if (transfer) {
            out.add(new CityTransit.Over(-1, "academy:plaza_tile"));
            column(out, 0, 3, "minecraft:air");
        }
        boolean campusWalk = x >= -68 && x <= 137 && z >= 96 && z <= 100;
        boolean sidewalkLink = false;
        for (var stop : NorthSouthLine.STATIONS)
            if (x >= 132 && x <= 137 && z >= stop.z() + 96 && z <= stop.z() + 100) sidewalkLink = true;
        if (campusWalk || sidewalkLink) {
            var kind = CityPlan.sample(seed, x, z).kind();
            boolean road = kind == CityPlan.Kind.ROAD || kind == CityPlan.Kind.MARKING;
            String floor = road ? (Math.floorMod(z, 2) == 0 ? "minecraft:white_concrete" : "academy:asphalt")
                    : campusWalk && (z == 96 || z == 100) ? "minecraft:cyan_concrete" : "academy:plaza_tile";
            out.add(new CityTransit.Over(-1, floor));
            column(out, 0, 3, "minecraft:air");
        }
        // D15's south station connects to the existing landmark approach at Z=699.
        boolean d15West = x >= -68 && x <= 100 && z >= 695 && z <= 699;
        boolean d15North = x >= 96 && x <= 100 && z >= 695 && z <= 1100;
        boolean d15Station = x >= 96 && x <= 142 && z >= 1096 && z <= 1100;
        if (d15West || d15North || d15Station) {
            var kind = CityPlan.sample(seed, x, z).kind();
            boolean road = kind == CityPlan.Kind.ROAD || kind == CityPlan.Kind.MARKING;
            int stripe = d15North && !d15West && !d15Station ? x : z;
            boolean edge = d15North && !d15West && !d15Station ? x == 96 || x == 100
                    : z == 695 || z == 699 || z == 1096 || z == 1100;
            String floor = road ? (Math.floorMod(stripe, 2) == 0 ? "minecraft:white_concrete" : "academy:asphalt")
                    : edge ? "minecraft:yellow_concrete" : "academy:plaza_tile";
            out.add(new CityTransit.Over(-1, floor));
            column(out, 0, 3, "minecraft:air");
        }
        if ((x == 94 || x == 102) && z >= 720 && z <= 1080 && Math.floorMod(z - 720, 40) == 0) {
            var kind = CityPlan.sample(seed, x, z).kind();
            if (kind != CityPlan.Kind.ROAD && kind != CityPlan.Kind.MARKING) {
                out.add(new CityTransit.Over(-1, "academy:plaza_tile"));
                column(out, 0, 3, "academy:pillar_white");
                out.add(new CityTransit.Over(4, "minecraft:lantern[hanging=false,waterlogged=false]"));
            }
        }
        // The lighting and seating stand outside the five-wide route, leaving it clear.
        if (x >= -48 && x <= 112 && Math.floorMod(x + 48, 32) == 0 && (z == 94 || z == 102)) {
            var kind = CityPlan.sample(seed, x, z).kind();
            if (kind != CityPlan.Kind.ROAD && kind != CityPlan.Kind.MARKING) {
                out.add(new CityTransit.Over(-1, "academy:plaza_tile"));
                column(out, 0, 3, "academy:pillar_white");
                out.add(new CityTransit.Over(4, "minecraft:lantern[hanging=false,waterlogged=false]"));
            }
        }
        if ((x == -32 || x == 32 || x == 96) && (z == 93 || z == 103)) {
            var kind = CityPlan.sample(seed, x, z).kind();
            if (kind != CityPlan.Kind.ROAD && kind != CityPlan.Kind.MARKING) {
                out.add(new CityTransit.Over(-1, "academy:plaza_tile"));
                out.add(new CityTransit.Over(0, "minecraft:oak_stairs[facing=" + (z == 93 ? "south" : "north") + "]"));
            }
        }
        if (line || platform) {
            out.add(new CityTransit.Over(43, "academy:reinforced_concrete"));
            String floor = platform && Math.abs(cross) >= 3 ? "academy:plaza_tile" : "academy:reinforced_concrete";
            if (Math.abs(cross) <= 1) floor = "academy:brushed_steel";
            if (platform && Math.abs(cross) == 3) floor = "minecraft:yellow_concrete";
            out.add(new CityTransit.Over(44, floor));
            if (!platform && Math.abs(cross) == 4) out.add(new CityTransit.Over(45, "minecraft:iron_bars"));
        }
        if (platform) {
            boolean stairOpening = along == 50 && Math.abs(cross) >= 6 && Math.abs(cross) <= 8;
            if ((Math.abs(cross) == 10 || Math.abs(along) == 50) && !stairOpening)
                out.add(new CityTransit.Over(45, "minecraft:iron_bars"));
            out.add(new CityTransit.Over(54, "academy:white_panel_slab[type=bottom]"));
            if (Math.abs(cross) == 10 && Math.floorMod(along, 20) == 0)
                column(out, 45, 53, "academy:pillar_white");
            if (Math.abs(cross) <= 9 && Math.floorMod(cross, 3) == 0 && Math.floorMod(along, 5) == 0)
                out.add(new CityTransit.Over(53, "academy:ceiling_light"));
            boolean bay = false;
            for (int car = 0; car < 3; car++) {
                int offset = (int) Math.round((car - 1) * TrainLine.CAR_PITCH);
                if (Math.abs(along - offset) <= 2) bay = true;
                if (along == offset && Math.abs(cross) == 9) out.add(new CityTransit.Over(45, "academy:led_screen"));
                if (Math.abs(along - offset) <= 2 && Math.abs(cross) == 3) {
                    String p = "[facing=" + (cross < 0 ? "west" : "east")
                            + ",hinge=" + (Math.floorMod(along, 2) == 0 ? "left" : "right")
                            + ",open=false,powered=false,half=";
                    out.add(new CityTransit.Over(45, "academy:platform_gate" + p + "lower]"));
                    out.add(new CityTransit.Over(46, "academy:platform_gate" + p + "upper]"));
                }
            }
            if (!bay && Math.abs(cross) == 2) out.add(new CityTransit.Over(45, "minecraft:iron_bars"));
        }
        for (var stop : NorthSouthLine.STATIONS) {
            int delta = z - stop.z();
            if (delta >= 51 && delta <= 95 && Math.abs(cross) >= 6 && Math.abs(cross) <= 8) {
                int step = 95 - delta;
                if (step > 0) out.add(new CityTransit.Over(step - 1, "academy:brushed_steel"));
                out.add(new CityTransit.Over(step, "minecraft:quartz_stairs[facing=north,half=bottom]"));
            }
            if (delta >= 51 && delta <= 92 && Math.abs(cross) == 9)
                out.add(new CityTransit.Over(96 - delta, "minecraft:iron_bars"));
            // A half-step lets the transfer path approach the stair from its side.
            if (delta == 95 && Math.abs(cross) == 9)
                out.add(new CityTransit.Over(0, "minecraft:quartz_slab[type=bottom]"));
            if (delta >= 96 && delta <= 100 && Math.abs(cross) <= 12) {
                out.add(new CityTransit.Over(-1, "academy:plaza_tile"));
                column(out, 0, 3, "minecraft:air");
            }
        }
        // Keep all supports clear of the lower east–west viaduct and of road surfaces.
        if (line && Math.abs(cross) <= 1 && Math.floorMod(z, 24) == 0 && Math.abs(z - 400) > 28) {
            var kind = CityPlan.sample(seed, x, z).kind();
            if (kind != CityPlan.Kind.ROAD && kind != CityPlan.Kind.MARKING
                    && CityDistrictLandmarks.at(seed, x, z) == null)
                column(out, 0, 42, "academy:pillar_dark");
        }
        return out.isEmpty() ? null : out.toArray(CityTransit.Over[]::new);
    }
    private static void column(List<CityTransit.Over> out, int low, int high, String block) {
        for (int y = low; y <= high; y++) out.add(new CityTransit.Over(y, block));
    }
}
