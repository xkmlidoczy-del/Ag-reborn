package cn.academy.port.highpower;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class IgnisDamage {
    private static final ResourceLocation IGNIS = ResourceLocation.fromNamespaceAndPath("cataclysm", "ignis");

    private IgnisDamage() {}

    /** Only the bonus term in Ignis's HP-scaling attacks uses this encounter-relative basis. */
    public static float percentageBasis(LivingEntity victim, float health, Entity caster) {
        if (victim instanceof Player && caster instanceof LivingEntity boss
                && IGNIS.equals(BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()))) {
            return Math.min(health, boss.getMaxHealth());
        }
        return health;
    }
}
