/* AcademyCraft 1.0.7 MineDetect numeric/state rules. GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;

import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;

/** Pure stock-source formulas; no world/client initialization. */
public final class MineDetectRules {
    public static final String ID = "mine_detect";
    public static final int LEVEL = 3, TIME = SkillTuning.integer("mine_detect", "duration_ticks");
    public static final float EXPERIENCE = .008F;
    private MineDetectRules() {}

    public static float experience(double value) { return (float) ClassicRules.clamp(value, 0, 1); }
    private static float lerpf(float a, float b, float t) { return a + t * (b - a); }
    public static float range(double exp) { return lerpf((float) SkillTuning.number("mine_detect", "reach_novice"), (float) SkillTuning.number("mine_detect", "reach_mastered"), experience(exp)); }
    public static boolean advanced(double exp, int level) { return experience(exp) > .5F && level >= 4; }
    public static int cooldown(double postAwardExp) { return (int) lerpf(900F, 400F, experience(postAwardExp)); }
    public static ClassicRules.SkillCost cost(double exp) {
        float e = experience(exp);
        return new ClassicRules.SkillCost(lerpf(1500F, 1000F, e), lerpf(200F, 180F, e), 0,
                range(e), cooldown(e));
    }
    public static boolean canUse(AbilityProgress state) {
        return state != null && "electromaster".equals(state.category)
                && state.level >= LEVEL && state.canUse(ID);
    }
    public static Plan prepare(AbilityProgress state, boolean creative) {
        if (!canUse(state)) return null;
        float captured = experience(state.exp(ID));
        var c = cost(captured);
        if (!state.consumeSkill(ID,c.cp(), c.overload(), creative)) return null;
        return new Plan(captured, range(captured), advanced(captured, state.level), c);
    }
    /** AbilityData adds Float min(1-old,amount), even near the cap; cooldown reads afterward. */
    public static int complete(AbilityProgress state, Plan plan) {
        awardExperience(state, plan);
        if (state == null || plan == null) return 0;
        int ticks = cooldown(state.exp(ID));
        state.setCooldown(ID, ticks);
        return ticks;
    }
    public static void awardExperience(AbilityProgress state, Plan plan) {
        if (state == null || plan == null || !state.learned(ID)) return;
        float old = experience(state.exp(ID));
        float after = old + Math.min(1F - old, EXPERIENCE);
        state.experience.put(ID, (double) after);
        state.levelExperience += (double) EXPERIENCE;

    }
    public record Plan(float experience, float range, boolean advanced, ClassicRules.SkillCost cost) {}
}
