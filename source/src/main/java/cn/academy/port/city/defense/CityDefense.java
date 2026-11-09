package cn.academy.port.city.defense;

import cn.academy.port.city.*;
import cn.academy.port.fleet.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Civilian harm dispatches one ten-officer response, coalesced by suspect or neighborhood. */
@EventBusSubscriber(modid="academy")
public final class CityDefense {
    private static final Set<ServerLevel> TEST_WORLDS=Collections.newSetFromMap(new WeakHashMap<>());
    public static void allowTestWorld(ServerLevel world){if(!net.neoforged.neoforge.gametest.GameTestHooks.isGametestEnabled()&&!Boolean.getBoolean("academy.qa.confirmOwnWorld"))throw new IllegalStateException("Isolated QA only");TEST_WORLDS.add(world);}
    public static boolean enabled(ServerLevel world){return CityDefenseContent.ENABLED.get()&&(world.dimension().location().toString().equals("academy:story_city_map")||TEST_WORLDS.contains(world));}
    public static boolean civilian(LivingEntity e){return e instanceof CityNpcEntity n&&n.look()!=CityNpcEntity.Look.GUARD&&!n.sparring()||e instanceof Villager;}
    public static boolean exposesCivilian(CityNpcEntity n,DamageSource source){return n.level() instanceof ServerLevel world&&enabled(world)&&civilian(n)&&source.getEntity() instanceof LivingEntity a&&a!=n&&!(a instanceof GuardianEntity)&&!a.isAlliedTo(n);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void harmed(LivingDamageEvent.Post event){
        var victim=event.getEntity();if(!(victim.level() instanceof ServerLevel world)||!enabled(world)||!civilian(victim)||event.getNewDamage()<=0)return;
        if(event.getSource().getEntity() instanceof LivingEntity suspect&&suspect!=victim&&!(suspect instanceof GuardianEntity)&&!suspect.isAlliedTo(victim))dispatch(world,victim,suspect);
    }
    public static CityDefenseData.Team dispatch(ServerLevel world,LivingEntity victim,LivingEntity suspect){
        if(!enabled(world)||!civilian(victim)||suspect instanceof GuardianEntity||suspect==victim)return null;
        var data=CityDefenseData.get(world);long now=world.getGameTime();
        for(var t:data.teams.values())if(t.threats.contains(suspect.getUUID())||t.center.distanceToSqr(victim.position())<64*64){
            if(t.active(now)){if(!t.threats.contains(suspect.getUUID()))t.threats.add(suspect.getUUID());t.lastHarm=now;t.quietSince=-1;data.setDirty();return t;}
            if(now-Math.max(t.closedAt,t.expires)<600)return null;
        }
        if(data.teams.values().stream().filter(t->t.active(now)).count()>=CityDefenseContent.MAX_TEAMS.get())return null;
        var spawn=spawnSite(world,victim.position());if(spawn==null)return null;
        int sector=Math.floorMod((int)Math.floor(victim.getX()/256)+(int)Math.floor(victim.getZ()/256),4);
        String name=new String[]{"中央守护一队","学园守护二队","南区守护三队","外围守护四队"}[sector];
        var t=new CityDefenseData.Team(UUID.randomUUID(),victim.getUUID(),victim.position(),now,now+CityDefenseContent.LIFETIME.get(),name);t.threats.add(suspect.getUUID());
        var created=new ArrayList<Entity>();
        // Every officer is placed safely first; a failed addition rolls the entire response back.
        for(int i=0;i<10;i++){
            Vec3 p=ground(world,spawn.add((i%5-2)*1.5,0,(i/5)*2));
            if(p==null){created.forEach(Entity::discard);return null;}
            var g=CityDefenseContent.GUARD.get().create(world);if(g==null){created.forEach(Entity::discard);return null;}
            g.moveTo(p.x,p.y,p.z,0,0);g.prepare(t.id,i,CityDefenseContent.LEVEL.get(),t.expires,name);
            if(!world.addFreshEntity(g)){g.discard();created.forEach(Entity::discard);return null;}created.add(g);t.officers.add(g.getUUID());
        }
        for(int i=0;i<3;i++){
            var car=InteractionLab.CAR.get().create(world);if(car==null)continue;car.variant(i==2?4:3);
            Vec3 p=vehicleSite(world,car,spawn,i,victim.position());if(p==null){car.discard();continue;}
            float yaw=yaw(victim.position().subtract(p));car.moveTo(p.x,p.y,p.z,yaw,0);car.response(t.id,t.expires);if(!car.siren())car.toggleSiren();
            if(!world.addFreshEntity(car)){car.discard();continue;}created.add(car);t.vehicles.add(car.getUUID());
            int start=i==0?0:i==1?4:7,end=i==0?4:i==1?7:10;
            for(int n=start;n<end;n++){var g=world.getEntity(t.officers.get(n));if(g!=null)g.startRiding(car,true);}
        }
        data.teams.put(t.id,t);data.setDirty();
        cn.academy.port.fleet.InteractionLab.LOGGER.info("ACA_DEFENSE_DISPATCH team={} officers={} vehicles={} suspect={}",t.id,t.officers.size(),t.vehicles.size(),suspect.getUUID());
        return t;
    }
    private static Vec3 spawnSite(ServerLevel world,Vec3 center){
        for(int radius:new int[]{26,20,32,14,8})for(int angle=0;angle<16;angle++){
            double a=angle*Math.PI/8;Vec3 p=ground(world,center.add(Math.cos(a)*radius,0,Math.sin(a)*radius));if(p==null)continue;
            boolean clear=true;for(int i=0;i<10;i++)if(ground(world,p.add((i%5-2)*1.5,0,(i/5)*2))==null){clear=false;break;}if(clear)return p;
        }return null;
    }
    private static Vec3 vehicleSite(ServerLevel world,LabVehicle car,Vec3 origin,int ordinal,Vec3 scene){
        for(int attempt=0;attempt<28;attempt++){
            double angle=attempt*Math.PI/7;double radius=attempt<7?0:3+(attempt/7)*3;
            Vec3 p=ground(world,origin.add((ordinal-1)*7+Math.cos(angle)*radius,0,-6+Math.sin(angle)*radius));
            if(p!=null&&car.canDriveAt(p,yaw(scene.subtract(p)))&&world.getEntitiesOfClass(LabVehicle.class,new AABB(p,p).inflate(4)).isEmpty())return p;
        }return null;
    }
    /** Search only loaded blocks close to the incident's floor; roofs are not road candidates. */
    public static Vec3 ground(ServerLevel world,Vec3 candidate){
        BlockPos at=BlockPos.containing(candidate);if(!world.hasChunkAt(at)||!world.getWorldBorder().isWithinBounds(at))return null;
        var hit=world.clip(new ClipContext(candidate.add(0,2.1,0),candidate.add(0,-4,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if(hit.getType()!=HitResult.Type.BLOCK)return null;double y=hit.getLocation().y+.02;
        var p=new Vec3(candidate.x,y,candidate.z);var box=new AABB(p.x-.32,y,p.z-.32,p.x+.32,y+1.85,p.z+.32);
        return world.getBlockCollisions(null,box).iterator().hasNext()||!world.getFluidState(BlockPos.containing(p)).isEmpty()?null:p;
    }
    public static LivingEntity civilian(ServerLevel world,CityDefenseData.Team t){var e=world.getEntity(t.victim);return e instanceof LivingEntity l&&l.isAlive()?l:null;}
    private static boolean enemy(LivingEntity e){return e.isAlive()&&!(e instanceof GuardianEntity)&&!civilian(e)&&!(e instanceof Player p&&(p.isCreative()||p.isSpectator()));}
    public static LivingEntity target(ServerLevel world,CityDefenseData.Team t){
        for(var id:t.threats){var e=world.getEntity(id);if(e instanceof LivingEntity l&&enemy(l)&&l.position().distanceToSqr(t.center)<110*110)return l;}return null;
    }
    public static void addThreat(ServerLevel world,UUID team,LivingEntity attacker){var t=CityDefenseData.get(world).team(team);if(t!=null&&enemy(attacker)&&!t.threats.contains(attacker.getUUID())&&t.threats.size()<16){t.threats.add(attacker.getUUID());CityDefenseData.get(world).setDirty();}}
    public static int coverCount(ServerLevel world,CityDefenseData.Team t,GuardianEntity except){int n=0;for(var id:t.officers)if(world.getEntity(id) instanceof GuardianEntity g&&g!=except&&g.isAlive()&&!g.isPassenger()&&!g.reloading()&&GunItem.ammo(g.getMainHandItem())>0)n++;return n;}
    public static Vec3 tacticalPoint(GuardianEntity guard,LivingEntity enemy,CityDefenseData.Team team,boolean cover,boolean clear){
        var world=(ServerLevel)guard.level();Vec3 away=team.center.subtract(enemy.position()).multiply(1,0,1).normalize();if(away.lengthSqr()<.1)away=new Vec3(0,0,1);
        Vec3 side=new Vec3(-away.z,0,away.x);double offset=(guard.role()%2==0?-1:1)*(guard.role()==4||guard.role()==5?10:3+guard.role()*.45);
        Vec3 wanted=enemy.position().add(away.scale(guard.role()==3?24:14)).add(side.scale(offset));
        Vec3 best=null;double score=Double.MAX_VALUE;
        for(int i=0;i<12;i++){
            double a=i*Math.PI/6;var p=ground(world,wanted.add(Math.cos(a)*3,0,Math.sin(a)*3));if(p==null)continue;
            // Prefer a visible firing aperture with a low obstacle between the torso and suspect.
            var eye=world.clip(new ClipContext(p.add(0,1.45,0),enemy.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,guard));
            var body=world.clip(new ClipContext(p.add(0,.65,0),enemy.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,guard));
            double s=p.distanceToSqr(guard.position())*.1+(eye.getType()==HitResult.Type.MISS?0:80)+(cover&&body.getType()==HitResult.Type.BLOCK?-12:0);
            if(s<score){score=s;best=p;}
        }return best;
    }
    public static void drive(GuardianEntity driver,LabVehicle car,CityDefenseData.Team t){
        if(car.getFirstPassenger()!=driver)return;long elapsed=driver.level().getGameTime()-t.dispatched;
        Vec3 delta=t.center.subtract(car.position()).multiply(1,0,1);double distance=delta.length();
        if(distance<12||elapsed>200||target((ServerLevel)driver.level(),t)==null){
            car.aiInput(driver,0,0);if(Math.abs(car.speed())>.035&&elapsed<=220)return;
            if(!car.doorOpen())car.toggleDoor();for(var e:List.copyOf(car.getPassengers()))e.stopRiding();if(car.siren())car.toggleSiren();return;
        }
        float desired=yaw(delta),turn=Mth.clamp(-Mth.wrapDegrees(desired-car.getYRot())/32f,-1,1);
        Vec3 ahead=car.position().add(delta.normalize().scale(2));
        boolean blocked=!car.canDriveAt(ahead,car.getYRot());
        // Local collision avoidance tries both sides before waiting for a safe dismount.
        if(blocked){boolean left=car.canDriveAt(car.position().add(delta.z/distance*2,0,-delta.x/distance*2),car.getYRot()-20);
            turn=left?1:-1;car.aiInput(driver,Math.abs(car.speed())>.06f?0:.25f,turn);
        }else car.aiInput(driver,distance<16?.3f:.8f,turn);
    }
    private static float yaw(Vec3 delta){return (float)Math.toDegrees(Math.atan2(-delta.x,delta.z));}
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof ServerLevel world)||world.getGameTime()%20!=0)return;
        var data=CityDefenseData.get(world);long now=world.getGameTime();
        for(var t:data.teams.values()){
            if(t.active(now)){
                O5Reinforcements.update(world,t);
                if(target(world,t)==null){if(t.quietSince<0){t.quietSince=now;data.setDirty();}}else t.quietSince=-1;
                // The escort uses existing native navigation; the daily AI is suspended only during the lease.
                var civilian=civilian(world,t);var enemy=target(world,t);
                if(civilian instanceof CityNpcEntity n&&enemy!=null&&!n.isPassenger()){
                    Vec3 away=n.position().subtract(enemy.position()).multiply(1,0,1).normalize();var safe=ground(world,t.center.add(away.scale(10)));
                    if(safe!=null)n.getNavigation().moveTo(safe.x,safe.y,safe.z,1.05);
                }
                if(t.quietSince>=0&&now-t.quietSince>240||now+20>=t.expires){t.closedAt=now;data.setDirty();}
            }else if(t.closedAt<0){t.closedAt=now;data.setDirty();}
            if(t.closedAt>=0){for(var id:t.operatives){var e=world.getEntity(id);if(e!=null)e.discard();}for(var id:t.officers){var e=world.getEntity(id);if(e!=null){e.stopRiding();e.discard();}}for(var id:t.vehicles){var e=world.getEntity(id);if(e!=null){e.ejectPassengers();e.discard();}}}
        }
        if(data.teams.values().removeIf(t->t.closedAt>=0&&now-t.closedAt>1200))data.setDirty();
    }
    public static boolean escorted(CityNpcEntity n){if(!(n.level() instanceof ServerLevel world)||!enabled(world))return false;long now=world.getGameTime();return CityDefenseData.get(world).teams.values().stream().anyMatch(t->t.active(now)&&t.victim.equals(n.getUUID()));}
}
