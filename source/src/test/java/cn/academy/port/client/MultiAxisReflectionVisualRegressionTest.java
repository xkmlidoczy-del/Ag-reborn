package cn.academy.port.client;

import cn.academy.port.client.MultiAxisReflectionMesh.Point;
import cn.academy.port.client.MultiAxisReflectionMesh.Role;
import cn.academy.port.highpower.core.MultiAxisReflectionGeometry;
import cn.academy.port.highpower.HighPowerRecoveryHudModel;
import cn.academy.port.highpower.core.MultiAxisReflectionGeometry.Vector;
import java.util.List;
import java.util.UUID;

/** Finite native geometry, receipt truth, observed paths, replay protection and global render limits. */
public final class MultiAxisReflectionVisualRegressionTest {
    private static int checks;
    private static final UUID OWNER = new UUID(442, 1), ARROW = new UUID(442, 90);
    private static final Point BODY = new Point(2, 64, 3), FACING = new Point(0, 0, 1), ACTUAL = new Point(2.1, 64.1, 3.1);
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void near(double value, double expected, String message) { check(Math.abs(value - expected) < 1e-10, message); }
    private static MultiAxisReflectionState.Frame passive(UUID owner, long input, long token, long sequence, int phase, int remaining, int liveMask) {
        return new MultiAxisReflectionState.Frame(new MultiAxisReflectionState.Identity(19, owner, 71, input, token), sequence,
                phase, BODY, FACING, remaining, liveMask, -1, -1, MultiAxisReflectionState.ZERO_UUID, BODY);
    }
    private static MultiAxisReflectionState.Frame passive(long sequence, int phase, int remaining, int liveMask) {
        return passive(OWNER, 1, 10, sequence, phase, remaining, liveMask);
    }
    private static MultiAxisReflectionState.Frame event(long sequence, int phase, int arrow, UUID uuid, Point actual) {
        return new MultiAxisReflectionState.Frame(passive(1, 1, 4, 15).id(), sequence, phase, BODY, FACING,
                3, 14, 0, arrow, uuid, actual);
    }
    private static MultiAxisReflectionState.Frame event(long sequence, int phase) { return event(sequence, phase, 90, ARROW, ACTUAL); }
    private static List<MultiAxisReflectionMesh.Stroke> strokes(MultiAxisReflectionMesh.Shape shape, Role role) {
        return shape.strokes().stream().filter(s -> s.role() == role).toList();
    }
    private static long count(MultiAxisReflectionMesh.Shape shape, Role role) { return strokes(shape, role).size(); }
    private static MultiAxisReflectionMesh.Shape shape(MultiAxisReflectionState state) { return MultiAxisReflectionMesh.shape(state.current(OWNER), BODY, 0); }
    public static void main(String[] args) {
        var state = new MultiAxisReflectionState();
        check(state.accept(passive(1, 0, 4, 15), true), "startup accepts real quota but does not activate facets");
        check(count(shape(state), Role.EDGE_READY) == 0 && count(shape(state), Role.EDGE_UNAVAILABLE) == 16, "startup remains visibly unavailable");
        check(state.accept(passive(2, 1, 4, 15), true), "authoritative activation");
        var active = shape(state);
        check(active.panels().size() == 4 && count(active, Role.EDGE_READY) == 16 && count(active, Role.VECTOR) == 8,
                "four finite transparent planes with sparse per-facet marks");
        check(count(active, Role.QUOTA_READY) == 4 && count(active, Role.REFLECT) == 0 && count(active, Role.CONTACT) == 0,
                "activation shows actual quota and invents neither reflection nor damage");
        var pose = MultiAxisReflectionGeometry.pose(new Vector(BODY.x(), BODY.y(), BODY.z()), new Vector(0, 0, 1));
        for (var panel : active.panels()) {
            check(panel.alpha() > 0 && panel.alpha() <= 12, "pressure fill is faint and transparent");
            var expected = MultiAxisReflectionGeometry.vertices(pose, panel.facet());
            for (int i = 0; i < 4; i++) {
                var v = expected.get(i); check(panel.corners().get(i).equals(new Point(v.x(), v.y(), v.z())), "drawn plane exactly matches admission corners");
            }
            near(Math.sqrt(panel.corners().get(0).distanceSquared(panel.corners().get(1))), .85, "finite width .85m");
            near(Math.sqrt(panel.corners().get(1).distanceSquared(panel.corners().get(2))), 2.6, "finite height 2.6m");
            var center = MultiAxisReflectionGeometry.facetCenter(pose, panel.facet());
            near(center.subtract(pose.center()).length(), 1.25, "body-centered radial offset 1.25m");
            near(Math.toDegrees(Math.atan2(center.x() - BODY.x(), center.z() - BODY.z())), -60 + 40 * panel.facet(), "exact yaw offset");
            if (panel.facet() < 3) {
                Point right = panel.corners().get(1), left = active.panels().get(panel.facet() + 1).corners().get(0);
                check(right.distanceSquared(left) > .002, "physical inter-facet gap remains open");
                check(MultiAxisReflectionMesh.profile(strokes(active, Role.EDGE_READY).getFirst(), false).width()
                        < Math.sqrt(right.distanceSquared(left)), "soft edge shoulders still leave the narrow real gap open");
                check(active.strokes().stream().noneMatch(s -> s.from().equals(right) && s.to().equals(left)), "no decorative bridge fills real gap");
            }
        }
        check(!state.accept(passive(2, 1, 4, 15), true), "duplicate sequence rejected");
        check(state.accept(event(3, 2), true), "authoritative reflection accepted");
        var reflected = shape(state);
        check(count(reflected, Role.REFLECT) == 2 && count(reflected, Role.CONTACT) == 0 && count(reflected, Role.TRAIL) == 0,
                "reflection flash is distinct from a damage cue and invents no trajectory");
        for (var stroke : strokes(reflected, Role.REFLECT)) {
            near((stroke.from().x() + stroke.to().x()) / 2, ACTUAL.x(), "flash at actual arrow X");
            near((stroke.from().y() + stroke.to().y()) / 2, ACTUAL.y(), "flash at actual arrow Y");
            near((stroke.from().z() + stroke.to().z()) / 2, ACTUAL.z(), "flash at actual arrow Z");
            check(stroke.from().distanceSquared(new Point(MultiAxisReflectionGeometry.facetCenter(pose, 0).x(), 64,
                    MultiAxisReflectionGeometry.facetCenter(pose, 0).z())) > .5, "native body-contact cue is not faked at distant facet intersection");
        }
        var arrow = event(3, 2).arrow();
        Point observed = new Point(2.2, 64.2, 3.2);
        state.observe(OWNER, arrow, observed);
        var trail = strokes(shape(state), Role.TRAIL);
        check(trail.size() == 1 && trail.getFirst().from().equals(ACTUAL) && trail.getFirst().to().equals(observed), "trail joins only actual observed historical positions");
        state.tick(false, c -> true);
        check(state.accept(passive(4, 1, 3, 14), true) && state.current(OWNER).traces().getFirst().age() == 1, "heartbeat never refreshes flash age");
        var moved = MultiAxisReflectionMesh.shape(state.current(OWNER), new Point(3, 65, 4), .2);
        near((strokes(moved, Role.REFLECT).getFirst().from().x() + strokes(moved, Role.REFLECT).getFirst().to().x()) / 2, ACTUAL.x(),
                "body movement cannot drag immutable actual reflection location");
        for (int i = 0; i < 100; i++) state.tick(true, c -> true);
        check(state.current(OWNER).traces().getFirst().age() == 1 && state.current(OWNER).quiet() == 0, "pause freezes cosmetic clocks");
        for (int i = 0; i < 12; i++) state.observe(OWNER, arrow, new Point(2.3 + i * .1, 64.2, 3.2));
        check(state.current(OWNER).traces().getFirst().points().size() == 5, "observed path storage bounded");
        state.observe(OWNER, arrow, new Point(20, 64, 3));
        check(count(shape(state), Role.TRAIL) == 0 && count(shape(state), Role.REFLECT) == 0, "teleport/discontinuity clears trail and old flash");
        state.forgetArrow(OWNER, arrow);
        check(state.current(OWNER).traces().isEmpty(), "arrow unload/removal clears path");
        check(state.accept(event(5, 2), true), "new true-position event accepted");
        state.observeBody(OWNER, new Point(30, 64, 3));
        check(state.current(OWNER).traces().isEmpty(), "owner teleport clears transient trail without retiring control");
        state.observeBody(OWNER, BODY);
        check(state.accept(event(6, 3), true), "confirmed HP/absorption loss receipt accepted");
        check(count(shape(state), Role.CONTACT) == 3 && count(shape(state), Role.REFLECT) == 0, "contact cue only from CONTACT receipt");
        state.forgetArrow(OWNER, arrow);
        check(count(shape(state), Role.CONTACT) == 3, "confirmed contact survives removal of spent arrow briefly");
        state.tick(false, c -> true);
        check(state.accept(passive(7, 1, 3, 14), true) && state.current(OWNER).contactAge() == 1, "heartbeat cannot extend contact cue");
        for (int i = 0; i < 9; i++) state.tick(false, c -> true);
        check(count(shape(state), Role.CONTACT) == 0 && count(shape(state), Role.TRAIL) == 0, "transient cues expire");
        check(state.current(OWNER).frame().remainingHits() == 3, "client time never predicts rolling quota refund");
        for (int i = 0; i < 200; i++) check(state.tick(false, c -> true).isEmpty(), "network silence cannot abort server control");
        check(state.size() == 1 && shape(state).vertexCount() == 0, "quiet controller retained but hidden");
        check(!state.accept(passive(6, 1, 4, 15), true), "quiet sequence watermark retained");
        check(state.accept(passive(8, 1, 4, 15), true), "late authenticated heartbeat restores exact token");
        check(count(shape(state), Role.QUOTA_READY) == 4, "only server receipt replenishes quota");
        check(state.accept(passive(9, 1, 0, 0), true), "zero rolling quota remains active but unavailable");
        check(count(shape(state), Role.QUOTA_USED) == 4 && count(shape(state), Role.EDGE_UNAVAILABLE) == 16, "quota unavailable clearly distinct");
        check(state.tick(true, c -> false).size() == 1 && state.size() == 0, "owner invalidity terminates even while paused");
        check(!state.accept(passive(10, 1, 4, 15), true), "retired owner cannot revive");
        check(state.accept(passive(OWNER, 2, 11, 1, 1, 4, 15), true), "new input/server token accepted");
        state.clear();
        check(!state.accept(passive(OWNER, 2, 11, 2, 1, 4, 15), true), "reload retains replay fence");
        state.cancel(71, 3);
        check(!state.accept(passive(OWNER, 3, 12, 1, 1, 4, 15), true), "pre-ack local cancel fences delayed receipt");
        check(state.accept(passive(OWNER, 3, 12, 1, 1, 4, 15), false), "local cancellation does not apply to remote player");
        check(!state.accept(passive(OWNER, 4, 11, 1, 1, 4, 15), false), "new input cannot reuse old server token");
        state.resetSession();
        check(state.accept(passive(1, 1, 4, 15), true) && state.accept(passive(2, 4, 0, 0), true), "new connection resets fence; END clears controller");
        check(!state.accept(event(3, 2), true), "END prevents late reflection replay");
        check(!MultiAxisReflectionState.valid(passive(1, 1, 0, 15)), "empty quota cannot claim live facets");
        check(!MultiAxisReflectionState.valid(passive(1, 4, 1, 0)), "END must clear quota");
        check(!MultiAxisReflectionState.valid(event(1, 2, -1, ARROW, ACTUAL)), "event needs real entity identity");
        check(!MultiAxisReflectionState.valid(event(1, 2, 90, MultiAxisReflectionState.ZERO_UUID, ACTUAL)), "event needs real UUID");
        check(!MultiAxisReflectionState.valid(event(1, 2, 90, ARROW, new Point(8, 64, 3))), "out-of-range event rejected");
        check(!MultiAxisReflectionState.valid(event(1, 2, 90, ARROW, new Point(Double.NaN, 64, 3))), "nonfinite receipt rejected");

        var observedOnly = new MultiAxisReflectionState();
        check(observedOnly.accept(event(1, 2), true), "first observed arrow starts a short true reflection cue");
        observedOnly.observe(OWNER, arrow, observed);
        var retainedPoints = observedOnly.current(OWNER).traces().getFirst().points();
        check(observedOnly.accept(event(2, 2, 90, ARROW, new Point(2.05, 64.05, 3.05)), true), "newer server observation refreshes allowance");
        check(observedOnly.current(OWNER).traces().getFirst().points().equals(retainedPoints),
                "delayed server point cannot reverse a path already using live client positions");
        for (int i = 0; i < 9; i++) observedOnly.tick(false, c -> true);
        check(observedOnly.current(OWNER).traces().isEmpty(), "short trace expires before server's longer observation window");
        check(observedOnly.accept(event(3, 2), true) && count(shape(observedOnly), Role.REFLECT) == 0
                && count(shape(observedOnly), Role.TRAIL) == 0, "late observed REFLECT cannot fabricate a second reflection after expiry");
        observedOnly.clear(); observedOnly.resetSession();
        check(observedOnly.accept(event(1, 2), true) && count(shape(observedOnly), Role.REFLECT) == 2,
                "only a fresh connection can discard retired arrow visibility markers");

        var numeric = passive(1, 1, 4, 15);
        var exact = new MultiAxisReflectionState.Frame(numeric.id(), numeric.sequence(), numeric.phase(), numeric.point(), numeric.facing(),
                numeric.remainingHits(), numeric.liveMask(), -1, -1, MultiAxisReflectionState.ZERO_UUID, numeric.point(),
                "123456789012345.6789", "150000000000000");
        check(MultiAxisReflectionState.valid(exact), "resource receipt retains exact base+lower available and nominal 1.5S capacity");
        check(HighPowerRecoveryHudModel.pool(exact.available(), exact.capacity(), HighPowerRecoveryHudModel.hudNumberLocale("zh_cn")).text().contains("万亿"),
                "Chinese resource HUD uses readable compact large-number units");
        check(HighPowerRecoveryHudModel.pool(exact.available(), exact.capacity(), HighPowerRecoveryHudModel.hudNumberLocale("en_us")).text().contains("T"),
                "English resource HUD uses existing compact units");
        for (String bad : new String[]{"-1", "1e4", "01", "0.0", "100000000000000000001", "1".repeat(1025)}) {
            var invalid = new MultiAxisReflectionState.Frame(numeric.id(), 1, 1, BODY, FACING, 4, 15, -1, -1,
                    MultiAxisReflectionState.ZERO_UUID, BODY, bad, "150000000000000");
            check(!MultiAxisReflectionState.valid(invalid), "malformed, noncanonical or oversized display amount rejected");
        }
        check(HighPowerRecoveryHudModel.fittedScale(120, 300, .66f) * 300 <= 120.001,
                "complete resource text fits its available width");

        var worst = new MultiAxisReflectionState(); worst.accept(passive(1, 1, 4, 15), true);
        for (int i = 0; i < 4; i++) {
            UUID uuid = new UUID(442, 100 + i); var receipt = event(2 + i, 2, 100 + i, uuid, ACTUAL);
            check(worst.accept(receipt, true), "concurrent reflection admitted");
            for (int point = 1; point < 5; point++) worst.observe(OWNER, receipt.arrow(), new Point(ACTUAL.x() + point * .1, ACTUAL.y(), ACTUAL.z()));
        }
        check(worst.accept(event(6, 3, 200, new UUID(442, 200), ACTUAL), true), "independent contact can coexist with four short trails");
        check(shape(worst).vertexCount() == MultiAxisReflectionMesh.MAX_CONTEXT_VERTICES, "worst-case complete shape budget is exact");
        var budget = new MultiAxisReflectionMesh.Budget();
        for (int i = 0; i < 4; i++) check(budget.take(shape(worst).vertexCount()), "all four complete controllers admitted");
        int before = budget.used();
        check(before == 1824 && before <= 2048 && MultiAxisReflectionMesh.MAX_PARTICLES == 0, "global 2048 vertex hard limit and zero particles");
        check(!budget.take(2049 - before) && budget.used() == before, "overflow rejects whole group atomically");
        check(!budget.take(-1) && !budget.take(Integer.MAX_VALUE), "budget arithmetic cannot wrap");
        Point far = new Point(29_999_990, 64, -29_999_990);
        var farFrame = new MultiAxisReflectionState.Frame(passive(1, 1, 4, 15).id(), 1, 1, far, FACING, 4, 15, -1, -1, MultiAxisReflectionState.ZERO_UUID, far);
        var controller = new MultiAxisReflectionState.Controller(farFrame, false, 0, List.of(), null, 7, far);
        for (var stroke : MultiAxisReflectionMesh.shape(controller, far, .5).strokes()) {
            check(stroke.from().finite() && stroke.to().finite(), "far-coordinate geometry stays finite");
            var core = MultiAxisReflectionMesh.profile(stroke, true); var soft = MultiAxisReflectionMesh.profile(stroke, false);
            check(soft.width() > core.width() && core.gray() > soft.gray() && core.alpha() > soft.alpha(), "layered white-gray core and soft pressure shoulder");
        }
        check(MultiAxisReflectionMesh.relative(new Point(far.x() + .03125, far.y(), far.z() + .0625), far).equals(new Point(.03125, 0, .0625)), "camera subtraction remains double precision");
        var crossing = new MultiAxisReflectionMesh.Stroke(new Point(0, 0, -1), new Point(0, 0, 1), .04, 200, Role.EDGE_READY);
        var clipped = MultiAxisReflectionMesh.clipNear(crossing, FACING, .02);
        check(clipped != null && clipped.from().z() >= .1 - 1e-12, "near-eye segments clip before extrusion");
        check(MultiAxisReflectionMesh.clipNear(crossing, new Point(0, 0, 0), .02) == null, "invalid camera direction fails closed");
        check(MultiAxisReflectionMesh.nearFade(new Point(0, 0, .1)) == 0 && MultiAxisReflectionMesh.nearFade(new Point(0, 0, 1)) == 1, "near-eye fade is bounded");
        check(!MultiAxisReflectionMesh.panelVisible(active.panels().getFirst(), new Point(BODY.x(), BODY.y(), BODY.z() + .5), FACING), "plane straddling eye plane is suppressed as a whole");
        check(MultiAxisReflectionMesh.shape(controller, far, Double.NaN).vertexCount() == 0, "invalid render partial rejected");
        var bounded = new MultiAxisReflectionState();
        for (int i = 2; i <= 5; i++) check(bounded.accept(passive(new UUID(442, i), 1, 10, 1, 1, 4, 15), false), "remote context admitted");
        check(!bounded.accept(passive(new UUID(442, 6), 1, 10, 1, 1, 4, 15), false) && bounded.size() == 4, "fifth context rejected");
        check(bounded.accept(passive(1, 1, 4, 15), true) && bounded.size() == 4, "local context survives remote saturation");
        bounded.clear();
        for (int i = 6; i <= 1024; i++) check(bounded.accept(passive(new UUID(442, i), 1, 10, 1, 4, 0, 0), false), "bounded replay fence");
        check(!bounded.accept(passive(new UUID(442, 1025), 1, 10, 1, 4, 0, 0), false) && bounded.watermarkCount() == 1024, "watermark saturation preserves older replay protection");
        System.out.println("MultiAxisReflectionVisualRegressionTest: " + checks + " checks passed");
    }
}
