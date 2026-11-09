/* AcademyCraft 1.0.7 VecAccelContext adaptation. Copyright Lambda Innovation, GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;

import cn.academy.port.core.AbilityProgress;

/** Pure classic charge/debit state. Transport owns elapsed ticks, ground trace and previous body aim. */
public final class VecAccelSession {
    public static final String ID = "vec_accel";
    public static final int LEVEL = 2, MAX_CHARGE = 20;
    public static final double MAX_VELOCITY = SkillTuning.number("vec_accel", "speed_coefficient");
    private final AbilityProgress state;
    private final float consumption;
    private final boolean ignoreGround;
    private int ticks;
    // Source canPerform is initially true until the first local context tick.
    private boolean active = true, canPerform = true;
    private VecAccelSession(AbilityProgress state) {
        this.state = state; consumption = cp(state.exp(ID)); ignoreGround = (float) state.exp(ID) > .5F;
    }
    public static boolean mayStart(AbilityProgress state) {
        return state != null && state.category.equals("vecmanip") && state.level >= LEVEL && state.canUse(ID)
                && Double.isFinite(state.cp) && Double.isFinite(state.overload) && Double.isFinite(state.exp(ID));
    }
    public static VecAccelSession begin(AbilityProgress state) { return mayStart(state) ? new VecAccelSession(state) : null; }
    public static float lerp(float a, float b, double e) { return a + (float) e * (b - a); }
    public static float cp(double e) { return lerp(120, 80, e); }
    public static float overload(double e) { return lerp(30, 15, e); }
    public static int cooldown(double e) { return (int) lerp(80, 50, e); }
    /** No min release, timeout or invented linear speed: source takes sine of progress in radians. */
    public static double speed(int ticks) {
        return Math.sin(.4 + Math.max(0, Math.min(1, ticks / 20.0)) * .6) * MAX_VELOCITY;
    }
    public void tick(boolean groundWithinTwo) {
        if (!active) return;
        if (ticks < Integer.MAX_VALUE) ticks++;
        canPerform = ignoreGround || groundWithinTwo;
    }
    public boolean release(boolean creative) {
        if (!active) return false;
        active = false;
        if (!canPerform || !state.consumeSkill(ID,consumption, overload(state.exp(ID)), creative)) return false;
        state.setCooldown(ID, cooldown(state.exp(ID)));
        state.addExperience(ID, .002F);
        return true;
    }
    public void discard() { active = false; }
    public AbilityProgress state() { return state; }
    public boolean active() { return active; }
    public boolean canPerform() { return canPerform; }
    public int ticks() { return ticks; }
    public float consumptionHint() { return consumption; }
    public boolean ignoreGround() { return ignoreGround; }
}
