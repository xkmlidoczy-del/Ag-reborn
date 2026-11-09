package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.ImpulseSplitArrowBudget;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
abstract class ProjectileImpulseSplitMixin {
    @Inject(method="setOwner(Lnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"),require=1,expect=1,allow=1)
    private void academy$impulseSplitOwner(Entity owner,CallbackInfo ci){if((Object)this instanceof AbstractArrow a)ImpulseSplitArrowBudget.ownerChanged(a);}
    @Inject(method="deflect(Lnet/minecraft/world/entity/projectile/ProjectileDeflection;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Z)Z",at=@At("HEAD"),require=1,expect=1,allow=1)
    private void academy$impulseSplitDeflect(ProjectileDeflection deflection,Entity entity,Entity owner,boolean deflectedByPlayer,CallbackInfoReturnable<Boolean> ci){if((Object)this instanceof AbstractArrow a)ImpulseSplitArrowBudget.ownerChanged(a);}
}
