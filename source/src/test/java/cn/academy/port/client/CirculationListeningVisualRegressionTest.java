package cn.academy.port.client;

import cn.academy.port.client.CirculationListeningMesh.Point;
import cn.academy.port.client.CirculationListeningMesh.Role;
import cn.academy.port.client.CirculationListeningState.Cue;
import cn.academy.port.client.CirculationListeningState.Frame;
import cn.academy.port.client.CirculationListeningState.Identity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Executable production state and geometry coverage; no renderer mocks and no synthetic sensor inputs. */
public final class CirculationListeningVisualRegressionTest {
    private static final UUID OWNER = new UUID(433, 1);
    private static final Point EYE = new Point(2, 65.62, 3), FORWARD = new Point(0, 0, 1);
    private static final Cue ARROW = new Cue(0, 0, 3, 0), RUNNER = new Cue(4, -1, 1, 1);
    private static int checks;
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    private static Frame frame(long input, long token, long sequence, int phase, List<Cue> cues) {
        return new Frame(new Identity(19, OWNER, 71, input, token), sequence, phase, cues);
    }
    private static Frame frame(long sequence, List<Cue> cues) { return frame(1, 10, sequence, 0, cues); }
    private static int vertices(CirculationListeningState state) {
        return CirculationListeningMesh.shape(state.current(OWNER), EYE, 0).vertexCount();
    }
    public static void main(String[] args) {
        var state = new CirculationListeningState();
        check(state.accept(frame(1, List.of()), true), "empty authoritative listening heartbeat accepted");
        check(state.size() == 1 && vertices(state) == 0, "empty space retains status with no invented marks");
        check(!state.accept(frame(1, List.of(ARROW)), true), "same sequence cannot inject a false cue");
        check(!state.accept(frame(2, List.of(ARROW)), false), "remote or broadcast receipt rejected");
        check(state.accept(frame(2, List.of(ARROW, RUNNER)), true), "fresh authenticated cues accepted");
        int fresh = vertices(state);
        check(fresh == 96, "two cues each use six strokes and two four-vertex layers");
        for (int i = 0; i < 200; i++) state.tick(true, c -> true);
        check(vertices(state) == fresh && state.current(OWNER).cueAge() == 0, "native paused time cannot age cues");
        int bright = CirculationListeningMesh.shape(state.current(OWNER), EYE, 0).strokes().getFirst().alpha();
        state.tick(false, c -> true);
        check(CirculationListeningMesh.shape(state.current(OWNER), EYE, .5).strokes().getFirst().alpha() < bright, "short stale cue fades rather than persisting");
        for (int i = 1; i < 4; i++) state.tick(false, c -> true);
        check(vertices(state) == 0 && state.size() == 1, "four stale ticks hide marks without terminating listening");
        for (int i = 0; i < 1000; i++) check(state.tick(false, c -> true).isEmpty(), "silence never produces destructive owner loss");
        check(!state.accept(frame(2, List.of(ARROW)), true), "quiet controller retains replay sequence");
        check(state.accept(frame(3, List.of(ARROW)), true) && vertices(state) > 0, "late newer same-token heartbeat recovers after lag");
        check(state.accept(frame(4, List.of()), true) && vertices(state) == 0, "empty heartbeat removes prior cue immediately");
        check(state.accept(frame(1, 10, 5, CirculationListeningState.PAUSED, List.of()), true), "empty paused status accepted");
        check(vertices(state) == 0, "paused cannot draw disturbance cues");
        check(!CirculationListeningState.valid(frame(1, 10, 6, CirculationListeningState.PAUSED, List.of(ARROW))), "paused cannot claim a cue");
        check(state.accept(frame(1, 10, 6, CirculationListeningState.ENDED, List.of()), true), "exact owner end accepted");
        check(state.size() == 0 && !state.accept(frame(7, List.of(ARROW)), true), "terminal end blocks any late cue");
        check(!state.accept(frame(2, 10, 8, 0, List.of(ARROW)), true), "new input cannot reuse old token");
        check(state.accept(frame(2, 11, 1, 0, List.of(ARROW)), true), "fresh input and token may start");
        state.clear();
        check(!state.accept(frame(2, 11, 2, 0, List.of(ARROW)), true), "reload clear keeps retired identity fenced");
        state.cancel(71, 3);
        check(!state.accept(frame(3, 12, 1, 0, List.of(ARROW)), true), "pre-ack input invalidation fences delayed receipt");
        check(state.accept(frame(4, 13, 1, 0, List.of(ARROW)), true), "new later input accepted after cancellation");
        check(state.tick(true, c -> false).size() == 1 && state.size() == 0, "death/world/body invalidity clears while paused");
        check(!state.accept(frame(4, 13, 2, 0, List.of(ARROW)), true), "body invalidity is terminal for old receipt");
        state.resetSession();
        check(state.accept(frame(1, List.of(ARROW)), true), "new connection resets replay fences");
        check(!state.accept(new Frame(new Identity(20, OWNER, 71, 1, 10), 2, 0, List.of(ARROW)), true), "same token with changed entity is rejected");
        check(!state.accept(new Frame(new Identity(19, new UUID(433, 2), 71, 1, 10), 1, 0, List.of(ARROW)), true), "single owner slot cannot admit another player");

        for (Cue invalid : List.of(new Cue(-1, 0, 1, 0), new Cue(16, 0, 1, 0), new Cue(0, -2, 1, 0),
                new Cue(0, 2, 1, 0), new Cue(0, 0, 0, 0), new Cue(0, 0, 4, 0), new Cue(0, 0, 1, -1), new Cue(0, 0, 1, 2)))
            check(!CirculationListeningState.valid(frame(2, List.of(invalid))), "out-of-wire-range cue rejected: " + invalid);
        var many = new ArrayList<Cue>();
        for (int i = 0; i < 8; i++) many.add(new Cue(i * 2, i % 3 - 1, i % 3 + 1, i % 2));
        var maxFrame = frame(2, many);
        var maxController = new CirculationListeningState.Controller(maxFrame, 0, 0);
        var maxShape = CirculationListeningMesh.shape(maxController, EYE, 0);
        check(maxShape.vertexCount() == 384 && maxShape.vertexCount() <= 512, "all eight authentic cues stay below hard global ceiling");
        check(CirculationListeningMesh.MAX_CONTEXTS == 1 && CirculationListeningMesh.MAX_PARTICLES == 0, "one local controller, zero particles");
        many.add(ARROW);
        check(!CirculationListeningState.valid(frame(3, many)), "ninth cue rejected");
        check(maxFrame.cues().size() == 8, "wire frame copies cue list immutably");
        for (var stroke : maxShape.strokes()) {
            check(stroke.from().distanceSquared(EYE) < 3 && stroke.to().distanceSquared(EYE) < 3, "marks remain locally anchored, never at a sensed target");
            check(stroke.from().distanceSquared(stroke.to()) < .05, "every airflow segment is short");
            var core = CirculationListeningMesh.profile(stroke, true); var soft = CirculationListeningMesh.profile(stroke, false);
            check(core.gray() > soft.gray() && core.alpha() > soft.alpha() && soft.width() > core.width(), "readable white core and gray soft edge");
        }
        Point east = CirculationListeningMesh.direction(4);
        check(east.x() > .999 && Math.abs(east.z()) < 1e-12, "world azimuth four points east");
        check(Math.abs(CirculationListeningMesh.relativeAzimuth(4, FORWARD) - Math.PI / 2) < 1e-12, "east cue is to right when viewing south");
        check(Math.abs(CirculationListeningMesh.relativeAzimuth(4, east)) < 1e-12, "turning camera east moves same cue to front");
        check(Math.abs(CirculationListeningMesh.relativeAzimuth(0, east) + Math.PI / 2) < 1e-12, "turning east moves south cue left");
        check(maxShape.equals(CirculationListeningMesh.shape(maxController, EYE, 0)), "camera rotation cannot mutate world-sector geometry");
        Point far = new Point(29_999_990, 2048, -29_999_990);
        for (var stroke : CirculationListeningMesh.shape(maxController, far, .5).strokes())
            check(stroke.from().finite() && stroke.to().finite() && stroke.from().distanceSquared(far) < 3, "world-border geometry stays finite and local");
        check(CirculationListeningMesh.relative(new Point(far.x() + .03125, far.y(), far.z() + .0625), far).equals(new Point(.03125, 0, .0625)), "double camera subtraction preserves world-border precision");
        var crossing = new CirculationListeningMesh.Stroke(new Point(0, 0, -1), new Point(0, 0, 1), .05, 200, Role.FLOW);
        var clipped = CirculationListeningMesh.clipNear(crossing, FORWARD, .025);
        check(clipped != null && clipped.from().z() >= .105 - 1e-12, "near-eye crossing clipped before quad extrusion");
        check(CirculationListeningMesh.clipNear(crossing, new Point(0, 0, 0), .025) == null, "invalid camera basis fails closed");
        check(CirculationListeningMesh.shape(maxController, EYE, Double.NaN).vertexCount() == 0, "NaN animation rejected");
        var budget = new CirculationListeningMesh.Budget();
        check(budget.take(384), "full eight-cue shape admitted");
        check(!budget.take(129) && budget.used() == 384, "overflow rejected atomically");
        check(budget.take(128) && budget.used() == 512, "exact global ceiling accepted");
        check(!budget.take(1) && !budget.take(-1) && !budget.take(Integer.MAX_VALUE), "hard budget cannot overflow");
        var hud = CirculationListeningMesh.hudShape(maxController, 320, 202, FORWARD, 0);
        check(hud.markers().size() == 8 && hud.vertexCount() == 128, "eight two-layer compass arrows use exactly 128 vertices");
        check(hud.vertexCount() + maxShape.vertexCount() == 512, "world flow and edge compass share the exact 512 ceiling");
        var combined = new CirculationListeningMesh.Budget();
        check(combined.take(CirculationListeningMesh.MAX_CONTEXT_VERTICES) && combined.take(hud.vertexCount()) && !combined.take(1),
                "production HUD reservation admits the combined ceiling and no extra vertex");
        var eastOnly = new CirculationListeningState.Controller(frame(1, List.of(new Cue(4, 0, 2, 0))), 0, 0);
        var eastAtRight = CirculationListeningMesh.hudShape(eastOnly, 320, 202, FORWARD, 0).markers().getFirst();
        var eastAtFront = CirculationListeningMesh.hudShape(eastOnly, 320, 202, east, 0).markers().getFirst();
        var eastAtRear = CirculationListeningMesh.hudShape(eastOnly, 320, 202, new Point(-1, 0, 0), 0).markers().getFirst();
        check(eastAtRight.tip().x() == 320 - CirculationListeningMesh.HUD_SIDE_INSET && !eastAtRight.rear(), "east uses right edge from south-facing camera");
        check(eastAtFront.tip().x() == 160 && eastAtFront.tip().y() == CirculationListeningMesh.HUD_TOP_INSET && !eastAtFront.rear(),
                "camera turns east and same world cue moves to front/top compass edge");
        check(eastAtRear.rear() && eastAtRear.tip().y() > eastAtRear.left().y() && eastAtRear.tip().y() > eastAtRear.right().y(),
                "camera faces away: rear cue explicitly points down in the lower lane");
        check(eastAtRear.tip().y() <= 202 - CirculationListeningMesh.HUD_BOTTOM_RESERVE, "rear cue stays above health and hotbar area");
        check(CirculationListeningMesh.hudShape(new CirculationListeningState.Controller(frame(2, List.of()), 0, 0), 320, 202, FORWARD, 0).vertexCount() == 0,
                "empty heartbeat produces no edge arrows");
        check(CirculationListeningMesh.hudShape(new CirculationListeningState.Controller(frame(2, List.of(ARROW)), 0, 4), 320, 202, FORWARD, 0).vertexCount() == 0,
                "four stale ticks hide edge arrows using same cue lifetime");
        check(CirculationListeningMesh.hudShape(new CirculationListeningState.Controller(frame(2, List.of(ARROW)), 40, 0), 320, 202, FORWARD, 0).vertexCount() == 0,
                "lost heartbeat hides edge arrows without changing controller");
        check(CirculationListeningMesh.hudShape(new CirculationListeningState.Controller(frame(1, 10, 2, CirculationListeningState.ENDED, List.of()), 0, 0), 320, 202, FORWARD, 0).vertexCount() == 0,
                "ended controller has no edge rendering");
        check(CirculationListeningMesh.hudShape(eastOnly, 111, 202, FORWARD, 0).vertexCount() == 0
                && CirculationListeningMesh.hudShape(eastOnly, 320, 147, FORWARD, 0).vertexCount() == 0,
                "too-small scaled viewports fail closed instead of covering status or health");
        check(CirculationListeningMesh.hudShape(eastOnly, 320, 202, new Point(0, 1, 0), 0).vertexCount() == 0
                && CirculationListeningMesh.hudShape(eastOnly, Integer.MAX_VALUE, 202, FORWARD, 0).vertexCount() == 0,
                "invalid bearing and overflowing viewport rejected");
        for (int width : new int[]{112, 320, 960}) for (int height : new int[]{148, 202, 540})
            for (int viewBin : new int[]{0, 4, 8, 12}) for (int azimuth = 0; azimuth < 16; azimuth++)
                for (int elevation = -1; elevation <= 1; elevation++) {
                    var one = new CirculationListeningState.Controller(frame(2, List.of(new Cue(azimuth, elevation, 3, 0))), 0, 0);
                    var layout = CirculationListeningMesh.hudShape(one, width, height, CirculationListeningMesh.direction(viewBin), .5);
                    check(layout.markers().size() == 1, "valid narrow and scaled viewports retain bounded compass cue");
                    var marker = layout.markers().getFirst();
                    check(marker.alpha() > 0 && marker.alpha() < 232, "edge marks share the same four-tick fade");
                    for (var from : List.of(marker.left(), marker.right())) {
                        double dx = marker.tip().x() - from.x(), dy = marker.tip().y() - from.y(), length = Math.hypot(dx, dy);
                        double nx = -dy / length * 2, ny = dx / length * 2;
                        for (var end : List.of(from, marker.tip())) for (int sign : new int[]{-1, 1})
                            check(CirculationListeningMesh.hudPointSafe(new CirculationListeningMesh.ScreenPoint(end.x() + sign * nx, end.y() + sign * ny), width, height),
                                    "every extruded soft-arrow vertex stays in viewport and outside reserved HUD areas");
                    }
                }
        System.out.println("CirculationListeningVisualRegressionTest: " + checks + " checks passed");
    }
}
