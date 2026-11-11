package cn.academy.port.highpower;

import cn.academy.port.core.SkillTuning;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.LegacySingleKeyProtocol;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;
import java.util.function.Consumer;

/** NEW-123: one ground marker, real bounded steering, no entity, damage, teleport or mob aggro. */
@EventBusSubscriber(modid = "academy")
public final class ElectromasterChargeDecoy {
    public static final String ID = "charge_decoy";
    public static final double PLACEMENT_RANGE = SkillTuning.number("charge_decoy", "reach");
    /** Provisional implementation bound, not the workbook's placement range. */
    public static final double INFLUENCE_RADIUS = SkillTuning.number("charge_decoy", "radius"), MAX_ARROW_SPEED = 3;
    public static final int PULSE_TICKS = 20, MAX_ARROWS = 4, SCAN_LIMIT = 32;
    private static final double[] CP_COST = {0, 0, 5, 2, .5, .1};
    private static final TagKey<EntityType<?>> METAL = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("academy", "magnetic_projectiles"));
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    /** These rolling windows deliberately outlive replacement, abort, death and reconnect. */
    private static final Map<UUID, Deque<Admission>> QUOTAS = new HashMap<>();
    private static final Map<UUID, Lease> LEASES = new HashMap<>();
    private static final LegacySingleKeyProtocol.NonceLedger INPUTS = new LegacySingleKeyProtocol.NonceLedger();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private ElectromasterChargeDecoy() {}

    private record Ground(BlockPos block, Vec3 point) {}
    private record Admission(UUID arrow, long tick) {}
    private record Lease(Context owner, long until) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final long started;
        final Set<ServerPlayer> audience = new LinkedHashSet<>();
        final Map<UUID, AbstractArrow> arrows = new LinkedHashMap<>();
        Ground ground;
        Vec3 origin;
        long placed = -1, lastTick = Long.MIN_VALUE, sequence;
        Context(ServerPlayer player, long input, long token) {
            identity = new LegacySingleKeyServerIdentity(player, input, token, AbilityStorage.get(player));
            started = now(player); origin = player.getEyePosition();
        }
    }

    public static boolean canAcceptInput(ServerPlayer p, long input) {
        return LegacySingleKeyServerIdentity.canClaim(INPUTS, p, input);
    }
    public static boolean start(ServerPlayer p) { return startCaptured(p, 0); }
    public static boolean start(ServerPlayer p, long input) { return input > 0 && startCaptured(p, input); }
    private static boolean startCaptured(ServerPlayer p, long input) {
        if (!thread(p) || input > 0 && !LegacySingleKeyServerIdentity.claim(INPUTS, p, input)) return false;
        if (!canAct(p) || !AbilityStorage.get(p).canUse(ID)) return false;
        Context old = CONTEXTS.get(p.getUUID());
        if (old != null && old.identity.owner() == p && old.placed < 0) return false;
        Ground target = lookingGround(p);
        if (target == null) return false;
        if (old != null) finish(old);
        Context c = new Context(p, input, ++nextToken); c.ground = target;
        CONTEXTS.put(p.getUUID(), c); send(c, 0);
        return true;
    }
    public static boolean release(ServerPlayer p, long input, long token, long epoch) {
        Context c = owned(p);
        return c != null && c.identity.matches(p, input, token, epoch) && release(p, input);
    }
    public static boolean release(ServerPlayer p, long input) {
        Context c = owned(p);
        if (c == null || !c.identity.matches(p, input)) { retirePending(p, input); return false; }
        return release(p);
    }
    public static boolean release(ServerPlayer p) {
        Context c = owned(p);
        if (c == null || c.placed >= 0) return false;
        Ground ground = lookingGround(p);
        if (!valid(c) || now(p) - c.started < 1 || ground == null) { finish(c); return false; }
        int level = Math.min(HighPowerCombat.level(p), AbilityStorage.get(p).level);
        // Target, bounds and owner are checked before the only shared-ledger transaction.
        if (!ElectromasterPrototype.trySpend(p, ID, HighPowerGrowth.values(p).lv2Cost(), 1)) { finish(c); return false; }
        c.ground = ground; c.origin = p.getEyePosition(); c.placed = now(p); c.lastTick = Long.MIN_VALUE;
        send(c, 1); return true;
    }
    public static boolean abort(ServerPlayer p, long input, long token, long epoch) {
        Context c = owned(p);
        return c != null && c.identity.matches(p, input, token, epoch) && abort(p, input);
    }
    public static boolean abort(ServerPlayer p, long input) {
        Context c = owned(p);
        if (c == null || !c.identity.matches(p, input)) { retirePending(p, input); return false; }
        finish(c); return true;
    }
    private static void retirePending(ServerPlayer p, long input) {
        if (thread(p) && input > 0) LegacySingleKeyServerIdentity.claim(INPUTS, p, input);
    }
    public static void abort(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c); }
    public static void remove(ServerPlayer p) { if (!thread(p)) return; abort(p); INPUTS.forget(p); }
    public static boolean active(ServerPlayer p) { return owned(p) != null; }
    /** Read-only shared steering fence; residual leases survive ending a pulse. */
    public static boolean leased(AbstractArrow arrow) {
        if (arrow == null || !(arrow.level() instanceof ServerLevel world) || !world.getServer().isSameThread()) return true;
        Lease lease = LEASES.get(arrow.getUUID());
        return lease != null && world.getServer().overworld().getGameTime() < lease.until;
    }

    public static boolean previewing(ServerPlayer p) { Context c = owned(p); return c != null && c.placed < 0; }
    public static int affectedCount(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.arrows.size(); }
    public static void clear() { CONTEXTS.clear(); QUOTAS.clear(); LEASES.clear(); INPUTS.clear(); nextToken = 0; }

    public static void tick(ServerPlayer p) {
        Context c = owned(p);
        if (c == null) return;
        long now = now(p);
        if (!valid(c) || c.placed >= 0 && (now < c.placed || now - c.placed >= PULSE_TICKS)) { finish(c); return; }
        if (now <= c.lastTick) return;
        c.lastTick = now;
        if (c.placed < 0) {
            c.ground = lookingGround(p); c.origin = p.getEyePosition();
            // Invalid aim hides the marker immediately, but permits correcting aim while held.
            send(c, c.ground == null ? 3 : 0); return;
        }
        if (!groundValid(p, c.ground) || !loadedArea(p.serverLevel(), new AABB(c.ground.point, c.ground.point).inflate(INFLUENCE_RADIUS))) {
            finish(c); return;
        }
        prune(now);
        for (var entry : c.arrows.entrySet()) {
            AbstractArrow arrow = entry.getValue();
            Lease lease = LEASES.get(entry.getKey());
            if (lease != null && lease.owner == c && eligible(p, c.ground.point, arrow)) steer(p, c.ground.point, arrow);
        }
        Deque<Admission> quota = QUOTAS.computeIfAbsent(p.getUUID(), ignored -> new ArrayDeque<>());
        if (quota.size() < MAX_ARROWS && c.arrows.size() < MAX_ARROWS) {
            List<AbstractArrow> candidates = new ArrayList<>(SCAN_LIMIT);
            // Filter stuck/owned/nonmetal candidates before the32-arrow expensive visibility budget.
            // Native spatial traversal stays local; no chunk is loaded here.
            p.serverLevel().getEntities(EntityTypeTest.forClass(AbstractArrow.class),
                    new AABB(c.ground.point, c.ground.point).inflate(INFLUENCE_RADIUS), a -> cheapEligible(p, c.ground.point, a) && !c.arrows.containsKey(a.getUUID()) && !LEASES.containsKey(a.getUUID()), candidates, SCAN_LIMIT);
            candidates.sort(Comparator.comparingDouble(a -> a.position().distanceToSqr(c.ground.point)));
            for (AbstractArrow arrow : candidates) {
                UUID id = arrow.getUUID();
                if (quota.size() >= MAX_ARROWS || c.arrows.size() >= MAX_ARROWS) break;
                if (c.arrows.containsKey(id) || LEASES.containsKey(id) || quota.stream().anyMatch(a -> a.arrow.equals(id))
                        || !eligible(p, c.ground.point, arrow) || !steer(p, c.ground.point, arrow)) continue;
                boolean first = c.arrows.isEmpty();
                c.arrows.put(id, arrow); quota.addLast(new Admission(id, now)); LEASES.put(id, new Lease(c, now + PULSE_TICKS));
                if (first) {
                    // Provisional practice gain only for real admitted physics, once per pulse.
                    AbilityProgress state = AbilityStorage.get(p);
                    if (state == c.identity.state() && state.category.equals("electromaster") && state.learned(ID)) {
                        state.addExperience(ID, .005); AbilityStorage.save(p);
                    }
                }
            }
        }
        if (now % 4 == 0) send(c, 1);
    }

    private static boolean cheapEligible(ServerPlayer p, Vec3 point, AbstractArrow arrow) {
        if (arrow == null || MultiAxisReflectionArrowBridge.leased(arrow) || TrajectoryReturnArrowBridge.leased(arrow) || ShortInterceptionArrowBridge.leased(arrow)||IronSandLance.leased(arrow)||MagneticArrowSeparation.leased(arrow)||MagneticDomainArrows.leased(arrow) || ElectromasterPrototype.leased(arrow) || MagneticGroundStep.leased(arrow) || arrow.isRemoved() || !arrow.isAlive() || arrow.level() != p.serverLevel()
                || p.serverLevel().getEntity(arrow.getUUID()) != arrow
                || arrow.inGround || arrow.isNoPhysics() || !arrow.getType().is(METAL)
                || arrow.getClass() != Arrow.class && arrow.getClass() != SpectralArrow.class) return false;
        Entity owner = arrow.getOwner();
        if (owner == p || owner != null && p.isAlliedTo(owner)
                || owner instanceof Player && !AcademyConfig.ATTACK_PLAYERS.get()
                || owner instanceof ServerPlayer other && !p.canHarmPlayer(other)) return false;
        Vec3 at = arrow.position(), velocity = arrow.getDeltaMovement();
        if (!finite(at) || !finite(velocity) || velocity.lengthSqr() < .001 || velocity.lengthSqr() > MAX_ARROW_SPEED * MAX_ARROW_SPEED
                || at.distanceToSqr(point) > INFLUENCE_RADIUS * INFLUENCE_RADIUS || at.distanceToSqr(point) < .04) return false;
        return true;
    }
    private static boolean eligible(ServerPlayer p, Vec3 point, AbstractArrow arrow) {
        if (!cheapEligible(p, point, arrow)) return false;
        Vec3 at = arrow.position();
        return permitted(p, BlockPos.containing(at)) && loadedArea(p.serverLevel(), new AABB(at, point))
                && p.serverLevel().clip(new ClipContext(at, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }
    private static boolean steer(ServerPlayer p, Vec3 point, AbstractArrow arrow) {
        Vec3 velocity = arrow.getDeltaMovement(), attraction = point.subtract(arrow.position()).normalize();
        // <=0.18 radians per native tick; preserve speed exactly, never accelerate or teleport.
        double speed = velocity.length(); Vec3 forward = velocity.scale(1 / speed);
        double dot = Math.max(-1, Math.min(1, forward.dot(attraction)));
        double angle = Math.acos(dot);
        if (!Double.isFinite(angle) || angle < 1e-6) return false;
        Vec3 side = attraction.subtract(forward.scale(dot));
        if (side.lengthSqr() < 1e-12) { side = forward.cross(new Vec3(0, 1, 0)); if (side.lengthSqr() < 1e-12) side = forward.cross(new Vec3(1, 0, 0)); }
        double turn = Math.min(.18, angle);
        Vec3 changed = forward.scale(Math.cos(turn)).add(side.normalize().scale(Math.sin(turn))).normalize().scale(speed);
        Vec3 next = arrow.position().add(changed);
        if (!finite(changed) || !permitted(p, BlockPos.containing(next)) || !loadedArea(p.serverLevel(), new AABB(arrow.position(), next))) return false;
        // Native arrow collision remains authoritative, including walls/ground on its next movement.
        arrow.setDeltaMovement(changed); arrow.hasImpulse = true; arrow.hurtMarked = true;
        return true;
    }
    private static Ground lookingGround(ServerPlayer p) {
        Vec3 start = p.getEyePosition(), look = p.getLookAngle();
        if (!finite(start) || !finite(look) || look.lengthSqr() < .5) return null;
        Vec3 end = start.add(look.normalize().scale(PLACEMENT_RANGE));
        // A visible nearby support may precede the world-height boundary. Check only
        // that bounded native ray, rather than rejecting its unreachable void suffix.
        var bounded = cn.academy.port.highpower.core.ConductiveCircuitRules.rayEndWithinHeight(
                new cn.academy.port.highpower.core.ConductiveCircuitRules.Point(start.x,start.y,start.z),
                new cn.academy.port.highpower.core.ConductiveCircuitRules.Point(end.x,end.y,end.z),
                p.serverLevel().getMinBuildHeight(),p.serverLevel().getMaxBuildHeight());
        if (bounded == null) return null;
        end = new Vec3(bounded.x(),bounded.y(),bounded.z());
        if (!loadedArea(p.serverLevel(), new AABB(start, end))) return null;
        var hit = p.serverLevel().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != Direction.UP
                || start.distanceToSqr(hit.getLocation()) > PLACEMENT_RANGE * PLACEMENT_RANGE) return null;
        Ground ground = new Ground(hit.getBlockPos().immutable(), hit.getLocation().add(0, .05, 0));
        return groundValid(p, ground) && loadedArea(p.serverLevel(), new AABB(ground.point, ground.point).inflate(INFLUENCE_RADIUS)) ? ground : null;
    }
    private static boolean groundValid(ServerPlayer p, Ground g) {
        if (g == null || !permitted(p, g.block) || !permitted(p, BlockPos.containing(g.point))) return false;
        var state = p.serverLevel().getBlockState(g.block);
        if (state.getCollisionShape(p.serverLevel(), g.block).isEmpty()) return false;
        // Recheck the actual support surface; replacement/removal cannot leave a floating lure.
        var hit = p.serverLevel().clip(new ClipContext(g.point.add(0, .1, 0), g.point.add(0, -.15, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP && hit.getBlockPos().equals(g.block);
    }
    private static boolean permitted(ServerPlayer p, BlockPos block) {
        ServerLevel world = p.serverLevel();
        return !world.isOutsideBuildHeight(block) && world.hasChunkAt(block)
                && world.getWorldBorder().isWithinBounds(block) && world.mayInteract(p, block);
    }
    private static boolean loadedArea(ServerLevel world, AABB box) {
        // The influence volume may cross a build-height boundary. The support
        // itself is checked separately; querying entities in its loaded chunks
        // must not reject otherwise valid ground close to the world floor.
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.maxX) || !Double.isFinite(box.minZ) || !Double.isFinite(box.maxZ)
                || !Double.isFinite(box.minY) || !Double.isFinite(box.maxY)
                || box.getXsize() > 41 || box.getZsize() > 41
                || box.maxY < world.getMinBuildHeight() || box.minY >= world.getMaxBuildHeight()) return false;
        for (int x = ((int) Math.floor(box.minX)) >> 4; x <= ((int) Math.floor(box.maxX)) >> 4; x++)
            for (int z = ((int) Math.floor(box.minZ)) >> 4; z <= ((int) Math.floor(box.maxZ)) >> 4; z++)
                if (!world.hasChunk(x, z)) return false;
        return true;
    }
    private static boolean canAct(ServerPlayer p) {
        AbilityProgress s = AbilityStorage.get(p);
        return ElectromasterPrototype.ready(p) && s.activated && s.overloadFine && s.learned(ID)
                && s.category.equals("electromaster") && Math.min(s.level, HighPowerCombat.level(p)) >= 2
                && s.level <= 5 && SkillCatalog.enabled("electromaster", ID)
                && HighPowerCombat.unsupportedReason(p).isEmpty() && finite(p.position())
                && Float.isFinite(p.getXRot()) && Float.isFinite(p.getYRot()) && Float.isFinite(p.getYHeadRot());
    }
    private static boolean valid(Context c) {
        ServerPlayer p = c.identity.owner();
        return thread(p) && c.identity.level() == p.serverLevel() && c.identity.state() == AbilityStorage.get(p)
                && (c.identity.input() == 0 || c.identity.owns(p)) && canAct(p) && now(p) >= c.started;
    }
    private static Context owned(ServerPlayer p) {
        if (!thread(p)) return null;
        Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null;
    }
    private static boolean thread(ServerPlayer p) { return p != null && p.serverLevel().getServer().isSameThread(); }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static long now(ServerPlayer p) { return p.serverLevel().getServer().overworld().getGameTime(); }
    private static void finish(Context c) {
        CONTEXTS.remove(c.identity.owner().getUUID(), c); send(c, 2);
        // Lease remains for at most20ticks, preventing another caster from replaying the same arrow.
        c.arrows.clear();
    }
    private static void prune(long now) {
        QUOTAS.values().forEach(q -> q.removeIf(a -> now < a.tick || now - a.tick >= PULSE_TICKS));
        QUOTAS.values().removeIf(Deque::isEmpty);
        LEASES.values().removeIf(l -> now >= l.until || now < l.until - PULSE_TICKS);
    }
    private static void send(Context c, int phase) {
        ServerPlayer p = c.identity.owner();
        Vec3 point = c.ground == null ? c.origin : c.ground.point;
        if (phase == 0 || phase == 3) c.audience.add(p);
        else for (ServerPlayer viewer : c.identity.level().players())
            if (viewer.position().distanceToSqr(point) <= 32 * 32 || viewer == p) c.audience.add(viewer);
        c.audience.add(p);
        Visual packet = new Visual(p.getId(), p.getUUID(), c.identity.input(), c.identity.token(), c.identity.epoch(),
                ++c.sequence, phase, c.origin, point, phase == 1 ? (int) Math.max(0, PULSE_TICKS - (now(p) - c.placed)) : 8);
        for (ServerPlayer viewer : c.audience) if (viewer.connection != null && !viewer.hasDisconnected()) PacketDistributor.sendToPlayer(viewer, packet);
    }
    /** Fixed-size S2C state only. Client coordinates never participate in targeting or physics. */
    public record Visual(int entity, UUID owner, long input, long token, long epoch, long sequence, int phase, Vec3 origin, Vec3 point, int remaining) implements CustomPacketPayload {
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_charge_decoy"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.token); b.writeLong(v.epoch);
            b.writeLong(v.sequence); b.writeByte(v.phase); b.writeDouble(v.origin.x); b.writeDouble(v.origin.y); b.writeDouble(v.origin.z);
            b.writeDouble(v.point.x); b.writeDouble(v.point.y); b.writeDouble(v.point.z); b.writeByte(v.remaining);
        }, b -> new Visual(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readUnsignedByte(),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readUnsignedByte()));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) {
            e.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet));
        }
    }
    @SubscribeEvent public static void playerTick(PlayerTickEvent.Post e) { if (e.getEntity() instanceof ServerPlayer p) tick(p); }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post e) { prune(e.getServer().overworld().getGameTime()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { if (e.getOriginal() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void death(LivingDeathEvent e) { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { clear(); }
}
