package cn.academy.biohum;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import java.util.*;
public final class FlameFamiliar extends PathfinderMob {
 public UUID creator;public final List<UUID> anchors=new ArrayList<>();
 public FlameFamiliar(EntityType<? extends PathfinderMob> t,Level l){super(t,l);setPersistenceRequired();}
 public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.ATTACK_DAMAGE,7).add(Attributes.MOVEMENT_SPEED,.3).add(Attributes.FOLLOW_RANGE,24);}
 @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(1,new MeleeAttackGoal(this,1,true));}
 public boolean anchorsValid(){if(!(level() instanceof ServerLevel s)||anchors.size()<3)return false;
  int alive=0;
  for(UUID id:anchors){
   if(s.getEntity(id) instanceof RuneAnchor r&&r.isAlive()&&Objects.equals(r.creator,creator)
        &&r.distanceToSqr(this)<=48*48&&++alive>=3)return true;
  }
  return false;}
 @Override public void tick(){super.tick();if(level() instanceof ServerLevel s){if(tickCount>5&&!anchorsValid()){discard();return;}if(creator!=null&&s.getEntity(creator) instanceof HumNpc a){if(!a.isAlive()){discard();return;}setTarget(a.validTarget(a.getTarget())?a.getTarget():null);}if(tickCount%3==0)Abilities.particles(s,position().add(0,1,0),ParticleTypes.SMALL_FLAME,2);}}
 @Override public boolean hurt(DamageSource src,float amount){if(src.getEntity() instanceof HumNpc a&&a.characterId.equals("touma")&&distanceTo(a)<3){discard();return true;}if(anchorsValid())return false;return super.hurt(src,amount);}
 @Override public boolean doHurtTarget(Entity e){boolean b=super.doHurtTarget(e);if(b)e.igniteForSeconds(4);return b;}
 @Override public boolean isAlliedTo(Entity e){if(creator!=null&&e.getUUID().equals(creator))return true;if(level() instanceof ServerLevel s&&creator!=null&&s.getEntity(creator) instanceof HumNpc a)return a.ally(e);return super.isAlliedTo(e);}
 @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);if(creator!=null)t.putUUID("Creator",creator);t.putInt("AnchorCount",anchors.size());for(int i=0;i<anchors.size();i++)t.putUUID("Anchor"+i,anchors.get(i));}
 @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);creator=t.hasUUID("Creator")?t.getUUID("Creator"):null;anchors.clear();for(int i=0;i<Math.min(64,t.getInt("AnchorCount"));i++)if(t.hasUUID("Anchor"+i))anchors.add(t.getUUID("Anchor"+i));}
}
