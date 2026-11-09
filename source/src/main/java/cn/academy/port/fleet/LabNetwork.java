package cn.academy.port.fleet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class LabNetwork {
    public record Audio(int entity,int variant,String phase,double x,double y,double z,long tick,long action,long seed) implements CustomPacketPayload {
        public static final Type<Audio> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","fleet_audio"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Audio> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.entity);b.writeByte(p.variant);b.writeUtf(p.phase,32);b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);b.writeLong(p.tick);b.writeLong(p.action);b.writeLong(p.seed);},b->new Audio(b.readVarInt(),b.readByte(),b.readUtf(32),b.readDouble(),b.readDouble(),b.readDouble(),b.readLong(),b.readLong(),b.readLong()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Request(String action, float a, float b) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy","fleet_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((buf,p)->{buf.writeUtf(p.action,16);buf.writeFloat(p.a);buf.writeFloat(p.b);},buf->new Request(buf.readUtf(16),buf.readFloat(),buf.readFloat()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Audio.TYPE,Audio.CODEC,(packet,ctx)->ctx.enqueueWork(()->cn.academy.port.fleet.client.FleetAudio.receive(packet)));
        event.registrar("1").playToServer(Request.TYPE, Request.CODEC, (request,ctx)->{
            ctx.enqueueWork(()->{if(ctx.player() instanceof ServerPlayer p&&p.isAlive()&&!p.isSpectator())LabGameplay.request(p,request);});
        });
    }
}
