package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.ExactCombatState;
import cn.academy.port.highpower.core.ThermalShellRules;
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
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
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

/** NEW222. Paid, omnidirectional close shell over existing S; one deferred native pulse budget. */
@EventBusSubscriber(modid = "academy")
public final class ThermalShell {
    public static final String ID = "thermal_shell", PAYMENT_KEY = "academy:thermal_shell_payment",
            PULSE_KEY = "academy:thermal_shell_pulse", MASTERY_KEY = "academy:thermal_shell_mastery";
    public static final int ACTIVE = 0, HIT = 1, PULSE = 2, ENDED = 3;
    private static final String CLOCK = "next_" + ID;
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(long epoch, long input) {}
    private record Payment(long observed, long until, long stopUntil, String dimension) {}
    private record Rate(long observed, long until) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final int preset, cooldown;
        final long revision, started;
        final double activation;
        final Set<ServerPlayer> audience = new LinkedHashSet<>();
        long paidUntil, lastTick = -1, sequence;
        int pulses;
        boolean paying, processing, pulsePayment;
        Pending pending;
        EmissionPermit emitting;
        Context(ServerPlayer p, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision(); started = now(p);
            BigDecimal q = HighPowerGrowth.values(p).position();
            activation = ThermalShellRules.activationCost(q); cooldown = ThermalShellRules.cooldownTicks(q);
        }
    }
    /** Captures approved source provenance before observers; never reclassifies a moved attacker. */
    public static final class Capture {
        private final Context context;
        private final DamageSource source;
        private final Vec3 sourcePosition, ownerPosition, impact, facing;
        private final BigDecimal growthDamage;
        private final long tick;
        private boolean committed;
        private Capture(Context c, DamageSource source, Vec3 sourcePosition) {
            context = c; this.source = source; this.sourcePosition = sourcePosition;
            ServerPlayer p = c.identity.owner(); ownerPosition = p.position(); tick = now(p); facing = p.getLookAngle().normalize();
            Vec3 center = center(p), delta = sourcePosition.subtract(center);
            impact = center.add(delta.lengthSqr() > 1e-12 ? delta.normalize().scale(ThermalShellRules.SHELL_RADIUS) : Vec3.ZERO);
            growthDamage = HighPowerGrowth.values(p).damage();
        }
    }
    private record FrozenTarget(LivingEntity entity, int id, UUID uuid) {
        FrozenTarget(LivingEntity entity) { this(entity, entity.getId(), entity.getUUID()); }
        boolean same(ServerLevel level) { return entity.getId() == id && entity.getUUID().equals(uuid) && level.getEntity(id) == entity && level.getEntity(uuid) == entity; }
    }
    private record Pending(Context context, long tick, Vec3 ownerPosition, Vec3 origin, Vec3 facing,
                           List<FrozenTarget> targets, List<BigDecimal> shares) {}
    /** Only this controller constructs permits; no caller may supply an amount, skill or target. */
    public static final class EmissionPermit {
        private final Context context;
        private final Pending pending;
        private final FrozenTarget frozen;
        private final Vec3 ownerPosition, targetPosition, endpoint;
        private final BigDecimal damage;
        private final long tick;
        private boolean claimed;
        private EmissionPermit(Context c, Pending pending, FrozenTarget frozen, BigDecimal damage, Vec3 ownerPosition) {
            context = c; this.pending = pending; this.frozen = frozen; this.damage = damage;
            this.ownerPosition = ownerPosition; targetPosition = frozen.entity.position(); endpoint = frozen.entity.getBoundingBox().getCenter(); tick = now(player());
        }
        ServerPlayer player() { return context.identity.owner(); }
        LivingEntity target() { return frozen.entity; }
        Vec3 origin() { return pending.origin; }
        Vec3 endpoint() { return endpoint; }
        BigDecimal damage() { return damage; }
        boolean claim() { if (claimed || !valid()) return false; claimed = true; return true; }
        boolean valid() {
            ServerPlayer p = player();
            return context.emitting == this && context.processing && ThermalShell.valid(context, false) && now(p) == tick
                    && p.position().equals(ownerPosition) && withinDrift(p, pending)
                    && frozen.same(p.serverLevel()) && target().position().equals(targetPosition)
                    && target().getBoundingBox().getCenter().equals(endpoint) && eligible(p, target(), origin())
                    && clearLine(p, origin(), endpoint);
        }
    }
    private ThermalShell() {}

    public static boolean toggle(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input) || AbilityConsumption.busy(p)) return false;
        Context resolving = owned(p); if (resolving != null && (resolving.processing || resolving.paying)) return false;
        Watermark prior = INPUTS.get(p.getUUID());
        if (prior != null && prior.epoch == input.epoch() && prior.input >= input.input()) return false;
        INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        if (!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))) return false;
        Context old = CONTEXTS.get(p.getUUID());
        if (old != null) { boolean same = owned(p) == old && structurallyValid(old, false); finish(old); if (same) return true; }
        Payment fence = readPayment(p);
        if (!canAct(p) || nextToken == Long.MAX_VALUE || fence == null || now(p) < fence.stopUntil || !AbilityStorage.get(p).canUse(ID)) return false;
        Context c = new Context(p, input); CONTEXTS.put(p.getUUID(), c);
        if (!pay(c, true) || !valid(c, false)) { finish(c); return false; }
        send(c, ACTIVE, center(p)); return true;
    }
    public static boolean start(ServerPlayer p, String wire) { return toggle(p, wire); }
    public static boolean begin(ServerPlayer p, String wire) { return toggle(p, wire); }
    public static boolean abort(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire); if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Watermark old = INPUTS.get(p.getUUID());
        if (old == null || old.epoch != input.epoch() || old.input < input.input()) INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        Context c = owned(p);
        if (c == null || c.input.epoch() != input.epoch() || c.input.slot() != input.slot() || c.input.input() > input.input()) return false;
        finish(c); return true;
    }
    public static void abort(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c); }
    public static void remove(ServerPlayer p) { if (thread(p)) { abort(p); INPUTS.remove(p.getUUID()); } }
    public static boolean active(ServerPlayer p) { Context c = owned(p); return c != null && valid(c, false); }
    public static boolean blocksRecovery(ServerPlayer p) { Context c = owned(p); return c != null && c.paidUntil > 0 && structurallyValid(c, false); }
    public static boolean renewalPending(ServerPlayer p) { Context c = owned(p); return c != null && c.paidUntil > 0 && now(p) >= c.paidUntil && structurallyValid(c, false); }
    public static long paidUntil(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.paidUntil; }
    public static int pulseCount(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.pulses; }
    static boolean payingPulse(ServerPlayer p) { Context c = owned(p); return c != null && c.processing && c.pulsePayment && valid(c, false); }

    static Capture capture(ServerPlayer p, DamageSource source) {
        Context c = owned(p);
        if (c == null || !valid(c, false) || !reviewedMelee(p, source)) return null;
        return new Capture(c, source, source.getSourcePosition());
    }
    static boolean matches(ServerPlayer p, Capture capture) {
        return capture != null && !capture.committed && capture.source != null && finite(capture.sourcePosition)
                && owned(p) == capture.context && valid(capture.context, HighPowerCombat.thermalNativeFrame(p, capture))
                && now(p) == capture.tick && p.position().equals(capture.ownerPosition);
    }
    static ThermalShellRules.Result plan(ServerPlayer p, Capture capture, ExactCombatState before, BigDecimal mitigated) {
        return before == null || before.isDead() || !matches(p, capture) ? null : ThermalShellRules.absorb(before, mitigated);
    }
    /** Called only after the bridge commits HP, S, NEW212 share reconciliation and native history once. */
    static void committed(ServerPlayer p, Capture capture, ThermalShellRules.Result result) {
        if (result == null || result.absorbed().signum() <= 0 || !matches(p, capture)) return;
        Context c = capture.context; capture.committed = true;
        send(c, HIT, capture.impact);
        if (c.processing) return;
        c.processing = true;
        try {
            long tick = now(p);
            // Reserve immutable identities, exact total and durable cadence before any mastery callback.
            if (valid(c, false) && c.pending == null && rateReady(p, PULSE_KEY, tick, ThermalShellRules.PULSE_INTERVAL)) {
                List<FrozenTarget> targets = select(p, center(p), tick);
                BigDecimal total = ThermalShellRules.pulseBudget(capture.growthDamage, result.absorbed());
                if (!targets.isEmpty() && total.signum() > 0) {
                    stampRate(p, PULSE_KEY, tick, ThermalShellRules.PULSE_INTERVAL);
                    c.pending = new Pending(c, tick, capture.ownerPosition, center(p), capture.facing,
                            targets, ThermalShellRules.shares(total, targets.size()));
                }
            }
            if (structurallyValid(c, HighPowerCombat.thermalNativeFrame(p, capture))
                    && rateReady(p, MASTERY_KEY, tick, ThermalShellRules.MASTERY_INTERVAL)) {
                stampRate(p, MASTERY_KEY, tick, ThermalShellRules.MASTERY_INTERVAL);
                c.identity.state().addExperience(ID, .005); AbilityStorage.save(p);
            }
            if (owned(p) == c && !valid(c, HighPowerCombat.thermalNativeFrame(p, capture))) finish(c);
        } finally { c.processing = false; }
    }
    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null) return;
        if (!structurallyValid(c, false) || !canAct(p)) { finish(c); return; }
        long tick = now(p); if (tick <= c.lastTick) return; c.lastTick = tick;
        if (tick >= c.paidUntil && !pay(c, false)) { finish(c); return; }
        if (!valid(c, false)) { finish(c); return; }
        if ((tick - c.started) % 4 == 0) send(c, ACTIVE, center(p));
    }
    /** Server Post runs after native hurt unwinds; one-tick fallback allows no unbounded pending queue. */
    public static void drain(ServerPlayer p) {
        Context c = owned(p); if (c == null || c.pending == null || c.processing) return;
        Pending pending = c.pending; long tick = now(p);
        if (tick < pending.tick || HighPowerCombat.nativeHurtInProgress(p)) return;
        c.pending = null;
        if (!valid(c, false) || tick > pending.tick + 1 || !withinDrift(p, pending)) return;
        List<Integer> accepted = new ArrayList<>(ThermalShellRules.MAX_TARGETS);
        for (int i = 0; i < pending.targets.size(); i++) {
            FrozenTarget target = pending.targets.get(i);
            if (pending.shares.get(i).signum() > 0 && target.same(p.serverLevel()) && eligible(p, target.entity, pending.origin)
                    && rateReady(target.entity, PULSE_KEY, tick, ThermalShellRules.PULSE_INTERVAL)
                    && clearLine(p, pending.origin, target.entity.getBoundingBox().getCenter())) accepted.add(i);
        }
        // Rejected/empty opportunities spend no CP and never award pulse XP.
        if (accepted.isEmpty() || !valid(c, false)) return;
        Vec3 emissionPosition = p.position();
        c.processing = true; c.pulsePayment = true;
        try {
            if (!HighPowerResources.trySpendThermalShellPulse(p, ThermalShellRules.pulseCost(HighPowerGrowth.values(p).position()))) return;
            c.pulsePayment = false;
            for (int index : accepted) stampRate(pending.targets.get(index).entity, PULSE_KEY, tick, ThermalShellRules.PULSE_INTERVAL);
            c.pulses++; send(c, PULSE, pending.origin);
            for (int index : accepted) {
                if (!valid(c, false) || !p.position().equals(emissionPosition) || !withinDrift(p, pending)) break;
                FrozenTarget target = pending.targets.get(index);
                if (!target.same(p.serverLevel()) || !eligible(p, target.entity, pending.origin)
                        || !clearLine(p, pending.origin, target.entity.getBoundingBox().getCenter())) continue;
                EmissionPermit permit = new EmissionPermit(c, pending, target, pending.shares.get(index), emissionPosition);
                c.emitting = permit;
                try { HighPowerCombat.attackThermalShell(permit); }
                finally { c.emitting = null; }
            }
        } finally { c.pulsePayment = false; c.processing = false; }
        if (owned(p) == c && !valid(c, false)) finish(c);
    }
    private static boolean pay(Context c, boolean activation) {
        ServerPlayer p = c.identity.owner(); long tick = now(p); Payment previous = readPayment(p);
        double cost = activation ? c.activation : ThermalShellRules.sustainCost(HighPowerGrowth.values(p).position());
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
            writePayment(p, new Payment(tick, tick + ThermalShellRules.PAYMENT_PERIOD, previous.stopUntil, dimension));
            c.paidUntil = tick + ThermalShellRules.PAYMENT_PERIOD;
            return owned(p) == c && structurallyValid(c, false);
        } finally { c.paying = false; }
    }
    /** Preserve ordinary native knockback, while keeping the snapshotted emission origin local. */
    private static boolean withinDrift(ServerPlayer p, Pending pending) {
        return p.position().distanceToSqr(pending.ownerPosition) <= 1 && clearLine(p, center(p), pending.origin);
    }
    private static List<FrozenTarget> select(ServerPlayer p, Vec3 origin, long tick) {
        List<LivingEntity> scanned = new ArrayList<>(ThermalShellRules.SCAN_LIMIT);
        p.serverLevel().getEntities(EntityTypeTest.forClass(LivingEntity.class), new AABB(origin, origin).inflate(ThermalShellRules.RANGE),
                e -> true, scanned, ThermalShellRules.SCAN_LIMIT);
        scanned.removeIf(e -> !eligible(p, e, origin) || !rateReady(e, PULSE_KEY, tick, ThermalShellRules.PULSE_INTERVAL)
                || !clearLine(p, origin, e.getBoundingBox().getCenter()));
        scanned.sort(Comparator.<LivingEntity>comparingDouble(e -> e.getBoundingBox().getCenter().distanceToSqr(origin)).thenComparingInt(LivingEntity::getId));
        return scanned.subList(0, Math.min(scanned.size(), ThermalShellRules.MAX_TARGETS)).stream().map(FrozenTarget::new).toList();
    }
    private static boolean eligible(ServerPlayer p, LivingEntity e, Vec3 origin) {
        return HighPowerCombat.hostileTarget(e) && HighPowerCombat.supported(e) && e.level() == p.serverLevel() && e.isAlive() && !e.isRemoved()
                && !e.isSpectator() && !e.isPassenger() && !e.isVehicle() && !e.isInvulnerable() && !e.noPhysics
                && !p.isAlliedTo(e) && !e.isAlliedTo(p) && p.serverLevel().getEntity(e.getId()) == e && p.serverLevel().getEntity(e.getUUID()) == e
                && HighPowerCombat.unsupportedReason(e).isEmpty() && finite(e.position()) && finite(e.getDeltaMovement())
                && permittedVolume(p, e.getBoundingBox()) && ThermalShellRules.inside(point(e.getBoundingBox().getCenter()), point(origin));
    }
    private static boolean reviewedMelee(ServerPlayer p, DamageSource source) {
        if (source == null || source.getClass() != DamageSource.class || source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                || source.is(DamageTypeTags.BYPASSES_EFFECTS) || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.BYPASSES_COOLDOWN)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_FREEZING)
                || source.is(DamageTypeTags.DAMAGES_HELMET)) return false;
        String type;
        if (source.is(DamageTypes.PLAYER_ATTACK)) type = "player_attack";
        else if (source.is(DamageTypes.MOB_ATTACK)) type = "mob_attack";
        else if (source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) type = "mob_attack_no_aggro";
        else return false;
        var resource = p.server.getResourceManager().getResource(ResourceLocation.withDefaultNamespace("damage_type/" + type + ".json"));
        if (resource.isEmpty() || !resource.get().sourcePackId().equals("vanilla") || !resource.get().knownPackInfo().equals(Optional.of(KnownPack.vanilla("core")))) return false;
        if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == p || source.getDirectEntity() != attacker
                || !HighPowerCombat.supported(attacker) || attacker.level() != p.level() || !attacker.isAlive() || attacker.isRemoved()
                || type.equals("player_attack") && attacker.getClass() != ServerPlayer.class) return false;
        Vec3 origin = source.getSourcePosition();
        return finite(origin) && origin.equals(attacker.position()) && ThermalShellRules.inside(point(origin), point(p.position()))
                && clearLine(p, p.getEyePosition(), attacker.getBoundingBox().getCenter());
    }
    private static boolean canAct(ServerPlayer p) { return basic(p, false) && HighPowerResources.ready(p) && HighPowerResources.effectiveLevel(p) >= 2 && HighPowerCombat.unsupportedReason(p).isEmpty(); }
    private static boolean basic(ServerPlayer p, boolean nativeDamageFrame) {
        if (!thread(p) || p.connection == null || p.hasDisconnected() || p.isRemoved() || !nativeDamageFrame && !p.isAlive()
                || p.isSpectator() || p.getAbilities().instabuild || p.getAbilities().invulnerable || p.noPhysics || p.isPassenger()
                || p.isSleeping() || AbilityConsumption.busy(p)) return false;
        var s = AbilityStorage.peek(p);
        return s != null && HighPowerCombat.enabled(p) && s.category.equals("meltdowner") && s.activated && s.overloadFine && !s.interfering
                && s.learned(ID) && s.learned(ScatterPlates.ID) && s.level >= 2 && s.level <= 5 && SkillCatalog.enabled("meltdowner", ID)
                && finite(p.position()) && finite(p.getLookAngle()) && finite(p.getDeltaMovement())
                && Float.isFinite(p.getXRot()) && Float.isFinite(p.getYRot()) && ThermalShellRules.clock(now(p));
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
    private static boolean valid(Context c, boolean nativeDamageFrame) {
        if (c.paying || !structurallyValid(c, nativeDamageFrame) || now(c.identity.owner()) >= c.paidUntil) return false;
        Payment fence = readPayment(c.identity.owner()); return fence != null && fence.until == c.paidUntil && now(c.identity.owner()) >= fence.observed;
    }
    private static Payment readPayment(ServerPlayer p) {
        long tick = now(p); if (!ThermalShellRules.clock(tick)) return null;
        CompoundTag root = p.getPersistentData(); if (!root.contains(PAYMENT_KEY)) return new Payment(tick, 0, 0, "");
        if (!root.contains(PAYMENT_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag t = root.getCompound(PAYMENT_KEY);
        if (!t.getAllKeys().equals(Set.of("version", "observed", "until", "stopUntil", "dimension")) || !t.contains("version", Tag.TAG_INT) || t.getInt("version") != 1
                || !t.contains("observed", Tag.TAG_LONG) || !t.contains("until", Tag.TAG_LONG) || !t.contains("stopUntil", Tag.TAG_LONG) || !t.contains("dimension", Tag.TAG_STRING)
                || !ThermalShellRules.clock(t.getLong("observed")) || t.getLong("observed") > tick || t.getLong("until") < 0
                || t.getLong("until") > t.getLong("observed") + ThermalShellRules.PAYMENT_PERIOD || t.getLong("stopUntil") < 0
                || t.getLong("stopUntil") > t.getLong("observed") + ThermalShellRules.PAYMENT_PERIOD || t.getString("dimension").length() > 256) return null;
        return new Payment(t.getLong("observed"), t.getLong("until"), t.getLong("stopUntil"), t.getString("dimension"));
    }
    private static void writePayment(ServerPlayer p, Payment f) {
        CompoundTag t = new CompoundTag(); t.putInt("version", 1); t.putLong("observed", f.observed); t.putLong("until", f.until);
        t.putLong("stopUntil", f.stopUntil); t.putString("dimension", f.dimension); p.getPersistentData().put(PAYMENT_KEY, t);
    }
    private static boolean rateReady(LivingEntity e, String key, long tick, int period) {
        if (!ThermalShellRules.clock(tick)) return false;
        CompoundTag root = e.getPersistentData(); if (!root.contains(key)) return true;
        if (!root.contains(key, Tag.TAG_COMPOUND)) return false;
        CompoundTag t = root.getCompound(key);
        return t.getAllKeys().equals(Set.of("version", "observed", "until")) && t.contains("version", Tag.TAG_INT) && t.getInt("version") == 1
                && t.contains("observed", Tag.TAG_LONG) && t.contains("until", Tag.TAG_LONG) && ThermalShellRules.clock(t.getLong("observed"))
                && t.getLong("observed") <= tick && t.getLong("until") == t.getLong("observed") + period && tick >= t.getLong("until");
    }
    private static void stampRate(LivingEntity e, String key, long tick, int period) {
        CompoundTag t = new CompoundTag(); t.putInt("version", 1); t.putLong("observed", tick); t.putLong("until", tick + period); e.getPersistentData().put(key, t);
    }
    private static boolean permitted(ServerPlayer p, BlockPos pos) {
        ServerLevel w = p.serverLevel(); return !w.isOutsideBuildHeight(pos) && w.hasChunkAt(pos) && w.getWorldBorder().isWithinBounds(pos) && w.mayInteract(p, pos);
    }
    private static boolean permittedVolume(ServerPlayer p, AABB box) {
        if (!finite(new Vec3(box.minX, box.minY, box.minZ)) || !finite(new Vec3(box.maxX, box.maxY, box.maxZ))
                || box.getXsize() > 4 || box.getYsize() > 4 || box.getZsize() > 4) return false;
        for (int x = (int)Math.floor(box.minX); x <= Math.floor(box.maxX); x++)
            for (int y = (int)Math.floor(box.minY); y <= Math.floor(box.maxY); y++)
                for (int z = (int)Math.floor(box.minZ); z <= Math.floor(box.maxZ); z++) if (!permitted(p, new BlockPos(x, y, z))) return false;
        return true;
    }
    private static boolean clearLine(ServerPlayer p, Vec3 from, Vec3 to) {
        if (!finite(from) || !finite(to) || from.distanceToSqr(to) > 6 * 6) return false;
        for (int x = ((int)Math.floor(Math.min(from.x, to.x))) >> 4; x <= ((int)Math.floor(Math.max(from.x, to.x))) >> 4; x++)
            for (int z = ((int)Math.floor(Math.min(from.z, to.z))) >> 4; z <= ((int)Math.floor(Math.max(from.z, to.z))) >> 4; z++) if (!p.serverLevel().hasChunk(x, z)) return false;
        int samples = Math.max(1, (int)Math.ceil(from.distanceTo(to) * 2));
        for (int i = 0; i <= samples; i++) if (!permitted(p, BlockPos.containing(from.lerp(to, (double)i / samples)))) return false;
        return p.serverLevel().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }
    private static Context owned(ServerPlayer p) { if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null; }
    private static boolean thread(ServerPlayer p) { return p != null && p.server != null && p.server.isSameThread(); }
    private static long now(ServerPlayer p) { return MagneticDomain.serverTime(p.server); }
    private static Vec3 center(ServerPlayer p) { return p.position().add(0, 1, 0); }
    private static boolean finite(Vec3 p) { return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
    private static ThermalShellRules.Point point(Vec3 p) { return p == null ? null : new ThermalShellRules.Point(p.x, p.y, p.z); }
    private static void finish(Context c) {
        ServerPlayer p = c.identity.owner(); if (!CONTEXTS.remove(p.getUUID(), c)) return;
        c.pending = null; c.emitting = null;
        if (c.paidUntil > 0) {
            Payment fence = readPayment(p); long tick = now(p);
            if (fence != null) writePayment(p, new Payment(tick, fence.until, Math.max(fence.stopUntil, tick + c.cooldown), fence.dimension));
            c.identity.state().setCooldown(ID, c.cooldown);
        }
        send(c, ENDED, center(p)); c.audience.clear();
    }
    private static void send(Context c, int phase, Vec3 impact) {
        ServerPlayer p = c.identity.owner(); if (!finite(impact) || !finite(p.position()) || !finite(p.getLookAngle())) return;
        c.audience.removeIf(v -> v.connection == null || v.hasDisconnected() || v.isRemoved() || v.serverLevel() != c.identity.level() || v.position().distanceToSqr(p.position()) > 48 * 48);
        for (ServerPlayer viewer : c.identity.level().players()) if (viewer.position().distanceToSqr(p.position()) <= 32 * 32) c.audience.add(viewer);
        c.audience.add(p);
        Visual packet = new Visual(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.identity.epoch(), ++c.sequence,
                phase, p.position(), p.getLookAngle().normalize(), impact);
        for (ServerPlayer viewer : c.audience) if (viewer.connection != null && !viewer.hasDisconnected() && viewer.serverLevel() == c.identity.level()) PacketDistributor.sendToPlayer(viewer, packet);
    }
    public record Visual(int entity, UUID owner, long input, long token, long epoch, long sequence, int phase, Vec3 point, Vec3 facing, Vec3 impact) implements CustomPacketPayload {
        public Visual {
            if (entity < 0 || owner == null || input <= 0 || token <= 0 || epoch <= 0 || sequence <= 0 || phase < ACTIVE || phase > ENDED
                    || !finite(point) || !finite(facing) || !finite(impact) || facing.lengthSqr() < .5 || facing.lengthSqr() > 1.5
                    || point.add(0, 1, 0).distanceToSqr(impact) > 1.5 * 1.5) throw new IllegalArgumentException("Invalid bounded NEW222 visual");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_thermal_shell"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.token); b.writeLong(v.epoch); b.writeLong(v.sequence); b.writeByte(v.phase);
            writeVec(b, v.point); writeVec(b, v.facing); writeVec(b, v.impact);
        }, b -> new Visual(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readUnsignedByte(), readVec(b), readVec(b), readVec(b)));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static void writeVec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 readVec(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) { e.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet)); }
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Pre e) { for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) tick(c.identity.owner()); }
    @SubscribeEvent public static void afterTick(ServerTickEvent.Post e) { for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) drain(c.identity.owner()); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void death(LivingDeathEvent e) { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void leave(EntityLeaveLevelEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void clone(PlayerEvent.Clone e) {
        if (e.getOriginal() instanceof ServerPlayer old) {
            remove(old);
            if (e.getEntity() instanceof ServerPlayer replacement && old.getUUID().equals(replacement.getUUID()))
                for (String key : List.of(PAYMENT_KEY, PULSE_KEY, MASTERY_KEY)) if (old.getPersistentData().contains(key)) replacement.getPersistentData().put(key, old.getPersistentData().get(key).copy());
        }
    }
    public static void clear() { for (Context c : new ArrayList<>(CONTEXTS.values())) finish(c); CONTEXTS.clear(); INPUTS.clear(); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) { for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) finish(c); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { clear(); }
}
