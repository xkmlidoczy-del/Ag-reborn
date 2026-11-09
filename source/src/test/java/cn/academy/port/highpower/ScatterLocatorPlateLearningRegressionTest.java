package cn.academy.port.highpower;

import cn.academy.port.SkillCatalog;
import cn.academy.port.client.ClassicDeveloperLayout;
import cn.academy.port.client.HighPowerDeveloperLayout;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.preset.PresetSkills;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/** NEW223 current append proof; the count-bound NEW253 runner remains byte-exact and separately runnable. */
public final class ScatterLocatorPlateLearningRegressionTest {
    private static final String ID = "scatter_locator_plate", CATEGORY = "meltdowner", PARENT = "folded_mirror_array";
    private static final Path SNAPSHOTS = Path.of("src/test/resources/highpower-learning/scatter-locator-v80");
    private static final Pattern RECORD = Pattern.compile("new SkillCatalog\\.Skill\\(([^,\\n]+), ([^,\\n]+), ([0-9]+), (true|false),\\s*(List\\.of\\((?:[^()]|\\([^()]*\\))*\\)), ([0-9]+)\\)");
    private static int checks;

    private ScatterLocatorPlateLearningRegressionTest() {}
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }

    public static void main(String[] args) throws Exception {
        CurrentHighPowerInventory.verify();
        SkillCatalog.resetConfiguration();
        try {
            retainedSnapshots();
            registration();
            nativeLearning();
            finiteLearning();
            learningWire();
            retainedDeveloperPage();
            localizedHelp();
        } finally { SkillCatalog.resetConfiguration(); }
        System.out.println("PASS ScatterLocatorPlateLearningRegressionTest: " + checks + " checks");
    }

    private static void retainedSnapshots() throws Exception {
        byte[] manifest = Files.readAllBytes(SNAPSHOTS.resolve("manifest.json"));
        check(sha(manifest).equals("ccd37f3d29e8125eb32aaa14ce3178eeda25000c39ae9164acd7f1258ab39a4b"), "HEAD snapshot manifest remains independently pinned");
        var data = com.google.gson.JsonParser.parseString(new String(manifest, StandardCharsets.UTF_8)).getAsJsonObject();
        check(data.get("baseline_revision").getAsString().equals("27ecc8d869ae6389253c2e5c8fe4df68b6291424"), "snapshots identify the actual existing .79 commit");
        var files = data.getAsJsonObject("files");
        check(files.size() == 3, "the three complete predecessor sources remain inspectable");
        for (var entry : files.entrySet()) {
            var evidence = entry.getValue().getAsJsonObject();
            check(sha(Files.readAllBytes(SNAPSHOTS.resolve(evidence.get("snapshot").getAsString()))).equals(evidence.get("sha256").getAsString()), "byte-exact predecessor snapshot: " + entry.getKey());
        }
        check(Arrays.equals(Files.readAllBytes(Path.of("src/test/resources/highpower-learning/autonomous-vector-circle-v20/baseline-v80/RemoteScatterRelayLearningRegressionTest.java")),
                Files.readAllBytes(SNAPSHOTS.resolve("RemoteScatterRelayLearningRegressionTest.v79.java"))), "archived historical count-bound NEW253 runner is preserved verbatim; current 61-entry successor is verified separately");
    }

    private static List<SkillCatalog.Skill> predecessor() throws Exception {
        byte[] frozen = Files.readAllBytes(SNAPSHOTS.resolve("HighPowerSkills.v79.java"));
        check(sha(frozen).equals("7b7c560aec52fba4884d07c48d89346667847131075d9cd183934592d4b9d104"), "all original registry bytes are pinned to the genuine .79 source");
        String source = new String(frozen, StandardCharsets.UTF_8);
        var constants = new HashMap<String, String>();
        var strings = Pattern.compile("\\b([A-Z_]+) = \"([^\"]+)\"").matcher(source);
        while (strings.find()) {
            constants.put(strings.group(1), strings.group(2));
            check(HighPowerSkills.class.getField(strings.group(1)).get(null).equals(strings.group(2)), "predecessor canonical saved ID unchanged: " + strings.group(1));
        }
        var proposals = Pattern.compile("\\b([A-Z_]+_PROPOSAL) = ([0-9]+)").matcher(source);
        while (proposals.find()) check(HighPowerSkills.class.getField(proposals.group(1)).getInt(null) == Integer.parseInt(proposals.group(2)), "predecessor proposal unchanged: " + proposals.group(1));
        var previous = new ArrayList<SkillCatalog.Skill>();
        var oldBytes = new ArrayList<String>();
        var records = RECORD.matcher(source);
        while (records.find()) {
            String category = switch (records.group(1)) {
                case "CATEGORY" -> constants.get("CATEGORY");
                case "HighPowerCategories.MELTDOWNER" -> "meltdowner";
                case "HighPowerCategories.TELEPORTER" -> "teleporter";
                case "HighPowerCategories.VECMANIP" -> "vecmanip";
                default -> throw new AssertionError("Unknown frozen category " + records.group(1));
            };
            var requirements = new ArrayList<SkillCatalog.Requirement>();
            var requirement = Pattern.compile("new SkillCatalog.Requirement\\(([^,]+), ([^)]+)\\)").matcher(records.group(5));
            while (requirement.find()) requirements.add(new SkillCatalog.Requirement(constants.get(requirement.group(1)), Double.parseDouble(requirement.group(2))));
            previous.add(new SkillCatalog.Skill(category, constants.get(records.group(2)), Integer.parseInt(records.group(3)),
                    Boolean.parseBoolean(records.group(4)), List.copyOf(requirements), Integer.parseInt(records.group(6))));
            oldBytes.add(records.group());
        }
        var currentBytes = new ArrayList<String>();
        records = RECORD.matcher(Files.readString(Path.of("src/main/java/cn/academy/port/highpower/HighPowerSkills.java")));
        while (records.find()) currentBytes.add(records.group());
        check(previous.size() == 51 && currentBytes.size() == 61 && currentBytes.subList(0, 51).equals(oldBytes), "all 51 old source records retain every byte and ordinal");
        return List.copyOf(previous);
    }

    private static SkillCatalog.Skill expected() {
        return new SkillCatalog.Skill(CATEGORY, ID, 2, true, List.of(new SkillCatalog.Requirement(PARENT, 0)), 0);
    }

    private static boolean exactAppend(List<SkillCatalog.Skill> candidate, List<SkillCatalog.Skill> previous) {
        return previous.size() == 51 && candidate.size() == 61 && CurrentHighPowerInventory.matches(candidate) && candidate.subList(0, 51).equals(previous) && candidate.get(51).equals(expected());
    }

    private static void registration() throws Exception {
        var previous = predecessor();
        check(exactAppend(HighPowerSkills.ALL, previous), "NEW223 alone occupies index 51; all old record fields and ordinals are exact");
        check(HighPowerSkills.SCATTER_LOCATOR_PLATE.equals(ID) && HighPowerSkills.SCATTER_LOCATOR_PLATE_PROPOSAL == 223, "NEW223 canonical identity and proposal are exact");
        var expectedIds = new HashSet<String>(); previous.forEach(skill -> expectedIds.add(skill.id())); expectedIds.add(ID);
        CurrentHighPowerInventory.expected().forEach(entry -> expectedIds.add(entry.id()));
        check(expectedIds.size() == 61, "exact 61 high-power base IDs");
        expectedIds.addAll(cn.academy.port.canon.CanonSkills.IDS);
        check(HighPowerSkills.IDS.equals(expectedIds), "ID set is exactly the predecessor 51 plus NEW223");
        check(sha(Files.readAllBytes(Path.of("src/main/resources/classic-skills.json"))).equals("fe698b35ce94e9cbd628dc63950bfd706749177d3b0ebe4048c1b4e7905e4433"), "classic 50-record catalog retains its original bytes");
        check(SkillCatalog.ALL.size() == 50 && SkillCatalog.REGISTERED.size() == (111 + cn.academy.port.canon.CanonSkills.ALL.size()) && SkillCatalog.REGISTERED.subList(0, 50).equals(SkillCatalog.ALL)
                && SkillCatalog.REGISTERED.subList(50, 101).equals(previous) && SkillCatalog.REGISTERED.get(101).equals(expected()), "classic and all prior registered ordinals remain exact; NEW223 alone is index 101");
        Set<String> classicPresets = Set.of("arc_gen", "charging", "railgun", "electron_bomb", "threatening_teleport", "dir_shock", "ground_shock", "dir_blast", "storm_wing", "blood_retro", "vec_accel", "vec_deviation", "vec_reflection", "plasma_cannon", "mag_movement", "mag_manip", "thunder_bolt", "mine_detect", "body_intensify", "thunder_clap", "scatter_bomb", "light_shield", "meltdowner", "mine_ray_basic", "mine_ray_expert", "mine_ray_luck", "ray_barrage", "jet_engine", "electron_missile", "penetrate_teleport", "mark_teleport", "flesh_ripping", "location_teleport", "shift_tp", "flashing");
        var presets = new HashSet<>(classicPresets); presets.addAll(expectedIds);CurrentHighPowerInventory.expected().forEach(entry -> presets.add(entry.id()));
        check(PresetSkills.IMPLEMENTED.equals(classicPresets) && PresetSkills.REGISTERED.equals(presets) && presets.size() == (96 + cn.academy.port.canon.CanonSkills.IDS.size()), "all 35 classic preset IDs are exact; current presets are their union with the additive registry");
        check(SkillCatalog.CONFIG_KEYS.size() == 41 && SkillCatalog.REGISTERED_CONFIG_KEYS.size() == (102 + cn.academy.port.canon.CanonSkills.ALL.size())
                && SkillCatalog.REGISTERED_CONFIG_KEYS.contains(ClassicSkillConfiguration.key(CATEGORY, ID)), "configuration gains exactly the canonical NEW223 key");
        for (String category : List.of("electromaster", "meltdowner", "teleporter", "vecmanip")) for (int level = 1; level <= 5; level++) {
            var state = new AbilityProgress(); state.changeCategoryClassic(category); state.setLevel(level); int currentLevel = level;
            long classic = SkillCatalog.ALL.stream().filter(skill -> skill.category().equals(category) && skill.level() == currentLevel && skill.controllable()).count();
            check(SkillCatalog.levelSkillCount(state) == classic, "new skills never enter original XP denominator: " + category + " LV" + level);
        }
        for (int i = 0; i < previous.size(); i++) {
            var original = previous.get(i);
            var changed = new ArrayList<>(HighPowerSkills.ALL);
            changed.set(i, new SkillCatalog.Skill(original.category(), original.id(), original.level() + 1, original.controllable(), original.requirements(), original.anyLearnedSkillLevel()));
            check(!exactAppend(changed, previous), "negative control rejects old level mutation at ordinal " + i);
            changed.set(i, new SkillCatalog.Skill(original.category(), original.id(), original.level(), original.controllable(), List.of(new SkillCatalog.Requirement(ID, .5)), original.anyLearnedSkillLevel()));
            check(!exactAppend(changed, previous), "negative control rejects old parent mutation at ordinal " + i);
            changed = new ArrayList<>(HighPowerSkills.ALL); Collections.swap(changed, i, (i + 1) % previous.size());
            check(!exactAppend(changed, previous), "negative control rejects old order mutation at ordinal " + i);
        }
        check(!exactAppend(previous, previous), "missing NEW223 fails even when every old entry is intact");
        var changed = new ArrayList<>(HighPowerSkills.ALL); Collections.swap(changed, 17, 51);
        check(!exactAppend(changed, previous), "inserting NEW223 beside its parent instead of appending fails");
    }

    private static AbilityProgress prepared() {
        var state = new AbilityProgress(); state.changeCategoryClassic(CATEGORY); state.setLevel(2); state.learn(PARENT); return state;
    }

    private static void nativeLearning() {
        var state = prepared(); state.unlearn(PARENT); var skill = SkillCatalog.find(CATEGORY, ID).orElseThrow();
        check(!SkillCatalog.canLearn(state, skill) && !PresetSkills.edit(state, 0, 0, ID), "unlearned NEW223 and missing exact parent fail closed");
        state.learn(HighPowerSkills.DUAL_POINT); check(!SkillCatalog.canLearn(state, skill), "grandparent cannot replace the exact Folded Mirror Array prerequisite");
        state.learn(PARENT);
        check(state.exp(PARENT) == 0 && DeveloperType.minimumForSkill(2) == DeveloperType.PORTABLE, "LV2 uses the native Portable minimum without a mastery threshold");
        for (var machine : DeveloperType.values()) check(DevelopmentActions.canLearn(state, machine, skill), "zero-mastery parent admits supported native developer " + machine);
        state.setLevel(1); check(!DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill), "Advanced developer cannot bypass integer LV2"); state.setLevel(2);
        SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key(CATEGORY, ID), new ClassicSkillConfiguration.Flags(false, false)));
        check(!SkillCatalog.canLearn(state, skill) && !DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill), "disabled canonical configuration refuses learning");
        SkillCatalog.resetConfiguration();
        state.learn(ID); check(PresetSkills.edit(state, 0, 0, ID) && state.presets.contains(0, ID), "learned NEW223 binds through ordinary preset validation");
        state.changeCategoryClassic("teleporter"); check(!DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill), "wrong category cannot learn the MD skill");
    }

    private static final class Battery implements DevelopmentProcess.Developer {
        private double energy;
        Battery(double amount) { energy = amount; }
        public DeveloperType type() { return DeveloperType.PORTABLE; }
        public double energy() { return energy; }
        public double maxEnergy() { return type().energy; }
        public boolean tryPullEnergy(double amount) { if (energy < amount) return false; energy -= amount; return true; }
    }

    private static void finiteLearning() {
        var state = prepared(); var action = DevelopmentActions.skill(state, SkillCatalog.find(CATEGORY, ID).orElseThrow());
        check(action.id().equals("skill:" + ID) && action.stimulations() == 5 && DeveloperType.PORTABLE.ticksPerStimulation() == 26
                && DeveloperType.PORTABLE.actualConsumption(5) == 3900, "native LV2 learning costs five Portable stimulations, 130 ticks and 3900 IF");
        var process = new DevelopmentProcess(); var shortBattery = new Battery(3899);
        process.start(shortBattery, action); for (int tick = 0; tick < 129; tick++) process.tick();
        check(!state.learned(ID) && shortBattery.energy() == 29, "129 paid ticks cannot grant early learning"); process.tick();
        check(!state.learned(ID) && shortBattery.energy() == 29 && process.state() == DevelopmentProcess.State.FAILED, "atomic final shortage grants no skill and refunds no spent energy");
        var fullBattery = new Battery(3900); process.start(fullBattery, action); for (int tick = 0; tick < 129; tick++) process.tick();
        check(!state.learned(ID) && fullBattery.energy() == 30, "all 130 finite payments are required"); process.tick();
        check(state.learned(ID) && state.exp(ID) == 0 && fullBattery.energy() == 0 && process.state() == DevelopmentProcess.State.DONE, "final payment grants only the learned bit with zero mastery");
        state.unlearn(ID); process.start(new Battery(3900), action); for (int tick = 0; tick < 129; tick++) process.tick();
        state.unlearn(PARENT); process.tick();
        check(!state.learned(ID) && process.state() == DevelopmentProcess.State.FAILED, "final native validation rechecks the exact learned parent");
    }

    private static void learningWire() {
        var request = new HighPowerLearningProtocol.Request(ID, Integer.MAX_VALUE);
        check(request.wire().equals("slp:~zik0zj") && HighPowerLearningProtocol.parse(request.wire()).equals(request), "slp alias round-trips to the canonical saved ID at maximum sequence");
        String token = "01234567-89ab-cdef-0123-456789abcdef";
        check((token + ":" + request.wire()).getBytes(StandardCharsets.UTF_8).length == 48
                && (token + ":" + ID + ":~zik0zj").getBytes(StandardCharsets.UTF_8).length == 66, "compact alias retains the native 64-byte machine limit");
        for (String invalid : List.of(ID + ":~zik0zj", "slp:~0", "slp:~zik0zk", "slp:1", "slp:~01", "slp:" + "0".repeat(65)))
            check(HighPowerLearningProtocol.parse(invalid) == null, "noncanonical, zero, overflow or oversized request fails closed: " + invalid);
        check(HighPowerLearningProtocol.parse(ID + ":1").equals(new HighPowerLearningProtocol.Request(ID, 1)), "legacy canonical decimal request remains supported");
        var pending = new HighPowerLearningProtocol.Pending(); pending.begin(request);
        check(!pending.receive(HighPowerLearningProtocol.result(new HighPowerLearningProtocol.Request(ID, 1), true, "")) && pending.waiting(), "stale sequence cannot consume the current learning attempt");
        check(!pending.receive(HighPowerLearningProtocol.result(new HighPowerLearningProtocol.Request(PARENT, Integer.MAX_VALUE), true, "")) && pending.waiting(), "different skill cannot consume the current learning attempt");
        var result = HighPowerLearningProtocol.result(request, true, "");
        check(result.getString("skill").equals(ID) && pending.receive(result) && pending.accepted() && !pending.receive(result), "only matching canonical feedback completes once");
    }

    private static void retainedDeveloperPage() throws Exception {
        byte[] frozen = Files.readAllBytes(SNAPSHOTS.resolve("HighPowerDeveloperLayout.v79.java"));
        check(sha(frozen).equals("afc690720856b9198e83e102d332a26f99ed3716a7d2b622893fa2e49844541a"), "complete prior developer layout remains independently pinned");
        var page = HighPowerDeveloperLayout.page(CATEGORY, HighPowerDeveloperLayout.Page.HIGH_POWER);
        var baseline = Pattern.compile("case HighPowerSkills\\.([A-Z_]+) -> \\{x=([0-9.]+);y=([0-9.]+);\\}").matcher(new String(frozen, StandardCharsets.UTF_8));
        int retained = 0;
        while (baseline.find()) {
            String id = (String) HighPowerSkills.class.getField(baseline.group(1)).get(null);
            var node = page.stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
            double expectedX = CurrentHighPowerInventory.pageCoordinate(id,Double.parseDouble(baseline.group(2)),false);
            check(node.x() == expectedX && node.y() == CurrentHighPowerInventory.pageCoordinate(id,Double.parseDouble(baseline.group(3)),true), "only explicitly reviewed page-coordinate relocations are allowed; all raw .79 coordinates stay exact: " + id); retained++;
        }
        var node = page.stream().filter(value -> value.id().equals(ID)).findFirst().orElseThrow();
        check(retained == 13 && page.size() == CurrentHighPowerInventory.pageSize(page) && node.index() == 223 && node.level() == 2 && node.parent().equals(PARENT)
                && node.x() == 16 && node.y() == 17, "NEW223 occupies the bounded top-left position with its exact LV2 prerequisite");
        List<String> previousOrder = List.of("dual_point", "scatter_plates", "beam_etching", "thermal_shell", "folded_mirror_array",
                "disintegration_sweep", "beam_step", "point_meltdown", "particle_laminate", "directional_dissipation_curtain",
                "multicore_canopy", "zero_gap_armor", "remote_scatter_relay");
        for (var nodes : List.of(HighPowerDeveloperLayout.category(CATEGORY), page))
            check(nodes.size() == CurrentHighPowerInventory.pageSize(nodes) && nodes.subList(0, 13).stream().map(ClassicDeveloperLayout.Node::id).toList().equals(previousOrder)
                    && nodes.get(12).id().equals(HighPowerSkills.REMOTE_SCATTER_RELAY) && nodes.get(13).id().equals(ID),
                    "all thirteen old MD source/page ordinals remain exact; NEW223 appends after relay at index13");
        var reorderedNodes = new ArrayList<>(page); Collections.swap(reorderedNodes, 12, 13);
        check(!reorderedNodes.subList(0, 13).stream().map(ClassicDeveloperLayout.Node::id).toList().equals(previousOrder),
                "negative control rejects inserting NEW223 before the retained relay node");
        var rawNodes = HighPowerDeveloperLayout.category(CATEGORY);
        var oldRaw = Pattern.compile("node\\(category,HighPowerSkills\\.([A-Z_]+),[0-9]+,[0-9]+,([0-9.]+),([0-9.]+),").matcher(new String(frozen, StandardCharsets.UTF_8));
        int rawRetained = 0;
        while (oldRaw.find()) {
            String id = (String) HighPowerSkills.class.getField(oldRaw.group(1)).get(null);
            if (!previousOrder.contains(id)) continue;
            var current = rawNodes.stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
            check(current.x() == Double.parseDouble(oldRaw.group(2)) && current.y() == Double.parseDouble(oldRaw.group(3)), "all thirteen historical raw MD coordinates stay exact: " + id);
            rawRetained++;
        }
        check(rawRetained == 13 && rawNodes.stream().filter(value -> value.id().equals(HighPowerSkills.BEAM_STEP)).findFirst().orElseThrow().x() == 50,
                "NEW232 moves only on the display page, preserving its raw source x50");
        check(node.x() >= 0 && node.y() >= 0 && node.x() + 16 <= 256 && node.y() + 16 <= 144, "new native 16-pixel node is inside the existing page canvas");
        for (var current : page) check(current.x() + 8 - 18.6 - 5 >= 0 && current.x() + 8 + 18.6 + 5 <= 257
                && current.y() + 8 - 18.6 - 5 >= 0 && current.y() + 8 + 18.6 + 5 <= 139,
                "every complete full-hover circle plus maximum parallax stays inside the original canvas: " + current.id());
        check(8 + 8 - 18.6 - 5 < 0, "negative control detects the original x8/y8 clipped hover/parallax footprint");
        var merged=cn.academy.port.client.UnifiedSkillTreeLayout.category(CATEGORY);
        var navigation=new HighPowerDeveloperLayout.Navigation();navigation.synchronize(CATEGORY);
        check(ClassicDeveloperLayout.NODES.size()==50&&merged.size()==ClassicDeveloperLayout.category(CATEGORY).size()
                &&merged.stream().anyMatch(value->!HighPowerSkills.contains(value.id()))&&navigation.selectIn(ID,merged),
                "legacy source catalog stays at 50; the actual merged category tree contains original skills and selects the new skill directly");
        for (int i = 0; i < page.size(); i++) for (int j = i + 1; j < page.size(); j++) {
            var a = page.get(i); var b = page.get(j);
            check(Math.hypot(a.x() - b.x(), a.y() - b.y()) >= 37.2, "all current full-hover circles remain disjoint: " + a.id() + "/" + b.id());
        }
        check(node.conditions().stream().anyMatch(condition -> condition.get("type").getAsString().equals("skill_dependency")
                && condition.get("id").getAsString().equals(PARENT) && condition.get("minimum_exp").getAsDouble() == 0), "displayed dependency retains canonical identity and zero mastery");
        check(HighPowerDeveloperLayout.scatterLocatorDependency(0).isEmpty() && HighPowerDeveloperLayout.scatterLocatorDependency(Double.NaN).isEmpty(), "NEW223 branch respects native reveal boundaries");
        for (int sample = 1; sample <= 100; sample++) for (var edge : HighPowerDeveloperLayout.scatterLocatorDependency(sample / 100d))
            for (var other : page) if (!other.id().equals(ID) && !other.id().equals(PARENT))
                check(java.awt.geom.Line2D.ptSegDist(edge.x0(), edge.y0(), edge.x1(), edge.y1(), other.x() + 8, other.y() + 8) > 21.35,
                        "NEW223 full-width route clears unrelated hover circles throughout reveal: " + other.id());
        for (int sample = 1; sample <= 100; sample++) for (var edge : HighPowerDeveloperLayout.beamStepDependency(sample / 100d))
            for (var other : page) if (!other.id().equals(HighPowerSkills.BEAM_STEP) && !other.id().equals(HighPowerSkills.THERMAL_SHELL))
                check(java.awt.geom.Line2D.ptSegDist(edge.x0(), edge.y0(), edge.x1(), edge.y1(), other.x() + 8, other.y() + 8) > 21.35,
                        "shifted NEW232 full-width route clears every unrelated hover circle throughout reveal: " + other.id());
        check(Files.readString(Path.of("src/main/java/cn/academy/port/client/ClassicDeveloperScreen.java")).contains("UnifiedSkillTreeLayout.category(AcademyClient.state.category)") && cn.academy.port.client.UnifiedSkillTreeLayout.category("meltdowner").stream().anyMatch(n -> n.id().equals(ID)), "the merged single-page tree shows NEW223 beside the original meltdowner skills");
        var parent = page.stream().filter(value -> value.id().equals(PARENT)).findFirst().orElseThrow();
        check(page.stream().filter(other -> !other.id().equals(ID) && !other.id().equals(PARENT)).anyMatch(other ->
                java.awt.geom.Line2D.ptSegDist(parent.x() + 8, parent.y() + 8, node.x() + 8, node.y() + 8, other.x() + 8, other.y() + 8) <= 21.35),
                "negative control detects the unsafe direct connector instead of silently accepting it");
        var dualPoint = page.stream().filter(value -> value.id().equals(HighPowerSkills.DUAL_POINT)).findFirst().orElseThrow();
        check(java.awt.geom.Line2D.ptSegDist(34, 90, 4, 90, dualPoint.x() + 8, dualPoint.y() + 8) <= 21.35,
                "negative control detects the original 21-pixel Dual Point clearance failure");
    }

    private static void localizedHelp() throws Exception {
        for (String locale : List.of("en_us", "zh_cn", "zh_tw", "ja_jp")) {
            var language = com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/academy/lang/" + locale + ".json"))).getAsJsonObject();
            String base = "ac.ability." + CATEGORY + "." + ID;
            check(language.has(base + ".name") && !language.get(base + ".name").getAsString().isBlank(), locale + " provides a visible skill name");
            check(language.has(base + ".desc"), locale + " provides a learning description");
            SkillLocalizationContracts.verify(language, CATEGORY, ID, locale);
        }
    }
}
