package cn.academy.port.fusion.phase;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Staged/native datapack identity checks; Gson only, no Minecraft bootstrap. */
public final class PhaseLiquidWorldgenDataRegressionTest {
    private static int assertions;
    private static void check(boolean condition, String label) {
        ++assertions;
        if (!condition) throw new AssertionError(label);
    }
    private static JsonObject json(String path) {
        var stream = PhaseLiquidWorldgenDataRegressionTest.class.getResourceAsStream(path);
        check(stream != null, "phase worldgen resource exists: " + path);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    public static void main(String[] args) {
        var configured = json("/data/academy/worldgen/configured_feature/phase_liquid.json");
        check(configured.size() == 2, "no extra feature configuration scope");
        check(configured.get("type").getAsString().equals("academy:phase_liquid"), "native feature registry identity");
        check(configured.getAsJsonObject("config").isEmpty(), "NoneFeatureConfiguration");
        var placed = json("/data/academy/worldgen/placed_feature/phase_liquid.json");
        check(placed.size() == 2, "placed feature schema");
        check(placed.get("feature").getAsString().equals("academy:phase_liquid"), "configured feature identity");
        check(placed.getAsJsonArray("placement").isEmpty(), "no extra chance/count/range/biome modifiers or random draws");
        var modifier = json("/data/academy/neoforge/biome_modifier/phase_liquid.json");
        check(modifier.size() == 4, "native biome modifier schema");
        check(modifier.get("type").getAsString().equals("neoforge:add_features"), "native add_features serializer");
        check(modifier.getAsJsonObject("biomes").size() == 1
                && modifier.getAsJsonObject("biomes").get("type").getAsString().equals("neoforge:any"), "all registered biomes, dimension gate lives in Feature");
        check(modifier.getAsJsonArray("features").size() == 1
                && modifier.getAsJsonArray("features").get(0).getAsString().equals("academy:phase_liquid"), "one phase placed feature");
        check(modifier.get("step").getAsString().equals("underground_ores"), "declared native underground decoration step");
        System.out.println("PASS " + assertions + " phase-liquid worldgen datapack assertions");
    }
}
