package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.client.ClassicDeveloperLayout;
import cn.academy.port.client.HighPowerDeveloperLayout;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.preset.PresetSkills;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** NEW411 registration and native-core learning; no claim of native combat or visual acceptance. */
public final class VectorFoundationRegressionTest {
    private static int checks;
    private static final String CATEGORY = HighPowerCategories.VECMANIP, ID = HighPowerSkills.DEFLECTION_STRIKE;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static void denied(Runnable action, String why) {
        boolean refused = false; try { action.run(); } catch (IllegalArgumentException expected) { refused = true; }
        check(refused, why);
    }
    private static AbilityProgress ability(String category, int level) {
        var state = new AbilityProgress(); state.changeCategoryClassic(category); state.setLevel(level); state.setActivateState(true); return state;
    }
    private static final class Battery implements DevelopmentProcess.Developer {
        double energy;
        Battery(double energy) { this.energy = energy; }
        public DeveloperType type() { return DeveloperType.PORTABLE; }
        public double energy() { return energy; }
        public double maxEnergy() { return 10000; }
        public boolean tryPullEnergy(double amount) { double paid = Math.min(energy, amount); energy -= paid; return paid == amount; }
    }
    public static void main(String[] args) throws Exception {
        registrationAndLearning();
        savedIdentityAndCategoryGates();
        classicAndHighPowerPages();
        neutralHudAndLocales();
        System.out.println("PASS " + checks + " NEW411 registration, finite learning, save, category, page and neutral HUD checks");
    }
    private static void registrationAndLearning() {
        var skill = SkillCatalog.find(CATEGORY, ID).orElseThrow();
        check(CATEGORY.equals("vecmanip") && ID.equals("deflection_strike") && HighPowerSkills.DEFLECTION_STRIKE_PROPOSAL == 411, "exact vector category, canonical saved ID and proposal");
        check(skill.level() == 1 && skill.controllable() && skill.requirements().isEmpty() && skill.anyLearnedSkillLevel() == 0, "NEW411 is a true LV1 root with no invented prerequisites");
        check(HighPowerSkills.ALL.size() == 51 && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.highpower.HighPowerSkills.ALL.get(49).id().equals("point_meltdown") && HighPowerSkills.ALL.get(48).id().equals("wind_pressure_lift") && HighPowerSkills.ALL.get(47).id().equals("multi_axis_reflection") && HighPowerSkills.ALL.get(45).id().equals("circulation_listening") && HighPowerSkills.ALL.get(44).id().equals("circulation_relief") && HighPowerSkills.ALL.get(43).id().equals("trajectory_return") && HighPowerSkills.ALL.get(42).id().equals("yaw_palm") && HighPowerSkills.ALL.get(41).id().equals("inertial_anchor") && HighPowerSkills.ALL.get(40).id().equals("shear_windblade") && HighPowerSkills.ALL.get(39).id().equals("braking_touch") && HighPowerSkills.ALL.get(37).equals(skill)
                && HighPowerSkills.ALL.get(36).id().equals(HighPowerSkills.MULTIDIMENSIONAL_LANDING_ARRAY), "411 appends after every retained addition");
        check(SkillCatalog.ALL.size() == 50 && SkillCatalog.REGISTERED.size() == 101 && cn.academy.port.SkillCatalog.REGISTERED.get(100).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.SkillCatalog.REGISTERED.get(99).id().equals("point_meltdown") && SkillCatalog.REGISTERED.get(87).equals(skill)
                && SkillCatalog.CONFIG_KEYS.size() == 41 && SkillCatalog.REGISTERED_CONFIG_KEYS.size() == 92 && cn.academy.port.SkillCatalog.REGISTERED_CONFIG_KEYS.contains(cn.academy.port.core.ClassicSkillConfiguration.key("meltdowner", "remote_scatter_relay")) && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0))
                && SkillCatalog.REGISTERED_CONFIG_KEYS.contains(ClassicSkillConfiguration.key(CATEGORY, HighPowerSkills.BRAKING_TOUCH))
                && PresetSkills.IMPLEMENTED.size() == 35 && PresetSkills.REGISTERED.size() == 86 && cn.academy.port.preset.PresetSkills.REGISTERED.contains("remote_scatter_relay") && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.preset.PresetSkills.REGISTERED.contains("point_meltdown"), "only derived registries grow, classic catalog and config aliases stay fixed");
        check(HighPowerSkills.ALL.stream().filter(entry -> entry.category().equals(CATEGORY)).toList().equals(List.of(skill, HighPowerSkills.find(HighPowerSkills.VECTOR_PARRY).orElseThrow(), HighPowerSkills.find(HighPowerSkills.BRAKING_TOUCH).orElseThrow(), HighPowerSkills.find(HighPowerSkills.SHEAR_WINDBLADE).orElseThrow(), HighPowerSkills.find(HighPowerSkills.INERTIAL_ANCHOR).orElseThrow(), HighPowerSkills.find(HighPowerSkills.YAW_PALM).orElseThrow(), HighPowerSkills.find(HighPowerSkills.TRAJECTORY_RETURN).orElseThrow(), HighPowerSkills.find(HighPowerSkills.CIRCULATION_RELIEF).orElseThrow(), HighPowerSkills.find(HighPowerSkills.CIRCULATION_LISTENING).orElseThrow(), HighPowerSkills.find(HighPowerSkills.VECTOR_CAGE).orElseThrow(), HighPowerSkills.find(HighPowerSkills.MULTI_AXIS_REFLECTION).orElseThrow(), HighPowerSkills.find(HighPowerSkills.WIND_PRESSURE_LIFT).orElseThrow())), "only approved 411,412,413,421,422,423,431,432,433,441 442 and appended443 enter vector high power");
        for (int level = 0; level <= 6; level++) {
            var state = ability(CATEGORY, 1); state.level = level;
            check(HighPowerCategories.active(state) == (level >= 1 && level <= 5), "integer vector category gate " + level);
        }
        check(HighPowerCategories.enabled(CATEGORY) && HighPowerCategories.isVector(CATEGORY)
                && ID.equals(HighPowerCategories.recoveryRoot(CATEGORY)), "explicit vector admission and food root");
        for (String category : List.of("vector", "Vector", "unsupported_test", ""))
            check(!HighPowerCategories.enabled(category) && HighPowerCategories.recoveryRoot(category) == null, "unsupported aliases remain closed " + category);
        var state = ability(CATEGORY, 1); state.levelExperience = .125;
        int denominator = SkillCatalog.levelSkillCount(state);
        check(denominator == SkillCatalog.ALL.stream().filter(entry -> entry.category().equals(CATEGORY) && entry.level() == 1 && entry.controllable()).count(), "vector XP denominator comes only from classic active entries");
        check(!HighPowerSkills.eligible(state, ID) && !PresetSkills.edit(state, 0, 0, ID), "unlearned411 cannot cast or bind");
        check(DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill) && SkillCatalog.canLearn(state, skill), "LV1 Portable accepts root without another branch");
        var process = new DevelopmentProcess(); var battery = new Battery(2340); var action = DevelopmentActions.skill(state, skill);
        check(action.stimulations() == 3 && DeveloperType.PORTABLE.ticksPerStimulation() == 26, "three ordinary native stimulations");
        process.start(battery, action); for (int tick = 0; tick < 77; tick++) process.tick();
        check(!state.learned(ID) && battery.energy == 30, "77 paid ticks cannot grant411 early");
        process.tick();
        check(state.learned(ID) && state.exp(ID) == 0 && state.levelExperience == .125 && battery.energy == 0
                && process.state() == DevelopmentProcess.State.DONE, "final paid tick learns only411 without free mastery or XP");
        check(HighPowerSkills.eligible(state, ID) && SkillCatalog.levelSkillCount(state) == denominator, "learning grants eligibility without denominator growth");
        check(!SkillCatalog.canLearn(state, skill), "learned root cannot become a new purchase");
        for (int preset = 0; preset < 4; preset++) for (int slot = 0; slot < 4; slot++) {
            check(PresetSkills.edit(state, preset, slot, ID), "learned411 binds each native slot");
            check(!PresetSkills.edit(state, preset, (slot + 1) % 4, ID), "duplicate same-preset mapping stays rejected");
            check(PresetSkills.edit(state, preset, slot, ""), "mapping clears through ordinary editor");
        }
        var shortState = ability(CATEGORY, 1); var shortBattery = new Battery(2339);
        process = new DevelopmentProcess(); process.start(shortBattery, DevelopmentActions.skill(shortState, skill));
        for (int tick = 0; tick < 78; tick++) process.tick();
        check(!shortState.learned(ID) && shortBattery.energy == 0 && process.state() == DevelopmentProcess.State.FAILED, "finite final shortage gives no learned bit or refund");
    }
    private static void savedIdentityAndCategoryGates() {
        var state = ability(CATEGORY, 4); state.learn("dir_shock"); state.levelExperience = .37; state.extraCp = 3.5;
        state.cp = 31.25; state.overload = 7.5; state.cpDelay = 11; state.overloadDelay = 13;
        var old = AbilityStorage.encodeSaved(state); var restored = AbilityStorage.decodeSaved(old, state.cpDataConfig());
        check(!restored.learned(ID) && AbilityStorage.encodeSaved(restored).equals(old), "old classic vector save stays exact without silent411 learning");
        restored.learn(ID); restored.setActivateState(true); check(PresetSkills.edit(restored, 2, 1, ID), "canonical411 slot saves");
        restored.cp = 27.5; restored.overload = 4.25;
        var tag = AbilityStorage.encodeSaved(restored); var relog = AbilityStorage.decodeSaved(tag, restored.cpDataConfig());
        check(tag.getInt("schema") == 3 && relog.learned(ID) && relog.presets.skill(2, 1).equals(ID)
                && AbilityStorage.encodeSaved(relog).equals(tag), "411 roundtrips existing schema with depleted native resources");
        var skill = SkillCatalog.find(CATEGORY, ID).orElseThrow();
        for (String category : List.of("electromaster", "meltdowner", "teleporter", "unsupported_test")) {
            var wrong = ability(category, 5); wrong.experience.put(ID, 0.0);
            check(!HighPowerSkills.eligible(wrong, ID) && !PresetSkills.selectable(wrong, ID)
                    && !DevelopmentActions.canLearn(wrong, DeveloperType.ADVANCED, skill), "stale411 cannot cross category " + category);
        }
        try {
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key(CATEGORY, ID), new ClassicSkillConfiguration.Flags(false, false)));
            check(!HighPowerSkills.eligible(relog, ID) && !PresetSkills.selectable(relog, ID)
                    && !DevelopmentActions.canLearn(ability(CATEGORY, 1), DeveloperType.PORTABLE, skill), "live disabled411 gates learning, casting and binding");
            check(AbilityStorage.encodeSaved(relog).equals(tag), "disabled configuration does not mutate saved411 or balances");
        } finally { SkillCatalog.resetConfiguration(); }
        relog.changeCategoryClassic("electromaster"); relog.changeCategoryClassic(CATEGORY);
        check(relog.level == 4 && relog.levelExperience == .37 && relog.extraCp == 3.5
                && relog.experience.isEmpty() && relog.presets.skill(2, 1).isEmpty(), "native switching keeps progression but clears learned identities and mappings");
    }
    private static void classicAndHighPowerPages() throws Exception {
        var classic = ClassicDeveloperLayout.NODES.stream().filter(node -> node.category().equals(CATEGORY)).toList();
        check(classic.size() == 12 && HighPowerDeveloperLayout.page(CATEGORY, HighPowerDeveloperLayout.Page.CLASSIC).equals(classic), "all twelve classic vector nodes retain exact metadata and coordinates");
        var high = HighPowerDeveloperLayout.page(CATEGORY, HighPowerDeveloperLayout.Page.HIGH_POWER);
        var assets = Path.of("src/main/resources/assets/academy/textures/abilities/vecmanip/skills");
        check(java.util.Arrays.equals(Files.readAllBytes(assets.resolve("dir_shock.png")), Files.readAllBytes(assets.resolve("deflection_strike.png"))), "411 node uses a present byte-identical original directed-shock icon");
        check(high.size() == 12 && high.get(11).id().equals("wind_pressure_lift") && high.get(10).id().equals("multi_axis_reflection") && high.get(9).id().equals("vector_cage") && high.get(8).id().equals("circulation_listening") && high.get(7).id().equals("circulation_relief") && high.get(6).id().equals("trajectory_return") && high.get(5).id().equals("yaw_palm") && high.get(4).id().equals("inertial_anchor") && high.get(3).id().equals("shear_windblade") && high.get(2).id().equals("braking_touch") && high.getFirst().id().equals(ID) && high.getFirst().index() == 411
                && high.getFirst().level() == 1 && high.getFirst().parent().isEmpty(), "dedicated page retains independent LV1 root before learned412");
        check(high.getFirst().conditions().size() == 1 && high.getFirst().conditions().getFirst().get("minimum").getAsString().equals("portable"), "root learning condition is Portable, with no hidden prerequisite");
        check(HighPowerDeveloperLayout.highPowerTitle(CATEGORY).equals("screen.academy.developer.page_vector_highpower"), "vector has a named page");
        var navigation = new HighPowerDeveloperLayout.Navigation(); navigation.synchronize(CATEGORY);
        check(navigation.switchTo(HighPowerDeveloperLayout.Page.HIGH_POWER) && navigation.select(ID) && navigation.beginLearning(), "vector page selects and starts411");
        check(!navigation.switchTo(HighPowerDeveloperLayout.Page.CLASSIC) && navigation.back() == HighPowerDeveloperLayout.Back.BUSY, "paid development cannot be escaped by page navigation");
        navigation.finishLearning();
        check(navigation.back() == HighPowerDeveloperLayout.Back.SELECTION && navigation.back() == HighPowerDeveloperLayout.Back.CLASSIC_PAGE
                && navigation.back() == HighPowerDeveloperLayout.Back.CLOSE_SCREEN, "Back clears selection then returns to classic then closes");
    }
    private static void neutralHudAndLocales() throws Exception {
        var hud = new HighPowerRecoveryHudModel.Values("19", "20", "0", "0", "slow", "0.25", 80, 70, 0,
                false, "0", "0", false, CATEGORY, "0", "0", false, "0", "0", false);
        check(hud.vector() && hud.categoryLabel().equals("VM") && !hud.electromaster() && !hud.meltdowner() && !hud.teleporter(), "VM is an explicit HUD category");
        check(!hud.shieldVisible() && !hud.shieldActive() && !hud.domainVisible() && !hud.curtainVisible() && !hud.zeroGapVisible() && hud.healing(), "VM advertises only exact health and food-based recovery");
        var identity = new HighPowerRecoveryHudModel.Identity(new java.util.UUID(4, 11), 411, "minecraft:overworld", 1, 1);
        var packet = new HighPowerRecoveryHudPayload.Snapshot(identity, hud);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
        try {
            HighPowerRecoveryHudPayload.Snapshot.CODEC.encode(buffer, packet);
            check(packet.equals(HighPowerRecoveryHudPayload.Snapshot.CODEC.decode(buffer)) && !buffer.isReadable(), "optional v6 HUD wire retains explicit VM identity and exact neutral recovery data");
        } finally { buffer.release(); }
        check(new HighPowerRecoveryHudModel.Values("19", "20", "1", "10", "full", "0", 0, 0, 0, false, "0", "0", false, CATEGORY).shieldVisible(), "authenticated learned412 may advertise only existing base S");
        denied(() -> new HighPowerRecoveryHudModel.Values("19", "20", "0", "0", "full", "0", 0, 0, 0, true, "0", "0", false, CATEGORY), "VM rejects automatic shield flag");
        denied(() -> new HighPowerRecoveryHudModel.Values("19", "20", "0", "0", "full", "0", 0, 0, 0, false, "0", "0", true, CATEGORY), "VM rejects EM reserve benefit");
        denied(() -> new HighPowerRecoveryHudModel.Values("19", "20", "0", "0", "full", "0", 0, 0, 0, false, "0", "0", false, CATEGORY, "0", "0", true), "VM rejects MD curtain benefit");
        for (String lang : List.of("en_us", "zh_cn")) {
            var strings = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/academy/lang/" + lang + ".json"))).getAsJsonObject();
            for (String key : List.of("ac.ability.vecmanip.deflection_strike.name", "ac.ability.vecmanip.deflection_strike.desc", "screen.academy.developer.page_vector_highpower", "hud.academy.highpower.vector_shield_unavailable", "hud.academy.highpower.status.unlearned_vector"))
                check(strings.has(key) && !strings.get(key).getAsString().isBlank(), "localized vector contract " + lang + ":" + key);
            check(strings.get("ac.ability.vecmanip.deflection_strike.desc").getAsString().contains("0.035"), "help discloses actual narrow path " + lang);
            if (lang.equals("zh_cn")) check(strings.get("ac.ability.vecmanip.deflection_strike.name").getAsString().contains("偏转重击"), "Chinese411 name is canonical");
        }
    }
}
