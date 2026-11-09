package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.client.ClassicDeveloperLayout;
import cn.academy.port.client.ClassicDeveloperTimeline;
import cn.academy.port.client.HighPowerDeveloperLayout;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.preset.PresetSkills;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** NEW432 shared catalog, actual paid learning, saved identity and additive page contract. */
public final class CirculationReliefLearningRegressionTest {
    private static final String ID = "circulation_relief", CATEGORY = "vecmanip";
    private static int checks;

    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }

    private static AbilityProgress state() {
        var state = new AbilityProgress();
        state.changeCategoryClassic(CATEGORY); state.setLevel(3); state.setActivateState(true);
        return state;
    }

    private static AbilityProgress prerequisites() {
        var state = state();
        state.learn("deflection_strike"); state.learn("vector_parry"); state.learn("inertial_anchor");
        return state;
    }

    private static final class Battery implements DevelopmentProcess.Developer {
        private double remaining;
        private Battery(double value) { remaining = value; }
        public DeveloperType type() { return DeveloperType.NORMAL; }
        public double energy() { return remaining; }
        public double maxEnergy() { return 10000; }
        public boolean tryPullEnergy(double amount) {
            if (remaining < amount) return false;
            remaining -= amount; return true;
        }
    }

    public static void main(String[] args) throws Exception {
        SkillCatalog.resetConfiguration();
        try {
            registrationAndLearning();
            savedIdentityAndConfiguration();
            additivePageAndClassicIdentity();
        } finally { SkillCatalog.resetConfiguration(); }
        System.out.println("PASS " + checks + " NEW432 catalog, paid learning, save and additive page checks");
    }

    private static void registrationAndLearning() {
        var skill = SkillCatalog.find(CATEGORY, ID).orElseThrow();
        check(HighPowerSkills.CIRCULATION_RELIEF_PROPOSAL == 432 && HighPowerSkills.ALL.size() == 51 && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.highpower.HighPowerSkills.ALL.get(49).id().equals("point_meltdown") && HighPowerSkills.ALL.get(48).id().equals("wind_pressure_lift") && HighPowerSkills.ALL.get(47).id().equals("multi_axis_reflection") && HighPowerSkills.ALL.get(45).id().equals("circulation_listening")
                && HighPowerSkills.ALL.get(44).equals(skill) && SkillCatalog.REGISTERED.get(94).equals(skill)
                && HighPowerSkills.ALL.get(43).id().equals("trajectory_return")
                && HighPowerSkills.ALL.get(41).id().equals("inertial_anchor"), "432 appends after all44 prior registrations");
        check(skill.level() == 3 && skill.controllable() && skill.anyLearnedSkillLevel() == 0
                && skill.requirements().equals(List.of(new SkillCatalog.Requirement("inertial_anchor", 0))),
                "LV3 requires actual learned422 at zero mastery");
        var state = state();
        check(!SkillCatalog.canLearn(state, skill) && !PresetSkills.edit(state, 0, 0, ID), "unlearned432 cannot bind or bypass422");
        state.learn("deflection_strike"); state.learn("vector_parry"); state.learn("yaw_palm"); state.learn("trajectory_return");
        check(!SkillCatalog.canLearn(state, skill), "earlier vector and parallel431 skills cannot replace422");
        state.learn("inertial_anchor"); state.level = 2;
        check(!DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill), "higher machine never bypassesLV3");
        state.setLevel(3); state.levelExperience = .125;
        check(state.exp("inertial_anchor") == 0 && SkillCatalog.canLearn(state, skill)
                && DevelopmentActions.canLearn(state, DeveloperType.NORMAL, skill)
                && !DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill), "zero-mastery422 and Normal machine admit learning");
        var action = DevelopmentActions.skill(state, skill);
        check(action.stimulations() == 7 && DeveloperType.NORMAL.actualConsumption(action.stimulations()) == 5145,
                "LV3 uses existing seven-stimulation5145IF learning cost");
        var process = new DevelopmentProcess(); var battery = new Battery(5145);
        process.start(battery, action);
        for (int tick = 0; tick < 146; tick++) process.tick();
        check(!state.learned(ID) && battery.remaining == 35, "146 paid ticks grant no early learned bit");
        process.tick();
        check(state.learned(ID) && state.exp(ID) == 0 && state.levelExperience == .125 && battery.remaining == 0
                && process.state() == DevelopmentProcess.State.DONE && HighPowerSkills.eligible(state, ID),
                "final real payment grants learning without mastery or levelXP");
        var shortState = prerequisites(); var shortProcess = new DevelopmentProcess(); var shortBattery = new Battery(5144);
        shortProcess.start(shortBattery, DevelopmentActions.skill(shortState, skill));
        for (int tick = 0; tick < 147; tick++) shortProcess.tick();
        check(!shortState.learned(ID) && shortBattery.remaining == 34 && shortProcess.state() == DevelopmentProcess.State.FAILED,
                "oneIF shortage cannot grant learned432");
        for (int level = 1; level <= 5; level++) {
            final int nativeLevel = level;
            var nativeState = state(); nativeState.setLevel(level);
            long expected = SkillCatalog.ALL.stream().filter(s -> s.category().equals(CATEGORY)
                    && s.level() == nativeLevel && s.controllable()).count();
            check(SkillCatalog.levelSkillCount(nativeState) == expected, "classic denominator before432 atLV" + level);
            nativeState.learn(ID);
            check(SkillCatalog.levelSkillCount(nativeState) == expected, "learned432 never expands denominator atLV" + level);
        }
    }

    private static void savedIdentityAndConfiguration() {
        var state = prerequisites();
        check(!AbilityStorage.decodeSaved(AbilityStorage.encodeSaved(state), state.cpDataConfig()).learned(ID),
                "old save never auto-learns432");
        state.learn(ID); state.cp = 17.5; state.overload = 3.25; state.levelExperience = .375;
        for (int preset = 0; preset < 4; preset++) for (int slot = 0; slot < 4; slot++) {
            check(PresetSkills.edit(state, preset, slot, ID), "learned432 binds existing slot");
            check(!PresetSkills.edit(state, preset, (slot + 1) % 4, ID), "duplicate preset mapping remains rejected");
            check(PresetSkills.edit(state, preset, slot, ""), "existing clear action remains available");
        }
        check(PresetSkills.edit(state, 2, 1, ID), "bind canonical432 slot before save");
        var saved = AbilityStorage.encodeSaved(state); var loaded = AbilityStorage.decodeSaved(saved, state.cpDataConfig());
        check(saved.getInt("schema") == 3 && loaded.learned(ID) && loaded.presets.skill(2, 1).equals(ID)
                && AbilityStorage.encodeSaved(loaded).equals(saved), "existing schema retains432 and exact depleted resources");
        try {
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key(CATEGORY, ID), new ClassicSkillConfiguration.Flags(false, false)));
            check(!HighPowerSkills.eligible(loaded, ID) && !PresetSkills.selectable(loaded, ID)
                    && !DevelopmentActions.canLearn(prerequisites(), DeveloperType.NORMAL, SkillCatalog.find(CATEGORY, ID).orElseThrow()),
                    "configuration disables learning binding and use");
            check(AbilityStorage.encodeSaved(loaded).equals(saved), "configuration cannot erase saved identity");
        } finally { SkillCatalog.resetConfiguration(); }
        loaded.level = 2;
        check(!HighPowerSkills.eligible(loaded, ID) && !PresetSkills.mappedUsable(loaded, ID)
                && loaded.presets.skill(2, 1).equals(ID), "level loss preserves mapping while suspending use");
    }

    private static void additivePageAndClassicIdentity() throws Exception {
        var page = HighPowerDeveloperLayout.category(CATEGORY);
        String[] ids = {"deflection_strike", "vector_parry", "braking_touch", "shear_windblade", "inertial_anchor", "yaw_palm", "trajectory_return", ID};
        String[] parents = {"", "deflection_strike", "deflection_strike", "deflection_strike", "vector_parry", "braking_touch", "yaw_palm", "inertial_anchor"};
        int[] proposals = {411, 412, 413, 421, 422, 423, 431, 432}, levels = {1, 1, 1, 2, 2, 2, 3, 3};
        double[] xs = {118, 198, 118, 38, 118, 198, 118, 198}, ys = {61, 61, 126, 105, 105, 105, 17, 17};
        check(page.size() == ids.length + 4 && page.get(11).id().equals("wind_pressure_lift") && page.get(10).id().equals("multi_axis_reflection") && page.get(9).id().equals("vector_cage") && page.get(8).id().equals("circulation_listening") && HighPowerDeveloperLayout.page(CATEGORY, HighPowerDeveloperLayout.Page.HIGH_POWER).equals(page),
                "432 appends eighth node on the separate high-power page");
        for (int index = 0; index < ids.length; index++) {
            var node = page.get(index);
            check(node.id().equals(ids[index]) && node.category().equals(CATEGORY) && node.index() == proposals[index]
                    && node.level() == levels[index] && node.x() == xs[index] && node.y() == ys[index]
                    && node.parent().equals(parents[index]), "retained exact node metadata and ordinal " + ids[index]);
        }
        var node = page.get(7);
        check(node.conditions().size() == 2 && node.conditions().get(0).get("minimum").getAsString().equals("normal")
                && node.conditions().get(1).get("id").getAsString().equals("inertial_anchor")
                && node.conditions().get(1).get("minimum_exp").getAsDouble() == 0, "visible432 prerequisites match native learning");
        check(node.x() + 8 - 18.6 >= 0 && node.x() + 8 + 18.6 <= 257
                && node.y() + 8 - 18.6 >= 0 && node.y() + 8 + 18.6 <= 139, "432 hover ring stays in native viewport");
        var edge = ClassicDeveloperTimeline.edge(node.x(), node.y(), page.get(4).x(), page.get(4).y(), 1);
        for (int index = 0; index < 7; index++) {
            var prior = page.get(index);
            check(Math.hypot(node.x() - prior.x(), node.y() - prior.y()) > 37.2, "432 hover ring clears " + prior.id());
            if (index == 4) continue;
            double dx = edge.x1() - edge.x0(), dy = edge.y1() - edge.y0();
            double fraction = Math.max(0, Math.min(1, ((prior.x() + 8 - edge.x0()) * dx
                    + (prior.y() + 8 - edge.y0()) * dy) / (dx * dx + dy * dy)));
            check(Math.hypot(prior.x() + 8 - edge.x0() - dx * fraction,
                    prior.y() + 8 - edge.y0() - dy * fraction) > 18.6 + 2.75, "432 dependency clears unrelated hover ring " + prior.id());
        }
        var classic = ClassicDeveloperLayout.NODES.stream().filter(n -> n.category().equals(CATEGORY)).toList();
        check(classic.size() == 12 && HighPowerDeveloperLayout.page(CATEGORY, HighPowerDeveloperLayout.Page.CLASSIC).equals(classic),
                "all12 classic vector nodes remain unchanged");
        check(SkillCatalog.ALL.size() == 50 && SkillCatalog.CONFIG_KEYS.size() == 41 && PresetSkills.IMPLEMENTED.size() == 35
                && SkillCatalog.REGISTERED.size() == 101 && cn.academy.port.SkillCatalog.REGISTERED.get(100).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.SkillCatalog.REGISTERED.get(99).id().equals("point_meltdown") && SkillCatalog.REGISTERED_CONFIG_KEYS.size() == 92 && cn.academy.port.SkillCatalog.REGISTERED_CONFIG_KEYS.contains(cn.academy.port.core.ClassicSkillConfiguration.key("meltdowner", "remote_scatter_relay")) && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0))
                && PresetSkills.REGISTERED.size() == 86 && cn.academy.port.preset.PresetSkills.REGISTERED.contains("remote_scatter_relay") && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.preset.PresetSkills.REGISTERED.contains("point_meltdown"), "only derived registries gain432");
        check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of("src/main/resources/classic-skills.json"))))
                .equals("fe698b35ce94e9cbd628dc63950bfd706749177d3b0ebe4048c1b4e7905e4433"), "original classic catalog retains historical byte identity");
    }
}
