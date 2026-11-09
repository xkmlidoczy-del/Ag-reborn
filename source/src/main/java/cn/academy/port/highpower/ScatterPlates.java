package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.core.ScatterPlateGeometry;
import cn.academy.port.highpower.core.ScatterPlateShares;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW212: one paid controller, three independently exhaustible allocations of the existing S. */
@EventBusSubscriber(modid = "academy")
public final class ScatterPlates {
    public static final String ID = "scatter_plates", PAYMENT_KEY = "academy:scatter_plate_payment", MASTERY_KEY = "academy:scatter_plate_mastery";
    public static final int ACTIVE = 0, HIT = 1, ENDED = 2, PERIOD = 20;
    private static final String CLOCK = "next_" + ID;
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(long epoch, long input) {}
    private record Fence(long observed, long until, long stopUntil, String dimension) {}
    static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final int preset, cooldown;
        final long revision, started;
        final long[] generation = {1, 1, 1};
        final Set<ServerPlayer> audience = new LinkedHashSet<>();
        final double activation;
        long paidUntil, lastTick = -1, sequence;
        boolean paying, processing;
        Context(ServerPlayer p, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision(); started = now(p);
            BigDecimal q = HighPowerGrowth.values(p).position();
            activation = ScatterPlateGeometry.activationCost(q); cooldown = ScatterPlateGeometry.cooldownTicks(q);
        }
    }
    /** Opaque authenticated physical contact, never constructible by an arbitrary DamageSource caller. */
    public static final class Capture {
        private final Context context;
        private final int index;
        private final long generation, tick;
        private final Vec3 point;
        private final BigDecimal ceiling;
        private final boolean historical;
        private ScatterPlateArrowReceipts.Window arrowWindow;
        private boolean committed;
        private Capture(Context c, int index, Vec3 point, BigDecimal ceiling, boolean historical) {
            context = c; this.index = index; generation = c.generation[index]; tick = now(c.identity.owner());
            this.point = point; this.ceiling = ceiling; this.historical = historical;
        }
        public int plateIndex() { return index; }
        public BigDecimal ceiling() { return ceiling; }
    }
    private ScatterPlates() {}
    public static boolean toggle(ServerPlayer p, String wire) {
        HighPowerInput.Input input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input) || AbilityConsumption.busy(p)) return false;
        Context resolving = owned(p); if (resolving != null && resolving.processing) return false;
        Watermark previous = INPUTS.get(p.getUUID());
        if (previous != null && previous.epoch == input.epoch() && previous.input >= input.input()) return false;
        INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        if (!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))) return false;
        Context old = CONTEXTS.get(p.getUUID());
        if (old != null) { boolean same = owned(p) == old && structurallyValid(old); finish(old); if (same) return true; }
        if (ZeroGapArmor.blocksRecovery(p)) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("screen.academy.highpower.zero_gap_armor.conflict"), true);
            return false;
        }
        Fence fence = readFence(p);
        if (!canAct(p) || nextToken == Long.MAX_VALUE || fence == null || now(p) < fence.stopUntil
                || ZeroGapArmor.blocksRecovery(p) || !AbilityStorage.get(p).canUse(ID) || ScatterPlateStorage.ensure(p).isEmpty()) return false;
        Context c = new Context(p, input); CONTEXTS.put(p.getUUID(), c);
        if (!pay(c, true) || !current(c)) { finish(c); return false; }
        send(c, ACTIVE, -1, center(p)); return true;
    }
    public static boolean start(ServerPlayer p, String wire) { return toggle(p, wire); }
    public static boolean abort(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Watermark old = INPUTS.get(p.getUUID());
        if (old == null || old.epoch != input.epoch() || old.input < input.input()) INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        Context c = owned(p);
        if (c == null || c.input.epoch() != input.epoch() || c.input.slot() != input.slot() || c.input.input() > input.input()) return false;
        finish(c); return true;
    }
    public static void abort(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c); }
    public static void remove(ServerPlayer p) { if (thread(p)) { abort(p); INPUTS.remove(p.getUUID()); } }
    /** Queries are read-only; invalidation and payment run only in explicit controller/lifecycle paths. */
    public static boolean active(ServerPlayer p) { Context c = owned(p); return c != null && current(c); }
    public static boolean blocksRecovery(ServerPlayer p) { Context c = owned(p); return c != null && c.paidUntil > 0 && structurallyValid(c); }
    public static boolean renewalPending(ServerPlayer p) { Context c = owned(p); return c != null && c.paidUntil > 0 && now(p) >= c.paidUntil && structurallyValid(c); }
    public static long paidUntil(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.paidUntil; }
    public static Capture captureIncoming(ServerPlayer p, DamageSource source) { return ScatterPlateArrowReceipts.claim(p, source); }
    /** Caller is the closed NEW211 exact-source permit in HighPowerCombat, never a source-name lookup. */
    public static Capture captureBeam(ServerPlayer p, ServerPlayer attacker, Vec3 origin, Vec3 endpoint, LivingEntity exactTarget) {
        if (p != exactTarget || attacker == null || attacker == p || attacker.level() != p.level()) return null;
        return crossing(p, origin, endpoint, false);
    }
    static Capture crossing(ServerPlayer p, Vec3 start, Vec3 end, boolean historical) {
        Context c = owned(p);
        if (c == null || !current(c) || !finite(start) || !finite(end)) return null;
        var saved = ScatterPlateStorage.read(p); if (saved.isEmpty()) return null;
        var contact = ScatterPlateGeometry.crossing(pose(p), vector(start), vector(end), mask(saved.get()));
        if (contact.isEmpty()) return null;
        var hit = contact.get(); BigDecimal ceiling = saved.get().plate(hit.plateIndex()).current().min(saved.get().shield());
        return ceiling.signum() <= 0 ? null : new Capture(c, hit.plateIndex(), vec(hit.point()), ceiling, historical);
    }
    public static boolean matches(ServerPlayer p, Capture capture) {
        if (capture == null || capture.committed || owned(p) != capture.context || !current(capture.context, HighPowerCombat.scatterNativeFrame(p, capture))
                || capture.arrowWindow != null && !ScatterPlateArrowReceipts.validWindow(capture.arrowWindow)
                || capture.context.generation[capture.index] != capture.generation || now(p) < capture.tick
                || (!capture.historical && now(p) != capture.tick) || capture.historical && now(p) - capture.tick >= 20) return false;
        var saved = ScatterPlateStorage.read(p);
        return saved.isPresent() && saved.get().plate(capture.index).current().signum() > 0;
    }
    static boolean bindArrowWindow(Capture capture, ScatterPlateArrowReceipts.Window window) {
        if (capture == null || !capture.historical || capture.committed || capture.arrowWindow != null || window == null) return false;
        capture.arrowWindow = window; return true;
    }
    public static void committed(ServerPlayer p, Capture capture, BigDecimal absorbed, boolean broken) {
        // Base and shares have already been atomically debited, so a just-broken plate is now zero.
        if (capture == null || capture.committed || absorbed == null || absorbed.signum() <= 0 || absorbed.compareTo(capture.ceiling) > 0
                || owned(p) != capture.context || capture.context.generation[capture.index] != capture.generation) return;
        capture.committed = true;
        if (broken) invalidatePlate(capture.context, capture.index);
        if (current(capture.context)) send(capture.context, HIT, capture.index, capture.point);
        reward(capture.context);
    }
    /** A category-shield fallback may exhaust additional siblings after the authenticated local contact.
     * Consume that same one-use contact before invalidation/XP callbacks, and invalidate all real extra breaks first. */
    static void committed(ServerPlayer p,Capture capture,ScatterPlateStorage.Contact contact,ScatterPlateShares finalShares) {
        if(capture==null||contact==null||finalShares==null||capture.committed||contact.absorbed().signum()<=0
                ||contact.absorbed().compareTo(capture.ceiling)>0||owned(p)!=capture.context
                ||capture.context.generation[capture.index]!=capture.generation||!HighPowerCombat.scatterNativeFrame(p,capture))return;
        var saved=ScatterPlateStorage.read(p);
        var expected=contact.shares().afterBaseSpend(finalShares.shield());
        if(saved.isEmpty()||!expected.toDecimalMap().equals(finalShares.toDecimalMap())
                ||!saved.get().toDecimalMap().equals(finalShares.toDecimalMap())
                ||contact.broken()!=(contact.shares().plate(capture.index).current().signum()==0))return;
        var extraBroken=contact.shares().brokenByBaseSpend(finalShares.shield());
        capture.committed=true;
        if(contact.broken())invalidatePlate(capture.context,capture.index);
        for(int index:extraBroken)invalidatePlate(capture.context,index);
        if(current(capture.context))send(capture.context,HIT,capture.index,capture.point);
        reward(capture.context);
    }

    /** The accepted contact is productive even during native temporary HP0 before a totem/death veto. */
    private static void reward(Context c) {
        ServerPlayer p = c.identity.owner(); long tick = now(p);
        if (c.processing || owned(p) != c || !c.identity.owns(p) || !clock(tick)
                || !c.identity.state().category.equals("meltdowner") || !c.identity.state().learned(ID)
                || c.preset != c.identity.state().presets.current() || c.revision != c.identity.state().presets.revision()) return;
        CompoundTag data = p.getPersistentData();
        if (data.contains(MASTERY_KEY)) {
            if (!data.contains(MASTERY_KEY, Tag.TAG_COMPOUND)) return;
            CompoundTag prior = data.getCompound(MASTERY_KEY);
            if (!prior.getAllKeys().equals(Set.of("version", "observed", "until")) || !prior.contains("version", Tag.TAG_INT) || prior.getInt("version") != 1
                    || !prior.contains("observed", Tag.TAG_LONG) || !prior.contains("until", Tag.TAG_LONG) || !clock(prior.getLong("observed"))
                    || prior.getLong("observed") > tick || prior.getLong("until") != prior.getLong("observed") + PERIOD || tick < prior.getLong("until")) return;
        }
        // Persist the one20tick award fence before any mutable XP callback. Never roll it back.
        CompoundTag stamp = new CompoundTag(); stamp.putInt("version", 1); stamp.putLong("observed", tick); stamp.putLong("until", tick + PERIOD); data.put(MASTERY_KEY, stamp);
        c.processing = true;
        try { c.identity.state().addExperience(ID, .005); AbilityStorage.save(p); }
        finally { c.processing = false; }
    }
    public static void baseSharesChanged(ServerPlayer p, ScatterPlateShares before, ScatterPlateShares after) {
        Context c = owned(p); if (c == null || before == null || after == null) return;
        for (int i = 0; i < 3; i++) if (before.plate(i).current().signum() > 0 && after.plate(i).current().signum() == 0) invalidatePlate(c, i);
    }
    private static void invalidatePlate(Context c, int index) { c.generation[index]++; ScatterPlateArrowReceipts.invalidatePlate(c.identity.owner(), index); }
    static Object activation(Capture capture) { return capture.context; }
    static Vec3 point(Capture capture) { return capture.point; }
    static List<ServerPlayer> candidates(net.minecraft.server.level.ServerLevel world) {
        if (CONTEXTS.size() > ScatterPlateArrowReceipts.SCAN_LIMIT) return null;
        List<ServerPlayer> result = new ArrayList<>();
        for (Context c : CONTEXTS.values()) if (c.identity.level() == world && current(c)) { result.add(c.identity.owner()); if (result.size() > ScatterPlateArrowReceipts.SCAN_LIMIT) break; }
        return result;
    }
    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null) return;
        if (!structurallyValid(c) || !canAct(p)) { finish(c); return; }
        long tick = now(p); if (tick <= c.lastTick) return; c.lastTick = tick;
        if (tick >= c.paidUntil && !pay(c, false)) { finish(c); return; }
        if (!current(c)) { finish(c); return; }
        if ((tick - c.started) % 4 == 0) send(c, ACTIVE, -1, center(p));
    }
    private static boolean pay(Context c, boolean activation) {
        ServerPlayer p = c.identity.owner(); long tick = now(p); Fence previous = readFence(p);
        double cost = activation ? c.activation : ScatterPlateGeometry.sustainCost(HighPowerGrowth.values(p).position());
        if (c.paying || !structurallyValid(c) || previous == null || !activation && tick < previous.until || tick < previous.stopUntil
                || HighPowerResources.cp(p) < cost) return false;
        c.paying = true;
        try {
            String dimension = p.serverLevel().dimension().location().toString();
            CompoundTag budget = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
            boolean translate = !previous.dimension.isEmpty() && !previous.dimension.equals(dimension);
            Tag oldClock = budget.contains(CLOCK) ? budget.get(CLOCK).copy() : null;
            if (translate) { budget.putLong(CLOCK, p.serverLevel().getGameTime()); p.getPersistentData().put(HighPowerResources.DATA_KEY, budget); }
            // Payment must not make the live toggle's client delegate abort itself on cooldown.
            // The listed cooldown starts on finish and remains enforced by the persistent stop fence.
            if (!HighPowerResources.trySpend(p, ID, cost, 1)) {
                if (translate) { var restore = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY); if (oldClock == null) restore.remove(CLOCK); else restore.put(CLOCK, oldClock); p.getPersistentData().put(HighPowerResources.DATA_KEY, restore); }
                return false;
            }
            writeFence(p, new Fence(tick, tick + PERIOD, previous.stopUntil, dimension)); c.paidUntil = tick + PERIOD;
            return owned(p) == c && structurallyValid(c);
        } finally { c.paying = false; }
    }
    private static Fence readFence(ServerPlayer p) {
        long tick = now(p); if (!clock(tick)) return null;
        CompoundTag data = p.getPersistentData(); if (!data.contains(PAYMENT_KEY)) return new Fence(tick, 0, 0, "");
        if (!data.contains(PAYMENT_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag t = data.getCompound(PAYMENT_KEY);
        if (!t.getAllKeys().equals(Set.of("version", "observed", "until", "stopUntil", "dimension")) || !t.contains("version", Tag.TAG_INT) || t.getInt("version") != 1
                || !t.contains("observed", Tag.TAG_LONG) || !t.contains("until", Tag.TAG_LONG) || !t.contains("stopUntil", Tag.TAG_LONG) || !t.contains("dimension", Tag.TAG_STRING)
                || !clock(t.getLong("observed")) || t.getLong("observed") > tick || t.getLong("until") < 0 || t.getLong("until") > t.getLong("observed") + PERIOD
                || t.getLong("stopUntil") < 0 || t.getLong("stopUntil") > t.getLong("observed") + PERIOD || t.getString("dimension").length() > 256) return null;
        return new Fence(t.getLong("observed"), t.getLong("until"), t.getLong("stopUntil"), t.getString("dimension"));
    }
    private static void writeFence(ServerPlayer p, Fence f) {
        CompoundTag t = new CompoundTag(); t.putInt("version", 1); t.putLong("observed", f.observed); t.putLong("until", f.until);
        t.putLong("stopUntil", f.stopUntil); t.putString("dimension", f.dimension); p.getPersistentData().put(PAYMENT_KEY, t);
    }
    private static boolean canAct(ServerPlayer p) {
        return basic(p) && HighPowerResources.ready(p) && HighPowerCombat.unsupportedReason(p).isEmpty();
    }
    /** Only callback-free owner/state/clock getters are used while observing a native projectile segment. */
    private static boolean basic(ServerPlayer p) {
        return basic(p, false);
    }
    private static boolean basic(ServerPlayer p, boolean nativeDamageFrame) {
        if (!thread(p) || p.connection == null || p.hasDisconnected() || p.isRemoved() || !nativeDamageFrame && !p.isAlive() || p.isSpectator() || p.getAbilities().instabuild
                || p.getAbilities().invulnerable || p.noPhysics || p.isPassenger() || p.isSleeping() || AbilityConsumption.busy(p)) return false;
        var s = AbilityStorage.peek(p);
        return s != null && s.category.equals("meltdowner") && s.activated && s.overloadFine && !s.interfering
                && s.learned(ID) && s.learned(DualPoint.ID) && s.level >= 1 && s.level <= 5 && SkillCatalog.enabled("meltdowner", ID)
                && finite(p.position()) && finite(p.getLookAngle()) && finite(p.getDeltaMovement()) && Float.isFinite(p.getXRot()) && Float.isFinite(p.getYRot()) && clock(now(p));
    }
    private static boolean structurallyValid(Context c) {
        return structurallyValid(c, false);
    }
    private static boolean structurallyValid(Context c, boolean nativeDamageFrame) {
        ServerPlayer p = c.identity.owner();
        return owned(p) == c && snapshotOwned(c) && basic(p, nativeDamageFrame) && now(p) >= c.started
                && c.preset == c.identity.state().presets.current() && c.revision == c.identity.state().presets.revision()
                && ID.equals(c.identity.state().presets.currentSkill(c.input.slot()));
    }
    /** Does not call AbilityStorage.get/identity.owns: those ordinary getters rebind callbacks. */
    private static boolean snapshotOwned(Context c) {
        ServerPlayer p = c.identity.owner();
        return p.serverLevel() == c.identity.level() && p.level() == c.identity.level() && p.getId() == c.input.actor()
                && AbilityStorage.peek(p) == c.identity.state() && c.identity.level().getEntity(p.getId()) == p && c.identity.level().getEntity(p.getUUID()) == p
                && LegacySingleKeyOwnerEpoch.matches(p, p.connection, c.identity.level(), c.identity.state(), c.identity.epoch());
    }
    private static boolean current(Context c) {
        return current(c, false);
    }
    private static boolean current(Context c, boolean nativeDamageFrame) {
        if (c.paying || !structurallyValid(c, nativeDamageFrame) || now(c.identity.owner()) >= c.paidUntil || ScatterPlateStorage.read(c.identity.owner()).isEmpty()) return false;
        Fence fence = readFence(c.identity.owner()); return fence != null && fence.until == c.paidUntil && now(c.identity.owner()) >= fence.observed;
    }
    private static Context owned(ServerPlayer p) { if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null; }
    private static boolean thread(ServerPlayer p) { return p != null && p.server != null && p.server.isSameThread(); }
    private static boolean clock(long tick) { return tick >= 0 && tick <= Long.MAX_VALUE - PERIOD; }
    static long now(ServerPlayer p) { return MagneticDomain.serverTime(p.server); }
    static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    static Vec3 center(ServerPlayer p) { return p.position().add(0, 1, 0); }
    static ScatterPlateGeometry.Pose pose(ServerPlayer p) { return ScatterPlateGeometry.pose(vector(center(p)), vector(p.getLookAngle())); }
    static ScatterPlateGeometry.Vector vector(Vec3 v) { return new ScatterPlateGeometry.Vector(v.x, v.y, v.z); }
    static Vec3 vec(ScatterPlateGeometry.Vector v) { return new Vec3(v.x(), v.y(), v.z()); }
    private static int mask(ScatterPlateShares s) { int mask = 0; for (int i = 0; i < 3; i++) if (s.plate(i).current().signum() > 0) mask |= 1 << i; return mask; }
    private static void finish(Context c) {
        ServerPlayer p = c.identity.owner(); if (!CONTEXTS.remove(p.getUUID(), c)) return;
        ScatterPlateArrowReceipts.invalidate(p);
        if (c.paidUntil > 0) {
            Fence fence = readFence(p); long tick = now(p);
            if (fence != null) writeFence(p, new Fence(tick, fence.until, Math.max(fence.stopUntil, tick + c.cooldown), fence.dimension));
            c.identity.state().setCooldown(ID, c.cooldown);
        }
        send(c, ENDED, -1, center(p)); c.audience.clear();
    }
    private static void send(Context c, int phase, int plate, Vec3 point) {
        ServerPlayer p = c.identity.owner(); if (!finite(point) || !finite(p.getLookAngle())) return;
        int liveMask = phase == ENDED ? 0 : ScatterPlateStorage.read(p).map(ScatterPlates::mask).orElse(0);
        c.audience.removeIf(v -> v.connection == null || v.hasDisconnected() || v.isRemoved() || v.serverLevel() != c.identity.level() || v.position().distanceToSqr(p.position()) > 64 * 64);
        for (ServerPlayer viewer : c.identity.level().players()) if (viewer.position().distanceToSqr(p.position()) <= 48 * 48) c.audience.add(viewer);
        c.audience.add(p);
        Visual packet = new Visual(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.identity.epoch(), ++c.sequence, phase, center(p), p.getLookAngle(), liveMask, plate, point);
        for (ServerPlayer viewer : c.audience) if (viewer.connection != null && !viewer.hasDisconnected() && viewer.serverLevel() == c.identity.level()) PacketDistributor.sendToPlayer(viewer, packet);
    }
    public record Visual(int entity, UUID owner, long input, long token, long epoch, long sequence, int phase, Vec3 center, Vec3 axis, int liveMask, int plate, Vec3 point) implements CustomPacketPayload {
        public Visual {
            if (entity < 0 || owner == null || input <= 0 || token <= 0 || epoch <= 0 || sequence <= 0 || phase < ACTIVE || phase > ENDED || !finite(center) || !finite(axis) || !finite(point)
                    || axis.lengthSqr() < .5 || axis.lengthSqr() > 1.5 || liveMask < 0 || liveMask > 7 || plate < -1 || plate > 2 || phase == HIT && plate < 0 || phase != HIT && plate != -1)
                throw new IllegalArgumentException("Invalid bounded NEW212 visual");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_scatter_plates"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.token); b.writeLong(v.epoch); b.writeLong(v.sequence); b.writeByte(v.phase);
            writeVec(b, v.center); writeVec(b, v.axis); b.writeByte(v.liveMask); b.writeByte(v.plate); writeVec(b, v.point);
        }, b -> new Visual(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readUnsignedByte(), readVec(b), readVec(b), b.readUnsignedByte(), b.readByte(), readVec(b)));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static void writeVec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 readVec(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) { e.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet)); }
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Pre e) { for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) tick(c.identity.owner()); ScatterPlateArrowReceipts.prune(e.getServer()); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void death(LivingDeathEvent e) { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void leave(EntityLeaveLevelEvent e) { ScatterPlateArrowReceipts.removed(e.getEntity()); if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void clone(PlayerEvent.Clone e) {
        if (e.getOriginal() instanceof ServerPlayer old) { remove(old); if (e.getEntity() instanceof ServerPlayer replacement && old.getUUID().equals(replacement.getUUID())) for (String key : List.of(PAYMENT_KEY, MASTERY_KEY)) if (old.getPersistentData().contains(key)) replacement.getPersistentData().put(key, old.getPersistentData().get(key).copy()); }
    }
    public static void clear() { for (Context c : new ArrayList<>(CONTEXTS.values())) finish(c); CONTEXTS.clear(); INPUTS.clear(); ScatterPlateArrowReceipts.clear(); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) { for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) finish(c); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { clear(); }
}
