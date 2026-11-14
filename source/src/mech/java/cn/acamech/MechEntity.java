package cn.acamech;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import static cn.acamech.MechMod.Controls.*;

/** The server owns the vehicle's movement, hit checks, and stored state. */
public final class MechEntity extends Entity {
    private static final EntityDataAccessor<Integer> HULL = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CHARGE = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> AUXILIARY = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> STRIKE_AGE = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STRIKE_RESULT = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FIRE_AGE = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COOLDOWN = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FIRE_RESULT = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> AIM_PITCH = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AIM_YAW = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WALK_CYCLE = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RUN_BLEND = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DRIVE_LOAD = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TURN_RATE = SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<Footwork.Frame>> SOLES =
            SynchedEntityData.defineId(MechEntity.class, MechMod.FEET.get());
    private static final EntityDataAccessor<Float> BRAKE_BLEND =
            SynchedEntityData.defineId(MechEntity.class, EntityDataSerializers.FLOAT);
    private static final byte LAND = 74;
    private static final int LAND_TICKS = 14;
    private static final ResourceLocation ACADEMY_CELL = ResourceLocation.fromNamespaceAndPath("academy", "heavy_cell");
    private final Chassis chassis;
    private UUID owner;
    private UUID pilotId;
    private long lastInput = Long.MIN_VALUE;
    private int controls;
    private boolean strikeRequested, fireRequested;
    private UUID strikePilot, firePilot;
    private int powerTick, landingTicks, rangedFeedbackTicks;
    private int liftRemaining;
    private boolean liftReady = true;
    private double airDrop;
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    private float lerpYaw;
    private float gaitPhase, phaseOld, gaitWeight, weightOld, auxBlend, auxOld;
    private final Footwork footsteps = new Footwork();
    private Footwork.Frame feet, feetOld;
    private float groundBlend, groundOld, brake, brakeOld;
    private float turning, turningOld, walkingSign = 1f;
    private float runBlend, runOld, driveLoad, loadOld, landingStrength;
    private List<Vec3> strikeImpacts = List.of();
    private int impactReceived = -MechAttack.IMPACT_LIFE;
    private float impactYaw;

    public MechEntity(EntityType<MechEntity> type, Level level, Chassis chassis) {
        super(type, level);
        this.chassis = chassis;
        this.blocksBuilding = true;
        setHull(chassis.hull);
        setCharge(chassis.energy);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(HULL, 1).define(CHARGE, 0).define(SPEED, 0f).define(AUXILIARY, false)
                .define(STRIKE_AGE, -1).define(STRIKE_RESULT, -1)
                .define(FIRE_AGE, -1).define(COOLDOWN, 0).define(FIRE_RESULT, RangedWeapon.PENDING)
                .define(AIM_PITCH, 0f).define(AIM_YAW, 0f).define(WALK_CYCLE, 0f)
                .define(RUN_BLEND, 0f).define(DRIVE_LOAD, 0f).define(TURN_RATE, 0f)
                .define(SOLES, Optional.empty()).define(BRAKE_BLEND, 0f);
    }

    public Chassis chassis() { return chassis; }
    public int hull() { return entityData.get(HULL); }
    public int charge() { return entityData.get(CHARGE); }
    public float driveSpeed() { return entityData.get(SPEED); }
    public boolean auxiliary() { return entityData.get(AUXILIARY); }
    public int strikeAge() { return entityData.get(STRIKE_AGE); }
    /** -1 until contact, then the number of targets hit. */
    public int strikeResult() { return entityData.get(STRIKE_RESULT); }
    public int rangedAge() { return entityData.get(FIRE_AGE); }
    public int rangedCooldown() { return entityData.get(COOLDOWN); }
    public int rangedResult() { return entityData.get(FIRE_RESULT); }
    public float rangedPitch() { return entityData.get(AIM_PITCH); }
    public float rangedYaw() { return entityData.get(AIM_YAW); }
    public float rangedImpactAge(float partial) { return 10 - rangedFeedbackTicks + partial; }
    public boolean rangedHitMarker() { return rangedFeedbackTicks > 0 && rangedResult() > 0; }
    public boolean canAim(Player pilot) { return RangedCombat.canAim(this, pilot); }
    public Vec3 rangedMuzzle(int index) {
        Vec3 muzzle = chassis.ranged.muzzle(index);
        return position().add(local(muzzle.x, muzzle.y, muzzle.z));
    }
    void setRangedResult(int result) { entityData.set(FIRE_RESULT, result); }

    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key.equals(FIRE_RESULT) && level().isClientSide()) rangedFeedbackTicks = rangedResult() > 0 ? 10 : 0;
    }
    public List<Vec3> strikeImpacts() { return strikeImpacts; }
    public float strikeImpactAge(float partial) { return tickCount - impactReceived + partial; }
    public float strikeImpactYaw() { return impactYaw; }

    void showStrikeImpact(List<Vec3> positions, float yaw) {
        strikeImpacts = positions;
        impactReceived = tickCount;
        impactYaw = yaw;
    }
    public Footwork.Frame feet() { return feet; }
    public Footwork.Sole sole(boolean left, float partial) {
        if (feet == null) return null;
        Footwork.Sole current = left ? feet.left() : feet.right();
        Footwork.Frame previous = feetOld == null ? feet : feetOld;
        return (left ? previous.left() : previous.right()).lerp(current, partial);
    }
    public float footing(float partial) { return Mth.lerp(partial, groundOld, groundBlend); }
    public float braking(float partial) { return Mth.lerp(partial, brakeOld, brake); }

    public float phase(float tick) { return Mth.lerp(tick, phaseOld, gaitPhase); }
    public float gait(float tick) { return Mth.lerp(tick, weightOld, gaitWeight); }
    public float turning(float tick) { return Mth.lerp(tick, turningOld, turning); }
    public float walkingSign() { return walkingSign; }
    public float run(float partial) { return Mth.lerp(partial, runOld, runBlend); }
    public boolean running() { return entityData.get(RUN_BLEND) >= .44f; }
    public float driveLoad(float partial) { return Mth.lerp(partial, loadOld, driveLoad); }
    public float landing(float partial) {
        if (landingTicks == 0) return 0;
        float age = LAND_TICKS - landingTicks + partial;
        // Fast compression, slower oil-damped return and a small second bounce.
        float up = Mth.clamp((age + .5f) / 2.7f, 0, 1);
        float out = Mth.clamp((age - 2.2f) / 10f, 0, 1);
        float bounce = Mth.clamp(1 - Math.abs(age - 9) / 4f, 0, 1) * .105f;
        return landingStrength * (up * (1 - out) * (1 - out) + bounce);
    }
    public float auxBlend(float tick) { return Mth.lerp(tick, auxOld, auxBlend); }
    void setOwner(UUID owner) { this.owner = owner; }
    private void setHull(int value) { entityData.set(HULL, Mth.clamp(value, 1, chassis.hull)); }
    private void setCharge(int value) { entityData.set(CHARGE, Mth.clamp(value, 0, chassis.energy)); }

    public void acceptControls(Player player, byte bits, int mount) {
        if (getId() != mount || player != getFirstPassenger() || !player.isAlive() || player.isSpectator()) return;
        if (player.getVehicle() != this) return;
        pilotId = player.getUUID();
        lastInput = level().getGameTime();
        controls = bits & (FORWARD | BACK | LEFT | RIGHT | AUX | RUN);
        strikeRequested |= (bits & STRIKE) != 0;
        fireRequested |= (bits & FIRE) != 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (!strikeImpacts.isEmpty() && tickCount - impactReceived >= MechAttack.IMPACT_LIFE) strikeImpacts = List.of();
            if (landingTicks > 0) landingTicks--;
            if (rangedFeedbackTicks > 0) rangedFeedbackTicks--;
            if (lerpSteps > 0) {
                lerpPositionAndRotationStep(lerpSteps--, lerpX, lerpY, lerpZ, lerpYaw, 0);
            }
            phaseOld = gaitPhase;
            weightOld = gaitWeight;
            auxOld = auxBlend;
            turningOld = turning;
            runOld = runBlend;
            loadOld = driveLoad;
            brakeOld = brake;
            brake = entityData.get(BRAKE_BLEND);
            groundOld = groundBlend;
            feetOld = feet;
            var tracked = entityData.get(SOLES);
            groundBlend = Mth.approach(groundBlend, tracked.isPresent() ? 1f : 0f, tracked.isPresent() ? .25f : .34f);
            if (tracked.isPresent()) {
                feet = tracked.get();
                if (groundOld == 0 || feetOld == null || feetOld.left().point().distanceToSqr(feet.left().point()) > 9
                        || feetOld.right().point().distanceToSqr(feet.right().point()) > 9) feetOld = feet;
            } else if (groundBlend == 0) {
                feet = null;
                feetOld = null;
            }
            float speed = driveSpeed();
            gaitPhase = entityData.get(WALK_CYCLE);
            if (gaitPhase < phaseOld - .5f) phaseOld -= 1f;
            turning += (entityData.get(TURN_RATE) / chassis.turn - turning) * .52f;
            runBlend = entityData.get(RUN_BLEND);
            driveLoad += (entityData.get(DRIVE_LOAD) - driveLoad) * .62f;
            if (Math.abs(speed) > .02f) walkingSign += (Math.signum(speed) - walkingSign) * .32f;
            float effort = (float) Math.abs(speed / (chassis.speed * .85));
            float weight = onGround() ? Math.min(1f, Math.max(effort, Math.abs(turning) * .76f)) : 0f;
            gaitWeight += (weight - gaitWeight) * (weight < gaitWeight ? .23f : .44f);
            auxBlend += ((auxiliary() ? 1f : 0f) - auxBlend) * .28f;
            return;
        }

        Player pilot = getFirstPassenger() instanceof Player p && p.isAlive() && !p.isSpectator() ? p : null;
        boolean liveControl = pilot != null && pilot.getUUID().equals(pilotId) && level().getGameTime() - lastInput <= 8;
        updateStrike(pilot, liveControl);
        updateRanged(pilot, liveControl);
        int flags = charge() > 0 && liveControl ? controls : 0;
        boolean auxHeld = (flags & AUX) != 0;
        int fwd = bit(flags, FORWARD) - bit(flags, BACK);
        int steer = bit(flags, LEFT) - bit(flags, RIGHT);
        boolean swinging = strikeAge() >= 0;
        boolean aiming = rangedAge() >= 0;
        boolean brace = chassis == Chassis.ATLAS && auxHeld && onGround() && !swinging && !aiming;
        boolean sprint = chassis == Chassis.AETHER && auxHeld && fwd > 0 && onGround() && !isInWater() && !swinging && !aiming;
        if (chassis == Chassis.SERAPH) {
            if (onGround() && !auxHeld) liftReady = true;
            if (auxHeld && onGround() && liftReady) {
                liftRemaining = 23;
                liftReady = false;
            }
            if (!auxHeld || pilot == null) liftRemaining = 0;
        }
        boolean lift = chassis == Chassis.SERAPH && auxHeld && liftRemaining > 0 && charge() >= 4 && !isInWater();
        entityData.set(AUXILIARY, brace || sprint || lift);

        boolean wantsRun = fwd > 0 && ((flags & RUN) != 0 || sprint) && onGround() && !isInWater()
                && !brace && !lift && !swinging && !aiming;
        float previousSpeed = driveSpeed();
        float speedLimit = (float) (chassis.speed * (aiming ? .35 : swinging ? chassis.attack.movement
                : sprint ? 1.61 : brace ? .28 : wantsRun ? chassis.runMultiplier : 1));
        if (isInWater()) speedLimit *= .46f;
        float target = fwd * speedLimit * (fwd < 0 ? .48f : 1);
        float grip = onGround() ? 1f : .61f;
        float fromRest = Mth.clamp(Math.abs(previousSpeed) / .14f, 0, 1);
        float braking = (float) (chassis.acceleration * (liveControl ? 1.20 : 2.4));
        float throttle = (float) (chassis.acceleration * (.48f + fromRest * .60f));
        boolean slowing = Math.abs(target) < Math.abs(previousSpeed) || target * previousSpeed < 0;
        float speed = Mth.approach(previousSpeed, target, (slowing ? braking : throttle) * grip);
        // The chassis takes weight before the first stride and pitches forward when braking.
        float load = (float) Mth.clamp((speed - previousSpeed) / chassis.acceleration, -1, 1);
        entityData.set(DRIVE_LOAD, onGround() && !swinging && !aiming ? load : 0f);
        float runTarget = wantsRun ? Mth.clamp((speed / (float) chassis.speed - .65f) / .64f, 0, 1) : 0f;
        float run = Mth.approach(entityData.get(RUN_BLEND), runTarget, wantsRun ? .115f : .127f);
        boolean brakingRun = onGround() && !swinging && !aiming && fwd <= 0 && previousSpeed > chassis.speed * .83
                && (run > .20f || entityData.get(BRAKE_BLEND) > .20f);
        float stopPose = entityData.get(BRAKE_BLEND);
        stopPose = Mth.approach(stopPose, brakingRun ? 1f : 0f, brakingRun ? .43f : .12f);
        entityData.set(BRAKE_BLEND, stopPose);
        entityData.set(RUN_BLEND, run);
        float demand = -steer * chassis.turn * (brace || aiming ? .63f : 1f) * (1 - .29f * run) * grip;
        float oldTurn = entityData.get(TURN_RATE);
        float turn = !liveControl || swinging ? 0f : Mth.lerp(Mth.clamp(chassis.turnResponse
                * (steer == 0 ? 1.85f : 1f), 0, 1), oldTurn, demand);
        if (Math.abs(turn) < .035f) turn = 0;
        entityData.set(TURN_RATE, turn);
        if (turn != 0) setYRot(Mth.wrapDegrees(getYRot() + turn));
        double radians = Math.toRadians(getYRot());
        double vy = getDeltaMovement().y;
        if (lift) {
            vy = Math.min(.30, Math.max(.09, vy + .08));
            liftRemaining--;
            setCharge(charge() - 4);
        } else if (isNoGravity()) {
            vy = 0;
        } else {
            vy = Math.max(chassis == Chassis.SERAPH && pilot != null ? -.34 : -.90, vy - (isInWater() ? .035 : .08));
        }
        Vec3 before = position();
        boolean grounded = onGround();
        move(MoverType.SELF, new Vec3(-Math.sin(radians) * speed, vy, Math.cos(radians) * speed));
        double moved = Math.hypot(getX() - before.x, getZ() - before.z);
        entityData.set(SPEED, (float) (Math.copySign(moved, speed)));
        setDeltaMovement(0, verticalCollision || onGround() ? 0 : vy * .97, 0);

        if (lift && level() instanceof ServerLevel server && tickCount % 3 == 0) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + .5, getZ(),
                    9, .65, .25, .56, .06);
        }
        if (charge() > 0 && (moved > .004 || steer != 0 || brace)) {
            if (++powerTick >= 5) {
                setCharge(charge() - (sprint ? 4 : brace || wantsRun ? 2 : 1));
                powerTick = 0;
            }
        }
        if (!grounded) airDrop = before.y > getY() ? airDrop + before.y - getY() : 0;
        if (onGround()) {
            boolean landed = !grounded && vy < -.255 && airDrop > .7 && !isInWater();
            if (landed) land(-vy, airDrop);
            airDrop = 0;
        }
        Footwork.Frame frame = footsteps.update(this, before, driveSpeed(), turn, run,
                onGround() && !isInWater() && !swinging && !aiming, fwd == 0 && steer == 0);
        entityData.set(SOLES, Optional.ofNullable(frame));
        if (frame != null) entityData.set(WALK_CYCLE, footsteps.cycle());
        if (strikeAge() == chassis.attack.contactTick) resolveStrike(pilot);
    }

    private Vec3 footPoint(int side, float forward) {
        float spread = switch (chassis) { case ATLAS -> .24f; case AETHER -> .25f; case SERAPH -> .22f; };
        return position().add(local(side * chassis.width * spread, .10, forward));
    }

    private void land(double impactSpeed, double distance) {
        // SERAPH limits descent speed; distance still loads its feet and hinge dampers on a drop.
        int tier = impactSpeed > .54 || distance > 3.8 ? 2 : impactSpeed > .35 || distance > 1.55 ? 1 : 0;
        level().broadcastEntityEvent(this, (byte) (LAND + tier));
        float gain = (chassis == Chassis.ATLAS ? 1.38f : 1.05f) + tier * .18f;
        footstep(position(), gain);
        groundDust(footPoint(-1, .15f), 7 + tier * 3, .44);
        groundDust(footPoint(1, .15f), 7 + tier * 3, .44);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.HEAVY_CORE_FALL, SoundSource.NEUTRAL,
                gain * .83f, chassis == Chassis.ATLAS ? .57f : .74f);
    }

    void touchDown(Vec3 point, float run, boolean braking) {
        footstep(point.add(0, .06, 0), .79f + .48f * run);
        if (run > .26f || braking || chassis == Chassis.ATLAS) {
            groundDust(point.add(0, .1, 0), (int) (2 + 5 * run + (braking ? 2 : 0)), .19 + run * .09);
        }
    }

    private void groundDust(Vec3 point, int count, double width) {
        if (!(level() instanceof ServerLevel server)) return;
        BlockPos under = BlockPos.containing(point.x, point.y - .22, point.z);
        var surface = level().getBlockState(under);
        if (!surface.isAir() && surface.getFluidState().isEmpty()) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, surface),
                    point.x, point.y + .05, point.z, count, width, .11, width, .11);
        }
    }

    private void footstep(Vec3 point, float gain) {
        float pitch = switch (chassis) { case ATLAS -> .57f; case AETHER -> 1.11f; case SERAPH -> .85f; };
        level().playSound(null, point.x, point.y, point.z, SoundEvents.IRON_GOLEM_STEP, SoundSource.NEUTRAL,
                gain * (chassis == Chassis.ATLAS ? .84f : .62f), pitch);
        if (chassis != Chassis.AETHER || gain > 1.05f) level().playSound(null, point.x, point.y, point.z, SoundEvents.CHAIN_STEP,
                SoundSource.NEUTRAL, gain * .29f, pitch + .1f);
    }

    @Override public void handleEntityEvent(byte event) {
        if (event >= LAND && event <= LAND + 2) {
            landingTicks = LAND_TICKS;
            landingStrength = .57f + (event - LAND) * .215f;
        } else super.handleEntityEvent(event);
    }

    private void updateRanged(Player pilot, boolean liveControl) {
        var weapon = chassis.ranged;
        int cooldown = rangedCooldown();
        if (cooldown > 0) {
            entityData.set(COOLDOWN, cooldown - 1);
            if (cooldown == 1) setRangedResult(RangedWeapon.PENDING);
        }
        int age = rangedAge();
        if (age >= 0) {
            boolean samePilot = liveControl && pilot.getUUID().equals(firePilot);
            if (!samePilot || ++age >= weapon.windup + RangedWeapon.RECOVERY) {
                entityData.set(FIRE_AGE, -1);
                firePilot = null;
            } else {
                entityData.set(FIRE_AGE, age);
                if (age <= weapon.windup) {
                    entityData.set(AIM_PITCH, Mth.clamp(pilot.getXRot(), RangedCombat.PITCH_UP, RangedCombat.PITCH_DOWN));
                    entityData.set(AIM_YAW, Mth.clamp(Mth.wrapDegrees(pilot.getYRot() - getYRot()),
                            -RangedCombat.YAW_LIMIT, RangedCombat.YAW_LIMIT));
                }
                if (age == weapon.windup) {
                    if (charge() >= weapon.charge && RangedCombat.canAim(this, pilot)) {
                        setCharge(charge() - weapon.charge);
                        entityData.set(COOLDOWN, weapon.cooldown);
                        RangedCombat.fire(this, pilot);
                    } else {
                        setRangedResult(RangedWeapon.ABORTED);
                        entityData.set(COOLDOWN, 12);
                        attackSound(position(), SoundEvents.COPPER_BULB_TURN_OFF, .6f, .8f);
                    }
                } else if (age == weapon.windup - 3) {
                    attackSound(rangedMuzzle(0), SoundEvents.IRON_TRAPDOOR_CLOSE,
                            weapon == RangedWeapon.PULSE ? .82f : .49f, weapon == RangedWeapon.NEEDLE ? 1.5f : .87f);
                } else if (age == weapon.windup + 3 && rangedResult() != RangedWeapon.ABORTED) {
                    exhaustSound();
                } else if (age == weapon.windup + 7 && rangedResult() != RangedWeapon.ABORTED) {
                    attackSound(rangedMuzzle(0), SoundEvents.PISTON_CONTRACT, .54f, weapon == RangedWeapon.PULSE ? .76f : 1.21f);
                }
            }
        }
        if (fireRequested && liveControl && rangedAge() < 0 && rangedCooldown() == 0 && strikeAge() < 0
                && charge() >= weapon.charge) {
            if (RangedCombat.canAim(this, pilot)) {
                firePilot = pilot.getUUID();
                entityData.set(FIRE_AGE, 0);
                entityData.set(AIM_PITCH, pilot.getXRot());
                entityData.set(AIM_YAW, Mth.wrapDegrees(pilot.getYRot() - getYRot()));
                setRangedResult(RangedWeapon.PENDING);
                attackSound(position(), weapon == RangedWeapon.PULSE ? SoundEvents.PISTON_EXTEND : SoundEvents.CHAIN_HIT,
                        1.02f, weapon == RangedWeapon.PULSE ? .68f : 1.36f);
                attackSound(position(), SoundEvents.BEACON_POWER_SELECT, .70f, weapon == RangedWeapon.CONVERGENCE ? 1.45f : .89f);
            } else {
                setRangedResult(RangedWeapon.ABORTED);
                entityData.set(COOLDOWN, 12);
                attackSound(position(), SoundEvents.COPPER_BULB_TURN_OFF, .6f, .8f);
            }
        }
        fireRequested = false;
    }

    private static int bit(int mask, int flag) { return (mask & flag) == 0 ? 0 : 1; }

    private void updateStrike(Player pilot, boolean liveControl) {
        var attack = chassis.attack;
        int age = strikeAge();
        if (age >= 0) {
            boolean samePilot = liveControl && pilot.getUUID().equals(strikePilot);
            if (++age >= attack.duration || !samePilot) {
                entityData.set(STRIKE_AGE, -1);
                entityData.set(STRIKE_RESULT, -1);
                strikePilot = null;
            } else {
                entityData.set(STRIKE_AGE, age);
                if (age == attack.contactTick + 3) {
                    exhaustSound();
                } else if (age == attack.contactTick + 7) {
                    attackSound(position(), SoundEvents.PISTON_CONTRACT, .46f,
                            attack == MechAttack.HYDRAULIC ? .72f : 1.22f);
                } else if (age == attack.contactTick - 2) {
                    attackSound(position(), SoundEvents.IRON_TRAPDOOR_CLOSE, .49f,
                            attack == MechAttack.HYDRAULIC ? .68f : 1.16f);
                }
            }
        }
        if (strikeRequested && liveControl && strikeAge() < 0 && rangedAge() < 0 && charge() >= MechAttack.CHARGE_COST) {
            entityData.set(STRIKE_AGE, 0);
            strikePilot = pilot.getUUID();
            setCharge(charge() - MechAttack.CHARGE_COST);
            switch (attack) {
                case HYDRAULIC -> {
                    attackSound(position(), SoundEvents.PISTON_EXTEND, 1.05f, .67f);
                    attackSound(position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, .65f, .8f);
                }
                case SWEEP -> {
                    attackSound(position(), SoundEvents.CHAIN_HIT, .96f, 1.26f);
                    attackSound(position(), SoundEvents.BEACON_POWER_SELECT, .54f, 1.5f);
                }
                case WINGS -> {
                    attackSound(position(), SoundEvents.IRON_GOLEM_REPAIR, .77f, 1.22f);
                    attackSound(position(), SoundEvents.BEACON_POWER_SELECT, .74f, 1.36f);
                }
            }
        }
        strikeRequested = false;
    }

    private void resolveStrike(Player pilot) {
        var attack = chassis.attack;
        Vec3 direction = local(0, 0, 1);
        Vec3 origin = position().add(0, Math.min(1.7, chassis.height * .29), 0);
        List<LivingEntity> targets = level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(chassis.reach - chassis.width * .5, 0, chassis.reach - chassis.width * .5),
                e -> e instanceof Enemy && e.isAlive() && !e.isPassengerOfSameVehicle(this));
        targets.sort(Comparator.comparingDouble(this::distanceToSqr));
        var positions = new ArrayList<Vec3>(MechAttack.MAX_VISIBLE_HITS);
        int hits = 0;
        for (LivingEntity target : targets) {
            Vec3 p = target.getBoundingBox().getCenter();
            Vec3 rel = new Vec3(p.x - getX(), 0, p.z - getZ());
            double dot = rel.dot(direction);
            if (dot <= 0 || rel.lengthSqr() > chassis.reach * chassis.reach || dot < rel.length() * attack.coneCos) continue;
            if (level().clip(new ClipContext(origin, p, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType()
                    != HitResult.Type.MISS) continue;
            if (target.hurt(damageSources().playerAttack(pilot), chassis.power)) {
                hits++;
                if (positions.size() < MechAttack.MAX_VISIBLE_HITS) {
                    positions.add(p.add(0, target.getBbHeight() * .13, 0));
                }
                Vec3 away = rel.normalize();
                target.knockback(attack.knockback, -away.x, -away.z);
            }
        }
        entityData.set(STRIKE_RESULT, hits);
        Vec3 impactSound = hits == 0 ? position().add(direction) : positions.getFirst();
        switch (attack) {
            case HYDRAULIC -> {
                attackSound(position(), SoundEvents.IRON_GOLEM_ATTACK, .96f, .63f);
                if (hits > 0) attackSound(impactSound, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.10f, .73f);
            }
            case SWEEP -> {
                attackSound(position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.05f, 1.34f);
                if (hits > 0) attackSound(impactSound, SoundEvents.COPPER_HIT, 1.19f, .84f);
            }
            case WINGS -> {
                attackSound(position(), SoundEvents.BREEZE_SHOOT, .95f, .82f);
                if (hits > 0) {
                    attackSound(impactSound, SoundEvents.AMETHYST_BLOCK_BREAK, 1.13f, .84f);
                    attackSound(impactSound, SoundEvents.HEAVY_CORE_HIT, .70f, .95f);
                }
            }
        }
        if (!positions.isEmpty() && level() instanceof ServerLevel) {
            PacketDistributor.sendToPlayersTrackingEntity(this, new MechMod.StrikeImpact(getId(), getYRot(), positions));
        }
    }

    private void exhaustSound() {
        Vec3 outlet = position().add(local(0, chassis.height * .58, -.55));
        attackSound(outlet, SoundEvents.FIRE_EXTINGUISH, chassis == Chassis.ATLAS ? .58f : .41f,
                chassis == Chassis.ATLAS ? .74f : chassis == Chassis.AETHER ? 1.49f : 1.10f);
    }

    private void attackSound(Vec3 position, SoundEvent sound, float volume, float pitch) {
        level().playSound(null, position.x, position.y + .7, position.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSpectator() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        boolean pak = player.isSecondaryUseActive() && held.is(MechMod.WRENCH.get());
        int supplied = held.is(MechMod.CELL.get()) ? 360
                : BuiltInRegistries.ITEM.getKey(held.getItem()).equals(ACADEMY_CELL) ? 180 : 0;
        boolean repair = held.is(Items.IRON_INGOT);
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (pak) {
            if (isVehicle() || !onGround()) return message(player, "park");
            if (owner != null && !owner.equals(player.getUUID()) && !player.isCreative()) return message(player, "owner");
            ItemStack crate = new ItemStack(MechMod.crate(chassis));
            CustomData.update(DataComponents.CUSTOM_DATA, crate, tag -> {
                tag.putInt("Hull", hull());
                tag.putInt("Charge", charge());
            });
            if (!player.addItem(crate)) player.drop(crate, false);
            discard();
        } else if (supplied > 0) {
            if (charge() == chassis.energy) return message(player, "full_power");
            setCharge(charge() + supplied);
            consume(player, held);
            player.displayClientMessage(Component.translatable("message.acamech.charge", charge(), chassis.energy), true);
            level().playSound(null, blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.NEUTRAL, .42f, 1.28f);
        } else if (repair) {
            if (hull() == chassis.hull) return message(player, "full_hull");
            setHull(hull() + 24);
            consume(player, held);
            player.displayClientMessage(Component.translatable("message.acamech.hull", hull(), chassis.hull), true);
            level().playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, .75f, 1.13f);
        } else if (isVehicle() || !player.startRiding(this)) {
            return message(player, "occupied");
        }
        return InteractionResult.CONSUME;
    }

    private static void consume(Player player, ItemStack stack) {
        if (!player.getAbilities().instabuild) stack.shrink(1);
    }

    private static InteractionResult message(Player player, String key) {
        player.displayClientMessage(Component.translatable("message.acamech." + key), true);
        return InteractionResult.CONSUME;
    }

    void readCrate(ItemStack crate) {
        CompoundTag tag = crate.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains("Hull")) setHull(tag.getInt("Hull"));
        if (tag.contains("Charge")) setCharge(tag.getInt("Charge"));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || !Float.isFinite(amount) || amount <= 0) return false;
        if (source.getEntity() != null && source.getEntity().getVehicle() == this) return false;
        if (!level().isClientSide()) {
            int damage = Math.max(1, Mth.ceil(amount * (chassis == Chassis.ATLAS && auxiliary() ? .42f : 1f)));
            if (damage >= hull()) {
                ejectPassengers();
                level().playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_DEATH, SoundSource.NEUTRAL, 1f, .69f);
                spawnAtLocation(Items.IRON_INGOT, chassis == Chassis.ATLAS ? 4 : 2);
                discard();
            } else {
                setHull(hull() - damage);
                level().playSound(null, blockPosition(), SoundEvents.IRON_GOLEM_HURT, SoundSource.NEUTRAL, .55f, .83f);
            }
        }
        return true;
    }

    @Override public float maxUpStep() { return chassis.step; }
    @Override public boolean isPickable() { return isAlive(); }
    @Override public boolean canBeCollidedWith() { return !isRemoved(); }
    @Override public boolean causeFallDamage(float distance, float scale, DamageSource source) { return false; }
    @Override protected boolean canAddPassenger(Entity passenger) { return passenger instanceof Player && !isVehicle(); }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float partial) {
        return local(0, chassis.seat, chassis.seatForward).add(passenger.getVehicleAttachmentPoint(this));
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity rider) {
        double radius = chassis.width * .5 + .75;
        for (int below = -1; below <= 10; below++) {
            for (int direction = 0; direction < 8; direction++) {
                double angle = direction * Math.PI / 4;
                Vec3 side = position().add(local(Math.cos(angle) * radius, -below, Math.sin(angle) * radius));
                if (!level().hasChunkAt(BlockPos.containing(side))) continue;
                Vec3 place = DismountHelper.findSafeDismountLocation(rider.getType(), level(), BlockPos.containing(side), true);
                if (place == null || getBoundingBox().intersects(rider.getBoundingBox().move(place.subtract(rider.position())))) continue;
                if (!level().containsAnyLiquid(rider.getBoundingBox().move(place.subtract(rider.position())))) return place;
            }
        }
        // In an airborne emergency, leave the rider at a clear side or at the cabin with a soft descent.
        rider.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false, true));
        for (int direction = 0; direction < 8; direction++) {
            double a = direction * Math.PI / 4;
            Vec3 side = position().add(local(Math.cos(a) * radius, 0.3, Math.sin(a) * radius));
            if (DismountHelper.canDismountTo(level(), rider, rider.getBoundingBox().move(side.subtract(rider.position())))) return side;
        }
        return rider.position();
    }

    private Vec3 local(double x, double y, double z) {
        return new Vec3(x, y, z).yRot((float) -Math.toRadians(getYRot()));
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        if (distanceToSqr(x, y, z) > 64) {
            super.lerpTo(x, y, z, yaw, 0, steps);
            lerpSteps = 0;
        } else {
            lerpX = x;
            lerpY = y;
            lerpZ = z;
            lerpYaw = yaw;
            lerpSteps = 2;
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(strikeAge() >= 0 || !strikeImpacts.isEmpty() || rangedAge() >= 0 ? chassis.reach
                : chassis == Chassis.SERAPH ? 2 : .5);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Hull")) setHull(tag.getInt("Hull"));
        if (tag.contains("Charge")) setCharge(tag.getInt("Charge"));
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Hull", hull());
        tag.putInt("Charge", charge());
        if (owner != null) tag.putUUID("Owner", owner);
    }
}
