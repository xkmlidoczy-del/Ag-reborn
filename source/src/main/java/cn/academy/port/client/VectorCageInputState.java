package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

/** NEW441 two short releases, or a server-measured hold to clear. No local cast prediction. */
public final class VectorCageInputState {
    public record DispatchContext(Object world, Object player, Object connection, long session,
                                  int actor, long ownerEpoch, int preset, long revision, String mappings) {}
    public record Gates(DispatchContext context, int slot, int physicalKey, boolean inGame,
                        boolean categoryActivated, boolean canUseAbility, boolean mappedUsable,
                        boolean skillEnabled, boolean skillMapped, boolean screenClosed,
                        boolean focused, boolean cameraOwned, boolean cooldownReady) {
        public boolean allowed() {
            return context != null && context.world != null && context.player != null && context.connection != null
                    && context.session > 0 && context.actor >= 0 && context.ownerEpoch > 0
                    && slot >= 0 && slot < 4 && physicalKey != 0 && inGame && categoryActivated && canUseAbility
                    && mappedUsable && skillEnabled && skillMapped && screenClosed && focused && cameraOwned;
        }
    }
    private HighPowerInput.Input latest, pending, accepted, queued, release;
    private DispatchContext owner;
    private int physicalKey, acceptedPhase = -1, requestPhase = -1;
    private long highwater;
    private boolean holding, sent, released, cancelling;

    public boolean eligible(Gates g) { return g != null && g.allowed() && (g.cooldownReady || ownsControl(g)); }
    public boolean delegateEligible(Gates g) { return eligible(g); }
    public boolean ownsControl(Gates g) {
        return g != null && g.allowed() && latest != null && latest.slot() == g.slot
                && latest.actor() == g.context.actor && latest.epoch() == g.context.ownerEpoch
                && physicalKey == g.physicalKey && sameContext(owner, g.context);
    }
    /** Only a fresh physical down edge can create a request. Pending acknowledgements never replay it. */
    public boolean press(HighPowerInput.Input input, Gates g) {
        if (input == null || HighPowerInput.parse(input.wire()) == null || !eligible(g) || holding || cancelling
                || pending != null || queued != null || release != null || input.input() <= highwater
                || input.slot() != g.slot || input.actor() != g.context.actor || input.epoch() != g.context.ownerEpoch
                || latest != null && !ownsControl(g)) return false;
        owner = g.context; physicalKey = g.physicalKey; highwater = input.input();
        latest = pending = queued = input; holding = true; sent = released = false; requestPhase = acceptedPhase;
        return true;
    }
    public void release(HighPowerInput.Input input) {
        if (pending != null && pending.equals(input) && holding) { holding = false; release = input; }
    }
    public void releaseWhenUp(boolean physicalDown) { if (!physicalDown) release(pending); }
    /** Native Post dispatch preserves down-before-up even when both edges arrived in one client tick. */
    public boolean flushPost(Gates g, Consumer<HighPowerInput.Input> sendStart,
                             Consumer<HighPowerInput.Input> sendRelease, Consumer<HighPowerInput.Input> sendAbort) {
        if (latest == null || queued == null && release == null) return false;
        if (!ownsControl(g)) { cancelBeforeClear(sendAbort); return false; }
        boolean dispatched = false;
        if (queued != null) {
            var captured = queued; queued = null; sent = true;
            Objects.requireNonNull(sendStart).accept(captured); dispatched = true;
        }
        if (release != null && pending != null && sent) {
            var captured = release; release = null; released = true;
            Objects.requireNonNull(sendRelease).accept(captured); dispatched = true;
        }
        return dispatched;
    }
    public boolean permits(long input, long epoch) {
        return matches(accepted, input, epoch) || sent && matches(pending, input, epoch);
    }
    /** Confirmation retains the original server mark identity and frozen crouch mode. */
    public void accepted(long input, long epoch, int phase) {
        if (phase != VectorCageState.PREVIEW && phase != VectorCageState.STATE || !permits(input, epoch)) return;
        if (matches(pending, input, epoch) && accepted == null) {
            accepted = pending; acceptedPhase = phase; finishRequest();
        } else if (matches(accepted, input, epoch)) {
            if (acceptedPhase == VectorCageState.STATE && phase == VectorCageState.PREVIEW) return;
            acceptedPhase = phase;
            if (phase == VectorCageState.STATE && requestPhase == VectorCageState.PREVIEW && released) finishRequest();
        }
    }
    public void rejected(long input, long epoch) {
        if (matches(pending, input, epoch) && !matches(accepted, input, epoch)) finishRequest();
    }
    public void ended(long input, long epoch) {
        if (matches(accepted, input, epoch)) clearOwnership();
        else if (matches(pending, input, epoch)) finishRequest();
    }
    /** Silence does not turn a cosmetic timeout into a destructive server abort. */
    public void tick(Gates g, boolean paused, Consumer<HighPowerInput.Input> sendAbort) {
        if (latest != null && !ownsControl(g)) cancelBeforeClear(sendAbort);
    }
    public boolean cancel(HighPowerInput.Input input, Consumer<HighPowerInput.Input> sendAbort) {
        return input != null && (input.equals(latest) || input.equals(accepted)) && cancelBeforeClear(sendAbort);
    }
    public boolean cancelBeforeClear(Consumer<HighPowerInput.Input> sendAbort) {
        if (latest == null || cancelling) return false;
        cancelling = true;
        try {
            var ids = new ArrayList<HighPowerInput.Input>(2);
            if (accepted != null) ids.add(accepted);
            if (pending != null && !ids.contains(pending)) ids.add(pending);
            if (ids.isEmpty()) ids.add(latest);
            // Keep the exact original and latest identity available while the aborts are dispatched.
            var captured = latest;
            for (var id : ids) Objects.requireNonNull(sendAbort).accept(id);
            if (latest == captured) clearOwnership();
            return true;
        } finally { cancelling = false; }
    }
    public boolean ownsEnvironment(Object world, Object player, Object connection) {
        return owner != null && owner.world == world && owner.player == player && owner.connection == connection;
    }
    public HighPowerInput.Input current() { return latest; }
    public HighPowerInput.Input acceptedIdentity() { return accepted; }
    public HighPowerInput.Input queuedStart() { return queued; }
    public HighPowerInput.Input queuedRelease() { return release; }
    public boolean holding() { return holding; }
    public int physicalKey() { return physicalKey; }
    public int pendingCount() { return pending == null ? 0 : 1; }
    public void reset() { clearOwnership(); highwater = 0; }
    private void finishRequest() {
        pending = queued = release = null; holding = sent = released = false; requestPhase = -1;
        latest = accepted; if (latest == null) clearOwnership();
    }
    private void clearOwnership() {
        latest = pending = accepted = queued = release = null; owner = null; physicalKey = 0;
        acceptedPhase = requestPhase = -1; holding = sent = released = false;
    }
    private static boolean matches(HighPowerInput.Input id, long input, long epoch) {
        return id != null && id.input() == input && id.epoch() == epoch;
    }
    private static boolean sameContext(DispatchContext a, DispatchContext b) {
        return a != null && b != null && a.world == b.world && a.player == b.player && a.connection == b.connection
                && a.session == b.session && a.actor == b.actor && a.ownerEpoch == b.ownerEpoch
                && a.preset == b.preset && a.revision == b.revision && Objects.equals(a.mappings, b.mappings);
    }
}
