/* Source-derived ClientHandler keyActivate: V down displays numbers, short (<300ms) up activates. */
package cn.academy.port.client;

public final class ClassicActivationKey {
    public static final long SHORT_PRESS_MILLIS = 300;
    public static final long NUMBER_DELAY_MILLIS = 200;
    private boolean physical, armed;
    private long started;
    public boolean update(boolean down, boolean inGame, long now) {
        boolean shortRelease = !down && physical && armed && inGame && now - started < SHORT_PRESS_MILLIS && now >= started;
        if (down && !physical) {started = now;armed = inGame;}
        if (!inGame) armed = false;
        physical = down;
        return shortRelease;
    }
    public boolean held() {return physical && armed;}
    public long heldMillis(long now) { return physical && armed ? Math.max(0, now - started) : 0; }
    public boolean displayNumbers(long now) { return physical && armed && heldMillis(now) >= NUMBER_DELAY_MILLIS; }
    /** Replaced players/worlds cannot release an activation begun in another session. */
    public void replaceSession(boolean down, long now) { physical = down; armed = false; started = now; }
}
