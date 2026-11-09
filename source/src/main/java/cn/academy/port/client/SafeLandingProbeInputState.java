package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.util.Objects;
import java.util.function.Consumer;

/** NEW313 fresh-edge preview/confirm ownership; a receipt never moves or aims the client. */
public final class SafeLandingProbeInputState {
    public static final int ACK_TIMEOUT_TICKS = 60, QUIET_TICKS = 12, PREVIEW_TICKS = cn.academy.port.highpower.core.SafeLandingProbeRules.PREVIEW_TICKS;
    public record DispatchContext(Object world, Object player, Object connection, long session,
                                  int actor, long ownerEpoch, int preset, long revision, String mappings,
                                  Object pose, double x, double y, double z) {}
    public record Press(HighPowerInput.Input identity, boolean dispatch) {}
    public record Gates(DispatchContext context, int slot, int physicalKey, boolean inGame,
                        boolean categoryActivated, boolean canUseAbility, boolean mappedUsable,
                        boolean skillEnabled, boolean skillMapped, boolean screenClosed,
                        boolean focused, boolean cameraOwned, boolean cooldownReady) {
        public boolean allowed() {
            return context != null && context.world != null && context.player != null && context.connection != null
                    && context.session > 0 && context.actor >= 0 && context.ownerEpoch > 0 && context.pose != null
                    && Double.isFinite(context.x) && Double.isFinite(context.y) && Double.isFinite(context.z)
                    && slot >= 0 && slot < 4 && physicalKey != 0 && inGame && categoryActivated
                    && canUseAbility && mappedUsable && skillEnabled && skillMapped
                    && screenClosed && focused && cameraOwned;
        }
    }
    private HighPowerInput.Input latest, pending, accepted;
    private DispatchContext owner;
    private int physicalKey, pendingTicks, quietTicks, remaining;
    private long highwater;
    private boolean cancelling;

    public boolean eligible(Gates gates) {
        return gates != null && gates.allowed() && (gates.cooldownReady || ownsControl(gates));
    }
    public boolean ownsControl(Gates gates) {
        return gates != null && gates.allowed() && latest != null && latest.slot() == gates.slot
                && physicalKey == gates.physicalKey && latest.actor() == gates.context.actor
                && latest.epoch() == gates.context.ownerEpoch && sameContext(owner, gates.context)
                && (pending != null && pendingTicks < ACK_TIMEOUT_TICKS
                    || accepted != null && remaining > 0 && quietTicks < QUIET_TICKS);
    }
    /** Repress before acknowledgement is suppressed. Only a received preview permits confirmation. */
    public Press press(HighPowerInput.Input candidate, Gates gates) {
        if (candidate == null || HighPowerInput.parse(candidate.wire()) == null || !eligible(gates)
                || candidate.input() <= highwater || candidate.slot() != gates.slot
                || candidate.actor() != gates.context.actor || candidate.epoch() != gates.context.ownerEpoch) return null;
        if (pending != null) return ownsControl(gates) ? new Press(latest, false) : null;
        if (latest != null && !ownsControl(gates)) return null;
        if (latest == null) { owner = gates.context; physicalKey = gates.physicalKey; }
        latest = pending = candidate; highwater = candidate.input(); pendingTicks = 0;
        return new Press(candidate, true);
    }
    /** Release is inert. The classic coordinator supplies the next genuinely fresh physical edge. */
    public void release(HighPowerInput.Input input) {}
    public boolean permits(long input, long epoch) {
        return input > 0 && (matches(pending, input, epoch) && pendingTicks < ACK_TIMEOUT_TICKS
                || matches(accepted, input, epoch) && remaining > 0 && quietTicks < QUIET_TICKS);
    }
    /** A heartbeat can shorten the frozen expiry, but can never extend its original lifetime. */
    public void preview(long input, long epoch, int nativeRemaining) {
        if (!permits(input, epoch) || nativeRemaining < 1 || nativeRemaining > PREVIEW_TICKS) return;
        if (accepted == null && matches(pending, input, epoch)) {
            accepted = pending; pending = null; pendingTicks = 0; remaining = nativeRemaining;
        } else if (matches(accepted, input, epoch)) remaining = Math.min(remaining, nativeRemaining);
        if (matches(accepted, input, epoch)) quietTicks = 0;
    }
    /** SUCCESS, REFUSED and authoritative ENDED resolve control without sending an abort. */
    public void ended(long input, long epoch) {
        if (matches(accepted, input, epoch) || matches(pending, input, epoch)) clearOwnership();
    }
    public boolean cancelVisual(long input, long epoch, Consumer<HighPowerInput.Input> sendAbort) {
        if (!matches(accepted, input, epoch) && !matches(pending, input, epoch)) return false;
        return cancelBeforeClear(sendAbort);
    }
    public boolean cancel(HighPowerInput.Input identity, Consumer<HighPowerInput.Input> sendAbort) {
        return latest != null && latest.equals(identity) && cancelBeforeClear(sendAbort);
    }
    /** Transport executes while the exact latest identity is retained; a failure permits a retry. */
    public boolean cancelBeforeClear(Consumer<HighPowerInput.Input> sendAbort) {
        if (latest == null || cancelling) return false;
        var captured = latest; cancelling = true;
        try {
            Objects.requireNonNull(sendAbort).accept(captured);
            if (latest == captured) clearOwnership();
            return true;
        } finally { cancelling = false; }
    }
    public void tick(Gates gates, boolean paused, Consumer<HighPowerInput.Input> sendAbort) {
        if (latest == null) return;
        if (!ownsControl(gates)) { cancelBeforeClear(sendAbort); return; }
        if (!paused) {
            if (pending != null) pendingTicks++;
            if (accepted != null) { quietTicks++; remaining = Math.max(0, remaining - 1); }
        }
        if (pending != null && pendingTicks >= ACK_TIMEOUT_TICKS
                || accepted != null && (quietTicks >= QUIET_TICKS || remaining == 0)) cancelBeforeClear(sendAbort);
    }
    public boolean ownsEnvironment(Object world, Object player, Object connection) {
        return owner != null && owner.world == world && owner.player == player && owner.connection == connection;
    }
    public HighPowerInput.Input current() { return latest; }
    public HighPowerInput.Input acceptedIdentity() { return accepted; }
    public int pendingCount() { return pending == null ? 0 : 1; }
    public int remainingTicks() { return remaining; }
    public void reset() { clearOwnership(); highwater = 0; }
    private void clearOwnership() {
        latest = pending = accepted = null; owner = null;
        physicalKey = pendingTicks = quietTicks = remaining = 0;
    }
    private static boolean matches(HighPowerInput.Input identity, long input, long epoch) {
        return identity != null && identity.input() == input && identity.epoch() == epoch;
    }
    private static boolean sameContext(DispatchContext before, DispatchContext current) {
        if (before == null || current == null) return false;
        double dx = before.x - current.x, dy = before.y - current.y, dz = before.z - current.z;
        return before.world == current.world && before.player == current.player && before.connection == current.connection
                && before.session == current.session && before.actor == current.actor && before.ownerEpoch == current.ownerEpoch
                && before.preset == current.preset && before.revision == current.revision
                && Objects.equals(before.mappings, current.mappings) && before.pose == current.pose
                && dx * dx + dy * dy + dz * dz <= .25 * .25;
    }
}
