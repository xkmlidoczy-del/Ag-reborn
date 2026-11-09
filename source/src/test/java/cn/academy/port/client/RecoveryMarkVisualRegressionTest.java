package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Receipt provenance, exact frozen needle identity, silence/lease separation and near-camera geometry. */
public final class RecoveryMarkVisualRegressionTest {
    private static final UUID OWNER = new UUID(323, 1), OBSERVER = new UUID(323, 2), NEEDLE = new UUID(311, 1);
    private static final Point SOURCE = new Point(8, 64, 0), ANCHOR = new Point(0, 64, 0);
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static RecoveryMarkState.Frame frame(UUID owner, long input, long token, long sequence, int phase, int remaining) {
        return new RecoveryMarkState.Frame(new RecoveryMarkState.Identity(19, owner, 71, input, token), sequence, phase,
                SOURCE, ANCHOR, 91, NEEDLE, remaining, phase != RecoveryMarkState.REFUSED, "");
    }
    private static RecoveryMarkState.Frame change(RecoveryMarkState.Frame f, Point source, Point anchor, int entity, UUID needle, boolean accepted) {
        return new RecoveryMarkState.Frame(f.id(), f.sequence(), f.phase(), source, anchor, entity, needle,
                f.remainingTicks(), accepted, f.reason());
    }
    public static void main(String[] args) {
        var mark = frame(OWNER, 1, 1, 1, RecoveryMarkState.MARKED, 1200);
        var state = new RecoveryMarkState();
        check(state.accept(mark, true) && state.markerVisible(), "authenticated mark establishes a visible owner receipt");
        check(!state.accept(mark, true), "duplicate sequence cannot replay a mark");
        var heartbeat = frame(OWNER, 1, 1, 2, RecoveryMarkState.MARKED, 1190);
        check(!state.accept(change(heartbeat, SOURCE, new Point(1, 64, 0), 91, NEEDLE, true), true), "anchor is frozen for a token");
        check(!state.accept(change(heartbeat, SOURCE, ANCHOR, 92, NEEDLE, true), true), "needle entity id cannot change");
        check(!state.accept(change(heartbeat, SOURCE, ANCHOR, 91, new UUID(311, 2), true), true), "needle UUID cannot change even if native id is reused");
        check(!state.accept(frame(OWNER, 2, 1, 2, RecoveryMarkState.MARKED, 1190), true), "heartbeat cannot adopt a second press");
        check(state.accept(change(heartbeat, new Point(8, 63, 0), ANCHOR, 91, NEEDLE, true), true), "source follows actual needle gravity while anchor stays frozen");
        check(!state.accept(frame(OWNER, 1, 1, 3, RecoveryMarkState.RECOVERED, 0), true), "registration input alone cannot claim second-press recovery");
        var recovered = frame(OWNER, 2, 1, 3, RecoveryMarkState.RECOVERED, 0);
        check(!state.accept(change(recovered, SOURCE, ANCHOR, 91, NEEDLE, false), true), "unaccepted packet cannot animate recovery");
        check(state.accept(recovered, true) && state.active() == null, "confirmed recovery consumes the owner marker");
        check(!state.accept(recovered, true) && !state.accept(frame(OWNER, 1, 1, 4, RecoveryMarkState.MARKED, 1100), true), "retired token never reanimates");
        check(state.accept(frame(OWNER, 3, 2, 1, RecoveryMarkState.MARKED, 1200), true), "new press and token can mark again");
        check(state.accept(frame(OWNER, 4, 2, 2, RecoveryMarkState.REFUSED, 0), true) && state.active() == null, "refusal consumes mark with no recovery animation");
        check(!state.accept(frame(OWNER, 4, 2, 3, RecoveryMarkState.RECOVERED, 0), true), "late recovered receipt after refusal stays retired");
        state.cancel(71, 5);
        check(!state.accept(frame(OWNER, 5, 3, 1, RecoveryMarkState.MARKED, 1200), true), "cancellation fences delayed first acknowledgement");
        check(state.accept(frame(OWNER, 6, 4, 1, RecoveryMarkState.MARKED, 1200), true), "fresh uncancelled identity can create mark");
        state.clear();
        check(!state.accept(frame(OWNER, 6, 4, 2, RecoveryMarkState.MARKED, 1190), true), "context clear retires old token");
        state.resetSession();
        check(state.accept(mark, true), "new verified connection resets session replay provenance");

        var quiet = new RecoveryMarkState(); quiet.accept(mark, true);
        for (int i = 0; i < 29; i++) check(quiet.tick(false, true) == null, "silence within grace cannot cancel ownership");
        check(quiet.tick(false, true) == null && quiet.active() != null && !quiet.markerVisible(), "thirty-tick silence hides only the cosmetic marker");
        for (int i = 0; i < 70; i++) check(quiet.tick(false, true) == null, "long gap cannot issue destructive owner cancellation");
        check(quiet.remainingTicks() == 1100, "hidden marker spends the original lease");
        check(quiet.accept(heartbeat, true) && quiet.markerVisible() && quiet.remainingTicks() == 1100, "heartbeat can restore visibility but never renew original lifetime");
        check(!quiet.accept(mark, true), "older heartbeat cannot replay or extend lease");
        for (int i = 0; i < 1099; i++) check(quiet.tick(false, true) == null, "mark remains owned through original lifetime");
        check(quiet.tick(false, true) != null && quiet.active() == null && !quiet.markerVisible(), "original 1200-tick maximum expires exactly once");
        check(!quiet.accept(frame(OWNER, 1, 1, 3, RecoveryMarkState.MARKED, 10), true), "heartbeat after local expiry cannot revive owner");
        check(quiet.tick(false, true) == null, "expiration cannot repeat");
        var invalid = new RecoveryMarkState(); invalid.accept(mark, true);
        for (int i = 0; i < 35; i++) invalid.tick(false, true);
        check(invalid.tick(false, false) != null && invalid.active() == null, "hidden ownership clears immediately when context becomes invalid");
        check(!invalid.accept(heartbeat, true), "old-session heartbeat remains retired after context change");
        var nextSession = new RecoveryMarkState.Frame(new RecoveryMarkState.Identity(19, OWNER, 72, 1, 1), 1,
                RecoveryMarkState.MARKED, SOURCE, ANCHOR, 91, NEEDLE, 1200, true, "");
        check(invalid.accept(nextSession, true) && !invalid.accept(frame(OWNER, 8, 9, 10, RecoveryMarkState.MARKED, 1000), true), "higher authenticated epoch fences all prior session tokens");
        var paused = new RecoveryMarkState(); paused.accept(mark, true);
        for (int i = 0; i < 80; i++) paused.tick(true, true);
        check(paused.remainingTicks() == 1200 && paused.markerVisible(), "pause freezes lease and heartbeat visibility");
        check(paused.tick(true, false) != null, "invalid owner still clears while paused");
        var shortLife = new RecoveryMarkState(); shortLife.accept(frame(OWNER, 1, 1, 1, RecoveryMarkState.MARKED, 1), true);
        check(shortLife.tick(false, true) != null, "one-tick server lease expires exactly");

        var observer = new RecoveryMarkState(); observer.accept(mark, true);
        check(!observer.accept(frame(OBSERVER, 1, 1, 1, RecoveryMarkState.MARKED, 1200), false), "remote persistent source and anchor stay private");
        check(!observer.accept(frame(OBSERVER, 1, 1, 1, RecoveryMarkState.REFUSED, 0), false), "remote failure cannot allocate cosmetic state");
        check(observer.accept(frame(OBSERVER, 2, 1, 2, RecoveryMarkState.RECOVERED, 0), false) && observer.active().equals(mark), "confirmed observer recovery cannot replace local anchor");
        check(!observer.accept(frame(OBSERVER, 2, 1, 2, RecoveryMarkState.RECOVERED, 0), false), "observer duplicate recovery cannot replay");
        var full = new RecoveryMarkState();
        for (int i = 1; i <= RecoveryMarkState.MAX_WATERMARKS; i++)
            check(full.accept(frame(new UUID(323, i), 1, 1, 1, RecoveryMarkState.RECOVERED, 0), false), "bounded receipt owner admitted");
        check(!full.accept(frame(new UUID(323, 2000), 1, 1, 1, RecoveryMarkState.RECOVERED, 0), false) && full.watermarkCount() == 1024, "replay table never evicts an old fence for a new owner");
        check(!RecoveryMarkState.valid(change(mark, new Point(Double.NaN, 64, 0), ANCHOR, 91, NEEDLE, true)), "NaN receipt rejected");
        check(!RecoveryMarkState.valid(change(mark, SOURCE, ANCHOR, -1, NEEDLE, true)), "mark needs a native needle id");
        check(!RecoveryMarkState.valid(change(mark, SOURCE, ANCHOR, 91, new UUID(0, 0), true)), "zero needle UUID rejected");
        check(!RecoveryMarkState.valid(change(mark, SOURCE, ANCHOR, 91, NEEDLE, false)), "unaccepted mark rejected");
        for (int phase : new int[]{RecoveryMarkState.CLEARED, RecoveryMarkState.REFUSED})
            check(RecoveryMarkState.valid(change(frame(OWNER, 1, 1, 1, phase, 0), null, null, -1, null, phase == RecoveryMarkState.CLEARED)), "empty clear and early refusal can omit endpoints");

        for (double coordinate : new double[]{0, 25_000_000, -25_000_000, 29_999_000, -29_999_000}) {
            Point camera = new Point(coordinate + .0625, 64, coordinate + .125);
            Point corner = new Point(coordinate + .1765, 64.19, coordinate + .239);
            Point relative = RecoveryMarkState.relative(corner, camera);
            check(Math.abs((float)relative.x() - .114) < 1e-6 && Math.abs((float)relative.z() - .114) < 1e-6, "double-first camera subtraction preserves needle corner dimensions at world border");
            if (Math.abs(coordinate) > 1_000_000)
                check(Math.abs(((float)corner.x() - (float)camera.x()) - .114) > .1, "absolute-float mutation demonstrably destroys corner precision");
        }
        for (int age = 0; age < 7; age++) {
            check(DisplacedProjectionMesh.frame(age) == age && DisplacedProjectionMesh.alpha(age) > 0, "confirmed recovery uses each of seven original TP frames");
            check(DisplacedProjectionMesh.particles(SOURCE, ANCHOR, age).size() <= 32, "recovery has a bounded total of at most thirty-two endpoint particles");
        }
        check(DisplacedProjectionMesh.corners(SOURCE).size() == 24 && DisplacedProjectionMesh.corners(ANCHOR).size() == 24, "both endpoints have twenty-four compact corner strokes");
        check(DisplacedProjectionMesh.alpha(7) == 0 && DisplacedProjectionMesh.particles(SOURCE, ANCHOR, 7).isEmpty(), "seven frames leave no lingering portal or impact");
        for (double depth : new double[]{.36, .5, 1, 2, 8, 32, 64}) for (int width : new int[]{1, 40, 120, 480}) {
            float scale = RecoveryMarkState.labelScale(depth, 64, width);
            check(scale > 0 && scale <= .025f && scale * width / depth <= .360001, "near label angular width stays bounded independently of string length and lateral distance");
        }
        check(RecoveryMarkState.labelScale(.35, 1, 100) == 0 && RecoveryMarkState.labelScale(-1, 1, 100) == 0
                && RecoveryMarkState.labelScale(Double.NaN, 1, 100) == 0, "near-plane and invalid-depth labels are suppressed");
        multiHandleSnapshots();
        System.out.println("RecoveryMarkVisualRegressionTest: " + checks + " checks passed");
    }
    private static RecoveryMarkState.Handle handle(int index, int remaining) {
        return new RecoveryMarkState.Handle(100 + index, 90 + index, new UUID(311, index),
                new Point(8, 64, index), new Point(index, 64, 0), remaining);
    }
    private static RecoveryMarkState.Frame snapshot(long input, long sequence, int phase,
                                                     RecoveryMarkState.Handle selected, List<RecoveryMarkState.Handle> handles) {
        return new RecoveryMarkState.Frame(new RecoveryMarkState.Identity(19, OWNER, 71, input, 50), sequence, phase,
                selected == null ? null : selected.source(), selected == null ? null : selected.anchor(),
                selected == null ? -1 : selected.needleEntity(), selected == null ? null : selected.needleUuid(),
                phase == RecoveryMarkState.MARKED ? selected.remainingTicks() : 0, phase != RecoveryMarkState.REFUSED, "", handles);
    }
    private static void multiHandleSnapshots() {
        var state = new RecoveryMarkState(); var registered = new ArrayList<RecoveryMarkState.Handle>();
        for (int i = 1; i <= 8; i++) {
            registered.add(handle(i, 1200));
            check(state.accept(snapshot(i, i, RecoveryMarkState.MARKED, registered.getLast(), registered), true),
                    "fresh acknowledged input adds one handle without replacing previous anchors");
            check(state.handleCount() == i && state.handles().equals(registered), "bounded snapshot preserves registration order and identity");
        }
        check(RecoveryMarkState.MAX_HANDLES == 8 && RecoveryMarkState.MAX_LABELS == 1, "eight physical marker pairs share one label budget");
        check(state.labelHandle(handle(3, 1200).needleUuid()).equals(handle(3, 1200)), "aimed handle owns the single label");
        check(state.labelHandle(null).equals(handle(8, 1200)), "current handle owns label when no registered needle is aimed");
        var overCapacity = new ArrayList<>(registered); overCapacity.add(handle(9, 1200));
        check(!RecoveryMarkState.valid(snapshot(9, 9, RecoveryMarkState.MARKED, handle(9, 1200), overCapacity)), "ninth handle cannot allocate markers");
        var duplicateToken = new ArrayList<>(registered); duplicateToken.set(7,
                new RecoveryMarkState.Handle(101, 98, new UUID(311, 8), SOURCE, ANCHOR, 1200));
        check(!RecoveryMarkState.valid(snapshot(8, 9, RecoveryMarkState.MARKED, registered.getFirst(), duplicateToken)), "handle tokens are unique per snapshot");
        var duplicateUuid = new ArrayList<>(registered); duplicateUuid.set(7,
                new RecoveryMarkState.Handle(108, 98, new UUID(311, 1), SOURCE, ANCHOR, 1200));
        check(!RecoveryMarkState.valid(snapshot(8, 9, RecoveryMarkState.MARKED, registered.getFirst(), duplicateUuid)), "UUID reuse cannot manufacture two registrations");
        var duplicateEntity = new ArrayList<>(registered); duplicateEntity.set(7,
                new RecoveryMarkState.Handle(108, 91, new UUID(311, 8), SOURCE, ANCHOR, 1200));
        check(!RecoveryMarkState.valid(snapshot(8, 9, RecoveryMarkState.MARKED, registered.getFirst(), duplicateEntity)), "one native entity id cannot supply two handles");
        check(!state.accept(snapshot(8, 7, RecoveryMarkState.MARKED, registered.getLast(), registered), true), "older snapshot sequence cannot reorder current markers");
        check(!state.accept(snapshot(7, 9, RecoveryMarkState.MARKED, registered.getLast(), registered), true), "older input cannot become current even with newer sequence");
        var movedAnchor = new ArrayList<>(registered); var first = registered.getFirst();
        movedAnchor.set(0, new RecoveryMarkState.Handle(first.token(), first.needleEntity(), first.needleUuid(), SOURCE, new Point(2, 64, 2), 1200));
        check(!state.accept(snapshot(8, 9, RecoveryMarkState.MARKED, registered.getLast(), movedAnchor), true), "adding other needles never permits retargeting an earlier anchor");
        var survivors = new ArrayList<>(registered); survivors.remove(2);
        check(state.accept(snapshot(9, 9, RecoveryMarkState.RECOVERED, handle(3, 1200), survivors), true)
                && state.handleCount() == 7 && state.active().id().input() == 9, "recovering exactly one handle preserves seven and adopts current owner input");
        check(!state.accept(snapshot(10, 10, RecoveryMarkState.MARKED, handle(3, 1200), registered), true), "retired handle cannot return with a newer input or sequence");
        check(!state.accept(snapshot(9, 10, RecoveryMarkState.RECOVERED, handle(3, 1200), survivors), true), "same-input recovery cannot replay while other handles survive");
        check(state.accept(snapshot(10, 10, RecoveryMarkState.REFUSED, null, survivors), true)
                && state.handleCount() == 7 && state.active().id().input() == 10, "cap or aim refusal preserves authoritative surviving handles");
        var removeFailed = new ArrayList<>(survivors); removeFailed.remove(0);
        check(state.accept(snapshot(11, 11, RecoveryMarkState.REFUSED, first, removeFailed), true)
                && state.handleCount() == 6, "one failed recovery can retire its handle without clearing unrelated anchors");
        var newMark = handle(9, 1200); removeFailed.add(newMark);
        check(!state.accept(snapshot(11, 12, RecoveryMarkState.MARKED, newMark, removeFailed), true), "heartbeat nonce cannot invent another registration");
        check(state.accept(snapshot(12, 12, RecoveryMarkState.MARKED, newMark, removeFailed), true), "fresh higher token fills a freed registration slot");
        var reordered = new ArrayList<>(removeFailed); java.util.Collections.reverse(reordered);
        check(state.accept(snapshot(12, 13, RecoveryMarkState.MARKED, newMark, reordered), true)
                && state.handles().equals(reordered), "authoritative ordering may change without rebinding identities");
        for (int i = 0; i < 31; i++) check(state.tick(false, true) == null, "heartbeat gap leaves multi-handle ownership intact");
        check(!state.markerVisible() && state.handleCount() == 7, "silence hides all cosmetics without erasing any server registrations");
        check(state.accept(snapshot(12, 14, RecoveryMarkState.MARKED, newMark, reordered), true)
                && state.markerVisible() && state.remainingTicks() == 1169, "resumed heartbeat never replenishes any existing local lease");
        var invalidated = reordered.removeLast();
        check(state.accept(snapshot(12, 15, RecoveryMarkState.CLEARED, invalidated, reordered), true)
                && state.handleCount() == 6, "server expiry or merge invalidation clears only the named handle and preserves current input");
        state.cancel(71, 12);
        check(state.active() == null && state.handles().isEmpty() && !state.accept(snapshot(12, 16, RecoveryMarkState.MARKED, newMark, reordered), true),
                "owner cancellation retires the complete session and fences delayed snapshots");
        state.resetSession();
        check(state.accept(snapshot(1, 1, RecoveryMarkState.MARKED, first, List.of(first)), true), "verified connection reset clears handle replay provenance");

        var expiring = new RecoveryMarkState(); var shortMark = handle(1, 2); var longMark = handle(2, 5);
        check(expiring.accept(snapshot(1, 1, RecoveryMarkState.MARKED, shortMark, List.of(shortMark)), true), "short first lease admitted");
        expiring.tick(false, true);
        check(expiring.accept(snapshot(2, 2, RecoveryMarkState.MARKED, longMark, List.of(handle(1, 1), longMark)), true)
                && expiring.remainingTicks() == 5 && expiring.remainingTicks(101) == 1, "later registration extends total ownership but not the older handle");
        check(expiring.tick(false, true) == null && expiring.handleCount() == 1 && expiring.handles().getFirst().token() == 102,
                "first expiry removes only its own marker and preserves input for the longest lease");
        check(!expiring.accept(snapshot(2, 3, RecoveryMarkState.MARKED, longMark, List.of(handle(1, 1), handle(2, 4))), true),
                "a locally expired handle never revives from a delayed heartbeat");
        check(expiring.accept(snapshot(2, 3, RecoveryMarkState.MARKED, handle(2, 4), List.of(handle(2, 4))), true), "snapshot excluding expired handle keeps surviving ownership");
        check(!expiring.accept(snapshot(2, 4, RecoveryMarkState.MARKED, handle(2, 5), List.of(handle(2, 5))), true), "server snapshot cannot increase individual remaining lifetime");
        for (int i = 0; i < 3; i++) check(expiring.tick(false, true) == null, "last handle keeps input until its own expiry");
        check(expiring.tick(false, true) != null && expiring.active() == null && expiring.labelHandle(null) == null, "last independent expiry retires the owner exactly once");
    }
}
