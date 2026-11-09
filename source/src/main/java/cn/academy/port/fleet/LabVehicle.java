package cn.academy.port.fleet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** Per-model seating, controls, segmented block collision and independent tyre ground samples. */
public final class LabVehicle extends Entity {
 private static final EntityDataAccessor<Integer> VARIANT=SynchedEntityData.defineId(LabVehicle.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Float> SPEED=SynchedEntityData.defineId(LabVehicle.class,EntityDataSerializers.FLOAT),STEER=SynchedEntityData.defineId(LabVehicle.class,EntityDataSerializers.FLOAT),PITCH=SynchedEntityData.defineId(LabVehicle.class,EntityDataSerializers.FLOAT),ROLL=SynchedEntityData.defineId(LabVehicle.class,EntityDataSerializers.FLOAT);
 private static final EntityDataAccessor<Float> DRIVE=SynchedEntityData.defineId(LabVehicle.class,EntityDataSerializers.FLOAT);
 private static final EntityDataAccessor<Boolean> SIREN=SynchedEntityData.defineId(LabVehicle.class,EntityDataSerializers.BOOLEAN);
 private static final EntityDataAccessor<Boolean> DOOR=SynchedEntityData.defineId(LabVehicle.class,EntityDataSerializers.BOOLEAN);
 private java.util.UUID response;private long dutyEnd;
 public java.util.UUID response(){return response;}
 public void response(java.util.UUID id,long end){response=id;dutyEnd=end;}
 public boolean canDriveAt(Vec3 p,float yaw){return !blocked(p,yaw);}
 public void aiInput(cn.academy.port.city.defense.GuardianEntity driver,float forward,float turn){if(getFirstPassenger()!=driver||response==null||!response.equals(driver.response()))return;throttle=net.minecraft.util.Mth.clamp(forward,-1,1);steer=net.minecraft.util.Mth.clamp(turn,-1,1);lastInput=level().getGameTime();}
 private float throttle,steer,doorVisual,doorPrevious,visualSteer,oldSteer,visualPitch,oldPitch,visualRoll,oldRoll;private long lastInput,lastHorn=-100;public float wheelPhase;
 public LabVehicle(EntityType<?> type,Level level){super(type,level);blocksBuilding=true;}
 @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(VARIANT,0);b.define(SPEED,0f);b.define(STEER,0f);b.define(PITCH,0f);b.define(ROLL,0f);b.define(DOOR,false);b.define(SIREN,false);b.define(DRIVE,0f);}
 public int variant(){return Math.max(0,Math.min(5,entityData.get(VARIANT)));}
 public void variant(int v){entityData.set(VARIANT,Math.max(0,Math.min(5,v)));refreshDimensions();}
 public VehicleConfig config(){return VehicleConfig.get(variant());}
 public float speed(){return entityData.get(SPEED);}public float steering(){return entityData.get(STEER);}public float bodyPitch(){return entityData.get(PITCH);}public float bodyRoll(){return entityData.get(ROLL);}
 public float renderSteering(float partial){return level().isClientSide?oldSteer+(visualSteer-oldSteer)*partial:steering();}
 public float renderPitch(float partial){return level().isClientSide?oldPitch+(visualPitch-oldPitch)*partial:bodyPitch();}
 public float renderRoll(float partial){return level().isClientSide?oldRoll+(visualRoll-oldRoll)*partial:bodyRoll();}
 public float driveLoad(){return entityData.get(DRIVE);}
 public boolean doorOpen(){return entityData.get(DOOR);}
 public boolean siren(){return entityData.get(SIREN);}
 public void horn(){long now=level().getGameTime();if(now-lastHorn<12)return;lastHorn=now;AudioDefs.emit(this,variant(),"horn",now);}
 public void toggleSiren(){if(variant()==3||variant()==4)entityData.set(SIREN,!siren());}
 public void toggleDoor(){entityData.set(DOOR,!entityData.get(DOOR));}
 public float doorAmount(float partial){return doorPrevious+(doorVisual-doorPrevious)*partial;}
 public int seatIndex(Entity passenger){return Math.max(0,getPassengers().indexOf(passenger));}
 public void input(Player p,float forward,float turn){if(getFirstPassenger()!=p||!Float.isFinite(forward)||!Float.isFinite(turn))return;throttle=Math.max(-1,Math.min(1,forward));steer=Math.max(-1,Math.min(1,turn));lastInput=level().getGameTime();}
 public Vec3 localToWorld(Vec3 v){return localToWorld(v,position(),getYRot(),bodyPitch(),bodyRoll());}
 public static Vec3 localToWorld(Vec3 v,Vec3 pos,float yaw,float pitch,float roll){
  double a=Math.toRadians(roll),b=Math.toRadians(-pitch),c=Math.toRadians(yaw);double x=v.x*Math.cos(a)-v.y*Math.sin(a),y=v.x*Math.sin(a)+v.y*Math.cos(a),z=v.z;
  double yy=y*Math.cos(b)-z*Math.sin(b),zz=y*Math.sin(b)+z*Math.cos(b);
  return pos.add(x*Math.cos(c)-zz*Math.sin(c),yy,x*Math.sin(c)+zz*Math.cos(c));
 }
 private boolean blocked(Vec3 pos,float yaw){
  double a=Math.toRadians(yaw),co=Math.cos(a),si=Math.sin(a);
  for(double[] s:config().collision){double x=pos.x+s[0]*co-s[2]*si,z=pos.z+s[0]*si+s[2]*co,hx=Math.abs(co)*s[3]+Math.abs(si)*s[5],hz=Math.abs(si)*s[3]+Math.abs(co)*s[5];
   AABB box=new AABB(x-hx,pos.y+s[1]-s[4],z-hz,x+hx,pos.y+s[1]+s[4],z+hz);
   if(level().getBlockCollisions(this,box).iterator().hasNext())return true;
  }return false;
 }
 @Override public void tick(){
  super.tick();wheelPhase=(wheelPhase+speed()*115)%360;oldSteer=visualSteer;visualSteer+=(steering()-visualSteer)*.65f;oldPitch=visualPitch;visualPitch+=(bodyPitch()-visualPitch)*.65f;oldRoll=visualRoll;visualRoll+=(bodyRoll()-visualRoll)*.65f;doorPrevious=doorVisual;doorVisual+=((entityData.get(DOOR)?1:0)-doorVisual)*.18f;
  if(level().isClientSide)return;if(response!=null&&level().getGameTime()>dutyEnd){ejectPassengers();discard();return;}var cfg=config();if(getFirstPassenger()==null||level().getGameTime()-lastInput>8){throttle=0;steer=0;}
  entityData.set(DRIVE,throttle);
  float max=cfg.scooter()?.34f:cfg.kind.equals("bus")?.26f:.32f,speed=speed();if(throttle!=0)speed+=throttle*(cfg.kind.equals("bus")?.007f:.011f);else speed*=.95f;
  speed=Math.max(-max*.4f,Math.min(max,speed));if(Math.abs(speed)<.002f&&throttle==0)speed=0;
  float smooth=steering()+(steer-steering())*.25f;float yaw=getYRot()-smooth*Math.min(2.4f,Math.abs(speed)*10)*Math.signum(speed);double a=Math.toRadians(yaw);
  Vec3 next=position().add(-Math.sin(a)*speed,0,Math.cos(a)*speed);double total=0,front=0,rear=0,left=0,right=0;int hits=0,nf=0,nr=0,nl=0,ng=0;
  for(var wheel:cfg.wheels){Vec3 sample=localToWorld(new Vec3(wheel.position().x,0,wheel.position().z),next,yaw,0,0);var hit=level().clip(new ClipContext(new Vec3(sample.x,getY()+1.4,sample.z),new Vec3(sample.x,getY()-2.2,sample.z),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
   if(hit.getType()!=HitResult.Type.BLOCK)continue;double h=hit.getLocation().y-wheel.position().y+wheel.radius();total+=h;hits++;
   if(wheel.front()){front+=h;nf++;}else{rear+=h;nr++;}if(wheel.position().x<0){left+=h;nl++;}else if(wheel.position().x>0){right+=h;ng++;}
  }
  double dy=getDeltaMovement().y-.04;if(hits>0){double ground=total/hits;dy=Math.max(-.25,Math.min(.25,(ground-getY())*.7));setOnGround(Math.abs(ground-getY())<.25);}else setOnGround(false);
  next=next.add(0,Math.max(-.6,dy),0);
  if(blocked(next,yaw)){speed=0;next=new Vec3(getX(),next.y,getZ());if(blocked(next,getYRot()))next=position();yaw=getYRot();}
  setYRot(yaw);setPos(next.x,next.y,next.z);setDeltaMovement(-Math.sin(a)*speed,dy,Math.cos(a)*speed);
  float pitch=0,roll=0;if(nf>0&&nr>0){double wheelbase=Math.abs(cfg.wheels.get(0).position().z-cfg.wheels.get(cfg.wheels.size()-1).position().z);pitch=(float)Math.toDegrees(Math.atan2(front/nf-rear/nr,Math.max(1,wheelbase)));}
  if(nl>0&&ng>0)roll=(float)Math.toDegrees(Math.atan2(right/ng-left/nl,Math.max(.5,cfg.width*.75)));
  entityData.set(PITCH,bodyPitch()+(Math.max(-16,Math.min(16,pitch))-bodyPitch())*.25f);entityData.set(ROLL,bodyRoll()+(Math.max(-10,Math.min(10,roll))-bodyRoll())*.25f);
  entityData.set(SPEED,speed);entityData.set(STEER,smooth);
 }
 @Override public boolean isPickable(){return true;}
 @Override public EntityDimensions getDimensions(Pose pose){if(entityData==null)return super.getDimensions(pose);var c=config();return EntityDimensions.scalable(c.width,c.height);}
 @Override protected AABB makeBoundingBox(){if(entityData==null)return super.makeBoundingBox();var c=config();double a=Math.toRadians(getYRot()),hx=(Math.abs(Math.cos(a))*c.width+Math.abs(Math.sin(a))*c.length)/2,hz=(Math.abs(Math.sin(a))*c.width+Math.abs(Math.cos(a))*c.length)/2;return new AABB(getX()-hx,getY(),getZ()-hz,getX()+hx,getY()+c.height,getZ()+hz);}
 @Override protected boolean canAddPassenger(Entity passenger){return (passenger instanceof Player&&response==null||passenger instanceof cn.academy.port.city.defense.GuardianEntity g&&java.util.Objects.equals(response,g.response()))&&getPassengers().size()<config().seats.size();}
 @Override public InteractionResult interact(Player p,InteractionHand hand){if(!level().isClientSide){if(p.isSecondaryUseActive())toggleDoor();else p.startRiding(this);}return InteractionResult.sidedSuccess(level().isClientSide);}
 @Override protected void positionRider(Entity passenger,Entity.MoveFunction move){super.positionRider(passenger,move);if(passenger instanceof LivingEntity rider){rider.yBodyRotO=this.yRotO;rider.setYBodyRot(getYRot());}}
 @Override protected Vec3 getPassengerAttachmentPoint(Entity passenger,EntityDimensions d,float partial){var seats=config().seats;Vec3 seat=seats.get(Math.min(seats.size()-1,seatIndex(passenger)));return localToWorld(seat.add(0,-.15,0),Vec3.ZERO,getYRot(),bodyPitch(),bodyRoll());}
 @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger){var c=config();Vec3 seat=c.seats.get(Math.min(c.seats.size()-1,seatIndex(passenger)));for(int side:new int[]{-1,1}){Vec3 v=localToWorld(new Vec3(side*(c.width/2+.65),.2,seat.z));var hit=level().clip(new ClipContext(v.add(0,1.5,0),v.add(0,-2,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));double y=hit.getType()==HitResult.Type.BLOCK?hit.getLocation().y:v.y;AABB box=new AABB(v.x-.3,y+.01,v.z-.3,v.x+.3,y+1.8,v.z+.3);if(!level().getBlockCollisions(passenger,box).iterator().hasNext())return new Vec3(v.x,y+.02,v.z);}return position().add(0,c.height+.1,0);}
 @Override protected void readAdditionalSaveData(CompoundTag tag){variant(tag.getInt("variant"));response=tag.hasUUID("Response")?tag.getUUID("Response"):null;dutyEnd=tag.getLong("DutyEnd");entityData.set(SIREN,tag.getBoolean("Siren"));entityData.set(DOOR,tag.getBoolean("Door"));}
 @Override protected void addAdditionalSaveData(CompoundTag tag){tag.putInt("variant",variant());if(response!=null)tag.putUUID("Response",response);tag.putLong("DutyEnd",dutyEnd);tag.putBoolean("Siren",siren());tag.putBoolean("Door",doorOpen());}
}
