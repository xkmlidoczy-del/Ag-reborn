package cn.academy.port.highpower;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

/** NEW441 redirects only its real native hurt impulse into the contacted pressure plane. */
public final class VectorCageNativeBridge {
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();
    private static volatile boolean failed;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.vector_cage_native");
    private VectorCageNativeBridge() {}
    public static boolean available(LivingEntity entity) { return !failed && VectorParryNativeBridge.available(entity); }
    private static final class Frame {
        final LivingEntity entity;
        final VectorCage.EmissionPermit permit;
        final VectorParryNativeCall call;
        final Vec3 before;
        final double x, z, resistance;
        final boolean grounded;
        Vec3 expected;
        boolean applied, denied;
        Frame(LivingEntity entity, VectorCage.EmissionPermit permit, double strength, double x, double z) {
            this.entity = entity; this.permit = permit; call = new VectorParryNativeCall(entity, strength, x, z);
            before = permit.beforeVelocity(); resistance = permit.resistance(); grounded = permit.grounded();
            this.x = -permit.inward().x; this.z = -permit.inward().z;
        }
        boolean valid() {
            return ACTIVE.get() == this && call.valid() && available(entity)
                    && HighPowerCombat.vectorCageNativePermit(entity) == permit && permit.nativeValid()
                    && finite(before) && before.equals(entity.getDeltaMovement()) && grounded == entity.onGround()
                    && resistance == entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)
                    && ACTIVE.get() == this && call.valid();
        }
    }
    static void clear() { ACTIVE.remove(); }
    static void hurtEntered() { Frame frame = ACTIVE.get(); if (frame != null) frame.call.invalidate(); }
    static void nativeKnockback(LivingEntity entity, double strength, double x, double z, Operation<Void> original) {
        Frame pending = ACTIVE.get();
        if (pending != null) {
            pending.call.invalidate();
            original.call(entity, strength, x, z);
            return;
        }
        VectorCage.EmissionPermit permit = HighPowerCombat.vectorCageNativeSource(entity);
        if (permit == null) {
            // No closed NEW441 owner: preserve both established adapters and all native arguments exactly.
            YawPalmNativeBridge.nativeKnockback(entity, strength, x, z, original);
            return;
        }
        // A real handheld shield already owns an earlier native .5 response and the later .4 hurt call.
        // Preserve that complete native branch. Observed velocity or a posted event cannot select it.
        if (HighPowerCombat.vectorCageNativeShieldBlocked(entity, permit)) {
            permit.shieldBlocked = true;
            YawPalmNativeBridge.nativeKnockback(entity, strength, x, z, original);
            return;
        }
        // .252 posts its native event before randomizing a zero source offset. A certified
        // pressure face supplies its own unit normal to that same real impulse, even at t=0.
        Frame frame = new Frame(entity, permit, strength, x, z);
        try {
            frame.denied = !available(entity) || HighPowerCombat.vectorCageNativePermit(entity) != permit
                    || strength != (double).4F || x != permit.origin().x-entity.getX()
                    || z != permit.origin().z-entity.getZ() || !permit.claimNative();
        } catch (RuntimeException | LinkageError failure) { frame.denied = true; disable(failure); }
        ACTIVE.set(frame); boolean completed = false;
        try {
            original.call(entity, strength, x, z);
            completed = true;
        } finally {
            ACTIVE.remove();
            if (completed && frame.applied && frame.call.valid() && frame.expected != null
                    && frame.expected.equals(entity.getDeltaMovement())) {
                try { permit.completeNative(frame.expected, frame.before); }
                catch (RuntimeException | LinkageError failure) { disable(failure); }
            }
        }
    }
    static LivingKnockBackEvent nativeEvent(LivingEntity entity, float strength, double x, double z,
                                            Operation<LivingKnockBackEvent> original) {
        Frame frame = ACTIVE.get();
        if (frame == null) return YawPalmNativeBridge.nativeEvent(entity, strength, x, z, original);
        // Claim the original native .4/source-origin call before any listener can recursively dispatch.
        if (!frame.call.claim(entity, strength, x, z)) return original.call(entity, strength, x, z);
        // The one actual native event exposes the actual contacted wall inward direction to every priority.
        LivingKnockBackEvent event = frame.denied ? original.call(entity, strength, x, z)
                : original.call(entity, strength, frame.x, frame.z);
        if (event == null || event.isCanceled()) return event;
        try {
            if (frame.denied || !frame.valid() || !bounded(event, frame)) { event.setCanceled(true); return event; }
            double effective = (double)event.getStrength()*(1-frame.resistance);
            Vec3 force = new Vec3(frame.x, 0, frame.z).normalize().scale(effective);
            frame.expected = effective <= 0 ? frame.before : new Vec3(frame.before.x/2-force.x,
                    frame.grounded ? Math.min(.4D, frame.before.y/2+effective) : frame.before.y, frame.before.z/2-force.z);
            if (!finite(frame.expected) || !frame.valid() || !bounded(event, frame)) {
                event.setCanceled(true); return event;
            }
            frame.applied = true;
        } catch (RuntimeException | LinkageError failure) { event.setCanceled(true); disable(failure); }
        return event;
    }
    private static boolean bounded(LivingKnockBackEvent event, Frame frame) {
        return !event.isCanceled() && event.getEntity() == frame.entity
                && event.getOriginalStrength() == (float).4F
                && event.getOriginalRatioX() == frame.x && event.getOriginalRatioZ() == frame.z
                && Float.isFinite(event.getStrength()) && event.getStrength() >= 0 && event.getStrength() <= (float).4F
                && event.getRatioX() == frame.x && event.getRatioZ() == frame.z;
    }
    private static boolean finite(Vec3 value) { return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z); }
    private static void disable(Throwable failure) {
        failed = true;
        LOG.warn("NEW441 native redirect unavailable after callback failure; ordinary combat and existing adapters remain unchanged", failure);
    }
}
