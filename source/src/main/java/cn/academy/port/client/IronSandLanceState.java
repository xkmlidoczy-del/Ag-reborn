package cn.academy.port.client;

import java.util.*;

/** Exact receiver gate: one controller per owner, monotone phases, immutable mode and bounded tombstones. */
public final class IronSandLanceState {
    public static final int MAX_CONTEXTS=64,MAX_TOMBSTONES=256;
    public record Identity(UUID owner,long epoch,long input,long token) {}
    public record Pose(int index,IronSandLanceGeometry.Point tail,IronSandLanceGeometry.Point head,IronSandLanceGeometry.Point previousHead,int status,int hitEntity) {}
    public record Frame(Identity id,long sequence,int phase,boolean orbit,int chargeTicks,IronSandLanceGeometry.Point origin,List<Pose> lances) {}
    private final Map<UUID,Frame> active=new HashMap<>();
    private final Map<UUID,Long> terminals=new LinkedHashMap<>();
    private long cancelledEpoch,cancelledThrough;
    public static boolean valid(Frame f){
        if(f==null||f.id==null||f.id.owner==null||f.id.epoch<=0||f.id.input<=0||f.id.token<=0||f.sequence<=0||f.phase<0||f.phase>3
                ||f.chargeTicks<0||f.chargeTicks>12||f.origin==null||!f.origin.finite()||f.lances==null||f.lances.size()>3
                ||f.phase==1&&f.orbit||f.phase==2&&!f.orbit)return false;
        Set<Integer> indices=new HashSet<>();
        for(Pose p:f.lances){
            if(p==null||p.index<0||p.index>=3||!indices.add(p.index)||p.status<0||p.status>7||p.hitEntity< -1
                    ||p.tail==null||p.head==null||p.previousHead==null||!p.tail.finite()||!p.head.finite()||!p.previousHead.finite()
                    ||p.head.minus(p.tail).length()>3||p.head.minus(p.tail).length()<1e-7||p.head.minus(f.origin).length()>(f.phase==2?128:52)
                    ||p.previousHead.minus(f.origin).length()>(f.phase==2?130:54)||(f.phase==0||f.phase==2&&p.status<2)&&p.head.minus(f.origin).length()>5||f.phase==0&&p.status!=0
                    ||f.phase==1&&p.status==4||f.phase==2&&(p.status==2||p.status>=5)
                    ||(p.status==0||p.status==1||p.status==3)&&p.hitEntity!=-1)return false;
        }
        return true;
    }
    public boolean accept(Frame frame,boolean local){
        if(!valid(frame)||local&&frame.id.epoch==cancelledEpoch&&frame.id.input<=cancelledThrough)return false;
        Frame old=active.get(frame.id.owner);
        if(frame.id.token<=terminals.getOrDefault(frame.id.owner,0L)||old!=null&&(frame.id.token<old.id.token
                ||frame.id.token==old.id.token&&(!frame.id.equals(old.id)||frame.sequence<=old.sequence||frame.phase<old.phase
                ||frame.orbit!=old.orbit||frame.chargeTicks<old.chargeTicks)))return false;
        if(old!=null&&frame.id.token==old.id.token){
            for(Pose previous:old.lances)if(previous.status>=2){
                for(Pose next:frame.lances)if(next.index==previous.index&&next.status<2)return false;
            }
        }
        if(frame.phase==3){retire(frame.id);return true;}
        if(old==null&&active.size()>=MAX_CONTEXTS)return false;
        active.put(frame.id.owner,frame);return true;
    }
    public void retire(Identity id){if(id==null)return;Frame old=active.get(id.owner);if(old!=null&&old.id.token<=id.token)active.remove(id.owner);terminals.merge(id.owner,id.token,Math::max);trim(terminals);}
    public void cancel(long epoch,long input){if(epoch<=0||input<=0)return;if(cancelledEpoch!=epoch){cancelledEpoch=epoch;cancelledThrough=0;}cancelledThrough=Math.max(cancelledThrough,input);}
    public Frame current(UUID owner){return active.get(owner);}
    public int size(){return active.size();}
    public void clear(){active.clear();terminals.clear();cancelledEpoch=0;cancelledThrough=0;}
    private static void trim(Map<?,?> map){while(map.size()>MAX_TOMBSTONES)map.remove(map.keySet().iterator().next());}
}
