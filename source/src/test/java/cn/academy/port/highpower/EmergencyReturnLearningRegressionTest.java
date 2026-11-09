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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** NEW342 catalog, ordinary paid development and developer-page contracts. No rendered-client claim. */
public final class EmergencyReturnLearningRegressionTest {
    private static final String ID = HighPowerSkills.EMERGENCY_RETURN;
    private static final String ROOT = HighPowerSkills.RETURN_MARK;
    private static final String PREFIX = "academy.highpower.emergency_return.";
    private static final List<String> PREVIOUS_IDS = List.of(
            "forked_arc", "magnetic_bracer", "charge_decoy", "polarity_tether", "conductive_circuit",
            "grounding_return", "magnetic_ground_step", "magnetic_item_pull", "iron_sand_lance",
            "thunder_net", "potential_buffer", "rail_thunder_array", "magnetic_domain", "dual_point",
            "scatter_plates", "beam_etching", "thermal_shell", "folded_mirror_array", "disintegration_sweep",
            "beam_step", "particle_laminate", "directional_dissipation_curtain", "multicore_canopy",
            "zero_gap_armor", "safe_landing_probe", "phase_sidestep", "displaced_projection", "return_mark",
            "recovery_mark", "cross_substitution", "corner_exchange", "short_interception", "spatial_relay");
    private static final List<String> PREVIOUS_TP = PREVIOUS_IDS.subList(24, 33);
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
        try (var in = Objects.requireNonNull(EmergencyReturnLearningRegressionTest.class.getResourceAsStream(path), path)) {
            return in.readAllBytes();
        }
    }
    private static JsonObject language(String locale) throws Exception {
        return JsonParser.parseString(new String(resource("/assets/academy/lang/" + locale + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
    }
    public static void main(String[] args) throws Exception {
        CurrentHighPowerInventory.verify();
        SkillCatalog.resetConfiguration();
        try {
            appendOnlyRegistration();
            finiteLearningAndRevalidation();
            savedIdentitiesAndGates();
            boundedLearningFeedback();
            retainedLayoutAndOriginalArtwork();
            localizedPlayerContract();
        } finally { SkillCatalog.resetConfiguration(); }
        System.out.println("EmergencyReturnLearningRegressionTest: " + checks + " checks passed");
    }

    private static void appendOnlyRegistration() {
        var skill = SkillCatalog.find("teleporter", ID).orElseThrow();
        check(ID.equals("emergency_return") && HighPowerSkills.EMERGENCY_RETURN_PROPOSAL == 342,
                "exact NEW342 proposal and persistent identifier");
        check(skill.level() == 4 && skill.controllable() && skill.anyLearnedSkillLevel() == 0
                && skill.requirements().equals(List.of(new SkillCatalog.Requirement(ROOT, 0))),
                "LV4 requires exactly learned322 with zero mastery and no alternate-level shortcut");
        check(HighPowerSkills.ALL.subList(0, 33).stream().map(SkillCatalog.Skill::id).toList().equals(PREVIOUS_IDS),
                "all thirty-three previous high-power IDs and ordinals remain unchanged");
        check(HighPowerSkills.ALL.get(33).equals(skill) && SkillCatalog.REGISTERED.get(83).equals(skill),
                "342 is the single append after fifty classic and thirty-three high-power entries");
        check(HighPowerSkills.IDS.contains(ID) && SkillAvailability.learnable(ID)
                && PresetSkills.REGISTERED.contains(ID)
                && SkillCatalog.REGISTERED_CONFIG_KEYS.contains(ClassicSkillConfiguration.key("teleporter", ID)),
                "existing derived learning, preset and configuration registries admit342");
        check(SkillCatalog.ALL.size() == 50 && ClassicDeveloperLayout.NODES.size() == 50
                && PresetSkills.IMPLEMENTED.size() == 35 && SkillCatalog.CONFIG_KEYS.size() == 41
                && SkillCatalog.REGISTERED.subList(0, 50).equals(SkillCatalog.ALL),
                "classic fifty entries, forty-one config aliases and thirty-five preset denominator are unchanged");
        check(SkillCatalog.REGISTERED.size() == (111 + cn.academy.port.canon.CanonSkills.ALL.size()) && cn.academy.port.SkillCatalog.REGISTERED.get(100).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.SkillCatalog.REGISTERED.get(99).id().equals("point_meltdown") && PresetSkills.REGISTERED.size() == (96 + cn.academy.port.canon.CanonSkills.IDS.size()) && cn.academy.port.preset.PresetSkills.REGISTERED.contains("remote_scatter_relay") && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.preset.PresetSkills.REGISTERED.contains("point_meltdown")
                && HighPowerSkills.ALL.size() == 61 && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.highpower.HighPowerSkills.ALL.get(49).id().equals("point_meltdown") && HighPowerSkills.ALL.get(48).id().equals("wind_pressure_lift") && HighPowerSkills.ALL.get(47).id().equals("multi_axis_reflection") && HighPowerSkills.ALL.get(45).id().equals("circulation_listening") && HighPowerSkills.ALL.get(44).id().equals("circulation_relief") && HighPowerSkills.ALL.get(43).id().equals("trajectory_return") && HighPowerSkills.ALL.get(42).id().equals("yaw_palm") && HighPowerSkills.ALL.get(41).id().equals("inertial_anchor") && HighPowerSkills.ALL.get(40).id().equals("shear_windblade") && HighPowerSkills.ALL.get(39).id().equals("braking_touch") && HighPowerSkills.IDS.size() == (61 + cn.academy.port.canon.CanonSkills.IDS.size()) && cn.academy.port.highpower.HighPowerSkills.IDS.contains("remote_scatter_relay") && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.highpower.HighPowerSkills.IDS.contains("point_meltdown") && HighPowerSkills.IDS.contains("wind_pressure_lift") && HighPowerSkills.IDS.contains("multi_axis_reflection") && HighPowerSkills.IDS.contains("circulation_listening") && HighPowerSkills.IDS.contains("circulation_relief") && HighPowerSkills.IDS.contains("trajectory_return") && HighPowerSkills.IDS.contains("yaw_palm") && HighPowerSkills.IDS.contains("inertial_anchor") && HighPowerSkills.IDS.contains("shear_windblade")
                && SkillCatalog.REGISTERED_CONFIG_KEYS.size() == (102 + cn.academy.port.canon.CanonSkills.ALL.size()) && cn.academy.port.SkillCatalog.REGISTERED_CONFIG_KEYS.contains(cn.academy.port.core.ClassicSkillConfiguration.key("meltdowner", "remote_scatter_relay")) && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0))
                && HighPowerSkills.ALL.get(35).id().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR)
                && SkillCatalog.REGISTERED.get(85).equals(HighPowerSkills.ALL.get(35)),
                "NEW342 and NEW353 ordinals remain fixed; NEW341 appends once for current exact counts86/71/36/77");
        check(HighPowerSkills.ALL.get(34).id().equals("tactical_coordinate_formation") && SkillCatalog.REGISTERED.get(84).equals(HighPowerSkills.ALL.get(34)), "only reviewed NEW353 appends after retained NEW342");
        for (int level = 1; level <= 5; level++) {
            var state = ability("teleporter", level);
            final int currentLevel = level;
            check(SkillCatalog.levelSkillCount(state) == SkillCatalog.ALL.stream().filter(s -> s.category().equals("teleporter")
                    && s.level() == currentLevel && s.controllable()).count(), "342 never changes classic advancement atLV" + level);
        }
    }

    private static void finiteLearningAndRevalidation() {
        var skill = SkillCatalog.find("teleporter", ID).orElseThrow();
        var state = ability("teleporter", 3); state.learn(ROOT);
        check(!SkillCatalog.canLearn(state, skill) && !DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill),
                "learned322 and Advanced developer cannot bypass LV4");
        state.setLevel(4); state.unlearn(ROOT);
        for (String sibling : PREVIOUS_TP) if (!sibling.equals(ROOT)) state.learn(sibling);
        check(!SkillCatalog.canLearn(state, skill) && !DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill),
                "all other TP additions together cannot substitute for learned322");
        check(!HighPowerSkills.eligible(state, ID) && !PresetSkills.selectable(state, ID)
                && !PresetSkills.edit(state, 0, 0, ID), "unlearned342 cannot cast or enter a preset");
        for (String sibling : PREVIOUS_TP) state.unlearn(sibling);
        state.learn(ROOT);
        check(state.exp(ROOT) == 0 && SkillCatalog.canLearn(state, skill)
                && !DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill)
                && !DevelopmentActions.canLearn(state, DeveloperType.NORMAL, skill)
                && DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill)
                && DeveloperType.minimumForSkill(4) == DeveloperType.ADVANCED,
                "zero-mastery322 requires canonical LV4 Advanced hardware");
        var action = DevelopmentActions.skill(state, skill);
        check(action.id().equals("skill:emergency_return") && action.stimulations() == 11
                && ClassicRules.learningStimulations(4) == 11
                && DeveloperType.ADVANCED.ticksPerStimulation() * action.stimulations() == 176
                && DeveloperType.ADVANCED.estimatedConsumption(action.stimulations()) == 6600
                && DeveloperType.ADVANCED.actualConsumption(action.stimulations()) == 7040,
                "eleven native Advanced stimulations charge176 ticks/7040 IF, including the native extra tick");
        var process = new DevelopmentProcess(); var shortBattery = new Battery(DeveloperType.ADVANCED, 7039);
        process.start(shortBattery, action);
        for (int tick = 0; tick < 175; tick++) process.tick();
        check(process.state() == DevelopmentProcess.State.DEVELOPING && shortBattery.energy == 39
                && !state.learned(ID), "one IF short cannot grant early learning");
        process.tick();
        check(process.state() == DevelopmentProcess.State.FAILED && shortBattery.energy == 0 && !state.learned(ID),
                "unpaid final Advanced tick grants no learned bit");
        state.levelExperience = .375; state.cp = 17.25; state.overload = 4.5;
        var exactBattery = new Battery(DeveloperType.ADVANCED, 7040); process.start(exactBattery, action);
        for (int tick = 0; tick < 175; tick++) process.tick();
        check(!state.learned(ID) && exactBattery.energy == 40, "exact battery also waits for the final paid tick");
        process.tick();
        check(process.state() == DevelopmentProcess.State.DONE && exactBattery.energy == 0 && state.learned(ID),
                "finite exact payment completes ordinary LV4 learning");
        check(state.exp(ID) == 0 && state.exp(ROOT) == 0 && state.levelExperience == .375
                && PREVIOUS_TP.stream().filter(id -> !id.equals(ROOT)).noneMatch(state::learned),
                "learning grants no mastery, XP or sibling skills");
        check(state.cp == state.maxCp() && state.overload == 0,
                "ordinary SkillLearnEvent resource recalculation is preserved");
        check(!SkillCatalog.canLearn(state, skill) && DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill),
                "catalog hides duplicate learning while native completion remains idempotent");
        state.unlearn(ID);
        var interruptedBattery = new Battery(DeveloperType.ADVANCED, 7040);
        process.start(interruptedBattery, action);
        for (int tick = 0; tick < 88; tick++) process.tick();
        process.abort();
        check(process.state() == DevelopmentProcess.State.FAILED && !state.learned(ID)
                && interruptedBattery.energy == 3520 && state.levelExperience == .375,
                "cancelled ordinary development preserves paid energy loss without granting learning or XP");
        for (String changed : List.of("prerequisite", "level", "category", "configuration")) {
            state.category = "teleporter"; state.setLevel(4); state.unlearn(ID); state.learn(ROOT);
            process.start(new Battery(DeveloperType.ADVANCED, 7040), action);
            for (int tick = 0; tick < 175; tick++) process.tick();
            switch (changed) {
                case "prerequisite" -> state.unlearn(ROOT);
                case "level" -> state.setLevel(3);
                case "category" -> state.category = "meltdowner";
                case "configuration" -> SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("teleporter", ID),
                        new ClassicSkillConfiguration.Flags(false, false)));
                default -> throw new AssertionError(changed);
            }
            try {
                process.tick();
                check(process.state() == DevelopmentProcess.State.FAILED && !state.learned(ID),
                        "paid completion revalidates " + changed);
            } finally { SkillCatalog.resetConfiguration(); }
        }
    }

    private static void savedIdentitiesAndGates() {
        var skill = SkillCatalog.find("teleporter", ID).orElseThrow();
        var old = ability("teleporter", 4);
        for (String id : PREVIOUS_TP) old.learn(id);
        old.learn("threatening_teleport");
        for (int i = 0; i < PREVIOUS_TP.size(); i++)
            check(PresetSkills.edit(old, i / 4, i % 4, PREVIOUS_TP.get(i)), "prepare previous saved binding " + i);
        check(PresetSkills.edit(old, 2, 1, "threatening_teleport"), "classic skill retains its binding");
        old.levelExperience = .375; old.cp = 17.25; old.overload = 4.5;
        var tag = AbilityStorage.encodeSaved(old); var state = AbilityStorage.decodeSaved(tag, old.cpDataConfig());
        check(!state.learned(ID) && AbilityStorage.encodeSaved(state).equals(tag),
                "pre342 save gets no learned bit, refill, binding remapping or schema migration");
        state.setActivateState(true); state.learn(ID);
        check(PresetSkills.edit(state, 3, 3, ID) && !PresetSkills.edit(state, 3, 2, ID)
                && SkillPresets.MAX_PRESETS == 4 && SkillPresets.MAX_KEYS == 4,
                "342 retains existing four by four preset slots and duplicate protection");
        tag = AbilityStorage.encodeSaved(state); var saved = AbilityStorage.decodeSaved(tag, state.cpDataConfig());
        check(saved.learned(ID) && saved.exp(ID) == 0 && saved.presets.skill(3, 3).equals(ID)
                && saved.presets.skill(2, 1).equals("threatening_teleport")
                && AbilityStorage.encodeSaved(saved).equals(tag), "new and classic saved IDs roundtrip exactly");
        for (int i = 0; i < PREVIOUS_TP.size(); i++)
            check(saved.learned(PREVIOUS_TP.get(i)) && saved.presets.skill(i / 4, i % 4).equals(PREVIOUS_TP.get(i)),
                    "old learned bit and binding retained " + PREVIOUS_TP.get(i));
        for (int level = 1; level <= 5; level++) {
            state.setLevel(level); state.setActivateState(true);
            check(HighPowerSkills.eligible(state, ID) == (level >= 4) && PresetSkills.selectable(state, ID) == (level >= 4)
                    && state.learned(ID) && state.presets.skill(3, 3).equals(ID), "LV" + level + " gate preserves raw saved342");
        }
        state.setActivateState(false);
        check(!HighPowerSkills.eligible(state, ID), "inactive ability cannot cast342");
        state.setActivateState(true); state.unlearn(ID);
        check(!HighPowerSkills.eligible(state, ID) && !PresetSkills.selectable(state, ID)
                && state.presets.skill(3, 3).equals(ID), "unlearning blocks use without deleting raw mapping");
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
            var fresh = ability("teleporter", 4); fresh.learn(ROOT);
            check(!DevelopmentActions.canLearn(fresh, DeveloperType.ADVANCED, skill) && !SkillCatalog.canLearn(fresh, skill)
                    && !HighPowerSkills.eligible(state, ID) && !PresetSkills.selectable(state, ID)
                    && state.learned(ID) && state.presets.skill(3, 3).equals(ID), "disabled342 blocks learning and use while retaining save");
            var snapshot = SkillCatalog.configurationSnapshot(); SkillCatalog.resetConfiguration();
            check(SkillCatalog.applyConfigurationSnapshot(snapshot) && !SkillCatalog.enabled("teleporter", ID),
                    "normal registered configuration snapshot carries342");
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("teleporter", ROOT), disabled));
            fresh.unlearn(ROOT);
            check(!SkillCatalog.canLearn(fresh, skill) && !DevelopmentActions.canLearn(fresh, DeveloperType.ADVANCED, skill),
                    "disabling322 cannot erase its prerequisite");
        } finally { SkillCatalog.resetConfiguration(); }
    }

    private static void boundedLearningFeedback() {
        var request = new HighPowerLearningProtocol.Request(ID, Integer.MAX_VALUE);
        check(request.wire().startsWith("emergency_return:") && ("00000000-0000-0000-0000-000000000000:" + request.wire()).length() <= 64
                && request.equals(HighPowerLearningProtocol.parse(request.wire())), "342 fits unchanged bounded machine request");
        var pending = new HighPowerLearningProtocol.Pending(); pending.begin(request);
        check(!pending.receive(HighPowerLearningProtocol.result(new HighPowerLearningProtocol.Request(ID, 1), true, ""))
                && pending.waiting(), "old sequence cannot consume342 learning feedback");
        check(pending.receive(HighPowerLearningProtocol.result(request, false, "not_ready"))
                && !pending.waiting() && !pending.accepted() && pending.reason().equals("not_ready"),
                "342 uses correlated refusal through existing protocol");
        pending.begin(request);
        check(!pending.receive(HighPowerLearningProtocol.result(new HighPowerLearningProtocol.Request(HighPowerSkills.CORNER_EXCHANGE,
                request.sequence()), true, "")) && pending.waiting(), "NEW333 feedback cannot consume NEW342 request");
        check(pending.receive(HighPowerLearningProtocol.result(request, true, "")) && pending.accepted()
                && !pending.receive(HighPowerLearningProtocol.result(request, true, "")), "matching342 acknowledgement is one-shot");
        var feedback = new HighPowerDeveloperLayout.DevelopmentFeedback();
        feedback.begin("skill:" + ID);
        check(feedback.receive("DONE", "", true) == HighPowerDeveloperLayout.DevelopmentUpdate.IGNORED,
                "old blank terminal snapshot cannot finish new342 attempt before matching start");
        check(feedback.receive("DEVELOPING", "skill:" + ID, false) == HighPowerDeveloperLayout.DevelopmentUpdate.IGNORED
                && feedback.receive("DEVELOPING", "skill:" + ID, true) == HighPowerDeveloperLayout.DevelopmentUpdate.PROGRESS
                && feedback.receive("DONE", "", true) == HighPowerDeveloperLayout.DevelopmentUpdate.DONE,
                "matching accepted342 start owns native blank completion snapshot");
        feedback.begin("skill:" + ID);
        feedback.receive("DEVELOPING", "skill:" + ID, true);
        check(feedback.receive("DEVELOPING", "skill:corner_exchange", true) == HighPowerDeveloperLayout.DevelopmentUpdate.DISPLACED
                && feedback.receive("DONE", "", true) == HighPowerDeveloperLayout.DevelopmentUpdate.IGNORED,
                "replacement with333 relinquishes342 completion ownership");
    }

    private static void retainedLayoutAndOriginalArtwork() throws Exception {
        var original = ClassicDeveloperLayout.NODES.stream().filter(n -> n.category().equals("teleporter")).toList();
        check(original.size() == 12 && HighPowerDeveloperLayout.page("teleporter", HighPowerDeveloperLayout.Page.CLASSIC).equals(original),
                "twelve classic TP nodes retain all metadata and placement");
        var page = HighPowerDeveloperLayout.page("teleporter", HighPowerDeveloperLayout.Page.HIGH_POWER);
        check(page.size() == CurrentHighPowerInventory.pageSize(page) && page.get(12).id().equals(HighPowerSkills.MULTIDIMENSIONAL_LANDING_ARRAY) && page.get(11).id().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR) && page.get(10).id().equals(HighPowerSkills.TACTICAL_COORDINATE_FORMATION) && page.subList(0, 9).stream().map(ClassicDeveloperLayout.Node::id).toList().equals(PREVIOUS_TP),
                "nine existing TP page identities remain an exact prefix");
        int[] proposals = {313, 312, 311, 322, 323, 321, 333, 331, 343};
        double[] xs = {118, 198, 38, 118, 38, 198, 198, 118, 38}, ys = {61, 61, 61, 105, 105, 105, 17, 17, 17};
        String[] parents = {"", "safe_landing_probe", "safe_landing_probe", "safe_landing_probe", "displaced_projection", "displaced_projection", "return_mark", "cross_substitution", "recovery_mark"};
        for (int i = 0; i < 9; i++) {
            var prior = page.get(i);
            check(prior.index() == proposals[i] && prior.level() == (i < 3 ? 1 : i < 6 ? 2 : i < 8 ? 3 : 4)
                    && prior.x() == xs[i] && prior.y() == ys[i] && prior.parent().equals(parents[i]),
                    "prior TP proposal, level, position and prerequisite retained " + prior.id());
        }
        var node = page.get(9);
        check(node.id().equals(ID) && node.index() == 342 && node.level() == 4
                && node.x() == 78 && node.y() == 17 && node.parent().equals(ROOT), "342 occupies distinct LV4 slot and learned322 branch");
        check(node.conditions().size() == 2 && node.conditions().get(0).get("minimum").getAsString().equals("advanced")
                && node.conditions().get(1).get("id").getAsString().equals(ROOT)
                && node.conditions().get(1).get("minimum_exp").getAsDouble() == 0,
                "displayed machine and skill requirements match native learning");
        for (var item : page) {
            check(item.x() + 8 - 18.6 >= 0 && item.x() + 8 + 18.6 <= 257
                    && item.y() + 8 - 18.6 >= 0 && item.y() + 8 + 18.6 <= 139,
                    "full hover circle fits viewport " + item.id());
            for (var other : page) if (item != other)
                check(Math.hypot(item.x() - other.x(), item.y() - other.y()) > 37.2,
                        "full hover circles do not overlap " + item.id() + "/" + other.id());
        }
        var root = page.get(3); var route = HighPowerDeveloperLayout.emergencyReturnDependency(1);
        check(route.size() == 5 && HighPowerDeveloperLayout.emergencyReturnDependency(0).isEmpty()
                && HighPowerDeveloperLayout.emergencyReturnDependency(Double.NaN).isEmpty(), "342 reveal has finite routed edges");
        check(Math.abs(Math.hypot(route.getFirst().x0() - root.x() - 8, route.getFirst().y0() - root.y() - 8)
                - ClassicDeveloperTimeline.LINK_INSET) < 1e-10
                && Math.abs(Math.hypot(route.getLast().x1() - node.x() - 8, route.getLast().y1() - node.y() - 8)
                - ClassicDeveloperTimeline.LINK_INSET) < 1e-10, "342 preserves native endpoint insets");
        double[][] points = {{126,113},{126,91},{16,91},{16,47},{86,47},{86,25}};
        check(root.x() + 8 == points[0][0] && root.y() + 8 == points[0][1]
                && node.x() + 8 == points[5][0] && node.y() + 8 == points[5][1],
                "342 retains original prerequisite and skill centers while avoiding the new341 hover ring");
        for (int i = 0; i < 5; i++) {
            var edge = route.get(i);
            double startY = points[i][1] - (i == 0 ? ClassicDeveloperTimeline.LINK_INSET : 0);
            double endY = points[i + 1][1] + (i == 4 ? ClassicDeveloperTimeline.LINK_INSET : 0);
            check(Math.abs(edge.x0() - points[i][0]) < 1e-10 && Math.abs(edge.y0() - startY) < 1e-10
                    && Math.abs(edge.x1() - points[i + 1][0]) < 1e-10 && Math.abs(edge.y1() - endY) < 1e-10,
                    "342 exact route follows(126,113)-(126,91)-(16,91)-(16,47)-(86,47)-(86,25) with native endpoint insets");
        }
        double length = route.stream().mapToDouble(e -> Math.hypot(e.x1() - e.x0(), e.y1() - e.y0())).sum();
        for (double reveal : new double[]{0, .1, .5, .9, 1}) {
            var edges = HighPowerDeveloperLayout.emergencyReturnDependency(reveal);
            check(Math.abs(edges.stream().mapToDouble(e -> Math.hypot(e.x1() - e.x0(), e.y1() - e.y0())).sum()
                    - length * reveal) < 1e-9, "342 line reveal traverses full route continuously");
            for (int i = 0; i < edges.size(); i++) {
                var edge = edges.get(i);
                check(Math.abs(Math.hypot(edge.nx(), edge.ny()) - ClassicDeveloperTimeline.LINK_WIDTH / 2) < 1e-10,
                        "342 uses native line width");
                check(Math.min(edge.x0(), edge.x1()) - Math.abs(edge.nx()) - 5 >= 0
                        && Math.max(edge.x0(), edge.x1()) + Math.abs(edge.nx()) + 5 <= 257
                        && Math.min(edge.y0(), edge.y1()) - Math.abs(edge.ny()) - 5 >= 0
                        && Math.max(edge.y0(), edge.y1()) + Math.abs(edge.ny()) + 5 <= 139,
                        "full line plus maximum parallax remains within viewport");
                if (i > 0) check(edge.x0() == edges.get(i - 1).x1() && edge.y0() == edges.get(i - 1).y1(),
                        "342 routed edges remain joined");
                for (var other : page) if (!other.id().equals(ROOT) && !other.id().equals(ID))
                    check(java.awt.geom.Line2D.ptSegDist(edge.x0(), edge.y0(), edge.x1(), edge.y1(),
                            other.x() + 8, other.y() + 8) > 18.6 + ClassicDeveloperTimeline.LINK_WIDTH / 2,
                            "342 full line clears unrelated hover circle " + other.id());
            }
        }
        String screen = Files.readString(Path.of("src/main/java/cn/academy/port/client/ClassicDeveloperScreen.java"));
        check(screen.contains("UnifiedSkillTreeLayout.category(AcademyClient.state.category)") && !screen.contains("Page.HIGH_POWER")
                && cn.academy.port.client.UnifiedSkillTreeLayout.category("teleporter").stream().anyMatch(n -> n.id().equals(cn.academy.port.highpower.HighPowerSkills.EMERGENCY_RETURN)),
                "the merged single-page tree shows this skill beside the original teleporter skills");
        var nav = new HighPowerDeveloperLayout.Navigation(); nav.synchronize("teleporter");
        var merged = cn.academy.port.client.UnifiedSkillTreeLayout.category("teleporter");
        check(merged.stream().anyMatch(n -> !HighPowerSkills.contains(n.id())) && nav.selectIn(ID, merged),
                "new and original skills select directly in the same category tree");
        check(!nav.selectIn(ROOT, merged) && nav.beginLearning() && !nav.beginLearning()
                && nav.back() == HighPowerDeveloperLayout.Back.BUSY, "pending attempt owns its selection and cannot duplicate or dismiss");
        check(!nav.synchronize("teleporter") && nav.pending(), "matching category update retains learning attempt");
        nav.finishLearning();
        check(nav.back() == HighPowerDeveloperLayout.Back.SELECTION && nav.back() == HighPowerDeveloperLayout.Back.CLOSE_SCREEN,
                "Back dismisses selection then closes without an extra high-power page");
        check(Arrays.equals(resource("/assets/academy/textures/abilities/teleporter/skills/mark_teleport.png"),
                resource("/assets/academy/textures/abilities/teleporter/skills/emergency_return.png")), "342 reuses original icon bytes");
        String notice = new String(resource("/META-INF/ACADEMY-NOTICE"), StandardCharsets.UTF_8);
        check(notice.contains("NEW342 emergency_return") && notice.contains("teleporter/mark_teleport.png")
                && notice.contains("Lambda Innovation"), "packaged notice credits original artwork and author");
    }

    private static void localizedPlayerContract() throws Exception {
        var english = language("en_us");
        var keys = english.keySet().stream().filter(k -> k.startsWith(PREFIX)).collect(Collectors.toSet());
        check(!keys.isEmpty() && keys.stream().anyMatch(k -> k.startsWith(PREFIX + "reason.")),
                "342 has player feedback and specific refusal labels");
        for (String locale : List.of("en_us", "zh_cn", "zh_tw", "ja_jp")) {
            var words = language(locale);
            check(words.keySet().stream().filter(k -> k.startsWith(PREFIX)).collect(Collectors.toSet()).equals(keys),
                    "342 feedback keys match across locales " + locale);
            for (String key : java.util.stream.Stream.concat(keys.stream(), List.of(
                    "ac.ability.teleporter.emergency_return.name", "ac.ability.teleporter.emergency_return.desc").stream()).toList())
                check(words.has(key) && !words.get(key).getAsString().isBlank(), "nonblank342 text " + locale + "/" + key);
            SkillLocalizationContracts.verify(words, "teleporter", "emergency_return", locale);
        }
    }
}
