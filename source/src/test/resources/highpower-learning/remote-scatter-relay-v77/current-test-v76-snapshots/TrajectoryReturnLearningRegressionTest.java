package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
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
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** Real shared learning, save, page and codec boundaries; not a native combat/rendering acceptance. */
public final class TrajectoryReturnLearningRegressionTest {
    private static final String ID = "trajectory_return", CATEGORY = "vecmanip";
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static AbilityProgress state() {
        var s = new AbilityProgress(); s.changeCategoryClassic(CATEGORY); s.setLevel(3); s.setActivateState(true); return s;
    }
    private static AbilityProgress prerequisites() {
        var s = state(); s.learn("deflection_strike"); s.learn("braking_touch"); s.learn("yaw_palm"); return s;
    }
    private static final class Battery implements DevelopmentProcess.Developer {
        double remaining;
        Battery(double value) { remaining = value; }
        public DeveloperType type() { return DeveloperType.NORMAL; }
        public double energy() { return remaining; }
        public double maxEnergy() { return 10000; }
        public boolean tryPullEnergy(double amount) {
            if (remaining < amount) return false; remaining -= amount; return true;
        }
    }
    public static void main(String[] args) throws Exception {
        registrationAndLearning(); saveAndBinding(); pageAndClassicIdentity(); localizationAndCodec();
        System.out.println("PASS " + checks + " NEW431 registration, finite learning, save, binding, page and codec checks");
    }
    private static void registrationAndLearning() {
        var skill = SkillCatalog.find(CATEGORY, ID).orElseThrow();
        check(HighPowerSkills.TRAJECTORY_RETURN_PROPOSAL == 431 && HighPowerSkills.ALL.size() == 50 && cn.academy.port.highpower.HighPowerSkills.ALL.get(49).id().equals("point_meltdown") && HighPowerSkills.ALL.get(48).id().equals("wind_pressure_lift") && HighPowerSkills.ALL.get(47).id().equals("multi_axis_reflection") && HighPowerSkills.ALL.get(45).id().equals("circulation_listening") && HighPowerSkills.ALL.get(44).id().equals("circulation_relief")
                && HighPowerSkills.ALL.get(43).equals(skill) && HighPowerSkills.ALL.get(42).id().equals("yaw_palm")
                && HighPowerSkills.ALL.get(41).id().equals("inertial_anchor") && HighPowerSkills.ALL.get(40).id().equals("shear_windblade"),
                "431 appends at index43 with prior vector registrations untouched");
        check(skill.level() == 3 && skill.controllable() && skill.anyLearnedSkillLevel() == 0
                && skill.requirements().equals(List.of(new SkillCatalog.Requirement("yaw_palm", 0))),
                "actual LV3 learned423 prerequisite at zero mastery");
        var s = state();
        check(!SkillCatalog.canLearn(s, skill) && !DevelopmentActions.canLearn(s, DeveloperType.NORMAL, skill), "missing423 refuses learning");
        s.learn("deflection_strike");
        check(!SkillCatalog.canLearn(s, skill), "root411 alone cannot replace learned423");
        s.learn("vector_parry"); s.learn("shear_windblade"); s.learn("inertial_anchor");
        check(!SkillCatalog.canLearn(s, skill), "other learned vector branches cannot replace423");
        s.learn("braking_touch");
        check(!SkillCatalog.canLearn(s, skill), "learned413 still cannot replace learned423");
        s.learn("yaw_palm"); s.level = 2;
        check(!SkillCatalog.canLearn(s, skill) && !DevelopmentActions.canLearn(s, DeveloperType.ADVANCED, skill), "better machine never bypasses integerLV3");
        s.setLevel(3); s.cp = 31.25; s.overload = 7.5; s.levelExperience = .125;
        int denominator = SkillCatalog.levelSkillCount(s);
        check(s.exp("yaw_palm") == 0 && SkillCatalog.canLearn(s, skill)
                && DevelopmentActions.canLearn(s, DeveloperType.NORMAL, skill), "zero mastery learned423 opens Normal learning");
        check(!DevelopmentActions.canLearn(s, DeveloperType.PORTABLE, skill), "portable cannot purchase LV3 even with learned423");
        check(DevelopmentActions.skill(s, skill).stimulations() == DevelopmentActions.skill(s, SkillCatalog.find("teleporter", "short_interception").orElseThrow()).stimulations()
                && DevelopmentActions.skill(s, skill).stimulations() == DevelopmentActions.skill(s, SkillCatalog.find("meltdowner", "beam_step").orElseThrow()).stimulations(),
                "same-level NEW331 and NEW232 use the identical native learning cost formula");
        check(!PresetSkills.edit(s, 0, 0, ID) && !HighPowerSkills.eligible(s, ID), "unlearned431 cannot bind or cast");
        var action = DevelopmentActions.skill(s, skill); var process = new DevelopmentProcess(); var battery = new Battery(5145);
        check(action.stimulations() == 7 && DeveloperType.minimumForSkill(3) == DeveloperType.NORMAL
                && DeveloperType.NORMAL.ticksPerStimulation() == 21
                && DeveloperType.NORMAL.actualConsumption(action.stimulations()) == 5145
                && DeveloperType.ADVANCED.actualConsumption(action.stimulations()) == 4480
                && DeveloperType.ADVANCED.ticksPerStimulation() * action.stimulations() == 112,
                "seven stimulations match same-level Normal147ticks/5145IF and Advanced112ticks/4480IF");
        process.start(battery, action);
        for (int i = 0; i < 146; i++) process.tick();
        check(!s.learned(ID) && battery.remaining == 35 && process.state() == DevelopmentProcess.State.DEVELOPING, "no early grant on146th paid tick");
        process.tick();
        check(s.learned(ID) && s.exp(ID) == 0 && s.levelExperience == .125 && s.cp == s.maxCp() && s.overload == 0
                && battery.remaining == 0 && process.state() == DevelopmentProcess.State.DONE,
                "final real payment grants only learning with unchanged classic CP recalculation and noXP");
        check(SkillCatalog.levelSkillCount(s) == denominator && HighPowerSkills.eligible(s, ID), "high power never expands classic level denominator");
        check(!SkillCatalog.canLearn(s, skill), "learned skill is not a new purchase");

        var shortState = prerequisites(); var shortBattery = new Battery(5144); var shortProcess = new DevelopmentProcess();
        shortProcess.start(shortBattery, DevelopmentActions.skill(shortState, skill));
        for (int i = 0; i < 147; i++) shortProcess.tick();
        check(!shortState.learned(ID) && shortBattery.remaining == 34 && shortProcess.state() == DevelopmentProcess.State.FAILED,
                "atomic Normal-machine oneIF shortage retains34IF and cannot grant learned bit");
        var interrupted = prerequisites(); var interruptedBattery = new Battery(5145); var interruptedProcess = new DevelopmentProcess();
        interruptedProcess.start(interruptedBattery, DevelopmentActions.skill(interrupted, skill));
        for (int i = 0; i < 10; i++) interruptedProcess.tick();
        interruptedProcess.abort(); for (int i = 0; i < 147; i++) interruptedProcess.tick();
        check(!interrupted.learned(ID) && interruptedBattery.remaining == 4795
                && interruptedProcess.state() == DevelopmentProcess.State.FAILED, "abort retains finite spend and cannot grant later");
        var switched = prerequisites(); var switchedProcess = new DevelopmentProcess(); var switchedBattery = new Battery(5145);
        switchedProcess.start(switchedBattery, DevelopmentActions.skill(switched, skill));
        for (int i = 0; i < 146; i++) switchedProcess.tick();
        switched.changeCategoryClassic("electromaster"); switchedProcess.tick();
        check(!switched.learned(ID) && switchedBattery.remaining == 0 && switchedProcess.state() == DevelopmentProcess.State.FAILED,
                "completion rechecks current category after final payment");
    }
    private static void saveAndBinding() {
        var s = prerequisites(); var old = AbilityStorage.encodeSaved(s);
        check(!AbilityStorage.decodeSaved(old, s.cpDataConfig()).learned(ID), "existing save never silently learns431");
        s.learn(ID); s.cp = 27.5; s.overload = 4.25; s.levelExperience = .375;
        for (int preset = 0; preset < 4; preset++) for (int slot = 0; slot < 4; slot++) {
            check(PresetSkills.edit(s, preset, slot, ID), "learned431 binds each native slot");
            check(!PresetSkills.edit(s, preset, (slot + 1) % 4, ID), "duplicate mapping remains rejected");
            check(PresetSkills.edit(s, preset, slot, ""), "normal clearing remains available");
        }
        check(PresetSkills.edit(s, 2, 1, ID), "save canonical binding");
        var saved = AbilityStorage.encodeSaved(s); var loaded = AbilityStorage.decodeSaved(saved, s.cpDataConfig());
        check(saved.getInt("schema") == 3 && loaded.learned(ID) && loaded.presets.skill(2, 1).equals(ID)
                && AbilityStorage.encodeSaved(loaded).equals(saved), "existing schema preserves exact learned identity, binding and depleted resources");
        var skill = SkillCatalog.find(CATEGORY, ID).orElseThrow();
        try {
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key(CATEGORY, ID), new ClassicSkillConfiguration.Flags(false, false)));
            check(!HighPowerSkills.eligible(loaded, ID) && !PresetSkills.selectable(loaded, ID)
                    && !DevelopmentActions.canLearn(prerequisites(), DeveloperType.NORMAL, skill), "live configuration disables learning, binding and use");
            check(AbilityStorage.encodeSaved(loaded).equals(saved), "disabling never erases saved identity or resources");
        } finally { SkillCatalog.resetConfiguration(); }
        loaded.level = 2;
        check(!PresetSkills.mappedUsable(loaded, ID) && !HighPowerSkills.eligible(loaded, ID)
                && loaded.presets.skill(2, 1).equals(ID), "level loss disables use while preserving raw saved mapping");
        for (String category : List.of("electromaster", "meltdowner", "teleporter", "unsupported_test")) {
            var wrong = prerequisites(); wrong.changeCategoryClassic(category); wrong.setLevel(5);
            wrong.experience.put(ID, 0.0); wrong.experience.put("yaw_palm", 0.0);
            check(!HighPowerSkills.eligible(wrong, ID) && !PresetSkills.selectable(wrong, ID)
                    && !DevelopmentActions.canLearn(wrong, DeveloperType.ADVANCED, skill), "stale identities cannot cross category " + category);
        }
        loaded.changeCategoryClassic("electromaster"); loaded.changeCategoryClassic(CATEGORY);
        check(!loaded.learned(ID) && loaded.presets.skill(2, 1).isEmpty(), "ordinary category switch clears learned431 and binding");
    }
    private static void pageAndClassicIdentity() throws Exception {
        var page = HighPowerDeveloperLayout.category(CATEGORY);
        check(page.size() == 12 && page.get(11).id().equals("wind_pressure_lift") && page.get(10).id().equals("multi_axis_reflection") && page.get(9).id().equals("vector_cage") && page.get(8).id().equals("circulation_listening") && page.get(7).id().equals("circulation_relief") && page.get(6).id().equals("trajectory_return") && page.get(5).id().equals("yaw_palm") && HighPowerDeveloperLayout.page(CATEGORY, HighPowerDeveloperLayout.Page.HIGH_POWER).equals(page), "eight-node high-power page retains all seven prior nodes");
        String[] ids = {"deflection_strike", "vector_parry", "braking_touch", "shear_windblade", "inertial_anchor", "yaw_palm", ID};
        String[] parents = {"", "deflection_strike", "deflection_strike", "deflection_strike", "vector_parry", "braking_touch", "yaw_palm"};
        int[] proposals = {411, 412, 413, 421, 422, 423, 431}, levels = {1, 1, 1, 2, 2, 2, 3};
        double[] xs = {118, 198, 118, 38, 118, 198, 118}, ys = {61, 61, 126, 105, 105, 105, 17};
        for (int i = 0; i < ids.length; i++) {
            var node = page.get(i);
            check(node.category().equals(CATEGORY) && node.id().equals(ids[i]) && node.index() == proposals[i]
                    && node.level() == levels[i] && node.x() == xs[i] && node.y() == ys[i] && node.parent().equals(parents[i]),
                    "exact retained node metadata and order for " + ids[i]);
        }
        var node = page.get(6);
        check(node.conditions().size() == 2 && node.conditions().get(0).get("minimum").getAsString().equals("normal")
                && node.conditions().get(1).get("id").getAsString().equals("yaw_palm")
                && node.conditions().get(1).get("minimum_exp").getAsDouble() == 0, "visible developer conditions match native prerequisites");
        check(node.x() + 8 - 18.6 >= 0 && node.x() + 8 + 18.6 <= 257
                && node.y() + 8 - 18.6 >= 0 && node.y() + 8 + 18.6 <= 139, "431 full-hover ring fits native viewport");
        var edge = cn.academy.port.client.ClassicDeveloperTimeline.edge(node.x(), node.y(), page.get(5).x(), page.get(5).y(), 1);
        for (int i = 0; i < 6; i++) {
            var prior = page.get(i);
            check(Math.hypot(node.x() - prior.x(), node.y() - prior.y()) > 37.2, "431 full-hover ring clears " + prior.id());
            if (i == 5) continue;
            double dx = edge.x1() - edge.x0(), dy = edge.y1() - edge.y0();
            double fraction = Math.max(0, Math.min(1, ((prior.x() + 8 - edge.x0()) * dx
                    + (prior.y() + 8 - edge.y0()) * dy) / (dx * dx + dy * dy)));
            check(Math.hypot(prior.x() + 8 - edge.x0() - dx * fraction,
                    prior.y() + 8 - edge.y0() - dy * fraction) > 18.6 + 2.75,
                    "431 native dependency line clears unrelated full-hover ring " + prior.id());
        }
        var classic = ClassicDeveloperLayout.NODES.stream().filter(n -> n.category().equals(CATEGORY)).toList();
        check(classic.size() == 12 && HighPowerDeveloperLayout.page(CATEGORY, HighPowerDeveloperLayout.Page.CLASSIC).equals(classic), "all12 classic vector node records remain unchanged");
        byte[] catalog = Files.readAllBytes(Path.of("src/main/resources/classic-skills.json"));
        check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(catalog))
                .equals("fe698b35ce94e9cbd628dc63950bfd706749177d3b0ebe4048c1b4e7905e4433"), "all50 original classic catalog records remain byte-exact");
        check(SkillCatalog.ALL.size() == 50 && SkillCatalog.CONFIG_KEYS.size() == 41 && PresetSkills.IMPLEMENTED.size() == 35
                && SkillCatalog.REGISTERED.size() == 100 && cn.academy.port.SkillCatalog.REGISTERED.get(99).id().equals("point_meltdown") && SkillCatalog.REGISTERED_CONFIG_KEYS.size() == 91
                && PresetSkills.REGISTERED.size() == 85 && cn.academy.port.preset.PresetSkills.REGISTERED.contains("point_meltdown"), "only exact derived registry totals grow by one");
        var assets = Path.of("src/main/resources/assets/academy/textures/abilities/vecmanip/skills");
        check(Arrays.equals(Files.readAllBytes(assets.resolve("vec_reflection.png")), Files.readAllBytes(assets.resolve(ID + ".png"))), "new utility node reuses exact original vector asset");
        var navigation = new HighPowerDeveloperLayout.Navigation(); navigation.synchronize(CATEGORY);
        check(navigation.switchTo(HighPowerDeveloperLayout.Page.HIGH_POWER) && navigation.select(ID) && navigation.beginLearning(), "newnode is reachable through real shared navigation");
        check(!navigation.switchTo(HighPowerDeveloperLayout.Page.CLASSIC) && navigation.back() == HighPowerDeveloperLayout.Back.BUSY, "paid development remains busy");
        navigation.finishLearning();
        check(navigation.back() == HighPowerDeveloperLayout.Back.SELECTION && navigation.back() == HighPowerDeveloperLayout.Back.CLASSIC_PAGE
                && navigation.back() == HighPowerDeveloperLayout.Back.CLOSE_SCREEN, "Back retains established navigation sequence");
    }
    private static void localizationAndCodec() throws Exception {
        for (String locale : List.of("zh_cn", "en_us")) {
            var strings = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/academy/lang/" + locale + ".json"))).getAsJsonObject();
            for (String key : List.of("ac.ability.vecmanip.trajectory_return.name", "ac.ability.vecmanip.trajectory_return.desc",
                    "message.academy.highpower.trajectory_return.not_ready", "message.academy.highpower.trajectory_return.no_target",
                    "message.academy.highpower.trajectory_return.unsupported"))
                check(strings.has(key) && !strings.get(key).getAsString().isBlank(), "localized learning/use contract " + locale + ":" + key);
            if (locale.equals("zh_cn")) check(strings.get("ac.ability.vecmanip.trajectory_return.name").getAsString().startsWith("轨迹折返"), "workbook Chinese name retained");
        }
        var request = new HighPowerLearningProtocol.Request(ID, Integer.MAX_VALUE);
        String machineWire = "12345678-1234-1234-1234-123456789012:" + request.wire();
        check(machineWire.length() == 62 && HighPowerLearningProtocol.parse(request.wire()).equals(request),
                "full canonical431 ID fits unchanged64-character machine request at maximum sequence");
        var learningBuffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
        try {
            var payload = new AcademyNetwork.Request("machine_hp_learn", machineWire);
            AcademyNetwork.Request.CODEC.encode(learningBuffer, payload);
            check(payload.equals(AcademyNetwork.Request.CODEC.decode(learningBuffer)) && !learningBuffer.isReadable(),
                    "native machine learning codec retains canonical431 ID");
        } finally { learningBuffer.release(); }
        String wire = new HighPowerInput.Input(3, Long.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE).wire();
        check(wire.length() == 52 && HighPowerInput.parse(wire) != null, "largest authenticated identity fits unchanged64-character value limit");
        for (String action : List.of("trajectory_return_tap", "trajectory_return_release", "trajectory_return_abort")) {
            check(action.length() <= 32, "scoped action fits unchanged32-character action limit");
            var payload = new AcademyNetwork.Request(action, wire);
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
            try {
                AcademyNetwork.Request.CODEC.encode(buffer, payload);
                check(payload.equals(AcademyNetwork.Request.CODEC.decode(buffer)) && !buffer.isReadable(), "actual existing request codec roundtrip");
            } finally { buffer.release(); }
        }
    }
}
