/* Adapted from Lambda Innovation AcademyCraft 1.0.7 RangedRayDamage/Plotter. See NOTICE. */
package cn.academy.port.skill;
import net.minecraft.server.level.ServerPlayer;
import cn.academy.port.AbilityDamage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.api.SkillReflectEvent;
import cn.academy.port.highpower.environment.SkillEnvironment;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;
public final class RailgunDamage {
    public record Result(double beamLength,boolean reflectedHit) {}
    public static Result perform(ServerPlayer player,float damage,float energy) {
        var world=player.serverLevel();var slope=ClassicRaytrace.direction(player).normalize();var start=player.getEyePosition();var end=start.add(slope.scale(cn.academy.port.core.RangedSkillRanges.fixed("railgun")));
        var box=new AABB(start,end).inflate(2.4);
        double maxDistance=Double.MAX_VALUE,beamLength=cn.academy.port.core.RangedSkillRanges.fixed("railgun");boolean reflectedHit=false;
        var targets=new ArrayList<>(world.getEntities(player,box,e->e instanceof LivingEntity&&e.isAlive()));targets.sort(Comparator.comparingDouble(e->e.distanceToSqr(player)));
        for(var target:targets) {
            var offset=target.position().subtract(start);double projection=offset.dot(slope);double lateral=offset.cross(slope).length();
            if(projection<0||projection>cn.academy.port.core.RangedSkillRanges.fixed("railgun")||lateral>=2.4)continue;
            var reflect=new SkillReflectEvent(player,"electromaster.railgun",target);
            if(NeoForge.EVENT_BUS.post(reflect).isCanceled()) {
                var hit=ClassicRaytrace.living(target,15,ClipContext.Fluid.NONE);
                if(hit instanceof EntityHitResult entity) {AbilityDamage.attack(player,"electromaster.railgun",entity.getEntity(),14);reflectedHit=true;}
                double distance=target.distanceTo(player);maxDistance=distance*distance;beamLength=Math.min(beamLength,distance);
                AcademyNetwork.effect(player,"railgun",player.getEyePosition().add(slope.scale(distance)),ClassicRaytrace.direction(target),15);break;
            }
            AbilityDamage.attack(player,"electromaster.railgun",target,damage*(1-.8*Math.min(50,lateral)/50));
        }
        if(player.serverLevel()!=world||!world.mayInteract(player,player.blockPosition()))return new Result(beamLength,reflectedHit);
        var terrain=SkillEnvironment.begin(player,"electromaster.railgun",4);
        if(!terrain.allowsBlockChanges())return new Result(beamLength,reflectedHit);
        var up=Math.abs(slope.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0);var a=slope.cross(up).normalize();var b=slope.cross(a).normalize();var rays=new LinkedHashSet<BlockPos>();
        for(double s=-2;s<=2;s+=.9)for(double t=-2;t<=2;t+=.9) {double r=2*(.9+world.random.nextDouble()*.2);if(s*s+t*t>r*r)continue;var point=start.add(a.scale(s)).add(b.scale(t));rays.add(BlockPos.containing(point));}
        if(rays.isEmpty())return new Result(beamLength,reflectedHit);float average=energy/rays.size();
        for(var pos:rays) {float remaining=average*(.95f+world.random.nextFloat()*.1f);var plotter=new Plotter(pos.getX(),pos.getY(),pos.getZ(),slope.x,slope.y,slope.z);
            for(int step=0;step<(int)Math.ceil(cn.academy.port.core.RangedSkillRanges.fixed("railgun")*(Math.abs(slope.x)+Math.abs(slope.y)+Math.abs(slope.z)))+4&&remaining>0;step++) {var xyz=plotter.next();var hit=new BlockPos(xyz[0],xyz[1],xyz[2]);if(hit.distSqr(pos)>maxDistance||Vec3.atCenterOf(hit).subtract(start).dot(slope)>cn.academy.port.core.RangedSkillRanges.fixed("railgun")||!world.hasChunkAt(hit)||!world.mayInteract(player,hit))break;var state=world.getBlockState(hit);if(state.isAir()||SkillEnvironment.isVacancy(state))continue;float hardness=state.getDestroySpeed(world,hit);if(hardness<0||remaining<hardness||state.hasBlockEntity())break;
                if(!terrain.breakBlock(hit,()->world.random.nextFloat()<.05))break;
                remaining-=hardness;
            }
        }
        return new Result(beamLength,reflectedHit);
    }
}
