package cn.academy.port.compat.sable;

import cn.academy.port.highpower.ConstructLink;
import cn.academy.port.highpower.core.ConstructRules;
import cn.academy.port.highpower.core.V3;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.joml.Matrix3dc;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Sable backend for V-ADV-01. Loaded reflectively only when Sable is installed. Every world mutation goes through Sable's own
 * assembly helper (blocks are moved, never copied), and every grab or landing is re-validated against protection events.
 */
public final class SableConstructBridge implements ConstructLink {
    /** Blocks that never join a construct (datapack-extensible); natural terrain stops the search so the ground is not lifted. */
    static final TagKey<Block> FORBIDDEN = BlockTags.create(ResourceLocation.fromNamespaceAndPath("academy", "construct_forbidden")),
            TERRAIN = BlockTags.create(ResourceLocation.fromNamespaceAndPath("academy", "construct_terrain"));
    private static final String OWNER_KEY = "academy_construct_owner";
    private record Control(ServerLevel level, double[] target, double yaw, double maxAccel) {}
    private record Held(List<BlockPos> plotBlocks) {}
    private static final Map<UUID, Control> CONTROLS = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> EFFORT = new ConcurrentHashMap<>();
    private static final Map<UUID, Held> HELD = new ConcurrentHashMap<>();
    private static boolean listening;

    /**
     * Opt-in read-only witness for the far-coordinate precision question: {@code -Dacademy.highpower.constructFarDiagnostic=true}
     * logs the hold controller's requested world target, the body centre Sable reports and their error once per physics step.
     * It never changes a target, an impulse, a receipt or an assertion; it exists so the residual error can be compared with the
     * float quantum of the coordinates it is measured at. Line count is bounded by
     * {@code -Dacademy.highpower.constructFarDiagnostic.limit} (default 240).
     */
    private static final boolean FAR_DIAGNOSTIC = Boolean.getBoolean("academy.highpower.constructFarDiagnostic");
    private static final int FAR_DIAGNOSTIC_LIMIT = Integer.getInteger("academy.highpower.constructFarDiagnostic.limit", 240);
    private static final java.util.concurrent.atomic.AtomicInteger FAR_DIAGNOSTIC_LINES = new java.util.concurrent.atomic.AtomicInteger();
    private static final org.slf4j.Logger FAR_LOG = org.slf4j.LoggerFactory.getLogger("academy.construct_far_qa");

    public SableConstructBridge() {
        synchronized (SableConstructBridge.class) {
            if (!listening) { SableEventPlatform.INSTANCE.onPhysicsTick(SableConstructBridge::physicsTick); listening = true; }
        }
    }

    // ---- selection ---------------------------------------------------------------------------------------------------------------

    static boolean forbidden(BlockState s) {
        return s.isAir() || s.is(FORBIDDEN) || s.hasBlockEntity() || !s.getFluidState().isEmpty() && s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock
                || s.is(Blocks.BEDROCK) || s.is(Blocks.NETHER_PORTAL) || s.is(Blocks.END_PORTAL) || s.is(Blocks.END_PORTAL_FRAME) || s.is(Blocks.END_GATEWAY)
                || s.is(Blocks.COMMAND_BLOCK) || s.is(Blocks.CHAIN_COMMAND_BLOCK) || s.is(Blocks.REPEATING_COMMAND_BLOCK) || s.is(Blocks.STRUCTURE_BLOCK)
                || s.is(Blocks.JIGSAW) || s.is(Blocks.BARRIER) || s.is(Blocks.LIGHT) || s.is(Blocks.REINFORCED_DEEPSLATE) || s.is(Blocks.STRUCTURE_VOID)
                || s.is(Blocks.MOVING_PISTON) || s.is(Blocks.PISTON_HEAD);
    }

    static boolean terrain(BlockState s) {
        return s.is(TERRAIN) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND)
                || s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.SNOW) || s.is(BlockTags.ICE) || s.is(BlockTags.NYLIUM) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.END_STONE) || s.is(Blocks.SOUL_SOIL) || s.is(Blocks.SOUL_SAND) || s.is(Blocks.CLAY) || s.is(Blocks.MAGMA_BLOCK)
                || s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES) || s.is(BlockTags.GOLD_ORES)
                || s.is(BlockTags.REDSTONE_ORES) || s.is(BlockTags.LAPIS_ORES) || s.is(BlockTags.DIAMOND_ORES) || s.is(BlockTags.EMERALD_ORES);
    }

    private static boolean mayBreak(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state) {
        return level.mayInteract(p, pos) && !p.blockActionRestricted(level, pos, p.gameMode.getGameModeForPlayer())
                && !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, p)).isCanceled();
    }

    @Override
    public Selection select(ServerPlayer p, BlockPos origin, int maxBlocks, int maxSpan) {
        ServerLevel level = p.serverLevel();
        if (origin == null || maxBlocks <= 0 || maxSpan <= 0 || !level.isLoaded(origin)) return Selection.refuse("no_target");
        if (Sable.HELPER.getContaining(level, origin) != null) return Selection.refuse("in_structure");
        BlockState o = level.getBlockState(origin);
        if (forbidden(o)) return Selection.refuse("forbidden");
        if (terrain(o)) return Selection.refuse("terrain");
        var result = SubLevelAssemblyHelper.gatherConnectedBlocks(origin, level, maxBlocks,
                (originPos, originState, pos, state, from) -> level.isLoaded(pos) && !forbidden(state) && !terrain(state)
                        && Math.abs(pos.getX() - origin.getX()) < maxSpan && Math.abs(pos.getY() - origin.getY()) < maxSpan && Math.abs(pos.getZ() - origin.getZ()) < maxSpan);
        if (result.assemblyState() == SubLevelAssemblyHelper.GatherResult.State.TOO_MANY_BLOCKS) return Selection.refuse("too_large");
        if (result.assemblyState() != SubLevelAssemblyHelper.GatherResult.State.SUCCESS || result.blocks() == null || result.boundingBox() == null) return Selection.refuse("no_target");
        BoundingBox3i b = result.boundingBox();
        int span = Math.max(b.maxX - b.minX, Math.max(b.maxY - b.minY, b.maxZ - b.minZ)) + 1;
        if (!ConstructRules.withinBudget(result.blocks().size(), span, 5) || span > maxSpan || result.blocks().size() > maxBlocks) return Selection.refuse("too_large");
        double cx = 0, cy = 0, cz = 0;
        for (BlockPos pos : result.blocks()) {
            if (!mayBreak(p, level, pos, level.getBlockState(pos))) return Selection.refuse("protected");
            cx += pos.getX() + .5; cy += pos.getY() + .5; cz += pos.getZ() + .5;
        }
        int n = result.blocks().size();
        return new Selection(true, "", origin.immutable(), n, span, new double[] {b.minX, b.minY, b.minZ}, new double[] {b.maxX + 1, b.maxY + 1, b.maxZ + 1}, new double[] {cx / n, cy / n, cz / n});
    }

    @Override
    public Grab grab(ServerPlayer p, Selection selection, int maxBlocks, int maxSpan) {
        if (selection == null || !selection.ok()) return Grab.refuse("no_target");
        ServerLevel level = p.serverLevel();
        // Commit re-validates from scratch: anything that changed since the preview (blocks, rights) must be confirmed again.
        Selection now = select(p, selection.origin(), maxBlocks, maxSpan);
        if (!now.ok()) return Grab.refuse(now.reason());
        if (now.blocks() != selection.blocks() || now.span() != selection.span()) return Grab.refuse("changed");
        var gathered = SubLevelAssemblyHelper.gatherConnectedBlocks(selection.origin(), level, maxBlocks,
                (originPos, originState, pos, state, from) -> level.isLoaded(pos) && !forbidden(state) && !terrain(state)
                        && Math.abs(pos.getX() - selection.origin().getX()) < maxSpan && Math.abs(pos.getY() - selection.origin().getY()) < maxSpan
                        && Math.abs(pos.getZ() - selection.origin().getZ()) < maxSpan);
        if (gathered.blocks() == null || gathered.boundingBox() == null || gathered.blocks().size() != selection.blocks()) return Grab.refuse("changed");
        List<BlockPos> blocks = new ArrayList<>(gathered.blocks());
        BoundingBox3i bounds = new BoundingBox3i(gathered.boundingBox()).expand(1, 1, 1);
        ServerSubLevel sub = SubLevelAssemblyHelper.assembleBlocks(level, selection.origin(), blocks, bounds);
        BlockPos plotAnchor = sub.getPlot().getCenterBlock();
        List<BlockPos> plot = new ArrayList<>(blocks.size());
        for (BlockPos b : blocks) plot.add(b.subtract(selection.origin()).offset(plotAnchor));
        CompoundTag tag = sub.getUserDataTag() == null ? new CompoundTag() : sub.getUserDataTag().copy();
        tag.putUUID(OWNER_KEY, p.getUUID());
        sub.setUserDataTag(tag);
        HELD.put(sub.getUniqueId(), new Held(List.copyOf(plot)));
        double mass = sub.getMassTracker().getMass();
        if (!(mass > 0) || !Double.isFinite(mass)) {
            String back = land(p, sub.getUniqueId());
            return Grab.refuse(back == null ? "massless" : "massless_stuck");
        }
        return new Grab(true, "", sub.getUniqueId(), blocks.size(), selection.span(), mass);
    }

    private static ServerSubLevel find(ServerLevel level, UUID id) {
        if (level == null || id == null) return null;
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        SubLevel sub = container.getSubLevel(id);
        return sub instanceof ServerSubLevel s && !s.isRemoved() ? s : null;
    }

    @Override
    public UUID ownedBodyAt(ServerPlayer p, BlockPos pos) {
        SubLevel sub = Sable.HELPER.getContaining(p.serverLevel(), pos);
        if (!(sub instanceof ServerSubLevel s) || s.isRemoved()) return null;
        CompoundTag tag = s.getUserDataTag();
        if (tag == null || !tag.hasUUID(OWNER_KEY)) return null;
        boolean owner = tag.getUUID(OWNER_KEY).equals(p.getUUID()) || p.hasPermissions(2);
        if (!owner) return null;
        if (!HELD.containsKey(s.getUniqueId())) HELD.put(s.getUniqueId(), new Held(scanPlot(s)));
        return s.getUniqueId();
    }

    /** Recovers the block list of a body this server did not assemble in the current session (bounded scan of its plot bounds). */
    private static List<BlockPos> scanPlot(ServerSubLevel s) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos c = s.getPlot().getCenterBlock();
        int r = ConstructRules.ABS_MAX_SPAN + 1;
        ServerLevel level = s.getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r)))
            if (!level.getBlockState(pos).isAir() && Sable.HELPER.getContaining(level, pos) == s && out.size() < ConstructRules.ABS_MAX_BLOCKS) out.add(pos.immutable());
        return out;
    }

    // ---- body state and control --------------------------------------------------------------------------------------------------

    private static Vector3d worldCenter(ServerSubLevel s) {
        Pose3dc pose = s.logicalPose();
        var com = s.getMassTracker().getCenterOfMass();
        Vector3d local = com == null ? new Vector3d(pose.rotationPoint()) : new Vector3d(com);
        return pose.transformPosition(local, new Vector3d());
    }

    @Override
    public Body body(ServerLevel level, UUID id) {
        ServerSubLevel s = find(level, id);
        if (s == null) return null;
        RigidBodyHandle h = RigidBodyHandle.of(s);
        Vector3d c = worldCenter(s), v = h == null ? new Vector3d() : h.getLinearVelocity(new Vector3d());
        BoundingBox3dc b = s.boundingBox();
        Quaterniond q = new Quaterniond(s.logicalPose().orientation());
        Vector3d up = q.transform(new Vector3d(0, 1, 0)), x = q.transform(new Vector3d(1, 0, 0));
        double tilt = Math.acos(Math.max(-1, Math.min(1, up.y)));
        double yaw = Math.atan2(-x.z, x.x);
        return new Body(new double[] {c.x, c.y, c.z}, new double[] {v.x, v.y, v.z}, new double[] {b.minX(), b.minY(), b.minZ()},
                new double[] {b.maxX(), b.maxY(), b.maxZ()}, s.getMassTracker().getMass(), tilt, yaw);
    }

    @Override
    public void control(ServerLevel level, UUID body, double[] target, double yaw, double maxAccel) {
        if (body == null || target == null || target.length < 3) return;
        CONTROLS.put(body, new Control(level, target.clone(), yaw, maxAccel));
    }

    @Override public double effort(UUID body) { return body == null ? 0 : EFFORT.getOrDefault(body, 0d); }

    @Override public void release(UUID body) { if (body != null) { CONTROLS.remove(body); EFFORT.remove(body); } }

    @Override
    public net.minecraft.world.phys.Vec3 throwAim(ServerPlayer player, UUID ignoredBody, double range) {
        var from = player.getEyePosition();
        var to = from.add(player.getLookAngle().scale(range));
        var context = new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player);
        ((dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension) (Object) context)
                .sable$setIgnoredSubLevel(find(player.serverLevel(), ignoredBody));
        var hit = player.serverLevel().clip(context);
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return to;
        var point = hit.getLocation();
        // Sable clip returns a sublevel hit in its storage plot coordinates, not world coordinates.
        var sublevel = Sable.HELPER.getContaining(player.serverLevel(), hit.getBlockPos());
        if (sublevel == null) return point;
        var world = sublevel.logicalPose().transformPosition(new Vector3d(point.x, point.y, point.z));
        return new net.minecraft.world.phys.Vec3(world.x, world.y, world.z);
    }

    /** Runs on every Sable physics step: spring the held body to its target, cancel gravity, keep it upright and turned to yaw. */
    private static void physicsTick(dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem system, double dt) {
        if (CONTROLS.isEmpty()) return;
        ServerLevel level = system.getLevel();
        for (var e : CONTROLS.entrySet()) {
            Control c = e.getValue();
            if (c.level != level) continue;
            ServerSubLevel s = find(level, e.getKey());
            if (s == null) { CONTROLS.remove(e.getKey()); continue; }
            RigidBodyHandle h = system.getPhysicsHandle(s);
            if (h == null || !h.isValid()) continue;
            double mass = s.getMassTracker().getMass();
            if (!(mass > 0)) continue;
            Pose3dc pose = s.logicalPose();
            Vector3d center = worldCenter(s), vel = h.getLinearVelocity(new Vector3d()), w = h.getAngularVelocity(new Vector3d());
            Vector3d g = DimensionPhysicsData.getGravity(level);
            double[] j = ConstructRules.hoverImpulse(new V3(center.x, center.y, center.z), new V3(vel.x, vel.y, vel.z),
                    new V3(c.target[0], c.target[1], c.target[2]), new V3(g.x, g.y, g.z), mass, dt, c.maxAccel);
            latticeHold(h, pose, new Vector3d(pose.position()), center, vel, c.target, j, mass, dt);
            EFFORT.put(e.getKey(), j[3]);
            farDiagnostic(level, e.getKey(), c, center, vel, j, mass, dt);
            Quaterniond q = new Quaterniond(pose.orientation());
            Vector3d up = q.transform(new Vector3d(0, 1, 0)), x = q.transform(new Vector3d(1, 0, 0));
            double tilt = Math.acos(Math.max(-1, Math.min(1, up.y)));
            Vector3d axis = new Vector3d(up).cross(0, 1, 0, new Vector3d()).negate();
            double yaw = Math.atan2(-x.z, x.x);
            Matrix3dc inertia = s.getMassTracker().getInertiaTensor();
            double inv = inertia == null ? mass : Math.max(1e-6, (inertia.m00() + inertia.m11() + inertia.m22()) / 3);
            V3 ang = ConstructRules.uprightImpulse(new V3(w.x, w.y, w.z), new V3(axis.x, axis.y, axis.z), tilt, -ConstructRules.wrap(c.yaw - yaw), inv, dt);
            // Sable's impulses are expressed in the body frame.
            Vector3d linearLocal = pose.transformNormalInverse(new Vector3d(j[0], j[1], j[2]));
            Vector3d angularLocal = pose.transformNormalInverse(new Vector3d(ang.x(), ang.y(), ang.z()));
            h.applyLinearAndAngularImpulse(linearLocal, angularLocal, true);
        }
    }

    /** Lattice steps at or below this size are resolved by the solver, so the classical spring is left untouched. */
    private static final double LATTICE_FLOOR = 1e-3;
    /** Most lattice steps corrected per physics tick, so a distant hold point is approached instead of snapped. */
    private static final double LATTICE_MAX_STEPS = 2;

    /**
     * Keeps the hold reachable where the solver's single-precision world position cannot express the spring's motion.
     * Sable hands the world pose straight to rapier/parry, which are compiled in {@code f32}, so the representable step
     * grows with the coordinate: 0.25 blocks at |x| = 2.7e6, 0.5 at |z| = 5.3e6 and 1.0 past 8.4e6. A sub-step
     * displacement below half a step is discarded, so the spring commands a velocity the solver never turns into motion
     * and the body stays pinned to a lattice point: measured at z = -5.25e6, a frozen 1.2976-block error with 1.8 m/s of
     * commanded z velocity, while the fine y axis (step 3.8e-6) converged normally. On an axis where the spring's own
     * displacement cannot cross half a lattice step, and the achievable centre is further than half a step from the
     * target, the pose is therefore placed directly on the nearest representable point the body can occupy (at most
     * {@link #LATTICE_MAX_STEPS} steps per tick) and the leftover velocity is cancelled. That lands within half a step
     * of the target by construction. No assertion, threshold, site or near-origin behaviour changes: where the step is
     * fine the guard never fires, the impulse is untouched and {@code effort} keeps the spring's value, so upkeep cost
     * is unchanged.
     */
    private static void latticeHold(RigidBodyHandle handle, Pose3dc pose, Vector3d translation, Vector3d center, Vector3d velocity, double[] target, double[] impulse, double mass, double dt) {
        double[] t = {translation.x, translation.y, translation.z}, c = {center.x, center.y, center.z}, v = {velocity.x, velocity.y, velocity.z};
        double[] want = t.clone();
        boolean moved = false;
        for (int axis = 0; axis < 3; axis++) {
            double step = Math.ulp((float) t[axis]);
            if (!(step > LATTICE_FLOOR) || !Double.isFinite(step)) continue;
            if (Math.abs(impulse[axis] / mass * dt) >= step * .5) continue;
            double d = target[axis] - c[axis];
            double steps = Math.round(d / step);
            if (steps == 0) continue;
            want[axis] = t[axis] + Math.copySign(Math.min(Math.abs(steps), LATTICE_MAX_STEPS), steps) * step;
            impulse[axis] = -mass * v[axis];
            moved = true;
        }
        if (moved) {
            if (FAR_DIAGNOSTIC && FAR_DIAGNOSTIC_LINES.get() < FAR_DIAGNOSTIC_LIMIT) FAR_LOG.info("CONSTRUCT_FAR_PROBE_LATTICE from=({},{},{}) to=({},{},{}) step=({},{},{})",
                    t[0], t[1], t[2], want[0], want[1], want[2], Math.ulp((float) t[0]), Math.ulp((float) t[1]), Math.ulp((float) t[2]));
            handle.teleport(new Vector3d(want[0], want[1], want[2]), new Quaterniond(pose.orientation()));
        }
    }

    /** Read-only far-coordinate witness; disabled and allocation-free unless the property is set. Never mutates control state. */
    private static void farDiagnostic(ServerLevel level, UUID body, Control control, Vector3d center, Vector3d velocity, double[] impulse, double mass, double dt) {
        if (!FAR_DIAGNOSTIC) return;
        int line = FAR_DIAGNOSTIC_LINES.getAndIncrement();
        if (line >= FAR_DIAGNOSTIC_LIMIT) return;
        double ex = control.target[0] - center.x, ey = control.target[1] - center.y, ez = control.target[2] - center.z;
        FAR_LOG.info("CONSTRUCT_FAR_PROBE n={} tick={} body={} dt={} center=({},{},{}) target=({},{},{}) err=({},{},{}) absErr={} vel=({},{},{}) impulse=({},{},{}) mass={} effort={} ulpF32=({},{},{}) f32Exact=({},{},{})",
                line, level.getGameTime(), body, dt,
                center.x, center.y, center.z, control.target[0], control.target[1], control.target[2], ex, ey, ez,
                Math.sqrt(ex * ex + ey * ey + ez * ez), velocity.x, velocity.y, velocity.z,
                impulse[0], impulse[1], impulse[2], mass, impulse[3],
                Math.ulp((float) center.x), Math.ulp((float) center.y), Math.ulp((float) center.z),
                (double) (float) center.x == center.x, (double) (float) center.y == center.y, (double) (float) center.z == center.z);
    }

    @Override
    public boolean launch(ServerLevel level, UUID id, double[] look, double speed) {
        ServerSubLevel s = find(level, id);
        if (s == null || look == null) return false;
        release(id);
        RigidBodyHandle h = RigidBodyHandle.of(s);
        if (h == null || !h.isValid()) return false;
        Vector3d v = h.getLinearVelocity(new Vector3d());
        V3 j = ConstructRules.throwImpulse(new V3(v.x, v.y, v.z), new V3(look[0], look[1], look[2]), speed, s.getMassTracker().getMass());
        if (j.length() == 0) return false;
        h.applyLinearAndAngularImpulse(s.logicalPose().transformNormalInverse(new Vector3d(j.x(), j.y(), j.z())), new Vector3d(), true);
        return true;
    }

    // ---- landing --------------------------------------------------------------------------------------------------------------

    private static Rotation rotation(int quarter) {
        return switch (Math.floorMod(quarter, 4)) { case 1 -> Rotation.COUNTERCLOCKWISE_90; case 2 -> Rotation.CLOCKWISE_180; case 3 -> Rotation.CLOCKWISE_90; default -> Rotation.NONE; };
    }

    @Override
    public String land(ServerPlayer p, UUID id) {
        ServerLevel level = p.serverLevel();
        ServerSubLevel s = find(level, id);
        if (s == null) return "lost";
        Held held = HELD.get(id);
        List<BlockPos> plot = new ArrayList<>();
        for (BlockPos b : held == null ? scanPlot(s) : held.plotBlocks()) if (!level.getBlockState(b).isAir()) plot.add(b);
        if (plot.isEmpty()) return "lost";
        Body body = body(level, id);
        if (body == null || !ConstructRules.landable(body.tilt())) return "tilted";
        int quarter = ConstructRules.quarterTurns(body.yaw());
        BlockPos plotAnchor = s.getPlot().getCenterBlock();
        var anchorWorld = s.logicalPose().transformPosition(plotAnchor.getCenter());
        BlockPos target = BlockPos.containing(anchorWorld);
        var transform = new SubLevelAssemblyHelper.AssemblyTransform(plotAnchor, target, quarter, rotation(quarter), level);
        Set<BlockPos> seen = new java.util.HashSet<>();
        for (BlockPos b : plot) {
            BlockPos t = transform.apply(b);
            if (!seen.add(t)) return "blocked";
            if (!level.isLoaded(t) || level.isOutsideBuildHeight(t)) return "unloaded";
            BlockState there = level.getBlockState(t);
            if (!there.isAir() && !there.canBeReplaced() || Sable.HELPER.getContaining(level, t) != null) return "blocked";
            if (!level.mayInteract(p, t) || p.blockActionRestricted(level, t, p.gameMode.getGameModeForPlayer())
                    || EventHooks.onBlockPlace(p, BlockSnapshot.create(level.dimension(), level, t), Direction.UP)) return "protected";
        }
        release(id);
        SubLevelAssemblyHelper.moveBlocks(level, transform, plot);
        HELD.remove(id);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null && !s.isRemoved()) container.removeSubLevel(s, SubLevelRemovalReason.REMOVED);
        return null;
    }

    @Override
    public List<BlockState> palette(ServerLevel level, UUID id, int max) {
        ServerSubLevel s = find(level, id);
        if (s == null || max <= 0) return List.of();
        Held held = HELD.get(id);
        List<BlockState> out = new ArrayList<>();
        for (BlockPos b : held == null ? scanPlot(s) : held.plotBlocks()) {
            BlockState state = level.getBlockState(b);
            if (!state.isAir()) out.add(state);
        }
        java.util.Collections.shuffle(out, new java.util.Random(id.getLeastSignificantBits()));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    @Override
    public boolean shatter(ServerLevel level, UUID id) {
        ServerSubLevel s = find(level, id);
        if (s == null) return false;
        release(id);
        HELD.remove(id);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || s.isRemoved()) return false;
        container.removeSubLevel(s, SubLevelRemovalReason.REMOVED);
        return true;
    }

    @Override
    public List<LivingEntity> touching(ServerLevel level, UUID id, double inflate) {
        ServerSubLevel s = find(level, id);
        if (s == null) return List.of();
        BoundingBox3dc b = s.boundingBox();
        AABB box = new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()).inflate(Math.max(0, Math.min(2, inflate)));
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator());
    }
}
