package cn.academy.port.client;

import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Plate;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Engine-free NEW221 provenance, lifetime and presentation gate. A preview never confirms a hit. */
public final class FoldedMirrorState {
    public static final int ACTIVE = 0, EDIT = 1, FIRE = 2, ENDED = 3;
    public static final int PREPARED = 0, MISS = 1, BLOCKED = 2, REFUSED = 3, DAMAGED = 4, ABSORBED = 5, DEGENERATE = 6;
    public static final int MAX_CONTEXTS = 64, MAX_WATERMARKS = 1024, QUIET_TICKS = 12, LIFE_TICKS = 400, EDIT_TICKS = 100, FLASH_TICKS = 6;
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Segment(Vector start, Vector end) {}
    public record Frame(Identity id, long sequence, int phase, int status, int remainingTicks,
                        Vector ownerFeet, List<Plate> plates, Vector ghostCenter, Vector ghostNormal, List<Segment> path) {
        public Frame { if (plates != null) plates = List.copyOf(plates); if (path != null) path = List.copyOf(path); }
    }
    public record Status(int count, int seconds, String key, boolean confirmedDamage) {}
    private record Fence(Frame frame, boolean retired) {}
    private final Map<UUID, Frame> active = new HashMap<>();
    private final Map<UUID, Fence> fences = new HashMap<>();
    private long cancelledEpoch, cancelledThrough;
    private static final UUID ZERO_UUID = new UUID(0, 0);
    private static final double EPS = 1e-7;

    private static boolean point(Vector p) { return p != null && p.finite(); }
    public static boolean direction(Vector p) { return point(p) && Math.abs(p.length() - 1) <= 1e-10; }
    public static boolean hasGhost(Frame f) { return f != null && f.phase == EDIT && direction(f.ghostNormal); }
    /** LV2 is usable immediately after NEW211. LV4-only eligibility would silently hide this whole skill. */
    public static boolean eligible(int level, boolean parentLearned, boolean skillLearned) {
        return level >= 2 && level <= 5 && parentLearned && skillLearned;
    }
    /** Only a currently leased paid array may justify opening its free recall input during cooldown. */
    public static boolean recallAvailable(Frame frame, int remaining, int quiet) {
        return valid(frame) && frame.phase != ENDED && !frame.plates.isEmpty()
                && remaining > 0 && remaining <= LIFE_TICKS && quiet >= 0 && quiet < QUIET_TICKS;
    }
    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(ZERO_UUID)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < ACTIVE || f.phase > ENDED || f.status < PREPARED || f.status > DEGENERATE
                || f.remainingTicks < 0 || f.remainingTicks > LIFE_TICKS
                || !point(f.ownerFeet) || !point(f.ghostCenter) || !point(f.ghostNormal)
                || f.ghostNormal.length() != 0 && !direction(f.ghostNormal)
                || f.plates == null || f.plates.size() > FoldedMirrorGeometry.MAX_REFLECTIONS
                || f.path == null || f.path.size() > FoldedMirrorGeometry.MAX_SEGMENTS
                || f.phase != FIRE && f.phase != ENDED && (f.status == DAMAGED || f.status == ABSORBED)
                || f.phase == FIRE && f.plates.isEmpty()
                || f.phase == ENDED && (!f.path.isEmpty() || f.remainingTicks != 0)
                || f.phase != EDIT && f.ghostNormal.length() != 0) return false;
        if (hasGhost(f)) {
            try { new Plate(0, f.ghostCenter, f.ghostNormal); } catch (IllegalArgumentException invalidSlab) { return false; }
        }
        for (int i = 0; i < f.plates.size(); i++) {
            Plate p = f.plates.get(i);
            if (p == null || p.index() != i || !point(p.center()) || !direction(p.normal())) return false;
        }
        double length = 0;
        for (int i = 0; i < f.path.size(); i++) {
            Segment s = f.path.get(i);
            if (s == null || !point(s.start) || !point(s.end)) return false;
            double distance = s.end.subtract(s.start).length();
            if (!(distance > 0) || !Double.isFinite(distance)) return false;
            length += distance;
            if (length > FoldedMirrorGeometry.MAX_LENGTH + 1e-8) return false;
            if (i > 0 && f.path.get(i - 1).end.subtract(s.start).length() > EPS) return false;
            if (i > 0 && f.phase == FIRE && reflectionPlate(f.plates, f.path.get(i - 1), s) == null) return false;
        }
        return true;
    }
    /** An arc belongs to an actual specular crossing on one of the receipt's finite physical plates. */
    public static Plate reflectionPlate(List<Plate> plates, Segment before, Segment after) {
        if (plates == null || before == null || after == null || !point(before.start) || !point(before.end)
                || !point(after.start) || !point(after.end) || before.end.subtract(after.start).length() > EPS) return null;
        double length = before.end.subtract(before.start).length();
        var incoming = FoldedMirrorGeometry.unit(before.end.subtract(before.start));
        var outgoing = FoldedMirrorGeometry.unit(after.end.subtract(after.start));
        if (incoming.isEmpty() || outgoing.isEmpty() || length > FoldedMirrorGeometry.MAX_LENGTH) return null;
        for (Plate plate : plates) {
            var hit = FoldedMirrorGeometry.contact(plate, before.start, incoming.get(), Math.min(FoldedMirrorGeometry.MAX_LENGTH, length + EPS));
            if (hit.isPresent() && hit.get().reflects() && hit.get().point().subtract(before.end).length() <= EPS
                    && hit.get().reflectedDirection().subtract(outgoing.get()).length() <= EPS) return plate;
        }
        return null;
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || local && (f.id.epoch < cancelledEpoch || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence fence = fences.get(f.id.owner);
        if (fence == null && fences.size() >= MAX_WATERMARKS) return false;
        if (fence != null) {
            Frame old = fence.frame;
            if (f.id.epoch < old.id.epoch || f.id.token < old.id.token) return false;
            if (f.id.token == old.id.token) {
                if (fence.retired || f.id.entity != old.id.entity || f.id.epoch != old.id.epoch
                        || f.id.input < old.id.input || f.sequence <= old.sequence
                        || !old.plates.isEmpty() && f.remainingTicks > old.remainingTicks) return false;
                if (f.phase != ENDED) {
                    if (f.plates.size() < old.plates.size()) return false;
                    for (int i = 0; i < old.plates.size(); i++) {
                        if (!old.plates.get(i).center().equals(f.plates.get(i).center())) return false;
                        if (!old.plates.get(i).normal().equals(f.plates.get(i).normal()) && old.phase != EDIT) return false;
                    }
                }
            } else if (f.id.epoch == old.id.epoch && f.id.input <= old.id.input) return false;
        }
        if (f.phase != ENDED && !active.containsKey(f.id.owner) && active.size() >= MAX_CONTEXTS) return false;
        fences.put(f.id.owner, new Fence(f, f.phase == ENDED));
        if (f.phase == ENDED) active.remove(f.id.owner); else active.put(f.id.owner, f);
        return true;
    }
    public void retire(Identity id) {
        if (id == null) return; Fence fence = fences.get(id.owner);
        if (fence != null && fence.frame.id.token == id.token && fence.frame.id.epoch == id.epoch) {
            fences.put(id.owner, new Fence(fence.frame, true)); active.remove(id.owner);
        }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
    }
    public void clear() { for (Frame frame : List.copyOf(active.values())) retire(frame.id); }
    public void resetSession() { active.clear(); fences.clear(); cancelledEpoch = cancelledThrough = 0; }
    public Frame current(UUID owner) { return active.get(owner); }
    public int size() { return active.size(); }
    public int watermarkCount() { return fences.size(); }
    public static Status status(Frame f, int remaining) {
        String key = switch (f.status) {
            case DEGENERATE -> "degenerate"; case REFUSED -> "refused"; case BLOCKED -> "blocked";
            case DAMAGED -> "damaged"; case ABSORBED -> "absorbed"; case MISS -> "miss";
            default -> f.phase == ENDED ? "ended" : f.phase == EDIT ? "adjusting" : "prepared";
        };
        return new Status(f.plates.size(), f.plates.isEmpty() ? -1 : (Math.max(0, Math.min(LIFE_TICKS, remaining)) + 19) / 20,
                key, f.phase == FIRE && f.status == DAMAGED);
    }
    /** Remaining lifetime can initialize on first paid placement, then can only decrease. */
    public static final class Lease {
        private int remaining, quiet, editAge;
        private boolean received, paid, ended;
        public void received(Frame f) {
            quiet = 0;
            boolean nowPaid = !f.plates.isEmpty();
            remaining = !received || !paid && nowPaid ? f.remainingTicks : Math.min(remaining, f.remainingTicks);
            paid |= nowPaid; received = true; ended = f.phase == ENDED;
        }
        public boolean tick(boolean paused, boolean valid) {
            if (!valid || ended) return false;
            if (!paused) { if (remaining > 0) remaining--; quiet = Math.min(QUIET_TICKS, quiet + 1); if (!paid) editAge++; }
            return quiet < QUIET_TICKS && (paid ? remaining > 0 : editAge < EDIT_TICKS);
        }
        public int remaining() { return remaining; }
        public int quiet() { return quiet; }
    }
}
