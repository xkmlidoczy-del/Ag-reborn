package cn.academy.port.highpower;

import cn.academy.port.AcademyConfig;
import java.util.Map;
import java.util.WeakHashMap;
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

/**
 * Skill damage must not be swallowed by another mod's damage limiter: a boss {@code hurt} override that clamps the amount to a
 * per-hit cap, drains it into a DPS bucket, or refuses hits from beyond a range limit before the vanilla pipeline ever runs.
 * <p>
 * Detection is exact, not a guess from missing health: {@link #arm} marks the target, the first
 * {@link LivingIncomingDamageEvent} inside the vanilla pipeline records {@code DamageContainer.getOriginalDamage()} (the amount
 * the override actually handed to vanilla), and {@link #afterHit} compares it with the skill amount. Everything vanilla or other
 * event handlers do after that point stays authoritative: armor, shields, hurt cooldown and its residual rule, absorption,
 * totems, death vetoes and handlers that reduce or cancel the hit. Only the clamped difference (or, for a refused hit, the whole
 * hit) is restored, scaled by the vanilla armor/resistance/enchantment result. A lethal restoration ends with an ordinary
 * hit so totems and death events still decide. Players and exact-ledger targets are never touched, nor are entities that are
 * immune by their own {@code isInvulnerableTo}.
 */
public final class CapBreak {
    private static final Map<LivingEntity, Float> ARMED = new WeakHashMap<>();
    private static final Float UNSEEN = -1F;
    private CapBreak() {}

    public static boolean enabled() {
        try { return AcademyConfig.BREAK_DAMAGE_CAPS.get(); }
        catch (IllegalStateException configNotLoaded) { return true; }
    }

    /** Call immediately before the skill's {@code hurt}. */
    public static void arm(LivingEntity target) {
        if (target != null && !target.level().isClientSide) ARMED.put(target, UNSEEN);
    }

    /** First vanilla stage of the armed hit: what the target's own override let through. */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void incoming(LivingIncomingDamageEvent event) {
        var entity = event.getEntity();
        Float seen = ARMED.get(entity);
        if (seen != null && seen < 0) ARMED.put(entity, event.getContainer().getOriginalDamage());
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

    /**
     * @param source the source the skill passed to {@code hurt}
     * @param amount the amount the skill passed to {@code hurt}
     * @param accepted the return value of {@code hurt}
     * @return true if a limiter was detected and broken
     */
    public static boolean afterHit(ServerPlayer player, String skill, LivingEntity target, DamageSource source, float amount, boolean accepted) {
        Float seen = target == null ? null : ARMED.remove(target);
        if (seen == null || !enabled() || player == null || target instanceof Player || target.level().isClientSide) return false;
        if (!target.isAlive() || target.isDeadOrDying() || HighPowerCombat.enabled(target) || !(amount > 0) || !Float.isFinite(amount)) return false;
        if (target.isInvulnerableTo(source) || source.is(DamageTypeTags.IS_FIRE) && target.hasEffect(MobEffects.FIRE_RESISTANCE)) return false;
        float lost;
        if (seen >= 0) {
            if (!(seen < amount - Math.max(1e-3F, amount * 1e-4F))) return false; // the override let the whole hit through
            lost = vanillaExpected(target, source, amount) - vanillaExpected(target, source, seen);
        } else {
            if (accepted) return false; // no vanilla stage yet accepted: not ours to judge
            lost = vanillaExpected(target, source, amount); // refused before vanilla ran (range limit, DPS lockout)
        }
        if (!(lost > 1e-3F)) return false;
        if (seen < 0) {
            // Refused before vanilla ran. A limiter (range cap, DPS lockout) lets invulnerability-bypassing damage through;
            // an entity that refuses that as well is genuinely invulnerable and is left alone.
            target.invulnerableTime = 0;
            return target.hurt(new FinishSource(player, skill, true), lost);
        }
        restore(player, skill, target, lost);
        return true;
    }

    private static void restore(ServerPlayer player, String skill, LivingEntity target, float lost) {
        float absorption = target.getAbsorptionAmount();
        float fromAbsorption = Math.min(absorption, lost);
        if (fromAbsorption > 0) target.setAbsorptionAmount(absorption - fromAbsorption);
        float rest = lost - fromAbsorption;
        if (rest <= 0) return;
        float health = target.getHealth();
        if (rest < health) { target.setHealth(health - rest); return; }
        // Lethal: leave a sliver and land one ordinary hit, so totems, death vetoes and drops follow vanilla.
        target.setHealth(Math.min(health, 1e-3F));
        target.invulnerableTime = 0;
        target.hurt(new FinishSource(player, skill, false), 1000F);
        if (target.isAlive() && !target.isDeadOrDying() && target.getHealth() <= 1e-3F) {
            // The limiter refused the finishing hit as well; bosses route invulnerability-bypassing damage past it.
            target.invulnerableTime = 0;
            target.hurt(new FinishSource(player, skill, true), 1000F);
            // It took the clamped part of the hit, so it is damageable: settle the death the way vanilla does.
            if (target.isAlive() && !target.isDeadOrDying() && target.getHealth() <= 1e-3F) { target.setHealth(0F); target.die(new FinishSource(player, skill, true)); }
        }
    }

    /** Finishing hit: hurt cooldown and mitigation are already accounted for; optionally passes boss limiters. */
    public static final class FinishSource extends DamageSource {
        public final String skill;
        private final boolean passLimiter;
        public FinishSource(ServerPlayer player, String skill, boolean passLimiter) {
            super(player.damageSources().playerAttack(player).typeHolder(), player); this.skill = skill; this.passLimiter = passLimiter;
        }
        @Override public boolean is(TagKey<DamageType> tag) {
            return passLimiter && tag.equals(DamageTypeTags.BYPASSES_INVULNERABILITY) || tag.equals(DamageTypeTags.BYPASSES_COOLDOWN)
                    || tag.equals(DamageTypeTags.BYPASSES_ARMOR) || tag.equals(DamageTypeTags.BYPASSES_EFFECTS) || tag.equals(DamageTypeTags.BYPASSES_RESISTANCE)
                    || tag.equals(DamageTypeTags.BYPASSES_ENCHANTMENTS) || super.is(tag);
        }
    }
}
