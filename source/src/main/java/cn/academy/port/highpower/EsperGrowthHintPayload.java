package cn.academy.port.highpower;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Dedicated optional S2C channel for the growth-style status line. It carries only a category, a bounded percentage and a small
 * counter; the recovery HUD snapshot and every exact number stay on their own channel. Clients without it simply show no hint.
 */
public final class EsperGrowthHintPayload {
    private EsperGrowthHintPayload() {}
    public static Consumer<Hint> clientReceiver = hint -> {};

    public record Hint(UUID owner, HighPowerRecoveryHudModel.GrowthHint hint) implements CustomPacketPayload {
        public static final Type<Hint> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "growth_style_hint_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hint> CODEC = StreamCodec.of((buffer, packet) -> {
            buffer.writeUUID(packet.owner); buffer.writeUtf(packet.hint.category(), 24);
            buffer.writeVarInt(packet.hint.percent()); buffer.writeVarInt(packet.hint.count());
        }, buffer -> new Hint(buffer.readUUID(), new HighPowerRecoveryHudModel.GrowthHint(buffer.readUtf(24), buffer.readVarInt(), buffer.readVarInt())));
        public Hint { if (owner == null || hint == null) throw new IllegalArgumentException("Missing growth hint owner or value"); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static boolean supported(net.minecraft.server.level.ServerPlayer player) {
        return player.connection != null && !player.hasDisconnected()
                && net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(player.connection, Hint.TYPE.id());
    }

    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").optional().playToClient(Hint.TYPE, Hint.CODEC, (packet, context) -> clientReceiver.accept(packet));
        }
    }
}
