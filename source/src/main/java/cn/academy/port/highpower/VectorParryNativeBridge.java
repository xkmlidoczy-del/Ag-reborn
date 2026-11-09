package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeVectorParryMixinPlugin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

/** NEW412 changes only the admitted native event's horizontal ratios, after its listeners finish. */
public final class VectorParryNativeBridge {
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();
    private VectorParryNativeBridge() {}

    /** Precision alone is unavailable when the optional adapter was not admitted and installed. */
    public static boolean available(LivingEntity entity) {
        return entity instanceof NativeVectorParryAccess access && access.academy$vectorParryNativeSeam();
    }

    private static final class Frame {
        final Frame parent;
        final LivingEntity entity;
        final VectorParry.NativeTurn turn;
        final VectorParryNativeCall call;
        final Vec3 position;
        Vec3 before;
        boolean applied;
        Frame(Frame parent, LivingEntity entity, VectorParry.NativeTurn turn, double strength, double x, double z) {
            this.parent = parent;
            this.entity = entity;
            this.turn = turn;
            call = new VectorParryNativeCall(entity, strength, x, z);
            position = entity.position();
            before = entity.getDeltaMovement();
        }
    }

    /** Any hurt reentry, even one that returns before knockback, retires the pending outer turn. */
    public static boolean nativeHurt(LivingEntity entity, DamageSource source, float amount, Operation<Boolean> original) {
        Frame pending = ACTIVE.get();
        if (pending != null) pending.call.invalidate();
        InertialAnchorNativeBridge.hurtEntered();
        YawPalmNativeBridge.hurtEntered();
        VectorCageNativeBridge.hurtEntered();
        return original.call(source, amount);
    }

    public static void nativeKnockback(LivingEntity entity, double strength, double x, double z, Operation<Void> original) {
        VectorParry.NativeTurn turn = null;
        if (available(entity)) {
            try { turn = VectorParry.beginNativeTurn(entity, strength, x, z); }
            catch (RuntimeException | LinkageError failure) { NativeVectorParryMixinPlugin.disableAfterCallbackFailure(failure); }
        }
        if (turn == null) {
            VectorCageNativeBridge.nativeKnockback(entity, strength, x, z, original);
            return;
        }
        Frame frame = new Frame(ACTIVE.get(), entity, turn, strength, x, z);
        ACTIVE.set(frame);
        boolean completed = false;
        try {
            // Preserve the original arguments. Only the native hook's returned event can be changed.
            original.call(entity, strength, x, z);
            completed = true;
        } finally {
            // Restore/remove first: finish callbacks and thrown native listeners cannot leak a receipt.
            if (frame.parent == null) ACTIVE.remove(); else ACTIVE.set(frame.parent);
            try {
                VectorParry.finishNativeTurn(turn, entity, frame.before, entity.getDeltaMovement(),
                        frame.applied && frame.call.valid(), completed);
            } catch (RuntimeException | LinkageError failure) {
                NativeVectorParryMixinPlugin.disableAfterCallbackFailure(failure);
            }
        }
    }

    public static LivingKnockBackEvent nativeEvent(LivingEntity entity, float strength, double x, double z,
            Operation<LivingKnockBackEvent> original) {
        Frame frame = ACTIVE.get();
        if (frame == null) return VectorCageNativeBridge.nativeEvent(entity, strength, x, z, original);
        // Claim BEFORE dispatch: recursive knockback posted by a listener is an ordinary native call.
        boolean claimed = frame != null && frame.call.claim(entity, strength, x, z);
        LivingKnockBackEvent event = original.call(entity, strength, x, z);
        if (!claimed || event == null || event.isCanceled() || ACTIVE.get() != frame
                || !frame.call.unchanged(event.getEntity(), event.getStrength(), event.getRatioX(), event.getRatioZ())
                || !frame.position.equals(entity.position())) return event;
        try {
            if (!VectorParry.allowNativeTurn(frame.turn, entity, strength, x, z)) return event;
            Vec3 ratio = VectorParry.nativeTurnRatio(frame.turn);
            if (ratio == null || !Double.isFinite(ratio.x) || !Double.isFinite(ratio.z)
                    || ratio.x * ratio.x + ratio.z * ratio.z < 1.0E-5F) return event;
            frame.before = entity.getDeltaMovement();
            VectorParry.commitNativeTurn(frame.turn);
            event.setRatioX(ratio.x);
            event.setRatioZ(ratio.z);
            frame.applied = true;
        } catch (RuntimeException | LinkageError failure) {
            NativeVectorParryMixinPlugin.disableAfterCallbackFailure(failure);
        }
        return event;
    }
}
