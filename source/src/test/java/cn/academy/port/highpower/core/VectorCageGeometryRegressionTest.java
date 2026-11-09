package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.VectorCageGeometry.Box;
import cn.academy.port.highpower.core.VectorCageGeometry.Contact;
import cn.academy.port.highpower.core.VectorCageGeometry.Frame;
import cn.academy.port.highpower.core.VectorCageGeometry.Point;
import java.util.List;
import java.util.Random;

/** Analytic CPU-only regressions for finite continuous faces against native-shaped AABBs. */
public final class VectorCageGeometryRegressionTest {
    private static int checks;
    private static final Point ZERO = new Point(0, 0, 0);
    private static final Frame FRAME = new Frame(ZERO);

    private static void check(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }
    private static void close(double actual, double expected, double tolerance, String why) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                why + ": actual=" + actual + " expected=" + expected);
    }
    private static void rejected(Runnable action, String why) {
        try { action.run(); throw new AssertionError(why); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static Box moved(Box box, Point offset) {
        return new Box(box.minX() + offset.x(), box.minY() + offset.y(), box.minZ() + offset.z(),
                box.maxX() + offset.x(), box.maxY() + offset.y(), box.maxZ() + offset.z());
    }
    private static boolean contains(Box box, Point point) {
        return point.x() >= box.minX() && point.x() <= box.maxX()
                && point.y() >= box.minY() && point.y() <= box.maxY()
                && point.z() >= box.minZ() && point.z() <= box.maxZ();
    }
    private static Box positiveX(double low, double high, double zLow, double zHigh) {
        return new Box(low, 1, zLow, high, 2, zHigh);
    }

    public static void main(String[] args) {
        continuousAndFinite();
        exactEdgesAndTies();
        translationAndBounds();
        rangeAndValidation();
        randomThinSweeps();
        System.out.println("PASS " + checks + " NEW441 continuous finite-face/AABB geometry checks");
    }

    private static void continuousAndFinite() {
        Box thin = positiveX(2.2, 2.200001, .123, .124);
        Contact hit = VectorCageGeometry.earliestContact(FRAME, 4, .25, thin).orElseThrow();
        check(hit.wall() == VectorCageGeometry.POSITIVE_X, "thin interior target meets the actual +X wall");
        close(hit.time(), (4 - 2.200001) / 3.75, 0, "analytic continuous first contact between endpoints");
        check(hit.time() > 0 && hit.time() < 1 && contains(thin, hit.point()), "real AABB contact, not center proximity");
        close(hit.point().x(), 2.200001, 0, "earliest target edge is authoritative");
        close(hit.previousPoint().x(), 4, 0, "previous point starts on actual wall");
        close(hit.previousPoint().z() / 4, hit.point().z() / 2.200001, 1e-16,
                "contact traces one shrinking material coordinate");
        check(VectorCageGeometry.earliestContact(FRAME, 4, 4, thin).isEmpty()
                && VectorCageGeometry.earliestContact(FRAME, .25, .25, thin).isEmpty(),
                "endpoint-only tests would miss thin target");
        int hits = 0;
        for (int age = 6; age <= 11; age++) if (VectorCageGeometry.earliestContact(FRAME,
                VectorCageRules.fromHalfWidth(age), VectorCageRules.toHalfWidth(age), thin).isPresent()) hits++;
        check(hits == 1, "six real contiguous steps catch the thin target once");
        check(VectorCageGeometry.earliestContact(FRAME, 4, .25,
                new Box(-.2, 1, -.2, .2, 2, .2)).isEmpty(), "empty inner cavity is not a filled damage volume");
        check(VectorCageGeometry.earliestContact(FRAME, VectorCageGeometry.POSITIVE_X, 4, .25,
                positiveX(2, 2.1, 2.2, 2.3)).isEmpty(), "shrinking orthogonal span rejects infinite-plane false hit");
        check(VectorCageGeometry.earliestContact(FRAME, 4, .25,
                positiveX(4.001, 4.1, -.1, .1)).isEmpty(), "no hidden pressure-face thickness or outward sweep");
        Box large = positiveX(1.5, 5, .1, .2);
        Contact edge = VectorCageGeometry.earliestContact(FRAME, 4, 3.375, large).orElseThrow();
        check(edge.time() == 0 && contains(large, edge.point()), "a box edge overlaps even when its center is elsewhere");
        check(VectorCageGeometry.earliestContact(FRAME, 4, .25,
                new Box(1, 3.1, -.1, 2, 4, .1)).isEmpty(), "faces cannot acquire infinite vertical extent");
    }

    private static void exactEdgesAndTies() {
        Box edge = new Box(4, .05, -.1, 4.1, .1, .1);
        check(VectorCageGeometry.earliestContact(FRAME, 4, 4, edge).orElseThrow().time() == 0,
                "closed initial face and lower height edge");
        check(VectorCageGeometry.earliestContact(FRAME, 4, 4,
                new Box(Math.nextUp(4.0), .05, -.1, 4.1, .1, .1)).isEmpty(),
                "one representable step past plane is outside");
        check(VectorCageGeometry.earliestContact(FRAME, 4, 4,
                new Box(3.9, .05, -.1, Math.nextDown(4.0), .1, .1)).isEmpty(),
                "one representable step inside plane is outside a static face");
        double top = VectorCageGeometry.BOTTOM_OFFSET + VectorCageGeometry.HEIGHT;
        check(VectorCageGeometry.earliestContact(FRAME, 4, 4,
                new Box(3.9, top, -.1, 4.1, top + .1, .1)).isPresent(), "closed top height edge");
        check(VectorCageGeometry.earliestContact(FRAME, 4, 4,
                new Box(3.9, Math.nextUp(top), -.1, 4.1, top + .1, .1)).isEmpty(), "no height epsilon expansion");
        check(VectorCageGeometry.earliestContact(FRAME, 4, 4,
                new Box(3.9, -.1, -.1, 4.1, Math.nextDown(.05), .1)).isEmpty(), "below bottom is outside");
        Contact last = VectorCageGeometry.earliestContact(FRAME, 4, .25,
                positiveX(.2, .25, -.1, .1)).orElseThrow();
        close(last.time(), 1, 0, "closed final-time edge reaches inner half-width");
        check(VectorCageGeometry.earliestContact(FRAME, 4, .25,
                positiveX(.2, Math.nextDown(.25), -.1, .1)).isEmpty(), "no extrapolation past final face");
        Contact corner = VectorCageGeometry.earliestContact(FRAME, 4, .25,
                new Box(2, 1, 2, 2.1, 2, 2.1)).orElseThrow();
        check(corner.wall() == VectorCageGeometry.POSITIVE_X, "+X wins simultaneous +X/+Z corner");
        close(corner.time(), (4 - 2.1) / 3.75, 0, "corner tie retains earliest continuous time");
        Contact all = VectorCageGeometry.earliestContact(FRAME, 4, .25,
                new Box(-5, 0, -5, 5, 4, 5)).orElseThrow();
        check(all.wall() == VectorCageGeometry.NEGATIVE_X && all.time() == 0,
                "all-face simultaneous overlap chooses stable first wall");
        for (int repeat = 0; repeat < 50; repeat++) check(corner.equals(VectorCageGeometry.earliestContact(FRAME,
                4, .25, new Box(2, 1, 2, 2.1, 2, 2.1)).orElseThrow()), "corner ties are deterministic");
        Box tangent = positiveX(1.9, 2, 2, 2.1);
        check(VectorCageGeometry.earliestContact(FRAME, 1, 4, .25, tangent).isPresent(),
                "finite moving face keeps exact simultaneous tangential corner");
        check(VectorCageGeometry.earliestContact(FRAME, 1, 4, .25,
                positiveX(1.9, 2, Math.nextUp(2.0), 2.1)).isEmpty(),
                "one representable step beyond shrinking corner is outside that face");
    }

    private static void translationAndBounds() {
        Box box = positiveX(2.125, 2.126, .125, .25);
        Contact baseline = VectorCageGeometry.earliestContact(FRAME, 4, .25, box).orElseThrow();
        for (Point shift : List.of(ZERO, new Point(8192, 64, -8192), new Point(-1_000_000, 100, 1_000_000),
                new Point(29_999_990, 64, -29_999_990), new Point(-29_999_990, 64, 29_999_990))) {
            Frame frame = new Frame(shift);
            Box actualBox = moved(box, shift);
            Contact hit = VectorCageGeometry.earliestContact(frame, 4, .25, actualBox).orElseThrow();
            close(hit.time(), baseline.time(), 1e-9, "translation preserves continuous contact time near world border");
            close(hit.point().x() - shift.x(), baseline.point().x(), 3e-9, "near-border contact keeps sub-block geometry");
            check(hit.wall() == baseline.wall() && contains(actualBox, hit.point()), "translated contact stays inside actual AABB");
            Box full = VectorCageGeometry.bounds(frame);
            close(full.maxX() - full.minX(), 8, 0, "frozen field width is eight metres");
            close(full.maxZ() - full.minZ(), 8, 0, "frozen field depth is eight metres");
            close(full.maxY() - full.minY(), 3, 1e-13, "frozen field height is three metres");
            for (double h : new double[]{4, 3.375, 2.75, 2.125, 1.5, .875, .25}) {
                check(VectorCageGeometry.vertices(frame, h).size() == 8, "eight shared authoritative ring vertices");
                for (Point vertex : VectorCageGeometry.vertices(frame, h)) check(contains(full, vertex),
                        "full frozen bounds enclose all face vertices at every step");
                for (int wall = 0; wall < 4; wall++) for (Point vertex : VectorCageGeometry.faceVertices(frame, wall, h)) {
                    check(VectorCageGeometry.vertices(frame, h).contains(vertex), "face corners exactly match common geometry");
                    check(contains(VectorCageGeometry.sweptBounds(frame, h, .25), vertex), "broad phase contains finite faces");
                }
            }
        }
        Frame touching = new Frame(new Point(29_999_996, 0, -29_999_996));
        check(VectorCageGeometry.bounds(touching).finite(), "whole field may exactly touch world boundary");
        rejected(() -> new Frame(new Point(Math.nextUp(29_999_996.0), 0, 0)), "no full field beyond world boundary");
    }

    private static void rangeAndValidation() {
        check(VectorCageGeometry.withinRange(new Frame(new Point(50, 0, 0)), 4, ZERO, ZERO),
                "all vertices of nearby field fit in both range spheres");
        check(!VectorCageGeometry.withinRange(new Frame(new Point(52, 0, 0)), 4, ZERO, ZERO),
                "center in range does not excuse out-of-range corner vertices");
        check(!VectorCageGeometry.withinRange(FRAME, 4, ZERO, new Point(60, 0, 0)),
                "current owner position independently bounds field");
        Point exact = new Point(56, 0, 0);
        check(VectorCageGeometry.contactWithinRange(new Contact(0, 0, exact, exact), ZERO, ZERO), "closed exact56m contact");
        Point beyond = new Point(Math.nextUp(56.0), 0, 0);
        check(!VectorCageGeometry.contactWithinRange(new Contact(0, 0, beyond, beyond), ZERO, ZERO), "contact range has no epsilon apron");
        check(!VectorCageGeometry.contactWithinRange(new Contact(0, 0, ZERO, beyond), ZERO, ZERO), "previous wall material point is range checked");
        for (Box invalid : List.of(new Box(0, 0, 0, 0, 1, 1), new Box(0, 0, 0, 1, 0, 1),
                new Box(0, 0, 0, 1, 1, 0), new Box(1, 0, 0, 0, 1, 1),
                new Box(Double.NaN, 0, 0, 1, 1, 1), new Box(0, 0, 0, Double.POSITIVE_INFINITY, 1, 1)))
            check(VectorCageGeometry.earliestContact(FRAME, 4, .25, invalid).isEmpty(), "degenerate/nonfinite AABB fails closed");
        Box valid = positiveX(2, 3, -.1, .1);
        check(VectorCageGeometry.earliestContact(null, 4, .25, valid).isEmpty()
                && VectorCageGeometry.earliestContact(FRAME, 4, .25, null).isEmpty(), "null frame or box fails closed");
        for (double[] widths : new double[][]{{Double.NaN, .25}, {4, Double.NaN}, {Double.POSITIVE_INFINITY, .25},
                {4, Double.NEGATIVE_INFINITY}, {4.0001, .25}, {4, .2499}, {.25, 4}, {-1, -2}})
            check(VectorCageGeometry.earliestContact(FRAME, widths[0], widths[1], valid).isEmpty(), "invalid contraction fails closed");
        check(VectorCageGeometry.earliestContact(FRAME, -1, 4, .25, valid).isEmpty()
                && VectorCageGeometry.earliestContact(FRAME, 4, 4, .25, valid).isEmpty(), "invalid wall fails closed");
        rejected(() -> new Frame(null), "null frozen center rejected");
        rejected(() -> new Frame(new Point(Double.NaN, 0, 0)), "nonfinite frozen center rejected");
        rejected(() -> VectorCageGeometry.vertices(FRAME, .2), "undersized face rejected");
        rejected(() -> VectorCageGeometry.faceVertices(FRAME, 4, 4), "fifth face rejected");
        check(!VectorCageGeometry.withinRange(FRAME, 4, null, ZERO), "missing range origin rejected");
        check(!VectorCageGeometry.contactWithinRange(new Contact(Double.NaN, 0, ZERO, ZERO), ZERO, ZERO), "nonfinite contact time rejected");
    }

    private static void randomThinSweeps() {
        Random random = new Random(441);
        for (int i = 0; i < 1000; i++) {
            double edge = .5 + random.nextDouble() * 3;
            double width = 1e-8 + random.nextDouble() * 1e-5;
            int wall = i % 4;
            Box box = switch (wall) {
                case 0 -> new Box(-edge, 1, .1, -edge + width, 2, .2);
                case 1 -> new Box(edge - width, 1, .1, edge, 2, .2);
                case 2 -> new Box(.1, 1, -edge, .2, 2, -edge + width);
                default -> new Box(.1, 1, edge - width, .2, 2, edge);
            };
            Contact contact = VectorCageGeometry.earliestContact(FRAME, 4, .25, box).orElseThrow();
            check(contact.wall() == wall, "all four opposing faces independently reach thin AABBs");
            close(contact.time(), (4 - edge) / 3.75, 0, "random thin target retains analytic earliest time");
            check(contains(box, contact.point()), "every reported random contact lies on real target box");
            check(VectorCageGeometry.earliestContact(FRAME, 4, 4, box).isEmpty()
                    && VectorCageGeometry.earliestContact(FRAME, .25, .25, box).isEmpty(),
                    "random swept hits require continuous geometry");
        }
    }
}
