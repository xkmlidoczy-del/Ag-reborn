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
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class TemporalEffects {
    public static final int RANGE = 40;
    public static final int DURATION = 240;
    private record Freeze(long expiry, Vec3 velocity) {}
    private record Field(ServerPlayer caster, Vec3 center, long expiry) {}
    private static final Map<Entity, Freeze> FROZEN = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<java.util.UUID, Field>> FIELDS = new WeakHashMap<>();

    private TemporalEffects() {}

    static void register(IEventBus bus) {
        bus.addListener(TemporalEffects::pause);
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
        Freeze previous = FROZEN.get(target);
        if (previous != null && previous.expiry >= expiry) return;
        FROZEN.put(target, new Freeze(expiry, previous == null ? target.getDeltaMovement() : previous.velocity));
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        WristNetwork.freeze(target, (int) (expiry - level.getServer().overworld().getGameTime()));
        level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + target.getBbHeight() * .54,
                target.getZ(), 9, .31, .48, .31, .009);
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
        var owner = projectile.getOwner();
        return owner == null ? projectile instanceof net.minecraft.world.entity.projectile.AbstractArrow
                : owner instanceof LivingEntity living
                && (owner instanceof Enemy || owner instanceof ServerPlayer) && PursuitBody.permitted(p, living);
    }

    private static void pause(EntityTickEvent.Pre e) {
        if (!e.getEntity().level().isClientSide && FROZEN.containsKey(e.getEntity())) e.setCanceled(true);
    }

    private static void tracking(net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking event) {
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
            if (now >= entry.getValue().expiry) {
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
