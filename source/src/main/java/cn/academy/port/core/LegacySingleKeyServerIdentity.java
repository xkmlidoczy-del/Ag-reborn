package cn.academy.port.core;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Captured authenticated owner of one held skill; contains no preset lookup or global abort operation. */
public final class LegacySingleKeyServerIdentity {
    private final ServerPlayer owner;
    private final Object connection;
    private final ServerLevel level;
    private final AbilityProgress state;
    private final int entityId;
    private final java.util.UUID entityUuid;
    private final long input, token, ownerEpoch;
    public LegacySingleKeyServerIdentity(ServerPlayer owner, long input, long token, AbilityProgress state) {
        if (owner == null || state == null || input < 0 || token <= 0) throw new IllegalArgumentException("Invalid captured skill identity");
        this.owner = owner; this.connection = owner.connection; this.level = owner.serverLevel();
        this.state = state; this.input = input; this.token = token;
        this.entityId = owner.getId(); this.entityUuid = owner.getUUID();
        this.ownerEpoch = LegacySingleKeyOwnerEpoch.current(owner, connection, level, state);
        if (input > 0 && ownerEpoch <= 0) throw new IllegalArgumentException("Positive input requires an issued owner epoch");
    }
    public ServerPlayer owner() { return owner; }
    public ServerLevel level() { return level; }
    public AbilityProgress state() { return state; }
    public long input() { return input; }
    public long token() { return token; }
    public long epoch() { return ownerEpoch; }
    public boolean owns(ServerPlayer player) {
        return player == owner && player != null && player.connection == connection
            && player.serverLevel() == level && player.level() == level
            && player.getId() == entityId && entityUuid.equals(player.getUUID())
            && AbilityStorage.get(player) == state && level.getEntity(entityId) == player
            && level.getServer().isSameThread()
            && (input == 0 && ownerEpoch == 0 && connection == null
                || LegacySingleKeyOwnerEpoch.matches(player, connection, level, state, ownerEpoch));
    }
    public boolean matches(ServerPlayer player, long requestedInput) {
        return requestedInput > 0 && input == requestedInput && token > 0 && owns(player)
            && live(player) && !AbilityConsumption.busy(player);
    }
    public boolean matches(ServerPlayer player, long requestedInput, long requestedToken, long requestedEpoch) {
        return requestedToken > 0 && requestedToken == token && requestedEpoch > 0 && requestedEpoch == ownerEpoch
            && matches(player, requestedInput);
    }
    public static boolean claim(LegacySingleKeyProtocol.NonceLedger ledger, ServerPlayer player, long input) {
        return canClaim(ledger,player,input) && ledger.claim(player, player.connection, input);
    }
    public static boolean canClaim(LegacySingleKeyProtocol.NonceLedger ledger, ServerPlayer player, long input) {
        return ledger != null && input > 0 && live(player) && player.serverLevel().getServer().isSameThread()
            && player.serverLevel().getEntity(player.getId()) == player && !AbilityConsumption.busy(player)
            && input > ledger.highest(player, player.connection);
    }
    private static boolean live(ServerPlayer player) {
        return player != null && player.connection != null && !player.hasDisconnected()
            && player.isAlive() && !player.isRemoved() && !player.isSpectator();
    }
    public void write(CompoundTag tag) {
        tag.putInt("entity", entityId); tag.putUUID("entity_uuid", entityUuid);
        tag.putLong("input", input); tag.putLong("token", token); tag.putLong("owner_epoch", ownerEpoch);
    }
}
