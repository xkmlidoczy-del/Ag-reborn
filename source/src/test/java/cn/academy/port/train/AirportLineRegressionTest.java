package cn.academy.port.train;

/** Continuity, clearance and timetable checks for the airport shuttle alignment. */
public final class AirportLineRegressionTest {
    public static void main(String[] args) {
        require(close(AirportLine.point(0).x(), 1040) && close(AirportLine.point(0).z(), 300), "interchange");
        require(close(AirportLine.point(AirportLine.LENGTH).x(), 1560)
                && close(AirportLine.point(AirportLine.LENGTH).z(), -1790), "airport");
        for (double join : new double[]{AirportLine.STRAIGHT_NORTH, AirportLine.STRAIGHT_NORTH + AirportLine.CURVE_LENGTH}) {
            var before = AirportLine.point(join - 1e-5);
            var after = AirportLine.point(join + 1e-5);
            require(Math.hypot(before.x() - after.x(), before.z() - after.z()) < 0.00003, "position at bend");
            require(Math.hypot(before.dx() - after.dx(), before.dz() - after.dz()) < 0.00001, "tangent at bend");
        }
        for (double d = -40; d <= AirportLine.LENGTH + 40; d += .5) {
            var p = AirportLine.point(d);
            require(close(Math.hypot(p.dx(), p.dz()), 1), "unit tangent");
            // Keep even a full seven-block-wide viaduct north/west of the runway footprint.
            require(!(p.x() + 4 >= 1252 && p.x() - 4 < 1948 && p.z() + 4 >= -1688 && p.z() - 4 < -1628), "runway obstruction");
        }
        for (int station = 0; station <= 1; station++) {
            double t = AirportLine.dwellStart(station);
            require(AirportLine.at(t).phase() == TrainLine.Phase.ARRIVED, "arrival");
            require(AirportLine.at(t + 2).station() == station && AirportLine.at(t + 2).boarding(), "boarding");
            require(AirportLine.at(t + AirportLine.DWELL - 2).phase() == TrainLine.Phase.CLOSING, "closing");
            require(AirportLine.at(t + AirportLine.DWELL + .1).station() == -1, "departure");
            require(Math.abs(AirportLine.arrivalIn(station, t - 3) - 3) < 1e-7, "arrival estimate");
        }
        for (double t = 0; t < AirportLine.CYCLE; t += .05) {
            var s = AirportLine.at(t);
            require(s.distance() >= -1e-8 && s.distance() <= AirportLine.LENGTH + 1e-8, "train out of bounds");
            require(s.speed() >= -1e-8 && s.speed() <= AirportLine.VMAX + 1e-8, "speed out of bounds");
            require(!s.boarding() || s.station() >= 0, "boarding between stations");
            require(Math.abs(AirportLine.at(t - AirportLine.CYCLE).distance() - s.distance()) < 1e-7, "cycle wrapping");
        }
        System.out.println("PASS airport alignment, continuous bend, runway clearance and two-way shuttle timetable");
    }

    private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-8; }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
