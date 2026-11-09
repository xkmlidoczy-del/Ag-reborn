package cn.academy.port.client;

import cn.academy.port.highpower.core.DissipationCurtainGeometry;
import cn.academy.port.highpower.core.DissipationCurtainGeometry.Vector;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** NEW242 authoritative warning/live receipts, bounded replay fences and purely cosmetic retirement. */
public final class DissipationCurtainState {
    public static final int ACTIVE = 0, HIT = 1, ENDED = 2, STARTUP = 3;
    public static final int MAX_CONTEXTS = 64, MAX_WATERMARKS = 1024, QUIET_TICKS = 12;
    public static final int STARTUP_TICKS = 6, HIT_TICKS = 10, FADE_TICKS = 4;
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, Vector center, Vector axis,
                        int liveMask, int plate, Vector point) {}
    private record Fence(Frame frame, boolean retired, boolean live) {}
    private final Map<UUID, Frame> active = new HashMap<>();
    private final Map<UUID, Fence> fences = new HashMap<>();
    private long cancelledEpoch, cancelledThrough;

    public static boolean eligible(int level, boolean parentLearned, boolean learned) {
        return level >= 4 && level <= 5 && parentLearned && learned;
    }
    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(new UUID(0, 0))
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < ACTIVE || f.phase > STARTUP || !DissipationCurtainMesh.validPoint(f.center)
                || !DissipationCurtainMesh.validDirection(f.axis) || !DissipationCurtainMesh.validPoint(f.point)
                || f.liveMask != (f.phase == ACTIVE || f.phase == HIT ? 7 : 0)) return false;
        if (f.phase != HIT) return f.plate == -1 && f.point.subtract(f.center).length() <= 1e-7;
        return f.plate >= 0 && f.plate < 3 && DissipationCurtainMesh.onFacet(
                DissipationCurtainGeometry.pose(f.center, f.axis), f.plate, f.point);
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || local && (f.id.epoch < cancelledEpoch || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        if (old != null) {
            int order = compare(f.id, old.frame.id);
            if (order < 0 || order == 0 && (!f.id.equals(old.frame.id) || old.retired || f.sequence <= old.frame.sequence)
                    || order > 0 && f.id.token <= old.frame.id.token || order == 0 && old.live && f.phase == STARTUP) return false;
        }
        if (f.phase != ENDED && !active.containsKey(f.id.owner) && active.size() >= MAX_CONTEXTS) return false;
        boolean live = f.phase == ACTIVE || f.phase == HIT || old != null && old.frame.id.equals(f.id) && old.live;
        fences.put(f.id.owner, new Fence(f, f.phase == ENDED, live));
        if (f.phase == ENDED) active.remove(f.id.owner); else active.put(f.id.owner, f);
        return true;
    }
    private static int compare(Identity a, Identity b) {
        int epoch = Long.compare(a.epoch, b.epoch); return epoch != 0 ? epoch : Long.compare(a.input, b.input);
    }
    public void retire(Identity id) {
        if (id == null) return;
        Fence old = fences.get(id.owner);
        if (old != null && old.frame.id.equals(id)) {
            fences.put(id.owner, new Fence(old.frame, true, old.live)); active.remove(id.owner);
        }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
    }
    public Frame current(UUID owner) { return active.get(owner); }
    public int size() { return active.size(); }
    public int watermarkCount() { return fences.size(); }
    public void clear() { for (Frame f : List.copyOf(active.values())) retire(f.id); }
    public void resetSession() { active.clear(); fences.clear(); cancelledEpoch = cancelledThrough = 0; }

    /** An event retains its contact pose; subsequent movement/keepalives cannot move or renew its ripple. */
    public static final class Contacts {
        private final Frame[] frames = new Frame[3];
        private final int[] ages = {HIT_TICKS, HIT_TICKS, HIT_TICKS};
        public void received(Frame f) {
            if (!valid(f) || f.phase != HIT) return;
            Frame previous = frames[f.plate];
            if (previous != null && previous.id.equals(f.id) && previous.sequence >= f.sequence) return;
            frames[f.plate] = f; ages[f.plate] = 0;
        }
        public void tick(boolean paused) { if (!paused) for (int i = 0; i < 3; i++) ages[i] = Math.min(HIT_TICKS, ages[i] + 1); }
        public Frame frame(int facet) { return facet >= 0 && facet < 3 && ages[facet] < HIT_TICKS ? frames[facet] : null; }
        public int age(int facet) { return facet >= 0 && facet < 3 ? ages[facet] : HIT_TICKS; }
        public void clear() { for (int i = 0; i < 3; i++) { frames[i] = null; ages[i] = HIT_TICKS; } }
    }
    public static final class Lease {
        private int quiet, fade = FADE_TICKS;
        private boolean ending;
        public void received(Frame f) { if (!valid(f) || ending) return; quiet = 0; if (f.phase == ENDED) end(); }
        public void end() { if (!ending) { ending = true; fade = 0; } }
        public boolean tick(boolean paused, boolean validOwner) {
            if (!validOwner) return false;
            if (!paused) { if (ending) fade = Math.min(FADE_TICKS, fade + 1); else if (++quiet >= QUIET_TICKS) end(); }
            return !ending || fade < FADE_TICKS;
        }
        public boolean ending() { return ending; }
        public boolean live() { return !ending && quiet < QUIET_TICKS; }
        public int quiet() { return quiet; }
        public int fadeAge() { return fade; }
    }
}
