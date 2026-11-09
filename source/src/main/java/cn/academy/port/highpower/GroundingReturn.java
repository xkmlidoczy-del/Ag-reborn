package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.ExactCombatState;
import cn.academy.port.highpower.core.GroundingReturnRules;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
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

/** NEW-122. One stationary, crouched, server-owned electric defense; no generic damage or velocity hook. */
@EventBusSubscriber(modid = "academy")
public final class GroundingReturn {
    public static final String ID = "grounding_return";
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(long epoch, long input) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final Vec3 position;
        final int preset;
        final long revision, started;
        final BigDecimal capacity;
        final Set<ServerPlayer> audience = new LinkedHashSet<>();
        BigDecimal spent = BigDecimal.ZERO;
        boolean precisionUsed;
        long lastTick = Long.MIN_VALUE, sequence;
        Context(ServerPlayer p, HighPowerInput.Input input, BigDecimal capacity) {
            this.identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input; this.position = p.position(); this.preset = identity.state().presets.current();
            this.revision = identity.state().presets.revision(); this.started = now(p); this.capacity = capacity;
        }
    }
    /** Opaque identity captured while native HP is still live, never client supplied. */
    static final class Capture {
        private final Context context;
        private Capture(Context context) { this.context = context; }
    }
    private GroundingReturn() {}

    /** A tap starts sixty ticks. Ordinary key release does not cancel the stance. */
    public static boolean start(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input) || AbilityConsumption.busy(p)) return false;
        var old = INPUTS.get(p.getUUID());
        if (old != null && old.epoch == input.epoch() && input.input() <= old.input) return false;
        INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        if (!canAct(p, false) || !ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))
                || !AbilityStorage.get(p).canUse(ID) || nextToken == Long.MAX_VALUE) return false;
        Context previous = owned(p);
        if (previous != null) { if (!valid(previous, false)) finish(previous); else return false; }
        var state = HighPowerCombat.exactState(p);
        if (state.isEmpty() || state.get().isDead() || state.get().shield().signum() <= 0) return false;
        // This defense's payment is actual shield absorption. Zero CP, bounded recast cooldown.
        if (!ElectromasterPrototype.trySpend(p, ID, 0, HighPowerGrowth.values(p).groundingCooldown())) return false;
        var c = new Context(p, input, state.get().maxShield());
        CONTEXTS.put(p.getUUID(), c); send(c, 0); return true;
    }
    /** UI/focus cancel fences a delayed matching start; older cancels cannot end a newer stance. */
    public static boolean abort(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        var old = INPUTS.get(p.getUUID());
        if (old == null || old.epoch != input.epoch() || old.input < input.input())
            INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        Context c = owned(p);
        if (c == null || c.input.epoch() != input.epoch() || c.input.slot() != input.slot()
                || c.input.input() > input.input()) return false;
        finish(c); return true;
    }
    public static void abort(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c); }
    public static void remove(ServerPlayer p) { if (!thread(p)) return; abort(p); INPUTS.remove(p.getUUID()); }
    public static void clear() { CONTEXTS.clear(); INPUTS.clear(); }
    public static boolean active(ServerPlayer p) {
        Context c = owned(p);
        if (c == null) return false;
        if (!valid(c, false)) { finish(c); return false; }
        return true;
    }
    /** Called at the Incoming stage only for explicitly reviewed electrical sources. */
    static Capture capture(ServerPlayer p) {
        Context c = owned(p);
        if (c == null) return null;
        if (!valid(c, false)) { finish(c); return null; }
        return new Capture(c);
    }
    /** Post may observe zero normalized native HP. The caller supplies the still-live exact ledger. */
    static GroundingReturnRules.Result plan(ServerPlayer p, Capture captured, ExactCombatState before, BigDecimal damage) {
        if (captured == null || before.isDead()) return null;
        Context c = captured.context;
        if (owned(p) != c || !valid(c, true)) { if (owned(p) == c) finish(c); return null; }
        return GroundingReturnRules.absorb(before, damage, c.capacity, c.spent, c.precisionUsed,
                GroundingReturnRules.precise(c.started, now(p)));
    }
    /** A receipt cannot survive owner/position/world/preset replacement inside an XP or damage observer. */
    static boolean matches(ServerPlayer p, Capture captured) {
        return captured != null && owned(p) == captured.context && valid(captured.context, true);
    }
    /** Called only after HighPowerCombat's single authoritative ledger write and normalized HP sync. */
    static void committed(ServerPlayer p, Capture captured, GroundingReturnRules.Result result) {
        if (captured == null || result == null || result.absorbed().signum() <= 0) return;
        Context c = captured.context;
        if (owned(p) != c) return;
        c.spent = result.spent(); c.precisionUsed = result.precisionUsed();
        // Absorption, not merely activation, is the sole successful-action witness.
        var s = c.identity.state(); s.addExperience(ID, .005); AbilityStorage.save(p);
        if (owned(p) != c) return;
        if (!valid(c, true)) { finish(c); return; }
        send(c, 1);
    }
    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null) return;
        if (!valid(c, false)) { finish(c); return; }
        long tick = now(p); if (tick <= c.lastTick) return; c.lastTick = tick;
        if ((tick - c.started) % 4 == 0) send(c, 0);
    }
    private static boolean canAct(ServerPlayer p, boolean insideDamage) {
        if (!thread(p) || p.connection == null || p.hasDisconnected() || p.isRemoved() || p.isSpectator()
                || !insideDamage && !p.isAlive() || p.serverLevel().getEntity(p.getId()) != p) return false;
        var s = AbilityStorage.get(p);
        return HighPowerCombat.enabled(p) && s.category.equals("electromaster") && s.activated && s.overloadFine
                && !s.interfering && s.learned(ID) && s.level <= 5 && ElectromasterPrototype.effectiveLevel(p) >= 2
                && SkillCatalog.enabled("electromaster", ID) && HighPowerCombat.unsupportedReason(p).isEmpty()
                && !AbilityConsumption.busy(p) && p.isShiftKeyDown() && p.onGround() && !p.isPassenger()
                && !p.isSleeping() && !p.isFallFlying() && !p.getAbilities().flying
                && !p.isInWaterOrBubble() && !p.isInLava() && finite(p.position()) && supportedGround(p);
    }
    private static boolean valid(Context c, boolean insideDamage) {
        ServerPlayer p = c.identity.owner();
        return c.identity.owns(p) && canAct(p, insideDamage) && GroundingReturnRules.active(c.started, now(p))
                && c.preset == c.identity.state().presets.current() && c.revision == c.identity.state().presets.revision()
                && ID.equals(c.identity.state().presets.currentSkill(c.input.slot()))
                && p.position().distanceToSqr(c.position) <= GroundingReturnRules.MOVEMENT_TOLERANCE * GroundingReturnRules.MOVEMENT_TOLERANCE;
    }
    /** Confirm an actual loaded collision surface immediately beneath the feet, not a forged onGround bit. */
    private static boolean supportedGround(ServerPlayer p) {
        Vec3 point = p.position(); BlockPos below = BlockPos.containing(point.add(0, -.05, 0));
        var world = p.serverLevel();
        if (world.isOutsideBuildHeight(below) || !world.hasChunkAt(below) || !world.getWorldBorder().isWithinBounds(below)) return false;
        var hit = world.clip(new ClipContext(point.add(0, .05, 0), point.add(0, -.10, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP
                && Math.abs(hit.getLocation().y - point.y) <= .025;
    }
    private static Context owned(ServerPlayer p) {
        if (!thread(p)) return null;
        Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null;
    }
    private static boolean thread(ServerPlayer p) { return p != null && p.serverLevel().getServer().isSameThread(); }
    private static long now(ServerPlayer p) { return p.serverLevel().getServer().overworld().getGameTime(); }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static void finish(Context c) { CONTEXTS.remove(c.identity.owner().getUUID(), c); send(c, 2); c.audience.clear(); }
    private static void send(Context c, int phase) {
        ServerPlayer p = c.identity.owner();
        for (ServerPlayer viewer : c.identity.level().players())
            if (viewer.position().distanceToSqr(c.position) <= 32 * 32) c.audience.add(viewer);
        c.audience.add(p);
        int remaining = phase == 2 ? 0 : (int)Math.max(0, Math.min(GroundingReturnRules.DURATION_TICKS,
                GroundingReturnRules.DURATION_TICKS - (now(p) - c.started)));
        var packet = new Visual(c.input.actor(), p.getUUID(), c.input.input(), c.input.epoch(), ++c.sequence, phase,
                c.position, remaining);
        for (ServerPlayer viewer : c.audience)
            if (viewer.connection != null && !viewer.hasDisconnected() && viewer.serverLevel() == c.identity.level()) PacketDistributor.sendToPlayer(viewer, packet);
    }
    public record Visual(int entity, UUID owner, long input, long epoch, long sequence, int phase, Vec3 point, int remaining) implements CustomPacketPayload {
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_grounding_return"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b,v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.epoch); b.writeLong(v.sequence); b.writeByte(v.phase);
            b.writeDouble(v.point.x); b.writeDouble(v.point.y); b.writeDouble(v.point.z); b.writeByte(v.remaining);
        }, b -> new Visual(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readUnsignedByte(),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readUnsignedByte()));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) {
            e.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet));
        }
    }
    @SubscribeEvent public static void playerTick(PlayerTickEvent.Post e) { if (e.getEntity() instanceof ServerPlayer p) tick(p); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { if (e.getOriginal() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void death(LivingDeathEvent e) { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { clear(); }
}
