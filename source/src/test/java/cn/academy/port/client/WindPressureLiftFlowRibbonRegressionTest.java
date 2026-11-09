package cn.academy.port.client;

import cn.academy.port.client.WindPressureLiftFlowMesh.Segment;
import cn.academy.port.client.WindPressureLiftFlowMesh.Shape;
import cn.academy.port.client.WindPressureLiftFlowRibbon.Draw;
import cn.academy.port.client.WindPressureLiftFlowRibbon.Quad;
import cn.academy.port.client.WindPressureLiftMesh.Point;
import cn.academy.port.highpower.core.WindPressureLiftRules.Mode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Executed expanded-corner and clipping evidence, not an assertion of rendered-image acceptance. */
public final class WindPressureLiftFlowRibbonRegressionTest {
    private static int checks;
    private static final Point ZERO = new Point(0, 0, 0), X = new Point(1, 0, 0), Y = new Point(0, 1, 0), Z = new Point(0, 0, 1);
    private static final double[] PASS_SCALES = {1, .24, WindPressureLiftFlowMesh.CORE_WIDTH_SCALE};

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static Shape shape(Point anchor, Mode mode, int age, double partial) {
        var identity = new WindPressureLiftState.Identity(1, new UUID(3, 1), 1, 11, 1);
        var frame = new WindPressureLiftState.Frame(identity, 1, anchor, mode, new Point(Math.sqrt(.5), 0, Math.sqrt(.5)), 10, 14, false);
        return WindPressureLiftFlowMesh.shape(new WindPressureLiftState.Controller(frame, true, age, 0), partial);
    }
    private static Point add(Point a, Point b) { return new Point(a.x() + b.x(), a.y() + b.y(), a.z() + b.z()); }
    private static Point sub(Point a, Point b) { return new Point(a.x() - b.x(), a.y() - b.y(), a.z() - b.z()); }
    private static Point mul(Point a, double s) { return new Point(a.x() * s, a.y() * s, a.z() * s); }
    private static Point lerp(Point a, Point b, double t) { return add(a, mul(sub(b, a), t)); }
    private static double dot(Point a, Point b) { return a.x() * b.x() + a.y() * b.y() + a.z() * b.z(); }
    private static Point unit(Point a) { return mul(a, 1 / Math.sqrt(dot(a, a))); }
    private static Point offset(Draw draw, boolean end, boolean positive, double scale) {
        Point center = end ? draw.segment().b() : draw.segment().a();
        Point side = end ? draw.sideTo() : draw.sideFrom();
        double width = end ? draw.segment().widthTo() : draw.segment().widthFrom();
        return add(center, mul(side, width * .5 * scale * (positive ? 1 : -1)));
    }
    private static Point[] corners(Quad q) { return new Point[]{q.aMinus(), q.aPlus(), q.bPlus(), q.bMinus()}; }
    private static void bounds(Point relativeCorner, Point camera, Point anchor) {
        Point p = add(relativeCorner, sub(camera, anchor));
        check(p.finite(), "actual corner is finite");
        check(Math.hypot(p.x(), p.z()) <= 3 + 1e-8, "actual expanded corner stays inside six metre diameter");
        check(p.y() >= -1e-8 && p.y() <= 6 + 1e-8, "actual expanded corner stays inside six metre height");
    }

    private static void joinsAndWitnesses() {
        Shape shape = shape(ZERO, Mode.UP, 26, .25);
        for (Point camera : new Point[]{new Point(1.8, 1.62, 0), new Point(0, 1.62, 0),
                new Point(8, 2, -8), new Point(-7, 5, 2)}) {
            List<Draw> draws = WindPressureLiftFlowRibbon.build(shape, camera, X);
            check(draws.size() == 44, "all four packets and independent arcs retained");
            for (int packet = 0; packet < 4; packet++) for (int sample = 0; sample < 7; sample++) {
                Draw a = draws.get(packet * 8 + sample), b = draws.get(packet * 8 + sample + 1);
                check(a.sideTo() == b.sideFrom(), "adjacent chords reuse exact same side object");
                check(a.segment().b() == b.segment().a(), "adjacent chords reuse exact same relative sample");
                for (double scale : PASS_SCALES) {
                    check(offset(a, true, true, scale).equals(offset(b, false, true, scale)), "positive expanded join has exactly zero mismatch");
                    check(offset(a, true, false, scale).equals(offset(b, false, false, scale)), "negative expanded join has exactly zero mismatch");
                }
            }
            for (Draw draw : draws) {
                check(Math.abs(dot(draw.sideFrom(), draw.sideFrom()) - 1) < 1e-14
                        && Math.abs(dot(draw.sideTo(), draw.sideTo()) - 1) < 1e-14, "sides stay unit length without miter enlargement");
                check(dot(draw.sideFrom(), draw.sideTo()) >= -1e-15, "side sign stays consistent along packet");
            }
        }
        Point camera = new Point(8, 2, -8), forward = unit(sub(new Point(0, 2, 0), camera));
        List<Draw> draws = WindPressureLiftFlowRibbon.build(shape, camera, X);
        for (double scale : PASS_SCALES)
            for (int packet = 0; packet < 4; packet++) for (int sample = 0; sample < 7; sample++) {
                Quad a = WindPressureLiftFlowRibbon.clip(draws.get(packet * 8 + sample), forward, scale);
                Quad b = WindPressureLiftFlowRibbon.clip(draws.get(packet * 8 + sample + 1), forward, scale);
                check(a != null && b != null && a.bMinus().equals(b.aMinus()) && a.bPlus().equals(b.aPlus()),
                        "final emitted quad corners match exactly at visible joins");
            }
        try { draws.clear(); throw new AssertionError("mutable draw list"); }
        catch (UnsupportedOperationException expected) { checks++; }
    }

    private static void expandedBounds() {
        Point[] cameras = {new Point(1.8, 1.62, 0), new Point(0, 1.62, 0), new Point(8, 2, -8),
                new Point(-7, 5, 2), new Point(.01, 5.8, .01), new Point(0, -.1, 0)};
        Point[] forwards = {X, Y, Z, unit(new Point(1, -.4, -1))};
        int emitted = 0;
        for (Mode mode : Mode.values()) for (int age = 0; age < 56; age += 2) {
            Shape source = shape(ZERO, mode, age, .25);
            for (Point camera : cameras) {
                List<Draw> draws = WindPressureLiftFlowRibbon.build(source, camera, X);
                check(draws.size() == 44, "valid off-axis view produces complete immutable draw list");
                for (Draw draw : draws) for (double scale : PASS_SCALES) {
                    for (boolean end : new boolean[]{false, true}) for (boolean positive : new boolean[]{false, true})
                        bounds(offset(draw, end, positive, scale), camera, ZERO);
                    for (Point forward : forwards) {
                        Quad q = WindPressureLiftFlowRibbon.clip(draw, forward, scale);
                        if (q == null) continue;
                        emitted++;
                        for (Point p : corners(q)) {
                            bounds(p, camera, ZERO);
                            check(dot(p, forward) >= WindPressureLiftMesh.NEAR_PLANE - 1e-12, "actual clipped corner stays beyond near plane");
                        }
                    }
                }
            }
        }
        check(emitted > 10_000, "coverage includes visible clipped quads in many views");
    }

    private static void cameraOnCurveAndFallback() {
        Shape source = shape(ZERO, Mode.SIDE, 26, .25);
        for (int i = 0; i < 32; i++) {
            Point camera = source.segments().get(i).a();
            for (Point fallback : new Point[]{X, Y, Z}) {
                var draws = WindPressureLiftFlowRibbon.build(source, camera, fallback);
                check(draws.size() == 44, "camera exactly on curve keeps finite geometry");
                for (Draw draw : draws) {
                    check(draw.sideFrom().finite() && draw.sideTo().finite(), "camera-on-sample sides finite");
                    bounds(offset(draw, false, true, 1), camera, ZERO);
                    bounds(offset(draw, true, false, 1), camera, ZERO);
                }
            }
        }
        // Force a camera-aligned straight tangent and a parallel fallback, requiring the least-aligned axis.
        var segments = new ArrayList<Segment>();
        for (int packet = 0; packet < 4; packet++) for (int i = 0; i < 8; i++)
            segments.add(new Segment(new Point(0, i + 1, 0), new Point(0, i + 2, 0), .2, .2, 100, 100, i / 8.0, (i + 1) / 8.0));
        segments.addAll(source.segments().subList(32, 44));
        var fallbackDraws = WindPressureLiftFlowRibbon.build(new Shape(segments), ZERO, Y);
        check(fallbackDraws.size() == 44, "least-aligned-axis fallback handles collinear eye tangent and fallback");
        for (int i = 0; i < 32; i++) check(Math.abs(dot(fallbackDraws.get(i).sideFrom(), Y)) < 1e-15,
                "fallback side stays perpendicular to tangent");
    }

    private static void clipping() {
        var source = new Segment(new Point(0, 0, -.2), new Point(0, 0, 1), .1, .3, 20, 120, .25, .75);
        var draw = new Draw(source, X, Y);
        Quad q = WindPressureLiftFlowRibbon.clip(draw, Z, 1);
        double t = (.08 + .15 + .2) / 1.2;
        check(q != null && Math.abs(q.segment().a().z() - .23) < 1e-15, "clip center uses conservative maximum half-width");
        Point expectedPlus = lerp(offset(draw, false, true, 1), offset(draw, true, true, 1), t);
        Point expectedMinus = lerp(offset(draw, false, false, 1), offset(draw, true, false, 1), t);
        check(q.aPlus().distanceSquared(expectedPlus) < 1e-29 && q.aMinus().distanceSquared(expectedMinus) < 1e-29,
                "cut interpolates actual expanded corners rather than normalized interpolated side");
        double cutOffset = Math.sqrt(q.aPlus().distanceSquared(q.segment().a()));
        check(cutOffset < q.segment().widthFrom() * .5 - .01, "interpolated shoulder remains shorter instead of expanding at cut");
        check(q.segment().alphaFrom() == Math.round(20 + 100 * t)
                && Math.abs(q.segment().uFrom() - (.25 + .5 * t)) < 1e-15, "cut interpolates alpha and U");
        check(q.bPlus().equals(offset(draw, true, true, 1)) && q.bMinus().equals(offset(draw, true, false, 1)), "uncut endpoint corners retained");
        var reverse = new Draw(new Segment(source.b(), source.a(), .3, .1, 120, 20, .75, .25), Y, X);
        Quad reversed = WindPressureLiftFlowRibbon.clip(reverse, Z, 1);
        check(reversed != null && reversed.bPlus().distanceSquared(q.aPlus()) < 1e-29
                && reversed.bMinus().distanceSquared(q.aMinus()) < 1e-29, "reverse crossing preserves same cut corners");
        var slanted = new Draw(source, unit(new Point(1, 0, 1)), unit(new Point(0, 1, -1)));
        for (double scale : PASS_SCALES) {
            Quad slant = WindPressureLiftFlowRibbon.clip(slanted, Z, scale);
            check(slant != null, "slanted near clip remains renderable");
            for (Point p : corners(slant)) check(p.z() >= .08 - 1e-15, "side offsets cannot cross conservative near plane");
        }
        check(WindPressureLiftFlowRibbon.clip(draw, new Point(0, 0, -1), 1) == null, "wholly hidden center span rejected with shoulder allowance");
    }

    private static void translationAndAdmission() {
        Point camera = new Point(1.8, 1.62, 0), shift = new Point(29_999_990, 4000, -29_999_990);
        var origin = WindPressureLiftFlowRibbon.build(shape(ZERO, Mode.SIDE, 17, .375), camera, X);
        var far = WindPressureLiftFlowRibbon.build(shape(shift, Mode.SIDE, 17, .375), add(camera, shift), X);
        check(origin.size() == 44 && far.size() == 44, "large world translations retain draw count");
        for (int i = 0; i < origin.size(); i++) {
            for (boolean end : new boolean[]{false, true}) for (boolean plus : new boolean[]{false, true})
                check(offset(origin.get(i), end, plus, 1).distanceSquared(offset(far.get(i), end, plus, 1)) < 1e-14,
                        "camera-relative double corners are translation invariant at thirty million coordinates");
            bounds(offset(far.get(i), false, true, 1), add(camera, shift), shift);
        }
        Shape source = shape(ZERO, Mode.UP, 1, 0);
        check(WindPressureLiftFlowRibbon.build(null, camera, X).isEmpty(), "null shape rejected");
        check(WindPressureLiftFlowRibbon.build(new Shape(List.of()), camera, X).isEmpty(), "empty flow remains empty");
        check(WindPressureLiftFlowRibbon.build(source, new Point(Double.NaN, 0, 0), X).isEmpty(), "NaN camera rejected");
        check(WindPressureLiftFlowRibbon.build(source, camera, ZERO).isEmpty(), "nonunit fallback rejected");
        var changed = new ArrayList<>(source.segments());
        Segment old = changed.get(1);
        changed.set(1, new Segment(add(old.a(), new Point(.001, 0, 0)), old.b(), old.widthFrom(), old.widthTo(), old.alphaFrom(), old.alphaTo(), old.uFrom(), old.uTo()));
        check(WindPressureLiftFlowRibbon.build(new Shape(changed), camera, X).isEmpty(), "disconnected packet fails closed");
        Draw valid = origin.getFirst();
        for (double scale : new double[]{Double.NaN, 0, -.1, Math.nextUp(1.0)})
            check(WindPressureLiftFlowRibbon.clip(valid, Z, scale) == null, "invalid or enlarging pass scale rejected");
        check(WindPressureLiftFlowRibbon.clip(valid, ZERO, 1) == null, "nonunit forward rejected");
        check(WindPressureLiftFlowRibbon.clip(new Draw(valid.segment(), new Point(Double.NaN, 0, 0), X), Z, 1) == null,
                "NaN shoulder rejected");
    }

    public static void main(String[] args) {
        joinsAndWitnesses(); expandedBounds(); cameraOnCurveAndFallback(); clipping(); translationAndAdmission();
        System.out.println("PASS WindPressureLiftFlowRibbonRegressionTest: " + checks + " checks");
    }
}
