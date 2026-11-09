package cn.academy.port.skill;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Original first-four-category renderers receive a genuine non-player caster, without forged player input tokens. */
public final class EntitySkillVisual {
    public static Consumer<Visual> clientReceiver=v -> {};
    private EntitySkillVisual() {}
    public static void emit(LivingEntity caster,String skill,String phase,Vec3 from,Vec3 to,int ticks) {
        if(!(caster.level() instanceof ServerLevel world)) return;
        PacketDistributor.sendToPlayersNear(world,null,from.x,from.y,from.z,64,
                new Visual(caster.getId(),caster.getUUID(),world.dimension().location(),skill,phase,from,to,ticks));
    }
    public record Visual(int entity,UUID owner,ResourceLocation dimension,String skill,String phase,Vec3 from,Vec3 to,int ticks) implements CustomPacketPayload {
        public static final Type<Visual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","entity_skill_visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v) -> {
            b.writeVarInt(v.entity);b.writeUUID(v.owner);b.writeResourceLocation(v.dimension);b.writeUtf(v.skill,64);b.writeUtf(v.phase,16);
            b.writeVec3(v.from);b.writeVec3(v.to);b.writeVarInt(v.ticks);
        },b -> new Visual(b.readVarInt(),b.readUUID(),b.readResourceLocation(),b.readUtf(64),b.readUtf(16),b.readVec3(),b.readVec3(),b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) {
            e.registrar("1").playToClient(Visual.TYPE,Visual.CODEC,(v,ctx) -> clientReceiver.accept(v));
        }
    }
}
