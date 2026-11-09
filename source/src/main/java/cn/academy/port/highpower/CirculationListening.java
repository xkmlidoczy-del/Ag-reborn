package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.CirculationListeningRules;
import cn.academy.port.highpower.core.VectorParryRules;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW433: paid local air-disturbance listening. No combat, tracking, protection or XP writer. */
@EventBusSubscriber(modid = "academy")
public final class CirculationListening {
    public static final String ID = "circulation_listening", PAYMENT_KEY = "academy:circulation_listening_payment";
    public static final int ACTIVE = 0, PAUSED = 1, ENDED = 2, KIND_ARROW = 0, KIND_RUNNER = 1;
    private static final String CLOCK = "next_" + ID;
    private static final int MAX_INPUTS = 4096;
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<MinecraftServer, PhysicalTick> PHYSICAL = new WeakHashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    public static Consumer<Rejection> clientRejectionReceiver = ignored -> {};
    private record Watermark(ServerPlayer owner, long epoch, long input) {}
    /** Independent remaining native-player-tick debt; never an absolute world-clock deadline. */
    private record Payment(int remaining, int restart, long receipt, String dimension, long resourceUntil) {}
    private static final class PhysicalTick {
        final int tick;
        final Set<UUID> players = new HashSet<>();
        final Map<UUID, Integer> paymentStarts = new HashMap<>();
        PhysicalTick(int tick) { this.tick = tick; }
    }
    private record Observation(Entity entity, int entityId, int nativeTick, AABB body, Pose pose, CirculationListeningRules.Sample sample,
                               CirculationListeningMotionProof.Snapshot motion, List<AirView> paths) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final int preset, cooldown;
        final long revision, started;
        final double activation;
        Map<UUID, Observation> samples = new HashMap<>();
        long paymentReceipt, lastTick = -1, lastScan = -1, sequence;
        boolean paying, scanning;
        Visual lastVisual;
        Context(ServerPlayer p, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            started = now(p);
            BigDecimal growth = HighPowerGrowth.values(p).position();
            activation = VectorParryRules.activationCost(growth); cooldown = VectorParryRules.cooldownTicks(growth);
        }
    }
    private CirculationListening() {}

    /** Each fresh authenticated press toggles; physical key-up never ends the toggle. */
    public static boolean toggle(ServerPlayer p, String wire) {
        HighPowerInput.Input input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Watermark previous = INPUTS.get(p.getUUID());
        if (previous != null && previous.owner == p && previous.epoch == input.epoch() && previous.input >= input.input()) return false;
        if (previous == null && INPUTS.size() >= MAX_INPUTS) return reject(p, input);
        watermark(p, input);
        Context resolving = owned(p);
        if (AbilityConsumption.busy(p) || resolving != null && busy(resolving)) return reject(p, input);
        if (!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))) return reject(p, input);
        Context old = CONTEXTS.get(p.getUUID());
        if (old != null) {
            boolean same = owned(p) == old && structurallyValid(old);
            finish(old); if (same) return true;
        }
        if (!CirculationListeningMotionProof.available() || HighPowerCombat.unsupportedReason(p).isPresent()) return reject(p, input, "unsupported");
        if (!canAct(p)) return reject(p, input, "not_ready");
        Payment fence = readPayment(p);
        if (CONTEXTS.size() >= MAX_INPUTS || nextToken == Long.MAX_VALUE || fence == null || fence.restart > 0
                || !AbilityStorage.get(p).canUse(ID)) return reject(p, input, "not_ready");
        Context c = new Context(p, input); CONTEXTS.put(p.getUUID(), c);
        if (!pay(c, true) || !valid(c)) { finish(c); feedback(p, "not_ready"); return false; }
        // Activation establishes only an empty owner heartbeat; no spawn/first-frame cue.
        send(c, submerged(p) ? PAUSED : ACTIVE, List.of()); return true;
    }
    public static boolean start(ServerPlayer p, String wire) { return toggle(p, wire); }
    public static boolean release(ServerPlayer p, String wire) {
        HighPowerInput.Input input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Context c = owned(p); return c != null && c.input.equals(input);
    }
    public static boolean abort(ServerPlayer p, String wire) {
        Context c = owned(p); if (c != null && busy(c)) return false;
        HighPowerInput.Input input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        watermark(p, input); c = owned(p);
        if (c == null || c.input.epoch() != input.epoch() || c.input.slot() != input.slot() || c.input.input() > input.input()) return false;
        finish(c); return true;
    }
    private static void watermark(ServerPlayer p, HighPowerInput.Input input) {
        Watermark old = INPUTS.get(p.getUUID());
        if (old == null && INPUTS.size() >= MAX_INPUTS) return;
        if (old == null || old.owner != p || old.epoch != input.epoch() || old.input < input.input())
            INPUTS.put(p.getUUID(), new Watermark(p, input.epoch(), input.input()));
    }
    public static void abort(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c); }
    public static void remove(ServerPlayer p) {
        if (!thread(p)) return;
        abort(p); Watermark mark = INPUTS.get(p.getUUID());
        if (mark != null && mark.owner == p) INPUTS.remove(p.getUUID(), mark);
    }
    public static boolean active(ServerPlayer p) {
        Context c = owned(p); return c != null && (busy(c) && snapshotOwned(c) || valid(c));
    }
    public static boolean holding(ServerPlayer p) { return active(p); }
    public static boolean renewalPending(ServerPlayer p) {
        Context c = owned(p); Payment payment = c == null ? null : readPayment(p);
        return c != null && payment != null && c.paymentReceipt > 0 && payment.receipt == c.paymentReceipt
                && payment.remaining == 0 && structurallyValid(c);
    }
    /** Diagnostic session estimate; persisted remaining native ticks are payment authority. */
    public static long paidUntil(ServerPlayer p) {
        Context c = owned(p); Payment payment = c == null ? null : readPayment(p);
        return c == null || payment == null || payment.receipt != c.paymentReceipt ? 0 : now(p) + payment.remaining;
    }
    public static Visual lastVisual(ServerPlayer p) { Context c = owned(p); return c == null ? null : c.lastVisual; }
    public static int sampledCount(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.samples.size(); }
    private static boolean busy(Context c) { return c.paying || c.scanning; }

    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null || busy(c)) return;
        if (!CirculationListeningMotionProof.available() || !structurallyValid(c) || !canAct(p)) { finish(c); return; }
        long tick = now(p); if (tick <= c.lastTick) return; c.lastTick = tick;
        Payment payment = readPayment(p);
        if (payment == null || payment.receipt != c.paymentReceipt || payment.remaining == 0 && !pay(c, false)) { finish(c); return; }
        if (!valid(c)) { finish(c); return; }
        if (submerged(p)) {
            clearSamples(c); c.lastScan = -1; send(c, PAUSED, List.of()); return;
        }
        if (c.lastScan >= 0 && tick - c.lastScan < CirculationListeningRules.SAMPLE_INTERVAL_TICKS) return;
        c.lastScan = tick; c.scanning = true;
        try { scan(c, tick); }
        catch (RuntimeException | LinkageError refusal) { clearSamples(c); if (owned(p) == c) send(c, PAUSED, List.of()); }
        finally { c.scanning = false; }
    }
    /** One bounded local query; every non-owner raw result counts, including unreviewed entities. */
    private static void scan(Context c, long tick) {
        ServerPlayer p = c.identity.owner(); ServerLevel world = c.identity.level();
        Vec3 ownerPosition = p.position(), eye = p.getEyePosition();
        CompoundTag budget = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
        CompoundTag budgetSnapshot = budget.copy();
        Tag paymentSnapshot = p.getPersistentData().get(PAYMENT_KEY).copy();
        AABB box = new AABB(ownerPosition, ownerPosition).inflate(CirculationListeningRules.RANGE);
        if (!loadedQuery(world, box)) { pause(c); return; }
        List<Entity> raw = new ArrayList<>(CirculationListeningRules.QUERY_LIMIT + 1);
        world.getEntities(EntityTypeTest.forClass(Entity.class), box, entity -> entity != p, raw, CirculationListeningRules.QUERY_LIMIT + 1);
        if (raw.size() > CirculationListeningRules.QUERY_LIMIT) { pause(c); return; }
        Map<UUID, Observation> next = new HashMap<>();
        List<CirculationListeningRules.Track> tracks = new ArrayList<>();
        Set<Entity> watched = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            for (Entity entity : raw) {
                CirculationListeningRules.Kind kind = reviewed(p, entity); if (kind == null) continue;
                if (next.containsKey(entity.getUUID())) { pause(c); releaseObservations(c, next); return; }
                if (!CirculationListeningMotionProof.watch(entity, c)) { pause(c); releaseObservations(c, next); return; }
                watched.add(entity);
                CirculationListeningMotionProof.Snapshot motion = CirculationListeningMotionProof.snapshot(entity);
                if (motion == null) { CirculationListeningMotionProof.unwatch(entity, c); continue; }
                var sample = new CirculationListeningRules.Sample(entity.getUUID(), world, tick, kind,
                        point(entity.position()), point(entity.getDeltaMovement()), entity.isSprinting());
                Observation current = new Observation(entity, entity.getId(), entity.tickCount, entity.getBoundingBox(), entity.getPose(), sample, motion, new ArrayList<>());
                next.put(entity.getUUID(), current);
                if (!visibleNow(p, current, ownerPosition, eye)) {
                    next.remove(entity.getUUID()); CirculationListeningMotionProof.unwatch(entity, c); continue;
                }
                Observation old = c.samples.get(entity.getUUID());
                if (old == null || old.entity != entity || old.entityId != entity.getId()
                        || entity.tickCount - old.nativeTick != CirculationListeningRules.SAMPLE_INTERVAL_TICKS
                        || old.motion.discontinuity() != motion.discontinuity() || motion.physicalWrites() <= old.motion.physicalWrites()
                        || !CirculationListeningRules.continuous(old.sample, sample)) continue;
                // Unknown/unloaded paths and any native collider or fluid fully suppress this source.
                // No inferred attenuation, diffraction, hearing through walls, or client-side target scan.
                AirView physicalPath = airPath(p, vec(old.sample.position()), entity.position());
                if (physicalPath == null) continue;
                current.paths.add(physicalPath);
                tracks.add(new CirculationListeningRules.Track(old.sample, sample));
            }
            if (!valid(c) || !ownerPosition.equals(p.position()) || !eye.equals(p.getEyePosition())) { pause(c); releaseObservations(c, next); return; }
            // Recheck after every candidate's native shapes/permission observers have run.
            // Retain only observations still matching the exact position, velocity, sprint,
            // identity and motion seal. Unknown callback effects never create a stale cue.
            next.entrySet().removeIf(entry -> {
                if (visibleNow(p, entry.getValue(), ownerPosition, eye)) return false;
                CirculationListeningMotionProof.unwatch(entry.getValue().entity, c); return true;
            });
            if (!valid(c) || !canAct(p) || submerged(p) || !ownerPosition.equals(p.position()) || !eye.equals(p.getEyePosition())
                    || p.getPersistentData().getCompound(HighPowerResources.DATA_KEY) != budget || !budget.equals(budgetSnapshot)
                    || !Objects.equals(p.getPersistentData().get(PAYMENT_KEY), paymentSnapshot)) {
                pause(c); releaseObservations(c, next); return;
            }
            next.entrySet().removeIf(entry -> {
                if (unchanged(p, entry.getValue())) return false;
                CirculationListeningMotionProof.unwatch(entry.getValue().entity, c); return true;
            });
            tracks.removeIf(track -> !next.containsKey(track.current().identity()));
            var selection = CirculationListeningRules.collect(point(ownerPosition), tracks);
            if (selection.overloaded()) { pause(c); releaseObservations(c, next); return; }
            for (Observation old : c.samples.values()) {
                Observation replacement = next.get(old.sample.identity());
                if (replacement == null || replacement.entity != old.entity) CirculationListeningMotionProof.unwatch(old.entity, c);
            }
            c.samples = next;
            List<Cue> cues = selection.cues().stream().map(value -> new Cue(value.azimuth(), value.elevation(), value.strength(), value.kind())).toList();
            send(c, ACTIVE, cues);
        } catch (RuntimeException | LinkageError failure) { releaseObservations(c, next); throw failure; }
        finally {
            // Includes a watch that failed before its Observation could be materialized.
            for (Entity entity : watched) {
                Observation retained = c.samples.get(entity.getUUID());
                if (owned(p) != c || retained == null || retained.entity != entity) CirculationListeningMotionProof.unwatch(entity, c);
            }
        }
    }
    private static boolean visibleNow(ServerPlayer p, Observation observation, Vec3 ownerPosition, Vec3 eye) {
        if (observation == null) return false;
        Entity entity = observation.entity;
        if (reviewed(p, entity) != observation.sample.kind() || !entity.position().equals(vec(observation.sample.position()))
                || !CirculationListeningRules.withinRange(point(ownerPosition), point(entity.position()))) return false;
        Vec3 source = entity instanceof Arrow ? entity.position() : observation.body.getCenter();
        // Sight uses eye and body-center endpoints while admission uses feet. Preserve the four-metre
        // allowance between the original 20m detection sphere and 24m sight path limit.
        AirView sight = airPath(p, eye, source, CirculationListeningRules.RANGE + 4);
        if (sight == null) return false;
        observation.paths.add(sight);
        var motion = CirculationListeningMotionProof.snapshot(entity);
        return motion != null && motion.equals(observation.motion) && unchanged(p, observation)
                && ownerPosition.equals(p.position()) && eye.equals(p.getEyePosition());
    }
    /** Final read-only seal after all guarded native shape calls; no target coordinates leave the server. */
    private static boolean unchanged(ServerPlayer p, Observation observation) {
        if (observation == null) return false;
        Entity entity = observation.entity;
        return entity.getId() == observation.entityId && entity.getUUID().equals(observation.sample.identity())
                && entity.level() == observation.sample.world() && entity.tickCount == observation.nativeTick
                && entity.getBoundingBox().equals(observation.body) && entity.getPose() == observation.pose
                && entity.position().equals(vec(observation.sample.position())) && entity.getDeltaMovement().equals(vec(observation.sample.velocity()))
                && entity.isSprinting() == observation.sample.sprinting() && reviewed(p, entity) == observation.sample.kind()
                && observation.motion.equals(CirculationListeningMotionProof.snapshot(entity))
                && observation.paths.stream().allMatch(AirView::unchanged);
    }
    private static void pause(Context c) { clearSamples(c); send(c, PAUSED, List.of()); }
    private static void releaseObservations(Context c, Map<UUID, Observation> observations) {
        for (Observation observation : observations.values()) CirculationListeningMotionProof.unwatch(observation.entity, c);
    }
    private static void clearSamples(Context c) { releaseObservations(c, c.samples); c.samples.clear(); }

    private static CirculationListeningRules.Kind reviewed(ServerPlayer p, Entity entity) {
        if (entity == null || entity == p || entity.level() != p.level() || entity.isRemoved() || !entity.isAlive()
                || entity.isSpectator() || entity.isInvisible() || entity.isInvisibleTo(p) || entity.noPhysics || entity.isNoGravity()
                || entity.isPassenger() || entity.isVehicle() || entity.isInWaterOrBubble() || entity.isInLava()
                || entity.tickCount < 2 || !finite(entity.position()) || !finite(entity.getDeltaMovement())
                || p.serverLevel().getEntity(entity.getId()) != entity || p.serverLevel().getEntity(entity.getUUID()) != entity
                || !CirculationListeningRules.withinRange(point(p.position()), point(entity.position()))) return null;
        if (entity instanceof Arrow arrow) return plainArrow(arrow) ? CirculationListeningRules.Kind.ARROW : null;
        if (!(entity instanceof LivingEntity living) || !living.isSprinting() || living.isSleeping() || living.isFallFlying() || living.isSwimming()
                || !HighPowerCombat.supported(living) || living instanceof Player && living.getClass() != ServerPlayer.class) return null;
        if (living instanceof ServerPlayer player && (player.connection == null || player.hasDisconnected()
                || player.getAbilities().flying || player.getAbilities().instabuild || player.getAbilities().invulnerable)) return null;
        return CirculationListeningRules.Kind.RUNNER;
    }
    private static boolean plainArrow(Arrow arrow) {
        if (arrow.getClass() != Arrow.class || arrow.getType() != EntityType.ARROW || arrow.inGround || arrow.isNoPhysics()) return false;
        // Air disturbance depends on physical movement, not shooter, damage, critical flag,
        // fire or weapon enchantments. Only the ordinary vanilla arrow pickup is reviewed.
        ItemStack stack = arrow.getPickupItemStackOrigin();
        return stack.getCount() == 1 && ItemStack.isSameItemSameComponents(stack, new ItemStack(Items.ARROW));
    }
    private static boolean loadedQuery(ServerLevel world, AABB box) {
        if (!finite(new Vec3(box.minX, box.minY, box.minZ)) || !finite(new Vec3(box.maxX, box.maxY, box.maxZ))
                || !world.getWorldBorder().isWithinBounds(box)) return false;
        for (int x = ((int)Math.floor(box.minX)) >> 4; x <= ((int)Math.floor(box.maxX)) >> 4; x++)
            for (int z = ((int)Math.floor(box.minZ)) >> 4; z <= ((int)Math.floor(box.maxZ)) >> 4; z++)
                if (world.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
    private static AirView airPath(ServerPlayer p, Vec3 from, Vec3 to) {
        return airPath(p, from, to, 24);
    }
    private static AirView airPath(ServerPlayer p, Vec3 from, Vec3 to, double limit) {
        if (!finite(from) || !finite(to) || from.distanceToSqr(to) > limit * limit) return null;
        try { AirView view = new AirView(p, from, to); return view.clear(from, to) ? view : null; }
        catch (RuntimeException | LinkageError refused) { return null; }
    }
    private static final class AirRefusal extends RuntimeException { AirRefusal() { super(null, null, false, false); } }
    /** Native clip with only already loaded chunks, local bounded neighbor reads and shapes. */
    private static final class AirView implements BlockGetter {
        final ServerPlayer player; final ServerLevel world; final AABB scope; int reads, boxes;
        final Map<BlockPos, Read> observed = new LinkedHashMap<>();
        private record Read(BlockPos position, LevelChunk chunk, BlockState block, FluidState fluid) {}
        AirView(ServerPlayer p, Vec3 from, Vec3 to) { player = p; world = p.serverLevel(); scope = new AABB(from, to).inflate(1.001); }
        boolean clear(Vec3 from, Vec3 to) {
            BlockHitResult hit = clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player) {
                @Override public VoxelShape getBlockShape(BlockState state, BlockGetter blocks, BlockPos pos) {
                    VoxelShape shape = super.getBlockShape(state, blocks, pos); AirView.this.shape(shape); return shape;
                }
                @Override public VoxelShape getFluidShape(FluidState state, BlockGetter blocks, BlockPos pos) {
                    VoxelShape shape = super.getFluidShape(state, blocks, pos); AirView.this.shape(shape); return shape;
                }
            });
            return player.serverLevel() == world && !player.isRemoved() && hit.getType() == HitResult.Type.MISS;
        }
        LevelChunk chunk(BlockPos pos) {
            if (++reads > 768 || player.serverLevel() != world || player.isRemoved() || !scope.intersects(new AABB(pos))
                    || world.isOutsideBuildHeight(pos) || !world.getWorldBorder().isWithinBounds(pos)) throw new AirRefusal();
            LevelChunk chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null || !world.mayInteract(player, pos)) throw new AirRefusal();
            return chunk;
        }
        private Read read(BlockPos pos) {
            LevelChunk chunk = chunk(pos); BlockPos immutable = pos.immutable();
            Read read = new Read(immutable, chunk, chunk.getBlockState(pos), chunk.getFluidState(pos));
            Read old = observed.putIfAbsent(immutable, read);
            if (old != null && (old.chunk != chunk || old.block != read.block || old.fluid != read.fluid)) throw new AirRefusal();
            return read;
        }
        @Override public BlockState getBlockState(BlockPos pos) { return read(pos).block; }
        @Override public FluidState getFluidState(BlockPos pos) { return read(pos).fluid; }
        @Override public BlockEntity getBlockEntity(BlockPos pos) {
            // Mutable block-entity-dependent geometry has no reviewed immutable receipt.
            throw new AirRefusal();
        }
        /** Callback-free seal of every native collision/fluid/neighbor read, after all rays. */
        boolean unchanged() {
            if (player.serverLevel() != world || player.isRemoved()) return false;
            for (Read read : observed.values()) {
                BlockPos pos = read.position;
                if (world.isOutsideBuildHeight(pos) || !world.getWorldBorder().isWithinBounds(pos) || !world.mayInteract(player, pos)
                        || world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != read.chunk
                        || read.chunk.getBlockState(pos) != read.block || read.chunk.getFluidState(pos) != read.fluid) return false;
            }
            return true;
        }
        @Override public int getHeight() { return world.getHeight(); }
        @Override public int getMinBuildHeight() { return world.getMinBuildHeight(); }
        @Override public BlockHitResult clipWithInteractionOverride(Vec3 from, Vec3 to, BlockPos pos, VoxelShape shape, BlockState state) {
            return shape.clip(from, to, pos);
        }
        void shape(VoxelShape shape) {
            long cells = 1;
            for (Direction.Axis axis : Direction.Axis.values()) {
                var coords = shape.getCoords(axis);
                if (coords.size() > 34) throw new AirRefusal();
                for (double value : coords) if (!Double.isFinite(value)) throw new AirRefusal();
                cells *= Math.max(1, coords.size() - 1);
            }
            if (cells > 4096) throw new AirRefusal();
            shape.forAllBoxes((a, b, c, d, e, f) -> {
                if (++boxes > 2048 || !Double.isFinite(a) || !Double.isFinite(b) || !Double.isFinite(c)
                        || !Double.isFinite(d) || !Double.isFinite(e) || !Double.isFinite(f)) throw new AirRefusal();
            });
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !thread(p) || p.connection == null || p.hasDisconnected() || p.isRemoved()
                || p.serverLevel().getEntity(p.getUUID()) != p) return;
        PhysicalTick clock = physicalTick(p);
        if (!clock.players.add(p.getUUID())) return;
        if (p.getPersistentData().contains(PAYMENT_KEY)) {
            Payment payment = readPayment(p);
            if (payment != null && (payment.remaining > 0 || payment.restart > 0)) {
                int started = clock.paymentStarts.getOrDefault(p.getUUID(), 0);
                writePayment(p, new Payment((started & 1) != 0 ? payment.remaining : Math.max(0, payment.remaining - 1),
                        (started & 2) != 0 ? payment.restart : Math.max(0, payment.restart - 1),
                        payment.receipt, payment.dimension, payment.resourceUntil));
            }
        }
    }
    private static PhysicalTick physicalTick(ServerPlayer p) {
        PhysicalTick clock = PHYSICAL.get(p.server);
        if (clock == null || clock.tick != p.server.getTickCount()) {
            clock = new PhysicalTick(p.server.getTickCount()); PHYSICAL.put(p.server, clock);
        }
        return clock;
    }
    private static boolean pay(Context c, boolean activation) {
        ServerPlayer p = c.identity.owner(); Payment previous = readPayment(p);
        HighPowerGrowth.Values growth = HighPowerGrowth.values(p);
        double cost = activation ? c.activation : VectorParryRules.sustainCost(growth.position());
        if (c.paying || !structurallyValid(c) || previous == null || previous.receipt == Long.MAX_VALUE
                || !activation && (previous.remaining > 0 || previous.receipt != c.paymentReceipt)
                || previous.restart > 0 || HighPowerResources.availableCp(p) < cost) return false;
        long worldTick = p.serverLevel().getGameTime();
        if (!VectorParryRules.clock(worldTick) || !resourceClockMatches(p, previous.resourceUntil)) return false;
        c.paying = true;
        CompoundTag budget = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
        Vec3 ownerPosition = p.position();
        double cpBefore = HighPowerResources.cp(p);
        CompoundTag refusedBudget = budget.copy(); refusedBudget.putLong(CLOCK, worldTick);
        CompoundTag paidBudget = budget.copy(); paidBudget.putLong(CLOCK, worldTick + 1); paidBudget.putDouble("cp", cpBefore - cost);
        Tag oldClock = budget.contains(CLOCK) ? budget.get(CLOCK).copy() : null;
        Tag oldPayment = p.getPersistentData().contains(PAYMENT_KEY) ? p.getPersistentData().get(PAYMENT_KEY).copy() : null;
        boolean spent = false;
        try {
            // This controller's physical debt is the cadence authority. Translate only
            // its exact certified one-tick CP writer clock, including a frozen world or
            // restarted clock origin; never touch another skill's or a global CP clock.
            budget.putLong(CLOCK, worldTick);
            if (!HighPowerResources.trySpend(p, ID, cost, 1)) return false;
            spent = true;
            if (owned(p) != c || !snapshotOwned(c) || !ownerPosition.equals(p.position()) || !growth.equals(HighPowerGrowth.values(p))
                    || !Objects.equals(p.getPersistentData().get(PAYMENT_KEY), oldPayment)
                    || p.getPersistentData().getCompound(HighPowerResources.DATA_KEY) != budget
                    || !budget.equals(paidBudget) || !budget.contains(CLOCK, Tag.TAG_LONG) || budget.getLong(CLOCK) != worldTick + 1) return false;
            long receipt = previous.receipt + 1;
            int restart = activation ? VectorParryRules.RESTART_FENCE : 0;
            writePayment(p, new Payment(VectorParryRules.PAYMENT_PERIOD, restart, receipt,
                    p.serverLevel().dimension().location().toString(), worldTick + 1));
            c.paymentReceipt = receipt;
            holdPaymentTick(p, 1 | (restart > 0 ? 2 : 0));
            return owned(p) == c && structurallyValid(c);
        } catch (RuntimeException | LinkageError uncertainPayment) {
            // A callback may already have paid. Never refund CP or overwrite changed foreign data.
            return false;
        } finally {
            if (!spent && p.getPersistentData().getCompound(HighPowerResources.DATA_KEY) == budget
                    && budget.equals(refusedBudget) && budget.contains(CLOCK, Tag.TAG_LONG) && budget.getLong(CLOCK) == worldTick) {
                // Refusal may restore our temporary value only while it is still ours.
                // A foreign callback's replacement compound or clock must survive.
                if (oldClock == null) budget.remove(CLOCK); else budget.put(CLOCK, oldClock);
            }
            c.paying = false;
        }
    }
    private static boolean resourceClockMatches(ServerPlayer p, long expected) {
        if (!p.getPersistentData().contains(HighPowerResources.DATA_KEY, Tag.TAG_COMPOUND)) return false;
        CompoundTag budget = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
        return expected == -1 ? !budget.contains(CLOCK)
                : budget.contains(CLOCK, Tag.TAG_LONG) && budget.getLong(CLOCK) == expected;
    }
    /** -1 represents a genuinely absent initial writer clock; malformed stored clocks refuse. */
    private static Long resourceClock(ServerPlayer p) {
        if (!p.getPersistentData().contains(HighPowerResources.DATA_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag budget = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
        if (!budget.contains(CLOCK)) return -1L;
        return budget.contains(CLOCK, Tag.TAG_LONG) && budget.getLong(CLOCK) >= 0
                && budget.getLong(CLOCK) < Long.MAX_VALUE ? budget.getLong(CLOCK) : null;
    }
    private static Payment readPayment(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(PAYMENT_KEY)) {
            Long resource = resourceClock(p);
            return resource != null && resource == -1 ? new Payment(0, 0, 0, "", -1) : null;
        }
        if (!root.contains(PAYMENT_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = root.getCompound(PAYMENT_KEY);
        if (!tag.contains("version", Tag.TAG_INT)) return null;
        if (tag.getInt("version") != 2 || !tag.getAllKeys().equals(Set.of("version", "remaining", "restart", "receipt", "dimension", "resourceUntil"))
                || !tag.contains("remaining", Tag.TAG_INT) || !tag.contains("restart", Tag.TAG_INT) || !tag.contains("receipt", Tag.TAG_LONG)
                || !tag.contains("dimension", Tag.TAG_STRING) || !tag.contains("resourceUntil", Tag.TAG_LONG)
                || tag.getInt("remaining") < 0 || tag.getInt("remaining") > VectorParryRules.PAYMENT_PERIOD
                || tag.getInt("restart") < 0 || tag.getInt("restart") > VectorParryRules.PAYMENT_PERIOD
                || tag.getLong("receipt") < 0 || tag.getString("dimension").length() > 256
                || tag.getLong("resourceUntil") < 0 || tag.getLong("resourceUntil") == Long.MAX_VALUE) return null;
        return new Payment(tag.getInt("remaining"), tag.getInt("restart"), tag.getLong("receipt"),
                tag.getString("dimension"), tag.getLong("resourceUntil"));
    }
    private static void writePayment(ServerPlayer p, Payment payment) {
        CompoundTag tag = new CompoundTag(); tag.putInt("version", 2); tag.putInt("remaining", payment.remaining);
        tag.putInt("restart", payment.restart); tag.putLong("receipt", payment.receipt); tag.putString("dimension", payment.dimension);
        tag.putLong("resourceUntil", payment.resourceUntil); p.getPersistentData().put(PAYMENT_KEY, tag);
    }
    private static void holdPaymentTick(ServerPlayer p, int fields) {
        PhysicalTick clock = physicalTick(p);
        if (!clock.players.contains(p.getUUID())) clock.paymentStarts.merge(p.getUUID(), fields, (a, b) -> a | b);
    }

    private static boolean canAct(ServerPlayer p) { return basic(p) && HighPowerResources.ready(p) && HighPowerCombat.unsupportedReason(p).isEmpty(); }
    private static boolean basic(ServerPlayer p) {
        if (!thread(p) || p.connection == null || p.hasDisconnected() || p.isRemoved() || !p.isAlive()
                || p.isSpectator() || p.getAbilities().instabuild || p.getAbilities().invulnerable || p.noPhysics || p.isPassenger() || p.isVehicle()
                || p.isSleeping() || p.isFallFlying() || p.getAbilities().flying || p.containerMenu != p.inventoryMenu || AbilityConsumption.busy(p)) return false;
        var state = AbilityStorage.peek(p);
        return state != null && HighPowerCombat.enabled(p) && state.category.equals("vecmanip") && state.activated && state.overloadFine && !state.interfering
                && state.learned(ID) && state.learned(CirculationRelief.ID) && state.learned(InertialAnchor.ID) && state.learned(VectorParry.ID)
                && state.level >= 3 && state.level <= 5 && SkillCatalog.enabled("vecmanip", ID)
                && finite(p.position()) && finite(p.getLookAngle()) && finite(p.getDeltaMovement()) && p.getLookAngle().lengthSqr() > .5
                && Float.isFinite(p.getXRot()) && Float.isFinite(p.getYRot()) && VectorParryRules.clock(now(p));
    }
    private static boolean structurallyValid(Context c) {
        ServerPlayer p = c.identity.owner();
        return owned(p) == c && snapshotOwned(c) && basic(p) && now(p) >= c.started
                && c.preset == c.identity.state().presets.current() && c.revision == c.identity.state().presets.revision()
                && ID.equals(c.identity.state().presets.currentSkill(c.input.slot()));
    }
    private static boolean snapshotOwned(Context c) {
        ServerPlayer p = c.identity.owner();
        return p.serverLevel() == c.identity.level() && p.level() == c.identity.level() && p.getId() == c.input.actor()
                && AbilityStorage.peek(p) == c.identity.state() && c.identity.level().getEntity(p.getId()) == p && c.identity.level().getEntity(p.getUUID()) == p
                && LegacySingleKeyOwnerEpoch.matches(p, p.connection, c.identity.level(), c.identity.state(), c.identity.epoch());
    }
    private static boolean valid(Context c) {
        if (c.paying || !structurallyValid(c)) return false;
        Payment payment = readPayment(c.identity.owner());
        return payment != null && c.paymentReceipt > 0 && payment.receipt == c.paymentReceipt && payment.remaining > 0;
    }
    private static Context owned(ServerPlayer p) { if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null; }
    private static boolean thread(ServerPlayer p) { return p != null && p.server != null && p.server.isSameThread(); }
    private static long now(ServerPlayer p) { return MagneticDomain.serverTime(p.server); }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z) && Math.abs(v.x) <= 30_000_001 && Math.abs(v.y) <= 30_000_001 && Math.abs(v.z) <= 30_000_001; }
    private static VectorParryRules.Point point(Vec3 v) { return v == null ? null : new VectorParryRules.Point(v.x, v.y, v.z); }
    private static Vec3 vec(VectorParryRules.Point value) { return new Vec3(value.x(), value.y(), value.z()); }
    private static boolean submerged(ServerPlayer p) { return p.isUnderWater() || p.isInLava(); }
    private static void finish(Context c) {
        ServerPlayer p = c.identity.owner(); if (!CONTEXTS.remove(p.getUUID(), c)) return;
        clearSamples(c);
        if (c.paymentReceipt > 0) {
            Payment payment = readPayment(p);
            if (payment != null && payment.receipt == c.paymentReceipt) {
                int restart = Math.max(payment.restart, c.cooldown);
                writePayment(p, new Payment(payment.remaining, restart, payment.receipt, payment.dimension, payment.resourceUntil));
                if (restart > payment.restart) holdPaymentTick(p, 2);
            }
            c.identity.state().setCooldown(ID, c.cooldown);
        }
        send(c, ENDED, List.of());
    }
    private static void send(Context c, int phase, List<Cue> cues) {
        ServerPlayer p = c.identity.owner();
        if (c.sequence == Long.MAX_VALUE || phase != ENDED && (owned(p) != c || !snapshotOwned(c))) return;
        Visual packet = new Visual(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.identity.epoch(), ++c.sequence, phase, cues);
        c.lastVisual = packet;
        // Deliberately only the authenticated caster: never nearby players or entity trackers.
        if (p.connection != null && !p.hasDisconnected() && p.serverLevel() == c.identity.level()) PacketDistributor.sendToPlayer(p, packet);
    }
    public record Cue(int azimuth, int elevation, int strength, int kind) {
        public Cue {
            if (azimuth < 0 || azimuth > 15 || elevation < -1 || elevation > 1 || strength < 1 || strength > 3 || kind < KIND_ARROW || kind > KIND_RUNNER)
                throw new IllegalArgumentException("Invalid bounded NEW433 cue");
        }
    }
    public record Visual(int entity, UUID owner, long input, long token, long epoch, long sequence, int phase, List<Cue> cues) implements CustomPacketPayload {
        public Visual {
            if (entity < 0 || owner == null || input <= 0 || token <= 0 || epoch <= 0 || sequence <= 0 || phase < ACTIVE || phase > ENDED
                    || cues == null || cues.size() > CirculationListeningRules.MAX_CUES || phase != ACTIVE && !cues.isEmpty())
                throw new IllegalArgumentException("Invalid bounded NEW433 visual");
            cues = List.copyOf(cues);
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_circulation_listening"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((buffer, value) -> {
            buffer.writeInt(value.entity); buffer.writeUUID(value.owner); buffer.writeLong(value.input); buffer.writeLong(value.token);
            buffer.writeLong(value.epoch); buffer.writeLong(value.sequence); buffer.writeByte(value.phase); buffer.writeByte(value.cues.size());
            for (Cue cue : value.cues) { buffer.writeByte(cue.azimuth); buffer.writeByte(cue.elevation); buffer.writeByte(cue.strength); buffer.writeByte(cue.kind); }
        }, buffer -> {
            int entity = buffer.readInt(); UUID owner = buffer.readUUID(); long input = buffer.readLong(), token = buffer.readLong(), epoch = buffer.readLong(), sequence = buffer.readLong();
            int phase = buffer.readUnsignedByte(), count = buffer.readUnsignedByte();
            if (count > CirculationListeningRules.MAX_CUES) throw new IllegalArgumentException("Oversized NEW433 cue batch");
            List<Cue> cues = new ArrayList<>(count);
            for (int i = 0; i < count; i++) cues.add(new Cue(buffer.readUnsignedByte(), buffer.readByte(), buffer.readUnsignedByte(), buffer.readUnsignedByte()));
            return new Visual(entity, owner, input, token, epoch, sequence, phase, cues);
        });
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static void feedback(ServerPlayer p, String key) {
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.academy.highpower.circulation_listening." + key), true);
    }
    private static boolean reject(ServerPlayer p, HighPowerInput.Input input, String reason) { feedback(p, reason); return reject(p, input); }
    private static boolean reject(ServerPlayer p, HighPowerInput.Input input) {
        PacketDistributor.sendToPlayer(p, new Rejection(p.getId(), p.getUUID(), input.input(), input.epoch())); return false;
    }
    public record Rejection(int entity, UUID owner, long input, long epoch) implements CustomPacketPayload {
        public Rejection {
            if (entity < 0 || owner == null || input <= 0 || epoch <= 0) throw new IllegalArgumentException("Invalid NEW433 rejection");
        }
        public static final Type<Rejection> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_circulation_listening_rejected"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Rejection> CODEC = StreamCodec.of((buffer, value) -> {
            buffer.writeInt(value.entity); buffer.writeUUID(value.owner); buffer.writeLong(value.input); buffer.writeLong(value.epoch);
        }, buffer -> new Rejection(buffer.readInt(), buffer.readUUID(), buffer.readLong(), buffer.readLong()));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet));
            event.registrar("1").playToClient(Rejection.TYPE, Rejection.CODEC, (packet, context) -> clientRejectionReceiver.accept(packet));
        }
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Pre event) {
        for (Context context : new ArrayList<>(CONTEXTS.values())) if (context.identity.owner().server == event.getServer()) tick(context.identity.owner());
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void death(LivingDeathEvent event) { if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void leave(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof ServerPlayer p) remove(p);
        for (Context context : CONTEXTS.values()) {
            Observation observation = context.samples.get(entity.getUUID());
            if (observation != null && observation.entity == entity) {
                context.samples.remove(entity.getUUID()); CirculationListeningMotionProof.unwatch(entity, context);
            }
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void clone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer old)) return;
        remove(old);
        if (!(event.getEntity() instanceof ServerPlayer replacement) || !thread(replacement) || !old.getUUID().equals(replacement.getUUID())) return;
        CompoundTag original = old.getPersistentData(), copied = replacement.getPersistentData();
        if (!original.contains(PAYMENT_KEY)) {
            // An orphaned writer was already uncertified before cloning; a fresh body must
            // not launder it into a free initial payment just because vanilla omits this tag.
            if (original.contains(HighPowerResources.DATA_KEY, Tag.TAG_COMPOUND)
                    && original.getCompound(HighPowerResources.DATA_KEY).contains(CLOCK)) blockClonedPayment(copied);
            return;
        }
        copied.put(PAYMENT_KEY, original.get(PAYMENT_KEY).copy());
        Payment proof = readPayment(old);
        if (proof == null || proof.receipt <= 0 || proof.resourceUntil < 0 || !resourceClockMatches(old, proof.resourceUntil)) {
            // Preserve uncertainty even if another clone subscriber supplied a coincidentally
            // matching destination writer. No missing/corrupt proof can certify a new one.
            blockClonedPayment(copied); return;
        }
        if (copied.contains(HighPowerResources.DATA_KEY) && !copied.contains(HighPowerResources.DATA_KEY, Tag.TAG_COMPOUND)) return;
        boolean absent = !copied.contains(HighPowerResources.DATA_KEY);
        CompoundTag budget = absent ? new CompoundTag() : copied.getCompound(HighPowerResources.DATA_KEY);
        // Copy exactly our certified writer only; never replace a present field, CP, S,
        // another skill's clock or the rest of the shared resource compound.
        if (!budget.contains(CLOCK)) {
            budget.putLong(CLOCK, proof.resourceUntil);
            if (absent) copied.put(HighPowerResources.DATA_KEY, budget);
        }
    }
    private static void blockClonedPayment(CompoundTag root) {
        CompoundTag invalid = new CompoundTag(); invalid.putInt("version", -1);
        root.put(PAYMENT_KEY, invalid);
    }
    public static void clear() {
        for (Context c : new ArrayList<>(CONTEXTS.values())) finish(c);
        CONTEXTS.clear(); INPUTS.clear();
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == event.getServer()) finish(c);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { clear(); PHYSICAL.remove(event.getServer()); }
}
