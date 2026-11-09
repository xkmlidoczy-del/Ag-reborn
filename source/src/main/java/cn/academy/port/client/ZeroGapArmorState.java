package cn.academy.port.client;

import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.highpower.core.ZeroGapArmorGeometry;
import cn.academy.port.highpower.core.ZeroGapArmorTiming;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** NEW252 receipt-only physical phase, finite owner leases and non-evicting replay fences. */
public final class ZeroGapArmorState {
    public static final int ACTIVE = 0, HIT = 1, ENDED = 2, STARTUP = 3;
    public static final int MAX_CONTEXTS = 64, MAX_WATERMARKS = 1024, QUIET_TICKS = 12;
    public static final int HIT_TICKS = 6, FADE_TICKS = 4;
    private static final UUID ZERO_UUID = new UUID(0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, Vector feet, double yaw, int phaseTick,
                        int selectedMask, int liveMask, int breakTicks, int plate, Vector point) {}
    private record Fence(Frame frame, boolean retired, boolean started) {}
    private final Map<UUID, Frame> active = new HashMap<>();
    private final Map<UUID, Fence> fences = new HashMap<>();
    private long cancelledEpoch, cancelledThrough;

    public static boolean eligible(int level, boolean parentLearned, boolean learned) {
        return level == 5 && parentLearned && learned;
    }
    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(ZERO_UUID)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < ACTIVE || f.phase > STARTUP || !ZeroGapArmorMesh.validPoint(f.feet)
                || !ZeroGapArmorMesh.validPoint(f.point) || !Double.isFinite(f.yaw) || f.yaw < 0 || f.yaw >= 360
                || f.phaseTick < 0 || f.phaseTick >= ZeroGapArmorTiming.REVOLUTION_TICKS
                || f.breakTicks < 0 || f.breakTicks > ZeroGapArmorTiming.BREAK_TICKS
                || f.selectedMask < 0 || f.selectedMask > 63 || Integer.bitCount(f.selectedMask) != 3
                || f.liveMask < 0 || (f.liveMask & ~f.selectedMask) != 0
                || (f.phase == STARTUP || f.phase == ENDED || f.breakTicks > 0) && f.liveMask != 0) return false;
        try {
            var geometry = ZeroGapArmorGeometry.capture(f.feet, f.yaw, f.phaseTick);
            if (geometry.activeMask() != f.selectedMask) return false;
            for (var plate : geometry.plates()) {
                var bounds = ZeroGapArmorGeometry.volumeBounds(plate);
                if (!ZeroGapArmorMesh.validPoint(bounds.min()) || !ZeroGapArmorMesh.validPoint(bounds.max())) return false;
            }
        } catch (IllegalArgumentException invalidWorldBoundary) { return false; }
        // An arrow can settle a historical crossing after the numbered plate has rotated away.
        // Such a finite receipt is valid; only the mesh may decide if a local current-plane glint is safe.
        return f.phase == HIT ? f.plate >= 0 && f.plate < ZeroGapArmorGeometry.PLATE_COUNT
                : f.plate == -1 && f.point.subtract(f.feet).length() <= 1e-7;
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || local && (f.id.epoch < cancelledEpoch
                || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        if (old != null) {
            int order = compare(f.id, old.frame.id);
            if (order < 0 || order == 0 && (!f.id.equals(old.frame.id) || old.retired || f.sequence <= old.frame.sequence)
                    || order > 0 && f.id.token <= old.frame.id.token
                    || order == 0 && old.started && f.phase == STARTUP) return false;
        }
        if (f.phase != ENDED && !active.containsKey(f.id.owner) && active.size() >= MAX_CONTEXTS) return false;
        boolean started = f.phase == ACTIVE || f.phase == HIT || old != null && old.frame.id.equals(f.id) && old.started;
        fences.put(f.id.owner, new Fence(f, f.phase == ENDED, started));
        if (f.phase == ENDED) active.remove(f.id.owner); else active.put(f.id.owner, f);
        return true;
    }
    private static int compare(Identity a, Identity b) {
        int epoch = Long.compare(a.epoch, b.epoch);
        return epoch != 0 ? epoch : Long.compare(a.input, b.input);
    }
    public void retire(Identity id) {
        if (id == null) return;
        Fence old = fences.get(id.owner);
        if (old != null && old.frame.id.equals(id)) {
            fences.put(id.owner, new Fence(old.frame, true, old.started)); active.remove(id.owner);
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

    /** Keep real contact points immutable; keepalives neither relocate nor renew a glint. */
    public static final class Contacts {
        private final Frame[] frames = new Frame[ZeroGapArmorGeometry.PLATE_COUNT];
        private final int[] ages = new int[ZeroGapArmorGeometry.PLATE_COUNT];
        public Contacts() { clear(); }
        public void received(Frame f) {
            if (!valid(f) || f.phase != HIT) return;
            Frame previous = frames[f.plate];
            if (previous != null && previous.id.equals(f.id) && previous.sequence >= f.sequence) return;
            frames[f.plate] = f; ages[f.plate] = 0;
        }
        public void tick(boolean paused) {
            if (!paused) for (int i = 0; i < ages.length; i++) ages[i] = Math.min(HIT_TICKS, ages[i] + 1);
        }
        public Frame frame(int plate) { return plate >= 0 && plate < frames.length && ages[plate] < HIT_TICKS ? frames[plate] : null; }
        public int age(int plate) { return plate >= 0 && plate < ages.length ? ages[plate] : HIT_TICKS; }
        public void clear() { for (int i = 0; i < frames.length; i++) { frames[i] = null; ages[i] = HIT_TICKS; } }
    }
    /** Cosmetic clocks can expire or fade a receipt, and never advance phase, break debt or live mask. */
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
