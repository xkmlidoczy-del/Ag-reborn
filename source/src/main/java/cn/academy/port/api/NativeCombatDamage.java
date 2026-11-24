package cn.academy.port.api;

import cn.academy.port.highpower.CapBreak;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Synchronous server entry for mod-authored attacks. The result is the native hurt result. */
public final class NativeCombatDamage {
    private NativeCombatDamage() {}

    public static CombatReceipt hurtDetailed(LivingEntity target, DamageSource source, float amount) {
        if (!(target.level() instanceof net.minecraft.server.level.ServerLevel level) || !level.getServer().isSameThread())
            throw new IllegalStateException("Combat receipts require the logical server thread");
        return CombatReceipt.observe(target, source, () -> hurt(target, source, amount));
    }

    public static boolean hurt(LivingEntity target, DamageSource source, float amount) {
        return CapBreak.hurt(target, source, amount);
    }

    public static boolean hurtSkill(LivingEntity target, DamageSource source, float amount) {
        return CapBreak.hurtSkill(target, source, amount);
    }

    /** Mental projection keeps the puppet in charge of intent and delivers a source-safe strike. */
    public static boolean hurtProjected(LivingEntity target, DamageSource source, float amount) {
        return CapBreak.hurtProjected(target, source, amount);
    }

    public static CombatReceipt hurtSkillDetailed(LivingEntity target, DamageSource source, float amount) {
        if (!(target.level() instanceof net.minecraft.server.level.ServerLevel level) || !level.getServer().isSameThread())
            throw new IllegalStateException("Combat receipts require the logical server thread");
        return CombatReceipt.observe(target, source, () -> hurtSkill(target, source, amount));
    }
}
