package cn.academy.port.skill;
import cn.academy.port.*;
import cn.academy.port.core.*;
import cn.academy.port.develop.*;
import cn.academy.port.machine.*;
import cn.academy.port.preset.PresetSkills;
import java.util.*;
public final class VectorStarterLearningRegressionTest {
 private static int checks;private static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  for(String id:List.of("vec_accel","vec_deviation")){String parent=id.equals("vec_accel")?"dir_shock":"vec_accel";var skill=SkillCatalog.find("vecmanip",id).orElseThrow();check(skill.level()==2&&skill.controllable()&&skill.requirements().equals(List.of(new SkillCatalog.Requirement(parent,0))),"canonicalLv2 parent no inventedmastery");check(PresetSkills.IMPLEMENTED.contains(id)&&SkillAvailability.learnable(id),"common availability");
   var s=ready();check(!SkillCatalog.canLearn(s,skill),"missing learnedparent denied");s.learn(parent);check(SkillCatalog.canLearn(s,skill)&&DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,skill),"zeroEXPparent andportable allowed");
   for(var type:DeveloperType.values()){s=ready();s.learn(parent);var battery=new Battery(type,type.energy);var process=new DevelopmentProcess();var action=DevelopmentActions.skill(s,skill);check(action.stimulations()==5,"sourcefiveLv2stimulations");process.start(battery,action);int ticks=5*type.ticksPerStimulation();for(int t=1;t<=ticks;t++){process.tick();if(t<ticks)check(!s.learned(id),"noearly targetgrant");}check(process.state()==DevelopmentProcess.State.DONE&&s.learned(id)&&s.exp(id)==0,"truezeromasterycompletion");check(Math.abs(battery.energy()-(type.energy-type.actualConsumption(5)))<1E-5,"finiteIF exactdrain");s.activated=true;var bound=s;check(s.presets.edit(0,0,id,value->PresetSkills.selectable(bound,value)),"earnedskillbinds");}
   s=ready();s.learn(parent);var battery=new Battery(DeveloperType.NORMAL,34);var process=new DevelopmentProcess();process.start(battery,DevelopmentActions.skill(s,skill));process.tick();check(process.state()==DevelopmentProcess.State.FAILED&&!s.learned(id)&&battery.energy()==34,"finite machine energy failure");
   s=ready();s.learn(parent);battery=new Battery(DeveloperType.NORMAL,50000);process=new DevelopmentProcess();process.start(battery,DevelopmentActions.skill(s,skill));for(int tick=1;tick<105;tick++)process.tick();s.experience.remove(parent);process.tick();check(process.state()==DevelopmentProcess.State.FAILED&&!s.learned(id),"completion revalidates sourceparent");
  }
  check(SkillAvailability.learnable("dir_shock")&&SkillAvailability.learnable("ground_shock")&&SkillAvailability.learnable("electron_missile"),"previous pathsretained");check(!SkillAvailability.learnable("plasma_blow"),"unimplemented legacy metadata stays unavailable");
  System.out.println("PASS "+checks+" VecManip sourcegates and finite-power learning checks");
 }
 private static AbilityProgress ready(){var s=new AbilityProgress();s.selectCategory("vecmanip");s.setLevel(2);return s;}
 private static final class Battery implements DevelopmentProcess.Developer{final DeveloperType type;final MachineDeveloperEnergy energy;double portable;Battery(DeveloperType type,double n){this.type=type;portable=n;energy=type==DeveloperType.PORTABLE?null:new MachineDeveloperEnergy(type,()->{},()->true);if(energy!=null)energy.load(n);}public DeveloperType type(){return type;}public boolean tryPullEnergy(double n){if(energy!=null)return energy.tryPull(n);double old=portable;portable=Math.max(0,portable-n);return old>=n;}public double energy(){return energy==null?portable:energy.energy();}public double maxEnergy(){return type.energy;}}
}
