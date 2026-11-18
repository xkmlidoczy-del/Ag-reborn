package cn.academy.port.titan;

import java.util.ArrayList;
import java.util.List;
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

@EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
public record TitanPatternPayload(int entity,int color,boolean warning,float density,List<TitanPattern.Zone> zones) implements CustomPacketPayload {
    public static final Type<TitanPatternPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","titan_pattern"));
    public static java.util.function.Consumer<TitanPatternPayload> receiver=p->{};
    private static void vector(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 vector(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    public static final StreamCodec<RegistryFriendlyByteBuf,TitanPatternPayload> CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.entity);b.writeInt(p.color);b.writeBoolean(p.warning);b.writeFloat(p.density);b.writeVarInt(p.zones.size());
        for(var z:p.zones){b.writeEnum(z.kind());vector(b,z.a());vector(b,z.b());b.writeFloat((float)z.outer());b.writeFloat((float)z.inner());b.writeFloat((float)z.angle());b.writeFloat((float)z.height());}
    },b->{
        int entity=b.readVarInt(),color=b.readInt();boolean warning=b.readBoolean();float density=b.readFloat();int count=b.readVarInt();
        if(count<0||count>32)throw new IllegalArgumentException("Invalid titan zone count");
        var zones=new ArrayList<TitanPattern.Zone>(count);
        for(int i=0;i<count;i++)zones.add(new TitanPattern.Zone(b.readEnum(TitanPattern.Kind.class),vector(b),vector(b),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat()));
        return new TitanPatternPayload(entity,color,warning,density,List.copyOf(zones));
    });
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){
        e.registrar("1").playToClient(TYPE,CODEC,(p,c)->c.enqueueWork(()->receiver.accept(p)));
    }
    public static void send(ServerLevel w,TitanEntity e,boolean warning,List<TitanPattern.Zone> zones){
        PacketDistributor.sendToPlayersNear(w,null,e.getX(),e.getY(),e.getZ(),190,new TitanPatternPayload(e.getId(),e.profile().color(),warning,TitanConfig.PARTICLE_DENSITY.get().floatValue(),zones));
    }
}
