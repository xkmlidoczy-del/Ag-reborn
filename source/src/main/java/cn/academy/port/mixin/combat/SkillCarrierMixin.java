package cn.academy.port.mixin.combat;

import cn.academy.port.highpower.CapBreak;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DamageSource.class)
abstract class SkillCarrierMixin {
    @Inject(method = {"getDirectEntity", "getEntity"}, at = @At("HEAD"), cancellable = true)
    private void academy$contactlessOrigin(CallbackInfoReturnable<Entity> cir) {
        if (CapBreak.originOnly((DamageSource) (Object) this)) cir.setReturnValue(null);
    }
}
