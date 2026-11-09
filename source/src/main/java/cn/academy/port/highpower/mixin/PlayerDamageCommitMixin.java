package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.NativeCooldownAccess;
import cn.academy.port.highpower.AnchorDamagePostProof;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player's independent actuallyHurt override needs the same post-modifier, pre-debit veto. */
@Mixin(Player.class)
abstract class PlayerDamageCommitMixin implements NativeCooldownAccess.PlayerDamageCommit {
    @WrapOperation(method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/CommonHooks;onLivingDamagePost(Lnet/minecraft/world/entity/LivingEntity;Lnet/neoforged/neoforge/common/damagesource/DamageContainer;)V"),
            require = 1, expect = 1, allow = 1)
    private void anchor$nativePost(LivingEntity entity, DamageContainer container, Operation<Void> original) {
        AnchorDamagePostProof.nativePost(entity, container, original);
    }

    @Inject(method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/damagesource/DamageContainer;setReduction(Lnet/neoforged/neoforge/common/damagesource/DamageContainer$Reduction;F)V",
                    ordinal = 1, shift = At.Shift.AFTER), cancellable = true, require = 1, expect = 1, allow = 1)
    private void academy$vetoClosedDamageCommit(DamageSource source, float amount, CallbackInfo callback) {
        if (((NativeCooldownAccess)(Object)this).academy$vetoClosedDamageCommit(source)) callback.cancel();
    }
}
