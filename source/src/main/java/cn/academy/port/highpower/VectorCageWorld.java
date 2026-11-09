package cn.academy.port.highpower;

import cn.academy.port.highpower.core.VectorCageGeometry;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** NEW441 loaded-only terrain authority. Native shapes can read only this bounded view. */
final class VectorCageWorld {
    record Anchor(VectorCageGeometry.Frame frame, Vec3 castOrigin, Proof proof) {}
    private static final int MAX_READS = 16_384, MAX_BOXES = 8_192;
    private VectorCageWorld() {}

    static Anchor acquire(ServerPlayer player) {
        if (!finite(player.getEyePosition()) || !finite(player.getLookAngle())) return null;
        Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().normalize().scale(56));
        try {
            Proof ray = new Proof(player, new AABB(eye, end).inflate(1.001));
            BlockHitResult hit = ray.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player) {
                @Override public VoxelShape getBlockShape(BlockState state, BlockGetter view, BlockPos pos) {
                    VoxelShape shape = super.getBlockShape(state, view, pos); ray.checkShape(shape); return shape;
                }
                @Override public VoxelShape getFluidShape(FluidState state, BlockGetter view, BlockPos pos) {
                    VoxelShape shape = super.getFluidShape(state, view, pos); ray.checkShape(shape); return shape;
                }
            });
            if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != Direction.UP || hit.isInside()
                    || !ray.getFluidState(hit.getBlockPos()).isEmpty() || !ray.unchanged()) return null;
            Vec3 center = hit.getLocation();
            VectorCageGeometry.Frame frame = new VectorCageGeometry.Frame(point(center));
            if (!VectorCageGeometry.withinRange(frame, 4, point(eye), point(player.getEyePosition()))) return null;
            Proof field = inspect(player, frame, hit.getBlockPos());
            return field != null && ray.unchanged() && field.unchanged() ? new Anchor(frame, eye, field) : null;
        } catch (Refusal | IllegalArgumentException invalid) { return null; }
    }

    private static Proof inspect(ServerPlayer player, VectorCageGeometry.Frame frame, BlockPos ground) {
        AABB bounds = box(VectorCageGeometry.bounds(frame));
        if (!loaded(player, bounds)) return null;
        Proof proof = new Proof(player, bounds.inflate(1.001));
        // Include the actual supporting block in the immutable terrain receipt. Removing it cancels the field.
        VoxelShape support = proof.getBlockState(ground).getCollisionShape(proof, ground, CollisionContext.of(player));
        proof.checkShape(support); if (support.isEmpty()) return null;
        int x0 = floor(Math.nextDown(bounds.minX)), x1 = floor(bounds.maxX), y0 = floor(Math.nextDown(bounds.minY)), y1 = floor(bounds.maxY), z0 = floor(Math.nextDown(bounds.minZ)), z1 = floor(bounds.maxZ);
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = proof.getBlockState(pos);
            if (!proof.getFluidState(pos).isEmpty()) return null;
            VoxelShape shape = state.getCollisionShape(proof, pos, CollisionContext.of(player));
            proof.checkShape(shape);
            final boolean[] collision = {false};
            shape.forAllBoxes((a, b, c, d, e, f) -> {
                if (a + pos.getX() <= bounds.maxX && d + pos.getX() >= bounds.minX
                        && b + pos.getY() <= bounds.maxY && e + pos.getY() >= bounds.minY
                        && c + pos.getZ() <= bounds.maxZ && f + pos.getZ() >= bounds.minZ) collision[0] = true;
            });
            if (collision[0]) return null;
        }
        proof.field = bounds;
        return proof.unchanged() ? proof : null;
    }
    static boolean loaded(ServerPlayer player, AABB bounds) {
        if (player == null || bounds == null || !finite(new Vec3(bounds.minX, bounds.minY, bounds.minZ)) || !finite(new Vec3(bounds.maxX, bounds.maxY, bounds.maxZ))) return false;
        ServerLevel world = player.serverLevel();
        if (!world.getWorldBorder().isWithinBounds(bounds) || bounds.minY < world.getMinBuildHeight() || bounds.maxY >= world.getMaxBuildHeight()) return false;
        for (int x = floor(Math.nextDown(bounds.minX)) >> 4; x <= floor(bounds.maxX) >> 4; x++) for (int z = floor(Math.nextDown(bounds.minZ)) >> 4; z <= floor(bounds.maxZ) >> 4; z++)
            if (world.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }

    static boolean hostile(ServerPlayer player, LivingEntity target) {
        return target != null && HighPowerCombat.hostileTarget(target) && HighPowerCombat.supported(target) && VectorCageNativeBridge.available(target)
                && target.level() == player.level() && target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && !target.isInvulnerable() && !target.noPhysics && !target.isPassenger() && !target.isVehicle()
                && !target.getPersistentData().getBoolean("academy:protected") && !target.getPersistentData().getBoolean("academy:immovable")
                && !player.isAlliedTo(target) && !target.isAlliedTo(player)
                && player.serverLevel().getEntity(target.getId()) == target && player.serverLevel().getEntity(target.getUUID()) == target
                && finite(target.position()) && finite(target.getDeltaMovement()) && HighPowerCombat.unsupportedReason(target).isEmpty()
                && loaded(player, target.getBoundingBox());
    }

    static final class Proof implements BlockGetter {
        private record Read(BlockPos pos, LevelChunk chunk, BlockState block, FluidState fluid) {}
        final ServerPlayer player; final ServerLevel world; final AABB scope;
        final Map<BlockPos, Read> reads = new LinkedHashMap<>();
        AABB field; int readCount, boxCount;
        Proof(ServerPlayer player, AABB scope) { this.player = player; world = player.serverLevel(); this.scope = scope; }
        private Read read(BlockPos pos) {
            if (++readCount > MAX_READS || player.serverLevel() != world || player.isRemoved() || !scope.intersects(new AABB(pos))
                    || world.isOutsideBuildHeight(pos) || !world.getWorldBorder().isWithinBounds(pos)) throw new Refusal();
            LevelChunk chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null || !world.mayInteract(player, pos)) throw new Refusal();
            Read current = new Read(pos.immutable(), chunk, chunk.getBlockState(pos), chunk.getFluidState(pos));
            // These native shapes depend on the observing body's equipment/posture.
            // An unchanged block-state receipt cannot certify them for this frozen field.
            if (current.block.getBlock() instanceof ScaffoldingBlock || current.block.getBlock() instanceof PowderSnowBlock) throw new Refusal();
            Read old = reads.putIfAbsent(current.pos, current);
            if (old != null && (old.chunk != current.chunk || old.block != current.block || old.fluid != current.fluid)) throw new Refusal();
            return current;
        }
        @Override public BlockState getBlockState(BlockPos pos) { return read(pos).block; }
        @Override public FluidState getFluidState(BlockPos pos) { return read(pos).fluid; }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { throw new Refusal(); }
        @Override public int getHeight() { return world.getHeight(); }
        @Override public int getMinBuildHeight() { return world.getMinBuildHeight(); }
        @Override public BlockHitResult clipWithInteractionOverride(Vec3 from, Vec3 to, BlockPos pos, VoxelShape shape, BlockState state) { return shape.clip(from, to, pos); }
        void checkShape(VoxelShape shape) {
            if (shape == null) throw new Refusal();
            long cells = 1;
            for (Direction.Axis axis : Direction.Axis.values()) {
                var coords = shape.getCoords(axis); if (coords.size() > 34) throw new Refusal();
                for (double value : coords) if (!Double.isFinite(value)) throw new Refusal();
                cells *= Math.max(1, coords.size() - 1);
            }
            if (cells > 4096) throw new Refusal();
            shape.forAllBoxes((a, b, c, d, e, f) -> { if (++boxCount > MAX_BOXES || !Double.isFinite(a) || !Double.isFinite(b)
                    || !Double.isFinite(c) || !Double.isFinite(d) || !Double.isFinite(e) || !Double.isFinite(f)) throw new Refusal(); });
        }
        boolean unchanged() {
            if (player.serverLevel() != world || player.isRemoved() || field != null && !loaded(player, field)) return false;
            for (Read value : reads.values()) if (!world.mayInteract(player, value.pos)) return false;
            // Check states after permission callbacks, which may synchronously mutate another observed cell.
            for (Read value : reads.values()) if (world.isOutsideBuildHeight(value.pos) || !world.getWorldBorder().isWithinBounds(value.pos)
                    || world.getChunkSource().getChunkNow(value.pos.getX() >> 4, value.pos.getZ() >> 4) != value.chunk
                    || value.chunk.getBlockState(value.pos) != value.block || value.chunk.getFluidState(value.pos) != value.fluid) return false;
            return player.serverLevel() == world && !player.isRemoved();
        }
    }
    private static final class Refusal extends RuntimeException { Refusal() { super(null, null, false, false); } }
    static boolean finite(Vec3 value) { return value != null && point(value).finite(); }
    static VectorCageGeometry.Point point(Vec3 value) { return new VectorCageGeometry.Point(value.x, value.y, value.z); }
    static Vec3 vec(VectorCageGeometry.Point value) { return new Vec3(value.x(), value.y(), value.z()); }
    static VectorCageGeometry.Box box(AABB value) { return new VectorCageGeometry.Box(value.minX, value.minY, value.minZ, value.maxX, value.maxY, value.maxZ); }
    static AABB box(VectorCageGeometry.Box value) { return new AABB(value.minX(), value.minY(), value.minZ(), value.maxX(), value.maxY(), value.maxZ()); }
    private static int floor(double value) { return (int)Math.floor(value); }
}
