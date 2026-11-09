package cn.academy.port.client;

import cn.academy.port.highpower.core.FoldedMirrorGeometry.Plate;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** NEW223 bounded, preview-only receipts; NEW221 owns every actual attack receipt. */
public final class ScatterLocatorState {
    public static final int ACTIVE = 0, EDIT = 1, PREVIEW = 2, ENDED = 3;
    public static final int PREPARED = 0, MISS = 1, BLOCKED = 2, REFUSED = 3, DEGENERATE = 6;
    public static final int MAX_PLATES = 1, MAX_SEGMENTS = 2, MAX_CONTEXTS = 64, MAX_WATERMARKS = 1024;
    public static final int LIFE_TICKS = cn.academy.port.highpower.core.ScatterLocatorRules.LIFETIME, QUIET_TICKS = 12, EDIT_TICKS = 100;
    public record Frame(FoldedMirrorState.Identity id, long sequence, int phase, int status, int remainingTicks,
                        Vector ownerFeet, List<Plate> plates, Vector ghostCenter, Vector ghostNormal,
                        List<FoldedMirrorState.Segment> path, double heldCp) {
        public Frame { if (plates != null) plates = List.copyOf(plates); if (path != null) path = List.copyOf(path); }
    }
    public record Status(int seconds, String key, double heldCp, boolean recallable) {}
    private final FoldedMirrorState receipts = new FoldedMirrorState();
    private final Map<UUID, Frame> active = new HashMap<>();

    /** High-level control fees remain positive in the HUD, including fractions below one CP. */
    public static String heldText(double held) {
        return !Double.isFinite(held) || held < 0 ? "0" : java.math.BigDecimal.valueOf(held).stripTrailingZeros().toPlainString();
    }

    /** PREVIEW can only enter the original renderer as a dashed ACTIVE path, never FIRE. */
    public static FoldedMirrorState.Frame mirror(Frame f) {
        return new FoldedMirrorState.Frame(f.id, f.sequence, f.phase == PREVIEW ? FoldedMirrorState.ACTIVE : f.phase,
                f.status, f.remainingTicks, f.ownerFeet, f.plates, f.ghostCenter, f.ghostNormal, f.path);
    }
    public static boolean eligible(int level, boolean parentLearned, boolean learned) {
        return level >= 2 && level <= 5 && parentLearned && learned;
    }
    public static boolean valid(Frame f) {
        return f != null && f.phase >= ACTIVE && f.phase <= ENDED
                && (f.status >= PREPARED && f.status <= REFUSED || f.status == DEGENERATE)
                && f.plates != null && f.plates.size() <= MAX_PLATES && f.path != null && f.path.size() <= MAX_SEGMENTS
                && Double.isFinite(f.heldCp) && f.heldCp >= 0 && f.heldCp <= 1_000_000_000
                && (f.phase != ENDED || f.heldCp == 0) && (f.plates.isEmpty() || f.phase == ENDED || f.heldCp > 0)
                && (f.phase != PREVIEW || !f.plates.isEmpty()) && FoldedMirrorState.valid(mirror(f));
    }
    public static boolean hasGhost(Frame f) { return f != null && FoldedMirrorState.hasGhost(mirror(f)); }
    public static boolean recallAvailable(Frame f, int remaining, int quiet) {
        return valid(f) && f.phase != ENDED && !f.plates.isEmpty() && remaining > 0 && remaining <= LIFE_TICKS
                && quiet >= 0 && quiet < QUIET_TICKS;
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f)) return false;
        Frame old = active.get(f.id.owner());
        if (old != null && old.id.token() == f.id.token() && !old.plates.isEmpty()
                && f.phase != ENDED && Double.compare(old.heldCp, f.heldCp) != 0) return false;
        if (!receipts.accept(mirror(f), local)) return false;
        if (f.phase == ENDED) active.remove(f.id.owner()); else active.put(f.id.owner(), f);
        return true;
    }
    public void retire(FoldedMirrorState.Identity id) {
        if (id == null) return;
        receipts.retire(id);
        Frame current = active.get(id.owner());
        if (current != null && current.id.epoch() == id.epoch() && current.id.token() == id.token()) active.remove(id.owner());
    }
    public void cancel(long epoch, long input) { receipts.cancel(epoch, input); }
    public void clear() { receipts.clear(); active.clear(); }
    public void resetSession() { receipts.resetSession(); active.clear(); }
    public Frame current(UUID owner) { return active.get(owner); }
    public int size() { return active.size(); }
    public int watermarkCount() { return receipts.watermarkCount(); }
    public static Status status(Frame f, int remaining, int quiet) {
        String key = switch (f.status) {
            case BLOCKED -> "blocked"; case REFUSED -> "refused"; case DEGENERATE -> "degenerate";
            default -> f.phase == ENDED ? "ended" : f.phase == EDIT ? "adjusting" : f.phase == PREVIEW ? "preview" : "prepared";
        };
        return new Status(f.plates.isEmpty() ? -1 : (Math.clamp(remaining, 0, LIFE_TICKS) + 19) / 20,
                key, f.heldCp, recallAvailable(f, remaining, quiet));
    }
    /** Packet gaps hide the presentation without destroying the server's still-live reserved plate. */
    public static final class Lease {
        private int remaining, quiet, editAge;
        private boolean received, placed, ended;
        public void received(Frame f) {
            boolean nowPlaced = !f.plates.isEmpty();
            remaining = !received || !placed && nowPlaced ? f.remainingTicks : Math.min(remaining, f.remainingTicks);
            quiet = 0; placed |= nowPlaced; received = true; ended = f.phase == ENDED;
        }
        public boolean tick(boolean paused, boolean valid) {
            if (!valid || ended) return false;
            if (!paused) { if (remaining > 0) remaining--; quiet = Math.min(QUIET_TICKS, quiet + 1); if (!placed) editAge++; }
            return placed ? remaining > 0 : editAge < EDIT_TICKS;
        }
        public boolean visible() { return received && !ended && quiet < QUIET_TICKS && (placed ? remaining > 0 : editAge < EDIT_TICKS); }
        public int remaining() { return remaining; }
        public int quiet() { return quiet; }
    }
}
