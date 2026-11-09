package cn.academy.port.client;

import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** NEW231 bounded receipt gate. Hollow warnings never prove a hit; only native damage receipts do. */
public final class DisintegrationSweepState {
    public static final int CHARGING = 0, SWEEPING = 1, ENDED = 2;
    public static final int PREPARED = 0, MISS = 1, BLOCKED = 2, REFUSED = 3, DAMAGED = 4, ABSORBED = 5, MINED = 6;
    public static final int MAX_CONTEXTS = 64, MAX_WATERMARKS = 1024, QUIET_TICKS = 12, CHARGE_TICKS = 100, SWEEP_TICKS = 10, FLASH_TICKS = 4;
    public static final double HALF_ANGLE = Math.toRadians(25), RANGE = 48, ORIGIN_LEASH = .25;
    private static final double EPS = 1e-7;
    private static final UUID ZERO_UUID = new UUID(0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Segment(Vector start, Vector end, int status) {}
    public record Frame(Identity id, long sequence, int phase, int status, int remainingTicks, boolean mining,
                        Vector ownerFeet, Vector origin, Vector forward, Vector across, double fromAngle, double toAngle,
                        List<Segment> path, List<Vector> marks) {
        public Frame { if (path != null) path = List.copyOf(path); if (marks != null) marks = List.copyOf(marks); }
    }
    private record Fence(Frame frame, boolean retired) {}
    private final Map<UUID, Frame> active = new HashMap<>();
    private final Map<UUID, Fence> fences = new HashMap<>();
    private long cancelledEpoch, cancelledThrough;
    private static boolean point(Vector p) { return p != null && p.finite(); }
    public static boolean direction(Vector p) { return point(p) && Math.abs(p.length() - 1) <= 1e-10; }
    public static boolean eligible(int level, boolean parentLearned, boolean skillLearned) { return level >= 3 && level <= 5 && parentLearned && skillLearned; }
    public static Vector ray(Frame f, double angle) { return f.forward.scale(Math.cos(angle)).add(f.across.scale(Math.sin(angle))); }
    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(ZERO_UUID)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < CHARGING || f.phase > ENDED || f.status < PREPARED || f.status > MINED
                || f.remainingTicks < 0 || f.remainingTicks > (f.phase == CHARGING ? CHARGE_TICKS : SWEEP_TICKS)
                || !point(f.ownerFeet) || !point(f.origin) || !direction(f.forward) || !direction(f.across)
                || Math.abs(f.forward.dot(f.across)) > 1e-10 || !Double.isFinite(f.fromAngle) || !Double.isFinite(f.toAngle)
                || f.fromAngle < -HALF_ANGLE - EPS || f.toAngle > HALF_ANGLE + EPS || f.fromAngle > f.toAngle + EPS
                || f.path == null || f.path.size() > (f.mining ? 2 : 8) || f.marks == null || f.marks.size() > 16
                || !f.mining && (!f.marks.isEmpty() || f.status == MINED)
                || f.mining && (f.status == DAMAGED || f.status == ABSORBED)
                || f.phase != SWEEPING && (!f.path.isEmpty() || !f.marks.isEmpty() || f.status == DAMAGED || f.status == ABSORBED || f.status == MINED)
                || f.phase == ENDED && f.remainingTicks != 0) return false;
        for (Segment s : f.path) {
            if (s == null || !point(s.start) || !point(s.end) || s.status < MISS || s.status > MINED
                    || s.start.subtract(f.origin).length() > ORIGIN_LEASH + EPS
                    || f.mining && (s.status == DAMAGED || s.status == ABSORBED) || !f.mining && s.status == MINED) return false;
            Vector delta = s.end.subtract(s.start); double length = delta.length();
            if (!(length > 0) || length > RANGE + EPS || Math.abs(delta.dot(f.forward.cross(f.across))) > .035 + EPS) return false;
            double angle = Math.atan2(delta.dot(f.across), delta.dot(f.forward));
            if (angle < -HALF_ANGLE - EPS || angle > HALF_ANGLE + EPS) return false;
        }
        for (Vector p : f.marks) if (!point(p) || p.subtract(f.origin).length() > RANGE + ORIGIN_LEASH + EPS) return false;
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
                if (fence.retired || !f.id.equals(old.id) || f.sequence <= old.sequence || f.phase < old.phase || f.mining != old.mining
                        || f.phase == old.phase && f.remainingTicks > old.remainingTicks) return false;
                if (old.phase == SWEEPING && f.phase != ENDED && (!f.origin.equals(old.origin) || !f.forward.equals(old.forward)
                        || !f.across.equals(old.across) || f.fromAngle < old.fromAngle - EPS || f.toAngle < old.toAngle - EPS)) return false;
            } else if (f.id.epoch == old.id.epoch && f.id.input <= old.id.input) return false;
        }
        if (f.phase != ENDED && !active.containsKey(f.id.owner) && active.size() >= MAX_CONTEXTS) return false;
        fences.put(f.id.owner, new Fence(f, f.phase == ENDED));
        if (f.phase == ENDED) active.remove(f.id.owner); else active.put(f.id.owner, f);
        return true;
    }
    public void retire(Identity id) {
        if (id == null) return; Fence fence = fences.get(id.owner);
        if (fence != null && fence.frame.id.equals(id)) { fences.put(id.owner, new Fence(fence.frame, true)); active.remove(id.owner); }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
    }
    public void clear() { for (Frame f : List.copyOf(active.values())) retire(f.id); }
    public void resetSession() { active.clear(); fences.clear(); cancelledEpoch = cancelledThrough = 0; }
    public int size() { return active.size(); }
    public int watermarkCount() { return fences.size(); }
    public Frame current(UUID owner) { return active.get(owner); }
    public static String status(Frame f) {
        return switch (f.status) {
            case MISS -> "miss"; case BLOCKED -> "blocked"; case REFUSED -> "refused"; case DAMAGED -> "damaged";
            case ABSORBED -> "absorbed"; case MINED -> "mined";
            default -> f.phase == ENDED ? "ended" : f.phase == CHARGING ? "charging" : "sweeping";
        };
    }
    /** A terminal visual tail owns no input, warning, live core, damage, or renewable lifetime. */
    public static final class Afterglow {
        private final Frame frame;
        private int age;
        public Afterglow(Frame receipt, Frame terminal) {
            if (!valid(receipt) || !valid(terminal) || receipt.phase != SWEEPING || receipt.path.isEmpty()
                    || terminal.phase != ENDED || !receipt.id.equals(terminal.id) || terminal.sequence <= receipt.sequence)
                throw new IllegalArgumentException("An authenticated final native receipt and its matching terminal are required");
            frame = receipt;
        }
        public Frame frame() { return frame; }
        public int age() { return age; }
        public boolean tick(boolean paused, boolean valid) { if (!valid) age = FLASH_TICKS; if (age >= FLASH_TICKS) return false; if (!paused) age++; return age < FLASH_TICKS; }
    }
    /** Charge and sweep each have a non-renewable local lease as well as a finite heartbeat grace. */
    public static final class Lease {
        private int remaining, quiet, phase = -1, age;
        public void received(Frame f) {
            remaining = phase != f.phase ? f.remainingTicks : Math.min(remaining, f.remainingTicks);
            phase = f.phase; quiet = 0;
        }
        public boolean tick(boolean paused, boolean valid) {
            if (!valid || phase == ENDED || phase < 0) return false;
            if (!paused) { remaining = Math.max(-1, remaining - 1); quiet = Math.min(QUIET_TICKS, quiet + 1); age++; }
            // The server sends remaining=1 on sweep tick9; keep ownership through the tick10 receipt/ENDED boundary.
            return (phase == SWEEPING ? remaining >= 0 : remaining > 0) && quiet < QUIET_TICKS && age < CHARGE_TICKS + SWEEP_TICKS;
        }
        public int remaining() { return remaining; }
        public int quiet() { return quiet; }
    }
}
