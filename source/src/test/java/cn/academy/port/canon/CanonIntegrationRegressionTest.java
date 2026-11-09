package cn.academy.port.canon;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.highpower.HighPowerCategories;
import cn.academy.port.highpower.HighPowerLearningProtocol;
import cn.academy.port.highpower.HighPowerSkills;
import cn.academy.port.preset.PresetSkills;

/** Exercises enabled/disabled launch modes in separate JVMs. */
public final class CanonIntegrationRegressionTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        boolean expected = args.length == 1 && Boolean.parseBoolean(args[0]);
        check(CanonSkills.EXPERIMENTAL == expected, "launch mode");
        check(SkillCatalog.ALL.size() == 50 && PresetSkills.IMPLEMENTED.size() == 35, "classic catalog remains intact");
        check(DevelopmentActions.CATEGORIES.subList(0, 4).equals(java.util.List.of("electromaster", "meltdowner", "teleporter", "vecmanip")), "classic acquisition order");
        var state = new AbilityProgress();
        state.selectCategory(CanonSkills.MENTAL);
        state.setLevel(3);
        state.learn("mental_probe");
        state.setActivateState(true);
        check(state.presets.edit(0, 0, "mental_probe", ignored -> true), "fixture preset saved");
        var loaded = AbilityStorage.decode(AbilityStorage.encodeSaved(state));
        check(loaded.category.equals(CanonSkills.MENTAL) && loaded.level == 3 && loaded.learned("mental_probe"), "known experimental skill survives save in both modes");
        check(loaded.presets.skill(0, 0).equals("mental_probe"), "experimental mapping survives disabled mode");
        check(SkillCatalog.levelSkillCount(loaded) == 1, "new-category level threshold is nonzero");
        check(DevelopmentActions.CATEGORIES.contains(CanonSkills.MENTAL) == expected, "experimental acquisition gate");
        check(HighPowerCategories.active(loaded) == expected, "experimental high-power gate");
        check(HighPowerSkills.contains("mental_probe") == expected, "cast and learning registry gate");
        check(PresetSkills.mappedUsable(loaded, "mental_probe") == expected, "preset input gate");
        var next = SkillCatalog.find(CanonSkills.MENTAL, "mental_disrupt").orElseThrow();
        check(SkillCatalog.canLearn(loaded, next) == expected, "learning gate and dependency");
        check(DevelopmentActions.canLearn(loaded, DeveloperType.NORMAL, next) == expected, "developer tier and gate");
        if (expected) {
            var req = new HighPowerLearningProtocol.Request("mental_probe", 1);
            check(HighPowerLearningProtocol.parse(req.wire()) != null, "learning request accepts new skill");
            check(HighPowerSkills.eligible(loaded, "mental_probe"), "learned skill satisfies shared high-power admission");
            check(SkillCatalog.REGISTERED.stream().filter(s -> CanonSkills.knownCategory(s.category())).count() == CanonSkills.ALL.size(), "new catalog entries included exactly once");
            check(CanonSkills.CATEGORIES.size() == 8 && CanonSkills.ALL.size() >= 25, "eight categories retain their original implemented paths");
            for (String category : CanonSkills.CATEGORIES) {
                var nodes = cn.academy.port.client.UnifiedSkillTreeLayout.category(category);
                check(nodes.size() == CanonSkills.ALL.stream().filter(s -> s.category().equals(category)).count(), "every registered skill has a tree node for " + category);
                for (var skill : CanonSkills.ALL) if (skill.category().equals(category)) {
                    boolean routed = CanonOffenseSkills.find(skill.id()) != null || CanonExpansionA.contains(skill.id()) || CanonLiquidCombatExpansion.contains(skill.id())
                            || CanonWaterExpansion.contains(skill.id()) || CanonFloatExpansion.contains(skill.id())
                            || CanonSkillsCombatA.IMPLEMENTED.contains(skill.id())
                            || CanonSkillsCombatB.IMPLEMENTED.contains(skill.id())
                            || CanonFloatItems.IMPLEMENTED.contains(skill.id())
                            || CanonWaterSkills.IMPLEMENTED.contains(skill.id())
                            || CanonPaperSkills.IMPLEMENTED.contains(skill.id())
                            || CanonLiquidSkills.IMPLEMENTED.contains(skill.id());
                    check(routed, "registered skill has controller " + skill.id());
                    String asset = "/assets/academy/textures/skill_icons_v2/" + category + "/" + skill.id() + ".png";
                    check(CanonIntegrationRegressionTest.class.getResource(asset) != null, "skill icon " + skill.id());
                }
            }
        } else {
            check(!SkillCatalog.enabled(CanonSkills.MENTAL, "mental_probe"), "disabled skill cannot be cast");
        }
        var roots = java.util.Map.of(CanonSkills.MENTAL, "mental_probe", CanonSkills.AERO, "aero_jet",
                CanonSkills.NITROGEN, "nitrogen_armor", CanonSkills.DARK, "dark_blade",
                CanonSkills.FLOAT, "float_lift", CanonSkills.HYDRO, "hydro_whip",
                CanonSkills.PAPER, "paper_blade", CanonSkills.LIQUID, "liquid_scout");
        for (var entry : roots.entrySet()) {
            var saved = new AbilityProgress(); saved.selectCategory(entry.getKey()); saved.setLevel(1); saved.learn(entry.getValue());
            check(saved.presets.edit(0, 0, entry.getValue(), ignored -> true), "save root fixture");
            var restored = AbilityStorage.decode(AbilityStorage.encodeSaved(saved));
            check(restored.category.equals(entry.getKey()) && restored.learned(entry.getValue())
                    && restored.presets.skill(0, 0).equals(entry.getValue()), "root and preset retained " + entry.getKey());
        }
        System.out.println("PASS canon integration, experimental=" + expected);
    }
}
