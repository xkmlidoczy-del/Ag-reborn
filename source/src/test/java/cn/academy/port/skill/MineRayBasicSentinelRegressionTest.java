/* MRContext source-order oracle adapted from AcademyCraft 1.0.7, Copyright Lambda Innovation,
 * 2013-2016, GPLv3 and additional upstream notices; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.AbilityProgress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A real modern block coordinate may equal the original particle sentinel. No game bootstrap. */
public final class MineRayBasicSentinelRegressionTest {
    private static final MineRayBasicSession.Cell NEGATIVE = new MineRayBasicSession.Cell(-1, -1, -1);
    private static int checks;
    private static void check(boolean value, String why) {
        ++checks;
        if (!value) throw new AssertionError(why);
    }
    private static void exact(float value, float expected, String why) {
        check(Float.floatToIntBits(value) == Float.floatToIntBits(expected),
                why + " actual=" + value + " expected=" + expected);
    }
    private static AbilityProgress ready(double exp) {
        var state = new AbilityProgress();
        state.selectCategory("meltdowner");
        state.setLevel(3);
        state.learn(MineRayBasicSession.ID);
        state.experience.put(MineRayBasicSession.ID, exp);
        state.activated = true;
        state.cp = 100000;
        return state;
    }
    private static final class World implements MineRayBasicSession.World {
        MineRayBasicSession.Cell hit = NEGATIVE;
        float hardness = .4F;
        int harvest = 2, permissions, harvestReads, hardnessReads, breaks;
        boolean denied;
        final List<String> calls = new ArrayList<>();
        final List<MineRayBasicSession.Cell> metadata = new ArrayList<>();
        final List<MineRayBasicSession.Cell> particles = new ArrayList<>();
        public MineRayBasicSession.Cell trace() { calls.add("trace"); return hit; }
        public boolean denied(MineRayBasicSession.Cell cell) {
            ++permissions; calls.add("permission:" + cell); return denied;
        }
        public int harvestLevel(MineRayBasicSession.Cell cell, MineRayBasicSession.Cell previous) {
            ++harvestReads; metadata.add(previous); calls.add("harvest:" + cell + ":" + previous); return harvest;
        }
        public float hardness(MineRayBasicSession.Cell cell) {
            ++hardnessReads; calls.add("hardness:" + cell); return hardness;
        }
        public void breakBlock(MineRayBasicSession.Cell cell) { ++breaks; calls.add("break:" + cell); }
        public void particles(MineRayBasicSession.Cell cell) { particles.add(cell); calls.add("particle:" + cell); }
    }
    public static void main(String[] args) {
        if (args.length != 0) {
            if (args[0].equals("denial")) negativePermissionDenial();
            else if (args[0].equals("acquisition")) negativeFirstAcquisition();
            else throw new IllegalArgumentException(args[0]);
        } else {
            ordinaryCoordinatesRemainLiteralSource();
            negativePermissionDenial();
            negativeFirstAcquisition();
            negativeHarvestAndReset();
            previousTargetMetadataAndMiss();
            failedConsumptionAndReplacementQuirks();
        }
        System.out.println("PASS " + checks + " Basic sentinel capture, permission, source-order and quirk checks");
    }
    private static void negativePermissionDenial() {
        var state = ready(0); var session = MineRayBasicSession.begin(state, false); var world = new World();
        world.denied = true;
        for (int tick = 1; tick <= 4; ++tick) {
            check(!session.tick(false, world), "denied target does not end hold");
            check(world.permissions == tick, "real (-1,-1,-1) must request permission on every uncaptured hit");
            check(world.harvestReads == 0 && world.hardnessReads == 0,
                    "permission denial short-circuits harvest and hardness");
            check(world.breaks == 0 && world.particles.isEmpty(), "denied negative coordinate cannot progress or emit");
            exact(session.hardnessLeft(), Float.MAX_VALUE, "denied target preserves original hardness field");
            check(session.target().equals(MineRayBasicSession.Cell.NONE), "denial retains literal sentinel representation");
        }
        check(state.cp == 100000 - 4 * 12, "denied ticks still pay original CP");
    }
    private static void negativeFirstAcquisition() {
        for (float hardness : new float[]{0, .2F, .4F, .5F, -1}) {
            var state = ready(0); var session = MineRayBasicSession.begin(state, false); var world = new World();
            world.hardness = hardness;
            session.tick(false, world);
            check(world.permissions == 1 && world.harvestReads == 1 && world.hardnessReads == 1,
                    "negative first acquisition must run permission, harvest and hardness exactly once");
            exact(session.hardnessLeft(), hardness < 0 ? Float.MAX_VALUE : hardness,
                    "negative first acquisition captures hardness without subtracting speed");
            check(world.breaks == 0 && world.particles.isEmpty(), "first acquisition never breaks or emits, even zero hardness");
            check(world.metadata.equals(List.of(MineRayBasicSession.Cell.NONE)), "first acquisition keeps previous sentinel metadata argument");
            check(world.calls.equals(List.of("trace", "permission:" + NEGATIVE,
                    "harvest:" + NEGATIVE + ":" + MineRayBasicSession.Cell.NONE, "hardness:" + NEGATIVE)),
                    "negative acquisition preserves source call order");
            session.tick(false, world);
            check(world.permissions == 1 && world.harvestReads == 1 && world.hardnessReads == 1,
                    "captured negative coordinate follows progress branch without reacquisition");
            float left = (hardness < 0 ? Float.MAX_VALUE : hardness) - .2F;
            exact(session.hardnessLeft(), left, "second tick performs original float subtraction");
            check(world.breaks == (left <= 0 ? 1 : 0) && world.particles.size() == 1,
                    "second tick alone may break and always emits original progress particle");
            if (left <= 0) {
                check(world.particles.get(0).equals(MineRayBasicSession.Cell.NONE), "post-break particle is literal (-1,-1,-1)");
                int previousPermissions = world.permissions;
                session.tick(false, world);
                check(world.permissions == previousPermissions + 1, "same negative coordinate must reacquire after break reset");
                exact(session.hardnessLeft(), hardness, "same negative coordinate recaptures, without subtraction");
            }
        }
    }
    private static void negativeHarvestAndReset() {
        var state = ready(0); var session = MineRayBasicSession.begin(state, false); var world = new World();
        world.harvest = 3;
        for (int tick = 1; tick <= 3; ++tick) session.tick(false, world);
        check(world.permissions == 3 && world.harvestReads == 3 && world.hardnessReads == 0,
                "uncaptured negative harvest-3 rejection repeats original event and harvest order");
        check(world.breaks == 0 && world.particles.isEmpty(), "harvest rejection cannot progress");
        world.harvest = 2; session.tick(false, world);
        exact(session.hardnessLeft(), .4F, "negative coordinate becomes capturable after harvest gate changes");
        world.hit = new MineRayBasicSession.Cell(2, 4, 6); world.denied = true; session.tick(false, world);
        world.hit = NEGATIVE; world.denied = false; session.tick(false, world);
        exact(session.hardnessLeft(), .4F, "rejection clears capture flag before a negative-coordinate retry");
        check(world.hardnessReads == 2, "negative retry actually rereads captured hardness");
    }
    private static void previousTargetMetadataAndMiss() {
        var state = ready(0); var session = MineRayBasicSession.begin(state, false); var world = new World();
        var old = new MineRayBasicSession.Cell(2, 3, 4);
        world.hit = old; session.tick(false, world);
        world.hit = NEGATIVE; session.tick(false, world);
        check(world.metadata.get(1).equals(old), "changed target keeps original old-coordinate metadata quirk");
        world.hit = null; session.tick(false, world);
        world.hit = NEGATIVE; world.hardness = .9F; session.tick(false, world);
        exact(session.hardnessLeft(), .9F, "miss clears capture so sentinel hit captures again");
        check(world.metadata.get(2).equals(MineRayBasicSession.Cell.NONE), "miss still resets metadata representation");
        check(world.particles.isEmpty() && world.breaks == 0, "miss/change/acquire never invent progress work");
    }
    private static void failedConsumptionAndReplacementQuirks() {
        var state = ready(0); var session = MineRayBasicSession.begin(state, false); var world = new World();
        world.hardness = .2F; session.tick(false, world);
        state.cp = 0; world.hardness = -1; world.harvest = 3; world.denied = true;
        check(session.tick(false, world), "failed CP ends context after progress work");
        check(world.breaks == 1 && world.permissions == 1 && world.harvestReads == 1 && world.hardnessReads == 1,
                "failed consumption still attempts source captured-hardness break without reacquisition/rechecking replacement");
        check(state.exp(MineRayBasicSession.ID) == (double).0005F && world.particles.equals(List.of(MineRayBasicSession.Cell.NONE)),
                "failed final tick still trains and emits literal post-break sentinel");
        check(session.finish() && !session.finish() && state.cooldowns.get(MineRayBasicSession.ID) == 40,
                "termination applies captured cooldown exactly once");
        state = ready(0); session = MineRayBasicSession.begin(state, false); world = new World(); state.cp = 0;
        check(session.tick(false, world), "failed consumption on first tick still ends");
        exact(session.hardnessLeft(), .4F, "failed first acquisition still captures without subtracting");
        check(world.permissions == 1 && world.breaks == 0 && world.particles.isEmpty(), "failed first acquisition preserves source work order");
    }
    /** Literal 1.0.7 MRContext branch order, intentionally coordinate-sentinel based.
     * Its y=-1 collision is inapplicable to original 1.7.10's 0..255 block world. */
    private static final class SourceOracle {
        MineRayBasicSession.Cell target = MineRayBasicSession.Cell.NONE;
        float hardness = Float.MAX_VALUE;
        final float speed;
        SourceOracle(float speed) { this.speed = speed; }
        void tick(World world) {
            var hit = world.trace();
            if (hit == null) { target = MineRayBasicSession.Cell.NONE; return; }
            if (!hit.equals(target)) {
                if (!world.denied(hit) && world.harvestLevel(hit, target) <= 2) {
                    target = hit; hardness = world.hardness(hit); if (hardness < 0) hardness = Float.MAX_VALUE;
                } else target = MineRayBasicSession.Cell.NONE;
            } else {
                hardness -= speed;
                if (hardness <= 0) { world.breakBlock(target); target = MineRayBasicSession.Cell.NONE; }
                world.particles(target);
            }
        }
    }
    private static void ordinaryCoordinatesRemainLiteralSource() {
        var random = new Random(107);
        for (int sample = 0; sample < 1001; ++sample) {
            float mastery = sample / 1000F;
            var session = MineRayBasicSession.begin(ready(mastery), false);
            var oracle = new SourceOracle(.2F + (.4F - .2F) * mastery);
            var actual = new World(); var expected = new World();
            for (int tick = 0; tick < 40; ++tick) {
                // Original valid Y and modern negative Y other than the representational collision.
                var hit = random.nextInt(10) == 0 ? null : new MineRayBasicSession.Cell(
                        random.nextInt(2) - 1, random.nextBoolean() ? 4 : -2, random.nextInt(2) - 1);
                boolean denied = random.nextInt(5) == 0;
                int harvest = random.nextInt(5) - 1;
                float hardness = random.nextInt(9) == 0 ? -1 : random.nextInt(5) / 10F;
                actual.hit = expected.hit = hit; actual.denied = expected.denied = denied;
                actual.harvest = expected.harvest = harvest; actual.hardness = expected.hardness = hardness;
                session.tick(false, actual); oracle.tick(expected);
                check(actual.calls.equals(expected.calls), "ordinary-coordinate source event/work trajectory sample=" + sample + " tick=" + tick);
                check(session.target().equals(oracle.target), "ordinary-coordinate source target trajectory");
                exact(session.hardnessLeft(), oracle.hardness, "ordinary-coordinate literal source hardness trajectory");
            }
        }
        System.out.println("PASS ordinary-target literal-source trajectories");
    }
}
