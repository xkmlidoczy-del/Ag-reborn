package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** NEW431 bounded authenticated native receipts. Nothing extrapolates a projectile position. */
public final class TrajectoryReturnState {
    public static final int REVERSE=0, FLIGHT=1, CONTACT=2, AIR_HAMMER=3, ENDED=4;
    public static final int REFUSED=0, DAMAGED=1, ABSORBED=2, COMMITTED=3;
    public static final int MAX_CONTROLLERS=4, MAX_SLOTS=4, MAX_SEGMENTS=4, MAX_WATERMARKS=1024;
    public static final int LIFE_TICKS=32, TRAIL_LIFE=5, GAP_TICKS=2;
    public static final Point ZERO=new Point(0,0,0);
    private static final UUID ZERO_UUID=new UUID(0,0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int kind, int slot, Point origin, Point end, Point velocity, int status) {}
    public record Segment(Point from, Point to, int tick) {}
    public record Slot(int number, boolean hammer, Point origin, Point position, Point velocity,
                       Point reversal, Point reversalVelocity, int reversedAt, int receivedAt,
                       int kind, int status, List<Segment> trail) {
        public Slot { trail=List.copyOf(trail); }
    }
    public record Controller(Identity id, boolean local, List<Slot> slots, int age) {
        public Controller { slots=List.copyOf(slots); }
    }
    private record Fence(Frame frame, boolean closed) {}
    private record Highwater(long epoch,long input,long token) {}
    private final Map<Identity,Fence> fences=new HashMap<>();
    private final Map<UUID,Highwater> highwater=new HashMap<>();
    private final Map<Identity,Controller> active=new LinkedHashMap<>();
    private long cancelledEpoch,cancelledThrough;

    public static boolean valid(Frame f) {
        if(f==null||f.id==null||f.id.entity<0||f.id.owner==null||f.id.owner.equals(ZERO_UUID)
                ||f.id.epoch<=0||f.id.input<=0||f.id.token<=0||f.sequence<=0||f.sequence>128
                ||f.kind<REVERSE||f.kind>ENDED||f.slot<0||f.slot>=MAX_SLOTS
                ||f.status<REFUSED||f.status>COMMITTED||!finite(f.origin)||!finite(f.end)||!finite(f.velocity)
                ||f.velocity.distanceSquared(ZERO)>64.000001) return false;
        if(f.kind==ENDED) return f.slot==0&&f.status==REFUSED&&f.origin.equals(ZERO)&&f.end.equals(ZERO)&&f.velocity.equals(ZERO);
        if(f.kind==REVERSE) return f.status==COMMITTED&&f.origin.equals(f.end)&&f.velocity.distanceSquared(ZERO)>1e-12;
        if(f.kind==AIR_HAMMER) return f.status!=COMMITTED&&f.velocity.equals(ZERO)&&f.origin.distanceSquared(f.end)<=1600.000001;
        if(f.kind==FLIGHT) return f.status==COMMITTED&&f.origin.distanceSquared(f.end)<=64.000001;
        return f.status!=COMMITTED&&f.origin.distanceSquared(f.end)<=64.000001;
    }
    private static boolean finite(Point p){return p!=null&&p.finite();}
    public boolean accept(Frame f,boolean local) {
        if(!valid(f)||local&&(f.id.epoch<cancelledEpoch||f.id.epoch==cancelledEpoch&&f.id.input<=cancelledThrough))return false;
        Highwater ownerHighwater=highwater.get(f.id.owner);
        if(ownerHighwater!=null&&f.id.epoch<ownerHighwater.epoch)return false;
        Fence old=fences.get(f.id); Controller previous=active.get(f.id);
        if(old!=null) {
            if(old.closed||previous==null||f.sequence<=old.frame.sequence)return false;
            if(f.sequence!=old.frame.sequence+1){retire(f.id);return false;}
        } else {
            Highwater h=highwater.get(f.id.owner);
            if(fences.size()>=MAX_WATERMARKS||h==null&&highwater.size()>=MAX_WATERMARKS
                    ||h!=null&&(f.id.epoch<h.epoch||f.id.epoch==h.epoch&&(f.id.input<=h.input||f.id.token<=h.token)))return false;
            if(f.sequence!=1||f.kind!=REVERSE&&f.kind!=AIR_HAMMER&&f.kind!=ENDED)return false;
        }
        if(ownerHighwater!=null&&f.id.epoch>ownerHighwater.epoch)
            for(var c:controllers())if(c.id.owner.equals(f.id.owner)&&c.id.epoch<f.id.epoch)retire(c.id);
        if(f.kind==ENDED) {
            remember(f,true); active.remove(f.id); return true;
        }
        if(previous==null&&active.size()>=MAX_CONTROLLERS) {
            if(local){Controller disposable=active.values().stream().filter(c->!c.local).findFirst().orElse(null);if(disposable!=null)retire(disposable.id);}
            if(active.size()>=MAX_CONTROLLERS){remember(f,true);return false;}
        }
        var slots=new ArrayList<Slot>(previous==null?List.of():previous.slots);
        int age=previous==null?0:previous.age;
        Slot slot=slots.stream().filter(s->s.number==f.slot).findFirst().orElse(null);
        if(f.kind==REVERSE||f.kind==AIR_HAMMER) {
            if(slot!=null||slots.size()>=MAX_SLOTS||f.kind==AIR_HAMMER&&!slots.isEmpty()
                    ||slots.stream().anyMatch(Slot::hammer))return false;
            slots.add(new Slot(f.slot,f.kind==AIR_HAMMER,f.origin,f.end,f.velocity,f.end,f.velocity,age,age,f.kind,f.status,List.of()));
        } else {
            if(slot==null||slot.kind==CONTACT||slot.hammer&&f.kind!=CONTACT)return false;
            if(!slot.hammer&&f.origin.distanceSquared(f.end)>64.000001)return false;
            var trail=new ArrayList<Segment>();
            // Only consecutive real FLIGHT receipts can join. A discontinuity starts a fresh empty trail.
            boolean continuous=slot.position.distanceSquared(f.origin)<=1e-8&&age-slot.receivedAt<=GAP_TICKS;
            if(f.kind==FLIGHT&&continuous){
                for(var segment:slot.trail)if(age-segment.tick<TRAIL_LIFE)trail.add(segment);
                if(f.origin.distanceSquared(f.end)>1e-12)trail.add(new Segment(f.origin,f.end,age));
                while(trail.size()>MAX_SEGMENTS)trail.removeFirst();
            }
            slots.remove(slot);
            slots.add(new Slot(f.slot,slot.hammer,slot.hammer?slot.origin:f.origin,f.end,f.velocity,
                    slot.reversal,slot.reversalVelocity,slot.reversedAt,age,f.kind,f.status,trail));
        }
        remember(f,false);active.put(f.id,new Controller(f.id,local,slots,age));return true;
    }
    private void remember(Frame f,boolean closed){
        fences.put(f.id,new Fence(f,closed));Highwater h=highwater.get(f.id.owner);
        if(h==null||f.id.epoch>h.epoch||f.id.epoch==h.epoch&&f.id.input>h.input)
            highwater.put(f.id.owner,new Highwater(f.id.epoch,f.id.input,f.id.token));
    }
    public List<Controller> tick(boolean paused,Predicate<Controller> validContext){
        var retired=new ArrayList<Controller>();
        for(var cast:controllers()) {
            if(!validContext.test(cast)||!paused&&cast.age+1>=LIFE_TICKS){retire(cast.id);retired.add(cast);}
            else if(!paused) {
                int next=cast.age+1;var slots=new ArrayList<Slot>();
                for(var s:cast.slots) {
                    var trail=s.trail.stream().filter(t->next-t.tick<TRAIL_LIFE&&next-s.receivedAt<=GAP_TICKS).toList();
                    slots.add(new Slot(s.number,s.hammer,s.origin,s.position,s.velocity,s.reversal,s.reversalVelocity,s.reversedAt,s.receivedAt,s.kind,s.status,trail));
                }
                active.put(cast.id,new Controller(cast.id,cast.local,slots,next));
            }
        }
        return List.copyOf(retired);
    }
    public List<Controller> controllers(){return List.copyOf(active.values());}
    public boolean owns(Identity id){return id!=null&&active.containsKey(id);}
    public void cancel(long epoch,long input){
        if(epoch<=0||input<=0||epoch<cancelledEpoch)return;
        if(epoch>cancelledEpoch){cancelledEpoch=epoch;cancelledThrough=0;}
        cancelledThrough=Math.max(cancelledThrough,input);
        for(var c:controllers())if(c.local&&c.id.epoch==epoch&&c.id.input<=input)retire(c.id);
    }
    public void retire(Identity id){
        Fence f=fences.get(id);if(f!=null)fences.put(id,new Fence(f.frame,true));active.remove(id);
    }
    public void clear(){for(var c:controllers())retire(c.id);}
    public void resetSession(){clear();fences.clear();highwater.clear();cancelledEpoch=cancelledThrough=0;}
    public int watermarkCount(){return fences.size();}
}
