package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.DissipationCurtainGeometry;
import cn.academy.port.highpower.core.DissipationCurtainShieldRules;
import cn.academy.port.highpower.core.ExactCombatState;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
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

/** NEW242. A paid held controller authenticates finite contacts; the combat ledger owns every debit. */
@EventBusSubscriber(modid = "academy")
public final class DissipationCurtain {
    public static final String ID = "directional_dissipation_curtain", PAYMENT_KEY = "academy:dissipation_curtain_payment", MASTERY_KEY = "academy:dissipation_curtain_mastery";
    public static final int ACTIVE = 0, HIT = 1, ENDED = 2, STARTUP = 3, PERIOD = 20, STARTUP_TICKS = DissipationCurtainGeometry.STARTUP_TICKS;
    private static final String CLOCK = "next_" + ID;
    private static final int MAX_COOLDOWN = 60;
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final boolean DIAGNOSTICS = Boolean.getBoolean("academy.highpower.dissipationCurtainDiagnostic");
    private static final org.slf4j.Logger DIAGNOSTIC_LOG = org.slf4j.LoggerFactory.getLogger("academy.dissipation_curtain_diagnostics");
    private static final int MAX_DIAGNOSTIC_EVENTS = 256;
    private static int diagnosticEvents;
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
        long paidUntil, lastTick = -1, sequence, depletedAt = -1;
        boolean paying, processing, validating, planning, shownActive, depleted;
        Context(ServerPlayer p, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision(); started = now(p);
            BigDecimal q = HighPowerGrowth.values(p).position();
            activation = DissipationCurtainGeometry.activationCost(q); cooldown = DissipationCurtainGeometry.cooldownTicks(q);
        }
    }
    /** Opaque contact bound to its activation, facet generation, immutable paid-resource ceiling and pose. */
    public static final class Capture {
        private final Context context;
        private final int index;
        private final long generation, tick;
        private final Vec3 point, origin, axis, start;
        private final BigDecimal ceiling;
        private final boolean historical;
        private DissipationCurtainArrowReceipts.Window arrowWindow;
        private boolean committed;
        private Capture(Context c, int index, Vec3 point, Vec3 origin, Vec3 axis, Vec3 start, BigDecimal ceiling, boolean historical) {
            context = c; this.index = index; generation = c.generation[index]; tick = now(c.identity.owner());
            this.point = point; this.origin = origin; this.axis = axis; this.start = start; this.ceiling = ceiling; this.historical = historical;
        }
        public int plateIndex() { return index; }
        public int facetIndex() { return index; }
        public BigDecimal ceiling() { return ceiling; }
    }
    private DissipationCurtain() {}

    public static boolean start(ServerPlayer p, String wire) {
        HighPowerInput.Input input = HighPowerInput.parse(wire);
        Context resolving = owned(p); if (resolving != null && (resolving.processing || resolving.paying || resolving.validating || resolving.planning)) return false;
        if (!HighPowerBracerSession.authenticated(p, input) || AbilityConsumption.busy(p)) return false;
        Watermark previous = INPUTS.get(p.getUUID());
        if (previous != null && previous.epoch == input.epoch() && previous.input >= input.input()) return false;
        INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        if (!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))) return false;
        Context old = CONTEXTS.get(p.getUUID()); if (old != null) finish(old);
        if (ZeroGapArmor.blocksRecovery(p)) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("screen.academy.highpower.zero_gap_armor.conflict"), true);
            return false;
        }
        Fence fence = readFence(p);
        if (!canAct(p) || nextToken == Long.MAX_VALUE || fence == null || now(p) < fence.stopUntil
                || ZeroGapArmor.blocksRecovery(p) || !AbilityStorage.get(p).canUse(ID) || !MagneticDomainReserve.ensureMigrated(p) || available(p).signum() <= 0) return false;
        Context c = new Context(p, input); CONTEXTS.put(p.getUUID(), c);
        if (!pay(c, true) || !paid(c, false)) { finish(c); return false; }
        diagnostic(c, "start", null, BigDecimal.ZERO, BigDecimal.ZERO);
        send(c, STARTUP, -1, center(p), center(p), p.getLookAngle()); return true;
    }
    /** An old release cannot terminate a newer activation, even when the same slot is reused. */
    public static boolean release(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        watermark(p, input); Context c = owned(p);
        if (c == null || !c.input.equals(input)) return false;
        finish(c); return true;
    }
    public static boolean abort(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        watermark(p, input); Context c = owned(p);
        if (c == null || c.input.epoch() != input.epoch() || c.input.slot() != input.slot() || c.input.input() > input.input()) return false;
        finish(c); return true;
    }
    private static void watermark(ServerPlayer p, HighPowerInput.Input input) {
        Watermark old = INPUTS.get(p.getUUID());
        if (old == null || old.epoch != input.epoch() || old.input < input.input()) INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
    }
    public static void abort(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c); }
    public static void remove(ServerPlayer p) { if (thread(p)) { abort(p); INPUTS.remove(p.getUUID()); } }
    public static boolean holding(ServerPlayer p) { Context c = owned(p); return c != null && paid(c, false); }
    public static boolean active(ServerPlayer p) { Context c = owned(p); return c != null && current(c, false); }
    public static boolean blocksRecovery(ServerPlayer p) { Context c = owned(p); return c != null && c.paidUntil > 0 && structurallyValid(c, false); }
    public static boolean renewalPending(ServerPlayer p) { Context c = owned(p); return c != null && c.paidUntil > 0 && now(p) >= c.paidUntil && structurallyValid(c, false); }
    public static long paidUntil(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.paidUntil; }
    public static Capture captureIncoming(ServerPlayer p, DamageSource source) { return DissipationCurtainArrowReceipts.claim(p, source); }
    /** Called solely from the closed exact-beam permit; source names do not admit a beam. */
    public static Capture captureBeam(ServerPlayer p, ServerPlayer attacker, Vec3 origin, Vec3 endpoint, LivingEntity exactTarget) {
        if (p != exactTarget || attacker == null || attacker == p || attacker.level() != p.level()) return null;
        return crossing(p, origin, endpoint, false);
    }
    static Capture crossing(ServerPlayer p, Vec3 start, Vec3 end, boolean historical) {
        Context c = owned(p);
        if (c == null || c.validating || !current(c, false) || !finite(start) || !finite(end)) return null;
        Vec3 origin = center(p), axis = p.getLookAngle();
        var contact = DissipationCurtainGeometry.crossing(DissipationCurtainGeometry.pose(vector(origin), vector(axis)), vector(start), vector(end), 7);
        if (contact.isEmpty()) return null;
        var hit = contact.get(); Vec3 point = vec(hit.point()); BigDecimal ceiling = available(p);
        if (ceiling.signum() <= 0) return null;
        var base = HighPowerCombat.exactStateSnapshot(p);
        var shares = base.flatMap(saved -> MagneticDomainReserve.savedShares(p, saved));
        c.validating = true;
        try {
            if (!guard(p, origin, point) || !guard(p, start, point) || !current(c, false)
                    || !origin.equals(center(p)) || !axis.equals(p.getLookAngle()) || available(p).compareTo(ceiling) != 0
                    || !base.equals(HighPowerCombat.exactStateSnapshot(p))
                    || !shares.equals(base.flatMap(saved -> MagneticDomainReserve.savedShares(p, saved)))) return null;
            return new Capture(c, hit.plateIndex(), point, origin, axis, start, ceiling, historical);
        } finally { c.validating = false; }
    }
    public static boolean matches(ServerPlayer p, Capture capture) {
        if (capture == null || capture.committed || owned(p) != capture.context || capture.context.validating || capture.context.planning
                || !current(capture.context, HighPowerCombat.curtainNativeFrame(p, capture))
                || capture.arrowWindow != null && !DissipationCurtainArrowReceipts.validWindow(capture.arrowWindow)
                || capture.context.generation[capture.index] != capture.generation || now(p) < capture.tick
                || (!capture.historical && now(p) != capture.tick) || capture.historical && now(p) - capture.tick >= PERIOD) return false;
        // The physical path was certified at crossing; native flight/beam emission owns later obstruction checks.
        // Never invoke permission or block-shape callbacks from the damage settlement's immutable read phase.
        return loadedPoint(p, capture.origin) && loadedPoint(p, capture.point) && loadedPoint(p, capture.start);
    }
    static boolean bindArrowWindow(Capture capture, DissipationCurtainArrowReceipts.Window window) {
        if (capture == null || !capture.historical || capture.committed || capture.arrowWindow != null || window == null) return false;
        capture.arrowWindow = window; return true;
    }
    static DissipationCurtainShieldRules.Result plan(ServerPlayer p, Capture capture, ExactCombatState base, BigDecimal incoming) {
        if (base == null || incoming == null || !matches(p, capture) || !HighPowerCombat.exactStateSnapshot(p).filter(base::equals).isPresent()) return null;
        Context c = capture.context; c.planning = true;
        try {
            return MagneticDomainReserve.savedShares(p, base)
                    .map(shares -> DissipationCurtainShieldRules.absorb(base, shares, incoming, capture.arrowWindow != null, capture.ceiling)).orElse(null);
        } finally { c.planning = false; }
    }
    /** Called only after a positive exact debit. A zero/no-op/canceled source never sends a ripple or awards XP. */
    public static void committed(ServerPlayer p, Capture capture, BigDecimal absorbed, BigDecimal spent) {
        if (capture == null || capture.committed || absorbed == null || absorbed.signum() <= 0
                || spent == null || spent.signum() <= 0 || spent.compareTo(capture.ceiling) > 0
                || absorbed.compareTo(spent.multiply(capture.arrowWindow == null ? BigDecimal.ONE : new BigDecimal("1.25"))) > 0
                || owned(p) != capture.context
                || capture.context.generation[capture.index] != capture.generation
                    && !(capture.context.depleted && capture.context.depletedAt == now(p) && capture.context.generation[capture.index] == capture.generation + 1)) return;
        capture.committed = true;
        Context c = capture.context;
        diagnostic(c, "committed", capture, absorbed, spent);
        // Historical evidence may survive motion; it never creates a detached old curtain in the renderer.
        if (DissipationCurtainGeometry.contains(pose(p), capture.index, vector(capture.point)))
            send(c, HIT, capture.index, capture.point, center(p), p.getLookAngle());
        reward(c);
        if (c.depleted || available(p).signum() <= 0) finish(c);
    }
    /** Atomic ledger writers call after both balances agree. No callbacks, packets or cooldown mutation here. */
    public static void resourcesChanged(ServerPlayer p) {
        Context c = owned(p);
        if (c == null || c.depleted || available(p).signum() > 0) return;
        c.depleted = true; c.depletedAt = now(p);
        for (int i = 0; i < c.generation.length; i++) c.generation[i]++;
    }
    private static void reward(Context c) {
        ServerPlayer p = c.identity.owner(); long tick = now(p);
        if (c.processing || owned(p) != c || !snapshotOwned(c) || !clock(tick)
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
        CompoundTag stamp = new CompoundTag(); stamp.putInt("version", 1); stamp.putLong("observed", tick); stamp.putLong("until", tick + PERIOD); data.put(MASTERY_KEY, stamp);
        c.processing = true;
        try { c.identity.state().addExperience(ID, .005); AbilityStorage.save(p); }
        finally { c.processing = false; }
    }
    static Object activation(Capture capture) { return capture.context; }
    static Vec3 point(Capture capture) { return capture.point; }
    static List<ServerPlayer> candidates(ServerLevel world) {
        if (CONTEXTS.size() > DissipationCurtainArrowReceipts.SCAN_LIMIT) return null;
        List<ServerPlayer> result = new ArrayList<>();
        for (Context c : CONTEXTS.values()) if (c.identity.level() == world && current(c, false)) result.add(c.identity.owner());
        return result;
    }
    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null) return;
        if (c.depleted || !structurallyValid(c, false) || !canAct(p) || available(p).signum() <= 0) { finish(c); return; }
        long tick = now(p); if (tick <= c.lastTick) return; c.lastTick = tick;
        if (tick >= c.paidUntil && !pay(c, false)) { finish(c); return; }
        if (!paid(c, false)) { finish(c); return; }
        if (tick < c.started + STARTUP_TICKS) return;
        if (!c.shownActive || (tick - c.started) % 4 == 0) { c.shownActive = true; send(c, ACTIVE, -1, center(p), center(p), p.getLookAngle()); }
    }
    private static boolean pay(Context c, boolean activation) {
        ServerPlayer p = c.identity.owner(); long tick = now(p); Fence previous = readFence(p);
        double cost = activation ? c.activation : 1;
        if (c.paying || !structurallyValid(c, false) || previous == null || !activation && tick < previous.until || tick < previous.stopUntil
                || HighPowerResources.availableCp(p) < cost) return false;
        c.paying = true;
        try {
            String dimension = p.serverLevel().dimension().location().toString();
            CompoundTag budget = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
            boolean translate = !previous.dimension.isEmpty() && !previous.dimension.equals(dimension);
            Tag oldClock = budget.contains(CLOCK) ? budget.get(CLOCK).copy() : null;
            if (translate) { budget.putLong(CLOCK, p.serverLevel().getGameTime()); p.getPersistentData().put(HighPowerResources.DATA_KEY, budget); }
            if (!HighPowerResources.trySpend(p, ID, cost, 1)) {
                if (translate) { var restore = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY); if (oldClock == null) restore.remove(CLOCK); else restore.put(CLOCK, oldClock); p.getPersistentData().put(HighPowerResources.DATA_KEY, restore); }
                return false;
            }
            writeFence(p, new Fence(tick, tick + PERIOD, previous.stopUntil, dimension)); c.paidUntil = tick + PERIOD;
            return owned(p) == c && structurallyValid(c, false);
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
                || t.getLong("stopUntil") < 0 || t.getLong("stopUntil") > t.getLong("observed") + MAX_COOLDOWN || t.getString("dimension").length() > 256) return null;
        return new Fence(t.getLong("observed"), t.getLong("until"), t.getLong("stopUntil"), t.getString("dimension"));
    }
    private static void writeFence(ServerPlayer p, Fence f) {
        CompoundTag t = new CompoundTag(); t.putInt("version", 1); t.putLong("observed", f.observed); t.putLong("until", f.until);
        t.putLong("stopUntil", f.stopUntil); t.putString("dimension", f.dimension); p.getPersistentData().put(PAYMENT_KEY, t);
    }
    private static boolean canAct(ServerPlayer p) { return basic(p, false) && HighPowerResources.ready(p) && HighPowerCombat.unsupportedReason(p).isEmpty(); }
    private static boolean basic(ServerPlayer p, boolean nativeDamageFrame) {
        if (!thread(p) || p.connection == null || p.hasDisconnected() || p.isRemoved() || !nativeDamageFrame && !p.isAlive() || p.isSpectator() || p.getAbilities().instabuild
                || p.getAbilities().invulnerable || p.noPhysics || p.isPassenger() || p.isSleeping() || p.containerMenu != p.inventoryMenu || AbilityConsumption.busy(p)) return false;
        var s = AbilityStorage.peek(p);
        return s != null && s.category.equals("meltdowner") && s.activated && s.overloadFine && !s.interfering
                && s.learned(ID) && s.learned(ScatterPlates.ID) && s.learned(DualPoint.ID) && s.level >= 4 && s.level <= 5 && SkillCatalog.enabled("meltdowner", ID)
                && finite(p.position()) && finite(p.getLookAngle()) && finite(p.getDeltaMovement()) && Float.isFinite(p.getXRot()) && Float.isFinite(p.getYRot()) && clock(now(p));
    }
    private static boolean structurallyValid(Context c, boolean nativeDamageFrame) {
        ServerPlayer p = c.identity.owner();
        return owned(p) == c && snapshotOwned(c) && basic(p, nativeDamageFrame) && now(p) >= c.started
                && c.preset == c.identity.state().presets.current() && c.revision == c.identity.state().presets.revision()
                && ID.equals(c.identity.state().presets.currentSkill(c.input.slot()));
    }
    private static boolean snapshotOwned(Context c) {
        ServerPlayer p = c.identity.owner();
        return p.serverLevel() == c.identity.level() && p.level() == c.identity.level() && p.getId() == c.input.actor()
                && AbilityStorage.peek(p) == c.identity.state() && c.identity.level().getEntity(p.getId()) == p && c.identity.level().getEntity(p.getUUID()) == p
                && LegacySingleKeyOwnerEpoch.matches(p, p.connection, c.identity.level(), c.identity.state(), c.identity.epoch());
    }
    private static boolean current(Context c, boolean nativeDamageFrame) { return now(c.identity.owner()) >= c.started + STARTUP_TICKS && paid(c, nativeDamageFrame); }
    private static boolean paid(Context c, boolean nativeDamageFrame) {
        if (c.depleted || c.paying || !structurallyValid(c, nativeDamageFrame) || now(c.identity.owner()) >= c.paidUntil || available(c.identity.owner()).signum() <= 0) return false;
        Fence fence = readFence(c.identity.owner()); return fence != null && fence.until == c.paidUntil && now(c.identity.owner()) >= fence.observed;
    }
    /** No ordinary ability getter/callback is used by the native arrow observer. */
    private static BigDecimal available(ServerPlayer p) {
        return HighPowerCombat.exactStateSnapshot(p).flatMap(base -> MagneticDomainReserve.savedShares(p, base).map(shares -> base.shield().add(shares.lower()))).orElse(BigDecimal.ZERO);
    }
    /** Bounded loaded-world/permission checks precede every collider query; no chunk may be generated. */
    private static boolean guard(ServerPlayer p, Vec3 from, Vec3 to) {
        if (!finite(from) || !finite(to) || from.distanceToSqr(to) > 128 * 128) return false;
        ServerLevel world = p.serverLevel();
        for (int x = ((int)Math.floor(Math.min(from.x, to.x))) >> 4; x <= ((int)Math.floor(Math.max(from.x, to.x))) >> 4; x++)
            for (int z = ((int)Math.floor(Math.min(from.z, to.z))) >> 4; z <= ((int)Math.floor(Math.max(from.z, to.z))) >> 4; z++) if (!world.hasChunk(x, z)) return false;
        int samples = Math.max(1, (int)Math.ceil(from.distanceTo(to) * 4));
        for (int i = 0; i <= samples; i++) {
            BlockPos pos = BlockPos.containing(from.lerp(to, (double)i / samples));
            if (world.isOutsideBuildHeight(pos) || !world.hasChunkAt(pos) || !world.getWorldBorder().isWithinBounds(pos) || !world.mayInteract(p, pos)) return false;
        }
        return world.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }
    private static boolean loadedPoint(ServerPlayer p, Vec3 point) {
        BlockPos pos = BlockPos.containing(point); ServerLevel world = p.serverLevel();
        return !world.isOutsideBuildHeight(pos) && world.hasChunkAt(pos) && world.getWorldBorder().isWithinBounds(pos);
    }
    private static Context owned(ServerPlayer p) { if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null; }
    private static boolean thread(ServerPlayer p) { return p != null && p.server != null && p.server.isSameThread(); }
    private static boolean clock(long tick) { return tick >= 0 && tick <= Long.MAX_VALUE - MAX_COOLDOWN; }
    static long now(ServerPlayer p) { return MagneticDomain.serverTime(p.server); }
    static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z) && Math.abs(v.x) <= 30_000_001 && Math.abs(v.y) <= 30_000_001 && Math.abs(v.z) <= 30_000_001; }
    static Vec3 center(ServerPlayer p) { return p.getEyePosition(); }
    static DissipationCurtainGeometry.Pose pose(ServerPlayer p) { return DissipationCurtainGeometry.pose(vector(center(p)), vector(p.getLookAngle())); }
    static DissipationCurtainGeometry.Vector vector(Vec3 v) { return new DissipationCurtainGeometry.Vector(v.x, v.y, v.z); }
    static Vec3 vec(DissipationCurtainGeometry.Vector v) { return new Vec3(v.x(), v.y(), v.z()); }
    private static void finish(Context c) {
        ServerPlayer p = c.identity.owner(); if (!CONTEXTS.remove(p.getUUID(), c)) return;
        diagnostic(c, "end", null, BigDecimal.ZERO, BigDecimal.ZERO);
        for (int i = 0; i < c.generation.length; i++) c.generation[i]++;
        DissipationCurtainArrowReceipts.invalidate(p);
        if (c.paidUntil > 0) {
            Fence fence = readFence(p); long tick = now(p);
            if (fence != null && clock(tick)) writeFence(p, new Fence(tick, fence.until, Math.max(fence.stopUntil, tick + c.cooldown), fence.dimension));
            c.identity.state().setCooldown(ID, c.cooldown);
        }
        send(c, ENDED, -1, center(p), center(p), p.getLookAngle()); c.audience.clear();
    }
    /** Opt-in proof only: bounded server-thread reads after real lifecycle/debit events, never tick logging. */
    private static void diagnostic(Context c, String stage, Capture capture, BigDecimal absorbed, BigDecimal spent) {
        if (!DIAGNOSTICS || diagnosticEvents >= MAX_DIAGNOSTIC_EVENTS || !thread(c.identity.owner())) return;
        diagnosticEvents++;
        ServerPlayer p = c.identity.owner(); var base = HighPowerCombat.exactStateSnapshot(p);
        var shares = base.flatMap(state -> MagneticDomainReserve.savedShares(p, state));
        DIAGNOSTIC_LOG.info("NEW242 stage={} token={} input={} tick={} source={} facet={} absorbed={} spent={} base={} lower={} upper={} hp={} pose={} axis={} reason={}",
                stage, c.identity.token(), c.input.input(), now(p), capture == null ? "none" : capture.arrowWindow == null ? "beam" : "arrow",
                capture == null ? -1 : capture.index, absorbed.toPlainString(), spent.toPlainString(),
                base.map(state -> state.shield().toPlainString()).orElse("unavailable"),
                shares.map(state -> state.lower().toPlainString()).orElse("unavailable"),
                shares.map(state -> state.upper().toPlainString()).orElse("unavailable"),
                base.map(state -> state.health().toPlainString()).orElse("unavailable"),
                stage.equals("start") ? center(p) : "omitted", stage.equals("start") ? p.getLookAngle() : "omitted",
                stage.equals("end") ? c.depleted ? "depleted" : "closed" : "none");
    }
    private static void send(Context c, int phase, int plate, Vec3 point, Vec3 origin, Vec3 axis) {
        ServerPlayer p = c.identity.owner(); if (!finite(point) || !finite(origin) || !finite(axis)) return;
        int liveMask = phase == ENDED || phase == STARTUP ? 0 : 7;
        c.audience.removeIf(v -> v.connection == null || v.hasDisconnected() || v.isRemoved() || v.serverLevel() != c.identity.level() || v.position().distanceToSqr(p.position()) > 64 * 64);
        for (ServerPlayer viewer : c.identity.level().players()) if (viewer.position().distanceToSqr(p.position()) <= 48 * 48) c.audience.add(viewer);
        c.audience.add(p);
        Visual packet = new Visual(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.identity.epoch(), ++c.sequence, phase, origin, axis, liveMask, plate, point);
        for (ServerPlayer viewer : c.audience) if (viewer.connection != null && !viewer.hasDisconnected() && viewer.serverLevel() == c.identity.level()) PacketDistributor.sendToPlayer(viewer, packet);
    }
    public record Visual(int entity, UUID owner, long input, long token, long epoch, long sequence, int phase, Vec3 center, Vec3 axis, int liveMask, int plate, Vec3 point) implements CustomPacketPayload {
        public Visual {
            if (entity < 0 || owner == null || input <= 0 || token <= 0 || epoch <= 0 || sequence <= 0 || phase < ACTIVE || phase > STARTUP || !finite(center) || !finite(axis) || !finite(point)
                    || axis.lengthSqr() < .5 || axis.lengthSqr() > 1.5 || liveMask != (phase == ACTIVE || phase == HIT ? 7 : 0) || plate < -1 || plate > 2 || phase == HIT && plate < 0 || phase != HIT && plate != -1)
                throw new IllegalArgumentException("Invalid bounded NEW242 visual");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_directional_dissipation_curtain"));
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
    @SubscribeEvent public static void serverTick(ServerTickEvent.Pre e) { for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) tick(c.identity.owner()); DissipationCurtainArrowReceipts.prune(e.getServer()); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void death(LivingDeathEvent e) { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void leave(EntityLeaveLevelEvent e) { DissipationCurtainArrowReceipts.removed(e.getEntity()); if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void clone(PlayerEvent.Clone e) {
        if (e.getOriginal() instanceof ServerPlayer old) { remove(old); if (e.getEntity() instanceof ServerPlayer replacement && old.getUUID().equals(replacement.getUUID())) for (String key : List.of(PAYMENT_KEY, MASTERY_KEY)) if (old.getPersistentData().contains(key)) replacement.getPersistentData().put(key, old.getPersistentData().get(key).copy()); }
    }
    public static void clear() { for (Context c : new ArrayList<>(CONTEXTS.values())) finish(c); CONTEXTS.clear(); INPUTS.clear(); DissipationCurtainArrowReceipts.clear(); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) { for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) finish(c); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { clear(); }
}
