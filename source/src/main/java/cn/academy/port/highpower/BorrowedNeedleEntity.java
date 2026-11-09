package cn.academy.port.highpower;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Session projection of a retained original. Native ticks alone invoke the closed volley controller. */
public final class BorrowedNeedleEntity extends Entity {
    public static final int MAX_LEASE_TICKS = 40;
    private static final EntityDataAccessor<Boolean> ADMITTED =
            SynchedEntityData.defineId(BorrowedNeedleEntity.class, EntityDataSerializers.BOOLEAN);
    @Nullable private ServerPlayer owner;
    @Nullable private UUID ownerId;
    @Nullable private Object lease;
    private long nonce;
    private int leaseTicks;
    private boolean removing;
    private int slotIndex = -1, shotIndex = -1;
    @Nullable private UUID roundId;
    @Nullable private BorrowedNeedleEscrow.ShotTick shotTick;

    public BorrowedNeedleEntity(EntityType<? extends BorrowedNeedleEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ADMITTED, false);
    }

    boolean prepare(ServerPlayer player, long value, Object sessionLease) {
        if (level().isClientSide || isRemoved() || owner != null || sessionLease == null || value <= 0
                || getType() != TeleporterNeedleEntities.BORROWED_NEEDLE.get() || player.level() != level()) return false;
        owner = player;
        ownerId = player.getUUID();
        nonce = value;
        lease = sessionLease;
        return true;
    }

    boolean hasLease(ServerPlayer player, long value, Object sessionLease) {
        return owner == player && ownerId != null && ownerId.equals(player.getUUID())
                && nonce == value && lease != null && lease == sessionLease;
    }

    boolean prepareShot(ServerPlayer player, long generation, Object sessionLease, UUID round,
            int slot, int shot, BorrowedNeedleEscrow.ShotTick tick) {
        if (round == null || tick == null || slot < 0 || slot >= BorrowedNeedleEscrow.VOLLEY_SLOTS
                || shot < 0 || shot >= BorrowedNeedleEscrow.MAX_VOLLEY_SHOTS || shot % BorrowedNeedleEscrow.VOLLEY_SLOTS != slot
                || !prepare(player, generation, sessionLease)) return false;
        roundId = round;
        slotIndex = slot;
        shotIndex = shot;
        shotTick = tick;
        return true;
    }

    boolean matchesShot(UUID round, int slot, int shot) {
        return roundId != null && roundId.equals(round) && slotIndex == slot && shotIndex == shot;
    }

    @Nullable ServerPlayer leaseOwner() { return owner; }
    boolean removalInProgress() { return removing; }

    boolean admit(ServerPlayer player, long value, Object sessionLease) {
        if (isRemoved() || !hasLease(player, value, sessionLease)) return false;
        entityData.set(ADMITTED, true);
        return true;
    }

    /** Called before discarding or moving the stock out of escrow. */
    void revoke() {
        lease = null;
        shotTick = null;
        entityData.set(ADMITTED, false);
    }

    /** The native remove already in progress owns its callback; never nest another one. */
    void discardRetired() {
        if (!removing && !isRemoved()) discard();
    }

    @Nullable public UUID ownerId() { return ownerId; }
    public long nonce() { return nonce; }
    public long leaseGeneration() { return nonce; }
    public int leaseAge() { return leaseTicks; }
    public int slotIndex() { return slotIndex; }
    public int shotIndex() { return shotIndex; }
    @Nullable public UUID roundId() { return roundId; }
    public boolean isAdmitted() { return entityData.get(ADMITTED); }
    public boolean isLeaseActive() {
        return !level().isClientSide && isAdmitted() && !isRemoved()
                && BorrowedNeedleEscrow.leaseActive(owner, this, nonce, lease);
    }

    @Override public void tick() {
        // No vanilla projectile/baseTick damage path. The closed controller owns native collision and movement.
        if (slotIndex >= 0 || level().isClientSide && isAdmitted()) setOldPosAndRot();
        if (slotIndex < 0) setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) return;
        if (!isLeaseActive()) {
            BorrowedNeedleEscrow.projectionRemoved(owner, this);
            revoke();
            discard();
        } else if (++leaseTicks >= MAX_LEASE_TICKS) {
            if (slotIndex < 0) BorrowedNeedleEscrow.retire(owner);
            else BorrowedNeedleEscrow.retireShot(this);
        } else if (shotTick != null) {
            try { shotTick.tick(this); }
            catch (RuntimeException failure) {
                BorrowedNeedleEscrow.shotFailed(this, "shot_tick_exception:" + failure.getClass().getSimpleName());
            } catch (Error failure) {
                BorrowedNeedleEscrow.shotFailed(this, "shot_tick_error:" + failure.getClass().getSimpleName());
                throw failure;
            }
        }
    }

    @Override public void remove(RemovalReason reason) {
        if (removing) return;
        removing = true;
        try {
            BorrowedNeedleEscrow.projectionRemoved(owner, this);
            revoke();
            super.remove(reason);
        } finally { removing = false; }
    }

    @Override public void onRemovedFromLevel() {
        boolean wasRemoving = removing;
        removing = true;
        try {
            BorrowedNeedleEscrow.projectionRemoved(owner, this);
            revoke();
            super.onRemovedFromLevel();
        } finally { removing = wasRemoving; }
    }

    @Override public boolean isPickable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean save(CompoundTag tag) { return false; }
    @Override public boolean saveAsPassenger(CompoundTag tag) { return false; }
    @Override protected void addAdditionalSaveData(CompoundTag tag) { }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        // Even an explicit Entity.load cannot restore a live lease.
        BorrowedNeedleEscrow.projectionRemoved(owner, this);
        revoke();
        owner = null;
        ownerId = null;
        nonce = 0;
        roundId = null;
        slotIndex = shotIndex = -1;
        leaseTicks = 0;
    }
}
