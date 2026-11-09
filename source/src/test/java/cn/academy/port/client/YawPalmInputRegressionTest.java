package cn.academy.port.client;

import cn.academy.port.core.TargetedContextTermination;
import cn.academy.port.highpower.HighPowerInput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Actual classic coordinator drives NEW423 fresh-edge, Post sample and receipt lifetime seams. */
public final class YawPalmInputRegressionTest {
    private static int checks;
    private static void check(boolean value,String message) { checks++; if(!value) throw new AssertionError(message); }
    private static final class Rig implements ClassicClientInputBinding.Host {
        final YawPalmInputState input = new YawPalmInputState();
        ClassicClientInputBinding binding;
        Object world=new Object(),player=new Object(),connection=new Object();
        long session=1,epoch=71,revision=1,counter; int actor=19,preset,slot,cooldown;
        boolean live=true,activated=true,usable=true,learned=true,enabled=true,mapped=true,screen,focused=true,camera=true,source=true,paused,crouch;
        String mappings="original"; final int[] keys={-100,-99,19,33};
        final Set<Integer> physical=new HashSet<>(); final List<String> wire=new ArrayList<>();
        Rig(int slot){this.slot=slot;binding=new ClassicClientInputBinding(this);binding.runtime().updateDefaultGroup();}
        public boolean available(){return world!=null&&player!=null&&connection!=null;}
        public boolean inGame(){return live;} public boolean categoryActivated(){return activated;}
        public boolean canUseAbility(){return usable&&activated;} public boolean terminalOpen(){return screen;}
        public int actorId(){return actor;} public long ownerEpoch(){return epoch;}
        public boolean physicalDown(int key){return physical.contains(key);}
        public boolean registeredMapping(String skill){return "yaw_palm".equals(skill);}
        public boolean eligible(ClassicClientInputBinding.Node node){return input.delegateEligible(gates(node.slot,node.key));}
        public String mappedSkill(int index){return index==slot?"yaw_palm":"";}
        public int mappedKey(int index){return keys[index];} public int slotCount(){return 4;}
        public void rawActivation(boolean desired){activated=desired;input.cancelBeforeClear(this::abort);}
        public void replaceOverrides(int[] keys){}
        public long down(ClassicClientInputBinding.Node node){
            var id=new HighPowerInput.Input(node.slot,counter+1,node.actorId(),node.ownerEpoch());
            return !paused&&input.press(id,gates(node.slot,node.key))?++counter:0;
        }
        public void up(ClassicClientInputBinding.Node node,long id){input.release(new HighPowerInput.Input(node.slot,id,node.actorId(),node.ownerEpoch()));}
        public void abort(ClassicClientInputBinding.Node node,long id){input.cancel(new HighPowerInput.Input(node.slot,id,node.actorId(),node.ownerEpoch()),this::abort);}
        public boolean cancelPending(ClassicClientInputBinding.Node node,long id){return false;}
        public void direction(ClassicClientInputBinding.ContextIdentity c,int d,ClassicClientInputBinding.Edge e,boolean s){}
        public void localContextEnd(ClassicClientInputBinding.ContextIdentity c){}
        public void contextAbort(TargetedContextTermination.Request r){throw new AssertionError("legacy route");}
        public void modernCleanup(Collection<ClassicClientInputBinding.Node> d){}
        YawPalmInputState.Gates gates(int slot,int key){return new YawPalmInputState.Gates(new YawPalmInputState.DispatchContext(world,player,connection,session,actor,epoch,preset,revision,mappings),slot,key,live,activated,usable,learned,enabled,mapped&&slot==this.slot,!screen,focused,camera,cooldown==0,source);}
        void pre(){var id=input.current();if(id!=null){input.releaseWhenUp(physicalDown(input.physicalKey()));input.tick(gates(id.slot(),keys[id.slot()]),paused,this::abort);}binding.tickStart();}
        void post(){binding.finishLocalDisposals();var id=input.current();if(id==null)return;input.flushPost(gates(id.slot(),keys[id.slot()]),crouch,p->wire.add("tap:"+p.wire()),this::abort);input.flushReleasePost(gates(id.slot(),keys[id.slot()]),p->wire.add("release:"+p.wire()),this::abort);}
        void press(){physical.add(keys[slot]);pre();} void release(){physical.remove(keys[slot]);pre();}
        void abort(HighPowerInput.Input id){wire.add("abort:"+id.wire());}
        long taps(){return wire.stream().filter(s->s.startsWith("tap:")).count();}
    }
    public static void main(String[] args){
        for(int slot=0;slot<4;slot++)for(boolean early:new boolean[]{false,true}){
            Rig r=new Rig(slot);r.press();check(r.wire.isEmpty(),"Pre queues only");if(early)r.release();
            r.crouch=true;r.post();check(r.taps()==1,"one instant Post tap with no charge");
            check(r.input.permits(1,71,true)&&!r.input.permits(1,71,false),"crouch sampled at Post, not key Pre");
            check(r.wire.size()==(early?2:1),"early release follows tap and preserves receipt");
            r.cooldown=12;for(int i=0;i<12;i++){r.pre();r.post();}
            check(r.taps()==1&&r.input.permits(1,71,true)&&r.wire.stream().noneMatch(s->s.startsWith("abort:")),"own cooldown does not self-abort receipt");
            if(!early){r.release();r.post();}r.input.ended(1,71);
            check(!r.input.permits(1,71)&&r.input.current()==null,"ended release retires identity");
            r.cooldown=0;r.press();r.post();check(r.taps()==2,"fresh edge works once after cooldown");
            r.binding.modernSnapshotFallback();r.pre();r.post();check(r.taps()==2,"snapshot cannot replay held edge");
        }
        List<Consumer<Rig>> losses=List.of(r->r.screen=true,r->r.focused=false,r->r.camera=false,r->r.live=false,r->r.activated=false,
                r->r.usable=false,r->r.learned=false,r->r.enabled=false,r->r.mapped=false,r->r.preset++,r->r.revision++,r->r.mappings="changed",
                r->r.keys[0]++,r->r.actor++,r->r.epoch++,r->r.session++,r->r.world=new Object(),r->r.player=new Object(),r->r.connection=new Object());
        for(var loss:losses){
            Rig q=new Rig(0);q.press();q.release();loss.accept(q);q.post();check(q.taps()==0&&q.input.current()==null,"between-Pre/Post invalidation cancels queued start");
            Rig p=new Rig(0);p.press();p.post();p.release();p.post();p.cooldown=12;loss.accept(p);p.pre();
            check(p.input.current()==null&&!p.input.permits(1,71),"genuine context loss fences released pending receipt despite cooldown");
            int count=p.wire.size();p.pre();p.post();check(count==p.wire.size(),"cancel is one shot");
        }
        Rig source=new Rig(0);source.press();source.post();source.source=false;source.cooldown=12;source.release();source.post();
        check(source.input.permits(1,71)&&!source.input.eligible(source.gates(0,-100)),"later native motion retains existing receipt but blocks new cast");
        Rig timeout=new Rig(0);timeout.press();timeout.post();timeout.release();timeout.post();timeout.paused=true;
        for(int i=0;i<80;i++)timeout.pre();check(timeout.input.permits(1,71),"pause freezes acknowledgement age");
        timeout.paused=false;for(int i=0;i<YawPalmInputState.ACK_TIMEOUT_TICKS;i++)timeout.pre();
        check(timeout.input.current()==null&&!timeout.input.permits(1,71),"missing ack expires and fences late packet");
        Rig multi=new Rig(0);multi.press();multi.post();multi.release();multi.post();multi.press();multi.post();multi.release();multi.post();
        check(multi.input.pendingCount()==2,"multiple fresh receipts have bounded ownership");multi.screen=true;multi.pre();
        check(multi.wire.stream().filter(s->s.startsWith("abort:")).count()==2,"all outstanding identities aborted before clear");
        Rig reuse=new Rig(0);reuse.press();reuse.post();reuse.input.cancelBeforeClear(reuse::abort);
        check(!reuse.input.press(new HighPowerInput.Input(0,1,19,71),reuse.gates(0,-100)),"cancellation preserves input highwater");
        System.out.println("YawPalmInputRegressionTest: "+checks+" checks passed");
    }
}
