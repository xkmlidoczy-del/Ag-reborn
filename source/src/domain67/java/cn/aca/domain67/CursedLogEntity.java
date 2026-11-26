package cn.aca.domain67;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Heavy Spinning Iron-Bound Cursed Log ("咒力金箍滚木") Entity (v1.2.0):
 * - Clamps cleanly against `floorY` instead of sinking underground, transitioning from aerial plunge
 *   into a heavy, unmistakable horizontal floor-rolling crush across the restaurant marble floor!
 * - Tracks `hitTargets` per entity so each victim takes exact single-hit damage while the log continues
 *   visibly thundering & rolling across the stage for 2.0-2.6 seconds.
 */
public final class CursedLogEntity extends Entity {
    public static final int VARIANT_GUARD = 0;
    public static final int VARIANT_BARRAGE = 1;
    public static final int VARIANT_MEGA_BLACK_FLASH = 2;

    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(CursedLogEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(CursedLogEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_GROUNDED =
            SynchedEntityData.defineId(CursedLogEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID ownerId;
    private Vec3 velocity = Vec3.ZERO;
    private Vec3 knockbackTarget = null;
    private double floorY = Double.NaN;
    private boolean hasSlammedFloor = false;
    private float damage = 14.0f;
    private int maxLife = 48;
    private final Set<UUID> hitTargets = new HashSet<>();

    public CursedLogEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static CursedLogEntity spawn(
            ServerLevel level,
            UUID ownerId,
            int variant,
            Vec3 startPos,
            Vec3 velocity,
            float scale,
            float damage,
            int maxLife,
            Vec3 knockbackTarget
    ) {
        return spawnOnStage(level, ownerId, variant, startPos, velocity, scale, damage, maxLife, knockbackTarget, Double.NaN);
    }

    public static CursedLogEntity spawnOnStage(
            ServerLevel level,
            UUID ownerId,
            int variant,
            Vec3 startPos,
            Vec3 velocity,
            float scale,
            float damage,
            int maxLife,
            Vec3 knockbackTarget,
            double stageFloorY
    ) {
        CursedLogEntity log = new CursedLogEntity(AcaDomain67.CURSED_LOG.get(), level);
        log.ownerId = ownerId;
        log.setVariant(variant);
        log.setLogScale(scale);
        log.velocity = velocity;
        log.damage = damage;
        log.maxLife = maxLife;
        log.knockbackTarget = knockbackTarget;
        log.floorY = stageFloorY;
        log.setPos(startPos.x, startPos.y, startPos.z);

        double horiz = Math.hypot(velocity.x, velocity.z);
        float yaw = horiz > 1e-4
                ? (float) (Math.toDegrees(Math.atan2(-velocity.x, velocity.z)))
                : (level.random.nextFloat() * 360.0f);
        float pitch = (float) (Math.toDegrees(Math.atan2(-velocity.y, Math.max(1e-4, horiz))));
        log.setYRot(yaw);
        log.setXRot(pitch);
        level.addFreshEntity(log);
        return log;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_VARIANT, VARIANT_GUARD);
        builder.define(DATA_SCALE, 1.0f);
        builder.define(DATA_GROUNDED, false);
    }

    public int getVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public void setVariant(int v) {
        this.entityData.set(DATA_VARIANT, v);
    }

    public float getLogScale() {
        return this.entityData.get(DATA_SCALE);
    }

    public void setLogScale(float s) {
        this.entityData.set(DATA_SCALE, s);
    }

    public boolean isGroundedRolling() {
        return this.entityData.get(DATA_GROUNDED);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 196.0 * 196.0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            double nextX = getX() + velocity.x;
            double nextY = getY() + velocity.y;
            double nextZ = getZ() + velocity.z;

            // Clamp onto restaurant stage floor so logs NEVER vanish underground — they slam & roll across the hall!
            if (!Double.isNaN(floorY) && nextY <= floorY + 0.08) {
                nextY = floorY + 0.08;
                if (!hasSlammedFloor) {
                    hasSlammedFloor = true;
                    this.entityData.set(DATA_GROUNDED, true);
                    double rad = Math.toRadians(getYRot());
                    double fwdX = -Math.sin(rad);
                    double fwdZ = Math.cos(rad);
                    double rollSpeed = (getVariant() == VARIANT_MEGA_BLACK_FLASH) ? 0.34 : 0.56;
                    double curH = Math.hypot(velocity.x, velocity.z);
                    if (curH > 0.08) {
                        velocity = new Vec3(velocity.x / curH * rollSpeed, 0.0, velocity.z / curH * rollSpeed);
                    } else {
                        velocity = new Vec3(fwdX * rollSpeed, 0.0, fwdZ * rollSpeed);
                    }
                    if (level() instanceof ServerLevel sl) {
                        boolean mega = getVariant() == VARIANT_MEGA_BLACK_FLASH;
                        sl.playSound(null, nextX, nextY, nextZ,
                                mega ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.ANVIL_LAND,
                                SoundSource.PLAYERS, mega ? 1.4f : 0.85f, mega ? 0.62f : 0.78f);
                        sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, nextX, nextY + 0.25, nextZ,
                                mega ? 22 : 8, 1.2 * getLogScale(), 0.15, 1.2 * getLogScale(), 0.03);
                        sl.sendParticles(ParticleTypes.CRIT, nextX, nextY + 0.4, nextZ,
                                mega ? 28 : 10, 1.1 * getLogScale(), 0.3, 1.1 * getLogScale(), 0.25);
                        if (mega && ownerId != null) {
                            Domain67Network.broadcastCue(sl, new Vec3(nextX, nextY, nextZ),
                                    new Domain67Network.VfxCuePayload(
                                            Domain67Network.CUE_MEGA_LOG_IMPACT, ownerId,
                                            new Vec3(nextX, nextY, nextZ), new Vec3(nextX, nextY, nextZ), 0));
                        }
                    }
                } else {
                    // Gradual heavy rolling friction on marble floor
                    velocity = new Vec3(velocity.x * 0.95, 0.0, velocity.z * 0.95);
                }
            }

            setPos(nextX, nextY, nextZ);

            if (level() instanceof ServerLevel sl) {
                float sc = getLogScale();
                if (hasSlammedFloor && (tickCount & 2) == 0) {
                    sl.sendParticles(ParticleTypes.WAX_ON, getX(), getY() + 0.2, getZ(),
                            2, 0.7 * sc, 0.1, 0.7 * sc, 0.02);
                }

                double hitRadius = 1.55 * sc;
                AABB box = getBoundingBox().inflate(hitRadius);
                var owner = ownerId != null ? sl.getServer().getPlayerList().getPlayer(ownerId) : null;
                for (LivingEntity target : sl.getEntitiesOfClass(LivingEntity.class, box)) {
                    if (!AcademyCompat.canTarget(owner, target)) continue;
                    if (!hitTargets.add(target.getUUID())) continue;

                    var src = owner != null ? sl.damageSources().playerAttack(owner) : sl.damageSources().magic();
                    boolean shielded = AcademyCompat.ignoresControl(target);
                    if (!AcademyCompat.hurt(target, src, damage)) continue;

                    if (!shielded && target.isAlive() && !AcademyCompat.ignoresControl(target)) {
                        applyKnockback(target);
                    }

                    sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                            getVariant() == VARIANT_MEGA_BLACK_FLASH
                                    ? SoundEvents.GENERIC_EXPLODE.value()
                                    : SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
                            SoundSource.PLAYERS, 1.2f, getVariant() == VARIANT_MEGA_BLACK_FLASH ? 0.65f : 0.9f);
                    sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(),
                            14, 0.6, 0.6, 0.6, 0.18);
                }
            }
            if (tickCount >= maxLife) {
                discard();
            }
        }
    }

    private void applyKnockback(LivingEntity target) {
        if (knockbackTarget != null) {
            Vec3 push = knockbackTarget.subtract(target.position());
            double len = Math.max(0.1, push.length());
            target.setDeltaMovement(push.x / len * 1.45, 0.42, push.z / len * 1.45);
        } else {
            Vec3 dir = velocity.lengthSqr() > 1e-4 ? velocity.normalize() : new Vec3(0, 0.3, 0);
            target.setDeltaMovement(dir.x * 1.15, Math.max(0.35, dir.y * 0.6), dir.z * 1.15);
        }
        target.hurtMarked = true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setVariant(tag.getInt("Variant"));
        setLogScale(tag.contains("Scale") ? tag.getFloat("Scale") : 1.0f);
        damage = tag.contains("Damage") ? tag.getFloat("Damage") : 14.0f;
        maxLife = tag.contains("MaxLife") ? tag.getInt("MaxLife") : 48;
        if (tag.contains("FloorY")) floorY = tag.getDouble("FloorY");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Variant", getVariant());
        tag.putFloat("Scale", getLogScale());
        tag.putFloat("Damage", damage);
        tag.putInt("MaxLife", maxLife);
        if (!Double.isNaN(floorY)) tag.putDouble("FloorY", floorY);
    }
}
