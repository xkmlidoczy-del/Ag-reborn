package cn.aca.infinity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Counts confirmed erasures among the selected lives. */
final class SnapResults {
    private int erased, resisted;
    private final int ships;

    SnapResults(int ships) {
        this.ships = ships;
    }

    void erase(ServerPlayer caster, LivingEntity target) {
        if (AcademyCompat.erasureAvailable()) {
            if (cn.academy.port.api.CombatErasure.erase(caster, target)) erased++;
            else resisted++;
        } else if (target instanceof ServerPlayer player) {
            var source = new DamageSource(caster.damageSources().fellOutOfWorld().typeHolder(), caster);
            player.setHealth(0);
            player.die(source);
            if (player.isDeadOrDying()) erased++;
            else resisted++;
        } else {
            target.setRemoved(Entity.RemovalReason.DISCARDED);
            if (target.isRemoved()) erased++;
            else resisted++;
        }
    }

    int affected() {
        return erased + ships;
    }

    void show(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("msg.aca_infinity.snap_erased", erased, resisted, ships)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
    }
}
