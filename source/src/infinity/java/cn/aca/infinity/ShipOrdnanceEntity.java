package cn.aca.infinity;

import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ShipOrdnanceEntity extends Entity {
    private int shipId = -1;
    private UUID pilotId;
    private boolean hostileOrdnance;
    private int life;

    public ShipOrdnanceEntity(EntityType<ShipOrdnanceEntity> type, Level level) {
        super(type, level);
    }

    public void configure(SanctuaryShipEntity ship, ServerPlayer pilot, Vec3 velocity, boolean hostile) {
        this.shipId = ship.getId();
        this.pilotId = pilot != null ? pilot.getUUID() : null;
        this.hostileOrdnance = hostile;
        setDeltaMovement(velocity);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (++life > 70) {
            discard();
            return;
        }

        Vec3 vel = getDeltaMovement();
        if (!level().isClientSide && level() instanceof ServerLevel sl) {
            // Subtle guidance towards nearest valid target
            LivingEntity lock = sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(14.0), e -> {
                if (!e.isAlive() || e.getId() == shipId) return false;
                if (e.getVehicle() != null && e.getVehicle().getId() == shipId) return false;
                if (hostileOrdnance) return e instanceof Player p && !p.isSpectator();
                Player owner = pilotId != null ? sl.getPlayerByUUID(pilotId) : null;
                return owner != null ? !StoneAbilities.isFriendlyTo(owner, e) : !(e instanceof Player);
            }).stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);

            if (lock != null) {
                Vec3 desired = lock.getBoundingBox().getCenter().subtract(position()).normalize().scale(vel.length());
                vel = vel.lerp(desired, 0.16);
                setDeltaMovement(vel);
            }

            sl.sendParticles(ParticleTypes.WITCH, getX(), getY(), getZ(), 2, 0.05, 0.05, 0.05, 0.01);
            sl.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 1, 0.04, 0.04, 0.04, 0.01);
        }

        move(MoverType.SELF, vel);
        if (!level().isClientSide && level() instanceof ServerLevel sl) {
            boolean hitEntity = !sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(1.1), e -> {
                if (!e.isAlive()) return false;
                if (e.getVehicle() != null && e.getVehicle().getId() == shipId) return false;
                if (hostileOrdnance) return e instanceof Player;
                return !(e instanceof Player);
            }).isEmpty();

            if (horizontalCollision || verticalCollision || hitEntity) {
                detonate(sl);
            }
        }
    }

    private void detonate(ServerLevel sl) {
        Player pilot = pilotId != null ? sl.getPlayerByUUID(pilotId) : null;
        AABB blast = getBoundingBox().inflate(3.8);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, blast, LivingEntity::isAlive)) {
            if (e.getVehicle() != null && e.getVehicle().getId() == shipId) continue;
            if (!hostileOrdnance && pilot != null && StoneAbilities.isFriendlyTo(pilot, e)) continue;
            if (hostileOrdnance && !(e instanceof Player)) continue;
            e.invulnerableTime = 0;
            DamageSource src = pilot != null ? sl.damageSources().playerAttack(pilot) : sl.damageSources().magic();
            e.hurt(src, hostileOrdnance ? 9.0f : 34.0f);
        }
        sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 1, 0.2, 0.2, 0.2, 0.0);
        sl.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.65f, 1.25f);
        discard();
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
