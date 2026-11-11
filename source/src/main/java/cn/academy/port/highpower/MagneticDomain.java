package cn.academy.port.highpower;

import cn.academy.port.core.SkillTuning;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.MagneticDomainClock;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW152: paid mobile domain. Its native effects require an exact live, current paid owner. */
@EventBusSubscriber(modid = "academy")
public final class MagneticDomain {
    public static final String ID = "magnetic_domain", PAYMENT_KEY = "academy:magnetic_domain_payment";
    public static final int ACTIVE = 0, HIT = 1, REFRACT = 2, ENDED = 3, PERIOD = 20, POST_COOLDOWN = 4;
    public static final double RADIUS = SkillTuning.number("magnetic_domain", "radius"), CP_COST = 1;
    private static final String BUDGET_KEY = "academy:electromaster_prototype", CLOCK = "next_" + ID;
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static long nextToken;
    // A native server frame has one time even before its world gameTime increments. Anchoring to
    // persisted overworld time keeps fences restart-safe without a pre/world tick benefit gap.
    private static final Map<net.minecraft.server.MinecraftServer, MagneticDomainClock> CLOCKS = new WeakHashMap<>();
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(long epoch, long input) {}
    private record Fence(long observed, long until, long stopUntil, String dimension) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final int preset;
        final long revision, started;
        final Set<ServerPlayer> audience = new LinkedHashSet<>();
        long paidUntil, lastTick = -1, sequence;
        boolean paying;
        net.minecraft.world.effect.MobEffectInstance inheritedTeamPulse;
        Context(ServerPlayer p, HighPowerInput.Input input) {
            inheritedTeamPulse = MagneticDomainTeamSlowFall.ownedTeamPulse(p);
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision(); started = now(p);
        }
    }
    /** One-use opaque exact-session damage receipt. All directions are eligible; the bridge owns source review. */
    public static final class AbsorptionSnapshot {
        private final Context context;
        private final long tick;
        private final Vec3 position, impact;
        private boolean committed;
        private AbsorptionSnapshot(Context c, Vec3 source) {
            context = c; tick = now(c.identity.owner()); position = c.identity.owner().position();
            Vec3 center = center(c.identity.owner()), direction = source == null ? Vec3.ZERO : source.subtract(center);
            impact = direction.lengthSqr() < 1e-8 ? center : center.add(direction.normalize().scale(.9));
        }
    }
    private MagneticDomain() {}
    public static boolean toggle(ServerPlayer p, String wire) {
        HighPowerInput.Input input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input) || AbilityConsumption.busy(p)) return false;
        Watermark old = INPUTS.get(p.getUUID());
        if (old != null && old.epoch == input.epoch() && input.input() <= old.input) return false;
        INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        if (!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))) return false;
        Context previous = CONTEXTS.get(p.getUUID());
        if (previous != null) {
            boolean same = previous.identity.owner() == p && structurallyValid(previous, false);
            finish(previous); if (same) return true;
        }
        Fence fence = readFence(p);
        if (HighPowerCombat.enabled(p) && AbilityStorage.get(p).learned(ID) && !MagneticDomainReserve.available(p)) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("academy.highpower.magnetic_domain.reserve_unavailable"), true);
            return false;
        }
        if (!canAct(p, false) || nextToken == Long.MAX_VALUE || fence == null) return false;
        if (now(p) < fence.until || now(p) < fence.stopUntil) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("academy.highpower.magnetic_domain.payment_wait", Math.max(fence.until, fence.stopUntil) - now(p)), true);
            return false;
        }
        if (!AbilityStorage.get(p).canUse(ID)) return false;
        Context c = new Context(p, input); CONTEXTS.put(p.getUUID(), c);
        if (!pay(c)) { finish(c); return false; }
        if (!current(c, false)) { finish(c); return false; }
        send(c, ACTIVE, center(p));
        return true;
    }
    public static boolean start(ServerPlayer p, String wire) { return toggle(p, wire); }
    public static boolean abort(ServerPlayer p, String wire) {
        HighPowerInput.Input input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Watermark old = INPUTS.get(p.getUUID());
        if (old == null || old.epoch != input.epoch() || input.input() > old.input) INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        Context c = owned(p);
        if (c == null || c.input.epoch() != input.epoch() || c.input.slot() != input.slot() || c.input.input() > input.input()) return false;
        finish(c); return true;
    }
    public static void abort(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c); }
    public static void remove(ServerPlayer p) { if (thread(p)) { abort(p); INPUTS.remove(p.getUUID()); } }
    public static boolean active(ServerPlayer p) {
        Context c = owned(p); if (c == null) return false;
        if (!structurallyValid(c, true)) { finish(c); return false; }
        return current(c, true);
    }
    /** Recovery cannot use the tiny observer-order gap before this frame's paid upkeep attempt. */
    public static boolean blocksRecovery(ServerPlayer p) { Context c = owned(p); return c != null && c.paidUntil > 0 && structurallyValid(c, true); }
    public static boolean renewalPending(ServerPlayer p) { Context c = owned(p); return c != null && c.paidUntil > 0 && now(p) >= c.paidUntil && structurallyValid(c, true); }
    public static long paidUntil(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.paidUntil; }
    public static int refractedCount(ServerPlayer p) { return MagneticDomainArrows.count(p); }
    public static AbsorptionSnapshot capture(ServerPlayer p, Vec3 source, Vec3 facing) {
        Context c = owned(p);
        return c != null && current(c, false) && (source == null || finite(source)) ? new AbsorptionSnapshot(c, source) : null;
    }
    public static boolean matches(ServerPlayer p, AbsorptionSnapshot capture) {
        return capture != null && !capture.committed && owned(p) == capture.context && current(capture.context, true)
                && now(p) == capture.tick && p.position().equals(capture.position);
    }
    public static void committed(ServerPlayer p, AbsorptionSnapshot capture, BigDecimal absorbed) {
        if (absorbed == null || absorbed.signum() <= 0 || !matches(p, capture)) return;
        capture.committed = true; send(capture.context, HIT, capture.impact);
    }
    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null) return;
        if (!structurallyValid(c, false)) { finish(c); return; }
        long tick = now(p); if (tick <= c.lastTick) return; c.lastTick = tick;
        if (tick >= c.paidUntil && !pay(c)) { finish(c); return; }
        if (!current(c, false)) { finish(c); return; }
        if (!awaitInheritedTeamPulse(c))
            MagneticDomainSlowFall.refresh(p, c, () -> current(c, false));
        if (!current(c, false)) { finish(c); return; }
        MagneticDomainArrows.tick(p, c, () -> current(c, false), point -> { if (current(c, false)) send(c, REFRACT, point); });
        if (current(c, false) && (tick - c.started) % 4 == 0) send(c, ACTIVE, center(p));
    }
    /** This exact pulse belongs to the newly paid self context, not revocable teammate consent. */
    private static boolean awaitInheritedTeamPulse(Context c) {
        ServerPlayer p = c.identity.owner();
        MagneticDomainTeamSlowFall.releaseRecipient(p);
        var inherited = c.inheritedTeamPulse;
        if (inherited != null && p.getEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING) == inherited
                && inherited.getDuration() > 0 && inherited.getDuration() <= MagneticDomainSlowFall.DURATION)
            return true;
        c.inheritedTeamPulse = null;
        return false;
    }
    /** Package-local opaque key; teammates cannot create or extend a paid domain. */
    static Object teamLeaseKey(ServerPlayer owner) {
        Context c = owned(owner);
        return c != null && current(c, false) ? c : null;
    }
    static boolean teamLeaseCurrent(ServerPlayer owner, Object key) {
        Context c = owned(owner);
        return c != null && c == key && current(c, false);
    }
    /** An expired paid window is never a benefit lease. A new activation pays only after the old window expires. */
    private static boolean pay(Context c) {
        ServerPlayer p = c.identity.owner(); long tick = now(p);
        Fence previous = readFence(p);
        if (c.paying || !structurallyValid(c, false) || previous == null || tick < previous.until || tick < previous.stopUntil
                || ElectromasterPrototype.prototypeCp(p) < CP_COST) return false;
        c.paying = true;
        try {
            // trySpend's ordinary clock is dimension-local. Translate only our own key after a verified
            // dimension change and after BOTH global fences expire; a relog cannot clear this boundary.
            String dimension = p.serverLevel().dimension().location().toString();
            CompoundTag budget = p.getPersistentData().getCompound(BUDGET_KEY);
            boolean translate = !previous.dimension.isEmpty() && !previous.dimension.equals(dimension);
            Tag oldClock = budget.contains(CLOCK) ? budget.get(CLOCK).copy() : null;
            if (translate) { budget.putLong(CLOCK, p.serverLevel().getGameTime()); p.getPersistentData().put(BUDGET_KEY, budget); }
            if (!ElectromasterPrototype.trySpend(p, ID, CP_COST, 1)) {
                if (translate) { CompoundTag restore = p.getPersistentData().getCompound(BUDGET_KEY); if (oldClock == null) restore.remove(CLOCK); else restore.put(CLOCK, oldClock); p.getPersistentData().put(BUDGET_KEY, restore); }
                return false;
            }
            // Closed shared high-power transaction has no skill callbacks. Persist before any effect/event/packet.
            writeFence(p, new Fence(tick, tick + PERIOD, previous.stopUntil, dimension));
            c.paidUntil = tick + PERIOD;
            return owned(p) == c && structurallyValid(c, false);
        } finally { c.paying = false; }
    }
    private static Fence readFence(ServerPlayer p) {
        long tick = now(p); if (!clock(tick)) return null;
        CompoundTag data = p.getPersistentData();
        if (!data.contains(PAYMENT_KEY)) return new Fence(tick, 0, 0, "");
        if (!data.contains(PAYMENT_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag t = data.getCompound(PAYMENT_KEY);
        if (!t.getAllKeys().equals(Set.of("version", "observed", "until", "stopUntil", "dimension"))
                || !t.contains("version", Tag.TAG_INT) || t.getInt("version") != 1 || !t.contains("observed", Tag.TAG_LONG)
                || !t.contains("until", Tag.TAG_LONG) || !t.contains("stopUntil", Tag.TAG_LONG) || !t.contains("dimension", Tag.TAG_STRING)
                || !clock(t.getLong("observed")) || t.getLong("observed") > tick || t.getLong("until") < 0
                || t.getLong("until") > t.getLong("observed") + PERIOD || t.getLong("stopUntil") < 0
                || t.getLong("stopUntil") > t.getLong("observed") + PERIOD || t.getString("dimension").length() > 256) return null;
        return new Fence(t.getLong("observed"), t.getLong("until"), t.getLong("stopUntil"), t.getString("dimension"));
    }
    private static void writeFence(ServerPlayer p, Fence f) {
        CompoundTag t = new CompoundTag(); t.putInt("version", 1); t.putLong("observed", f.observed); t.putLong("until", f.until);
        t.putLong("stopUntil", f.stopUntil); t.putString("dimension", f.dimension); p.getPersistentData().put(PAYMENT_KEY, t);
    }
    private static boolean canAct(ServerPlayer p, boolean insideDamage) {
        if (!thread(p) || p.connection == null || p.hasDisconnected() || p.isRemoved() || p.isSpectator() || !insideDamage && !p.isAlive()
                || p.serverLevel().getEntity(p.getId()) != p || p.serverLevel().getEntity(p.getUUID()) != p || p.getAbilities().instabuild
                || p.noPhysics || p.isPassenger() || p.isSleeping()) return false;
        var s = AbilityStorage.get(p);
        return HighPowerCombat.enabled(p) && s.category.equals("electromaster") && s.activated && s.overloadFine && !s.interfering
                && s.learned(ID) && s.learned(PotentialBuffer.ID) && s.level == 5 && ElectromasterPrototype.effectiveLevel(p) == 5
                && SkillCatalog.enabled("electromaster", ID) && MagneticDomainReserve.available(p) && HighPowerCombat.unsupportedReason(p).isEmpty() && !AbilityConsumption.busy(p)
                && finite(p.position()) && finite(p.getLookAngle()) && finite(p.getDeltaMovement())
                && Float.isFinite(p.getYRot()) && Float.isFinite(p.getXRot()) && clock(now(p));
    }
    private static boolean structurallyValid(Context c, boolean insideDamage) {
        ServerPlayer p = c.identity.owner();
        return owned(p) == c && c.identity.owns(p) && canAct(p, insideDamage) && now(p) >= c.started
                && c.preset == c.identity.state().presets.current() && c.revision == c.identity.state().presets.revision()
                && ID.equals(c.identity.state().presets.currentSkill(c.input.slot()));
    }
    private static boolean current(Context c, boolean insideDamage) {
        if (c.paying || !structurallyValid(c, insideDamage) || now(c.identity.owner()) >= c.paidUntil) return false;
        Fence f = readFence(c.identity.owner()); return f != null && f.until == c.paidUntil && now(c.identity.owner()) >= f.observed;
    }
    private static Context owned(ServerPlayer p) { if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null; }
    private static boolean thread(ServerPlayer p) { return p != null && p.server != null && p.server.isSameThread(); }
    private static boolean clock(long tick) { return tick >= 0 && tick <= Long.MAX_VALUE - PERIOD; }
    private static long now(ServerPlayer p) { return serverTime(p.server); }
    static long serverTime(net.minecraft.server.MinecraftServer server) {
        MagneticDomainClock anchor = CLOCKS.get(server);
        return anchor == null ? -1 : anchor.at(server.getTickCount());
    }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    static Vec3 center(ServerPlayer p) { return p.position().add(0, 1, 0); }
    private static void finish(Context c) {
        ServerPlayer p = c.identity.owner(); if (!CONTEXTS.remove(p.getUUID(), c)) return;
        MagneticDomainTeamSlowFall.releaseSource(p, c);
        MagneticDomainSlowFall.release(p, c); MagneticDomainArrows.release(p, c);
        // Failed/unpaid activation never changes the resource/cooldown ledger.
        if (c.paidUntil > 0) {
            Fence f = readFence(p); long tick = now(p);
            if (f != null) writeFence(p, new Fence(tick, f.until, Math.max(f.stopUntil, tick + POST_COOLDOWN), f.dimension));
            c.identity.state().setCooldown(ID, POST_COOLDOWN);
        }
        send(c, ENDED, center(p)); c.audience.clear();
    }
    private static void send(Context c, int phase, Vec3 point) {
        ServerPlayer p = c.identity.owner(); if (!finite(point) || !finite(p.getLookAngle())) return;
        cn.academy.port.macro.MacroVfx.emit(p,ID,phase==ENDED?cn.academy.port.macro.MacroVfx.Phase.END:cn.academy.port.macro.MacroVfx.Phase.ACTIVE,
                p.position(),p.position(),phase==ENDED?8:24,phase==ENDED?0:RADIUS);
        c.audience.removeIf(v -> v.connection == null || v.hasDisconnected() || v.isRemoved() || v.serverLevel() != c.identity.level() || v.position().distanceToSqr(p.position()) > 64 * 64);
        for (ServerPlayer viewer : c.identity.level().players()) if (viewer.position().distanceToSqr(p.position()) <= 48 * 48) c.audience.add(viewer);
        c.audience.add(p);
        Visual packet = new Visual(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.identity.epoch(), ++c.sequence,
                phase, center(p), p.getLookAngle(), point, phase != ENDED && MagneticDomainSlowFall.active(p, c), phase == ENDED ? 0 : MagneticDomainArrows.count(p));
        for (ServerPlayer viewer : c.audience) if (viewer.connection != null && !viewer.hasDisconnected() && viewer.serverLevel() == c.identity.level()) PacketDistributor.sendToPlayer(viewer, packet);
    }
    public record Visual(int entity, UUID owner, long input, long token, long epoch, long sequence, int phase,
                         Vec3 center, Vec3 axis, Vec3 point, boolean slowFalling, int refracted) implements CustomPacketPayload {
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_magnetic_domain"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b,v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.token); b.writeLong(v.epoch); b.writeLong(v.sequence); b.writeByte(v.phase);
            b.writeDouble(v.center.x); b.writeDouble(v.center.y); b.writeDouble(v.center.z); b.writeDouble(v.axis.x); b.writeDouble(v.axis.y); b.writeDouble(v.axis.z);
            b.writeDouble(v.point.x); b.writeDouble(v.point.y); b.writeDouble(v.point.z); b.writeBoolean(v.slowFalling); b.writeByte(v.refracted);
        }, b -> new Visual(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readUnsignedByte(),
                new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readBoolean(),b.readUnsignedByte()));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) { e.registrar("1").playToClient(Visual.TYPE,Visual.CODEC,(packet,context)->clientReceiver.accept(packet)); }
    }
    @SubscribeEvent public static void started(ServerStartedEvent e) {
        long saved = e.getServer().overworld().getGameTime();
        if (clock(saved) && e.getServer().getTickCount() >= 0) CLOCKS.put(e.getServer(), new MagneticDomainClock(saved, e.getServer().getTickCount()));
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Pre e) { MagneticDomainArrows.prune(e.getServer()); for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) tick(c.identity.owner()); }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if(e.getEntity() instanceof ServerPlayer p)remove(p); }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if(e.getEntity() instanceof ServerPlayer p)remove(p); }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void death(LivingDeathEvent e) { if(!e.isCanceled()&&e.getEntity() instanceof ServerPlayer p)remove(p); }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void clone(PlayerEvent.Clone e) {
        if(e.getOriginal() instanceof ServerPlayer old) {
            remove(old);
            if(e.getEntity() instanceof ServerPlayer replacement && old.getUUID().equals(replacement.getUUID())) {
                if(old.getPersistentData().contains(PAYMENT_KEY))replacement.getPersistentData().put(PAYMENT_KEY,old.getPersistentData().get(PAYMENT_KEY).copy());
                MagneticDomainArrows.copyQuota(old,replacement);
            }
        }
    }
    public static void clear() { for(Context c:new ArrayList<>(CONTEXTS.values()))finish(c); CONTEXTS.clear();INPUTS.clear();MagneticDomainArrows.clear();MagneticDomainSlowFall.clear(); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) { for(Context c:new ArrayList<>(CONTEXTS.values()))if(c.identity.owner().server==e.getServer())finish(c); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { clear(); CLOCKS.remove(e.getServer()); }
}
