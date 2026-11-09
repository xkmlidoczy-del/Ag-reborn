package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.List;
import java.util.UUID;

/** Executable receipt transitions, native medium identity fencing and bounded coordinate artwork. */
public final class DisplacedProjectionVisualRegressionTest {
    private static int checks;
    private static final UUID OWNER = new UUID(311, 1), NEEDLE = new UUID(311, 2);
    private static final Point SOURCE = new Point(0, 65.2, 0), ARRIVAL = new Point(4.5, 65, 0), PLAN = new Point(3, 65, 0);
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static DisplacedProjectionState.Frame frame(long input, long token, long sequence, int phase, boolean accepted, Point contact) {
        return new DisplacedProjectionState.Frame(new DisplacedProjectionState.Identity(19, OWNER, 71, input, token), sequence,
                phase, SOURCE, ARRIVAL, contact, 27, NEEDLE, accepted, "");
    }
    private static DisplacedProjectionState.Frame replaceMedium(DisplacedProjectionState.Frame frame, int entity, UUID uuid) {
        return new DisplacedProjectionState.Frame(frame.id(), frame.sequence(), frame.phase(), frame.source(), frame.arrival(),
                frame.impact(), entity, uuid, frame.accepted(), frame.reason());
    }
    public static void main(String[] args) {
        var state = new DisplacedProjectionState(); var projected = frame(1, 1, 1, 1, false, PLAN);
        check(DisplacedProjectionState.valid(projected) && state.accept(projected, true), "committed relocation admits finite endpoints without claiming damage");
        check(!DisplacedProjectionState.terminal(projected), "relocation keeps input authority for native contact");
        check(!state.accept(projected, true), "repeated relocation cannot reanimate marks");
        check(!state.accept(frame(1, 1, 2, 1, false, PLAN), true), "higher sequence cannot project the same cast twice");
        check(!DisplacedProjectionState.valid(frame(1, 1, 1, 1, true, PLAN)), "PROJECTED cannot claim accepted hurt");
        var actualContact = new Point(3.1, 64.97, .04);
        var hit = frame(1, 1, 2, 2, true, actualContact);
        check(!state.accept(replaceMedium(hit, 28, NEEDLE), true), "recycled native medium id rejected");
        check(!state.accept(replaceMedium(hit, 27, new UUID(99, 99)), true), "medium UUID change rejected");
        check(!state.accept(frame(1, 1, 1, 2, true, actualContact), true), "out-of-order contact rejected");
        check(state.accept(hit, true) && DisplacedProjectionState.terminal(hit), "actual contact can differ from frozen aim endpoint and terminates once");
        check(!state.accept(hit, true) && !state.accept(projected, true), "terminal native contact never reopens");
        check(!state.accept(frame(2, 2, 2, 2, true, actualContact), true), "unseen impact does not invent a cast");
        check(!state.accept(frame(1, 2, 1, 1, false, PLAN), true), "old input cannot reopen with new token");
        check(!state.accept(frame(2, 1, 1, 1, false, PLAN), true), "old token cannot reopen with new input");
        check(state.accept(frame(2, 2, 1, 1, false, PLAN), true), "fresh projection accepted");
        check(state.accept(frame(2, 2, 2, 2, false, actualContact), true), "native armor or shield refusal remains a terminal contact without claiming hurt");
        var refused = new DisplacedProjectionState.Frame(new DisplacedProjectionState.Identity(19, OWNER, 71, 3, 3),
                1, 3, null, null, null, -1, null, false, "blocked");
        check(DisplacedProjectionState.valid(refused) && state.accept(refused, true), "refusal can contain no success geometry or medium");
        check(!state.accept(frame(3, 3, 2, 1, false, PLAN), true), "refusal is terminal");
        check(state.accept(frame(4, 4, 1, 1, false, PLAN), true), "fresh projection before cancellation");
        check(state.accept(frame(4, 4, 2, 4, false, PLAN), true), "cancel closes active projection");
        check(!state.accept(frame(4, 4, 3, 2, true, actualContact), true), "cancelled cast cannot receive damage cosmetics");
        state.cancel(71, 5); check(!state.accept(frame(5, 5, 1, 1, false, PLAN), true), "local abort suppresses late success");
        check(!DisplacedProjectionState.valid(frame(6, 6, 1, 1, false, new Point(Double.NaN, 65, 0))), "nonfinite endpoint rejected");
        check(!DisplacedProjectionState.valid(replaceMedium(frame(6, 6, 1, 1, false, PLAN), 27, new UUID(0, 0))), "zero medium UUID rejected");
        state.resetSession(); check(state.accept(projected, true), "new connection resets replay history");
        var remote = new DisplacedProjectionState();
        check(!remote.accept(refused, false), "remote refusal cannot paint anything");
        check(remote.accept(projected, false) && remote.accept(frame(1, 1, 2, 4, false, PLAN), false), "remote cancellation fences its confirmed relocation");
        var full = new DisplacedProjectionState();
        for (int i = 1; i <= DisplacedProjectionState.MAX_WATERMARKS; i++) {
            var receipt = new DisplacedProjectionState.Frame(new DisplacedProjectionState.Identity(i, new UUID(311, i), 71, 1, 1),
                    1, 1, SOURCE, ARRIVAL, PLAN, 27, NEEDLE, false, "");
            check(full.accept(receipt, false), "bounded owner accepted");
        }
        var extra = new DisplacedProjectionState.Frame(new DisplacedProjectionState.Identity(2000, new UUID(311, 2000), 71, 1, 1),
                1, 1, SOURCE, ARRIVAL, PLAN, 27, NEEDLE, false, "");
        check(!full.accept(extra, false) && full.watermarkCount() == 1024, "replay fences never evict to animate an old packet");
        for (Point endpoint : List.of(SOURCE, ARRIVAL)) {
            check(DisplacedProjectionMesh.corners(endpoint).size() == 24, "each endpoint gets twenty-four small coordinate strokes");
            for (var segment : DisplacedProjectionMesh.corners(endpoint)) for (Point point : List.of(segment.from(), segment.to()))
                check(point.finite() && point.distanceSquared(endpoint) < .12, "all strokes remain local to confirmed endpoints");
        }
        for (int age = 0; age < 7; age++) {
            var particles = DisplacedProjectionMesh.particles(SOURCE, ARRIVAL, age);
            check(DisplacedProjectionMesh.frame(age) == age && DisplacedProjectionMesh.alpha(age) > 0 && particles.size() <= 32,
                    "seven original mark frames and at most thirty-two total fragments");
            for (var particle : particles) check(particle.position().finite()
                    && Math.min(particle.position().distanceSquared(SOURCE), particle.position().distanceSquared(ARRIVAL)) < .05,
                    "fragments never form a synthetic projectile or bright path");
        }
        check(DisplacedProjectionMesh.alpha(7) == 0 && DisplacedProjectionMesh.particles(SOURCE, ARRIVAL, 7).isEmpty(), "seven-tick hard expiry");
        System.out.println("DisplacedProjectionVisualRegressionTest: " + checks + " checks passed");
    }
}
