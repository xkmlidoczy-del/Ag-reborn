package cn.academy.port.city;

import cn.academy.port.train.TrainLine;
import java.util.ArrayList;
import java.util.List;

/** Station approaches, platforms and bridges are generated per column. */
public final class CityTransit {
    public static final int Z = (int) TrainLine.Z;
    public static final int FIRST_X = -1900, LAST_X = 1900;
    public static final int PLATFORM_HALF_LENGTH = 50;
    public static final int PLATFORM_HALF_WIDTH = 9;
    private static final int CANOPY_Y = 34;

    public record Over(int y, String block) {}

    private CityTransit() {}

    public static int stationAt(int x) {
        return TrainLine.stationNear(x, 88);
    }

    public static int bayX(int station, int car) {
        return TrainLine.STATIONS.get(station).x() + (int) Math.round((car - 1) * TrainLine.CAR_PITCH);
    }

    public static int boardCar(int station, int x, int y, int z) {
        if (station < 0 || y != TrainLine.RAIL_Y || Math.abs(z - Z) != 7) return -1;
        for (int car = 0; car < 3; car++) if (x == bayX(station, car)) return car;
        return -1;
    }

    /** The three five-block-wide platform gates, one for each car and each platform side. */
    public static boolean isGate(int station, int x, int y, int z) {
        return station >= 0 && station < TrainLine.STATIONS.size() && (y == 25 || y == 26)
                && Math.abs(z - Z) == 3 && nearBay(station, x);
    }

    public static boolean groundConcourse(int x, int z) {
        int dz = Math.abs(z - Z);
        if (dz < 12 || dz > 27) return false;
        int station = stationAt(x);
        if (station < 0) return false;
        int dx = x - TrainLine.STATIONS.get(station).x();
        return dx >= -86 && dx <= -65;
    }

    public static Over[] overlay(int x, int z) {
        int dz = z - Z, side = Math.abs(dz);
        if (x < FIRST_X || x > LAST_X || side > 27) return null;
        int station = stationAt(x);
        int center = station < 0 ? 0 : TrainLine.STATIONS.get(station).x();
        int dx = x - center;
        List<Over> out = new ArrayList<>();
        boolean platform = station >= 0 && Math.abs(dx) <= PLATFORM_HALF_LENGTH && side <= PLATFORM_HALF_WIDTH;
        boolean deck = side <= 4 || platform;
        if (deck) {
            out.add(new Over(23, "academy:reinforced_concrete"));
            String floor = platform && side >= 3 ? "academy:plaza_tile" : "academy:reinforced_concrete";
            if (dz == 0 || dz == -1) floor = "academy:brushed_steel";
            if (platform && side == 3) floor = "minecraft:yellow_concrete";
            out.add(new Over(24, floor));
            if ((!platform && side == 4) || (platform && side == PLATFORM_HALF_WIDTH)) {
                out.add(new Over(25, "academy:white_panel_slab[type=bottom]"));
            }
        }
        boolean pier = (Math.floorMod(x, 24) < 2 && side <= 1)
                || (platform && Math.floorMod(dx, 12) == 0 && (side == 5 || side == 9));
        if (pier) column(out, -1, 22, "academy:pillar_dark");
        if (station >= 0) {
            if (Math.abs(dx) <= PLATFORM_HALF_LENGTH && side <= 10) {
                out.add(new Over(CANOPY_Y, "academy:white_panel_slab[type=bottom]"));
                if (side == 10 && Math.floorMod(dx, 20) == 0) column(out, 25, 33, "academy:pillar_white");
                if (side <= 9 && side % 3 == 0 && Math.floorMod(dx, 5) == 0) {
                    out.add(new Over(CANOPY_Y - 1, "academy:ceiling_light"));
                }
            }
            if (dx >= -75 && dx <= -51 && side >= 6 && side <= 8) {
                int step = dx + 75;
                if (station == 2) {
                    if (step > 0) out.add(new Over(step - 1, "academy:brushed_steel"));
                    if (side == 8 && step >= 6 && step % 6 == 0) {
                        column(out, 0, step - 2, "academy:pillar_white");
                    }
                } else {
                    column(out, -1, step - 1, "academy:reinforced_concrete");
                }
                out.add(new Over(step, "minecraft:quartz_stairs[facing=east,half=bottom]"));
            }
            if (station == 2 && dx >= -75 && dx <= -51 && side == 9) {
                out.add(new Over(dx + 76, "minecraft:iron_bars[east=true,west=true]"));
            }
            if (dx >= -80 && dx <= -76 && side >= 6 && side <= 23) {
                out.add(new Over(-1, "academy:plaza_tile"));
            }
            if (groundConcourse(x, z)) {
                out.add(new Over(-1, dx == -86 || dx == -65 ? "minecraft:cyan_concrete" : "academy:plaza_tile"));
                if ((dx == -86 || dx == -65) && side == 21) {
                    column(out, 0, 3, "academy:pillar_white");
                    out.add(new Over(4, "academy:neon_cyan"));
                }
            }
            if (platform && boardCar(station, x, 25, z) >= 0) out.add(new Over(25, "academy:led_screen"));
            if (platform && side == 2 && !nearBay(station, x)) out.add(new Over(25, "minecraft:iron_bars"));
            if (platform && side == 3 && nearBay(station, x)) {
                    String facing = dz < 0 ? "north" : "south";
                    String hinge = Math.floorMod(x, 2) == 0 ? "left" : "right";
                    String properties = "[facing=" + facing + ",hinge=" + hinge + ",open=false,powered=false,half=";
                    out.add(new Over(25, "academy:platform_gate" + properties + "lower]"));
                    out.add(new Over(26, "academy:platform_gate" + properties + "upper]"));
            }
            bridge(out, dx, dz);
        }
        return out.isEmpty() ? null : out.toArray(Over[]::new);
    }

    private static boolean nearBay(int station, int x) {
        for (int car = 0; car < 3; car++) if (Math.abs(x - bayX(station, car)) <= 2) return true;
        return false;
    }

    private static void bridge(List<Over> out, int dx, int dz) {
        int side = Math.abs(dz);
        if (dx >= -50 && dx <= -45 && side >= 6 && side <= 8) {
            int step = 25 + dx + 50;
            column(out, 25, step - 1, "academy:reinforced_concrete");
            out.add(new Over(step, "minecraft:quartz_stairs[facing=east,half=bottom]"));
            column(out, step + 1, 33, "minecraft:air");
        } else if (dx >= -44 && dx <= -40 && side <= 8) {
            out.add(new Over(30, "academy:composite_floor"));
            String edge = (dx == -44 || dx == -40) && side <= 5
                    ? "academy:steel_slab[type=top]" : "minecraft:air";
            out.add(new Over(31, edge));
            column(out, 32, 33, "minecraft:air");
        }
    }

    private static void column(List<Over> out, int low, int high, String block) {
        for (int y = low; y <= high; y++) out.add(new Over(y, block));
    }
}
