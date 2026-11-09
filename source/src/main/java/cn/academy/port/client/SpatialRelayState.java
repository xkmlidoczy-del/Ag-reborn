package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** NEW343 owner-only immutable marks and truthful, server-confirmed native item movements. */
public final class SpatialRelayState {
    public static final int MARKED = 0, QUEUED = 1, MOVED = 2, ENDED = 3, REFUSED = 4;
    public static final int MAX_WATERMARKS = 1024, MARK_LIFE_TICKS = cn.academy.port.highpower.core.SpatialRelayRules.PREPARATION_TICKS,
            HEARTBEAT_TIMEOUT_TICKS = 30, MAX_ITEMS = 16, MAX_LEGS = 48;
    private static final UUID ZERO = new UUID(0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Leg(int itemEntityId, Point from, Point to) {}
    public record Frame(Identity id, long sequence, int phase, int remainingTicks,
                        Point markA, Point markB, Point markC, int eligible, int queued,
                        int completed, List<Leg> planned, Leg confirmed, String reason) {
        public Frame { planned = planned == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(planned)); }
        public int pointCount() { return markC != null ? 3 : markB != null ? 2 : markA != null ? 1 : 0; }
    }
    private record Fence(Frame frame, boolean retired, long lastSequence) {}
    private final Map<UUID, Fence> fences = new HashMap<>();
    private Frame active;
    private int quiet, remaining;
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || ZERO.equals(f.id.owner)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < MARKED || f.phase > REFUSED || f.remainingTicks < 0 || f.remainingTicks > MARK_LIFE_TICKS
                || !optional(f.markA) || !optional(f.markB) || !optional(f.markC)
                || f.markA == null && f.markB != null || f.markB == null && f.markC != null
                || (f.markA == null) != (f.remainingTicks == 0)
                || f.eligible < 0 || f.eligible > MAX_ITEMS || f.queued < 0 || f.queued > MAX_ITEMS
                || f.completed < 0 || f.completed > MAX_LEGS || f.planned == null || f.planned.size() > MAX_LEGS
                || f.planned.stream().anyMatch(p -> !validLeg(p))
                || f.reason == null || f.reason.length() > 128
                || (f.phase == MOVED) != (f.confirmed != null) || f.phase == MOVED && f.completed == 0
                || f.confirmed != null && (!validLeg(f.confirmed) || f.confirmed.from.equals(f.confirmed.to))) return false;
        if (f.phase == ENDED) return f.markA == null && f.queued == 0 && f.planned.isEmpty();
        if (f.phase == REFUSED) return f.markA != null || f.queued == 0 && f.planned.isEmpty();
        if (f.markA == null) return false;
        return f.phase == MARKED || f.markB != null;
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || !local && f.phase != MOVED && f.phase != ENDED
                || local && (f.id.epoch < cancelledEpoch || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        var old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        boolean same = old != null && sameController(old.frame.id, f.id);
        if (old != null) {
            var before = old.frame;
            if (f.id.epoch < before.id.epoch || f.id.epoch == before.id.epoch && f.id.token < before.id.token) return false;
            if (f.id.epoch == before.id.epoch && f.id.token == before.id.token) {
                if (!same || old.retired || f.sequence <= old.lastSequence || f.id.input < before.id.input) return false;
                if (!terminal(f)) {
                    if (f.remainingTicks > before.remainingTicks || before.markA != null && !before.markA.equals(f.markA)
                            || before.markB != null && !before.markB.equals(f.markB)
                            || before.markC != null && !before.markC.equals(f.markC)) return false;
                    boolean addition = f.pointCount() > before.pointCount();
                    if (addition && (f.phase != MARKED || f.id.input <= before.id.input || f.pointCount() != before.pointCount()+1)) return false;
                    if (f.phase == MARKED && f.id.input != before.id.input && !addition) return false;
                    if ((before.phase == QUEUED || before.phase == MOVED) && (f.phase == QUEUED || f.phase == MOVED)) {
                        if (f.id.input != before.id.input || f.queued > before.queued || f.completed < before.completed) return false;
                        if (f.phase == MOVED && f.completed != before.completed + 1) return false;
                    }
                }
            } else if (f.id.epoch == before.id.epoch && f.id.input <= before.id.input) return false;
        }
        fences.put(f.id.owner, new Fence(f, terminal(f), f.sequence));
        if (local) {
            int lease = same && active != null ? Math.min(remaining, f.remainingTicks) : f.remainingTicks;
            active = terminal(f) ? null : f; remaining = active == null ? 0 : lease; quiet = 0;
        }
        return true;
    }
    /** A new short release while a queue runs acknowledges that gesture without replacing the frozen queue nonce. */
    public boolean acceptQueueRefusal(Frame f) {
        if (!valid(f) || !queued() || f.phase != REFUSED || !"queue_running".equals(f.reason)
                || !sameController(active.id,f.id) || f.id.input <= active.id.input || f.remainingTicks > active.remainingTicks
                || !Objects.equals(active.markA,f.markA) || !Objects.equals(active.markB,f.markB)
                || !Objects.equals(active.markC,f.markC)) return false;
        var old = fences.get(f.id.owner);
        if (old == null || old.retired || f.sequence <= old.lastSequence) return false;
        fences.put(f.id.owner,new Fence(old.frame,false,f.sequence));
        remaining = Math.min(remaining,f.remainingTicks); quiet = 0;
        return true;
    }
    /** Clean completion may let only the already-confirmed last cue finish its existing seven-frame age. */
    public static boolean preservesConfirmedCue(Frame end, Frame moved) {
        return end != null && moved != null && end.phase == ENDED && "completed".equals(end.reason)
                && moved.phase == MOVED && moved.confirmed != null && sameController(end.id,moved.id)
                && end.id.input == moved.id.input && end.completed == moved.completed && end.sequence > moved.sequence;
    }
    public static boolean terminal(Frame f) { return f != null && (f.phase == ENDED || f.phase == REFUSED && f.markA == null); }
    public Frame active() { return active; }
    public int remainingTicks() { return remaining; }
    public int watermarkCount() { return fences.size(); }
    public boolean markerVisible() { return active != null && remaining > 0 && quiet < HEARTBEAT_TIMEOUT_TICKS; }
    public boolean queued() { return active != null && (active.phase == QUEUED || active.phase == MOVED); }
    /** Heartbeat silence only hides geometry. It never issues abort, creates an arrival or renews a lease. */
    public Identity tick(boolean paused, boolean validOwner) {
        if (active == null) return null;
        if (!paused) { quiet++; remaining = Math.max(0, remaining-1); }
        if (validOwner && remaining > 0) return null;
        var id = active.id; retire(id); return id;
    }
    public void retire(Identity id) {
        if (id == null) return;
        var old = fences.get(id.owner);
        if (old != null && sameController(old.frame.id, id)) fences.put(id.owner,new Fence(old.frame,true,old.lastSequence));
        if (active != null && sameController(active.id,id)) { active = null; remaining = quiet = 0; }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough,input);
        if (active != null && active.id.epoch == epoch && active.id.input <= input) retire(active.id);
    }
    public void clear() { fences.replaceAll((owner,fence) -> new Fence(fence.frame,true,fence.lastSequence)); active = null; remaining = quiet = 0; }
    public void resetSession() { fences.clear(); active = null; remaining = quiet = 0; cancelledEpoch = cancelledThrough = 0; }
    public static boolean sameController(Identity a, Identity b) {
        return a != null && b != null && a.entity == b.entity && Objects.equals(a.owner,b.owner) && a.epoch == b.epoch && a.token == b.token;
    }
    private static boolean optional(Point p) { return p == null || p.finite(); }
    private static boolean validLeg(Leg p) { return p != null && p.itemEntityId >= 0 && p.from != null && p.from.finite() && p.to != null && p.to.finite(); }
}
