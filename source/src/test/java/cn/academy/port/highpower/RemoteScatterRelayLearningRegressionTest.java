package cn.academy.port.highpower;

import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.preset.PresetSkills;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/** NEW253 append-only registry proof against pinned predecessor source, independent of current totals. */
public final class RemoteScatterRelayLearningRegressionTest {
    private static final String ID = "remote_scatter_relay", CATEGORY = "meltdowner", PARENT = "folded_mirror_array";
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }

    public static void main(String[] args) throws Exception {
        CurrentHighPowerInventory.verify();
        SkillCatalog.resetConfiguration();
        try { registration(); nativeGates(); finiteLearning(); learningWire(); retainedDeveloperPage(); retainedCurrentTestSnapshots(); retainedMdPageTestSnapshots(); localizedHelp(); }
        finally { SkillCatalog.resetConfiguration(); }
        System.out.println("RemoteScatterRelayLearningRegressionTest: " + checks + " checks passed");
    }

    private static void registration() throws Exception {
        byte[] frozen = Files.readAllBytes(Path.of("src/test/resources/highpower-learning/remote-scatter-relay-v77/HighPowerSkills.v76.java"));
        check(sha(frozen).equals("1d921ba1d78120a30b057b921620e728fe033aa04f664b5d2953d657586f4ad8"),
                "immutable pre-253 source retains the independently pinned .76 hash");
        String source = new String(frozen, StandardCharsets.UTF_8); var constants = new HashMap<String, String>();
        var strings = Pattern.compile("\\b([A-Z_]+) = \"([^\"]+)\"").matcher(source);
        while (strings.find()) {
            constants.put(strings.group(1), strings.group(2));
            check(HighPowerSkills.class.getField(strings.group(1)).get(null).equals(strings.group(2)), "predecessor canonical saved-ID constant unchanged: " + strings.group(1));
        }
        var proposals = Pattern.compile("\\b([A-Z_]+_PROPOSAL) = ([0-9]+)").matcher(source);
        while (proposals.find()) check(HighPowerSkills.class.getField(proposals.group(1)).getInt(null) == Integer.parseInt(proposals.group(2)), "predecessor proposal unchanged: " + proposals.group(1));
        var records = Pattern.compile("new SkillCatalog\\.Skill\\(([^,\\n]+), ([^,\\n]+), ([0-9]+), (true|false),\\s*(List\\.of\\((?:[^()]|\\([^()]*\\))*\\)), ([0-9]+)\\)").matcher(source);
        var previous = new ArrayList<SkillCatalog.Skill>();
        while (records.find()) {
            String category = switch (records.group(1)) {
                case "CATEGORY" -> constants.get("CATEGORY");
                case "HighPowerCategories.MELTDOWNER" -> "meltdowner";
                case "HighPowerCategories.TELEPORTER" -> "teleporter";
                case "HighPowerCategories.VECMANIP" -> "vecmanip";
                default -> throw new AssertionError("Unexpected frozen category " + records.group(1));
            };
            var requirements = new ArrayList<SkillCatalog.Requirement>();
            var requirement = Pattern.compile("new SkillCatalog.Requirement\\(([^,]+), ([^)]+)\\)").matcher(records.group(5));
            while (requirement.find()) requirements.add(new SkillCatalog.Requirement(constants.get(requirement.group(1)), Double.parseDouble(requirement.group(2))));
            previous.add(new SkillCatalog.Skill(category, constants.get(records.group(2)), Integer.parseInt(records.group(3)),
                    Boolean.parseBoolean(records.group(4)), List.copyOf(requirements), Integer.parseInt(records.group(6))));
        }
        check(previous.size() == 50, "all 50 immutable .76 predecessor records were independently parsed");
        check(retainsPrevious(HighPowerSkills.ALL, previous), "NEW253 is the sole index-50 append; all 50 predecessor records and ordinals stay exact");
        var expected = new SkillCatalog.Skill(CATEGORY, ID, 5, true, List.of(new SkillCatalog.Requirement(PARENT, 0)), 0);
        check(HighPowerSkills.REMOTE_SCATTER_RELAY.equals(ID) && HighPowerSkills.REMOTE_SCATTER_RELAY_PROPOSAL == 253
                        && HighPowerSkills.ALL.get(50).equals(expected), "NEW253 is precisely controllable MD LV5 after FoldedMirrorArray at zero mastery");
        var expectedIds = new HashSet<String>(); previous.forEach(skill -> expectedIds.add(skill.id())); expectedIds.add(ID);
        CurrentHighPowerInventory.expected().forEach(entry -> expectedIds.add(entry.id()));
        check(expectedIds.size() == 61, "exact 61 high-power base IDs");
        expectedIds.addAll(cn.academy.port.canon.CanonSkills.IDS);
        check(HighPowerSkills.IDS.equals(expectedIds), "ID set is exactly predecessor 50 plus RemoteScatterRelay");
        for (var field : HighPowerSkills.class.getFields()) if (field.getName().endsWith("_PROPOSAL"))
            check(field.getInt(null) == 143 ? field.getName().equals("PULSE_SHIELD_PROPOSAL") && HighPowerSkills.ALL.get(53).id().equals("pulse_shield")
                    : field.getInt(null) == 153 ? field.getName().equals("RAIL_RELAY_PROPOSAL") && HighPowerSkills.ALL.get(54).id().equals("rail_relay")
                    : !field.getName().equals("PULSE_SHIELD_PROPOSAL") && !field.getName().equals("RAIL_RELAY_PROPOSAL"),
                    "143/153 are now implemented exactly once at the approved appended ordinals: " + field.getName());

        var reordered = new ArrayList<>(HighPowerSkills.ALL); Collections.swap(reordered, 17, 50);
        check(!retainsPrevious(reordered, previous), "inserting NEW253 beside FoldedMirrorArray instead of appending fails this proof");
        var changedParent = new ArrayList<>(HighPowerSkills.ALL); var prior = previous.get(49);
        changedParent.set(49, new SkillCatalog.Skill(prior.category(), prior.id(), prior.level(), prior.controllable(),
                List.of(new SkillCatalog.Requirement("vector_cage", 0)), prior.anyLearnedSkillLevel()));
        check(!retainsPrevious(changedParent, previous), "same totals with a rewritten preexisting prerequisite fail this proof");
        var changedLevel = new ArrayList<>(HighPowerSkills.ALL);
        changedLevel.set(49, new SkillCatalog.Skill(prior.category(), prior.id(), 2, prior.controllable(), prior.requirements(), prior.anyLearnedSkillLevel()));
        check(!retainsPrevious(changedLevel, previous), "same totals with a rewritten preexisting integer level fail this proof");

        check(sha(Files.readAllBytes(Path.of("src/main/resources/classic-skills.json"))).equals("fe698b35ce94e9cbd628dc63950bfd706749177d3b0ebe4048c1b4e7905e4433"),
                "original 50-entry classic catalog bytes remain exact");
        check(SkillCatalog.ALL.size() == 50 && SkillCatalog.REGISTERED.size() == (111 + cn.academy.port.canon.CanonSkills.ALL.size())
                        && SkillCatalog.REGISTERED.subList(0, 50).equals(SkillCatalog.ALL)
                        && SkillCatalog.REGISTERED.subList(50, 100).equals(previous) && SkillCatalog.REGISTERED.get(100).equals(expected),
                "classic and predecessor registered ordinals stay exact; RemoteScatterRelay alone occupies index 100");
        Set<String> classicPresets = Set.of("arc_gen", "charging", "railgun", "electron_bomb", "threatening_teleport", "dir_shock", "ground_shock", "dir_blast", "storm_wing", "blood_retro", "vec_accel", "vec_deviation", "vec_reflection", "plasma_cannon", "mag_movement", "mag_manip", "thunder_bolt", "mine_detect", "body_intensify", "thunder_clap", "scatter_bomb", "light_shield", "meltdowner", "mine_ray_basic", "mine_ray_expert", "mine_ray_luck", "ray_barrage", "jet_engine", "electron_missile", "penetrate_teleport", "mark_teleport", "flesh_ripping", "location_teleport", "shift_tp", "flashing");
        check(PresetSkills.IMPLEMENTED.equals(classicPresets) && PresetSkills.REGISTERED.size() == (96 + cn.academy.port.canon.CanonSkills.IDS.size()) && PresetSkills.REGISTERED.contains(ID),
                "all 35 original preset identities remain exact and NEW253 adds one ordinary selectable identity");
        check(SkillCatalog.CONFIG_KEYS.size() == 41 && SkillCatalog.REGISTERED_CONFIG_KEYS.size() == (102 + cn.academy.port.canon.CanonSkills.ALL.size())
                        && SkillCatalog.REGISTERED_CONFIG_KEYS.contains(ClassicSkillConfiguration.key(CATEGORY, ID)),
                "configuration gains exactly the canonical RemoteScatterRelay key");
        for (String category : List.of("electromaster", "meltdowner", "teleporter", "vecmanip")) for (int level = 1; level <= 5; level++) {
            var state = new AbilityProgress(); state.changeCategoryClassic(category); state.setLevel(level); int expectedLevel = level;
            long denominator = SkillCatalog.ALL.stream().filter(skill -> skill.category().equals(category) && skill.level() == expectedLevel && skill.controllable()).count();
            check(SkillCatalog.levelSkillCount(state) == denominator, "high-power additions never enter original XP denominator: " + category + " LV" + level);
        }
    }

    private static boolean retainsPrevious(List<SkillCatalog.Skill> candidate, List<SkillCatalog.Skill> previous) {
        return candidate.size() == 61 && CurrentHighPowerInventory.matches(candidate) && candidate.subList(0, 50).equals(previous) && candidate.get(50).id().equals(ID);
    }

    private static void nativeGates() {
        var state = new AbilityProgress(); state.changeCategoryClassic(CATEGORY); state.setLevel(5); var skill = SkillCatalog.find(CATEGORY, ID).orElseThrow();
        check(!SkillCatalog.canLearn(state, skill) && !PresetSkills.edit(state, 0, 0, ID), "unlearned skill and exact missing parent fail closed");
        state.learn("dual_point"); check(!SkillCatalog.canLearn(state, skill), "grandparent is not the exact FoldedMirrorArray prerequisite");
        state.learn(PARENT);
        check(state.exp(PARENT) == 0 && DeveloperType.minimumForSkill(5) == DeveloperType.ADVANCED
                        && DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill)
                        && !DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill), "zero-mastery FoldedMirrorArray admits exactly the native minimum Advanced developer");
        check(!DevelopmentActions.canLearn(state, DeveloperType.NORMAL, skill), "Normal developer cannot bypass LV5 Advanced requirement");
        state.setLevel(4); check(!DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill), "valid parent and machine cannot bypass integer LV5"); state.setLevel(5);
        SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key(CATEGORY, ID), new ClassicSkillConfiguration.Flags(false, false)));
        check(!SkillCatalog.canLearn(state, skill) && !DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill), "live canonical disabled configuration refuses learning");
        SkillCatalog.resetConfiguration();
    }


    private static final class Battery implements cn.academy.port.develop.DevelopmentProcess.Developer {
        private double energy;
        Battery(double amount) { energy = amount; }
        public DeveloperType type() { return DeveloperType.ADVANCED; }
        public double energy() { return energy; }
        public double maxEnergy() { return type().energy; }
        public boolean tryPullEnergy(double amount) { if (energy < amount) return false; energy -= amount; return true; }
    }
    private static void finiteLearning() {
        var state = new AbilityProgress(); state.changeCategoryClassic(CATEGORY); state.setLevel(5); state.learn(PARENT);
        var skill = SkillCatalog.find(CATEGORY, ID).orElseThrow();
        var action = DevelopmentActions.skill(state, skill);
        check(action.id().equals("skill:" + ID) && action.stimulations() == 15
                && DeveloperType.ADVANCED.ticksPerStimulation() == 16 && DeveloperType.ADVANCED.actualConsumption(15) == 9600,
                "ordinary LV5 learning costs 15 Advanced stimulations, 240 ticks and 9600 IF");
        var process = new cn.academy.port.develop.DevelopmentProcess(); var shortBattery = new Battery(9599);
        process.start(shortBattery, action); for (int tick = 0; tick < 239; tick++) process.tick();
        check(!state.learned(ID) && shortBattery.energy() == 39, "239 finite payments never grant early learning");
        process.tick();
        check(!state.learned(ID) && shortBattery.energy() == 39 && process.state() == cn.academy.port.develop.DevelopmentProcess.State.FAILED,
                "atomic final shortage grants no skill and does not refund spent work");
        var full = new Battery(9600); process.start(full, action); for (int tick = 0; tick < 239; tick++) process.tick();
        check(!state.learned(ID) && full.energy() == 40, "all 240 Advanced payments are required"); process.tick();
        check(state.learned(ID) && state.exp(ID) == 0 && full.energy() == 0 && process.state() == cn.academy.port.develop.DevelopmentProcess.State.DONE,
                "the final finite payment grants only the learned bit with zero mastery");
        state.unlearn(ID); process.start(new Battery(9600), action); for (int tick = 0; tick < 239; tick++) process.tick();
        state.unlearn(PARENT); process.tick();
        check(!state.learned(ID) && process.state() == cn.academy.port.develop.DevelopmentProcess.State.FAILED,
                "final native validation requires the exact learned Folded Mirror Array parent");
    }
    private static void learningWire() {
        var request = new HighPowerLearningProtocol.Request(ID, Integer.MAX_VALUE);
        check(request.wire().equals("rsr:~zik0zj") && HighPowerLearningProtocol.parse(request.wire()).equals(request),
                "bounded wire alias round-trips to the unchanged canonical saved identity");
        String token = "01234567-89ab-cdef-0123-456789abcdef";
        check((token + ":" + request.wire()).getBytes(StandardCharsets.UTF_8).length == 48
                && (token + ":" + ID + ":~zik0zj").getBytes(StandardCharsets.UTF_8).length == 65,
                "compact alias fixes the genuine 65-byte maximum-sequence overflow without widening native limits");
        check(HighPowerLearningProtocol.parse(ID + ":~zik0zj") == null
                && HighPowerLearningProtocol.parse("rsr:~0") == null
                && HighPowerLearningProtocol.parse("rsr:~zik0zk") == null
                && HighPowerLearningProtocol.parse("rsr:" + "0".repeat(65)) == null,
                "noncanonical full compact form, zero, overflow and oversized sequence fail closed");
        var pending = new HighPowerLearningProtocol.Pending(); pending.begin(request);
        check(!pending.receive(HighPowerLearningProtocol.result(new HighPowerLearningProtocol.Request(ID, 1), true, ""))
                && pending.waiting(), "stale sequence cannot consume this learning attempt");
        check(!pending.receive(HighPowerLearningProtocol.result(new HighPowerLearningProtocol.Request(PARENT, Integer.MAX_VALUE), true, ""))
                && pending.waiting(), "a different skill cannot consume this learning attempt");
        var result = HighPowerLearningProtocol.result(request, true, "");
        check(result.getString("skill").equals(ID) && pending.receive(result) && pending.accepted() && !pending.receive(result),
                "only matching canonical feedback completes once");
        for (String action : List.of("hp_remote_relay_start", "hp_remote_relay_release", "hp_remote_relay_abort"))
            check(action.length() <= 32, "each native action retains the original 32-character bound: " + action);
    }
    private static void retainedDeveloperPage() throws Exception {
        byte[] frozen = Files.readAllBytes(Path.of("src/test/resources/highpower-learning/remote-scatter-relay-v77/HighPowerDeveloperLayout.v76.java"));
        check(sha(frozen).equals("257909d55cbffd131eab533a42367d907787acaa542240368d2f91ac27bbe9ed"),
                "the genuine .76 developer layout remains pinned independently of the NEW253 implementation");
        var page = cn.academy.port.client.HighPowerDeveloperLayout.page(CATEGORY, cn.academy.port.client.HighPowerDeveloperLayout.Page.HIGH_POWER);
        var baseline = Pattern.compile("case HighPowerSkills\\.([A-Z_]+) -> \\{x=([0-9.]+);y=([0-9.]+);\\}").matcher(new String(frozen, StandardCharsets.UTF_8));
        int retained = 0;
        while (baseline.find()) {
            String id = (String) HighPowerSkills.class.getField(baseline.group(1)).get(null);
            var node = page.stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
            check(node.x() == CurrentHighPowerInventory.pageCoordinate(id,Double.parseDouble(baseline.group(2)),false) && node.y() == CurrentHighPowerInventory.pageCoordinate(id,Double.parseDouble(baseline.group(3)),true),
                    "every .76 MD node retains its exact accepted page coordinates: " + id);
            retained++;
        }
        var node = page.stream().filter(value -> value.id().equals(ID)).findFirst().orElseThrow();
        check(retained == 12 && page.size() == CurrentHighPowerInventory.pageSize(page) && node.index() == 253 && node.level() == 5
                && node.parent().equals(PARENT) && node.x() == 75 && node.y() == 61,
                "NEW253 alone occupies the free MD page location with exact LV5 mirror prerequisite");
        check(cn.academy.port.client.ClassicDeveloperLayout.NODES.size() == 50
                && cn.academy.port.client.HighPowerDeveloperLayout.page(CATEGORY, cn.academy.port.client.HighPowerDeveloperLayout.Page.CLASSIC)
                        .stream().noneMatch(value -> value.id().equals(ID)),
                "NEW253 remains on the separate high-power page and leaves the classic tree unchanged");
        for (var a : page) for (var b : page) if (!a.id().equals(b.id()))
            check(Math.hypot(a.x() - b.x(), a.y() - b.y()) >= 37.2,
                    "all 13 full-hover circles remain disjoint: " + a.id() + "/" + b.id());
        check(cn.academy.port.client.HighPowerDeveloperLayout.remoteScatterRelayDependency(0).isEmpty()
                && cn.academy.port.client.HighPowerDeveloperLayout.remoteScatterRelayDependency(Double.NaN).isEmpty(),
                "NEW253 branch respects native reveal boundaries");
        for (int sample = 1; sample <= 100; sample++) {
            var edges = cn.academy.port.client.HighPowerDeveloperLayout.remoteScatterRelayDependency(sample / 100d);
            for (var edge : edges) for (var other : page) if (!other.id().equals(ID) && !other.id().equals(PARENT))
                check(java.awt.geom.Line2D.ptSegDist(edge.x0(), edge.y0(), edge.x1(), edge.y1(), other.x() + 8, other.y() + 8) > 21.35,
                        "NEW253 full-width link clears unrelated hover circles during reveal: " + other.id());
        }
    }
    private static void retainedCurrentTestSnapshots() throws Exception {
        Path root = Path.of("src/test/resources/highpower-learning/remote-scatter-relay-v77/current-test-v76-snapshots");
        byte[] manifest = Files.readAllBytes(root.resolve("manifest.json"));
        check(sha(manifest).equals("9abfd077c6b093a3baee7e31d9b79812b4fa67509ac778dbe08a19d50113872b"),
                "the explicit .76 current-learning test snapshot manifest is immutable");
        var files = com.google.gson.JsonParser.parseString(new String(manifest, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("files");
        check(files.size() == 23, "all 23 narrowly maintained current test sources retain their complete original files");
        for (var entry : files.entrySet()) {
            var evidence = entry.getValue().getAsJsonObject();
            check(sha(Files.readAllBytes(root.resolve(evidence.get("snapshot").getAsString()))).equals(evidence.get("sha256").getAsString()),
                    "the byte-exact .76 learning/foundation predecessor remains inspectable: " + entry.getKey());
        }
    }
    private static void retainedMdPageTestSnapshots() throws Exception {
        Path root = Path.of("src/test/resources/highpower-learning/remote-scatter-relay-v77/md-page-v76-snapshots");
        byte[] manifest = Files.readAllBytes(root.resolve("manifest.json"));
        check(sha(manifest).equals("33970b6fd2c6f659ec530185867f1cb3579b7b4fca6c7e89caa3728846fa7f28"),
                "the separate .76 MD-page runner snapshot manifest is immutable");
        var files = com.google.gson.JsonParser.parseString(new String(manifest, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("files");
        check(files.size() == 2, "both maintained MD-page runners retain complete .76 source snapshots");
        for (var entry : files.entrySet()) {
            var evidence = entry.getValue().getAsJsonObject();
            check(sha(Files.readAllBytes(root.resolve(evidence.get("snapshot").getAsString()))).equals(evidence.get("sha256").getAsString()),
                    "the byte-exact .76 MD-page runner remains inspectable: " + entry.getKey());
        }
    }
    private static void localizedHelp() throws Exception {
        for (String locale : List.of("en_us", "zh_cn", "zh_tw")) {
            var language = com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/academy/lang/" + locale + ".json"))).getAsJsonObject();
            SkillLocalizationContracts.verify(language, "meltdowner", ID, locale);
            for (String key : List.of("status", "status_unpaid", "prepared", "endpoint", "measuring", "blocked", "refused", "degenerate", "miss", "damaged", "absorbed", "ended", "node", "distance", "terminal"))
                check(language.has("academy.highpower." + ID + "." + key) && !language.get("academy.highpower." + ID + "." + key).getAsString().isBlank(),
                        locale + " contains native HUD label " + key);
        }
    }
}
