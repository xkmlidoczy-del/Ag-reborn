package cn.academy.port.highpower;

import cn.academy.port.highpower.core.CombatAmounts;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Dedicated optional S2C channel. No client request, writable health field, or shared ability payload. */
@EventBusSubscriber(modid = "academy")
public final class HighPowerRecoveryHudPayload {
    private HighPowerRecoveryHudPayload() {}
    public static Consumer<Snapshot> clientReceiver = snapshot -> {};
    private static final AtomicLong EPOCHS = new AtomicLong();
    private static final Map<ServerPlayer, Sender> SENDERS = new IdentityHashMap<>();
    private static final class Sender {
        final Object level, connection;
        final long epoch = EPOCHS.incrementAndGet();
        long sequence, checked = Long.MIN_VALUE, sent = Long.MIN_VALUE;
        HighPowerRecoveryHudModel.Values previous;
        boolean initialized;
        Sender(ServerPlayer player) { level = player.level(); connection = player.connection; }
    }
    public record Snapshot(HighPowerRecoveryHudModel.Identity identity, HighPowerRecoveryHudModel.Values values) implements CustomPacketPayload {
        // v5's enum rejects TP. A distinct optional channel negotiates support before
        // any TP value is sent; older clients retain the native/actionbar fallback.
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_recovery_hud_v6"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of((buffer, packet) -> {
            var id = packet.identity;
            buffer.writeUUID(id.owner()); buffer.writeInt(id.entity()); buffer.writeUtf(id.dimension(), 128);
            buffer.writeLong(id.epoch()); buffer.writeLong(id.sequence()); buffer.writeBoolean(packet.values != null);
            if (packet.values != null) {
                var value = packet.values;
                text(buffer, value.health()); text(buffer, value.maxHealth()); text(buffer, value.shield()); text(buffer, value.maxShield());
                buffer.writeUtf(value.status(), 24); text(buffer, value.exactHpPerSecond());
                buffer.writeVarInt(value.pulseTicks()); buffer.writeVarInt(value.ticksUntilPulse()); buffer.writeVarInt(value.pauseTicksRemaining());
                buffer.writeBoolean(value.shieldActive());
                text(buffer, value.domainReserve()); text(buffer, value.domainCapacity()); buffer.writeBoolean(value.domainActive()); buffer.writeUtf(value.category(), 24);
                text(buffer,value.curtainReserve()); text(buffer,value.curtainCapacity()); buffer.writeBoolean(value.curtainActive());
                text(buffer,value.zeroGapReserve()); text(buffer,value.zeroGapCapacity()); buffer.writeBoolean(value.zeroGapActive());
            }
        }, buffer -> {
            var id = new HighPowerRecoveryHudModel.Identity(buffer.readUUID(), buffer.readInt(), buffer.readUtf(128), buffer.readLong(), buffer.readLong());
            var value = buffer.readBoolean() ? new HighPowerRecoveryHudModel.Values(text(buffer), text(buffer), text(buffer), text(buffer),
                    buffer.readUtf(24), text(buffer), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), text(buffer), text(buffer), buffer.readBoolean(), buffer.readUtf(24), text(buffer), text(buffer), buffer.readBoolean(), text(buffer), text(buffer), buffer.readBoolean()) : null;
            return new Snapshot(id, value);
        });
        public Snapshot { if (identity == null) throw new IllegalArgumentException("Missing recovery HUD owner"); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static void text(RegistryFriendlyByteBuf buffer, String value) { buffer.writeUtf(value, CombatAmounts.MAX_ENCODED_LENGTH); }
    private static String text(RegistryFriendlyByteBuf buffer) { return buffer.readUtf(CombatAmounts.MAX_ENCODED_LENGTH); }
    public static boolean supported(ServerPlayer player) {
        if (player.connection == null || player.hasDisconnected()) return false;
        // A server player whose connection has no network channel (mock or embedded fake players) can never receive the snapshot.
        try { return NetworkRegistry.hasChannel(player.connection, Snapshot.TYPE.id()); }
        catch (NullPointerException noChannel) { return false; }
    }
    /** Also used by the actionbar fallback: suppress its exact numbers only after this connection received a snapshot. */
    public static boolean published(ServerPlayer player) {
        Sender sender = SENDERS.get(player);
        return supported(player) && sender != null && sender.initialized && sender.previous != null
                && sender.connection == player.connection && sender.level == player.level()
                && sender.previous.category().equals(cn.academy.port.AbilityStorage.get(player).category);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !supported(player)) return;
        long now = player.level().getGameTime();
        Sender sender = SENDERS.get(player);
        if (sender == null || sender.level != player.level() || sender.connection != player.connection) {
            sender = new Sender(player); SENDERS.put(player, sender);
        }
        if (sender.checked != Long.MIN_VALUE && now >= sender.checked && now - sender.checked < HighPowerRecoveryHudModel.SEND_INTERVAL) return;
        sender.checked = now;
        HighPowerRecoveryHudModel.Values value;
        try {
            var snapshot = HighPowerNaturalRecovery.snapshot(player);
            String category = cn.academy.port.AbilityStorage.get(player).category;
            boolean electromaster = HighPowerCategories.isElectromaster(category);
            boolean meltdowner = HighPowerCategories.isMeltdowner(category);
            boolean shieldCategory = electromaster || meltdowner || EsperShields.eligible(player) || HighPowerCombat.vectorParryBenefitsActive(player);
            boolean zeroGap = meltdowner && HighPowerCombat.zeroGapBenefitsActive(player);
            value = snapshot.map(s -> new HighPowerRecoveryHudModel.Values(s.health(), s.maxHealth(),
                    shieldCategory ? s.shield() : "0", shieldCategory ? s.maxShield() : "0", s.status(),
                    s.exactHpPerSecond(), s.pulseTicks(), s.ticksUntilPulse(), s.pauseTicksRemaining(), shieldCategory && HighPowerCombat.shieldActive(player),
                    electromaster ? CombatAmounts.decimalString(MagneticDomainReserve.reserve(player)) : "0",
                    electromaster ? CombatAmounts.decimalString(MagneticDomainReserve.capacity(player)) : "0",
                    electromaster && MagneticDomain.active(player), category,
                    meltdowner ? CombatAmounts.decimalString(HighPowerCombat.curtainReserve(player)) : "0",
                    meltdowner ? CombatAmounts.decimalString(HighPowerCombat.curtainReserveCapacity(player)) : "0",
                    meltdowner && DissipationCurtain.active(player),
                    zeroGap ? CombatAmounts.decimalString(HighPowerCombat.zeroGapReserve(player)) : "0",
                    zeroGap ? CombatAmounts.decimalString(HighPowerCombat.zeroGapReserveCapacity(player)) : "0",
                    zeroGap && ZeroGapArmor.active(player))).orElse(null);
        } catch (IllegalArgumentException invalidSnapshot) {
            value = null; // Malformed presentation state cannot poison gameplay or advertise stale health.
        }
        if (!sender.initialized && value == null) return;
        if (sender.initialized && java.util.Objects.equals(value, sender.previous) && now >= sender.sent
                && now - sender.sent < HighPowerRecoveryHudModel.KEEPALIVE_TICKS) return;
        if (sender.epoch <= 0 || sender.sequence == Long.MAX_VALUE) return;
        String dimension = player.level().dimension().location().toString();
        if (dimension.length() > 128) return; // Unsupported identifiers retain the native/actionbar fallback.
        var identity = new HighPowerRecoveryHudModel.Identity(player.getUUID(), player.getId(), dimension, sender.epoch, ++sender.sequence);
        PacketDistributor.sendToPlayer(player, new Snapshot(identity, value));
        sender.previous = value; sender.sent = now; sender.initialized = true;
    }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("6").optional().playToClient(Snapshot.TYPE, Snapshot.CODEC, (packet, context) -> clientReceiver.accept(packet));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer player) SENDERS.remove(player); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) { if (event.getOriginal() instanceof ServerPlayer player) SENDERS.remove(player); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer player) SENDERS.remove(player); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SENDERS.clear(); }
}
