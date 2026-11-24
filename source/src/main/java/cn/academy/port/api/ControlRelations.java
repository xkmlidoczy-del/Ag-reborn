package cn.academy.port.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.*;

/**
 * Shared ownership of active control effects and their attributed attacks on the logical server.
 */
@EventBusSubscriber(modid = "academy")
public final class ControlRelations {
    private record Lease(UUID owner, long expires, String skill) {}

    private record Refusal(long tick, StackTraceElement caller, Lease lease) {}

    private static final Map<Entity, Lease> LEASES = new WeakHashMap<>();
    private static final Map<Entity, Refusal> REFUSED = new WeakHashMap<>();
    private static final ThreadLocal<Lease> ACTOR = new ThreadLocal<>();
    private static final ThreadLocal<Entity> TICKING = new ThreadLocal<>();
    private static final StackWalker WALKER =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final String SHOT_OWNER = "academy.control_shot_owner";

    private ControlRelations() {}

    public static boolean inForeignTick(Entity target) {
        Entity actor = TICKING.get();
        return actor != null && actor != target && actor.getRootVehicle() != target.getRootVehicle();
    }

    /** An unrelated sourced hit inside a controlled actor's tick keeps its own authority. */
    public static Runnable enterDamage(LivingEntity victim, DamageSource source) {
        if (!(victim.level() instanceof ServerLevel)
                || LEASES.isEmpty()
                || source.getEntity() == null) return null;
        var parent = ACTOR.get();
        if (parent == null) return null;
        var next = attributed(source.getEntity());
        if (next == null) next = attributed(source.getDirectEntity());
        if (next == parent) return null;
        if (next == null) ACTOR.remove();
        else ACTOR.set(next);
        return () -> ACTOR.set(parent);
    }

    public static boolean bind(ServerPlayer owner, LivingEntity body, long until, String skill) {
        if (body == owner || body.level() != owner.level() || until <= body.level().getGameTime()
                || !CombatControl.canAffect(owner, body))
            return false;
        Lease old = lease(body);
        if (old != null && (!old.owner.equals(owner.getUUID()) || !old.skill.equals(skill)))
            return false;
        LEASES.put(body, new Lease(owner.getUUID(), until, skill));
        return true;
    }

    public static void release(Entity body, String skill) {
        var lease = LEASES.get(body);
        if (lease != null && lease.skill.equals(skill)) LEASES.remove(body);
    }

    private static Lease lease(Entity body) {
        if (body == null || LEASES.isEmpty()) return null;
        Lease lease = LEASES.get(body);
        if (lease != null && lease.expires <= body.level().getGameTime()) {
            LEASES.remove(body);
            return null;
        }
        return lease;
    }

    private static Lease attributed(Entity source) {
        Lease active = lease(source);
        if (active == null && source instanceof Projectile projectile)
            active = lease(projectile.getOwner());
        if (active == null && source instanceof OwnableEntity pet) active = lease(pet.getOwner());
        return active;
    }

    public static boolean allied(ServerPlayer player, Entity target) {
        Lease other = lease(target);
        return other != null && other.owner.equals(player.getUUID());
    }

    private static boolean protects(Lease lease, Entity target) {
        if (lease == null || !(target.level() instanceof ServerLevel level)) return false;
        if (target.getUUID().equals(lease.owner)) return true;
        var owner = level.getPlayerByUUID(lease.owner);
        var other = attributed(target);
        return other != null && other.owner.equals(lease.owner)
                || owner != null
                        && (owner.isAlliedTo(target)
                                || target.isAlliedTo(owner)
                                || target instanceof OwnableEntity pet
                                        && owner.getUUID().equals(pet.getOwnerUUID())
                                || target instanceof net.minecraft.world.entity.player.Player player
                                        && !owner.canHarmPlayer(player));
    }

    public static void tick(Entity actor, Runnable original) {
        Entity previousEntity = TICKING.get();
        Lease parent = ACTOR.get();
        Lease next = attributed(actor);
        TICKING.set(actor);
        if (next != parent) {
            if (next == null) ACTOR.remove();
            else ACTOR.set(next);
        }
        try {
            original.run();
        } finally {
            if (previousEntity == null) TICKING.remove();
            else TICKING.set(previousEntity);
            if (next != parent) {
                if (parent == null) ACTOR.remove();
                else ACTOR.set(parent);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void incoming(LivingIncomingDamageEvent event) {
        if (refuses(event.getEntity(), event.getSource())) event.setCanceled(true);
    }

    public static boolean refuses(LivingEntity target, DamageSource source) {
        if (!(target.level() instanceof ServerLevel) || LEASES.isEmpty()) return false;
        Entity actor = source.getEntity(), direct = source.getDirectEntity();
        Lease cause = attributed(actor);
        if (cause == null) cause = attributed(direct);
        if (cause == null && actor == null) cause = ACTOR.get();
        if (!protects(cause, target)) return false;
        if (ACTOR.get() == cause || actor == null) return true;
        // Called outside an entity tick (e.g. a scheduled melee move). Bind following direct writes
        // to the attacker's own stack frame, never to the remainder of the server tick.
        var frame = attackingMethod(actor);
        if (frame != null)
            REFUSED.put(target, new Refusal(target.level().getGameTime(), frame, cause));
        return true;
    }

    public static StackTraceElement attackingMethod(Entity actor) {
        if (actor == null) return null;
        Class<?> type = actor.getClass();
        return WALKER.walk(
                frames ->
                        frames.filter(
                                        f ->
                                                f.getDeclaringClass().isAssignableFrom(type)
                                                        && Entity.class.isAssignableFrom(
                                                                f.getDeclaringClass())
                                                        && !f.getMethodName().equals("hurt")
                                                        && !f.getMethodName().contains("$"))
                                .findFirst()
                                .map(StackWalker.StackFrame::toStackTraceElement)
                                .orElse(null));
    }

    public static boolean stillAttacking(StackTraceElement caller) {
        return WALKER.walk(
                frames ->
                        frames.anyMatch(
                                f ->
                                        f.getClassName().equals(caller.getClassName())
                                                && f.getMethodName()
                                                        .equals(caller.getMethodName())));
    }

    public static boolean blocksWrite(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel) || LEASES.isEmpty()) return false;
        if (protects(ACTOR.get(), target)) return true;
        Refusal refused = REFUSED.get(target);
        if (refused == null) return false;
        if (refused.tick == target.level().getGameTime()
                && protects(refused.lease, target)
                && stillAttacking(refused.caller)) return true;
        REFUSED.remove(target);
        return false;
    }

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        var entity = event.getEntity();
        if (entity instanceof Projectile
                && event.loadedFromDisk()
                && entity.getPersistentData().hasUUID(SHOT_OWNER)) {
            LEASES.put(
                    entity,
                    new Lease(
                            entity.getPersistentData().getUUID(SHOT_OWNER),
                            Long.MAX_VALUE,
                            "projectile"));
            return;
        }
        if (LEASES.isEmpty()) return;
        Lease owner = attributed(entity);
        if (owner == null && (entity instanceof Mob || entity instanceof Projectile))
            owner = ACTOR.get();
        if (owner == null) return;
        if (entity instanceof Projectile) {
            entity.getPersistentData().putUUID(SHOT_OWNER, owner.owner);
            LEASES.put(entity, new Lease(owner.owner, Long.MAX_VALUE, "projectile"));
        } else LEASES.put(entity, owner);
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        LEASES.remove(event.getEntity());
        REFUSED.remove(event.getEntity());
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        LEASES.values()
                .removeIf(
                        lease ->
                                !lease.skill.equals("projectile")
                                        && lease.owner.equals(event.getEntity().getUUID()));
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        LEASES.clear();
        REFUSED.clear();
    }
}
