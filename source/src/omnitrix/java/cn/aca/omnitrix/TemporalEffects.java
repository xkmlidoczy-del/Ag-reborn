package cn.aca.omnitrix;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class TemporalEffects {
    public static final int RANGE = 40;
    public static final int DURATION = 240;
    private record Freeze(long expiry, Vec3 velocity, Vec3 lockedPos) {}
    private record Field(ServerPlayer caster, Vec3 center, long expiry) {}
    private static final Map<Entity, Freeze> FROZEN = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<java.util.UUID, Field>> FIELDS = new WeakHashMap<>();

    private TemporalEffects() {}

    static void register(IEventBus bus) {
        bus.addListener(TemporalEffects::pause);
        bus.addListener(TemporalEffects::pausePost);
        bus.addListener(TemporalEffects::onAttackFrozenProjectile);
        bus.addListener(TemporalEffects::thaw);
        bus.addListener(TemporalEffects::tracking);
    }

    public static boolean isFrozen(Entity entity) {
        return FROZEN.containsKey(entity);
    }

    static List<LivingEntity> freezeHostiles(ServerPlayer p) {
        long now = WristService.time(p);
        var field = new Field(p, p.position(), now + DURATION);
        FIELDS.computeIfAbsent(p.serverLevel(), level -> new java.util.HashMap<>()).put(p.getUUID(), field);
        WristNetwork.beginScene(p, WristNetwork.STASIS_SCENE, 0, field.center, DURATION);
        return freezeInside(p.serverLevel(), field);
    }

    static void freezeSingle(ServerLevel level, Entity target, long expiry) {
        if (AcademyInterop.holdingShieldArmor(target)) return;
        if (target instanceof Mob mob && OmnitrixAiControl.isAnyPuppet(mob)) return;
        Freeze previous = FROZEN.get(target);
        if (previous != null && previous.expiry >= expiry) return;
        Vec3 storedVel = previous == null ? target.getDeltaMovement() : previous.velocity;
        FROZEN.put(target, new Freeze(expiry, storedVel, target.position()));
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        if (target instanceof Mob mob) {
            mob.getNavigation().stop();
            OmnitrixAiControl.clearBrainCombatMemories(mob);
        }
        WristNetwork.freeze(target, (int) (expiry - level.getServer().overworld().getGameTime()));
        level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + target.getBbHeight() * .54,
                target.getZ(), 9, .31, .48, .31, .009);
    }

    /** Left-clicking a frozen projectile redirects its thaw launch vector 1.6x along the player's crosshair! */
    private static void onAttackFrozenProjectile(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.level().isClientSide) return;
        if (!(event.getTarget() instanceof Projectile proj)) return;
        Freeze cur = FROZEN.get(proj);
        if (cur == null) return;
        Vec3 aim = sp.getLookAngle().normalize();
        double speed = Math.max(2.8, cur.velocity.length() * 1.6);
        proj.setOwner(sp);
        if (proj instanceof AbstractArrow arrow) {
            arrow.setBaseDamage(Math.max(arrow.getBaseDamage() * 3.5, 45_000.0));
        }
        FROZEN.put(proj, new Freeze(Math.min(cur.expiry, WristService.time(sp) + 6), aim.scale(speed), cur.lockedPos));
        WristNetwork.cue(sp, WristNetwork.AEGIS_CUE, 0, WristNetwork.FLAG_PROJECTILE,
                sp.getEyePosition(), proj.position());
        sp.level().playSound(null, proj.blockPosition(), Omnitrix.ALIEN_X_AEGIS.get(),
                net.minecraft.sounds.SoundSource.PLAYERS, .85f, 1.32f);
        event.setCanceled(true);
    }

    static void deflectNearbyProjectiles(ServerPlayer p, double radius) {
        ServerLevel level = p.serverLevel();
        long now = WristService.time(p);
        Vec3 center = p.position().add(0, 1.15, 0);
        var projectiles = level.getEntitiesOfClass(Projectile.class, AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
                proj -> proj.distanceToSqr(center) <= radius * radius && hostileProjectile(p, proj));
        for (Projectile proj : projectiles) {
            if (!FROZEN.containsKey(proj)) {
                freezeSingle(level, proj, now + 60);
                if (now - Omnitrix.wrist(p).lastAegisCueAt >= 8) {
                    Omnitrix.wrist(p).lastAegisCueAt = now;
                    WristNetwork.cue(p, WristNetwork.AEGIS_CUE, center, proj.position());
                    level.playSound(null, p.blockPosition(), Omnitrix.ALIEN_X_AEGIS.get(),
                            net.minecraft.sounds.SoundSource.PLAYERS, .65f, 1.05f);
                }
            }
        }
    }

    private static List<LivingEntity> freezeInside(ServerLevel level, Field field) {
        ServerPlayer p = field.caster;
        var targets = level.getEntities(p, AABB.ofSize(field.center, RANGE * 2 + 4, RANGE * 2 + 4, RANGE * 2 + 4),
                e -> e.distanceToSqr(field.center) <= RANGE * RANGE
                && !OmnitrixAiControl.isPuppetOf(e, p.getUUID())
                && (e instanceof LivingEntity living && Abilities.validTarget(p, living, false)
                || e instanceof Projectile projectile && hostileProjectile(p, projectile) || e instanceof FallingBlockEntity))
                .stream().sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(field.center))).limit(128).toList();
        var frozenLivings = new java.util.ArrayList<LivingEntity>();
        for (Entity target : targets) {
            freezeSingle(level, target, field.expiry);
            if (target instanceof LivingEntity living) frozenLivings.add(living);
        }
        return frozenLivings;
    }

    private static boolean hostileProjectile(ServerPlayer p, Projectile projectile) {
        if (projectile instanceof Pulse || projectile instanceof AscalonSlash) return false;
        var owner = projectile.getOwner();
        if (owner == p) return false;
        if (owner == null) return true;
        return owner instanceof LivingEntity living
                && (owner instanceof Enemy || owner instanceof ServerPlayer
                || (owner instanceof Mob mob && Abilities.validTarget(p, mob, false)))
                && PursuitBody.permitted(p, living);
    }

    private static void pause(EntityTickEvent.Pre e) {
        if (!e.getEntity().level().isClientSide && FROZEN.containsKey(e.getEntity())
                && !AcademyInterop.holdingShieldArmor(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    private static void pausePost(EntityTickEvent.Post e) {
        Entity ent = e.getEntity();
        if (ent.level().isClientSide) return;
        Freeze fr = FROZEN.get(ent);
        if (fr != null && !AcademyInterop.holdingShieldArmor(ent)) {
            ent.setDeltaMovement(Vec3.ZERO);
            if (fr.lockedPos != null && ent.position().distanceToSqr(fr.lockedPos) > 0.04) {
                ent.setPos(fr.lockedPos.x, fr.lockedPos.y, fr.lockedPos.z);
            }
        }
    }

    private static void tracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer)) return;
        var freeze = FROZEN.get(event.getTarget());
        if (freeze != null) WristNetwork.freezeFor(viewer, event.getTarget(), (int) (freeze.expiry - WristService.time(viewer)));
    }

    private static void thaw(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        for (var level : event.getServer().getAllLevels()) {
            var fields = FIELDS.get(level);
            if (fields == null) continue;
            for (var entry : List.copyOf(fields.entrySet())) {
                Field field = entry.getValue();
                if (now >= field.expiry) {
                    fields.remove(entry.getKey());
                    WristNetwork.cueNear(level, field.caster.getUUID(), WristNetwork.THAW_CUE, field.center, 88);
                    level.sendParticles(ParticleTypes.FIREWORK, field.center.x, field.center.y + .8,
                            field.center.z, 40, 1.2, .5, 1.2, .19);
                    level.playSound(null, net.minecraft.core.BlockPos.containing(field.center),
                            Omnitrix.ALIEN_X_STASIS.get(), net.minecraft.sounds.SoundSource.PLAYERS, .75f, .78f);
                } else if (now % 4 == 0) freezeInside(level, field);
            }
        }
        var iterator = FROZEN.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Entity entity = entry.getKey();
            if (entity.isRemoved()) { iterator.remove(); continue; }
            if (!(entity.level() instanceof ServerLevel world) || world.getServer() != event.getServer()) continue;
            if (now >= entry.getValue().expiry || AcademyInterop.holdingShieldArmor(entity)) {
                entity.setDeltaMovement(entry.getValue().velocity);
                entity.hurtMarked = true;
                WristNetwork.freeze(entity, 0);
                iterator.remove();
            } else if (now % 16 == 0 && entity.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY() + entity.getBbHeight() * .55,
                        entity.getZ(), 2, .26, .33, .26, .004);
            }
        }
    }
}
