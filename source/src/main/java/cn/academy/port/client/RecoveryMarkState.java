package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** NEW323 authenticated owner sessions carry at most eight independently expiring needle handles. */
public final class RecoveryMarkState {
    public static final int MARKED = 1, RECOVERED = 2, CLEARED = 3, REFUSED = 4;
    public static final int MAX_HANDLES = 8, MAX_LABELS = 1;
    public static final int MAX_WATERMARKS = 1024, MARK_LIFE_TICKS = 1200, HEARTBEAT_TIMEOUT_TICKS = 30;
    private static final UUID ZERO_UUID = new UUID(0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Handle(long token, int needleEntity, UUID needleUuid, Point source, Point anchor, int remainingTicks) {}
    public record Frame(Identity id, long sequence, int phase, Point source, Point anchor,
                        int needleEntity, UUID needleUuid, int remainingTicks, boolean accepted, String reason,
                        List<Handle> handles) {
        public Frame { handles = handles == null ? null : Collections.unmodifiableList(new ArrayList<>(handles)); }
        /** Compatibility for the original one-handle receipt contract. */
        public Frame(Identity id, long sequence, int phase, Point source, Point anchor,
                     int needleEntity, UUID needleUuid, int remainingTicks, boolean accepted, String reason) {
            this(id, sequence, phase, source, anchor, needleEntity, needleUuid, remainingTicks, accepted, reason,
                    phase == MARKED && id != null
                            ? List.of(new Handle(id.token, needleEntity, needleUuid, source, anchor, remainingTicks)) : List.of());
        }
    }
    private static final class Fence {
        final Frame frame;
        final Map<Long, Handle> handles = new LinkedHashMap<>();
        final long highHandle;
        boolean retired;
        Fence(Frame frame, long highHandle) {
            this.frame = frame; this.highHandle = highHandle; retired = frame.handles.isEmpty();
            for (var handle : frame.handles) handles.put(handle.token, handle);
        }
    }
    private final Map<UUID, Fence> fences = new HashMap<>();
    private final Map<Long, Integer> leases = new LinkedHashMap<>();
    private Frame active;
    private int quiet;
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(ZERO_UUID)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < MARKED || f.phase > REFUSED || f.remainingTicks < 0 || f.remainingTicks > MARK_LIFE_TICKS
                || f.reason == null || f.reason.length() > 128 || f.source != null && !f.source.finite()
                || f.anchor != null && !f.anchor.finite() || f.handles == null || f.handles.size() > MAX_HANDLES) return false;
        var tokens = new HashSet<Long>(); var uuids = new HashSet<UUID>(); var entities = new HashSet<Integer>();
        for (var h : f.handles) if (h == null || h.token <= 0 || h.needleEntity < 0 || h.needleUuid == null
                || h.needleUuid.equals(ZERO_UUID) || h.source == null || !h.source.finite() || h.anchor == null || !h.anchor.finite()
                || h.remainingTicks <= 0 || h.remainingTicks > MARK_LIFE_TICKS || !tokens.add(h.token)
                || !uuids.add(h.needleUuid) || !entities.add(h.needleEntity)) return false;
        if (f.phase == CLEARED || f.phase == REFUSED)
            return f.remainingTicks == 0 && (f.phase != REFUSED || !f.accepted);
        if (!f.accepted || f.source == null || f.anchor == null || f.needleEntity < 0
                || f.needleUuid == null || f.needleUuid.equals(ZERO_UUID)) return false;
        if (f.phase == RECOVERED) return f.remainingTicks == 0
                && f.handles.stream().noneMatch(h -> h.needleUuid.equals(f.needleUuid));
        return f.remainingTicks > 0 && f.handles.stream().anyMatch(h -> eventMatches(f, h));
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || !local && f.phase != RECOVERED || local && (f.id.epoch < cancelledEpoch
                || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        boolean same = old != null && sameController(old.frame.id, f.id);
        long highHandle = same ? old.highHandle : 0;
        if (old != null) {
            var before = old.frame;
            if (f.id.epoch < before.id.epoch || f.id.epoch == before.id.epoch && f.id.token < before.id.token) return false;
            if (f.id.epoch == before.id.epoch && f.id.token == before.id.token) {
                if (old.retired || !same || f.id.input < before.id.input || f.sequence <= before.sequence) return false;
                boolean added = false;
                for (var handle : f.handles) {
                    var prior = old.handles.get(handle.token);
                    if (prior == null) {
                        // One monotonic high-water value fences every removed or locally expired handle forever.
                        if (handle.token <= old.highHandle) return false;
                        added = true;
                    } else if (!sameNeedle(handle, prior) || handle.remainingTicks > prior.remainingTicks) return false;
                }
                if (added && (f.phase != MARKED || f.id.input <= before.id.input)) return false;
                if (f.phase == MARKED && f.id.input > before.id.input && !added) return false;
                if (f.phase == RECOVERED) {
                    var recovered = old.handles.values().stream().filter(h -> h.needleUuid.equals(f.needleUuid)).findFirst().orElse(null);
                    if (f.id.input <= before.id.input || recovered != null && !eventMatches(f, recovered)
                            || local && recovered == null) return false;
                }
            } else if (f.id.epoch == before.id.epoch && f.id.input <= before.id.input) return false;
        }
        for (var handle : f.handles) highHandle = Math.max(highHandle, handle.token);
        var fence = new Fence(f, highHandle);
        fences.put(f.id.owner, fence);
        if (local) {
            var previous = same && active != null && sameController(active.id, f.id)
                    ? new HashMap<>(leases) : Map.<Long, Integer>of();
            leases.clear();
            for (var handle : f.handles) leases.put(handle.token, Math.min(handle.remainingTicks,
                    previous.getOrDefault(handle.token, handle.remainingTicks)));
            active = leases.isEmpty() ? null : f; quiet = 0;
        }
        return true;
    }
    public Frame active() { return active; }
    /** Live handle order follows the authoritative snapshot, with independently clamped local clocks. */
    public List<Handle> handles() {
        if (active == null) return List.of();
        var result = new ArrayList<Handle>(MAX_HANDLES);
        for (var handle : active.handles) {
            int remaining = leases.getOrDefault(handle.token, 0);
            if (remaining > 0) result.add(new Handle(handle.token, handle.needleEntity, handle.needleUuid,
                    handle.source, handle.anchor, remaining));
        }
        return List.copyOf(result);
    }
    /** A thirty-tick silence hides cosmetics only; each original lease continues independently. */
    public boolean markerVisible() { return active != null && !leases.isEmpty() && quiet < HEARTBEAT_TIMEOUT_TICKS; }
    public int remainingTicks() { return leases.values().stream().mapToInt(Integer::intValue).max().orElse(0); }
    public int remainingTicks(long token) { return leases.getOrDefault(token, 0); }
    public int handleCount() { return leases.size(); }
    /** One compact label follows the aimed handle, then the latest acted handle, then snapshot order. */
    public Handle labelHandle(UUID aimedNeedle) {
        var live = handles();
        for (var handle : live) if (handle.needleUuid.equals(aimedNeedle)) return handle;
        for (var handle : live) if (handle.needleUuid.equals(active.needleUuid)) return handle;
        return live.isEmpty() ? null : live.getFirst();
    }
    public int watermarkCount() { return fences.size(); }
    public Identity tick(boolean paused, boolean validOwner) {
        if (active == null) return null;
        var id = active.id;
        if (!validOwner) { retire(id); return id; }
        if (!paused) {
            quiet++;
            leases.replaceAll((token, ticks) -> Math.max(0, ticks - 1));
            var fence = fences.get(id.owner);
            leases.entrySet().removeIf(entry -> {
                if (entry.getValue() > 0) return false;
                if (fence != null) fence.handles.remove(entry.getKey());
                return true;
            });
        }
        if (!leases.isEmpty()) return null;
        retire(id); return id;
    }
    public void retire(Identity id) {
        if (id == null) return;
        var old = fences.get(id.owner);
        if (old != null && sameController(old.frame.id, id)) { old.retired = true; old.handles.clear(); }
        if (active != null && sameController(active.id, id)) { active = null; quiet = 0; leases.clear(); }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
        if (active != null && active.id.epoch == epoch && active.id.input <= input) retire(active.id);
    }
    public void clear() { if (active != null) retire(active.id); }
    public void resetSession() { fences.clear(); active = null; quiet = 0; leases.clear(); cancelledEpoch = cancelledThrough = 0; }
    /** Subtract double world coordinates before narrowing a vertex to float. */
    public static Point relative(Point point, Point camera) {
        if (point == null || camera == null || !point.finite() || !camera.finite()) return null;
        return new Point(point.x() - camera.x(), point.y() - camera.y(), point.z() - camera.z());
    }
    /** Physical width is capped by depth as well as distance so a near label cannot fill the screen. */
    public static float labelScale(double depth, double distance, int pixels) {
        if (!Double.isFinite(depth) || !Double.isFinite(distance) || depth <= .35 || distance < 0 || pixels <= 0) return 0;
        return (float)Math.min(Math.min(.025, .009 + distance * .0003), depth * .36 / pixels);
    }
    private static boolean eventMatches(Frame frame, Handle handle) {
        return frame.needleEntity == handle.needleEntity && Objects.equals(frame.needleUuid, handle.needleUuid)
                && Objects.equals(frame.anchor, handle.anchor);
    }
    private static boolean sameNeedle(Handle a, Handle b) {
        return a.needleEntity == b.needleEntity && a.needleUuid.equals(b.needleUuid) && a.anchor.equals(b.anchor);
    }
    private static boolean sameController(Identity a, Identity b) {
        return a.entity == b.entity && a.owner.equals(b.owner) && a.epoch == b.epoch && a.token == b.token;
    }
}
