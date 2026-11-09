/* AcademyCraft 1.0.7 SBContext adaptation. Copyright Lambda Innovation, GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;

import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;

/** Pure captured-mastery context. Termination, including ordinary key-abort, shoots every ball. */
public final class ScatterBombSession {
    public static final String ID = "scatter_bomb";
    public static final int LEVEL = 2, MAX_CHARGE = 80, FIRST_BALL = 20, BALL_INTERVAL = 10, SELF_HURT_TICK = 200;
    public static final int BALL_LIFE = 2333333;
    public static final double RANGE = SkillTuning.number("scatter_bomb", "reach");
    public record Tick(boolean spawnBall, boolean ending, boolean selfHurt) {}
    private final AbilityProgress state;
    private final float mastery, consumption, damage;
    private final double overloadFloor;
    private int ticks, balls;
    private boolean active = true, ending;

    private ScatterBombSession(AbilityProgress state, boolean creative) {
        this.state = state; mastery = mastery(state.exp(ID));
        consumption = consumption(mastery); damage = damage(mastery);
        // Classic ignores consume's return value here; zero CP always succeeds in the default pipeline.
        state.consumeSkill(ID,0, overload(mastery), creative);
        overloadFloor = state.overload;
        ending = !state.overloadFine;
    }
    public static boolean mayStart(AbilityProgress state) {
        return state != null && "meltdowner".equals(state.category) && state.level >= LEVEL && state.canUse(ID);
    }
    public static ScatterBombSession begin(AbilityProgress state, boolean creative) {
        return mayStart(state) ? new ScatterBombSession(state, creative) : null;
    }
    private static float mastery(double exp) { return (float) ClassicRules.clamp(exp, 0, 1); }
    private static float lerp(float from, float to, double exp) { return from + (to - from) * mastery(exp); }
    public static float overload(double exp) { return lerp(80, 60, exp); }
    public static float consumption(double exp) { return lerp(3, 6, exp); }
    public static float damage(double exp) { return lerp(5, 9, exp); }
    public Tick tick(boolean creative) {
        if (!active || ending) return new Tick(false, ending, false);
        if (state.overload < overloadFloor) state.overload = overloadFloor;
        ticks++;
        boolean spawn = ticks <= MAX_CHARGE && ticks >= FIRST_BALL && ticks % BALL_INTERVAL == 0;
        // Ball creation precedes CP consumption, including a failed final consumption.
        if (spawn) balls++;
        if (ticks <= MAX_CHARGE && !state.consumeSkill(ID,consumption, 0, creative)) ending = true;
        boolean selfHurt = ticks == SELF_HURT_TICK;
        if (selfHurt || !state.overloadFine) ending = true;
        return new Tick(spawn, ending, selfHurt);
    }
    /** Called after world attacks, as source s_onEnd adds EXP after iterating its balls. */
    public boolean complete() {
        if (!active) return false;
        active = false; ending = true;
        state.addExperience(ID, .001F * balls);
        return true;
    }
    /** Lifecycle disposal deliberately has no attack/EXP side effects. */
    public void discard() { active = false; ending = true; }
    public boolean active() { return active; }
    public boolean ending() { return ending; }
    public int ticks() { return ticks; }
    public int balls() { return balls; }
    public float damage() { return damage; }
    public float mastery() { return mastery; }
    public double overloadFloor() { return overloadFloor; }
    public AbilityProgress state() { return state; }
}
