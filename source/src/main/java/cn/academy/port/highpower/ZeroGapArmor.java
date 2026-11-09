package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.highpower.core.ZeroGapArmorGeometry;
import cn.academy.port.highpower.core.ZeroGapArmorRules;
import cn.academy.port.highpower.core.ZeroGapArmorShares;
import cn.academy.port.highpower.core.ZeroGapArmorTiming;
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

/** NEW252: paid toggle and authenticated finite contacts; the shared exact ledger owns all debits and physical timing. */
@EventBusSubscriber(modid = "academy")
public final class ZeroGapArmor {
    public static final String ID = "zero_gap_armor", PAYMENT_KEY = "academy:zero_gap_armor_payment", MASTERY_KEY = "academy:zero_gap_armor_mastery";
    public static final int ACTIVE = 0, HIT = 1, ENDED = 2, STARTUP = 3;
    public static final int PERIOD = ZeroGapArmorRules.SUSTAIN_INTERVAL_TICKS, STARTUP_TICKS = ZeroGapArmorRules.STARTUP_TICKS;
    private static final int MAX_COOLDOWN = 60, MAX_INPUTS = 4096;
    private static final String CLOCK = "next_" + ID;
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static long nextToken;
    /** Private QA observer, default-off, at most256 events per process; never changes a transaction. */
    private static final boolean DIAGNOSTIC = Boolean.getBoolean("academy.highpower.zeroGapArmorDiagnostic");
    private static final org.slf4j.Logger DIAGNOSTIC_LOG = org.slf4j.LoggerFactory.getLogger("academy.zero_gap_armor_diagnostics");
    private static int diagnosticEvents;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(ServerPlayer owner, long epoch, long input) {}
    private record Fence(long observed, long until, long stopUntil, String dimension) {}

    static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final int preset, cooldown;
        final long revision, started;
        final long[] generation = {1, 1, 1, 1, 1, 1};
        final Set<ServerPlayer> audience = new LinkedHashSet<>();
        final double activation;
        long paidUntil, lastTick = -1, sequence, breakGeneration;
        boolean paying, processing, validating, planning, closing, diagnosticGap;
        Capture pending;
        Visual lastVisual;
        Context(ServerPlayer p, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input;
            preset = identity.state().presets.current(); revision = identity.state().presets.revision(); started = now(p);
            BigDecimal q = HighPowerGrowth.values(p).position();
            activation = ZeroGapArmorRules.activationCost(q); cooldown = ZeroGapArmorRules.cooldownTicks(q);
        }
    }

    /** A closed source may retain this immutable crossing; there is no public constructor or source-name admission. */
    public static final class Capture {
        private final Context context;
        private final int index;
        private final long generation, breakGeneration, tick;
        private final ZeroGapArmorGeometry.Snapshot geometry;
        private final Vec3 point, start;
        private final BigDecimal baseCeiling, reserveCeiling;
        private final boolean historical;
        private ZeroGapArmorArrowReceipts.Window arrowWindow;
        private ZeroGapArmorShares.Contact planned;
        private ZeroGapArmorShares.Transaction settlement;
        private boolean applied, committed;
        private long appliedGeneration, appliedBreakGeneration, plannedAt = -1;
        private Capture(Context c, int index, ZeroGapArmorGeometry.Snapshot geometry, Vec3 point, Vec3 start,
                        BigDecimal baseCeiling, BigDecimal reserveCeiling, boolean historical) {
            context = c; this.index = index; generation = c.generation[index]; breakGeneration = c.breakGeneration;
            tick = now(c.identity.owner()); this.geometry = geometry; this.point = point; this.start = start;
            this.baseCeiling = baseCeiling; this.reserveCeiling = reserveCeiling; this.historical = historical;
        }
        public int plateIndex() { return index; }
        public BigDecimal baseCeiling() { return baseCeiling; }
        public BigDecimal reserveCeiling() { return reserveCeiling; }
        public BigDecimal ceiling() { return baseCeiling.add(reserveCeiling); }
    }

    private ZeroGapArmor() {}

    /** A fresh press opens a paid startup, or closes the existing activation without spending CP. */
    public static boolean toggle(ServerPlayer p, String wire) {
        Context resolving = owned(p);
        if (resolving != null && busy(resolving)) return false;
        HighPowerInput.Input input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input) || AbilityConsumption.busy(p)) return false;
        Watermark previous = INPUTS.get(p.getUUID());
        if (previous != null && previous.owner == p && previous.epoch == input.epoch() && previous.input >= input.input()) return false;
        if (previous == null && INPUTS.size() >= MAX_INPUTS) return false;
        watermark(p, input);
        if (!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))) return false;
        Context old = CONTEXTS.get(p.getUUID());
        if (old != null) {
            boolean same = owned(p) == old && structurallyValid(old, false);
            finish(old);
            if (same) return true;
        }
        Fence fence = readFence(p);
        if (canAct(p) && conflicts(p)) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("screen.academy.highpower.zero_gap_armor.conflict"), true);
            return false;
        }
        if (canAct(p) && !usableStorage(p)) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("screen.academy.highpower.zero_gap_armor.unavailable"), true);
            return false;
        }
        // Reads never enroll or repair child shares, and another paid defense is rejected before payment.
        if (!canAct(p) || nextToken == Long.MAX_VALUE || CONTEXTS.size() >= ZeroGapArmorArrowReceipts.SCAN_LIMIT
                || fence == null || now(p) < fence.stopUntil || !AbilityStorage.get(p).canUse(ID)
                || conflicts(p) || !usableStorage(p)) return false;
        Context c = new Context(p, input); CONTEXTS.put(p.getUUID(), c);
        if (!pay(c, true) || !paid(c, false)) { finish(c); return false; }
        send(c, STARTUP, -1, center(p));
        diagnostic(c, "start", -1, null);
        return true;
    }
    public static boolean start(ServerPlayer p, String wire) { return toggle(p, wire); }
    /** Key-up is intentionally harmless for a toggle. Explicit abort still closes the matching activation. */
    public static boolean release(ServerPlayer p, String wire) {
        HighPowerInput.Input input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Context c = owned(p);
        return c != null && c.input.equals(input);
    }
    public static boolean abort(ServerPlayer p, String wire) {
        Context c = owned(p);
        if (c != null && busy(c)) return false;
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
        abort(p);
        Watermark watermark = INPUTS.get(p.getUUID());
        if (watermark != null && watermark.owner == p) INPUTS.remove(p.getUUID(), watermark);
    }

    public static boolean holding(ServerPlayer p) { Context c = owned(p); return c != null && paid(c, false); }
    public static boolean active(ServerPlayer p) { Context c = owned(p); return c != null && current(c, false); }
    public static boolean blocksRecovery(ServerPlayer p) { Context c = owned(p); return c != null && c.paidUntil > 0 && structurallyValid(c, false); }
    public static boolean renewalPending(ServerPlayer p) { Context c = owned(p); return c != null && c.paidUntil > 0 && now(p) >= c.paidUntil && structurallyValid(c, false); }
    public static long paidUntil(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.paidUntil; }
    /** Only a caller's independently authenticated eligible source may use this to exclude other MD defenses. */
    public static boolean blocksOtherDefense(ServerPlayer p) {
        if (!thread(p)) return false;
        var saved = ZeroGapArmorStorage.capture(p);
        if (saved.isEmpty() || saved.get().armor().isEmpty() || saved.get().timing().isEmpty()) return false;
        if (saved.get().timing().get().openGap()) return true;
        Context c = owned(p);
        return c != null && now(p) >= c.started + STARTUP_TICKS && paid(c, false);
    }
    public static Capture captureIncoming(ServerPlayer p, DamageSource source) { return ZeroGapArmorArrowReceipts.claim(p, source); }
    /** Origin is the exact native beam origin supplied by the closed combat permit, never a shifted defensive ray. */
    public static Capture captureBeam(ServerPlayer p, ServerPlayer attacker, Vec3 origin, Vec3 endpoint, LivingEntity exactTarget) {
        if (p == null || p != exactTarget || attacker == null || attacker == p || attacker.level() != p.level()) return null;
        return crossing(p, origin, endpoint, false);
    }
    static Capture crossing(ServerPlayer p, Vec3 start, Vec3 end, boolean historical) {
        Context c = owned(p);
        if (c == null || busy(c) || !current(c, false) || !finite(start) || !finite(end)) return null;
        var saved = ZeroGapArmorStorage.capture(p);
        if (saved.isEmpty() || saved.get().armor().isEmpty() || saved.get().timing().isEmpty()) return null;
        ZeroGapArmorShares shares = saved.get().armor().get();
        ZeroGapArmorGeometry.Snapshot geometry = pose(p, saved.get().timing().get());
        // Exhausted selected plates are physical misses; no sibling or unselected plate may fund them.
        List<ZeroGapArmorGeometry.Plate> funded = new ArrayList<>();
        for (var plate : geometry.plates()) if (plate.active() && shares.plate(plate.id()).current().signum() > 0) funded.add(plate);
        var contact = ZeroGapArmorGeometry.nearestContact(funded, vector(start), vector(end));
        if (contact.isEmpty()) return null;
        var hit = contact.get(); Vec3 point = vec(hit.point());
        var plate = shares.plate(hit.plateId());
        c.validating = true;
        try {
            if (!guard(p, center(p), point) || !guard(p, start, point) || !current(c, false)) return null;
            var fresh = ZeroGapArmorStorage.capture(p);
            if (fresh.isEmpty() || fresh.get().armor().isEmpty() || fresh.get().timing().isEmpty()
                    || !sameShares(shares, fresh.get().armor().get()) || !saved.get().base().equals(fresh.get().base())
                    || !saved.get().timing().equals(fresh.get().timing()) || !geometry.equals(pose(p, fresh.get().timing().get()))) return null;
            return new Capture(c, hit.plateId(), geometry, point, start, plate.baseCurrent(), plate.reserveCurrent(), historical);
        } finally { c.validating = false; closeDeferred(c); }
    }
    public static boolean matches(ServerPlayer p, Capture capture) {
        if (capture == null || capture.committed || capture.applied || owned(p) != capture.context || capture.context.validating || capture.context.planning
                || !current(capture.context, HighPowerCombat.zeroGapNativeFrame(p, capture))
                || capture.arrowWindow != null && !ZeroGapArmorArrowReceipts.validWindow(capture.arrowWindow)
                || capture.context.generation[capture.index] != capture.generation || capture.context.breakGeneration != capture.breakGeneration
                || now(p) < capture.tick || (!capture.historical && now(p) != capture.tick)
                || capture.historical && now(p) - capture.tick >= PERIOD) return false;
        var saved = ZeroGapArmorStorage.capture(p);
        if (saved.isEmpty() || saved.get().armor().isEmpty() || saved.get().timing().isEmpty()) return false;
        var geometry = pose(p, saved.get().timing().get());
        return geometry.plates().get(capture.index).active() && saved.get().armor().get().plate(capture.index).current().signum() > 0
                && (capture.historical || geometry.equals(capture.geometry))
                && loadedPoint(p, capture.point) && loadedPoint(p, capture.start);
    }
    static boolean bindArrowWindow(Capture capture, ZeroGapArmorArrowReceipts.Window window) {
        if (capture == null || !capture.historical || capture.committed || capture.arrowWindow != null || window == null) return false;
        capture.arrowWindow = window; return true;
    }

    /** Pure proposal only. The caller persists all parents/children and final HP in the shared atomic boundary. */
    static ZeroGapArmorShares.Contact plan(ServerPlayer p, Capture capture, ZeroGapArmorStorage.Capture saved, BigDecimal incoming) {
        if (saved == null || incoming == null || saved.armor().isEmpty() || !matches(p, capture)) return null;
        var fresh = ZeroGapArmorStorage.capture(p);
        if (fresh.isEmpty() || fresh.get().armor().isEmpty() || !sameShares(saved.armor().get(), fresh.get().armor().get())
                || !saved.base().equals(fresh.get().base()) || !saved.timing().equals(fresh.get().timing())) return null;
        Context c = capture.context; c.planning = true;
        try {
            var result = saved.armor().get().contact(capture.index, incoming, capture.baseCeiling, capture.reserveCeiling);
            capture.planned = result; capture.plannedAt = now(p); c.pending = capture;
            return result;
        } finally { c.planning = false; closeDeferred(c); }
    }

    /** Authorize exactly the deterministic remainder of the original native contact, before the atomic resource apply.
     * No caller-proposed shares or damage can replace the captured plan. A stale, foreign or already applied capture fails closed. */
    static ZeroGapArmorShares.Transaction authorizeSettlement(ServerPlayer p,Capture capture,ZeroGapArmorShares.Contact result,
                                                               cn.academy.port.highpower.core.ExactCombatState finalBase) {
        if(result==null||capture==null||capture.planned!=result||capture.context.pending!=capture
                ||capture.settlement!=null||capture.plannedAt!=now(p)||!matches(p,capture)
                ||result.baseAbsorbed().compareTo(capture.baseCeiling)>0||result.reserveAbsorbed().compareTo(capture.reserveCeiling)>0
                ||!HighPowerCombat.zeroGapNativeFrame(p,capture))throw new IllegalStateException("Unowned or stale252 final settlement");
        var saved=ZeroGapArmorStorage.capture(p);
        if(saved.isEmpty()||saved.get().armor().isEmpty()||!sameShares(result.transaction().before(),saved.get().armor().get())
                ||!result.transaction().before().base().equals(saved.get().base()))throw new IllegalStateException("Changed252 pre-settlement resources");
        var settled=result.finishResidual(EsperShields.eligible(p));
        if(!settled.after().base().equals(finalBase))throw new IllegalStateException("Noncanonical252 residual settlement");
        capture.settlement=settled;return settled;
    }

    /** Called immediately after shared apply and before mutable resource observers, particles or experience. */
    public static void resourcesChanged(ServerPlayer p, ZeroGapArmorShares before, ZeroGapArmorShares after) {
        Context c = owned(p);
        if (c == null || before == null || after == null) return;
        boolean broken = false;
        for (int i = 0; i < c.generation.length; i++) {
            if (before.plate(i).current().signum() > 0 && after.plate(i).current().signum() == 0) {
                if (c.generation[i] == Long.MAX_VALUE) c.closing = true;
                else c.generation[i]++;
                ZeroGapArmorArrowReceipts.invalidatePlate(p, i); broken = true;
            }
        }
        if (broken) {
            if (DIAGNOSTIC) c.diagnosticGap = true;
            if (c.breakGeneration == Long.MAX_VALUE) c.closing = true;
            else c.breakGeneration++;
            // The whole armor is open during break debt. No old other-plate receipt revives after it.
            ZeroGapArmorArrowReceipts.invalidate(p);
        }
        Capture pending = c.pending;
        if (pending != null && !pending.committed && !pending.applied && pending.planned != null
                && pending.planned.absorbed().signum() > 0 && now(p) == pending.plannedAt
                && sameShares(before, pending.planned.transaction().before())
                && sameShares(after,pending.settlement==null?pending.planned.shares():pending.settlement.after())) {
            pending.applied = true;
            pending.appliedGeneration = c.generation[pending.index]; pending.appliedBreakGeneration = c.breakGeneration;
        }
    }

    /** Positive, applied exact contact only. Generation invalidation and saved break debt already precede this callback. */
    public static void committed(ServerPlayer p, Capture capture, ZeroGapArmorShares.Contact result) {
        if (capture == null || result == null || capture.committed || !capture.applied || capture.planned != result
                || result.absorbed().signum() <= 0 || result.baseAbsorbed().compareTo(capture.baseCeiling) > 0
                || result.reserveAbsorbed().compareTo(capture.reserveCeiling) > 0 || owned(p) != capture.context
                || capture.context.pending != capture || capture.context.generation[capture.index] != capture.appliedGeneration
                || capture.context.breakGeneration != capture.appliedBreakGeneration || !HighPowerCombat.zeroGapNativeFrame(p, capture)) return;
        var saved = ZeroGapArmorStorage.read(p);
        if (saved.isEmpty() || !sameShares(saved.get(),capture.settlement==null?result.shares():capture.settlement.after())) return;
        capture.committed = true; capture.context.pending = null;
        // Packet pose/masks are current; the renderer omits a historical point outside its current numbered plate.
        send(capture.context, HIT, capture.index, capture.point);
        diagnostic(capture.context, "committed", capture.index, result);
        reward(capture.context);
    }

    private static void reward(Context c) {
        ServerPlayer p = c.identity.owner(); long tick = now(p);
        if (busy(c) || c.closing || owned(p) != c || !snapshotOwned(c) || !clock(tick)
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
        finally { c.processing = false; closeDeferred(c); }
    }
    static Object activation(Capture capture) { return capture.context; }
    static Vec3 point(Capture capture) { return capture.point; }
    static List<ServerPlayer> candidates(ServerLevel world) {
        if (CONTEXTS.size() > ZeroGapArmorArrowReceipts.SCAN_LIMIT) return null;
        List<ServerPlayer> result = new ArrayList<>();
        for (Context c : CONTEXTS.values()) if (c.identity.level() == world && current(c, false)) result.add(c.identity.owner());
        return result;
    }

    /** Controller tick only. It must never advance persisted phase or break debt. */
    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null || busy(c)) return;
        if (c.closing || !structurallyValid(c, false) || !canAct(p) || conflicts(p) || !usableStorage(p)) { finish(c); return; }
        long tick = now(p); if (tick <= c.lastTick) return; c.lastTick = tick;
        if (tick >= c.paidUntil && !pay(c, false)) { finish(c); return; }
        if (!paid(c, false)) { finish(c); return; }
        if (DIAGNOSTIC && c.diagnosticGap) {
            var timing = ZeroGapArmorStorage.capture(p).flatMap(ZeroGapArmorStorage.Capture::timing);
            if (timing.isPresent() && !timing.get().openGap()) {
                c.diagnosticGap = false; diagnostic(c, "gap_closed", -1, null);
            }
        }
        send(c, tick < c.started + STARTUP_TICKS ? STARTUP : ACTIVE, -1, center(p));
    }
    private static boolean pay(Context c, boolean activation) {
        ServerPlayer p = c.identity.owner(); long tick = now(p); Fence previous = readFence(p);
        double cost = activation ? c.activation : ZeroGapArmorRules.sustainCost(HighPowerGrowth.values(p).position());
        if (busy(c) || c.closing || !structurallyValid(c, false) || previous == null || !activation && tick < previous.until
                || tick < previous.stopUntil || conflicts(p) || !usableStorage(p) || HighPowerResources.availableCp(p) < cost) return false;
        c.paying = true;
        try {
            String dimension = p.serverLevel().dimension().location().toString();
            CompoundTag budget = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
            boolean translate = !previous.dimension.isEmpty() && !previous.dimension.equals(dimension);
            Tag oldClock = budget.contains(CLOCK) ? budget.get(CLOCK).copy() : null;
            if (translate) { budget.putLong(CLOCK, p.serverLevel().getGameTime()); p.getPersistentData().put(HighPowerResources.DATA_KEY, budget); }
            if (!HighPowerResources.trySpend(p, ID, cost, 1)) {
                if (translate) {
                    var restore = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
                    if (oldClock == null) restore.remove(CLOCK); else restore.put(CLOCK, oldClock);
                    p.getPersistentData().put(HighPowerResources.DATA_KEY, restore);
                }
                return false;
            }
            writeFence(p, new Fence(tick, tick + PERIOD, previous.stopUntil, dimension)); c.paidUntil = tick + PERIOD;
            return owned(p) == c && !c.closing && structurallyValid(c, false);
        } finally { c.paying = false; closeDeferred(c); }
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
    private static void writeFence(ServerPlayer p, Fence fence) {
        CompoundTag t = new CompoundTag(); t.putInt("version", 1); t.putLong("observed", fence.observed); t.putLong("until", fence.until);
        t.putLong("stopUntil", fence.stopUntil); t.putString("dimension", fence.dimension); p.getPersistentData().put(PAYMENT_KEY, t);
    }
    private static boolean conflicts(ServerPlayer p) { return ScatterPlates.blocksRecovery(p) || DissipationCurtain.blocksRecovery(p); }
    private static boolean usableStorage(ServerPlayer p) {
        var saved = ZeroGapArmorStorage.capture(p);
        return saved.isPresent() && saved.get().armor().isPresent() && saved.get().timing().isPresent();
    }
    private static boolean canAct(ServerPlayer p) { return basic(p, false) && HighPowerResources.ready(p) && HighPowerCombat.unsupportedReason(p).isEmpty(); }
    private static boolean basic(ServerPlayer p, boolean nativeDamageFrame) {
        if (!thread(p) || p.connection == null || p.hasDisconnected() || p.isRemoved() || !nativeDamageFrame && !p.isAlive() || p.isSpectator() || p.getAbilities().instabuild
                || p.getAbilities().invulnerable || p.noPhysics || p.isPassenger() || p.isSleeping() || p.containerMenu != p.inventoryMenu || AbilityConsumption.busy(p)) return false;
        var s = AbilityStorage.peek(p);
        return s != null && s.category.equals("meltdowner") && s.activated && s.overloadFine && !s.interfering
                && s.learned(ID) && s.learned(ScatterPlates.ID) && s.learned(DissipationCurtain.ID) && s.learned(DualPoint.ID)
                && s.level == 5 && SkillCatalog.enabled("meltdowner", ID)
                && boundedFeet(p.position()) && finite(p.getLookAngle()) && finite(p.getDeltaMovement())
                && Float.isFinite(p.getXRot()) && Float.isFinite(p.getYRot()) && clock(now(p));
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
    private static boolean current(Context c, boolean nativeDamageFrame) {
        if (now(c.identity.owner()) < c.started + STARTUP_TICKS || !paid(c, nativeDamageFrame)) return false;
        var saved = ZeroGapArmorStorage.capture(c.identity.owner());
        return saved.isPresent() && saved.get().timing().isPresent() && !saved.get().timing().get().openGap();
    }
    private static boolean paid(Context c, boolean nativeDamageFrame) {
        if (c.closing || c.paying || !structurallyValid(c, nativeDamageFrame) || now(c.identity.owner()) >= c.paidUntil || !usableStorage(c.identity.owner())) return false;
        Fence fence = readFence(c.identity.owner());
        return fence != null && fence.until == c.paidUntil && now(c.identity.owner()) >= fence.observed;
    }
    /** Loaded-world/permission checks are bounded and only run before capture, never in settlement. */
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
        if (!finite(point)) return false;
        BlockPos pos = BlockPos.containing(point); ServerLevel world = p.serverLevel();
        return !world.isOutsideBuildHeight(pos) && world.hasChunkAt(pos) && world.getWorldBorder().isWithinBounds(pos);
    }
    private static boolean sameShares(ZeroGapArmorShares first, ZeroGapArmorShares second) { return first.toDecimalMap().equals(second.toDecimalMap()); }
    private static boolean busy(Context c) { return c.paying || c.processing || c.validating || c.planning; }
    private static void closeDeferred(Context c) { if (c.closing && !busy(c)) finish(c); }
    private static Context owned(ServerPlayer p) { if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null; }
    private static boolean thread(ServerPlayer p) { return p != null && p.server != null && p.server.isSameThread(); }
    private static boolean clock(long tick) { return tick >= 0 && tick <= Long.MAX_VALUE - MAX_COOLDOWN; }
    static long now(ServerPlayer p) { return MagneticDomain.serverTime(p.server); }
    static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z) && Math.abs(v.x) <= 30_000_001 && Math.abs(v.y) <= 30_000_001 && Math.abs(v.z) <= 30_000_001; }
    private static boolean boundedFeet(Vec3 feet) {
        return finite(feet) && Math.abs(feet.x) <= 29_999_997 && Math.abs(feet.y) <= 29_999_997 && Math.abs(feet.z) <= 29_999_997;
    }
    static Vec3 center(ServerPlayer p) { return p.position().add(0, ZeroGapArmorGeometry.CENTER_HEIGHT, 0); }
    static ZeroGapArmorGeometry.Snapshot pose(ServerPlayer p, ZeroGapArmorTiming timing) { return ZeroGapArmorGeometry.capture(vector(p.position()), p.getYRot(), timing); }
    static Vector vector(Vec3 v) { return new Vector(v.x, v.y, v.z); }
    static Vec3 vec(Vector v) { return new Vec3(v.x(), v.y(), v.z()); }

    private static void finish(Context c) {
        if (busy(c)) { c.closing = true; return; }
        ServerPlayer p = c.identity.owner(); if (!CONTEXTS.remove(p.getUUID(), c)) return;
        c.closing = true; c.pending = null;
        diagnostic(c, "end", -1, null);
        ZeroGapArmorArrowReceipts.invalidate(p);
        if (c.paidUntil > 0) {
            Fence fence = readFence(p); long tick = now(p);
            if (fence != null && clock(tick)) writeFence(p, new Fence(tick, fence.until, Math.max(fence.stopUntil, tick + c.cooldown), fence.dimension));
            c.identity.state().setCooldown(ID, c.cooldown);
        }
        send(c, ENDED, -1, center(p)); c.audience.clear();
    }
    private static void send(Context c, int phase, int plate, Vec3 point) {
        ServerPlayer p = c.identity.owner();
        if (c.sequence == Long.MAX_VALUE) return;
        Visual packet;
        var saved = ZeroGapArmorStorage.capture(p);
        if (boundedFeet(p.position()) && Float.isFinite(p.getYRot()) && finite(point)
                && saved.isPresent() && saved.get().armor().isPresent() && saved.get().timing().isPresent()) {
            var timing = saved.get().timing().get(); var geometry = pose(p, timing); int liveMask = 0;
            if (phase != ENDED && phase != STARTUP && !timing.openGap())
                for (var face : geometry.plates()) if (face.active() && saved.get().armor().get().plate(face.id()).current().signum() > 0) liveMask |= 1 << face.id();
            packet = new Visual(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.identity.epoch(), ++c.sequence, phase,
                    p.position(), geometry.facingYawDegrees(), timing.phaseTick(), geometry.activeMask(), liveMask, timing.breakTicks(), plate,
                    phase == HIT ? point : p.position());
        } else {
            // Corrupt storage or invalid motion still removes the last authenticated visual immediately.
            Visual last = c.lastVisual;
            if (phase != ENDED || last == null) return;
            packet = new Visual(last.entity, last.owner, last.input, last.token, last.epoch, ++c.sequence, ENDED,
                    last.feet, last.yaw, last.phaseTick, last.selectedMask, 0, last.breakTicks, -1, last.feet);
        }
        c.lastVisual = packet;
        c.audience.removeIf(v -> v.connection == null || v.hasDisconnected() || v.isRemoved() || v.serverLevel() != c.identity.level()
                || phase != ENDED && v.position().distanceToSqr(p.position()) > 64 * 64);
        if (finite(p.position())) for (ServerPlayer viewer : c.identity.level().players())
            if (viewer.position().distanceToSqr(p.position()) <= 48 * 48) c.audience.add(viewer);
        c.audience.add(p);
        for (ServerPlayer viewer : c.audience)
            if (viewer.connection != null && !viewer.hasDisconnected() && viewer.serverLevel() == c.identity.level()) PacketDistributor.sendToPlayer(viewer, packet);
    }

    private static void diagnostic(Context c, String stage, int plate, ZeroGapArmorShares.Contact contact) {
        if (!DIAGNOSTIC || diagnosticEvents >= 256 || !thread(c.identity.owner())) return;
        var saved = ZeroGapArmorStorage.capture(c.identity.owner());
        if (saved.isEmpty() || saved.get().armor().isEmpty() || saved.get().timing().isEmpty()) return;
        var armor = saved.get().armor().get(); var timing = saved.get().timing().get();
        diagnosticEvents++;
        DIAGNOSTIC_LOG.info("NEW252 stage={} token={} tick={} plate={} absorbed={} baseSpent={} reserveSpent={} hp={} base={} lower={} upper={} phase={} gap={} feet={} yaw={}",
                stage, c.identity.token(), now(c.identity.owner()), plate,
                contact == null ? BigDecimal.ZERO : contact.absorbed(),
                contact == null ? BigDecimal.ZERO : contact.baseAbsorbed(),
                contact == null ? BigDecimal.ZERO : contact.reserveAbsorbed(),
                saved.get().base().health(), saved.get().base().shield(), armor.reserves().lower(), armor.reserves().upper(),
                timing.phaseTick(), timing.breakTicks(), c.identity.owner().position(), c.identity.owner().getYRot());
    }

    /** Authoritative physical phase: clients need no free-running phase clock or unbounded extrapolation. */
    public record Visual(int entity, UUID owner, long input, long token, long epoch, long sequence, int phase,
                         Vec3 feet, double yaw, int phaseTick, int selectedMask, int liveMask, int breakTicks, int plate, Vec3 point) implements CustomPacketPayload {
        public Visual {
            if (entity < 0 || owner == null || input <= 0 || token <= 0 || epoch <= 0 || sequence <= 0 || phase < ACTIVE || phase > STARTUP
                    || !finite(feet) || !finite(point) || !Double.isFinite(yaw) || yaw < 0 || yaw >= 360
                    || phaseTick < 0 || phaseTick >= ZeroGapArmorTiming.REVOLUTION_TICKS || breakTicks < 0 || breakTicks > ZeroGapArmorTiming.BREAK_TICKS
                    || selectedMask < 0 || selectedMask > 63 || Integer.bitCount(selectedMask) != 3 || liveMask < 0 || liveMask > 63 || (liveMask & ~selectedMask) != 0
                    || (phase == ENDED || phase == STARTUP || breakTicks > 0) && liveMask != 0
                    || plate < -1 || plate >= ZeroGapArmorGeometry.PLATE_COUNT || phase == HIT && plate < 0 || phase != HIT && plate != -1
                    || selectedMask != ZeroGapArmorGeometry.capture(vector(feet), yaw, phaseTick).activeMask())
                throw new IllegalArgumentException("Invalid bounded NEW252 visual");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_zero_gap_armor"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.token); b.writeLong(v.epoch); b.writeLong(v.sequence); b.writeByte(v.phase);
            writeVec(b, v.feet); b.writeDouble(v.yaw); b.writeByte(v.phaseTick); b.writeByte(v.selectedMask); b.writeByte(v.liveMask); b.writeByte(v.breakTicks); b.writeByte(v.plate); writeVec(b, v.point);
        }, b -> new Visual(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readUnsignedByte(),
                readVec(b), b.readDouble(), b.readUnsignedByte(), b.readUnsignedByte(), b.readUnsignedByte(), b.readUnsignedByte(), b.readByte(), readVec(b)));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static void writeVec(RegistryFriendlyByteBuf buffer, Vec3 vector) { buffer.writeDouble(vector.x); buffer.writeDouble(vector.y); buffer.writeDouble(vector.z); }
    private static Vec3 readVec(RegistryFriendlyByteBuf buffer) { return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()); }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) { event.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet)); }
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Pre event) {
        INPUTS.entrySet().removeIf(entry -> entry.getValue().owner.server == event.getServer()
                && (entry.getValue().owner.connection == null || entry.getValue().owner.hasDisconnected() || entry.getValue().owner.isRemoved()));
        for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == event.getServer()) tick(c.identity.owner());
        ZeroGapArmorArrowReceipts.prune(event.getServer());
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void death(LivingDeathEvent event) { if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void leave(EntityLeaveLevelEvent event) { ZeroGapArmorArrowReceipts.removed(event.getEntity()); if (event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer old) {
            remove(old);
            if (event.getEntity() instanceof ServerPlayer replacement && old.getUUID().equals(replacement.getUUID()))
                for (String key : List.of(PAYMENT_KEY, MASTERY_KEY)) if (old.getPersistentData().contains(key)) replacement.getPersistentData().put(key, old.getPersistentData().get(key).copy());
        }
    }
    public static void clear() { for (Context c : new ArrayList<>(CONTEXTS.values())) finish(c); CONTEXTS.clear(); INPUTS.clear(); ZeroGapArmorArrowReceipts.clear(); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == event.getServer()) finish(c); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { clear(); }
}
