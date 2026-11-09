package cn.academy.port.highpower;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry;
import cn.academy.port.highpower.core.SafeLandingProbeRules;
import cn.academy.port.highpower.core.PhaseSidestepGeometry;
import cn.academy.port.highpower.core.PhaseSidestepRules;
import cn.academy.port.highpower.core.ReturnMarkGeometry;
import cn.academy.port.highpower.core.ReturnMarkRules;
import cn.academy.port.highpower.core.CrossSwapGeometry;
import cn.academy.port.highpower.core.CornerExchangeGeometry;
import cn.academy.port.highpower.core.EmergencyReturnGeometry;
import cn.academy.port.highpower.core.TacticalCoordinateGeometry;
import cn.academy.port.highpower.core.TacticalCoordinateRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
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
 * NEW313 read-only, same-world landing assessment. The native first COLLIDER/ANY hit is
 * final: a rejected wall, fluid or unsupported top never starts a search behind it.
 * Every native block/shape read is fenced through a non-loading BlockGetter, including
 * shape neighbor reads. Ordinary full slabs work; narrow edges and incomplete footprints
 * deliberately refuse because this first prototype requires a fully supported foot area.
 */
public final class SafeLandingProbeWorld {
    private static final int MAX_SHAPE_GRID = 8192;
    private static final double SURFACE_TOLERANCE = 1e-5;

    public record Candidate(ServerLevel world, UUID owner, BlockPos surface, Vec3 surfacePoint,
                            Vec3 destination, Vec3 sourcePosition, Pose pose, AABB relativeBody) {
        public Candidate {
            if (world == null || owner == null || surface == null || !finite(surfacePoint)
                    || !finite(destination) || !finite(sourcePosition) || pose == null
                    || relativeBody == null || !box(relativeBody).body()
                    || !surfacePoint.equals(destination)) throw new IllegalArgumentException("Invalid NEW313 capture");
            surface = surface.immutable();
        }
    }
    public record Assessment(String reason, Candidate candidate) {
        public Assessment {
            if (reason == null || (reason.isEmpty() != (candidate != null)))
                throw new IllegalArgumentException("Invalid NEW313 assessment");
        }
        public boolean valid() { return candidate != null && reason.isEmpty(); }
    }

    /** NEW312 capture is closed: no public constructor accepts an arbitrary destination. */
    public static final class PhaseCandidate {
        private final ServerLevel world;
        private final UUID owner;
        private final Vec3 sourcePosition, destination;
        private final Pose pose;
        private final AABB relativeBody;
        private final boolean left;
        private final float yaw;
        private final double distance;
        private PhaseCandidate(ServerPlayer player, boolean left) {
            world = player.serverLevel(); owner = player.getUUID(); sourcePosition = player.position();
            pose = player.getPose(); relativeBody = player.getBoundingBox().move(sourcePosition.scale(-1));
            this.left = left; yaw = player.getYRot();
            distance = phaseDistance(player);
            var target = PhaseSidestepGeometry.destination(point(sourcePosition), yaw, left, distance);
            destination = new Vec3(target.x(), target.y(), target.z());
        }
        public ServerLevel world() { return world; }
        public UUID owner() { return owner; }
        public Vec3 sourcePosition() { return sourcePosition; }
        public Vec3 destination() { return destination; }
        public Pose pose() { return pose; }
        public AABB relativeBody() { return relativeBody; }
        public boolean left() { return left; }
        public float yaw() { return yaw; }
    }
    public record PhaseAssessment(String reason, PhaseCandidate candidate) {
        public PhaseAssessment {
            if (reason == null || (reason.isEmpty() != (candidate != null)))
                throw new IllegalArgumentException("Invalid NEW312 assessment");
        }
        public boolean valid() { return candidate != null && reason.isEmpty(); }
    }

    /** NEW322 anchors can only originate at an assessed real grounded player position. */
    public static final class ReturnAnchor {
        private final ServerLevel world;
        private final UUID owner;
        private final Vec3 destination;
        private final Pose pose;
        private final AABB relativeBody;
        private ReturnAnchor(ServerPlayer p) {
            world=p.serverLevel(); owner=p.getUUID(); destination=p.position(); pose=p.getPose();
            relativeBody=p.getBoundingBox().move(destination.scale(-1));
        }
        public ServerLevel world(){return world;} public UUID owner(){return owner;}
        public Vec3 destination(){return destination;} public Pose pose(){return pose;}
        public AABB relativeBody(){return relativeBody;}
    }
    public record ReturnAnchorAssessment(String reason,ReturnAnchor anchor) {
        public ReturnAnchorAssessment { if(reason==null||reason.isEmpty()!=(anchor!=null))throw new IllegalArgumentException("Invalid return anchor assessment"); }
        public boolean valid(){return anchor!=null&&reason.isEmpty();}
    }
    /** Closed current-source capture: callers cannot supply or change a destination. */
    public static final class ReturnCandidate {
        private final ReturnAnchor anchor;
        private final Vec3 sourcePosition;
        private final Pose pose;
        private final AABB relativeBody;
        private ReturnCandidate(ServerPlayer p,ReturnAnchor anchor) {
            this.anchor=anchor;sourcePosition=p.position();pose=p.getPose();
            relativeBody=p.getBoundingBox().move(sourcePosition.scale(-1));
        }
        public ServerLevel world(){return anchor.world;} public UUID owner(){return anchor.owner;}
        public Vec3 sourcePosition(){return sourcePosition;} public Vec3 destination(){return anchor.destination;}
        public Pose pose(){return pose;} public AABB relativeBody(){return relativeBody;}
    }
    public record ReturnAssessment(String reason,ReturnCandidate candidate) {
        public ReturnAssessment { if(reason==null||reason.isEmpty()!=(candidate!=null))throw new IllegalArgumentException("Invalid return assessment"); }
        public boolean valid(){return candidate!=null&&reason.isEmpty();}
    }
    public static ReturnAnchorAssessment mark(ServerPlayer player) {
        if(!thread(player))return new ReturnAnchorAssessment("server_thread_required",null);
        try {
            ReturnAnchor anchor=new ReturnAnchor(player);
            Guard view=new Guard(player);source(player,view);
            // Source checks intentionally allow escape from hazards; a saved destination must not.
            clearance(player,view,player.getBoundingBox(),player.getY(),"destination");
            view.sameWorld();
            if(!anchor.destination.equals(player.position())||anchor.pose!=player.getPose()
                    ||!sameBody(anchor.relativeBody,player.getBoundingBox().move(player.position().scale(-1))))throw refuse("source_changed");
            return new ReturnAnchorAssessment("",anchor);
        } catch(Refusal r){return new ReturnAnchorAssessment(r.reason,null);}
    }
    public static ReturnAssessment returning(ServerPlayer player,ReturnAnchor anchor) {
        if(!thread(player))return new ReturnAssessment("server_thread_required",null);
        if(anchor==null||anchor.world!=player.serverLevel()||!anchor.owner.equals(player.getUUID()))
            return new ReturnAssessment("candidate_world_or_owner_changed",null);
        try {
            ReturnCandidate c=new ReturnCandidate(player,anchor);returnClearance(player,c);
            return new ReturnAssessment("",c);
        }catch(Refusal r){return new ReturnAssessment(r.reason,null);}
        catch(IllegalArgumentException r){return new ReturnAssessment("return_geometry_invalid",null);}
    }
    public static ReturnAssessment revalidateReturn(ServerPlayer player,ReturnCandidate candidate) {
        if(!thread(player))return new ReturnAssessment("server_thread_required",null);
        try {
            if(candidate==null||candidate.world()!=player.serverLevel()||!candidate.owner().equals(player.getUUID()))
                throw refuse("candidate_world_or_owner_changed");
            if(!candidate.sourcePosition.equals(player.position()))throw refuse("source_changed");
            returnClearance(player,candidate);return new ReturnAssessment("",candidate);
        }catch(Refusal r){return new ReturnAssessment(r.reason,null);}
        catch(IllegalArgumentException r){return new ReturnAssessment("return_geometry_invalid",null);}
    }
    private static void returnClearance(ServerPlayer player,ReturnCandidate c) {
        if(!c.sourcePosition.equals(player.position()))throw refuse("source_changed");
        Guard view=new Guard(player,true);source(player,view);
        AABB local=player.getBoundingBox().move(player.position().scale(-1));
        if(c.pose!=player.getPose()||c.anchor.pose!=player.getPose()||!sameBody(c.relativeBody,local)||!sameBody(c.anchor.relativeBody,local))
            throw refuse("body_changed");
        if(!ReturnMarkGeometry.withinRange(point(player.position()),point(c.destination())))throw refuse("destination_out_of_range");
        if(player.position().distanceToSqr(c.destination())<=1e-8)throw refuse("destination_has_no_displacement");
        var sweep=ReturnMarkGeometry.sweep(box(player.getBoundingBox()),point(player.position()),point(c.destination()));
        var target=sweep.destinationBody();
        clearance(player,view,new AABB(target.minX(),target.minY(),target.minZ(),target.maxX(),target.maxY(),target.maxZ()),c.destination().y,"destination");
        var b=sweep.bounds();AABB envelope=new AABB(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ());
        view.returnVolume(envelope);view.scope=envelope.inflate(1.001);
        var cells=sweep.cells();var context=new LandingCollisionContext(player,player.getY());
        for(BlockPos pos:BlockPos.betweenClosed(cells.minX(),cells.minY(),cells.minZ(),cells.maxX(),cells.maxY(),cells.maxZ())) {
            if(view.world.isOutsideBuildHeight(pos))continue;
            // Neighbor shape cells are read safely, but only the true swept body qualifies fluid/hazards.
            boolean crossed=sweep.intersects(box(new AABB(pos)));
            BlockState state=view.getBlockState(pos);
            if(crossed&&!view.getFluidState(pos).isEmpty())throw refuse("corridor_fluid");
            if(crossed&&destinationHazard(state))throw refuse("corridor_hazard");
            VoxelShape shape=state.getCollisionShape(view,pos,context);
            // The canonical unit cube is disjoint when its cell is disjoint from the exact sweep.
            if (!crossed && shape == Shapes.block()) continue;
            view.shape(shape);
            shape.forAllBoxes((a,d,e,f,g,h)->{
                if(sweep.intersects(new SafeLandingProbeGeometry.Box(a+pos.getX(),d+pos.getY(),e+pos.getZ(),f+pos.getX(),g+pos.getY(),h+pos.getZ())))
                    throw refuse("corridor_block_collision");
            });
        }
        List<Entity> entities=new ArrayList<>();
        view.world.getEntities(EntityTypeTest.forClass(Entity.class),envelope.inflate(1e-7),e->{view.entity();return e!=player&&!e.isSpectator()
                &&(player.canCollideWith(e)||e instanceof LivingEntity&&e.isAlive());},entities,SafeLandingProbeGeometry.MAX_ENTITIES+1);
        if(entities.size()>SafeLandingProbeGeometry.MAX_ENTITIES)throw refuse("entity_scan_budget");
        for(Entity e:entities)if(sweep.intersects(box(e.getBoundingBox())))throw refuse("corridor_entity_collision");
        view.sameWorld();
        if(!c.sourcePosition.equals(player.position())||c.pose!=player.getPose()
                ||!sameBody(c.relativeBody,player.getBoundingBox().move(player.position().scale(-1))))throw refuse("source_changed");
    }

    /** NEW342 has its own closed anchor, so a NEW322 mark cannot acquire a longer range. */
    public static final class EmergencyAnchor {
        private final ServerPlayer player;
        private final ServerLevel world;
        private final UUID owner;
        private final Vec3 destination;
        private final Pose pose;
        private final AABB relativeBody, standingDimensions;
        private EmergencyAnchor(ServerPlayer player) {
            this.player = player; world = player.serverLevel(); owner = player.getUUID();
            destination = player.position(); pose = player.getPose();
            relativeBody = player.getBoundingBox().move(destination.scale(-1));
            standingDimensions = player.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO);
        }
        public ServerLevel world() { return world; }
        public UUID owner() { return owner; }
        public Vec3 destination() { return destination; }
        public Pose pose() { return pose; }
        public AABB relativeBody() { return relativeBody; }
    }
    public record EmergencyAnchorAssessment(String reason, EmergencyAnchor anchor) {
        public EmergencyAnchorAssessment {
            if (reason == null || reason.isEmpty() != (anchor != null))
                throw new IllegalArgumentException("Invalid emergency anchor assessment");
        }
        public boolean valid() { return anchor != null; }
    }

    /** The optional controller-owned capture is opaque: no caller can synthesize auto eligibility. */
    public static final class EmergencyCandidate {
        private final EmergencyAnchor anchor;
        private final Vec3 sourcePosition;
        private final Pose pose;
        private final AABB sourceBody, relativeBody, standingDimensions;
        private final EmergencyReturn.Capture capture;
        private EmergencyCandidate(ServerPlayer player, EmergencyAnchor anchor, EmergencyReturn.Capture capture) {
            this.anchor = anchor; this.capture = capture; sourcePosition = player.position();
            pose = player.getPose(); sourceBody = player.getBoundingBox();
            relativeBody = sourceBody.move(sourcePosition.scale(-1));
            standingDimensions = player.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO);
        }
        public ServerLevel world() { return anchor.world; }
        public UUID owner() { return anchor.owner; }
        public Vec3 sourcePosition() { return sourcePosition; }
        public Vec3 destination() { return anchor.destination; }
        public Pose pose() { return pose; }
        public AABB relativeBody() { return relativeBody; }
    }
    public record EmergencyAssessment(String reason, EmergencyCandidate candidate) {
        public EmergencyAssessment {
            if (reason == null || reason.isEmpty() != (candidate != null))
                throw new IllegalArgumentException("Invalid emergency return assessment");
        }
        public boolean valid() { return candidate != null; }
    }

    /** An explicit mark is the player's assessed current feet, never an arbitrary supplied point. */
    public static EmergencyAnchorAssessment emergencyMark(ServerPlayer player) {
        if (!thread(player)) return new EmergencyAnchorAssessment("server_thread_required", null);
        try {
            EmergencySourceSnapshot snapshot = new EmergencySourceSnapshot(player);
            EmergencyAnchor anchor = new EmergencyAnchor(player);
            Guard view = emergencyGuard(player);
            emergencySource(player, view, null);
            // A source may escape an existing hazard, but a future return mark must be harmless.
            clearance(player, view, snapshot.body, snapshot.position.y, "destination");
            view.sameWorld(); snapshot.unchanged(player, null);
            return new EmergencyAnchorAssessment("", anchor);
        } catch (Refusal refusal) { return new EmergencyAnchorAssessment(refusal.reason, null); }
        catch (IllegalArgumentException invalid) { return new EmergencyAnchorAssessment("emergency_geometry_invalid", null); }
    }

    /** Ordinary manual returns retain the existing grounded vertical-motion rule. */
    public static EmergencyAssessment emergencyReturning(ServerPlayer player, EmergencyAnchor anchor) {
        return assessEmergencyReturn(player, anchor, null);
    }

    /** Only a sealed native hurt capture can admit an unchanged, still-supported upward impulse. */
    public static EmergencyAssessment emergencyReturning(ServerPlayer player, EmergencyAnchor anchor,
                                                          EmergencyReturn.Capture capture) {
        if (capture == null) return new EmergencyAssessment("auto_capture_required", null);
        return assessEmergencyReturn(player, anchor, capture);
    }

    private static EmergencyAssessment assessEmergencyReturn(ServerPlayer player, EmergencyAnchor anchor,
                                                              EmergencyReturn.Capture capture) {
        if (!thread(player)) return new EmergencyAssessment("server_thread_required", null);
        try {
            emergencyOwner(player, anchor);
            EmergencyCandidate candidate = new EmergencyCandidate(player, anchor, capture);
            emergencyReturnClearance(player, candidate);
            return new EmergencyAssessment("", candidate);
        } catch (Refusal refusal) { return new EmergencyAssessment(refusal.reason, null); }
        catch (IllegalArgumentException invalid) { return new EmergencyAssessment("emergency_geometry_invalid", null); }
    }

    /** Reassess source, destination and every crossed native shape after callbacks and before commit. */
    public static EmergencyAssessment revalidateEmergencyReturn(ServerPlayer player, EmergencyCandidate candidate) {
        if (!thread(player)) return new EmergencyAssessment("server_thread_required", null);
        try {
            emergencyReturnClearance(player, candidate);
            return new EmergencyAssessment("", candidate);
        } catch (Refusal refusal) { return new EmergencyAssessment(refusal.reason, null); }
        catch (IllegalArgumentException invalid) { return new EmergencyAssessment("emergency_geometry_invalid", null); }
    }

    /** The native pre-hit source test is read-only and cannot admit already-rising movement. */
    public static String emergencySourceRefusal(ServerPlayer player) {
        if (!thread(player)) return "server_thread_required";
        try {
            EmergencySourceSnapshot snapshot = new EmergencySourceSnapshot(player);
            Guard view = emergencyGuard(player);
            emergencySource(player, view, null);
            view.sameWorld(); snapshot.unchanged(player, null);
            return "";
        } catch (Refusal refusal) { return refusal.reason; }
        catch (IllegalArgumentException invalid) { return "emergency_geometry_invalid"; }
    }

    /** Closed ground/sweep evidence for exactly one observed native NEW342 connection tick.
     * It grants no movement by itself; only the controller's private hurt capture may use it. */
    static final class EmergencyConnectionGround {
        final ServerPlayer owner;
        final ServerLevel world;
        final Vec3 source, transientPosition;
        final AABB sourceBody, transientBody;
        final BlockPos frictionPosition;
        final float friction;
        final EmergencyReturnGeometry.Sweep sweep;
        final Map<BlockPos, BlockState> states;
        private EmergencyConnectionGround(ServerPlayer p, Vec3 movement, BlockPos floor, float friction,
                                           EmergencyReturnGeometry.Sweep sweep, Map<BlockPos, BlockState> states) {
            owner = p; world = p.serverLevel(); source = p.position(); sourceBody = p.getBoundingBox();
            transientPosition = source.add(movement); transientBody = p.getDimensions(Pose.STANDING).makeBoundingBox(transientPosition);
            frictionPosition = floor.immutable(); this.friction = friction; this.sweep = sweep; this.states = Map.copyOf(states);
        }
        float friction() { return friction; }
    }

    /** Read the entire bounded native excursion before doTick; never adjust the player to probe it. */
    static EmergencyConnectionGround emergencyConnectionGround(ServerPlayer p, EmergencyReturn.Capture capture, Vec3 movement) {
        if (!thread(p) || capture == null || !capture.matchesOriginalSeal(p) || !finite(movement)
                || movement.y <= 0 || movement.y > .4 || Math.abs(movement.x) > 2 || Math.abs(movement.z) > 2
                || !(p instanceof NativeEmergencyReturnMovementAccess access)) return null;
        try {
            Vec3 position = p.position(), motion = p.getDeltaMovement(); AABB body = p.getBoundingBox();
            Guard view = emergencyGuard(p);
            clearance(p, view, body, position.y, "source");
            var sweep = EmergencyReturnGeometry.sweep(box(body), point(position), point(position.add(movement)));
            emergencyConnectionSweep(p, view, sweep);
            var b = sweep.bounds();
            AABB scope = new AABB(b.minX(), b.minY() - SafeLandingProbeGeometry.SUPPORT_DEPTH,
                    b.minZ(), b.maxX(), b.maxY(), b.maxZ()).inflate(1.001);
            view.emergencyReturnVolume(scope); view.scope = scope;
            Map<BlockPos, BlockState> states = new LinkedHashMap<>();
            for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(scope.minX, scope.minY, scope.minZ),
                    BlockPos.containing(Math.nextDown(scope.maxX), Math.nextDown(scope.maxY), Math.nextDown(scope.maxZ)))) {
                if (!view.world.isOutsideBuildHeight(cell)) states.put(cell.immutable(), view.getBlockState(cell));
            }
            BlockPos floor = p.getBlockPosBelowThatAffectsMyMovement();
            BlockState floorState = states.get(floor);
            if (floorState == null || !net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(floorState.getBlock()).getNamespace().equals("minecraft"))
                return null;
            float friction = floorState.getFriction(p.serverLevel(), floor, p);
            if (!Float.isFinite(friction) || friction <= 0 || friction > 1 || access.academy$emergencyBlockSpeedFactor() != 1F
                    || p.onClimbable() || !position.equals(p.position()) || !motion.equals(p.getDeltaMovement())
                    || !body.equals(p.getBoundingBox()) || !capture.matchesOriginalSeal(p)) return null;
            EmergencyConnectionGround result = new EmergencyConnectionGround(p, movement, floor, friction, sweep, states);
            return emergencyConnectionGroundValid(p, result) ? result : null;
        } catch (Refusal | IllegalArgumentException invalid) { return null; }
    }

    /** Recheck saved floor cells, full support and the same excursion after native work and next Pre. */
    static boolean emergencyConnectionGroundValid(ServerPlayer p, EmergencyConnectionGround ground) {
        if (!thread(p) || ground == null || ground.owner != p || ground.world != p.serverLevel()
                || !(p instanceof NativeEmergencyReturnMovementAccess access)) return false;
        try {
            Vec3 position = p.position(), motion = p.getDeltaMovement(); AABB body = p.getBoundingBox();
            Pose pose = p.getPose(); float fall = p.fallDistance; boolean grounded = p.onGround();
            if (!(position.equals(ground.source) && body.equals(ground.sourceBody)
                    || position.equals(ground.transientPosition) && body.equals(ground.transientBody)) || pose != Pose.STANDING) return false;
            Guard view = emergencyGuard(p);
            var b = ground.sweep.bounds();
            AABB scope = new AABB(b.minX(), b.minY() - SafeLandingProbeGeometry.SUPPORT_DEPTH,
                    b.minZ(), b.maxX(), b.maxY(), b.maxZ()).inflate(1.001);
            view.emergencyReturnVolume(scope); view.scope = scope;
            for (var saved : ground.states.entrySet()) if (!saved.getValue().equals(view.getBlockState(saved.getKey()))) return false;
            float friction = view.getBlockState(ground.frictionPosition).getFriction(p.serverLevel(), ground.frictionPosition, p);
            if (Float.compare(friction, ground.friction) != 0 || access.academy$emergencyBlockSpeedFactor() != 1F || p.onClimbable()) return false;
            clearance(p, view, ground.sourceBody, ground.source.y, "source");
            emergencyConnectionSweep(p, view, ground.sweep);
            view.sameWorld();
            return position.equals(p.position()) && motion.equals(p.getDeltaMovement()) && body.equals(p.getBoundingBox())
                    && pose == p.getPose() && Float.compare(fall, p.fallDistance) == 0 && grounded == p.onGround();
        } catch (Refusal | IllegalArgumentException invalid) { return false; }
    }

    private static void emergencyConnectionSweep(ServerPlayer p, Guard view, EmergencyReturnGeometry.Sweep sweep) {
        var b = sweep.bounds(); AABB envelope = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
        view.emergencyReturnVolume(envelope); view.scope = envelope.inflate(1.001);
        var cells = sweep.cells(); var context = new LandingCollisionContext(p, envelope.minY);
        for (BlockPos cell : BlockPos.betweenClosed(cells.minX(), cells.minY(), cells.minZ(), cells.maxX(), cells.maxY(), cells.maxZ())) {
            if (view.world.isOutsideBuildHeight(cell)) continue;
            BlockState state = view.getBlockState(cell);
            if (sweep.intersects(box(new AABB(cell))) && (!view.getFluidState(cell).isEmpty() || destinationHazard(state)))
                throw refuse("connection_sweep_hazard");
            VoxelShape shape = state.getCollisionShape(view, cell, context); view.shape(shape);
            shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
                if (sweep.intersects(new SafeLandingProbeGeometry.Box(x0 + cell.getX(), y0 + cell.getY(), z0 + cell.getZ(),
                        x1 + cell.getX(), y1 + cell.getY(), z1 + cell.getZ()))) throw refuse("connection_sweep_collision");
            });
        }
        List<Entity> entities = new ArrayList<>();
        view.world.getEntities(EntityTypeTest.forClass(Entity.class), envelope.inflate(.2), entity -> {
            view.entity();
            // Vanilla arrows/items neither collide with nor push the player. A hit arrow
            // can still be alive during the hurt seal; its presence is not body obstruction.
            // Keep every living body, native collider and native pushable nonliving entity.
            return entity != p && !entity.isSpectator() && entity.isAlive()
                    && (entity instanceof LivingEntity || p.canCollideWith(entity) || entity.isPushable());
        }, entities, SafeLandingProbeGeometry.MAX_ENTITIES + 1);
        if (entities.size() > SafeLandingProbeGeometry.MAX_ENTITIES) throw refuse("entity_scan_budget");
        for (Entity entity : entities) if (sweep.intersects(box(entity.getBoundingBox().inflate(.2)))) throw refuse("connection_sweep_entity");
        view.sameWorld();
    }

    private static Guard emergencyGuard(ServerPlayer player) {
        return new Guard(player, EmergencyReturnGeometry.MAX_READS, EmergencyReturnGeometry.MAX_SHAPE_BOXES);
    }
    private static void emergencyOwner(ServerPlayer player, EmergencyAnchor anchor) {
        if (anchor == null || anchor.player != player || anchor.world != player.serverLevel()
                || !anchor.owner.equals(player.getUUID())) throw refuse("candidate_world_or_owner_changed");
    }
    private static void unchangedEmergencyReturn(ServerPlayer player, EmergencyCandidate candidate) {
        if (candidate == null) throw refuse("candidate_world_or_owner_changed");
        emergencyOwner(player, candidate.anchor);
        if (!candidate.sourcePosition.equals(player.position())) throw refuse("source_changed");
        AABB dimensions = player.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO);
        if (candidate.pose != player.getPose() || candidate.anchor.pose != player.getPose()
                || !candidate.sourceBody.equals(player.getBoundingBox())
                || !candidate.standingDimensions.equals(dimensions)
                || !candidate.anchor.standingDimensions.equals(dimensions)) throw refuse("body_changed");
        emergencySourceFlags(player, candidate.capture);
    }

    private static void emergencyReturnClearance(ServerPlayer player, EmergencyCandidate candidate) {
        unchangedEmergencyReturn(player, candidate);
        EmergencySourceSnapshot snapshot = new EmergencySourceSnapshot(player);
        if (!EmergencyReturnGeometry.withinRange(point(candidate.sourcePosition), point(candidate.destination())))
            throw refuse("destination_out_of_range");
        if (candidate.sourcePosition.distanceToSqr(candidate.destination()) <= 1e-8)
            throw refuse("destination_has_no_displacement");
        Guard view = emergencyGuard(player);
        emergencySource(player, view, candidate.capture);
        var sweep = EmergencyReturnGeometry.sweep(box(candidate.sourceBody), point(candidate.sourcePosition),
                point(candidate.destination()));
        var target = sweep.destinationBody();
        clearance(player, view, new AABB(target.minX(), target.minY(), target.minZ(), target.maxX(), target.maxY(),
                target.maxZ()), candidate.destination().y, "destination");
        var bounds = sweep.bounds();
        AABB envelope = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
        view.emergencyReturnVolume(envelope); view.scope = envelope.inflate(1.001);
        var cells = sweep.cells();
        var context = new LandingCollisionContext(player, candidate.sourcePosition.y);
        for (BlockPos pos : BlockPos.betweenClosed(cells.minX(), cells.minY(), cells.minZ(),
                cells.maxX(), cells.maxY(), cells.maxZ())) {
            if (view.world.isOutsideBuildHeight(pos)) continue;
            boolean crossed = sweep.intersects(box(new AABB(pos)));
            BlockState state = view.getBlockState(pos);
            if (crossed && !view.getFluidState(pos).isEmpty()) throw refuse("corridor_fluid");
            if (crossed && destinationHazard(state)) throw refuse("corridor_hazard");
            VoxelShape shape = state.getCollisionShape(view, pos, context); view.shape(shape);
            shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
                if (sweep.intersects(new SafeLandingProbeGeometry.Box(x0 + pos.getX(), y0 + pos.getY(), z0 + pos.getZ(),
                        x1 + pos.getX(), y1 + pos.getY(), z1 + pos.getZ()))) throw refuse("corridor_block_collision");
            });
        }
        List<Entity> entities = new ArrayList<>();
        view.world.getEntities(EntityTypeTest.forClass(Entity.class), envelope.inflate(1e-7), entity -> {
            view.entity();
            return entity != player && !entity.isSpectator()
                    && (player.canCollideWith(entity) || entity instanceof LivingEntity && entity.isAlive());
        }, entities, SafeLandingProbeGeometry.MAX_ENTITIES + 1);
        if (entities.size() > SafeLandingProbeGeometry.MAX_ENTITIES) throw refuse("entity_scan_budget");
        for (Entity entity : entities)
            if (sweep.intersects(box(entity.getBoundingBox()))) throw refuse("corridor_entity_collision");
        view.sameWorld(); snapshot.unchanged(player, candidate.capture); unchangedEmergencyReturn(player, candidate);
    }

    /** No physics is patched even temporarily; full native support is required in both paths. */
    private static void emergencySource(ServerPlayer player, Guard view, EmergencyReturn.Capture capture) {
        EmergencySourceSnapshot snapshot = new EmergencySourceSnapshot(player);
        emergencySourceFlags(player, capture);
        clearance(player, view, player.getBoundingBox(), player.getY(), "source");
        if (capture != null && !capture.revalidateConnectionGround(player)) throw refuse("auto_source_changed");
        if (view.getBlockState(player.blockPosition()).is(Blocks.BUBBLE_COLUMN))
            throw refuse("source_motion_or_pose_unsupported");
        // A permission/shape callback cannot move the player before this native block-reading query.
        snapshot.unchanged(player, capture);
        if (player.onClimbable()) throw refuse("source_climbing");
    }

    /** Cached eligibility only: the final callback fence performs no additional world reads. */
    private static void emergencySourceFlags(ServerPlayer player, EmergencyReturn.Capture capture) {
        if (!player.isAlive() || player.isRemoved() || player.hasDisconnected() || player.connection == null)
            throw refuse("source_unavailable");
        if (!player.onGround() && (capture == null || !capture.matchesAutoSource(player))) throw refuse("source_airborne");
        if (capture == null) {
            if (!SafeLandingProbeRules.groundedVerticalMotion(player.getDeltaMovement().y))
                throw refuse("source_airborne");
        } else {
            if (!capture.matchesAutoSource(player)) throw refuse("auto_source_changed");
            // The closed exception admits only upward hurt impulse, never arbitrary falling motion.
            if (!SafeLandingProbeRules.groundedVerticalMotion(player.getDeltaMovement().y)
                    && !(player.getDeltaMovement().y > 0)) throw refuse("source_airborne");
        }
        if (player.getPose() != Pose.STANDING || player.isShiftKeyDown()
                || !player.getDimensions(Pose.STANDING).makeBoundingBox(player.position()).equals(player.getBoundingBox()))
            throw refuse("standing_required");
        if (player.noPhysics || player.isPassenger() || player.isSleeping() || player.isFallFlying()
                || player.getAbilities().flying || player.isInWater() || player.isInLava()
                || !finite(player.position()) || !finite(player.getDeltaMovement()) || !Float.isFinite(player.fallDistance)
                || !Float.isFinite(player.getXRot()) || !Float.isFinite(player.getYRot()))
            throw refuse("source_motion_or_pose_unsupported");
    }

    private static final class EmergencySourceSnapshot {
        final ServerLevel world;
        final Vec3 position, motion;
        final Pose pose;
        final AABB body, standingDimensions;
        final float fallDistance;
        EmergencySourceSnapshot(ServerPlayer player) {
            world = player.serverLevel(); position = player.position(); motion = player.getDeltaMovement();
            pose = player.getPose(); body = player.getBoundingBox(); fallDistance = player.fallDistance;
            standingDimensions = player.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO);
        }
        void unchanged(ServerPlayer player, EmergencyReturn.Capture capture) {
            if (world != player.serverLevel() || !position.equals(player.position())) throw refuse("source_changed");
            if (pose != player.getPose() || !body.equals(player.getBoundingBox())
                    || !standingDimensions.equals(player.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO)))
                throw refuse("body_changed");
            if (!motion.equals(player.getDeltaMovement()) || Float.compare(fallDistance, player.fallDistance) != 0)
                throw refuse("source_motion_changed");
            emergencySourceFlags(player, capture);
        }
    }

    private SafeLandingProbeWorld() {}

    /** NEW353 capture is a checked current position, never persisted movement authority. */
    public static final class TacticalTemplate {
        private final ServerLevel world;
        private final Vec3 feet;
        private TacticalTemplate(ServerPlayer player) { world = player.serverLevel(); feet = player.position(); }
        public ServerLevel world() { return world; }
        public Vec3 feet() { return feet; }
    }
    public record TacticalCaptureAssessment(String reason, TacticalTemplate template) {
        public TacticalCaptureAssessment {
            if (reason == null || reason.isEmpty() != (template != null))
                throw new IllegalArgumentException("Invalid tactical capture assessment");
        }
        public boolean valid() { return template != null; }
    }
    /** Only a fresh loaded-world assessment creates this source-, body- and connection-bound proof. */
    public static final class TacticalCandidate {
        private final TacticalSourceSnapshot source;
        private final Vec3 destination;
        private final AABB nativeDestinationBody;
        private final TacticalCoordinateGeometry.Sweep route;
        private TacticalCandidate(ServerPlayer player, Vec3 feet) {
            source = new TacticalSourceSnapshot(player); destination = feet;
            nativeDestinationBody = player.getDimensions(Pose.STANDING).makeBoundingBox(feet);
            route = TacticalCoordinateGeometry.sweep(box(source.standingBody), point(source.position), point(feet));
        }
        public ServerLevel world() { return source.world; }
        public UUID owner() { return source.uuid; }
        public Vec3 sourcePosition() { return source.position; }
        public Vec3 destination() { return destination; }
        public Pose pose() { return source.pose; }
        public AABB destinationBody() { return nativeDestinationBody; }
    }
    public record TacticalAssessment(String reason, TacticalCandidate candidate) {
        public TacticalAssessment {
            if (reason == null || reason.isEmpty() != (candidate != null))
                throw new IllegalArgumentException("Invalid tactical destination assessment");
        }
        public boolean valid() { return candidate != null; }
    }

    /** Captures only real, safe standing feet; the controller owns UUIDs, revisions and persistence. */
    public static TacticalCaptureAssessment tacticalCapture(ServerPlayer player) {
        if (!thread(player)) return new TacticalCaptureAssessment("server_thread_required", null);
        try {
            TacticalSourceSnapshot snapshot = new TacticalSourceSnapshot(player);
            if (!TacticalCoordinateRules.validCoordinates(snapshot.position.x, snapshot.position.y, snapshot.position.z))
                throw refuse("tactical_geometry_invalid");
            if (snapshot.pose != Pose.STANDING || snapshot.shiftDown) throw refuse("standing_required");
            Guard view = tacticalGuard(player);
            tacticalSource(player, view, snapshot);
            clearance(player, view, snapshot.standingBody, snapshot.position.y, "destination");
            view.sameWorld(); snapshot.unchanged(player);
            return new TacticalCaptureAssessment("", new TacticalTemplate(player));
        } catch (Refusal refusal) { return new TacticalCaptureAssessment(refusal.reason, null); }
        catch (IllegalArgumentException invalid) { return new TacticalCaptureAssessment("tactical_geometry_invalid", null); }
    }

    /** Saved coordinates are inert input. Current traveler dimensions, permissions and the entire route are checked. */
    public static TacticalAssessment tacticalDestination(ServerPlayer traveler, ServerLevel exactWorld, Vec3 inertStoredFeet) {
        if (!thread(traveler)) return new TacticalAssessment("server_thread_required", null);
        if (exactWorld == null || exactWorld != traveler.serverLevel())
            return new TacticalAssessment("candidate_world_or_owner_changed", null);
        if (inertStoredFeet == null || !TacticalCoordinateRules.validCoordinates(inertStoredFeet.x, inertStoredFeet.y, inertStoredFeet.z))
            return new TacticalAssessment("tactical_geometry_invalid", null);
        try {
            if (!TacticalCoordinateGeometry.withinRange(point(traveler.position()), point(inertStoredFeet)))
                throw refuse("destination_out_of_range");
            if (traveler.position().equals(inertStoredFeet)) throw refuse("destination_has_no_displacement");
            TacticalCandidate candidate = new TacticalCandidate(traveler, inertStoredFeet);
            tacticalClearance(traveler, candidate);
            return new TacticalAssessment("", candidate);
        } catch (Refusal refusal) { return new TacticalAssessment(refusal.reason, null); }
        catch (IllegalArgumentException invalid) { return new TacticalAssessment("tactical_geometry_invalid", null); }
    }

    /** Revalidates the same exact one-shot candidate after teleport events, before any payment or native movement. */
    public static TacticalAssessment tacticalRevalidate(ServerPlayer traveler, TacticalCandidate candidate) {
        if (!thread(traveler)) return new TacticalAssessment("server_thread_required", null);
        try {
            if (candidate == null) throw refuse("candidate_world_or_owner_changed");
            tacticalClearance(traveler, candidate);
            return new TacticalAssessment("", candidate);
        } catch (Refusal refusal) { return new TacticalAssessment(refusal.reason, null); }
        catch (IllegalArgumentException invalid) { return new TacticalAssessment("tactical_geometry_invalid", null); }
    }

    private static Guard tacticalGuard(ServerPlayer player) {
        return new Guard(player, TacticalCoordinateGeometry.MAX_READS, TacticalCoordinateGeometry.MAX_SHAPE_BOXES);
    }
    private static AABB tacticalBox(SafeLandingProbeGeometry.Box body) {
        return new AABB(body.minX(), body.minY(), body.minZ(), body.maxX(), body.maxY(), body.maxZ());
    }
    private static void tacticalClearance(ServerPlayer player, TacticalCandidate candidate) {
        candidate.source.unchanged(player);
        if (!TacticalCoordinateGeometry.withinRange(point(candidate.source.position), point(candidate.destination)))
            throw refuse("destination_out_of_range");
        Guard view = tacticalGuard(player);
        tacticalSource(player, view, candidate.source);
        clearance(player, view, candidate.destinationBody(), candidate.destination.y, "destination");
        var sweep = candidate.route;
        // At far coordinates rebuilding a native box and translating a source box can round differently.
        // Check both exact volumes; never shrink or epsilon-adjust the actual native landing body.
        AABB translatedDestination = tacticalBox(sweep.destinationBody());
        if (!translatedDestination.equals(candidate.destinationBody()))
            clearance(player, view, translatedDestination, candidate.destination.y, "destination");
        AABB envelope = tacticalBox(sweep.bounds());
        tacticalVolume(view, envelope); view.scope = envelope.inflate(1.001);
        var cells = sweep.cells();
        var context = new LandingCollisionContext(player, candidate.source.position.y);
        for (BlockPos pos : BlockPos.betweenClosed(cells.minX(), cells.minY(), cells.minZ(),
                cells.maxX(), cells.maxY(), cells.maxZ())) {
            if (view.world.isOutsideBuildHeight(pos)) continue;
            boolean crossed = sweep.intersects(box(new AABB(pos)));
            BlockState state = view.getBlockState(pos);
            if (crossed && !view.getFluidState(pos).isEmpty()) throw refuse("corridor_fluid");
            if (crossed && destinationHazard(state)) throw refuse("corridor_hazard");
            VoxelShape shape = state.getCollisionShape(view, pos, context); view.shape(shape);
            shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
                if (sweep.intersects(new SafeLandingProbeGeometry.Box(x0 + pos.getX(), y0 + pos.getY(), z0 + pos.getZ(),
                        x1 + pos.getX(), y1 + pos.getY(), z1 + pos.getZ()))) throw refuse("corridor_block_collision");
            });
        }
        List<Entity> entities = new ArrayList<>();
        view.world.getEntities(EntityTypeTest.forClass(Entity.class), envelope.inflate(1e-7), entity -> {
            view.entity();
            return entity != player && !entity.isSpectator()
                    && (player.canCollideWith(entity) || entity instanceof LivingEntity && entity.isAlive());
        }, entities, SafeLandingProbeGeometry.MAX_ENTITIES + 1);
        if (entities.size() > SafeLandingProbeGeometry.MAX_ENTITIES) throw refuse("entity_scan_budget");
        for (Entity entity : entities)
            if (sweep.intersects(box(entity.getBoundingBox()))) throw refuse("corridor_entity_collision");
        view.sameWorld(); candidate.source.unchanged(player);
    }

    /** Separate 64m extent; no historical landing/swap scan limit is enlarged. */
    private static void tacticalVolume(Guard view, AABB volume) {
        view.sameWorld();
        double edge = TacticalCoordinateRules.RANGE + SafeLandingProbeGeometry.MAX_BODY_EDGE;
        if (!box(volume).finite() || volume.getXsize() > edge || volume.getYsize() > edge || volume.getZsize() > edge)
            throw refuse("collision_scan_budget");
        if (volume.minY < view.world.getMinBuildHeight() || volume.maxY > view.world.getMaxBuildHeight())
            throw refuse("outside_build_height");
        if (!view.world.getWorldBorder().isWithinBounds(volume)) throw refuse("outside_world_border");
        int x0 = ((int)Math.floor(volume.minX)) >> 4, x1 = ((int)Math.floor(Math.nextDown(volume.maxX))) >> 4;
        int z0 = ((int)Math.floor(volume.minZ)) >> 4, z1 = ((int)Math.floor(Math.nextDown(volume.maxZ))) >> 4;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++)
            if (view.world.getChunkSource().getChunkNow(x, z) == null) throw refuse("unloaded_chunk");
    }

    private static void tacticalSource(ServerPlayer player, Guard view, TacticalSourceSnapshot snapshot) {
        snapshot.unchanged(player);
        // Both current native pose and full standing dimensions must fit without setPose or movement probes.
        clearance(player, view, snapshot.body, snapshot.position.y, "source");
        clearance(player, view, snapshot.standingBody, snapshot.position.y, "source");
        if (view.getBlockState(player.blockPosition()).is(Blocks.BUBBLE_COLUMN))
            throw refuse("source_motion_or_pose_unsupported");
        snapshot.unchanged(player); // Protect onClimbable's native position read after permission/shape callbacks.
        if (player.onClimbable()) throw refuse("source_climbing");
        snapshot.unchanged(player);
    }
    private static void tacticalSourceFlags(ServerPlayer player) {
        if (!player.isAlive() || player.isRemoved() || player.hasDisconnected() || player.connection == null || player.isSpectator())
            throw refuse("source_unavailable");
        if (!player.onGround() || !SafeLandingProbeRules.groundedVerticalMotion(player.getDeltaMovement().y)
                || !Float.isFinite(player.fallDistance) || player.fallDistance != 0) throw refuse("source_airborne");
        if (player.getPose() != Pose.STANDING && player.getPose() != Pose.CROUCHING)
            throw refuse("standing_required");
        if (player.noPhysics || player.isPassenger() || player.isVehicle() || player.isSleeping() || player.isFallFlying()
                || player.getAbilities().flying || player.isInWater() || player.isInLava()
                || !finite(player.position()) || !finite(player.getEyePosition()) || !finite(player.getDeltaMovement())
                || !Float.isFinite(player.getXRot()) || !Float.isFinite(player.getYRot())
                || !player.getDimensions(player.getPose()).makeBoundingBox(player.position()).equals(player.getBoundingBox()))
            throw refuse("source_motion_or_pose_unsupported");
        if (player.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)
                || player.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)) throw refuse("movement_conflict");
    }

    private static final class TacticalSourceSnapshot {
        final ServerPlayer owner;
        final UUID uuid;
        final Object connection;
        final ServerLevel world;
        final int entity;
        final Vec3 position, eye, motion;
        final Pose pose;
        final AABB body, standingBody, standingDimensions;
        final float yaw, pitch, fall;
        final boolean shiftDown, grounded;
        TacticalSourceSnapshot(ServerPlayer player) {
            owner = player; uuid = player.getUUID(); connection = player.connection; world = player.serverLevel(); entity = player.getId();
            position = player.position(); eye = player.getEyePosition(); motion = player.getDeltaMovement(); pose = player.getPose();
            body = player.getBoundingBox(); standingDimensions = player.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO);
            standingBody = player.getDimensions(Pose.STANDING).makeBoundingBox(position);
            yaw = player.getYRot(); pitch = player.getXRot(); fall = player.fallDistance;
            shiftDown = player.isShiftKeyDown(); grounded = player.onGround();
        }
        void unchanged(ServerPlayer player) {
            if (player != owner || player.connection != connection || player.serverLevel() != world || player.level() != world
                    || !uuid.equals(player.getUUID()) || player.getId() != entity || world.getEntity(entity) != player)
                throw refuse("candidate_world_or_owner_changed");
            if (!position.equals(player.position()) || !eye.equals(player.getEyePosition())) throw refuse("source_changed");
            if (pose != player.getPose() || !body.equals(player.getBoundingBox())
                    || !standingDimensions.equals(player.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO))
                    || !standingBody.equals(player.getDimensions(Pose.STANDING).makeBoundingBox(position))) throw refuse("body_changed");
            if (standingBody.minX > body.minX || standingBody.minY > body.minY || standingBody.minZ > body.minZ
                    || standingBody.maxX < body.maxX || standingBody.maxY < body.maxY || standingBody.maxZ < body.maxZ)
                throw refuse("body_changed");
            if (!motion.equals(player.getDeltaMovement()) || Float.compare(fall, player.fallDistance) != 0
                    || Float.compare(yaw, player.getYRot()) != 0 || Float.compare(pitch, player.getXRot()) != 0
                    || shiftDown != player.isShiftKeyDown() || grounded != player.onGround()) throw refuse("source_motion_changed");
            tacticalSourceFlags(player);
        }
    }

    /** NEW321's closed 32m capture. An arbitrary position cannot become a prepared point. */
    public static final class CrossPoint {
        private final ServerLevel world;
        private final UUID owner;
        private final BlockPos surface;
        private final Vec3 destination;
        private final AABB relativeBody;
        private final AABB standingDimensions;
        private CrossPoint(ServerPlayer player, BlockHitResult hit) {
            world = player.serverLevel(); owner = player.getUUID(); surface = hit.getBlockPos().immutable();
            destination = hit.getLocation(); relativeBody = player.getBoundingBox().move(player.position().scale(-1));
            standingDimensions = player.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO);
        }
        public ServerLevel world() { return world; }
        public UUID owner() { return owner; }
        public Vec3 destination() { return destination; }
    }
    public record CrossAssessment(String reason, CrossPoint point) {
        public CrossAssessment {
            if (reason == null || reason.isEmpty() != (point != null)) throw new IllegalArgumentException("Invalid cross assessment");
        }
        public boolean valid() { return point != null; }
    }
    public static CrossAssessment crossPoint(ServerPlayer player) {
        if (!thread(player)) return new CrossAssessment("server_thread_required", null);
        try {
            if (player.getPose() != Pose.STANDING || player.isShiftKeyDown()) throw refuse("standing_required");
            Guard view = new Guard(player, true); source(player, view);
            Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
            if (!finite(eye) || !finite(look) || look.lengthSqr() < .999 || look.lengthSqr() > 1.001)
                throw refuse("invalid_server_aim");
            BlockHitResult hit = crossFirstHit(player, view, eye, eye.add(look.scale(32)));
            standableHit(view, hit);
            CrossPoint point = new CrossPoint(player, hit);
            clearance(player, view, crossTranslated(player.getBoundingBox(), player.position(), point.destination), point.destination.y, "destination");
            view.sameWorld();
            return new CrossAssessment("", point);
        } catch (Refusal refusal) { return new CrossAssessment(refusal.reason, null); }
    }
    /** Reassess the complete current standing body and exact first visible saved UP surface. */
    public static CrossAssessment revalidateCrossPoint(ServerPlayer player, CrossPoint point) {
        if (!thread(player)) return new CrossAssessment("server_thread_required", null);
        try {
            if (point == null || point.world != player.serverLevel() || !point.owner.equals(player.getUUID()))
                throw refuse("candidate_world_or_owner_changed");
            if (player.getPose() != Pose.STANDING || player.isShiftKeyDown()
                    || !sameBody(point.relativeBody, player.getBoundingBox().move(player.position().scale(-1))))
                throw refuse("body_changed");
            Guard view = new Guard(player, true); source(player, view);
            Vec3 eye = player.getEyePosition(), direction = point.destination.subtract(eye);
            if (!finite(eye) || direction.lengthSqr() < 1e-10 || direction.lengthSqr() > 32 * 32)
                throw refuse("surface_out_of_range");
            BlockHitResult hit = crossFirstHit(player, view, eye,
                    point.destination.add(direction.normalize().scale(SURFACE_TOLERANCE)));
            standableHit(view, hit);
            if (!hit.getBlockPos().equals(point.surface)
                    || hit.getLocation().distanceToSqr(point.destination) > SURFACE_TOLERANCE * SURFACE_TOLERANCE)
                throw refuse("surface_changed");
            clearance(player, view, crossTranslated(player.getBoundingBox(), player.position(), point.destination), point.destination.y, "destination");
            view.sameWorld();
            return new CrossAssessment("", point);
        } catch (Refusal refusal) { return new CrossAssessment(refusal.reason, null); }
    }

    /** Closed NEW321 capture: only the opposite stored mark can become the destination. */
    public static final class CrossSwapCandidate {
        private final CrossPoint a, b;
        private final int sourceIndex;
        private final Vec3 sourcePosition;
        private final Pose pose;
        private final AABB relativeBody;
        private CrossSwapCandidate(ServerPlayer player, CrossPoint a, CrossPoint b, int sourceIndex) {
            this.a = a; this.b = b; this.sourceIndex = sourceIndex;
            sourcePosition = player.position(); pose = player.getPose();
            relativeBody = player.getBoundingBox().move(sourcePosition.scale(-1));
        }
        public ServerLevel world() { return a.world; }
        public UUID owner() { return a.owner; }
        public Vec3 sourcePosition() { return sourcePosition; }
        public Vec3 destination() { return (sourceIndex == 0 ? b : a).destination; }
        public Pose pose() { return pose; }
    }
    public record CrossSwapAssessment(String reason, CrossSwapCandidate candidate) {
        public CrossSwapAssessment {
            if (reason == null || reason.isEmpty() != (candidate != null))
                throw new IllegalArgumentException("Invalid cross swap assessment");
        }
        public boolean valid() { return candidate != null && reason.isEmpty(); }
    }

    /** Grounded crouch activation near exactly one prepared point; this method never moves anyone. */
    public static CrossSwapAssessment crossSwap(ServerPlayer player, CrossPoint a, CrossPoint b) {
        if (!thread(player)) return new CrossSwapAssessment("server_thread_required", null);
        try {
            crossOwner(player, a); crossOwner(player, b);
            int index = crossSourceIndex(player, a, b);
            CrossSwapCandidate candidate = new CrossSwapCandidate(player, a, b, index);
            crossSwapClearance(player, candidate);
            return new CrossSwapAssessment("", candidate);
        } catch (Refusal refusal) { return new CrossSwapAssessment(refusal.reason, null); }
        catch (IllegalArgumentException invalid) { return new CrossSwapAssessment("cross_geometry_invalid", null); }
    }

    /** Re-read both saved surfaces and the complete current route immediately before commit. */
    public static CrossSwapAssessment revalidateCrossSwap(ServerPlayer player, CrossSwapCandidate candidate) {
        if (!thread(player)) return new CrossSwapAssessment("server_thread_required", null);
        try {
            unchangedCrossSwap(player, candidate);
            crossSwapClearance(player, candidate);
            return new CrossSwapAssessment("", candidate);
        } catch (Refusal refusal) { return new CrossSwapAssessment(refusal.reason, null); }
        catch (IllegalArgumentException invalid) { return new CrossSwapAssessment("cross_geometry_invalid", null); }
    }

    private static void crossOwner(ServerPlayer player, CrossPoint point) {
        if (point == null || point.world != player.serverLevel() || !point.owner.equals(player.getUUID()))
            throw refuse("candidate_world_or_owner_changed");
    }
    private static int crossSourceIndex(ServerPlayer player, CrossPoint a, CrossPoint b) {
        int index = CrossSwapGeometry.sourceIndex(point(player.position()), point(a.destination), point(b.destination));
        if (index < 0) throw refuse(CrossSwapGeometry.near(point(player.position()), point(a.destination))
                && CrossSwapGeometry.near(point(player.position()), point(b.destination))
                ? "source_near_both_points" : "source_not_near_point");
        return index;
    }
    private static void unchangedCrossSwap(ServerPlayer player, CrossSwapCandidate candidate) {
        if (candidate == null) throw refuse("candidate_world_or_owner_changed");
        crossOwner(player, candidate.a); crossOwner(player, candidate.b);
        if (!candidate.sourcePosition.equals(player.position())) throw refuse("source_changed");
        if (!player.isShiftKeyDown() || player.getPose() != Pose.CROUCHING && player.getPose() != Pose.STANDING)
            throw refuse("crouch_required");
        if (candidate.pose != player.getPose()
                || !sameBody(candidate.relativeBody, player.getBoundingBox().move(player.position().scale(-1))))
            throw refuse("body_changed");
        // Derive standing dimensions without setPose(), callbacks or a transient body mutation.
        AABB dimensions = player.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO);
        if (!dimensions.equals(candidate.a.standingDimensions) || !dimensions.equals(candidate.b.standingDimensions)
                || !sameBody(candidate.a.relativeBody, dimensions) || !sameBody(candidate.b.relativeBody, dimensions))
            throw refuse("body_changed");
        if (crossSourceIndex(player, candidate.a, candidate.b) != candidate.sourceIndex) throw refuse("source_changed");
    }

    private static void crossSwapClearance(ServerPlayer player, CrossSwapCandidate candidate) {
        unchangedCrossSwap(player, candidate);
        if (!CrossSwapGeometry.withinRange(point(candidate.sourcePosition), point(candidate.destination())))
            throw refuse("destination_out_of_range");
        if (candidate.sourcePosition.distanceToSqr(candidate.destination()) <= 1e-8)
            throw refuse("destination_has_no_displacement");
        Guard view = new Guard(player, CrossSwapGeometry.MAX_READS, CrossSwapGeometry.MAX_SHAPE_BOXES);
        source(player, view);
        CrossPoint sourcePoint = candidate.sourceIndex == 0 ? candidate.a : candidate.b;
        CrossPoint targetPoint = candidate.sourceIndex == 0 ? candidate.b : candidate.a;
        // A crouched player must fit the full stored standing body at the real source too.
        AABB standingBody = sourcePoint.relativeBody.move(candidate.sourcePosition);
        clearance(player, view, standingBody, candidate.sourcePosition.y, "destination", true);
        crossSwapPoint(player, view, sourcePoint, true);
        crossSwapPoint(player, view, targetPoint, false);

        var sweep = CrossSwapGeometry.sweep(box(standingBody), point(candidate.sourcePosition), point(candidate.destination()));
        var b = sweep.bounds();
        AABB envelope = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
        view.crossSwapVolume(envelope); view.scope = envelope.inflate(1.001);
        var cells = sweep.cells();
        var context = new LandingCollisionContext(player, candidate.sourcePosition.y);
        for (BlockPos pos : BlockPos.betweenClosed(cells.minX(), cells.minY(), cells.minZ(), cells.maxX(), cells.maxY(), cells.maxZ())) {
            if (view.world.isOutsideBuildHeight(pos)) continue;
            boolean crossed = sweep.intersects(box(new AABB(pos)));
            BlockState state = view.getBlockState(pos);
            if (crossed && !view.getFluidState(pos).isEmpty()) throw refuse("corridor_fluid");
            if (crossed && destinationHazard(state)) throw refuse("corridor_hazard");
            VoxelShape shape = state.getCollisionShape(view, pos, context); view.shape(shape);
            shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
                if (sweep.intersects(new SafeLandingProbeGeometry.Box(x0 + pos.getX(), y0 + pos.getY(), z0 + pos.getZ(),
                        x1 + pos.getX(), y1 + pos.getY(), z1 + pos.getZ()))) throw refuse("corridor_block_collision");
            });
        }
        List<Entity> entities = new ArrayList<>();
        view.world.getEntities(EntityTypeTest.forClass(Entity.class), envelope.inflate(1e-7), entity -> {
            view.entity();
            // The caster is the one permitted occupant of its unchanged source. Every other
            // native collidable entity or live living body blocks; noncolliding media stay non-solid.
            return entity != player && !entity.isSpectator()
                    && (player.canCollideWith(entity) || entity instanceof LivingEntity && entity.isAlive());
        }, entities, SafeLandingProbeGeometry.MAX_ENTITIES + 1);
        if (entities.size() > SafeLandingProbeGeometry.MAX_ENTITIES) throw refuse("entity_scan_budget");
        for (Entity entity : entities)
            if (sweep.intersects(box(entity.getBoundingBox()))) throw refuse("corridor_entity_collision");
        view.sameWorld(); unchangedCrossSwap(player, candidate);
    }

    private static void crossSwapPoint(ServerPlayer player, Guard view, CrossPoint point, boolean sourcePoint) {
        AABB body = point.relativeBody.move(point.destination);
        clearance(player, view, body, point.destination.y, "destination", sourcePoint);
        // Recheck the exact saved UP face locally. Reusing the attack validator would require
        // standing input and a new eye ray instead of validating the prepared self-swap route.
        view.scope = body.inflate(1.001);
        BlockState state = view.getBlockState(point.surface);
        if (!view.getFluidState(point.surface).isEmpty()) throw refuse("first_surface_fluid");
        VoxelShape shape = state.getCollisionShape(view, point.surface, new LandingCollisionContext(player, point.destination.y));
        view.shape(shape);
        BlockHitResult hit = shape.clip(point.destination.add(0, .01, 0), point.destination.add(0, -.01, 0), point.surface);
        if (hit == null || hit.isInside() || hit.getDirection() != Direction.UP
                || !hit.getBlockPos().equals(point.surface)
                || hit.getLocation().distanceToSqr(point.destination) > SURFACE_TOLERANCE * SURFACE_TOLERANCE)
            throw refuse("surface_changed");
    }

    /** Kept separate from the unchanged NEW313 12m first-hit entry. */
    private static BlockHitResult crossFirstHit(ServerPlayer player, Guard view, Vec3 from, Vec3 to) {
        if (!finite(from) || !finite(to) || from.distanceToSqr(to) > 1024.001) throw refuse("invalid_ray");
        view.scope = new AABB(from, to).inflate(1.001);
        return view.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player) {
            @Override public VoxelShape getBlockShape(BlockState state, BlockGetter blocks, BlockPos pos) {
                VoxelShape shape = super.getBlockShape(state, blocks, pos); view.shape(shape); return shape;
            }
            @Override public VoxelShape getFluidShape(FluidState fluid, BlockGetter blocks, BlockPos pos) {
                VoxelShape shape = super.getFluidShape(fluid, blocks, pos); view.shape(shape); return shape;
            }
        });
    }

    /** NEW333 callback fence: rotation can change during warning, position/pose/body cannot. */
    private static final class CornerSourceSnapshot {
        final Vec3 position;
        final Pose pose;
        final AABB body, standingDimensions;
        final boolean shiftDown;
        CornerSourceSnapshot(ServerPlayer player) {
            position = player.position(); pose = player.getPose(); body = player.getBoundingBox();
            standingDimensions = player.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO);
            shiftDown = player.isShiftKeyDown();
        }
        void unchanged(ServerPlayer player) {
            if (!position.equals(player.position())) throw refuse("source_changed");
            if (pose != player.getPose() || shiftDown != player.isShiftKeyDown()
                    || !body.equals(player.getBoundingBox())
                    || !standingDimensions.equals(player.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO)))
                throw refuse("body_changed");
            cornerSourceFlags(player);
        }
    }

    /** Cached native eligibility only; this final fence cannot invoke another block/shape read. */
    private static void cornerSourceFlags(ServerPlayer player) {
        if (!player.isAlive() || player.isRemoved() || player.hasDisconnected() || player.connection == null)
            throw refuse("source_unavailable");
        if (!player.onGround() || !SafeLandingProbeRules.groundedVerticalMotion(player.getDeltaMovement().y))
            throw refuse("source_airborne");
        if (player.noPhysics || player.isPassenger() || player.isSleeping() || player.isFallFlying()
                || player.getAbilities().flying || player.isInWater() || player.isInLava()
                || !finite(player.position()) || !finite(player.getDeltaMovement())
                || !Float.isFinite(player.getXRot()) || !Float.isFinite(player.getYRot()))
            throw refuse("source_motion_or_pose_unsupported");
    }

    /** NEW333's closed 24m capture. An arbitrary position cannot become a prepared point. */
    public static final class CornerPoint {
        private final ServerLevel world;
        private final UUID owner;
        private final BlockPos surface;
        private final Vec3 destination;
        private final AABB relativeBody;
        private final AABB standingDimensions;
        private CornerPoint(ServerPlayer player, BlockHitResult hit) {
            world = player.serverLevel(); owner = player.getUUID(); surface = hit.getBlockPos().immutable();
            destination = hit.getLocation(); relativeBody = player.getBoundingBox().move(player.position().scale(-1));
            standingDimensions = player.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO);
        }
        public ServerLevel world() { return world; }
        public UUID owner() { return owner; }
        public Vec3 destination() { return destination; }
        public AABB relativeBody() { return relativeBody; }
    }
    public record CornerAssessment(String reason, CornerPoint point) {
        public CornerAssessment {
            if (reason == null || reason.isEmpty() != (point != null)) throw new IllegalArgumentException("Invalid corner assessment");
        }
        public boolean valid() { return point != null; }
    }
    public static CornerAssessment cornerPoint(ServerPlayer player) {
        if (!thread(player)) return new CornerAssessment("server_thread_required", null);
        try {
            if (player.getPose() != Pose.STANDING || player.isShiftKeyDown()) throw refuse("standing_required");
            CornerSourceSnapshot snapshot = new CornerSourceSnapshot(player);
            Guard view = new Guard(player, CornerExchangeGeometry.MAX_READS, CornerExchangeGeometry.MAX_SHAPE_BOXES);
            source(player, view);
            clearance(player, view, snapshot.body, snapshot.position.y, "destination");
            Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
            if (!finite(eye) || !finite(look) || look.lengthSqr() < .999 || look.lengthSqr() > 1.001)
                throw refuse("invalid_server_aim");
            BlockHitResult hit = cornerFirstHit(player, view, eye, eye.add(look.normalize().scale(CornerExchangeGeometry.RANGE)));
            standableHit(view, hit);
            if (!CornerExchangeGeometry.withinRange(point(eye), point(hit.getLocation())))
                throw refuse("surface_out_of_range");
            CornerPoint point = new CornerPoint(player, hit);
            if (!CornerExchangeGeometry.withinRange(point(snapshot.position), point(point.destination)))
                throw refuse("destination_out_of_range");
            clearance(player, view, cornerTranslated(player.getBoundingBox(), player.position(), point.destination), point.destination.y, "destination");
            view.sameWorld(); snapshot.unchanged(player);
            return new CornerAssessment("", point);
        } catch (Refusal refusal) { return new CornerAssessment(refusal.reason, null); }
    }
    /** Reassess the complete current standing body and exact first visible saved UP surface. */
    public static CornerAssessment revalidateCornerPoint(ServerPlayer player, CornerPoint point) {
        if (!thread(player)) return new CornerAssessment("server_thread_required", null);
        try {
            if (point == null || point.world != player.serverLevel() || !point.owner.equals(player.getUUID()))
                throw refuse("candidate_world_or_owner_changed");
            if (player.getPose() != Pose.STANDING || player.isShiftKeyDown()
                    || !sameBody(point.relativeBody, player.getBoundingBox().move(player.position().scale(-1))))
                throw refuse("body_changed");
            CornerSourceSnapshot snapshot = new CornerSourceSnapshot(player);
            if (!snapshot.standingDimensions.equals(point.standingDimensions)) throw refuse("body_changed");
            Guard view = new Guard(player, CornerExchangeGeometry.MAX_READS, CornerExchangeGeometry.MAX_SHAPE_BOXES);
            source(player, view);
            clearance(player, view, snapshot.body, snapshot.position.y, "destination");
            Vec3 eye = player.getEyePosition(), direction = point.destination.subtract(eye);
            if (!finite(eye) || direction.lengthSqr() < 1e-10 || direction.lengthSqr() > CornerExchangeGeometry.RANGE * CornerExchangeGeometry.RANGE)
                throw refuse("surface_out_of_range");
            BlockHitResult hit = cornerFirstHit(player, view, eye,
                    point.destination.add(direction.normalize().scale(SURFACE_TOLERANCE)));
            standableHit(view, hit);
            if (!hit.getBlockPos().equals(point.surface)
                    || hit.getLocation().distanceToSqr(point.destination) > SURFACE_TOLERANCE * SURFACE_TOLERANCE)
                throw refuse("surface_changed");
            if (!CornerExchangeGeometry.withinRange(point(snapshot.position), point(point.destination)))
                throw refuse("destination_out_of_range");
            clearance(player, view, cornerTranslated(player.getBoundingBox(), player.position(), point.destination), point.destination.y, "destination");
            view.sameWorld(); snapshot.unchanged(player);
            return new CornerAssessment("", point);
        } catch (Refusal refusal) { return new CornerAssessment(refusal.reason, null); }
    }

    /** Closed NEW333 capture: only the opposite stored mark can become the destination. */
    public static final class CornerSwapCandidate {
        private final CornerPoint a, b;
        private final int sourceIndex;
        private final Vec3 sourcePosition;
        private final Pose pose;
        private final AABB relativeBody;
        private final boolean shiftDown;
        private CornerSwapCandidate(ServerPlayer player, CornerPoint a, CornerPoint b, int sourceIndex) {
            this.a = a; this.b = b; this.sourceIndex = sourceIndex;
            sourcePosition = player.position(); pose = player.getPose(); shiftDown = player.isShiftKeyDown();
            relativeBody = player.getBoundingBox().move(sourcePosition.scale(-1));
        }
        public ServerLevel world() { return a.world; }
        public UUID owner() { return a.owner; }
        public Vec3 sourcePosition() { return sourcePosition; }
        public Vec3 destination() { return (sourceIndex == 0 ? b : a).destination; }
        public Pose pose() { return pose; }
    }
    public record CornerSwapAssessment(String reason, CornerSwapCandidate candidate) {
        public CornerSwapAssessment {
            if (reason == null || reason.isEmpty() != (candidate != null))
                throw new IllegalArgumentException("Invalid corner swap assessment");
        }
        public boolean valid() { return candidate != null && reason.isEmpty(); }
    }

    /** Grounded standing/crouched activation near exactly one point; facing is deliberately not captured. */
    public static CornerSwapAssessment cornerSwap(ServerPlayer player, CornerPoint a, CornerPoint b) {
        if (!thread(player)) return new CornerSwapAssessment("server_thread_required", null);
        try {
            cornerOwner(player, a); cornerOwner(player, b);
            int index = cornerSourceIndex(player, a, b);
            CornerSwapCandidate candidate = new CornerSwapCandidate(player, a, b, index);
            cornerSwapClearance(player, candidate);
            return new CornerSwapAssessment("", candidate);
        } catch (Refusal refusal) { return new CornerSwapAssessment(refusal.reason, null); }
        catch (IllegalArgumentException invalid) { return new CornerSwapAssessment("corner_geometry_invalid", null); }
    }

    /** Re-read both saved surfaces and the complete current route immediately before commit. */
    public static CornerSwapAssessment revalidateCornerSwap(ServerPlayer player, CornerSwapCandidate candidate) {
        if (!thread(player)) return new CornerSwapAssessment("server_thread_required", null);
        try {
            unchangedCornerSwap(player, candidate);
            cornerSwapClearance(player, candidate);
            return new CornerSwapAssessment("", candidate);
        } catch (Refusal refusal) { return new CornerSwapAssessment(refusal.reason, null); }
        catch (IllegalArgumentException invalid) { return new CornerSwapAssessment("corner_geometry_invalid", null); }
    }

    private static void cornerOwner(ServerPlayer player, CornerPoint point) {
        if (point == null || point.world != player.serverLevel() || !point.owner.equals(player.getUUID()))
            throw refuse("candidate_world_or_owner_changed");
    }
    private static int cornerSourceIndex(ServerPlayer player, CornerPoint a, CornerPoint b) {
        int index = CornerExchangeGeometry.sourceIndex(point(player.position()), point(a.destination), point(b.destination));
        if (index < 0) throw refuse(CornerExchangeGeometry.near(point(player.position()), point(a.destination))
                && CornerExchangeGeometry.near(point(player.position()), point(b.destination))
                ? "source_near_both_points" : "source_not_near_point");
        return index;
    }
    private static void unchangedCornerSwap(ServerPlayer player, CornerSwapCandidate candidate) {
        if (candidate == null) throw refuse("candidate_world_or_owner_changed");
        cornerOwner(player, candidate.a); cornerOwner(player, candidate.b);
        if (!candidate.sourcePosition.equals(player.position())) throw refuse("source_changed");
        if (player.getPose() != Pose.CROUCHING && player.getPose() != Pose.STANDING)
            throw refuse("source_motion_or_pose_unsupported");
        if (candidate.shiftDown != player.isShiftKeyDown()) throw refuse("body_changed");
        if (candidate.pose != player.getPose()
                || !candidate.relativeBody.equals(player.getBoundingBox().move(player.position().scale(-1))))
            throw refuse("body_changed");
        // Derive standing dimensions without setPose(), callbacks or a transient body mutation.
        AABB dimensions = player.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO);
        if (!dimensions.equals(candidate.a.standingDimensions) || !dimensions.equals(candidate.b.standingDimensions)
                || !sameBody(candidate.a.relativeBody, dimensions) || !sameBody(candidate.b.relativeBody, dimensions))
            throw refuse("body_changed");
        if (cornerSourceIndex(player, candidate.a, candidate.b) != candidate.sourceIndex) throw refuse("source_changed");
        cornerSourceFlags(player);
    }

    private static void cornerSwapClearance(ServerPlayer player, CornerSwapCandidate candidate) {
        unchangedCornerSwap(player, candidate);
        if (!CornerExchangeGeometry.withinRange(point(candidate.a.destination), point(candidate.b.destination)))
            throw refuse("point_pair_out_of_range");
        if (!CornerExchangeGeometry.withinRange(point(candidate.sourcePosition), point(candidate.destination())))
            throw refuse("destination_out_of_range");
        if (candidate.sourcePosition.distanceToSqr(candidate.destination()) <= 1e-8)
            throw refuse("destination_has_no_displacement");
        Guard view = new Guard(player, CornerExchangeGeometry.MAX_READS, CornerExchangeGeometry.MAX_SHAPE_BOXES);
        source(player, view);
        CornerPoint sourcePoint = candidate.sourceIndex == 0 ? candidate.a : candidate.b;
        CornerPoint targetPoint = candidate.sourceIndex == 0 ? candidate.b : candidate.a;
        // A crouched player must fit the full stored standing body at the real source too.
        AABB standingBody = sourcePoint.relativeBody.move(candidate.sourcePosition);
        clearance(player, view, standingBody, candidate.sourcePosition.y, "destination", true);
        cornerSwapPoint(player, view, sourcePoint, true);
        cornerSwapPoint(player, view, targetPoint, false);

        var sweep = CornerExchangeGeometry.sweep(box(standingBody), point(candidate.sourcePosition), point(candidate.destination()));
        var b = sweep.bounds();
        AABB envelope = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
        view.cornerSwapVolume(envelope); view.scope = envelope.inflate(1.001);
        var cells = sweep.cells();
        var context = new LandingCollisionContext(player, candidate.sourcePosition.y);
        for (BlockPos pos : BlockPos.betweenClosed(cells.minX(), cells.minY(), cells.minZ(), cells.maxX(), cells.maxY(), cells.maxZ())) {
            if (view.world.isOutsideBuildHeight(pos)) continue;
            boolean crossed = sweep.intersects(box(new AABB(pos)));
            BlockState state = view.getBlockState(pos);
            if (crossed && !view.getFluidState(pos).isEmpty()) throw refuse("corridor_fluid");
            if (crossed && destinationHazard(state)) throw refuse("corridor_hazard");
            VoxelShape shape = state.getCollisionShape(view, pos, context); view.shape(shape);
            shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
                if (sweep.intersects(new SafeLandingProbeGeometry.Box(x0 + pos.getX(), y0 + pos.getY(), z0 + pos.getZ(),
                        x1 + pos.getX(), y1 + pos.getY(), z1 + pos.getZ()))) throw refuse("corridor_block_collision");
            });
        }
        List<Entity> entities = new ArrayList<>();
        view.world.getEntities(EntityTypeTest.forClass(Entity.class), envelope.inflate(1e-7), entity -> {
            view.entity();
            // The caster is the one permitted occupant of its unchanged source. Every other
            // native collidable entity or live living body blocks; noncolliding media stay non-solid.
            return entity != player && !entity.isSpectator()
                    && (player.canCollideWith(entity) || entity instanceof LivingEntity && entity.isAlive());
        }, entities, SafeLandingProbeGeometry.MAX_ENTITIES + 1);
        if (entities.size() > SafeLandingProbeGeometry.MAX_ENTITIES) throw refuse("entity_scan_budget");
        for (Entity entity : entities)
            if (sweep.intersects(box(entity.getBoundingBox()))) throw refuse("corridor_entity_collision");
        view.sameWorld(); unchangedCornerSwap(player, candidate);
    }

    private static void cornerSwapPoint(ServerPlayer player, Guard view, CornerPoint point, boolean sourcePoint) {
        AABB body = point.relativeBody.move(point.destination);
        clearance(player, view, body, point.destination.y, "destination", sourcePoint);
        // Recheck the exact saved UP face locally. Reusing the attack validator would require
        // standing input and a new eye ray instead of validating the prepared corner exchange route.
        view.scope = body.inflate(1.001);
        BlockState state = view.getBlockState(point.surface);
        if (!view.getFluidState(point.surface).isEmpty()) throw refuse("first_surface_fluid");
        VoxelShape shape = state.getCollisionShape(view, point.surface, new LandingCollisionContext(player, point.destination.y));
        view.shape(shape);
        BlockHitResult hit = shape.clip(point.destination.add(0, .01, 0), point.destination.add(0, -.01, 0), point.surface);
        if (hit == null || hit.isInside() || hit.getDirection() != Direction.UP
                || !hit.getBlockPos().equals(point.surface)
                || hit.getLocation().distanceToSqr(point.destination) > SURFACE_TOLERANCE * SURFACE_TOLERANCE)
            throw refuse("surface_changed");
    }

    /** Kept separate from the unchanged NEW313 12m first-hit entry. */
    private static BlockHitResult cornerFirstHit(ServerPlayer player, Guard view, Vec3 from, Vec3 to) {
        if (!finite(from) || !finite(to) || from.distanceToSqr(to) > 576.001) throw refuse("invalid_ray");
        view.scope = new AABB(from, to).inflate(1.001);
        return view.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player) {
            @Override public VoxelShape getBlockShape(BlockState state, BlockGetter blocks, BlockPos pos) {
                VoxelShape shape = super.getBlockShape(state, blocks, pos); view.shape(shape); return shape;
            }
            @Override public VoxelShape getFluidShape(FluidState fluid, BlockGetter blocks, BlockPos pos) {
                VoxelShape shape = super.getFluidShape(fluid, blocks, pos); view.shape(shape); return shape;
            }
        });
    }

    public static Assessment preview(ServerPlayer player) {
        if (!thread(player)) return refused("server_thread_required");
        try {
            Guard view = new Guard(player);
            source(player, view);
            Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
            if (!finite(eye) || !finite(look) || look.lengthSqr() < .999 || look.lengthSqr() > 1.001)
                throw refuse("invalid_server_aim");
            BlockHitResult hit = firstHit(player, view, eye, eye.add(look.scale(SafeLandingProbeRules.RANGE)));
            standableHit(view, hit);
            Vec3 destination = hit.getLocation();
            if (!range(player.position(), destination)) throw refuse("destination_out_of_range");
            if (player.position().distanceToSqr(destination) <= 1e-8) throw refuse("destination_has_no_displacement");
            AABB body = translated(player, destination);
            clearance(player, view, body, destination.y, "destination");
            Candidate candidate = new Candidate(view.world, player.getUUID(), hit.getBlockPos(), destination,
                    destination, player.position(), player.getPose(), player.getBoundingBox().move(player.position().scale(-1)));
            view.sameWorld();
            return new Assessment("", candidate);
        } catch (Refusal refusal) { return refused(refusal.reason); }
    }

    /** Frozen destination, current native source/pose/body, and a fresh first-hit sight line. */
    public static Assessment revalidate(ServerPlayer player, Candidate candidate) {
        if (!thread(player)) return refused("server_thread_required");
        if (candidate == null || candidate.world != player.serverLevel() || !candidate.owner.equals(player.getUUID()))
            return refused("candidate_world_or_owner_changed");
        try {
            Guard view = new Guard(player);
            source(player, view);
            if (candidate.pose != player.getPose()
                    || !sameBody(candidate.relativeBody, player.getBoundingBox().move(player.position().scale(-1))))
                throw refuse("body_changed");
            if (!range(player.position(), candidate.destination)) throw refuse("destination_out_of_range");
            Vec3 eye = player.getEyePosition(), direction = candidate.surfacePoint.subtract(eye);
            if (!finite(eye) || !range(eye, candidate.surfacePoint) || direction.lengthSqr() < 1e-10)
                throw refuse("surface_out_of_range");
            // Extend only through the frozen boundary to let the native clip identify that
            // exact surface. The first result is never replaced with another candidate.
            Vec3 end = candidate.surfacePoint.add(direction.normalize().scale(SURFACE_TOLERANCE));
            BlockHitResult hit = firstHit(player, view, eye, end);
            standableHit(view, hit);
            if (!hit.getBlockPos().equals(candidate.surface)
                    || hit.getLocation().distanceToSqr(candidate.surfacePoint) > SURFACE_TOLERANCE * SURFACE_TOLERANCE)
                throw refuse("surface_changed");
            clearance(player, view, translated(player, candidate.destination), candidate.destination.y, "destination");
            view.sameWorld();
            return new Assessment("", candidate);
        } catch (Refusal refusal) { return refused(refusal.reason); }
    }

    /** Immediate, single exact three-meter lateral candidate, selected from the server yaw. */
    public static PhaseAssessment phase(ServerPlayer player, boolean left) {
        if (!thread(player)) return phaseRefused("server_thread_required");
        try {
            Guard view = new Guard(player);
            source(player, view);
            PhaseCandidate candidate = new PhaseCandidate(player, left);
            phaseClearance(player, view, candidate);
            unchangedPhase(player, candidate);
            view.sameWorld();
            return new PhaseAssessment("", candidate);
        } catch (Refusal refusal) { return phaseRefused(refusal.reason); }
        catch (IllegalArgumentException invalid) { return phaseRefused("phase_geometry_invalid"); }
    }

    /** The frozen source, yaw, pose and exact endpoint all survive the final read-only check. */
    public static PhaseAssessment revalidatePhase(ServerPlayer player, PhaseCandidate candidate) {
        if (!thread(player)) return phaseRefused("server_thread_required");
        try {
            unchangedPhase(player, candidate);
            Guard view = new Guard(player);
            source(player, view);
            phaseClearance(player, view, candidate);
            unchangedPhase(player, candidate);
            view.sameWorld();
            return new PhaseAssessment("", candidate);
        } catch (Refusal refusal) { return phaseRefused(refusal.reason); }
        catch (IllegalArgumentException invalid) { return phaseRefused("phase_geometry_invalid"); }
    }

    private static void unchangedPhase(ServerPlayer player, PhaseCandidate candidate) {
        if (candidate == null || candidate.world != player.serverLevel() || !candidate.owner.equals(player.getUUID()))
            throw refuse("candidate_world_or_owner_changed");
        if (!candidate.sourcePosition.equals(player.position())) throw refuse("source_position_changed");
        if (Float.compare(candidate.yaw, player.getYRot()) != 0) throw refuse("source_yaw_changed");
        if (candidate.pose != player.getPose()
                || !sameBody(candidate.relativeBody, player.getBoundingBox().move(player.position().scale(-1))))
            throw refuse("body_changed");
        var expected = PhaseSidestepGeometry.destination(point(candidate.sourcePosition), candidate.yaw, candidate.left, candidate.distance);
        if (!point(candidate.destination).equals(expected)) throw refuse("phase_destination_changed");
    }

    /** The admitted sidestep distance of this player's growth position; the plain 3 m whenever no growth is available. */
    private static double phaseDistance(ServerPlayer player) {
        try { return PhaseSidestepRules.distance(HighPowerGrowth.values(player).position()); }
        catch (IllegalStateException | IllegalArgumentException unavailable) { return PhaseSidestepRules.DISTANCE; }
    }

    private static void phaseClearance(ServerPlayer player, Guard view, PhaseCandidate candidate) {
        // Keep the proven full landing checks, including the entire footprint and destination hazards.
        clearance(player, view, translated(player, candidate.destination), candidate.destination.y, "destination");
        var sweep = PhaseSidestepGeometry.sweep(box(player.getBoundingBox()), point(candidate.sourcePosition),
                candidate.yaw, candidate.left, candidate.distance);
        var bounds = sweep.bounds();
        AABB envelope = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
        view.phaseVolume(envelope);
        view.scope = envelope.inflate(1.001);
        // Only actual swept body cells are the corridor. Its broad-phase corner cells do not
        // become a filled sector, and there is deliberately no intermediate floor requirement.
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(envelope.minX, envelope.minY, envelope.minZ),
                BlockPos.containing(Math.nextDown(envelope.maxX), Math.nextDown(envelope.maxY), Math.nextDown(envelope.maxZ)))) {
            if (sweep.intersects(box(new AABB(pos))) && !view.getFluidState(pos).isEmpty()) throw refuse("corridor_fluid");
        }
        var cells = sweep.cells();
        var context = new LandingCollisionContext(player, candidate.sourcePosition.y);
        for (BlockPos pos : BlockPos.betweenClosed(cells.minX(), cells.minY(), cells.minZ(), cells.maxX(), cells.maxY(), cells.maxZ())) {
            if (view.world.isOutsideBuildHeight(pos)) continue;
            VoxelShape local = view.getBlockState(pos).getCollisionShape(view, pos, context);
            view.shape(local);
            local.forAllBoxes((a, b, c, d, e, f) -> {
                var nativeBox = new SafeLandingProbeGeometry.Box(a + pos.getX(), b + pos.getY(), c + pos.getZ(),
                        d + pos.getX(), e + pos.getY(), f + pos.getZ());
                if (sweep.intersects(nativeBox)) throw refuse("corridor_block_collision");
            });
        }
        List<Entity> entities = new ArrayList<>();
        view.world.getEntities(EntityTypeTest.forClass(Entity.class), envelope.inflate(1e-7), entity -> {
            view.entity();
            return entity != player && !entity.isSpectator()
                    && (player.canCollideWith(entity) || entity instanceof LivingEntity && entity.isAlive());
        }, entities, SafeLandingProbeGeometry.MAX_ENTITIES + 1);
        if (entities.size() > SafeLandingProbeGeometry.MAX_ENTITIES) throw refuse("entity_scan_budget");
        for (Entity entity : entities)
            if (sweep.intersects(box(entity.getBoundingBox()))) throw refuse("corridor_entity_collision");
        view.sameWorld();
    }

    /** Convenience source fence, with no candidate, cost, cooldown or movement mutation. */
    public static String sourceRefusal(ServerPlayer player) {
        if (!thread(player)) return "server_thread_required";
        try { source(player, new Guard(player)); return ""; }
        catch (Refusal refusal) { return refusal.reason; }
    }

    private static void source(ServerPlayer player, Guard view) {
        if (!player.isAlive() || player.isRemoved() || player.hasDisconnected() || player.connection == null)
            throw refuse("source_unavailable");
        if (!player.onGround() || !SafeLandingProbeRules.groundedVerticalMotion(player.getDeltaMovement().y))
            throw refuse("source_airborne");
        if (player.noPhysics || player.isPassenger() || player.isSleeping() || player.isFallFlying()
                || player.getAbilities().flying || player.isInWater() || player.isInLava()
                || !finite(player.position()) || !finite(player.getDeltaMovement())
                || !Float.isFinite(player.getXRot()) || !Float.isFinite(player.getYRot()))
            throw refuse("source_motion_or_pose_unsupported");
        clearance(player, view, player.getBoundingBox(), player.getY(), "source");
        // isInWaterOrBubble() reads Entity.getInBlockState(), which may load a chunk.
        // Use the guarded accessor instead; clearance has already refused all body fluids.
        if (view.getBlockState(player.blockPosition()).is(Blocks.BUBBLE_COLUMN))
            throw refuse("source_motion_or_pose_unsupported");
        // The body/support fence ran before this native eligibility method reads a block.
        if (player.onClimbable()) throw refuse("source_climbing");
    }

    private static BlockHitResult firstHit(ServerPlayer player, Guard view, Vec3 from, Vec3 to) {
        if (!finite(from) || !finite(to) || from.distanceToSqr(to) > 144.001) throw refuse("invalid_ray");
        view.scope = new AABB(from, to).inflate(1.001);
        return view.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player) {
            @Override public VoxelShape getBlockShape(BlockState state, BlockGetter blocks, BlockPos pos) {
                VoxelShape shape = super.getBlockShape(state, blocks, pos);
                view.shape(shape);
                return shape;
            }
            @Override public VoxelShape getFluidShape(FluidState fluid, BlockGetter blocks, BlockPos pos) {
                VoxelShape shape = super.getFluidShape(fluid, blocks, pos);
                view.shape(shape);
                return shape;
            }
        });
    }
    private static void standableHit(Guard view, BlockHitResult hit) {
        if (hit.getType() != HitResult.Type.BLOCK) throw refuse("no_visible_surface");
        if (!view.getFluidState(hit.getBlockPos()).isEmpty()) throw refuse("first_surface_fluid");
        if (hit.isInside() || hit.getDirection() != Direction.UP) throw refuse("first_surface_not_up");
    }

    private static void clearance(ServerPlayer player, Guard view, AABB body, double feet, String prefix) {
        clearance(player, view, body, feet, prefix, true);
    }
    private static void clearance(ServerPlayer player, Guard view, AABB body, double feet, String prefix, boolean excludeCaster) {
        if (!box(body).body() || Math.abs(body.minY - feet) > SURFACE_TOLERANCE)
            throw refuse(prefix + "_body_invalid");
        AABB support = new AABB(body.minX, body.minY - SafeLandingProbeGeometry.SUPPORT_DEPTH,
                body.minZ, body.maxX, body.minY, body.maxZ);
        AABB envelope = body.minmax(support);
        view.volume(envelope);
        view.scope = envelope.inflate(1.001);
        // Hazards are checked only in the actual destination body/support cells. A grounded
        // player can escape a hazardous source; attached burning is preserved by the controller.
        // Fluid, build-height and interaction policy cover the entire actual body and floor.
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(envelope.minX, envelope.minY, envelope.minZ),
                BlockPos.containing(Math.nextDown(envelope.maxX), Math.nextDown(envelope.maxY), Math.nextDown(envelope.maxZ)))) {
            if ("destination".equals(prefix) && destinationHazard(view.getBlockState(pos)))
                throw refuse("destination_hazard");
            if (!view.getFluidState(pos).isEmpty()) throw refuse(prefix + "_fluid");
        }
        SafeLandingProbeGeometry.Cells cells;
        try { cells = SafeLandingProbeGeometry.collisionCells(box(envelope)); }
        catch (IllegalArgumentException invalid) { throw refuse("collision_scan_budget"); }
        VoxelShape bodyShape = Shapes.create(body), needed = Shapes.create(support), supported = Shapes.empty();
        var context = new LandingCollisionContext(player, feet);
        for (BlockPos pos : BlockPos.betweenClosed(cells.minX(), cells.minY(), cells.minZ(), cells.maxX(), cells.maxY(), cells.maxZ())) {
            // Outside build height has no native block. Never substitute air for an unloaded cell.
            if (view.world.isOutsideBuildHeight(pos)) continue;
            BlockState state = view.getBlockState(pos);
            VoxelShape local = state.getCollisionShape(view, pos, context);
            view.shape(local);
            VoxelShape solid = local.move(pos.getX(), pos.getY(), pos.getZ());
            if (Shapes.joinIsNotEmpty(bodyShape, solid, BooleanOp.AND)) throw refuse(prefix + "_block_collision");
            if (Shapes.joinIsNotEmpty(needed, solid, BooleanOp.AND)) {
                unionBudget(supported, solid);
                supported = Shapes.or(supported, solid);
            }
        }
        if (Shapes.joinIsNotEmpty(needed, supported, BooleanOp.ONLY_FIRST)) throw refuse(prefix + "_floor_unsupported");
        // Native collision eligibility, with a hard result/predicate budget instead of an
        // unbounded getEntityCollisions list. A crowded query refuses before teleporting.
        List<Entity> entities = new ArrayList<>();
        view.world.getEntities(EntityTypeTest.forClass(Entity.class), body.inflate(1e-7), entity -> {
            view.entity();
            return (entity != player || !excludeCaster) && !entity.isSpectator()
                    && (player.canCollideWith(entity) || entity instanceof LivingEntity && entity.isAlive());
        }, entities, SafeLandingProbeGeometry.MAX_ENTITIES + 1);
        if (entities.size() > SafeLandingProbeGeometry.MAX_ENTITIES) throw refuse("entity_scan_budget");
        for (Entity entity : entities) if (entity.getBoundingBox().intersects(body)) throw refuse(prefix + "_entity_collision");
        view.sameWorld();
    }

    /** Explicit native first-prototype hazards; no claim to classify arbitrary modded hazards. */
    private static boolean destinationHazard(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK)
                || (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) && state.getValue(CampfireBlock.LIT)
                || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.COBWEB)
                || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY);
    }

    /** Preserve the native entity context while evaluating its floor height at the landing. */
    private static final class LandingCollisionContext extends EntityCollisionContext {
        LandingCollisionContext(ServerPlayer player, double feet) {
            super(player.isDescending(), feet, player.getMainHandItem(), player::canStandOnFluid, player);
        }
    }

    /** Fail-closed native BlockGetter. getChunkNow never creates or loads a chunk. */
    private static final class Guard implements BlockGetter {
        final ServerPlayer player;
        final ServerLevel world;
        AABB scope;
        int reads, shapes, entities;
        final int readLimit, shapeLimit;
        Guard(ServerPlayer player) { this(player,false); }
        private Guard(ServerPlayer player,boolean returnMark) {
            this(player, returnMark ? 32768 : SafeLandingProbeGeometry.MAX_READS, SafeLandingProbeGeometry.MAX_SHAPE_BOXES);
        }
        private Guard(ServerPlayer player, int readLimit, int shapeLimit) {
            this.player = player; world = player.serverLevel(); this.readLimit = readLimit; this.shapeLimit = shapeLimit;
        }
        void sameWorld() {
            if (player.level() != world || player.serverLevel() != world || player.isRemoved()) throw refuse("world_changed");
        }
        void volume(AABB volume) {
            sameWorld();
            if (!box(volume).body() || volume.minY < world.getMinBuildHeight() || volume.maxY > world.getMaxBuildHeight())
                throw refuse("outside_build_height");
            if (!world.getWorldBorder().isWithinBounds(volume)) throw refuse("outside_world_border");
            int minX = ((int)Math.floor(volume.minX)) >> 4, maxX = ((int)Math.floor(Math.nextDown(volume.maxX))) >> 4;
            int minZ = ((int)Math.floor(volume.minZ)) >> 4, maxZ = ((int)Math.floor(Math.nextDown(volume.maxZ))) >> 4;
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
                if (world.getChunkSource().getChunkNow(x, z) == null) throw refuse("unloaded_chunk");
        }
        /** NEW312 sweep can exceed the single-body four-meter edge; its extent stays fixed and bounded. */
        void phaseVolume(AABB volume) {
            sameWorld();
            if (!box(volume).finite() || volume.getXsize() > 7.000001 || volume.getYsize() > 4
                    || volume.getZsize() > 7.000001) throw refuse("collision_scan_budget");
            if (volume.minY < world.getMinBuildHeight() || volume.maxY > world.getMaxBuildHeight())
                throw refuse("outside_build_height");
            if (!world.getWorldBorder().isWithinBounds(volume)) throw refuse("outside_world_border");
            int minX = ((int)Math.floor(volume.minX)) >> 4, maxX = ((int)Math.floor(Math.nextDown(volume.maxX))) >> 4;
            int minZ = ((int)Math.floor(volume.minZ)) >> 4, maxZ = ((int)Math.floor(Math.nextDown(volume.maxZ))) >> 4;
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
                if (world.getChunkSource().getChunkNow(x, z) == null) throw refuse("unloaded_chunk");
        }
        /** Closed NEW322 path only; does not broaden NEW312/313 scan or range limits. */
        void returnVolume(AABB volume) {
            sameWorld();
            double edge = ReturnMarkRules.RANGE + SafeLandingProbeGeometry.MAX_BODY_EDGE;
            if (!box(volume).finite() || volume.getXsize() > edge || volume.getYsize() > edge || volume.getZsize() > edge)
                throw refuse("collision_scan_budget");
            if(volume.minY<world.getMinBuildHeight()||volume.maxY>world.getMaxBuildHeight())throw refuse("outside_build_height");
            if(!world.getWorldBorder().isWithinBounds(volume))throw refuse("outside_world_border");
            int x0=((int)Math.floor(volume.minX))>>4,x1=((int)Math.floor(Math.nextDown(volume.maxX)))>>4;
            int z0=((int)Math.floor(volume.minZ))>>4,z1=((int)Math.floor(Math.nextDown(volume.maxZ)))>>4;
            for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(world.getChunkSource().getChunkNow(x,z)==null)throw refuse("unloaded_chunk");
        }
        /** NEW342 only: a 40m translation with a full standing-body envelope, never a global limit change. */
        void emergencyReturnVolume(AABB volume) {
            sameWorld();
            double edge = EmergencyReturnGeometry.RANGE + SafeLandingProbeGeometry.MAX_BODY_EDGE;
            if (!box(volume).finite() || volume.getXsize() > edge || volume.getYsize() > edge || volume.getZsize() > edge)
                throw refuse("collision_scan_budget");
            if (volume.minY < world.getMinBuildHeight() || volume.maxY > world.getMaxBuildHeight())
                throw refuse("outside_build_height");
            if (!world.getWorldBorder().isWithinBounds(volume)) throw refuse("outside_world_border");
            int x0 = ((int)Math.floor(volume.minX)) >> 4, x1 = ((int)Math.floor(Math.nextDown(volume.maxX))) >> 4;
            int z0 = ((int)Math.floor(volume.minZ)) >> 4, z1 = ((int)Math.floor(Math.nextDown(volume.maxZ))) >> 4;
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++)
                if (world.getChunkSource().getChunkNow(x, z) == null) throw refuse("unloaded_chunk");
        }
        /** NEW321 only: a maximum 32m translation of a maximum four-meter standing body. */
        void crossSwapVolume(AABB volume) {
            sameWorld();
            double edge = CrossSwapGeometry.RANGE + SafeLandingProbeGeometry.MAX_BODY_EDGE;
            if (!box(volume).finite() || volume.getXsize() > edge || volume.getYsize() > edge || volume.getZsize() > edge)
                throw refuse("collision_scan_budget");
            if (volume.minY < world.getMinBuildHeight() || volume.maxY > world.getMaxBuildHeight())
                throw refuse("outside_build_height");
            if (!world.getWorldBorder().isWithinBounds(volume)) throw refuse("outside_world_border");
            int x0 = ((int)Math.floor(volume.minX)) >> 4, x1 = ((int)Math.floor(Math.nextDown(volume.maxX))) >> 4;
            int z0 = ((int)Math.floor(volume.minZ)) >> 4, z1 = ((int)Math.floor(Math.nextDown(volume.maxZ))) >> 4;
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++)
                if (world.getChunkSource().getChunkNow(x, z) == null) throw refuse("unloaded_chunk");
        }
        /** NEW333 only: a maximum 24m translation of a maximum four-meter standing body. */
        void cornerSwapVolume(AABB volume) {
            sameWorld();
            double edge = CornerExchangeGeometry.RANGE + SafeLandingProbeGeometry.MAX_BODY_EDGE;
            if (!box(volume).finite() || volume.getXsize() > edge || volume.getYsize() > edge || volume.getZsize() > edge)
                throw refuse("collision_scan_budget");
            if (volume.minY < world.getMinBuildHeight() || volume.maxY > world.getMaxBuildHeight())
                throw refuse("outside_build_height");
            if (!world.getWorldBorder().isWithinBounds(volume)) throw refuse("outside_world_border");
            int x0 = ((int)Math.floor(volume.minX)) >> 4, x1 = ((int)Math.floor(Math.nextDown(volume.maxX))) >> 4;
            int z0 = ((int)Math.floor(volume.minZ)) >> 4, z1 = ((int)Math.floor(Math.nextDown(volume.maxZ))) >> 4;
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++)
                if (world.getChunkSource().getChunkNow(x, z) == null) throw refuse("unloaded_chunk");
        }
        LevelChunk chunk(BlockPos pos) {
            sameWorld();
            if (++reads > readLimit) throw refuse("block_read_budget");
            if (scope == null || !scope.intersects(new AABB(pos))) throw refuse("shape_neighbor_out_of_scope");
            if (world.isOutsideBuildHeight(pos)) throw refuse("outside_build_height");
            if (!world.getWorldBorder().isWithinBounds(pos)) throw refuse("outside_world_border");
            LevelChunk chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) throw refuse("unloaded_chunk");
            if (!world.mayInteract(player, pos)) throw refuse("interaction_denied");
            sameWorld();
            return chunk;
        }
        @Override public BlockState getBlockState(BlockPos pos) { return chunk(pos).getBlockState(pos); }
        @Override public FluidState getFluidState(BlockPos pos) { return chunk(pos).getFluidState(pos); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return chunk(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK); }
        @Override public BlockHitResult clipWithInteractionOverride(Vec3 from, Vec3 to, BlockPos pos,
                                                                    VoxelShape shape, BlockState state) {
            BlockHitResult hit = shape.clip(from, to, pos);
            if (hit != null) {
                VoxelShape interaction = state.getInteractionShape(this, pos);
                shape(interaction);
                BlockHitResult alternate = interaction.clip(from, to, pos);
                if (alternate != null && alternate.getLocation().subtract(from).lengthSqr()
                        < hit.getLocation().subtract(from).lengthSqr()) return hit.withDirection(alternate.getDirection());
            }
            return hit;
        }
        @Override public int getHeight() { return world.getHeight(); }
        @Override public int getMinBuildHeight() { return world.getMinBuildHeight(); }
        void entity() { sameWorld(); if (++entities > SafeLandingProbeGeometry.MAX_ENTITIES) throw refuse("entity_scan_budget"); }
        void shape(VoxelShape shape) {
            if (grid(shape) > MAX_SHAPE_GRID) throw refuse("shape_grid_budget");
            shape.forAllBoxes((a, b, c, d, e, f) -> {
                if (++shapes > shapeLimit) throw refuse("shape_box_budget");
                if (!new SafeLandingProbeGeometry.Box(a, b, c, d, e, f).finite()) throw refuse("invalid_native_shape");
            });
        }
    }

    private static long grid(VoxelShape shape) {
        long cells = 1;
        for (Direction.Axis axis : Direction.Axis.values()) {
            var coords = shape.getCoords(axis);
            if (coords.size() > 66) return Long.MAX_VALUE;
            for (double coordinate : coords) if (!Double.isFinite(coordinate)) throw refuse("invalid_native_shape");
            cells *= Math.max(1, coords.size() - 1);
        }
        return cells;
    }
    private static void unionBudget(VoxelShape a, VoxelShape b) {
        long cells = 1;
        for (Direction.Axis axis : Direction.Axis.values()) {
            Set<Double> coords = new HashSet<>();
            for (double coordinate : a.getCoords(axis)) coords.add(coordinate);
            for (double coordinate : b.getCoords(axis)) coords.add(coordinate);
            cells *= Math.max(1, coords.size() - 1);
            if (cells > MAX_SHAPE_GRID) throw refuse("support_grid_budget");
        }
    }
    private static AABB translated(ServerPlayer player, Vec3 destination) {
        try {
            var moved = SafeLandingProbeGeometry.translatedBody(box(player.getBoundingBox()), point(player.position()), point(destination));
            return new AABB(moved.minX(), moved.minY(), moved.minZ(), moved.maxX(), moved.maxY(), moved.maxZ());
        } catch (IllegalArgumentException invalid) { throw refuse("body_translation_invalid"); }
    }
    /** The independent closed 32m translator also repairs NEW321's accidental NEW313 12m cap. */
    private static AABB crossTranslated(AABB body, Vec3 source, Vec3 destination) {
        try {
            var moved = CrossSwapGeometry.translatedBody(box(body), point(source), point(destination));
            return new AABB(moved.minX(), moved.minY(), moved.minZ(), moved.maxX(), moved.maxY(), moved.maxZ());
        } catch (IllegalArgumentException invalid) { throw refuse("body_translation_invalid"); }
    }
    /** Dedicated NEW333 translation; historical wrappers keep their own range policies. */
    private static AABB cornerTranslated(AABB body, Vec3 source, Vec3 destination) {
        try {
            var moved = CornerExchangeGeometry.translatedBody(box(body), point(source), point(destination));
            return new AABB(moved.minX(), moved.minY(), moved.minZ(), moved.maxX(), moved.maxY(), moved.maxZ());
        } catch (IllegalArgumentException invalid) { throw refuse("body_translation_invalid"); }
    }
    private static boolean sameBody(AABB first, AABB second) {
        return Math.abs(first.minX - second.minX) <= 1e-7 && Math.abs(first.minY - second.minY) <= 1e-7
                && Math.abs(first.minZ - second.minZ) <= 1e-7 && Math.abs(first.maxX - second.maxX) <= 1e-7
                && Math.abs(first.maxY - second.maxY) <= 1e-7 && Math.abs(first.maxZ - second.maxZ) <= 1e-7;
    }
    private static boolean range(Vec3 a, Vec3 b) { return SafeLandingProbeGeometry.withinRange(point(a), point(b)); }
    private static boolean finite(Vec3 value) { return value != null && point(value).finite(); }
    private static SafeLandingProbeGeometry.Point point(Vec3 point) { return new SafeLandingProbeGeometry.Point(point.x, point.y, point.z); }
    private static SafeLandingProbeGeometry.Box box(AABB box) {
        return new SafeLandingProbeGeometry.Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }
    private static boolean thread(ServerPlayer player) { return player != null && player.getServer() != null && player.getServer().isSameThread(); }
    private static Assessment refused(String reason) { return new Assessment(reason, null); }
    private static PhaseAssessment phaseRefused(String reason) { return new PhaseAssessment(reason, null); }
    private static Refusal refuse(String reason) { return new Refusal(reason); }
    private static final class Refusal extends RuntimeException {
        final String reason;
        Refusal(String reason) { super(reason, null, false, false); this.reason = reason; }
    }
}
