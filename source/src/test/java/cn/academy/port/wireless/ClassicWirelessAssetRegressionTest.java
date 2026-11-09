package cn.academy.port.wireless;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Portable classpath-based original resource identity and actual native blockstate variants. */
public final class ClassicWirelessAssetRegressionTest {
    private static int assertions;
    private static void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    private static byte[] resource(String path)throws Exception{try(var stream=ClassicWirelessAssetRegressionTest.class.getResourceAsStream('/'+path)){if(stream==null)throw new AssertionError("Missing "+path);return stream.readAllBytes();}}
    private static com.google.gson.JsonObject object(String path)throws Exception{return JsonParser.parseString(new String(resource(path),StandardCharsets.UTF_8)).getAsJsonObject();}
    public static void main(String[] args)throws Exception{
        var manifest=JsonParser.parseString(new String(resource("cn/academy/port/wireless/original-assets.json"),StandardCharsets.UTF_8)).getAsJsonArray();for(var entry:manifest){var original=entry.getAsJsonObject();String name=original.get("asset").getAsString();check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource("assets/academy/"+name))).equals(original.get("sha256").getAsString()),"canonical originalbytes "+name);}
        for(String tier:new String[]{"basic","standard","advanced"}){var variants=object("assets/academy/blockstates/wireless_node_"+tier+".json").getAsJsonObject("variants");check(variants.size()==10,"all2×5native states "+tier);for(boolean connected:new boolean[]{false,true})for(int level=0;level<5;level++){var reference=variants.getAsJsonObject("connected="+connected+",energy_level="+level).get("model").getAsString();var model=object("assets/academy/models/"+reference.substring(reference.indexOf(':')+1)+".json").getAsJsonObject("textures");for(String side:new String[]{"up","down"})check(model.get(side).getAsString().equals("academy:blocks/node_top_"+(connected?1:0)),"native source top/bottom "+tier);for(String side:new String[]{"north","south","east","west"})check(model.get(side).getAsString().equals("academy:blocks/node_"+tier+"_side_"+level),"native source energy-side "+tier);}}
        System.out.println("ClassicWirelessAssetRegressionTest: "+assertions+" assertions passed;43canonical resources and30native node variants");
    }
}
