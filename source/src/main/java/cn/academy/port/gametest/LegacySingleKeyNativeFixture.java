/* Isolated M37 native ingress fixture candidate. See NOTICE. */
package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.core.LegacySingleKeyProtocol;
import cn.academy.port.core.LegacySingleKeyProtocol.AcceptedRequest;
import cn.academy.port.core.LegacySingleKeyProtocol.Skill;
import cn.academy.port.core.LegacySingleKeyProtocol.StartRequest;
import cn.academy.port.skill.CurrentCharging;
import cn.academy.port.skill.DirectedShock;
import cn.academy.port.skill.Flashing;
import cn.academy.port.skill.GroundShock;
import cn.academy.port.skill.MagManip;
import cn.academy.port.skill.MagMovement;
import cn.academy.port.skill.ThreateningTeleport;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Admits an actual native PlayerList/Level owner and captures production ClientData identities.
 * Calls the programmatic requestFromClient seam; it does not exercise a socket, client, codec,
 * unchanged source engine, or rendering. No synthetic player or world ticks are posted here.
 */
final class LegacySingleKeyNativeFixture implements AutoCloseable {
    private final GameTestHelper helper;
    private final CaptureConnection connection;
    private final EmbeddedChannel channel;
    final ServerPlayer player;
    private final List<ServerPlayer> owners = new ArrayList<>();
    private final List<Entity> entities = new ArrayList<>();
    private boolean closed;

    LegacySingleKeyNativeFixture(GameTestHelper helper) {
        this.helper = helper;
        var level = helper.getLevel();
        var server = level.getServer();
        var cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "ACSingleKey"), false);
        player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        owners.add(player);
        connection = new CaptureConnection();
        channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            helper.assertTrue(server.getPlayerList().getPlayer(player.getUUID()) == player
                    && level.getEntity(player.getId()) == player,
                    "exact native PlayerList and Level single-key owner admitted");
            var at = helper.absoluteVec(new Vec3(3.5, 1, 2.5));
            player.moveTo(at.x, at.y, at.z, 0, 0);
            player.setYHeadRot(0);
            player.setGameMode(GameType.SURVIVAL);
            player.setNoGravity(true);
            player.setInvulnerable(true);
            player.getAbilities().instabuild = false;
        } catch (RuntimeException | Error failure) {
            try { close(); }
            catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }

    boolean press(int slot, Skill skill, long input) {
        long epoch = LegacySingleKeyOwnerEpoch.current(
                player, player.connection, player.serverLevel(), AbilityStorage.get(player));
        var start = new StartRequest(slot, skill, input, player.getId(), epoch);
        return request(LegacySingleKeyProtocol.PRESS, start.wire());
    }

    AcceptedRequest accepted(Skill skill, long input) {
        return accepted(player, skill, input);
    }

    AcceptedRequest accepted(ServerPlayer owner, Skill skill, long input) {
        String kind = skill == Skill.DIRECTED_SHOCK ? "dir_shock_prepare" : skill.id() + "_start";
        var tag = connection.data.stream().filter(data -> data.getString("kind").equals(kind)
                        && data.getLong("input") == input && data.getInt("entity") == owner.getId()
                        && data.hasUUID("entity_uuid") && data.getUUID("entity_uuid").equals(owner.getUUID())
                        && LegacySingleKeyOwnerEpoch.matches(owner, owner.connection, owner.serverLevel(),
                                AbilityStorage.get(owner), data.getLong("owner_epoch")))
                .reduce((previous, latest) -> latest)
                .orElseThrow(() -> new AssertionError("Missing actual accepted payload " + kind));
        helper.assertTrue(tag.getLong("input") > 0 && tag.getLong("token") > 0
                        && tag.getLong("owner_epoch") > 0
                        && LegacySingleKeyOwnerEpoch.matches(owner, owner.connection,
                                owner.serverLevel(), AbilityStorage.get(owner), tag.getLong("owner_epoch")),
                "actual start payload carries exact positive input, server token and issued owner epoch");
        return new AcceptedRequest(skill, tag.getLong("input"), tag.getInt("entity"),
                tag.getLong("owner_epoch"), tag.getLong("token"));
    }

    boolean terminal(String action, AcceptedRequest accepted) {
        if (!action.equals(LegacySingleKeyProtocol.RELEASE) && !action.equals(LegacySingleKeyProtocol.ABORT))
            throw new IllegalArgumentException("Accepted single-key terminal required");
        return request(action, accepted.wire());
    }

    void assertPlainDenied(int slot, Skill skill) {
        helper.assertTrue(AbilityStorage.get(player).presets.currentSkill(slot).equals(skill.id()),
                "plain-route denial tests the actual mapped single-key skill " + skill.id());
        for (String action : List.of("slot_press", "slot_release", "slot_abort"))
            helper.assertFalse(request(action, Integer.toString(slot)),
                    "plain legacy ingress cannot bypass single-key identity " + skill.id() + ":" + action);
    }

    private boolean request(String action, String value) {
        return AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request(action, value));
    }

    long syncEpoch(ServerPlayer owner) {
        int before = connection.data.size();
        AcademyNetwork.sync(owner);
        var state = connection.data.subList(before, connection.data.size()).stream()
                .filter(tag -> tag.getString("kind").equals("state")).reduce((old, latest) -> latest)
                .orElseThrow(() -> new AssertionError("Actual AcademyNetwork.sync did not emit state"));
        long epoch = state.getLong("single_key_owner_epoch");
        helper.assertTrue(epoch > 0 && LegacySingleKeyOwnerEpoch.matches(owner, owner.connection,
                        owner.serverLevel(), AbilityStorage.get(owner), epoch),
                "actual sync payload carries positive exact current-owner epoch");
        return epoch;
    }

    int payloads(String kind) {
        return (int) connection.data.stream().filter(tag -> tag.getString("kind").equals(kind)).count();
    }

    /** Near-player broadcasts include legitimate other actors; count only the captured actor. */
    int payloads(String kind, ServerPlayer owner) {
        return (int) connection.data.stream().filter(tag -> tag.getString("kind").equals(kind)
                && tag.getInt("entity") == owner.getId() && tag.hasUUID("entity_uuid")
                && tag.getUUID("entity_uuid").equals(owner.getUUID())).count();
    }

    ServerPlayer respawn(ServerPlayer original) {
        var replacement = original.serverLevel().getServer().getPlayerList()
                .respawn(original, true, Entity.RemovalReason.CHANGED_DIMENSION);
        owners.add(replacement);
        // Official ServerGamePacketListenerImpl.handleClientCommand assigns the respawn return value.
        original.connection.player = replacement;
        helper.assertTrue(replacement != original && replacement.getId() == original.getId()
                        && replacement.getUUID().equals(original.getUUID())
                        && replacement.connection == original.connection
                        && replacement.connection.player == replacement,
                "official respawn creates a new object with copied entity ID and shared current listener");
        helper.assertTrue(replacement.serverLevel().getEntity(replacement.getId()) == replacement
                        && replacement.serverLevel().getServer().getPlayerList().getPlayer(replacement.getUUID()) == replacement,
                "official respawn admits the exact replacement into Level and PlayerList");
        var at = helper.absoluteVec(new Vec3(3.5, 1, 2.5));
        replacement.moveTo(at.x, at.y, at.z, 0, 0);
        replacement.setYHeadRot(0);
        replacement.setGameMode(GameType.SURVIVAL);
        replacement.setNoGravity(true);
        replacement.setInvulnerable(true);
        replacement.getAbilities().instabuild = false;
        return replacement;
    }

    void track(Entity entity) { entities.add(entity); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        // Also own a replacement admitted before respawn could return with an exception.
        var admitted = helper.getLevel().getServer().getPlayerList().getPlayer(player.getUUID());
        if (admitted != null && owners.stream().noneMatch(owner -> owner == admitted)) owners.add(admitted);
        for (int index = owners.size() - 1; index >= 0; --index) {
            var owner = owners.get(index);
            failure = cleanup(() -> CurrentCharging.remove(owner), failure);
            failure = cleanup(() -> MagMovement.remove(owner), failure);
            failure = cleanup(() -> MagManip.remove(owner), failure);
            failure = cleanup(() -> GroundShock.remove(owner), failure);
            failure = cleanup(() -> DirectedShock.remove(owner), failure);
            failure = cleanup(() -> ThreateningTeleport.remove(owner), failure);
            failure = cleanup(() -> Flashing.remove(owner), failure);
            failure = cleanup(() -> {
                var players = helper.getLevel().getServer().getPlayerList();
                if (players.getPlayer(owner.getUUID()) == owner) players.remove(owner);
                else owner.discard();
            }, failure);
            failure = cleanup(() -> LegacySingleKeyOwnerEpoch.forget(owner), failure);
            failure = cleanup(() -> AbilityStorage.remove(owner), failure);
        }
        for (var entity : entities) failure = cleanup(entity::discard, failure);
        failure = cleanup(() -> channel.finishAndReleaseAll(), failure);
        if (failure != null) throw failure;
    }

    private static RuntimeException cleanup(Runnable action, RuntimeException failure) {
        try { action.run(); }
        catch (RuntimeException next) {
            if (failure == null) failure = next;
            else failure.addSuppressed(next);
        }
        return failure;
    }

    private static final class CaptureConnection extends Connection {
        final List<CompoundTag> data = new ArrayList<>();
        CaptureConnection() { super(PacketFlow.SERVERBOUND); }
        @Override public void send(Packet<?> packet, PacketSendListener listener, boolean flush) {
            if (packet instanceof ClientboundCustomPayloadPacket payload
                    && payload.payload() instanceof AcademyNetwork.ClientData message)
                data.add(message.data().copy());
            super.send(packet, listener, flush);
        }
    }
}
