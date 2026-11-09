package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.util.Objects;
import java.util.function.Consumer;

/** NEW413 fresh authenticated edges, dispatched after vanilla movement/crouch packets. */
public final class BrakingTouchInputState {
    public static final int ACK_TIMEOUT_TICKS = 60;
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
    private int physicalKey, age;
    private long highwater;
    private boolean holding, sent, accepted, crouched, completed, cancelling;

    public boolean eligible(Gates gates) {
        return gates != null && gates.allowed() && gates.cooldownReady && gates.paymentReady;
    }
    /** Only the existing identity inherits its own payment/cooldown exemption. */
    public boolean delegateEligible(Gates gates) { return ownsControl(gates) || eligible(gates); }
    public boolean press(HighPowerInput.Input input, Gates gates) {
        if (input == null || HighPowerInput.parse(input.wire()) == null || !eligible(gates)
                || latest != null && !replaceable(gates) || input.input() <= highwater || input.slot() != gates.slot
                || input.actor() != gates.context.actor || input.epoch() != gates.context.ownerEpoch) return false;
        if (latest != null) clearOwnership();
        latest = queuedStart = input; owner = gates.context; physicalKey = gates.physicalKey;
        highwater = input.input(); holding = true; return true;
    }
    /** A cosmetic lease cannot extend gameplay cooldown; pending, held or unspent inputs stay exclusive. */
    private boolean replaceable(Gates gates) {
        return !holding && sent && accepted && completed && queuedStart == null && queuedRelease == null && ownsControl(gates);
    }
    /** Returns whether this release must immediately hide/fence an unspent landing arm. */
    public boolean release(HighPowerInput.Input input) {
        if (!holding || latest == null || !latest.equals(input)) return false;
        holding = false;
        boolean fenceArm = sent && crouched && !completed;
        if (queuedStart != null || sent) queuedRelease = latest;
        else compact();
        return fenceArm;
    }
    public HighPowerInput.Input releaseWhenUp(boolean physicalDown) {
        var captured = latest;
        return !physicalDown && release(captured) ? captured : null;
    }
    /** Crouch is sampled here only for receipt ownership; the wire carries identity only. */
    public boolean flushStartPost(Gates gates, boolean vanillaCrouching,
                                  Consumer<HighPowerInput.Input> sendStart, Consumer<HighPowerInput.Input> sendAbort) {
        if (queuedStart == null) return false;
        if (!ownsControl(gates) || !eligible(gates)) { cancelBeforeClear(sendAbort); return false; }
        var captured = queuedStart; queuedStart = null; sent = true; crouched = vanillaCrouching; age = 0;
        Objects.requireNonNull(sendStart).accept(captured); return true;
    }
    /** A same-tick tap sends start then end; released taps retain their authenticated visual receipt. */
    public boolean flushReleasePost(Gates gates, Consumer<HighPowerInput.Input> sendRelease,
                                    Consumer<HighPowerInput.Input> sendAbort) {
        if (queuedRelease == null || queuedStart != null) return false;
        if (!ownsControl(gates)) { cancelBeforeClear(sendAbort); return false; }
        var captured = queuedRelease; queuedRelease = null;
        if (!sent) { compact(); return false; }
        Objects.requireNonNull(sendRelease).accept(captured);
        if (latest == captured && crouched && !completed) { sent = accepted = false; compact(); }
        return true;
    }
    public boolean ownsControl(Gates gates) {
        return gates != null && gates.allowed() && latest != null && latest.slot() == gates.slot
                && physicalKey == gates.physicalKey && sameContext(owner, gates.context);
    }
    public boolean permits(long input, long epoch) {
        return sent && matches(latest, input, epoch) && (!crouched || holding || completed);
    }
    public void accepted(long input, long epoch, int phase) {
        if (!permits(input, epoch)) return;
        accepted = true;
        if (phase == BrakingTouchState.BRAKED || phase == BrakingTouchState.LANDED) completed = true;
    }
    /** No automatic repeat when a server end or expiry arrives while the real key is still down. */
    public void ended(long input, long epoch) {
        if (!matches(latest, input, epoch)) return;
        queuedStart = queuedRelease = null; sent = accepted = completed = false; compact();
    }
    public void tick(Gates gates, boolean paused, Consumer<HighPowerInput.Input> sendAbort) {
        if (latest == null) return;
        if (!ownsControl(gates)) { cancelBeforeClear(sendAbort); return; }
        if (sent && !paused && ++age >= ACK_TIMEOUT_TICKS) cancelBeforeClear(sendAbort);
    }
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
    private void compact() { if (!holding && !sent && queuedStart == null && queuedRelease == null) clearOwnership(); }
    private void clearOwnership() {
        latest = queuedStart = queuedRelease = null; owner = null; physicalKey = age = 0;
        holding = sent = accepted = crouched = completed = false;
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
