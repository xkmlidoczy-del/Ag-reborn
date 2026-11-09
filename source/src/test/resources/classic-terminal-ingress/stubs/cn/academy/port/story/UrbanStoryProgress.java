package cn.academy.port.story;
public record UrbanStoryProgress(Chapter chapter, boolean leadComplete) {
 public enum Chapter { BROADCAST, STUDENT, PROTOCOL, CALIBRATOR, COMPLETE }
 public enum Objective { BROADCAST_LEAD }
 public boolean completed(Objective objective) { return objective == Objective.BROADCAST_LEAD && leadComplete; }
}
