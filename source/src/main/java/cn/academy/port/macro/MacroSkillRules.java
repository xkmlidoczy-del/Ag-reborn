package cn.academy.port.macro;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Packaged, explicit presentation inventory. No damage, cost, duration or progression values live here. */
public final class MacroSkillRules {
    public enum Shape { SELF, POINT, LINE, AREA }
    public record Profile(String category,String skill,int level,Shape shape,double radius) {
        public boolean ultimate(){return level==5;}
    }
    public static final int TARGETS_PER_TICK=64, BLOCKS_PER_CAST=512;
    public static final double MAX_RADIUS=64, MAX_SPAN=192;
    private static final Map<String,Profile> PROFILES=load();
    private MacroSkillRules(){}
    public static Profile find(String skill){int dot=skill==null?-1:skill.indexOf('.');return PROFILES.get(dot<0?skill:skill.substring(dot+1));}
    public static boolean ultimate(String skill){var p=find(skill);return p!=null&&p.ultimate();}
    public static Collection<Profile> profiles(){return PROFILES.values();}
    public static double radius(String skill){var p=find(skill);return p==null?0:p.radius();}
    /** Ordinary fields grow from 32 to their packaged maximum; existing larger ranges are never reduced. */
    public static double area(String skill,double mastery,double maximum){
        if(!ultimate(skill)||maximum>MAX_RADIUS)return maximum;
        return 32+(Math.max(32,maximum)-32)*Math.clamp(mastery,0,1);
    }
    public static boolean unlimited(String skill){var p=find(skill);return p!=null&&p.ultimate()&&p.shape()!=Shape.POINT;}
    private static Map<String,Profile> load(){
        var result=new LinkedHashMap<String,Profile>();
        try(var in=Objects.requireNonNull(MacroSkillRules.class.getResourceAsStream("/academy-macro-vfx.tsv"));
            var reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
            for(String line;(line=reader.readLine())!=null;){
                if(line.isBlank()||line.startsWith("#"))continue;var c=line.split("\\t");
                if(c.length!=5)throw new IOException("Malformed macro profile");
                var p=new Profile(c[0],c[1],Integer.parseInt(c[2]),Shape.valueOf(c[3]),Double.parseDouble(c[4]));
                if(p.level()<1||p.level()>5||p.radius()<32||p.radius()>MAX_RADIUS||result.put(p.skill(),p)!=null)throw new IOException("Invalid macro profile: "+p.skill());
            }
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
        return Collections.unmodifiableMap(result);
    }
}
