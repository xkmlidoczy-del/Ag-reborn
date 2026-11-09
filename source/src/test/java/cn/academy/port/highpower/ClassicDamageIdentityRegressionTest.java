package cn.academy.port.highpower;

import cn.academy.port.SkillCatalog;

/** Executes the production identity gate against the complete real source and extension catalogs. */
public final class ClassicDamageIdentityRegressionTest {
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    public static void main(String[] args) {
        for (var skill : SkillCatalog.ALL) {
            check(ClassicDamageScaleRuntime.isClassicSkill(skill.category(), skill.id()), "classic identity retains lift: " + skill);
            check(!ClassicDamageScaleRuntime.isClassicSkill("unknown", skill.id()), "another category cannot borrow a classic lift");
        }
        for (var skill : HighPowerSkills.ALL)
            check(!ClassicDamageScaleRuntime.isClassicSkill(skill.category(), skill.id()), "closed extension budget is never lifted: " + skill);
        for (String suffix : new String[] {"trajectory_return_air", "trajectory_return_native", "short_interception_native", "railgun_air", "railgun_native", "unknown"})
            for (String category : new String[] {"electromaster", "meltdowner", "teleporter", "vecmanip"})
                check(!ClassicDamageScaleRuntime.isClassicSkill(category, suffix), "unregistered internal identity cannot claim classic scaling: " + category + "." + suffix);
        check(ClassicDamageScaleRuntime.isClassicSkill("electromaster", "railgun"), "original advanced railgun retains classic identity");
        check(!ClassicDamageScaleRuntime.isClassicSkill(null, "railgun") && !ClassicDamageScaleRuntime.isClassicSkill("electromaster", null), "absent identity has no lift");
        System.out.println("Classic damage identity regression: " + checks + " checks passed");
    }
}
