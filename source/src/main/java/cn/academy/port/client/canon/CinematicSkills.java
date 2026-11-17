package cn.academy.port.client.canon;

import cn.academy.port.macro.MacroSkillRules;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Art direction inventory. It adds neither a target nor a radius to any skill. */
final class CinematicSkills {
    enum Role { FEATURE, SCAN, GUARD, FORM, TRANSIT, RAY, LIFT, DRIVE, FLOW, FIELD, LINK, PULSE }
    private static final Map<String,Role> ROLES=load();
    private CinematicSkills(){}
    static Role role(String skill){return ROLES.getOrDefault(skill,Role.PULSE);}

    private static Map<String,Role> load(){
        var result=new LinkedHashMap<String,Role>();
        try(var reader=new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                CinematicSkills.class.getResourceAsStream("/academy-cinematic-skills.tsv")),StandardCharsets.UTF_8))){
            for(String line;(line=reader.readLine())!=null;){
                if(line.isBlank()||line.startsWith("#"))continue;
                var fields=line.split("\t");
                if(fields.length!=2||MacroSkillRules.find(fields[0])==null||result.put(fields[0],Role.valueOf(fields[1]))!=null)
                    throw new IllegalArgumentException("Invalid cinematic skill profile: "+line);
            }
            if(result.size()!=MacroSkillRules.profiles().size())throw new IllegalArgumentException("Incomplete cinematic skill inventory");
            return Map.copyOf(result);
        }catch(IOException failure){throw new ExceptionInInitializerError(failure);}
    }
}
