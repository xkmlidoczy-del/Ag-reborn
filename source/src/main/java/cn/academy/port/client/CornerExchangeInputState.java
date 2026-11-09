package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** NEW333 native down/up ownership. Both edges are sent after native movement; only release may act. */
public final class CornerExchangeInputState {
    public static final int ACK_TIMEOUT_TICKS = 60, PAIR_LIFE_TICKS = cn.academy.port.highpower.core.CornerExchangeRules.MARK_LIFE_TICKS, HOLD_CLEAR_TICKS = 6;
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
    private final LinkedHashSet<HighPowerInput.Input> released = new LinkedHashSet<>();
    private HighPowerInput.Input latest, queuedStart, queuedRelease, accepted;
    private DispatchContext owner;
    private int physicalKey, remaining;
    private long highwater;
    private boolean holding, cancelling, warning, capturedReversed;

    /** A free hold-clear remains available during cooldown or an invalid source posture. */
    public boolean eligible(Gates gates) {
        return gates != null && gates.allowed() && pending.isEmpty() && !warning;
    }
    public boolean delegateEligible(Gates gates) {
        return ownsControl(gates) && (holding || queuedStart != null || queuedRelease != null || !pending.isEmpty() || accepted != null)
                || eligible(gates);
    }
    public boolean press(HighPowerInput.Input input, Gates gates) {
        if (input == null || HighPowerInput.parse(input.wire()) == null || !eligible(gates)
                || holding || queuedStart != null || queuedRelease != null || input.input() <= highwater
                || input.slot() != gates.slot || input.actor() != gates.context.actor || input.epoch() != gates.context.ownerEpoch
                || latest != null && !ownsControl(gates)) return false;
        owner = gates.context; physicalKey = gates.physicalKey; latest = queuedStart = input;
        highwater = input.input(); holding = true; capturedReversed = gates.crouching; return true;
    }
    /** Authentication is the owned native identity; no crouch, duration, target or facing is client-supplied. */
    public void release(HighPowerInput.Input input) {
        if (holding && latest != null && latest.equals(input)) {
            holding = false;
            if (queuedStart != null || pending.containsKey(input)) queuedRelease = input;
            compact();
        }
    }
    public void releaseWhenUp(boolean physicalDown) { if (!physicalDown) release(latest); }
    public boolean flushStartPost(Gates gates, Consumer<HighPowerInput.Input> sendStart, Consumer<HighPowerInput.Input> sendAbort) {
        if (queuedStart == null) return false;
        if (!ownsControl(gates)) { cancelBeforeClear(sendAbort); return false; }
        var captured = queuedStart; queuedStart = null; pending.put(captured, 0); capturedReversed = gates.crouching;
        Objects.requireNonNull(sendStart).accept(captured); return true;
    }
    public boolean flushReleasePost(Gates gates, Consumer<HighPowerInput.Input> sendRelease, Consumer<HighPowerInput.Input> sendAbort) {
        if (queuedRelease == null || queuedStart != null) return false;
        if (!ownsControl(gates)) { cancelBeforeClear(sendAbort); return false; }
        var captured = queuedRelease; queuedRelease = null;
        if (!pending.containsKey(captured)) return false;
        released.add(captured); Objects.requireNonNull(sendRelease).accept(captured); return true;
    }
    public boolean ownsControl(Gates gates) {
        return gates != null && gates.allowed() && latest != null && latest.slot() == gates.slot
                && physicalKey == gates.physicalKey && sameContext(owner, gates.context);
    }
    public boolean permits(long input, long epoch) {
        return remaining > 0 && matches(accepted, input, epoch)
                || pending.entrySet().stream().anyMatch(p -> matches(p.getKey(), input, epoch) && p.getValue() < ACK_TIMEOUT_TICKS);
    }
    /** A held gesture can only receive a terminal clear; a pair heartbeat must not authenticate its unreleased action. */
    public boolean permitsReceipt(long input, long epoch, boolean terminal) {
        return permits(input, epoch) && (terminal || matches(accepted, input, epoch)
                || released.stream().anyMatch(p -> matches(p, input, epoch)));
    }
    public void accepted(long input, long epoch, int remainingTicks, boolean isWarning) {
        if (remainingTicks <= 0 || remainingTicks > PAIR_LIFE_TICKS || !permitsReceipt(input, epoch, false)) return;
        var identity = pending.keySet().stream().filter(p -> matches(p, input, epoch)).findFirst().orElse(null);
        boolean existingPair = accepted != null;
        if (identity != null) {
            pending.keySet().removeIf(p -> p.epoch() == epoch && p.input() <= input);
            released.removeIf(p -> p.epoch() == epoch && p.input() <= input); accepted = identity;
        }
        remaining = existingPair ? Math.min(remaining, remainingTicks) : remainingTicks;
        warning = isWarning;
    }
    public void ended(long input, long epoch) {
        released.removeIf(p -> p.epoch() == epoch && p.input() <= input);
        pending.keySet().removeIf(p -> p.epoch() == epoch && p.input() <= input);
        if (matches(queuedRelease, input, epoch)) queuedRelease = null;
        if (accepted != null && accepted.epoch() == epoch && accepted.input() <= input) { accepted = null; remaining = 0; warning = false; }
        compact();
    }
    public void tick(Gates gates, boolean paused, Consumer<HighPowerInput.Input> sendAbort) {
        if (latest == null) return;
        if (!ownsControl(gates)) { cancelBeforeClear(sendAbort); return; }
        if (!paused) { pending.replaceAll((identity, age) -> age + 1); if (remaining > 0) remaining--; }
        if (pending.containsValue(ACK_TIMEOUT_TICKS) || remaining == 0 && accepted != null) cancelBeforeClear(sendAbort);
    }
    public boolean cancel(HighPowerInput.Input input, Consumer<HighPowerInput.Input> sendAbort) {
        return latest != null && latest.equals(input) && cancelBeforeClear(sendAbort);
    }
    public boolean cancelBeforeClear(Consumer<HighPowerInput.Input> sendAbort) {
        if (latest == null || cancelling) return false;
        var captured = latest; var identities = new LinkedHashSet<HighPowerInput.Input>();
        if (accepted != null) identities.add(accepted);
        identities.addAll(pending.keySet()); identities.add(captured); cancelling = true;
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
    public HighPowerInput.Input queuedStart() { return queuedStart; }
    public HighPowerInput.Input queuedRelease() { return queuedRelease; }
    public int acceptedRemainingTicks() { return remaining; }
    public int physicalKey() { return physicalKey; }
    public boolean holding() { return holding; }
    public boolean warning() { return warning; }
    public boolean previewReversed(boolean liveCrouch) {
        return holding || queuedStart != null || queuedRelease != null || !pending.isEmpty() ? capturedReversed : liveCrouch;
    }
    public int pendingCount() { return pending.size(); }
    public void reset() { clearOwnership(); highwater = 0; }
    private void compact() { if (!holding && queuedStart == null && queuedRelease == null && pending.isEmpty() && accepted == null) clearOwnership(); }
    private void clearOwnership() {
        latest = queuedStart = queuedRelease = accepted = null; owner = null; physicalKey = remaining = 0;
        holding = warning = capturedReversed = false; pending.clear(); released.clear();
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
