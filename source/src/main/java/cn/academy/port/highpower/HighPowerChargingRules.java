package cn.academy.port.highpower;

/** Pure bounded arithmetic for the enrolled-only classic charging extension. */
public final class HighPowerChargingRules {
    public static final int WINDOW_TICKS = 20;
    private HighPowerChargingRules() {}
    /** Return the actual representable store delta, never a nominal or simulated amount. */
    public static double accepted(double stored, double capacity, double bandwidth, double requested) {
        if (!Double.isFinite(stored) || !Double.isFinite(capacity) || !Double.isFinite(bandwidth)
                || !Double.isFinite(requested) || stored < 0 || capacity <= 0 || stored >= capacity
                || bandwidth <= 0 || requested <= 0) return 0;
        double bound = Math.min(Math.min(requested, bandwidth), capacity - stored);
        double next = Math.min(capacity, stored + bound);
        if (next - stored > bound) next = Math.nextDown(next);
        double accepted = next - stored;
        return accepted > 0 && accepted <= bound ? accepted : 0;
    }
    public static double price(double accepted, double speed, double cpPerTick) {
        if (!Double.isFinite(accepted) || !Double.isFinite(speed) || !Double.isFinite(cpPerTick)
                || accepted <= 0 || speed <= 0 || accepted > speed || cpPerTick <= 0) return 0;
        return cpPerTick * (accepted / speed);
    }
}
