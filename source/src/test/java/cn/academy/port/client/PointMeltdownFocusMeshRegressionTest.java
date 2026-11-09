package cn.academy.port.client;

import cn.academy.port.client.BeamEtchingMesh.Block;
import cn.academy.port.client.BeamEtchingMesh.Box;
import cn.academy.port.client.BeamEtchingMesh.Mark;
import cn.academy.port.client.BeamEtchingMesh.Point;
import cn.academy.port.client.PointMeltdownFocusMesh.Face;
import cn.academy.port.client.PointMeltdownFocusMesh.Vertex;
import java.util.List;

/** Standalone enhanced-material witnesses: native geometry, clipped UVs, authoritative focus and camera safety. */
public final class PointMeltdownFocusMeshRegressionTest {
    private static final Point ORIGIN = new Point(.5, .5, -12), LOOK = new Point(0, 0, 1);
    private static final Mark TARGET = new Mark(new Block(0, 0, 0), new Point(.5, .5, 0), 2);
    private static final Box CUBE = new Box(new Point(0, 0, 0), new Point(1, 1, 1));
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void close(double actual, double expected, double tolerance, String message) {
        check(Math.abs(actual - expected) <= tolerance, message + " (" + actual + " versus " + expected + ")");
    }
    private static List<Face> build(int focus, boolean focusing, boolean confirmed, int age, double visualTicks) {
        return PointMeltdownFocusMesh.build(ORIGIN, TARGET, CUBE, ORIGIN, LOOK, focus, focusing, confirmed, age, visualTicks);
    }
    public static void main(String[] args) {
        phases(); nativeFaces(); partialFacesAndUvs(); nearPlaneUvs(); rangeAndSource(); cameraSafety(); farCoordinates(); validationAndBudgets();
        System.out.println("PASS " + checks + " NEW233 enhanced focus, original materials, native-face UV, camera and bounded geometry checks");
    }

    private static void phases() {
        List<Face> preview = build(0, false, false, 0, 0);
        check(preview.size() == 53, "preview uses three open arcs and five modest source-ray layers");
        check(preview.stream().noneMatch(f -> f.texture() == 4), "preview does not manufacture an active heat core");
        check(surfaceCount(preview, TARGET, 5) == 24 && surfaceCount(preview, TARGET, -1) == 24,
                "three eight-segment arcs each have a soft shoulder and fine bright core");
        close(maxSurfaceRadius(preview, TARGET, 5), .24, 1e-12, "preview outer radius stays at .24 metres");
        // At each of the three gaps, no arc vertex reaches the intervening direction.
        for (int gap = 0; gap < 3; gap++) {
            double angle = Math.toRadians(100 + 120 * gap);
            Point direction = new Point(Math.cos(angle), -Math.sin(angle), 0);
            double nearest = preview.stream().filter(f -> surface(f, TARGET) && f.texture() == 5)
                    .flatMap(f -> f.vertices().stream()).mapToDouble(v -> {
                        Point radial = v.point().subtract(TARGET.point()).add(new Point(0, 0, PointMeltdownFocusMesh.FACE_OFFSET));
                        return radial.dot(direction) / radial.length();
                    }).max().orElseThrow();
            check(nearest < Math.cos(Math.toRadians(19.9)), "each preview arc leaves a real forty-degree gap");
        }
        double[] rings = {.24, .16, .08}, heats = {.04, .0575, .075};
        int index = 0, previousCoreAlpha = -1;
        for (int focus : new int[]{0, 5, 10}) {
            List<Face> faces = build(focus, true, false, 0, 3);
            check(faces.size() == 79, "focus has 79 preclip faces, including exactly two short packets");
            check(materialRuns(faces) <= 10, "arc material layers are contiguous instead of flushing once per segment");
            for (int arcVertex = 0; arcVertex < 24; arcVertex++) {
                check(faces.get(arcVertex).texture() == 5 && faces.get(arcVertex + 24).texture() == -1,
                        "all soft arc shoulders precede the matching bright-core layer");
                Point shoulderMid = faces.get(arcVertex).a().point().lerp(faces.get(arcVertex).b().point(), .5);
                Point coreMid = faces.get(arcVertex + 24).a().point().lerp(faces.get(arcVertex + 24).b().point(), .5);
                check(shoulderMid.subtract(coreMid).length() < 1e-12, "material batching preserves the same exact arc centerline");
                close(faces.get(arcVertex + 24).a().point().subtract(faces.get(arcVertex + 24).b().point()).length(), .008, 1e-12,
                        "open-arc bright core preserves the readable .008 metre physical width");
            }
            close(maxSurfaceRadius(faces, TARGET, 5), rings[index], 1e-12, "only server focus contracts the arcs");
            close(maxSurfaceRadius(faces, TARGET, 4), heats[index], 1e-12, "native heat core grows locally through real focus");
            check(maxSurfaceRadius(faces, TARGET, 0) <= .16 + 1e-12, "transparent heat shoulder never exceeds .16 metres");
            int coreAlpha = faces.stream().filter(f -> f.texture() == 4).findFirst().orElseThrow().alpha();
            check(coreAlpha > previousCoreAlpha, "heat brightness rises with authoritative focus");
            previousCoreAlpha = coreAlpha;
            check(faces.stream().filter(f -> f.texture() == 0 && !surface(f, TARGET)).count() == 2,
                    "focusing creates exactly two bounded longitudinal light packets");
            check(faces.stream().filter(f -> f.texture() == 4).allMatch(f -> f.rgb() == 0xFFFFFF),
                    "the original irregular green-white mdball core retains its own texture palette");
            index++;
        }
        List<Face> fixed = build(5, true, false, 0, 3), later = build(5, true, false, 0, 4000);
        close(maxSurfaceRadius(fixed, TARGET, 5), maxSurfaceRadius(later, TARGET, 5), 1e-12,
                "cosmetic elapsed time cannot advance focus radius");
        check(fixed.stream().filter(f -> f.texture() == 4).toList().equals(later.stream().filter(f -> f.texture() == 4).toList()),
                "cosmetic rotation and packets cannot advance heat or produce success");
        check(fixed.equals(build(5, true, false, 0, 3)), "a paused cosmetic clock gives byte-for-byte stable mesh values");
        check(build(10, false, false, 0, 3).stream().noneMatch(f -> f.texture() == 4), "nonfocus preview cannot infer completion from elapsed ticks");
        List<Face> flash0 = build(10, false, true, 0, 3), flash5 = build(10, false, true, 5, 3);
        check(!flash0.isEmpty() && !flash5.isEmpty() && build(10, false, true, 6, 3).isEmpty(), "confirmation exists only at ages zero through five");
        check(flash0.stream().allMatch(f -> surface(f, TARGET)) && flash5.stream().allMatch(f -> surface(f, TARGET)),
                "confirmed success is only a local heat flash and outward broken arcs");
        check(maxSurfaceRadius(flash5, TARGET, 5) > maxSurfaceRadius(flash0, TARGET, 5), "confirmed broken arcs move outward");
        check(flash5.stream().mapToInt(Face::alpha).max().orElseThrow() < flash0.stream().mapToInt(Face::alpha).max().orElseThrow(),
                "confirmed heat fades instead of accumulating into persistent success geometry");
        check(PointMeltdownFocusMesh.build(ORIGIN, null, CUBE, ORIGIN, LOOK, 5, true, false, 0, 3).isEmpty(),
                "a canceled context with its target cleared cannot leave residual geometry");
    }

    private static void nativeFaces() {
        for (int face = 0; face < 6; face++) {
            Point normal = BeamEtchingMesh.normal(face), center = new Point(.5, .5, .5).add(normal.scale(.5));
            Mark target = new Mark(new Block(0, 0, 0), center, face);
            Point origin = center.add(normal.scale(6)), look = normal.scale(-1);
            for (int phase : new int[]{0, 5, 10}) {
                List<Face> faces = PointMeltdownFocusMesh.build(origin, target, CUBE, origin, look, phase, true, false, 0, 2.25);
                check(!faces.isEmpty(), "all six native faces support enhanced focus");
                for (Face f : faces) if (surface(f, target)) for (Vertex vertex : f.vertices()) {
                    Point relative = vertex.point().subtract(center);
                    close(relative.dot(normal), .003, 1e-12, "each heat and arc vertex remains on the selected native face");
                    check(relative.subtract(normal.scale(.003)).length() <= .24 + 1e-12, "surface decorations never become a block-sized panel");
                }
            }
        }
    }

    private static void partialFacesAndUvs() {
        Box slab = new Box(new Point(.90, 0, .90), new Point(1, .5, 1));
        Mark edge = new Mark(new Block(0, 0, 0), new Point(.98, .5, .98), 1);
        Point origin = edge.point().add(new Point(0, 4, 0)), look = new Point(0, -1, 0);
        check(PointMeltdownFocusMesh.build(origin, edge, CUBE, origin, look, 5, true, false, 0, 0).isEmpty(),
                "a full-cube face cannot replace the selected partial slab face");
        List<Face> clipped = PointMeltdownFocusMesh.build(origin, edge, slab, origin, look, 5, true, false, 0, 0);
        check(!clipped.isEmpty(), "a partial face retains its genuine clipped marker");
        int intersections = 0;
        for (Face face : clipped) if (surface(face, edge)) for (Vertex vertex : face.vertices()) {
            Point p = vertex.point();
            check(p.x() >= .90 - 1e-12 && p.x() <= 1 + 1e-12 && p.z() >= .90 - 1e-12 && p.z() <= 1 + 1e-12,
                    "every surface decoration is clipped to the native partial Box");
            close(p.y(), .503, 1e-12, "partial face offset stays on the real slab top");
            if (face.texture() == 4 || face.texture() == 0) {
                double radius = face.texture() == 4 ? .0575 : .13;
                close(vertex.u(), .5 + (p.x() - .98) / (2 * radius), 1e-12, "surface cut preserves affine heat U coordinates");
                close(vertex.v(), .5 - (p.z() - .98) / (2 * radius), 1e-12, "surface cut preserves affine heat V coordinates");
                if (Math.abs(p.x() - 1) < 1e-12 || Math.abs(p.z() - 1) < 1e-12) {
                    check(vertex.u() > 0 && vertex.u() < 1 && vertex.v() > 0 && vertex.v() < 1,
                            "new cut edges do not reset texture coordinates to zero or one");
                    intersections++;
                }
            }
        }
        check(intersections > 10, "UV continuity witness exercises actual newly clipped native edges");
    }

    private static void nearPlaneUvs() {
        Point camera = new Point(.5, .5, -2), look = unit(new Point(1, 0, .04));
        List<Face> faces = PointMeltdownFocusMesh.build(ORIGIN, TARGET, CUBE, camera, look, 5, true, false, 0, 1);
        int cutVertices = 0;
        for (Face face : faces) for (Vertex vertex : face.vertices()) {
            double depth = vertex.point().subtract(camera).dot(look);
            check(depth >= (surface(face, TARGET) ? .08 : .2) - 1e-10, "returned polygons are fully near-plane clipped");
            if (face.texture() == 4) {
                close(vertex.u(), .5 + (vertex.point().x() - .5) / (.0575 * 2), 1e-12, "near cuts interpolate original heat U");
                close(vertex.v(), .5 - (vertex.point().y() - .5) / (.0575 * 2), 1e-12, "near cuts interpolate original heat V");
                if (Math.abs(depth - .08) < 1e-10) cutVertices++;
            }
        }
        check(cutVertices > 0, "the near-plane witness genuinely intersects the textured core");
        List<Face> sourceClipped = build(5, true, false, 0, 3);
        Face entry = sourceClipped.stream().filter(f -> f.texture() == 1).findFirst().orElseThrow();
        check(entry.vertices().stream().anyMatch(v -> v.u() > .79 && v.u() < .81),
                "clipping the first .20 metres preserves .8 U within the original .25 metre ray entry");
    }

    private static void rangeAndSource() {
        Point origin = new Point(.5, .5, -36);
        List<Face> faces = PointMeltdownFocusMesh.build(origin, TARGET, CUBE, origin, LOOK, 5, true, false, 0, 13.99);
        check(!faces.isEmpty(), "an exact 36-metre native source remains valid");
        verifyRayCenters(faces, origin, TARGET, origin, LOOK);
        check(PointMeltdownFocusMesh.build(new Point(.5, .5, -36.001), TARGET, CUBE, ORIGIN, LOOK, 5, true, false, 0, 3).isEmpty(),
                "enhancement cannot extend the native 36-metre reach");
        check(PointMeltdownFocusMesh.build(new Point(.5, .5, 1), TARGET, CUBE, ORIGIN, LOOK, 5, true, false, 0, 3).isEmpty(),
                "rear-side source cannot fabricate a hit on the selected front face");
        for (double ticks : new double[]{0, 3.5, 6.999, 7, 13.999, 14, 1e300, -20}) {
            List<Face> sample = build(5, true, false, 0, ticks);
            verifyRayCenters(sample, ORIGIN, TARGET, ORIGIN, LOOK);
            for (Face f : sample) if (f.texture() == 0 && !surface(f, TARGET)) {
                double min = f.vertices().stream().mapToDouble(v -> v.point().z()).min().orElseThrow();
                double max = f.vertices().stream().mapToDouble(v -> v.point().z()).max().orElseThrow();
                check(max - min <= .65 + 1e-10, "each cosmetic packet has a bounded longitudinal length");
            }
        }
    }

    private static void cameraSafety() {
        int rayFaces = 0;
        for (double strafe : new double[]{-.6, -.1, 0, .1, .6}) for (double rise : new double[]{-.15, 0, .15})
            for (int yaw : new int[]{-25, 0, 25}) for (int pitch : new int[]{-15, 0, 15}) {
                Point camera = ORIGIN.add(new Point(strafe, rise, 0));
                double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
                Point look = new Point(Math.sin(y) * Math.cos(p), Math.sin(p), Math.cos(y) * Math.cos(p));
                List<Face> faces = PointMeltdownFocusMesh.build(ORIGIN, TARGET, CUBE, camera, look, 5, true, false, 0, 3);
                check(!faces.isEmpty(), "moving cameras retain the native target cue");
                rayFaces += verifyRayCenters(faces, ORIGIN, TARGET, camera, look);
            }
        check(rayFaces > 800, "projection witness exercises varied historical-source camera motion");
        List<Face> endOn = build(5, true, false, 0, 3);
        for (Face face : endOn) if (!surface(face, TARGET)) {
            double twiceArea = 0;
            List<Vertex> vertices = face.vertices();
            for (int i = 0; i < vertices.size(); i++) {
                Point a = vertices.get(i).point().subtract(ORIGIN), b = vertices.get((i + 1) % vertices.size()).point().subtract(ORIGIN);
                twiceArea += a.x() / a.z() * b.y() / b.z() - b.x() / b.z() * a.y() / a.z();
            }
            close(twiceArea, 0, 1e-14, "true end-on source projection is inherently collapsed, with no invented hand offset");
        }
        check(PointMeltdownFocusMesh.build(ORIGIN, TARGET, CUBE, ORIGIN, new Point(0, 0, -1), 5, true, false, 0, 3).isEmpty(),
                "behind-camera geometry is not mirrored into view");
        check(PointMeltdownFocusMesh.build(ORIGIN, TARGET, CUBE, new Point(.5, .5, -.55), LOOK, 5, true, false, 0, 3).isEmpty(),
                "near-eye target and ray fade completely by .55 metres");
        close(PointMeltdownFocusMesh.nearFade(TARGET.point(), new Point(.5, .5, -.975)), .5, 1e-12,
                "endpoint fade is smoothly half visible midway through the near range");
        close(PointMeltdownFocusMesh.nearFade(TARGET.point(), new Point(.5, .5, -1.4)), 1, 1e-12,
                "endpoint is fully readable from 1.4 metres");
        check(PointMeltdownFocusMesh.nearFade(TARGET.point(), new Point(.5, .5, -.5501)) < 1e-6,
                "near fade starts smoothly without a sudden opaque panel");
        // Look direction can be vertical without producing a zero fallback cross product.
        Mark floor = new Mark(new Block(0, 0, 0), new Point(.5, 1, .5), 1);
        Point above = new Point(.5, 8, .5);
        check(!PointMeltdownFocusMesh.build(above, floor, CUBE, above, new Point(0, -1, 0), 5, true, false, 0, 3).isEmpty(),
                "vertical end-on rays retain a finite frame");
    }

    private static int verifyRayCenters(List<Face> faces, Point origin, Mark mark, Point camera, Point look) {
        Point axis = mark.point().subtract(origin), direction = unit(axis);
        Point right = unit(look.cross(Math.abs(look.y()) > .9 ? new Point(1, 0, 0) : new Point(0, 1, 0))), up = right.cross(look);
        int count = 0;
        for (Face face : faces) if (!surface(face, mark)) {
            count++;
            for (Vertex vertex : face.vertices()) {
                Point fromSource = vertex.point().subtract(origin);
                double along = fromSource.dot(direction);
                check(along >= -1e-8 && along <= axis.length() + 1e-8, "rays and packets cannot overshoot either true segment endpoint");
                Point center = origin.add(direction.scale(along));
                check(vertex.point().subtract(center).length() <= .0225 + 1e-9, "ray shoulder stays inside its .045 metre physical full width");
                Point relative = vertex.point().subtract(camera), c = center.subtract(camera);
                check(relative.dot(look) >= .2 - 1e-8, "every expanded ray vertex is near clipped at .20 metres");
                check(c.dot(look) >= .19, "near-plane clipping never fabricates a center behind the eye");
                double dx = relative.dot(right) / relative.dot(look) - c.dot(right) / c.dot(look);
                double dy = relative.dot(up) / relative.dot(look) - c.dot(up) / c.dot(look);
                check(Math.hypot(dx, dy) <= .006 + 1e-9, "ray expansion cannot produce a perspective wedge above .006 half width");
            }
        }
        return count;
    }

    private static void farCoordinates() {
        Point offset = new Point(29_999_990, 0, 29_999_990), camera = ORIGIN.add(new Point(.125, .2, 0));
        Mark shifted = new Mark(new Block(29_999_990, 0, 29_999_990), TARGET.point().add(offset), 2);
        Box shiftedBox = new Box(CUBE.min().add(offset), CUBE.max().add(offset));
        List<Face> local = PointMeltdownFocusMesh.build(ORIGIN, TARGET, CUBE, camera, LOOK, 5, true, false, 0, 3.5);
        List<Face> far = PointMeltdownFocusMesh.build(ORIGIN.add(offset), shifted, shiftedBox, camera.add(offset), LOOK, 5, true, false, 0, 3.5);
        check(local.size() == far.size(), "large-coordinate translation keeps mesh topology");
        for (int i = 0; i < local.size(); i++) {
            Face a = local.get(i), b = far.get(i);
            check(a.rgb() == b.rgb() && a.alpha() == b.alpha() && a.texture() == b.texture(), "translation preserves material values");
            for (int v = 0; v < 4; v++) {
                Vertex expected = a.vertices().get(v), actual = b.vertices().get(v);
                check(actual.point().subtract(camera.add(offset)).subtract(expected.point().subtract(camera)).length() < 3e-8,
                        "double world subtraction preserves fine material geometry at the world border");
                close(actual.u(), expected.u(), 1e-7, "large-coordinate near cuts preserve U continuity");
                close(actual.v(), expected.v(), 1e-7, "large-coordinate near cuts preserve V continuity");
            }
        }
    }

    private static void validationAndBudgets() {
        check(PointMeltdownFocusMesh.SPAWNED_PARTICLES == 0, "enhanced focus spawns zero particles");
        check(PointMeltdownFocusMesh.MAX_PRECLIP_QUADS <= 100, "each context has an explicit sub-100 preclip face bound");
        for (double clock : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
            check(build(5, true, false, 0, clock).isEmpty(), "nonfinite visual clocks fail closed");
        for (int focus : new int[]{-1, 11, Integer.MAX_VALUE}) check(build(focus, true, false, 0, 0).isEmpty(), "invalid focus cannot create geometry");
        check(build(5, true, false, -1, 0).isEmpty(), "negative ages fail closed");
        for (Point bad : new Point[]{null, new Point(Double.NaN, 0, 0), new Point(0, Double.POSITIVE_INFINITY, 0)}) {
            check(PointMeltdownFocusMesh.build(bad, TARGET, CUBE, ORIGIN, LOOK, 5, true, false, 0, 0).isEmpty(), "invalid source fails closed");
            check(PointMeltdownFocusMesh.build(ORIGIN, TARGET, CUBE, bad, LOOK, 5, true, false, 0, 0).isEmpty(), "invalid camera fails closed");
            check(PointMeltdownFocusMesh.build(ORIGIN, TARGET, CUBE, ORIGIN, bad, 5, true, false, 0, 0).isEmpty(), "invalid look fails closed");
        }
        check(PointMeltdownFocusMesh.build(ORIGIN, TARGET, CUBE, ORIGIN, new Point(0, 0, 2), 5, true, false, 0, 0).isEmpty(),
                "nonnormalized view directions are rejected");
        check(PointMeltdownFocusMesh.build(ORIGIN, TARGET, new Box(new Point(Double.NaN, 0, 0), new Point(1, 1, 1)), ORIGIN, LOOK,
                5, true, false, 0, 0).isEmpty(), "invalid native shape boxes fail closed");
        for (int phase : new int[]{0, 5, 10}) for (int age : new int[]{0, 5, 6}) for (boolean confirmed : new boolean[]{false, true}) {
            List<Face> faces = build(phase, true, confirmed, age, 3.25);
            check(faces.size() <= PointMeltdownFocusMesh.MAX_CLIPPED_QUADS, "postclip polygon count has a finite bound");
            if (!confirmed) check(faces.size() * 4 * 4 <= 4096, "four unobstructed enhanced contexts fit the shared 4096-vertex scene ceiling");
            for (Face face : faces) {
                check(face.alpha() > 0 && face.alpha() <= 255 && face.rgb() >= 0 && face.rgb() <= 0xFFFFFF, "every emitted material has bounded color and alpha");
                check(face.texture() >= -1 && face.texture() <= 5, "only approved original material identifiers are emitted");
                for (Vertex vertex : face.vertices()) {
                    check(BeamEtchingMesh.validPoint(vertex.point()) && Double.isFinite(vertex.u()) && Double.isFinite(vertex.v()), "all emitted vertices and UVs are finite");
                    check(vertex.u() >= -1e-12 && vertex.u() <= 1 + 1e-12 && vertex.v() >= -1e-12 && vertex.v() <= 1 + 1e-12,
                            "clipping retains bounded original texture coordinates");
                }
            }
        }
        List<Face> immutable = build(5, true, false, 0, 3);
        boolean rejected = false;
        try { immutable.clear(); } catch (UnsupportedOperationException expected) { rejected = true; }
        check(rejected, "mesh snapshots are immutable across later frames");
    }

    private static Point unit(Point value) { return value.scale(1 / value.length()); }
    private static int materialRuns(List<Face> faces) {
        int runs = 0, previous = Integer.MIN_VALUE;
        for (Face face : faces) if (face.texture() != previous) { runs++; previous = face.texture(); }
        return runs;
    }
    private static boolean surface(Face face, Mark mark) {
        Point normal = BeamEtchingMesh.normal(mark.face());
        return face.vertices().stream().allMatch(v -> Math.abs(v.point().subtract(mark.point()).dot(normal) - .003) < 1e-8);
    }
    private static long surfaceCount(List<Face> faces, Mark mark, int texture) {
        return faces.stream().filter(f -> f.texture() == texture && surface(f, mark)).count();
    }
    private static double maxSurfaceRadius(List<Face> faces, Mark mark, int texture) {
        Point center = mark.point().add(BeamEtchingMesh.normal(mark.face()).scale(.003));
        return faces.stream().filter(f -> f.texture() == texture && surface(f, mark)).flatMap(f -> f.vertices().stream())
                .mapToDouble(v -> v.point().subtract(center).length()).max().orElseThrow();
    }
}
