/* AcademyCraft1.0.7 RayBarrage/RBContext adaptation, GPLv3; see NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
/** Instant source context; body-yaw wedge, head-yaw selection, no arbitrary client target/geometry. */
public final class RayBarrage {
 public static final String ID=RayBarrageRules.ID;private static final Map<UUID,Long> INPUTS=new HashMap<>();private static final Set<UUID> COMMITTING=new HashSet<>();private static long nextToken;
 private RayBarrage(){}public static boolean start(ServerPlayer p){if(cn.academy.port.AbilityConsumption.busy(p))return false;return start(p,0);}
 public static boolean start(ServerPlayer p,long nonce){if(cn.academy.port.AbilityConsumption.busy(p))return false;if(!MeltdownerLateSupport.ready(p)||!RayBarrageRules.mayStart(AbilityStorage.get(p))||nonce<0||nonce>0&&nonce<=INPUTS.getOrDefault(p.getUUID(),0L)||!COMMITTING.add(p.getUUID()))return false;long token=++nextToken;if(nonce>0)INPUTS.put(p.getUUID(),nonce);var audience=MeltdownerStarterSupport.audience(p);var state=AbilityStorage.get(p);var plan=RayBarrageRules.plan(state.exp(ID));try{MeltdownerStarterSupport.send(audience,MeltdownerLateSupport.packet(p,"ray_barrage_start",token,nonce));var initial=ClassicRaytrace.perform(p,p.getEyePosition(),p.getEyePosition().add(MeltdownerLateSupport.direction(p).scale(20)),ClipContext.Fluid.NONE,e->true);SilbarnEntity silbarn=initial instanceof EntityHitResult eh&&eh.getEntity() instanceof SilbarnEntity s&&!s.isHit()?s:null;
  // Source terminate on failed consume is deferred. Immediate sendToSelf(execute) still runs.
  RayBarrageRules.debit(state,plan,p.getAbilities().instabuild);Vec3 destination;
  if(silbarn!=null){destination=silbarn.position();silbarn.collideWithSelf();var barrage=MeltdownerLateSupport.packet(p,"ray_barrage_scatter",token,nonce);MeltdownerStarterSupport.position(barrage,destination);barrage.putFloat("yaw",p.getYRot());barrage.putFloat("pitch",p.getXRot());MeltdownerStarterSupport.send(audience,barrage);var b=RayBarrageRules.bounds(new ClassicBeamRay.Vec(p.getX(),p.getY(),p.getZ()),p.getYRot(),p.getXRot());for(var target:p.serverLevel().getEntities(p,new AABB(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()),e->e!=silbarn)){if(RayBarrageRules.angular(target.getX()-p.getX(),target.getY()+target.getEyeHeight()-(p.getY()+p.getEyeHeight()),target.getZ()-p.getZ(),p.getYRot(),p.getXRot()))RadiationMarks.attack(p,"meltdowner."+ID,target,plan.scatteredDamage());}}
  else{destination=MeltdownerLateSupport.destination(p,20,true);var result=ClassicRaytrace.perform(p,p.getEyePosition(),p.getEyePosition().add(MeltdownerLateSupport.direction(p).scale(20)),ClipContext.Fluid.NONE,e->true);if(result instanceof EntityHitResult eh)RadiationMarks.attack(p,"meltdowner."+ID,eh.getEntity(),plan.plainDamage());}
  var ray=MeltdownerLateSupport.packet(p,"ray_barrage_preray",token,nonce);MeltdownerLateSupport.ray(ray,p.position().add(0,1.6,0),destination);ray.putBoolean("hit",silbarn!=null);MeltdownerStarterSupport.send(audience,ray);RayBarrageRules.complete(state,plan);return true;
 }finally{COMMITTING.remove(p.getUUID());MeltdownerStarterSupport.send(audience,MeltdownerLateSupport.packet(p,"ray_barrage_end",token,nonce));AbilityStorage.save(p);AcademyNetwork.sync(p);}}
 public static void abort(ServerPlayer p){}public static boolean release(ServerPlayer p){return false;}public static void remove(ServerPlayer p){if(MeltdownerStarterSupport.serverThread(p))INPUTS.remove(p.getUUID());}public static void clear(){INPUTS.clear();COMMITTING.clear();nextToken=0;}
}
