package cn.academy.port.client;

/** Immutable identity of the level-up shown by one confirmation/completion cover. */
public record DeveloperLevelAttempt(int targetLevel) {
    public DeveloperLevelAttempt {
        if(targetLevel<1||targetLevel>5)throw new IllegalArgumentException("Development target must be LV1 through LV5");
    }
    public static DeveloperLevelAttempt capture(int currentLevel){return new DeveloperLevelAttempt(currentLevel+1);}
    public int stimulations(){return 5*targetLevel;}
    public boolean canStartAt(int currentLevel){return currentLevel+1==targetLevel;}
}
