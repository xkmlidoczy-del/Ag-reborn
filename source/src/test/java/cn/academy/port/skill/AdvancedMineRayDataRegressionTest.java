package cn.academy.port.skill;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
/** Packaged original media hashes and complete sound-event bindings. No original checkout dependency. */
public final class AdvancedMineRayDataRegressionTest {
    private static byte[] resource(String path)throws Exception {try(var input=AdvancedMineRayDataRegressionTest.class.getResourceAsStream("/"+path)){if(input==null)throw new AssertionError("Missing required original media fixture/resource "+path);return input.readAllBytes();}}
    public static void main(String[] args)throws Exception {
        var source=JsonParser.parseString(new String(resource("classic-oracles/advanced-mining/media-manifest.json"),StandardCharsets.UTF_8)).getAsJsonObject();int checks=0;
        for(var entry:source.getAsJsonArray("assets")) {
            var row=entry.getAsJsonObject();String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource(row.get("path").getAsString())));
            if(!actual.equals(row.get("sha256").getAsString()))throw new AssertionError("Original Expert/Luck media bytes changed: "+row.get("path"));checks++;
        }
        var sounds=JsonParser.parseString(new String(resource("assets/academy/sounds.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        for(var entry:source.getAsJsonArray("sound_events")){var row=entry.getAsJsonObject();if(!row.get("value").equals(sounds.get(row.get("event").getAsString())))throw new AssertionError("Original sound event changed: "+row.get("event"));checks++;}
        System.out.println("PASS "+checks+" unchanged Expert/Luck original media bytes and reused sound-event bindings");
    }
}
