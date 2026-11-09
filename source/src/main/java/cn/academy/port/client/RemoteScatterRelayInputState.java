package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.util.ArrayDeque;

/** NEW253 physical edges are dispatched after native movement; the server owns press-time crouch and release-time aim. */
public final class RemoteScatterRelayInputState {
    public static final int MAX_PENDING = 16;
    public record Held(HighPowerInput.Input identity, int physicalKey, boolean editing) {}
    public record DispatchContext(Object world, Object player, Object connection, long session, int preset, long revision, String mappings) {}
    public record QueuedStart(HighPowerInput.Input identity, DispatchContext context) {}
    public record DeferredRelease(HighPowerInput.Input identity, DispatchContext context) {}
    public record Dispatch(HighPowerInput.Input start, HighPowerInput.Input release) {}
    private final ArrayDeque<HighPowerInput.Input> pending = new ArrayDeque<>();
    private Held latest;
    private HighPowerInput.Input accepted;
    private QueuedStart queuedStart;
    private DeferredRelease deferredRelease;
    private boolean queuedRelease;
    private boolean holding;
    private long epoch, latestInput, deferredThrough;
    private int actor = -1;

    public void begin(HighPowerInput.Input input, int physicalKey, boolean editing) {
        begin(input, physicalKey, editing, null);
    }
    public void begin(HighPowerInput.Input input, int physicalKey, boolean editing, DispatchContext context) {
        if (input == null || HighPowerInput.parse(input.wire()) == null || physicalKey == 0)
            throw new IllegalArgumentException("Authenticated input and physical key required");
        if (input.epoch() < epoch || input.epoch() == epoch && actor != -1 && input.actor() != actor)
            throw new IllegalArgumentException("Stale owner session");
        if (input.epoch() > epoch) reset();
        if (holding || queuedStart != null || input.input() <= latestInput)
            throw new IllegalArgumentException("A fresh physical edge must follow release");
        epoch = input.epoch(); actor = input.actor(); latestInput = input.input();
        latest = new Held(input, physicalKey, editing); holding = true;
        queuedStart = new QueuedStart(input, context); queuedRelease = false;
    }
    public HighPowerInput.Input release(HighPowerInput.Input input) {
        if (!holding || latest == null || !latest.identity.equals(input)) return null;
        holding = false;
        if (queuedStart != null) { queuedRelease = true; return null; }
        return latest.identity;
    }
    /** Only Post dispatches start, after vanilla's native sneak/movement input packets. */
    public Dispatch flushQueuedStart() {
        if (queuedStart == null) return null;
        var start = queuedStart.identity;
        if (pending.size() == MAX_PENDING) pending.removeFirst();
        pending.addLast(start);
        var result = new Dispatch(start, queuedRelease ? start : null);
        queuedStart = null; queuedRelease = false; return result;
    }
    public QueuedStart queuedStart() { return queuedStart; }
    public boolean queuedFor(DispatchContext current) {
        return queuedStart != null && sameContext(queuedStart.context, current);
    }
    private static boolean sameContext(DispatchContext before, DispatchContext current) {
        if (before == null || current == null) return false;
        return before.world == current.world && before.player == current.player && before.connection == current.connection
                && before.session == current.session && before.preset == current.preset && before.revision == current.revision
                && java.util.Objects.equals(before.mappings, current.mappings);
    }
    /** One released physical edge waits for vanilla's release-tick crouch and final look packets. */
    public boolean deferRelease(HighPowerInput.Input input, DispatchContext context) {
        if (input == null || latest == null || !latest.identity.equals(input) || holding || queuedStart != null
                || context == null || context.world == null || context.player == null || context.connection == null || context.session <= 0
                || !permits(input.input(), input.epoch()) || input.input() <= deferredThrough || deferredRelease != null) return false;
        deferredRelease = new DeferredRelease(input, context);
        deferredThrough = input.input();
        return true;
    }
    /** Post consumes the slot even after a context mismatch; an old release can never retry in a new world. */
    public HighPowerInput.Input flushDeferredRelease(DispatchContext current) {
        DeferredRelease release = deferredRelease;
        deferredRelease = null;
        if (release == null || !sameContext(release.context, current)
                || !permits(release.identity.input(), release.identity.epoch())) return null;
        return release.identity;
    }
    /** Silent CP snapshot delegate replacement cannot swallow the original physical release. */
    public HighPowerInput.Input releaseWhenUp(boolean physicalDown) {
        return !physicalDown && latest != null ? release(latest.identity) : null;
    }
    public boolean permits(long input, long ownerEpoch) {
        return input > 0 && ownerEpoch == epoch && (accepted != null && accepted.input() == input
                || pending.stream().anyMatch(p -> p.input() == input && p.epoch() == ownerEpoch));
    }
    public void accepted(long input, long ownerEpoch) {
        if (!permits(input, ownerEpoch) || accepted != null && accepted.input() > input) return;
        for (var p : pending) if (p.input() == input && p.epoch() == ownerEpoch) { accepted = p; break; }
        pending.removeIf(p -> p.epoch() == ownerEpoch && p.input() <= input);
    }
    public void ended(long input, long ownerEpoch) {
        if (ownerEpoch != epoch) return;
        if (accepted != null && accepted.input() <= input) accepted = null;
        pending.removeIf(p -> p.epoch() == ownerEpoch && p.input() <= input);
        if (deferredRelease != null && deferredRelease.identity.input() <= input) deferredRelease = null;
        if (latest != null && latest.identity.input() <= input) { holding = false; queuedStart = null; queuedRelease = false; }
    }
    /** Explicit focus/menu/category/preset cancellation also clears the released persistent relay. */
    public HighPowerInput.Input cancel() {
        var result = latest == null ? null : latest.identity;
        latest = null; accepted = null; pending.clear(); holding = false; queuedStart = null; deferredRelease = null; queuedRelease = false; return result;
    }
    /** A disposed delegate after an authentic release does not revoke a paid stationary relay. */
    public HighPowerInput.Input cancel(HighPowerInput.Input input) {
        return (holding || queuedStart != null) && latest != null && latest.identity.equals(input) ? cancel() : null;
    }
    public HighPowerInput.Input cancelWhenBlocked(boolean blocked) { return blocked ? cancel() : null; }
    public Held current() { return latest; }
    public boolean holding() { return holding; }
    public int pendingCount() { return pending.size(); }
    public void reset() { cancel(); epoch = latestInput = deferredThrough = 0; actor = -1; }
}
