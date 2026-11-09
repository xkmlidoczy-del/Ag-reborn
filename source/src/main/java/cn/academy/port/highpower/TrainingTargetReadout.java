package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.canon.CanonTargetPolicy;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * A training dummy has no high-power ledger, so it only ever sees the native witness (capped at
 * {@link HighPowerCombat#MAX_NATIVE_WITNESS}) and every exact-damage skill reads the same on it. The caster gets the exact amount instead.
 */
final class TrainingTargetReadout {
    private TrainingTargetReadout() {}

    static void hit(ServerPlayer player, String skill, LivingEntity target, BigDecimal raw) {
        if (player == null || skill == null || raw == null || !CanonTargetPolicy.trainingTarget(target) || player.connection == null) return;
        String plain = raw.stripTrailingZeros().toPlainString();
        LogUtils.getLogger().info("QA_EXACT_DMG {} {}", skill, plain);
        String text = HighPowerRecoveryHudModel.compact(plain, HighPowerRecoveryHudModel.numberLocale(player.clientInformation().language()));
        // Classic keys arrive as "category.skill" (optionally with a "_native" suffix), canon ones as the bare id.
        String key = skill.contains(".") ? skill.replaceFirst("_native$", "") : AbilityStorage.get(player).category + "." + skill;
        player.displayClientMessage(Component.translatable("ac.highpower.training_readout",
                Component.translatable("ac.ability." + key + ".name"), text), true);
    }
}
