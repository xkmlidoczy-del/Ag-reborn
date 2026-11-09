package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.AutonomousVectorCircleRules;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.ExactCombatState;
import cn.academy.port.highpower.core.SharedShieldReserveShares;
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
import net.minecraft.world.level.entity.EntityTypeTest;
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

/** NEW452: a paid three-dimensional forward circle using only the existing base and reserve shares. */
@EventBusSubscriber(modid = "academy")
public final class AutonomousVectorCircle {
    public static final String ID = "autonomous_vector_circle", PAYMENT_KEY = "academy:autonomous_vector_circle_payment",
            QUOTA_KEY = "academy:autonomous_vector_circle_quota";
    public static final int ACTIVE = 0, ABSORBED = 1, PAUSED = 2, ENDED = 3;
    private static final String CLOCK = "next_" + ID;
    private static final int MAX_INPUTS = 4096;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.autonomous_vector_circle");
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<MinecraftServer, PhysicalTick> PHYSICAL = new WeakHashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    public static Consumer<Rejection> clientRejectionReceiver = ignored -> {};
    private record Watermark(ServerPlayer owner, long epoch, long input) {}
    /** Native player-tick debt, never a deadline in a world or process clock. */
    private record Payment(int remaining, int restart, long receipt, String dimension, long resourceUntil) {}
    private record Balance(String available, String capacity) {
        private static final Balance UNKNOWN = new Balance("", "");
    }
    private static final class PhysicalTick {
        final int tick;
        final Set<UUID> players = new HashSet<>();
        final Map<UUID, Integer> admissions = new HashMap<>(), beforePostAdmissions = new HashMap<>();
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
    /** Source and pose identity belonging to precisely one current native hurt frame. */
    public static final class Capture {
        private final Context context;
        private final DamageSource source;
        private final Entity direct;
        private final LivingEntity attacker;
        private final int directId, attackerId, physicalTick;
        private final UUID directUuid, attackerUuid;
        private final Vec3 sourcePosition, sourceRaw, ownerPosition, eye, look, facing, contact;
        private final Vec3 directPosition, attackerPosition;
        private final AABB body, attackerBody;
        private final Pose pose, attackerPose;
        private final float yaw, pitch;
        private final List<ItemStack> equipment;
        private final AutonomousVectorArrowReceipts.Capture arrow;
        private final Payment payment;
        private final long tick;
        private boolean committed;
        private AutonomousVectorCircleRules.Result planned;
        private AutonomousVectorCircleRules.Quota quota, nextQuota;
        private PhysicalTick physical;
        private int admissions;
        private Capture(Context c, DamageSource source, LivingEntity attacker, Vec3 origin,
                        Vec3 position, Vec3 eye, Vec3 look, Vec3 facing, Pose pose, AABB body,
                        Vec3 contact, AutonomousVectorArrowReceipts.Capture arrow) {
            ServerPlayer p = c.identity.owner();
            context = c; this.source = source; this.attacker = attacker; direct = source.getDirectEntity();
            directId = direct.getId(); directUuid = direct.getUUID(); attackerId = attacker.getId(); attackerUuid = attacker.getUUID();
            sourcePosition = origin; sourceRaw = source.sourcePositionRaw(); ownerPosition = position; this.eye = eye;
            this.look = look; this.facing = facing; this.pose = pose; this.body = body; this.contact = contact; this.arrow = arrow;
            payment = readPayment(p);
            yaw = p.getYRot(); pitch = p.getXRot(); tick = now(p); physicalTick = p.server.getTickCount();
            directPosition = direct.position(); attackerPosition = attacker.position(); attackerBody = attacker.getBoundingBox(); attackerPose = attacker.getPose();
            equipment = new ArrayList<>(); for (ItemStack stack : attacker.getAllSlots()) equipment.add(stack.copy());
        }
    }
    private AutonomousVectorCircle() {}

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
        if (!AnchorDamagePostProof.available() || !AutonomousVectorArrowReceipts.available() || HighPowerCombat.unsupportedReason(p).isPresent()) return reject(p, input, "unsupported");
        if (!canAct(p)) return reject(p, input, "not_ready");
        if (conflicts(p)) return reject(p, input, "conflict");
        if (nextToken == Long.MAX_VALUE || fence == null || fence.restart > 0
                || !AbilityStorage.get(p).canUse(ID) || available(p).signum() <= 0) return reject(p, input, "not_ready");
        AutonomousVectorArrowReceipts.invalidate(p);
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
        return c != null && AnchorDamagePostProof.available() && valid(c, false) && !conflicts(p) && canAdmit(p)
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
    private static boolean canAdmit(ServerPlayer p) {
        var quota = thread(p) ? readQuota(p) : null;
        return quota != null && quota.available(physicalTick(p).admissions.getOrDefault(p.getUUID(), 0));
    }
    private static boolean busy(Context c) { return c.paying || c.planning || c.capturing; }
    private static boolean conflicts(ServerPlayer p) { return MultiAxisReflection.active(p) || CirculationRelief.active(p) || WindPressureLift.active(p)
            || VectorParry.active(p) || InertialAnchor.active(p) || BrakingTouch.active(p); }

    /** Identity only: remaining admission may change during this same real hurt. */
    static Object arrowGeneration(ServerPlayer p) { return owned(p); }
    static boolean pendingArrowAllowed(ServerPlayer p, Arrow arrow, LivingEntity shooter, Vec3 from, Vec3 contact) {
        Context c = owned(p);
        if (c == null || busy(c) || !protecting(p) || !AutonomousVectorArrowReceipts.ordinaryArrowType(p) || !finite(from) || !finite(contact)
                || !from.equals(arrow.position()) || !plainArrow(p, arrow, shooter)
                || !AutonomousVectorCircleRules.inRange(point(p.position()), point(from))
                || !AutonomousVectorCircleRules.frontDirection(point(p.getLookAngle()), point(from.subtract(contact)))
                || p.position().add(0, 1, 0).distanceToSqr(contact) > 4) return false;
        var resource = p.server.getResourceManager().getResource(ResourceLocation.withDefaultNamespace("damage_type/arrow.json"));
        if (resource.isEmpty() || !resource.get().sourcePackId().equals("vanilla")
                || !resource.get().knownPackInfo().equals(Optional.of(KnownPack.vanilla("core")))
                || !visibleSource(p, p.getEyePosition(), from, arrow)) return false;
        return owned(p) == c && !busy(c) && protecting(p);
    }
    static Capture capture(ServerPlayer p, DamageSource source) {
        Context c = owned(p);
        if (c == null || busy(c) || !valid(c, false) || conflicts(p) || !canAdmit(p) || !AnchorDamagePostProof.available()) return null;
        c.capturing = true;
        try {
            Vec3 position = p.position(), eye = p.getEyePosition(), look = p.getLookAngle(), facing = visualFacing(p);
            Pose pose = p.getPose(); AABB body = p.getBoundingBox();
            LivingEntity attacker = reviewedSource(p, source); if (attacker == null) return null;
            AutonomousVectorArrowReceipts.Capture arrow = source.getDirectEntity() instanceof Arrow
                    ? AutonomousVectorArrowReceipts.capture(p, source) : null;
            if (source.getDirectEntity() instanceof Arrow && arrow == null) return null;
            Vec3 origin = arrow == null ? source.getSourcePosition() : arrow.from();
            if (!AutonomousVectorCircleRules.inRange(point(position), point(origin))) return null;
            if (arrow == null ? !AutonomousVectorCircleRules.front(point(position), point(look), point(origin))
                    : !AutonomousVectorCircleRules.frontDirection(point(look), point(origin.subtract(arrow.contact())))) return null;
            Vec3 delta = origin.subtract(position.add(0, 1, 0));
            Vec3 contact = arrow == null ? position.add(0, 1, 0).add(delta.lengthSqr() > 1e-12 ? delta.normalize() : Vec3.ZERO) : arrow.contact();
            if (!finite(contact) || position.add(0, 1, 0).distanceToSqr(contact) > 4) return null;
            Capture capture = new Capture(c, source, attacker, origin, position, eye, look, facing, pose, body, contact, arrow);
            return valid(c, false) && !conflicts(p) && frozen(capture, p) && visibleSource(p, eye, origin, capture.direct)
                    && (arrow == null || arrow.valid(p, source)) && reviewedSource(p, source) == attacker
                    && capturedContext(p, capture, false) ? capture : null;
        } catch (RuntimeException | LinkageError refused) { return null; }
        finally { c.capturing = false; }
    }
    static boolean matches(ServerPlayer p, Capture capture) {
        if (capture == null || capture.committed || capture.planned != null || busy(capture.context)) return false;
        capture.context.capturing = true;
        try { return currentCapture(p, capture) && canAdmit(p); }
        catch (RuntimeException | LinkageError refused) { return false; }
        finally { capture.context.capturing = false; }
    }
    private static boolean currentCapture(ServerPlayer p, Capture capture) {
        return capture != null && owned(p) == capture.context && HighPowerCombat.autonomousVectorCircleNativeFrame(p, capture)
                && valid(capture.context, true) && !conflicts(p) && frozen(capture, p)
                && reviewedSource(p, capture.source) == capture.attacker
                && visibleSource(p, capture.eye, capture.sourcePosition, capture.direct)
                && (capture.arrow == null || capture.arrow.valid(p, capture.source))
                && reviewedSource(p, capture.source) == capture.attacker
                && capturedContext(p, capture, true) && HighPowerCombat.autonomousVectorCircleNativeFrame(p, capture);
    }
    /** Run after every shape/source callback; do not perform another world query after this gate. */
    private static boolean capturedContext(ServerPlayer p, Capture capture, boolean nativeFrame) {
        return owned(p) == capture.context && frozen(capture, p) && valid(capture.context, nativeFrame) && !conflicts(p)
                && Objects.equals(capture.payment, readPayment(p));
    }
    private static boolean frozen(Capture capture, ServerPlayer p) {
        if (now(p) != capture.tick || p.server.getTickCount() != capture.physicalTick
                || !capture.ownerPosition.equals(p.position()) || !capture.eye.equals(p.getEyePosition()) || !capture.look.equals(p.getLookAngle())
                || capture.yaw != p.getYRot() || capture.pitch != p.getXRot() || capture.pose != p.getPose() || !capture.body.equals(p.getBoundingBox())
                || !identity(capture, p.serverLevel()) || !Objects.equals(capture.sourceRaw, capture.source.sourcePositionRaw())
                || !capture.sourcePosition.equals(capture.source.getSourcePosition()) || !capture.directPosition.equals(capture.direct.position())
                || !capture.attackerPosition.equals(capture.attacker.position()) || !capture.attackerBody.equals(capture.attacker.getBoundingBox())
                || capture.attackerPose != capture.attacker.getPose()) return false;
        int i = 0;
        for (ItemStack stack : capture.attacker.getAllSlots()) {
            if (i == capture.equipment.size() || !ItemStack.matches(capture.equipment.get(i++), stack)) return false;
        }
        return i == capture.equipment.size();
    }
    static AutonomousVectorCircleRules.Result plan(ServerPlayer p, Capture capture, ExactCombatState before, BigDecimal mitigated) {
        if (before == null || before.isDead() || mitigated == null || mitigated.signum() <= 0
                || capture == null || owned(p) != capture.context || capture.committed || capture.planned != null || busy(capture.context)
                || !HighPowerCombat.autonomousVectorCircleNativeFrame(p, capture)
                || !HighPowerCombat.exactStateSnapshot(p).filter(before::equals).isPresent()) return null;
        // Capture resource/admission authority BEFORE visibility can call arbitrary block shapes.
        var quota = readQuota(p); PhysicalTick physical = physicalTick(p);
        int admissions = physical.admissions.getOrDefault(p.getUUID(), 0);
        var shares = MagneticDomainReserve.savedShares(p, before).orElse(null);
        if (quota == null || !quota.available(admissions) || shares == null || before.shield().add(shares.total()).signum() <= 0
                || !matches(p, capture)) return null;
        Context c = capture.context; c.planning = true;
        try {
            var result = AutonomousVectorCircleRules.absorb(before, shares, mitigated);
            if (result.absorbed().signum() <= 0) return null;
            var nextQuota = quota.afterAbsorption(result.absorbed(), admissions);
            // No world shape, source review, event, or notification may follow this final gate.
            if (!capturedContext(p, capture, true) || physicalTick(p) != physical
                    || physical.admissions.getOrDefault(p.getUUID(), 0) != admissions || !Objects.equals(readQuota(p), quota)
                    || !HighPowerCombat.exactStateSnapshot(p).filter(before::equals).isPresent()
                    || !MagneticDomainReserve.savedShares(p, before).filter(shares::equals).isPresent()
                    || !HighPowerCombat.autonomousVectorCircleNativeFrame(p, capture)) return null;
            capture.quota = quota; capture.nextQuota = nextQuota;
            capture.physical = physical; capture.admissions = admissions; capture.planned = result;
            return result;
        } finally { c.planning = false; }
    }
    /** Callback-free quota admission after exact base AND reserve publication; cue runs after HP settlement. */
    static Runnable committed(ServerPlayer p, Capture capture, AutonomousVectorCircleRules.Result result) {
        if (capture == null || capture.committed || result == null || result != capture.planned || result.absorbed().signum() <= 0
                || owned(p) != capture.context || !HighPowerCombat.autonomousVectorCircleNativeFrame(p, capture) || now(p) != capture.tick
                || p.server.getTickCount() != capture.physicalTick || physicalTick(p) != capture.physical
                || capture.physical.admissions.getOrDefault(p.getUUID(), 0) != capture.admissions
                || !Objects.equals(readQuota(p), capture.quota)
                || !HighPowerCombat.exactStateSnapshot(p).filter(result.state()::equals).isPresent()
                || !MagneticDomainReserve.savedShares(p, result.state()).filter(result.shares()::equals).isPresent()) return () -> {};
        capture.committed = true;
        stampQuota(p, capture.nextQuota);
        capture.physical.admissions.put(p.getUUID(), capture.admissions + 1);
        if (!capture.physical.players.contains(p.getUUID())) capture.physical.beforePostAdmissions.merge(p.getUUID(), 1, Integer::sum);
        return new Runnable() {
            private boolean attempted;
            @Override public void run() {
                if (attempted) return;
                attempted = true;
                try {
                    Context c = capture.context;
                    if (owned(p) == c && valid(c, true) && capture.ownerPosition.equals(p.position()))
                        send(c, ABSORBED, capture.ownerPosition, capture.facing, capture.sourcePosition, capture.contact,
                                canAdmit(p) && available(p).signum() > 0);
                } catch (RuntimeException | LinkageError failure) {
                    LOG.warn("NEW452 cue unavailable after committed exact settlement", failure);
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
        int phase = canAdmit(p) ? ACTIVE : PAUSED;
        if (phase != c.lastPhase || (tick - c.started) % 4 == 0)
            send(c, phase, p.position(), visualFacing(p), p.position(), center(p), phase == ACTIVE);
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
            if (quota != null && !quota.equals(AutonomousVectorCircleRules.Quota.EMPTY)) {
                int[] aged = quota.nativeTick().remainingTicks();
                // An admission during this player tick owns the NEXT twenty Post ticks. Existing
                // slots still age normally; frequent staggered hits cannot extend an older slot.
                int added = Math.min(AutonomousVectorCircleRules.MAX_ABSORPTIONS, clock.beforePostAdmissions.getOrDefault(p.getUUID(), 0));
                for (int i = 0; i < added; i++) aged[i] = AutonomousVectorCircleRules.WINDOW_TICKS;
                stampQuota(p, AutonomousVectorCircleRules.Quota.restore(aged));
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
    private static AutonomousVectorCircleRules.Quota readQuota(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(QUOTA_KEY)) return AutonomousVectorCircleRules.Quota.EMPTY;
        if (!root.contains(QUOTA_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag t = root.getCompound(QUOTA_KEY);
        if (!t.getAllKeys().equals(Set.of("version", "remaining")) || !t.contains("version", Tag.TAG_INT)
                || t.getInt("version") != 1 || !t.contains("remaining", Tag.TAG_INT_ARRAY)) return null;
        try { return AutonomousVectorCircleRules.Quota.restore(t.getIntArray("remaining")); }
        catch (IllegalArgumentException malformed) { return null; }
    }
    private static void stampQuota(ServerPlayer p, AutonomousVectorCircleRules.Quota quota) {
        CompoundTag t = new CompoundTag(); t.putInt("version", 1); t.putIntArray("remaining", quota.remainingTicks());
        p.getPersistentData().put(QUOTA_KEY, t);
    }

    /** Loaded-only native collision and entity-body visibility of the actual source point. */
    private static boolean visibleSource(ServerPlayer p, Vec3 eye, Vec3 source, Entity endpoint) {
        if (!finite(eye) || !finite(source) || eye.distanceToSqr(source) > 12 * 12) return false;
        try {
            SightView view = new SightView(p, eye, source);
            view.loaded(new AABB(eye, source).inflate(.001));
            ClipContext context = new ClipContext(eye, source, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p) {
                @Override public VoxelShape getBlockShape(BlockState state, BlockGetter blocks, BlockPos pos) {
                    VoxelShape shape = super.getBlockShape(state, blocks, pos); view.shape(shape); return shape;
                }
                @Override public VoxelShape getFluidShape(FluidState state, BlockGetter blocks, BlockPos pos) {
                    VoxelShape shape = super.getFluidShape(state, blocks, pos); view.shape(shape); return shape;
                }
            };
            if (view.clip(context).getType() != HitResult.Type.MISS) return false;
            List<Entity> bodies = new ArrayList<>();
            view.world.getEntities(EntityTypeTest.forClass(Entity.class), new AABB(eye, source).inflate(.001),
                    entity -> entity != p && entity != endpoint && !entity.isRemoved() && !entity.isSpectator(), bodies, 65);
            if (bodies.size() > 64) return false;
            for (Entity entity : bodies) {
                AABB bounds = entity.getBoundingBox();
                if (!finite(new Vec3(bounds.minX, bounds.minY, bounds.minZ)) || !finite(new Vec3(bounds.maxX, bounds.maxY, bounds.maxZ))
                        || bounds.contains(eye) || bounds.clip(eye, source).isPresent()) return false;
            }
            view.sameWorld(); return true;
        } catch (SightRefusal | IllegalArgumentException refused) { return false; }
    }
    private static final class SightRefusal extends RuntimeException {
        private SightRefusal() { super(null, null, false, false); }
    }
    /** Neighbor shape reads remain bounded and can never synchronously load another chunk. */
    private static final class SightView implements BlockGetter {
        final ServerPlayer player;
        final ServerLevel world;
        final AABB scope;
        int reads, boxes;
        SightView(ServerPlayer p, Vec3 from, Vec3 to) {
            player = p; world = p.serverLevel(); scope = new AABB(from, to).inflate(1.01);
        }
        void sameWorld() { if (player.serverLevel() != world || player.level() != world || player.isRemoved()) throw new SightRefusal(); }
        void loaded(AABB volume) {
            sameWorld();
            if (!world.getWorldBorder().isWithinBounds(volume) || volume.minY < world.getMinBuildHeight() || volume.maxY > world.getMaxBuildHeight()) throw new SightRefusal();
            for (int x = (int)Math.floor(volume.minX) >> 4; x <= (int)Math.floor(volume.maxX) >> 4; x++)
                for (int z = (int)Math.floor(volume.minZ) >> 4; z <= (int)Math.floor(volume.maxZ) >> 4; z++)
                    if (world.getChunkSource().getChunkNow(x, z) == null) throw new SightRefusal();
        }
        LevelChunk chunk(BlockPos pos) {
            sameWorld();
            if (++reads > 4096 || !scope.intersects(new AABB(pos)) || world.isOutsideBuildHeight(pos)
                    || !world.getWorldBorder().isWithinBounds(pos)) throw new SightRefusal();
            LevelChunk chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) throw new SightRefusal();
            return chunk;
        }
        @Override public BlockState getBlockState(BlockPos pos) { return chunk(pos).getBlockState(pos); }
        @Override public FluidState getFluidState(BlockPos pos) { return chunk(pos).getFluidState(pos); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return chunk(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK); }
        @Override public int getHeight() { return world.getHeight(); }
        @Override public int getMinBuildHeight() { return world.getMinBuildHeight(); }
        @Override public BlockHitResult clipWithInteractionOverride(Vec3 from, Vec3 to, BlockPos pos, VoxelShape shape, BlockState state) {
            return shape.clip(from, to, pos);
        }
        void shape(VoxelShape shape) {
            long cells = 1;
            for (Direction.Axis axis : Direction.Axis.values()) {
                var coords = shape.getCoords(axis);
                if (coords.size() > 34) throw new SightRefusal();
                for (double n : coords) if (!Double.isFinite(n)) throw new SightRefusal();
                cells *= Math.max(1, coords.size() - 1);
            }
            if (cells > 4096) throw new SightRefusal();
            shape.forAllBoxes((a, b, c, d, e, f) -> {
                if (++boxes > 2048 || !Double.isFinite(a) || !Double.isFinite(b) || !Double.isFinite(c)
                        || !Double.isFinite(d) || !Double.isFinite(e) || !Double.isFinite(f)) throw new SightRefusal();
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
                || MultiAxisReflectionArrowBridge.leased(arrow) || IronSandLance.leased(arrow) || ElectromasterPrototype.leased(arrow) || ElectromasterChargeDecoy.leased(arrow) || MagneticGroundStep.leased(arrow)) return false;
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
                && state.learned(ID) && state.learned(MultiAxisReflection.ID) && state.level == 5 && SkillCatalog.enabled("vecmanip", ID)
                && HighPowerGrowth.available(p) && HighPowerResources.effectiveLevel(p) == 5
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
    private static BigDecimal available(ServerPlayer p) {
        return HighPowerCombat.exactStateSnapshot(p).flatMap(base -> MagneticDomainReserve.savedShares(p, base)
                .map(shares -> base.shield().add(shares.total()))).orElse(BigDecimal.ZERO);
    }
    private static Context owned(ServerPlayer p) { if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null; }
    private static boolean thread(ServerPlayer p) { return p != null && p.server != null && p.server.isSameThread(); }
    private static long now(ServerPlayer p) { return MagneticDomain.serverTime(p.server); }
    private static Vec3 center(ServerPlayer p) { return p.position().add(0, 1, 0); }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z) && Math.abs(v.x) <= 30_000_001 && Math.abs(v.y) <= 30_000_001 && Math.abs(v.z) <= 30_000_001; }
    private static VectorParryRules.Point point(Vec3 v) { return v == null ? null : new VectorParryRules.Point(v.x, v.y, v.z); }
    private static Vec3 visualFacing(ServerPlayer p) {
        Vec3 look = p.getLookAngle(); return finite(look) && look.lengthSqr() > 1e-12 ? look.normalize() : new Vec3(0, 0, 1);
    }
    private static void finish(Context c) {
        ServerPlayer p = c.identity.owner(); if (!CONTEXTS.remove(p.getUUID(), c)) return;
        AutonomousVectorArrowReceipts.invalidate(p);
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
        ServerPlayer p = c.identity.owner();
        int phase = canAdmit(p) ? ACTIVE : PAUSED;
        send(c, phase, p.position(), visualFacing(p), p.position(), center(p), phase == ACTIVE);
    }
    /** Owner-only display data; an unreadable or unrepresentable snapshot is unknown, never zero. */
    private static Balance balance(ServerPlayer p) {
        var base = HighPowerCombat.exactStateSnapshot(p).orElse(null);
        if (base == null) return Balance.UNKNOWN;
        var shares = MagneticDomainReserve.savedShares(p, base).orElse(null);
        if (shares == null) return Balance.UNKNOWN;
        try {
            String available = CombatAmounts.decimalString(base.shield().add(shares.total()));
            String capacity = CombatAmounts.decimalString(base.maxShield().add(base.maxShield()).min(CombatAmounts.CAP));
            return available.length() <= 128 && capacity.length() <= 128 ? new Balance(available, capacity) : Balance.UNKNOWN;
        } catch (IllegalArgumentException invalid) { return Balance.UNKNOWN; }
    }
    private static void send(Context c, int phase, Vec3 position, Vec3 facing, Vec3 source, Vec3 contact, boolean ready) {
        ServerPlayer p = c.identity.owner();
        if (c.sequence == Long.MAX_VALUE || !finite(position) || !unit(facing) || !finite(source) || !finite(contact)) return;
        c.audience.removeIf(viewer -> viewer.connection == null || viewer.hasDisconnected() || viewer.isRemoved()
                || viewer.serverLevel() != c.identity.level() || viewer.position().distanceToSqr(p.position()) > 48 * 48);
        for (ServerPlayer viewer : c.identity.level().players()) if (viewer.position().distanceToSqr(p.position()) <= 32 * 32) c.audience.add(viewer);
        c.audience.add(p);
        long sequence = ++c.sequence;
        int remaining = phase == ENDED ? 0 : remainingHits(p);
        Balance balance = phase == ENDED ? Balance.UNKNOWN : balance(p);
        Visual ownerPacket = new Visual(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.identity.epoch(), sequence,
                phase, position, facing, source, contact, remaining, ready, balance.available, balance.capacity);
        Visual observerPacket = new Visual(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.identity.epoch(), sequence,
                phase, position, facing, source, contact, remaining, ready, "", "");
        c.lastPhase = phase;
        for (ServerPlayer viewer : c.audience) if (viewer.connection != null && !viewer.hasDisconnected() && viewer.serverLevel() == c.identity.level())
            PacketDistributor.sendToPlayer(viewer, viewer == p ? ownerPacket : observerPacket);
    }
    private static boolean unit(Vec3 v) { return finite(v) && Math.abs(v.lengthSqr() - 1) <= 1e-9; }
    public record Visual(int entity, UUID owner, long input, long token, long epoch, long sequence, int phase,
                         Vec3 point, Vec3 facing, Vec3 source, Vec3 contact, int remainingHits, boolean ready,
                         String availableShield, String shieldCapacity) implements CustomPacketPayload {
        public Visual {
            if (entity < 0 || owner == null || input <= 0 || token <= 0 || epoch <= 0 || sequence <= 0 || phase < ACTIVE || phase > ENDED
                    || !finite(point) || !unit(facing) || !finite(source) || !finite(contact) || point.add(0, 1, 0).distanceToSqr(contact) > 4
                    || !AutonomousVectorCircleRules.inRange(AutonomousVectorCircle.point(point), AutonomousVectorCircle.point(source))
                    || remainingHits < 0 || remainingHits > AutonomousVectorCircleRules.MAX_ABSORPTIONS
                    || phase != ABSORBED && (!source.equals(point) || !contact.equals(point.add(0, 1, 0)))
                    || phase == ACTIVE && !ready || phase == ABSORBED && remainingHits == AutonomousVectorCircleRules.MAX_ABSORPTIONS
                    || phase == ACTIVE && remainingHits == 0 || phase == PAUSED && ready
                    || phase == ENDED && (remainingHits != 0 || ready))
                throw new IllegalArgumentException("Invalid bounded NEW452 visual");
            if (availableShield == null || shieldCapacity == null || availableShield.length() > 128 || shieldCapacity.length() > 128
                    || availableShield.isEmpty() != shieldCapacity.isEmpty() || phase == ENDED && !availableShield.isEmpty())
                throw new IllegalArgumentException("Invalid owner-only NEW452 balance");
            if (!availableShield.isEmpty()) {
                BigDecimal available = CombatAmounts.parse(availableShield, "displayed available shield");
                BigDecimal capacity = CombatAmounts.parse(shieldCapacity, "displayed shield capacity");
                if (!CombatAmounts.decimalString(available).equals(availableShield) || !CombatAmounts.decimalString(capacity).equals(shieldCapacity)
                        || available.compareTo(capacity) > 0) throw new IllegalArgumentException("Invalid owner-only NEW452 balance");
            }
        }
        public Visual(int entity, UUID owner, long input, long token, long epoch, long sequence, int phase,
                      Vec3 point, Vec3 facing, Vec3 source, Vec3 contact, int remainingHits, boolean ready) {
            this(entity, owner, input, token, epoch, sequence, phase, point, facing, source, contact, remainingHits, ready, "", "");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_autonomous_vector_circle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((buffer, value) -> {
            buffer.writeInt(value.entity); buffer.writeUUID(value.owner); buffer.writeLong(value.input); buffer.writeLong(value.token);
            buffer.writeLong(value.epoch); buffer.writeLong(value.sequence); buffer.writeByte(value.phase);
            writeVec(buffer, value.point); writeVec(buffer, value.facing); writeVec(buffer, value.source); writeVec(buffer, value.contact);
            buffer.writeByte(value.remainingHits); buffer.writeBoolean(value.ready);
            buffer.writeUtf(value.availableShield, 128); buffer.writeUtf(value.shieldCapacity, 128);
        }, buffer -> new Visual(buffer.readInt(), buffer.readUUID(), buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readLong(),
                buffer.readUnsignedByte(), readVec(buffer), readVec(buffer), readVec(buffer), readVec(buffer), buffer.readUnsignedByte(), buffer.readBoolean(),
                buffer.readUtf(128), buffer.readUtf(128)));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static void feedback(ServerPlayer p, String key) {
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.academy.highpower.autonomous_vector_circle." + key), true);
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
            if (entity < 0 || owner == null || input <= 0 || epoch <= 0) throw new IllegalArgumentException("Invalid NEW452 rejection");
        }
        public static final Type<Rejection> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_autonomous_vector_circle_rejected"));
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
        CONTEXTS.clear(); INPUTS.clear(); AutonomousVectorArrowReceipts.clear();
        // Runtime cleanup never clears persisted admission or grants an extra physical tick.
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == event.getServer()) finish(c);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { clear(); PHYSICAL.remove(event.getServer()); }
}
