package cn.academy.port.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** NEW222 transport gate. Finite per-owner controllers with connection-lifetime replay fences. */
public final class ThermalShellState {
    public static final int ACTIVE = 0, HIT = 1, PULSE = 2, ENDED = 3;
    public static final int MAX_CONTEXTS = 64, MAX_WATERMARKS = 1024, QUIET_TICKS = 12, HIT_TICKS = 4, PULSE_TICKS = 6;
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, ThermalShellMesh.Point origin,
                        ThermalShellMesh.Point facing, ThermalShellMesh.Point impact) {}
    private record Fence(Identity id, long sequence, boolean retired) {}
    private final Map<UUID, Frame> active = new HashMap<>();
    private final Map<UUID, Fence> fences = new HashMap<>();
    private long cancelledEpoch, cancelledThrough;

    /** NEW222 starts at LV2, including both the learning tier and every supported later tier. */
    public static boolean eligibleLevel(int level) { return level >= 2 && level <= 5; }

    public static boolean valid(Frame frame) {
        if (frame == null || frame.id == null || frame.id.owner == null || frame.id.entity < 0
                || frame.id.epoch <= 0 || frame.id.input <= 0 || frame.id.token <= 0 || frame.sequence <= 0
                || frame.phase < ACTIVE || frame.phase > ENDED || !ThermalShellMesh.validPoint(frame.origin)
                || !ThermalShellMesh.validDirection(frame.facing) || !ThermalShellMesh.validPoint(frame.impact)) return false;
        // Impact geometry is local to the owner; a malformed packet must never create a distant flash.
        var contactDelta = frame.impact.minus(frame.origin.plus(new ThermalShellMesh.Point(0, 1, 0)));
        double contactDistance = contactDelta.length();
        // The server retains the real center for the degenerate source-at-center contact.
        if (frame.phase == HIT) return contactDistance <= .001 || Math.abs(contactDistance - ThermalShellMesh.SHELL_RADIUS) <= .001;
        // Deferred release retains its absorption-center snapshot while the owner may drift <=1m.
        if (frame.phase == PULSE) return contactDelta.dot(contactDelta) <= 1;
        return contactDistance <= 1.5;
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

    /** Historical event anchors survive moving keepalives, and ACTIVE can never invent a contact. */
    public static final class Events {
        public record Contact(ThermalShellMesh.Point point, ThermalShellMesh.Point outward, long salt) {}
        private Contact hit;
        private ThermalShellMesh.Point pulse;
        public void received(Frame frame) {
            if (!valid(frame)) return;
            if (frame.phase == HIT) {
                var direction = frame.impact.minus(frame.origin.plus(new ThermalShellMesh.Point(0, 1, 0)));
                hit = new Contact(frame.impact, direction.length() > 1e-8 ? direction.scale(1 / direction.length()) : frame.facing,
                        frame.id.token ^ frame.sequence);
            }
            if (frame.phase == PULSE) pulse = frame.impact;
        }
        public Contact hit() { return hit; }
        public ThermalShellMesh.Point pulse() { return pulse; }
    }

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
