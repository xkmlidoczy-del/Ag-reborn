/* AcademyCraft 1.0.7 terminal/Syncs modern sender-bound transport. GPLv3. See NOTICE. */
package cn.academy.port.terminal;

import cn.academy.port.AcademyNetwork;
import cn.academy.port.tutorial.TutorialNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** No client namespace links. Only the real item use path installs anything. */
public final class TerminalNetwork {
    private static final java.util.Map<ServerPlayer,Long> URBAN_LOCATE_COOLDOWN=new java.util.WeakHashMap<>();
    public record FrequencyRequest(BlockPos target,BlockPos selected,String password,String kind) implements CustomPacketPayload {
        public static final Type<FrequencyRequest> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","terminal_frequency"));
        public static final StreamCodec<RegistryFriendlyByteBuf,FrequencyRequest> CODEC=StreamCodec.of((wire,r)->{wire.writeBlockPos(r.target);wire.writeBlockPos(r.selected);wire.writeUtf(r.password,128);wire.writeUtf(r.kind,32);},wire->new FrequencyRequest(wire.readBlockPos(),wire.readBlockPos(),wire.readUtf(128),wire.readUtf(32)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static boolean valid(ServerPlayer player){return player!=null&&!player.isRemoved()&&player.isAlive()&&!player.isSpectator()&&player.serverLevel().getServer().isSameThread();}
    private static void send(ServerPlayer player,String kind,String app){
        var tag=TerminalStorage.encode(TerminalStorage.get(player));tag.putString("kind",kind);if(app!=null)tag.putString("app",app);
        if ("terminal_app".equals(kind) && "urban_communication".equals(app)) {
            var progress=cn.academy.port.story.UrbanStoryStorage.read(player);
            tag.putBoolean("urban_valid",progress.isPresent());
            progress.ifPresent(value->tag.put("urban_story",cn.academy.port.story.UrbanStoryStorage.encode(value)));
        }
        PacketDistributor.sendToPlayer(player,new AcademyNetwork.ClientData(tag));
    }
    public static void sync(ServerPlayer player){send(player,"terminal_state",null);}
    public static void installed(ServerPlayer player,String app){send(player,app==null?"terminal_installed":"terminal_app_installed",app);}
    public static void installEffect(ServerPlayer player){send(player,"terminal_install_effect",null);}
    public static void requestOpen(){PacketDistributor.sendToServer(new AcademyNetwork.Request("terminal_open",""));}
    public static void requestClose(){PacketDistributor.sendToServer(new AcademyNetwork.Request("terminal_close",""));}
    public static void requestApp(String app){PacketDistributor.sendToServer(new AcademyNetwork.Request("terminal_app",app));}
    public static void requestUrbanLocation(){PacketDistributor.sendToServer(new AcademyNetwork.Request("terminal_urban_locate",""));}
    public static void requestFrequency(BlockPos target,BlockPos selected,String password,String kind){PacketDistributor.sendToServer(new FrequencyRequest(target,selected,password,kind));}
    public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(FrequencyRequest.TYPE,FrequencyRequest.CODEC,(request,context)->{if(context.player() instanceof ServerPlayer player)TerminalFrequencySessions.request(player,request);});}
    public static boolean request(ServerPlayer player,AcademyNetwork.Request request){
        if(!valid(player)||request==null||request.action()==null||request.value()==null)return false;
        if(!request.action().equals("terminal_open")&&!request.action().equals("terminal_app")&&!request.action().equals("terminal_close")&&!request.action().equals("terminal_urban_locate"))return false;
        if(!request.action().equals("terminal_app")&&!request.value().isEmpty())return false;
        var state=TerminalStorage.get(player);
        if(!state.terminalInstalled()){player.sendSystemMessage(Component.translatable("ac.terminal.notinstalled"));return false;}
        if(request.action().equals("terminal_urban_locate"))return locateUrban(player,state);
        if(request.action().equals("terminal_close")){TerminalFrequencySessions.remove(player);return true;}
        if(request.action().equals("terminal_open")){TerminalFrequencySessions.remove(player);send(player,"terminal_open",null);return true;}
        String app=request.value();if(!state.isInstalled(app))return false;TerminalFrequencySessions.remove(player);
        if(app.equals("tutorial")){TutorialNetwork.open(player);return true;}
        if(app.equals("skill_tree"))AcademyNetwork.sync(player);
        send(player,"terminal_app",app);return true;
    }
    /** Bounded server-side search, no client-supplied coordinates, rate limited per player. */
    private static boolean locateUrban(ServerPlayer player,TerminalState terminal){
        if(!terminal.isInstalled("urban_communication"))return false;
        var tag=new CompoundTag();tag.putString("kind","terminal_urban_location");
        if(!player.serverLevel().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)){
            tag.putString("status","unavailable_dimension");
        }else{
            long now=player.serverLevel().getGameTime(),last=URBAN_LOCATE_COOLDOWN.getOrDefault(player,Long.MIN_VALUE);
            if(last!=Long.MIN_VALUE&&now>=last&&now-last<100)tag.putString("status","locate_busy");
            else{
                URBAN_LOCATE_COOLDOWN.put(player,now);
                var progress=cn.academy.port.story.UrbanStoryStorage.read(player);
                if(progress.isEmpty())tag.putString("status","unavailable");
                else{
                    boolean campus=progress.get().chapter()==cn.academy.port.story.UrbanStoryProgress.Chapter.BROADCAST
                            &&!progress.get().completed(cn.academy.port.story.UrbanStoryProgress.Objective.BROADCAST_LEAD);
                    boolean facility=progress.get().chapter()==cn.academy.port.story.UrbanStoryProgress.Chapter.PROTOCOL
                            || progress.get().chapter()==cn.academy.port.story.UrbanStoryProgress.Chapter.CALIBRATOR;
                    var key=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.STRUCTURE,
                            ResourceLocation.fromNamespaceAndPath("academy",campus?"urban_campus":facility?"urban_facility":"urban_station"));
                    BlockPos pos=player.serverLevel().findNearestMapStructure(key,player.blockPosition(),4,false);
                    if(pos==null)tag.putString("status","not_found");
                    else{tag.putString("status","located");tag.putInt("x",pos.getX());tag.putInt("z",pos.getZ());}
                }
            }
        }
        PacketDistributor.sendToPlayer(player,new AcademyNetwork.ClientData(tag));return true;
    }
    static void frequencyReply(ServerPlayer player,FrequencyRequest request,BlockPos selected,boolean accepted,String name,String status){
        var tag=new CompoundTag();tag.putString("kind","terminal_frequency");tag.putString("action",request.kind());tag.putLong("pos",request.target().asLong());tag.putLong("selected",request.selected().asLong());tag.putBoolean("accepted",accepted);tag.putString("name",name);tag.putString("status",status);
        PacketDistributor.sendToPlayer(player,new AcademyNetwork.ClientData(tag));
    }
    private TerminalNetwork(){}
}
