package cn.academy.port.skill;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.preset.PresetSkills;
public final class ElectromasterFinalLearningRegressionTest {
    private static int assertions;
    private static void check(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }
    public static void main(String[] args) {
        var state = new AbilityProgress(); state.selectCategory("electromaster"); state.setLevel(5);
        var body = SkillCatalog.find("electromaster", "body_intensify").orElseThrow();
        var thunder = SkillCatalog.find("electromaster", "thunder_clap").orElseThrow();
        check(body.controllable() && body.level() == 3 && thunder.controllable() && thunder.level() == 5, "canonical category classifies both ACTIVE");
        check(body.requirements().equals(java.util.List.of(new SkillCatalog.Requirement("arc_gen", 1), new SkillCatalog.Requirement("charging", 1))), "body exact two mastered prereqs");
        check(thunder.requirements().equals(java.util.List.of(new SkillCatalog.Requirement("thunder_bolt", 1))), "thunder masteredbolt only, no railgun prerequisite");
        check(!SkillCatalog.canLearn(state, body) && !SkillCatalog.canLearn(state, thunder), "no missing prereqs allowed");
        state.learn("arc_gen"); state.learn("charging"); state.experience.put("arc_gen", 1D); state.experience.put("charging", Math.nextDown(1D));
        check(SkillCatalog.canLearn(state, body), "double-only Charging predecessor rounds to source 1f and accepts");
        state.experience.put("charging", (double)Math.nextDown(1f));
        check(!SkillCatalog.canLearn(state, body), "charging justbelow1 insufficient"); state.experience.put("charging", 1D); state.experience.put("arc_gen", Math.nextDown(1D));
        check(SkillCatalog.canLearn(state, body), "double-only ArcGen predecessor rounds to source 1f and accepts");
        state.experience.put("arc_gen", (double)Math.nextDown(1f));
        check(!SkillCatalog.canLearn(state, body), "arc justbelow1 insufficient"); state.experience.put("arc_gen", 1D);
        check(SkillCatalog.canLearn(state, body), "body bothmastered unlock");
        check(!DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, body) && DevelopmentActions.canLearn(state, DeveloperType.NORMAL, body) && DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, body), "body genuine normaltier learning");
        check(DevelopmentActions.skill(state, body).stimulations() == 7, "body7 stimulations");
        state.setLevel(2); check(!SkillCatalog.canLearn(state, body), "bodybelowlevel3reject"); state.setLevel(3); DevelopmentActions.skill(state, body).complete(); state.activated = true;
        check(state.exp(body.id()) == 0 && BodyIntensifySession.mayStart(state), "naturalbodyzero mastery starts");
        check(PresetSkills.selectable(state, body.id()), "naturalbodypreset selectable");
        state.setLevel(5); state.learn("thunder_bolt"); state.experience.put("thunder_bolt", Math.nextDown(1D));
        check(SkillCatalog.canLearn(state, thunder), "double-only ThunderBolt predecessor rounds to source 1f and accepts");
        state.experience.put("thunder_bolt", (double)Math.nextDown(1f));
        check(!SkillCatalog.canLearn(state, thunder), "bolt justbelow1 insufficient"); state.experience.put("thunder_bolt", 1D);
        check(SkillCatalog.canLearn(state, thunder), "masteredbolt unlocks WITHOUTrailgun");
        check(!DevelopmentActions.canLearn(state, DeveloperType.PORTABLE, thunder) && !DevelopmentActions.canLearn(state, DeveloperType.NORMAL, thunder) && DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, thunder), "thunder genuine advancedtier learning");
        check(DevelopmentActions.skill(state, thunder).stimulations() == 15, "thunder15stimulations");
        state.setLevel(4); check(!SkillCatalog.canLearn(state, thunder), "thunderbelowlevel5reject"); state.setLevel(5); DevelopmentActions.skill(state, thunder).complete();
        check(state.exp(thunder.id()) == 0 && ThunderClapSession.mayStart(state), "naturalthunderzero mastery starts");
        check(PresetSkills.selectable(state, thunder.id()), "naturalthunderpreset selectable");
        check(!SkillCatalog.canLearn(state, body) && !SkillCatalog.canLearn(state, thunder), "duplicatecatalog learning rejected");
        check(PresetSkills.IMPLEMENTED.containsAll(java.util.List.of(body.id(), thunder.id(), "mine_detect", "thunder_bolt", "railgun", "mag_manip", "mag_movement", "charging", "arc_gen")), "allnineoriginalEMactive paths preserved");
        for (var skill : java.util.List.of(body, thunder)) {
            var learner=new AbilityProgress();learner.selectCategory("electromaster");learner.setLevel(5);
            for(var req:skill.requirements()){learner.learn(req.id());learner.experience.put(req.id(),req.exp());}
            var tier=skill==body?DeveloperType.NORMAL:DeveloperType.ADVANCED;
            var battery=new Battery(tier,tier.energy);var process=new DevelopmentProcess();var action=DevelopmentActions.skill(learner,skill);process.start(battery,action);
            int ticks=action.stimulations()*tier.ticksPerStimulation();
            for(int tick=1;tick<=ticks;tick++){process.tick();if(tick<ticks)check(!learner.learned(skill.id()),"finite IF learning never completes early");}
            check(process.state()==DevelopmentProcess.State.DONE&&learner.learned(skill.id())&&learner.exp(skill.id())==0,"genuine completed finite IF process learns zero mastery");
            check(Math.abs(battery.energy-(tier.energy-tier.actualConsumption(action.stimulations())))<1E-8,"source TPS+1 real IF consumption");
            learner.experience.remove(skill.id());battery=new Battery(tier,tier.energyPerTick()-1);process=new DevelopmentProcess();process.start(battery,DevelopmentActions.skill(learner,skill));process.tick();
            check(process.state()==DevelopmentProcess.State.FAILED&&!learner.learned(skill.id()),"finite power exhaustion never grants skill");
            battery=new Battery(tier,tier.energy);process=new DevelopmentProcess();process.start(battery,DevelopmentActions.skill(learner,skill));learner.experience.put(skill.requirements().getFirst().id(),0D);
            for(int tick=0;tick<ticks;tick++)process.tick();check(process.state()==DevelopmentProcess.State.FAILED&&!learner.learned(skill.id()),"prerequisites revalidated at completion");
        }
        System.out.println("PASS " + assertions + " final Electromaster source prerequisite and natural learning assertions");
    }
    private static final class Battery implements DevelopmentProcess.Developer {
        final DeveloperType type;double energy;
        Battery(DeveloperType type,double energy){this.type=type;this.energy=energy;}
        public DeveloperType type(){return type;}
        public boolean tryPullEnergy(double amount){double old=energy;energy=Math.max(0,energy-amount);return old>=amount;}
        public double energy(){return energy;}
        public double maxEnergy(){return type.energy;}
    }
}
