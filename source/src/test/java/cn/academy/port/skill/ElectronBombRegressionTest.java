package cn.academy.port.skill;

import cn.academy.port.core.AbilityProgress;

/** Deterministic server-state checks. This class does not start or bootstrap Minecraft. */
public final class ElectronBombRegressionTest {
    private static int assertions;

    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }

    private static void equal(double expected, double actual, String description) {
        check(Math.abs(expected - actual) < .000001, description + ": " + expected + " != " + actual);
    }

    private static AbilityProgress state(double experience) {
        var state = new AbilityProgress();
        state.selectCategory("meltdowner");
        state.setLevel(1);
        state.learn(ElectronBomb.ID);
        state.experience.put(ElectronBomb.ID, experience);
        state.activated = true;
        return state;
    }

    public static void main(String[] args) {
        var novice = ElectronBomb.cost(0);
        equal(35, novice.cp(), "novice CP");
        equal(16, novice.overload(), "novice overload");
        equal(6, novice.damage(), "novice damage");
        equal(cn.academy.port.core.SkillTuning.number("electron_bomb", "reach"), novice.range(), "novice range");
        equal(20, novice.cooldown(), "novice cooldown");
        var master = ElectronBomb.cost(1);
        equal(80, master.cp(), "master CP");
        equal(13, master.overload(), "master overload");
        equal(12, master.damage(), "master damage");
        equal(cn.academy.port.core.SkillTuning.number("electron_bomb", "reach"), master.range(), "fixed master range");
        equal(10, master.cooldown(), "master cooldown");
        equal(57.5, ElectronBomb.cost(.5).cp(), "interpolated CP");
        equal(35, ElectronBomb.cost(-1).cp(), "low mastery clamp");
        equal(80, ElectronBomb.cost(2).cp(), "high mastery clamp");
        equal(35, ElectronBomb.cost(Double.NaN).cp(), "invalid mastery clamp");
        equal(20, ElectronBomb.lifeTicks(.799999), "strict improved-life threshold below");
        equal(5, ElectronBomb.lifeTicks(.8), "improved-life threshold inclusive");
        equal(18, ElectronBomb.firingDelayTicks(0), "normal life minus two");
        equal(3, ElectronBomb.firingDelayTicks(1), "improved life minus two");

        var state = state(0);
        check(ElectronBomb.mayStart(state), "valid learned root can activate");
        var launch = ElectronBomb.prepare(state, false);
        check(launch != null, "ordinary launch accepted");
        equal(1765, state.cp, "CP spent at launch");
        equal(16, state.overload, "overload spent at launch");
        equal(.0875, state.extraCp, "CP training at launch");
        equal(.0928, state.extraOverload, "overload training at launch");
        equal(.005, state.exp(ElectronBomb.ID), "EXP awarded at launch even without a hit");
        equal(.005, state.levelExperience, "level progression at launch");
        equal(20, state.cooldowns.get(ElectronBomb.ID), "cooldown uses pre-award mastery");
        equal(15, state.cpDelay, "normal CP recovery delay");
        equal(32, state.overloadDelay, "normal overload recovery delay");
        equal(6, launch.cost().damage(), "delayed damage uses captured pre-award mastery");
        check(!launch.ready(-1) && !launch.ready(17), "normal shot cannot fire early");
        check(launch.ready(18), "normal shot ready exactly at callback tick");
        check(ElectronBomb.prepare(state, false) == null, "duplicate request during cooldown rejected");

        state = state(.799);
        launch = ElectronBomb.prepare(state, false);
        equal(20, launch.life(), "crossing threshold on activation does not shorten current ball");
        check(state.exp(ElectronBomb.ID) > .8, "activation crosses mastery threshold");
        state.cooldowns.clear();
        launch = ElectronBomb.prepare(state, false);
        equal(5, launch.life(), "next ball receives improved life");
        check(!launch.ready(2) && launch.ready(3), "improved shot callback tick");

        state = state(1);
        ElectronBomb.prepare(state, false);
        equal(1, state.exp(ElectronBomb.ID), "mastery stays capped");
        equal(.005, state.levelExperience, "capped mastery still trains player level");
        equal(10, state.cooldowns.get(ElectronBomb.ID), "master cooldown");

        state = state(0);
        state.cp = 34;
        check(ElectronBomb.prepare(state, false) == null, "insufficient CP rejects launch");
        equal(34, state.cp, "failed launch leaves CP unchanged");
        equal(0, state.overload, "failed launch adds no overload");
        equal(0, state.extraCp, "failed launch adds no training");
        equal(0, state.exp(ElectronBomb.ID), "failed launch grants no EXP");
        check(state.cooldowns.isEmpty(), "failed launch starts no cooldown");
        state.cp = 0;
        check(ElectronBomb.prepare(state, true) != null, "creative player needs no CP balance");
        equal(0, state.cp, "creative launch does not consume CP");
        equal(0, state.overload, "creative launch does not add overload");
        equal(.0875, state.extraCp, "creative still trains CP");
        equal(.0928, state.extraOverload, "creative still trains overload");

        state = state(0);
        state.overload = state.maxOverload() - 1;
        launch = ElectronBomb.prepare(state, false);
        check(launch != null && !state.overloadFine, "a successful launch may overload the caster");
        check(launch.ready(18), "accepted delayed launch is independent of later canUse gating");

        state = state(0);
        state.category = "electromaster";
        check(!ElectronBomb.mayStart(state), "wrong category rejected even if learned map is corrupt");
        state = state(0);
        state.level = 0;
        check(!ElectronBomb.mayStart(state), "level-zero learned-data anomaly rejected");
        state = state(0);
        state.activated = false;
        check(!ElectronBomb.mayStart(state), "deactivated abilities rejected");
        state = state(0);
        state.interfering = true;
        check(!ElectronBomb.mayStart(state), "interfered abilities rejected");
        state = state(0);
        state.overloadFine = false;
        check(!ElectronBomb.mayStart(state), "overload lock rejected");
        state = state(0);
        state.experience.clear();
        check(!ElectronBomb.mayStart(state), "unlearned skill rejected");

        System.out.println("PASS " + assertions + " deterministic Electron Bomb assertions");
    }
}
