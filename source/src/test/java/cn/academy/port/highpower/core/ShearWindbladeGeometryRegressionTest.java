package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.ShearWindbladeGeometry.Box;
import cn.academy.port.highpower.core.ShearWindbladeGeometry.Contact;
import cn.academy.port.highpower.core.ShearWindbladeGeometry.Frame;
import cn.academy.port.highpower.core.ShearWindbladeGeometry.Point;
import java.util.List;
import java.util.Random;

/** CPU-only analytic/contact regression suite; no Minecraft runtime or source-text oracle. */
public final class ShearWindbladeGeometryRegressionTest {
    private static int checks;
    private static final double R = ShearWindbladeGeometry.RADIUS;
    private static final double LIMIT = ShearWindbladeGeometry.MAX_TRANSLATION;
    private static final Point ZERO = p(0, 0, 0);

    private static Point p(double x, double y, double z) { return new Point(x, y, z); }
    private static Point add(Point a, Point b) { return p(a.x() + b.x(), a.y() + b.y(), a.z() + b.z()); }
    private static Point scale(Point a, double s) { return p(a.x() * s, a.y() * s, a.z() * s); }
    private static Point sub(Point a, Point b) { return p(a.x() - b.x(), a.y() - b.y(), a.z() - b.z()); }
    private static Box moved(Box b, Point t) {
        return new Box(b.minX() + t.x(), b.minY() + t.y(), b.minZ() + t.z(),
                b.maxX() + t.x(), b.maxY() + t.y(), b.maxZ() + t.z());
    }
    private static void yes(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void close(double actual, double expected, double tolerance, String message) {
        yes(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                message + ": actual=" + actual + " expected=" + expected);
    }
    private static void close(Point a, Point b, double tolerance, String message) {
        close(a.x(), b.x(), tolerance, message + " x"); close(a.y(), b.y(), tolerance, message + " y");
        close(a.z(), b.z(), tolerance, message + " z");
    }
    private static void rejected(Runnable work, String message) {
        try { work.run(); throw new AssertionError(message); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static Frame frame(Point origin, Point look) { return ShearWindbladeGeometry.frame(origin, look, 0); }
    private static Box tiny(Point point) {
        return new Box(point.x() - .002, point.y() - .002, point.z() - .002,
                point.x() + .002, point.y() + .002, point.z() + .002);
    }

    private static void interiorChordAndTime() {
        Point a = p(-1, 0, 0), b = p(1, 0, 0), delta = p(0, 0, 2);
        Box target = new Box(.123, -.001, .9, .124, .001, .91);
        Contact c = ShearWindbladeGeometry.sweepChord(a, b, delta, target).orElseThrow();
        close(c.time(), (.9 - R) / 2, 1e-15, "analytical first travel fraction");
        close(c.along(), (1 + .123 - R) / 2, 1e-15, "actual interior chord coordinate");
        yes(c.along() > 0 && c.along() < 1, "contact cannot be represented by either endpoint ray");
        close(c.point(), p(.123 - R, 0, .9 - R), 1e-15, "actual swept contact");
        close(c.previousPoint(), p(.123 - R, 0, 0), 1e-15, "previous point is same u, not a chord endpoint");
        close(sub(c.point(), c.previousPoint()), scale(delta, c.time()), 1e-15, "contact trace follows actual material point");
        yes(ShearWindbladeGeometry.sweepChord(a, b, delta,
                new Box(1 + R + 1e-10, -.1, 0, 1.2, .1, 2)).isEmpty(), "no extrapolation past finite chord");
        yes(ShearWindbladeGeometry.sweepChord(a, b, delta,
                new Box(-.1, -.1, 2 + R + 1e-10, .1, .1, 3)).isEmpty(), "no extrapolation past finite sweep");
    }

    private static void exactTangencyAndStatic() {
        Point a = p(-1, 0, 0), b = p(1, 0, 0), travel = p(0, 0, 2);
        yes(ShearWindbladeGeometry.sweepChord(a, b, travel,
                new Box(-.01, R, .5, .01, R + .1, 1)).isPresent(), "closed exact radius tangency");
        yes(ShearWindbladeGeometry.sweepChord(a, b, travel,
                new Box(-.01, Math.nextUp(R), .5, .01, R + .1, 1)).isEmpty(),
                "one representable step beyond radius is rejected without epsilon");
        Contact c = ShearWindbladeGeometry.sweepChord(a, b, ZERO,
                new Box(.31, -.01, -.01, .32, .01, .01)).orElseThrow();
        close(c.time(), 0, 0, "static segment overlap starts now");
        close(c.point(), c.previousPoint(), 0, "static contact has no travel");
        yes(ShearWindbladeGeometry.sweepChord(a, b, ZERO,
                new Box(-.1, .1, -.1, .1, .2, .1)).isEmpty(), "static miss stays empty");
        Contact atEnd = ShearWindbladeGeometry.sweepChord(a, b, travel,
                new Box(-.1, -.1, 2 + R, .1, .1, 3)).orElseThrow();
        close(atEnd.time(), 1, 1e-15, "closed end-time tangency");
        Contact corner = ShearWindbladeGeometry.sweepChord(a, b, travel,
                new Box(1 + R, -.1, 2 + R, 2, .1, 3)).orElseThrow();
        close(corner.time(), 1, 0, "single-corner tangent keeps closed final time");
        close(corner.along(), 1, 0, "single-corner tangent keeps closed chord endpoint");
    }

    private static void finiteArcNotFan() {
        Frame f = frame(ZERO, p(0, 0, 1));
        List<Point> vertices = ShearWindbladeGeometry.front(f, 0, 3);
        yes(vertices.size() == 9, "exactly eight chords");
        close(vertices.getFirst(), p(-1.2, 0, 3), 1e-15, "left finite endpoint");
        close(vertices.get(4), p(0, 0, 3.4), 1e-15, "forward bow center");
        close(vertices.getLast(), p(1.2, 0, 3), 1e-15, "right finite endpoint");
        Point between = add(scale(vertices.get(1), .37), scale(vertices.get(2), .63));
        Contact interior = ShearWindbladeGeometry.earliestContact(f, 0, 3, 3, tiny(between)).orElseThrow();
        yes(interior.chord() == 1 && interior.along() > .4 && interior.along() < .8,
                "static arc interior between vertices is authoritative");
        yes(ShearWindbladeGeometry.earliestContact(f, 0, 3, 5,
                new Box(-.1, -.1, .5, .1, .1, .6)).isEmpty(), "fan interior behind moving front is empty");
        yes(ShearWindbladeGeometry.earliestContact(f, 0, 3, 3,
                new Box(-.1, -.1, 3.1, .1, .1, 3.2)).isEmpty(), "bow does not fill the sector behind it");
        yes(ShearWindbladeGeometry.earliestContact(f, 0, 3, 5,
                new Box(1.3, -.1, 3, 1.4, .1, 4)).isEmpty(), "finite width rejects neighboring radial sector");
        Box near = new Box(-2, -1, 3, 2, 1, 3.1), far = new Box(-2, -1, 6, 2, 1, 6.1);
        Contact n = ShearWindbladeGeometry.earliestContact(f, 0, 0, LIMIT, near).orElseThrow();
        Contact distant = ShearWindbladeGeometry.earliestContact(f, 0, 0, LIMIT, far).orElseThrow();
        close(n.time(), (3 - R - .4) / LIMIT, 1e-15, "nearest wall time follows bow center");
        close(distant.time(), (6 - R - .4) / LIMIT, 1e-15, "farther wall's independent time");
        yes(n.time() < distant.time(), "native obstruction candidates sort by time");
        yes(n.chord() == 3, "shared-vertex tie uses earlier chord deterministically");
        close(n.along(), 1, 1e-15, "shared vertex correction can only move representably inward");
        for (int i = 0; i < 30; i++) yes(n.equals(ShearWindbladeGeometry.earliestContact(f, 0, 0, LIMIT, near).orElseThrow()),
                "same sweep produces identical contact and tie order");
    }

    private static void planesPolesAndCapturedFrame() {
        Frame f = frame(p(0, 64, 0), p(0, 0, 1));
        Box horizontal = tiny(p(1, 64, 4.1)), vertical = tiny(p(0, 65, 4.1));
        yes(ShearWindbladeGeometry.earliestContact(f, 0, 3, 5, horizontal).isPresent(), "horizontal front hits side target");
        yes(ShearWindbladeGeometry.earliestContact(f, 1, 3, 5, horizontal).isEmpty(), "vertical front does not acquire horizontal width");
        yes(ShearWindbladeGeometry.earliestContact(f, 1, 3, 5, vertical).isPresent(), "vertical front hits upper target");
        yes(ShearWindbladeGeometry.earliestContact(f, 0, 3, 5, vertical).isEmpty(), "horizontal front does not acquire vertical width");
        List<Point> frozen = ShearWindbladeGeometry.front(f, 0, 4);
        Frame unrelatedCamera = ShearWindbladeGeometry.frame(p(99, 77, -99), p(1, -1, .2), 2.9);
        yes(unrelatedCamera != f && frozen.equals(ShearWindbladeGeometry.front(f, 0, 4)), "camera or later look cannot mutate captured front");
        for (Point look : List.of(p(0, 1, 0), p(0, -1, 0), p(1e-200, 1, -1e-200), p(1e-15, -1, 0))) {
            for (double yaw : new double[]{0, .7, Math.PI, -2.2}) {
                Frame pole = ShearWindbladeGeometry.frame(p(0, 64, 0), look, yaw);
                for (int plane = 0; plane < 2; plane++) for (Point v : ShearWindbladeGeometry.front(pole, plane, LIMIT))
                    yes(v.finite(), "pole and near-pole frame remains finite for both planes");
                close(pole.forward().distanceSquared(ZERO), 1, 1e-15, "pole unit forward");
            }
        }
        Frame north = ShearWindbladeGeometry.frame(ZERO, p(0, 1, 0), 0);
        Frame east = ShearWindbladeGeometry.frame(ZERO, p(0, 1, 0), Math.PI / 2);
        close(north.horizontal(), p(1, 0, 0), 1e-15, "pole uses captured yaw fallback");
        close(east.horizontal(), p(0, 0, 1), 1e-15, "different captured pole yaw rotates basis intentionally");
    }

    private static void translationAndBounds() {
        Point look = p(.31, .22, 1);
        Frame zero = frame(ZERO, look);
        Point witness = ShearWindbladeGeometry.front(zero, 0, 5).get(3);
        Box box = tiny(witness);
        Contact baseline = ShearWindbladeGeometry.earliestContact(zero, 0, 4, 6, box).orElseThrow();
        for (Point shift : List.of(ZERO, p(8192, 64, -8192), p(-1_000_000, 100, 1_000_000),
                p(29_999_900, 64, -29_999_900), p(-29_999_900, 64, 29_999_900))) {
            Frame translated = frame(shift, look);
            Contact actual = ShearWindbladeGeometry.earliestContact(translated, 0, 4, 6, moved(box, shift)).orElseThrow();
            close(actual.time(), baseline.time(), 5e-9, "double-relative sweep time survives worldborder translation");
            close(actual.along(), baseline.along(), 5e-8, "double-relative along survives worldborder translation");
            close(sub(actual.point(), shift), baseline.point(), 7e-9, "actual contact world coordinates retain small geometry");
            yes(actual.chord() == baseline.chord(), "worldborder translation preserves chord");
            for (int plane = 0; plane < 2; plane++) {
                Box bounds = ShearWindbladeGeometry.sweptBounds(translated, plane, 2, 4);
                for (double d : new double[]{2, 2.25, 3, 3.5, 4}) for (Point point : ShearWindbladeGeometry.front(translated, plane, d))
                    yes(point.x() >= bounds.minX() && point.x() <= bounds.maxX()
                            && point.y() >= bounds.minY() && point.y() <= bounds.maxY()
                            && point.z() >= bounds.minZ() && point.z() <= bounds.maxZ(), "broad phase contains complete translated chords");
            }
        }
        Frame border = frame(p(29_999_999, 64, 0), p(1, 0, 0));
        rejected(() -> ShearWindbladeGeometry.front(border, 0, 4), "front cannot cross finite world border");
        rejected(() -> ShearWindbladeGeometry.sweptBounds(border, 0, 0, 4), "sweep cannot cross finite world border");
        yes(ShearWindbladeGeometry.earliestContact(border, 0, 0, 4,
                new Box(29_999_999, 63, -1, 30_000_000, 65, 1)).isEmpty(), "out-of-world sweep is closed failure");
    }

    private static void fullReachEnvelope() {
        Random random = new Random(421);
        for (int k = 0; k < 200; k++) {
            Frame f = frame(ZERO, p(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1));
            for (int plane = 0; plane < 2; plane++) {
                List<Point> initial = ShearWindbladeGeometry.front(f, plane, 0), end = ShearWindbladeGeometry.front(f, plane, LIMIT);
                for (int i = 0; i <= ShearWindbladeGeometry.CHORDS; i++) {
                    close(sub(end.get(i), initial.get(i)), scale(f.forward(), LIMIT), 8e-15, "all vertices share one translation without clamping");
                    yes(Math.sqrt(end.get(i).distanceSquared(ZERO)) + Math.sqrt(3) * R <= 24,
                            "entire declared axis-aligned radius envelope remains within 24m");
                }
            }
        }
        Frame f = frame(ZERO, p(0, 0, 1));
        rejected(() -> ShearWindbladeGeometry.front(f, 0, Math.nextUp(LIMIT)), "no tolerance beyond common maximum translation");
        yes(ShearWindbladeGeometry.earliestContact(f, 0, 0, LIMIT,
                new Box(-.01, -.01, 24, .01, .01, 24.1)).isEmpty(), "24m boundary is not extended by bow or radius");
    }

    private static void malformedInputs() {
        Frame f = frame(ZERO, p(0, 0, 1));
        Box valid = new Box(-.1, -.1, 1, .1, .1, 2);
        for (Box box : new Box[]{null, new Box(1, 0, 0, 0, 1, 1), new Box(0, 0, 0, 0, 1, 1),
                new Box(Double.NaN, 0, 0, 1, 1, 1), new Box(0, 0, 0, Double.POSITIVE_INFINITY, 1, 1),
                new Box(30_000_000, 0, 0, 30_000_001, 1, 1)})
            yes(ShearWindbladeGeometry.earliestContact(f, 0, 0, 2, box).isEmpty(), "malformed box cannot create contact");
        for (double[] interval : new double[][]{{-1, 1}, {2, 1}, {0, Double.NaN}, {0, Double.POSITIVE_INFINITY}, {0, LIMIT + 1}})
            yes(ShearWindbladeGeometry.earliestContact(f, 0, interval[0], interval[1], valid).isEmpty(), "malformed travel cannot create contact");
        yes(ShearWindbladeGeometry.earliestContact(null, 0, 0, 2, valid).isEmpty(), "null frame rejected");
        yes(ShearWindbladeGeometry.earliestContact(f, 2, 0, 2, valid).isEmpty(), "unknown plane rejected");
        yes(ShearWindbladeGeometry.sweepChord(ZERO, ZERO, p(0, 0, 2), valid).isEmpty(), "degenerate chord rejected");
        yes(ShearWindbladeGeometry.sweepChord(ZERO, p(1, 0, 0), p(0, 0, Double.NaN), valid).isEmpty(), "nonfinite translation rejected");
        rejected(() -> frame(ZERO, ZERO), "zero look rejected");
        rejected(() -> frame(p(Double.NaN, 0, 0), p(0, 0, 1)), "nonfinite origin rejected");
        rejected(() -> ShearWindbladeGeometry.frame(ZERO, p(0, 0, 1), Double.NaN), "nonfinite fallback yaw rejected");
        rejected(() -> new Frame(ZERO, p(0, 0, 2), p(1, 0, 0), p(0, 1, 0)), "nonunit fabricated basis rejected");
    }

    private static void returnedContactIsActuallyInside() {
        Random random = new Random(421421);
        for (int sample = 0; sample < 800; sample++) {
            Point origin = switch (sample % 3) {
                case 1 -> p(29_999_900, 100, -29_999_900);
                case 2 -> p(-29_999_900, -100, 29_999_900);
                default -> ZERO;
            };
            Frame f = frame(origin, p(random.nextDouble() - .5, random.nextDouble() - .5, random.nextDouble() - .5));
            int plane = sample % 2, chord = random.nextInt(8);
            double from = random.nextDouble() * 18, to = from + 2, along = random.nextDouble(), fraction = random.nextDouble();
            Frame localFrame = new Frame(ZERO, f.forward(), f.horizontal(), f.vertical());
            List<Point> initial = ShearWindbladeGeometry.front(localFrame, plane, from);
            Point a = initial.get(chord), side = sub(initial.get(chord + 1), a), delta = scale(f.forward(), to - from);
            Point witness = add(origin, add(a, add(scale(side, along), scale(delta, fraction))));
            Box box = tiny(witness);
            Contact c = ShearWindbladeGeometry.earliestContact(f, plane, from, to, box).orElseThrow();
            yes(c.time() <= fraction + 3e-9, "first contact cannot be later than a known interior witness");
            yes(Double.isFinite(c.time()) && Double.isFinite(c.along()) && c.time() >= 0 && c.time() <= 1
                    && c.along() >= 0 && c.along() <= 1, "returned parameters are finite and in original unit square");
            a = initial.get(c.chord()); side = sub(initial.get(c.chord() + 1), a);
            double[] originAxis = {origin.x(), origin.y(), origin.z()}, aAxis = {a.x(), a.y(), a.z()},
                    uAxis = {side.x(), side.y(), side.z()}, tAxis = {delta.x(), delta.y(), delta.z()},
                    low = {box.minX(), box.minY(), box.minZ()}, high = {box.maxX(), box.maxY(), box.maxZ()},
                    reported = {c.point().x(), c.point().y(), c.point().z()};
            for (int axis = 0; axis < 3; axis++) {
                double min = (low[axis] - originAxis[axis]) - aAxis[axis] - R,
                        max = (high[axis] - originAxis[axis]) - aAxis[axis] + R;
                yes(Math.fma(-uAxis[axis], c.along(), Math.fma(-tAxis[axis], c.time(), max)) >= 0,
                        "returned pair satisfies original upper half-plane without tolerance");
                yes(Math.fma(uAxis[axis], c.along(), Math.fma(tAxis[axis], c.time(), -min)) >= 0,
                        "returned pair satisfies original lower half-plane without tolerance");
                yes(reported[axis] - originAxis[axis] >= (low[axis] - originAxis[axis]) - R
                                && reported[axis] - originAxis[axis] <= (high[axis] - originAxis[axis]) + R,
                        "reported world point is inside actual relative radius-expanded box without tolerance");
            }
        }
    }

    public static void main(String[] args) {
        interiorChordAndTime(); exactTangencyAndStatic(); finiteArcNotFan(); planesPolesAndCapturedFrame();
        translationAndBounds(); fullReachEnvelope(); malformedInputs(); returnedContactIsActuallyInside();
        System.out.println("ShearWindbladeGeometryRegressionTest: " + checks + " checks passed");
    }
}
