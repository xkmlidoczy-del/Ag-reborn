package cn.academy.port.former;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Independent canonical transformation/crafting declarations plus registered binding contracts. */
public final class ClassicMetalFormerDataRegressionTest {
    private static int checks;
    private static void yes(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static JsonObject json(Path path)throws Exception{return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}
    private static Path shared(Path stage,String path){var staged=stage.resolve("integration").resolve(path);return Files.exists(staged)?staged:stage.resolve(path);}
    public static void main(String[] args)throws Exception{
        Path root=Path.of(System.getProperty("user.dir"));Path stage=Path.of(System.getProperty("academy.former.stage",(Files.exists(root.resolve("src/main/java/cn/academy/port/former/ClassicMetalFormer.java"))?root:root.resolve(".staging/metal-former")).toString()));
        Path fixture=stage.resolve("src/test/resources/classic-metal-former-source");
        var expected=ClassicMetalFormerCanonicalRecipes.read(fixture);yes(expected.equals(ClassicMetalFormerRules.BUILT_INS),"all 21 actual canonical input/output/count/mode/order declarations");
        yes(ClassicMetalFormerCanonicalRecipes.conditionalMetals(fixture).equals(ClassicMetalFormerRules.CONDITIONAL_METALS),"all six original conditional metal dictionary names");
        for(var rule:expected){yes(rule.inputCount()>0&&rule.outputCount()>0&&rule.outputCount()<=64,"finite original yields "+rule);yes(!rule.input().contains("raw_")&&!rule.input().contains("deepslate"),"source ore block inputs remain stone-era identities");}
        String original=Files.readString(fixture.resolve("resources/assets/academy/recipes/default.recipe"));
        var declaration=Pattern.compile("shaped\\(metal_former\\)\\s*\\{([^}]+)\\}").matcher(original);yes(declaration.find(),"source crafting declaration present");
        var rowMatcher=Pattern.compile("\\[([^]]+)\\]").matcher(declaration.group(1));var rows=new ArrayList<List<String>>();while(rowMatcher.find())rows.add(Arrays.stream(rowMatcher.group(1).split(",")).map(String::trim).toList());
        Path resources=stage.resolve("src/main/resources");var recipe=json(resources.resolve("data/academy/recipe/classic/metal_former_26.json"));yes(recipe.get("type").getAsString().equals("minecraft:crafting_shaped"),"registered native shaped crafting");
        var aliases=Map.of("shears","minecraft:shears","calc_chip","academy:calc_chip","frame","academy:machine_frame","cons_plate","academy:constraint_plate","matter_unit","academy:matter_unit");
        var pattern=recipe.getAsJsonArray("pattern");yes(rows.size()==3&&pattern.size()==rows.size(),"original three crafting rows");
        for(int y=0;y<3;y++){String row=pattern.get(y).getAsString();yes(row.length()==rows.get(y).size(),"original full occupied/empty width");for(int x=0;x<row.length();x++){String alias=rows.get(y).get(x);char key=row.charAt(x);if(alias.equals("nil"))yes(key==' ',"source nil remains empty");else yes(recipe.getAsJsonObject("key").getAsJsonObject(String.valueOf(key)).get("item").getAsString().equals(aliases.get(alias)),"source cell ingredient "+x+","+y);}}
        yes(recipe.getAsJsonObject("result").get("id").getAsString().equals("academy:metal_former")&&recipe.getAsJsonObject("result").get("count").getAsInt()==1,"source machine yield one");
        var loot=json(resources.resolve("data/academy/loot_table/blocks/metal_former.json"));yes(loot.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString().equals("academy:metal_former"),"registered block self drop");
        for(String tag:List.of("mineable/pickaxe","needs_stone_tool")){var values=json(shared(stage,"src/main/resources/data/minecraft/tags/block/"+tag+".json")).getAsJsonArray("values");yes(values.asList().stream().anyMatch(e->e.getAsString().equals("academy:metal_former")),"source harvest level one/pickaxe "+tag);}
        String bindings=Files.readString(stage.resolve("src/main/java/cn/academy/port/former/ClassicMetalFormer.java"));
        for(String literal:List.of("BLOCKS.register(\"metal_former\"","ITEMS.registerSimpleBlockItem(BLOCK)","TILES.register(\"metal_former\"","MENUS.register(\"metal_former\"","Capabilities.ItemHandler.BLOCK","MachineDevelopers.IMAG_FLUX"))yes(bindings.contains(literal),"actual registered binding "+literal);
        String academy=Files.readString(shared(stage,"src/main/java/cn/academy/port/AcademyCraft.java"));yes(academy.contains("cn.academy.port.former.ClassicMetalFormer.register(bus)"),"real mod registration hook");
        String wireless=Files.readString(shared(stage,"src/main/java/cn/academy/port/wireless/ClassicWirelessProtocol.java"));yes(wireless.contains("request.action.equals(\"open_former\")")&&wireless.contains("menu.isFor(former)")&&wireless.contains("tile instanceof ClassicMetalFormerBlockEntity former"),"sender-owned open and return wireless protocol");
        String tile=Files.readString(stage.resolve("src/main/java/cn/academy/port/former/ClassicMetalFormerBlockEntity.java"));yes(tile.contains("tag.putDouble(\"energy\",energy)")&&tile.contains("tag.putInt(\"mode\",work.mode().ordinal())")&&tile.contains("ContainerHelper.saveAllItems"),"inventory finite IF and mode saved");
        yes(!tile.contains("tag.putInt(\"recipe\"")&&!tile.contains("tag.putInt(\"work\""),"transient recipe/work are not persisted");
        String menu=Files.readString(stage.resolve("src/main/java/cn/academy/port/former/ClassicMetalFormerMenu.java"));yes(menu.contains("player.containerMenu!=this")&&menu.contains("!tile.stillValid(player)")&&menu.contains("player.level().isClientSide"),"mode changes bound to live server menu/session/distance");
        System.out.println("ClassicMetalFormerDataRegressionTest: "+checks+" independent source/declaration/binding checks passed");
    }
}
