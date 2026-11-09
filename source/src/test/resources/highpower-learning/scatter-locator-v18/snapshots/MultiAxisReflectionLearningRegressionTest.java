package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.client.HighPowerDeveloperLayout;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.develop.*;
import cn.academy.port.preset.PresetSkills;
import java.util.*;

public final class MultiAxisReflectionLearningRegressionTest {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static final class Battery implements DevelopmentProcess.Developer {
        double value=7040; public DeveloperType type(){return DeveloperType.ADVANCED;}
        public double energy(){return value;}public double maxEnergy(){return 10000;}
        public boolean tryPullEnergy(double amount){if(value<amount)return false;value-=amount;return true;}
    }
    public static void main(String[] args) {
        SkillCatalog.resetConfiguration();
        var skill=SkillCatalog.find("vecmanip",MultiAxisReflection.ID).orElseThrow();
        check(HighPowerSkills.ALL.size() == 51 && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.highpower.HighPowerSkills.ALL.get(49).id().equals("point_meltdown") && HighPowerSkills.ALL.get(48).id().equals("wind_pressure_lift")&&HighPowerSkills.ALL.get(47).equals(skill),"442 retains predecessor ordinal before sole443 append");
        check(skill.level()==4&&skill.controllable()&&skill.requirements().equals(List.of(new SkillCatalog.Requirement("vector_cage",0))),"LV4 actual441 prerequisite");
        var s=new AbilityProgress();s.changeCategoryClassic("vecmanip");s.setLevel(4);s.setActivateState(true);
        check(!SkillCatalog.canLearn(s,skill)&&!PresetSkills.edit(s,0,0,skill.id()),"missing parent/unlearned refuses");
        s.learn("vector_cage");s.setLevel(3);check(!SkillCatalog.canLearn(s,skill),"underlevel refuses");s.setLevel(4);s.levelExperience=.125;
        check(DevelopmentActions.canLearn(s,DeveloperType.ADVANCED,skill)&&!DevelopmentActions.canLearn(s,DeveloperType.NORMAL,skill),"actual advanced tier");
        var action=DevelopmentActions.skill(s,skill);check(action.stimulations()==11,"native eleven stimulations");
        var b=new Battery();var process=new DevelopmentProcess();process.start(b,action);
        for(int tick=0;tick<175;tick++)process.tick();check(!s.learned(skill.id())&&b.value==40,"cannot learn before last40IF");
        process.tick();check(s.learned(skill.id())&&b.value==0&&s.exp(skill.id())==0&&s.levelExperience==.125,"7040IF noXP/mastery grant");
        check(PresetSkills.edit(s,0,2,skill.id()),"real learned binding");
        var saved=AbilityStorage.encodeSaved(s);var loaded=AbilityStorage.decodeSaved(saved,s.cpDataConfig());
        check(AbilityStorage.encodeSaved(loaded).equals(saved)&&loaded.presets.skill(0,2).equals(skill.id()),"saved identity/binding exact");
        for(long request:List.of(1L,(long)Integer.MAX_VALUE)){
            String wire=new HighPowerLearningProtocol.Request(skill.id(),(int)request).wire();var parsed=HighPowerLearningProtocol.parse(wire);
            check(wire.length()<=32&&parsed!=null&&parsed.skill().equals(skill.id())&&parsed.sequence()==request,"bounded actual learning action codec");
            check(("00000000-0000-0000-0000-000000000000:"+wire).length()<=64&&wire.startsWith("mar:~"),"UUID plus max sequence fits unchanged native64 bound");
        }
        var page=HighPowerDeveloperLayout.category("vecmanip");check(page.size() == 12 && page.get(11).id().equals("wind_pressure_lift"),"twelve vector nodes with sole443 append");
        var node=page.get(10);check(node.id().equals(skill.id())&&node.index()==442&&node.x()==78&&node.y()==61&&node.parent().equals("vector_cage"),"442 position/parent");
        for(var old:page.subList(0,10))check(Math.hypot(node.x()-old.x(),node.y()-old.y())>37.2,"hover clearance "+old.id());
        check(SkillCatalog.ALL.size()==50&&SkillCatalog.CONFIG_KEYS.size()==41&&PresetSkills.IMPLEMENTED.size()==35,"classic identities/XP denominator unchanged");
        check(SkillCatalog.REGISTERED.size() == 101 && cn.academy.port.SkillCatalog.REGISTERED.get(100).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.SkillCatalog.REGISTERED.get(99).id().equals("point_meltdown")&&SkillCatalog.REGISTERED_CONFIG_KEYS.size() == 92 && cn.academy.port.SkillCatalog.REGISTERED_CONFIG_KEYS.contains(cn.academy.port.core.ClassicSkillConfiguration.key("meltdowner", "remote_scatter_relay")) && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0))&&PresetSkills.REGISTERED.size() == 86 && cn.academy.port.preset.PresetSkills.REGISTERED.contains("remote_scatter_relay") && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.preset.PresetSkills.REGISTERED.contains("point_meltdown"),"explicit derived49 highpower totals");
        System.out.println("MultiAxisReflectionLearningRegressionTest: "+checks+" checks passed");
    }
}
