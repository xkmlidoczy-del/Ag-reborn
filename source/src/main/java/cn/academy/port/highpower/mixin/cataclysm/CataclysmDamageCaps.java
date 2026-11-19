package cn.academy.port.highpower.mixin.cataclysm;

import cn.academy.port.highpower.CapBreak;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Only the two numerical caps in the reviewed Cataclysm boss hurt methods. */
@Pseudo
@Mixin(targets = {
        "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.LLibrary_Boss_Monster",
        "com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.IABoss_monster"
}, remap = false)
abstract class CataclysmDamageCaps {
    @WrapOperation(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At(value = "INVOKE",
            target = "Ljava/lang/Math;min(FF)F", ordinal = 0, remap = false), require = 1, allow = 1)
    private float academy$perHitCap(float cap, float amount, Operation<Float> original, DamageSource source, float requested) {
        return CapBreak.bypassKnownCap((Entity)(Object)this, source) ? amount : original.call(cap, amount);
    }

    // The second is(TagKey) call tests Cataclysm's BYPASSES_HURT_TIME, not either vanilla immunity or cooldown.
    @WrapOperation(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z", ordinal = 1,
            remap = false), require = 1, allow = 1)
    private boolean academy$dpsCap(DamageSource source, TagKey<DamageType> tag, Operation<Boolean> original) {
        return CapBreak.bypassKnownCap((Entity)(Object)this, source) || original.call(source, tag);
    }
}
