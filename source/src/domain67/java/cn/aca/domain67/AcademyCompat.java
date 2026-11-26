package cn.aca.domain67;

import cn.academy.port.AcademyConfig;
import cn.academy.port.api.ControlRelations;
import cn.academy.port.api.NativeCombatDamage;
import cn.academy.port.highpower.PlayerShieldArmor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

/** Shared damage, control and terrain policy for the embedded build. */
final class AcademyCompat {
    private static final boolean ACADEMY_PRESENT = ModList.get().isLoaded("academy");

    private AcademyCompat() {}

    static boolean canTarget(ServerPlayer owner, LivingEntity target) {
        if (!target.isAlive() || target.isSpectator() || target == owner) return false;
        if (owner != null && (owner.level() != target.level() || owner.isAlliedTo(target)
                || target.isAlliedTo(owner) || ACADEMY_PRESENT && ControlRelations.allied(owner, target))) return false;
        if (!(target instanceof ServerPlayer player)) return true;
        return owner != null && !player.getAbilities().invulnerable && owner.server.isPvpAllowed()
                && owner.canHarmPlayer(player) && (!ACADEMY_PRESENT || AcademyConfig.ATTACK_PLAYERS.get());
    }

    static boolean ignoresControl(LivingEntity target) {
        return ACADEMY_PRESENT && PlayerShieldArmor.ignoresControl(target);
    }

    static boolean canTransformTerrain(ServerLevel level) {
        return !ACADEMY_PRESENT || AcademyConfig.canDestroyBlocks(level);
    }

    static boolean hurt(LivingEntity target, DamageSource source, float amount) {
        if (ACADEMY_PRESENT) return NativeCombatDamage.hurt(target, source, amount);
        target.invulnerableTime = 0;
        return target.hurt(source, amount);
    }
}
