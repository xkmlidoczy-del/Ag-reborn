package cn.academy.port.highpower.environment;

import cn.academy.port.AbilityDamage;
import cn.academy.port.canon.CanonTargetPolicy;
import cn.academy.port.highpower.CompatMode;
import cn.academy.port.highpower.EsperLegacyBypass;
import cn.academy.port.highpower.PlayerShieldArmor;
import cn.academy.port.macro.MacroGeometry;
import cn.academy.port.skill.RadiationMarks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Controlled server-side endpoint blast, radial splash damage, and restorable crater helper.
 *
 * <p>All combat damage remains owned by {@link AbilityDamage} / {@link RadiationMarks} while
 * NeoForge's explosion lifecycle handles event hooks, radial knockback, sound, and particles,
 * and {@link SkillEnvironment#beginBlast} manages restorable block vacancies.
 */
public final class SkillBlastHelper {
    private SkillBlastHelper() {}

    public static void emitBlast(
            ServerPlayer caster,
            String qualifiedSkill,
            int skillLevel,
            Vec3 center,
            float power,
            double craterRadius,
            boolean allowTerrain) {
        if (!CompatMode.openGameplay()
                || EsperLegacyBypass.standAside()
                || caster == null
                || qualifiedSkill == null
                || center == null
                || !caster.server.isSameThread()
                || !caster.isAlive()
                || caster.isRemoved()
                || caster.isSpectator()
                || !Double.isFinite(center.x)
                || !Double.isFinite(center.y)
                || !Double.isFinite(center.z)
                || !Float.isFinite(power)
                || power <= 0f) {
            return;
        }
        ServerLevel world = caster.serverLevel();
        BlockPos blockCenter = BlockPos.containing(center);
        if (world.isOutsideBuildHeight(blockCenter) || !MacroGeometry.sphereLoaded(world, center, power * 2.0 + 2.0)) {
            return;
        }

        var calculator = new EntityBasedExplosionDamageCalculator(caster) {
            @Override
            public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
                return false;
            }

            @Override
            public float getKnockbackMultiplier(Entity entity) {
                return entity instanceof LivingEntity living && CanonTargetPolicy.directedAreaTarget(caster, living)
                        && !PlayerShieldArmor.ignoresControl(living) ? super.getKnockbackMultiplier(entity) : 0.0f;
            }
        };
        var explosion = new Explosion(
                world,
                caster,
                null,
                calculator,
                center.x,
                center.y,
                center.z,
                power,
                false,
                Explosion.BlockInteraction.DESTROY,
                ParticleTypes.EXPLOSION,
                ParticleTypes.EXPLOSION_EMITTER,
                SoundEvents.GENERIC_EXPLODE);
        if (EventHooks.onExplosionStart(world, explosion)) {
            return;
        }

        double effectiveRadius = Double.isFinite(craterRadius) ? Math.max(1.2, Math.min(8.0, craterRadius)) : 1.6;
        boolean doTerrain = allowTerrain
                && skillLevel >= 3
                && SkillEnvironment.terrainPermitted(world, qualifiedSkill)
                && MacroGeometry.sphereLoaded(world, center, effectiveRadius + 2.0);

        explosion.explode();
        explosion.getHitPlayers().remove(caster);

        if (doTerrain && caster.serverLevel() == world) {
            var terrain = SkillEnvironment.beginBlast(caster, qualifiedSkill, skillLevel);
            if (terrain.allowsBlockChanges()) {
                var candidates = new ArrayList<>(explosion.getToBlow());
                candidates.sort(Comparator.comparingDouble((BlockPos pos) -> pos.distToCenterSqr(center))
                        .thenComparingLong(BlockPos::asLong));
                terrain.blast(center, effectiveRadius, candidates);
            }
        }

        explosion.getToBlow().clear();
        explosion.finalizeExplosion(true);

        for (var viewer : world.players()) {
            if (!viewer.hasDisconnected() && viewer.distanceToSqr(center) < 4096 && viewer.connection != null) {
                viewer.connection.send(new ClientboundExplodePacket(
                        center.x,
                        center.y,
                        center.z,
                        explosion.radius(),
                        List.of(),
                        explosion.getHitPlayers().get(viewer),
                        explosion.getBlockInteraction(),
                        explosion.getSmallExplosionParticles(),
                        explosion.getLargeExplosionParticles(),
                        explosion.getExplosionSound()));
            }
        }
    }

    public static int splashEntities(
            ServerPlayer caster,
            String qualifiedDamageKey,
            Vec3 center,
            double radius,
            double maxSplashDamage,
            Entity excludedPrimary,
            boolean meltdownerMark) {
        if (!CompatMode.openGameplay()
                || EsperLegacyBypass.standAside()
                || caster == null
                || qualifiedDamageKey == null
                || center == null
                || !caster.server.isSameThread()
                || !caster.isAlive()
                || caster.isRemoved()
                || !Double.isFinite(radius)
                || radius <= 0
                || !Double.isFinite(maxSplashDamage)
                || maxSplashDamage <= 0) {
            return 0;
        }
        ServerLevel world = caster.serverLevel();
        if (!world.hasChunkAt(BlockPos.containing(center))) {
            return 0;
        }
        double r2 = radius * radius;
        var candidates = new ArrayList<>(world.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(center, center).inflate(radius + 1.0),
                target -> target != caster
                        && target != excludedPrimary
                        && target.isAlive()
                        && !target.isRemoved()
                        && boxDistanceSqr(target.getBoundingBox(), center) <= r2
                        && CanonTargetPolicy.directedAreaTarget(caster, target)));
        if (candidates.isEmpty()) {
            return 0;
        }
        candidates.sort(Comparator.comparingDouble((LivingEntity e) -> boxDistanceSqr(e.getBoundingBox(), center))
                .thenComparingInt(Entity::getId));
        int hits = 0;
        int limit = Math.min(8, candidates.size());
        for (int i = 0; i < limit; i++) {
            LivingEntity victim = candidates.get(i);
            double dist = Math.sqrt(boxDistanceSqr(victim.getBoundingBox(), center));
            double falloff = 1.0 - 0.35 * Math.min(1.0, dist / radius);
            double dealt = maxSplashDamage * falloff;
            if (dealt <= 0.05) continue;
            if (meltdownerMark) {
                RadiationMarks.attack(caster, qualifiedDamageKey, victim, (float) dealt);
                hits++;
            } else if (AbilityDamage.attack(caster, qualifiedDamageKey, victim, dealt)) {
                hits++;
            }
        }
        return hits;
    }

    public static double boxDistanceSqr(AABB box, Vec3 point) {
        double cx = Mth.clamp(point.x, box.minX, box.maxX);
        double cy = Mth.clamp(point.y, box.minY, box.maxY);
        double cz = Mth.clamp(point.z, box.minZ, box.maxZ);
        double dx = cx - point.x;
        double dy = cy - point.y;
        double dz = cz - point.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
