package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import java.util.ArrayDeque;

/** Authenticated NEW212 toggle requests survive key release; no client prediction of plate state or hits. */
public final class ScatterPlateInputState {
    public static final int MAX_PENDING = 16;
    private final ArrayDeque<HighPowerInput.Input> pending = new ArrayDeque<>();
    private HighPowerInput.Input latest, accepted;

    public void tap(HighPowerInput.Input input) {
        if (input == null || HighPowerInput.parse(input.wire()) == null)
            throw new IllegalArgumentException("Valid authenticated input required");
        if (latest != null && (latest.epoch() != input.epoch() || latest.actor() != input.actor())) reset();
        if (latest != null && input.input() <= latest.input()) throw new IllegalArgumentException("Input must advance");
        latest = input;
        if (pending.size() == MAX_PENDING) pending.removeFirst();
        pending.addLast(input);
    }
    public void release(HighPowerInput.Input input) { /* A toggle is not a hold. */ }
    public boolean permits(long input, long epoch) {
        return input > 0 && latest != null && epoch == latest.epoch()
                && (accepted != null && accepted.input() == input && accepted.epoch() == epoch
                || pending.stream().anyMatch(p -> p.input() == input && p.epoch() == epoch));
    }
    public void accepted(long input, long epoch) {
        if (!permits(input, epoch)) return;
        for (var p : pending) if (p.input() == input && p.epoch() == epoch) { accepted = p; break; }
        pending.removeIf(p -> p.epoch() == epoch && p.input() < input);
    }
    public void ended(long input, long epoch) {
        if (accepted != null && accepted.epoch() == epoch && accepted.input() <= input) accepted = null;
        pending.removeIf(p -> p.epoch() == epoch && p.input() <= input);
    }
    public HighPowerInput.Input cancel() { var result = latest; reset(); return result; }
    public HighPowerInput.Input cancel(HighPowerInput.Input input) { return latest != null && latest.equals(input) ? cancel() : null; }
    public HighPowerInput.Input cancelWhenBlocked(boolean blocked) { return blocked ? cancel() : null; }
    public HighPowerInput.Input current() { return latest; }
    public int pendingCount() { return pending.size(); }
    public void reset() { latest = accepted = null; pending.clear(); }
}
