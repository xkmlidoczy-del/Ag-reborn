/* AcademyCraft1.0.7 PlasmaCannonContext adaptation. GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;
import cn.academy.port.highpower.environment.SkillEnvironment;
import cn.academy.port.*;
import net.minecraft.server.level.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Explosion;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.event.EventHooks;
import net.minecraft.world.phys.*;
import java.util.*;
/** Server-owned charge and world trajectory. Destination is fixed from caster's current living trace on release. */
public final class PlasmaCannon {
 public static final String ID=PlasmaCannonSession.ID;private static final Map<UUID,Hold> HOLDS=new HashMap<>();private static final Map<UUID,Long> INPUTS=new HashMap<>();private static long nextToken;private PlasmaCannon(){}
 public static boolean start(ServerPlayer p){if(cn.academy.port.AbilityConsumption.busy(p))return false;return start(p,0);}public static boolean start(ServerPlayer p,long input){if(cn.academy.port.AbilityConsumption.busy(p))return false;if(!VectorStarterSupport.ready(p)||input<0||input>0&&input<=INPUTS.getOrDefault(p.getUUID(),0L))return false;var old=HOLDS.get(p.getUUID());if(old!=null){if(valid(old))return false;remove(p);}var s=PlasmaCannonSession.begin(AbilityStorage.get(p),p.getAbilities().instabuild);if(s==null)return false;var h=new Hold(p,s,++nextToken,input);HOLDS.put(p.getUUID(),h);if(input>0)INPUTS.put(p.getUUID(),input);send(h,"plasma_cannon_start");return true;}
 public static void tick(ServerPlayer p){if(!VectorStarterSupport.serverThread(p))return;var h=HOLDS.get(p.getUUID());if(h==null)return;if(!valid(h)||!h.session.go()&&(!h.session.state().activated||!h.session.state().overloadFine||h.session.state().interfering)){remove(p);return;}long now=p.serverLevel().getGameTime();if(now<=h.lastTick)return;h.lastTick=now;if(!h.session.tick(p.getAbilities().instabuild)){finish(h);return;}if(h.session.chargedSoundTick())send(h,"plasma_cannon_ready");if(h.session.go()){Vec3 before=h.at;double[] moved=PlasmaCannonSession.move(new double[]{h.at.x,h.at.y,h.at.z},new double[]{h.destination.x,h.destination.y,h.destination.z});h.at=new Vec3(moved[0],moved[1],moved[2]);if(!p.serverLevel().hasChunkAt(BlockPos.containing(h.at))){finish(h);return;}int explosions=h.session.explosions(VectorFinalSupport.collision(p,before,h.at),h.at.distanceTo(h.destination));if(explosions>0){var terrain=SkillEnvironment.begin(p,"vecmanip."+ID,5);for(int i=0;i<explosions;i++)explode(h,terrain);finish(h);return;}if(h.session.syncPosition())send(h,"plasma_cannon_position");}}
 public static boolean release(ServerPlayer p){return release(p,0);}public static boolean release(ServerPlayer p,long input){if(!VectorStarterSupport.serverThread(p))return false;var h=HOLDS.get(p.getUUID());if(h==null||input>0&&input!=h.input)return false;if(!valid(h)||!h.session.ready()){if(!h.session.go())finish(h);return false;}Vec3 destination=VectorFinalSupport.looking(p,SkillTuning.number("plasma_cannon", "reach"),true);if(!VectorStarterSupport.finite(destination)||!p.serverLevel().hasChunkAt(BlockPos.containing(destination))||!h.session.perform()){finish(h);return false;}h.destination=destination;send(h,"plasma_cannon_go");return true;}
 private static void explode(Hold h,SkillEnvironment.Cast terrain){var p=h.player;var w=p.serverLevel();Vec3 destination=h.destination;var s=h.session.state();float damage=PlasmaCannonSession.damage(s.exp(ID));double radius=SkillTuning.number(ID,"radius_novice")+(SkillTuning.number(ID,"radius_mastered")-SkillTuning.number(ID,"radius_novice"))*Math.clamp(s.exp(ID),0,1);
  cn.academy.port.macro.MacroVfx.emit(p,ID,cn.academy.port.macro.MacroVfx.Phase.HIT,destination,destination,70,radius);
  var victims=new ArrayList<>(w.getEntities((Entity)null,new AABB(destination,destination).inflate(radius),e->e.position().distanceToSqr(destination)<=radius*radius));
  cn.academy.port.macro.MacroSettlement.submit(w,victims,()->p.isAlive()&&!p.hasDisconnected()&&p.serverLevel()==w&&AbilityStorage.get(p)==s,e->{
   if(e.isRemoved()||e.level()!=w||!w.hasChunkAt(e.blockPosition())||e.position().distanceToSqr(destination)>radius*radius)return;
   VectorFinalSupport.attack(p,ID,e,damage);if(e instanceof LivingEntity living)living.invulnerableTime=-1;
  },()->{}); // Source resets AFTER each explicit attack, including rejected damage.
  // Keep the original native explosion phases and entity effects. The A2 cast owns all block writes.
  var explosion=new Explosion(w,p,null,null,destination.x,destination.y,destination.z,PlasmaCannonSession.explosionPower(s.exp(ID)),false,Explosion.BlockInteraction.DESTROY,ParticleTypes.EXPLOSION,ParticleTypes.EXPLOSION_EMITTER,SoundEvents.GENERIC_EXPLODE);
  if(EventHooks.onExplosionStart(w,explosion))return;
  if(AcademyConfig.contextTerrain(w,"vecmanip",ID)&&cn.academy.port.macro.MacroGeometry.sphereLoaded(w,destination,PlasmaCannonSession.explosionPower(s.exp(ID))*2+1)){
   explosion.explode();
   if(p.serverLevel()==w&&terrain.allowsBlockChanges()){
    var candidates=new ArrayList<>(explosion.getToBlow());
    candidates.sort(Comparator.comparingDouble((BlockPos pos)->pos.distToCenterSqr(destination)).thenComparingLong(BlockPos::asLong));
    terrain.blast(destination,radius,candidates);
   }
  }
  // The clients receive actual block updates from the terrain policy. A native affected-block list would replace temporary markers with air.
  explosion.getToBlow().clear();
  explosion.finalizeExplosion(true);
  for(var viewer:w.players())if(!viewer.hasDisconnected()&&viewer.distanceToSqr(destination)<4096&&viewer.connection!=null)viewer.connection.send(new ClientboundExplodePacket(destination.x,destination.y,destination.z,explosion.radius(),List.of(),explosion.getHitPlayers().get(viewer),explosion.getBlockInteraction(),explosion.getSmallExplosionParticles(),explosion.getLargeExplosionParticles(),explosion.getExplosionSound()));
 }
 public static void abort(ServerPlayer p){abort(p,0);}public static boolean abort(ServerPlayer p,long input){if(!VectorStarterSupport.serverThread(p))return false;var h=HOLDS.get(p.getUUID());if(h==null||h.session.go()||input>0&&input!=h.input)return false;finish(h);return true;}public static void remove(ServerPlayer p){if(!VectorStarterSupport.serverThread(p))return;var h=HOLDS.get(p.getUUID());if(h!=null)finish(h);INPUTS.remove(p.getUUID());}
 private static boolean valid(Hold h){var p=h.player;var s=AbilityStorage.get(p);return VectorStarterSupport.ready(p)&&h.world==p.serverLevel()&&h.session.state()==s&&h.session.active()&&s.category.equals("vecmanip")&&s.level>=5&&s.learned(ID)&&Double.isFinite(s.cp)&&Double.isFinite(s.overload)&&Double.isFinite(s.exp(ID))&&VectorStarterSupport.finite(h.at)&&p.serverLevel().getGameTime()>=h.lastTick;}
 private static void finish(Hold h){if(HOLDS.remove(h.player.getUUID(),h)){h.session.discard();send(h,"plasma_cannon_end");}}private static void send(Hold h,String kind){var tag=VectorStarterSupport.packet(h.player,kind,h.token,h.input);tag.putLong("sequence",++h.sequence);VectorStarterSupport.position(tag,h.at);tag.putFloat("charge",h.session.capturedChargeTime());if(h.destination!=null){tag.putDouble("dx",h.destination.x);tag.putDouble("dy",h.destination.y);tag.putDouble("dz",h.destination.z);}VectorStarterSupport.send(h.audience,tag);}
 public static boolean active(ServerPlayer p){return p!=null&&HOLDS.containsKey(p.getUUID());}public static int heldTicks(ServerPlayer p){var h=p==null?null:HOLDS.get(p.getUUID());return h==null?0:h.session.ticks();}public static Vec3 position(ServerPlayer p){var h=p==null?null:HOLDS.get(p.getUUID());return h==null?null:h.at;}public static boolean moving(ServerPlayer p){var h=p==null?null:HOLDS.get(p.getUUID());return h!=null&&h.session.go();}public static void clear(){HOLDS.clear();INPUTS.clear();nextToken=0;}
 private static final class Hold{final ServerPlayer player;final ServerLevel world;final PlasmaCannonSession session;final long token,input;final Set<ServerPlayer> audience;Vec3 at,destination;long lastTick,sequence;Hold(ServerPlayer p,PlasmaCannonSession s,long t,long i){player=p;world=p.serverLevel();session=s;token=t;input=i;audience=VectorStarterSupport.audience(p);at=p.position().add(0,15,0);lastTick=world.getGameTime()-1;}}
}
