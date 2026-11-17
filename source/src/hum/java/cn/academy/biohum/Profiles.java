package cn.academy.biohum;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Stable named identities; ordinal positions are never written to saves. */
public final class Profiles {
 public record Accessory(String parent,String name,float[] box,float[] pivot,float[] rotation,int[] uv,float[] taper) {}
 public record Profile(String id,String name,String phase,boolean slim,float scale,float health,float attack,boolean esper,List<String> abilities,List<String> dialogue,String appearance,List<Accessory> accessories,int eggBase,int eggSpot) {}
 public static final Map<String,Profile> ALL=new LinkedHashMap<>();
 static {
  try(var in=Profiles.class.getResourceAsStream("/assets/acabiohum/characters.json")) {
   if(in==null)throw new IOException("Missing characters.json");
   for(Profile p:new Gson().fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),Profile[].class)) {
    if(ALL.putIfAbsent(p.id,p)!=null||p.accessories.size()>16||p.abilities.isEmpty()||p.scale<=0)throw new IOException("Invalid character "+p.id);
   }
  }catch(IOException e){throw new ExceptionInInitializerError(e);}
 }
 public static Profile get(String id){var p=ALL.get(id);if(p==null)throw new IllegalArgumentException("Unknown character "+id);return p;}
 private Profiles(){}
}
