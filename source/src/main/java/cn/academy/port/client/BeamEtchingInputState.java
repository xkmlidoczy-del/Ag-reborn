package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;

/** One authentic hold identity survives physical release until the server finishes native mining. */
public final class BeamEtchingInputState {
    public record Held(HighPowerInput.Input identity, int physicalKey) {}
    private Held current;
    private boolean holding;
    private long epoch, latestInput;
    private int actor = -1;

    public void begin(HighPowerInput.Input input, int physicalKey) {
        if (input == null || HighPowerInput.parse(input.wire()) == null || physicalKey == 0)
            throw new IllegalArgumentException("Authenticated input and physical key required");
        if (epoch != input.epoch() || actor != input.actor()) reset();
        if (input.input() <= latestInput || current != null)
            throw new IllegalArgumentException("Previous hold must end and input must advance");
        epoch = input.epoch(); actor = input.actor(); latestInput = input.input();
        current = new Held(input, physicalKey); holding = true;
    }
    public HighPowerInput.Input release(HighPowerInput.Input input) {
        if (!holding || current == null || !current.identity.equals(input)) return null;
        holding = false; return current.identity;
    }
    /** Snapshot delegate replacement cannot swallow the original physical key's release edge. */
    public HighPowerInput.Input releaseWhenUp(boolean physicalDown) {
        return !physicalDown && current != null ? release(current.identity) : null;
    }
    public boolean permits(long input, long ownerEpoch) {
        return current != null && current.identity.input() == input && current.identity.epoch() == ownerEpoch;
    }
    public void accepted(long input, long ownerEpoch) { /* Receipt acceptance never re-arms a released key. */ }
    public void ended(long input, long ownerEpoch) {
        if (permits(input, ownerEpoch)) { current = null; holding = false; }
    }
    public HighPowerInput.Input cancel() {
        var input = current == null ? null : current.identity;
        current = null; holding = false; return input;
    }
    public HighPowerInput.Input cancel(HighPowerInput.Input input) {
        return current != null && current.identity.equals(input) ? cancel() : null;
    }
    public HighPowerInput.Input cancelWhenBlocked(boolean blocked) { return blocked ? cancel() : null; }
    public Held current() { return current; }
    public boolean holding() { return holding; }
    public void reset() { current = null; holding = false; epoch = latestInput = 0; actor = -1; }
}
