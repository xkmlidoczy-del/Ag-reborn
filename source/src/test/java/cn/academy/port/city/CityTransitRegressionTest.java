package cn.academy.port.city;

import cn.academy.port.train.TrainLine;
import java.util.Arrays;

public final class CityTransitRegressionTest {
    public static void main(String[] args) {
        require(TrainLine.at(-Double.MIN_VALUE).equals(TrainLine.at(0)), "near-zero negative cycle wrap");
        for (int station = 0; station < TrainLine.STATIONS.size(); station++) {
            verifyStation(station, true);
            verifyStation(station, false);
        }
        for (int tick = 0; tick < TrainLine.CYCLE * 20; tick++) {
            double time = tick / 20.0;
            var state = TrainLine.at(time);
            require(state.x() >= -1800 && state.x() <= 1800, "train outside line");
            require(state.speed() >= 0 && state.speed() <= TrainLine.VMAX, "speed outside design");
            require(state.next() >= 0 && state.next() < 5, "next station invalid");
            require(state.station() >= 0 || !state.boarding(), "boarding underway");
            require(Math.abs(TrainLine.at(time - TrainLine.CYCLE).x() - state.x()) < 1e-8, "wrapped timetable");
        }
        System.out.println("PASS all 5 station approaches and gates, 8 direction-specific stops and complete timetable cycle");
    }

    private static void verifyStation(int station, boolean eastbound) {
        var stop = TrainLine.STATIONS.get(station);
        double arrive = TrainLine.dwellStart(station, eastbound);
        require(TrainLine.at(arrive).phase() == TrainLine.Phase.ARRIVED, "arrive cue");
        require(TrainLine.at(arrive + 2).boarding(), "boarding after settling");
        require(!TrainLine.at(arrive + TrainLine.DWELL - 2).boarding(), "closing cutoff");
        require(TrainLine.at(arrive + TrainLine.DWELL + 0.1).station() == -1, "departure changes mode");
        require(Math.abs(TrainLine.arrivalIn(station, arrive - 3) - 3) < 0.01, "ETA before approach");
        require(TrainLine.arrivalIn(station, arrive + 5) == 0, "ETA at platform");
        require(CityPlan.district(stop.x(), 399) == stop.northDistrict()
                && CityPlan.district(stop.x(), 400) == stop.southDistrict(), "station must link advertised districts");
        for (int side : new int[]{-1, 1}) {
            int z = 400 + side * 7;
            require(has(stop.x() - 76, z, -1, "academy:plaza_tile"), "low stair entrance");
            require(has(stop.x() - 75, z, 0, "minecraft:quartz_stairs[facing=east,half=bottom]"), "first low stair");
            require(has(stop.x() - 51, z, 24, "minecraft:quartz_stairs[facing=east,half=bottom]"), "last stair to platform");
            for (int role = 0; role < 3; role++) {
                int x = CityTransit.bayX(station, role);
                require(CityTransit.boardCar(station, x, 25, z) == role
                        && has(x, z, 25, "academy:led_screen"), "board marker matches car and side");
                require(has(x, 400 + side * 5, 24, "academy:plaza_tile"), "safe platform below dismount point");
                for (int dx = -2; dx <= 2; dx++) for (int y = 25; y <= 26; y++) {
                    int gateZ = 400 + side * 3;
                    require(CityTransit.isGate(station, x + dx, y, gateZ)
                                    && hasPrefix(x + dx, gateZ, y, "academy:platform_gate["),
                            "station gate and service geometry disagree");
                }
            }
        }
        for (int z = 393; z <= 407; z++) {
            require(has(stop.x() - 42, z, 30, "academy:composite_floor")
                    && has(stop.x() - 42, z, 32, "minecraft:air"), "overpass must keep the headroom");
        }
    }

    private static boolean has(int x, int z, int y, String block) {
        var column = CityTransit.overlay(x, z);
        return column != null && Arrays.stream(column).anyMatch(over -> over.y() == y && over.block().equals(block));
    }

    private static boolean hasPrefix(int x, int z, int y, String prefix) {
        var column = CityTransit.overlay(x, z);
        return column != null && Arrays.stream(column).anyMatch(over -> over.y() == y && over.block().startsWith(prefix));
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
}
