package cn.academy.port.titan;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-owned cast geometry. Payload is clientbound only. */
public record TitanFxPayload(String species,int slot,int phase,int age,Vec3 origin,Vec3 target,float density) implements CustomPacketPayload {
    public static final Type<TitanFxPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(TitanBlocks.MODID,"fx_v6"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TitanFxPayload> CODEC=StreamCodec.of(
        (b,p)-> {
            b.writeUtf(p.species,64);b.writeVarInt(p.slot);b.writeVarInt(p.phase);b.writeVarInt(p.age);
            b.writeDouble(p.origin.x);b.writeDouble(p.origin.y);b.writeDouble(p.origin.z);
            b.writeDouble(p.target.x);b.writeDouble(p.target.y);b.writeDouble(p.target.z);b.writeFloat(p.density);
        },
        b->new TitanFxPayload(b.readUtf(64),b.readVarInt(),b.readVarInt(),b.readVarInt(),
            new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}

    @EventBusSubscriber(modid=TitanBlocks.MODID,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("6").playToClient(TYPE,CODEC,(payload,context)->TitanFxClient.accept(payload));
        }
    }
}
