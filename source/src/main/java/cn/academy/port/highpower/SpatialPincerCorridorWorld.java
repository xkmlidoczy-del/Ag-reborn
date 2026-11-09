package cn.academy.port.highpower;

import cn.academy.port.highpower.core.SpatialPincerCorridorRules;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import java.util.ArrayList;
import java.util.Comparator;
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

/** NEW341 read-only bounded geometry. All native shape reads use a non-loading view. */
public final class SpatialPincerCorridorWorld {
    record Plan(ServerLevel world, ServerPlayer owner, LivingEntity target, UUID targetUuid,
                int targetId, Vec3 endpoint) {}
    record Result(Plan plan, String reason) { boolean valid() { return plan != null; } }
    record VolleyResult(List<Plan> plans, String reason) {
        VolleyResult { plans = List.copyOf(plans); }
        boolean valid() { return !plans.isEmpty(); }
    }
    private record Candidate(Plan plan, double angle, double distance) {}
    private SpatialPincerCorridorWorld() {}

    /** Captured UP surface and standing body; arbitrary coordinates cannot construct a mark. */
    public static final class Point {
        private final ServerLevel world;
        private final UUID owner;
        private final BlockPos surface;
        private final Vec3 destination;
        private final AABB dimensions;
        private final float yaw, pitch;
        private Point(ServerPlayer p, BlockHitResult hit) {
            world = p.serverLevel(); owner = p.getUUID(); surface = hit.getBlockPos().immutable();
            destination = hit.getLocation(); yaw = p.getYRot(); pitch = p.getXRot(); dimensions = p.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO);
        }
        public ServerLevel world() { return world; }
        public UUID owner() { return owner; }
        public Vec3 destination() { return destination; }
        public float yaw() { return yaw; }
        public float pitch() { return pitch; }
        public AABB standingBody() { return dimensions.move(destination); }
    }
    public record PointResult(Point point, String reason) { public boolean valid() { return point != null; } }
    public static PointResult capturePoint(ServerPlayer p) {
        if (p == null || !p.server.isSameThread()) return new PointResult(null, "server_thread_required");
        try {
            source(p, true);
            Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
            if (!finite(eye) || !finite(look) || look.lengthSqr() < .999 || look.lengthSqr() > 1.001)
                throw refuse("invalid_server_aim");
            Guard view = new Guard(p);
            BlockHitResult hit = view.clip(eye, eye.add(look.scale(SpatialPincerCorridorRules.RANGE)));
            standable(view, hit);
            // A native body in front of the surface is never bypassed to mark behind it.
            visible(p, eye, hit.getLocation(), null);
            Point point = new Point(p, hit);
            clearance(p, view, point.standingBody(), point.destination.y, true);
            originClear(p, origin(point), null);
            return new PointResult(point, "");
        } catch (Refusal failure) { return new PointResult(null, failure.reason); }
    }
    /** Revalidates the exact saved surface, complete standing body, and current eye visibility. */
    public static PointResult revalidatePoint(ServerPlayer p, Point point) { return revalidatePoint(p, point, null); }
    private static PointResult revalidatePoint(ServerPlayer p, Point point, Entity ignored) {
        return revalidatePoint(p, point, ignored, false);
    }
    private static PointResult revalidatePoint(ServerPlayer p, Point point, Entity ignored, boolean entrancePreview) {
        if (p == null || !p.server.isSameThread()) return new PointResult(null, "server_thread_required");
        try {
            if (point == null || point.world != p.serverLevel() || !point.owner.equals(p.getUUID()))
                throw refuse("candidate_world_or_owner_changed");
            source(p, false);
            if (!point.dimensions.equals(p.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO)))
                throw refuse("body_changed");
            Vec3 eye = p.getEyePosition(), direction = point.destination.subtract(eye);
            if (!finite(eye) || direction.lengthSqr() < 1e-10 || direction.lengthSqr() > 64 * 64)
                throw refuse("surface_out_of_range");
            Guard view = new Guard(p);
            BlockHitResult hit = view.clip(eye, point.destination.add(direction.normalize().scale(1e-5)));
            standable(view, hit);
            if (!hit.getBlockPos().equals(point.surface) || hit.getLocation().distanceToSqr(point.destination) > 1e-10)
                throw refuse("surface_changed");
            if (!entrancePreview) visible(p, eye, point.destination, ignored);
            clearance(p, view, point.standingBody(), point.destination.y, true, ignored, entrancePreview);
            return new PointResult(point, "");
        } catch (Refusal failure) { return new PointResult(null, failure.reason); }
    }
    /** A preview may show occupied A; an actual admission excludes only the selected traveler. */
    static String consentPoint(ServerPlayer p, Point point, ServerPlayer traveler, boolean entrance) {
        PointResult result = revalidatePoint(p, point, entrance ? traveler : null, entrance && traveler == null);
        return result.reason();
    }
    private static void source(ServerPlayer p, boolean standing) {
        if (standing && (p.getPose() != Pose.STANDING || p.isShiftKeyDown())) throw refuse("standing_required");
        String reason = SafeLandingProbeWorld.sourceRefusal(p);
        if (!reason.isEmpty()) throw refuse(reason);
    }
    private static void standable(Guard view, BlockHitResult hit) {
        if (hit.getType() != HitResult.Type.BLOCK) throw refuse("no_visible_surface");
        if (!view.getFluidState(hit.getBlockPos()).isEmpty()) throw refuse("first_surface_fluid");
        if (hit.isInside() || hit.getDirection() != Direction.UP) throw refuse("first_surface_not_up");
    }
    private static void visible(ServerPlayer p, Vec3 start, Vec3 end, Entity ignored) {
        double distance = start.distanceToSqr(end);
        for (Entity entity : entities(p, new AABB(start, end).inflate(.001), ignored)) {
            AABB body = entity.getBoundingBox();
            if (body.contains(start)) throw refuse("surface_entity_occluded");
            var hit = body.clip(start, end);
            if (hit.isPresent() && start.distanceToSqr(hit.get()) <= distance) throw refuse("surface_entity_occluded");
        }
    }

    /** One bounded scan supplies the primary, every secondary, and their entity-occlusion checks. */
    static VolleyResult acquireVolley(ServerPlayer p) {
        if (p == null || !p.server.isSameThread() || !finite(p.getEyePosition()) || !finite(p.getLookAngle()))
            return new VolleyResult(List.of(), "not_ready");
        try {
            Vec3 start = p.getEyePosition(), look = p.getLookAngle();
            if (look.lengthSqr() < .999 || look.lengthSqr() > 1.001) throw refuse("invalid_server_aim");
            look = look.normalize();
            // Count native candidates before eligibility, with no per-target rescans or 8-result early exit.
            List<Entity> scanned = entities(p, coneBounds(start, look), null, false);
            Vec3 end = start.add(look.scale(SpatialPincerCorridorRules.RANGE));
            Guard view = new Guard(p); BlockHitResult block = view.clip(start, end);
            double limit = block.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : start.distanceToSqr(block.getLocation());
            Entity first = nearest(scanned, start, end, limit, null, false);
            if (!(first instanceof LivingEntity primary) || !hostile(p, primary)) throw refuse("no_target");
            Plan main = capture(p, primary);
            if (!targetVisible(p, main, scanned, start)) throw refuse("target_occluded");
            List<Candidate> secondary = new ArrayList<>();
            for (Entity entity : scanned) {
                if (entity == primary || !(entity instanceof LivingEntity target) || !hostile(p, target)) continue;
                Plan plan = capture(p, target); Vec3 delta = plan.endpoint.subtract(start);
                double distance = delta.length(), cosine = distance > 0 ? look.dot(delta.scale(1 / distance)) : Double.NaN;
                if (!SpatialPincerCorridorRules.inCone(cosine, distance)) continue;
                if (!targetVisible(p, plan, scanned, start)) continue;
                secondary.add(new Candidate(plan, Math.acos(Math.max(-1, Math.min(1, cosine))), distance));
            }
            secondary.sort(Comparator.comparingDouble(Candidate::angle).thenComparingDouble(Candidate::distance)
                    .thenComparing(candidate -> candidate.plan.targetUuid));
            List<Plan> plans = new ArrayList<>(); plans.add(main);
            for (Candidate candidate : secondary) {
                if (plans.size() == SpatialPincerCorridorRules.MAX_TARGETS) break;
                plans.add(candidate.plan);
            }
            for (Plan plan : plans) if (!sameTarget(plan)) throw refuse("target_changed");
            return new VolleyResult(plans, "");
        } catch (Refusal refusal) { return new VolleyResult(List.of(), refusal.reason); }
    }
    /** Retained primary-only query for focused callers; its scan uses the same whole-volley bound. */
    static Result acquire(ServerPlayer p) {
        VolleyResult result = acquireVolley(p);
        return result.valid() ? new Result(result.plans.getFirst(), "") : new Result(null, result.reason);
    }
    private static Plan capture(ServerPlayer p, LivingEntity target) {
        Vec3 endpoint = target.getBoundingBox().getCenter();
        if (!finite(endpoint)) throw refuse("invalid_target");
        return new Plan(p.serverLevel(), p, target, target.getUUID(), target.getId(), endpoint);
    }
    /** Axis bounds of a radius-64 spherical cone, including its origin. */
    private static AABB coneBounds(Vec3 start, Vec3 look) {
        double[] direction = {look.x, look.y, look.z}, minimum = new double[3], maximum = new double[3];
        double cosine = Math.cos(Math.toRadians(SpatialPincerCorridorRules.CONE_HALF_ANGLE_DEGREES));
        double sine = Math.sin(Math.toRadians(SpatialPincerCorridorRules.CONE_HALF_ANGLE_DEGREES));
        for (int axis = 0; axis < 3; axis++) {
            double component = direction[axis], tangent = Math.sqrt(Math.max(0, 1 - component * component)) * sine;
            minimum[axis] = SpatialPincerCorridorRules.RANGE * Math.min(0, component <= -cosine ? -1 : component * cosine - tangent);
            maximum[axis] = SpatialPincerCorridorRules.RANGE * Math.max(0, component >= cosine ? 1 : component * cosine + tangent);
        }
        return new AABB(start.x + minimum[0], start.y + minimum[1], start.z + minimum[2],
                start.x + maximum[0], start.y + maximum[1], start.z + maximum[2]).inflate(1e-7);
    }
    private static boolean targetVisible(ServerPlayer p, Plan plan, List<Entity> scanned, Vec3 start) {
        Guard view = new Guard(p);
        if (view.clip(start, plan.endpoint).getType() != HitResult.Type.MISS) return false;
        return nearest(scanned, start, plan.endpoint, Double.POSITIVE_INFINITY, null, false) == plan.target;
    }
    /** Any competing body at the nearest contact distance blocks target authority. */
    private static Entity nearest(List<Entity> entities, Vec3 start, Vec3 end, double limit, Entity ignored, boolean padded) {
        Entity first = null; double closest = limit; boolean tied = false;
        for (Entity entity : entities) {
            if (entity == ignored) continue;
            AABB body = padded ? entity.getBoundingBox().inflate((double) .3F) : entity.getBoundingBox();
            Vec3 contact = body.contains(start) ? start : body.clip(start, end).orElse(null);
            if (contact == null) continue;
            double distance = start.distanceToSqr(contact);
            if (distance < closest) { first = entity; closest = distance; tied = false; }
            else if (distance == closest && first != null && entity != first) tied = true;
        }
        if (tied) throw refuse("entity_collision_tie");
        return first;
    }

    static Vec3 origin(Point point) {
        return point.destination().add(0, SpatialPincerCorridorRules.ORIGIN_HEIGHT, 0);
    }
    static AABB needleBody(Vec3 origin) {
        return new AABB(origin.x - .125, origin.y, origin.z - .125, origin.x + .125, origin.y + .25, origin.z + .125);
    }
    static String point(ServerPlayer p, Point point, DisplacedNeedleEntity pending) {
        var assessed = revalidatePoint(p, point, pending);
        if (!assessed.valid()) return assessed.reason();
        try { originClear(p, origin(point), pending); return ""; }
        catch (Refusal refusal) { return refusal.reason; }
    }
    static String validate(Plan plan, Point point, DisplacedNeedleEntity pending) {
        if (plan == null || plan.world != plan.owner.serverLevel() || !sameTarget(plan)) return "target_changed";
        if (!currentlyVisible(plan, pending)) return "target_occluded";
        String reason = point(plan.owner, point, pending); if (!reason.isEmpty()) return reason;
        Vec3 origin = origin(point);
        if (!SpatialPincerCorridorRules.path(plan.owner.getEyePosition().distanceTo(origin), origin.distanceTo(plan.endpoint)))
            return "out_of_range";
        try {
            Guard view = new Guard(plan.owner);
            if (view.clip(plan.owner.getEyePosition(), origin).getType() != HitResult.Type.MISS
                    || view.clip(origin, plan.endpoint).getType() != HitResult.Type.MISS) return "blocked";
            // Each assigned path independently respects the nearest native body, including old needles.
            Vec3 eye = plan.owner.getEyePosition();
            if (nearest(entities(plan.owner, new AABB(eye, origin).inflate(.301), pending), eye, origin,
                    Double.POSITIVE_INFINITY, pending, true) != null) return "source_entity_occluded";
            Entity first = nearest(entities(plan.owner, new AABB(origin, plan.endpoint).inflate(.301), pending), origin,
                    plan.endpoint, Double.POSITIVE_INFINITY, pending, true);
            if (first != null && first != plan.target) return "target_occluded";
            // A moving frozen target may be missed; no path or endpoint ever follows it.
            return sameTarget(plan) ? "" : "target_changed";
        } catch (Refusal refusal) { return refusal.reason; }
    }
    static boolean sameTarget(Plan plan) {
        return sameTargetState(plan) && hostile(plan.owner, plan.target);
    }
    /** Final native eligibility only, without geometry reads; supported target classes are reviewed. */
    static boolean sameTargetState(Plan plan) {
        return plan != null && plan.world == plan.owner.serverLevel()
                && plan.target.getUUID().equals(plan.targetUuid) && plan.target.getId() == plan.targetId
                && hostileState(plan.owner, plan.target);
    }
    /** A live frozen identity loses attack authority when its current body loses actual owner LOS. */
    static boolean currentlyVisible(Plan plan) { return currentlyVisible(plan, null); }
    static boolean currentlyVisible(Plan plan, Entity ignored) {
        try {
            if (!sameTarget(plan)) return false;
            Vec3 start = plan.owner.getEyePosition(), position = plan.target.position(); AABB body = plan.target.getBoundingBox();
            Vec3 center = body.getCenter();
            if (!finite(start) || !finite(center) || start.distanceToSqr(center) > SpatialPincerCorridorRules.RANGE * SpatialPincerCorridorRules.RANGE)
                return false;
            Guard view = new Guard(plan.owner);
            if (view.clip(start, center).getType() != HitResult.Type.MISS) return false;
            Entity first = nearest(entities(plan.owner, new AABB(start, center).inflate(.001), ignored),
                    start, center, Double.POSITIVE_INFINITY, ignored, false);
            return first == plan.target && sameTarget(plan) && position.equals(plan.target.position())
                    && body.equals(plan.target.getBoundingBox()) && start.equals(plan.owner.getEyePosition());
        } catch (RuntimeException refusal) {
            // Native collision/post-tick callers cannot let a mod's failed read grant attack authority.
            // Errors keep their normal escalation; a failed visibility read retires only this assignment.
            return false;
        }
    }
    static boolean hostile(ServerPlayer p, LivingEntity target) {
        if (!hostileState(p, target)) return false;
        Vec3 position = target.position(); AABB body = target.getBoundingBox();
        try {
            new Guard(p).volume(body);
            return hostileState(p, target) && position.equals(target.position()) && body.equals(target.getBoundingBox());
        } catch (Refusal refusal) { return false; }
    }
    /** No geometry callbacks after the last guarded world assessment. */
    private static boolean hostileState(ServerPlayer p, LivingEntity target) {
        return p != null && HighPowerCombat.hostileTarget(target) && HighPowerCombat.supported(target)
                && target.level() == p.level() && target.isAlive() && !target.isRemoved()
                && !target.isSpectator() && !target.isInvulnerable() && !target.isInvisibleTo(p)
                && HighPowerCombat.unsupportedReason(target).isEmpty() && !p.isAlliedTo(target) && !target.isAlliedTo(p)
                && p.serverLevel().getEntity(target.getUUID()) == target && p.serverLevel().getEntity(target.getId()) == target;
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
        return entities(p, volume, ignored, true);
    }
    private static List<Entity> entities(ServerPlayer p, AABB volume, Entity ignored, boolean validateVolume) {
        // The candidate query enumerates only resident entity sections and never loads chunks.
        // Empty cone tails have no authority; selected targets and all actual paths get guarded reads.
        if (validateVolume) new Guard(p).volume(volume);
        List<Entity> result = new ArrayList<>(); int[] scanned = {0};
        p.serverLevel().getEntities(EntityTypeTest.forClass(Entity.class), volume, e -> {
            if (++scanned[0] > SpatialPincerCorridorRules.CANDIDATE_LIMIT) throw refuse("entity_scan_budget");
            return e != p && e != ignored && !e.isSpectator() && (e.isPickable() || e instanceof LivingEntity && e.isAlive());
        }, result, SpatialPincerCorridorRules.CANDIDATE_LIMIT + 1);
        if (result.size() > SpatialPincerCorridorRules.CANDIDATE_LIMIT) throw refuse("entity_scan_budget");
        return result;
    }
    private static void clearance(ServerPlayer p, Guard view, AABB body, double feet, boolean hazard) {
        clearance(p, view, body, feet, hazard, null);
    }
    private static void clearance(ServerPlayer p, Guard view, AABB body, double feet, boolean hazard, Entity ignored) {
        clearance(p, view, body, feet, hazard, ignored, false);
    }
    private static void clearance(ServerPlayer p, Guard view, AABB body, double feet, boolean hazard, Entity ignored, boolean skipEntities) {
        if (!finite(new Vec3(body.minX, body.minY, body.minZ)) || !finite(new Vec3(body.maxX, body.maxY, body.maxZ))
                || body.getXsize() <= 0 || body.getYsize() <= 0 || body.getZsize() <= 0
                || body.getXsize() > 4 || body.getYsize() > 4 || body.getZsize() > 4
                || Math.abs(body.minY - feet) > 1e-5) throw refuse("destination_body_invalid");
        AABB support = new AABB(body.minX, body.minY - SafeLandingProbeGeometry.SUPPORT_DEPTH,
                body.minZ, body.maxX, body.minY, body.maxZ);
        AABB envelope = body.minmax(support); view.volume(envelope); view.scope = envelope.inflate(1.001);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(envelope.minX, envelope.minY, envelope.minZ),
                BlockPos.containing(Math.nextDown(envelope.maxX), Math.nextDown(envelope.maxY), Math.nextDown(envelope.maxZ)))) {
            if (hazard && destinationHazard(view.getBlockState(pos))) throw refuse("destination_hazard");
            if (!view.getFluidState(pos).isEmpty()) throw refuse("destination_fluid");
        }
        VoxelShape bodyShape = Shapes.create(body), needed = Shapes.create(support), supported = Shapes.empty();
        EntityCollisionContext context = new LandingCollisionContext(p, feet);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(envelope.minX - 1, envelope.minY - 1, envelope.minZ - 1),
                BlockPos.containing(envelope.maxX + 1, envelope.maxY + 1, envelope.maxZ + 1))) {
            if (view.world.isOutsideBuildHeight(pos)) continue;
            VoxelShape local = view.getBlockState(pos).getCollisionShape(view, pos, context); view.shape(local);
            VoxelShape solid = local.move(pos.getX(), pos.getY(), pos.getZ());
            if (Shapes.joinIsNotEmpty(bodyShape, solid, BooleanOp.AND)) throw refuse("destination_block_collision");
            if (Shapes.joinIsNotEmpty(needed, solid, BooleanOp.AND)) {
                long cells = 1;
                for (Direction.Axis axis : Direction.Axis.values()) {
                    Set<Double> coords = new HashSet<>();
                    for (double value : supported.getCoords(axis)) coords.add(value);
                    for (double value : solid.getCoords(axis)) coords.add(value);
                    cells *= Math.max(1, coords.size() - 1);
                    if (cells > 8192) throw refuse("support_grid_budget");
                }
                supported = Shapes.or(supported, solid);
            }
        }
        if (Shapes.joinIsNotEmpty(needed, supported, BooleanOp.ONLY_FIRST)) throw refuse("destination_floor_unsupported");
        if (!skipEntities) for (Entity entity : entities(p, body.inflate(1e-7), ignored))
            if (entity.getBoundingBox().intersects(body)) throw refuse("destination_entity_collision");
    }
    private static boolean destinationHazard(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK)
                || (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) && state.getValue(CampfireBlock.LIT)
                || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.COBWEB) || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY);
    }
    private static final class LandingCollisionContext extends EntityCollisionContext {
        LandingCollisionContext(ServerPlayer player, double feet) {
            super(player.isDescending(), feet, player.getMainHandItem(), player::canStandOnFluid, player);
        }
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
            if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 262144) throw refuse("volume_budget");
            for (int x = x0 >> 4; x <= x1 >> 4; x++) for (int z = z0 >> 4; z <= z1 >> 4; z++)
                if (world.getChunkSource().getChunkNow(x, z) == null) throw refuse("unloaded_chunk");
            for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1))
                if (!world.mayInteract(player, pos)) throw refuse("interaction_denied");
        }
        BlockHitResult clip(Vec3 from, Vec3 to) {
            if (!finite(from) || !finite(to) || from.distanceToSqr(to) > 64 * 64 + .002) throw refuse("invalid_ray");
            // Native traversal stops on its first COLLIDER/ANY hit. Only cells it actually visits,
            // including shape-neighbor reads, pass through chunk() and its non-loading fence.
            // Preflighting an entire 64m endpoint would reject a nearby floor whenever the unused
            // ray tail extends below build height, outside the border, or into an unloaded chunk.
            scope = new AABB(from, to).inflate(1.001);
            BlockHitResult hit = clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player) {
                @Override public VoxelShape getBlockShape(BlockState state, BlockGetter blocks, BlockPos pos) {
                    VoxelShape shape = super.getBlockShape(state, blocks, pos); Guard.this.shape(shape); return shape;
                }
                @Override public VoxelShape getFluidShape(FluidState fluid, BlockGetter blocks, BlockPos pos) {
                    VoxelShape shape = super.getFluidShape(fluid, blocks, pos); Guard.this.shape(shape); return shape;
                }
            });
            Vec3 reached = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
            AABB actual = new AABB(from, reached);
            if (player.serverLevel() != world || player.isRemoved()) throw refuse("world_changed");
            if (actual.minY < world.getMinBuildHeight() || actual.maxY > world.getMaxBuildHeight())
                throw refuse("outside_build_height");
            // Exact geometric bounds complement the per-cell border checks, including fractional borders.
            if (!world.getWorldBorder().isWithinBounds(actual)) throw refuse("outside_world_border");
            return hit;
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
