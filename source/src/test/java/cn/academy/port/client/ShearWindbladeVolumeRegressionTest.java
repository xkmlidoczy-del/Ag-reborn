package cn.academy.port.client;

import cn.academy.port.client.ShearWindbladeVolumeGeometry.Material;
import cn.academy.port.client.ShearWindbladeVolumeGeometry.Mesh;
import cn.academy.port.client.ShearWindbladeVolumeGeometry.Quad;
import cn.academy.port.highpower.core.ShearWindbladeGeometry;
import cn.academy.port.highpower.core.ShearWindbladeGeometry.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** CPU tests of production .62 geometry against actual accepted NEW421 receipts. */
public final class ShearWindbladeVolumeRegressionTest {
    private static final Point ZERO = new Point(0, 0, 0), RIGHT = new Point(1, 0, 0);
    private static int checks;
    private record Fixture(ShearWindbladeRenderGeometry.Shape shape, Point origin, Point forward, double distance) {}
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void near(double actual, double expected, double tolerance, String message) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance, message + ": " + actual + " != " + expected);
    }
    private static Fixture fixture(Point origin, Point look, int plane, double distance, int status, int fronts) {
        var basis = ShearWindbladeGeometry.frame(origin, look, .73);
        var poses = new ArrayList<ShearWindbladeState.Front>();
        for (int blade = 0; blade < fronts; blade++) {
            var points = ShearWindbladeGeometry.front(basis, plane, distance);
            poses.add(new ShearWindbladeState.Front(blade, points, points, status));
        }
        var receipt = new ShearWindbladeState.Frame(new ShearWindbladeState.Identity(19, new UUID(421, 62), 71, 1, 1),
                1, ShearWindbladeState.FLYING, plane == 1, origin, poses, List.of());
        var state = new ShearWindbladeState();
        check(state.accept(receipt, true), "real authoritative receipt admitted before enhancement");
        return new Fixture(ShearWindbladeRenderGeometry.shape(state.local(), 1), origin, basis.forward(), distance);
    }
    private static Mesh mesh(Fixture fixture, Point camera) {
        return ShearWindbladeVolumeGeometry.mesh(fixture.shape, fixture.origin, camera, RIGHT);
    }
    private static void verify(Fixture fixture, Point camera, Mesh mesh, int fronts) {
        check(mesh.frontCount() == fronts && mesh.quads().size() == fronts * 8 * 8, "exactly eight quads per actual chord");
        check(mesh.vertexCount() == fronts * 8 * 8 * 4, "bounded exact vertex accounting");
        int[] counts = new int[3];
        int lowGray = 255, highGray = 0;
        for (Quad quad : mesh.quads()) {
            counts[quad.material().ordinal()]++;
            var line = fixture.shape.strokes().get(quad.blade() * 8 + quad.chord());
            check(quad.vertices().size() == 4, "no hidden triangle fans or end caps");
            check(quad.alpha() >= 0 && quad.alpha() <= line.alpha(), "alpha bounded by accepted receipt");
            if (quad.material() == Material.SIDE) {
                check(quad.alpha() < line.alpha() * .40 && quad.gray() >= 140 && quad.gray() <= 245,
                        "neutral gray-white translucent faces avoid an opaque slab");
                lowGray = Math.min(lowGray, quad.gray()); highGray = Math.max(highGray, quad.gray());
            }
            for (int i = 0; i < 4; i++) {
                var vertex = quad.vertices().get(i);
                Point world = add(vertex.position(), camera);
                Point center = i < 2 ? line.from() : line.to();
                Point offset = subtract(world, center);
                check(vertex.position().finite() && Double.isFinite(vertex.u()) && Double.isFinite(vertex.v()), "all generated coordinates and UVs finite");
                check(world.finite(), "decoration stays inside valid world coordinates");
                check(world.distanceSquared(fixture.origin) <= 24 * 24 + 1e-10, "every generated vertex is range bounded");
                check(vertex.u() >= 0 && vertex.u() <= 1 && vertex.v() >= 0 && vertex.v() <= 1, "UV domain bounded");
                if (quad.material() != Material.CORE) {
                    near(Math.max(0, dot(offset, fixture.forward)), 0, 1e-8, "decoration never advances past certified contour");
                    check(dot(offset, fixture.forward) >= -fixture.distance - 1e-8, "short or stopped decoration does not trail before real travel");
                }
                if (quad.material() == Material.SIDE)
                    check(length(offset) <= .22 + 1e-8, "volume envelope has at most .22m decorative offset");
            }
            if (quad.material() == Material.CORE) {
                near(length(subtract(quad.vertices().get(0).position(), quad.vertices().get(1).position())), .070, 1e-10, "classic core starts at full .070m width");
                near(length(subtract(quad.vertices().get(2).position(), quad.vertices().get(3).position())), .070, 1e-10, "classic core ends at full .070m width");
                near(length(subtract(scale(add(quad.vertices().get(0).position(), quad.vertices().get(1).position()), .5), subtract(line.from(), camera))), 0, 1e-10, "core starts at exact actual contour");
                near(length(subtract(scale(add(quad.vertices().get(2).position(), quad.vertices().get(3).position()), .5), subtract(line.to(), camera))), 0, 1e-10, "core ends at exact actual contour");
            }
            if (quad.material() == Material.SOFT) {
                check(length(subtract(quad.vertices().get(0).position(), quad.vertices().get(1).position())) <= .38 + 1e-10, "soft shoulder starts within .38m width");
                check(length(subtract(quad.vertices().get(2).position(), quad.vertices().get(3).position())) <= .38 + 1e-10, "soft shoulder ends within .38m width");
            }
        }
        check(counts[Material.SIDE.ordinal()] == fronts * 8 * 6 && counts[Material.SOFT.ordinal()] == fronts * 8
                && counts[Material.CORE.ordinal()] == fronts * 8, "six sides, one shoulder and one core per chord");
        check(highGray - lowGray >= 90, "side faces have a visible dimensional gray range");
        for (int blade = 0; blade < fronts; blade++) for (int chord = 0; chord < 8; chord++) for (int layer = 0; layer < 8; layer++) {
            Quad current = mesh.quads().get((blade * 8 + chord) * 8 + layer);
            if (chord < 7) {
                Quad next = mesh.quads().get((blade * 8 + chord + 1) * 8 + layer);
                check(current.vertices().get(2).equals(next.vertices().get(1))
                        && current.vertices().get(3).equals(next.vertices().get(0)), "adjacent curved sections share identical positions and UVs");
            }
            if (layer < 7 && chord == 0)
                check(current.vertices().get(0).position().equals(subtract(fixture.shape.strokes().get(blade * 8).from(), camera))
                        && current.vertices().get(1).position().equals(current.vertices().get(0).position()), "start decoration tapers to zero with no cap");
            if (layer < 7 && chord == 7)
                check(current.vertices().get(2).position().equals(subtract(fixture.shape.strokes().get(blade * 8 + 7).to(), camera))
                        && current.vertices().get(3).position().equals(current.vertices().get(2).position()), "end decoration tapers to zero with no cap");
        }
    }
    private static void translation() {
        Point shift = new Point(29_999_970, -29_999_970, 29_999_970);
        Point localCamera = new Point(.375, .625, -3.125);
        var near = fixture(ZERO, new Point(.25, -.125, 1), 1, 8, ShearWindbladeState.BLOCKED, 2);
        var far = fixture(shift, new Point(.25, -.125, 1), 1, 8, ShearWindbladeState.BLOCKED, 2);
        Mesh a = mesh(near, localCamera), b = mesh(far, add(localCamera, shift));
        check(a.vertexCount() == 512 && b.vertexCount() == 512, "world-border translation retains full mesh");
        for (int i = 0; i < a.quads().size(); i++) for (int j = 0; j < 4; j++) {
            var av = a.quads().get(i).vertices().get(j); var bv = b.quads().get(i).vertices().get(j);
            near(length(subtract(av.position(), bv.position())), 0, 2e-8, "camera-relative geometry is translation invariant near world border");
            near(av.u(), bv.u(), 1e-8, "arc UV stable near world border");
        }
        verify(far, add(localCamera, shift), b, 2);
    }
    private static void cameraIndependence() {
        var f = fixture(ZERO, new Point(.3, .4, 1), 0, 5, ShearWindbladeState.TRAVELLING, 1);
        Point ca = new Point(0, 1, -4), cb = new Point(-3, 5, 9);
        Mesh a = mesh(f, ca), b = mesh(f, cb);
        for (int i = 0; i < a.quads().size(); i++) if (a.quads().get(i).material() == Material.SIDE)
            for (int j = 0; j < 4; j++) near(length(subtract(add(a.quads().get(i).vertices().get(j).position(), ca),
                    add(b.quads().get(i).vertices().get(j).position(), cb))), 0, 1e-12, "actual volume is independent of camera rotation/position");
    }
    private static ShearWindbladeRenderGeometry.Shape lines(List<Point> points) {
        var strokes = new ArrayList<ShearWindbladeRenderGeometry.Stroke>();
        for (int i = 0; i < 8; i++) strokes.add(new ShearWindbladeRenderGeometry.Stroke(points.get(i), points.get(i + 1), 0, i, 238));
        return new ShearWindbladeRenderGeometry.Shape(strokes);
    }
    private static void degeneracies() {
        var f = fixture(ZERO, new Point(0, 0, 1), 0, 1, 0, 1);
        check(ShearWindbladeVolumeGeometry.mesh(null, ZERO, ZERO, RIGHT).vertexCount() == 0, "null shape fails closed");
        check(ShearWindbladeVolumeGeometry.mesh(f.shape, new Point(Double.NaN, 0, 0), ZERO, RIGHT).vertexCount() == 0, "nonfinite origin fails closed");
        check(ShearWindbladeVolumeGeometry.mesh(f.shape, ZERO, new Point(0, 0, Double.POSITIVE_INFINITY), RIGHT).vertexCount() == 0, "nonfinite camera fails closed");
        check(ShearWindbladeVolumeGeometry.mesh(f.shape, ZERO, ZERO, ZERO).vertexCount() == 0, "zero fallback vector fails closed");
        var partial = new ShearWindbladeRenderGeometry.Shape(f.shape.strokes().subList(0, 7));
        check(ShearWindbladeVolumeGeometry.mesh(partial, ZERO, ZERO, RIGHT).vertexCount() == 0, "incomplete front never yields a partial enhancement");
        var repeated = new ArrayList<>(f.shape.strokes()); repeated.set(7, repeated.get(0));
        check(ShearWindbladeVolumeGeometry.mesh(new ShearWindbladeRenderGeometry.Shape(repeated), ZERO, ZERO, RIGHT).vertexCount() == 0, "duplicate chord fails closed");
        repeated = new ArrayList<>(f.shape.strokes());
        var old = repeated.get(3);
        repeated.set(3, new ShearWindbladeRenderGeometry.Stroke(add(old.from(), new Point(0, .001, 0)), old.to(), 0, 3, 238));
        check(ShearWindbladeVolumeGeometry.mesh(new ShearWindbladeRenderGeometry.Shape(repeated), ZERO, ZERO, RIGHT).vertexCount() == 0, "broken seam fails closed");
        var points = new ArrayList<Point>();
        for (int i = 0; i < 9; i++) points.add(new Point(i * .2, 0, 0));
        Mesh straight = ShearWindbladeVolumeGeometry.mesh(lines(points), ZERO, ZERO, RIGHT);
        check(straight.vertexCount() == 256, "collinear path and collinear camera fallback remain finite");
        points.set(3, points.get(2));
        Mesh zeroSegment = ShearWindbladeVolumeGeometry.mesh(lines(points), ZERO, points.get(2), RIGHT);
        check(zeroSegment.vertexCount() == 256, "isolated zero-length chord uses neighboring tangent without changing budget");
        for (var m : List.of(straight, zeroSegment)) for (var q : m.quads()) for (var v : q.vertices())
            check(v.position().finite() && Double.isFinite(v.u()), "degenerate tangent fallback never emits NaN");
        check(ShearWindbladeVolumeGeometry.mesh(lines(java.util.Collections.nCopies(9, ZERO)), ZERO, ZERO, RIGHT).vertexCount() == 0, "fully collapsed front fails closed");
        for (int i = 0; i < 9; i++) points.set(i, new Point(0, 0, 24));
        points.set(4, new Point(0, 0, 23.9));
        Mesh rangeTouch = ShearWindbladeVolumeGeometry.mesh(lines(points), ZERO, new Point(0, 0, 23), RIGHT);
        check(rangeTouch.vertexCount() == 0, "range-edge core cannot silently narrow or create invalid vertices");
    }
    private static void staleReceipts() {
        var basis = ShearWindbladeGeometry.frame(ZERO, new Point(0, 0, 1), 0);
        var points = ShearWindbladeGeometry.front(basis, 0, 2);
        var poses = List.of(new ShearWindbladeState.Front(0, points, points, ShearWindbladeState.TRAVELLING),
                new ShearWindbladeState.Front(1, points, points, ShearWindbladeState.BLOCKED));
        var state = new ShearWindbladeState();
        check(state.accept(new ShearWindbladeState.Frame(new ShearWindbladeState.Identity(19, new UUID(421, 63), 71, 1, 1),
                1, ShearWindbladeState.FLYING, false, ZERO, poses, List.of()), true), "stale test starts from actual accepted receipt");
        for (int age = 0; age < 3; age++) state.tick(false, cast -> true);
        var fading = ShearWindbladeRenderGeometry.shape(state.local(), 1);
        check(fading.strokes().stream().allMatch(line -> line.alpha() > 0)
                && ShearWindbladeVolumeGeometry.mesh(fading, ZERO, new Point(0, 0, -3), RIGHT).vertexCount() == 512,
                "still-visible fading receipt retains its complete enhancement");
        for (int age = 3; age < 5; age++) state.tick(false, cast -> true);
        var stale = ShearWindbladeRenderGeometry.shape(state.local(), 1);
        check(state.local().receiptAge() == 5 && stale.strokes().stream().anyMatch(line -> line.alpha() == 0)
                && stale.strokes().stream().anyMatch(line -> line.alpha() > 0), "age-five fixture contains a faded travelling front and a visible stopped front");
        check(ShearWindbladeVolumeGeometry.mesh(stale, ZERO, new Point(0, 0, -3), RIGHT).vertexCount() == 0,
                "nonpositive source alpha declines whole enhancement so classic fallback alone skips stale quads");
        var invisible = new ShearWindbladeRenderGeometry.Shape(stale.strokes().subList(0, 8));
        check(invisible.strokes().stream().allMatch(line -> line.alpha() == 0)
                && ShearWindbladeVolumeGeometry.mesh(invisible, ZERO, new Point(0, 0, -3), RIGHT).quads().isEmpty(),
                "fully stale travelling receipt emits no transparent enhanced quads");
    }
    public static void main(String[] args) {
        check(ShearWindbladeGeometry.RADIUS == .035 && ShearWindbladeGeometry.RANGE == 24, "authoritative hit apron and range unchanged");
        check(ShearWindbladeVolumeGeometry.MAX_SCENE_VERTICES == 2048
                && ShearWindbladeVolumeGeometry.MAX_CONTROLLER_VERTICES == 512, "hard four-controller scene budget is exactly 2048");
        check(ShearWindbladeVolumeGeometry.MAX_PARTICLES == 0 && ShearWindbladeVolumeGeometry.AFTERIMAGES == 0, "no particles or phantom damage fronts");
        for (Point look : List.of(new Point(0, 0, 1), new Point(0, 1, 0), new Point(.3, -.4, .8)))
            for (int plane = 0; plane < 2; plane++) for (double distance : List.of(0.0, .001, .02, .25, 1.0, 8.0, ShearWindbladeGeometry.MAX_TRANSLATION)) {
                var f = fixture(ZERO, look, plane, distance, ShearWindbladeState.BLOCKED, 2);
                Point camera = new Point(.375, .625, -3.125);
                verify(f, camera, mesh(f, camera), 2);
            }
        translation(); cameraIndependence(); degeneracies(); staleReceipts();
        var near = fixture(ZERO, new Point(0, 0, 1), 0, 0, 0, 1);
        for (var line : near.shape.strokes()) {
            Mesh mesh = ShearWindbladeVolumeGeometry.mesh(near.shape, ZERO, line.from(), subtract(line.to(), line.from()));
            check(mesh.vertexCount() == 256, "camera at a contour knot with tangent fallback stays finite for whole-pass near-eye rejection");
            for (var quad : mesh.quads()) for (var vertex : quad.vertices()) check(vertex.position().finite(), "near-eye vertices finite");
        }
        System.out.println("ShearWindbladeVolumeRegressionTest: " + checks + " checks passed (CPU geometry only; native visual quality untested)");
    }
    private static Point add(Point a, Point b) { return new Point(a.x() + b.x(), a.y() + b.y(), a.z() + b.z()); }
    private static Point subtract(Point a, Point b) { return new Point(a.x() - b.x(), a.y() - b.y(), a.z() - b.z()); }
    private static Point scale(Point a, double b) { return new Point(a.x() * b, a.y() * b, a.z() * b); }
    private static double dot(Point a, Point b) { return a.x() * b.x() + a.y() * b.y() + a.z() * b.z(); }
    private static double length(Point a) { return Math.sqrt(dot(a, a)); }
}
