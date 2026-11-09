package cn.academy.port.skill;

import com.google.gson.JsonParser;
import cn.academy.port.testing.CheckpointSourceFixtures;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Actual canonical texture/audio and modern achievement structure, no Minecraft startup. */
public final class MineDetectDataRegressionTest {
    private static int n;
    private static void check(boolean yes,String text){n++;if(!yes)throw new AssertionError(text);}
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    public static void main(String[] args)throws Exception {
        String[] paths={"textures/effects/mineview.png","sounds/em/minedetect.ogg"};
        String[] hashes={"ac3125585f6f5f75e0b4a2d8633cd1b2117f574dc58efaa6887f496f1f3e4a0a","a7bed9635b389a4e77133f116aa6eb4edfbc5e43b6048c23e910369539796b7e"};
        var originals=JsonParser.parseString(CheckpointSourceFixtures.pinnedText("academycraft-1.0.7/mine-detect-original-assets.json")).getAsJsonObject().getAsJsonObject("assets");
        for(int i=0;i<paths.length;i++){
            byte[] asset=CheckpointSourceFixtures.resourceBytes("/assets/academy/"+paths[i]);
            check(hash(asset).equals(hashes[i]),"canonical original asset SHA256 "+paths[i]);
            var original=originals.getAsJsonObject(paths[i]);
            check(hash(asset).equals(original.get("sha256").getAsString())&&asset.length==original.get("bytes").getAsLong(),"byte-exact retained original asset SHA256/length audit "+paths[i]);
        }
        var sounds=JsonParser.parseString(CheckpointSourceFixtures.resourceText("/assets/academy/sounds.json")).getAsJsonObject();
        check(sounds.getAsJsonObject("em.minedetect").getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString().equals("academy:em/minedetect"),"native sound registration uses original file");
        var achievement=JsonParser.parseString(CheckpointSourceFixtures.resourceText("/data/academy/advancement/electromaster/mine_detect.json")).getAsJsonObject();
        check(achievement.getAsJsonObject("criteria").getAsJsonObject("cast").get("trigger").getAsString().equals("minecraft:impossible"),"only server successful skill grant criterion");
        var entry=cn.academy.port.achievements.ClassicAchievementCatalog.get("electromaster.mine_detect");
        check(entry.title().equals("achievement.ac_electromaster.mine_detect"),"original achievement title in source page catalog");
        check(entry.description().equals("achievement.ac_electromaster.mine_detect.desc"),"original achievement description in source page catalog");
        check(!achievement.has("display"),"native persistent record delegates source page and toast rendering");
        check(achievement.get("parent").getAsString().equals("academy:electromaster/body_intensify"),"original immediate parent retained");
        check(achievement.getAsJsonArray("requirements").size()==1&&achievement.getAsJsonArray("requirements").get(0).getAsJsonArray().get(0).getAsString().equals("cast"),"existing completion criterion retained");
        for(String locale:new String[]{"en_us","zh_cn"}){
            var lang=JsonParser.parseString(CheckpointSourceFixtures.resourceText("/assets/academy/lang/"+locale+".json")).getAsJsonObject();
            check(lang.has("ac.ability.electromaster.mine_detect.name")&&lang.has("achievement.ac_electromaster.mine_detect"),"existing original skill/achievement localization "+locale);
        }
        System.out.println("PASS "+n+" MineDetect canonical asset/advancement/localization assertions (no game startup)");
    }
}
