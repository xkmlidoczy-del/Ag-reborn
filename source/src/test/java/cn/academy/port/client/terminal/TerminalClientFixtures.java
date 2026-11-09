package cn.academy.port.client.terminal;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
final class TerminalClientFixtures {
    static int assertions;
    static byte[] bytes(String name)throws Exception{try(var in=TerminalClientFixtures.class.getResourceAsStream("/classic-terminal-client/"+name)){if(in==null)throw new AssertionError("Missing immutable fixture "+name);return in.readAllBytes();}}
    static String text(String name)throws Exception{return new String(bytes(name),StandardCharsets.UTF_8);}
    static String sha(byte[] bytes)throws Exception{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    static void equal(double a,double b,double epsilon,String message){check(Math.abs(a-b)<=epsilon,message+" "+a+" != "+b);}
    static void witnesses()throws Exception{check(sha(bytes("source-manifest.json")).equals("e283c9e59d387eed387f1f63b268a1d41fa267aa36b65ff2fab2d5f315d642da"),"Pinned source manifest");check(sha(bytes("asset-manifest.json")).equals("f119a795edaf7060f1972886367954c979b555ed82c6747fa050d3d6d07fc1df"),"Pinned safe asset manifest");for(var entry:JsonParser.parseString(text("source-manifest.json")).getAsJsonArray()){var e=entry.getAsJsonObject();check(sha(bytes(e.get("name").getAsString())).equals(e.get("sha256").getAsString()),"Modified source witness "+e.get("name"));}}
    static Path root(){return Path.of(System.getProperty("academy.terminal.root",".")).toAbsolutePath();}
    static String production(String path)throws Exception{return Files.readString(root().resolve(path));}
}
