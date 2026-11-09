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

/** NEW233 append-only registry proof against pinned predecessor source, independent of current totals. */
public final class PointMeltdownRegistrationRegressionTest {
    private static final String ID = "point_meltdown", CATEGORY = "meltdowner", PARENT = "beam_etching";
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }

    public static void main(String[] args) throws Exception {
        CurrentHighPowerInventory.verify();
        SkillCatalog.resetConfiguration();
        try { registration(); nativeGates(); }
        finally { SkillCatalog.resetConfiguration(); }
        System.out.println("PointMeltdownRegistrationRegressionTest: " + checks + " checks passed");
    }

    private static void registration() throws Exception {
        byte[] frozen = Files.readAllBytes(Path.of("src/test/resources/current-geometry-proof/charging-core/registry-443-v12/HighPowerSkills.v71.java"));
        check(sha(frozen).equals("cb3f225d820e1361510d021a52910f7b5f0ee2c3b329cfabbfd67ab3226b0b46"),
                "immutable pre-443 source retains the independently pinned .71 hash");
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
        check(previous.size() == 48, "all 48 immutable .71 predecessor records were independently parsed");
        previous.add(new SkillCatalog.Skill("vecmanip", "wind_pressure_lift", 4, true,
                List.of(new SkillCatalog.Requirement("multi_axis_reflection", 0)), 0));
        check(HighPowerSkills.WIND_PRESSURE_LIFT.equals("wind_pressure_lift") && HighPowerSkills.WIND_PRESSURE_LIFT_PROPOSAL == 443,
                "last pre-233 append retains its persistent identity and proposal");
        check(retainsPrevious(HighPowerSkills.ALL, previous), "NEW233 is the sole index-49 append; all 49 predecessor records and ordinals stay exact");
        var expected = new SkillCatalog.Skill(CATEGORY, ID, 3, true, List.of(new SkillCatalog.Requirement(PARENT, 0)), 0);
        check(HighPowerSkills.POINT_MELTDOWN.equals(ID) && HighPowerSkills.POINT_MELTDOWN_PROPOSAL == 233
                        && HighPowerSkills.ALL.get(49).equals(expected), "NEW233 is precisely controllable MD LV3 after BeamEtching at zero mastery");
        var expectedIds = new HashSet<String>(); previous.forEach(skill -> expectedIds.add(skill.id())); expectedIds.add(ID);
        CurrentHighPowerInventory.expected().forEach(entry -> expectedIds.add(entry.id()));
        check(expectedIds.size()==61,"exact historical high-power ID base");
        expectedIds.addAll(cn.academy.port.canon.CanonSkills.IDS);
        check(HighPowerSkills.IDS.equals(expectedIds), "exact historical base and canon extension ID union");
        for (var field : HighPowerSkills.class.getFields()) if (field.getName().endsWith("_PROPOSAL"))
            check(field.getInt(null) == 143 ? field.getName().equals("PULSE_SHIELD_PROPOSAL") && HighPowerSkills.ALL.get(53).id().equals("pulse_shield")
                    : field.getInt(null) == 153 ? field.getName().equals("RAIL_RELAY_PROPOSAL") && HighPowerSkills.ALL.get(54).id().equals("rail_relay")
                    : !field.getName().equals("PULSE_SHIELD_PROPOSAL") && !field.getName().equals("RAIL_RELAY_PROPOSAL"),
                    "143/153 are now implemented exactly once at the approved appended ordinals: " + field.getName());

        var reordered = new ArrayList<>(HighPowerSkills.ALL); Collections.swap(reordered, 15, 49);
        check(!retainsPrevious(reordered, previous), "inserting NEW233 beside BeamEtching instead of appending fails this proof");
        var changedParent = new ArrayList<>(HighPowerSkills.ALL); var prior = previous.get(48);
        changedParent.set(48, new SkillCatalog.Skill(prior.category(), prior.id(), prior.level(), prior.controllable(),
                List.of(new SkillCatalog.Requirement("vector_cage", 0)), prior.anyLearnedSkillLevel()));
        check(!retainsPrevious(changedParent, previous), "same totals with a rewritten preexisting prerequisite fail this proof");
        var changedLevel = new ArrayList<>(HighPowerSkills.ALL);
        changedLevel.set(48, new SkillCatalog.Skill(prior.category(), prior.id(), 3, prior.controllable(), prior.requirements(), prior.anyLearnedSkillLevel()));
        check(!retainsPrevious(changedLevel, previous), "same totals with a rewritten preexisting integer level fail this proof");

        check(sha(Files.readAllBytes(Path.of("src/main/resources/classic-skills.json"))).equals("fe698b35ce94e9cbd628dc63950bfd706749177d3b0ebe4048c1b4e7905e4433"),
                "original 50-entry classic catalog bytes remain exact");
        check(SkillCatalog.ALL.size() == 50 && SkillCatalog.REGISTERED.size() == (111 + cn.academy.port.canon.CanonSkills.ALL.size())
                        && SkillCatalog.REGISTERED.subList(0, 50).equals(SkillCatalog.ALL)
                        && SkillCatalog.REGISTERED.subList(50, 99).equals(previous) && SkillCatalog.REGISTERED.get(99).equals(expected),
                "classic and predecessor registered ordinals stay exact; PointMeltdown alone occupies index 99");
        Set<String> classicPresets = Set.of("arc_gen", "charging", "railgun", "electron_bomb", "threatening_teleport", "dir_shock", "ground_shock", "dir_blast", "storm_wing", "blood_retro", "vec_accel", "vec_deviation", "vec_reflection", "plasma_cannon", "mag_movement", "mag_manip", "thunder_bolt", "mine_detect", "body_intensify", "thunder_clap", "scatter_bomb", "light_shield", "meltdowner", "mine_ray_basic", "mine_ray_expert", "mine_ray_luck", "ray_barrage", "jet_engine", "electron_missile", "penetrate_teleport", "mark_teleport", "flesh_ripping", "location_teleport", "shift_tp", "flashing");
        check(PresetSkills.IMPLEMENTED.equals(classicPresets) && PresetSkills.REGISTERED.size() == (96 + cn.academy.port.canon.CanonSkills.IDS.size()) && PresetSkills.REGISTERED.contains(ID),
                "all 35 original preset identities remain exact and NEW233 adds one ordinary selectable identity");
        check(SkillCatalog.CONFIG_KEYS.size() == 41 && SkillCatalog.REGISTERED_CONFIG_KEYS.size() == (102 + cn.academy.port.canon.CanonSkills.ALL.size())
                        && SkillCatalog.REGISTERED_CONFIG_KEYS.contains(ClassicSkillConfiguration.key(CATEGORY, ID)),
                "configuration gains exactly the canonical PointMeltdown key");
        for (String category : List.of("electromaster", "meltdowner", "teleporter", "vecmanip")) for (int level = 1; level <= 5; level++) {
            var state = new AbilityProgress(); state.changeCategoryClassic(category); state.setLevel(level); int expectedLevel = level;
            long denominator = SkillCatalog.ALL.stream().filter(skill -> skill.category().equals(category) && skill.level() == expectedLevel && skill.controllable()).count();
            check(SkillCatalog.levelSkillCount(state) == denominator, "high-power additions never enter original XP denominator: " + category + " LV" + level);
        }
    }

    private static boolean retainsPrevious(List<SkillCatalog.Skill> candidate, List<SkillCatalog.Skill> previous) {
        return candidate.size() == 61 && CurrentHighPowerInventory.matches(candidate) && candidate.subList(0, 49).equals(previous) && candidate.get(49).id().equals(ID);
    }

    private static void nativeGates() {
        var state = new AbilityProgress(); state.changeCategoryClassic(CATEGORY); state.setLevel(3); var skill = SkillCatalog.find(CATEGORY, ID).orElseThrow();
        check(!SkillCatalog.canLearn(state, skill) && !PresetSkills.edit(state, 0, 0, ID), "unlearned skill and exact missing parent fail closed");
        state.learn("dual_point"); check(!SkillCatalog.canLearn(state, skill), "grandparent is not the exact BeamEtching prerequisite");
        state.learn(PARENT);
        check(state.exp(PARENT) == 0 && DeveloperType.minimumForSkill(3) == DeveloperType.NORMAL
                        && DevelopmentActions.canLearn(state, DeveloperType.NORMAL, skill)
                        && !DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, skill), "zero-mastery BeamEtching admits exactly the native minimum Normal developer");
        state.setLevel(2); check(!DevelopmentActions.canLearn(state, DeveloperType.NORMAL, skill), "valid parent and machine cannot bypass integer LV3"); state.setLevel(3);
        SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key(CATEGORY, ID), new ClassicSkillConfiguration.Flags(false, false)));
        check(!SkillCatalog.canLearn(state, skill) && !DevelopmentActions.canLearn(state, DeveloperType.NORMAL, skill), "live canonical disabled configuration refuses learning");
    }
}
