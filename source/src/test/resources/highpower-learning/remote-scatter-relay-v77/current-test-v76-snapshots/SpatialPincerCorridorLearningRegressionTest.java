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
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.preset.PresetSkills;
import java.awt.geom.Line2D;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleFunction;

/** NEW341 append-only identity, paid LV4 learning and actual developer route geometry. */
public final class SpatialPincerCorridorLearningRegressionTest {
    private static final String ID = HighPowerSkills.SPATIAL_PINCER_CORRIDOR;
    private static final String ROOT = HighPowerSkills.CROSS_SUBSTITUTION;
    private static final List<String> PREVIOUS_IDS = List.of(
            "forked_arc", "magnetic_bracer", "charge_decoy", "polarity_tether", "conductive_circuit",
            "grounding_return", "magnetic_ground_step", "magnetic_item_pull", "iron_sand_lance",
            "thunder_net", "potential_buffer", "rail_thunder_array", "magnetic_domain", "dual_point",
            "scatter_plates", "beam_etching", "thermal_shell", "folded_mirror_array", "disintegration_sweep",
            "beam_step", "particle_laminate", "directional_dissipation_curtain", "multicore_canopy",
            "zero_gap_armor", "safe_landing_probe", "phase_sidestep", "displaced_projection", "return_mark",
            "recovery_mark", "cross_substitution", "corner_exchange", "short_interception", "spatial_relay",
            "emergency_return", "tactical_coordinate_formation");
    private static int checks;
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    private static AbilityProgress ability(String category, int level) {
        var state = new AbilityProgress(); state.changeCategoryClassic(category); state.setLevel(level); state.setActivateState(true); return state;
    }
    private static final class Battery implements DevelopmentProcess.Developer {
        private final DeveloperType type; private double energy;
        Battery(DeveloperType type, double energy) { this.type = type; this.energy = energy; }
        public DeveloperType type() { return type; }
        public boolean tryPullEnergy(double amount) { if (energy < amount) return false; energy -= amount; return true; }
        public double energy() { return energy; }
        public double maxEnergy() { return type.energy; }
    }
    public static void main(String[] args) {
        SkillCatalog.resetConfiguration();
        try { appendOnlyRegistration(); learningAndLateRefusals(); savedGates(); boundedFeedback(); developerRoutes(); }
        finally { SkillCatalog.resetConfiguration(); }
        System.out.println("SpatialPincerCorridorLearningRegressionTest: " + checks + " checks passed");
    }
    private static void appendOnlyRegistration() {
        var skill = SkillCatalog.find("teleporter", ID).orElseThrow();
        check(ID.equals("spatial_pincer_corridor") && HighPowerSkills.SPATIAL_PINCER_CORRIDOR_PROPOSAL == 341,
                "canonical NEW341 saved identity");
        check(skill.level() == 4 && skill.controllable() && skill.anyLearnedSkillLevel() == 0
                && skill.requirements().equals(List.of(new SkillCatalog.Requirement(ROOT, 0))),
                "only learned321 at zero mastery plus integerLV4 unlock341");
        check(HighPowerSkills.ALL.size() == 50 && cn.academy.port.highpower.HighPowerSkills.ALL.get(49).id().equals("point_meltdown") && HighPowerSkills.ALL.get(48).id().equals("wind_pressure_lift") && HighPowerSkills.ALL.get(47).id().equals("multi_axis_reflection") && HighPowerSkills.ALL.get(45).id().equals("circulation_listening") && HighPowerSkills.ALL.get(44).id().equals("circulation_relief") && HighPowerSkills.ALL.get(43).id().equals("trajectory_return") && HighPowerSkills.ALL.get(42).id().equals("yaw_palm") && HighPowerSkills.ALL.get(41).id().equals("inertial_anchor") && HighPowerSkills.ALL.get(40).id().equals("shear_windblade") && HighPowerSkills.ALL.get(39).id().equals("braking_touch") && HighPowerSkills.IDS.size() == 50 && cn.academy.port.highpower.HighPowerSkills.IDS.contains("point_meltdown") && HighPowerSkills.IDS.contains("wind_pressure_lift") && HighPowerSkills.IDS.contains("multi_axis_reflection") && HighPowerSkills.IDS.contains("circulation_listening") && HighPowerSkills.IDS.contains("circulation_relief") && HighPowerSkills.IDS.contains("trajectory_return") && HighPowerSkills.IDS.contains("yaw_palm") && HighPowerSkills.IDS.contains("inertial_anchor") && HighPowerSkills.IDS.contains("shear_windblade")
                && HighPowerSkills.ALL.subList(0, 35).stream().map(SkillCatalog.Skill::id).toList().equals(PREVIOUS_IDS)
                && HighPowerSkills.ALL.get(35).equals(skill), "all thirty-five prior IDs/ordinals are an unchanged exact prefix");
        check(SkillCatalog.ALL.size() == 50 && ClassicDeveloperLayout.NODES.size() == 50
                && SkillCatalog.CONFIG_KEYS.size() == 41 && PresetSkills.IMPLEMENTED.size() == 35
                && SkillCatalog.REGISTERED.subList(0, 50).equals(SkillCatalog.ALL), "classic50/35 identities and41 config aliases stay untouched");
        check(SkillCatalog.REGISTERED.size() == 100 && cn.academy.port.SkillCatalog.REGISTERED.get(99).id().equals("point_meltdown") && SkillCatalog.REGISTERED.get(85).equals(skill)
                && PresetSkills.REGISTERED.size() == 85 && cn.academy.port.preset.PresetSkills.REGISTERED.contains("point_meltdown") && SkillCatalog.REGISTERED_CONFIG_KEYS.size() == 91,
                "341 adds exactly one catalog, preset and configuration entry");
        check(SkillAvailability.learnable(ID) && PresetSkills.REGISTERED.contains(ID)
                && SkillCatalog.REGISTERED_CONFIG_KEYS.contains(ClassicSkillConfiguration.key("teleporter", ID)),
                "derived registries accept canonical341");
        for (int level = 1; level <= 5; level++) {
            var state = ability("teleporter", level); int lv = level;
            check(SkillCatalog.levelSkillCount(state) == SkillCatalog.ALL.stream().filter(s -> s.category().equals("teleporter")
                    && s.level() == lv && s.controllable()).count(), "classic XP advancement denominator unchanged at LV" + level);
        }
    }
    private static void learningAndLateRefusals() {
        var skill = SkillCatalog.find("teleporter", ID).orElseThrow(); var state = ability("teleporter", 3); state.learn(ROOT);
        check(!SkillCatalog.canLearn(state, skill) && !DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill),
                "learned321 never bypasses integerLV4");
        state.setLevel(4); state.unlearn(ROOT);
        for (String sibling : PREVIOUS_IDS.subList(24, 35)) if (!sibling.equals(ROOT)) state.learn(sibling);
        check(!SkillCatalog.canLearn(state, skill) && !DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill),
                "all other teleporter additions together do not substitute for321");
        state = ability("teleporter", 4); state.learn(ROOT);
        check(state.exp(ROOT) == 0 && DeveloperType.minimumForSkill(4) == DeveloperType.ADVANCED
                && !DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill)
                && !DevelopmentActions.canLearn(state, DeveloperType.NORMAL, skill)
                && DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill), "canonical minimum hardware is Advanced");
        var action = DevelopmentActions.skill(state, skill);
        check(action.id().equals("skill:" + ID) && action.stimulations() == 11 && ClassicRules.learningStimulations(4) == 11
                && DeveloperType.ADVANCED.ticksPerStimulation() * 11 == 176
                && DeveloperType.ADVANCED.estimatedConsumption(11) == 6600
                && DeveloperType.ADVANCED.actualConsumption(11) == 7040, "native extra paid tick yields176 ticks/7040 IF");
        state.levelExperience = .375;
        var process = new DevelopmentProcess(); var exact = new Battery(DeveloperType.ADVANCED, 7040);
        process.start(exact, action); for (int tick = 0; tick < 175; tick++) process.tick();
        check(!state.learned(ID) && exact.energy == 40 && process.state() == DevelopmentProcess.State.DEVELOPING,
                "175 paid ticks cannot grant early341");
        process.tick();
        check(state.learned(ID) && state.exp(ID) == 0 && state.exp(ROOT) == 0 && state.levelExperience == .375
                && exact.energy == 0 && process.state() == DevelopmentProcess.State.DONE,
                "only final paid tick grants canonical learned bit without mastery or XP");
        for (String reason : List.of("battery", "abort", "parent", "level", "category", "configuration")) {
            var candidate = ability("teleporter", 4); candidate.learn(ROOT); candidate.levelExperience = .375;
            var battery = new Battery(DeveloperType.ADVANCED, reason.equals("battery") ? 7039 : 7040);
            process = new DevelopmentProcess(); process.start(battery, DevelopmentActions.skill(candidate, skill));
            for (int tick = 0; tick < 175; tick++) process.tick();
            try {
                switch (reason) {
                    case "abort" -> process.abort();
                    case "parent" -> candidate.unlearn(ROOT);
                    case "level" -> candidate.setLevel(3);
                    case "category" -> candidate.changeCategoryClassic("electromaster");
                    case "configuration" -> SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("teleporter", ID),
                            new ClassicSkillConfiguration.Flags(false, false)));
                    default -> { }
                }
                process.tick();
                check(!candidate.learned(ID) && process.state() == DevelopmentProcess.State.FAILED,
                        "late native gate refuses grant: " + reason);
                check(battery.energy == (reason.equals("abort") ? 40 : reason.equals("battery") ? 39 : 0),
                        "paid ticks are not refunded after late failure: " + reason);
            } finally { SkillCatalog.resetConfiguration(); }
        }
    }
    private static void savedGates() {
        var skill = SkillCatalog.find("teleporter", ID).orElseThrow(); var state = ability("teleporter", 5);
        var previousTp = PREVIOUS_IDS.subList(24, 35);
        for (int i = 0; i < previousTp.size(); i++) { state.learn(previousTp.get(i)); check(PresetSkills.edit(state, i / 4, i % 4, previousTp.get(i)), "prepare retained TP binding"); }
        state.learn(ID); state.learn("threatening_teleport"); check(PresetSkills.edit(state, 3, 2, "threatening_teleport") && PresetSkills.edit(state, 3, 3, ID), "new and classic bindings coexist");
        var tag = AbilityStorage.encodeSaved(state); var restored = AbilityStorage.decodeSaved(tag, state.cpDataConfig());
        check(tag.getInt("schema") == 3 && restored.learned(ID) && restored.presets.skill(3, 3).equals(ID)
                && restored.presets.skill(3, 2).equals("threatening_teleport") && AbilityStorage.encodeSaved(restored).equals(tag),
                "new and classic saved identities roundtrip under unchanged schema3");
        for (int i = 0; i < previousTp.size(); i++) check(restored.learned(previousTp.get(i))
                && restored.presets.skill(i / 4, i % 4).equals(previousTp.get(i)), "prior teleporter learned bit/binding survives");
        for (int level = 1; level <= 5; level++) {
            state.setLevel(level); state.setActivateState(true);
            check(HighPowerSkills.eligible(state, ID) == (level >= 4) && PresetSkills.selectable(state, ID) == (level >= 4)
                    && state.learned(ID) && state.presets.skill(3, 3).equals(ID), "integer level gate preserves saved mapping at" + level);
        }
        state.setActivateState(false); check(!HighPowerSkills.eligible(state, ID), "inactive ability cannot cast341");
        state.setActivateState(true); state.unlearn(ID);
        check(!HighPowerSkills.eligible(state, ID) && !PresetSkills.selectable(state, ID) && state.presets.skill(3, 3).equals(ID),
                "unlearned341 cannot cast while raw save mapping stays intact");
        for (String category : List.of("electromaster", "meltdowner", "vecmanip")) {
            var wrong = ability(category, 5); wrong.experience.put(ROOT, 0.0); wrong.experience.put(ID, 0.0);
            check(!HighPowerSkills.eligible(wrong, ID) && !PresetSkills.selectable(wrong, ID)
                    && !DevelopmentActions.canLearn(wrong, DeveloperType.ADVANCED, skill), "stale canonical IDs cannot cross category");
        }
        state.learn(ID);
        try {
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("teleporter", ID), new ClassicSkillConfiguration.Flags(false, false)));
            var fresh = ability("teleporter", 4); fresh.learn(ROOT);
            check(!SkillCatalog.canLearn(fresh, skill) && !DevelopmentActions.canLearn(fresh, DeveloperType.ADVANCED, skill)
                    && !HighPowerSkills.eligible(state, ID) && state.learned(ID), "disabled341 blocks learning and use without save deletion");
        } finally { SkillCatalog.resetConfiguration(); }
    }
    private static void boundedFeedback() {
        var request = new HighPowerLearningProtocol.Request(ID, Integer.MAX_VALUE);
        check(request.equals(HighPowerLearningProtocol.parse(request.wire()))
                && ("00000000-0000-0000-0000-000000000000:" + request.wire()).length() <= 64,
                "maximum341 sequence fits existing token-authenticated machine payload bound");
        var pending = new HighPowerLearningProtocol.Pending(); pending.begin(request);
        check(!pending.receive(HighPowerLearningProtocol.result(new HighPowerLearningProtocol.Request(ROOT, request.sequence()), true, ""))
                && pending.waiting(), "other skill cannot consume341 learning feedback");
        check(!pending.receive(HighPowerLearningProtocol.result(new HighPowerLearningProtocol.Request(ID, 1), true, ""))
                && pending.waiting(), "old sequence cannot consume newer341 attempt");
        check(pending.receive(HighPowerLearningProtocol.result(request, true, "")) && pending.accepted()
                && !pending.receive(HighPowerLearningProtocol.result(request, true, "")), "matching feedback is one-shot");
    }
    private static void developerRoutes() {
        var page = HighPowerDeveloperLayout.page("teleporter", HighPowerDeveloperLayout.Page.HIGH_POWER);
        check(page.size() == 13 && page.get(12).id().equals(HighPowerSkills.MULTIDIMENSIONAL_LANDING_ARRAY) && page.subList(0, 11).stream().map(ClassicDeveloperLayout.Node::id).toList().equals(PREVIOUS_IDS.subList(24, 35)),
                "all eleven prior TP page identities remain an exact prefix");
        var node = page.get(11);
        check(node.id().equals(ID) && node.index() == 341 && node.level() == 4 && node.x() == 78 && node.y() == 105
                && node.parent().equals(ROOT), "341 uses distinct lower-row slot with canonical321 prerequisite");
        check(node.conditions().size() == 2 && node.conditions().getFirst().get("minimum").getAsString().equals("advanced")
                && node.conditions().getLast().get("id").getAsString().equals(ROOT)
                && node.conditions().getLast().get("minimum_exp").getAsDouble() == 0, "shown conditions match actual paid learning");
        for (var a : page) {
            check(a.x() + 8 - 18.6 >= 0 && a.x() + 8 + 18.6 <= 257
                    && a.y() + 8 - 18.6 >= 0 && a.y() + 8 + 18.6 <= 139, "full hover ring inside viewport");
            for (var b : page) if (a != b) check(Math.hypot(a.x() - b.x(), a.y() - b.y()) > 37.2,
                    "new and retained full-hover rings remain disjoint");
        }
        route(page, ROOT, ID, HighPowerDeveloperLayout::spatialPincerDependency);
        route(page, HighPowerSkills.RETURN_MARK, HighPowerSkills.EMERGENCY_RETURN, HighPowerDeveloperLayout::emergencyReturnDependency);
        var navigation = new HighPowerDeveloperLayout.Navigation(); navigation.synchronize("teleporter");
        check(!navigation.select(ID) && navigation.switchTo(HighPowerDeveloperLayout.Page.HIGH_POWER) && navigation.select(ID),
                "341 selects only in high-power page");
        check(navigation.beginLearning() && navigation.back() == HighPowerDeveloperLayout.Back.BUSY, "pending learning preserves navigation fence");
        navigation.finishLearning();
        check(navigation.back() == HighPowerDeveloperLayout.Back.SELECTION && navigation.back() == HighPowerDeveloperLayout.Back.CLASSIC_PAGE,
                "Back dismisses selected341 before returning to classic page");
    }
    private static void route(List<ClassicDeveloperLayout.Node> page, String parent, String child,
                              DoubleFunction<List<ClassicDeveloperTimeline.Edge>> route) {
        var start = page.stream().filter(n -> n.id().equals(parent)).findFirst().orElseThrow();
        var end = page.stream().filter(n -> n.id().equals(child)).findFirst().orElseThrow();
        var full = route.apply(1);
        check(full.size() == 3 && route.apply(0).isEmpty() && route.apply(Double.NaN).isEmpty(), "three finite native-width segments for" + child);
        check(Math.abs(Math.hypot(full.getFirst().x0() - start.x() - 8, full.getFirst().y0() - start.y() - 8)
                - ClassicDeveloperTimeline.LINK_INSET) < 1e-10
                && Math.abs(Math.hypot(full.getLast().x1() - end.x() - 8, full.getLast().y1() - end.y() - 8)
                - ClassicDeveloperTimeline.LINK_INSET) < 1e-10, "native endpoint insets preserved for" + child);
        double length = full.stream().mapToDouble(e -> Math.hypot(e.x1() - e.x0(), e.y1() - e.y0())).sum();
        for (double reveal : new double[]{0, .1, .5, .9, 1}) {
            var edges = route.apply(reveal);
            check(Math.abs(edges.stream().mapToDouble(e -> Math.hypot(e.x1() - e.x0(), e.y1() - e.y0())).sum() - length * reveal) < 1e-9,
                    "continuous reveal retains complete routed length for" + child);
            for (int i = 0; i < edges.size(); i++) {
                var edge = edges.get(i);
                check(Math.abs(Math.hypot(edge.nx(), edge.ny()) - ClassicDeveloperTimeline.LINK_WIDTH / 2) < 1e-10, "retained native5.5px line");
                check(Math.min(edge.x0(), edge.x1()) - Math.abs(edge.nx()) - 5 >= 0
                        && Math.max(edge.x0(), edge.x1()) + Math.abs(edge.nx()) + 5 <= 257
                        && Math.min(edge.y0(), edge.y1()) - Math.abs(edge.ny()) - 5 >= 0
                        && Math.max(edge.y0(), edge.y1()) + Math.abs(edge.ny()) + 5 <= 139, "full line plus maximum parallax stays inside viewport");
                if (i > 0) check(edge.x0() == edges.get(i - 1).x1() && edge.y0() == edges.get(i - 1).y1(), "route corners remain joined");
                for (var other : page) if (!other.id().equals(parent) && !other.id().equals(child))
                    check(Line2D.ptSegDist(edge.x0(), edge.y0(), edge.x1(), edge.y1(), other.x() + 8, other.y() + 8)
                            > 18.6 + ClassicDeveloperTimeline.LINK_WIDTH / 2, "native link clears unrelated full-hover ring:" + child + "/" + other.id());
            }
        }
    }
}
