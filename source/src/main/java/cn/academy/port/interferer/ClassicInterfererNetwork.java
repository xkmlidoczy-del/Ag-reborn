/* AcademyCraft1.0.7 AbilityInterf source channels with modern sender/menu guards. GPLv3. */
package cn.academy.port.interferer;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
public final class ClassicInterfererNetwork {
    public static Consumer<Snapshot> clientReceiver=value->{};
    public record Request(int menuId,UUID token,long sequence,String action,double range,boolean enabled,List<String> names) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","interferer_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((wire,r)->{wire.writeVarInt(r.menuId);wire.writeUUID(r.token);wire.writeVarLong(r.sequence);wire.writeUtf(r.action,32);wire.writeDouble(r.range);wire.writeBoolean(r.enabled);writeNames(wire,r.names);},wire->new Request(wire.readVarInt(),wire.readUUID(),wire.readVarLong(),wire.readUtf(32),wire.readDouble(),wire.readBoolean(),readNames(wire)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Snapshot(int menuId,UUID token,long sequence,String action,boolean periodic,double range,boolean enabled,List<String> names) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","interferer_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((wire,s)->{wire.writeVarInt(s.menuId);wire.writeUUID(s.token);wire.writeVarLong(s.sequence);wire.writeUtf(s.action,32);wire.writeBoolean(s.periodic);wire.writeDouble(s.range);wire.writeBoolean(s.enabled);writeNames(wire,s.names);},wire->new Snapshot(wire.readVarInt(),wire.readUUID(),wire.readVarLong(),wire.readUtf(32),wire.readBoolean(),wire.readDouble(),wire.readBoolean(),readNames(wire)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static Consumer<TileSnapshot> clientTileReceiver=value->{};
    public record TileSnapshot(BlockPos pos,double range,boolean enabled,List<String> names) implements CustomPacketPayload {
        public static final Type<TileSnapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","interferer_tile_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf,TileSnapshot> CODEC=StreamCodec.of((wire,s)->{wire.writeBlockPos(s.pos);wire.writeDouble(s.range);wire.writeBoolean(s.enabled);writeNames(wire,s.names);},wire->new TileSnapshot(wire.readBlockPos(),wire.readDouble(),wire.readBoolean(),readNames(wire)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private ClassicInterfererNetwork(){}
    private static void writeNames(RegistryFriendlyByteBuf wire,List<String> names){if(names.size()>ClassicInterfererRules.MAX_NAMES)throw new IllegalArgumentException("whitelist count");wire.writeVarInt(names.size());for(String name:names)wire.writeUtf(name,ClassicInterfererRules.MAX_NAME_LENGTH);}
    private static List<String> readNames(RegistryFriendlyByteBuf wire){int count=wire.readVarInt();if(count<0||count>ClassicInterfererRules.MAX_NAMES)throw new IllegalArgumentException("whitelist count");List<String> names=new ArrayList<>(count);for(int i=0;i<count;i++)names.add(wire.readUtf(ClassicInterfererRules.MAX_NAME_LENGTH));return List.copyOf(names);}
    public static void register(RegisterPayloadHandlersEvent event){var registrar=event.registrar("1");registrar.playToServer(Request.TYPE,Request.CODEC,(request,context)->{if(context.player() instanceof ServerPlayer player)handle(player,request);});registrar.playToClient(Snapshot.TYPE,Snapshot.CODEC,(snapshot,context)->clientReceiver.accept(snapshot));registrar.playToClient(TileSnapshot.TYPE,TileSnapshot.CODEC,(snapshot,context)->clientTileReceiver.accept(snapshot));}
    public static boolean open(ServerPlayer player,BlockPos pos){
        if(player==null||pos==null||!player.serverLevel().getServer().isSameThread()||!player.serverLevel().hasChunkAt(pos)||!(player.serverLevel().getBlockEntity(pos) instanceof ClassicAbilityInterfererBlockEntity tile))return false;
        var token=UUID.randomUUID();var probe=new ClassicAbilityInterfererMenu(0,pos,token,tile);
        if(!probe.stillValid(player)){player.sendSystemMessage(Component.translatable("ac.ability_interf.cantuse"));return false;}
        var result=player.openMenu(new SimpleMenuProvider((id,inventory,actor)->new ClassicAbilityInterfererMenu(id,pos,token,tile),Component.translatable("block.academy.ability_interferer")),wire->{wire.writeBlockPos(pos);wire.writeUUID(token);});
        if(result.isPresent()&&player.containerMenu instanceof ClassicAbilityInterfererMenu menu){send(player,menu,tile,"sync",true);return true;}return false;
    }
    public static boolean handle(ServerPlayer player,Request request){
        if(player==null||request==null||request.token==null||request.action==null||request.names==null||!Double.isFinite(request.range)||request.names.size()>ClassicInterfererRules.MAX_NAMES||request.names.stream().anyMatch(name->!ClassicInterfererRules.validName(name))||!player.serverLevel().getServer().isSameThread())return false;
        if(!(player.containerMenu instanceof ClassicAbilityInterfererMenu menu)||menu.containerId!=request.menuId||!menu.token().equals(request.token)||!menu.stillValid(player)||!(player.serverLevel().getBlockEntity(menu.sourcePos()) instanceof ClassicAbilityInterfererBlockEntity tile)||!menu.isFor(tile))return false;
        if(!Set.of("set_range","set_enabled","set_whitelist").contains(request.action)||!menu.next(request.sequence))return false;
        switch(request.action){case "set_range"->tile.setRange(request.range);case "set_enabled"->tile.setEnabled(request.enabled);case "set_whitelist"->tile.setWhitelist(request.names);default->throw new AssertionError();}
        send(player,menu,tile,request.action,false);return true;
    }
    private static void send(ServerPlayer player,ClassicAbilityInterfererMenu menu,ClassicAbilityInterfererBlockEntity tile,String action,boolean periodic){var snapshot=new Snapshot(menu.containerId,menu.token(),menu.sequence(),action,periodic,tile.range(),tile.enabled(),List.copyOf(tile.whitelist()));menu.acceptSnapshot(snapshot);PacketDistributor.sendToPlayer(player,snapshot);}
    public static void periodic(ServerLevel level,ClassicAbilityInterfererBlockEntity tile){PacketDistributor.sendToPlayersNear(level,null,tile.getBlockPos().getX()+.5,tile.getBlockPos().getY()+.5,tile.getBlockPos().getZ()+.5,ClassicInterfererRules.SYNC_RADIUS,new TileSnapshot(tile.getBlockPos(),tile.range(),tile.enabled(),List.copyOf(tile.whitelist())));for(var player:level.players())if(player.containerMenu instanceof ClassicAbilityInterfererMenu menu&&menu.isFor(tile)&&menu.stillValid(player)&&player.distanceToSqr(tile.getBlockPos().getX()+.5,tile.getBlockPos().getY()+.5,tile.getBlockPos().getZ()+.5)<ClassicInterfererRules.SYNC_RADIUS*ClassicInterfererRules.SYNC_RADIUS)send(player,menu,tile,"sync",true);}
}
