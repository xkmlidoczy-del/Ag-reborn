package cn.academy.port.city;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Persistent owner of exactly one ephemeral cabin. No independent saved vehicle can duplicate after restart. */
public final class CityElevatorController extends BlockEntity {
    public enum Phase { IDLE,OPENING,DWELL,CLOSING,MOVING,FAULT }
    public static final double SPEED=.25,ACCEL=.01;
    private String identity="";private int shaftX,shaftZ;private int[] floors=new int[0];
    private double y,velocity;private int at,target=-1,timer,door=0,lastDock;private Phase phase=Phase.IDLE;
    private final Set<Integer> queue=new LinkedHashSet<>();private CityElevatorEntity cabin;private int direction=1;private long activeTicks;private String failure="";
    public CityElevatorController(BlockPos pos,BlockState state){super(CityInteriorContent.ELEVATOR.get(),pos,state);}
    @Override public void setChanged(){
        // This controller exposes no comparator output. Vanilla's generic setter notifies a
        // two-block neighborhood and can synchronously obtain an unloaded border chunk.
        if(level instanceof ServerLevel w){var chunk=w.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);if(chunk!=null)chunk.setUnsaved(true);}
    }
    public void configure(CityInteriorPlan.Lift l){if(!identity.isEmpty())return;identity=l.id();shaftX=l.x();shaftZ=l.z();floors=l.floors();y=floors[0];at=lastDock=0;target=-1;phase=Phase.OPENING;timer=12;setChanged();}
    public String identity(){return identity;}public int[] floors(){return floors.clone();}public double cabinY(){return y;}public int door(){return door;}public Phase phase(){return phase;}public int current(){return at;}public int shaftX(){return shaftX;}public int shaftZ(){return shaftZ;}public long activeTicks(){return activeTicks;}
    public CityElevatorEntity cabin(){return cabin!=null&&!cabin.isRemoved()?cabin:null;}
    public String failure(){return failure;}
    public boolean configured(){return !identity.isEmpty()&&floors.length>0&&floors.length<=256;}
    public BlockPos panel(int f){return new BlockPos(shaftX+3,floors[f]+1,shaftZ);}
    public Vec3 centre(){return new Vec3(shaftX+2,y,shaftZ+2);}
    public Vec3 landing(int f){return new Vec3(shaftX+2,floors[f],shaftZ-.5);}
    public int panelFloor(BlockPos p){if(worldPosition.equals(p))return 0;for(int i=0;i<floors.length;i++)if(panel(i).equals(p))return i;return -1;}
    public boolean request(int floor){if(!configured()||floor<0||floor>=floors.length)return false;queue.add(floor);if(phase==Phase.FAULT){target=floor;failure="";phase=Phase.CLOSING;timer=12;}setChanged();return true;}
    public boolean boarding(){return configured()&&phase==Phase.DWELL&&door==12&&at>=0&&Math.abs(y-floors[at])<.001;}
    public Optional<Vec3> exit(LivingEntity passenger){if(!boarding()||level==null)return Optional.empty();return CityElevators.validExit(level,landing(at),passenger)?Optional.of(landing(at)):Optional.empty();}
    public boolean board(LivingEntity p){
        if(!(p instanceof Player)||!boarding()||!p.isAlive()||p.isPassenger()||p.level()!=level||p.distanceToSqr(centre())>16||!CityElevators.validExit(level,landing(at),p))return false;
        if(cabin()==null||cabin.getPassengers().size()>=4)return false;
        boolean boarded=p.startRiding(cabin);if(boarded&&p instanceof Player player)CityElevators.remember(player,this,landing(at));return boarded;
    }
    public void emergency(){
        if(!configured())return;
        int best=-1;double distance=Double.MAX_VALUE;
        if(!(level instanceof ServerLevel w))return;
        for(int f=0;f<floors.length;f++)if(CityElevators.validExit(level,landing(f),null)&&routeFailure(w,floors[f]).isEmpty()&&Math.abs(floors[f]-y)<distance){best=f;distance=Math.abs(floors[f]-y);}
        if(best>=0){queue.clear();queue.add(best);target=best;failure="";phase=Phase.CLOSING;timer=12;setChanged();}
    }
    public static void tick(Level level,BlockPos pos,BlockState state,CityElevatorController c){if(level instanceof ServerLevel w)c.tick(w);}
    private void tick(ServerLevel w){
        if(!configured())return;
        boolean nearby=w.players().stream().anyMatch(p->{double dx=p.getX()-(shaftX+2),dz=p.getZ()-(shaftZ+2);return dx*dx+dz*dz<96*96;});
        if(!nearby&&(cabin()==null||cabin.getPassengers().isEmpty())){if(cabin()!=null){cabin.discard();cabin=null;velocity=0;setChanged();}return;}
        if(!shaftLoaded(w)){fault("unloaded");syncCabin();return;}
        if(cabin()==null){
            cabin=CityInteriorContent.CABIN.get().create(w);if(cabin==null)return;
            cabin.controller(worldPosition);cabin.setPos(centre());if(!w.addFreshEntity(cabin)){cabin=null;return;}
        }
        activeTicks++;
        if(!shaftLoaded(w)){fault("unloaded");syncCabin();return;}
        if(phase==Phase.IDLE){phase=Phase.OPENING;timer=12;}
        if(phase==Phase.OPENING){
            if(at<0||!CityElevators.validExit(w,landing(at),null)){fault("exit_blocked");syncCabin();return;}
            dock(w,at,true);door=Math.min(12,door+1);gates(w,at,door);
            if(--timer<=0){door=12;phase=Phase.DWELL;timer=60;queue.remove(at);}
        }else if(phase==Phase.DWELL){
            door=12;dock(w,at,true);gates(w,at,12);
            // Occupants in the actual cabin volume board before its temporary dock support can be removed.
            for(LivingEntity e:w.getEntitiesOfClass(LivingEntity.class,new AABB(shaftX+1,y-.2,shaftZ+1,shaftX+3,y+2.5,shaftZ+3)))
                if(e instanceof Player&&!e.isPassenger()&&board(e)&&e instanceof net.minecraft.server.level.ServerPlayer player)CityElevatorNetwork.open(player,this,at);
            queue.remove(at);if(timer>0)timer--;
            if(timer==0&&!queue.isEmpty()){target=next();phase=Phase.CLOSING;timer=12;}
        }else if(phase==Phase.CLOSING){
            if(at>=0&&(doorwayOccupied(w)||unmountedInside(w))){phase=Phase.OPENING;timer=Math.max(1,12-door);syncCabin();return;}
            door=Math.max(0,door-1);if(at>=0)gates(w,at,door);
            if(--timer<=0){
                if(target<0||target>=floors.length){phase=Phase.OPENING;timer=12;}
                else if(Math.abs(y-floors[target])<.001){at=target;phase=Phase.OPENING;timer=12;}
                else {
                    String reason=!CityElevators.validExit(w,landing(target),null)?"exit_blocked":routeFailure(w,floors[target]);
                    if(!reason.isEmpty()){fault(reason);if(at>=0){door=12;gates(w,at,12);}syncCabin();return;}
                    failure="";if(at>=0)dock(w,at,false);lastDock=at>=0?at:lastDock;at=-1;velocity=0;phase=Phase.MOVING;
                }
            }
        }else if(phase==Phase.MOVING){
            double delta=floors[target]-y;direction=delta>=0?1:-1;
            double speed=Math.min(SPEED,Math.min(Math.abs(velocity)+ACCEL,Math.sqrt(Math.max(0,2*ACCEL*Math.abs(delta)))));
            double nextY=Math.abs(delta)<=speed?floors[target]:y+direction*speed;
            String reason=spanFailure(w,nextY,false);if(!reason.isEmpty()){fault(reason);syncCabin();return;}
            velocity=direction*speed;y=nextY;
            if(Math.abs(y-floors[target])<.001){y=floors[target];at=target;lastDock=at;velocity=0;queue.remove(at);phase=Phase.OPENING;timer=12;}
        }
        syncCabin();if(phase!=Phase.DWELL||timer>0||activeTicks%20==0)setChanged();
    }
    private boolean shaftLoaded(ServerLevel w){
        for(int x=(shaftX-2)>>4;x<=(shaftX+5)>>4;x++)for(int z=(shaftZ-2)>>4;z<=(shaftZ+5)>>4;z++)if(w.getChunkSource().getChunkNow(x,z)==null)return false;return true;
    }
    private String routeFailure(ServerLevel w,double nextY){return spanFailure(w,nextY,true);}
    private String spanFailure(ServerLevel w,double nextY,boolean parked){
        if(!shaftLoaded(w))return "unloaded";
        int support=parked&&at>=0?floors[at]-1:Integer.MIN_VALUE;
        for(int h=(int)Math.floor(Math.min(y,nextY));h<=Math.floor(Math.max(y,nextY)+2.8);h++)for(int x=shaftX;x<=shaftX+3;x++)for(int z=shaftZ;z<=shaftZ+3;z++){
            BlockPos p=new BlockPos(x,h,z);BlockState s=w.getBlockState(p);boolean edge=x==shaftX||x==shaftX+3||z==shaftZ||z==shaftZ+3;
            if(edge){if(s.isAir()||s.getFluidState().isSource())return "shaft_damaged";}
            else if(h!=support&&!s.getCollisionShape(w,p).isEmpty())return "shaft_blocked";
        }return "";
    }
    private int next(){
        int n=-1;double best=Double.MAX_VALUE;
        for(int f:queue){double d=floors[f]-y;if(d*direction>=0&&Math.abs(d)<best){best=Math.abs(d);n=f;}}
        if(n>=0)return n;direction=-direction;for(int f:queue){double d=Math.abs(floors[f]-y);if(d<best){best=d;n=f;}}return n;
    }
    private boolean doorwayOccupied(ServerLevel w){return !w.getEntitiesOfClass(LivingEntity.class,new AABB(shaftX+1,y,shaftZ-.35,shaftX+3,y+2.8,shaftZ+.4),e->!e.isPassenger()).isEmpty();}
    private boolean unmountedInside(ServerLevel w){return !w.getEntitiesOfClass(LivingEntity.class,new AABB(shaftX+1,y-.2,shaftZ+1,shaftX+3,y+2.7,shaftZ+3),e->!e.isPassenger()).isEmpty();}
    private void gates(ServerLevel w,int f,int slide){for(int x=shaftX+1;x<=shaftX+2;x++)for(int dy=0;dy<3;dy++){
        BlockPos p=new BlockPos(x,floors[f]+dy,shaftZ);if(!w.hasChunkAt(p))continue;BlockState s=w.getBlockState(p);
        if(s.is(CityInteriorContent.block("elevator_gate"))&&s.getValue(CityInteriorContent.SLIDE)!=slide)w.setBlock(p,s.setValue(CityInteriorContent.SLIDE,slide),3);
    }}
    private void dock(ServerLevel w,int f,boolean add){for(int x=shaftX+1;x<=shaftX+2;x++)for(int z=shaftZ+1;z<=shaftZ+2;z++){
        BlockPos p=new BlockPos(x,floors[f]-1,z);BlockState old=w.getBlockState(p);BlockState platform=CityBlocks.block("composite_floor").defaultBlockState();
        if(add&&old.isAir())w.setBlock(p,platform,3);else if(!add&&old.equals(platform))w.setBlock(p,Blocks.AIR.defaultBlockState(),3);
    }}
    private void fault(String reason){velocity=0;phase=Phase.FAULT;failure=reason;setChanged();}
    private void syncCabin(){if(cabin()!=null){cabin.setPos(centre());cabin.door(door);cabin.phase(phase.ordinal());int nearest=0;for(int f=1;f<floors.length;f++)if(Math.abs(y-floors[f])<Math.abs(y-floors[nearest]))nearest=f;cabin.floor(nearest);for(Entity rider:List.copyOf(cabin.getPassengers()))cabin.positionRider(rider);}}
    @Override public void setRemoved(){if(cabin()!=null){for(Entity e:List.copyOf(cabin.getPassengers()))if(e instanceof LivingEntity l)CityElevators.rescue(l,this);if(cabin.getPassengers().isEmpty())cabin.discard();else cabin.phase(Phase.FAULT.ordinal());cabin=null;}super.setRemoved();}
    @Override protected void saveAdditional(CompoundTag t,HolderLookup.Provider lookup){super.saveAdditional(t,lookup);t.putInt("LayoutVersion",CityInteriorVersions.VERSION);t.putString("Failure",failure);t.putString("Identity",identity);t.putInt("ShaftX",shaftX);t.putInt("ShaftZ",shaftZ);t.putIntArray("Floors",floors);t.putDouble("CabinY",y);t.putDouble("Velocity",velocity);t.putInt("At",at);t.putInt("Target",target);t.putInt("Timer",timer);t.putInt("Door",door);t.putInt("LastDock",lastDock);t.putString("Phase",phase.name());t.putIntArray("Queue",queue.stream().mapToInt(Integer::intValue).toArray());}
    @Override protected void loadAdditional(CompoundTag t,HolderLookup.Provider lookup){
        super.loadAdditional(t,lookup);failure=t.getString("Failure");identity=t.getString("Identity");shaftX=t.getInt("ShaftX");shaftZ=t.getInt("ShaftZ");floors=t.getIntArray("Floors");y=t.getDouble("CabinY");velocity=t.getDouble("Velocity");at=t.getInt("At");target=t.getInt("Target");timer=t.getInt("Timer");door=t.getInt("Door");lastDock=t.getInt("LastDock");
        try{phase=Phase.valueOf(t.getString("Phase"));}catch(IllegalArgumentException e){phase=Phase.FAULT;}
        queue.clear();for(int n:t.getIntArray("Queue"))if(n>=0&&n<floors.length)queue.add(n);
        if(!Double.isFinite(y)||!Double.isFinite(velocity)||floors.length>256||floors.length==0){identity="";phase=Phase.FAULT;}
        boolean ordered=true;for(int i=1;i<floors.length;i++)if(floors[i]<=floors[i-1])ordered=false;
        if(!ordered||Math.abs(velocity)>SPEED||door<0||door>12||timer<0||timer>1200||identity.length()>200||floors.length>0&&(y<floors[0]||y>floors[floors.length-1]||(long)floors[floors.length-1]-floors[0]>1024L||floors[0]< -2048||floors[floors.length-1]>2048)||phase==Phase.MOVING&&target<0){identity="";phase=Phase.FAULT;}
        if(at>=floors.length||at< -1||target>=floors.length||target< -1){at=-1;target=-1;phase=Phase.FAULT;}
    }
}
