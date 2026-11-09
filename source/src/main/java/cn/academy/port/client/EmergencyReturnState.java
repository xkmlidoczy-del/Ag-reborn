package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerRecoveryHudModel;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** NEW342 receipt-only mark, queued automatic return, and terminal arrival fences. */
public final class EmergencyReturnState {
    public static final int MARKED = 0, WARNING = 1, SUCCESS = 2, ENDED = 3, REFUSED = 4;
    public static final int MAX_WATERMARKS = 1024, MARK_LIFE_TICKS = 1200, WARNING_TICKS = 1,
            HEARTBEAT_TIMEOUT_TICKS = 30, AUTOMATIC_COOLDOWN_TICKS = 1200;
    private static final UUID ZERO = new UUID(0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, Point source, Point destination,
                        Box bounds, boolean moved, String reason, int remainingTicks,
                        String currentBudget, String maxBudget, int automaticCooldownTicks) {}
    private record Fence(Frame frame, boolean retired) {}
    private final Map<UUID, Fence> fences = new HashMap<>();
    private Frame active, status;
    private int quiet, remaining, warningAge, statusAge, automaticRemaining;
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || ZERO.equals(f.id.owner)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < MARKED || f.phase > REFUSED || f.remainingTicks < 0 || f.remainingTicks > MARK_LIFE_TICKS
                || f.reason == null || f.reason.length() > 128 || f.source != null && !f.source.finite()
                || f.destination != null && !f.destination.finite() || f.bounds != null && !f.bounds.body()
                || f.currentBudget == null || f.maxBudget == null
                || f.automaticCooldownTicks < 0 || f.automaticCooldownTicks > AUTOMATIC_COOLDOWN_TICKS) return false;
        try {
            if (HighPowerRecoveryHudModel.decimal(f.currentBudget).compareTo(HighPowerRecoveryHudModel.decimal(f.maxBudget)) > 0) return false;
        } catch (IllegalArgumentException invalid) { return false; }
        if (f.phase == ENDED) return !f.moved && f.remainingTicks == 0;
        if (f.phase == REFUSED) return !f.moved;
        if (f.source == null || f.destination == null || f.bounds == null
                || Math.abs(f.bounds.minY() - f.destination.y()) > 1e-5
                || Math.abs((f.bounds.minX() + f.bounds.maxX()) / 2 - f.destination.x()) > 1e-5
                || Math.abs((f.bounds.minZ() + f.bounds.maxZ()) / 2 - f.destination.z()) > 1e-5) return false;
        if (f.phase == MARKED) return !f.moved && f.remainingTicks > 0;
        if (f.source.distanceSquared(f.destination) > (cn.academy.port.highpower.core.EmergencyReturnRules.RANGE * cn.academy.port.highpower.core.EmergencyReturnRules.RANGE) + 1e-7) return false;
        return f.phase == WARNING ? !f.moved && f.remainingTicks == WARNING_TICKS : f.moved && f.remainingTicks == 0;
    }
    public static boolean terminal(Frame f) {
        return f != null && (f.phase == SUCCESS || f.phase == ENDED || f.phase == REFUSED && f.remainingTicks == 0);
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || !local && (f.phase == MARKED || f.phase == REFUSED)
                || local && (f.id.epoch < cancelledEpoch || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        var old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        boolean same = old != null && sameController(old.frame.id, f.id);
        if (!local && f.phase == ENDED && (!same || old.retired || old.frame.phase != WARNING)) return false;
        if (old != null) {
            var before = old.frame;
            if (f.id.epoch < before.id.epoch || f.id.epoch == before.id.epoch && f.id.token < before.id.token) return false;
            if (f.id.epoch == before.id.epoch && f.id.token == before.id.token) {
                if (!same || old.retired || f.sequence <= before.sequence || f.id.input < before.id.input) return false;
                if (f.phase == MARKED && (before.phase == WARNING || f.remainingTicks > before.remainingTicks)) return false;
                if (f.remainingTicks > 0 && f.phase != WARNING && before.phase != WARNING
                        && f.remainingTicks > before.remainingTicks) return false;
                if (before.destination != null && f.destination != null && !Objects.equals(before.destination, f.destination)) return false;
                if (before.bounds != null && f.bounds != null && !Objects.equals(before.bounds, f.bounds)) return false;
                if (before.phase == WARNING && (f.phase == WARNING || f.phase == SUCCESS)
                        && (f.id.input != before.id.input || !Objects.equals(f.source, before.source))) return false;
            } else if (f.id.epoch == before.id.epoch && f.id.input <= before.id.input) return false;
        }
        // A refusal can carry no geometry. Retain only already authenticated coordinates.
        if (local && f.phase == REFUSED && f.remainingTicks > 0 && (f.destination == null || f.bounds == null)) {
            if (!same || active == null || active.destination == null || active.bounds == null) return false;
            f = new Frame(f.id, f.sequence, f.phase, active.source, active.destination, active.bounds, false,
                    f.reason, f.remainingTicks, f.currentBudget, f.maxBudget, f.automaticCooldownTicks);
        }
        fences.put(f.id.owner, new Fence(f, terminal(f)));
        if (local) {
            boolean sameWarning = same && active != null && active.phase == WARNING && f.phase == WARNING;
            status = f; statusAge = 0; automaticRemaining = f.automaticCooldownTicks;
            active = terminal(f) ? null : f; remaining = active == null ? 0 : f.remainingTicks; quiet = 0;
            if (!sameWarning) warningAge = 0;
        }
        return true;
    }
    public Frame active() { return active; }
    public Frame status() { return status; }
    public int remainingTicks() { return remaining; }
    public int warningAge() { return warningAge; }
    public int automaticRemainingTicks() { return automaticRemaining; }
    public boolean statusFresh() { return status != null && statusAge < HEARTBEAT_TIMEOUT_TICKS; }
    public boolean markerVisible() {
        return active != null && quiet < HEARTBEAT_TIMEOUT_TICKS && (active.phase == WARNING ? warningAge < WARNING_TICKS : remaining > 0);
    }
    public boolean queuedVisible() { return active != null && active.phase == WARNING && quiet < HEARTBEAT_TIMEOUT_TICKS; }
    public int watermarkCount() { return fences.size(); }
    /** A missing heartbeat or local countdown hides cosmetics only; only context loss can cancel. */
    public Identity tick(boolean paused, boolean validOwner) {
        if (!paused) {
            if (statusAge < Integer.MAX_VALUE) statusAge++;
            if (automaticRemaining > 0) automaticRemaining--;
            if (active != null) { if (quiet < Integer.MAX_VALUE) quiet++; if (remaining > 0) remaining--; if (warningAge < Integer.MAX_VALUE) warningAge++; }
        }
        if (active == null || validOwner) return null;
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
        if (status != null && status.id.epoch == epoch && status.id.input <= input) status = null;
    }
    public void clear() { if (active != null) retire(active.id); status = null; automaticRemaining = 0; }
    public void resetSession() { fences.clear(); active = status = null; quiet = remaining = warningAge = statusAge = automaticRemaining = 0; cancelledEpoch = cancelledThrough = 0; }
    public static Point relative(Point p, Point camera) {
        return p == null || camera == null || !p.finite() || !camera.finite() ? null
                : new Point(p.x() - camera.x(), p.y() - camera.y(), p.z() - camera.z());
    }
    /** A projected width cap prevents a nearby world label becoming a screen-sized HUD strip. */
    public static float labelScale(double depth, double distance, int pixels) {
        if (!Double.isFinite(depth) || !Double.isFinite(distance) || depth <= .5 || distance < .8 || pixels <= 0) return 0;
        return (float)Math.min(Math.min(.024, .012 + distance * .0004), depth * .16 / pixels);
    }
    private static boolean sameController(Identity a, Identity b) {
        return a.entity == b.entity && a.owner.equals(b.owner) && a.epoch == b.epoch && a.token == b.token;
    }
}
