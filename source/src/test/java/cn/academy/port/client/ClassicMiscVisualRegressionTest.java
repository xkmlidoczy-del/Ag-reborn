package cn.academy.port.client;
import cn.academy.port.display.ClassicCreativeCatalog;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;
import javax.imageio.ImageIO;
/** Byte-exact source bitmaps, locale/title/member contracts and native model layout. No client launched. */
public final class ClassicMiscVisualRegressionTest {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static String sha(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    private static JsonObject json(Path p)throws Exception{return JsonParser.parseString(Files.readString(p)).getAsJsonObject();}
    public static void main(String[] args)throws Exception{
        var root=Path.of(System.getProperty("academy.misc.stage","."));var original=root.resolve("src/test/resources/classic-misc-source/original/src/main/resources/assets/academy");var runtime=root.resolve("src/main/resources/assets/academy");
        for(var rel:List.of("textures/blocks/cat_engine.png","textures/items/logo.png","textures/null.png")){
            check(sha(original.resolve(rel)).equals(sha(runtime.resolve(rel))),"original bitmap bytes "+rel);var a=ImageIO.read(original.resolve(rel).toFile());var b=ImageIO.read(runtime.resolve(rel).toFile());check(a.getWidth()==b.getWidth()&&a.getHeight()==b.getHeight(),"original bitmap dimensions "+rel);for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)check(a.getRGB(x,y)==b.getRGB(x,y),"exact original RGBA pixel");
        }
        var nullImage=ImageIO.read(runtime.resolve("textures/null.png").toFile());for(int y=0;y<nullImage.getHeight();y++)for(int x=0;x<nullImage.getWidth();x++)check((nullImage.getRGB(x,y)>>>24)==0,"source default dummy transparent");
        var world=json(runtime.resolve("models/block/cat_engine.json"));check(world.getAsJsonArray("elements").isEmpty(),"source no world baked cube");check(world.getAsJsonObject("textures").get("particle").getAsString().equals("academy:blocks/cat_engine"),"source block particle");var item=json(runtime.resolve("models/item/cat_engine.json"));check(item.get("parent").getAsString().equals("minecraft:item/generated")&&item.getAsJsonObject("textures").get("layer0").getAsString().equals("academy:blocks/cat_engine"),"source non3D inventory flat cat icon");
        var logo=json(runtime.resolve("models/item/logo.json"));check(logo.getAsJsonObject("textures").get("layer0").getAsString().equals("academy:items/logo"),"source logo binding");var icon=json(runtime.resolve("models/item/achievement_icon.json"));check(icon.get("parent").getAsString().equals("builtin/entity"),"dynamic inventory renderer enabled");check(icon.getAsJsonObject("display").getAsJsonObject("gui").getAsJsonArray("scale").toString().equals("[1,1,1]"),"source16px inventory square");
        for(var locale:Map.of("en_US","en_us","zh_CN","zh_cn","zh_TW","zh_tw","ja_JP","ja_jp").entrySet()){
            var values=new HashMap<String,String>();for(var line:Files.readAllLines(original.resolve("lang/"+locale.getKey()+".lang"))){int equals=line.indexOf('=');if(equals>=0&&!line.startsWith("#"))values.put(line.substring(0,equals),line.substring(equals+1));}var modern=json(runtime.resolve("lang/"+locale.getValue()+".json"));for(var key:List.of("itemGroup.AcademyCraft","ac.cat_engine.unlink","ac.cat_engine.notfound","ac.cat_engine.linked"))check(values.get(key).equals(modern.get(key).getAsString()),"exact original locale "+locale+" "+key);check(values.get("tile.ac_cat_engine.name").equals(modern.get("block.academy.cat_engine").getAsString()),"original cat native name "+locale);
        }
        var source=root.resolve("src/test/resources/classic-misc-source/original/src/main/java");var tab=Files.readString(source.resolve("cn/academy/core/AcademyCraft.java"));check(tab.contains("new CreativeTabs(\"AcademyCraft\")")&&tab.contains("return logo;"),"original dedicated tab icon/title");var dummy=Files.readString(source.resolve("cn/academy/misc/achievements/ItemAchievement.java"));check(!dummy.contains("setCreativeTab")&&dummy.contains("list.add(Resources.getTexture(\"null\"))"),"source hidden index0 dynamic dummy");check(!ClassicCreativeCatalog.ITEMS.contains("logo")&&!ClassicCreativeCatalog.ITEMS.contains("achievement_icon"),"source hidden creative distinction");
        var energy=Files.readString(source.resolve("cn/academy/energy/template/ItemEnergyBase.java"));check(energy.contains("itemManager.charge(is, 0, true)")&&energy.contains("itemManager.charge(is, Double.MAX_VALUE, true)"),"source empty/full creative variants");var matrix=Files.readString(source.resolve("cn/academy/energy/item/ItemMatrixCore.java"));check(matrix.contains("int LEVELS = 3;")&&matrix.contains("new ItemStack(this, 1, i)"),"source three matrix variants");
        System.out.println("ClassicMiscVisualRegressionTest: "+checks+" original pixels/alpha/locales, source creative and resource-layout checks passed; actual native/client acceptance pending");
    }
}
