package cn.academy.port.client;

import cn.academy.port.client.CirculationReliefMesh.Point;
import cn.academy.port.client.CirculationReliefMesh.Role;
import java.util.List;
import java.util.UUID;

/** Receipt truth, real-sink geometry, lag recovery, replay fences and all-or-nothing budgets. */
public final class CirculationReliefVisualRegressionTest {
    private static int checks;
    private static final UUID OWNER = new UUID(432, 1);
    private static final Point FEET = new Point(2, 64, 3), SINK = new Point(2, 60, 3), FORWARD = new Point(0, 0, 1);
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static CirculationReliefState.Frame frame(UUID owner, long input, long token, long sequence, int phase, int remaining, boolean ground) {
        return new CirculationReliefState.Frame(new CirculationReliefState.Identity(19, owner, 71, input, token), sequence,
                phase, FEET, FORWARD, ground ? SINK : FEET, new Point(3, 65, 3), remaining, ground);
    }
    private static CirculationReliefState.Frame frame(long sequence, int phase, int remaining, boolean ground) {
        return frame(OWNER, 1, 10, sequence, phase, remaining, ground);
    }
    private static long count(CirculationReliefMesh.Shape shape, Role role) { return strokes(shape, role).size(); }
    private static List<CirculationReliefMesh.Stroke> strokes(CirculationReliefMesh.Shape shape, Role role) {
        return shape.strokes().stream().filter(s -> s.role() == role).toList();
    }
    public static void main(String[] args) {
        var state = new CirculationReliefState();
        check(state.accept(frame(1, CirculationReliefState.ACTIVE, 4, true), true), "authoritative activation");
        var active = CirculationReliefMesh.shape(state.current(OWNER), FEET, 0);
        check(count(active, Role.SIDE) == 24 && count(active, Role.QUOTA_READY) == 4, "two open arcs and four receipt quota marks");
        check(count(active, Role.DESCENT) == 0, "activation does not invent absorption");
        for (var stroke : strokes(active, Role.SIDE)) for (Point end : List.of(stroke.from(), stroke.to())) {
            double angle = Math.toDegrees(Math.atan2(end.x() - FEET.x(), end.z() - FEET.z()));
            if (angle < 0) angle += 360;
            check(angle >= 60 - 1e-10 && angle <= 120 + 1e-10 || angle >= 240 - 1e-10 && angle <= 300 + 1e-10,
                    "arcs remain strictly inside the side sectors");
            check(end.y() < FEET.y() + 1.0 && Math.abs(end.x() - FEET.x()) > .67, "open front/rear and first-person center");
        }
        check(!state.accept(frame(1, CirculationReliefState.ACTIVE, 4, true), true), "duplicate activation fenced");
        check(state.accept(frame(2, CirculationReliefState.PAUSED, 4, false), true), "missing sink pauses");
        var paused = CirculationReliefMesh.shape(state.current(OWNER), FEET, 0);
        check(count(paused, Role.DESCENT) == 0 && count(paused, Role.QUOTA_READY) == 4, "pause preserves actual quota without fake sink");
        check(paused.strokes().getFirst().alpha() < active.strokes().getFirst().alpha(), "paused arcs visibly dim");
        check(state.accept(frame(3, CirculationReliefState.ABSORBED, 3, true), true), "positive absorption receipt");
        var absorbed = CirculationReliefMesh.shape(state.current(OWNER), FEET, 0);
        check(count(absorbed, Role.DESCENT) == 12 && count(absorbed, Role.QUOTA_READY) == 3 && count(absorbed, Role.QUOTA_USED) == 1,
                "only receipt consumes quota and creates the admitted side descent stream");
        check(absorbed.vertexCount() == CirculationReliefMesh.MAX_CONTEXT_VERTICES, "worst case complete geometry budget");
        var descent = strokes(absorbed, Role.DESCENT);
        for (var stroke : descent) {
            check(stroke.from().y() > stroke.to().y(), "stream strictly descends");
            check(stroke.from().x() > FEET.x(), "only actual contact side descends; opposite arc stays quiet");
        }
        check(descent.get(11).to().equals(SINK), "stream terminates at actual receipt sink");
        state.tick(false, c -> true);
        check(state.accept(frame(4, CirculationReliefState.ACTIVE, 3, true), true) && state.current(OWNER).absorptionAge() == 1,
                "heartbeat cannot refresh event age");
        Point moving = new Point(3, 65, 4);
        var moved = strokes(CirculationReliefMesh.shape(state.current(OWNER), moving, .25), Role.DESCENT);
        check(moved.get(11).to().equals(SINK), "moving body never drags the real sink");
        check(moved.getFirst().alpha() < descent.getFirst().alpha(), "success naturally fades");
        for (int i = 0; i < 100; i++) state.tick(true, c -> true);
        check(state.current(OWNER).absorptionAge() == 1 && state.current(OWNER).quiet() == 0, "game pause freezes cosmetic clocks");
        for (int i = 1; i < CirculationReliefState.ABSORPTION_TICKS; i++) state.tick(false, c -> true);
        check(count(CirculationReliefMesh.shape(state.current(OWNER), moving, 0), Role.DESCENT) == 0, "heartbeat cannot extend descent effect");
        check(state.current(OWNER).frame().remainingHits() == 3, "elapsed ticks never predict rolling quota refund");
        for (int i = 0; i < 200; i++) check(state.tick(false, c -> true).isEmpty(), "network silence cannot emit destructive loss");
        check(state.size() == 1 && CirculationReliefMesh.shape(state.current(OWNER), FEET, 0).vertexCount() == 0, "stale controller is retained but hidden");
        check(!state.accept(frame(3, CirculationReliefState.ABSORBED, 2, true), true), "hidden controller retains sequence watermark");
        check(state.accept(frame(5, CirculationReliefState.ACTIVE, 4, true), true), "later heartbeat recovers exact same token after lag");
        check(count(CirculationReliefMesh.shape(state.current(OWNER), FEET, 0), Role.QUOTA_READY) == 4, "only authoritative heartbeat refunds quota");
        check(state.accept(frame(6, CirculationReliefState.PAUSED, 0, true), true), "exhausted quota is distinct from missing ground");
        check(count(CirculationReliefMesh.shape(state.current(OWNER), FEET, 0), Role.QUOTA_USED) == 4, "all four used ticks stay visible");
        check(state.tick(true, c -> false).size() == 1 && state.size() == 0, "explicit owner invalidity retires even while paused");
        check(!state.accept(frame(7, CirculationReliefState.ACTIVE, 4, true), true), "invalidated owner cannot revive");
        check(state.accept(frame(OWNER, 2, 11, 1, 0, 4, true), true), "newer activation accepted");
        state.clear();
        check(!state.accept(frame(OWNER, 2, 11, 2, 0, 4, true), true), "reload preserves replay fence");
        state.cancel(71, 3);
        check(!state.accept(frame(OWNER, 3, 12, 1, 0, 4, true), true), "pre-ack cancellation fences delayed receipt");
        check(state.accept(frame(OWNER, 3, 12, 1, 0, 4, true), false), "local fence does not reject remote identity");
        check(!state.accept(frame(OWNER, 4, 11, 1, 0, 4, true), false), "new input cannot reuse old server token");
        state.resetSession();
        check(state.accept(frame(1, 0, 4, true), true) && state.accept(frame(2, 3, 0, false), true), "connection reset and matching authoritative end");
        check(!state.accept(frame(3, 1, 3, true), true), "ended controller cannot replay absorption");

        var id = frame(1, 0, 4, true).id();
        check(!CirculationReliefState.valid(new CirculationReliefState.Frame(id, 1, 0, FEET, FORWARD, new Point(2, 59.9, 3), FEET, 4, true)), "sink beyond four metres rejected");
        check(!CirculationReliefState.valid(new CirculationReliefState.Frame(id, 1, 0, FEET, FORWARD, new Point(2.1, 60, 3), FEET, 4, true)), "unrelated off-axis sink rejected");
        check(!CirculationReliefState.valid(frame(1, 1, 4, true)), "absorbed cannot claim unchanged full quota");
        check(!CirculationReliefState.valid(frame(1, 0, 4, false)), "active cannot claim missing sink");
        check(!CirculationReliefState.valid(frame(1, 0, 0, true)), "exhausted controller must indicate paused");
        check(!CirculationReliefState.valid(new CirculationReliefState.Frame(id, 1, 0, FEET, new Point(0, 1, 0), SINK, FEET, 4, true)), "server side sectors require horizontal facing");
        Point far = new Point(29_999_999, 64, -29_999_999);
        var farFrame = new CirculationReliefState.Frame(id, 1, 0, far, FORWARD, far, far, 4, true);
        var controller = new CirculationReliefState.Controller(farFrame, false, 0, null, 10);
        for (var stroke : CirculationReliefMesh.shape(controller, far, .5).strokes()) {
            check(stroke.from().finite() && stroke.to().finite() && stroke.from().distanceSquared(stroke.to()) < 1, "far-coordinate geometry stays finite");
            var core = CirculationReliefMesh.profile(stroke, true); var soft = CirculationReliefMesh.profile(stroke, false);
            check(core.width() >= .04 && soft.width() > core.width() && core.gray() > soft.gray() && core.alpha() > soft.alpha(), "readable white core and gray soft shoulder");
        }
        check(CirculationReliefMesh.relative(new Point(far.x() + .03125, far.y(), far.z() + .0625), far).equals(new Point(.03125, 0, .0625)), "double camera subtraction preserves world precision");
        var crossing = new CirculationReliefMesh.Stroke(new Point(0, 0, -1), new Point(0, 0, 1), .046, 200, Role.SIDE);
        var clipped = CirculationReliefMesh.clipNear(crossing, FORWARD, .023);
        check(clipped != null && clipped.from().z() >= .103 - 1e-12, "near-eye crossing clipped before extrusion");
        check(CirculationReliefMesh.clipNear(crossing, new Point(0, 0, 0), .023) == null, "invalid view vector fails closed");
        check(CirculationReliefMesh.shape(controller, far, Double.NaN).vertexCount() == 0, "invalid render time rejected");
        var budget = new CirculationReliefMesh.Budget();
        for (int i = 0; i < 4; i++) check(budget.take(CirculationReliefMesh.MAX_CONTEXT_VERTICES), "four complete enhancements admitted");
        int before = budget.used();
        check(!budget.take(CirculationReliefMesh.MAX_VERTICES - before + 1) && budget.used() == before, "overflow discards whole enhancement atomically");
        check(!budget.take(-1) && !budget.take(Integer.MAX_VALUE), "budget arithmetic cannot overflow");
        check(before <= 2048 && CirculationReliefMesh.MAX_PARTICLES == 0, "global vertex ceiling and zero particles");
        var bounded = new CirculationReliefState();
        for (int i = 2; i <= 5; i++) check(bounded.accept(frame(new UUID(432, i), 1, 10, 1, 0, 4, true), false), "remote admitted");
        check(!bounded.accept(frame(new UUID(432, 6), 1, 10, 1, 0, 4, true), false) && bounded.size() == 4, "fifth controller rejected");
        check(bounded.accept(frame(1, 0, 4, true), true) && bounded.size() == 4, "local toggle survives remote saturation");
        bounded.clear();
        for (int i = 6; i <= 1024; i++) check(bounded.accept(frame(new UUID(432, i), 1, 10, 1, 3, 0, false), false), "bounded replay tombstone");
        check(!bounded.accept(frame(new UUID(432, 1025), 1, 10, 1, 3, 0, false), false) && bounded.watermarkCount() == 1024, "fences never evict older replay protection");
        System.out.println("CirculationReliefVisualRegressionTest: " + checks + " checks passed");
    }
}
