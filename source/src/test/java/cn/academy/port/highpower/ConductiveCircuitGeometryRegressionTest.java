package cn.academy.port.highpower;

import cn.academy.port.client.ConductiveCircuitGeometry;
import cn.academy.port.client.ConductiveCircuitGeometry.Point;
import cn.academy.port.client.ConductiveCircuitGeometry.Ribbon;
import java.util.List;
import java.util.Random;

/** Exercises the exact pure helper called by NEW-121 rendering, including former near-plane flare cases. */
public final class ConductiveCircuitGeometryRegressionTest {
    private static int checks;
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-8; }

    public static void main(String[] args) {
        Point start = new Point(2, 64.05, 3), end = new Point(2, 64.05, 3 + ConductiveCircuitGeometry.MAX_LENGTH);
        check(ConductiveCircuitGeometry.validEndpoints(start, end), "exact configured maximum accepted");
        check(!ConductiveCircuitGeometry.validEndpoints(start, end.plus(new Point(0, 0, .001))), "overlength rejected");
        check(!ConductiveCircuitGeometry.validEndpoints(start, end.plus(new Point(0, .01, 0))), "airborne/sloped endpoint rejected");
        check(!ConductiveCircuitGeometry.validEndpoints(start, new Point(Double.NaN, 64.05, 3)), "nonfinite point rejected");
        check(!ConductiveCircuitGeometry.validEndpoints(new Point(30_000_001, 64, 0), new Point(30_000_001, 64, 0)), "outside world bounds rejected");
        check(ConductiveCircuitGeometry.segments(start, start, false, 0, 1).size() == 2, "A-only authoritative placement is a compact cross");
        check(ConductiveCircuitGeometry.segments(start, start, true, 0, 1).isEmpty(), "zero-length energized state cannot create a fake beam");
        var maxDashes = ConductiveCircuitGeometry.segments(start, end, false, 0, 1);
        check(maxDashes.size() <= Math.ceil(ConductiveCircuitGeometry.MAX_LENGTH / .32) + 5, "configured maximum has a bounded dash and anchor allocation");
        var dashed = ConductiveCircuitGeometry.segments(start, start.plus(new Point(0, 0, 32)), false, 0, 1);
        check(dashed.size() == 104, "32m resting circuit has bounded dashes plus two anchor crosses");
        check(dashed.get(1).start().minus(dashed.get(0).end()).length() > .14, "resting line contains visible gaps");
        check(dashed.getFirst().start().equals(start), "resting circuit starts at authoritative A");
        for (var segment : dashed) check(close(segment.start().y(), 64.05) && close(segment.end().y(), 64.05), "resting circuit never leaves ground plane");

        Point forward = new Point(0, 0, 1);
        check(ConductiveCircuitGeometry.clip(new Point(0, -.2, -1), new Point(0, -.2, .01), forward, .018) == null,
                "camera-plane and fully behind ribbons suppressed");
        checkRibbon(ConductiveCircuitGeometry.clip(new Point(0, -.2, -1), new Point(0, -.2, 3), forward, .018), forward, -.2, .018);
        checkRibbon(ConductiveCircuitGeometry.clip(new Point(0, -.2, 3), new Point(0, -.2, -1), forward, .018), forward, -.2, .018);
        check(ConductiveCircuitGeometry.clip(new Point(0, 0, 1), new Point(1, .1, 1), forward, .018) == null, "sloped ribbon rejected");
        check(ConductiveCircuitGeometry.clip(new Point(0, 0, 1), new Point(1, 0, 1), forward, .5) == null, "screenful requested width rejected");
        check(ConductiveCircuitGeometry.clip(new Point(0, 0, 1), new Point(1, 0, 1), new Point(0, 0, 0), .018) == null, "zero camera direction rejected");

        Random random = new Random(1212026);
        for (int i = 0; i < 4000; i++) {
            double angle = random.nextDouble() * Math.PI * 2, length = .25 + random.nextDouble() * (ConductiveCircuitGeometry.MAX_LENGTH - .25);
            Point a = new Point(random.nextDouble() * 100 - 50, 65.05, random.nextDouble() * 100 - 50);
            Point b = a.plus(new Point(Math.cos(angle) * length, 0, Math.sin(angle) * length));
            var active = ConductiveCircuitGeometry.segments(a, b, true, i, 999);
            check(active.size() <= Math.ceil(ConductiveCircuitGeometry.MAX_LENGTH / .35), "energized segment allocation is bounded");
            check(active.getFirst().start().equals(a) && active.getLast().end().equals(b), "energized path uses exact authoritative endpoints");
            Point direction = b.minus(a).scale(1 / length), side = new Point(-direction.z(), 0, direction.x());
            for (int j = 0; j < active.size(); j++) {
                var segment = active.get(j);
                check(close(segment.start().y(), a.y()) && close(segment.end().y(), a.y()), "all energized points stay grounded");
                check(Math.abs(segment.end().minus(a).dot(side)) <= ConductiveCircuitGeometry.JAGGED_OFFSET + 1e-9, "jagged movement stays within a narrow strip");
                if (j > 0) check(segment.start().equals(active.get(j - 1).end()), "energized path stays continuous");
            }
            check(active.equals(ConductiveCircuitGeometry.segments(a, b, true, i, 999)), "same authoritative token and tick yield deterministic geometry");
            // Include vertical and nearly vertical views: a floor ribbon must never become an upright billboard.
            double pitch = i % 3 == 0 ? -Math.PI / 2 : (random.nextDouble() - .5) * Math.PI;
            double yaw = random.nextDouble() * Math.PI * 2;
            Point view = new Point(Math.cos(pitch) * Math.sin(yaw), Math.sin(pitch), Math.cos(pitch) * Math.cos(yaw));
            Point camera = a.minus(view.scale(2)).plus(new Point(0, .001, 0));
            for (var segment : active) {
                Ribbon clipped = ConductiveCircuitGeometry.clip(segment.start().minus(camera), segment.end().minus(camera), view, .018);
                if (clipped != null) checkRibbon(clipped, view, a.y() - camera.y(), .018);
            }
        }
        System.out.println("PASS " + checks + " NEW-121 grounded anchors, dashed/jagged paths, finite near-clip and bounded-width checks");
    }
    private static void checkRibbon(Ribbon ribbon, Point forward, double y, double requestedWidth) {
        check(ribbon != null, "visible grounded segment retained");
        check(ribbon.halfWidth() > 0 && ribbon.halfWidth() <= requestedWidth, "ribbon clipping never expands requested width");
        double nearest = Math.min(ribbon.start().dot(forward), ribbon.end().dot(forward));
        check(ribbon.halfWidth() / nearest <= ConductiveCircuitGeometry.MAX_ANGULAR_HALF_WIDTH + 1e-12, "bounded projected half-width");
        for (Point vertex : List.of(ribbon.start().plus(ribbon.side()), ribbon.start().minus(ribbon.side()),
                ribbon.end().minus(ribbon.side()), ribbon.end().plus(ribbon.side()))) {
            check(vertex.finite() && close(vertex.y(), y), "all four output vertices remain on authoritative plane");
            check(vertex.dot(forward) >= ConductiveCircuitGeometry.NEAR_DEPTH - 1e-10, "full ribbon is in front of near plane");
        }
    }
}
