/* AcademyCraft1.0.7 EntitySilbarn and LambdaLib1.2.3 Rigidbody adaptation. GPLv3/MIT; see NOTICE. */
package cn.academy.port.skill;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
/** Real pickable support semiconductor: block-only collision, drag .8, gravity .12 after update50. */
public final class SilbarnEntity extends Entity {
 private static final EntityDataAccessor<Boolean> HIT=SynchedEntityData.defineId(SilbarnEntity.class,EntityDataSerializers.BOOLEAN);
 private int deadAt=Integer.MAX_VALUE;public final int axisX,axisY,axisZ;
 public SilbarnEntity(EntityType<? extends SilbarnEntity> type,Level world){super(type,world);noPhysics=true;setNoGravity(true);axisX=random.nextInt();axisY=random.nextInt();axisZ=random.nextInt();}
 public SilbarnEntity(ServerPlayer p){this(MeltdownerLateEntities.SILBARN_ENTITY.get(),p.serverLevel());setPos(p.getEyePosition());setDeltaMovement(MeltdownerLateSupport.direction(p));setYRot(p.getYHeadRot());setXRot(p.getXRot());}
 @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(HIT,false);}
 @Override public boolean isPickable(){return true;}public boolean isHit(){return entityData.get(HIT);}
 public void collideWithSelf(){collide(true);}
 private void collide(boolean heavy){if(level().isClientSide||isHit())return;entityData.set(HIT,true);deadAt=tickCount+10;level().playSound(null,getX(),getY(),getZ(),SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy",heavy?"entity.silbarn_heavy":"entity.silbarn_light")),SoundSource.MASTER,.5F,1F);}
 @Override public void tick(){super.tick();Vec3 velocity=getDeltaMovement();if(!MeltdownerStarterSupport.finite(position())||!MeltdownerStarterSupport.finite(velocity)){discard();return;}if(!level().isClientSide){var hit=level().clip(new ClipContext(position(),position().add(velocity),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));if(hit.getType()!=HitResult.Type.MISS)collide(false);}
  // Rigidbody performs collision with previous velocity; then gravity, drag and displacement.
  Vec3 next=velocity.add(0,tickCount>50?-.12:0,0).scale(.8);setDeltaMovement(next);setPos(position().add(next));if(!level().isClientSide&&tickCount>=deadAt)discard();}
 @Override protected void readAdditionalSaveData(CompoundTag tag){discard();}
 @Override protected void addAdditionalSaveData(CompoundTag tag){}
 @Override public boolean shouldBeSaved(){return false;}
}
