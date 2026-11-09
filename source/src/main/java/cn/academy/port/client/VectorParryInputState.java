package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.util.Objects;
import java.util.function.Consumer;

/** NEW412 authentic held edges. Native movement precedes Post; only receipts create defense visuals. */
public final class VectorParryInputState {
    public record DispatchContext(Object world, Object player, Object connection, long session,
                                  int actor, long ownerEpoch, int preset, long revision, String mappings) {}
    public record Gates(DispatchContext context, int slot, int physicalKey, boolean inGame,
                        boolean categoryActivated, boolean canUseAbility, boolean mappedUsable,
                        boolean skillEnabled, boolean skillMapped, boolean screenClosed,
                        boolean focused, boolean cameraOwned, boolean cooldownReady,
                        boolean sourceEligible, boolean paymentReady) {
        public boolean allowed() {
            return context != null && context.world != null && context.player != null && context.connection != null
                    && context.session > 0 && context.actor >= 0 && context.ownerEpoch > 0
                    && slot >= 0 && slot < 4 && physicalKey != 0 && inGame && categoryActivated
                    && canUseAbility && mappedUsable && skillEnabled && skillMapped && screenClosed
                    && focused && cameraOwned && sourceEligible;
        }
    }
    private HighPowerInput.Input latest, queuedStart, queuedRelease;
    private DispatchContext owner;
    private int physicalKey;
    private long highwater;
    private boolean holding, sent, accepted, cancelling;

    /** Fresh presses and unsent Post starts cannot borrow the held controller's cooldown exemption. */
    public boolean eligible(Gates gates) {
        return gates != null && gates.allowed() && gates.cooldownReady && gates.paymentReady;
    }
    public boolean delegateEligible(Gates gates) {
        return ownsControl(gates) || eligible(gates);
    }
    public boolean press(HighPowerInput.Input input, Gates gates) {
        if (input == null || HighPowerInput.parse(input.wire()) == null || !eligible(gates)
                || latest != null || input.input() <= highwater || input.slot() != gates.slot
                || input.actor() != gates.context.actor || input.epoch() != gates.context.ownerEpoch) return false;
        latest = queuedStart = input; owner = gates.context; physicalKey = gates.physicalKey;
        highwater = input.input(); holding = true; return true;
    }
    /** Release immediately fences delayed visual receipts, but its packet waits for native Post movement. */
    public boolean release(HighPowerInput.Input input) {
        if (!holding || latest == null || !latest.equals(input)) return false;
        holding = false;
        if (queuedStart != null || sent) queuedRelease = latest;
        else clearOwnership();
        return true;
    }
    /** Snapshot replacement can remove the old delegate, so keep polling its original physical key. */
    public HighPowerInput.Input releaseWhenUp(boolean physicalDown) {
        var captured = latest;
        return !physicalDown && release(captured) ? captured : null;
    }
    public boolean flushStartPost(Gates gates, Consumer<HighPowerInput.Input> sendStart,
                                  Consumer<HighPowerInput.Input> sendAbort) {
        if (queuedStart == null) return false;
        if (!ownsControl(gates) || !eligible(gates)) { cancelBeforeClear(sendAbort); return false; }
        var captured = queuedStart; queuedStart = null; sent = true;
        Objects.requireNonNull(sendStart).accept(captured); return true;
    }
    /** A same-tick down/up pair sends start followed by release once, never release before start. */
    public boolean flushReleasePost(Gates gates, Consumer<HighPowerInput.Input> sendRelease,
                                    Consumer<HighPowerInput.Input> sendAbort) {
        if (queuedRelease == null || queuedStart != null) return false;
        if (!ownsControl(gates)) { cancelBeforeClear(sendAbort); return false; }
        var captured = queuedRelease; queuedRelease = null;
        if (!sent) { clearOwnership(); return false; }
        Objects.requireNonNull(sendRelease).accept(captured);
        if (latest == captured) clearOwnership();
        return true;
    }
    /** Payment and the one-tick cooldown renewed by this hold do not revoke its input ownership. */
    public boolean ownsControl(Gates gates) {
        return gates != null && gates.allowed() && latest != null && latest.slot() == gates.slot
                && physicalKey == gates.physicalKey && sameContext(owner, gates.context);
    }
    public boolean permits(long input, long epoch) {
        return holding && sent && matches(latest, input, epoch);
    }
    public void accepted(long input, long epoch) { if (permits(input, epoch)) accepted = true; }
    /** Server end cannot synthesize a fresh press while the real key remains held. */
    public void ended(long input, long epoch) {
        if (!matches(latest, input, epoch)) return;
        queuedStart = queuedRelease = null; sent = accepted = false;
        if (!holding) clearOwnership();
    }
    public void tick(Gates gates, Consumer<HighPowerInput.Input> sendAbort) {
        if (latest != null && !ownsControl(gates)) cancelBeforeClear(sendAbort);
    }
    /** Visual lease expiry is cosmetic; the original identity still owns the next physical release. */
    public void visualLost(long input, long epoch) {
        if (matches(latest, input, epoch)) accepted = false;
    }
    /** An old delegate cannot turn an already queued normal release into an abort. */
    public boolean cancel(HighPowerInput.Input input, Consumer<HighPowerInput.Input> sendAbort) {
        return (holding || queuedStart != null) && latest != null && latest.equals(input) && cancelBeforeClear(sendAbort);
    }
    public boolean cancelBeforeClear(Consumer<HighPowerInput.Input> sendAbort) {
        if (latest == null || cancelling) return false;
        var captured = latest; cancelling = true;
        try {
            Objects.requireNonNull(sendAbort).accept(captured);
            if (latest == captured) clearOwnership();
            return true;
        } finally { cancelling = false; }
    }
    public boolean ownsEnvironment(Object world, Object player, Object connection) {
        return owner != null && owner.world == world && owner.player == player && owner.connection == connection;
    }
    public HighPowerInput.Input current() { return latest; }
    public HighPowerInput.Input queuedStart() { return queuedStart; }
    public HighPowerInput.Input queuedRelease() { return queuedRelease; }
    public int physicalKey() { return physicalKey; }
    public boolean holding() { return holding; }
    public int pendingCount() { return sent && !accepted ? 1 : 0; }
    public void reset() { clearOwnership(); highwater = 0; }
    private void clearOwnership() {
        latest = queuedStart = queuedRelease = null; owner = null; physicalKey = 0;
        holding = sent = accepted = false;
    }
    private static boolean matches(HighPowerInput.Input identity, long input, long epoch) {
        return identity != null && identity.input() == input && identity.epoch() == epoch;
    }
    private static boolean sameContext(DispatchContext a, DispatchContext b) {
        return a != null && b != null && a.world == b.world && a.player == b.player && a.connection == b.connection
                && a.session == b.session && a.actor == b.actor && a.ownerEpoch == b.ownerEpoch
                && a.preset == b.preset && a.revision == b.revision && Objects.equals(a.mappings, b.mappings);
    }
}
