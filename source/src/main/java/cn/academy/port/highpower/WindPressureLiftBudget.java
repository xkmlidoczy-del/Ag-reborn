package cn.academy.port.highpower;

import java.util.ArrayList;
import java.util.List;

/** Pure owner budget. Every access is under WindPressureLift's commit lock. */
final class WindPressureLiftBudget {
    static final long SECOND = 1_000_000_000L, MAX_GAP = 100_000_000L;
    private static final class Use {
        int ticks;
        long nanos;
        Use(int ticks, long nanos) { this.ticks = ticks; this.nanos = nanos; }
    }
    private final List<Use> uses = new ArrayList<>();
    private long nativeTick = -1, anchorNanos;
    private int tickUses;
    private boolean online, valid = true;
    private long receipt;
    private int receiptUses;

    WindPressureLiftBudget(int[] ticks, long[] nanos, long receipt, int receiptUses) {
        if (ticks == null || nanos == null || ticks.length != nanos.length || ticks.length > 20
                || receipt < 0 || receiptUses < 0 || receiptUses > 20 || receipt == 0 && receiptUses != 0)
            throw new IllegalArgumentException("Malformed NEW443 budget");
        for (int i = 0; i < ticks.length; i++) {
            if (ticks[i] < 0 || ticks[i] > 20 || nanos[i] < 0 || nanos[i] > SECOND || ticks[i] == 0 && nanos[i] == 0)
                throw new IllegalArgumentException("Malformed NEW443 debt");
            uses.add(new Use(ticks[i], nanos[i]));
        }
        this.receipt = receipt; this.receiptUses = receiptUses;
    }
    boolean valid() { return valid; }
    long receipt() { return receipt; }
    void invalidate() { valid = false; }
    void initialize(long tick, long now) { if (nativeTick < 0) nativeTick = tick; resume(now); }
    void resume(long now) { if (!online) { online = true; anchorNanos = now; } }
    void pause(long now) { advanceWall(now); online = false; }
    void nativePre(long tick, long now) {
        if (!valid || nativeTick == tick) return;
        if (nativeTick > tick) { valid = false; return; }
        resume(now); advanceWall(now);
        for (Use use : uses) use.ticks = Math.max(0, use.ticks - 1);
        prune(); nativeTick = tick; tickUses = 0;
    }
    /** No absolute nanoTime value is serialized. Paused/offline or >100ms gaps give no credit. */
    private void advanceWall(long now) {
        long elapsed = now - anchorNanos;
        if (online && elapsed >= 0 && elapsed <= MAX_GAP)
            for (Use use : uses) use.nanos = Math.max(0, use.nanos - elapsed);
        anchorNanos = now; prune();
    }
    private void prune() { uses.removeIf(use -> use.ticks == 0 && use.nanos == 0); }
    boolean take(long tick, long receipt, long now) {
        if (!valid || !online || tick != nativeTick || receipt <= 0 || receipt < this.receipt) return false;
        advanceWall(now);
        if (receipt > this.receipt) { this.receipt = receipt; receiptUses = 0; }
        int physical = 0, wall = 0;
        for (Use use : uses) { if (use.ticks > 0) physical++; if (use.nanos > 0) wall++; }
        if (tickUses >= 2 || physical >= 20 || wall >= 20 || receiptUses >= 20 || uses.size() >= 20) return false;
        uses.add(new Use(20, SECOND)); tickUses++; receiptUses++; return true;
    }
    Saved saved(long now) {
        advanceWall(now);
        int[] ticks = new int[uses.size()]; long[] nanos = new long[uses.size()];
        for (int i = 0; i < uses.size(); i++) { ticks[i] = uses.get(i).ticks; nanos[i] = uses.get(i).nanos; }
        return new Saved(ticks, nanos, receipt, receiptUses, valid);
    }
    record Saved(int[] ticks, long[] nanos, long receipt, int receiptUses, boolean valid) {}
}
