package cn.academy.port.highpower;

import cn.academy.port.highpower.core.RecoveryMarkGeometry;
import cn.academy.port.highpower.core.RecoveryMarkRules;
import cn.academy.port.highpower.core.ReturnMarkGeometry;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * NEW323 read-only physical recovery assessment. All native shape reads use a non-loading
 * bounded BlockGetter, including callbacks' neighbor reads. No inventory, block, entity,
 * custody, damage, position, or motion mutation occurs here.
 */
public final class RecoveryMarkWorld {
    private static final int MAX_CELLS = 8192, MAX_READS = 32768, MAX_SHAPES = 8192,
            MAX_SHAPE_GRID = 8192, MAX_ENTITIES = 64;

    /** Closed provenance: only a successfully assessed real grounded player's feet issue an anchor. */
    public static final class Anchor {
        private final ServerLevel world;
        private final UUID owner;
        private final Entity needle;
        private final Vec3 destination, originalSource;
        private final AABB originalBody;
        private final Embedding embedding;
        private Anchor(Stamp stamp, Embedding embedding) {
            world = stamp.world; owner = stamp.player.getUUID(); needle = stamp.needle;
            destination = stamp.playerPosition; originalSource = stamp.needlePosition;
            originalBody = stamp.needleBody; this.embedding = embedding;
        }
        public Vec3 destination() { return destination; }
    }

    public record Assessment(String reason, Anchor anchor) {
        public Assessment {
            if (reason == null || reason.isEmpty() != (anchor != null))
                throw new IllegalArgumentException("Invalid NEW323 assessment");
        }
        public boolean valid() { return reason.isEmpty() && anchor != null; }
    }
    public record Check(String reason) {
        public Check { if (reason == null) throw new IllegalArgumentException("Invalid NEW323 check"); }
        public boolean valid() { return reason.isEmpty(); }
    }
    private record Embedding(BlockPos block, BlockState state, SafeLandingProbeGeometry.Box shape) {}

    private RecoveryMarkWorld() {}

    /** Selection remains a separate controller step; the captured destination is never supplied by a client. */
    public static Assessment mark(ServerPlayer player, DisplacedNeedleEntity needle) { return mark(player, (Entity)needle); }

    public static Assessment mark(ServerPlayer player, Entity needle) {
        if (!thread(player)) return new Assessment("server_thread_required", null);
        try {
            Stamp stamp = new Stamp(player, needle);
            // This established closed helper proves a full, grounded and hazard-free player footprint.
            var grounded = SafeLandingProbeWorld.mark(player);
            if (!grounded.valid()) throw refuse(grounded.reason());
            stamp.unchanged();
            Guard view = new Guard(stamp);
            Embedding embedding = assess(view, stamp.playerPosition, null, true);
            stamp.unchanged();
            return new Assessment("", new Anchor(stamp, embedding));
        } catch (Refusal failure) { return new Assessment(failure.reason, null); }
        catch (IllegalArgumentException failure) { return new Assessment("recovery_geometry_invalid", null); }
    }

    /** Fresh current source and world check; this never recaptures or moves the frozen anchor. */
    public static Check check(ServerPlayer player, DisplacedNeedleEntity needle, Anchor anchor) { return check(player, (Entity)needle, anchor); }

    public static Check check(ServerPlayer player, Entity needle, Anchor anchor) {
        if (!thread(player)) return new Check("server_thread_required");
        try {
            if (anchor == null || anchor.world != player.serverLevel() || !anchor.owner.equals(player.getUUID())
                    || anchor.needle != needle) throw refuse("anchor_world_or_owner_changed");
            Stamp stamp = new Stamp(player, needle);
            String source = SafeLandingProbeWorld.sourceRefusal(player);
            if (!source.isEmpty()) throw refuse(source);
            stamp.unchanged();
            // A different current source loses the old exception completely, even when
            // meeting a different block with an identical state. Ordinary clear paths still work.
            Embedding original = stamp.needlePosition.equals(anchor.originalSource)
                    && stamp.needleBody.equals(anchor.originalBody) ? anchor.embedding : null;
            assess(new Guard(stamp), anchor.destination, original, false);
            stamp.unchanged();
            return new Check("");
        } catch (Refusal failure) { return new Check(failure.reason); }
        catch (IllegalArgumentException failure) { return new Check("recovery_geometry_invalid"); }
    }

    /** The current server look ray must reach an exposed native needle body before any solid/fluid. */
    public static Check sight(ServerPlayer player, DisplacedNeedleEntity needle) { return sight(player, (Entity)needle); }

    public static Check sight(ServerPlayer player, Entity needle) {
        if (!thread(player)) return new Check("server_thread_required");
        try {
            Stamp stamp = new Stamp(player, needle);
            Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
            if (!finite(eye) || !finite(look) || look.lengthSqr() < .999 || look.lengthSqr() > 1.001)
                throw refuse("invalid_server_aim");
            Vec3 end = eye.add(look.scale(RecoveryMarkRules.RANGE));
            Vec3 entry = stamp.needleBody.clip(eye, end).orElseThrow(() -> refuse("needle_not_aimed"));
            if (stamp.needleBody.contains(eye)) throw refuse("needle_not_aimed");
            Guard view = new Guard(stamp);
            AABB ray = new AABB(eye, entry).inflate(1e-7);
            view.volume(ray); view.scope = ray.inflate(1.001);
            BlockHitResult block = view.clip(new ClipContext(eye, entry, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player) {
                @Override public VoxelShape getBlockShape(BlockState state, BlockGetter blocks, BlockPos pos) {
                    VoxelShape shape = super.getBlockShape(state, blocks, pos); view.shape(shape); return shape;
                }
                @Override public VoxelShape getFluidShape(FluidState fluid, BlockGetter blocks, BlockPos pos) {
                    VoxelShape shape = super.getFluidShape(fluid, blocks, pos); view.shape(shape); return shape;
                }
            });
            if (block.getType() != HitResult.Type.MISS) throw refuse("needle_not_visible");
            entities(view, ray, body -> body.contains(eye) || body.clip(eye, entry).isPresent(), "needle_not_visible");
            view.volume(ray);
            stamp.unchanged();
            return new Check("");
        } catch (Refusal failure) { return new Check(failure.reason); }
        catch (IllegalArgumentException failure) { return new Check("recovery_geometry_invalid"); }
    }

    private static Embedding assess(Guard view, Vec3 destination, Embedding original, boolean capture) {
        Stamp stamp = view.stamp;
        if (!RecoveryMarkGeometry.pairwiseRange(point(stamp.playerPosition), point(stamp.needlePosition), point(destination)))
            throw refuse("recovery_out_of_range");
        if (stamp.needlePosition.distanceToSqr(destination) <= 1e-8) throw refuse("destination_has_no_displacement");
        var sweep = ReturnMarkGeometry.sweep(box(stamp.needleBody), point(stamp.needlePosition), point(destination));
        var target = sweep.destinationBody();
        AABB destinationBody = nativeBox(target);
        if (!RecoveryMarkGeometry.needleBody(target) || Math.abs(destinationBody.minY - destination.y) > 1e-7)
            throw refuse("destination_body_invalid");
        destination(view, destinationBody, destination.y);
        AABB envelope = nativeBox(sweep.bounds());
        view.volume(envelope); view.scope = envelope.inflate(1.001);
        var cells = sweep.cells();
        if (cells.count() > MAX_CELLS) throw refuse("collision_scan_budget");
        Embedding found = null;
        var context = new NeedleCollisionContext(stamp.needle, stamp.needlePosition.y);
        for (BlockPos pos : BlockPos.betweenClosed(cells.minX(), cells.minY(), cells.minZ(), cells.maxX(), cells.maxY(), cells.maxZ())) {
            if (view.world.isOutsideBuildHeight(pos)) continue;
            BlockState state = view.getBlockState(pos);
            boolean crossed = sweep.intersects(box(new AABB(pos)));
            if (crossed && (!view.getFluidState(pos).isEmpty() || hazard(state))) throw refuse("corridor_fluid_or_hazard");
            VoxelShape shape = state.getCollisionShape(view, pos, context);
            List<SafeLandingProbeGeometry.Box> boxes = view.shape(shape);
            for (var local : boxes) {
                var obstacle = moved(local, pos);
                if (!sweep.intersects(obstacle)) continue;
                if (boxes.size() != 1 || found != null
                        || !RecoveryMarkGeometry.shallowOutwardExit(box(stamp.needleBody), point(stamp.needlePosition), point(destination), obstacle))
                    throw refuse("corridor_block_collision");
                Embedding observed = new Embedding(pos.immutable(), state, obstacle);
                if (!capture && (original == null || !original.equals(observed))) throw refuse("source_embedding_changed");
                found = observed;
            }
        }
        entities(view, envelope, body -> sweep.intersects(box(body)), "corridor_entity_collision");
        view.volume(envelope);
        stamp.unchanged();
        return found;
    }

    private static void destination(Guard view, AABB body, double feet) {
        AABB support = new AABB(body.minX, body.minY - SafeLandingProbeGeometry.SUPPORT_DEPTH,
                body.minZ, body.maxX, body.minY, body.maxZ);
        AABB envelope = body.minmax(support);
        view.volume(envelope); view.scope = envelope.inflate(1.001);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(envelope.minX, envelope.minY, envelope.minZ),
                BlockPos.containing(Math.nextDown(envelope.maxX), Math.nextDown(envelope.maxY), Math.nextDown(envelope.maxZ)))) {
            if (hazard(view.getBlockState(pos))) throw refuse("destination_hazard");
            if (!view.getFluidState(pos).isEmpty()) throw refuse("destination_fluid");
        }
        var cells = SafeLandingProbeGeometry.collisionCells(box(envelope));
        VoxelShape needed = Shapes.create(support), supported = Shapes.empty();
        var context = new NeedleCollisionContext(view.stamp.needle, feet);
        for (BlockPos pos : BlockPos.betweenClosed(cells.minX(), cells.minY(), cells.minZ(), cells.maxX(), cells.maxY(), cells.maxZ())) {
            if (view.world.isOutsideBuildHeight(pos)) continue;
            VoxelShape local = view.getBlockState(pos).getCollisionShape(view, pos, context);
            for (var shapeBox : view.shape(local))
                if (body.intersects(nativeBox(moved(shapeBox, pos)))) throw refuse("destination_block_collision");
            VoxelShape solid = local.move(pos.getX(), pos.getY(), pos.getZ());
            if (Shapes.joinIsNotEmpty(needed, solid, BooleanOp.AND)) {
                unionBudget(supported, solid); supported = Shapes.or(supported, solid);
            }
        }
        if (Shapes.joinIsNotEmpty(needed, supported, BooleanOp.ONLY_FIRST)) throw refuse("destination_floor_unsupported");
        entities(view, body, body::intersects, "destination_entity_collision");
        view.stamp.unchanged();
    }

    private static void entities(Guard view, AABB bounds, java.util.function.Predicate<AABB> hits, String reason) {
        List<Entity> found = new ArrayList<>();
        view.world.getEntities(EntityTypeTest.forClass(Entity.class), bounds.inflate(1e-7), entity -> {
            if (++view.entities > MAX_ENTITIES) throw refuse("entity_scan_budget");
            view.stamp.unchanged();
            // Recovery deliberately ends inside its owner's feet. Other living/pickable or
            // natively collidable bodies are real blockers over the whole route.
            return entity != view.stamp.player && entity != view.stamp.needle && !entity.isSpectator()
                    && (entity.isPickable() || entity instanceof LivingEntity && entity.isAlive()
                    || view.stamp.needle.canCollideWith(entity));
        }, found, MAX_ENTITIES + 1);
        if (found.size() > MAX_ENTITIES) throw refuse("entity_scan_budget");
        for (Entity entity : found) {
            if (!box(entity.getBoundingBox()).finite()) throw refuse("invalid_entity_body");
            if (hits.test(entity.getBoundingBox())) throw refuse(reason);
        }
    }

    /** Captures every mutable actor input before permission/shape callbacks can run. */
    private static final class Stamp {
        final ServerPlayer player;
        final Entity needle;
        final ServerLevel world;
        final Vec3 playerPosition, playerMotion, needlePosition, needleMotion;
        final AABB playerBody, needleBody;
        final Pose pose;
        final float yaw, pitch;
        final RecoveryMedium medium;
        final CompoundTag nativeState;
        Stamp(ServerPlayer player, Entity needle) {
            this.player = player; this.needle = needle; world = player.serverLevel();
            medium = RecoveryMedium.bind(player, needle);
            if (medium == null) throw refuse("needle_unavailable");
            nativeState = medium.nativeState();
            playerPosition = player.position(); playerMotion = player.getDeltaMovement(); playerBody = player.getBoundingBox();
            needlePosition = needle.position(); needleMotion = needle.getDeltaMovement(); needleBody = needle.getBoundingBox();
            pose = player.getPose(); yaw = player.getYRot(); pitch = player.getXRot();
            if (!finite(playerPosition) || !finite(playerMotion) || !finite(needlePosition) || !finite(needleMotion)
                    || !box(playerBody).body() || !RecoveryMarkGeometry.needleBody(box(needleBody))
                    || !needleBody.equals(new AABB(needlePosition.x - .125, needlePosition.y, needlePosition.z - .125,
                    needlePosition.x + .125, needlePosition.y + .25, needlePosition.z + .125)))
                throw refuse("recovery_geometry_invalid");
            unchanged();
        }
        void unchanged() {
            if (player.serverLevel() != world || player.isRemoved() || !player.isAlive() || player.hasDisconnected()
                    || world.getEntity(player.getUUID()) != player || world.getEntity(player.getId()) != player
                    || !medium.valid() || !nativeState.equals(medium.nativeState())
                    || world.getEntity(needle.getUUID()) != needle || world.getEntity(needle.getId()) != needle)
                throw refuse("recovery_actor_changed");
            if (!insideBorder(world, playerBody) || !insideBorder(world, needleBody)) throw refuse("outside_world_border");
            if (!playerPosition.equals(player.position()) || !playerMotion.equals(player.getDeltaMovement())
                    || !playerBody.equals(player.getBoundingBox()) || pose != player.getPose()
                    || Float.compare(yaw, player.getYRot()) != 0 || Float.compare(pitch, player.getXRot()) != 0
                    || !needlePosition.equals(needle.position()) || !needleMotion.equals(needle.getDeltaMovement())
                    || !needleBody.equals(needle.getBoundingBox())) throw refuse("recovery_source_changed");
        }
    }

    private static final class NeedleCollisionContext extends EntityCollisionContext {
        NeedleCollisionContext(Entity needle, double feet) {
            super(needle.isDescending(), feet, ItemStack.EMPTY, fluid -> false, needle);
        }
    }

    private static final class Guard implements BlockGetter {
        final Stamp stamp;
        final ServerLevel world;
        AABB scope;
        int reads, shapes, entities;
        Guard(Stamp stamp) { this.stamp = stamp; world = stamp.world; }
        void volume(AABB volume) {
            stamp.unchanged();
            if (!box(volume).finite() || volume.getXsize() > 25 || volume.getYsize() > 25 || volume.getZsize() > 25)
                throw refuse("collision_scan_budget");
            if (volume.minY < world.getMinBuildHeight() || volume.maxY > world.getMaxBuildHeight())
                throw refuse("outside_build_height");
            if (!insideBorder(world, volume)) throw refuse("outside_world_border");
            int minX = ((int)Math.floor(volume.minX)) >> 4, maxX = ((int)Math.floor(Math.nextDown(volume.maxX))) >> 4;
            int minZ = ((int)Math.floor(volume.minZ)) >> 4, maxZ = ((int)Math.floor(Math.nextDown(volume.maxZ))) >> 4;
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
                if (world.getChunkSource().getChunkNow(x, z) == null) throw refuse("unloaded_chunk");
        }
        LevelChunk chunk(BlockPos pos) {
            stamp.unchanged();
            if (++reads > MAX_READS) throw refuse("block_read_budget");
            if (scope == null || !scope.intersects(new AABB(pos))) throw refuse("shape_neighbor_out_of_scope");
            if (world.isOutsideBuildHeight(pos)) throw refuse("outside_build_height");
            if (!world.getWorldBorder().isWithinBounds(pos)) throw refuse("outside_world_border");
            LevelChunk chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) throw refuse("unloaded_chunk");
            if (!world.mayInteract(stamp.player, pos)) throw refuse("interaction_denied");
            stamp.unchanged();
            if (world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != chunk) throw refuse("unloaded_chunk");
            return chunk;
        }
        @Override public BlockState getBlockState(BlockPos pos) { return chunk(pos).getBlockState(pos); }
        @Override public FluidState getFluidState(BlockPos pos) { return chunk(pos).getFluidState(pos); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return chunk(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK); }
        @Override public int getHeight() { return world.getHeight(); }
        @Override public int getMinBuildHeight() { return world.getMinBuildHeight(); }
        @Override public BlockHitResult clipWithInteractionOverride(Vec3 from, Vec3 to, BlockPos pos, VoxelShape shape, BlockState state) {
            BlockHitResult hit = shape.clip(from, to, pos);
            if (hit != null) {
                VoxelShape interaction = state.getInteractionShape(this, pos); shape(interaction);
                BlockHitResult alternate = interaction.clip(from, to, pos);
                if (alternate != null && alternate.getLocation().distanceToSqr(from) < hit.getLocation().distanceToSqr(from))
                    return hit.withDirection(alternate.getDirection());
            }
            return hit;
        }
        List<SafeLandingProbeGeometry.Box> shape(VoxelShape shape) {
            long grid = 1;
            for (Direction.Axis axis : Direction.Axis.values()) {
                var coordinates = shape.getCoords(axis);
                if (coordinates.size() > 66) throw refuse("shape_grid_budget");
                for (double coordinate : coordinates) if (!Double.isFinite(coordinate)) throw refuse("invalid_native_shape");
                grid *= Math.max(1, coordinates.size() - 1);
                if (grid > MAX_SHAPE_GRID) throw refuse("shape_grid_budget");
            }
            List<SafeLandingProbeGeometry.Box> boxes = new ArrayList<>();
            shape.forAllBoxes((a, b, c, d, e, f) -> {
                if (++shapes > MAX_SHAPES) throw refuse("shape_box_budget");
                var box = new SafeLandingProbeGeometry.Box(a, b, c, d, e, f);
                if (!box.finite()) throw refuse("invalid_native_shape");
                boxes.add(box);
            });
            return boxes;
        }
    }

    private static boolean hazard(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK)
                || (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) && state.getValue(CampfireBlock.LIT)
                || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.COBWEB) || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY);
    }
    private static void unionBudget(VoxelShape a, VoxelShape b) {
        long cells = 1;
        for (Direction.Axis axis : Direction.Axis.values()) {
            Set<Double> coordinates = new HashSet<>();
            for (double c : a.getCoords(axis)) coordinates.add(c);
            for (double c : b.getCoords(axis)) coordinates.add(c);
            cells *= Math.max(1, coordinates.size() - 1);
            if (cells > MAX_SHAPE_GRID) throw refuse("support_grid_budget");
        }
    }
    private static SafeLandingProbeGeometry.Box moved(SafeLandingProbeGeometry.Box b, BlockPos p) {
        return new SafeLandingProbeGeometry.Box(b.minX() + p.getX(), b.minY() + p.getY(), b.minZ() + p.getZ(),
                b.maxX() + p.getX(), b.maxY() + p.getY(), b.maxZ() + p.getZ());
    }
    private static AABB nativeBox(SafeLandingProbeGeometry.Box b) { return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()); }
    private static boolean insideBorder(ServerLevel world, AABB body) {
        var border = world.getWorldBorder();
        return RecoveryMarkGeometry.insideBorder(box(body), border.getMinX(), border.getMinZ(), border.getMaxX(), border.getMaxZ());
    }
    private static SafeLandingProbeGeometry.Box box(AABB b) { return new SafeLandingProbeGeometry.Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ); }
    private static SafeLandingProbeGeometry.Point point(Vec3 v) { return new SafeLandingProbeGeometry.Point(v.x, v.y, v.z); }
    private static boolean finite(Vec3 v) { return v != null && point(v).finite(); }
    private static boolean thread(ServerPlayer player) { return player != null && player.getServer() != null && player.getServer().isSameThread(); }
    private static Refusal refuse(String reason) { return new Refusal(reason); }
    private static final class Refusal extends RuntimeException {
        final String reason;
        Refusal(String reason) { super(reason, null, false, false); this.reason = reason; }
    }
}
