package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** NEW333 immutable pair lease and distinct authoritative warning/arrival phases. */
public final class CornerExchangeState {
    public static final int MARKED = 0, WARNING = 1, SUCCESS = 2, ENDED = 3, REFUSED = 4;
    public static final int MAX_WATERMARKS = 1024, PAIR_LIFE_TICKS = cn.academy.port.highpower.core.CornerExchangeRules.MARK_LIFE_TICKS, WARNING_TICKS = 3,
            HEARTBEAT_TIMEOUT_TICKS = 30;
    private static final UUID ZERO = new UUID(0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, Point source, Point destination,
                        Box bounds, boolean moved, String reason, int remainingTicks,
                        Point markA, Point markB, boolean reversed, int warningTicks) {}
    private record Fence(Frame frame, boolean retired, long completedThrough) {}
    private final Map<UUID, Fence> fences = new HashMap<>();
    private Frame active;
    private int quiet, remaining, warningRemaining;
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || ZERO.equals(f.id.owner)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < MARKED || f.phase > REFUSED || f.remainingTicks < 0 || f.remainingTicks > PAIR_LIFE_TICKS
                || f.reason == null || f.reason.length() > 128 || f.source == null || !f.source.finite()
                || f.destination == null || !f.destination.finite() || f.bounds != null && !f.bounds.body()
                || !optional(f.markA) || !optional(f.markB) || f.markA == null && f.markB != null
                || f.markA != null && f.markB != null && f.markA.distanceSquared(f.markB) > (cn.academy.port.highpower.core.CornerExchangeRules.RANGE * cn.academy.port.highpower.core.CornerExchangeRules.RANGE) + 1e-7
                || (f.markA == null) != (f.remainingTicks == 0)
                || f.moved != (f.phase == SUCCESS) || f.warningTicks < 0 || f.warningTicks > WARNING_TICKS
                || (f.phase == WARNING) != (f.warningTicks > 0)) return false;
        if (f.phase == ENDED) return f.markA == null;
        if (f.phase == REFUSED) return true;
        if (f.markA == null) return false;
        if (f.phase == MARKED) return true;
        return f.markB != null && f.bounds != null
                && Math.abs(f.bounds.minY() - f.destination.y()) <= 1e-5
                && Math.abs((f.bounds.minX() + f.bounds.maxX()) / 2 - f.destination.x()) <= 1e-5
                && Math.abs((f.bounds.minZ() + f.bounds.maxZ()) / 2 - f.destination.z()) <= 1e-5
                && f.source.distanceSquared(f.destination) <= (cn.academy.port.highpower.core.CornerExchangeRules.RANGE * cn.academy.port.highpower.core.CornerExchangeRules.RANGE) + 1e-7;
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || !local && (f.phase == MARKED || f.phase == REFUSED)
                || local && (f.id.epoch < cancelledEpoch || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        var old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        boolean same = old != null && sameController(old.frame.id, f.id);
        if (old != null) {
            var before = old.frame;
            if (f.id.epoch < before.id.epoch || f.id.epoch == before.id.epoch && f.id.token < before.id.token) return false;
            if (f.id.epoch == before.id.epoch && f.id.token == before.id.token) {
                if (!same || old.retired || f.sequence <= before.sequence || f.id.input < before.id.input) return false;
                if (f.phase != ENDED && (f.remainingTicks > before.remainingTicks
                        || before.markA != null && !before.markA.equals(f.markA)
                        || before.markB != null && !before.markB.equals(f.markB))) return false;
                if (before.markB == null && f.markB != null && (f.phase != MARKED || f.id.input <= before.id.input)) return false;
                if ((f.phase == WARNING || f.phase == SUCCESS) && f.id.input <= old.completedThrough) return false;
                if (before.phase == WARNING && f.id.input == before.id.input) {
                    if (f.phase == MARKED || !Objects.equals(before.markA, f.markA) && f.phase != ENDED) return false;
                    if ((f.phase == WARNING || f.phase == SUCCESS) && (!before.source.equals(f.source) || !before.destination.equals(f.destination)
                            || !before.bounds.equals(f.bounds) || before.reversed != f.reversed
                            || f.phase == WARNING && f.warningTicks > before.warningTicks)) return false;
                }
            } else if (f.id.epoch == before.id.epoch && f.id.input <= before.id.input) return false;
        }
        long completed = same ? old.completedThrough : 0;
        if (f.phase == SUCCESS || f.phase == REFUSED) completed = Math.max(completed, f.id.input);
        fences.put(f.id.owner, new Fence(f, terminal(f), completed));
        if (local) {
            int lease = same && active != null ? Math.min(remaining, f.remainingTicks) : f.remainingTicks;
            boolean sameWarning = same && active != null && active.phase == WARNING && f.phase == WARNING && active.id.input == f.id.input;
            warningRemaining = f.phase == WARNING ? sameWarning ? Math.min(warningRemaining, f.warningTicks) : f.warningTicks : 0;
            active = terminal(f) ? null : f; remaining = active == null ? 0 : lease; quiet = 0;
        }
        return true;
    }
    public static boolean terminal(Frame f) { return f != null && (f.phase == ENDED || f.phase == REFUSED && f.markA == null); }
    public Frame active() { return active; }
    public int remainingTicks() { return remaining; }
    public int warningRemainingTicks() { return warningRemaining; }
    public boolean markerVisible() { return active != null && remaining > 0 && quiet < HEARTBEAT_TIMEOUT_TICKS; }
    public boolean warningVisible() { return markerVisible() && active.phase == WARNING && warningRemaining > 0; }
    /** Silence hides geometry only. Warning expiry never invents arrival, terminates the pair or restarts its lease. */
    public Identity tick(boolean paused, boolean validOwner) {
        if (active == null) return null;
        if (!validOwner) { var id = active.id; retire(id); return id; }
        if (!paused) { quiet++; if (remaining > 0) remaining--; if (warningRemaining > 0) warningRemaining--; }
        if (remaining > 0) return null;
        var id = active.id; retire(id); return id;
    }
    public void retire(Identity id) {
        if (id == null) return;
        var fence = fences.get(id.owner);
        if (fence != null && sameController(fence.frame.id, id)) fences.put(id.owner, new Fence(fence.frame, true, fence.completedThrough));
        if (active != null && sameController(active.id, id)) { active = null; quiet = remaining = warningRemaining = 0; }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
        if (active != null && active.id.epoch == epoch && active.id.input <= input) retire(active.id);
    }
    public void clear() { if (active != null) retire(active.id); }
    public void resetSession() { fences.clear(); active = null; quiet = remaining = warningRemaining = 0; cancelledEpoch = cancelledThrough = 0; }
    public int watermarkCount() { return fences.size(); }
    public static Point relative(Point p, Point camera) {
        return p == null || camera == null || !p.finite() || !camera.finite() ? null
                : new Point(p.x() - camera.x(), p.y() - camera.y(), p.z() - camera.z());
    }
    public static float labelScale(double depth, double distance, int pixels) {
        if (!Double.isFinite(depth) || !Double.isFinite(distance) || depth <= .35 || distance < 0 || pixels <= 0) return 0;
        return (float)Math.min(Math.min(.025, .015 + distance * .00035), depth * .22 / pixels);
    }
    public static int nearbyMark(Frame f, Point feet) {
        if (f == null || f.markA == null || f.markB == null || feet == null || !feet.finite()) return 0;
        boolean a = near(feet, f.markA), b = near(feet, f.markB); return a == b ? 0 : a ? 1 : 2;
    }
    private static boolean near(Point p, Point m) {
        double x = p.x() - m.x(), z = p.z() - m.z();
        return x*x + z*z <= .75*.75 && Math.abs(p.y()-m.y()) <= .125;
    }
    private static boolean optional(Point p) { return p == null || p.finite(); }
    private static boolean sameController(Identity a, Identity b) {
        return a.entity == b.entity && a.owner.equals(b.owner) && a.epoch == b.epoch && a.token == b.token;
    }
}
