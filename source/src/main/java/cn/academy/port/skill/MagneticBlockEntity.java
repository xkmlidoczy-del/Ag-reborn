/* AcademyCraft1.0.7 EntityBlock/MagManipEntityBlock + LambdaLib1.2.3 Rigidbody. See NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.AbilityDamage;
import cn.academy.port.core.MagneticRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.UUID;

/**
 * Rotating real block. Eight corner sweeps and two sequential movement advances preserve
 * the original Rigidbody+MagManip update order. No FallingBlockEntity vanilla projectile
 * physics, explosion, arbitrary lifetime, bounce or per-flight attack deduplication is added.
 * The original deleted transported blocks on reload: saved state/BE data and recoverable
 * drops deliberately protect a modern survival save from that data-loss bug.
 */
public final class MagneticBlockEntity extends Entity {
    private static final EntityDataAccessor<Integer> BLOCK=SynchedEntityData.defineId(MagneticBlockEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> MOVING=SynchedEntityData.defineId(MagneticBlockEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> YAW_SPEED=SynchedEntityData.defineId(MagneticBlockEntity.class,EntityDataSerializers.FLOAT),PITCH_SPEED=SynchedEntityData.defineId(MagneticBlockEntity.class,EntityDataSerializers.FLOAT);
    private UUID owner;
    private ServerPlayer liveOwner;
    private CompoundTag blockEntityData;
    private BlockPos recoveryPosition;
    private Vec3 target=Vec3.ZERO;
    private boolean placeWhenCollide;
    public float spinYaw,previousSpinYaw,spinPitch,previousSpinPitch;
    private int interpolationSteps;
    private double targetX,targetY,targetZ;

    public MagneticBlockEntity(EntityType<? extends MagneticBlockEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    public MagneticBlockEntity(ServerPlayer player,BlockState state,CompoundTag data,Vec3 position,BlockPos recovery){this(ElectromasterEntities.MAGNETIC_BLOCK.get(),player.level());owner=player.getUUID();liveOwner=player;entityData.set(BLOCK,Block.getId(state));blockEntityData=data==null?null:data.copy();setPos(position);recoveryPosition=recovery.immutable();entityData.set(YAW_SPEED,1+random.nextFloat()*2);entityData.set(PITCH_SPEED,1+random.nextFloat()*2);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(BLOCK,Block.getId(Blocks.AIR.defaultBlockState()));b.define(MOVING,false);b.define(YAW_SPEED,1f);b.define(PITCH_SPEED,1f);}
    public BlockState carriedState(){return Block.stateById(entityData.get(BLOCK));}
    public boolean hovering(){return entityData.get(MOVING);}
    public UUID ownerId(){return owner;}
    public void moveToTarget(Vec3 point){target=point;entityData.set(MOVING,true);placeWhenCollide=false;}
    public void release(Vec3 velocity){entityData.set(MOVING,false);placeWhenCollide=true;setDeltaMovement(velocity);hurtMarked=true;}
    // Original EntityBlock inherits Entity.canBeCollidedWith()==false; skill rays ignore it.
    @Override public boolean isPickable(){return false;}
    @Override public void lerpTo(double x,double y,double z,float yaw,float pitch,int steps){targetX=x;targetY=y;targetZ=z;interpolationSteps=Math.max(1,steps);}
    @Override public void tick(){
        super.tick();previousSpinYaw=spinYaw;previousSpinPitch=spinPitch;spinYaw+=entityData.get(YAW_SPEED);spinPitch+=entityData.get(PITCH_SPEED);
        if(level().isClientSide){if(interpolationSteps>0){setPos(getX()+(targetX-getX())/interpolationSteps,getY()+(targetY-getY())/interpolationSteps,getZ()+(targetZ-getZ())/interpolationSteps);interpolationSteps--;}else setPos(position().add(getDeltaMovement()));return;}
        if(carriedState().isAir()){discard();return;}
        if(!finite(position())||!finite(getDeltaMovement())){recover();return;}
        ServerPlayer player=liveOwner!=null&&liveOwner.level()==level()&&!liveOwner.isRemoved()?liveOwner:owner!=null?((ServerLevel)level()).getPlayerByUUID(owner) instanceof ServerPlayer p?p:null:null;
        if(hovering()&&(player==null||!player.isAlive()||player.isRemoved()||!MagManip.owns(player,this)))release(getDeltaMovement());
        // Rigidbody performs accurate collision first using last tick's motion.
        HitResult hit=collision(player);
        if(hit instanceof EntityHitResult e&&player!=null)AbilityDamage.attack(player,"electromaster.mag_manip",e.getEntity(),MagneticRules.BLOCK_COLLISION_DAMAGE);
        if(placeWhenCollide&&hit instanceof BlockHitResult b&&hit.getType()==HitResult.Type.BLOCK){place(b,player);return;}
        Vec3 old=getDeltaMovement();Vec3 next;
        // Rigidbody itself already moves once before the subclass calculates hover/gravity.
        setPos(position().add(old));
        if(hovering()){var n=MagneticRules.hoverVelocity(vector(target.subtract(position())));next=new Vec3(n.x(),n.y(),n.z());}
        else next=old.add(0,-MagneticRules.BLOCK_GRAVITY,0);
        setDeltaMovement(next);setPos(position().add(next));
        if(getY()<level().getMinBuildHeight()-16||getY()>level().getMaxBuildHeight()+256)recover();
    }
    private HitResult collision(ServerPlayer player){
        double half=getBbWidth()/2;double height=getBbHeight();Vec3 motion=getDeltaMovement();
        // Preserve original corner order and return the first corner with any collision.
        double[][] corners={{-half,0,-half},{-half,0,half},{half,0,half},{half,0,-half},{-half,height,-half},{-half,height,half},{half,height,half},{half,height,-half}};
        for(double[] c:corners){Vec3 start=position().add(c[0],c[1],c[2]);HitResult hit=ClassicRaytrace.perform(this,start,start.add(motion),ClipContext.Fluid.NONE,e->e!=player);if(hit.getType()!=HitResult.Type.MISS)return hit;}
        return null;
    }
    private void place(BlockHitResult hit,ServerPlayer player){
        var world=(ServerLevel)level();BlockPos pos=hit.getBlockPos();Direction side=hit.getDirection();
        for(int i=0;i<10;i++){
            if(!world.getBlockState(pos).canBeReplaced()){pos=pos.relative(side);continue;}
            if(!world.hasChunkAt(pos)||!world.getWorldBorder().isWithinBounds(pos)||(player!=null&&!world.mayInteract(player,pos))){drop();return;}
            var snapshot=BlockSnapshot.create(world.dimension(),world,pos);
            BlockState planned=carriedState();
            // Pre-mutation event: snapshots represent replaced data, while event accessors expose
            // the block about to be placed. This avoids speculative placement/drop duplication.
            var event=new BlockEvent.EntityPlaceEvent(snapshot,world.getBlockState(hit.getBlockPos()),player==null?this:player){
                @Override public BlockState getState(){return planned;}
                @Override public BlockState getPlacedBlock(){return planned;}
            };
            if(NeoForge.EVENT_BUS.post(event).isCanceled()){drop();return;}
            if(world.setBlock(pos,carriedState(),3)){
                var te=world.getBlockEntity(pos);if(te!=null&&blockEntityData!=null){te.loadWithComponents(blockEntityData,world.registryAccess());te.setChanged();world.sendBlockUpdated(pos,carriedState(),carriedState(),3);}
                blockEntityData=null;discard();return;
            }
            break;
        }
        // Original discards even if all ten positions are occupied; modern adapter drops the block.
        drop();
    }
    private void recover(){
        if(recoveryPosition!=null&&level().hasChunkAt(recoveryPosition))setPos(Vec3.atCenterOf(recoveryPosition));
        drop();
    }
    private void drop(){
        ItemStack stack=new ItemStack(carriedState().getBlock().asItem());
        if(!stack.isEmpty()){
            var properties=NbtUtils.writeBlockState(carriedState()).getCompound("Properties");
            if(!properties.isEmpty()){var values=new java.util.LinkedHashMap<String,String>();for(String name:properties.getAllKeys())values.put(name,properties.getString(name));stack.set(DataComponents.BLOCK_STATE,new net.minecraft.world.item.component.BlockItemStateProperties(values));}
            if(blockEntityData!=null)stack.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(blockEntityData));spawnAtLocation(stack);
        }
        blockEntityData=null;discard();
    }
    private static MagneticRules.Vector vector(Vec3 v){return new MagneticRules.Vector(v.x,v.y,v.z);}
    private static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    @Override protected void addAdditionalSaveData(CompoundTag tag){tag.put("block_state",NbtUtils.writeBlockState(carriedState()));if(owner!=null)tag.putUUID("owner",owner);if(blockEntityData!=null)tag.put("block_entity",blockEntityData.copy());if(recoveryPosition!=null)tag.putLong("recovery",recoveryPosition.asLong());tag.putFloat("yaw_speed",entityData.get(YAW_SPEED));tag.putFloat("pitch_speed",entityData.get(PITCH_SPEED));tag.putFloat("spin_yaw",spinYaw);tag.putFloat("spin_pitch",spinPitch);}
    @Override protected void readAdditionalSaveData(CompoundTag tag){entityData.set(BLOCK,Block.getId(NbtUtils.readBlockState(level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),tag.getCompound("block_state"))));owner=tag.hasUUID("owner")?tag.getUUID("owner"):null;blockEntityData=tag.contains("block_entity",10)?tag.getCompound("block_entity").copy():null;recoveryPosition=tag.contains("recovery")?BlockPos.of(tag.getLong("recovery")):blockPosition();entityData.set(YAW_SPEED,tag.getFloat("yaw_speed"));entityData.set(PITCH_SPEED,tag.getFloat("pitch_speed"));spinYaw=previousSpinYaw=tag.getFloat("spin_yaw");spinPitch=previousSpinPitch=tag.getFloat("spin_pitch");entityData.set(MOVING,false);placeWhenCollide=true;}
}
