package cn.academy.port.train;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One car of the express: role 0 = tail (nose west), 1 = middle, 2 = head (nose east). Position is driven by {@link TrainManager}. */
public class TrainCarEntity extends Entity {
    private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SERVICE = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> AIRPORT = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> NORTH_SOUTH = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RING = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> METRO = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> METRO_NORTH = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> METRO_AIRPORT = SynchedEntityData.defineId(TrainCarEntity.class, EntityDataSerializers.BOOLEAN);
    private final Map<LivingEntity, Boolean> northSide = new WeakHashMap<>();

    // Network packets only set a target. ClientLevel snapshots the car, ticks it, then
    // snapshots and positions its riders; keeping movement in that tick makes both
    // render interpolation histories describe the same pair of positions.
    private int interpolationSteps;
    private double targetX, targetY, targetZ;
    private float targetYaw, targetPitch;
    private float doorOpen, previousDoorOpen;

    /** Paired seats line up with the car-local cushions baked by export_train_mesh.py. */
    static final double[] SEAT_X = {-0.85, 0.60};
    static final double[] MID_ROWS = {-6, -3, 0, 3, 6};
    static final double[] HEAD_ROWS = {-9, -6, -3, 0, 3};
    // The player vehicle attachment is 0.6 blocks above its feet: 1.64 - 0.6 = floor 1.04.
    static final double SEAT_Y = 1.64;
    private static final int SEAT_COUNT = 10;

    public TrainCarEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        if (!level().isClientSide) {
            super.lerpTo(x, y, z, yaw, pitch, steps);
            return;
        }
        targetX = x;
        targetY = y;
        targetZ = z;
        targetYaw = yaw;
        targetPitch = pitch;
        interpolationSteps = Math.max(1, steps);
    }

    @Override public double lerpTargetX() { return interpolationSteps > 0 ? targetX : getX(); }
    @Override public double lerpTargetY() { return interpolationSteps > 0 ? targetY : getY(); }
    @Override public double lerpTargetZ() { return interpolationSteps > 0 ? targetZ : getZ(); }
    @Override public float lerpTargetYRot() { return interpolationSteps > 0 ? targetYaw : getYRot(); }
    @Override public float lerpTargetXRot() { return interpolationSteps > 0 ? targetPitch : getXRot(); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && interpolationSteps > 0) {
            lerpPositionAndRotationStep(interpolationSteps, targetX, targetY, targetZ, targetYaw, targetPitch);
            interpolationSteps--;
        }
        if (level().isClientSide) {
            previousDoorOpen = doorOpen;
            float target = servicePhase() == TrainLine.Phase.BOARDING ? 1f : 0f;
            doorOpen = net.minecraft.util.Mth.approach(doorOpen, target, 1f / 12f);
        }
    }

    public int role() { return this.entityData.get(ROLE); }
    public void role(int role) { this.entityData.set(ROLE, role); }
    public boolean airport() { return this.entityData.get(AIRPORT); }
    void airport(boolean value) { this.entityData.set(AIRPORT, value); }
    public boolean northSouth() { return this.entityData.get(NORTH_SOUTH); }
    void northSouth(boolean value) { this.entityData.set(NORTH_SOUTH, value); }
    public boolean ring() { return this.entityData.get(RING); }
    void ring(boolean value) { this.entityData.set(RING, value); }
    public boolean metro() { return this.entityData.get(METRO); }
    void metro(boolean value) { this.entityData.set(METRO, value); }
    public boolean metroNorth() { return this.entityData.get(METRO_NORTH); }
    void metroNorth(boolean value) { this.entityData.set(METRO_NORTH, value); }
    public boolean metroAirport() { return this.entityData.get(METRO_AIRPORT); }
    void metroAirport(boolean value) { this.entityData.set(METRO_AIRPORT, value); }

    public TrainLine.Phase servicePhase() { return TrainLine.Phase.values()[entityData.get(SERVICE)]; }
    void servicePhase(TrainLine.Phase phase) { entityData.set(SERVICE, phase.ordinal()); }
    public float doorOpen(float partialTick) { return net.minecraft.util.Mth.lerp(partialTick, previousDoorOpen, doorOpen); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ROLE, 1);
        builder.define(SERVICE, TrainLine.Phase.CRUISE.ordinal());
        builder.define(AIRPORT, false);
        builder.define(NORTH_SOUTH, false);
        builder.define(RING, false);
        builder.define(METRO, false);
        builder.define(METRO_NORTH, false);
        builder.define(METRO_AIRPORT, false);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    /** Axis-aligned envelope of the car, including turns on the airport route. */
    @Override protected AABB makeBoundingBox() {
        double angle = Math.toRadians(getYRot());
        double hx = 12.7 * Math.abs(Math.sin(angle)) + 1.7 * Math.abs(Math.cos(angle));
        double hz = 12.7 * Math.abs(Math.cos(angle)) + 1.7 * Math.abs(Math.sin(angle));
        return new AABB(getX() - hx, getY(), getZ() - hz, getX() + hx, getY() + 3.7, getZ() + hz);
    }

    @Override public boolean isPickable() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isIgnoringBlockTriggers() { return true; }

    @Override public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) {
            TrainPassengers.tryBoard(serverPlayer, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    boolean hasSeat() { return getPassengers().size() < SEAT_COUNT; }

    boolean board(ServerPlayer player, boolean north) {
        if (!player.startRiding(this)) return false;
        northSide.put(player, north);
        return true;
    }
    /** Civilian commuters use the same seats and rider motion; leave at least four places for players. */
    public boolean boardCommuter(cn.academy.port.city.CityNpcEntity npc) {
        if(!hasCommuterSeat()||npc.commuterId().isEmpty()||npc.isPassenger())return false;
        if(!npc.startRiding(this))return false;northSide.put(npc,true);return true;
    }
    public boolean hasCommuterSeat(){return northSouth()&&servicePhase()==TrainLine.Phase.BOARDING&&getPassengers().size()<6&&getPassengers().stream().filter(p->p instanceof cn.academy.port.city.CityNpcEntity).count()<2;}

    boolean preferredNorth(LivingEntity rider) { return northSide.getOrDefault(rider, true); }

    @Override protected boolean canAddPassenger(Entity passenger) {
        return (passenger instanceof Player||northSouth()&&passenger instanceof cn.academy.port.city.CityNpcEntity npc&&!npc.commuterId().isEmpty())&&hasSeat();
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity rider) {
        Vec3 exit = TrainPassengers.exit(this, rider);
        northSide.remove(rider);
        return exit == null ? rider.position() : exit;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        int i = Math.max(0, getPassengers().indexOf(passenger)) % SEAT_COUNT;
        double lx = SEAT_X[i % 2], lz = (role() == 1 ? MID_ROWS : HEAD_ROWS)[i / 2];
        double theta = Math.toRadians(-getYRot());                // same rotation the renderer applies
        double dx = lx * Math.cos(theta) + lz * Math.sin(theta);
        double dz = -lx * Math.sin(theta) + lz * Math.cos(theta);
        return new Vec3(dx, SEAT_Y, dz);
    }
}
