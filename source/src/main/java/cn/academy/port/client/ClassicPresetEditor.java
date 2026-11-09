/* Source PresetEditUI page/selector state, separate from server-selected gameplay preset. See NOTICE. */
package cn.academy.port.client;

import cn.academy.port.core.SkillPresets;

/** Pure UI state for repeat-click, transition and cancellation tests without a game client. */
public final class ClassicPresetEditor {
    public static final long TRANSIT_MILLIS = 350;
    public static final double STEP = 125, MAX_ALPHA = 1, MIN_ALPHA = .3, MAX_SCALE = 1, MIN_SCALE = .8;
    private int active, previous, selector = -1;
    private long started;
    private boolean transiting;
    public int browsing() { return active; } // Classic editor opens at page zero, irrespective of gameplay selection.
    public int selectorSlot() { return selector; }
    public boolean transiting(long now) { update(now); return transiting; }
    public boolean click(int preset, int slot, long now) {
        update(now);
        if (transiting || !SkillPresets.validPreset(preset) || !SkillPresets.validSlot(slot)) return false;
        if (selector != -1) { selector = -1; return true; }
        if (preset != active) { previous = active; active = preset; started = now; transiting = true; }
        else selector = slot;
        return true;
    }
    public void closeSelector() { selector = -1; }
    public boolean closeOrCancel() { if (selector == -1) return true; selector = -1; return false; }
    public void close() { selector = -1; transiting = false; }
    private void update(long now) { if (transiting && now - started >= TRANSIT_MILLIS) transiting = false; }
    private double progress(long now) { return transiting ? Math.max(0, Math.min(1, (double)(now-started)/TRANSIT_MILLIS)) : 1; }
    public double x(int page, long now) {
        update(now);double t=progress(now);return transiting ? STEP*((page-previous)*(1-t)+(page-active)*t) : STEP*(page-active);
    }
    public double alpha(int page, long now) { return interpolate(page, now, MAX_ALPHA, MIN_ALPHA); }
    public double scale(int page, long now) { return interpolate(page, now, MAX_SCALE, MIN_SCALE); }
    private double interpolate(int page,long now,double max,double min) {
        update(now);if(!transiting)return page==active?max:min;double t=progress(now);
        return page==previous?max+(min-max)*t:page==active?min+(max-min)*t:min;
    }
}
