package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillAvailability;
import cn.academy.port.SkillCatalog;
import cn.academy.port.client.ClassicDeveloperLayout;
import cn.academy.port.client.HighPowerDeveloperLayout;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.preset.PresetSkills;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Additive pack checks. Canonical source catalog, layout and progression assertions remain independent. */
public final class HighPowerProgressionRegressionTest {
    private static int checks;
    private static void check(boolean v,String reason){checks++;if(!v)throw new AssertionError(reason);}
    private static AbilityProgress state(int level){var s=new AbilityProgress();s.changeCategoryClassic("electromaster");s.setLevel(level);s.setActivateState(true);return s;}
    private static final class Battery implements DevelopmentProcess.Developer {
        double energy; final DeveloperType type;
        Battery(double energy){this(energy,DeveloperType.PORTABLE);}
        Battery(double energy,DeveloperType type){this.energy=energy;this.type=type;}
        public DeveloperType type(){return type;}
        public boolean tryPullEnergy(double amount){double paid=Math.min(energy,amount);energy-=paid;return paid==amount;}
        public double energy(){return energy;}
        public double maxEnergy(){return type().energy;}
    }
    public static void main(String[] args)throws Exception{
        clientReleaseIsolation();
        check(SkillCatalog.ALL.size()==50,"all fifty canonical source entries retained");
        check(ClassicDeveloperLayout.NODES.size()==50,"all fifty canonical source layout entries retained");
        check(PresetSkills.IMPLEMENTED.size()==35,"all thirty-five classic active routes retained");
        check(SkillCatalog.CONFIG_KEYS.size()==41,"all classic generic aliases retained");
        check(SkillCatalog.REGISTERED.size() == 101 && cn.academy.port.SkillCatalog.REGISTERED.get(100).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.SkillCatalog.REGISTERED.get(99).id().equals("point_meltdown")&&PresetSkills.REGISTERED.size() == 86 && cn.academy.port.preset.PresetSkills.REGISTERED.contains("remote_scatter_relay") && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.preset.PresetSkills.REGISTERED.contains("point_meltdown")&&HighPowerSkills.ALL.get(30).id().equals(HighPowerSkills.CORNER_EXCHANGE) && HighPowerSkills.ALL.get(31).id().equals(HighPowerSkills.SHORT_INTERCEPTION)
                && HighPowerSkills.ALL.get(32).id().equals(HighPowerSkills.SPATIAL_RELAY)
                && HighPowerSkills.ALL.get(33).id().equals(HighPowerSkills.EMERGENCY_RETURN)
                && HighPowerSkills.ALL.get(34).id().equals(HighPowerSkills.TACTICAL_COORDINATE_FORMATION)
                && SkillCatalog.REGISTERED.get(84).equals(HighPowerSkills.ALL.get(34))
                && HighPowerSkills.ALL.get(35).id().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR)
                && SkillCatalog.REGISTERED.get(85).equals(HighPowerSkills.ALL.get(35)),"thirty-five retained additions plus appended NEW341");
        check(HighPowerSkills.ALL.subList(0,30).stream().map(SkillCatalog.Skill::id).toList().equals(List.of("forked_arc","magnetic_bracer","charge_decoy","polarity_tether","conductive_circuit","grounding_return","magnetic_ground_step","magnetic_item_pull","iron_sand_lance","thunder_net","potential_buffer","rail_thunder_array","magnetic_domain","dual_point","scatter_plates","beam_etching","thermal_shell","folded_mirror_array","disintegration_sweep","beam_step","particle_laminate","directional_dissipation_curtain","multicore_canopy","zero_gap_armor","safe_landing_probe","phase_sidestep","displaced_projection","return_mark","recovery_mark","cross_substitution")),"NEW321 appends without reordering or renaming all29 prior additions");
        for(var entry:HighPowerSkills.ALL){
            check(SkillCatalog.find(entry.category(),entry.id()).orElseThrow().equals(entry),"registered extension "+entry.id());
            check(SkillCatalog.ALL.stream().noneMatch(classic->classic.category().equals(entry.category())&&classic.id().equals(entry.id())),"extension cannot collide with a canonical skill "+entry.id());
            check(SkillAvailability.learnable(entry.id()),"real developer ingress "+entry.id());
            check(SkillCatalog.find("unsupported_test",entry.id()).isEmpty(),"unsupported category rejects additive identity "+entry.id());
        }
        var s=state(1);check(SkillCatalog.levelSkillCount(s)==2,"pack additions do not inflate classic level-one threshold");
        var arc=SkillCatalog.find(s.category,HighPowerSkills.FORKED_ARC).orElseThrow();
        var guard=SkillCatalog.find(s.category,HighPowerSkills.MAGNETIC_BRACER).orElseThrow();
        var decoy=SkillCatalog.find(s.category,HighPowerSkills.CHARGE_DECOY).orElseThrow();
        var tether=SkillCatalog.find(s.category,HighPowerSkills.POLARITY_TETHER).orElseThrow();
        var circuit=SkillCatalog.find(s.category,HighPowerSkills.CONDUCTIVE_CIRCUIT).orElseThrow();
        var grounding=SkillCatalog.find(s.category,HighPowerSkills.GROUNDING_RETURN).orElseThrow();
        check(circuit.level()==2&&grounding.level()==2,"NEW121/122 are exact LV2 registrations");
        check(tether.level()==1&&!DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,tether),"LV1 tether has an actual forked-arc prerequisite");
        check(!PresetSkills.selectable(s,tether.id()),"unlearned tether cannot enter a saved slot");
        check(DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,arc),"level-one portable root");
        check(!DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,guard),"bracer requires arc");
        check(!PresetSkills.selectable(s,arc.id()),"unlearned binding refused");
        var battery=new Battery(10000);var process=new DevelopmentProcess();process.start(battery,DevelopmentActions.skill(s,arc));
        for(int tick=0;tick<77;tick++)process.tick();check(!s.learned(arc.id()),"cannot learn before all paid native stimulation ticks");
        process.tick();check(process.state()==DevelopmentProcess.State.DONE&&s.learned(arc.id()),"paid learning completes");
        check(battery.energy==7660,"three real portable stimulations cost exactly 2340 IF");
        check(DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,tether),"learned zero-mastery arc unlocks provisional tether prerequisite");
        process.start(battery,DevelopmentActions.skill(s,tether));for(int tick=0;tick<77;tick++)process.tick();
        check(!s.learned(tether.id()),"tether waits for the final paid stimulation tick");process.tick();
        check(s.learned(tether.id())&&battery.energy==5320,"tether learns through three additional finite stimulations/2340 IF");
        check(SkillCatalog.levelSkillCount(s)==2,"fourth extension does not alter classic XP denominator");
        check(HighPowerSkills.ALL.get(0).id().equals("forked_arc")&&HighPowerSkills.ALL.get(1).id().equals("magnetic_bracer")&&HighPowerSkills.ALL.get(2).id().equals("charge_decoy"),"existing extension order and saved IDs remain unchanged");
        check(DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,guard),"no invented mastery threshold");
        var shortBattery=new Battery(100);process.start(shortBattery,DevelopmentActions.skill(s,guard));
        for(int tick=0;tick<4;tick++)process.tick();check(process.state()==DevelopmentProcess.State.FAILED&&!s.learned(guard.id())&&shortBattery.energy==0,"insufficient finite energy loses partial payment without learning");
        s.learn(guard.id());check(!DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,decoy),"LV2 decoy blocked at LV1");
        check(!DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,circuit)&&!DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,grounding),"learned prerequisites cannot bypass both new LV2 gates");
        s.setLevel(2);check(DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,decoy),"LV2 decoy available after bracer");
        s.learn(decoy.id());
        var lv2Battery=new Battery(10000);
        for(var lv2:List.of(circuit,grounding)){
            check(DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,lv2),"real LV2 and learned parent allow portable "+lv2.id());
            process.start(lv2Battery,DevelopmentActions.skill(s,lv2));for(int tick=0;tick<129;tick++)process.tick();
            check(!s.learned(lv2.id()),"no early LV2 learned bit "+lv2.id());process.tick();
            check(s.learned(lv2.id())&&process.state()==DevelopmentProcess.State.DONE,"five actual paid stimulations complete "+lv2.id());
        }
        check(lv2Battery.energy==2200,"NEW121/122 each debit3900IF, total7800IF");
        check(SkillCatalog.levelSkillCount(s)==2,"six extensions leave the original LV2 denominator unchanged");
        var groundStep=SkillCatalog.find(s.category,HighPowerSkills.MAGNETIC_GROUND_STEP).orElseThrow();
        check(groundStep.level()==3&&groundStep.requirements().size()==1&&groundStep.requirements().getFirst().id().equals(tether.id()),"NEW132 has exact integer LV3 and learned tether prerequisite");
        var stepState=state(2);stepState.learn(arc.id());stepState.learn(tether.id());
        stepState.levelExperience=cn.academy.port.core.ClassicRules.levelThreshold(2,SkillCatalog.levelSkillCount(stepState));
        check(!DevelopmentActions.canLearn(stepState,DeveloperType.NORMAL,groundStep),"continuous LV2 progress reaching3 cannot unlock integer LV3 skill");
        stepState.setLevel(3);
        check(!DevelopmentActions.canLearn(stepState,DeveloperType.PORTABLE,groundStep),"LV3 preserves classic Normal developer minimum");
        stepState.unlearn(tether.id());check(!DevelopmentActions.canLearn(stepState,DeveloperType.NORMAL,groundStep),"LV3 alone cannot bypass learned tether parent");
        stepState.learn(tether.id());check(DevelopmentActions.canLearn(stepState,DeveloperType.NORMAL,groundStep),"genuine LV3 and zero-mastery tether permit Normal learning");
        check(!PresetSkills.selectable(stepState,groundStep.id()),"unlearned NEW132 cannot bind even at LV3");
        var normalBattery=new Battery(50000,DeveloperType.NORMAL);process.start(normalBattery,DevelopmentActions.skill(stepState,groundStep));
        for(int tick=0;tick<146;tick++)process.tick();check(!stepState.learned(groundStep.id()),"NEW132 waits for all seven Normal stimulations");process.tick();
        check(stepState.learned(groundStep.id())&&process.state()==DevelopmentProcess.State.DONE&&normalBattery.energy==44855,"NEW132 pays exactly5145 IF over147 real developer ticks");
        check(SkillCatalog.levelSkillCount(stepState)==SkillCatalog.ALL.stream().filter(skill->skill.category().equals(stepState.category)&&skill.level()==stepState.level&&skill.controllable()).count(),"NEW132 leaves classic LV3 denominator untouched");
        check(stepState.presets.edit(0,3,groundStep.id(),id->PresetSkills.selectable(stepState,id)),"genuinely learned NEW132 reuses existing fourth slot");
        var savedStep=AbilityStorage.decodeSaved(AbilityStorage.encodeSaved(stepState),stepState.cpDataConfig());
        check(savedStep.learned(groundStep.id())&&savedStep.presets.currentSkill(3).equals(groundStep.id()),"NEW132 learned bit and fourth slot persist with existing schema");
        stepState.setLevel(2);check(!HighPowerSkills.eligible(stepState,groundStep.id())&&!PresetSkills.mappedUsable(stepState,groundStep.id()),"saved learned NEW132 cannot cast after integer downgrade");
        var lance=SkillCatalog.find(s.category,HighPowerSkills.IRON_SAND_LANCE).orElseThrow();
        check(lance.level()==3&&lance.requirements().size()==1&&lance.requirements().getFirst().id().equals(tether.id()),"NEW131 exact LV3 and provisional learned-tether prerequisite");
        var lanceState=state(2);lanceState.learn(arc.id());lanceState.learn(tether.id());
        lanceState.levelExperience=cn.academy.port.core.ClassicRules.levelThreshold(2,SkillCatalog.levelSkillCount(lanceState));
        check(!DevelopmentActions.canLearn(lanceState,DeveloperType.NORMAL,lance),"NEW131 continuous XP never bypasses integer LV3 unlock");
        lanceState.setLevel(3);check(!DevelopmentActions.canLearn(lanceState,DeveloperType.PORTABLE,lance),"NEW131 minimum Normal developer");
        check(!PresetSkills.selectable(lanceState,lance.id()),"NEW131 unlearned binding refused");
        var lanceBattery=new Battery(10000,DeveloperType.NORMAL);process.start(lanceBattery,DevelopmentActions.skill(lanceState,lance));
        for(int tick=0;tick<146;tick++)process.tick();check(!lanceState.learned(lance.id()),"NEW131 all147 paid ticks required");process.tick();
        check(lanceState.learned(lance.id())&&lanceBattery.energy==4855&&process.state()==DevelopmentProcess.State.DONE,"NEW131 genuine5145IF paid learning");
        check(PresetSkills.edit(lanceState,0,1,lance.id()),"NEW131 uses existing right mouse slot");
        var savedLance=AbilityStorage.decodeSaved(AbilityStorage.encodeSaved(lanceState),lanceState.cpDataConfig());
        check(savedLance.learned(lance.id())&&savedLance.presets.currentSkill(1).equals(lance.id()),"NEW131 learned/bound state roundtrips existing schema");
        check(SkillCatalog.levelSkillCount(lanceState)==SkillCatalog.ALL.stream().filter(k->k.category().equals("electromaster")&&k.level()==3&&k.controllable()&&SkillCatalog.enabled(k.category(),k.id())).count(),"NEW131 preserves classic LV3 XP denominator");
        lanceState.setLevel(2);check(!HighPowerSkills.eligible(lanceState,lance.id()),"NEW131 real downgrade disables cast without unlearning");
        var itemPull=SkillCatalog.find(s.category,HighPowerSkills.MAGNETIC_ITEM_PULL).orElseThrow();
        check(itemPull.level()==3&&itemPull.requirements().size()==1&&itemPull.requirements().getFirst().id().equals(tether.id()),"NEW133 exact LV3 learned-tether prerequisite");
        var pullState=state(2);pullState.learn(arc.id());pullState.learn(tether.id());
        pullState.levelExperience=cn.academy.port.core.ClassicRules.levelThreshold(2,SkillCatalog.levelSkillCount(pullState));
        check(!DevelopmentActions.canLearn(pullState,DeveloperType.NORMAL,itemPull),"NEW133 continuous growth never replaces real integer LV3");
        pullState.setLevel(3);check(!DevelopmentActions.canLearn(pullState,DeveloperType.PORTABLE,itemPull),"NEW133 requires Normal-or-better developer");
        pullState.unlearn(tether.id());check(!DevelopmentActions.canLearn(pullState,DeveloperType.NORMAL,itemPull),"NEW133 cannot skip learned tether");
        pullState.learn(tether.id());check(DevelopmentActions.canLearn(pullState,DeveloperType.NORMAL,itemPull)&&DevelopmentActions.canLearn(pullState,DeveloperType.ADVANCED,itemPull),"NEW133 standard developer learning gates");
        check(!PresetSkills.selectable(pullState,itemPull.id()),"NEW133 cannot bind before actual learning");
        var pullShort=new Battery(100,DeveloperType.NORMAL);process.start(pullShort,DevelopmentActions.skill(pullState,itemPull));
        for(int tick=0;tick<4;tick++)process.tick();check(!pullState.learned(itemPull.id())&&process.state()==DevelopmentProcess.State.FAILED,"NEW133 finite IF shortage never grants learned state");
        var pullBattery=new Battery(10000,DeveloperType.NORMAL);process.start(pullBattery,DevelopmentActions.skill(pullState,itemPull));
        for(int tick=0;tick<146;tick++)process.tick();check(!pullState.learned(itemPull.id()),"NEW133 no early stimulation completion");process.tick();
        check(pullState.learned(itemPull.id())&&process.state()==DevelopmentProcess.State.DONE&&pullBattery.energy==4855,"NEW133 seven actual Normal stimulations cost5145IF over147ticks");
        check(pullState.presets.edit(0,0,itemPull.id(),id->PresetSkills.selectable(pullState,id)),"NEW133 uses existing first slot");
        var savedPull=AbilityStorage.decodeSaved(AbilityStorage.encodeSaved(pullState),pullState.cpDataConfig());
        check(savedPull.learned(itemPull.id())&&savedPull.presets.currentSkill(0).equals(itemPull.id()),"NEW133 learned bit and slot survive existing storage format");
        check(!savedStep.learned(itemPull.id())&&savedStep.presets.currentSkill(3).equals(groundStep.id()),"pre-NEW133 save does not learn new skill or disturb old slot");
        check(SkillCatalog.levelSkillCount(pullState)==SkillCatalog.ALL.stream().filter(skill->skill.category().equals(pullState.category)&&skill.level()==3&&skill.controllable()).count(),"NEW133 preserves classic LV3 XP denominator");
        pullState.setLevel(2);check(!HighPowerSkills.eligible(pullState,itemPull.id())&&!PresetSkills.mappedUsable(pullState,itemPull.id()),"NEW133 saved learning cannot bypass later level downgrade");
        var thunderNet=SkillCatalog.find(s.category,HighPowerSkills.THUNDER_NET).orElseThrow();
        check(thunderNet.id().equals("thunder_net")&&HighPowerSkills.THUNDER_NET_PROPOSAL==141&&thunderNet.level()==4&&thunderNet.controllable(),"NEW141 has the canonical saved ID, proposal and exact integer LV4");
        check(thunderNet.requirements().size()==1&&thunderNet.requirements().getFirst().id().equals(circuit.id())&&thunderNet.requirements().getFirst().exp()==0,"NEW141 requires learned conductive circuit without an invented mastery threshold");
        var netState=state(3);netState.learn(arc.id());netState.learn(circuit.id());
        double lv3Threshold=cn.academy.port.core.ClassicRules.levelThreshold(3,SkillCatalog.levelSkillCount(netState));
        for(double progress:List.of(lv3Threshold*.999,lv3Threshold)){
            netState.levelExperience=progress;
            check(netState.level==3&&!DevelopmentActions.canLearn(netState,DeveloperType.ADVANCED,thunderNet)&&!SkillCatalog.canLearn(netState,thunderNet),"NEW141 near or complete LV3 XP cannot replace actual integer LV4");
        }
        netState.setLevel(4);
        check(!DevelopmentActions.canLearn(netState,DeveloperType.PORTABLE,thunderNet)&&!DevelopmentActions.canLearn(netState,DeveloperType.NORMAL,thunderNet),"NEW141 requires Advanced rather than Portable or Normal developer");
        netState.unlearn(circuit.id());
        check(!DevelopmentActions.canLearn(netState,DeveloperType.ADVANCED,thunderNet)&&!SkillCatalog.canLearn(netState,thunderNet),"NEW141 real LV4 alone cannot skip learned circuit");
        netState.learn(circuit.id());
        check(netState.exp(circuit.id())==0&&DevelopmentActions.canLearn(netState,DeveloperType.ADVANCED,thunderNet)&&SkillCatalog.canLearn(netState,thunderNet),"NEW141 genuine LV4 and zero-mastery learned circuit permit Advanced learning");
        check(!PresetSkills.selectable(netState,thunderNet.id())&&!PresetSkills.edit(netState,3,3,thunderNet.id())&&!HighPowerSkills.eligible(netState,thunderNet.id()),"NEW141 cannot bind or cast before paid learning");
        var netAction=DevelopmentActions.skill(netState,thunderNet);
        check(cn.academy.port.core.ClassicRules.learningStimulations(4)==11,"original ClassicRules API retains eleven LV4 learning stimulations");
        check(netAction.stimulations()==11&&DeveloperType.ADVANCED.ticksPerStimulation()==16&&DeveloperType.ADVANCED.energyPerTick()==40,"NEW141 preserves the actual classic LV4 eleven-stimulation and Advanced timing formulas");
        check(DeveloperType.ADVANCED.actualConsumption(netAction.stimulations())==7040,"NEW141 actual paid IF includes every native TPS+1 tick");
        check(DeveloperType.ADVANCED.estimatedConsumption(cn.academy.port.core.ClassicRules.learningStimulations(4))==6600,"ordinary classic LV4 skill retains its legacy 6600 IF estimate");
        String developerScreenSource=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cn/academy/port/client/ClassicDeveloperScreen.java"));
        int estimateStart=developerScreenSource.indexOf("private double estimated(Cover current)");
        String estimateSource=developerScreenSource.substring(estimateStart,developerScreenSource.indexOf("private record Condition",estimateStart));
        check(estimateSource.contains("int stimulations=current.kind.equals(\"skill\")?ClassicRules.learningStimulations(current.node.level()):current.levelAttempt.stimulations();"),"developer quote retains the actual skill count and captured classic level-attempt count");
        check(estimateSource.contains("if(current.kind.equals(\"skill\")&&cn.academy.port.highpower.HighPowerSkills.contains(current.node.id()))return developerType.actualConsumption(stimulations);")&&estimateSource.contains("return developerType.estimatedConsumption(stimulations);"),"only high-power skill quotes use actual 7040 IF while classic skill and level quotes retain legacy estimates");
        int startCoverStart=developerScreenSource.indexOf("private void startCover()");
        String startCoverSource=developerScreenSource.substring(startCoverStart,developerScreenSource.indexOf("current.requested=true",startCoverStart));
        check(developerScreenSource.contains("local(\"learn_question\",Long.toString(Math.round(estimated(current))))")&&startCoverSource.contains("if(energy()<estimated(current)){current.message=local(\"noenergy\");return;}"),"high-power learning preview and insufficient-IF refusal share the same corrected estimate");
        var netShort=new Battery(7039,DeveloperType.ADVANCED);process.start(netShort,netAction);
        for(int tick=0;tick<175;tick++)process.tick();
        check(!netState.learned(thunderNet.id())&&process.state()==DevelopmentProcess.State.DEVELOPING&&netShort.energy==39,"NEW141 one-IF-short battery reaches the final native tick without learning");
        process.tick();check(!netState.learned(thunderNet.id())&&process.state()==DevelopmentProcess.State.FAILED&&netShort.energy==0,"NEW141 insufficient final IF drains the finite remainder without granting learned state");
        var netBattery=new Battery(7040,DeveloperType.ADVANCED);process.start(netBattery,netAction);
        for(int tick=0;tick<175;tick++)process.tick();
        check(!netState.learned(thunderNet.id())&&process.state()==DevelopmentProcess.State.DEVELOPING&&netBattery.energy==40,"NEW141 cannot learn before all 176 paid native ticks");
        process.tick();check(netState.learned(thunderNet.id())&&process.state()==DevelopmentProcess.State.DONE&&netBattery.energy==0,"NEW141 completes eleven Advanced stimulations for exactly 7040 IF over 176 ticks");
        check(HighPowerSkills.eligible(netState,thunderNet.id())&&PresetSkills.selectable(netState,thunderNet.id()),"NEW141 learned LV4 skill is eligible through ordinary existing gates");
        check(PresetSkills.edit(netState,0,0,arc.id())&&PresetSkills.edit(netState,1,1,circuit.id())&&PresetSkills.edit(netState,3,3,thunderNet.id()),"NEW141 reuses an existing fourth-page fourth slot beside untouched old page mappings");
        var savedNet=AbilityStorage.decodeSaved(AbilityStorage.encodeSaved(netState),netState.cpDataConfig());
        check(savedNet.learned(thunderNet.id())&&savedNet.exp(thunderNet.id())==0&&savedNet.presets.skill(3,3).equals(thunderNet.id()),"NEW141 learned bit and canonical preset ID survive the existing save schema");
        check(savedNet.presets.current()==0&&savedNet.presets.skill(0,0).equals(arc.id())&&savedNet.presets.skill(1,1).equals(circuit.id()),"NEW141 save roundtrip preserves the active page and old learned mappings");
        check(!savedPull.learned(thunderNet.id())&&savedPull.presets.currentSkill(0).equals(itemPull.id()),"pre-NEW141 save neither learns the addition nor changes its existing slot");
        check(SkillCatalog.levelSkillCount(netState)==2&&SkillCatalog.levelSkillCount(netState)==SkillCatalog.ALL.stream().filter(skill->skill.category().equals(netState.category)&&skill.level()==4&&skill.controllable()&&SkillCatalog.enabled(skill.category(),skill.id())).count(),"NEW141 retains the two original classic LV4 skills as the entire XP denominator");
        check(cn.academy.port.core.ClassicRules.levelThreshold(4,SkillCatalog.levelSkillCount(netState))==cn.academy.port.core.ClassicRules.levelThreshold(4,2),"NEW141 cannot inflate the original classic LV4 advancement threshold");
        netState.setLevel(3);
        check(netState.learned(thunderNet.id())&&netState.presets.skill(3,3).equals(thunderNet.id())&&!HighPowerSkills.eligible(netState,thunderNet.id())&&!PresetSkills.mappedUsable(netState,thunderNet.id())&&!PresetSkills.selectable(netState,thunderNet.id()),"NEW141 stored learned bit and preset never bypass an actual level downgrade");
        for(String category:List.of("meltdowner","teleporter","vecmanip")){
            var wrongCategory=state(4);wrongCategory.learn(circuit.id());wrongCategory.learn(thunderNet.id());wrongCategory.category=category;
            check(!DevelopmentActions.canLearn(wrongCategory,DeveloperType.ADVANCED,thunderNet)&&!SkillCatalog.canLearn(wrongCategory,thunderNet)&&!HighPowerSkills.eligible(wrongCategory,thunderNet.id())&&!PresetSkills.selectable(wrongCategory,thunderNet.id()),"NEW141 stale learned state cannot cross the category gate "+category);
        }
        potentialBufferProgression(savedNet,estimateSource,startCoverSource,developerScreenSource);
        railThunderArrayProgression(savedNet,estimateSource,startCoverSource,developerScreenSource);
        magneticDomainProgression(savedNet,estimateSource,startCoverSource,developerScreenSource);
        var bindingState=s;
        for(int slot=0;slot<4;slot++){String id=HighPowerSkills.ALL.get(slot).id();check(s.presets.edit(0,slot,id,x->PresetSkills.selectable(bindingState,x)),"bind learned extension "+id);}
        check(s.presets.edit(1,0,circuit.id(),x->PresetSkills.selectable(bindingState,x))&&s.presets.edit(1,1,grounding.id(),x->PresetSkills.selectable(bindingState,x)),"new LV2 skills use another existing preset without adding keys");
        var saved=AbilityStorage.decodeSaved(AbilityStorage.encodeSaved(s),cn.academy.port.core.SkillConsumption.Config.DEFAULT);
        check(saved.experience.equals(s.experience),"extension learned/mastery roundtrip");
        for(int slot=0;slot<4;slot++)check(saved.presets.currentSkill(slot).equals(HighPowerSkills.ALL.get(slot).id()),"extension preset roundtrip "+slot);
        check(saved.presets.skill(1,0).equals(circuit.id())&&saved.presets.skill(1,1).equals(grounding.id())&&saved.presets.current()==0,"new alternate-page mappings persist without changing the active page");
        check(PresetSkills.available(s,0).stream().filter(v->HighPowerSkills.contains(v.id())).map(SkillCatalog.Skill::id).collect(java.util.stream.Collectors.toSet()).equals(java.util.Set.of(circuit.id(),grounding.id())),"four old bound IDs stay absent while the new unbound page-zero choices remain available");
        check(cn.academy.port.core.SkillPresets.MAX_KEYS==4&&cn.academy.port.core.SkillPresets.MAX_PRESETS==4,"no extra keys or preset pages invented");
        double levelXp=s.levelExperience;s.addExperience(arc.id(),.005);check(s.levelExperience>levelXp&&s.exp(arc.id())>0,"new skill mastery awards ordinary level progress");
        s.setLevel(1);check(HighPowerSkills.effectiveLevel(s,5)==1,"saved LV5 ledger cannot cast LV5 at real LV1");
        s.setLevel(0);check(HighPowerSkills.effectiveLevel(s,5)==0,"level-zero category cannot cast");
        s.changeCategoryClassic("teleporter");check(HighPowerSkills.effectiveLevel(s,5)==0,"another category cannot use old exact preset");
        s=state(1);var off=new ClassicSkillConfiguration.Flags(false,false);
        try{
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("electromaster",arc.id()),off));
            check(!DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,arc)&&!SkillCatalog.canLearn(s,arc),"disabled high-power skill rejected by both learning validators");
            check(!DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,guard),"disabled high-power predecessor cannot erase its dependency");
            s.learn(arc.id());check(!HighPowerSkills.eligible(s,arc.id())&&!PresetSkills.selectable(s,arc.id()),"disabled high-power cast and new binding refused");
            var snapshot=SkillCatalog.configurationSnapshot();SkillCatalog.resetConfiguration();check(SkillCatalog.applyConfigurationSnapshot(snapshot)&&!SkillCatalog.enabled("electromaster",arc.id()),"extension flags survive bounded network snapshot");
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("electromaster",tether.id()),off));
            check(!DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,tether),"disabled tether cannot start finite learning");
            s.learn(tether.id());check(!HighPowerSkills.eligible(s,tether.id())&&!PresetSkills.selectable(s,tether.id()),"disabled tether cannot activate or be newly bound");
        }finally{SkillCatalog.resetConfiguration();}
        netState.setLevel(4);
        try{
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("electromaster",thunderNet.id()),off));
            var unlearnedNet=state(4);unlearnedNet.learn(circuit.id());
            check(!DevelopmentActions.canLearn(unlearnedNet,DeveloperType.ADVANCED,thunderNet)&&!SkillCatalog.canLearn(unlearnedNet,thunderNet),"disabled NEW141 is rejected by both actual learning validators");
            check(!HighPowerSkills.eligible(netState,thunderNet.id())&&!PresetSkills.selectable(netState,thunderNet.id())&&!PresetSkills.edit(netState,2,0,thunderNet.id()),"disabled NEW141 refuses casting and new preset assignments");
            check(netState.learned(thunderNet.id())&&netState.presets.skill(3,3).equals(thunderNet.id()),"disabling NEW141 preserves stored learning and existing raw preset mapping");
            var netSnapshot=SkillCatalog.configurationSnapshot();SkillCatalog.resetConfiguration();
            check(SkillCatalog.applyConfigurationSnapshot(netSnapshot)&&!SkillCatalog.enabled("electromaster",thunderNet.id()),"NEW141 config gate survives the existing bounded network snapshot");
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("electromaster",circuit.id()),off));
            unlearnedNet.unlearn(circuit.id());
            check(!DevelopmentActions.canLearn(unlearnedNet,DeveloperType.ADVANCED,thunderNet)&&!SkillCatalog.canLearn(unlearnedNet,thunderNet),"disabled circuit never erases NEW141's learned prerequisite");
        }finally{SkillCatalog.resetConfiguration();}
        check(HighPowerSkills.eligible(netState,thunderNet.id())&&PresetSkills.selectable(netState,thunderNet.id()),"reenabling NEW141 restores normal learned LV4 eligibility");
        var retained=state(3);retained.learn(HighPowerSkills.MAGNETIC_GROUND_STEP);retained.learn(HighPowerSkills.MAGNETIC_ITEM_PULL);retained.learn("arc_gen");retained.learn("charging");
        check(PresetSkills.edit(retained,3,2,HighPowerSkills.MAGNETIC_GROUND_STEP)&&PresetSkills.edit(retained,3,3,"arc_gen"),"setup actual old fourth-page R=NEW132 mapping while enabled");
        try{
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("electromaster",HighPowerSkills.MAGNETIC_GROUND_STEP),off));
            long oldRevision=retained.presets.revision();
            check(PresetSkills.edit(retained,3,0,HighPowerSkills.MAGNETIC_ITEM_PULL),"learned NEW133 can fill empty LMB beside retained disabled132");
            check(retained.presets.skill(3,2).equals(HighPowerSkills.MAGNETIC_GROUND_STEP)&&retained.presets.skill(3,3).equals("arc_gen")&&retained.presets.revision()==oldRevision+1,"bounded edit preserves exact old R slot and unrelated classic slot");
            check(!PresetSkills.selectable(retained,HighPowerSkills.MAGNETIC_GROUND_STEP)&&!HighPowerSkills.eligible(retained,HighPowerSkills.MAGNETIC_GROUND_STEP),"retaining disabled132 never enables assignment or cast");
            oldRevision=retained.presets.revision();
            check(!PresetSkills.edit(retained,3,1,HighPowerSkills.MAGNETIC_GROUND_STEP)&&!PresetSkills.edit(retained,0,0,HighPowerSkills.MAGNETIC_GROUND_STEP),"cannot move/copy disabled132 to another slot or page");
            check(!PresetSkills.edit(retained,3,2,HighPowerSkills.MAGNETIC_GROUND_STEP)&&retained.presets.revision()==oldRevision,"requesting even the same disabled132 ID is rejected without mutation");
            check(!PresetSkills.edit(retained,3,1,HighPowerSkills.MAGNETIC_ITEM_PULL)&&retained.presets.revision()==oldRevision,"ordinary duplicate mapping rejection stays atomic");
            check(!PresetSkills.edit(retained,3,1,"railgun")&&retained.presets.revision()==oldRevision,"unlearned requested skill never inherits retained132 exception");
            check(PresetSkills.edit(retained,3,2,"charging")&&retained.presets.skill(3,2).equals("charging"),"explicit user replacement removes disabled132 without clearing unrelated slots");
            check(!PresetSkills.edit(retained,3,1,HighPowerSkills.MAGNETIC_GROUND_STEP),"removed disabled132 cannot be assigned back");
            SkillCatalog.resetConfiguration();
            check(PresetSkills.edit(retained,3,2,HighPowerSkills.MAGNETIC_GROUND_STEP),"setup second explicit132 mapping before gate");
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("electromaster",HighPowerSkills.MAGNETIC_GROUND_STEP),off));
            check(PresetSkills.edit(retained,3,2,"")&&retained.presets.skill(3,2).isEmpty(),"explicit clear removes disabled132");
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("electromaster","arc_gen"),off));
            oldRevision=retained.presets.revision();
            check(!PresetSkills.edit(retained,3,1,"charging")&&retained.presets.revision()==oldRevision,"disabled classic slot still blocks unrelated edits under unchanged classic semantics");
            check(PresetSkills.edit(retained,3,3,"")&&retained.presets.skill(3,3).isEmpty(),"explicit clear of disabled classic slot keeps existing behavior");
            SkillCatalog.resetConfiguration();PresetSkills.edit(retained,3,2,HighPowerSkills.MAGNETIC_GROUND_STEP);retained.unlearn(HighPowerSkills.MAGNETIC_GROUND_STEP);
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("electromaster",HighPowerSkills.MAGNETIC_GROUND_STEP),off));
            check(!PresetSkills.edit(retained,3,1,"charging"),"disabled132 exception does not bypass missing learned state");
        }finally{SkillCatalog.resetConfiguration();}
        String presetClientSource=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        String presetServerSource=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cn/academy/port/AcademyGameplay.java"));
        check(presetClientSource.contains("PresetSkills.edit(state,preset,slot,skill)")&&presetServerSource.contains("PresetSkills.edit(state,preset,slot,fields[2])"),"actual client and server preset edit ingress share bounded migration validation");
        for(String category:List.of("unsupported_test"))check(HighPowerDeveloperLayout.category(category).isEmpty(),"no foreign tree additions "+category);
        var nodes=HighPowerDeveloperLayout.category("electromaster");check(nodes.size()==13,"thirteen additive tree nodes");
        var fresh=state(1);check(nodes.stream().filter(n->fresh.level>=n.level()||n.parent().isEmpty()||fresh.learned(n.parent())).count()==3,"fresh LV1 displays exactly the three LV1 additions");
        var lanceNode=nodes.stream().filter(n->n.id().equals(lance.id())).findFirst().orElseThrow();
        check(lanceNode.index()==131&&lanceNode.level()==3&&lanceNode.parent().equals(tether.id()),"NEW131 exact proposal/parent in real tree");
        for(var other:ClassicDeveloperLayout.category("electromaster"))if(!other.id().equals(lance.id()))
            check(Math.hypot(lanceNode.x()-other.x(),lanceNode.y()-other.y())>=28,"NEW131 distinct14px icon and16px selection boxes in dense canonical tree "+other.id());
        check(lanceNode.x()+8-18.6>=0&&lanceNode.x()+8+18.6<=257&&lanceNode.y()+8-18.6>=0&&lanceNode.y()+8+18.6<=139,"NEW131 full hover stays inside original tree");
        try(var original=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/electromaster/skills/iron_sand.png");var reused=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/electromaster/skills/iron_sand_lance.png")){
            check(java.util.Arrays.equals(original.readAllBytes(),reused.readAllBytes()),"NEW131 byte-identical classic iron-sand icon attribution preserved");
        }
        var tetherNode=nodes.stream().filter(node->node.id().equals(tether.id())).findFirst().orElseThrow();
        check(tetherNode.index()==113&&tetherNode.level()==1&&tetherNode.parent().equals(arc.id()),"tether tree preserves authoritative proposal113 and provisional parent");
        for(var other:ClassicDeveloperLayout.category("electromaster"))if(!other.id().equals(tether.id()))
            check(Math.hypot(tetherNode.x()-other.x(),tetherNode.y()-other.y())>=35,"tether node outline does not overlap existing node "+other.id());
        for(String id:List.of(circuit.id(),grounding.id())){
            var node=nodes.stream().filter(n->n.id().equals(id)).findFirst().orElseThrow();
            check(node.level()==2&&node.index()==(id.equals(circuit.id())?121:122),"authoritative LV2 proposal identity "+id);
            check(node.parent().equals(id.equals(circuit.id())?arc.id():guard.id()),"documented provisional learned parent "+id);
            for(var other:ClassicDeveloperLayout.category("electromaster"))if(!other.id().equals(id))check(Math.hypot(node.x()-other.x(),node.y()-other.y())>=35,"LV2 node outline does not overlap "+id+"/"+other.id());
            check(node.x()+8-18.6>=0&&node.x()+8+18.6<=257&&node.y()+8-18.6>=0&&node.y()+8+18.6<=139,"full hover outline remains inside tree area "+id);
        }
        var stepNode=nodes.stream().filter(node->node.id().equals(groundStep.id())).findFirst().orElseThrow();
        check(stepNode.index()==132&&stepNode.level()==3&&stepNode.parent().equals(tether.id()),"NEW132 tree preserves exact proposal and parent");
        var stepTier=stepNode.conditions().stream().filter(condition->condition.get("type").getAsString().equals("developer_type")).findFirst().orElseThrow();
        check(stepTier.get("minimum").getAsString().equals("normal")&&stepTier.get("hintKey").getAsString().equals("ac.skill_tree.type_normal"),"NEW132 tree advertises actual Normal learning requirement");
        for(var other:ClassicDeveloperLayout.category("electromaster"))if(!other.id().equals(stepNode.id()))check(Math.hypot(stepNode.x()-other.x(),stepNode.y()-other.y())>=35,"NEW132 outline does not overlap "+other.id());
        check(stepNode.x()+8-18.6>=0&&stepNode.x()+8+18.6<=257&&stepNode.y()+8-18.6>=0&&stepNode.y()+8+18.6<=139,"NEW132 full hover outline stays inside existing tree area");
        try(var original=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/electromaster/skills/mag_movement.png");var reused=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/electromaster/skills/magnetic_ground_step.png")){
            check(java.util.Arrays.equals(original.readAllBytes(),reused.readAllBytes()),"NEW132 reuses actual classic magnetic movement pixels");
        }
        var pullNode=nodes.stream().filter(node->node.id().equals(itemPull.id())).findFirst().orElseThrow();
        check(pullNode.index()==133&&pullNode.level()==3&&pullNode.parent().equals(tether.id()),"NEW133 authoritative proposal and provisional parent retained");
        var pullTier=pullNode.conditions().stream().filter(condition->condition.get("type").getAsString().equals("developer_type")).findFirst().orElseThrow();
        check(pullTier.get("minimum").getAsString().equals("normal")&&pullTier.get("hintKey").getAsString().equals("ac.skill_tree.type_normal"),"NEW133 honest Normal developer condition");
        for(var other:ClassicDeveloperLayout.category("electromaster"))if(!other.id().equals(pullNode.id()))check(Math.hypot(pullNode.x()-other.x(),pullNode.y()-other.y())>=35,"NEW133 full outline does not overlap "+other.id());
        check(pullNode.x()+8-18.6>=0&&pullNode.x()+8+18.6<=257&&pullNode.y()+8-18.6>=0&&pullNode.y()+8+18.6<=139,"NEW133 full hover outline stays inside original tree");
        try(var original=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/electromaster/skills/mag_manip.png");var reused=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/electromaster/skills/magnetic_item_pull.png")){
            check(java.util.Arrays.equals(original.readAllBytes(),reused.readAllBytes()),"NEW133 reuses actual classic magnetic manipulation icon");
        }
        var netNode=nodes.stream().filter(node->node.id().equals(thunderNet.id())).findFirst().orElseThrow();
        check(netNode.index()==141&&netNode.level()==4&&netNode.x()==77&&netNode.y()==38&&netNode.parent().equals(circuit.id()),"NEW141 authoritative proposal, actual LV4 and learned circuit parent occupy the additive tree position");
        check(netNode.nameKey().equals("ac.ability.electromaster.thunder_net.name")&&netNode.descriptionKey().equals("ac.ability.electromaster.thunder_net.desc"),"NEW141 actual tree uses canonical localization keys");
        var netTier=netNode.conditions().stream().filter(condition->condition.get("type").getAsString().equals("developer_type")).findFirst().orElseThrow();
        check(netTier.get("minimum").getAsString().equals("advanced")&&netTier.get("hintKey").getAsString().equals("ac.skill_tree.type_advanced"),"NEW141 advertises its real Advanced developer requirement");
        var netParent=netNode.conditions().stream().filter(condition->condition.get("type").getAsString().equals("skill_dependency")).findFirst().orElseThrow();
        check(netParent.get("id").getAsString().equals(circuit.id())&&netParent.get("minimum_exp").getAsDouble()==0,"NEW141 visible dependency matches zero-mastery learned circuit");
        for(var other:ClassicDeveloperLayout.category("electromaster"))if(!other.id().equals(netNode.id())){
            check(Math.hypot(netNode.x()-other.x(),netNode.y()-other.y())>=28,"NEW141 distinct 14px icons and 16px selection boxes retain at least 28px spacing "+other.id());
            check(Math.abs(netNode.x()-other.x())>=16||Math.abs(netNode.y()-other.y())>=16,"NEW141 selection boxes do not intersect "+other.id());
        }
        check(netNode.x()+8-18.6>=0&&netNode.x()+8+18.6<=257&&netNode.y()+8-18.6>=0&&netNode.y()+8+18.6<=139,"NEW141 full hover outline stays inside the original 257x139 tree");
        try(var original=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/electromaster/skills/thunder_bolt.png");var reused=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/electromaster/skills/thunder_net.png")){
            check(original!=null&&reused!=null&&java.util.Arrays.equals(original.readAllBytes(),reused.readAllBytes()),"NEW141 retains byte-identical classic thunder-bolt icon pixels and attribution");
        }
        var bufferNode=nodes.stream().filter(node->node.id().equals(HighPowerSkills.POTENTIAL_BUFFER)).findFirst().orElseThrow();
        check(bufferNode.index()==142&&bufferNode.level()==4&&bufferNode.x()==230&&bufferNode.y()==94&&bufferNode.parent().equals(grounding.id()),"NEW142 authoritative proposal, actual LV4 and grounding-return parent occupy the new additive position");
        check(bufferNode.nameKey().equals("ac.ability.electromaster.potential_buffer.name")&&bufferNode.descriptionKey().equals("ac.ability.electromaster.potential_buffer.desc"),"NEW142 tree uses canonical localized name and description keys");
        var bufferTier=bufferNode.conditions().stream().filter(condition->condition.get("type").getAsString().equals("developer_type")).findFirst().orElseThrow();
        check(bufferTier.get("minimum").getAsString().equals("advanced")&&bufferTier.get("hintKey").getAsString().equals("ac.skill_tree.type_advanced"),"NEW142 visibly advertises its actual Advanced-only developer condition");
        var bufferParent=bufferNode.conditions().stream().filter(condition->condition.get("type").getAsString().equals("skill_dependency")).findFirst().orElseThrow();
        check(bufferParent.get("id").getAsString().equals(grounding.id())&&bufferParent.get("minimum_exp").getAsDouble()==0,"NEW142 tree advertises learned grounding return without a hidden mastery threshold");
        for(var other:ClassicDeveloperLayout.category("electromaster"))if(!other.id().equals(bufferNode.id())){
            check(Math.hypot(bufferNode.x()-other.x(),bufferNode.y()-other.y())>=28,"NEW142 distinct 14px icons and 16px selection boxes retain at least 28px spacing "+other.id());
            check(Math.abs(bufferNode.x()-other.x())>=16||Math.abs(bufferNode.y()-other.y())>=16,"NEW142 selection boxes never intersect another tree node "+other.id());
        }
        check(bufferNode.x()+8-18.6>=0&&bufferNode.x()+8+18.6<=257&&bufferNode.y()+8-18.6>=0&&bufferNode.y()+8+18.6<=139,"NEW142 full hover outline stays inside the original 257x139 tree");
        try(var original=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/electromaster/skills/body_intensify.png");var reused=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/electromaster/skills/potential_buffer.png")){
            check(original!=null&&reused!=null&&java.util.Arrays.equals(original.readAllBytes(),reused.readAllBytes()),"NEW142 retains byte-identical classic body-intensify defensive icon pixels and attribution");
        }
        railThunderArrayLayout(nodes);
        magneticDomainLayout(nodes);
        dualPointProgression();
        dualPointLayout();
        scatterPlatesProgression();
        scatterPlatesLayout();
        beamEtchingProgression();
        beamEtchingLayout();
        thermalShellProgression();
        foldedMirrorProgression();
        foldedMirrorLayout();
        disintegrationSweepProgression();
        disintegrationSweepLayout();
        meltdownerPageNavigation();
        beamStepLayout();
        zeroGapArmorLayout();
        nativeDevelopmentFeedback();
        for(var node:java.util.stream.Stream.concat(nodes.stream(),HighPowerDeveloperLayout.category("meltdowner").stream()).toList()){
            check(node.x()>=0&&node.x()<257&&node.y()>=0&&node.y()<139,"extension node within existing tree area");
            check(HighPowerProgressionRegressionTest.class.getResource("/assets/academy/"+node.hintIcon().substring("academy:".length()))!=null,"real icon asset "+node.id());
            for(String lang:List.of("en_us","zh_cn","zh_tw","ja_jp"))try(var reader=new InputStreamReader(HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/lang/"+lang+".json"),StandardCharsets.UTF_8)){
                var dictionary=JsonParser.parseReader(reader).getAsJsonObject();check(dictionary.has(node.nameKey())&&dictionary.has(node.descriptionKey()),"localized extension "+node.id()+"/"+lang);
                if(node.id().equals(HighPowerSkills.THUNDER_NET))check(!dictionary.get(node.nameKey()).getAsString().isBlank()&&!dictionary.get(node.descriptionKey()).getAsString().isBlank(),"NEW141 canonical name and description are nonblank in "+lang);
                if(node.id().equals(HighPowerSkills.POTENTIAL_BUFFER))check(!dictionary.get(node.nameKey()).getAsString().isBlank()&&!dictionary.get(node.descriptionKey()).getAsString().isBlank(),"NEW142 canonical name and description are nonblank in "+lang);
                if(node.id().equals(HighPowerSkills.RAIL_THUNDER_ARRAY))check(!dictionary.get(node.nameKey()).getAsString().isBlank()&&!dictionary.get(node.descriptionKey()).getAsString().isBlank(),"NEW151 canonical name and description are nonblank in "+lang);
                if(node.id().equals(HighPowerSkills.MAGNETIC_DOMAIN))check(!dictionary.get(node.nameKey()).getAsString().isBlank()&&!dictionary.get(node.descriptionKey()).getAsString().isBlank(),"NEW152 canonical name and description are nonblank in "+lang);
                if(node.id().equals(HighPowerSkills.DUAL_POINT)||node.id().equals(HighPowerSkills.SCATTER_PLATES)||node.id().equals(HighPowerSkills.BEAM_ETCHING)||node.id().equals(HighPowerSkills.FOLDED_MIRROR_ARRAY))check(!dictionary.get(node.nameKey()).getAsString().isBlank()&&!dictionary.get(node.descriptionKey()).getAsString().isBlank(),"MD addition canonical name and description are nonblank in "+lang);
                if(node.id().equals(HighPowerSkills.MAGNETIC_ITEM_PULL))for(String key:List.of("select","candidates","release","empty_selection","no_owned_drops","nonmagnetic","blocked","unavailable","cannot_start","selection_expired"))check(dictionary.has("academy.highpower.item_pull."+key)&&!dictionary.get("academy.highpower.item_pull."+key).getAsString().isBlank(),"NEW133 honest candidate and rejection translation "+key+"/"+lang);
                if(node.id().equals(HighPowerSkills.MAGNETIC_GROUND_STEP))for(String reason:List.of("autojump","settling","ground","input","unsupported"))check(dictionary.has("academy.highpower.ground_step."+reason)&&!dictionary.get("academy.highpower.ground_step."+reason).getAsString().isBlank(),"explicit local preflight refusal translated "+reason+"/"+lang);
            }
        }
        for(String malformed:List.of("", "0:1:1", "0:1:1:1:1", "4:1:1:1", "0:0:1:1", "0:1:-1:1", "0:1:1:0", "00:1:1:1", "0:+1:1:1", "0:1:1:01", "0:99999999999999999999:1:1"))check(HighPowerInput.parse(malformed)==null,"bounded canonical wire rejects "+malformed);
        var input=new HighPowerInput.Input(3,Long.MAX_VALUE,Integer.MAX_VALUE,Long.MAX_VALUE);
        check(input.wire().length()<=64&&input.equals(HighPowerInput.parse(input.wire())),"maximum input remains within existing payload budget");
        var shifted=state(1);shifted.bindProgress(id->shifted.changeCategoryClassic("teleporter"),level->{});
        shifted.addExperience(HighPowerSkills.FORKED_ARC,.005);check(shifted.experience.isEmpty()&&shifted.levelExperience==0,"extension award cannot leak a nonexistent classic ordinal after category observer mutation");
        var learning=new HighPowerLearningProtocol.Request(HighPowerSkills.FORKED_ARC,1);
        check(learning.equals(HighPowerLearningProtocol.parse(learning.wire())),"canonical high-power learning request");
        for(String id:HighPowerSkills.IDS){var maximum=new HighPowerLearningProtocol.Request(id,Integer.MAX_VALUE);
            check(("00000000-0000-0000-0000-000000000000:"+maximum.wire()).length()<=64,"machine token plus compact max learning sequence fits original bound "+id);
            check(maximum.equals(HighPowerLearningProtocol.parse(maximum.wire())),"compact max sequence roundtrip "+id);
        }
        var projectionLearning=new HighPowerLearningProtocol.Request(HighPowerSkills.DISPLACED_PROJECTION,Integer.MAX_VALUE);
        check(projectionLearning.wire().startsWith("dp:~")&&projectionLearning.equals(HighPowerLearningProtocol.parse(projectionLearning.wire()))
                &&HighPowerLearningProtocol.parse("displaced_projection:1").skill().equals(HighPowerSkills.DISPLACED_PROJECTION),
                "NEW311 machine wire compacts only transport and preserves canonical saved and legacy numeric IDs");
        var itemPullLearning=new HighPowerLearningProtocol.Request(HighPowerSkills.MAGNETIC_ITEM_PULL,Integer.MAX_VALUE);
        check(("00000000-0000-0000-0000-000000000000:"+itemPullLearning.wire()).length()==63&&itemPullLearning.equals(HighPowerLearningProtocol.parse(itemPullLearning.wire())),"NEW133 full canonical name and maximum machine request fit63characters within64byte bound without alias");
        var netLearning=new HighPowerLearningProtocol.Request(HighPowerSkills.THUNDER_NET,Integer.MAX_VALUE);
        String netMachineLearning="00000000-0000-0000-0000-000000000000:"+netLearning.wire();
        check(netLearning.wire().equals("thunder_net:~zik0zj")&&netMachineLearning.length()==56&&netMachineLearning.getBytes(StandardCharsets.UTF_8).length<=64&&netLearning.equals(HighPowerLearningProtocol.parse(netLearning.wire())),"NEW141 canonical learning ID and maximum sequence fit the existing 64-byte machine request budget without alias");
        var bufferLearning=new HighPowerLearningProtocol.Request(HighPowerSkills.POTENTIAL_BUFFER,Integer.MAX_VALUE);
        String bufferMachineLearning="00000000-0000-0000-0000-000000000000:"+bufferLearning.wire();
        check(bufferLearning.wire().equals("potential_buffer:~zik0zj")&&bufferMachineLearning.length()==61&&bufferMachineLearning.getBytes(StandardCharsets.UTF_8).length<=64&&bufferLearning.equals(HighPowerLearningProtocol.parse(bufferLearning.wire())),"NEW142 canonical learning ID and maximum sequence fit the unchanged 64-byte machine request budget without alias");
        var arrayLearning=new HighPowerLearningProtocol.Request(HighPowerSkills.RAIL_THUNDER_ARRAY,Integer.MAX_VALUE);
        String arrayMachineLearning="00000000-0000-0000-0000-000000000000:"+arrayLearning.wire();
        check(arrayLearning.wire().equals("rail_thunder_array:~zik0zj")&&arrayMachineLearning.length()==63&&arrayMachineLearning.getBytes(StandardCharsets.UTF_8).length<=64&&arrayLearning.equals(HighPowerLearningProtocol.parse(arrayLearning.wire())),"NEW151 canonical learning ID and maximum sequence fit the unchanged 64-byte machine request budget without alias");
        var domainLearning=new HighPowerLearningProtocol.Request(HighPowerSkills.MAGNETIC_DOMAIN,Integer.MAX_VALUE);
        String domainMachineLearning="00000000-0000-0000-0000-000000000000:"+domainLearning.wire();
        check(domainLearning.wire().equals("magnetic_domain:~zik0zj")&&domainMachineLearning.length()==60&&domainMachineLearning.getBytes(StandardCharsets.UTF_8).length<=64&&domainLearning.equals(HighPowerLearningProtocol.parse(domainLearning.wire())),"NEW152 full canonical learning ID and maximum sequence fit the unchanged 64-byte machine request without an alias");
        var groundStepLearning=new HighPowerLearningProtocol.Request(HighPowerSkills.MAGNETIC_GROUND_STEP,Integer.MAX_VALUE);
        check(groundStepLearning.wire().equals("mgs:~zik0zj")&&HighPowerLearningProtocol.parse(groundStepLearning.wire()).skill().equals(HighPowerSkills.MAGNETIC_GROUND_STEP),"NEW132 compact wire alias restores canonical skill identity");
        check(HighPowerLearningProtocol.parse("magnetic_ground_step:132").skill().equals(HighPowerSkills.MAGNETIC_GROUND_STEP),"NEW132 full canonical ID remains accepted in explicit legacy-decimal input");
        for(String bad:List.of("mgs:~ZIK0ZJ","mgs:~0","mgs:132","mgs:~zik0zk","magnetic_ground_step:~zik0zj"))check(HighPowerLearningProtocol.parse(bad)==null,"NEW132 rejects noncanonical or overflowing compact alias "+bad);
        check(HighPowerLearningProtocol.parse("conductive_circuit:~ZIK0ZJ")==null,"noncanonical uppercase compact sequence refused");
        check(HighPowerLearningProtocol.parse("forked_arc:10").sequence()==10,"legacy decimal10 retains sequence10 rather than base36 reinterpretation");
        check(HighPowerLearningProtocol.parse("forked_arc:~a").sequence()==10,"explicit compact prefix represents the same integer10");
        check(HighPowerLearningProtocol.parse("forked_arc:~10").sequence()==36,"compact36 remains distinct from legacy decimal10");
        for(String invalid:List.of("forked_arc:~","forked_arc:~01","forked_arc:~+1","forked_arc:~-1","forked_arc:+1","forked_arc:~zik0zk"))check(HighPowerLearningProtocol.parse(invalid)==null,"noncanonical or overflowing compact/legacy sequence refused "+invalid);
        var legacyMaximum=HighPowerLearningProtocol.parse("forked_arc:"+Integer.MAX_VALUE);
        check(legacyMaximum!=null&&legacyMaximum.sequence()==Integer.MAX_VALUE&&legacyMaximum.equals(HighPowerLearningProtocol.parse(legacyMaximum.wire())),"legacy maximum roundtrips through explicit compact format");
        for(String bad:List.of("forked_arc","forked_arc:0","forked_arc:01","forked_arc:-1","forked_arc:2147483648","arc_gen:1","forked_arc:1:2"))check(HighPowerLearningProtocol.parse(bad)==null,"malformed learning correlation rejected "+bad);
        var feedback=new HighPowerLearningProtocol.Pending();feedback.begin(learning);
        var rejection=HighPowerLearningProtocol.result(learning,false,"This older ledger needs explicit vanilla damage-contract approval");
        check(feedback.receive(rejection)&&!feedback.waiting()&&!feedback.accepted()&&feedback.reason().contains("approval"),"early rejection immediately clears waiting and preserves exact modal reason");
        check(!feedback.receive(rejection),"duplicate failure cannot consume an ended attempt");
        var retry=new HighPowerLearningProtocol.Request(HighPowerSkills.FORKED_ARC,2);feedback.begin(retry);
        check(!feedback.receive(rejection)&&feedback.waiting(),"late old failure cannot dismiss a retry");
        check(!feedback.receive(HighPowerLearningProtocol.result(new HighPowerLearningProtocol.Request(HighPowerSkills.MAGNETIC_BRACER,2),false,"wrong skill")),"same sequence for different skill cannot consume modal");
        check(feedback.receive(HighPowerLearningProtocol.result(retry,true,""))&&feedback.accepted()&&!feedback.waiting(),"successful retry confirms actual server start");
        feedback.begin(new HighPowerLearningProtocol.Request(HighPowerSkills.FORKED_ARC,3));feedback.cancel();
        check(!feedback.receive(HighPowerLearningProtocol.result(new HighPowerLearningProtocol.Request(HighPowerSkills.FORKED_ARC,3),false,"late")),"closed or timed-out attempt ignores late feedback");
        var tetherInput=new cn.academy.port.client.PolarityTetherInputTracker();
        var tapOne=new HighPowerInput.Input(3,10,42,7);var tapTwo=new HighPowerInput.Input(3,11,42,7);
        check(tetherInput.cancelWhenBlocked(true)==null,"screen transition without emitted tether input sends nothing");
        tetherInput.remember(tapOne);check(tetherInput.cancelWhenBlocked(false)==null,"normal focused gameplay retains the persistent tether input");
        tetherInput.remember(tapTwo);check(tapTwo.equals(tetherInput.cancelWhenBlocked(true)),"screen or focus loss emits the latest bounded actor/slot/epoch input");
        for(int frame=0;frame<20;frame++)check(tetherInput.cancelWhenBlocked(true)==null,"blocked frames do not repeat cancellation "+frame);
        tetherInput.remember(new HighPowerInput.Input(3,12,42,7));check(tetherInput.cancel()!=null&&tetherInput.cancel()==null,"V or preset cancellation is also one-shot");
        tetherInput.remember(new HighPowerInput.Input(3,13,42,7));tetherInput.reset();check(tetherInput.cancel()==null,"world/session replacement drops old input without sending a stale cancel");
        var circuitInput=new cn.academy.port.client.PolarityTetherInputTracker();var groundingInput=new cn.academy.port.client.PolarityTetherInputTracker();
        circuitInput.remember(new HighPowerInput.Input(2,121,42,7));groundingInput.remember(new HighPowerInput.Input(3,122,42,7));
        check(circuitInput.cancelWhenBlocked(true).slot()==2&&circuitInput.cancelWhenBlocked(true)==null,"circuit focus cancellation consumes only its own tracker once");
        check(groundingInput.cancelWhenBlocked(false)==null&&groundingInput.cancelWhenBlocked(true).slot()==3,"independent grounding input survives circuit cancellation until its own cancel");
        check(groundingInput.cancelWhenBlocked(true)==null,"grounding blocked frames do not repeat packets");
        var groundStepInput=new cn.academy.port.client.PolarityTetherInputTracker();
        var dashPress=new HighPowerInput.Input(3,132,42,7);groundStepInput.remember(dashPress);
        check(groundStepInput.cancelWhenBlocked(false)==null,"NEW132 press retains captured identity independent of later sprint or crouch changes");
        circuitInput.remember(new HighPowerInput.Input(2,133,42,7));circuitInput.cancel();
        check(dashPress.equals(groundStepInput.cancelWhenBlocked(true)),"NEW132 screen/focus cancellation is scoped independently of other high-power trackers");
        check(groundStepInput.cancelWhenBlocked(true)==null,"NEW132 blocked frames cannot repeat cancellation");
        groundStepInput.remember(dashPress);check(dashPress.equals(groundStepInput.cancel())&&groundStepInput.cancel()==null,"NEW132 remap or V cancels once");
        groundStepInput.remember(dashPress);groundStepInput.reset();check(groundStepInput.cancel()==null,"NEW132 world or owner replacement drops stale wire identity");
        String clientSource=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        int refusalStart=clientSource.indexOf("private static boolean groundStepStartAllowed()");
        String refusal=clientSource.substring(refusalStart,clientSource.indexOf("private static void cancelGroundStepLocally",refusalStart));
        check(refusal.contains("Component.translatable(reason),false)")&&!refusal.contains("Component.translatable(reason),true)"),"NEW132 localized refusal uses persistent ordinary chat instead of HUD-owned actionbar");
        check(refusal.contains("time-groundStepBlockerAt>=2000")&&refusal.contains("!reason.equals(groundStepBlocker)"),"NEW132 ordinary-chat refusal retains per-reason two-second throttle");
        System.out.println("PASS "+checks+" additive high-power progression, finite learning, preset, layout, config and input checks");
    }

    private static void thermalShellProgression()throws Exception{
        var skill=SkillCatalog.find("meltdowner",HighPowerSkills.THERMAL_SHELL).orElseThrow();
        check(skill.id().equals("thermal_shell")&&skill.level()==2&&skill.controllable()&&HighPowerSkills.THERMAL_SHELL_PROPOSAL==222,"NEW222 retains its exact MD LV2 saved identity and proposal");
        check(skill.requirements().equals(List.of(new SkillCatalog.Requirement(HighPowerSkills.SCATTER_PLATES,0)))&&skill.anyLearnedSkillLevel()==0,"NEW222 requires exactly learned zero-mastery scatter plates");
        var md=new AbilityProgress();md.changeCategoryClassic("meltdowner");md.setActivateState(true);
        md.learn(HighPowerSkills.DUAL_POINT);md.learn(HighPowerSkills.SCATTER_PLATES);
        md.levelExperience=cn.academy.port.core.ClassicRules.levelThreshold(1,SkillCatalog.levelSkillCount(md));
        check(!DevelopmentActions.canLearn(md,DeveloperType.PORTABLE,skill),"continuous growth cannot bypass NEW222 integer LV2");
        md.setLevel(2);md.unlearn(HighPowerSkills.SCATTER_PLATES);md.learn("light_shield");
        check(!DevelopmentActions.canLearn(md,DeveloperType.ADVANCED,skill),"classic shield and NEW211 cannot substitute for NEW212 prerequisite");
        md.learn(HighPowerSkills.SCATTER_PLATES);
        check(DevelopmentActions.canLearn(md,DeveloperType.PORTABLE,skill)&&md.exp(HighPowerSkills.SCATTER_PLATES)==0,"zero-mastery learned NEW212 permits Portable LV2 learning");
        check(!PresetSkills.edit(md,0,0,skill.id())&&!HighPowerSkills.eligible(md,skill.id()),"unlearned NEW222 cannot bind or cast");
        var action=DevelopmentActions.skill(md,skill);var process=new DevelopmentProcess();
        check(action.stimulations()==5&&DeveloperType.PORTABLE.actualConsumption(action.stimulations())==3900,"NEW222 uses five native stimulations and 3900 actual IF");
        var before=AbilityStorage.encodeSaved(md);var shortBattery=new Battery(3899);
        process.start(shortBattery,action);for(int tick=0;tick<129;tick++)process.tick();
        check(!md.learned(skill.id())&&shortBattery.energy==29&&AbilityStorage.encodeSaved(md).equals(before),"NEW222 no early grant on final-tick shortage");
        process.tick();check(process.state()==DevelopmentProcess.State.FAILED&&shortBattery.energy==0&&AbilityStorage.encodeSaved(md).equals(before),"NEW222 one-IF shortage preserves abilities with no refund");
        var paid=new Battery(3900);double xp=md.levelExperience;process.start(paid,action);
        for(int tick=0;tick<129;tick++)process.tick();check(!md.learned(skill.id())&&paid.energy==30,"NEW222 waits for all 130 paid ticks");
        process.tick();check(process.state()==DevelopmentProcess.State.DONE&&paid.energy==0&&md.learned(skill.id())&&md.exp(skill.id())==0&&md.levelExperience==xp,"NEW222 exact finite learning grants no mastery or level XP");
        for(int preset=0;preset<4;preset++)for(int slot=0;slot<4;slot++){
            check(PresetSkills.edit(md,preset,slot,skill.id()),"NEW222 binds each ordinary slot "+preset+"/"+slot);
            check(!PresetSkills.edit(md,preset,(slot+1)%4,skill.id()),"NEW222 keeps duplicate rejection within preset");
            check(PresetSkills.edit(md,preset,slot,""),"clear tested mapping without changing schema");
        }
        check(PresetSkills.edit(md,3,0,HighPowerSkills.DUAL_POINT)&&PresetSkills.edit(md,3,1,HighPowerSkills.SCATTER_PLATES)&&PresetSkills.edit(md,3,3,skill.id()),"NEW222 coexists beside old MD bindings");
        var saved=AbilityStorage.encodeSaved(md);var loaded=AbilityStorage.decodeSaved(saved,md.cpDataConfig());
        check(saved.getInt("schema")==3&&AbilityStorage.encodeSaved(loaded).equals(saved)&&loaded.presets.skill(3,3).equals(skill.id()),"NEW222 exact native save and preset roundtrip");
        check(AbilityStorage.encodeSaved(AbilityStorage.decodeSaved(before,md.cpDataConfig())).equals(before),"pre-NEW222 save never receives new learning or migration");
        check(SkillCatalog.levelSkillCount(md)==SkillCatalog.ALL.stream().filter(s->s.category().equals(md.category)&&s.level()==2&&s.controllable()).count(),"NEW222 does not inflate original MD LV2 XP denominator");
        for(String invalidation:List.of("level","category","parent","config")){
            var candidate=AbilityStorage.decodeSaved(before,md.cpDataConfig());var battery=new Battery(3900);
            process.start(battery,DevelopmentActions.skill(candidate,skill));for(int tick=0;tick<129;tick++)process.tick();
            try{
                switch(invalidation){
                    case "level"->candidate.setLevel(1);
                    case "category"->candidate.changeCategoryClassic("electromaster");
                    case "parent"->candidate.unlearn(HighPowerSkills.SCATTER_PLATES);
                    case "config"->SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("meltdowner",skill.id()),new ClassicSkillConfiguration.Flags(false,false)));
                }
                process.tick();check(process.state()==DevelopmentProcess.State.FAILED&&battery.energy==0&&!candidate.learned(skill.id()),"NEW222 final paid tick rejects late "+invalidation);
            }finally{SkillCatalog.resetConfiguration();}
        }
        loaded.setLevel(1);check(loaded.learned(skill.id())&&!HighPowerSkills.eligible(loaded,skill.id())&&!PresetSkills.selectable(loaded,skill.id()),"saved NEW222 cannot bypass level downgrade");
        var request=new HighPowerLearningProtocol.Request(skill.id(),Integer.MAX_VALUE);
        check(request.wire().equals("thermal_shell:~zik0zj")&&("00000000-0000-0000-0000-000000000000:"+request.wire()).length()==58&&request.equals(HighPowerLearningProtocol.parse(request.wire())),"NEW222 full canonical ID and max sequence fit machine request");
        check(HighPowerLearningProtocol.parse("new222:~1")==null,"proposal number is never a new saved or wire alias");
    }

    private static void foldedMirrorProgression()throws Exception{
        var skill=SkillCatalog.find("meltdowner",HighPowerSkills.FOLDED_MIRROR_ARRAY).orElseThrow();
        check(skill.id().equals("folded_mirror_array")&&skill.level()==2&&skill.controllable()&&HighPowerSkills.FOLDED_MIRROR_ARRAY_PROPOSAL==221,"NEW221 retains its exact MD LV2 saved identity and proposal");
        check(skill.requirements().equals(List.of(new SkillCatalog.Requirement(HighPowerSkills.DUAL_POINT,0)))&&skill.anyLearnedSkillLevel()==0,"NEW221 requires exactly learned zero-mastery dual point");
        var md=new AbilityProgress();md.changeCategoryClassic("meltdowner");md.setActivateState(true);
        var prerequisite=SkillCatalog.find("meltdowner",HighPowerSkills.DUAL_POINT).orElseThrow();
        var prerequisiteBattery=new Battery(2340);var prerequisiteProcess=new DevelopmentProcess();
        prerequisiteProcess.start(prerequisiteBattery,DevelopmentActions.skill(md,prerequisite));
        for(int tick=0;tick<78;tick++)prerequisiteProcess.tick();
        check(md.learned(prerequisite.id())&&prerequisiteBattery.energy==0&&md.exp(prerequisite.id())==0,"NEW221 prerequisite is genuinely paid with 78 native ticks and 2340 IF");
        md.levelExperience=cn.academy.port.core.ClassicRules.levelThreshold(1,SkillCatalog.levelSkillCount(md));
        check(!DevelopmentActions.canLearn(md,DeveloperType.PORTABLE,skill),"continuous growth cannot bypass NEW221 integer LV2");
        md.setLevel(2);md.unlearn(HighPowerSkills.DUAL_POINT);md.learn("light_shield");md.learn(HighPowerSkills.SCATTER_PLATES);md.learn(HighPowerSkills.BEAM_ETCHING);md.learn(HighPowerSkills.THERMAL_SHELL);
        check(!DevelopmentActions.canLearn(md,DeveloperType.ADVANCED,skill),"classic shield or any other MD addition cannot substitute for NEW211 prerequisite");
        md.learn(HighPowerSkills.DUAL_POINT);
        check(DevelopmentActions.canLearn(md,DeveloperType.PORTABLE,skill)&&md.exp(HighPowerSkills.DUAL_POINT)==0,"zero-mastery learned NEW211 permits Portable LV2 learning");
        check(!PresetSkills.edit(md,0,0,skill.id())&&!HighPowerSkills.eligible(md,skill.id()),"unlearned NEW221 cannot bind or cast");
        var action=DevelopmentActions.skill(md,skill);var process=new DevelopmentProcess();
        check(action.stimulations()==5&&DeveloperType.PORTABLE.actualConsumption(action.stimulations())==3900,"NEW221 uses five native stimulations and 3900 actual IF");
        md.cp=37.5;md.overload=2.25;md.extraCp=1.5;md.extraOverload=.25;
        var before=AbilityStorage.encodeSaved(md);var shortBattery=new Battery(3899);
        process.start(shortBattery,action);for(int tick=0;tick<129;tick++)process.tick();
        check(!md.learned(skill.id())&&shortBattery.energy==29&&AbilityStorage.encodeSaved(md).equals(before),"NEW221 no early grant on final-tick shortage");
        process.tick();check(process.state()==DevelopmentProcess.State.FAILED&&shortBattery.energy==0&&AbilityStorage.encodeSaved(md).equals(before),"NEW221 one-IF shortage preserves abilities with no refund");
        var cancelled=new Battery(3900);process.start(cancelled,action);for(int tick=0;tick<129;tick++)process.tick();process.abort();process.tick();
        check(process.state()==DevelopmentProcess.State.FAILED&&cancelled.energy==30&&!md.learned(skill.id())&&AbilityStorage.encodeSaved(md).equals(before),"NEW221 late cancellation spends only paid ticks and neither learns nor refunds");
        var paid=new Battery(3900);double xp=md.levelExperience;process.start(paid,action);
        for(int tick=0;tick<129;tick++)process.tick();check(!md.learned(skill.id())&&paid.energy==30,"NEW221 waits for all 130 paid ticks");
        process.tick();check(process.state()==DevelopmentProcess.State.DONE&&paid.energy==0&&md.learned(skill.id())&&md.exp(skill.id())==0&&md.levelExperience==xp,"NEW221 exact finite learning grants no mastery or level XP");
        for(int preset=0;preset<4;preset++)for(int slot=0;slot<4;slot++){
            check(PresetSkills.edit(md,preset,slot,skill.id()),"NEW221 binds each ordinary slot "+preset+"/"+slot);
            check(!PresetSkills.edit(md,preset,(slot+1)%4,skill.id()),"NEW221 keeps duplicate rejection within preset");
            check(PresetSkills.edit(md,preset,slot,""),"clear tested mapping without changing schema");
        }
        check(md.extraCp==1.5&&md.extraOverload==.25,"NEW221 pure learning preserves existing classic training fields; native tests cover shared CP/HP ledgers");
        md.cp=37.5;md.overload=2.25;
        check(PresetSkills.edit(md,3,0,HighPowerSkills.DUAL_POINT)&&PresetSkills.edit(md,3,1,HighPowerSkills.SCATTER_PLATES)&&PresetSkills.edit(md,3,2,HighPowerSkills.THERMAL_SHELL)&&PresetSkills.edit(md,3,3,skill.id()),"NEW221 coexists beside preserved old MD bindings");
        var saved=AbilityStorage.encodeSaved(md);var loaded=AbilityStorage.decodeSaved(saved,md.cpDataConfig());
        check(saved.getInt("schema")==3&&AbilityStorage.encodeSaved(loaded).equals(saved)&&loaded.presets.skill(3,3).equals(skill.id()),"NEW221 exact native save and preset roundtrip");
        check(AbilityStorage.encodeSaved(AbilityStorage.decodeSaved(before,md.cpDataConfig())).equals(before),"pre-NEW221 save never receives new learning or migration");
        check(SkillCatalog.levelSkillCount(md)==SkillCatalog.ALL.stream().filter(s->s.category().equals(md.category)&&s.level()==2&&s.controllable()).count(),"NEW221 does not inflate original MD LV2 XP denominator");
        for(String invalidation:List.of("level","category","parent","config")){
            var candidate=AbilityStorage.decodeSaved(before,md.cpDataConfig());var battery=new Battery(3900);
            process.start(battery,DevelopmentActions.skill(candidate,skill));for(int tick=0;tick<129;tick++)process.tick();
            try{
                switch(invalidation){
                    case "level"->candidate.setLevel(1);
                    case "category"->candidate.changeCategoryClassic("electromaster");
                    case "parent"->candidate.unlearn(HighPowerSkills.DUAL_POINT);
                    case "config"->SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("meltdowner",skill.id()),new ClassicSkillConfiguration.Flags(false,false)));
                }
                process.tick();check(process.state()==DevelopmentProcess.State.FAILED&&battery.energy==0&&!candidate.learned(skill.id()),"NEW221 final paid tick rejects late "+invalidation);
            }finally{SkillCatalog.resetConfiguration();}
        }
        check(!SkillCatalog.canLearn(md,skill),"NEW221 catalog rejects duplicate learned-skill requests; native test checks paid ingress");
        loaded.setLevel(1);check(loaded.learned(skill.id())&&!HighPowerSkills.eligible(loaded,skill.id())&&!PresetSkills.selectable(loaded,skill.id()),"saved NEW221 cannot bypass level downgrade");
        var request=new HighPowerLearningProtocol.Request(skill.id(),Integer.MAX_VALUE);
        check(request.wire().equals("folded_mirror_array:~zik0zj")&&("00000000-0000-0000-0000-000000000000:"+request.wire()).length()==64&&request.equals(HighPowerLearningProtocol.parse(request.wire())),"NEW221 full canonical ID and max sequence fit machine request");
        check(HighPowerLearningProtocol.parse("new221:~1")==null,"proposal number is never a new saved or wire alias");
        var previous=AbilityStorage.decodeSaved(before,md.cpDataConfig());
        check(!previous.learned(skill.id())&&previous.learned(HighPowerSkills.THERMAL_SHELL),"pre-NEW221 save preserves NEW222 without automatically learning NEW221");
        md.changeCategoryClassic("electromaster");md.changeCategoryClassic("meltdowner");md.setLevel(2);
        check(!DevelopmentActions.canLearn(md,DeveloperType.PORTABLE,skill)&&!md.learned(skill.id()),"category return requires real NEW211 relearning");
        var relearnRoot=new Battery(2340);process.start(relearnRoot,DevelopmentActions.skill(md,prerequisite));for(int tick=0;tick<78;tick++)process.tick();
        var relearn=new Battery(3900);double relearnXp=md.levelExperience;process.start(relearn,DevelopmentActions.skill(md,skill));for(int tick=0;tick<130;tick++)process.tick();
        check(md.learned(skill.id())&&relearnRoot.energy==0&&relearn.energy==0&&md.levelExperience==relearnXp,"category return pays full prerequisite and NEW221 cost without awarding XP");
    }

    private static void disintegrationSweepProgression()throws Exception{
        var skill=SkillCatalog.find("meltdowner",HighPowerSkills.DISINTEGRATION_SWEEP).orElseThrow();
        check(skill.id().equals("disintegration_sweep")&&skill.level()==3&&skill.controllable()&&HighPowerSkills.DISINTEGRATION_SWEEP_PROPOSAL==231,"NEW231 exact canonical saved identity and integerLV3");
        check(skill.requirements().equals(List.of(new SkillCatalog.Requirement(HighPowerSkills.FOLDED_MIRROR_ARRAY,0)))&&skill.anyLearnedSkillLevel()==0,"NEW231 requires learned zero-mastery NEW221 exactly");
        var md=new AbilityProgress();md.changeCategoryClassic("meltdowner");md.setActivateState(true);md.setLevel(2);
        var process=new DevelopmentProcess();
        for(String id:List.of(HighPowerSkills.DUAL_POINT,HighPowerSkills.FOLDED_MIRROR_ARRAY)){
            var parent=SkillCatalog.find("meltdowner",id).orElseThrow();int ticks=cn.academy.port.core.ClassicRules.learningStimulations(parent.level())*26;
            var battery=new Battery(ticks*30);process.start(battery,DevelopmentActions.skill(md,parent));for(int t=0;t<ticks;t++)process.tick();
            check(md.learned(id)&&md.exp(id)==0&&battery.energy==0,"NEW231 parent chain is paid by ordinary developer ticks");
        }
        md.levelExperience=cn.academy.port.core.ClassicRules.levelThreshold(2,SkillCatalog.levelSkillCount(md));
        check(!DevelopmentActions.canLearn(md,DeveloperType.NORMAL,skill),"continuous q reaching3 never bypasses integerLV3");
        md.setLevel(3);check(!DevelopmentActions.canLearn(md,DeveloperType.PORTABLE,skill)&&DevelopmentActions.canLearn(md,DeveloperType.NORMAL,skill),"NEW231 enforces real Normal developer tier");
        md.unlearn(HighPowerSkills.FOLDED_MIRROR_ARRAY);md.learn("meltdowner");
        check(!DevelopmentActions.canLearn(md,DeveloperType.ADVANCED,skill),"classic beam and paid NEW211 cannot substitute for NEW221");md.learn(HighPowerSkills.FOLDED_MIRROR_ARRAY);
        check(!PresetSkills.edit(md,0,0,skill.id()),"unlearned NEW231 cannot bind");
        md.cp=37.5;md.overload=2.25;md.extraCp=1.5;md.extraOverload=.25;
        var before=AbilityStorage.encodeSaved(md);double xp=md.levelExperience;var action=DevelopmentActions.skill(md,skill);
        check(action.stimulations()==7&&DeveloperType.NORMAL.actualConsumption(7)==5145,"NEW231 seven Normal stimulations cost5145IF");
        var shortage=new Battery(5144,DeveloperType.NORMAL);process.start(shortage,action);for(int t=0;t<146;t++)process.tick();
        check(shortage.energy==34&&!md.learned(skill.id()),"shortage waits until final real native tick");process.tick();
        check(shortage.energy==0&&process.state()==DevelopmentProcess.State.FAILED&&AbilityStorage.encodeSaved(md).equals(before),"oneIF shortage grants nothing and preserves all saved state");
        var cancelled=new Battery(5145,DeveloperType.NORMAL);process.start(cancelled,action);for(int t=0;t<146;t++)process.tick();process.abort();process.tick();
        check(cancelled.energy==35&&!md.learned(skill.id())&&AbilityStorage.encodeSaved(md).equals(before),"late cancellation never grants or refunds");
        var paid=new Battery(5145,DeveloperType.NORMAL);process.start(paid,action);for(int t=0;t<146;t++)process.tick();check(!md.learned(skill.id())&&paid.energy==35,"all147 ticks are required");process.tick();
        check(md.learned(skill.id())&&md.exp(skill.id())==0&&md.levelExperience==xp&&paid.energy==0&&md.extraCp==1.5&&md.extraOverload==.25,"pure learning gives noXP or training refill; native bound-event tests cover shared CP ledgers");
        for(int preset=0;preset<4;preset++)for(int slot=0;slot<4;slot++){
            check(PresetSkills.edit(md,preset,slot,skill.id()),"NEW231 binds ordinary slot"+preset+"/"+slot);
            check(!PresetSkills.edit(md,preset,(slot+1)%4,skill.id()),"NEW231 ordinary duplicate rejection");check(PresetSkills.edit(md,preset,slot,""),"clear tested ordinary mapping");
        }
        check(PresetSkills.edit(md,3,0,HighPowerSkills.DUAL_POINT)&&PresetSkills.edit(md,3,1,HighPowerSkills.FOLDED_MIRROR_ARRAY)&&PresetSkills.edit(md,3,3,skill.id()),"NEW231 coexists with existing exact mappings");
        var saved=AbilityStorage.encodeSaved(md);var loaded=AbilityStorage.decodeSaved(saved,md.cpDataConfig());
        check(saved.getInt("schema")==3&&AbilityStorage.encodeSaved(loaded).equals(saved)&&loaded.presets.skill(3,3).equals(skill.id()),"NEW231 exact save and preset roundtrip has no schema migration");
        check(AbilityStorage.encodeSaved(AbilityStorage.decodeSaved(before,md.cpDataConfig())).equals(before),"preNEW231 saves do not acquire learning or mappings");
        for(int level=1;level<=5;level++){loaded.setLevel(level);check(SkillCatalog.levelSkillCount(loaded)==SkillCatalog.ALL.stream().filter(v->v.category().equals("meltdowner")&&v.level()==loaded.level&&v.controllable()).count(),"all five classic MD denominators remain unchanged");}
        for(String invalidation:List.of("level","category","parent","config")){
            var candidate=AbilityStorage.decodeSaved(before,md.cpDataConfig());var battery=new Battery(5145,DeveloperType.NORMAL);process.start(battery,DevelopmentActions.skill(candidate,skill));for(int t=0;t<146;t++)process.tick();
            try{switch(invalidation){case "level"->candidate.setLevel(2);case "category"->candidate.changeCategoryClassic("electromaster");case "parent"->candidate.unlearn(HighPowerSkills.FOLDED_MIRROR_ARRAY);case "config"->SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("meltdowner",skill.id()),new ClassicSkillConfiguration.Flags(false,false)));}
                process.tick();check(battery.energy==0&&!candidate.learned(skill.id())&&process.state()==DevelopmentProcess.State.FAILED,"final paid tick rejects late"+invalidation);
            }finally{SkillCatalog.resetConfiguration();}
        }
        loaded.setLevel(2);check(loaded.learned(skill.id())&&!HighPowerSkills.eligible(loaded,skill.id())&&!PresetSkills.selectable(loaded,skill.id()),"retained savedNEW231 cannot bypass integer downgrade");
        var request=new HighPowerLearningProtocol.Request(skill.id(),Integer.MAX_VALUE);
        check(request.wire().equals("ds:~zik0zj")&&("00000000-0000-0000-0000-000000000000:"+request.wire()).length()<=64&&request.equals(HighPowerLearningProtocol.parse(request.wire())),"wire-only ds alias fits unchanged machine bound and restores canonical identity");
        check(HighPowerLearningProtocol.result(request,true,"").getString("skill").equals(skill.id())&&HighPowerLearningProtocol.parse("new231:~1")==null&&HighPowerLearningProtocol.parse("disintegration_sweep:~1")==null,"feedback stays canonical and wire aliases have one canonical encoding");
    }

    private static void disintegrationSweepLayout()throws Exception{
        var nodes=HighPowerDeveloperLayout.page("meltdowner",HighPowerDeveloperLayout.Page.HIGH_POWER);var node=nodes.stream().filter(n->n.id().equals(HighPowerSkills.DISINTEGRATION_SWEEP)).findFirst().orElseThrow();
        check(node.id().equals(HighPowerSkills.DISINTEGRATION_SWEEP)&&node.index()==231&&node.level()==3&&node.x()==160&&node.y()==105&&node.parent().equals(HighPowerSkills.FOLDED_MIRROR_ARRAY),"NEW231 gets one explicit page node without moving any old node");
        check(node.conditions().size()==2&&node.conditions().getFirst().get("minimum").getAsString().equals("normal")&&node.conditions().get(1).get("minimum_exp").getAsDouble()==0,"page discloses genuine Normal tier and zero-mastery parent");
        var nav=new HighPowerDeveloperLayout.Navigation();nav.synchronize("meltdowner");check(!nav.select(node.id())&&nav.switchTo(HighPowerDeveloperLayout.Page.HIGH_POWER)&&nav.select(node.id())&&nav.beginLearning(),"NEW231 selection and paid development use ordinary dedicated-page navigation");
        check(nav.back()==HighPowerDeveloperLayout.Back.BUSY&&!nav.switchTo(HighPowerDeveloperLayout.Page.CLASSIC),"pendingNEW231 cannot escape or duplicate development");nav.finishLearning();
        check(nav.back()==HighPowerDeveloperLayout.Back.SELECTION&&nav.back()==HighPowerDeveloperLayout.Back.CLASSIC_PAGE,"NEW231 Back preserves ordinary page order");
        try(var original=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/meltdowner/skills/meltdowner.png");var reused=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/meltdowner/skills/disintegration_sweep.png")){
            check(original!=null&&reused!=null&&java.util.Arrays.equals(original.readAllBytes(),reused.readAllBytes()),"NEW231 icon retains inspected authentic classic green-white beam pixels");
        }
        check(HighPowerProgressionRegressionTest.class.getResource("/assets/academy/textures/abilities/meltdowner/skills/disintegration_sweep.LICENSE.txt")!=null,"NEW231 icon retains attribution and GPL notice");
        for(String lang:List.of("en_us","zh_cn","zh_tw","ja_jp"))try(var reader=new InputStreamReader(HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/lang/"+lang+".json"),StandardCharsets.UTF_8)){
            var dictionary=JsonParser.parseReader(reader).getAsJsonObject();String help=dictionary.get(node.descriptionKey()).getAsString();
            for(String value:List.of("8","100","50°","48","10","0.035","2D","16","147","5145"))check(help.contains(value),"NEW231 real charge, finite geometry, total budget and paid learning help"+lang+"/"+value);
            if(lang.equals("zh_cn"))check(dictionary.get(node.nameKey()).getAsString().equals("崩解扫帚〔高数值〕"),"exact approved NEW231 workbook name");
            for(String status:List.of("status","attack","mining","charging","sweeping","ended","miss","blocked","refused","damaged","absorbed","mined","unavailable","too_short","expired","no_success"))check(dictionary.has("academy.highpower.disintegration_sweep."+status)&&!dictionary.get("academy.highpower.disintegration_sweep."+status).getAsString().isBlank(),"truthful NEW231 status localized"+lang+"/"+status);
        }
    }

    private static void foldedMirrorLayout()throws Exception{
        var nodes=HighPowerDeveloperLayout.page("meltdowner",HighPowerDeveloperLayout.Page.HIGH_POWER);
        var node=nodes.stream().filter(n->n.id().equals(HighPowerSkills.FOLDED_MIRROR_ARRAY)).findFirst().orElseThrow();var parent=nodes.getFirst();
        check(node.id().equals(HighPowerSkills.FOLDED_MIRROR_ARRAY)&&node.index()==221&&node.level()==2&&node.x()==206&&node.y()==97&&node.parent().equals(HighPowerSkills.DUAL_POINT),"NEW221 occupies the approved dedicated-page LV2 node after NEW211");
        check(nodes.stream().limit(5).map(n->List.of(n.x(),n.y())).toList().equals(List.of(List.of(26d,61d),List.of(110d,29d),List.of(110d,97d),List.of(206d,29d),List.of(206d,97d))),"NEW221 leaves all four accepted MD page coordinates unchanged");
        check(node.conditions().size()==2&&node.conditions().getFirst().get("minimum").getAsString().equals("portable")&&node.conditions().get(1).get("id").getAsString().equals(parent.id())&&node.conditions().get(1).get("minimum_exp").getAsDouble()==0,"NEW221 advertises exact Portable and learned-root conditions");
        check(node.hintIcon().equals("academy:textures/abilities/meltdowner/skills/folded_mirror_array.png")&&node.nameKey().equals("ac.ability.meltdowner.folded_mirror_array.name")&&node.descriptionKey().equals("ac.ability.meltdowner.folded_mirror_array.desc"),"NEW221 exact localized resource identities");
        var full=HighPowerDeveloperLayout.foldedMirrorDependency(1);
        check(full.size()==9&&HighPowerDeveloperLayout.foldedMirrorDependency(0).isEmpty()&&HighPowerDeveloperLayout.foldedMirrorDependency(Double.NaN).isEmpty(),"NEW221 display route has nine joined segments around NEW252/NEW253 and respects native reveal timing");
        check(full.subList(0,8).stream().map(edge->List.of(edge.x1(),edge.y1())).toList().equals(List.of(List.of(34d,90d),List.of(75d,90d),List.of(86d,92d),List.of(101d,87d),List.of(109d,69d),List.of(143d,69d),List.of(143d,91d),List.of(190d,91d))),"NEW221 display detour retains the exact approved nine-segment polyline");
        check(Math.abs(Math.hypot(full.getFirst().x0()-parent.x()-8,full.getFirst().y0()-parent.y()-8)-12.2)<1e-10&&Math.abs(Math.hypot(full.getLast().x1()-node.x()-8,full.getLast().y1()-node.y()-8)-12.2)<1e-10,"NEW221 route retains native endpoint insets");
        double total=full.stream().mapToDouble(e->Math.hypot(e.x1()-e.x0(),e.y1()-e.y0())).sum();
        for(int sample=0;sample<=100;sample++){
            double reveal=sample/100d;var edges=HighPowerDeveloperLayout.foldedMirrorDependency(reveal);
            check(Math.abs(edges.stream().mapToDouble(e->Math.hypot(e.x1()-e.x0(),e.y1()-e.y0())).sum()-total*reveal)<1e-9,"NEW221 reveal continuously traverses the full routed length");
            for(int i=0;i<edges.size();i++){
                var edge=edges.get(i);check(Math.abs(Math.hypot(edge.nx(),edge.ny())-2.75)<1e-10,"NEW221 route retains native 5.5px width");
                check(Math.min(edge.x0(),edge.x1())-Math.abs(edge.nx())-5>=0&&Math.max(edge.x0(),edge.x1())+Math.abs(edge.nx())+5<=257&&Math.min(edge.y0(),edge.y1())-Math.abs(edge.ny())-5>=0&&Math.max(edge.y0(),edge.y1())+Math.abs(edge.ny())+5<=139,"NEW221 full edge plus maximum parallax stays within tree bounds");
                if(i>0)check(edge.x0()==edges.get(i-1).x1()&&edge.y0()==edges.get(i-1).y1(),"NEW221 routed segments meet without gaps");
                for(var other:nodes)if(!other.id().equals(node.id())&&!other.id().equals(parent.id())){
                    check(java.awt.geom.Line2D.ptSegDist(edge.x0(),edge.y0(),edge.x1(),edge.y1(),other.x()+8,other.y()+8)>21.35,"NEW221 full-width link clears every unrelated full-hover circle");
                    check(!new java.awt.geom.Rectangle2D.Double(other.x()-Math.abs(edge.nx()),other.y()-Math.abs(edge.ny()),16+2*Math.abs(edge.nx()),16+2*Math.abs(edge.ny())).intersectsLine(edge.x0(),edge.y0(),edge.x1(),edge.y1()),"NEW221 full-width link clears unrelated selection boxes");
                }
            }
        }
        var nav=new HighPowerDeveloperLayout.Navigation();nav.synchronize("meltdowner");
        check(!nav.select(node.id())&&nav.switchTo(HighPowerDeveloperLayout.Page.HIGH_POWER)&&nav.select(node.id()),"NEW221 is selectable only on its dedicated page");
        check(nav.beginLearning()&&!nav.beginLearning()&&nav.back()==HighPowerDeveloperLayout.Back.BUSY,"NEW221 pending selection rejects duplicate start or Back");
        var pending=new HighPowerLearningProtocol.Pending();var old=new HighPowerLearningProtocol.Request(node.id(),221);pending.begin(old);nav.clearSelection();pending.cancel();
        check(!pending.receive(HighPowerLearningProtocol.result(old,true,"")),"closing NEW221 ignores late accepted responses");
        nav.select(node.id());var retry=new HighPowerLearningProtocol.Request(node.id(),222);pending.begin(retry);
        check(!pending.receive(HighPowerLearningProtocol.result(old,false,"old"))&&pending.receive(HighPowerLearningProtocol.result(retry,false,"current")),"NEW221 retry accepts only its new request generation");
        check(nav.back()==HighPowerDeveloperLayout.Back.SELECTION&&nav.back()==HighPowerDeveloperLayout.Back.CLASSIC_PAGE&&nav.back()==HighPowerDeveloperLayout.Back.CLOSE_SCREEN,"NEW221 Back dismisses selection then page then screen");
        String screen=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cn/academy/port/client/ClassicDeveloperScreen.java"));
        check(screen.contains("HighPowerDeveloperLayout.foldedMirrorDependency(reveal.line())")&&screen.contains("HighPowerSkills.FOLDED_MIRROR_ARRAY)&&navigation.page()==HighPowerDeveloperLayout.Page.HIGH_POWER"),"actual NEW221 renderer uses the tested route only on the MD high-power page");
        try(var original=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/meltdowner/skills/ray_barrage.png");var reused=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/meltdowner/skills/folded_mirror_array.png")){
            check(original!=null&&reused!=null&&java.util.Arrays.equals(original.readAllBytes(),reused.readAllBytes()),"NEW221 icon preserves inspected original MD beam-array pixels");
        }
        check(HighPowerProgressionRegressionTest.class.getResource("/assets/academy/textures/abilities/meltdowner/skills/folded_mirror_array.LICENSE.txt")!=null,"NEW221 icon retains its original attribution and licence notice");
        for(String lang:List.of("en_us","zh_cn","zh_tw","ja_jp"))try(var reader=new InputStreamReader(HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/lang/"+lang+".json"),StandardCharsets.UTF_8)){
            var dictionary=JsonParser.parseReader(reader).getAsJsonObject();String description=dictionary.get(node.descriptionKey()).getAsString();
            check(description.contains("8")&&description.contains("12")&&description.contains("400")&&description.contains("40")&&description.contains("1.5D")&&description.contains("130")&&description.contains("3900"),"NEW221 concise player help covers controls, fixed lifetime, total range, one hit and genuine learning "+lang);
            if(lang.equals("zh_cn"))check(dictionary.get(node.nameKey()).getAsString().equals("折束镜阵〔高数值〕"),"NEW221 approved workbook Chinese name");
            check(dictionary.get("academy.highpower.folded_mirror_array.status").getAsString().split("%s",-1).length==4&&dictionary.get("academy.highpower.folded_mirror_array.status_unpaid").getAsString().split("%s",-1).length==3,"NEW221 HUD formats retain count/lifetime/state placeholders "+lang);
            for(String status:List.of("unavailable","no_mirrors","full","recalled","expired","status","status_unpaid","prepared","adjusting","ended","miss","blocked","refused","damaged","absorbed","degenerate"))check(dictionary.has("academy.highpower.folded_mirror_array."+status)&&!dictionary.get("academy.highpower.folded_mirror_array."+status).getAsString().isBlank(),"NEW221 status is localized "+lang+"/"+status);
        }
    }

    private static void beamStepLayout()throws Exception{
        var skill=SkillCatalog.find("meltdowner",HighPowerSkills.BEAM_STEP).orElseThrow();
        check(skill.level()==3&&skill.requirements().equals(List.of(new SkillCatalog.Requirement(HighPowerSkills.THERMAL_SHELL,0))),"NEW232 requires integer LV3 and genuine NEW222 learning");
        var nodes=HighPowerDeveloperLayout.page("meltdowner",HighPowerDeveloperLayout.Page.HIGH_POWER);
        var node=nodes.stream().filter(n->n.id().equals(HighPowerSkills.BEAM_STEP)).findFirst().orElseThrow();
        check(node.index()==232&&node.x()==50&&node.y()==20&&node.parent().equals(HighPowerSkills.THERMAL_SHELL),"NEW232 has a distinct visible page slot");
        var nav=new HighPowerDeveloperLayout.Navigation();nav.synchronize("meltdowner");
        check(!nav.select(node.id())&&nav.switchTo(HighPowerDeveloperLayout.Page.HIGH_POWER)&&nav.select(node.id())&&nav.beginLearning(),"NEW232 uses ordinary page navigation and correlated learning");
        check(nav.back()==HighPowerDeveloperLayout.Back.BUSY,"NEW232 pending learning cannot escape");nav.finishLearning();
        check(nav.back()==HighPowerDeveloperLayout.Back.SELECTION&&nav.back()==HighPowerDeveloperLayout.Back.CLASSIC_PAGE,"NEW232 back navigation retains classic page");
        var request=new HighPowerLearningProtocol.Request(skill.id(),Integer.MAX_VALUE);
        check(request.wire().equals("beam_step:~zik0zj")&&request.equals(HighPowerLearningProtocol.parse(request.wire()))&&HighPowerLearningProtocol.parse("new232:~1")==null,"NEW232 canonical codec fits existing wire without aliases");
        try(var original=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/meltdowner/skills/jet_engine.png");var reused=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/meltdowner/skills/beam_step.png")){
            check(original!=null&&reused!=null&&java.util.Arrays.equals(original.readAllBytes(),reused.readAllBytes()),"NEW232 icon retains inspected original propulsion pixels");
        }
        for(String lang:List.of("en_us","zh_cn","zh_tw","ja_jp"))try(var reader=new InputStreamReader(HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/lang/"+lang+".json"),StandardCharsets.UTF_8)){
            var dictionary=JsonParser.parseReader(reader).getAsJsonObject();
            for(String key:List.of(node.nameKey(),node.descriptionKey(),"academy.highpower.beam_step.unsupported","academy.highpower.beam_step.airborne","academy.highpower.beam_step.assist_risk"))check(dictionary.has(key)&&!dictionary.get(key).getAsString().isBlank(),"NEW232 truthful help localized "+lang+"/"+key);
            var help=dictionary.get(node.descriptionKey()).getAsString();for(String number:List.of("0.72","0.36","32","147","5145"))check(help.contains(number),"NEW232 physical bounds and paid learning disclosed "+lang+"/"+number);
        }
    }

    private static void zeroGapArmorLayout()throws Exception{
        var skill=SkillCatalog.find("meltdowner",HighPowerSkills.ZERO_GAP_ARMOR).orElseThrow();
        check(skill.id().equals("zero_gap_armor")&&HighPowerSkills.ZERO_GAP_ARMOR_PROPOSAL==252&&skill.level()==5&&skill.controllable(),"NEW252 keeps its exact canonical identity and integer LV5");
        check(skill.requirements().equals(List.of(new SkillCatalog.Requirement(HighPowerSkills.DISSIPATION_CURTAIN,0)))&&skill.anyLearnedSkillLevel()==0,"NEW252 requires precisely learned NEW242 without a mastery gate");
        var nodes=HighPowerDeveloperLayout.page("meltdowner",HighPowerDeveloperLayout.Page.HIGH_POWER);
        var priorCoordinates=List.of(List.of(26d,61d),List.of(110d,29d),List.of(110d,97d),List.of(206d,29d),List.of(206d,97d),List.of(160d,105d),List.of(50d,20d),List.of(225d,63d),List.of(160d,20d),List.of(62d,106d));
        check(nodes.stream().filter(n->!n.id().equals(HighPowerSkills.POINT_MELTDOWN)).limit(10).map(n->List.of(n.x(),n.y())).toList().equals(priorCoordinates),"NEW252 preserves every previously accepted MD high-power node coordinate; later NEW233 does not displace an old node");
        var node=nodes.stream().filter(n->n.id().equals(HighPowerSkills.ZERO_GAP_ARMOR)).findFirst().orElseThrow();
        check(node.id().equals(skill.id())&&node.index()==252&&node.level()==5&&node.x()==160&&node.y()==61&&node.parent().equals(HighPowerSkills.DISSIPATION_CURTAIN),"NEW252 appends one visible LV5 node below its exact NEW242 parent");
        check(node.conditions().size()==2&&node.conditions().getFirst().get("minimum").getAsString().equals("advanced")&&node.conditions().get(1).get("id").getAsString().equals(HighPowerSkills.DISSIPATION_CURTAIN)&&node.conditions().get(1).get("minimum_exp").getAsDouble()==0,"NEW252 tooltip advertises Advanced and learned zero-mastery NEW242");
        var state=new AbilityProgress();state.changeCategoryClassic("meltdowner");state.setLevel(5);state.learn(HighPowerSkills.DISSIPATION_CURTAIN);
        var action=DevelopmentActions.skill(state,skill);
        check(action.stimulations()==15&&action.stimulations()*DeveloperType.ADVANCED.ticksPerStimulation()==240&&DeveloperType.ADVANCED.actualConsumption(action.stimulations())==9600,"NEW252 learning help matches the native 240-tick 9600-IF Advanced contract");
        check(SkillCatalog.levelSkillCount(state)==2,"NEW252 does not inflate the classic MD LV5 XP denominator");
        var nav=new HighPowerDeveloperLayout.Navigation();nav.synchronize("meltdowner");
        check(!nav.select(node.id())&&nav.switchTo(HighPowerDeveloperLayout.Page.HIGH_POWER)&&nav.select(node.id())&&nav.beginLearning(),"NEW252 uses ordinary dedicated-page selection and paid learning");
        check(nav.back()==HighPowerDeveloperLayout.Back.BUSY&&!nav.switchTo(HighPowerDeveloperLayout.Page.CLASSIC),"pending NEW252 cannot escape its selection or change pages");nav.finishLearning();
        check(nav.back()==HighPowerDeveloperLayout.Back.SELECTION&&nav.back()==HighPowerDeveloperLayout.Back.CLASSIC_PAGE,"NEW252 Back returns through the ordinary selection and page order");
        var request=new HighPowerLearningProtocol.Request(skill.id(),Integer.MAX_VALUE);
        check(request.equals(HighPowerLearningProtocol.parse(request.wire()))&&("00000000-0000-0000-0000-000000000000:"+request.wire()).getBytes(StandardCharsets.UTF_8).length<=64&&HighPowerLearningProtocol.parse("new252:~1")==null,"NEW252 canonical learning ID fits the unchanged machine request without proposal aliases");
        try(var original=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/meltdowner/skills/light_shield.png");var reused=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/meltdowner/skills/zero_gap_armor.png")){
            check(original!=null&&reused!=null&&java.util.Arrays.equals(original.readAllBytes(),reused.readAllBytes()),"NEW252 icon retains inspected original green shield pixels byte-for-byte");
        }
        check(HighPowerProgressionRegressionTest.class.getResource("/assets/academy/textures/abilities/meltdowner/skills/zero_gap_armor.LICENSE.txt")!=null,"NEW252 shield icon retains attribution and GPL notice");
        for(String lang:List.of("en_us","zh_cn","zh_tw","ja_jp"))try(var reader=new InputStreamReader(HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/lang/"+lang+".json"),StandardCharsets.UTF_8)){
            var dictionary=JsonParser.parseReader(reader).getAsJsonObject();var description=dictionary.get(node.descriptionKey()).getAsString();
            for(String value:List.of("6","3","2S","4","1:1","1CP","20","240","9600"))check(description.contains(value),"NEW252 finite coverage, global break, cost and paid learning are disclosed "+lang+"/"+value);
            if(lang.equals("zh_cn"))check(dictionary.get(node.nameKey()).getAsString().equals("零隙束甲〔高数值〕"),"NEW252 retains the exact approved workbook name");
            if(lang.equals("zh_tw"))check(dictionary.get(node.nameKey()).getAsString().equals("零隙束甲〔高數值〕"),"NEW252 retains the faithful Traditional Chinese workbook name");
            for(String key:List.of("hud.academy.highpower.zero_gap_total","hud.academy.highpower.zero_gap_reserve","hud.academy.highpower.zero_gap_capacity","hud.academy.highpower.zero_gap_coverage","hud.academy.highpower.zero_gap_reserve_idle","screen.academy.highpower.zero_gap_armor.conflict","screen.academy.highpower.zero_gap_armor.unavailable"))check(dictionary.has(key)&&!dictionary.get(key).getAsString().isBlank(),"NEW252 actual HUD and refusal messages are localized "+lang+"/"+key);
        }
    }

    private static void clientReleaseIsolation() throws Exception {
        String source=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        String[][] contexts={{"ParticleLaminate","particle_laminate"},{"DissipationCurtain","dissipation_curtain"},{"MulticoreCanopy","multicore_canopy"}};
        for(String[] context:contexts){
            int start=source.indexOf("private static void flush"+context[0]+"Release()");
            int end=source.indexOf("\n    private static ",start+10);
            check(start>=0&&end>start,"known deferred release handler exists: "+context[0]);
            String body=source.substring(start,end);
            check(body.contains("hp_"+context[1]+"_abort"),"invalid release cancels its own authenticated context");
            for(String[] other:contexts)if(other!=context)
                check(!body.contains("hp_"+other[1]+"_abort"),"one stale release cannot cancel a different live skill: "+context[0]+"/"+other[0]);
        }
    }

    private static void meltdownerAddedNodeLinkDetours(List<ClassicDeveloperLayout.Node> nodes)throws Exception{
        var root=nodes.stream().filter(n->n.id().equals(HighPowerSkills.DUAL_POINT)).findFirst().orElseThrow();
        var relay=nodes.stream().filter(n->n.id().equals(HighPowerSkills.REMOTE_SCATTER_RELAY)).findFirst().orElseThrow();
        for(String id:List.of(HighPowerSkills.SCATTER_PLATES,HighPowerSkills.BEAM_ETCHING)){
            var node=nodes.stream().filter(n->n.id().equals(id)).findFirst().orElseThrow();
            boolean scatter=id.equals(HighPowerSkills.SCATTER_PLATES);
            var previous=cn.academy.port.client.ClassicDeveloperTimeline.edge(node.x(),node.y(),root.x(),root.y(),1);
            check(java.awt.geom.Line2D.ptSegDist(previous.x0(),previous.y0(),previous.x1(),previous.y1(),relay.x()+8,relay.y()+8)<21.35,"negative control: the previous displayed direct branch crosses NEW253 full-hover clearance: "+id);
            var full=scatter?HighPowerDeveloperLayout.scatterPlatesPageDependency(1):HighPowerDeveloperLayout.beamEtchingPageDependency(1);
            var expectedTurns=scatter?List.of(List.of(34d,90d),List.of(75d,90d),List.of(86d,92d),List.of(101d,87d),List.of(109d,69d)):List.of(List.of(34d,90d),List.of(75d,90d),List.of(86d,92d),List.of(101d,87d));
            check(full.size()==expectedTurns.size()+1&&full.subList(0,full.size()-1).stream().map(edge->List.of(edge.x1(),edge.y1())).toList().equals(expectedTurns),"display branch retains its exact approved detour: "+id);
            check(Math.abs(Math.hypot(full.getFirst().x0()-root.x()-8,full.getFirst().y0()-root.y()-8)-12.2)<1e-10&&Math.abs(Math.hypot(full.getLast().x1()-node.x()-8,full.getLast().y1()-node.y()-8)-12.2)<1e-10,"display detour preserves both native endpoint insets: "+id);
            check((scatter?HighPowerDeveloperLayout.scatterPlatesPageDependency(0):HighPowerDeveloperLayout.beamEtchingPageDependency(0)).isEmpty()&&(scatter?HighPowerDeveloperLayout.scatterPlatesPageDependency(Double.NaN):HighPowerDeveloperLayout.beamEtchingPageDependency(Double.NaN)).isEmpty(),"display detour retains zero and invalid reveal timing: "+id);
            double total=full.stream().mapToDouble(edge->Math.hypot(edge.x1()-edge.x0(),edge.y1()-edge.y0())).sum();
            for(int sample=0;sample<=1000;sample++){
                double reveal=sample/1000d;
                var edges=scatter?HighPowerDeveloperLayout.scatterPlatesPageDependency(reveal):HighPowerDeveloperLayout.beamEtchingPageDependency(reveal);
                check(Math.abs(edges.stream().mapToDouble(edge->Math.hypot(edge.x1()-edge.x0(),edge.y1()-edge.y0())).sum()-total*reveal)<1e-9,"display detour reveal progresses by exact path length: "+id);
                for(int i=0;i<edges.size();i++){
                    var edge=edges.get(i);
                    check(Math.abs(Math.hypot(edge.nx(),edge.ny())-2.75)<1e-10,"display detour retains full native 5.5px width: "+id);
                    check(Math.min(edge.x0(),edge.x1())-Math.abs(edge.nx())-5>=0&&Math.max(edge.x0(),edge.x1())+Math.abs(edge.nx())+5<=257&&Math.min(edge.y0(),edge.y1())-Math.abs(edge.ny())-5>=0&&Math.max(edge.y0(),edge.y1())+Math.abs(edge.ny())+5<=139,"display detour and maximum parallax remain in the original viewport: "+id);
                    if(i>0)check(edge.x0()==edges.get(i-1).x1()&&edge.y0()==edges.get(i-1).y1(),"display detour has no gaps during reveal: "+id);
                    for(var other:nodes)if(!other.id().equals(node.id())&&!other.id().equals(root.id())){
                        check(java.awt.geom.Line2D.ptSegDist(edge.x0(),edge.y0(),edge.x1(),edge.y1(),other.x()+8,other.y()+8)>21.35,"display detour preserves full-width clearance from every unrelated hover circle: "+id+" vs "+other.id());
                        check(!new java.awt.geom.Rectangle2D.Double(other.x()-Math.abs(edge.nx()),other.y()-Math.abs(edge.ny()),16+2*Math.abs(edge.nx()),16+2*Math.abs(edge.ny())).intersectsLine(edge.x0(),edge.y0(),edge.x1(),edge.y1()),"display detour avoids every unrelated selection box: "+id+" vs "+other.id());
                    }
                }
            }
        }
        String screen=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cn/academy/port/client/ClassicDeveloperScreen.java"));
        check(screen.contains("scatterPageRoute?HighPowerDeveloperLayout.scatterPlatesPageDependency(reveal.line()):etchingPageRoute?HighPowerDeveloperLayout.beamEtchingPageDependency(reveal.line())")&&screen.contains("HighPowerSkills.SCATTER_PLATES)&&navigation.page()==HighPowerDeveloperLayout.Page.HIGH_POWER")&&screen.contains("HighPowerSkills.BEAM_ETCHING)&&navigation.page()==HighPowerDeveloperLayout.Page.HIGH_POWER"),"actual screen uses both tested detours only on the high-power page");
    }

    private static void meltdownerPageNavigation()throws Exception{
        var classic=HighPowerDeveloperLayout.page("meltdowner",HighPowerDeveloperLayout.Page.CLASSIC);
        var expected=ClassicDeveloperLayout.category("meltdowner").stream().filter(n->!HighPowerSkills.contains(n.id())).toList();
        check(classic.equals(expected)&&classic.size()==14,"MD classic page retains every configured original identity, coordinate, parent, condition and asset");
        var high=HighPowerDeveloperLayout.page("meltdowner",HighPowerDeveloperLayout.Page.HIGH_POWER);
        check(high.stream().map(ClassicDeveloperLayout.Node::id).toList().equals(List.of("dual_point","scatter_plates","beam_etching","thermal_shell","folded_mirror_array","disintegration_sweep","beam_step","point_meltdown","particle_laminate","directional_dissipation_curtain","multicore_canopy","zero_gap_armor","remote_scatter_relay")),"labelled MD page appends exactly 253 after approved 211/212/213/222/221/231/232/233/241/242/251/252, retaining every old relative order");
        meltdownerAddedNodeLinkDetours(high);
        for(var node:high){
            var original=HighPowerDeveloperLayout.category("meltdowner").stream().filter(n->n.id().equals(node.id())).findFirst().orElseThrow();
            check(node.category().equals(original.category())&&node.id().equals(original.id())&&node.index()==original.index()&&node.level()==original.level()&&node.parent().equals(original.parent())&&node.conditions().equals(original.conditions())&&node.hintIcon().equals(original.hintIcon())&&node.nameKey().equals(original.nameKey())&&node.descriptionKey().equals(original.descriptionKey()),"page changes only display coordinates "+node.id());
            check(node.x()+8-18.6-5>=0&&node.x()+8+18.6+5<=257&&node.y()+8-18.6-5>=0&&node.y()+8+18.6+5<=139,"full-hover plus maximum parallax remains inside tree "+node.id());
            for(var other:high)if(!node.id().equals(other.id()))check(Math.hypot(node.x()-other.x(),node.y()-other.y())>=37.2,"MD page full-hover circles and selection boxes remain disjoint "+node.id()+"/"+other.id());
            if(!node.parent().isEmpty()){
                var parent=high.stream().filter(n->n.id().equals(node.parent())).findFirst().orElseThrow();
                for(int step=0;step<=100;step++){
                    var edges=node.id().equals(HighPowerSkills.SCATTER_PLATES)?HighPowerDeveloperLayout.scatterPlatesPageDependency(step/100d):node.id().equals(HighPowerSkills.BEAM_ETCHING)?HighPowerDeveloperLayout.beamEtchingPageDependency(step/100d):node.id().equals(HighPowerSkills.MULTICORE_CANOPY)?HighPowerDeveloperLayout.multicoreCanopyDependency(step/100d):node.id().equals(HighPowerSkills.THERMAL_SHELL)?HighPowerDeveloperLayout.thermalShellDependency(step/100d):node.id().equals(HighPowerSkills.PARTICLE_LAMINATE)?HighPowerDeveloperLayout.particleLaminateDependency(step/100d):node.id().equals(HighPowerSkills.BEAM_STEP)?HighPowerDeveloperLayout.beamStepDependency(step/100d):node.id().equals(HighPowerSkills.POINT_MELTDOWN)?HighPowerDeveloperLayout.pointMeltdownDependency(step/100d):node.id().equals(HighPowerSkills.FOLDED_MIRROR_ARRAY)?HighPowerDeveloperLayout.foldedMirrorDependency(step/100d):node.id().equals(HighPowerSkills.REMOTE_SCATTER_RELAY)?HighPowerDeveloperLayout.remoteScatterRelayDependency(step/100d):List.of(cn.academy.port.client.ClassicDeveloperTimeline.edge(node.x(),node.y(),parent.x(),parent.y(),step/100d));
                    for(var edge:edges)for(var other:high)if(!other.id().equals(node.id())&&!other.id().equals(parent.id()))check(java.awt.geom.Line2D.ptSegDist(edge.x0(),edge.y0(),edge.x1(),edge.y1(),other.x()+8,other.y()+8)>18.6+2.75,"complete native-width dependency avoids unrelated node throughout reveal: "+node.id()+" vs "+other.id());
                }
            }
        }
        var shell=high.stream().filter(n->n.id().equals(HighPowerSkills.THERMAL_SHELL)).findFirst().orElseThrow();check(shell.level()==2&&shell.parent().equals("scatter_plates")&&shell.conditions().getFirst().get("minimum").getAsString().equals("portable"),"NEW222 advertises exact LV2, Portable and NEW212 prerequisite");
        for(String category:List.of("electromaster","unsupported_test"))check(HighPowerDeveloperLayout.page(category,HighPowerDeveloperLayout.Page.HIGH_POWER).equals(ClassicDeveloperLayout.category(category)),"foreign trees are unchanged "+category);
        var nav=new HighPowerDeveloperLayout.Navigation();nav.synchronize("meltdowner");
        check(nav.page()==HighPowerDeveloperLayout.Page.CLASSIC&&nav.selected().isEmpty(),"MD opens on the original page");
        check(!nav.select("thermal_shell")&&!nav.beginLearning(),"hidden additions cannot be selected or learned on original page");
        check(nav.switchTo(HighPowerDeveloperLayout.Page.HIGH_POWER)&&nav.select("thermal_shell"),"real page selection exposes NEW222");
        check(!nav.switchTo(HighPowerDeveloperLayout.Page.CLASSIC)&&!nav.select("dual_point"),"open skill selection blocks page or selection replacement");
        check(nav.back()==HighPowerDeveloperLayout.Back.SELECTION&&nav.page()==HighPowerDeveloperLayout.Page.HIGH_POWER,"first Back dismisses selection and keeps its page");
        check(nav.back()==HighPowerDeveloperLayout.Back.CLASSIC_PAGE&&nav.page()==HighPowerDeveloperLayout.Page.CLASSIC&&nav.back()==HighPowerDeveloperLayout.Back.CLOSE_SCREEN,"next Back returns to classic, then closes");
        nav.switchTo(HighPowerDeveloperLayout.Page.HIGH_POWER);nav.select("thermal_shell");
        check(nav.beginLearning()&&!nav.beginLearning()&&nav.back()==HighPowerDeveloperLayout.Back.BUSY&&!nav.switchTo(HighPowerDeveloperLayout.Page.CLASSIC),"pending learning cannot duplicate, dismiss or switch pages");
        nav.finishLearning();check(nav.selected().equals("thermal_shell")&&!nav.pending()&&nav.beginLearning(),"server rejection or completion unlocks exact selection for a reviewed retry");
        var pending=new HighPowerLearningProtocol.Pending();var first=new HighPowerLearningProtocol.Request("thermal_shell",701);pending.begin(first);
        nav.clearSelection();pending.cancel();check(nav.selected().isEmpty()&&!nav.pending()&&!pending.receive(HighPowerLearningProtocol.result(first,true,"")),"close cancels local response ownership without accepting delayed result");
        nav.select("beam_etching");var second=new HighPowerLearningProtocol.Request("beam_etching",702);pending.begin(second);
        check(!pending.receive(HighPowerLearningProtocol.result(first,false,"old"))&&pending.receive(HighPowerLearningProtocol.result(second,false,"current")),"delayed response cannot replace another selected skill");
        check(nav.synchronize("electromaster")&&nav.page()==HighPowerDeveloperLayout.Page.CLASSIC&&nav.selected().isEmpty()&&!nav.pending()&&!nav.switchTo(HighPowerDeveloperLayout.Page.HIGH_POWER),"category replacement discards old MD page and pending selection");
        nav.synchronize("meltdowner");nav.switchTo(HighPowerDeveloperLayout.Page.HIGH_POWER);nav.select("thermal_shell");nav.beginLearning();
        check(!nav.synchronize("meltdowner")&&nav.pending()&&nav.page()==HighPowerDeveloperLayout.Page.HIGH_POWER,"same-category snapshots retain live page and request");
        check(nav.synchronize(null)&&nav.selected().isEmpty()&&!nav.pending()&&nav.page()==HighPowerDeveloperLayout.Page.CLASSIC,"category removal clears page session");
        String screen=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cn/academy/port/client/ClassicDeveloperScreen.java"));
        check(screen.contains("return HighPowerDeveloperLayout.page(AcademyClient.state.category,navigation.page())")&&screen.contains("var category=pageNodes()")&&screen.contains("switchPage(HighPowerDeveloperLayout.Page.HIGH_POWER)"),"actual rendered nodes, dependency lookup and click use approved page");
        check(screen.contains("HighPowerDeveloperLayout.pointMeltdownDependency(reveal.line())")&&screen.contains("HighPowerSkills.POINT_MELTDOWN)&&navigation.page()==HighPowerDeveloperLayout.Page.HIGH_POWER"),"actual NEW233 renderer uses the same full-width dependency route tested against every MD hover circle");
        check(screen.contains("cover!=null||console!=null||developing()||!navigation.switchTo(page)")&&screen.contains("navigation.select(node.id())")&&screen.contains("discardCover();classicFont.release()"),"actual screen guards page switching, selection and removal");
        check(screen.contains("cover.developmentFeedback.receive(status,action,accepted)")&&screen.contains("current.developmentFeedback.begin(expectedAction(current))")&&screen.contains("cover.developmentFeedback.cancel()")&&screen.contains("navigation.finishLearning()"),"actual screen uses correlated native snapshots and releases canceled or failed request state");
        try(var original=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/meltdowner/skills/light_shield.png");var reused=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/meltdowner/skills/thermal_shell.png")){
            check(original!=null&&reused!=null&&java.util.Arrays.equals(original.readAllBytes(),reused.readAllBytes()),"NEW222 preserves the original inspected green shield pixels");
        }
        check(HighPowerProgressionRegressionTest.class.getResource("/assets/academy/textures/abilities/meltdowner/skills/thermal_shell.LICENSE.txt")!=null,"NEW222 asset attribution retained");
        for(String lang:List.of("en_us","zh_cn","zh_tw","ja_jp"))try(var reader=new InputStreamReader(HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/lang/"+lang+".json"),StandardCharsets.UTF_8)){
            var dictionary=JsonParser.parseReader(reader).getAsJsonObject();
            for(String key:List.of(shell.nameKey(),shell.descriptionKey(),"screen.academy.developer.page_classic","screen.academy.developer.page_md_highpower"))check(dictionary.has(key)&&!dictionary.get(key).getAsString().isBlank(),"NEW222 and both pages are localized "+lang+"/"+key);
            if(lang.equals("zh_cn"))check(dictionary.get(shell.nameKey()).getAsString().equals("热解外壳〔高数值〕"),"NEW222 exact approved Chinese name");
            String description=dictionary.get(shell.descriptionKey()).getAsString();check(description.contains("0.05D")&&description.contains("3900")&&description.contains("0.75")&&description.contains("0.002"),"NEW222 detailed help discloses provisional damage, physical interval, genuine learning and upkeep "+lang);
        }
    }

    private static void nativeDevelopmentFeedback(){
        for(String learnedSkill:List.of(HighPowerSkills.THERMAL_SHELL,HighPowerSkills.FOLDED_MIRROR_ARRAY))for(boolean sufficient:List.of(true,false)){
            String expected="skill:"+learnedSkill;
            var state=new AbilityProgress();state.changeCategoryClassic("meltdowner");state.setLevel(2);state.learn(HighPowerSkills.DUAL_POINT);state.learn(HighPowerSkills.SCATTER_PLATES);
            var battery=new Battery(sufficient?3900:3899);var process=new DevelopmentProcess();
            var feedback=new HighPowerDeveloperLayout.DevelopmentFeedback();feedback.begin(expected);
            var updates=new java.util.ArrayList<HighPowerDeveloperLayout.DevelopmentUpdate>();
            var snapshots=new java.util.ArrayList<DevelopmentProcess.Snapshot>();
            process.start(battery,DevelopmentActions.skill(state,SkillCatalog.find("meltdowner",learnedSkill).orElseThrow()));
            for(int tick=0;tick<130;tick++)process.tick(snapshot->{snapshots.add(snapshot);updates.add(feedback.receive(snapshot.state().name(),snapshot.action(),true));});
            check(battery.energy==0&&updates.stream().allMatch(u->u==HighPowerDeveloperLayout.DevelopmentUpdate.PROGRESS),"real native sync-before-consume publishes only matching progress during 130 paid ticks");
            check(state.learned(learnedSkill)==sufficient,"UI feedback never duplicates or grants actual paid learning");
            var terminal=process.snapshot();
            check(terminal.action().isEmpty()&&terminal.developerType()==null&&terminal.normalizedProgress()==0&&terminal.maxStimulations()==0,"native terminal genuinely clears action, developer and progress before its dirty sync");
            process.tick(snapshot->{snapshots.add(snapshot);updates.add(feedback.receive(snapshot.state().name(),snapshot.action(),true));});
            check(updates.getLast()==(sufficient?HighPowerDeveloperLayout.DevelopmentUpdate.DONE:HighPowerDeveloperLayout.DevelopmentUpdate.FAILED),"actual next-tick native terminal completes the matching accepted cover");
            check(snapshots.getFirst().action().equals(expected)&&snapshots.getLast().action().isEmpty(),"test exercises genuine named start followed by blank terminal");
            check(feedback.receive(terminal.state().name(),terminal.action(),true)==HighPowerDeveloperLayout.DevelopmentUpdate.IGNORED,"duplicate native terminal cannot complete an ended attempt");
        }
        String expected="skill:thermal_shell";
        var feedback=new HighPowerDeveloperLayout.DevelopmentFeedback();feedback.begin(expected);
        check(feedback.receive("DONE","",true)==HighPowerDeveloperLayout.DevelopmentUpdate.IGNORED,"accepted request alone cannot consume an unrelated old blank DONE");
        check(feedback.receive("DEVELOPING",expected,false)==HighPowerDeveloperLayout.DevelopmentUpdate.IGNORED,"uncorrelated progress cannot establish an accepted high-power start");
        check(feedback.receive("FAILED","",true)==HighPowerDeveloperLayout.DevelopmentUpdate.IGNORED,"pre-acceptance progress cannot own later terminal");
        check(feedback.receive("DEVELOPING","skill:beam_etching",true)==HighPowerDeveloperLayout.DevelopmentUpdate.IGNORED,"foreign start before our start is ignored");
        check(feedback.receive("DEVELOPING",expected,true)==HighPowerDeveloperLayout.DevelopmentUpdate.PROGRESS,"accepted exact skill start owns following native snapshots");
        check(feedback.receive("DEVELOPING","skill:beam_etching",true)==HighPowerDeveloperLayout.DevelopmentUpdate.DISPLACED,"foreign replacement after start invalidates cover ownership");
        check(feedback.receive("DONE","",true)==HighPowerDeveloperLayout.DevelopmentUpdate.IGNORED&&feedback.receive("DEVELOPING",expected,true)==HighPowerDeveloperLayout.DevelopmentUpdate.IGNORED,"displaced cover cannot consume replacement completion or silently reacquire an action");
        feedback.begin(expected);feedback.receive("DEVELOPING",expected,true);feedback.cancel();
        check(feedback.receive("DONE","",true)==HighPowerDeveloperLayout.DevelopmentUpdate.IGNORED,"cancellation or closed cover rejects delayed terminal");
        feedback.begin(expected);check(feedback.receive("DONE","",true)==HighPowerDeveloperLayout.DevelopmentUpdate.IGNORED,"retry clears previous attempt start latch");
        feedback.receive("DEVELOPING",expected,true);
        check(feedback.receive("IDLE","",true)==HighPowerDeveloperLayout.DevelopmentUpdate.DISPLACED&&feedback.receive("DONE","",true)==HighPowerDeveloperLayout.DevelopmentUpdate.IGNORED,"native reset invalidates start latch before unrelated completion");
        feedback.begin(expected);feedback.receive("DEVELOPING",expected,true);
        check(feedback.receive("DONE","skill:beam_etching",true)==HighPowerDeveloperLayout.DevelopmentUpdate.DISPLACED&&feedback.receive("DONE","",true)==HighPowerDeveloperLayout.DevelopmentUpdate.IGNORED,"foreign named terminal cannot finish this cover or leave its latch armed");
        feedback.begin("level");feedback.receive("DEVELOPING","level",true);
        check(feedback.receive("DONE","",true)==HighPowerDeveloperLayout.DevelopmentUpdate.DONE,"classic level completion also retains native blank terminal semantics");
    }

    private static void beamEtchingProgression()throws Exception{
        var skill=SkillCatalog.find("meltdowner",HighPowerSkills.BEAM_ETCHING).orElseThrow();
        check(skill.id().equals("beam_etching")&&HighPowerSkills.BEAM_ETCHING_PROPOSAL==213&&skill.level()==1&&skill.controllable(),"NEW213 uses canonical saved ID, proposal and integer LV1");
        check(skill.requirements().equals(List.of(new SkillCatalog.Requirement(HighPowerSkills.DUAL_POINT,0)))&&skill.anyLearnedSkillLevel()==0,"NEW213 requires exactly learned NEW211 with no mastery or classic prerequisite");
        check(HighPowerSkills.ALL.size() == 51 && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.highpower.HighPowerSkills.ALL.get(49).id().equals("point_meltdown") && HighPowerSkills.ALL.get(48).id().equals("wind_pressure_lift") && HighPowerSkills.ALL.get(47).id().equals("multi_axis_reflection") && HighPowerSkills.ALL.get(45).id().equals("circulation_listening") && HighPowerSkills.ALL.get(44).id().equals("circulation_relief") && HighPowerSkills.ALL.get(43).id().equals("trajectory_return") && HighPowerSkills.ALL.get(42).id().equals("yaw_palm") && HighPowerSkills.ALL.get(41).id().equals("inertial_anchor") && HighPowerSkills.ALL.get(40).id().equals("shear_windblade") && HighPowerSkills.ALL.get(39).id().equals("braking_touch") && HighPowerSkills.ALL.get(30).id().equals(HighPowerSkills.CORNER_EXCHANGE) && HighPowerSkills.ALL.get(31).id().equals(HighPowerSkills.SHORT_INTERCEPTION)
                && HighPowerSkills.ALL.get(32).id().equals(HighPowerSkills.SPATIAL_RELAY)
                && HighPowerSkills.ALL.get(33).id().equals(HighPowerSkills.EMERGENCY_RETURN)
                && HighPowerSkills.ALL.get(34).id().equals(HighPowerSkills.TACTICAL_COORDINATE_FORMATION)
                && SkillCatalog.REGISTERED.get(84).equals(HighPowerSkills.ALL.get(34))&&HighPowerSkills.ALL.stream().allMatch(entry->List.of("electromaster","meltdowner","teleporter","vecmanip").contains(entry.category()))
                && HighPowerSkills.ALL.get(35).id().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR)
                && SkillCatalog.REGISTERED.get(85).equals(HighPowerSkills.ALL.get(35)),"thirty-eight additions preserve retained ordinals and remain explicitly limited to EM, MD, TP and vector");
        check(SkillCatalog.REGISTERED_CONFIG_KEYS.contains(ClassicSkillConfiguration.key("meltdowner",skill.id()))&&SkillCatalog.enabled("meltdowner",skill.id()),"NEW213 receives registered enabled config defaults");
        var md=new AbilityProgress();md.changeCategoryClassic("meltdowner");md.setActivateState(true);
        check(!DevelopmentActions.canLearn(md,DeveloperType.PORTABLE,skill)&&!SkillCatalog.canLearn(md,skill),"fresh MD cannot skip learned NEW211");
        md.learn("electron_bomb");md.learn("light_shield");
        check(!DevelopmentActions.canLearn(md,DeveloperType.PORTABLE,skill),"classic MD skills cannot substitute for NEW211");
        md.learn(HighPowerSkills.DUAL_POINT);
        check(md.exp(HighPowerSkills.DUAL_POINT)==0&&DevelopmentActions.canLearn(md,DeveloperType.PORTABLE,skill)&&SkillCatalog.canLearn(md,skill),"zero-mastery learned NEW211 unlocks genuine Portable learning");
        check(!HighPowerSkills.eligible(md,skill.id())&&!PresetSkills.edit(md,3,3,skill.id()),"unlearned NEW213 cannot cast or bind");
        var action=DevelopmentActions.skill(md,skill);var process=new DevelopmentProcess();
        check(action.stimulations()==3&&DeveloperType.PORTABLE.ticksPerStimulation()==26&&DeveloperType.PORTABLE.actualConsumption(action.stimulations())==2340,"NEW213 preserves three native stimulations, 78 paid ticks and 2340 IF");
        var before=AbilityStorage.encodeSaved(md);var shortBattery=new Battery(2339);process.start(shortBattery,action);
        for(int tick=0;tick<77;tick++)process.tick();
        check(shortBattery.energy==29&&!md.learned(skill.id())&&AbilityStorage.encodeSaved(md).equals(before),"NEW213 shortage reaches the last tick without early learning");
        process.tick();check(process.state()==DevelopmentProcess.State.FAILED&&shortBattery.energy==0&&AbilityStorage.encodeSaved(md).equals(before),"NEW213 one-IF shortage drains only the remaining battery with no grant or refund");
        var paid=new Battery(2340);process.start(paid,action);for(int tick=0;tick<77;tick++)process.tick();
        check(paid.energy==30&&!md.learned(skill.id())&&process.state()==DevelopmentProcess.State.DEVELOPING,"NEW213 must wait for the final paid tick");
        process.tick();check(paid.energy==0&&process.state()==DevelopmentProcess.State.DONE&&md.learned(skill.id())&&md.exp(skill.id())==0&&md.levelExperience==0,"NEW213 learns at exact native cost without free mastery or level XP");
        check(HighPowerSkills.eligible(md,skill.id())&&PresetSkills.edit(md,3,2,HighPowerSkills.DUAL_POINT)&&PresetSkills.edit(md,3,3,skill.id()),"NEW211 and NEW213 occupy ordinary adjacent existing slots");
        check(PresetSkills.edit(md,3,3,""),"temporarily clear NEW213 to exercise each existing slot without duplicate mappings");
        for(int preset=0;preset<4;preset++)for(int slot=0;slot<4;slot++){
            check(PresetSkills.edit(md,preset,slot,skill.id()),"NEW213 may use every existing preset slot "+preset+"/"+slot);
            check(md.presets.skill(preset,slot).equals(skill.id()),"NEW213 stores canonical ID in each existing slot");
            check(!PresetSkills.edit(md,preset,(slot+1)%4,skill.id()),"NEW213 retains ordinary duplicate-per-preset rejection");
            check(PresetSkills.edit(md,preset,slot,""),"clear only tested mapping before checking the next slot");
        }
        check(PresetSkills.edit(md,3,2,HighPowerSkills.DUAL_POINT)&&PresetSkills.edit(md,3,3,skill.id()),"restore NEW211 beside NEW213 without changing active preset");
        var saved=AbilityStorage.encodeSaved(md);var restored=AbilityStorage.decodeSaved(saved,md.cpDataConfig());
        check(saved.getInt("schema")==3&&restored.learned(skill.id())&&restored.presets.skill(3,2).equals(HighPowerSkills.DUAL_POINT)&&restored.presets.skill(3,3).equals(skill.id())&&AbilityStorage.encodeSaved(restored).equals(saved),"NEW213 learned bit and canonical presets roundtrip unchanged schema without disturbing NEW211");
        var pre213=before.copy();var oldSave=AbilityStorage.decodeSaved(pre213,md.cpDataConfig());
        check(!oldSave.learned(skill.id())&&AbilityStorage.encodeSaved(oldSave).equals(pre213),"pre-NEW213 save never grants the addition or rewrites existing state");
        check(SkillCatalog.levelSkillCount(md)==1&&SkillCatalog.ALL.size()==50&&PresetSkills.IMPLEMENTED.size()==35,"NEW213 never changes classic MD LV1 denominator, ordinals or active route count");
        for(String category:List.of("electromaster","teleporter","vecmanip")){
            var wrong=AbilityStorage.decodeSaved(saved,md.cpDataConfig());wrong.category=category;
            check(!DevelopmentActions.canLearn(wrong,DeveloperType.PORTABLE,skill)&&!HighPowerSkills.eligible(wrong,skill.id())&&!PresetSkills.selectable(wrong,skill.id()),"stored NEW213 cannot cross category gate "+category);
        }
        restored.setLevel(0);
        check(restored.learned(skill.id())&&!HighPowerSkills.eligible(restored,skill.id())&&!PresetSkills.selectable(restored,skill.id()),"stored NEW213 cannot bypass an integer level downgrade");
        for(String invalidation:List.of("category","level","config","parent")){
            var current=new AbilityProgress();current.changeCategoryClassic("meltdowner");current.learn(HighPowerSkills.DUAL_POINT);
            var battery=new Battery(2340);process.start(battery,DevelopmentActions.skill(current,skill));for(int tick=0;tick<77;tick++)process.tick();
            try{
                switch(invalidation){
                    case "category"->current.changeCategoryClassic("electromaster");
                    case "level"->current.setLevel(0);
                    case "config"->SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("meltdowner",skill.id()),new ClassicSkillConfiguration.Flags(false,false)));
                    case "parent"->current.unlearn(HighPowerSkills.DUAL_POINT);
                }
                process.tick();check(process.state()==DevelopmentProcess.State.FAILED&&battery.energy==0&&!current.learned(skill.id()),"NEW213 final validation rejects late "+invalidation+" without refund");
            }finally{SkillCatalog.resetConfiguration();}
        }
        try{
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("meltdowner",skill.id()),new ClassicSkillConfiguration.Flags(false,false)));
            check(!HighPowerSkills.eligible(md,skill.id())&&!PresetSkills.selectable(md,skill.id())&&AbilityStorage.encodeSaved(md).equals(saved),"NEW213 config disable preserves learned state and raw mappings while blocking use");
            var snapshot=SkillCatalog.configurationSnapshot();SkillCatalog.resetConfiguration();
            check(SkillCatalog.applyConfigurationSnapshot(snapshot)&&!SkillCatalog.enabled("meltdowner",skill.id()),"NEW213 live gate roundtrips bounded config snapshot");
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("meltdowner",HighPowerSkills.DUAL_POINT),new ClassicSkillConfiguration.Flags(false,false)));
            var noParent=new AbilityProgress();noParent.changeCategoryClassic("meltdowner");
            check(SkillCatalog.requirements(skill).equals(skill.requirements())&&!DevelopmentActions.canLearn(noParent,DeveloperType.PORTABLE,skill),"disabled NEW211 cannot erase NEW213's learned-parent requirement");
        }finally{SkillCatalog.resetConfiguration();}
        String configSource=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cn/academy/port/AcademyConfig.java"));
        check(configSource.contains("return !(key.location().equals(\"electromaster\")&&key.skill().equals(\"magnetic_ground_step\"));"),"NEW213 registration preserves NEW132 as the sole default-off release gate");
        var max=new HighPowerLearningProtocol.Request(skill.id(),Integer.MAX_VALUE);
        check(max.wire().equals("beam_etching:~zik0zj")&&("00000000-0000-0000-0000-000000000000:"+max.wire()).getBytes(StandardCharsets.UTF_8).length==57&&max.equals(HighPowerLearningProtocol.parse(max.wire())),"NEW213 full canonical learning ID and maximum sequence fit the existing 64-byte machine request");
        check(("3:3:"+skill.id()).length()<=64&&new HighPowerInput.Input(3,Long.MAX_VALUE,Integer.MAX_VALUE,Long.MAX_VALUE).wire().length()<=64,"NEW213 preset and slot input retain existing bounded wire schema");
    }

    private static void beamEtchingLayout()throws Exception{
        var nodes=HighPowerDeveloperLayout.category("meltdowner");
        var node=nodes.stream().filter(n->n.id().equals(HighPowerSkills.BEAM_ETCHING)).findFirst().orElseThrow();
        var parent=nodes.getFirst();
        check(node.index()==213&&node.level()==1&&node.x()==92.1&&node.y()==10.6&&node.parent().equals(HighPowerSkills.DUAL_POINT),"NEW213 has a distinct MD LV1 node after learned NEW211");
        check(parent.x()==160&&parent.y()==110&&nodes.get(1).x()==83&&nodes.get(1).y()==87,"NEW213 retains exact NEW211 and NEW212 coordinates");
        check(node.conditions().size()==2&&node.conditions().getFirst().get("minimum").getAsString().equals("portable")&&node.conditions().get(1).get("id").getAsString().equals(HighPowerSkills.DUAL_POINT),"NEW213 advertises its real Portable and learned-root conditions");
        check(node.hintIcon().equals("academy:textures/abilities/meltdowner/skills/beam_etching.png")&&node.nameKey().equals("ac.ability.meltdowner.beam_etching.name")&&node.descriptionKey().equals("ac.ability.meltdowner.beam_etching.desc"),"NEW213 has exact localized resource keys");
        var full=HighPowerDeveloperLayout.beamEtchingDependency(1);
        check(full.size()==4&&HighPowerDeveloperLayout.beamEtchingDependency(0).isEmpty()&&HighPowerDeveloperLayout.beamEtchingDependency(Double.NaN).isEmpty(),"NEW213 routed link reveals only after its normal timeline starts");
        var first=full.getFirst();var last=full.getLast();
        check(Math.abs(Math.hypot(first.x0()-parent.x()-8,first.y0()-parent.y()-8)-12.2)<1e-10&&Math.abs(Math.hypot(last.x1()-node.x()-8,last.y1()-node.y()-8)-12.2)<1e-10,"NEW213 clips both dependency endpoints by the original inset");
        // Preserve the retired combined-tree route oracle; NEW231 exists only on the independently checked current page.
        for(var other:ClassicDeveloperLayout.category("meltdowner"))if(!other.id().equals(node.id())&&!other.id().equals(HighPowerSkills.DISINTEGRATION_SWEEP)){
            double dx=Math.abs(node.x()-other.x()),dy=Math.abs(node.y()-other.y());
            check(dx>=16||dy>=16,"NEW213 hit box avoids "+other.id());
            check(Math.hypot(dx,dy)>=37.2,"NEW213 complete circular hover avoids "+other.id());
            if(!other.id().equals(parent.id()))for(var edge:full){
                check(java.awt.geom.Line2D.ptSegDist(edge.x0(),edge.y0(),edge.x1(),edge.y1(),other.x()+8,other.y()+8)>=19.6,"NEW213 entire 2px dependency avoids unrelated full-hover circle "+other.id());
                check(!new java.awt.geom.Rectangle2D.Double(other.x()-Math.abs(edge.nx()),other.y()-Math.abs(edge.ny()),16+2*Math.abs(edge.nx()),16+2*Math.abs(edge.ny())).intersectsLine(edge.x0(),edge.y0(),edge.x1(),edge.y1()),"NEW213 dependency avoids unrelated hit box "+other.id());
            }
        }
        double total=full.stream().mapToDouble(e->Math.hypot(e.x1()-e.x0(),e.y1()-e.y0())).sum();
        for(int sample=0;sample<=100;sample++){
            double reveal=sample/100d;var edges=HighPowerDeveloperLayout.beamEtchingDependency(reveal);
            check(Math.abs(edges.stream().mapToDouble(e->Math.hypot(e.x1()-e.x0(),e.y1()-e.y0())).sum()-total*reveal)<1e-9,"NEW213 link reveal travels continuously from parent to child");
            for(int i=0;i<edges.size();i++){
                var edge=edges.get(i);
                check(Math.abs(Math.hypot(edge.nx(),edge.ny())-1)<1e-10,"NEW213 keeps its narrow two-pixel route");
                check(Math.min(edge.x0(),edge.x1())-Math.abs(edge.nx())>=0&&Math.max(edge.x0(),edge.x1())+Math.abs(edge.nx())<=257&&Math.min(edge.y0(),edge.y1())-Math.abs(edge.ny())>=0&&Math.max(edge.y0(),edge.y1())+Math.abs(edge.ny())<=139,"NEW213 full dependency width stays inside original tree bounds");
                if(i>0)check(edge.x0()==edges.get(i-1).x1()&&edge.y0()==edges.get(i-1).y1(),"NEW213 route segments meet without a gap");
            }
        }
        check(node.x()+8-18.6>=0&&node.x()+8+18.6<=257&&node.y()+8-18.6>=0&&node.y()+8+18.6<=139,"NEW213 full hover stays inside original developer canvas");
        try(var original=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/meltdowner/skills/mine_ray_basic.png");var reused=HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/textures/abilities/meltdowner/skills/beam_etching.png")){
            check(original!=null&&reused!=null&&java.util.Arrays.equals(original.readAllBytes(),reused.readAllBytes()),"NEW213 retains inspected mining-ray icon bytes without unlicensed image edits");
        }
        check(HighPowerProgressionRegressionTest.class.getResource("/assets/academy/textures/abilities/meltdowner/skills/beam_etching.LICENSE.txt")!=null,"NEW213 copied mining icon retains its attribution notice");
        for(String lang:List.of("en_us","zh_cn","zh_tw","ja_jp"))try(var reader=new InputStreamReader(HighPowerProgressionRegressionTest.class.getResourceAsStream("/assets/academy/lang/"+lang+".json"),StandardCharsets.UTF_8)){
            var dictionary=JsonParser.parseReader(reader).getAsJsonObject();
            String authoritativeName=switch(lang){
                case "zh_cn"->"束端刻线〔高数值〕";
                case "zh_tw"->"束端刻線〔高數值〕";
                case "ja_jp"->"ビーム先端刻線［高出力］";
                default->"Beam-Tip Etching [High Power]";
            };
            check(dictionary.get(node.nameKey()).getAsString().equals(authoritativeName),"NEW213 keeps the exact authoritative workbook name 束端刻线 and its faithful localized display name "+lang);
            for(String status:List.of("unavailable","no_success"))check(dictionary.has("academy.highpower.beam_etching."+status)&&!dictionary.get("academy.highpower.beam_etching."+status).getAsString().isBlank(),"NEW213 truthful native status is localized "+lang+"/"+status);
        }
    }

    private static void scatterPlatesProgression()throws Exception{
        var skill=SkillCatalog.find("meltdowner",HighPowerSkills.SCATTER_PLATES).orElseThrow();
        check(skill.id().equals("scatter_plates")&&HighPowerSkills.SCATTER_PLATES_PROPOSAL==212&&skill.level()==1&&skill.controllable(),"NEW212 uses canonical saved ID, proposal and integer LV1");
        check(skill.requirements().equals(List.of(new SkillCatalog.Requirement(HighPowerSkills.DUAL_POINT,0)))&&skill.anyLearnedSkillLevel()==0,"NEW212 requires exactly learned NEW211 with no mastery or classic prerequisite");
        check(HighPowerSkills.ALL.size() == 51 && cn.academy.port.highpower.HighPowerSkills.ALL.get(50).equals(new cn.academy.port.SkillCatalog.Skill("meltdowner", "remote_scatter_relay", 5, true, java.util.List.of(new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)), 0)) && cn.academy.port.highpower.HighPowerSkills.ALL.get(49).id().equals("point_meltdown") && HighPowerSkills.ALL.get(48).id().equals("wind_pressure_lift") && HighPowerSkills.ALL.get(47).id().equals("multi_axis_reflection") && HighPowerSkills.ALL.get(45).id().equals("circulation_listening") && HighPowerSkills.ALL.get(44).id().equals("circulation_relief") && HighPowerSkills.ALL.get(43).id().equals("trajectory_return") && HighPowerSkills.ALL.get(42).id().equals("yaw_palm") && HighPowerSkills.ALL.get(41).id().equals("inertial_anchor") && HighPowerSkills.ALL.get(40).id().equals("shear_windblade") && HighPowerSkills.ALL.get(39).id().equals("braking_touch") && HighPowerSkills.ALL.get(30).id().equals(HighPowerSkills.CORNER_EXCHANGE) && HighPowerSkills.ALL.get(31).id().equals(HighPowerSkills.SHORT_INTERCEPTION)
                && HighPowerSkills.ALL.get(32).id().equals(HighPowerSkills.SPATIAL_RELAY)
                && HighPowerSkills.ALL.get(33).id().equals(HighPowerSkills.EMERGENCY_RETURN)
                && HighPowerSkills.ALL.get(34).id().equals(HighPowerSkills.TACTICAL_COORDINATE_FORMATION)
                && SkillCatalog.REGISTERED.get(84).equals(HighPowerSkills.ALL.get(34))&&HighPowerSkills.ALL.stream().allMatch(entry->List.of("electromaster","meltdowner","teleporter","vecmanip").contains(entry.category()))
                && HighPowerSkills.ALL.get(35).id().equals(HighPowerSkills.SPATIAL_PINCER_CORRIDOR)
                && SkillCatalog.REGISTERED.get(85).equals(HighPowerSkills.ALL.get(35)),"thirty-eight additions preserve retained ordinals and remain explicitly limited to EM, MD, TP and vector");
        check(SkillCatalog.REGISTERED_CONFIG_KEYS.contains(ClassicSkillConfiguration.key("meltdowner",skill.id()))&&SkillCatalog.enabled("meltdowner",skill.id()),"NEW212 receives registered enabled config defaults");
        var md=new AbilityProgress();md.changeCategoryClassic("meltdowner");md.setActivateState(true);
        check(!DevelopmentActions.canLearn(md,DeveloperType.PORTABLE,skill)&&!SkillCatalog.canLearn(md,skill),"fresh MD cannot skip learned NEW211");
        md.learn("electron_bomb");md.learn("light_shield");
        check(!DevelopmentActions.canLearn(md,DeveloperType.PORTABLE,skill),"classic MD skills cannot substitute for NEW211");
        md.learn(HighPowerSkills.DUAL_POINT);
        check(md.exp(HighPowerSkills.DUAL_POINT)==0&&DevelopmentActions.canLearn(md,DeveloperType.PORTABLE,skill)&&SkillCatalog.canLearn(md,skill),"zero-mastery learned NEW211 unlocks genuine Portable learning");
        check(!HighPowerSkills.eligible(md,skill.id())&&!PresetSkills.edit(md,3,3,skill.id()),"unlearned NEW212 cannot cast or bind");
        var action=DevelopmentActions.skill(md,skill);var process=new DevelopmentProcess();
        check(action.stimulations()==3&&DeveloperType.PORTABLE.ticksPerStimulation()==26&&DeveloperType.PORTABLE.actualConsumption(action.stimulations())==2340,"NEW212 preserves three native stimulations, 78 paid ticks and 2340 IF");
        var before=AbilityStorage.encodeSaved(md);var shortBattery=new Battery(2339);process.start(shortBattery,action);
        for(int tick=0;tick<77;tick++)process.tick();
        check(shortBattery.energy==29&&!md.learned(skill.id())&&AbilityStorage.encodeSaved(md).equals(before),"NEW212 shortage reaches the last tick without early learning");
        process.tick();check(process.state()==DevelopmentProcess.State.FAILED&&shortBattery.energy==0&&AbilityStorage.encodeSaved(md).equals(before),"NEW212 one-IF shortage drains only the remaining battery with no grant or refund");
        var paid=new Battery(2340);process.start(paid,action);for(int tick=0;tick<77;tick++)process.tick();
        check(paid.energy==30&&!md.learned(skill.id())&&process.state()==DevelopmentProcess.State.DEVELOPING,"NEW212 must wait for the final paid tick");
        process.tick();check(paid.energy==0&&process.state()==DevelopmentProcess.State.DONE&&md.learned(skill.id())&&md.exp(skill.id())==0&&md.levelExperience==0,"NEW212 learns at exact native cost without free mastery or level XP");
        check(HighPowerSkills.eligible(md,skill.id())&&PresetSkills.edit(md,3,2,HighPowerSkills.DUAL_POINT)&&PresetSkills.edit(md,3,3,skill.id()),"NEW211 and NEW212 occupy ordinary adjacent existing slots");
        var saved=AbilityStorage.encodeSaved(md);var restored=AbilityStorage.decodeSaved(saved,md.cpDataConfig());
        check(saved.getInt("schema")==3&&restored.learned(skill.id())&&restored.presets.skill(3,2).equals(HighPowerSkills.DUAL_POINT)&&restored.presets.skill(3,3).equals(skill.id())&&AbilityStorage.encodeSaved(restored).equals(saved),"NEW212 learned bit and canonical presets roundtrip unchanged schema without disturbing NEW211");
        var pre212=before.copy();var oldSave=AbilityStorage.decodeSaved(pre212,md.cpDataConfig());
        check(!oldSave.learned(skill.id())&&AbilityStorage.encodeSaved(oldSave).equals(pre212),"pre-NEW212 save never grants the addition or rewrites existing state");
        check(SkillCatalog.levelSkillCount(md)==1&&SkillCatalog.ALL.size()==50&&PresetSkills.IMPLEMENTED.size()==35,"NEW212 never changes classic MD LV1 denominator, ordinals or active route count");
        for(String category:List.of("electromaster","teleporter","vecmanip")){
            var wrong=AbilityStorage.decodeSaved(saved,md.cpDataConfig());wrong.category=category;
            check(!DevelopmentActions.canLearn(wrong,DeveloperType.PORTABLE,skill)&&!HighPowerSkills.eligible(wrong,skill.id())&&!PresetSkills.selectable(wrong,skill.id()),"stored NEW212 cannot cross category gate "+category);
        }
        restored.setLevel(0);
        check(restored.learned(skill.id())&&!HighPowerSkills.eligible(restored,skill.id())&&!PresetSkills.selectable(restored,skill.id()),"stored NEW212 cannot bypass an integer level downgrade");
        for(String invalidation:List.of("category","level","config","parent")){
            var current=new AbilityProgress();current.changeCategoryClassic("meltdowner");current.learn(HighPowerSkills.DUAL_POINT);
            var battery=new Battery(2340);process.start(battery,DevelopmentActions.skill(current,skill));for(int tick=0;tick<77;tick++)process.tick();
            try{
                switch(invalidation){
                    case "category"->current.changeCategoryClassic("electromaster");
                    case "level"->current.setLevel(0);
                    case "config"->SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("meltdowner",skill.id()),new ClassicSkillConfiguration.Flags(false,false)));
                    case "parent"->current.unlearn(HighPowerSkills.DUAL_POINT);
                }
                process.tick();check(process.state()==DevelopmentProcess.State.FAILED&&battery.energy==0&&!current.learned(skill.id()),"NEW212 final validation rejects late "+invalidation+" without refund");
            }finally{SkillCatalog.resetConfiguration();}
        }
        try{
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("meltdowner",skill.id()),new ClassicSkillConfiguration.Flags(false,false)));
            check(!HighPowerSkills.eligible(md,skill.id())&&!PresetSkills.selectable(md,skill.id())&&AbilityStorage.encodeSaved(md).equals(saved),"NEW212 config disable preserves learned state and raw mappings while blocking use");
            var snapshot=SkillCatalog.configurationSnapshot();SkillCatalog.resetConfiguration();
            check(SkillCatalog.applyConfigurationSnapshot(snapshot)&&!SkillCatalog.enabled("meltdowner",skill.id()),"NEW212 live gate roundtrips bounded config snapshot");
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("meltdowner",HighPowerSkills.DUAL_POINT),new ClassicSkillConfiguration.Flags(false,false)));
            var noParent=new AbilityProgress();noParent.changeCategoryClassic("meltdowner");
            check(SkillCatalog.requirements(skill).equals(skill.requirements())&&!DevelopmentActions.canLearn(noParent,DeveloperType.PORTABLE,skill),"disabled NEW211 cannot erase NEW212's learned-parent requirement");
        }finally{SkillCatalog.resetConfiguration();}
        var max=new HighPowerLearningProtocol.Request(skill.id(),Integer.MAX_VALUE);
        check(max.wire().equals("scatter_plates:~zik0zj")&&("00000000-0000-0000-0000-000000000000:"+max.wire()).getBytes(StandardCharsets.UTF_8).length==59&&max.equals(HighPowerLearningProtocol.parse(max.wire())),"NEW212 full canonical learning ID and maximum sequence fit the existing 64-byte machine request");
        check(("3:3:"+skill.id()).length()<=64&&new HighPowerInput.Input(3,Long.MAX_VALUE,Integer.MAX_VALUE,Long.MAX_VALUE).wire().length()<=64,"NEW212 preset and slot input retain existing bounded wire schema");
    }

    private static void scatterPlatesLayout(){
        var node=HighPowerDeveloperLayout.category("meltdowner").stream().filter(n->n.id().equals(HighPowerSkills.SCATTER_PLATES)).findFirst().orElseThrow();
        check(node.index()==212&&node.level()==1&&node.x()==83&&node.y()==87&&node.parent().equals(HighPowerSkills.DUAL_POINT),"NEW212 exact proposal and parent occupy the new MD position 83,87");
        check(node.hintIcon().equals("academy:textures/abilities/meltdowner/skills/scatter_plates.png")&&node.nameKey().equals("ac.ability.meltdowner.scatter_plates.name")&&node.descriptionKey().equals("ac.ability.meltdowner.scatter_plates.desc"),"NEW212 canonical MD resource keys");
        var tier=node.conditions().stream().filter(c->c.get("type").getAsString().equals("developer_type")).findFirst().orElseThrow();
        var parent=node.conditions().stream().filter(c->c.get("type").getAsString().equals("skill_dependency")).findFirst().orElseThrow();
        check(node.conditions().size()==2&&tier.get("minimum").getAsString().equals("portable")&&parent.get("id").getAsString().equals(HighPowerSkills.DUAL_POINT)&&parent.get("minimum_exp").getAsDouble()==0,"NEW212 tree advertises Portable and zero-mastery learned NEW211");
        var root=HighPowerDeveloperLayout.category("meltdowner").getFirst();
        var edge=cn.academy.port.client.ClassicDeveloperTimeline.edge(node.x(),node.y(),root.x(),root.y(),1);
        // These exact source-coordinate guards predate MD pagination. Preserve their entire
        // original 26-node domain; NEW253 is rendered only in the separate high-power page below.
        var retainedSourceNodes=ClassicDeveloperLayout.category("meltdowner").stream().filter(n->!n.id().equals(HighPowerSkills.REMOTE_SCATTER_RELAY)).toList();
        check(retainedSourceNodes.size()==26&&retainedSourceNodes.stream().map(ClassicDeveloperLayout.Node::id).toList().equals(java.util.stream.Stream.concat(ClassicDeveloperLayout.NODES.stream().filter(n->n.category().equals("meltdowner")),HighPowerDeveloperLayout.category("meltdowner").stream().limit(12)).map(ClassicDeveloperLayout.Node::id).toList()),"NEW212 source-coordinate guards still cover every original classic and .76 high-power node in order");
        for(var other:retainedSourceNodes)if(!other.id().equals(node.id())){
            double dx=Math.abs(node.x()-other.x()),dy=Math.abs(node.y()-other.y());
            check(dx>=16||dy>=16,"NEW212 selection box avoids "+other.id());
            check(Math.hypot(dx,dy)>=37.2,"NEW212 full circular hover outline avoids "+other.id());
            if(!other.id().equals(root.id()))check(!new java.awt.geom.Rectangle2D.Double(other.x()-Math.abs(edge.nx()),other.y()-Math.abs(edge.ny()),16+2*Math.abs(edge.nx()),16+2*Math.abs(edge.ny())).intersectsLine(edge.x0(),edge.y0(),edge.x1(),edge.y1()),"NEW212 full-width dependency link avoids unrelated selection box "+other.id());
        }
        check(node.x()+8-18.6>=0&&node.x()+8+18.6<=257&&node.y()+8-18.6>=0&&node.y()+8+18.6<=139,"NEW212 full hover remains inside the original developer canvas");
        var page=HighPowerDeveloperLayout.page("meltdowner",HighPowerDeveloperLayout.Page.HIGH_POWER);
        var shown=page.stream().filter(n->n.id().equals(HighPowerSkills.SCATTER_PLATES)).findFirst().orElseThrow();
        var relay=page.stream().filter(n->n.id().equals(HighPowerSkills.REMOTE_SCATTER_RELAY)).findFirst().orElseThrow();
        check(page.size()==13&&shown.x()==110&&shown.y()==29&&relay.x()==75&&relay.y()==61
                &&Math.hypot(node.x()-relay.x(),node.y()-relay.y())<37.2
                &&Math.hypot(shown.x()-relay.x(),shown.y()-relay.y())>=37.2,
                "the old source-coordinate overlap is not a displayed overlap: actual NEW212/253 page positions are distinct");
        check(HighPowerDeveloperLayout.page("meltdowner",HighPowerDeveloperLayout.Page.CLASSIC).stream().noneMatch(n->n.id().equals(shown.id())||n.id().equals(relay.id()))
                &&pageHoverCirclesClear(page),"all thirteen actually displayed MD high-power nodes retain full-hover clearance");
        var overlappingPage=new java.util.ArrayList<>(page);
        overlappingPage.set(page.indexOf(relay),new ClassicDeveloperLayout.Node(relay.category(),relay.id(),relay.index(),relay.level(),shown.x(),shown.y(),relay.parent(),relay.enabled(),relay.hintIcon(),relay.nameKey(),relay.descriptionKey(),relay.conditions()));
        check(!pageHoverCirclesClear(overlappingPage),"negative control: moving NEW253 onto the visible NEW212 position is rejected, so the added node is not skipped");
    }

    private static boolean pageHoverCirclesClear(List<ClassicDeveloperLayout.Node> nodes){
        for(int first=0;first<nodes.size();first++)for(int second=first+1;second<nodes.size();second++){
            var a=nodes.get(first);var b=nodes.get(second);
            if(Math.hypot(a.x()-b.x(),a.y()-b.y())<37.2)return false;
        }
        return true;
    }

    private static void dualPointProgression()throws Exception{
        var skill=SkillCatalog.find("meltdowner",HighPowerSkills.DUAL_POINT).orElseThrow();
        check(skill.id().equals("dual_point")&&HighPowerSkills.DUAL_POINT_PROPOSAL==211&&skill.level()==1&&skill.controllable(),"NEW211 has its canonical short ID, proposal and actual integer LV1");
        check(skill.requirements().isEmpty()&&skill.anyLearnedSkillLevel()==0,"NEW211 is a root with no invented classic prerequisite");
        check(HighPowerSkills.contains("meltdowner",skill.id())&&!HighPowerSkills.contains("electromaster",skill.id())&&HighPowerSkills.CATEGORY.equals("electromaster"),"category-aware extension identity retains the old electromaster alias");
        var md=new AbilityProgress();md.changeCategoryClassic("meltdowner");md.setActivateState(true);
        check(md.level==1&&DevelopmentActions.canLearn(md,DeveloperType.PORTABLE,skill)&&SkillCatalog.canLearn(md,skill),"fresh native MD LV1 can learn the root without any classic learned skill");
        check(!HighPowerSkills.eligible(md,skill.id())&&!PresetSkills.edit(md,0,0,skill.id()),"unlearned NEW211 cannot cast or bind");
        var action=DevelopmentActions.skill(md,skill);var process=new DevelopmentProcess();
        check(action.stimulations()==3&&DeveloperType.PORTABLE.ticksPerStimulation()==26&&DeveloperType.PORTABLE.energyPerTick()==30&&DeveloperType.PORTABLE.actualConsumption(action.stimulations())==2340,"NEW211 uses three native portable stimulations, 78 paid ticks and exactly 2340 IF");
        String screen=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cn/academy/port/client/ClassicDeveloperScreen.java"));
        check(screen.contains("if(current.kind.equals(\"skill\")&&cn.academy.port.highpower.HighPowerSkills.contains(current.node.id()))return developerType.actualConsumption(stimulations);")&&screen.contains("if(energy()<estimated(current)){current.message=local(\"noenergy\");return;}"),"NEW211 uses the actual finite IF preview and the same upfront short-budget check");
        var before=AbilityStorage.encodeSaved(md);var shortBattery=new Battery(2339);process.start(shortBattery,action);
        for(int tick=0;tick<77;tick++)process.tick();
        check(!md.learned(skill.id())&&shortBattery.energy==29&&AbilityStorage.encodeSaved(md).equals(before),"one-IF-short NEW211 budget reaches final tick with no premature player mutation");
        process.tick();check(process.state()==DevelopmentProcess.State.FAILED&&shortBattery.energy==0&&AbilityStorage.encodeSaved(md).equals(before),"last paid tick shortage drains the remainder without learning or refund");
        var paid=new Battery(2340);process.start(paid,action);
        for(int tick=0;tick<77;tick++)process.tick();
        check(!md.learned(skill.id())&&paid.energy==30&&process.state()==DevelopmentProcess.State.DEVELOPING,"NEW211 waits for every native paid tick");
        process.tick();check(process.state()==DevelopmentProcess.State.DONE&&paid.energy==0&&md.learned(skill.id())&&md.exp(skill.id())==0&&md.levelExperience==0,"NEW211 exact finite completion grants only the root, with no free mastery or level XP");
        check(HighPowerSkills.eligible(md,skill.id())&&PresetSkills.edit(md,3,3,skill.id()),"earned NEW211 uses the existing fourth-page fourth slot");
        check(HighPowerSkills.effectiveLevel(md,5)==0,"legacy electromaster effective-level wrapper stays EM-only");
        var saved=AbilityStorage.encodeSaved(md);var restored=AbilityStorage.decodeSaved(saved,md.cpDataConfig());
        check(saved.getInt("schema")==3&&restored.learned(skill.id())&&restored.presets.skill(3,3).equals(skill.id())&&AbilityStorage.encodeSaved(restored).equals(saved),"NEW211 uses unchanged learned and preset save schema");
        for(String category:List.of("electromaster","teleporter","vecmanip")){
            var wrong=AbilityStorage.decodeSaved(saved,md.cpDataConfig());wrong.category=category;
            check(!HighPowerSkills.eligible(wrong,skill.id())&&!PresetSkills.selectable(wrong,skill.id())&&!DevelopmentActions.canLearn(wrong,DeveloperType.PORTABLE,skill),"stale learned NEW211 cannot cross category gate "+category);
        }
        for(String category:List.of("electromaster","meltdowner"))for(int level=1;level<=5;level++){
            var counted=new AbilityProgress();counted.changeCategoryClassic(category);counted.setLevel(level);
            int actual=level;
            check(SkillCatalog.levelSkillCount(counted)==SkillCatalog.ALL.stream().filter(entry->entry.category().equals(category)&&entry.level()==actual&&entry.controllable()&&SkillCatalog.enabled(category,entry.id())).count(),"all shared-category XP denominators remain canonical "+category+"/"+level);
        }
        // Native category change deliberately removes learned skills and all mappings while
        // retaining level, progress and training. Shared ledgers must survive the required relearning.
        md.setLevel(3);md.levelExperience=.375;md.extraCp=2.5;md.extraOverload=1.25;
        md.changeCategoryClassic("electromaster");
        check(md.level==3&&md.levelExperience==.375&&md.extraCp==2.5&&md.extraOverload==1.25&&md.experience.isEmpty(),"native category change retains level/progress/training and clears learned skills");
        for(int preset=0;preset<4;preset++)for(int slot=0;slot<4;slot++)check(md.presets.skill(preset,slot).isEmpty(),"native category change clears preset "+preset+"/"+slot);
        md.changeCategoryClassic("meltdowner");
        check(!md.learned(skill.id())&&!HighPowerSkills.eligible(md,skill.id())&&DevelopmentActions.canLearn(md,DeveloperType.PORTABLE,skill),"returning to MD requires actual relearning and does not restore its old learned bit");
        var off=new ClassicSkillConfiguration.Flags(false,false);
        for(String invalidation:List.of("category","level","config")){
            var current=new AbilityProgress();current.changeCategoryClassic("meltdowner");
            var battery=new Battery(2340);process.start(battery,DevelopmentActions.skill(current,skill));
            for(int tick=0;tick<77;tick++)process.tick();
            try{
                switch(invalidation){
                    case "category"->current.changeCategoryClassic("electromaster");
                    case "level"->current.setLevel(0);
                    case "config"->SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("meltdowner",skill.id()),off));
                }
                process.tick();check(process.state()==DevelopmentProcess.State.FAILED&&battery.energy==0&&!current.learned(skill.id()),"NEW211 final native validation rejects late "+invalidation+" without refund");
            }finally{SkillCatalog.resetConfiguration();}
        }
        // Iterate registrations rather than a hard-coded electromaster allowlist: future
        // additions must also honor their own category's disabled learning/casting gates.
        for(var entry:HighPowerSkills.ALL)try{
            var candidate=new AbilityProgress();candidate.changeCategoryClassic(entry.category());candidate.setLevel(5);candidate.setActivateState(true);
            for(var parent:entry.requirements())candidate.learn(parent.id());
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key(entry.category(),entry.id()),off));
            check(!DevelopmentActions.canLearn(candidate,DeveloperType.ADVANCED,entry)&&!SkillCatalog.canLearn(candidate,entry),"every registered and future extension rejects disabled learning "+entry.category()+"/"+entry.id());
            candidate.learn(entry.id());check(!HighPowerSkills.eligible(candidate,entry.id())&&!PresetSkills.selectable(candidate,entry.id()),"every registered and future extension rejects disabled casting and assignment "+entry.id());
        }finally{SkillCatalog.resetConfiguration();}
        var max=new HighPowerLearningProtocol.Request(skill.id(),Integer.MAX_VALUE);
        check(max.wire().equals("dual_point:~zik0zj")&&("00000000-0000-0000-0000-000000000000:"+max.wire()).getBytes(StandardCharsets.UTF_8).length==55&&max.equals(HighPowerLearningProtocol.parse(max.wire())),"NEW211 short canonical ID fits the original 64-byte machine request at maximum sequence");
        check(HighPowerSkills.ALL.stream().filter(entry->entry.category().equals("meltdowner")).count()==13&&HighPowerSkills.ALL.get(50).equals(new SkillCatalog.Skill("meltdowner","remote_scatter_relay",5,true,List.of(new SkillCatalog.Requirement("folded_mirror_array",0)),0))&&HighPowerLearningProtocol.parse("new212:~1")==null&&HighPowerLearningProtocol.parse("new213:~1")==null&&HighPowerLearningProtocol.parse("new221:~1")==null,"thirteen explicit canonical MD additions admit only appended NEW253; no proposal aliases or future ID ingress");
    }

    private static void retainedMeltdownerPageAfter253() {
        String[] ids = {"dual_point", "scatter_plates", "beam_etching", "thermal_shell", "folded_mirror_array", "disintegration_sweep", "beam_step", "point_meltdown", "particle_laminate", "directional_dissipation_curtain", "multicore_canopy", "zero_gap_armor"};
        int[] proposals = {211, 212, 213, 222, 221, 231, 232, 233, 241, 242, 251, 252};
        int[] levels = {1, 1, 1, 2, 2, 3, 3, 3, 4, 4, 5, 5};
        String[] parents = {"", "dual_point", "dual_point", "scatter_plates", "dual_point", "folded_mirror_array", "thermal_shell", "beam_etching", "disintegration_sweep", "scatter_plates", "particle_laminate", "directional_dissipation_curtain"};
        double[][] rawPositions = {{160,110},{83,87},{92.1,10.6},{230,110},{206,97},{160,105},{50,20},{20,105},{225,63},{142,40},{8,110},{160,61}};
        double[][] pagePositions = {{26,61},{110,29},{110,97},{206,29},{206,97},{160,105},{50,20},{20,105},{225,63},{160,20},{62,106},{160,61}};
        var raw = HighPowerDeveloperLayout.category("meltdowner");
        var page = HighPowerDeveloperLayout.page("meltdowner", HighPowerDeveloperLayout.Page.HIGH_POWER);
        check(raw.size() == 13 && page.size() == 13, "NEW253 is the sole thirteenth MD source/page node");
        for (int i = 0; i < ids.length; i++) {
            var prior = raw.get(i); var shown = page.get(i);
            check(prior.category().equals("meltdowner") && prior.id().equals(ids[i]) && prior.index() == proposals[i]
                    && prior.level() == levels[i] && prior.parent().equals(parents[i])
                    && prior.x() == rawPositions[i][0] && prior.y() == rawPositions[i][1],
                    "the exact .76 MD source node and ordinal remain unchanged: " + ids[i]);
            check(shown.category().equals(prior.category()) && shown.id().equals(prior.id()) && shown.index() == prior.index()
                    && shown.level() == prior.level() && shown.parent().equals(prior.parent())
                    && shown.x() == pagePositions[i][0] && shown.y() == pagePositions[i][1]
                    && shown.conditions().equals(prior.conditions()) && shown.hintIcon().equals(prior.hintIcon())
                    && shown.nameKey().equals(prior.nameKey()) && shown.descriptionKey().equals(prior.descriptionKey()),
                    "the exact .76 MD display position and identity remain unchanged: " + ids[i]);
        }
        for (var node : List.of(raw.get(12), page.get(12)))
            check(node.category().equals("meltdowner") && node.id().equals("remote_scatter_relay") && node.index() == 253
                    && node.level() == 5 && node.x() == 75 && node.y() == 61 && node.parent().equals("folded_mirror_array")
                    && node.conditions().size() == 2 && node.conditions().getFirst().get("minimum").getAsString().equals("advanced")
                    && node.conditions().get(1).get("type").getAsString().equals("skill_dependency")
                    && node.conditions().get(1).get("id").getAsString().equals("folded_mirror_array")
                    && node.conditions().get(1).get("minimum_exp").getAsDouble() == 0,
                    "NEW253 appends its precise LV5 node at 75,61 with Advanced and zero-mastery NEW221 conditions");
    }

    private static void dualPointLayout(){
        var additions=HighPowerDeveloperLayout.category("meltdowner");
        check(additions.size()==13&&ClassicDeveloperLayout.category("meltdowner").size()==27,"the original fourteen-node MD tree plus twelve retained additions and NEW253 total twenty-seven identities");
        retainedMeltdownerPageAfter253();
        var node=additions.getFirst();
        check(node.id().equals(HighPowerSkills.DUAL_POINT)&&node.index()==211&&node.level()==1&&node.x()==160&&node.y()==110&&node.parent().isEmpty(),"NEW211 root occupies the free MD position 160,110");
        check(node.hintIcon().equals("academy:textures/abilities/meltdowner/skills/dual_point.png")&&node.nameKey().equals("ac.ability.meltdowner.dual_point.name")&&node.descriptionKey().equals("ac.ability.meltdowner.dual_point.desc"),"NEW211 uses MD-specific canonical resource keys");
        check(node.conditions().size()==1&&node.conditions().getFirst().get("type").getAsString().equals("developer_type")&&node.conditions().getFirst().get("minimum").getAsString().equals("portable"),"NEW211 advertises Portable learning without any hidden parent");
        for(var other:ClassicDeveloperLayout.NODES)if(other.category().equals("meltdowner")){
            double dx=Math.abs(node.x()-other.x()),dy=Math.abs(node.y()-other.y());
            check(dx>=16||dy>=16,"NEW211 selection box does not overlap "+other.id());
            check(dx>=37.2||dy>=37.2,"NEW211 full-hover outline does not overlap "+other.id());
        }
        check(node.x()+8-18.6>=0&&node.x()+8+18.6<=257&&node.y()+8-18.6>=0&&node.y()+8+18.6<=139,"NEW211 full-hover outline stays within the original developer canvas");
        var old=HighPowerDeveloperLayout.category("electromaster");
        check(old.size()==13&&old.get(12).id().equals(HighPowerSkills.MAGNETIC_DOMAIN)&&old.get(12).x()==94&&old.get(12).y()==93,"MD addition does not move, remove or append to the thirteen-node EM layout");
    }

    private static void railThunderArrayProgression(AbilityProgress preArraySave,String estimateSource,String startCoverSource,String developerScreenSource)throws Exception{
        var array=SkillCatalog.find("electromaster",HighPowerSkills.RAIL_THUNDER_ARRAY).orElseThrow();
        check(array.id().equals("rail_thunder_array")&&HighPowerSkills.RAIL_THUNDER_ARRAY_PROPOSAL==151&&array.level()==5&&array.controllable(),"NEW151 has the canonical saved ID, authoritative proposal and exact integer LV5");
        check(array.requirements().equals(List.of(new SkillCatalog.Requirement(HighPowerSkills.THUNDER_NET,0)))&&array.anyLearnedSkillLevel()==0,"NEW151 requires learned NEW141 without an invented mastery or alternate prerequisite");
        var oldTag=AbilityStorage.encodeSaved(preArraySave);
        var arrayState=AbilityStorage.decodeSaved(oldTag,preArraySave.cpDataConfig());
        check(!arrayState.learned(array.id())&&AbilityStorage.encodeSaved(arrayState).equals(oldTag),"pre-NEW151 save neither grants the addition nor changes any old learned bit or mapping");
        arrayState.learn(HighPowerSkills.POTENTIAL_BUFFER);arrayState.learn("arc_gen");arrayState.setSkillExp("arc_gen",.375f);
        check(PresetSkills.edit(arrayState,3,2,HighPowerSkills.POTENTIAL_BUFFER),"pre-NEW151 fourth-page NEW142 mapping is present beside NEW141");
        for(int level=1;level<=4;level++){
            arrayState.setLevel(level);
            double threshold=cn.academy.port.core.ClassicRules.levelThreshold(level,SkillCatalog.levelSkillCount(arrayState));
            for(double progress:List.of(threshold*.999,threshold)){
                arrayState.levelExperience=progress;
                check(arrayState.level==level&&!DevelopmentActions.canLearn(arrayState,DeveloperType.ADVANCED,array)&&!SkillCatalog.canLearn(arrayState,array),"NEW151 near or complete XP cannot replace genuine integer LV5 at LV"+level);
            }
        }
        arrayState.setLevel(5);
        check(!DevelopmentActions.canLearn(arrayState,DeveloperType.PORTABLE,array)&&!DevelopmentActions.canLearn(arrayState,DeveloperType.NORMAL,array),"NEW151 refuses Portable and Normal developers even at real LV5");
        arrayState.unlearn(HighPowerSkills.THUNDER_NET);
        check(!DevelopmentActions.canLearn(arrayState,DeveloperType.ADVANCED,array)&&!SkillCatalog.canLearn(arrayState,array),"NEW151 learned NEW142 and circuit cannot substitute for learned NEW141");
        arrayState.learn(HighPowerSkills.THUNDER_NET);
        check(arrayState.exp(HighPowerSkills.THUNDER_NET)==0&&DevelopmentActions.canLearn(arrayState,DeveloperType.ADVANCED,array)&&SkillCatalog.canLearn(arrayState,array),"NEW151 actual LV5 and zero-mastery learned NEW141 permit Advanced learning");
        long revisionBefore=arrayState.presets.revision();
        check(!HighPowerSkills.eligible(arrayState,array.id())&&!PresetSkills.selectable(arrayState,array.id())&&!PresetSkills.edit(arrayState,3,1,array.id())&&arrayState.presets.revision()==revisionBefore,"NEW151 cannot cast or mutate a preset before paid learning");
        var action=DevelopmentActions.skill(arrayState,array);var process=new DevelopmentProcess();
        check(cn.academy.port.core.ClassicRules.learningStimulations(5)==15&&action.id().equals("skill:rail_thunder_array")&&action.stimulations()==15,"NEW151 uses the original fifteen LV5 learning stimulations");
        check(DeveloperType.ADVANCED.ticksPerStimulation()==16&&DeveloperType.ADVANCED.energyPerTick()==40&&DeveloperType.ADVANCED.actualConsumption(action.stimulations())==9600,"NEW151 actual Advanced budget is exactly 9600 IF over 240 native ticks");
        check(DeveloperType.ADVANCED.estimatedConsumption(action.stimulations())==9000,"ordinary classic LV5 quote keeps the original 9000 IF estimate");
        check(!action.validate(new Battery(20000,DeveloperType.PORTABLE))&&!action.validate(new Battery(20000,DeveloperType.NORMAL))&&action.validate(new Battery(9600,DeveloperType.ADVANCED)),"NEW151 completion validation enforces Advanced tier");
        check(HighPowerSkills.contains(array.id())&&estimateSource.contains("if(current.kind.equals(\"skill\")&&cn.academy.port.highpower.HighPowerSkills.contains(current.node.id()))return developerType.actualConsumption(stimulations);")&&developerScreenSource.contains("local(\"learn_question\",Long.toString(Math.round(estimated(current))))")&&startCoverSource.contains("if(energy()<estimated(current)){current.message=local(\"noenergy\");return;}"),"NEW151 real registration selects the exact 9600 IF preview and matching upfront short-budget refusal");
        arrayState.cp=123.5;arrayState.overload=17.25;arrayState.extraCp=2.5;arrayState.extraOverload=1.25;
        arrayState.cpDelay=11;arrayState.overloadDelay=13;arrayState.levelExperience=.125;
        var beforeLearning=AbilityStorage.encodeSaved(arrayState);
        var shortBattery=new Battery(9599,DeveloperType.ADVANCED);process.start(shortBattery,action);
        for(int tick=0;tick<239;tick++)process.tick();
        check(!arrayState.learned(array.id())&&process.state()==DevelopmentProcess.State.DEVELOPING&&shortBattery.energy==39&&AbilityStorage.encodeSaved(arrayState).equals(beforeLearning),"NEW151 one-IF-short battery reaches the final tick without early learning or any player-state mutation");
        process.tick();
        check(!arrayState.learned(array.id())&&process.state()==DevelopmentProcess.State.FAILED&&shortBattery.energy==0&&AbilityStorage.encodeSaved(arrayState).equals(beforeLearning),"NEW151 final IF shortage consumes only the finite remainder, grants no learned bit and refunds nothing");
        var paid=new Battery(9600,DeveloperType.ADVANCED);process.start(paid,action);
        for(int tick=0;tick<239;tick++)process.tick();
        check(!arrayState.learned(array.id())&&process.state()==DevelopmentProcess.State.DEVELOPING&&paid.energy==40&&AbilityStorage.encodeSaved(arrayState).equals(beforeLearning),"NEW151 waits for every one of 240 paid native ticks");
        process.tick();
        check(arrayState.learned(array.id())&&arrayState.exp(array.id())==0&&process.state()==DevelopmentProcess.State.DONE&&paid.energy==0,"NEW151 fifteen Advanced stimulations complete for exactly 9600 IF");
        var afterLearning=AbilityStorage.encodeSaved(arrayState);afterLearning.getCompound("skills").remove(array.id());
        var expectedLearning=beforeLearning.copy();expectedLearning.putDouble("cp",arrayState.maxCp());expectedLearning.putDouble("overload",0);
        check(afterLearning.equals(expectedLearning),"NEW151 preserves the original CPData learn-event refill only, every previous mastery entry, level XP, delays, training and preset state");
        check(HighPowerSkills.eligible(arrayState,array.id())&&PresetSkills.selectable(arrayState,array.id()),"NEW151 becomes eligible only after successful real LV5 learning");
        arrayState.cp=123.5;arrayState.overload=17.25;
        var beforeBinding=AbilityStorage.encodeSaved(arrayState);beforeBinding.remove("presets");
        long oldRevision=arrayState.presets.revision();
        check(PresetSkills.edit(arrayState,3,1,array.id())&&arrayState.presets.revision()==oldRevision+1,"NEW151 binds the existing fourth-page right mouse slot in one ordinary edit");
        check(arrayState.presets.current()==0&&arrayState.presets.skill(0,0).equals(HighPowerSkills.FORKED_ARC)&&arrayState.presets.skill(1,1).equals(HighPowerSkills.CONDUCTIVE_CIRCUIT)&&arrayState.presets.skill(3,2).equals(HighPowerSkills.POTENTIAL_BUFFER)&&arrayState.presets.skill(3,3).equals(HighPowerSkills.THUNDER_NET),"NEW151 binding preserves the active page and all old NEW141/142 mappings");
        var afterBinding=AbilityStorage.encodeSaved(arrayState);afterBinding.remove("presets");
        check(afterBinding.equals(beforeBinding),"NEW151 binding cannot refill CP, clear overload, change delays or award mastery or level XP");
        var boundTag=AbilityStorage.encodeSaved(arrayState);var restored=AbilityStorage.decodeSaved(boundTag,arrayState.cpDataConfig());
        check(boundTag.getInt("schema")==3&&restored.learned(array.id())&&restored.exp(array.id())==0&&restored.presets.skill(3,1).equals("rail_thunder_array")&&AbilityStorage.encodeSaved(restored).equals(boundTag),"NEW151 canonical learned bit and preset roundtrip unchanged schema with depleted balances and old mappings intact");
        check(SkillCatalog.levelSkillCount(arrayState)==SkillCatalog.ALL.stream().filter(skill->skill.category().equals(arrayState.category)&&skill.level()==5&&skill.controllable()&&SkillCatalog.enabled(skill.category(),skill.id())).count(),"NEW151 does not become part of the classic LV5 XP denominator");
        check(!arrayState.canLevelUp(SkillCatalog.levelSkillCount(arrayState)),"NEW151 never introduces an integer level above classic LV5");
        restored.setLevel(4);
        check(restored.learned(array.id())&&restored.presets.skill(3,1).equals(array.id())&&!HighPowerSkills.eligible(restored,array.id())&&!PresetSkills.mappedUsable(restored,array.id())&&!PresetSkills.selectable(restored,array.id()),"NEW151 learned bit and binding never bypass a later real level downgrade");
        for(String category:List.of("meltdowner","teleporter","vecmanip")){
            var wrongCategory=AbilityStorage.decodeSaved(boundTag,arrayState.cpDataConfig());wrongCategory.category=category;
            check(!DevelopmentActions.canLearn(wrongCategory,DeveloperType.ADVANCED,array)&&!SkillCatalog.canLearn(wrongCategory,array)&&!HighPowerSkills.eligible(wrongCategory,array.id())&&!PresetSkills.selectable(wrongCategory,array.id())&&!PresetSkills.edit(wrongCategory,2,0,array.id()),"NEW151 stale learned and bound state cannot cross the category gate "+category);
        }
        var off=new ClassicSkillConfiguration.Flags(false,false);
        try{
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("electromaster",array.id()),off));
            var unlearned=state(5);unlearned.learn(HighPowerSkills.THUNDER_NET);
            check(!DevelopmentActions.canLearn(unlearned,DeveloperType.ADVANCED,array)&&!SkillCatalog.canLearn(unlearned,array),"disabled NEW151 fails both learning validators");
            check(!HighPowerSkills.eligible(arrayState,array.id())&&!PresetSkills.selectable(arrayState,array.id())&&!PresetSkills.edit(arrayState,2,0,array.id()),"disabled NEW151 refuses casts and new preset assignments");
            check(arrayState.learned(array.id())&&arrayState.presets.skill(3,1).equals(array.id())&&AbilityStorage.encodeSaved(arrayState).equals(boundTag),"disabling NEW151 preserves saved learning, canonical bindings and depleted balances");
            var snapshot=SkillCatalog.configurationSnapshot();SkillCatalog.resetConfiguration();
            check(SkillCatalog.applyConfigurationSnapshot(snapshot)&&!SkillCatalog.enabled("electromaster",array.id()),"NEW151 config gate roundtrips the unchanged bounded network snapshot");
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("electromaster",HighPowerSkills.THUNDER_NET),off));
            unlearned.unlearn(HighPowerSkills.THUNDER_NET);
            check(!DevelopmentActions.canLearn(unlearned,DeveloperType.ADVANCED,array)&&!SkillCatalog.canLearn(unlearned,array)&&SkillCatalog.requirements(array).equals(array.requirements()),"disabled NEW141 cannot erase NEW151's learned prerequisite");
        }finally{SkillCatalog.resetConfiguration();}
        check(HighPowerSkills.eligible(arrayState,array.id())&&PresetSkills.selectable(arrayState,array.id())&&AbilityStorage.encodeSaved(arrayState).equals(boundTag),"reenabling NEW151 restores eligibility without rewriting saved balances or mappings");
        for(String invalidation:List.of("level","parent","category","config")){
            var interrupted=state(5);interrupted.learn(HighPowerSkills.THUNDER_NET);
            var interruptedBattery=new Battery(9600,DeveloperType.ADVANCED);process.start(interruptedBattery,DevelopmentActions.skill(interrupted,array));
            for(int tick=0;tick<239;tick++)process.tick();
            try{
                switch(invalidation){
                    case "level"->interrupted.setLevel(4);
                    case "parent"->interrupted.unlearn(HighPowerSkills.THUNDER_NET);
                    case "category"->interrupted.category="teleporter";
                    case "config"->SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("electromaster",array.id()),off));
                }
                process.tick();check(!interrupted.learned(array.id())&&process.state()==DevelopmentProcess.State.FAILED&&interruptedBattery.energy==0,"NEW151 completion rechecks "+invalidation+" after paid work without granting or refunding on failure");
            }finally{SkillCatalog.resetConfiguration();}
        }
        String progressionSource=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cn/academy/port/highpower/HighPowerProgression.java"));
        int learningStart=progressionSource.indexOf("public static DevelopmentProcess.Action learning(");
        String learningSource=progressionSource.substring(learningStart);
        String enrollmentOnly="if (fresh) {\n                    if (!HighPowerCombat.enroll(player, state.level)) throw new IllegalStateException(\"Validated high-power enrollment failed\");\n                    HighPowerResources.initializeBudget(player);\n                }";
        check(learningSource.contains(enrollmentOnly),"NEW151 shared learning wrapper initializes exact HP/shield and prototype CP only for a genuinely unenrolled player");
        String existingLedgerCompletion=learningSource.substring(learningSource.indexOf("public void complete()" )).replace(enrollmentOnly,"");
        for(String forbidden:List.of("enroll(","initializeBudget(","setHealth(","regenerateShield(","recoverAll(","putDouble(","spendShield("))
            check(!existingLedgerCompletion.contains(forbidden),"NEW151 existing-ledger completion source has no direct HP, shield or prototype CP refill path "+forbidden);
        check(existingLedgerCompletion.contains("original.complete();")&&existingLedgerCompletion.contains("boolean fresh = !HighPowerCombat.enabled(player);")&&existingLedgerCompletion.contains("if (fresh) HighPowerCombat.synchronizeEarnedProgress(player);"),"shared learning retains native effects but certifies existing progress only on genuinely fresh enrollment");
        String configSource=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/cn/academy/port/AcademyConfig.java"));
        check(configSource.contains("return !(key.location().equals(\"electromaster\")&&key.skill().equals(\"magnetic_ground_step\"));"),"NEW151 addition preserves NEW132's production default-off setting");
    }

    private static void railThunderArrayLayout(List<ClassicDeveloperLayout.Node> nodes){
        var oldIds=List.of("forked_arc","magnetic_bracer","charge_decoy","polarity_tether","conductive_circuit","grounding_return","magnetic_ground_step","magnetic_item_pull","iron_sand_lance","thunder_net","potential_buffer");
        var oldCoordinates=List.of(List.of(8d,8d),List.of(8d,78d),List.of(73d,110d),List.of(127d,76d),List.of(170d,12d),List.of(160d,112d),List.of(230d,56.5),List.of(46d,77d),List.of(107d,44d),List.of(77d,38d),List.of(230d,94d));
        for(int index=0;index<oldIds.size();index++){
            var old=nodes.get(index);var expected=oldCoordinates.get(index);
            check(old.id().equals(oldIds.get(index))&&old.x()==expected.get(0)&&old.y()==expected.get(1),"NEW151 preserves exact prior node order and coordinates "+oldIds.get(index));
        }
        var node=nodes.get(11);
        check(node.id().equals(HighPowerSkills.RAIL_THUNDER_ARRAY)&&node.index()==151&&node.level()==5&&node.x()==124&&node.y()==11&&node.parent().equals(HighPowerSkills.THUNDER_NET),"NEW151 appends its authoritative identity, LV5 and learned NEW141 parent at the additive position");
        check(node.hintIcon().equals("academy:textures/abilities/electromaster/skills/rail_thunder_array.png")&&node.nameKey().equals("ac.ability.electromaster.rail_thunder_array.name")&&node.descriptionKey().equals("ac.ability.electromaster.rail_thunder_array.desc"),"NEW151 uses canonical saved-ID icon and localization resources");
        var tier=node.conditions().stream().filter(condition->condition.get("type").getAsString().equals("developer_type")).findFirst().orElseThrow();
        check(tier.get("minimum").getAsString().equals("advanced")&&tier.get("hintKey").getAsString().equals("ac.skill_tree.type_advanced"),"NEW151 tree truthfully advertises Advanced-only learning");
        var parent=node.conditions().stream().filter(condition->condition.get("type").getAsString().equals("skill_dependency")).findFirst().orElseThrow();
        check(parent.get("id").getAsString().equals(HighPowerSkills.THUNDER_NET)&&parent.get("minimum_exp").getAsDouble()==0,"NEW151 visible prerequisite is learned NEW141 with zero mastery threshold");
        for(var other:ClassicDeveloperLayout.category("electromaster"))if(!other.id().equals(node.id())){
            double dx=Math.abs(node.x()-other.x()),dy=Math.abs(node.y()-other.y());
            check(dx>=16||dy>=16,"NEW151 16px selection box stays disjoint from "+other.id());
            check(dx>=16.8||dy>=16.8,"NEW151 full-hover 16.8px icon stays disjoint from "+other.id());
        }
        check(node.x()+8-18.6>=0&&node.x()+8+18.6<=257&&node.y()+8-18.6>=0&&node.y()+8+18.6<=139,"NEW151 full hover outline remains inside the actual 257x139 developer canvas");
    }

    private static void magneticDomainProgression(AbilityProgress preDomainSave,String estimateSource,String startCoverSource,String developerScreenSource){
        var domain=SkillCatalog.find("electromaster",HighPowerSkills.MAGNETIC_DOMAIN).orElseThrow();
        check(domain.id().equals("magnetic_domain")&&HighPowerSkills.MAGNETIC_DOMAIN_PROPOSAL==152&&domain.level()==5&&domain.controllable(),"NEW152 has the canonical saved ID, authoritative proposal and exact integer LV5");
        check(domain.requirements().equals(List.of(new SkillCatalog.Requirement(HighPowerSkills.POTENTIAL_BUFFER,0)))&&domain.anyLearnedSkillLevel()==0,"NEW152 requires only learned NEW142 with no invented mastery or alternate prerequisite");
        var previous=AbilityStorage.decodeSaved(AbilityStorage.encodeSaved(preDomainSave),preDomainSave.cpDataConfig());
        previous.setLevel(5);previous.learn(HighPowerSkills.POTENTIAL_BUFFER);previous.learn(HighPowerSkills.RAIL_THUNDER_ARRAY);
        previous.learn("arc_gen");previous.setSkillExp("arc_gen",.375f);
        check(PresetSkills.edit(previous,3,2,HighPowerSkills.POTENTIAL_BUFFER)&&PresetSkills.edit(previous,3,1,HighPowerSkills.RAIL_THUNDER_ARRAY),"pre-NEW152 save contains the learned NEW142 and NEW151 mappings beside NEW141");
        previous.cp=123.5;previous.overload=17.25;previous.extraCp=2.5;previous.extraOverload=1.25;
        previous.cpDelay=11;previous.overloadDelay=13;previous.levelExperience=.125;
        var oldTag=AbilityStorage.encodeSaved(previous);
        var domainState=AbilityStorage.decodeSaved(oldTag,previous.cpDataConfig());
        check(!domainState.learned(domain.id())&&AbilityStorage.encodeSaved(domainState).equals(oldTag),"pre-NEW152 save gains no learning, remap, CP refill, overload reset or training change on load");
        for(int level=1;level<=4;level++){
            domainState.setLevel(level);
            double threshold=cn.academy.port.core.ClassicRules.levelThreshold(level,SkillCatalog.levelSkillCount(domainState));
            for(double progress:List.of(threshold*.999,threshold)){
                domainState.levelExperience=progress;
                check(domainState.level==level&&!DevelopmentActions.canLearn(domainState,DeveloperType.ADVANCED,domain)&&!SkillCatalog.canLearn(domainState,domain),"NEW152 near or complete XP cannot replace actual integer LV5 at LV"+level);
            }
        }
        domainState.setLevel(5);
        check(!DevelopmentActions.canLearn(domainState,DeveloperType.PORTABLE,domain)&&!DevelopmentActions.canLearn(domainState,DeveloperType.NORMAL,domain),"NEW152 requires the Advanced developer even with genuine LV5 and learned NEW142");
        domainState.unlearn(HighPowerSkills.POTENTIAL_BUFFER);
        check(!DevelopmentActions.canLearn(domainState,DeveloperType.ADVANCED,domain)&&!SkillCatalog.canLearn(domainState,domain),"NEW152 learned NEW141 and NEW151 cannot substitute for its learned NEW142 prerequisite");
        domainState.learn(HighPowerSkills.POTENTIAL_BUFFER);
        check(domainState.exp(HighPowerSkills.POTENTIAL_BUFFER)==0&&DevelopmentActions.canLearn(domainState,DeveloperType.ADVANCED,domain)&&SkillCatalog.canLearn(domainState,domain),"NEW152 real LV5 and zero-mastery learned potential buffer permit Advanced learning");
        long unlearnedRevision=domainState.presets.revision();
        check(!HighPowerSkills.eligible(domainState,domain.id())&&!PresetSkills.selectable(domainState,domain.id())&&!PresetSkills.edit(domainState,3,0,domain.id())&&domainState.presets.revision()==unlearnedRevision,"NEW152 unlearned state cannot cast, bind or revise any existing preset");
        var action=DevelopmentActions.skill(domainState,domain);var process=new DevelopmentProcess();
        check(action.id().equals("skill:magnetic_domain")&&action.stimulations()==15&&cn.academy.port.core.ClassicRules.learningStimulations(5)==15,"NEW152 retains the original fifteen LV5 stimulations");
        check(DeveloperType.ADVANCED.ticksPerStimulation()==16&&DeveloperType.ADVANCED.energyPerTick()==40&&DeveloperType.ADVANCED.actualConsumption(action.stimulations())==9600&&DeveloperType.ADVANCED.estimatedConsumption(action.stimulations())==9000,"NEW152 uses 240 paid Advanced ticks and 9600 IF while classic LV5 retains its 9000 IF estimate");
        check(!action.validate(new Battery(20000,DeveloperType.PORTABLE))&&!action.validate(new Battery(20000,DeveloperType.NORMAL))&&action.validate(new Battery(9600,DeveloperType.ADVANCED)),"NEW152 final learning validator enforces the Advanced developer tier");
        check(HighPowerSkills.contains(domain.id())&&estimateSource.contains("if(current.kind.equals(\"skill\")&&cn.academy.port.highpower.HighPowerSkills.contains(current.node.id()))return developerType.actualConsumption(stimulations);")&&developerScreenSource.contains("local(\"learn_question\",Long.toString(Math.round(estimated(current))))")&&startCoverSource.contains("if(energy()<estimated(current)){current.message=local(\"noenergy\");return;}"),"NEW152 registration selects the truthful 9600 IF preview and matching upfront energy check");
        domainState.cp=123.5;domainState.overload=17.25;domainState.extraCp=2.5;domainState.extraOverload=1.25;
        domainState.cpDelay=11;domainState.overloadDelay=13;domainState.levelExperience=.125;
        var beforeLearning=AbilityStorage.encodeSaved(domainState);
        var shortBattery=new Battery(9599,DeveloperType.ADVANCED);process.start(shortBattery,action);
        for(int tick=0;tick<239;tick++)process.tick();
        check(!domainState.learned(domain.id())&&process.state()==DevelopmentProcess.State.DEVELOPING&&shortBattery.energy==39&&AbilityStorage.encodeSaved(domainState).equals(beforeLearning),"NEW152 one-IF-short battery reaches the last tick without early learning or state mutation");
        process.tick();
        check(!domainState.learned(domain.id())&&process.state()==DevelopmentProcess.State.FAILED&&shortBattery.energy==0&&AbilityStorage.encodeSaved(domainState).equals(beforeLearning),"NEW152 final IF shortage drains its finite remainder without learning, player-state mutation or refund");
        var paid=new Battery(9600,DeveloperType.ADVANCED);process.start(paid,action);
        for(int tick=0;tick<239;tick++)process.tick();
        check(!domainState.learned(domain.id())&&process.state()==DevelopmentProcess.State.DEVELOPING&&paid.energy==40&&AbilityStorage.encodeSaved(domainState).equals(beforeLearning),"NEW152 requires every one of the 240 paid native developer ticks");
        process.tick();
        check(domainState.learned(domain.id())&&domainState.exp(domain.id())==0&&process.state()==DevelopmentProcess.State.DONE&&paid.energy==0,"NEW152 finishes exactly fifteen Advanced stimulations for 9600 IF");
        var afterLearning=AbilityStorage.encodeSaved(domainState);afterLearning.getCompound("skills").remove(domain.id());
        var expectedLearning=beforeLearning.copy();expectedLearning.putDouble("cp",domainState.maxCp());expectedLearning.putDouble("overload",0);
        check(afterLearning.equals(expectedLearning),"NEW152 preserves the original CPData learning event only and does not alter existing mastery, XP, training, delays or preset state");
        check(HighPowerSkills.eligible(domainState,domain.id())&&PresetSkills.selectable(domainState,domain.id()),"NEW152 is eligible only after successful real LV5 learning");
        domainState.cp=123.5;domainState.overload=17.25;
        var beforeDuplicate=AbilityStorage.encodeSaved(domainState);
        var duplicateBattery=new Battery(9600,DeveloperType.ADVANCED);process.start(duplicateBattery,DevelopmentActions.skill(domainState,domain));
        for(int tick=0;tick<240;tick++)process.tick();
        check(process.state()==DevelopmentProcess.State.DONE&&duplicateBattery.energy==0&&AbilityStorage.encodeSaved(domainState).equals(beforeDuplicate),"NEW152 duplicate completion remains paid and idempotent with no second CP refill, overload reset, mastery grant or preset mutation");
        var beforeBinding=AbilityStorage.encodeSaved(domainState);beforeBinding.remove("presets");
        long oldRevision=domainState.presets.revision();
        check(PresetSkills.edit(domainState,3,0,domain.id())&&domainState.presets.revision()==oldRevision+1,"NEW152 fills the existing fourth-page left mouse slot in one ordinary edit");
        check(domainState.presets.current()==0&&domainState.presets.skill(0,0).equals(HighPowerSkills.FORKED_ARC)&&domainState.presets.skill(1,1).equals(HighPowerSkills.CONDUCTIVE_CIRCUIT)&&domainState.presets.skill(3,1).equals(HighPowerSkills.RAIL_THUNDER_ARRAY)&&domainState.presets.skill(3,2).equals(HighPowerSkills.POTENTIAL_BUFFER)&&domainState.presets.skill(3,3).equals(HighPowerSkills.THUNDER_NET),"NEW152 binding preserves the active page and every prior NEW141, NEW142 and NEW151 mapping");
        var afterBinding=AbilityStorage.encodeSaved(domainState);afterBinding.remove("presets");
        check(afterBinding.equals(beforeBinding),"NEW152 preset binding cannot refill CP, clear overload, change delays, grant training or award mastery or level XP");
        var boundTag=AbilityStorage.encodeSaved(domainState);var restored=AbilityStorage.decodeSaved(boundTag,domainState.cpDataConfig());
        check(boundTag.getInt("schema")==3&&restored.learned(domain.id())&&restored.exp(domain.id())==0&&restored.presets.skill(3,0).equals("magnetic_domain")&&AbilityStorage.encodeSaved(restored).equals(boundTag),"NEW152 learned bit, depleted balances, canonical slot and all older mappings roundtrip the unchanged save schema");
        for(int level=1;level<=5;level++){
            restored.setLevel(level);
            check(SkillCatalog.levelSkillCount(restored)==SkillCatalog.ALL.stream().filter(skill->skill.category().equals(restored.category)&&skill.level()==restored.level&&skill.controllable()&&SkillCatalog.enabled(skill.category(),skill.id())).count(),"NEW152 leaves the canonical skill-only XP denominator unchanged at LV"+level);
            if(level<5)check(restored.learned(domain.id())&&restored.presets.skill(3,0).equals(domain.id())&&!HighPowerSkills.eligible(restored,domain.id())&&!PresetSkills.mappedUsable(restored,domain.id())&&!PresetSkills.selectable(restored,domain.id()),"NEW152 learned and bound state cannot bypass an integer downgrade to LV"+level);
        }
        check(!domainState.canLevelUp(SkillCatalog.levelSkillCount(domainState)),"NEW152 does not introduce any level beyond classic LV5");
        for(String category:List.of("meltdowner","teleporter","vecmanip")){
            var wrongCategory=AbilityStorage.decodeSaved(boundTag,domainState.cpDataConfig());wrongCategory.category=category;
            check(!DevelopmentActions.canLearn(wrongCategory,DeveloperType.ADVANCED,domain)&&!SkillCatalog.canLearn(wrongCategory,domain)&&!HighPowerSkills.eligible(wrongCategory,domain.id())&&!PresetSkills.selectable(wrongCategory,domain.id())&&!PresetSkills.edit(wrongCategory,2,0,domain.id()),"NEW152 stale learning and binding cannot cross the category boundary "+category);
        }
        var off=new ClassicSkillConfiguration.Flags(false,false);
        try{
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("electromaster",domain.id()),off));
            var unlearned=state(5);unlearned.learn(HighPowerSkills.POTENTIAL_BUFFER);
            check(!DevelopmentActions.canLearn(unlearned,DeveloperType.ADVANCED,domain)&&!SkillCatalog.canLearn(unlearned,domain),"disabled NEW152 fails both actual learning validators");
            check(!HighPowerSkills.eligible(domainState,domain.id())&&!PresetSkills.selectable(domainState,domain.id())&&!PresetSkills.edit(domainState,2,0,domain.id()),"disabled NEW152 refuses casts and new preset assignments");
            check(domainState.learned(domain.id())&&domainState.presets.skill(3,0).equals(domain.id())&&AbilityStorage.encodeSaved(domainState).equals(boundTag),"disabled NEW152 retains saved learned state, canonical bindings and depleted balances");
            var snapshot=SkillCatalog.configurationSnapshot();SkillCatalog.resetConfiguration();
            check(SkillCatalog.applyConfigurationSnapshot(snapshot)&&!SkillCatalog.enabled("electromaster",domain.id()),"NEW152 disabled flag roundtrips the existing bounded configuration snapshot");
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("electromaster",HighPowerSkills.POTENTIAL_BUFFER),off));
            unlearned.unlearn(HighPowerSkills.POTENTIAL_BUFFER);
            check(!DevelopmentActions.canLearn(unlearned,DeveloperType.ADVANCED,domain)&&!SkillCatalog.canLearn(unlearned,domain)&&SkillCatalog.requirements(domain).equals(domain.requirements()),"disabling NEW142 never erases NEW152's actual learned parent requirement");
        }finally{SkillCatalog.resetConfiguration();}
        check(HighPowerSkills.eligible(domainState,domain.id())&&PresetSkills.selectable(domainState,domain.id())&&AbilityStorage.encodeSaved(domainState).equals(boundTag),"reenabling NEW152 restores eligibility without a refill, ledger rewrite or mapping change");
        for(String invalidation:List.of("level","parent","category","config")){
            var interrupted=state(5);interrupted.learn(HighPowerSkills.POTENTIAL_BUFFER);
            var interruptedBattery=new Battery(9600,DeveloperType.ADVANCED);process.start(interruptedBattery,DevelopmentActions.skill(interrupted,domain));
            for(int tick=0;tick<239;tick++)process.tick();
            try{
                switch(invalidation){
                    case "level"->interrupted.setLevel(4);
                    case "parent"->interrupted.unlearn(HighPowerSkills.POTENTIAL_BUFFER);
                    case "category"->interrupted.category="teleporter";
                    case "config"->SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("electromaster",domain.id()),off));
                }
                var beforeFinalTick=AbilityStorage.encodeSaved(interrupted);
                process.tick();
                check(!interrupted.learned(domain.id())&&process.state()==DevelopmentProcess.State.FAILED&&interruptedBattery.energy==0&&AbilityStorage.encodeSaved(interrupted).equals(beforeFinalTick),"NEW152 completion revalidates "+invalidation+" after paid work without learning, refilling or refunding");
            }finally{SkillCatalog.resetConfiguration();}
        }
    }

    private static void magneticDomainLayout(List<ClassicDeveloperLayout.Node> nodes){
        var node=nodes.get(12);
        check(node.id().equals(HighPowerSkills.MAGNETIC_DOMAIN)&&node.index()==152&&node.level()==5&&node.x()==94&&node.y()==93&&node.parent().equals(HighPowerSkills.POTENTIAL_BUFFER),"NEW152 appends its authoritative identity, actual LV5 and NEW142 parent after the unchanged twelve existing nodes");
        check(node.hintIcon().equals("academy:textures/abilities/electromaster/skills/magnetic_domain.png")&&node.nameKey().equals("ac.ability.electromaster.magnetic_domain.name")&&node.descriptionKey().equals("ac.ability.electromaster.magnetic_domain.desc"),"NEW152 tree uses canonical saved-ID icon and localization resources");
        var tier=node.conditions().stream().filter(condition->condition.get("type").getAsString().equals("developer_type")).findFirst().orElseThrow();
        check(tier.get("minimum").getAsString().equals("advanced")&&tier.get("hintKey").getAsString().equals("ac.skill_tree.type_advanced"),"NEW152 tree truthfully displays its Advanced-only learning requirement");
        var parent=node.conditions().stream().filter(condition->condition.get("type").getAsString().equals("skill_dependency")).findFirst().orElseThrow();
        check(parent.get("id").getAsString().equals(HighPowerSkills.POTENTIAL_BUFFER)&&parent.get("minimum_exp").getAsDouble()==0,"NEW152 visible prerequisite is learned potential buffer with no mastery threshold");
        for(var other:ClassicDeveloperLayout.category("electromaster"))if(!other.id().equals(node.id())){
            double dx=Math.abs(node.x()-other.x()),dy=Math.abs(node.y()-other.y());
            check(dx>=16||dy>=16,"NEW152 selection box stays disjoint from "+other.id());
            check(dx>=16.8||dy>=16.8,"NEW152 full-hover icon stays disjoint from "+other.id());
        }
        check(node.x()+8-18.6>=0&&node.x()+8+18.6<=257&&node.y()+8-18.6>=0&&node.y()+8+18.6<=139,"NEW152 full hover outline remains inside the actual 257x139 developer canvas");
    }

    private static void potentialBufferProgression(AbilityProgress preBufferSave,String estimateSource,String startCoverSource,String developerScreenSource){
        var buffer=SkillCatalog.find("electromaster",HighPowerSkills.POTENTIAL_BUFFER).orElseThrow();
        check(buffer.id().equals("potential_buffer")&&HighPowerSkills.POTENTIAL_BUFFER_PROPOSAL==142&&buffer.level()==4&&buffer.controllable(),"NEW142 has the canonical saved ID, proposal and exact integer LV4");
        check(buffer.requirements().equals(List.of(new SkillCatalog.Requirement(HighPowerSkills.GROUNDING_RETURN,0)))&&buffer.anyLearnedSkillLevel()==0,"NEW142 requires only learned grounding return without an invented mastery threshold");
        var oldTag=AbilityStorage.encodeSaved(preBufferSave);
        var bufferState=AbilityStorage.decodeSaved(oldTag,preBufferSave.cpDataConfig());
        check(!bufferState.learned(buffer.id())&&AbilityStorage.encodeSaved(bufferState).equals(oldTag),"pre-NEW142 save preserves every old mapping, learned bit and ledger without granting the addition");
        bufferState.learn(HighPowerSkills.MAGNETIC_BRACER);bufferState.learn(HighPowerSkills.GROUNDING_RETURN);bufferState.learn("arc_gen");
        bufferState.setSkillExp("arc_gen",.375f);bufferState.setLevel(3);
        double threshold=cn.academy.port.core.ClassicRules.levelThreshold(3,SkillCatalog.levelSkillCount(bufferState));
        for(double progress:List.of(threshold*.999,threshold)){
            bufferState.levelExperience=progress;
            check(bufferState.level==3&&!DevelopmentActions.canLearn(bufferState,DeveloperType.ADVANCED,buffer)&&!SkillCatalog.canLearn(bufferState,buffer),"NEW142 near or complete LV3 XP cannot replace actual integer LV4");
        }
        bufferState.setLevel(4);
        check(!DevelopmentActions.canLearn(bufferState,DeveloperType.PORTABLE,buffer)&&!DevelopmentActions.canLearn(bufferState,DeveloperType.NORMAL,buffer),"NEW142 refuses Portable and Normal developers even at real LV4");
        bufferState.unlearn(HighPowerSkills.GROUNDING_RETURN);
        check(!DevelopmentActions.canLearn(bufferState,DeveloperType.ADVANCED,buffer)&&!SkillCatalog.canLearn(bufferState,buffer),"NEW142 cannot substitute learned NEW141 or circuit for the grounding-return predecessor");
        bufferState.learn(HighPowerSkills.GROUNDING_RETURN);
        check(bufferState.exp(HighPowerSkills.GROUNDING_RETURN)==0&&DevelopmentActions.canLearn(bufferState,DeveloperType.ADVANCED,buffer)&&SkillCatalog.canLearn(bufferState,buffer),"NEW142 real LV4 and zero-mastery learned grounding return allow Advanced learning");
        long unlearnedRevision=bufferState.presets.revision();
        check(!HighPowerSkills.eligible(bufferState,buffer.id())&&!PresetSkills.selectable(bufferState,buffer.id())&&!PresetSkills.edit(bufferState,3,2,buffer.id())&&bufferState.presets.revision()==unlearnedRevision,"NEW142 cannot cast or mutate an existing empty slot before paid learning");
        var action=DevelopmentActions.skill(bufferState,buffer);var process=new DevelopmentProcess();
        check(action.id().equals("skill:potential_buffer")&&action.stimulations()==11&&DeveloperType.ADVANCED.ticksPerStimulation()==16&&DeveloperType.ADVANCED.energyPerTick()==40,"NEW142 uses eleven original LV4 stimulations and sixteen paid Advanced ticks each");
        check(!action.validate(new Battery(10000,DeveloperType.PORTABLE))&&!action.validate(new Battery(10000,DeveloperType.NORMAL))&&action.validate(new Battery(7040,DeveloperType.ADVANCED)),"NEW142 actual completion validator enforces the same Advanced-only tier");
        check(DeveloperType.ADVANCED.actualConsumption(action.stimulations())==7040&&DeveloperType.ADVANCED.estimatedConsumption(action.stimulations())==6600,"NEW142 actual learning costs 7040 IF while the legacy classic estimate remains 6600 IF");
        check(HighPowerSkills.contains(buffer.id())&&estimateSource.contains("if(current.kind.equals(\"skill\")&&cn.academy.port.highpower.HighPowerSkills.contains(current.node.id()))return developerType.actualConsumption(stimulations);")&&developerScreenSource.contains("local(\"learn_question\",Long.toString(Math.round(estimated(current))))")&&startCoverSource.contains("if(energy()<estimated(current)){current.message=local(\"noenergy\");return;}"),"NEW142 actual registration selects the truthful 7040 IF preview and matching insufficient-energy check");
        var shortBattery=new Battery(7039,DeveloperType.ADVANCED);process.start(shortBattery,action);
        for(int tick=0;tick<175;tick++)process.tick();
        check(!bufferState.learned(buffer.id())&&process.state()==DevelopmentProcess.State.DEVELOPING&&shortBattery.energy==39,"NEW142 one-IF-short battery reaches the last paid tick without a learned bit");
        process.tick();check(!bufferState.learned(buffer.id())&&process.state()==DevelopmentProcess.State.FAILED&&shortBattery.energy==0,"NEW142 final IF shortage drains the finite remainder without learning or refund");
        bufferState.levelExperience=.125;
        var learnedBefore=Map.copyOf(bufferState.experience);double levelXpBefore=bufferState.levelExperience;
        var paid=new Battery(7040,DeveloperType.ADVANCED);process.start(paid,action);
        for(int tick=0;tick<175;tick++)process.tick();
        check(!bufferState.learned(buffer.id())&&process.state()==DevelopmentProcess.State.DEVELOPING&&paid.energy==40,"NEW142 waits for all 176 paid native developer ticks");
        process.tick();check(bufferState.learned(buffer.id())&&bufferState.exp(buffer.id())==0&&process.state()==DevelopmentProcess.State.DONE&&paid.energy==0,"NEW142 finishes eleven Advanced stimulations for exactly 7040 IF over 176 ticks");
        check(bufferState.levelExperience==levelXpBefore&&bufferState.experience.size()==learnedBefore.size()+1&&learnedBefore.entrySet().stream().allMatch(entry->bufferState.experience.get(entry.getKey()).equals(entry.getValue())),"NEW142 learning grants no mastery or level XP and preserves every existing classic and pack mastery entry");
        check(HighPowerSkills.eligible(bufferState,buffer.id())&&PresetSkills.selectable(bufferState,buffer.id()),"NEW142 genuinely learned LV4 state becomes eligible through ordinary gates");
        bufferState.cp=Math.min(123.5,bufferState.maxCp()/2);bufferState.overload=Math.min(17.25,bufferState.maxOverload()/2);
        bufferState.extraCp=2.5;bufferState.extraOverload=1.25;bufferState.cpDelay=11;bufferState.overloadDelay=13;
        var beforeBinding=AbilityStorage.encodeSaved(bufferState);beforeBinding.remove("presets");
        long oldRevision=bufferState.presets.revision();
        check(PresetSkills.edit(bufferState,3,2,buffer.id())&&bufferState.presets.revision()==oldRevision+1,"NEW142 fills the existing fourth-page R slot with one ordinary preset edit");
        check(bufferState.presets.current()==0&&bufferState.presets.skill(0,0).equals(HighPowerSkills.FORKED_ARC)&&bufferState.presets.skill(1,1).equals(HighPowerSkills.CONDUCTIVE_CIRCUIT)&&bufferState.presets.skill(3,3).equals(HighPowerSkills.THUNDER_NET),"NEW142 binding preserves the active page and every old NEW141-era mapping");
        var afterBinding=AbilityStorage.encodeSaved(bufferState);afterBinding.remove("presets");
        check(afterBinding.equals(beforeBinding),"NEW142 binding never refills CP, clears overload, changes training/delays or awards classic XP");
        var boundTag=AbilityStorage.encodeSaved(bufferState);var restored=AbilityStorage.decodeSaved(boundTag,bufferState.cpDataConfig());
        check(boundTag.getInt("schema")==3&&restored.learned(buffer.id())&&restored.exp(buffer.id())==0&&restored.presets.skill(3,2).equals("potential_buffer")&&AbilityStorage.encodeSaved(restored).equals(boundTag),"NEW142 learned bit and canonical slot roundtrip the unchanged save schema without any refill, mastery or ledger mutation");
        check(SkillCatalog.levelSkillCount(bufferState)==2&&SkillCatalog.levelSkillCount(bufferState)==SkillCatalog.ALL.stream().filter(skill->skill.category().equals(bufferState.category)&&skill.level()==4&&skill.controllable()&&SkillCatalog.enabled(skill.category(),skill.id())).count(),"NEW142 keeps only the two original classic LV4 skills in the advancement denominator");
        check(cn.academy.port.core.ClassicRules.levelThreshold(4,SkillCatalog.levelSkillCount(bufferState))==cn.academy.port.core.ClassicRules.levelThreshold(4,2),"NEW142 preserves the original LV4 advancement threshold");
        restored.setLevel(3);
        check(restored.learned(buffer.id())&&restored.presets.skill(3,2).equals(buffer.id())&&!HighPowerSkills.eligible(restored,buffer.id())&&!PresetSkills.mappedUsable(restored,buffer.id())&&!PresetSkills.selectable(restored,buffer.id()),"NEW142 saved learned bit and binding never bypass an actual integer level downgrade");
        for(String category:List.of("meltdowner","teleporter","vecmanip")){
            var wrongCategory=AbilityStorage.decodeSaved(boundTag,bufferState.cpDataConfig());wrongCategory.category=category;
            check(!DevelopmentActions.canLearn(wrongCategory,DeveloperType.ADVANCED,buffer)&&!SkillCatalog.canLearn(wrongCategory,buffer)&&!HighPowerSkills.eligible(wrongCategory,buffer.id())&&!PresetSkills.selectable(wrongCategory,buffer.id())&&!PresetSkills.edit(wrongCategory,2,0,buffer.id()),"NEW142 stale learned/bound state cannot cross the category gate "+category);
        }
        var off=new ClassicSkillConfiguration.Flags(false,false);
        try{
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("electromaster",buffer.id()),off));
            var unlearned=state(4);unlearned.learn(HighPowerSkills.GROUNDING_RETURN);
            check(!DevelopmentActions.canLearn(unlearned,DeveloperType.ADVANCED,buffer)&&!SkillCatalog.canLearn(unlearned,buffer),"disabled NEW142 is rejected by both real learning validators");
            check(!HighPowerSkills.eligible(bufferState,buffer.id())&&!PresetSkills.selectable(bufferState,buffer.id())&&!PresetSkills.edit(bufferState,2,0,buffer.id()),"disabled NEW142 refuses casts and new preset assignments");
            check(bufferState.learned(buffer.id())&&bufferState.presets.skill(3,2).equals(buffer.id())&&AbilityStorage.encodeSaved(bufferState).equals(boundTag),"disabling NEW142 preserves stored learning, canonical bindings and depleted ledgers");
            var snapshot=SkillCatalog.configurationSnapshot();SkillCatalog.resetConfiguration();
            check(SkillCatalog.applyConfigurationSnapshot(snapshot)&&!SkillCatalog.enabled("electromaster",buffer.id()),"NEW142 config gate survives the unchanged bounded network snapshot");
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key("electromaster",HighPowerSkills.GROUNDING_RETURN),off));
            unlearned.unlearn(HighPowerSkills.GROUNDING_RETURN);
            check(!DevelopmentActions.canLearn(unlearned,DeveloperType.ADVANCED,buffer)&&!SkillCatalog.canLearn(unlearned,buffer)&&SkillCatalog.requirements(buffer).equals(buffer.requirements()),"disabled grounding return cannot erase NEW142's actual learned prerequisite");
        }finally{SkillCatalog.resetConfiguration();}
        check(HighPowerSkills.eligible(bufferState,buffer.id())&&PresetSkills.selectable(bufferState,buffer.id())&&AbilityStorage.encodeSaved(bufferState).equals(boundTag),"reenabling NEW142 restores eligibility without refilling or rewriting saved state");
    }
}
