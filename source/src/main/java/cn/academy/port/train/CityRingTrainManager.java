package cn.academy.port.train;

import cn.academy.port.city.CityPlatformGate;
import cn.academy.port.city.CityRingTransit;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Synchronized three-car services alternating directions over one city ring. */
@EventBusSubscriber(modid="academy")
public final class CityRingTrainManager {
    private static final TrainCarEntity[][] CARS=new TrainCarEntity[CityRingLine.SERVICES][3];
    private static final TicketType<ChunkPos> RIDE_TICKET=TicketType.create("academy_ring_train",Comparator.comparingLong(ChunkPos::toLong));
    private static final Set<ChunkPos> TICKETS=new HashSet<>();
    private record Cue(int station, TrainLine.Phase phase) {}
    private static final Map<ServerPlayer,Cue> LAST_CUE=new WeakHashMap<>();
    private static double offset;
    private CityRingTrainManager() {}
    public static CityRingLine.State state(ServerLevel level) { return CityRingLine.at(level.getGameTime()/20.0+offset); }
    public static CityRingLine.State state(ServerLevel level,int service) { return CityRingLine.at(level.getGameTime()/20.0+offset,service); }
    public static CityRingLine.State state(ServerLevel level,TrainCarEntity car) {
        int service=serviceOf(car);
        return service<0 ? null : state(level,service);
    }
    private static int serviceOf(TrainCarEntity car) {
        if(car==null) return -1;
        for(int service=0;service<CARS.length;service++)
            for(var candidate:CARS[service]) if(candidate==car) return service;
        return -1;
    }
    private static Component name(int station) { return Component.translatable(CityRingLine.STATIONS.get(station).translationKey()); }
    private static boolean occupied() {
        for (var cars:CARS) for (var car:cars) if (car!=null && !car.isRemoved() && car.isVehicle()) return true;
        return false;
    }
    private static boolean occupied(int service) {
        for(var car:CARS[service]) if(car!=null && !car.isRemoved() && car.isVehicle()) return true;
        return false;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var level=event.getServer().getLevel(TrainManager.CITY); if (level==null) return;
        updateTickets(level);
        for(int service=0;service<CARS.length;service++) {
            var state=state(level,service); var p=state.point();
            double range=CARS[service][1]==null?220:320;
            boolean near=occupied(service) || level.players().stream().anyMatch(player -> Math.abs(player.getX()-p.x())<range && Math.abs(player.getZ()-p.z())<range);
            if (near) for (int role=0;role<CARS[service].length;role++) {
                var car=CARS[service][role];
                if (car==null || car.isRemoved() || car.level()!=level) {
                    car=new TrainCarEntity(TrainContent.CAR.get(),level);car.role(role);car.ring(true);place(car,state);
                    CARS[service][role]=level.addFreshEntity(car)?car:null;
                } else place(car,state);
            } else release(service);
        }
        if (level.getGameTime()%5==0) updateGates(level);
        if (level.getGameTime()%20==0) showStatus(level);
    }
    private static void place(TrainCarEntity car,CityRingLine.State state) {
        var p=CityRingLine.point(state.distance()+(car.role()-1)*TrainLine.CAR_PITCH);
        car.setOldPosAndRot(); car.setYRot((float)(p.yaw()+(car.role()==0?180:0)));
        car.setPos(p.x(),CityRingLine.RAIL_Y,p.z()); car.setDeltaMovement(p.dx()*state.direction()*state.speed()/20,0,p.dz()*state.direction()*state.speed()/20);
        car.servicePhase(state.phase()); for (var passenger:car.getPassengers()) car.positionRider(passenger);
    }
    private static void updateTickets(ServerLevel level) {
        if (!occupied() && TICKETS.isEmpty()) return;
        Set<ChunkPos> wanted=new HashSet<>();
        for(int service=0;service<CARS.length;service++) if(occupied(service)) {
          var state=state(level,service);
          for (int step=-3;step<=6;step++) {
            var p=CityRingLine.point(state.distance()+state.direction()*step*16);
            int cx=Math.floorDiv((int)Math.floor(p.x()),16),cz=Math.floorDiv((int)Math.floor(p.z()),16);
            for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) wanted.add(new ChunkPos(cx+dx,cz+dz));
          }
        }
        var chunks=level.getChunkSource();
        for(var pos:TICKETS) if(!wanted.contains(pos)) chunks.removeRegionTicket(RIDE_TICKET,pos,1,pos);
        for(var pos:wanted) if(!TICKETS.contains(pos)) chunks.addRegionTicket(RIDE_TICKET,pos,1,pos);
        TICKETS.clear();TICKETS.addAll(wanted);
    }
    private static void release(int service) {
        for(int i=0;i<CARS[service].length;i++) {
            if(CARS[service][i]!=null && !CARS[service][i].isRemoved() && !CARS[service][i].isVehicle()) CARS[service][i].discard();
            CARS[service][i]=null;
        }
    }
    private static void updateGates(ServerLevel level) {
        boolean[] openStations=new boolean[CityRingLine.STATIONS.size()];
        for(int service=0;service<CARS.length;service++) {
            var state=state(level,service);
            if(state.station()>=0 && state.boarding()) openStations[state.station()]=true;
        }
        for(int station=0;station<CityRingLine.STATIONS.size();station++)
            for(int role=0;role<3;role++) for(int side:new int[]{-3,3}) for(int delta=-2;delta<=2;delta++) {
                var s=CityRingLine.STATIONS.get(station);var base=CityRingTransit.bay(station,role,side,CityRingLine.RAIL_Y);
                var lower=base.offset(s.horizontal()?delta:0,0,s.horizontal()?0:delta);
                if(!level.hasChunkAt(lower)) continue;
                for(int y=0;y<=1;y++) {
                    var pos=lower.above(y);var block=level.getBlockState(pos);
                    boolean open=openStations[station];
                    if(block.is(CityPlatformGate.GATE.get()) && block.getValue(DoorBlock.OPEN)!=open)
                        level.setBlock(pos,block.setValue(DoorBlock.OPEN,open),2);
                }
            }
    }
    private static void showStatus(ServerLevel level) {
        for(var player:level.players()) {
            TrainCarEntity vehicle=player.getVehicle() instanceof TrainCarEntity car && car.ring()?car:null;
            boolean rider=vehicle!=null;
            int station=Math.abs(player.getY()-CityRingLine.RAIL_Y)<4
                    ?CityRingTransit.stationNear(player.getBlockX(),player.getBlockZ(),50):-1;
            if(!rider && station<0) { LAST_CUE.remove(player);continue; }
            var state=rider?state(level,vehicle):null;
            if(rider && state==null) { LAST_CUE.remove(player);continue; }
            if(!rider) {
                for(int service=0;service<CARS.length;service++) {
                    var candidate=state(level,service);
                    int relevant=candidate.station()>=0?candidate.station():candidate.phase()==TrainLine.Phase.DEPARTING
                            ?Math.floorMod(candidate.next()-candidate.direction(),CityRingLine.STATIONS.size()):candidate.next();
                    if(station==relevant && candidate.phase()!=TrainLine.Phase.CRUISE) {state=candidate;break;}
                }
                if(state==null) state=state(level);
            }
            int relevant=state.station()>=0?state.station():state.phase()==TrainLine.Phase.DEPARTING
                    ?Math.floorMod(state.next()-state.direction(),CityRingLine.STATIONS.size()):state.next();
            boolean cueRelevant=rider || station==relevant && state.phase()!=TrainLine.Phase.CRUISE;
            if(cueRelevant) {
                var cue=new Cue(relevant,state.phase());
                if(!cue.equals(LAST_CUE.put(player,cue))) {
                    float pitch=TrainPresentation.cuePitch(state.phase());
                    if(pitch>0) player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(),SoundSource.BLOCKS,.6f,pitch);
                }
            } else LAST_CUE.remove(player);
            Component message;
            if(rider || station==state.station()) {
                message=state.station()<0?Component.translatable("ac.train.next",name(state.next()),(int)Math.ceil(state.secondsLeft()))
                        :state.boarding()?Component.translatable("ac.train.boarding",name(state.station()),name(state.next()),(int)Math.ceil(state.secondsLeft()))
                        :state.phase()==TrainLine.Phase.CLOSING?Component.translatable("ac.train.closing",name(state.next()),(int)Math.ceil(state.secondsLeft()))
                        :Component.translatable("ac.train.arrived",name(state.station()));
            } else message=Component.translatable("ac.train.eta",name(station),
                    (int)Math.ceil(CityRingLine.arrivalIn(station,level.getGameTime()/20.0+offset)));
            player.displayClientMessage(message,true);
        }
    }
    static int panelStation(BlockPos pos) {
        return pos.getY()==CityRingLine.RAIL_Y?CityRingTransit.stationNear(pos.getX(),pos.getZ(),50):-1;
    }
    static int panelRole(int station,BlockPos pos) {
        if(station<0 || pos.getY()!=CityRingLine.RAIL_Y) return -1;
        for(int role=0;role<3;role++) for(int side:new int[]{-9,9})
            if(pos.equals(CityRingTransit.bay(station,role,side,CityRingLine.RAIL_Y))) return role;
        return -1;
    }
    static TrainCarEntity panelCar(ServerLevel level,int station,int role) {
        if(role<0 || role>=3) return null;
        for(int service=0;service<CARS.length;service++)
            if(state(level,service).station()==station) return CARS[service][role];
        return null;
    }
    static boolean tryBoard(ServerPlayer player,TrainCarEntity car) {
        var level=player.serverLevel();
        if(!level.dimension().equals(TrainManager.CITY) || player.isSpectator() || player.isPassenger() || !player.isAlive()
                || !player.onGround() || car.level()!=level || !car.isAlive() || serviceOf(car)<0) return false;
        var state=state(level,car);
        if(!state.boarding() || state.station()<0) {player.displayClientMessage(Component.translatable("ac.train.wait_board"),true);return false;}
        var stop=CityRingLine.STATIONS.get(state.station());var center=CityRingTransit.bay(state.station(),car.role(),0,CityRingLine.RAIL_Y);
        double along=stop.horizontal()?Math.abs(player.getX()-center.getX()-.5):Math.abs(player.getZ()-center.getZ()-.5);
        double cross=stop.horizontal()?player.getZ()-stop.z():player.getX()-stop.x();
        if(along>7 || Math.abs(cross)<2.5 || Math.abs(cross)>9 || Math.abs(player.getY()-CityRingLine.RAIL_Y)>.65) {
            player.displayClientMessage(Component.translatable("ac.train.board_platform"),true);return false;
        }
        if(!car.hasSeat()) {player.displayClientMessage(Component.translatable("ac.train.full"),true);return false;}
        return car.board(player,cross<0);
    }
    static Vec3 exit(TrainCarEntity car,LivingEntity rider) {
        if(!(car.level() instanceof ServerLevel level) || !level.dimension().equals(TrainManager.CITY)) return null;
        var state=state(level,car);if(state==null)return null;
        int station=state.station();if(station<0)return null;
        var stop=CityRingLine.STATIONS.get(station);int side=car.preferredNorth(rider)?-1:1;
        for(int cross:new int[]{5*side,-5*side,6*side,-6*side}) for(int d:new int[]{0,-1,1,-2,2,-3,3}) {
            var base=CityRingTransit.bay(station,car.role(),cross,CityRingLine.RAIL_Y);
            var pos=base.offset(stop.horizontal()?d:0,0,stop.horizontal()?0:d);
            if(!level.hasChunkAt(pos) || !level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)) continue;
            Vec3 safe=DismountHelper.findSafeDismountLocation(rider.getType(),level,pos,true);
            if(safe!=null && Math.abs(safe.y-CityRingLine.RAIL_Y)<.01)return safe;
        }
        return null;
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        for(var cars:CARS) java.util.Arrays.fill(cars,null);TICKETS.clear();LAST_CUE.clear();offset=0;
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        int last=CityRingLine.STATIONS.size()-1;
        event.getDispatcher().register(Commands.literal("academy_ring").requires(s->s.hasPermission(2))
                .then(Commands.literal("goto").then(Commands.argument("station",IntegerArgumentType.integer(0,last)).executes(c->{
                    var player=c.getSource().getPlayerOrException();var level=c.getSource().getServer().getLevel(TrainManager.CITY);
                    if(level==null || player.isPassenger())return 0;
                    var target=CityRingTransit.bay(IntegerArgumentType.getInteger(c,"station"),1,-8,CityRingLine.RAIL_Y);
                    player.teleportTo(level,target.getX()+.5,target.getY(),target.getZ()+.5,player.getYRot(),0);return 1;
                })))
                .then(Commands.literal("arrive").then(Commands.argument("station",IntegerArgumentType.integer(0,last))
                        .then(Commands.argument("seconds",DoubleArgumentType.doubleArg(0,600)).executes(c->{
                            var level=c.getSource().getServer().getLevel(TrainManager.CITY);if(level==null || occupied())return 0;
                            int station=IntegerArgumentType.getInteger(c,"station");double delay=DoubleArgumentType.getDouble(c,"seconds");
                            offset=CityRingLine.dwellStart(station)-level.getGameTime()/20.0-delay;
                            c.getSource().sendSuccess(()->Component.translatable("ac.train.eta",name(station),(int)Math.ceil(delay)),false);return 1;
                        }))))
                .then(Commands.literal("arrive_reverse").then(Commands.argument("station",IntegerArgumentType.integer(0,last))
                        .then(Commands.argument("seconds",DoubleArgumentType.doubleArg(0,600)).executes(c->{
                            var level=c.getSource().getServer().getLevel(TrainManager.CITY);if(level==null || occupied())return 0;
                            int station=IntegerArgumentType.getInteger(c,"station");double delay=DoubleArgumentType.getDouble(c,"seconds");
                            offset=CityRingLine.dwellStart(station,false)-level.getGameTime()/20.0-delay;
                            c.getSource().sendSuccess(()->Component.translatable("ac.train.eta",name(station),(int)Math.ceil(delay)),false);return 1;
                        }))))
                .then(Commands.literal("board").executes(c->{
                    var player=c.getSource().getPlayerOrException();TrainCarEntity best=null;
                    for(var cars:CARS) for(var car:cars) if(car!=null && car.isAlive() && car.level()==player.level()
                            && (best==null || car.distanceToSqr(player)<best.distanceToSqr(player)))best=car;
                    return best!=null && tryBoard(player,best)?1:0;
                })));
    }
}
