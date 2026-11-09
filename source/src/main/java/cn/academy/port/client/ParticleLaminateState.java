package cn.academy.port.client;

import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.highpower.core.ParticleLaminateGeometry;
import cn.academy.port.highpower.core.ParticleLaminateGeometry.Panel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** NEW241 receipt provenance and finite leases. A warning or preview never confirms damage. */
public final class ParticleLaminateState {
    public static final int ACTIVE = 0, EDIT = 1, FIRE = 2, ENDED = 3;
    public static final int PREPARED = 0, MISS = 1, BLOCKED = 2, REFUSED = 3, DAMAGED = 4, ABSORBED = 5, DEGENERATE = 6;
    public static final int MAX_CONTEXTS = 64, MAX_WATERMARKS = 1024, QUIET_TICKS = 12;
    public static final int ANCHOR_TICKS = 200, FIELD_TICKS = 100, HOLD_TICKS = 40, PULSES = 5, PULSE_INTERVAL = 20;
    public static final int LIVE_FLASH_TICKS = 2, FLASH_TICKS = 6, WARNING_TICKS = 6, WARMUP_TICKS = 6;
    private static final double EPS = 1e-7;
    private static final UUID ZERO_UUID = new UUID(0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Segment(Vector start, Vector end, int status) {}
    public record Frame(Identity id, long sequence, int phase, int status, int remainingTicks,
                        Vector ownerFeet, List<Panel> panels, Vector ghostCenter, Vector ghostNormal,
                        List<Segment> path, boolean lowMode, int pulseIndex, int fieldAge) {
        public Frame { if (panels != null) panels = List.copyOf(panels); if (path != null) path = List.copyOf(path); }
    }
    private record Fence(Frame frame, boolean retired) {}
    private final Map<UUID, Frame> active = new HashMap<>();
    private final Map<UUID, Fence> fences = new HashMap<>();
    private long cancelledEpoch, cancelledThrough;

    private static boolean point(Vector p) { return p != null && p.finite(); }
    public static boolean direction(Vector p) { return point(p) && Math.abs(p.length() - 1) <= 1e-10; }
    public static boolean eligible(int level, boolean parentLearned, boolean skillLearned) { return level >= 4 && level <= 5 && parentLearned && skillLearned; }
    public static boolean armed(Frame f) { return f != null && f.panels != null && f.panels.size() == 2 && f.phase != ENDED; }
    public static boolean hasGhost(Frame f) { return f != null && f.phase == EDIT && direction(f.ghostNormal); }
    public static Panel ghost(Frame f) { return hasGhost(f) ? new Panel(f.panels.size(), f.ghostCenter, f.ghostNormal) : null; }
    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(ZERO_UUID)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < ACTIVE || f.phase > ENDED || f.status < PREPARED || f.status > DEGENERATE
                || f.remainingTicks < 0 || f.remainingTicks > ANCHOR_TICKS || f.pulseIndex < 0 || f.pulseIndex > PULSES || f.fieldAge < 0 || f.fieldAge > FIELD_TICKS
                || !point(f.ownerFeet) || !point(f.ghostCenter) || !point(f.ghostNormal)
                || f.ghostNormal.length() != 0 && !direction(f.ghostNormal)
                || f.panels == null || f.panels.size() > 2 || f.path == null || f.path.size() > 3
                || f.phase != EDIT && f.ghostNormal.length() != 0 || f.phase == EDIT && f.panels.size() > 1
                || f.phase != ENDED && f.panels.size() < 2 && (f.pulseIndex != 0 || f.fieldAge != 0)
                || f.panels.size() == 2 && f.remainingTicks > FIELD_TICKS
                || f.phase == FIRE && (f.panels.size() != 2 || f.pulseIndex < 1)
                || f.phase != FIRE && (f.status == DAMAGED || f.status == ABSORBED)
                || f.phase == ENDED && (!f.path.isEmpty() || f.remainingTicks != 0)) return false;
        for (int i = 0; i < f.panels.size(); i++) {
            Panel p = f.panels.get(i);
            if (p == null || p.index() != i || !point(p.center()) || !direction(p.normal())) return false;
        }
        try { if (hasGhost(f)) ghost(f);
            if (f.panels.size() == 2 && ParticleLaminateGeometry.segments(f.panels.getFirst(), f.panels.getLast()).isEmpty()) return false;
        } catch (IllegalArgumentException malformed) { return false; }
        if (f.path.isEmpty()) return true;
        Panel first = f.panels.isEmpty() ? null : f.panels.getFirst();
        Panel second = f.panels.size() == 2 ? f.panels.getLast() : hasGhost(f) ? ghost(f) : null;
        if (first == null || second == null) return false;
        var planned = ParticleLaminateGeometry.segments(first, second).orElse(List.of());
        if (planned.size() != 3) return false;
        Set<Integer> used = new HashSet<>();
        for (Segment s : f.path) {
            if (s == null || !point(s.start) || !point(s.end) || s.status < PREPARED || s.status > DEGENERATE
                    || f.phase != FIRE && (s.status == DAMAGED || s.status == ABSORBED)) return false;
            double length = s.end.subtract(s.start).length();
            if (length > 64 + EPS || length == 0 && s.status != BLOCKED && s.status != REFUSED && s.status != DAMAGED && s.status != ABSORBED) return false;
            int match = -1;
            for (int i = 0; i < planned.size(); i++) {
                var expected = planned.get(i); Vector delta = expected.end().subtract(expected.start());
                double fullLength = delta.length();
                if (s.start.subtract(expected.start()).length() > EPS || !(fullLength > 0)) continue;
                Vector axis = delta.scale(1 / fullLength), actual = s.end.subtract(s.start);
                double along = actual.dot(axis);
                if (along < 0 || along > fullLength + EPS || actual.subtract(axis.scale(along)).length() > EPS) return false;
                match = i; break;
            }
            if (match < 0 || !used.add(match)) return false;
        }
        return true;
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
                        || f.id.input < old.id.input || f.sequence <= old.sequence || f.pulseIndex < old.pulseIndex || f.fieldAge < old.fieldAge
                        || old.panels.size() == f.panels.size() && f.remainingTicks > old.remainingTicks) return false;
                if (f.phase != ENDED) {
                    if (f.panels.size() < old.panels.size() || armed(old) && !armed(f)) return false;
                    for (int i = 0; i < old.panels.size(); i++) if (!f.panels.get(i).equals(old.panels.get(i))) return false;
                    if (armed(old) && f.lowMode != old.lowMode) return false;
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
    public void clear() { for (Frame f : List.copyOf(active.values())) retire(f.id); }
    public void resetSession() { active.clear(); fences.clear(); cancelledEpoch = cancelledThrough = 0; }
    public Frame current(UUID owner) { return active.get(owner); }
    public int size() { return active.size(); }
    public int watermarkCount() { return fences.size(); }
    public static boolean recallAvailable(Frame f, int remaining, int quiet) {
        return valid(f) && armed(f) && remaining > 0 && remaining <= FIELD_TICKS && quiet >= 0 && quiet < QUIET_TICKS;
    }
    /** A countdown decorates edges only. It cannot construct a FIRE receipt, beam or hit glint. */
    public static boolean warning(Frame f, int remaining, int fieldAge) {
        if (!armed(f) || f.pulseIndex >= PULSES || remaining <= 0) return false;
        int until = WARMUP_TICKS + f.pulseIndex * PULSE_INTERVAL - fieldAge;
        return until > 0 && until <= WARNING_TICKS;
    }
    public static String status(Frame f, int remaining, int fieldAge) {
        return switch (f.status) {
            case MISS -> "miss"; case BLOCKED -> "blocked"; case REFUSED -> "refused";
            case DAMAGED -> "damaged"; case ABSORBED -> "absorbed"; case DEGENERATE -> "degenerate";
            default -> f.phase == ENDED ? "ended" : f.phase == EDIT ? "adjusting" : armed(f) ? warning(f, remaining, fieldAge) ? "warning" : "armed" : "prepared";
        };
    }
    /** First release starts the anchor lease; second release starts a shorter non-renewable field lease. */
    public static final class Lease {
        private int remaining, quiet, totalAge, holdAge, fieldAge;
        private boolean received, anchored, paid, ended;
        public void received(Frame f) {
            boolean nowAnchored = !f.panels.isEmpty(), nowPaid = f.panels.size() == 2;
            remaining = !received || !anchored && nowAnchored || !paid && nowPaid ? f.remainingTicks : Math.min(remaining, f.remainingTicks);
            if (!received || !anchored && nowAnchored) totalAge = 0;
            if (nowPaid) remaining = Math.min(remaining, FIELD_TICKS);
            fieldAge = Math.max(fieldAge, f.fieldAge);
            quiet = 0; received = true; anchored |= nowAnchored; paid |= nowPaid; ended = f.phase == ENDED;
        }
        public boolean tick(boolean paused, boolean valid) {
            if (!valid || ended || !received) return false;
            if (!paused) { remaining = Math.max(0, remaining - 1); quiet = Math.min(QUIET_TICKS, quiet + 1); totalAge++; if (paid) fieldAge = Math.min(FIELD_TICKS, fieldAge + 1); if (!anchored) holdAge++; }
            return remaining > 0 && quiet < QUIET_TICKS && (anchored ? totalAge < ANCHOR_TICKS : holdAge < HOLD_TICKS);
        }
        public int remaining() { return remaining; }
        public int quiet() { return quiet; }
        public int fieldAge() { return fieldAge; }
    }
}
