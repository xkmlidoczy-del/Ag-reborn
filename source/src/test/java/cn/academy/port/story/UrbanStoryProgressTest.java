package cn.academy.port.story;

import java.util.EnumMap;
import java.util.Map;
import static cn.academy.port.story.UrbanStoryProgress.*;

/** Behavioral checks; world-event authenticity is the responsibility of future native adapters. */
public final class UrbanStoryProgressTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (Approach approach : Approach.values()) runChapter(approach);
        var invalid = new EnumMap<Objective, Approach>(Objective.class);
        invalid.put(Objective.STUDENT_WITNESS, Approach.EQUIPMENT);
        check(UrbanStoryProgress.restore(invalid).isEmpty(), "future chapter cannot be restored");
        invalid.clear();
        for (Objective objective : Objective.values()) {
            if (objective.chapter() == Chapter.BROADCAST || objective.chapter() == Chapter.STUDENT
                    || objective.chapter() == Chapter.PROTOCOL)
                invalid.put(objective, Approach.ABILITY);
        }
        invalid.put(Objective.CALIBRATOR_DEFEATED, Approach.COMBAT);
        check(UrbanStoryProgress.restore(invalid).isEmpty(), "boss completion requires all nodes and evacuation");
        check(UrbanStoryProgress.restore(null).isEmpty(), "null save rejected");
        System.out.println("PASS urban story ordering, three approaches, replay and restore checks");
    }
    private static void runChapter(Approach approach) {
        var progress = new UrbanStoryProgress();
        check(progress.chapter() == Chapter.BROADCAST, "fresh chapter");
        check(progress.recordVerified(Objective.STUDENT_WITNESS, approach) == Result.WRONG_CHAPTER, "no chapter skip");
        for (Objective objective : Objective.values()) {
            if (objective == Objective.CALIBRATOR_NODE_A)
                check(progress.recordVerified(Objective.CALIBRATOR_DEFEATED, approach) == Result.MISSING_PREREQUISITES,
                        "finale cannot finish before objectives");
            check(progress.recordVerified(objective, approach) == Result.RECORDED, "record " + objective);
            check(progress.recordVerified(objective, Approach.COMBAT) == Result.ALREADY_RECORDED, "idempotent " + objective);
            check(progress.approach(objective).orElseThrow() == approach, "first route retained");
            var restored = UrbanStoryProgress.restore(progress.snapshot()).orElseThrow();
            check(restored.snapshot().equals(progress.snapshot()) && restored.chapter() == progress.chapter(), "round trip " + objective);
        }
        check(progress.chapter() == Chapter.COMPLETE, "completion");
        check(UrbanStoryProgress.restore(Map.of()).orElseThrow().chapter() == Chapter.BROADCAST, "empty save");
        try { progress.snapshot().clear(); throw new AssertionError("snapshot mutable"); }
        catch (UnsupportedOperationException expected) { /* caller cannot mutate progress */ }
    }
}
