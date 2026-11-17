package cn.academy.biohum;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.particles.ParticleTypes;
import java.util.UUID;
public final class RoseTrap extends Entity {
 public UUID creator;public int age;
 public RoseTrap(EntityType<? extends RoseTrap> t,Level l){super(t,l);}
 @Override protected void defineSynchedData(SynchedEntityData.Builder b){}
 @Override public void tick(){super.tick();if(!(level() instanceof ServerLevel s))return;if(++age>200){discard();return;}if(creator==null||!(s.getEntity(creator) instanceof HumNpc a)||!a.isAlive()){discard();return;}
  for(var t:s.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(.5,1,.5),a::validTarget)){t.hurt(damageSources().indirectMagic(this,a),6);t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,60,1));Abilities.particles(s,t.position().add(0,.5,0),ParticleTypes.WITCH,22);discard();return;}
  if(age%5==0)Abilities.particles(s,position().add(0,.15,0),ParticleTypes.WITCH,3);
 }
 @Override protected void addAdditionalSaveData(CompoundTag t){if(creator!=null)t.putUUID("Creator",creator);t.putInt("Age",age);}
 @Override protected void readAdditionalSaveData(CompoundTag t){creator=t.hasUUID("Creator")?t.getUUID("Creator"):null;age=t.getInt("Age");}
}
