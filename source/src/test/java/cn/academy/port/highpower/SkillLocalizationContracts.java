package cn.academy.port.highpower;

import cn.academy.port.SkillCatalog;
import cn.academy.port.core.SkillTuning;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Player-facing controls and limits; development prices are tested against gameplay models separately. */
public final class SkillLocalizationContracts {
    private static final Set<String> GENERIC = Set.of("brain_course", "brain_course_advanced", "mind_course");
    private static final Pattern INTERNAL = Pattern.compile("NEW\\d+|V-ADV-\\d+|暂定|暫定|验收|驗收|原型|prototype|provisional", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTITION = Pattern.compile("[〔［\\[](?:高数值|高數值|高能|高出力|高能力|高算力|High Power)", Pattern.CASE_INSENSITIVE);
    private SkillLocalizationContracts() {}
    private static void require(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
    private static String key(String category, String id) { return "ac.ability." + (GENERIC.contains(id) ? "generic" : category) + "." + id; }
    public static void verify(JsonObject words, String category, String id, String locale) {
        var skill = SkillCatalog.find(category, id).orElseThrow();
        String base = key(category, id);
        require(words.has(base + ".name") && words.has(base + ".desc"), "complete skill translation " + locale + "/" + id);
        String name = words.get(base + ".name").getAsString(), description = words.get(base + ".desc").getAsString();
        require(!name.isBlank() && !PARTITION.matcher(name).find(), "skill name belongs to its ability category " + locale + "/" + id);
        if (!locale.equals("en_us")) require(name.codePoints().anyMatch(cp -> cp > 127), "localized skill name has native characters " + locale + "/" + id);
        require(description.length() >= 30 && description.length() <= 1400 && !INTERNAL.matcher(description).find(),
                "concise player-facing skill help without internal proposal or acceptance prose " + locale + "/" + id);
        require(description.contains("LV" + skill.level()), "description follows registered skill tier " + locale + "/" + id);
        for (var prerequisite : skill.requirements()) {
            String prerequisiteName = words.get(key(category, prerequisite.id()) + ".name").getAsString();
            require(description.contains(prerequisiteName), "live prerequisite is named in help " + locale + "/" + id);
            if (prerequisite.exp() > 0) require(description.contains(Math.round(prerequisite.exp() * 100) + "%"),
                    "mastery prerequisite matches live catalog " + locale + "/" + id);
        }
        description = SkillTuning.format(id, description);
        require(!description.contains("{{"), "all tuning fields render in " + locale + "/" + id);
        if (!locale.equals("en_us")) return;
        List<String> limits = switch (id) {
            case "potential_buffer" -> List.of("120", "existing shield", "{{radius}} m", "once per second", "Idle costs no CP", "pulses deal no damage");
            case "thunder_net" -> List.of("{{reach}} m", "6 targets", "0.4 seconds", "3 seconds", "does not affect players");
            case "folded_mirror_array" -> List.of("2 mirrors", "{{placement_reach}} m", "40 m", "0.6 seconds", "{{duration_ticks:s}} seconds", "no tracking");
            case "disintegration_sweep" -> List.of("0.4 seconds", "{{reach}} m", "8 targets", "16 supported soft blocks", "5 seconds", "Only the moving beam hits");
            case "beam_step" -> List.of("0.4 seconds", "3 in 1.6 seconds", "single-player only", "potion effects");
            case "zero_gap_armor" -> List.of("0.3 seconds", "Six plates", "3 facing forward", "1 CP per second");
            case "thermal_shell" -> List.of("{{radius}} m", "0.75 seconds", "does not remove poison or burning");
            case "deflection_strike" -> List.of("{{reach}} m", "2 enemies", "Sneak-tap deals no damage", "Decorative rings have no area damage");
            case "arc_gen" -> List.of("Tap", "Releasing does not cancel");
            case "electron_bomb" -> List.of("Tap", "delay", "{{reach}} m", "Releasing does not cancel");
            case "mine_detect" -> List.of("Tap", "{{duration_ticks:s}} seconds", "blind", "{{reach_novice}} to {{reach_mastered}} m");
            case "body_intensify" -> List.of("Hold", "release", "Jump Boost", "Regeneration", "hunger", "random");
            case "mine_ray_expert" -> List.of("Hold", "{{reach}} m", "not passive", "release");
            case "mine_ray_luck" -> List.of("Hold", "{{reach}} m", "Fortune III", "active", "release");
            case "jet_engine" -> List.of("Hold", "release", "no light shield");
            case "brain_course_advanced" -> List.of("1,500", "100", "stacking");
            case "storm_wing" -> List.of("85%", "60%", "cooling", "4 seconds", "CP exhaustion");
            case "blood_retro" -> List.of("finite-damage", "not a guaranteed kill");
            case "displaced_projection" -> List.of("offhand", "1.5 m", "{{reach}} m", "actual collision", "same needle");
            case "return_mark" -> List.of("Tap", "1 minute", "{{reach}} m", "0.2-second", "clear path", "consumes the mark");
            case "recovery_mark" -> List.of("single needles", "{{reach}} m", "8 entries", "1 minute", "Sneak-tap", "pickup");
            case "cross_substitution" -> List.of("3 actual offhand needles", "Sneak-tap", "{{reach}} m", "{{duration_ticks:s}} seconds", "cannot cross walls");
            case "corner_exchange" -> List.of("{{reach}} m", "0.15-second", "180", "0.3 seconds", "{{duration_ticks:s}} seconds", "no fall rescue");
            case "short_interception" -> List.of("4 ordinary enemy arrows", "1 real inventory needle", "48 m", "0.3 seconds", "{{duration_ticks:s}} seconds");
            case "spatial_relay" -> List.of("A, B and C", "16 stacks", "2 m", "Cannot cross walls", "{{duration_ticks:s}} seconds", "costs CP");
            case "emergency_return" -> List.of("1 minute", "{{reach}} m", "actual PvE damage", "next tick", "Does not cancel", "heal");
            case "remote_scatter_relay" -> List.of("4 relay nodes", "32 m", "160 m", "{{duration_ticks:s}} seconds", "do not track");
            case "scatter_locator_plate" -> List.of("24 m", "0.6 seconds", "next Folded Mirror Array", "{{duration_ticks:s}} seconds", "reserved");
            default -> List.of();
        };
        for (String limit : limits) require(description.contains(SkillTuning.format(id, limit)), "actual player action or restriction " + id + "/" + limit);
    }
    public static void verifyAll() throws Exception {
        int verified = 0;
        for (String locale : List.of("en_us", "zh_cn", "zh_tw", "ja_jp")) {
            try (var reader = new InputStreamReader(java.util.Objects.requireNonNull(SkillLocalizationContracts.class.getResourceAsStream(
                    "/assets/academy/lang/" + locale + ".json")), StandardCharsets.UTF_8)) {
                JsonObject words = JsonParser.parseReader(reader).getAsJsonObject();
                for (var skill : SkillCatalog.REGISTERED) { verify(words, skill.category(), skill.id(), locale); verified++; }
            }
        }
        System.out.println("PASS " + verified + " registered skill/locales with names, controls, live prerequisites and limits");
    }
    public static void main(String[] args) throws Exception { verifyAll(); }
}
