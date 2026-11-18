package cn.aca.omnitrix.mixin;

import cn.aca.omnitrix.SurvivalForms;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractMinecart.class)
abstract class AssimilatedRail {
    @Inject(method = "moveMinecartOnRail", at = @At("HEAD"))
    private void omnitrix$accelerate(BlockPos pos, CallbackInfo callback) {
        SurvivalForms.accelerateCart((AbstractMinecart)(Object)this, pos);
    }

    @Inject(method = "getMaxSpeedWithRail", at = @At("RETURN"), cancellable = true)
    private void omnitrix$speedCap(CallbackInfoReturnable<Double> callback) {
        var cart = (AbstractMinecart)(Object)this;
        if (SurvivalForms.fastRail(cart, cart.getCurrentRailPosition())) callback.setReturnValue(1.2);
    }
}
