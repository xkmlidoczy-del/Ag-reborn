package cn.academy.port.highpower.mixin;

import cn.academy.port.highpower.ScatterPlateArrowReceipts;
import cn.academy.port.highpower.ShortInterceptionArrowBridge;
import cn.academy.port.highpower.TrajectoryReturnArrowBridge;
import cn.academy.port.highpower.MultiAxisReflectionArrowBridge;
import cn.academy.port.highpower.DissipationCurtainArrowReceipts;
import cn.academy.port.highpower.ZeroGapArmorArrowReceipts;
import cn.academy.port.highpower.AutonomousVectorArrowReceipts;
import cn.academy.port.highpower.DisplacedNeedleEntity;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Ordinary projectile operations preserve their exact returns. NEW311 alone fails closed on a throwing impact hook. */
@Mixin(AbstractArrow.class)
abstract class AbstractArrowScatterPlateMixin implements ShortInterceptionArrowBridge.MetadataAccess {
    @Shadow private int life;
    @Shadow protected int inGroundTime;
    @Override public int academy$interceptionLife() { return life; }
    @Override public int academy$interceptionInGroundTime() { return inGroundTime; }
    @WrapMethod(method = "tick()V")
    private void academy$observeNativeTick(Operation<Void> original) {
        var multiAxis = MultiAxisReflectionArrowBridge.beginTick((AbstractArrow)(Object)this);
        var trajectory = TrajectoryReturnArrowBridge.beginTick((AbstractArrow)(Object)this);
        var interception = ShortInterceptionArrowBridge.beginTick((AbstractArrow)(Object)this);
        var frame = ScatterPlateArrowReceipts.beginTick((AbstractArrow)(Object)this);
        var curtain = DissipationCurtainArrowReceipts.beginTick((AbstractArrow)(Object)this);
        var armor = ZeroGapArmorArrowReceipts.beginTick((AbstractArrow)(Object)this);
        var circle = AutonomousVectorArrowReceipts.beginTick((AbstractArrow)(Object)this);
        boolean completed = false;
        try { original.call(); completed = true; } finally { AutonomousVectorArrowReceipts.endTick(circle); MultiAxisReflectionArrowBridge.endTick(multiAxis, completed); TrajectoryReturnArrowBridge.endTick(trajectory); ShortInterceptionArrowBridge.endTick(interception); ZeroGapArmorArrowReceipts.endTick(armor); DissipationCurtainArrowReceipts.endTick(curtain); ScatterPlateArrowReceipts.endTick(frame); }
    }
    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/AbstractArrow;findHitEntity(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/EntityHitResult;"), require = 1, expect = 1, allow = 1)
    private EntityHitResult academy$observeClippedSegment(AbstractArrow arrow, Vec3 start, Vec3 end, Operation<EntityHitResult> original) {
        EntityHitResult result = original.call(arrow, start, end);
        MultiAxisReflectionArrowBridge.observe(arrow, start, end, result);
        TrajectoryReturnArrowBridge.observe(arrow, start, end, result);
        ShortInterceptionArrowBridge.observe(arrow, start, end, result);
        ScatterPlateArrowReceipts.observe(arrow, start, end, result);
        DissipationCurtainArrowReceipts.observe(arrow, start, end, result);
        ZeroGapArmorArrowReceipts.observe(arrow, start, end, result);
        AutonomousVectorArrowReceipts.observe(arrow, start, end, result);
        return result;
    }
    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/EventHooks;onProjectileImpact(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/world/phys/HitResult;)Z"), require = 1, expect = 1, allow = 1)
    private boolean academy$consumeBeforeImpactCallbacks(Projectile projectile, HitResult hit, Operation<Boolean> original) {
        AbstractArrow arrow = (AbstractArrow)projectile;
        DisplacedNeedleEntity needle = projectile instanceof DisplacedNeedleEntity medium ? medium : null;
        var needleImpact = needle == null ? null : needle.beforeNativeImpact(hit);
        boolean canceled = true;
        boolean completed = false;
        try {
            MultiAxisReflectionArrowBridge.beforeImpact(arrow, hit);
            TrajectoryReturnArrowBridge.beforeImpact(arrow, hit);
            ShortInterceptionArrowBridge.beforeImpact(arrow, hit);
            ScatterPlateArrowReceipts.beforeImpact(arrow, hit);
            DissipationCurtainArrowReceipts.beforeImpact(arrow, hit);
            ZeroGapArmorArrowReceipts.beforeImpact(arrow, hit);
            AutonomousVectorArrowReceipts.beforeImpact(arrow, hit);
            try { canceled = original.call(projectile, hit); return canceled; }
            catch (RuntimeException failure) {
                if (needle == null) throw failure;
                // A foreign/test callback already failed before contact. Keep this one
                // physical medium, retire its permission, and do not crash the game tick.
                // The event bus logs the original exception; ordinary arrows still throw.
                canceled = true;
                return true;
            }
            finally {
                AutonomousVectorArrowReceipts.afterImpactEvent(arrow, hit, canceled);
                MultiAxisReflectionArrowBridge.afterImpactEvent(arrow, hit, canceled);
                TrajectoryReturnArrowBridge.afterImpactEvent(arrow, hit, canceled);
                ShortInterceptionArrowBridge.afterImpactEvent(arrow, hit, canceled);
                ZeroGapArmorArrowReceipts.afterImpactEvent(arrow, hit, canceled);
                DissipationCurtainArrowReceipts.afterImpactEvent(arrow, hit, canceled);
                ScatterPlateArrowReceipts.afterImpactEvent(arrow, hit, canceled);
                completed = true;
            }
        } finally {
            if (needle != null) needle.afterNativeImpact(needleImpact, !completed || canceled);
        }
    }
    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/AbstractArrow;hitTargetOrDeflectSelf(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/entity/projectile/ProjectileDeflection;"), require = 1, expect = 1, allow = 1)
    private ProjectileDeflection academy$nativeImpactWindow(AbstractArrow arrow, HitResult hit, Operation<ProjectileDeflection> original) {
        // NEW442 replaces only an accepted native pending body impact. Returning the marker
        // breaks the native impact loop; never invoke REVERSE.deflect (it halves speed/changes yaw).
        if (MultiAxisReflectionArrowBridge.reflect(arrow, hit)) return ProjectileDeflection.REVERSE;
        var trajectory = TrajectoryReturnArrowBridge.beginImpact(arrow, hit);
        var interception = ShortInterceptionArrowBridge.beginImpact(arrow, hit);
        var frame = ScatterPlateArrowReceipts.beginImpact(arrow, hit);
        var curtain = DissipationCurtainArrowReceipts.beginImpact(arrow, hit);
        var armor = ZeroGapArmorArrowReceipts.beginImpact(arrow, hit);
        var circle = AutonomousVectorArrowReceipts.beginImpact(arrow, hit);
        try { return original.call(arrow, hit); } finally { AutonomousVectorArrowReceipts.endImpact(circle); TrajectoryReturnArrowBridge.endImpact(trajectory); ShortInterceptionArrowBridge.endImpact(interception); ZeroGapArmorArrowReceipts.endImpact(armor); DissipationCurtainArrowReceipts.endImpact(curtain); ScatterPlateArrowReceipts.endImpact(frame); }
    }
    @WrapOperation(method = "onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), require = 1, expect = 1, allow = 1)
    private boolean academy$nativeBodyHurtWindow(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
        var window = ScatterPlateArrowReceipts.beginHurt((AbstractArrow)(Object)this, target, source, amount);
        var curtain = DissipationCurtainArrowReceipts.beginHurt((AbstractArrow)(Object)this, target, source, amount);
        var armor = ZeroGapArmorArrowReceipts.beginHurt((AbstractArrow)(Object)this, target, source, amount);
        var circle = AutonomousVectorArrowReceipts.beginHurt((AbstractArrow)(Object)this, target, source, amount);
        try {
            AbstractArrow arrow = (AbstractArrow)(Object)this;
            return TrajectoryReturnArrowBridge.leased(arrow)
                    ? TrajectoryReturnArrowBridge.nativeHurt(arrow, target, source, amount, original)
                    : ShortInterceptionArrowBridge.nativeHurt(arrow, target, source, amount, original);
        } finally { AutonomousVectorArrowReceipts.endHurt(circle); ZeroGapArmorArrowReceipts.endHurt(armor); DissipationCurtainArrowReceipts.endHurt(curtain); ScatterPlateArrowReceipts.endHurt(window); }
    }
}
