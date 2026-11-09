package cn.academy.port.city;

import cn.academy.port.train.AirportLine;
import cn.academy.port.train.TrainLine;
import java.util.ArrayList;
import java.util.List;

/** Chunk-independent guideway and the two airport shuttle platforms. */
public final class CityAirportTransit {
    private CityAirportTransit() {}

    /** Conservative reservation: keep a complete template lot out of the viaduct corridor. */
    static boolean overlapsLot(int x, int z, int width, int depth) {
        return overlaps(x, z, width, depth, 1024, -1726, 33, 2103)
                || overlaps(x, z, width, depth, 1024, -1806, 112, 96)
                || overlaps(x, z, width, depth, 1104, -1806, 507, 33)
                || overlaps(x, z, width, depth, 924, 374, 124, 3)
                || overlaps(x, z, width, depth, 924, 374, 3, 19);
    }

    private static boolean overlaps(int x, int z, int w, int d, int a, int b, int aw, int ad) {
        return x < a + aw && x + w > a && z < b + ad && z + d > b;
    }

    /** Prevent procedural towers from filling the travel corridor. */
    static boolean reserved(int x, int z) { return closest(x, z)[0] <= 14; }

    /** [distance to centreline, distance along line]. Includes 50 blocks beyond each stop. */
    private static double[] closest(int x, int z) {
        double northZ = Math.max(AirportLine.CURVE_Z, Math.min(AirportLine.SOUTH_Z + 50, z));
        double best = Math.hypot(x - AirportLine.SOUTH_X, z - northZ);
        double along = AirportLine.SOUTH_Z - northZ;
        double eastX = Math.max(AirportLine.CURVE_X, Math.min(AirportLine.AIRPORT_X + 50, x));
        double east = Math.hypot(x - eastX, z - AirportLine.AIRPORT_Z);
        if (east < best) { best = east; along = AirportLine.STRAIGHT_NORTH + AirportLine.CURVE_LENGTH + eastX - AirportLine.CURVE_X; }
        double angle = Math.atan2(z - AirportLine.CURVE_Z, x - AirportLine.CURVE_X);
        if (angle < 0) angle += Math.PI * 2;
        angle = Math.max(Math.PI, Math.min(Math.PI * 1.5, angle));
        double cx = AirportLine.CURVE_X + AirportLine.RADIUS * Math.cos(angle);
        double cz = AirportLine.CURVE_Z + AirportLine.RADIUS * Math.sin(angle);
        double bend = Math.hypot(x - cx, z - cz);
        if (bend < best) { best = bend; along = AirportLine.STRAIGHT_NORTH + (angle - Math.PI) * AirportLine.RADIUS; }
        return new double[]{best, along};
    }

    public static CityTransit.Over[] overlay(long seed, int x, int z) {
        if (!overlapsLot(x, z, 1, 1)) return null;
        double[] nearest = closest(x, z);
        double distance = nearest[0], along = nearest[1];
        boolean interchange = Math.abs(x - AirportLine.SOUTH_X) <= 10 && Math.abs(z - AirportLine.SOUTH_Z) <= 50;
        boolean airport = Math.abs(z - AirportLine.AIRPORT_Z) <= 10 && Math.abs(x - AirportLine.AIRPORT_X) <= 50;
        boolean station = interchange || airport;
        List<CityTransit.Over> out = new ArrayList<>();
        boolean interchangeWalk = x >= 924 && x <= 1047 && z >= 374 && z <= 376
                || x >= 924 && x <= 926 && z >= 374 && z <= 392;
        if (interchangeWalk) {
            out.add(new CityTransit.Over(-1, "academy:plaza_tile"));
            column(out, 0, 3, "minecraft:air");
            // The existing eastbound stair has its upper half at x=925. Approach it
            // from the north via a half step instead of walking into a full-height face.
            if (x == 925 && z == 391) out.add(new CityTransit.Over(0, "minecraft:quartz_slab[type=bottom]"));
        }
        if (distance <= 4 || station) {
            out.add(new CityTransit.Over(23, "academy:reinforced_concrete"));
            String floor = station && distance >= 3 ? "academy:plaza_tile" : "academy:reinforced_concrete";
            if (distance <= 1) floor = "academy:brushed_steel";
            if (station && distance >= 2.5 && distance <= 3.5) floor = "minecraft:yellow_concrete";
            out.add(new CityTransit.Over(24, floor));
        }
        if (station) {
            int alongInt = interchange ? (int) (AirportLine.SOUTH_Z - z) : (int) (x - AirportLine.AIRPORT_X);
            int cross = interchange ? x - (int) AirportLine.SOUTH_X : z - (int) AirportLine.AIRPORT_Z;
            boolean stairOpening = alongInt == -50 && Math.abs(cross) >= 6 && Math.abs(cross) <= 8;
            if ((Math.abs(cross) == 10 || Math.abs(alongInt) == 50) && !stairOpening)
                out.add(new CityTransit.Over(25, "minecraft:iron_bars"));
            if (Math.abs(cross) <= 10) {
                out.add(new CityTransit.Over(34, "academy:white_panel_slab[type=bottom]"));
                if (Math.abs(cross) == 10 && Math.floorMod(alongInt, 20) == 0)
                    column(out, 25, 33, "academy:pillar_white");
                if (Math.abs(cross) <= 9 && Math.floorMod(cross, 3) == 0 && Math.floorMod(alongInt, 5) == 0)
                    out.add(new CityTransit.Over(33, "academy:ceiling_light"));
            }
            boolean opening = false;
            for (int car = 0; car < 3; car++)
                if (Math.abs(alongInt - Math.round((car - 1) * TrainLine.CAR_PITCH)) <= 2) opening = true;
            if (!opening && Math.abs(cross) == 2) out.add(new CityTransit.Over(25, "minecraft:iron_bars"));
            if (Math.abs(cross) == 9 && Math.floorMod(alongInt, 20) == 0) {
                var kind = CityPlan.sample(seed, x, z).kind();
                if (kind != CityPlan.Kind.ROAD && kind != CityPlan.Kind.MARKING
                        && CityDistrictLandmarks.at(seed, x, z) == null)
                    column(out, 0, 22, "academy:pillar_dark");
            }
            for (int car = 0; car < 3; car++) {
                int bay = (int) Math.round((car - 1) * TrainLine.CAR_PITCH);
                // Keep the three-wide stair approach (cross 6..8) free at every bay.
                if (alongInt == bay && Math.abs(cross) == 9) out.add(new CityTransit.Over(25, "academy:led_screen"));
                if (Math.abs(alongInt - bay) <= 2 && Math.abs(cross) == 3) {
                    String facing = interchange ? (cross < 0 ? "west" : "east") : (cross < 0 ? "north" : "south");
                    String hinge = Math.floorMod(alongInt, 2) == 0 ? "left" : "right";
                    String p = "[facing=" + facing + ",hinge=" + hinge + ",open=false,powered=false,half=";
                    out.add(new CityTransit.Over(25, "academy:platform_gate" + p + "lower]"));
                    out.add(new CityTransit.Over(26, "academy:platform_gate" + p + "upper]"));
                }
            }
        }
        boolean northSouthStairs = z >= 351 && z <= 375 && Math.abs(x - AirportLine.SOUTH_X) >= 6
                && Math.abs(x - AirportLine.SOUTH_X) <= 8;
        boolean airportStairs = x >= 1485 && x <= 1509 && Math.abs(z - AirportLine.AIRPORT_Z) >= 6
                && Math.abs(z - AirportLine.AIRPORT_Z) <= 8;
        if (northSouthStairs || airportStairs) {
            int step = northSouthStairs ? 375 - z : x - 1485;
            if (step > 0) out.add(new CityTransit.Over(step - 1, "academy:brushed_steel"));
            out.add(new CityTransit.Over(step, "minecraft:quartz_stairs[facing="
                    + (northSouthStairs ? "north" : "east") + ",half=bottom]"));
        }
        // A support only on open ground, never in a roadway or the airport entrance.
        if (!station && distance < 1 && Math.floorMod((int) Math.round(along), 24) == 0) {
            var kind = CityPlan.sample(seed, x, z).kind();
            if (kind != CityPlan.Kind.ROAD && kind != CityPlan.Kind.MARKING && kind != CityPlan.Kind.RUNWAY
                    && CityDistrictLandmarks.at(seed, x, z) == null)
                column(out, 0, 22, "academy:pillar_dark");
        }
        return out.isEmpty() ? null : out.toArray(CityTransit.Over[]::new);
    }

    private static void column(List<CityTransit.Over> out, int low, int high, String block) {
        for (int y = low; y <= high; y++) out.add(new CityTransit.Over(y, block));
    }
}
