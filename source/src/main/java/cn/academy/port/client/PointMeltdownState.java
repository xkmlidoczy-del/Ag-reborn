package cn.academy.port.client;

import cn.academy.port.client.BeamEtchingMesh.Mark;
import cn.academy.port.client.BeamEtchingMesh.Point;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** NEW233 authenticated, single-target visual receipts. A client clock never predicts a removal. */
public final class PointMeltdownState {
    public static final int PREVIEW = 0, CONFIRMED = 1, ENDED = 2, FOCUS = 3;
    public static final int READY = 0, UNAVAILABLE = 1, CANCELED = 2;
    public static final int MAX_CONTEXTS = 4, MAX_WATERMARKS = 1024, QUIET_TICKS = 12, HOLD_TICKS = 100, FOCUS_TICKS = 10;
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, Point origin, List<Mark> marks,
                        int focusTicks, int status, int remainingTicks) {}
    private record Fence(Frame frame, boolean retired, Mark selected, boolean confirmed) {}
    private final Map<UUID, Frame> active = new HashMap<>();
    private final Map<UUID, Fence> fences = new HashMap<>();
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(new UUID(0, 0))
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < PREVIEW || f.phase > FOCUS || !BeamEtchingMesh.validPoint(f.origin)
                || f.marks == null || f.marks.size() > 1 || f.focusTicks < 0 || f.focusTicks > FOCUS_TICKS
                || f.status < READY || f.status > CANCELED || f.remainingTicks < 0 || f.remainingTicks > HOLD_TICKS) return false;
        for (Mark mark : f.marks) if (!PointMeltdownMesh.validSource(f.origin, mark)) return false;
        return switch (f.phase) {
            case PREVIEW -> f.focusTicks == 0 && f.status != CANCELED && f.remainingTicks > 0;
            case FOCUS -> f.marks.size() == 1 && f.status == READY && f.remainingTicks == FOCUS_TICKS - f.focusTicks;
            case CONFIRMED -> f.marks.size() == 1 && f.focusTicks == FOCUS_TICKS && f.status == READY && f.remainingTicks == 0;
            default -> f.marks.isEmpty() && f.remainingTicks == 0;
        };
    }

    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || local && (f.id.epoch < cancelledEpoch || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        Mark selected = f.phase == FOCUS || f.phase == CONFIRMED ? f.marks.getFirst() : null;
        boolean confirmed = f.phase == CONFIRMED;
        if (old != null) {
            Frame before = old.frame;
            if (f.id.epoch < before.id.epoch || f.id.token < before.id.token) return false;
            if (f.id.token == before.id.token) {
                if (old.retired || !f.id.equals(before.id) || f.sequence <= before.sequence || f.focusTicks < before.focusTicks
                        || before.phase != PREVIEW && f.phase == PREVIEW || old.confirmed && f.phase != ENDED) return false;
                if (old.selected != null && selected != null && !old.selected.equals(selected)) return false;
                if (old.selected != null && f.phase != ENDED && selected == null) return false;
                if (f.phase == PREVIEW && f.remainingTicks > before.remainingTicks) return false;
                selected = old.selected == null ? selected : old.selected;
                confirmed |= old.confirmed;
            } else if (f.id.epoch == before.id.epoch && f.id.input <= before.id.input) return false;
        }
        if (f.phase != ENDED && !active.containsKey(f.id.owner) && active.size() >= MAX_CONTEXTS) return false;
        fences.put(f.id.owner, new Fence(f, f.phase == ENDED, selected, confirmed));
        if (f.phase == ENDED) active.remove(f.id.owner); else active.put(f.id.owner, f);
        return true;
    }

    public void retire(Identity id) {
        Fence fence = id == null ? null : fences.get(id.owner);
        if (fence != null && fence.frame.id.equals(id)) {
            fences.put(id.owner, new Fence(fence.frame, true, fence.selected, fence.confirmed));
            active.remove(id.owner);
        }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
    }
    /** Keep tombstones across world/player replacement; only an actual connection change resets them. */
    public void clear() { for (Frame frame : List.copyOf(active.values())) retire(frame.id); }
    public void resetSession() { active.clear(); fences.clear(); cancelledEpoch = cancelledThrough = 0; }
    public Frame current(UUID owner) { return active.get(owner); }
    public int size() { return active.size(); }
    public int watermarkCount() { return fences.size(); }

    /** Native paused ticks do not age a receipt, and a preview heartbeat cannot renew a hold. */
    public static final class Lease {
        private List<Mark> marks = List.of();
        private int remaining, quiet, phase = PREVIEW, focusTicks;
        private boolean received, ended;
        public void received(Frame frame) {
            quiet = 0;
            if (frame.phase == PREVIEW) remaining = received ? Math.min(remaining, frame.remainingTicks) : frame.remainingTicks;
            else remaining = frame.remainingTicks;
            marks = frame.phase == PREVIEW || frame.phase == FOCUS ? List.copyOf(frame.marks) : List.of();
            phase = frame.phase; focusTicks = frame.focusTicks; ended = frame.phase == ENDED; received = true;
        }
        public boolean tick(boolean paused, boolean valid) {
            if (!valid) { marks = List.of(); return false; }
            if (!paused) { if (remaining > 0) remaining--; if (quiet < QUIET_TICKS) quiet++; }
            if (phase == PREVIEW && remaining == 0 || quiet >= QUIET_TICKS || ended) marks = List.of();
            return quiet < QUIET_TICKS;
        }
        public List<Mark> marks() { return quiet < QUIET_TICKS && !ended && (phase == FOCUS || remaining > 0) ? marks : List.of(); }
        public int quiet() { return quiet; }
        public int remaining() { return remaining; }
        public int focusTicks() { return focusTicks; }
        public boolean focusing() { return phase == FOCUS; }
        public boolean ended() { return ended; }
    }
}
