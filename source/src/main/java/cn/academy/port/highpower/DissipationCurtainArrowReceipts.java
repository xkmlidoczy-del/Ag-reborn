package cn.academy.port.highpower;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * NEW242 observer-only native path evidence. It never changes an arrow, calls hurt, invokes enchantment
 * callbacks, or spends a resource. Only the pinned native body's own hurt call exposes one receipt.
 */
public final class DissipationCurtainArrowReceipts {
    public static final int TTL = 20, MAX_PER_CONTROLLER = 16, MAX_PER_ARROW = 4, SCAN_LIMIT = 64, MAX_ARROWS = 512;
    private static final double EPS = 1e-8;
    private static final Map<AbstractArrow, Ledger> LEDGERS = new IdentityHashMap<>();
    private static final Map<AbstractArrow, Tick> TICKS = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Window> WINDOWS = new IdentityHashMap<>();
    private static final class Pair {
        final ServerPlayer defender;
        final Object activation;
        final long first;
        DissipationCurtain.Capture capture;
        Pair(ServerPlayer defender, DissipationCurtain.Capture capture, long first) { this.defender = defender; activation = DissipationCurtain.activation(capture); this.capture = capture; this.first = first; }
    }
    private static final class Ledger {
        final Arrow arrow;
        final UUID uuid, ownerUuid;
        final int id, ownerId;
        final ServerLevel world;
        final LivingEntity owner;
        final double baseDamage;
        final ItemStack pickup, weapon;
        final List<Pair> pairs = new ArrayList<>(MAX_PER_ARROW);
        boolean retired, invalidated;
        Ledger(Arrow arrow, LivingEntity owner, ServerLevel world) {
            this.arrow = arrow; this.owner = owner; this.world = world; uuid = arrow.getUUID(); id = arrow.getId(); ownerUuid = owner.getUUID(); ownerId = owner.getId();
            baseDamage = arrow.getBaseDamage(); pickup = arrow.getPickupItemStackOrigin().copy(); weapon = arrow.getWeaponItem() == null ? null : arrow.getWeaponItem().copy();
        }
    }
    /** Opaque scopes are only acquired by the pinned mixin around a genuine native tick invocation. */
    public static final class Tick {
        final AbstractArrow arrow;
        final boolean admitted;
        HitResult pendingHit;
        Pair pending;
        boolean nativeImpact, hurtUsed, poisoned;
        Tick(AbstractArrow arrow, boolean admitted) { this.arrow = arrow; this.admitted = admitted; }
    }
    public static final class Window {
        final ServerPlayer defender;
        final DamageSource source;
        final DissipationCurtain.Capture capture;
        final Ledger ledger;
        final Tick tick;
        boolean claimed;
        Window(ServerPlayer defender, DamageSource source, DissipationCurtain.Capture capture, Ledger ledger, Tick tick) {
            this.defender = defender; this.source = source; this.capture = capture; this.ledger = ledger; this.tick = tick;
        }
    }
    private DissipationCurtainArrowReceipts() {}
    public static Tick beginTick(AbstractArrow arrow) {
        Tick prior = TICKS.get(arrow);
        if (prior != null) {
            prior.poisoned = true;
            retire(arrow);
            if (prior.pending != null) prior.pending.capture = null;
            prior.pending = null; prior.pendingHit = null; prior.nativeImpact = false; prior.hurtUsed = true;
        }
        boolean admitted = arrow != null && arrow.level() instanceof ServerLevel world && world.getServer().isSameThread() && !TICKS.containsKey(arrow);
        Tick tick = new Tick(arrow, admitted);
        if (admitted) TICKS.put(arrow, tick);
        return tick;
    }
    public static void endTick(Tick tick) {
        if (tick == null || !tick.admitted || TICKS.get(tick.arrow) != tick) return;
        TICKS.remove(tick.arrow);
        if (!plain(tick.arrow)) retire(tick.arrow);
    }
    /** Called AFTER the original native search exactly once, with its first-solid-clipped endpoints. */
    public static void observe(AbstractArrow projectile, Vec3 start, Vec3 end, EntityHitResult nativeResult) {
        Tick tick = TICKS.get(projectile);
        if (tick == null || !tick.admitted || tick.poisoned || !plain(projectile) || !finite(start) || !finite(end) || start.distanceToSqr(end) <= EPS || start.distanceToSqr(end) > 64 * 64) { retire(projectile); return; }
        Arrow arrow = (Arrow)projectile; ServerLevel world = (ServerLevel)arrow.level(); LivingEntity owner = owner(arrow);
        List<ServerPlayer> defenders = DissipationCurtain.candidates(world);
        if (defenders == null || defenders.isEmpty()) return;
        Ledger existing = LEDGERS.get(arrow);
        if (existing != null && (existing.retired || !identity(existing))) { retire(arrow); return; }
        double nativeDistance = start.distanceToSqr(end);
        Entity nearest = nativeResult == null ? null : nativeResult.getEntity();
        if (nearest != null) {
            if (!(nearest instanceof LivingEntity living) || !VanillaCombatContract.supportedClass(living)) return;
            var body = nearest.getBoundingBox().inflate((double).3F).clip(start, end);
            if (body.isEmpty()) return;
            nativeDistance = start.distanceToSqr(body.get());
        }
        // Native EntityHitResult(entity) contains entity.position(), NOT the bounding-box intercept.
        // Recheck a bounded read-only native sweep; unknown possible blockers or a tie refuse evidence.
        AABB swept = arrow.getBoundingBox().expandTowards(arrow.getDeltaMovement()).inflate(1);
        List<Entity> candidates = new ArrayList<>();
        world.getEntities(EntityTypeTest.forClass(Entity.class), swept, e -> e != arrow, candidates, SCAN_LIMIT + 1);
        if (candidates.size() > SCAN_LIMIT) return;
        int equallyNearest = 0;
        NativeScatterArrowOwnerAccess access = (NativeScatterArrowOwnerAccess)arrow;
        for (Entity entity : candidates) {
            var clip = entity.getBoundingBox().inflate((double).3F).clip(start, end);
            if (clip.isEmpty()) continue;
            double distance = start.distanceToSqr(clip.get());
            if (distance > nativeDistance + EPS) continue;
            if (!(entity instanceof LivingEntity living) || !VanillaCombatContract.supportedClass(living)) return;
            if (!living.isAlive() || living.isRemoved() || living.isSpectator()) continue;
            if (entity == owner && !access.academy$scatterLeftOwner()) continue;
            if (entity.isPassenger() || entity.isVehicle()) return;
            if (nearest == null || distance < nativeDistance - EPS) return;
            if (Math.abs(distance - nativeDistance) <= EPS) equallyNearest++;
        }
        if (nearest != null && equallyNearest != 1) return;
        for (ServerPlayer defender : defenders) {
            if (defender == owner || defender.position().distanceToSqr(start) > 70 * 70) continue;
            DissipationCurtain.Capture capture = DissipationCurtain.crossing(defender, start, end, true);
            if (capture == null || start.distanceToSqr(DissipationCurtain.point(capture)) >= nativeDistance - EPS) continue;
            Ledger ledger = LEDGERS.get(arrow);
            if (ledger == null) {
                if (LEDGERS.size() >= MAX_ARROWS) return;
                ledger = new Ledger(arrow, owner, world); LEDGERS.put(arrow, ledger);
            }
            boolean seen = false;
            for (Pair pair : ledger.pairs) if (pair.defender == defender && pair.activation == DissipationCurtain.activation(capture)) { seen = true; break; }
            if (seen || ledger.retired || ledger.pairs.size() >= MAX_PER_ARROW || receiptCount(defender) >= MAX_PER_CONTROLLER) continue;
            ledger.pairs.add(new Pair(defender, capture, now(world)));
        }
    }
    /** Take the single matching receipt before NeoForge's impact callbacks; other impacts consume all. */
    public static void beforeImpact(AbstractArrow arrow, HitResult hit) {
        Tick tick = TICKS.get(arrow); if (tick == null || !tick.admitted || tick.poisoned) return;
        Ledger ledger = LEDGERS.get(arrow); tick.pendingHit = hit; tick.pending = null;
        if (ledger != null && !ledger.retired && identity(ledger) && plain(arrow)) {
            Entity target = hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
            for (Pair pair : ledger.pairs) {
                if (pair.defender == target && valid(ledger, pair)) tick.pending = pair;
                else pair.capture = null;
            }
            ledger.retired = true; // No event cancellation, zero, iframe or deflection can earn a second claim.
        }
    }
    /** Read-only handoff before category projectile traits: only this real native impact may continue to the paid specialist. */
    public static boolean pendingContact(ServerPlayer defender, AbstractArrow arrow, HitResult hit) {
        Tick tick = TICKS.get(arrow);
        if (defender == null || tick == null || !tick.admitted || tick.poisoned || tick.hurtUsed || tick.nativeImpact
                || tick.pendingHit != hit || !(hit instanceof EntityHitResult entityHit) || entityHit.getEntity() != defender
                || tick.pending == null || tick.pending.defender != defender || tick.pending.capture == null || !plain(arrow)) return false;
        Ledger ledger = LEDGERS.get(arrow);
        return ledger != null && ledger.retired && !ledger.invalidated && ledger.pairs.contains(tick.pending)
                && valid(ledger, tick.pending);
    }
    public static void afterImpactEvent(AbstractArrow arrow, HitResult hit, boolean canceled) {
        Tick tick = TICKS.get(arrow);
        if (tick != null && (tick.pendingHit != hit || canceled)) { if (tick.pending != null) tick.pending.capture = null; tick.pending = null; tick.pendingHit = null; }
    }
    public static Tick beginImpact(AbstractArrow arrow, HitResult hit) {
        Tick tick = TICKS.get(arrow);
        if (tick == null || tick.poisoned || tick.pendingHit != hit || tick.nativeImpact || !plain(arrow)) return null;
        tick.nativeImpact = true; return tick;
    }
    public static void endImpact(Tick tick) {
        if (tick == null) return;
        if (tick.pending != null) tick.pending.capture = null;
        tick.pending = null; tick.pendingHit = null; tick.nativeImpact = false;
    }
    /** The wrapped operand is the one Entity.hurt in vanilla AbstractArrow.onHitEntity. */
    public static Window beginHurt(AbstractArrow arrow, Entity target, DamageSource source, float amount) {
        Tick tick = TICKS.get(arrow);
        if (tick == null || tick.poisoned || !tick.nativeImpact || tick.hurtUsed || tick.pending == null) return null;
        tick.hurtUsed = true; Pair pair = tick.pending; DissipationCurtain.Capture capture = pair.capture; pair.capture = null;
        if (!(target instanceof ServerPlayer defender) || pair.defender != defender || capture == null || !Float.isFinite(amount) || amount <= 0
                || source == null || source.getClass() != DamageSource.class || !source.is(DamageTypes.ARROW) || source.getDirectEntity() != arrow
                || source.getEntity() != owner(arrow) || LEDGERS.get(arrow) == null || !identity(LEDGERS.get(arrow)) || !plain(arrow) || !DissipationCurtain.matches(defender, capture) || WINDOWS.containsKey(defender)) return null;
        Window window = new Window(defender, source, capture, LEDGERS.get(arrow), tick);
        if (!DissipationCurtain.bindArrowWindow(capture, window)) return null;
        WINDOWS.put(defender, window); return window;
    }
    public static void endHurt(Window window) { if (window != null) WINDOWS.remove(window.defender, window); }
    static DissipationCurtain.Capture claim(ServerPlayer player, DamageSource source) {
        Window window = WINDOWS.get(player);
        if (window == null || window.claimed || window.source != source) return null;
        window.claimed = true; return DissipationCurtain.matches(player, window.capture) ? window.capture : null;
    }
    /** Recheck immutable native-source identity after every incoming/Pre callback, before the exact debit. */
    static boolean validWindow(Window window) {
        Ledger ledger = window.ledger;
        return window.claimed && !window.tick.poisoned && WINDOWS.get(window.defender) == window && ledger != null && !ledger.invalidated
                && LEDGERS.get(ledger.arrow) == ledger && TICKS.get(ledger.arrow) == window.tick && window.tick.nativeImpact && window.tick.hurtUsed
                && identity(ledger) && plain(ledger.arrow) && window.source.getDirectEntity() == ledger.arrow && window.source.getEntity() == ledger.owner;
    }
    /** Conservative, read-only writer exclusion. Never recurse through plain()/controller validation. */
    public static boolean hasEvidence(AbstractArrow arrow) {
        Ledger ledger = LEDGERS.get(arrow);
        if (ledger != null && !ledger.invalidated)
            for (Pair pair : ledger.pairs) if (pair.capture != null) return true;
        Tick tick = TICKS.get(arrow);
        if (tick != null && tick.pending != null && tick.pending.capture != null) return true;
        for (Window window : WINDOWS.values())
            if (window.ledger != null && window.ledger.arrow == arrow && window.capture != null) return true;
        return false;
    }
    public static int receiptCount(ServerPlayer defender) {
        int count = 0;
        for (Ledger ledger : LEDGERS.values()) for (Pair pair : ledger.pairs) if (pair.defender == defender && pair.capture != null && !ledger.retired && valid(ledger, pair)) count++;
        return count;
    }
    public static void invalidate(ServerPlayer defender) {
        for (Ledger ledger : LEDGERS.values()) for (Pair pair : ledger.pairs) if (pair.defender == defender) pair.capture = null;
        WINDOWS.remove(defender);
    }
    public static void invalidatePlate(ServerPlayer defender, int index) {
        for (Ledger ledger : LEDGERS.values()) for (Pair pair : ledger.pairs) if (pair.defender == defender && pair.capture != null && pair.capture.plateIndex() == index) pair.capture = null;
    }
    public static void retire(AbstractArrow arrow) { Ledger ledger = LEDGERS.get(arrow); if (ledger != null) { ledger.retired = true; ledger.invalidated = true; for (Pair pair : ledger.pairs) pair.capture = null; } }
    public static void ownerChanged(Entity projectile) { if (projectile instanceof AbstractArrow arrow) retire(arrow); }
    public static void removed(Entity entity) {
        if (entity instanceof AbstractArrow arrow) { LEDGERS.remove(arrow); TICKS.remove(arrow); }
        else if (entity instanceof LivingEntity) for (Ledger ledger : LEDGERS.values()) if (ledger.owner == entity) retire(ledger.arrow);
    }
    public static void prune(MinecraftServer server) {
        for (Iterator<Map.Entry<AbstractArrow, Ledger>> it = LEDGERS.entrySet().iterator(); it.hasNext();) {
            Ledger ledger = it.next().getValue(); if (ledger.world.getServer() != server) continue;
            if (ledger.arrow.isRemoved() || ledger.world.getEntity(ledger.id) != ledger.arrow || ledger.world.getEntity(ledger.uuid) != ledger.arrow) { it.remove(); continue; }
            if (!identity(ledger) || !plain(ledger.arrow)) retire(ledger.arrow);
            for (Pair pair : ledger.pairs) if (pair.capture != null && !valid(ledger, pair)) pair.capture = null;
        }
    }
    public static void clear() { LEDGERS.clear(); TICKS.clear(); WINDOWS.clear(); }
    private static boolean valid(Ledger ledger, Pair pair) {
        long now = now(ledger.world);
        return pair.capture != null && now >= pair.first && now - pair.first < TTL && identity(ledger) && DissipationCurtain.matches(pair.defender, pair.capture);
    }
    private static boolean identity(Ledger ledger) {
        return ledger.arrow.level() == ledger.world && ledger.arrow.getId() == ledger.id && ledger.arrow.getUUID().equals(ledger.uuid)
                && ledger.world.getEntity(ledger.id) == ledger.arrow && ledger.world.getEntity(ledger.uuid) == ledger.arrow
                && ledger.arrow.getBaseDamage() == ledger.baseDamage && ItemStack.matches(ledger.pickup, ledger.arrow.getPickupItemStackOrigin())
                && (ledger.weapon == null ? ledger.arrow.getWeaponItem() == null : ledger.arrow.getWeaponItem() != null && ItemStack.matches(ledger.weapon, ledger.arrow.getWeaponItem()))
                && owner(ledger.arrow) == ledger.owner && ledger.owner.getId() == ledger.ownerId && ledger.owner.getUUID().equals(ledger.ownerUuid)
                && ledger.world.getEntity(ledger.ownerId) == ledger.owner && ledger.world.getEntity(ledger.ownerUuid) == ledger.owner;
    }
    private static LivingEntity owner(AbstractArrow arrow) {
        if (!(arrow instanceof NativeScatterArrowOwnerAccess access) || !(access.academy$scatterCachedOwner() instanceof LivingEntity living)
                || !living.getUUID().equals(access.academy$scatterOwnerUuid())) return null;
        return living;
    }
    private static boolean plain(AbstractArrow arrow) {
        if (arrow == null || arrow.getClass() != Arrow.class || arrow.getType() != EntityType.ARROW || !(arrow.level() instanceof ServerLevel world)
                || !world.getServer().isSameThread() || arrow.isRemoved() || !arrow.isAlive() || world.getEntity(arrow.getId()) != arrow || world.getEntity(arrow.getUUID()) != arrow
                || arrow.inGround || arrow.isNoPhysics() || arrow.noPhysics || arrow.isNoGravity() || arrow.isPassenger() || arrow.isVehicle() || arrow.isCritArrow()
                || arrow.isOnFire() || arrow.getPierceLevel() != 0 || !finite(arrow.position()) || !finite(arrow.getDeltaMovement()) || arrow.getDeltaMovement().lengthSqr() > 64 * 64
                || !Double.isFinite(arrow.getBaseDamage()) || arrow.getBaseDamage() <= 0 || arrow.getBaseDamage() > 64 || foreignLease(arrow)) return false;
        LivingEntity owner = owner(arrow);
        if (owner == null || !VanillaCombatContract.supportedClass(owner) || owner.level() != world || owner.isRemoved() || !owner.isAlive() || owner.isSpectator()
                || owner.isPassenger() || owner.isVehicle() || world.getEntity(owner.getId()) != owner || world.getEntity(owner.getUUID()) != owner) return false;
        var stack = arrow.getPickupItemStackOrigin(); var weapon = arrow.getWeaponItem();
        return stack.is(Items.ARROW) && stack.getCount() == 1 && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).equals(PotionContents.EMPTY)
                && stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()
                && (weapon == null || !weapon.isEmpty() && (weapon.is(Items.BOW) || weapon.is(Items.CROSSBOW)) && weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty());
    }
    private static boolean foreignLease(AbstractArrow arrow) {
        return MultiAxisReflectionArrowBridge.leased(arrow) || TrajectoryReturnArrowBridge.leased(arrow) || ShortInterceptionArrowBridge.leased(arrow) || MagneticDomainArrows.leased(arrow) || MagneticArrowSeparation.leased(arrow) || IronSandLance.leased(arrow)
                || ElectromasterPrototype.leased(arrow) || ElectromasterChargeDecoy.leased(arrow) || MagneticGroundStep.leased(arrow);
    }
    private static boolean finite(Vec3 point) { return DissipationCurtain.finite(point); }
    private static long now(ServerLevel world) { return MagneticDomain.serverTime(world.getServer()); }
}
