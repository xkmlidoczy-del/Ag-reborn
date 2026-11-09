package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.NativeCooldownAccess;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import cn.academy.port.highpower.AnchorDamagePostProof;
import java.util.Stack;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No timer/field write: comparison, transaction boundaries and the closed-source commit veto only. */
@Mixin(LivingEntity.class)
abstract class LivingEntityCooldownMixin implements NativeCooldownAccess {
    @Shadow protected float lastHurt;
    @Shadow protected Stack<DamageContainer> damageContainers;

    @WrapMethod(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z")
    private boolean academy$frameHurt(DamageSource source, float amount, Operation<Boolean> nativeHurt) {
        LivingEntity entity = (LivingEntity)(Object)this;
        HighPowerCombat.beginNativeHurt(entity, source, lastHurt);
        boolean completed = false;
        try {
            boolean accepted = nativeHurt.call(source, amount);
            completed = true;
            return accepted;
        } finally {
            HighPowerCombat.endNativeHurt(entity, source, lastHurt, completed);
        }
    }

    /** NEW422 only: distinguish the actual native Post object from subscriber-created events. */
    @WrapOperation(method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/CommonHooks;onLivingDamagePost(Lnet/minecraft/world/entity/LivingEntity;Lnet/neoforged/neoforge/common/damagesource/DamageContainer;)V"),
            require = 1, expect = 1, allow = 1)
    private void anchor$nativePost(LivingEntity entity, DamageContainer container, Operation<Void> original) {
        AnchorDamagePostProof.nativePost(entity, container, original);
    }

    @ModifyExpressionValue(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;lastHurt:F", opcode = Opcodes.GETFIELD, ordinal = 0), require = 1, expect = 1, allow = 1)
    private float academy$compareLogicalCooldown(float nativePrevious, DamageSource source, float current) {
        return HighPowerCombat.nativeCooldownComparison((LivingEntity)(Object)this, source, current, nativePrevious,
                damageContainers.isEmpty() ? null : damageContainers.peek());
    }

    @Inject(method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/damagesource/DamageContainer;setReduction(Lnet/neoforged/neoforge/common/damagesource/DamageContainer$Reduction;F)V",
                    ordinal = 1, shift = At.Shift.AFTER), cancellable = true, require = 1, expect = 1, allow = 1)
    private void academy$vetoClosedDamageCommit(DamageSource source, float amount, CallbackInfo callback) {
        if (academy$vetoClosedDamageCommit(source)) callback.cancel();
    }

    @Override
    public boolean academy$vetoClosedDamageCommit(DamageSource source) {
        return HighPowerCombat.vetoClosedDamageCommit((LivingEntity)(Object)this, source,
                damageContainers.isEmpty() ? null : damageContainers.peek());
    }
    @Override
    public boolean academy$yawPalmShieldBlocked(DamageSource source) {
        if (source == null || damageContainers.isEmpty()) return false;
        DamageContainer container = damageContainers.peek();
        float blocked = container.getBlockedDamage(), original = container.getOriginalDamage();
        return container.getSource() == source && Float.isFinite(original) && original > 0
                && Float.isFinite(blocked) && blocked > 0 && blocked <= original;
    }

}
