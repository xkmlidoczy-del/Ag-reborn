package cn.academy.port.highpower.mixin.cataclysm;

import cn.academy.port.highpower.IgnisDamage;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = {"com.github.L_Ender.cataclysm.entity.effect.Flame_Strike_Entity"}, remap = false)
abstract class IgnisFlameHealth {
    @Shadow public abstract LivingEntity getOwner();

    @WrapOperation(method = {"damage"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getMaxHealth()F"), require = 2, allow = 2)
    private float academy$attackHealth(LivingEntity victim, Operation<Float> original) {
        return IgnisDamage.percentageBasis(victim, original.call(victim), getOwner());
    }
}
