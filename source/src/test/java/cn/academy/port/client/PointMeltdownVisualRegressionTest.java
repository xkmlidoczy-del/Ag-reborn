package cn.academy.port.client;

import cn.academy.port.client.BeamEtchingMesh.Block;
import cn.academy.port.client.BeamEtchingMesh.Box;
import cn.academy.port.client.BeamEtchingMesh.Mark;
import cn.academy.port.client.BeamEtchingMesh.Point;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Independent receipt, world-face, camera projection and full-scene witnesses for NEW233. No Minecraft boot. */
public final class PointMeltdownVisualRegressionTest {
    private static final UUID OWNER = new UUID(233, 1);
    private static final Point ORIGIN = new Point(.5, .5, -12), LOOK = new Point(0, 0, 1);
    private static final Mark TARGET = new Mark(new Block(0, 0, 0), new Point(.5, .5, 0), 2);
    private static final Mark OTHER = new Mark(new Block(1, 0, 0), new Point(1.5, .5, 0), 2);
    private static final Box CUBE = new Box(new Point(0, 0, 0), new Point(1, 1, 1));
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static PointMeltdownState.Frame frame(long input, long token, long epoch, long sequence, int phase,
                                                  List<Mark> marks, int focusTicks, int status, int remaining) {
        return new PointMeltdownState.Frame(new PointMeltdownState.Identity(4, OWNER, epoch, input, token), sequence,
                phase, ORIGIN, marks, focusTicks, status, remaining);
    }
    public static void main(String[] args) throws Exception {
        lifecycle(); clocks(); validation(); geometry(); perspective(); largeCoordinates(); budgets(); integration();
        System.out.println("PASS " + checks + " NEW233 authoritative focus, immutable target, lifecycle, native-face, camera and budget checks");
    }
    private static void lifecycle() {
        var state = new PointMeltdownState();
        var preview = frame(1, 1, 1, 1, 0, List.of(TARGET), 0, 0, 100);
        check(state.accept(preview, true), "one authoritative hold admits one context");
        check(!state.accept(preview, true), "duplicate sequence cannot redraw or extend the hold");
        check(state.accept(frame(1, 1, 1, 2, 0, List.of(OTHER), 0, 0, 96), true), "aim can move before release");
        check(state.accept(frame(1, 1, 1, 3, 0, List.of(), 0, 1, 92), true), "invalid current aim clears the harmless preview");
        check(!state.accept(frame(1, 1, 1, 4, 0, List.of(TARGET), 0, 0, 100), true), "late heartbeat cannot replenish the server hold lease");
        check(state.accept(frame(1, 1, 1, 4, 3, List.of(TARGET), 0, 0, 10), true), "release freezes its actual native target");
        check(!state.accept(frame(1, 1, 1, 5, 3, List.of(OTHER), 1, 0, 9), true), "focus cannot retarget a second obstacle");
        Mark shifted = new Mark(TARGET.block(), new Point(.7, .5, 0), 2);
        check(!state.accept(frame(1, 1, 1, 5, 3, List.of(shifted), 1, 0, 9), true), "even another point on the same block is a different frozen target");
        check(state.accept(frame(1, 1, 1, 5, 3, List.of(TARGET), 5, 0, 5), true), "missing intermediate receipts do not invalidate authoritative progress");
        check(!state.accept(frame(1, 1, 1, 6, 3, List.of(TARGET), 4, 0, 6), true), "a newer sequence cannot reverse real focus progress");
        check(!state.accept(frame(1, 1, 1, 6, 0, List.of(TARGET), 0, 0, 80), true), "released focus never becomes another preview");
        var walking = new PointMeltdownState.Frame(new PointMeltdownState.Identity(4, OWNER, 1, 1, 1), 6, 3,
                new Point(1.5, .5, -11), List.of(TARGET), 9, 0, 1);
        check(state.accept(walking, true), "ordinary source movement preserves the frozen target");
        check(!state.accept(frame(1, 1, 1, 7, 1, List.of(OTHER), 10, 0, 0), true), "success cannot invent another block after focus");
        check(state.accept(frame(1, 1, 1, 7, 1, List.of(TARGET), 10, 0, 0), true), "only exact actual-removal receipt confirms the focus");
        check(!state.accept(frame(1, 1, 1, 8, 1, List.of(TARGET), 10, 0, 0), true), "another sequence cannot repeat one removal flash");
        check(state.accept(frame(1, 1, 1, 8, 2, List.of(), 10, 0, 0), true) && state.size() == 0, "natural end retires its controller");
        check(!state.accept(frame(1, 1, 1, 9, 1, List.of(TARGET), 10, 0, 0), true), "late packets cannot resurrect an ended focus");
        check(!state.accept(frame(2, 1, 1, 10, 0, List.of(TARGET), 0, 0, 100), false), "new input cannot reuse an ended token");
        check(state.accept(frame(2, 2, 1, 1, 3, List.of(TARGET), 2, 0, 8), false), "observer joining during focus can read the first authenticated receipt");
        state.clear();
        check(!state.accept(frame(2, 2, 1, 2, 1, List.of(TARGET), 10, 0, 0), false), "world replacement preserves the old token tombstone");
        state.cancel(2, 9);
        check(!state.accept(frame(9, 3, 2, 1, 0, List.of(TARGET), 0, 0, 100), true), "canceled pending input cannot arrive after menu opening");
        check(state.accept(frame(10, 4, 2, 1, 0, List.of(TARGET), 0, 0, 100), true), "a later input in the same actor epoch remains usable");
        state.resetSession(); check(state.accept(preview, true), "new connection explicitly resets the receipt namespace");
    }
    private static void clocks() {
        var lease = new PointMeltdownState.Lease();
        lease.received(frame(1, 1, 1, 1, 0, List.of(TARGET), 0, 0, 100));
        for (int i = 0; i < 4; i++) lease.tick(false, true);
        lease.received(frame(1, 1, 1, 2, 0, List.of(TARGET), 0, 0, 99));
        check(lease.remaining() == 96, "a heartbeat never renews elapsed preview time");
        lease.received(frame(1, 1, 1, 3, 3, List.of(TARGET), 3, 0, 7));
        for (int i = 0; i < 200; i++) check(lease.tick(true, true), "paused world keeps the valid visual lease");
        check(lease.remaining() == 7 && lease.quiet() == 0 && lease.focusTicks() == 3, "pause freezes all local counters");
        for (int i = 0; i < 7; i++) lease.tick(false, true);
        check(lease.focusTicks() == 3 && lease.focusing() && lease.marks().size() == 1, "local time cannot manufacture focus completion or success");
        for (int i = 7; i < PointMeltdownState.QUIET_TICKS; i++) lease.tick(false, true);
        check(lease.marks().isEmpty() && !lease.tick(false, true), "lost terminal receipt has a bounded visual lifetime");
        lease.received(frame(1, 1, 1, 4, 3, List.of(TARGET), 4, 0, 6));
        check(!lease.tick(true, false) && lease.marks().isEmpty(), "menu death preset and camera invalidation clears even while paused");
        lease = new PointMeltdownState.Lease();
        lease.received(frame(1, 1, 1, 1, 0, List.of(TARGET), 0, 0, 1)); lease.tick(false, true);
        check(lease.marks().isEmpty(), "preview cannot persist beyond its native maximum hold time");
    }
    private static void validation() {
        check(PointMeltdownMesh.validSource(new Point(.5, .5, -36), TARGET), "native range includes exactly 36 metres");
        check(!PointMeltdownMesh.validSource(new Point(.5, .5, -36.001), TARGET), "source cannot extend the approved range");
        check(!PointMeltdownMesh.validSource(new Point(.5, .5, 1), TARGET), "rear-side source cannot claim the selected front face");
        check(!PointMeltdownMesh.validSource(new Point(Double.NaN, 0, 0), TARGET), "nonfinite source fails closed");
        check(!PointMeltdownState.valid(frame(1, 1, 1, 1, 0, List.of(TARGET, OTHER), 0, 0, 100)), "a second target cannot enter the packet");
        check(!PointMeltdownState.valid(frame(1, 1, 1, 1, 3, List.of(), 2, 0, 8)), "focus cannot invent an endpoint without a mark");
        check(!PointMeltdownState.valid(frame(1, 1, 1, 1, 3, List.of(TARGET), 2, 0, 10)), "focus clock must match its remaining real server ticks");
        check(!PointMeltdownState.valid(frame(1, 1, 1, 1, 1, List.of(TARGET), 9, 0, 0)), "partial focus cannot claim a confirmed removal");
        check(!PointMeltdownState.valid(frame(1, 1, 1, 1, 1, List.of(TARGET), 10, 1, 0)), "unavailable removal cannot produce success");
        check(!PointMeltdownState.valid(frame(1, 1, 1, 1, 2, List.of(TARGET), 10, 0, 0)), "an ended receipt carries no invented geometry");
    }
    private static void geometry() {
        double previous = Double.POSITIVE_INFINITY;
        for (int focus = 0; focus <= 10; focus++) {
            double radius = PointMeltdownMesh.ringRadius(focus);
            check(radius < previous && radius >= .045 - 1e-9 && radius <= .18, "focus ring converges monotonically on a small native endpoint");
            previous = radius;
        }
        for (int face = 0; face < 6; face++) {
            Point n = BeamEtchingMesh.normal(face), center = new Point(.5, .5, .5).add(n.scale(.5));
            Mark target = new Mark(new Block(0, 0, 0), center, face); Point camera = center.add(n.scale(6));
            for (int focus : new int[]{0, 5, 10}) for (boolean confirmed : new boolean[]{false, true}) {
                var quads = PointMeltdownMesh.focal(target, CUBE, focus, true, confirmed, 0, camera);
                check(!quads.isEmpty() && quads.size() <= 84, "clear ring, heat shoulder and core have a fixed small face budget");
                for (var quad : quads) for (Point vertex : quad.vertices()) {
                    Point relative = vertex.subtract(center);
                    check(Math.abs(relative.dot(n) - PointMeltdownMesh.FACE_OFFSET) < 1e-9, "ring stays on its exact face plane for all six native sides");
                    check(relative.subtract(n.scale(PointMeltdownMesh.FACE_OFFSET)).length() <= .184 + 1e-9, "no target-sized panel is hidden in the shoulder or core");
                }
            }
            check(PointMeltdownMesh.focal(target, CUBE, 10, false, true, 6, camera).isEmpty(), "confirmed flash ends in six unpaused ticks");
            check(PointMeltdownMesh.focal(target, CUBE, 5, true, false, 0, center.add(n.scale(.5))).isEmpty(), "near-eye target geometry fades away instead of filling the screen");
        }
        Mark slab = new Mark(new Block(0, 0, 0), new Point(.98, .5, .98), 1);
        Box half = new Box(new Point(0, 0, 0), new Point(1, .5, 1)); Point camera = new Point(.98, 3, .98);
        check(PointMeltdownMesh.focal(slab, CUBE, 5, true, false, 0, camera).isEmpty(), "full-cube face cannot substitute for the actual slab outline");
        var quads = PointMeltdownMesh.focal(slab, half, 5, true, false, 0, camera);
        check(!quads.isEmpty(), "near-edge partial block keeps its genuine clipped focus marker");
        for (var quad : quads) for (Point p : quad.vertices()) check(p.x() >= 0 && p.x() <= 1 && p.z() >= 0 && p.z() <= 1
                && Math.abs(p.y() - .503) < 1e-9, "partial face clips every ring and heat vertex to its actual wall geometry");
        check(PointMeltdownMesh.nearFade(TARGET.point(), new Point(.5, .5, -.54)) == 0
                && PointMeltdownMesh.nearFade(TARGET.point(), new Point(.5, .5, -2)) == 1, "near-eye opacity has explicit invisible and fully readable ranges");
    }
    private static void perspective() {
        int vertices = 0;
        Point axis = TARGET.point().subtract(ORIGIN);
        for (double strafe : new double[]{-.6, -.1, 0, .1, .6}) for (double rise : new double[]{-.15, 0, .15})
            for (int yaw : new int[]{-25, 0, 25}) for (int pitch : new int[]{-15, 0, 15}) {
                Point camera = ORIGIN.add(new Point(strafe, rise, 0)); double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
                Point look = new Point(Math.sin(y) * Math.cos(p), Math.sin(p), Math.cos(y) * Math.cos(p));
                Point right = look.cross(new Point(0, 1, 0)); right = right.scale(1 / right.length()); Point up = right.cross(look);
                var ray = PointMeltdownMesh.ray(ORIGIN, TARGET, camera, look, true, false, 0);
                check(!ray.isEmpty() && ray.size() <= 5, "moving first-person camera retains a bounded thin original ray");
                for (var quad : ray) {
                    Point a = quad.a().add(quad.b()).scale(.5), b = quad.c().add(quad.d()).scale(.5);
                    Point[] centers = {a, a, b, b}; int i = 0;
                    for (Point vertex : quad.vertices()) {
                        Point center = centers[i++], relative = vertex.subtract(camera), c = center.subtract(camera);
                        double t = center.subtract(ORIGIN).dot(axis) / axis.dot(axis);
                        check(t >= -1e-9 && t <= 1 + 1e-9 && center.subtract(ORIGIN.lerp(TARGET.point(), t)).length() < 1e-8,
                                "rendering preserves the exact source-to-target segment without a fabricated hand source");
                        check(c.dot(look) >= .2 - 1e-9 && relative.dot(look) >= .08, "centerline clips before ribbon expansion");
                        double dx = relative.dot(right) / relative.dot(look) - c.dot(right) / c.dot(look);
                        double dy = relative.dot(up) / relative.dot(look) - c.dot(up) / c.dot(look);
                        check(Math.hypot(dx, dy) <= .002 + 1e-10, "moving historical source never creates a perspective wedge");
                        vertices++;
                    }
                }
            }
        check(vertices > 2000, "projection witness covers actual varied camera motion");
        check(PointMeltdownMesh.ray(ORIGIN, TARGET, ORIGIN, new Point(0, 0, -1), true, false, 0).isEmpty(), "behind-camera ray is never mirrored into the viewport");
        var corner = BeamEtchingMesh.crossedChunks(new Point(15, 64, 15), new Point(17, 64, 17));
        check(corner.size() == 4 && corner.contains(new BeamEtchingMesh.Chunk(0, 1)), "loaded LOS checks even corner-grazed neighboring chunks");
        check(BeamEtchingMesh.crossedChunks(new Point(-17, 64, -17), new Point(-15, 64, -15)).size() == 4, "negative chunk boundaries use floor semantics");
    }
    private static void largeCoordinates() {
        double offset = 29_999_990;
        Point origin = ORIGIN.add(new Point(offset, 0, offset)), camera = origin.add(new Point(.125, .2, 0));
        Mark target = new Mark(new Block((int)offset, 0, (int)offset), TARGET.point().add(new Point(offset, 0, offset)), 2);
        var far = PointMeltdownMesh.ray(origin, target, camera, LOOK, true, false, 0);
        var near = PointMeltdownMesh.ray(ORIGIN, TARGET, ORIGIN.add(new Point(.125, .2, 0)), LOOK, true, false, 0);
        check(!far.isEmpty() && far.size() == near.size(), "far-world ray preserves the same topology");
        for (int i = 0; i < far.size(); i++) for (int j = 0; j < 4; j++) {
            Point actual = far.get(i).vertices().get(j).subtract(camera);
            Point expected = near.get(i).vertices().get(j).subtract(ORIGIN.add(new Point(.125, .2, 0)));
            check(actual.subtract(expected).length() < 2e-8, "world subtraction in double retains millimetre detail at world border");
            check(Math.abs((float)actual.x() - expected.x()) < 2e-7, "only camera-relative coordinates are narrowed to float");
        }
        float lossy = (float)target.point().x() - (float)camera.x();
        check(Math.abs(lossy - target.point().subtract(camera).x()) > .1, "witness would fail if absolute world coordinates were cast first");
    }
    private static void budgets() {
        var budget = new PointMeltdownMesh.Budget(); int accepted = 0;
        for (int i = 0; i < 10000; i++) if (budget.take()) accepted++;
        check(accepted == 512 && budget.vertices() == 2048, "all contexts share the 2048-vertex post-clipping scene ceiling");
        check(PointMeltdownMesh.SPAWNED_PARTICLES == 0, "focusing and confirmation spawn zero particles");
        var state = new PointMeltdownState();
        for (int i = 1; i <= 5; i++) {
            var id = new PointMeltdownState.Identity(i, new UUID(233, i), 1, 1, 1);
            check(state.accept(new PointMeltdownState.Frame(id, 1, 3, ORIGIN, List.of(TARGET), 2, 0, 8), false) == (i <= 4),
                    "controller admission is bounded to four actual owners");
        }
        check(state.size() == 4 && state.watermarkCount() == 4, "rejected fifth controller leaves no extra active state or watermark");
        state.resetSession();
        for (int i = 1; i <= 1025; i++) {
            var id = new PointMeltdownState.Identity(i, new UUID(233, i), 1, 1, 1);
            check(state.accept(new PointMeltdownState.Frame(id, 1, 2, ORIGIN, List.of(), 0, 2, 0), false) == (i <= 1024),
                    "terminal-only replay tombstones remain bounded");
        }
    }
    private static void integration() throws Exception {
        String effects = Files.readString(Path.of("src/main/java/cn/academy/port/client/PointMeltdownEffects.java"));
        check(effects.contains("ClassicRenderTypes.world(") && !effects.contains("disableDepthTest") && !effects.contains("RenderGuiEvent"), "all focus geometry participates in native world depth");
        check(effects.contains("ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY") && effects.contains("hit.getDirection().ordinal() == mark.face()")
                && effects.contains("hit.getLocation().distanceToSqr(end) <= .0001"), "visibility validates exact outline, fluid, side and point");
        check(effects.contains("!visibleFace(marker.owner, ownerEye, mark)") && effects.contains("!visibleFace(marker.owner, marker.state.origin(), mark)")
                && effects.contains("!visibleFace(marker.owner, camera, mark)"), "both live source and historical source plus observer reject intervening walls");
        check(effects.contains("generation != capturedGeneration") && effects.contains("!AcademyClient.sameInputSessionEpoch(capturedSession)")
                && effects.contains("mc.getConnection() != capturedConnection"), "queued receipts capture world, actor, connection and input-session identity");
        check(effects.contains("old.owner != owner") && effects.contains("world.getEntity(marker.owner.getId()) == marker.owner"), "entity ID reuse cannot inherit another actor's visual");
        check(effects.contains("mc.screen == null") && effects.contains("mc.isWindowActive()") && effects.contains("mc.getCameraEntity() == mc.player"), "menu window and camera changes invalidate local focus");
        check(effects.contains("else if (receipt.phase() == PointMeltdownState.CONFIRMED)") && effects.contains("if (receipt.status() != PointMeltdownState.READY) flashes.clear()"), "only removal confirmation creates flash; cancel and unavailable terminal clear it");
        check(effects.contains("clipped.a().subtract(camera)") && effects.contains("(float)p.x()"), "production emission subtracts camera in double before float");
        check(!effects.contains("addParticle(") && !effects.contains("hurt(") && !effects.contains("destroyBlock(") && !effects.contains("explosion"), "receipt renderer owns neither effects spam nor authoritative world mutation");
    }
}
