package cn.academy.port.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** NEW433 owner-only server receipts. No source identity or source position enters client state. */
public final class CirculationListeningState {
    public static final int ACTIVE = 0, PAUSED = 1, ENDED = 2;
    public static final int KIND_ARROW = 0, KIND_RUNNER = 1;
    public static final int MAX_CONTEXTS = 1, MAX_CUES = 8, MAX_WATERMARKS = 1024;
    public static final int QUIET_TICKS = 40, CUE_TICKS = 4;
    private static final UUID ZERO_UUID = new UUID(0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Cue(int azimuth, int elevation, int strength, int kind) {}
    public record Frame(Identity id, long sequence, int phase, List<Cue> cues) {
        public Frame { if (cues != null) cues = List.copyOf(cues); }
    }
    public record Controller(Frame frame, int quiet, int cueAge) {
        public Identity id() { return frame.id; }
    }
    private record Fence(Frame frame, boolean retired) {}
    private final Map<UUID, Fence> fences = new LinkedHashMap<>();
    private Controller active;
    private long cancelledEpoch, cancelledThrough;
    public static boolean validCue(Cue cue) {
        return cue != null && cue.azimuth >= 0 && cue.azimuth < 16 && cue.elevation >= -1 && cue.elevation <= 1
                && cue.strength >= 1 && cue.strength <= 3 && cue.kind >= KIND_ARROW && cue.kind <= KIND_RUNNER;
    }
    public static boolean valid(Frame f) {
        return f != null && f.id != null && f.id.entity >= 0 && f.id.owner != null && !f.id.owner.equals(ZERO_UUID)
                && f.id.epoch > 0 && f.id.input > 0 && f.id.token > 0 && f.sequence > 0
                && f.phase >= ACTIVE && f.phase <= ENDED && f.cues != null && f.cues.size() <= MAX_CUES
                && f.cues.stream().allMatch(CirculationListeningState::validCue)
                && (f.phase == ACTIVE || f.cues.isEmpty());
    }
    /** Remote receipts are rejected even if a transport incorrectly broadcasts one. */
    public boolean accept(Frame f, boolean local) {
        if (!local || !valid(f) || f.id.epoch < cancelledEpoch
                || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough) return false;
        Fence old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        boolean same = old != null && old.frame.id.equals(f.id);
        if (old != null) {
            int order = compare(f.id, old.frame.id);
            if (order < 0 || order == 0 && (!same || old.retired || f.sequence <= old.frame.sequence)
                    || order > 0 && f.id.token <= old.frame.id.token) return false;
        }
        if (active != null && !active.id().owner.equals(f.id.owner)) return false;
        fences.put(f.id.owner, new Fence(f, f.phase == ENDED));
        active = f.phase == ENDED ? null : new Controller(f, 0, 0);
        return true;
    }
    private static int compare(Identity a, Identity b) {
        int epoch = Long.compare(a.epoch, b.epoch);
        return epoch != 0 ? epoch : Long.compare(a.input, b.input);
    }
    /** Silence hides the four-tick cue, then the status lease, without cancelling server ownership. */
    public List<Identity> tick(boolean paused, Predicate<Controller> validOwner) {
        if (active == null) return List.of();
        if (!validOwner.test(active)) {
            Identity lost = active.id(); retire(lost); return List.of(lost);
        }
        if (!paused) active = new Controller(active.frame, Math.min(QUIET_TICKS, active.quiet + 1),
                Math.min(CUE_TICKS, active.cueAge + 1));
        return List.of();
    }
    public Controller current(UUID owner) { return active != null && active.id().owner.equals(owner) ? active : null; }
    public List<Controller> controllers() { return active == null ? List.of() : List.of(active); }
    public int size() { return active == null ? 0 : 1; }
    public int watermarkCount() { return fences.size(); }
    public void retire(Identity id) {
        if (id == null) return;
        Fence old = fences.get(id.owner);
        if (old != null && old.frame.id.equals(id)) {
            fences.put(id.owner, new Fence(old.frame, true));
            if (active != null && active.id().equals(id)) active = null;
        }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
        if (active != null && (active.id().epoch < epoch || active.id().epoch == epoch && active.id().input <= input)) retire(active.id());
    }
    /** Reload, death, dimension change and body replacement retain terminal replay fences. */
    public void clear() { if (active != null) retire(active.id()); }
    public void resetSession() { active = null; fences.clear(); cancelledEpoch = cancelledThrough = 0; }
}
