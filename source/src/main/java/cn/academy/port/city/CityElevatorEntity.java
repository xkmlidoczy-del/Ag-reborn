package cn.academy.port.city;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Real kinematic cabin; passengers have fixed standing attachments and ordinary client interpolation. */
public final class CityElevatorEntity extends Entity {
    private static final EntityDataAccessor<BlockPos> CONTROLLER=SynchedEntityData.defineId(CityElevatorEntity.class,EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> DOOR=SynchedEntityData.defineId(CityElevatorEntity.class,EntityDataSerializers.INT),PHASE=SynchedEntityData.defineId(CityElevatorEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLOOR=SynchedEntityData.defineId(CityElevatorEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> SLOTS=SynchedEntityData.defineId(CityElevatorEntity.class,EntityDataSerializers.STRING);
    private int interpolation;private double tx,ty,tz;private final Map<UUID,Integer> slots=new HashMap<>();
    public CityElevatorEntity(EntityType<?> t,Level w){super(t,w);setNoGravity(true);noPhysics=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(CONTROLLER,BlockPos.ZERO);b.define(DOOR,0);b.define(PHASE,0);b.define(SLOTS,"");b.define(FLOOR,0);}
    public BlockPos controller(){return entityData.get(CONTROLLER);}public void controller(BlockPos p){entityData.set(CONTROLLER,p.immutable());}
    public int displayFloor(){return entityData.get(FLOOR);}public void floor(int n){entityData.set(FLOOR,n);}public int phaseValue(){return entityData.get(PHASE);}
    public int door(){return entityData.get(DOOR);}public void door(int n){entityData.set(DOOR,n);}public void phase(int n){entityData.set(PHASE,n);}
    public CityElevatorController owner(){return !level().isClientSide&&level().hasChunkAt(controller())&&level().getBlockEntity(controller())instanceof CityElevatorController c&&!c.isRemoved()?c:null;}
    @Override public boolean shouldBeSaved(){return false;}
    @Override protected void readAdditionalSaveData(CompoundTag t){}
    @Override protected void addAdditionalSaveData(CompoundTag t){}
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps){if(!level().isClientSide){super.lerpTo(x,y,z,yaw,pitch,steps);return;}tx=x;ty=y;tz=z;interpolation=Math.max(1,steps);}
    @Override public double lerpTargetX(){return interpolation>0?tx:getX();}@Override public double lerpTargetY(){return interpolation>0?ty:getY();}@Override public double lerpTargetZ(){return interpolation>0?tz:getZ();}
    @Override public void tick(){super.tick();if(level().isClientSide&&interpolation>0){lerpPositionAndRotationStep(interpolation,tx,ty,tz,0,0);interpolation--;}else if(!level().isClientSide&&owner()==null&&tickCount>20){if(tickCount%20==0)for(Entity e:List.copyOf(getPassengers()))if(e instanceof LivingEntity l)CityElevators.rescue(l,null);if(getPassengers().isEmpty())discard();else phase(CityElevatorController.Phase.FAULT.ordinal());}}
    @Override public boolean isPickable(){return true;}
    @Override public boolean shouldRiderSit(){return false;}
    @Override protected boolean canAddPassenger(Entity e){return getPassengers().size()<4&&e instanceof Player;}
    @Override protected void addPassenger(Entity e){super.addPassenger(e);int slot=0;while(slots.containsValue(slot))slot++;slots.put(e.getUUID(),slot);syncSlots();}
    @Override protected void removePassenger(Entity e){super.removePassenger(e);slots.remove(e.getUUID());syncSlots();}
    private void syncSlots(){if(level().isClientSide)return;StringJoiner j=new StringJoiner(",");for(Entity e:getPassengers())j.add(e.getId()+":"+slots.getOrDefault(e.getUUID(),0));entityData.set(SLOTS,j.toString());}
    private int slot(Entity e){if(!level().isClientSide)return slots.getOrDefault(e.getUUID(),0);for(String s:entityData.get(SLOTS).split(",")){String[] p=s.split(":");if(p.length==2&&p[0].equals(Integer.toString(e.getId())))try{return Integer.parseInt(p[1]);}catch(NumberFormatException ignored){}}return Math.max(0,getPassengers().indexOf(e));}
    @Override protected Vec3 getPassengerAttachmentPoint(Entity e,EntityDimensions d,float partial){int n=slot(e);double x=(n%2==0?-.4:.4),z=(n<2?-.4:.4);return new Vec3(x,e.getVehicleAttachmentPoint(this).y,z);}
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity p){var o=owner();return o==null?CityElevators.safeExit(level(),blockPosition(),p).orElse(position()):o.exit(p).orElse(position());}
    @Override public InteractionResult interact(Player p,InteractionHand hand){if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;if(!level().isClientSide&&p instanceof ServerPlayer s){var o=owner();if(o!=null){if(!p.isPassenger())o.board(p);CityElevatorNetwork.open(s,o,Math.max(0,o.current()));}else if(p.getVehicle()==this)CityElevators.rescue(p,null);}return InteractionResult.sidedSuccess(level().isClientSide);}
}
