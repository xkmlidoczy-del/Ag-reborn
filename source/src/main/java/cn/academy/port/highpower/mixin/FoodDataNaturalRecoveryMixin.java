package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.HighPowerNaturalRecovery;
import cn.academy.port.highpower.NativeFoodRecoveryAccess;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Branches/timer/hunger remain vanilla. Only reviewed natural-heal provenance and exact missing-HP are adapted. */
@Mixin(FoodData.class)
abstract class FoodDataNaturalRecoveryMixin implements NativeFoodRecoveryAccess {
    @Shadow private int tickTimer;
    @Override public int academy$naturalRecoveryTimer() { return tickTimer; }

    @WrapMethod(method = "tick(Lnet/minecraft/world/entity/player/Player;)V")
    private void academy$foodTick(Player player, Operation<Void> original) {
        HighPowerNaturalRecovery.foodTick((FoodData)(Object)this, player, () -> original.call(player));
    }
    @WrapOperation(method = "tick(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isHurt()Z"), require = 2, expect = 2, allow = 2)
    private boolean academy$exactMissingHealth(Player player, Operation<Boolean> original) {
        return HighPowerNaturalRecovery.foodIsHurt((FoodData)(Object)this, player, original.call(player));
    }
    @WrapOperation(method = "tick(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"), require = 2, expect = 2, allow = 2)
    private void academy$naturalHeal(Player player, float amount, Operation<Void> original) {
        HighPowerNaturalRecovery.foodHeal((FoodData)(Object)this, player, amount, () -> original.call(player, amount));
    }
}
