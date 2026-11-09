package cn.academy.port.city.defense;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-authored, clientbound visual events. Debris never becomes world entities or block edits. */
public record O5Effect(int kind,int owner,int move,int variant,long tick,Vec3 from,Vec3 to) implements CustomPacketPayload {
    public static final Type<O5Effect> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","o5_response_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf,O5Effect> CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.kind);b.writeVarInt(p.owner);b.writeVarInt(p.move);b.writeVarInt(p.variant);b.writeLong(p.tick);
        b.writeDouble(p.from.x);b.writeDouble(p.from.y);b.writeDouble(p.from.z);b.writeDouble(p.to.x);b.writeDouble(p.to.y);b.writeDouble(p.to.z);
    },b->new O5Effect(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readLong(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble())));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void emit(O5OperativeEntity actor,int kind,int move,int variant,Vec3 from,Vec3 to){
        if(actor.level() instanceof ServerLevel world)PacketDistributor.sendToPlayersNear(world,null,to.x,to.y,to.z,128,new O5Effect(kind,actor.getId(),move,variant,world.getGameTime(),from,to));
    }
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(TYPE,CODEC,(p,c)->c.enqueueWork(()->cn.academy.port.client.city.o5.CombatEffects.accept(p)));}
    }
}
