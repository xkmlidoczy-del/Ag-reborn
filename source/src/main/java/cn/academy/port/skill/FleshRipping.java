/* AcademyCraft1.0.7 FRContext adaptation. GPLv3; see NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.*;
import cn.academy.port.core.AbilityProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import static cn.academy.port.skill.TeleporterProgressionRules.*;
/** Cached server tick target, no wall penetration and no client target/damage/duration packet. */
public final class FleshRipping {
 public static final String ID=FLESH;private FleshRipping(){}
 public static boolean start(ServerPlayer p){if(cn.academy.port.AbilityConsumption.busy(p))return false;return start(p,0);}public static boolean start(ServerPlayer p,long nonce){if(cn.academy.port.AbilityConsumption.busy(p))return false;if(!TeleporterProgressionSupport.ready(p)||!TeleporterProgressionSupport.loaded(p,p.getEyePosition(),p.getEyePosition().add(TeleporterProgressionSupport.direction(p).scale(fleshRange(cn.academy.port.AbilityStorage.get(p).exp(ID))))))return false;var h=TeleporterProgressionSupport.start(p,ID,nonce);if(h==null)return false;h.target=target(p,h.exp);return true;}
 public static HitResult target(Entity p,double exp){return ClassicRaytrace.perform(p,p.getEyePosition(),p.getEyePosition().add(TeleporterProgressionSupport.direction(p).scale(fleshRange(exp))),ClipContext.Fluid.NONE,e->e instanceof LivingEntity||e instanceof EnderDragonPart);}
 public static void tick(ServerPlayer p){var h=TeleporterProgressionSupport.tick(p,ID);if(h==null)return;if(!p.getAbilities().instabuild&&h.state.cp<fleshConsumption(h.exp)){TeleporterProgressionSupport.abort(p,ID,h.nonce);return;}Vec3 eye=p.getEyePosition(),end=eye.add(TeleporterProgressionSupport.direction(p).scale(fleshRange(h.exp)));if(!TeleporterProgressionSupport.loaded(p,eye,end)){TeleporterProgressionSupport.abort(p,ID,h.nonce);return;}h.target=target(p,h.exp);}
 public static boolean release(ServerPlayer p){return release(p,0);}public static boolean release(ServerPlayer p,long nonce){var h=TeleporterProgressionSupport.terminal(p,ID,nonce);if(h==null)return false;TeleporterProgressionSupport.COMMITTING.add(p.getUUID());try{var entity=h.target instanceof EntityHitResult e?e.getEntity():null;if(entity==null||entity.isRemoved()||entity.level()!=p.level()){TeleporterProgressionSupport.send(p,ID,h,"end",null,false,-1);return false;}if(!consumeWithForce(h.state,ID,fleshConsumption(h.exp),fleshOverload(h.exp),p.getAbilities().instabuild))return false;double damage=fleshDamage(h.exp);int tier=ThreateningTeleport.criticalTier(h.state,()->p.serverLevel().random.nextFloat());if(tier>=0){damage=cn.academy.port.core.ClassicPassiveSkills.criticalDamage(damage,tier);cn.academy.port.achievements.ClassicAchievements.trigger(p,"teleporter.critical_attack");ThreateningTeleport.recordCritical(h.state,tier);p.getPersistentData().putBoolean("ac_teleporter_critical_attack",true);p.sendSystemMessage(Component.translatable("ac.ability.teleporter.crithit",Float.valueOf((float)ThreateningTeleport.criticalRate(tier))));NeoForge.EVENT_BUS.post(new CriticalHitEvent(p,entity,tier));}TeleporterProgressionSupport.send(p,ID,h,"end",entity.position(),true,tier);AbilityDamage.attack(p,"teleporter."+ID,entity,damage,true);if(p.serverLevel().random.nextDouble()<NAUSEA_PROBABILITY)p.addEffect(new MobEffectInstance(MobEffects.CONFUSION,NAUSEA_TICKS));h.state.setCooldown(ID,fleshCooldown(h.exp));h.state.addExperience(ID,FLESH_EXP);TeleporterProgressionSupport.save(p);return true;}finally{TeleporterProgressionSupport.COMMITTING.remove(p.getUUID());}}
 /** Common-side counterpart of source TPSkillHelper.TPCritHitEvent. */
 public static final class CriticalHitEvent extends Event{public final ServerPlayer player;public final Entity target;public final int tier;public CriticalHitEvent(ServerPlayer p,Entity target,int tier){player=p;this.target=target;this.tier=tier;}}
 public static void abort(ServerPlayer p){abort(p,0);}public static boolean abort(ServerPlayer p,long nonce){return TeleporterProgressionSupport.abort(p,ID,nonce);}public static void remove(ServerPlayer p){TeleporterProgressionSupport.remove(p,ID);}public static boolean active(ServerPlayer p){return TeleporterProgressionSupport.active(p,ID);}public static boolean canUse(AbilityProgress s){return TeleporterProgressionSupport.usable(s,ID);}public static void clear(){TeleporterProgressionSupport.clear(ID);}
}
