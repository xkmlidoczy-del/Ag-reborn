package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.client.ClassicDeveloperLayout;
import cn.academy.port.client.HighPowerDeveloperLayout;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.develop.*;
import cn.academy.port.preset.PresetSkills;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Additive NEW441 learning, immutable earlier ordinals and exact ordinary paid development. */
public final class VectorCageLearningRegressionTest {
    private static int checks;
    private static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    private static AbilityProgress state(){var s=new AbilityProgress();s.changeCategoryClassic("vecmanip");s.setLevel(4);s.setActivateState(true);return s;}
    private static final class Battery implements DevelopmentProcess.Developer {
        double energy; Battery(double e){energy=e;} public DeveloperType type(){return DeveloperType.ADVANCED;}
        public double energy(){return energy;}public double maxEnergy(){return 10000;}
        public boolean tryPullEnergy(double amount){if(energy<amount)return false;energy-=amount;return true;}
    }
    public static void main(String[] args)throws Exception {
        SkillCatalog.resetConfiguration();
        var skill=SkillCatalog.find("vecmanip","vector_cage").orElseThrow();
        check(HighPowerSkills.ALL.size() == 50 && cn.academy.port.highpower.HighPowerSkills.ALL.get(49).id().equals("point_meltdown") && HighPowerSkills.ALL.get(48).id().equals("wind_pressure_lift") && HighPowerSkills.ALL.get(47).id().equals("multi_axis_reflection")&&HighPowerSkills.ALL.get(46).equals(skill)&&SkillCatalog.REGISTERED.get(96).equals(skill),"441 registration index unchanged after442");
        check(skill.level()==4&&skill.controllable()&&skill.requirements().equals(List.of(new SkillCatalog.Requirement("trajectory_return",0))),"exact LV4 prerequisite");
        var s=state();check(!SkillCatalog.canLearn(s,skill)&&!PresetSkills.edit(s,0,0,skill.id()),"unlearned or missing prerequisite refuses");
        s.learn("trajectory_return");s.level=2;check(!SkillCatalog.canLearn(s,skill),"LV2 refuses");s.setLevel(4);s.levelExperience=.125;
        check(DevelopmentActions.canLearn(s,DeveloperType.ADVANCED,skill)&&!DevelopmentActions.canLearn(s,DeveloperType.NORMAL,skill),"advanced machine required at this tier");
        var action=DevelopmentActions.skill(s,skill);check(action.stimulations()==11,"native learning count unchanged");
        var b=new Battery(7040);var process=new DevelopmentProcess();process.start(b,action);
        for(int i=0;i<175;i++)process.tick();check(!s.learned(skill.id())&&b.energy==40,"not granted before final IF payment");
        process.tick();check(s.learned(skill.id())&&b.energy==0&&s.exp(skill.id())==0&&s.levelExperience==.125,"advanced exact payment grants no free mastery or XP");
        check(PresetSkills.edit(s,0,2,skill.id()),"learned binding works");
        var saved=AbilityStorage.encodeSaved(s);var loaded=AbilityStorage.decodeSaved(saved,s.cpDataConfig());
        check(AbilityStorage.encodeSaved(loaded).equals(saved)&&loaded.learned(skill.id())&&loaded.presets.skill(0,2).equals(skill.id()),"native save keeps ID/binding");
        var shortState=state();shortState.learn("trajectory_return");var shortBattery=new Battery(7039);var failed=new DevelopmentProcess();failed.start(shortBattery,DevelopmentActions.skill(shortState,skill));
        for(int i=0;i<176;i++)failed.tick();check(!shortState.learned(skill.id()),"insufficient IF cannot mint learning");
        for(int sequence:List.of(1,36,65535,Integer.MAX_VALUE)) {
            var request=new HighPowerLearningProtocol.Request(skill.id(),sequence);
            check(request.wire().startsWith("vector_cage:~")&&request.equals(HighPowerLearningProtocol.parse(request.wire())),"NEW441 bounded alias roundtrip "+sequence);
            check(("00000000-0000-0000-0000-000000000000:"+request.wire()).length()<=64,"machine token and maximum learning sequence remain bounded");
        }
        check(HighPowerLearningProtocol.parse("vector_cage:~0")==null&&HighPowerLearningProtocol.parse("vector_cage:~zik0zk")==null,"invalid alias sequences refuse");
        var page=HighPowerDeveloperLayout.category("vecmanip");check(page.size() == 12 && page.get(11).id().equals("wind_pressure_lift"),"twelve bounded vector nodes after443");
        String[] old={"deflection_strike","vector_parry","braking_touch","shear_windblade","inertial_anchor","yaw_palm","trajectory_return","circulation_relief","circulation_listening"};
        double[] xs={118,198,118,38,118,198,118,198,38},ys={61,61,126,105,105,105,17,17,17};
        for(int i=0;i<old.length;i++)check(page.get(i).id().equals(old[i])&&page.get(i).x()==xs[i]&&page.get(i).y()==ys[i],"old page position preserved "+old[i]);
        var node=page.get(9);check(node.id().equals(skill.id())&&node.index()==441&&node.x()==38&&node.y()==61&&node.parent().equals("trajectory_return"),"new node and dependency identity");
        for(var prior:page.subList(0,9))check(Math.hypot(node.x()-prior.x(),node.y()-prior.y())>37.2,"hover clearance "+prior.id());
        check(SkillCatalog.ALL.size()==50&&SkillCatalog.CONFIG_KEYS.size()==41&&PresetSkills.IMPLEMENTED.size()==35,"classic catalog/XP identifiers stay unchanged");
        check(SkillCatalog.REGISTERED.size() == 100 && cn.academy.port.SkillCatalog.REGISTERED.get(99).id().equals("point_meltdown")&&SkillCatalog.REGISTERED_CONFIG_KEYS.size() == 91&&PresetSkills.REGISTERED.size() == 85 && cn.academy.port.preset.PresetSkills.REGISTERED.contains("point_meltdown"),"exact derived totals");
        check(HighPowerDeveloperLayout.page("vecmanip",HighPowerDeveloperLayout.Page.CLASSIC).equals(ClassicDeveloperLayout.NODES.stream().filter(n->n.category().equals("vecmanip")).toList()),"classic page unchanged");
        check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of("src/main/resources/classic-skills.json")))).equals("fe698b35ce94e9cbd628dc63950bfd706749177d3b0ebe4048c1b4e7905e4433"),"original catalog immutable hash");
        for(String lang:List.of("zh_cn","en_us")) {
            var text=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/academy/lang/"+lang+".json"))).getAsJsonObject();
            for(String key:List.of("ac.ability.vecmanip.vector_cage.name","ac.ability.vecmanip.vector_cage.desc",
                    "hud.academy.highpower.vector_cage","hud.academy.highpower.vector_cage.preview",
                    "message.academy.highpower.vector_cage.not_ready","message.academy.highpower.vector_cage.blocked"))
                check(text.has(key)&&!text.get(key).getAsString().isBlank(),"localized actual UI key "+lang+" "+key);
        }
        System.out.println("PASS "+checks+" NEW441 paid learning/save/additive page checks");
    }
}
