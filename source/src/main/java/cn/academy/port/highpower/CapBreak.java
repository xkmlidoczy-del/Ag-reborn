package cn.academy.port.highpower;

import cn.academy.port.AcademyConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Adapts an observed per-hit cap before the original native damage commits. */
public final class CapBreak {
    private static final ThreadLocal<Hit> HIT = new ThreadLocal<>();
    private record Hit(LivingEntity target, DamageSource source, float amount) {}
    private CapBreak() {}

    private static boolean enabled() {
        try { return AcademyConfig.BREAK_DAMAGE_CAPS.get(); }
        catch (IllegalStateException configNotLoaded) { return true; }
    }

    public static boolean hurt(LivingEntity target, DamageSource source, float amount) {
        if (!Float.isFinite(amount) || amount <= 0) return false;
        if (target instanceof Player || target.level().isClientSide || HighPowerCombat.enabled(target) || !enabled())
            return target.hurt(source, amount);
        var parent = HIT.get();
        HIT.set(new Hit(target, source, amount));
        try {
            return target.hurt(source, amount);
        } finally {
            if (parent == null) HIT.remove();
            else HIT.set(parent);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void incoming(LivingIncomingDamageEvent event) {
        var hit = HIT.get();
        if (hit == null || hit.target != event.getEntity() || hit.source != event.getSource()) return;
        HIT.remove();
        float nativeAmount = event.getContainer().getOriginalDamage();
        if (nativeAmount > 0 && nativeAmount < hit.amount - Math.max(1e-3F, hit.amount * 1e-4F)
                && event.getAmount() == nativeAmount)
            event.setAmount(hit.amount);
    }

    /** What the vanilla pipeline removes from {@code amount} on this target for this source. */
    public static float vanillaExpected(LivingEntity target, DamageSource source, float amount) {
        float damage = amount;
        if (!source.is(DamageTypeTags.BYPASSES_ARMOR))
            damage = CombatRules.getDamageAfterAbsorb(target, damage, source, (float) target.getArmorValue(), (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        if (!source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
            if (!source.is(DamageTypeTags.BYPASSES_RESISTANCE) && target.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
                int resistance = (target.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1) * 5;
                damage = Math.max(damage * (25 - resistance) / 25F, 0F);
            }
            if (damage > 0 && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) && target.level() instanceof ServerLevel level)
                damage = CombatRules.getDamageAfterMagicAbsorb(damage, EnchantmentHelper.getDamageProtection(level, target, source));
        }
        return Math.max(damage, 0F);
    }

}
