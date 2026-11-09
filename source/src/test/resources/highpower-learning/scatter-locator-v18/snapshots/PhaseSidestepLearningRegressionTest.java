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

/** NEW312 uses the real finite developer and preset models; no world, client or aggregate run. */
public final class PhaseSidestepLearningRegressionTest {
    private static final String ID = HighPowerSkills.PHASE_SIDESTEP;
    private static final String ROOT = HighPowerSkills.SAFE_LANDING_PROBE;
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
        try (var in = Objects.requireNonNull(PhaseSidestepLearningRegressionTest.class.getResourceAsStream(path), path)) {
            return in.readAllBytes();
        }
    }
    private static JsonObject language(String locale) throws Exception {
        return JsonParser.parseString(new String(resource("/assets/academy/lang/" + locale + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
    }
    public static void main(String[] args) throws Exception {
        SkillCatalog.resetConfiguration();
        try {
            registrationAndFiniteLearning();
            saveBindingAndGates();
            layoutAndLocalization();
        } finally { SkillCatalog.resetConfiguration(); }
        System.out.println("PhaseSidestepLearningRegressionTest: " + checks + " checks passed");
    }
    private static void registrationAndFiniteLearning() {
        var skill = SkillCatalog.find("teleporter", ID).orElseThrow();
        check(ID.equals("phase_sidestep") && HighPowerSkills.PHASE_SIDESTEP_PROPOSAL == 312, "exact proposal and persistent ID");
        check(skill.category().equals("teleporter") && skill.level() == 1 && skill.controllable()
                && skill.anyLearnedSkillLevel() == 0, "TP LV1 controllable addition without alternate-level shortcut");
        check(skill.requirements().equals(List.of(new SkillCatalog.Requirement(ROOT, 0))), "learned NEW313 is the sole prerequisite; no NEW311 or mastery gate");
        check(HighPowerSkills.ALL.get(25).equals(skill) && HighPowerSkills.IDS.contains(ID)
                && SkillAvailability.learnable(ID) && PresetSkills.REGISTERED.contains(ID), "ordinary catalog learning and preset ingress include NEW312");
        check(SkillCatalog.ALL.size() == 50 && ClassicDeveloperLayout.NODES.size() == 50
                && PresetSkills.IMPLEMENTED.size() == 35, "classic fifty nodes and thirty-five active IDs unchanged");
        check(SkillCatalog.REGISTERED.size() == 101 && cn.academy.port.SkillCatalog.REGISTERED.get(100).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.SkillCatalog.REGISTERED.get(99).id().equals("point_meltdown") && PresetSkills.REGISTERED.size() == 86 && cn.academy.port.preset.PresetSkills.REGISTERED.contains("remote_scatter_relay") && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.preset.PresetSkills.REGISTERED.contains("point_meltdown")
                && HighPowerSkills.ALL.size() == 51 && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.highpower.HighPowerSkills.ALL.get(49).id().equals("point_meltdown") && HighPowerSkills.ALL.get(48).id().equals("wind_pressure_lift") && HighPowerSkills.ALL.get(47).id().equals("multi_axis_reflection") && HighPowerSkills.ALL.get(45).id().equals("circulation_listening") && HighPowerSkills.ALL.get(44).id().equals("circulation_relief") && HighPowerSkills.ALL.get(43).id().equals("trajectory_return") && HighPowerSkills.ALL.get(42).id().equals("yaw_palm") && HighPowerSkills.ALL.get(41).id().equals("inertial_anchor") && HighPowerSkills.ALL.get(40).id().equals("shear_windblade") && HighPowerSkills.ALL.get(39).id().equals("braking_touch") && HighPowerSkills.ALL.get(30).id().equals(HighPowerSkills.CORNER_EXCHANGE) && HighPowerSkills.ALL.get(31).id().equals(HighPowerSkills.SHORT_INTERCEPTION) && HighPowerSkills.IDS.size() == 51 && cn.academy.port.highpower.HighPowerSkills.IDS.contains("remote_scatter_relay") && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.highpower.HighPowerSkills.IDS.contains("point_meltdown") && HighPowerSkills.IDS.contains("wind_pressure_lift") && HighPowerSkills.IDS.contains("multi_axis_reflection") && HighPowerSkills.IDS.contains("circulation_listening") && HighPowerSkills.IDS.contains("circulation_relief") && HighPowerSkills.IDS.contains("trajectory_return") && HighPowerSkills.IDS.contains("yaw_palm") && HighPowerSkills.IDS.contains("inertial_anchor") && HighPowerSkills.IDS.contains("shear_windblade")
                && HighPowerSkills.ALL.get(32).id().equals(HighPowerSkills.SPATIAL_RELAY)
                && HighPowerSkills.ALL.get(33).id().equals(HighPowerSkills.EMERGENCY_RETURN)
                && HighPowerSkills.ALL.get(34).id().equals(HighPowerSkills.TACTICAL_COORDINATE_FORMATION)
                && SkillCatalog.REGISTERED.get(84).equals(HighPowerSkills.ALL.get(34))
                && HighPowerSkills.ALL.get(35).id().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR)
                && SkillCatalog.REGISTERED.get(85).equals(HighPowerSkills.ALL.get(35)), "exactly thirty-five retained registrations plus appended NEW341");
        check(HighPowerSkills.ALL.subList(0, 30).stream().filter(s -> s.category().equals("teleporter")).map(SkillCatalog.Skill::id).toList()
                .equals(List.of(ROOT, ID, HighPowerSkills.DISPLACED_PROJECTION, HighPowerSkills.RETURN_MARK, HighPowerSkills.RECOVERY_MARK, HighPowerSkills.CROSS_SUBSTITUTION)), "TP preserves root313 and child312 before appended311,322,323 and321");
        var state = ability("teleporter", 1);
        int denominator = SkillCatalog.levelSkillCount(state);
        check(denominator == SkillCatalog.ALL.stream().filter(s -> s.category().equals("teleporter")
                && s.level() == 1 && s.controllable()).count(), "the original LV1 skills form the complete XP denominator");
        check(!SkillCatalog.canLearn(state, skill) && !DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill), "unlearned313 refuses both learning validators");
        check(!HighPowerSkills.eligible(state, ID) && !PresetSkills.selectable(state, ID)
                && !PresetSkills.edit(state, 0, 0, ID), "unlearned312 cannot cast or enter a preset");
        state.learn(ROOT);
        check(state.exp(ROOT) == 0 && SkillCatalog.canLearn(state, skill)
                && DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill), "learned313 with zero mastery enables ordinary Portable learning");
        var action = DevelopmentActions.skill(state, skill);
        check(action.id().equals("skill:phase_sidestep") && action.stimulations() == 3
                && ClassicRules.learningStimulations(1) == 3
                && DeveloperType.PORTABLE.ticksPerStimulation() * action.stimulations() == 78
                && DeveloperType.PORTABLE.actualConsumption(action.stimulations()) == 2340, "three native stimulations take 78 ticks and 2340 IF");
        var process = new DevelopmentProcess(); var shortBattery = new Battery(2339);
        process.start(shortBattery, action);
        for (int tick = 0; tick < 77; tick++) process.tick();
        check(process.state() == DevelopmentProcess.State.DEVELOPING && shortBattery.energy == 29
                && !state.learned(ID), "one-IF-short battery reaches final tick without early learning");
        process.tick();
        check(process.state() == DevelopmentProcess.State.FAILED && shortBattery.energy == 0
                && !state.learned(ID), "insufficient final IF drains remainder and grants no learned bit");
        state.levelExperience = .375; state.cp = 17.25; state.overload = 4.5;
        var battery = new Battery(2340); process.start(battery, action);
        for (int tick = 0; tick < 77; tick++) process.tick();
        check(!state.learned(ID) && battery.energy == 30, "full battery still waits for final paid tick");
        process.tick();
        check(process.state() == DevelopmentProcess.State.DONE && battery.energy == 0 && state.learned(ID), "exact finite payment completes learning");
        check(state.exp(ID) == 0 && state.levelExperience == .375,
                "learning grants no mastery or level XP; live high-power ledger preservation is covered by native tests");
        check(SkillCatalog.levelSkillCount(state) == denominator
                && ClassicRules.levelThreshold(1, SkillCatalog.levelSkillCount(state)) == ClassicRules.levelThreshold(1, denominator), "learning cannot inflate classic advancement threshold");
        state.unlearn(ID); process.start(new Battery(2340), DevelopmentActions.skill(state, skill));
        for (int tick = 0; tick < 77; tick++) process.tick();
        state.unlearn(ROOT); process.tick();
        check(process.state() == DevelopmentProcess.State.FAILED && !state.learned(ID), "prerequisite is revalidated at paid completion");
    }
    private static void saveBindingAndGates() {
        var skill = SkillCatalog.find("teleporter", ID).orElseThrow();
        var old = ability("teleporter", 1); old.learn(ROOT); old.learn("threatening_teleport");
        check(PresetSkills.edit(old, 0, 0, ROOT) && PresetSkills.edit(old, 0, 1, "threatening_teleport"), "setup existing high-power and classic bindings");
        old.levelExperience = .375; old.cp = 17.25; old.overload = 4.5;
        var tag = AbilityStorage.encodeSaved(old); var state = AbilityStorage.decodeSaved(tag, old.cpDataConfig());
        check(!state.learned(ID) && state.presets.skill(0, 0).equals(ROOT)
                && state.presets.skill(0, 1).equals("threatening_teleport") && AbilityStorage.encodeSaved(state).equals(tag), "pre-312 save gains no learning, refill or remapping");
        state.setActivateState(true); state.learn(ID);
        check(PresetSkills.edit(state, 3, 3, ID) && !PresetSkills.edit(state, 3, 2, ID), "new skill uses existing fourth-page R slot and retains duplicate rejection");
        check(SkillPresets.MAX_PRESETS == 4 && SkillPresets.MAX_KEYS == 4, "no extra pages or key bindings");
        tag = AbilityStorage.encodeSaved(state); var saved = AbilityStorage.decodeSaved(tag, state.cpDataConfig());
        check(saved.learned(ID) && saved.exp(ID) == 0 && saved.presets.skill(3, 3).equals(ID)
                && saved.presets.skill(0, 0).equals(ROOT) && saved.presets.skill(0, 1).equals("threatening_teleport")
                && AbilityStorage.encodeSaved(saved).equals(tag), "312 learned bit, exact ID and existing bindings roundtrip without schema change");
        check(HighPowerSkills.eligible(state, ID), "learned active TP may use ordinary NEW312 gate");
        state.setLevel(0);
        check(state.learned(ID) && !HighPowerSkills.eligible(state, ID) && !PresetSkills.selectable(state, ID), "stored learning cannot bypass actual level downgrade");
        state.setLevel(1); state.setActivateState(true);
        for (String category : List.of("electromaster", "meltdowner", "vecmanip")) {
            var wrong = ability(category, 5); wrong.experience.put(ROOT, 0.0); wrong.experience.put(ID, 0.0);
            check(!HighPowerSkills.eligible(wrong, ID) && !PresetSkills.selectable(wrong, ID)
                    && !DevelopmentActions.canLearn(wrong, DeveloperType.ADVANCED, skill), "stale learned state cannot cross category " + category);
        }
        var disabled = new ClassicSkillConfiguration.Flags(false, false);
        try {
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("teleporter", ID), disabled));
            var fresh = ability("teleporter", 1); fresh.learn(ROOT);
            check(!DevelopmentActions.canLearn(fresh, DeveloperType.PORTABLE, skill) && !SkillCatalog.canLearn(fresh, skill), "disabled312 refuses both learning validators");
            check(!HighPowerSkills.eligible(state, ID) && !PresetSkills.selectable(state, ID)
                    && state.learned(ID) && state.presets.skill(3, 3).equals(ID), "live disable blocks use without deleting stored learning or binding");
            var snapshot = SkillCatalog.configurationSnapshot(); SkillCatalog.resetConfiguration();
            check(SkillCatalog.applyConfigurationSnapshot(snapshot) && !SkillCatalog.enabled("teleporter", ID), "bounded configuration snapshot includes312");
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("teleporter", ROOT), disabled));
            fresh.unlearn(ROOT);
            check(!SkillCatalog.canLearn(fresh, skill) && !DevelopmentActions.canLearn(fresh, DeveloperType.PORTABLE, skill), "disabled313 never removes the learned prerequisite");
        } finally { SkillCatalog.resetConfiguration(); }
    }
    private static void layoutAndLocalization() throws Exception {
        var original = ClassicDeveloperLayout.NODES.stream().filter(n -> n.category().equals("teleporter")).toList();
        check(original.size() == 12 && HighPowerDeveloperLayout.page("teleporter", HighPowerDeveloperLayout.Page.CLASSIC).equals(original), "all twelve classic TP nodes retain full metadata and placement");
        var page = HighPowerDeveloperLayout.page("teleporter", HighPowerDeveloperLayout.Page.HIGH_POWER);
        check(page.size() == 13 && page.get(12).id().equals(HighPowerSkills.MULTIDIMENSIONAL_LANDING_ARRAY) && page.get(11).id().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR) && page.get(10).id().equals(HighPowerSkills.TACTICAL_COORDINATE_FORMATION) && page.get(7).id().equals(HighPowerSkills.SHORT_INTERCEPTION) && page.get(6).id().equals(HighPowerSkills.CORNER_EXCHANGE), "retained NEW333/331/343/342/353 page nodes precede appended NEW341");
        check(page.subList(0, 6).stream().map(ClassicDeveloperLayout.Node::id).toList().equals(List.of(ROOT, ID, HighPowerSkills.DISPLACED_PROJECTION, HighPowerSkills.RETURN_MARK, HighPowerSkills.RECOVERY_MARK, HighPowerSkills.CROSS_SUBSTITUTION)), "six prior TP nodes retain their exact page prefix");
        var root = page.getFirst(); var node = page.stream().filter(n -> n.id().equals(ID)).findFirst().orElseThrow();
        check(root.id().equals(ROOT) && root.index() == 313 && root.x() == 118 && root.y() == 61
                && root.parent().isEmpty(), "NEW313 keeps its existing root identity and position");
        check(node.id().equals(ID) && node.index() == 312 && node.level() == 1 && node.parent().equals(ROOT), "NEW312 is visibly linked to learned313");
        check(node.conditions().size() == 2 && node.conditions().get(0).get("minimum").getAsString().equals("portable")
                && node.conditions().get(1).get("type").getAsString().equals("skill_dependency")
                && node.conditions().get(1).get("id").getAsString().equals(ROOT)
                && node.conditions().get(1).get("minimum_exp").getAsDouble() == 0, "displayed tier and zero-mastery prerequisite match actual learning");
        for (var item : page) check(item.x() + 8 - 18.6 >= 0 && item.x() + 8 + 18.6 <= 257
                && item.y() + 8 - 18.6 >= 0 && item.y() + 8 + 18.6 <= 139, "full hover fits existing viewport " + item.id());
        check(Math.hypot(node.x() - root.x(), node.y() - root.y()) > 37.2, "two full hover rings stay separate");
        var edge = ClassicDeveloperTimeline.edge(node.x(), node.y(), root.x(), root.y(), 1);
        check(edge.x0() > root.x() + 8 && edge.x1() < node.x() + 8 && edge.x1() > edge.x0()
                && edge.y0() == edge.y1(), "existing native dependency renderer connects the two nodes");
        var nav = new HighPowerDeveloperLayout.Navigation(); nav.synchronize("teleporter");
        check(!nav.select(ID) && nav.switchTo(HighPowerDeveloperLayout.Page.HIGH_POWER) && nav.select(ID), "selection uses the actual TP high-power page");
        check(nav.beginLearning() && nav.back() == HighPowerDeveloperLayout.Back.BUSY, "pending development keeps standard navigation fence");
        nav.finishLearning();
        check(nav.back() == HighPowerDeveloperLayout.Back.SELECTION && nav.back() == HighPowerDeveloperLayout.Back.CLASSIC_PAGE, "Back dismisses312 then returns to canonical page");
        check(Arrays.equals(resource("/assets/academy/textures/abilities/teleporter/skills/flashing.png"),
                resource("/assets/academy/textures/abilities/teleporter/skills/phase_sidestep.png")), "NEW312 reuses original flashing icon bytes");
        String notice = new String(resource("/META-INF/ACADEMY-NOTICE"), StandardCharsets.UTF_8);
        check(notice.contains("NEW312 phase_sidestep") && notice.contains("teleporter/flashing.png")
                && notice.contains("Lambda Innovation"), "original icon authorship retained in packaged notice");
        var english = language("en_us");
        var reasonKeys = english.keySet().stream().filter(k -> k.startsWith("academy.highpower.phase_sidestep.reason.")).collect(java.util.stream.Collectors.toSet());
        check(reasonKeys.contains("academy.highpower.phase_sidestep.reason.corridor_block_collision")
                && reasonKeys.contains("academy.highpower.phase_sidestep.reason.phase_destination_changed")
                && reasonKeys.contains("academy.highpower.phase_sidestep.reason.commit_unconfirmed"), "path, fixed destination and ambiguous commit reasons have explicit labels");
        for (String locale : List.of("en_us", "zh_cn", "zh_tw", "ja_jp")) {
            var words = language(locale);
            check(words.keySet().stream().filter(k -> k.startsWith("academy.highpower.phase_sidestep.reason.")).collect(java.util.stream.Collectors.toSet()).equals(reasonKeys), "all refusal tokens match across locales " + locale);
            for (String key : java.util.stream.Stream.concat(reasonKeys.stream(), List.of(node.nameKey(), node.descriptionKey(),
                    "academy.highpower.phase_sidestep.blocked", "academy.highpower.phase_sidestep.refused").stream()).toList())
                check(words.has(key) && !words.get(key).getAsString().isBlank(), "nonblank312 translation " + locale + "/" + key);
            check(words.get("academy.highpower.phase_sidestep.blocked").getAsString().contains("%s"), "refusal reason placeholder retained " + locale);
            String description = words.get(node.descriptionKey()).getAsString();
            check(description.contains("0.15") && description.contains("78") && description.contains("2340")
                    && description.contains("0.05"), "timing cue, finite learning and low-cost anchor disclosed " + locale);
        }
    }
}
