package cn.academy.port.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** NEW142 transport gate. Finite per-owner controllers with connection-lifetime replay fences. */
public final class PotentialBufferState {
    public static final int ACTIVE = 0, HIT = 1, PULSE = 2, ENDED = 3;
    public static final int MAX_CONTEXTS = 64, MAX_WATERMARKS = 1024, QUIET_TICKS = 12, HIT_TICKS = 4, PULSE_TICKS = 6;
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, PotentialBufferGeometry.Point origin,
                        PotentialBufferGeometry.Point facing, PotentialBufferGeometry.Point impact) {}
    private record Fence(Identity id, long sequence, boolean retired) {}
    private final Map<UUID, Frame> active = new HashMap<>();
    private final Map<UUID, Fence> fences = new HashMap<>();
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame frame) {
        if (frame == null || frame.id == null || frame.id.owner == null || frame.id.entity < 0
                || frame.id.epoch <= 0 || frame.id.input <= 0 || frame.id.token <= 0 || frame.sequence <= 0
                || frame.phase < ACTIVE || frame.phase > ENDED || !PotentialBufferGeometry.validPoint(frame.origin)
                || !PotentialBufferGeometry.validDirection(frame.facing) || !PotentialBufferGeometry.validPoint(frame.impact)) return false;
        // Impact geometry is local to the owner; a malformed packet must never create a distant flash.
        return frame.impact.minus(frame.origin).length() <= 3;
    }
    public boolean accept(Frame frame, boolean local) {
        if (!valid(frame) || local && frame.id.epoch == cancelledEpoch && frame.id.input <= cancelledThrough) return false;
        Fence old = fences.get(frame.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        if (old != null) {
            int order = compare(frame.id, old.id);
            if (order < 0 || order == 0 && (!frame.id.equals(old.id) || old.retired || frame.sequence <= old.sequence)) return false;
            // Tokens identify one server context. They cannot be changed under the same input or recycled later.
            if (order > 0 && frame.id.token <= old.id.token) return false;
        }
        if (frame.phase == ENDED) {
            fences.put(frame.id.owner, new Fence(frame.id, frame.sequence, true));
            active.remove(frame.id.owner);
            return true;
        }
        if (!active.containsKey(frame.id.owner) && active.size() >= MAX_CONTEXTS) return false;
        fences.put(frame.id.owner, new Fence(frame.id, frame.sequence, false));
        active.put(frame.id.owner, frame);
        return true;
    }
    private static int compare(Identity left, Identity right) {
        int order = Long.compare(left.epoch, right.epoch);
        return order != 0 ? order : Long.compare(left.input, right.input);
    }
    public void retire(Identity id) {
        if (id == null) return;
        Fence old = fences.get(id.owner);
        if (old != null && compare(id, old.id) == 0 && id.equals(old.id)) {
            fences.put(id.owner, new Fence(id, old.sequence, true));
            Frame current = active.get(id.owner);
            if (current != null && current.id.equals(id)) active.remove(id.owner);
        }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
    }
    public Frame current(UUID owner) { return active.get(owner); }
    public int size() { return active.size(); }
    public int watermarkCount() { return fences.size(); }
    public void clear() { for (Frame frame : List.copyOf(active.values())) retire(frame.id); }
    public void resetSession() { active.clear(); fences.clear(); cancelledEpoch = 0; cancelledThrough = 0; }

    /** Renewable quiet lease, without an absolute mode timeout or unbounded elapsed counter. */
    public static final class Lease {
        private int frame, quiet, hitAge = HIT_TICKS, pulseAge = PULSE_TICKS;
        public void received(int phase) {
            quiet = 0;
            if (phase == HIT) hitAge = 0;
            if (phase == PULSE) pulseAge = 0;
        }
        public boolean tick(boolean paused, boolean valid) {
            if (!valid) return false;
            if (paused) return true;
            frame = (frame + 1) % 1200;
            hitAge = Math.min(HIT_TICKS, hitAge + 1);
            pulseAge = Math.min(PULSE_TICKS, pulseAge + 1);
            return ++quiet < QUIET_TICKS;
        }
        public int frame() { return frame; }
        public int quiet() { return quiet; }
        public int hitAge() { return hitAge; }
        public int pulseAge() { return pulseAge; }
    }
}
