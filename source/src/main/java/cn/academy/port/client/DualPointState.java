package cn.academy.port.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded, engine-free receipt fences; this state never selects a target or resolves a hit. */
public final class DualPointState {
    public static final int MARK = 0, FIRE = 1, ENDED = 2, CLEAR_MARK = 3;
    public static final int PREPARED = 0, MISS = 1, BLOCKED = 2, REFUSED = 3, DAMAGED = 4, ABSORBED = 5;
    public static final int MAX_CONTEXTS = 64, MAX_WATERMARKS = 1024, QUIET_TICKS = 12, MARK_TICKS = cn.academy.port.highpower.core.DualPointRules.MARK_TICKS;
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, int slot, DualPointGeometry.Point origin,
                        DualPointGeometry.Point end, int status, int remainingTicks) {}
    private record Fence(Frame frame, boolean retired, long shotInput, int shotSlots) {}
    private final Map<UUID, Frame> active = new HashMap<>();
    private final Map<UUID, Fence> fences = new HashMap<>();
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(new UUID(0, 0))
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < MARK || f.phase > CLEAR_MARK || f.slot < 0 || f.slot > 1
                || f.status < PREPARED || f.status > ABSORBED || f.remainingTicks < 0 || f.remainingTicks > MARK_TICKS
                || !DualPointGeometry.validEndpoints(f.origin, f.end)) return false;
        return switch (f.phase) {
            case MARK -> f.slot == 1 && f.status == PREPARED && f.remainingTicks > 0;
            case FIRE -> f.status != PREPARED && f.remainingTicks == 0;
            default -> f.remainingTicks == 0 && f.origin.equals(new DualPointGeometry.Point(0, 0, 0))
                    && f.end.equals(f.origin);
        };
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || local && (f.id.epoch < cancelledEpoch || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        long shotInput = 0; int shotSlots = 0;
        if (old != null) {
            Frame before = old.frame;
            if (f.id.epoch < before.id.epoch || f.id.token < before.id.token) return false;
            if (f.id.token == before.id.token) {
                if (old.retired || f.id.entity != before.id.entity || f.id.epoch != before.id.epoch
                        || f.id.input < before.id.input || f.sequence <= before.sequence) return false;
                shotInput = old.shotInput; shotSlots = old.shotSlots;
            } else if (f.id.epoch == before.id.epoch && f.id.input <= before.id.input) return false;
        }
        if (f.phase == FIRE) {
            if (f.id.input < shotInput) return false;
            if (f.id.input > shotInput) { shotInput = f.id.input; shotSlots = 0; }
            int bit = 1 << f.slot;
            if ((shotSlots & bit) != 0) return false;
            shotSlots |= bit;
        }
        if (f.phase != ENDED && !active.containsKey(f.id.owner) && active.size() >= MAX_CONTEXTS) return false;
        fences.put(f.id.owner, new Fence(f, f.phase == ENDED, shotInput, shotSlots));
        if (f.phase == ENDED) active.remove(f.id.owner); else active.put(f.id.owner, f);
        return true;
    }
    public void retire(Identity id) {
        Fence f = id == null ? null : fences.get(id.owner);
        if (f != null && f.frame.id.token == id.token && f.frame.id.epoch == id.epoch) {
            fences.put(id.owner, new Fence(f.frame, true, f.shotInput, f.shotSlots)); active.remove(id.owner);
        }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
    }
    public void clear() { for (Frame f : java.util.List.copyOf(active.values())) retire(f.id); }
    public void resetSession() { active.clear(); fences.clear(); cancelledEpoch = cancelledThrough = 0; }
    public int size() { return active.size(); }
    public int watermarkCount() { return fences.size(); }
    public Frame current(UUID owner) { return active.get(owner); }

    /** Frozen endpoint lease: a heartbeat can only shorten its original remaining lifetime. */
    public static final class MarkLease {
        private DualPointGeometry.Point end;
        private int remaining, quiet;
        private long input;
        public void received(Frame frame) {
            if (frame.phase == CLEAR_MARK || frame.phase == ENDED) { end = null; remaining = 0; return; }
            if (frame.phase != MARK) return;
            boolean replacement = end == null || frame.id.input > input;
            if (replacement) remaining = frame.remainingTicks;
            else remaining = Math.min(remaining, frame.remainingTicks);
            end = frame.end; input = frame.id.input; quiet = 0;
        }
        public boolean tick(boolean paused, boolean valid) {
            if (!valid) { end = null; remaining = 0; return false; }
            if (!paused) { if (remaining > 0) remaining--; if (quiet < QUIET_TICKS) quiet++; }
            return visible();
        }
        public boolean visible() { return end != null && remaining > 0 && quiet < QUIET_TICKS; }
        public int remaining() { return remaining; }
        public DualPointGeometry.Point end() { return visible() ? end : null; }
    }
}
