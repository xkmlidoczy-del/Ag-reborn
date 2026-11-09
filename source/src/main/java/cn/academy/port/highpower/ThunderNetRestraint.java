package cn.academy.port.highpower;

import cn.academy.port.core.LegacySingleKeyServerIdentity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** NEW-141: bounded server-mob movement-speed leases, never player movement or potion state. */
@EventBusSubscriber(modid = "academy")
public final class ThunderNetRestraint {
    public static final ResourceLocation MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("academy", "thunder_net_restraint");
    public static final int MAX_TARGETS = 6, MAX_LEASES_PER_TARGET = 16, MAX_CONTROLLER_TICKS = 60, DEFENSIVE_TICKS = 2;
    public static final double SLOWDOWN = .30;
    private static final Map<Object, Controller> CONTROLLERS = new IdentityHashMap<>();
    private static final Map<LivingEntity, Target> TARGETS = new IdentityHashMap<>();
    private static boolean committing;

    private ThunderNetRestraint() {}

    private static final class Controller {
        final Object key;
        final LegacySingleKeyServerIdentity identity;
        final long started, until;
        final Map<LivingEntity, Lease> leases = new IdentityHashMap<>();
        // Keep admission history until this controller ends: releasing a target cannot exceed six unique targets.
        final Set<UUID> admitted = new java.util.HashSet<>();
        Controller(Object key, LegacySingleKeyServerIdentity identity, long now) {
            this.key = key; this.identity = identity; started = now; until = now + MAX_CONTROLLER_TICKS;
        }
    }

    private static final class Target {
        final LivingEntity entity;
        final ServerLevel level;
        final UUID uuid;
        final int id;
        final AttributeInstance attribute;
        final AttributeModifier modifier = new AttributeModifier(MODIFIER_ID, -SLOWDOWN, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        final Map<Object, Lease> leases = new IdentityHashMap<>();
        Target(LivingEntity entity, AttributeInstance attribute) {
            this.entity = entity; level = (ServerLevel) entity.level(); uuid = entity.getUUID(); id = entity.getId(); this.attribute = attribute;
        }
    }

    private static final class Lease {
        final Controller controller;
        final Target target;
        BooleanSupplier stillValid;
        long until;
        Lease(Controller controller, Target target, BooleanSupplier stillValid, long now) {
            this.controller = controller; this.target = target; this.stillValid = stillValid; until = now + DEFENSIVE_TICKS;
        }
    }

    /** One same-tick preflight; preparation itself never changes movement, CP, effects, or lease ownership. */
    public static final class Claim {
        private final Object key;
        private final LegacySingleKeyServerIdentity identity;
        private final LivingEntity entity;
        private final AttributeInstance attribute;
        private final Target captured;
        private final Controller capturedController;
        private final BooleanSupplier stillValid;
        private final long tick;
        private boolean consumed;
        private Claim(Object key, LegacySingleKeyServerIdentity identity, LivingEntity entity, long tick, BooleanSupplier stillValid) {
            this.key = key; this.identity = identity; this.entity = entity; this.tick = tick; this.stillValid = stillValid;
            attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED); captured = TARGETS.get(entity);
            capturedController = CONTROLLERS.get(key);
        }
    }

    /** Collision and player refusal are observable before the caller attempts a resource transaction. */
    public static boolean available(LegacySingleKeyServerIdentity identity, LivingEntity entity) {
        if (!validOwner(identity) || !eligible(identity.owner(), entity)) return false;
        AttributeInstance attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        Target target = TARGETS.get(entity);
        if (target == null) return attribute.getModifier(MODIFIER_ID) == null;
        return targetValid(target) && (target.leases.size() < MAX_LEASES_PER_TARGET
                || target.leases.values().stream().anyMatch(lease -> lease.controller.identity == identity));
    }

    public static Claim prepare(Object controller, LegacySingleKeyServerIdentity identity, LivingEntity entity,
                                long now, BooleanSupplier stillValid) {
        if (controller == null || stillValid == null || !available(identity, entity) || now != now(identity.level().getServer())
                || now < 0 || now > Long.MAX_VALUE - MAX_CONTROLLER_TICKS || !stillValid.getAsBoolean()) return null;
        Controller current = CONTROLLERS.get(controller);
        if (current != null && (!controllerValid(current, now) || current.identity != identity
                || !current.admitted.contains(entity.getUUID()) && current.admitted.size() >= MAX_TARGETS)) return null;
        Claim claim = new Claim(controller, identity, entity, now, stillValid);
        return structural(claim, now) ? claim : null;
    }

    /**
     * Validate the complete batch, then debit once, then install all leases before any native damage callback.
     * This supplier is a closed synchronous prototype CP debit, not an extension/event hook. Its caller must not
     * mutate any target, attribute or controller after prevalidation. There is no atomicity promise across foreign code.
     * In this reviewed native runtime addTransientModifier only dirties AttributeMap sets; it fires no gameplay hook.
     * There is deliberately no fallible target/claim check or external callback after a successful payment.
     */
    public static boolean commitPaid(List<Claim> claims, BooleanSupplier payment) {
        if (committing || claims == null || claims.isEmpty() || claims.size() > MAX_TARGETS || payment == null) return false;
        List<Claim> batch = new ArrayList<>(claims);
        Claim first = batch.getFirst();
        if (first == null || !validOwner(first.identity)) return false;
        long now = now(first.identity.level().getServer());
        Set<LivingEntity> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<UUID> admitted = new java.util.HashSet<>();
        Controller controller = CONTROLLERS.get(first.key);
        if (controller != null) admitted.addAll(controller.admitted);
        for (Claim claim : batch) {
            if (claim == null || claim.key != first.key || claim.identity != first.identity || !unique.add(claim.entity)
                    || !structural(claim, now) || !claim.stillValid.getAsBoolean()) return false;
            admitted.add(claim.entity.getUUID());
        }
        if (admitted.size() > MAX_TARGETS) return false;
        // Predicates are caller code. Recheck every captured identity after all predicates and before payment.
        for (Claim claim : batch) if (!structural(claim, now)) return false;
        committing = true;
        try {
            batch.forEach(claim -> claim.consumed = true);
            if (!payment.getAsBoolean()) return false;
            if (controller == null) {
                controller = new Controller(first.key, first.identity, now);
                CONTROLLERS.put(first.key, controller);
            }
            for (Claim claim : batch) {
                Target target = TARGETS.get(claim.entity);
                if (target == null) {
                    target = new Target(claim.entity, claim.attribute);
                    target.attribute.addTransientModifier(target.modifier);
                    TARGETS.put(claim.entity, target);
                }
                Lease lease = target.leases.get(first.key);
                if (lease == null) {
                    lease = new Lease(controller, target, claim.stillValid, now);
                    target.leases.put(first.key, lease); controller.leases.put(claim.entity, lease);
                } else {
                    lease.stillValid = claim.stillValid; lease.until = now + DEFENSIVE_TICKS;
                }
                controller.admitted.add(claim.entity.getUUID());
            }
            return true;
        } finally { committing = false; }
    }

    /** Backend revalidates locks every tick. No CP-free new acquisition is possible through refresh. */
    public static boolean hold(Object controller, LivingEntity entity) {
        Controller current = CONTROLLERS.get(controller);
        if (current == null || !thread(current.identity.level().getServer())) return false;
        Lease lease = current.leases.get(entity);
        if (lease == null) return false;
        long now = now(current.identity.level().getServer());
        if (!leaseValid(lease, now)) { releaseLease(lease); return false; }
        lease.until = Math.min(current.until, now + DEFENSIVE_TICKS);
        return true;
    }

    public static void release(Object controller, LivingEntity entity) {
        Controller current = CONTROLLERS.get(controller);
        if (current == null || !thread(current.identity.level().getServer())) return;
        Lease lease = current.leases.get(entity);
        if (lease != null) releaseLease(lease);
    }

    public static void releaseController(Object controller) {
        Controller current = CONTROLLERS.get(controller);
        if (current == null || !thread(current.identity.level().getServer())) return;
        CONTROLLERS.remove(controller);
        for (Lease lease : new ArrayList<>(current.leases.values())) releaseLease(lease);
    }

    /** Defensive orphan cleanup, including controllers which no longer receive owner ticks. */
    public static void tick(MinecraftServer server) {
        if (!thread(server)) return;
        long now = now(server);
        for (Controller controller : new ArrayList<>(CONTROLLERS.values())) {
            if (controller.identity.level().getServer() != server) continue;
            if (!controllerValid(controller, now)) { releaseController(controller.key); continue; }
            for (Lease lease : new ArrayList<>(controller.leases.values())) if (!leaseValid(lease, now)) releaseLease(lease);
        }
    }

    public static void clearAll() {
        for (Controller controller : new ArrayList<>(CONTROLLERS.values())) releaseController(controller.key);
    }

    private static boolean structural(Claim claim, long now) {
        if (claim.consumed || claim.tick != now || !validOwner(claim.identity) || !eligible(claim.identity.owner(), claim.entity)
                || claim.entity.getAttribute(Attributes.MOVEMENT_SPEED) != claim.attribute || TARGETS.get(claim.entity) != claim.captured) return false;
        Controller controller = CONTROLLERS.get(claim.key);
        if (controller != claim.capturedController) return false;
        if (controller != null && (controller.identity != claim.identity || !controllerValid(controller, now))) return false;
        return claim.captured == null ? claim.attribute.getModifier(MODIFIER_ID) == null
                : targetValid(claim.captured) && (claim.captured.leases.containsKey(claim.key)
                    || claim.captured.leases.size() < MAX_LEASES_PER_TARGET);
    }

    private static boolean validOwner(LegacySingleKeyServerIdentity identity) {
        if (identity == null || !thread(identity.level().getServer())) return false;
        ServerPlayer owner = identity.owner();
        return identity.owns(owner) && identity.level().getEntity(owner.getUUID()) == owner
                && owner.connection != null && !owner.hasDisconnected() && owner.isAlive() && !owner.isRemoved() && !owner.isSpectator()
                && identity.state().activated && identity.state().overloadFine && identity.state().category.equals("electromaster")
                && identity.state().learned("thunder_net");
    }

    private static boolean eligible(ServerPlayer owner, LivingEntity entity) {
        if (!(HighPowerCombat.hostileTarget(entity)) || !HighPowerCombat.supported(entity) || entity.level() != owner.serverLevel()
                || !entity.isAlive() || entity.isRemoved() || entity.isSpectator() || entity.isPassenger() || entity.isVehicle()
                || owner.serverLevel().getEntity(entity.getId()) != entity || owner.serverLevel().getEntity(entity.getUUID()) != entity
                || owner.isAlliedTo(entity) || entity.isAlliedTo(owner) || HighPowerCombat.unsupportedReason(entity).isPresent()) return false;
        AttributeInstance attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        return attribute != null && attribute.getClass() == AttributeInstance.class && Double.isFinite(attribute.getBaseValue())
                && Double.isFinite(attribute.getValue()) && attribute.getValue() >= 0;
    }

    private static boolean controllerValid(Controller controller, long now) {
        return now >= controller.started && now < controller.until && validOwner(controller.identity);
    }

    private static boolean targetValid(Target target) {
        return target.entity.level() == target.level && target.id == target.entity.getId() && target.uuid.equals(target.entity.getUUID())
                && target.level.getEntity(target.id) == target.entity && target.level.getEntity(target.uuid) == target.entity
                && target.entity.isAlive() && !target.entity.isRemoved()
                && target.entity.getAttribute(Attributes.MOVEMENT_SPEED) == target.attribute
                && target.attribute.getModifier(MODIFIER_ID) == target.modifier;
    }

    private static boolean leaseValid(Lease lease, long now) {
        return now < lease.until && controllerValid(lease.controller, now) && targetValid(lease.target)
                && eligible(lease.controller.identity.owner(), lease.target.entity) && lease.stillValid.getAsBoolean()
                && CONTROLLERS.get(lease.controller.key) == lease.controller
                && lease.controller.leases.get(lease.target.entity) == lease && lease.target.leases.get(lease.controller.key) == lease
                && TARGETS.get(lease.target.entity) == lease.target && targetValid(lease.target);
    }

    private static void releaseLease(Lease lease) {
        lease.controller.leases.remove(lease.target.entity, lease);
        lease.target.leases.remove(lease.controller.key, lease);
        if (!lease.target.leases.isEmpty()) return;
        TARGETS.remove(lease.target.entity, lease.target);
        // Native removeModifier(AttributeModifier) also deletes by ID, so identity comparison MUST precede it.
        // Never dirty a detached AttributeInstance: its old AttributeMap callback could enqueue stale native sync.
        // A replacement instance or a foreign same-ID modifier is neither changed nor overwritten with cached state.
        if (lease.target.entity.getAttribute(Attributes.MOVEMENT_SPEED) == lease.target.attribute
                && lease.target.attribute.getModifier(MODIFIER_ID) == lease.target.modifier)
            lease.target.attribute.removeModifier(MODIFIER_ID);
    }

    private static void leave(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !thread(level.getServer())) return;
        Target target = TARGETS.get(entity);
        if (target != null) for (Lease lease : new ArrayList<>(target.leases.values())) releaseLease(lease);
        for (Controller controller : new ArrayList<>(CONTROLLERS.values()))
            if (controller.identity.owner() == entity) releaseController(controller.key);
    }

    private static boolean thread(MinecraftServer server) { return server != null && server.isSameThread(); }
    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) { tick(event.getServer()); }
    @SubscribeEvent public static void leaving(EntityLeaveLevelEvent event) { if (event.getEntity() instanceof LivingEntity entity) leave(entity); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { if (!event.isCanceled()) leave(event.getEntity()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { leave(event.getEntity()); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) { leave(event.getOriginal()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { leave(event.getEntity()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { clearAll(); }
}
