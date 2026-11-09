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

/** NEW323 finite LV2 learning and resources; this is not live movement or rendered-client acceptance. */
public final class RecoveryMarkLearningRegressionTest {
    private static final String ID = HighPowerSkills.RECOVERY_MARK;
    private static final String ROOT = HighPowerSkills.DISPLACED_PROJECTION;
    private static final String PREFIX = "academy.highpower.recovery_mark.";
    private static final List<String> PREVIOUS_IDS = List.of(
            "forked_arc", "magnetic_bracer", "charge_decoy", "polarity_tether", "conductive_circuit",
            "grounding_return", "magnetic_ground_step", "magnetic_item_pull", "iron_sand_lance",
            "thunder_net", "potential_buffer", "rail_thunder_array", "magnetic_domain", "dual_point",
            "scatter_plates", "beam_etching", "thermal_shell", "folded_mirror_array", "disintegration_sweep",
            "beam_step", "particle_laminate", "directional_dissipation_curtain", "multicore_canopy",
            "zero_gap_armor", "safe_landing_probe", "phase_sidestep", "displaced_projection", "return_mark");
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
        try (var in = Objects.requireNonNull(RecoveryMarkLearningRegressionTest.class.getResourceAsStream(path), path)) {
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
        System.out.println("RecoveryMarkLearningRegressionTest: " + checks + " checks passed");
    }

    private static void appendOnlyRegistration() {
        var skill = SkillCatalog.find("teleporter", ID).orElseThrow();
        check(ID.equals("recovery_mark") && HighPowerSkills.RECOVERY_MARK_PROPOSAL == 323,
                "exact NEW323 proposal and persistent identifier");
        check(skill.level() == 2 && skill.controllable() && skill.anyLearnedSkillLevel() == 0,
                "LV2 controllable TP skill has no alternate-level shortcut");
        check(skill.requirements().equals(List.of(new SkillCatalog.Requirement(ROOT, 0))),
                "learned311 is the only prerequisite, with zero mastery");
        check(HighPowerSkills.ALL.subList(0, 28).stream().map(SkillCatalog.Skill::id).toList().equals(PREVIOUS_IDS),
                "all28 previous high-power identities and ordinals remain unchanged");
        check(HighPowerSkills.ALL.get(28).equals(skill) && SkillCatalog.REGISTERED.get(78).equals(skill),
                "NEW323 appends after all50 classic and28 existing high-power registrations");
        check(HighPowerSkills.IDS.contains(ID) && SkillAvailability.learnable(ID)
                && PresetSkills.REGISTERED.contains(ID)
                && SkillCatalog.REGISTERED_CONFIG_KEYS.contains(ClassicSkillConfiguration.key("teleporter", ID)),
                "derived learning, preset and configuration mappings admit323");
        check(SkillCatalog.ALL.size() == 50 && ClassicDeveloperLayout.NODES.size() == 50
                && PresetSkills.IMPLEMENTED.size() == 35 && SkillCatalog.CONFIG_KEYS.size() == 41,
                "classic50 catalog,41 shared configuration keys and35 preset denominator remain unchanged");
        check(SkillCatalog.REGISTERED.size() == 100 && cn.academy.port.SkillCatalog.REGISTERED.get(99).id().equals("point_meltdown") && PresetSkills.REGISTERED.size() == 85 && cn.academy.port.preset.PresetSkills.REGISTERED.contains("point_meltdown")
                && HighPowerSkills.ALL.size() == 50 && cn.academy.port.highpower.HighPowerSkills.ALL.get(49).id().equals("point_meltdown") && HighPowerSkills.ALL.get(48).id().equals("wind_pressure_lift") && HighPowerSkills.ALL.get(47).id().equals("multi_axis_reflection") && HighPowerSkills.ALL.get(45).id().equals("circulation_listening") && HighPowerSkills.ALL.get(44).id().equals("circulation_relief") && HighPowerSkills.ALL.get(43).id().equals("trajectory_return") && HighPowerSkills.ALL.get(42).id().equals("yaw_palm") && HighPowerSkills.ALL.get(41).id().equals("inertial_anchor") && HighPowerSkills.ALL.get(40).id().equals("shear_windblade") && HighPowerSkills.ALL.get(39).id().equals("braking_touch") && HighPowerSkills.ALL.get(30).id().equals(HighPowerSkills.CORNER_EXCHANGE) && HighPowerSkills.ALL.get(31).id().equals(HighPowerSkills.SHORT_INTERCEPTION) && HighPowerSkills.IDS.size() == 50 && cn.academy.port.highpower.HighPowerSkills.IDS.contains("point_meltdown") && HighPowerSkills.IDS.contains("wind_pressure_lift") && HighPowerSkills.IDS.contains("multi_axis_reflection") && HighPowerSkills.IDS.contains("circulation_listening") && HighPowerSkills.IDS.contains("circulation_relief") && HighPowerSkills.IDS.contains("trajectory_return") && HighPowerSkills.IDS.contains("yaw_palm") && HighPowerSkills.IDS.contains("inertial_anchor") && HighPowerSkills.IDS.contains("shear_windblade")
                && HighPowerSkills.ALL.get(32).id().equals(HighPowerSkills.SPATIAL_RELAY)
                && HighPowerSkills.ALL.get(33).id().equals(HighPowerSkills.EMERGENCY_RETURN)
                && HighPowerSkills.ALL.get(34).id().equals(HighPowerSkills.TACTICAL_COORDINATE_FORMATION)
                && SkillCatalog.REGISTERED.get(84).equals(HighPowerSkills.ALL.get(34))
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
        state.learn(HighPowerSkills.SAFE_LANDING_PROBE);
        state.learn(HighPowerSkills.RETURN_MARK);
        check(!SkillCatalog.canLearn(state, skill) && !DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill),
                "learned313 and322 cannot substitute for learned311");
        state.unlearn(HighPowerSkills.RETURN_MARK);
        state.learn(ROOT);
        check(state.exp(ROOT) == 0 && SkillCatalog.canLearn(state, skill)
                && DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill)
                && DeveloperType.minimumForSkill(2) == DeveloperType.PORTABLE,
                "LV2 zero-mastery311 preserves canonical Portable minimum");
        var action = DevelopmentActions.skill(state, skill);
        check(action.id().equals("skill:recovery_mark") && action.stimulations() == 5
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
                && !state.learned(HighPowerSkills.PHASE_SIDESTEP) && !state.learned(HighPowerSkills.RETURN_MARK),
                "learning grants no mastery, XP or sibling learning");
        check(state.cp == state.maxCp() && state.overload == 0,
                "ordinary classic SkillLearnEvent resource recalculation remains unchanged");
        check(SkillCatalog.levelSkillCount(state) == denominator
                && ClassicRules.levelThreshold(2, SkillCatalog.levelSkillCount(state)) == ClassicRules.levelThreshold(2, denominator),
                "323 cannot inflate the classic advancement threshold");
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
    }

    private static void savedIdentitiesAndGates() {
        var skill = SkillCatalog.find("teleporter", ID).orElseThrow();
        var old = ability("teleporter", 2);
        for (String id : List.of(HighPowerSkills.SAFE_LANDING_PROBE, HighPowerSkills.PHASE_SIDESTEP, ROOT, HighPowerSkills.RETURN_MARK, "threatening_teleport")) old.learn(id);
        check(PresetSkills.edit(old, 0, 0, HighPowerSkills.SAFE_LANDING_PROBE) && PresetSkills.edit(old, 0, 1, HighPowerSkills.PHASE_SIDESTEP)
                && PresetSkills.edit(old, 0, 2, HighPowerSkills.DISPLACED_PROJECTION)
                && PresetSkills.edit(old, 0, 3, "threatening_teleport"), "prepare saved pre323 bindings");
        old.levelExperience = .375; old.cp = 17.25; old.overload = 4.5;
        var tag = AbilityStorage.encodeSaved(old); var state = AbilityStorage.decodeSaved(tag, old.cpDataConfig());
        check(!state.learned(ID) && AbilityStorage.encodeSaved(state).equals(tag),
                "pre323 save receives no learning, refill, preset remapping or schema migration");
        state.setActivateState(true); state.learn(ID);
        check(PresetSkills.edit(state, 3, 3, ID) && !PresetSkills.edit(state, 3, 2, ID)
                && SkillPresets.MAX_PRESETS == 4 && SkillPresets.MAX_KEYS == 4,
                "323 uses existing preset slots and duplicate protection");
        tag = AbilityStorage.encodeSaved(state); var saved = AbilityStorage.decodeSaved(tag, state.cpDataConfig());
        check(saved.learned(ID) && saved.exp(ID) == 0 && saved.presets.skill(3, 3).equals(ID)
                && AbilityStorage.encodeSaved(saved).equals(tag), "learned323 and all existing bindings roundtrip exactly");
        check(HighPowerSkills.eligible(state, ID) && PresetSkills.selectable(state, ID), "learned activeLV2 admits323");
        state.setActivateState(false);
        check(!HighPowerSkills.eligible(state, ID), "inactive ability cannot cast323");
        state.setLevel(1); state.setActivateState(true);
        check(state.learned(ID) && !HighPowerSkills.eligible(state, ID) && !PresetSkills.selectable(state, ID)
                && state.presets.skill(3, 3).equals(ID), "downgrade retains raw save data while preventing use");
        state.setLevel(2); state.setActivateState(true);
        for (String category : List.of("electromaster", "meltdowner", "vecmanip")) {
            var wrong = ability(category, 5); wrong.experience.put(ROOT, 0.0); wrong.experience.put(ID, 0.0);
            check(!HighPowerSkills.eligible(wrong, ID) && !PresetSkills.selectable(wrong, ID)
                    && !DevelopmentActions.canLearn(wrong, DeveloperType.ADVANCED, skill), "stale IDs cannot cross category " + category);
        }
        var disabled = new ClassicSkillConfiguration.Flags(false, false);
        try {
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("teleporter", ID), disabled));
            var fresh = ability("teleporter", 2); fresh.learn(ROOT);
            check(!DevelopmentActions.canLearn(fresh, DeveloperType.PORTABLE, skill) && !SkillCatalog.canLearn(fresh, skill),
                    "disabled323 refuses both learning validators");
            check(!HighPowerSkills.eligible(state, ID) && !PresetSkills.selectable(state, ID)
                    && state.learned(ID) && state.presets.skill(3, 3).equals(ID), "disable blocks use without deleting saved data");
            var snapshot = SkillCatalog.configurationSnapshot(); SkillCatalog.resetConfiguration();
            check(SkillCatalog.applyConfigurationSnapshot(snapshot) && !SkillCatalog.enabled("teleporter", ID),
                    "registered configuration snapshot carries323");
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("teleporter", ROOT), disabled));
            fresh.unlearn(ROOT);
            check(!SkillCatalog.canLearn(fresh, skill) && !DevelopmentActions.canLearn(fresh, DeveloperType.PORTABLE, skill),
                    "disabled311 cannot erase the root prerequisite");
        } finally { SkillCatalog.resetConfiguration(); }
    }

    private static void boundedLearningFeedback() {
        var request = new HighPowerLearningProtocol.Request(ID, Integer.MAX_VALUE);
        check(request.wire().startsWith("recovery_mark:") && ("00000000-0000-0000-0000-000000000000:" + request.wire()).length() <= 64
                && request.equals(HighPowerLearningProtocol.parse(request.wire())), "canonical323 wire fits machine request bound");
        var pending = new HighPowerLearningProtocol.Pending(); pending.begin(request);
        check(!pending.receive(HighPowerLearningProtocol.result(new HighPowerLearningProtocol.Request(ID, 1), true, ""))
                && pending.waiting(), "stale learning reply cannot consume current request");
        check(pending.receive(HighPowerLearningProtocol.result(request, false, "not_ready"))
                && !pending.waiting() && !pending.accepted() && pending.reason().equals("not_ready"),
                "323 receives correlated refusal through unchanged learning protocol");
    }

    private static void retainedLayoutAndOriginalArtwork() throws Exception {
        var original = ClassicDeveloperLayout.NODES.stream().filter(n -> n.category().equals("teleporter")).toList();
        check(original.size() == 12 && HighPowerDeveloperLayout.page("teleporter", HighPowerDeveloperLayout.Page.CLASSIC).equals(original),
                "all twelve classicTP nodes retain metadata and placement");
        var page = HighPowerDeveloperLayout.page("teleporter", HighPowerDeveloperLayout.Page.HIGH_POWER);
        check(page.size() == 13 && page.get(12).id().equals(HighPowerSkills.MULTIDIMENSIONAL_LANDING_ARRAY) && page.get(11).id().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR) && page.get(10).id().equals(HighPowerSkills.TACTICAL_COORDINATE_FORMATION) && page.get(7).id().equals(HighPowerSkills.SHORT_INTERCEPTION) && page.get(6).id().equals(HighPowerSkills.CORNER_EXCHANGE), "retained NEW333/331/343/342/353 page nodes precede appended NEW341");
        check(page.subList(0, 6).stream().map(ClassicDeveloperLayout.Node::id).toList().equals(List.of(
                HighPowerSkills.SAFE_LANDING_PROBE, HighPowerSkills.PHASE_SIDESTEP, ROOT, HighPowerSkills.RETURN_MARK, ID, HighPowerSkills.CROSS_SUBSTITUTION)),
                "323 retains its place after all four previous TP nodes and before appended321");
        String[] oldIds = {HighPowerSkills.SAFE_LANDING_PROBE, HighPowerSkills.PHASE_SIDESTEP, ROOT, HighPowerSkills.RETURN_MARK};
        int[] oldProposals = {313, 312, 311, 322};
        double[] oldX = {118, 198, 38, 118}; double[] oldY = {61, 61, 61, 105};
        for (int i = 0; i < 4; i++) {
            var previous = page.get(i);
            check(previous.id().equals(oldIds[i]) && previous.index() == oldProposals[i]
                    && previous.level() == (i == 3 ? 2 : 1) && previous.x() == oldX[i] && previous.y() == oldY[i]
                    && previous.parent().equals(i == 0 ? "" : HighPowerSkills.SAFE_LANDING_PROBE),
                    "prior TP identity, level, position and prerequisite retained " + oldIds[i]);
        }
        var root = page.get(2); var node = page.get(4);
        check(node.index() == 323 && node.level() == 2 && node.x() == 38 && node.y() == 105 && node.parent().equals(ROOT),
                "323 has a clear LV2 branch below its311 prerequisite");
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
        var edge = ClassicDeveloperTimeline.edge(node.x(), node.y(), root.x(), root.y(), 1);
        check(edge.x0() == edge.x1() && edge.x0() == root.x() + 8 && edge.y1() > edge.y0()
                && edge.y0() > root.y() + 8 && edge.y1() < node.y() + 8,
                "ordinary vertical dependency fits between311 and323 without crossing siblings");
        var nav = new HighPowerDeveloperLayout.Navigation(); nav.synchronize("teleporter");
        check(!nav.select(ID) && nav.switchTo(HighPowerDeveloperLayout.Page.HIGH_POWER) && nav.select(ID),
                "323 is selectable only from its actual page");
        check(nav.beginLearning() && nav.back() == HighPowerDeveloperLayout.Back.BUSY, "pending323 retains navigation fence");
        nav.finishLearning();
        check(nav.back() == HighPowerDeveloperLayout.Back.SELECTION && nav.back() == HighPowerDeveloperLayout.Back.CLASSIC_PAGE,
                "Back dismisses323 then restores canonical page");
        check(Arrays.equals(resource("/assets/academy/textures/abilities/teleporter/skills/location_teleport.png"),
                resource("/assets/academy/textures/abilities/teleporter/skills/recovery_mark.png")), "323 reuses original icon bytes unchanged");
        String notice = new String(resource("/META-INF/ACADEMY-NOTICE"), StandardCharsets.UTF_8);
        check(notice.contains("NEW323 recovery_mark") && notice.contains("teleporter/location_teleport.png")
                && notice.contains("Lambda Innovation"), "packaged attribution names original artwork and author");
    }

    private static void localizedPlayerContract() throws Exception {
        var english = language("en_us");
        var keys = english.keySet().stream().filter(k -> k.startsWith(PREFIX)).collect(Collectors.toSet());
        check(keys.containsAll(Set.of(PREFIX + "marked", PREFIX + "recalled", PREFIX + "expired", PREFIX + "cleared",
                PREFIX + "blocked", PREFIX + "refused")), "recovery lifecycle and refusal have player labels");
        check(keys.containsAll(Set.of(PREFIX + "reason.needle_missing", PREFIX + "reason.needle_changed",
                PREFIX + "reason.needle_busy", PREFIX + "reason.not_aimed", PREFIX + "reason.combat_active",
                PREFIX + "reason.inventory_full", PREFIX + "reason.transport_unconfirmed",
                PREFIX + "reason.visual_unavailable", PREFIX + "reason.insufficient_cp", PREFIX + "reason.registration_limit",
                PREFIX + "reason.explicit_aim_required", PREFIX + "reason.request_fence")),
                "custody, aim, combat, inventory, registration budget, request interval and confirmation failures have specific labels");
        for (String reason : List.of("server_thread_required", "recovery_geometry_invalid", "anchor_world_or_owner_changed",
                "recovery_out_of_range", "destination_has_no_displacement", "destination_body_invalid", "destination_hazard",
                "destination_fluid", "destination_block_collision", "destination_floor_unsupported", "destination_entity_collision",
                "corridor_fluid_or_hazard", "corridor_block_collision", "corridor_entity_collision", "source_embedding_changed",
                "entity_scan_budget", "invalid_entity_body", "needle_unavailable", "recovery_actor_changed", "recovery_source_changed",
                "collision_scan_budget", "outside_build_height", "outside_world_border", "unloaded_chunk", "block_read_budget",
                "shape_neighbor_out_of_scope", "interaction_denied", "shape_grid_budget", "invalid_native_shape", "shape_box_budget",
                "support_grid_budget", "invalid_server_aim", "needle_not_aimed", "needle_not_visible", "source_unavailable",
                "source_airborne", "source_motion_or_pose_unsupported", "source_climbing", "source_body_invalid",
                "source_block_collision", "source_floor_unsupported", "source_entity_collision", "source_fluid", "world_changed", "source_changed"))
            check(keys.contains(PREFIX + "reason." + reason), "native world refusal has a recovery-specific label " + reason);
        for (String locale : List.of("en_us", "zh_cn", "zh_tw", "ja_jp")) {
            var words = language(locale);
            check(words.keySet().stream().filter(k -> k.startsWith(PREFIX)).collect(Collectors.toSet()).equals(keys),
                    "all323 status and reason tokens match across locales " + locale);
            for (String key : java.util.stream.Stream.concat(keys.stream(), List.of(
                    "ac.ability.teleporter.recovery_mark.name", "ac.ability.teleporter.recovery_mark.desc").stream()).toList())
                check(words.has(key) && !words.get(key).getAsString().isBlank(), "nonblank323 translation " + locale + "/" + key);
            check(words.get(PREFIX + "blocked").getAsString().contains("%s"), "refusal keeps reason placeholder " + locale);
            check(words.get(PREFIX + "marked").getAsString().split("%s", -1).length == 4
                    && words.get(PREFIX + "marked").getAsString().contains("%s/8"),
                    "one registered mark label consumes count out of eight, distance and remaining seconds " + locale);
            String description = words.get("ac.ability.teleporter.recovery_mark.desc").getAsString();
            for (String value : List.of("1200", "24", "8", "LV2", "6 / 3 / 1.5 / 0.375 / 0.025",
                    "40 / 20 / 10 / 4 / 1", "130", "3900"))
                check(description.contains(value), "explicit323 bounded contract " + locale + "/" + value);
            var scopedLimits = Map.of(
                    "en_us", List.of("up to 8", "Q-dropped", "count 1", "merged stack", "only that same entity", "expires independently", "clears all registrations"),
                    "zh_cn", List.of("最多登记8", "Q", "只有1枚", "合并成堆叠", "只回收该实体", "每项独立", "清除全部登记"),
                    "zh_tw", List.of("最多登記8", "Q", "只有1枚", "合併成堆疊", "只回收該實體", "每項獨立", "清除全部登記"),
                    "ja_jp", List.of("最大8本", "Qキー", "個数は1本", "スタックへの合流", "同じ実体1本だけ", "個別に期限切れ", "全登録を無料で解除"));
            for (String limit : scopedLimits.get(locale))
                check(description.contains(limit), "localized eight-registration contract states " + locale + "/" + limit);
        }
        check(language("zh_cn").get("ac.ability.teleporter.recovery_mark.name").getAsString().contains("携物回收标"),
                "323 retains the approved Chinese skill name");
        String description = english.get("ac.ability.teleporter.recovery_mark.desc").getAsString();
        for (String limit : List.of("up to 8", "Displaced Projection needle", "frozen", "same entity", "same world", "Sneak-tap",
                "full inventory", "loaded", "ground", "walls", "normal pickup", "No damage or XP", "provisional",
                "8-tick", "no mastery requirement", "fresh second tap may confirm immediately", "reset by a committed recall",
                "Q-dropped plain needles only", "count 1", "no custom components", "merged stack", "lost ownership",
                "recover only that same entity", "With exactly one registration", "with multiple registrations, aim",
                "clears all registrations", "expires independently", "new registrations never refresh older marks",
                "Only a committed recall spends CP", "An unconfirmed committed recall may already spend CP and cooldown"))
            check(description.contains(limit), "honest scoped player contract states " + limit);
    }
}
