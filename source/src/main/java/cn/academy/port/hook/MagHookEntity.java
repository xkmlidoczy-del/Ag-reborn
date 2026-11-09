/* AcademyCraft1.0.7 EntityMagHook and LambdaLib1.2.3 Rigidbody/Raytrace adaptation. GPLv3/MIT; see NOTICE. */
package cn.academy.port.hook;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** Authoritative recoverable support target. This deliberately adds no rope, pull, timer or durability. */
public final class MagHookEntity extends Entity {
    private static final EntityDataAccessor<Byte> STATE=SynchedEntityData.defineId(MagHookEntity.class,EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<BlockPos> HOST=SynchedEntityData.defineId(MagHookEntity.class,EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Optional<UUID>> OWNER=SynchedEntityData.defineId(MagHookEntity.class,EntityDataSerializers.OPTIONAL_UUID);
    private boolean pendingStill,anchorActive,pendingReturn,returnCommitted;

    public MagHookEntity(EntityType<? extends MagHookEntity> type,Level world){
        super(type,world);noPhysics=true;noCulling=true;setNoGravity(true);setOnGround(false);
    }
    public MagHookEntity(Player player){
        this(ClassicHooks.ENTITY.get(),player.level());
        entityData.set(OWNER,Optional.of(player.getUUID()));setPos(player.getEyePosition());
        double[] raw=cn.academy.port.clientless.ClassicLateTrig.direction(player.getYHeadRot(),player.getXRot());
        Vec3 direction=new Vec3(raw[0],raw[1],raw[2]).normalize();
        setDeltaMovement(direction.scale(ClassicHookRules.SPEED));
        setYRot((float)(-Math.atan2(direction.x,direction.z)*180/Math.PI));
        setXRot((float)(-Math.atan2(direction.y,Math.sqrt(direction.x*direction.x+direction.z*direction.z))*180/Math.PI));
        yRotO=getYRot();xRotO=getXRot();hasImpulse=true;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){builder.define(STATE,(byte)0);builder.define(HOST,BlockPos.ZERO);builder.define(OWNER,Optional.empty());}
    public boolean isHit(){return ClassicHookRules.hit(entityData.get(STATE));}
    public int hitSide(){return ClassicHookRules.side(entityData.get(STATE));}
    public BlockPos host(){return entityData.get(HOST);}
    public Optional<UUID> ownerUUID(){return entityData.get(OWNER);}
    public Player owner(){return ownerUUID().map(id->level().getPlayerByUUID(id)).orElse(null);}
    @Override public boolean isPickable(){return isHit()&&!isRemoved();}
    @Override public boolean canBeCollidedWith(){return isHit()&&!isRemoved();}
    @Override public EntityDimensions getDimensions(Pose pose){float size=anchorActive?1F:.5F;return EntityDimensions.scalable(size,size);}
    @Override public boolean fudgePositionAfterSizeChange(EntityDimensions previous){return false;}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){
        super.onSyncedDataUpdated(key);
        if(key==STATE&&isHit())pendingStill=true;
    }
    @Override public void tick(){
        super.tick();if(isRemoved())return;
        if(!finite(position())||!finite(getDeltaMovement())){discard();return;}
        if(pendingReturn){if(!level().isClientSide)tryReturn();return;}
        if(pendingStill){pendingStill=false;anchorActive=true;refreshDimensions();setDeltaMovement(Vec3.ZERO);}
        if(anchorActive){
            preRender();
            // Unloaded hosts are not air; never cause a load, drop or duplicate across chunk unload.
            if(!level().isClientSide&&level().hasChunkAt(host())&&level().isEmptyBlock(host()))requestReturn();
            return;
        }
        Vec3 velocity=getDeltaMovement();
        if(!level().isClientSide){
            HitResult hit=trace(position(),position().add(velocity));
            if(hit instanceof EntityHitResult entityHit){
                Entity other=entityHit.getEntity();
                if(ClassicHookRules.returnOnImpact(other instanceof MagHookEntity,other instanceof MagHookEntity h&&h.isHit())){
                    if(!(other instanceof MagHookEntity)){
                        Player shooter=owner();
                        other.hurt(shooter==null?damageSources().thrown(this,null):damageSources().playerAttack(shooter),ClassicHookRules.DAMAGE);
                    }
                    // Original return occurs at the old tick position, before gravity/displacement.
                    requestReturn();return;
                }
            }else if(hit instanceof BlockHitResult block&&block.getType()!=HitResult.Type.MISS){
                entityData.set(HOST,block.getBlockPos().immutable());
                entityData.set(STATE,ClassicHookRules.packed(true,block.getDirection().get3DDataValue()));pendingStill=true;
            }
        }
        // Even the block-hit tick completes Rigidbody's new-velocity advance; anchoring starts next tick.
        Vec3 next=velocity.add(0,-ClassicHookRules.GRAVITY,0);setDeltaMovement(next);setPos(position().add(next));
    }
    private HitResult trace(Vec3 start,Vec3 end){
        BlockHitResult block=level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        Entity nearest=null;double best=Double.POSITIVE_INFINITY;
        for(Entity candidate:level().getEntities(this,new AABB(start,end).inflate(1),e->e.isPickable()&&!e.isRemoved()&&!ownerUUID().filter(e.getUUID()::equals).isPresent())){
            Optional<Vec3> intercept=candidate.getBoundingBox().inflate(.3).clip(start,end);
            if(intercept.isEmpty())continue;double distance=start.distanceToSqr(intercept.get());
            if(distance<best){best=distance;nearest=candidate;}
        }
        // LambdaLib chooses nearest intercept, but compares that entity's position with block hit.
        if(nearest!=null&&(block.getType()==HitResult.Type.MISS||start.distanceToSqr(nearest.position())<=start.distanceToSqr(block.getLocation())))return new EntityHitResult(nearest,nearest.position());
        return block;
    }
    /** Source face math also consumed by renderer; server state owns host coordinates. */
    public ClassicHookRules.Anchor anchor(){var p=host();return ClassicHookRules.anchor(p.getX(),p.getY(),p.getZ(),hitSide(),getYRot());}
    public void preRender(){if(isHit()){var a=anchor();setYRot(a.yaw());setXRot(a.pitch());var p=a.position();setPos(p.x(),p.y(),p.z());}}
    @Override public boolean hurt(DamageSource source,float damage){
        if(isHit()&&!level().isClientSide&&source.getEntity() instanceof Player)requestReturn();
        return true;
    }
    private void requestReturn(){if(level().isClientSide||isRemoved()||returnCommitted)return;pendingReturn=true;tryReturn();}
    private void tryReturn(){
        if(level().isClientSide||isRemoved()||returnCommitted)return;
        var drop=new ItemEntity(level(),getX(),getY(),getZ(),new ItemStack(ClassicHooks.MAGHOOK.get()));
        // Cancellation conserves the entity and retries without repeating impact damage.
        if(level().addFreshEntity(drop)){returnCommitted=true;pendingReturn=false;discard();}
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag){
        tag.putBoolean("isHit",isHit());tag.putInt("hitSide",hitSide());var p=host();tag.putInt("hookX",p.getX());tag.putInt("hookY",p.getY());tag.putInt("hookZ",p.getZ());
        ownerUUID().ifPresent(id->tag.putUUID("Owner",id));tag.putBoolean("ReturnPending",pendingReturn);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag){
        int side=tag.getInt("hitSide");boolean hit=tag.getBoolean("isHit");
        if(hit&&!ClassicHookRules.validSide(side)){discard();return;}
        entityData.set(OWNER,tag.hasUUID("Owner")?Optional.of(tag.getUUID("Owner")):Optional.empty());
        entityData.set(HOST,new BlockPos(tag.getInt("hookX"),tag.getInt("hookY"),tag.getInt("hookZ")));
        entityData.set(STATE,ClassicHookRules.packed(hit,hit?side:0));pendingStill=hit;pendingReturn=tag.getBoolean("ReturnPending");returnCommitted=false;
    }
    private static boolean finite(Vec3 vector){return Double.isFinite(vector.x)&&Double.isFinite(vector.y)&&Double.isFinite(vector.z);}
}
