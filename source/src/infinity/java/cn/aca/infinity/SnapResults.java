package cn.aca.infinity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Counts confirmed outcomes from one snap, including a boss whose own death takes several seconds.
 */
final class SnapResults {
    int banished, damaged, killed, rescued, resisted;
    final int ships;

    SnapResults(int ships) {
        this.ships = ships;
    }

    void banish(LivingEntity target) {
        target.discard();
        if (target.isRemoved() && target.getRemovalReason() == Entity.RemovalReason.DISCARDED)
            banished++;
        else resisted++;
    }

    void strike(ServerPlayer caster, LivingEntity target) {
        var source = new DamageSource(caster.damageSources().fellOutOfWorld().typeHolder(), caster);
        float amount = Math.max(180F, target.getMaxHealth() * .55F);
        if (AcademyCompat.contracts()) {
            var receipt =
                    cn.academy.port.api.NativeCombatDamage.hurtDetailed(target, source, amount);
            if (receipt.totem() || receipt.rescued()) rescued++;
            else if (receipt.killed()) killed++;
            else if (receipt.affected()) {
                damaged++;
                receipt.whenKilled(
                        target,
                        ignored -> {
                            damaged--;
                            killed++;
                            if (!caster.hasDisconnected()) show(caster);
                        });
            } else resisted++;
            return;
        }
        float health = target.getHealth(), absorption = target.getAbsorptionAmount();
        AcademyCompat.hurt(target, source, amount, true);
        if (target.isRemoved() && target.getRemovalReason() == Entity.RemovalReason.KILLED)
            killed++;
        else if (target.getHealth() < health || target.getAbsorptionAmount() < absorption)
            damaged++;
        else resisted++;
    }

    int affected() {
        return banished + damaged + killed + rescued + ships;
    }

    void show(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable(
                                "msg.aca_infinity.snap_executed",
                                banished,
                                damaged,
                                killed,
                                rescued,
                                resisted,
                                ships)
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                false);
    }
}
