package cn.academy.port.city.defense;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** One durable five-person escalation per incident, based on actual deaths, never unloads. */
@EventBusSubscriber(modid="academy")
public final class O5Reinforcements {
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void died(LivingDeathEvent event){
        if(event.isCanceled()||!(event.getEntity().level() instanceof ServerLevel world))return;
        var dead=event.getEntity();var data=CityDefenseData.get(world);
        for(var t:data.teams.values()){
            if(!t.active(world.getGameTime()))continue;
            if(t.officers.contains(dead.getUUID())&&!t.casualties.contains(dead.getUUID())){t.casualties.add(dead.getUUID());data.setDirty();}
            if(t.victim.equals(dead.getUUID())){t.civilianLost=true;data.setDirty();}
        }
    }
    public static boolean failed(ServerLevel world,CityDefenseData.Team t){
        if(t.civilianLost||t.casualties.size()>=CityDefenseContent.O5_LOSSES.get())return true;
        if(t.officers.size()!=10)return false;
        int survivors=0;
        for(var id:t.officers){
            if(t.casualties.contains(id))continue;
            // A missing entity might simply be in an unloaded chunk: it is neither dead nor low-health evidence.
            if(!(world.getEntity(id) instanceof GuardianEntity guard)||!guard.isAlive())return false;
            survivors++;
            var health=cn.academy.port.highpower.CityNpcPower.health(guard);var maximum=cn.academy.port.highpower.CityNpcPower.maxHealth(guard);
            if(health.compareTo(maximum.multiply(java.math.BigDecimal.valueOf(CityDefenseContent.O5_LOW_HEALTH.get())))>0)return false;
        }return survivors>0;
    }
    public static boolean update(ServerLevel world,CityDefenseData.Team t){
        if(t.o5Called||!t.active(world.getGameTime())||!failed(world,t)||CityDefense.target(world,t)==null)return false;
        List<Vec3> sites=landingSites(world,t.center);if(sites.size()!=5)return false;
        var created=new ArrayList<O5OperativeEntity>();
        for(int i=0;i<5;i++){
            var g=CityDefenseContent.O5.get().create(world);
            if(g==null){created.forEach(Entity::discard);return false;}
            var at=sites.get(i);g.prepareDrop(t.id,i,t.expires,at,24+i*2);
            if(!world.addFreshEntity(g)){g.discard();created.forEach(Entity::discard);return false;}created.add(g);
        }
        created.forEach(g->t.operatives.add(g.getUUID()));t.o5Called=true;CityDefenseData.get(world).setDirty();
        world.players().stream().filter(p->p.position().distanceToSqr(t.center)<160*160).forEach(p->p.sendSystemMessage(Component.translatable("academy.defense.o5_inbound")));
        cn.academy.port.fleet.InteractionLab.LOGGER.info("ACA_O5_DISPATCH incident={} count={} casualties={} civilianLost={}",t.id,t.operatives.size(),t.casualties.size(),t.civilianLost);
        return true;
    }
    /** Native hurt cooldown is shared by all attackers. Rifles cover the perimeter once O5 owns the contact. */
    public static boolean ownsContact(ServerLevel world,CityDefenseData.Team t,net.minecraft.world.entity.LivingEntity enemy){
        if(!t.o5Called)return false;
        for(var id:t.operatives)if(world.getEntity(id) instanceof O5OperativeEntity g&&g.handles(enemy))return true;
        return false;
    }
    /** Entire vertical body sweep is checked first. No roofs, interiors, fluids or player structures are overwritten. */
    public static List<Vec3> landingSites(ServerLevel world,Vec3 center){
        var sites=new ArrayList<Vec3>();
        for(int slot=0;slot<5;slot++){
          boolean found=false;
          for(int radius:new int[]{9,14,20,27,35,43}){
           for(int i:new int[]{0,1,-1,2,-2,3,-3,4,-4}){
            double angle=(slot*72+i*15)*Math.PI/180;Vec3 p=CityDefense.ground(world,center.add(Math.cos(angle)*radius,0,Math.sin(angle)*radius));
            if(p==null||sites.stream().anyMatch(q->q.distanceToSqr(p)<16))continue;
            var block=BlockPos.containing(p);if(!world.canSeeSky(block.above(4))||p.y+40>=world.getMaxBuildHeight())continue;
            var sweep=new AABB(p.x-.63,p.y+.02,p.z-.63,p.x+.63,p.y+38,p.z+.63);
            if(!world.getWorldBorder().isWithinBounds(sweep)||world.getBlockCollisions(null,sweep).iterator().hasNext())continue;
            if(!world.getFluidState(block).isEmpty()||!world.getBlockState(block.below()).isFaceSturdy(world,block.below(),net.minecraft.core.Direction.UP))continue;
            sites.add(p);found=true;break;
           }if(found)break;
          }if(!found)return List.of();
        }return sites;
    }
}
