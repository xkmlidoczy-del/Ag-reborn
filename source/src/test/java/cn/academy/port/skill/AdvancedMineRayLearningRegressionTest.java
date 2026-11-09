package cn.academy.port.skill;
import cn.academy.port.*;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.develop.*;
import cn.academy.port.preset.PresetSkills;
import cn.academy.port.machine.MachineDeveloperEnergy;
import java.util.*;
import static cn.academy.port.skill.AdvancedMineRaySession.*;

/** True common-registry learning, declared prerequisite states and a finite physical battery model. */
public final class AdvancedMineRayLearningRegressionTest {
    private static int checks;private static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    private static AbilityProgress ready(Tier tier){var s=new AbilityProgress();s.selectCategory("meltdowner");s.setLevel(tier.level);return s;}
    public static void main(String[] args) {
        check(SkillCatalog.ALL.size()==50,"canonical metadata unchanged");
        check(PresetSkills.IMPLEMENTED.containsAll(List.of("arc_gen","charging","railgun","electron_bomb","threatening_teleport","dir_shock","ground_shock","mag_movement","mag_manip","thunder_bolt","mine_detect","body_intensify","thunder_clap","scatter_bomb","light_shield","meltdowner","mine_ray_basic")),"all m13 seventeen active paths preserved");
        for(Tier tier:Tier.values()) {
            var skill=SkillCatalog.find("meltdowner",tier.id).orElseThrow();String parent=tier==Tier.EXPERT?"mine_ray_basic":"mine_ray_expert";double exp=tier==Tier.EXPERT?.8:1;int stims=tier==Tier.EXPERT?11:15,ticks=stims*16;double total=ticks*40D;
            check(skill.level()==tier.level&&skill.requirements().equals(List.of(new SkillCatalog.Requirement(parent,exp))),"source dependency and category level");
            check(SkillAvailability.LEARNABLE.equals(union())&&SkillAvailability.learnable(tier.id),"actual SkillAvailability union wires genuine learning ingress");
            var s=ready(tier);check(!SkillCatalog.canLearn(s,skill),"unlearned prerequisite blocks");s.learn(parent);s.experience.put(parent,Math.nextDown(exp));check(SkillCatalog.canLearn(s,skill),"double-only predecessor rounds to exact source float and accepts");s.experience.put(parent,(double)Math.nextDown((float)exp));check(!SkillCatalog.canLearn(s,skill),"just-below prerequisite mastery blocks");s.experience.put(parent,exp);check(SkillCatalog.canLearn(s,skill),"exact source parent threshold accepts");
            check(!DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,skill)&&!DevelopmentActions.canLearn(s,DeveloperType.NORMAL,skill)&&DevelopmentActions.canLearn(s,DeveloperType.ADVANCED,skill),"both require Advanced developer");
            var action=DevelopmentActions.skill(s,skill);check(action.stimulations()==stims,"11 or15 source stimulations");check(DeveloperType.ADVANCED.estimatedConsumption(stims)==(tier==Tier.EXPERT?6600:9000),"source estimate differs from real debit");
            var battery=new Battery(200000);var process=new DevelopmentProcess();process.start(battery,action);
            for(int tick=1;tick<=ticks;tick++){process.tick();if(tick<ticks)check(!s.learned(tier.id),"target never granted early");}
            check(process.state()==DevelopmentProcess.State.DONE&&s.learned(tier.id)&&s.exp(tier.id)==0,"real finite development learns zero mastery");check(battery.energy()==200000-total,"7040 or9600 actual IF debit");check(s.presets.currentSkill(0).isEmpty(),"learning never autobinds");
            s.activated=true;final var earned=s;check(s.presets.edit(0,0,tier.id,id->PresetSkills.selectable(earned,id))&&mayStart(s,tier),"naturally learned target binds and passes genuine use gate");
            s=ready(tier);s.experience.put(parent,exp);battery=new Battery(39);process=new DevelopmentProcess();process.start(battery,DevelopmentActions.skill(s,skill));process.tick();check(process.state()==DevelopmentProcess.State.FAILED&&!s.learned(tier.id)&&battery.energy()==39,"insufficient battery fails atomically, grants nothing");
            s=ready(tier);s.experience.put(parent,exp);battery=new Battery(total-1);process=new DevelopmentProcess();process.start(battery,DevelopmentActions.skill(s,skill));for(int tick=0;tick<ticks;tick++)process.tick();check(process.state()==DevelopmentProcess.State.FAILED&&!s.learned(tier.id)&&battery.energy()==39,"last actual40IF tick cannot be replaced by nominal estimate");
            s=ready(tier);s.experience.put(parent,exp);battery=new Battery(200000);process=new DevelopmentProcess();process.start(battery,DevelopmentActions.skill(s,skill));for(int tick=0;tick<ticks-1;tick++)process.tick();s.experience.remove(parent);process.tick();check(process.state()==DevelopmentProcess.State.FAILED&&!s.learned(tier.id),"completion revalidates exact prerequisite");
        }
        System.out.println("PASS "+checks+" genuine Advanced developer gates, finite IF learning and registry checks");
    }
    private static Set<String> union(){var set=new HashSet<>(PresetSkills.IMPLEMENTED);set.addAll(SkillAvailability.PASSIVES);return set;}
    private static final class Battery implements DevelopmentProcess.Developer {
        final MachineDeveloperEnergy energy=new MachineDeveloperEnergy(DeveloperType.ADVANCED,()->{},()->true);
        Battery(double power){energy.load(power);}public DeveloperType type(){return DeveloperType.ADVANCED;}
        public boolean tryPullEnergy(double amount){return energy.tryPull(amount);}public double energy(){return energy.energy();}public double maxEnergy(){return 200000;}
    }
}
