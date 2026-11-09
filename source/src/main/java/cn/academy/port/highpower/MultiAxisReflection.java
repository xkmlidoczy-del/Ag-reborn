package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.MultiAxisReflectionGeometry;
import cn.academy.port.highpower.core.MultiAxisReflectionRules;
import cn.academy.port.highpower.core.ExactCombatState;
import cn.academy.port.highpower.core.SharedShieldReserveShares;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.VectorParryRules;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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

/** NEW442: four finite facets; only the certified native-arrow bridge may settle a reflection. */
@EventBusSubscriber(modid = "academy")
public final class MultiAxisReflection {
    public static final String ID = "multi_axis_reflection", PAYMENT_KEY = "academy:multi_axis_reflection_payment",
            QUOTA_KEY = "academy:multi_axis_reflection_quota";
    public static final int STARTUP = 0, ACTIVE = 1, REFLECT = 2, CONTACT = 3, ENDED = 4;
    private static final String CLOCK = "next_" + ID;
    private static final int MAX_INPUTS = 4096, MAX_CANDIDATES = 64;
    private static final UUID NO_ARROW = new UUID(0, 0);
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.multi_axis_reflection");
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<MinecraftServer, PhysicalTick> PHYSICAL = new WeakHashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    public static Consumer<Rejection> clientRejectionReceiver = ignored -> {};
    private record Watermark(ServerPlayer owner, long epoch, long input) {}
    /** Remaining native-player ticks survive stopped world clocks, logout, and reload. */
    private record Payment(int remaining, int restart, long receipt, String dimension, long resourceUntil) {}
    private record Resources(ExactCombatState base, SharedShieldReserveShares shares) {}
    private static final class PhysicalTick {
        final int tick;
        final Set<UUID> players = new HashSet<>();
        final Map<UUID, Integer> admissions = new HashMap<>(), facets = new HashMap<>(), paymentStarts = new HashMap<>();
        PhysicalTick(int tick) { this.tick = tick; }
    }
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final int preset, cooldown;
        final long revision, started;
        final double activation;
        final Set<ServerPlayer> audience = new LinkedHashSet<>();
        long paymentReceipt, lastTick = -1, sequence;
        int lastPhase = -1;
        boolean paying, capturing;
        Claim claim;
        Context(ServerPlayer p, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            started = now(p);
            BigDecimal growth = HighPowerGrowth.values(p).position();
            activation = VectorParryRules.activationCost(growth); cooldown = VectorParryRules.cooldownTicks(growth);
        }
    }
    /** Historical crossing identity. Construction alone never authorizes native impact or resource use. */
    public static final class Capture {
        private final Context context;
        private final Arrow arrow;
        private final int arrowId, facet;
        private final UUID arrowUuid;
        private final Vec3 ownerPosition, center, facing, crossing;
        private final AABB body;
        private final Pose pose;
        private final float yaw, pitch;
        private final long tick;
        private final BigDecimal ceiling;
        private final Resources ledger;
        private boolean committed, reflected, contacted;
        private Vec3 lastObserved;
        private Capture(Context c, Arrow arrow, int facet, Vec3 crossing, BigDecimal ceiling, Resources ledger) {
            ServerPlayer p = c.identity.owner();
            context = c; this.arrow = arrow; arrowId = arrow.getId(); arrowUuid = arrow.getUUID(); this.facet = facet;
            ownerPosition = p.position(); center = center(p); facing = visualFacing(p); body = p.getBoundingBox(); pose = p.getPose();
            yaw = p.getYRot(); pitch = p.getXRot(); tick = now(p); this.crossing = crossing;
            this.ceiling = ceiling; this.ledger = ledger;
        }
    }
    /** Opaque callback-free admission, prepared before the combat ledger publication. */
    public static final class Claim {
        private final Capture capture;
        private final BigDecimal cost;
        private final MultiAxisReflectionRules.Quota before, after;
        private final CompoundTag quotaTag;
        private final PhysicalTick physical;
        private boolean finished;
        private Claim(Capture capture, BigDecimal cost, MultiAxisReflectionRules.Quota quota, CompoundTag quotaTag, PhysicalTick physical) {
            this.capture = capture; this.cost = cost; before = quota; after = quota.consume(capture.facet);
            this.quotaTag = quotaTag; this.physical = physical;
        }
    }
    private MultiAxisReflection() {}

    /** Each fresh authenticated press toggles. Releasing a key does not end the toggle. */
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
            boolean same = owned(p) == old && structurallyValid(old, false);
            finish(old); if (same) return true;
        }
        Payment fence = readPayment(p);
        if (readQuota(p) == null) return reject(p, input, "corrupt");
        if (!MultiAxisReflectionArrowBridge.available() || HighPowerCombat.unsupportedReason(p).isPresent()) return reject(p, input, "unsupported");
        if (!canAct(p)) return reject(p, input, "not_ready");
        if (conflicts(p)) return reject(p, input, "conflict");
        if (CONTEXTS.size() >= MAX_CANDIDATES || nextToken == Long.MAX_VALUE || fence == null || fence.restart > 0
                || !AbilityStorage.get(p).canUse(ID) || available(p).signum() <= 0) return reject(p, input, "not_ready");
        Context c = new Context(p, input); CONTEXTS.put(p.getUUID(), c);
        if (!pay(c, true) || !valid(c, false) || conflicts(p)) {
            finish(c); feedback(p, "not_ready"); return false;
        }
        send(c, STARTUP, null, null); return true;
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
        // Neither the persistent quota nor its current physical-tick deduplication is removed here.
    }
    public static boolean active(ServerPlayer p) {
        Context c = owned(p);
        // A payment/capture transaction reserves the stance before any observable work can reenter.
        return c != null && (busy(c) && snapshotOwned(c) || valid(c, false));
    }
    public static boolean protecting(ServerPlayer p) {
        Context c = owned(p);
        return c != null && MultiAxisReflectionArrowBridge.available() && valid(c, false) && !conflicts(p) && remainingHits(p) > 0
                && available(p).signum() > 0;
    }
    public static boolean holding(ServerPlayer p) { return active(p); }
    public static boolean blocksRecovery(ServerPlayer p) { Context c = owned(p); return c != null && c.paymentReceipt > 0 && structurallyValid(c, false); }
    public static boolean renewalPending(ServerPlayer p) {
        Context c = owned(p); Payment payment = c == null ? null : readPayment(p);
        return c != null && payment != null && c.paymentReceipt > 0 && payment.receipt == c.paymentReceipt
                && payment.remaining == 0 && structurallyValid(c, false);
    }
    /** Current-session estimate only; persistent authority is the remaining physical-tick debt. */
    public static long paidUntil(ServerPlayer p) {
        Context c = owned(p); Payment payment = c == null ? null : readPayment(p);
        return c == null || payment == null || payment.receipt != c.paymentReceipt ? 0 : now(p) + payment.remaining;
    }
    public static int remainingHits(ServerPlayer p) {
        var quota = thread(p) ? readQuota(p) : null;
        if (quota == null) return 0;
        int remaining = 0; for (int age : quota.remainingTicks()) if (age == 0) remaining++;
        return remaining;
    }
    private static boolean busy(Context c) { return c.paying || c.claim != null || c.capturing; }
    private static boolean conflicts(ServerPlayer p) { return WindPressureLift.active(p) || VectorParry.active(p) || InertialAnchor.active(p) || (CirculationRelief.active(p) || AutonomousVectorCircle.active(p)) || BrakingTouch.active(p); }

    /** Opaque session identity for native begin/end pose continuity; a retoggle always changes it. */
    public static Object generation(ServerPlayer p) { Context c = owned(p); return c != null && valid(c, false) ? c : null; }

    /** Constant-time denial path: no controllers means no per-arrow owner or metadata scan. */
    public static boolean hasCandidates(ServerLevel world) {
        return world != null && world.getServer().isSameThread() && !CONTEXTS.isEmpty();
    }

    /** Bounded owner discovery for the bridge's historical flight recorder. */
    public static List<ServerPlayer> candidates(ServerLevel world) {
        if (!hasCandidates(world) || CONTEXTS.size() > MAX_CANDIDATES) return List.of();
        List<ServerPlayer> result = new ArrayList<>();
        int examined = 0;
        for (Context c : CONTEXTS.values()) {
            if (++examined > MAX_CANDIDATES) return List.of();
            if (c.identity.level() == world && valid(c, false) && !busy(c)) result.add(c.identity.owner());
        }
        result.sort(Comparator.comparingInt(ServerPlayer::getId));
        return List.copyOf(result);
    }

    /** Only an observed, completely traversed native segment may be supplied by the bridge. */
    public static Capture capture(ServerPlayer p, Arrow arrow, int facet, Vec3 crossing, Vec3 historicalFrom, Vec3 historicalTo) {
        return capture(p, arrow, facet, crossing, historicalFrom, historicalTo, false);
    }
    /** Separate pending-native path: the bridge must certify the original first body-entry sweep. */
    public static Capture capturePendingNative(ServerPlayer p, Arrow arrow, int facet, Vec3 crossing, Vec3 from, Vec3 to) {
        if (!MultiAxisReflectionArrowBridge.validPendingSweep(p, arrow, facet, crossing, from, to)) return null;
        return capture(p, arrow, facet, crossing, from, to, true);
    }
    private static Capture capture(ServerPlayer p, Arrow arrow, int facet, Vec3 crossing, Vec3 historicalFrom, Vec3 historicalTo, boolean pending) {
        Context c = owned(p);
        if (c == null || busy(c) || !valid(c, false) || conflicts(p) || arrow == null || !finite(crossing)
                || arrow.level() != p.level() || arrow.isRemoved() || !finite(arrow.position())
                || center(p).distanceToSqr(arrow.position()) > 25 || !arrow.position().equals(pending ? historicalFrom : historicalTo)) return null;
        var quota = readQuota(p);
        if (quota == null || !quota.canConsume(facet)) return null;
        c.capturing = true;
        try {
            Vec3 position = p.position(), facing = visualFacing(p); Pose pose = p.getPose(); AABB body = p.getBoundingBox();
            float yaw = p.getYRot(), pitch = p.getXRot();
            var geometry = MultiAxisReflectionGeometry.pose(vector(center(p)), vector(p.getLookAngle()));
            var contact = MultiAxisReflectionGeometry.crossing(geometry, vector(historicalFrom), vector(historicalTo));
            if (contact.isEmpty() || contact.get().facetIndex() != facet || !vec(contact.get().point()).equals(crossing)) return null;
            Resources ledger = resources(p);
            BigDecimal ceiling = available(p);
            if (ledger == null || ceiling.signum() <= 0 || !ledger.equals(resources(p))
                    || !valid(c, false) || !position.equals(p.position()) || !facing.equals(visualFacing(p))
                    || yaw != p.getYRot() || pitch != p.getXRot() || pose != p.getPose() || !body.equals(p.getBoundingBox())) return null;
            return new Capture(c, arrow, facet, crossing, ceiling, ledger);
        } finally { c.capturing = false; }
    }
    public static BigDecimal capturedCeiling(Capture capture) { return capture == null ? BigDecimal.ZERO : capture.ceiling; }
    /** Controller authority only. The bridge separately requires its exact native accepted-impact frame. */
    public static boolean validCapture(ServerPlayer p, Arrow arrow, Capture capture) {
        return capture != null && !capture.committed && owned(p) == capture.context && !capture.context.paying
                && !capture.context.capturing && valid(capture.context, true) && !conflicts(p)
                && now(p) >= capture.tick && capture.ownerPosition.equals(p.position()) && capture.center.equals(center(p))
                && capture.pose == p.getPose() && capture.body.equals(p.getBoundingBox())
                && capture.yaw == p.getYRot() && capture.pitch == p.getXRot() && capture.facing.equals(visualFacing(p))
                && arrowIdentity(p, arrow, capture) && finite(arrow.position()) && center(p).distanceToSqr(arrow.position()) <= 25
                && capture.ledger.equals(resources(p));
    }
    /** Read-only preflight for the exact pending native projectile event.
     * The later authenticated impact still owns claim creation and every debit. */
    static boolean pendingArrowAllowed(ServerPlayer p, Arrow arrow, Capture capture, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || capture == null || busy(capture.context)
                || !validCapture(p, arrow, capture) || amount.compareTo(capture.ceiling) > 0) return false;
        var quota = readQuota(p);
        return quota != null && quota.canConsume(capture.facet)
                && amount.compareTo(capture.ledger.base().shield().add(capture.ledger.shares().lower())) <= 0;
    }
    public static Claim prepareClaim(ServerPlayer p, Arrow arrow, Capture capture, BigDecimal positiveCost) {
        if (positiveCost == null || positiveCost.signum() <= 0 || capture == null || positiveCost.compareTo(capture.ceiling) > 0
                || busy(capture.context) || !validCapture(p, arrow, capture)) return null;
        var quota = readQuota(p); if (quota == null || !quota.canConsume(capture.facet)) return null;
        CompoundTag tag = p.getPersistentData().contains(QUOTA_KEY) ? p.getPersistentData().getCompound(QUOTA_KEY).copy() : null;
        Claim claim = new Claim(capture, positiveCost, quota, tag, physicalTick(p)); capture.context.claim = claim; return claim;
    }
    /** Equality-only guard usable immediately before root publishes its exact resource transaction. */
    public static boolean claimValid(Claim claim) {
        if (claim == null || claim.finished || claim.capture.committed || claim.capture.context.claim != claim) return false;
        Capture capture = claim.capture; ServerPlayer p = capture.context.identity.owner();
        return owned(p) == capture.context && PHYSICAL.get(p.server) == claim.physical && p.server.getTickCount() == claim.physical.tick
                && (claim.quotaTag == null ? !p.getPersistentData().contains(QUOTA_KEY)
                    : claim.quotaTag.equals(p.getPersistentData().get(QUOTA_KEY)));
    }
    /** Call directly after callback-free ledger publication; no resource read, observer, or packet may run here. */
    public static void commitClaim(Claim claim) {
        if (!claimValid(claim)) throw new IllegalStateException("Stale NEW442 quota claim");
        Capture capture = claim.capture; ServerPlayer p = capture.context.identity.owner();
        stampQuota(p, claim.after); capture.committed = true; claim.finished = true; capture.context.claim = null;
        if (!claim.physical.players.contains(p.getUUID())) claim.physical.admissions.merge(p.getUUID(), 1, Integer::sum);
        claim.physical.facets.merge(p.getUUID(), 1 << capture.facet, (a, b) -> a | b);
    }
    public static void abandonClaim(Claim claim) {
        if (claim != null && !claim.finished) {
            claim.finished = true;
            if (claim.capture.context.claim == claim) claim.capture.context.claim = null;
        }
    }
    /** Compatibility entry for a bridge that already prepared this exact claim before publication. */
    public static boolean claim(ServerPlayer p, Arrow arrow, Capture capture, BigDecimal positiveCost) {
        Claim claim = capture == null ? null : capture.context.claim;
        if (claim == null || claim.capture != capture || capture.context.identity.owner() != p || capture.arrow != arrow
                || positiveCost == null || positiveCost.compareTo(claim.cost) != 0 || !claimValid(claim)) return false;
        commitClaim(claim); return true;
    }
    public static void reflected(ServerPlayer p, Arrow arrow, Capture capture) {
        if (capture == null || capture.reflected || !cueAllowed(p, arrow, capture)) return;
        capture.reflected = true; capture.lastObserved = arrow.position(); safeCue(capture, REFLECT, arrow);
    }
    public static void observed(ServerPlayer p, Arrow arrow, Capture capture) {
        if (capture == null || !capture.reflected || capture.contacted || !cueAllowed(p, arrow, capture)
                || arrow.position().equals(capture.lastObserved)) return;
        capture.lastObserved = arrow.position(); safeCue(capture, REFLECT, arrow);
    }
    public static void contact(ServerPlayer p, Arrow arrow, Capture capture) {
        if (capture == null || !capture.reflected || capture.contacted || !cueAllowed(p, arrow, capture)) return;
        capture.contacted = true; safeCue(capture, CONTACT, arrow);
    }
    private static boolean cueAllowed(ServerPlayer p, Arrow arrow, Capture capture) {
        return capture.committed && owned(p) == capture.context && valid(capture.context, false)
                && arrowIdentity(p, arrow, capture) && finite(arrow.position()) && center(p).distanceToSqr(arrow.position()) <= 25;
    }
    private static boolean arrowIdentity(ServerPlayer p, Arrow arrow, Capture capture) {
        return arrow != null && arrow == capture.arrow && arrow.getId() == capture.arrowId && arrow.getUUID().equals(capture.arrowUuid)
                && arrow.level() == p.level() && !arrow.isRemoved() && p.serverLevel().getEntity(arrow.getId()) == arrow
                && p.serverLevel().getEntity(arrow.getUUID()) == arrow;
    }
    private static void safeCue(Capture capture, int phase, Arrow arrow) {
        try { send(capture.context, phase, capture, arrow); }
        catch (RuntimeException | LinkageError failure) { LOG.warn("NEW442 cue unavailable after committed native reflection", failure); }
    }

    /** Maintenance never ages quota; only the deduplicated native player Post event does. */
    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null) return;
        if (!MultiAxisReflectionArrowBridge.available() || !structurallyValid(c, false) || !canAct(p) || conflicts(p)
                || available(p).signum() <= 0 || readQuota(p) == null) { finish(c); return; }
        if (busy(c)) return;
        long tick = now(p); if (tick <= c.lastTick) return; c.lastTick = tick;
        Payment payment = readPayment(p);
        if (payment == null || payment.receipt != c.paymentReceipt || payment.remaining == 0 && !pay(c, false)) { finish(c); return; }
        if (!valid(c, false)) { finish(c); return; }
        if (c.lastPhase != ACTIVE || (tick - c.started) % 4 == 0) send(c, ACTIVE, null, null);
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
        if (p.getPersistentData().contains(QUOTA_KEY)) {
            var quota = readQuota(p);
            if (quota != null && !quota.equals(MultiAxisReflectionRules.Quota.EMPTY)) {
                int[] aged = quota.nativeTick().remainingTicks();
                int added = Math.min(MultiAxisReflectionRules.MAX_REFLECTIONS, clock.admissions.getOrDefault(p.getUUID(), 0));
                for (int i = 0; i < added; i++) aged[i] = MultiAxisReflectionRules.QUOTA_TICKS;
                // A Post between two native impacts must not reopen a facet already used in this server tick.
                stampQuota(p, MultiAxisReflectionRules.Quota.restore(aged, clock.facets.getOrDefault(p.getUUID(), 0)));
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
        double cost = activation ? c.activation : VectorParryRules.sustainCost(HighPowerGrowth.values(p).position());
        if (c.paying || !structurallyValid(c, false) || previous == null || previous.receipt == Long.MAX_VALUE
                || !activation && (previous.remaining > 0 || previous.receipt != c.paymentReceipt)
                || previous.restart > 0 || HighPowerResources.availableCp(p) < cost) return false;
        long worldTick = p.serverLevel().getGameTime();
        if (!VectorParryRules.clock(worldTick) || !resourceClockMatches(p, previous.resourceUntil)) return false;
        c.paying = true;
        CompoundTag budget = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
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
            if (owned(p) != c || !snapshotOwned(c)
                    || !Objects.equals(p.getPersistentData().get(PAYMENT_KEY), oldPayment)
                    || p.getPersistentData().getCompound(HighPowerResources.DATA_KEY) != budget
                    || !budget.contains(CLOCK, Tag.TAG_LONG) || budget.getLong(CLOCK) != worldTick + 1) return false;
            long receipt = previous.receipt + 1;
            int restart = activation ? VectorParryRules.RESTART_FENCE : 0;
            writePayment(p, new Payment(VectorParryRules.PAYMENT_PERIOD, restart, receipt,
                    p.serverLevel().dimension().location().toString(), worldTick + 1));
            c.paymentReceipt = receipt;
            holdPaymentTick(p, 1 | (restart > 0 ? 2 : 0));
            return owned(p) == c && structurallyValid(c, false);
        } finally {
            if (!spent && p.getPersistentData().getCompound(HighPowerResources.DATA_KEY) == budget
                    && budget.contains(CLOCK, Tag.TAG_LONG) && budget.getLong(CLOCK) == worldTick) {
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
    private static MultiAxisReflectionRules.Quota readQuota(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(QUOTA_KEY)) return MultiAxisReflectionRules.Quota.EMPTY;
        if (!root.contains(QUOTA_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = root.getCompound(QUOTA_KEY);
        if (!tag.getAllKeys().equals(Set.of("version", "remaining", "facets")) || !tag.contains("version", Tag.TAG_INT)
                || tag.getInt("version") != 1 || !tag.contains("remaining", Tag.TAG_INT_ARRAY) || !tag.contains("facets", Tag.TAG_INT)) return null;
        try { return MultiAxisReflectionRules.Quota.restore(tag.getIntArray("remaining"), tag.getInt("facets")); }
        catch (IllegalArgumentException malformed) { return null; }
    }
    private static void stampQuota(ServerPlayer p, MultiAxisReflectionRules.Quota quota) {
        CompoundTag tag = new CompoundTag(); tag.putInt("version", 1); tag.putIntArray("remaining", quota.remainingTicks());
        tag.putInt("facets", quota.usedFacetMask()); p.getPersistentData().put(QUOTA_KEY, tag);
    }
    private static boolean canAct(ServerPlayer p) { return basic(p, false) && HighPowerResources.ready(p) && HighPowerCombat.unsupportedReason(p).isEmpty(); }
    private static boolean basic(ServerPlayer p, boolean nativeFrame) {
        if (!thread(p) || p.connection == null || p.hasDisconnected() || p.isRemoved() || !nativeFrame && !p.isAlive()
                || p.isSpectator() || p.getAbilities().instabuild || p.getAbilities().invulnerable || p.noPhysics || p.isPassenger() || p.isVehicle()
                || p.isSleeping() || p.isFallFlying() || p.getAbilities().flying || p.containerMenu != p.inventoryMenu || AbilityConsumption.busy(p)) return false;
        var state = AbilityStorage.peek(p);
        return state != null && HighPowerCombat.enabled(p) && state.category.equals("vecmanip") && state.activated && state.overloadFine && !state.interfering
                && state.learned(ID) && state.learned("vector_cage") && state.level >= 4 && state.level <= 5 && SkillCatalog.enabled("vecmanip", ID)
                && finite(p.position()) && finite(p.getLookAngle()) && finite(p.getDeltaMovement()) && p.getLookAngle().lengthSqr() > .5
                && Float.isFinite(p.getXRot()) && Float.isFinite(p.getYRot()) && VectorParryRules.clock(now(p));
    }
    private static boolean structurallyValid(Context c, boolean nativeFrame) {
        ServerPlayer p = c.identity.owner();
        return owned(p) == c && snapshotOwned(c) && basic(p, nativeFrame) && now(p) >= c.started
                && c.preset == c.identity.state().presets.current() && c.revision == c.identity.state().presets.revision()
                && ID.equals(c.identity.state().presets.currentSkill(c.input.slot()));
    }
    private static boolean snapshotOwned(Context c) {
        ServerPlayer p = c.identity.owner();
        return p.serverLevel() == c.identity.level() && p.level() == c.identity.level() && p.getId() == c.input.actor()
                && AbilityStorage.peek(p) == c.identity.state() && c.identity.level().getEntity(p.getId()) == p && c.identity.level().getEntity(p.getUUID()) == p
                && LegacySingleKeyOwnerEpoch.matches(p, p.connection, c.identity.level(), c.identity.state(), c.identity.epoch());
    }
    private static boolean valid(Context c, boolean nativeFrame) {
        if (c.paying || !structurallyValid(c, nativeFrame)) return false;
        Payment payment = readPayment(c.identity.owner());
        return payment != null && c.paymentReceipt > 0 && payment.receipt == c.paymentReceipt && payment.remaining > 0;
    }
    private static BigDecimal available(ServerPlayer p) { return HighPowerCombat.multiAxisReflectionAvailable(p); }
    private static Resources resources(ServerPlayer p) {
        return HighPowerCombat.exactStateSnapshot(p).flatMap(base -> MagneticDomainReserve.savedShares(p, base)
                .map(shares -> new Resources(base, shares))).orElse(null);
    }
    private static Context owned(ServerPlayer p) { if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null; }
    private static boolean thread(ServerPlayer p) { return p != null && p.server != null && p.server.isSameThread(); }
    private static long now(ServerPlayer p) { return MagneticDomain.serverTime(p.server); }
    private static Vec3 center(ServerPlayer p) { return p.getBoundingBox().getCenter(); }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z) && Math.abs(v.x) <= 30_000_001 && Math.abs(v.y) <= 30_000_001 && Math.abs(v.z) <= 30_000_001; }
    private static MultiAxisReflectionGeometry.Vector vector(Vec3 v) { return v == null ? null : new MultiAxisReflectionGeometry.Vector(v.x, v.y, v.z); }
    private static Vec3 vec(MultiAxisReflectionGeometry.Vector v) { return new Vec3(v.x(), v.y(), v.z()); }
    private static Vec3 visualFacing(ServerPlayer p) {
        double yaw = Math.toRadians(p.getYRot()); return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }
    private static void finish(Context c) {
        ServerPlayer p = c.identity.owner(); if (!CONTEXTS.remove(p.getUUID(), c)) return;
        MultiAxisReflectionArrowBridge.invalidate(p);
        if (c.paymentReceipt > 0) {
            Payment payment = readPayment(p);
            if (payment != null && payment.receipt == c.paymentReceipt) {
                int restart = Math.max(payment.restart, c.cooldown);
                writePayment(p, new Payment(payment.remaining, restart, payment.receipt, payment.dimension, payment.resourceUntil));
                if (restart > payment.restart) holdPaymentTick(p, 2);
            }
            c.identity.state().setCooldown(ID, c.cooldown);
        }
        if (c.claim != null) abandonClaim(c.claim);
        send(c, ENDED, null, null); c.audience.clear();
    }
    private static int liveMask(ServerPlayer p) {
        var quota = readQuota(p); if (quota == null) return 0;
        int mask = 0; for (int facet = 0; facet < 4; facet++) if (quota.canConsume(facet)) mask |= 1 << facet;
        return mask;
    }
    private static void send(Context c, int phase, Capture capture, Arrow arrow) {
        ServerPlayer p = c.identity.owner(); Vec3 point = center(p), facing = visualFacing(p);
        if (c.sequence == Long.MAX_VALUE || !finite(point) || !unit(facing)) return;
        boolean event = phase == REFLECT || phase == CONTACT;
        Vec3 arrowPoint = event ? arrow.position() : point;
        if (!finite(arrowPoint) || point.distanceToSqr(arrowPoint) > 25) return;
        c.audience.removeIf(viewer -> viewer.connection == null || viewer.hasDisconnected() || viewer.isRemoved()
                || viewer.serverLevel() != c.identity.level() || viewer.position().distanceToSqr(p.position()) > 48 * 48);
        for (ServerPlayer viewer : c.identity.level().players()) if (viewer.position().distanceToSqr(p.position()) <= 32 * 32) c.audience.add(viewer);
        c.audience.add(p);
        Visual packet = new Visual(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.identity.epoch(), ++c.sequence,
                phase, point, facing, phase == ENDED ? 0 : remainingHits(p), phase == ENDED ? 0 : liveMask(p),
                event ? capture.facet : -1, event ? arrow.getId() : -1, event ? arrow.getUUID() : NO_ARROW, arrowPoint,
                phase == ENDED ? "0" : available(p).stripTrailingZeros().toPlainString(),
                phase == ENDED ? "0" : HighPowerCombat.multiAxisReflectionCapacity(p).stripTrailingZeros().toPlainString());
        c.lastPhase = phase;
        for (ServerPlayer viewer : c.audience) if (viewer.connection != null && !viewer.hasDisconnected() && viewer.serverLevel() == c.identity.level())
            PacketDistributor.sendToPlayer(viewer, packet);
    }
    private static boolean unit(Vec3 v) { return finite(v) && v.y == 0 && Math.abs(v.lengthSqr() - 1) <= 1e-9; }
    public record Visual(int entity, UUID owner, long input, long token, long epoch, long sequence, int phase,
                         Vec3 point, Vec3 facing, int remainingHits, int liveMask, int facet,
                         int arrowEntity, UUID arrowUuid, Vec3 arrowPoint, String available, String capacity) implements CustomPacketPayload {
        public Visual {
            boolean event = phase == REFLECT || phase == CONTACT;
            BigDecimal availableAmount = visualAmount(available), capacityAmount = visualAmount(capacity);
            if (entity < 0 || owner == null || input <= 0 || token <= 0 || epoch <= 0 || sequence <= 0 || phase < STARTUP || phase > ENDED
                    || !finite(point) || !unit(facing) || !finite(arrowPoint) || point.distanceToSqr(arrowPoint) > 25
                    || remainingHits < 0 || remainingHits > 4 || liveMask < 0 || liveMask > 15 || remainingHits == 0 && liveMask != 0
                    || arrowUuid == null || event && (facet < 0 || facet >= 4 || arrowEntity < 0 || NO_ARROW.equals(arrowUuid))
                    || !event && (facet != -1 || arrowEntity != -1 || !NO_ARROW.equals(arrowUuid) || !arrowPoint.equals(point))
                    || availableAmount.compareTo(capacityAmount) > 0
                    || phase == ENDED && (remainingHits != 0 || liveMask != 0 || availableAmount.signum() != 0 || capacityAmount.signum() != 0))
                throw new IllegalArgumentException("Invalid bounded NEW442 visual");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_multi_axis_reflection"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((buffer, value) -> {
            buffer.writeInt(value.entity); buffer.writeUUID(value.owner); buffer.writeLong(value.input); buffer.writeLong(value.token);
            buffer.writeLong(value.epoch); buffer.writeLong(value.sequence); buffer.writeByte(value.phase);
            writeVec(buffer, value.point); writeVec(buffer, value.facing); buffer.writeByte(value.remainingHits); buffer.writeByte(value.liveMask);
            buffer.writeByte(value.facet); buffer.writeInt(value.arrowEntity); buffer.writeUUID(value.arrowUuid); writeVec(buffer, value.arrowPoint);
            buffer.writeUtf(value.available, 1024); buffer.writeUtf(value.capacity, 1024);
        }, buffer -> new Visual(buffer.readInt(), buffer.readUUID(), buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readLong(),
                buffer.readUnsignedByte(), readVec(buffer), readVec(buffer), buffer.readUnsignedByte(), buffer.readUnsignedByte(),
                buffer.readByte(), buffer.readInt(), buffer.readUUID(), readVec(buffer), buffer.readUtf(1024), buffer.readUtf(1024)));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static BigDecimal visualAmount(String encoded) {
        if (encoded == null || encoded.length() > 1024) throw new IllegalArgumentException("Invalid NEW442 visual amount length");
        BigDecimal amount = CombatAmounts.requireAmount(new BigDecimal(encoded), "NEW442 visual amount");
        if (!amount.stripTrailingZeros().toPlainString().equals(encoded)) throw new IllegalArgumentException("Noncanonical NEW442 visual amount");
        return amount;
    }
    private static void feedback(ServerPlayer p, String key) {
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.academy.highpower.multi_axis_reflection." + key), true);
    }
    private static boolean reject(ServerPlayer p, HighPowerInput.Input input, String reason) {
        feedback(p, reason); return reject(p, input);
    }
    private static boolean reject(ServerPlayer p, HighPowerInput.Input input) {
        PacketDistributor.sendToPlayer(p, new Rejection(p.getId(), p.getUUID(), input.input(), input.epoch()));
        return false;
    }
    /** Refuses only the named authenticated request, without retiring an earlier accepted toggle. */
    public record Rejection(int entity, UUID owner, long input, long epoch) implements CustomPacketPayload {
        public Rejection {
            if (entity < 0 || owner == null || input <= 0 || epoch <= 0) throw new IllegalArgumentException("Invalid NEW442 rejection");
        }
        public static final Type<Rejection> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_multi_axis_reflect_rejected"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Rejection> CODEC = StreamCodec.of((buffer, value) -> {
            buffer.writeInt(value.entity); buffer.writeUUID(value.owner); buffer.writeLong(value.input); buffer.writeLong(value.epoch);
        }, buffer -> new Rejection(buffer.readInt(), buffer.readUUID(), buffer.readLong(), buffer.readLong()));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static void writeVec(RegistryFriendlyByteBuf buffer, Vec3 value) { buffer.writeDouble(value.x); buffer.writeDouble(value.y); buffer.writeDouble(value.z); }
    private static Vec3 readVec(RegistryFriendlyByteBuf buffer) { return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()); }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet));
            event.registrar("1").playToClient(Rejection.TYPE, Rejection.CODEC, (packet, context) -> clientRejectionReceiver.accept(packet));
        }
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Pre event) {
        MultiAxisReflectionArrowBridge.prune(event.getServer());
        for (Context context : new ArrayList<>(CONTEXTS.values())) if (context.identity.owner().server == event.getServer()) tick(context.identity.owner());
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void death(LivingDeathEvent event) { if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void leave(EntityLeaveLevelEvent event) { if (event.getEntity() instanceof ServerPlayer p) remove(p); else MultiAxisReflectionArrowBridge.ownerChanged(event.getEntity()); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer old) {
            remove(old);
            if (event.getEntity() instanceof ServerPlayer replacement && old.getUUID().equals(replacement.getUUID()))
                for (String key : List.of(PAYMENT_KEY, QUOTA_KEY)) if (old.getPersistentData().contains(key))
                    replacement.getPersistentData().put(key, old.getPersistentData().get(key).copy());
        }
    }
    public static void clear() {
        for (Context c : new ArrayList<>(CONTEXTS.values())) finish(c);
        CONTEXTS.clear(); INPUTS.clear(); MultiAxisReflectionArrowBridge.clear();
        // Runtime cleanup never clears persisted admission or grants an extra physical tick.
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == event.getServer()) finish(c);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { clear(); PHYSICAL.remove(event.getServer()); }
}
