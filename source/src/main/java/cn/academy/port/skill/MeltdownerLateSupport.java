/* AcademyCraft1.0.7 common transport/lifecycle adaptation, GPLv3; see NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.core.AbilityProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
final class MeltdownerLateSupport {
 private MeltdownerLateSupport(){}
 static boolean ready(ServerPlayer p){if(!MeltdownerStarterSupport.serverThread(p)||!p.isAlive()||p.isRemoved()||p.isSpectator()||!MeltdownerStarterSupport.finiteAim(p)||!MeltdownerStarterSupport.finite(p.position()))return false;AbilityProgress s=cn.academy.port.AbilityStorage.get(p);return Double.isFinite(s.cp)&&s.cp>=0&&Double.isFinite(s.overload)&&s.overload>=0&&Double.isFinite(s.extraCp)&&Double.isFinite(s.extraOverload);}
 static Vec3 direction(Entity p){var d=RayBarrageRules.rawDirection(p instanceof net.minecraft.world.entity.LivingEntity l?l.getYHeadRot():p.getYRot(),p.getXRot());return new Vec3(d.x(),d.y(),d.z()).normalize();}
 static Vec3 destination(ServerPlayer p,double range,boolean entities){Vec3 eye=p.getEyePosition(),end=eye.add(direction(p).scale(range));var hit=ClassicRaytrace.perform(p,eye,end,ClipContext.Fluid.NONE,e->entities);if(hit instanceof EntityHitResult eh)return hit.getLocation().add(0,eh.getEntity().getEyeHeight()*.6,0);return hit.getType()==HitResult.Type.MISS?end:hit.getLocation();}
 static CompoundTag packet(ServerPlayer p,String kind,long token,long nonce){var t=MeltdownerStarterSupport.packet(p,kind,token,nonce);t.putUUID("entity_uuid",p.getUUID());return t;}
 static void ray(CompoundTag t,Vec3 from,Vec3 to){MeltdownerStarterSupport.position(t,from);Vec3 d=to.subtract(from);t.putDouble("dx",d.lengthSqr()==0?0:d.normalize().x);t.putDouble("dy",d.lengthSqr()==0?0:d.normalize().y);t.putDouble("dz",d.lengthSqr()==0?1:d.normalize().z);t.putDouble("length",d.length());}
 static void target(CompoundTag t,Vec3 p){t.putDouble("tx",p.x);t.putDouble("ty",p.y);t.putDouble("tz",p.z);}
}
