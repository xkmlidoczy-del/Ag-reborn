package cn.academy.port.client;

import cn.academy.port.client.BeamEtchingMesh.Block;
import cn.academy.port.client.BeamEtchingMesh.Mark;
import cn.academy.port.client.BeamEtchingMesh.Point;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bounded authenticated visual receipts. No target selection, mining, damage or predicted success. */
public final class BeamEtchingState {
    public static final int PREVIEW = 0, CONFIRMED = 1, ENDED = 2;
    public static final int READY = 0, UNAVAILABLE = 1, NO_SUCCESS = 2;
    public static final int MAX_CONTEXTS = 64, MAX_WATERMARKS = 1024, QUIET_TICKS = 12, HOLD_TICKS = 100;
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, Point origin, List<Mark> marks,
                        int confirmedCount, int status, int remainingTicks) {}
    private record Fence(Frame frame, boolean retired, Set<Block> confirmed, List<Mark> selected) {}
    private final Map<UUID, Frame> active = new HashMap<>();
    private final Map<UUID, Fence> fences = new HashMap<>();
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(new UUID(0, 0))
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < PREVIEW || f.phase > ENDED || !BeamEtchingMesh.validPoint(f.origin)
                || f.marks == null || f.marks.size() > BeamEtchingMesh.MAX_MARKS || f.confirmedCount < 0 || f.confirmedCount > BeamEtchingMesh.MAX_MARKS
                || f.status < READY || f.status > NO_SUCCESS || f.remainingTicks < 0 || f.remainingTicks > HOLD_TICKS) return false;
        Set<Block> blocks = new HashSet<>();
        for (Mark mark : f.marks) if (!BeamEtchingMesh.validSource(f.origin, mark) || !blocks.add(mark.block())) return false;
        return switch (f.phase) {
            case PREVIEW -> f.confirmedCount == 0 && f.status != NO_SUCCESS && f.remainingTicks > 0;
            case CONFIRMED -> f.marks.size() == 1 && f.confirmedCount > 0 && f.status == READY && f.remainingTicks == 0;
            default -> f.marks.isEmpty() && f.remainingTicks == 0 && (f.status != NO_SUCCESS || f.confirmedCount == 0);
        };
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || local && (f.id.epoch < cancelledEpoch || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        Set<Block> confirmed = new HashSet<>();
        List<Mark> selected = f.phase == PREVIEW ? List.copyOf(f.marks) : List.of();
        if (old != null) {
            Frame before = old.frame;
            if (f.id.epoch < before.id.epoch || f.id.token < before.id.token) return false;
            if (f.id.token == before.id.token) {
                if (old.retired || !f.id.equals(before.id) || f.sequence <= before.sequence || f.confirmedCount < before.confirmedCount
                        || before.phase == CONFIRMED && f.phase == PREVIEW) return false;
                confirmed.addAll(old.confirmed);
                if (f.phase != PREVIEW) selected = old.selected;
                if (f.phase == CONFIRMED && f.confirmedCount != before.confirmedCount + 1) return false;
                if (f.phase == CONFIRMED) {
                    // A just-acquired mark may be released before the next four-tick preview packet.
                    Mark actual = f.marks.getFirst();
                    for (Mark known : selected) if (known.block().equals(actual.block()) && !known.equals(actual)) return false;
                }
                if (f.phase == PREVIEW) {
                    // Already selected marks are immutable; a later heartbeat cannot substitute a hidden or new point.
                    if (f.marks.size() < before.marks.size()) return false;
                    for (int i = 0; i < before.marks.size(); i++) if (!before.marks.get(i).equals(f.marks.get(i))) return false;
                }
            } else if (f.id.epoch == before.id.epoch && f.id.input <= before.id.input) return false;
        }
        if (f.phase == CONFIRMED && !confirmed.add(f.marks.getFirst().block())) return false;
        if (f.phase != ENDED && !active.containsKey(f.id.owner) && active.size() >= MAX_CONTEXTS) return false;
        fences.put(f.id.owner, new Fence(f, f.phase == ENDED, Set.copyOf(confirmed), selected));
        if (f.phase == ENDED) active.remove(f.id.owner); else active.put(f.id.owner, f);
        return true;
    }
    public void retire(Identity id) {
        Fence fence = id == null ? null : fences.get(id.owner);
        if (fence != null && fence.frame.id.equals(id)) {
            fences.put(id.owner, new Fence(fence.frame, true, fence.confirmed, fence.selected)); active.remove(id.owner);
        }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
    }
    public void clear() { for (Frame frame : List.copyOf(active.values())) retire(frame.id); }
    public void resetSession() { active.clear(); fences.clear(); cancelledEpoch = cancelledThrough = 0; }
    public Frame current(UUID owner) { return active.get(owner); }
    public int size() { return active.size(); }
    public int watermarkCount() { return fences.size(); }

    /** Heartbeats can shorten a hold's lease; receipts never extend it beyond the server's 100 ticks. */
    public static final class Lease {
        private List<Mark> marks = List.of();
        private int remaining, quiet;
        private boolean received, executing, ended;
        public void received(Frame frame) {
            quiet = 0;
            if (frame.phase == PREVIEW) {
                remaining = received ? Math.min(remaining, frame.remainingTicks) : frame.remainingTicks;
                marks = List.copyOf(frame.marks);
            } else if (frame.phase == CONFIRMED) {
                executing = true;
                marks = marks.stream().filter(m -> !m.block().equals(frame.marks.getFirst().block())).toList();
            } else { marks = List.of(); remaining = 0; ended = true; }
            received = true;
        }
        public boolean tick(boolean paused, boolean valid) {
            if (!valid) { marks = List.of(); return false; }
            if (!paused) { if (remaining > 0) remaining--; if (quiet < QUIET_TICKS) quiet++; }
            if (!executing && remaining == 0 || quiet >= QUIET_TICKS || ended) marks = List.of();
            return quiet < QUIET_TICKS;
        }
        public List<Mark> marks() { return quiet < QUIET_TICKS && !ended && (remaining > 0 || executing) ? marks : List.of(); }
        public int quiet() { return quiet; }
        public int remaining() { return remaining; }
        public boolean ended() { return ended; }
        public boolean executing() { return executing; }
    }
}
