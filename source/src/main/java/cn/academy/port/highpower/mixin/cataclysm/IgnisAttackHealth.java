package cn.academy.port.highpower.mixin.cataclysm;

import cn.academy.port.highpower.IgnisDamage;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = {"com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignis_Entity"}, remap = false)
abstract class IgnisAttackHealth {
    @WrapOperation(method = {"AreaAttack", "BodyCheckAttack", "Poke", "positionRider", "ShieldSmashDamage", "UltimateAttack", "Phase_Transition", "aiStep"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getMaxHealth()F"), require = 9, allow = 9)
    private float academy$attackHealth(LivingEntity victim, Operation<Float> original) {
        return IgnisDamage.percentageBasis(victim, original.call(victim), (LivingEntity)(Object)this);
    }
}
