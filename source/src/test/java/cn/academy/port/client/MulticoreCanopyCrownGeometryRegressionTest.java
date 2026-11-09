package cn.academy.port.client;

import cn.academy.port.client.FoldedMirrorMesh.Face;
import cn.academy.port.client.MulticoreCanopyState.Core;
import cn.academy.port.client.MulticoreCanopyState.Frame;
import cn.academy.port.client.MulticoreCanopyState.Identity;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.highpower.core.MulticoreCanopyGeometry;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Standalone CPU coverage of the optional receipt-proven, open NEW251 source crown. */
public final class MulticoreCanopyCrownGeometryRegressionTest {
    private static int checks;
    private static final Vector ZERO = v(0, 0, 0), X = v(1, 0, 0), Y = v(0, 1, 0), Z = v(0, 0, 1);
    private static final Identity ID = new Identity(9, new UUID(251, 63), 2, 3, 5);

    public static void main(String[] args) {
        frozenOrientation(); substantialOpenPetals(); missingAndUnavailable(); phasesAndExpiry();
        cameraVisibility(); softenedCurvesAndSourceProximity(); worldBorderTranslation(); invalidInputs(); budgetsAndImmutability();
        System.out.println("PASS MulticoreCanopyCrownGeometryRegressionTest: " + checks + " checks");
    }

    private static void frozenOrientation() {
        for (Vector forward : List.of(Z, X, Y, Y.scale(-1), unit(v(2, -3, 5)))) {
            Vector eye = v(8, 72, -13), center = eye.add(forward.scale(2));
            Frame frame = frame(cores(eye, forward), MulticoreCanopyState.ARMED, 0, 200);
            var pose = MulticoreCanopyCrownGeometry.basis(frame).orElseThrow();
            close(pose.center(), center, "source center is recovered from the original fixed core geometry");
            close(pose.forward(), forward, "core IDs prove orientation and forward sign, including vertical poses");
            check(Math.abs(pose.right().dot(pose.up())) < 1e-10, "recovered basis is orthogonal");
            Frame moved = new Frame(ID, 2, frame.phase(), frame.status(), 190, v(200, 90, -80),
                    frame.cores(), List.of(), List.of(), false, 0, 0);
            check(MulticoreCanopyCrownGeometry.basis(moved).equals(MulticoreCanopyCrownGeometry.basis(frame)),
                    "current owner feet never reorient or relocate the frozen source");
            List<Face> first = source(frame, center.subtract(forward.scale(8)), forward, 40);
            List<Face> shifted = source(moved, center.subtract(forward.scale(8)), forward, 40);
            check(first.equals(shifted), "owner movement alone preserves every crown face");
        }
        List<Core> malformed = new ArrayList<>(cores(ZERO, Z));
        Core last = malformed.getLast();
        malformed.set(5, new Core(5, last.center().add(v(.001, 0, 0)), true));
        Frame inconsistent = frame(malformed, MulticoreCanopyState.ARMED, 0, 200);
        check(MulticoreCanopyState.valid(inconsistent), "fixture is an otherwise valid receipt");
        check(MulticoreCanopyCrownGeometry.basis(inconsistent).isEmpty()
                        && source(inconsistent, v(0, 0, -6), Z, 40).isEmpty(),
                "every received center must agree with the indexed original layout");
    }

    private static void substantialOpenPetals() {
        Frame frame = frame(cores(ZERO, Z), MulticoreCanopyState.ARMED, 0, 200);
        List<Core> original = List.copyOf(frame.cores());
        List<Face> faces = source(frame, v(0, 0, -6), Z, 40);
        check(faces.size() == 96, "six open curved petals contain eight two-layer segments each");
        var pose = MulticoreCanopyCrownGeometry.basis(frame).orElseThrow();
        double deepest = 0, outer = 0, bodyArea = 0, broadest = 0;
        for (Face face : faces) {
            check(face.kind() == MulticoreCanopyMesh.ORB_GLOW || face.kind() == MulticoreCanopyMesh.RIM,
                    "crown uses existing non-beam material kinds");
            check(face.texture() == MulticoreCanopyMesh.GLOW_TEXTURE,
                    "both source layers use original soft glow artwork rather than an opaque white bar");
            if (face.kind() == MulticoreCanopyMesh.ORB_GLOW) {
                bodyArea += area(face);
                broadest = Math.max(broadest, face.a().subtract(face.b()).length());
            }
            for (Vector vertex : face.vertices()) {
                Vector local = vertex.subtract(pose.center());
                double depth = local.dot(pose.forward());
                double radius = Math.hypot(local.dot(pose.right()), local.dot(pose.up()));
                check(depth <= 1e-8 && depth >= -1.2 - 1e-8, "petal curves only through the specified 1.2m source depth");
                check(radius >= .79 && radius <= 2, "all corners preserve the central opening and four-metre outer diameter");
                deepest = Math.min(deepest, depth); outer = Math.max(outer, radius);
            }
        }
        check(Math.abs(deepest + 1.2) < 1e-8 && outer > 1.97, "actual geometry reaches the advertised diameter and depth");
        check(bodyArea > 3 && broadest > .77, "wide energy surfaces have substantial area, not fine outlines");
        for (int core = 0; core < 6; core++) {
            Face root = faces.get(core * 16);
            close(root.a().add(root.b()).scale(.5), frame.cores().get(core).center(),
                    "each petal starts on its received core center");
            Vector firstDirection = midpoint(faces.get(core * 16 + 2)).subtract(midpoint(root));
            Vector lastDirection = midpoint(faces.get(core * 16 + 14)).subtract(midpoint(faces.get(core * 16 + 12)));
            check(unit(firstDirection).dot(unit(lastDirection)) < .8, "petal centerline bends visibly through its depth");
        }
        check(original.equals(frame.cores()), "source decoration never mutates any actual core position");
        // Each face remains wholly on one radial side; no face joins distinct petals or crosses the open middle.
        for (Face face : faces) {
            Vector radial = unit(midpoint(face).subtract(pose.center()).subtract(pose.forward()
                    .scale(midpoint(face).subtract(pose.center()).dot(pose.forward()))));
            for (Vector vertex : face.vertices())
                check(vertex.subtract(pose.center()).dot(radial) > .65, "whole petal surface remains outside the open center");
        }
    }

    private static void missingAndUnavailable() {
        List<Core> all = cores(ZERO, Z);
        Frame complete = frame(all, MulticoreCanopyState.ARMED, 0, 200);
        List<Face> full = source(complete, v(0, 0, -6), Z, 40);
        for (int mask = 0; mask < 64; mask++) {
            List<Core> received = new ArrayList<>(), available = new ArrayList<>();
            List<Face> expected = new ArrayList<>();
            for (Core core : all) {
                boolean exists = (mask & 1 << core.id()) != 0;
                if (exists) {
                    received.add(core);
                    expected.addAll(full.subList(core.id() * 16, (core.id() + 1) * 16));
                }
                available.add(new Core(core.id(), core.center(), exists));
            }
            Frame partial = frame(received, MulticoreCanopyState.DEPLOY, 0, 200);
            List<Face> result = source(partial, v(0, 0, -6), Z, 40);
            check(result.size() == (received.size() < 3 ? 0 : received.size() * 16),
                    "zero, one and two cores omit the ambiguous crown; proven partial layouts draw only their sectors");
            if (received.size() >= 3) {
                for (int i = 0; i < result.size(); i++) for (int p = 0; p < 4; p++)
                    close(result.get(i).vertices().get(p), expected.get(i).vertices().get(p),
                            "partial deployment does not redistribute or fabricate missing sectors");
            }
            List<Face> held = source(frame(available, MulticoreCanopyState.HELD, 0, 200), v(0, 0, -6), Z, 40);
            check(held.equals(expected), "unavailable sectors vanish without shifting remaining petals");
        }
    }

    private static void phasesAndExpiry() {
        List<Core> cores = cores(ZERO, Z);
        Frame armed = frame(cores, MulticoreCanopyState.ARMED, 0, 200);
        List<Face> reference = source(armed, v(0, 0, -6), Z, 40);
        for (int phase = MulticoreCanopyState.DEPLOY; phase < MulticoreCanopyState.ENDED; phase++) {
            Frame received = frame(cores, phase, phase == MulticoreCanopyState.WARNING ? 12 : 0, 200);
            List<Face> result = source(received, v(0, 0, -6), Z, 40);
            check(result.size() == reference.size(), "actual active phase retains only the established source sectors");
            for (int i = 0; i < result.size(); i++)
                check(result.get(i).vertices().equals(reference.get(i).vertices()), "phase and warning never move source cores or petals");
            check(result.equals(source(received, v(0, 0, -6), Z, 40)), "same pause-aware age yields identical paused material and geometry");
        }
        int previous = -1;
        double previousExtent = Double.POSITIVE_INFINITY;
        for (int warning = 12; warning >= 0; warning--) {
            List<Face> result = source(frame(cores, MulticoreCanopyState.WARNING, warning, 200), v(0, 0, -6), Z, 40);
            int alpha = result.getFirst().alpha();
            check(alpha >= previous, "warning charge follows only the received countdown");
            previous = alpha;
            Vector actualCore = cores.getFirst().center();
            double extent = result.subList(0, 16).stream().flatMap(f -> f.vertices().stream())
                    .mapToDouble(p -> p.subtract(actualCore).length()).max().orElseThrow();
            check(extent < previousExtent, "received warning countdown progressively contracts decoration toward its own actual source");
            previousExtent = extent;
            for (int core = 0; core < 6; core++) {
                Face root = result.get(core * 16);
                close(root.a().add(root.b()).scale(.5), cores.get(core).center(),
                        "warning contraction preserves the exact source root for every existing core");
            }
            for (int core = 1; core < 6; core++)
                check(result.get(core * 16).alpha() == alpha, "warning never guesses which core will fire next");
        }
        double armedExtent = reference.subList(0, 16).stream().flatMap(f -> f.vertices().stream())
                .mapToDouble(p -> p.subtract(cores.getFirst().center()).length()).max().orElseThrow();
        check(Math.abs(previousExtent / armedExtent - .55) < 1e-8,
                "warning convergence is bounded at fifty-five percent while ARMED retains its full four-metre crown");
        Frame earlierWarning = frame(cores, MulticoreCanopyState.WARNING, 7, 200);
        Frame refreshedWarning = frame(cores, MulticoreCanopyState.WARNING, 4, 197);
        List<Face> elapsedWarning = MulticoreCanopyCrownGeometry.source(earlierWarning, v(0, 0, -6), Z, 43, 197, 3);
        List<Face> refreshed = MulticoreCanopyCrownGeometry.source(refreshedWarning, v(0, 0, -6), Z, 43, 197, 0);
        check(elapsedWarning.equals(refreshed), "warning receipt refresh at the same remaining time preserves its visual phase");
        check(elapsedWarning.equals(MulticoreCanopyCrownGeometry.source(earlierWarning, v(0, 0, -6), Z, 43, 197, 3)),
                "paused source age and receipt quiet clock leave warning geometry and material unchanged");
        check(elapsedWarning.getFirst().alpha() > MulticoreCanopyCrownGeometry.source(
                        earlierWarning, v(0, 0, -6), Z, 43, 197, 0).getFirst().alpha(),
                "nonpaused quiet ticks advance the received countdown between heartbeat packets");
        check(MulticoreCanopyCrownGeometry.source(earlierWarning, v(0, 0, -6), Z, 43, 190, 10)
                        .equals(MulticoreCanopyCrownGeometry.source(frame(cores, MulticoreCanopyState.WARNING, 0, 190),
                                v(0, 0, -6), Z, 43, 190, 0)),
                "elapsed countdown saturates at zero without restarting a warning timer");
        check(source(frame(cores, MulticoreCanopyState.ENDED, 0, 0), v(0, 0, -6), Z, 40).isEmpty(),
                "ENDED creates neither a new crown nor a source tail");
        for (float age : new float[] {-1, 240, Float.NaN, Float.POSITIVE_INFINITY})
            check(source(armed, v(0, 0, -6), Z, age).isEmpty(), "source clock is finite and bounded to its actual lifetime");
        for (int remaining : new int[] {-1, 0, 201, Integer.MAX_VALUE})
            check(MulticoreCanopyCrownGeometry.source(armed, v(0, 0, -6), Z, 40, remaining, 0).isEmpty(),
                    "explicit live lease cannot be expired or exceed the authenticated lease");
        for (int quiet : new int[] {-1, 12, Integer.MAX_VALUE})
            check(MulticoreCanopyCrownGeometry.source(armed, v(0, 0, -6), Z, 40, 100, quiet).isEmpty(),
                    "stale or invalid source context never creates decoration");
        check(!MulticoreCanopyCrownGeometry.source(armed, v(0, 0, -6), Z, 239.9f, 1, 11).isEmpty(),
                "last authenticated fraction remains visible before the explicit lease ends");
    }

    private static void cameraVisibility() {
        Frame frame = frame(cores(ZERO, Z), MulticoreCanopyState.ARMED, 0, 200);
        List<Face> original = source(frame, v(0, 0, -6), Z, 40);
        check(source(frame, v(0, 0, 6), Z, 40).isEmpty(), "behind-camera source emits no faces");
        Random random = new Random(25163);
        boolean faded = false;
        for (int sample = 0; sample < 500; sample++) {
            Vector camera = v(random.nextDouble() * 6 - 3, random.nextDouble() * 6 - 3, random.nextDouble() * 8 - 4);
            Vector look = unit(v(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1));
            List<Face> result = source(frame, camera, look, 40);
            verifyCamera(result, camera, look);
            for (Face face : result) {
                Face fixed = original.stream().filter(f -> f.vertices().equals(face.vertices())).findFirst().orElseThrow();
                check(face.alpha() <= fixed.alpha(), "camera proximity only fades existing decoration");
                faded |= face.alpha() < fixed.alpha();
            }
        }
        check(faded, "camera traversal actually exercises the smooth near-eye fade");
    }

    private static void softenedCurvesAndSourceProximity() {
        Frame frame = frame(cores(ZERO, Z), MulticoreCanopyState.ARMED, 0, 200);
        List<Face> far = source(frame, v(0, 0, -4), Z, 40), near = source(frame, ZERO, Z, 40);
        check(far.size() == 96 && near.size() == 96, "near-source opacity preserves the complete proven curved silhouette");
        double nearCoverage = 0, unfadedCoverage = 0;
        for (int core = 0; core < 6; core++) {
            Vector radial = unit(frame.cores().get(core).center().subtract(Z.scale(2)));
            Vector tangent = Z.cross(radial);
            Face middle = far.get(core * 16 + 6), tip = far.get(core * 16 + 14);
            Vector midEnd = middle.c().add(middle.d()).scale(.5);
            check(midEnd.subtract(frame.cores().get(core).center()).dot(tangent) > .35,
                    "front-view petal centerline has a visible tangential bow rather than a straight spoke");
            check(tip.c().subtract(tip.d()).length() <= .05000001,
                    "energy sweep tapers to a narrow .05m tip without extending beyond the four-metre crown");
        }
        for (int i = 0; i < far.size(); i++) {
            Face remote = far.get(i), close = near.get(i);
            check(remote.vertices().equals(close.vertices()), "source proximity changes opacity only, never world geometry");
            check(close.alpha() <= remote.alpha() * .17, "two-metre source view has at most seventeen percent of distant decoration alpha");
            check(remote.alpha() >= 100, "six-metre third-person source retains readable luminous surfaces");
            if (remote.kind() == MulticoreCanopyMesh.RIM) {
                Face body = far.get(i - 1);
                check(remote.a().subtract(remote.b()).length() <= body.a().subtract(body.b()).length() * .131,
                        "soft ivory ridge is substantially narrower than its green energy wing");
                check(remote.uvA().u() >= .18 && remote.uvC().u() <= .82 + 1e-10
                                && remote.uvA().v() == 0 && remote.uvB().v() == 1,
                        "ridge UVs retain soft transverse glow edges and a continuous central longitudinal profile");
                check(remote.alpha() <= 112, "armed additive ridge cannot become a high-alpha white bar");
            }
            nearCoverage += projectedArea(close, ZERO) * close.alpha() / 255.0;
            unfadedCoverage += projectedArea(remote, ZERO) * remote.alpha() / 255.0;
        }
        check(nearCoverage > 0 && nearCoverage <= unfadedCoverage * .17,
                "opacity-weighted first-person screen coverage is reduced by at least eighty-three percent");
        verifyCamera(near, ZERO, Z);
    }

    private static void worldBorderTranslation() {
        Vector forward = unit(v(1, -.3, 2)), eye = v(0, 0, 0), camera = forward.scale(-6);
        Frame origin = frame(cores(eye, forward), MulticoreCanopyState.ARMED, 0, 200);
        List<Face> reference = source(origin, camera, forward, 40);
        for (Vector shift : List.of(v(29_999_980, 4000, 29_999_980), v(-29_999_980, -4000, -29_999_980))) {
            Frame translated = frame(cores(eye.add(shift), forward), MulticoreCanopyState.ARMED, 0, 200);
            List<Face> result = source(translated, camera.add(shift), forward, 40);
            check(reference.size() == result.size(), "world-border translation retains source topology");
            for (int i = 0; i < result.size(); i++) for (int p = 0; p < 4; p++) {
                Vector a = reference.get(i).vertices().get(p).subtract(camera);
                Vector b = result.get(i).vertices().get(p).subtract(camera.add(shift));
                close(a, b, "double camera subtraction preserves near-origin precision at the world border");
            }
            verifyCamera(result, camera.add(shift), forward);
        }
    }

    private static void invalidInputs() {
        Frame valid = frame(cores(ZERO, Z), MulticoreCanopyState.ARMED, 0, 200);
        check(source(null, ZERO, Z, 40).isEmpty() && MulticoreCanopyCrownGeometry.basis(null).isEmpty(),
                "null receipt produces no pose or geometry");
        for (Vector camera : new Vector[] {null, v(Double.NaN, 0, 0), v(Double.POSITIVE_INFINITY, 0, 0), v(30_000_001, 0, 0)})
            check(source(valid, camera, Z, 40).isEmpty(), "nonfinite or out-of-bounds camera is rejected");
        for (Vector look : new Vector[] {null, ZERO, v(Double.NaN, 0, 0), Z.scale(2)})
            check(source(valid, ZERO, look, 40).isEmpty(), "camera direction must already be a valid unit vector");
        check(source(valid, v(0, 0, -129), Z, 40).isEmpty(), "unsupported distant camera has no optional source geometry");
        for (List<Core> cores : List.of(
                List.of(new Core(0, v(Double.NaN, 0, 0), true)),
                List.of(new Core(0, Z, true), new Core(0, X, true)),
                List.of(new Core(6, Z, true)),
                List.of(new Core(0, ZERO, true), new Core(1, ZERO, true), new Core(2, ZERO, true)))) {
            Frame bad = frame(cores, MulticoreCanopyState.ARMED, 0, 200);
            check(source(bad, ZERO, Z, 40).isEmpty(), "malformed or degenerate indexed core geometry fails closed");
        }
        Frame warning = frame(valid.cores(), MulticoreCanopyState.WARNING, 13, 200);
        check(source(warning, ZERO, Z, 40).isEmpty(), "actual Frame validation rejects an invented warning duration");
    }

    private static void budgetsAndImmutability() {
        check(MulticoreCanopyCrownGeometry.SPAWNED_PARTICLES == 0
                        && MulticoreCanopyCrownGeometry.MAX_QUADS_PER_CONTEXT <= 192,
                "source is a bounded mesh with no particle creation");
        Frame frame = frame(cores(ZERO, Z), MulticoreCanopyState.ARMED, 0, 200);
        List<Face> faces = source(frame, v(0, 0, -6), Z, 40);
        var budget = new MulticoreCanopyMesh.Budget();
        check(budget.reserve(900), "existing classic geometry and labels reserve their budget first");
        int added = 0;
        for (int context = 0; context < 4; context++) {
            List<Face> ordered = MulticoreCanopyMesh.ordered(faces, v(0, 0, -6), Z, budget);
            check(ordered.size() == 96, "existing near clip never expands the camera-safe source mesh");
            added += ordered.size();
        }
        check(added == 384 && budget.used() == 1284, "four full crowns leave the existing global budget intact");
        boolean immutable = false;
        try { faces.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "returned source mesh is immutable");
    }

    private static void verifyCamera(List<Face> faces, Vector camera, Vector look) {
        check(faces.size() <= 96, "arbitrary camera never expands the source budget");
        Vector right = unit(look.cross(Math.abs(look.y()) > .9 ? X : Y)), up = right.cross(look);
        for (Face face : faces) {
            check(face.alpha() >= 4 && face.alpha() <= 255 && Float.isFinite(face.material().phase()), "material remains finite and bounded");
            check(FoldedMirrorMesh.nearClip(face, camera, look).size() == 1, "existing near clip cannot multiply a source face");
            for (var uv : face.uvs()) check(Double.isFinite(uv.u()) && Double.isFinite(uv.v())
                    && uv.u() >= 0 && uv.u() <= 1 && uv.v() >= 0 && uv.v() <= 1, "continuous petal UVs remain finite");
            for (int along = 0; along <= 4; along++) for (int across = 0; across <= 4; across++) {
                Vector point = lerp(lerp(face.a(), face.d(), along / 4.0), lerp(face.b(), face.c(), along / 4.0), across / 4.0);
                Vector relative = point.subtract(camera);
                double depth = relative.dot(look);
                check(point.finite() && depth > .20 - 1e-8, "whole petal segment stays beyond the near-eye exclusion plane");
                check(Math.max(Math.abs(relative.dot(right)), Math.abs(relative.dot(up))) >= depth * .024 - 1e-8,
                        "the complete petal surface preserves an empty aiming aperture");
            }
        }
    }

    private static List<Core> cores(Vector eye, Vector forward) {
        return MulticoreCanopyGeometry.cores(eye, forward).stream().map(c -> new Core(c.id(), c.center(), true)).toList();
    }
    private static Frame frame(List<Core> cores, int phase, int warning, int remaining) {
        return new Frame(ID, 1, phase, MulticoreCanopyState.PREPARED, remaining, v(40, 63, -10),
                cores, List.of(), List.of(), phase == MulticoreCanopyState.HELD, 0, warning);
    }
    private static List<Face> source(Frame frame, Vector camera, Vector look, float age) {
        return MulticoreCanopyCrownGeometry.source(frame, camera, look, age, frame == null ? 1 : frame.remainingTicks(), 0);
    }
    private static Vector midpoint(Face face) { return face.a().add(face.b()).add(face.c()).add(face.d()).scale(.25); }
    private static Vector lerp(Vector a, Vector b, double t) { return a.add(b.subtract(a).scale(t)); }
    private static double area(Face face) {
        return face.b().subtract(face.a()).cross(face.c().subtract(face.a())).length() * .5
                + face.c().subtract(face.a()).cross(face.d().subtract(face.a())).length() * .5;
    }
    private static double projectedArea(Face face, Vector camera) {
        List<Vector> p = face.vertices().stream().map(v -> v.subtract(camera))
                .map(v -> new Vector(v.x() / v.z(), v.y() / v.z(), 0)).toList();
        return p.get(1).subtract(p.get(0)).cross(p.get(2).subtract(p.get(0))).length() * .5
                + p.get(2).subtract(p.get(0)).cross(p.get(3).subtract(p.get(0))).length() * .5;
    }
    private static Vector v(double x, double y, double z) { return new Vector(x, y, z); }
    private static Vector unit(Vector v) { return v.scale(1 / v.length()); }
    private static void close(Vector a, Vector b, String message) { check(a.subtract(b).length() < 1e-6, message); }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
