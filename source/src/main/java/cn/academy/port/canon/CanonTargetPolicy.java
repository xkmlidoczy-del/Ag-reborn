package cn.academy.port.canon;

import cn.academy.port.story.UrbanCalibrator;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/** Hostile vanilla mobs plus the explicitly reviewed exceptions: the ACA story boss and tagged training dummies. */
public final class CanonTargetPolicy {
    /** Practice targets from other mods (optional entries, e.g. MmmMmmMmmMmm's target dummy): skills treat them as an enemy. */
    public static final TagKey<EntityType<?>> TRAINING_TARGETS = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("academy", "training_targets"));
    private CanonTargetPolicy() {}
    public static boolean urbanBoss(LivingEntity entity) {
        return entity != null && entity.getClass() == UrbanCalibrator.Boss.class
                && entity.getType() == UrbanCalibrator.BOSS.get();
    }
    public static boolean trainingTarget(LivingEntity entity) {
        return entity != null && (entity.getType().is(TRAINING_TARGETS)
                || entity instanceof cn.academy.port.city.CityNpcEntity npc && npc.sparring());
    }
    /** The non-vanilla entities a canon skill may strike: the story boss and training dummies. */
    public static boolean reviewed(LivingEntity entity) { return urbanBoss(entity) || trainingTarget(entity); }
    public static boolean hostile(LivingEntity entity) { return entity instanceof Enemy || reviewed(entity); }
}
