package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** NEW312 terminal receipts only; finite geometry and non-evicting replay fences never predict a dodge. */
public final class PhaseSidestepState {
    public static final int SUCCESS = 1, REFUSED = 3, MAX_WATERMARKS = 1024;
    private static final UUID ZERO_UUID = new UUID(0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, Point source, Point destination,
                        Box bounds, boolean moved, String reason, boolean left) {}
    private final Map<UUID, Frame> fences = new HashMap<>();
    private long cancelledEpoch, cancelledThrough;
    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(ZERO_UUID)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase != SUCCESS && f.phase != REFUSED || f.reason == null || f.reason.length() > 128
                || f.source == null || !f.source.finite() || f.destination != null && !f.destination.finite()
                || f.bounds != null && !f.bounds.body()) return false;
        if (f.phase == REFUSED) return !f.moved;
        return f.moved && f.destination != null && f.bounds != null
                && Math.sqrt(f.source.distanceSquared(f.destination)) >= cn.academy.port.highpower.core.PhaseSidestepRules.DISTANCE - 1e-6 && Math.sqrt(f.source.distanceSquared(f.destination)) <= cn.academy.port.highpower.core.PhaseSidestepRules.MAX_DISTANCE + 1e-6
                && Math.abs(f.source.y() - f.destination.y()) <= 1e-5
                && Math.abs(f.bounds.minY() - f.destination.y()) <= 1e-5
                && Math.abs((f.bounds.minX() + f.bounds.maxX()) / 2 - f.destination.x()) <= 1e-5
                && Math.abs((f.bounds.minZ() + f.bounds.maxZ()) / 2 - f.destination.z()) <= 1e-5;
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || !local && f.phase != SUCCESS || local && (f.id.epoch < cancelledEpoch
                || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        var old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        if (old != null && (f.id.epoch < old.id.epoch || f.id.epoch == old.id.epoch
                && (f.id.input <= old.id.input || f.id.token <= old.id.token))) return false;
        fences.put(f.id.owner, f); return true;
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
    }
    public int watermarkCount() { return fences.size(); }
    public void resetSession() { fences.clear(); cancelledEpoch = cancelledThrough = 0; }
}
