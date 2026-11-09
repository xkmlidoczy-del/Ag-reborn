package cn.academy.port.client;

import cn.academy.port.client.FoldedMirrorMesh.Face;
import cn.academy.port.client.MulticoreCanopyState.Core;
import cn.academy.port.client.MulticoreCanopyState.Frame;
import cn.academy.port.client.MulticoreCanopyState.Identity;
import cn.academy.port.client.MulticoreCanopyState.Segment;
import cn.academy.port.client.MulticoreCanopyState.Target;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Engine-free optional NEW251 emission contract; receipt replay/lease ownership stays in the state tests. */
public final class MulticoreCanopyEmissionGeometryRegressionTest {
    private static final Vector ZERO = v(0, 0, 0), X = v(1, 0, 0), Y = v(0, 1, 0), Z = v(0, 0, 1);
    private static final Identity ID = new Identity(7, UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"), 1, 2, 3);
    private static int checks;

    public static void main(String[] args) {
        mixedStatusReceipts();
        exactSpanAndSources();
        phasesAndOriginalAge();
        shortPathsAndWidths();
        nearEyeAndPostClipBudget();
        largeCoordinates();
        invalidInputs();
        budgetsAndImmutability();
        System.out.println("MulticoreCanopyEmissionGeometryRegressionTest: " + checks + " checks passed");
    }

    private static void mixedStatusReceipts() {
        int[] statuses = {MulticoreCanopyState.MISS, MulticoreCanopyState.REFUSED, MulticoreCanopyState.BLOCKED,
                MulticoreCanopyState.DAMAGED, MulticoreCanopyState.ABSORBED, MulticoreCanopyState.MISS};
        Frame frame = volley(statuses, MulticoreCanopyState.DAMAGED);
        Vector camera = v(0, 2, -10);
        List<Face> faces = emission(frame, camera, Z, 0);
        check(faces.size() == 42, "six actual rays create six ordinary overlays and only two actual impact responses");
        check(faces.equals(emission(withGlobalStatus(frame, MulticoreCanopyState.MISS), camera, Z, 0)),
                "contradictory global MISS or DAMAGED never changes any segment response");
        List<Target> unrelated = List.of(new Target(81, v(25, 4, 30), MulticoreCanopyState.DAMAGED));
        Frame target = new Frame(frame.id(), frame.sequence(), frame.phase(), frame.status(), frame.remainingTicks(),
                frame.ownerFeet(), frame.cores(), unrelated, frame.path(), frame.held(), frame.shotIndex(), frame.warningTicks());
        check(faces.equals(emission(target, camera, Z, 0)), "target status never fabricates an impact or source");
        for (int i = 0; i < statuses.length; i++) {
            Segment ray = frame.path().get(i);
            List<Face> end = faces.stream().filter(f -> onPlane(f, ray.end(), Z)).toList();
            long emberCount = faces.stream().filter(f -> f.color() == MulticoreCanopyEmissionGeometry.EMBER
                    && Math.abs(center(f).x() - ray.end().x()) < .5).count();
            check(emberCount == (statuses[i] == MulticoreCanopyState.DAMAGED ? 2 : 0),
                    "only a confirmed damage segment earns a bounded pair of cooling embers");
            if (statuses[i] == MulticoreCanopyState.DAMAGED) {
                check(end.size() == 2 && end.stream().allMatch(f -> f.kind() == MulticoreCanopyMesh.HIT_GLINT
                                && f.texture() == MulticoreCanopyMesh.WHITE_TEXTURE),
                        "only native segment HP loss earns the ivory two-stroke glint");
                for (Face f : end) close(center(f), ray.end(), "damage glint remains centered at the actual endpoint");
            } else if (statuses[i] == MulticoreCanopyState.ABSORBED) {
                check(end.size() == 2 && end.stream().allMatch(f -> f.kind() == MulticoreCanopyMesh.RIM
                                && f.texture() == MulticoreCanopyMesh.GLOW_TEXTURE && f.color() == MulticoreCanopyMesh.GREEN),
                        "absorption gets a separate small green split, never an HP glint");
                close(center(end.get(0)).add(center(end.get(1))).scale(.5), ray.end(),
                        "the absorption pair dissipates symmetrically about the actual endpoint");
                for (Face f : end) for (Vector vertex : f.vertices())
                    check(vertex.subtract(ray.end()).length() < .12, "initial absorption response is smaller than the HP glint");
            } else check(end.isEmpty(), "MISS, REFUSED and BLOCKED have no impact artwork despite global DAMAGED");
        }
        Frame absorbed = single(v(0, 0, 4), v(0, 0, 24), MulticoreCanopyState.ABSORBED);
        List<Face> first = emission(absorbed, camera, Z, 0).stream().filter(f -> onPlane(f, absorbed.path().getFirst().end(), Z)).toList();
        List<Face> later = emission(absorbed, camera, Z, 1).stream().filter(f -> onPlane(f, absorbed.path().getFirst().end(), Z)).toList();
        check(later.size() == 2 && later.getFirst().alpha() < first.getFirst().alpha(), "absorbed split dissipates on the original flash clock");
        check(center(later.getFirst()).subtract(absorbed.path().getFirst().end()).length()
                        > center(first.getFirst()).subtract(absorbed.path().getFirst().end()).length(),
                "absorption pieces move only apart in the endpoint plane");
    }

    private static void exactSpanAndSources() {
        Vector start = v(3, 2, 5), axis = unit(v(1, .3, 2)), end = start.add(axis.scale(31));
        Frame frame = single(start, end, MulticoreCanopyState.DAMAGED);
        List<Segment> original = List.copyOf(frame.path());
        for (Vector look : List.of(Z, unit(v(1, -.2, 1)), Y, axis)) {
            Vector camera = start.subtract(look.scale(12));
            List<Face> faces = emission(frame, camera, look, 0);
            check(!faces.isEmpty(), "valid visible receipt produces optional emission geometry");
            verifySpan(faces, frame.path().getFirst());
            for (Face face : faces) {
                if (face.kind() == MulticoreCanopyMesh.BEAM_HALO) {
                    Vector a = face.a().add(face.b()).scale(.5), b = face.c().add(face.d()).scale(.5);
                    double tA = a.subtract(start).dot(axis), tB = b.subtract(start).dot(axis);
                    close(a, start.add(axis.scale(tA)), "camera changes never displace the received centerline");
                    close(b, start.add(axis.scale(tB)), "far shoulder center remains on the received centerline");
                    check((Math.abs(tA) < 1e-6 && Math.abs(tB - 15.5) < 1e-6)
                                    || (Math.abs(tA - 15.5) < 1e-6 && Math.abs(tB - 31) < 1e-6),
                            "two shoulder sections cover exactly the received start/middle/end without extension");
                } else if (face.kind() == MulticoreCanopyMesh.ORB_GLOW) {
                    close(center(face), start, "source flare is centered on the actual path start");
                    check(onPlane(face, start, axis), "source flare cannot creep forward or backward along the ray");
                }
            }
        }
        for (float age : new float[] {.5f, 1, 2, 3}) {
            List<Face> faces = emission(frame, start.subtract(axis.scale(12)), axis, age);
            verifySpan(faces, frame.path().getFirst());
            double crest = faces.stream().filter(f -> f.color() == MulticoreCanopyEmissionGeometry.HOT_FILAMENT)
                    .mapToDouble(f -> f.c().add(f.d()).scale(.5).subtract(start).dot(axis)).min().orElseThrow();
            check(faces.stream().filter(f -> f.color() == MulticoreCanopyEmissionGeometry.HOT_FILAMENT)
                            .allMatch(f -> MulticoreCanopyMesh.materialFace(f).kind() == 3),
                    "the warm layer uses the existing tint-preserving shader branch");
            check(Math.abs(crest - 31 * (.5 + .10 * age)) < 1e-6,
                    "the thermal crest advances on the receipt clock along the exact ray");
        }
        check(original.equals(frame.path()), "geometry cannot change the receipt path or collision span");
        Core actual = frame.cores().getFirst(), extra = new Core(5, v(20, 2, 5), true);
        Frame partial = new Frame(frame.id(), 1, frame.phase(), frame.status(), 160, ZERO,
                List.of(new Core(actual.id(), actual.center(), false), extra), List.of(), frame.path(), true, 3, 0);
        List<Face> result = emission(partial, v(0, 0, -10), Z, 0);
        check(result.stream().filter(f -> f.kind() == MulticoreCanopyMesh.ORB_GLOW).count() == 1,
                "only the actual emitting core gets a flare even when another available core exists");
        close(center(result.stream().filter(f -> f.kind() == MulticoreCanopyMesh.ORB_GLOW).findFirst().orElseThrow()),
                actual.center(), "consumed source retains its authenticated center without selecting a future source");
    }

    private static void phasesAndOriginalAge() {
        Frame fired = single(v(0, 0, 5), v(0, 0, 25), MulticoreCanopyState.DAMAGED);
        for (int phase = MulticoreCanopyState.DEPLOY; phase <= MulticoreCanopyState.ENDED; phase++) {
            if (phase == MulticoreCanopyState.FIRE) continue;
            Frame frame = new Frame(ID, 1, phase, MulticoreCanopyState.DAMAGED,
                    phase == MulticoreCanopyState.ENDED ? 0 : 160, ZERO, fired.cores(), List.of(), List.of(), true, 3, 0);
            check(MulticoreCanopyState.valid(frame) && emission(frame, ZERO, Z, 0).isEmpty(),
                    "DEPLOY/ARMED/WARNING/HELD/ENDED never predict a firing source or ray");
        }
        for (float age : new float[] {-1, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 4, 40})
            check(emission(fired, ZERO, Z, age).isEmpty(), "invalid or expired original FIRE age creates no geometry");
        int previous = Integer.MAX_VALUE;
        for (float age : new float[] {0, .5f, 1, 1.5f, 2, 2.5f, 3, 3.5f}) {
            List<Face> faces = emission(fired, ZERO, Z, age);
            check(faces.equals(emission(fired, ZERO, Z, age)), "pause/repaint at the same age cannot advance animation or restart a flare");
            int alpha = faces.stream().mapToInt(Face::alpha).sum();
            check(alpha <= previous, "original flash opacity only decreases with age"); previous = alpha;
            check(faces.stream().allMatch(f -> f.material().phase() == age), "material phase is exactly the supplied original tick age");
            if (age >= MulticoreCanopyEmissionGeometry.SOURCE_FLASH_TICKS)
                check(faces.stream().noneMatch(f -> f.kind() == MulticoreCanopyMesh.ORB_GLOW
                                || f.kind() == MulticoreCanopyMesh.RIM), "brief source flare cannot survive its two-tick envelope");
        }
        var receiptState = new MulticoreCanopyState();
        check(receiptState.accept(fired, true) && !receiptState.accept(fired, true),
                "existing receipt fence, rather than cosmetic geometry, owns duplicate FIRE rejection");
        check(!emission(fired, ZERO, Z, 3.5f).isEmpty(), "accepted original four-tick tail can still fade after the source flare");
        Frame empty = new Frame(ID, 1, MulticoreCanopyState.FIRE, MulticoreCanopyState.DAMAGED, 160, ZERO,
                fired.cores(), List.of(), List.of(), false, 0, 0);
        check(emission(empty, ZERO, Z, 0).isEmpty(), "global damaged status with no actual segment never invents an emission");
    }

    private static void shortPathsAndWidths() {
        Frame subnormal = single(ZERO, v(1e-320, 0, 0), MulticoreCanopyState.DAMAGED);
        check(MulticoreCanopyState.valid(subnormal) && emission(subnormal, v(-10, -10, -10), Z, 0).isEmpty(),
                "valid subnormal-length receipts fade away without reciprocal overflow or invented direction");
        int previous = 0;
        for (double length : new double[] {1e-12, .01, .10, .4, 1, 2.5, 40, 96}) {
            Frame frame = single(v(0, 0, 8), v(0, 0, 8 + length), MulticoreCanopyState.DAMAGED);
            List<Face> faces = emission(frame, v(0, 0, -10), Z, 0);
            verifySpan(faces, frame.path().getFirst());
            int alpha = faces.stream().mapToInt(Face::alpha).sum();
            check(alpha >= previous, "short paths conservatively reduce opacity instead of becoming bright point flashes"); previous = alpha;
            for (Face face : faces) if (face.kind() == MulticoreCanopyMesh.BEAM_HALO)
                for (Vector point : face.vertices())
                    check(Math.hypot(point.x(), point.y()) <= Math.min(.35, length * .14) + 1e-8,
                            "all shoulder half-widths honor both the .35m cap and short-path scale");
        }
        Frame longRay = single(v(0, 0, 8), v(0, 0, 48), MulticoreCanopyState.MISS);
        double widest = emission(longRay, v(0, 0, -10), Z, 0).stream()
                .filter(f -> f.kind() == MulticoreCanopyMesh.BEAM_HALO).flatMap(f -> f.vertices().stream())
                .mapToDouble(p -> Math.hypot(p.x(), p.y())).max().orElseThrow();
        check(Math.abs(widest - .35) < 1e-8, "distant long rays actually reach the decorative shoulder width");
    }

    private static void nearEyeAndPostClipBudget() {
        Frame frame = single(v(-8, 0, 0), v(8, 0, 4), MulticoreCanopyState.DAMAGED);
        boolean clipped = false, faded = false;
        for (double x : new double[] {-8, -2, 0, 2, 8}) for (double y : new double[] {0, .05, .4, 2})
            for (Vector look : List.of(Z, X, Y, unit(v(.7, .1, 1)))) {
                Vector camera = v(x, y, 1);
                List<Face> faces = emission(frame, camera, look, .5f);
                verifySpan(faces, frame.path().getFirst());
                for (Face face : faces) {
                    check(face.alpha() > 0 && face.alpha() <= 255, "near-eye fades remain finite and nonnegative");
                    if (face.kind() == MulticoreCanopyMesh.BEAM_HALO) faded |= face.alpha() < 66;
                    clipped |= face.vertices().stream().anyMatch(p -> p.subtract(camera).dot(look) < FoldedMirrorMesh.NEAR_DEPTH);
                }
                var budget = new MulticoreCanopyMesh.Budget();
                int expected = faces.stream().mapToInt(f -> FoldedMirrorMesh.nearClip(f, camera, look).size()).sum();
                List<Face> finalFaces = MulticoreCanopyMesh.ordered(faces, camera, look, budget);
                check(finalFaces.size() == expected && budget.used() == expected,
                        "actual polygon output is charged after the existing near clip");
                for (Face face : finalFaces) for (Vector point : face.vertices())
                    check(point.finite() && point.subtract(camera).dot(look) >= FoldedMirrorMesh.NEAR_DEPTH - 1e-7,
                            "no final vertex bypasses the existing .08m near plane");
            }
        check(clipped, "camera traversal exercises actual near-plane clipping of raw shoulder faces");
        check(faded, "near-eye traversal exercises actual conservative decoration fading");
        Frame centered = single(v(0, 0, .1), v(0, 0, 30), MulticoreCanopyState.MISS);
        List<Face> close = emission(centered, ZERO, Z, 0);
        check(close.stream().noneMatch(f -> f.kind() == MulticoreCanopyMesh.ORB_GLOW || f.kind() == MulticoreCanopyMesh.RIM),
                "near-eye source flare fades away before it can cover the view");
        check(emission(centered, v(0, 0, 40), Z, 0).isEmpty(), "fully behind-camera receipt has no optional faces");
    }

    private static void largeCoordinates() {
        Vector start = v(-3, 2, -4), axis = unit(v(.3, -.1, 1));
        Vector end = start.add(axis.scale(12)), camera = v(-1, 0, -12);
        Frame origin = single(start, end, MulticoreCanopyState.ABSORBED);
        List<Face> reference = emission(origin, camera, Z, 1.25f);
        for (Vector shift : List.of(v(29_999_980, 4000, 29_999_980), v(-29_999_980, -4000, -29_999_980))) {
            Frame translated = single(start.add(shift), end.add(shift), MulticoreCanopyState.ABSORBED);
            List<Face> faces = emission(translated, camera.add(shift), Z, 1.25f);
            check(faces.size() == reference.size(), "world-border translation preserves emission topology");
            for (int i = 0; i < faces.size(); i++) {
                check(faces.get(i).alpha() == reference.get(i).alpha() && faces.get(i).material().equals(reference.get(i).material()),
                        "large coordinates retain opacity and pause-aware material state");
                for (int p = 0; p < 4; p++) close(faces.get(i).vertices().get(p).subtract(camera.add(shift)),
                        reference.get(i).vertices().get(p).subtract(camera), "world-double camera subtraction preserves small geometry");
            }
            verifySpan(faces, translated.path().getFirst());
        }
    }

    private static void invalidInputs() {
        Frame valid = single(v(0, 0, 4), v(0, 0, 12), MulticoreCanopyState.DAMAGED);
        check(emission(null, ZERO, Z, 0).isEmpty(), "null frame fails closed");
        for (Vector camera : new Vector[] {null, v(Double.NaN, 0, 0), v(Double.POSITIVE_INFINITY, 0, 0), v(30_000_001, 0, 0)})
            check(emission(valid, camera, Z, 0).isEmpty(), "invalid camera cannot produce optional geometry");
        for (Vector look : new Vector[] {null, ZERO, Z.scale(2), v(Double.NaN, 0, 0)})
            check(emission(valid, ZERO, look, 0).isEmpty(), "look direction must already be valid and unit length");
        Segment ray = valid.path().getFirst();
        for (Segment bad : List.of(new Segment(5, 0, ray.start(), ray.end(), 4),
                new Segment(0, 0, ray.start().add(X), ray.end(), 4), new Segment(0, 0, ray.start(), ray.start(), 4),
                new Segment(0, 0, ray.start(), ray.start().add(Z.scale(96.001)), 4),
                new Segment(0, 0, ray.start(), v(Double.NaN, 0, 0), 4),
                new Segment(0, 16, ray.start(), ray.end(), 4), new Segment(0, 0, ray.start(), ray.end(), 0))) {
            Frame malformed = withPath(valid, List.of(bad));
            check(!MulticoreCanopyState.valid(malformed) && emission(malformed, ZERO, Z, 0).isEmpty(),
                    "actual State.valid rejects invalid core association, span, status and slot");
        }
        check(emission(withPath(valid, List.of(ray, ray)), ZERO, Z, 0).isEmpty(), "duplicate emitted core IDs fail existing validation");
        Frame wrongPhase = new Frame(ID, 1, MulticoreCanopyState.WARNING, 4, 160, ZERO,
                valid.cores(), List.of(), valid.path(), false, 0, 0);
        check(emission(wrongPhase, ZERO, Z, 0).isEmpty(), "a path in an unauthenticated firing phase fails closed");
    }

    private static void budgetsAndImmutability() {
        check(MulticoreCanopyEmissionGeometry.SPAWNED_PARTICLES == 0
                        && MulticoreCanopyEmissionGeometry.MAX_QUADS_PER_RAY <= 10
                        && MulticoreCanopyEmissionGeometry.MAX_QUADS_PER_CONTEXT <= 60,
                "optional output has no spawned particles and a fixed small raw-face ceiling");
        Frame frame = volley(new int[] {4, 4, 4, 4, 4, 4}, 4);
        Vector camera = v(0, 2, -10);
        List<Face> faces = emission(frame, camera, Z, 0);
        check(faces.size() == 60, "six simultaneous damage rays exercise the exact worst-case 60-quad bound");
        for (Face face : faces) {
            check(face.texture() == MulticoreCanopyMesh.WHITE_TEXTURE || face.texture() == MulticoreCanopyMesh.GLOW_TEXTURE,
                    "optional geometry reuses only existing white and glow artwork");
            check(!MulticoreCanopyMesh.culled(face), "decorative envelopes use the existing two-sided material route");
            check(face.material().contactPower() == 0 && Float.isFinite(face.material().phase()),
                    "optional material cannot fabricate shield contact or nonfinite time");
            for (var uv : face.uvs()) check(Double.isFinite(uv.u()) && Double.isFinite(uv.v())
                            && uv.u() >= 0 && uv.u() <= 1 && uv.v() >= 0 && uv.v() <= 1,
                    "reused texture coordinates stay finite and bounded");
        }
        var budget = new MulticoreCanopyMesh.Budget();
        check(budget.reserve(MulticoreCanopyMesh.MAX_MESH_QUADS - 7), "ordinary classic geometry can reserve the shared budget first");
        List<Face> bounded = MulticoreCanopyMesh.ordered(faces, camera, Z, budget);
        check(bounded.size() == 7 && budget.used() == MulticoreCanopyMesh.MAX_MESH_QUADS,
                "optional artwork cannot exceed the existing shared post-clip budget");
        boolean immutable = false;
        try { faces.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "emission geometry is returned as an immutable list");
    }

    private static Frame volley(int[] statuses, int globalStatus) {
        List<Core> cores = new ArrayList<>(); List<Segment> rays = new ArrayList<>();
        for (int i = 0; i < statuses.length; i++) {
            Vector start = v((i - 2.5) * 3, 0, 4), end = start.add(Z.scale(20 + i));
            cores.add(new Core(i, start, false)); rays.add(new Segment(i, i, start, end, statuses[i]));
        }
        return new Frame(ID, 1, MulticoreCanopyState.FIRE, globalStatus, 160, ZERO, cores, List.of(), rays, false, 0, 0);
    }

    private static Frame single(Vector start, Vector end, int status) {
        return new Frame(ID, 1, MulticoreCanopyState.FIRE, MulticoreCanopyState.DAMAGED, 160, ZERO,
                List.of(new Core(0, start, false)), List.of(), List.of(new Segment(0, 0, start, end, status)), false, 0, 0);
    }

    private static Frame withGlobalStatus(Frame frame, int status) {
        return new Frame(frame.id(), frame.sequence(), frame.phase(), status, frame.remainingTicks(), frame.ownerFeet(),
                frame.cores(), frame.targets(), frame.path(), frame.held(), frame.shotIndex(), frame.warningTicks());
    }

    private static Frame withPath(Frame frame, List<Segment> path) {
        return new Frame(frame.id(), frame.sequence(), frame.phase(), frame.status(), frame.remainingTicks(), frame.ownerFeet(),
                frame.cores(), frame.targets(), path, frame.held(), frame.shotIndex(), frame.warningTicks());
    }

    private static void verifySpan(List<Face> faces, Segment ray) {
        Vector delta = ray.end().subtract(ray.start()); double length = delta.length(); Vector axis = delta.scale(1 / length);
        check(faces.size() <= MulticoreCanopyEmissionGeometry.MAX_QUADS_PER_RAY, "one ray cannot exceed its raw-face bound");
        for (Face face : faces) for (Vector point : face.vertices()) {
            double along = point.subtract(ray.start()).dot(axis);
            check(point.finite() && along >= -1e-6 && along <= length + 1e-6,
                    "every shoulder, source and contact vertex stays inside the authentic endpoint planes");
        }
    }

    private static boolean onPlane(Face face, Vector point, Vector axis) {
        return face.vertices().stream().allMatch(p -> Math.abs(p.subtract(point).dot(axis)) < 1e-6)
                && center(face).subtract(point).length() < .5;
    }
    private static List<Face> emission(Frame frame, Vector camera, Vector look, float age) {
        return MulticoreCanopyEmissionGeometry.emission(frame, camera, look, age);
    }
    private static Vector center(Face face) { return face.a().add(face.b()).add(face.c()).add(face.d()).scale(.25); }
    private static Vector v(double x, double y, double z) { return new Vector(x, y, z); }
    private static Vector unit(Vector vector) { return vector.scale(1 / vector.length()); }
    private static void close(Vector a, Vector b, String message) { check(a.subtract(b).length() < 1e-6, message); }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
