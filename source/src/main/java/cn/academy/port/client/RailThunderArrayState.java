package cn.academy.port.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Engine-free server receipt gate. Preparatory input may advance within one immutable cast token. */
public final class RailThunderArrayState {
    public static final int PREPARING = 0, ARMED = 1, TAP = 2, HOLD = 3, ENDED = 4;
    public static final int PREVIEW = 0, MISS = 1, HIT = 2, REFUSED = 3, BLOCKED = 4;
    public static final int MAX_CONTEXTS = 64, MAX_WATERMARKS = 1024, QUIET_TICKS = 12, MAX_AGE = 268, FLASH_TICKS = 5;
    public static final UUID NO_TARGET = new UUID(0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Segment(int axis, int entity, UUID target, RailThunderArrayGeometry.Point from, RailThunderArrayGeometry.Point to, int status) {}
    public record Frame(Identity id, long sequence, int phase, int age, RailThunderArrayGeometry.Point origin,
                        List<RailThunderArrayGeometry.Point> axes, List<Segment> segments, int reason) {}
    private record Fence(Frame frame, boolean retired) {}
    private final Map<UUID, Frame> active = new HashMap<>();
    private final Map<UUID, Fence> fences = new HashMap<>();
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(NO_TARGET)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < PREPARING || f.phase > ENDED || f.age < 0 || f.age > MAX_AGE
                || f.phase == PREPARING && f.age > 200 || f.phase == ARMED && f.age > 208
                || f.reason < 0 || f.reason > 4 || f.phase != ENDED && f.reason != 0 && !(f.phase == PREPARING && f.reason == 1)
                || !RailThunderArrayGeometry.validPoint(f.origin) || f.axes == null || f.axes.size() > RailThunderArrayGeometry.MAX_AXES
                || f.phase >= ARMED && f.phase < ENDED && f.axes.size() != 3
                || f.segments == null || f.segments.size() > RailThunderArrayGeometry.MAX_SEGMENTS
                || f.phase == ENDED && !f.segments.isEmpty()) return false;
        for (var axis : f.axes) if (!RailThunderArrayGeometry.validDirection(axis)) return false;
        for (Segment segment : f.segments) {
            if (segment == null || segment.axis < 0 || segment.axis >= f.axes.size() || segment.target == null
                    || segment.status < PREVIEW || segment.status > BLOCKED
                    || segment.entity < -1 || (segment.entity == -1) != segment.target.equals(NO_TARGET)
                    || segment.target.equals(f.id.owner) || segment.entity == f.id.entity
                    || segment.status == HIT && segment.entity < 0
                    || f.phase <= ARMED && segment.status != PREVIEW || f.phase >= TAP && segment.status == PREVIEW
                    || !RailThunderArrayGeometry.validEndpoints(segment.from, segment.to)
                    || !RailThunderArrayGeometry.validEndpoints(f.origin, segment.from)
                    || !RailThunderArrayGeometry.validEndpoints(f.origin, segment.to)) return false;
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
                if (fence.retired || f.id.entity != old.id.entity || f.id.epoch != old.id.epoch || f.id.input < old.id.input
                        || f.sequence <= old.sequence || f.age < old.age || f.phase < old.phase
                        || old.phase == TAP && f.phase == HOLD
                        || old.phase >= ARMED && f.id.input != old.id.input
                        || f.axes.size() < old.axes.size()) return false;
                for (int i = 0; i < old.axes.size(); i++) if (!old.axes.get(i).equals(f.axes.get(i))) return false;
                if (old.phase >= TAP && !old.origin.equals(f.origin)) return false;
            } else if (f.id.epoch == old.id.epoch && f.id.input <= old.id.input) return false;
        }
        if (f.phase == ENDED) { fences.put(f.id.owner, new Fence(f, true)); active.remove(f.id.owner); return true; }
        if (!active.containsKey(f.id.owner) && active.size() >= MAX_CONTEXTS) return false;
        fences.put(f.id.owner, new Fence(f, false)); active.put(f.id.owner, f); return true;
    }
    public void retire(Identity id) {
        if (id == null) return; Fence old = fences.get(id.owner);
        if (old != null && old.frame.id.token == id.token && old.frame.id.epoch == id.epoch) {
            fences.put(id.owner, new Fence(old.frame, true)); active.remove(id.owner);
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
    public void clear() { for (Frame frame : List.copyOf(active.values())) retire(frame.id); }
    public void resetSession() { active.clear(); fences.clear(); cancelledEpoch = 0; cancelledThrough = 0; }

    /** This is the renderer's clock, with bounded age and invalidation even while a GUI pauses the game. */
    public static final class Lease {
        private int age, quiet, terminalAge = -1;
        public Lease(int serverAge) { age = Math.max(0, Math.min(MAX_AGE, serverAge)); }
        public void received(int serverAge, boolean terminal) {
            age = Math.max(age, Math.min(MAX_AGE, serverAge)); quiet = 0;
            if (terminal && terminalAge < 0) terminalAge = 0;
        }
        public boolean tick(boolean paused, boolean valid) {
            if (!valid) return false;
            if (paused) return true;
            quiet = Math.min(QUIET_TICKS, quiet + 1); age = Math.min(MAX_AGE + 1, age + 1);
            if (terminalAge >= 0) return ++terminalAge < FLASH_TICKS;
            return quiet < QUIET_TICKS && age <= MAX_AGE;
        }
        public int age() { return age; }
        public int quiet() { return quiet; }
        public int terminalAge() { return terminalAge; }
    }
}
