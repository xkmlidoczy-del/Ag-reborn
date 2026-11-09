package cn.academy.port.client;

import cn.academy.port.client.WindPressureLiftFlowMesh.Segment;
import cn.academy.port.client.WindPressureLiftFlowMesh.Shape;
import cn.academy.port.client.WindPressureLiftMesh.Point;
import cn.academy.port.client.WindPressureLiftState.Controller;
import cn.academy.port.client.WindPressureLiftState.Frame;
import cn.academy.port.client.WindPressureLiftState.Identity;
import cn.academy.port.highpower.core.WindPressureLiftRules.Mode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Engine-free geometry evidence; this does not assert rendered-image or gameplay acceptance. */
public final class WindPressureLiftFlowMeshRegressionTest {
    private static int checks;
    private static final Point BASE = new Point(0, 64, 0), NORTH = new Point(0, 0, 1);

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }

    private static Frame frame(int owner, Point base, Mode mode, Point direction, boolean terminal) {
        return new Frame(new Identity(owner, new UUID(17, owner), 1, 10 + owner, 2), 1,
                base, mode, direction, 10, 14, terminal);
    }

    private static Controller controller(Point base, Mode mode, Point direction, int age, int quiet) {
        return new Controller(frame(1, base, mode, direction, false), true, age, quiet);
    }

    private static Shape shape(Mode mode, Point direction, int age, double partial) {
        return WindPressureLiftFlowMesh.shape(controller(BASE, mode, direction, age, 0), partial);
    }

    private static void empty(Controller controller, double partial, String label) {
        check(WindPressureLiftFlowMesh.shape(controller, partial).vertexCount() == 0, label);
    }

    private static void checkEndpoint(Point point, Point anchor, double width, int alpha, boolean ribbon) {
        Point p = WindPressureLiftMesh.relative(point, anchor);
        double half = width * .5;
        check(point.finite(), "finite endpoint");
        // Conservative sphere shoulders cover every camera-facing ribbon orientation, not just centerlines.
        check(Math.hypot(p.x(), p.z()) + half <= 3 + 1e-8, "entire soft shoulder inside true six metre diameter");
        check(p.y() - half >= -1e-8 && p.y() + half <= 6 + 1e-8, "entire soft shoulder inside true six metre height");
        check(width >= 0 && width <= .54 && alpha >= 0 && alpha <= 208, "finite bounded taper and opacity");
        if (ribbon) check(Math.hypot(p.x(), p.z()) - half > .90, "ribbons leave a clear central opening");
    }

    private static void checkBounds(Shape shape, Point base) {
        check(shape.segments().size() == 44 && shape.vertexCount() == 352, "four packets plus twelve open arcs, both passes counted");
        for (int i = 0; i < shape.segments().size(); i++) {
            Segment s = shape.segments().get(i);
            checkEndpoint(s.a(), base, s.widthFrom(), s.alphaFrom(), i < 32);
            checkEndpoint(s.b(), base, s.widthTo(), s.alphaTo(), i < 32);
            check(s.a().distanceSquared(s.b()) < .75 * .75, "no wraparound or oversized chord");
            check(s.uFrom() >= 0 && s.uTo() <= 1 && s.uFrom() < s.uTo(), "finite increasing texture coordinates");
        }
    }

    private static void admission() {
        var valid = controller(BASE, Mode.UP, NORTH, 0, 0);
        empty(null, 0, "no controller no flow");
        empty(new Controller(null, true, 0, 0), 0, "null receipt refused");
        empty(new Controller(frame(1, BASE, Mode.UP, NORTH, true), true, 0, 0), 0, "END has no residual geometry");
        empty(controller(BASE, Mode.UP, NORTH, -1, 0), 0, "negative controller age refused");
        empty(controller(BASE, Mode.UP, NORTH, 0, -1), 0, "negative quiet counter refused");
        empty(controller(BASE, Mode.UP, NORTH, 0, 4), 0, "exact quiet timeout has no flow");
        empty(controller(BASE, Mode.UP, NORTH, 0, Integer.MAX_VALUE), 0, "stale quiet timeout has no flow");
        check(WindPressureLiftFlowMesh.shape(controller(BASE, Mode.UP, NORTH, 0, 3), 0).vertexCount() == 352,
                "last current receipt tick remains visible");
        for (double partial : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                -Double.MIN_VALUE, 1, Math.nextUp(1.0)}) empty(valid, partial, "invalid partial refused");
        check(WindPressureLiftFlowMesh.shape(valid, -0.0).vertexCount() == 352, "signed zero partial accepted");
        check(WindPressureLiftFlowMesh.shape(valid, Math.nextDown(1.0)).vertexCount() == 352, "last legal partial accepted");
        for (Point invalid : new Point[]{new Point(Double.NaN, 64, 0), new Point(0, Double.POSITIVE_INFINITY, 0),
                new Point(Math.nextUp(30_000_000.0), 64, 0), new Point(0, Math.nextUp(4096.0), 0),
                new Point(0, 64, Math.nextDown(-30_000_000.0))})
            empty(controller(invalid, Mode.UP, NORTH, 0, 0), 0, "invalid anchor uses original validator");
        for (Point invalid : new Point[]{null, new Point(0, 0, 0), new Point(0, .01, 1), new Point(Double.NaN, 0, 1)})
            empty(controller(BASE, Mode.SIDE, invalid, 0, 0), 0, "invalid frozen direction refused");
        empty(controller(BASE, null, NORTH, 0, 0), 0, "missing mode refused");
        var missingIdentity = new Frame(null, 1, BASE, Mode.UP, NORTH, 10, 14, false);
        empty(new Controller(missingIdentity, true, 0, 0), 0, "missing authenticated identity refused");
        var badExpiry = new Frame(valid.id(), 1, BASE, Mode.UP, NORTH, 10, 15, false);
        empty(new Controller(badExpiry, true, 0, 0), 0, "invalid receipt window refused");
    }

    private static void shapeAndMotion() {
        Shape first = shape(Mode.UP, NORTH, 0, .25), later = shape(Mode.UP, NORTH, 0, .75);
        checkBounds(first, BASE);
        check(first.equals(shape(Mode.UP, NORTH, 0, .25)), "same controller and partial give exact deterministic geometry");
        var copied = new ArrayList<>(first.segments());
        var immutable = new Shape(copied); copied.clear();
        check(immutable.equals(first), "shape owns an immutable copy");
        try { immutable.segments().clear(); throw new AssertionError("mutable shape"); }
        catch (UnsupportedOperationException expected) { checks++; }
        for (int ribbon = 0; ribbon < 4; ribbon++) {
            int offset = ribbon * 8;
            Segment start = first.segments().get(offset), end = first.segments().get(offset + 7);
            check(start.widthFrom() == 0 && start.alphaFrom() == 0 && end.widthTo() == 0 && end.alphaTo() == 0,
                    "each packet tapers exactly to transparent zero-width tips");
            check(first.segments().get(offset + 3).widthTo() >= .42
                    && first.segments().get(offset + 3).widthTo() * WindPressureLiftFlowMesh.CORE_WIDTH_SCALE <= .152,
                    "soft shoulder and narrow core remain bounded");
            check(later.segments().get(offset).a().y() > start.a().y(), "phase moves each packet upward");
            Point midpoint = first.segments().get(offset + 3).b();
            double chordX = (start.a().x() + end.b().x()) * .5, chordZ = (start.a().z() + end.b().z()) * .5;
            check(Math.hypot(midpoint.x() - chordX, midpoint.z() - chordZ) > .08, "packet has actual curved geometry");
            check(end.b().y() - start.a().y() < 3, "packet cannot become a full-height pillar");
            for (int step = 0; step < 8; step++) {
                Segment s = first.segments().get(offset + step);
                check(s.b().y() > s.a().y(), "UP chord rises");
                check(s.uFrom() == step / 8.0 && s.uTo() == (step + 1) / 8.0, "U spans whole packet without chord resets");
                if (step < 7) {
                    Segment next = first.segments().get(offset + step + 1);
                    check(s.b().equals(next.a()) && s.widthTo() == next.widthFrom()
                            && s.alphaTo() == next.alphaFrom() && s.uTo() == next.uFrom(), "adjacent chords join with continuous taper and U");
                }
            }
            if (ribbon > 0) check(start.a().y() != first.segments().get(offset - 8).a().y(), "packets have staggered phases");
        }
        for (int arc = 32; arc < 44; arc++) {
            Segment s = first.segments().get(arc);
            check(s.a().y() < 64.1 && s.b().y() < 64.1 && s.alphaFrom() == 100 && s.alphaTo() == 35,
                    "floor arcs remain low with bounded alpha taper");
            check(s.widthFrom() == .07 && s.widthTo() == .035, "open floor shoulders use the bounded width taper");
            Segment next = first.segments().get(arc == 43 ? 32 : arc + 1);
            check(s.b().distanceSquared(next.a()) > .1, "floor boundary always has open gaps");
        }
        for (Mode mode : Mode.values()) for (Point direction : new Point[]{NORTH, new Point(1, 0, 0),
                new Point(-1, 0, 0), new Point(Math.sqrt(.5), 0, -Math.sqrt(.5))})
            for (int age = 0; age < 56; age++) for (double partial : new double[]{0, .5, Math.nextDown(1.0)})
                checkBounds(shape(mode, direction, age, partial), BASE);
        check(shape(Mode.UP, NORTH, 13, .75).equals(shape(Mode.UP, NORTH, 69, .75)), "whole-cycle wrap is exactly repeatable");
        Shape floorBeforeWrap = shape(Mode.UP, NORTH, 55, Math.nextDown(1.0)), floorAfterWrap = shape(Mode.UP, NORTH, 56, 0);
        for (int arc = 0; arc < 12; arc++) {
            Segment before = floorBeforeWrap.segments().get(32 + arc);
            Segment after = floorAfterWrap.segments().get(32 + (arc + 3) % 12);
            check(before.a().distanceSquared(after.a()) < 1e-27 && before.b().distanceSquared(after.b()) < 1e-27,
                    "rotating open-arc pattern is spatially continuous across phase wrap");
            check(first.segments().get(32 + arc).a().distanceSquared(later.segments().get(32 + arc).a()) > 0,
                    "floor arcs move using the receipt age and partial clock");
        }
        check(shape(Mode.UP, NORTH, Integer.MAX_VALUE, .25)
                .equals(shape(Mode.UP, NORTH, Integer.MAX_VALUE % 56, .25)), "large age preserves fractional phase");
        for (double partial : new double[]{.079999, .08, .080001}) {
            Shape wrap = shape(Mode.UP, NORTH, 52, partial);
            for (int i = 0; i < 8; i++) check(wrap.segments().get(i).alphaFrom() == 0
                    && wrap.segments().get(i).alphaTo() == 0, "packet is invisible across its position reset");
        }
    }

    private static void sideAndLargeCoordinates() {
        for (Point direction : new Point[]{NORTH, new Point(1, 0, 0), new Point(-Math.sqrt(.5), 0, Math.sqrt(.5))}) {
            var up = shape(Mode.UP, direction, 10, .3); var side = shape(Mode.SIDE, direction, 10, .3);
            for (int i = 0; i < 32; i++) {
                Point a = up.segments().get(i).b(), b = side.segments().get(i).b();
                double dx = b.x() - a.x(), dz = b.z() - a.z();
                check(a.y() == b.y(), "SIDE does not depict a downward or landing promise");
                check(dx * direction.x() + dz * direction.z() > 0, "SIDE bows along frozen direction");
                check(Math.abs(dx * direction.z() - dz * direction.x()) < 1e-14, "SIDE bow has no perpendicular heading change");
            }
        }
        check(shape(Mode.UP, NORTH, 3, .7).equals(shape(Mode.UP, new Point(1, 0, 0), 3, .7)), "UP does not follow camera heading");
        for (Point base : new Point[]{new Point(30_000_000, 4096, -30_000_000),
                new Point(-30_000_000, -4096, 30_000_000), new Point(29_999_999.875, 64, -29_999_999.75)}) {
            Shape far = WindPressureLiftFlowMesh.shape(controller(base, Mode.SIDE, NORTH, 25, 0), .375);
            Shape origin = WindPressureLiftFlowMesh.shape(controller(new Point(0, 0, 0), Mode.SIDE, NORTH, 25, 0), .375);
            checkBounds(far, base);
            for (int i = 0; i < far.segments().size(); i++) {
                Point relative = WindPressureLiftMesh.relative(far.segments().get(i).a(), base);
                check(relative.distanceSquared(origin.segments().get(i).a()) < 1e-14, "far geometry retains double-precision local shape");
            }
        }
    }

    private static void clipping() {
        var crossing = new Segment(new Point(0, 0, -1), new Point(0, 0, 1), .10, .30, 20, 120, .25, .75);
        Segment clipped = WindPressureLiftFlowMesh.clipNear(crossing, NORTH, .15);
        double t = (1 + WindPressureLiftMesh.NEAR_PLANE + .15) / 2;
        check(clipped != null && Math.abs(clipped.a().z() - .23) < 1e-15, "near plane includes soft shoulder");
        check(Math.abs(clipped.widthFrom() - (.10 + .20 * t)) < 1e-15 && clipped.alphaFrom() == Math.round(20 + 100 * t)
                && Math.abs(clipped.uFrom() - (.25 + .50 * t)) < 1e-15, "clip interpolates width alpha and continuous U");
        check(clipped.b().equals(crossing.b()) && clipped.widthTo() == .30 && clipped.uTo() == .75, "visible endpoint retained");
        Segment reverse = new Segment(crossing.b(), crossing.a(), .30, .10, 120, 20, .75, .25);
        Segment back = WindPressureLiftFlowMesh.clipNear(reverse, NORTH, .15);
        check(back != null && back.b().distanceSquared(clipped.a()) < 1e-28
                && Math.abs(back.widthTo() - clipped.widthFrom()) < 1e-15 && back.alphaTo() == clipped.alphaFrom(),
                "reverse crossing retains interpolated taper");
        var plane = new Segment(new Point(0, 0, .23), new Point(0, 0, 1), .1, .1, 80, 80, 0, 1);
        check(WindPressureLiftFlowMesh.clipNear(plane, NORTH, .15) == plane, "exact near boundary accepted unchanged");
        check(WindPressureLiftFlowMesh.clipNear(crossing, new Point(0, 0, 0), .15) == null, "nonunit camera refused");
        check(WindPressureLiftFlowMesh.clipNear(crossing, new Point(Double.NaN, 0, 1), .15) == null, "NaN camera refused");
        check(WindPressureLiftFlowMesh.clipNear(crossing, NORTH, Double.NaN) == null, "NaN clipping width refused");
        check(WindPressureLiftFlowMesh.clipNear(crossing, NORTH, -.01) == null, "negative clipping width refused");
        check(WindPressureLiftFlowMesh.clipNear(new Segment(new Point(0, 0, -.2), new Point(0, 0, -.1), .1, .1, 80, 80, 0, 1),
                NORTH, .1) == null, "wholly hidden segment refused");
        check(WindPressureLiftFlowMesh.clipNear(new Segment(crossing.a(), crossing.b(), Double.NaN, .1, 80, 80, 0, 1),
                NORTH, .1) == null, "NaN taper refused");
        check(WindPressureLiftFlowMesh.clipNear(new Segment(crossing.a(), crossing.b(), .1, .1, 80, 80, 0, Double.NaN),
                NORTH, .1) == null, "NaN texture coordinate refused");
    }

    private static void lifecycleAndBudget() {
        var state = new WindPressureLiftState();
        for (int owner = 1; owner <= 4; owner++) check(state.accept(frame(owner, BASE, Mode.UP, NORTH, false), owner == 1),
                "four authenticated controllers admitted");
        check(!state.accept(frame(5, BASE, Mode.UP, NORTH, false), false), "fifth controller denied by original state");
        var budget = new WindPressureLiftMesh.Budget();
        for (var controller : state.controllers()) {
            Shape before = WindPressureLiftFlowMesh.shape(controller, .4);
            check(before.vertexCount() <= 512 && budget.take(before.vertexCount()), "whole controller stays under two-pass cap");
        }
        check(budget.used() == 1408 && budget.used() <= 2048, "four-controller global budget includes both passes");
        var hard = new WindPressureLiftMesh.Budget();
        for (int i = 0; i < 4; i++) check(hard.take(512), "exact hard budget admitted");
        check(!hard.take(1) && !hard.take(Integer.MAX_VALUE), "hard global cap cannot overflow");
        Controller active = state.controllers().getFirst();
        Shape beforePause = WindPressureLiftFlowMesh.shape(active, .4);
        state.tick(true, candidate -> true);
        check(beforePause.equals(WindPressureLiftFlowMesh.shape(state.current(active.id().owner()), .4)), "paused state freezes geometry phase");
        check(active.age() == 0 && active.quiet() == 0, "pure geometry never mutates receipt age or quiet");
        for (int i = 0; i < 4; i++) state.tick(false, candidate -> true);
        for (var c : state.controllers()) empty(c, .4, "quiet timeout hides every flow without extending receipt");
        check(state.accept(frame(1, BASE, Mode.UP, NORTH, true), true), "terminal receipt accepted");
        empty(state.current(active.id().owner()), .4, "terminal state removes flow immediately");
        state.clear(); check(state.size() == 0, "existing retirement owns lifecycle");
        check(WindPressureLiftFlowMesh.MAX_CONTEXTS == 4 && WindPressureLiftFlowMesh.MAX_CONTEXT_VERTICES == 512
                && WindPressureLiftFlowMesh.MAX_VERTICES == 2048 && WindPressureLiftFlowMesh.MAX_PARTICLES == 0,
                "original controller and hard vertex limits with zero particles");
    }

    public static void main(String[] args) {
        admission(); shapeAndMotion(); sideAndLargeCoordinates(); clipping(); lifecycleAndBudget();
        System.out.println("PASS WindPressureLiftFlowMeshRegressionTest: " + checks + " checks");
    }
}
