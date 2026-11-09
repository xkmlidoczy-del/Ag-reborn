/* AcademyCraft1.0.7 BodyIntensify/IntensifyContext adaptation. GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;

import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;

/** Active held context, not a passive. Preserve source's discarded Random.shuffle result. */
public final class BodyIntensifySession {
    public static final String ID = "body_intensify";
    public static final int LEVEL = 3, MIN_TIME = 10, MAX_TIME = 40, MAX_TOLERANT_TIME = 100;
    public static final float EXPERIENCE = .01F;
    public enum Effect { SPEED, JUMP, REGENERATION, STRENGTH, RESISTANCE }
    public record Buff(Effect effect, int duration, int amplifier) {}
    public record Release(List<Buff> buffs, int hungerDuration, int hungerAmplifier, int chargeTicks) {}
    private final AbilityProgress state;
    private final float consumption;
    private final double overloadFloor;
    private int ticks;
    private boolean active;
    private boolean released;
    private boolean committed;
    private BodyIntensifySession(AbilityProgress state, boolean creative) {
        this.state = state;
        consumption = consumption(state.exp(ID)); // Captured at context construction.
        state.consumeSkill(ID,0, overload(state.exp(ID)), creative);
        overloadFloor = state.overload;
        active = mayStart(state);
    }
    public static boolean mayStart(AbilityProgress state) {
        return state != null && "electromaster".equals(state.category) && state.level >= LEVEL && state.canUse(ID);
    }
    public static BodyIntensifySession begin(AbilityProgress state, boolean creative) {
        return mayStart(state) ? new BodyIntensifySession(state, creative) : null;
    }
    private static float mastery(double experience) { return (float) ClassicRules.clamp(experience, 0, 1); }
    private static float lerp(float from, float to, double experience) { return from + mastery(experience) * (to - from); }
    public static float overload(double exp) { return lerp(200, 120, exp); }
    public static float consumption(double exp) { return lerp(20, 15, exp); }
    public static double probability(int ticks) { return (ticks - 10.0) / 18.0; }
    public static int buffLevel(int ticks) { return (int) Math.floor(probability(ticks)); }
    public static int buffDuration(double exp, int ticks, double sample) {
        // RandUtils.ranged(1,2) and lerp(1.5,2.5,Float) both use Double arithmetic.
        return (int) ((1 + sample) * ticks * (SkillTuning.number("body_intensify", "buff_duration_multiplier_novice") + (SkillTuning.number("body_intensify", "buff_duration_multiplier_mastered") - SkillTuning.number("body_intensify", "buff_duration_multiplier_novice")) * mastery(exp)));
    }
    public static int hungerDuration(int ticks) { return (int) (1.25F * ticks); }
    public static int cooldown(double exp) { return (int) lerp(900, 600, exp); }
    public boolean tick(boolean creative) {
        if (!active) return false;
        if (!mayStart(state)) { end(); return false; }
        if (state.overload < overloadFloor) state.overload = overloadFloor;
        ticks++;
        if ((ticks <= MAX_TIME && !state.consumeSkill(ID,consumption, 0, creative)) || ticks >= MAX_TOLERANT_TIME) {
            end(); return false;
        }
        return true;
    }
    /** Random source is server-owned; wire ingress supplies only authenticated slot/key transitions. */
    public Release release(DoubleSupplier random) {
        Objects.requireNonNull(random);
        if (!active || !mayStart(state)) { end(); return null; }
        active = false;
        if (ticks < MIN_TIME) { committed = true; return null; }
        int charge = Math.min(ticks, MAX_TIME);
        double p = probability(charge);
        int i = 0;
        int duration = buffDuration(state.exp(ID), charge, sample(random));
        var buffs = new ArrayList<Buff>();
        while (p > 0) {
            double roll = sample(random);
            if (roll < p) {
                // Scala Random.shuffle(effects) returned an unused Vector; i is preincremented.
                // Actual source selection is JUMP then REGENERATION. SPEED is never selected.
                i++;
                Effect effect = Effect.values()[i];
                buffs.add(new Buff(effect, duration, Math.min(buffLevel(charge), effect == Effect.SPEED ? 3 : 1)));
            }
            p -= 1;
        }
        released = true;
        return new Release(List.copyOf(buffs), hungerDuration(charge), 2, charge);
    }
    private static double sample(DoubleSupplier source) {
        double value = source.getAsDouble();
        if (!Double.isFinite(value) || value < 0 || value >= 1) throw new IllegalArgumentException("random sample must be in [0,1)");
        return value;
    }
    /** Source adds EXP BEFORE deriving cooldown from the new current mastery. */
    public boolean complete() {
        if (!released || committed) return false;
        committed = true;
        state.addExperience(ID, EXPERIENCE);
        state.setCooldown(ID, cooldown(state.exp(ID)));
        return true;
    }
    public void end() { active = false; committed = true; }
    public boolean active() { return active; }
    public int ticks() { return ticks; }
    public double overloadFloor() { return overloadFloor; }
    public AbilityProgress state() { return state; }
}
