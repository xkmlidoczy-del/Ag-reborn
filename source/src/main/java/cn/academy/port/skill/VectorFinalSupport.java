/* AcademyCraft1.0.7 and LambdaLib1.2.3 world/transport adaptation. See NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.AcademyConfig;
import cn.academy.port.AbilityDamage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
final class VectorFinalSupport {
 private VectorFinalSupport(){}
 static Vec3 looking(ServerPlayer p,double range,boolean livingOnly){var start=p.getEyePosition();var hit=ClassicRaytrace.perform(p,start,start.add(ClassicRaytrace.direction(p).scale(range)),ClipContext.Fluid.NONE,e->!livingOnly||e instanceof LivingEntity);return hit instanceof EntityHitResult e?e.getEntity().position().add(0,e.getEntity().getEyeHeight()*.6,0):hit.getLocation();}
 /** World ray includes the caster, unlike traceLiving; nearest inflated intercept reports entity feet. */
 static boolean collision(ServerPlayer p,Vec3 a,Vec3 b){var w=p.serverLevel();if(w.clip(new ClipContext(a,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()!=HitResult.Type.MISS)return true;for(var e:w.getEntities((Entity)null,new AABB(a,b).inflate(1),e->e.isPickable()))if(e.getBoundingBox().inflate(.3).clip(a,b).isPresent())return true;return false;}
 static void attack(ServerPlayer p,String id,Entity e,float amount){if(e instanceof Player&&!AcademyConfig.ATTACK_PLAYERS.get()||e instanceof ServerPlayer other&&!p.canHarmPlayer(other))return;AbilityDamage.attack(p,"vecmanip."+id,e,amount);}
}
