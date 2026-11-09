package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** NEW423 authenticated receipts only; independent bounded controllers and persistent replay fences. */
public final class YawPalmState {
    public static final int FIRE = 0, ENDED = 1, REFUSED = 0, DAMAGED = 1, ABSORBED = 2, PUSHED = 3;
    public static final int MAX_CONTROLLERS = 4, MAX_WATERMARKS = 1024, LIFE_TICKS = 12, MAX_SLOTS = 3;
    private static final UUID ZERO_UUID = new UUID(0, 0);
    public static final Point ZERO = new Point(0, 0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, int slot, boolean crouch,
                        Point origin, Point end, int status, Point velocityDelta) {}
    public record Contact(Point end, int status, Point velocityDelta) {}
    public record Controller(Identity id, boolean local, boolean crouch, Point origin,
                             List<Contact> contacts, int age) {
        public Controller { contacts = List.copyOf(contacts); }
        public Point end() { return contacts.getLast().end; }
    }
    private record Fence(Frame frame, boolean closed) {}
    private final Map<UUID, Fence> fences = new HashMap<>();
    private final Map<UUID, Controller> active = new LinkedHashMap<>();
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(ZERO_UUID)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0 || f.sequence > 4
                || f.phase != FIRE && f.phase != ENDED || f.slot < 0 || f.slot >= MAX_SLOTS
                || f.status < REFUSED || f.status > PUSHED || !finite(f.origin) || !finite(f.end) || !finite(f.velocityDelta)
                || f.origin.distanceSquared(f.end) > (cn.academy.port.highpower.core.YawPalmRules.RANGE * cn.academy.port.highpower.core.YawPalmRules.RANGE + 1e-6) || f.velocityDelta.distanceSquared(ZERO) > 4) return false;
        if (f.phase == ENDED) return f.slot == 0 && f.status == REFUSED && f.origin.equals(ZERO)
                && f.end.equals(ZERO) && f.velocityDelta.equals(ZERO);
        if (f.sequence > 3) return false;
        return f.status != REFUSED || f.velocityDelta.equals(ZERO);
    }
    private static boolean finite(Point p) { return p != null && p.finite(); }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || local && (f.id.epoch < cancelledEpoch
                || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        boolean same = old != null && old.frame.id.equals(f.id);
        if (old != null) {
            if (same) {
                if (old.closed || f.sequence <= old.frame.sequence || f.crouch != old.frame.crouch) return false;
                if (f.phase == FIRE && (f.slot <= old.frame.slot || f.sequence != old.frame.sequence + 1 || !f.origin.equals(old.frame.origin)
                        || !sameRay(f.origin, old.frame.end, f.end))) return false;
            } else if (f.id.epoch < old.frame.id.epoch || f.id.epoch == old.frame.id.epoch
                    && (f.id.input <= old.frame.id.input || f.id.token <= old.frame.id.token)) return false;
        }
        if (!same && f.phase == FIRE && f.sequence != 1) return false;
        if (f.phase == ENDED) {
            fences.put(f.id.owner, new Fence(f, true));
            Controller previous = active.get(f.id.owner);
            if (previous != null && previous.id.equals(f.id)) active.remove(f.id.owner);
            return true;
        }
        Controller previous = active.get(f.id.owner);
        if (!same && previous != null) retire(previous.id);
        if (previous == null && active.size() >= MAX_CONTROLLERS) {
            if (local) {
                Controller disposable = active.values().stream().filter(c -> !c.local).findFirst().orElse(null);
                if (disposable != null) retire(disposable.id);
            }
            if (active.size() >= MAX_CONTROLLERS) { fences.put(f.id.owner, new Fence(f, true)); return false; }
        }
        var contacts = new ArrayList<Contact>(MAX_SLOTS);
        int age = 0;
        if (same && previous != null) { contacts.addAll(previous.contacts); age = previous.age; }
        contacts.add(new Contact(f.end, f.status, f.velocityDelta));
        if (contacts.size() > MAX_SLOTS) return false;
        fences.put(f.id.owner, new Fence(f, false));
        active.put(f.id.owner, new Controller(f.id, local, f.crouch, f.origin, contacts, age));
        return true;
    }
    private static boolean sameRay(Point start, Point middle, Point end) {
        double ax = middle.x() - start.x(), ay = middle.y() - start.y(), az = middle.z() - start.z();
        double bx = end.x() - start.x(), by = end.y() - start.y(), bz = end.z() - start.z();
        double aa = ax * ax + ay * ay + az * az, bb = bx * bx + by * by + bz * bz, dot = ax * bx + ay * by + az * bz;
        return bb + 1e-8 >= aa && dot >= 0 && aa * bb - dot * dot <= 1e-7;
    }
    public List<Controller> tick(boolean paused, Predicate<Controller> validContext) {
        var retired = new ArrayList<Controller>();
        for (Controller cast : controllers()) {
            if (!validContext.test(cast) || !paused && cast.age + 1 >= LIFE_TICKS) { retire(cast.id); retired.add(cast); }
            else if (!paused) active.put(cast.id.owner, new Controller(cast.id, cast.local, cast.crouch, cast.origin, cast.contacts, cast.age + 1));
        }
        return List.copyOf(retired);
    }
    public List<Controller> controllers() { return List.copyOf(active.values()); }
    public boolean owns(Identity id) { Controller c = id == null ? null : active.get(id.owner); return c != null && c.id.equals(id); }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
        for (Controller c : controllers()) if (c.local && c.id.epoch == epoch && c.id.input <= input) retire(c.id);
    }
    public void clear() { for (Controller c : controllers()) retire(c.id); }
    public void retire(Identity id) {
        if (!owns(id)) return;
        Fence f = fences.get(id.owner);
        if (f != null && f.frame.id.equals(id)) fences.put(id.owner, new Fence(f.frame, true));
        active.remove(id.owner);
    }
    public void resetSession() { clear(); fences.clear(); cancelledEpoch = cancelledThrough = 0; }
    public int watermarkCount() { return fences.size(); }
}
