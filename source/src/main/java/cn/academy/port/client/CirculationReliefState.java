package cn.academy.port.client;

import cn.academy.port.client.CirculationReliefMesh.Point;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** NEW432 authoritative receipts, recoverable quiet visuals and connection-scoped replay fences. */
public final class CirculationReliefState {
    public static final int ACTIVE = 0, ABSORBED = 1, PAUSED = 2, ENDED = 3;
    public static final int MAX_CONTEXTS = 4, MAX_WATERMARKS = 1024, QUIET_TICKS = 40;
    public static final int ABSORPTION_TICKS = 10;
    private static final UUID ZERO_UUID = new UUID(0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, Point point, Point facing,
                        Point groundSink, Point contact, int remainingHits, boolean grounded) {}
    public record Controller(Frame frame, boolean local, int quiet, Frame absorption, int absorptionAge) {
        public Identity id() { return frame.id; }
    }
    private record Fence(Frame frame, boolean retired) {}
    private final Map<UUID, Controller> active = new LinkedHashMap<>();
    private final Map<UUID, Fence> fences = new LinkedHashMap<>();
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        return f != null && f.id != null && f.id.entity >= 0 && f.id.owner != null && !f.id.owner.equals(ZERO_UUID)
                && f.id.epoch > 0 && f.id.input > 0 && f.id.token > 0 && f.sequence > 0
                && f.phase >= ACTIVE && f.phase <= ENDED && CirculationReliefMesh.validPoint(f.point)
                && CirculationReliefMesh.validPoint(f.contact) && CirculationReliefMesh.horizontalUnit(f.facing)
                && CirculationReliefMesh.validPoint(f.groundSink) && f.point.distanceSquared(f.contact) <= 9
                && (f.grounded ? Math.abs(f.groundSink.x() - f.point.x()) <= .001
                    && Math.abs(f.groundSink.z() - f.point.z()) <= .001
                    && f.point.y() - f.groundSink.y() >= -.001 && f.point.y() - f.groundSink.y() <= 4.001
                    : f.groundSink.equals(f.point))
                && f.remainingHits >= 0 && f.remainingHits <= 4
                && (f.phase != ENDED || f.remainingHits == 0)
                && (f.phase != ACTIVE || f.grounded && f.remainingHits > 0)
                && (f.phase != ABSORBED || f.grounded && f.remainingHits < 4)
                && (f.phase != PAUSED || !f.grounded || f.remainingHits == 0);
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || local && (f.id.epoch < cancelledEpoch
                || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        boolean same = old != null && old.frame.id.equals(f.id);
        if (old != null) {
            int order = compare(f.id, old.frame.id);
            if (order < 0 || order == 0 && (!same || old.retired || f.sequence <= old.frame.sequence)
                    || order > 0 && f.id.token <= old.frame.id.token) return false;
        }
        if (f.phase != ENDED && !active.containsKey(f.id.owner) && active.size() >= MAX_CONTEXTS) {
            // Local control must not expire merely because four remote players arrived first.
            Controller remote = local ? active.values().stream().filter(c -> !c.local).findFirst().orElse(null) : null;
            if (remote == null) return false;
            retire(remote.id());
        }
        fences.put(f.id.owner, new Fence(f, f.phase == ENDED));
        if (f.phase == ENDED) { active.remove(f.id.owner); return true; }
        Controller prior = same ? active.get(f.id.owner) : null;
        Frame absorption = f.phase == ABSORBED ? f : prior == null ? null : prior.absorption;
        int age = f.phase == ABSORBED ? 0 : prior == null ? ABSORPTION_TICKS : prior.absorptionAge;
        active.put(f.id.owner, new Controller(f, local, 0, absorption, age));
        return true;
    }
    private static int compare(Identity a, Identity b) {
        int epoch = Long.compare(a.epoch, b.epoch);
        return epoch != 0 ? epoch : Long.compare(a.input, b.input);
    }
    /** Heartbeats refresh only the owner lease, never a success cue's immutable contact or age. */
    public List<Identity> tick(boolean paused, Predicate<Controller> validOwner) {
        var losses = new ArrayList<Identity>();
        for (Controller c : List.copyOf(active.values())) {
            if (!validOwner.test(c)) {
                retire(c.id()); losses.add(c.id());
            } else if (!paused) {
                int age = Math.min(ABSORPTION_TICKS, c.absorptionAge + 1);
                // Quiet receipts are hidden, never retired: an authenticated later heartbeat can recover.
                active.put(c.id().owner, new Controller(c.frame, c.local, Math.min(QUIET_TICKS, c.quiet + 1),
                        age < ABSORPTION_TICKS ? c.absorption : null, age));
            }
        }
        return List.copyOf(losses);
    }
    public Controller current(UUID owner) { return active.get(owner); }
    public List<Controller> controllers() { return List.copyOf(active.values()); }
    public int size() { return active.size(); }
    public int watermarkCount() { return fences.size(); }
    public void retire(Identity id) {
        if (id == null) return;
        Fence old = fences.get(id.owner);
        if (old != null && old.frame.id.equals(id)) {
            fences.put(id.owner, new Fence(old.frame, true)); active.remove(id.owner);
        }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
        for (Controller c : List.copyOf(active.values()))
            if (c.local && (c.id().epoch < epoch || c.id().epoch == epoch && c.id().input <= input)) retire(c.id());
    }
    /** Reload, death, dimension change and body replacement retain retired identities. */
    public void clear() { for (Controller c : List.copyOf(active.values())) retire(c.id()); }
    public void resetSession() { active.clear(); fences.clear(); cancelledEpoch = cancelledThrough = 0; }
}
