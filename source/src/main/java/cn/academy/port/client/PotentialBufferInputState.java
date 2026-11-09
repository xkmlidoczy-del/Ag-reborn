package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;

/** NEW142 remembers authenticated taps, never predicts toggle state or requires a held key. */
public final class PotentialBufferInputState {
    private HighPowerInput.Input latest;
    private long acceptedInput, acceptedEpoch;
    public void tap(HighPowerInput.Input input) {
        if (input == null || HighPowerInput.parse(input.wire()) == null)
            throw new IllegalArgumentException("Valid authenticated input required");
        latest = input;
    }
    /** Releasing any physical key is intentionally inert for this server-owned toggle. */
    public void release(HighPowerInput.Input input) {}
    public HighPowerInput.Input cancel() { var result = latest; reset(); return result; }
    public HighPowerInput.Input cancel(HighPowerInput.Input input) {
        return latest != null && latest.equals(input) ? cancel() : null;
    }
    public HighPowerInput.Input cancelWhenBlocked(boolean blocked) { return blocked ? cancel() : null; }
    /** A newer toggle request cannot erase the older accepted context before the server ends it. */
    public boolean permits(long input, long epoch) {
        return input > 0 && latest != null && epoch == latest.epoch()
                && (input == latest.input() || input == acceptedInput && epoch == acceptedEpoch);
    }
    public void accepted(long input, long epoch) {
        if (permits(input, epoch)) { acceptedInput = input; acceptedEpoch = epoch; }
    }
    public void ended(long input, long epoch) {
        if (acceptedInput == input && acceptedEpoch == epoch) { acceptedInput = 0; acceptedEpoch = 0; }
    }
    public HighPowerInput.Input current() { return latest; }
    public void reset() { latest = null; acceptedInput = 0; acceptedEpoch = 0; }
}
