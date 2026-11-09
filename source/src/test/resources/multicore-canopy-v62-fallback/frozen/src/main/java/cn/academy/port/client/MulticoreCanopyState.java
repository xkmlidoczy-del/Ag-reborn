package cn.academy.port.client;

import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Engine-free NEW251 receipt fences. Camera movement can never alter a core or reveal a hidden target. */
public final class MulticoreCanopyState {
    public static final int DEPLOY = 0, ARMED = 1, WARNING = 2, HELD = 3, FIRE = 4, ENDED = 5;
    public static final int PREPARED = 0, MISS = 1, BLOCKED = 2, REFUSED = 3, DAMAGED = 4, ABSORBED = 5, LOST = 6;
    public static final int MAX_CORES = 6, MAX_TARGETS = 16, MAX_CONTEXTS = 64, MAX_WATERMARKS = 1024;
    public static final int LIFE_TICKS = 240, QUIET_TICKS = 12, FLASH_TICKS = 4, LOST_TICKS = 10;
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Core(int id, Vector center, boolean available) {}
    public record Target(int id, Vector point, int status) {}
    public record Segment(int coreId, int slot, Vector start, Vector end, int status) {}
    public record Frame(Identity id, long sequence, int phase, int status, int remainingTicks, Vector ownerFeet,
                        List<Core> cores, List<Target> targets, List<Segment> path, boolean held, int shotIndex, int warningTicks) {
        public Frame {
            if (cores != null) cores = List.copyOf(cores);
            if (targets != null) targets = List.copyOf(targets);
            if (path != null) path = List.copyOf(path);
        }
    }
    public record Status(int count, int available, int seconds, String key, boolean confirmedDamage) {}
    private record Fence(Frame frame, boolean retired, Set<Integer> roster, boolean rosterFrozen) {}
    private static final UUID ZERO_UUID = new UUID(0, 0);
    private final Map<UUID, Frame> active = new HashMap<>();
    private final Map<UUID, Fence> fences = new HashMap<>();
    private long cancelledEpoch, cancelledThrough;
    private static boolean point(Vector p) { return p != null && p.finite(); }
    public static boolean direction(Vector p) { return point(p) && Math.abs(p.length() - 1) <= 1e-10; }
    public static boolean eligible(int level, boolean parentLearned, boolean skillLearned) {
        return level == 5 && parentLearned && skillLearned;
    }
    public static boolean controlAvailable(Frame frame, int remaining, int quiet) {
        return valid(frame) && frame.phase == ARMED && frame.cores.stream().anyMatch(Core::available)
                && remaining > 0 && quiet >= 0 && quiet < QUIET_TICKS;
    }
    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || ZERO_UUID.equals(f.id.owner)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < DEPLOY || f.phase > ENDED || f.status < PREPARED || f.status > LOST
                || f.remainingTicks < 0 || f.remainingTicks > LIFE_TICKS || !point(f.ownerFeet)
                || f.cores == null || f.cores.size() > MAX_CORES || f.targets == null || f.targets.size() > MAX_TARGETS
                || f.path == null || f.path.size() > MAX_CORES || f.shotIndex < 0 || f.shotIndex > MAX_TARGETS
                || f.warningTicks < 0 || f.warningTicks > 12
                || f.phase != FIRE && !f.path.isEmpty()
                || f.phase == ENDED && (f.remainingTicks != 0 || !f.path.isEmpty())) return false;
        Set<Integer> coreIds = new HashSet<>(), targetIds = new HashSet<>(), emissions = new HashSet<>();
        int previous = -1;
        for (Core c : f.cores) {
            if (c == null || c.id < 0 || c.id >= MAX_CORES || c.id <= previous || !point(c.center) || !coreIds.add(c.id)) return false;
            previous = c.id;
        }
        for (Target t : f.targets) {
            if (t == null || t.id < 0 || !point(t.point) || t.status < PREPARED || t.status > LOST || !targetIds.add(t.id)) return false;
        }
        for (Segment s : f.path) {
            if (s == null || !coreIds.contains(s.coreId) || !emissions.add(s.coreId) || s.slot < 0 || s.slot >= MAX_TARGETS
                    || !point(s.start) || !point(s.end) || s.status < MISS || s.status > ABSORBED) return false;
            Core core = f.cores.stream().filter(c -> c.id == s.coreId).findFirst().orElseThrow();
            double length = s.end.subtract(s.start).length();
            if (!s.start.equals(core.center) || !(length > 0) || length > 96 + 1e-8) return false;
        }
        return true;
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || local && (f.id.epoch < cancelledEpoch || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence fence = fences.get(f.id.owner);
        if (fence == null && fences.size() >= MAX_WATERMARKS) return false;
        Set<Integer> roster = new HashSet<>();
        boolean rosterFrozen = false;
        if (fence != null) {
            Frame old = fence.frame;
            if (f.id.epoch < old.id.epoch || f.id.token < old.id.token) return false;
            if (f.id.token == old.id.token) {
                if (fence.retired || f.id.entity != old.id.entity || f.id.epoch != old.id.epoch
                        || f.id.input < old.id.input || f.sequence <= old.sequence || f.remainingTicks > old.remainingTicks) return false;
                roster.addAll(fence.roster); rosterFrozen = fence.rosterFrozen;
                if (f.phase != ENDED) {
                    if (f.phase == DEPLOY && old.phase != DEPLOY || f.phase == ARMED && old.phase > ARMED
                            || f.phase == WARNING && old.phase > WARNING) return false;
                    if (f.cores.size() < old.cores.size() || old.phase != DEPLOY && f.cores.size() != old.cores.size()) return false;
                    for (Core c : old.cores) {
                        Core next = f.cores.stream().filter(n -> n.id == c.id).findFirst().orElse(null);
                        if (next == null || !next.center.equals(c.center) || !c.available && next.available) return false;
                    }
                    for (Target t : f.targets) {
                        if (rosterFrozen && (!roster.contains(t.id) || old.targets.stream().noneMatch(n -> n.id == t.id))) return false;
                        if (t.status == LOST) {
                            Target before = old.targets.stream().filter(n -> n.id == t.id).findFirst().orElse(null);
                            if (before == null || !before.point.equals(t.point)) return false;
                        }
                    }
                }
            } else if (f.id.epoch == old.id.epoch && f.id.input <= old.id.input) return false;
        }
        if (f.phase != ENDED && !active.containsKey(f.id.owner) && active.size() >= MAX_CONTEXTS) return false;
        if (!rosterFrozen) for (Target t : f.targets) roster.add(t.id);
        rosterFrozen |= f.phase >= WARNING;
        fences.put(f.id.owner, new Fence(f, f.phase == ENDED, Set.copyOf(roster), rosterFrozen));
        if (f.phase == ENDED) active.remove(f.id.owner); else active.put(f.id.owner, f);
        return true;
    }
    public void retire(Identity id) {
        if (id == null) return;
        Fence f = fences.get(id.owner);
        if (f != null && f.frame.id.token == id.token && f.frame.id.epoch == id.epoch) {
            fences.put(id.owner, new Fence(f.frame, true, f.roster, f.rosterFrozen)); active.remove(id.owner);
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
    public static Status status(Frame f, int remaining) {
        String key = switch (f.phase) {
            case DEPLOY -> "deploying"; case ARMED -> "armed"; case WARNING -> "warning";
            case HELD -> "sequential"; case FIRE -> f.held ? "sequential" : "volley"; default -> "ended";
        };
        return new Status(f.cores.size(), (int)f.cores.stream().filter(Core::available).count(),
                (Math.max(0, Math.min(LIFE_TICKS, remaining)) + 19) / 20, key,
                f.phase == FIRE && f.path.stream().anyMatch(s -> s.status == DAMAGED));
    }
    /** Packet refreshes may shorten a lease, never restart it. Paused clients do not advance cosmetic time. */
    public static final class Lease {
        private int remaining, quiet;
        private boolean received, ended;
        public void received(Frame f) {
            remaining = received ? Math.min(remaining, f.remainingTicks) : f.remainingTicks;
            received = true; ended = f.phase == ENDED; quiet = 0;
        }
        public boolean tick(boolean paused, boolean valid) {
            if (!valid || ended || !received) return false;
            if (!paused) { if (remaining > 0) remaining--; quiet = Math.min(QUIET_TICKS, quiet + 1); }
            return quiet < QUIET_TICKS && remaining > 0;
        }
        public int remaining() { return remaining; }
        public int quiet() { return quiet; }
    }
    /** Defensive client retirement; repeated LOST receipts never refresh a hidden target's ten-tick grace. */
    public static final class Visibility {
        private final Map<Integer, Integer> lost = new HashMap<>();
        public void received(Frame f) {
            Set<Integer> present = new HashSet<>();
            for (Target t : f.targets) {
                present.add(t.id);
                if (t.status == LOST) lost.putIfAbsent(t.id, 0); else lost.remove(t.id);
            }
            lost.keySet().retainAll(present);
        }
        public void tick(boolean paused) { if (!paused) lost.replaceAll((id, age) -> Math.min(LOST_TICKS, age + 1)); }
        public boolean shown(Target target) { return target.status != LOST || lost.getOrDefault(target.id, LOST_TICKS) < LOST_TICKS; }
    }
}
