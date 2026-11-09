package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.core.SkillTuning;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.CirculationReliefRules;
import cn.academy.port.highpower.core.ExactCombatState;
import cn.academy.port.highpower.core.VectorParryRules;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
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

/** NEW432: paid side-sector toggle, exact base-S absorption and a real vertical ground sink. */
@EventBusSubscriber(modid = "academy")
public final class CirculationRelief {
    public static final String ID = "circulation_relief", PAYMENT_KEY = "academy:circulation_relief_payment",
            QUOTA_KEY = "academy:circulation_relief_quota";
    public static final int ACTIVE = 0, ABSORBED = 1, PAUSED = 2, ENDED = 3;
    private static final String CLOCK = "next_" + ID;
    private static final int MAX_INPUTS = 4096;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.circulation_relief");
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<MinecraftServer, PhysicalTick> PHYSICAL = new WeakHashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    public static Consumer<Rejection> clientRejectionReceiver = ignored -> {};
    private record Watermark(ServerPlayer owner, long epoch, long input) {}
    /** Native player-tick debt, never a deadline in a world or process clock. */
    private record Payment(int remaining, int restart, long receipt, String dimension, long resourceUntil) {}
    private record Ground(BlockPos block, BlockState state, Vec3 point) {}
    private static final class PhysicalTick {
        final int tick;
        final Set<UUID> players = new HashSet<>();
        final Map<UUID, Integer> admissions = new HashMap<>();
        final Map<UUID, Integer> paymentStarts = new HashMap<>();
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
        boolean paying, planning, capturing;
        Context(ServerPlayer p, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            started = now(p);
            BigDecimal growth = HighPowerGrowth.values(p).position();
            activation = VectorParryRules.activationCost(growth); cooldown = VectorParryRules.cooldownTicks(growth);
        }
    }
    /** Opaque source and owner-pose snapshot captured before native settlement callbacks. */
    public static final class Capture {
        private final Context context;
        private final DamageSource source;
        private final Entity direct;
        private final LivingEntity attacker;
        private final int directId, attackerId;
        private final UUID directUuid, attackerUuid;
        private final Vec3 sourcePosition, ownerPosition, facing, contact;
        private final AABB body;
        private final Pose pose;
        private final Ground ground;
        private final long tick;
        private boolean committed;
        private VectorParryRules.Result planned;
        private CirculationReliefRules.Quota quota;
        private Capture(Context c, DamageSource source, LivingEntity attacker, Vec3 origin,
                        Vec3 position, Vec3 facing, Pose pose, AABB body, Ground ground) {
            context = c; this.source = source; this.attacker = attacker; direct = source.getDirectEntity();
            directId = direct.getId(); directUuid = direct.getUUID(); attackerId = attacker.getId(); attackerUuid = attacker.getUUID();
            sourcePosition = origin; ownerPosition = position; this.facing = facing;
            this.pose = pose; this.body = body; this.ground = ground; tick = now(c.identity.owner());
            Vec3 delta = origin.subtract(position.add(0, 1, 0));
            contact = position.add(0, 1, 0).add(delta.lengthSqr() > 1e-12 ? delta.normalize() : Vec3.ZERO);
        }
    }
    private CirculationRelief() {}

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
        if (!AnchorDamagePostProof.available() || HighPowerCombat.unsupportedReason(p).isPresent()) return reject(p, input, "unsupported");
        if (!canAct(p)) return reject(p, input, "not_ready");
        if (conflicts(p)) return reject(p, input, "conflict");
        if (ground(p, p.position()) == null) return reject(p, input, "ground_required");
        if (nextToken == Long.MAX_VALUE || fence == null || fence.restart > 0
                || !AbilityStorage.get(p).canUse(ID) || available(p).signum() <= 0) return reject(p, input, "not_ready");
        Context c = new Context(p, input); CONTEXTS.put(p.getUUID(), c);
        if (!pay(c, true) || !valid(c, false) || conflicts(p)) {
            finish(c); feedback(p, "not_ready"); return false;
        }
        sendState(c); return true;
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
        return c != null && AnchorDamagePostProof.available() && valid(c, false) && !conflicts(p) && remainingHits(p) > 0
                && available(p).signum() > 0 && ground(p, p.position()) != null;
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
    private static boolean busy(Context c) { return c.paying || c.planning || c.capturing; }
    private static boolean conflicts(ServerPlayer p) { return AutonomousVectorCircle.active(p) || (MultiAxisReflection.active(p) || WindPressureLift.active(p)) || VectorParry.active(p) || InertialAnchor.active(p) || BrakingTouch.active(p); }

    static Capture capture(ServerPlayer p, DamageSource source) {
        Context c = owned(p);
        if (c == null || busy(c) || !valid(c, false) || conflicts(p) || remainingHits(p) == 0 || !AnchorDamagePostProof.available()) return null;
        c.capturing = true;
        try {
            // Freeze eligibility before resource, shape or native settlement observers can run.
            Vec3 position = p.position(), look = p.getLookAngle(), facing = visualFacing(p);
            Pose pose = p.getPose(); AABB body = p.getBoundingBox();
            LivingEntity attacker = reviewedSource(p, source); if (attacker == null) return null;
            Vec3 origin = source.getSourcePosition();
            if (!finite(origin) || origin.distanceToSqr(position) > 16
                    || !CirculationReliefRules.side(point(position), point(look), point(origin))) return null;
            Ground ground = ground(p, position); if (ground == null) return null;
            if (!valid(c, false) || !position.equals(p.position()) || pose != p.getPose() || !body.equals(p.getBoundingBox())) return null;
            return new Capture(c, source, attacker, origin, position, facing, pose, body, ground);
        } finally { c.capturing = false; }
    }
    static boolean matches(ServerPlayer p, Capture capture) {
        return capture != null && !capture.committed && !capture.context.planning && currentCapture(p, capture) && remainingHits(p) > 0;
    }
    private static boolean currentCapture(ServerPlayer p, Capture capture) {
        return capture != null && owned(p) == capture.context && HighPowerCombat.circulationReliefNativeFrame(p, capture)
                && valid(capture.context, true) && !conflicts(p) && now(p) == capture.tick
                && capture.ownerPosition.equals(p.position()) && capture.pose == p.getPose() && capture.body.equals(p.getBoundingBox())
                && identity(capture, p.serverLevel()) && reviewedSource(p, capture.source) == capture.attacker
                && capture.ground.equals(ground(p, capture.ownerPosition));
    }
    static VectorParryRules.Result plan(ServerPlayer p, Capture capture, ExactCombatState before, BigDecimal mitigated) {
        if (before == null || before.isDead() || mitigated == null || mitigated.signum() <= 0 || before.shield().signum() <= 0
                || !matches(p, capture) || !HighPowerCombat.exactStateSnapshot(p).filter(before::equals).isPresent()) return null;
        var quota = readQuota(p); if (quota == null || !quota.available()) return null;
        Context c = capture.context; c.planning = true;
        try {
            capture.quota = quota; capture.planned = CirculationReliefRules.absorb(before, mitigated, EsperShields.eligible(p)); return capture.planned;
        } finally { c.planning = false; }
    }
    /** Callback-free quota admission after exact publication; the returned cue runs after HP settlement. */
    static Runnable committed(ServerPlayer p, Capture capture, VectorParryRules.Result result) {
        if (capture == null || capture.committed || result == null || result != capture.planned || result.absorbed().signum() <= 0
                || owned(p) != capture.context || !HighPowerCombat.circulationReliefNativeFrame(p, capture) || now(p) != capture.tick
                || !Objects.equals(readQuota(p), capture.quota)
                || !HighPowerCombat.exactStateSnapshot(p).filter(result.state()::equals).isPresent()) return () -> {};
        stampQuota(p, capture.quota.afterAbsorption(result.absorbed())); capture.committed = true;
        PhysicalTick physical = physicalTick(p);
        if (!physical.players.contains(p.getUUID())) physical.admissions.merge(p.getUUID(), 1, Integer::sum);
        return new Runnable() {
            private boolean attempted;
            @Override public void run() {
                if (attempted) return;
                attempted = true;
                try {
                    Context c = capture.context;
                    if (owned(p) == c && valid(c, true) && capture.ownerPosition.equals(p.position()))
                        send(c, ABSORBED, capture.ownerPosition, capture.facing, capture.ground.point, capture.contact, true);
                } catch (RuntimeException | LinkageError failure) {
                    LOG.warn("NEW432 cue unavailable after committed exact settlement", failure);
                }
            }
        };
    }

    /** Controller maintenance cannot age the rolling quota. Only the physical Post hook below may do so. */
    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null) return;
        if (!AnchorDamagePostProof.available() || !structurallyValid(c, false) || !canAct(p) || conflicts(p) || available(p).signum() <= 0 || readQuota(p) == null) { finish(c); return; }
        if (busy(c)) return;
        long tick = now(p); if (tick <= c.lastTick) return; c.lastTick = tick;
        Payment payment = readPayment(p);
        if (payment == null || payment.receipt != c.paymentReceipt || payment.remaining == 0 && !pay(c, false)) { finish(c); return; }
        if (!valid(c, false)) { finish(c); return; }
        Ground ground = ground(p, p.position()); int phase = ground == null || remainingHits(p) == 0 ? PAUSED : ACTIVE;
        if (phase != c.lastPhase || (tick - c.started) % 4 == 0)
            send(c, phase, p.position(), visualFacing(p), ground == null ? p.position() : ground.point, center(p), ground != null);
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
            if (quota != null && !quota.equals(CirculationReliefRules.Quota.EMPTY)) {
                int[] aged = quota.nativeTick().remainingTicks();
                // An admission during this player tick owns the NEXT twenty Post ticks. Existing
                // slots still age normally; frequent staggered hits cannot extend an older slot.
                int added = Math.min(CirculationReliefRules.MAX_ABSORPTIONS, clock.admissions.getOrDefault(p.getUUID(), 0));
                for (int i = 0; i < added; i++) aged[i] = CirculationReliefRules.WINDOW_TICKS;
                stampQuota(p, CirculationReliefRules.Quota.restore(aged));
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
        if (tag.getInt("version") == 1) return migratePayment(p, tag);
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
    /** Preserve the legacy saved debt; current/offline/world time can never erase it. */
    private static Payment migratePayment(ServerPlayer p, CompoundTag tag) {
        if (!tag.getAllKeys().equals(Set.of("version", "observed", "until", "stopUntil", "dimension"))
                || !tag.contains("observed", Tag.TAG_LONG) || !tag.contains("until", Tag.TAG_LONG)
                || !tag.contains("stopUntil", Tag.TAG_LONG) || !tag.contains("dimension", Tag.TAG_STRING)
                || !VectorParryRules.clock(tag.getLong("observed")) || tag.getLong("until") < 0
                || tag.getLong("until") > tag.getLong("observed") + VectorParryRules.PAYMENT_PERIOD
                || tag.getLong("stopUntil") < 0 || tag.getLong("stopUntil") > tag.getLong("observed") + VectorParryRules.PAYMENT_PERIOD
                || tag.getString("dimension").length() > 256) return null;
        Long resource = resourceClock(p);
        // A previously paid v1 receipt must have its known exact own CP writer field.
        // Its world-tick writer cannot be ahead of the saved physical observation + 1
        // in the reviewed clock mapping. Do not bless a corrupt far-future CP fence.
        if (resource == null || resource < 0 || resource > tag.getLong("observed") + 1) return null;
        Payment migrated = new Payment((int)Math.max(0, tag.getLong("until") - tag.getLong("observed")),
                (int)Math.max(0, tag.getLong("stopUntil") - tag.getLong("observed")), 0, tag.getString("dimension"), resource);
        writePayment(p, migrated); return migrated;
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
    private static CirculationReliefRules.Quota readQuota(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(QUOTA_KEY)) return CirculationReliefRules.Quota.EMPTY;
        if (!root.contains(QUOTA_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag t = root.getCompound(QUOTA_KEY);
        if (!t.getAllKeys().equals(Set.of("version", "remaining")) || !t.contains("version", Tag.TAG_INT)
                || t.getInt("version") != 1 || !t.contains("remaining", Tag.TAG_INT_ARRAY)) return null;
        try { return CirculationReliefRules.Quota.restore(t.getIntArray("remaining")); }
        catch (IllegalArgumentException malformed) { return null; }
    }
    private static void stampQuota(ServerPlayer p, CirculationReliefRules.Quota quota) {
        CompoundTag t = new CompoundTag(); t.putInt("version", 1); t.putIntArray("remaining", quota.remainingTicks());
        p.getPersistentData().put(QUOTA_KEY, t);
    }

    /** The first vertical native hit is final; rejected fluid/decorative surfaces never search through to a floor. */
    private static Ground ground(ServerPlayer p, Vec3 position) {
        if (!finite(position)) return null;
        try {
            GroundView view = new GroundView(p, position);
            // The pinned VoxelShape.clip probes start + delta * .001 for an inside hit.
            // Start above that ~.004m probe so a real floor touching the feet is not
            // misclassified as an embedded origin. The accepted sink stays at/below
            // the actual feet and within four meters, irrespective of this ray origin.
            // Native AABB clipping excludes its endpoint. Extend only the query by the
            // established surface tolerance; the accepted sink-depth check below stays exact.
            Vec3 start = position.add(0, .01, 0), end = position.subtract(0, CirculationReliefRules.GROUND_SINK_RANGE + 1e-5, 0);
            ClipContext context = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p) {
                @Override public VoxelShape getBlockShape(BlockState state, BlockGetter blocks, BlockPos pos) {
                    VoxelShape shape = super.getBlockShape(state, blocks, pos); view.shape(shape); return shape;
                }
                @Override public VoxelShape getFluidShape(FluidState state, BlockGetter blocks, BlockPos pos) {
                    VoxelShape shape = super.getFluidShape(state, blocks, pos); view.shape(shape); return shape;
                }
            };
            BlockHitResult hit = view.clip(context);
            if (hit.getType() != HitResult.Type.BLOCK || hit.isInside() || hit.getDirection() != Direction.UP) return null;
            BlockPos block = hit.getBlockPos(); BlockState state = view.getBlockState(block); Vec3 sink = hit.getLocation();
            if (!finite(sink) || Math.abs(sink.x - position.x) > 1e-7 || Math.abs(sink.z - position.z) > 1e-7
                    || sink.y > position.y + 1e-7 || position.y - sink.y > CirculationReliefRules.GROUND_SINK_RANGE
                    || !view.getFluidState(block).isEmpty() || !state.isSolid() || state.isAir()) return null;
            // The same .2m footprint can straddle a real floor seam. Partition it at
            // integer X/Z boundaries so every touched cell must supply its own portion;
            // a missing, liquid, unloaded or lower neighboring cell never gets skipped.
            AABB foot = new AABB(sink.x - .1, sink.y - .025, sink.z - .1,
                    sink.x + .1, sink.y - 1e-7, sink.z + .1);
            int minX = (int)Math.floor(foot.minX), maxX = (int)Math.floor(Math.nextDown(foot.maxX));
            int minZ = (int)Math.floor(foot.minZ), maxZ = (int)Math.floor(Math.nextDown(foot.maxZ));
            if (maxX - minX > 1 || maxZ - minZ > 1) return null;
            CollisionContext collisionContext = CollisionContext.of(p);
            for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                BlockPos cell = new BlockPos(x, block.getY(), z);
                BlockState cellState = view.getBlockState(cell);
                if (!view.getFluidState(cell).isEmpty() || !cellState.isSolid() || cellState.isAir()) return null;
                VoxelShape collision = cellState.getCollisionShape(view, cell, collisionContext);
                VoxelShape support = cellState.getBlockSupportShape(view, cell); view.shape(collision); view.shape(support);
                VoxelShape portion = Shapes.create(new AABB(Math.max(foot.minX, x) - x, foot.minY - cell.getY(),
                        Math.max(foot.minZ, z) - z, Math.min(foot.maxX, x + 1D) - x,
                        foot.maxY - cell.getY(), Math.min(foot.maxZ, z + 1D) - z));
                if (collision.isEmpty() || support.isEmpty() || Shapes.joinIsNotEmpty(portion, collision, BooleanOp.ONLY_FIRST)
                        || Shapes.joinIsNotEmpty(portion, support, BooleanOp.ONLY_FIRST)) return null;
            }
            view.sameWorld(); return new Ground(block.immutable(), state, sink);
        } catch (GroundRefusal | IllegalArgumentException refused) { return null; }
    }
    private static final class GroundRefusal extends RuntimeException {
        private GroundRefusal() { super(null, null, false, false); }
    }
    /** Shape neighbor lookups cannot load chunks, leave the bounded column, or query unbounded geometry. */
    private static final class GroundView implements BlockGetter {
        final ServerPlayer player;
        final ServerLevel world;
        final AABB scope;
        int reads, boxes;
        GroundView(ServerPlayer p, Vec3 position) {
            player = p; world = p.serverLevel();
            scope = new AABB(position.x - 1.01, position.y - CirculationReliefRules.GROUND_SINK_RANGE - 1.01,
                    position.z - 1.01, position.x + 1.01, position.y + 1.01, position.z + 1.01);
        }
        void sameWorld() { if (player.serverLevel() != world || player.level() != world || player.isRemoved()) throw new GroundRefusal(); }
        LevelChunk chunk(BlockPos pos) {
            sameWorld();
            if (++reads > 192 || !scope.intersects(new AABB(pos)) || world.isOutsideBuildHeight(pos)
                    || !world.getWorldBorder().isWithinBounds(pos)) throw new GroundRefusal();
            LevelChunk chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) throw new GroundRefusal();
            return chunk;
        }
        @Override public BlockState getBlockState(BlockPos pos) { return chunk(pos).getBlockState(pos); }
        @Override public FluidState getFluidState(BlockPos pos) { return chunk(pos).getFluidState(pos); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return chunk(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK); }
        @Override public int getHeight() { return world.getHeight(); }
        @Override public int getMinBuildHeight() { return world.getMinBuildHeight(); }
        @Override public BlockHitResult clipWithInteractionOverride(Vec3 from, Vec3 to, BlockPos pos, VoxelShape shape, BlockState state) {
            // Only the actual collision shape, never a larger decorative interaction outline, certifies support.
            return shape.clip(from, to, pos);
        }
        void shape(VoxelShape shape) {
            long cells = 1;
            for (Direction.Axis axis : Direction.Axis.values()) {
                var coords = shape.getCoords(axis);
                if (coords.size() > 34) throw new GroundRefusal();
                for (double n : coords) if (!Double.isFinite(n)) throw new GroundRefusal();
                cells *= Math.max(1, coords.size() - 1);
            }
            if (cells > 4096) throw new GroundRefusal();
            shape.forAllBoxes((a, b, c, d, e, f) -> {
                if (++boxes > 192 || !Double.isFinite(a) || !Double.isFinite(b) || !Double.isFinite(c)
                        || !Double.isFinite(d) || !Double.isFinite(e) || !Double.isFinite(f)) throw new GroundRefusal();
            });
        }
    }

    /** NEW412's closed vanilla provenance, plus explicit friendly and unknown-component refusal. */
    private static LivingEntity reviewedSource(ServerPlayer p, DamageSource source) {
        if (source == null || source.getClass() != DamageSource.class || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(DamageTypeTags.BYPASSES_RESISTANCE) || source.is(DamageTypeTags.BYPASSES_EFFECTS)
                || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.BYPASSES_COOLDOWN)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypeTags.DAMAGES_HELMET)) return null;
        String type;
        if (source.is(DamageTypes.PLAYER_ATTACK)) type = "player_attack";
        else if (source.is(DamageTypes.MOB_ATTACK)) type = "mob_attack";
        else if (source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) type = "mob_attack_no_aggro";
        else if (source.is(DamageTypes.ARROW)) type = "arrow";
        else return null;
        var resource = p.server.getResourceManager().getResource(ResourceLocation.withDefaultNamespace("damage_type/" + type + ".json"));
        if (resource.isEmpty() || !resource.get().sourcePackId().equals("vanilla")
                || !resource.get().knownPackInfo().equals(Optional.of(KnownPack.vanilla("core")))) return null;
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == p || !HighPowerCombat.supported(attacker)
                || attacker.level() != p.level() || !attacker.isAlive() || attacker.isRemoved() || attacker.isSpectator()
                || p.isAlliedTo(attacker) || attacker.isAlliedTo(p)
                || p.serverLevel().getEntity(attacker.getId()) != attacker || p.serverLevel().getEntity(attacker.getUUID()) != attacker) return null;
        Vec3 origin = source.getSourcePosition(); if (!finite(origin)) return null;
        if (type.equals("arrow")) return source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() instanceof Arrow arrow
                && plainArrow(p, arrow, attacker) && origin.equals(arrow.position()) ? attacker : null;
        if (source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() != attacker || !origin.equals(attacker.position())
                || type.equals("player_attack") && attacker.getClass() != ServerPlayer.class) return null;
        for (ItemStack stack : attacker.getAllSlots()) if (!plainEquipment(stack)) return null;
        return attacker;
    }
    private static boolean plainArrow(ServerPlayer p, Arrow arrow, LivingEntity attacker) {
        if (arrow.getClass() != Arrow.class || arrow.getType() != EntityType.ARROW || arrow.level() != p.level()
                || arrow.isRemoved() || !arrow.isAlive() || p.serverLevel().getEntity(arrow.getId()) != arrow || p.serverLevel().getEntity(arrow.getUUID()) != arrow
                || !(arrow instanceof NativeScatterArrowOwnerAccess access) || access.academy$scatterCachedOwner() != attacker
                || !attacker.getUUID().equals(access.academy$scatterOwnerUuid()) || arrow.inGround || arrow.isNoPhysics() || arrow.noPhysics
                || arrow.isNoGravity() || arrow.isPassenger() || arrow.isVehicle() || arrow.isCritArrow() || arrow.isOnFire() || arrow.getPierceLevel() != 0
                || !finite(arrow.position()) || !finite(arrow.getDeltaMovement()) || arrow.getDeltaMovement().lengthSqr() <= 0 || arrow.getDeltaMovement().lengthSqr() > 64 * 64
                || !Double.isFinite(arrow.getBaseDamage()) || arrow.getBaseDamage() <= 0 || arrow.getBaseDamage() > 64
                || TrajectoryReturnArrowBridge.leased(arrow) || ShortInterceptionArrowBridge.leased(arrow) || MagneticDomainArrows.leased(arrow) || MagneticArrowSeparation.leased(arrow)
                || IronSandLance.leased(arrow) || ElectromasterPrototype.leased(arrow) || ElectromasterChargeDecoy.leased(arrow) || MagneticGroundStep.leased(arrow)) return false;
        ItemStack stack = arrow.getPickupItemStackOrigin(), weapon = arrow.getWeaponItem();
        return stack.getCount() == 1 && ItemStack.isSameItemSameComponents(stack, new ItemStack(Items.ARROW))
                && (weapon == null || !weapon.isEmpty() && weapon.getCount() == 1 && (weapon.is(Items.BOW) || weapon.is(Items.CROSSBOW))
                    && plainEquipment(weapon));
    }
    private static boolean plainEquipment(ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (stack.getCount() != 1 || !BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft")
                || !stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()) return false;
        ItemStack canonical = new ItemStack(stack.getItem()); int damage = stack.getDamageValue();
        if (damage < 0 || damage > canonical.getMaxDamage()) return false;
        if (canonical.isDamageableItem()) canonical.setDamageValue(damage);
        return ItemStack.isSameItemSameComponents(stack, canonical);
    }
    private static boolean identity(Capture capture, ServerLevel world) {
        return capture.source.getEntity() == capture.attacker && capture.source.getDirectEntity() == capture.direct
                && capture.attacker.level() == world && capture.direct.level() == world && !capture.attacker.isRemoved() && !capture.direct.isRemoved()
                && capture.attackerId == capture.attacker.getId() && capture.directId == capture.direct.getId()
                && capture.attackerUuid.equals(capture.attacker.getUUID()) && capture.directUuid.equals(capture.direct.getUUID())
                && world.getEntity(capture.attackerId) == capture.attacker && world.getEntity(capture.attackerUuid) == capture.attacker
                && world.getEntity(capture.directId) == capture.direct && world.getEntity(capture.directUuid) == capture.direct;
    }
    private static boolean canAct(ServerPlayer p) { return basic(p, false) && HighPowerResources.ready(p) && HighPowerCombat.unsupportedReason(p).isEmpty(); }
    private static boolean basic(ServerPlayer p, boolean nativeFrame) {
        if (!thread(p) || p.connection == null || p.hasDisconnected() || p.isRemoved() || !nativeFrame && !p.isAlive()
                || p.isSpectator() || p.getAbilities().instabuild || p.getAbilities().invulnerable || p.noPhysics || p.isPassenger() || p.isVehicle()
                || p.isSleeping() || p.isFallFlying() || p.getAbilities().flying || p.containerMenu != p.inventoryMenu || AbilityConsumption.busy(p)) return false;
        var state = AbilityStorage.peek(p);
        return state != null && HighPowerCombat.enabled(p) && state.category.equals("vecmanip") && state.activated && state.overloadFine && !state.interfering
                && state.learned(ID) && state.learned(InertialAnchor.ID) && state.learned(VectorParry.ID) && state.level >= 3 && state.level <= 5 && SkillCatalog.enabled("vecmanip", ID)
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
    private static BigDecimal available(ServerPlayer p) { return HighPowerCombat.exactStateSnapshot(p).map(ExactCombatState::shield).orElse(BigDecimal.ZERO); }
    private static Context owned(ServerPlayer p) { if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null; }
    private static boolean thread(ServerPlayer p) { return p != null && p.server != null && p.server.isSameThread(); }
    private static long now(ServerPlayer p) { return MagneticDomain.serverTime(p.server); }
    private static Vec3 center(ServerPlayer p) { return p.position().add(0, 1, 0); }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z) && Math.abs(v.x) <= 30_000_001 && Math.abs(v.y) <= 30_000_001 && Math.abs(v.z) <= 30_000_001; }
    private static VectorParryRules.Point point(Vec3 v) { return v == null ? null : new VectorParryRules.Point(v.x, v.y, v.z); }
    private static Vec3 visualFacing(ServerPlayer p) {
        double yaw = Math.toRadians(p.getYRot()); return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }
    private static void finish(Context c) {
        ServerPlayer p = c.identity.owner(); if (!CONTEXTS.remove(p.getUUID(), c)) return;
        if (c.paymentReceipt > 0) {
            Payment payment = readPayment(p);
            if (payment != null && payment.receipt == c.paymentReceipt) {
                int restart = Math.max(payment.restart, c.cooldown);
                writePayment(p, new Payment(payment.remaining, restart, payment.receipt, payment.dimension, payment.resourceUntil));
                if (restart > payment.restart) holdPaymentTick(p, 2);
            }
            c.identity.state().setCooldown(ID, c.cooldown);
        }
        send(c, ENDED, p.position(), visualFacing(p), p.position(), center(p), false); c.audience.clear();
    }
    private static void sendState(Context c) {
        ServerPlayer p = c.identity.owner(); Ground ground = ground(p, p.position());
        int phase = ground == null || remainingHits(p) == 0 ? PAUSED : ACTIVE;
        send(c, phase, p.position(), visualFacing(p), ground == null ? p.position() : ground.point, center(p), ground != null);
    }
    private static void send(Context c, int phase, Vec3 position, Vec3 facing, Vec3 groundSink, Vec3 contact, boolean grounded) {
        ServerPlayer p = c.identity.owner();
        if (c.sequence == Long.MAX_VALUE || !finite(position) || !unit(facing) || !finite(groundSink) || !finite(contact)) return;
        c.audience.removeIf(viewer -> viewer.connection == null || viewer.hasDisconnected() || viewer.isRemoved()
                || viewer.serverLevel() != c.identity.level() || viewer.position().distanceToSqr(p.position()) > 48 * 48);
        for (ServerPlayer viewer : c.identity.level().players()) if (viewer.position().distanceToSqr(p.position()) <= 32 * 32) c.audience.add(viewer);
        c.audience.add(p);
        Visual packet = new Visual(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.identity.epoch(), ++c.sequence,
                phase, position, facing, groundSink, contact, phase == ENDED ? 0 : remainingHits(p), grounded);
        c.lastPhase = phase;
        for (ServerPlayer viewer : c.audience) if (viewer.connection != null && !viewer.hasDisconnected() && viewer.serverLevel() == c.identity.level())
            PacketDistributor.sendToPlayer(viewer, packet);
    }
    private static boolean unit(Vec3 v) { return finite(v) && v.y == 0 && Math.abs(v.lengthSqr() - 1) <= 1e-9; }
    public record Visual(int entity, UUID owner, long input, long token, long epoch, long sequence, int phase,
                         Vec3 point, Vec3 facing, Vec3 groundSink, Vec3 contact, int remainingHits, boolean grounded) implements CustomPacketPayload {
        public Visual {
            if (entity < 0 || owner == null || input <= 0 || token <= 0 || epoch <= 0 || sequence <= 0 || phase < ACTIVE || phase > ENDED
                    || !finite(point) || !unit(facing) || !finite(groundSink) || !finite(contact) || point.add(0, 1, 0).distanceToSqr(contact) > 1.5 * 1.5
                    || remainingHits < 0 || remainingHits > CirculationReliefRules.MAX_ABSORPTIONS
                    || grounded && (Math.abs(groundSink.x - point.x) > 1e-7 || Math.abs(groundSink.z - point.z) > 1e-7
                        || groundSink.y > point.y + 1e-7 || point.y - groundSink.y > CirculationReliefRules.GROUND_SINK_RANGE)
                    || !grounded && !groundSink.equals(point) || (phase == ACTIVE || phase == ABSORBED) && !grounded
                    || phase == ACTIVE && remainingHits == 0 || phase == ENDED && (remainingHits != 0 || grounded))
                throw new IllegalArgumentException("Invalid bounded NEW432 visual");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_circulation_relief"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((buffer, value) -> {
            buffer.writeInt(value.entity); buffer.writeUUID(value.owner); buffer.writeLong(value.input); buffer.writeLong(value.token);
            buffer.writeLong(value.epoch); buffer.writeLong(value.sequence); buffer.writeByte(value.phase);
            writeVec(buffer, value.point); writeVec(buffer, value.facing); writeVec(buffer, value.groundSink); writeVec(buffer, value.contact);
            buffer.writeByte(value.remainingHits); buffer.writeBoolean(value.grounded);
        }, buffer -> new Visual(buffer.readInt(), buffer.readUUID(), buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readLong(),
                buffer.readUnsignedByte(), readVec(buffer), readVec(buffer), readVec(buffer), readVec(buffer), buffer.readUnsignedByte(), buffer.readBoolean()));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static void feedback(ServerPlayer p, String key) {
        Object[] args = "ground_required".equals(key)
                ? new Object[]{SkillTuning.format(ID, "{{reach}}")} : new Object[0];
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.academy.highpower.circulation_relief." + key, args), true);
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
            if (entity < 0 || owner == null || input <= 0 || epoch <= 0) throw new IllegalArgumentException("Invalid NEW432 rejection");
        }
        public static final Type<Rejection> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_circulation_relief_rejected"));
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
        for (Context context : new ArrayList<>(CONTEXTS.values())) if (context.identity.owner().server == event.getServer()) tick(context.identity.owner());
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void death(LivingDeathEvent event) { if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void leave(EntityLeaveLevelEvent event) { if (event.getEntity() instanceof ServerPlayer p) remove(p); }
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
        CONTEXTS.clear(); INPUTS.clear();
        // Runtime cleanup never clears persisted admission or grants an extra physical tick.
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == event.getServer()) finish(c);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { clear(); PHYSICAL.remove(event.getServer()); }
}
