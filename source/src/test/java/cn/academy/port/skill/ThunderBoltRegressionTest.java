package cn.academy.port.skill;

import cn.academy.port.core.AbilityProgress;

/** Pure server rules/state checks; does not bootstrap or launch Minecraft. */
public final class ThunderBoltRegressionTest {
    private static int assertions;
    private static void check(boolean value, String description) {
        assertions++;
        if (!value) throw new AssertionError(description);
    }
    private static void equal(double expected, double actual, String description) {
        check(Double.isFinite(actual) && Math.abs(expected - actual) < 1.0E-6,
                description + ": " + expected + " != " + actual);
    }
    private static AbilityProgress state(double experience) {
        var state = new AbilityProgress();
        state.selectCategory("electromaster");
        state.setLevel(4);
        state.learn(ThunderBoltRules.ID);
        state.experience.put(ThunderBoltRules.ID, experience);
        state.activated = true;
        return state;
    }
    public static void main(String[] args) {
        var novice = ThunderBoltRules.cost(0);
        equal(280, novice.cp(), "novice CP"); equal(50, novice.overload(), "novice overload");
        equal(10, novice.damage(), "novice primary damage"); equal(cn.academy.port.core.SkillTuning.number("thunder_bolt", "reach"), novice.range(), "configured range");
        equal(120, novice.cooldown(), "novice cooldown"); equal(6, ThunderBoltRules.aoeDamage(0), "novice AOE damage");
        var master = ThunderBoltRules.cost(1);
        equal(420, master.cp(), "master CP"); equal(27, master.overload(), "master overload");
        equal(25, master.damage(), "master primary damage"); equal(50, master.cooldown(), "master cooldown");
        equal(15, ThunderBoltRules.aoeDamage(1), "master AOE damage");
        equal(350, ThunderBoltRules.cost(.5).cp(), "midpoint integer CP");
        equal(38.5, ThunderBoltRules.cost(.5).overload(), "midpoint overload");
        equal(17.5, ThunderBoltRules.cost(.5).damage(), "midpoint primary damage");
        equal(10.5, ThunderBoltRules.aoeDamage(.5), "midpoint AOE damage");
        equal(85, ThunderBoltRules.cost(.5).cooldown(), "midpoint cooldown");
        equal(280, ThunderBoltRules.cost(Double.NaN).cp(), "NaN mastery is safely clamped");
        equal(280, ThunderBoltRules.cost(-1).cp(), "negative mastery clamped");
        equal(420, ThunderBoltRules.cost(2).cp(), "high mastery clamped");
        for (int i = 0; i <= 1000; i++) {
            double experience = i / 1000.0;
            float captured = (float) experience;
            var cost = ThunderBoltRules.cost(experience);
            equal((int) (280 + (double) captured * 140), cost.cp(), "source CP truncation " + i);
            equal(50F + captured * -23F, cost.overload(), "float overload " + i);
            equal(10F + captured * 15F, cost.damage(), "float primary damage " + i);
            equal(6F + captured * 9F, ThunderBoltRules.aoeDamage(experience), "float AOE damage " + i);
            equal((int) (120F + captured * -70F), cost.cooldown(), "float cooldown truncation " + i);
        }
        check(!ThunderBoltRules.rollsSlowdown(Math.nextDown(.2F)), "float below threshold is ineligible");
        check(ThunderBoltRules.rollsSlowdown(.2F), "Float .2 is greater than the source Double .2 literal");
        check(!ThunderBoltRules.slowdownSucceeds(0, 0), "novice never slows");
        check(ThunderBoltRules.slowdownSucceeds(1, Math.nextDown(.8)), "chance strictly below .8 succeeds");
        check(!ThunderBoltRules.slowdownSucceeds(1, .8), "chance exactly .8 fails");
        check(!ThunderBoltRules.slowdownSucceeds(1, Double.NaN), "invalid random sample rejected");
        equal(40, ThunderBoltRules.DIRECT_SLOW_TICKS, "direct debuff ticks");
        equal(20, ThunderBoltRules.AOE_SLOW_TICKS, "AOE wrong-target debuff ticks");
        equal(3, ThunderBoltRules.SLOW_AMPLIFIER, "Slowness IV amplifier");

        check(ThunderBoltRules.withinAoe(64), "radius boundary included using feet distance");
        check(!ThunderBoltRules.withinAoe(Math.nextUp(64.0)), "outside sphere excluded");
        check(!ThunderBoltRules.withinAoe(8 * 8 + 8 * 8), "AABB corner is outside sphere");
        check(!ThunderBoltRules.withinAoe(Double.NaN), "nonfinite distance rejected");
        var offset = ThunderBoltRules.missOffset(3, 4, 0);
        equal(ThunderBoltRules.RANGE * .6, offset.x(), "moving miss uses normalized velocity X");
        equal(ThunderBoltRules.RANGE * .8, offset.y(), "moving miss uses normalized velocity Y");
        equal(0, offset.z(), "moving miss Z");
        offset = ThunderBoltRules.missOffset(0, 0, 0);
        equal(0, offset.x(), "stationary miss finite X"); equal(0, offset.y(), "stationary miss finite Y");
        equal(0, offset.z(), "stationary miss finite Z");
        equal(0, ThunderBoltRules.missOffset(Double.NaN, 1, 0).x(), "invalid velocity finite guard");
        equal(ThunderBoltRules.RANGE, ThunderBoltRules.missOffset(1.0E-300, 0, 0).x(), "small finite movement normalizes safely");

        var state = state(0);
        double beforeCp = state.cp;
        var plan = ThunderBoltRules.prepare(state, false);
        check(plan != null, "learned level-four cast accepted");
        equal(beforeCp - 280, state.cp, "CP spent immediately"); equal(50, state.overload, "overload spent immediately");
        equal(.7, state.extraCp, "CP training"); equal(.29, state.extraOverload, "overload training");
        equal(0, state.exp(ThunderBoltRules.ID), "EXP awaits authoritative target classification");
        check(state.cooldowns.isEmpty(), "cooldown follows attack and classification");
        equal(15, state.cpDelay, "CP recovery delay"); equal(32, state.overloadDelay, "overload recovery delay");
        ThunderBoltRules.complete(state, plan, false, 0);
        equal(.003, state.exp(ThunderBoltRules.ID), "no selected entities gives miss EXP");
        equal(.003, state.levelExperience, "miss trains level"); equal(120, state.cooldowns.get(ThunderBoltRules.ID), "captured cooldown after miss");
        check(ThunderBoltRules.prepare(state, false) == null, "duplicate request blocked by cooldown");

        state = state(.199);
        plan = ThunderBoltRules.prepare(state, false);
        ThunderBoltRules.complete(state, plan, true, 3);
        equal(.204, state.exp(ThunderBoltRules.ID), "one EXP award for direct plus multiple AOE targets");
        check(!ThunderBoltRules.rollsSlowdown(plan.experience()), "current cast retains old mastery after crossing threshold");
        equal(ThunderBoltRules.cost(.199).cooldown(), state.cooldowns.get(ThunderBoltRules.ID), "pre-award cooldown");
        state = state(0);
        plan = ThunderBoltRules.prepare(state, false);
        ThunderBoltRules.complete(state, plan, false, 1);
        equal(.005, state.exp(ThunderBoltRules.ID), "AOE-only cast is effective");
        state = state(1);
        plan = ThunderBoltRules.prepare(state, false);
        ThunderBoltRules.complete(state, plan, true, 0);
        equal(1, state.exp(ThunderBoltRules.ID), "mastery capped"); equal(.005, state.levelExperience, "mastery-cap cast still trains level");
        equal(50, state.cooldowns.get(ThunderBoltRules.ID), "master cooldown");

        state = state(0); state.cp = 279;
        check(ThunderBoltRules.prepare(state, false) == null, "insufficient CP rejects cast");
        equal(279, state.cp, "failed cast consumes no CP"); equal(0, state.overload, "failed cast adds no overload");
        check(state.cooldowns.isEmpty(), "failed cast starts no cooldown"); equal(0, state.extraCp, "failed cast adds no training");
        state.cp = 0;
        plan = ThunderBoltRules.prepare(state, true);
        check(plan != null, "creative cast skips CP balance requirement");
        equal(0, state.cp, "creative no CP consumption"); equal(0, state.overload, "creative no overload consumption");
        equal(.7, state.extraCp, "creative still trains");
        state = state(0); state.overload = state.maxOverload() - 1;
        plan = ThunderBoltRules.prepare(state, false);
        check(plan != null && !state.overloadFine, "accepted cast may cross overload cap");
        ThunderBoltRules.complete(state, plan, true, 0);
        equal(.005, state.exp(ThunderBoltRules.ID), "accepted cast completes despite resulting overload lockout");

        check(!ThunderBoltRules.canUse(null), "null state rejected");
        state = state(0); state.category = "meltdowner"; check(!ThunderBoltRules.canUse(state), "wrong category rejected");
        state = state(0); state.level = 3; check(!ThunderBoltRules.canUse(state), "level-three learned-data anomaly rejected");
        state = state(0); state.activated = false; check(!ThunderBoltRules.canUse(state), "deactivated abilities rejected");
        state = state(0); state.interfering = true; check(!ThunderBoltRules.canUse(state), "interference rejected");
        state = state(0); state.overloadFine = false; check(!ThunderBoltRules.canUse(state), "overload lock rejected");
        state = state(0); state.experience.clear(); check(!ThunderBoltRules.canUse(state), "unlearned skill rejected");
        System.out.println("PASS " + assertions + " ThunderBolt numeric/state assertions (no game bootstrap)");
    }
}
