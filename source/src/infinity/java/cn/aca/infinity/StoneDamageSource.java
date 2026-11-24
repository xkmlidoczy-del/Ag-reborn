package cn.aca.infinity;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** Energy from the stones enters the same hurt/absorption/Academy-shield pipeline as abilities. */
final class StoneDamageSource extends DamageSource {
    private static final ResourceKey<DamageType> TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, AcaInfinity.id("stone_damage"));
    private final DamageSource original;
    private final boolean separatePulse;

    StoneDamageSource(Level level, DamageSource original, boolean separatePulse) {
        super(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TYPE),
                original.getDirectEntity(), original.getEntity());
        this.original = original;
        this.separatePulse = separatePulse;
    }

    @Override public boolean is(TagKey<DamageType> tag) {
        return separatePulse && tag.equals(DamageTypeTags.BYPASSES_COOLDOWN) || super.is(tag);
    }

    @Override public Component getLocalizedDeathMessage(LivingEntity target) {
        return original.getLocalizedDeathMessage(target);
    }
}
