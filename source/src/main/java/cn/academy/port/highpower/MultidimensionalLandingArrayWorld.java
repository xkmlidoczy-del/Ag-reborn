package cn.academy.port.highpower;

import cn.academy.port.highpower.core.MultidimensionalLandingArrayRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** NEW351 read-only geometry. A source moves a real needle, never the player or a target. */
public final class MultidimensionalLandingArrayWorld {
    public record Plan(ServerLevel world, ServerPlayer owner, LivingEntity target, UUID targetUuid,
                       int targetId, Vec3 endpoint) {}
    public record VolleyResult(List<Plan> plans, String reason) {
        public VolleyResult { plans = List.copyOf(plans); }
        public boolean valid() { return !plans.isEmpty() && reason.isEmpty(); }
    }
    public record PointResult(Point point, String reason) { public boolean valid() { return point != null && reason.isEmpty(); } }
    /** A blocker position is the actual first native solid/body contact, never an alternate endpoint. */
    public record SweepResult(Entity entity, Vec3 position, boolean blocked, String reason) {
        public boolean valid() { return reason.isEmpty(); }
    }
    private record Candidate(Plan plan, double angle, double distance) {}
    private MultidimensionalLandingArrayWorld() {}

    /** Closed UP-surface capture. Held free-point mode is initially restricted to these real surfaces. */
    public static final class Point {
        private final ServerLevel world;
        private final UUID owner;
        private final BlockPos surface;
        private final Vec3 destination;
        private final float yaw, pitch;
        private Point(ServerPlayer player, BlockHitResult hit) {
            world = player.serverLevel(); owner = player.getUUID(); surface = hit.getBlockPos().immutable();
            destination = hit.getLocation(); yaw = player.getYRot(); pitch = player.getXRot();
        }
        public ServerLevel world() { return world; }
        public UUID owner() { return owner; }
        public Vec3 destination() { return destination; }
        public float yaw() { return yaw; }
        public float pitch() { return pitch; }
    }

    public static PointResult capturePoint(ServerPlayer player) {
        if (!thread(player)) return new PointResult(null, "server_thread_required");
        try {
            source(player);
            Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
            if (!finite(eye) || !finite(look) || look.lengthSqr() < .999 || look.lengthSqr() > 1.001)
                throw refuse("invalid_server_aim");
            Guard view = new Guard(player); Budget budget = new Budget();
            BlockHitResult hit = view.clip(eye, eye.add(look.scale(MultidimensionalLandingArrayRules.RANGE)));
            standable(view, hit);
            visible(player, eye, hit.getLocation(), null, budget);
            Point point = new Point(player, hit);
            anchorSupport(view, point);
            originClear(player, view, origin(point), null, budget);
            if (!MultidimensionalLandingArrayRules.path(sourceDistance(player, point), 0)) throw refuse("out_of_range");
            return new PointResult(point, "");
        } catch (RuntimeException failure) { return new PointResult(null, reason(failure)); }
    }
    public static PointResult revalidatePoint(ServerPlayer player, Point point) {
        if (!thread(player)) return new PointResult(null, "server_thread_required");
        try {
            assessPoint(player, point, null, new Guard(player), new Budget());
            return new PointResult(point, "");
        } catch (RuntimeException failure) { return new PointResult(null, reason(failure)); }
    }
    public static String point(ServerPlayer player, Point point, Entity pending) {
        if (!thread(player)) return "server_thread_required";
        try { assessPoint(player, point, pending, new Guard(player), new Budget()); return ""; }
        catch (RuntimeException failure) { return reason(failure); }
    }
    private static void assessPoint(ServerPlayer player, Point point, Entity pending, Guard view, Budget budget) {
        source(player);
        if (point == null || point.world != player.serverLevel() || !point.owner.equals(player.getUUID()))
            throw refuse("candidate_world_or_owner_changed");
        Vec3 eye = player.getEyePosition(), delta = point.destination.subtract(eye);
        if (!finite(eye) || !finite(point.destination) || delta.lengthSqr() < 1e-10
                || !MultidimensionalLandingArrayRules.path(sourceDistance(player, point), 0)) throw refuse("out_of_range");
        BlockHitResult hit = view.clip(eye, point.destination.add(delta.normalize().scale(1e-5)));
        standable(view, hit);
        if (!hit.getBlockPos().equals(point.surface) || hit.getLocation().distanceToSqr(point.destination) > 1e-10)
            throw refuse("surface_changed");
        visible(player, eye, point.destination, pending, budget);
        anchorSupport(view, point);
        originClear(player, view, origin(point), pending, budget);
    }
    private static void source(ServerPlayer player) {
        String reason = SafeLandingProbeWorld.sourceRefusal(player);
        if (!reason.isEmpty()) throw refuse(reason);
    }
    private static void standable(Guard view, BlockHitResult hit) {
        if (hit.getType() != HitResult.Type.BLOCK) throw refuse("no_visible_surface");
        if (hit.isInside() || hit.getDirection() != Direction.UP) throw refuse("first_surface_not_up");
        if (!view.getFluidState(hit.getBlockPos()).isEmpty()) throw refuse("first_surface_fluid");
        if (hazard(view.getBlockState(hit.getBlockPos()))) throw refuse("source_hazard");
    }
    public static Vec3 origin(Point point) { return point.destination.add(0, MultidimensionalLandingArrayRules.ORIGIN_HEIGHT, 0); }
    public static double sourceDistance(ServerPlayer player, Point point) {
        return player.getEyePosition().distanceTo(point.destination) + point.destination.distanceTo(origin(point));
    }
    public static AABB needleBody(Vec3 position) {
        return new AABB(position.x - .125, position.y, position.z - .125, position.x + .125, position.y + .25, position.z + .125);
    }
    /** All six visible, separately marked sources remain prerequisites, even in held free-point mode. */
    public static String anchors(ServerPlayer player, List<Point> points, Entity ignored) {
        if (!thread(player)) return "server_thread_required";
        if (points == null || points.size() != MultidimensionalLandingArrayRules.POINTS) return "six_anchors_required";
        try {
            Guard view = new Guard(player); Budget budget = new Budget();
            for (int i = 0; i < points.size(); i++) {
                Point point = points.get(i);
                for (int j = 0; j < i; j++) if (point != null && points.get(j) != null
                        && point.destination.distanceToSqr(points.get(j).destination) < 1e-10) throw refuse("duplicate_anchor");
                assessPoint(player, point, ignored, view, budget);
            }
            return "";
        } catch (RuntimeException failure) { return reason(failure); }
    }

    /** One resident-section query counts every native candidate before filtering, and refuses the 65th. */
    public static VolleyResult acquireVolley(ServerPlayer player) {
        if (!thread(player)) return new VolleyResult(List.of(), "server_thread_required");
        try {
            source(player);
            Vec3 start = player.getEyePosition(), look = player.getLookAngle();
            if (!finite(start) || !finite(look) || look.lengthSqr() < .999 || look.lengthSqr() > 1.001)
                throw refuse("invalid_server_aim");
            look = look.normalize();
            Budget budget = new Budget(); Guard view = new Guard(player);
            // This scan only enumerates already resident sections. All selected bodies and actual rays are guarded.
            List<Entity> scanned = entities(player, coneBounds(start, look), null, budget, false);
            Vec3 end = start.add(look.scale(MultidimensionalLandingArrayRules.RANGE));
            Entity bodyFirst = nearest(scanned, start, end, Double.POSITIVE_INFINITY, null, false);
            // A body is itself a first blocker. Its unused ray tail cannot require or load a chunk.
            Vec3 firstStop = bodyFirst == null ? end : contact(bodyFirst, start, end, false);
            BlockHitResult solid = view.clip(start, firstStop);
            double limit = solid.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : start.distanceToSqr(solid.getLocation());
            Entity first = nearest(scanned, start, end, limit, null, false);
            if (!(first instanceof LivingEntity target) || !hostile(player, target, view)) throw refuse("no_target");
            Plan primary = capture(player, target);
            if (!targetVisible(primary, scanned, start, null, view)) throw refuse("target_occluded");
            List<Candidate> secondary = new ArrayList<>();
            for (Entity entity : scanned) {
                if (entity == target || !(entity instanceof LivingEntity next) || !hostile(player, next, view)) continue;
                Plan plan = capture(player, next); Vec3 delta = plan.endpoint.subtract(start);
                double distance = delta.length(), cosine = distance > 0 ? look.dot(delta.scale(1 / distance)) : Double.NaN;
                if (!MultidimensionalLandingArrayRules.inCone(cosine, distance) || !targetVisible(plan, scanned, start, null, view)) continue;
                secondary.add(new Candidate(plan, Math.acos(Math.max(-1, Math.min(1, cosine))), distance));
            }
            secondary.sort(Comparator.comparingDouble(Candidate::angle).thenComparingDouble(Candidate::distance)
                    .thenComparing(candidate -> candidate.plan.targetUuid));
            List<Plan> plans = new ArrayList<>(); plans.add(primary);
            for (Candidate candidate : secondary) {
                if (plans.size() == MultidimensionalLandingArrayRules.MAX_TARGETS) break;
                plans.add(candidate.plan);
            }
            for (Plan plan : plans) if (!sameTargetState(plan)) throw refuse("target_changed");
            return new VolleyResult(plans, "");
        } catch (RuntimeException failure) { return new VolleyResult(List.of(), reason(failure)); }
    }
    private static Plan capture(ServerPlayer player, LivingEntity target) {
        Vec3 endpoint = target.getBoundingBox().getCenter();
        if (!finite(endpoint)) throw refuse("invalid_target");
        return new Plan(player.serverLevel(), player, target, target.getUUID(), target.getId(), endpoint);
    }
    private static AABB coneBounds(Vec3 start, Vec3 look) {
        double[] direction = {look.x, look.y, look.z}, min = new double[3], max = new double[3];
        double cosine = Math.cos(Math.toRadians(MultidimensionalLandingArrayRules.CONE_HALF_ANGLE_DEGREES));
        double sine = Math.sin(Math.toRadians(MultidimensionalLandingArrayRules.CONE_HALF_ANGLE_DEGREES));
        for (int axis = 0; axis < 3; axis++) {
            double component = direction[axis], tangent = Math.sqrt(Math.max(0, 1 - component * component)) * sine;
            min[axis] = MultidimensionalLandingArrayRules.RANGE * Math.min(0, component <= -cosine ? -1 : component * cosine - tangent);
            max[axis] = MultidimensionalLandingArrayRules.RANGE * Math.max(0, component >= cosine ? 1 : component * cosine + tangent);
        }
        return new AABB(start.x + min[0], start.y + min[1], start.z + min[2], start.x + max[0], start.y + max[1], start.z + max[2]).inflate(1e-7);
    }

    public static String validate(Plan plan, Point point, Entity pending) { return validate(plan, point, point, pending); }
    public static String validate(Plan plan, Point mandatoryAnchor, Point actualSource, Entity pending) {
        if (plan == null || !thread(plan.owner)) return "server_thread_required";
        try {
            if (!sameTargetState(plan)) throw refuse("target_changed");
            Guard view = new Guard(plan.owner); Budget budget = new Budget();
            assessPoint(plan.owner, mandatoryAnchor, pending, view, budget);
            if (actualSource != mandatoryAnchor) assessPoint(plan.owner, actualSource, pending, view, budget);
            if (!currentlyVisible(plan, pending, view, budget)) throw refuse("target_occluded");
            Vec3 origin = origin(actualSource), eye = plan.owner.getEyePosition();
            if (!MultidimensionalLandingArrayRules.path(sourceDistance(plan.owner, actualSource), origin.distanceTo(plan.endpoint)))
                throw refuse("out_of_range");
            if (pending != null && (!origin.equals(pending.position()) || !needleBody(origin).equals(pending.getBoundingBox())))
                throw refuse("medium_changed");
            if (view.clip(eye, origin).getType() != HitResult.Type.MISS || view.clip(origin, plan.endpoint).getType() != HitResult.Type.MISS)
                throw refuse("blocked");
            if (nearest(entities(plan.owner, new AABB(eye, origin).inflate(.301), pending, budget, true),
                    eye, origin, Double.POSITIVE_INFINITY, pending, true) != null) throw refuse("source_entity_occluded");
            Entity first = nearest(entities(plan.owner, new AABB(origin, plan.endpoint).inflate(.301), pending, budget, true),
                    origin, plan.endpoint, Double.POSITIVE_INFINITY, pending, true);
            if (first != null && first != plan.target) throw refuse("target_occluded");
            return sameTargetState(plan) ? "" : "target_changed";
        } catch (RuntimeException failure) { return reason(failure); }
    }
    public static boolean sameTargetState(Plan plan) {
        return plan != null && plan.world != null && plan.owner != null && plan.target != null && plan.targetUuid != null
                && plan.world == plan.owner.serverLevel() && plan.target.getUUID().equals(plan.targetUuid)
                && plan.target.getId() == plan.targetId && hostileState(plan.owner, plan.target);
    }
    public static boolean sameTarget(Plan plan) {
        try { return sameTargetState(plan) && hostile(plan.owner, plan.target, new Guard(plan.owner)); }
        catch (RuntimeException failure) { return false; }
    }
    public static boolean currentlyVisible(Plan plan) { return currentlyVisible(plan, null); }
    public static boolean currentlyVisible(Plan plan, Entity ignored) {
        try { return sameTargetState(plan) && thread(plan.owner) && currentlyVisible(plan, ignored, new Guard(plan.owner), new Budget()); }
        catch (RuntimeException failure) { return false; }
    }
    /** Current-body visibility only revokes authority; it never changes the frozen aiming endpoint. */
    private static boolean currentlyVisible(Plan plan, Entity ignored, Guard view, Budget budget) {
        if (!sameTargetState(plan)) return false;
        Vec3 start = plan.owner.getEyePosition(), position = plan.target.position(); AABB body = plan.target.getBoundingBox();
        Vec3 center = body.getCenter();
        if (!finite(start) || !finite(center) || start.distanceToSqr(center) > MultidimensionalLandingArrayRules.RANGE * MultidimensionalLandingArrayRules.RANGE)
            return false;
        view.volume(body);
        if (view.clip(start, center).getType() != HitResult.Type.MISS) return false;
        Entity first = nearest(entities(plan.owner, new AABB(start, center).inflate(.001), ignored, budget, true),
                start, center, Double.POSITIVE_INFINITY, ignored, false);
        return first == plan.target && sameTargetState(plan) && position.equals(plan.target.position())
                && body.equals(plan.target.getBoundingBox()) && start.equals(plan.owner.getEyePosition());
    }
    private static boolean targetVisible(Plan plan, List<Entity> scanned, Vec3 start, Entity ignored, Guard view) {
        return view.clip(start, plan.endpoint).getType() == HitResult.Type.MISS
                && nearest(scanned, start, plan.endpoint, Double.POSITIVE_INFINITY, ignored, false) == plan.target;
    }
    private static boolean hostile(ServerPlayer player, LivingEntity target, Guard view) {
        if (!hostileState(player, target)) return false;
        Vec3 position = target.position(); AABB body = target.getBoundingBox();
        view.volume(body);
        return hostileState(player, target) && position.equals(target.position()) && body.equals(target.getBoundingBox());
    }
    private static boolean hostileState(ServerPlayer player, LivingEntity target) {
        return player != null && HighPowerCombat.hostileTarget(target) && HighPowerCombat.supportedMultidimensionalLandingArrayTarget(target)
                && target.level() == player.level() && target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && !target.isInvulnerable() && !target.isInvisibleTo(player) && HighPowerCombat.unsupportedReason(target).isEmpty()
                && !player.isAlliedTo(target) && !target.isAlliedTo(player)
                && player.serverLevel().getEntity(target.getUUID()) == target && player.serverLevel().getEntity(target.getId()) == target;
    }

    /** Native first-collision check for one physical step. Friendly/nonliving bodies remain obstructions. */
    public static SweepResult sweep(Plan plan, Vec3 from, Vec3 proposed, Entity medium) {
        if (plan == null || !thread(plan.owner)) return new SweepResult(null, from, true, "server_thread_required");
        try {
            if (!sameTargetState(plan) || !finite(from) || !finite(proposed)) throw refuse("target_changed");
            Guard view = new Guard(plan.owner);
            List<Entity> scanned = entities(plan.owner, new AABB(from, proposed).inflate(.301), medium, new Budget(), false);
            Entity bodyFirst = nearest(scanned, from, proposed, Double.POSITIVE_INFINITY, medium, true);
            Vec3 firstStop = bodyFirst == null ? proposed : contact(bodyFirst, from, proposed, true);
            BlockHitResult block = view.clip(from, firstStop);
            Vec3 stop = block.getType() == HitResult.Type.MISS ? firstStop : block.getLocation();
            double limit = block.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : from.distanceToSqr(stop);
            view.volume(new AABB(from, stop).inflate(.301));
            Entity first = nearest(scanned, from, stop, limit, medium, true);
            if (first != null) {
                Vec3 contact = contact(first, from, stop, true);
                view.volume(first.getBoundingBox());
                return new SweepResult(first, contact, false, "");
            }
            return new SweepResult(null, stop, block.getType() != HitResult.Type.MISS,
                    block.getType() == HitResult.Type.MISS ? "" : "block_collision");
        } catch (RuntimeException failure) { return new SweepResult(null, from, true, reason(failure)); }
    }
    public static String loadedSegment(ServerPlayer player, Vec3 from, Vec3 to) {
        if (!thread(player)) return "server_thread_required";
        try { return new Guard(player).clip(from, to).getType() == HitResult.Type.MISS ? "" : "blocked"; }
        catch (RuntimeException failure) { return reason(failure); }
    }
    /** Exact native contact may spend only its assigned target; padding cannot reach through a wall. */
    public static boolean contactClear(ServerPlayer player, Vec3 start, Vec3 end, LivingEntity target, Entity medium) {
        if (!thread(player)) return false;
        try {
            if (!finite(start) || !finite(end) || !hostileState(player, target)) return false;
            Guard view = new Guard(player); view.volume(target.getBoundingBox());
            if (view.clip(start, end).getType() != HitResult.Type.MISS
                    || view.clip(start, target.getBoundingBox().getCenter()).getType() != HitResult.Type.MISS) return false;
            AABB padded = target.getBoundingBox().inflate((double) .3F);
            if (end.x < padded.minX || end.x > padded.maxX || end.y < padded.minY || end.y > padded.maxY || end.z < padded.minZ || end.z > padded.maxZ)
                return false;
            List<Entity> scanned = entities(player, new AABB(start, end).inflate(.301), medium, new Budget(), true);
            return nearest(scanned, start, end, Double.POSITIVE_INFINITY, medium, true) == target;
        } catch (RuntimeException failure) { return false; }
    }
    private static Entity nearest(List<Entity> entities, Vec3 start, Vec3 end, double limit, Entity ignored, boolean padded) {
        Entity first = null; double closest = limit; boolean tied = false;
        for (Entity entity : entities) {
            if (entity == ignored) continue;
            AABB body = padded ? entity.getBoundingBox().inflate((double) .3F) : entity.getBoundingBox();
            if (!validBody(body)) throw refuse("invalid_entity_body");
            Vec3 contact = body.contains(start) ? start : body.clip(start, end).orElse(null);
            if (contact == null) continue;
            double distance = start.distanceToSqr(contact);
            if (distance < closest) { first = entity; closest = distance; tied = false; }
            else if (distance == closest && first != null && entity != first) tied = true;
        }
        if (tied) throw refuse("entity_collision_tie");
        return first;
    }
    private static Vec3 contact(Entity entity, Vec3 start, Vec3 end, boolean padded) {
        AABB body = padded ? entity.getBoundingBox().inflate((double) .3F) : entity.getBoundingBox();
        if (!validBody(body)) throw refuse("invalid_entity_body");
        return body.contains(start) ? start : body.clip(start, end).orElseThrow(() -> refuse("entity_changed"));
    }
    private static void visible(ServerPlayer player, Vec3 start, Vec3 end, Entity ignored, Budget budget) {
        if (nearest(entities(player, new AABB(start, end).inflate(.001), ignored, budget, true),
                start, end, Double.POSITIVE_INFINITY, ignored, false) != null) throw refuse("surface_entity_occluded");
    }
    private static final class Budget { int candidates; }
    private static List<Entity> entities(ServerPlayer player, AABB box, Entity ignored, Budget budget, boolean validateVolume) {
        if (validateVolume) new Guard(player).volume(box);
        List<Entity> result = new ArrayList<>();
        player.serverLevel().getEntities(EntityTypeTest.forClass(Entity.class), box, entity -> {
            if (++budget.candidates > MultidimensionalLandingArrayRules.CANDIDATE_LIMIT) throw refuse("entity_scan_budget");
            return entity != player && entity != ignored && !entity.isSpectator()
                    && (entity.isPickable() || entity instanceof LivingEntity && entity.isAlive());
        }, result, MultidimensionalLandingArrayRules.CANDIDATE_LIMIT + 1);
        return result;
    }
    private static void originClear(ServerPlayer player, Guard view, Vec3 origin, Entity pending, Budget budget) {
        AABB body = needleBody(origin); view.volume(body); view.scope = body.inflate(1.001);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX - 1, body.minY - 1, body.minZ - 1),
                BlockPos.containing(body.maxX + 1, body.maxY + 1, body.maxZ + 1))) {
            if (view.world.isOutsideBuildHeight(pos)) continue;
            BlockState state = view.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(view, pos, pending == null ? CollisionContext.empty() : CollisionContext.of(pending));
            view.shape(shape);
            for (AABB box : shape.toAabbs()) if (box.move(pos).intersects(body)) throw refuse("origin_blocked");
            if (new AABB(pos).intersects(body) && (!view.getFluidState(pos).isEmpty() || hazard(state))) throw refuse("origin_hazard");
        }
        for (Entity entity : entities(player, body.inflate(1e-7), pending, budget, true))
            if (entity.getBoundingBox().intersects(body)) throw refuse("origin_occupied");
    }
    private static void anchorSupport(Guard view, Point point) {
        Vec3 at = point.destination;
        AABB support = new AABB(at.x - .125, at.y - .125, at.z - .125, at.x + .125, at.y, at.z + .125);
        view.volume(support); view.scope = support.inflate(1.001);
        VoxelShape needed = Shapes.create(support), supplied = Shapes.empty();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(support.minX - 1, support.minY - 1, support.minZ - 1),
                BlockPos.containing(support.maxX + 1, support.maxY + 1, support.maxZ + 1))) {
            if (view.world.isOutsideBuildHeight(pos)) continue;
            VoxelShape local = view.getBlockState(pos).getCollisionShape(view, pos, CollisionContext.empty()); view.shape(local);
            VoxelShape solid = local.move(pos.getX(), pos.getY(), pos.getZ());
            if (Shapes.joinIsNotEmpty(needed, solid, BooleanOp.AND)) {
                supplied = Shapes.or(supplied, Shapes.join(needed, solid, BooleanOp.AND));
                view.shape(supplied);
            }
        }
        if (Shapes.joinIsNotEmpty(needed, supplied, BooleanOp.ONLY_FIRST)) throw refuse("anchor_unsupported");
    }
    private static boolean hazard(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK) || (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) && state.getValue(CampfireBlock.LIT)
                || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.COBWEB)
                || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY);
    }

    /** Every native shape/neighbor read is fenced, including reads made by modded collision shapes. */
    private static final class Guard implements BlockGetter {
        final ServerPlayer player; final ServerLevel world; AABB scope; int reads, shapes;
        Guard(ServerPlayer player) { this.player = player; world = player.serverLevel(); }
        void volume(AABB box) {
            if (player.serverLevel() != world || player.isRemoved() || !finite(new Vec3(box.minX, box.minY, box.minZ))
                    || !finite(new Vec3(box.maxX, box.maxY, box.maxZ))) throw refuse("world_changed");
            if (box.minY < world.getMinBuildHeight() || box.maxY > world.getMaxBuildHeight()) throw refuse("outside_build_height");
            if (!world.getWorldBorder().isWithinBounds(box)) throw refuse("outside_world_border");
            int x0 = (int) Math.floor(box.minX), x1 = (int) Math.floor(box.maxX), y0 = (int) Math.floor(box.minY), y1 = (int) Math.floor(box.maxY),
                    z0 = (int) Math.floor(box.minZ), z1 = (int) Math.floor(box.maxZ);
            long nx = (long) x1 - x0 + 1, ny = (long) y1 - y0 + 1, nz = (long) z1 - z0 + 1;
            if (nx < 1 || ny < 1 || nz < 1 || nx > 1_048_576 || ny > 1_048_576 || nz > 1_048_576
                    || nx * ny * nz > 1_048_576) throw refuse("volume_budget");
            for (int x = x0 >> 4; x <= x1 >> 4; x++) for (int z = z0 >> 4; z <= z1 >> 4; z++)
                if (world.getChunkSource().getChunkNow(x, z) == null) throw refuse("unloaded_chunk");
            for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1))
                if (!world.mayInteract(player, pos)) throw refuse("interaction_denied");
        }
        BlockHitResult clip(Vec3 from, Vec3 to) {
            if (!finite(from) || !finite(to) || from.distanceToSqr(to) > MultidimensionalLandingArrayRules.RANGE * MultidimensionalLandingArrayRules.RANGE + .002)
                throw refuse("invalid_ray");
            // Native traversal stops at its first collider/fluid. The unused ray tail never preflights a chunk.
            scope = new AABB(from, to).inflate(1.001);
            BlockHitResult hit = clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player) {
                @Override public VoxelShape getBlockShape(BlockState state, BlockGetter blocks, BlockPos pos) {
                    VoxelShape shape = super.getBlockShape(state, blocks, pos); Guard.this.shape(shape); return shape;
                }
                @Override public VoxelShape getFluidShape(FluidState fluid, BlockGetter blocks, BlockPos pos) {
                    VoxelShape shape = super.getFluidShape(fluid, blocks, pos); Guard.this.shape(shape); return shape;
                }
            });
            Vec3 reached = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation(); AABB actual = new AABB(from, reached);
            if (player.serverLevel() != world || player.isRemoved()) throw refuse("world_changed");
            if (actual.minY < world.getMinBuildHeight() || actual.maxY > world.getMaxBuildHeight()) throw refuse("outside_build_height");
            if (!world.getWorldBorder().isWithinBounds(actual)) throw refuse("outside_world_border");
            return hit;
        }
        LevelChunk chunk(BlockPos pos) {
            if (++reads > 65536 || scope == null || !scope.intersects(new AABB(pos))) throw refuse("block_read_budget");
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
            for (Direction.Axis axis : Direction.Axis.values()) {
                var coordinates = shape.getCoords(axis);
                if (coordinates.size() > 66) throw refuse("shape_budget");
                for (double coordinate : coordinates) if (!Double.isFinite(coordinate)) throw refuse("invalid_shape");
                cells *= Math.max(1, coordinates.size() - 1);
            }
            if (cells > 8192) throw refuse("shape_budget");
            shape.forAllBoxes((a, b, c, d, e, f) -> {
                if (++shapes > 8192 || !finite(new Vec3(a, b, c)) || !finite(new Vec3(d, e, f))) throw refuse("shape_budget");
            });
        }
    }
    public static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z)
                && Math.abs(value.x) <= 30_000_000 && Math.abs(value.y) <= 30_000_000 && Math.abs(value.z) <= 30_000_000;
    }
    private static boolean validBody(AABB body) {
        return body != null && finite(new Vec3(body.minX, body.minY, body.minZ)) && finite(new Vec3(body.maxX, body.maxY, body.maxZ))
                && body.getXsize() > 0 && body.getYsize() > 0 && body.getZsize() > 0
                && body.getXsize() <= 256 && body.getYsize() <= 256 && body.getZsize() <= 256;
    }
    private static boolean thread(ServerPlayer player) { return player != null && player.server.isSameThread(); }
    private static String reason(RuntimeException failure) { return failure instanceof Refusal refusal ? refusal.reason : "assessment_failed"; }
    private static Refusal refuse(String reason) { return new Refusal(reason); }
    private static final class Refusal extends RuntimeException {
        final String reason;
        Refusal(String reason) { super(reason, null, false, false); this.reason = reason; }
    }
}
