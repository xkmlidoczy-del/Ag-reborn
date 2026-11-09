package cn.academy.port.skill;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
/** Immutable noticed classpath fixtures, independent of the private .reference checkout. */
public final class VectorFinalSourceFixtures {
 public static final String ROOT="/classic-oracles/vector-final/";
 private static final Map<String,String> HASHES=load();private VectorFinalSourceFixtures(){}
 private static String hash(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
 private static byte[] raw(String name)throws Exception{try(var in=VectorFinalSourceFixtures.class.getResourceAsStream(ROOT+name)){if(in==null)throw new AssertionError("Missing pinned fixture "+name);return in.readAllBytes();}}
 private static Map<String,String> load(){try{byte[] b=raw("source-manifest.json");if(!hash(b).equals("6843d48da059abd22c0492a0cfd7dbfd05c0e3ed8c4c81d8fb2f3e5d57656a7c"))throw new AssertionError("Altered fixture manifest");var map=new HashMap<String,String>();for(var e:JsonParser.parseString(new String(b,StandardCharsets.UTF_8)).getAsJsonArray()){var o=e.getAsJsonObject();map.put(o.get("resource").getAsString(),o.get("sha256").getAsString());}return Map.copyOf(map);}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 public static byte[] bytes(String name)throws Exception{byte[] b=raw(name);String expected=HASHES.get(name);if(expected==null||!hash(b).equals(expected))throw new AssertionError("Altered or unlisted source "+name);return b;}
 public static String source(String name)throws Exception{return new String(bytes(name),StandardCharsets.UTF_8);}public static int verifyAll()throws Exception{for(var name:HASHES.keySet())bytes(name);return HASHES.size();}
}
