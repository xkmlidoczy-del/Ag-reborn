package cn.academy.port.core;
import java.util.*;

/** Distinct startup/reload/alias contracts from immutable original Skill/Category/Preset witnesses. */
public final class ClassicSkillConfigurationRegressionTest {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args){
        var arc=ClassicSkillConfiguration.key("electromaster","arc_gen");var charging=ClassicSkillConfiguration.key("electromaster","charging");
        var movement=ClassicSkillConfiguration.key("electromaster","mag_movement");var brain=ClassicSkillConfiguration.key("electromaster","brain_course");
        var keys=List.of(arc,charging,movement,brain);var disabled=new ClassicSkillConfiguration.Flags(false,false);
        var config=new ClassicSkillConfiguration(keys,Map.of(arc,disabled));
        check(!config.enabled("electromaster","arc_gen")&&config.enabled("electromaster","charging"),"one disabled object leaves sibling defaults true");
        check(config.parent("electromaster","arc_gen")==null,"disabled source parent produces true root");
        var requirements=List.of(new ClassicSkillConfiguration.Requirement("arc_gen",0),new ClassicSkillConfiguration.Requirement("charging",.7));
        check(config.requirements("electromaster",requirements).equals(List.of(requirements.get(1))),"only disabled initial dependency is skipped");
        config.replaceLive(Map.of(charging,disabled,brain,disabled));
        check(config.enabled("electromaster","arc_gen")&&!config.enabled("electromaster","charging"),"booleans refresh independently");
        check(config.parent("electromaster","arc_gen")==null&&config.requirements("electromaster",requirements).equals(List.of(requirements.get(1))),"reload neither restores skipped parent nor removes initially present extra dependency");
        for(String category:ClassicSkillConfiguration.CATEGORIES)check(!config.enabled(category,"brain_course"),"same generic object governs all four category instances");
        check(brain.sourcePath().equals("ac.ability.category.generic.skills.brain_course"),"exact source generic path");
        check(!config.newPresetSelection("electromaster","charging",true,true)&&!config.controllable("electromaster","brain_course",false),"new selection hides disabled/passive controls");
        check(!config.contextTerrain("electromaster","charging",true)&&!config.contextTerrain("electromaster","arc_gen",false),"local false survives world override; global denial still denies default local true");
        var learned=new HashSet<>(List.of("brain_course","charging"));config.replaceLive(Map.of(brain,disabled));
        check(learned.contains("brain_course")&&learned.contains("charging"),"configuration never erases saved learned/passive state");
        boolean unknown=false;try{config.enabled("electromaster","unknown");}catch(IllegalArgumentException expected){unknown=true;}check(unknown,"missing whole source object is not optional-property default");
        check(config.parent("electromaster",null)==null,"root parent lookup is safe");
        System.out.println("PASS "+checks+" source boolean aliases, startup graph/live reload, selection and local terrain core contracts");
    }
}
