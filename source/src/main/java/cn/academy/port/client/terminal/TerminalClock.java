/* LambdaLib1.2.3 GameTimer client pause semantics. MIT. */
package cn.academy.port.client.terminal;
/** Explicit wall/pause inputs keep source UI time frozen while a single-player menu pauses the game. */
public final class TerminalClock {
    private boolean initialized;private long lastWall,time;
    public long update(long wall,boolean paused){if(!initialized){initialized=true;lastWall=wall;time=wall;}else{if(!paused)time+=Math.max(0,wall-lastWall);lastWall=wall;}return time;}
}
