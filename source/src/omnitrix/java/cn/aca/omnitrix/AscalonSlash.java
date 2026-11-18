package cn.aca.omnitrix;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class AscalonSlash extends ThrowableProjectile {
    private static final EntityDataAccessor<Float> ROLL_DEG = SynchedEntityData.defineId(AscalonSlash.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPAN_SCALE = SynchedEntityData.defineId(AscalonSlash.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> SLASH_VARIANT = SynchedEntityData.defineId(AscalonSlash.class, EntityDataSerializers.BYTE);
    private final Set<UUID> cleaved = new HashSet<>();

    public AscalonSlash(EntityType<? extends AscalonSlash> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    void launch(ServerPlayer owner, Vec3 origin, Vec3 velocity, float rollDeg, float spanScale) {
        launch(owner, origin, velocity, rollDeg, spanScale, 0);
    }

    void launch(ServerPlayer owner, Vec3 origin, Vec3 velocity, float rollDeg, float spanScale, int variant) {
        setOwner(owner);
        setPos(origin);
        setDeltaMovement(velocity);
        entityData.set(ROLL_DEG, rollDeg);
        entityData.set(SPAN_SCALE, spanScale);
        entityData.set(SLASH_VARIANT, (byte) variant);
        float horiz = (float) velocity.horizontalDistance();
        setYRot((float) (Math.toDegrees(Math.atan2(velocity.x, velocity.z))));
        setXRot((float) (Math.toDegrees(Math.atan2(velocity.y, horiz))));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ROLL_DEG, 0f);
        builder.define(SPAN_SCALE, 1f);
        builder.define(SLASH_VARIANT, (byte) 0);
    }

    public float rollDeg() { return entityData.get(ROLL_DEG); }
    public float spanScale() { return entityData.get(SPAN_SCALE); }
    /** 0 = Standard Crescent Arc, 1 = Charged Astral Iaido Horizon Sever, 2 = Twin Cross Crescent */
    public int variant() { return entityData.get(SLASH_VARIANT); }

    @Override protected double getDefaultGravity() { return 0.0; }
    @Override public boolean isNoGravity() { return true; }
    @Override public boolean canUsePortal(boolean cooldown) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean fireImmune() { return true; }

    @Override
    public void tick() {
        Vec3 vel = getDeltaMovement();
        if (!level().isClientSide) {
            int maxTicks = variant() == 1 ? 48 : 38;
            if (tickCount > maxTicks || vel.lengthSqr() < 1e-4 || !(getOwner() instanceof ServerPlayer sp) || !sp.isAlive()) {
                if (level() instanceof ServerLevel sl && getOwner() instanceof ServerPlayer sp) {
                    WristNetwork.cueNear(sl, sp.getUUID(), WristNetwork.ASCALON_BEAM_END_CUE, position(), 96);
                }
                discard();
                return;
            }
            ServerLevel sl = (ServerLevel) level();
            ServerPlayer owner = (ServerPlayer) getOwner();
            boolean iaido = variant() == 1;
            double halfSpan = (iaido ? 3.85 : 2.85) * spanScale();
            AABB sweep = getBoundingBox().expandTowards(vel).inflate(halfSpan, (iaido ? 2.15 : 1.65) * spanScale(), halfSpan);

            for (Projectile proj : sl.getEntitiesOfClass(Projectile.class, sweep,
                    p -> p != this && !(p instanceof AscalonSlash) && p.getOwner() != owner)) {
                WristNetwork.cueNear(sl, owner.getUUID(), Kind.CELESTIAL.ordinal(),
                        WristNetwork.ASCALON_PARRY_CUE, 0, position(), proj.position(), 96);
                proj.discard();
            }

            boolean hitAny = false;
            float baseDmg = iaido ? 285_000_000f : 145_000_000f;
            for (LivingEntity target : sl.getEntitiesOfClass(LivingEntity.class, sweep,
                    e -> e != owner && cleaved.add(e.getUUID()) && Abilities.validTarget(owner, e, false))) {
                if (AscalonService.fundamentalCleave(owner, target, baseDmg, iaido, vel, iaido ? 2.45 : 1.85)) {
                    hitAny = true;
                    WristNetwork.cueNear(sl, owner.getUUID(), Kind.CELESTIAL.ordinal(),
                            WristNetwork.ASCALON_CLEAVE_HIT_CUE, WristNetwork.FLAG_HIT_ENTITY,
                            position(), target.getBoundingBox().getCenter(), 96);
                }
            }
            if (hitAny) {
                Omnitrix.ascalon(owner).addCharge(iaido ? 22f : 14f);
                WristNetwork.syncAscalon(owner);
            }

            BlockPos centerPos = BlockPos.containing(position());
            if (sl.hasChunkAt(centerPos)) {
                var state = sl.getBlockState(centerPos);
                if (state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER)) {
                    WristNetwork.cueNear(sl, owner.getUUID(), WristNetwork.ASCALON_BEAM_END_CUE, position(), 96);
                    discard();
                    return;
                }
                for (BlockPos bp : BlockPos.betweenClosed(centerPos.offset(-1, -1, -1), centerPos.offset(1, 1, 1))) {
                    if (sl.getBlockState(bp).is(Blocks.FIRE) || sl.getBlockState(bp).is(Blocks.SOUL_FIRE)) {
                        sl.removeBlock(bp, false);
                    }
                }
                if (tickCount % (iaido ? 1 : 2) == 0) {
                    BlockPos below = centerPos.below();
                    for (int dy = 0; dy <= 5; dy++) {
                        BlockPos probe = centerPos.below(dy);
                        if (!sl.getBlockState(probe).isAir()) {
                            below = probe;
                            break;
                        }
                    }
                    Vec3 groundAt = new Vec3(getX(), below.getY() + 1.02, getZ());
                    if (Math.abs(groundAt.y - getY()) <= 6.0) {
                        WristNetwork.cueNear(sl, owner.getUUID(), Kind.CELESTIAL.ordinal(),
                                WristNetwork.ASCALON_FISSURE_CUE, iaido ? WristNetwork.FLAG_LINKED : 0,
                                groundAt, groundAt.add(vel.normalize().scale(iaido ? 3.6 : 2.6)), 96);
                    }
                }
            }
        }

        setPos(getX() + vel.x, getY() + vel.y, getZ() + vel.z);
    }

    @Override
    protected void onHit(HitResult result) {
        if (result instanceof BlockHitResult bhr && level() instanceof ServerLevel sl) {
            var st = sl.getBlockState(bhr.getBlockPos());
            if (st.is(Blocks.BEDROCK) || st.is(Blocks.BARRIER)) {
                discard();
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return false;
    }
}
