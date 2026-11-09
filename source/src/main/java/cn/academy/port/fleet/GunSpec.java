package cn.academy.port.fleet;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
/** Small shared authoritative event table, separate from client pose tracks. */
public final class GunSpec {
 public record Timing(int release,int tacticalInsert,int emptyInsert,int lock,int tacticalEnd,int emptyEnd){}
 private static final Timing[] VALUES=load();
 private static Timing[] load(){
  try(var in=GunSpec.class.getResourceAsStream("/assets/academy/spec/guns.json")){
   var json=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonArray();Timing[] out=new Timing[6];
   for(int i=0;i<6;i++){var j=json.get(i).getAsJsonObject();out[i]=new Timing(j.get("release").getAsInt(),j.get("tactical_insert").getAsInt(),j.get("empty_insert").getAsInt(),j.get("lock").getAsInt(),j.get("tactical_end").getAsInt(),j.get("empty_end").getAsInt());}
   return out;
  }catch(Exception ex){throw new IllegalStateException("Missing six-gun event table",ex);}
 }
 public static Timing timing(int variant){return VALUES[Math.max(0,Math.min(5,variant))];}
}
