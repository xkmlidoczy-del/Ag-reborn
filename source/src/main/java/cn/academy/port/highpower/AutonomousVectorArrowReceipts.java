package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeScatterPlateMixinPlugin;
import cn.academy.port.highpower.core.AutonomousVectorCircleRules;
import cn.academy.port.highpower.core.VectorParryRules;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** NEW452 read-only evidence for the current original native arrow sweep and body hurt.
 * No history, extrapolated origin, projectile write, damage call or resource authority lives here.
 * All public adapters require their actual transformed native host; a direct hurt cannot mint proof. */
public final class AutonomousVectorArrowReceipts {
    private static final int LIMIT = 512, SCAN_LIMIT = 64;
    private static final double EPS = 1e-8;
    private static final Map<AbstractArrow, Tick> TICKS = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Capture> WINDOWS = new IdentityHashMap<>();
    private AutonomousVectorArrowReceipts() {}

    private record Defender(ServerPlayer player, ServerLevel world, UUID uuid, int id,
                            Vec3 position, Vec3 eye, Vec3 look, AABB bounds, Pose pose, float yaw, float pitch, Object generation) {
        Defender(ServerPlayer player) { this(player, player.serverLevel(), player.getUUID(), player.getId(),
                player.position(), player.getEyePosition(), player.getLookAngle(), player.getBoundingBox(),
                player.getPose(), player.getYRot(), player.getXRot(), AutonomousVectorArrowReceipts.generation(player)); }
        boolean frozen() {
            // Native HP may temporarily be zero at Post; the controller owns that outer-frame check.
            return generation != null && generation == AutonomousVectorArrowReceipts.generation(player) && player.level() == world && !player.isRemoved() && player.getId() == id && player.getUUID().equals(uuid)
                    && world.getEntity(id) == player && world.getEntity(uuid) == player
                    && position.equals(player.position()) && eye.equals(player.getEyePosition()) && look.equals(player.getLookAngle())
                    && bounds.equals(player.getBoundingBox()) && pose == player.getPose() && yaw == player.getYRot() && pitch == player.getXRot();
        }
    }

    private static final class Snapshot {
        final Arrow arrow;
        final ServerLevel world;
        final LivingEntity shooter;
        final UUID uuid, shooterUuid;
        final int id, shooterId, age, life, inGroundTime, shake;
        final AbstractArrow.Pickup pickupMode;
        final double damage;
        final ItemStack pickup, weapon;
        final Vec3 position, velocity;
        Snapshot(Arrow arrow) {
            this.arrow = arrow; world = (ServerLevel) arrow.level(); shooter = shooter(arrow);
            uuid = arrow.getUUID(); id = arrow.getId(); shooterUuid = shooter.getUUID(); shooterId = shooter.getId();
            position = arrow.position(); velocity = arrow.getDeltaMovement(); damage = arrow.getBaseDamage();
            pickup = arrow.getPickupItemStackOrigin().copy(); weapon = arrow.getWeaponItem() == null ? null : arrow.getWeaponItem().copy();
            pickupMode = arrow.pickup; age = arrow.tickCount; shake = arrow.shakeTime;
            var access = (ShortInterceptionArrowBridge.MetadataAccess) arrow;
            life = access.academy$interceptionLife(); inGroundTime = access.academy$interceptionInGroundTime();
        }
        boolean frozen() {
            var access = (ShortInterceptionArrowBridge.MetadataAccess) arrow;
            return arrow.level() == world && arrow.getId() == id && arrow.getUUID().equals(uuid)
                    && world.getEntity(id) == arrow && world.getEntity(uuid) == arrow
                    && shooter(arrow) == shooter && shooter.getId() == shooterId && shooter.getUUID().equals(shooterUuid)
                    && world.getEntity(shooterId) == shooter && world.getEntity(shooterUuid) == shooter
                    && position.equals(arrow.position()) && velocity.equals(arrow.getDeltaMovement())
                    && damage == arrow.getBaseDamage() && pickupMode == arrow.pickup && age == arrow.tickCount && shake == arrow.shakeTime
                    && access.academy$interceptionLife() == life && access.academy$interceptionInGroundTime() == inGroundTime
                    && ItemStack.matches(pickup, arrow.getPickupItemStackOrigin())
                    && (weapon == null ? arrow.getWeaponItem() == null : arrow.getWeaponItem() != null && ItemStack.matches(weapon, arrow.getWeaponItem()));
        }
    }

    /** Opaque, bounded lifetime opened and closed only by the pinned native tick wrapper. */
    public static final class Tick {
        private final Arrow arrow;
        private Snapshot snapshot;
        private Defender defender;
        private EntityHitResult nativeHit;
        private Vec3 from, end, contact;
        private Capture window;
        private boolean observed, eventSeen, accepted, inImpact, hurtUsed, poisoned;
        private Tick(Arrow arrow) { this.arrow = arrow; }
    }

    /** A single native hurt's source-bound proof; constructors and all mutable state are private. */
    public static final class Capture {
        private final Tick tick;
        private final DamageSource source;
        private boolean claimed;
        private Capture(Tick tick, DamageSource source) { this.tick = tick; this.source = source; }
        public Vec3 from() { return tick.from; }
        public Vec3 contact() { return tick.contact; }
        public boolean valid(ServerPlayer player, DamageSource source) {
            try {
                return claimed && this.source == source && player != null && tick.defender.player == player
                        && WINDOWS.get(player) == this && tick.window == this && tick.inImpact && tick.hurtUsed
                        && live(tick) && nativeSource(tick, source) && contactClear(tick) && live(tick);
            } catch (RuntimeException | LinkageError failure) { poison(tick); return false; }
        }
    }

    public static boolean available() { return NativeScatterPlateMixinPlugin.autonomousVectorCircleAvailable(); }

    public static Tick beginTick(AbstractArrow arrow) {
        if (!nativeCaller("academy$observeNativeTick")) return null;
        Tick prior = TICKS.get(arrow);
        if (prior != null) { poison(prior); return null; }
        try {
            // Projectile.tick may establish leftOwner during this first genuine launch tick.
            // Full canonical admission happens after the original findHitEntity has returned.
            if (TICKS.size() >= LIMIT || arrow == null || arrow.getClass() != Arrow.class || arrow.getType() != EntityType.ARROW
                    || !(arrow.level() instanceof ServerLevel world) || !world.getServer().isSameThread() || !available()) return null;
            Tick tick = new Tick((Arrow) arrow); TICKS.put(arrow, tick); return tick;
        } catch (RuntimeException | LinkageError failure) { return null; }
    }

    /** Exact arguments and result from the original findHitEntity, before any impact callback. */
    public static void observe(AbstractArrow arrow, Vec3 start, Vec3 end, EntityHitResult result) {
        Tick tick = TICKS.get(arrow); if (tick == null) return;
        try {
            if (!nativeCaller("academy$observeClippedSegment") || tick.poisoned || tick.observed || !plain(arrow)
                    || !finite(start) || !finite(end) || !start.equals(arrow.position())
                    || start.distanceToSqr(end) <= EPS || start.distanceToSqr(end) > 64 * 64) { poison(tick); return; }
            tick.observed = true;
            if (result == null || !(result.getEntity() instanceof ServerPlayer player) || !(AutonomousVectorCircle.active(player) || VectorParry.active(player))
                    || player.level() != arrow.level() || !player.isAlive() || player.isRemoved() || player == shooter(arrow)
                    || player.isAlliedTo(shooter(arrow)) || shooter(arrow).isAlliedTo(player)
                    || !inRange(player.position(), start)) return;
            Defender defender = new Defender(player);
            AABB body = defender.bounds.inflate((double) .3F);
            if (body.contains(start)) return;
            var entry = body.clip(start, end);
            if (entry.isEmpty() || !aligned(start, entry.get(), arrow.getDeltaMovement())) return;
            // EntityHitResult(entity) reports feet, so only this first inflated-body entry is B.
            tick.snapshot = new Snapshot((Arrow) arrow); tick.defender = defender; tick.nativeHit = result;
            tick.from = start; tick.end = end; tick.contact = entry.get();
            if (!live(tick) || !contactClear(tick) || !live(tick)) poison(tick);
        } catch (RuntimeException | LinkageError failure) { poison(tick); }
    }

    public static void beforeImpact(AbstractArrow arrow, HitResult hit) {
        Tick tick = TICKS.get(arrow); if (tick == null) return;
        try {
            if (!nativeCaller("academy$consumeBeforeImpactCallbacks") || tick.eventSeen || !live(tick)
                    || hit != tick.nativeHit) { poison(tick); return; }
            tick.eventSeen = true;
        } catch (RuntimeException | LinkageError failure) { poison(tick); }
    }

    private static Object generation(ServerPlayer p) {
        Object circle = AutonomousVectorCircle.arrowGeneration(p);
        return circle != null ? circle : VectorParry.arrowGeneration(p);
    }
    /** Mirror the existing reviewedSource tag gate without manufacturing a
     * DamageSource. Datapacks can alter type tags while leaving its JSON vanilla. */
    static boolean ordinaryArrowType(ServerPlayer p) {
        var type = p.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.ARROW);
        if (!type.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) return false;
        for (var tag : List.of(net.minecraft.tags.DamageTypeTags.IS_FIRE, net.minecraft.tags.DamageTypeTags.IS_EXPLOSION,
                net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR, net.minecraft.tags.DamageTypeTags.BYPASSES_RESISTANCE,
                net.minecraft.tags.DamageTypeTags.BYPASSES_EFFECTS, net.minecraft.tags.DamageTypeTags.BYPASSES_ENCHANTMENTS,
                net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD, net.minecraft.tags.DamageTypeTags.BYPASSES_COOLDOWN,
                net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY, net.minecraft.tags.DamageTypeTags.IS_FREEZING,
                net.minecraft.tags.DamageTypeTags.DAMAGES_HELMET)) if (type.is(tag)) return false;
        return true;
    }
    private static Tick pending(ServerPlayer p, AbstractArrow arrow, HitResult hit) {
        Tick tick = TICKS.get(arrow);
        return tick != null && tick.eventSeen && !tick.accepted && !tick.inImpact && !tick.hurtUsed
                && tick.nativeHit == hit && tick.defender != null && tick.defender.player == p
                && live(tick) && contactClear(tick) && live(tick) ? tick : null;
    }
    public static boolean pendingCircleContact(ServerPlayer p, AbstractArrow arrow, HitResult hit) {
        try {
            Tick tick = pending(p, arrow, hit);
            return tick != null && AutonomousVectorCircle.pendingArrowAllowed(p, tick.arrow, tick.snapshot.shooter, tick.from, tick.contact)
                    && pending(p, arrow, hit) == tick;
        } catch (RuntimeException | LinkageError refused) { poison(TICKS.get(arrow)); return false; }
    }
    public static boolean pendingParryContact(ServerPlayer p, AbstractArrow arrow, HitResult hit) {
        try {
            Tick tick = pending(p, arrow, hit);
            return tick != null && VectorParry.pendingArrowAllowed(p, tick.arrow, tick.snapshot.shooter, tick.from)
                    && pending(p, arrow, hit) == tick;
        } catch (RuntimeException | LinkageError refused) { poison(TICKS.get(arrow)); return false; }
    }
    public static void afterImpactEvent(AbstractArrow arrow, HitResult hit, boolean canceled) {
        Tick tick = TICKS.get(arrow); if (tick == null) return;
        try {
            if (!nativeCaller("academy$consumeBeforeImpactCallbacks") || canceled || !tick.eventSeen
                    || hit != tick.nativeHit || !live(tick)) { poison(tick); return; }
            tick.accepted = true;
        } catch (RuntimeException | LinkageError failure) { poison(tick); }
    }

    public static Tick beginImpact(AbstractArrow arrow, HitResult hit) {
        Tick tick = TICKS.get(arrow); if (tick == null) return null;
        try {
            if (!nativeCaller("academy$nativeImpactWindow") || !tick.accepted || tick.inImpact || tick.hurtUsed
                    || hit != tick.nativeHit || !live(tick)) { poison(tick); return null; }
            tick.inImpact = true; return tick;
        } catch (RuntimeException | LinkageError failure) { poison(tick); return null; }
    }

    public static Capture beginHurt(AbstractArrow arrow, Entity target, DamageSource source, float amount) {
        Tick tick = TICKS.get(arrow); if (tick == null) return null;
        try {
            if (!nativeCaller("academy$nativeBodyHurtWindow") || !tick.inImpact || tick.hurtUsed || !live(tick)) return null;
            ServerPlayer player = tick.defender.player;
            // Parry borrows only this read-only pending-sweep evidence. Its actual
            // native hurt stays on its existing independently reviewed plan path.
            if (!AutonomousVectorCircle.active(player)) return null;
            tick.hurtUsed = true;
            if (target != player || !Float.isFinite(amount) || amount <= 0
                    || amount != (float) Math.ceil((double) (float) tick.snapshot.velocity.length() * tick.snapshot.damage)
                    || !nativeSource(tick, source) || WINDOWS.containsKey(player) || WINDOWS.size() >= LIMIT
                    || !contactClear(tick) || !live(tick)) { poison(tick); return null; }
            Capture window = new Capture(tick, source); tick.window = window; WINDOWS.put(player, window); return window;
        } catch (RuntimeException | LinkageError failure) { poison(tick); return null; }
    }

    /** Called by the controller only while this source is inside vanilla's one actual Entity.hurt. */
    public static Capture capture(ServerPlayer player, DamageSource source) {
        Capture window = WINDOWS.get(player);
        if (window == null || window.claimed || window.source != source) return null;
        window.claimed = true;
        return window.valid(player, source) ? window : null;
    }

    public static void endHurt(Capture window) {
        if (window != null) { WINDOWS.remove(window.tick.defender.player, window); window.tick.window = null; }
    }
    public static void endImpact(Tick tick) { if (tick != null) { endHurt(tick.window); tick.inImpact = false; tick.poisoned = true; } }
    public static void endTick(Tick tick) {
        if (tick != null) { endImpact(tick); TICKS.remove(tick.arrow, tick); }
    }
    /** Ending/restarting a controller invalidates proof even if the same pose is restored. */
    public static void invalidate(ServerPlayer player) {
        for (Tick tick : TICKS.values()) if (tick.defender != null && tick.defender.player == player) poison(tick);
        WINDOWS.remove(player);
    }
    /** A setter call, including writing the same owner again, invalidates this native proof. */
    public static void ownerChanged(Entity entity) { if (entity instanceof AbstractArrow arrow) poison(TICKS.get(arrow)); }
    public static void clear() { for (Tick tick : TICKS.values()) poison(tick); TICKS.clear(); WINDOWS.clear(); }

    private static void poison(Tick tick) { if (tick != null) { tick.poisoned = true; tick.accepted = false; endHurt(tick.window); } }
    private static boolean live(Tick tick) {
        return !tick.poisoned && TICKS.get(tick.arrow) == tick && tick.snapshot != null && tick.defender != null
                && tick.nativeHit != null && tick.nativeHit.getEntity() == tick.defender.player && tick.snapshot.frozen()
                && tick.defender.frozen() && plain(tick.arrow)
                && !tick.defender.player.isAlliedTo(tick.snapshot.shooter) && !tick.snapshot.shooter.isAlliedTo(tick.defender.player);
    }
    private static boolean nativeSource(Tick tick, DamageSource source) {
        return source != null && source.getClass() == DamageSource.class && source.is(DamageTypes.ARROW)
                && source.getDirectEntity() == tick.arrow && source.getEntity() == tick.snapshot.shooter
                && source.sourcePositionRaw() == null && tick.snapshot.position.equals(source.getSourcePosition());
    }

    /** Bounded, loaded-only verification of the same native first collision, repeated after callbacks. */
    private static boolean contactClear(Tick tick) {
        Vec3 start = tick.from, end = tick.contact;
        AABB defenderBody = tick.defender.bounds.inflate((double) .3F);
        if (!finite(start) || !finite(end) || defenderBody.contains(start)
                || !inRange(tick.defender.position, start) || !aligned(start, end, tick.snapshot.velocity)) return false;
        var entry = defenderBody.clip(start, tick.end);
        if (entry.isEmpty() || !entry.get().equals(end)) return false;
        AABB swept = new AABB(start, end).inflate(.301);
        if (!DisplacedProjectionWorld.volume(tick.defender.player, swept).isEmpty()
                || tick.snapshot.world.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, tick.arrow)).getType() != HitResult.Type.MISS) return false;
        List<Entity> bodies = new ArrayList<>();
        tick.snapshot.world.getEntities(EntityTypeTest.forClass(Entity.class), swept, entity -> entity != tick.arrow, bodies, SCAN_LIMIT + 1);
        if (bodies.size() > SCAN_LIMIT) return false;
        double terminal = start.distanceToSqr(end);
        for (Entity body : bodies) {
            if (body == tick.defender.player || body.isRemoved() || body.isSpectator()) continue;
            AABB bounds = body.getBoundingBox().inflate((double) .3F);
            if (bounds.contains(start)) return false;
            var contact = bounds.clip(start, end);
            if (contact.isPresent() && start.distanceToSqr(contact.get()) <= terminal + EPS) return false;
        }
        return true;
    }
    private static boolean aligned(Vec3 start, Vec3 end, Vec3 velocity) {
        if (!finite(start) || !finite(end) || !finite(velocity)) return false;
        Vec3 segment = end.subtract(start);
        double length = segment.lengthSqr(), speed = velocity.lengthSqr();
        // Clipping can round the certified collinear endpoints. These small tolerances
        // check native-sweep arithmetic only; the controller's 160-degree admission is exact.
        return Double.isFinite(length) && Double.isFinite(speed) && length > EPS && speed > EPS
                && length <= speed + EPS && segment.dot(velocity) > 0
                && segment.cross(velocity).lengthSqr() <= 1e-12 * length * speed;
    }
    private static boolean inRange(Vec3 owner, Vec3 from) {
        return AutonomousVectorCircleRules.inRange(new VectorParryRules.Point(owner.x, owner.y, owner.z),
                new VectorParryRules.Point(from.x, from.y, from.z));
    }
    private static LivingEntity shooter(AbstractArrow arrow) {
        return arrow instanceof NativeScatterArrowOwnerAccess access && access.academy$scatterLeftOwner()
                && access.academy$scatterCachedOwner() instanceof LivingEntity living
                && living.getUUID().equals(access.academy$scatterOwnerUuid()) ? living : null;
    }
    private static boolean plain(AbstractArrow arrow) {
        if (arrow == null || arrow.getClass() != Arrow.class || arrow.getType() != EntityType.ARROW
                || !(arrow instanceof ShortInterceptionArrowBridge.MetadataAccess) || !(arrow.level() instanceof ServerLevel world)
                || !world.getServer().isSameThread() || !available() || arrow.isRemoved() || !arrow.isAlive()
                || arrow.inGround || arrow.isNoPhysics() || arrow.noPhysics || arrow.isNoGravity() || arrow.shakeTime != 0
                || arrow.isPassenger() || arrow.isVehicle() || arrow.isCritArrow() || arrow.isOnFire() || arrow.getPierceLevel() != 0
                || arrow.isInWaterOrBubble() || arrow.isInLava() || arrow.getPersistentData().getBoolean("academy:protected")
                || arrow.getPersistentData().getBoolean("academy:immovable") || !finite(arrow.position()) || !finite(arrow.getDeltaMovement())
                || arrow.getDeltaMovement().lengthSqr() <= EPS || arrow.getDeltaMovement().lengthSqr() > 64 * 64
                || !Double.isFinite(arrow.getBaseDamage()) || arrow.getBaseDamage() <= 0 || arrow.getBaseDamage() > 64
                || world.getEntity(arrow.getId()) != arrow || world.getEntity(arrow.getUUID()) != arrow || foreignLease(arrow)) return false;
        LivingEntity shooter = shooter(arrow);
        if (shooter == null || !VanillaCombatContract.supportedClass(shooter) || !HighPowerCombat.supported(shooter) || shooter.level() != world
                || !shooter.isAlive() || shooter.isRemoved() || shooter.isSpectator() || shooter.isInvulnerable() || shooter.noPhysics
                || shooter.isPassenger() || shooter.isVehicle() || !finite(shooter.position()) || !finite(shooter.getDeltaMovement())
                || shooter.getPersistentData().getBoolean("academy:protected") || world.getEntity(shooter.getId()) != shooter
                || world.getEntity(shooter.getUUID()) != shooter) return false;
        ItemStack pickup = arrow.getPickupItemStackOrigin(), weapon = arrow.getWeaponItem();
        if (pickup.getCount() != 1 || !ItemStack.isSameItemSameComponents(pickup, new ItemStack(Items.ARROW))) return false;
        if (weapon == null) return true;
        if (weapon.getCount() != 1 || !(weapon.is(Items.BOW) || weapon.is(Items.CROSSBOW))
                || weapon.getDamageValue() < 0 || weapon.getDamageValue() >= weapon.getMaxDamage()) return false;
        ItemStack normalized = weapon.copy(); normalized.setDamageValue(0);
        return ItemStack.isSameItemSameComponents(normalized, new ItemStack(weapon.getItem()));
    }
    private static boolean foreignLease(AbstractArrow arrow) {
        return arrow.getPersistentData().contains("academy:trajectory_return_used")
                || arrow.getPersistentData().contains("academy:short_interception_used")
                || arrow.getPersistentData().contains(BrakingTouch.SPENT)
                || MultiAxisReflectionArrowBridge.leased(arrow) || TrajectoryReturnArrowBridge.leased(arrow)
                || ShortInterceptionArrowBridge.leased(arrow) || MagneticDomainArrows.leased(arrow)
                || MagneticArrowSeparation.leased(arrow) || IronSandLance.leased(arrow)
                || ElectromasterPrototype.leased(arrow) || ElectromasterChargeDecoy.leased(arrow) || MagneticGroundStep.leased(arrow);
    }
    private static boolean nativeCaller(String method) {
        try {
            return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
                    .filter(frame -> frame.getDeclaringClass() != AutonomousVectorArrowReceipts.class)
                    .findFirst().map(frame -> frame.getDeclaringClass() == AbstractArrow.class && frame.getMethodName().contains(method)).orElse(false));
        } catch (RuntimeException | LinkageError unavailable) { return false; }
    }
    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z)
                && Math.abs(value.x) <= 30_000_000 && Math.abs(value.y) <= 30_000_000 && Math.abs(value.z) <= 30_000_000;
    }
}
