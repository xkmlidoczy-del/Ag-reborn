package cn.academy.port.mixin.combat;

import cn.academy.port.api.CombatTargeting;

import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class BodyDimensionsMixin {
    @Inject(method = "refreshDimensions", at = @At("RETURN"))
    private void academy$refreshBody(CallbackInfo ci) {
        Entity body = (Entity) (Object) this;
        if (body.isAddedToLevel()) CombatTargeting.resize(body);
    }
}
