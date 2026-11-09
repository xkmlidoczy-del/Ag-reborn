package cn.academy.port.train;

/**
 * Airport shuttle alignment in the story city. Distance increases from the
 * East City interchange towards the terminal. The alignment stays west of the
 * runway until it has reached the airport approach, then turns east north of it.
 * This is shared geometry for track, train poses and station access, not a service.
 */
public final class AirportLine {
    public static final double SOUTH_X = 1040, SOUTH_Z = 300;
    public static final double CURVE_X = 1120, CURVE_Z = -1710, RADIUS = 80;
    public static final double AIRPORT_X = 1560, AIRPORT_Z = -1790;
    public static final double STRAIGHT_NORTH = SOUTH_Z - CURVE_Z;
    public static final double CURVE_LENGTH = Math.PI * RADIUS / 2;
    public static final double LENGTH = STRAIGHT_NORTH + CURVE_LENGTH + AIRPORT_X - CURVE_X;
    public static final double RAIL_Y = TrainLine.RAIL_Y;
    // A lower line speed keeps the 80-block-radius curve comfortable and visible.
    public static final double VMAX = 24, ACC = TrainLine.ACC, DWELL = TrainLine.DWELL;
    public static final double LEG_TIME = LENGTH / VMAX + VMAX / ACC;
    public static final double CYCLE = 2 * (DWELL + LEG_TIME);

    /** Unit tangent points from East City towards the airport. */
    public record Point(double x, double z, double dx, double dz) {
        public double yaw() { return Math.toDegrees(Math.atan2(-dx, dz)); }
    }

    public record State(double distance, int direction, double speed, int station,
                        int next, double secondsLeft, TrainLine.Phase phase) {
        public boolean boarding() { return phase == TrainLine.Phase.BOARDING; }
        public Point point() { return AirportLine.point(distance); }
    }

    private AirportLine() {}

    /** Extrapolate past station centres so a complete three-car train fits at both platforms. */
    public static Point point(double distance) {
        if (!Double.isFinite(distance)) throw new IllegalArgumentException("distance must be finite");
        if (distance <= STRAIGHT_NORTH) return new Point(SOUTH_X, SOUTH_Z - distance, 0, -1);
        if (distance < STRAIGHT_NORTH + CURVE_LENGTH) {
            double theta = Math.PI + (distance - STRAIGHT_NORTH) / RADIUS;
            return new Point(CURVE_X + RADIUS * Math.cos(theta), CURVE_Z + RADIUS * Math.sin(theta),
                    -Math.sin(theta), Math.cos(theta));
        }
        return new Point(CURVE_X + distance - STRAIGHT_NORTH - CURVE_LENGTH, AIRPORT_Z, 1, 0);
    }

    /** Symmetric shuttle: dwell, depart, accelerate, cruise, brake and dwell at the far end. */
    public static State at(double seconds) {
        if (!Double.isFinite(seconds)) seconds = 0;
        double t = seconds % CYCLE;
        if (t < 0) t += CYCLE;
        if (t >= CYCLE) t = 0; // roundoff for tiny negative times
        int direction = t < DWELL + LEG_TIME ? 1 : -1;
        double leg = direction > 0 ? t : t - DWELL - LEG_TIME;
        int from = direction > 0 ? 0 : 1;
        int to = 1 - from;
        if (leg < DWELL) {
            double remaining = DWELL - leg;
            TrainLine.Phase phase = leg < TrainLine.OPEN_DELAY ? TrainLine.Phase.ARRIVED
                    : remaining <= TrainLine.CLOSE_WARNING ? TrainLine.Phase.CLOSING : TrainLine.Phase.BOARDING;
            return new State(from == 0 ? 0 : LENGTH, direction, 0, from, to, remaining, phase);
        }
        double elapsed = leg - DWELL, remaining = LEG_TIME - elapsed;
        double ramp = VMAX / ACC;
        double travelled, speed;
        if (elapsed < ramp) {
            speed = ACC * elapsed;
            travelled = 0.5 * speed * elapsed;
        } else if (remaining < ramp) {
            speed = ACC * remaining;
            travelled = LENGTH - 0.5 * speed * remaining;
        } else {
            speed = VMAX;
            travelled = VMAX * (elapsed - ramp / 2);
        }
        TrainLine.Phase phase = remaining <= 5 ? TrainLine.Phase.APPROACHING
                : elapsed < 2 ? TrainLine.Phase.DEPARTING : TrainLine.Phase.CRUISE;
        return new State(direction > 0 ? travelled : LENGTH - travelled, direction, speed, -1, to, remaining, phase);
    }

    public static double dwellStart(int station) {
        return switch (station) {
            case 0 -> 0;
            case 1 -> DWELL + LEG_TIME;
            default -> throw new IllegalArgumentException("unknown airport station " + station);
        };
    }

    public static double arrivalIn(int station, double seconds) {
        double start = dwellStart(station);
        if (at(seconds).station() == station) return 0;
        if (!Double.isFinite(seconds)) seconds = 0;
        double t = seconds % CYCLE;
        if (t < 0) t += CYCLE;
        double remaining = start - t;
        return remaining < 0 ? remaining + CYCLE : remaining;
    }
}
