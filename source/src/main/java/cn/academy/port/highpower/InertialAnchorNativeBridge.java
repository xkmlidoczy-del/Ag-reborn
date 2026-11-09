package cn.academy.port.highpower;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** NEW422 consumes only the existing reviewed hurt knockback call, after its event listeners. */
public final class InertialAnchorNativeBridge {
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();
    private static final Logger LOG = LoggerFactory.getLogger("academy.inertial_anchor_native");
    private static volatile boolean failed;
    private InertialAnchorNativeBridge() {}

    /** A sibling failure must never revoke the established NEW412 native adapter. */
    public static boolean available(LivingEntity entity) {
        return !failed && VectorParryNativeBridge.available(entity);
    }

    private static final class Frame {
        final ServerPlayer player;
        final InertialAnchor.NativeReduction reduction;
        final VectorParryNativeCall call;
        final Vec3 position;
        final AABB body;
        final Pose pose;
        final boolean grounded;
        Vec3 before, expected;
        double resistance;
        boolean applied;
        Frame(ServerPlayer player, InertialAnchor.NativeReduction reduction, double strength, double x, double z) {
            this.player = player; this.reduction = reduction;
            call = new VectorParryNativeCall(player, strength, x, z);
            position = player.position(); body = player.getBoundingBox(); pose = player.getPose(); grounded = player.onGround();
        }
        boolean physicalIdentity() {
            return ACTIVE.get() == this && call.valid() && player.position().equals(position)
                    && player.getBoundingBox().equals(body) && player.getPose() == pose
                    && grounded && player.onGround() == grounded;
        }
    }

    /** Even a rejected recursive hurt retires the outer reduction before native dispatch. */
    static void hurtEntered() {
        Frame frame = ACTIVE.get();
        if (frame != null) frame.call.invalidate();
    }

    static void nativeKnockback(LivingEntity entity, double strength, double x, double z, Operation<Void> original) {
        Frame pending = ACTIVE.get();
        if (pending != null) {
            pending.call.invalidate();
            original.call(entity, strength, x, z);
            return;
        }
        InertialAnchor.NativeReduction reduction = null;
        if (entity instanceof ServerPlayer player && available(player)
                && Double.isFinite(strength) && strength > 0 && strength == (double)(float)strength) {
            try { reduction = InertialAnchor.beginNativeReduction(player, strength, x, z); }
            catch (RuntimeException | LinkageError failure) { disable(failure); }
        }
        if (reduction == null) {
            original.call(entity, strength, x, z);
            return;
        }
        ServerPlayer player = (ServerPlayer)entity;
        Frame frame = new Frame(player, reduction, strength, x, z);
        ACTIVE.set(frame);
        boolean completed = false;
        try {
            original.call(entity, strength, x, z);
            completed = true;
        } finally {
            // Completion may notify resource observers; no native receipt remains available to them.
            ACTIVE.remove();
            boolean changed = completed && frame.applied && frame.call.valid()
                    && frame.expected != null && frame.expected.equals(player.getDeltaMovement());
            try { InertialAnchor.finishNativeReduction(player, reduction, changed); }
            catch (RuntimeException | LinkageError failure) { disable(failure); }
        }
    }

    static LivingKnockBackEvent nativeEvent(LivingEntity entity, float strength, double x, double z,
                                            Operation<LivingKnockBackEvent> original) {
        Frame frame = ACTIVE.get();
        // Claim before listeners: nested event dispatch cannot borrow this exact native call.
        boolean claimed = frame != null && frame.call.claim(entity, strength, x, z);
        LivingKnockBackEvent event = original.call(entity, strength, x, z);
        if (!claimed || event == null || event.isCanceled() || entity != frame.player || !frame.physicalIdentity()
                || !frame.call.unchanged(event.getEntity(), event.getStrength(), event.getRatioX(), event.getRatioZ())) return event;
        try {
            ServerPlayer player = frame.player;
            if (!available(player) || !InertialAnchor.allowNativeReduction(player, frame.reduction, strength, x, z)) return event;
            float reduced = InertialAnchor.reductionStrength(frame.reduction);
            if (!Float.isFinite(strength) || strength <= 0 || !Float.isFinite(reduced) || reduced <= 0 || reduced >= strength
                    || Float.floatToRawIntBits(reduced) != Float.floatToRawIntBits(strength / 2F)) return event;
            double squared = x * x + z * z;
            if (!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(squared) || squared < 1.0E-5F) return event;
            frame.resistance = player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            frame.before = player.getDeltaMovement();
            if (!Double.isFinite(frame.resistance) || frame.resistance < 0 || frame.resistance >= 1 || !finite(frame.before)) return event;
            double effective = (double)reduced * (1 - frame.resistance);
            double ordinary = (double)strength * (1 - frame.resistance);
            if (!Double.isFinite(effective) || effective <= 0 || !Double.isFinite(ordinary) || ordinary <= effective) return event;
            Vec3 ratios = new Vec3(x, 0, z).normalize();
            frame.expected = nativeResult(frame.before, ratios, effective, frame.grounded);
            Vec3 unchanged = nativeResult(frame.before, ratios, ordinary, frame.grounded);
            if (!finite(frame.expected) || !finite(unchanged) || frame.expected.equals(unchanged)
                    || !frame.physicalIdentity() || !frame.before.equals(player.getDeltaMovement())
                    || frame.resistance != player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)
                    || event.isCanceled() || !frame.call.unchanged(event.getEntity(), event.getStrength(), event.getRatioX(), event.getRatioZ())) return event;
            // This controller boundary must publish payment without invoking mutable observers.
            // A false result means no debit; a true result is final and is never refunded or retried.
            if (!InertialAnchor.commitNativeReduction(player, frame.reduction)) return event;
            event.setStrength(reduced);
            frame.applied = true;
        } catch (RuntimeException | LinkageError failure) { disable(failure); }
        return event;
    }

    private static Vec3 nativeResult(Vec3 before, Vec3 ratios, double strength, boolean grounded) {
        Vec3 impulse = ratios.scale(strength);
        return new Vec3(before.x / 2 - impulse.x, grounded ? Math.min(.4D, before.y / 2 + strength) : before.y,
                before.z / 2 - impulse.z);
    }
    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
    private static void disable(Throwable failure) {
        failed = true;
        LOG.warn("NEW422 native reduction unavailable after callback failure; ordinary knockback and NEW412 remain unchanged", failure);
    }
}
