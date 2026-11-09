package cn.academy.port.client;

import cn.academy.port.client.MultidimensionalLandingCueGeometry.Phase;
import cn.academy.port.client.MultidimensionalLandingCueGeometry.Quad;
import cn.academy.port.client.MultidimensionalLandingCueGeometry.Role;
import cn.academy.port.client.MultidimensionalLandingCueGeometry.Shape;
import cn.academy.port.client.MultidimensionalLandingCueGeometry.Stroke;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Runs the production helper on Java 21 without Minecraft, native rendering or replacement stubs. */
public final class MultidimensionalLandingCueGeometryRegressionTest {
    private static final Point ZERO = new Point(0, 0, 0), X = new Point(1, 0, 0), Y = new Point(0, 1, 0), Z = new Point(0, 0, 1);
    private static final Point AT = new Point(0, 0, 6);
    private static int checks;

    public static void main(String[] args) {
        silhouettesAndLocalMotion();
        exactLifetimeAndPause();
        finiteWidthsRadiusAndBudget();
        atomicNearPlaneRefusal();
        tangentAndVariedViews();
        worldBorderPrecision();
        invalidInputsAndImmutability();
        System.out.println("PASS " + checks + " NEW351 optional landing cue geometry checks; max 144 vertices/cue, 2048 shared vertices, 0 particles");
    }

    private static void silhouettesAndLocalMotion() {
        Shape source = shape(false, 0), contact = shape(true, 0);
        check(source.strokes().size() == 36 && contact.strokes().size() == 36, "exact bounded three-layer contours");
        check(!source.equals(contact), "actual publication and accepted contact have distinct silhouettes");
        check(spanY(source) > .5 && spanY(contact) < .2, "open upright panels differ from compressed fragments at first sight");
        check(averageRadius(shape(false, 3.5)) < averageRadius(source) * .75, "source panels converge locally");
        check(averageRadius(shape(true, 3.5)) > averageRadius(contact) * 1.5, "contact fragments spread locally");
        topology(source, 4, 3);
        topology(contact, 6, 2);
        for (boolean acceptedContact : new boolean[]{false, true}) {
            Shape first = shape(acceptedContact, 1.25), later = shape(acceptedContact, 1.75);
            check(!first.equals(later), "fractional tick changes geometry smoothly");
            check(first.equals(shape(acceptedContact, 1.25)), "same receipt and supplied time produce deterministic geometry");
            int[] alpha = {peak(shape(acceptedContact, 1)), peak(shape(acceptedContact, 4.5)), peak(shape(acceptedContact, 6.5))};
            check(alpha[0] >= 220 && alpha[0] > alpha[1] && alpha[1] > alpha[2], "bright early core fades before original expiry");
        }
    }

    private static void topology(Shape shape, int components, int segments) {
        List<Stroke> core = shape.strokes().stream().filter(s -> s.role() == Role.CORE).toList();
        for (int group = 0; group < components; group++) {
            for (int i = 1; i < segments; i++)
                check(core.get(group * segments + i - 1).to().equals(core.get(group * segments + i).from()), "each open contour joins locally");
            Point start = core.get(group * segments).from(), end = core.get((group + 1) * segments - 1).to();
            check(!start.equals(end), "no closed panel or ring perimeter");
            for (int other = group + 1; other < components; other++) {
                Point next = core.get(other * segments).from();
                check(!end.equals(next), "separate fragments do not form a connected force ring");
            }
        }
    }

    private static void exactLifetimeAndPause() {
        check(MultidimensionalLandingCueGeometry.LIFE_TICKS == 7, "original seven-tick lifetime");
        check(MultidimensionalLandingCueGeometry.phase(0) == Phase.EARLY, "early phase begins on receipt");
        check(MultidimensionalLandingCueGeometry.phase(2) == Phase.MID, "middle phase has explicit boundary");
        check(MultidimensionalLandingCueGeometry.phase(4.5) == Phase.FADE, "fade occurs within original life");
        for (double age : new double[]{-1, 7, 8, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            check(shape(false, age).strokes().isEmpty(), "invalid or expired source emits nothing");
            check(shape(true, age).strokes().isEmpty(), "invalid or expired contact emits nothing");
            check(MultidimensionalLandingCueGeometry.phase(age) == Phase.EXPIRED, "invalid age fails closed");
        }
        for (int tick = 0; tick < 7; tick++) {
            check(MultidimensionalLandingCueGeometry.shape(AT, false, tick, .25, false).equals(shape(false, tick + .25)), "render partial is used before expiry");
            check(MultidimensionalLandingCueGeometry.shape(AT, true, tick, .1, true).equals(
                    MultidimensionalLandingCueGeometry.shape(AT, true, tick, .9, true)), "paused integer clock ignores moving render partial");
        }
        check(MultidimensionalLandingCueGeometry.age(2, -5, false) == 2, "negative partial clamps to current tick");
        check(MultidimensionalLandingCueGeometry.age(2, 8, false) <= 3, "large partial does not skip ticks");
        check(Double.isNaN(MultidimensionalLandingCueGeometry.age(7, 0, false)), "partial cannot revive expired cue");
        for (double boundary : new double[]{.8, 2, 3.5, 4.5}) {
            Shape before = shape(false, boundary - 1e-5), after = shape(false, boundary + 1e-5);
            for (int i = 0; i < before.strokes().size(); i++)
                check(distance(before.strokes().get(i).from(), after.strokes().get(i).from()) < 1e-4,
                        "phase boundary has no spatial jump");
        }
    }

    private static void finiteWidthsRadiusAndBudget() {
        check(MultidimensionalLandingCueGeometry.MAX_VERTICES == 144 && MultidimensionalLandingCueGeometry.MAX_VERTICES <= 256, "per-cue vertex ceiling");
        check(MultidimensionalLandingCueGeometry.MAX_SCENE_VERTICES == 2048, "owner's shared budget constant");
        check(MultidimensionalLandingCueGeometry.MAX_PARTICLES == 0, "no added particles");
        for (boolean contact : new boolean[]{false, true}) for (int tick = 0; tick < 280; tick++) {
            Shape shape = shape(contact, tick / 40.0);
            check(shape.vertexCount() <= MultidimensionalLandingCueGeometry.MAX_VERTICES, "all age samples respect vertex ceiling");
            for (Stroke stroke : shape.strokes()) {
                check(stroke.from().finite() && stroke.to().finite(), "finite bounded world endpoints");
                check(distance(stroke.from(), stroke.to()) > 1e-5, "no zero-length generated strokes");
                check(distance(stroke.from(), AT) + stroke.halfWidth() <= .7, "full shoulder fits local .7m radius at start");
                check(distance(stroke.to(), AT) + stroke.halfWidth() <= .7, "full shoulder fits local .7m radius at end");
                check(stroke.alpha() >= 0 && stroke.alpha() < 255, "no opaque wall material");
                int red = stroke.rgb() >> 16 & 255, green = stroke.rgb() >> 8 & 255, blue = stroke.rgb() & 255;
                check(Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue)) <= 8, "restrained gray-white palette");
                if (stroke.role() == Role.CORE) check(stroke.halfWidth() * 2 >= .044 - 1e-12
                        && stroke.halfWidth() * 2 <= .050 + 1e-12, "readable .044-.050m physical core width");
            }
            List<Quad> quads = MultidimensionalLandingCueGeometry.prepare(shape, ZERO, Z);
            check(quads.size() * 4 == shape.vertexCount(), "successful prepare retains the complete silhouette");
            verifyQuads(quads, ZERO, AT, Z);
        }
    }

    private static void atomicNearPlaneRefusal() {
        Stroke visible = stroke(new Point(-.2, 0, 1), new Point(.2, 0, 1), .03);
        double edge = MultidimensionalLandingCueGeometry.NEAR_PLANE + .03;
        for (Stroke refused : List.of(stroke(new Point(-.2, 0, edge), new Point(.2, 0, edge), .03),
                stroke(new Point(0, 0, -1), new Point(0, 0, 1), .03),
                stroke(new Point(0, 0, -2), new Point(0, 1, -1), .03))) {
            check(MultidimensionalLandingCueGeometry.prepare(new Shape(List.of(visible, refused), Phase.MID), ZERO, Z).isEmpty(),
                    "one near-plane crossing/refusal rejects whole optional batch before any output");
        }
        Stroke above = stroke(new Point(-.2, 0, edge + 1e-6), new Point(.2, 0, edge + 1e-6), .03);
        check(MultidimensionalLandingCueGeometry.prepare(new Shape(List.of(above), Phase.MID), ZERO, Z).size() == 1,
                "full width strictly outside near plane is admitted");
        check(MultidimensionalLandingCueGeometry.prepare(shape(false, 1), ZERO, scale(Z, -1)).isEmpty(), "actual camera forward controls visibility");
    }

    private static void tangentAndVariedViews() {
        for (Point direction : List.of(X, Y, Z, scale(X, -1), scale(Y, -1), scale(Z, -1))) {
            Stroke tangent = stroke(scale(direction, 2), scale(direction, 3), .025);
            var quads = MultidimensionalLandingCueGeometry.prepare(new Shape(List.of(tangent), Phase.EARLY), ZERO, direction);
            check(quads.size() == 1, "tangent camera gets deterministic finite basis");
            check(distance(quads.getFirst().a(), quads.getFirst().b()) > .049, "tangent width never collapses to zero");
            check(quads.equals(MultidimensionalLandingCueGeometry.prepare(new Shape(List.of(tangent), Phase.EARLY), ZERO, direction)), "tangent fallback is stable");
        }
        Random random = new Random(35157);
        for (int i = 0; i < 400; i++) {
            Point forward = unit(new Point(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1));
            Point camera = new Point(random.nextDouble() * 50, random.nextDouble() * 50, random.nextDouble() * 50);
            Point center = add(camera, scale(forward, 5));
            Shape shape = MultidimensionalLandingCueGeometry.shape(center, i % 2 == 0, random.nextDouble() * 7);
            List<Quad> quads = MultidimensionalLandingCueGeometry.prepare(shape, camera, scale(forward, 9));
            check(quads.size() == 36, "all valid yaw/pitch views retain full shape");
            verifyQuads(quads, camera, center, forward);
        }
    }

    private static void worldBorderPrecision() {
        Point at = new Point(.125, .25, 6.375);
        for (Point shift : List.of(new Point(29_999_990, 999_990, 29_999_990), new Point(-29_999_990, -999_990, -29_999_990))) {
            var base = MultidimensionalLandingCueGeometry.prepare(MultidimensionalLandingCueGeometry.shape(at, false, 1.5), ZERO, Z);
            var far = MultidimensionalLandingCueGeometry.prepare(MultidimensionalLandingCueGeometry.shape(add(at, shift), false, 1.5), shift, Z);
            check(base.size() == far.size() && !far.isEmpty(), "world-border cue retained");
            for (int i = 0; i < base.size(); i++) for (int v = 0; v < 4; v++) {
                Point a = vertices(base.get(i)).get(v), b = vertices(far.get(i)).get(v);
                check(distance(a, b) < 1e-8, "double camera subtraction preserves fractional world-border geometry");
                check(Math.abs((float)a.x() - (float)b.x()) < 1e-6, "only short relative vectors cross float boundary");
            }
        }
        check(MultidimensionalLandingCueGeometry.relative(new Point(30_000_000.125, 0, 0), new Point(30_000_000, 0, 0)).x() == .125,
                "world-border eighth-block offset survives double subtraction");
        check(!MultidimensionalLandingCueGeometry.shape(new Point(30_000_001, 1_000_000, -30_000_001), true, 2).strokes().isEmpty(), "valid receipt border inclusive");
        for (Point bad : List.of(new Point(30_000_001.1, 0, 0), new Point(0, 1_000_000.1, 0), new Point(0, 0, -30_000_001.1)))
            check(MultidimensionalLandingCueGeometry.shape(bad, false, 1).strokes().isEmpty(), "out-of-world receipt rejected before render conversion");
    }

    private static void invalidInputsAndImmutability() {
        check(MultidimensionalLandingCueGeometry.shape(null, false, 0).strokes().isEmpty(), "null point rejected");
        check(MultidimensionalLandingCueGeometry.prepare(null, ZERO, Z).isEmpty(), "null shape rejected");
        for (Point bad : new Point[]{null, ZERO, new Point(Double.NaN, 0, 1), new Point(0, Double.POSITIVE_INFINITY, 1)})
            check(MultidimensionalLandingCueGeometry.prepare(shape(false, 1), ZERO, bad).isEmpty(), "invalid/zero camera direction rejected");
        for (Point bad : new Point[]{null, new Point(Double.NaN, 0, 0), new Point(Double.MAX_VALUE, 0, 0)}) {
            check(MultidimensionalLandingCueGeometry.prepare(shape(false, 1), bad, Z).isEmpty(), "invalid camera rejected");
            check(MultidimensionalLandingCueGeometry.shape(bad, true, 1).strokes().isEmpty(), "invalid center rejected");
        }
        Stroke valid = stroke(new Point(0, 0, 2), new Point(0, 1, 2), .025);
        for (Stroke bad : List.of(stroke(AT, AT, .025), stroke(AT, add(AT, X), 0), stroke(AT, add(AT, X), Double.NaN),
                stroke(AT, add(AT, X), .061), new Stroke(AT, add(AT, X), .025, 0xFFFFFF, -1, Role.CORE),
                new Stroke(AT, add(AT, X), .025, 0xFFFFFF, 50, null)))
            check(MultidimensionalLandingCueGeometry.prepare(new Shape(List.of(valid, bad), Phase.MID), ZERO, Z).isEmpty(), "invalid later stroke rejects whole prepared batch");
        check(MultidimensionalLandingCueGeometry.prepare(new Shape(java.util.Collections.nCopies(37, valid), Phase.MID), ZERO, Z).isEmpty(), "over-budget shapes fail closed");
        check(MultidimensionalLandingCueGeometry.relative(new Point(Double.MAX_VALUE, 0, 0), new Point(-Double.MAX_VALUE, 0, 0)) == null, "overflowing relative delta rejected");
        var mutable = new ArrayList<Stroke>(); mutable.add(valid);
        Shape copied = new Shape(mutable, Phase.MID); mutable.clear();
        check(copied.strokes().size() == 1, "shape snapshots caller list");
        try { copied.strokes().clear(); throw new AssertionError("mutable shape"); } catch (UnsupportedOperationException expected) { checks++; }
        var quads = MultidimensionalLandingCueGeometry.prepare(copied, ZERO, Z);
        try { quads.clear(); throw new AssertionError("mutable prepared batch"); } catch (UnsupportedOperationException expected) { checks++; }
    }

    private static void verifyQuads(List<Quad> quads, Point camera, Point center, Point forward) {
        Point relative = MultidimensionalLandingCueGeometry.relative(center, camera);
        for (Quad quad : quads) {
            check(distance(quad.a(), quad.b()) > .04, "prepared quad retains physical width");
            for (Point vertex : vertices(quad)) {
                check(vertex.finite(), "finite prepared vertex");
                check(dot(vertex, forward) > MultidimensionalLandingCueGeometry.NEAR_PLANE, "all corners safely ahead of near plane");
                check(distance(vertex, relative) <= .7 + 1e-8, "all prepared corners stay inside local cue bound");
            }
        }
    }
    private static Shape shape(boolean contact, double age) { return MultidimensionalLandingCueGeometry.shape(AT, contact, age); }
    private static Stroke stroke(Point from, Point to, double width) { return new Stroke(from, to, width, 0xEEEEEE, 200, Role.CORE); }
    private static List<Point> vertices(Quad q) { return List.of(q.a(), q.b(), q.c(), q.d()); }
    private static double spanY(Shape shape) {
        return shape.strokes().stream().flatMap(s -> List.of(s.from(), s.to()).stream()).mapToDouble(Point::y).max().orElse(0)
                - shape.strokes().stream().flatMap(s -> List.of(s.from(), s.to()).stream()).mapToDouble(Point::y).min().orElse(0);
    }
    private static double averageRadius(Shape shape) {
        return shape.strokes().stream().flatMap(s -> List.of(s.from(), s.to()).stream()).mapToDouble(p -> distance(p, AT)).average().orElse(0);
    }
    private static int peak(Shape shape) { return shape.strokes().stream().mapToInt(Stroke::alpha).max().orElse(0); }
    private static double distance(Point a, Point b) { return Math.sqrt(a.distanceSquared(b)); }
    private static Point add(Point a, Point b) { return new Point(a.x() + b.x(), a.y() + b.y(), a.z() + b.z()); }
    private static Point scale(Point p, double x) { return new Point(p.x() * x, p.y() * x, p.z() * x); }
    private static double dot(Point a, Point b) { return a.x() * b.x() + a.y() * b.y() + a.z() * b.z(); }
    private static Point unit(Point p) { return scale(p, 1 / Math.sqrt(dot(p, p))); }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
