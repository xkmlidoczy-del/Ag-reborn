/* AcademyCraft1.0.7 Skill boolean/startup dependency semantics. GPLv3; see NOTICE. */
package cn.academy.port.core;

import java.util.*;

/** Live booleans are distinct from relationships captured at category construction. Never owns learned data. */
public final class ClassicSkillConfiguration {
    public static final Set<String> GENERIC_SKILLS=Set.of("brain_course","brain_course_advanced","mind_course");
    public static final Set<String> CATEGORIES=Set.of("electromaster","meltdowner","teleporter","vecmanip","mental_out","aero_hand","float_dial","nitrogen_armor","dark_matter","hydro_hand","paper_psychokinesis","liquid_shadow");
    public record Key(String location,String skill){
        public Key{if(location==null||(!CATEGORIES.contains(location)&&!location.equals("generic"))||skill==null||!skill.matches("[a-z0-9_]+"))throw new IllegalArgumentException("canonical skill key required");}
        public String sourcePath(){return "ac.ability.category."+location+".skills."+skill;}
    }
    public record Flags(boolean enabled,boolean destroyBlocks){public static final Flags DEFAULT=new Flags(true,true);}
    public record Requirement(String id,double experience){public Requirement{if(id==null||!id.matches("[a-z0-9_]+")||!Double.isFinite(experience)||experience<0||experience>1)throw new IllegalArgumentException("finite source dependency required");}}
    private final Set<Key> registered;
    private final Map<Key,Boolean> startupEnabled;
    private volatile Map<Key,Flags> current;

    public static Key key(String category,String skill){return new Key(GENERIC_SKILLS.contains(skill)?"generic":category,skill);}
    public ClassicSkillConfiguration(Collection<Key> keys,Map<Key,Flags> initial){
        if(keys==null||initial==null)throw new IllegalArgumentException("complete source objects required");
        registered=Set.copyOf(keys);current=normalize(initial);var graph=new LinkedHashMap<Key,Boolean>();
        current.forEach((k,v)->graph.put(k,v.enabled));startupEnabled=Map.copyOf(graph);
    }
    private Map<Key,Flags> normalize(Map<Key,Flags> values){
        if(values.keySet().stream().anyMatch(k->!registered.contains(k)))throw new IllegalArgumentException("unregistered skill object");
        var result=new LinkedHashMap<Key,Flags>();for(var key:registered)result.put(key,Objects.requireNonNull(values.getOrDefault(key,Flags.DEFAULT)));return Map.copyOf(result);
    }
    /** Source ACConfig.updateConfig replaces getters without rebuilding parent/extra-condition lists. */
    public void replaceLive(Map<Key,Flags> values){current=normalize(Objects.requireNonNull(values));}
    public Map<Key,Flags> liveSnapshot(){return current;}
    public Map<Key,Boolean> startupSnapshot(){return startupEnabled;}
    private Key known(String category,String skill){var key=key(category,skill);if(!registered.contains(key))throw new IllegalArgumentException("missing source skill object: "+key.sourcePath());return key;}
    public boolean enabled(String category,String skill){return current.get(known(category,skill)).enabled;}
    public boolean destroyBlocks(String category,String skill){return current.get(known(category,skill)).destroyBlocks;}
    public boolean controllable(String category,String skill,boolean sourceControllable){return sourceControllable&&enabled(category,skill);}
    public boolean dependencyPresent(String category,String dependency){return startupEnabled.get(known(category,dependency));}
    public List<Requirement> requirements(String category,List<Requirement> source){return source.stream().filter(r->dependencyPresent(category,r.id)).toList();}
    public String parent(String category,String sourceParent){return sourceParent==null?null:dependencyPresent(category,sourceParent)?sourceParent:null;}
    /** Only new selection uses canControl. Original saved Preset mappings are not filtered or renumbered. */
    public boolean newPresetSelection(String category,String skill,boolean sourceControllable,boolean learned){return learned&&controllable(category,skill,sourceControllable);}
    /** AbilityContext local false cannot be overridden by the global dimension whitelist. Caller owns source exceptions. */
    public boolean contextTerrain(String category,String skill,boolean globalOrWorldAllowed){return destroyBlocks(category,skill)&&globalOrWorldAllowed;}
}
