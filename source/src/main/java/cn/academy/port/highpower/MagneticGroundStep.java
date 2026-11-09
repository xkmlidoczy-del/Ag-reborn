package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.MagneticGroundStepRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;
import java.util.function.Consumer;

/** NEW-132: bounded ground-only velocity assistance and one paid ordinary metal-arrow side turn. */
@EventBusSubscriber(modid = "academy")
public final class MagneticGroundStep {
    public static final String ID = "magnetic_ground_step";
    private static final String QUOTA = "academy:ground_step_deflection_until";
    private static final String ARROW_LEASE = "academy:ground_step_arrow_until";
    private static final TagKey<EntityType<?>> METAL = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("academy", "magnetic_projectiles"));
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<UUID, Lease> LEASES = new HashMap<>();
    private static final Map<UUID, Long> QUOTAS = new HashMap<>();
    private static final boolean DIAGNOSTICS = Boolean.getBoolean("academy.highpower.groundStepDiagnostics");
    private static final org.slf4j.Logger DIAGNOSTIC_LOG = org.slf4j.LoggerFactory.getLogger("academy.ground_step_diagnostics");
    private static final Map<Long, Set<String>> DIAGNOSTIC_EVENTS = DIAGNOSTICS ? new HashMap<>() : null;
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    public static Consumer<Assist> motionReceiver = ignored -> {};
    private record Watermark(long epoch, long input) {}
    private record Lease(AbstractArrow arrow, ServerLevel world, long until) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final Vec3 origin, direction;
        final int preset;
        final long revision, started;
        final Set<ServerPlayer> audience = new LinkedHashSet<>();
        Vec3 previous, segmentStart, segmentEnd;
        double travelled;
        boolean deflected, practiced;
        long lastTick, sequence, grants;
        final double range;
        Context(ServerPlayer p, HighPowerInput.Input input, Vec3 direction, double range) {
            this.range = range;
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input; this.direction = direction; this.origin = p.position();
            preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            started = lastTick = now(p); previous = p.position();
            segmentStart = segmentEnd = previous.add(0, .04, 0);
        }
    }
    private MagneticGroundStep() {}

    /** The client supplies identity only. Sprint, direction, geometry and resources are server observed. */
    public static boolean tap(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input) || AbilityConsumption.busy(p)) return false;
        var old = INPUTS.get(p.getUUID());
        if (old != null && old.epoch == input.epoch() && input.input() <= old.input) return false;
        INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        if (owned(p) != null || !canAct(p) || !p.isSprinting()
                || !ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))
                || !AbilityStorage.get(p).canUse(ID) || nextToken == Long.MAX_VALUE) return false;
        Vec3 look = p.getLookAngle(), horizontal = new Vec3(look.x, 0, look.z);
        if (!finite(look) || horizontal.lengthSqr() < .04) return false;
        Vec3 direction = horizontal.normalize();
        if (!safeGroundArea(p)) return false;
        var q = HighPowerGrowth.values(p).position();
        if (!ElectromasterPrototype.trySpend(p, ID, MagneticGroundStepRules.cpCost(q), MagneticGroundStepRules.cooldownTicks(q))) return false;
        var c = new Context(p, input, direction, MagneticGroundStepRules.range(q)); CONTEXTS.put(p.getUUID(), c);
        diagnostic(c.identity.token(), "server_start", "tick=" + c.started + " remaining=" + c.range + " lifetime=40");
        send(c, 0, Vec3.ZERO); return true;
    }
    public static boolean abort(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        var old = INPUTS.get(p.getUUID());
        if (old == null || old.epoch != input.epoch() || input.input() > old.input)
            INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        Context c = owned(p);
        if (c == null || c.input.epoch() != input.epoch() || c.input.slot() != input.slot()
                || c.input.input() > input.input()) return false;
        finish(c, "bounded_abort"); return true;
    }
    /** Cancellation never brakes or resets fall distance, including invalidation and range exhaustion. */
    public static void abort(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c, "lifecycle_abort"); }
    public static void remove(ServerPlayer p) { if (!thread(p)) return; abort(p); INPUTS.remove(p.getUUID()); }
    public static boolean active(ServerPlayer p) { return owned(p) != null; }
    public static double poweredDistance(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.travelled; }
    public static boolean deflected(ServerPlayer p) { Context c = owned(p); return c != null && c.deflected; }
    public static void clear() {
        if (DIAGNOSTICS) for (Context c : CONTEXTS.values()) diagnostic(c.identity.token(), "server_end",
                "reason=server_clear elapsed=" + (c.lastTick - c.started) + " grants=" + c.grants + " distance=" + c.travelled + " deflected=" + c.deflected);
        CONTEXTS.clear(); INPUTS.clear(); LEASES.clear(); QUOTAS.clear();
    }
    /** Read-only cross-controller exclusion; a finished cast retains the remaining arrow lease. */
    public static boolean leased(AbstractArrow arrow) {
        if (arrow == null || !(arrow.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return true;
        Lease lease = LEASES.get(arrow.getUUID()); long tick = level.getServer().overworld().getGameTime();
        var data = arrow.getPersistentData();
        if (tick < 0 || tick > Long.MAX_VALUE - MagneticGroundStepRules.QUOTA_TICKS
                || data.contains(ARROW_LEASE) && !data.contains(ARROW_LEASE, net.minecraft.nbt.Tag.TAG_LONG)) return true;
        long until = data.getLong(ARROW_LEASE);
        return until < 0 || until > tick || lease != null && lease.arrow == arrow && lease.world == level && tick < lease.until;
    }

    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null) return;
        long tick = now(p);
        if (!valid(c)) { if (DIAGNOSTICS) diagnostic(c.identity.token(), "invalid", "detail=" + invalidDetail(c, p)); finish(c, "owner_or_eligibility_invalid"); return; }
        if (MagneticGroundStepRules.expired(c.started, tick)) { finish(c, "lifetime_expired"); return; }
        if (tick <= c.lastTick) return;
        Vec3 displacement = p.position().subtract(c.previous);
        double distance = Math.hypot(displacement.x, displacement.z);
        if (!finite(displacement) || Math.abs(displacement.y) > .025 || distance > MagneticGroundStepRules.MAX_OBSERVED_STEP
                || c.travelled + distance > c.range + 1e-8) { finish(c, "observed_step_height_or_range_invalid"); return; }
        c.travelled += distance; c.lastTick = tick;
        c.segmentStart = c.previous.add(0, .04, 0); c.previous = p.position(); c.segmentEnd = c.previous.add(0, .04, 0);
        if (c.travelled >= c.range - 1e-8) { finish(c, "powered_range_exhausted"); return; }
        if (!safeGroundArea(p)) { finish(c, "unsupported_ground_or_body"); return; }
        // A server player tick's transient position is reset by the native connection loop.
        // This method is called at ServerTick.Post, after accepted movement is stable. Never
        // send vanilla absolute-motion packets: only a bounded additive client authorization.
        sendAssist(c, false);
        boolean turned = distance > 1e-6 && tryDeflect(c, c.direction.scale(MagneticGroundStepRules.MAX_SPEED));
        if (!turned && (tick - c.started) % 2 == 0) send(c, 0, Vec3.ZERO);
    }
    private static boolean canAct(ServerPlayer p) {
        if (!thread(p)) return false;
        // Native eligibility methods such as onClimbable read block state. Fence unloaded space
        // before calling any of them, not merely before the later collision/support sweep.
        AABB envelope = p.getBoundingBox().inflate(MagneticGroundStepRules.MAX_SPEED, .025, MagneticGroundStepRules.MAX_SPEED);
        if (!loadedArea(p.serverLevel(), envelope) || !p.serverLevel().getWorldBorder().isWithinBounds(envelope)) return false;
        var s = AbilityStorage.get(p);
        return ElectromasterPrototype.ready(p) && s.activated && s.overloadFine && s.learned(ID)
                && s.category.equals("electromaster") && s.level <= 5 && ElectromasterPrototype.effectiveLevel(p) >= 3
                && SkillCatalog.enabled("electromaster", ID) && HighPowerCombat.unsupportedReason(p).isEmpty()
                && !AbilityConsumption.busy(p) && !competingMovement(p) && !HighPowerBracerSession.active(p)
                && !(p.isUsingItem() && p.getUseItem().is(ElectromasterPrototype.CONTROL.get()))
                && p.onGround() && !p.noPhysics && !p.isPassenger() && !p.isSleeping() && !p.isFallFlying()
                && !p.getAbilities().flying && !p.isInWaterOrBubble() && !p.isInLava() && !p.onClimbable()
                && !p.isShiftKeyDown() && !p.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)
                && !p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)
                && finite(p.position()) && finite(p.getDeltaMovement()) && Float.isFinite(p.getXRot()) && Float.isFinite(p.getYRot());
    }
    private static boolean competingMovement(ServerPlayer p) {
        return WindPressureLift.active(p) || PolarityTether.active(p) || cn.academy.port.skill.MagMovement.active(p) || cn.academy.port.skill.VecAccel.active(p)
                || cn.academy.port.skill.JetEngine.active(p) || cn.academy.port.skill.StormWing.active(p)
                || cn.academy.port.skill.MarkTeleport.active(p) || cn.academy.port.skill.PenetrateTeleport.active(p)
                || cn.academy.port.skill.Flashing.active(p);
    }
    /**
     * Diagnostic-only mirror of {@link #valid} that names the first failing clause. Never called unless
     * {@code -Dacademy.highpower.groundStepDiagnostics=true}; it changes no state and gates nothing. The clause order
     * deliberately mirrors {@code valid}/{@code canAct} so the unloaded-space fence still runs before any native
     * eligibility read (onClimbable reads block state).
     */
    private static String invalidDetail(Context c, ServerPlayer p) {
        if (!c.identity.owns(p)) return "identity";
        if (!HighPowerBracerSession.authenticated(p, c.input)) return "bracer_session";
        String ineligible = ineligibleDetail(p);
        if (ineligible != null) return "canAct:" + ineligible;
        if (!p.isSprinting()) return "not_sprinting";
        if (c.preset != c.identity.state().presets.current()) return "preset_changed";
        if (c.revision != c.identity.state().presets.revision()) return "revision_changed";
        if (!ID.equals(c.identity.state().presets.currentSkill(c.input.slot()))) return "slot_skill_changed";
        return null;
    }
    /** Diagnostic-only mirror of {@link #canAct}; same clauses in the same order, returning the first failure. */
    private static String ineligibleDetail(ServerPlayer p) {
        if (!thread(p)) return "off_thread";
        AABB fence = p.getBoundingBox().inflate(MagneticGroundStepRules.MAX_SPEED, .025, MagneticGroundStepRules.MAX_SPEED);
        if (!loadedArea(p.serverLevel(), fence) || !p.serverLevel().getWorldBorder().isWithinBounds(fence)) return "unloaded_or_border";
        var s = AbilityStorage.get(p);
        if (!ElectromasterPrototype.ready(p)) return "prototype_not_ready";
        if (!s.activated) return "not_activated";
        if (!s.overloadFine) return "overload_unsafe";
        if (!s.learned(ID)) return "not_learned";
        if (!s.category.equals("electromaster")) return "category";
        if (s.level > 5) return "level_above_five";
        if (ElectromasterPrototype.effectiveLevel(p) < 3) return "effective_level_below_three";
        if (!SkillCatalog.enabled("electromaster", ID)) return "skill_disabled";
        String unsupported = HighPowerCombat.unsupportedReason(p).orElse("");
        if (!unsupported.isEmpty()) return "unsupported:" + unsupported;
        if (AbilityConsumption.busy(p)) return "consumption_busy";
        if (competingMovement(p)) return "competing_movement";
        if (HighPowerBracerSession.active(p)) return "bracer_active";
        if (p.isUsingItem() && p.getUseItem().is(ElectromasterPrototype.CONTROL.get())) return "using_control";
        if (!p.onGround()) return "not_on_ground";
        if (p.noPhysics) return "no_physics";
        if (p.isPassenger()) return "passenger";
        if (p.isSleeping()) return "sleeping";
        if (p.isFallFlying()) return "fall_flying";
        if (p.getAbilities().flying) return "abilities_flying";
        if (p.isInWaterOrBubble()) return "in_water";
        if (p.isInLava()) return "in_lava";
        if (p.onClimbable()) return "climbable";
        if (p.isShiftKeyDown()) return "shift_down";
        if (p.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)) return "levitation";
        if (p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)) return "slow_falling";
        if (!finite(p.position())) return "position_not_finite";
        if (!finite(p.getDeltaMovement())) return "delta_not_finite";
        if (!Float.isFinite(p.getXRot()) || !Float.isFinite(p.getYRot())) return "rotation_not_finite";
        return null;
    }
    private static boolean valid(Context c) {
        ServerPlayer p = c.identity.owner();
        return c.identity.owns(p) && HighPowerBracerSession.authenticated(p, c.input) && canAct(p) && p.isSprinting()
                && c.preset == c.identity.state().presets.current() && c.revision == c.identity.state().presets.revision()
                && ID.equals(c.identity.state().presets.currentSkill(c.input.slot()));
    }
    /** Conservative all-direction next-step envelope; the client separately checks its exact native step. */
    private static boolean safeGroundArea(ServerPlayer p) {
        AABB body = p.getBoundingBox().inflate(MagneticGroundStepRules.MAX_SPEED, 0, MagneticGroundStepRules.MAX_SPEED).deflate(1e-7);
        AABB support = new AABB(body.minX, p.getY() - .025, body.minZ, body.maxX, p.getY() - .005, body.maxZ);
        ServerLevel world = p.serverLevel();
        if (!loadedArea(world, body.minmax(support)) || !world.getWorldBorder().isWithinBounds(body.minmax(support))
                || !world.noCollision(p, body)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX, support.minY, body.minZ),
                BlockPos.containing(body.maxX, body.maxY, body.maxZ)))
            if (!permitted(p, pos) || !world.getFluidState(pos).isEmpty()) return false;
        var needed = Shapes.create(support); var supported = Shapes.empty();
        for (var shape : world.getBlockCollisions(p, support)) supported = Shapes.or(supported, shape);
        return !Shapes.joinIsNotEmpty(needed, supported, BooleanOp.ONLY_FIRST);
    }
    private static boolean tryDeflect(Context c, Vec3 velocity) {
        ServerPlayer p = c.identity.owner(); long tick = now(p);
        if (c.deflected || !quotaAvailable(p, tick)) return false;
        AABB path = p.getBoundingBox().expandTowards(new Vec3(velocity.x, 0, velocity.z)).inflate(.75, .25, .75);
        if (!loadedArea(p.serverLevel(), path)) return false;
        List<AbstractArrow> candidates = new ArrayList<>(MagneticGroundStepRules.SCAN_LIMIT);
        p.serverLevel().getEntities(EntityTypeTest.forClass(AbstractArrow.class), path,
                a -> cheapEligible(p, a), candidates, MagneticGroundStepRules.SCAN_LIMIT);
        candidates.sort(Comparator.comparingDouble(a -> a.distanceToSqr(p)));
        for (AbstractArrow arrow : candidates) {
            Vec3 at = arrow.position(), incoming = arrow.getDeltaMovement(), center = p.getBoundingBox().getCenter();
            if (incoming.dot(center.subtract(at)) <= 0 || !path.contains(at) && path.clip(at, at.add(incoming)).isEmpty()
                    || !permitted(p, BlockPos.containing(at)) || !clearLine(p, at, center)) continue;
            Vec3 left = vec(MagneticGroundStepRules.deflect(vector(incoming), 1));
            Vec3 right = vec(MagneticGroundStepRules.deflect(vector(incoming), -1));
            if (left == null || right == null) continue;
            Vec3 changed = at.add(left).distanceToSqr(center) >= at.add(right).distanceToSqr(center) ? left : right;
            Vec3 end = at.add(changed);
            if (!loadedArea(p.serverLevel(), new AABB(at, end)) || !permitted(p, BlockPos.containing(end))
                    || !clearLine(p, at, end)) continue;
            var ledger = HighPowerCombat.exactState(p);
            if (ledger.isEmpty() || !HighPowerCombat.spendShield(p, MagneticGroundStepRules.shieldFee(ledger.get().maxShield()))) return false;
            arrow.setDeltaMovement(changed); arrow.hasImpulse = true; arrow.hurtMarked = true;
            c.deflected = true; p.getPersistentData().putLong(QUOTA, tick + MagneticGroundStepRules.QUOTA_TICKS);
            QUOTAS.put(p.getUUID(), tick + MagneticGroundStepRules.QUOTA_TICKS);
            LEASES.put(arrow.getUUID(), new Lease(arrow, p.serverLevel(), tick + MagneticGroundStepRules.QUOTA_TICKS));
            arrow.getPersistentData().putLong(ARROW_LEASE, tick + MagneticGroundStepRules.QUOTA_TICKS);
            send(c, 1, at); practice(c); return true;
        }
        return false;
    }
    private static boolean cheapEligible(ServerPlayer p, AbstractArrow arrow) {
        if (arrow == null || arrow.isRemoved() || !arrow.isAlive() || arrow.level() != p.serverLevel()
                || p.serverLevel().getEntity(arrow.getUUID()) != arrow || arrow.inGround || arrow.isNoPhysics()
                || !arrow.getType().is(METAL) || arrow.getClass() != Arrow.class && arrow.getClass() != SpectralArrow.class
                || arrow.getPierceLevel() != 0 || arrow.isCritArrow() || arrow.isOnFire()
                || arrow.getClass() == Arrow.class && !arrow.getPickupItemStackOrigin().is(net.minecraft.world.item.Items.ARROW)
                || leased(arrow) || MultiAxisReflectionArrowBridge.leased(arrow) || TrajectoryReturnArrowBridge.leased(arrow) || ShortInterceptionArrowBridge.leased(arrow)||IronSandLance.leased(arrow)||MagneticArrowSeparation.leased(arrow)||MagneticDomainArrows.leased(arrow) || ElectromasterChargeDecoy.leased(arrow) || ElectromasterPrototype.leased(arrow)) return false;
        Entity owner = arrow.getOwner();
        if (owner == p || owner != null && p.isAlliedTo(owner) || owner instanceof Player && !AcademyConfig.ATTACK_PLAYERS.get()
                || owner instanceof ServerPlayer other && !p.canHarmPlayer(other)) return false;
        Vec3 v = arrow.getDeltaMovement();
        return finite(arrow.position()) && finite(v) && Math.hypot(v.x, v.z) >= .03 && v.lengthSqr() <= 9;
    }
    private static void practice(Context c) {
        if (c.practiced) return; c.practiced = true;
        if (c.identity.state() == AbilityStorage.get(c.identity.owner())) {
            c.identity.state().addExperience(ID, .005); AbilityStorage.save(c.identity.owner());
        }
    }
    private static boolean quotaAvailable(ServerPlayer p, long tick) {
        var data = p.getPersistentData();
        if (tick < 0 || tick > Long.MAX_VALUE - MagneticGroundStepRules.QUOTA_TICKS
                || data.contains(QUOTA) && !data.contains(QUOTA, net.minecraft.nbt.Tag.TAG_LONG)) return false;
        long persisted = data.getLong(QUOTA), live = QUOTAS.getOrDefault(p.getUUID(), 0L);
        // A bad or backwards-clock timestamp denies another deflection; it is never silently normalized.
        return persisted >= 0 && live >= 0 && persisted <= tick && live <= tick;
    }
    private static boolean clearLine(ServerPlayer p, Vec3 start, Vec3 end) {
        return loadedArea(p.serverLevel(), new AABB(start, end))
                && p.serverLevel().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }
    private static boolean permitted(ServerPlayer p, BlockPos pos) {
        ServerLevel world = p.serverLevel();
        return !world.isOutsideBuildHeight(pos) && world.hasChunkAt(pos)
                && world.getWorldBorder().isWithinBounds(pos) && world.mayInteract(p, pos);
    }
    private static boolean loadedArea(ServerLevel world, AABB box) {
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.maxX) || !Double.isFinite(box.minY) || !Double.isFinite(box.maxY)
                || !Double.isFinite(box.minZ) || !Double.isFinite(box.maxZ) || box.getXsize() > 8 || box.getYsize() > 8 || box.getZsize() > 8
                || box.minY < world.getMinBuildHeight() || box.maxY >= world.getMaxBuildHeight()) return false;
        for (int x = ((int)Math.floor(box.minX)) >> 4; x <= ((int)Math.floor(box.maxX)) >> 4; x++)
            for (int z = ((int)Math.floor(box.minZ)) >> 4; z <= ((int)Math.floor(box.maxZ)) >> 4; z++) if (!world.hasChunk(x, z)) return false;
        return true;
    }
    private static Context owned(ServerPlayer p) {
        if (!thread(p)) return null;
        Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null;
    }
    private static boolean thread(ServerPlayer p) { return p != null && p.serverLevel().getServer().isSameThread(); }
    private static long now(ServerPlayer p) { return p.serverLevel().getServer().overworld().getGameTime(); }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static MagneticGroundStepRules.Vector vector(Vec3 v) { return new MagneticGroundStepRules.Vector(v.x, v.y, v.z); }
    private static Vec3 vec(MagneticGroundStepRules.Vector v) { return v == null ? null : new Vec3(v.x(), v.y(), v.z()); }
    /** Opt-in bounded QA observer only. No coordinates, UUIDs, names, files, packets or gameplay writes. */
    public static synchronized void diagnostic(long token, String stage, String detail) {
        if (!DIAGNOSTICS || token <= 0 || stage == null || detail == null) return;
        if (!DIAGNOSTIC_EVENTS.containsKey(token) && DIAGNOSTIC_EVENTS.size() >= 256) return;
        Set<String> events = DIAGNOSTIC_EVENTS.computeIfAbsent(token, ignored -> new HashSet<>());
        if (events.size() >= 8 || !events.add(stage)) return;
        DIAGNOSTIC_LOG.info("NEW132 token={} stage={} {}", token, stage.length() > 48 ? stage.substring(0, 48) : stage,
                detail.length() > 512 ? detail.substring(0, 512) : detail);
    }
    private static void finish(Context c, String reason) {
        diagnostic(c.identity.token(), "server_end", "reason=" + reason + " elapsed=" + (now(c.identity.owner()) - c.started)
                + " grants=" + c.grants + " distance=" + c.travelled + " deflected=" + c.deflected
                + " sprint=" + c.identity.owner().isSprinting() + " ground=" + c.identity.owner().onGround());
        CONTEXTS.remove(c.identity.owner().getUUID(), c); sendAssist(c, true); send(c, 2, Vec3.ZERO); c.audience.clear();
    }
    private static void sendAssist(Context c, boolean terminal) {
        ServerPlayer p = c.identity.owner();
        if (p.connection == null || p.hasDisconnected() || p.serverLevel() != c.identity.level()) return;
        long issuedAt = p.serverLevel().getGameTime();
        if (issuedAt < 0 || issuedAt > Long.MAX_VALUE - 2) return;
        if (!terminal) c.grants++;
        PacketDistributor.sendToPlayer(p, new Assist(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.identity.epoch(), ++c.sequence,
                c.origin, c.direction, terminal ? 0 : Math.max(0, c.range - c.travelled),
                terminal ? 0 : (int)Math.max(0, MagneticGroundStepRules.MAX_TICKS - (now(p) - c.started)), issuedAt, issuedAt + 2, terminal));
    }
    private static void send(Context c, int phase, Vec3 deflection) {
        ServerPlayer p = c.identity.owner();
        for (ServerPlayer viewer : c.identity.level().players()) if (viewer.distanceToSqr(p) <= 32 * 32) c.audience.add(viewer);
        c.audience.add(p);
        var packet = new Visual(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.identity.epoch(), ++c.sequence,
                phase, c.segmentStart, c.segmentEnd, deflection, phase == 2 ? 0 : (int)Math.max(0, MagneticGroundStepRules.MAX_TICKS - (now(p) - c.started)));
        for (ServerPlayer viewer : c.audience) if (viewer.connection != null && !viewer.hasDisconnected() && viewer.serverLevel() == c.identity.level())
            PacketDistributor.sendToPlayer(viewer, packet);
    }
    public record Visual(int entity, UUID owner, long input, long token, long epoch, long sequence, int phase,
                         Vec3 start, Vec3 end, Vec3 deflection, int remaining) implements CustomPacketPayload {
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_magnetic_ground_step"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.token); b.writeLong(v.epoch); b.writeLong(v.sequence); b.writeByte(v.phase);
            writeVector(b, v.start); writeVector(b, v.end); writeVector(b, v.deflection); b.writeByte(v.remaining);
        }, b -> new Visual(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readUnsignedByte(),
                readVector(b), readVector(b), readVector(b), b.readUnsignedByte()));
        private static void writeVector(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
        private static Vec3 readVector(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    /** Server authorization only. No C2S endpoint accepts position, budget, force or progress claims. */
    public record Assist(int entity, UUID owner, long input, long token, long epoch, long sequence,
                         Vec3 origin, Vec3 direction, double remaining, int remainingTicks, long issuedAt, long expiresAt, boolean terminal) implements CustomPacketPayload {
        public static final Type<Assist> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_ground_step_assist"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Assist> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.token); b.writeLong(v.epoch); b.writeLong(v.sequence);
            b.writeDouble(v.origin.x); b.writeDouble(v.origin.y); b.writeDouble(v.origin.z);
            b.writeDouble(v.direction.x); b.writeDouble(v.direction.y); b.writeDouble(v.direction.z);
            b.writeDouble(v.remaining); b.writeByte(v.remainingTicks); b.writeLong(v.issuedAt); b.writeLong(v.expiresAt); b.writeBoolean(v.terminal);
        }, b -> new Assist(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readLong(),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                b.readDouble(), b.readUnsignedByte(), b.readLong(), b.readLong(), b.readBoolean()));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) {
            e.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet));
            e.registrar("1").playToClient(Assist.TYPE, Assist.CODEC, (packet, context) -> motionReceiver.accept(packet));
        }
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post e) {
        diagnosticWatch();
        for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.level().getServer() == e.getServer()) tick(c.identity.owner());
        long tick = e.getServer().overworld().getGameTime(); LEASES.values().removeIf(l -> tick >= l.until || l.arrow.isRemoved());
        QUOTAS.values().removeIf(until -> tick >= until);
    }
    private static Boolean diagnosticLastEnabled;
    /**
     * Diagnostic only: reports every observed change of the process-global NEW132 config flag. The flag defaults to
     * disabled in {@code academy-server.toml}, while several GameTest fixtures reset the whole skill configuration to
     * all-enabled; a config (re)load in between therefore flips it under a running controller. This watcher never
     * changes the flag, a controller or an assertion.
     */
    private static void diagnosticWatch() {
        if (!DIAGNOSTICS) return;
        boolean now = SkillCatalog.enabled("electromaster", ID);
        if (diagnosticLastEnabled == null) diagnosticLastEnabled = now;
        else if (diagnosticLastEnabled != now) { diagnosticLastEnabled = now; DIAGNOSTIC_LOG.info("NEW132 flag changed enabled={}", now); }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { if (e.getOriginal() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void death(LivingDeathEvent e) { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { clear(); }
}
