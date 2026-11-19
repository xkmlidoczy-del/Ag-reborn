package cn.academy.port.canon;

import cn.academy.port.AcademyConfig;
import cn.academy.port.story.UrbanCalibrator;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/**
 * Tiered Canon target eligibility:
 * <ul>
 *   <li>{@link #hostile(LivingEntity)} — used by autonomous constructs and {@code AUTO_AOE} skills so passive villagers,
 *       livestock, and calm neutral mobs are never swept up by area damage. Includes angry {@link NeutralMob}s.</li>
 *   <li>{@link #directTarget(ServerPlayer, LivingEntity)} — used by {@code DIRECT_AIM} single-target crosshair skills
 *       so players can deliberately strike neutral/modded mobs and training stands without hitting allies, pets, or civilians.</li>
 * </ul>
 */
public final class CanonTargetPolicy {
    /** Practice targets from other mods (optional entries, e.g. MmmMmmMmmMmm's target dummy): skills treat them as an enemy. */
    public static final TagKey<EntityType<?>> TRAINING_TARGETS = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("academy", "training_targets"));

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
    public static boolean reviewed(LivingEntity entity) {
        return urbanBoss(entity) || trainingTarget(entity);
    }

    public static boolean angryNeutral(LivingEntity entity) {
        return entity instanceof NeutralMob neutral && (neutral.isAngry() || neutral.getTarget() != null);
    }

    public static boolean hostile(LivingEntity entity) {
        return entity instanceof Enemy
                || angryNeutral(entity)
                || entity instanceof cn.academy.port.city.defense.GuardianEntity
                || entity instanceof cn.academy.port.city.CityNpcEntity npc && npc.look() == cn.academy.port.city.CityNpcEntity.Look.GUARD
                || reviewed(entity);
    }

    /** Direct crosshair strike eligibility: admits deliberate strikes on non-civilian mobs and armor stands while protecting allies & pets. */
    public static boolean directTarget(ServerPlayer caster, LivingEntity entity) {
        if (caster == null || entity == null || entity == caster || !entity.isAlive() || entity.isRemoved()
                || entity.isSpectator() || caster.isAlliedTo(entity) || entity.isAlliedTo(caster)) {
            return false;
        }
        if (entity instanceof Player other) {
            return AcademyConfig.ATTACK_PLAYERS.get() && caster.server.isPvpAllowed() && caster.canHarmPlayer(other);
        }
        if (hostile(entity)) {
            return true;
        }
        if (entity instanceof AbstractVillager) {
            return false;
        }
        if (entity instanceof cn.academy.port.city.CityNpcEntity npc) {
            return npc.sparring() || npc.look() == cn.academy.port.city.CityNpcEntity.Look.GUARD;
        }
        if (entity instanceof TamableAnimal tamable && tamable.isTame()) {
            if (caster.getUUID().equals(tamable.getOwnerUUID())) return false;
            if (tamable.getOwner() instanceof Player owner && (caster.isAlliedTo(owner) || owner.isAlliedTo(caster))) return false;
        }
        return entity instanceof Mob || entity instanceof ArmorStand;
    }
}
