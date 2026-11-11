package cn.academy.port.city;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid="academy")
public final class CityElevators {
    private static final String TRIP="academy:city_lift_trip";
    private static final Set<Entity> RESCUING=Collections.newSetFromMap(new IdentityHashMap<>());
    private CityElevators(){}
    public static void open(Player player,BlockPos at){
        if(!(player instanceof ServerPlayer p)||!p.isAlive()||p.isSpectator()||p.distanceToSqr(Vec3.atCenterOf(at))>25||!p.serverLevel().hasChunkAt(at))return;
        CityElevatorController c=null;int floor=-1;
        if(p.serverLevel().getBlockEntity(at)instanceof CityElevatorController direct){c=direct;floor=0;}
        else if(p.serverLevel().getChunkSource().getGenerator()instanceof CityGreyboxGenerator g){
            var group=g.interiorAt(at.getX(),at.getZ());if(group!=null)for(var plan:group.parts())if(plan.lift!=null){int f=plan.floorIndex(at.getY());if(f>=0&&(plan.lift.panelAt(at)||isGate(plan.lift,at,f))&&p.serverLevel().hasChunkAt(plan.lift.controller())&&p.serverLevel().getBlockEntity(plan.lift.controller())instanceof CityElevatorController found){c=found;floor=f;break;}}
        }
        if(c==null||!c.configured())p.displayClientMessage(net.minecraft.network.chat.Component.translatable("city.elevator.fault"),true);
        else {c.request(floor);CityElevatorNetwork.open(p,c,floor);}
    }
    private static boolean isGate(CityInteriorPlan.Lift l,BlockPos p,int f){return p.getZ()==l.z()&&p.getX()>=l.x()+1&&p.getX()<=l.x()+2&&p.getY()>=l.floors()[f]&&p.getY()<=l.floors()[f]+2;}
    public static boolean validExit(Level w,Vec3 at,LivingEntity rider){
        if(w==null||!Double.isFinite(at.x)||!Double.isFinite(at.y)||!Double.isFinite(at.z))return false;
        BlockPos p=BlockPos.containing(at);if(!w.hasChunkAt(p)||!w.hasChunkAt(p.below())||!w.hasChunkAt(p.above(2)))return false;
        double width=rider==null?.6:rider.getBbWidth(),height=rider==null?1.8:rider.getBbHeight();
        AABB box=new AABB(at.x-width/2,at.y+.001,at.z-width/2,at.x+width/2,at.y+height,at.z+width/2);
        if(w instanceof ServerLevel server)for(int x=net.minecraft.util.Mth.floor(box.minX)>>4;x<=net.minecraft.util.Mth.floor(box.maxX)>>4;x++)for(int z=net.minecraft.util.Mth.floor(box.minZ)>>4;z<=net.minecraft.util.Mth.floor(box.maxZ)>>4;z++)if(server.getChunkSource().getChunkNow(x,z)==null)return false;
        return !w.getBlockCollisions(rider,box).iterator().hasNext()&&w.getBlockState(p.below()).getCollisionShape(w,p.below()).max(net.minecraft.core.Direction.Axis.Y)>=.999&&w.getFluidState(p).isEmpty();
    }
    public static Optional<Vec3> safeExit(Level w,BlockPos origin,LivingEntity p){
        return safeExit(w,origin,p,4);
    }
    private static Optional<Vec3> safeExit(Level w,BlockPos origin,LivingEntity p,int maximumRadius){
        for(int radius=1;radius<=maximumRadius;radius++)for(int dy:new int[]{0,1,-1})for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;Vec3 at=new Vec3(origin.getX()+dx+.5,origin.getY()+dy,origin.getZ()+dz+.5);
            if(validExit(w,at,p))return Optional.of(at);
        }return Optional.empty();
    }
    public static void remember(Player p,CityElevatorController c,Vec3 exit){var t=new CompoundTag();t.putString("Dimension",p.level().dimension().location().toString());t.putDouble("X",exit.x);t.putDouble("Y",exit.y);t.putDouble("Z",exit.z);t.putLong("Controller",c.getBlockPos().asLong());p.getPersistentData().put(TRIP,t);}
    public static boolean rescue(LivingEntity p,CityElevatorController c){
        Optional<Vec3> dest=Optional.empty();if(c!=null&&c.boarding())dest=c.exit(p);
        if(dest.isEmpty()&&p instanceof Player player&&player.getPersistentData().contains(TRIP)){
            var t=player.getPersistentData().getCompound(TRIP);if(t.getString("Dimension").equals(player.level().dimension().location().toString())){
                var v=new Vec3(t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z"));if(validExit(p.level(),v,p))dest=Optional.of(v);else dest=safeExit(p.level(),BlockPos.containing(v),p,32);
            }
        }
        if(dest.isEmpty())dest=safeExit(p.level(),p.blockPosition(),p);
        if(dest.isEmpty()&&c!=null){for(int f=0;f<c.floors().length;f++)if(validExit(p.level(),c.landing(f),p)){dest=Optional.of(c.landing(f));break;}}
        if(dest.isPresent()){
            RESCUING.add(p);try{p.stopRiding();Vec3 at=dest.get();if(p instanceof ServerPlayer s)s.teleportTo(at.x,at.y,at.z);else p.setPos(at);p.fallDistance=0;if(p instanceof Player) p.getPersistentData().remove(TRIP);}finally{RESCUING.remove(p);}
            return true;
        }
        return false;
    }
    @SubscribeEvent public static void dismount(EntityMountEvent e){
        if(!e.isDismounting()||!(e.getEntityBeingMounted()instanceof CityElevatorEntity car)||!(e.getEntityMounting()instanceof ServerPlayer p)||RESCUING.contains(p)||!p.isAlive()||p.isRemoved()||p.level()!=car.level())return;
        if(p.hasDisconnected())return;
        var c=car.owner();if(c==null||c.exit(p).isEmpty()){
            e.setCanceled(true);p.displayClientMessage(net.minecraft.network.chat.Component.translatable("city.elevator.moving"),true);
        }else {p.fallDistance=0;p.getPersistentData().remove(TRIP);}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){CityElevatorNetwork.forget(e.getEntity());if(e.getEntity().getVehicle()instanceof CityElevatorEntity car)rescue(e.getEntity(),car.owner());}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity().getPersistentData().contains(TRIP)&&!rescue(e.getEntity(),null)&&e.getEntity()instanceof ServerPlayer p){
        var cabin=CityInteriorContent.CABIN.get().create(p.serverLevel());if(cabin!=null){cabin.setPos(p.position());cabin.controller(new BlockPos(0,-2048,0));cabin.phase(CityElevatorController.Phase.FAULT.ordinal());if(p.serverLevel().addFreshEntity(cabin))p.startRiding(cabin);}
    }}
    private static void detach(Player p){
        if(!(p.getVehicle()instanceof CityElevatorEntity))return;
        // Vanilla can retain a dead/cross-dimensional rider until a later respawn or transfer step.
        // Release capacity immediately and retain the already chosen dimension destination/death point.
        Vec3 at=p.position();RESCUING.add(p);try{p.stopRiding();if(p instanceof ServerPlayer s&&p.isAlive())s.teleportTo(at.x,at.y,at.z);else p.setPos(at);p.fallDistance=0;}finally{RESCUING.remove(p);}
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){detach(e.getEntity());CityElevatorNetwork.forget(e.getEntity());e.getEntity().getPersistentData().remove(TRIP);}
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void death(LivingDeathEvent e){if(!e.isCanceled()&&e.getEntity()instanceof Player p){detach(p);CityElevatorNetwork.forget(p);p.getPersistentData().remove(TRIP);}}
}
