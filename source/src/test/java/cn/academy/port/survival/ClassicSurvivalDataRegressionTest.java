package cn.academy.port.survival;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Deterministic source parity without Minecraft bootstrap or a native server. */
public final class ClassicSurvivalDataRegressionTest {
    private static int assertions;
    private static void check(boolean value, String label) {
        assertions++;
        if (!value) throw new AssertionError(label);
    }
    private static JsonObject json(String name) {
        var stream = ClassicSurvivalDataRegressionTest.class.getResourceAsStream(name);
        check(stream != null, "resource exists " + name);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static final Map<String,String> ALIASES = Map.ofEntries(
            Map.entry("imagsil_ingot","academy:imag_silicon_ingot"), Map.entry("wafer","academy:wafer"), Map.entry("si_piece","academy:imag_silicon_piece"),
            Map.entry("data_chip","academy:data_chip"), Map.entry("calc_chip","academy:calc_chip"),
            Map.entry("reso_crystal","academy:reso_crystal"), Map.entry("cons_ingot","academy:constraint_ingot"),
            Map.entry("cons_plate","academy:constraint_plate"), Map.entry("crystal0","academy:crystal_low"),
            Map.entry("brain_comp","academy:brain_component"), Map.entry("info_comp","academy:info_component"),
            Map.entry("conv_comp","academy:energy_convert_component"), Map.entry("plateIron","#c:plates/iron"),
            Map.entry("ingotIron","#c:ingots/iron"), Map.entry("dustRedstone","#c:dusts/redstone"),
            Map.entry("dustGlowstone","#c:dusts/glowstone"), Map.entry("nuggetGold","#c:nuggets/gold"));
    private static String ingredient(JsonObject json) {
        check(json.size() == 1 && (json.has("item") || json.has("tag")), "1.21.1 ingredient schema");
        return json.has("tag") ? "#" + json.get("tag").getAsString() : json.get("item").getAsString();
    }
    private static String expected(String key) { return ALIASES.getOrDefault(key, "minecraft:" + key); }
    private static void shaped(String id, String output, int count, String... rows) {
        var recipe=json("/data/academy/recipe/classic/"+id+".json");
        check(recipe.get("type").getAsString().equals("minecraft:crafting_shaped"), id+" type");
        check(recipe.getAsJsonObject("result").get("id").getAsString().equals("academy:"+output),id+" output");
        check(recipe.getAsJsonObject("result").get("count").getAsInt()==count,id+" yield");
        var pattern=recipe.getAsJsonArray("pattern");
        check(pattern.size()==rows.length,id+" exact source height");
        for(int y=0;y<rows.length;y++) {
            var source=rows[y].split(",",-1);var actual=pattern.get(y).getAsString();
            check(actual.length()==source.length,id+" exact source width");
            for(int x=0;x<source.length;x++) {
                char symbol=actual.charAt(x);
                if(source[x].isEmpty()) check(symbol==' ',id+" empty cell");
                else check(symbol!=' ' && ingredient(recipe.getAsJsonObject("key").getAsJsonObject(""+symbol)).equals(expected(source[x])),id+" exact ingredient "+x+","+y);
            }
        }
    }
    private static void shapeless(String id,String output,int count,String... ingredients) {
        var recipe=json("/data/academy/recipe/classic/"+id+".json");
        check(recipe.get("type").getAsString().equals("minecraft:crafting_shapeless"),id+" type");
        check(recipe.getAsJsonObject("result").get("id").getAsString().equals("academy:"+output),id+" output");
        check(recipe.getAsJsonObject("result").get("count").getAsInt()==count,id+" yield");
        var actual=recipe.getAsJsonArray("ingredients");check(actual.size()==ingredients.length,id+" count");
        for(int i=0;i<ingredients.length;i++) check(ingredient(actual.get(i).getAsJsonObject()).equals(expected(ingredients[i])),id+" ingredient "+i);
    }
    public static void main(String[] args) {
        var atlas=json("/assets/minecraft/atlases/blocks.json").getAsJsonArray("sources");
        var sources=new java.util.HashSet<String>();
        for(var entry:atlas) {
            var source=entry.getAsJsonObject();
            if(source.get("type").getAsString().equals("minecraft:directory"))
                sources.add(source.get("source").getAsString()+":"+source.get("prefix").getAsString());
        }
        check(sources.contains("items:items/") && sources.contains("blocks:blocks/"),"actual shared blocks atlas includes both classic plural directories");
        String[] materialNames={"crystal_low","crystal_normal","crystal_pure","calc_chip","data_chip","wafer","constraint_ingot","imag_silicon_ingot","reinforced_iron_plate","imag_silicon_piece","reso_crystal","constraint_plate","brain_component","info_component","resonance_component","energy_convert_component"};
        for(var material:materialNames) {
            var model=json("/assets/academy/models/item/"+material+".json");
            String sprite=model.getAsJsonObject("textures").get("layer0").getAsString();
            check(sprite.equals("academy:items/"+material),"source material icon identity");
            check(ClassicSurvivalDataRegressionTest.class.getResource("/assets/academy/textures/items/"+material+".png")!=null,"runtime classic material PNG");
            check(sources.contains("items:items/"),"material icon actually has atlas coverage");
        }
        for(var block:new String[]{"machine_frame","reso_crystal_ore","constraint_metal_ore","crystal_ore","imag_silicon_ore"}) {
            var model=json("/assets/academy/models/block/"+block+".json");
            check(model.get("parent").getAsString().equals("minecraft:block/cube_all"),"modern full-cube block model");
            check(model.getAsJsonObject("textures").get("all").getAsString().equals("academy:blocks/"+block),"source ore/frame texture identity");
            check(ClassicSurvivalDataRegressionTest.class.getResource("/assets/academy/textures/blocks/"+block+".png")!=null,"runtime classic ore/frame PNG");
            check(sources.contains("blocks:blocks/"),"block sprite actually has atlas coverage");
            check(json("/assets/academy/blockstates/"+block+".json").getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString().equals("academy:block/"+block),"blockstate resolves the staged native model");
            check(json("/assets/academy/models/item/"+block+".json").get("parent").getAsString().equals("academy:block/"+block),"block item inherits native block model");
        }
        check(ClassicOreRules.ORES.size()==4,"four classic ores");
        int[] attempts={18,24,48,22}, sizes={4,4,3,4}, levels={2,1,2,2};
        for(int i=0;i<4;i++) {
            var ore=ClassicOreRules.ORES.get(i);
            check(ore.attempts()==attempts[i] && ore.veinSize()==sizes[i] && ore.harvestLevel()==levels[i],"canonical generation/mining tuple");
            var config=json("/data/academy/worldgen/configured_feature/"+ore.id()+".json").getAsJsonObject("config");
            check(config.get("size").getAsInt()==sizes[i],"source vein size");
            check(config.get("discard_chance_on_air_exposure").getAsDouble()==0,"no unrequested exposure suppression");
            var target=config.getAsJsonArray("targets").get(0).getAsJsonObject();
            check(target.getAsJsonObject("target").get("predicate_type").getAsString().equals("minecraft:block_match"),"exact block target");
            check(target.getAsJsonObject("target").get("block").getAsString().equals("minecraft:stone"),"stone-only target");
            check(target.getAsJsonObject("state").get("Name").getAsString().equals("academy:"+ore.id()),"ore target state");
            var modifiers=json("/data/academy/worldgen/placed_feature/"+ore.id()+".json").getAsJsonArray("placement");
            check(modifiers.size()==5,"complete placement pipeline");
            check(modifiers.get(0).getAsJsonObject().get("type").getAsString().equals("academy:classic_overworld"),"runtime config and dimension gate");
            check(modifiers.get(1).getAsJsonObject().get("count").getAsInt()==attempts[i],"attempts per chunk");
            var range=modifiers.get(3).getAsJsonObject().getAsJsonObject("height");
            check(range.getAsJsonObject("min_inclusive").get("absolute").getAsInt()==0,"minimum start height");
            check(range.getAsJsonObject("max_inclusive").get("absolute").getAsInt()==59,"EXCLUSIVE 60 source upper boundary");
            var loot=json("/data/academy/loot_table/blocks/"+ore.id()+".json");
            var entry=loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
            if(ore.drop().equals(ore.id())) check(entry.get("name").getAsString().equals("academy:"+ore.id()) && !entry.has("functions"),"self ores always one and do not gain Fortune");
            else {
                var children=entry.getAsJsonArray("children");check(children.size()==2,"Silk Touch alternative");
                check(children.get(0).getAsJsonObject().get("name").getAsString().equals("academy:"+ore.id()),"Silk Touch ore identity");
                var normal=children.get(1).getAsJsonObject();check(normal.get("name").getAsString().equals("academy:"+ore.drop()),"classic drop item");
                if(ore.maximumDrop()>1) {
                    var count=normal.getAsJsonArray("functions").get(0).getAsJsonObject().getAsJsonObject("count");
                    check(count.get("min").getAsInt()==1 && count.get("max").getAsInt()==2,"RandUtils exclusive upper bound converted to inclusive1..2");
                } else check(normal.getAsJsonArray("functions").size()==2,"resonant base drop exactly one");
                check(normal.toString().contains("minecraft:ore_drops"),"inherited classic Fortune formula");
            }
        }
        check(ClassicOreRules.canGenerate(true,true),"default Overworld generation");
        check(!ClassicOreRules.canGenerate(false,true) && !ClassicOreRules.canGenerate(true,false),"config and dimension rejection");
        shapeless("imag_silicon_piece_01","imag_silicon_piece",2,"wafer");
        shaped("data_chip_02","data_chip",1,"dustRedstone,dustRedstone,dustRedstone",",plateIron,");
        shaped("data_chip_03","data_chip",1,"dustRedstone,dustRedstone,dustRedstone",",si_piece,");
        shapeless("calc_chip_04","calc_chip",1,"data_chip","quartz","quartz");
        shapeless("calc_chip_05","calc_chip",1,"data_chip","reso_crystal");
        shaped("reinforced_iron_plate_06","reinforced_iron_plate",2,"ingotIron","ingotIron","ingotIron");
        shaped("machine_frame_07","machine_frame",1,",plateIron,","plateIron,dustRedstone,plateIron",",plateIron,");
        shaped("constraint_plate_21","constraint_plate",2,"cons_ingot,cons_ingot,cons_ingot");
        shaped("info_component_34","info_component",1,"dustGlowstone","data_chip");
        shaped("brain_component_35","brain_component",1,",nuggetGold,","dustRedstone,calc_chip,dustRedstone",",nuggetGold,");
        shaped("resonance_component_36","resonance_component",1,"cons_plate,reso_crystal,cons_plate",",dustRedstone,");
        shaped("portable_developer_42","portable_developer",1,"data_chip,glass_pane,calc_chip","brain_comp,info_comp,conv_comp","cons_plate,crystal0,cons_plate");
        shapeless("wafer_47","wafer",1,"imagsil_ingot");
        shaped("magnetic_coil_49","magnetic_coil",1,"cons_plate,reso_crystal,cons_plate","cons_plate,reso_crystal,cons_plate","plateIron,diamond,plateIron");
        for(var rule:List.of(new String[]{"imag_silicon_ingot_31","imag_silicon_ore","imag_silicon_ingot","0.8"},new String[]{"constraint_ingot_32","constraint_metal_ore","constraint_ingot","0.7"},new String[]{"crystal_low_33","crystal_ore","crystal_low","0.8"})) {
            var recipe=json("/data/academy/recipe/classic/"+rule[0]+".json");
            check(recipe.get("type").getAsString().equals("minecraft:smelting"),"smelting only, no invented blasting");
            check(ingredient(recipe.getAsJsonObject("ingredient")).equals("academy:"+rule[1]),"exact ore smelting ingredient");
            check(recipe.getAsJsonObject("result").get("id").getAsString().equals("academy:"+rule[2]),"smelting output");
            check(recipe.getAsJsonObject("result").get("count").getAsInt()==1 && recipe.get("experience").getAsDouble()==Double.parseDouble(rule[3]),"exact smelting yield and XP");
            check(recipe.get("cookingtime").getAsInt()==200,"vanilla furnace time");
        }
        System.out.println("PASS "+assertions+" classic survival resource/rule parity assertions");
    }
}
