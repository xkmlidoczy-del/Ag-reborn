package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** NEW351 native key edges; the server alone measures holds, captures points and starts rounds. */
public final class MultidimensionalLandingArrayInputState {
    public static final int ACK_TIMEOUT_TICKS = 60, MARK_LIFE_TICKS = cn.academy.port.highpower.core.MultidimensionalLandingArrayRules.PREPARATION_TICKS, HOLD_TICKS = 6;
    // Physical rounds stop at160. This240-tick lease only tolerates transport/end delivery latency.
    // It cannot extend a server round, body lease, preparation or damage window.
    public static final int ROUND_OWNERSHIP_TICKS = 240;
    public record DispatchContext(Object world, Object player, Object connection, long session,
                                  int actor, long ownerEpoch, int preset, long revision, String mappings) {}
    public record Gates(DispatchContext context, int slot, int physicalKey, boolean inGame,
                        boolean categoryActivated, boolean canUseAbility, boolean mappedUsable,
                        boolean skillEnabled, boolean skillMapped, boolean screenClosed, boolean ownedPicker,
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
    private boolean holding, cancelling, firing, freeOrigin;

    /** Clear holds remain available during cooldown and during an active fixed round. */
    public boolean eligible(Gates gates) {
        return gates != null && gates.allowed() && pending.isEmpty() && !holding && queuedStart == null
                && queuedRelease == null && (!firing || gates.crouching);
    }
    public boolean delegateEligible(Gates gates) {
        return ownsControl(gates) && (holding || queuedStart != null || queuedRelease != null || !pending.isEmpty() || accepted != null)
                || eligible(gates);
    }
    public boolean press(HighPowerInput.Input input, Gates gates) {
        if (input == null || HighPowerInput.parse(input.wire()) == null || !eligible(gates)
                || input.input() <= highwater || input.slot() != gates.slot || input.actor() != gates.context.actor
                || input.epoch() != gates.context.ownerEpoch || latest != null && !ownsControl(gates)) return false;
        owner = gates.context; physicalKey = gates.physicalKey; latest = queuedStart = input;
        highwater = input.input(); holding = true; return true;
    }
    /** The held free round still owes a release after its early server acknowledgement. */
    public void release(HighPowerInput.Input input) {
        if (holding && latest != null && latest.equals(input)) {
            holding = false;
            if (queuedStart != null || pending.containsKey(input) || freeOrigin && matches(accepted, input.input(), input.epoch()))
                queuedRelease = input;
            compact();
        }
    }
    public void releaseWhenUp(boolean physicalDown) { if (!physicalDown) release(latest); }
    public boolean flushStartPost(Gates gates, Consumer<HighPowerInput.Input> sendStart, Consumer<HighPowerInput.Input> sendAbort) {
        if (queuedStart == null) return false;
        if (!ownsControl(gates)) { cancelBeforeClear(sendAbort); return false; }
        var captured = queuedStart; queuedStart = null; pending.put(captured, 0);
        Objects.requireNonNull(sendStart).accept(captured); return true;
    }
    public boolean flushReleasePost(Gates gates, Consumer<HighPowerInput.Input> sendRelease, Consumer<HighPowerInput.Input> sendAbort) {
        if (queuedRelease == null || queuedStart != null) return false;
        if (!ownsControl(gates)) { cancelBeforeClear(sendAbort); return false; }
        var captured = queuedRelease; queuedRelease = null;
        if (!pending.containsKey(captured) && !(freeOrigin && matches(accepted, captured.input(), captured.epoch()))) return false;
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
    /** Only ROUND may acknowledge a still-held gesture; no held tap can invent a coordinate or preview. */
    public boolean permitsReceipt(long input, long epoch, int phase) {
        return permits(input, epoch) && (phase == MultidimensionalLandingArrayMesh.ENDED
                || phase == MultidimensionalLandingArrayMesh.REFUSED || matches(accepted, input, epoch)
                || released.stream().anyMatch(p -> matches(p, input, epoch))
                || phase == MultidimensionalLandingArrayMesh.ROUND && holding && matches(latest, input, epoch));
    }
    public void accepted(long input, long epoch, int remainingTicks, int phase, boolean heldFreeOrigin) {
        boolean round = MultidimensionalLandingArrayMesh.round(phase);
        if (!permitsReceipt(input, epoch, phase) || remainingTicks < 0
                || !round && (remainingTicks == 0 || remainingTicks > MARK_LIFE_TICKS)
                || round && remainingTicks > MultidimensionalLandingArrayMesh.ROUND_TICKS) return;
        var identity = pending.keySet().stream().filter(p -> matches(p, input, epoch)).findFirst().orElse(null);
        boolean existing = accepted != null;
        if (identity != null) {
            pending.keySet().removeIf(p -> p.epoch() == epoch && p.input() <= input);
            released.removeIf(p -> p.epoch() == epoch && p.input() <= input); accepted = identity;
        }
        if (round && !firing) remaining = ROUND_OWNERSHIP_TICKS;
        else if (!round) remaining = existing ? Math.min(remaining, remainingTicks) : remainingTicks;
        firing |= round; if (round) freeOrigin = heldFreeOrigin;
    }
    /** A refusal of a newer gesture leaves the older authoritative plan or round untouched. */
    public void rejectedGesture(long input, long epoch) {
        released.removeIf(p -> matches(p, input, epoch)); pending.keySet().removeIf(p -> matches(p, input, epoch));
        if (matches(queuedRelease, input, epoch)) queuedRelease = null;
        compact();
    }
    public void ended(long input, long epoch) {
        released.removeIf(p -> p.epoch() == epoch && p.input() <= input);
        pending.keySet().removeIf(p -> p.epoch() == epoch && p.input() <= input);
        if (matches(queuedRelease, input, epoch)) queuedRelease = null;
        if (accepted != null && accepted.epoch() == epoch && accepted.input() <= input) {
            accepted = null; remaining = 0; firing = freeOrigin = false;
        }
        compact();
    }
    public void tick(Gates gates, boolean paused, Consumer<HighPowerInput.Input> sendAbort) {
        if (latest == null) return;
        if (!ownsControl(gates)) { cancelBeforeClear(sendAbort); return; }
        if (!paused) {
            pending.replaceAll((identity, age) -> Math.min(ACK_TIMEOUT_TICKS, age + 1));
            if (remaining > 0) remaining--;
        }
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
    public boolean firing() { return firing; }
    public boolean freeOrigin() { return freeOrigin; }
    public int pendingCount() { return pending.size(); }
    public void reset() { clearOwnership(); highwater = 0; }
    private void compact() { if (!holding && queuedStart == null && queuedRelease == null && pending.isEmpty() && accepted == null) clearOwnership(); }
    private void clearOwnership() {
        latest = queuedStart = queuedRelease = accepted = null; owner = null; physicalKey = remaining = 0;
        holding = firing = freeOrigin = false; pending.clear(); released.clear();
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
