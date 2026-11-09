package cn.academy.port.city;

import cn.academy.port.train.NorthSouthLine;
import cn.academy.port.train.NorthSouthTrainManager;
import cn.academy.port.train.TrainCarEntity;
import cn.academy.port.train.TrainManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Six bounded commuters: real stair paths, scheduled trains, native seats and ground-floor workplaces. */
@EventBusSubscriber(modid="academy")
public final class CityCommuters {
    public enum Phase { TO_PLATFORM, WAITING, RIDING, TO_WORK, WORKING }
    private record Plan(String id,CityNpcEntity.Look look,int home,int work,CityNpcEntity.Activity activity){}
    private static final List<Plan> PLANS=List.of(
            new Plan("student_intern",CityNpcEntity.Look.STUDENT_BLAZER,1,3,CityNpcEntity.Activity.CHECK),
            new Plan("campus_reader",CityNpcEntity.Look.STUDENT_BOOKS,1,2,CityNpcEntity.Activity.READ),
            new Plan("station_technician",CityNpcEntity.Look.TECHNICIAN,2,3,CityNpcEntity.Activity.CHECK),
            new Plan("visiting_teacher",CityNpcEntity.Look.TEACHER,2,1,CityNpcEntity.Activity.READ),
            new Plan("office_commuter",CityNpcEntity.Look.OFFICE_STAFF,3,2,CityNpcEntity.Activity.PHONE),
            new Plan("campus_assistant",CityNpcEntity.Look.STUDENT_CASUAL,3,1,CityNpcEntity.Activity.READ));
    private static final Map<ServerLevel,Map<String,Actor>> LEVELS=new IdentityHashMap<>();
    private static final class Actor {
        final Plan plan;CityNpcEntity npc;int station,target,waypoint;Phase phase;boolean homeTrip;long nextPath;
        Actor(Plan p,CompoundTag tag){plan=p;station=tag.contains("station")?tag.getInt("station"):p.home;homeTrip=tag.getBoolean("home_trip");target=homeTrip?p.home:p.work;phase=tag.contains("phase")?Phase.valueOf(tag.getString("phase")):Phase.TO_PLATFORM;
            if(phase==Phase.RIDING||phase==Phase.WAITING)phase=Phase.TO_PLATFORM;}
        CompoundTag save(){var t=new CompoundTag();t.putInt("station",station);t.putString("phase",phase.name());t.putBoolean("home_trip",homeTrip);return t;}
    }
    private static int z(int station){return NorthSouthLine.STATIONS.get(station).z();}
    private static BlockPos work(Actor a){return new BlockPos(a.plan.home==a.station?121:123,0,z(a.station)+(a.plan.home==a.station?82:90));}
    private static BlockPos platform(Actor a){return new BlockPos(144,45,z(a.station)+12+PLANS.indexOf(a.plan)%2*3);}
    private static void save(ServerLevel w,Actor a){CityNpcMemory.get(w).write("journey:"+a.plan.id,a.save());if(a.npc!=null)a.npc.combat().checkpoint();}
    private static boolean near(ServerLevel w,BlockPos p,double radius){return w.players().stream().anyMatch(x->x.distanceToSqr(Vec3.atCenterOf(p))<radius*radius);}
    private static List<BlockPos> descent(Actor a){
        var result=new ArrayList<BlockPos>();int z=z(a.station);
        result.add(new BlockPos(146,45,z+40));result.add(new BlockPos(144,45,z+49));
        for(int d=52;d<=94;d+=6)result.add(new BlockPos(144,96-d,z+d));
        result.add(new BlockPos(144,1,z+95));result.add(new BlockPos(144,0,z+99));result.add(new BlockPos(136,0,z+99));result.add(new BlockPos(120,0,z+99));result.add(new BlockPos(117,0,z+94));result.add(work(a));return result;
    }
    private static List<BlockPos> ascent(Actor a){
        var out=new ArrayList<BlockPos>();int z=z(a.station);out.add(work(a));out.add(new BlockPos(114,0,z+94));out.add(new BlockPos(120,0,z+97));out.add(new BlockPos(134,0,z+97));out.add(new BlockPos(142,0,z+97));out.add(new BlockPos(142,1,z+95));
        for(int d=94;d>=52;d-=6)out.add(new BlockPos(142,96-d,z+d));
        out.add(new BlockPos(142,45,z+49));out.add(new BlockPos(142,45,z+40));out.add(platform(a));return out;
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof ServerLevel world)||!world.dimension().equals(TrainManager.CITY))return;
        long now=world.getGameTime();if(now%5!=0)return;
        var actors=LEVELS.computeIfAbsent(world,w->new HashMap<>());
        for(var plan:PLANS){
            var a=actors.get(plan.id);
            if(a==null){
                a=new Actor(plan,CityNpcMemory.get(world).read("journey:"+plan.id));
                if(a.station==plan.home&&Math.floorMod(world.getDayTime(),24000)>=12000&&a.phase==Phase.TO_PLATFORM)a.phase=Phase.WORKING;
                var at=work(a);if(!world.hasChunkAt(at)||!near(world,at,176))continue;
                var n=CityNpcContent.NPC.get().create(world);if(n==null||!world.isEmptyBlock(at)||!world.isEmptyBlock(at.above()))continue;
                n.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);n.prepareAmbient(plan.look,CityNpcEntity.Activity.STAND);n.commuter(plan.id);n.identity("commuter:"+plan.id);
                n.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64);n.setCustomName(Component.translatable("academy.city.commute.person."+plan.id));n.setCustomNameVisible(true);
                if(!world.noCollision(n)||!world.addFreshEntity(n))continue;a.npc=n;actors.put(plan.id,a);
                if(a.phase==Phase.TO_WORK)a.phase=Phase.WORKING;
                n.combat().dailyHome(at);
            }
            if(a.npc.isRemoved()){save(world,a);actors.remove(plan.id);continue;}
            if(!a.npc.isPassenger()&&!near(world,a.npc.blockPosition(),208)){
                if(a.phase==Phase.TO_WORK)a.phase=Phase.WORKING;
                save(world,a);a.npc.discard();actors.remove(plan.id);continue;
            }
            advance(world,a,now);if(now%200==0)save(world,a);
        }
    }
    private static void advance(ServerLevel world,Actor a,long now){
        var n=a.npc;
        if(a.phase==Phase.RIDING){
            if(!(n.getVehicle() instanceof TrainCarEntity car)){a.phase=Phase.WAITING;save(world,a);return;}
            n.perform(CityNpcEntity.Activity.SIT,CityNpcEntity.Expression.NEUTRAL);n.setYRot(car.getYRot());n.yBodyRot=car.getYRot();n.setYHeadRot(car.getYRot()+(float)Math.sin(now*.025)*12);
            var s=NorthSouthTrainManager.state(world);
            if(s.boarding()&&s.station()==a.target){
                var exit=NorthSouthTrainManager.exit(car,n);
                if(exit!=null){n.stopRiding();n.setPos(exit);n.setDeltaMovement(Vec3.ZERO);a.station=a.target;a.phase=Phase.TO_WORK;a.waypoint=0;n.combat().dailyHome(n.blockPosition());n.perform(CityNpcEntity.Activity.STAND,CityNpcEntity.Expression.NEUTRAL);save(world,a);}
            }return;
        }
        if(n.conversing()||n.combat().busy()){n.getNavigation().stop();return;}
        long day=Math.floorMod(world.getDayTime(),24000);boolean homeTime=day>=12000;
        if(a.phase==Phase.WORKING){
            n.perform(homeTime?CityNpcEntity.Activity.READ:a.plan.activity,homeTime?CityNpcEntity.Expression.NEUTRAL:CityNpcEntity.Expression.FOCUS);
            int desired=homeTime?a.plan.home:a.plan.work;
            if(desired!=a.station){a.target=desired;a.homeTrip=homeTime;a.phase=Phase.TO_PLATFORM;a.waypoint=0;save(world,a);}return;
        }
        if(a.phase==Phase.WAITING){
            n.perform(CityNpcEntity.Activity.PHONE,CityNpcEntity.Expression.NEUTRAL);
            var s=NorthSouthTrainManager.state(world);
            if(s.boarding()&&s.station()==a.station&&s.dir()==Integer.signum(a.target-a.station)){
                for(int role:new int[]{1,0,2}){
                    var car=NorthSouthTrainManager.car(role);if(car==null||!car.isAlive()||!car.hasCommuterSeat())continue;
                    var board=new BlockPos(144,45,z(a.station)+(int)Math.round((role-1)*cn.academy.port.train.TrainLine.CAR_PITCH));
                    if(n.distanceToSqr(Vec3.atBottomCenterOf(board))>3){navigate(world,a,board,now);return;}
                    if(car.boardCommuter(n)){n.getNavigation().stop();a.phase=Phase.RIDING;n.perform(CityNpcEntity.Activity.SIT,CityNpcEntity.Expression.NEUTRAL);save(world,a);return;}
                }
            }else navigate(world,a,platform(a),now);
            return;
        }
        var path=a.phase==Phase.TO_PLATFORM?ascent(a):descent(a);
        while(a.waypoint<path.size()&&n.distanceToSqr(Vec3.atBottomCenterOf(path.get(a.waypoint)))<2.4)a.waypoint++;
        if(a.waypoint>=path.size()){
            a.phase=a.phase==Phase.TO_PLATFORM?Phase.WAITING:Phase.WORKING;a.waypoint=0;n.getNavigation().stop();n.combat().dailyHome(n.blockPosition());save(world,a);return;
        }
        n.perform(CityNpcEntity.Activity.STAND,CityNpcEntity.Expression.NEUTRAL);navigate(world,a,path.get(a.waypoint),now);
    }
    private static void navigate(ServerLevel world,Actor a,BlockPos p,long now){
        if(now<a.nextPath||!world.hasChunkAt(p))return;a.nextPath=now+20;
        a.npc.getNavigation().moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,.95);
    }
    public static Component description(CityNpcEntity npc){
        var all=npc.level() instanceof ServerLevel w?LEVELS.get(w):null;var a=all==null?null:all.get(npc.commuterId());
        if(a==null)return Component.translatable("academy.city.commute.intro");
        return Component.translatable("academy.city.commute.plan",Component.translatable(NorthSouthLine.STATIONS.get(a.plan.home).translationKey()),Component.translatable(NorthSouthLine.STATIONS.get(a.plan.work).translationKey()),Component.translatable("academy.city.commute.phase."+a.phase.name().toLowerCase(java.util.Locale.ROOT)));
    }
    public static Phase phase(CityNpcEntity npc){var map=LEVELS.get(npc.level());var a=map==null?null:map.get(npc.commuterId());return a==null?null:a.phase;}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel w){var map=LEVELS.remove(w);if(map!=null)map.values().forEach(a->save(w,a));}}
}
