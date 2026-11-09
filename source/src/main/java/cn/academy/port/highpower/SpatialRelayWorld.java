package cn.academy.port.highpower;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry;
import cn.academy.port.highpower.core.SpatialRelayRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
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

/** NEW343 bounded, read-only geometry. Every block/shape callback uses a loaded-only guarded view. */
public final class SpatialRelayWorld {
    private static final int MAX_READS = 131072, MAX_SHAPES = 32768, MAX_SHAPE_GRID = 8192, MAX_ENTITIES = 64;
    public static final class Anchor {
        private final ServerLevel world;
        private final UUID owner;
        private final Vec3 destination;
        private Anchor(Stamp stamp, Vec3 destination) {
            world = stamp.world; owner = stamp.player.getUUID(); this.destination = destination;
        }
        public Vec3 destination() { return destination; }
    }
    public record Assessment(String reason, Anchor anchor) {
        public boolean valid() { return reason != null && reason.isEmpty() && anchor != null; }
    }
    public record Check(String reason) { public boolean valid() { return reason != null && reason.isEmpty(); } }
    public record Selection(List<SpatialRelayMedium> media, String reason) {
        public Selection { media = List.copyOf(media); }
        public boolean valid() { return reason != null && reason.isEmpty(); }
    }
    private SpatialRelayWorld() {}

    /** The first visible upward-facing solid surface is the only permitted mark; no alternate landing search. */
    public static Assessment mark(ServerPlayer player) {
        if (!thread(player)) return new Assessment("server_thread_required", null);
        try {
            Stamp stamp = new Stamp(player, null); Guard view = new Guard(stamp);
            Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
            if (!finite(eye) || !finite(look) || look.lengthSqr() < .999 || look.lengthSqr() > 1.001) throw refuse("invalid_server_aim");
            Vec3 end = eye.add(look.scale(SpatialRelayRules.RANGE));
            // Native DDA must reach the first surface before judging the unused ray tail.
            // Every visited cell (and shape-neighbor read) still passes the loaded-only guard.
            view.scope = new AABB(eye, end).inflate(1.001);
            BlockHitResult hit = view.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player) {
                @Override public VoxelShape getBlockShape(BlockState state, BlockGetter blocks, BlockPos pos) {
                    VoxelShape shape = super.getBlockShape(state, blocks, pos); view.shape(shape); return shape;
                }
                @Override public VoxelShape getFluidShape(FluidState fluid, BlockGetter blocks, BlockPos pos) {
                    VoxelShape shape = super.getFluidShape(fluid, blocks, pos); view.shape(shape); return shape;
                }
            });
            // Complement guarded cell reads with exact fractional-border/build-height bounds
            // for the actually traversed prefix; never validate beyond the native first hit.
            Vec3 reached = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
            view.rayBounds(eye, reached);
            if (hit.getType() != HitResult.Type.BLOCK || hit.isInside() || hit.getDirection() != Direction.UP)
                throw refuse("no_visible_ground");
            Vec3 destination = hit.getLocation();
            if (!SpatialRelayRules.withinRange(point(eye), point(destination))) throw refuse("mark_out_of_range");
            if (!view.getFluidState(hit.getBlockPos()).isEmpty()) throw refuse("first_surface_fluid");
            entities(view, new AABB(eye, destination).inflate(1e-7),
                    body -> body.contains(eye) || body.clip(eye, destination).isPresent(), "ground_not_visible");
            destination(view, SpatialRelayMedium.bodyAt(destination), destination.y);
            stamp.unchanged(); return new Assessment("", new Anchor(stamp, destination));
        } catch (Refusal failure) { return new Assessment(failure.reason, null); }
        catch (IllegalArgumentException failure) { return new Assessment("relay_geometry_invalid", null); }
    }

    public static Check checkAnchor(ServerPlayer player, Anchor anchor) {
        if (!thread(player)) return new Check("server_thread_required");
        try {
            Stamp stamp = new Stamp(player, null); anchor(stamp, anchor);
            destination(new Guard(stamp), SpatialRelayMedium.bodyAt(anchor.destination), anchor.destination.y);
            stamp.unchanged(); return new Check("");
        } catch (Refusal failure) { return new Check(failure.reason); }
        catch (IllegalArgumentException failure) { return new Check("relay_geometry_invalid"); }
    }

    /** Reassess the current physical source and exactly the frozen target, immediately before each leg. */
    public static Check checkLeg(ServerPlayer player, SpatialRelayMedium medium, Anchor anchor) {
        return checkLeg(player, medium, null, anchor);
    }

    /** Read-only projected source for initial route preflight; execution must use the current-source overload. */
    public static Check checkLeg(ServerPlayer player, SpatialRelayMedium medium, Vec3 from, Anchor anchor) {
        if (!thread(player)) return new Check("server_thread_required");
        try {
            Stamp stamp = new Stamp(player, medium); anchor(stamp, anchor);
            if (medium == null || !medium.ownedBy(player) || !medium.valid()) throw refuse("item_unavailable");
            Vec3 source = from == null ? stamp.item.position() : from;
            if (!finite(source)) throw refuse("invalid_source");
            if (!SpatialRelayRules.positiveLeg(point(source), point(anchor.destination)))
                throw refuse(source.equals(anchor.destination) ? "no_movement" : "leg_out_of_range");
            AABB sourceBody = from == null ? stamp.item.body() : SpatialRelayMedium.bodyAt(source);
            var sweep = SpatialRelayRules.sweep(box(sourceBody), point(source), point(anchor.destination));
            Guard view = new Guard(stamp);
            destination(view, nativeBox(sweep.destinationBody()), anchor.destination.y);
            AABB envelope = nativeBox(sweep.bounds()); view.volume(envelope); view.scope = envelope.inflate(2.001);
            var cells = sweep.cells();
            var context = new ItemCollisionContext(medium.entity, source.y);
            for (BlockPos pos : BlockPos.betweenClosed(cells.minX(), cells.minY(), cells.minZ(), cells.maxX(), cells.maxY(), cells.maxZ())) {
                if (view.world.isOutsideBuildHeight(pos)) continue;
                BlockState state = view.getBlockState(pos);
                if (sweep.intersects(box(new AABB(pos))) && (hazard(state) || !view.getFluidState(pos).isEmpty()))
                    throw refuse("corridor_fluid_or_hazard");
                for (var local : view.shape(state.getCollisionShape(view, pos, context)))
                    if (sweep.intersects(moved(local, pos))) throw refuse("corridor_block_collision");
            }
            entities(view, envelope, body -> sweep.intersects(box(body)), "corridor_entity_collision");
            view.volume(envelope); stamp.unchanged(); return new Check("");
        } catch (Refusal failure) { return new Check(failure.reason); }
        catch (IllegalArgumentException failure) { return new Check("relay_geometry_invalid"); }
    }

    /** Bounded selection freezes the existing entities; overflow refuses the whole gather. */
    public static Selection gather(ServerPlayer player, Anchor anchor) {
        if (!thread(player)) return new Selection(List.of(), "server_thread_required");
        List<SpatialRelayMedium> media = new ArrayList<>();
        List<SpatialRelayMedium.Snapshot> snapshots = new ArrayList<>();
        try {
            Stamp stamp = new Stamp(player, null); anchor(stamp, anchor); Guard view = new Guard(stamp);
            destination(view, SpatialRelayMedium.bodyAt(anchor.destination), anchor.destination.y);
            AABB region = new AABB(anchor.destination, anchor.destination).inflate(SpatialRelayRules.GATHER_RADIUS);
            view.volume(region); view.scope = region.inflate(1.001);
            List<Entity> found = new ArrayList<>();
            view.world.getEntities(EntityTypeTest.forClass(Entity.class), region, entity -> true, found, MAX_ENTITIES + 1);
            if (found.size() > MAX_ENTITIES) throw refuse("entity_scan_budget");
            for (Entity entity : found) {
                stamp.unchanged();
                if (!(entity instanceof ItemEntity item) || !SpatialRelayRules.withinGather(point(anchor.destination), point(item.position()))) continue;
                SpatialRelayMedium medium = SpatialRelayMedium.capture(player, item);
                if (medium == null) continue;
                SpatialRelayMedium.Snapshot snapshot = medium.snapshot();
                // Binding does not authorize an inaccessible source cell; route validation follows per leg.
                view.volume(item.getBoundingBox());
                for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(item.getBoundingBox().minX, item.getBoundingBox().minY, item.getBoundingBox().minZ),
                        BlockPos.containing(Math.nextDown(item.getBoundingBox().maxX), Math.nextDown(item.getBoundingBox().maxY), Math.nextDown(item.getBoundingBox().maxZ)))) view.chunk(pos);
                if (!snapshot.matches(medium)) throw refuse("item_changed");
                media.add(medium); snapshots.add(snapshot);
                if (media.size() > SpatialRelayRules.MAX_ITEMS) throw refuse("item_limit");
            }
            stamp.unchanged();
            for (int i = 0; i < media.size(); i++) if (!snapshots.get(i).matches(media.get(i))) throw refuse("item_changed");
            media.sort(Comparator.comparingInt(medium -> medium.entityId));
            return new Selection(media, "");
        } catch (Refusal failure) {
            for (SpatialRelayMedium medium : media) medium.retire();
            return new Selection(List.of(), failure.reason);
        } catch (IllegalArgumentException failure) {
            for (SpatialRelayMedium medium : media) medium.retire();
            return new Selection(List.of(), "relay_geometry_invalid");
        }
    }

    private static void anchor(Stamp stamp, Anchor anchor) {
        if (anchor == null || anchor.world != stamp.world || !anchor.owner.equals(stamp.player.getUUID()) || !finite(anchor.destination))
            throw refuse("anchor_world_or_owner_changed");
    }
    private static void destination(Guard view, AABB body, double feet) {
        if (!SpatialRelayRules.itemBody(box(body)) || Double.compare(body.minY, feet) != 0) throw refuse("destination_body_invalid");
        AABB support = new AABB(body.minX, body.minY - SafeLandingProbeGeometry.SUPPORT_DEPTH,
                body.minZ, body.maxX, body.minY, body.maxZ);
        AABB envelope = body.minmax(support); view.volume(envelope); view.scope = envelope.inflate(1.001);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(envelope.minX, envelope.minY, envelope.minZ),
                BlockPos.containing(Math.nextDown(envelope.maxX), Math.nextDown(envelope.maxY), Math.nextDown(envelope.maxZ)))) {
            if (hazard(view.getBlockState(pos))) throw refuse("destination_hazard");
            if (!view.getFluidState(pos).isEmpty()) throw refuse("destination_fluid");
        }
        var cells = SafeLandingProbeGeometry.collisionCells(box(envelope));
        VoxelShape needed = Shapes.create(support), supported = Shapes.empty();
        var context = new ItemCollisionContext(view.stamp.medium == null ? null : view.stamp.medium.entity, feet);
        for (BlockPos pos : BlockPos.betweenClosed(cells.minX(), cells.minY(), cells.minZ(), cells.maxX(), cells.maxY(), cells.maxZ())) {
            if (view.world.isOutsideBuildHeight(pos)) continue;
            VoxelShape local = view.getBlockState(pos).getCollisionShape(view, pos, context);
            for (var shapeBox : view.shape(local)) if (body.intersects(nativeBox(moved(shapeBox, pos)))) throw refuse("destination_block_collision");
            VoxelShape solid = local.move(pos.getX(), pos.getY(), pos.getZ());
            if (Shapes.joinIsNotEmpty(needed, solid, BooleanOp.AND)) {
                unionBudget(supported, solid); supported = Shapes.or(supported, solid);
            }
        }
        if (Shapes.joinIsNotEmpty(needed, supported, BooleanOp.ONLY_FIRST)) throw refuse("destination_floor_unsupported");
        entities(view, body, body::intersects, "destination_entity_collision"); view.stamp.unchanged();
    }
    private static void entities(Guard view, AABB bounds, java.util.function.Predicate<AABB> hits, String reason) {
        view.volume(bounds); List<Entity> found = new ArrayList<>();
        // Count every queried entity before filtering so an ineligible crowd cannot create an unbounded scan.
        view.world.getEntities(EntityTypeTest.forClass(Entity.class), bounds.inflate(1e-7), entity -> true, found, MAX_ENTITIES + 1);
        if (found.size() > MAX_ENTITIES) throw refuse("entity_scan_budget");
        for (Entity entity : found) {
            view.stamp.unchanged();
            if (entity == view.stamp.player || view.stamp.medium != null && entity == view.stamp.medium.entity || entity.isSpectator()) continue;
            // Other native dropped items retain ordinary merge/pickup behavior at the endpoint.
            if (!(entity.isPickable() || entity instanceof LivingEntity && entity.isAlive() || entity.canBeCollidedWith())) continue;
            if (!box(entity.getBoundingBox()).finite()) throw refuse("invalid_entity_body");
            if (hits.test(entity.getBoundingBox())) throw refuse(reason);
        }
    }
    private static final class Stamp {
        final ServerPlayer player; final ServerLevel world; final SpatialRelayMedium medium;
        final SpatialRelayMedium.Snapshot item; final Vec3 position, motion; final AABB body;
        final Pose pose; final float yaw, pitch;
        Stamp(ServerPlayer player, SpatialRelayMedium medium) {
            this.player = player; world = player.serverLevel(); this.medium = medium;
            item = medium == null ? null : medium.snapshot();
            position = player.position(); motion = player.getDeltaMovement(); body = player.getBoundingBox();
            pose = player.getPose(); yaw = player.getYRot(); pitch = player.getXRot(); unchanged();
        }
        void unchanged() {
            if (player.serverLevel() != world || player.isRemoved() || !player.isAlive() || player.hasDisconnected()
                    || world.getEntity(player.getUUID()) != player || world.getEntity(player.getId()) != player
                    || !finite(position) || !finite(motion) || !position.equals(player.position()) || !motion.equals(player.getDeltaMovement())
                    || !body.equals(player.getBoundingBox()) || pose != player.getPose()
                    || Float.compare(yaw, player.getYRot()) != 0 || Float.compare(pitch, player.getXRot()) != 0
                    || medium != null && !item.matches(medium)) throw refuse("relay_actor_changed");
        }
    }
    private static final class ItemCollisionContext extends EntityCollisionContext {
        ItemCollisionContext(Entity entity, double feet) {
            super(entity != null && entity.isDescending(), feet, ItemStack.EMPTY, fluid -> false, entity);
        }
    }
    private static final class Guard implements BlockGetter {
        final Stamp stamp; final ServerLevel world; AABB scope; int reads, shapes;
        Guard(Stamp stamp) { this.stamp = stamp; world = stamp.world; }
        /** A segment may have zero width on one or two axes; it is not a body volume. */
        void rayBounds(Vec3 from, Vec3 reached) {
            stamp.unchanged();
            if(!finite(from)||!finite(reached))throw refuse("collision_scan_budget");
            AABB ray=new AABB(from,reached);
            if(ray.getXsize()>33||ray.getYsize()>33||ray.getZsize()>33)throw refuse("collision_scan_budget");
            if(ray.minY<world.getMinBuildHeight()||ray.maxY>world.getMaxBuildHeight())throw refuse("outside_build_height");
            var border=world.getWorldBorder();double x0=border.getMinX(),x1=border.getMaxX(),z0=border.getMinZ(),z1=border.getMaxZ();
            if(!Double.isFinite(x0)||!Double.isFinite(x1)||!Double.isFinite(z0)||!Double.isFinite(z1)
                    ||x0>=x1||z0>=z1||ray.minX<x0||ray.maxX>x1||ray.minZ<z0||ray.maxZ>z1)
                throw refuse("outside_world_border");
            // Native DDA already guarded every cell actually visited before this endpoint.
        }
        void volume(AABB volume) {
            stamp.unchanged();
            if (!box(volume).finite() || volume.getXsize() > 33 || volume.getYsize() > 33 || volume.getZsize() > 33)
                throw refuse("collision_scan_budget");
            if (volume.minY < world.getMinBuildHeight() || volume.maxY > world.getMaxBuildHeight()) throw refuse("outside_build_height");
            var border = world.getWorldBorder();
            if (!SpatialRelayRules.insideBorder(box(volume), border.getMinX(), border.getMinZ(), border.getMaxX(), border.getMaxZ()))
                throw refuse("outside_world_border");
            int x0 = (int)Math.floor(volume.minX) >> 4, x1 = (int)Math.floor(Math.nextDown(volume.maxX)) >> 4;
            int z0 = (int)Math.floor(volume.minZ) >> 4, z1 = (int)Math.floor(Math.nextDown(volume.maxZ)) >> 4;
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++)
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
                grid *= Math.max(1, coordinates.size() - 1); if (grid > MAX_SHAPE_GRID) throw refuse("shape_grid_budget");
            }
            List<SafeLandingProbeGeometry.Box> boxes = new ArrayList<>();
            shape.forAllBoxes((a, b, c, d, e, f) -> {
                if (++shapes > MAX_SHAPES) throw refuse("shape_box_budget");
                var box = new SafeLandingProbeGeometry.Box(a, b, c, d, e, f);
                if (!box.finite()) throw refuse("invalid_native_shape"); boxes.add(box);
            });
            return boxes;
        }
    }
    private static boolean hazard(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK) || (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) && state.getValue(CampfireBlock.LIT)
                || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.COBWEB)
                || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY);
    }
    private static void unionBudget(VoxelShape a, VoxelShape b) {
        long cells = 1;
        for (Direction.Axis axis : Direction.Axis.values()) {
            Set<Double> coordinates = new HashSet<>();
            for (double c : a.getCoords(axis)) coordinates.add(c);
            for (double c : b.getCoords(axis)) coordinates.add(c);
            cells *= Math.max(1, coordinates.size() - 1); if (cells > MAX_SHAPE_GRID) throw refuse("support_grid_budget");
        }
    }
    private static SafeLandingProbeGeometry.Box moved(SafeLandingProbeGeometry.Box b, BlockPos p) {
        return new SafeLandingProbeGeometry.Box(b.minX() + p.getX(), b.minY() + p.getY(), b.minZ() + p.getZ(),
                b.maxX() + p.getX(), b.maxY() + p.getY(), b.maxZ() + p.getZ());
    }
    private static AABB nativeBox(SafeLandingProbeGeometry.Box b) { return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()); }
    private static SafeLandingProbeGeometry.Box box(AABB b) { return new SafeLandingProbeGeometry.Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ); }
    private static SafeLandingProbeGeometry.Point point(Vec3 v) { return new SafeLandingProbeGeometry.Point(v.x, v.y, v.z); }
    private static boolean finite(Vec3 v) { return SpatialRelayMedium.finite(v); }
    private static boolean thread(ServerPlayer p) { return p != null && p.getServer() != null && p.getServer().isSameThread(); }
    private static Refusal refuse(String reason) { return new Refusal(reason); }
    private static final class Refusal extends RuntimeException {
        final String reason;
        Refusal(String reason) { super(reason, null, false, false); this.reason = reason; }
    }
}
