package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** NEW323 fresh presses follow native movement; the current nonempty handle snapshot outlives release. */
public final class RecoveryMarkInputState {
    public static final int MAX_PENDING = 16, ACK_TIMEOUT_TICKS = 60, MARK_LIFE_TICKS = 1200;
    public record DispatchContext(Object world, Object player, Object connection, long session,
                                  int actor, long ownerEpoch, int preset, long revision, String mappings) {}
    public record Gates(DispatchContext context, int slot, int physicalKey, boolean inGame,
                        boolean categoryActivated, boolean canUseAbility, boolean mappedUsable,
                        boolean skillEnabled, boolean skillMapped, boolean screenClosed,
                        boolean focused, boolean cameraOwned, boolean cooldownReady,
                        boolean sourceEligible, boolean crouching) {
        public boolean allowed() {
            return context != null && context.world != null && context.player != null && context.connection != null
                    && context.session > 0 && context.actor >= 0 && context.ownerEpoch > 0
                    && slot >= 0 && slot < 4 && physicalKey != 0 && inGame && categoryActivated
                    && canUseAbility && mappedUsable && skillEnabled && skillMapped && screenClosed && focused && cameraOwned;
        }
    }
    private final Map<HighPowerInput.Input, Integer> pending = new LinkedHashMap<>();
    private HighPowerInput.Input latest, queued, acceptedMark;
    private DispatchContext owner;
    private int physicalKey, remaining;
    private long highwater;
    private boolean holding, cancelling;

    /** Clearing is free even while airborne or cooling down; ownership/context checks still apply. */
    public boolean eligible(Gates gates) {
        return gates != null && gates.allowed() && (gates.crouching || gates.cooldownReady && gates.sourceEligible
                && pending.isEmpty());
    }
    /** Classic delegate availability must not turn a sent request's own cooldown into an abort edge. */
    public boolean delegateEligible(Gates gates) {
        return ownsControl(gates) && (queued != null || !pending.isEmpty() || acceptedMark != null)
                || eligible(gates);
    }
    public boolean press(HighPowerInput.Input input, Gates gates) {
        if (input == null || HighPowerInput.parse(input.wire()) == null || !eligible(gates)
                || holding || queued != null || pending.size() >= MAX_PENDING || input.input() <= highwater
                || input.slot() != gates.slot || input.actor() != gates.context.actor || input.epoch() != gates.context.ownerEpoch
                || latest != null && !ownsControl(gates)) return false;
        owner = gates.context; physicalKey = gates.physicalKey; latest = queued = input;
        highwater = input.input(); holding = true; return true;
    }
    public void release(HighPowerInput.Input input) {
        if (latest != null && latest.equals(input)) { holding = false; compact(); }
    }
    public void releaseWhenUp(boolean physicalDown) { if (!physicalDown) release(latest); }
    public boolean flushPost(Gates gates, Consumer<HighPowerInput.Input> sendStart, Consumer<HighPowerInput.Input> sendAbort) {
        if (queued == null) return false;
        if (!ownsControl(gates)) { cancelBeforeClear(sendAbort); return false; }
        if (!eligible(gates)) { discardUnsent(sendAbort); return false; }
        var captured = queued;
        pending.put(captured, 0); queued = null;
        Objects.requireNonNull(sendStart).accept(captured); compact(); return true;
    }
    /** Ground state and this skill's cooldown never revoke a stored anchor or a sent request. */
    public boolean ownsControl(Gates gates) {
        return gates != null && gates.allowed() && latest != null && latest.slot() == gates.slot
                && physicalKey == gates.physicalKey && sameContext(owner, gates.context);
    }
    public boolean permits(long input, long epoch) {
        return remaining > 0 && matches(acceptedMark, input, epoch)
                || pending.entrySet().stream().anyMatch(p -> matches(p.getKey(), input, epoch) && p.getValue() < ACK_TIMEOUT_TICKS);
    }
    /** The visual state supplies the maximum independently clamped lease in its authoritative snapshot. */
    public void accepted(long input, long epoch, int remainingTicks) {
        if (remainingTicks <= 0 || remainingTicks > MARK_LIFE_TICKS || !permits(input, epoch)) return;
        var identity = pending.keySet().stream().filter(p -> matches(p, input, epoch)).findFirst().orElse(null);
        if (identity != null) {
            pending.keySet().removeIf(p -> p.epoch() == epoch && p.input() <= input);
            // A new acknowledged command becomes the heartbeat nonce even when it only recovered/refused one handle.
            // New handles may extend the longest lease; old handles have already been clamped individually by the visual state.
            acceptedMark = identity; remaining = remainingTicks;
        } else {
            // A heartbeat can shorten a lease, never refresh its original 1,200-tick lifetime.
            remaining = Math.min(remaining, remainingTicks);
        }
    }
    /** Server terminal receipts consume the anchor, but cannot discard a newer unsent press. */
    public void ended(long input, long epoch) {
        pending.keySet().removeIf(p -> p.epoch() == epoch && p.input() <= input);
        if (acceptedMark != null && acceptedMark.epoch() == epoch && acceptedMark.input() <= input) acceptedMark = null;
        if (acceptedMark == null) remaining = 0;
        compact();
    }
    public void tick(Gates gates, boolean paused, Consumer<HighPowerInput.Input> sendAbort) {
        if (latest == null) return;
        if (!ownsControl(gates)) { cancelBeforeClear(sendAbort); return; }
        if (queued != null && !eligible(gates)) { discardUnsent(sendAbort); if (latest == null) return; }
        if (!paused) {
            pending.replaceAll((identity, age) -> age + 1);
            if (remaining > 0) remaining--;
        }
        if (pending.containsValue(ACK_TIMEOUT_TICKS) || remaining == 0 && acceptedMark != null)
            cancelBeforeClear(sendAbort);
    }
    public boolean cancel(HighPowerInput.Input input, Consumer<HighPowerInput.Input> sendAbort) {
        return latest != null && latest.equals(input) && cancelBeforeClear(sendAbort);
    }
    public boolean cancelBeforeClear(Consumer<HighPowerInput.Input> sendAbort) {
        if (latest == null || cancelling) return false;
        var captured = latest;
        // The latest press may not have reached the server. Retire the known anchor before newer attempts.
        var identities = new LinkedHashSet<HighPowerInput.Input>();
        if (acceptedMark != null) identities.add(acceptedMark);
        identities.addAll(pending.keySet()); identities.add(captured);
        cancelling = true;
        try {
            for (var identity : identities) Objects.requireNonNull(sendAbort).accept(identity);
            if (latest == captured) clearOwnership();
            return true;
        } finally { cancelling = false; }
    }
    public boolean ownsEnvironment(Object world, Object player, Object connection) {
        return owner != null && owner.world == world && owner.player == player && owner.connection == connection;
    }
    public HighPowerInput.Input current() { return latest; }
    public HighPowerInput.Input queuedStart() { return queued; }
    public HighPowerInput.Input acceptedMark() { return acceptedMark; }
    public int acceptedRemainingTicks() { return remaining; }
    public int physicalKey() { return physicalKey; }
    public boolean holding() { return holding; }
    public int pendingCount() { return pending.size(); }
    public void reset() { clearOwnership(); highwater = 0; }
    private void discardUnsent(Consumer<HighPowerInput.Input> sendAbort) {
        if (acceptedMark == null) cancelBeforeClear(sendAbort);
        else queued = null; // A jump after Pre cannot erase an otherwise valid, already stored anchor.
    }
    private void compact() {
        if (!holding && queued == null && pending.isEmpty() && acceptedMark == null) clearOwnership();
    }
    private void clearOwnership() {
        latest = queued = acceptedMark = null; owner = null; physicalKey = remaining = 0;
        holding = false; pending.clear();
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
