package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.WindPressureLiftGeometry;
import cn.academy.port.highpower.core.WindPressureLiftRules;
import cn.academy.port.highpower.core.WindPressureLiftRules.Mode;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW443 partial self-only integrated prototype. Grants authorize bounded additive local motion;
 * native travel, collision, gravity, landing, and fall accounting remain the sole movement owners. */
@EventBusSubscriber(modid = "academy")
public final class WindPressureLift {
    public static final String ID = "wind_pressure_lift", PAYMENT_KEY = "academy:wind_pressure_lift_payment";
    public static final String BUDGET_KEY = "academy:wind_pressure_lift_budget";
    public static final String BUDGET_QUARANTINE_KEY = "academy:wind_pressure_lift_budget_quarantined";
    private static final String CLOCK = "next_" + ID;
    private static final int MAX_CONTEXTS = 64, MAX_INPUTS = 4096;
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<MinecraftServer, PhysicalTick> PHYSICAL = new IdentityHashMap<>();
    // Client code may inspect ONLY these immutable records, with this lock. No ServerPlayer crosses it.
    private static final Object PROOF_LOCK = new Object();
    private static final Map<Long, Proof> PROOFS = new HashMap<>();
    private static final Map<MinecraftServer, Long> CLOCKS = new IdentityHashMap<>();
    private static final Map<Long, SnapshotProof> SNAPSHOTS = new HashMap<>();
    private static final Map<Long, StepFence> STEPS = new HashMap<>();
    private static final Map<Long, SessionProof> SESSIONS = new HashMap<>();
    private static final Map<BudgetKey, WindPressureLiftBudget> BUDGETS = new HashMap<>();
    // Only the server thread touches persistence images, never the client commit path.
    private static final Map<BudgetKey, Tag> BUDGET_IMAGES = new HashMap<>();
    private static long nextToken;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.wind_pressure_lift");
    public static Consumer<Grant> motionReceiver = ignored -> {};
    public static Consumer<Visual> clientReceiver = ignored -> {};
    public static Consumer<Rejection> clientRejectionReceiver = ignored -> {};
    private record Watermark(ServerPlayer owner, long epoch, long input) {}
    private record Proof(MinecraftServer server, Grant grant, long receipt) {}
    private record BudgetKey(MinecraftServer server, UUID owner) {}
    private record Window(long receipt, long from, long until) {}
    private record SnapshotProof(MinecraftServer server, Snapshot snapshot, WindPressureLiftBudget budget, StepFence fence) {}
    private record SessionProof(MinecraftServer server, int entity, UUID owner, long input, long epoch,
                                ResourceLocation dimension, long token, WindPressureLiftBudget budget, StepFence fence) {}
    private static final class StepFence {
        long baseLocal = -1, baseServer, lastLocal, lastRun;
        int lastIndex = -1, lastBatch;
        boolean closed;
    }
    /** Clocks are remaining genuine player ticks, never elapsed world time or offline time. */
    private record Payment(int remaining, int restart, int cooldown, long receipt, long resourceUntil) {}
    private static final class PhysicalTick {
        final int nativeTick;
        final long sequence;
        final long startedNanos;
        final Set<UUID> players = new HashSet<>();
        final Map<UUID, Integer> starts = new HashMap<>();
        PhysicalTick(int nativeTick, long sequence) { this.nativeTick = nativeTick; this.sequence = sequence; startedNanos = System.nanoTime(); }
    }
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final int preset;
        final long revision, started;
        final Vec3 anchor, direction;
        final Mode mode;
        final boolean fixture;
        Vec3 accepted;
        AABB acceptedBody;
        double activation;
        int cooldown;
        long receipt, lastGrantTick = -1, sequence, snapshotRevision;
        Window window, previousWindow;
        boolean paying;
        Grant grant;
        Context(ServerPlayer p, HighPowerInput.Input input, boolean fixture) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input; this.fixture = fixture;
            preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            started = physicalTick(p.server).sequence;
            anchor = p.position(); accepted = anchor; acceptedBody = p.getBoundingBox();
            mode = p.isShiftKeyDown() ? Mode.SIDE : Mode.UP;
            double yaw = Math.toRadians(p.getYRot());
            direction = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        }
    }
    private WindPressureLift() {}

    public static boolean toggle(ServerPlayer p, String wire) { return toggle(p, wire, false); }
    /** Private native fixture models integrated admission only; public requests always enforce the real gate. */
    private static boolean toggle(ServerPlayer p, String wire, boolean integratedContractFixture) {
        HighPowerInput.Input input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Watermark previous = INPUTS.get(p.getUUID());
        if (previous != null && previous.owner == p && previous.epoch == input.epoch() && previous.input >= input.input()) return false;
        if (previous == null && INPUTS.size() >= MAX_INPUTS) return reject(p, input, "not_ready");
        watermark(p, input);
        Context old = owned(p);
        if (AbilityConsumption.busy(p) || old != null && old.paying) return reject(p, input, "not_ready");
        if (!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))) return reject(p, input, "not_ready");
        if (old != null) {
            boolean same = structural(old);
            finish(old);
            if (same) return true;
        }
        // Unsupported servers refuse before cost lookup, CP, cooldown, or persistent payment mutation.
        if (!integratedContractFixture && (!p.server.isSingleplayer() || p.server.isPublished()))
            return reject(p, input, "unsupported");
        Payment previousPayment = readPayment(p);
        if (previousPayment == null || previousPayment.restart != 0 || previousPayment.cooldown != 0
                || !resourceClockMatches(p, previousPayment.resourceUntil) || !basic(p) || !ready(p)
                || conflicts(p) || !AbilityStorage.get(p).canUse(ID) || !safeBegin(p)
                || CONTEXTS.size() >= MAX_CONTEXTS || budget(p) == null || nextToken == Long.MAX_VALUE
                || physicalTick(p.server).sequence > Long.MAX_VALUE - WindPressureLiftRules.LIFETIME)
            return reject(p, input, "not_ready");
        // Readiness/collision extension callbacks may have accepted a newer press. Never overwrite
        // that context or resurrect this older input after they return.
        Watermark latest = INPUTS.get(p.getUUID());
        if (latest == null || latest.owner != p || latest.epoch != input.epoch() || latest.input != input.input()
                || CONTEXTS.containsKey(p.getUUID()) || !HighPowerBracerSession.authenticated(p, input)
                || !ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))) return reject(p, input, "not_ready");
        Context c = new Context(p, input, integratedContractFixture);
        CONTEXTS.put(p.getUUID(), c);
        // Freeze the price only after reserving the context: extension callbacks cannot reenter a start.
        c.paying = true;
        try {
            var growth = HighPowerGrowth.values(p).position();
            c.activation = WindPressureLiftRules.activationCost(growth);
            c.cooldown = WindPressureLiftRules.cooldownTicks(growth);
        } catch (RuntimeException | LinkageError failure) {
            finish(c); LOG.warn("NEW443 cost lookup refused", failure); return reject(p, input, "not_ready");
        } finally { c.paying = false; }
        if (!structural(c) || !c.anchor.equals(p.position()) || !c.acceptedBody.equals(p.getBoundingBox())
                || !safeBegin(p) || !pay(c, true) || !paid(c)) {
            finish(c); return reject(p, input, "not_ready");
        }
        WindPressureLiftBudget paidBudget = budget(p);
        if (paidBudget == null) { finish(c); return reject(p, input, "not_ready"); }
        synchronized (PROOF_LOCK) {
            StepFence fence = new StepFence(); STEPS.put(c.identity.token(), fence);
            SESSIONS.put(c.identity.token(), new SessionProof(p.server, p.getId(), p.getUUID(), input.input(), input.epoch(),
                    p.serverLevel().dimension().location(), c.identity.token(), paidBudget, fence));
        }
        sendVisual(c, false);
        if (owned(p) != c || !paid(c)) { finish(c); return false; }
        // The first movement permission is published only at the accepted-position ServerTick.Post.
        return true;
    }
    public static boolean start(ServerPlayer p, String wire) { return toggle(p, wire); }
    public static boolean release(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire); Context c = owned(p);
        return HighPowerBracerSession.authenticated(p, input) && c != null && c.input.equals(input);
    }
    public static boolean abort(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        watermark(p, input); Context c = owned(p);
        if (c == null || c.input.epoch() != input.epoch() || c.input.slot() != input.slot() || input.input() < c.input.input()) return false;
        finish(c); return true;
    }
    public static void abort(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c); }
    public static void remove(ServerPlayer p) {
        if (!thread(p)) return;
        abort(p); flushBudget(p, true); Watermark old = INPUTS.get(p.getUUID());
        if (old != null && old.owner == p) INPUTS.remove(p.getUUID(), old);
    }
    private static void watermark(ServerPlayer p, HighPowerInput.Input input) {
        Watermark old = INPUTS.get(p.getUUID());
        if (old == null && INPUTS.size() >= MAX_INPUTS) return;
        if (old == null || old.owner != p || old.epoch != input.epoch() || old.input < input.input())
            INPUTS.put(p.getUUID(), new Watermark(p, input.epoch(), input.input()));
    }
    /** A reserved payment context excludes other movement even while resource callbacks run. */
    public static boolean active(ServerPlayer p) { return owned(p) != null; }
    public static boolean holding(ServerPlayer p) { return active(p); }
    public static boolean renewalPending(ServerPlayer p) {
        Context c = owned(p); Payment payment = c == null ? null : readPayment(p);
        return c != null && c.receipt > 0 && payment != null && payment.receipt == c.receipt && payment.remaining == 0 && structural(c);
    }
    public static long paidUntil(ServerPlayer p) {
        Context c = owned(p); Payment payment = c == null ? null : readPayment(p);
        return c == null || payment == null || payment.receipt != c.receipt ? 0 : physicalTick(p.server).sequence + payment.remaining;
    }
    /** May run inside player travel: validates identity, never treats transient travel as accepted position. */
    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null || c.paying) return;
        if (!structural(c) || !ready(p) || conflicts(p) || expired(c)) finish(c);
    }
    private static boolean structural(Context c) {
        ServerPlayer p = c.identity.owner();
        return owned(p) == c && c.identity.owns(p) && HighPowerBracerSession.authenticated(p, c.input)
                && (c.fixture || p.server.isSingleplayer() && !p.server.isPublished()) && basic(p)
                && c.preset == c.identity.state().presets.current() && c.revision == c.identity.state().presets.revision()
                && ID.equals(c.identity.state().presets.currentSkill(c.input.slot()));
    }
    private static boolean basic(ServerPlayer p) {
        if (!thread(p) || p.connection == null || p.hasDisconnected() || !p.isAlive() || p.isRemoved() || p.isSpectator()
                || p.noPhysics || p.isNoGravity() || p.getGravity() != .08 || p.isPassenger() || p.isVehicle() || p.isSleeping()
                || p.isFallFlying() || p.isAutoSpinAttack() || p.getAbilities().flying || p.getAbilities().mayfly
                || p.getAbilities().instabuild || p.getAbilities().invulnerable || p.containerMenu != p.inventoryMenu
                || AbilityConsumption.busy(p) || p.isInWaterOrBubble() || p.isInLava() || !p.getMaxHeightFluidType().isAir()
                || p.isInPowderSnow || p.isSwimming() || !p.getActiveEffects().isEmpty()
                || p.getPose() != Pose.STANDING && p.getPose() != Pose.CROUCHING
                || !finite(p.position()) || !finite(p.getDeltaMovement()) || !Float.isFinite(p.getYRot()) || !Float.isFinite(p.getXRot())) return false;
        // Chunk access precedes the native climbable query; the controller never loads chunks.
        if (!loaded(p.serverLevel(), p.getBoundingBox()) || p.onClimbable()) return false;
        var state = AbilityStorage.peek(p);
        return state != null && "vecmanip".equals(state.category) && state.level >= 4 && state.level <= 5
                && state.activated && state.overloadFine && !state.interfering && state.learned(ID)
                && state.learned("multi_axis_reflection") && SkillCatalog.enabled("vecmanip", ID);
    }
    private static boolean ready(ServerPlayer p) {
        return p.connection instanceof NativeWindPressureLiftTeleportAccess
                && p.connection.getClass() == net.minecraft.server.network.ServerGamePacketListenerImpl.class
                && cn.academy.port.highpower.mixin.NativeScatterPlateMixinPlugin.windPressureLiftTeleportAvailable()
                && p.server.tickRateManager().tickrate() == 20.0F && !p.server.tickRateManager().isFrozen()
                && !p.server.tickRateManager().isSprinting()
                && HighPowerResources.ready(p) && HighPowerResources.effectiveLevel(p) >= 4 && HighPowerCombat.unsupportedReason(p).isEmpty();
    }
    private static boolean conflicts(ServerPlayer p) {
        return VectorParry.active(p) || BrakingTouch.active(p) || InertialAnchor.active(p) || (CirculationRelief.active(p) || AutonomousVectorCircle.active(p))
                || MultiAxisReflection.active(p) || MagneticGroundStep.active(p) || PolarityTether.active(p)
                || BeamStep.active(p) || HighPowerBracerSession.active(p)
                || cn.academy.port.skill.MagMovement.active(p) || cn.academy.port.skill.VecAccel.active(p)
                || cn.academy.port.skill.JetEngine.active(p) || cn.academy.port.skill.StormWing.active(p)
                || cn.academy.port.skill.MarkTeleport.active(p) || cn.academy.port.skill.PenetrateTeleport.active(p)
                || cn.academy.port.skill.Flashing.active(p);
    }
    private static boolean safeBegin(ServerPlayer p) {
        if (!p.onGround() || p.horizontalCollision || p.getDeltaMovement().y > 0 || !columnAvailable(p, p.position())) return false;
        AABB body = standingBody(p);
        if (!WindPressureLiftGeometry.contains(frame(p.position()), box(body)) || !safeVolume(p, body)) return false;
        AABB support = new AABB(body.minX, p.getY() - .025, body.minZ, body.maxX, p.getY() - .005, body.maxZ);
        if (!loaded(p.serverLevel(), support) || !p.serverLevel().getWorldBorder().isWithinBounds(support)) return false;
        var covered = Shapes.empty();
        for (var shape : p.serverLevel().getBlockCollisions(p, support)) covered = Shapes.or(covered, shape);
        return !Shapes.joinIsNotEmpty(Shapes.create(support), covered, BooleanOp.ONLY_FIRST);
    }
    private static AABB standingBody(ServerPlayer p) {
        return p.getDimensions(Pose.STANDING).makeBoundingBox(p.position());
    }
    private static boolean spatial(Context c) {
        ServerPlayer p = c.identity.owner(); AABB body = standingBody(p);
        if (!columnAvailable(p, c.anchor) || p.position().distanceToSqr(c.accepted) > WindPressureLiftRules.MAX_ACCEPTED_MOVE * WindPressureLiftRules.MAX_ACCEPTED_MOVE
                || p.horizontalCollision || p.verticalCollision && !p.onGround()
                || !WindPressureLiftGeometry.contains(frame(c.anchor), box(body)) || !safeVolume(p, body)) return false;
        // The swept accepted-position envelope must remain in the fixed column and in native clear space.
        // ServerPlayer travel can be transient during playerTick, so this is called only after native Post.
        AABB previous = body.move(c.accepted.subtract(p.position()));
        AABB sweep = previous.minmax(body);
        return WindPressureLiftGeometry.contains(frame(c.anchor), box(sweep)) && safeVolume(p, sweep);
    }
    private static boolean columnAvailable(ServerPlayer p, Vec3 anchor) {
        AABB column = new AABB(anchor.x - 3, anchor.y, anchor.z - 3, anchor.x + 3, anchor.y + 6, anchor.z + 3);
        if (column.minX < -30_000_000 || column.maxX > 30_000_000 || column.minZ < -30_000_000 || column.maxZ > 30_000_000)
            return false;
        return loaded(p.serverLevel(), column) && p.serverLevel().getWorldBorder().isWithinBounds(column);
    }
    private static boolean safeVolume(ServerPlayer p, AABB body) {
        ServerLevel level = p.serverLevel();
        if (!loaded(level, body) || !level.getWorldBorder().isWithinBounds(body) || !level.noCollision(p, body)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY, body.minZ),
                BlockPos.containing(body.maxX, body.maxY, body.maxZ)))
            if (!level.getFluidState(pos).isEmpty() || !level.mayInteract(p, pos)) return false;
        return true;
    }
    private static boolean loaded(ServerLevel level, AABB body) {
        if (!finite(new Vec3(body.minX, body.minY, body.minZ)) || !finite(new Vec3(body.maxX, body.maxY, body.maxZ))
                || body.minY < level.getMinBuildHeight() || body.maxY >= level.getMaxBuildHeight()) return false;
        for (int x = ((int)Math.floor(body.minX)) >> 4; x <= ((int)Math.floor(body.maxX)) >> 4; x++)
            for (int z = ((int)Math.floor(body.minZ)) >> 4; z <= ((int)Math.floor(body.maxZ)) >> 4; z++)
                if (!level.hasChunk(x, z)) return false;
        return true;
    }
    private static WindPressureLiftGeometry.Frame frame(Vec3 at) {
        return new WindPressureLiftGeometry.Frame(new WindPressureLiftGeometry.Point(at.x, at.y, at.z));
    }
    private static WindPressureLiftGeometry.Box box(AABB b) {
        return new WindPressureLiftGeometry.Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }
    private static boolean expired(Context c) {
        long now = physicalTick(c.identity.owner().server).sequence;
        return now < c.started || now - c.started >= WindPressureLiftRules.LIFETIME;
    }
    private static boolean paid(Context c) {
        if (c.paying || !structural(c) || expired(c)) return false;
        Payment payment = readPayment(c.identity.owner());
        return payment != null && c.receipt > 0 && payment.receipt == c.receipt && payment.remaining > 0
                && resourceClockMatches(c.identity.owner(), payment.resourceUntil);
    }
    private static boolean pay(Context c, boolean activation) {
        ServerPlayer p = c.identity.owner(); Payment old = readPayment(p);
        if (c.paying || !structural(c) || !ready(p) || conflicts(p) || old == null || old.receipt == Long.MAX_VALUE
                || old.cooldown != 0 || activation && old.restart != 0
                || !activation && (old.remaining != 0 || old.receipt != c.receipt)
                || !resourceClockMatches(p, old.resourceUntil) || !old.equals(readPayment(p))) return false;
        long worldTick = p.serverLevel().getGameTime();
        if (worldTick < 0 || worldTick >= Long.MAX_VALUE - 1) return false;
        Vec3 position = p.position(); AABB body = p.getBoundingBox();
        c.paying = true;
        CompoundTag budget = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
        Tag oldClock = budget.contains(CLOCK) ? budget.get(CLOCK).copy() : null;
        Tag oldPayment = p.getPersistentData().contains(PAYMENT_KEY) ? p.getPersistentData().get(PAYMENT_KEY).copy() : null;
        boolean spent = false, attempted = false, returned = false;
        try {
            double cost = activation ? c.activation : WindPressureLiftRules.sustainCost(HighPowerGrowth.values(p).position());
            if (!Double.isFinite(cost) || cost <= 0 || !structural(c) || !position.equals(p.position()) || !body.equals(p.getBoundingBox())
                    || !Objects.equals(oldPayment, p.getPersistentData().get(PAYMENT_KEY)) || !resourceClockMatches(p, old.resourceUntil)
                    || HighPowerResources.availableCp(p) < cost || !structural(c) || conflicts(p)
                    || !position.equals(p.position()) || !body.equals(p.getBoundingBox())
                    || !old.equals(readPayment(p)) || p.getPersistentData().getCompound(HighPowerResources.DATA_KEY) != budget) return false;
            // Only translate this controller's exact one-tick writer clock. Physical debt is authoritative.
            budget.putLong(CLOCK, worldTick);
            attempted = true;
            boolean result = HighPowerResources.trySpend(p, ID, cost, 1);
            returned = true;
            if (!result) return false;
            spent = true;
            // Certify the writer before checking the potentially canceled movement context. A known
            // successful debit still owes physical clocks, even when it bought no usable permission.
            // Unknown compound/clock/payment replacement is not repaired or rolled back here.
            if (!Objects.equals(oldPayment, p.getPersistentData().get(PAYMENT_KEY))
                    || p.getPersistentData().getCompound(HighPowerResources.DATA_KEY) != budget
                    || !budget.contains(CLOCK, Tag.TAG_LONG) || budget.getLong(CLOCK) != worldTick + 1) return false;
            long receipt = old.receipt + 1;
            int restart = activation ? WindPressureLiftRules.RESTART_FENCE : old.restart;
            writePayment(p, new Payment(WindPressureLiftRules.PAYMENT_PERIOD, restart, 0, receipt, worldTick + 1));
            c.receipt = receipt;
            long physical = physicalTick(p.server).sequence;
            c.previousWindow = c.window;
            long from = physical + 1, hardEnd = c.started + WindPressureLiftRules.LIFETIME;
            c.window = new Window(receipt, from, from + Math.min(WindPressureLiftRules.PAYMENT_PERIOD, hardEnd - from));
            holdPaymentTick(p, 1 | (activation ? 2 : 0));
            if (!structural(c) || !ready(p) || conflicts(p) || !structural(c)
                    || !position.equals(p.position()) || !body.equals(p.getBoundingBox())
                    || owned(p) != c || !resourceClockMatches(p, worldTick + 1)) {
                retirePayment(c); return false;
            }
            return true;
        } catch (RuntimeException | LinkageError failure) {
            // An exceptional writer may have debited before throwing. Never restore CP or mint a
            // replayable payment. Retire only our exact unchanged persistence scope as malformed.
            if (attempted && !returned && Objects.equals(oldPayment, p.getPersistentData().get(PAYMENT_KEY))) {
                CompoundTag closed = new CompoundTag(); closed.putInt("version", -1);
                p.getPersistentData().put(PAYMENT_KEY, closed);
            }
            if (c.receipt > 0) retirePayment(c);
            LOG.warn("NEW443 payment refused after an exceptional resource callback", failure);
            return false;
        } finally {
            if (!spent && (!attempted || returned) && p.getPersistentData().getCompound(HighPowerResources.DATA_KEY) == budget
                    && budget.contains(CLOCK, Tag.TAG_LONG) && budget.getLong(CLOCK) == worldTick) {
                if (oldClock == null) budget.remove(CLOCK); else budget.put(CLOCK, oldClock);
            }
            c.paying = false;
        }
    }
    private static Payment readPayment(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(PAYMENT_KEY)) return resourceClockMatches(p, -1) ? new Payment(0, 0, 0, 0, -1) : null;
        if (!root.contains(PAYMENT_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = root.getCompound(PAYMENT_KEY);
        if (!tag.getAllKeys().equals(Set.of("version", "remaining", "restart", "cooldown", "receipt", "resourceUntil"))
                || !tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != 1
                || !tag.contains("remaining", Tag.TAG_INT) || !tag.contains("restart", Tag.TAG_INT)
                || !tag.contains("cooldown", Tag.TAG_INT) || !tag.contains("receipt", Tag.TAG_LONG)
                || !tag.contains("resourceUntil", Tag.TAG_LONG)
                || !WindPressureLiftRules.paymentShape(tag.getInt("remaining"), tag.getInt("restart"), tag.getInt("cooldown"), tag.getLong("receipt"))
                || tag.getLong("receipt") == 0 || tag.getLong("resourceUntil") < 0 || tag.getLong("resourceUntil") >= Long.MAX_VALUE) return null;
        return new Payment(tag.getInt("remaining"), tag.getInt("restart"), tag.getInt("cooldown"), tag.getLong("receipt"), tag.getLong("resourceUntil"));
    }
    private static void writePayment(ServerPlayer p, Payment payment) {
        CompoundTag tag = new CompoundTag(); tag.putInt("version", 1); tag.putInt("remaining", payment.remaining);
        tag.putInt("restart", payment.restart); tag.putInt("cooldown", payment.cooldown); tag.putLong("receipt", payment.receipt);
        tag.putLong("resourceUntil", payment.resourceUntil); p.getPersistentData().put(PAYMENT_KEY, tag);
    }
    private static boolean resourceClockMatches(ServerPlayer p, long expected) {
        if (!p.getPersistentData().contains(HighPowerResources.DATA_KEY, Tag.TAG_COMPOUND)) return false;
        CompoundTag budget = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
        return expected == -1 ? !budget.contains(CLOCK) : budget.contains(CLOCK, Tag.TAG_LONG) && budget.getLong(CLOCK) == expected;
    }
    private static WindPressureLiftBudget budget(ServerPlayer p) {
        if (!thread(p) || p.getPersistentData().contains(BUDGET_QUARANTINE_KEY)) return null;
        Payment payment = readPayment(p);
        if (payment == null) return null;
        BudgetKey key = new BudgetKey(p.server, p.getUUID());
        WindPressureLiftBudget existing;
        synchronized (PROOF_LOCK) {
            existing = BUDGETS.get(key);
            if (existing == null && BUDGETS.size() >= MAX_INPUTS) return null;
        }
        if (existing != null) {
            boolean receiptAhead;
            synchronized (PROOF_LOCK) { receiptAhead = existing.receipt() > payment.receipt; }
            if (receiptAhead) { quarantineBudget(p, key); return null; }
            flushBudget(p, false);
            synchronized (PROOF_LOCK) { return existing.valid() ? existing : null; }
        }
        Tag original = p.getPersistentData().get(BUDGET_KEY);
        WindPressureLiftBudget created;
        try {
            if (original == null) created = new WindPressureLiftBudget(new int[0], new long[0], 0, 0);
            else {
                if (!(original instanceof CompoundTag tag) || !tag.getAllKeys().equals(Set.of("version", "ticks", "nanos", "receipt", "uses"))
                        || !tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != 1
                        || !tag.contains("ticks", Tag.TAG_INT_ARRAY) || !tag.contains("nanos", Tag.TAG_LONG_ARRAY)
                        || !tag.contains("receipt", Tag.TAG_LONG) || !tag.contains("uses", Tag.TAG_INT)) return null;
                created = new WindPressureLiftBudget(tag.getIntArray("ticks"), tag.getLongArray("nanos"), tag.getLong("receipt"), tag.getInt("uses"));
            }
        } catch (IllegalArgumentException malformed) { return null; }
        if (created.receipt() > payment.receipt) { quarantineBudget(p, key); return null; }
        long tick = physicalTick(p.server).sequence;
        synchronized (PROOF_LOCK) { created.initialize(tick, System.nanoTime()); BUDGETS.put(key, created); }
        if (original != null) BUDGET_IMAGES.put(key, original.copy());
        flushBudget(p, false);
        synchronized (PROOF_LOCK) { return created.valid() ? created : null; }
    }
    /** Preserve only this feature's refusal across normal save/load; never repair an external ledger. */
    private static void quarantineBudget(ServerPlayer p, BudgetKey key) {
        synchronized (PROOF_LOCK) { WindPressureLiftBudget budget = BUDGETS.get(key); if (budget != null) budget.invalidate(); }
        try { p.getPersistentData().putBoolean(BUDGET_QUARANTINE_KEY, true); }
        catch (RuntimeException | LinkageError failure) {
            LOG.warn("NEW443 budget remains closed in this session; quarantine persistence failed", failure);
        }
    }
    /** Server-thread persistence only. Logout first removes every consumable publication, then flushes. */
    private static void flushBudget(ServerPlayer p, boolean pause) {
        if (!thread(p)) return;
        BudgetKey key = new BudgetKey(p.server, p.getUUID()); WindPressureLiftBudget.Saved saved;
        synchronized (PROOF_LOCK) {
            WindPressureLiftBudget budget = BUDGETS.get(key); if (budget == null) return;
            if (!Objects.equals(BUDGET_IMAGES.get(key), p.getPersistentData().get(BUDGET_KEY))) budget.invalidate();
            long now = System.nanoTime(); if (pause) budget.pause(now); saved = budget.saved(now);
        }
        if (!saved.valid()) { quarantineBudget(p, key); return; }
        CompoundTag tag = new CompoundTag(); tag.putInt("version", 1); tag.putIntArray("ticks", saved.ticks());
        tag.putLongArray("nanos", saved.nanos()); tag.putLong("receipt", saved.receipt()); tag.putInt("uses", saved.receiptUses());
        CompoundTag expected = tag.copy();
        try { p.getPersistentData().put(BUDGET_KEY, tag); }
        catch (RuntimeException | LinkageError failure) { quarantineBudget(p, key); return; }
        if (!expected.equals(p.getPersistentData().get(BUDGET_KEY))) {
            quarantineBudget(p, key);
            return;
        }
        BUDGET_IMAGES.put(key, expected);
    }
    private static void advanceBudgets(MinecraftServer server, PhysicalTick clock) {
        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
            BudgetKey key = new BudgetKey(server, p.getUUID());
            boolean known;
            synchronized (PROOF_LOCK) {
                WindPressureLiftBudget existing = BUDGETS.get(key); known = existing != null;
                // Advance the already-online pure budget before payment/read callbacks. A current
                // step using the permitted prior snapshot must see this genuine new native tick.
                if (existing != null) existing.nativePre(clock.sequence, System.nanoTime());
            }
            if (!known && !p.getPersistentData().contains(BUDGET_KEY)) continue;
            WindPressureLiftBudget budget = budget(p); if (budget == null) { abort(p); continue; }
            synchronized (PROOF_LOCK) { budget.nativePre(clock.sequence, System.nanoTime()); }
            flushBudget(p, false);
        }
    }
    private static void holdPaymentTick(ServerPlayer p, int fields) {
        PhysicalTick clock = physicalTick(p.server);
        if (!clock.players.contains(p.getUUID())) clock.starts.merge(p.getUUID(), fields, (a, b) -> a | b);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !thread(p) || p.connection == null || p.hasDisconnected() || p.isRemoved()
                || p.serverLevel().getEntity(p.getUUID()) != p) return;
        PhysicalTick clock = physicalTick(p.server);
        if (!clock.players.add(p.getUUID()) || !p.getPersistentData().contains(PAYMENT_KEY)) return;
        Payment payment = readPayment(p);
        if (payment == null) return;
        int held = clock.starts.getOrDefault(p.getUUID(), 0);
        writePayment(p, new Payment((held & 1) != 0 ? payment.remaining : Math.max(0, payment.remaining - 1),
                (held & 2) != 0 ? payment.restart : Math.max(0, payment.restart - 1),
                (held & 4) != 0 ? payment.cooldown : Math.max(0, payment.cooldown - 1), payment.receipt, payment.resourceUntil));
    }
    private static PhysicalTick physicalTick(MinecraftServer server) {
        PhysicalTick previous = PHYSICAL.get(server);
        if (previous != null && previous.nativeTick == server.getTickCount()) return previous;
        long serial = previous == null ? 1 : previous.sequence == Long.MAX_VALUE ? Long.MAX_VALUE : previous.sequence + 1;
        PhysicalTick clock = new PhysicalTick(server.getTickCount(), serial); PHYSICAL.put(server, clock);
        synchronized (PROOF_LOCK) { CLOCKS.put(server, serial); }
        return clock;
    }
    /** Immutable evidence for the explicit dual-clock integrated-self contract. */
    public record Snapshot(Grant grant, long receipt, long paidFrom, long paidUntil, long revision, long issuedNanos) {}
    /** The client holds this only around pure identity/velocity checks, claim and one native setter. */
    public static Object integratedCommitLock() { return PROOF_LOCK; }
    /** Revocation only, including a previous cast's remaining debt. Never creates a budget or permission. */
    public static void pauseIntegratedOwner(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return;
        synchronized (PROOF_LOCK) {
            WindPressureLiftBudget budget = BUDGETS.get(new BudgetKey(server, owner));
            if (budget != null) budget.pause(System.nanoTime());
            for (SessionProof proof : new ArrayList<>(SESSIONS.values())) if (proof.server == server && proof.owner.equals(owner)) {
                proof.fence.closed = true; SNAPSHOTS.remove(proof.token); PROOFS.remove(proof.token);
            }
        }
    }
    /** Local cancellation freezes the owner's debt immediately; it never writes server persistence. */
    public static void stopIntegrated(MinecraftServer server, int entity, UUID owner, long input,
                                      long epoch, ResourceLocation dimension, long expectedToken) {
        if (server == null || owner == null || dimension == null || input <= 0 || epoch <= 0 || expectedToken < 0) return;
        synchronized (PROOF_LOCK) {
            for (SessionProof proof : new ArrayList<>(SESSIONS.values())) {
                if (proof.server != server || proof.entity != entity || !proof.owner.equals(owner) || proof.input != input
                        || proof.epoch != epoch || !proof.dimension.equals(dimension) || expectedToken != 0 && proof.token != expectedToken) continue;
                proof.fence.closed = true; proof.budget.pause(System.nanoTime());
                SNAPSHOTS.remove(proof.token); PROOFS.remove(proof.token);
            }
        }
    }
    public static Snapshot latestSnapshot(MinecraftServer server, int entity, UUID owner, long input,
                                          long epoch, ResourceLocation dimension, long expectedToken) {
        if (server == null || entity < 0 || owner == null || input <= 0 || epoch <= 0 || dimension == null || expectedToken < 0) return null;
        synchronized (PROOF_LOCK) {
            if (SNAPSHOTS.size() > MAX_CONTEXTS) return null;
            if (expectedToken > 0) {
                SnapshotProof proof = SNAPSHOTS.get(expectedToken);
                return matchesSnapshot(proof, server, entity, owner, input, epoch, dimension) ? proof.snapshot : null;
            }
            Snapshot result = null; int seen = 0;
            for (SnapshotProof proof : SNAPSHOTS.values()) {
                if (++seen > MAX_CONTEXTS) return null;
                if (!matchesSnapshot(proof, server, entity, owner, input, epoch, dimension)) continue;
                if (result != null) return null;
                result = proof.snapshot;
            }
            return result;
        }
    }
    private static boolean matchesSnapshot(SnapshotProof proof, MinecraftServer server, int entity, UUID owner,
                                            long input, long epoch, ResourceLocation dimension) {
        if (proof == null || proof.server != server) return false;
        Grant g = proof.snapshot.grant;
        return g.entity == entity && g.owner.equals(owner) && g.input == input && g.epoch == epoch
                && g.dimension.equals(dimension) && freshSnapshot(proof.snapshot, server);
    }
    private static boolean freshSnapshot(Snapshot snapshot, MinecraftServer server) {
        if (snapshot == null || server == null || snapshot.grant == null || snapshot.grant.terminal) return false;
        SnapshotProof proof = SNAPSHOTS.get(snapshot.grant.token); Long now = CLOCKS.get(server);
        if (proof == null || proof.server != server || proof.snapshot != snapshot || now == null
                || !proof.budget.valid() || proof.fence.closed || snapshot.receipt <= 0 || snapshot.revision <= 0
                || now < snapshot.paidFrom || now >= snapshot.paidUntil || now < snapshot.grant.issuedAt
                || now - snapshot.grant.issuedAt > 1) return false;
        long age = System.nanoTime() - snapshot.issuedNanos;
        return age >= 0 && age <= WindPressureLiftBudget.MAX_GAP;
    }
    /** Exact outcome of the existing atomic claim; never an additional authority or retry token. */
    public enum StepOutcome { ACCEPTED, INVALID_SNAPSHOT, INVALID_STEP, CLOCK_SKEW_CLOSED, BUDGET_REFUSED }
    public static boolean consumeIntegratedStep(Snapshot snapshot, MinecraftServer server, long localStep,
                                                long runId, int index, int batchSize) {
        return consumeIntegratedStepOutcome(snapshot, server, localStep, runId, index, batchSize) == StepOutcome.ACCEPTED;
    }
    /** Must be inside the caller's pure-setter critical section. No player, world or NBT is read here. */
    public static StepOutcome consumeIntegratedStepOutcome(Snapshot snapshot, MinecraftServer server, long localStep,
                                                long runId, int index, int batchSize) {
        if (!Thread.holdsLock(PROOF_LOCK) || !freshSnapshot(snapshot, server)) return StepOutcome.INVALID_SNAPSHOT;
        SnapshotProof proof = SNAPSHOTS.get(snapshot.grant.token); StepFence fence = proof.fence;
        long now = CLOCKS.get(server);
        if (localStep <= 0 || runId <= 0 || batchSize < 1 || batchSize > 2 || index < 0 || index >= batchSize
                || localStep <= fence.lastLocal || runId < fence.lastRun
                || runId == fence.lastRun && (index <= fence.lastIndex || batchSize != fence.lastBatch)) return StepOutcome.INVALID_STEP;
        if (fence.baseLocal < 0) { fence.baseLocal = localStep; fence.baseServer = now; }
        long localAge = localStep - fence.baseLocal, serverAge = now - fence.baseServer;
        fence.lastLocal = localStep; fence.lastRun = runId; fence.lastIndex = index; fence.lastBatch = batchSize;
        if (localAge < 0 || serverAge < 0 || localAge > serverAge + 1 || serverAge > localAge + 1) {
            fence.closed = true; return StepOutcome.CLOCK_SKEW_CLOSED;
        }
        return proof.budget.take(now, snapshot.receipt, System.nanoTime()) ? StepOutcome.ACCEPTED : StepOutcome.BUDGET_REFUSED;
    }
    /**
     * Non-consuming integrated-client lookup at its native input boundary. Only immutable proof and
     * clock publications are inspected under this lock; no mutable server owner/world is read.
     * Token zero bootstraps one exact captured input, with a bounded unique-match search. Once the
     * client binds a token it must supply that token. A missing, consumed or expired proof stays absent.
     */
    public static Grant latestIntegrated(MinecraftServer server, int entity, UUID owner, long input,
                                        long epoch, ResourceLocation dimension, long expectedToken) {
        if (server == null || entity < 0 || owner == null || input <= 0 || epoch <= 0
                || dimension == null || expectedToken < 0) return null;
        synchronized (PROOF_LOCK) {
            if (PROOFS.size() > MAX_CONTEXTS) return null;
            if (expectedToken > 0) {
                Proof proof = PROOFS.get(expectedToken);
                return matchingIntegrated(proof, server, entity, owner, input, epoch, dimension) ? proof.grant : null;
            }
            Grant match = null; int examined = 0;
            for (Proof proof : PROOFS.values()) {
                if (++examined > MAX_CONTEXTS) return null;
                if (!matchingIntegrated(proof, server, entity, owner, input, epoch, dimension)) continue;
                if (match != null) return null; // Ambiguity can never choose or mint a new authority.
                match = proof.grant;
            }
            return match;
        }
    }
    /** Called only with PROOF_LOCK held, including the final freshness test. */
    private static boolean matchingIntegrated(Proof proof, MinecraftServer server, int entity, UUID owner,
                                              long input, long epoch, ResourceLocation dimension) {
        if (proof == null || proof.server != server) return false;
        Grant grant = proof.grant;
        return grant.entity() == entity && grant.owner().equals(owner) && grant.input() == input
                && grant.epoch() == epoch && grant.dimension().equals(dimension) && freshProof(grant, server);
    }
    /** Integrated render thread API. The map, clock and grant are immutable server-thread publications. */
    public static boolean integratedFresh(Grant grant, MinecraftServer server) {
        synchronized (PROOF_LOCK) { return freshProof(grant, server); }
    }
    public static boolean consumeIntegrated(Grant grant, MinecraftServer server) {
        synchronized (PROOF_LOCK) {
            if (!freshProof(grant, server)) return false;
            Proof proof = PROOFS.get(grant.token());
            WindPressureLiftBudget budget = BUDGETS.get(new BudgetKey(server, grant.owner()));
            if (budget == null || !budget.take(CLOCKS.get(server), proof.receipt, System.nanoTime())) return false;
            PROOFS.remove(grant.token()); return true;
        }
    }
    private static boolean freshProof(Grant grant, MinecraftServer server) {
        if (grant == null || server == null || grant.terminal()) return false;
        Proof proof = PROOFS.get(grant.token()); Long clock = CLOCKS.get(server);
        return proof != null && proof.server == server && proof.grant.equals(grant) && clock != null
                && grant.expiresAt() == grant.issuedAt() + 1 && clock == grant.issuedAt();
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void pre(ServerTickEvent.Pre event) {
        PhysicalTick clock = physicalTick(event.getServer());
        advanceBudgets(event.getServer(), clock);
        for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == event.getServer()) {
            ServerPlayer p = c.identity.owner();
            boolean closed; synchronized (PROOF_LOCK) { StepFence f = STEPS.get(c.identity.token()); closed = f != null && f.closed; }
            if (closed || !structural(c) || !ready(p) || conflicts(p) || expired(c) || !paid(c) || !spatial(c)) { finish(c); continue; }
            publishSnapshot(c, clock, null);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void post(ServerTickEvent.Post event) { post(event.getServer()); }
    /** Sole grant publication point, after native ServerPlayer travel restored accepted positions. */
    private static void post(MinecraftServer server) {
        PhysicalTick physical = physicalTick(server);
        for (Context c : new ArrayList<>(CONTEXTS.values())) {
            ServerPlayer p = c.identity.owner();
            if (p.server != server || c.paying || c.lastGrantTick == physical.sequence) continue;
            boolean closed; synchronized (PROOF_LOCK) { StepFence f = STEPS.get(c.identity.token()); closed = f != null && f.closed; }
            if (closed) { finish(c); continue; }
            c.lastGrantTick = physical.sequence;
            if (physical.sequence == Long.MAX_VALUE || !structural(c) || !ready(p) || conflicts(p) || expired(c) || !spatial(c)) {
                finish(c); continue;
            }
            Vec3 accepted = p.position(); AABB body = p.getBoundingBox(); Pose pose = p.getPose();
            Payment payment = readPayment(p);
            if (payment == null || payment.receipt != c.receipt || payment.remaining == 0 && !pay(c, false)
                    || !paid(c) || !spatial(c) || !structural(c) || owned(p) != c
                    || !accepted.equals(p.position()) || !body.equals(p.getBoundingBox()) || pose != p.getPose()
                    || c.sequence == Long.MAX_VALUE) { finish(c); continue; }
            c.accepted = accepted; c.acceptedBody = body;
            Grant grant = new Grant(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.input.epoch(),
                    c.identity.level().dimension().location(), ++c.sequence, physical.sequence, physical.sequence + 1,
                    c.anchor, c.accepted, pose, c.mode, c.direction, false);
            c.grant = grant;
            synchronized (PROOF_LOCK) { PROOFS.put(c.identity.token(), new Proof(server, grant, c.receipt)); }
            publishSnapshot(c, physical, grant);
            PacketDistributor.sendToPlayer(p, grant);
            if (owned(p) == c && paid(c) && (c.sequence == 1 || c.sequence % 4 == 0)) sendVisual(c, false);
        }
        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) flushBudget(p, false);
    }
    private static Window windowFor(Context c, long now) {
        if (c.window != null && now >= c.window.from && now < c.window.until) return c.window;
        if (c.previousWindow != null && now >= c.previousWindow.from && now < c.previousWindow.until) return c.previousWindow;
        return null;
    }
    private static void publishSnapshot(Context c, PhysicalTick physical, Grant existing) {
        ServerPlayer p = c.identity.owner(); Window window = windowFor(c, physical.sequence);
        if (window == null) return; // A payment never starts a partial Pre window or borrows the next one.
        Vec3 position = p.position(); AABB body = p.getBoundingBox(); Pose pose = p.getPose();
        WindPressureLiftBudget budget = budget(p);
        if (budget == null || c.snapshotRevision == Long.MAX_VALUE || c.sequence == Long.MAX_VALUE
                || !structural(c) || !ready(p) || conflicts(p) || !paid(c) || !spatial(c)
                || !structural(c) || !position.equals(p.position()) || !body.equals(p.getBoundingBox()) || pose != p.getPose()) {
            finish(c); return;
        }
        Grant grant = existing == null ? new Grant(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.input.epoch(),
                c.identity.level().dimension().location(), c.sequence + 1, physical.sequence, physical.sequence + 1,
                c.anchor, position, pose, c.mode, c.direction, false) : existing;
        Snapshot snapshot = new Snapshot(grant, window.receipt, window.from, window.until, ++c.snapshotRevision, physical.startedNanos);
        synchronized (PROOF_LOCK) {
            StepFence fence = STEPS.computeIfAbsent(c.identity.token(), ignored -> new StepFence());
            if (!fence.closed && budget.valid()) SNAPSHOTS.put(c.identity.token(), new SnapshotProof(p.server, snapshot, budget, fence));
        }
    }
    private static void finish(Context c) {
        synchronized (PROOF_LOCK) { PROOFS.remove(c.identity.token()); SNAPSHOTS.remove(c.identity.token()); STEPS.remove(c.identity.token()); SESSIONS.remove(c.identity.token()); }
        ServerPlayer p = c.identity.owner();
        if (!CONTEXTS.remove(p.getUUID(), c)) return;
        retirePayment(c);
        flushBudget(p, false);
        if (p.connection == null || p.hasDisconnected()) return;
        if (c.grant != null) {
            Grant g = c.grant;
            PacketDistributor.sendToPlayer(p, new Grant(g.entity(), g.owner(), g.input(), g.token(), g.epoch(), g.dimension(),
                    g.sequence(), g.issuedAt(), g.expiresAt(), g.anchor(), g.accepted(), g.pose(), g.mode(), g.direction(), true));
        }
        sendVisual(c, true);
    }
    /** Debt settlement also covers a context removed inside a successful resource callback. */
    private static void retirePayment(Context c) {
        if (c.receipt <= 0) return;
        ServerPlayer p = c.identity.owner(); Payment payment = readPayment(p);
        if (payment != null && payment.receipt == c.receipt) {
            int restart = Math.max(payment.restart, WindPressureLiftRules.RESTART_FENCE);
            int cooldown = Math.max(payment.cooldown, c.cooldown);
            writePayment(p, new Payment(payment.remaining, restart, cooldown, payment.receipt, payment.resourceUntil));
            holdPaymentTick(p, (restart > payment.restart ? 2 : 0) | (cooldown > payment.cooldown ? 4 : 0));
        }
        c.identity.state().setCooldown(ID, c.cooldown);
    }
    private static void sendVisual(Context c, boolean terminal) {
        ServerPlayer p = c.identity.owner();
        if (p.connection == null || p.hasDisconnected()) return;
        long now = physicalTick(p.server).sequence;
        long remaining = Math.max(0, WindPressureLiftRules.LIFETIME - Math.max(0, now - c.started));
        long expires = terminal ? now : now + Math.min(4, remaining);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new Visual(p.getId(), p.getUUID(), c.input.input(), c.identity.token(),
                c.input.epoch(), c.identity.level().dimension().location(), c.sequence, c.anchor, c.mode, c.direction,
                now, expires, terminal));
    }
    private static boolean reject(ServerPlayer p, HighPowerInput.Input input, String reason) {
        PacketDistributor.sendToPlayer(p, new Rejection(p.getId(), p.getUUID(), input.input(), input.epoch()));
        p.displayClientMessage(Component.translatable("message.academy.highpower.wind_pressure_lift." + reason), true);
        return false;
    }
    private static boolean thread(ServerPlayer p) { return p != null && p.server != null && p.server.isSameThread(); }
    private static Context owned(ServerPlayer p) {
        if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null;
    }
    private static boolean finite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z)
                && Math.abs(v.x) <= 30_000_001 && Math.abs(v.y) <= 30_000_001 && Math.abs(v.z) <= 30_000_001;
    }
    private static boolean direction(Vec3 v) { return finite(v) && v.y == 0 && Math.abs(v.lengthSqr() - 1) <= 1e-9; }
    public record Grant(int entity, UUID owner, long input, long token, long epoch, ResourceLocation dimension,
                        long sequence, long issuedAt, long expiresAt, Vec3 anchor, Vec3 accepted,
                        Pose pose, Mode mode, Vec3 direction, boolean terminal) implements CustomPacketPayload {
        public Grant {
            if (entity < 0 || owner == null || input <= 0 || token <= 0 || epoch <= 0 || dimension == null || sequence <= 0
                    || issuedAt < 0 || issuedAt == Long.MAX_VALUE || expiresAt != issuedAt + 1 || !finite(anchor) || !finite(accepted)
                    || pose != Pose.STANDING && pose != Pose.CROUCHING
                    || mode == null || !WindPressureLift.direction(direction)) throw new IllegalArgumentException("Invalid NEW443 grant");
        }
        public static final Type<Grant> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_wind_pressure_lift_grant"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Grant> CODEC = StreamCodec.of((b, g) -> {
            b.writeInt(g.entity); b.writeUUID(g.owner); b.writeLong(g.input); b.writeLong(g.token); b.writeLong(g.epoch);
            b.writeResourceLocation(g.dimension); b.writeLong(g.sequence); b.writeLong(g.issuedAt); b.writeLong(g.expiresAt);
            vec(b, g.anchor); vec(b, g.accepted); b.writeEnum(g.pose); b.writeEnum(g.mode); vec(b, g.direction); b.writeBoolean(g.terminal);
        }, b -> new Grant(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readResourceLocation(),
                b.readLong(), b.readLong(), b.readLong(), vec(b), vec(b), b.readEnum(Pose.class), b.readEnum(Mode.class), vec(b), b.readBoolean()));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Visual(int entity, UUID owner, long input, long token, long epoch, ResourceLocation dimension,
                         long sequence, Vec3 anchor, Mode mode, Vec3 direction, long issuedAt, long expiresAt,
                         boolean terminal) implements CustomPacketPayload {
        public Visual {
            if (entity < 0 || owner == null || input <= 0 || token <= 0 || epoch <= 0 || dimension == null || sequence < 0
                    || !finite(anchor) || mode == null || !WindPressureLift.direction(direction) || issuedAt < 0 || expiresAt < 0)
                throw new IllegalArgumentException("Invalid NEW443 visual");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_wind_pressure_lift_visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, g) -> {
            b.writeInt(g.entity); b.writeUUID(g.owner); b.writeLong(g.input); b.writeLong(g.token); b.writeLong(g.epoch);
            b.writeResourceLocation(g.dimension); b.writeLong(g.sequence); vec(b, g.anchor); b.writeEnum(g.mode); vec(b, g.direction);
            b.writeLong(g.issuedAt); b.writeLong(g.expiresAt); b.writeBoolean(g.terminal);
        }, b -> new Visual(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readResourceLocation(),
                b.readLong(), vec(b), b.readEnum(Mode.class), vec(b), b.readLong(), b.readLong(), b.readBoolean()));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Rejection(int entity, UUID owner, long input, long epoch) implements CustomPacketPayload {
        public Rejection { if (entity < 0 || owner == null || input <= 0 || epoch <= 0) throw new IllegalArgumentException("Invalid NEW443 rejection"); }
        public static final Type<Rejection> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_wind_pressure_lift_rejected"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Rejection> CODEC = StreamCodec.of((b, g) -> {
            b.writeInt(g.entity); b.writeUUID(g.owner); b.writeLong(g.input); b.writeLong(g.epoch);
        }, b -> new Rejection(b.readInt(), b.readUUID(), b.readLong(), b.readLong()));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static void vec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 vec(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(Grant.TYPE, Grant.CODEC, (packet, context) -> motionReceiver.accept(packet));
            event.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet));
            event.registrar("1").playToClient(Rejection.TYPE, Rejection.CODEC, (packet, context) -> clientRejectionReceiver.accept(packet));
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void death(LivingDeathEvent e) { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void knockback(LivingKnockBackEvent e) { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) abort(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void leave(EntityLeaveLevelEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void clone(PlayerEvent.Clone e) {
        if (e.getOriginal() instanceof ServerPlayer old) {
            remove(old);
            if (e.getEntity() instanceof ServerPlayer replacement && old.getUUID().equals(replacement.getUUID()) && old.getPersistentData().contains(PAYMENT_KEY))
                replacement.getPersistentData().put(PAYMENT_KEY, old.getPersistentData().get(PAYMENT_KEY).copy());
            if (e.getEntity() instanceof ServerPlayer replacement && old.getUUID().equals(replacement.getUUID()) && old.getPersistentData().contains(BUDGET_KEY))
                replacement.getPersistentData().put(BUDGET_KEY, old.getPersistentData().get(BUDGET_KEY).copy());
            if (e.getEntity() instanceof ServerPlayer replacement && old.getUUID().equals(replacement.getUUID()) && old.getPersistentData().contains(BUDGET_QUARANTINE_KEY))
                replacement.getPersistentData().put(BUDGET_QUARANTINE_KEY, old.getPersistentData().get(BUDGET_QUARANTINE_KEY).copy());
        }
    }
    public static void clear() {
        for (Context c : new ArrayList<>(CONTEXTS.values())) finish(c);
        CONTEXTS.clear(); INPUTS.clear(); synchronized (PROOF_LOCK) { PROOFS.clear(); SNAPSHOTS.clear(); STEPS.clear(); SESSIONS.clear(); }
        // Runtime cancellation cannot reset persisted clocks, native-tick deduplication, or monotonic tokens.
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) {
        for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) finish(c);
        for (ServerPlayer p : new ArrayList<>(e.getServer().getPlayerList().getPlayers())) flushBudget(p, true);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) {
        for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) finish(c);
        INPUTS.entrySet().removeIf(entry -> entry.getValue().owner.server == e.getServer());
        PHYSICAL.remove(e.getServer());
        synchronized (PROOF_LOCK) {
            CLOCKS.remove(e.getServer()); PROOFS.entrySet().removeIf(entry -> entry.getValue().server == e.getServer());
            SNAPSHOTS.entrySet().removeIf(entry -> entry.getValue().server == e.getServer());
            BUDGETS.keySet().removeIf(key -> key.server == e.getServer());
        }
        BUDGET_IMAGES.keySet().removeIf(key -> key.server == e.getServer());
    }
}
