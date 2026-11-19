package cn.academy.port.highpower;

import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/** Super armor follows the live shield balance, including DNA and temporary defenses. */
@EventBusSubscriber(modid = "academy")
public final class PlayerShieldArmor {
    private static Predicate<ServerPlayer> wristShield = player -> false;

    private PlayerShieldArmor() {}

    public static void registerWristShield(Predicate<ServerPlayer> shield) {
        wristShield = shield;
    }

    public static boolean holding(Entity entity) {
        if (!(entity instanceof ServerPlayer player) || !player.isAlive() || !player.server.isSameThread()) return false;
        return wristShield.test(player) || HighPowerCombat.hasArmorShield(player);
    }

    private static boolean isAttack(DamageSource source) {
        return source.getEntity() != null || source.getDirectEntity() != null
                || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_PROJECTILE);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingIncomingDamageEvent event) {
        if (!event.isCanceled() && !isAttack(event.getSource()) && holding(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void knockback(LivingKnockBackEvent event) {
        if (HighPowerCombat.armoredOnContact(event.getEntity()) || holding(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void controlEffect(MobEffectEvent.Applicable event) {
        var effect = event.getEffectInstance().getEffect();
        if ((effect.equals(MobEffects.MOVEMENT_SLOWDOWN) || effect.equals(MobEffects.LEVITATION))
                && holding(event.getEntity())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }
}
