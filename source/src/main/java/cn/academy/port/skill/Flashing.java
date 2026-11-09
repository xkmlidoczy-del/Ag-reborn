/* Copyright (c) Lambda Innovation,2013-2016. AcademyCraft classic1.0.7 adaptation.
 * Licensed under GPLv3; see NOTICE and upstream source fixture. */
package cn.academy.port.skill;
import cn.academy.port.core.AbilityProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import static cn.academy.port.skill.TeleporterFinalRules.*;
/** Toggle mode, release-driven directional hops, captured mastery and original termination cooldown. */
public final class Flashing {
 public static final String ID=FLASH;private Flashing(){}
 public static boolean start(ServerPlayer p){if(cn.academy.port.AbilityConsumption.busy(p))return false;return start(p,0);}public static boolean start(ServerPlayer p,long nonce){if(cn.academy.port.AbilityConsumption.busy(p))return false;var h=TeleporterFinalSupport.start(p,ID,nonce);if(h==null)return false;if(!h.state.consumeSkill(ID,flashStartConsumption(h.exp),flashStartOverload(h.exp),p.getAbilities().instabuild,()->TeleporterFinalSupport.current(p,ID)==h)){TeleporterFinalSupport.dispose(p,ID);return false;}h.overloadKeep=h.state.overload;TeleporterFinalSupport.send(p,ID,h,"start",null,true);TeleporterFinalSupport.save(p);return true;}
 public static Vec3 destination(Entity p,double exp,int key){Vec3 dir=TeleporterFinalSupport.vec(flashDirection(key,p.getYRot(),p.getXRot()));Vec3 eye=p.getEyePosition(),end=eye.add(dir.scale(flashRange(exp)));var hit=ClassicRaytrace.perform(p,eye,end,ClipContext.Fluid.NONE,e->e instanceof LivingEntity||e instanceof EnderDragonPart);if(hit instanceof BlockHitResult b&&hit.getType()==HitResult.Type.BLOCK)return TeleporterFinalSupport.vec(flashBlock(TeleporterFinalSupport.point(hit.getLocation()),b.getDirection().get3DDataValue(),b.getBlockPos().getY(),(x,y,z)->p.level().isEmptyBlock(new BlockPos(x,y,z))));if(hit instanceof EntityHitResult e)return hit.getLocation().add(0,e.getEntity().getEyeHeight(),0);return end;}
 public static void tick(ServerPlayer p){var h=TeleporterFinalSupport.tick(p,ID);if(h==null)return;if(h.state.overload<h.overloadKeep)h.state.overload=h.overloadKeep;if(flashExpired(h.ticks,h.exp)){TeleporterFinalSupport.dispose(p,ID);return;}h.ticks++;}
 public static boolean perform(ServerPlayer p,long nonce,int key){var h=TeleporterFinalSupport.current(p,ID);return h!=null&&perform(p,nonce,h.lastPerform+1,key);}
 private static boolean perform(ServerPlayer p,long nonce,long sequence,int key){var h=TeleporterFinalSupport.current(p,ID);if(h==null||!TeleporterFinalSupport.server(p)||nonce>0&&nonce!=h.nonce||key<1||key>4||TeleporterFinalSupport.COMMITTING.contains(p.getUUID()))return false;if(!TeleporterFinalSupport.valid(p,h,ID)){TeleporterFinalSupport.dispose(p,ID);return false;}if(sequence!=h.lastPerform+1||sequence<1||sequence>4096)return false;h.lastPerform=sequence;Vec3 dir=TeleporterFinalSupport.vec(flashDirection(key,p.getYRot(),p.getXRot())),eye=p.getEyePosition();if(!TeleporterFinalSupport.loaded(p,eye,eye.add(dir.scale(flashRange(h.exp)))))return false;Vec3 at=destination(p,h.exp,key);if(!TeleporterFinalSupport.destinationSafe(p,at))return false;TeleporterFinalSupport.COMMITTING.add(p.getUUID());try{if(!h.state.consumeSkill(ID,flashConsumption(h.exp),0,p.getAbilities().instabuild,()->TeleporterFinalSupport.current(p,ID)==h&&TeleporterFinalSupport.destinationSafe(p,at)))return false;p.stopRiding();p.teleportTo(at.x,at.y,at.z);p.fallDistance=0;h.state.addExperience(ID,FLASH_EXP);cn.academy.port.achievements.ClassicAchievements.trigger(p,"teleporter.flashing");p.getPersistentData().putBoolean("ac_teleporter_flashing",true);TeleporterFinalSupport.count(p,false);TeleporterFinalSupport.send(p,ID,h,"perform",at,true);TeleporterFinalSupport.save(p);return true;}finally{TeleporterFinalSupport.COMMITTING.remove(p.getUUID());}}
 public static boolean request(ServerPlayer p,String value){String[] f=value.split(":",-1);if(f.length!=3)return false;long n=TeleporterFinalSupport.nonce(f[0]),sequence=TeleporterFinalSupport.nonce(f[1]);return n>0&&sequence>0&&f[2].length()==1&&f[2].charAt(0)>='1'&&f[2].charAt(0)<='4'&&perform(p,n,sequence,f[2].charAt(0)-'0');}
 /** Default bound-key release does not terminate this mode. */
 public static void release(ServerPlayer p){}public static boolean release(ServerPlayer p,long nonce){var h=TeleporterFinalSupport.current(p,ID);return h!=null&&(nonce==0||nonce==h.nonce);}
 public static void abort(ServerPlayer p){abort(p,0);}public static boolean abort(ServerPlayer p,long nonce){return TeleporterFinalSupport.abort(p,ID,nonce);}public static void remove(ServerPlayer p){TeleporterFinalSupport.remove(p,ID);}public static boolean active(ServerPlayer p){return TeleporterFinalSupport.active(p,ID);}public static int ticks(ServerPlayer p){var h=TeleporterFinalSupport.current(p,ID);return h==null?0:h.ticks;}public static boolean canUse(AbilityProgress s){return TeleporterFinalSupport.usable(s,ID);}public static void clear(){TeleporterFinalSupport.clear(ID);}
 /** Modern pending-start cancellation uses this mode's matching Hold and server-derived token. */
 public static boolean abortPendingContext(ServerPlayer p,long input){
  if(!TeleporterFinalSupport.server(p)||input<=0)return false;
  var h=TeleporterFinalSupport.current(p,ID);
  return h!=null&&h.nonce==input&&h.token>0&&abortContext(p,input,h.token);
 }
 /** Retained mode directions use accepted identities even after default preset unmapping. */
 public static boolean directionOwned(ServerPlayer p,long input,long token,long sequence,int key){
  if(!TeleporterFinalSupport.server(p)||p.hasDisconnected()||!p.isAlive()||p.isRemoved()||p.isSpectator()
    ||cn.academy.port.AbilityConsumption.busy(p)||TeleporterFinalSupport.COMMITTING.contains(p.getUUID())
    ||p.serverLevel().getEntity(p.getId())!=p||sequence<1||sequence>4096||key<1||key>4)return false;
  var h=TeleporterFinalSupport.current(p,ID);
  if(h==null||!cn.academy.port.core.TargetedContextTermination.matches(
    new cn.academy.port.core.TargetedContextTermination.Binding(h.owner,h.world,h.state,h.nonce,h.token),
    p,p.serverLevel(),cn.academy.port.AbilityStorage.get(p),input,token)||!TeleporterFinalSupport.valid(p,h,ID)
    ||!Double.isFinite(h.exp)||h.exp<0||h.exp>1||!Double.isFinite(h.overloadKeep))return false;
  return perform(p,input,sequence,key);
 }
 /** Strict accepted mode terminal, guarded against owner replacement and hop consumption reentry. */
 public static boolean abortContext(ServerPlayer p,long input,long token){
  if(!TeleporterFinalSupport.server(p)||p.hasDisconnected()||!p.isAlive()||p.isRemoved()||p.isSpectator()
    ||cn.academy.port.AbilityConsumption.busy(p)||TeleporterFinalSupport.COMMITTING.contains(p.getUUID())
    ||p.serverLevel().getEntity(p.getId())!=p)return false;
  var h=TeleporterFinalSupport.current(p,ID);
  if(h==null||!cn.academy.port.core.TargetedContextTermination.matches(
    new cn.academy.port.core.TargetedContextTermination.Binding(h.owner,h.world,h.state,h.nonce,h.token),
    p,p.serverLevel(),cn.academy.port.AbilityStorage.get(p),input,token)||!TeleporterFinalSupport.valid(p,h,ID)
    ||!Double.isFinite(h.exp)||h.exp<0||h.exp>1||!Double.isFinite(h.overloadKeep))return false;
  TeleporterFinalSupport.dispose(p,ID);return true;
 }
}
