package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.util.Objects;
import java.util.function.Consumer;

/** NEW422 toggle ownership, independent of visual prediction and the classic key callback policy. */
public final class InertialAnchorInputState {
    public static final int ACK_TIMEOUT_TICKS = 60, RECEIPT_TIMEOUT_TICKS = 60;
    public record DispatchContext(Object world, Object player, Object connection, long session,
                                  int actor, long ownerEpoch, int preset, long revision, String mappings) {}
    public record Press(HighPowerInput.Input identity, boolean dispatch) {}
    /** Every non-cooldown gate applies equally to a new start, held pending input and free off tap. */
    public record Gates(DispatchContext context, int slot, int physicalKey, boolean inGame,
                        boolean categoryActivated, boolean canUseAbility, boolean mappedUsable,
                        boolean skillEnabled, boolean skillMapped, boolean screenClosed,
                        boolean focused, boolean cameraOwned, boolean cooldownReady) {
        public boolean allowed() {
            return context != null && context.world != null && context.player != null && context.connection != null
                    && context.session > 0 && context.actor >= 0 && context.ownerEpoch > 0
                    && slot >= 0 && slot < 4 && physicalKey != 0 && inGame && categoryActivated
                    && canUseAbility && mappedUsable && skillEnabled && skillMapped
                    && screenClosed && focused && cameraOwned;
        }
    }
    private HighPowerInput.Input latest, pending, accepted;
    private DispatchContext owner;
    private int physicalKey, pendingTicks, receiptTicks;
    private long highwater;
    private boolean pendingOff, cancelling;

    /** Shared by production binding eligibility, dispatch and the real binding/coordinator regression. */
    public boolean eligible(Gates gates) {
        return gates != null && gates.allowed() && (gates.cooldownReady || ownsControl(gates));
    }
    public boolean ownsControl(Gates gates) {
        return gates != null && gates.allowed() && latest != null && latest.slot() == gates.slot
                && physicalKey == gates.physicalKey && latest.actor() == gates.context.actor
                && latest.epoch() == gates.context.ownerEpoch && sameContext(owner, gates.context)
                && (pending != null && pendingTicks < ACK_TIMEOUT_TICKS
                    || accepted != null && receiptTicks < RECEIPT_TIMEOUT_TICKS);
    }
    /** A suppressed pending re-press retains its original abort identity without issuing another request. */
    public Press press(HighPowerInput.Input candidate, Gates gates) {
        if (candidate == null || HighPowerInput.parse(candidate.wire()) == null || !eligible(gates)
                || candidate.input() <= highwater || candidate.slot() != gates.slot
                || candidate.actor() != gates.context.actor || candidate.epoch() != gates.context.ownerEpoch) return null;
        if (pending != null) return ownsControl(gates) ? new Press(latest, false) : null;
        return begin(candidate, gates) ? new Press(candidate, true) : null;
    }
    /** One outstanding request. A second physical edge cannot replay a start before acknowledgement. */
    public boolean begin(HighPowerInput.Input input, Gates gates) {
        if (input == null || HighPowerInput.parse(input.wire()) == null || !eligible(gates)
                || pending != null || input.input() <= highwater || input.slot() != gates.slot
                || input.actor() != gates.context.actor || input.epoch() != gates.context.ownerEpoch
                || latest != null && !ownsControl(gates)) return false;
        highwater = input.input(); latest = pending = input; owner = gates.context;
        physicalKey = gates.physicalKey; pendingTicks = 0; pendingOff = accepted != null;
        return true;
    }
    /** Physical release has no lifetime meaning for a server-owned toggle. */
    public void release(HighPowerInput.Input input) {}
    public boolean permits(long input, long epoch) {
        return input > 0 && (matches(pending, input, epoch) && pendingTicks < ACK_TIMEOUT_TICKS
                || matches(accepted, input, epoch) && receiptTicks < RECEIPT_TIMEOUT_TICKS);
    }
    /** An old accepted receipt can refresh its lease, but never acknowledge or extend a newer off request. */
    public void accepted(long input, long epoch) {
        if (!permits(input, epoch)) return;
        if (matches(pending, input, epoch)) {
            accepted = pending; pending = null; pendingOff = false; pendingTicks = 0;
        }
        if (matches(accepted, input, epoch)) receiptTicks = 0;
    }
    public void ended(long input, long epoch) {
        if (matches(accepted, input, epoch)) {
            accepted = null; receiptTicks = 0;
            if (pendingOff) { pending = null; pendingOff = false; pendingTicks = 0; }
        } else if (matches(pending, input, epoch)) {
            pending = null; pendingOff = false; pendingTicks = 0;
        }
        if (accepted == null && pending == null) clearOwnership();
    }
    /** Cosmetic loss is not an authoritative end: retain the exact owner until its abort is dispatched. */
    public boolean cancelVisual(long input, long epoch, Consumer<HighPowerInput.Input> sendAbort) {
        if (!matches(accepted, input, epoch) && !matches(pending, input, epoch)) return false;
        return cancelBeforeClear(sendAbort);
    }
    /** Also used before death cleanup discards the current server epoch. No new owner may be erased. */
    public boolean cancelBeforeClear(Consumer<HighPowerInput.Input> sendAbort) {
        if (latest == null || cancelling) return false;
        HighPowerInput.Input captured = latest;
        cancelling = true;
        try {
            Objects.requireNonNull(sendAbort).accept(captured);
            if (latest == captured) clearOwnership();
            return true;
        } finally { cancelling = false; }
    }
    /** Runs even after key-up, so GUI/category/session/preset and ability loss still abort explicitly. */
    public HighPowerInput.Input tick(Gates gates, boolean paused) {
        if (latest == null) return null;
        if (!ownsControl(gates)) return cancel();
        if (!paused) {
            if (pending != null) pendingTicks++;
            if (accepted != null) receiptTicks++;
        }
        if (pending != null && pendingTicks >= ACK_TIMEOUT_TICKS
                || accepted != null && receiptTicks >= RECEIPT_TIMEOUT_TICKS) return cancel();
        return null;
    }
    public HighPowerInput.Input cancel() {
        HighPowerInput.Input result = latest;
        clearOwnership(); return result;
    }
    public HighPowerInput.Input cancel(HighPowerInput.Input input) {
        return latest != null && latest.equals(input) ? cancel() : null;
    }
    /** Diagnostic identity only. Its presence is never sufficient to bypass cooldown. */
    public HighPowerInput.Input current() { return latest; }
    public HighPowerInput.Input acceptedIdentity() { return accepted; }
    public int pendingCount() { return pending == null ? 0 : 1; }
    public void reset() { clearOwnership(); highwater = 0; }
    private void clearOwnership() {
        latest = pending = accepted = null; owner = null; physicalKey = pendingTicks = receiptTicks = 0; pendingOff = false;
    }
    private static boolean matches(HighPowerInput.Input identity, long input, long epoch) {
        return identity != null && identity.input() == input && identity.epoch() == epoch;
    }
    private static boolean sameContext(DispatchContext before, DispatchContext current) {
        return before != null && current != null && before.world == current.world && before.player == current.player
                && before.connection == current.connection && before.session == current.session
                && before.actor == current.actor && before.ownerEpoch == current.ownerEpoch
                && before.preset == current.preset && before.revision == current.revision
                && Objects.equals(before.mappings, current.mappings);
    }
}
