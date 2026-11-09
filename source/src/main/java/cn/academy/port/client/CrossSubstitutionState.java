package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import cn.academy.port.highpower.core.CrossSwapGeometry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** NEW321 authenticated immutable preparation, native needles and confirmed self-swap endpoints. */
public final class CrossSubstitutionState {
    public static final int MARKED = 1, PROJECTED = 2, IMPACT = 3, ENDED = 4, REFUSED = 5,
            SWAP_REFUSED = 6, SWAPPED = 7;
    public static final int PREPARATION_TICKS = cn.academy.port.highpower.core.CrossSubstitutionRules.PREPARATION_TICKS, HEARTBEAT_TIMEOUT_TICKS = 30, MAX_NEEDLES = 3, MAX_WATERMARKS = 1024;
    private static final UUID ZERO = new UUID(0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, int prepared, int remaining,
                        Point pointA, Point pointB, Point source, Point arrival, Point impact,
                        int needleSlot, int needleEntity, UUID needleUuid, boolean accepted, String reason) {}
    private static final class Fence {
        Frame frame;
        final Frame[] needles = new Frame[MAX_NEEDLES];
        boolean retired, firing;
        Fence(Frame frame) { this.frame = frame; }
    }
    private final Map<UUID, Fence> fences = new HashMap<>();
    private Frame active;
    private int remaining, quiet;
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || !uuid(f.id.owner) || f.id.epoch <= 0
                || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0 || f.phase < MARKED || f.phase > SWAPPED
                || f.prepared < 0 || f.prepared > 2 || f.remaining < 0 || f.remaining > PREPARATION_TICKS
                || f.reason == null || f.reason.length() > 128 || !optional(f.pointA) || !optional(f.pointB)
                || !optional(f.source) || !optional(f.arrival) || !optional(f.impact)
                || f.prepared >= 1 && f.pointA == null || f.prepared == 2 && f.pointB == null
                || f.prepared == 0 && (f.pointA != null || f.pointB != null) || f.prepared == 1 && f.pointB != null) return false;
        if (f.phase == MARKED) return f.prepared > 0 && f.remaining > 0 && f.accepted;
        if (f.phase == SWAP_REFUSED) return !f.accepted && noNeedle(f)
                && (f.prepared == 0 ? f.remaining == 0 : f.remaining > 0);
        if (f.phase == SWAPPED) return f.prepared == 2 && f.remaining == 0 && f.accepted && noNeedle(f)
                && CrossSwapGeometry.withinRange(f.source, f.arrival) && f.arrival.equals(f.impact);
        if (f.phase == PROJECTED || f.phase == IMPACT) return f.prepared == 2 && f.remaining == 0
                && f.source != null && f.arrival != null && f.impact != null && f.needleSlot >= 0
                && f.needleSlot < MAX_NEEDLES && f.needleEntity >= 0 && uuid(f.needleUuid)
                && (f.phase == IMPACT || !f.accepted);
        return f.remaining == 0 && (f.phase != REFUSED || !f.accepted);
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || !local && (f.phase == MARKED || f.phase == REFUSED || f.phase == SWAP_REFUSED)
                || local && (f.id.epoch < cancelledEpoch || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        var old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        boolean same = old != null && sameController(old.frame.id, f.id);
        if (old != null) {
            var before = old.frame;
            if (f.id.epoch < before.id.epoch || f.id.epoch == before.id.epoch && f.id.token < before.id.token) return false;
            if (f.id.epoch == before.id.epoch && f.id.token == before.id.token) {
                if (!same || old.retired || f.sequence <= before.sequence || f.id.input < before.id.input
                        || before.prepared > f.prepared || before.pointA != null && !before.pointA.equals(f.pointA)
                        || before.pointB != null && !before.pointB.equals(f.pointB)) return false;
                if ((preparation(f) || f.phase == SWAPPED) && old.firing) return false;
                if (f.phase == SWAPPED && f.id.input <= before.id.input) return false;
                if (f.phase == SWAP_REFUSED && (f.prepared != before.prepared || f.remaining > before.remaining)) return false;
                if (f.phase == MARKED && (f.remaining > before.remaining
                        || f.prepared > before.prepared && f.id.input <= before.id.input)) return false;
            } else if (f.id.epoch == before.id.epoch && f.id.input <= before.id.input) return false;
        }
        if (!same && f.phase == IMPACT) return false;
        var fence = same ? old : new Fence(f);
        if (f.phase == PROJECTED) {
            if (fence.needles[f.needleSlot] != null) return false;
            for (var n : fence.needles) if (n != null && (n.needleEntity == f.needleEntity || n.needleUuid.equals(f.needleUuid))) return false;
            fence.needles[f.needleSlot] = f; fence.firing = true;
        } else if (f.phase == IMPACT) {
            var before = fence.needles[f.needleSlot];
            if (before == null || before.phase != PROJECTED || before.needleEntity != f.needleEntity
                    || !before.needleUuid.equals(f.needleUuid) || !before.source.equals(f.source)
                    || !before.arrival.equals(f.arrival)) return false;
            fence.needles[f.needleSlot] = f;
        }
        fence.frame = f; fence.retired = terminal(f); fences.put(f.id.owner, fence);
        if (local) {
            int lease = same && active != null ? Math.min(remaining, f.remaining) : f.remaining;
            active = terminal(f) ? null : f;
            remaining = preparation(f) ? lease : 0; quiet = 0;
        }
        return true;
    }
    public Frame active() { return active; }
    public int remainingTicks() { return remaining; }
    public boolean markerVisible() { return preparation(active) && remaining > 0 && quiet < HEARTBEAT_TIMEOUT_TICKS; }
    public boolean firing() { return active != null && (active.phase == PROJECTED || active.phase == IMPACT); }
    public static boolean terminal(Frame f) { return f != null && (f.phase == ENDED || f.phase == REFUSED
            || f.phase == SWAPPED || f.phase == SWAP_REFUSED && f.prepared == 0); }
    public static boolean preparation(Frame f) { return f != null && (f.phase == MARKED || f.phase == SWAP_REFUSED && f.prepared > 0); }
    /** Missing heartbeats hide the mark, without cancelling its server preparation. */
    public Identity tick(boolean paused, boolean validOwner) {
        if (active == null) return null;
        var id = active.id;
        if (!validOwner) { retire(id); return id; }
        if (!paused) { quiet++; if (remaining > 0) remaining--; }
        if (preparation(active) && remaining == 0) { retire(id); return id; }
        return null;
    }
    public void retire(Identity id) {
        if (id == null) return;
        var fence = fences.get(id.owner);
        if (fence != null && sameController(fence.frame.id, id)) fence.retired = true;
        if (active != null && sameController(active.id, id)) { active = null; remaining = quiet = 0; }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
        if (active != null && active.id.epoch == epoch && active.id.input <= input) retire(active.id);
    }
    public void clear() { if (active != null) retire(active.id); }
    public void resetSession() { fences.clear(); active = null; remaining = quiet = 0; cancelledEpoch = cancelledThrough = 0; }
    public int watermarkCount() { return fences.size(); }
    /** Read-only proximity guidance; the server independently validates posture, route and payment. */
    public static int nearbyMark(Frame f, Point feet) {
        return !preparation(f) || f.prepared != 2 ? 0 : CrossSwapGeometry.sourceIndex(feet, f.pointA, f.pointB) + 1;
    }
    public static Point relative(Point p, Point camera) {
        return p == null || camera == null || !p.finite() || !camera.finite() ? null
                : new Point(p.x() - camera.x(), p.y() - camera.y(), p.z() - camera.z());
    }
    public static float labelScale(double depth, double distance, int pixels) {
        if (!Double.isFinite(depth) || !Double.isFinite(distance) || depth <= .35 || distance < 0 || pixels <= 0) return 0;
        return (float)Math.min(Math.min(.022, .010 + distance * .00025), depth * .22 / pixels);
    }
    private static boolean sameController(Identity a, Identity b) {
        return a.entity == b.entity && a.owner.equals(b.owner) && a.epoch == b.epoch && a.token == b.token;
    }
    private static boolean uuid(UUID value) { return value != null && !ZERO.equals(value); }
    private static boolean noNeedle(Frame f) { return f.needleSlot == -1 && f.needleEntity == -1 && (f.needleUuid == null || ZERO.equals(f.needleUuid)); }
    private static boolean optional(Point value) { return value == null || value.finite(); }
}
