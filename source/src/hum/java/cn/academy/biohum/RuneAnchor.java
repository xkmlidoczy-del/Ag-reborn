package cn.academy.biohum;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;
public final class RuneAnchor extends PathfinderMob {
 public UUID creator;public long expires;
 public RuneAnchor(EntityType<? extends PathfinderMob> t,Level l){super(t,l);setNoAi(true);setPersistenceRequired();}
 public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,2).add(Attributes.MOVEMENT_SPEED,0);}
 @Override public void tick(){super.tick();if(!level().isClientSide&&expires>0&&level().getGameTime()>=expires){extinguish();discard();}}
 @Override public void die(net.minecraft.world.damagesource.DamageSource source){
  if(isDeadOrDying()&&!isRemoved())extinguish();
  super.die(source);
 }
 private void extinguish(){
  if(level() instanceof net.minecraft.server.level.ServerLevel server&&creator!=null){
   var at=position().add(0,.09,0);
   Payloads.cue(server,creator,"rune_summon",expires-600,Payloads.Cue.CANCEL,at,at,.61f,0,getUUID());
  }
 }
 @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);if(creator!=null)t.putUUID("Creator",creator);t.putLong("Expires",expires);}
 @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);creator=t.hasUUID("Creator")?t.getUUID("Creator"):null;expires=t.getLong("Expires");}
}
