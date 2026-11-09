package cn.academy.port.highpower;

import cn.academy.port.SkillCatalog;
import cn.academy.port.AbilityStorage;
import cn.academy.port.client.ClassicDeveloperLayout;
import cn.academy.port.client.ClassicDeveloperTimeline;
import cn.academy.port.client.HighPowerDeveloperLayout;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.develop.*;
import cn.academy.port.preset.PresetSkills;
import java.awt.geom.Line2D;
import java.util.List;

/** Append-only NEW351 learning; numerical growth/stock are not granted by learning. */
public final class MultidimensionalLandingArrayLearningRegressionTest {
    private static final String ID=HighPowerSkills.MULTIDIMENSIONAL_LANDING_ARRAY;
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static final class Battery implements DevelopmentProcess.Developer {
        double energy; Battery(double energy){this.energy=energy;}
        public DeveloperType type(){return DeveloperType.ADVANCED;}
        public boolean tryPullEnergy(double n){if(energy<n)return false;energy-=n;return true;}
        public double energy(){return energy;} public double maxEnergy(){return DeveloperType.ADVANCED.energy;}
    }
    public static void main(String[] args){
        SkillCatalog.resetConfiguration();
        try {
            var skill=SkillCatalog.find("teleporter",ID).orElseThrow();
            check(ID.equals("multidimensional_landing_array")&&HighPowerSkills.MULTIDIMENSIONAL_LANDING_ARRAY_PROPOSAL==351,"saved ID");
            check(skill.level()==5&&skill.controllable()&&skill.requirements().equals(List.of(new SkillCatalog.Requirement(HighPowerSkills.SPATIAL_PINCER_CORRIDOR,0))),"LV5 and actual341 prerequisite");
            check(HighPowerSkills.ALL.size() == 50 && cn.academy.port.highpower.HighPowerSkills.ALL.get(49).id().equals("point_meltdown") && HighPowerSkills.ALL.get(48).id().equals("wind_pressure_lift") && HighPowerSkills.ALL.get(47).id().equals("multi_axis_reflection") && HighPowerSkills.ALL.get(45).id().equals("circulation_listening") && HighPowerSkills.ALL.get(44).id().equals("circulation_relief") && HighPowerSkills.ALL.get(43).id().equals("trajectory_return") && HighPowerSkills.ALL.get(42).id().equals("yaw_palm") && HighPowerSkills.ALL.get(41).id().equals("inertial_anchor") && HighPowerSkills.ALL.get(40).id().equals("shear_windblade") && HighPowerSkills.ALL.get(39).id().equals("braking_touch")&&HighPowerSkills.ALL.get(36).equals(skill)&&HighPowerSkills.ALL.get(35).id().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR),"append after unchanged341ordinal");
            check(SkillCatalog.ALL.size()==50&&ClassicDeveloperLayout.NODES.size()==50&&PresetSkills.IMPLEMENTED.size()==35&&SkillCatalog.CONFIG_KEYS.size()==41,"classic catalog unchanged");
            check(SkillCatalog.REGISTERED.size() == 100 && cn.academy.port.SkillCatalog.REGISTERED.get(99).id().equals("point_meltdown")&&PresetSkills.REGISTERED.size() == 85 && cn.academy.port.preset.PresetSkills.REGISTERED.contains("point_meltdown")&&SkillCatalog.REGISTERED_CONFIG_KEYS.size() == 91,"one derived entry");
            for(int level=1;level<=5;level++){
                var s=new AbilityProgress();s.changeCategoryClassic("teleporter");s.setLevel(level);s.learn(HighPowerSkills.SPATIAL_PINCER_CORRIDOR);
                final int lv=level;
                check(SkillCatalog.levelSkillCount(s)==SkillCatalog.ALL.stream().filter(x->x.category().equals("teleporter")&&x.level()==lv&&x.controllable()).count(),"nativeXP denominator at"+level);
                check(SkillCatalog.canLearn(s,skill)==(level==5),"LV gate"+level);
            }
            var s=new AbilityProgress();s.changeCategoryClassic("teleporter");s.setLevel(5);s.setActivateState(true);
            check(!SkillCatalog.canLearn(s,skill),"no341 refuses");s.learn(HighPowerSkills.SPATIAL_PINCER_CORRIDOR);
            check(!DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,skill)&&!DevelopmentActions.canLearn(s,DeveloperType.NORMAL,skill)&&DevelopmentActions.canLearn(s,DeveloperType.ADVANCED,skill),"actualAdvanced hardware");
            check(ClassicRules.learningStimulations(5)==15&&DeveloperType.ADVANCED.actualConsumption(15)==9600,"15stimulations240nativepaidticks");
            var b=new Battery(9600);var process=new DevelopmentProcess();process.start(b,DevelopmentActions.skill(s,skill));
            double xp=s.levelExperience;for(int t=0;t<239;t++)process.tick();
            check(!s.learned(ID)&&b.energy==40,"lastpaidtick required");process.tick();
            check(s.learned(ID)&&s.exp(ID)==0&&s.levelExperience==xp&&b.energy==0&&process.state()==DevelopmentProcess.State.DONE,"paidonce noXPgrant");
            var saved=AbilityStorage.decodeSaved(AbilityStorage.encodeSaved(s),s.cpDataConfig());check(saved.learned(ID)&&saved.exp(ID)==0,"save preserveslearnedID");
            for(int sequence:new int[]{1,35,36,Integer.MAX_VALUE}){
                var request=new HighPowerLearningProtocol.Request(ID,sequence);check(request.wire().startsWith("mla:")&&HighPowerLearningProtocol.parse(request.wire()).equals(request)&&request.wire().length()+37<=64,"boundedcanonicalwire");
            }
            var page=HighPowerDeveloperLayout.category("teleporter");var node=page.get(12);
            check(page.size()==13&&node.id().equals(ID)&&node.level()==5&&node.index()==351&&node.parent().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR),"newnode withmatchingprerequisite");
            check(node.x()==158&&node.y()==105,"emptycell retainedlayout");
            for(var a:page)for(var bnode:page)if(a!=bnode)check(Math.hypot(a.x()-bnode.x(),a.y()-bnode.y())>37.2,"hoverrings disjoint");
            for(double phase:new double[]{0,.1,.5,.9,1})for(var edge:HighPowerDeveloperLayout.multidimensionalLandingArrayDependency(phase)){
                check(Math.min(edge.x0(),edge.x1())-Math.abs(edge.nx())-5>=0&&Math.max(edge.x0(),edge.x1())+Math.abs(edge.nx())+5<=257&&Math.min(edge.y0(),edge.y1())-Math.abs(edge.ny())-5>=0&&Math.max(edge.y0(),edge.y1())+Math.abs(edge.ny())+5<=139,"routeviewport");
                for(var other:page)if(!other.id().equals(ID)&&!other.id().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR))check(Line2D.ptSegDist(edge.x0(),edge.y0(),edge.x1(),edge.y1(),other.x()+8,other.y()+8)>18.6+ClassicDeveloperTimeline.LINK_WIDTH/2,"linkclears"+other.id());
            }
            System.out.println("MultidimensionalLandingArrayLearningRegressionTest: "+checks+" checks passed");
        } finally {SkillCatalog.resetConfiguration();}
    }
}
