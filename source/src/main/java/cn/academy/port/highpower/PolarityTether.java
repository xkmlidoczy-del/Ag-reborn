package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.PolarityTetherRules;
import cn.academy.port.highpower.core.PolarityTetherRules.Phase;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;
import java.util.function.Consumer;

/** NEW-113: one self-only velocity constraint. No teleport, damage, world edits, chunk loads or fall-distance reset. */
@EventBusSubscriber(modid = "academy")
public final class PolarityTether {
    public static final String ID = "polarity_tether";
    public static final TagKey<Block> MAGNETIC_ANCHORS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("academy", "polarity_tether_anchors"));
    private static final double MAX_MOVEMENT_AREA_SPAN = 33;
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(long epoch, long input) {}
    private record Anchor(BlockPos block, BlockState state, Direction face, Vec3 point) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final Anchor anchor;
        final int preset;
        final long revision, started;
        final Set<ServerPlayer> audience = new LinkedHashSet<>();
        Phase phase = Phase.PENDING;
        long lastTap, latestInput, lastTick = Long.MIN_VALUE, sequence;
        Context(ServerPlayer p, HighPowerInput.Input input, Anchor anchor) {
            this.identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input; this.anchor = anchor; this.preset = identity.state().presets.current();
            this.latestInput = input.input(); this.revision = identity.state().presets.revision(); this.started = this.lastTap = now(p);
        }
    }
    private PolarityTether() {}

    /** Fresh press only; client supplies no target, motion, cost or level. Release has no gameplay effect. */
    public static boolean tap(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input) || AbilityConsumption.busy(p)) return false;
        Watermark old = INPUTS.get(p.getUUID());
        if (old != null && old.epoch == input.epoch() && input.input() <= old.input) return false;
        INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        if (!canAct(p) || !ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))) return false;
        Context c = CONTEXTS.get(p.getUUID());
        if (c != null) {
            if (!valid(c) || c.identity.owner() != p) { finish(c); return false; }
            if (c.input.slot() != input.slot() || now(p) <= c.lastTap || c.phase == Phase.PENDING) return false;
            c.lastTap = now(p); c.latestInput = input.input(); c.phase = PolarityTetherRules.tap(c.phase, p.isShiftKeyDown());
            if (c.phase == Phase.ENDED) finish(c); else send(c);
            return true;
        }
        if (p.isShiftKeyDown() || !AbilityStorage.get(p).canUse(ID) || nextToken == Long.MAX_VALUE) return false;
        Anchor anchor = lookingAnchor(p);
        if (anchor == null) return false;
        c = new Context(p, input, anchor); CONTEXTS.put(p.getUUID(), c); send(c); return true;
    }
    /** Bounded UI/focus cancel. Retires delayed starts; an old cancel cannot terminate a newer cast or tap. */
    public static boolean abort(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p,input)) return false;
        var old = INPUTS.get(p.getUUID());
        if (old == null || old.epoch != input.epoch() || old.input < input.input())
            INPUTS.put(p.getUUID(),new Watermark(input.epoch(),input.input()));
        Context c = owned(p);
        if (c == null || c.input.epoch() != input.epoch() || c.input.slot() != input.slot()
                || c.input.input() > input.input() || c.latestInput > input.input()) return false;
        finish(c); return true;
    }
    public static boolean active(ServerPlayer p) { return owned(p) != null; }
    public static Phase phase(ServerPlayer p) { Context c = owned(p); return c == null ? Phase.ENDED : c.phase; }
    /** Invalidation never brakes: preserve real knockback, gravity and other existing inertia. */
    public static void abort(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c); }
    public static void remove(ServerPlayer p) { if (!thread(p)) return; abort(p); INPUTS.remove(p.getUUID()); }
    public static void clear() { CONTEXTS.clear(); INPUTS.clear(); /* Tokens never rewind within this JVM. */ }

    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null) return;
        long tick = now(p);
        // Anchor/identity checks precede every velocity write, including emergency braking.
        if (!valid(c) || PolarityTetherRules.expired(c.started, tick)) { debug(c, "invalid/expired canAct=" + canAct(p) + " anchor=" + anchorValid(p, c.anchor)); finish(c); return; }
        if (tick <= c.lastTick) return;
        c.lastTick = tick;
        if (c.phase == Phase.PENDING) {
            if (!PolarityTetherRules.canCommit(c.started, tick)) return;
            int level = ElectromasterPrototype.effectiveLevel(p);
            if (!ElectromasterPrototype.trySpend(p, ID, HighPowerGrowth.values(p).tetherCost(), 1)) { finish(c); return; }
            c.phase = Phase.ANCHORED; send(c); return;
        }
        if (c.phase == Phase.PULLING || c.phase == Phase.BRAKING) {
            Vec3 velocity = p.getDeltaMovement();
            var next = c.phase == Phase.PULLING
                    ? PolarityTetherRules.pull(vector(velocity), vector(c.anchor.point.subtract(p.getEyePosition())), p.getGravity())
                    : PolarityTetherRules.brake(vector(velocity));
            if (next == null) { debug(c, "no next velocity"); finish(c); return; }
            Vec3 changed = new Vec3(next.x(), next.y(), next.z());
            // On the floor the pull's small gravity-compensated downward step counted as a collision and ended the tether at once:
            // when only that step collides, keep the horizontal pull.
            if (!movementPermitted(p, changed) && changed.y < 0 && movementPermitted(p, new Vec3(changed.x, 0, changed.z))) changed = new Vec3(changed.x, 0, changed.z);
            if (!movementPermitted(p, changed)) { debug(c, "movement blocked " + changed); finish(c); return; }
            p.setDeltaMovement(changed); p.hasImpulse = true; p.hurtMarked = true;
            if (c.phase == Phase.BRAKING && changed.lengthSqr() < 1e-12) { finish(c); return; }
        }
        if (tick % 4 == 0) send(c);
    }
    private static void debug(Context c, String why) {
        if (Boolean.getBoolean("academy.debug.lease")) com.mojang.logging.LogUtils.getLogger().info("TETHER_END phase={} {}", c.phase, why);
    }
    private static boolean canAct(ServerPlayer p) {
        AbilityProgress s = AbilityStorage.get(p);
        return ElectromasterPrototype.ready(p) && s.activated && s.overloadFine && s.learned(ID)
                && s.category.equals("electromaster") && ElectromasterPrototype.effectiveLevel(p) >= 1
                && SkillCatalog.enabled("electromaster", ID) && HighPowerCombat.unsupportedReason(p).isEmpty()
                && !AbilityConsumption.busy(p) && !competingMovement(p) && !p.isPassenger() && !p.isSleeping() && !p.isFallFlying()
                && !p.getAbilities().flying && !p.isInWaterOrBubble() && !p.isInLava()
                && !p.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)
                && !p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)
                && Double.isFinite(p.getGravity()) && p.getGravity() >= 0 && p.getGravity() <= PolarityTetherRules.MAX_GRAVITY
                && finite(p.position()) && finite(p.getDeltaMovement())
                && Float.isFinite(p.getXRot()) && Float.isFinite(p.getYRot());
    }
    private static boolean competingMovement(ServerPlayer p) {
        return WindPressureLift.active(p) || MagneticGroundStep.active(p) || cn.academy.port.skill.MagMovement.active(p) || cn.academy.port.skill.VecAccel.active(p)
                || cn.academy.port.skill.JetEngine.active(p) || cn.academy.port.skill.StormWing.active(p)
                || cn.academy.port.skill.MarkTeleport.active(p) || cn.academy.port.skill.PenetrateTeleport.active(p)
                || cn.academy.port.skill.Flashing.active(p);
    }
    private static boolean valid(Context c) {
        ServerPlayer p = c.identity.owner();
        return thread(p) && c.identity.owns(p) && HighPowerBracerSession.authenticated(p, c.input)
                && canAct(p) && c.preset == c.identity.state().presets.current()
                && c.revision == c.identity.state().presets.revision()
                && ID.equals(c.identity.state().presets.currentSkill(c.input.slot())) && anchorValid(p, c.anchor);
    }
    /** The anchor reach grows with the level (see MobilityScale); pull speed and every per-tick limit stay as they were. */
    private static double range(ServerPlayer p) {
        try { return PolarityTetherRules.RANGE * cn.academy.port.highpower.core.MobilityScale.reach(HighPowerGrowth.values(p).position()); }
        catch (IllegalStateException | IllegalArgumentException unavailable) { return PolarityTetherRules.RANGE; }
    }
    private static Anchor lookingAnchor(ServerPlayer p) {
        Vec3 start = p.getEyePosition(), look = p.getLookAngle();
        if (!finite(start) || !finite(look) || look.lengthSqr() < .5) return null;
        double reach = range(p);
        Vec3 end = start.add(look.normalize().scale(reach));
        if (!loadedArea(p.serverLevel(), new AABB(start, end), reach)) return null;
        var hit = p.serverLevel().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.BLOCK || !permitted(p, hit.getBlockPos())) return null;
        Vec3 point = hit.getLocation();
        Anchor result = new Anchor(hit.getBlockPos().immutable(), p.serverLevel().getBlockState(hit.getBlockPos()), hit.getDirection(), point);
        return anchorValid(p, result) ? result : null;
    }
    private static boolean anchorValid(ServerPlayer p, Anchor a) {
        if (a == null || !permitted(p, a.block) || !finite(a.point)) return false;
        var state = p.serverLevel().getBlockState(a.block);
        if (state != a.state || !state.is(MAGNETIC_ANCHORS) || state.getCollisionShape(p.serverLevel(), a.block).isEmpty()) return false;
        Vec3 start = p.getEyePosition();
        double reach = range(p);
        if (!finite(start) || start.distanceToSqr(a.point) > reach * reach
                || !loadedArea(p.serverLevel(), new AABB(start, a.point), reach)) return false;
        Vec3 inward = new Vec3(a.face.getStepX(), a.face.getStepY(), a.face.getStepZ()).scale(-.025);
        var hit = p.serverLevel().clip(new ClipContext(start, a.point.add(inward), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(a.block)
                && hit.getDirection() == a.face && hit.getLocation().distanceToSqr(a.point) < .01;
    }
    private static boolean movementPermitted(ServerPlayer p, Vec3 velocity) {
        AABB swept = p.getBoundingBox().expandTowards(velocity).deflate(1e-7);
        ServerLevel world = p.serverLevel();
        if (!finite(velocity) || !loadedArea(world, swept, MAX_MOVEMENT_AREA_SPAN) || !world.getWorldBorder().isWithinBounds(swept)
                || !world.noCollision(p, swept)) return false;
        // At <=.6 speed the entire swept actor box spans a small, fixed number of blocks.
        for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(swept.minX, swept.minY, swept.minZ),
                BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))) if (!permitted(p, block)) return false;
        return true;
    }
    private static boolean permitted(ServerPlayer p, BlockPos pos) {
        ServerLevel world = p.serverLevel();
        return !world.isOutsideBuildHeight(pos) && world.hasChunkAt(pos) && world.getWorldBorder().isWithinBounds(pos) && world.mayInteract(p, pos);
    }
    private static boolean loadedArea(ServerLevel world, AABB box, double maxSpan) {
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.maxX) || !Double.isFinite(box.minY) || !Double.isFinite(box.maxY) || !Double.isFinite(box.minZ) || !Double.isFinite(box.maxZ)
                || box.getXsize() > maxSpan || box.getYsize() > maxSpan || box.getZsize() > maxSpan
                || box.minY < world.getMinBuildHeight() || box.maxY >= world.getMaxBuildHeight()) return false;
        for (int x = ((int)Math.floor(box.minX)) >> 4; x <= ((int)Math.floor(box.maxX)) >> 4; x++)
            for (int z = ((int)Math.floor(box.minZ)) >> 4; z <= ((int)Math.floor(box.maxZ)) >> 4; z++) if (!world.hasChunk(x,z)) return false;
        return true;
    }
    private static Context owned(ServerPlayer p) {
        if (!thread(p)) return null;
        Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null;
    }
    private static boolean thread(ServerPlayer p) { return p != null && p.serverLevel().getServer().isSameThread(); }
    private static long now(ServerPlayer p) { return p.serverLevel().getServer().overworld().getGameTime(); }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static PolarityTetherRules.Vector vector(Vec3 v) { return new PolarityTetherRules.Vector(v.x,v.y,v.z); }
    private static void finish(Context c) { CONTEXTS.remove(c.identity.owner().getUUID(), c); c.phase = Phase.ENDED; send(c); c.audience.clear(); }
    private static void send(Context c) {
        ServerPlayer p = c.identity.owner();
        for (ServerPlayer viewer : c.identity.level().players())
            if (viewer.position().distanceToSqr(c.anchor.point) <= 32*32) c.audience.add(viewer);
        c.audience.add(p);
        var packet = new Visual(p.getId(), p.getUUID(), c.identity.token(), c.identity.epoch(), ++c.sequence,
                c.phase.ordinal(), c.anchor.face.ordinal(), p.getEyePosition(), c.anchor.point);
        for (ServerPlayer viewer : c.audience) if (viewer.connection != null && !viewer.hasDisconnected() && viewer.serverLevel() == c.identity.level())
            PacketDistributor.sendToPlayer(viewer, packet);
    }
    /** Fixed bounded S2C visuals; neither endpoints nor phase are accepted from clients. */
    public record Visual(int entity, UUID owner, long token, long epoch, long sequence, int phase, int face, Vec3 origin, Vec3 anchor) implements CustomPacketPayload {
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy","hp_polarity_tether"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC = StreamCodec.of((b,v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.token); b.writeLong(v.epoch); b.writeLong(v.sequence); b.writeByte(v.phase); b.writeByte(v.face);
            b.writeDouble(v.origin.x); b.writeDouble(v.origin.y); b.writeDouble(v.origin.z);
            b.writeDouble(v.anchor.x); b.writeDouble(v.anchor.y); b.writeDouble(v.anchor.z);
        }, b -> new Visual(b.readInt(),b.readUUID(),b.readLong(),b.readLong(),b.readLong(),b.readUnsignedByte(),b.readUnsignedByte(),
                new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble())));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) {
            e.registrar("1").playToClient(Visual.TYPE,Visual.CODEC,(packet, context) -> clientReceiver.accept(packet));
        }
    }
    @SubscribeEvent public static void playerTick(PlayerTickEvent.Post e) { if (e.getEntity() instanceof ServerPlayer p) tick(p); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { if (e.getOriginal() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void death(LivingDeathEvent e) { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { clear(); }
}
