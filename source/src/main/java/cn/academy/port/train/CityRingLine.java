package cn.academy.port.train;

import java.util.ArrayList;
import java.util.List;

/** Rounded-square city railway, sharing its geometry with the guideway. */
public final class CityRingLine {
    public static final int HALF = 900, RADIUS = 100, STRAIGHT = 1600, RAIL_Y = 65;
    public static final double ARC = Math.PI * RADIUS / 2, EDGE = STRAIGHT + ARC, LENGTH = 4 * EDGE;
    public static final double VMAX = 24, ACC = TrainLine.ACC, DWELL = TrainLine.DWELL;
    /** Four trains occupy disjoint groups of three legs and reverse together. */
    public static final int SERVICES = 4, SERVICE_STATION_GAP = 3;
    public record Point(double x, double z, double dx, double dz) {
        public double yaw() { return Math.toDegrees(Math.atan2(-dx, dz)); }
    }
    public record Station(int x, int z, boolean horizontal, String id, double distance) {
        public String translationKey() { return "ac.train.station.ring_" + id; }
    }
    public static final List<Station> STATIONS = List.of(
            new Station(-500, -900, true, "northwest", 300),
            new Station(0, -900, true, "north", 800),
            new Station(500, -900, true, "northeast", 1300),
            new Station(900, -500, false, "east_north", EDGE + 300),
            new Station(900, 0, false, "east", EDGE + 800),
            new Station(900, 500, false, "east_south", EDGE + 1300),
            new Station(500, 900, true, "south_east", 2 * EDGE + 300),
            new Station(-100, 900, true, "south", 2 * EDGE + 900),
            new Station(-500, 900, true, "south_west", 2 * EDGE + 1300),
            new Station(-900, 500, false, "west_south", 3 * EDGE + 300),
            new Station(-900, 0, false, "west", 3 * EDGE + 800),
            new Station(-900, -500, false, "west_north", 3 * EDGE + 1300));
    private record Slot(double start, double end, int from, int to, int direction, double distance) {
        boolean dwelling() { return from == to; }
    }
    public record State(double distance, double speed, int direction, int station, int next, double secondsLeft, TrainLine.Phase phase) {
        public boolean boarding() { return phase == TrainLine.Phase.BOARDING; }
        public Point point() { return CityRingLine.point(distance); }
    }
    private static final List<Slot> SLOTS;
    public static final double CYCLE;
    static {
        var slots = new ArrayList<Slot>(); double time = 0;
        // The four trains stop and reverse simultaneously. Each occupies a different
        // three-station sector, so opposite-direction traffic never shares the line.
        for (int direction : new int[]{1, -1}) for (int i = 0; i < STATIONS.size(); i++) {
            int from = Math.floorMod(direction * i, STATIONS.size());
            int next = Math.floorMod(from + direction, STATIONS.size());
            slots.add(new Slot(time, time + DWELL, from, from, direction, 0)); time += DWELL;
            double distance = direction * (STATIONS.get(next).distance() - STATIONS.get(from).distance());
            if (distance <= 0) distance += LENGTH;
            double duration = travelTime(distance);
            for (int service = 1; service < SERVICES; service++) {
                int pairedFrom = Math.floorMod(from + service * SERVICE_STATION_GAP, STATIONS.size());
                int pairedNext = Math.floorMod(pairedFrom + direction, STATIONS.size());
                duration = Math.max(duration, travelTime(legDistance(pairedFrom, pairedNext, direction)));
            }
            slots.add(new Slot(time, time + duration, from, next, direction, distance)); time += duration;
        }
        SLOTS = List.copyOf(slots); CYCLE = time;
    }
    private CityRingLine() {}
    private static double legDistance(int from, int to, int direction) {
        double distance = direction * (STATIONS.get(to).distance() - STATIONS.get(from).distance());
        return distance <= 0 ? distance + LENGTH : distance;
    }
    private static double travelTime(double distance) {
        double peak = Math.min(VMAX, Math.sqrt(ACC * distance));
        return distance / peak + peak / ACC;
    }
    public static Point point(double distance) {
        if (!Double.isFinite(distance)) throw new IllegalArgumentException("non-finite ring distance");
        double s = distance % LENGTH; if (s < 0) s += LENGTH;
        int edge = Math.min(3, (int) (s / EDGE)); double d = s - edge * EDGE;
        if (d <= STRAIGHT) return switch (edge) {
            case 0 -> new Point(-800 + d, -900, 1, 0);
            case 1 -> new Point(900, -800 + d, 0, 1);
            case 2 -> new Point(800 - d, 900, -1, 0);
            default -> new Point(-900, 800 - d, 0, -1);
        };
        double theta = -Math.PI / 2 + edge * Math.PI / 2 + (d - STRAIGHT) / RADIUS;
        int cx = edge == 0 || edge == 1 ? 800 : -800;
        int cz = edge == 0 || edge == 3 ? -800 : 800;
        return new Point(cx + RADIUS * Math.cos(theta), cz + RADIUS * Math.sin(theta),
                -Math.sin(theta), Math.cos(theta));
    }
    public static State at(double seconds) {
        return at(seconds, 0);
    }
    public static State at(double seconds, int service) {
        if (service < 0 || service >= SERVICES) throw new IllegalArgumentException("unknown ring service " + service);
        if (!Double.isFinite(seconds)) seconds = 0;
        double t = seconds % CYCLE; if (t < 0) t += CYCLE;
        for (var slot : SLOTS) {
            if (t >= slot.end()) continue;
            double elapsed = t - slot.start(), remaining = slot.end() - t;
            int from = Math.floorMod(slot.from() + service * SERVICE_STATION_GAP, STATIONS.size());
            int to = Math.floorMod(from + slot.direction(), STATIONS.size());
            double origin = STATIONS.get(from).distance();
            if (slot.dwelling()) {
                var phase = elapsed < TrainLine.OPEN_DELAY ? TrainLine.Phase.ARRIVED
                        : remaining <= TrainLine.CLOSE_WARNING ? TrainLine.Phase.CLOSING : TrainLine.Phase.BOARDING;
                return new State(origin, 0, slot.direction(), from, to, remaining, phase);
            }
            double legDistance = legDistance(from, to, slot.direction());
            double scale = travelTime(legDistance) / (slot.end() - slot.start());
            double naturalElapsed = elapsed * scale, naturalRemaining = remaining * scale;
            double peak = Math.min(VMAX, Math.sqrt(ACC * legDistance)), ramp = peak / ACC;
            double distance, speed;
            if (naturalElapsed < ramp) { speed = ACC * naturalElapsed; distance = .5 * speed * naturalElapsed; }
            else if (naturalRemaining < ramp) { speed = ACC * naturalRemaining; distance = legDistance - .5 * speed * naturalRemaining; }
            else { speed = peak; distance = peak * (naturalElapsed - ramp / 2); }
            speed *= scale;
            var phase = remaining <= 5 ? TrainLine.Phase.APPROACHING
                    : elapsed < 2 ? TrainLine.Phase.DEPARTING : TrainLine.Phase.CRUISE;
            double position = (origin + slot.direction() * distance) % LENGTH;
            if (position < 0) position += LENGTH;
            return new State(position, speed, slot.direction(), -1, to, remaining, phase);
        }
        throw new IllegalStateException("invalid ring clock");
    }
    public static double dwellStart(int station) {
        return dwellStart(station, true);
    }
    public static double dwellStart(int station, boolean clockwise) {
        for (var slot : SLOTS) if (slot.dwelling() && slot.from() == station
                && (slot.direction() > 0) == clockwise) return slot.start();
        throw new IllegalArgumentException("unknown ring station " + station);
    }
    public static double arrivalIn(int station, double seconds) {
        for (int service = 0; service < SERVICES; service++) if (at(seconds, service).station() == station) return 0;
        if (!Double.isFinite(seconds)) seconds = 0;
        double t = seconds % CYCLE; if (t < 0) t += CYCLE;
        double best = CYCLE;
        for (var slot : SLOTS) if (slot.dwelling()) for (int service=0; service<SERVICES; service++)
            if (Math.floorMod(slot.from()+service*SERVICE_STATION_GAP, STATIONS.size()) == station) {
                double delta = slot.start() - t;
                best = Math.min(best, delta < 0 ? delta + CYCLE : delta);
            }
        return best;
    }
}
