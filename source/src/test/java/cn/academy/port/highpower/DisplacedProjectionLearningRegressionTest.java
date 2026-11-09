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

/** NEW311 finite learning, stable identities and localized player contract; no world or client acceptance. */
public final class DisplacedProjectionLearningRegressionTest {
    private static final String ID = HighPowerSkills.DISPLACED_PROJECTION;
    private static final String ROOT = HighPowerSkills.SAFE_LANDING_PROBE;
    private static final String SIBLING = HighPowerSkills.PHASE_SIDESTEP;
    private static final String REASONS = "academy.highpower.displaced_projection.reason.";
    private static final List<String> PREVIOUS_IDS = List.of(
            "forked_arc", "magnetic_bracer", "charge_decoy", "polarity_tether", "conductive_circuit",
            "grounding_return", "magnetic_ground_step", "magnetic_item_pull", "iron_sand_lance",
            "thunder_net", "potential_buffer", "rail_thunder_array", "magnetic_domain", "dual_point",
            "scatter_plates", "beam_etching", "thermal_shell", "folded_mirror_array", "disintegration_sweep",
            "beam_step", "particle_laminate", "directional_dissipation_curtain", "multicore_canopy",
            "zero_gap_armor", "safe_landing_probe", "phase_sidestep");
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
        private double energy;
        private Battery(double energy) { this.energy = energy; }
        public DeveloperType type() { return DeveloperType.PORTABLE; }
        public boolean tryPullEnergy(double amount) {
            double paid = Math.min(energy, amount); energy -= paid; return paid == amount;
        }
        public double energy() { return energy; }
        public double maxEnergy() { return type().energy; }
    }
    private static byte[] resource(String path) throws Exception {
        try (var in = Objects.requireNonNull(DisplacedProjectionLearningRegressionTest.class.getResourceAsStream(path), path)) {
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
            retainedLayoutAndOriginalArtwork();
            localizedPlayerContract();
        } finally { SkillCatalog.resetConfiguration(); }
        System.out.println("DisplacedProjectionLearningRegressionTest: " + checks + " checks passed");
    }

    private static void appendOnlyRegistration() {
        var skill = SkillCatalog.find("teleporter", ID).orElseThrow();
        check(ID.equals("displaced_projection") && HighPowerSkills.DISPLACED_PROJECTION_PROPOSAL == 311,
                "exact NEW311 proposal and persistent identifier");
        check(skill.category().equals("teleporter") && skill.level() == 1 && skill.controllable()
                && skill.anyLearnedSkillLevel() == 0, "LV1 controllable TP skill has no alternate-level shortcut");
        check(skill.requirements().equals(List.of(new SkillCatalog.Requirement(ROOT, 0))),
                "learned313 is the only prerequisite, with no mastery or312 requirement");
        check(HighPowerSkills.ALL.subList(0, 26).stream().map(SkillCatalog.Skill::id).toList().equals(PREVIOUS_IDS),
                "every previous high-power registration retains its identity and ordinal");
        check(HighPowerSkills.ALL.get(26).equals(skill) && SkillCatalog.REGISTERED.get(76).equals(skill)
                && HighPowerSkills.find(ID).orElseThrow().equals(skill), "NEW311 retains its appended ordinal before NEW322");
        check(HighPowerSkills.IDS.contains(ID) && SkillAvailability.learnable(ID)
                && PresetSkills.REGISTERED.contains(ID), "ordinary learning and preset registries admit311");
        check(SkillCatalog.ALL.size() == 50 && ClassicDeveloperLayout.NODES.size() == 50
                && PresetSkills.IMPLEMENTED.size() == 35, "classic50 catalog and classic35 preset denominator are untouched");
        check(SkillCatalog.REGISTERED.size() == (111 + cn.academy.port.canon.CanonSkills.ALL.size()) && cn.academy.port.SkillCatalog.REGISTERED.get(100).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.SkillCatalog.REGISTERED.get(99).id().equals("point_meltdown") && PresetSkills.REGISTERED.size() == (96 + cn.academy.port.canon.CanonSkills.IDS.size()) && cn.academy.port.preset.PresetSkills.REGISTERED.contains("remote_scatter_relay") && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.preset.PresetSkills.REGISTERED.contains("point_meltdown")
                && HighPowerSkills.ALL.size() == 61 && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.highpower.HighPowerSkills.ALL.get(49).id().equals("point_meltdown") && HighPowerSkills.ALL.get(48).id().equals("wind_pressure_lift") && HighPowerSkills.ALL.get(47).id().equals("multi_axis_reflection") && HighPowerSkills.ALL.get(45).id().equals("circulation_listening") && HighPowerSkills.ALL.get(44).id().equals("circulation_relief") && HighPowerSkills.ALL.get(43).id().equals("trajectory_return") && HighPowerSkills.ALL.get(42).id().equals("yaw_palm") && HighPowerSkills.ALL.get(41).id().equals("inertial_anchor") && HighPowerSkills.ALL.get(40).id().equals("shear_windblade") && HighPowerSkills.ALL.get(39).id().equals("braking_touch") && HighPowerSkills.ALL.get(30).id().equals(HighPowerSkills.CORNER_EXCHANGE) && HighPowerSkills.ALL.get(31).id().equals(HighPowerSkills.SHORT_INTERCEPTION) && HighPowerSkills.IDS.size() == (61 + cn.academy.port.canon.CanonSkills.IDS.size()) && cn.academy.port.highpower.HighPowerSkills.IDS.contains("remote_scatter_relay") && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.highpower.HighPowerSkills.IDS.contains("point_meltdown") && HighPowerSkills.IDS.contains("wind_pressure_lift") && HighPowerSkills.IDS.contains("multi_axis_reflection") && HighPowerSkills.IDS.contains("circulation_listening") && HighPowerSkills.IDS.contains("circulation_relief") && HighPowerSkills.IDS.contains("trajectory_return") && HighPowerSkills.IDS.contains("yaw_palm") && HighPowerSkills.IDS.contains("inertial_anchor") && HighPowerSkills.IDS.contains("shear_windblade")
                && HighPowerSkills.ALL.get(32).id().equals(HighPowerSkills.SPATIAL_RELAY)
                && HighPowerSkills.ALL.get(33).id().equals(HighPowerSkills.EMERGENCY_RETURN)
                && HighPowerSkills.ALL.get(34).id().equals(HighPowerSkills.TACTICAL_COORDINATE_FORMATION)
                && SkillCatalog.REGISTERED.get(84).equals(HighPowerSkills.ALL.get(34))
                && HighPowerSkills.ALL.get(35).id().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR)
                && SkillCatalog.REGISTERED.get(85).equals(HighPowerSkills.ALL.get(35)),
                "exactly35 high-power additions with retained ordinals and appended NEW353,85 total catalog entries and70 preset entries");
        check(HighPowerSkills.ALL.subList(0, 30).stream().filter(s -> s.category().equals("teleporter")).map(SkillCatalog.Skill::id).toList()
                .equals(List.of(ROOT, SIBLING, ID, HighPowerSkills.RETURN_MARK, HighPowerSkills.RECOVERY_MARK, HighPowerSkills.CROSS_SUBSTITUTION)), "TP append order retains313 then312 then311 before322,323 and321");
    }

    private static void finiteLearningAndRevalidation() {
        var skill = SkillCatalog.find("teleporter", ID).orElseThrow();
        var state = ability("teleporter", 1);
        int denominator = SkillCatalog.levelSkillCount(state);
        check(denominator == SkillCatalog.ALL.stream().filter(s -> s.category().equals("teleporter")
                && s.level() == 1 && s.controllable()).count(), "only original LV1 skills contribute to advancement");
        check(!SkillCatalog.canLearn(state, skill) && !DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill),
                "both learning validators refuse unlearned313");
        check(!HighPowerSkills.eligible(state, ID) && !PresetSkills.selectable(state, ID)
                && !PresetSkills.edit(state, 0, 0, ID), "unlearned311 cannot cast or enter a preset");
        state.learn(ROOT);
        check(state.exp(ROOT) == 0 && !state.learned(SIBLING) && SkillCatalog.canLearn(state, skill)
                && DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill),
                "zero-mastery313 permits Portable learning without312");
        var action = DevelopmentActions.skill(state, skill);
        check(action.id().equals("skill:displaced_projection") && action.stimulations() == 3
                && ClassicRules.learningStimulations(1) == 3
                && DeveloperType.PORTABLE.ticksPerStimulation() * action.stimulations() == 78
                && DeveloperType.PORTABLE.actualConsumption(action.stimulations()) == 2340,
                "three ordinary Portable stimulations take78 ticks and2340 IF");
        var process = new DevelopmentProcess(); var shortBattery = new Battery(2339);
        process.start(shortBattery, action);
        for (int tick = 0; tick < 77; tick++) process.tick();
        check(process.state() == DevelopmentProcess.State.DEVELOPING && shortBattery.energy == 29
                && !state.learned(ID), "one-IF-short development does not grant early learning");
        process.tick();
        check(process.state() == DevelopmentProcess.State.FAILED && shortBattery.energy == 0
                && !state.learned(ID), "unpaid final tick grants no learned bit");
        state.levelExperience = .375; state.cp = 17.25; state.overload = 4.5;
        var battery = new Battery(2340); process.start(battery, action);
        for (int tick = 0; tick < 77; tick++) process.tick();
        check(!state.learned(ID) && battery.energy == 30, "exact payment still waits for final tick");
        process.tick();
        check(process.state() == DevelopmentProcess.State.DONE && battery.energy == 0 && state.learned(ID),
                "exact finite payment completes normal learning");
        check(state.exp(ID) == 0 && state.exp(ROOT) == 0 && state.levelExperience == .375 && !state.learned(SIBLING),
                "learning grants no mastery, levelXP or sibling learning");
        check(SkillCatalog.levelSkillCount(state) == denominator
                && ClassicRules.levelThreshold(1, SkillCatalog.levelSkillCount(state)) == ClassicRules.levelThreshold(1, denominator),
                "311 cannot inflate the classic advancement threshold");
        check(!SkillCatalog.canLearn(state, skill) && DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill),
                "catalog hides duplicate learning while native completion retains its classic idempotent validator");
        state.unlearn(ID); process.start(new Battery(2340), DevelopmentActions.skill(state, skill));
        for (int tick = 0; tick < 77; tick++) process.tick();
        state.unlearn(ROOT); process.tick();
        check(process.state() == DevelopmentProcess.State.FAILED && !state.learned(ID),
                "paid completion revalidates learned313 rather than trusting the request");
    }

    private static void savedIdentitiesAndGates() {
        var skill = SkillCatalog.find("teleporter", ID).orElseThrow();
        var old = ability("teleporter", 1);
        for (String id : List.of(ROOT, SIBLING, "threatening_teleport")) old.learn(id);
        check(PresetSkills.edit(old, 0, 0, ROOT) && PresetSkills.edit(old, 0, 1, SIBLING)
                && PresetSkills.edit(old, 0, 2, "threatening_teleport"), "prepare saved pre311 root, sibling and classic bindings");
        old.levelExperience = .375; old.cp = 17.25; old.overload = 4.5;
        var tag = AbilityStorage.encodeSaved(old); var state = AbilityStorage.decodeSaved(tag, old.cpDataConfig());
        check(!state.learned(ID) && state.presets.skill(0, 0).equals(ROOT)
                && state.presets.skill(0, 1).equals(SIBLING) && state.presets.skill(0, 2).equals("threatening_teleport")
                && AbilityStorage.encodeSaved(state).equals(tag), "pre311 save gets no learning, refill or remapping");
        state.setActivateState(true); state.learn(ID);
        check(PresetSkills.edit(state, 3, 3, ID) && !PresetSkills.edit(state, 3, 2, ID),
                "311 uses the existing fourth-page R slot and rejects duplicates");
        check(SkillPresets.MAX_PRESETS == 4 && SkillPresets.MAX_KEYS == 4, "no additional pages or key bindings");
        tag = AbilityStorage.encodeSaved(state); var saved = AbilityStorage.decodeSaved(tag, state.cpDataConfig());
        check(saved.learned(ID) && saved.exp(ID) == 0 && saved.presets.skill(3, 3).equals(ID)
                && saved.presets.skill(0, 0).equals(ROOT) && saved.presets.skill(0, 1).equals(SIBLING)
                && saved.presets.skill(0, 2).equals("threatening_teleport") && AbilityStorage.encodeSaved(saved).equals(tag),
                "311 learning and exact ID roundtrip alongside every existing binding without schema changes");
        check(HighPowerSkills.eligible(state, ID), "learned activeTP passes ordinary skill eligibility");
        state.setActivateState(false);
        check(!HighPowerSkills.eligible(state, ID), "inactive ability cannot cast311");
        state.setLevel(0); state.setActivateState(true);
        check(state.learned(ID) && !HighPowerSkills.eligible(state, ID) && !PresetSkills.selectable(state, ID),
                "stored learning cannot bypass an actual level downgrade");
        state.setLevel(1); state.setActivateState(true);
        for (String category : List.of("electromaster", "meltdowner", "vecmanip")) {
            var wrong = ability(category, 5); wrong.experience.put(ROOT, 0.0); wrong.experience.put(ID, 0.0);
            check(!HighPowerSkills.eligible(wrong, ID) && !PresetSkills.selectable(wrong, ID)
                    && !DevelopmentActions.canLearn(wrong, DeveloperType.ADVANCED, skill),
                    "stale learned IDs cannot cross category " + category);
        }
        var disabled = new ClassicSkillConfiguration.Flags(false, false);
        try {
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("teleporter", ID), disabled));
            var fresh = ability("teleporter", 1); fresh.learn(ROOT);
            check(!DevelopmentActions.canLearn(fresh, DeveloperType.PORTABLE, skill) && !SkillCatalog.canLearn(fresh, skill),
                    "disabled311 refuses both learning validators");
            check(!HighPowerSkills.eligible(state, ID) && !PresetSkills.selectable(state, ID)
                    && state.learned(ID) && state.presets.skill(3, 3).equals(ID),
                    "live disable blocks311 without deleting saved learning or binding");
            var snapshot = SkillCatalog.configurationSnapshot(); SkillCatalog.resetConfiguration();
            check(SkillCatalog.applyConfigurationSnapshot(snapshot) && !SkillCatalog.enabled("teleporter", ID),
                    "bounded configuration snapshot carries311");
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("teleporter", ROOT), disabled));
            fresh.unlearn(ROOT);
            check(!SkillCatalog.canLearn(fresh, skill) && !DevelopmentActions.canLearn(fresh, DeveloperType.PORTABLE, skill),
                    "disabled313 cannot remove the learned prerequisite");
        } finally { SkillCatalog.resetConfiguration(); }
    }

    private static void retainedLayoutAndOriginalArtwork() throws Exception {
        var original = ClassicDeveloperLayout.NODES.stream().filter(n -> n.category().equals("teleporter")).toList();
        check(original.size() == 12 && HighPowerDeveloperLayout.page("teleporter", HighPowerDeveloperLayout.Page.CLASSIC).equals(original),
                "all twelve classicTP nodes retain full metadata and placement");
        var page = HighPowerDeveloperLayout.page("teleporter", HighPowerDeveloperLayout.Page.HIGH_POWER);
        check(page.size() == CurrentHighPowerInventory.pageSize(page) && page.get(12).id().equals(HighPowerSkills.MULTIDIMENSIONAL_LANDING_ARRAY) && page.get(11).id().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR) && page.get(10).id().equals(HighPowerSkills.TACTICAL_COORDINATE_FORMATION) && page.get(7).id().equals(HighPowerSkills.SHORT_INTERCEPTION) && page.get(6).id().equals(HighPowerSkills.CORNER_EXCHANGE), "retained NEW333/331/343/342/353 page nodes precede appended NEW341");
        check(page.subList(0, 6).stream().map(ClassicDeveloperLayout.Node::id).toList().equals(List.of(ROOT, SIBLING, ID, HighPowerSkills.RETURN_MARK, HighPowerSkills.RECOVERY_MARK, HighPowerSkills.CROSS_SUBSTITUTION)),
                "311 keeps its place before appended322,323 and321 on the explicit high-power page");
        var root = page.get(0); var sibling = page.get(1); var node = page.get(2);
        check(root.id().equals(ROOT) && root.index() == 313 && root.x() == 118 && root.y() == 61
                && root.parent().isEmpty(), "313 retains its exact existing root coordinates");
        check(sibling.id().equals(SIBLING) && sibling.index() == 312 && sibling.x() == 198 && sibling.y() == 61
                && sibling.parent().equals(ROOT), "312 retains its exact existing child coordinates and parent");
        check(node.id().equals(ID) && node.index() == 311 && node.level() == 1 && node.x() == 38 && node.y() == 61
                && node.parent().equals(ROOT), "311 uses the clear left branch after313");
        check(node.conditions().size() == 2 && node.conditions().get(0).get("minimum").getAsString().equals("portable")
                && node.conditions().get(1).get("type").getAsString().equals("skill_dependency")
                && node.conditions().get(1).get("id").getAsString().equals(ROOT)
                && node.conditions().get(1).get("minimum_exp").getAsDouble() == 0,
                "displayed Portable and zero-mastery313 conditions match native learning");
        for (var item : page) {
            check(item.x() + 8 - 18.6 >= 0 && item.x() + 8 + 18.6 <= 257
                    && item.y() + 8 - 18.6 >= 0 && item.y() + 8 + 18.6 <= 139,
                    "full hover fits the retained viewport " + item.id());
            for (var other : page) if (item != other)
                check(Math.hypot(item.x() - other.x(), item.y() - other.y()) > 37.2,
                        "full hover circles are disjoint " + item.id() + "/" + other.id());
        }
        var edge = ClassicDeveloperTimeline.edge(node.x(), node.y(), root.x(), root.y(), 1);
        check(edge.x0() < root.x() + 8 && edge.x1() > node.x() + 8 && edge.x1() < edge.x0()
                && edge.y0() == edge.y1() && edge.x0() < sibling.x() + 8 - 18.6,
                "existing dependency renderer connects313 to311 clear of312");
        var nav = new HighPowerDeveloperLayout.Navigation(); nav.synchronize("teleporter");
        check(!nav.select(ID) && nav.switchTo(HighPowerDeveloperLayout.Page.HIGH_POWER) && nav.select(ID),
                "311 is selectable only from its actual high-power page");
        check(nav.beginLearning() && nav.back() == HighPowerDeveloperLayout.Back.BUSY,
                "pending311 learning retains the ordinary navigation fence");
        nav.finishLearning();
        check(nav.back() == HighPowerDeveloperLayout.Back.SELECTION && nav.back() == HighPowerDeveloperLayout.Back.CLASSIC_PAGE,
                "Back dismisses311 then restores the canonical page");
        check(Arrays.equals(resource("/assets/academy/textures/abilities/teleporter/skills/threatening_teleport.png"),
                resource("/assets/academy/textures/abilities/teleporter/skills/displaced_projection.png")),
                "311 preserves original threatening_teleport icon bytes");
        String notice = new String(resource("/META-INF/ACADEMY-NOTICE"), StandardCharsets.UTF_8);
        check(notice.contains("NEW311 displaced_projection") && notice.contains("teleporter/threatening_teleport.png")
                && notice.contains("Lambda Innovation"), "packaged notice retains original artwork attribution");
    }

    private static void localizedPlayerContract() throws Exception {
        var english = language("en_us");
        var keys = english.keySet().stream().filter(k -> k.startsWith(REASONS)).collect(Collectors.toSet());
        check(keys.containsAll(Set.of(REASONS + "medium_missing", REASONS + "medium_unloaded", REASONS + "no_medium",
                REASONS + "cast_fence", REASONS + "refund_pending", REASONS + "blocked", REASONS + "insufficient_cp")),
                "custody, unloaded, fixed physical interval and preactivation return outcomes have player labels");
        for (String locale : List.of("en_us", "zh_cn", "zh_tw", "ja_jp")) {
            var words = language(locale);
            check(words.keySet().stream().filter(k -> k.startsWith(REASONS)).collect(Collectors.toSet()).equals(keys),
                    "all311 reason tokens match across locales " + locale);
            for (String key : java.util.stream.Stream.concat(keys.stream(), List.of(
                    "ac.ability.teleporter.displaced_projection.name", "ac.ability.teleporter.displaced_projection.desc",
                    "academy.highpower.displaced_projection.blocked", "academy.highpower.displaced_projection.refused").stream()).toList())
                check(words.has(key) && !words.get(key).getAsString().isBlank(), "nonblank311 translation " + locale + "/" + key);
            check(words.get("academy.highpower.displaced_projection.blocked").getAsString().contains("%s"),
                    "refusal keeps its reason placeholder " + locale);
            SkillLocalizationContracts.verify(words, "teleporter", "displaced_projection", locale);
        }
    }
}
