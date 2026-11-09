package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.NativeScatterArrowOwnerAccess;
import cn.academy.port.highpower.ScatterPlateArrowReceipts;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
abstract class ProjectileScatterPlateMixin implements NativeScatterArrowOwnerAccess {
    @Shadow private UUID ownerUUID;
    @Shadow private Entity cachedOwner;
    @Shadow private boolean leftOwner;
    public Entity academy$scatterCachedOwner() { return cachedOwner; }
    public UUID academy$scatterOwnerUuid() { return ownerUUID; }
    public boolean academy$scatterLeftOwner() { return leftOwner; }
    @Inject(method = "setOwner(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void academy$clearOnOwnerWrite(Entity owner, CallbackInfo ci) { cn.academy.port.highpower.AutonomousVectorArrowReceipts.ownerChanged((Entity)(Object)this); cn.academy.port.highpower.MultiAxisReflectionArrowBridge.ownerChanged((Entity)(Object)this); cn.academy.port.highpower.BrakingTouch.ownerChanged((Entity)(Object)this); cn.academy.port.highpower.ShortInterceptionArrowBridge.ownerChanged((Entity)(Object)this); cn.academy.port.highpower.TrajectoryReturnArrowBridge.ownerChanged((Entity)(Object)this); ScatterPlateArrowReceipts.ownerChanged((Entity)(Object)this); cn.academy.port.highpower.DissipationCurtainArrowReceipts.ownerChanged((Entity)(Object)this); cn.academy.port.highpower.ZeroGapArmorArrowReceipts.ownerChanged((Entity)(Object)this); }
    @Inject(method = "deflect(Lnet/minecraft/world/entity/projectile/ProjectileDeflection;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Z)Z", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void academy$clearOnDeflection(ProjectileDeflection deflection, Entity target, Entity owner, boolean fromAttack, CallbackInfoReturnable<Boolean> cir) { cn.academy.port.highpower.AutonomousVectorArrowReceipts.ownerChanged((Entity)(Object)this); cn.academy.port.highpower.MultiAxisReflectionArrowBridge.ownerChanged((Entity)(Object)this); cn.academy.port.highpower.BrakingTouch.ownerChanged((Entity)(Object)this); cn.academy.port.highpower.ShortInterceptionArrowBridge.ownerChanged((Entity)(Object)this); cn.academy.port.highpower.TrajectoryReturnArrowBridge.ownerChanged((Entity)(Object)this); ScatterPlateArrowReceipts.ownerChanged((Entity)(Object)this); cn.academy.port.highpower.DissipationCurtainArrowReceipts.ownerChanged((Entity)(Object)this); cn.academy.port.highpower.ZeroGapArmorArrowReceipts.ownerChanged((Entity)(Object)this); }
}
