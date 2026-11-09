package cn.academy.port.client;

import java.util.*;

/** Engine-free receiver gate: server identity, initial-only target set, replay and cancellation fences. */
public final class ThunderNetState {
    public static final int PENDING=0,HOLDING=1,TAP=2,ENDED=3,LOCKED=0,HIT=1,REFUSED=2;
    public static final int MAX_CONTEXTS=64,MAX_WATERMARKS=1024,QUIET_TICKS=8,MAX_AGE=68,HIT_TICKS=4;
    public record Identity(UUID owner,long epoch,long input,long token){}
    public record Node(int entity,UUID uuid,ThunderNetGeometry.Point point,int status){}
    public record Frame(Identity id,long sequence,int phase,int age,ThunderNetGeometry.Point origin,ThunderNetGeometry.Point anchor,List<Node> nodes,int links,int reason){}
    private final Map<UUID,Frame> active=new HashMap<>();
    private final Map<UUID,Long> retired=new HashMap<>();
    private long cancelledEpoch,cancelledThrough;
    public static boolean valid(Frame f){
        if(f==null||f.id==null||f.id.owner==null||f.id.epoch<=0||f.id.input<=0||f.id.token<=0||f.sequence<=0
                ||f.phase<PENDING||f.phase>ENDED||f.age<0||f.age>MAX_AGE||f.reason<0||f.reason>3
                ||f.phase!=ENDED&&f.reason!=0||f.phase==PENDING&&f.age>=8||f.phase==HOLDING&&f.age<8||f.phase==TAP&&f.age>=8
                ||!ThunderNetGeometry.validPoint(f.origin)||!ThunderNetGeometry.validPoint(f.anchor)
                ||f.nodes==null||f.nodes.size()>ThunderNetGeometry.MAX_NODES||f.links<0||(f.links&~ThunderNetGeometry.linkMask(f.nodes.size()))!=0)return false;
        Set<UUID> uuids=new HashSet<>();Set<Integer> entities=new HashSet<>();
        for(Node n:f.nodes){
            if(n==null||n.uuid==null||n.uuid.equals(f.id.owner)||n.entity<0||!uuids.add(n.uuid)||!entities.add(n.entity)
                    ||n.status<LOCKED||n.status>REFUSED||!ThunderNetGeometry.validEndpoints(f.origin,n.point)
                    ||n.point.minus(f.anchor).length()>ThunderNetGeometry.ANCHOR_RADIUS+1e-6
                    ||f.phase==PENDING&&n.status!=LOCKED||(f.phase==TAP||f.phase==ENDED)&&n.status==LOCKED
                    ||f.reason!=0&&n.status==HIT)return false;
        }
        return true;
    }
    public boolean accept(Frame f,boolean local){
        if(!valid(f)||local&&f.id.epoch==cancelledEpoch&&f.id.input<=cancelledThrough)return false;
        Frame old=active.get(f.id.owner);long terminal=retired.getOrDefault(f.id.owner,0L);
        // Never evict a retired owner to admit a replay. Unknown owners fail closed at the hard cap.
        if(!retired.containsKey(f.id.owner)&&retired.size()>=MAX_WATERMARKS||f.id.token<=terminal)return false;
        if(old!=null){
            if(f.id.token<old.id.token)return false;
            if(f.id.token==old.id.token){
                if(!f.id.equals(old.id)||f.sequence<=old.sequence||f.phase<old.phase||f.age<old.age||!f.anchor.equals(old.anchor))return false;
                // Targets can leave; no added/replaced/reacquired UUID or entity ID is accepted later.
                for(Node next:f.nodes)if(old.nodes.stream().noneMatch(previous->previous.uuid.equals(next.uuid)&&previous.entity==next.entity))return false;
            }
        }
        if(f.phase==ENDED){retire(f.id);return true;}
        if(old==null&&active.size()>=MAX_CONTEXTS)return false;
        retired.putIfAbsent(f.id.owner,0L);active.put(f.id.owner,f);return true;
    }
    public void retire(Identity id){
        if(id==null)return;Frame old=active.get(id.owner);if(old!=null&&old.id.token<=id.token)active.remove(id.owner);
        if(retired.containsKey(id.owner)||retired.size()<MAX_WATERMARKS)retired.merge(id.owner,id.token,Math::max);
    }
    public void cancel(long epoch,long input){
        if(epoch<=0||input<=0)return;if(cancelledEpoch!=epoch){cancelledEpoch=epoch;cancelledThrough=0;}cancelledThrough=Math.max(cancelledThrough,input);
    }
    public Frame current(UUID owner){return active.get(owner);}
    public int size(){return active.size();}
    public int watermarkCount(){return retired.size();}
    public void clear(){for(Frame f:List.copyOf(active.values()))retire(f.id);}
    public void resetSession(){active.clear();retired.clear();cancelledEpoch=0;cancelledThrough=0;}
    /** Actual renderer clock. Pauses freeze time but never delay a genuine invalidation. */
    public static final class Lease {
        private int age,quiet,terminalAge=-1;
        public Lease(int serverAge,boolean terminal){age=serverAge;if(terminal)terminalAge=0;}
        public void received(int serverAge,boolean terminal){age=Math.max(age,serverAge);quiet=0;if(terminal&&terminalAge<0)terminalAge=0;}
        public boolean tick(boolean paused,boolean valid){
            if(!valid)return false;if(paused)return true;quiet++;age++;
            if(terminalAge>=0)return ++terminalAge<HIT_TICKS;
            return quiet<QUIET_TICKS&&age<=MAX_AGE;
        }
        public int age(){return age;}public int quiet(){return quiet;}public int terminalAge(){return terminalAge;}
    }
}
