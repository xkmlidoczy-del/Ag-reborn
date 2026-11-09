package cn.academy.port.story;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Server-side progress rules for the proposed first urban story.
 *
 * This is intentionally independent of a particular screen, quest object or network packet.
 * A caller must verify the actual world interaction before recording an objective; a client
 * choosing a route or naming an objective is not evidence that it happened.
 */
public final class UrbanStoryProgress {
    public enum Chapter { BROADCAST, STUDENT, PROTOCOL, CALIBRATOR, COMPLETE }
    public enum Approach { ABILITY, EQUIPMENT, COMBAT }
    public enum Objective {
        BROADCAST_LEAD(Chapter.BROADCAST),
        BROADCAST_RESCUE(Chapter.BROADCAST),
        BROADCAST_SOURCE(Chapter.BROADCAST),
        STUDENT_WITNESS(Chapter.STUDENT),
        STUDENT_RECORDS(Chapter.STUDENT),
        STUDENT_TRACES(Chapter.STUDENT),
        PROTOCOL_ACCESS(Chapter.PROTOCOL),
        PROTOCOL_RESCUE(Chapter.PROTOCOL),
        PROTOCOL_ARRAY(Chapter.PROTOCOL),
        CALIBRATOR_NODE_A(Chapter.CALIBRATOR),
        CALIBRATOR_NODE_B(Chapter.CALIBRATOR),
        CALIBRATOR_NODE_C(Chapter.CALIBRATOR),
        CALIBRATOR_EVACUATION(Chapter.CALIBRATOR),
        CALIBRATOR_DEFEATED(Chapter.CALIBRATOR);

        private final Chapter chapter;
        Objective(Chapter chapter) { this.chapter = chapter; }
        public Chapter chapter() { return chapter; }
    }
    public enum Result { RECORDED, ALREADY_RECORDED, WRONG_CHAPTER, MISSING_PREREQUISITES }

    private final EnumMap<Objective, Approach> completed = new EnumMap<>(Objective.class);

    public Chapter chapter() {
        for (Chapter chapter : Chapter.values()) {
            if (chapter == Chapter.COMPLETE || !chapterComplete(chapter)) return chapter;
        }
        throw new AssertionError("COMPLETE must be the final chapter");
    }

    public boolean completed(Objective objective) { return completed.containsKey(Objects.requireNonNull(objective)); }
    public Optional<Approach> approach(Objective objective) {
        return Optional.ofNullable(completed.get(Objects.requireNonNull(objective)));
    }
    public Map<Objective, Approach> snapshot() {
        return Collections.unmodifiableMap(new EnumMap<>(completed));
    }

    /** Only the authority that observed the in-world action may call this method. */
    public Result recordVerified(Objective objective, Approach approach) {
        Objects.requireNonNull(objective);
        Objects.requireNonNull(approach);
        if (completed.containsKey(objective)) return Result.ALREADY_RECORDED;
        if (objective.chapter() != chapter()) return Result.WRONG_CHAPTER;
        if (objective == Objective.CALIBRATOR_DEFEATED && !calibratorReady())
            return Result.MISSING_PREREQUISITES;
        completed.put(objective, approach);
        return Result.RECORDED;
    }

    /** Restoration rejects impossible transitions instead of silently granting chapter rewards. */
    public static Optional<UrbanStoryProgress> restore(Map<Objective, Approach> snapshot) {
        if (snapshot == null || snapshot.entrySet().stream()
                .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) return Optional.empty();
        var restored = new UrbanStoryProgress();
        for (Chapter chapter : Chapter.values()) {
            if (chapter == Chapter.COMPLETE) break;
            if (restored.chapter() != chapter) {
                if (snapshot.keySet().stream().anyMatch(objective -> objective.chapter() == chapter)) return Optional.empty();
                continue;
            }
            for (Objective objective : Objective.values()) {
                if (objective.chapter() != chapter || objective == Objective.CALIBRATOR_DEFEATED) continue;
                Approach approach = snapshot.get(objective);
                if (approach != null && restored.recordVerified(objective, approach) != Result.RECORDED)
                    return Optional.empty();
            }
            if (chapter == Chapter.CALIBRATOR && snapshot.containsKey(Objective.CALIBRATOR_DEFEATED)
                    && restored.recordVerified(Objective.CALIBRATOR_DEFEATED,
                    snapshot.get(Objective.CALIBRATOR_DEFEATED)) != Result.RECORDED) return Optional.empty();
        }
        return restored.completed.size() == snapshot.size() ? Optional.of(restored) : Optional.empty();
    }

    private boolean chapterComplete(Chapter chapter) {
        for (Objective objective : Objective.values())
            if (objective.chapter() == chapter && !completed.containsKey(objective)) return false;
        return true;
    }
    private boolean calibratorReady() {
        return completed.containsKey(Objective.CALIBRATOR_NODE_A)
                && completed.containsKey(Objective.CALIBRATOR_NODE_B)
                && completed.containsKey(Objective.CALIBRATOR_NODE_C)
                && completed.containsKey(Objective.CALIBRATOR_EVACUATION);
    }
}
