package cn.academy.port.macro;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Result-only presentation: every cell names an actual successful server block change. */
@EventBusSubscriber(modid="academy")
public final class BeamImpactVfx {
    public static Consumer<Scene> receiver=v->{};
    public static Consumer<Scene> observer=v->{};
    public enum Style { BEAM, BLAST, PRESSURE }
    public record Cell(BlockPos position,int originalState){}
    private record Key(UUID owner,String skill,UUID cast,Vec3 centre){}
    private static final Map<ServerLevel,Map<Key,List<Cell>>> PENDING=new WeakHashMap<>();
    private BeamImpactVfx(){}
    public static void beam(ServerPlayer p,String skill,Vec3 from,Vec3 to,float radius,List<Cell> removed,List<Vec3> impacts){
        send(p,new Scene(UUID.randomUUID(),p.getId(),p.getUUID(),p.serverLevel().dimension().location(),skill,Style.BEAM,from,to,radius,p.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT?-1:1,p.getYHeadRot(),p.serverLevel().getGameTime(),50,List.copyOf(removed.stream().limit(256).toList()),List.copyOf(impacts.stream().limit(64).toList())));
    }
    public static void changed(ServerPlayer p,String skill,UUID cast,Vec3 centre,BlockPos at,BlockState original){
        if(!p.server.isSameThread()||p.hasDisconnected())return;
        var cells=PENDING.computeIfAbsent(p.serverLevel(),w->new LinkedHashMap<>()).computeIfAbsent(new Key(p.getUUID(),skill,cast,centre),k->new ArrayList<>());
        if(cells.size()<256)cells.add(new Cell(at.immutable(),Block.getId(original)));
    }
    @SubscribeEvent public static void flush(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
        for(var w:List.copyOf(PENDING.keySet())){
            if(w.getServer()!=event.getServer())continue;var groups=PENDING.remove(w);if(groups==null)continue;
            for(var entry:groups.entrySet()){
                var p=w.getServer().getPlayerList().getPlayer(entry.getKey().owner);var cells=entry.getValue();if(p==null||p.serverLevel()!=w||cells.isEmpty())continue;
                var centre=entry.getKey().centre;boolean blast=centre!=null;
                var from=blast?centre:p.getEyePosition();var to=blast?centre:cells.getFirst().position.getCenter();
                send(p,new Scene(entry.getKey().cast,p.getId(),p.getUUID(),w.dimension().location(),entry.getKey().skill,blast?Style.BLAST:Style.PRESSURE,from,to,1,1,p.getYRot(),w.getGameTime(),34,List.copyOf(cells),List.of()));
            }
        }
    }
    private static void send(ServerPlayer p,Scene scene){
        if(!p.server.isSameThread()||!p.isAlive()||p.hasDisconnected()||!MacroVfx.finite(scene.from)||!MacroVfx.finite(scene.to)||scene.from.distanceToSqr(scene.to)>192*192)return;
        observer.accept(scene);
        double reach=scene.style==Style.BEAM?72+scene.radius:160;
        for(var viewer:p.serverLevel().players())if(!viewer.hasDisconnected()&&MacroVfx.distanceSquared(viewer.getEyePosition(),scene.from,scene.to)<=reach*reach)PacketDistributor.sendToPlayer(viewer,scene);
    }
    public record Scene(UUID cast,int entity,UUID owner,ResourceLocation dimension,String skill,Style style,Vec3 from,Vec3 to,float radius,int hand,float yaw,long tick,int life,List<Cell> cells,List<Vec3> impacts)implements CustomPacketPayload{
        public static final Type<Scene> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","beam_impact_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Scene> CODEC=StreamCodec.of((b,v)->{
            b.writeUUID(v.cast);b.writeVarInt(v.entity);b.writeUUID(v.owner);b.writeResourceLocation(v.dimension);b.writeUtf(v.skill,64);b.writeEnum(v.style);b.writeVec3(v.from);b.writeVec3(v.to);b.writeFloat(v.radius);b.writeByte(v.hand);b.writeFloat(v.yaw);b.writeLong(v.tick);b.writeVarInt(v.life);
            b.writeVarInt(v.cells.size());for(var c:v.cells){b.writeBlockPos(c.position);b.writeVarInt(c.originalState);}b.writeVarInt(v.impacts.size());v.impacts.forEach(b::writeVec3);
        },b->{UUID cast=b.readUUID();int entity=b.readVarInt();UUID owner=b.readUUID();var dimension=b.readResourceLocation();String skill=b.readUtf(64);var style=b.readEnum(Style.class);var from=b.readVec3();var to=b.readVec3();float radius=b.readFloat();int hand=b.readByte();float yaw=b.readFloat();long tick=b.readLong();int life=b.readVarInt();
            int n=b.readVarInt();if(n<0||n>256)throw new IllegalArgumentException("terrain presentation count");var cells=new ArrayList<Cell>(n);for(int i=0;i<n;i++)cells.add(new Cell(b.readBlockPos(),b.readVarInt()));n=b.readVarInt();if(n<0||n>64)throw new IllegalArgumentException("impact presentation count");var impacts=new ArrayList<Vec3>(n);for(int i=0;i<n;i++)impacts.add(b.readVec3());return new Scene(cast,entity,owner,dimension,skill,style,from,to,radius,hand,yaw,tick,life,List.copyOf(cells),List.copyOf(impacts));});
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD) public static final class Network{
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("2").playToClient(Scene.TYPE,Scene.CODEC,(v,c)->c.enqueueWork(()->receiver.accept(v)));}
    }
    @SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e){PENDING.clear();}
}
