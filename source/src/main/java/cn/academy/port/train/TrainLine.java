package cn.academy.port.train;

import java.util.ArrayList;
import java.util.List;

/** Deterministic timetable for the five-stop city express. */
public final class TrainLine {
    public record Station(int x, String id, int northDistrict, int southDistrict) {
        public String translationKey() { return "ac.train.station." + id; }
    }

    public static final List<Station> STATIONS = List.of(
            new Station(-1800, "west_end", 20, 8),
            new Station(-1000, "west_city", 1, 5),
            new Station(0, "central", 7, 15),
            new Station(1000, "east_city", 6, 9),
            new Station(1800, "east_end", 16, 10));
    public static final double Z = 400;
    public static final int RAIL_Y = 25;
    public static final double VMAX = 50; // blocks per second
    public static final double ACC = 2.5; // blocks per second squared
    public static final double DWELL = 25;
    public static final double CAR_PITCH = 25.4;
    public static final double OPEN_DELAY = 1;
    public static final double CLOSE_WARNING = 3;
    private static final double APPROACH_WINDOW = 5;

    public enum Phase {
        CRUISE, DEPARTING, APPROACHING, ARRIVED, BOARDING, CLOSING
    }

    private record Slot(double start, double end, int from, int to, int direction) {
        boolean dwelling() { return from == to; }
    }

    public record State(double x, int dir, double speed, int station, int next, double secondsLeft, Phase phase) {
        public boolean boarding() { return phase == Phase.BOARDING; }
        public int terminus() { return dir > 0 ? STATIONS.size() - 1 : 0; }
    }

    private static final List<Slot> SLOTS;
    public static final double CYCLE;

    static {
        List<Slot> slots = new ArrayList<>();
        int[] order = {0, 1, 2, 3, 4, 3, 2, 1, 0};
        double time = 0;
        for (int i = 0; i < order.length - 1; i++) {
            int from = order[i], to = order[i + 1];
            int direction = Integer.signum(to - from);
            slots.add(new Slot(time, time + DWELL, from, from, direction));
            time += DWELL;
            double duration = legTime(Math.abs(STATIONS.get(to).x() - STATIONS.get(from).x()));
            slots.add(new Slot(time, time + duration, from, to, direction));
            time += duration;
        }
        SLOTS = List.copyOf(slots);
        CYCLE = time;
    }

    private TrainLine() {}

    public static int stationNear(double x, double radius) {
        for (int i = 0; i < STATIONS.size(); i++) {
            if (Math.abs(x - STATIONS.get(i).x()) <= radius) return i;
        }
        return -1;
    }

    private static double cycleTime(double seconds) {
        if (!Double.isFinite(seconds)) return 0;
        double t = seconds % CYCLE;
        if (t < 0) t += CYCLE;
        return t == CYCLE ? 0 : t;
    }

    private static double peak(double length) {
        return Math.min(VMAX, Math.sqrt(ACC * length));
    }

    private static double legTime(double length) {
        double speed = peak(length);
        return length / speed + speed / ACC;
    }

    public static State at(double seconds) {
        double t = cycleTime(seconds);
        for (Slot slot : SLOTS) {
            if (t >= slot.end()) continue;
            double elapsed = t - slot.start();
            double remaining = slot.end() - t;
            int x = STATIONS.get(slot.from()).x();
            if (slot.dwelling()) {
                Phase phase = elapsed < OPEN_DELAY ? Phase.ARRIVED
                        : remaining <= CLOSE_WARNING ? Phase.CLOSING : Phase.BOARDING;
                return new State(x, slot.direction(), 0, slot.from(), slot.from() + slot.direction(), remaining, phase);
            }

            double length = Math.abs(STATIONS.get(slot.to()).x() - x);
            double topSpeed = peak(length), ramp = topSpeed / ACC;
            double distance;
            double speed;
            if (elapsed < ramp) {
                speed = ACC * elapsed;
                distance = 0.5 * speed * elapsed;
            } else if (remaining < ramp) {
                speed = ACC * remaining;
                distance = length - 0.5 * speed * remaining;
            } else {
                speed = topSpeed;
                distance = topSpeed * (elapsed - ramp / 2);
            }
            Phase phase = remaining <= APPROACH_WINDOW ? Phase.APPROACHING
                    : elapsed < 2 ? Phase.DEPARTING : Phase.CRUISE;
            return new State(x + slot.direction() * distance, slot.direction(), speed, -1, slot.to(), remaining, phase);
        }
        throw new IllegalStateException("invalid train cycle");
    }

    public static double arrivalIn(int station, double seconds) {
        if (at(seconds).station() == station) return 0;
        double now = cycleTime(seconds), best = CYCLE;
        for (Slot slot : SLOTS) {
            if (slot.dwelling() && slot.from() == station) {
                double delta = slot.start() - now;
                best = Math.min(best, delta < 0 ? delta + CYCLE : delta);
            }
        }
        return best;
    }

    /** End stations have one stop per cycle. Middle stations stop in both directions. */
    public static double dwellStart(int station, boolean eastbound) {
        for (Slot slot : SLOTS) {
            if (slot.dwelling() && slot.from() == station
                    && (station == 0 || station == STATIONS.size() - 1 || (slot.direction() > 0) == eastbound)) {
                return slot.start();
            }
        }
        throw new IllegalArgumentException("unknown train station " + station);
    }
}
