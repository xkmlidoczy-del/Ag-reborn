package cn.academy.port.highpower;

import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
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
    public static final TagKey<DamageType> ABILITY_ATTACK = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("academy", "ability_attack"));
    private static Predicate<ServerPlayer> wristShield = player -> false;

    private static final StackWalker STACK = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private record Contact(long tick, StackTraceElement caller) {}
    private static final java.util.Map<ServerPlayer,Contact> BROKEN = new java.util.WeakHashMap<>();
    private PlayerShieldArmor() {}

    /** Preserve super armor for the attack that exhausted the last shield, including its follow-up impulse. */
    public static void finishContact(net.minecraft.world.entity.LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer player) || !HighPowerCombat.armoredOnContact(player)
                || holding(player) || !isAttack(source)) return;
        var frame = cn.academy.port.api.ControlRelations.attackingMethod(source.getEntity());
        if (frame == null) frame = cn.academy.port.api.ControlRelations.attackingMethod(source.getDirectEntity());
        if (frame == null && source.is(DamageTypeTags.IS_EXPLOSION))
            frame = STACK.walk(frames -> frames.filter(f -> f.getDeclaringClass() == net.minecraft.world.level.Explosion.class
                    && f.getMethodName().equals("explode")).findFirst().map(StackWalker.StackFrame::toStackTraceElement).orElse(null));
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

    public static boolean ignoresControl(Entity entity) {
        return entity instanceof ServerPlayer player && player.server != null && player.server.isSameThread()
                && (holding(player) || contacting(player));
    }

    public static boolean rejectsEffect(LivingEntity entity, MobEffectInstance effect) {
        return effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL && ignoresControl(entity);
    }

    public static void registerWristShield(Predicate<ServerPlayer> shield) {
        wristShield = shield;
    }

    public static boolean holding(Entity entity) {
        if (!(entity instanceof ServerPlayer player) || player.server == null || !player.server.isSameThread() || !player.isAlive()) return false;
        return wristShield.test(player) || HighPowerCombat.hasArmorShield(player);
    }

    private static boolean isAttack(DamageSource source) {
        return source.is(ABILITY_ATTACK) || CapBreak.originOnly(source) || source.getEntity() != null || source.getDirectEntity() != null
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
        if (ignoresControl(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void controlEffect(MobEffectEvent.Applicable event) {
        if (rejectsEffect(event.getEntity(), event.getEffectInstance()))
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void explosion(ExplosionKnockbackEvent event) {
        if (ignoresControl(event.getAffectedEntity())) event.setKnockbackVelocity(Vec3.ZERO);
    }

    @SubscribeEvent
    public static void clearExisting(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !holding(player)) return;
        if (player.getTicksFrozen() > 0) player.setTicksFrozen(0);
        var effects = player.getActiveEffects();
        if (!effects.isEmpty())
            java.util.List.copyOf(effects).stream().filter(effect -> rejectsEffect(player, effect))
                    .forEach(effect -> player.removeEffect(effect.getEffect()));
    }
}
