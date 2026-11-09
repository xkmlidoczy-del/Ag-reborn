package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillAvailability;
import cn.academy.port.SkillCatalog;
import cn.academy.port.client.ClassicDeveloperLayout;
import cn.academy.port.client.HighPowerDeveloperLayout;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.core.SkillPresets;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.preset.PresetSkills;
import java.util.List;
import java.util.Map;

/** NEW353 append-only catalog, exact paid LV5 learning, saved IDs and correlated machine feedback. */
public final class TacticalCoordinateLearningRegressionTest {
    private static final String ID = HighPowerSkills.TACTICAL_COORDINATE_FORMATION;
    private static final String ROOT = HighPowerSkills.SPATIAL_RELAY;
    private static final List<String> CLASSIC_IDENTITIES = List.of(
            "electromaster:arc_gen", "electromaster:charging", "electromaster:mag_movement", "electromaster:mag_manip",
            "electromaster:mine_detect", "electromaster:body_intensify", "electromaster:thunder_bolt", "electromaster:railgun",
            "electromaster:thunder_clap", "electromaster:brain_course", "electromaster:brain_course_advanced", "electromaster:mind_course",
            "meltdowner:electron_bomb", "meltdowner:rad_intensify", "meltdowner:scatter_bomb", "meltdowner:light_shield",
            "meltdowner:meltdowner", "meltdowner:mine_ray_basic", "meltdowner:ray_barrage", "meltdowner:jet_engine",
            "meltdowner:mine_ray_expert", "meltdowner:mine_ray_luck", "meltdowner:electron_missile", "meltdowner:brain_course",
            "meltdowner:brain_course_advanced", "meltdowner:mind_course", "teleporter:threatening_teleport", "teleporter:dim_folding_theorem",
            "teleporter:penetrate_teleport", "teleporter:mark_teleport", "teleporter:flesh_ripping", "teleporter:location_teleport",
            "teleporter:shift_tp", "teleporter:space_fluct", "teleporter:flashing", "teleporter:brain_course",
            "teleporter:brain_course_advanced", "teleporter:mind_course", "vecmanip:dir_shock", "vecmanip:ground_shock",
            "vecmanip:vec_accel", "vecmanip:vec_deviation", "vecmanip:dir_blast", "vecmanip:storm_wing",
            "vecmanip:blood_retro", "vecmanip:vec_reflection", "vecmanip:plasma_cannon", "vecmanip:brain_course",
            "vecmanip:brain_course_advanced", "vecmanip:mind_course");
    private static final List<String> PREVIOUS_IDS = List.of(
            "forked_arc", "magnetic_bracer", "charge_decoy", "polarity_tether", "conductive_circuit",
            "grounding_return", "magnetic_ground_step", "magnetic_item_pull", "iron_sand_lance",
            "thunder_net", "potential_buffer", "rail_thunder_array", "magnetic_domain", "dual_point",
            "scatter_plates", "beam_etching", "thermal_shell", "folded_mirror_array", "disintegration_sweep",
            "beam_step", "particle_laminate", "directional_dissipation_curtain", "multicore_canopy",
            "zero_gap_armor", "safe_landing_probe", "phase_sidestep", "displaced_projection", "return_mark",
            "recovery_mark", "cross_substitution", "corner_exchange", "short_interception", "spatial_relay", "emergency_return");
    private static final List<String> PREVIOUS_TP = PREVIOUS_IDS.subList(24, 34);
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
    public static void main(String[] args) throws Exception {
        SkillCatalog.resetConfiguration();
        try {
            appendOnlyRegistration();
            finiteLearningAndRevalidation();
            savedIdentitiesAndGates();
            boundedLearningFeedback();
        } finally { SkillCatalog.resetConfiguration(); }
        System.out.println("TacticalCoordinateLearningRegressionTest: " + checks + " checks passed");
    }

    private static void appendOnlyRegistration() {
        var skill = SkillCatalog.find("teleporter", ID).orElseThrow();
        check(ID.equals("tactical_coordinate_formation") && HighPowerSkills.TACTICAL_COORDINATE_FORMATION_PROPOSAL == 353,
                "exact NEW353 proposal and persistent identifier");
        check(skill.level() == 5 && skill.controllable() && skill.anyLearnedSkillLevel() == 0
                && skill.requirements().equals(List.of(new SkillCatalog.Requirement(ROOT, 0))),
                "LV5 requires exactly learned343 with zero mastery and no alternate-level shortcut");
        check(HighPowerSkills.ALL.subList(0, 34).stream().map(SkillCatalog.Skill::id).toList().equals(PREVIOUS_IDS),
                "all thirty-four previous high-power IDs and ordinals remain unchanged");
        check(HighPowerSkills.ALL.get(34).equals(skill) && SkillCatalog.REGISTERED.get(84).equals(skill),
                "353 is the single append after fifty classic and thirty-four high-power entries");
        check(HighPowerSkills.IDS.contains(ID) && SkillAvailability.learnable(ID)
                && PresetSkills.REGISTERED.contains(ID)
                && SkillCatalog.REGISTERED_CONFIG_KEYS.contains(ClassicSkillConfiguration.key("teleporter", ID)),
                "existing derived learning, preset and configuration registries admit353");
        check(SkillCatalog.REGISTERED.subList(0, 50).stream().map(entry -> entry.category() + ":" + entry.id()).toList().equals(CLASSIC_IDENTITIES),
                "all fifty classic category/skill identities retain exact ordinals");
        check(SkillCatalog.ALL.size() == 50 && ClassicDeveloperLayout.NODES.size() == 50
                && PresetSkills.IMPLEMENTED.size() == 35 && SkillCatalog.CONFIG_KEYS.size() == 41
                && SkillCatalog.REGISTERED.subList(0, 50).equals(SkillCatalog.ALL),
                "classic fifty entries, forty-one config aliases and thirty-five preset denominator are unchanged");
        check(SkillCatalog.REGISTERED.size() == 100 && cn.academy.port.SkillCatalog.REGISTERED.get(99).id().equals("point_meltdown") && PresetSkills.REGISTERED.size() == 85 && cn.academy.port.preset.PresetSkills.REGISTERED.contains("point_meltdown")
                && HighPowerSkills.ALL.size() == 50 && cn.academy.port.highpower.HighPowerSkills.ALL.get(49).id().equals("point_meltdown") && HighPowerSkills.ALL.get(48).id().equals("wind_pressure_lift") && HighPowerSkills.ALL.get(47).id().equals("multi_axis_reflection") && HighPowerSkills.ALL.get(45).id().equals("circulation_listening") && HighPowerSkills.ALL.get(44).id().equals("circulation_relief") && HighPowerSkills.ALL.get(43).id().equals("trajectory_return") && HighPowerSkills.ALL.get(42).id().equals("yaw_palm") && HighPowerSkills.ALL.get(41).id().equals("inertial_anchor") && HighPowerSkills.ALL.get(40).id().equals("shear_windblade") && HighPowerSkills.ALL.get(39).id().equals("braking_touch") && HighPowerSkills.IDS.size() == 50 && cn.academy.port.highpower.HighPowerSkills.IDS.contains("point_meltdown") && HighPowerSkills.IDS.contains("wind_pressure_lift") && HighPowerSkills.IDS.contains("multi_axis_reflection") && HighPowerSkills.IDS.contains("circulation_listening") && HighPowerSkills.IDS.contains("circulation_relief") && HighPowerSkills.IDS.contains("trajectory_return") && HighPowerSkills.IDS.contains("yaw_palm") && HighPowerSkills.IDS.contains("inertial_anchor") && HighPowerSkills.IDS.contains("shear_windblade")
                && SkillCatalog.REGISTERED_CONFIG_KEYS.size() == 91
                && HighPowerSkills.ALL.get(35).id().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR)
                && SkillCatalog.REGISTERED.get(85).equals(HighPowerSkills.ALL.get(35)),
                "NEW353 ordinal is retained and NEW341 appends for exact counts86/71/36/77");
        var node = HighPowerDeveloperLayout.page("teleporter", HighPowerDeveloperLayout.Page.HIGH_POWER).stream()
                .filter(entry -> entry.id().equals(ID)).findFirst().orElseThrow();
        check(node.index() == 353 && node.level() == 5 && node.parent().equals(ROOT)
                && node.conditions().size() == 2 && node.conditions().get(0).get("minimum").getAsString().equals("advanced")
                && node.conditions().get(1).get("id").getAsString().equals(ROOT)
                && node.conditions().get(1).get("minimum_exp").getAsDouble() == 0,
                "developer node advertises exact LV5 Advanced and zero-mastery343 requirements");
        for (int level = 1; level <= 5; level++) {
            var state = ability("teleporter", level);
            final int currentLevel = level;
            check(SkillCatalog.levelSkillCount(state) == SkillCatalog.ALL.stream().filter(s -> s.category().equals("teleporter")
                    && s.level() == currentLevel && s.controllable()).count(), "353 never changes classic advancement atLV" + level);
        }
    }

    private static void finiteLearningAndRevalidation() {
        var skill = SkillCatalog.find("teleporter", ID).orElseThrow();
        var state = ability("teleporter", 4); state.learn(ROOT);
        check(!SkillCatalog.canLearn(state, skill) && !DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill),
                "learned343 and Advanced developer cannot bypass LV5");
        state.setLevel(5); state.unlearn(ROOT);
        for (String sibling : PREVIOUS_TP) if (!sibling.equals(ROOT)) state.learn(sibling);
        check(!SkillCatalog.canLearn(state, skill) && !DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill),
                "all other TP additions together cannot substitute for learned343");
        check(!HighPowerSkills.eligible(state, ID) && !PresetSkills.selectable(state, ID)
                && !PresetSkills.edit(state, 0, 0, ID), "unlearned353 cannot cast or enter a preset");
        for (String sibling : PREVIOUS_TP) state.unlearn(sibling);
        state.learn(ROOT);
        check(state.exp(ROOT) == 0 && SkillCatalog.canLearn(state, skill)
                && !DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill)
                && !DevelopmentActions.canLearn(state, DeveloperType.NORMAL, skill)
                && DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill)
                && DeveloperType.minimumForSkill(5) == DeveloperType.ADVANCED,
                "zero-mastery343 requires canonical LV5 Advanced hardware");
        var action = DevelopmentActions.skill(state, skill);
        check(action.id().equals("skill:tactical_coordinate_formation") && action.stimulations() == 15
                && ClassicRules.learningStimulations(5) == 15
                && DeveloperType.ADVANCED.ticksPerStimulation() * action.stimulations() == 240
                && DeveloperType.ADVANCED.estimatedConsumption(action.stimulations()) == 9000
                && DeveloperType.ADVANCED.actualConsumption(action.stimulations()) == 9600,
                "fifteen native Advanced stimulations charge240 ticks/9600 IF, including the native extra tick");
        var process = new DevelopmentProcess(); var shortBattery = new Battery(DeveloperType.ADVANCED, 9599);
        process.start(shortBattery, action);
        for (int tick = 0; tick < 239; tick++) process.tick();
        check(process.state() == DevelopmentProcess.State.DEVELOPING && shortBattery.energy == 39
                && !state.learned(ID), "one IF short cannot grant early learning");
        process.tick();
        check(process.state() == DevelopmentProcess.State.FAILED && shortBattery.energy == 0 && !state.learned(ID),
                "unpaid final Advanced tick grants no learned bit");
        state.levelExperience = .375; state.cp = 17.25; state.overload = 4.5;
        var exactBattery = new Battery(DeveloperType.ADVANCED, 9600); process.start(exactBattery, action);
        for (int tick = 0; tick < 239; tick++) process.tick();
        check(!state.learned(ID) && exactBattery.energy == 40, "exact battery also waits for the final paid tick");
        process.tick();
        check(process.state() == DevelopmentProcess.State.DONE && exactBattery.energy == 0 && state.learned(ID),
                "finite exact payment completes ordinary LV5 learning");
        check(state.exp(ID) == 0 && state.exp(ROOT) == 0 && state.levelExperience == .375
                && PREVIOUS_TP.stream().filter(id -> !id.equals(ROOT)).noneMatch(state::learned),
                "learning grants no mastery, XP or sibling skills");
        check(state.cp == state.maxCp() && state.overload == 0,
                "ordinary SkillLearnEvent resource recalculation is preserved");
        check(!SkillCatalog.canLearn(state, skill) && DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill),
                "catalog hides duplicate learning while native completion remains idempotent");
        state.unlearn(ID);
        var interruptedBattery = new Battery(DeveloperType.ADVANCED, 9600);
        process.start(interruptedBattery, action);
        for (int tick = 0; tick < 120; tick++) process.tick();
        process.abort();
        check(process.state() == DevelopmentProcess.State.FAILED && !state.learned(ID)
                && interruptedBattery.energy == 4800 && state.levelExperience == .375,
                "cancelled ordinary development preserves paid energy loss without granting learning or XP");
        for (String changed : List.of("prerequisite", "level", "category", "configuration")) {
            state.category = "teleporter"; state.setLevel(5); state.unlearn(ID); state.learn(ROOT);
            process.start(new Battery(DeveloperType.ADVANCED, 9600), action);
            for (int tick = 0; tick < 239; tick++) process.tick();
            switch (changed) {
                case "prerequisite" -> state.unlearn(ROOT);
                case "level" -> state.setLevel(4);
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
        var old = ability("teleporter", 5);
        for (String id : PREVIOUS_TP) old.learn(id);
        old.learn("threatening_teleport");
        for (int i = 0; i < PREVIOUS_TP.size(); i++)
            check(PresetSkills.edit(old, i / 4, i % 4, PREVIOUS_TP.get(i)), "prepare previous saved binding " + i);
        check(PresetSkills.edit(old, 2, 2, "threatening_teleport"), "classic skill retains its binding");
        old.levelExperience = .375; old.cp = 17.25; old.overload = 4.5;
        var tag = AbilityStorage.encodeSaved(old); var state = AbilityStorage.decodeSaved(tag, old.cpDataConfig());
        check(!state.learned(ID) && AbilityStorage.encodeSaved(state).equals(tag),
                "pre353 save gets no learned bit, refill, binding remapping or schema migration");
        state.setActivateState(true); state.learn(ID);
        check(PresetSkills.edit(state, 3, 3, ID) && !PresetSkills.edit(state, 3, 2, ID)
                && SkillPresets.MAX_PRESETS == 4 && SkillPresets.MAX_KEYS == 4,
                "353 retains existing four by four preset slots and duplicate protection");
        tag = AbilityStorage.encodeSaved(state); var saved = AbilityStorage.decodeSaved(tag, state.cpDataConfig());
        check(saved.learned(ID) && saved.exp(ID) == 0 && saved.presets.skill(3, 3).equals(ID)
                && saved.presets.skill(2, 2).equals("threatening_teleport")
                && AbilityStorage.encodeSaved(saved).equals(tag), "new and classic saved IDs roundtrip exactly");
        for (int i = 0; i < PREVIOUS_TP.size(); i++)
            check(saved.learned(PREVIOUS_TP.get(i)) && saved.presets.skill(i / 4, i % 4).equals(PREVIOUS_TP.get(i)),
                    "old learned bit and binding retained " + PREVIOUS_TP.get(i));
        for (int level = 1; level <= 5; level++) {
            state.setLevel(level); state.setActivateState(true);
            check(HighPowerSkills.eligible(state, ID) == (level >= 5) && PresetSkills.selectable(state, ID) == (level >= 5)
                    && state.learned(ID) && state.presets.skill(3, 3).equals(ID), "LV" + level + " gate preserves raw saved353");
        }
        state.setActivateState(false);
        check(!HighPowerSkills.eligible(state, ID), "inactive ability cannot cast353");
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
            var fresh = ability("teleporter", 5); fresh.learn(ROOT);
            check(!DevelopmentActions.canLearn(fresh, DeveloperType.ADVANCED, skill) && !SkillCatalog.canLearn(fresh, skill)
                    && !HighPowerSkills.eligible(state, ID) && !PresetSkills.selectable(state, ID)
                    && state.learned(ID) && state.presets.skill(3, 3).equals(ID), "disabled353 blocks learning and use while retaining save");
            var snapshot = SkillCatalog.configurationSnapshot(); SkillCatalog.resetConfiguration();
            check(SkillCatalog.applyConfigurationSnapshot(snapshot) && !SkillCatalog.enabled("teleporter", ID),
                    "normal registered configuration snapshot carries353");
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("teleporter", ROOT), disabled));
            fresh.unlearn(ROOT);
            check(!SkillCatalog.canLearn(fresh, skill) && !DevelopmentActions.canLearn(fresh, DeveloperType.ADVANCED, skill),
                    "disabling343 cannot erase its prerequisite");
        } finally { SkillCatalog.resetConfiguration(); }
    }

    private static void boundedLearningFeedback() {
        var request = new HighPowerLearningProtocol.Request(ID, Integer.MAX_VALUE);
        check(!request.wire().startsWith(ID + ":") && ("00000000-0000-0000-0000-000000000000:" + request.wire()).length() <= 64
                && request.equals(HighPowerLearningProtocol.parse(request.wire())), "353 fits unchanged bounded machine request");
        var pending = new HighPowerLearningProtocol.Pending(); pending.begin(request);
        check(!pending.receive(HighPowerLearningProtocol.result(new HighPowerLearningProtocol.Request(ID, 1), true, ""))
                && pending.waiting(), "old sequence cannot consume353 learning feedback");
        check(pending.receive(HighPowerLearningProtocol.result(request, false, "not_ready"))
                && !pending.waiting() && !pending.accepted() && pending.reason().equals("not_ready"),
                "353 uses correlated refusal through existing protocol");
        pending.begin(request);
        check(!pending.receive(HighPowerLearningProtocol.result(new HighPowerLearningProtocol.Request(HighPowerSkills.CORNER_EXCHANGE,
                request.sequence()), true, "")) && pending.waiting(), "NEW333 feedback cannot consume NEW353 request");
        check(pending.receive(HighPowerLearningProtocol.result(request, true, "")) && pending.accepted()
                && !pending.receive(HighPowerLearningProtocol.result(request, true, "")), "matching353 acknowledgement is one-shot");
        var feedback = new HighPowerDeveloperLayout.DevelopmentFeedback();
        feedback.begin("skill:" + ID);
        check(feedback.receive("DONE", "", true) == HighPowerDeveloperLayout.DevelopmentUpdate.IGNORED,
                "old blank terminal snapshot cannot finish new353 attempt before matching start");
        check(feedback.receive("DEVELOPING", "skill:" + ID, false) == HighPowerDeveloperLayout.DevelopmentUpdate.IGNORED
                && feedback.receive("DEVELOPING", "skill:" + ID, true) == HighPowerDeveloperLayout.DevelopmentUpdate.PROGRESS
                && feedback.receive("DONE", "", true) == HighPowerDeveloperLayout.DevelopmentUpdate.DONE,
                "matching accepted353 start owns native blank completion snapshot");
        feedback.begin("skill:" + ID);
        feedback.receive("DEVELOPING", "skill:" + ID, true);
        check(feedback.receive("DEVELOPING", "skill:corner_exchange", true) == HighPowerDeveloperLayout.DevelopmentUpdate.DISPLACED
                && feedback.receive("DONE", "", true) == HighPowerDeveloperLayout.DevelopmentUpdate.IGNORED,
                "replacement with333 relinquishes353 completion ownership");
    }

}
