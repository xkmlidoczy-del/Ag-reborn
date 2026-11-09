package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** NEW413 bounded receipt state. Input never manufactures a braking or ground-contact success. */
public final class BrakingTouchState {
    public static final int ARMED = 0, BRAKED = 1, LANDED = 2, ENDED = 3;
    public static final int MAX_CONTEXTS = 64, MAX_WATERMARKS = 1024, ARM_TICKS = 40, LIFE_TICKS = 12;
    private static final UUID ZERO_UUID = new UUID(0, 0);
    private static final Point ZERO = new Point(0, 0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, Point point, Point before, Point after, long expiresAt) {}
    public record Controller(Frame frame, boolean local, int age) { public Identity id() { return frame.id; } }
    private record Fence(Frame frame, boolean retired) {}
    private final Map<UUID, Controller> active = new HashMap<>();
    private final Map<UUID, Fence> fences = new HashMap<>();
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(ZERO_UUID)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < ARMED || f.phase > ENDED || !BrakingTouchGeometry.validPoint(f.point)
                || !velocity(f.before) || !velocity(f.after) || f.expiresAt < 0) return false;
        if (f.phase == ARMED || f.phase == ENDED) return f.before.equals(ZERO) && f.after.equals(ZERO);
        double before = lengthSquared(f.before), after = lengthSquared(f.after);
        // LANDED carries native downward displacement and unchanged stored velocity, not comparable magnitudes.
        return f.phase == BRAKED ? before > 1e-12 && after <= 1e-12 : f.before.y() < 0;
    }
    private static boolean velocity(Point p) { return p != null && p.finite() && lengthSquared(p) <= 1_000_000; }
    private static double lengthSquared(Point p) { return p.x() * p.x() + p.y() * p.y() + p.z() * p.z(); }
    public boolean accept(Frame f, boolean local, long now) {
        if (!valid(f) || now < 0 || local && (f.id.epoch < cancelledEpoch
                || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        int lifetime = f.phase == ARMED ? ARM_TICKS : LIFE_TICKS;
        if (f.phase != ENDED && (f.expiresAt <= now || f.expiresAt - now > lifetime)) return false;
        Fence old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        boolean same = old != null && old.frame.id.equals(f.id);
        if (old != null) {
            int order = compare(f.id, old.frame.id);
            if (order < 0 || order == 0 && (!same || old.retired || f.sequence <= old.frame.sequence)
                    || order > 0 && f.id.token <= old.frame.id.token) return false;
            if (same && f.phase != ENDED) {
                if (old.frame.phase == BRAKED || old.frame.phase == LANDED || f.phase == BRAKED
                        || old.frame.phase == ARMED && f.phase == ARMED && f.expiresAt != old.frame.expiresAt) return false;
            }
        }
        if (f.phase != ENDED && !active.containsKey(f.id.owner) && active.size() >= MAX_CONTEXTS) return false;
        fences.put(f.id.owner, new Fence(f, f.phase == ENDED));
        if (f.phase == ENDED) { active.remove(f.id.owner); return true; }
        Controller prior = same ? active.get(f.id.owner) : null;
        int age = prior != null && f.phase == ARMED ? prior.age : 0;
        active.put(f.id.owner, new Controller(f, local, age)); return true;
    }
    private static int compare(Identity a, Identity b) {
        int epoch = Long.compare(a.epoch, b.epoch);
        return epoch != 0 ? epoch : Long.compare(a.input, b.input);
    }
    /** Pause freezes cosmetic clocks, but context loss is still fenced immediately. */
    public List<Identity> tick(boolean paused, long now, Predicate<Controller> validOwner) {
        var losses = new ArrayList<Identity>();
        for (Controller c : List.copyOf(active.values())) {
            int lifetime = c.frame.phase == ARMED ? ARM_TICKS : LIFE_TICKS;
            if (!validOwner.test(c) || !paused && (now >= c.frame.expiresAt || c.age + 1 >= lifetime)) {
                retire(c.id()); losses.add(c.id());
            } else if (!paused) active.put(c.id().owner, new Controller(c.frame, c.local, c.age + 1));
        }
        return List.copyOf(losses);
    }
    public Controller current(UUID owner) { return active.get(owner); }
    public List<Controller> controllers() { return List.copyOf(active.values()); }
    public int size() { return active.size(); }
    public int watermarkCount() { return fences.size(); }
    public void retire(Identity id) {
        if (id == null) return;
        Fence fence = fences.get(id.owner);
        if (fence != null && fence.frame.id.equals(id)) {
            fences.put(id.owner, new Fence(fence.frame, true)); active.remove(id.owner);
        }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
        for (Controller c : List.copyOf(active.values()))
            if (c.local && (c.id().epoch < epoch || c.id().epoch == epoch && c.id().input <= input)) retire(c.id());
    }
    /** Keep replay tombstones through reload, death, entity replacement and dimension changes. */
    public void clear() { for (Controller c : List.copyOf(active.values())) retire(c.id()); }
    public void resetSession() { active.clear(); fences.clear(); cancelledEpoch = cancelledThrough = 0; }
}
