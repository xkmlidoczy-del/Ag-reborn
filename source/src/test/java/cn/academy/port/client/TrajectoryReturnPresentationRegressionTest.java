package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.List;
import java.util.UUID;
import static cn.academy.port.client.TrajectoryReturnState.*;

/** Pure accepted-receipt presentation lifecycle; no renderer, packet, or gameplay substitutions. */
public final class TrajectoryReturnPresentationRegressionTest {
    private static int checks;
    private static final Point V = new Point(0, 0, 1);
    private static final class Fixture {
        final TrajectoryReturnState state = new TrajectoryReturnState();
        final TrajectoryReturnPresentation presentation = new TrajectoryReturnPresentation();
        boolean enabled = true;
        boolean accept(Frame frame, boolean local) {
            boolean accepted = state.accept(frame, local);
            presentation.prune(state.controllers());
            if (accepted) presentation.accepted(frame, controller(frame.id()), enabled);
            return accepted;
        }
        Controller controller(Identity id) { return state.controllers().stream().filter(c -> c.id().equals(id)).findFirst().orElse(null); }
        void tick(boolean paused) { state.tick(paused, c -> true); presentation.prune(state.controllers()); }
        List<Segment> history(Identity id, int slot) { return presentation.histories(controller(id)).getOrDefault(slot, List.of()); }
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static Identity id(int owner, long input) { return new Identity(owner, new UUID(431, owner), 67, input, 100 + input); }
    private static Point p(double z) { return new Point(0, 1.6, z); }
    private static Frame reverse(Identity id, long sequence, int slot, double z) { return new Frame(id, sequence, REVERSE, slot, p(z), p(z), V, COMMITTED); }
    private static Frame flight(Identity id, long sequence, int slot, double from, double to) { return new Frame(id, sequence, FLIGHT, slot, p(from), p(to), V, COMMITTED); }
    private static Frame end(Identity id, long sequence) { return new Frame(id, sequence, ENDED, 0, ZERO, ZERO, ZERO, REFUSED); }

    public static void main(String[] args) {
        authenticatedHistory(); frozenSelection(); clearAndRetire(); boundedSlotsAndControllers();
        System.out.println("TrajectoryReturnPresentationRegressionTest: " + checks + " checks passed");
    }

    private static void authenticatedHistory() {
        var f = new Fixture(); Identity a = id(1, 1);
        check(!f.accept(flight(a, 1, 0, 3, 4), true) && f.presentation.size() == 0, "orphan FLIGHT cannot create presentation");
        check(f.accept(reverse(a, 1, 0, 3), true), "authenticated reversal opens presentation");
        check(f.presentation.enhanced(f.controller(a)) && f.history(a, 0).isEmpty(), "reversal stores selection without inventing flight");
        f.tick(false);
        Frame first = flight(a, 2, 0, 3, 4);
        check(f.accept(first, true), "native segment is accepted");
        var exact = f.history(a, 0).getFirst();
        check(exact.from() == first.origin() && exact.to() == first.end() && exact.tick() == 1, "cache preserves exact receipt endpoint objects and client age");
        var snapshot = f.presentation.histories(f.controller(a));
        check(!f.accept(first, true) && f.history(a, 0).size() == 1, "duplicate packet cannot create or refresh history");
        for (int i = 0; i < 3; i++) f.tick(false);
        check(f.controller(a).slots().getFirst().trail().isEmpty() && f.history(a, 0).size() == 1, "optional history outlives original receipt gap without altering classic state");
        check(f.accept(flight(a, 3, 0, 20, 21), true), "disconnected native segment is independently accepted");
        var separated = f.history(a, 0);
        check(separated.size() == 2 && separated.get(0).to().equals(p(4)) && separated.get(1).from().equals(p(20)), "discontinuity never creates a connector across unknown motion");
        check(snapshot.get(0).size() == 1, "published history snapshots cannot change under later receipts");
        for (int i = 0; i < 40; i++) f.tick(true);
        check(f.controller(a).age() == 4 && f.history(a, 0).size() == 2, "pause freezes retained receipt age");
        for (int i = 0; i < 4; i++) f.tick(false);
        check(f.history(a, 0).size() == 2, "first receipt remains visible through its seventh client tick");
        f.tick(false);
        check(f.history(a, 0).size() == 1 && f.history(a, 0).getFirst().from().equals(p(20)), "each receipt expires independently at exactly eight client ticks");
        Frame contact = new Frame(a, 4, CONTACT, 0, p(21), p(21.5), V, DAMAGED);
        check(f.accept(contact, true) && f.history(a, 0).isEmpty(), "accepted contact clears retained flight immediately");
        check(!f.accept(flight(a, 5, 0, 21.5, 22), true) && f.history(a, 0).isEmpty(), "post-contact flight cannot manufacture history");
        check(f.accept(end(a, 5), true) && f.presentation.size() == 0, "END clears controller and decorative history immediately");

        var bounds = new Fixture(); Identity b = id(2, 1);
        bounds.accept(reverse(b, 1, 0, 0), true);
        bounds.accept(flight(b, 2, 0, 80, 81), true);
        bounds.tick(false);
        bounds.accept(flight(b, 3, 0, 0, 1), true);
        var cast = bounds.controller(b);
        check(!TrajectoryReturnMesh.bounds(cast).contains(p(81)), "historical endpoint is absent from original current-state bounds");
        var extended = bounds.presentation.bounds(cast);
        check(extended.max().z() == 81 + TrajectoryReturnMesh.DECORATIVE_MARGIN
                && extended.min().z() == -TrajectoryReturnMesh.DECORATIVE_MARGIN, "retained endpoints receive precisely the original 1.15m margin");
        check(extended.min().x() == -1.15 && extended.max().x() == 1.15, "history does not inflate the original margin twice");
        for (int i = 0; i < 7; i++) bounds.tick(false);
        check(bounds.presentation.bounds(bounds.controller(b)).equals(TrajectoryReturnMesh.bounds(bounds.controller(b))), "expired historical extent is removed from culling bounds");
    }

    private static void frozenSelection() {
        var f = new Fixture(); Identity classic = id(10, 1), enhanced = id(10, 2);
        f.enabled = false; f.accept(reverse(classic, 1, 0, 3), true);
        f.enabled = true; f.accept(flight(classic, 2, 0, 3, 4), true);
        check(!f.presentation.enhanced(f.controller(classic)) && f.history(classic, 0).isEmpty(), "OFF controller stays classic when toggle turns ON");
        f.accept(reverse(enhanced, 1, 0, 3), true);
        f.enabled = false; f.accept(flight(enhanced, 2, 0, 3, 4), true);
        check(f.presentation.enhanced(f.controller(enhanced)) && f.history(enhanced, 0).size() == 1, "ON controller keeps its choice when toggle turns OFF");
        check(f.presentation.bounds(f.controller(classic)).equals(TrajectoryReturnMesh.bounds(f.controller(classic))), "OFF presentation bounds equal original bounds exactly");
        f.presentation.clear();
        f.enabled = true; f.accept(flight(classic, 3, 0, 4, 5), true);
        check(!f.presentation.enhanced(f.controller(classic)) && f.presentation.size() == 0, "missing cache cannot reinterpret an already active classic controller");
    }

    private static void clearAndRetire() {
        var f = new Fixture(); Identity a = id(20, 1), b = id(20, 2);
        f.accept(reverse(a, 1, 0, 3), true); f.accept(flight(a, 2, 0, 3, 4), true);
        f.accept(reverse(b, 1, 0, 3), true);
        check(!f.accept(flight(a, 4, 0, 4, 5), true), "wire sequence gap retires authoritative controller");
        check(f.presentation.size() == 1 && f.presentation.enhanced(f.controller(b)), "retired sequence history is pruned without touching concurrent cast");
        f.state.cancel(b.epoch(), b.input()); f.presentation.prune(f.state.controllers());
        check(f.presentation.size() == 0, "local cancellation clears cache in the same lifecycle action");
        check(!f.accept(reverse(b, 1, 0, 3), true) && f.presentation.size() == 0, "cancelled replay cannot restore presentation");
        Identity remote = id(21, 1); f.accept(reverse(remote, 1, 0, 3), false);
        f.state.tick(true, c -> false); f.presentation.prune(f.state.controllers());
        check(f.presentation.size() == 0, "owner invalidation clears cache even while paused");
        Identity old = id(22, 1), next = new Identity(old.entity(), old.owner(), old.epoch() + 1, 1, 101);
        f.accept(reverse(old, 1, 0, 3), false); f.accept(reverse(next, 1, 0, 3), false);
        check(f.presentation.size() == 1 && f.presentation.enhanced(f.controller(next)), "owner epoch replacement prunes older presentation");
        f.state.clear(); f.presentation.clear();
        check(f.presentation.size() == 0 && !f.accept(flight(next, 2, 0, 3, 4), false), "world or reload clear retains authoritative replay fence");
        f.state.resetSession(); f.presentation.clear();
        check(f.accept(reverse(next, 1, 0, 3), false), "new connection starts a clean independently authenticated cache");
        for (int i = 0; i < LIFE_TICKS; i++) f.tick(false);
        check(f.presentation.size() == 0 && f.state.controllers().isEmpty(), "hard lifecycle expiry always defeats longer decorative lifetimes");
        Identity hammer = id(23, 1);
        check(f.accept(new Frame(hammer, 1, AIR_HAMMER, 0, p(0), p(8), ZERO, REFUSED), true), "air-hammer presentation starts from authoritative result");
        check(f.presentation.histories(f.controller(hammer)).isEmpty(), "air hammer never creates projectile history");
        check(f.accept(end(hammer, 2), true) && f.presentation.size() == 0, "immediate END wins over nominal sixteen-tick hammer decoration");
    }

    private static void boundedSlotsAndControllers() {
        var f = new Fixture(); Identity a = id(30, 1); long sequence = 1;
        for (int slot = 0; slot < MAX_SLOTS; slot++) f.accept(reverse(a, sequence++, slot, slot * 2), true);
        for (int receipt = 0; receipt < 6; receipt++) for (int slot = 0; slot < MAX_SLOTS; slot++)
            check(f.accept(flight(a, sequence++, slot, slot * 2 + receipt, slot * 2 + receipt + 1), true), "bounded independent slot receipt accepted");
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            var history = f.history(a, slot);
            check(history.size() == MAX_SEGMENTS && history.getFirst().from().equals(p(slot * 2 + 2)), "each slot retains exactly its newest four segments");
        }
        check(f.presentation.histories(f.controller(a)).size() == MAX_SLOTS, "history does not allocate unbounded slot keys");
        var pool = new Fixture();
        for (int owner = 40; owner < 44; owner++) check(pool.accept(reverse(id(owner, 1), 1, 0, 3), false), "four remote controllers accepted");
        check(!pool.accept(reverse(id(44, 1), 1, 0, 3), false) && pool.presentation.size() == MAX_CONTROLLERS, "fifth rejected controller cannot enter cache");
        check(pool.accept(reverse(id(45, 1), 1, 0, 3), true) && pool.presentation.size() == MAX_CONTROLLERS, "local priority retirement prunes before replacement cache admission");
    }
}
