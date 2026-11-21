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
import cn.academy.port.mixin.combat.LivingRescueMixin;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.minecraft.world.entity.Entity;
import java.util.function.BooleanSupplier;

/** Adapts an observed per-hit cap before the original native damage commits. */
public final class CapBreak {
    private static final ThreadLocal<Hit> HIT = new ThreadLocal<>();
    private static volatile SynchedEntityData rescuingData, settlingData;
    private static final class Hit {
        private final LivingEntity target;
        private final DamageSource source;
        private final float amount;
        private final cn.academy.port.api.CombatDefense defense;
        private final DamageShape.Shape shape;
        private final boolean breakCaps;
        private final float before;
        private boolean entered, committed, rescuing;
        private float nativeInput, finalDamage, projected;
        private DamageContainer transaction;
        private boolean posted, ambiguous, canceled;

        private Hit(LivingEntity target, DamageSource source, float amount, cn.academy.port.api.CombatDefense defense, boolean breakCaps) {
            this.target = target;
            this.source = source;
            this.amount = amount;
            this.defense = defense;
            this.breakCaps = breakCaps;
            this.shape = defense == null ? DamageShape.of(target) : DamageShape.UNKNOWN;
            this.before = shape.vitality() != null ? target.getHealth() : 0F;
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
        if (!(target instanceof LivingEntity living) || target.level().isClientSide
                || target.getServer() == null || !target.getServer().isSameThread() || !Float.isFinite(amount) || amount <= 0) {
            return original.getAsBoolean();
        }
        boolean breakCaps = enabled();
        var defense = living.getCapability(cn.academy.port.api.CombatDefense.CAPABILITY);
        if (defense == null && (!breakCaps || living instanceof Player)) return original.getAsBoolean();
        Hit parent = HIT.get();
        Hit hit = new Hit(living, source, amount, defense, breakCaps);
        var parentData = settlingData;
        var data = hit.shape.vitality() == null || hit.defense != null ? null : living.getEntityData();
        if (data != parentData) settlingData = data;
        HIT.set(hit);
        try {
            return original.getAsBoolean();
        } finally {
            if (data != parentData) settlingData = parentData;
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
        Hit hit = pending(target,source);
        return hit != null && hit.breakCaps;
    }

    /** Before the native DamageContainer is created so every observer sees the same starting damage. */
    public static float atNativeEntry(LivingEntity target, DamageSource source, float amount) {
        Hit hit = pending(target, source);
        if (hit == null) return amount;
        hit.entered = true;
        hit.nativeInput = amount;
        if (!hit.breakCaps) return amount;
        if (hit.defense != null && amount > 0) {
            float allowed = hit.defense.withoutDamageCap(target, source, hit.amount, amount);
            return Float.isFinite(allowed) && allowed >= amount ? Math.min(allowed,(float)CombatAttributes.LIMIT) : amount;
        }
        return hit.shape.restore(target, source, hit.amount, amount);
    }

    /** Some entities construct their own native container without delegating to LivingEntity.hurt. */
    public static void incoming(LivingEntity target, DamageContainer container) {
        Hit hit = HIT.get();
        if (hit == null || hit.target != target || hit.source != container.getSource()) return;
        if (hit.transaction != null) {
            hit.ambiguous = true;
            return;
        }
        hit.transaction = container;
        if (!hit.entered) {
            float amount = container.getNewDamage();
            container.setNewDamage(atNativeEntry(target, hit.source, amount));
        }
    }

    public static void settled(LivingEntity target, DamageContainer container) {
        Hit hit = HIT.get();
        if (hit == null || hit.target != target || hit.transaction != container) return;
        if (hit.posted) hit.ambiguous = true;
        hit.posted = true;
        hit.finalDamage = container.getNewDamage();
    }

    public static void canceled(LivingEntity target, DamageContainer container) {
        var hit = HIT.get();
        if (hit != null && hit.target == target && hit.transaction == container) hit.canceled = true;
    }

    public static float ownedDebit(LivingEntity target, DamageSource source, float original) {
        var hit = HIT.get();
        if (hit == null || hit.target != target || hit.source != source || hit.defense == null) return original;
        if (hit.canceled || hit.committed) return 0;
        if (hit.ambiguous || !hit.posted || !Float.isFinite(hit.finalDamage) || hit.finalDamage < 0) return original;
        hit.committed = true;
        return hit.finalDamage;
    }

    public static Object vitalityWrite(SynchedEntityData data, EntityDataAccessor<?> key, Object value) {
        if (data != settlingData) return value;
        Hit hit = HIT.get();
        if (hit == null || hit.shape.vitality() != key || hit.target.getEntityData() != data
                || !(value instanceof Float proposed) || !Float.isFinite(proposed)) return value;
        if (hit.rescuing) {
            hit.projected = Math.clamp(proposed, 0F, hit.target.getMaxHealth());
            return hit.before; // A rescue listener wrote the owned field directly. The outer write commits it.
        }
        if (!hit.posted || hit.ambiguous || hit.committed || hit.target.getHealth() != hit.before
                || !Float.isFinite(hit.finalDamage) || hit.finalDamage < 0 || hit.before <= 0
                || proposed != Math.max(0F, hit.before - hit.nativeInput)) return value;
        hit.committed = true;
        float after = Math.max(0F, hit.before - hit.finalDamage);
        if (after > 0 || hit.target.isRemoved() || ((LivingRescueMixin)hit.target).academy$deathStarted()) return after;
        var previousRescue = rescuingData;
        rescuingData = data;
        hit.rescuing = true;
        hit.projected = 0;
        try {
            if (((LivingRescueMixin)hit.target).academy$tryTotem(hit.source)) return hit.projected;
            if (hasOwnDeathTicks(hit.target.getClass())) {
                if (CommonHooks.onLivingDeath(hit.target, hit.source)) return Math.max(1F, hit.projected);
            } else {
                hit.target.die(hit.source);
                if (!((LivingRescueMixin)hit.target).academy$deathStarted() && !hit.target.isRemoved()) return Math.max(1F, hit.projected);
            }
            return hit.projected;
        } finally {
            hit.rescuing = false;
            rescuingData = previousRescue;
        }
    }

    private static boolean hasOwnDeathTicks(Class<?> type) {
        for (Class<?> owner=type; owner != null && owner != LivingEntity.class; owner=owner.getSuperclass()) {
            try { owner.getDeclaredMethod("tickDeath"); return true; }
            catch (NoSuchMethodException inherited) { /* inspect superclass */ }
        }
        return false;
    }

    public static Float rescueHealth(SynchedEntityData data, EntityDataAccessor<?> key) {
        if (data != rescuingData) return null;
        Hit hit = HIT.get();
        return hit != null && hit.rescuing && hit.target.getEntityData() == data && hit.shape.vitality() == key
                ? hit.projected : null;
    }

    public static boolean rescueWrite(LivingEntity entity, float value) {
        Hit hit = HIT.get();
        if (hit == null || !hit.rescuing || hit.target != entity || !Float.isFinite(value)) return false;
        hit.projected = Math.clamp(value, 0F, entity.getMaxHealth());
        return true;
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
