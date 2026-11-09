package cn.academy.port.skill;

import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DeveloperType;

/** Catalog checks keep normal learning at zero mastery distinct from operator test grants. */
public final class ThunderBoltLearningRegressionTest {
    private static int assertions;
    private static void check(boolean value, String description) {
        assertions++;
        if (!value) throw new AssertionError(description);
    }
    public static void main(String[] args) {
        var skill = SkillCatalog.find("electromaster", ThunderBoltRules.ID).orElseThrow();
        var railgun = SkillCatalog.find("electromaster", "railgun").orElseThrow();
        var state = new AbilityProgress();
        state.selectCategory("electromaster"); state.setLevel(4);
        check(!SkillCatalog.canLearn(state, skill), "ThunderBolt requires learned ArcGen and Charging");
        state.learn("arc_gen");
        check(!SkillCatalog.canLearn(state, skill), "ArcGen alone insufficient");
        state.learn("charging"); state.experience.put("charging", Math.nextDown(.7));
        check(SkillCatalog.canLearn(state, skill), "double-only Charging predecessor rounds to source .7f and accepts");
        state.experience.put("charging", (double)Math.nextDown(.7f));
        check(!SkillCatalog.canLearn(state, skill), "Charging mastery below .7 insufficient");
        state.experience.put("charging", .7);
        check(SkillCatalog.canLearn(state, skill), "ArcGen learned plus Charging .7 unlocks level-four learning");
        check(!DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill), "portable developer cannot teach level four");
        check(!DevelopmentActions.canLearn(state, DeveloperType.NORMAL, skill), "normal developer cannot teach level four");
        check(DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill), "advanced developer can teach ThunderBolt");
        check(DevelopmentActions.skill(state, skill).stimulations() == 11, "normal learning requires eleven stimulations");
        state.setLevel(3);
        check(!SkillCatalog.canLearn(state, skill), "level-three player cannot learn level-four skill");
        state.setLevel(4); state.learn(ThunderBoltRules.ID); state.activated = true;
        check(state.exp(ThunderBoltRules.ID) == 0, "naturally learned ThunderBolt starts at zero mastery");
        check(ThunderBoltRules.canUse(state), "naturally learned zero-mastery skill casts");
        check(!SkillCatalog.canLearn(state, skill), "already learned skill cannot learn twice");
        state.learn("mag_manip"); state.experience.put("mag_manip", 1.0);
        check(!SkillCatalog.canLearn(state, railgun), "natural ThunderBolt zero mastery does not unlock Railgun");
        state.experience.put(ThunderBoltRules.ID, Math.nextDown(.3));
        check(SkillCatalog.canLearn(state, railgun), "double-only ThunderBolt predecessor rounds to source .3f and accepts");
        state.experience.put(ThunderBoltRules.ID, (double)Math.nextDown(.3f));
        check(!SkillCatalog.canLearn(state, railgun), "ThunderBolt below .3 insufficient for Railgun");
        state.experience.put(ThunderBoltRules.ID, .3);
        check(SkillCatalog.canLearn(state, railgun), "ThunderBolt .3 plus MagManip1 unlocks Railgun");
        state.experience.put(ThunderBoltRules.ID, 1.0);
        check(state.exp(ThunderBoltRules.ID) == 1, "operator-style mastery is a distinct test state");
        System.out.println("PASS " + assertions + " ThunderBolt natural-learning/catalog assertions");
    }
}
