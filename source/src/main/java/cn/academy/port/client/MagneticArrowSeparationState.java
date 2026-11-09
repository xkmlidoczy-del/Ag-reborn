package cn.academy.port.client;

import java.util.*;

/** Engine-free replay/cancellation gate shared by the receiver and regression tests. */
public final class MagneticArrowSeparationState {
    public static final int MAX_CONTEXTS=64,MAX_TOMBSTONES=256,MAX_TARGETS=4;
    public record Identity(UUID owner,long epoch,long input,long token) {}
    public record Input(long epoch,long input) {}
    public record Frame(Identity id,long sequence,int phase,List<UUID> targets) {}
    private final Map<UUID,Frame> active=new HashMap<>();
    private final Map<UUID,Long> terminals=new LinkedHashMap<>();
    private final Set<Input> cancelled=new LinkedHashSet<>();
    public static boolean valid(Frame f){
        return f!=null&&f.id!=null&&f.id.owner!=null&&f.id.epoch>0&&f.id.input>0&&f.id.token>0&&f.sequence>0&&f.phase>=0&&f.phase<=2
                &&f.targets!=null&&f.targets.size()<=MAX_TARGETS&&f.targets.stream().noneMatch(Objects::isNull)
                &&new HashSet<>(f.targets).size()==f.targets.size()&&(f.phase!=0||f.targets.isEmpty());
    }
    public boolean accept(Frame f,boolean local){
        if(!valid(f)||local&&cancelled.contains(new Input(f.id.epoch,f.id.input)))return false;
        Frame old=active.get(f.id.owner);
        if(f.id.token<=terminals.getOrDefault(f.id.owner,0L)||old!=null&&(f.id.token<old.id.token
                ||f.id.token==old.id.token&&(!f.id.equals(old.id)||f.sequence<=old.sequence||f.phase<old.phase)))return false;
        if(f.phase==2){retire(f.id);return true;}
        if(old==null&&active.size()>=MAX_CONTEXTS)return false;
        active.put(f.id.owner,f);return true;
    }
    public void retire(Identity id){if(id==null)return;Frame old=active.get(id.owner);if(old!=null&&old.id.token<=id.token)active.remove(id.owner);terminals.merge(id.owner,id.token,Math::max);trim(terminals);}
    public void cancel(long epoch,long input){if(epoch<=0||input<=0)return;cancelled.add(new Input(epoch,input));while(cancelled.size()>MAX_TOMBSTONES)cancelled.remove(cancelled.iterator().next());}
    public Frame current(UUID owner){return active.get(owner);}
    public int size(){return active.size();}
    public void clear(){active.clear();terminals.clear();cancelled.clear();}
    private static void trim(Map<?,?> map){while(map.size()>MAX_TOMBSTONES)map.remove(map.keySet().iterator().next());}
}
