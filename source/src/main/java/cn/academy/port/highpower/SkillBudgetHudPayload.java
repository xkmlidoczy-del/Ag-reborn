package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Independent optional S2C-only channel; existing v6 exact-health packets remain byte-compatible. */
@EventBusSubscriber(modid = "academy")
public final class SkillBudgetHudPayload {
    private SkillBudgetHudPayload() {}
    public static Consumer<Snapshot> clientReceiver = ignored -> {};
    private static final AtomicLong EPOCHS = new AtomicLong();
    private static final Map<ServerPlayer, Sender> SENDERS = new IdentityHashMap<>();
    private static final class Sender {
        final Object level, connection; final long epoch = EPOCHS.incrementAndGet();
        long sequence, checked = Long.MIN_VALUE, sent = Long.MIN_VALUE; boolean initialized;
        SkillBudgetHudModel.Values previous;
        Sender(ServerPlayer player) { level = player.level(); connection = player.connection; }
    }
    public record Snapshot(HighPowerRecoveryHudModel.Identity identity, SkillBudgetHudModel.Values values) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "skill_budget_hud_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of((buffer, packet) -> {
            var id = packet.identity();
            buffer.writeUUID(id.owner()); buffer.writeInt(id.entity()); buffer.writeUtf(id.dimension(), 128);
            buffer.writeLong(id.epoch()); buffer.writeLong(id.sequence()); buffer.writeBoolean(packet.values() != null);
            if (packet.values() != null) {
                var v = packet.values(); buffer.writeUtf(v.category(), 24);
                buffer.writeDouble(v.available()); buffer.writeDouble(v.total()); buffer.writeDouble(v.maximum()); buffer.writeDouble(v.held());
            }
        }, buffer -> {
            var id = new HighPowerRecoveryHudModel.Identity(buffer.readUUID(), buffer.readInt(), buffer.readUtf(128), buffer.readLong(), buffer.readLong());
            var values = buffer.readBoolean() ? new SkillBudgetHudModel.Values(buffer.readUtf(24), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble()) : null;
            return new Snapshot(id, values);
        });
        public Snapshot { if (identity == null) throw new IllegalArgumentException("Missing skill CP owner"); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static boolean supported(ServerPlayer player) {
        if (player.connection == null || player.hasDisconnected()) return false;
        try { return NetworkRegistry.hasChannel(player.connection, Snapshot.TYPE.id()); }
        catch (NullPointerException noChannel) { return false; }
    }
    /** Never initializes a budget, spends CP or releases a reservation. */
    static SkillBudgetHudModel.Values snapshot(ServerPlayer player) {
        if (!HighPowerResources.ready(player)) return null;
        var data = player.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
        if (!data.contains("cp", Tag.TAG_DOUBLE)) return null;
        double total = HighPowerResources.cp(player);
        if (Double.compare(data.getDouble("cp"), total) != 0) return null;
        double held = HighPowerResources.heldAmount(player);
        double available = SkillBudgetHudModel.displayAvailable(HighPowerResources.availableCp(player), total, held);
        return new SkillBudgetHudModel.Values(AbilityStorage.get(player).category, available,
                total, HighPowerGrowth.values(player).cpMaximum(), held);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !supported(player)) return;
        long now = player.level().getGameTime(); Sender sender = SENDERS.get(player);
        if (sender == null || sender.level != player.level() || sender.connection != player.connection) {
            sender = new Sender(player); SENDERS.put(player, sender);
        }
        if (sender.checked != Long.MIN_VALUE && now >= sender.checked && now - sender.checked < 5) return;
        sender.checked = now;
        SkillBudgetHudModel.Values value;
        try { value = snapshot(player); }
        catch (IllegalArgumentException | IllegalStateException invalid) { value = null; }
        if (!sender.initialized && value == null) return;
        if (sender.initialized && java.util.Objects.equals(value, sender.previous) && now >= sender.sent && now - sender.sent < 20) return;
        if (sender.epoch <= 0 || sender.sequence == Long.MAX_VALUE) return;
        String dimension = player.level().dimension().location().toString(); if (dimension.length() > 128) return;
        PacketDistributor.sendToPlayer(player, new Snapshot(new HighPowerRecoveryHudModel.Identity(player.getUUID(), player.getId(), dimension,
                sender.epoch, ++sender.sequence), value));
        sender.previous = value; sender.sent = now; sender.initialized = true;
    }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").optional().playToClient(Snapshot.TYPE, Snapshot.CODEC, (packet, context) -> clientReceiver.accept(packet));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer player) SENDERS.remove(player); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) { if (event.getOriginal() instanceof ServerPlayer player) SENDERS.remove(player); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer player) SENDERS.remove(player); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SENDERS.clear(); }
}
