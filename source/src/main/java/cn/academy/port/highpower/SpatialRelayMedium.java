package cn.academy.port.highpower;

import cn.academy.port.highpower.core.SpatialRelayRules;
import java.util.Objects;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One real native item. This binding never takes custody or mutates native physics or inventory. */
public final class SpatialRelayMedium {
    /** Reviewed native merge contract verifies this exact private int field; failed read denies admission. */
    private static final Field PICKUP_DELAY = pickupField();
    public final ItemEntity entity;
    public final UUID uuid;
    public final int entityId;
    private final ServerPlayer owner;
    private final ServerLevel world;
    private final SpatialRelayRules.Proof toss;
    private final ItemStack originalItem, fingerprint;
    private final int lifespan;
    private final UUID thrower, target;
    private int observedAge;
    private boolean retired;

    private SpatialRelayMedium(ServerPlayer owner, ItemEntity item) {
        this.owner = owner; entity = item; world = owner.serverLevel(); uuid = item.getUUID(); entityId = item.getId();
        toss = SpatialRelayProvenance.proof(item); originalItem = item.getItem(); fingerprint = originalItem.copy();
        lifespan = item.lifespan; observedAge = item.getAge(); thrower = thrower(item); target = item.getTarget();
    }
    public static SpatialRelayMedium capture(ServerPlayer owner, ItemEntity item) {
        if (owner == null || item == null || item.getClass() != ItemEntity.class || !owner.server.isSameThread()) return null;
        SpatialRelayMedium medium = new SpatialRelayMedium(owner, item);
        return medium.valid() ? medium : null;
    }
    boolean ownedBy(ServerPlayer player) { return owner == player; }
    public ItemStack stack() { return entity.getItem().copy(); }
    public void retire() { retired = true; }
    public boolean valid() {
        if (retired) return false;
        boolean valid = world.getServer().isSameThread() && owner.serverLevel() == world && !owner.isRemoved() && owner.isAlive()
                && entity.level() == world && !entity.isRemoved() && entity.isAlive() && entity.getClass() == ItemEntity.class
                && entity.getType() == EntityType.ITEM && entity.getId() == entityId && uuid.equals(entity.getUUID())
                && world.getEntity(uuid) == entity && world.getEntity(entityId) == entity && !entity.noPhysics
                && !entity.isNoGravity() && !entity.isPassenger() && !entity.isVehicle() && !entity.isInvulnerable()
                && !entity.getPersistentData().getBoolean("academy:protected") && !entity.getPersistentData().getBoolean("academy:immovable")
                && entity instanceof NativeItemMergeAccess seam && seam.academy$nativeItemMergeContractVersion() == 1
                && toss != null && toss.owner().equals(owner.getUUID()) && SpatialRelayRules.exactProof(toss, SpatialRelayProvenance.proof(entity))
                && originalItem == entity.getItem() && ItemStack.matches(fingerprint, entity.getItem())
                && finite(entity.position()) && finite(entity.getDeltaMovement()) && loaded(world, entity.getBoundingBox())
                && entity.getBoundingBox().equals(bodyAt(entity.position()))
                && pickupDelay(entity) >= 0 && pickupDelay(entity) <= 32767
                && lifespan > 0 && entity.lifespan == lifespan && entity.getAge() >= 0
                && entity.getAge() >= observedAge && entity.getAge() < lifespan
                && Objects.equals(thrower, thrower(entity)) && Objects.equals(target, entity.getTarget())
                && (target == null || target.equals(owner.getUUID()));
        if (!valid) { retired = true; return false; }
        observedAge = entity.getAge(); return true;
    }
    /** Includes exact full-width age/lifespan plus vanilla PickupDelay, Thrower, Owner and stack data. */
    public CompoundTag nativeState() {
        CompoundTag tag = new CompoundTag(); entity.addAdditionalSaveData(tag);
        tag.putInt("ExactAge", entity.getAge()); tag.putInt("ExactLifespan", entity.lifespan);
        tag.putInt("ExactPickupDelay", pickupDelay(entity));
        tag.putFloat("FallDistance", entity.fallDistance); tag.putBoolean("OnGround", entity.onGround());
        tag.putBoolean("NoGravity", entity.isNoGravity());
        return tag;
    }
    public Snapshot snapshot() {
        return new Snapshot(entity.position(), entity.getDeltaMovement(), entity.getBoundingBox(), entity.getYRot(),
                entity.getXRot(), nativeState(), stack(), toss);
    }
    public record Snapshot(Vec3 position, Vec3 motion, AABB body, float yaw, float pitch,
                           CompoundTag nativeState, ItemStack stack, SpatialRelayRules.Proof proof) {
        public boolean matches(SpatialRelayMedium medium) { return matchesAt(medium, position, body); }
        public boolean matchesMoved(SpatialRelayMedium medium, Vec3 destination) {
            return destination != null && matchesAt(medium, destination, bodyAt(destination));
        }
        private boolean matchesAt(SpatialRelayMedium medium, Vec3 position, AABB body) {
            return medium != null && medium.valid() && SpatialRelayRules.exactProof(proof, medium.toss)
                    && position.equals(medium.entity.position()) && body.equals(medium.entity.getBoundingBox())
                    && motion.equals(medium.entity.getDeltaMovement()) && Float.compare(yaw, medium.entity.getYRot()) == 0
                    && Float.compare(pitch, medium.entity.getXRot()) == 0 && ItemStack.matches(stack, medium.entity.getItem())
                    && nativeState.equals(medium.nativeState());
        }
    }
    static AABB bodyAt(Vec3 position) {
        return new AABB(position.x - .125, position.y, position.z - .125, position.x + .125, position.y + .25, position.z + .125);
    }
    private static Field pickupField() {
        try {
            Field field = ItemEntity.class.getDeclaredField("pickupDelay");
            return field.getType() == int.class && Modifier.isPrivate(field.getModifiers()) && field.trySetAccessible() ? field : null;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }
    private static int pickupDelay(ItemEntity item) {
        if (PICKUP_DELAY == null) return Integer.MIN_VALUE;
        try { return PICKUP_DELAY.getInt(item); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return Integer.MIN_VALUE; }
    }
    private static UUID thrower(ItemEntity item) {
        CompoundTag tag = new CompoundTag(); item.addAdditionalSaveData(tag);
        return tag.hasUUID("Thrower") ? tag.getUUID("Thrower") : null;
    }
    static boolean loaded(ServerLevel world, AABB body) {
        if (!Double.isFinite(body.minX) || !Double.isFinite(body.minY) || !Double.isFinite(body.minZ)
                || !Double.isFinite(body.maxX) || !Double.isFinite(body.maxY) || !Double.isFinite(body.maxZ)
                || body.getXsize() <= 0 || body.getYsize() <= 0 || body.getZsize() <= 0
                || body.getXsize() > .25000001 || body.getYsize() > .25000001 || body.getZsize() > .25000001) return false;
        int x0 = (int)Math.floor(body.minX) >> 4, x1 = (int)Math.floor(Math.nextDown(body.maxX)) >> 4;
        int z0 = (int)Math.floor(body.minZ) >> 4, z1 = (int)Math.floor(Math.nextDown(body.maxZ)) >> 4;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++)
            if (world.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
    static boolean finite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z)
                && Math.abs(v.x) <= 30_000_000 && Math.abs(v.z) <= 30_000_000 && Math.abs(v.y) <= 30_000_000;
    }
}
