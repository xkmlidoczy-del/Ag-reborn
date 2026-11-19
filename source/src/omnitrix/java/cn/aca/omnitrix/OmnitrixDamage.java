package cn.aca.omnitrix;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/** Penetrating moves keep normal attack attribution and use the native damage transaction once. */
final class OmnitrixDamage {
    private OmnitrixDamage() {}

    static boolean hit(ServerPlayer player, LivingEntity target, float amount, boolean piercesArmor) {
        return AcademyInterop.hurt(target, strike(player, piercesArmor), amount);
    }

    private static DamageSource strike(ServerPlayer player, boolean piercesArmor) {
        DamageSource nativeAttack = player.damageSources().playerAttack(player);
        if (!piercesArmor) return nativeAttack;
        return new DamageSource(nativeAttack.typeHolder(), player) {
            @Override public boolean is(TagKey<DamageType> tag) {
                return tag.equals(DamageTypeTags.BYPASSES_ARMOR) || super.is(tag);
            }
        };
    }
}
