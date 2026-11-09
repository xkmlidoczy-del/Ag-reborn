package cn.academy.port.fleet;

import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.core.ContinuousHighPowerGrowth;
import java.math.BigDecimal;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;

/** A block-clipped hitscan. A guardian must see the actual first living body before it spends a shot. */
public final class FleetBallistics {
    public static double damage(LivingEntity shooter,int variant){
        double base=InteractionLab.DAMAGE[variant];
        if(!HighPowerCombat.enabled(shooter))return base;
        var position=shooter instanceof ServerPlayer p&&cn.academy.port.highpower.HighPowerGrowth.available(p)?cn.academy.port.highpower.HighPowerGrowth.values(p).position():BigDecimal.valueOf(HighPowerCombat.level(shooter));
        return base*ContinuousHighPowerGrowth.sample(position).damage().doubleValue()/50.0;
    }
    public static HitResult trace(LivingEntity shooter,Vec3 direction,double range){
        var world=shooter.level();Vec3 start=shooter.getEyePosition().add(0,-.12,0),end=start.add(direction.normalize().scale(range));
        var block=world.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,shooter));
        Vec3 stop=block.getType()==HitResult.Type.MISS?end:block.getLocation();
        var entity=ProjectileUtil.getEntityHitResult(world,shooter,start,stop,new AABB(start,stop).inflate(.35),
            e->e!=shooter&&!e.isSpectator()&&e.isAlive()&&e.isPickable()&&!e.isPassengerOfSameVehicle(shooter));
        return entity==null?block:entity;
    }
    public static boolean clearShot(LivingEntity shooter,LivingEntity target){
        var hit=trace(shooter,target.getBoundingBox().getCenter().subtract(shooter.getEyePosition().add(0,-.12,0)),64);
        return hit instanceof EntityHitResult e&&e.getEntity()==target;
    }
    public static boolean fire(LivingEntity shooter,int variant,Vec3 direction){
        var hit=trace(shooter,direction,64);boolean accepted=false;
        if(hit instanceof EntityHitResult e){
            if(shooter instanceof cn.academy.port.city.defense.GuardianEntity g&&e.getEntity()!=g.getTarget())return false;
            var source=shooter instanceof net.minecraft.world.entity.player.Player p?shooter.damageSources().playerAttack(p):shooter.damageSources().mobAttack(shooter);
            accepted=e.getEntity().hurt(source,(float)damage(shooter,variant));
        }
        if(shooter.level() instanceof ServerLevel world){
            Vec3 a=shooter.getEyePosition().add(0,-.12,0),b=hit.getLocation();double length=a.distanceTo(b);
            Vec3 step=b.subtract(a).normalize();var dust=new DustParticleOptions(new Vector3f(.25f,.9f,1f),.45f);
            // Sparse server-authored tracer: no second damage path and no client-selected endpoint.
            for(double d=.6;d<Math.min(64,length);d+=1.7){var p=a.add(step.scale(d));world.sendParticles(dust,p.x,p.y,p.z,1,0,0,0,0);}
        }
        return accepted;
    }
}
