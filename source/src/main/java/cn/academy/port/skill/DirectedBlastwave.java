/* AcademyCraft1.0.7 DirectedBlastwave/BlastwaveContext adaptation, GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;
import cn.academy.port.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import java.util.*;
/** Server chooses duration, trace and random block action; nonce and commit fence close replay/reentry. */
public final class DirectedBlastwave {
 public static final String ID=DirectedBlastwaveSession.ID;public static final double RANGE = SkillTuning.number("dir_blast", "reach"),RADIUS = SkillTuning.number("dir_blast", "radius");
 private static final Map<UUID,Hold> HOLDS=new HashMap<>();private static final Map<UUID,Long> INPUTS=new HashMap<>();private static final Set<UUID> COMMITTING=new HashSet<>();private static long token;
 private DirectedBlastwave(){}
 public static boolean start(ServerPlayer p){if(cn.academy.port.AbilityConsumption.busy(p))return false;return start(p,0);}public static boolean start(ServerPlayer p,long input){if(cn.academy.port.AbilityConsumption.busy(p))return false;if(!VectorCombatSupport.ready(p)||input<0||input>0&&input<=INPUTS.getOrDefault(p.getUUID(),0L)||COMMITTING.contains(p.getUUID()))return false;var old=HOLDS.get(p.getUUID());if(old!=null){if(valid(p,old))return false;remove(p);}var session=DirectedBlastwaveSession.begin(AbilityStorage.get(p));if(session==null)return false;var h=new Hold(p,session,++token,input);HOLDS.put(p.getUUID(),h);if(input>0)INPUTS.put(p.getUUID(),input);var t=VectorCombatSupport.packet(p,"dir_blast_prepare",h.token,h.input);t.putFloat("cp",session.cp());VectorCombatSupport.send(h.audience,t);return true;}
 private static boolean valid(ServerPlayer p,Hold h){var s=AbilityStorage.get(p);return VectorCombatSupport.ready(p)&&h.level==p.serverLevel()&&h.session.state()==s&&VectorCombatSupport.state(s,ID,3)&&h.session.active()&&DirectedBlastwaveSession.holds(p.serverLevel().getGameTime()-h.started);}
 public static void tick(ServerPlayer p){if(!VectorCombatSupport.thread(p))return;var h=HOLDS.get(p.getUUID());if(h!=null&&!valid(p,h))abort(p);}
 public static boolean release(ServerPlayer p){return release(p,0);}public static boolean release(ServerPlayer p,long input){if(!VectorCombatSupport.thread(p)||COMMITTING.contains(p.getUUID()))return false;var h=HOLDS.get(p.getUUID());if(h==null||input>0&&h.input!=input)return false;if(!valid(p,h)){abort(p);return false;}HOLDS.remove(p.getUUID(),h);COMMITTING.add(p.getUUID());try{if(!h.session.release(p.serverLevel().getGameTime()-h.started,p.getAbilities().instabuild)){send(p,h,"dir_blast_abort");return false;}
  Vec3 origin=p.getEyePosition(),end=origin.add(ClassicRaytrace.direction(p).scale(RANGE));var hit=ClassicRaytrace.perform(p,origin,end,ClipContext.Fluid.NONE,e->e instanceof LivingEntity||e instanceof EnderDragonPart);Vec3 center=hit instanceof EntityHitResult eh?eh.getEntity().getEyePosition():hit.getType()==HitResult.Type.MISS?p.position().add(ClassicRaytrace.direction(p).scale(RANGE)):hit.getLocation();var packet=VectorCombatSupport.packet(p,"dir_blast_perform",h.token,h.input);VectorCombatSupport.position(packet,center);VectorCombatSupport.send(h.audience,packet);
  var entities=p.serverLevel().getEntities(p,new AABB(center.subtract(RADIUS,RADIUS,RADIUS),center.add(RADIUS,RADIUS,RADIUS)),e->e.position().distanceToSqr(center)<=RADIUS*RADIUS);boolean effective=!entities.isEmpty();for(var e:entities){if(VectorCombatSupport.protectedPlayer(p,e))continue;AbilityDamage.attack(p,"vecmanip."+ID,e,h.session.damage());knockback(p,e);}
  breakBlocks(p,center,h.session);h.session.award(effective);cn.academy.port.achievements.ClassicAchievements.trigger(p,"vecmanip.dir_blast");return true;
 }finally{COMMITTING.remove(p.getUUID());VectorCombatSupport.sync(p);}}
 static Vec3 strongImpulse(Vec3 casterHead,Vec3 targetHead){var delta=casterHead.subtract(targetHead).normalize();return new Vec3(delta.x,-.4F,delta.z).normalize().scale(-1.2F);}
 static Vec3 extraImpulse(Vec3 casterFeet,Vec3 targetFeet){return targetFeet.subtract(casterFeet).normalize().scale(.24);}
 private static void knockback(ServerPlayer p,Entity e){var v=strongImpulse(p.getEyePosition(),e.getEyePosition());e.setPos(e.getX(),e.getY()+.1,e.getZ());e.setDeltaMovement(v.add(extraImpulse(p.position(),e.position())));e.hasImpulse=true;e.hurtMarked=true;}
 private static void breakBlocks(ServerPlayer p,Vec3 center,DirectedBlastwaveSession s){int x=VectorCombatRules.blastCenter(center.x),y=VectorCombatRules.blastCenter(center.y),z=VectorCombatRules.blastCenter(center.z);var world=p.serverLevel();for(int i=x-3;i<x+3;i++)for(int j=y-3;j<y+3;j++)for(int k=z-3;k<z+3;k++){int dx=i-x,dy=j-y,dz=k-z,dist=dx*dx+dy*dy+dz*dz;if(dist>6||dist!=0&&world.random.nextFloat()>=s.breakProbability())continue;var pos=new BlockPos(i,j,k);if(!VectorCombatSupport.loaded(p,pos))continue;var state=world.getBlockState(pos);float hardness=state.getDestroySpeed(world,pos);if(hardness<0||hardness>s.hardness()||!VectorCombatSupport.canBreak(p,ID,pos))continue;boolean drop=world.random.nextFloat()<s.dropRate();if(state.isAir())continue;if(drop)Block.dropResources(state,world,pos,world.getBlockEntity(pos));world.setBlock(pos,Blocks.AIR.defaultBlockState(),3);/* Original intentionally omits block sound because it obscures skill sound. */}}
public static void abort(ServerPlayer p){abort(p,0);}public static boolean abort(ServerPlayer p,long input){if(!VectorCombatSupport.thread(p)||COMMITTING.contains(p.getUUID()))return false;var h=HOLDS.get(p.getUUID());if(h==null||input>0&&h.input!=input)return false;HOLDS.remove(p.getUUID(),h);h.session.discard();send(p,h,"dir_blast_abort");return true;}
 public static void remove(ServerPlayer p){if(!VectorCombatSupport.thread(p))return;abort(p);INPUTS.remove(p.getUUID());}public static boolean active(ServerPlayer p){return p!=null&&HOLDS.containsKey(p.getUUID());}public static long input(ServerPlayer p){var h=p==null?null:HOLDS.get(p.getUUID());return h==null?0:h.input;}public static void clear(){HOLDS.clear();INPUTS.clear();COMMITTING.clear();token=0;}private static void send(ServerPlayer p,Hold h,String kind){VectorCombatSupport.send(h.audience,VectorCombatSupport.packet(p,kind,h.token,h.input));}
 private static final class Hold{final ServerLevel level;final DirectedBlastwaveSession session;final long token,input,started;final Set<ServerPlayer> audience;Hold(ServerPlayer p,DirectedBlastwaveSession s,long t,long i){level=p.serverLevel();session=s;token=t;input=i;started=level.getGameTime();audience=VectorCombatSupport.audience(p);}}
}
