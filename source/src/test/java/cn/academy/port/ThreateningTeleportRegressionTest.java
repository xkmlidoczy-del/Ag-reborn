package cn.academy.port;

import cn.academy.port.core.AbilityProgress;
import cn.academy.port.skill.ThreateningTeleport;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Deterministic source-derived assertions. No Minecraft server, client or GameTest is started. */
public final class ThreateningTeleportRegressionTest {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static void eq(double expected, double actual) {
        check(Double.isFinite(actual) && Math.abs(expected - actual) < .000001, expected + " != " + actual);
    }
    private static AbilityProgress ready() {
        var state = new AbilityProgress();
        state.selectCategory("teleporter");
        state.setLevel(1);
        state.learn(ThreateningTeleport.ID);
        state.activated = true;
        return state;
    }
    private static int roll(AbilityProgress state, double... rolls) {
        int[] index = {0};
        return ThreateningTeleport.criticalTier(state, () -> rolls[index[0]++]);
    }
    public static void main(String[] args) {
        for (double exp : new double[]{0, .1, .5, .9, 1}) {
            var cost = ThreateningTeleport.rules(exp);
            eq(35 + 65 * exp, cost.cp());
            eq(18 - 8 * exp, cost.overload());
            eq(3 + 3 * exp, cost.damage());
            eq((cn.academy.port.core.SkillTuning.number("threatening_teleport", "reach_novice") + (cn.academy.port.core.SkillTuning.number("threatening_teleport", "reach_mastered") - cn.academy.port.core.SkillTuning.number("threatening_teleport", "reach_novice")) * exp), cost.range());
            eq((int)(30 - 15 * exp), cost.cooldown());
            eq(cost.damage(), ThreateningTeleport.damage(exp, false));
            eq(cost.damage() * 1.5, ThreateningTeleport.damage(exp, true));
        }
        eq(35, ThreateningTeleport.rules(-1).cp());
        eq(100, ThreateningTeleport.rules(2).cp());
        eq(35, ThreateningTeleport.rules(Double.NaN).cp());
        eq(cn.academy.port.core.SkillTuning.number("threatening_teleport", "reach_novice"), ThreateningTeleport.rules(Double.POSITIVE_INFINITY).range());
        eq(.003, ThreateningTeleport.experienceIncrement(true));
        eq(.0006, ThreateningTeleport.experienceIncrement(false));
        eq(.3, ThreateningTeleport.dropProbability(true));
        eq(1, ThreateningTeleport.dropProbability(false));
        eq(1.3, ThreateningTeleport.criticalRate(0));
        eq(1.6, ThreateningTeleport.criticalRate(1));
        eq(2.6, ThreateningTeleport.criticalRate(2));

        var state = ready();
        check(ThreateningTeleport.canUse(state), "learned level-one root skill allowed");
        state.activated = false; check(!ThreateningTeleport.canUse(state), "inactive blocked");
        state.activated = true; state.interfering = true; check(!ThreateningTeleport.canUse(state), "interference blocked");
        state.interfering = false; state.overloadFine = false; check(!ThreateningTeleport.canUse(state), "overload lockout blocked");
        state.overloadFine = true; state.cooldowns.put(ThreateningTeleport.ID, 1); check(!ThreateningTeleport.canUse(state), "cooldown blocked");
        state.cooldowns.clear(); state.level = 0; check(!ThreateningTeleport.canUse(state), "corrupt level-zero learned skill blocked");
        state.level = 1; state.category = "electromaster"; check(!ThreateningTeleport.canUse(state), "cross-category spoof blocked");
        state.category = "teleporter"; state.experience.clear(); check(!ThreateningTeleport.canUse(state), "unlearned blocked");

        state = ready();
        for (int tier = 0; tier < 3; tier++) eq(0, ThreateningTeleport.criticalProbability(state, tier));
        int[] calls = {0};
        eq(-1, ThreateningTeleport.criticalTier(state, () -> {calls[0]++; return 0;}));
        eq(3, calls[0]); // Original rolls even the unlearned zero-probability tiers.
        state.learn("dim_folding_theorem");
        eq(.1, ThreateningTeleport.criticalProbability(state, 0));
        eq(0, ThreateningTeleport.criticalProbability(state, 1));
        eq(0, ThreateningTeleport.criticalProbability(state, 2));
        eq(0, roll(state, .099));
        eq(-1, roll(state, (double).1f, 0, 0)); // Strict '<', not '<='.
        state.experience.put("dim_folding_theorem", 1.0);
        eq(.2, ThreateningTeleport.criticalProbability(state, 0));
        state.learn("space_fluct");
        eq(.38, ThreateningTeleport.criticalProbability(state, 0));
        eq(.10, ThreateningTeleport.criticalProbability(state, 1));
        eq(.01, ThreateningTeleport.criticalProbability(state, 2));
        state.experience.put("space_fluct", 1.0);
        eq(.45, ThreateningTeleport.criticalProbability(state, 0));
        eq(.15, ThreateningTeleport.criticalProbability(state, 1));
        eq(.03, ThreateningTeleport.criticalProbability(state, 2));
        eq(0, roll(state, .449));
        eq(1, roll(state, .5, .149));
        eq(2, roll(state, .5, .2, .029));
        eq(-1, roll(state, .5, .2, .031));
        calls[0] = 0;
        eq(0, ThreateningTeleport.criticalTier(state, () -> {calls[0]++; return 0;}));
        eq(1, calls[0]); // Later tiers are not rolled after the first success.
        eq(.0825, (1 - ThreateningTeleport.criticalProbability(state, 0)) * ThreateningTeleport.criticalProbability(state, 1));
        eq(.014025, (1 - ThreateningTeleport.criticalProbability(state, 0))
                * (1 - ThreateningTeleport.criticalProbability(state, 1)) * ThreateningTeleport.criticalProbability(state, 2));
        state.experience.remove("dim_folding_theorem");
        eq(.25, ThreateningTeleport.criticalProbability(state, 0));

        state = ready();
        state.learn("dim_folding_theorem");
        state.cp = 100; state.overload = 99; state.cpDelay = 12; state.overloadDelay = 31; state.overloadFine = false;
        ThreateningTeleport.recordCritical(state, 0);
        check(state.learned("space_fluct"), "legacy crit auto-learns space_fluct without level-four prerequisites");
        eq(1, state.level); eq(.005, state.exp("dim_folding_theorem")); eq(.0001, state.exp("space_fluct"));
        eq(state.maxCp(), state.cp); eq(0, state.overload);
        eq(12, state.cpDelay); eq(31, state.overloadDelay);
        check(!state.overloadFine, "legacy passive learning leaves overloadFine untouched");
        eq(.0051, state.levelExperience);
        state.cp = 50; state.overload = 25;
        ThreateningTeleport.recordCritical(state, 2);
        eq(.02, state.exp("dim_folding_theorem")); eq(.0002, state.exp("space_fluct"));
        eq(50, state.cp); eq(25, state.overload); // Already learned passives do not refill.
        state = ready(); state.learn("space_fluct"); state.cp = 20; state.overload = 15;
        ThreateningTeleport.recordCritical(state, 1);
        check(state.learned("dim_folding_theorem"), "space-only crit auto-learns Dim Folding");
        eq(.01, state.exp("dim_folding_theorem")); eq(.0001, state.exp("space_fluct"));
        eq(state.maxCp(), state.cp); eq(0, state.overload);
        var other = ready(); other.category = "vecmanip";
        ThreateningTeleport.recordCritical(other, 0);
        check(!other.learned("space_fluct"), "cross-category passive changes rejected");
        check(!ThreateningTeleport.canUse(null), "missing ability state rejected");

        state = ready();
        var cost = ThreateningTeleport.rules(0);
        check(state.consume(cost.cp(), cost.overload(), false), "resource gate succeeds");
        eq(1765, state.cp); eq(18, state.overload);
        eq(.0875, state.extraCp); eq(.1044, state.extraOverload);
        state.cp = 34.99;
        double strain = state.overload;
        check(!state.consume(cost.cp(), cost.overload(), false), "CP shortage fails atomically");
        eq(34.99, state.cp); eq(strain, state.overload);

        AABB box = new AABB(-.5, 0, -.5, .5, 2, .5);
        double margin = (double).3F;
        eq((3 - .5 - margin) * (3 - .5 - margin), ThreateningTeleport.classicEntityDistanceSquared(box,
                new Vec3(0, 1, -3), new Vec3(0, 1, 3)));
        eq((.5 + margin) * (.5 + margin), ThreateningTeleport.classicEntityDistanceSquared(box,
                new Vec3(0, 1, 0), new Vec3(0, 1, 3))); // Exit plane when starting inside.
        check(Double.isInfinite(ThreateningTeleport.classicEntityDistanceSquared(box,
                new Vec3(0, 1, 0), new Vec3(0, 1, .1))), "contained ray has no intercept");
        check(Double.isInfinite(ThreateningTeleport.classicEntityDistanceSquared(box,
                new Vec3(1, 1, -3), new Vec3(1, 1, 3))), "outside expanded box misses");
        check(Double.isFinite(ThreateningTeleport.classicEntityDistanceSquared(box,
                new Vec3(.7, 1, -3), new Vec3(.7, 1, 3))), "classic float .3 pick inflation applied");
        eq(0, ThreateningTeleport.classicEntityDistanceSquared(box,
                new Vec3(0, 1, -.5 - margin), new Vec3(0, 1, 3)));
        check(Double.isInfinite(ThreateningTeleport.classicEntityDistanceSquared(box,
                new Vec3(0, 1, -3), new Vec3(0, 1, -2))), "out-of-range target misses");
        check(Double.isInfinite(ThreateningTeleport.classicEntityDistanceSquared(box,
                new Vec3(0, 1, 0), new Vec3(0, 1, 0))), "zero segment misses");
        System.out.println("PASS " + assertions + " Threatening Teleport source-derived assertions");
    }
}
