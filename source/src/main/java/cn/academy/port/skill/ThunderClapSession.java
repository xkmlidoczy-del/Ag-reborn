/* AcademyCraft1.0.7 ThunderClap.scala and AbilityContext.attackRange adaptation. GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;

import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;

/** Source tick/order arithmetic without any game or client bootstrap. Release is cancellation. */
public final class ThunderClapSession {
    public static final String ID = "thunder_clap";
    public static final int LEVEL = 5, MIN_TICKS = 40, MAX_TICKS = 60;
    public static final double AIM_DISTANCE = SkillTuning.number("thunder_clap", "reach");
    public static final float EXPERIENCE = .003F;
    public enum TickResult { CONTINUE, CANCEL, FIRE, ALREADY_ENDED }
    private final AbilityProgress state;
    private final float mastery;
    private int ticks;
    private boolean active;
    private boolean committed;
    private ThunderClapSession(AbilityProgress state, boolean creative) {
        this.state = state;
        mastery = (float) ClassicRules.clamp(state.exp(ID), 0, 1);
        state.consumeSkill(ID,0, overload(mastery), creative);
        active = mayStart(state); // The original OverloadEvent disposes live contexts.
    }
    public static boolean mayStart(AbilityProgress state) {
        return state != null && "electromaster".equals(state.category) && state.level >= LEVEL && state.canUse(ID);
    }
    public static ThunderClapSession begin(AbilityProgress state, boolean creative) {
        return mayStart(state) ? new ThunderClapSession(state, creative) : null;
    }
    private static float lerp(float from, float to, double mastery) {
        return from + (float) ClassicRules.clamp(mastery, 0, 1) * (to - from);
    }
    public static float overload(double exp) { return lerp(390, 252, exp); }
    public static float consumption(double exp) { return lerp(18, 25, exp); }
    public static float range(double exp) { return lerp((float) SkillTuning.number("thunder_clap", "radius_novice"), (float) SkillTuning.number("thunder_clap", "radius_mastered"), exp); }
    public static float damage(double exp, int ticks) {
        float fraction = (ticks - 40F) / 60F; // Source divisor60, intentionally not20.
        return lerp(36, 72, exp) * (1F + fraction * (1.2F - 1F));
    }
    public static int cooldown(double exp, int ticks) { return (int) (ticks * lerp(10, 6, exp)); }
    /** All entity kinds except caster are queried; sphere membership tests feet inclusively. */
    public static boolean within(double squaredDistance, double range) {
        return Double.isFinite(squaredDistance) && squaredDistance >= 0 && squaredDistance <= range * range;
    }
    /** Float intermediate ordering matches AbilityContext.attackRange and MathUtils.lerpf. */
    public static float radialDamage(float damage, double distance, double range) {
        float ratio = (float) (distance / range);
        float factor = 1F - Math.max(0F, Math.min(1F, ratio));
        return factor * damage;
    }
    public TickResult tick(boolean creative) {
        if (!active) return TickResult.ALREADY_ENDED;
        if (!mayStart(state)) { end(); return TickResult.CANCEL; }
        ticks++;
        boolean insufficient = ticks <= MIN_TICKS && !state.consumeSkill(ID,consumption(mastery), 0, creative);
        if (insufficient || ticks >= MAX_TICKS) {
            active = false;
            return ticks < MIN_TICKS ? TickResult.CANCEL : TickResult.FIRE;
        }
        return TickResult.CONTINUE;
    }
    /** Call only once after authoritative lightning and attack effects. */
    public boolean complete() {
        if (active || committed || ticks < MIN_TICKS) return false;
        committed = true;
        state.setCooldown(ID, cooldown(mastery, ticks));
        state.addExperience(ID, EXPERIENCE);
        return true;
    }
    public void end() { active = false; committed = true; }
    public boolean active() { return active; }
    public int ticks() { return ticks; }
    public float mastery() { return mastery; }
    public AbilityProgress state() { return state; }
}
