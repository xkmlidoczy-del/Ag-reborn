package cn.academy.port.skill;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
/** Pinned original classpath texts, never excluded reference/staging runtime paths. */
public final class LateCombatSourceFixtures {
 public static final String ROOT="/classic-oracles/meltdowner-late-combat/";
 private static final Map<String,String> HASHES=load();private LateCombatSourceFixtures(){}
 private static String hash(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
 private static byte[] bytes(String path)throws Exception{try(var in=LateCombatSourceFixtures.class.getResourceAsStream(ROOT+path)){if(in==null)throw new AssertionError("Missing pinned source fixture "+path);return in.readAllBytes();}}
 private static Map<String,String> load(){try{byte[] raw=bytes("source-manifest.json");if(!hash(raw).equals("ce44a3030334688c5cb9cad7716c7d00bc5258851957ab72ba5f7c4d83f7bd86"))throw new AssertionError("Altered pinned fixture manifest");var map=new HashMap<String,String>();for(var e:JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonArray()){var o=e.getAsJsonObject();map.put(o.get("resource").getAsString(),o.get("sha256").getAsString());}return Map.copyOf(map);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 public static String source(String path)throws Exception{String expected=HASHES.get(path);if(expected==null)throw new AssertionError("Unlisted fixture "+path);byte[] b=bytes(path);if(b.length==0||!hash(b).equals(expected))throw new AssertionError("Altered original fixture "+path);return new String(b,StandardCharsets.UTF_8);}
 public static int verifyAll()throws Exception{for(String name:HASHES.keySet())source(name);return HASHES.size();}
}
