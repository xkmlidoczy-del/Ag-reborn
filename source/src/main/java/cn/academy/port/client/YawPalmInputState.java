package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** NEW423 fresh-edge tap, native Post crouch sample, inert release and bounded receipt ownership. */
public final class YawPalmInputState {
    public static final int MAX_PENDING = 4, ACK_TIMEOUT_TICKS = 60;
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
    private record Receipt(int age, boolean crouch) {}
    private final Map<HighPowerInput.Input, Receipt> pending = new LinkedHashMap<>();
    private HighPowerInput.Input latest, queued, release;
    private DispatchContext owner;
    private int physicalKey;
    private long highwater;
    private boolean holding, cancelling;

    public boolean eligible(Gates g) { return g != null && g.allowed() && g.cooldownReady && g.sourceEligible; }
    public boolean delegateEligible(Gates g) { return ownsControl(g) && !pending.isEmpty() || eligible(g); }
    public boolean press(HighPowerInput.Input input, Gates g) {
        if (input == null || HighPowerInput.parse(input.wire()) == null || !eligible(g)
                || holding || queued != null || release != null || pending.size() >= MAX_PENDING || input.input() <= highwater
                || input.slot() != g.slot || input.actor() != g.context.actor || input.epoch() != g.context.ownerEpoch
                || latest != null && !ownsControl(g)) return false;
        owner = g.context; physicalKey = g.physicalKey; latest = queued = input;
        highwater = input.input(); holding = true; return true;
    }
    /** Release never retracts a tap, even for down/up within one Pre/Post interval. */
    public void release(HighPowerInput.Input input) {
        if (latest != null && latest.equals(input) && holding) { holding = false; release = input; }
    }
    public void releaseWhenUp(boolean physicalDown) { if (!physicalDown) release(latest); }
    /** Crouch is sampled here after native movement; only the existing bounded identity is transmitted. */
    public boolean flushPost(Gates g, boolean crouch, Consumer<HighPowerInput.Input> sendTap, Consumer<HighPowerInput.Input> sendAbort) {
        if (queued == null) return false;
        if (!eligible(g) || !ownsControl(g)) { cancelBeforeClear(sendAbort); return false; }
        var captured = queued; queued = null; pending.put(captured, new Receipt(0, crouch));
        Objects.requireNonNull(sendTap).accept(captured); compact(); return true;
    }
    public boolean flushReleasePost(Gates g, Consumer<HighPowerInput.Input> sendRelease, Consumer<HighPowerInput.Input> sendAbort) {
        if (release == null || queued != null) return false;
        if (!ownsControl(g)) { cancelBeforeClear(sendAbort); return false; }
        var captured = release; release = null;
        if (pending.containsKey(captured)) Objects.requireNonNull(sendRelease).accept(captured);
        compact(); return true;
    }
    /** Own cooldown and later motion changes do not invalidate an already-authenticated receipt. */
    public boolean ownsControl(Gates g) {
        return g != null && g.allowed() && latest != null && latest.slot() == g.slot
                && physicalKey == g.physicalKey && sameContext(owner, g.context);
    }
    public boolean permits(long input, long epoch) { return pending.keySet().stream().anyMatch(p -> p.input() == input && p.epoch() == epoch); }
    public boolean permits(long input, long epoch, boolean crouch) {
        return pending.entrySet().stream().anyMatch(p -> p.getKey().input() == input && p.getKey().epoch() == epoch
                && p.getValue().crouch == crouch && p.getValue().age < ACK_TIMEOUT_TICKS);
    }
    public void ended(long input, long epoch) {
        pending.keySet().removeIf(p -> p.input() == input && p.epoch() == epoch);
        if (release != null && release.input() == input && release.epoch() == epoch) release = null;
        compact();
    }
    public void tick(Gates g, boolean paused, Consumer<HighPowerInput.Input> sendAbort) {
        if (latest == null) return;
        if (!ownsControl(g) || queued != null && !g.sourceEligible) { cancelBeforeClear(sendAbort); return; }
        if (!paused) pending.replaceAll((id, p) -> new Receipt(p.age + 1, p.crouch));
        if (pending.values().stream().anyMatch(p -> p.age >= ACK_TIMEOUT_TICKS)) cancelBeforeClear(sendAbort);
    }
    public boolean cancel(HighPowerInput.Input input, Consumer<HighPowerInput.Input> sendAbort) {
        return latest != null && latest.equals(input) && cancelBeforeClear(sendAbort);
    }
    public boolean cancelBeforeClear(Consumer<HighPowerInput.Input> sendAbort) {
        if (latest == null || cancelling) return false;
        cancelling = true;
        try {
            // Every outstanding identity is fenced before the context is discarded, including superseded taps.
            var cancelled = new ArrayList<>(pending.keySet());
            if (!cancelled.contains(latest)) cancelled.add(latest);
            for (var id : cancelled) Objects.requireNonNull(sendAbort).accept(id);
            clearOwnership(); return true;
        } finally { cancelling = false; }
    }
    public boolean ownsEnvironment(Object world, Object player, Object connection) {
        return owner != null && owner.world == world && owner.player == player && owner.connection == connection;
    }
    public HighPowerInput.Input current() { return latest; }
    public HighPowerInput.Input queuedStart() { return queued; }
    public HighPowerInput.Input queuedRelease() { return release; }
    public int physicalKey() { return physicalKey; }
    public boolean holding() { return holding; }
    public int pendingCount() { return pending.size(); }
    public void reset() { clearOwnership(); highwater = 0; }
    private void compact() { if (!holding && queued == null && release == null && pending.isEmpty()) clearOwnership(); }
    private void clearOwnership() { latest = queued = release = null; owner = null; physicalKey = 0; holding = false; pending.clear(); }
    private static boolean sameContext(DispatchContext a, DispatchContext b) {
        return a != null && b != null && a.world == b.world && a.player == b.player && a.connection == b.connection
                && a.session == b.session && a.actor == b.actor && a.ownerEpoch == b.ownerEpoch
                && a.preset == b.preset && a.revision == b.revision && Objects.equals(a.mappings, b.mappings);
    }
}
