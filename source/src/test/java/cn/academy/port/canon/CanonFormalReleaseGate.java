package cn.academy.port.canon;

import cn.academy.port.SkillCatalog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Release admission is intentionally stricter than intermediate catalog integration. */
public final class CanonFormalReleaseGate {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static JsonObject language(String locale)throws Exception{
        try(var input=CanonFormalReleaseGate.class.getResourceAsStream("/assets/academy/lang/"+locale+".json")){
            require(input!=null,"language "+locale);
            return JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    public static void main(String[] args)throws Exception{
        require(CanonSkills.EXPERIMENTAL,"formal abilities enabled in release-gate JVM");
        Map<String,JsonObject> languages=new HashMap<>();
        for(String locale:List.of("zh_cn","en_us","zh_tw","ja_jp"))languages.put(locale,language(locale));
        Map<String,SkillCatalog.Skill> definitions=new HashMap<>();
        for(var skill:CanonSkills.ALL)require(definitions.put(skill.id(),skill)==null,"unique ID "+skill.id());
        require(CanonSkills.CATEGORIES.size()==8,"eight canon categories");
        for(String category:CanonSkills.CATEGORIES){
            var skills=CanonSkills.ALL.stream().filter(s->s.category().equals(category)).toList();
            require(skills.size()>=24,"formal category needs 24 real skills: "+category+" has "+skills.size());
            for(int level=1;level<=5;level++){final int l=level;require(skills.stream().anyMatch(s->s.level()==l),category+" LV"+level+" branch");}
            var names=new HashSet<String>();
            for(var skill:skills){
                require(skill.controllable(),"active skill "+skill.id());
                for(int sequence:new int[]{1,35,36,Integer.MAX_VALUE}){
                    var request=new cn.academy.port.highpower.HighPowerLearningProtocol.Request(skill.id(),sequence);
                    require(("00000000-0000-0000-0000-000000000000:"+request.wire()).length()<=64,"native learning message bound "+skill.id());
                    require(request.equals(cn.academy.port.highpower.HighPowerLearningProtocol.parse(request.wire())),"canonical machine learning roundtrip "+skill.id());
                }
                var design=CanonVfxDesign.find(skill.id());
                require(design!=null&&design.category().equals(category),"category-specific visual "+skill.id());
                require(CanonFormalReleaseGate.class.getResource("/assets/academy/textures/skill_icons_v2/"+category+"/"+skill.id()+".png")!=null,"icon "+skill.id());
                for(var entry:languages.entrySet()){
                    String prefix="ac.ability."+category+"."+skill.id();
                    require(entry.getValue().has(prefix+".name")&&entry.getValue().has(prefix+".desc"),entry.getKey()+" text "+skill.id());
                    require(entry.getValue().get(prefix+".desc").getAsString().length()>=12,"meaningful description "+entry.getKey()+" "+skill.id());
                }
                require(names.add(languages.get("zh_cn").get("ac.ability."+category+"."+skill.id()+".name").getAsString()),"distinct Chinese name "+skill.id());
                walk(skill,definitions,new HashSet<>());
            }
        }
        System.out.println("PASS formal catalog gate "+checks+" checks, "+CanonSkills.ALL.size()+" skills; runtime/visual/multiplayer receipts are separate release gates");
    }
    private static void walk(SkillCatalog.Skill child,Map<String,SkillCatalog.Skill> definitions,Set<String> path){
        require(path.add(child.id()),"acyclic prerequisite "+child.id());
        for(var requirement:child.requirements()){
            var parent=definitions.get(requirement.id());require(parent!=null,"known prerequisite "+child.id()+" -> "+requirement.id());
            require(parent.category().equals(child.category())&&parent.level()<=child.level(),"valid prerequisite tier "+child.id());
            walk(parent,definitions,new HashSet<>(path));
        }
    }
}
