package cn.academy.biohum;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class AbilityBolt extends Projectile {
 private static final EntityDataAccessor<String> SKILL=SynchedEntityData.defineId(AbilityBolt.class,EntityDataSerializers.STRING);
 public String kind="physical";public float power=4;public int age,bounces;public double traveled;
 private double range=60;
 private long cast;
 private final Set<UUID> reflectedBy=new HashSet<>();
 public AbilityBolt(EntityType<? extends AbilityBolt> t,Level l){super(t,l);}
 @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(SKILL,"");}
 public String skill(){return entityData.get(SKILL);}
 public void skill(String id,double maxRange){entityData.set(SKILL,id);range=maxRange>0?maxRange:range;}
 void castStarted(long value){cast=value;}
 public boolean supernatural(){return !kind.equals("physical");}
 public boolean reflectable(){return kind.equals("physical")||kind.equals("electric")||kind.equals("vector");}
 @Override public void tick(){
  super.tick();if(level().isClientSide)return;
  age++;
  Vec3 delta=getDeltaMovement();
  if(delta.lengthSqr()<1.0e-8||traveled>=range||getOwner()==null||!getOwner().isAlive()){
   cue(Payloads.Cue.WHIFF,position());discard();return;
  }
  if(delta.length()>range-traveled)delta=delta.normalize().scale(range-traveled);
  Vec3 endpoint=SkillTrajectory.loadedEnd((ServerLevel)level(),position(),position().add(delta));
  delta=endpoint.subtract(position());
  if(delta.lengthSqr()<1.0e-8){discard();return;}
  setDeltaMovement(delta);
  var hit=ProjectileUtil.getHitResultOnMoveVector(this,e->canHitEntity(e)&&!(getOwner() instanceof HumNpc n&&n.ally(e)));traveled+=delta.length();
  if(hit instanceof EntityHitResult eh&&eh.getEntity() instanceof LivingEntity t){
   if(t instanceof HumNpc a&&CombatSkills.reflects(a)&&reflectable()){
    if(!reflectedBy.add(a.getUUID())){discard();return;}
    bounces++;var old=getOwner();setOwner(a);setPos(eh.getLocation());
    setDeltaMovement(old.getBoundingBox().getCenter().subtract(position()).normalize().scale(delta.length()));
    Vec3 next=SkillTrajectory.surface((ServerLevel)level(),position(),position().add(getDeltaMovement())).position();
    Payloads.cue(a,"vector_guard",Payloads.Cue.REFLECT,position(),next,.8f,0,getUUID());return;
   }
   if(t instanceof HumNpc a&&a.characterId.equals("touma")&&supernatural()&&a.guardUntil>level().getGameTime()&&eh.getLocation().distanceToSqr(a.rightHand())<.7*.7){
    Payloads.cue(a,"imagine_breaker",Payloads.Cue.CANCEL,eh.getLocation(),eh.getLocation(),.8f);discard();return;
   }
   if(getOwner() instanceof LivingEntity owner&&t.hurt(kind.equals("magic")?damageSources().indirectMagic(this,owner):damageSources().mobProjectile(this,owner),power))
    cue(Payloads.Cue.IMPACT,eh.getLocation());
   if(kind.equals("fire"))t.igniteForSeconds(3);discard();
  }else if(hit.getType()==HitResult.Type.BLOCK){cue(Payloads.Cue.WALL,hit.getLocation());Abilities.particles((ServerLevel)level(),hit.getLocation(),ParticleTypes.CRIT,5);discard();}
  else {setPos(position().add(delta));Abilities.particles((ServerLevel)level(),position(),kind.equals("electric")?ParticleTypes.ELECTRIC_SPARK:kind.equals("fire")?ParticleTypes.FLAME:kind.equals("magic")?ParticleTypes.WITCH:ParticleTypes.CRIT,2);}
 }
 private void cue(Payloads.Cue type,Vec3 point){
  if(!skill().isEmpty()&&level() instanceof ServerLevel server&&getOwner() instanceof HumNpc npc)
   Payloads.cue(server,npc.getUUID(),skill(),cast,type,point,point,.75f,-1,null);
 }
 @Override protected void addAdditionalSaveData(CompoundTag t){
  super.addAdditionalSaveData(t);t.putString("Kind",kind);t.putFloat("Power",power);t.putInt("Age",age);t.putInt("Bounces",bounces);t.putDouble("Traveled",traveled);t.putString("Skill",skill());t.putDouble("Range",range);
  var bounced=new net.minecraft.nbt.ListTag();for(var id:reflectedBy)bounced.add(net.minecraft.nbt.NbtUtils.createUUID(id));t.put("ReflectedBy",bounced);
  t.putLong("Cast",cast);
 }
 @Override protected void readAdditionalSaveData(CompoundTag t){
  super.readAdditionalSaveData(t);kind=t.getString("Kind");power=t.getFloat("Power");age=t.getInt("Age");bounces=t.getInt("Bounces");traveled=t.getDouble("Traveled");
  skill(t.getString("Skill"),t.getDouble("Range"));
  cast=t.getLong("Cast");
  reflectedBy.clear();for(var id:t.getList("ReflectedBy",net.minecraft.nbt.Tag.TAG_INT_ARRAY))reflectedBy.add(net.minecraft.nbt.NbtUtils.loadUUID(id));
 }
}
