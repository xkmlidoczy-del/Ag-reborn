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

    private record Contact(long tick, StackTraceElement caller) {}
    private static final java.util.Map<ServerPlayer,Contact> BROKEN = new java.util.WeakHashMap<>();
    private PlayerShieldArmor() {}

    /** Preserve super armor for the attack that exhausted the last shield, including its follow-up impulse. */
    public static void finishContact(net.minecraft.world.entity.LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer player) || !HighPowerCombat.armoredOnContact(player)
                || holding(player) || !isAttack(source)) return;
        var frame = cn.academy.port.api.ControlRelations.attackingMethod(source.getEntity());
        if (frame == null) frame = cn.academy.port.api.ControlRelations.attackingMethod(source.getDirectEntity());
        if (frame != null) BROKEN.put(player,new Contact(player.level().getGameTime(),frame));
    }

    public static boolean contacting(ServerPlayer player) {
        if (HighPowerCombat.armoredOnContact(player)) return true;
        var contact = BROKEN.get(player);
        if (contact == null) return false;
        if (contact.tick == player.level().getGameTime() && cn.academy.port.api.ControlRelations.stillAttacking(contact.caller)) return true;
        BROKEN.remove(player);
        return false;
    }

    @SubscribeEvent public static void stop(net.neoforged.neoforge.event.server.ServerStoppedEvent event) { BROKEN.clear(); }

    private static boolean defended(net.minecraft.world.entity.LivingEntity entity) {
        return entity instanceof ServerPlayer player && (holding(player) || contacting(player));
    }

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
        if (event.getEntity() instanceof ServerPlayer player) BROKEN.remove(player);
        if (!event.isCanceled() && !isAttack(event.getSource()) && holding(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void knockback(LivingKnockBackEvent event) {
        if (defended(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void controlEffect(MobEffectEvent.Applicable event) {
        var effect = event.getEffectInstance().getEffect();
        if ((effect.equals(MobEffects.MOVEMENT_SLOWDOWN) || effect.equals(MobEffects.LEVITATION))
                && defended(event.getEntity())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }
}
