package cn.academy.port.client;

import cn.academy.port.core.TargetedContextTermination;
import java.util.*;

/** Executes the same binding seam used by staged AcademyClient. No game or platform hosts. */
public final class ClassicClientInputBindingTest {
    private static int assertions,scenarios;
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    private static void equal(Object actual,Object expected,String message){check(Objects.equals(actual,expected),message+" actual="+actual+" expected="+expected);}
    private static final Set<String> ALL=Set.of("arc_gen","charging","railgun","electron_bomb","threatening_teleport","dir_shock","ground_shock","dir_blast","storm_wing","blood_retro","vec_accel","vec_deviation","vec_reflection","plasma_cannon","mag_movement","mag_manip","thunder_bolt","mine_detect","body_intensify","thunder_clap","scatter_bomb","light_shield","meltdowner","mine_ray_basic","mine_ray_expert","mine_ray_luck","ray_barrage","jet_engine","electron_missile","penetrate_teleport","mark_teleport","flesh_ripping","location_teleport","shift_tp","flashing");
    private static final Set<String> INSTANT=Set.of("arc_gen","thunder_bolt","mine_detect","electron_bomb","ray_barrage");
    private static final Set<String> TOGGLES=Set.of("vec_deviation","vec_reflection","storm_wing","flashing");
    private static final class Rig implements ClassicClientInputBinding.Host {
        final String[] skills={"","","",""};final int[] keys={-100,-99,19,33};
        final Set<Integer> down=new HashSet<>(),samples=new HashSet<>();final List<Integer> sampleOrder=new ArrayList<>();
        final Set<String> denied=new HashSet<>();final List<String> wire=new ArrayList<>(),events=new ArrayList<>();
        final Map<Long,Boolean> arrowModes=new HashMap<>();boolean crouch;
        ClassicClientInputBinding binding;boolean available=true,inGame=true,activated=true,canUse=true,terminal=false,charge=true,coin=false,legacyInput=false,paused=false;
        final Map<String,Long> lastInputs=new HashMap<>();long nextInput;int[] overrides=new int[0];int sourceTicks;String expectedRemovalSkill;String hintDuringClear;
        Rig(String... mapped){System.arraycopy(mapped,0,skills,0,mapped.length);binding=new ClassicClientInputBinding(this);binding.runtime().updateDefaultGroup();events.clear();}
        public boolean available(){return available;}public boolean inGame(){return inGame;}public boolean endTickAllowed(){return available&&inGame&&!paused;}public boolean categoryActivated(){return activated;}public boolean canUseAbility(){return canUse;}public boolean terminalOpen(){return terminal;}
        public boolean physicalDown(int key){samples.add(key);sampleOrder.add(key);return down.contains(key);}
        public boolean registeredMapping(String skill){return ALL.contains(skill)||Set.of("magnetic_item_pull","magnetic_ground_step","polarity_tether","conductive_circuit","grounding_return","magnetic_bracer","charge_decoy").contains(skill);}public boolean eligible(ClassicClientInputBinding.Node node){return !denied.contains(node.skill);}
        public String mappedSkill(int slot){return skills[slot];}public int mappedKey(int slot){return keys[slot];}public int slotCount(){return 4;}
        public void rawActivation(boolean desired){activated=desired;wire.add("activation:"+desired);check(activated==desired,"raw mutation precedes send");}
        public void replaceOverrides(int[] keys){overrides=keys.clone();events.add("overrides:"+Arrays.toString(keys));}
        public long down(ClassicClientInputBinding.Node node){long input=coin&&node.skill.equals("railgun")?-1:legacyInput?0:++nextInput;wire.add("down:"+node.slot+":"+node.skill+":"+input);events.add("down:"+node.skill);lastInputs.put(node.skill,input);if(node.skill.equals("magnetic_item_pull"))arrowModes.put(input,crouch);return input;}
        public void up(ClassicClientInputBinding.Node node,long input){wire.add("up:"+node.slot+":"+node.skill+":"+input);events.add("up:"+node.skill);}
        public void abort(ClassicClientInputBinding.Node node,long input){wire.add("abort:"+node.slot+":"+node.skill+":"+input);events.add("abort:"+node.skill);if(expectedRemovalSkill!=null)hintDuringClear=binding.runtime().activationHint();}
        public boolean cancelPending(ClassicClientInputBinding.Node node,long input){wire.add("pending_abort:"+node.skill+":"+input);events.add("pending-close:"+node.skill);return true;}
        public void localKeyTick(ClassicClientInputBinding.Node node){sourceTicks++;events.add("keytick:"+node.skill);}
        public boolean railgunCharges(ClassicClientInputBinding.Node node){return charge&&node.input()>=0;}
        public void railgunTick(ClassicClientInputBinding.Node node,int remaining){events.add("railgun-tick:"+remaining);}
        public void railgunEnd(ClassicClientInputBinding.Node node,boolean abort){wire.add("railgun_abort:");events.add("railgun-local-reset");}
        public void direction(ClassicClientInputBinding.ContextIdentity context,int direction,ClassicClientInputBinding.Edge edge,boolean silent){events.add("direction:"+context.skill+":"+direction+":"+edge+":"+silent);if(!silent)wire.add("direction:"+context.skill+":"+direction+":"+edge);if(expectedRemovalSkill!=null)hintDuringClear=binding.runtime().activationHint();}
        public void localContextEnd(ClassicClientInputBinding.ContextIdentity context){events.add("local-end:"+context.skill);}
        public void contextAbort(TargetedContextTermination.Request request){wire.add("context_abort:"+request.wire());events.add("wire-context-end:"+request.skill().id());}
        public void modernCleanup(Collection<ClassicClientInputBinding.Node> defaults){events.add("modern-cleanup:"+defaults.size());}
        void tick(){samples.clear();sampleOrder.clear();binding.tickStart();}
        void press(int slot){down.add(keys[slot]);tick();}
        void release(int slot){down.remove(keys[slot]);tick();}
        long input(int slot){return lastInputs.getOrDefault(skills[slot],0L);}
        void accept(int slot,long token,boolean active){check(binding.acceptedStart(skills[slot],input(slot),token,active),"accepted owned context "+skills[slot]);}
    }
    private static void scenario(String name,Runnable run){run.run();scenarios++;System.out.println("PASS "+name);}

    public static void main(String[] args){
        scenario("all 35 source callback classes and no keytick start replay",()->{
            int singles=0;
            for(String skill:ALL){Rig r=new Rig(skill);r.press(0);int initial=r.wire.size();r.tick();r.tick();equal(r.wire.size(),initial,"ticks send no wire "+skill);
                var kind=ClassicClientInputBinding.kind(skill);if(kind==ClassicClientInputBinding.Kind.SINGLE_KEY){singles++;equal(r.sourceTicks,2,"SK local MSG_KEYTICK "+skill);}else equal(r.sourceTicks,0,"custom callback tick empty "+skill);
                r.wire.clear();r.release(0);boolean substantive=kind==ClassicClientInputBinding.Kind.SINGLE_KEY&&!INSTANT.contains(skill);int expected=kind==ClassicClientInputBinding.Kind.RAILGUN||substantive?1:0;equal(r.wire.size(),expected,"source release listener "+skill);
                r.press(0);r.wire.clear();r.binding.runtime().clearKeys("absent");boolean abortSubstantive=substantive&&!skill.equals("blood_retro");expected=kind==ClassicClientInputBinding.Kind.RAILGUN||abortSubstantive?1:0;equal(r.wire.size(),expected,"source abort listener "+skill);
                if(TOGGLES.contains(skill))check(r.binding.context(skill)==null,"prediction never registers V context "+skill);
            }
            equal(singles,29,"29 source SK nodes");
        });
        scenario("pack tether has three distinct press edges while bracer and decoy remain held",()->{
            Rig r=new Rig("polarity_tether","magnetic_bracer","charge_decoy");
            for(int phase=1;phase<=3;phase++){
                r.press(0);equal(r.input(0),(long)phase,"each tether tap receives a new positive input");
                int messages=r.wire.size();for(int tick=0;tick<4;tick++)r.tick();equal(r.wire.size(),messages,"holding tether sends no repeated tap");
                r.release(0);equal(r.wire.size(),messages,"releasing tether does not cancel the persistent anchor");
            }
            r.wire.clear();r.press(1);r.release(1);check(r.wire.stream().anyMatch(value->value.startsWith("up:1:magnetic_bracer:")),"bracer keeps held-key release");
            r.wire.clear();r.press(2);r.release(2);check(r.wire.stream().anyMatch(value->value.startsWith("up:2:charge_decoy:")),"decoy keeps held-preview release");
            r.wire.clear();r.binding.discard();r.press(0);equal(r.wire,List.of(),"discarded owner cannot send a stale tether tap");
        });
        scenario("NEW121 circuit taps and NEW122 automatic duration ignore normal release",()->{
            Rig r=new Rig("conductive_circuit","grounding_return","magnetic_bracer","charge_decoy");
            for(int phase=1;phase<=3;phase++){
                r.press(0);equal(r.input(0),(long)phase,"circuit anchorA/anchorB/energize each require a new press");
                int messages=r.wire.size();r.tick();r.tick();equal(r.wire.size(),messages,"held circuit input cannot retrigger");
                r.release(0);equal(r.wire.size(),messages,"circuit key-up does not cancel placed anchors");
            }
            r.wire.clear();r.press(1);int messages=r.wire.size();r.release(1);equal(r.wire.size(),messages,"grounding tap has automatic duration rather than held-key lifetime");
            r.wire.clear();r.press(2);r.release(2);check(r.wire.stream().anyMatch(value->value.startsWith("up:2:magnetic_bracer:")),"bracer still releases on key-up");
            r.wire.clear();r.press(3);r.release(3);check(r.wire.stream().anyMatch(value->value.startsWith("up:3:charge_decoy:")),"decoy still commits on key-up");
        });
        scenario("NEW132 fixed tap ignores release and retains four-slot input ownership",()->{
            Rig r=new Rig("magnetic_ground_step","polarity_tether","magnetic_bracer","charge_decoy");
            equal(r.binding.defaultNodes().size(),4,"NEW132 uses only the existing four slots");
            r.press(0);equal(r.input(0),1L,"one authenticated input is allocated on ground-step press");
            for(int tick=0;tick<12;tick++)r.tick();equal(r.wire.size(),1,"holding does not restart or resample the press mode");
            r.release(0);equal(r.wire.size(),1,"normal key-up does not end fixed-duration ground movement");
            r.press(0);equal(r.input(0),2L,"a later physical press gets a fresh input");
            r.wire.clear();r.binding.presetEdit();equal(r.wire,List.of(),"source default abort never emits generic slot-abort for persistent ground step");
            r.tick();equal(r.wire,List.of(),"held remapped key cannot invent a new dash");
            r.release(0);r.press(0);equal(r.input(0),3L,"release-repress is required after remapping");
            r.wire.clear();r.binding.discard();r.release(0);r.press(0);equal(r.wire,List.of(),"replaced world owner emits no stale ground-step request");
        });
        scenario("NEW133 crouch-at-start mode remains captured through every assigned-slot release",()->{
            for(int slot=0;slot<4;slot++)for(boolean arrowMode:List.of(false,true)){
                Rig r=new Rig();r.skills[slot]="magnetic_item_pull";r.binding.presetEdit();r.crouch=arrowMode;r.press(slot);
                equal(r.arrowModes.get(r.input(slot)),arrowMode,"press captures item/arrow mode for slot "+slot);
                r.crouch=!arrowMode;for(int i=0;i<4;i++)r.tick();
                equal(r.wire.size(),1,"crouch release or re-press cannot terminate or restart held slot "+slot);
                equal(r.arrowModes.get(r.input(slot)),arrowMode,"crouch changes cannot reinterpret current lease "+slot);
                r.release(slot);equal(r.wire.size(),2,"actual assigned-key release commits exactly once "+slot);
                check(r.wire.getLast().startsWith("up:"+slot+":magnetic_item_pull:"),"normal release is never abort "+slot);
                r.press(slot);equal(r.arrowModes.get(r.input(slot)),!arrowMode,"next press may choose other mode "+slot);
                r.wire.clear();r.terminal=true;r.tick();
                equal(r.wire,List.of("abort:"+slot+":magnetic_item_pull:2"),"unrelated screen cancels either mode "+slot);
            }
        });
        scenario("NEW133 bounded selector uses actual held mouse and keyboard release edges",()->{
            for(int slot=0;slot<4;slot++){
                Rig r=new Rig();r.skills[slot]="magnetic_item_pull";r.binding.presetEdit();r.press(slot);
                equal(r.input(slot),1L,"selection starts on positive physical input "+slot);
                // setScreen releases KeyMapping's logical state, not Host.physicalDown's raw GLFW state.
                for(int tick=0;tick<8;tick++)r.tick();equal(r.wire.size(),1,"synthetic mapping reset cannot release raw held key "+slot);
                r.release(slot);equal(r.wire,List.of("down:"+slot+":magnetic_item_pull:1","up:"+slot+":magnetic_item_pull:1"),"one actual mouse/keyboard release commits the captured input "+slot);
                r.release(slot);equal(r.wire.size(),2,"duplicate up emits no repeated submission "+slot);
                r.press(slot);r.wire.clear();r.binding.presetEdit();equal(r.wire,List.of("abort:"+slot+":magnetic_item_pull:2"),"preset change aborts selector instead of committing "+slot);
                r.wire.clear();r.tick();equal(r.wire,List.of(),"remapped still-held selector cannot restart "+slot);
                r.release(slot);r.press(slot);r.wire.clear();r.terminal=true;r.tick();equal(r.wire,List.of("abort:"+slot+":magnetic_item_pull:3"),"unrelated screen causes abort "+slot);
                r.wire.clear();r.binding.discard();r.release(slot);equal(r.wire,List.of(),"replacement session cannot submit stale selection "+slot);
            }
        });
        scenario("canonical physical-key identities and source polling order",()->{
            equal(ClassicPhysicalKeys.keysym(87),17,"W identity");equal(ClassicPhysicalKeys.keysym(65),30,"A identity");equal(ClassicPhysicalKeys.keysym(83),31,"S identity");equal(ClassicPhysicalKeys.keysym(68),32,"D identity");equal(ClassicPhysicalKeys.keysym(82),19,"R identity");equal(ClassicPhysicalKeys.keysym(70),33,"F identity");equal(ClassicPhysicalKeys.mouse(0),-100,"left source mouse");equal(ClassicPhysicalKeys.mouse(1),-99,"right source mouse");
            for(var e:ClassicPhysicalKeys.supportedKeysyms().entrySet()){equal(ClassicPhysicalKeys.glfw(e.getValue()),e.getKey(),"canonical roundtrip");check(ClassicPhysicalKeys.canonical(e.getValue()),"canonical map");}
            check(!ClassicPhysicalKeys.canonical(ClassicPhysicalKeys.keysym(305)),"modern F16 ordering explicitly extension");
            Rig r=new Rig("storm_wing");r.press(0);r.accept(0,1,false);equal(r.binding.winningNodes().size(),1,"Storm charging has no directions");check(r.binding.acceptedActive("storm_wing",r.input(0),1),"Storm enters active");r.down.addAll(List.of(17,30,31,32));r.wire.clear();r.tick();equal(r.sampleOrder,List.of(-100,17,30,31,32),"one source-ordered sample per winner");equal(r.wire,List.of("direction:storm_wing:1:DOWN","direction:storm_wing:3:DOWN","direction:storm_wing:2:DOWN","direction:storm_wing:4:DOWN"),"source physical ordering determines last direction");equal(r.binding.context("storm_wing").applying(),4,"D wins simultaneous direction down");
        });
        scenario("persisted membership independent of eligibility and winning collisions",()->{
            Rig r=new Rig("charging","arc_gen");r.keys[1]=r.keys[0];r.denied.add("arc_gen");r.binding.presetEdit();check(!r.binding.ownsDefault(0)&&r.binding.ownsDefault(1),"later source slot owns duplicate physical key despite denied cast");r.press(1);equal(r.wire.size(),0,"modern callback eligibility prevents unauthorized cast");check(Arrays.equals(r.overrides,new int[]{-100}),"registered winner still overrides while ineligible");
            r.denied.clear();r.release(1);r.press(1);equal(r.wire.size(),1,"release-repress allows eligible winning mapping");
        });
        scenario("source default rebuild retains history without hardware sampling",()->{
            Rig r=new Rig("charging");r.press(0);r.samples.clear();r.sampleOrder.clear();r.binding.presetEdit();equal(r.sampleOrder,List.of(),"normal group rebuild never samples hardware");r.wire.clear();r.tick();equal(r.wire.size(),0,"held key after rebuild is not new down");r.release(0);r.press(0);equal(r.wire.size(),1,"fresh release/repress starts once");
        });
        scenario("accepted context handler priority and retained unmapped identity",()->{
            Rig r=new Rig("vec_deviation","vec_reflection");r.press(0);long deviation=r.input(0);r.accept(0,10,true);r.release(0);r.press(1);long reflection=r.input(1);r.accept(1,11,true);r.release(1);equal(r.binding.runtime().activationHint(),"endspecial","newest context hint");r.wire.clear();r.binding.activateKeyDown();r.binding.activateKeyDown();equal(r.wire,List.of(),"V marks disposal only");check(r.binding.context("vec_reflection").disposalRequested(),"newest selected");check(!r.binding.context("vec_deviation").disposalRequested(),"older retained");r.binding.finishLocalDisposals();equal(r.wire,List.of("context_abort:vec_reflection:"+reflection+":11"),"one targeted newer context request");check(r.binding.context("vec_deviation")!=null,"older context survives");
            r.skills[0]="arc_gen";r.binding.presetSwitch();r.wire.clear();r.binding.activateKeyDown();r.binding.finishLocalDisposals();equal(r.wire,List.of("context_abort:vec_deviation:"+deviation+":10"),"unmapped original identity terminates");check(r.binding.contextStartCancelled("vec_deviation",deviation,10),"closed identity tombstone");check(!r.binding.acceptedStart("vec_deviation",deviation,10,true),"late reinstallation rejected");check(!r.binding.acceptedEnd("vec_deviation",deviation,10),"later matching end idempotent");
        });
        scenario("context handlers require positive accepted original identities",()->{
            Rig r=new Rig("vec_deviation");r.press(0);long input=r.input(0);check(!r.binding.acceptedStart("vec_deviation",0,1,true),"no input0 context handler");check(!r.binding.acceptedStart("vec_deviation",input,0,true),"no token0 wildcard");check(!r.binding.acceptedStart("vec_deviation",input+1,1,true),"input mismatch rejected");check(!r.binding.acceptedStart("vec_reflection",input,1,true),"wrong skill rejected");r.accept(0,20,true);check(!r.binding.acceptedEnd("vec_deviation",input+1,20),"wrong original input end rejected");check(!r.binding.acceptedEnd("vec_deviation",input,21),"wrong accepted token end rejected");
        });
        scenario("default clear aborts live direction winner without context disposal",()->{
            Rig r=new Rig("flashing");r.press(0);long input=r.input(0);r.accept(0,30,true);r.release(0);r.down.add(30);r.tick();r.wire.clear();r.binding.presetEdit();check(r.binding.context("flashing")!=null,"Flash independent context survives clear");equal(r.binding.context("flashing").applying(),-1,"direction action aborted");check(r.wire.stream().noneMatch(e->e.startsWith("context_abort")),"direction abort never terminates context");check(r.binding.runtime().groupSnapshot().containsKey("TP_Flashing"),"retained direction group");
            r.down.remove(30);r.tick();r.down.add(30);r.tick();equal(r.binding.context("flashing").applying(),1,"direction release/repress resumes");
        });
        scenario("Flash handler-before-clear and Storm clear-before-handler removal",()->{
            Rig flash=new Rig("flashing","charging");flash.press(0);flash.accept(0,40,true);flash.release(0);flash.press(1);flash.expectedRemovalSkill="flashing";flash.binding.activateKeyDown();flash.binding.finishLocalDisposals();check(!Objects.equals(flash.hintDuringClear,"endspecial"),"Flash handler removed before another delegate abort callback");
            Rig storm=new Rig("storm_wing","charging");storm.press(0);storm.accept(0,41,false);storm.release(0);storm.press(1);storm.expectedRemovalSkill="storm_wing";storm.binding.activateKeyDown();storm.binding.finishLocalDisposals();equal(storm.hintDuringClear,"endspecial","Storm unconditional handler remains during clear callbacks");
        });
        scenario("dying Storm messages suppressed while other live direction winner aborts",()->{
            Rig r=new Rig("flashing","storm_wing");r.press(0);r.accept(0,50,true);r.release(0);r.press(1);r.accept(1,51,true);r.release(1);r.down.add(17);r.tick();r.wire.clear();r.binding.activateKeyDown();r.binding.finishLocalDisposals();check(r.wire.stream().noneMatch(e->e.startsWith("direction:storm_wing")),"dying Storm sendToSelf after TERMINATED suppressed");equal(r.wire,List.of("context_abort:storm_wing:"+2+":51"),"one dying Storm request only");
            Rig other=new Rig("flashing","storm_wing");other.press(0);other.accept(0,60,true);other.release(0);other.press(1);other.accept(1,61,false);other.release(1);
            check(other.binding.acceptedActive("storm_wing",2,61),"other live Storm becomes winning direction group");other.down.add(17);other.tick();other.wire.clear();
            other.binding.acceptedEnd("flashing",1,60);other.binding.finishLocalDisposals();
            equal(other.wire,List.of("direction:storm_wing:1:ABORT"),"terminating Flash clear aborts OTHER live Storm direction normally");
            check(other.binding.context("storm_wing")!=null&&!other.binding.context("storm_wing").terminated(),"other Storm context survives direction abort");

        });
        scenario("accepted server end waits for final local tick and supports final ACTIVE",()->{
            Rig r=new Rig("storm_wing");r.press(0);long input=r.input(0);r.accept(0,70,false);r.wire.clear();check(r.binding.acceptedEnd("storm_wing",input,70),"accepted server termination");check(r.binding.context("storm_wing")!=null&&!r.binding.context("storm_wing").terminated(),"end merely marks disposed");check(r.binding.acceptedActive("storm_wing",input,70),"disposed-but-ALIVE can initialize keys in final tick");r.events.add("final-local-tick");r.binding.finishLocalDisposals();check(r.events.indexOf("final-local-tick")<r.events.indexOf("local-end:storm_wing"),"final tick before termination cleanup");equal(r.wire,List.of(),"server end has no outbound context termination");check(r.binding.context("storm_wing")==null,"context gone after END");
        });
        scenario("source END disposal waits through pause until player-playing resumes",()->{
            Rig r=new Rig("vec_reflection");r.press(0);r.accept(0,75,true);r.binding.activateKeyDown();r.wire.clear();r.paused=true;r.binding.finishLocalDisposals();equal(r.wire,List.of(),"paused Post emits no context request");check(r.binding.context("vec_reflection")!=null&&!r.binding.context("vec_reflection").terminated(),"paused context stays disposed-but-ALIVE");r.paused=false;r.events.add("unpaused-final-local-tick");r.binding.finishLocalDisposals();equal(r.wire,List.of("context_abort:vec_reflection:1:75"),"unpaused END sends exactly one context request");check(r.binding.context("vec_reflection")==null,"termination occurs after unpaused final tick");
        });
        scenario("snapshot fallback silent held fence preserves accepted handler identity",()->{
            Rig r=new Rig("vec_deviation","charging");r.press(0);r.accept(0,80,true);r.press(1);var context=r.binding.context("vec_deviation");r.wire.clear();r.binding.modernSnapshotFallback();equal(r.wire,List.of(),"fallback suppresses all old default/direction wires");check(r.binding.context("vec_deviation")==context,"fallback retains accepted context identity");equal(r.binding.runtime().activationHint(),"endspecial","fallback retains handler order");check(r.binding.fenceSnapshot().contains(-100)&&r.binding.fenceSnapshot().contains(-99),"physical holds explicitly fenced");r.tick();equal(r.wire,List.of(),"held fallback tick cannot cast");r.release(1);r.press(1);equal(r.wire.size(),1,"new press after release casts");
            Rig pending=new Rig("vec_reflection");pending.press(0);long input=pending.input(0);pending.release(0);pending.press(0);check(pending.binding.contextStartCancelled("vec_reflection",input,81),"explicit pending cancel input tombstoned");check(!pending.binding.acceptedStart("vec_reflection",input,81,true),"late canceled pending start rejected");
        });
        scenario("pending persistent activation acknowledgements preserve original owned start",()->{
            for(String skill:TOGGLES){Rig r=new Rig(skill);r.press(0);long input=r.input(0);r.wire.clear();r.binding.modernSnapshotFallback();equal(r.wire,List.of(),"matching activation acknowledgement has no old request "+skill);check(!r.binding.contextStartCancelled(skill,input,82),"ack preserves pending identity "+skill);check(r.binding.context(skill)==null,"ack invents no V handler "+skill);check(r.binding.acceptedStart(skill,input,82,false),"later genuine accepted start owns handler "+skill);equal(r.binding.runtime().activationHint(),"endspecial","accepted pending context survives cosmetic fallback "+skill);}
        });
        scenario("second pending toggle cancels original positive input without allocating input2",()->{
            for(String skill:TOGGLES){Rig r=new Rig(skill);r.press(0);long input=r.input(0);r.release(0);r.wire.clear();r.press(0);equal(r.nextInput,1L,"second pending toggle allocates no input2 "+skill);equal(r.wire,List.of("pending_abort:"+skill+":"+input),"positive original input cancellation "+skill);check(r.binding.context(skill)==null,"pending cancellation invents no handler "+skill);check(!r.binding.acceptedStart(skill,input,83,true),"late canceled start cannot create handler "+skill);check(!r.binding.acceptedEnd(skill,input,83),"late canceled end idempotent "+skill);}
            Rig remapped=new Rig("vec_deviation","");remapped.press(0);remapped.release(0);remapped.skills[0]="plasma_cannon";remapped.skills[1]="vec_deviation";remapped.binding.presetSwitch();remapped.nextInput=0;remapped.press(0);equal(remapped.input(0),1L,"separate vector family counters may both allocate input1");remapped.wire.clear();remapped.press(1);equal(remapped.wire,List.of("pending_abort:vec_deviation:1"),"remapped pending abort names only original skill, independent of same-nonce current slot");check(remapped.binding.activeDefault(0),"equal-nonce Plasma winner still logically active");check(!remapped.binding.acceptedStart("vec_deviation",1,84,true),"canceled remapped pending cannot reinstall a ghost handler");
        });
        scenario("full identity discard and death fence send no stale request",()->{
            Rig r=new Rig("storm_wing");r.press(0);r.accept(0,90,true);r.binding.activateKeyDown();r.wire.clear();r.inGame=false;r.binding.finishLocalDisposals();equal(r.wire,List.of(),"death between PRE and POST emits no request");r.binding.discard();r.inGame=true;r.binding.finishLocalDisposals();check(!r.binding.acceptedStart("storm_wing",1,90,true),"discarded owner never accepts new callback");equal(r.wire,List.of(),"discard callback-silent");r.binding=new ClassicClientInputBinding(r);r.binding.runtime().updateDefaultGroup();r.binding.establishSessionFence();r.tick();equal(r.wire,List.of(),"new owner held key fenced");r.release(0);r.press(0);equal(r.wire.size(),1,"new owner release/repress casts");
        });
        scenario("Railgun only local countdown plus deliberate scoped timer cancellation",()->{
            Rig r=new Rig("railgun");r.press(0);equal(r.binding.railgunTicks(0),20,"item charge starts20");int wires=r.wire.size();for(int i=0;i<20;i++)r.tick();equal(r.binding.railgunTicks(0),0,"local countdown reaches0");equal(r.wire.size(),wires,"local countdown adds no second perform authority");r.tick();equal(r.binding.railgunTicks(0),-1,"HUD source charge ends after next tick");r.wire.clear();r.skills[0]="arc_gen";r.binding.presetEdit();equal(r.wire,List.of("railgun_abort:"),"owned old-node scoped cancellation after local remap");
            Rig coin=new Rig("railgun");coin.coin=true;coin.press(0);equal(coin.binding.railgunTicks(0),-1,"early/failed coin attempt has no item countdown");coin.tick();check(coin.events.stream().noneMatch(e->e.startsWith("railgun-tick")),"coin attempt never ticks item charge");
        });
        scenario("accepted local Railgun coin resets charge without runtime history mutation",()->{
            Rig r=new Rig("railgun");r.press(0);r.tick();var before=r.binding.runtime().keyStateSnapshot();r.wire.clear();r.binding.railgunCoinAccepted();equal(r.binding.railgunTicks(0),-1,"source informThrowCoin local charge reset");equal(r.binding.runtime().keyStateSnapshot(),before,"coin inform keeps physical/logical runtime history");equal(r.wire,List.of("railgun_abort:"),"one scoped modern server timer cancellation");r.wire.clear();r.coin=true;r.tick();equal(r.wire,List.of(),"held key does not become a fresh QTE press");r.release(0);r.press(0);check(r.wire.stream().anyMatch(e->e.startsWith("down:0:railgun:-1")),"release-repress selects new coin once");
        });
        scenario("SingleKey terminated status suppresses later tick up abort at deferred END",()->{
            Rig r=new Rig("blood_retro");r.press(0);long input=r.input(0);check(r.binding.acceptedSingleStart("blood_retro",input,100),"accepted single context token");check(!r.binding.acceptedSingleEnd("blood_retro",input,101),"wrong token cannot end current single");check(r.binding.acceptedSingleEnd("blood_retro",input,100),"exact single end accepted");r.wire.clear();int ticks=r.sourceTicks;r.tick();equal(r.sourceTicks,ticks+1,"disposed-but-ALIVE still receives final keytick");r.binding.finishLocalDisposals();r.tick();equal(r.sourceTicks,ticks+1,"TERMINATED suppresses keytick");r.release(0);equal(r.wire,List.of(),"terminated single release emits no wire");
            Rig legacy=new Rig("charging");legacy.press(0);var node=legacy.binding.defaultNodes().get(0); // test host generated positive input; exact positive path
            check(legacy.binding.acceptedSingleStart("charging",node.input(),101),"accepted single token bound");legacy.binding.acceptedSingleEnd("charging",node.input(),101);legacy.binding.finishLocalDisposals();legacy.wire.clear();legacy.binding.runtime().clearKeys("def");equal(legacy.wire,List.of(),"terminated single abort emits no wire");
        });
        scenario("positive-input SingleKey end never suppresses a newer epoch",()->{
            Rig r=new Rig("plasma_cannon");r.press(0);check(r.binding.acceptedSingleStart("plasma_cannon",1,105),"first positive input start");r.release(0);r.press(0);check(r.binding.acceptedSingleStart("plasma_cannon",2,106),"second positive input start");
            check(!r.binding.acceptedSingleEnd("plasma_cannon",1,105),"old token/input end cannot terminate newer reference");r.binding.finishLocalDisposals();r.wire.clear();r.release(0);equal(r.wire,List.of("up:0:plasma_cannon:2"),"new epoch release uses immutable own input");
        });
        scenario("legacy input0 ambiguity never attributes delayed press1 to press2",()->{
            for(String skill:List.of("charging","mag_movement","mag_manip","ground_shock")){
                Rig r=new Rig(skill);r.legacyInput=true;r.press(0);r.release(0);r.press(0);r.wire.clear();
                check(r.binding.singleStatusUnknown(skill),"unacknowledged legacy release makes status UNKNOWN "+skill);
                check(!r.binding.acceptedSingleStart(skill,0,110),"delayed start1 never binds node2 "+skill);
                check(!r.binding.acceptedSingleEnd(skill,0,110),"delayed end1 never terminates node2 "+skill);
                r.binding.finishLocalDisposals();int ticks=r.sourceTicks;r.tick();equal(r.sourceTicks,ticks+1,"UNKNOWN preserves legacy keytick "+skill);
                r.release(0);equal(r.wire.size(),1,"UNKNOWN preserves current node terminal "+skill);
                Rig stable=new Rig(skill);stable.legacyInput=true;stable.press(0);check(stable.binding.acceptedSingleStart(skill,0,111),"unambiguous accepted legacy token start "+skill);
                check(stable.binding.acceptedSingleEnd(skill,0,111),"same exact legacy token end "+skill);stable.binding.finishLocalDisposals();stable.wire.clear();stable.release(0);equal(stable.wire,List.of(),"unambiguous terminated legacy release suppressed "+skill);
            }
        });
        scenario("observer duplicates never mutate raw flag and each rebuilds source default",()->{
            Rig r=new Rig("charging");r.press(0);r.wire.clear();r.binding.observerActivate();equal(r.wire.size(),1,"activate observer aborts one live key");r.samples.clear();r.sampleOrder.clear();r.binding.observerActivate();equal(r.sampleOrder,List.of(),"duplicate observer still rebuilds without physical sample");check(r.activated,"observer never changes raw activation");r.binding.observerDeactivate();check(r.activated,"deactivate observer never predicts raw flag");equal(r.binding.winningNodes().size(),0,"deactivate clears all groups");equal(r.binding.runtime().activationHint(),null,"no logical key default flag hint");r.binding.activateKeyDown();check(!r.activated,"built-in raw branch mutates before explicit send");
        });
        System.out.println("PASS ClassicClientInputBindingTest scenarios="+scenarios+" assertions="+assertions);
    }
}
