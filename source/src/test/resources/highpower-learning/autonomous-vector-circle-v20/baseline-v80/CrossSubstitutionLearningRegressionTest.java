package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillAvailability;
import cn.academy.port.SkillCatalog;
import cn.academy.port.client.ClassicDeveloperLayout;
import cn.academy.port.client.ClassicDeveloperTimeline;
import cn.academy.port.client.HighPowerDeveloperLayout;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.core.SkillPresets;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.preset.PresetSkills;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** NEW321 paid learning, saved identities and attack-scope resources; no live combat or rendered-client acceptance. */
public final class CrossSubstitutionLearningRegressionTest {
    private static final String ID = HighPowerSkills.CROSS_SUBSTITUTION;
    private static final String ROOT = HighPowerSkills.DISPLACED_PROJECTION;
    private static final String PREFIX = "academy.highpower.cross_substitution.";
    private static final List<String> PREVIOUS_IDS = List.of(
            "forked_arc", "magnetic_bracer", "charge_decoy", "polarity_tether", "conductive_circuit",
            "grounding_return", "magnetic_ground_step", "magnetic_item_pull", "iron_sand_lance",
            "thunder_net", "potential_buffer", "rail_thunder_array", "magnetic_domain", "dual_point",
            "scatter_plates", "beam_etching", "thermal_shell", "folded_mirror_array", "disintegration_sweep",
            "beam_step", "particle_laminate", "directional_dissipation_curtain", "multicore_canopy",
            "zero_gap_armor", "safe_landing_probe", "phase_sidestep", "displaced_projection", "return_mark", "recovery_mark");
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static AbilityProgress ability(String category, int level) {
        var state = new AbilityProgress();
        state.changeCategoryClassic(category); state.setLevel(level); state.setActivateState(true);
        return state;
    }
    private static final class Battery implements DevelopmentProcess.Developer {
        private final DeveloperType type;
        private double energy;
        private Battery(DeveloperType type, double energy) { this.type = type; this.energy = energy; }
        public DeveloperType type() { return type; }
        public boolean tryPullEnergy(double amount) {
            double paid = Math.min(energy, amount); energy -= paid; return paid == amount;
        }
        public double energy() { return energy; }
        public double maxEnergy() { return type.energy; }
    }
    private static byte[] resource(String path) throws Exception {
        try (var in = Objects.requireNonNull(CrossSubstitutionLearningRegressionTest.class.getResourceAsStream(path), path)) {
            return in.readAllBytes();
        }
    }
    private static JsonObject language(String locale) throws Exception {
        return JsonParser.parseString(new String(resource("/assets/academy/lang/" + locale + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
    }
    public static void main(String[] args) throws Exception {
        SkillCatalog.resetConfiguration();
        try {
            appendOnlyRegistration();
            finiteLearningAndRevalidation();
            savedIdentitiesAndGates();
            boundedLearningFeedback();
            retainedLayoutAndOriginalArtwork();
            localizedPlayerContract();
        } finally { SkillCatalog.resetConfiguration(); }
        System.out.println("CrossSubstitutionLearningRegressionTest: " + checks + " checks passed");
    }

    private static void appendOnlyRegistration() {
        var skill = SkillCatalog.find("teleporter", ID).orElseThrow();
        check(ID.equals("cross_substitution") && HighPowerSkills.CROSS_SUBSTITUTION_PROPOSAL == 321,
                "exact NEW321 proposal and persistent identifier");
        check(skill.level() == 2 && skill.controllable() && skill.anyLearnedSkillLevel() == 0,
                "LV2 controllable TP skill has no alternate-level shortcut");
        check(skill.requirements().equals(List.of(new SkillCatalog.Requirement(ROOT, 0))),
                "learned311 is the only prerequisite, with zero mastery");
        check(HighPowerSkills.ALL.subList(0, 29).stream().map(SkillCatalog.Skill::id).toList().equals(PREVIOUS_IDS),
                "all29 previous high-power identities and ordinals remain unchanged");
        check(HighPowerSkills.ALL.get(29).equals(skill) && SkillCatalog.REGISTERED.get(79).equals(skill),
                "NEW321 appends after all50 classic and29 existing high-power registrations");
        check(HighPowerSkills.IDS.contains(ID) && SkillAvailability.learnable(ID)
                && PresetSkills.REGISTERED.contains(ID)
                && SkillCatalog.REGISTERED_CONFIG_KEYS.contains(ClassicSkillConfiguration.key("teleporter", ID)),
                "derived learning, preset and configuration mappings admit321");
        check(SkillCatalog.ALL.size() == 50 && ClassicDeveloperLayout.NODES.size() == 50
                && PresetSkills.IMPLEMENTED.size() == 35 && SkillCatalog.CONFIG_KEYS.size() == 41
                && SkillCatalog.REGISTERED.subList(0, 50).equals(SkillCatalog.ALL),
                "classic50 catalog,41 shared configuration keys and35 preset denominator remain unchanged");
        check(SkillCatalog.REGISTERED.size() == 101 && cn.academy.port.SkillCatalog.REGISTERED.get(100).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.SkillCatalog.REGISTERED.get(99).id().equals("point_meltdown") && PresetSkills.REGISTERED.size() == 86 && cn.academy.port.preset.PresetSkills.REGISTERED.contains("remote_scatter_relay") && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.preset.PresetSkills.REGISTERED.contains("point_meltdown")
                && HighPowerSkills.ALL.size() == 51 && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.highpower.HighPowerSkills.ALL.get(49).id().equals("point_meltdown") && HighPowerSkills.ALL.get(48).id().equals("wind_pressure_lift") && HighPowerSkills.ALL.get(47).id().equals("multi_axis_reflection") && HighPowerSkills.ALL.get(45).id().equals("circulation_listening") && HighPowerSkills.ALL.get(44).id().equals("circulation_relief") && HighPowerSkills.ALL.get(43).id().equals("trajectory_return") && HighPowerSkills.ALL.get(42).id().equals("yaw_palm") && HighPowerSkills.ALL.get(41).id().equals("inertial_anchor") && HighPowerSkills.ALL.get(40).id().equals("shear_windblade") && HighPowerSkills.ALL.get(39).id().equals("braking_touch") && HighPowerSkills.ALL.get(30).id().equals(HighPowerSkills.CORNER_EXCHANGE) && HighPowerSkills.ALL.get(31).id().equals(HighPowerSkills.SHORT_INTERCEPTION) && HighPowerSkills.IDS.size() == 51 && cn.academy.port.highpower.HighPowerSkills.IDS.contains("remote_scatter_relay") && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.highpower.HighPowerSkills.IDS.contains("point_meltdown") && HighPowerSkills.IDS.contains("wind_pressure_lift") && HighPowerSkills.IDS.contains("multi_axis_reflection") && HighPowerSkills.IDS.contains("circulation_listening") && HighPowerSkills.IDS.contains("circulation_relief") && HighPowerSkills.IDS.contains("trajectory_return") && HighPowerSkills.IDS.contains("yaw_palm") && HighPowerSkills.IDS.contains("inertial_anchor") && HighPowerSkills.IDS.contains("shear_windblade")
                && HighPowerSkills.ALL.get(32).id().equals(HighPowerSkills.SPATIAL_RELAY)
                && HighPowerSkills.ALL.get(33).id().equals(HighPowerSkills.EMERGENCY_RETURN)
                && HighPowerSkills.ALL.get(34).id().equals(HighPowerSkills.TACTICAL_COORDINATE_FORMATION)
                && SkillCatalog.REGISTERED.get(84).equals(HighPowerSkills.ALL.get(34))
                && SkillCatalog.REGISTERED_CONFIG_KEYS.size() == 92 && cn.academy.port.SkillCatalog.REGISTERED_CONFIG_KEYS.contains(cn.academy.port.core.ClassicSkillConfiguration.key("meltdowner", "remote_scatter_relay")) && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0))
                && HighPowerSkills.ALL.get(35).id().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR)
                && SkillCatalog.REGISTERED.get(85).equals(HighPowerSkills.ALL.get(35)),
                "exactly35 high-power additions with retained ordinals and appended NEW353 and85 total catalog entries");
    }

    private static void finiteLearningAndRevalidation() {
        var skill = SkillCatalog.find("teleporter", ID).orElseThrow();
        var state = ability("teleporter", 1); state.learn(ROOT);
        check(!SkillCatalog.canLearn(state, skill) && !DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill),
                "learned311 and advanced hardware cannot bypass LV2");
        state.setLevel(2); state.unlearn(ROOT);
        int denominator = SkillCatalog.levelSkillCount(state);
        check(denominator == SkillCatalog.ALL.stream().filter(s -> s.category().equals("teleporter")
                && s.level() == 2 && s.controllable()).count(), "only original LV2 skills contribute to advancement");
        check(!SkillCatalog.canLearn(state, skill) && !DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill),
                "both learning validators require learned311");
        check(!HighPowerSkills.eligible(state, ID) && !PresetSkills.selectable(state, ID)
                && !PresetSkills.edit(state, 0, 0, ID), "unlearned321 cannot cast or enter a preset");
        state.learn(HighPowerSkills.SAFE_LANDING_PROBE);
        state.learn(HighPowerSkills.RETURN_MARK); state.learn(HighPowerSkills.RECOVERY_MARK);
        check(!SkillCatalog.canLearn(state, skill) && !DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill),
                "learned313,322 and323 cannot substitute for learned311");
        state.unlearn(HighPowerSkills.RETURN_MARK); state.unlearn(HighPowerSkills.RECOVERY_MARK);
        state.learn(ROOT);
        check(state.exp(ROOT) == 0 && SkillCatalog.canLearn(state, skill)
                && DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill)
                && DeveloperType.minimumForSkill(2) == DeveloperType.PORTABLE,
                "LV2 zero-mastery311 preserves canonical Portable minimum");
        var action = DevelopmentActions.skill(state, skill);
        check(action.id().equals("skill:cross_substitution") && action.stimulations() == 5
                && ClassicRules.learningStimulations(2) == 5
                && DeveloperType.PORTABLE.ticksPerStimulation() * action.stimulations() == 130
                && DeveloperType.PORTABLE.actualConsumption(action.stimulations()) == 3900,
                "five ordinary Portable stimulations cost130 ticks and3900 IF");
        check(DeveloperType.NORMAL.ticksPerStimulation() * action.stimulations() == 105
                && DeveloperType.NORMAL.actualConsumption(action.stimulations()) == 3675,
                "Normal hardware keeps its existing105 ticks and3675 IF cost");
        var process = new DevelopmentProcess(); var shortBattery = new Battery(DeveloperType.PORTABLE, 3899);
        process.start(shortBattery, action);
        for (int tick = 0; tick < 129; tick++) process.tick();
        check(process.state() == DevelopmentProcess.State.DEVELOPING && shortBattery.energy == 29
                && !state.learned(ID), "one-IF-short development cannot grant early learning");
        process.tick();
        check(process.state() == DevelopmentProcess.State.FAILED && shortBattery.energy == 0 && !state.learned(ID),
                "unpaid final tick grants no learned bit");
        state.levelExperience = .375; state.cp = 17.25; state.overload = 4.5;
        var exactBattery = new Battery(DeveloperType.PORTABLE, 3900); process.start(exactBattery, action);
        for (int tick = 0; tick < 129; tick++) process.tick();
        check(!state.learned(ID) && exactBattery.energy == 30, "exact payment still waits for final tick");
        process.tick();
        check(process.state() == DevelopmentProcess.State.DONE && exactBattery.energy == 0 && state.learned(ID),
                "exact finite payment completes normal LV2 learning");
        check(state.exp(ID) == 0 && state.exp(ROOT) == 0 && state.levelExperience == .375
                && !state.learned(HighPowerSkills.PHASE_SIDESTEP) && !state.learned(HighPowerSkills.RETURN_MARK)
                && !state.learned(HighPowerSkills.RECOVERY_MARK),
                "learning grants no mastery, XP or sibling learning");
        check(state.cp == state.maxCp() && state.overload == 0,
                "ordinary classic SkillLearnEvent resource recalculation remains unchanged");
        check(SkillCatalog.levelSkillCount(state) == denominator
                && ClassicRules.levelThreshold(2, SkillCatalog.levelSkillCount(state)) == ClassicRules.levelThreshold(2, denominator),
                "321 cannot inflate the classic advancement threshold");
        check(!SkillCatalog.canLearn(state, skill) && DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill),
                "catalog hides duplicate learning while native completion keeps classic idempotent validation");
        state.unlearn(ID); process.start(new Battery(DeveloperType.PORTABLE, 3900), action);
        for (int tick = 0; tick < 129; tick++) process.tick();
        state.unlearn(ROOT); process.tick();
        check(process.state() == DevelopmentProcess.State.FAILED && !state.learned(ID),
                "paid completion revalidates the root prerequisite");
        state.learn(ROOT); process.start(new Battery(DeveloperType.NORMAL, 3675), action);
        for (int tick = 0; tick < 104; tick++) process.tick();
        state.setLevel(1); process.tick();
        check(process.state() == DevelopmentProcess.State.FAILED && !state.learned(ID),
                "paid completion revalidates LV2 after a downgrade");
        state.setLevel(2); process.start(new Battery(DeveloperType.PORTABLE, 3900), action);
        for (int tick = 0; tick < 129; tick++) process.tick();
        state.category = "meltdowner"; process.tick();
        check(process.state() == DevelopmentProcess.State.FAILED && !state.learned(ID),
                "paid completion rejects a category change even with stale learned311");
        state.category = "teleporter"; process.start(new Battery(DeveloperType.PORTABLE, 3900), action);
        for (int tick = 0; tick < 129; tick++) process.tick();
        try {
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("teleporter", ID),
                    new ClassicSkillConfiguration.Flags(false, false)));
            process.tick();
            check(process.state() == DevelopmentProcess.State.FAILED && !state.learned(ID),
                    "paid completion revalidates live321 configuration");
        } finally { SkillCatalog.resetConfiguration(); }
    }

    private static void savedIdentitiesAndGates() {
        var skill = SkillCatalog.find("teleporter", ID).orElseThrow();
        var old = ability("teleporter", 2);
        for (String id : List.of(HighPowerSkills.SAFE_LANDING_PROBE, HighPowerSkills.PHASE_SIDESTEP, ROOT, HighPowerSkills.RETURN_MARK, HighPowerSkills.RECOVERY_MARK, "threatening_teleport")) old.learn(id);
        check(PresetSkills.edit(old, 0, 0, HighPowerSkills.SAFE_LANDING_PROBE) && PresetSkills.edit(old, 0, 1, HighPowerSkills.PHASE_SIDESTEP)
                && PresetSkills.edit(old, 0, 2, HighPowerSkills.DISPLACED_PROJECTION)
                && PresetSkills.edit(old, 0, 3, "threatening_teleport")
                && PresetSkills.edit(old, 1, 0, HighPowerSkills.RETURN_MARK)
                && PresetSkills.edit(old, 1, 1, HighPowerSkills.RECOVERY_MARK), "prepare saved pre321 bindings");
        old.levelExperience = .375; old.cp = 17.25; old.overload = 4.5;
        var tag = AbilityStorage.encodeSaved(old); var state = AbilityStorage.decodeSaved(tag, old.cpDataConfig());
        check(!state.learned(ID) && AbilityStorage.encodeSaved(state).equals(tag),
                "pre321 save receives no learning, refill, preset remapping or schema migration");
        state.setActivateState(true); state.learn(ID);
        check(PresetSkills.edit(state, 3, 3, ID) && !PresetSkills.edit(state, 3, 2, ID)
                && SkillPresets.MAX_PRESETS == 4 && SkillPresets.MAX_KEYS == 4,
                "321 uses existing preset slots and duplicate protection");
        tag = AbilityStorage.encodeSaved(state); var saved = AbilityStorage.decodeSaved(tag, state.cpDataConfig());
        check(saved.learned(ID) && saved.exp(ID) == 0 && saved.presets.skill(3, 3).equals(ID)
                && saved.presets.skill(0, 0).equals(HighPowerSkills.SAFE_LANDING_PROBE)
                && saved.presets.skill(0, 1).equals(HighPowerSkills.PHASE_SIDESTEP)
                && saved.presets.skill(0, 2).equals(ROOT) && saved.presets.skill(0, 3).equals("threatening_teleport")
                && saved.presets.skill(1, 0).equals(HighPowerSkills.RETURN_MARK)
                && saved.presets.skill(1, 1).equals(HighPowerSkills.RECOVERY_MARK)
                && AbilityStorage.encodeSaved(saved).equals(tag), "learned321 and all existing bindings roundtrip exactly");
        check(HighPowerSkills.eligible(state, ID) && PresetSkills.selectable(state, ID), "learned activeLV2 admits321");
        state.setActivateState(false);
        check(!HighPowerSkills.eligible(state, ID), "inactive ability cannot cast321");
        state.setLevel(1); state.setActivateState(true);
        check(state.learned(ID) && !HighPowerSkills.eligible(state, ID) && !PresetSkills.selectable(state, ID)
                && state.presets.skill(3, 3).equals(ID), "downgrade retains raw save data while preventing use");
        state.setLevel(2); state.setActivateState(true);
        state.unlearn(ID);
        check(!HighPowerSkills.eligible(state, ID) && !PresetSkills.selectable(state, ID)
                && state.presets.skill(3, 3).equals(ID), "unlearning retains the raw mapping but prevents321 use");
        state.learn(ID);
        for (String category : List.of("electromaster", "meltdowner", "vecmanip")) {
            var wrong = ability(category, 5); wrong.experience.put(ROOT, 0.0); wrong.experience.put(ID, 0.0);
            check(!HighPowerSkills.eligible(wrong, ID) && !PresetSkills.selectable(wrong, ID)
                    && !DevelopmentActions.canLearn(wrong, DeveloperType.ADVANCED, skill)
                    && !SkillCatalog.canLearn(wrong, skill), "stale IDs cannot cross category " + category);
        }
        var disabled = new ClassicSkillConfiguration.Flags(false, false);
        try {
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("teleporter", ID), disabled));
            var fresh = ability("teleporter", 2); fresh.learn(ROOT);
            check(!DevelopmentActions.canLearn(fresh, DeveloperType.PORTABLE, skill) && !SkillCatalog.canLearn(fresh, skill),
                    "disabled321 refuses both learning validators");
            check(!HighPowerSkills.eligible(state, ID) && !PresetSkills.selectable(state, ID)
                    && state.learned(ID) && state.presets.skill(3, 3).equals(ID), "disable blocks use without deleting saved data");
            var snapshot = SkillCatalog.configurationSnapshot(); SkillCatalog.resetConfiguration();
            check(SkillCatalog.applyConfigurationSnapshot(snapshot) && !SkillCatalog.enabled("teleporter", ID),
                    "registered configuration snapshot carries321");
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("teleporter", ROOT), disabled));
            fresh.unlearn(ROOT);
            check(!SkillCatalog.canLearn(fresh, skill) && !DevelopmentActions.canLearn(fresh, DeveloperType.PORTABLE, skill),
                    "disabled311 cannot erase the root prerequisite");
        } finally { SkillCatalog.resetConfiguration(); }
    }

    private static void boundedLearningFeedback() {
        var request = new HighPowerLearningProtocol.Request(ID, Integer.MAX_VALUE);
        check(request.wire().startsWith("cross_substitution:") && ("00000000-0000-0000-0000-000000000000:" + request.wire()).length() <= 64
                && request.equals(HighPowerLearningProtocol.parse(request.wire())), "canonical321 wire fits machine request bound");
        var pending = new HighPowerLearningProtocol.Pending(); pending.begin(request);
        check(!pending.receive(HighPowerLearningProtocol.result(new HighPowerLearningProtocol.Request(ID, 1), true, ""))
                && pending.waiting(), "stale learning reply cannot consume current request");
        check(pending.receive(HighPowerLearningProtocol.result(request, false, "not_ready"))
                && !pending.waiting() && !pending.accepted() && pending.reason().equals("not_ready"),
                "321 receives correlated refusal through unchanged learning protocol");
    }

    private static void retainedLayoutAndOriginalArtwork() throws Exception {
        var original = ClassicDeveloperLayout.NODES.stream().filter(n -> n.category().equals("teleporter")).toList();
        check(original.size() == 12 && HighPowerDeveloperLayout.page("teleporter", HighPowerDeveloperLayout.Page.CLASSIC).equals(original),
                "all twelve classic TP nodes retain metadata and placement");
        var page = HighPowerDeveloperLayout.page("teleporter", HighPowerDeveloperLayout.Page.HIGH_POWER);
        check(page.size() == 13 && page.get(12).id().equals(HighPowerSkills.MULTIDIMENSIONAL_LANDING_ARRAY) && page.get(11).id().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR) && page.get(10).id().equals(HighPowerSkills.TACTICAL_COORDINATE_FORMATION) && page.get(7).id().equals(HighPowerSkills.SHORT_INTERCEPTION) && page.get(6).id().equals(HighPowerSkills.CORNER_EXCHANGE), "retained NEW333/331/343/342/353 page nodes precede appended NEW341");
        check(page.subList(0, 6).stream().map(ClassicDeveloperLayout.Node::id).toList().equals(List.of(
                HighPowerSkills.SAFE_LANDING_PROBE, HighPowerSkills.PHASE_SIDESTEP, ROOT,
                HighPowerSkills.RETURN_MARK, HighPowerSkills.RECOVERY_MARK, ID)),
                "321 appends after all five retained TP high-power nodes");
        String[] oldIds = {HighPowerSkills.SAFE_LANDING_PROBE, HighPowerSkills.PHASE_SIDESTEP, ROOT,
                HighPowerSkills.RETURN_MARK, HighPowerSkills.RECOVERY_MARK};
        int[] oldProposals = {313, 312, 311, 322, 323};
        double[] oldX = {118, 198, 38, 118, 38}; double[] oldY = {61, 61, 61, 105, 105};
        String[] oldParents = {"", HighPowerSkills.SAFE_LANDING_PROBE, HighPowerSkills.SAFE_LANDING_PROBE,
                HighPowerSkills.SAFE_LANDING_PROBE, ROOT};
        for (int i = 0; i < oldIds.length; i++) {
            var previous = page.get(i);
            check(previous.id().equals(oldIds[i]) && previous.index() == oldProposals[i]
                    && previous.level() == (i >= 3 ? 2 : 1) && previous.x() == oldX[i] && previous.y() == oldY[i]
                    && previous.parent().equals(oldParents[i]),
                    "prior TP identity, level, position and prerequisite retained " + oldIds[i]);
        }
        var node = page.get(5);
        check(node.index() == 321 && node.level() == 2 && node.x() == 198 && node.y() == 105 && node.parent().equals(ROOT),
                "321 occupies its approved LV2 node and depends on learned311");
        check(node.conditions().size() == 2 && node.conditions().get(0).get("minimum").getAsString().equals("portable")
                && node.conditions().get(1).get("type").getAsString().equals("skill_dependency")
                && node.conditions().get(1).get("id").getAsString().equals(ROOT)
                && node.conditions().get(1).get("minimum_exp").getAsDouble() == 0,
                "displayed conditions match canonical Portable and zero-mastery311 requirements");
        for (var item : page) {
            check(item.x() + 8 - 18.6 >= 0 && item.x() + 8 + 18.6 <= 257
                    && item.y() + 8 - 18.6 >= 0 && item.y() + 8 + 18.6 <= 139,
                    "full hover fits retained viewport " + item.id());
            for (var other : page) if (item != other)
                check(Math.hypot(item.x() - other.x(), item.y() - other.y()) > 37.2,
                        "full hover circles remain disjoint " + item.id() + "/" + other.id());
        }
        var root = page.get(2);
        var route = HighPowerDeveloperLayout.crossSubstitutionDependency(1);
        check(route.size() == 3 && HighPowerDeveloperLayout.crossSubstitutionDependency(0).isEmpty()
                && HighPowerDeveloperLayout.crossSubstitutionDependency(Double.NaN).isEmpty(),
                "321 routes through the retained row gap with native reveal timing");
        check(Math.abs(Math.hypot(route.getFirst().x0() - root.x() - 8, route.getFirst().y0() - root.y() - 8)
                - ClassicDeveloperTimeline.LINK_INSET) < 1e-10
                && Math.abs(Math.hypot(route.getLast().x1() - node.x() - 8, route.getLast().y1() - node.y() - 8)
                - ClassicDeveloperTimeline.LINK_INSET) < 1e-10, "321 dependency retains both native endpoint insets");
        double length = route.stream().mapToDouble(e -> Math.hypot(e.x1() - e.x0(), e.y1() - e.y0())).sum();
        for (double reveal : new double[]{0, .1, .5, .9, 1}) {
            var edges = HighPowerDeveloperLayout.crossSubstitutionDependency(reveal);
            check(Math.abs(edges.stream().mapToDouble(e -> Math.hypot(e.x1() - e.x0(), e.y1() - e.y0())).sum()
                    - length * reveal) < 1e-9, "321 reveal traverses the entire routed length continuously");
            for (int i = 0; i < edges.size(); i++) {
                var edge = edges.get(i);
                check(Math.abs(Math.hypot(edge.nx(), edge.ny()) - ClassicDeveloperTimeline.LINK_WIDTH / 2) < 1e-10,
                        "321 link retains native 5.5px width");
                check(Math.min(edge.x0(), edge.x1()) - Math.abs(edge.nx()) - 5 >= 0
                        && Math.max(edge.x0(), edge.x1()) + Math.abs(edge.nx()) + 5 <= 257
                        && Math.min(edge.y0(), edge.y1()) - Math.abs(edge.ny()) - 5 >= 0
                        && Math.max(edge.y0(), edge.y1()) + Math.abs(edge.ny()) + 5 <= 139,
                        "321 full link plus maximum parallax fits retained viewport");
                if (i > 0) check(edge.x0() == edges.get(i - 1).x1() && edge.y0() == edges.get(i - 1).y1(),
                        "321 dependency segments meet without gaps");
                for (var other : page) if (!other.id().equals(ROOT) && !other.id().equals(ID))
                    check(java.awt.geom.Line2D.ptSegDist(edge.x0(), edge.y0(), edge.x1(), edge.y1(),
                            other.x() + 8, other.y() + 8) > 18.6 + ClassicDeveloperTimeline.LINK_WIDTH / 2,
                            "321 full-width link clears unrelated full-hover circle " + other.id());
            }
        }
        String screen = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cn/academy/port/client/ClassicDeveloperScreen.java"));
        check(screen.contains("HighPowerDeveloperLayout.crossSubstitutionDependency(reveal.line())")
                && screen.contains("HighPowerSkills.CROSS_SUBSTITUTION)&&navigation.page()==HighPowerDeveloperLayout.Page.HIGH_POWER"),
                "actual321 developer renderer uses the tested route on its high-power page");
        var nav = new HighPowerDeveloperLayout.Navigation(); nav.synchronize("teleporter");
        check(!nav.select(ID) && nav.switchTo(HighPowerDeveloperLayout.Page.HIGH_POWER) && nav.select(ID),
                "321 is selectable only from its actual page");
        check(nav.beginLearning() && nav.back() == HighPowerDeveloperLayout.Back.BUSY, "pending321 retains navigation fence");
        nav.finishLearning();
        check(nav.back() == HighPowerDeveloperLayout.Back.SELECTION && nav.back() == HighPowerDeveloperLayout.Back.CLASSIC_PAGE,
                "Back dismisses321 then restores canonical page");
        check(Arrays.equals(resource("/assets/academy/textures/abilities/teleporter/skills/threatening_teleport.png"),
                resource("/assets/academy/textures/abilities/teleporter/skills/cross_substitution.png")),
                "321 reuses original icon bytes unchanged");
        String notice = new String(resource("/META-INF/ACADEMY-NOTICE"), StandardCharsets.UTF_8);
        check(notice.contains("NEW321 cross_substitution") && notice.contains("teleporter/threatening_teleport.png")
                && notice.contains("Lambda Innovation"), "packaged attribution names original artwork and author");
    }

    private static void localizedPlayerContract() throws Exception {
        var english = language("en_us");
        var keys = english.keySet().stream().filter(k -> k.startsWith(PREFIX)).collect(Collectors.toSet());
        check(keys.containsAll(Set.of(PREFIX + "marked", PREFIX + "blocked", PREFIX + "refused")),
                "mark feedback and refusal have player labels");
        for (String reason : List.of("crouch_unsupported", "three_needles_required", "refund_pending",
                "medium_unavailable", "out_of_range", "origin_blocked", "not_ready", "cooldown", "expired",
                "spawn_rejected", "publication_unconfirmed", "target_changed", "request_fence", "unloaded_chunk",
                "origin_fluid", "origin_occupied", "interaction_denied", "point_changed", "visual_unavailable"))
            check(keys.contains(PREFIX + "reason." + reason), "321 has a specific label for " + reason);
        for (String locale : List.of("en_us", "zh_cn", "zh_tw", "ja_jp")) {
            var words = language(locale);
            check(words.keySet().stream().filter(k -> k.startsWith(PREFIX)).collect(Collectors.toSet()).equals(keys),
                    "all321 status and reason tokens match across locales " + locale);
            for (String key : java.util.stream.Stream.concat(keys.stream(), List.of(
                    "ac.ability.teleporter.cross_substitution.name", "ac.ability.teleporter.cross_substitution.desc").stream()).toList())
                check(words.has(key) && !words.get(key).getAsString().isBlank(), "nonblank321 translation " + locale + "/" + key);
            check(words.get(PREFIX + "blocked").getAsString().contains("%s"), "refusal keeps reason placeholder " + locale);
            String description = words.get("ac.ability.teleporter.cross_substitution.desc").getAsString();
            for (String value : List.of("academy:needle", "400", "A/B/A", "0/4/8", "0.5D", "1.5D", "32", "130", "3900",
                    "12 / 6 / 3 / 0.75 / 0.075", "60 / 30 / 16 / 6 / 1"))
                check(description.contains(value), "explicit321 bounded attack and finite-learning contract " + locale + "/" + value);
        }
        String description = english.get("ac.ability.teleporter.cross_substitution.desc").getAsString();
        for (String limit : List.of("For self-swap, crouch and fresh-tap", "0.75 m horizontally and 0.125 m vertically",
                "exactly one mark", "clear route of at most 32 m", "one whole-salvo CP payment",
                "Ordinary preflight refusal keeps both marks and their original expiry for free",
                "callback cancellation or redirection consumes the marks for free",
                "an unconfirmed paid failure is terminal",
                "placing B never refreshes A", "3 real plain academy:needle needles", "native armor and native i-frames",
                "invulnerability frames can reject later hits", "Each actual native contact is checked separately",
                "The target endpoint never tracks movement", "cumulative flight", "At most 3 outstanding needles",
                "Recovery Mark (NEW323)", "Marking and refusal before publication are free",
                "A partial salvo costs only its published needles, one fixed third each",
                "Committed but unconfirmed publication may already spend CP", "No mastery gain"))
            check(description.contains(limit), "honest321 player contract states " + limit);
    }
}
