/* AcademyCraft1.0.7 WirelessPage/NodeNetworkProxy/MatrixNetProxy sender-bound adaptation. GPLv3. See NOTICE. */
package cn.academy.port.wireless;

import java.util.UUID;
import java.util.function.Consumer;
import cn.academy.port.fusion.ClassicFusorBlockEntity;
import cn.academy.port.fusion.ClassicFusorMenu;
import cn.academy.port.former.ClassicMetalFormerBlockEntity;
import cn.academy.port.former.ClassicMetalFormerMenu;
import cn.academy.port.machine.MachineDeveloperBlockEntity;
import cn.academy.port.machine.MachineDeveloperSessions;
import cn.academy.port.machine.MachineDevelopers;
import cn.academy.port.solar.ClassicSolarBlockEntity;
import cn.academy.port.solar.ClassicSolarMenu;
import cn.academy.port.solar.ClassicSolarGenerators;
import cn.academy.port.solar.ImagFluxGenerator;
import cn.academy.port.phasegen.ClassicPhaseGeneratorBlockEntity;
import cn.academy.port.phasegen.ClassicPhaseGeneratorMenu;
import cn.academy.port.wind.ClassicWindBaseBlockEntity;
import cn.academy.port.wind.ClassicWindMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** All names/passwords refer exclusively to fictional local game-world mod state. */
public final class ClassicWirelessProtocol {
    public static final UUID OPEN_TOKEN=new UUID(0,0);
    public static Consumer<Snapshot> clientReceiver=data->{};
    public record Request(int menuId,UUID token,String action,BlockPos target,String value,String password) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","wireless_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((wire,request)->{wire.writeVarInt(request.menuId);wire.writeUUID(request.token);wire.writeUtf(request.action,32);wire.writeBlockPos(request.target);wire.writeUtf(request.value,128);wire.writeUtf(request.password,128);},wire->new Request(wire.readVarInt(),wire.readUUID(),wire.readUtf(32),wire.readBlockPos(),wire.readUtf(128),wire.readUtf(128)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Snapshot(int menuId,UUID token,CompoundTag data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","wireless_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((wire,snapshot)->{wire.writeVarInt(snapshot.menuId);wire.writeUUID(snapshot.token);wire.writeNbt(snapshot.data);},wire->new Snapshot(wire.readVarInt(),wire.readUUID(),wire.readNbt()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private ClassicWirelessProtocol(){}
    public static void register(RegisterPayloadHandlersEvent event){var registrar=event.registrar("1");registrar.playToServer(Request.TYPE,Request.CODEC,(request,context)->{if(context.player() instanceof ServerPlayer player)handle(player,request);});registrar.playToClient(Snapshot.TYPE,Snapshot.CODEC,(snapshot,context)->clientReceiver.accept(snapshot));}
    private static BlockEntity source(ServerPlayer player,BlockPos pos){var level=player.serverLevel();return level.hasChunkAt(pos)?level.getBlockEntity(pos):null;}
    public static boolean reachable(ServerPlayer player,BlockPos pos,ClassicWirelessMenu.Kind kind){
        if(player==null||player.isRemoved()||!player.isAlive()||player.isSpectator()||pos==null||!player.serverLevel().hasChunkAt(pos)||!player.serverLevel().mayInteract(player,pos)||player.distanceToSqr(pos.getX(),pos.getY(),pos.getZ())>=64)return false;
        var tile=source(player,pos);if(tile==null||tile.isRemoved())return false;
        return switch(kind){case NODE->tile instanceof ClassicWirelessNodeBlockEntity node&&node.available();case MATRIX->tile instanceof ClassicWirelessMatrixBlockEntity matrix&&matrix.available();case USER->tile instanceof ImagFluxGenerator?player.serverLevel().getCapability(ClassicSolarGenerators.IMAG_FLUX,pos,Direction.UP)!=null:tile instanceof MachineDeveloperBlockEntity machine?machine.isOrigin()&&machine.available():player.serverLevel().getCapability(MachineDevelopers.IMAG_FLUX,pos,Direction.UP)!=null;};
    }
    public static boolean open(ServerPlayer player,BlockPos pos,ClassicWirelessMenu.Kind kind){
        if(!player.serverLevel().getServer().isSameThread()||!reachable(player,pos,kind))return false;
        var tile=source(player,pos);var container=kind==ClassicWirelessMenu.Kind.NODE||kind==ClassicWirelessMenu.Kind.MATRIX?(net.minecraft.world.Container)tile:new SimpleContainer(0);UUID token=UUID.randomUUID();
        Component title=Component.translatable(kind==ClassicWirelessMenu.Kind.NODE?"block.academy.wireless_node_"+((ClassicWirelessNodeBlockEntity)tile).nodeType().name().toLowerCase(java.util.Locale.ROOT):kind==ClassicWirelessMenu.Kind.MATRIX?"block.academy.wireless_matrix":"screen.academy.wireless.title");
        var provider=new SimpleMenuProvider((id,inventory,actor)->new ClassicWirelessMenu(id,inventory,pos,kind,token,container),title);
        var result=player.openMenu(provider,wire->{wire.writeBlockPos(pos);wire.writeVarInt(kind.ordinal());wire.writeUUID(token);});
        if(result.isPresent()&&player.containerMenu instanceof ClassicWirelessMenu menu){sendSnapshot(player,menu,"");return true;}return false;
    }
    private static boolean clean(String value){return value!=null&&value.length()<=128&&value.codePoints().noneMatch(Character::isISOControl);}
    public static boolean handle(ServerPlayer player,Request request){
        if(player==null||request==null||request.token==null||request.action==null||request.target==null||!clean(request.value)||!clean(request.password)||request.action.length()>32||!player.serverLevel().getServer().isSameThread())return false;
        if(request.action.equals("open_solar")){
            if(!OPEN_TOKEN.equals(request.token)||request.menuId!=player.containerMenu.containerId||!(player.containerMenu instanceof ClassicSolarMenu menu)||!menu.sourcePos().equals(request.target)||!menu.stillValid(player)||!(source(player,request.target) instanceof ClassicSolarBlockEntity solar)||!menu.isFor(solar))return false;
            return open(player,request.target,ClassicWirelessMenu.Kind.USER);
        }
        if(request.action.equals("open_windgen")){
            if(!OPEN_TOKEN.equals(request.token)||request.menuId!=player.containerMenu.containerId||!(player.containerMenu instanceof ClassicWindMenu menu)||!menu.sourcePos().equals(request.target)||!menu.stillValid(player)||!(source(player,request.target) instanceof ClassicWindBaseBlockEntity wind)||!menu.isFor(wind))return false;
            return open(player,request.target,ClassicWirelessMenu.Kind.USER);
        }
        if(request.action.equals("open_phasegen")){
            if(!OPEN_TOKEN.equals(request.token)||request.menuId!=player.containerMenu.containerId||!(player.containerMenu instanceof ClassicPhaseGeneratorMenu menu)||!menu.sourcePos().equals(request.target)||!menu.stillValid(player)||!(source(player,request.target) instanceof ClassicPhaseGeneratorBlockEntity phase)||!menu.isFor(phase))return false;
            return open(player,request.target,ClassicWirelessMenu.Kind.USER);
        }
        if(request.action.equals("open_receiver")){
            if(!OPEN_TOKEN.equals(request.token)||request.menuId!=player.containerMenu.containerId||!(player.containerMenu instanceof ClassicFusorMenu menu)||!menu.sourcePos().equals(request.target)||!menu.stillValid(player)||!(source(player,request.target) instanceof ClassicFusorBlockEntity fusor)||!menu.isFor(fusor))return false;
            return open(player,request.target,ClassicWirelessMenu.Kind.USER);
        }
        if(request.action.equals("open_former")){
            if(!OPEN_TOKEN.equals(request.token)||request.menuId!=player.containerMenu.containerId||!(player.containerMenu instanceof ClassicMetalFormerMenu menu)||!menu.sourcePos().equals(request.target)||!menu.stillValid(player)||!(source(player,request.target) instanceof ClassicMetalFormerBlockEntity former)||!menu.isFor(former))return false;
            return open(player,request.target,ClassicWirelessMenu.Kind.USER);
        }
        if(request.action.equals("open_machine")){
            if(!OPEN_TOKEN.equals(request.token)||request.menuId!=player.containerMenu.containerId||!MachineDeveloperSessions.matches(player,request.value,request.target))return false;
            MachineDeveloperSessions.close(player);return open(player,request.target,ClassicWirelessMenu.Kind.USER);
        }
        if(!(player.containerMenu instanceof ClassicWirelessMenu menu)||menu.containerId!=request.menuId||!menu.token().equals(request.token)||!menu.stillValid(player))return false;
        var tile=source(player,menu.sourcePos());var graph=ClassicWirelessSavedData.get(player.serverLevel()).graph();var own=ClassicWirelessSavedData.pos(menu.sourcePos());var target=ClassicWirelessSavedData.pos(request.target);
        boolean accepted=false;
        switch(request.action){
            case "refresh" -> accepted=true;
            case "return" -> {
                if(menu.kind()!=ClassicWirelessMenu.Kind.USER)break;
                if(tile instanceof cn.academy.port.bridge.ClassicEnergyBridgeBlockEntity){player.closeContainer();return true;}
                if(tile instanceof ClassicSolarBlockEntity solar){player.openMenu(new SimpleMenuProvider(solar::createMenu,solar.getDisplayName()),solar.getBlockPos());return true;}
                if(tile instanceof ClassicWindBaseBlockEntity wind){player.openMenu(new SimpleMenuProvider(wind::createMenu,wind.getDisplayName()),wire->{wire.writeBoolean(true);wire.writeBlockPos(wind.getBlockPos());});return true;}
                if(tile instanceof ClassicPhaseGeneratorBlockEntity phase){player.openMenu(new SimpleMenuProvider(phase::createMenu,phase.getDisplayName()),phase.getBlockPos());return true;}
                if(tile instanceof MachineDeveloperBlockEntity machine){player.closeContainer();return machine.use(player);}
                if(tile instanceof ClassicFusorBlockEntity fusor){player.openMenu(new SimpleMenuProvider(fusor::createMenu,fusor.getDisplayName()),fusor.getBlockPos());return true;}
                if(tile instanceof ClassicMetalFormerBlockEntity former){player.openMenu(new SimpleMenuProvider(former::createMenu,former.getDisplayName()),former.getBlockPos());return true;}break;
            }
            case "link" -> {
                if(menu.kind()==ClassicWirelessMenu.Kind.NODE)accepted=graph.linkNode(target,own,request.password);
                else if(menu.kind()==ClassicWirelessMenu.Kind.USER)accepted=tile instanceof ImagFluxGenerator?graph.linkGenerator(target,own,request.password,true):graph.linkReceiver(target,own,request.password,true);
            }
            case "unlink" -> {if(menu.kind()==ClassicWirelessMenu.Kind.NODE){graph.unlinkNode(own);accepted=true;}else if(menu.kind()==ClassicWirelessMenu.Kind.USER){if(tile instanceof ImagFluxGenerator)graph.unlinkGenerator(own);else graph.unlinkReceiver(own);accepted=true;}}
            case "node_name" -> {if(tile instanceof ClassicWirelessNodeBlockEntity node&&node.ownedBy(player)){node.setNodeName(request.value);accepted=true;}}
            case "node_password" -> {if(tile instanceof ClassicWirelessNodeBlockEntity node&&node.ownedBy(player)){node.setPassword(request.password);accepted=true;}}
            case "matrix_init" -> {if(tile instanceof ClassicWirelessMatrixBlockEntity matrix&&matrix.ownedBy(player))accepted=graph.createNetwork(own,request.value,request.password);}
            case "matrix_ssid" -> {if(tile instanceof ClassicWirelessMatrixBlockEntity matrix&&matrix.ownedBy(player))accepted=graph.renameNetwork(own,request.value);}
            case "matrix_password" -> {if(tile instanceof ClassicWirelessMatrixBlockEntity matrix&&matrix.ownedBy(player))accepted=graph.changeNetworkPassword(own,request.password);}
            case "matrix_reset" -> {if(tile instanceof ClassicWirelessMatrixBlockEntity matrix&&matrix.ownedBy(player)){graph.removeNetwork(own);accepted=true;}}
            default -> {}
        }
        sendSnapshot(player,menu,accepted?"accepted":"rejected");return accepted;
    }
    private static CompoundTag candidate(BlockPos pos,String name,boolean encrypted){var tag=new CompoundTag();tag.putLong("pos",pos.asLong());tag.putString("name",name);tag.putBoolean("encrypted",encrypted);return tag;}
    private static BlockPos blockPos(ClassicWirelessGraph.Pos pos){return new BlockPos(pos.x(),pos.y(),pos.z());}
    public static void sendSnapshot(ServerPlayer player,ClassicWirelessMenu menu,String status){
        if(player.containerMenu!=menu||!menu.stillValid(player))return;
        var tile=source(player,menu.sourcePos());var data=ClassicWirelessSavedData.get(player.serverLevel());var graph=data.graph();var own=ClassicWirelessSavedData.pos(menu.sourcePos());var tag=new CompoundTag();tag.putString("kind",menu.kind().name().toLowerCase(java.util.Locale.ROOT));tag.putLong("pos",menu.sourcePos().asLong());tag.putString("status",status);tag.putString("title_key",tile.getBlockState().getBlock().getDescriptionId());var candidates=new ListTag();
        if(tile instanceof ClassicWirelessNodeBlockEntity node){
            tag.putString("title",Component.translatable("block.academy.wireless_node_"+node.nodeType().name().toLowerCase(java.util.Locale.ROOT)).getString());tag.putString("owner",node.ownerName());tag.putBoolean("configure",node.ownedBy(player));tag.putString("node_name",node.getNodeName());tag.putDouble("energy",node.getEnergy());tag.putDouble("max_energy",node.getMaxEnergy());tag.putDouble("bandwidth",node.getBandwidth());tag.putDouble("range",node.getRange());tag.putInt("capacity",node.getCapacity());var connection=graph.connectionAt(own);tag.putInt("load",connection==null?0:connection.load());
            var linked=graph.networkAt(own);if(linked!=null)tag.put("linked",candidate(blockPos(linked.matrix()),linked.ssid(),graph.isNetworkEncrypted(linked.matrix())));
            for(var net:graph.nearbyNetworks(own,node.getRange(),20))if(linked==null||!linked.matrix().equals(net.matrix()))candidates.add(candidate(blockPos(net.matrix()),net.ssid(),graph.isNetworkEncrypted(net.matrix())));
        }else if(tile instanceof ClassicWirelessMatrixBlockEntity matrix){
            tag.putString("title",Component.translatable("block.academy.wireless_matrix").getString());tag.putString("owner",matrix.ownerName());tag.putBoolean("configure",matrix.ownedBy(player));tag.putDouble("bandwidth",matrix.getBandwidth());tag.putDouble("range",matrix.getRange());tag.putInt("capacity",matrix.getCapacity());var net=graph.networkAt(own);tag.putBoolean("initialized",net!=null);tag.putString("ssid",net==null?"":net.ssid());tag.putInt("load",net==null?0:net.load());tag.putDouble("energy",net==null?0:net.buffer());tag.putDouble("max_energy",ClassicWirelessGraph.BUFFER_MAX);
        }else{
            tag.putString("title",tile.getBlockState().getBlock().getName().getString());
            var linked=tile instanceof ImagFluxGenerator?graph.nodeForGenerator(own):graph.nodeForReceiver(own);
            if(linked!=null){var linkedTile=source(player,blockPos(linked));if(linkedTile instanceof ClassicWirelessNodeBlockEntity node)tag.put("linked",candidate(blockPos(linked),node.getNodeName(),!node.getPassword().isEmpty()));}
            for(var snapshot:graph.nearbyNodes(own,20,100)){if(linked!=null&&linked.equals(snapshot.node()))continue;var actual=source(player,blockPos(snapshot.node()));if(actual instanceof ClassicWirelessNodeBlockEntity node)candidates.add(candidate(blockPos(snapshot.node()),node.getNodeName(),!node.getPassword().isEmpty()));}
            if(tile instanceof cn.academy.port.bridge.ClassicEnergyBridgeBlockEntity bridge){tag.putDouble("energy",bridge.getEnergy());tag.putDouble("max_energy",bridge.getMaxEnergy());tag.putDouble("bandwidth",bridge.getBandwidth());tag.putBoolean("standalone",true);}
            else if(tile instanceof ClassicSolarBlockEntity solar){tag.putDouble("energy",solar.getEnergy());tag.putDouble("max_energy",1000);tag.putDouble("bandwidth",solar.getBandwidth());}
            else if(tile instanceof ClassicWindBaseBlockEntity wind){tag.putDouble("energy",wind.getEnergy());tag.putDouble("max_energy",20000);tag.putDouble("bandwidth",wind.getBandwidth());}
            else if(tile instanceof ClassicPhaseGeneratorBlockEntity phase){tag.putDouble("energy",phase.getEnergy());tag.putDouble("max_energy",6000);tag.putDouble("bandwidth",phase.getBandwidth());}
            else if(tile instanceof MachineDeveloperBlockEntity machine){tag.putDouble("energy",machine.battery().getEnergy());tag.putDouble("max_energy",machine.developerType().energy);tag.putDouble("bandwidth",machine.developerType().bandwidth);}
            else {var receiver=player.serverLevel().getCapability(MachineDevelopers.IMAG_FLUX,menu.sourcePos(),Direction.UP);if(receiver!=null){tag.putDouble("energy",receiver.getEnergy());tag.putDouble("max_energy",receiver.getMaxEnergy());tag.putDouble("bandwidth",receiver.getBandwidth());}}
        }
        tag.put("candidates",candidates);menu.acceptSnapshot(tag);PacketDistributor.sendToPlayer(player,new Snapshot(menu.containerId,menu.token(),tag));
    }
}
