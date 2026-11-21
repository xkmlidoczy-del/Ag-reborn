package cn.academy.port.mixin.combat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingRescueMixin {
    @org.spongepowered.asm.mixin.gen.Accessor("dead")
    boolean academy$deathStarted();

    @Invoker("checkTotemDeathProtection")
    boolean academy$tryTotem(DamageSource source);
}
