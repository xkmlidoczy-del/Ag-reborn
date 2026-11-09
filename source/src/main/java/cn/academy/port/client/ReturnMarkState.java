package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** NEW322 receipt-only mark/warning ownership and non-evicting per-owner replay fences. */
public final class ReturnMarkState {
    public static final int MARKED = 0, WARNING = 1, SUCCESS = 2, ENDED = 3, REFUSED = 4;
    public static final int MAX_WATERMARKS = 1024, MARK_LIFE_TICKS = 1200, WARNING_TICKS = 4,
            HEARTBEAT_TIMEOUT_TICKS = 30, ACK_TIMEOUT_TICKS = 60;
    private static final UUID ZERO_UUID = new UUID(0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, Point source, Point destination,
                        Box bounds, boolean moved, String reason, int remainingTicks) {}
    private record Fence(Frame frame, boolean retired) {}
    private final Map<UUID, Fence> fences = new HashMap<>();
    private Frame active;
    private int quiet, remaining, warningAge;
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(ZERO_UUID)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < MARKED || f.phase > REFUSED || f.remainingTicks < 0 || f.remainingTicks > MARK_LIFE_TICKS
                || f.reason == null || f.reason.length() > 128 || f.source != null && !f.source.finite()
                || f.destination != null && !f.destination.finite() || f.bounds != null && !f.bounds.body()) return false;
        if (f.phase == ENDED || f.phase == REFUSED) return !f.moved && f.remainingTicks == 0;
        if (f.source == null || f.destination == null || f.bounds == null
                || Math.abs(f.bounds.minY() - f.destination.y()) > 1e-5
                || Math.abs((f.bounds.minX() + f.bounds.maxX()) / 2 - f.destination.x()) > 1e-5
                || Math.abs((f.bounds.minZ() + f.bounds.maxZ()) / 2 - f.destination.z()) > 1e-5) return false;
        if (f.phase == MARKED) return !f.moved && f.remainingTicks > 0;
        if (f.source.distanceSquared(f.destination) > (cn.academy.port.highpower.core.ReturnMarkRules.RANGE * cn.academy.port.highpower.core.ReturnMarkRules.RANGE) + 1e-7) return false;
        return f.phase == WARNING ? !f.moved && f.remainingTicks <= WARNING_TICKS
                : f.moved && f.remainingTicks == 0;
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || !local && f.phase == MARKED || local && (f.id.epoch < cancelledEpoch
                || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence old = fences.get(f.id.owner);
        if (!local && (f.phase == ENDED || f.phase == REFUSED)
                && (old == null || old.retired || !sameController(old.frame.id, f.id) || old.frame.phase != WARNING)) return false;
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        if (old != null) {
            var before = old.frame;
            if (f.id.epoch < before.id.epoch || f.id.epoch == before.id.epoch && f.id.token < before.id.token) return false;
            if (f.id.epoch == before.id.epoch && f.id.token == before.id.token) {
                if (old.retired || f.id.entity != before.id.entity || f.id.input < before.id.input || f.sequence <= before.sequence)
                    return false;
                if (f.phase == MARKED && (before.phase != MARKED || f.id.input != before.id.input
                        || f.remainingTicks > before.remainingTicks || !Objects.equals(f.destination, before.destination)
                        || !Objects.equals(f.bounds, before.bounds))) return false;
                if ((f.phase == WARNING || f.phase == SUCCESS) && !Objects.equals(f.destination, before.destination)) return false;
                if (before.phase == WARNING && (f.phase == WARNING || f.phase == SUCCESS)
                        && (f.id.input != before.id.input || !Objects.equals(f.source, before.source)
                        || !Objects.equals(f.bounds, before.bounds)
                        || f.phase == WARNING && f.remainingTicks > before.remainingTicks)) return false;
            } else if (f.id.epoch == before.id.epoch && f.id.input <= before.id.input) return false;
        }
        boolean terminal = f.phase == SUCCESS || f.phase == ENDED || f.phase == REFUSED;
        fences.put(f.id.owner, new Fence(f, terminal));
        if (!terminal && local) {
            boolean samePhase = active != null && sameController(active.id, f.id) && active.phase == f.phase;
            active = f; quiet = 0;
            remaining = samePhase ? Math.min(remaining, f.remainingTicks) : f.remainingTicks;
            if (!samePhase) warningAge = f.phase == WARNING ? WARNING_TICKS - f.remainingTicks : 0;
        } else if (active != null && sameController(active.id, f.id)) { active = null; quiet = remaining = warningAge = 0; }
        return true;
    }
    public Frame active() { return active; }
    /** A heartbeat gap hides only the cosmetic marker; it does not revoke an authenticated anchor. */
    public boolean markerVisible() { return active != null && (active.phase != MARKED || quiet < HEARTBEAT_TIMEOUT_TICKS); }
    public int remainingTicks() { return remaining; }
    public int warningAge() { return warningAge; }
    public int watermarkCount() { return fences.size(); }
    /** WARNING's four cosmetic ticks never predict a terminal result or discard its pending receipt. */
    public Identity tick(boolean paused, boolean validOwner) {
        if (active == null) return null;
        if (!paused) { quiet++; remaining = Math.max(0, remaining - 1); if (active.phase == WARNING) warningAge++; }
        if (validOwner && (active.phase == WARNING ? quiet < ACK_TIMEOUT_TICKS : remaining > 0)) return null;
        var id = active.id; retire(id); return id;
    }
    public void retire(Identity id) {
        if (id == null) return;
        var old = fences.get(id.owner);
        if (old != null && sameController(old.frame.id, id)) fences.put(id.owner, new Fence(old.frame, true));
        if (active != null && sameController(active.id, id)) { active = null; quiet = remaining = warningAge = 0; }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
        if (active != null && active.id.epoch == epoch && active.id.input <= input) retire(active.id);
    }
    public void clear() { if (active != null) retire(active.id); }
    public void resetSession() { fences.clear(); active = null; quiet = remaining = warningAge = 0; cancelledEpoch = cancelledThrough = 0; }
    /** World coordinates stay doubles until subtraction, preserving corners at the world border. */
    public static Point relative(Point point, Point camera) {
        if (point == null || camera == null || !point.finite() || !camera.finite()) return null;
        return new Point(point.x() - camera.x(), point.y() - camera.y(), point.z() - camera.z());
    }
    private static boolean sameController(Identity a, Identity b) {
        return a.entity == b.entity && a.owner.equals(b.owner) && a.epoch == b.epoch && a.token == b.token;
    }
}
