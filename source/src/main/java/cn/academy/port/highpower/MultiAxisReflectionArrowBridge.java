package cn.academy.port.highpower;

import cn.academy.port.highpower.core.MultiAxisReflectionGeometry;
import cn.academy.port.highpower.core.MultiAxisReflectionRules;
import cn.academy.port.highpower.mixin.NativeScatterPlateMixinPlugin;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
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

/** NEW442: one physical reversal at the reviewed native pending-body-impact seam.
 * Historical receipts describe completed movement; a separate original-native-sweep receipt
 * certifies pending body contact without claiming that the predicted path was traversed.
 * No damage source, damage permit, replacement arrow, owner write or second hurt exists here. */
public final class MultiAxisReflectionArrowBridge {
    public static final String SPENT = "academy:multi_axis_reflection_spent";
    private static final int LIMIT = 512, TTL = 20, SCAN_LIMIT = 64;
    private static final Map<AbstractArrow, Tick> TICKS = new IdentityHashMap<>();
    private static final Map<AbstractArrow, List<Receipt>> RECEIPTS = new IdentityHashMap<>();
    private static final Map<AbstractArrow, Flight> FLIGHTS = new IdentityHashMap<>();
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.multi_axis_reflection.arrow");
    private MultiAxisReflectionArrowBridge() {}

    private record OwnerSample(ServerPlayer player, Vec3 position, Vec3 look, AABB bounds,
                               net.minecraft.world.entity.Pose pose, float yaw, float pitch, Object generation) {
        OwnerSample(ServerPlayer player) { this(player, player.position(), player.getLookAngle(), player.getBoundingBox(),
                player.getPose(), player.getYRot(), player.getXRot(), MultiAxisReflection.generation(player)); }
        boolean unchanged() { return position.equals(player.position()) && look.equals(player.getLookAngle()) && bounds.equals(player.getBoundingBox())
                && pose == player.getPose() && yaw == player.getYRot() && pitch == player.getXRot() && generation == MultiAxisReflection.generation(player); }
    }
    private static final class Snapshot {
        final Arrow arrow;
        final ServerLevel world;
        final LivingEntity shooter;
        final UUID uuid, ownerUuid;
        final int id, ownerId, ticks, life, inGroundTime, shake;
        final AbstractArrow.Pickup pickupMode;
        final double baseDamage;
        final ItemStack pickup, weapon;
        final Vec3 position, velocity;
        Snapshot(Arrow arrow) {
            this.arrow = arrow; world = (ServerLevel) arrow.level(); shooter = owner(arrow);
            uuid = arrow.getUUID(); id = arrow.getId(); ownerUuid = shooter.getUUID(); ownerId = shooter.getId();
            position = arrow.position(); velocity = arrow.getDeltaMovement(); baseDamage = arrow.getBaseDamage();
            pickup = arrow.getPickupItemStackOrigin().copy(); weapon = arrow.getWeaponItem() == null ? null : arrow.getWeaponItem().copy();
            pickupMode = arrow.pickup; ticks = arrow.tickCount; shake = arrow.shakeTime;
            var access = (ShortInterceptionArrowBridge.MetadataAccess) arrow;
            life = access.academy$interceptionLife(); inGroundTime = access.academy$interceptionInGroundTime();
        }
        boolean identity() {
            return arrow.level() == world && arrow.getId() == id && arrow.getUUID().equals(uuid)
                    && world.getEntity(id) == arrow && world.getEntity(uuid) == arrow
                    && arrow.getBaseDamage() == baseDamage && arrow.pickup == pickupMode
                    && ItemStack.matches(pickup, arrow.getPickupItemStackOrigin())
                    && (weapon == null ? arrow.getWeaponItem() == null : arrow.getWeaponItem() != null && ItemStack.matches(weapon, arrow.getWeaponItem()))
                    && owner(arrow) == shooter && shooter.getId() == ownerId && shooter.getUUID().equals(ownerUuid)
                    && world.getEntity(ownerId) == shooter && world.getEntity(ownerUuid) == shooter;
        }
        boolean frozen() {
            var access = (ShortInterceptionArrowBridge.MetadataAccess) arrow;
            return identity() && position.equals(arrow.position()) && velocity.equals(arrow.getDeltaMovement())
                    && arrow.tickCount == ticks && arrow.shakeTime == shake
                    && access.academy$interceptionLife() == life && access.academy$interceptionInGroundTime() == inGroundTime;
        }
    }
    private static final class Receipt {
        final Snapshot identity;
        final ServerPlayer defender;
        final MultiAxisReflection.Capture capture;
        final long created;
        Receipt(Snapshot identity, ServerPlayer defender, MultiAxisReflection.Capture capture) {
            this.identity = identity; this.defender = defender; this.capture = capture; created = now(identity.world);
        }
    }
    private record Flight(Snapshot identity, ServerPlayer defender, MultiAxisReflection.Capture capture, long created) {}
    /** Opaque lifetime is opened and closed only around the reviewed native tick. */
    public static final class Tick {
        final Arrow arrow;
        final Snapshot snapshot;
        final List<OwnerSample> owners;
        EntityHitResult nativeHit;
        HitResult pending;
        Vec3 segmentEnd;
        Vec3 sweepTerminal;
        MultiAxisReflectionGeometry.Contact sweepContact;
        ServerPlayer sweepDefender;
        boolean capturingSweep;
        Receipt receipt;
        Payment payment;
        boolean observed, anyImpact, acceptedEvent, inImpact, consumed, poisoned;
        Tick(Arrow arrow, List<OwnerSample> owners) { this.arrow = arrow; snapshot = new Snapshot(arrow); this.owners = owners; }
    }

    /** The sole resource-write authority is created privately from an authenticated live impact. */
    public static final class Payment {
        private final Tick tick;
        private final Receipt receipt;
        private final BigDecimal amount;
        private final MultiAxisReflection.Claim claim;
        private boolean ledgerEntered, committed, uncertain, finished;
        private Payment(Tick tick, Receipt receipt, BigDecimal amount, MultiAxisReflection.Claim claim) {
            this.tick = tick; this.receipt = receipt; this.amount = amount; this.claim = claim;
        }
        public ServerPlayer player() { return receipt.defender; }
        public Arrow arrow() { return tick.arrow; }
        public MultiAxisReflection.Capture capture() { return receipt.capture; }
        public BigDecimal amount() { return amount; }
        public boolean committed() { return committed; }
        public boolean paidUncertain() { return uncertain; }
        public boolean valid() {
            if (ledgerEntered || tick.payment != this || !validFrame(player(), arrow(), capture())
                    || !MultiAxisReflection.validCapture(player(), arrow(), capture()) || !MultiAxisReflection.claimValid(claim)
                    || !plain(arrow()) || foreignLease(arrow()) || !hostile(player(), tick.snapshot.shooter)
                    || !contactClear(player(), tick) || !reverseClear(player(), tick.snapshot)) return false;
            // All potentially calling checks finish before these immutable observations.
            return MultiAxisReflection.validCapture(player(), arrow(), capture())
                    && validFrame(player(), arrow(), capture()) && tick.snapshot.frozen() && MultiAxisReflection.claimValid(claim);
        }
        /** Called immediately after callback-free ledger publication; never posts an observer. */
        public void commitAfterLedger() {
            if (ledgerEntered) throw new IllegalStateException("NEW442 payment already entered");
            ledgerEntered = true; tick.consumed = true;
            try {
                MultiAxisReflection.commitClaim(claim);
                arrow().getPersistentData().putBoolean(SPENT, true);
                arrow().setDeltaMovement(tick.snapshot.velocity.scale(-1));
                arrow().hasImpulse = true; arrow().hurtMarked = true;
                committed = tick.snapshot.identity() && tick.snapshot.position.equals(arrow().position())
                        && tick.snapshot.velocity.scale(-1).equals(arrow().getDeltaMovement());
                if (!committed) throw new IllegalStateException("NEW442 paid reversal could not be confirmed");
                if (FLIGHTS.size() < LIMIT) FLIGHTS.put(arrow(), new Flight(tick.snapshot, player(), capture(), now(tick.snapshot.world)));
            } catch (RuntimeException | LinkageError failure) {
                uncertain = true;
                // A paid uncertain result cannot become a retry or a second native damage settlement.
                arrow().getPersistentData().putBoolean(SPENT, true);
                throw failure;
            } finally { RECEIPTS.remove(arrow()); }
        }
        /** Notifications occur only after ledger, direction quota, tombstone and velocity commit. */
        public void finished() {
            if (finished) return;
            finished = true;
            if (committed) MultiAxisReflection.reflected(player(), arrow(), capture());
        }
    }

    public static boolean available() { return NativeScatterPlateMixinPlugin.multiAxisReflectionAvailable(); }
    /** Denial-only permanent marker. Malformed records also deny future steering. */
    public static boolean reflecting(AbstractArrow arrow) {
        return arrow == null || arrow.getPersistentData().contains(SPENT);
    }
    public static boolean leased(AbstractArrow arrow) {
        if (reflecting(arrow)) return true;
        Tick tick = TICKS.get(arrow);
        return tick != null && tick.payment != null;
    }
    public static Tick beginTick(AbstractArrow projectile) {
        if (projectile == null || !(projectile.level() instanceof ServerLevel world) || !world.getServer().isSameThread()
                || !RECEIPTS.containsKey(projectile) && !FLIGHTS.containsKey(projectile) && !MultiAxisReflection.hasCandidates(world)) return null;
        if (!nativeCaller("academy$observeNativeTick")) return null;
        Tick prior = TICKS.get(projectile);
        if (prior != null) { prior.poisoned = true; RECEIPTS.remove(projectile); return null; }
        Flight flight = FLIGHTS.get(projectile);
        if (flight != null && !validFlight(flight)) { FLIGHTS.remove(projectile); flight = null; }
        if (TICKS.size() >= LIMIT || !plain(projectile) || flight == null && (reflecting(projectile) || foreignLease(projectile))) return null;
        Arrow arrow = (Arrow) projectile;
        if (flight != null) { Tick tick = new Tick(arrow, List.of()); TICKS.put(arrow, tick); return tick; }
        List<ServerPlayer> candidates = MultiAxisReflection.candidates((ServerLevel) arrow.level());
        if (candidates == null || candidates.size() > SCAN_LIMIT) return null;
        var owners = new ArrayList<OwnerSample>();
        for (ServerPlayer player : candidates) if (player.position().distanceToSqr(arrow.position()) <= 13 * 13) owners.add(new OwnerSample(player));
        if (owners.isEmpty() && !RECEIPTS.containsKey(arrow)) return null;
        Tick tick = new Tick(arrow, List.copyOf(owners)); TICKS.put(arrow, tick); return tick;
    }
    public static void observe(AbstractArrow arrow, Vec3 start, Vec3 end, EntityHitResult result) {
        Tick tick = TICKS.get(arrow); if (tick == null) return;
        if (!nativeCaller("academy$observeClippedSegment")) { tick.poisoned = true; return; }
        if (tick.poisoned || tick.observed || !tick.snapshot.frozen() || !finite(start) || !finite(end)
                || !start.equals(tick.snapshot.position) || start.distanceToSqr(end) > 64) { tick.poisoned = true; return; }
        tick.observed = true; tick.nativeHit = result; tick.segmentEnd = end;
        if (reflecting(arrow) || result == null || !(result.getEntity() instanceof ServerPlayer defender)) return;
        OwnerSample sample = tick.owners.stream().filter(owner -> owner.player == defender).findFirst().orElse(null);
        if (sample == null || !sample.unchanged() || !hostile(defender, tick.snapshot.shooter)) return;
        AABB bounds = defender.getBoundingBox().inflate((double) .3F);
        // The vanilla EntityHitResult point is entity.position(), not the first body intercept.
        if (bounds.contains(start)) return;
        var entry = bounds.clip(start, end);
        if (entry.isEmpty()) return;
        var pose = MultiAxisReflectionGeometry.pose(vector(sample.bounds.getCenter()), vector(sample.look));
        var contact = MultiAxisReflectionGeometry.crossing(pose, vector(start), vector(entry.get()));
        if (contact.isEmpty()) return;
        tick.sweepDefender = defender; tick.sweepTerminal = entry.get(); tick.sweepContact = contact.get(); tick.capturingSweep = true;
        try {
            var capture = MultiAxisReflection.capturePendingNative(defender, tick.arrow, contact.get().facetIndex(), vec(contact.get().point()), start, entry.get());
            if (capture != null && sample.unchanged() && tick.snapshot.frozen()) tick.receipt = new Receipt(tick.snapshot, defender, capture);
        } finally { tick.capturingSweep = false; }
    }
    /** Current native swept-body qualification is not a claim that the arrow traversed the segment. */
    public static boolean validPendingSweep(ServerPlayer defender, Arrow arrow, int facet, Vec3 crossing, Vec3 from, Vec3 to) {
        Tick tick = TICKS.get(arrow);
        return tick != null && !tick.poisoned && tick.capturingSweep && tick.observed && tick.nativeHit != null
                && tick.nativeHit.getEntity() == defender && tick.sweepDefender == defender && tick.sweepContact != null
                && tick.sweepContact.facetIndex() == facet && vec(tick.sweepContact.point()).equals(crossing)
                && tick.snapshot.position.equals(from) && tick.sweepTerminal.equals(to) && tick.snapshot.frozen();
    }
    public static void beforeImpact(AbstractArrow arrow, HitResult hit) {
        Tick tick = TICKS.get(arrow); if (tick == null) return;
        if (!nativeCaller("academy$consumeBeforeImpactCallbacks")) { tick.poisoned = true; return; }
        tick.anyImpact = true;
        if (tick.poisoned || tick.consumed || !tick.observed || hit != tick.nativeHit
                || !(hit instanceof EntityHitResult body) || !(body.getEntity() instanceof ServerPlayer defender)
                || !tick.snapshot.frozen()) { RECEIPTS.remove(arrow); return; }
        List<Receipt> receipts = RECEIPTS.remove(arrow); // Retire before arbitrary impact callbacks.
        if (tick.receipt == null && receipts != null) for (Receipt receipt : receipts) {
            if (receipt.defender == defender && validReceipt(receipt) && MultiAxisReflection.validCapture(defender, tick.arrow, receipt.capture)) {
                if (tick.receipt != null) { tick.poisoned = true; tick.receipt = null; break; }
                tick.receipt = receipt;
            }
        }
        if (tick.receipt != null) tick.pending = hit;
    }
    /** No payment/claim mutation: expose only an authenticated current native
     * impact which can still pay its complete existing reversal contract. */
    public static boolean pendingContact(ServerPlayer defender, AbstractArrow arrow, HitResult hit) {
        Tick tick = TICKS.get(arrow);
        try {
        if (defender == null || tick == null || tick.poisoned || tick.consumed || tick.inImpact || !tick.observed || !tick.anyImpact
                || tick.pending != hit || tick.nativeHit != hit || tick.receipt == null || tick.receipt.defender != defender
                || tick.nativeHit.getEntity() != defender || !tick.snapshot.frozen() || !validReceipt(tick.receipt)) return false;
        Receipt receipt = tick.receipt;
        BigDecimal amount = MultiAxisReflectionRules.nativeArrowDamage(tick.snapshot.velocity.length(), tick.snapshot.baseDamage, tick.snapshot.weapon != null);
        if (!MultiAxisReflection.pendingArrowAllowed(defender, tick.arrow, receipt.capture, amount)
                || !plain(arrow) || foreignLease(arrow) || !hostile(defender, tick.snapshot.shooter)
                || !contactClear(defender, tick) || !reverseClear(defender, tick.snapshot)) return false;
        return TICKS.get(arrow) == tick && tick.receipt == receipt && tick.pending == hit && tick.nativeHit == hit
                && !tick.poisoned && !tick.consumed && tick.snapshot.frozen() && plain(arrow) && !foreignLease(arrow)
                && hostile(defender, tick.snapshot.shooter)
                && MultiAxisReflection.pendingArrowAllowed(defender, tick.arrow, receipt.capture, amount);
        } catch (RuntimeException | LinkageError refused) { if (tick != null) tick.poisoned = true; return false; }
    }
    public static void afterImpactEvent(AbstractArrow arrow, HitResult hit, boolean canceled) {
        Tick tick = TICKS.get(arrow); if (tick == null) return;
        if (!nativeCaller("academy$consumeBeforeImpactCallbacks")) { tick.poisoned = true; return; }
        tick.acceptedEvent = !canceled && tick.pending == hit && !tick.poisoned && tick.snapshot.frozen();
        if (!tick.acceptedEvent) tick.receipt = null;
    }
    /** True means the one native body impact is replaced by this already-committed physical reversal. */
    public static boolean reflect(AbstractArrow arrow, HitResult hit) {
        if (!nativeCaller("academy$nativeImpactWindow")) return false;
        Tick tick = TICKS.get(arrow);
        if (tick == null || !tick.acceptedEvent || tick.pending != hit || tick.receipt == null || tick.inImpact || tick.consumed || tick.poisoned) return false;
        tick.inImpact = true;
        MultiAxisReflection.Claim claim = null;
        try {
            Receipt receipt = tick.receipt;
            if (!validFrame(receipt.defender, tick.arrow, receipt.capture) || !MultiAxisReflection.validCapture(receipt.defender, tick.arrow, receipt.capture)
                    || !plain(arrow) || foreignLease(arrow) || !hostile(receipt.defender, tick.snapshot.shooter)
                    || !contactClear(receipt.defender, tick) || !reverseClear(receipt.defender, tick.snapshot)) return false;
            BigDecimal amount = MultiAxisReflectionRules.nativeArrowDamage(tick.snapshot.velocity.length(), tick.snapshot.baseDamage, tick.snapshot.weapon != null);
            if (amount == null || amount.signum() <= 0) return false;
            claim = MultiAxisReflection.prepareClaim(receipt.defender, tick.arrow, receipt.capture, amount);
            if (claim == null) return false;
            tick.payment = new Payment(tick, receipt, amount, claim);
            HighPowerCombat.commitMultiAxisReflection(tick.payment);
            return tick.payment.ledgerEntered;
        } catch (RuntimeException | LinkageError failure) {
            boolean paid = tick.payment != null && tick.payment.ledgerEntered;
            if (paid) tick.payment.uncertain = !tick.payment.committed;
            LOG.warn("NEW442 native contact stopped; paid={}, reversalConfirmed={}", paid, tick.payment != null && tick.payment.committed, failure);
            return paid;
        } finally {
            tick.inImpact = false;
            if (claim != null) MultiAxisReflection.abandonClaim(claim);
            tick.receipt = null;
        }
    }
    /** No controller calls here: validCapture and this native proof remain independent. */
    public static boolean validFrame(ServerPlayer defender, Arrow arrow, MultiAxisReflection.Capture capture) {
        Tick tick = TICKS.get(arrow);
        if (tick == null || tick.poisoned || tick.consumed || !tick.inImpact || !tick.acceptedEvent || tick.receipt == null
                || tick.receipt.defender != defender || tick.receipt.capture != capture || tick.pending != tick.nativeHit
                || !(tick.nativeHit.getEntity() == defender) || !validReceipt(tick.receipt) || !tick.snapshot.frozen()) return false;
        return finite(defender.position()) && MultiAxisReflectionGeometry.withinOwnerRange(vector(defender.getBoundingBox().getCenter()), vector(arrow.position()))
                && defender.level() == arrow.level() && defender.isAlive() && !defender.isRemoved()
                && defender.serverLevel().getEntity(defender.getUUID()) == defender;
    }
    public static void endTick(Tick tick, boolean completed) {
        if (tick == null || TICKS.get(tick.arrow) != tick) return;
        TICKS.remove(tick.arrow);
        if (!completed || !nativeCaller("academy$observeNativeTick")) { RECEIPTS.remove(tick.arrow); FLIGHTS.remove(tick.arrow); return; }
        Flight flight = FLIGHTS.get(tick.arrow);
        if (flight != null) {
            boolean reversedNow = tick.payment != null && tick.payment.committed;
            Vec3 expected = tick.snapshot.position.add(reversedNow ? tick.snapshot.velocity.scale(-1) : tick.snapshot.velocity);
            if (!tick.poisoned && validFlight(flight) && tick.snapshot.identity() && tick.arrow.position().equals(expected)
                    && (!tick.anyImpact || reversedNow)) MultiAxisReflection.observed(flight.defender, tick.arrow, flight.capture);
            else FLIGHTS.remove(tick.arrow);
            return;
        }
        if (tick.poisoned || tick.anyImpact || !tick.observed || !plain(tick.arrow) || reflecting(tick.arrow) || foreignLease(tick.arrow)
                || !tick.snapshot.identity() || !tick.arrow.position().equals(tick.snapshot.position.add(tick.snapshot.velocity))) {
            if (tick.poisoned) RECEIPTS.remove(tick.arrow);
            return;
        }
        // Only this completed native setPos proves a travelled segment. A search endpoint proves nothing.
        Vec3 from = tick.snapshot.position, to = tick.arrow.position();
        if (from.distanceToSqr(to) <= 0 || from.distanceToSqr(to) > 64) return;
        if (!RECEIPTS.containsKey(tick.arrow) && RECEIPTS.size() >= LIMIT) return;
        List<Receipt> receipts = RECEIPTS.computeIfAbsent(tick.arrow, ignored -> new ArrayList<>());
        receipts.removeIf(receipt -> !validReceipt(receipt));
        for (OwnerSample sample : tick.owners) {
            if (receipts.size() >= 4 || RECEIPTS.size() > LIMIT) break;
            ServerPlayer defender = sample.player;
            if (!sample.unchanged() || !hostile(defender, tick.snapshot.shooter)) continue;
            var pose = MultiAxisReflectionGeometry.pose(vector(sample.bounds.getCenter()), vector(sample.look));
            var crossing = MultiAxisReflectionGeometry.crossing(pose, vector(from), vector(to));
            if (crossing.isEmpty()) continue;
            var contact = crossing.get();
            boolean seen = receipts.stream().anyMatch(receipt -> receipt.defender == defender);
            if (seen) continue;
            var capture = MultiAxisReflection.capture(defender, tick.arrow, contact.facetIndex(), vec(contact.point()), from, to);
            if (capture != null && sample.unchanged() && tick.snapshot.identity()) receipts.add(new Receipt(tick.snapshot, defender, capture));
        }
        if (receipts.isEmpty()) RECEIPTS.remove(tick.arrow);
    }
    public static void ownerChanged(Entity entity) {
        if (entity instanceof AbstractArrow arrow) { Tick tick = TICKS.get(arrow); if (tick != null) tick.poisoned = true; RECEIPTS.remove(arrow); FLIGHTS.remove(arrow); }
    }
    public static void invalidate(ServerPlayer player) {
        RECEIPTS.values().forEach(receipts -> receipts.removeIf(receipt -> receipt.defender == player));
        FLIGHTS.values().removeIf(flight -> flight.defender == player);
        for (Tick tick : TICKS.values()) if (tick.receipt != null && tick.receipt.defender == player) tick.poisoned = true;
    }
    public static void prune(MinecraftServer server) {
        RECEIPTS.entrySet().removeIf(entry -> {
            if (entry.getKey().getServer() != server) return false;
            entry.getValue().removeIf(receipt -> !validReceipt(receipt));
            return entry.getValue().isEmpty();
        });
        FLIGHTS.entrySet().removeIf(entry -> entry.getKey().getServer() == server && !validFlight(entry.getValue()));
    }
    public static void clear() { TICKS.clear(); RECEIPTS.clear(); FLIGHTS.clear(); }
    private static boolean validFlight(Flight flight) {
        long tick = now(flight.identity.world);
        return tick >= flight.created && tick - flight.created <= TTL && flight.identity.identity()
                && !flight.identity.arrow.isRemoved() && flight.defender.level() == flight.identity.world
                && flight.defender.getBoundingBox().getCenter().distanceToSqr(flight.identity.arrow.position()) <= 25;
    }
    private static boolean validReceipt(Receipt receipt) {
        long tick = now(receipt.identity.world);
        return tick >= receipt.created && tick - receipt.created <= TTL && receipt.identity.identity()
                && !reflecting(receipt.identity.arrow) && receipt.defender.level() == receipt.identity.world;
    }
    private static boolean reverseClear(ServerPlayer player, Snapshot snapshot) {
        Vec3 start = snapshot.position, end = start.subtract(snapshot.velocity);
        AABB swept = snapshot.arrow.getBoundingBox().expandTowards(snapshot.velocity.scale(-1)).inflate(.301);
        if (!finite(end) || !DisplacedProjectionWorld.volume(player, swept).isEmpty()
                || !snapshot.world.noBlockCollision(snapshot.arrow, swept) || snapshot.world.containsAnyLiquid(swept)) return false;
        if (snapshot.world.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, snapshot.arrow)).getType() != HitResult.Type.MISS) return false;
        // Refuse every body, including an overlapping defender. This deliberately avoids inventing a safe exit.
        List<Entity> bodies = new ArrayList<>();
        snapshot.world.getEntities(EntityTypeTest.forClass(Entity.class), swept, entity -> entity != snapshot.arrow, bodies, SCAN_LIMIT + 1);
        if (bodies.size() > SCAN_LIMIT) return false;
        for (Entity body : bodies) {
            if (body.isRemoved() || body.isSpectator()) continue;
            AABB bounds = body.getBoundingBox().inflate((double) .3F);
            if (bounds.contains(start) || bounds.contains(end) || bounds.clip(start, end).isPresent()) return false;
        }
        return snapshot.frozen();
    }
    /** Repeat bounded, read-only obstruction checks after the native impact listeners have returned. */
    private static boolean contactClear(ServerPlayer defender, Tick tick) {
        Vec3 start = tick.snapshot.position;
        AABB defenderBounds = defender.getBoundingBox().inflate((double) .3F);
        if (tick.segmentEnd == null || defenderBounds.contains(start)) return false;
        var entry = defenderBounds.clip(start, tick.segmentEnd);
        if (entry.isEmpty()) return false;
        Vec3 end = entry.get();
        AABB swept = new AABB(start, end).inflate(.301);
        if (!DisplacedProjectionWorld.volume(defender, swept).isEmpty()
                || tick.snapshot.world.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, tick.arrow)).getType() != HitResult.Type.MISS) return false;
        List<Entity> bodies = new ArrayList<>();
        tick.snapshot.world.getEntities(EntityTypeTest.forClass(Entity.class), swept, entity -> entity != tick.arrow, bodies, SCAN_LIMIT + 1);
        if (bodies.size() > SCAN_LIMIT) return false;
        double terminal = start.distanceToSqr(end);
        for (Entity body : bodies) {
            if (body == defender || body.isRemoved() || body.isSpectator()) continue;
            AABB bounds = body.getBoundingBox().inflate((double) .3F);
            if (bounds.contains(start)) return false;
            var contact = bounds.clip(start, end);
            if (contact.isPresent() && start.distanceToSqr(contact.get()) <= terminal) return false;
        }
        return tick.snapshot.frozen();
    }
    private static boolean hostile(ServerPlayer defender, LivingEntity shooter) {
        return defender != shooter && HighPowerCombat.hostileTarget(shooter) && shooter.level() == defender.level()
                && !defender.isAlliedTo(shooter) && !shooter.isAlliedTo(defender)
                && HighPowerCombat.unsupportedReason(defender).isEmpty() && HighPowerCombat.unsupportedReason(shooter).isEmpty();
    }
    private static LivingEntity owner(AbstractArrow arrow) {
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
                || arrow.getDeltaMovement().lengthSqr() <= 0 || arrow.getDeltaMovement().lengthSqr() > 64
                || !Double.isFinite(arrow.getBaseDamage()) || arrow.getBaseDamage() <= 0 || arrow.getBaseDamage() > 64
                || world.getEntity(arrow.getId()) != arrow || world.getEntity(arrow.getUUID()) != arrow) return false;
        LivingEntity shooter = owner(arrow);
        if (!(HighPowerCombat.hostileTarget(shooter)) || !VanillaCombatContract.supportedClass(shooter) || shooter.level() != world
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
                || TrajectoryReturnArrowBridge.leased(arrow) || ShortInterceptionArrowBridge.leased(arrow)
                || MagneticDomainArrows.leased(arrow) || MagneticArrowSeparation.leased(arrow) || IronSandLance.leased(arrow)
                || ElectromasterPrototype.leased(arrow) || ElectromasterChargeDecoy.leased(arrow) || MagneticGroundStep.leased(arrow)
                || ScatterPlateArrowReceipts.hasEvidence(arrow) || DissipationCurtainArrowReceipts.hasEvidence(arrow)
                || ZeroGapArmorArrowReceipts.hasEvidence(arrow);
    }
    private static long now(ServerLevel world) { return Integer.toUnsignedLong(world.getServer().getTickCount()); }
    /** Public mixin adapters are not public authority factories. Require their actual transformed host caller. */
    private static boolean nativeCaller(String method) {
        try {
            return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
                    .filter(frame -> frame.getDeclaringClass() != MultiAxisReflectionArrowBridge.class)
                    .findFirst().map(frame -> frame.getDeclaringClass() == AbstractArrow.class && frame.getMethodName().contains(method)).orElse(false));
        } catch (RuntimeException | LinkageError unavailable) { return false; }
    }
    private static boolean finite(Vec3 value) { return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z)
            && Math.abs(value.x) <= 30_000_000 && Math.abs(value.y) <= 30_000_000 && Math.abs(value.z) <= 30_000_000; }
    private static MultiAxisReflectionGeometry.Vector vector(Vec3 value) { return new MultiAxisReflectionGeometry.Vector(value.x, value.y, value.z); }
    private static Vec3 vec(MultiAxisReflectionGeometry.Vector value) { return new Vec3(value.x(), value.y(), value.z()); }
}
