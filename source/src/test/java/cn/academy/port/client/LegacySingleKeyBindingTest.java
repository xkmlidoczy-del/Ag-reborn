package cn.academy.port.client;

import cn.academy.port.core.LegacySingleKeyProtocol;
import cn.academy.port.core.TargetedContextTermination;
import java.util.Collection;
import java.util.List;

/** Executes the actual staged binding/coordinator; all platform inputs are explicit deterministic hosts. */
public final class LegacySingleKeyBindingTest {
    private static int checks,scenarios;
    private static void check(boolean value,String message){++checks;if(!value)throw new AssertionError(message);}
    private static final class Rig implements ClassicClientInputBinding.Host {
        final String skill;final ClassicClientInputBinding binding;boolean down,live=true,paused;long counter,ownerEpoch=1;int starts,ups,aborts,ticks;final java.util.ArrayList<Long> cleanupEpochs=new java.util.ArrayList<>();
        Rig(String skill){this.skill=skill;binding=new ClassicClientInputBinding(this);binding.runtime().updateDefaultGroup();}
        public boolean available(){return live;}public boolean inGame(){return live;}public boolean categoryActivated(){return true;}public boolean canUseAbility(){return true;}public boolean terminalOpen(){return false;}public boolean endTickAllowed(){return live&&!paused;}
        public int actorId(){return 77;}public long ownerEpoch(){return ownerEpoch;}
        public boolean physicalDown(int key){return down;}public boolean registeredMapping(String id){return skill.equals(id);}public boolean eligible(ClassicClientInputBinding.Node node){return true;}
        public String mappedSkill(int slot){return skill;}public int mappedKey(int slot){return 17;}public int slotCount(){return 1;}
        public void rawActivation(boolean desired){}public void replaceOverrides(int[] keys){}
        public long down(ClassicClientInputBinding.Node node){++starts;return ++counter;}public void up(ClassicClientInputBinding.Node node,long input){++ups;}public void abort(ClassicClientInputBinding.Node node,long input){++aborts;}public boolean cancelPending(ClassicClientInputBinding.Node node,long input){return false;}
        public void localKeyTick(ClassicClientInputBinding.Node node){++ticks;}
        public void direction(ClassicClientInputBinding.ContextIdentity context,int direction,ClassicClientInputBinding.Edge edge,boolean silent){throw new AssertionError("single key has no direction authority");}
        public void localContextEnd(ClassicClientInputBinding.ContextIdentity context){throw new AssertionError("single key has no V context handler");}
        public void contextAbort(TargetedContextTermination.Request request){throw new AssertionError("single key never global/context aborts");}
        public void modernCleanup(Collection<ClassicClientInputBinding.Node> defaults){}
        public void rejectedSingle(ClassicClientInputBinding.Node node,long input,long capturedOwnerEpoch){cleanupEpochs.add(capturedOwnerEpoch);}
        ClassicClientInputBinding.Node node(){return binding.winningNodes().getFirst();}
        void press(){down=true;binding.tickStart();}void release(){down=false;binding.tickStart();}void tick(){binding.tickStart();}
    }
    public static void main(String[] args){
        for(var family:LegacySingleKeyProtocol.Skill.values()){
            String skill=family.id();
            Rig r=new Rig(skill);r.press();check(r.starts==1&&r.node().input()==1,"one authority allocates one input/start");
            check(r.binding.hasPositiveSingleOwnership(skill),"pending positive ownership visible");
            check(!r.binding.singleStartAllowed(skill,2,101)&&!r.binding.singleStartAllowed(skill,1,0),"wrong input/token cannot start");
            check(!r.binding.singleTerminalAllowed(skill,2,101)&&!r.binding.singleTerminalAllowed(skill,0,101),"terminal cannot bind another pending input/input0");
            check(r.binding.acceptedSingleEnd(skill,1,101),"terminal without start accepted only exact pending");
            check(r.binding.singleInputRetired(skill,1)&&!r.binding.singleStartAllowed(skill,1,101)&&!r.binding.acceptedSingleStart(skill,1,102),"early terminal retires input for every late token");
            int before=r.ticks;r.tick();check(r.ticks==before+1,"disposed context receives final source tick before END");
            r.paused=true;r.binding.finishLocalDisposals();before=r.ticks;r.tick();check(r.ticks==before+1,"paused END retains pending termination");
            r.paused=false;r.binding.finishLocalDisposals();before=r.ticks;r.tick();check(r.ticks==before&&!r.node().singleReference(),"END terminates only exact pending epoch");
            r.release();check(r.ups==0&&r.aborts==0&&r.starts==1,"terminated reference emits no later wire callbacks");++scenarios;

            r=new Rig(skill);r.press();r.release();check(r.ups==1,"pre-ack release routes captured positive input once");r.press();
            check(r.node().input()==2&&!r.binding.acceptedSingleEnd(skill,1,101)&&!r.binding.acceptedSingleStart(skill,1,101),"old start/end cannot claim press2");
            check(r.binding.acceptedSingleStart(skill,2,102),"press2 exact accepted start");
            check(!r.binding.acceptedSingleEnd(skill,2,101)&&!r.binding.acceptedSingleEnd(skill,1,102),"accepted terminal requires both input/token");
            check(r.binding.acceptedSingleEnd(skill,2,102),"press2 exact terminal");r.tick();r.binding.finishLocalDisposals();before=r.ticks;r.tick();check(r.ticks==before,"press2 ends at final tick boundary");++scenarios;

            r=new Rig(skill);r.press();check(r.binding.acceptedSingleStart(skill,1,101),"old accepted identity before press2");r.release();r.press();
            check(r.binding.singleTokenConflicts(skill,2,101)&&!r.binding.singleTerminalAllowed(skill,2,101)&&!r.binding.acceptedSingleEnd(skill,2,101),"old accepted token cannot poison pending input2");
            check(!r.binding.singleInputRetired(skill,2)&&r.binding.acceptedSingleStart(skill,2,102),"exact newer start survives wrong-input old-token end");++scenarios;

            r=new Rig(skill);r.press();check(r.binding.rejectedSingle(skill,1),"exact rejection retires pending input");
            check(!r.binding.rejectedSingle(skill,1)&&!r.binding.acceptedSingleStart(skill,1,101),"rejection idempotent and late start blocked");
            r.release();r.press();check(!r.binding.rejectedSingle(skill,1)&&r.binding.singleStartAllowed(skill,2,102),"old rejection cannot cancel press2");
            r.binding.finishLocalDisposals();check(r.node().singleReference(),"old rejected epoch cannot terminate newer node epoch");++scenarios;

            r=new Rig(skill);r.press();check(r.binding.acceptedSingleStart(skill,1,101),"accepted active context");check(!r.binding.rejectedSingle(skill,1),"duplicate-start rejection cannot kill accepted identity");
            r.binding.discard();check(!r.binding.acceptedSingleEnd(skill,1,101)&&!r.binding.acceptedSingleStart(skill,1,102)&&!r.binding.rejectedSingle(skill,1),"session discard rejects old callbacks silently");++scenarios;

            r=new Rig(skill);r.press();var mutable=r.node();check(mutable.ownerEpoch()==1&&r.binding.rejectedSingle(skill,1),"retired epoch1 pending record");
            r.binding.retireSingleOwnerEpoch();r.ownerEpoch=2;r.release();r.counter=0;r.press();
            check(r.node()==mutable&&mutable.ownerEpoch()==2&&mutable.input()==1,"same mutable node reuses input in new owner namespace");
            check(r.binding.acceptedSingleStart(skill,1,102),"new scope admits same input without old retirement collision");
            r.binding.finishLocalDisposals();check(r.cleanupEpochs.equals(List.of(1L))&&mutable.singleReference(),"deferred cleanup uses immutable retired epoch and leaves new node alive");
            check(r.binding.singleTerminalAllowed(skill,1,102),"new scope accepted token remains owned");++scenarios;
        }
        System.out.println("PASS LegacySingleKeyBinding scenarios="+scenarios+" checks="+checks+"; actual binding/coordinator, deterministic platform hosts only");
    }
}
