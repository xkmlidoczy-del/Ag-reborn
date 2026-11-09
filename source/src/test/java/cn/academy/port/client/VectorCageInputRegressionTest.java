package cn.academy.port.client;

import cn.academy.port.core.TargetedContextTermination;
import cn.academy.port.highpower.HighPowerInput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** NEW441 production binding, native Post dispatch, persistent mark ownership and explicit invalidation. */
public final class VectorCageInputRegressionTest {
    private static final String ID="vector_cage";
    private static int checks,scenarios;
    private static void check(boolean c,String m){checks++;if(!c)throw new AssertionError(m);}
    private static void eq(Object a,Object b,String m){check(Objects.equals(a,b),m+": "+a+" != "+b);}
    private static void scenario(String name,Runnable r){r.run();scenarios++;System.out.println("PASS "+name);}
    private record Loss(String name,Consumer<Rig> apply){}
    private static final class Rig implements ClassicClientInputBinding.Host {
        final VectorCageInputState input=new VectorCageInputState();
        final Object boundWorld=new Object(),boundPlayer=new Object(),boundConnection=new Object();
        Object world=boundWorld,player=boundPlayer,connection=boundConnection;
        long session=1,epoch=71,revision=1,counter;
        int actor=19,preset,slot,cooldown;
        boolean inGame=true,category=true,activated=true,ability=true,learned=true,enabled=true,mapped=true;
        boolean screen,focused=true,camera=true,paused;
        String mappings="cage-original";
        final int[] keys={-100,-99,19,33};
        final Set<Integer> physical=new HashSet<>();
        final List<String> wire=new ArrayList<>();
        final List<HighPowerInput.Input> aborted=new ArrayList<>();
        final ClassicClientInputBinding binding=new ClassicClientInputBinding(this);
        Rig(){this(0);} Rig(int slot){this.slot=slot;binding.runtime().updateDefaultGroup();}
        public boolean available(){return world==boundWorld&&player==boundPlayer&&connection==boundConnection;}
        public boolean inGame(){return inGame;}
        public boolean categoryActivated(){return category&&activated;}
        public boolean canUseAbility(){return ability;}
        public boolean terminalOpen(){return screen;}
        public int actorId(){return actor;}
        public long ownerEpoch(){return epoch;}
        public boolean physicalDown(int key){return physical.contains(key);}
        public boolean registeredMapping(String skill){return ID.equals(skill);}
        public boolean eligible(ClassicClientInputBinding.Node node){return input.delegateEligible(gates(node.slot,node.key));}
        public String mappedSkill(int s){return s==slot?ID:"";}
        public int mappedKey(int s){return keys[s];}
        public int slotCount(){return 4;}
        public void rawActivation(boolean value){activated=value;input.cancelBeforeClear(this::abortInput);}
        public void replaceOverrides(int[] value){}
        public long down(ClassicClientInputBinding.Node n){
            if(paused)return 0;
            var id=new HighPowerInput.Input(n.slot,counter+1,n.actorId(),n.ownerEpoch());
            if(!input.press(id,gates(n.slot,n.key)))return 0;
            return ++counter;
        }
        public void up(ClassicClientInputBinding.Node n,long value){input.release(new HighPowerInput.Input(n.slot,value,n.actorId(),n.ownerEpoch()));}
        public void abort(ClassicClientInputBinding.Node n,long value){input.cancel(new HighPowerInput.Input(n.slot,value,n.actorId(),n.ownerEpoch()),this::abortInput);}
        public boolean cancelPending(ClassicClientInputBinding.Node n,long value){return false;}
        public void direction(ClassicClientInputBinding.ContextIdentity c,int d,ClassicClientInputBinding.Edge e,boolean silent){}
        public void localContextEnd(ClassicClientInputBinding.ContextIdentity c){}
        public void contextAbort(TargetedContextTermination.Request r){throw new AssertionError("No classic context route");}
        public void modernCleanup(Collection<ClassicClientInputBinding.Node> nodes){}
        VectorCageInputState.Gates gates(int s,int key){
            var c=new VectorCageInputState.DispatchContext(world,player,connection,session,actor,epoch,preset,revision,mappings);
            return new VectorCageInputState.Gates(c,s,key,available()&&inGame,categoryActivated(),ability,
                    category&&learned,enabled,mapped&&s==slot,!screen,focused,camera,cooldown==0);
        }
        void pre(){
            var id=input.current();
            if(id!=null){input.releaseWhenUp(physicalDown(input.physicalKey()));input.tick(gates(id.slot(),keys[id.slot()]),paused,this::abortInput);}
            binding.tickStart();
        }
        void post(){var id=input.current();if(id!=null&&!paused)input.flushPost(gates(id.slot(),keys[id.slot()]),v->wire.add("start:"+v.wire()),v->wire.add("release:"+v.wire()),this::abortInput);}
        void tick(){pre();post();}
        void down(){physical.add(keys[slot]);tick();}
        void up(){physical.remove(keys[slot]);tick();}
        void tap(){down();up();}
        void mark(){tap();input.accepted(counter,epoch,VectorCageState.PREVIEW);}
        void abortInput(HighPowerInput.Input id){aborted.add(id);wire.add("abort:"+id.wire());}
    }
    public static void main(String[] args){
        scenario("two short releases retain original mark identity across all physical slots",()->{
            for(int slot=0;slot<4;slot++){
                Rig r=new Rig(slot);r.mark();eq(r.wire.size(),2,"mark starts and releases once");
                r.cooldown=40;r.tap();eq(r.wire.size(),4,"accepted mark permits confirm during own cooldown");
                check(r.input.permits(1,r.epoch)&&r.input.permits(2,r.epoch),"original and pending confirm both authenticated");
                r.input.accepted(1,r.epoch,VectorCageState.PREVIEW);eq(r.input.pendingCount(),1,"preview heartbeat cannot consume confirm");
                r.input.accepted(1,r.epoch,VectorCageState.STATE);eq(r.input.pendingCount(),0,"active original acknowledges confirmation");
                eq(r.input.current().input(),1L,"controller keeps original mark identity");
                for(int i=0;i<1000;i++)r.tick();eq(r.aborted.size(),0,"cooldown and silence do not self-abort");
                r.tap();eq(r.wire.size(),6,"fresh active short cancel dispatched once");
                r.input.accepted(1,r.epoch,VectorCageState.STATE);eq(r.input.pendingCount(),1,"active heartbeat cannot replay or consume cancel");
                r.input.ended(1,r.epoch);check(r.input.current()==null,"original end clears cancel request too");
                r.up();r.down();eq(r.wire.size(),6,"ended owner has no cooldown bypass");
            }
        });
        scenario("down and up in one Pre/Post turn preserve native release ordering",()->{
            Rig r=new Rig();r.physical.add(r.keys[0]);r.pre();r.physical.clear();r.pre();
            check(r.wire.isEmpty(),"nothing transmits before native Post");r.post();
            eq(r.wire,List.of("start:0:1:19:71","release:0:1:19:71"),"start precedes release exactly once");r.post();eq(r.wire.size(),2,"Post is idempotent");
        });
        scenario("pending mark suppresses repeat edges until authenticated receipt or rejection",()->{
            Rig r=new Rig();r.tap();r.tap();eq(r.wire.size(),2,"no duplicate mark while unacknowledged");
            r.input.rejected(1,r.epoch);check(r.input.current()==null,"matching rejection releases initial pending");
            r.tap();eq(r.counter,2L,"fresh edge recovers after rejection");r.input.accepted(2,r.epoch,0);
            r.input.rejected(2,r.epoch);check(r.input.permits(2,r.epoch),"late rejection cannot erase accepted mark");
            r.tap();r.input.rejected(3,r.epoch);eq(r.input.current().input(),2L,"refused confirm retains original until authoritative end");
            r.input.ended(2,r.epoch);check(r.input.current()==null,"server clears refused preview explicitly");
        });
        scenario("six tick hold is server-owned and free clear never locally confirms",()->{
            Rig r=new Rig();r.mark();r.down();int messages=r.wire.size();
            for(int i=0;i<6;i++)r.tick();eq(r.wire.size(),messages,"held ticks transmit no synthetic release or repeated start");
            r.input.ended(1,r.epoch);r.up();eq(r.wire.size(),messages,"server end suppresses stale held release");
        });
        List<Loss> losses=List.of(new Loss("GUI",r->r.screen=true),new Loss("focus",r->r.focused=false),new Loss("camera",r->r.camera=false),
                new Loss("category",r->r.category=false),new Loss("activation",r->r.activated=false),new Loss("ability",r->r.ability=false),
                new Loss("learned",r->r.learned=false),new Loss("enabled",r->r.enabled=false),new Loss("mapping",r->r.mapped=false),
                new Loss("slot",r->r.slot=1),new Loss("preset",r->r.preset++),new Loss("revision",r->r.revision++),
                new Loss("mapping signature",r->r.mappings="changed"),new Loss("key",r->r.keys[0]=30),new Loss("actor",r->r.actor++),
                new Loss("epoch",r->r.epoch++),new Loss("session",r->r.session++),new Loss("world",r->r.world=new Object()),
                new Loss("body",r->r.player=new Object()),new Loss("connection",r->r.connection=new Object()),new Loss("death",r->r.inGame=false));
        scenario("all context loss aborts exact original and pending identity even paused",()->{
            for(var loss:losses)for(boolean paused:new boolean[]{false,true}){
                Rig r=new Rig();r.mark();r.down();r.paused=paused;loss.apply.accept(r);r.tick();
                check(r.input.current()==null,"ownership removed: "+loss.name);eq(r.aborted.size(),2,"both identities canceled: "+loss.name);
                eq(r.aborted.get(0).input(),1L,"accepted abort first");eq(r.aborted.get(1).input(),2L,"pending abort second");
                check(!r.input.permits(1,71)&&!r.input.permits(2,71),"late receipts fenced");r.tick();eq(r.aborted.size(),2,"no repeated abort");
            }
        });
        scenario("pause queues native Post and does not drop explicit teardown",()->{
            Rig r=new Rig();r.physical.add(r.keys[0]);r.pre();r.paused=true;r.post();check(r.wire.isEmpty(),"paused Post withheld");
            r.paused=false;r.post();eq(r.wire.size(),1,"resume sends one start");
            var id=r.input.current();check(r.input.cancelBeforeClear(v->{check(r.input.current()==id,"identity retained through send");r.abortInput(v);}),"teardown sent");
            check(r.input.current()==null,"clear after dispatch");
        });
        System.out.println("VectorCageInputRegressionTest: "+checks+" checks in "+scenarios+" scenarios passed");
    }
}
