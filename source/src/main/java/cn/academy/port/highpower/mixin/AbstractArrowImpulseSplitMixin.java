package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.ImpulseSplitArrowBudget;
import cn.academy.port.highpower.NativeImpulseSplitArrowAccess;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Separate NEW453 adapter: default native arrows are unchanged; split objects alone carry a finite integer cap. */
@Mixin(AbstractArrow.class)
abstract class AbstractArrowImpulseSplitMixin implements NativeImpulseSplitArrowAccess {
    @WrapMethod(method="onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V")
    private void academy$impulseSplitNativeHit(EntityHitResult hit,Operation<Void> original){
        var frame=ImpulseSplitArrowBudget.beginNativeHit((AbstractArrow)(Object)this);
        try{original.call(hit);}finally{ImpulseSplitArrowBudget.endNativeHit(frame);}
    }
    @ModifyExpressionValue(method="onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V",
            at=@At(value="FIELD",target="Lnet/minecraft/world/entity/projectile/AbstractArrow;baseDamage:D"),require=1,expect=1,allow=1)
    private double academy$impulseSplitBase(double ordinary){return ImpulseSplitArrowBudget.nativeBaseDamage((AbstractArrow)(Object)this,ordinary);}
    @WrapOperation(method="onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),require=1,expect=1,allow=1)
    private boolean academy$impulseSplitHurt(Entity target,DamageSource source,float amount,Operation<Boolean> original){
        float bounded=ImpulseSplitArrowBudget.nativeHurtAmount((AbstractArrow)(Object)this,target,source,amount);
        return original.call(target,source,bounded);
    }
}
