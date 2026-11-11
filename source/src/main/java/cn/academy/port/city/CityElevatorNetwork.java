package cn.academy.port.city;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** V1 controls send identifiers and choices only; cabin coordinates are never accepted from a client. */
@EventBusSubscriber(modid="academy")
public final class CityElevatorNetwork {
    public static final int CALL=0,SELECT=1,BOARD=2,EXIT=3,EMERGENCY=4,CLOSE=5,OPEN=6;
    public static Consumer<View> clientReceiver=v->{};
    public record View(BlockPos controller,long token,int[] floors,int current,int phase,boolean riding,int panel,String failure)implements CustomPacketPayload{
        public static final Type<View> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","city_lift_view_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf,View> CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.controller);b.writeLong(p.token);b.writeVarInt(p.floors.length);for(int y:p.floors)b.writeInt(y);b.writeInt(p.current);b.writeInt(p.phase);b.writeBoolean(p.riding);b.writeInt(p.panel);b.writeUtf(p.failure,80);},b->{BlockPos pos=b.readBlockPos();long token=b.readLong();int n=b.readVarInt();if(n<1||n>256)throw new IllegalArgumentException("Lift floor count");int[] fs=new int[n];for(int i=0;i<n;i++)fs[i]=b.readInt();return new View(pos,token,fs,b.readInt(),b.readInt(),b.readBoolean(),b.readInt(),b.readUtf(80));});
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Request(BlockPos controller,long token,int action,int floor)implements CustomPacketPayload{
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","city_lift_request_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.controller);b.writeLong(p.token);b.writeVarInt(p.action);b.writeInt(p.floor);},b->new Request(b.readBlockPos(),b.readLong(),b.readVarInt(),b.readInt()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private record Session(ServerLevel world,BlockPos controller,String identity,long token,int panel,long until){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    public static void register(RegisterPayloadHandlersEvent e){e.registrar("city-interiors-1")
        .playToClient(View.TYPE,View.CODEC,(p,c)->c.enqueueWork(()->clientReceiver.accept(p)))
        .playToServer(Request.TYPE,Request.CODEC,(p,c)->c.enqueueWork(()->{if(c.player()instanceof ServerPlayer s)request(s,p);}));}
    public static void open(ServerPlayer p,CityElevatorController c,int panel){
        if(!p.isAlive()||p.isSpectator()||p.level()!=c.getLevel()||!c.configured()||panel<0||panel>=c.floors().length)return;
        if((c.cabin()==null||p.getVehicle()!=c.cabin())&&p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(c.panel(panel)))>36&&p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(c.getBlockPos()))>25)return;
        long token=java.util.concurrent.ThreadLocalRandom.current().nextLong();var s=new Session(p.serverLevel(),c.getBlockPos(),c.identity(),token,panel,p.level().getGameTime()+1200);SESSIONS.put(p.getUUID(),s);send(p,c,s);
    }
    private static void send(ServerPlayer p,CityElevatorController c,Session s){PacketDistributor.sendToPlayer(p,new View(s.controller,s.token,c.floors(),c.current(),c.phase().ordinal(),(c.cabin()!=null&&p.getVehicle()==c.cabin()),s.panel,c.failure()));}
    public static boolean request(ServerPlayer p,Request r){
        if(r.action==OPEN&&r.token==0&&p.isAlive()&&!p.isSpectator()&&p.getVehicle()instanceof CityElevatorEntity cabin&&cabin.controller().equals(r.controller)){
            var owner=cabin.owner();if(owner==null)return CityElevators.rescue(p,null);open(p,owner,Math.max(0,owner.current()));return true;
        }
        Session s=SESSIONS.get(p.getUUID());if(s==null||s.token!=r.token||!s.controller.equals(r.controller)||s.world!=p.serverLevel()||s.until<p.level().getGameTime()||!p.isAlive()||p.isSpectator()||!p.serverLevel().hasChunkAt(r.controller))return false;
        if(!(p.serverLevel().getBlockEntity(r.controller)instanceof CityElevatorController c)||!s.identity.equals(c.identity()))return false;
        boolean rider=(c.cabin()!=null&&p.getVehicle()==c.cabin());if(!rider&&p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(c.panel(s.panel)))>36&&p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(c.getBlockPos()))>25)return false;
        boolean accepted=switch(r.action){
            case CALL->!rider&&r.floor==s.panel&&c.request(s.panel);
            case SELECT->rider&&c.request(r.floor);
            case BOARD->!rider&&c.board(p);
            case EXIT->{if(rider&&c.exit(p).isPresent()){p.stopRiding();yield true;}yield false;}
            case EMERGENCY->{if(rider){c.emergency();yield true;}yield false;}
            case CLOSE->{SESSIONS.remove(p.getUUID());yield true;}
            default->false;
        };
        if(!accepted&&r.action==BOARD)p.displayClientMessage(net.minecraft.network.chat.Component.translatable(c.cabin()!=null&&c.cabin().getPassengers().size()>=4?"city.elevator.full":"city.elevator.wait"),true);
        if(r.action!=CLOSE)send(p,c,s);return accepted;
    }
    public static void forget(Player p){SESSIONS.remove(p.getUUID());}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){if(e.getServer().getTickCount()%10!=0)return;
        for(var entry:List.copyOf(SESSIONS.entrySet())){
            ServerPlayer p=e.getServer().getPlayerList().getPlayer(entry.getKey());Session s=entry.getValue();
            if(p==null||p.serverLevel()!=s.world||!s.world.hasChunkAt(s.controller)){SESSIONS.remove(entry.getKey());continue;}
            if(!(s.world.getBlockEntity(s.controller)instanceof CityElevatorController c)||!s.identity.equals(c.identity())){SESSIONS.remove(entry.getKey());continue;}
            // A legitimate ride in a seventy-storey tower can outlast the idle panel timeout.
            if(p.isAlive()&&c.cabin()!=null&&p.getVehicle()==c.cabin()){s=new Session(s.world,s.controller,s.identity,s.token,s.panel,p.level().getGameTime()+1200);SESSIONS.put(entry.getKey(),s);}
            else if(s.until<p.level().getGameTime()){SESSIONS.remove(entry.getKey());continue;}
            send(p,c,s);
        }
    }
}
