package cn.academy.port.client;

import cn.academy.port.client.RailThunderArrayGeometry.Point;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-only activation and receipt admission, with bounded controllers and connection-lifetime fences. */
public final class MagneticDomainState {
    public static final int ACTIVE = 0, HIT = 1, REFRACT = 2, ENDED = 3;
    public static final int MAX_CONTEXTS = 64, MAX_WATERMARKS = 1024, QUIET_TICKS = 12, HIT_TICKS = 5, REFRACT_TICKS = 7;
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, Point center, Point axis, Point point, boolean slowFalling, int refracted) {}
    private record Fence(Identity id, long sequence, boolean retired) {}
    private final Map<UUID, Frame> active = new HashMap<>();
    private final Map<UUID, Fence> fences = new HashMap<>();
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        return f != null && f.id != null && f.id.entity >= 0 && f.id.owner != null && !f.id.owner.equals(new UUID(0, 0))
                && f.id.epoch > 0 && f.id.input > 0 && f.id.token > 0 && f.sequence > 0
                && f.phase >= ACTIVE && f.phase <= ENDED && f.refracted >= 0 && f.refracted <= 4
                && RailThunderArrayGeometry.validPoint(f.center) && RailThunderArrayGeometry.validDirection(f.axis)
                && RailThunderArrayGeometry.validPoint(f.point) && f.point.minus(f.center).length() <= MagneticDomainGeometry.RADIUS + 1
                && (f.phase != ENDED || !f.slowFalling);
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || local && (f.id.epoch < cancelledEpoch || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        if (old != null) {
            int order = compare(f.id, old.id);
            if (order < 0 || order == 0 && (!f.id.equals(old.id) || old.retired || f.sequence <= old.sequence)
                    || order > 0 && f.id.token <= old.id.token) return false;
        }
        if (f.phase == ENDED) {
            fences.put(f.id.owner, new Fence(f.id, f.sequence, true)); active.remove(f.id.owner); return true;
        }
        if (!active.containsKey(f.id.owner) && active.size() >= MAX_CONTEXTS) return false;
        fences.put(f.id.owner, new Fence(f.id, f.sequence, false)); active.put(f.id.owner, f); return true;
    }
    private static int compare(Identity left, Identity right) {
        int epoch = Long.compare(left.epoch, right.epoch);
        return epoch != 0 ? epoch : Long.compare(left.input, right.input);
    }
    public void retire(Identity id) {
        if (id == null) return;
        Fence old = fences.get(id.owner);
        if (old != null && old.id.equals(id)) { fences.put(id.owner, new Fence(id, old.sequence, true)); active.remove(id.owner); }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
    }
    public Frame current(UUID owner) { return active.get(owner); }
    public int size() { return active.size(); }
    public int watermarkCount() { return fences.size(); }
    public void clear() { for (Frame f : List.copyOf(active.values())) retire(f.id); }
    public void resetSession() { active.clear(); fences.clear(); cancelledEpoch = cancelledThrough = 0; }

    /** Quiet heartbeat timeout; indefinite toggles use a wrapping animation clock, never growing history. */
    public static final class Lease {
        private int frame, quiet, hitAge = HIT_TICKS, refractionAge = REFRACT_TICKS;
        public void received(int phase) {
            quiet = 0;
            if (phase == HIT) hitAge = 0;
            if (phase == REFRACT) refractionAge = 0;
        }
        public boolean tick(boolean paused, boolean valid) {
            if (!valid) return false;
            if (paused) return true;
            frame = (frame + 1) % 1200;
            hitAge = Math.min(HIT_TICKS, hitAge + 1); refractionAge = Math.min(REFRACT_TICKS, refractionAge + 1);
            return ++quiet < QUIET_TICKS;
        }
        public int frame() { return frame; }
        public int quiet() { return quiet; }
        public int hitAge() { return hitAge; }
        public int refractionAge() { return refractionAge; }
    }
}
