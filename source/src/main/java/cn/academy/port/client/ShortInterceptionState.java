package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Receipt-owned target marker and bounded native relocation/contact receipts; no prediction. */
public final class ShortInterceptionState {
    public static final int MARKED = 0, PROJECTED = 1, IMPACT = 2, REFUSED = 3, CANCELLED = 4, ENDED = 5;
    public static final int TARGET_LIFE_TICKS = cn.academy.port.highpower.core.ShortInterceptionRules.MARK_TICKS, HEARTBEAT_TIMEOUT_TICKS = 30,
            RECEIPT_WINDOW_TICKS = 60, MAX_WATERMARKS = 1024, MAX_GROUPS = 4;
    private static final UUID ZERO = new UUID(0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Segment(Point entry, Point exit, Point incoming, Point outgoing) {}
    public record Frame(Identity id, long sequence, int phase, int remainingTicks, Point target,
                        List<Segment> segments, String reason, boolean fallback, int admitted) {
        public Frame { if (segments != null) segments = List.copyOf(segments); }
    }
    private static final class Fence {
        Frame frame;
        boolean retired, projected;
        int receiptAge;
        final Set<Segment> contacts = new HashSet<>();
        Fence(Frame frame) { this.frame = frame; }
    }
    private final Map<UUID, Fence> fences = new HashMap<>();
    private Frame active;
    private int remaining, quiet;
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || ZERO.equals(f.id.owner)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < MARKED || f.phase > ENDED || f.remainingTicks < 0 || f.remainingTicks > TARGET_LIFE_TICKS
                || f.reason == null || f.reason.length() > 128 || f.segments == null || f.segments.size() > MAX_GROUPS
                || f.target != null && !f.target.finite() || f.admitted < 0 || f.admitted > MAX_GROUPS
                || f.fallback && f.admitted > 1) return false;
        for (Segment s : f.segments) if (s == null || s.entry == null || !s.entry.finite()
                || s.exit == null || !s.exit.finite() || !direction(s.incoming) || !direction(s.outgoing)) return false;
        if (f.phase == PROJECTED) return f.remainingTicks == 0 && f.target != null
                && f.admitted > 0 && f.segments.size() == f.admitted;
        if (f.phase == IMPACT) return f.remainingTicks == 0 && f.admitted > 0 && f.segments.size() == 1;
        // Refusal/cleanup can carry bounded accounting from a partially paid cast. It is never a visual move.
        if (terminal(f)) return f.remainingTicks == 0 && f.segments.size() == f.admitted;
        if (!f.segments.isEmpty() || f.admitted != 0) return false;
        if (f.phase == MARKED) return f.target != null && f.remainingTicks > 0;
        if (f.phase == REFUSED) return f.remainingTicks == 0 || f.target != null;
        return f.remainingTicks == 0;
    }
    public static boolean terminal(Frame f) {
        return f != null && (f.phase == PROJECTED || f.phase == CANCELLED || f.phase == ENDED
                || f.phase == REFUSED && f.remainingTicks == 0);
    }
    public boolean permitsImpact(Frame f) {
        if (!valid(f) || f.phase != IMPACT) return false;
        var old = fences.get(f.id.owner);
        return old != null && old.projected && !old.retired && old.receiptAge < RECEIPT_WINDOW_TICKS
                && sameController(old.frame.id, f.id) && old.frame.id.input == f.id.input
                && f.sequence > old.frame.sequence && f.admitted == old.frame.admitted && f.fallback == old.frame.fallback
                && old.contacts.size() < f.admitted && !old.contacts.contains(f.segments.getFirst());
    }
    /** A finished input may still receive its server cleanup; it cannot create or renew a cosmetic. */
    public boolean permitsEnd(Frame f) {
        if (!valid(f) || f.phase != CANCELLED && f.phase != ENDED) return false;
        var old = fences.get(f.id.owner);
        return old != null && old.projected && !old.retired && sameController(old.frame.id,f.id)
                && f.id.input == old.frame.id.input && f.sequence > old.frame.sequence;
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || !local && f.phase != PROJECTED && f.phase != IMPACT && !permitsEnd(f)
                || local && (f.id.epoch < cancelledEpoch || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        var old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        if (f.phase == IMPACT) {
            if (!permitsImpact(f)) return false;
            old.contacts.add(f.segments.getFirst()); old.frame = f; return true;
        }
        if (permitsEnd(f)) { old.frame = f; old.retired = true; return true; }
        boolean same = old != null && sameController(old.frame.id, f.id);
        if (old != null) {
            Frame before = old.frame;
            if (f.id.epoch < before.id.epoch || f.id.epoch == before.id.epoch && f.id.token < before.id.token) return false;
            if (f.id.epoch == before.id.epoch && f.id.token == before.id.token) {
                if (!same || old.retired || old.projected || f.sequence <= before.sequence || f.id.input < before.id.input) return false;
                if (f.remainingTicks > before.remainingTicks) return false;
                // A heartbeat never advances to a nonce for which the server has not sent a release result.
                if (f.phase == MARKED && f.id.input != before.id.input) return false;
                if (f.phase == PROJECTED && f.id.input <= before.id.input) return false;
            } else if (f.id.epoch == before.id.epoch && f.id.input <= before.id.input) return false;
        }
        var fence = same ? old : new Fence(f);
        fence.frame = f; fence.projected = f.phase == PROJECTED;
        fence.retired = terminal(f) && !fence.projected;
        fences.put(f.id.owner, fence);
        if (local) {
            int lease = same && active != null ? Math.min(remaining, f.remainingTicks) : f.remainingTicks;
            active = terminal(f) ? null : f;
            remaining = active == null ? 0 : lease; quiet = 0;
        }
        return true;
    }
    public Frame active() { return active; }
    public int remainingTicks() { return remaining; }
    public int watermarkCount() { return fences.size(); }
    public boolean markerVisible() { return active != null && remaining > 0 && quiet < HEARTBEAT_TIMEOUT_TICKS; }
    /** Pausing freezes both leases and latency windows. A missing heartbeat only hides the marker. */
    public Identity tick(boolean paused, boolean validOwner) {
        if (!paused) for (var fence : fences.values()) if (fence.projected && fence.receiptAge < RECEIPT_WINDOW_TICKS) fence.receiptAge++;
        if (active == null) return null;
        if (!paused) { quiet++; remaining = Math.max(0, remaining - 1); }
        if (validOwner && remaining > 0) return null;
        var id = active.id; retire(id); return id;
    }
    public void retire(Identity id) {
        if (id == null) return;
        var old = fences.get(id.owner);
        if (old != null && sameController(old.frame.id, id)) old.retired = true;
        if (active != null && sameController(active.id, id)) { active = null; remaining = quiet = 0; }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
        if (active != null && active.id.epoch == epoch && active.id.input <= input) retire(active.id);
    }
    public void clear() { fences.values().forEach(f -> f.retired = true); active = null; remaining = quiet = 0; }
    public void resetSession() { fences.clear(); active = null; remaining = quiet = 0; cancelledEpoch = cancelledThrough = 0; }
    private static boolean sameController(Identity a, Identity b) {
        return a.entity == b.entity && a.owner.equals(b.owner) && a.epoch == b.epoch && a.token == b.token;
    }
    private static boolean direction(Point p) { return p != null && p.finite() && p.distanceSquared(new Point(0,0,0)) > 1e-12; }
}
