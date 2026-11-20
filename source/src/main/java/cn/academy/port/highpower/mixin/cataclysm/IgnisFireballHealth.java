package cn.academy.port.highpower.mixin.cataclysm;

import cn.academy.port.highpower.IgnisDamage;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = {"com.github.L_Ender.cataclysm.entity.projectile.Ignis_Fireball_Entity",
        "com.github.L_Ender.cataclysm.entity.projectile.Ignis_Abyss_Fireball_Entity"}, remap = false)
abstract class IgnisFireballHealth {
    @WrapOperation(method = {"onHitEntity"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getMaxHealth()F"), require = 1, allow = 1)
    private float academy$attackHealth(LivingEntity victim, Operation<Float> original) {
        return IgnisDamage.percentageBasis(victim, original.call(victim), ((Projectile)(Object)this).getOwner());
    }
}
