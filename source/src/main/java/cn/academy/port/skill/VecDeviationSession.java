/* AcademyCraft 1.0.7 VecDeviationContext adaptation. Copyright Lambda Innovation, GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.AbilityProgress;

/** Two server debits, forced fixed entity cost, dynamic post-award reduction and deferred termination. */
public final class VecDeviationSession {
    public static final String ID = "vec_deviation";
    public static final int LEVEL = 2;
    private final AbilityProgress state;
    private final float tickCp, entityCp;
    private final double overloadFloor;
    private boolean active = true, ending;
    private int ticks;
    private VecDeviationSession(AbilityProgress state, boolean creative) {
        this.state = state;
        tickCp = tickCp(state.exp(ID)); entityCp = entityCp(state.exp(ID));
        state.consumeSkill(ID,0, initialOverload(state.exp(ID)), creative);
        overloadFloor = state.overload;
    }
    public static boolean mayStart(AbilityProgress state) {
        return state != null && state.category.equals("vecmanip") && state.level >= LEVEL && state.canUse(ID)
                && Double.isFinite(state.cp) && Double.isFinite(state.overload) && Double.isFinite(state.exp(ID));
    }
    public static VecDeviationSession begin(AbilityProgress state, boolean creative) {
        return mayStart(state) ? new VecDeviationSession(state, creative) : null;
    }
    private static float lerp(float a, float b, double e) { return a + (float) e * (b - a); }
    public static float initialOverload(double e) { return lerp(80, 50, e); }
    public static float tickCp(double e) { return lerp(13, 5, e); }
    public static float entityCp(double e) { return lerp(15, 12, e); }
    public static float normalCp(double e) { return lerp(5, 2.5F, e); }
    public static float normalOverload(double e) { return lerp(.5F, .2F, e); }
    public static float reduction(double e) { return lerp(.4F, .9F, e); }
    /** Source terminate() marks ending but does not return before scanning entities or g_tick. */
    public boolean beginTick(boolean creative) {
        if (!active || ending) return false;
        ticks++;
        if (!state.consumeSkill(ID,tickCp,0,creative,()->active)) ending = true;
        if (state.overload < overloadFloor) state.overload = overloadFloor;
        return true;
    }
    /** Difficulty affects EXP only; source spelling comsumption is a captured fixed debit. */
    public void affect(float difficulty, boolean creative) {
        if (!active || !Float.isFinite(difficulty) || difficulty < 0) return;
        // CPData.performWithForce clamps at zero, but trains against the full requested cost.
        if(!state.consumeWithForceSkill(ID,entityCp,0,creative,()->active))return;
        state.addExperience(ID, .001F * difficulty);
    }
    /** Source g_tick is separately registered on server AND local client; server applies it once. */
    public void endTick(boolean creative) {
        if (!active) return;
        state.consumeSkill(ID,normalCp(state.exp(ID)),normalOverload(state.exp(ID)),creative,()->active);
        if (!state.overloadFine) ending = true;
    }
    public float reduceDamage(float damage, boolean creative) {
        if (!active || state.consumptionInProgress() || !Float.isFinite(damage) || damage < 0) return damage;
        float consumption = Math.min((float) state.cp, entityCp(state.exp(ID)));
        if(!state.consumeSkill(ID,consumption,0,creative,()->active))return damage;
        state.addExperience(ID, damage * .0006F);
        // getSkillExp is read AFTER the award in executable source.
        return damage * (1 - reduction(state.exp(ID)));
    }
    public void discard() { active = false; ending = true; }
    public AbilityProgress state() { return state; }
    public int ticks() { return ticks; }
    public boolean active() { return active; }
    public boolean ending() { return ending; }
    public double overloadFloor() { return overloadFloor; }
}
