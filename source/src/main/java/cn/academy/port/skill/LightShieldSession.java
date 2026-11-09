/* AcademyCraft 1.0.7 LSContext adaptation. Copyright Lambda Innovation, GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;

import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;

/** Literal source quirks: incoming costs reversed, dangling else, inclusive 18-tick absorb gate. */
public final class LightShieldSession {
    public static final String ID = "light_shield";
    public static final int LEVEL = 2, ACTION_INTERVAL = 18, SLOW_TICKS = 100, SLOW_AMPLIFIER = 1;
    public static final float TICK_EXPERIENCE = 1E-6F, ACTION_EXPERIENCE = .001F;
    private final AbilityProgress state;
    private final float mastery, maximumTime, tickCp, touchCp, touchOverload, touchDamage, absorbDamage;
    private final double overloadFloor;
    private int ticks, lastAbsorb = -1;
    private boolean active = true, ending;
    private LightShieldSession(AbilityProgress state, boolean creative) {
        this.state = state; mastery = mastery(state.exp(ID)); maximumTime = maximumTime(mastery);
        tickCp = consumption(mastery); touchCp = actionCp(mastery); touchOverload = actionOverload(mastery);
        touchDamage = touchDamage(mastery); absorbDamage = absorbDamage(mastery);
        state.consumeSkill(ID,0, overload(mastery), creative); overloadFloor = state.overload;
        ending = !state.overloadFine;
    }
    public static boolean mayStart(AbilityProgress state) {
        return state != null && "meltdowner".equals(state.category) && state.level >= LEVEL && state.canUse(ID);
    }
    public static LightShieldSession begin(AbilityProgress state, boolean creative) {
        return mayStart(state) ? new LightShieldSession(state, creative) : null;
    }
    private static float mastery(double exp) { return (float) ClassicRules.clamp(exp, 0, 1); }
    private static float lerp(float from, float to, double exp) { return from + (to - from) * mastery(exp); }
    public static float overload(double exp) { return lerp(110, 60, exp); }
    public static float consumption(double exp) { return lerp(9, 4, exp); }
    public static float maximumTime(double exp) { return lerp((float) SkillTuning.number("light_shield", "duration_ticks_novice"), (float) SkillTuning.number("light_shield", "duration_ticks_mastered"), exp); }
    public static float actionCp(double exp) { return lerp(50, 30, exp); }
    public static float actionOverload(double exp) { return lerp(5, 3, exp); }
    public static float touchDamage(double exp) { return lerp(2, 6, exp); }
    public static float absorbDamage(double exp) { return lerp(15, 50, exp); }
    public static int cooldown(double exp, int ticks) { return (int) lerp(2 * ticks, ticks, exp); }
    /** Source angle compares unnormalized body yaw; vertical distance is intentionally ignored. */
    public static boolean reachable(double dx, double dz, float bodyYaw) {
        double yaw = -(Math.atan2(dx, dz) * 180 / Math.PI);
        return Math.abs(yaw - bodyYaw) % 360 < 60;
    }
    /** Returns true while this tick body may still perform contact actions, even after terminate(). */
    public boolean beginTick(boolean creative) {
        if (!active || ending) return false;
        if (state.overload < overloadFloor) state.overload = overloadFloor;
        ticks++;
        if (ticks > maximumTime) ending = true;
        if (!state.consumeSkill(ID,tickCp,0,creative,()->active)) ending = true;
        state.addExperience(ID, TICK_EXPERIENCE);
        if (!state.overloadFine) ending = true;
        return true;
    }
    /** Source consumes before trying target damage and awards EXP even if attack is canceled. */
    public boolean touch(boolean creative, boolean targetReady) {
        if (!active || !targetReady || !state.consumeSkill(ID,touchCp,touchOverload,creative,()->active)) return false;
        if (!state.overloadFine) ending = true;
        return true;
    }
    public void completeTouch() { if (active) state.addExperience(ID, ACTION_EXPERIENCE); }
    /** getSourceOfDamage is the direct projectile/source entity, not necessarily its owner. */
    public float absorb(float damage, boolean hasDirectSource, boolean creative) {
        if (!active || damage == 0 || lastAbsorb != -1 && ticks - lastAbsorb <= ACTION_INTERVAL) return damage;
        float result = damage;
        // Scala's else binds the inner if: every non-null source performs, including rear/far sources.
        // Null environmental sources do not absorb, but still award the trailing EXP.
        if (hasDirectSource) {
            lastAbsorb = ticks;
            // ctx.consumeSkill(ID,overload, CP) receives (getAbsorbConsumption,getAbsorbOverload).
            if (state.consumeSkill(ID,touchOverload,touchCp,creative,()->active)) result -= Math.min(damage, absorbDamage);
            if (!state.overloadFine) ending = true;
        }
        state.addExperience(ID, ACTION_EXPERIENCE);
        return result;
    }
    /** End cooldown uses the captured context EXP, not current EXP after tick/touch/absorb awards. */
    public boolean complete() {
        if (!active) return false;
        active = false; ending = true; state.setCooldown(ID, cooldown(mastery, ticks)); return true;
    }
    public void discard() { active = false; ending = true; }
    public boolean active() { return active; }
    public boolean ending() { return ending; }
    public int ticks() { return ticks; }
    public int lastAbsorb() { return lastAbsorb; }
    public float touchDamage() { return touchDamage; }
    public float mastery() { return mastery; }
    public double overloadFloor() { return overloadFloor; }
    public AbilityProgress state() { return state; }
}
