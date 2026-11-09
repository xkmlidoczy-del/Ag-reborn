package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.core.CastPose;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Cosmetic relay of casting gestures so other players see the caster's arms. The client states a skill/archetype and a phase;
 * the server checks the sender has an activated ability, caps the rate and forwards to players tracking the sender. The packet
 * changes no game state: the worst a modified client can do is make its own character wave.
 */
@EventBusSubscriber(modid = "academy")
public final class CastPoseNet {
    public static final int HOLD = 0, TAP = 1, END = 2;
    private static final int MIN_GAP_TICKS = 2;
    public static Consumer<Cue> clientReceiver = ignored -> {};
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private CastPoseNet() {}

    private static boolean valid(int archetype, int phase) { return archetype >= 0 && archetype < CastPose.Archetype.values().length && phase >= HOLD && phase <= END; }

    /** Client to server. */
    public record Intent(int archetype, int phase, String skill) implements CustomPacketPayload {
        public Intent(int archetype, int phase) { this(archetype, phase, ""); }
        public Intent { if (!valid(archetype, phase) || skill == null || skill.length() > 64) throw new IllegalArgumentException("cast pose intent"); }
        public static final Type<Intent> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "cast_pose_intent"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Intent> CODEC = StreamCodec.of((b, v) -> { b.writeVarInt(v.archetype); b.writeVarInt(v.phase); b.writeUtf(v.skill, 64); }, b -> new Intent(b.readVarInt(), b.readVarInt(), b.readUtf(64)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server to client. */
    public record Cue(int entityId, int archetype, int phase, String category, String skill, int level) implements CustomPacketPayload {
        public Cue(int entityId, int archetype, int phase) { this(entityId, archetype, phase, "", "", 3); }
        public Cue {
            if (entityId < 0 || !valid(archetype, phase) || category == null || category.length() > 32
                    || skill == null || skill.length() > 64 || level < 0 || level > 5) throw new IllegalArgumentException("cast pose cue");
        }
        public static final Type<Cue> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "cast_pose_cue"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Cue> CODEC = StreamCodec.of((b, v) -> { b.writeInt(v.entityId); b.writeVarInt(v.archetype); b.writeVarInt(v.phase); b.writeUtf(v.category, 32); b.writeUtf(v.skill, 64); b.writeVarInt(v.level); },
                b -> new Cue(b.readInt(), b.readVarInt(), b.readVarInt(), b.readUtf(32), b.readUtf(64), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server-side handling, separated so a test can drive it without a connection. Returns the cue to forward, or null. */
    public static Cue accept(ServerPlayer player, Intent intent, long gameTime) {
        if (player == null || intent == null || !player.isAlive() || player.isSpectator()) return null;
        var state = AbilityStorage.get(player);
        if (state == null || !state.isActivated()) return null;
        // Category and level are derived on the server; a client cannot borrow another category's signature or LV5 scale.
        int level = 3;
        if (!intent.skill().isEmpty()) {
            var skill = SkillCatalog.find(state.category, intent.skill()).orElse(null);
            if (skill == null || !skill.controllable() || !state.learned(skill.id()) || state.level < skill.level()
                    || CastPose.of(skill.id()) != CastPose.Archetype.values()[intent.archetype()]
                    || !SkillCatalog.enabled(skill.category(), skill.id())) return null;
            level = skill.level();
        }
        Long last = LAST.get(player.getUUID());
        if (last != null && gameTime - last < MIN_GAP_TICKS && intent.phase() != END) return null;
        LAST.put(player.getUUID(), gameTime);
        if (LAST.size() > 512) LAST.keySet().removeIf(id -> !id.equals(player.getUUID()) && LAST.size() > 256);
        return new Cue(player.getId(), intent.archetype(), intent.phase(), state.category, intent.skill(), level);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { LAST.remove(e.getEntity().getUUID()); }

    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) {
            // Skill identity and authoritative category/level require matching clients; reject old builds at negotiation, before decoding a cue.
            var registrar = e.registrar("3");
            registrar.playToServer(Intent.TYPE, Intent.CODEC, (intent, context) -> context.enqueueWork(() -> {
                if (!(context.player() instanceof ServerPlayer player)) return;
                Cue cue = accept(player, intent, player.serverLevel().getGameTime());
                if (cue != null) PacketDistributor.sendToPlayersTrackingEntity(player, cue);
            }));
            registrar.playToClient(Cue.TYPE, Cue.CODEC, (cue, context) -> clientReceiver.accept(cue));
        }
    }
}
