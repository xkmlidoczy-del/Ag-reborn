package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.util.ArrayDeque;

/** NEW151 keeps physical edges and authenticated requests; only the server counts marks and chooses mode. */
public final class RailThunderArrayInputState {
    public static final int MAX_PENDING = 16;
    public record Held(HighPowerInput.Input identity, int physicalKey) {}
    private final ArrayDeque<HighPowerInput.Input> pending = new ArrayDeque<>();
    private Held latest;
    private HighPowerInput.Input accepted;
    private boolean holding;

    public void begin(HighPowerInput.Input input, int physicalKey) {
        if (input == null || HighPowerInput.parse(input.wire()) == null || physicalKey == 0)
            throw new IllegalArgumentException("Valid authenticated input and physical key required");
        if (latest != null && (latest.identity.epoch() != input.epoch() || latest.identity.actor() != input.actor())) reset();
        if (latest != null && input.input() <= latest.identity.input()) throw new IllegalArgumentException("Input must advance");
        latest = new Held(input, physicalKey); holding = true;
        if (pending.size() == MAX_PENDING) pending.removeFirst();
        pending.addLast(input);
    }
    public HighPowerInput.Input release(HighPowerInput.Input input) {
        if (!holding || latest == null || !latest.identity.equals(input)) return null;
        holding = false; return latest.identity;
    }
    /** Silent CP snapshots may rebuild the delegate; its actual physical key still owns release. */
    public HighPowerInput.Input releaseWhenUp(boolean physicalDown) {
        return !physicalDown && latest != null ? release(latest.identity) : null;
    }
    public boolean permits(long input, long epoch) {
        if (input <= 0 || latest == null || epoch != latest.identity.epoch()) return false;
        return accepted != null && accepted.input() == input && accepted.epoch() == epoch
                || pending.stream().anyMatch(p -> p.input() == input && p.epoch() == epoch);
    }
    public void accepted(long input, long epoch) {
        if (!permits(input, epoch)) return;
        for (var p : pending) if (p.input() == input && p.epoch() == epoch) { accepted = p; break; }
        pending.removeIf(p -> p.epoch() == epoch && p.input() < input);
    }
    public void ended(long input, long epoch) {
        if (accepted != null && accepted.input() <= input && accepted.epoch() == epoch) accepted = null;
        pending.removeIf(p -> p.epoch() == epoch && p.input() <= input);
    }
    public HighPowerInput.Input cancel() { var input = latest == null ? null : latest.identity; reset(); return input; }
    public HighPowerInput.Input cancel(HighPowerInput.Input input) { return latest != null && latest.identity.equals(input) ? cancel() : null; }
    public HighPowerInput.Input cancelWhenBlocked(boolean blocked) { return blocked ? cancel() : null; }
    public Held current() { return latest; }
    public boolean holding() { return holding; }
    public int pendingCount() { return pending.size(); }
    public void reset() { latest = null; accepted = null; pending.clear(); holding = false; }
}
