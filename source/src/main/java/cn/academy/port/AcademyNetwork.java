package cn.academy.port;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.function.Consumer;
public final class AcademyNetwork {
    public static Consumer<ClientData> clientReceiver=data->{};
    public record Request(String action,String value) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((buf,p)->{buf.writeUtf(p.action,32);buf.writeUtf(p.value,64);},buf->new Request(buf.readUtf(32),buf.readUtf(64)));
        public Type<? extends CustomPacketPayload> type() {return TYPE;}
    }
    public record ClientData(CompoundTag data) implements CustomPacketPayload {
        public static final Type<ClientData> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","client_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf,ClientData> CODEC=StreamCodec.of((buf,p)->buf.writeNbt(p.data),buf->new ClientData(buf.readNbt()));
        public Type<? extends CustomPacketPayload> type() {return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("1");registrar.playToServer(Request.TYPE,Request.CODEC,(request,ctx)->{if(ctx.player() instanceof ServerPlayer player) {if(request.action().startsWith("terminal_"))cn.academy.port.terminal.TerminalNetwork.request(player,request);else if(request.action().startsWith("tutorial_"))cn.academy.port.tutorial.TutorialNetwork.request(player,request);else if(request.action().startsWith("machine_"))cn.academy.port.machine.MachineDeveloperSessions.request(player,request);else AcademyGameplay.requestFromClient(player,request);}});
        registrar.playToClient(ClientData.TYPE,ClientData.CODEC,(data,ctx)->clientReceiver.accept(data));
    }
    public static CompoundTag encodeActivationEvent(int entity,boolean active){var tag=new CompoundTag();tag.putString("kind","activation_event");tag.putInt("entity",entity);tag.putBoolean("active",active);return tag;}
    public static Boolean activationEventState(CompoundTag tag,int entity){
        return tag!=null&&tag.getString("kind").equals("activation_event")&&tag.contains("entity",net.minecraft.nbt.Tag.TAG_INT)&&tag.getInt("entity")==entity&&tag.contains("active",net.minecraft.nbt.Tag.TAG_BYTE)?tag.getBoolean("active"):null;
    }
    public static void activationEvent(ServerPlayer player,boolean active){PacketDistributor.sendToPlayer(player,new ClientData(encodeActivationEvent(player.getId(),active)));}
    public static void sync(ServerPlayer player) {var state=AbilityStorage.get(player);var data=AbilityStorage.encode(state);data.putLong("single_key_owner_epoch",cn.academy.port.core.LegacySingleKeyOwnerEpoch.current(player,player.connection,player.serverLevel(),state));data.put("skill_configuration",SkillCatalog.configurationSnapshot());data.putBoolean("interfering",state.interfering);data.putString("kind","state");PacketDistributor.sendToPlayer(player,new ClientData(data));}
    public static void effect(ServerPlayer player,String effect,double length) {effect(player,effect,player.getEyePosition(),cn.academy.port.skill.ClassicRaytrace.direction(player),length);}
    public static void effect(ServerPlayer player,String effect,Vec3 origin,Vec3 direction,double length) {
        sendEffect(player,effect,origin,direction,length,false,null);
    }
    public static void railgun(ServerPlayer player,Vec3 origin,Vec3 direction,double length,cn.academy.port.skill.RailgunVisualContact contact) {
        sendEffect(player,"railgun",origin,direction,length,true,contact);
    }
    private static void sendEffect(ServerPlayer player,String effect,Vec3 origin,Vec3 direction,double length,
                                   boolean witnessedRailgun,cn.academy.port.skill.RailgunVisualContact contact) {
        var tag=new CompoundTag();tag.putString("kind",effect);tag.putInt("entity",player.getId());tag.putDouble("length",length);
        tag.putDouble("x",origin.x);tag.putDouble("y",origin.y);tag.putDouble("z",origin.z);tag.putDouble("dx",direction.x);tag.putDouble("dy",direction.y);tag.putDouble("dz",direction.z);
        if(witnessedRailgun)tag.putString("shot_dimension",player.serverLevel().dimension().location().toString());
        if(contact!=null){
            tag.putDouble("block_contact",contact.distance());
            tag.putInt("block_face",contact.face().get3DDataValue());
        }
        double audience=effect.equals("railgun")?Math.max(32,Math.min(128,length+16)):32;
        PacketDistributor.sendToPlayersNear(player.serverLevel(),null,origin.x,origin.y,origin.z,audience,new ClientData(tag));
    }
}
