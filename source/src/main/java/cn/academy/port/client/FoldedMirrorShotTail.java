package cn.academy.port.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** A receipt-confirmed borrowed NEW221 shot outlives its retired control context for the normal flash duration. */
public final class FoldedMirrorShotTail {
    public record Shot(FoldedMirrorState.Frame frame, int age) {}
    private final Map<UUID, Shot> shots = new LinkedHashMap<>();

    /** Call only after the normal FoldedMirrorState identity/sequence fence accepts this receipt. */
    public boolean accepted(FoldedMirrorState.Frame frame) {
        if (!FoldedMirrorState.valid(frame) || frame.phase() != FoldedMirrorState.FIRE || frame.remainingTicks() != 0
                || frame.plates().size() != 1 || frame.path().isEmpty()
                || frame.status() != FoldedMirrorState.MISS && frame.status() != FoldedMirrorState.BLOCKED
                && frame.status() != FoldedMirrorState.DAMAGED && frame.status() != FoldedMirrorState.ABSORBED) return false;
        Shot old = shots.get(frame.id().owner());
        if (old != null && (frame.id().epoch() < old.frame.id().epoch() || frame.id().token() < old.frame.id().token()
                || frame.id().token() == old.frame.id().token() && frame.sequence() <= old.frame.sequence())) return false;
        if (old == null && shots.size() >= FoldedMirrorState.MAX_CONTEXTS) return false;
        shots.put(frame.id().owner(), new Shot(frame, 0)); return true;
    }
    public void tick(boolean paused, Predicate<FoldedMirrorState.Frame> valid) {
        var iterator = shots.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next(); Shot shot = entry.getValue();
            if (!valid.test(shot.frame) || !paused && shot.age + 1 >= FoldedMirrorState.FLASH_TICKS) iterator.remove();
            else if (!paused) entry.setValue(new Shot(shot.frame, shot.age + 1));
        }
    }
    public void cancel(UUID owner, long epoch, long input) {
        Shot shot = shots.get(owner);
        if (shot != null && shot.frame.id().epoch() == epoch && shot.frame.id().input() <= input) shots.remove(owner);
    }
    public List<Shot> shots() { return List.copyOf(shots.values()); }
    public Shot current(UUID owner) { return shots.get(owner); }
    public boolean empty() { return shots.isEmpty(); }
    public void clear() { shots.clear(); }
}
