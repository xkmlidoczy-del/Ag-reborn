package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Game-independent geometry and input fences for the explicit NEW341 menu. */
public final class SpatialPincerUiState {
    public static final int SLOTS = 3, MAX_RECIPIENTS = 32, MAX_NOTICES = 8;
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) { return px >= x && py >= y && px < x + width && py < y + height; }
    }
    public record Layout(Rect panel, int inset, int columnWidth) {
        public Rect button(int column) { return new Rect(panel.x + inset + column * (columnWidth + 6), panel.y + 154, columnWidth, 20); }
        public Rect recipient(int row) { return new Rect(panel.x + inset, panel.y + 61 + row * 20, panel.width - inset * 2, 18); }
    }
    public static Layout layout(int width, int height) {
        int panelWidth = Math.min(296, Math.max(120, width - 12));
        int panelHeight = 180;
        return new Layout(new Rect((width - panelWidth) / 2, Math.max(0, (height - panelHeight) / 2), panelWidth, panelHeight), 8, (panelWidth - 22) / 2);
    }
    public static long invitationDeadline(long now, int remaining, long previousDeadline) {
        return Math.min(previousDeadline, now + Math.max(0, Math.min(200, remaining)));
    }
    /** Observed motion on an uncertain or failed transport is not a confirmed arrival. */
    public static boolean confirmedArrival(String phase, String reason, boolean moved) {
        return moved && "success".equals(phase) && "".equals(reason);
    }

    /** A held arrow/Enter never repeats. Opening with a held input requires release before action. */
    public static final class Keys {
        private final Set<Integer> down = new HashSet<>();
        public void blockHeld(int key) { down.add(key); }
        public boolean press(int key, boolean panelFocused) { boolean edge = down.add(key); return edge && panelFocused; }
        public void release(int key) { down.remove(key); }
        public void clear() { down.clear(); }
    }
    public record Identity(long token, UUID nonce) {}
    /** Only an explicit review request may replace its exact initiating chat or the empty world screen. */
    public static final class ReviewGate {
        private UUID nonce;
        private Object origin;
        private long session;
        public void begin(UUID nextNonce, Object initiatingScreen, long generation) {
            nonce = nextNonce; origin = initiatingScreen; session = generation;
        }
        public boolean active() { return nonce != null; }
        public boolean expects(UUID candidate, long generation) { return nonce != null && nonce.equals(candidate) && session == generation; }
        public boolean allows(Object currentScreen, long generation) {
            return active() && session == generation && (currentScreen == null || currentScreen == origin);
        }
        public void cancel(UUID candidate) { if (nonce != null && nonce.equals(candidate)) clear(); }
        public void clear() { nonce = null; origin = null; session = 0; }
    }
    /** Bounded receipt replay protection; retired identities cannot be revived by late packets. */
    public static final class Receipts {
        private final LinkedHashMap<Identity, Long> latest = new LinkedHashMap<>();
        private final Set<Identity> retired = new HashSet<>();
        public boolean accept(Identity id, long sequence) {
            if (id == null || id.nonce == null || id.token <= 0 || sequence < 0 || retired.contains(id)) return false;
            Long old = latest.get(id);
            if (old != null && sequence <= old) return false;
            if (old == null && latest.size() >= 64) {
                Identity oldest = latest.keySet().iterator().next(); latest.remove(oldest); retired.remove(oldest);
            }
            latest.put(id, sequence);
            return true;
        }
        public void retire(Identity id) { if (id != null && latest.containsKey(id)) retired.add(id); }
        public void clear() { latest.clear(); retired.clear(); }
    }
    public static final int MARKED = 1, PROJECTED = 2, IMPACT = 3, ENDED = 4, REFUSED = 5, PICKER = 6, PREVIEW = 7;
    public static final int PLAN_TICKS = cn.academy.port.highpower.core.SpatialPincerCorridorRules.PREPARATION_TICKS, HEARTBEAT_TIMEOUT_TICKS = 30, MAX_TARGETS = 8, MAX_NEEDLES = 8, MAX_WATERMARKS = 1024;
    public record Controller(int entity, UUID owner, long epoch, long input, long token) {}
    /** Server-frozen identity and exact endpoint; never looked up again to follow a moving entity. */
    public record Target(int entity, UUID uuid, Point endpoint) {}
    public record Frame(Controller id, long sequence, int phase, int prepared, int remaining,
                        Point pointA, Point pointB, Point pointC, Point source, Point arrival, Point impact,
                        int needleSlot, int needleEntity, UUID needleUuid, boolean accepted, String reason,
                        List<Target> targets, int volleyNeedles) {
        public Frame { if (targets != null) targets = Collections.unmodifiableList(new ArrayList<>(targets)); }
        public Frame(Controller id, long sequence, int phase, int prepared, int remaining,
                     Point pointA, Point pointB, Point pointC, Point source, Point arrival, Point impact,
                     int needleSlot, int needleEntity, UUID needleUuid, boolean accepted, String reason) {
            this(id, sequence, phase, prepared, remaining, pointA, pointB, pointC, source, arrival, impact,
                    needleSlot, needleEntity, needleUuid, accepted, reason, List.of(),
                    phase == PROJECTED || phase == IMPACT ? SLOTS : 0);
        }
    }
    public static int requiredNeedles(int targets) { return targets >= 1 && targets <= MAX_TARGETS ? Math.max(SLOTS, targets) : 0; }
    public static int targetShares(int targets, int target) {
        int needles = requiredNeedles(targets);
        return needles == 0 || target < 0 || target >= targets ? 0 : 1 + (needles - 1 - target) / targets;
    }
    private static boolean targets(Frame f) {
        if (f.targets == null || f.targets.size() > MAX_TARGETS || f.volleyNeedles < 0 || f.volleyNeedles > MAX_NEEDLES) return false;
        if (f.targets.isEmpty()) return f.volleyNeedles == 0 || f.volleyNeedles >= SLOTS;
        if (f.volleyNeedles != requiredNeedles(f.targets.size())) return false;
        var entities = new HashSet<Integer>(); var identities = new HashSet<UUID>();
        for (var target : f.targets) if (target == null || target.entity < 0 || !uuid(target.uuid)
                || target.endpoint == null || !point(target.endpoint) || !entities.add(target.entity) || !identities.add(target.uuid)) return false;
        return true;
    }
    private static boolean uuid(UUID id) { return id != null && !id.equals(new UUID(0, 0)); }
    private static boolean point(Point p) { return p == null || p.finite() && Math.abs(p.x()) <= 30_000_001 && Math.abs(p.z()) <= 30_000_001 && Math.abs(p.y()) <= 1_000_000; }
    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || !uuid(f.id.owner) || f.id.epoch <= 0 || f.id.input <= 0
                || f.id.token <= 0 || f.sequence <= 0 || f.phase < MARKED || f.phase > PREVIEW || f.prepared < 0 || f.prepared > SLOTS
                || f.remaining < 0 || f.remaining > PLAN_TICKS || f.reason == null || f.reason.length() > 128
                || !targets(f)
                || !point(f.pointA) || !point(f.pointB) || !point(f.pointC) || !point(f.source) || !point(f.arrival) || !point(f.impact)
                || (f.prepared >= 1) != (f.pointA != null) || (f.prepared >= 2) != (f.pointB != null)
                || (f.prepared == 3) != (f.pointC != null)) return false;
        if (preparation(f)) return f.prepared > 0 && f.remaining > 0 && f.accepted
                && (f.phase != PICKER && f.phase != PREVIEW || f.prepared == SLOTS)
                && (f.phase == PREVIEW ? !f.targets.isEmpty() : f.targets.isEmpty() && f.volleyNeedles == 0);
        if (f.phase == PROJECTED || f.phase == IMPACT) return f.prepared == 3 && f.remaining == 0
                && f.source != null && f.arrival != null && f.impact != null && f.needleSlot >= 0
                && f.volleyNeedles >= SLOTS && f.needleSlot < f.volleyNeedles && f.needleEntity >= 0 && uuid(f.needleUuid)
                && f.arrival.equals(emitter(f, f.needleSlot))
                && (f.phase != PROJECTED || f.targets.isEmpty() || f.impact.equals(f.targets.get(f.needleSlot % f.targets.size()).endpoint))
                && (f.phase == IMPACT || !f.accepted);
        return f.remaining == 0 && !f.accepted;
    }
    private static Point emitter(Frame f, int needle) {
        Point point = switch (needle % SLOTS) { case 0 -> f.pointA; case 1 -> f.pointB; default -> f.pointC; };
        return point == null ? null : new Point(point.x(), point.y() + 1.2, point.z());
    }
    public static boolean terminal(Frame f) { return f != null && (f.phase == ENDED || f.phase == REFUSED); }
    public static boolean preparation(Frame f) { return f != null && (f.phase == MARKED || f.phase == PICKER || f.phase == PREVIEW); }
    public static boolean sameController(Controller a, Controller b) {
        return a != null && b != null && a.entity == b.entity && Objects.equals(a.owner, b.owner) && a.epoch == b.epoch && a.token == b.token;
    }
    /** Only actual published needles and matching actual contact receipts can produce cosmetics. */
    public static final class VisualState {
        private static final class Fence {
            Frame frame;
            final Frame[] needles = new Frame[MAX_NEEDLES];
            List<Target> targets = List.of();
            long previewInput;
            int volleyNeedles;
            boolean retired, firing;
            Fence(Frame frame) { this.frame = frame; }
        }
        private final Map<UUID, Fence> fences = new HashMap<>();
        private Frame active;
        private int remaining, quiet;
        private long cancelledEpoch, cancelledThrough;
        public boolean accept(Frame f, boolean local) {
            if (!valid(f) || !local && (preparation(f) || f.phase == REFUSED || !f.targets.isEmpty())
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
                            || before.pointB != null && !before.pointB.equals(f.pointB)
                            || before.pointC != null && !before.pointC.equals(f.pointC)) return false;
                    if (preparation(f) && (old.firing || f.remaining > before.remaining
                            || f.prepared > before.prepared && (f.id.input <= before.id.input || f.prepared != before.prepared + 1))) return false;
                    if (!old.targets.isEmpty() && (!old.targets.equals(f.targets) || old.volleyNeedles != f.volleyNeedles
                            || preparation(f) && f.phase != PREVIEW)) return false;
                    if (old.firing && (f.phase == PROJECTED || f.phase == IMPACT)
                            && (f.id.input != before.id.input || f.volleyNeedles != before.volleyNeedles)) return false;
                } else if (f.id.epoch == before.id.epoch && f.id.input <= before.id.input) return false;
            }
            if (!same && f.phase == IMPACT) return false;
            if (f.phase == PREVIEW && (!same || old.frame.prepared != SLOTS || old.firing
                    || old.targets.isEmpty() && f.id.input <= old.frame.id.input
                    || !old.targets.isEmpty() && f.id.input != old.previewInput)) return false;
            if (local && f.phase == PROJECTED && (!same || old.targets.isEmpty() || f.id.input <= old.previewInput)) return false;
            var fence = same ? old : new Fence(f);
            if (f.phase == PREVIEW) {
                fence.targets = f.targets; fence.previewInput = f.id.input; fence.volleyNeedles = f.volleyNeedles;
            } else if (f.phase == PROJECTED) {
                if (fence.needles[f.needleSlot] != null) return false;
                for (var n : fence.needles) if (n != null && (n.needleEntity == f.needleEntity || n.needleUuid.equals(f.needleUuid))) return false;
                fence.needles[f.needleSlot] = f; fence.firing = true;
            } else if (f.phase == IMPACT) {
                var before = fence.needles[f.needleSlot];
                if (before == null || before.phase != PROJECTED || before.needleEntity != f.needleEntity
                        || !before.needleUuid.equals(f.needleUuid) || !before.source.equals(f.source) || !before.arrival.equals(f.arrival)) return false;
                fence.needles[f.needleSlot] = f;
            }
            fence.frame = f; fence.retired = terminal(f); fences.put(f.id.owner, fence);
            if (local) {
                int lease = same && active != null ? Math.min(remaining, f.remaining) : f.remaining;
                active = terminal(f) ? null : f; remaining = preparation(f) ? lease : 0; quiet = 0;
            }
            return true;
        }
        public Frame active() { return active; }
        public int remainingTicks() { return remaining; }
        public boolean markerVisible() { return preparation(active) && remaining > 0 && quiet < HEARTBEAT_TIMEOUT_TICKS; }
        public boolean firing() { return active != null && (active.phase == PROJECTED || active.phase == IMPACT); }
        /** Silence hides stale previews without guessing a server cancellation. */
        public Controller tick(boolean paused, boolean validOwner) {
            if (active == null) return null;
            var id = active.id;
            if (!validOwner) { retire(id); return id; }
            if (!paused) { quiet++; if (remaining > 0) remaining--; }
            if (preparation(active) && remaining == 0) { retire(id); return id; }
            return null;
        }
        public void retire(Controller id) {
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
    }
    private SpatialPincerUiState() {}
}
