package cn.academy.port.highpower;

import cn.academy.port.AcademyConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import cn.academy.port.city.CityNpcEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import java.util.function.BooleanSupplier;

/** Adapts an observed per-hit cap before the original native damage commits. */
public final class CapBreak {
    private static final ThreadLocal<Hit> HIT = new ThreadLocal<>();
    private static final class Hit {
        private final LivingEntity target;
        private final DamageSource source;
        private final float amount;
        private boolean entered;

        private Hit(LivingEntity target, DamageSource source, float amount) {
            this.target = target;
            this.source = source;
            this.amount = amount;
        }
    }

    private CapBreak() {}

    private static boolean enabled() {
        try { return AcademyConfig.BREAK_DAMAGE_CAPS.get(); }
        catch (IllegalStateException configNotLoaded) { return true; }
    }

    public static boolean hurt(LivingEntity target, DamageSource source, float amount) {
        if (!Float.isFinite(amount) || amount <= 0) return false;
        float requested = Math.min(amount, (float) CombatAttributes.LIMIT);
        return aroundHurt(target, source, requested, () -> target.hurt(source, requested));
    }

    /** Also wraps the original invocation inside Player.attack to retain its modifiers and attack bookkeeping. */
    public static boolean aroundHurt(Entity target, DamageSource source, float amount, BooleanSupplier original) {
        if (!(target instanceof LivingEntity living) || target instanceof Player || target.level().isClientSide
                || target.getServer() == null || !target.getServer().isSameThread() || !Float.isFinite(amount) || amount <= 0 || !enabled()) {
            return original.getAsBoolean();
        }
        Hit parent = HIT.get();
        HIT.set(new Hit(living, source, amount));
        try {
            return original.getAsBoolean();
        } finally {
            if (parent == null) HIT.remove();
            else HIT.set(parent);
        }
    }

    private static Hit pending(Entity target, DamageSource source) {
        Hit hit = HIT.get();
        return hit != null && !hit.entered && hit.target == target && hit.source == source ? hit : null;
    }

    /** Checked at Cataclysm's two specific cap instructions while its own hurt method is still running. */
    public static boolean bypassKnownCap(Entity target, DamageSource source) {
        return pending(target, source) != null;
    }

    /** Before the native DamageContainer is created so every observer sees the same starting damage. */
    public static float atNativeEntry(LivingEntity target, DamageSource source, float amount) {
        Hit hit = pending(target, source);
        if (hit == null) return amount;
        hit.entered = true;
        // The dedicated Cataclysm adapter preserves its distance multiplier. City NPCs can spend a
        // legacy local shield before entering this method, and that debit is already authoritative.
        if (target instanceof CityNpcEntity || BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getNamespace().equals("cataclysm")) {
            return amount;
        }
        float tolerance = Math.max(1e-3F, hit.amount * 1e-4F);
        return amount > 0 && amount < hit.amount - tolerance ? hit.amount : amount;
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
