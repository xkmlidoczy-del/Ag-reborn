package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** NEW411 authenticated, bounded receipts. One cosmetic controller; no speculative contacts. */
public final class DeflectionStrikeState {
    public static final int FIRE = 0, ENDED = 1, REFUSED = 0, DAMAGED = 1, ABSORBED = 2, PUSHED = 3;
    public static final int MAX_WATERMARKS = 1024, LIFE_TICKS = 12;
    private static final UUID ZERO_UUID = new UUID(0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, int slot, boolean crouch,
                        Point origin, Point end, int status) {}
    public record Contact(Point point, int status) {}
    public record Controller(Identity id, boolean local, boolean crouch, Point origin, Point end,
                             List<Contact> contacts, int age) {
        public Controller { contacts = List.copyOf(contacts); }
    }
    private record Fence(Frame frame, Point origin, boolean closed) {}
    private final Map<UUID, Fence> fences = new HashMap<>();
    private Controller active;
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(ZERO_UUID)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0 || f.sequence > 3
                || f.phase != FIRE && f.phase != ENDED || f.slot < 0 || f.slot > 1
                || f.status < REFUSED || f.status > PUSHED
                || f.origin == null || f.end == null || !f.origin.finite() || !f.end.finite()
                || f.origin.distanceSquared(f.end) > (cn.academy.port.highpower.core.DeflectionStrikeRules.RANGE * cn.academy.port.highpower.core.DeflectionStrikeRules.RANGE + 1e-6)) return false;
        if (f.phase == ENDED) return f.slot == 0 && f.status == REFUSED
                && f.origin.equals(new Point(0, 0, 0)) && f.end.equals(new Point(0, 0, 0));
        return f.crouch ? f.status == REFUSED || f.status == PUSHED : f.status != PUSHED;
    }

    /** Receipt geometry is not an authoritative hit query. Dust requires a confirmed native status. */
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || local && (f.id.epoch < cancelledEpoch
                || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        boolean same = old != null && old.frame.id.equals(f.id);
        Point castOrigin = f.origin;
        if (old != null) {
            if (same) {
                if (old.closed || f.sequence <= old.frame.sequence || f.crouch != old.frame.crouch) return false;
                castOrigin = old.origin;
                if (f.phase == FIRE && (f.slot <= old.frame.slot || old.frame.end.distanceSquared(f.origin) > 1e-8
                        || !sameRay(castOrigin, old.frame.end, f.end))) return false;
            } else if (f.id.epoch < old.frame.id.epoch || f.id.epoch == old.frame.id.epoch
                    && (f.id.input <= old.frame.id.input || f.id.token <= old.frame.id.token)) return false;
        }
        if (f.phase == FIRE && castOrigin.distanceSquared(f.end) > (cn.academy.port.highpower.core.DeflectionStrikeRules.RANGE * cn.academy.port.highpower.core.DeflectionStrikeRules.RANGE + 1e-6)) return false;
        fences.put(f.id.owner, new Fence(f, castOrigin, f.phase == ENDED));
        if (f.phase == ENDED) {
            if (active != null && active.id.equals(f.id)) active = null;
            return true;
        }
        // Remote traffic cannot displace the local player's one existing controller.
        if (active != null && active.local && !local) return true;
        if (active != null && !active.id.equals(f.id)) retire();
        var contacts = new ArrayList<Contact>(2);
        int age = 0;
        if (same && active != null && active.id.equals(f.id)) {
            contacts.addAll(active.contacts); age = active.age;
        }
        if (f.status != REFUSED) contacts.add(new Contact(f.end, f.status));
        if (contacts.size() > 2) return false;
        active = new Controller(f.id, local, f.crouch, castOrigin, f.end, contacts, age);
        return true;
    }
    private static boolean sameRay(Point start, Point middle, Point end) {
        double ax = middle.x() - start.x(), ay = middle.y() - start.y(), az = middle.z() - start.z();
        double bx = end.x() - start.x(), by = end.y() - start.y(), bz = end.z() - start.z();
        double aa = ax * ax + ay * ay + az * az, bb = bx * bx + by * by + bz * bz;
        double dot = ax * bx + ay * by + az * bz;
        return bb + 1e-8 >= aa && dot >= 0 && aa * bb - dot * dot <= 1e-7;
    }
    public Identity tick(boolean paused, boolean validContext) {
        if (active == null) return null;
        if (!validContext || !paused && active.age + 1 >= LIFE_TICKS) {
            Identity id = active.id; retire(); return id;
        }
        if (!paused) active = new Controller(active.id, active.local, active.crouch, active.origin, active.end, active.contacts, active.age + 1);
        return null;
    }
    public Controller active() { return active; }
    public boolean owns(Identity id) { return active != null && active.id.equals(id); }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
        if (active != null && active.local && active.id.epoch == epoch && active.id.input <= input) retire();
    }
    /** Preserve replay fences through reload, death and dimension transitions. */
    public void clear() { retire(); }
    private void retire() {
        if (active == null) return;
        Fence fence = fences.get(active.id.owner);
        if (fence != null && fence.frame.id.equals(active.id)) fences.put(active.id.owner, new Fence(fence.frame, fence.origin, true));
        active = null;
    }
    public void resetSession() { clear(); fences.clear(); cancelledEpoch = cancelledThrough = 0; }
    public int watermarkCount() { return fences.size(); }
}
