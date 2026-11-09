package cn.academy.port.highpower;

import cn.academy.port.AcademyCraft;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * NEW311/NEW321/NEW331/NEW341's native physical medium. Exact entity types separate their custody.
 * This class never applies combat damage.
 * Preparation, publication and durable-registry validation belong to the owner controller.
 * Neither construction, NBT loading nor setting a vanilla projectile owner admits a medium.
 */
public final class DisplacedNeedleEntity extends AbstractArrow {
    private static final String SAVE_KEY = "AcademyDisplacedNeedle";
    private static final String CROSS_SAVE_KEY = "AcademyCrossNeedle";
    private static final String INTERCEPTION_SAVE_KEY = "AcademyInterceptionNeedle";
    private static final String CORRIDOR_SAVE_KEY = "AcademyCorridorNeedle";
    private static final int INTERCEPTION_FLIGHT_TICKS = 20;
    private static final int SAVE_VERSION = 1;
    private static final EntityDataAccessor<Boolean> ADMITTED =
        SynchedEntityData.defineId(DisplacedNeedleEntity.class, EntityDataSerializers.BOOLEAN);

    public record Identity(UUID owner, long nonce, UUID entityId, long generation) {
        public Identity {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(entityId, "entityId");
            if (nonce <= 0 || generation < 0) throw new IllegalArgumentException("Invalid needle identity");
        }
    }

    /** All callbacks run on the server. Implementations must check their own live ledger. */
    public interface Listener {
        default boolean canContact(DisplacedNeedleEntity medium, Identity identity, Entity target) { return false; }
        default void onContact(DisplacedNeedleEntity medium, Identity identity, EntityHitResult hit) { }
        default void onFlightRetired(DisplacedNeedleEntity medium, Identity identity) { }
        default boolean canPickup(DisplacedNeedleEntity medium, Identity identity, Player player) { return false; }
        /** Persist transfer ambiguity before any inventory mutation. */
        default boolean beginPickup(DisplacedNeedleEntity medium, Identity identity, Player player) { return false; }
        default void onPickupFailed(DisplacedNeedleEntity medium, Identity identity, Player player, boolean originalUnchanged) { }
        default void onPickedUp(DisplacedNeedleEntity medium, Identity identity, Player player) { }
        default void onRemoved(DisplacedNeedleEntity medium, Identity identity, RemovalReason reason) { }
    }

    @Nullable private Identity identity;
    @Nullable private Listener listener;
    private boolean committedMedium;
    private boolean contactEnabled;
    private long lastArmedGeneration = -1;
    private int interceptionFlightRemaining;
    private boolean pickupInProgress;
    private boolean terminalNotified;
    private boolean nativeDespawn;
    private boolean impactInProgress;
    @Nullable private Vec3 nativeSegmentOrigin;
    @Nullable private Vec3 contactSegmentOrigin;
    @Nullable private Vec3 contactPoint;
    @Nullable private NativeImpact observingImpact;
    @Nullable private NativeImpact authorizedImpact;
    private long lastRetiredGeneration = -1;
    private long deflectionSerial;
    private long retirementSerial;
    @Nullable private Recovery recovery;
    private boolean trackingSuspended;
    private int trackingSuspendedTick;

    /** Opaque, short-lived proof for moving the existing medium without changing custody. */
    static final class Recovery {
        private final Identity identity;
        private final Entity owner;
        private final Level level;
        private final ItemStack original, item;
        private final Vec3 position, motion;
        private final AABB bounds;
        private final int nativeId, groundedTicks, shake, life;
        private final float yaw, pitch;
        private final boolean grounded, noPhysics, noGravity;
        private final Pickup pickup;
        private Recovery(DisplacedNeedleEntity medium) {
            identity = medium.identity; owner = medium.getOwner(); level = medium.level();
            original = medium.rawMedium(); item = original.copy();
            position = medium.position(); motion = medium.getDeltaMovement(); bounds = medium.getBoundingBox();
            nativeId = medium.getId(); groundedTicks = medium.inGroundTime; shake = medium.shakeTime;
            life = medium.nativeLife(); yaw = medium.getYRot(); pitch = medium.getXRot();
            grounded = medium.inGround; noPhysics = medium.isNoPhysics(); noGravity = medium.isNoGravity();
            pickup = medium.pickup;
        }
    }

    /** An opaque witness issued only while this entity is executing its native tick. */
    public static final class NativeImpact {
        private final DisplacedNeedleEntity medium;
        private final Identity identity;
        private final Listener listener;
        private final HitResult hit;
        private final Vec3 origin, position, motion;
        private final Entity owner;
        private final int nativeId;
        private final boolean armed;
        private final long retirementSerial;
        @Nullable private final Entity target;
        @Nullable private final AABB targetBounds;
        @Nullable private final Vec3 targetPosition;
        @Nullable private final UUID targetUuid;
        @Nullable private final Level targetLevel;
        @Nullable private final Vec3 contactPoint;
        private final int targetNativeId;

        private NativeImpact(DisplacedNeedleEntity medium, HitResult hit) {
            this.medium = medium;
            this.identity = medium.identity;
            this.listener = medium.listener;
            this.hit = hit;
            this.origin = medium.nativeSegmentOrigin;
            this.position = medium.position();
            this.motion = medium.getDeltaMovement();
            this.owner = medium.getOwner();
            this.nativeId = medium.getId();
            this.armed = medium.contactEnabled;
            this.retirementSerial = medium.retirementSerial;
            this.target = hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
            this.targetBounds = target == null ? null : target.getBoundingBox();
            this.targetPosition = target == null ? null : target.position();
            this.targetUuid = target == null ? null : target.getUUID();
            this.targetLevel = target == null ? null : target.level();
            this.targetNativeId = target == null ? -1 : target.getId();
            // The native ProjectileUtil overload chooses the first target using this
            // exact float margin but discards the clip point in EntityHitResult(entity).
            this.contactPoint = target == null ? null : targetBounds.inflate((double)0.3F)
                .clip(origin, origin.add(motion)).orElse(null);
        }
    }

    public DisplacedNeedleEntity(EntityType<? extends DisplacedNeedleEntity> type, Level level) {
        super(type, level);
        setBaseDamage(0);
        pickup = Pickup.DISALLOWED;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ADMITTED, false);
    }

    /** Accepts one already escrowed plain medium; this does not consume player inventory. */
    public boolean prepare(ServerPlayer owner, long nonce, long generation, ItemStack oneNeedle) {
        if (!registeredType() || level().isClientSide || isRemoved() || identity != null || owner == null || owner.level() != level()
            || nonce <= 0 || generation < 0 || ((isInterceptionNeedle() || isCorridorNeedle()) && generation != 1) || !isPlainNeedle(oneNeedle)) return false;
        identity = new Identity(owner.getUUID(), nonce, getUUID(), generation);
        super.setPickupItemStack(oneNeedle);
        setOwner(owner);
        return true;
    }

    /** Only the owner of an aborted, proven-removed pending publication may recover escrow. */
    public ItemStack releaseUnadmittedEscrow(Identity expected) {
        if (level().isClientSide || !isRemoved() || isAdmitted() || committedMedium
            || pickupInProgress || !matches(expected)) return ItemStack.EMPTY;
        ItemStack escrow = rawMedium();
        super.setPickupItemStack(ItemStack.EMPTY);
        identity = null;
        return escrow;
    }

    /** The caller must first verify/commit the corresponding owner ledger and escrow. */
    public boolean admit(Identity expected, Listener controller) {
        if (!registeredType() || level().isClientSide || isAdmitted() || isRemoved() || terminalNotified
            || !acceptsRegistry(controller)
            || !matches(expected) || !isPlainNeedle(rawMedium())) return false;
        listener = controller;
        committedMedium = true;
        contactEnabled = false;
        entityData.set(ADMITTED, true);
        pickup = Pickup.ALLOWED;
        setNoPhysics(false);
        setNoGravity(false);
        return true;
    }

    /** Retires any old contact before the controller evaluates a new attempt. */
    public boolean advanceGeneration(Identity expected, long nextGeneration) {
        if (isInterceptionNeedle() || isCorridorNeedle() || !live(expected) || pickupInProgress || impactBusy() || nextGeneration <= identity.generation()) return false;
        contactEnabled = false;
        identity = new Identity(identity.owner(), identity.nonce(), identity.entityId(), nextGeneration);
        return true;
    }

    /** Arms one contact notification, never damage, for an already committed generation. */
    public boolean beginFlight(Identity expected) {
        if (!live(expected) || pickupInProgress || impactBusy() || identity.generation() <= 0
            || identity.generation() <= lastArmedGeneration) return false;
        lastArmedGeneration = identity.generation();
        contactEnabled = true;
        if (isInterceptionNeedle()) interceptionFlightRemaining = INTERCEPTION_FLIGHT_TICKS;
        inGround = false;
        inGroundTime = 0;
        shakeTime = 0;
        setNoPhysics(false);
        setNoGravity(false);
        return true;
    }

    /** Retains this exact entity and item, with native gravity and block embedding. */
    public boolean markHarmless(Identity expected) {
        if (!live(expected)) return false;
        contactEnabled = false;
        retirementSerial++;
        if (isCorridorNeedle()) lastArmedGeneration = Math.max(lastArmedGeneration, identity.generation());
        return true;
    }

    @Nullable public Identity identity() { return identity; }
    public boolean isDisplacedNeedle() { return getType() == TeleporterNeedleEntities.DISPLACED_NEEDLE.get(); }
    public boolean isCrossNeedle() { return getType() == TeleporterNeedleEntities.CROSS_NEEDLE.get(); }
    public boolean isInterceptionNeedle() { return getType() == TeleporterNeedleEntities.INTERCEPTION_NEEDLE.get(); }
    public boolean isCorridorNeedle() { return getType() == TeleporterNeedleEntities.CORRIDOR_NEEDLE.get(); }
    private boolean registeredType() { return isDisplacedNeedle() || isCrossNeedle() || isInterceptionNeedle() || isCorridorNeedle(); }
    private boolean acceptsRegistry(Listener controller) {
        return isDisplacedNeedle() ? controller instanceof DisplacedNeedleRegistry
                : isCrossNeedle() ? controller instanceof CrossNeedleRegistry
                : isInterceptionNeedle() ? controller instanceof InterceptionNeedleRegistry
                : isCorridorNeedle() && controller instanceof CorridorNeedleRegistry;
    }
    private String saveKey() {
        return isCrossNeedle() ? CROSS_SAVE_KEY : isInterceptionNeedle() ? INTERCEPTION_SAVE_KEY
                : isCorridorNeedle() ? CORRIDOR_SAVE_KEY : SAVE_KEY;
    }
    private boolean hasForeignSaveKey(CompoundTag tag) {
        return !isDisplacedNeedle() && tag.contains(SAVE_KEY)
                || !isCrossNeedle() && tag.contains(CROSS_SAVE_KEY)
                || !isInterceptionNeedle() && tag.contains(INTERCEPTION_SAVE_KEY)
                || !isCorridorNeedle() && tag.contains(CORRIDOR_SAVE_KEY);
    }
    private boolean tryBindRegistry() {
        return isDisplacedNeedle() ? DisplacedNeedleRegistry.tryBind(this)
                : isCrossNeedle() ? CrossNeedleRegistry.tryBind(this)
                : isInterceptionNeedle() ? InterceptionNeedleRegistry.tryBind(this)
                : isCorridorNeedle() && CorridorNeedleRegistry.tryBind(this);
    }
    public boolean isAdmitted() { return entityData.get(ADMITTED); }
    public boolean isContactEnabled() { return contactEnabled && live(identity); }
    public ItemStack mediumSnapshot() { return rawMedium().copy(); }
    /** Pending publication must still hold the exact item object removed from the hand. */
    boolean holdsEscrow(Identity expected, ItemStack original) {
        return matches(expected) && rawMedium() == original && isPlainNeedle(original);
    }
    /** Actual incoming native segment origin, available only inside guarded onContact. */
    @Nullable public Vec3 contactSegmentOrigin() { return contactSegmentOrigin; }
    /** Verified intersection on the native-selected target, only inside guarded onContact. */
    @Nullable public Vec3 contactPoint() { return contactPoint; }
    public boolean impactBusy() { return impactInProgress || observingImpact != null || authorizedImpact != null || recovery != null; }

    /** Holds native tick/pickup/reprojection reentry until the registry closes this attempt. */
    @Nullable Recovery beginRecovery(Identity expected) {
        if (!live(expected) || pickupInProgress || impactBusy() || nativeSegmentOrigin != null) return null;
        Recovery attempt = new Recovery(this);
        if (attempt.life < 0 || attempt.life >= 1200) return null;
        recovery = attempt;
        markHarmless(expected);
        lastArmedGeneration = Math.max(lastArmedGeneration, expected.generation());
        lastRetiredGeneration = Math.max(lastRetiredGeneration, expected.generation());
        return attempt;
    }

    boolean recoveryUnchanged(Recovery attempt) {
        return recoveryIdentityUnchanged(attempt)
            && position().equals(attempt.position) && getBoundingBox().equals(attempt.bounds)
            && getDeltaMovement().equals(attempt.motion) && getYRot() == attempt.yaw && getXRot() == attempt.pitch
            && inGround == attempt.grounded && inGroundTime == attempt.groundedTicks && shakeTime == attempt.shake
            && pickup == attempt.pickup && isNoPhysics() == attempt.noPhysics && isNoGravity() == attempt.noGravity
            && nativeLife() == attempt.life && !contactEnabled;
    }

    private boolean recoveryIdentityUnchanged(Recovery attempt) {
        return attempt != null && recovery == attempt && live(attempt.identity) && !pickupInProgress
            && getOwner() == attempt.owner && level() == attempt.level && getId() == attempt.nativeId
            && rawMedium() == attempt.original && ItemStack.matches(rawMedium(), attempt.item);
    }

    /** No item transfer, new generation, or native lifetime reset. */
    boolean relocateRecovery(Recovery attempt, Vec3 destination) {
        if (!recoveryUnchanged(attempt) || destination == null
            || !Double.isFinite(destination.x) || !Double.isFinite(destination.y) || !Double.isFinite(destination.z)) return false;
        // Native AbstractArrow.startFalling resets its private life counter. Clear the old
        // embedding BEFORE setPos, so the next tick takes ordinary flight rather than that
        // branch. The later native block hit preserves life and resumes tickDespawn.
        // Never use move(non-SELF), shoot, lerpMotion, or an NBT read to perform this move.
        inGround = false;
        inGroundTime = 0;
        shakeTime = 0;
        setDeltaMovement(Vec3.ZERO);
        setNoPhysics(false);
        setNoGravity(false);
        setPos(destination);
        hasImpulse = true;
        // Native section/tracking callbacks run inside setPos; the barrier is still held.
        return recoveryIdentityUnchanged(attempt) && position().equals(destination)
            && getBoundingBox().equals(attempt.bounds.move(destination.subtract(attempt.position)))
            && getDeltaMovement().equals(Vec3.ZERO) && getYRot() == attempt.yaw && getXRot() == attempt.pitch
            && !inGround && inGroundTime == 0 && shakeTime == 0 && pickup == attempt.pickup
            && !isNoPhysics() && !isNoGravity() && nativeLife() == attempt.life && !contactEnabled;
    }

    void endRecovery(Recovery attempt) {
        if (recovery == attempt) recovery = null;
    }

    /** Read the pinned native field through its public save codec; never reload the entity. */
    private int nativeLife() {
        CompoundTag nativeState = new CompoundTag();
        super.addAdditionalSaveData(nativeState);
        return nativeState.getShort("life");
    }

    /** Called by the narrow native EventHooks seam, before any external impact callback. */
    @Nullable public NativeImpact beforeNativeImpact(HitResult hit) {
        if (hit == null || nativeSegmentOrigin == null || impactBusy() || pickupInProgress || !live(identity)) return null;
        observingImpact = new NativeImpact(this, hit);
        return observingImpact;
    }

    /** A thrown event is reported as canceled; a witness is usable for exactly one native hit. */
    public void afterNativeImpact(@Nullable NativeImpact observation, boolean canceled) {
        if (observation == null || observation != observingImpact || observation.medium != this) return;
        observingImpact = null;
        if (canceled || !impactMatches(observation) || (observation.armed && !contactEnabled)) {
            retireFlight(observation);
        } else {
            authorizedImpact = observation;
        }
    }

    private boolean impactMatches(NativeImpact observation) {
        return observation.medium == this && observation.origin != null && observation.origin == nativeSegmentOrigin
            && observation.position.equals(observation.origin)
            && live(observation.identity) && observation.listener == listener && observation.nativeId == getId()
            && observation.retirementSerial == retirementSerial
            && (!observation.armed || observation.identity.generation() > lastRetiredGeneration)
            && observation.owner == getOwner()
            && (!observation.armed || (observation.owner != null
                && observation.identity.owner().equals(observation.owner.getUUID())))
            && observation.position.equals(position()) && observation.motion.equals(getDeltaMovement())
            && targetMatches(observation);
    }

    private boolean targetMatches(NativeImpact observation) {
        return observation.target == null || (!observation.target.isRemoved()
            && observation.target.getId() == observation.targetNativeId
            && observation.target.getUUID().equals(observation.targetUuid)
            && observation.target.level() == observation.targetLevel
            && observation.target.position().equals(observation.targetPosition)
            && observation.target.getBoundingBox().equals(observation.targetBounds));
    }

    private void retireFlight(NativeImpact observation) {
        contactEnabled = false;
        if (observation.armed) notifyFlightRetired(observation.identity, observation.listener);
    }

    private void notifyFlightRetired(Identity retiredIdentity, Listener controller) {
        contactEnabled = false;
        retirementSerial++;
        lastArmedGeneration = Math.max(lastArmedGeneration, retiredIdentity.generation());
        if (retiredIdentity.generation() <= lastRetiredGeneration) return;
        lastRetiredGeneration = retiredIdentity.generation();
        boolean previousImpact = impactInProgress;
        impactInProgress = true;
        try {
            controller.onFlightRetired(this, retiredIdentity);
        } finally {
            contactEnabled = false;
            impactInProgress = previousImpact;
        }
    }

    public static boolean isPlainNeedle(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getCount() == 1
            && stack.is(AcademyCraft.NEEDLE.get()) && stack.isComponentsPatchEmpty();
    }

    private ItemStack rawMedium() { return super.getPickupItemStackOrigin(); }

    private boolean matches(@Nullable Identity expected) {
        return expected != null && expected.equals(identity) && expected.entityId().equals(getUUID());
    }

    private boolean live(@Nullable Identity expected) {
        return registeredType() && !level().isClientSide && !isRemoved() && !terminalNotified && !trackingSuspended && committedMedium
            && isAdmitted() && listener != null && matches(expected) && isPlainNeedle(rawMedium());
    }

    /** Called only after the registry verifies this exact ledger and both native indexes. */
    boolean resumeTracking(Identity expected, Listener controller) {
        if (!matches(expected) || listener != controller || !committedMedium || !isAdmitted()
                || isRemoved() || terminalNotified || !isPlainNeedle(rawMedium())) return false;
        if (trackingSuspended) {
            // Native stopTracking removes its visible indexes AFTER firing leave callbacks.
            // Do not let those callbacks mistake the still-present old indexes for reentry.
            if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)
                    || serverLevel.getServer().getTickCount() == trackingSuspendedTick) return false;
            trackingSuspended = false;
            contactEnabled = false;
        }
        return true;
    }

    @Override
    public void tick() {
        if (nativeSegmentOrigin != null || impactBusy()) return;
        if (!level().isClientSide && !isRemoved() && trackingSuspended) tryBindRegistry();
        if (!level().isClientSide && !isRemoved() && !isAdmitted() && committedMedium
            && identity != null && listener == null && !terminalNotified) {
            tryBindRegistry();
        }
        // A join-world callback may tick a pending entity before escrow commits. No native
        // movement, impact, game event, despawn or pickup may occur in that window.
        if (!isAdmitted() || (!level().isClientSide && !live(identity))) return;
        // NEW331 owns one finite native flight. Retirement changes only contact authority;
        // ordinary movement, embedding, pickup and native despawn continue on this entity.
        if (!level().isClientSide && isInterceptionNeedle() && contactEnabled) {
            if (interceptionFlightRemaining <= 0) notifyFlightRetired(identity, listener);
            else interceptionFlightRemaining--;
        }
        Vec3 previousOrigin = nativeSegmentOrigin;
        nativeSegmentOrigin = position();
        try {
            super.tick();
        } finally {
            nativeSegmentOrigin = previousOrigin;
            NativeImpact unfinished = observingImpact != null ? observingImpact : authorizedImpact;
            observingImpact = null;
            authorizedImpact = null;
            if (unfinished != null) retireFlight(unfinished);
            if (!level().isClientSide && isInterceptionNeedle() && contactEnabled
                    && interceptionFlightRemaining == 0) notifyFlightRetired(identity, listener);
        }
    }

    @Override
    public void setOwner(@Nullable Entity owner) {
        if (committedMedium && isAdmitted() && identity != null && listener != null && getOwner() != owner) {
            notifyFlightRetired(identity, listener);
        }
        if (identity != null && (owner == null || !identity.owner().equals(owner.getUUID()))) return;
        super.setOwner(owner);
        // AbstractArrow.setOwner normally grants pickup automatically for a player.
        pickup = committedMedium && isAdmitted() ? Pickup.ALLOWED : Pickup.DISALLOWED;
    }

    @Override
    public boolean deflect(ProjectileDeflection deflection, @Nullable Entity deflector,
                           @Nullable Entity owner, boolean byPlayer) {
        if (committedMedium && isAdmitted() && identity != null && listener != null) {
            notifyFlightRetired(identity, listener);
        }
        deflectionSerial++;
        return super.deflect(deflection, deflector, owner, byPlayer);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return isContactEnabled() && !pickupInProgress && super.canHitEntity(target);
    }

    @Override
    protected ProjectileDeflection hitTargetOrDeflectSelf(HitResult hit) {
        if (!level().isClientSide && !live(identity)) return ProjectileDeflection.NONE;
        // Avoid Projectile's redirectable-projectile combat side effect as well as the
        // vanilla arrow damage, fire, knockback, enchantment and consume-on-hit branches.
        if (hit instanceof EntityHitResult entityHit) {
            onHitEntity(entityHit);
            return ProjectileDeflection.NONE;
        }
        return super.hitTargetOrDeflectSelf(hit);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (!isContactEnabled() || pickupInProgress || impactInProgress) return;
        NativeImpact observation = authorizedImpact;
        if (observation == null || observation.hit != hit || !observation.armed
            || observation.contactPoint == null || !impactMatches(observation)) return;
        authorizedImpact = null;
        Identity contactIdentity = observation.identity;
        Listener controller = observation.listener;
        Vec3 incomingOrigin = observation.origin;
        long previousDeflection = deflectionSerial;
        contactEnabled = false; // retire before any controller/reentrant callback
        impactInProgress = true;
        EntityHitResult actualContact = new EntityHitResult(hit.getEntity(), observation.contactPoint);
        setPos(observation.contactPoint);
        try {
            if (incomingOrigin != null && controller.canContact(this, contactIdentity, hit.getEntity())
                && live(contactIdentity) && listener == controller && getOwner() == observation.owner
                && observation.retirementSerial == retirementSerial && contactIdentity.generation() > lastRetiredGeneration
                && getId() == observation.nativeId && position().equals(observation.contactPoint)
                && getDeltaMovement().equals(observation.motion) && targetMatches(observation)) {
                contactSegmentOrigin = incomingOrigin;
                contactPoint = observation.contactPoint;
                controller.onContact(this, contactIdentity, actualContact);
            }
        } finally {
            contactSegmentOrigin = null;
            contactPoint = null;
            contactEnabled = false;
            // Keep the real incoming projectile direction through native hurt/knockback.
            // A native deflection owns its new velocity; cleanup must not overwrite it.
            if (deflectionSerial == previousDeflection) setDeltaMovement(Vec3.ZERO);
            try { retireFlight(observation); }
            finally { impactInProgress = false; }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (impactInProgress || !isAdmitted() || (!level().isClientSide && !live(identity))) return;
        NativeImpact observation = authorizedImpact;
        if (!level().isClientSide && (observation == null || observation.hit != hit || !impactMatches(observation))) return;
        authorizedImpact = null;
        contactEnabled = false;
        impactInProgress = true;
        try {
            if (observation != null) retireFlight(observation);
            super.onHitBlock(hit);
        } finally {
            contactEnabled = false;
            impactInProgress = false;
        }
    }

    @Override
    public void playerTouch(Player player) {
        if (!live(identity) || pickupInProgress || impactBusy()) return;
        try {
            super.playerTouch(player);
        } finally {
            // Includes an exception from the owner callback or native take notification.
            if (terminalNotified && rawMedium().isEmpty() && !isRemoved()) discard();
        }
    }

    @Override
    protected boolean tryPickup(Player player) {
        if (!live(identity) || pickupInProgress || impactBusy() || pickup != Pickup.ALLOWED) return false;
        Identity pickupIdentity = identity;
        Listener controller = listener;
        pickupInProgress = true;
        try {
            if (!controller.canPickup(this, pickupIdentity, player)
                || !live(pickupIdentity) || listener != controller) return false;
            // Native Inventory.add erases excess items for creative players. Preflight
            // capacity so a full creative inventory also leaves this medium untouched.
            var inventory = player.getInventory();
            if (inventory.getSlotWithRemainingSpace(rawMedium()) < 0 && inventory.getFreeSlot() < 0) return false;
            ItemStack transfer = rawMedium();
            if (!controller.beginPickup(this, pickupIdentity, player)) return false;
            if (!live(pickupIdentity) || listener != controller || rawMedium() != transfer) {
                controller.onPickupFailed(this, pickupIdentity, player,
                    rawMedium() == transfer && isPlainNeedle(transfer));
                return false;
            }
            super.setPickupItemStack(ItemStack.EMPTY); // custody moves, never a pickup copy
            boolean added;
            try {
                added = inventory.add(transfer);
            } catch (RuntimeException | Error failure) {
                // Inventory may have changed before throwing. Never recreate ambiguous custody.
                try { controller.onPickupFailed(this, pickupIdentity, player, false); }
                finally { discard(); }
                throw failure;
            }
            if (!added || !transfer.isEmpty()) {
                boolean originalUnchanged = isPlainNeedle(transfer);
                if (originalUnchanged) super.setPickupItemStack(transfer);
                try { controller.onPickupFailed(this, pickupIdentity, player, originalUnchanged); }
                finally { if (!originalUnchanged) discard(); }
                return false;
            }
            terminalNotified = true;
            contactEnabled = false;
            committedMedium = false;
            pickup = Pickup.DISALLOWED;
            entityData.set(ADMITTED, false);
            controller.onPickedUp(this, pickupIdentity, player);
            return true;
        } finally {
            pickupInProgress = false;
            if (isRemoved()) notifyRemoval();
        }
    }

    /** Positive proof only during the pinned native1200-in-ground-tick discard callback. */
    @Override protected void tickDespawn() {
        nativeDespawn=true;
        try { super.tickDespawn(); } finally { nativeDespawn=false; }
    }
    boolean authenticNativeDespawnRemoval() {
        return nativeDespawn&&isRemoved()&&getRemovalReason()==RemovalReason.DISCARDED
                &&committedMedium&&identity!=null&&!pickupInProgress&&isPlainNeedle(rawMedium());
    }

    @Override
    public void remove(RemovalReason reason) {
        if (isRemoved()) return;
        contactEnabled = false;
        super.remove(reason);
        if (!pickupInProgress) notifyRemoval();
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        contactEnabled = false;
        // ServerLevel also calls this when tracking becomes HIDDEN, before assigning any
        // removal reason. That is not destruction and must not permanently tombstone custody.
        if (!level().isClientSide && !isRemoved() && getRemovalReason() == null) {
            trackingSuspended = true;
            trackingSuspendedTick = ((net.minecraft.server.level.ServerLevel)level()).getServer().getTickCount();
            if (!terminalNotified && committedMedium && listener != null && identity != null)
                notifyFlightRetired(identity, listener);
            return;
        }
        if (!pickupInProgress) notifyRemoval();
    }

    @Override
    public void setLevelCallback(EntityInLevelCallback callback) {
        super.setLevelCallback(callback);
        // Pinned native unloadEntity calls final Entity.setRemoved, bypassing remove().
        // Callback.onRemove installs NULL after removal even when hidden entities never
        // receive another onRemovedFromLevel. Non-null changes are not removal evidence.
        if (callback == EntityInLevelCallback.NULL && isRemoved() && getRemovalReason() != null) {
            contactEnabled = false;
            if (!pickupInProgress) notifyRemoval();
        }
    }

    private void notifyRemoval() {
        if (level().isClientSide || terminalNotified || listener == null || identity == null) return;
        terminalNotified = true;
        entityData.set(ADMITTED, false);
        pickup = Pickup.DISALLOWED;
        listener.onRemoved(this, identity, getRemovalReason());
    }

    @Override protected ItemStack getDefaultPickupItem() { return ItemStack.EMPTY; }
    @Override protected ItemStack getPickupItem() { return live(identity) ? rawMedium().copy() : ItemStack.EMPTY; }
    @Override public ItemStack getPickupItemStackOrigin() { return rawMedium().copy(); }
    @Override public SlotAccess getSlot(int slot) { return SlotAccess.NULL; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        tag.remove(SAVE_KEY);
        tag.remove(CROSS_SAVE_KEY);
        tag.remove(INTERCEPTION_SAVE_KEY);
        tag.remove(CORRIDOR_SAVE_KEY);
        tag.remove("item");
        // Native AbstractArrow cannot encode an empty stack. An unprepared/malformed or
        // already-transferred entity deliberately saves no usable medium or identity.
        if (!registeredType() || !isPlainNeedle(rawMedium())) return;
        super.addAdditionalSaveData(tag);
        tag.putByte("pickup", (byte)Pickup.DISALLOWED.ordinal());
        if (!committedMedium || identity == null || !matches(identity)) return;
        CompoundTag saved = new CompoundTag();
        saved.putInt("Version", SAVE_VERSION);
        saved.putUUID("Owner", identity.owner());
        saved.putLong("Nonce", identity.nonce());
        saved.putUUID("Entity", identity.entityId());
        saved.putLong("Generation", identity.generation());
        saved.putInt("Count", 1);
        tag.put(saveKey(), saved);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        identity = null;
        listener = null;
        committedMedium = false;
        contactEnabled = false;
        pickupInProgress = false;
        terminalNotified = false;
        impactInProgress = false;
        nativeSegmentOrigin = null;
        contactSegmentOrigin = null;
        contactPoint = null;
        observingImpact = null;
        authorizedImpact = null;
        lastRetiredGeneration = -1;
        deflectionSerial = 0;
        retirementSerial = 0;
        lastArmedGeneration = -1;
        interceptionFlightRemaining = 0;
        recovery = null;
        trackingSuspended = false;
        trackingSuspendedTick = 0;
        entityData.set(ADMITTED, false);

        CompoundTag safe = tag.copy();
        safe.putByte("pickup", (byte)Pickup.DISALLOWED.ordinal());
        safe.putDouble("damage", 0);
        safe.putBoolean("crit", false);
        safe.putByte("PierceLevel", (byte)0);
        safe.remove("weapon");
        safe.remove("SoundEvent");
        ItemStack savedItem = tag.contains("item", Tag.TAG_COMPOUND)
            ? ItemStack.parse(registryAccess(), tag.getCompound("item")).orElse(ItemStack.EMPTY)
            : ItemStack.EMPTY;
        if (registeredType() && tag.contains(saveKey(), Tag.TAG_COMPOUND) && isPlainNeedle(savedItem)
                && !hasForeignSaveKey(tag)) {
            CompoundTag saved = tag.getCompound(saveKey());
            if (saved.contains("Version", Tag.TAG_INT) && saved.getInt("Version") == SAVE_VERSION
                && saved.contains("Count", Tag.TAG_INT) && saved.getInt("Count") == 1
                && saved.hasUUID("Owner") && saved.hasUUID("Entity")
                && saved.contains("Nonce", Tag.TAG_LONG) && saved.getLong("Nonce") > 0
                && saved.contains("Generation", Tag.TAG_LONG) && saved.getLong("Generation") >= 0
                && (!(isInterceptionNeedle() || isCorridorNeedle()) || saved.getLong("Generation") == 1)
                && saved.getUUID("Entity").equals(getUUID()) && tag.hasUUID("Owner")
                && saved.getUUID("Owner").equals(tag.getUUID("Owner"))) {
                identity = new Identity(saved.getUUID("Owner"), saved.getLong("Nonce"),
                    saved.getUUID("Entity"), saved.getLong("Generation"));
                // A restored attack may never resume, even after durable identity validation.
                lastArmedGeneration = identity.generation();
                committedMedium = true;
            }
        }
        if (identity == null) safe.remove("item");
        super.readAdditionalSaveData(safe);
        pickup = Pickup.DISALLOWED;
        setNoPhysics(false);
        setNoGravity(false);
        clearFire();
    }
}
