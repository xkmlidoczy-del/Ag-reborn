package cn.academy.port.client;

import cn.academy.port.highpower.core.WindPressureLiftRules.Mode;
import cn.academy.port.client.WindPressureLiftMesh.Point;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Authenticated visual receipts only. A permission flow never asserts that movement happened. */
public final class WindPressureLiftState {
    public static final int MAX_CONTEXTS = 4, MAX_FENCES = 1024, QUIET_TICKS = 4;
    public record Identity(int entity, UUID owner, long input, long token, long epoch) {}
    public record Frame(Identity id, long sequence, Point anchor, Mode mode, Point direction,
                        long issuedAt, long expiresAt, boolean terminal) {}
    public record Controller(Frame frame, boolean local, int age, int quiet) {
        public Identity id() { return frame.id; }
    }
    private record Fence(Frame frame, boolean retired) {}
    private final Map<UUID, Controller> active = new LinkedHashMap<>();
    private final Map<UUID, Fence> fences = new LinkedHashMap<>();
    private long cancelledEpoch, cancelledThrough;
    public static boolean valid(Frame f) {
        return f != null && f.id != null && f.id.entity >= 0 && f.id.owner != null
                && !f.id.owner.equals(new UUID(0, 0)) && f.id.input > 0 && f.id.epoch > 0 && f.id.token > 0
                && f.sequence >= 0 && WindPressureLiftMesh.validPoint(f.anchor) && f.mode != null
                && WindPressureLiftMesh.horizontalUnit(f.direction) && f.issuedAt >= 0
                && f.issuedAt < Long.MAX_VALUE - 4 && f.expiresAt >= f.issuedAt && f.expiresAt <= f.issuedAt + 4;
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || local && (f.id.epoch < cancelledEpoch
                || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_FENCES) return false;
        boolean same = old != null && old.frame.id.equals(f.id);
        if (old != null) {
            int order = compare(f.id, old.frame.id);
            if (order < 0 || order == 0 && (!same || old.retired || f.sequence < old.frame.sequence
                    || f.sequence == old.frame.sequence && !f.terminal)
                    || order > 0 && f.id.token <= old.frame.id.token) return false;
            if (same && (!f.anchor.equals(old.frame.anchor) || f.mode != old.frame.mode
                    || !f.direction.equals(old.frame.direction) || f.issuedAt < old.frame.issuedAt)) return false;
        }
        if (!f.terminal && !active.containsKey(f.id.owner) && active.size() >= MAX_CONTEXTS) {
            var remote = local ? active.values().stream().filter(c -> !c.local).findFirst().orElse(null) : null;
            if (remote == null) return false;
            retire(remote.id());
        }
        fences.put(f.id.owner, new Fence(f, f.terminal));
        if (f.terminal) { active.remove(f.id.owner); return true; }
        Controller prior = active.get(f.id.owner);
        active.put(f.id.owner, new Controller(f, local, same && prior != null ? prior.age : 0, 0));
        return true;
    }
    private static int compare(Identity a, Identity b) {
        int n = Long.compare(a.epoch, b.epoch); return n != 0 ? n : Long.compare(a.input, b.input);
    }
    public void tick(boolean paused, Predicate<Controller> valid) {
        for (Controller c : List.copyOf(active.values())) {
            if (!valid.test(c)) { retire(c.id()); continue; }
            if (!paused) active.put(c.id().owner, new Controller(c.frame, c.local, c.age + 1, Math.min(QUIET_TICKS, c.quiet + 1)));
        }
    }
    public void cancel(long input, long epoch) {
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = input; }
        else if (epoch == cancelledEpoch) cancelledThrough = Math.max(cancelledThrough, input);
        for (Controller c : List.copyOf(active.values())) if (c.local && (c.id().epoch < epoch
                || c.id().epoch == epoch && c.id().input <= input)) retire(c.id());
    }
    public void retire(Identity id) {
        Controller c = active.get(id.owner);
        if (c != null && c.id().equals(id)) active.remove(id.owner);
        Fence f = fences.get(id.owner);
        if (f != null && f.frame.id.equals(id)) fences.put(id.owner, new Fence(f.frame, true));
    }
    public void clear() { for (Controller c : List.copyOf(active.values())) retire(c.id()); }
    public void resetSession() { active.clear(); fences.clear(); cancelledEpoch = cancelledThrough = 0; }
    public Controller current(UUID owner) { return active.get(owner); }
    public List<Controller> controllers() { return List.copyOf(active.values()); }
    public int size() { return active.size(); }
}
