package cn.academy.port.passive;
import cn.academy.port.*;
import cn.academy.port.core.*;
import cn.academy.port.develop.*;
import cn.academy.port.client.ClassicDeveloperLayout;
import cn.academy.port.preset.PresetSkills;
import java.util.*;
public final class PassiveSkillsLearningRegressionTest {
 static int checks;static void yes(boolean v,String l){checks++;if(!v)throw new AssertionError(l);}
 static class Battery implements DevelopmentProcess.Developer {final DeveloperType type;double stored;Battery(DeveloperType t,double e){type=t;stored=e;}public DeveloperType type(){return type;}public double energy(){return stored;}public double maxEnergy(){return type.energy;}public boolean tryPullEnergy(double amount){if(stored<amount){if(type==DeveloperType.PORTABLE)stored=0;return false;}stored-=amount;return true;}}
 static AbilityProgress ready(SkillCatalog.Skill skill){var s=new AbilityProgress();s.selectCategory(skill.category());s.setLevel(skill.level());for(var req:skill.requirements()){s.learn(req.id());s.experience.put(req.id(),req.exp());}if(skill.anyLearnedSkillLevel()>0){var other=SkillCatalog.ALL.stream().filter(k->k.category().equals(skill.category())&&k.level()==skill.anyLearnedSkillLevel()&&!k.id().equals(skill.id())).findFirst().orElseThrow();s.learn(other.id());}return s;}
 public static void main(String[] args){
  yes(PresetSkills.IMPLEMENTED.size()==35&&SkillAvailability.PASSIVES.size()==6&&SkillCatalog.ALL.size()==50,"35active+3categorypassives+12generic category instances=50entries/41IDs");
  var passives=SkillCatalog.ALL.stream().filter(k->!k.controllable()).toList();yes(passives.size()==15&&passives.stream().allMatch(k->SkillAvailability.learnable(k.id())),"every source passive advertised and implemented");
  for(var skill:passives){
   var node=ClassicDeveloperLayout.NODES.stream().filter(n->n.category().equals(skill.category())&&n.id().equals(skill.id())).findFirst().orElseThrow();
   int[] xy=switch(skill.id()){case "rad_intensify"->new int[]{35,75};case "dim_folding_theorem"->new int[]{50,75};case "space_fluct"->new int[]{160,80};case "brain_course"->new int[]{30,110};case "brain_course_advanced"->new int[]{115,110};case "mind_course"->new int[]{205,110};default->throw new AssertionError();};
   yes(node.enabled()&&node.x()==xy[0]&&node.y()==xy[1]&&node.level()==skill.level(),"source GUI position/enable/level "+skill.category()+":"+skill.id());
   yes(node.conditions().stream().anyMatch(c->c.get("type").getAsString().equals("developer_type")),"source GUI exposes machine tier");
   var state=ready(skill);yes(!state.learned(skill.id())&&SkillCatalog.canLearn(state,skill),"unlearned target with declared prerequisites");
   for(var req:skill.requirements()){Double previous=state.experience.remove(req.id());yes(!SkillCatalog.canLearn(state,skill),"zero mastery still needs learned parent");state.experience.put(req.id(),(double)Math.nextDown((float)req.exp()));yes(req.exp()==0||!SkillCatalog.canLearn(state,skill),"one float ULP below source mastery gate");state.experience.put(req.id(),previous);}
   if(skill.id().equals("dim_folding_theorem")){state.experience.put("threatening_teleport",Math.nextDown(.2d));yes(SkillCatalog.canLearn(state,skill),"source float comparison rounds double fixture to exact .2f");}
   if(skill.anyLearnedSkillLevel()>0){var withLevel=new HashMap<String,Double>();for(var other:SkillCatalog.ALL)if(other.category().equals(skill.category())&&other.level()==skill.anyLearnedSkillLevel()&&state.learned(other.id()))withLevel.put(other.id(),state.experience.remove(other.id()));yes(!SkillCatalog.canLearn(state,skill),"generic any-learned-level condition enforced");state.experience.putAll(withLevel);}
   for(var type:DeveloperType.values()){
    state=ready(skill);boolean expected=type.supportsSkill(skill.level());yes(DevelopmentActions.canLearn(state,type,skill)==expected,"source Portable/Normal/Advanced tier matrix");if(!expected)continue;
    var battery=new Battery(type,type.energy);var process=new DevelopmentProcess();process.start(battery,DevelopmentActions.skill(state,skill));int ticks=ClassicRules.learningStimulations(skill.level())*type.ticksPerStimulation();
    for(int tick=1;tick<=ticks;tick++){process.tick();if(tick<ticks)yes(!state.learned(skill.id()),"finite stimulations cannot grant early");}
    yes(process.state()==DevelopmentProcess.State.DONE&&state.learned(skill.id())&&state.experience.get(skill.id())==0,"genuine zero stored mastery after finite learning");yes(Math.abs(battery.energy()-(type.energy-type.actualConsumption(ClassicRules.learningStimulations(skill.level()))))<1e-6,"source TPS+1 finite IF cost");var done=state;yes(!PresetSkills.selectable(done,skill.id())&&!done.presets.edit(0,0,skill.id(),id->PresetSkills.selectable(done,id)),"earned passives stay hidden from presets");
   }
   state=ready(skill);var type=DeveloperType.minimumForSkill(skill.level());var battery=new Battery(type,type.energyPerTick()-.25);var process=new DevelopmentProcess();process.start(battery,DevelopmentActions.skill(state,skill));process.tick();yes(process.state()==DevelopmentProcess.State.FAILED&&!state.learned(skill.id()),"finite battery shortage never learns");
   state=ready(skill);battery=new Battery(type,type.energy);process=new DevelopmentProcess();process.start(battery,DevelopmentActions.skill(state,skill));int ticks=ClassicRules.learningStimulations(skill.level())*type.ticksPerStimulation();for(int tick=1;tick<ticks;tick++)process.tick();state.level=skill.level()-1;process.tick();yes(process.state()==DevelopmentProcess.State.FAILED&&!state.learned(skill.id()),"completion rechecks current level");
  }
  System.out.println("PASS "+checks+" all15 passive/generic GUI nodes, source prerequisites, tiers and genuine finite-power learning checks");
 }
}
