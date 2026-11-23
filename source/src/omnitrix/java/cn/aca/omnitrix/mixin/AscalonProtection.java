package cn.aca.omnitrix.mixin;

import cn.aca.omnitrix.AscalonService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class AscalonProtection {
    @Shadow public abstract float getMaxHealth();

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void omnitrix$ascalonHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player p && AscalonService.isProtected(p)) {
            AscalonService.onInterceptedHurt(p, source, amount);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "actuallyHurt", at = @At("HEAD"), cancellable = true)
    private void omnitrix$ascalonActuallyHurt(DamageSource source, float amount, CallbackInfo ci) {
        if ((Object) this instanceof Player p && AscalonService.isProtected(p)) {
            ci.cancel();
        }
    }

    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void omnitrix$ascalonInvuln(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player p && AscalonService.isProtected(p)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getHealth", at = @At("RETURN"), cancellable = true)
    private void omnitrix$ascalonGetHealth(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof Player p && AscalonService.isProtected(p) && !cn.aca.omnitrix.AcademyInterop.nativeCapacity(p)) {
            float max = getMaxHealth();
            if (max > 0f && (Float.isNaN(cir.getReturnValueF()) || cir.getReturnValueF() < max)) {
                cir.setReturnValue(max);
            }
        }
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void omnitrix$ascalonHealthLock(float health, CallbackInfo ci) {
        if ((Object) this instanceof Player p && AscalonService.isProtected(p)) {
            float max = getMaxHealth();
            if (cn.aca.omnitrix.AcademyInterop.adjustingCapacity(p)) return;
            float minimum = cn.aca.omnitrix.AcademyInterop.nativeCapacity(p) ? p.getHealth() : max;
            if (minimum > 0f && (Float.isNaN(health) || health < minimum)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "isDeadOrDying", at = @At("HEAD"), cancellable = true)
    private void omnitrix$ascalonNeverDeadOrDying(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player p && AscalonService.isProtected(p)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isAlive", at = @At("HEAD"), cancellable = true)
    private void omnitrix$ascalonAlwaysAlive(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player p && !p.isRemoved() && AscalonService.isProtected(p)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void omnitrix$ascalonNeverDie(DamageSource source, CallbackInfo ci) {
        if ((Object) this instanceof Player p && AscalonService.isProtected(p)) {
            p.deathTime = 0;
            p.hurtTime = 0;
            ci.cancel();
        }
    }

    @Inject(method = "kill", at = @At("HEAD"), cancellable = true)
    private void omnitrix$ascalonLivingKill(CallbackInfo ci) {
        if ((Object) this instanceof Player p && AscalonService.isProtected(p)) {
            ci.cancel();
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void omnitrix$ascalonLivingRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer sp
                && (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED)
                && !sp.hasDisconnected() && !sp.wonGame
                && AscalonService.blockForcedRemoval(sp)) {
            ci.cancel();
        }
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void omnitrix$ascalonPurifyEffects(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player p && AscalonService.isProtected(p)
                && effect != null && !effect.getEffect().value().isBeneficial()) {
            cir.setReturnValue(false);
        }
    }
}
