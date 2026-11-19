package cn.aca.infinity;

import java.util.Comparator;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SanctuaryShipEntity extends Entity {
    public static final int MAX_HULL = 1600;
    public static final int MAX_ENERGY = 1200;

    // Pilot input bitmask
    public static final int IN_FWD = 1;
    public static final int IN_BACK = 2;
    public static final int IN_LEFT = 4;
    public static final int IN_RIGHT = 8;
    public static final int IN_ASCEND = 16;
    public static final int IN_DESCEND = 32;
    public static final int IN_BOOST = 64;
    public static final int IN_CANNON = 128;
    public static final int IN_MISSILE = 256;
    public static final int IN_SHIELD_TOGGLE = 512;
    public static final int IN_RAIN_FIRE = 1024;
    public static final int IN_ESCORT_TOGGLE = 2048;
    public static final int IN_WARP = 4096;

    private static final EntityDataAccessor<Integer> DATA_HULL =
            SynchedEntityData.defineId(SanctuaryShipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ENERGY =
            SynchedEntityData.defineId(SanctuaryShipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SHIELD =
            SynchedEntityData.defineId(SanctuaryShipEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HOSTILE =
            SynchedEntityData.defineId(SanctuaryShipEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_COLLAPSE =
            SynchedEntityData.defineId(SanctuaryShipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SPEED =
            SynchedEntityData.defineId(SanctuaryShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BANK_ROLL =
            SynchedEntityData.defineId(SanctuaryShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_CANNON_CD =
            SynchedEntityData.defineId(SanctuaryShipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MISSILE_CD =
            SynchedEntityData.defineId(SanctuaryShipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ESCORT =
            SynchedEntityData.defineId(SanctuaryShipEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_RAIN_CD =
            SynchedEntityData.defineId(SanctuaryShipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_WARP_CD =
            SynchedEntityData.defineId(SanctuaryShipEntity.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossBar = new ServerBossEvent(
            Component.translatable("entity.aca_infinity.sanctuary_ship").append(Component.literal(" · 终局母舰")),
            BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.NOTCHED_10
    );

    private UUID ownerId;
    private int activeControls;
    private long lastControlTick = -100L;
    private boolean pendingCannon;
    private boolean pendingMissile;
    private boolean pendingShieldToggle;
    private boolean pendingRainFire;
    private boolean pendingEscortToggle;
    private boolean pendingWarp;

    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    private float lerpYaw, lerpPitch;

    public SanctuaryShipEntity(EntityType<SanctuaryShipEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.noPhysics = false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HULL, MAX_HULL)
                .define(DATA_ENERGY, MAX_ENERGY)
                .define(DATA_SHIELD, false)
                .define(DATA_HOSTILE, false)
                .define(DATA_COLLAPSE, 0)
                .define(DATA_SPEED, 0f)
                .define(DATA_BANK_ROLL, 0f)
                .define(DATA_CANNON_CD, 0)
                .define(DATA_MISSILE_CD, 0)
                .define(DATA_ESCORT, false)
                .define(DATA_RAIN_CD, 0)
                .define(DATA_WARP_CD, 0);
    }

    public int hull() { return entityData.get(DATA_HULL); }
    public void setHull(int v) { entityData.set(DATA_HULL, Mth.clamp(v, 0, MAX_HULL)); }
    public int energy() { return entityData.get(DATA_ENERGY); }
    public void setEnergy(int v) { entityData.set(DATA_ENERGY, Mth.clamp(v, 0, MAX_ENERGY)); }
    public boolean isShieldActive() { return entityData.get(DATA_SHIELD); }
    public void setShieldActive(boolean active) { entityData.set(DATA_SHIELD, active); }
    public boolean isEscortActive() { return entityData.get(DATA_ESCORT); }
    public void setEscortActive(boolean active) { entityData.set(DATA_ESCORT, active); }
    public boolean isHostileShip() { return entityData.get(DATA_HOSTILE); }
    public void setHostileShip(boolean hostile) {
        entityData.set(DATA_HOSTILE, hostile);
        bossBar.setVisible(hostile);
    }
    public int collapseTicks() { return entityData.get(DATA_COLLAPSE); }
    public float flightSpeed() { return entityData.get(DATA_SPEED); }
    public float bankRoll() { return entityData.get(DATA_BANK_ROLL); }
    public int cannonCooldown() { return entityData.get(DATA_CANNON_CD); }
    public int missileCooldown() { return entityData.get(DATA_MISSILE_CD); }
    public int rainFireCooldown() { return entityData.get(DATA_RAIN_CD); }
    public int warpCooldown() { return entityData.get(DATA_WARP_CD); }
    public void setOwnerId(UUID id) { this.ownerId = id; }
    public UUID ownerId() { return ownerId; }

    public boolean isSnapVulnerableTo(Player caster) {
        return isHostileShip() && collapseTicks() == 0;
    }

    public void triggerSnapCollapse(ServerPlayer caster) {
        if (collapseTicks() > 0) return;
        entityData.set(DATA_COLLAPSE, 1);
        setShieldActive(false);
        bossBar.setColor(BossEvent.BossBarColor.YELLOW);
        bossBar.setName(Component.literal("圣殿二号 · 正在化作宇宙星尘"));
        if (level() instanceof ServerLevel sl) {
            InfinityNetwork.broadcastCue(sl, caster.getUUID(), InfinityNetwork.CUE_SHIP_SNAP_COLLAPSE, getId(),
                    position(), position().add(0, 2.0, 0), 128.0);
        }
    }

    public void acceptPilotInput(ServerPlayer pilot, int bits, int mountId) {
        if (mountId != getId() || pilot != getFirstPassenger() || isHostileShip() || collapseTicks() > 0) return;
        this.activeControls = bits & (IN_FWD | IN_BACK | IN_LEFT | IN_RIGHT | IN_ASCEND | IN_DESCEND | IN_BOOST);
        this.lastControlTick = level().getGameTime();
        if ((bits & IN_CANNON) != 0) pendingCannon = true;
        if ((bits & IN_MISSILE) != 0) pendingMissile = true;
        if ((bits & IN_SHIELD_TOGGLE) != 0) pendingShieldToggle = true;
        if ((bits & IN_RAIN_FIRE) != 0) pendingRainFire = true;
        if ((bits & IN_ESCORT_TOGGLE) != 0) pendingEscortToggle = true;
        if ((bits & IN_WARP) != 0) pendingWarp = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (lerpSteps > 0) {
                lerpPositionAndRotationStep(lerpSteps--, lerpX, lerpY, lerpZ, lerpYaw, lerpPitch);
            }
            return;
        }

        ServerLevel sl = (ServerLevel) level();
        long now = sl.getGameTime();

        int col = collapseTicks();
        if (col > 0) {
            ejectPassengers();
            setDeltaMovement(0, 0.03, 0);
            move(MoverType.SELF, getDeltaMovement());
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 1.4, getZ(), 18, 2.8, 0.9, 2.8, 0.03);
            sl.sendParticles(ParticleTypes.ASH, getX(), getY() + 1.6, getZ(), 35, 3.2, 1.1, 3.2, 0.08);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1.5, getZ(), 10, 2.4, 0.8, 2.4, 0.04);
            bossBar.setProgress(Math.max(0f, 1f - col / 36f));
            if (col >= 36) {
                sl.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6f, 0.65f);
                spawnAtLocation(AcaInfinity.stoneItem(Stone.POWER), 1);
                discard();
                return;
            }
            entityData.set(DATA_COLLAPSE, col + 1);
            return;
        }

        if (cannonCooldown() > 0) entityData.set(DATA_CANNON_CD, cannonCooldown() - 1);
        if (missileCooldown() > 0) entityData.set(DATA_MISSILE_CD, missileCooldown() - 1);
        if (rainFireCooldown() > 0) entityData.set(DATA_RAIN_CD, rainFireCooldown() - 1);
        if (warpCooldown() > 0) entityData.set(DATA_WARP_CD, warpCooldown() - 1);

        if (isShieldActive()) {
            if (energy() >= 2) {
                setEnergy(energy() - 1);
            } else {
                setShieldActive(false);
            }
        } else if (energy() < MAX_ENERGY) {
            setEnergy(energy() + 4);
        }

        if (isHostileShip()) {
            tickHostileFlagship(sl, now);
            return;
        }

        Player pilot = getFirstPassenger() instanceof Player p && p.isAlive() ? p : null;
        if (pilot == null && isEscortActive()) {
            tickHoverAutoEscort(sl, now);
            return;
        }

        boolean liveInput = pilot != null && (now - lastControlTick) <= 8L;

        if (pendingShieldToggle && pilot != null) {
            pendingShieldToggle = false;
            setShieldActive(!isShieldActive());
            sl.playSound(null, blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.9f,
                    isShieldActive() ? 1.35f : 0.75f);
        }
        if (pendingEscortToggle && pilot != null) {
            pendingEscortToggle = false;
            setEscortActive(!isEscortActive());
            sl.playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.85f,
                    isEscortActive() ? 1.25f : 0.8f);
            pilot.displayClientMessage(Component.translatable(
                    isEscortActive() ? "msg.aca_infinity.ship_escort_on" : "msg.aca_infinity.ship_escort_off")
                    .withStyle(ChatFormatting.AQUA), true);
        }

        int bits = liveInput ? activeControls : 0;
        int fwd = ((bits & IN_FWD) != 0 ? 1 : 0) - ((bits & IN_BACK) != 0 ? 1 : 0);
        int steer = ((bits & IN_LEFT) != 0 ? 1 : 0) - ((bits & IN_RIGHT) != 0 ? 1 : 0);
        int vert = ((bits & IN_ASCEND) != 0 ? 1 : 0) - ((bits & IN_DESCEND) != 0 ? 1 : 0);
        boolean boost = (bits & IN_BOOST) != 0 && energy() > 20;

        if (pilot != null) {
            float yawDelta = -steer * 3.6f;
            float aimDiff = Mth.wrapDegrees(pilot.getYRot() - getYRot());
            if (steer == 0 && Math.abs(aimDiff) > 2.0f && fwd != 0) {
                yawDelta = Mth.clamp(aimDiff * 0.14f, -3.2f, 3.2f);
            }
            setYRot(Mth.wrapDegrees(getYRot() + yawDelta));
            setXRot(Mth.lerp(0.22f, getXRot(), Mth.clamp(pilot.getXRot() * 0.35f, -22f, 22f)));
            float targetBank = Mth.clamp(-yawDelta * 6.2f, -24f, 24f);
            entityData.set(DATA_BANK_ROLL, Mth.lerp(0.24f, bankRoll(), targetBank));
        } else {
            setXRot(Mth.lerp(0.2f, getXRot(), 0f));
            entityData.set(DATA_BANK_ROLL, Mth.lerp(0.2f, bankRoll(), 0f));
        }

        float maxSpd = boost ? 1.48f : 0.88f;
        float targetSpd = fwd * maxSpd * (fwd < 0 ? 0.45f : 1.0f);
        float nextSpd = Mth.approach(flightSpeed(), targetSpd, fwd == 0 ? 0.09f : 0.065f);
        entityData.set(DATA_SPEED, nextSpd);
        if (boost && Math.abs(nextSpd) > 0.2f) {
            setEnergy(Math.max(0, energy() - 2));
        }

        double rad = Math.toRadians(getYRot());
        double pitchRad = Math.toRadians(getXRot());
        double vx = -Math.sin(rad) * Math.cos(pitchRad) * nextSpd;
        double vz = Math.cos(rad) * Math.cos(pitchRad) * nextSpd;
        double vy = vert * 0.48 - Math.sin(pitchRad) * nextSpd * 0.55;
        if (pilot == null && !onGround()) {
            vy = -0.04;
        }

        move(MoverType.SELF, new Vec3(vx, vy, vz));
        setDeltaMovement(vx, vy, vz);

        if (now % 3L == 0L && (pilot != null || !onGround())) {
            Vec3 aft = position().add(localOffset(0, 1.4, -3.2));
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, aft.x, aft.y, aft.z, 3, 0.75, 0.18, 0.75, 0.02);
        }

        if (pilot instanceof ServerPlayer sp) {
            if (pendingCannon) {
                pendingCannon = false;
                fireTwinOrbitalCannons(sl, sp, sp.getLookAngle());
            }
            if (pendingMissile) {
                pendingMissile = false;
                fireWingMissileSalvo(sl, sp, sp.getLookAngle());
            }
            if (pendingRainFire) {
                pendingRainFire = false;
                triggerRainFire(sl, sp);
            }
            if (pendingWarp) {
                pendingWarp = false;
                triggerShortWarp(sl, sp);
            }
        } else {
            pendingCannon = false;
            pendingMissile = false;
            pendingRainFire = false;
            pendingWarp = false;
        }
    }

    /** [B] Hover Auto-Escort Mode: orbits 12m above owner and auto-fires cannons at nearby hostiles. */
    private void tickHoverAutoEscort(ServerLevel sl, long now) {
        ServerPlayer owner = ownerId != null ? sl.getServer().getPlayerList().getPlayer(ownerId) : null;
        if (owner == null || !owner.isAlive() || owner.level() != sl) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        double orbitAng = now * 0.022;
        Vec3 desired = owner.position().add(Math.cos(orbitAng) * 11.0, 12.0, Math.sin(orbitAng) * 11.0);
        Vec3 diff = desired.subtract(position());
        Vec3 vel = diff.length() > 0.65 ? diff.normalize().scale(0.48) : diff.scale(0.22);
        move(MoverType.SELF, vel);
        setDeltaMovement(vel);
        entityData.set(DATA_SPEED, (float) vel.horizontalDistance());

        LivingEntity hostile = sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(38.0),
                        e -> e.isAlive() && e instanceof Enemy && !StoneAbilities.isFriendlyTo(owner, e))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(this))).orElse(null);
        if (hostile != null) {
            Vec3 toFoe = hostile.getEyePosition().subtract(position().add(0, 1.5, 0));
            float targetYaw = (float) (Mth.atan2(toFoe.z, toFoe.x) * (180.0 / Math.PI)) - 90.0f;
            setYRot(Mth.rotLerp(0.18f, getYRot(), targetYaw));
            if (now % 35L == 0L) {
                fireTwinOrbitalCannons(sl, owner, toFoe.normalize());
            }
        } else if (vel.horizontalDistanceSqr() > 0.01) {
            float moveYaw = (float) (Mth.atan2(vel.z, vel.x) * (180.0 / Math.PI)) - 90.0f;
            setYRot(Mth.rotLerp(0.10f, getYRot(), moveYaw));
        }
        entityData.set(DATA_BANK_ROLL, Mth.sin(now * 0.04f) * 6f);
    }

    /** [V] Rain Fire: 8-warhead orbital saturation bombardment targeting ground/enemies ahead & below. */
    public boolean triggerRainFire(ServerLevel sl, ServerPlayer pilot) {
        if (rainFireCooldown() > 0 || energy() < 160) return false;
        setEnergy(energy() - 160);
        entityData.set(DATA_RAIN_CD, 140);

        Vec3 eye = pilot.getEyePosition();
        Vec3 look = pilot.getLookAngle();
        BlockHitResult hit = sl.clip(new ClipContext(eye, eye.add(look.scale(54.0)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 targetCenter = hit.getType() == HitResult.Type.MISS ? position().add(look.scale(28.0)).add(0, -10.0, 0) : hit.getLocation();

        for (int i = 0; i < 8; i++) {
            double ang = i * (Math.PI * 2.0 / 8.0);
            Vec3 launch = position().add(localOffset(Math.cos(ang) * 3.8, 0.9, Math.sin(ang) * 2.2));
            Vec3 spreadTarget = targetCenter.add(Math.cos(ang) * 4.5, 0.0, Math.sin(ang) * 4.5);
            Vec3 vel = spreadTarget.subtract(launch).normalize().scale(1.85);
            ShipOrdnanceEntity missile = AcaInfinity.SHIP_ORDNANCE.get().create(sl);
            if (missile != null) {
                missile.moveTo(launch.x, launch.y, launch.z, getYRot(), 45f);
                missile.configure(this, pilot, vel, false);
                sl.addFreshEntity(missile);
            }
        }

        // Direct orbital concussion damage on hostiles in target zone (routed through AcademyCompat.hurt!)
        for (LivingEntity foe : sl.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(targetCenter, 18.0, 12.0, 18.0), LivingEntity::isAlive)) {
            if (foe == pilot || foe.getVehicle() == this || StoneAbilities.isFriendlyTo(pilot, foe)) continue;
            AcademyCompat.hurt(foe, sl.damageSources().explosion(pilot, pilot), 85.0f, true);
        }

        sl.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.3f, 0.75f);
        InfinityNetwork.broadcastCue(sl, pilot.getUUID(), InfinityNetwork.CUE_SHIP_RAIN_FIRE, getId(),
                position(), targetCenter, 128.0);
        pilot.displayClientMessage(Component.translatable("msg.aca_infinity.ship_rain_fire")
                .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD), true);
        return true;
    }

    /** [X] Short-Range Warp Dash: instant 18-block forward micro-warp with lensing flash. */
    public boolean triggerShortWarp(ServerLevel sl, ServerPlayer pilot) {
        if (warpCooldown() > 0 || energy() < 100) return false;
        setEnergy(energy() - 100);
        entityData.set(DATA_WARP_CD, 100);

        Vec3 from = position();
        Vec3 dir = pilot.getLookAngle().normalize();
        BlockHitResult hit = sl.clip(new ClipContext(from.add(0, 1.8, 0), from.add(0, 1.8, 0).add(dir.scale(18.0)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 dest = hit.getType() == HitResult.Type.MISS
                ? from.add(dir.scale(18.0))
                : hit.getLocation().subtract(dir.scale(3.0));
        setPos(dest.x, dest.y, dest.z);
        sl.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.15f, 0.82f);
        InfinityNetwork.broadcastCue(sl, pilot.getUUID(), InfinityNetwork.CUE_SHIP_WARP, getId(), from, dest, 128.0);
        pilot.displayClientMessage(Component.translatable("msg.aca_infinity.ship_warp")
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    private void tickHostileFlagship(ServerLevel sl, long now) {
        bossBar.setProgress(hull() / (float) MAX_HULL);
        setShieldActive(now % 160L < 50L);

        Player target = sl.getNearestPlayer(this, 64.0);
        if (target != null && target.isAlive() && !target.isSpectator()) {
            double orbitAngle = now * 0.032;
            Vec3 desired = target.position().add(Math.cos(orbitAngle) * 18.0, 11.5, Math.sin(orbitAngle) * 18.0);
            Vec3 step = desired.subtract(position());
            Vec3 vel = step.length() > 0.55 ? step.normalize().scale(0.42) : step.scale(0.2);
            move(MoverType.SELF, vel);

            Vec3 toTarget = target.getEyePosition().subtract(position().add(0, 1.2, 0));
            float targetYaw = (float) (Mth.atan2(toTarget.z, toTarget.x) * (180.0 / Math.PI)) - 90.0f;
            setYRot(Mth.rotLerp(0.16f, getYRot(), targetYaw));
            entityData.set(DATA_BANK_ROLL, Mth.sin(now * 0.05f) * 10f);

            if (now % 55L == 0L && toTarget.lengthSqr() > 4.0) {
                fireTwinOrbitalCannons(sl, null, toTarget.normalize());
            }
            if (now % 90L == 35L && toTarget.lengthSqr() > 4.0) {
                fireWingMissileSalvo(sl, null, toTarget.normalize());
            }
        }
    }

    public boolean fireTwinOrbitalCannons(ServerLevel sl, ServerPlayer pilot, Vec3 aimDir) {
        if (cannonCooldown() > 0 || energy() < 35) return false;
        setEnergy(energy() - 35);
        entityData.set(DATA_CANNON_CD, 18);

        Vec3 dir = aimDir.lengthSqr() < 1e-4 ? localOffset(0, 0, 1).normalize() : aimDir.normalize();
        for (double side : new double[]{-1.10, 1.10}) {
            Vec3 muzzle = position().add(localOffset(side, 1.45, 4.2));
            Vec3 maxEnd = muzzle.add(dir.scale(60.0));
            BlockHitResult hit = sl.clip(new ClipContext(muzzle, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            Vec3 impact = hit.getType() == HitResult.Type.MISS ? maxEnd : hit.getLocation();

            AABB beamBox = new AABB(muzzle, impact).inflate(2.4);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, beamBox, LivingEntity::isAlive)) {
                if (e == pilot || e.getVehicle() == this) continue;
                if (pilot != null && StoneAbilities.isFriendlyTo(pilot, e)) continue;
                if (pilot == null && !(e instanceof Player)) continue;
                Vec3 p = e.getBoundingBox().getCenter();
                double t = Mth.clamp(p.subtract(muzzle).dot(impact.subtract(muzzle))
                        / Math.max(0.01, impact.distanceToSqr(muzzle)), 0.0, 1.0);
                if (p.distanceToSqr(muzzle.lerp(impact, t)) <= 7.5 || p.distanceToSqr(impact) <= 18.0) {
                    DamageSource src = pilot != null ? sl.damageSources().playerAttack(pilot) : sl.damageSources().magic();
                    AcademyCompat.hurt(e, src, pilot != null ? 72.0f : 12.0f, true);
                }
            }

            sl.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 2, 0.4, 0.4, 0.4, 0.0);
            InfinityNetwork.broadcastCue(sl, pilot != null ? pilot.getUUID() : getUUID(),
                    InfinityNetwork.CUE_SHIP_CANNON, getId(), muzzle, impact, 96.0);
        }
        sl.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.95f, 1.25f);
        return true;
    }

    public boolean fireWingMissileSalvo(ServerLevel sl, ServerPlayer pilot, Vec3 aimDir) {
        if (missileCooldown() > 0 || energy() < 50) return false;
        setEnergy(energy() - 50);
        entityData.set(DATA_MISSILE_CD, 36);

        Vec3 dir = aimDir.lengthSqr() < 1e-4 ? localOffset(0, 0, 1).normalize() : aimDir.normalize();
        double[] wingOffsets = {-3.4, -2.1, 2.1, 3.4};
        for (int i = 0; i < wingOffsets.length; i++) {
            ShipOrdnanceEntity missile = AcaInfinity.SHIP_ORDNANCE.get().create(sl);
            if (missile == null) continue;
            Vec3 pod = position().add(localOffset(wingOffsets[i], 1.15, 1.4));
            missile.moveTo(pod.x, pod.y, pod.z, getYRot(), getXRot());
            missile.configure(this, pilot, dir.add((i - 1.5) * 0.04, -0.02, 0).normalize().scale(1.60), isHostileShip());
            sl.addFreshEntity(missile);
        }
        sl.playSound(null, blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.15f, 0.72f);
        return true;
    }

    public Vec3 localOffset(double x, double y, double z) {
        return new Vec3(x, y, z).yRot((float) -Math.toRadians(getYRot()));
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSpectator() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (isHostileShip() || collapseTicks() > 0) return InteractionResult.PASS;

        ItemStack held = player.getItemInHand(hand);
        if (level().isClientSide) return InteractionResult.SUCCESS;

        if (player.isSecondaryUseActive() && !isVehicle()) {
            if (ownerId != null && !ownerId.equals(player.getUUID()) && !player.isCreative()) {
                return InteractionResult.FAIL;
            }
            ItemStack beacon = new ItemStack(AcaInfinity.SANCTUARY_BEACON.get());
            CustomData.update(DataComponents.CUSTOM_DATA, beacon, tag -> {
                tag.putInt("Hull", hull());
                tag.putInt("Energy", energy());
            });
            if (!player.getInventory().add(beacon)) player.drop(beacon, false);
            level().playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9f, 1.15f);
            discard();
            return InteractionResult.CONSUME;
        }

        if (held.is(Items.IRON_INGOT) || held.is(Items.GOLD_INGOT) || held.is(Items.NETHERITE_INGOT)) {
            if (hull() < MAX_HULL) {
                int heal = held.is(Items.NETHERITE_INGOT) ? 400 : held.is(Items.GOLD_INGOT) ? 160 : 100;
                setHull(hull() + heal);
                if (!player.getAbilities().instabuild) held.shrink(1);
                level().playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 0.9f, 1.1f);
                return InteractionResult.CONSUME;
            }
        }

        if (!isVehicle()) {
            player.startRiding(this);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || collapseTicks() > 0 || amount <= 0f) return false;
        if (source.getEntity() != null && source.getEntity().getVehicle() == this) return false;
        if (!level().isClientSide) {
            float finalDmg = isShieldActive() ? amount * 0.35f : amount;
            int loss = Math.max(1, Mth.ceil(finalDmg));
            if (loss >= hull()) {
                ejectPassengers();
                level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 1.4f, 0.7f);
                if (!isHostileShip()) {
                    ItemStack beacon = new ItemStack(AcaInfinity.SANCTUARY_BEACON.get());
                    CustomData.update(DataComponents.CUSTOM_DATA, beacon, t -> t.putInt("Hull", MAX_HULL / 2));
                    spawnAtLocation(beacon);
                } else {
                    spawnAtLocation(AcaInfinity.stoneItem(Stone.SPACE), 1);
                }
                discard();
            } else {
                setHull(hull() - loss);
                level().playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_HURT, SoundSource.NEUTRAL, 0.8f, 0.72f);
            }
        }
        return true;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (isHostileShip()) bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override public boolean isPickable() { return isAlive(); }
    @Override public boolean canBeCollidedWith() { return !isRemoved(); }
    @Override public boolean causeFallDamage(float dist, float mult, DamageSource src) { return false; }
    @Override protected boolean canAddPassenger(Entity p) { return p instanceof Player && !isHostileShip() && !isVehicle(); }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float partial) {
        // Elevated & forward-shifted bridge deck eye position (clears hull cleanly for 3A holographic canopy!)
        return localOffset(0, 2.55, 0.95).add(passenger.getVehicleAttachmentPoint(this));
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity rider) {
        Vec3 side = position().add(localOffset(4.5, 0.5, 0.0));
        BlockPos bp = BlockPos.containing(side);
        Vec3 safe = DismountHelper.findSafeDismountLocation(rider.getType(), level(), bp, true);
        if (safe != null) return safe;
        rider.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false, true));
        return position().add(0, 3.6, 0);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYaw = yaw;
        this.lerpPitch = pitch;
        this.lerpSteps = 3;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(12.0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Hull")) setHull(tag.getInt("Hull"));
        if (tag.contains("Energy")) setEnergy(tag.getInt("Energy"));
        setShieldActive(tag.getBoolean("Shield"));
        setEscortActive(tag.getBoolean("Escort"));
        setHostileShip(tag.getBoolean("Hostile"));
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Hull", hull());
        tag.putInt("Energy", energy());
        tag.putBoolean("Shield", isShieldActive());
        tag.putBoolean("Escort", isEscortActive());
        tag.putBoolean("Hostile", isHostileShip());
        if (ownerId != null) tag.putUUID("Owner", ownerId);
    }
}
