package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.client.HighPowerDeveloperLayout;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.preset.PresetSkills;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Real shared developer/preset/save APIs; does not stand in for client navigation. */
public final class VectorParryLearningRegressionTest {
    private static int checks;
    private static final String ID="vector_parry", CATEGORY="vecmanip";
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static AbilityProgress state(){var s=new AbilityProgress();s.changeCategoryClassic(CATEGORY);s.setLevel(1);s.setActivateState(true);return s;}
    private static final class Battery implements DevelopmentProcess.Developer {
        double remaining; Battery(double value){remaining=value;}
        public DeveloperType type(){return DeveloperType.PORTABLE;}
        public double energy(){return remaining;} public double maxEnergy(){return 10000;}
        public boolean tryPullEnergy(double amount){double taken=Math.min(amount,remaining);remaining-=taken;return taken==amount;}
    }
    public static void main(String[] args)throws Exception{
        CurrentHighPowerInventory.verify();
        var skill=SkillCatalog.find(CATEGORY,ID).orElseThrow();
        check(HighPowerSkills.VECTOR_PARRY_PROPOSAL==412&&HighPowerSkills.ALL.get(38).equals(skill),"412 append preserves prior38 identities");
        check(skill.level()==1&&skill.controllable()&&skill.requirements().size()==1&&skill.requirements().getFirst().id().equals("deflection_strike"),"actual learned411 prerequisite only");
        var s=state();check(!SkillCatalog.canLearn(s,skill)&&!DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,skill),"unlearned root refuses purchase");
        s.learn("deflection_strike");s.cp=31.25;s.overload=7.5;s.levelExperience=.125;
        int denominator=SkillCatalog.levelSkillCount(s);
        check(SkillCatalog.canLearn(s,skill)&&!PresetSkills.edit(s,0,0,ID),"root opens learning but not unlearned binding");
        var action=DevelopmentActions.skill(s,skill);var process=new DevelopmentProcess();var battery=new Battery(2340);
        check(action.stimulations()==3,"same-tier finite stimulation count");process.start(battery,action);
        for(int i=0;i<77;i++)process.tick();check(!s.learned(ID)&&battery.remaining==30,"no early grant");process.tick();
        check(s.learned(ID)&&s.exp(ID)==0&&s.levelExperience==.125&&s.cp==s.maxCp()&&s.overload==0&&battery.remaining==0,"paid final tick retains classic CP recalculation and grants no XP/mastery; exact high-power budget is native-tested separately");
        check(SkillCatalog.levelSkillCount(s)==denominator&&HighPowerSkills.eligible(s,ID),"classic XP denominator unchanged");
        for(int preset=0;preset<4;preset++)for(int slot=0;slot<4;slot++){
            check(PresetSkills.edit(s,preset,slot,ID),"bind each existing slot");
            check(!PresetSkills.edit(s,preset,(slot+1)%4,ID),"duplicate binding rejected");
            check(PresetSkills.edit(s,preset,slot,""),"clear existing slot");
        }
        PresetSkills.edit(s,0,0,ID);var saved=AbilityStorage.encodeSaved(s);var loaded=AbilityStorage.decodeSaved(saved,s.cpDataConfig());
        check(loaded.learned(ID)&&loaded.presets.skill(0,0).equals(ID)&&loaded.cp==s.cp&&loaded.levelExperience==s.levelExperience,"save identity and resources retained");
        try{
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key(CATEGORY,ID),new ClassicSkillConfiguration.Flags(false,false)));
            check(!HighPowerSkills.eligible(loaded,ID)&&!PresetSkills.selectable(loaded,ID),"disabled configuration gates use");
            check(AbilityStorage.encodeSaved(loaded).equals(saved),"config cannot erase saved identity");
        }finally{SkillCatalog.resetConfiguration();}
        loaded.changeCategoryClassic("electromaster");loaded.changeCategoryClassic(CATEGORY);
        check(!loaded.learned(ID)&&loaded.presets.skill(0,0).isEmpty(),"native category change clears learned and binding as before");
        var page=HighPowerDeveloperLayout.category(CATEGORY);check(page.size() == CurrentHighPowerInventory.pageSize(page) && page.get(11).id().equals("wind_pressure_lift") && page.get(10).id().equals("multi_axis_reflection") && page.get(9).id().equals("vector_cage") && page.get(8).id().equals("circulation_listening") && page.get(7).id().equals("circulation_relief") && page.get(6).id().equals("trajectory_return")&&page.get(5).id().equals("yaw_palm")&&page.get(2).id().equals("braking_touch")&&page.get(1).id().equals(ID)&&page.get(1).index()==412&&page.get(1).x()==198&&page.get(1).y()==61&&page.get(1).parent().equals("deflection_strike"),"separate readable LV1 node");
        var assets=Path.of("src/main/resources/assets/academy/textures/abilities/vecmanip/skills");
        check(java.util.Arrays.equals(Files.readAllBytes(assets.resolve("vec_reflection.png")),Files.readAllBytes(assets.resolve(ID+".png"))),"original vector asset bytes retained");
        for(String locale:new String[]{"zh_cn","en_us"}){
            var json=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/academy/lang/"+locale+".json"))).getAsJsonObject();
            check(!json.get("ac.ability.vecmanip.vector_parry.desc").getAsString().isBlank(),"localized bounded contract");
            if(locale.equals("zh_cn"))check(json.get("ac.ability.vecmanip.vector_parry.name").getAsString().startsWith("向量拨挡"),"workbook name exact");
        }
        System.out.println("PASS "+checks+" NEW412 developer, registration, save, binding and original-asset checks");
    }
}
