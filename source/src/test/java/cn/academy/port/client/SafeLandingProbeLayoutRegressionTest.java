package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.highpower.HighPowerSkills;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Actual TP developer navigation, immutable classic page and four-locale asset coverage. */
public final class SafeLandingProbeLayoutRegressionTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static byte[] resource(String name) throws Exception {
        try (var in = Objects.requireNonNull(SafeLandingProbeLayoutRegressionTest.class.getResourceAsStream(name), name)) { return in.readAllBytes(); }
    }
    private static void retainedMeltdownerPageAfter253() {
        String[] ids = {"dual_point", "scatter_plates", "beam_etching", "thermal_shell", "folded_mirror_array", "disintegration_sweep", "beam_step", "point_meltdown", "particle_laminate", "directional_dissipation_curtain", "multicore_canopy", "zero_gap_armor"};
        int[] proposals = {211, 212, 213, 222, 221, 231, 232, 233, 241, 242, 251, 252};
        int[] levels = {1, 1, 1, 2, 2, 3, 3, 3, 4, 4, 5, 5};
        String[] parents = {"", "dual_point", "dual_point", "scatter_plates", "dual_point", "folded_mirror_array", "thermal_shell", "beam_etching", "disintegration_sweep", "scatter_plates", "particle_laminate", "directional_dissipation_curtain"};
        double[][] rawPositions = {{160,110},{83,87},{92.1,10.6},{230,110},{206,97},{160,105},{50,20},{20,105},{225,63},{142,40},{8,110},{160,61}};
        double[][] pagePositions = {{26,61},{110,29},{110,97},{206,29},{206,97},{160,105},{50,20},{20,105},{225,63},{160,20},{62,106},{160,61}};
        var raw = HighPowerDeveloperLayout.category("meltdowner");
        var page = HighPowerDeveloperLayout.page("meltdowner", HighPowerDeveloperLayout.Page.HIGH_POWER);
        check(raw.size() == 15 && page.size() == 15, "NEW253 is the sole thirteenth MD source/page node");
        for (int i = 0; i < ids.length; i++) {
            var prior = raw.get(i); var shown = page.get(i);
            check(prior.category().equals("meltdowner") && prior.id().equals(ids[i]) && prior.index() == proposals[i]
                    && prior.level() == levels[i] && prior.parent().equals(parents[i])
                    && prior.x() == rawPositions[i][0] && prior.y() == rawPositions[i][1],
                    "the exact .76 MD source node and ordinal remain unchanged: " + ids[i]);
            check(shown.category().equals(prior.category()) && shown.id().equals(prior.id()) && shown.index() == prior.index()
                    && shown.level() == prior.level() && shown.parent().equals(prior.parent())
                    && shown.x() == cn.academy.port.highpower.CurrentHighPowerInventory.pageCoordinate(shown.id(),pagePositions[i][0],false) && shown.y() == cn.academy.port.highpower.CurrentHighPowerInventory.pageCoordinate(shown.id(),pagePositions[i][1],true)
                    && shown.conditions().equals(prior.conditions()) && shown.hintIcon().equals(prior.hintIcon())
                    && shown.nameKey().equals(prior.nameKey()) && shown.descriptionKey().equals(prior.descriptionKey()),
                    "the exact .76 MD display position and identity remain unchanged: " + ids[i]);
        }
        for (var node : List.of(raw.get(12), page.get(12)))
            check(node.category().equals("meltdowner") && node.id().equals("remote_scatter_relay") && node.index() == 253
                    && node.level() == 5 && node.x() == 75 && node.y() == 61 && node.parent().equals("folded_mirror_array")
                    && node.conditions().size() == 2 && node.conditions().getFirst().get("minimum").getAsString().equals("advanced")
                    && node.conditions().get(1).get("type").getAsString().equals("skill_dependency")
                    && node.conditions().get(1).get("id").getAsString().equals("folded_mirror_array")
                    && node.conditions().get(1).get("minimum_exp").getAsDouble() == 0,
                    "NEW253 appends its precise LV5 node at 75,61 with Advanced and zero-mastery NEW221 conditions");
    }

    public static void main(String[] args) throws Exception {
        SkillCatalog.resetConfiguration();
        cn.academy.port.highpower.CurrentHighPowerInventory.verify();
        check(ClassicDeveloperLayout.NODES.size() == 50 && SkillCatalog.ALL.size() == 50, "classic nodes and XP catalog stay fifty");
        var baseline = ClassicDeveloperLayout.NODES.stream().filter(n -> n.category().equals("teleporter")).toList();
        var classic = HighPowerDeveloperLayout.page("teleporter", HighPowerDeveloperLayout.Page.CLASSIC);
        check(baseline.size() == 12 && classic.equals(baseline), "all twelve canonical TP node IDs positions parents assets and conditions preserved");
        var high = HighPowerDeveloperLayout.page("teleporter", HighPowerDeveloperLayout.Page.HIGH_POWER);
        check(high.size() == 15 && high.get(12).id().equals(HighPowerSkills.MULTIDIMENSIONAL_LANDING_ARRAY) && high.get(11).id().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR) && high.get(10).id().equals(HighPowerSkills.TACTICAL_COORDINATE_FORMATION) && high.get(9).id().equals(HighPowerSkills.EMERGENCY_RETURN) && high.get(8).id().equals(HighPowerSkills.SPATIAL_RELAY) && high.get(7).id().equals(HighPowerSkills.SHORT_INTERCEPTION) && high.get(6).id().equals(HighPowerSkills.CORNER_EXCHANGE) && high.get(1).id().equals(HighPowerSkills.PHASE_SIDESTEP) && high.get(2).id().equals(HighPowerSkills.DISPLACED_PROJECTION)
                && high.get(3).id().equals(HighPowerSkills.RETURN_MARK) && high.get(4).id().equals(HighPowerSkills.RECOVERY_MARK) && high.get(5).id().equals(HighPowerSkills.CROSS_SUBSTITUTION), "all twelve retained TP nodes precede the single appended NEW351");
        int[] retainedProposals = {313, 312, 311, 322, 323, 321, 333, 331, 343};
        double[] retainedX = {118, 198, 38, 118, 38, 198, 198, 118, 38};
        double[] retainedY = {61, 61, 61, 105, 105, 105, 17, 17, 17};
        String[] retainedParents = {"", "safe_landing_probe", "safe_landing_probe", "safe_landing_probe",
                "displaced_projection", "displaced_projection", "return_mark", "cross_substitution", "recovery_mark"};
        for (int i = 0; i < retainedProposals.length; i++) {
            var retained = high.get(i);
            check(retained.index() == retainedProposals[i] && retained.level() == (i < 3 ? 1 : i < 6 ? 2 : i < 8 ? 3 : 4)
                    && retained.x() == retainedX[i] && retained.y() == retainedY[i]
                    && retained.parent().equals(retainedParents[i]), "all prior TP proposal, level, coordinates and prerequisite retained " + retained.id());
        }
        var relay = high.get(8);
        check(relay.index() == 343 && relay.level() == 4 && relay.x() == 38 && relay.y() == 17
                && relay.parent().equals(HighPowerSkills.RECOVERY_MARK), "NEW343 appends in its own LV4 slot after recovery_mark");
        var emergency = high.get(9);
        check(emergency.index() == 342 && emergency.level() == 4 && emergency.x() == 78 && emergency.y() == 17
                && emergency.parent().equals(HighPowerSkills.RETURN_MARK)
                && Math.hypot(emergency.x() - relay.x(), emergency.y() - relay.y()) > 37.2,
                "NEW342 appends after all nine retained nodes without overlapping NEW343");
        var tactical = high.get(10);
        check(tactical.index()==353 && tactical.level()==5 && tactical.x()==158 && tactical.y()==61
                && tactical.parent().equals(HighPowerSkills.SPATIAL_RELAY), "NEW353 appends in its own slot after spatial_relay");
        for(int i=0;i<10;i++) check(Math.hypot(tactical.x()-high.get(i).x(),tactical.y()-high.get(i).y())>37.2,
                "NEW353 full-hover ring does not overlap retained node "+high.get(i).id());
        var node = high.getFirst();
        check(node.category().equals("teleporter") && node.id().equals(HighPowerSkills.SAFE_LANDING_PROBE)
                && node.index() == 313 && node.level() == 1 && node.parent().isEmpty(), "NEW313 actual LV1 root identity without fabricated prerequisite");
        check(node.x() + 8 - 18.6 >= 0 && node.x() + 8 + 18.6 <= 257
                && node.y() + 8 - 18.6 >= 0 && node.y() + 8 + 18.6 <= 139, "root full-hover ring fits original tree viewport");
        check(node.conditions().size() == 1, "root only shows developer tier condition");
        var tier = node.conditions().getFirst();
        check(tier.get("type").getAsString().equals("developer_type") && tier.get("minimum").getAsString().equals("portable"), "native portable tier displayed");
        int stimulations = ClassicRules.learningStimulations(node.level());
        check(stimulations == 3 && DeveloperType.PORTABLE.ticksPerStimulation() * stimulations == 78
                && DeveloperType.PORTABLE.actualConsumption(stimulations) == 2340, "finite native learning is three stimulations 78 ticks 2340IF");
        check(HighPowerDeveloperLayout.highPowerTitle("teleporter").equals("screen.academy.developer.page_tp_highpower"), "TP title is explicit");
        check(HighPowerDeveloperLayout.highPowerTitle("meltdowner").equals("screen.academy.developer.page_md_highpower"), "existing MD page title retained");
        retainedMeltdownerPageAfter253();
        for (String category : List.of("electromaster", "unsupported_test"))
            check(HighPowerDeveloperLayout.page(category, HighPowerDeveloperLayout.Page.HIGH_POWER).equals(ClassicDeveloperLayout.category(category)), "foreign tree unchanged " + category);

        var nav = new HighPowerDeveloperLayout.Navigation(); nav.synchronize("teleporter");
        var merged = UnifiedSkillTreeLayout.category("teleporter");
        check(merged.size()==ClassicDeveloperLayout.category("teleporter").size() && nav.selectIn(node.id(),merged),
                "TP root and all original nodes occupy the same selectable category tree");
        check(!nav.selectIn(HighPowerSkills.PHASE_SIDESTEP,merged), "open selection cannot be replaced");
        check(nav.beginLearning() && !nav.beginLearning() && nav.back() == HighPowerDeveloperLayout.Back.BUSY, "native learning remains correlated and finite");
        check(!nav.synchronize("teleporter") && nav.pending(), "matching snapshot preserves development attempt");
        nav.finishLearning();
        check(nav.back() == HighPowerDeveloperLayout.Back.SELECTION && nav.back() == HighPowerDeveloperLayout.Back.CLOSE_SCREEN,
                "Back dismisses selection then screen without an extra page");
        nav.selectIn(node.id(),merged); nav.beginLearning();
        check(nav.synchronize("meltdowner") && nav.selected().isEmpty() && !nav.pending(),
                "category replacement cannot retain TP learning ownership");

        check(Arrays.equals(resource("/assets/academy/textures/abilities/teleporter/skills/shift_tp.png"),
                resource("/assets/academy/textures/abilities/teleporter/skills/safe_landing_probe.png")), "NEW313 icon retains original pixels");
        for (int frame = 0; frame < 7; frame++) check(resource("/assets/academy/textures/effects/tp_mark/" + frame + ".png").length > 0, "original biped frame present");
        check(resource("/assets/academy/textures/effects/tp_particle.png").length > 0, "original particle present");
        String notice = new String(resource("/META-INF/ACADEMY-NOTICE"), StandardCharsets.UTF_8);
        check(notice.contains("NEW313 safe_landing_probe") && notice.contains("Lambda Innovation") && notice.contains("not captured player skins"), "original authorship and honest asset description retained");
        List<String> keys = List.of(node.nameKey(), node.descriptionKey(), "screen.academy.developer.page_tp_highpower",
                "academy.highpower.safe_landing_probe.safe", "academy.highpower.safe_landing_probe.marker_blocked",
                "academy.highpower.safe_landing_probe.blocked", "academy.highpower.safe_landing_probe.refused",
                "hud.academy.highpower.teleporter_shield_unavailable", "hud.academy.highpower.scheduled_rest_rate", "hud.academy.highpower.status.unlearned_tp");
        JsonObject english = JsonParser.parseString(new String(resource("/assets/academy/lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        var reasons = english.keySet().stream().filter(k -> k.startsWith("academy.highpower.safe_landing_probe.reason.")).toList();
        check(reasons.size() >= 55, "all current native/controller refusal reasons localized");
        for (String language : List.of("en_us", "zh_cn", "zh_tw", "ja_jp")) {
            var dictionary = JsonParser.parseString(new String(resource("/assets/academy/lang/" + language + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : java.util.stream.Stream.concat(keys.stream(), reasons.stream()).toList())
                check(dictionary.has(key) && !dictionary.get(key).getAsString().isBlank(), "nonblank NEW313 localization " + language + "/" + key);
            check(dictionary.get("academy.highpower.safe_landing_probe.safe").getAsString().contains("%s")
                    && dictionary.get("academy.highpower.safe_landing_probe.marker_blocked").getAsString().contains("%s"), "distance text retained in both marker states");
            check(dictionary.get("hud.academy.highpower.scheduled_rest_rate").getAsString().contains("%s"), "fractional rest-rate argument retained");
        }
        // Production routing/source guards are supplementary static evidence, not a runtime-render claim.
        String client = Files.readString(Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        String effects = Files.readString(Path.of("src/main/java/cn/academy/port/client/SafeLandingProbeEffects.java"));
        String screen = Files.readString(Path.of("src/main/java/cn/academy/port/client/ClassicDeveloperScreen.java"));
        check(client.contains("request(\"hp_safe_landing_probe\",press.identity().wire())") && client.contains("request(\"hp_safe_landing_probe_abort\",input.wire())"), "actual client uses only scoped NEW313 wire actions");
        check(client.contains("cancelSafeLandingProbeBeforeDiscard();cancelZeroGapArmorBeforeDiscard();discardBinding()"), "death cancellation retains NEW313 identity and preserves existing NEW252 ordering");
        check(!screen.contains("highPowerTitle(") && screen.contains("UnifiedSkillTreeLayout.category(AcademyClient.state.category)"), "merged tree: no separate high-power page or title is drawn");
        check(effects.contains("AcademyClient.safeLandingProbeLocalVisualLost(lost.input(), lost.epoch())")
                && effects.contains("AcademyClient.safeLandingProbeEndedInput(packet.input(), packet.epoch())"), "cosmetic loss and true server terminal use distinct callbacks");
        check(!effects.contains("ClassicTeleporterFinalEffects") && !effects.contains("setDeltaMovement") && !effects.contains("setPos(")
                && !effects.contains("playLocalSound") && !effects.contains("NO_DEPTH_TEST"), "render scope has no classic movement assist audio or depth bypass");
        check(effects.contains("setDepthTestState(LEQUAL_DEPTH_TEST)") && effects.contains("setWriteMaskState(COLOR_WRITE)"), "scoped material respects world depth without writing it");
        System.out.println("SafeLandingProbeLayoutRegressionTest: " + checks + " checks passed");
    }
}
