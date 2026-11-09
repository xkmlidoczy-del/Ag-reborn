package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.List;
import java.util.UUID;

/** Actual pure NEW412 receipt and geometry regression; this does not claim native rendering acceptance. */
public final class VectorParryVisualRegressionTest {
    private static final UUID OWNER = new UUID(412, 1);
    private static final Point FEET = new Point(0, 64, 0), FRONT = new Point(0, 0, 1);
    private static final Point ZERO = new Point(0, 0, 0), IMPACT = new Point(0, 65, .7);
    private static final Point INCOMING = new Point(0, 0, -1), TURN = new Point(-1, 0, 0);
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static VectorParryState.Frame frame(UUID owner, long input, long token, long sequence, int phase, int precise) {
        return new VectorParryState.Frame(new VectorParryState.Identity(21, owner, 61, input, token), sequence, phase,
                FEET, FRONT, IMPACT, phase == 1 || phase == 2 ? INCOMING : ZERO, phase == 2 ? TURN : ZERO, precise, phase == VectorParryState.ENDED ? 0 : 4);
    }
    private static VectorParryState.Frame frame(long sequence, int phase, int precise) { return frame(OWNER, 1, 1, sequence, phase, precise); }
    private static VectorParryState.Frame alter(VectorParryState.Frame f, Point feet, Point facing, Point impact, Point incoming, Point turn) {
        return new VectorParryState.Frame(f.id(), f.sequence(), f.phase(), feet, facing, impact, incoming, turn, f.preciseTicks(), f.remainingHits());
    }
    public static void main(String[] args) {
        lifecycle(); invalidReceipts(); phases(); geometry(); budgets();
        System.out.println("VectorParryVisualRegressionTest: " + checks + " pure checks passed; native client rendering remains separate");
    }
    private static void lifecycle() {
        var state = new VectorParryState(); var active = frame(1, VectorParryState.ACTIVE, 3);
        check(state.accept(active, true), "first server-active receipt creates controller");
        check(!state.accept(active, true), "duplicate sequence is rejected");
        for (int i = 0; i < 100; i++) state.tick(true, c -> true);
        check(state.current(OWNER).quiet() == 0 && state.current(OWNER).animationTick() == 0 && state.current(OWNER).preciseTicks() == 3,
                "pause freezes precision, quiet lease, animation and contact clocks");
        state.tick(false, c -> true);
        check(state.current(OWNER).preciseTicks() == 2, "local precision count only decreases");
        check(state.accept(frame(2, VectorParryState.ACTIVE, 3), true) && state.current(OWNER).preciseTicks() == 2,
                "same-age network heartbeat cannot extend local precision clock");
        check(state.accept(frame(3, VectorParryState.ACTIVE, 1), true), "new authoritative remaining window is accepted");
        check(!state.accept(frame(4, VectorParryState.ACTIVE, 2), true), "wire precision window cannot grow within one hold");
        check(!state.accept(frame(OWNER, 1, 2, 4, VectorParryState.ACTIVE, 0), true), "same input cannot change token");
        check(!state.accept(frame(OWNER, 2, 1, 4, VectorParryState.ACTIVE, 0), true), "later input cannot recycle token");
        for (int i = 0; i < 11; i++) check(state.tick(false, c -> true).isEmpty(), "eleven quiet ticks keep receipt live");
        check(state.tick(false, c -> true).size() == 1 && state.current(OWNER).quiet() == 12, "quiet tick twelve hides stale stance without aborting held context");
        check(VectorParryGeometry.shape(state.current(OWNER), 0).strokes().isEmpty(), "quiet-hidden stance emits no mesh");
        check(state.tick(false, c -> true).isEmpty(), "quiet loss callback is emitted once");
        check(!state.accept(frame(4, VectorParryState.ABSORBED, 0), true), "event-only traffic cannot revive a hidden stance");
        check(!state.accept(frame(3, VectorParryState.ACTIVE, 0), true), "old heartbeat cannot revive a hidden stance");
        check(state.accept(frame(5, VectorParryState.ACTIVE, 0), true) && state.current(OWNER).quiet() == 0,
                "fresh same-hold ACTIVE resumes authenticated base stance after a heartbeat gap");
        check(state.current(OWNER).absorption() == null && state.current(OWNER).turn() == null && state.current(OWNER).preciseTicks() == 0,
                "gap resume never restores an expired contact or precision cue");
        check(state.accept(frame(OWNER, 2, 2, 1, VectorParryState.ACTIVE, 3), true), "fresh input and token begin a new hold");
        var end = frame(OWNER, 2, 2, 2, VectorParryState.ENDED, 0);
        check(state.accept(end, true) && state.size() == 0, "end clears controller immediately");
        check(!state.accept(frame(OWNER, 2, 2, 3, VectorParryState.ACTIVE, 0), true), "fresh sequence cannot resurrect ended hold");
        state.resetSession(); check(state.accept(active, true), "verified new connection clears old fence");
        state.clear(); check(!state.accept(frame(2, VectorParryState.ACTIVE, 0), true), "resource and world clear preserve end tombstones");
        state.resetSession(); state.cancel(61, 1);
        check(!state.accept(active, true), "cancel before server acknowledgement rejects late admission");
        var oldEpoch = new VectorParryState.Frame(new VectorParryState.Identity(21, OWNER, 60, 99, 99), 1, 0, FEET, FRONT, IMPACT, ZERO, ZERO, 0, 4);
        check(!state.accept(oldEpoch, true), "cancel fence rejects a lower input session epoch");
        state.resetSession(); state.accept(active, true);
        state.accept(frame(2, VectorParryState.ABSORBED, 2), true);
        for (int i = 0; i < 40; i++) state.tick(true, c -> true);
        check(state.current(OWNER).absorptionAge() == 0 && VectorParryGeometry.shape(state.current(OWNER), 0).rings().size() == 1,
                "pause freezes confirmed contact age and pressure size");
        for (int i = 0; i < 12; i++) state.tick(false, c -> true);
        check(state.current(OWNER).absorption() == null && state.current(OWNER).turn() == null, "quiet hide drops prior hit geometry permanently");
        state.cancel(61, 1);
        check(!state.accept(frame(3, VectorParryState.ACTIVE, 0), true), "release after quiet hide still fences same-hold resume");
        state.resetSession(); state.accept(active, true);
        check(state.tick(true, c -> false).size() == 1 && state.size() == 0, "pause never preserves invalid world, owner or binding");
        var tombstone = new VectorParryState();
        check(tombstone.accept(frame(2, VectorParryState.ENDED, 0), false), "observer can receive END before an ACTIVE");
        check(!tombstone.accept(frame(1, VectorParryState.ACTIVE, 3), false)
                && !tombstone.accept(frame(3, VectorParryState.ABSORBED, 0), false), "early END fences both old and newer event sequences");
    }
    private static void invalidReceipts() {
        var active = frame(1, VectorParryState.ACTIVE, 3);
        check(!VectorParryState.valid(null), "null receipt is rejected");
        check(!VectorParryState.valid(alter(active, FEET, new Point(0, 0, 4), IMPACT, ZERO, ZERO)), "non-unit facing rejected");
        check(VectorParryState.valid(alter(active, FEET, new Point(0, 1, 0), IMPACT, ZERO, ZERO)), "vertical owner look remains valid metadata");
        var vertical = new VectorParryState(); vertical.accept(alter(active, FEET, new Point(0, 1, 0), IMPACT, ZERO, ZERO), true);
        check(VectorParryGeometry.shape(vertical.current(OWNER), 0).strokes().isEmpty(), "exact vertical look cannot invent a horizontal protected sector");
        var verticalEnd = alter(frame(2, VectorParryState.ENDED, 0), FEET, new Point(0, 1, 0), IMPACT, ZERO, ZERO);
        check(vertical.accept(verticalEnd, true) && !vertical.accept(frame(3, VectorParryState.ACTIVE, 0), true),
                "END looking vertically still installs final replay tombstone");
        check(!VectorParryState.valid(alter(active, new Point(Double.NaN, 64, 0), FRONT, IMPACT, ZERO, ZERO)), "nonfinite feet rejected");
        check(!VectorParryState.valid(alter(active, FEET, FRONT, new Point(0, 65, 1.501), ZERO, ZERO)), "impact outside local 1.5-meter body region rejected");
        check(!VectorParryState.valid(alter(active, FEET, FRONT, IMPACT, INCOMING, ZERO)), "ACTIVE cannot carry a made-up incoming force");
        check(!VectorParryState.valid(alter(active, FEET, FRONT, IMPACT, ZERO, TURN)), "ACTIVE cannot carry a turn preview");
        var absorbed = frame(2, VectorParryState.ABSORBED, 0);
        check(!VectorParryState.valid(alter(absorbed, FEET, FRONT, IMPACT, INCOMING, TURN)), "absorption cannot claim knockback turning");
        var turned = frame(3, VectorParryState.TURNED, 0);
        check(!VectorParryState.valid(alter(turned, FEET, FRONT, IMPACT, INCOMING, ZERO)), "TURNED requires a nonzero receipt direction");
        check(!VectorParryState.valid(alter(turned, FEET, FRONT, IMPACT, INCOMING, new Point(0, 1, 0))), "turn cannot change vertical force");
        check(!VectorParryState.valid(frame(3, VectorParryState.TURNED, 1)), "confirmed turn consumes its precision token");
        check(!VectorParryState.valid(frame(3, VectorParryState.ENDED, 1)), "END cannot advertise a precision window");
        check(!VectorParryState.valid(new VectorParryState.Frame(active.id(), 1, 0, FEET, FRONT, IMPACT, ZERO, ZERO, 4, 4)), "four-tick precision claim rejected");
        check(!VectorParryState.valid(new VectorParryState.Frame(active.id(), 1, 0, FEET, FRONT, IMPACT, ZERO, ZERO, 3, 5)), "fifth hit budget rejected");
        check(!VectorParryState.valid(frame(new UUID(0, 0), 1, 1, 1, 0, 3)), "zero owner UUID rejected");
    }
    private static void phases() {
        var state = new VectorParryState(); state.accept(frame(1, VectorParryState.ACTIVE, 3), true);
        var stance = VectorParryGeometry.shape(state.current(OWNER), 0);
        check(stance.strokes().size() == VectorParryGeometry.ACTIVE_STROKES && stance.rings().isEmpty()
                && stance.strokes().stream().noneMatch(s -> s.role() == VectorParryGeometry.Role.TURN), "active precision/selection never paints a contact or turn");
        check(state.accept(frame(2, VectorParryState.ABSORBED, 2), true), "positive-absorption receipt accepted");
        check(VectorParryGeometry.shape(state.current(OWNER), 0).rings().size() == 1, "only positive-absorption phase produces pressure ring");
        for (int i = 0; i < 3; i++) state.tick(false, c -> true);
        var captured = state.current(OWNER).absorption();
        var moved = alter(frame(3, VectorParryState.ACTIVE, 0), new Point(.2, 64, 0), FRONT, new Point(.2, 65, .7), ZERO, ZERO);
        check(state.accept(moved, true) && state.current(OWNER).absorption() == captured && state.current(OWNER).absorptionAge() == 3,
                "ACTIVE preserves exact historical impact and age instead of relocating or renewing contact");
        check(state.accept(frame(4, VectorParryState.TURNED, 0), true), "actual defender force turn accepted");
        var turned = VectorParryGeometry.shape(state.current(OWNER), 0);
        check(turned.strokes().stream().filter(s -> s.role() == VectorParryGeometry.Role.TURN).count() == 7,
                "TURNED receipt produces bounded bent force glyph");
        check(!state.accept(frame(5, VectorParryState.TURNED, 0), true), "second turn in one hold cannot renew a consumed precision turn");
        for (int i = 0; i < 3; i++) state.tick(false, c -> true);
        check(VectorParryGeometry.shape(state.current(OWNER), 0).rings().isEmpty(), "absorption expires independently of later TURNED receipt");
        check(state.accept(frame(6, VectorParryState.ACTIVE, 0), true) && state.current(OWNER).turnAge() == 3,
                "ACTIVE cannot refresh TURNED either");
        for (int i = 0; i < 3; i++) state.tick(false, c -> true);
        check(VectorParryGeometry.shape(state.current(OWNER), 0).strokes().stream().noneMatch(s -> s.role() == VectorParryGeometry.Role.TURN),
                "turn glyph disappears after its exact six cosmetic ticks");
        var onlyTurn = new VectorParryState(); onlyTurn.accept(frame(1, VectorParryState.TURNED, 0), false);
        check(VectorParryGeometry.shape(onlyTurn.current(OWNER), 0).rings().isEmpty(), "TURNED alone never fabricates absorption");
        var unknownIncoming = alter(frame(1, VectorParryState.TURNED, 0), FEET, FRONT, IMPACT, ZERO, TURN);
        var noIncoming = new VectorParryState(); noIncoming.accept(unknownIncoming, false);
        check(VectorParryGeometry.shape(noIncoming.current(OWNER), 0).strokes().stream().filter(s -> s.role() == VectorParryGeometry.Role.TURN).count() == 3,
                "missing reliable incoming vector draws only confirmed outgoing force, without invented incoming path");
    }
    private static void geometry() {
        for (Point facing : List.of(FRONT, new Point(1, 0, 0), new Point(-1, 0, 0), new Point(0, 0, -1), new Point(0, .6, .8))) {
            var state = new VectorParryState(); state.accept(alter(frame(1, 0, 3), FEET, facing, IMPACT, ZERO, ZERO), true);
            var shape = VectorParryGeometry.shape(state.current(OWNER), .5);
            check(shape.strokes().size() == 16, "all cardinal and pitched looks preserve open wedge topology");
            Point flat = new Point(facing.x() / Math.hypot(facing.x(), facing.z()), 0, facing.z() / Math.hypot(facing.x(), facing.z()));
            for (var line : shape.strokes()) for (var point : List.of(line.from(), line.to())) {
                double x = point.x() - FEET.x(), z = point.z() - FEET.z();
                double cosine = (x * flat.x() + z * flat.z()) / Math.hypot(x, z);
                check(point.finite() && cosine >= .5 - 1e-12, "all held geometry is inside the forward 120-degree sector; no back shield");
                check(point.distanceSquared(new Point(0, 65.1, 0)) < 1, "held contour stays near the defender's hand and chest");
            }
            check(shape.strokes().getFirst().from().distanceSquared(shape.strokes().get(9).to()) > 2,
                    "wedge endpoints are visibly separated, never a full sphere or closed circumference");
        }
        for (int guiScale = 1; guiScale <= 4; guiScale++) {
            int width = (1178 + guiScale - 1) / guiScale, height = (814 + guiScale - 1) / guiScale;
            var layout = CornerExchangeHudLayout.fit(width, height, 4, 9);
            check(layout != null && layout.top() >= height / 2 + 16 && layout.top() + layout.height() <= height - 48,
                    "compact mode guidance stays below reticle and above hotbar at GUI scale " + guiScale);
        }
        var state = new VectorParryState(); state.accept(frame(1, 1, 0), true);
        var ring = VectorParryGeometry.shape(state.current(OWNER), 0).rings().getFirst();
        for (int i = 0; i < 4; i++) state.tick(false, c -> true);
        var small = VectorParryGeometry.shape(state.current(OWNER), 0).rings().getFirst();
        check(small.a().distanceSquared(small.c()) < ring.a().distanceSquared(ring.c()) && small.alpha() < ring.alpha(),
                "positive absorption compresses and fades instead of expanding an explosion");
        check(VectorParryGeometry.shape(state.current(OWNER), Double.NaN).strokes().isEmpty()
                && VectorParryGeometry.shape(state.current(OWNER), 1).strokes().isEmpty(), "invalid partial clocks draw nothing");
        Point camera = new Point(29_999_999, 64, -29_999_999), world = new Point(29_999_999.125, 64.5, -29_999_999.25);
        check(VectorParryGeometry.relative(world, camera).equals(new Point(.125, .5, -.25)), "double camera subtraction preserves world-edge sub-block precision");
        var crossing = new VectorParryGeometry.Stroke(new Point(0, 0, -.5), new Point(0, 0, 1), .08, 180, VectorParryGeometry.Role.WEDGE);
        var clipped = VectorParryGeometry.clipNear(crossing, FRONT, .04);
        check(clipped != null && Math.abs(clipped.from().z() - .12) < 1e-12, "near clipping reserves full shoulder half-width");
        check(VectorParryGeometry.clipNear(new VectorParryGeometry.Stroke(new Point(0, 0, -1), new Point(0, 0, -.1), .1, 100,
                VectorParryGeometry.Role.HAND), FRONT, .05) == null, "behind-eye line cannot invert");
        check(!VectorParryGeometry.ringInFront(new VectorParryGeometry.Ring(new Point(-1, -1, .04), new Point(-1, 1, .2),
                new Point(1, 1, .2), new Point(1, -1, .2), 100), ZERO, FRONT), "ring with any near-plane corner is rejected whole");
        check(VectorParryGeometry.ringInFront(new VectorParryGeometry.Ring(new Point(-1, -1, 1), new Point(-1, 1, 1),
                new Point(1, 1, 1), new Point(1, -1, 1), 100), ZERO, FRONT), "fully visible ring survives near test");
        for (double edge : new double[]{29_999_999.8, -29_999_999.8}) {
            var border = new VectorParryState(); Point feet = new Point(edge, 64, edge);
            border.accept(alter(frame(1, 1, 0), feet, FRONT, new Point(edge, 65, edge), INCOMING, ZERO), false);
            var shape = VectorParryGeometry.shape(border.current(OWNER), 0);
            for (var line : shape.strokes()) check(VectorParryGeometry.validPoint(line.from()) && VectorParryGeometry.validPoint(line.to()),
                    "world-edge generation omits out-of-range contours");
            for (var q : shape.rings()) check(VectorParryGeometry.validPoint(q.a()) && VectorParryGeometry.validPoint(q.b())
                    && VectorParryGeometry.validPoint(q.c()) && VectorParryGeometry.validPoint(q.d()), "world-edge ring corners are bounded");
        }
    }
    private static void budgets() {
        check(VectorParryGeometry.MAX_CONTROLLERS == 4 && VectorParryGeometry.MAX_VERTICES == 1024
                && VectorParryGeometry.MAX_PARTICLES == 0, "approved global caps remain exact");
        var state = new VectorParryState(); state.accept(frame(1, 1, 2), false); state.accept(frame(2, 2, 0), false);
        var shape = VectorParryGeometry.shape(state.current(OWNER), 0);
        check(shape.vertexCount() == VectorParryGeometry.MAX_CONTROLLER_VERTICES
                && 4 * shape.vertexCount() <= VectorParryGeometry.MAX_VERTICES, "four simultaneous absorption-and-turn controllers fit global budget");
        var budget = new VectorParryGeometry.Budget();
        for (int i = 0; i < 256; i++) check(budget.take(4), "four-vertex submission is counted");
        check(!budget.take(4) && !budget.take(Integer.MAX_VALUE) && !budget.take(-4) && budget.used() == 1024,
                "global hard cap rejects overflow and malformed reservations without changing usage");
        var bounded = new VectorParryState();
        for (int i = 0; i < VectorParryState.MAX_CONTEXTS; i++) check(bounded.accept(frame(new UUID(412, i + 1), 1, 1, 1, 0, 3), false), "bounded observer context admitted");
        check(!bounded.accept(frame(new UUID(412, 1000), 1, 1, 1, 0, 3), false) && bounded.size() == 64, "sixty-fifth live context rejected without eviction");
        bounded.clear();
        for (int i = 64; i < VectorParryState.MAX_WATERMARKS; i++) check(bounded.accept(frame(new UUID(412, i + 1), 1, 1, 1, 3, 0), false), "ended owner watermark admitted");
        check(!bounded.accept(frame(new UUID(412, 9000), 1, 1, 1, 3, 0), false) && bounded.watermarkCount() == 1024,
                "watermark saturation cannot evict old replay protection");
    }
}
