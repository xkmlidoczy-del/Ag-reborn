package cn.academy.port.develop;

import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Standalone JVM regression; never launches a Minecraft client or server. */
public final class DevelopmentRegressionTest {
    private static int assertions;
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static void eq(double expected, double actual, String message) { check(Math.abs(expected - actual) < 1e-8, message + ": " + expected + " != " + actual); }

    private static final class Battery implements DevelopmentProcess.Developer, DeveloperEnergy.Access {
        private double energy;
        private final DeveloperType type;
        private int pulls;
        Battery(DeveloperType type, double energy) { this.type = type; this.energy = energy; }
        public DeveloperType type() { return type; }
        public double energy() { return energy; }
        public void energy(double amount) { energy = DeveloperEnergy.sanitize(amount, type); }
        public double maxEnergy() { return type.energy; }
        public boolean tryPullEnergy(double amount) { pulls++; return DeveloperEnergy.tryPull(this, amount); }
    }

    private static final class Action implements DevelopmentProcess.Action {
        final int stimulations;
        boolean valid = true;
        int validations, completions;
        Action(int stimulations) { this.stimulations = stimulations; }
        public String id() { return "test"; }
        public int stimulations() { return stimulations; }
        public boolean validate(DevelopmentProcess.Developer developer) { validations++; return valid; }
        public void complete() { completions++; }
    }

    private static final class Items implements DevelopmentActions.CategoryItems {
        String factor;
        boolean coil;
        int factorsConsumed, coilsConsumed;
        public Optional<String> differentFactor(String category) { return Optional.ofNullable(factor).filter(f -> !f.equals(category)); }
        public boolean hasHeldMagneticCoil() { return coil; }
        public void consumeFactor(String category) { check(category.equals(factor), "consume current factor"); factor = null; factorsConsumed++; }
        public void consumeHeldMagneticCoil() { check(coil, "consume current coil"); coil = false; coilsConsumed++; }
    }

    private static void run(DevelopmentProcess process, int ticks) { for (int i = 0; i < ticks; i++) process.tick(); }
    private static void complete(DevelopmentProcess.Action action, Battery battery) {
        var process = new DevelopmentProcess(); process.start(battery, action);
        run(process, action.stimulations() * battery.type.ticksPerStimulation());
        check(process.state() == DevelopmentProcess.State.DONE, "action completes");
    }

    public static void main(String[] args) {
        DeveloperType[] tiers = DeveloperType.values();
        double[] capacity = {10000, 50000, 200000}, bandwidth = {50, 100, 300}, syncRate = {.3, .7, 1}, cps = {750, 700, 600};
        int[] tps = {25, 20, 15}, stimulations = {3, 3, 5, 7, 11, 15};
        for (int i = 0; i < tiers.length; i++) {
            var tier = tiers[i];
            eq(capacity[i], tier.energy, "capacity"); eq(bandwidth[i], tier.bandwidth, "bandwidth");
            eq(syncRate[i], tier.syncRate, "source syncRate"); eq(cps[i], tier.cps, "cps"); eq(tps[i], tier.tps, "tps");
            eq(cps[i] / tps[i], tier.energyPerTick(), "consume per tick");
            for (int level = 0; level <= 5; level++) {
                var state = new AbilityProgress();
                var skill = new SkillCatalog.Skill("electromaster", "test", level, true, List.of(), 0);
                var count = DevelopmentActions.skill(state, skill).stimulations();
                eq(stimulations[level], count, "skill stimulations at level " + level);
                var action = new Action(count);
                // A real portable battery cannot hold an entire high-level skill cost; stream energy via a test callback.
                var battery = new Battery(tier, tier.energy);
                var process = new DevelopmentProcess(); process.start(battery, action);
                int ticks = count * (tier.tps + 1);
                double totalConsumed = 0;
                for (int tick = 0; tick < ticks; tick++) {
                    battery.energy(tier.energy); // Explicit test fixture charging; production storage never refills itself.
                    process.tick(); totalConsumed += tier.energy - battery.energy;
                    if (tick < ticks - 1) check(process.isDeveloping(), "does not complete early");
                }
                eq(count * (tier.tps + 1) * tier.energyPerTick(), totalConsumed, "TPS+1 energy cost");
                eq(tier.actualConsumption(count), totalConsumed, "actual energy helper");
                eq(tier.cps * count, tier.estimatedConsumption(count), "source nominal estimate");
                eq(ticks, battery.pulls, "one pull each tick"); eq(1, action.validations, "validate only at completion");
                eq(1, action.completions, "complete exactly once"); check(process.state() == DevelopmentProcess.State.DONE, "DONE");
                check(!process.isDeveloping() && process.action() == null, "developer and action cleared on DONE");
                eq(0, process.stimulation(), "finished counters cleared"); eq(0, process.maxStimulations(), "finished total cleared");
                eq(0, process.getDevelopProgress(), "finished source progress returns zero");
            }
        }

        var battery = new Battery(DeveloperType.PORTABLE, 10000);
        var action = new Action(3); var process = new DevelopmentProcess(); process.start(battery, action);
        run(process, 25); eq(0, process.stimulation(), "25 portable ticks are still first stimulation");
        eq(25, process.tickThisStimulation(), "source off-by-one counter");
        eq(1, process.getDevelopProgress(), "retain original unnormalized source accessor");
        eq(25.0 / 26 / 3, process.snapshot().normalizedProgress(), "normalized display accessor");
        process.tick(); eq(1, process.stimulation(), "26th tick stimulates"); eq(9220, battery.energy(), "780 IF first stimulation");

        var replacement = new Action(1); process.start(battery, replacement);
        eq(0, process.stimulation(), "restart resets stimulations"); eq(0, process.tickThisStimulation(), "restart resets tick");
        eq(9220, battery.energy(), "override never refunds energy"); run(process, 26);
        eq(0, action.completions, "overridden action not completed"); eq(1, replacement.completions, "replacement completed");

        battery = new Battery(DeveloperType.PORTABLE, 29); action = new Action(1); process.start(battery, action); process.tick();
        check(process.state() == DevelopmentProcess.State.FAILED, "underpowered tick fails");
        eq(0, battery.energy(), "failed source pull consumes partial remainder"); eq(0, action.validations, "energy failure does not validate");
        check(!process.isDeveloping(), "failure clears developer");
        battery = new Battery(DeveloperType.PORTABLE, 780); action = new Action(1); process.start(battery, action); run(process, 26);
        check(process.state() == DevelopmentProcess.State.DONE, "exact true cost succeeds"); eq(0, battery.energy(), "exact energy exhausted");
        battery = new Battery(DeveloperType.PORTABLE, 750); action = new Action(1); process.start(battery, action); run(process, 26);
        check(process.state() == DevelopmentProcess.State.FAILED, "nominal 750 IF is insufficient for one source stimulation");

        battery = new Battery(DeveloperType.NORMAL, 50000); action = new Action(1); process.start(battery, action); run(process, 20);
        action.valid = false; process.tick(); check(process.state() == DevelopmentProcess.State.FAILED, "revalidation rejects changed state");
        eq(1, action.validations, "revalidation once"); eq(0, action.completions, "invalid action not applied");
        eq(50000 - 735, battery.energy(), "failed completion still costs full process energy");
        action = new Action(2); process.start(battery, action); run(process, 4); double beforeAbort = battery.energy(); process.abort();
        check(process.state() == DevelopmentProcess.State.FAILED, "abort sets FAILED"); process.tick(); eq(beforeAbort, battery.energy(), "aborted process stops consuming");
        process.reset(); check(process.state() == DevelopmentProcess.State.IDLE, "reset sets IDLE"); process.abort();
        check(process.state() == DevelopmentProcess.State.IDLE, "abort idle does nothing");

        process = new DevelopmentProcess(); process.start(new Battery(DeveloperType.PORTABLE, 10000), new Action(1));
        var syncTicks = new ArrayList<Integer>(); var syncStates = new ArrayList<DevelopmentProcess.Snapshot>();
        for (int tick = 1; tick <= 27; tick++) { final int currentTick = tick; process.tick(snapshot -> { syncTicks.add(currentTick); syncStates.add(snapshot); }); }
        check(syncTicks.equals(List.of(1, 6, 12, 18, 24, 27)), "dirty start, six-tick periodic sync, dirty DONE on next tick");
        eq(0, syncStates.get(0).tickThisStimulation(), "sync before first consumption");
        eq(5, syncStates.get(1).tickThisStimulation(), "sync before sixth consumption");
        check(syncStates.get(syncStates.size() - 1).state() == DevelopmentProcess.State.DONE, "terminal state synced");

        var state = new AbilityProgress(); var items = new Items(); items.factor = "teleporter";
        var acquisition = DevelopmentActions.level(state, items, bound -> { throw new AssertionError("factor bypasses random"); });
        eq(5, acquisition.stimulations(), "category acquisition stimulations");
        complete(acquisition, new Battery(DeveloperType.PORTABLE, 10000));
        check(state.category.equals("teleporter"), "factor category chosen"); eq(1, state.level, "source category starts at level 1");
        eq(1, items.factorsConsumed, "factor consumed once");
        state = new AbilityProgress();
        complete(DevelopmentActions.level(state, DevelopmentActions.CategoryItems.NONE, bound -> 2), new Battery(DeveloperType.PORTABLE, 10000));
        check(state.category.equals("teleporter") && state.level == 1, "random source acquisition");
        eq(10, DevelopmentActions.level(state, items, bound -> 0).stimulations(), "L1 upgrade stimulations");
        check(!DevelopmentActions.canLevelUp(state), "untrained level blocks upgrade");
        state.levelExperience = 100;
        complete(DevelopmentActions.level(state, items, bound -> 0), new Battery(DeveloperType.PORTABLE, 10000));
        eq(2, state.level, "portable can level up when source prerequisites met"); eq(0, state.levelExperience, "new level clears level experience");
        state.setLevel(5); state.levelExperience = 100; check(!DevelopmentActions.canLevelUp(state), "level 5 cap");

        state = new AbilityProgress(); state.selectCategory("electromaster"); state.setLevel(5);
        for (int level = 1; level <= 5; level++) {
            var skill = new SkillCatalog.Skill(state.category, "fixture" + level, level, true, List.of(), 0);
            for (var tier : tiers) check(DevelopmentActions.canLearn(state, tier, skill) == tier.supportsSkill(level), "tier restriction L" + level);
        }
        var arc = SkillCatalog.find("electromaster", "arc_gen").orElseThrow();
        complete(DevelopmentActions.skill(state, arc), new Battery(DeveloperType.PORTABLE, 10000)); check(state.learned("arc_gen"), "skill action learns");
        check(DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, arc), "source duplicate completion is idempotent");
        var railgun = SkillCatalog.find("electromaster", "railgun").orElseThrow();
        check(!DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, railgun), "dependencies block");
        state.experience.put("thunder_bolt", .3); state.experience.put("mag_manip", 1.0);
        check(DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, railgun), "exact dependencies accepted");
        check(!DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, railgun), "portable cannot learn railgun even at level 5");

        state.setLevel(3); items = new Items(); items.coil = true; items.factor = "teleporter";
        check(!DevelopmentActions.canReset(state, DeveloperType.PORTABLE, items), "portable reset forbidden");
        check(!DevelopmentActions.canReset(state, DeveloperType.NORMAL, items), "normal reset forbidden");
        check(DevelopmentActions.canReset(state, DeveloperType.ADVANCED, items), "advanced reset with coil and different factor");
        items.factor = "electromaster"; check(!DevelopmentActions.canReset(state, DeveloperType.ADVANCED, items), "same category factor rejected");
        items.factor = "teleporter"; items.coil = false; check(!DevelopmentActions.canReset(state, DeveloperType.ADVANCED, items), "coil missing");
        items.coil = true; state.setLevel(2); check(!DevelopmentActions.canReset(state, DeveloperType.ADVANCED, items), "minimum reset level 3");
        state.setLevel(3); state.activated = true; state.experience.put("arc_gen", 1.0);
        var reset = DevelopmentActions.reset(state, items); eq(30, reset.stimulations(), "reset stimulations level times ten");
        complete(reset, new Battery(DeveloperType.ADVANCED, 200000));
        check(state.category.equals("teleporter"), "reset switches category"); eq(2, state.level, "reset loses one level");
        check(state.experience.isEmpty() && state.cooldowns.isEmpty(), "reset clears learned skills and cooldowns");
        eq(1, items.coilsConsumed, "coil slot consumed"); eq(1, items.factorsConsumed, "factor slot consumed");

        state = new AbilityProgress(); state.selectCategory("electromaster"); state.setLevel(3);
        items = new Items(); items.coil = true; items.factor = "teleporter";
        reset = DevelopmentActions.reset(state, items); battery = new Battery(DeveloperType.ADVANCED, 200000);
        process.start(battery, reset); run(process, 479); items.factor = null; process.tick();
        check(process.state() == DevelopmentProcess.State.FAILED, "reset missing factor at completion fails");
        check(state.category.equals("electromaster") && state.level == 3, "failed reset preserves category and level");
        check(items.coil && items.coilsConsumed == 0 && items.factorsConsumed == 0, "failed reset does not consume ingredients");
        eq(200000 - 30 * 640, battery.energy(), "failed reset still spends complete stimulation budget");

        state = new AbilityProgress(); state.selectCategory("electromaster"); state.setLevel(1);
        battery = new Battery(DeveloperType.PORTABLE, 10000);
        process.start(battery, DevelopmentActions.skill(state, arc)); run(process, 77);
        state.selectCategory("teleporter"); state.setLevel(1); process.tick();
        check(process.state() == DevelopmentProcess.State.FAILED, "category changed during skill development invalidates completion");
        check(!state.learned("arc_gen"), "invalid skill never applied to new category");

        var firstItem = new Battery(DeveloperType.PORTABLE, 10000);
        var secondItem = new Battery(DeveloperType.PORTABLE, 10000);
        Battery[] heldItem = {firstItem};
        var currentMainHandDeveloper = new DevelopmentProcess.Developer() {
            public DeveloperType type() { return DeveloperType.PORTABLE; }
            public boolean tryPullEnergy(double amount) { return heldItem[0] != null && heldItem[0].tryPullEnergy(amount); }
            public double energy() { return heldItem[0] == null ? 0 : heldItem[0].energy(); }
            public double maxEnergy() { return heldItem[0] == null ? 0 : type().energy; }
        };
        process.start(currentMainHandDeveloper, new Action(3)); process.tick(); heldItem[0] = secondItem; process.tick();
        eq(9970, firstItem.energy(), "first held item's energy remains on original item");
        eq(9970, secondItem.energy(), "switch to another developer consumes its own energy");
        heldItem[0] = null; process.tick(); check(process.state() == DevelopmentProcess.State.FAILED, "unequipping fails next energy tick");
        eq(9970, secondItem.energy(), "unequipping does not drain previous item");

        System.out.println("PASS " + assertions + " development state-machine/action assertions");
    }
}
