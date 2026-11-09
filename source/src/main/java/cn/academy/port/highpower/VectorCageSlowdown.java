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

/** NEW441 native Slowness II. Each cast owns at most eight leases; foreign state always wins. */
@EventBusSubscriber(modid = "academy")
public final class VectorCageSlowdown {
    public static final int DURATION = 20;
    public static final int AMPLIFIER = 1;
    private static final int MAX_TARGETS = 8;
    // Synchronous native addEffect scope only. Nothing here survives a tick, unload, or server stop.
    private static final ThreadLocal<Addition> PENDING = new ThreadLocal<>();
    public interface NativeGuard {}
    private VectorCageSlowdown() {}

    /** Stored exclusively by one live controller, never in a global entity/UUID registry. */
    public static final class LeaseSet {
        private final Map<LivingEntity, Lease> leases = new IdentityHashMap<>();
        private boolean released;

        public boolean apply(LivingEntity target, ServerPlayer source, BooleanSupplier valid) {
            if (released || !thread(target) || source == null || source.level() != target.level()
                    || valid == null || !(target instanceof NativeGuard)) return false;
            Lease lease = leases.get(target);
            if (lease == null) {
                if (leases.size() >= MAX_TARGETS) return false;
                lease = new Lease(this, target, source, valid);
                leases.put(target, lease);
            }
            if (lease.source != source || lease.pending != null || lease.yielded) return false;
            lease.valid = valid;
            if (!live(lease) || !uncontested(lease) || lease.owned != null) return false;
            OwnedEffect incoming = new OwnedEffect(lease);
            Addition addition = new Addition(lease, incoming);
            lease.pending = addition;
            Addition previous = PENDING.get();
            if (previous != null) yieldOwnership(previous.lease);
            PENDING.set(addition);
            try {
                boolean accepted = target.addEffect(incoming, source);
                if (!accepted || !addition.committed || addition.refused || !live(lease)
                        || target.getEffect(MobEffects.MOVEMENT_SLOWDOWN) != incoming || !fresh(incoming)) {
                    yieldOwnership(lease);
                    return false;
                }
                lease.owned = incoming;
                lease.expiring = null;
                return true;
            } finally {
                if (previous == null) PENDING.remove(); else PENDING.set(previous);
                if (lease.pending == addition) lease.pending = null;
            }
        }

        /** Invalidate pending commits; installed native effects receive no refresh and expire naturally. */
        public void release() {
            released = true;
            for (Lease lease : leases.values()) {
                yieldOwnership(lease);
                lease.valid = null;
                lease.source = null;
            }
            leases.clear();
        }
    }

    private static final class Lease {
        final LeaseSet owner;
        final LivingEntity target;
        ServerPlayer source;
        BooleanSupplier valid;
        OwnedEffect owned, expiring;
        Addition pending;
        boolean yielded;
        Lease(LeaseSet owner, LivingEntity target, ServerPlayer source, BooleanSupplier valid) {
            this.owner = owner; this.target = target; this.source = source; this.valid = valid;
            yielded = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN) != null;
        }
    }

    /** Exact object provenance also notices foreign in-place updates and hidden-effect merges. */
    private static final class OwnedEffect extends MobEffectInstance {
        final Lease lease;
        OwnedEffect(Lease lease) {
            super(MobEffects.MOVEMENT_SLOWDOWN, DURATION, AMPLIFIER, false, false, false);
            this.lease = lease;
        }
        @Override public boolean update(MobEffectInstance other) {
            yieldOwnership(lease);
            return super.update(other);
        }
    }

    private static final class Addition {
        final Lease lease;
        final OwnedEffect incoming;
        boolean entered, added, committed, refused;
        Addition(Lease lease, OwnedEffect incoming) { this.lease = lease; this.incoming = incoming; }
    }

    /** Pinned addEffect HEAD, before Applicable: foreign/reentrant callbacks cannot reuse our object. */
    public static boolean beginNativeAddition(LivingEntity target, MobEffectInstance effect) {
        Addition active = PENDING.get();
        if (active != null && active.lease.target == target && active.incoming != effect)
            yieldOwnership(active.lease);
        if (!(effect instanceof OwnedEffect owned)) return true;
        Addition addition = owned.lease.pending;
        if (addition == null || addition.incoming != effect || addition.entered || addition.refused
                || target != owned.lease.target || !pendingCurrent(addition)
                || !live(owned.lease) || !pendingCurrent(addition) || !fresh(effect)
                || target.getEffect(MobEffects.MOVEMENT_SLOWDOWN) != null) {
            yieldOwnership(owned.lease);
            return false;
        }
        addition.entered = true;
        return true;
    }

    /** Pinned post-Added boundary, immediately before native put/update; only pending NEW441 can veto. */
    public static boolean allowCommit(LivingEntity target, MobEffectInstance effect) {
        if (!(effect instanceof OwnedEffect owned)) return true;
        Addition addition = owned.lease.pending;
        if (addition == null || addition.incoming != effect || target != owned.lease.target
                || !addition.entered || !addition.added || addition.committed || addition.refused
                || !pendingCurrent(addition) || !live(owned.lease) || !pendingCurrent(addition)
                || !fresh(effect) || target.getEffect(MobEffects.MOVEMENT_SLOWDOWN) != null) {
            yieldOwnership(owned.lease);
            return false;
        }
        addition.committed = true;
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void added(MobEffectEvent.Added event) {
        if (event.getOldEffectInstance() instanceof OwnedEffect prior) yieldOwnership(prior.lease);
        if (!(event.getEffectInstance() instanceof OwnedEffect owned)) return;
        Addition addition = owned.lease.pending;
        if (addition != null && addition.incoming == owned && event.getEntity() == owned.lease.target
                && event.getEffectSource() == owned.lease.source && event.getOldEffectInstance() == null
                && addition.entered && !addition.added && pendingCurrent(addition)) addition.added = true;
        else yieldOwnership(owned.lease);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void removed(MobEffectEvent.Remove event) {
        if (event.getEffectInstance() instanceof OwnedEffect owned) yieldOwnership(owned.lease);
        Addition active = PENDING.get();
        if (active != null && active.lease.target == event.getEntity()
                && event.getEffect().equals(MobEffects.MOVEMENT_SLOWDOWN)) yieldOwnership(active.lease);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void expired(MobEffectEvent.Expired event) {
        if (!(event.getEffectInstance() instanceof OwnedEffect owned)) return;
        Lease lease = owned.lease;
        if (event.isCanceled() || lease.owned != owned || owned.getDuration() != 0) yieldOwnership(lease);
        else lease.expiring = owned;
    }

    private static boolean uncontested(Lease lease) {
        MobEffectInstance current = lease.target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (current == null && lease.owned != null && lease.expiring == lease.owned && lease.owned.getDuration() == 0) {
            lease.owned = null;
            lease.expiring = null;
        }
        if (current != lease.owned || current != null && (!nativeShape(current) || current.getDuration() == 0))
            yieldOwnership(lease);
        return !lease.yielded;
    }

    private static boolean pendingCurrent(Addition addition) {
        Lease lease = addition.lease;
        return PENDING.get() == addition && lease.pending == addition && !addition.refused
                && !lease.yielded && !lease.owner.released && lease.owner.leases.get(lease.target) == lease;
    }
    private static boolean live(Lease lease) {
        if (lease.yielded || lease.owner.released || !thread(lease.target) || lease.source == null
                || !lease.target.isAlive() || lease.target.isRemoved() || !lease.source.isAlive() || lease.source.isRemoved()
                || lease.source.level() != lease.target.level() || lease.owner.leases.get(lease.target) != lease
                || lease.valid == null) return false;
        boolean valid;
        try { valid = lease.valid.getAsBoolean(); }
        catch (RuntimeException refused) { valid = false; }
        return valid && !lease.yielded && !lease.owner.released && lease.owner.leases.get(lease.target) == lease;
    }
    private static void yieldOwnership(Lease lease) {
        lease.yielded = true;
        if (lease.pending != null) lease.pending.refused = true;
    }
    private static boolean nativeShape(MobEffectInstance effect) {
        return effect != null && effect.is(MobEffects.MOVEMENT_SLOWDOWN) && effect.getAmplifier() == AMPLIFIER
                && effect.getDuration() >= 0 && effect.getDuration() <= DURATION
                && !effect.isAmbient() && !effect.isVisible() && !effect.showIcon();
    }
    private static boolean fresh(MobEffectInstance effect) { return nativeShape(effect) && effect.getDuration() == DURATION; }
    private static boolean thread(LivingEntity target) {
        return target != null && target.getServer() != null && target.getServer().isSameThread();
    }
}
