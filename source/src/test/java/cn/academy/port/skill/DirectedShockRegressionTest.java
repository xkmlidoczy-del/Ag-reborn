package cn.academy.port.skill;

import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Deterministic rules/geometry tests; never launches Minecraft or a world. */
public final class DirectedShockRegressionTest {
    private static int assertions;

    private static void equal(double expected, double actual) {
        assertions++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 0.000001)
            throw new AssertionError(expected + " != " + actual);
    }

    private static void check(boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError("condition failed");
    }

    private static void vector(Vec3 expected, Vec3 actual) {
        equal(expected.x, actual.x);
        equal(expected.y, actual.y);
        equal(expected.z, actual.z);
    }

    private static AbilityProgress learned() {
        var state = new AbilityProgress();
        state.selectCategory("vecmanip");
        state.setLevel(1);
        state.learn(DirectedShock.ID);
        state.activated = true;
        return state;
    }

    public static void main(String[] args) {
        // Exact endpoint/default formulas and intermediate mastery, including truncation.
        for (double mastery : new double[]{0, .25, .5, .75, 1}) {
            var cost = DirectedShock.cost(mastery);
            equal(50 + 50 * mastery, cost.cp());
            equal(18 - 6 * mastery, cost.overload());
            equal(7 + 8 * mastery, cost.damage());
            equal(cn.academy.port.core.SkillTuning.number("dir_shock", "reach"), cost.range());
            equal((int) (60 - 40 * mastery), cost.cooldown());
        }
        equal(59, DirectedShock.cost(.0035).cooldown());
        equal(50, DirectedShock.cost(Double.NaN).cp());
        equal(100, DirectedShock.cost(2).cp());
        equal(18, DirectedShock.cost(-1).overload());
        check(!DirectedShock.acceptsRelease(Long.MIN_VALUE));
        check(!DirectedShock.acceptsRelease(-1));
        check(!DirectedShock.acceptsRelease(0));
        check(!DirectedShock.acceptsRelease(6));
        check(DirectedShock.acceptsRelease(7));
        check(DirectedShock.acceptsRelease(49));
        check(!DirectedShock.acceptsRelease(50));
        check(!DirectedShock.acceptsRelease(199));
        check(!DirectedShock.acceptsRelease(200));
        check(!DirectedShock.acceptsRelease(Long.MAX_VALUE));
        check(!DirectedShock.acceptsHold(-1));
        check(DirectedShock.acceptsHold(0));
        check(DirectedShock.acceptsHold(49));
        check(DirectedShock.acceptsHold(50));
        check(DirectedShock.acceptsHold(199));
        check(!DirectedShock.acceptsHold(200));
        check(!DirectedShock.acceptsHold(Long.MAX_VALUE));
        equal(6, DirectedShock.PUNCH_ANIM_TICKS);

        // Skill belongs to VecManip, requires learning, level 1 and usable ability state.
        var skill = SkillCatalog.find("vecmanip", DirectedShock.ID).orElseThrow();
        equal(1, skill.level());
        check(skill.controllable());
        check(skill.requirements().isEmpty());
        check(!DirectedShock.canUse(null));
        var state = learned();
        check(DirectedShock.canUse(state));
        state.category = "teleporter";
        check(!DirectedShock.canUse(state));
        state.category = "vecmanip";
        state.level = 0;
        check(!DirectedShock.canUse(state));
        state.level = 1;
        state.activated = false;
        check(!DirectedShock.canUse(state));
        state.activated = true;
        state.interfering = true;
        check(!DirectedShock.canUse(state));
        state.interfering = false;
        state.overloadFine = false;
        check(!DirectedShock.canUse(state));
        state.overloadFine = true;
        state.cooldowns.put(DirectedShock.ID, 1);
        check(!DirectedShock.canUse(state));
        state.cooldowns.clear();
        state.experience.clear();
        check(!DirectedShock.canUse(state));

        // Consumption order, paid miss, hit-only cooldown, and pre-gain cooldown mastery.
        state = learned();
        var cost = DirectedShock.cost(state.exp(DirectedShock.ID));
        check(state.consume(cost.cp(), cost.overload(), false));
        DirectedShock.recordResult(state, true);
        equal(1750, state.cp);
        equal(18, state.overload);
        equal(15, state.cpDelay);
        equal(32, state.overloadDelay);
        equal(60, state.cooldowns.get(DirectedShock.ID));
        equal(.0035, state.exp(DirectedShock.ID));
        equal(.0035, state.levelExperience);
        for (int tick = 0; tick < 60; tick++) state.tick();
        check(!state.cooldowns.containsKey(DirectedShock.ID));
        state = learned();
        check(state.consume(50, 18, false));
        DirectedShock.recordResult(state, false);
        equal(1750, state.cp);
        equal(18, state.overload);
        equal(.0010, state.exp(DirectedShock.ID));
        equal(.0010, state.levelExperience);
        check(state.cooldowns.isEmpty());
        state = learned();
        state.cp = 49;
        check(!state.consume(50, 18, false));
        equal(49, state.cp);
        equal(0, state.overload);
        equal(0, state.exp(DirectedShock.ID));
        check(state.cooldowns.isEmpty());
        check(state.consume(50, 18, true));
        equal(49, state.cp);
        equal(0, state.overload);
        state = learned();
        state.experience.put(DirectedShock.ID, 1.0);
        DirectedShock.recordResult(state, true);
        equal(20, state.cooldowns.get(DirectedShock.ID));
        equal(1, state.exp(DirectedShock.ID));

        // Threshold, overwritten strong velocity, and extra impulse after lifting feet.
        check(!DirectedShock.hasStrongKnockback(0));
        check(!DirectedShock.hasStrongKnockback(.249999));
        check(DirectedShock.hasStrongKnockback(.25));
        check(DirectedShock.hasStrongKnockback(1));
        Vec3 casterHead = new Vec3(0, 1.62, 0);
        Vec3 targetHead = new Vec3(2, 1.62, 0);
        Vec3 strong = DirectedShock.strongImpulse(casterHead, targetHead);
        double scale = .7 / Math.sqrt(1 + .6 * .6);
        vector(new Vec3(scale, .6 * scale, 0), strong);
        equal(.7, strong.length());
        vector(new Vec3(.24, 0, 0), DirectedShock.extraImpulse(Vec3.ZERO, new Vec3(2, 0, 0)));
        Vec3 liftedImpulse = DirectedShock.extraImpulse(Vec3.ZERO, new Vec3(2, .1, 0));
        equal(.24, liftedImpulse.length());
        check(liftedImpulse.y > 0);
        Vec3 finalStrong = strong.add(liftedImpulse);
        equal(strong.x + liftedImpulse.x, finalStrong.x);
        equal(strong.y + liftedImpulse.y, finalStrong.y);
        vector(Vec3.ZERO, DirectedShock.extraImpulse(Vec3.ZERO, Vec3.ZERO));
        vector(new Vec3(0, .7, 0), DirectedShock.strongImpulse(casterHead, casterHead));

        // Legacy entity-vs-block test deliberately compares feet, including equality.
        check(DirectedShock.entityWinsBlock(Vec3.ZERO, new Vec3(2, 0, 0), new Vec3(3, 0, 0)));
        check(DirectedShock.entityWinsBlock(Vec3.ZERO, new Vec3(3, 0, 0), new Vec3(3, 0, 0)));
        check(!DirectedShock.entityWinsBlock(Vec3.ZERO, new Vec3(4, 0, 0), new Vec3(3, 0, 0)));
        check(!DirectedShock.entityWinsBlock(casterHead, new Vec3(2, 0, 0), new Vec3(2.1, 1.62, 0)));

        // All six legacy planes, point-blank exit, exact boundary and endpoint.
        var box = new AABB(1, -1, -1, 2, 1, 1);
        vector(new Vec3(1, 0, 0), DirectedShock.classicIntercept(box, Vec3.ZERO, new Vec3(3, 0, 0)));
        vector(new Vec3(2, 0, 0), DirectedShock.classicIntercept(box, new Vec3(3, 0, 0), Vec3.ZERO));
        vector(new Vec3(2, 0, 0), DirectedShock.classicIntercept(box, new Vec3(1.5, 0, 0), new Vec3(3, 0, 0)));
        vector(new Vec3(1, 0, 0), DirectedShock.classicIntercept(box, new Vec3(1, 0, 0), new Vec3(3, 0, 0)));
        vector(new Vec3(1, 0, 0), DirectedShock.classicIntercept(box, Vec3.ZERO, new Vec3(1, 0, 0)));
        vector(new Vec3(1.5, -1, 0), DirectedShock.classicIntercept(box, new Vec3(1.5, -2, 0), new Vec3(1.5, 2, 0)));
        vector(new Vec3(1.5, 1, 0), DirectedShock.classicIntercept(box, new Vec3(1.5, 2, 0), new Vec3(1.5, -2, 0)));
        vector(new Vec3(1.5, 0, -1), DirectedShock.classicIntercept(box, new Vec3(1.5, 0, -2), new Vec3(1.5, 0, 2)));
        vector(new Vec3(1.5, 0, 1), DirectedShock.classicIntercept(box, new Vec3(1.5, 0, 2), new Vec3(1.5, 0, -2)));
        check(DirectedShock.classicIntercept(box, new Vec3(1.25, 0, 0), new Vec3(1.75, 0, 0)) == null);
        check(DirectedShock.classicIntercept(box, Vec3.ZERO, new Vec3(.5, 0, 0)) == null);
        check(DirectedShock.classicIntercept(box, new Vec3(0, 2, 0), new Vec3(3, 2, 0)) == null);
        check(DirectedShock.classicIntercept(box, Vec3.ZERO, Vec3.ZERO) == null);
        var tiny = new AABB(.0001, -1, -1, .0002, 1, 1);
        check(DirectedShock.classicIntercept(tiny, Vec3.ZERO, new Vec3(.0003, 0, 0)) == null);

        System.out.println("PASS " + assertions + " Directed Shock deterministic assertions; no game runtime launched");
    }
}
