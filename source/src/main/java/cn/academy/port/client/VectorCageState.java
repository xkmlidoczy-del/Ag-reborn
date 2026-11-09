package cn.academy.port.client;

import cn.academy.port.highpower.core.VectorCageRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Bounded NEW441 receipts. The clock expires decoration; it never advances pressure or invents hits. */
public final class VectorCageState {
    public static final int PREVIEW = 0, STATE = 1, HIT = 2, ENDED = 3;
    public static final int REFUSED = 0, DAMAGED = 1, ABSORBED = 2;
    public static final int MAX_CONTROLLERS = 4, MAX_WATERMARKS = 1024, MAX_CONTACTS = 4;
    public static final int PREVIEW_TICKS = cn.academy.port.highpower.core.VectorCageRules.PREVIEW_TICKS, ACTIVE_TICKS = 80, CONTACT_TICKS = 5;
    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(x) <= 30_000_000 && Math.abs(y) <= 30_000_000 && Math.abs(z) <= 30_000_000; }
    }
    public static final Point ZERO = new Point(0, 0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, int age, boolean low, Point center,
                        double halfWidth, int pulse, int wall, Point contact, int status) {}
    public record Contact(Point point, int wall, int status, int age) {}
    public record Controller(Frame frame, boolean local, int quiet, List<Contact> contacts) {
        public Controller { contacts = List.copyOf(contacts); }
        public Identity id() { return frame.id; }
        public boolean preview() { return frame.phase == PREVIEW; }
    }
    private record Fence(Frame frame, boolean retired) {}
    private final Map<UUID, Fence> fences = new LinkedHashMap<>();
    private final Map<UUID, Controller> active = new LinkedHashMap<>();
    private long cancelledEpoch, cancelledThrough;

    public static int pulseAt(int age) {
        return VectorCageRules.sweepIndex(age);
    }
    public static double halfWidthAt(int age) {
        return VectorCageRules.halfWidth(age);
    }
    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null
                || f.id.owner.equals(new UUID(0, 0)) || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0
                || f.sequence <= 0 || f.sequence > 1024 || f.phase < PREVIEW || f.phase > ENDED
                || f.center == null || !f.center.finite() || f.contact == null || !f.contact.finite()
                || !Double.isFinite(f.halfWidth) || f.halfWidth < .25 || f.halfWidth > 4) return false;
        if (f.phase == ENDED) return f.status == REFUSED && f.wall == -1;
        if (f.age < 0 || f.age >= (f.phase == PREVIEW ? PREVIEW_TICKS : ACTIVE_TICKS)) return false;
        if (f.phase == PREVIEW) return f.halfWidth == 4 && f.pulse == -1 && f.wall == -1 && f.status == REFUSED;
        if (f.pulse != pulseAt(f.age) || Math.abs(f.halfWidth - halfWidthAt(f.age)) > 1e-6) return false;
        if (f.phase == STATE) return f.wall == -1 && f.status == REFUSED;
        if (f.pulse < 0 || f.wall < 0 || f.wall > 3 || f.status != DAMAGED && f.status != ABSORBED) return false;
        // The receipt may lie anywhere in the real six-tick swept slab, never outside the frozen area.
        double dx = f.contact.x - f.center.x, dz = f.contact.z - f.center.z;
        double dy = f.contact.y - f.center.y;
        double radius = switch (f.wall) { case 0 -> -dx; case 1 -> dx; case 2 -> -dz; default -> dz; };
        double tangent = f.wall < 2 ? Math.abs(dz) : Math.abs(dx);
        return radius >= VectorCageRules.toHalfWidth(f.age)-1e-6 && radius <= VectorCageRules.fromHalfWidth(f.age)+1e-6
                && tangent <= radius+1e-6 && dy >= .049999 && dy <= 3.050001;
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || local && (f.id.epoch < cancelledEpoch
                || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        boolean same = old != null && old.frame.id.equals(f.id);
        if (old != null) {
            int order = compare(f.id, old.frame.id);
            // A later authoritative end still resolves input ownership after cosmetic expiry.
            if (order < 0 || order == 0 && (!same || old.retired && f.phase != ENDED || f.sequence <= old.frame.sequence)
                    || order > 0 && f.id.token <= old.frame.id.token) return false;
            if (same && f.phase != ENDED) {
                if (!f.center.equals(old.frame.center) || f.low != old.frame.low
                        || old.frame.phase != PREVIEW && f.phase == PREVIEW
                        || (old.frame.phase == PREVIEW) == (f.phase == PREVIEW) && f.age < old.frame.age) return false;
            }
        }
        Controller previous = active.get(f.id.owner);
        if (f.phase == HIT && (!same || previous == null || previous.preview())) return false;
        if (f.phase != ENDED && previous == null && active.size() >= MAX_CONTROLLERS) {
            if (!local) return false;
            var victim = active.values().stream().filter(c -> !c.local).findFirst().orElse(null);
            if (victim == null) return false;
            retire(victim.id());
        }
        fences.put(f.id.owner, new Fence(f, f.phase == ENDED));
        if (f.phase == ENDED) { active.remove(f.id.owner); return true; }
        var contacts = new ArrayList<Contact>();
        if (same && previous != null && !previous.preview() && f.phase != PREVIEW) contacts.addAll(previous.contacts);
        if (f.phase == HIT) {
            if (contacts.size() == MAX_CONTACTS) contacts.removeFirst();
            contacts.add(new Contact(f.contact, f.wall, f.status, 0));
        }
        active.put(f.id.owner, new Controller(f, local, 0, contacts));
        return true;
    }
    public List<Identity> tick(boolean paused, Predicate<Controller> validOwner) {
        var ended = new ArrayList<Identity>();
        for (var c : List.copyOf(active.values())) {
            if (!validOwner.test(c)) { ended.add(c.id()); retire(c.id()); continue; }
            if (paused) continue;
            int quiet = c.quiet + 1, life = c.preview() ? PREVIEW_TICKS : ACTIVE_TICKS;
            if (quiet >= life - c.frame.age) { ended.add(c.id()); retire(c.id()); continue; }
            var contacts = c.contacts.stream().filter(p -> p.age + 1 < CONTACT_TICKS)
                    .map(p -> new Contact(p.point, p.wall, p.status, p.age + 1)).toList();
            active.put(c.id().owner, new Controller(c.frame, c.local, quiet, contacts));
        }
        return List.copyOf(ended);
    }
    public boolean owns(Identity id) { var c = id == null ? null : active.get(id.owner); return c != null && c.id().equals(id); }
    public List<Controller> controllers() { return List.copyOf(active.values()); }
    public int watermarkCount() { return fences.size(); }
    public void retire(Identity id) {
        if (id == null) return;
        Fence f = fences.get(id.owner);
        if (f != null && f.frame.id.equals(id)) fences.put(id.owner, new Fence(f.frame, true));
        var c = active.get(id.owner); if (c != null && c.id().equals(id)) active.remove(id.owner);
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
        for (var c : List.copyOf(active.values())) if (c.local && (c.id().epoch < epoch
                || c.id().epoch == epoch && c.id().input <= input)) retire(c.id());
    }
    /** Reload/body replacement retain closed replay fences; a new network session resets them. */
    public void clear() { for (var c : List.copyOf(active.values())) retire(c.id()); }
    public void resetSession() { active.clear(); fences.clear(); cancelledEpoch = cancelledThrough = 0; }
    private static int compare(Identity a, Identity b) {
        int epoch = Long.compare(a.epoch, b.epoch); return epoch != 0 ? epoch : Long.compare(a.input, b.input);
    }
}
