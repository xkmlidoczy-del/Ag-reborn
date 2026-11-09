package cn.academy.port.skill;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.google.gson.JsonParser;
public final class TeleporterSourceFixtures {
 private TeleporterSourceFixtures(){}public static String source(String project,String path){try(var in=TeleporterSourceFixtures.class.getResourceAsStream("/classic-teleporter-source/"+project+"/"+path)){if(in==null)throw new AssertionError("Missing restored-build canonical fixture "+path);return new String(in.readAllBytes(),StandardCharsets.UTF_8);}catch(Exception e){throw new AssertionError(e);}}
 public static String skill(String name){return source("AcademyCraft-1.0.7","src/main/scala/cn/academy/vanilla/teleporter/skill/"+name+".scala");}
 public static void verify(){try(var in=TeleporterSourceFixtures.class.getResourceAsStream("/classic-teleporter-source/manifest.json")){var manifest=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();if(!manifest.get("academycraft_commit").getAsString().equals("00d19ec0cf538f61c1095c9292f5ee6863db4521"))throw new AssertionError("Canonical commit mismatch");for(var element:manifest.getAsJsonArray("sources")){var item=element.getAsJsonObject();String text=source(item.get("project").getAsString(),item.get("path").getAsString());String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));if(!hash.equals(item.get("sha256").getAsString()))throw new AssertionError("Canonical fixture hash mismatch "+item);}}catch(Exception e){throw new AssertionError(e);}}
 public static void contains(String source,String text){if(!source.contains(text))throw new AssertionError("Pinned source contract missing: "+text);}
}
