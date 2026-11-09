package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.ExactCombatState;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
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

/** NEW412: held frontal base-S absorption and one correlated native horizontal turn. */
@EventBusSubscriber(modid = "academy")
public final class VectorParry {
    public static final String ID = "vector_parry", PAYMENT_KEY = "academy:vector_parry_payment", QUOTA_KEY = "academy:vector_parry_quota";
    public static final int ACTIVE = 0, ABSORBED = 1, TURNED = 2, ENDED = 3;
    private static final String CLOCK = "next_" + ID;
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(long epoch, long input) {}
    private record Payment(long observed, long until, long stopUntil, String dimension) {}
    private record Quota(long observed, int hits) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final int preset, cooldown;
        final long revision, started;
        final double activation;
        final boolean crouching;
        final Set<ServerPlayer> audience = new LinkedHashSet<>();
        long paidUntil, lastTick = -1, sequence;
        boolean paying, planning, capturing, precisionUsed;
        Capture pending;
        NativeTurn turning;
        Context(ServerPlayer p, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            started = now(p); crouching = p.isShiftKeyDown();
            BigDecimal q = HighPowerGrowth.values(p).position();
            activation = VectorParryRules.activationCost(q); cooldown = VectorParryRules.cooldownTicks(q);
        }
    }
    /** Opaque immutable provenance/pose; only a current exact hurt frame can settle it. */
    public static final class Capture {
        private final Context context;
        private final DamageSource source;
        private final Entity direct;
        private final LivingEntity attacker;
        private final int directId, attackerId;
        private final UUID directUuid, attackerUuid;
        private final Vec3 sourcePosition, ownerPosition, facing, impact, incoming;
        private final long tick;
        private final boolean preciseSource;
        private boolean committed, claimed;
        private VectorParryRules.Result planned;
        private Quota quota;
        private Capture(Context c, DamageSource source, LivingEntity attacker, Vec3 sourcePosition) {
            context = c; this.source = source; this.attacker = attacker; direct = source.getDirectEntity();
            directId = direct.getId(); directUuid = direct.getUUID(); attackerId = attacker.getId(); attackerUuid = attacker.getUUID();
            this.sourcePosition = sourcePosition;
            ServerPlayer p = c.identity.owner(); ownerPosition = p.position(); facing = p.getLookAngle().normalize(); tick = now(p);
            Vec3 delta = sourcePosition.subtract(center(p));
            incoming = delta.lengthSqr() > 1e-12 ? delta.normalize().scale(-1) : Vec3.ZERO;
            impact = center(p).add(delta.lengthSqr() > 1e-12 ? delta.normalize() : Vec3.ZERO);
            preciseSource = preciseSource(p, source, attacker);
        }
    }
    /** Sole native call receipt. No caller can invent an amount, source, facing or turn. */
    public static final class NativeTurn {
        private final Capture capture;
        private final double strength, originalX, originalZ;
        private final Vec3 ratios, direction;
        private double effectiveStrength, resistance;
        private boolean grounded, allowed, committed, finished;
        private Vec3 before;
        private NativeTurn(Capture capture, double strength, double x, double z) {
            this.capture = capture; this.strength = strength; originalX = x; originalZ = z;
            var ratio = VectorParryRules.knockbackRatios(point(capture.facing), capture.context.crouching);
            ratios = new Vec3(ratio.x(), 0, ratio.z()); direction = ratios.scale(-1);
        }
    }
    private VectorParry() {}

    public static boolean start(ServerPlayer p, String wire) {
        if (InertialAnchor.active(p) || (CirculationRelief.active(p) || AutonomousVectorCircle.active(p)) || (MultiAxisReflection.active(p) || WindPressureLift.active(p))) return false;
        Context resolving = owned(p);
        if (resolving != null && (resolving.paying || resolving.planning || resolving.capturing || resolving.turning != null)) return false;
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input) || AbilityConsumption.busy(p)) return false;
        Watermark previous = INPUTS.get(p.getUUID());
        if (previous != null && previous.epoch == input.epoch() && previous.input >= input.input()) return false;
        INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        if (!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))) return false;
        Context old = CONTEXTS.get(p.getUUID()); if (old != null) finish(old);
        Payment fence = readPayment(p);
        if (!canAct(p) || nextToken == Long.MAX_VALUE || fence == null || readQuota(p) == null || now(p) < fence.stopUntil
                || !AbilityStorage.get(p).canUse(ID) || available(p).signum() <= 0) return false;
        Context c = new Context(p, input); CONTEXTS.put(p.getUUID(), c);
        if (!pay(c, true) || !valid(c, false)) { finish(c); return false; }
        send(c, ACTIVE, p.position(), p.getLookAngle().normalize(), center(p), Vec3.ZERO, Vec3.ZERO); return true;
    }
    /** Release before start is remembered, and an old release cannot close a newer hold. */
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
        Watermark previous = INPUTS.get(p.getUUID());
        if (previous == null || previous.epoch != input.epoch() || previous.input < input.input())
            INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
    }
    public static void abort(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c); }
    public static void remove(ServerPlayer p) { if (thread(p)) { abort(p); INPUTS.remove(p.getUUID()); } }
    public static boolean active(ServerPlayer p) { Context c = owned(p); return c != null && valid(c, false); }
    public static boolean holding(ServerPlayer p) { return active(p); }
    public static boolean blocksRecovery(ServerPlayer p) { Context c = owned(p); return c != null && c.paidUntil > 0 && structurallyValid(c, false); }
    public static boolean renewalPending(ServerPlayer p) { Context c = owned(p); return c != null && c.paidUntil > 0 && now(p) >= c.paidUntil && structurallyValid(c, false); }
    public static long paidUntil(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.paidUntil; }
    public static int remainingHits(ServerPlayer p) { Quota q = thread(p) ? readQuota(p) : null; return q == null ? 0 : VectorParryRules.MAX_HITS_PER_TICK - q.hits; }
    public static boolean precisionAvailable(ServerPlayer p) {
        Context c = owned(p); return c != null && valid(c, false) && !c.precisionUsed && VectorParryRules.precise(c.started, now(p)) && VectorParryNativeBridge.available(p);
    }

    /** Identity only: remaining admission may change during this same real hurt. */
    static Object arrowGeneration(ServerPlayer p) { return owned(p); }
    static boolean pendingArrowAllowed(ServerPlayer p, Arrow arrow, LivingEntity shooter, Vec3 from) {
        Context c = owned(p);
        if (c == null || c.capturing || c.planning || c.turning != null || !valid(c, false) || remainingHits(p) <= 0
                || available(p).signum() <= 0 || !AutonomousVectorArrowReceipts.ordinaryArrowType(p) || !finite(from) || !from.equals(arrow.position())
                || !plainArrow(p, arrow, shooter) || !VectorParryRules.front(point(p.position()), point(p.getLookAngle()), point(from))) return false;
        var resource = p.server.getResourceManager().getResource(ResourceLocation.withDefaultNamespace("damage_type/arrow.json"));
        return resource.isPresent() && resource.get().sourcePackId().equals("vanilla")
                && resource.get().knownPackInfo().equals(Optional.of(KnownPack.vanilla("core")))
                && owned(p) == c && !c.capturing && !c.planning && c.turning == null && valid(c, false)
                && remainingHits(p) > 0 && available(p).signum() > 0;
    }
    static Capture capture(ServerPlayer p, DamageSource source) {
        Context c = owned(p);
        if (c == null || c.capturing || c.planning || c.turning != null || !valid(c, false) || remainingHits(p) == 0) return null;
        c.capturing = true;
        try {
            LivingEntity attacker = reviewedSource(p, source);
            if (attacker == null) return null;
            // This one snapshot precedes settlement observers; later attacker/look movement cannot reclassify it.
            Vec3 origin = source.getSourcePosition();
            if (!finite(origin) || !VectorParryRules.front(point(p.position()), point(p.getLookAngle()), point(origin))) return null;
            return valid(c, false) ? new Capture(c, source, attacker, origin) : null;
        } finally { c.capturing = false; }
    }
    static boolean matches(ServerPlayer p, Capture capture) {
        return capture != null && !capture.committed && currentCapture(p, capture)
                && !capture.context.planning && remainingHits(p) > 0;
    }
    private static boolean currentCapture(ServerPlayer p, Capture capture) {
        return capture != null && capture.source != null && owned(p) == capture.context
                && HighPowerCombat.vectorParryNativeFrame(p, capture) && valid(capture.context, true)
                && now(p) == capture.tick && p.position().equals(capture.ownerPosition)
                && finite(capture.sourcePosition) && identity(capture, p.serverLevel())
                // Provenance must remain approved; facing/front eligibility remains the frozen snapshot.
                && reviewedSource(p, capture.source) == capture.attacker;
    }
    static VectorParryRules.Result plan(ServerPlayer p, Capture capture, ExactCombatState before, BigDecimal mitigated) {
        if (before == null || before.isDead() || mitigated == null || mitigated.signum() <= 0 || before.shield().signum() <= 0
                || !matches(p, capture) || !HighPowerCombat.exactStateSnapshot(p).filter(before::equals).isPresent()) return null;
        Quota q = readQuota(p); if (q == null || q.hits >= VectorParryRules.MAX_HITS_PER_TICK) return null;
        Context c = capture.context; c.planning = true;
        try {
            capture.quota = q;
            // Result already includes the residual HP pass; no caller may apply it again.
            capture.planned = VectorParryRules.absorb(before, mitigated, EsperShields.eligible(p)); return capture.planned;
        } finally { c.planning = false; }
    }
    /** Absorption and admission commit independently of whether native knockback later occurs. */
    static void committed(ServerPlayer p, Capture capture, VectorParryRules.Result result) {
        if (result == null || capture == null || result != capture.planned || result.absorbed().signum() <= 0 || !matches(p, capture)
                || !HighPowerCombat.exactStateSnapshot(p).filter(result.state()::equals).isPresent()
                || !Objects.equals(readQuota(p), capture.quota)) return;
        stampQuota(p, new Quota(now(p), capture.quota.hits + 1)); capture.committed = true;
        Context c = capture.context;
        if (!c.precisionUsed && capture.preciseSource && VectorParryRules.precise(c.started, capture.tick)) c.pending = capture;
        send(c, ABSORBED, capture.ownerPosition, capture.facing, capture.impact, capture.incoming, Vec3.ZERO);
    }

    /** Only the same hurt frame's base impulse is adapted. Arrow.doKnockback is never intercepted. */
    public static NativeTurn beginNativeTurn(LivingEntity entity, double strength, double x, double z) {
        if (!(entity instanceof ServerPlayer p) || !VectorParryNativeBridge.available(p)) return null;
        Context c = owned(p); Capture capture = c == null ? null : c.pending;
        if (capture == null || !capture.committed || capture.claimed || c.turning != null || c.precisionUsed
                || !VectorParryRules.precise(c.started, now(p)) || !currentCapture(p, capture)
                || !capture.preciseSource || !preciseSource(p, capture.source, capture.attacker)
                || strength != (double).4F || !Double.isFinite(x) || !Double.isFinite(z)
                || VectorParryRules.knockbackRatios(point(capture.facing), c.crouching) == null) return null;
        capture.claimed = true; c.pending = null;
        NativeTurn turn = new NativeTurn(capture, strength, x, z); c.turning = turn; return turn;
    }
    public static boolean allowNativeTurn(NativeTurn turn, LivingEntity entity, double strength, double x, double z) {
        if (!(entity instanceof ServerPlayer p) || !turnCurrent(turn, p) || turn.allowed || turn.committed
                || strength != (double)(float)turn.strength || x != turn.originalX || z != turn.originalZ
                || !preciseSource(p, turn.capture.source, turn.capture.attacker)) return false;
        double resistance = p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (!Double.isFinite(resistance) || resistance < 0 || resistance >= 1 || !finite(p.getDeltaMovement())) return false;
        turn.resistance = resistance; turn.effectiveStrength = strength * (1 - resistance);
        turn.grounded = p.onGround(); turn.before = p.getDeltaMovement(); turn.allowed = true; return true;
    }
    public static Vec3 nativeTurnRatio(NativeTurn turn) {
        return turn != null && turn.allowed && !turn.committed && turnCurrent(turn, turn.capture.context.identity.owner()) ? turn.ratios : null;
    }
    public static void commitNativeTurn(NativeTurn turn) {
        ServerPlayer p = turn == null ? null : turn.capture.context.identity.owner();
        if (p == null || !turn.allowed || turn.committed || !turnCurrent(turn, p)
                || !turn.before.equals(p.getDeltaMovement()) || turn.grounded != p.onGround()
                || turn.resistance != p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE))
            throw new IllegalStateException("Stale NEW412 native turn");
        turn.committed = true; turn.capture.context.precisionUsed = true;
    }
    public static void finishNativeTurn(NativeTurn turn, LivingEntity entity, Vec3 before, Vec3 after, boolean applied, boolean completed) {
        if (turn == null || turn.finished) return;
        Context c = turn.capture.context;
        boolean current = entity == c.identity.owner() && turnCurrent(turn, c.identity.owner());
        turn.finished = true; if (c.turning == turn) c.turning = null;
        if (!current || !turn.committed || !applied || !completed || !finite(before) || !finite(after) || !before.equals(turn.before)) return;
        Vec3 impulse = turn.ratios.normalize().scale(turn.effectiveStrength);
        Vec3 expected = new Vec3(before.x / 2 - impulse.x, turn.grounded ? Math.min(.4D, before.y / 2 + turn.effectiveStrength) : before.y,
                before.z / 2 - impulse.z);
        // Evidence is the actual native result, including its untouched vertical/resistance behavior.
        if (!expected.equals(after) || before.x == after.x && before.z == after.z) return;
        Capture capture = turn.capture;
        send(c, TURNED, capture.ownerPosition, capture.facing, capture.impact, capture.incoming, turn.direction);
    }
    private static boolean turnCurrent(NativeTurn turn, ServerPlayer p) {
        return turn != null && !turn.finished && turn.capture.context.turning == turn
                && (!turn.capture.context.precisionUsed || turn.committed) && currentCapture(p, turn.capture)
                && VectorParryRules.precise(turn.capture.context.started, now(p));
    }

    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null) return;
        if (!structurallyValid(c, false) || !canAct(p) || available(p).signum() <= 0 || readQuota(p) == null) { finish(c); return; }
        long tick = now(p); if (tick <= c.lastTick) return; c.lastTick = tick;
        if (c.pending != null && c.pending.tick != tick) c.pending = null;
        if (tick >= c.paidUntil && !pay(c, false)) { finish(c); return; }
        if (!valid(c, false)) { finish(c); return; }
        if ((tick - c.started) % 4 == 0) send(c, ACTIVE, p.position(), p.getLookAngle().normalize(), center(p), Vec3.ZERO, Vec3.ZERO);
    }
    private static boolean pay(Context c, boolean activation) {
        ServerPlayer p = c.identity.owner(); long tick = now(p); Payment previous = readPayment(p);
        double cost = activation ? c.activation : VectorParryRules.sustainCost(HighPowerGrowth.values(p).position());
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
            writePayment(p, new Payment(tick, tick + VectorParryRules.PAYMENT_PERIOD,
                    Math.max(previous.stopUntil, activation ? tick + VectorParryRules.RESTART_FENCE : 0), dimension));
            c.paidUntil = tick + VectorParryRules.PAYMENT_PERIOD;
            return owned(p) == c && structurallyValid(c, false);
        } finally { c.paying = false; }
    }
    private static Payment readPayment(ServerPlayer p) {
        long tick = now(p); if (!VectorParryRules.clock(tick)) return null;
        CompoundTag root = p.getPersistentData(); if (!root.contains(PAYMENT_KEY)) return new Payment(tick, 0, 0, "");
        if (!root.contains(PAYMENT_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag t = root.getCompound(PAYMENT_KEY);
        if (!t.getAllKeys().equals(Set.of("version", "observed", "until", "stopUntil", "dimension")) || !t.contains("version", Tag.TAG_INT) || t.getInt("version") != 1
                || !t.contains("observed", Tag.TAG_LONG) || !t.contains("until", Tag.TAG_LONG) || !t.contains("stopUntil", Tag.TAG_LONG) || !t.contains("dimension", Tag.TAG_STRING)
                || !VectorParryRules.clock(t.getLong("observed")) || t.getLong("observed") > tick || t.getLong("until") < 0
                || t.getLong("until") > t.getLong("observed") + VectorParryRules.PAYMENT_PERIOD || t.getLong("stopUntil") < 0
                || t.getLong("stopUntil") > t.getLong("observed") + VectorParryRules.PAYMENT_PERIOD || t.getString("dimension").length() > 256) return null;
        return new Payment(t.getLong("observed"), t.getLong("until"), t.getLong("stopUntil"), t.getString("dimension"));
    }
    private static void writePayment(ServerPlayer p, Payment f) {
        CompoundTag t = new CompoundTag(); t.putInt("version", 1); t.putLong("observed", f.observed); t.putLong("until", f.until);
        t.putLong("stopUntil", f.stopUntil); t.putString("dimension", f.dimension); p.getPersistentData().put(PAYMENT_KEY, t);
    }
    private static Quota readQuota(ServerPlayer p) {
        long tick = now(p); if (!VectorParryRules.clock(tick)) return null;
        CompoundTag root = p.getPersistentData(); if (!root.contains(QUOTA_KEY)) return new Quota(tick, 0);
        if (!root.contains(QUOTA_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag t = root.getCompound(QUOTA_KEY);
        if (!t.getAllKeys().equals(Set.of("version", "observed", "hits")) || !t.contains("version", Tag.TAG_INT) || t.getInt("version") != 1
                || !t.contains("observed", Tag.TAG_LONG) || !t.contains("hits", Tag.TAG_INT) || !VectorParryRules.clock(t.getLong("observed"))
                || t.getLong("observed") > tick || t.getInt("hits") < 0 || t.getInt("hits") > VectorParryRules.MAX_HITS_PER_TICK) return null;
        return new Quota(tick, t.getLong("observed") == tick ? t.getInt("hits") : 0);
    }
    private static void stampQuota(ServerPlayer p, Quota q) {
        CompoundTag t = new CompoundTag(); t.putInt("version", 1); t.putLong("observed", q.observed); t.putInt("hits", q.hits); p.getPersistentData().put(QUOTA_KEY, t);
    }

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
        if (resource.isEmpty() || !resource.get().sourcePackId().equals("vanilla") || !resource.get().knownPackInfo().equals(Optional.of(KnownPack.vanilla("core")))) return null;
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == p || !HighPowerCombat.supported(attacker)
                || attacker.level() != p.level() || !attacker.isAlive() || attacker.isRemoved() || attacker.isSpectator()
                || p.serverLevel().getEntity(attacker.getId()) != attacker || p.serverLevel().getEntity(attacker.getUUID()) != attacker) return null;
        Vec3 origin = source.getSourcePosition(); if (!finite(origin)) return null;
        if (type.equals("arrow")) return source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() instanceof Arrow arrow
                && plainArrow(p, arrow, attacker) && origin.equals(arrow.position()) ? attacker : null;
        return !source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() == attacker && origin.equals(attacker.position())
                && origin.distanceToSqr(p.position()) <= 36 && (!type.equals("player_attack") || attacker.getClass() == ServerPlayer.class) ? attacker : null;
    }
    private static boolean preciseSource(ServerPlayer p, DamageSource source, LivingEntity attacker) {
        if (source.getDirectEntity() instanceof Arrow arrow) return source.is(DamageTypes.ARROW) && plainArrow(p, arrow, attacker);
        if (attacker.getClass() != Zombie.class || attacker.getType() != EntityType.ZOMBIE || source.getDirectEntity() != attacker
                || !source.is(DamageTypes.MOB_ATTACK) || attacker.getAttributeValue(Attributes.ATTACK_KNOCKBACK) != 0) return false;
        for (ItemStack stack : attacker.getAllSlots()) if (!plainEquipment(stack)) return false;
        return true;
    }
    private static boolean plainArrow(ServerPlayer p, Arrow arrow, LivingEntity attacker) {
        if (arrow.getClass() != Arrow.class || arrow.getType() != EntityType.ARROW || arrow.level() != p.level()
                || arrow.isRemoved() || !arrow.isAlive() || p.serverLevel().getEntity(arrow.getId()) != arrow || p.serverLevel().getEntity(arrow.getUUID()) != arrow
                || !(arrow instanceof NativeScatterArrowOwnerAccess access) || access.academy$scatterCachedOwner() != attacker
                || !attacker.getUUID().equals(access.academy$scatterOwnerUuid()) || arrow.inGround || arrow.isNoPhysics() || arrow.noPhysics
                || arrow.isNoGravity() || arrow.isPassenger() || arrow.isVehicle() || arrow.isCritArrow() || arrow.isOnFire() || arrow.getPierceLevel() != 0
                || !finite(arrow.position()) || !finite(arrow.getDeltaMovement()) || arrow.getDeltaMovement().lengthSqr() <= 0 || arrow.getDeltaMovement().lengthSqr() > 64 * 64
                || !Double.isFinite(arrow.getBaseDamage()) || arrow.getBaseDamage() <= 0 || arrow.getBaseDamage() > 64
                || MultiAxisReflectionArrowBridge.leased(arrow) || TrajectoryReturnArrowBridge.leased(arrow) || ShortInterceptionArrowBridge.leased(arrow) || MagneticDomainArrows.leased(arrow) || MagneticArrowSeparation.leased(arrow)
                || IronSandLance.leased(arrow) || ElectromasterPrototype.leased(arrow) || ElectromasterChargeDecoy.leased(arrow) || MagneticGroundStep.leased(arrow)) return false;
        ItemStack stack = arrow.getPickupItemStackOrigin(), weapon = arrow.getWeaponItem();
        // Custom arrow components are not ordinary kinetic ammunition. No Punch means the later
        // Arrow.doKnockback leg contributes zero; it is deliberately outside the optional seam.
        return stack.getCount() == 1 && ItemStack.isSameItemSameComponents(stack, new ItemStack(Items.ARROW))
                && (weapon == null || !weapon.isEmpty() && weapon.getCount() == 1 && (weapon.is(Items.BOW) || weapon.is(Items.CROSSBOW))
                    && plainEquipment(weapon));
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
                || p.isSpectator() || p.getAbilities().instabuild || p.getAbilities().invulnerable || p.noPhysics || p.isPassenger()
                || p.isSleeping() || p.containerMenu != p.inventoryMenu || AbilityConsumption.busy(p)) return false;
        var s = AbilityStorage.peek(p);
        return s != null && HighPowerCombat.enabled(p) && s.category.equals("vecmanip") && s.activated && s.overloadFine && !s.interfering
                && s.learned(ID) && s.learned(DeflectionStrike.ID) && s.level >= 1 && s.level <= 5 && SkillCatalog.enabled("vecmanip", ID)
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
    private static void finish(Context c) {
        ServerPlayer p = c.identity.owner(); if (!CONTEXTS.remove(p.getUUID(), c)) return;
        AutonomousVectorArrowReceipts.invalidate(p);
        c.pending = null; if (c.turning != null) c.turning.finished = true; c.turning = null;
        if (c.paidUntil > 0) {
            Payment fence = readPayment(p); long tick = now(p);
            if (fence != null) writePayment(p, new Payment(tick, fence.until, Math.max(fence.stopUntil, tick + c.cooldown), fence.dimension));
            c.identity.state().setCooldown(ID, c.cooldown);
        }
        send(c, ENDED, p.position(), p.getLookAngle().normalize(), center(p), Vec3.ZERO, Vec3.ZERO); c.audience.clear();
    }
    private static void send(Context c, int phase, Vec3 point, Vec3 facing, Vec3 impact, Vec3 incoming, Vec3 turn) {
        ServerPlayer p = c.identity.owner();
        if (c.sequence == Long.MAX_VALUE || !finite(point) || !unit(facing) || !finite(impact) || !finite(incoming) || !finite(turn)) return;
        c.audience.removeIf(v -> v.connection == null || v.hasDisconnected() || v.isRemoved() || v.serverLevel() != c.identity.level() || v.position().distanceToSqr(p.position()) > 48 * 48);
        for (ServerPlayer viewer : c.identity.level().players()) if (viewer.position().distanceToSqr(p.position()) <= 32 * 32) c.audience.add(viewer);
        c.audience.add(p);
        int precision = phase == ENDED || c.precisionUsed || !VectorParryNativeBridge.available(p) || !VectorParryRules.precise(c.started, now(p)) ? 0
                : VectorParryRules.PRECISION_TICKS - (int)(now(p) - c.started);
        Visual packet = new Visual(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.identity.epoch(), ++c.sequence,
                phase, point, facing, impact, incoming, turn, precision, phase == ENDED ? 0 : remainingHits(p));
        for (ServerPlayer viewer : c.audience) if (viewer.connection != null && !viewer.hasDisconnected() && viewer.serverLevel() == c.identity.level()) PacketDistributor.sendToPlayer(viewer, packet);
    }
    private static boolean unit(Vec3 v) { return finite(v) && Math.abs(v.lengthSqr() - 1) <= 1e-9; }
    private static boolean zero(Vec3 v) { return finite(v) && v.x == 0 && v.y == 0 && v.z == 0; }
    public record Visual(int entity, UUID owner, long input, long token, long epoch, long sequence, int phase,
                         Vec3 point, Vec3 facing, Vec3 impact, Vec3 incoming, Vec3 turn, int preciseTicks, int remainingHits) implements CustomPacketPayload {
        public Visual {
            if (entity < 0 || owner == null || input <= 0 || token <= 0 || epoch <= 0 || sequence <= 0 || phase < ACTIVE || phase > ENDED
                    || !finite(point) || !unit(facing) || !finite(impact) || point.add(0, 1, 0).distanceToSqr(impact) > 1.5 * 1.5
                    || !zero(incoming) && !unit(incoming) || phase == TURNED && (!unit(turn) || turn.y != 0) || phase != TURNED && !zero(turn)
                    || (phase == ACTIVE || phase == ENDED) && !zero(incoming)
                    || preciseTicks < 0 || preciseTicks > VectorParryRules.PRECISION_TICKS || remainingHits < 0 || remainingHits > VectorParryRules.MAX_HITS_PER_TICK
                    || phase == ENDED && (preciseTicks != 0 || remainingHits != 0) || phase == TURNED && preciseTicks != 0)
                throw new IllegalArgumentException("Invalid bounded NEW412 visual");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_vector_parry"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.token); b.writeLong(v.epoch); b.writeLong(v.sequence); b.writeByte(v.phase);
            writeVec(b, v.point); writeVec(b, v.facing); writeVec(b, v.impact); writeVec(b, v.incoming); writeVec(b, v.turn); b.writeByte(v.preciseTicks); b.writeByte(v.remainingHits);
        }, b -> new Visual(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readUnsignedByte(),
                readVec(b), readVec(b), readVec(b), readVec(b), readVec(b), b.readUnsignedByte(), b.readUnsignedByte()));
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
