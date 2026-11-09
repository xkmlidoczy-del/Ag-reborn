package cn.academy.port.client;

import cn.academy.port.client.AutonomousVectorCircleMesh.Point;
import cn.academy.port.client.AutonomousVectorCircleMesh.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Executable receipt, full-3D sector, first-person visibility, fallback and scene-budget regression. */
public final class AutonomousVectorCircleVisualRegressionTest {
    private static int checks;
    private static final UUID OWNER = new UUID(452, 1);
    private static final Point FEET = new Point(2, 64, 3), FORWARD = new Point(0, 0, 1);
    private static final Point SOURCE = new Point(2, 65, 9), CONTACT = new Point(2.3, 65, 3.5);
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static AutonomousVectorCircleState.Frame frame(UUID owner, long input, long token, long sequence,
                                                           int phase, int remaining, boolean ready) {
        return new AutonomousVectorCircleState.Frame(new AutonomousVectorCircleState.Identity(19, owner, 71, input, token),
                sequence, phase, FEET, FORWARD, phase == 1 ? SOURCE : FEET,
                phase == 1 ? CONTACT : new Point(FEET.x(), FEET.y() + 1, FEET.z()), remaining, ready);
    }
    private static AutonomousVectorCircleState.Frame frame(long sequence, int phase, int remaining, boolean ready) {
        return frame(OWNER, 1, 10, sequence, phase, remaining, ready);
    }
    private static List<AutonomousVectorCircleMesh.Stroke> strokes(AutonomousVectorCircleMesh.Shape shape, Role role) {
        return shape.strokes().stream().filter(s -> s.role() == role).toList();
    }
    private static long count(AutonomousVectorCircleMesh.Shape shape, Role role) { return strokes(shape, role).size(); }
    private static AutonomousVectorCircleMesh.Shape shape(AutonomousVectorCircleState state) {
        return AutonomousVectorCircleMesh.shape(state.current(OWNER), FEET, 0);
    }
    public static void main(String[] args) {
        checks += AutonomousVectorCircleSoftProfileRegressionTest.runChecks();
        var state = new AutonomousVectorCircleState();
        check(state.accept(frame(1, 0, 8, true), true), "authoritative activation");
        var active = shape(state);
        check(count(active, Role.BOUNDARY) == 48 && count(active, Role.FLOW) == 40, "four open outer arcs and four sparse forward flows");
        check(count(active, Role.QUOTA_READY) == 8 && count(active, Role.COMPRESSION) == 0 && active.waves().isEmpty(),
                "all eight quota marks and no speculative activation success");
        verifySector(active, FEET, FORWARD);
        Point ordinaryEye = new Point(FEET.x(), FEET.y() + 1.62, FEET.z());
        for (var s : strokes(active, Role.QUOTA_READY)) {
            var a = AutonomousVectorCircleMesh.relative(s.from(), ordinaryEye);
            var b = AutonomousVectorCircleMesh.relative(s.to(), ordinaryEye);
            var relative = new AutonomousVectorCircleMesh.Stroke(a, b, s.width(), s.alpha(), s.role());
            check(AutonomousVectorCircleMesh.outsideAim(relative, FORWARD), "all eight actual quota marks stay outside normal aiming corridor");
            check(Math.abs(a.x()) < a.z() * .6 && Math.abs(a.y()) < a.z() * .6, "all eight quota marks stay inside normal first-person field of view");
        }
        check(!state.accept(frame(1, 0, 8, true), true), "duplicate activation rejected");
        state.tick(false, c -> true);
        check(state.current(OWNER).age() == 1, "flow clock advances independently from sequence");
        check(!strokes(shape(state), Role.FLOW).equals(strokes(active, Role.FLOW)), "flow brightness advances while geometry stays bounded");
        check(state.accept(frame(2, 2, 8, false), true), "temporary unavailable receipt");
        var paused = shape(state);
        check(count(paused, Role.QUOTA_READY) == 8 && count(paused, Role.COMPRESSION) == 0, "pause cannot consume quota or fake absorption");
        check(paused.strokes().getFirst().alpha() < active.strokes().getFirst().alpha(), "unavailable sector stays visible but dim");
        check(state.accept(frame(3, 1, 7, true), true), "server-confirmed positive absorption");
        var hit = shape(state);
        check(count(hit, Role.COMPRESSION) == 12 && hit.waves().size() == 1
                && count(hit, Role.QUOTA_READY) == 7 && count(hit, Role.QUOTA_USED) == 1, "one real receipt gives contact compression and one quota debit");
        check(hit.vertexCount() == AutonomousVectorCircleMesh.MAX_CONTEXT_VERTICES, "worst-case renderer count exact");
        check(hit.vertexCount() * 4 == 3472 && hit.vertexCount() * 4 <= 4096, "whole four-controller scene under cap");
        for (var s : strokes(hit, Role.COMPRESSION)) for (var p : List.of(s.from(), s.to()))
            check(p.distanceSquared(CONTACT) < .12 && p.distanceSquared(SOURCE) > 20, "compression stays local to actual contact");
        state.tick(false, c -> true);
        check(state.accept(frame(4, 0, 7, true), true) && state.current(OWNER).absorptionAge() == 1, "heartbeat cannot refresh contact event");
        var moved = AutonomousVectorCircleMesh.shape(state.current(OWNER), new Point(4, 65, 4), .25);
        check(strokes(moved, Role.COMPRESSION).getFirst().from().distanceSquared(CONTACT) < .12, "movement cannot drag immutable contact");
        check(strokes(moved, Role.COMPRESSION).getFirst().alpha() < strokes(hit, Role.COMPRESSION).getFirst().alpha(), "contact compression fades");
        int age = state.current(OWNER).age();
        for (int i = 0; i < 50; i++) state.tick(true, c -> true);
        check(state.current(OWNER).age() == age && state.current(OWNER).absorptionAge() == 1, "game pause freezes cosmetic clocks");
        for (int i = 1; i < AutonomousVectorCircleState.ABSORPTION_TICKS; i++) state.tick(false, c -> true);
        check(shape(state).waves().isEmpty() && count(shape(state), Role.COMPRESSION) == 0, "heartbeat never prolongs success pulse");
        check(state.current(OWNER).frame().remainingHits() == 7, "client time cannot predict physical quota refill");
        for (int i = 0; i < 100; i++) check(state.tick(false, c -> true).isEmpty(), "quiet receipts never cancel server toggle");
        check(state.size() == 1 && shape(state).vertexCount() == 0, "quiet state retained with hidden stale receipts");
        check(!state.accept(frame(3, 1, 6, true), true), "hidden old hit cannot replay");
        check(state.accept(frame(5, 0, 8, true), true), "matching later heartbeat recovers after lag");
        check(count(shape(state), Role.QUOTA_READY) == 8, "only new receipt replenishes physical ticks");
        check(state.accept(frame(6, 2, 0, false), true) && count(shape(state), Role.QUOTA_USED) == 8, "exhausted physical quota remains visibly eight empty marks");
        check(state.tick(true, c -> false).size() == 1 && state.size() == 0, "explicit invalidity retires during pause");
        check(!state.accept(frame(7, 0, 8, true), true), "invalid owner cannot revive");
        check(state.accept(frame(OWNER, 2, 11, 1, 0, 8, true), true), "new accepted input");
        state.clear();
        check(!state.accept(frame(OWNER, 2, 11, 2, 0, 8, true), true), "reload/menu clear retains replay fence");
        state.cancel(71, 3);
        check(!state.accept(frame(OWNER, 3, 12, 1, 0, 8, true), true), "pre-ack abort fences late receipt");
        check(state.accept(frame(OWNER, 3, 12, 1, 0, 8, true), false), "local cancel cannot reject unrelated remote control");
        check(!state.accept(frame(OWNER, 4, 11, 1, 0, 8, true), false), "new input cannot reuse old nonce");
        state.resetSession();
        check(state.accept(frame(1, 0, 8, true), true) && state.accept(frame(2, 3, 0, false), true), "connection reset and authenticated end");
        check(!state.accept(frame(3, 1, 7, true), true), "off receipt permanently fences that controller");
        check(!AutonomousVectorCircleState.valid(new AutonomousVectorCircleState.Frame(identityForTest(), 1, 0,
                FEET, FORWARD, SOURCE, CONTACT, 8, true)), "idle heartbeat cannot smuggle a hit location");
        check(!AutonomousVectorCircleState.valid(frame(1, 2, 0, true)), "paused receipt cannot claim ready");
        check(!AutonomousVectorCircleState.valid(frame(1, 1, 8, true)), "absorption cannot claim untouched full quota");
        check(!AutonomousVectorCircleState.valid(frame(1, 0, 8, false)), "active cannot claim unavailable");
        check(!AutonomousVectorCircleState.valid(frame(1, 0, 0, true)), "active cannot claim exhausted quota");
        var identity = frame(1, 0, 8, true).id();
        var outside = new AutonomousVectorCircleState.Frame(identity, 1, 1, FEET, FORWARD, new Point(2, 64, 11.01), CONTACT, 7, true);
        check(!AutonomousVectorCircleState.valid(outside), "source beyond eight metres refused");
        check(!AutonomousVectorCircleState.valid(new AutonomousVectorCircleState.Frame(identity, 1, 1, FEET,
                new Point(0, 0, 0), SOURCE, CONTACT, 7, true)), "invalid 3D facing refused");
        for (Point forward : List.of(FORWARD, new Point(0, 1, 0), new Point(0, -1, 0), new Point(.6, .8, 0))) {
            var f = new AutonomousVectorCircleState.Frame(identity, 1, 0, FEET, forward, FEET, new Point(FEET.x(), FEET.y() + 1, FEET.z()), 8, true);
            check(AutonomousVectorCircleState.valid(f), "vertical/pitched full-3D orientation accepted");
            var c = new AutonomousVectorCircleState.Controller(f, true, 0, null, 10, 0);
            var mesh = AutonomousVectorCircleMesh.shape(c, FEET, .5);
            verifySector(mesh, FEET, forward);
            int visible = 0;
            Point eye = new Point(FEET.x(), FEET.y() + 1.62, FEET.z());
            for (var s : mesh.strokes()) {
                var relative = new AutonomousVectorCircleMesh.Stroke(AutonomousVectorCircleMesh.relative(s.from(), eye),
                        AutonomousVectorCircleMesh.relative(s.to(), eye), s.width(), s.alpha(), s.role());
                var clipped = AutonomousVectorCircleMesh.clipNear(relative, forward, s.width() / 2);
                if (clipped != null && AutonomousVectorCircleMesh.outsideAim(clipped, forward)) visible++;
            }
            check(visible > 8, "first-person aiming corridor keeps useful base geometry at extreme pitch");
        }
        var crossing = new AutonomousVectorCircleMesh.Stroke(new Point(-1, 0, 1), new Point(1, 0, 1), .04, 200, Role.FLOW);
        check(!AutonomousVectorCircleMesh.outsideAim(crossing, FORWARD), "stroke crossing central aim is culled as a whole");
        check(AutonomousVectorCircleMesh.outsideAim(new AutonomousVectorCircleMesh.Stroke(new Point(.6, 0, 1),
                new Point(.8, 0, 1), .04, 200, Role.FLOW), FORWARD), "peripheral flow survives aim guard");
        var near = AutonomousVectorCircleMesh.clipNear(new AutonomousVectorCircleMesh.Stroke(new Point(0, 0, -1),
                new Point(0, 0, 1), .04, 200, Role.FLOW), FORWARD, .02);
        check(near != null && near.from().z() >= .1 - 1e-10, "near-eye clip occurs before quad extrusion");
        Point far = new Point(29_999_998, 64, -29_999_998);
        var farFrame = new AutonomousVectorCircleState.Frame(identity, 1, 0, far, FORWARD, far, new Point(far.x(), 65, far.z()), 8, true);
        var farController = new AutonomousVectorCircleState.Controller(farFrame, false, 0, null, 10, 0);
        var farShape = AutonomousVectorCircleMesh.shape(farController, far, .25);
        check(farShape.vertexCount() > 0, "far-coordinate sector remains visible");
        for (var s : farShape.strokes()) check(s.from().finite() && s.to().finite() && s.from().distanceSquared(s.to()) < 1, "finite double-precision local geometry");
        check(AutonomousVectorCircleMesh.relative(new Point(far.x() + .03125, 64, far.z() + .0625), far).equals(new Point(.03125, 0, .0625)), "camera subtraction preserves fine far-world coordinates");
        check(AutonomousVectorCircleMesh.shape(farController, far, Double.NaN).vertexCount() == 0, "invalid timing refuses geometry");
        var base = AutonomousVectorCircleMesh.base(farController, far, .25);
        check(count(base, Role.BOUNDARY) == 12 && count(base, Role.QUOTA_READY) == 8, "simple base retains complete sector and all eight ticks");
        var fullGroup = List.of(hit, hit, hit, hit);
        var baseGroup = List.of(base, base, base, base);
        var group = AutonomousVectorCircleMesh.group(fullGroup, baseGroup, true);
        check(group.enhanced() && group.vertexCount() == 3472, "all four enhanced controllers admitted atomically");
        group = AutonomousVectorCircleMesh.group(fullGroup, baseGroup, false);
        check(!group.enhanced() && group.shapes().size() == 4 && group.vertexCount() == 320, "missing material selects complete plain base group");
        group = AutonomousVectorCircleMesh.group(fullGroup, baseGroup, true, 512);
        check(!group.enhanced() && group.shapes().size() == 4 && group.vertexCount() == 320, "scene budget fallback never silently drops later owners");
        check(AutonomousVectorCircleMesh.MAX_PARTICLES == 0, "zero particle emissions");
        var bounded = new AutonomousVectorCircleState();
        for (int i = 2; i <= 5; i++) check(bounded.accept(frame(new UUID(452, i), 1, 10, 1, 0, 8, true), false), "remote owner admitted");
        check(!bounded.accept(frame(new UUID(452, 6), 1, 10, 1, 0, 8, true), false), "fifth owner rejected");
        check(bounded.accept(frame(1, 0, 8, true), true) && bounded.size() == 4, "local control keeps reserved priority under saturation");
        bounded.clear();
        for (int i = 6; i <= 1024; i++) check(bounded.accept(frame(new UUID(452, i), 1, 10, 1, 3, 0, false), false), "bounded replay tombstone");
        check(!bounded.accept(frame(new UUID(452, 1025), 1, 10, 1, 3, 0, false), false) && bounded.watermarkCount() == 1024, "old replay protection never evicted");
        System.out.println("AutonomousVectorCircleVisualRegressionTest: " + checks + " checks passed");
    }
    private static AutonomousVectorCircleState.Identity identityForTest() { return frame(1, 0, 8, true).id(); }
    private static void verifySector(AutonomousVectorCircleMesh.Shape shape, Point feet, Point forward) {
        for (var s : shape.strokes()) {
            if (s.role() == Role.COMPRESSION) continue;
            for (Point p : List.of(s.from(), s.to())) {
                double x = p.x() - feet.x(), y = p.y() - feet.y() - 1, z = p.z() - feet.z();
                double length = Math.sqrt(x*x + y*y + z*z), along = x*forward.x() + y*forward.y() + z*forward.z();
                check(Double.isFinite(length) && length > 1 && length < 1.31, "sparse finite body-local cap");
                check(along / length >= Math.cos(Math.toRadians(80)) - 1e-8, "no geometry outside true 160-degree 3D forward sector");
                if (s.role() == Role.BOUNDARY || s.role() == Role.FLOW)
                    check(along / length <= Math.cos(Math.toRadians(31)) + 1e-8, "open cone center and rear gap remain empty");
            }
        }
    }
}
