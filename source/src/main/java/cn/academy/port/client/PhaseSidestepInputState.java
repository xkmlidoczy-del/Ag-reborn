package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** NEW312 one fresh edge, dispatched only after vanilla's native movement packets. Release is inert. */
public final class PhaseSidestepInputState {
    public static final int MAX_PENDING = 16, ACK_TIMEOUT_TICKS = 60;
    public record DispatchContext(Object world, Object player, Object connection, long session,
                                  int actor, long ownerEpoch, int preset, long revision, String mappings) {}
    public record Gates(DispatchContext context, int slot, int physicalKey, boolean inGame,
                        boolean categoryActivated, boolean canUseAbility, boolean mappedUsable,
                        boolean skillEnabled, boolean skillMapped, boolean screenClosed,
                        boolean focused, boolean cameraOwned, boolean cooldownReady, boolean sourceEligible) {
        public boolean allowed() {
            return context != null && context.world != null && context.player != null && context.connection != null
                    && context.session > 0 && context.actor >= 0 && context.ownerEpoch > 0
                    && slot >= 0 && slot < 4 && physicalKey != 0 && inGame && categoryActivated
                    && canUseAbility && mappedUsable && skillEnabled && skillMapped && screenClosed && focused && cameraOwned;
        }
    }
    private final Map<HighPowerInput.Input, Integer> pending = new LinkedHashMap<>();
    private HighPowerInput.Input latest, queued;
    private DispatchContext owner;
    private int physicalKey;
    private long highwater;
    private boolean holding, cancelling;

    public boolean eligible(Gates gates) { return gates != null && gates.allowed() && gates.cooldownReady && gates.sourceEligible; }
    public boolean press(HighPowerInput.Input input, Gates gates) {
        if (input == null || HighPowerInput.parse(input.wire()) == null || !eligible(gates)
                || holding || queued != null || pending.size() >= MAX_PENDING || input.input() <= highwater
                || input.slot() != gates.slot || input.actor() != gates.context.actor || input.epoch() != gates.context.ownerEpoch
                || latest != null && !ownsControl(gates)) return false;
        owner = gates.context; physicalKey = gates.physicalKey; latest = queued = input;
        highwater = input.input(); holding = true; return true;
    }
    /** A down/up pair before Post still dispatches exactly one start. No release request is generated. */
    public void release(HighPowerInput.Input input) {
        if (latest != null && latest.equals(input)) { holding = false; compact(); }
    }
    public void releaseWhenUp(boolean physicalDown) { if (!physicalDown) release(latest); }
    /** Production Post owns this seam. Its callback sends the identity only, never side or destination. */
    public boolean flushPost(Gates gates, Consumer<HighPowerInput.Input> sendStart, Consumer<HighPowerInput.Input> sendAbort) {
        if (queued == null) return false;
        if (!eligible(gates) || !ownsControl(gates)) { cancelBeforeClear(sendAbort); return false; }
        var captured = queued;
        // Claim before sending so an immediate acknowledgement can resolve the exact request.
        pending.put(captured, 0); queued = null;
        Objects.requireNonNull(sendStart).accept(captured); compact(); return true;
    }
    /** A native move or later knockback may change the source pose; sent receipts retain their provenance. */
    public boolean ownsControl(Gates gates) {
        return gates != null && gates.allowed() && latest != null && latest.slot() == gates.slot
                && physicalKey == gates.physicalKey && sameContext(owner, gates.context);
    }
    public boolean permits(long input, long epoch) {
        return pending.entrySet().stream().anyMatch(p -> p.getKey().input() == input && p.getKey().epoch() == epoch
                && p.getValue() < ACK_TIMEOUT_TICKS);
    }
    public void ended(long input, long epoch) {
        pending.keySet().removeIf(p -> p.input() == input && p.epoch() == epoch); compact();
    }
    public void tick(Gates gates, boolean paused, Consumer<HighPowerInput.Input> sendAbort) {
        if (latest == null) return;
        if (!ownsControl(gates) || queued != null && !gates.sourceEligible) { cancelBeforeClear(sendAbort); return; }
        if (!paused) pending.replaceAll((identity, age) -> age + 1);
        if (pending.containsValue(ACK_TIMEOUT_TICKS)) cancelBeforeClear(sendAbort);
    }
    public boolean cancel(HighPowerInput.Input input, Consumer<HighPowerInput.Input> sendAbort) {
        return latest != null && latest.equals(input) && cancelBeforeClear(sendAbort);
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
    public HighPowerInput.Input queuedStart() { return queued; }
    public int physicalKey() { return physicalKey; }
    public boolean holding() { return holding; }
    public int pendingCount() { return pending.size(); }
    public void reset() { clearOwnership(); highwater = 0; }
    private void compact() { if (!holding && queued == null && pending.isEmpty()) clearOwnership(); }
    private void clearOwnership() { latest = queued = null; owner = null; physicalKey = 0; holding = false; pending.clear(); }
    private static boolean sameContext(DispatchContext a, DispatchContext b) {
        return a != null && b != null && a.world == b.world && a.player == b.player && a.connection == b.connection
                && a.session == b.session && a.actor == b.actor && a.ownerEpoch == b.ownerEpoch
                && a.preset == b.preset && a.revision == b.revision && Objects.equals(a.mappings, b.mappings);
    }
}
