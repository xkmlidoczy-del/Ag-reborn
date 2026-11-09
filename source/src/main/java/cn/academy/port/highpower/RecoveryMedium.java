package cn.academy.port.highpower;

import java.util.UUID;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Closed NEW323 medium binding. No caller-supplied adapters, replacement entities, or item transfer. */
final class RecoveryMedium {
    final Entity entity;
    final UUID uuid;
    final int entityId;
    private final ServerPlayer owner;
    private final ServerLevel world;
    private final DisplacedNeedleEntity.Identity displaced;
    private final MagneticItemProvenance.RecoveryProof toss;
    private final ItemStack originalItem, fingerprint;
    private final int originalLifespan;
    private final UUID originalThrower, originalTarget;
    private int observedAge;
    private boolean retired;

    private RecoveryMedium(ServerPlayer owner, Entity entity) {
        this.owner = owner; this.entity = entity; world = owner.serverLevel();
        uuid = entity.getUUID(); entityId = entity.getId();
        displaced = entity instanceof DisplacedNeedleEntity needle ? needle.identity() : null;
        toss = entity instanceof ItemEntity item ? MagneticItemProvenance.recoveryProof(item) : null;
        originalItem = entity instanceof ItemEntity item ? item.getItem() : null;
        fingerprint = stack().copy();
        originalLifespan = entity instanceof ItemEntity item ? item.lifespan : 0;
        observedAge = entity instanceof ItemEntity item ? item.getAge() : 0;
        originalThrower = entity instanceof ItemEntity item ? thrower(item) : null;
        originalTarget = entity instanceof ItemEntity item ? item.getTarget() : null;
    }
    static RecoveryMedium bind(ServerPlayer owner, Entity entity) {
        if (owner == null || entity == null || !(entity instanceof DisplacedNeedleEntity needle
                && (needle.isDisplacedNeedle() || needle.isCrossNeedle() || needle.isInterceptionNeedle() || needle.isCorridorNeedle()) || entity.getClass() == ItemEntity.class)) return null;
        RecoveryMedium medium = new RecoveryMedium(owner, entity);
        return medium.valid() ? medium : null;
    }
    ItemStack stack() {
        return entity instanceof DisplacedNeedleEntity needle ? needle.mediumSnapshot() : ((ItemEntity)entity).getItem().copy();
    }
    boolean valid() {
        if (retired || !world.getServer().isSameThread() || owner.serverLevel() != world || entity.level() != world
                || entity.isRemoved() || !entity.isAlive() || entity.getId() != entityId || !uuid.equals(entity.getUUID())
                || world.getEntity(uuid) != entity || world.getEntity(entityId) != entity || entity.noPhysics
                || entity.isPassenger() || entity.isVehicle() || !finite(entity.position()) || !finite(entity.getDeltaMovement())
                || !loaded(world, entity.getBoundingBox()) || !DisplacedNeedleEntity.isPlainNeedle(stack())
                || !ItemStack.matches(fingerprint, stack())) return false;
        if (entity instanceof DisplacedNeedleEntity needle)
            return displaced != null && displaced.equals(needle.identity()) && displaced.owner().equals(owner.getUUID())
                    && displaced.entityId().equals(uuid) && needle.getOwner() == owner && needle.isAdmitted()
                    && (needle.isDisplacedNeedle() ? DisplacedNeedleRegistry.existing(owner).orElse(null) == needle
                        : needle.isCrossNeedle() ? CrossNeedleRegistry.authentic(owner, needle)
                        : needle.isInterceptionNeedle() ? InterceptionNeedleRegistry.authentic(owner, needle)
                        : needle.isCorridorNeedle() && CorridorNeedleRegistry.authentic(owner, needle));
        ItemEntity item = (ItemEntity)entity;
        int age = item.getAge();
        // The native finite lifetime may advance, never rewind or extend. An expiry
        // hook changing lifespan retires this narrow registration rather than renewing it.
        if (originalLifespan <= 0 || item.lifespan != originalLifespan || age < 0
                || age < observedAge || age >= originalLifespan || !Objects.equals(originalThrower, thrower(item))
                || !Objects.equals(originalTarget, item.getTarget())) { retired = true; return false; }
        observedAge = age;
        return item.getClass() == ItemEntity.class && item.getType() == EntityType.ITEM
                && item instanceof NativeItemMergeAccess seam && seam.academy$nativeItemMergeContractVersion() == 1
                && toss != null && toss.owner().equals(owner.getUUID()) && toss.entity().equals(uuid)
                && toss.equals(MagneticItemProvenance.recoveryProof(item)) && item.getItem() == originalItem
                && (item.getTarget() == null || owner.getUUID().equals(item.getTarget()))
                && !item.isInvulnerable() && !item.getPersistentData().getBoolean("academy:protected")
                && !item.getPersistentData().getBoolean("academy:immovable");
    }
    private static UUID thrower(ItemEntity item) {
        CompoundTag state = new CompoundTag(); item.addAdditionalSaveData(state);
        return state.hasUUID("Thrower") ? state.getUUID("Thrower") : null;
    }
    void retire() { retired = true; }
    boolean busy(boolean recovering) {
        return entity instanceof DisplacedNeedleEntity needle
                && (needle.isContactEnabled() || !recovering && needle.impactBusy());
    }
    /** Exact synchronous snapshot only: natural ticking between requests does not alter the registration. */
    CompoundTag nativeState() {
        CompoundTag tag = new CompoundTag();
        if (entity instanceof ItemEntity item) {
            item.addAdditionalSaveData(tag);
            tag.putInt("ExactAge", item.getAge());
            tag.putInt("ExactLifespan", item.lifespan);
            tag.putFloat("FallDistance", item.fallDistance);
            tag.putBoolean("OnGround", item.onGround());
            tag.putBoolean("NoGravity", item.isNoGravity());
        }
        return tag;
    }
    /** Exact native type selects one closed custody domain; proofs never cross domains. */
    boolean recover(Vec3 destination, BooleanSupplier guard, BooleanSupplier payment) {
        if (entity instanceof DisplacedNeedleEntity needle)
            return needle.isDisplacedNeedle()
                    ? DisplacedNeedleRegistry.recoverExisting(owner, needle, displaced, destination, guard, payment)
                    : needle.isCrossNeedle()
                        ? CrossNeedleRegistry.recoverExisting(owner, needle, displaced, destination, guard, payment)
                        : needle.isInterceptionNeedle()
                            ? InterceptionNeedleRegistry.recoverExisting(owner, needle, displaced, destination, guard, payment)
                            : needle.isCorridorNeedle() && CorridorNeedleRegistry.recoverExisting(owner, needle, displaced, destination, guard, payment);
        if (!valid() || !finite(destination)) return false;
        Vec3 position = entity.position(), motion = entity.getDeltaMovement();
        AABB body = entity.getBoundingBox();
        float yaw = entity.getYRot(), pitch = entity.getXRot();
        CompoundTag nativeBefore = nativeState();
        BooleanSupplier unchanged = () -> valid() && nativeBefore.equals(nativeState()) && entity.position().equals(position)
                && entity.getBoundingBox().equals(body) && entity.getDeltaMovement().equals(motion)
                && Float.compare(entity.getYRot(), yaw) == 0 && Float.compare(entity.getXRot(), pitch) == 0;
        if (!loaded(world, body.move(destination.subtract(position))) || !guard.getAsBoolean() || !unchanged.getAsBoolean()) return false;
        if (!payment.getAsBoolean() || !unchanged.getAsBoolean()) return false;
        // setPos is the only native item mutation. Do not reload NBT, reset age/delay,
        // rewrite ownership, insert inventory, synthesize an item, or touch its velocity.
        entity.setPos(destination);
        entity.hasImpulse = true;
        return valid() && nativeBefore.equals(nativeState()) && entity.position().equals(destination)
                && entity.getBoundingBox().equals(body.move(destination.subtract(position)))
                && entity.getDeltaMovement().equals(motion) && Float.compare(entity.getYRot(), yaw) == 0
                && Float.compare(entity.getXRot(), pitch) == 0;
    }
    private static boolean loaded(ServerLevel world, AABB body) {
        if (!Double.isFinite(body.minX) || !Double.isFinite(body.minY) || !Double.isFinite(body.minZ)
                || !Double.isFinite(body.maxX) || !Double.isFinite(body.maxY) || !Double.isFinite(body.maxZ)
                || body.getXsize() <= 0 || body.getYsize() <= 0 || body.getZsize() <= 0
                || body.getXsize() > 1 || body.getYsize() > 1 || body.getZsize() > 1) return false;
        int minX = ((int)Math.floor(body.minX)) >> 4, maxX = ((int)Math.floor(Math.nextDown(body.maxX))) >> 4;
        int minZ = ((int)Math.floor(body.minZ)) >> 4, maxZ = ((int)Math.floor(Math.nextDown(body.maxZ))) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (world.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
    private static boolean finite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z)
                && Math.abs(v.x) <= 30_000_032 && Math.abs(v.z) <= 30_000_032 && Math.abs(v.y) <= 4096;
    }
}
