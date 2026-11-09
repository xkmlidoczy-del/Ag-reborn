package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static cn.academy.port.client.TrajectoryReturnState.*;

/** Optional presentation history. The unchanged receipt state remains the sole lifecycle authority. */
public final class TrajectoryReturnPresentation {
    public static final int HISTORY_TICKS = 8;
    private static final class Entry {
        final boolean enhanced;
        final Map<Integer, List<Segment>> histories = new HashMap<>();
        long sequence;
        Entry(boolean enhanced) { this.enhanced = enhanced; }
    }
    private final Map<Identity, Entry> entries = new HashMap<>();

    /** Call only after State.accept returned true, with its current controller (null for END). */
    public void accepted(Frame receipt, Controller controller, boolean enabledForNewCast) {
        if (receipt == null) return;
        if (receipt.kind() == ENDED) { entries.remove(receipt.id()); return; }
        if (controller == null || !controller.id().equals(receipt.id())) return;
        Entry entry = entries.get(receipt.id());
        if (entry == null) {
            // A missing presentation entry never promotes an already-running classic cast.
            if (receipt.sequence() != 1 || entries.size() >= MAX_CONTROLLERS) return;
            entry = new Entry(enabledForNewCast); entries.put(receipt.id(), entry);
        }
        if (receipt.sequence() <= entry.sequence) return;
        entry.sequence = receipt.sequence();
        trim(entry, controller);
        if (!entry.enhanced) return;
        if (receipt.kind() == CONTACT) { entry.histories.remove(receipt.slot()); return; }
        if (receipt.kind() != FLIGHT || receipt.origin().distanceSquared(receipt.end()) <= 1e-12) return;
        var history = new ArrayList<>(entry.histories.getOrDefault(receipt.slot(), List.of()));
        // Each pair is independently authenticated. Neither gaps nor contact produce a joined path.
        history.add(new Segment(receipt.origin(), receipt.end(), controller.age()));
        while (history.size() > MAX_SEGMENTS) history.removeFirst();
        entry.histories.put(receipt.slot(), List.copyOf(history));
    }

    public boolean enhanced(Controller controller) {
        Entry entry = controller == null ? null : entries.get(controller.id());
        return entry != null && entry.enhanced;
    }

    public Map<Integer, List<Segment>> histories(Controller controller) {
        Entry entry = controller == null ? null : entries.get(controller.id());
        if (entry == null || !entry.enhanced) return Map.of();
        trim(entry, controller);
        return Map.copyOf(entry.histories);
    }

    /** Includes retained endpoints with precisely the original 1.15m decoration margin. */
    public TrajectoryReturnMesh.Bounds bounds(Controller controller) {
        var original = TrajectoryReturnMesh.bounds(controller);
        Point min = original.min(), max = original.max();
        double margin = TrajectoryReturnMesh.DECORATIVE_MARGIN;
        for (var history : histories(controller).values()) for (var segment : history)
            for (Point p : List.of(segment.from(), segment.to())) {
                min = new Point(Math.min(min.x(), p.x() - margin), Math.min(min.y(), p.y() - margin), Math.min(min.z(), p.z() - margin));
                max = new Point(Math.max(max.x(), p.x() + margin), Math.max(max.y(), p.y() + margin), Math.max(max.z(), p.z() + margin));
            }
        return new TrajectoryReturnMesh.Bounds(min, max);
    }

    /** Run after acceptance, retirement, cancellation and ticks, including paused context checks. */
    public void prune(List<Controller> active) {
        entries.keySet().removeIf(id -> active.stream().noneMatch(c -> c.id().equals(id)));
        for (var controller : active) {
            Entry entry = entries.get(controller.id());
            if (entry != null) trim(entry, controller);
        }
    }

    private static void trim(Entry entry, Controller controller) {
        entry.histories.entrySet().removeIf(history -> controller.slots().stream()
                .noneMatch(slot -> slot.number() == history.getKey() && !slot.hammer() && slot.kind() != CONTACT));
        entry.histories.replaceAll((slot, history) -> history.stream()
                .filter(segment -> controller.age() >= segment.tick() && controller.age() - segment.tick() < HISTORY_TICKS).toList());
        entry.histories.values().removeIf(List::isEmpty);
    }

    public void clear() { entries.clear(); }
    public int size() { return entries.size(); }
}
