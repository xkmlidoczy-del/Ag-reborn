package cn.academy.port.client;

/** Pure one-shot receipt fence. An invalid paid receipt is retired, never banked or retried. */
public final class BeamStepReceiptState {
    public static final int TTL = 2;
    private long input, epoch, started, maxToken;
    private double initialY;
    private boolean pending, recorded;

    public boolean begin(long input, long epoch, long tick, double y) {
        if (input <= 0 || epoch <= 0 || tick < 0 || !Double.isFinite(y)) return false;
        if (epoch < this.epoch || epoch == this.epoch && input <= this.input) return false;
        this.input = input; this.epoch = epoch; started = tick; initialY = y; pending = recorded = true;
        return true;
    }
    public boolean matches(long input, long epoch) { return pending && this.input == input && this.epoch == epoch; }
    /** Retired motion may still explain one authenticated paid no-op; this never reopens pending motion. */
    public boolean recordedMatches(long input,long epoch,long tick) {
        return recorded && this.input==input && this.epoch==epoch && tick>=started && tick-started<20;
    }
    public void forgetFeedback() { recorded=false; }
    public boolean pending() { return pending; }
    public void cancel() { pending = false; }
    public void cancel(long input, long epoch) {
        if (epoch == this.epoch && input >= this.input) pending = false;
    }
    public void resetConnection() { input = epoch = started = maxToken = 0; pending = recorded = false; }
    /** Must only be called after owner/world/session authentication; unrelated packets cannot retire a real input. */
    public boolean consume(long input, long epoch, long token, long tick, double currentY,
                           long issued, long expires, long serverNow, boolean eligible) {
        if (!matches(input, epoch) || token <= 0 || token <= maxToken) return false;
        maxToken = token; pending = false;
        long elapsed = tick - started;
        return eligible && elapsed >= 0 && elapsed <= TTL && fresh(issued, expires, serverNow, TTL)
                && nativeY(initialY, currentY, (int)elapsed);
    }
    public static boolean fresh(long issued, long expires, long now, int ttl) {
        return issued >= 0 && issued <= Long.MAX_VALUE - ttl && expires == issued + ttl
                && now >= issued - 1 && now <= expires;
    }
    /** Only ordinary gravity evolution is allowed; a new vertical controller invalidates the pending tap. */
    public static boolean nativeY(double captured, double current, int maxTicks) {
        if (!Double.isFinite(captured) || !Double.isFinite(current) || maxTicks < 0 || maxTicks > TTL) return false;
        for (int i = 0; i <= maxTicks; i++) {
            if (Math.abs(captured - current) <= 1e-9) return true;
            if (Math.abs(captured) < .003) captured = 0;
            captured = (captured - .08) * (double).98F;
        }
        return false;
    }
    public static double assistedY(double currentY) {
        if (!Double.isFinite(currentY) || currentY >= .36) return Double.NaN;
        double result = Math.min(.36, currentY + Math.min(.72, .36 - currentY));
        if (result - currentY > .72) result = Math.nextDown(result);
        return result > currentY ? result : Double.NaN;
    }
}
