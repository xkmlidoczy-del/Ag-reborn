package cn.academy.port.energy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Resource/source parity checks; no Minecraft bootstrap, registry or native server required. */
public final class ClassicEnergyDataRegressionTest {
    private static int assertions;
    private static final String SOURCE = "/cn/academy/port/energy/source/";
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("ene_unit", "academy:energy_unit"),
            Map.entry("cons_plate", "academy:constraint_plate"),
            Map.entry("crystal0", "academy:crystal_low"),
            Map.entry("crystal1", "academy:crystal_normal"),
            Map.entry("crystal2", "academy:crystal_pure"),
            Map.entry("data_chip", "academy:data_chip"),
            Map.entry("calc_chip", "academy:calc_chip"),
            Map.entry("reso_crystal", "academy:reso_crystal"),
            Map.entry("conv_comp", "academy:energy_convert_component"));

    private ClassicEnergyDataRegressionTest() {}

    private static void check(boolean value, String label) {
        assertions++;
        if (!value) throw new AssertionError(label);
    }

    private static InputStream resource(String name) {
        var stream = ClassicEnergyDataRegressionTest.class.getResourceAsStream(name);
        check(stream != null, "resource exists " + name);
        return stream;
    }

    private static JsonObject json(String name) throws Exception {
        try (var stream = resource(name); var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static byte[] bytes(String name) throws Exception {
        try (var stream = resource(name)) { return stream.readAllBytes(); }
    }

    private static String digest(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static String ingredient(JsonObject value) {
        check(value.size() == 1 && value.has("item"), "exact item ingredient, no substitute tag");
        return value.get("item").getAsString();
    }

    private static void recipes(JsonObject manifest) throws Exception {
        var sourceBytes = bytes(SOURCE + "default-energy.recipe");
        check(digest(sourceBytes).equals(manifest.get("fixture_sha256").getAsString()), "unchanged source recipe excerpt");
        var matcher = Pattern.compile("shaped\\(([^)]+)\\)\\s*\\{(.*?)\\}", Pattern.DOTALL)
                .matcher(new String(sourceBytes, StandardCharsets.UTF_8));
        String[] ids = {"energy_unit_18", "energy_unit_19", "energy_unit_20", "energy_convert_component_37"};
        int[] counts = {1, 2, 4, 1};
        var entries = manifest.getAsJsonArray("recipes");
        check(entries.size() == 4, "only the four source recipes in the scoped manifest");
        int index = 0;
        while (matcher.find()) {
            check(index < ids.length, "no invented source recipe");
            var output = matcher.group(1).split("\\*");
            int count = output.length == 2 ? Integer.parseInt(output[1].trim()) : 1;
            check(count == counts[index], "source yield 1/2/4 and converter 1");
            var entry = entries.get(index).getAsJsonObject();
            check(entry.get("id").getAsString().equals(ids[index]), "source ordinal recipe identity");
            var recipe = json("/data/academy/recipe/classic/" + ids[index] + ".json");
            check(recipe.size() == 5, "no unexplained recipe fields or condition shortcuts");
            String serializer = index < 3 ? "academy:energy_unit_shaped" : "minecraft:crafting_shaped";
            check(recipe.get("type").getAsString().equals(serializer), "native source-shaped recipe with bounded multi-yield serializer");
            check(recipe.get("category").getAsString().equals("misc"), "native 1.21.1 recipe category");
            var result = recipe.getAsJsonObject("result");
            check(result.size() == 2 && result.has("id") && result.has("count"), "1.21.1 result; no prefilled energy component");
            check(result.get("id").getAsString().equals(ALIASES.get(output[0].trim())), "exact source result item");
            check(result.get("count").getAsInt() == count, "exact source recipe yield");
            var sourceRows = new ArrayList<String[]>();
            var rows = Pattern.compile("\\[([^]]*)]").matcher(matcher.group(2));
            while (rows.find()) {
                var cells = rows.group(1).split(",", -1);
                for (int i = 0; i < cells.length; i++) cells[i] = cells[i].trim();
                sourceRows.add(cells);
            }
            var pattern = recipe.getAsJsonArray("pattern");
            var key = recipe.getAsJsonObject("key");
            check(pattern.size() == sourceRows.size(), "exact source grid height");
            var used = new HashSet<String>();
            for (int y = 0; y < sourceRows.size(); y++) {
                var actual = pattern.get(y).getAsString();
                var expected = sourceRows.get(y);
                check(actual.length() == expected.length, "exact source grid width");
                for (int x = 0; x < expected.length; x++) {
                    char symbol = actual.charAt(x);
                    if (expected[x].equals("nil")) {
                        check(symbol == ' ', "source empty crafting cell");
                    } else {
                        check(symbol != ' ' && key.has(String.valueOf(symbol)), "source ingredient cell exists");
                        used.add(String.valueOf(symbol));
                        check(ingredient(key.getAsJsonObject(String.valueOf(symbol))).equals(ALIASES.get(expected[x])),
                                "exact source ingredient " + ids[index] + " at " + x + "," + y);
                    }
                }
            }
            check(used.size() == key.size(), "no unused or invented recipe ingredient");
            index++;
        }
        check(index == 4, "three source unit recipes plus source converter recipe");
        check(json("/data/academy/recipe/classic/energy_unit_18.json").getAsJsonObject("result").get("count").getAsInt() == 1,
                "no low-crystal upgrading shortcut replaces native unit recipe");
    }

    private static String overriddenModel(JsonObject base, int level) {
        String model = base.get("parent").getAsString();
        for (var value : base.getAsJsonArray("overrides")) {
            var override = value.getAsJsonObject();
            if (level >= override.getAsJsonObject("predicate").get("academy:energy_unit_level").getAsInt())
                model = override.get("model").getAsString();
        }
        return model;
    }

    private static void icons(JsonObject manifest) throws Exception {
        var atlas = json("/assets/minecraft/atlases/blocks.json").getAsJsonArray("sources");
        boolean pluralItems = false;
        for (var value : atlas) {
            var source = value.getAsJsonObject();
            if (source.get("type").getAsString().equals("minecraft:directory")
                    && source.get("source").getAsString().equals("items")
                    && source.get("prefix").getAsString().equals("items/")) pluralItems = true;
        }
        check(pluralItems, "actual shared atlas includes classic plural items directory");
        check(manifest.getAsJsonArray("textures").size() == 3, "exact three legacy energy unit icons");
        for (var value : manifest.getAsJsonArray("textures")) {
            var entry = value.getAsJsonObject();
            byte[] png = bytes("/" + entry.get("target").getAsString());
            check(digest(png).equals(entry.get("sha256").getAsString()), "original source PNG bytes");
            check(png.length > 24 && png[0] == (byte) 137 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G', "valid original PNG header");
        }
        for (var tier : List.of("empty", "half", "full")) {
            var model = json("/assets/academy/models/item/energy_unit_" + tier + ".json");
            check(model.size() == 2 && model.get("parent").getAsString().equals("minecraft:item/generated"), "native generated source icon model");
            check(model.getAsJsonObject("textures").size() == 1, "one original icon layer");
            check(model.getAsJsonObject("textures").get("layer0").getAsString().equals("academy:items/energy_unit_" + tier), "exact source sprite identity");
        }
        var base = json("/assets/academy/models/item/energy_unit.json");
        check(base.size() == 2 && base.get("parent").getAsString().equals("academy:item/energy_unit_empty"), "uninitialized/empty unit uses source empty icon");
        var overrides = base.getAsJsonArray("overrides");
        check(overrides.size() == 2, "half and full icon transitions only");
        for (int i = 0; i < overrides.size(); i++) {
            var override = overrides.get(i).getAsJsonObject();
            check(override.size() == 2 && override.getAsJsonObject("predicate").size() == 1, "one bounded level predicate");
            check(override.getAsJsonObject("predicate").get("academy:energy_unit_level").getAsInt() == i + 1, "ascending level 1 then 2");
        }
        var levels = manifest.getAsJsonObject("model_levels");
        check(levels.get("empty").getAsInt() == 0 && levels.get("half").getAsInt() == 1 && levels.get("full").getAsInt() == 2,
                "registered property contract empty=0 half=1 full=2");
        check(manifest.get("legacy_max_damage").getAsInt() == 13, "source max damage=13");
        for (int damage = 0; damage <= 13; damage++) {
            String tier = damage < 3 ? "full" : damage > 10 ? "empty" : "half";
            check(overriddenModel(base, levels.get(tier).getAsInt()).equals("academy:item/energy_unit_" + tier),
                    "legacy damage icon for damage " + damage);
        }
        check(manifest.get("legacy_capacity_if").getAsInt() == 10000 && manifest.get("legacy_bandwidth_if").getAsInt() == 20,
                "exact source capacity/bandwidth manifest");
        var fluid = manifest.getAsJsonObject("fluid_dependency");
        check(fluid.get("id").getAsString().equals("imagProj") && fluid.get("amount_mb").getAsInt() == 1000,
                "source fluid registration recorded as dependency, no invented direct interaction");
    }

    public static void main(String[] args) throws Exception {
        var manifest = json(SOURCE + "asset-source-manifest.json");
        check(manifest.get("source_version").getAsString().equals("AcademyCraft-1.0.7"), "canonical source version");
        check(manifest.get("model_property").getAsString().equals("academy:energy_unit_level"), "client property identity");
        recipes(manifest);
        icons(manifest);
        System.out.println("PASS " + assertions + " classic energy resource/source parity assertions");
    }
}
