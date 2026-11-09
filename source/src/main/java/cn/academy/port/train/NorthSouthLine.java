package cn.academy.port.train;

import java.util.ArrayList;
import java.util.List;

/** Four-stop elevated service, with distance and speed in blocks and seconds. */
public final class NorthSouthLine {
    public record Station(int z, String id) {
        public String translationKey() { return "ac.train.station." + id; }
    }
    public static final List<Station> STATIONS = List.of(
            new Station(-800, "exhibition"), new Station(0, "campus"),
            new Station(400, "central_transfer"), new Station(1000, "south_commercial"));
    public static final int X = 150, RAIL_Y = 45;
    public static final double VMAX = 32, ACC = TrainLine.ACC, DWELL = TrainLine.DWELL;
    private record Slot(double start, double end, int from, int to, int direction) {
        boolean dwelling() { return from == to; }
    }
    public record State(double z, int dir, double speed, int station, int next,
                        double secondsLeft, TrainLine.Phase phase) {
        public boolean boarding() { return phase == TrainLine.Phase.BOARDING; }
        public int terminus() { return dir > 0 ? STATIONS.size() - 1 : 0; }
    }
    private static final List<Slot> SLOTS;
    public static final double CYCLE;
    static {
        var slots = new ArrayList<Slot>();
        int[] order = {0, 1, 2, 3, 2, 1, 0};
        double time = 0;
        for (int i = 0; i < order.length - 1; i++) {
            int from = order[i], to = order[i + 1], dir = Integer.signum(to - from);
            slots.add(new Slot(time, time + DWELL, from, from, dir));
            time += DWELL;
            double length = Math.abs(STATIONS.get(to).z() - STATIONS.get(from).z());
            double peak = Math.min(VMAX, Math.sqrt(ACC * length));
            double duration = length / peak + peak / ACC;
            slots.add(new Slot(time, time + duration, from, to, dir));
            time += duration;
        }
        SLOTS = List.copyOf(slots); CYCLE = time;
    }
    private NorthSouthLine() {}
    private static double cycleTime(double seconds) {
        if (!Double.isFinite(seconds)) return 0;
        double t = seconds % CYCLE;
        if (t < 0) t += CYCLE;
        return t >= CYCLE ? 0 : t;
    }
    public static State at(double seconds) {
        double t = cycleTime(seconds);
        for (var slot : SLOTS) {
            if (t >= slot.end()) continue;
            double elapsed = t - slot.start(), remaining = slot.end() - t;
            int z = STATIONS.get(slot.from()).z();
            if (slot.dwelling()) {
                var phase = elapsed < TrainLine.OPEN_DELAY ? TrainLine.Phase.ARRIVED
                        : remaining <= TrainLine.CLOSE_WARNING ? TrainLine.Phase.CLOSING : TrainLine.Phase.BOARDING;
                return new State(z, slot.direction(), 0, slot.from(), slot.from() + slot.direction(), remaining, phase);
            }
            double length = Math.abs(STATIONS.get(slot.to()).z() - z);
            double peak = Math.min(VMAX, Math.sqrt(ACC * length)), ramp = peak / ACC;
            double distance, speed;
            if (elapsed < ramp) { speed = ACC * elapsed; distance = .5 * speed * elapsed; }
            else if (remaining < ramp) { speed = ACC * remaining; distance = length - .5 * speed * remaining; }
            else { speed = peak; distance = peak * (elapsed - ramp / 2); }
            var phase = remaining <= 5 ? TrainLine.Phase.APPROACHING
                    : elapsed < 2 ? TrainLine.Phase.DEPARTING : TrainLine.Phase.CRUISE;
            return new State(z + slot.direction() * distance, slot.direction(), speed, -1, slot.to(), remaining, phase);
        }
        throw new IllegalStateException("invalid north-south cycle");
    }
    public static int stationNear(double z, double radius) {
        for (int i = 0; i < STATIONS.size(); i++) if (Math.abs(z - STATIONS.get(i).z()) <= radius) return i;
        return -1;
    }
    public static double dwellStart(int station, boolean southbound) {
        for (var slot : SLOTS) if (slot.dwelling() && slot.from() == station
                && (station == 0 || station == STATIONS.size() - 1 || (slot.direction() > 0) == southbound)) return slot.start();
        throw new IllegalArgumentException("unknown north-south station " + station);
    }
    public static double arrivalIn(int station, double seconds) {
        if (at(seconds).station() == station) return 0;
        double now = cycleTime(seconds), best = CYCLE;
        for (var slot : SLOTS) if (slot.dwelling() && slot.from() == station) {
            double delta = slot.start() - now;
            best = Math.min(best, delta < 0 ? delta + CYCLE : delta);
        }
        return best;
    }
}
