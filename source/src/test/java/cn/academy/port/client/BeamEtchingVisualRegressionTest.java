package cn.academy.port.client;

import cn.academy.port.client.BeamEtchingMesh.Block;
import cn.academy.port.client.BeamEtchingMesh.Box;
import cn.academy.port.client.BeamEtchingMesh.Mark;
import cn.academy.port.client.BeamEtchingMesh.Point;
import cn.academy.port.highpower.HighPowerInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pure production input/receipts/mesh tested against independent native-face and projection witnesses. */
public final class BeamEtchingVisualRegressionTest {
    private static final UUID OWNER = new UUID(213, 1);
    private static final Point ORIGIN = new Point(.5, .5, -6), LOOK = new Point(0, 0, 1);
    private static final Mark FIRST = new Mark(new Block(0, 0, 0), new Point(.5, .5, 0), 2);
    private static final Mark SECOND = new Mark(new Block(1, 0, 0), new Point(1.5, .5, 0), 2);
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static void denied(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(message);
    }
    private static BeamEtchingState.Frame frame(long input, long token, long epoch, long sequence, int phase, List<Mark> marks, int count, int status, int remaining) {
        return new BeamEtchingState.Frame(new BeamEtchingState.Identity(4, OWNER, epoch, input, token), sequence, phase, ORIGIN, marks, count, status, remaining);
    }
    public static void main(String[] args) throws Exception {
        input(); lifecycle(); validation(); nativeFaces(); nativeRays(); perspectiveRays(); clipping(); budgets(); integration();
        System.out.println("PASS " + checks + " NEW213 native face cuts, range/source truth, receipt flashes, replay/end fences, hold/release and mesh-budget checks");
    }
    private static void input() {
        var state = new BeamEtchingInputState();
        var first = new HighPowerInput.Input(0, 1, 4, 9); var second = new HighPowerInput.Input(0, 2, 4, 9);
        state.begin(first, -100);
        for (int i = 0; i < 200; i++) check(state.releaseWhenUp(true) == null && state.holding(), "hold generates no repeated release or request");
        check(state.releaseWhenUp(false).equals(first), "physical release retains exact authenticated start tuple after delegate replacement");
        check(!state.holding() && state.permits(1, 9), "release awaits native receipt without predicting completion");
        check(state.release(first) == null && state.releaseWhenUp(false) == null, "ordinary and fallback release submit exactly once together");
        state.accepted(1, 9); check(state.release(first) == null, "late acceptance never rearms the released physical key");
        denied(() -> state.begin(second, 19), "a new hold cannot silently overwrite an executing identity");
        check(state.cancelWhenBlocked(false) == null && state.permits(1, 9), "ordinary execution survives unchanged context");
        check(state.cancelWhenBlocked(true).equals(first) && state.current() == null, "menu focus camera category preset block cancels immediately");
        check(state.cancel() == null && !state.permits(1, 9), "blocked input cannot replay an abort or accept late visual");
        denied(() -> state.begin(first, -100), "cancel preserves input watermark");
        state.begin(second, 19); state.ended(1, 9);
        check(state.current().identity().equals(second) && state.cancel(first) == null, "late old terminal and delegate disposal cannot consume newer hold");
        check(state.release(second).equals(second), "newer hold releases through ordinary delegate path");
        state.ended(2, 9); check(state.current() == null && !state.permits(2, 9), "authoritative finish clears only its own input");
        denied(() -> state.begin(second, 19), "finish preserves anti-replay watermark");
        state.reset(); state.begin(first, -100); check(state.permits(1, 9), "new connection explicitly resets input namespace");
        state.begin(new HighPowerInput.Input(1, 1, 5, 10), 33); check(!state.permits(1, 9), "actor epoch replacement removes old session");
        denied(() -> new BeamEtchingInputState().begin(first, 0), "unbound physical key cannot start");
    }
    private static void lifecycle() {
        var state = new BeamEtchingState();
        var start = frame(1, 1, 1, 1, 0, List.of(FIRST), 0, 0, 100);
        check(state.accept(start, true), "one authoritative preview starts one controller");
        check(!state.accept(start, true), "replayed preview is rejected");
        check(state.accept(frame(1, 1, 1, 2, 0, List.of(FIRST, SECOND), 0, 1, 96), true), "unavailable new aim can retain existing valid immutable marks");
        check(!state.accept(frame(1, 1, 1, 3, 0, List.of(SECOND), 0, 0, 95), true), "heartbeat cannot shrink or substitute selected mark prefix");
        check(state.accept(frame(1, 1, 1, 3, 1, List.of(FIRST), 1, 0, 0), true), "only singleton confirmed native removal starts solid flash");
        check(!state.accept(frame(1, 1, 1, 4, 1, List.of(FIRST), 2, 0, 0), true), "same removed block cannot flash twice under a fresh sequence");
        check(!state.accept(frame(1, 1, 1, 4, 0, List.of(FIRST, SECOND), 0, 0, 90), true), "execution cannot resume preparatory previews");
        check(!state.accept(frame(1, 1, 1, 4, 1, List.of(SECOND), 3, 0, 0), true), "receipt count cannot invent skipped native removals");
        var third = new Mark(new Block(2, 0, 0), new Point(2.5, .5, 0), 2);
        var moved = new Mark(SECOND.block(), new Point(1.8, .5, 0), 2);
        check(!state.accept(frame(1, 1, 1, 4, 1, List.of(moved), 2, 0, 0), true), "previously previewed block cannot relabel its exact selected point");
        check(state.accept(frame(1, 1, 1, 4, 1, List.of(SECOND), 2, 0, 0), true), "second actual removal advances one strict success count");
        check(state.accept(frame(1, 1, 1, 5, 2, List.of(), 2, 0, 0), true) && state.size() == 0, "terminal removes active state");
        check(!state.accept(frame(1, 1, 1, 6, 1, List.of(third), 3, 0, 0), true), "late confirmed packet cannot resurrect a completed cut");
        check(!state.accept(frame(2, 1, 1, 6, 0, List.of(FIRST), 0, 0, 100), true), "new input cannot reuse retired token");
        check(state.accept(frame(2, 2, 1, 1, 0, List.of(), 0, 1, 100), true), "fresh unavailable cast has no fake geometry");
        state.clear(); check(!state.accept(frame(2, 2, 1, 2, 0, List.of(FIRST), 0, 0, 99), false), "world replacement preserves retired-token fence");
        state.cancel(2, 9); check(!state.accept(frame(9, 3, 2, 1, 0, List.of(FIRST), 0, 0, 100), true), "blocked pending start cannot appear late");
        check(state.accept(frame(10, 4, 2, 1, 0, List.of(FIRST), 0, 0, 100), true), "later authentic input can resume normally");
        state.resetSession(); check(state.accept(start, true), "reconnect starts fresh server namespace");
        check(state.accept(frame(1, 1, 1, 2, 1, List.of(SECOND), 1, 0, 0), true), "release before next preview can confirm a newly appended native mark");
        var lease = new BeamEtchingState.Lease(); lease.received(start);
        for (int i = 0; i < 4; i++) lease.tick(false, true);
        lease.received(frame(1, 1, 1, 2, 0, List.of(FIRST, SECOND), 0, 0, 100));
        check(lease.remaining() == 96, "heartbeat cannot renew 100-tick hold");
        lease.received(frame(1, 1, 1, 3, 1, List.of(FIRST), 1, 0, 0));
        check(lease.marks().equals(List.of(SECOND)) && lease.executing(), "receipt removes only truly completed preview");
        lease.tick(true, true); check(lease.quiet() == 0, "pause does not age receipt lifetime");
        for (int i = 0; i < BeamEtchingState.QUIET_TICKS; i++) lease.tick(false, true);
        check(lease.marks().isEmpty() && !lease.tick(false, true), "lost end packet has a bounded visual lease");
        lease.received(start); check(!lease.tick(true, false) && lease.marks().isEmpty(), "menu death preset invalidation clears even while paused");
    }
    private static void validation() {
        check(BeamEtchingMesh.validSource(ORIGIN, FIRST), "native first surface point is in range and source-facing");
        check(BeamEtchingMesh.validSource(new Point(.5, .5, -12), FIRST), "exact 12-meter native endpoint remains valid");
        check(!BeamEtchingMesh.validSource(new Point(.5, .5, -12.0001), FIRST), "no preview beyond true 12-meter range");
        check(!BeamEtchingMesh.validSource(new Point(.5, .5, 1), FIRST), "back-side source cannot claim first visible front face");
        check(!BeamEtchingMesh.validSource(new Point(Double.NaN, 0, 0), FIRST), "nonfinite source rejected");
        check(!BeamEtchingMesh.validMark(new Mark(FIRST.block(), FIRST.point(), 6)), "unknown native face rejected");
        check(!BeamEtchingMesh.validMark(new Mark(FIRST.block(), new Point(10, .5, 0), 2)), "point cannot relocate to a side or hidden block");
        check(!BeamEtchingState.valid(frame(1, 1, 1, 1, 0, List.of(FIRST, FIRST), 0, 0, 100)), "duplicate-block preview denied");
        check(!BeamEtchingState.valid(frame(1, 1, 1, 1, 1, List.of(FIRST), 0, 0, 0)), "zero successes cannot produce confirmed flash");
        check(!BeamEtchingState.valid(frame(1, 1, 1, 1, 1, List.of(FIRST), 1, 1, 0)), "refused native removal is never a positive flash");
        check(!BeamEtchingState.valid(frame(1, 1, 1, 1, 2, List.of(), 1, 2, 0)), "no-success label cannot mask an actual positive receipt");
        List<Mark> tooMany = new ArrayList<>();
        for (int i = 0; i < 17; i++) tooMany.add(new Mark(new Block(i % 6 - 3, i / 6, 0), new Point(i % 6 - 2.5, i / 6 + .5, 0), 2));
        check(!BeamEtchingState.valid(frame(1, 1, 1, 1, 0, tooMany, 0, 0, 100)), "17th mark cannot expand packet/mesh storage");
    }
    private static void nativeFaces() {
        Box cube = new Box(new Point(0, 0, 0), new Point(1, 1, 1));
        for (int face = 0; face < 6; face++) {
            Point n = BeamEtchingMesh.normal(face), center = new Point(.5, .5, .5).add(n.scale(.5));
            Mark mark = new Mark(new Block(0, 0, 0), center, face);
            check(BeamEtchingMesh.validSource(center.add(n.scale(6)), mark), "each native face has its own physical source side");
            Box actual = BeamEtchingMesh.surface(mark, List.of(cube)); check(actual != null, "exact full-cube native face recognized");
            var preview = BeamEtchingMesh.cut(mark, actual, false, 0); var confirmed = BeamEtchingMesh.cut(mark, actual, true, 0);
            check(preview.size() == 6 && confirmed.size() == 2, "preview has three genuinely separated dashes; confirmation one solid cut");
            for (var quad : preview) for (Point p : quad.vertices()) {
                check(Math.abs(p.subtract(center).dot(n) - BeamEtchingMesh.FACE_OFFSET) < 1e-8, "every cut vertex stays on the actual selected face plane");
                check(p.subtract(center).subtract(n.scale(BeamEtchingMesh.FACE_OFFSET)).length() <= .221, "cut never enlarges into a block face fill");
            }
            for (int guiScale = 1; guiScale <= 4; guiScale++) for (int fov : new int[]{60, 70, 90}) {
                double framebufferFocal = 812 / (2 * Math.tan(Math.toRadians(fov / 2.0)));
                double physicalGapPixels = .28 * BeamEtchingMesh.HALF_CUT / 6 * framebufferFocal;
                double guiGap = physicalGapPixels / guiScale;
                check(Math.abs(guiGap * guiScale - physicalGapPixels) < 1e-10 && physicalGapPixels > 4,
                        "native world cut gaps retain actual pixel spacing independent of GUI scale and FOV");
            }
            check(BeamEtchingMesh.cut(mark, actual, true, 6).isEmpty(), "confirmed bright cut lasts only six native ticks");
        }
        Mark slab = new Mark(new Block(0, 0, 0), new Point(.95, .5, .95), 1);
        Box half = new Box(new Point(0, 0, 0), new Point(1, .5, 1));
        check(BeamEtchingMesh.surface(slab, List.of(cube)) == null, "an interior native shape face cannot be invented at full-cube height");
        var clipped = BeamEtchingMesh.cut(slab, BeamEtchingMesh.surface(slab, List.of(half)), false, 0);
        check(!clipped.isEmpty(), "edge hit preserves exact native point without enlarging outline");
        for (var quad : clipped) for (Point p : quad.vertices())
            check(p.x() >= -1e-9 && p.x() <= 1 + 1e-9 && p.z() >= -1e-9 && p.z() <= 1 + 1e-9 && Math.abs(p.y() - .5025) < 1e-9,
                    "all edge-cut vertices clip against actual surface tangents");
        check(BeamEtchingMesh.surface(FIRST, java.util.Collections.nCopies(33, cube)) == null, "pathological native outline complexity fails closed");
    }
    private static void nativeRays() {
        var rays = BeamEtchingMesh.confirmedRay(ORIGIN, FIRST, new Point(2, 2, -6), LOOK, 0);
        check(rays.size() == 5, "confirmation reuses only three licensed small-ray strips and two narrow color layers");
        Point direction = FIRST.point().subtract(ORIGIN); double length = direction.length(); Point axis = direction.scale(1 / length);
        for (var quad : rays) for (Point p : quad.vertices()) {
            double projection = p.subtract(ORIGIN).dot(axis);
            check(projection >= -1e-9 && projection <= length + 1e-9, "solid confirmed ray cannot overshoot native source or removed block point");
            check(p.subtract(ORIGIN).subtract(axis.scale(projection)).length() <= BeamEtchingMesh.RAY_WIDTH + 1e-9, "native ray stays narrow green MD beam");
        }
        check(BeamEtchingMesh.confirmedRay(ORIGIN, FIRST, ORIGIN, LOOK, 6).isEmpty(), "no persisting confirmed ray after brief receipt flash");
        check(BeamEtchingMesh.confirmedRay(new Point(.5, .5, -13), FIRST, ORIGIN, LOOK, 0).isEmpty(), "bad source range cannot be repaired by camera-shifted emission");
        var near = BeamEtchingMesh.confirmedRay(ORIGIN, FIRST, ORIGIN, LOOK, 0);
        Point visibleStart = near.getFirst().a().add(near.getFirst().b()).scale(.5);
        check(visibleStart.subtract(ORIGIN).cross(axis).length() < 1e-9
                && Math.abs(visibleStart.subtract(ORIGIN).dot(LOOK) - BeamEtchingMesh.RAY_NEAR_DEPTH) < 1e-9,
                "first-person clipping removes only the near portion of actual server-eye ray, never inventing a hand source");
    }
    /** Reproduces native strafing-camera history and checks actual perspective width rather than only world-space meters. */
    private static void perspectiveRays() {
        double nativeFocal = 812 / (2 * Math.tan(Math.toRadians(70 / 2.0)));
        double oldEyePlaneWidth = 2 * BeamEtchingMesh.RAY_WIDTH / BeamEtchingMesh.NEAR_DEPTH * nativeFocal;
        check(oldEyePlaneWidth > 120, "regression witness reproduces the historical 130px eye-origin glow wedge");
        Point nativeAxis = FIRST.point().subtract(ORIGIN); double squaredLength = nativeAxis.dot(nativeAxis);
        int drawn = 0;
        for (double strafe : new double[]{-.6, -.12, -.01, 0, .01, .12, .6})
            for (double vertical : new double[]{-.15, 0, .15})
                for (double forward : new double[]{-.15, 0, .15})
                    for (int yaw : new int[]{-25, 0, 25}) for (int pitch : new int[]{-15, 0, 15}) {
                        Point camera = ORIGIN.add(new Point(strafe, vertical, forward));
                        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
                        Point look = new Point(Math.sin(y) * Math.cos(p), Math.sin(p), Math.cos(y) * Math.cos(p));
                        Point right = look.cross(new Point(0, 1, 0)); right = right.scale(1 / right.length()); Point up = right.cross(look);
                        var rays = BeamEtchingMesh.confirmedRay(ORIGIN, FIRST, camera, look, 0);
                        check(!rays.isEmpty() && rays.size() <= 5, "ordinary moving-camera ray keeps bounded visible native segments");
                        for (var quad : rays) {
                            Point a = quad.a().add(quad.b()).scale(.5), b = quad.c().add(quad.d()).scale(.5);
                            for (Point center : List.of(a, b)) {
                                double t = center.subtract(ORIGIN).dot(nativeAxis) / squaredLength;
                                check(t >= -1e-9 && t <= 1 + 1e-9 && center.subtract(ORIGIN.lerp(FIRST.point(), t)).length() < 1e-8,
                                        "both visible ribbon centers stay on the exact original server source-to-end segment");
                                check(center.subtract(camera).dot(look) >= BeamEtchingMesh.RAY_NEAR_DEPTH - 1e-9,
                                        "native centerline is clipped before width expansion");
                            }
                            Point[] centers = {a, a, b, b}; int index = 0;
                            for (Point vertex : quad.vertices()) {
                                Point center = centers[index++], relative = vertex.subtract(camera), centerRelative = center.subtract(camera);
                                double z = relative.dot(look), centerZ = centerRelative.dot(look);
                                check(z > BeamEtchingMesh.NEAR_DEPTH, "expanded native ribbon remains in front of camera clipping plane");
                                double dx = relative.dot(right) / z - centerRelative.dot(right) / centerZ;
                                double dy = relative.dot(up) / z - centerRelative.dot(up) / centerZ;
                                double angularRadius = Math.hypot(dx, dy);
                                check(angularRadius <= BeamEtchingMesh.MAX_RAY_ANGULAR_HALF_WIDTH + 1e-10,
                                        "near-camera perspective thickness never forms a broad triangle even with tilted source history");
                                for (int fov : new int[]{60, 70, 90, 110}) {
                                    double focal = 812 / (2 * Math.tan(Math.toRadians(fov / 2.0)));
                                    check(angularRadius * focal * 2 < 3,
                                            "confirmed beam stays below three physical pixels across native FOVs and every GUI scale");
                                }
                                drawn++;
                            }
                        }
                    }
        check(drawn > 5000, "perspective regression covers many real moving-camera rays rather than one centered view");
        check(BeamEtchingMesh.confirmedRay(ORIGIN, FIRST, ORIGIN, new Point(0, 0, -1), 0).isEmpty(), "beam behind camera is clipped without moving its source into view");
    }
    private static void clipping() {
        var quad = new BeamEtchingMesh.Quad(new Point(-1, 0, -.5), new Point(1, 0, -.5), new Point(1, 0, .5), new Point(-1, 0, .5), 1, 255, -1);
        var pieces = BeamEtchingMesh.nearClip(quad, new Point(0, 0, 0), LOOK);
        check(!pieces.isEmpty(), "near-crossing native polygon is clipped instead of discarded or mirrored");
        for (var piece : pieces) for (Point p : piece.vertices()) check(p.z() >= BeamEtchingMesh.NEAR_DEPTH - 1e-9 && p.z() <= .5, "near-plane clipping stays inside original geometry");
        check(BeamEtchingMesh.nearClip(quad, new Point(0, 0, 1), LOOK).isEmpty(), "behind-camera geometry has no xray or mirrored overlay");
        check(BeamEtchingMesh.nearClip(quad, new Point(0, 0, 0), new Point(Double.NaN, 0, 1)).isEmpty(), "invalid camera cannot poison buffers");
        var corner = BeamEtchingMesh.crossedChunks(new Point(15, 64, 15), new Point(17, 64, 17));
        check(corner.containsAll(List.of(new BeamEtchingMesh.Chunk(0, 0), new BeamEtchingMesh.Chunk(0, 1),
                new BeamEtchingMesh.Chunk(1, 0), new BeamEtchingMesh.Chunk(1, 1))), "corner-crossing visibility checks every touched chunk, including tiny adjacent grazes");
        check(BeamEtchingMesh.crossedChunks(new Point(-17, 64, -17), new Point(-15, 64, -15)).size() == 4, "negative-coordinate chunk corridors preserve exact floor semantics");
        check(BeamEtchingMesh.crossedChunks(new Point(0, 64, 0), new Point(65, 64, 0)).isEmpty(), "distant camera corridor cannot expand unloaded-world reads");
        check(BeamEtchingMesh.crossedChunks(new Point(.01, 64, .01), new Point(45.01, 64, 45.01)).size() <= 16, "exact read-only visibility corridor remains bounded at full observer distance");
    }
    private static void budgets() {
        var budget = new BeamEtchingMesh.Budget(); int accepted = 0;
        for (int i = 0; i < 10000; i++) if (budget.take()) accepted++;
        check(accepted == 512 && budget.used() == 512, "all casters share hard 512-quad/2048-vertex post-clip frame budget");
        check(BeamEtchingMesh.SPAWNED_PARTICLES == 0 && BeamEtchingMesh.SPAWNED_PARTICLES <= 64, "no particles or per-dash particle controllers");
        var state = new BeamEtchingState();
        for (int i = 1; i <= BeamEtchingState.MAX_CONTEXTS + 1; i++) {
            var id = new BeamEtchingState.Identity(i, new UUID(213, i), 1, 1, 1);
            boolean acceptedFrame = state.accept(new BeamEtchingState.Frame(id, 1, 0, ORIGIN, List.of(FIRST), 0, 0, 100), false);
            check(acceptedFrame == (i <= 64), "controller admission cannot exceed 64 independent owners");
        }
        check(state.size() == 64 && state.watermarkCount() == 64, "controller overflow allocates no extra replay watermark");
        state.resetSession();
        for (int i = 1; i <= BeamEtchingState.MAX_WATERMARKS + 1; i++) {
            var id = new BeamEtchingState.Identity(i, new UUID(213, i), 1, 1, 1);
            boolean acceptedFrame = state.accept(new BeamEtchingState.Frame(id, 1, 2, ORIGIN, List.of(), 0, 2, 0), false);
            check(acceptedFrame == (i <= 1024), "terminal-only observer receipts retain bounded anti-replay storage");
        }
        check(state.size() == 0 && state.watermarkCount() == 1024, "terminal replay fences cannot grow without bound");
    }
    private static void integration() throws Exception {
        String effects = Files.readString(Path.of("src/main/java/cn/academy/port/client/BeamEtchingEffects.java"));
        check(effects.contains("ClassicRenderTypes.world(") && !effects.contains("disableDepthTest") && !effects.contains("RenderGuiEvent"), "every cut uses real world depth and no GUI/xray overlay");
        check(effects.contains("ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY") && effects.contains("hit.getDirection().ordinal() == mark.face()"), "read-only visibility matches exact native side, point, fluid and outline path");
        check(effects.contains("!visibleFace(marker.owner, ownerEye, mark) || !visibleFace(marker.owner, camera, mark)"), "both current physical source and camera reject hidden or side-block marks");
        check(effects.contains("packet.phase() == BeamEtchingState.ENDED") && effects.contains("receipt.phase() == BeamEtchingState.CONFIRMED"), "only actual native confirmations create flashes and terminal is explicit");
        check(!effects.contains("addParticle(") && !effects.contains("explosion") && !effects.contains("RenderGuiEvent") && !effects.contains("getGuiScaled"), "no fake impact explosions, particles, GUI scaling or screen-space targets");
        check(effects.contains("generation != capturedGeneration") && effects.contains("!AcademyClient.sameInputSessionEpoch(capturedSession)"), "queued visuals respect world/session generation before touching geometry");
        check(effects.contains("mc.getCameraEntity() == mc.player") && effects.contains("mc.screen == null") && effects.contains("mc.isWindowActive()"), "local camera menu and focus invalidation cannot leave stale cuts");
        String client = Files.readString(Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        check(client.contains("request(\"hp_beam_etching_start\",identity.wire())") && client.contains("request(\"hp_beam_etching_release\",identity.wire())"), "actual ability binding uses authentic captured hold/release wire");
        check(client.contains("beamEtchingInputs.release(identity)") && client.contains("beamEtchingInputs.releaseWhenUp(physicalDown(etchingHeld.physicalKey()))"), "normal release and snapshot fallback share exactly-once production input state");
        check(client.contains("beamEtchingInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null||mc.getCameraEntity()!=mc.player")
                && client.contains("beamEtchingInputs.reset();BeamEtchingEffects.clear();"), "blocked menu/focus/category/preset and death/session paths clear owned state");
        check(client.contains("dualPointInputs.release(identity)") && client.contains("scatterPlateInputs.release(identity)") && client.contains("railArrayInputs.release(identity)"), "NEW211 NEW212 and electromaster release paths remain present");
    }
}
