package cn.academy.port.skill;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
/** Pinned noticed classpath source fixture integrity; no private reference path runtime. */
public final class VectorSourceFixtures {
 public static final String ROOT="/classic-oracles/vector-starters/";
 private static final Map<String,String> HASHES=load();private VectorSourceFixtures(){}
 private static String hash(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
 private static byte[] bytes(String name)throws Exception{try(var in=VectorSourceFixtures.class.getResourceAsStream(ROOT+name)){if(in==null)throw new AssertionError("Missing pinned fixture "+name);return in.readAllBytes();}}
 private static Map<String,String> load(){try{byte[] raw=bytes("source-manifest.json");if(!hash(raw).equals("d1eb0c34180a046302ffe289bc3989b31ab8311970fc83e0353e9f56a739cbff"))throw new AssertionError("Altered fixture manifest");var map=new HashMap<String,String>();for(var e:JsonParser.parseString(new String(raw,StandardCharsets.UTF_8)).getAsJsonArray()){var o=e.getAsJsonObject();map.put(o.get("resource").getAsString(),o.get("sha256").getAsString());}return Map.copyOf(map);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 public static String source(String name)throws Exception{String expected=HASHES.get(name);if(expected==null)throw new AssertionError("Unlisted source "+name);byte[] b=bytes(name);if(!hash(b).equals(expected))throw new AssertionError("Altered source "+name);return new String(b,StandardCharsets.UTF_8);}
 public static int verifyAll()throws Exception{for(var name:HASHES.keySet())source(name);return HASHES.size();}
}
