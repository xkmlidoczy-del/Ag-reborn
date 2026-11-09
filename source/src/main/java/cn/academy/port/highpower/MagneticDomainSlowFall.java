package cn.academy.port.highpower;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/** Owned native, two-tick slow falling. Foreign potion ownership wins for the whole lease. */
@EventBusSubscriber(modid = "academy")
public final class MagneticDomainSlowFall {
    public static final int DURATION = 2;
    private static final Map<ServerPlayer, Lease> LEASES = new IdentityHashMap<>();
    // Kept separately from LEASES: a callback may release/clear a lease before addEffect commits.
    private static final Map<MobEffectInstance, Addition> PENDING = new IdentityHashMap<>();

    /** Absent when the reviewed native addEffect guard was not installed: refresh then fails closed. */
    public interface NativeGuard {}

    private static final class Lease {
        final ServerPlayer player, source;
        final Object key;
        BooleanSupplier valid;
        MobEffectInstance owned, expiring;
        boolean yielded;
        Addition pending;
        Lease(ServerPlayer player, ServerPlayer source, Object key, BooleanSupplier valid) {
            this.player = player; this.source = source; this.key = key; this.valid = valid;
            yielded = player.getEffect(MobEffects.SLOW_FALLING) != null;
        }
    }

    private static final class Addition {
        final Lease lease;
        final MobEffectInstance incoming, before;
        boolean entered, added, committed, refused;
        Addition(Lease lease, MobEffectInstance incoming, MobEffectInstance before) {
            this.lease = lease; this.incoming = incoming; this.before = before;
        }
    }

    private MagneticDomainSlowFall() {}

    public static void refresh(ServerPlayer player, Object key, BooleanSupplier valid) {
        refresh(player, player, key, valid);
    }

    /** Same guarded native effect, attributed to its actual paid domain owner. */
    public static void refresh(ServerPlayer player, ServerPlayer source, Object key, BooleanSupplier valid) {
        if (!thread(player) || !thread(source) || source.serverLevel() != player.serverLevel()) return;
        if (!thread(player) || key == null || valid == null || !(player instanceof NativeGuard)) return;
        Lease lease = LEASES.get(player);
        // Reentrant refresh cannot replace the currently executing native addition.
        if (lease != null && lease.pending != null) return;
        if (lease == null || lease.key != key || lease.source != source) {
            lease = new Lease(player, source, key, valid);
            LEASES.put(player, lease);
        } else lease.valid = valid;
        if (!live(lease) || !uncontested(lease) || !descending(player)) return;

        MobEffectInstance incoming = new MobEffectInstance(MobEffects.SLOW_FALLING, DURATION, 0, false, false, false);
        Addition addition = new Addition(lease, incoming, player.getEffect(MobEffects.SLOW_FALLING));
        lease.pending = addition;
        PENDING.put(incoming, addition);
        try {
            player.addEffect(incoming, source);
            if (!addition.committed || addition.refused || !live(lease)) {
                if (source != player) yieldOwnership(lease);
                return;
            }
            MobEffectInstance installed = player.getEffect(MobEffects.SLOW_FALLING);
            // Vanilla updates the previous object in place; never claim by type/amplifier alone.
            if (installed != (addition.before == null ? incoming : addition.before) || !nativeShape(installed)) {
                yieldOwnership(lease);
                return;
            }
            lease.owned = installed;
            lease.expiring = null;
        } finally {
            PENDING.remove(incoming, addition);
            if (lease.pending == addition) lease.pending = null;
        }
    }

    /** Called at native HEAD, before Applicable. Reusing our pending object recursively is refused. */
    public static boolean beginNativeAddition(LivingEntity entity, MobEffectInstance incoming) {
        Addition addition = PENDING.get(incoming);
        if (addition == null) return true;
        if (entity != addition.lease.player || addition.entered || addition.refused
                || !pendingCurrent(addition) || !live(addition.lease) || !pendingCurrent(addition)
                || !descending(addition.lease.player) || !fresh(incoming)) {
            addition.refused = true;
            yieldOwnership(addition.lease);
            return false;
        }
        addition.entered = true;
        return true;
    }

    /** Called after the entire noncancellable Added dispatch, immediately before native put/update. */
    public static boolean allowCommit(LivingEntity entity, MobEffectInstance incoming) {
        Addition addition = PENDING.get(incoming);
        if (addition == null) return true;
        Lease lease = addition.lease;
        if (entity != lease.player || !addition.entered || !addition.added || addition.committed
                || addition.refused || !pendingCurrent(addition) || !live(lease) || !pendingCurrent(addition)
                || !descending(lease.player) || !fresh(incoming)
                || lease.player.getEffect(MobEffects.SLOW_FALLING) != addition.before
                || addition.before != null && (lease.owned != addition.before || !nativeShape(addition.before))) {
            addition.refused = true;
            yieldOwnership(lease);
            return false;
        }
        addition.committed = true;
        return true;
    }

    public static boolean active(ServerPlayer player, Object key) {
        if (!thread(player)) return false;
        Lease lease = LEASES.get(player);
        return lease != null && lease.key == key && live(lease) && uncontested(lease)
                && lease.owned != null && lease.owned.getDuration() > 0 && descending(player);
    }

    static MobEffectInstance ownedEffect(ServerPlayer player, Object key) {
        Lease lease = LEASES.get(player);
        return lease != null && lease.key == key && !lease.yielded && lease.owned == player.getEffect(MobEffects.SLOW_FALLING)
                ? lease.owned : null;
    }
    static boolean yielded(ServerPlayer player, Object key) {
        Lease lease = LEASES.get(player);
        return lease != null && lease.key == key && lease.yielded;
    }

    /** Stop refreshing only. The native effect expires without touching any foreign potion state. */
    public static void release(ServerPlayer player, Object key) {
        if (!thread(player)) return;
        Lease lease = LEASES.get(player);
        if (lease != null && lease.key == key) {
            yieldOwnership(lease);
            LEASES.remove(player, lease);
        }
    }

    public static void clear() {
        for (Lease lease : LEASES.values()) yieldOwnership(lease);
        LEASES.clear();
        // Do not forget executing additions: their post-Added fence must still refuse the write.
        for (Addition addition : PENDING.values()) addition.refused = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void added(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !thread(player)
                || !event.getEffectInstance().is(MobEffects.SLOW_FALLING)) return;
        Lease lease = LEASES.get(player);
        if (lease == null) return;
        Addition addition = PENDING.get(event.getEffectInstance());
        if (addition != null && addition.lease == lease && addition.entered && !addition.added
                && !addition.refused && pendingCurrent(addition) && event.getEffectSource() == lease.source
                && event.getOldEffectInstance() == addition.before) {
            addition.added = true;
        } else yieldOwnership(lease);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void removed(MobEffectEvent.Remove event) {
        if (event.getEntity() instanceof ServerPlayer player && thread(player)
                && event.getEffect().equals(MobEffects.SLOW_FALLING)) {
            Lease lease = LEASES.get(player);
            if (lease != null) yieldOwnership(lease);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void expired(MobEffectEvent.Expired event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !thread(player)) return;
        Lease lease = LEASES.get(player);
        if (lease != null && lease.owned == event.getEffectInstance()) lease.expiring = lease.owned;
    }

    private static boolean pendingCurrent(Addition addition) {
        Lease lease = addition.lease;
        return LEASES.get(lease.player) == lease && lease.pending == addition && !lease.yielded && !addition.refused;
    }

    private static boolean live(Lease lease) {
        if (LEASES.get(lease.player) != lease || lease.yielded || !thread(lease.player)) return false;
        boolean valid = lease.valid.getAsBoolean();
        return valid && LEASES.get(lease.player) == lease && !lease.yielded;
    }

    private static boolean uncontested(Lease lease) {
        MobEffectInstance current = lease.player.getEffect(MobEffects.SLOW_FALLING);
        if (current == null && lease.owned != null && lease.expiring == lease.owned && lease.owned.getDuration() == 0) {
            lease.owned = null;
            lease.expiring = null;
        }
        if (current != lease.owned || current != null && !nativeShape(current)) yieldOwnership(lease);
        return !lease.yielded;
    }

    private static void yieldOwnership(Lease lease) {
        lease.yielded = true;
        if (lease.pending != null) lease.pending.refused = true;
    }

    private static boolean nativeShape(MobEffectInstance effect) {
        return effect != null && effect.is(MobEffects.SLOW_FALLING) && effect.getAmplifier() == 0
                && effect.getDuration() >= 0 && effect.getDuration() <= DURATION
                && !effect.isAmbient() && !effect.isVisible() && !effect.showIcon();
    }

    private static boolean fresh(MobEffectInstance effect) { return nativeShape(effect) && effect.getDuration() == DURATION; }
    private static boolean descending(ServerPlayer player) {
        double y = player.getDeltaMovement().y;
        return Double.isFinite(y) && y < 0 && !player.onGround();
    }
    private static boolean thread(ServerPlayer player) { return player != null && player.server != null && player.server.isSameThread(); }
}
