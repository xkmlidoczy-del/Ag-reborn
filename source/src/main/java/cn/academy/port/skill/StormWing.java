/* AcademyCraft1.0.7 StormWing/StormWingContext adaptation, GPLv3; see NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;
/** Charged toggle. Direction wire carries only a bounded W/S/A/D index and owner-context nonce. */
public final class StormWing {
 public static final String ID=StormWingSession.ID;private static final Map<UUID,Hold> HOLDS=new HashMap<>();private static final Map<UUID,Long> INPUTS=new HashMap<>();private static final Set<UUID> TICKING=new HashSet<>();private static long token;
 private StormWing(){}public static boolean start(ServerPlayer p){if(cn.academy.port.AbilityConsumption.busy(p))return false;return start(p,0);}public static boolean start(ServerPlayer p,long input){if(cn.academy.port.AbilityConsumption.busy(p))return false;if(!VectorCombatSupport.ready(p)||input<0||input>0&&input<=INPUTS.getOrDefault(p.getUUID(),0L)||TICKING.contains(p.getUUID()))return false;var old=HOLDS.get(p.getUUID());if(old!=null){if(input>0)INPUTS.put(p.getUUID(),input);finish(p,old,true);return true;}var session=StormWingSession.begin(AbilityStorage.get(p));if(session==null)return false;var h=new Hold(p,session,++token,input);HOLDS.put(p.getUUID(),h);if(input>0)INPUTS.put(p.getUUID(),input);p.getAbilities().mayfly=true;p.onUpdateAbilities();var tag=packet(p,h,"storm_wing_start");tag.putFloat("mastery",(float)session.state().exp(ID));tag.putFloat("charge",session.charge());tag.putFloat("cp",session.cp());VectorCombatSupport.send(h.audience,tag);return true;}
 private static boolean valid(ServerPlayer p,Hold h){var s=AbilityStorage.get(p);return VectorCombatSupport.ready(p)&&p.serverLevel()==h.level&&h.session.state()==s&&VectorCombatSupport.state(s,ID,3)&&h.session.active()&&p.serverLevel().getGameTime()>=h.lastTick;}
 public static boolean start(ServerPlayer p,long input,int slot){if(cn.academy.port.AbilityConsumption.busy(p))return false;if(slot<0||slot>3)return false;boolean accepted=start(p,input);var h=p==null?null:HOLDS.get(p.getUUID());if(accepted&&h!=null&&h.input==input)h.slot=slot;return accepted;}
 public static boolean direction(ServerPlayer p,int slot,long input,int direction){var h=p==null?null:HOLDS.get(p.getUUID());return h!=null&&h.slot==slot&&direction(p,input,direction);}
 public static boolean direction(ServerPlayer p,long input,int direction){if(!VectorCombatSupport.thread(p)||direction< -1||direction>3||input<=0||TICKING.contains(p.getUUID()))return false;var h=HOLDS.get(p.getUUID());if(h==null||h.input!=input||!valid(p,h)||h.session.phase()!=StormWingSession.ACTIVE)return false;h.direction=direction;var tag=packet(p,h,"storm_wing_state");tag.putBoolean("applying",direction!=-1);VectorCombatSupport.send(h.audience,tag);return true;}
 /** Source worldSpace rotation uses head yaw and pitch for all four movement keys. */
 public static Vec3 worldSpace(float yaw,float pitch,int direction){double x=direction==2?1:direction==3?-1:0,z=direction==0?1:direction==1?-1:0;float yawRadians=yaw*(float)Math.PI/180F,pitchRadians=pitch*(float)Math.PI/180F;return new Vec3(x,0,z).xRot(-pitchRadians).yRot(-yawRadians);}
 public static Vec3 movement(Vec3 current,Vec3 direction,double speed,boolean ground){if(direction==null)return new Vec3(current.x,ground?.1:current.y+.078,current.z);var expected=direction.scale(speed);return new Vec3(VectorCombatRules.approach(current.x,expected.x),VectorCombatRules.approach(current.y,expected.y),VectorCombatRules.approach(current.z,expected.z));}
 public static void tick(ServerPlayer p){if(!VectorCombatSupport.thread(p)||TICKING.contains(p.getUUID()))return;var h=HOLDS.get(p.getUUID());if(h==null)return;if(!valid(p,h)){if(h.level==p.serverLevel()&&h.session.state()==AbilityStorage.get(p)&&p.isAlive()&&!p.isRemoved())finish(p,h,true);else remove(p);return;}long now=p.serverLevel().getGameTime();if(now<=h.lastTick)return;h.lastTick=now;TICKING.add(p.getUUID());try{
  Vec3 from=p.position();var ground=p.serverLevel().clip(new ClipContext(from.add(0,.5,0),from.add(0,-.3,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()!=HitResult.Type.MISS;Vec3 dir=h.direction<0?null:worldSpace(p.getYHeadRot(),p.getXRot(),h.direction);h.session.hovering(dir==null);Vec3 velocity=movement(p.getDeltaMovement(),dir,h.session.flightSpeed(),ground);
  if(!VectorCombatSupport.finite(velocity)||!VectorCombatSupport.loaded(p,BlockPos.containing(from.add(velocity)))){finish(p,h,true);return;}
  if(dir!=null)p.stopRiding();p.setDeltaMovement(velocity);p.fallDistance=0;
  // Native collision resolver replaces classic client travel; authoritative relocation prevents forged endpoints.
  p.move(MoverType.SELF,velocity);Vec3 at=p.position();p.teleportTo(at.x,at.y,at.z);p.fallDistance=0;p.hasImpulse=true;p.hurtMarked=true;
  // Vanilla 1.7 air gravity/drag were applied after the local velocity edit. Block friction is modern native behavior.
  Vec3 after=p.getDeltaMovement();if(!p.isNoGravity())after=new Vec3(after.x*.91,(after.y-.08)*.98,after.z*.91);p.setDeltaMovement(after);
  softBlocks(p,h.session);if(h.session.phase()==StormWingSession.ACTIVE)cn.academy.port.achievements.ClassicAchievements.trigger(p,"vecmanip.storm_wing");int previous=h.session.phase();if(!h.session.tick(p.getAbilities().instabuild)){finish(p,h,true);return;}if(previous!=h.session.phase()){if(h.session.masteryBurst())burst(p);VectorCombatSupport.send(h.audience,packet(p,h,"storm_wing_state"));}
 }finally{TICKING.remove(p.getUUID());}}
 private static void softBlocks(ServerPlayer p,StormWingSession s){if(!s.softBreak())return;var w=p.serverLevel();for(int i=0;i<40;i++){var pos=new BlockPos(VectorCombatRules.stormProbe(p.getX(),-10+w.random.nextDouble()*20),VectorCombatRules.stormProbe(p.getY(),-10+w.random.nextDouble()*20),VectorCombatRules.stormProbe(p.getZ(),-10+w.random.nextDouble()*20));if(!VectorCombatSupport.loaded(p,pos))continue;var block=w.getBlockState(pos);float hardness=block.getDestroySpeed(w,pos);if(block.isAir()||hardness<0||hardness>.3F||!VectorCombatSupport.canBreak(p,ID,pos))continue;var sound=block.getSoundType(w,pos,p);w.setBlock(pos,Blocks.AIR.defaultBlockState(),3);w.playSound(null,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,sound.getBreakSound(),SoundSource.BLOCKS,.5F,1F);}}
 private static void burst(ServerPlayer p){Vec3 at=p.position();var w=p.serverLevel();for(var e:w.getEntities((net.minecraft.world.entity.Entity)null,new AABB(at.subtract(6,6,6),at.add(6,6,6)),e->e.position().distanceToSqr(at)<=36)){if(e!=p&&VectorCombatSupport.protectedPlayer(p,e))continue;Vec3 delta=e.getEyePosition().subtract(at);Vec3 v=new Vec3(delta.x*(.9+w.random.nextDouble()*.3),delta.y*(.9+w.random.nextDouble()*.3),delta.z*(.9+w.random.nextDouble()*.3)).normalize().scale(.5+w.random.nextDouble()*.5);e.setDeltaMovement(v);e.hasImpulse=true;e.hurtMarked=true;}}
 /** Toggle's key-up is empty in classic; it does not terminate the flight context. */
 public static boolean release(ServerPlayer p){return release(p,0);}public static boolean release(ServerPlayer p,long input){if(!VectorCombatSupport.thread(p))return false;var h=HOLDS.get(p.getUUID());return h!=null&&(input==0||input==h.input);}
 public static void abort(ServerPlayer p){abort(p,0);}public static boolean abort(ServerPlayer p,long input){if(!VectorCombatSupport.thread(p)||TICKING.contains(p.getUUID()))return false;var h=HOLDS.get(p.getUUID());if(h==null||input>0&&h.input!=input)return false;finish(p,h,true);return true;}
 /** Lifecycle disposal restores owned flight permission, without mutating replacement category cooldowns. */
 public static void remove(ServerPlayer p){if(!VectorCombatSupport.thread(p))return;var h=HOLDS.get(p.getUUID());if(h!=null)finish(p,h,false);INPUTS.remove(p.getUUID());}
 private static void finish(ServerPlayer p,Hold h,boolean gameplay){if(!HOLDS.remove(p.getUUID(),h))return;h.session.terminate(gameplay);if(gameplay&&p.isAlive()&&!p.onGround())p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING,80,0,false,false,true)); /* glide down instead of dropping */p.getAbilities().mayfly=h.previousMayfly;p.onUpdateAbilities();p.fallDistance=0;VectorCombatSupport.send(h.audience,packet(p,h,"storm_wing_end"));VectorCombatSupport.sync(p);}
 private static net.minecraft.nbt.CompoundTag packet(ServerPlayer p,Hold h,String kind){var t=VectorCombatSupport.packet(p,kind,h.token,h.input);t.putInt("state",h.session.phase());t.putInt("index",++h.sequence);t.putInt("ticks",h.session.ticks());t.putBoolean("applying",h.direction!=-1);return t;}
 public static boolean active(ServerPlayer p){return p!=null&&HOLDS.containsKey(p.getUUID());}public static int phase(ServerPlayer p){var h=p==null?null:HOLDS.get(p.getUUID());return h==null?-1:h.session.phase();}public static int ticks(ServerPlayer p){var h=p==null?null:HOLDS.get(p.getUUID());return h==null?0:h.session.ticks();}public static long input(ServerPlayer p){var h=p==null?null:HOLDS.get(p.getUUID());return h==null?0:h.input;}public static void clear(){HOLDS.clear();INPUTS.clear();TICKING.clear();token=0;}
 private static final class Hold{final ServerPlayer owner;final ServerLevel level;final StormWingSession session;final long token,input;final Set<ServerPlayer> audience;final boolean previousMayfly;long lastTick;int direction=-1,sequence,slot=-1;Hold(ServerPlayer p,StormWingSession s,long token,long input){owner=p;level=p.serverLevel();session=s;this.token=token;this.input=input;audience=VectorCombatSupport.audience(p);previousMayfly=p.getAbilities().mayfly;lastTick=level.getGameTime();}}
 /** Modern pending-start cancellation never selects a context by a current slot mapping. */
 public static boolean abortPendingContext(ServerPlayer p,long input){
  if(!VectorCombatSupport.thread(p)||input<=0)return false;
  var h=HOLDS.get(p.getUUID());
  return h!=null&&h.input==input&&h.token>0&&abortContext(p,input,h.token);
 }
 /** Strict accepted flight-context terminal; no slot/preset lookup or nonce/token wildcard. */
 public static boolean abortContext(ServerPlayer p,long input,long token){
  if(!VectorCombatSupport.thread(p)||p.hasDisconnected()||!p.isAlive()||p.isRemoved()||p.isSpectator()
    ||cn.academy.port.AbilityConsumption.busy(p)||TICKING.contains(p.getUUID())
    ||p.serverLevel().getEntity(p.getId())!=p)return false;
  var h=HOLDS.get(p.getUUID());
  if(h==null||!cn.academy.port.core.TargetedContextTermination.matches(
    new cn.academy.port.core.TargetedContextTermination.Binding(h.owner,h.level,h.session.state(),h.input,h.token),
    p,p.serverLevel(),AbilityStorage.get(p),input,token)||!valid(p,h)||h.session.state().level>5)return false;
  finish(p,h,true);return true;
 }
}
