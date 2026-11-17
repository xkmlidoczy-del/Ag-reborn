package cn.academy.port.city;

import cn.academy.port.train.NorthSouthTrainManager;
import cn.academy.port.train.TrainCarEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Earn transport-contract credit by actually boarding and alighting at different service stops. */
@EventBusSubscriber(modid="academy")
public final class CityRideReceipts {
    private record Ride(UUID vehicle,int from,long start){}
    private static final Map<UUID,Ride> RIDES=new HashMap<>();
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void mount(EntityMountEvent event){
        if(event.isCanceled()||!(event.getEntityMounting() instanceof ServerPlayer p)||!(event.getEntityBeingMounted() instanceof TrainCarEntity car)||!car.northSouth())return;
        var s=NorthSouthTrainManager.state(p.serverLevel());
        if(!event.isDismounting()){
            if(s.boarding()&&s.station()>=0)RIDES.put(p.getUUID(),new Ride(car.getUUID(),s.station(),p.level().getGameTime()));
        }else{
            var ride=RIDES.remove(p.getUUID());
            long now=p.level().getGameTime();
            if(ride!=null&&ride.vehicle.equals(car.getUUID())&&s.boarding()&&s.station()>=0&&s.station()!=ride.from&&p.isAlive()&&now>=ride.start&&now-ride.start>=20)
                CityErrands.rode(p,ride.from,s.station());
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){RIDES.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){RIDES.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void death(net.neoforged.neoforge.event.entity.living.LivingDeathEvent e){RIDES.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e){RIDES.clear();}
}
