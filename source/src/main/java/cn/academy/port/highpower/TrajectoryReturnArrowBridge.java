package cn.academy.port.highpower;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/** NEW431 permission for one existing native body call. This class never moves or edits an arrow. */
public final class TrajectoryReturnArrowBridge {
    private static final int LIMIT = 512;
    private static final String USED = "academy:trajectory_return_used";
    private static final Map<AbstractArrow, Claim> CLAIMS = new IdentityHashMap<>();
    private static final Map<AbstractArrow, Lease> LEASES = new IdentityHashMap<>();
    private static final Map<AbstractArrow, Tick> TICKS = new IdentityHashMap<>();
    private static final Map<LivingEntity, Contact> VICTIMS = new IdentityHashMap<>();
    private TrajectoryReturnArrowBridge() {}

    /** Accessors exist only when the same native arrow fingerprint that owns this seam was admitted. */
    // The accessors belong to the independently pinned native seam, not NEW331 authority.
    /** Callback-free reservation of the exact observed arrow during the controller's paid reversal. */
    public static final class Claim {
        final Snapshot snapshot;
        boolean paid, invalid;
        private Claim(Snapshot snapshot) { this.snapshot = snapshot; }
    }
    /** Immutable observation only; possessing it cannot create damage authority. */
    public static final class Snapshot {
        final ServerPlayer controller;
        final Arrow arrow;
        final LivingEntity shooter;
        final ServerLevel level;
        final UUID uuid, shooterUuid;
        final int id, shooterId, life, inGroundTime, ticks, shake;
        final AbstractArrow.Pickup pickupMode;
        final UUID rawOwnerUuid;
        final boolean leftOwner;
        final double baseDamage;
        final ItemStack pickup, weapon;
        final Vec3 position, velocity;
        private Snapshot(ServerPlayer controller, Arrow arrow) {
            this.controller = controller; this.arrow = arrow; shooter = owner(arrow); level = controller.serverLevel();
            uuid = arrow.getUUID(); id = arrow.getId(); shooterUuid = shooter.getUUID(); shooterId = shooter.getId();
            baseDamage = arrow.getBaseDamage(); pickup = arrow.getPickupItemStackOrigin().copy();
            weapon = arrow.getWeaponItem() == null ? null : arrow.getWeaponItem().copy();
            position = arrow.position(); velocity = arrow.getDeltaMovement();
            ShortInterceptionArrowBridge.MetadataAccess metadata = (ShortInterceptionArrowBridge.MetadataAccess)arrow;
            life = metadata.academy$interceptionLife(); inGroundTime = metadata.academy$interceptionInGroundTime();
            ticks = arrow.tickCount; shake = arrow.shakeTime; pickupMode = arrow.pickup;
            NativeScatterArrowOwnerAccess owner = (NativeScatterArrowOwnerAccess)arrow;
            rawOwnerUuid = owner.academy$scatterOwnerUuid(); leftOwner = owner.academy$scatterLeftOwner();
        }
        public Arrow arrow() { return arrow; }
        public LivingEntity shooter() { return shooter; }
        public Vec3 position() { return position; }
        public Vec3 velocity() { return velocity; }
    }
    private static final class Lease {
        final TrajectoryReturn.EmissionPermit permit;
        final Snapshot snapshot;
        final long armed;
        Vec3 lastPosition, expectedPosition;
        double traveled;
        boolean consumed, invalid;
        Lease(TrajectoryReturn.EmissionPermit permit) {
            this.permit = permit; snapshot = new Snapshot(permit.controller(), permit.arrow());
            armed = now(snapshot.level); lastPosition = snapshot.position; expectedPosition = snapshot.position;
        }
    }
    public static final class Tick {
        final AbstractArrow arrow;
        final Lease lease;
        EntityHitResult nativeHit;
        HitResult pending;
        Vec3 contactPoint, segmentStart, segmentVelocity;
        LivingEntity target;
        UUID targetUuid;
        int targetId;
        Vec3 targetPosition;
        AABB targetBounds;
        net.minecraft.world.entity.Pose targetPose;
        TrajectoryReturn.ContactState controller;
        Contact contact;
        boolean nativeImpact, used, invalid, reported;
        private Tick(AbstractArrow arrow, Lease lease) { this.arrow = arrow; this.lease = lease; }
    }
    /** Only the pinned tick/impact/hurt stack creates a contact. No public factory exists. */
    static final class Contact {
        final Tick tick;
        final Lease lease;
        final DamageSource original;
        final Vec3 origin;
        final long serverTick;
        final BigDecimal health, shield;
        final float absorption;
        int status = TrajectoryReturn.REFUSED;
        boolean confirmed;
        private Contact(Tick tick, DamageSource original) {
            this.tick = tick; lease = tick.lease; this.original = original; origin = original.getSourcePosition();
            serverTick = now(lease.snapshot.level); health = health(tick.target); shield = HighPowerCombat.shield(tick.target);
            absorption = tick.target.getAbsorptionAmount();
        }
        TrajectoryReturn.EmissionPermit permit() { return lease.permit; }
        LivingEntity target() { return tick.target; }
    }

    public static boolean eligibleArrow(ServerPlayer controller, Arrow arrow) {
        return plain(controller, arrow) && !arrow.getPersistentData().contains(USED) && !leased(arrow) && !foreignLease(arrow);
    }
    public static Snapshot snapshot(ServerPlayer controller, Arrow arrow) {
        return eligibleArrow(controller, arrow) ? new Snapshot(controller, arrow) : null;
    }
    public static boolean matches(Snapshot snapshot) {
        return snapshot != null && reversalIdentity(snapshot) && eligibleArrow(snapshot.controller, snapshot.arrow)
                && snapshot.position.equals(snapshot.arrow.position()) && snapshot.velocity.equals(snapshot.arrow.getDeltaMovement());
    }
    public static Claim reserveReverse(Snapshot snapshot) {
        if (!matches(snapshot) || CLAIMS.size() >= LIMIT || LEASES.containsKey(snapshot.arrow)) return null;
        Claim claim = new Claim(snapshot); CLAIMS.put(snapshot.arrow, claim); return claim;
    }
    public static boolean markPaid(Claim claim) {
        if (claim == null || claim.invalid || claim.paid || CLAIMS.get(claim.snapshot.arrow) != claim
                || !reversalIdentity(claim.snapshot) || !plain(claim.snapshot.controller, claim.snapshot.arrow)
                || foreignLease(claim.snapshot.arrow) || !claim.snapshot.position.equals(claim.snapshot.arrow.position())
                || !claim.snapshot.velocity.equals(claim.snapshot.arrow.getDeltaMovement())
                || claim.snapshot.arrow.getPersistentData().contains(USED)) return false;
        claim.paid = true;
        // This denial-only tombstone survives unload, reload and a later controller session.
        claim.snapshot.arrow.getPersistentData().putBoolean(USED, true);
        return true;
    }
    public static boolean matchesReversed(Snapshot snapshot, Vec3 position, Vec3 velocity, Claim claim) {
        return claim != null && claim.snapshot == snapshot && !claim.invalid && claim.paid && CLAIMS.get(snapshot.arrow) == claim
                && reversalIdentity(snapshot) && plain(snapshot.controller, snapshot.arrow) && !foreignLease(snapshot.arrow)
                && snapshot.arrow.getPersistentData().getBoolean(USED) && finite(position) && finite(velocity)
                && position.equals(snapshot.position) && velocity.equals(snapshot.velocity.scale(-1))
                && position.equals(snapshot.arrow.position()) && velocity.equals(snapshot.arrow.getDeltaMovement());
    }
    public static void release(Claim claim) {
        if (claim != null) { CLAIMS.remove(claim.snapshot.arrow, claim); claim.invalid = true; }
    }
    /** Only the controller's private, committed permit supplies the frozen 2D amount. */
    public static boolean arm(TrajectoryReturn.EmissionPermit permit, Claim claim) {
        if (permit == null || claim == null || claim.invalid || !claim.paid || CLAIMS.get(permit.arrow()) != claim
                || claim.snapshot.controller != permit.controller() || claim.snapshot.arrow != permit.arrow()
                || !reversalIdentity(claim.snapshot) || !plain(permit.controller(), permit.arrow()) || foreignLease(permit.arrow())
                || !matchesReversed(claim.snapshot, claim.snapshot.position, claim.snapshot.velocity.scale(-1), claim)
                || !TrajectoryReturn.emissionMatches(permit) || permit.shooter() != owner(permit.arrow())
                || !finite(permit.origin()) || !permit.origin().equals(permit.arrow().position())
                || !Double.isFinite(permit.travelBudget()) || permit.travelBudget() <= 0 || permit.travelBudget() > 40
                || LEASES.containsKey(permit.arrow()) || LEASES.size() >= LIMIT) return false;
        Lease lease = new Lease(permit);
        if (permit.expiresAt() <= lease.armed || permit.expiresAt() - lease.armed > 20 || !valid(lease)) return false;
        LEASES.put(permit.arrow(), lease);
        return true;
    }
    public static boolean leased(AbstractArrow arrow) {
        Claim claim = CLAIMS.get(arrow);
        if (claim != null && !claim.invalid) return true;
        Lease lease = LEASES.get(arrow);
        return lease != null && !lease.invalid && (!lease.consumed || TICKS.containsKey(arrow));
    }
    public static void retire(AbstractArrow arrow) {
        Claim claim = CLAIMS.get(arrow); if (claim != null) claim.invalid = true;
        Lease lease = LEASES.get(arrow); if (lease != null) lease.invalid = true;
        Tick tick = TICKS.get(arrow); if (tick != null) tick.invalid = true;
    }
    public static void ownerChanged(Entity entity) { if (entity instanceof AbstractArrow arrow) retire(arrow); }
    public static void invalidate(ServerPlayer controller) {
        for (Claim claim : CLAIMS.values()) if (claim.snapshot.controller == controller) claim.invalid = true;
        for (Lease lease : LEASES.values()) if (lease.permit.controller() == controller) retire(lease.snapshot.arrow);
    }
    public static void prune(MinecraftServer server) {
        LEASES.entrySet().removeIf(entry -> {
            Lease lease = entry.getValue();
            if (lease.snapshot.level.getServer() != server || TICKS.containsKey(entry.getKey())) return false;
            if (!valid(lease)) lease.invalid = true;
            return lease.snapshot.arrow.isRemoved() || now(lease.snapshot.level) >= lease.permit.expiresAt();
        });
        VICTIMS.entrySet().removeIf(e -> e.getValue().lease.snapshot.level.getServer() == server
                && e.getValue().serverTick != now(e.getValue().lease.snapshot.level));
    }
    public static void clear() { LEASES.clear(); TICKS.clear(); CLAIMS.clear(); VICTIMS.clear(); }

    public static Tick beginTick(AbstractArrow arrow) {
        Tick prior = TICKS.get(arrow);
        if (prior != null) { retire(arrow); return null; }
        Lease lease = LEASES.get(arrow);
        if (lease == null || lease.consumed || !valid(lease) || !arrow.position().equals(lease.expectedPosition)) { if (lease != null) retire(arrow); return null; }
        // The previous native tick's actual movement, including drag/gravity, spends the remaining path.
        lease.traveled += lease.lastPosition.distanceTo(arrow.position()); lease.lastPosition = arrow.position();
        if (!Double.isFinite(lease.traveled) || lease.traveled > lease.permit.travelBudget()) { retire(arrow); return null; }
        Tick tick = new Tick(arrow, lease); TICKS.put(arrow, tick); return tick;
    }
    public static void endTick(Tick tick) {
        if (tick == null || TICKS.get(tick.arrow) != tick) return;
        TICKS.remove(tick.arrow);
        if (!tick.lease.consumed && !tick.invalid && valid(tick.lease)) {
            // Only the pinned native movement itself authorizes a trail, never the collision search's predicted endpoint.
            if (tick.segmentStart == null || tick.segmentVelocity == null
                    || !tick.arrow.position().equals(tick.segmentStart.add(tick.segmentVelocity))
                    || tick.lease.traveled + tick.segmentStart.distanceTo(tick.arrow.position()) > tick.lease.permit.travelBudget()) retire(tick.arrow);
            else TrajectoryReturn.onArrowFlight(tick.lease.permit, tick.segmentStart, tick.arrow.position(), tick.arrow.getDeltaMovement());
        }
        tick.lease.expectedPosition = tick.arrow.position();
        if (tick.lease.consumed || !valid(tick.lease)) tick.lease.invalid = true;
        if (tick.lease.consumed) report(tick);
    }
    /** Native first-solid-clipped search has already run exactly once. */
    public static void observe(AbstractArrow arrow, Vec3 start, Vec3 end, EntityHitResult hit) {
        Tick tick = TICKS.get(arrow);
        if (tick == null) return;
        if (tick.invalid || tick.lease.consumed || tick.segmentStart != null || !valid(tick.lease) || !finite(start) || !finite(end)
                || !start.equals(arrow.position())) { retire(arrow); return; }
        tick.nativeHit = hit; tick.segmentStart = start; tick.segmentVelocity = arrow.getDeltaMovement();
        tick.controller = new TrajectoryReturn.ContactState(tick.lease.permit);
        Vec3 terminal = end;
        if (hit != null) {
            AABB body = hit.getEntity().getBoundingBox().inflate((double).3F);
            var clip = body.contains(start) ? java.util.Optional.of(start) : body.clip(start, end);
            if (clip.isEmpty()) { retire(arrow); return; }
            terminal = clip.get();
            if (hit.getEntity() instanceof LivingEntity living) {
                tick.target = living; tick.targetUuid = living.getUUID(); tick.targetId = living.getId();
                tick.targetPosition = living.position(); tick.targetBounds = living.getBoundingBox(); tick.targetPose = living.getPose();
            }
        }
        tick.contactPoint = terminal;
        if (tick.lease.traveled + start.distanceTo(terminal) > tick.lease.permit.travelBudget()
                || hit != null && !withinContactReach(tick.lease.permit, terminal)) retire(arrow);
    }
    /** Retire the claim before any projectile-impact observer can cancel, mutate or reenter. */
    public static void beforeImpact(AbstractArrow arrow, HitResult hit) {
        Tick tick = TICKS.get(arrow);
        if (tick == null) { if (LEASES.containsKey(arrow)) retire(arrow); return; }
        Lease lease = tick.lease;
        boolean admitted = !lease.consumed && !tick.invalid && validTick(tick)
                && hit == tick.nativeHit && hit instanceof EntityHitResult body && body.getEntity() == tick.target && validTarget(tick);
        lease.consumed = true;
        if (admitted) tick.pending = hit; else retire(arrow);
    }
    public static void afterImpactEvent(AbstractArrow arrow, HitResult hit, boolean canceled) {
        Tick tick = TICKS.get(arrow);
        if (tick != null && (canceled || tick.pending != hit || !validTick(tick))) retire(arrow);
    }
    public static Tick beginImpact(AbstractArrow arrow, HitResult hit) {
        Tick tick = TICKS.get(arrow);
        if (tick == null || tick.invalid || tick.pending != hit || tick.nativeImpact || !validTick(tick)) {
            if (tick != null) retire(arrow); return null;
        }
        tick.nativeImpact = true; return tick;
    }
    public static void endImpact(Tick tick) {
        if (tick != null) { tick.nativeImpact = false; tick.pending = null; tick.lease.invalid = true; report(tick); }
    }
    /** Invoke the original operand once, retaining its real native boolean continuation. */
    public static boolean nativeHurt(AbstractArrow arrow, Entity target, DamageSource source, float amount, Operation<Boolean> original) {
        Tick tick = TICKS.get(arrow);
        if (tick == null || tick.invalid || !tick.nativeImpact || tick.used || tick.pending == null)
            return original.call(target, source, amount);
        tick.used = true;
        if (target != tick.target || !Float.isFinite(amount) || amount <= 0 || !nativeSource(tick.lease, source)
                || !validTick(tick) || !validTarget(tick)) { retire(arrow); return original.call(target, source, amount); }
        Contact prior = VICTIMS.get(tick.target);
        if (prior != null && prior.serverTick == now(tick.lease.snapshot.level)) {
            retire(arrow); return original.call(target, source, amount);
        }
        tick.contact = new Contact(tick, source);
        // Consume this body's opportunity before any combat callback can reenter on a different arrow.
        VICTIMS.put(tick.target, tick.contact);
        boolean accepted = HighPowerCombat.returnNativeArrow(tick.contact, amount, original);
        if (accepted && tick.contact.confirmed) {
            if (health(tick.target).compareTo(tick.contact.health) < 0) tick.contact.status = TrajectoryReturn.DAMAGED;
            else if (HighPowerCombat.shield(tick.target).compareTo(tick.contact.shield) < 0
                    || tick.target.getAbsorptionAmount() < tick.contact.absorption) tick.contact.status = TrajectoryReturn.ABSORBED;
        }
        return accepted;
    }
    private static void report(Tick tick) {
        if (tick.reported) return;
        tick.reported = true;
        boolean confirmed = tick.contact != null && tick.contact.confirmed && tick.contact.status != TrajectoryReturn.REFUSED;
        TrajectoryReturn.onArrowOutcome(tick.lease.permit, confirmed, confirmed ? tick.contact.status : TrajectoryReturn.REFUSED,
                // Canceled impact events/search predictions never become visible physical contacts.
                tick.used ? tick.segmentStart : null, tick.used ? tick.contactPoint : null, tick.targetUuid);
    }
    static boolean validContact(Contact contact) {
        if (contact == null) return false;
        Tick tick = contact.tick;
        return TICKS.get(tick.arrow) == tick && LEASES.get(tick.arrow) == contact.lease
                && !tick.invalid && tick.used && tick.nativeImpact && tick.pending != null && contact.lease.consumed
                && VICTIMS.get(tick.target) == contact && contact.serverTick == now(contact.lease.snapshot.level)
                && withinContactReach(contact.lease.permit, tick.contactPoint)
                && validTick(tick) && validTarget(tick) && nativeSource(contact.lease, contact.original)
                // Hostility/permission checks above may call out. Finish on callback-free observations.
                && identity(contact.lease.snapshot) && tick.controller.matches()
                && tick.segmentStart.equals(tick.arrow.position()) && tick.segmentVelocity.equals(tick.arrow.getDeltaMovement());
    }
    /** Range never adds selection distance to a fresh forty metres of amplified reach. */
    private static boolean withinContactReach(TrajectoryReturn.EmissionPermit permit, Vec3 contact) {
        return finite(contact) && finite(permit.castOrigin()) && finite(permit.controller().position())
                && permit.castOrigin().distanceToSqr(contact) <= 40D * 40D
                && permit.controller().position().distanceToSqr(contact) <= 40D * 40D;
    }
    private static boolean validTick(Tick tick) {
        return valid(tick.lease) && tick.segmentStart != null && tick.segmentVelocity != null
                && tick.controller != null && tick.controller.matches()
                && tick.segmentStart.equals(tick.arrow.position()) && tick.segmentVelocity.equals(tick.arrow.getDeltaMovement());
    }
    private static boolean validTarget(Tick tick) {
        LivingEntity target = tick.target;
        if (target == null || !TrajectoryReturn.hostile(tick.lease.permit.controller(), target)) return false;
        return tick.targetUuid.equals(target.getUUID()) && tick.targetId == target.getId()
                && tick.targetPosition.equals(target.position()) && tick.targetPose == target.getPose() && tick.targetBounds.equals(target.getBoundingBox());
    }
    private static boolean nativeSource(Lease lease, DamageSource source) {
        return source != null && source.getClass() == DamageSource.class && source.is(DamageTypes.ARROW)
                && source.getDirectEntity() == lease.snapshot.arrow && source.getEntity() == lease.snapshot.shooter
                && source.sourcePositionRaw() == null && lease.snapshot.arrow.position().equals(source.getSourcePosition());
    }
    private static boolean valid(Lease lease) {
        long now = now(lease.snapshot.level);
        return !lease.invalid && now >= lease.armed && now < lease.permit.expiresAt() && identity(lease.snapshot)
                && plain(lease.permit.controller(), lease.snapshot.arrow) && !foreignLease(lease.snapshot.arrow)
                && TrajectoryReturn.emissionMatches(lease.permit);
    }
    private static boolean reversalIdentity(Snapshot s) {
        if (!identity(s) || !(s.arrow instanceof ShortInterceptionArrowBridge.MetadataAccess metadata)) return false;
        return metadata.academy$interceptionLife() == s.life && metadata.academy$interceptionInGroundTime() == s.inGroundTime
                && s.arrow.tickCount == s.ticks && s.arrow.shakeTime == s.shake && s.arrow.pickup == s.pickupMode;
    }
    private static boolean identity(Snapshot s) {
        Arrow arrow = s.arrow;
        return arrow.level() == s.level && arrow.getId() == s.id && arrow.getUUID().equals(s.uuid)
                && s.level.getEntity(s.id) == arrow && s.level.getEntity(s.uuid) == arrow
                && arrow.getBaseDamage() == s.baseDamage && ItemStack.matches(s.pickup, arrow.getPickupItemStackOrigin())
                && (s.weapon == null ? arrow.getWeaponItem() == null : arrow.getWeaponItem() != null && ItemStack.matches(s.weapon, arrow.getWeaponItem()))
                && arrow instanceof NativeScatterArrowOwnerAccess access && access.academy$scatterLeftOwner() == s.leftOwner
                && java.util.Objects.equals(access.academy$scatterOwnerUuid(), s.rawOwnerUuid)
                && arrow.pickup == s.pickupMode && owner(arrow) == s.shooter && s.shooter.getId() == s.shooterId && s.shooter.getUUID().equals(s.shooterUuid)
                && s.level.getEntity(s.shooterId) == s.shooter && s.level.getEntity(s.shooterUuid) == s.shooter;
    }
    private static LivingEntity owner(AbstractArrow arrow) {
        return arrow instanceof NativeScatterArrowOwnerAccess access && access.academy$scatterCachedOwner() instanceof LivingEntity living
                && living.getUUID().equals(access.academy$scatterOwnerUuid()) && access.academy$scatterLeftOwner() ? living : null;
    }
    private static boolean plain(ServerPlayer controller, Arrow arrow) {
        if (controller == null || arrow == null || arrow.getClass() != Arrow.class || arrow.getType() != EntityType.ARROW
                || !(arrow instanceof ShortInterceptionArrowBridge.MetadataAccess) || !(arrow.level() instanceof ServerLevel world) || world != controller.level() || !world.getServer().isSameThread()
                || arrow.isRemoved() || !arrow.isAlive() || arrow.inGround || arrow.isNoPhysics() || arrow.noPhysics || arrow.isNoGravity()
                || arrow.isPassenger() || arrow.isVehicle() || arrow.isCritArrow() || arrow.isOnFire() || arrow.getPierceLevel() != 0
                || arrow.isInWaterOrBubble() || arrow.isInLava() || arrow.getPersistentData().getBoolean("academy:protected")
                || arrow.getPersistentData().getBoolean("academy:immovable")
                || !finite(arrow.position()) || !finite(arrow.getDeltaMovement()) || arrow.getDeltaMovement().lengthSqr() <= 0
                || arrow.getDeltaMovement().lengthSqr() > 8 * 8 || !Double.isFinite(arrow.getBaseDamage())
                || arrow.getBaseDamage() <= 0 || arrow.getBaseDamage() > 64
                || world.getEntity(arrow.getId()) != arrow || world.getEntity(arrow.getUUID()) != arrow) return false;
        LivingEntity shooter = owner(arrow);
        if (shooter == null || !(HighPowerCombat.hostileTarget(shooter)) || !VanillaCombatContract.supportedClass(shooter)
                || controller.isAlliedTo(shooter) || shooter.isAlliedTo(controller)
                || shooter.isInvulnerable() || shooter.noPhysics || !finite(shooter.position()) || !finite(shooter.getDeltaMovement())
                || shooter.getPersistentData().getBoolean("academy:protected") || HighPowerCombat.unsupportedReason(shooter).isPresent()
                || shooter.level() != world || !shooter.isAlive() || shooter.isRemoved() || shooter.isSpectator()
                || shooter.isPassenger() || shooter.isVehicle() || world.getEntity(shooter.getId()) != shooter
                || world.getEntity(shooter.getUUID()) != shooter) return false;
        var pickup = arrow.getPickupItemStackOrigin(); var weapon = arrow.getWeaponItem();
        return pickup.getCount() == 1 && ItemStack.isSameItemSameComponents(pickup, new ItemStack(Items.ARROW))
                && plainWeapon(weapon);
    }
    private static boolean plainWeapon(ItemStack weapon) {
        if (weapon == null) return true;
        if (weapon.getCount() != 1 || !(weapon.is(Items.BOW) || weapon.is(Items.CROSSBOW))
                || weapon.getDamageValue() < 0 || weapon.getDamageValue() >= weapon.getMaxDamage()) return false;
        // Real bow/crossbow firing retains ordinary durability; every other component must match
        // the built-in item. Native CrossbowItem clears CHARGED_PROJECTILES before constructing its arrow.
        ItemStack normalized = weapon.copy(); normalized.setDamageValue(0);
        return ItemStack.isSameItemSameComponents(normalized, new ItemStack(weapon.getItem()));
    }
    private static boolean foreignLease(AbstractArrow arrow) {
        return MultiAxisReflectionArrowBridge.leased(arrow) || ShortInterceptionArrowBridge.leased(arrow) || arrow.getPersistentData().contains(BrakingTouch.SPENT)
                || MagneticDomainArrows.leased(arrow) || MagneticArrowSeparation.leased(arrow) || IronSandLance.leased(arrow)
                || ElectromasterPrototype.leased(arrow) || ElectromasterChargeDecoy.leased(arrow) || MagneticGroundStep.leased(arrow)
                || ScatterPlateArrowReceipts.hasEvidence(arrow) || DissipationCurtainArrowReceipts.hasEvidence(arrow)
                || ZeroGapArmorArrowReceipts.hasEvidence(arrow);
    }
    private static long now(ServerLevel level) { return Integer.toUnsignedLong(level.getServer().getTickCount()); }
    private static BigDecimal health(LivingEntity target) { return HighPowerCombat.enabled(target)
            ? new BigDecimal(HighPowerCombat.healthText(target)) : BigDecimal.valueOf(target.getHealth()); }
    private static boolean finite(Vec3 value) { return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z)
            && Math.abs(value.x) <= 30_000_000 && Math.abs(value.y) <= 30_000_000 && Math.abs(value.z) <= 30_000_000; }
}
