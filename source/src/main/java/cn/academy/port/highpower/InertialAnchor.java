package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.ExactCombatState;
import cn.academy.port.highpower.core.InertialAnchorRules;
import cn.academy.port.highpower.core.VectorParryRules;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
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
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW422: provisional paid toggle; exact base S owns fees and native grounded fall absorption. */
@EventBusSubscriber(modid = "academy")
public final class InertialAnchor {
    public static final String ID = "inertial_anchor", PAYMENT_KEY = "academy:inertial_anchor_payment", QUOTA_KEY = "academy:inertial_anchor_quota";
    public static final int ACTIVE = 0, KNOCKBACK = 1, LANDED = 2, ENDED = 3;
    public static final ResourceLocation MOVEMENT_MODIFIER = ResourceLocation.fromNamespaceAndPath("academy", "inertial_anchor_movement");
    private static final int MAX_INPUTS = 4096;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.inertial_anchor");
    private static final String CLOCK = "next_" + ID;
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(ServerPlayer owner, long epoch, long input) {}
    private record Payment(long observed, long until, long stopUntil, String dimension) {}
    private record Quota(long observed, int hits) {}
    private record FallPlan(InertialAnchorRules.Result result, Quota quota, long tick, Vec3 position, Vec3 facing) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final int preset, cooldown;
        final long revision, started;
        final double activation;
        final AttributeInstance movement;
        final AttributeModifier modifier = new AttributeModifier(MOVEMENT_MODIFIER, -.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        final Set<ServerPlayer> audience = new LinkedHashSet<>();
        long paidUntil, lastTick = -1, sequence, lastInsufficientNotice = -1;
        boolean paying, planning, capturing, installed;
        Capture pending;
        NativeReduction reducing;
        FallPlan fall;
        Context(ServerPlayer p, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            started = now(p); movement = p.getAttribute(Attributes.MOVEMENT_SPEED);
            BigDecimal q = HighPowerGrowth.values(p).position();
            activation = InertialAnchorRules.activationCost(q); cooldown = InertialAnchorRules.cooldownTicks(q);
        }
    }
    /** Source/facing are frozen before callbacks. A native frame must independently bind this receipt. */
    public static final class Capture {
        private final Context context;
        private final DamageSource source;
        private final Zombie attacker;
        private final int attackerId;
        private final UUID attackerUuid;
        private final Vec3 ownerPosition, facing, contact;
        private final long tick;
        private boolean claimed;
        private Capture(Context c, DamageSource source, Zombie attacker, Vec3 origin) {
            context = c; this.source = source; this.attacker = attacker;
            attackerId = attacker.getId(); attackerUuid = attacker.getUUID();
            ServerPlayer p = c.identity.owner(); ownerPosition = p.position(); facing = visualFacing(p); tick = now(p);
            Vec3 direction = origin.subtract(center(p));
            contact = center(p).add(direction.lengthSqr() > 0 ? direction.normalize() : Vec3.ZERO);
        }
    }
    /** One reviewed native base-knockback call. It is never an arbitrary velocity multiplier. */
    public static final class NativeReduction {
        private final Capture capture;
        private final double strength, x, z;
        private InertialAnchorRules.KnockbackPlan plan;
        private Quota quota;
        private Vec3 velocity;
        private Pose pose;
        private boolean grounded, allowed, claimed, committed, finished;
        private double resistance;
        private Runnable notifications;
        private NativeReduction(Capture capture, double strength, double x, double z) {
            this.capture = capture; this.strength = strength; this.x = x; this.z = z;
        }
    }
    private InertialAnchor() {}

    /** Fresh press toggles; release never ends this stance or spends another CP payment. */
    public static boolean toggle(ServerPlayer p, String wire) {
        Context resolving = owned(p); if (resolving != null && busy(resolving)) return false;
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
            finish(old); if (same) return true;
        }
        Payment fence = readPayment(p);
        AttributeInstance movement = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (!basic(p, false) || !HighPowerResources.ready(p)) return feedback(p, "not_ready");
        if (HighPowerCombat.unsupportedReason(p).isPresent()) return feedback(p, "unsupported");
        if (conflicts(p)) return feedback(p, "conflict");
        if (!stableGrounded(p)) return feedback(p, "standing_required");
        if (nextToken == Long.MAX_VALUE || fence == null || readQuota(p) == null
                || now(p) < fence.stopUntil || !AbilityStorage.get(p).canUse(ID) || available(p).signum() <= 0
                || movement == null || movement.getModifier(MOVEMENT_MODIFIER) != null) return feedback(p, "not_ready");
        Vec3 position = p.position(), velocity = p.getDeltaMovement(); AABB body = p.getBoundingBox();
        Context c = new Context(p, input); CONTEXTS.put(p.getUUID(), c);
        if (!pay(c, true) || !valid(c, false) || conflicts(p) || !position.equals(p.position())
                || !velocity.equals(p.getDeltaMovement()) || !body.equals(p.getBoundingBox()) || !stableGrounded(p)
                || p.getAttribute(Attributes.MOVEMENT_SPEED) != c.movement || c.movement.getModifier(MOVEMENT_MODIFIER) != null) {
            finish(c); return feedback(p, "not_ready");
        }
        c.installed = true;
        try { c.movement.addTransientModifier(c.modifier); }
        catch (RuntimeException failure) { finish(c); return feedback(p, "not_ready"); }
        if (!valid(c, false)) { finish(c); return feedback(p, "not_ready"); }
        send(c, ACTIVE, p.position(), visualFacing(p), p.position(), p.onGround()); return true;
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
        Watermark previous = INPUTS.get(p.getUUID());
        if (previous == null && INPUTS.size() >= MAX_INPUTS) return;
        if (previous == null || previous.owner != p || previous.epoch != input.epoch() || previous.input < input.input())
            INPUTS.put(p.getUUID(), new Watermark(p, input.epoch(), input.input()));
    }
    public static void abort(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c); }
    public static void remove(ServerPlayer p) {
        if (!thread(p)) return;
        abort(p); Watermark watermark = INPUTS.get(p.getUUID());
        if (watermark != null && watermark.owner == p) INPUTS.remove(p.getUUID(), watermark);
    }
    public static boolean active(ServerPlayer p) { Context c = owned(p); return c != null && c.installed && valid(c, false); }
    public static boolean holding(ServerPlayer p) { return active(p); }
    public static boolean blocksRecovery(ServerPlayer p) { Context c = owned(p); return c != null && c.paidUntil > 0 && structurallyValid(c, false); }
    public static boolean renewalPending(ServerPlayer p) { Context c = owned(p); return c != null && c.paidUntil > 0 && now(p) >= c.paidUntil && structurallyValid(c, false); }
    public static long paidUntil(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.paidUntil; }
    public static int remainingHits(ServerPlayer p) { Quota q = thread(p) ? readQuota(p) : null; return q == null ? 0 : InertialAnchorRules.MAX_HITS_PER_TICK - q.hits; }
    private static boolean busy(Context c) { return c.paying || c.planning || c.capturing || c.reducing != null || c.fall != null; }
    private static boolean conflicts(ServerPlayer p) { return (MultiAxisReflection.active(p) || WindPressureLift.active(p)) || VectorParry.active(p) || BrakingTouch.active(p) || (CirculationRelief.active(p) || AutonomousVectorCircle.active(p)); }

    static Capture capture(ServerPlayer p, DamageSource source) {
        Context c = owned(p);
        if (c == null || !c.installed || busy(c) || !valid(c, false) || conflicts(p) || remainingHits(p) == 0) return null;
        c.capturing = true;
        try {
            Zombie attacker = reviewedSource(p, source); if (attacker == null) return null;
            Vec3 origin = source.getSourcePosition();
            if (!finite(origin) || !InertialAnchorRules.front(point(p.position()), point(p.getLookAngle()), point(origin))) return null;
            Capture capture = new Capture(c, source, attacker, origin);
            if (!valid(c, false)) return null;
            c.pending = capture; return capture;
        } finally { c.capturing = false; }
    }
    private static boolean currentCapture(ServerPlayer p, Capture capture) {
        if (capture == null || owned(p) != capture.context || !capture.context.installed
                || !HighPowerCombat.inertialAnchorNativeFrame(p, capture) || !valid(capture.context, true)
                || now(p) != capture.tick || !capture.ownerPosition.equals(p.position())) return false;
        Zombie attacker = capture.attacker;
        return attacker.getId() == capture.attackerId && attacker.getUUID().equals(capture.attackerUuid)
                && p.serverLevel().getEntity(capture.attackerId) == attacker && p.serverLevel().getEntity(capture.attackerUuid) == attacker
                && reviewedSource(p, capture.source) == attacker;
    }
    public static NativeReduction beginNativeReduction(ServerPlayer p, double strength, double x, double z) {
        Context c = owned(p); Capture capture = c == null ? null : c.pending;
        if (capture == null || capture.claimed || busy(c) || !currentCapture(p, capture)
                || !InertialAnchorNativeBridge.available(p) || strength != (double).4F
                || !Double.isFinite(x) || !Double.isFinite(z) || x == 0 && z == 0 || remainingHits(p) == 0) return null;
        capture.claimed = true; c.pending = null;
        NativeReduction receipt = new NativeReduction(capture, strength, x, z); c.reducing = receipt; return receipt;
    }
    public static boolean allowNativeReduction(ServerPlayer p, NativeReduction receipt, float strength, double x, double z) {
        if (!reductionCurrent(p, receipt) || receipt.allowed || receipt.claimed || !Float.isFinite(strength)
                || strength != (float)receipt.strength || x != receipt.x || z != receipt.z
                || HighPowerCombat.unsupportedReason(p).isPresent()) return false;
        double resistance = p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (!Double.isFinite(resistance) || resistance < 0 || resistance >= 1 || !finite(p.getDeltaMovement())) return false;
        ExactCombatState before = HighPowerCombat.exactStateSnapshot(p).orElse(null);
        if (before == null) return false;
        InertialAnchorRules.KnockbackPlan plan = InertialAnchorRules.planKnockback(before, (double)strength);
        if (plan == null) { insufficientFeedback(receipt.capture.context, before); return false; }
        Quota quota = readQuota(p);
        if (quota == null || quota.hits >= InertialAnchorRules.MAX_HITS_PER_TICK
                || (double)(float)plan.reducedStrength() != plan.reducedStrength()
                || !Double.isFinite(plan.reducedStrength() * (1 - resistance)) || plan.reducedStrength() * (1 - resistance) <= 0) return false;
        receipt.plan = plan; receipt.quota = quota; receipt.resistance = resistance;
        receipt.velocity = p.getDeltaMovement(); receipt.grounded = p.onGround(); receipt.pose = p.getPose(); receipt.allowed = true;
        return true;
    }
    public static float reductionStrength(NativeReduction receipt) {
        return receipt != null && receipt.allowed && !receipt.claimed && !receipt.finished ? (float)receipt.plan.reducedStrength() : Float.NaN;
    }
    /** False means no publication. The claimed receipt is terminal even if the atomic debit refuses. */
    public static boolean commitNativeReduction(ServerPlayer p, NativeReduction receipt) {
        if (!reductionCurrent(p, receipt) || !receipt.allowed || receipt.claimed
                || HighPowerCombat.unsupportedReason(p).isPresent()
                || !receipt.velocity.equals(p.getDeltaMovement()) || receipt.grounded != p.onGround() || receipt.pose != p.getPose()
                || receipt.resistance != p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)
                || !Objects.equals(readQuota(p), receipt.quota)
                || !HighPowerCombat.exactStateSnapshot(p).filter(s -> receipt.plan.matches(s, (double)(float)receipt.strength)).isPresent()) return false;
        receipt.claimed = true;
        Runnable notifications = HighPowerCombat.commitAnchorKnockback(p, receipt.plan);
        if (notifications == null) return false;
        receipt.notifications = notifications; receipt.committed = true;
        stampQuota(p, new Quota(now(p), receipt.quota.hits + 1)); return true;
    }
    public static void finishNativeReduction(ServerPlayer p, NativeReduction receipt, boolean completed) {
        if (receipt == null || receipt.finished) return;
        Context c = receipt.capture.context;
        Runnable notifications = receipt.notifications; receipt.notifications = null;
        boolean current;
        try {
            current = c.identity.owner() == p && reductionCurrent(p, receipt);
        } finally {
            // Source/world validation can throw too. Retire authority before any deferred observer,
            // including that exception path, so a paid receipt never strands the stance as busy.
            receipt.finished = true; if (c.reducing == receipt) c.reducing = null;
            if (notifications != null) notifications.run();
        }
        if (current && receipt.committed && completed && owned(p) == c && valid(c, true))
            send(c, KNOCKBACK, receipt.capture.ownerPosition, receipt.capture.facing, receipt.capture.contact, receipt.grounded);
    }
    private static boolean reductionCurrent(ServerPlayer p, NativeReduction receipt) {
        return receipt != null && !receipt.finished && receipt.capture.context.reducing == receipt && currentCapture(p, receipt.capture);
    }

    /** The caller owns and claims the opaque native accepted-ground-contact receipt before this call. */
    static InertialAnchorRules.Result planFall(ServerPlayer p, ExactCombatState before, BigDecimal damage) {
        Context c = owned(p);
        if (c == null || !c.installed || busy(c) || !valid(c, true) || conflicts(p) || before == null || before.isDead()
                || damage == null || damage.signum() <= 0 || before.shield().signum() <= 0
                || HighPowerCombat.unsupportedReason(p).isPresent()
                || !HighPowerCombat.exactStateSnapshot(p).filter(before::equals).isPresent()) return null;
        Quota q = readQuota(p); if (q == null || q.hits >= InertialAnchorRules.MAX_HITS_PER_TICK) return null;
        c.planning = true;
        try {
            InertialAnchorRules.Result result = InertialAnchorRules.absorbFall(before, damage, EsperShields.eligible(p));
            c.fall = new FallPlan(result, q, now(p), p.position(), visualFacing(p)); return result;
        } finally { c.planning = false; }
    }
    /** Callback-free admission after publication; the returned optional cue runs after HP/death/recovery settlement. */
    static Runnable committedFall(ServerPlayer p, InertialAnchorRules.Result result, Vec3 contact) {
        Context c = owned(p); FallPlan fall = c == null ? null : c.fall;
        if (fall == null || result != fall.result || result.absorbed().signum() <= 0 || fall.tick != now(p)
                || !Objects.equals(readQuota(p), fall.quota)
                || !HighPowerCombat.exactStateSnapshot(p).filter(result.state()::equals).isPresent()) return () -> {};
        c.fall = null; stampQuota(p, new Quota(now(p), fall.quota.hits + 1));
        return new Runnable() {
            private boolean attempted;
            @Override public void run() {
                if (attempted) return;
                attempted = true;
                try {
                    if (owned(p) == c && valid(c, true) && finite(contact))
                        send(c, LANDED, fall.position, fall.facing, contact, true);
                } catch (RuntimeException | LinkageError failure) {
                    // Exact resources and admission are already terminal. A cosmetic failure cannot undo them.
                    LOG.warn("NEW422 landing cue unavailable after committed exact settlement", failure);
                }
            }
        };
    }

    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null) return;
        if (!structurallyValid(c, false) || !canAct(p) || conflicts(p) || available(p).signum() <= 0 || readQuota(p) == null) { finish(c); return; }
        if (c.paying || c.planning || c.capturing || c.reducing != null) return;
        long tick = now(p); if (tick <= c.lastTick) return; c.lastTick = tick;
        if (c.pending != null && c.pending.tick != tick) c.pending = null;
        if (c.fall != null && c.fall.tick != tick) c.fall = null;
        if (tick >= c.paidUntil && !pay(c, false)) { finish(c); return; }
        if (!valid(c, false)) { finish(c); return; }
        if ((tick - c.started) % 4 == 0) send(c, ACTIVE, p.position(), visualFacing(p), p.position(), p.onGround());
    }
    private static boolean pay(Context c, boolean activation) {
        ServerPlayer p = c.identity.owner(); long tick = now(p); Payment previous = readPayment(p);
        double cost = activation ? c.activation : InertialAnchorRules.sustainCost(HighPowerGrowth.values(p).position());
        if (c.paying || !structurallyValid(c, false) || previous == null || !activation && tick < previous.until
                || tick < previous.stopUntil || HighPowerResources.availableCp(p) < cost) return false;
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
            writePayment(p, new Payment(tick, tick + InertialAnchorRules.PAYMENT_PERIOD,
                    Math.max(previous.stopUntil, activation ? tick + InertialAnchorRules.RESTART_FENCE : 0), dimension));
            c.paidUntil = tick + InertialAnchorRules.PAYMENT_PERIOD;
            return owned(p) == c && structurallyValid(c, false);
        } finally { c.paying = false; }
    }
    private static Payment readPayment(ServerPlayer p) {
        long tick = now(p); if (!InertialAnchorRules.clock(tick)) return null;
        CompoundTag root = p.getPersistentData(); if (!root.contains(PAYMENT_KEY)) return new Payment(tick, 0, 0, "");
        if (!root.contains(PAYMENT_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag t = root.getCompound(PAYMENT_KEY);
        if (!t.getAllKeys().equals(Set.of("version", "observed", "until", "stopUntil", "dimension")) || !t.contains("version", Tag.TAG_INT) || t.getInt("version") != 1
                || !t.contains("observed", Tag.TAG_LONG) || !t.contains("until", Tag.TAG_LONG) || !t.contains("stopUntil", Tag.TAG_LONG) || !t.contains("dimension", Tag.TAG_STRING)
                || !InertialAnchorRules.clock(t.getLong("observed")) || t.getLong("observed") > tick || t.getLong("until") < 0
                || t.getLong("until") > t.getLong("observed") + InertialAnchorRules.PAYMENT_PERIOD || t.getLong("stopUntil") < 0
                || t.getLong("stopUntil") > t.getLong("observed") + InertialAnchorRules.PAYMENT_PERIOD || t.getString("dimension").length() > 256) return null;
        return new Payment(t.getLong("observed"), t.getLong("until"), t.getLong("stopUntil"), t.getString("dimension"));
    }
    private static void writePayment(ServerPlayer p, Payment f) {
        CompoundTag t = new CompoundTag(); t.putInt("version", 1); t.putLong("observed", f.observed); t.putLong("until", f.until);
        t.putLong("stopUntil", f.stopUntil); t.putString("dimension", f.dimension); p.getPersistentData().put(PAYMENT_KEY, t);
    }
    private static Quota readQuota(ServerPlayer p) {
        long tick = now(p); if (!InertialAnchorRules.clock(tick)) return null;
        CompoundTag root = p.getPersistentData(); if (!root.contains(QUOTA_KEY)) return new Quota(tick, 0);
        if (!root.contains(QUOTA_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag t = root.getCompound(QUOTA_KEY);
        if (!t.getAllKeys().equals(Set.of("version", "observed", "hits")) || !t.contains("version", Tag.TAG_INT) || t.getInt("version") != 1
                || !t.contains("observed", Tag.TAG_LONG) || !t.contains("hits", Tag.TAG_INT) || !InertialAnchorRules.clock(t.getLong("observed"))
                || t.getLong("observed") > tick || t.getInt("hits") < 0 || t.getInt("hits") > InertialAnchorRules.MAX_HITS_PER_TICK) return null;
        return new Quota(tick, t.getLong("observed") == tick ? t.getInt("hits") : 0);
    }
    private static void stampQuota(ServerPlayer p, Quota q) {
        CompoundTag t = new CompoundTag(); t.putInt("version", 1); t.putLong("observed", q.observed); t.putInt("hits", q.hits); p.getPersistentData().put(QUOTA_KEY, t);
    }


    /** Closed prototype source: plain exact Zombie base melee only, with no extra knockback leg. */
    private static Zombie reviewedSource(ServerPlayer p, DamageSource source) {
        if (source == null || source.getClass() != DamageSource.class || !source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(DamageTypeTags.BYPASSES_RESISTANCE) || source.is(DamageTypeTags.BYPASSES_EFFECTS)
                || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.BYPASSES_COOLDOWN)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypeTags.DAMAGES_HELMET)) return null;
        var resource = p.server.getResourceManager().getResource(ResourceLocation.withDefaultNamespace("damage_type/mob_attack.json"));
        if (resource.isEmpty() || !resource.get().sourcePackId().equals("vanilla")
                || !resource.get().knownPackInfo().equals(Optional.of(KnownPack.vanilla("core")))) return null;
        if (!(source.getEntity() instanceof Zombie attacker) || attacker.getClass() != Zombie.class || attacker.getType() != EntityType.ZOMBIE
                || source.getDirectEntity() != attacker || attacker.level() != p.level() || !attacker.isAlive() || attacker.isRemoved()
                || attacker.isSpectator() || !HighPowerCombat.supported(attacker) || attacker.getAttributeValue(Attributes.ATTACK_KNOCKBACK) != 0
                || p.serverLevel().getEntity(attacker.getId()) != attacker || p.serverLevel().getEntity(attacker.getUUID()) != attacker) return null;
        Vec3 origin = source.getSourcePosition();
        if (!finite(origin) || !origin.equals(attacker.position()) || origin.distanceToSqr(p.position()) > 36) return null;
        for (ItemStack stack : attacker.getAllSlots()) if (!plainEquipment(stack)) return null;
        return attacker;
    }
    /** Only canonical vanilla components plus the item's ordinary durability are admitted. */
    private static boolean plainEquipment(ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (stack.getCount() != 1 || !BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft")
                || !stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()) return false;
        ItemStack canonical = new ItemStack(stack.getItem());
        int damage = stack.getDamageValue();
        if (damage < 0 || damage > canonical.getMaxDamage()) return false;
        if (canonical.isDamageableItem()) canonical.setDamageValue(damage);
        return ItemStack.isSameItemSameComponents(stack, canonical);
    }

    private static boolean canAct(ServerPlayer p) { return basic(p, false) && HighPowerResources.ready(p) && HighPowerCombat.unsupportedReason(p).isEmpty(); }
    private static boolean basic(ServerPlayer p, boolean nativeFrame) {
        if (!thread(p) || p.connection == null || p.hasDisconnected() || p.isRemoved() || !nativeFrame && !p.isAlive()
                || p.isSpectator() || p.getAbilities().instabuild || p.getAbilities().invulnerable || p.noPhysics || p.isPassenger() || p.isVehicle()
                || p.isSleeping() || p.isFallFlying() || p.getAbilities().flying || p.containerMenu != p.inventoryMenu || AbilityConsumption.busy(p)) return false;
        var s = AbilityStorage.peek(p);
        return s != null && HighPowerCombat.enabled(p) && s.category.equals("vecmanip") && s.activated && s.overloadFine && !s.interfering
                && s.learned(ID) && s.learned(VectorParry.ID) && s.level >= 2 && s.level <= 5 && SkillCatalog.enabled("vecmanip", ID)
                && finite(p.position()) && finite(p.getLookAngle()) && finite(p.getDeltaMovement()) && p.getLookAngle().lengthSqr() > .5
                && Float.isFinite(p.getXRot()) && Float.isFinite(p.getYRot()) && InertialAnchorRules.clock(now(p));
    }
    private static boolean stableGrounded(ServerPlayer p) {
        return p.getPose() == Pose.STANDING && !p.isShiftKeyDown() && p.onGround()
                && finite(p.getDeltaMovement()) && Math.hypot(p.getDeltaMovement().x, p.getDeltaMovement().z) <= .05
                && SafeLandingProbeWorld.sourceRefusal(p).isEmpty();
    }
    private static boolean structurallyValid(Context c, boolean nativeFrame) {
        ServerPlayer p = c.identity.owner();
        return owned(p) == c && snapshotOwned(c) && basic(p, nativeFrame) && now(p) >= c.started
                && c.preset == c.identity.state().presets.current() && c.revision == c.identity.state().presets.revision()
                && ID.equals(c.identity.state().presets.currentSkill(c.input.slot()))
                && p.getAttribute(Attributes.MOVEMENT_SPEED) == c.movement && c.movement != null
                && c.movement.getModifier(MOVEMENT_MODIFIER) == (c.installed ? c.modifier : null);
    }
    private static boolean snapshotOwned(Context c) {
        ServerPlayer p = c.identity.owner();
        return p.serverLevel() == c.identity.level() && p.level() == c.identity.level() && p.getId() == c.input.actor()
                && AbilityStorage.peek(p) == c.identity.state() && c.identity.level().getEntity(p.getId()) == p && c.identity.level().getEntity(p.getUUID()) == p
                && LegacySingleKeyOwnerEpoch.matches(p, p.connection, c.identity.level(), c.identity.state(), c.identity.epoch());
    }
    private static boolean valid(Context c, boolean nativeFrame) {
        if (c.paying || !structurallyValid(c, nativeFrame) || now(c.identity.owner()) >= c.paidUntil) return false;
        Payment fence = readPayment(c.identity.owner()); return fence != null && fence.until == c.paidUntil && now(c.identity.owner()) >= fence.observed;
    }

    private static BigDecimal available(ServerPlayer p) { return HighPowerCombat.exactStateSnapshot(p).map(ExactCombatState::shield).orElse(BigDecimal.ZERO); }
    private static Context owned(ServerPlayer p) { if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null; }
    private static boolean thread(ServerPlayer p) { return p != null && p.server != null && p.server.isSameThread(); }
    private static long now(ServerPlayer p) { return MagneticDomain.serverTime(p.server); }
    private static Vec3 center(ServerPlayer p) { return p.position().add(0, 1, 0); }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z) && Math.abs(v.x) <= 30_000_001 && Math.abs(v.y) <= 30_000_001 && Math.abs(v.z) <= 30_000_001; }
    private static VectorParryRules.Point point(Vec3 v) { return v == null ? null : new VectorParryRules.Point(v.x, v.y, v.z); }
    /** Visual orientation only. Gameplay eligibility still uses the exact real horizontal look vector. */
    private static Vec3 visualFacing(ServerPlayer p) {
        double yaw = Math.toRadians(p.getYRot());
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }
    private static boolean feedback(ServerPlayer p, String key) {
        if (HighPowerCombat.unsupportedReason(p).isPresent()) key = "unsupported";
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.academy.highpower.inertial_anchor." + key), true);
        return false;
    }
    /** A refusal keeps the original native strength and never partially pays; notices are bounded. */
    private static void insufficientFeedback(Context c, ExactCombatState before) {
        BigDecimal fee;
        try { fee = InertialAnchorRules.knockbackFee(before.maxShield()); }
        catch (IllegalArgumentException unsupported) { return; }
        ServerPlayer p = c.identity.owner(); long tick = now(p);
        if (fee.signum() <= 0 || before.shield().compareTo(fee) >= 0 || c.lastInsufficientNotice >= 0
                && tick - c.lastInsufficientNotice < InertialAnchorRules.PAYMENT_PERIOD) return;
        c.lastInsufficientNotice = tick; feedback(p, "insufficient_shield");
    }
    private static void finish(Context c) {
        ServerPlayer p = c.identity.owner(); if (!CONTEXTS.remove(p.getUUID(), c)) return;
        c.pending = null; c.fall = null;
        // Only the exact instance installed by this activation belongs to us. Foreign same-ID modifiers survive.
        if (p.getAttribute(Attributes.MOVEMENT_SPEED) == c.movement && c.movement != null
                && c.movement.getModifier(MOVEMENT_MODIFIER) == c.modifier) c.movement.removeModifier(MOVEMENT_MODIFIER);
        c.installed = false;
        if (c.paidUntil > 0) {
            Payment fence = readPayment(p); long tick = now(p);
            if (fence != null) writePayment(p, new Payment(tick, fence.until, Math.max(fence.stopUntil, tick + c.cooldown), fence.dimension));
            c.identity.state().setCooldown(ID, c.cooldown);
        }
        send(c, ENDED, p.position(), visualFacing(p), p.position(), p.onGround()); c.audience.clear();
    }
    private static void send(Context c, int phase, Vec3 point, Vec3 facing, Vec3 contact, boolean grounded) {
        ServerPlayer p = c.identity.owner();
        if (c.sequence == Long.MAX_VALUE || !finite(point) || !unit(facing) || !finite(contact) || point.distanceToSqr(contact) > 9) return;
        c.audience.removeIf(v -> v.connection == null || v.hasDisconnected() || v.isRemoved() || v.serverLevel() != c.identity.level() || v.position().distanceToSqr(p.position()) > 48 * 48);
        for (ServerPlayer viewer : c.identity.level().players()) if (viewer.position().distanceToSqr(p.position()) <= 32 * 32) c.audience.add(viewer);
        c.audience.add(p);
        Visual packet = new Visual(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.identity.epoch(), ++c.sequence,
                phase, point, facing, contact, phase == ENDED ? 0 : remainingHits(p), grounded);
        for (ServerPlayer viewer : c.audience) if (viewer.connection != null && !viewer.hasDisconnected() && viewer.serverLevel() == c.identity.level()) PacketDistributor.sendToPlayer(viewer, packet);
    }
    private static boolean unit(Vec3 v) { return finite(v) && v.y == 0 && Math.abs(v.lengthSqr() - 1) <= 1e-9; }
    public record Visual(int entity, UUID owner, long input, long token, long epoch, long sequence, int phase,
                         Vec3 point, Vec3 facing, Vec3 contact, int remainingHits, boolean grounded) implements CustomPacketPayload {
        public Visual {
            if (entity < 0 || owner == null || input <= 0 || token <= 0 || epoch <= 0 || sequence <= 0 || phase < ACTIVE || phase > ENDED
                    || !finite(point) || !unit(facing) || !finite(contact) || point.distanceToSqr(contact) > 9
                    || remainingHits < 0 || remainingHits > InertialAnchorRules.MAX_HITS_PER_TICK
                    || phase == ENDED && remainingHits != 0 || phase == LANDED && !grounded)
                throw new IllegalArgumentException("Invalid bounded NEW422 visual");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_inertial_anchor"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.token); b.writeLong(v.epoch); b.writeLong(v.sequence); b.writeByte(v.phase);
            writeVec(b, v.point); writeVec(b, v.facing); writeVec(b, v.contact); b.writeByte(v.remainingHits); b.writeBoolean(v.grounded);
        }, b -> new Visual(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readUnsignedByte(),
                readVec(b), readVec(b), readVec(b), b.readUnsignedByte(), b.readBoolean()));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static void writeVec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 readVec(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) { e.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet)); }
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Pre e) { for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) tick(c.identity.owner()); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void death(LivingDeathEvent e) { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void leave(EntityLeaveLevelEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void clone(PlayerEvent.Clone e) {
        if (e.getOriginal() instanceof ServerPlayer old) {
            remove(old);
            if (e.getEntity() instanceof ServerPlayer replacement && old.getUUID().equals(replacement.getUUID()))
                for (String key : List.of(PAYMENT_KEY, QUOTA_KEY)) if (old.getPersistentData().contains(key)) replacement.getPersistentData().put(key, old.getPersistentData().get(key).copy());
        }
    }
    public static void clear() { for (Context c : new ArrayList<>(CONTEXTS.values())) finish(c); CONTEXTS.clear(); INPUTS.clear(); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) { for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) finish(c); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { clear(); }
}
