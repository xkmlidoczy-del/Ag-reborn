package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** NEW412 receipt-only held defense. Contact clocks never infer an impact from input or selection. */
public final class VectorParryState {
    public static final int ACTIVE = 0, ABSORBED = 1, TURNED = 2, ENDED = 3;
    public static final int MAX_CONTEXTS = 64, MAX_WATERMARKS = 1024, QUIET_TICKS = 12, CONTACT_TICKS = 6;
    private static final UUID ZERO_UUID = new UUID(0, 0);
    private static final Point ZERO = new Point(0, 0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, Point point, Point facing, Point impact,
                        Point incoming, Point turn, int preciseTicks, int remainingHits) {}
    public record Controller(Frame frame, boolean local, Frame absorption, int absorptionAge,
                             Frame turn, int turnAge, int quiet, int animationTick, int preciseTicks) {
        public Identity id() { return frame.id; }
    }
    private record Fence(Frame frame, boolean retired, boolean turned) {}
    private final Map<UUID, Controller> active = new HashMap<>();
    private final Map<UUID, Fence> fences = new HashMap<>();
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(ZERO_UUID)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < ACTIVE || f.phase > ENDED || !VectorParryGeometry.validPoint(f.point)
                || !VectorParryGeometry.validPoint(f.impact) || !VectorParryGeometry.unit(f.facing)
                || !directionOrZero(f.incoming) || !directionOrZero(f.turn)
                || f.preciseTicks < 0 || f.preciseTicks > 3 || f.remainingHits < 0 || f.remainingHits > 4) return false;
        Point chest = new Point(f.point.x(), f.point.y() + 1, f.point.z());
        if (f.impact.distanceSquared(chest) > 2.250001) return false;
        if (f.phase == ENDED) return f.preciseTicks == 0 && f.remainingHits == 0 && f.incoming.equals(ZERO) && f.turn.equals(ZERO);
        if (f.phase == ACTIVE) return f.incoming.equals(ZERO) && f.turn.equals(ZERO);
        if (f.phase == ABSORBED) return f.turn.equals(ZERO);
        return f.preciseTicks == 0 && VectorParryGeometry.unit(f.turn) && f.turn.y() == 0;
    }
    private static boolean directionOrZero(Point p) { return p != null && (p.equals(ZERO) || VectorParryGeometry.unit(p)); }

    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || local && (f.id.epoch < cancelledEpoch
                || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        boolean same = old != null && old.frame.id.equals(f.id);
        if (old != null) {
            int order = compare(f.id, old.frame.id);
            if (order < 0 || order == 0 && (!same || old.retired || f.sequence <= old.frame.sequence)
                    || order > 0 && f.id.token <= old.frame.id.token
                    || same && (f.preciseTicks > old.frame.preciseTicks || old.turned && f.phase == TURNED)) return false;
        }
        Controller existing = same ? active.get(f.id.owner) : null;
        if (existing != null && existing.quiet >= QUIET_TICKS && f.phase != ACTIVE && f.phase != ENDED) return false;
        if (f.phase != ENDED && !active.containsKey(f.id.owner) && active.size() >= MAX_CONTEXTS) return false;
        fences.put(f.id.owner, new Fence(f, f.phase == ENDED, f.phase == TURNED || same && old.turned));
        if (f.phase == ENDED) { active.remove(f.id.owner); return true; }
        Controller prior = same ? active.get(f.id.owner) : null;
        Frame absorption = prior == null ? null : prior.absorption, turn = prior == null ? null : prior.turn;
        int absorptionAge = prior == null ? CONTACT_TICKS : prior.absorptionAge;
        int turnAge = prior == null ? CONTACT_TICKS : prior.turnAge;
        if (f.phase == ABSORBED) { absorption = f; absorptionAge = 0; }
        if (f.phase == TURNED) { turn = f; turnAge = 0; }
        active.put(f.id.owner, new Controller(f, local, absorption, absorptionAge, turn, turnAge, 0,
                prior == null ? 0 : prior.animationTick, prior == null ? f.preciseTicks : Math.min(prior.preciseTicks, f.preciseTicks)));
        return true;
    }
    private static int compare(Identity a, Identity b) {
        int epoch = Long.compare(a.epoch, b.epoch);
        return epoch != 0 ? epoch : Long.compare(a.input, b.input);
    }
    /** Return newly hidden/lost identities once. Context validation applies while paused; cosmetic clocks stop. */
    public List<Identity> tick(boolean paused, Predicate<Controller> validOwner) {
        var losses = new ArrayList<Identity>();
        for (Controller c : List.copyOf(active.values())) {
            if (!validOwner.test(c)) {
                retire(c.id()); losses.add(c.id());
            } else if (!paused) {
                int quiet = Math.min(QUIET_TICKS, c.quiet + 1);
                if (quiet == QUIET_TICKS && c.quiet < QUIET_TICKS) losses.add(c.id());
                // A lost heartbeat hides cosmetics but cannot terminate an authoritative held context.
                // Drop event geometry on hide: a later fresh ACTIVE can restore only the base stance.
                active.put(c.id().owner, new Controller(c.frame, c.local, quiet == QUIET_TICKS ? null : c.absorption,
                        quiet == QUIET_TICKS ? CONTACT_TICKS : Math.min(CONTACT_TICKS, c.absorptionAge + 1),
                        quiet == QUIET_TICKS ? null : c.turn, quiet == QUIET_TICKS ? CONTACT_TICKS : Math.min(CONTACT_TICKS, c.turnAge + 1),
                        quiet, (c.animationTick + 1) % 1200, Math.max(0, c.preciseTicks - 1)));
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
        Fence fence = fences.get(id.owner);
        if (fence != null && fence.frame.id.equals(id)) {
            fences.put(id.owner, new Fence(fence.frame, true, fence.turned)); active.remove(id.owner);
        }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
        for (Controller c : List.copyOf(active.values()))
            if (c.local && (c.id().epoch < epoch || c.id().epoch == epoch && c.id().input <= input)) retire(c.id());
    }
    /** Reload, death and dimension loss keep replay tombstones; only a new connection resets them. */
    public void clear() { for (Controller c : List.copyOf(active.values())) retire(c.id()); }
    public void resetSession() { active.clear(); fences.clear(); cancelledEpoch = cancelledThrough = 0; }
}
