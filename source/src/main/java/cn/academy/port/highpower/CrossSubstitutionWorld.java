package cn.academy.port.highpower;

import cn.academy.port.highpower.core.CrossSubstitutionRules;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** NEW321 read-only bounded geometry. All native shape reads use a non-loading view. */
final class CrossSubstitutionWorld {
    record Plan(ServerLevel world, ServerPlayer owner, LivingEntity target, UUID targetUuid,
                int targetId, Vec3 endpoint) {}
    record Result(Plan plan, String reason) { boolean valid() { return plan != null; } }
    private CrossSubstitutionWorld() {}

    static Result acquire(ServerPlayer p) {
        if (p == null || !p.server.isSameThread() || !finite(p.getEyePosition()) || !finite(p.getLookAngle()))
            return new Result(null, "not_ready");
        try {
            Vec3 start = p.getEyePosition(), look = p.getLookAngle();
            if (look.lengthSqr() < .999 || look.lengthSqr() > 1.001) throw refuse("invalid_server_aim");
            Vec3 end = start.add(look.scale(CrossSubstitutionRules.RANGE));
            Guard view = new Guard(p); BlockHitResult block = view.clip(start, end);
            double limit = block.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : start.distanceToSqr(block.getLocation());
            Entity first = null; double closest = limit;
            for (Entity e : entities(p, new AABB(start, end).inflate(1), null)) {
                AABB body = e.getBoundingBox();
                Vec3 contact = body.contains(start) ? start : body.clip(start, end).orElse(null);
                if (contact != null && start.distanceToSqr(contact) < closest) { first = e; closest = start.distanceToSqr(contact); }
            }
            if (!(first instanceof LivingEntity target) || !hostile(p, target)) throw refuse("no_target");
            Vec3 endpoint = target.getBoundingBox().getCenter();
            if (!finite(endpoint)) throw refuse("invalid_target");
            return new Result(new Plan(p.serverLevel(), p, target, target.getUUID(), target.getId(), endpoint), "");
        } catch (Refusal refusal) { return new Result(null, refusal.reason); }
    }

    static Vec3 origin(SafeLandingProbeWorld.CrossPoint point) {
        return point.destination().add(0, CrossSubstitutionRules.ORIGIN_HEIGHT, 0);
    }
    static AABB needleBody(Vec3 origin) {
        return new AABB(origin.x - .125, origin.y, origin.z - .125, origin.x + .125, origin.y + .25, origin.z + .125);
    }
    static String point(ServerPlayer p, SafeLandingProbeWorld.CrossPoint point, DisplacedNeedleEntity pending) {
        var assessed = SafeLandingProbeWorld.revalidateCrossPoint(p, point);
        if (!assessed.valid()) return assessed.reason();
        try { originClear(p, origin(point), pending); return ""; }
        catch (Refusal refusal) { return refusal.reason; }
    }
    static String validate(Plan plan, SafeLandingProbeWorld.CrossPoint point, DisplacedNeedleEntity pending) {
        if (plan == null || plan.world != plan.owner.serverLevel() || !sameTarget(plan)) return "target_changed";
        String reason = point(plan.owner, point, pending); if (!reason.isEmpty()) return reason;
        Vec3 origin = origin(point);
        if (!CrossSubstitutionRules.path(plan.owner.getEyePosition().distanceTo(origin), origin.distanceTo(plan.endpoint)))
            return "out_of_range";
        try {
            Guard view = new Guard(plan.owner);
            if (view.clip(plan.owner.getEyePosition(), origin).getType() != HitResult.Type.MISS
                    || view.clip(origin, plan.endpoint).getType() != HitResult.Type.MISS) return "blocked";
            // New occupants, including an old real needle, block publication at this emitter.
            // The target may move after the third tap; the fixed endpoint never follows it.
            return sameTarget(plan) ? "" : "target_changed";
        } catch (Refusal refusal) { return refusal.reason; }
    }
    static boolean sameTarget(Plan plan) {
        return plan != null && plan.target.getUUID().equals(plan.targetUuid) && plan.target.getId() == plan.targetId
                && hostile(plan.owner, plan.target);
    }
    static boolean hostile(ServerPlayer p, LivingEntity target) {
        if (!(HighPowerCombat.hostileTarget(target)) || target.level() != p.level() || !target.isAlive() || target.isRemoved()
                || target.isSpectator() || target.isInvulnerable() || !HighPowerCombat.supported(target)
                || HighPowerCombat.unsupportedReason(target).isPresent() || p.isAlliedTo(target) || target.isAlliedTo(p)
                || p.serverLevel().getEntity(target.getUUID()) != target || p.serverLevel().getEntity(target.getId()) != target)
            return false;
        try { new Guard(p).volume(target.getBoundingBox()); return true; }
        catch (Refusal refusal) { return false; }
    }
    private static void originClear(ServerPlayer p, Vec3 origin, DisplacedNeedleEntity pending) {
        AABB body = needleBody(origin); Guard view = new Guard(p); view.volume(body); view.scope = body.inflate(1.001);
        if (pending != null && (!pending.position().equals(origin) || !pending.getBoundingBox().equals(body)))
            throw refuse("medium_changed");
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX - 1, body.minY - 1, body.minZ - 1),
                BlockPos.containing(body.maxX + 1, body.maxY + 1, body.maxZ + 1))) {
            if (view.world.isOutsideBuildHeight(pos)) continue;
            BlockState state = view.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(view, pos, pending == null ? CollisionContext.empty() : CollisionContext.of(pending));
            view.shape(shape);
            for (AABB box : shape.toAabbs()) if (box.move(pos).intersects(body)) throw refuse("origin_blocked");
            if (new AABB(pos).intersects(body) && !view.getFluidState(pos).isEmpty()) throw refuse("origin_fluid");
        }
        for (Entity e : entities(p, body.inflate(1e-7), pending))
            if (e.getBoundingBox().intersects(body)) throw refuse("origin_occupied");
    }
    /** Native first collision is the only entry here; padding cannot reach through terrain. */
    static boolean contactClear(ServerPlayer p, Vec3 start, Vec3 end, LivingEntity target, DisplacedNeedleEntity medium) {
        try {
            if (!finite(start) || !finite(end)) return false;
            Guard view = new Guard(p);
            if (view.clip(start, end).getType() != HitResult.Type.MISS
                    || view.clip(start, target.getBoundingBox().getCenter()).getType() != HitResult.Type.MISS) return false;
            AABB padded = target.getBoundingBox().inflate((double) .3F);
            if (end.x < padded.minX || end.x > padded.maxX || end.y < padded.minY || end.y > padded.maxY
                    || end.z < padded.minZ || end.z > padded.maxZ) return false;
            double distance = start.distanceToSqr(end);
            for (Entity e : entities(p, new AABB(start, end).inflate(1), medium)) if (e != target) {
                AABB obstruction = e.getBoundingBox().inflate((double) .3F);
                if (obstruction.contains(start)) return false;
                var hit = obstruction.clip(start, end);
                if (hit.isPresent() && start.distanceToSqr(hit.get()) <= distance) return false;
            }
            return true;
        } catch (Refusal refusal) { return false; }
    }
    private static List<Entity> entities(ServerPlayer p, AABB volume, Entity ignored) {
        new Guard(p).volume(volume);
        List<Entity> result = new ArrayList<>(); int[] scanned = {0};
        p.serverLevel().getEntities(EntityTypeTest.forClass(Entity.class), volume, e -> {
            if (++scanned[0] > 64) throw refuse("entity_scan_budget");
            return e != p && e != ignored && !e.isSpectator() && (e.isPickable() || e instanceof LivingEntity && e.isAlive());
        }, result, 65);
        if (result.size() > 64) throw refuse("entity_scan_budget");
        return result;
    }
    private static final class Guard implements BlockGetter {
        final ServerPlayer player; final ServerLevel world; AABB scope; int reads, shapes;
        Guard(ServerPlayer player) { this.player = player; world = player.serverLevel(); }
        void volume(AABB box) {
            if (player.serverLevel() != world || player.isRemoved() || !finite(new Vec3(box.minX, box.minY, box.minZ))
                    || !finite(new Vec3(box.maxX, box.maxY, box.maxZ))) throw refuse("world_changed");
            if (box.minY < world.getMinBuildHeight() || box.maxY > world.getMaxBuildHeight()) throw refuse("outside_build_height");
            if (!world.getWorldBorder().isWithinBounds(box)) throw refuse("outside_world_border");
            int x0 = (int) Math.floor(box.minX), x1 = (int) Math.floor(box.maxX),
                    y0 = (int) Math.floor(box.minY), y1 = (int) Math.floor(box.maxY),
                    z0 = (int) Math.floor(box.minZ), z1 = (int) Math.floor(box.maxZ);
            if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 24000) throw refuse("volume_budget");
            for (int x = x0 >> 4; x <= x1 >> 4; x++) for (int z = z0 >> 4; z <= z1 >> 4; z++)
                if (world.getChunkSource().getChunkNow(x, z) == null) throw refuse("unloaded_chunk");
            for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1))
                if (!world.mayInteract(player, pos)) throw refuse("interaction_denied");
        }
        BlockHitResult clip(Vec3 from, Vec3 to) {
            if (!finite(from) || !finite(to) || from.distanceToSqr(to) > 32 * 32 + .001) throw refuse("invalid_ray");
            AABB segment = new AABB(from, to).inflate(.001); volume(segment); scope = segment.inflate(1.001);
            return clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player) {
                @Override public VoxelShape getBlockShape(BlockState state, BlockGetter blocks, BlockPos pos) {
                    VoxelShape shape = super.getBlockShape(state, blocks, pos); Guard.this.shape(shape); return shape;
                }
                @Override public VoxelShape getFluidShape(FluidState fluid, BlockGetter blocks, BlockPos pos) {
                    VoxelShape shape = super.getFluidShape(fluid, blocks, pos); Guard.this.shape(shape); return shape;
                }
            });
        }
        LevelChunk chunk(BlockPos pos) {
            if (++reads > 32768 || scope == null || !scope.intersects(new AABB(pos))) throw refuse("block_read_budget");
            if (player.serverLevel() != world || player.isRemoved()) throw refuse("world_changed");
            if (world.isOutsideBuildHeight(pos)) throw refuse("outside_build_height");
            if (!world.getWorldBorder().isWithinBounds(pos)) throw refuse("outside_world_border");
            LevelChunk chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) throw refuse("unloaded_chunk");
            if (!world.mayInteract(player, pos)) throw refuse("interaction_denied");
            return chunk;
        }
        @Override public BlockState getBlockState(BlockPos pos) { return chunk(pos).getBlockState(pos); }
        @Override public FluidState getFluidState(BlockPos pos) { return chunk(pos).getFluidState(pos); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return chunk(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK); }
        @Override public BlockHitResult clipWithInteractionOverride(Vec3 from, Vec3 to, BlockPos pos, VoxelShape solid, BlockState state) {
            BlockHitResult hit = solid.clip(from, to, pos);
            if (hit != null) {
                VoxelShape interaction = state.getInteractionShape(this, pos); shape(interaction);
                BlockHitResult alternate = interaction.clip(from, to, pos);
                if (alternate != null && alternate.getLocation().distanceToSqr(from) < hit.getLocation().distanceToSqr(from))
                    return hit.withDirection(alternate.getDirection());
            }
            return hit;
        }
        @Override public int getHeight() { return world.getHeight(); }
        @Override public int getMinBuildHeight() { return world.getMinBuildHeight(); }
        void shape(VoxelShape shape) {
            long cells = 1;
            for (net.minecraft.core.Direction.Axis axis : net.minecraft.core.Direction.Axis.values()) {
                var coordinates = shape.getCoords(axis);
                if (coordinates.size() > 66) throw refuse("shape_budget");
                for (double coordinate : coordinates) if (!Double.isFinite(coordinate)) throw refuse("invalid_shape");
                cells *= Math.max(1, coordinates.size() - 1);
            }
            if (cells > 8192) throw refuse("shape_budget");
            shape.forAllBoxes((a, b, c, d, e, f) -> {
                if (++shapes > 4096 || !finite(new Vec3(a, b, c)) || !finite(new Vec3(d, e, f))) throw refuse("shape_budget");
            });
        }
    }
    static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z)
            && Math.abs(v.x) <= 30_000_000 && Math.abs(v.y) <= 30_000_000 && Math.abs(v.z) <= 30_000_000; }
    private static Refusal refuse(String reason) { return new Refusal(reason); }
    private static final class Refusal extends RuntimeException {
        final String reason;
        Refusal(String reason) { super(reason, null, false, false); this.reason = reason; }
    }
}
