package cn.academy.port.client;

import cn.academy.port.client.InertialAnchorMesh.Point;
import cn.academy.port.client.InertialAnchorMesh.Role;
import java.util.List;
import java.util.UUID;

/** Pure lifecycle, receipt truth, world-space precision, camera safety and fixed allocation budgets. */
public final class InertialAnchorVisualRegressionTest {
    private static int checks;
    private static final UUID OWNER = new UUID(422, 1);
    private static final Point FEET = new Point(2, 64, 3), FORWARD = new Point(0, 0, 1);
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static InertialAnchorState.Frame frame(UUID owner, long input, long token, long sequence, int phase, boolean grounded) {
        return new InertialAnchorState.Frame(new InertialAnchorState.Identity(19, owner, 71, input, token),
                sequence, phase, FEET, FORWARD, phase == InertialAnchorState.KNOCKBACK ? new Point(2, 64.8, 4) : FEET,
                phase == InertialAnchorState.ENDED ? 0 : 3, grounded);
    }
    private static InertialAnchorState.Frame frame(long sequence, int phase, boolean grounded) { return frame(OWNER, 1, 10, sequence, phase, grounded); }
    private static long count(InertialAnchorMesh.Shape shape, Role role) { return shape.strokes().stream().filter(s -> s.role() == role).count(); }
    private static List<InertialAnchorMesh.Stroke> strokes(InertialAnchorMesh.Shape shape, Role role) {
        return shape.strokes().stream().filter(s -> s.role() == role).toList();
    }
    public static void main(String[] args) {
        InertialAnchorState state = new InertialAnchorState();
        check(state.accept(frame(1, InertialAnchorState.ACTIVE, true), true), "server activation accepted");
        var grounded = InertialAnchorMesh.shape(state.current(OWNER), FEET, true, 0);
        check(count(grounded, Role.ANCHOR) == 16 && count(grounded, Role.DOWN) == 12, "two open arcs and four downward arrows");
        check(count(grounded, Role.LANDING) == 0 && count(grounded, Role.PRESSURE) == 0, "activation cannot synthesize impact or landing");
        for (var stroke : strokes(grounded, Role.ANCHOR)) {
            check(Math.abs(stroke.from().x() - FEET.x()) > .14 && Math.abs(stroke.to().x() - FEET.x()) > .14,
                    "front/back arc gaps stay open and readable");
            check(Math.abs(stroke.from().y() - FEET.y() - .035) < 1e-10, "ground stance stays low");
        }
        check(!state.accept(frame(1, InertialAnchorState.ACTIVE, true), true), "duplicate receipt ignored");
        check(state.accept(frame(2, InertialAnchorState.ACTIVE, false), true), "walking to falling is a legal continuing state");
        Point movingFeet = new Point(2.5, 70, 3.5);
        var airborne = InertialAnchorMesh.shape(state.current(OWNER), movingFeet, false, 0);
        check(count(airborne, Role.LANDING) == 0 && count(airborne, Role.PRESSURE) == 0, "falling shows no projected floor or unconfirmed success");
        check(airborne.strokes().getFirst().alpha() < grounded.strokes().getFirst().alpha(), "airborne waiting has distinct opacity");
        for (var stroke : airborne.strokes()) check(stroke.from().y() >= movingFeet.y() + .18 && stroke.to().y() >= movingFeet.y() + .18,
                "airborne notation follows current body and never sticks to previous floor");
        check(state.accept(frame(3, InertialAnchorState.KNOCKBACK, false), true), "settled positive knockback event accepted");
        var pressure = InertialAnchorMesh.shape(state.current(OWNER), movingFeet, false, 0);
        check(count(pressure, Role.PRESSURE) == 4 && count(pressure, Role.LANDING) == 0, "pressure event has a short feet-directed cue only");
        for (var stroke : strokes(pressure, Role.PRESSURE)) check(stroke.from().y() > stroke.to().y(), "pressure travels down toward feet");
        state.tick(false, c -> true);
        check(state.accept(frame(4, InertialAnchorState.ACTIVE, false), true) && state.current(OWNER).pressureAge() == 1,
                "keepalive does not restart pressure age");
        var savedPressure = state.current(OWNER).pressure();
        check(savedPressure.sequence() == 3, "keepalive preserves original immutable event receipt");
        check(state.accept(frame(5, InertialAnchorState.LANDED, true), true), "real landing receipt accepted");
        var landed = InertialAnchorMesh.shape(state.current(OWNER), FEET, true, 0);
        check(count(landed, Role.LANDING) == 20 && count(landed, Role.PRESSURE) == 4, "bounded concurrent settled-event cues");
        check(landed.vertexCount() == InertialAnchorMesh.MAX_CONTEXT_VERTICES, "worst-case owner count measured exactly");
        var ring = strokes(landed, Role.LANDING);
        for (var stroke : ring) check(Math.abs(stroke.from().y() - 64.022) < 1e-10 && stroke.from().y() == stroke.to().y(), "native landing ring is low and horizontal");
        for (int i = 0; i < 200; i++) state.tick(true, c -> true);
        check(state.current(OWNER).quiet() == 0 && state.current(OWNER).landingAge() == 0, "pause freezes cosmetic clocks");
        state.tick(false, c -> true);
        check(state.accept(frame(6, InertialAnchorState.ACTIVE, false), true), "subsequent jump retains controller");
        var movedRing = strokes(InertialAnchorMesh.shape(state.current(OWNER), movingFeet, false, 0), Role.LANDING);
        check(movedRing.getFirst().from().y() == ring.getFirst().from().y(), "contact ring remains at real world contact after body moves");
        check(movedRing.getFirst().alpha() < ring.getFirst().alpha(), "heartbeat never refreshes landing opacity");
        for (int i = 1; i < InertialAnchorState.LANDING_TICKS; i++) state.tick(false, c -> true);
        var expiredCue = InertialAnchorMesh.shape(state.current(OWNER), movingFeet, false, 0);
        check(count(expiredCue, Role.LANDING) == 0 && count(expiredCue, Role.PRESSURE) == 0 && state.size() == 1,
                "cue expiry does not end the persistent stance");
        for (int i = state.current(OWNER).quiet(); i < InertialAnchorState.QUIET_TICKS - 1; i++) state.tick(false, c -> true);
        check(state.size() == 1, "lease remains until deadline");
        check(state.tick(false, c -> true).size() == 1 && state.size() == 0, "quiet lease loss is returned once for authenticated abort");
        check(!state.accept(frame(7, InertialAnchorState.ACTIVE, true), true), "expired identity cannot revive with a higher sequence");
        check(state.accept(frame(OWNER, 2, 11, 1, InertialAnchorState.ACTIVE, true), true), "fresh activation identity admitted");
        state.clear();
        check(!state.accept(frame(OWNER, 2, 11, 2, InertialAnchorState.ACTIVE, true), true), "reload/body replacement keeps replay fence");
        state.cancel(71, 3);
        check(!state.accept(frame(OWNER, 3, 12, 1, InertialAnchorState.LANDED, true), true), "cancel before acknowledgement fences delayed success");
        check(state.accept(frame(OWNER, 3, 12, 1, InertialAnchorState.ACTIVE, true), false), "local cancellation does not fence remote identities");
        check(state.tick(true, c -> false).size() == 1 && state.size() == 0, "context loss retires even during pause");
        check(!state.accept(frame(OWNER, 4, 11, 1, InertialAnchorState.ACTIVE, true), false), "new input cannot reuse an older token");
        state.resetSession();
        check(state.accept(frame(1, InertialAnchorState.ACTIVE, true), true), "new connection resets session watermarks");
        check(state.accept(frame(2, InertialAnchorState.ENDED, false), true) && state.size() == 0, "authoritative end clears controller");
        check(!state.accept(frame(3, InertialAnchorState.KNOCKBACK, true), true), "ended context cannot replay success");

        var id = frame(1, InertialAnchorState.ACTIVE, true).id();
        check(!InertialAnchorState.valid(new InertialAnchorState.Frame(id, 1, 0, FEET, new Point(0, 0, 0), FEET, 4, true)), "zero facing rejected");
        check(InertialAnchorState.valid(new InertialAnchorState.Frame(id, 1, 0, FEET, new Point(0, 1, 0), FEET, 4, true)), "looking straight up preserves active receipt ownership");
        for (Point facing : new Point[]{new Point(0, 1, 0), new Point(0, -1, 0), new Point(.6, .8, 0)}) {
            var pitched = new InertialAnchorState.Frame(id, 1, 0, FEET, facing, FEET, 4, true);
            var shape = InertialAnchorMesh.shape(new InertialAnchorState.Controller(pitched, true, 0, null, 6, null, 8), FEET, true, 0);
            check(shape.vertexCount() == grounded.vertexCount(), "pitch never hides the stance");
            for (var stroke : strokes(shape, Role.ANCHOR)) check(Math.abs(stroke.from().y() - 64.035) < 1e-10, "pitched facing keeps foot arcs level");
        }
        check(!InertialAnchorState.valid(new InertialAnchorState.Frame(id, 1, 3, FEET, FORWARD, FEET, 1, true)), "ended receipt cannot retain live allowance");
        check(!InertialAnchorState.valid(new InertialAnchorState.Frame(id, 1, 2, FEET, FORWARD, new Point(2, 64, 7), 1, true)), "landing contact is bounded near actual feet");
        check(!InertialAnchorState.valid(new InertialAnchorState.Frame(id, 1, 0, FEET, FORWARD, FEET, 5, true)), "hit allowance bounded");
        check(!InertialAnchorState.valid(new InertialAnchorState.Frame(id, 1, 2, FEET, FORWARD, FEET, 4, false)), "airborne active update cannot claim settled landing");
        check(!InertialAnchorState.valid(new InertialAnchorState.Frame(id, 1, 0, new Point(Double.NaN, 64, 0), FORWARD, FEET, 4, true)), "nonfinite receipt rejected");
        Point far = new Point(29_999_999, 64, -29_999_999);
        var farFrame = new InertialAnchorState.Frame(id, 1, 0, far, FORWARD, far, 4, true);
        var controller = new InertialAnchorState.Controller(farFrame, false, 0, null, 6, null, 8);
        var farShape = InertialAnchorMesh.shape(controller, far, true, .5);
        check(farShape.vertexCount() == grounded.vertexCount(), "large coordinates preserve full stance geometry");
        for (var stroke : farShape.strokes()) {
            check(stroke.from().finite() && stroke.to().finite() && stroke.from().distanceSquared(stroke.to()) < 1,
                    "far geometry remains finite and short");
            var core = InertialAnchorMesh.profile(stroke, true); var soft = InertialAnchorMesh.profile(stroke, false);
            check(core.gray() > soft.gray() && core.alpha() > soft.alpha() && core.width() < soft.width(), "gray soft edge and readable white core");
        }
        check(InertialAnchorMesh.relative(new Point(far.x() + .03125, far.y() + .015625, far.z() + .0625), far)
                .equals(new Point(.03125, .015625, .0625)), "double camera subtraction retains sub-float detail");
        var crossing = new InertialAnchorMesh.Stroke(new Point(0, 0, -1), new Point(0, 0, 1), .02, 200, Role.DOWN);
        var clipped = InertialAnchorMesh.clipNear(crossing, FORWARD, .01);
        check(clipped != null && clipped.from().z() >= .09 - 1e-12, "near-eye crossing clips beyond width-expanded near plane");
        check(InertialAnchorMesh.clipNear(new InertialAnchorMesh.Stroke(new Point(0, 0, -2), new Point(0, 0, -1), .02, 200, Role.DOWN), FORWARD, .01) == null,
                "behind-camera stroke rejected");
        check(InertialAnchorMesh.clipNear(crossing, new Point(0, 0, 0), .01) == null && InertialAnchorMesh.clipNear(crossing, FORWARD, Double.NaN) == null,
                "invalid clipping inputs fail closed");
        check(InertialAnchorMesh.shape(controller, far, true, Double.NaN).vertexCount() == 0, "nonfinite render time rejected");
        var budget = new InertialAnchorMesh.Budget();
        for (int i = 0; i < 512; i++) check(budget.take(4), "quad budget admitted");
        check(!budget.take(4) && !budget.take(-1) && !budget.take(Integer.MAX_VALUE) && budget.used() == 2048, "global budget cannot overflow");
        check(InertialAnchorMesh.MAX_CONTEXTS * InertialAnchorMesh.MAX_CONTEXT_VERTICES <= InertialAnchorMesh.MAX_VERTICES
                && InertialAnchorMesh.MAX_PARTICLES == 0, "all four worst-case contexts fit without particles");
        InertialAnchorState bounded = new InertialAnchorState();
        for (int i = 2; i <= 5; i++) check(bounded.accept(frame(new UUID(422, i), 1, 10, 1, 0, true), false), "remote context admitted");
        check(!bounded.accept(frame(new UUID(422, 6), 1, 10, 1, 0, true), false) && bounded.size() == 4, "fifth remote context rejected");
        check(bounded.accept(frame(1, 0, true), true) && bounded.size() == 4 && bounded.current(OWNER).local(), "local control has priority over remote saturation");
        check(!bounded.accept(frame(new UUID(422, 2), 1, 10, 2, 0, true), false), "retired remote stays replay fenced");
        bounded.clear();
        for (int i = 6; i <= 1024; i++) check(bounded.accept(frame(new UUID(422, i), 1, 10, 1, 3, true), false), "bounded tombstone admitted");
        check(!bounded.accept(frame(new UUID(422, 1025), 1, 10, 1, 3, true), false) && bounded.watermarkCount() == 1024,
                "watermark saturation never evicts older replay protection");
        System.out.println("InertialAnchorVisualRegressionTest: " + checks + " checks passed");
    }
}
