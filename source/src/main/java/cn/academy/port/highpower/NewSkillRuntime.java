package cn.academy.port.highpower;

import cn.academy.port.core.SkillTuning;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.IncidentBypassRules;
import cn.academy.port.highpower.core.ImpulseSplitRules;
import cn.academy.port.highpower.core.NewSkillBudget;
import cn.academy.port.highpower.core.PhaseCorridorRules;
import cn.academy.port.highpower.core.PulseShieldRules;
import cn.academy.port.highpower.core.RailRelayRules;
import cn.academy.port.highpower.core.ReverseJetRules;
import cn.academy.port.highpower.core.V3;
import cn.academy.port.highpower.core.ZenithDominionRules;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Server runtime for the seven skills added after 0.81.2: pulse shield (143), rail relay (153), reverse jet brake (243),
 * incident bypass (332), phase corridor (352), zenith vector dominion (451) and impulse split (453). The numeric and abuse
 * limits live in the pure {@code core} rules; this class only authenticates input, pays through the shared CP ledger,
 * mutates the world on the server thread and tells nearby clients what to draw. Damage always goes through
 * {@link HighPowerCombat#attack}; nothing here edits health, shields or the exact ledger directly.
 */
@EventBusSubscriber(modid = "academy")
public final class NewSkillRuntime {
    public enum Kind {
        PULSE_SHIELD(HighPowerSkills.PULSE_SHIELD, HighPowerCategories.ELECTROMASTER, 4, NewSkillBudget.Skill.PULSE_SHIELD, "electromaster.pulse_shield"),
        RAIL_RELAY(HighPowerSkills.RAIL_RELAY, HighPowerCategories.ELECTROMASTER, 5, NewSkillBudget.Skill.RAIL_RELAY, "electromaster.rail_relay"),
        REVERSE_JET(HighPowerSkills.REVERSE_JET, HighPowerCategories.MELTDOWNER, 4, NewSkillBudget.Skill.REVERSE_JET, "meltdowner.reverse_jet_brake"),
        INCIDENT_BYPASS(HighPowerSkills.INCIDENT_BYPASS, HighPowerCategories.TELEPORTER, 3, NewSkillBudget.Skill.INCIDENT_BYPASS, "teleporter.incident_bypass"),
        PHASE_CORRIDOR(HighPowerSkills.PHASE_CORRIDOR, HighPowerCategories.TELEPORTER, 5, NewSkillBudget.Skill.PHASE_CORRIDOR, "teleporter.phase_corridor"),
        ZENITH_DOMINION(HighPowerSkills.ZENITH_DOMINION, HighPowerCategories.VECMANIP, 5, NewSkillBudget.Skill.ZENITH_DOMINION, "vecmanip." + HighPowerSkills.ZENITH_DOMINION),
        IMPULSE_SPLIT(HighPowerSkills.IMPULSE_SPLIT, HighPowerCategories.VECMANIP, 5, NewSkillBudget.Skill.IMPULSE_SPLIT, "vecmanip.impulse_split"),
        CONSTRUCT(HighPowerSkills.VECTOR_CONSTRUCT, HighPowerCategories.VECMANIP, 4, NewSkillBudget.Skill.CONSTRUCT, ConstructRuntime.DAMAGE_KEY);

        public final String id, category, damageKey;
        public final int level;
        final NewSkillBudget.Skill budget;
        Kind(String id, String category, int level, NewSkillBudget.Skill budget, String damageKey) {
            this.id = id; this.category = category; this.level = level; this.budget = budget; this.damageKey = damageKey;
        }
        public static Optional<Kind> of(String skill) {
            for (Kind k : values()) if (k.id.equals(skill)) return Optional.of(k);
            return Optional.empty();
        }
    }

    /** Visual phases sent to clients. */
    public static final int START = 0, DETECT = 1, APPLY = 2, PLACED = 3, RIDE = 4, ARMED = 5, DIVERT = 6, JUMP = 7, LIFT = 8, RELEASE = 9, END = 10;
    public static final TagKey<EntityType<?>> CIRCUIT_TAG = TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("academy", "circuit_adaptable"));
    public static final String CIRCUIT_FLAG = "academy:circuit", PULSE_UNTIL = "academy:pulse_until", BYPASSED = "academy:bypassed";
    static final int PULSE_DETECT_TICKS = 10, RIDE_LIMIT = 200, ROUTE_TTL = 20 * 180, SPLIT_WINDOW = SkillTuning.integer("impulse_split", "duration_ticks"), DIRECTION_PAUSE = 4;
    public static Consumer<Visual> clientReceiver = ignored -> {};

    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<UUID, Route> ROUTES = new HashMap<>();
    private static final Map<UUID, Armed> WINDOWS = new HashMap<>();
    private static final Map<UUID, Long> LAST_JUMP = new HashMap<>(), EVASION_UNTIL = new HashMap<>();
    private static long nextToken;

    private record Watermark(long epoch, long input) {}
    private record Route(List<V3> anchors, long expires) {}
    /** One armed one-shot window (incident bypass or impulse split). */
    private static final class Armed {
        final Kind kind; final IncidentBypassRules.Window window = new IncidentBypassRules.Window();
        final long expires; final V3 dirA, dirB; final long token; final HighPowerInput.Input input;
        long sequence;
        Armed(Context context, long expires, V3 a, V3 b) {
            kind = context.kind; input = context.input; token = context.identity.token();
            this.expires = expires; dirA = a; dirB = b;
            // Both arm sites immediately emit the context's next ARMED cue. Continue after that cue.
            sequence = context.sequence + 1;
            window.arm();
        }
    }
    private static final class Context {
        final LegacySingleKeyServerIdentity identity; final HighPowerInput.Input input; final Kind kind;
        final long started; final int preset; final long revision; final double cost; final int cooldown;
        long sequence; int phase; boolean paid, done, detected; List<UUID> marks = List.of(); V3 firstDirection;
        BigDecimal damage = BigDecimal.ZERO; List<BigDecimal> shares = List.of(); List<V3> route = List.of();
        Context(ServerPlayer p, HighPowerInput.Input in, Kind kind) {
            identity = new LegacySingleKeyServerIdentity(p, in.input(), ++nextToken, AbilityStorage.get(p));
            input = in; this.kind = kind; started = now(p);
            preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            BigDecimal q = HighPowerGrowth.values(p).position();
            cost = NewSkillBudget.cp(kind.budget, q); cooldown = NewSkillBudget.cooldownTicks(kind.budget, q);
        }
    }

    private NewSkillRuntime() {}

    // ---- input entry points ----------------------------------------------------------------------------------------
    public static boolean start(ServerPlayer p, String wire) {
        var in = HighPowerInput.parse(wire);
        if (in == null || !thread(p) || !HighPowerBracerSession.authenticated(p, in)) return false;
        var last = INPUTS.get(p.getUUID());
        if (last != null && last.epoch == in.epoch() && last.input >= in.input()) return false;
        INPUTS.put(p.getUUID(), new Watermark(in.epoch(), in.input()));
        var state = AbilityStorage.get(p);
        Optional<Kind> maybe = Kind.of(state.presets.currentSkill(in.slot()));
        if (maybe.isEmpty()) return false;
        Kind kind = maybe.get();
        if (kind == Kind.CONSTRUCT) {
            if(nextToken==Long.MAX_VALUE) return false;
            BigDecimal q = HighPowerGrowth.values(p).position();
            boolean accepted=ConstructRuntime.press(p, canAct(p, kind), state.canUse(kind.id), NewSkillBudget.cp(kind.budget, q), NewSkillBudget.cooldownTicks(kind.budget, q));
            // Each construct command is a completed tap. Its separate body cues do not carry input ownership.
            // Without this exact-input acknowledgement the shared client edge machine blocks the second tap.
            PacketDistributor.sendToPlayer(p,new Visual(kind.ordinal(),p.getId(),p.getUUID(),++nextToken,1,in.input(),in.epoch(),
                    p.serverLevel().dimension().location(),END,p.position(),Vec3.ZERO,List.of(),now(p)));
            return accepted;
        }
        if (!canAct(p, kind) || !state.canUse(kind.id) || nextToken == Long.MAX_VALUE) return feedback(p, kind, "not_ready");
        Context old = CONTEXTS.get(p.getUUID());
        if (old != null) finish(old, true);
        Context c = new Context(p, in, kind);
        CONTEXTS.put(p.getUUID(), c);
        switch (kind) {
            case PULSE_SHIELD -> send(c, START, p.position(), Vec3.ZERO, List.of());
            case RAIL_RELAY -> startRelay(p, c);
            case REVERSE_JET -> startJet(p, c);
            case INCIDENT_BYPASS -> send(c, START, p.position(), Vec3.ZERO, List.of());
            case PHASE_CORRIDOR -> startCorridor(p, c);
            case ZENITH_DOMINION -> startDominion(p, c);
            case IMPULSE_SPLIT -> startSplit(p, c);
        }
        return true;
    }

    public static boolean release(ServerPlayer p, String wire) { return stop(p, wire, false); }
    public static boolean abort(ServerPlayer p, String wire) { return stop(p, wire, true); }

    private static boolean stop(ServerPlayer p, String wire, boolean force) {
        var in = HighPowerInput.parse(wire);
        if (in == null || !thread(p) || !HighPowerBracerSession.authenticated(p, in)) return false;
        var prior = INPUTS.get(p.getUUID());
        if (prior == null || prior.epoch != in.epoch() || prior.input < in.input()) INPUTS.put(p.getUUID(), new Watermark(in.epoch(), in.input()));
        Context c = owned(p);
        if (c == null || c.input.epoch() != in.epoch() || c.input.slot() != in.slot() || in.input() < c.input.input()) return false;
        if (force) { finish(c, true); return true; }
        switch (c.kind) {
            case PULSE_SHIELD -> releasePulse(p, c);
            case INCIDENT_BYPASS -> { if (!c.done) finish(c, true); }
            case RAIL_RELAY -> { if (c.phase == RIDE) finish(c, true); }
            default -> { }
        }
        return true;
    }

    public static boolean active(ServerPlayer p) { return owned(p) != null; }
    public static void abort(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c, true); }
    public static void remove(ServerPlayer p) {
        if (!thread(p)) return;
        abort(p); INPUTS.remove(p.getUUID()); ROUTES.remove(p.getUUID());
        Armed armed = WINDOWS.remove(p.getUUID());
        if (armed != null) sendWindow(p, armed, END, p.position(), Vec3.ZERO);
        LAST_JUMP.remove(p.getUUID()); EVASION_UNTIL.remove(p.getUUID());
    }
    public static void clear() { CONTEXTS.clear(); INPUTS.clear(); ROUTES.clear(); WINDOWS.clear(); LAST_JUMP.clear(); EVASION_UNTIL.clear(); }
    /** Test/inspection hook: the player's current rail anchors. */
    public static List<V3> anchors(ServerPlayer p) { Route r = ROUTES.get(p.getUUID()); return r == null ? List.of() : r.anchors(); }
    public static boolean evading(ServerPlayer p) { return EVASION_UNTIL.getOrDefault(p.getUUID(), Long.MIN_VALUE) > now(p); }
    public static boolean windowArmed(ServerPlayer p) { Armed a = WINDOWS.get(p.getUUID()); return a != null && a.window.armed() && a.expires > now(p); }

    // ---- 143 pulse shield ------------------------------------------------------------------------------------------
    private static boolean circuitTagged(Entity e) {
        return e.getType().is(CIRCUIT_TAG) || e.getPersistentData().getBoolean(CIRCUIT_FLAG);
    }

    private static List<PulseShieldRules.Candidate> pulseCandidates(ServerPlayer p) {
        var out = new ArrayList<PulseShieldRules.Candidate>();
        AABB box = p.getBoundingBox().inflate(PulseShieldRules.RANGE);
        for (Entity e : p.serverLevel().getEntities(p, box, x -> !x.isRemoved() && x.isAlive() && circuitTagged(x)))
            out.add(new PulseShieldRules.Candidate(e.getUUID(), e.distanceToSqr(p), true));
        return out;
    }

    private static void releasePulse(ServerPlayer p, Context c) {
        if (c.done) return;
        if (now(p) - c.started >= PULSE_DETECT_TICKS) { detectPulse(p, c); finish(c, true); return; }
        var targets = PulseShieldRules.select(pulseCandidates(p));
        if (targets.isEmpty()) { feedback(p, c.kind, "no_target"); finish(c, true); return; }
        if (!HighPowerResources.trySpend(p, c.kind.id, c.cost, c.cooldown)) { finish(c, true); return; }
        c.paid = true; c.done = true;
        BigDecimal damage = PulseShieldRules.damage(HighPowerGrowth.values(p).damage());
        long until = now(p) + PulseShieldRules.interferenceTicks(false);
        var points = new ArrayList<Vec3>();
        for (var t : targets) {
            Entity e = p.serverLevel().getEntity(t.id());
            if (e == null || e.isRemoved() || !circuitTagged(e)) continue;
            e.getPersistentData().putLong(PULSE_UNTIL, until);
            if (e instanceof Mob mob) { mob.setTarget(null); mob.getNavigation().stop(); }
            if (e instanceof LivingEntity living) HighPowerCombat.attack(p, c.kind.damageKey, living, damage);
            points.add(e.position().add(0, e.getBbHeight() / 2, 0));
        }
        send(c, APPLY, p.getEyePosition(), p.getLookAngle(), points);
        finish(c, true);
    }

    private static void detectPulse(ServerPlayer p, Context c) {
        if (c.detected) return;
        c.detected = true;
        var points = new ArrayList<Vec3>();
        for (var t : PulseShieldRules.select(pulseCandidates(p))) {
            Entity e = p.serverLevel().getEntity(t.id());
            if (e != null && !e.isRemoved()) points.add(e.position().add(0, e.getBbHeight() / 2, 0));
        }
        send(c, DETECT, p.getEyePosition(), p.getLookAngle(), points);
    }

    // ---- 153 rail relay --------------------------------------------------------------------------------------------
    private static void startRelay(ServerPlayer p, Context c) {
        List<V3> anchors = anchors(p);
        if (p.isShiftKeyDown()) {
            Vec3 start = p.getEyePosition(), end = start.add(p.getLookAngle().scale(RailRelayRules.MAX_SEGMENT * reach(p)));
            var hit = p.serverLevel().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (hit.getType() != HitResult.Type.BLOCK) { feedback(p, c.kind, "no_target"); finish(c, true); return; }
            Vec3 at = hit.getLocation().add(Vec3.atLowerCornerOf(((net.minecraft.world.phys.BlockHitResult) hit).getDirection().getNormal()).scale(.15));
            Optional<List<V3>> next = RailRelayRules.addAnchor(anchors, new V3(at.x, at.y, at.z), RailRelayRules.MAX_SEGMENT * reach(p));
            if (next.isEmpty() || !loaded(p.serverLevel(), at)) { feedback(p, c.kind, "no_target"); finish(c, true); return; }
            if (!HighPowerResources.trySpend(p, c.kind.id, c.cost * .25, 4)) { finish(c, true); return; }
            ROUTES.put(p.getUUID(), new Route(next.get(), now(p) + ROUTE_TTL));
            c.paid = true; c.done = true;
            send(c, PLACED, at, Vec3.ZERO, vecs(next.get()));
            finish(c, false);
            return;
        }
        V3 here = new V3(p.getX(), p.getY(), p.getZ());
        if (anchors.size() < 2 || RailRelayRules.step(here, anchors, V3.ZERO, true).isEmpty()) { feedback(p, c.kind, "no_target"); finish(c, true); return; }
        if (!HighPowerResources.trySpend(p, c.kind.id, c.cost, c.cooldown)) { finish(c, true); return; }
        c.paid = true; c.phase = RIDE; c.route = anchors;
        send(c, RIDE, p.position(), p.getDeltaMovement(), vecs(anchors));
    }

    private static void tickRelay(ServerPlayer p, Context c) {
        long age = now(p) - c.started;
        if (c.phase != RIDE || age >= RIDE_LIMIT) { finish(c, true); return; }
        Vec3 d = p.getDeltaMovement();
        Optional<V3> step = RailRelayRules.step(new V3(p.getX(), p.getY(), p.getZ()), c.route, new V3(d.x, d.y, d.z), true);
        if (step.isEmpty()) { finish(c, true); return; }
        Vec3 v = new Vec3(step.get().x(), step.get().y(), step.get().z());
        if (!loaded(p.serverLevel(), p.position().add(v.scale(4)))) { finish(c, true); return; }
        AABB body = p.getBoundingBox(), sweep = body.expandTowards(v);
        Vec3 clipped = Entity.collideBoundingBox(p, v, body, p.serverLevel(), p.serverLevel().getEntityCollisions(p, sweep));
        // Native grounded motion includes a small downward gravity component. Let
        // the actual collision resolver remove that floor contact; walls, ceilings
        // and airborne obstructions still terminate the route rather than slide.
        var support = p.serverLevel().clip(new ClipContext(p.position().add(0,.05,0),p.position().add(0,-.10,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        boolean supportedFloor = support.getType() == HitResult.Type.BLOCK
                && support.getDirection() == net.minecraft.core.Direction.UP
                && Math.abs(support.getLocation().y-p.getY()) <= .025;
        boolean floorOnly = supportedFloor && v.y <= 0 && clipped.y == 0
                && clipped.x == v.x && clipped.z == v.z;
        if ((!clipped.equals(v) && !floorOnly) || !p.serverLevel().noCollision(p, body.move(clipped))) { finish(c, true); return; }
        v = clipped;
        if (age > 0 && age % 20 == 0 && !HighPowerResources.trySpend(p, c.kind.id, c.cost * .3, 0)) { finish(c, true); return; }
        p.setDeltaMovement(v); p.hurtMarked = true; p.fallDistance = 0;
    }

    // ---- 243 reverse jet brake -------------------------------------------------------------------------------------
    private static void startJet(ServerPlayer p, Context c) {
        Vec3 v = p.getDeltaMovement();
        if (v.length() < .08) { feedback(p, c.kind, "no_target"); finish(c, true); return; }
        int level = Math.max(1, Math.min(5, c.identity.state().level));
        double cost = ReverseJetRules.cpCost(level, ReverseJetRules.BRAKE_TICKS + ReverseJetRules.HOVER_TICKS) * c.cost / Math.max(1e-9, NewSkillBudget.cp(c.kind.budget, new BigDecimal(level)));
        if (!HighPowerResources.trySpend(p, c.kind.id, cost, c.cooldown)) { finish(c, true); return; }
        c.paid = true;
        send(c, START, p.position(), v, List.of());
    }

    private static void tickJet(ServerPlayer p, Context c) {
        long age = now(p) - c.started;
        if (!c.paid || age >= ReverseJetRules.BRAKE_TICKS + ReverseJetRules.HOVER_TICKS) { finish(c, true); return; }
        Vec3 v = p.getDeltaMovement();
        V3 before = new V3(v.x, v.y, v.z);
        V3 after = age < ReverseJetRules.BRAKE_TICKS ? ReverseJetRules.brake(before, .2)
                : ReverseJetRules.hoverVelocity(before, ReverseJetRules.BRAKE_TICKS + ReverseJetRules.HOVER_TICKS - (int) age);
        p.setDeltaMovement(after.x(), after.y(), after.z()); p.hurtMarked = true;
        p.fallDistance = ReverseJetRules.fallDistance(p.fallDistance, before.y(), after.y());
    }

    // ---- 332 incident bypass / 453 impulse split windows -----------------------------------------------------------
    private static void tickBypass(ServerPlayer p, Context c) {
        long held = now(p) - c.started;
        if (c.done) { finish(c, false); return; }
        if (held >= IncidentBypassRules.ARM_TICKS) {
            if (!HighPowerResources.trySpend(p, c.kind.id, c.cost, c.cooldown)) { finish(c, true); return; }
            c.paid = true; c.done = true;
            WINDOWS.put(p.getUUID(), new Armed(c, now(p) + IncidentBypassRules.WINDOW_TICKS, V3.ZERO, V3.ZERO));
            send(c, ARMED, p.position(), p.getLookAngle(), List.of());
        }
    }

    private static List<Projectile> incoming(ServerPlayer p, double range) {
        var out = new ArrayList<Projectile>();
        AABB box = p.getBoundingBox().inflate(range);
        for (Projectile pr : p.serverLevel().getEntitiesOfClass(Projectile.class, box, e -> !e.isRemoved() && e.getOwner() != p && !e.getPersistentData().getBoolean(BYPASSED))) {
            Vec3 v = pr.getDeltaMovement(), toward = p.getEyePosition().subtract(pr.position());
            if (v.lengthSqr() > .01 && toward.lengthSqr() > 1e-6 && v.normalize().dot(toward.normalize()) > .5) out.add(pr);
        }
        out.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        return out;
    }

    private static IncidentBypassRules.Kind kindOf(Projectile pr) {
        if (pr instanceof AbstractArrow) return IncidentBypassRules.Kind.ARROW;
        if (pr instanceof ThrowableProjectile && !(pr instanceof net.minecraft.world.entity.projectile.ThrownPotion)) return IncidentBypassRules.Kind.THROWN;
        if (pr instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile) return IncidentBypassRules.Kind.FIRE;
        return IncidentBypassRules.Kind.AREA_FIELD;
    }

    private static void tickWindow(ServerPlayer p, Armed armed) {
        if (!armed.window.armed() || armed.expires <= now(p)) {
            if (WINDOWS.remove(p.getUUID(), armed)) sendWindow(p, armed, END, p.position(), Vec3.ZERO);
            return;
        }
        for (Projectile pr : incoming(p, armed.kind == Kind.IMPULSE_SPLIT ? 8 : 7)) {
            if (!IncidentBypassRules.handles(kindOf(pr))) continue;
            if (armed.kind == Kind.IMPULSE_SPLIT && pr instanceof AbstractArrow a && !ImpulseSplitArrowBudget.candidate(a)) continue;
            if (!armed.window.consume()) return;
            WINDOWS.remove(p.getUUID(), armed);
            try {
                boolean ok = armed.kind == Kind.IMPULSE_SPLIT ? splitProjectile(p, pr, armed) : bypassProjectile(p, pr, armed);
                if (ok) sendWindow(p, armed, DIVERT, pr.position(), pr.getDeltaMovement());
            } finally {
                // Close input ownership even when a candidate is consumed without a successful diversion.
                // END does not remove the already received, independently timed DIVERT effect.
                sendWindow(p, armed, END, p.position(), Vec3.ZERO);
            }
            return;
        }
    }

    private static boolean bypassProjectile(ServerPlayer p, Projectile pr, Armed armed) {
        pr.getPersistentData().putBoolean(BYPASSED, true);
        double share = IncidentBypassRules.divertedShare(1);
        if (pr instanceof AbstractArrow arrow) {
            // The diverted share is removed from the hit; the arrow keeps its path with the remaining share.
            arrow.setBaseDamage(arrow.getBaseDamage() * IncidentBypassRules.remainingShare(1));
            return true;
        }
        Vec3 v = pr.getDeltaMovement(), toward = p.getEyePosition().subtract(pr.position());
        Optional<V3> out = IncidentBypassRules.bypass(new V3(v.x, v.y, v.z), new V3(toward.x, toward.y, toward.z), (armed.token & 1) == 0);
        if (out.isEmpty()) return false;
        pr.setDeltaMovement(out.get().x(), out.get().y(), out.get().z()); pr.hasImpulse = true;
        return share > 0;
    }

    // ---- 352 phase corridor ----------------------------------------------------------------------------------------
    private static boolean loaded(ServerLevel level, Vec3 at) {
        return level.hasChunkAt(BlockPos.containing(at));
    }

    /** Standing spot within {@code max} metres along the aim, searched from far to near; empty when none is safe. */
    static Optional<Vec3> landing(ServerPlayer p, double max) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle().normalize();
        var hit = level.clip(new ClipContext(eye, eye.add(look.scale(max)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double reach = hit.getType() == HitResult.Type.MISS ? max : Math.max(0, eye.distanceTo(hit.getLocation()) - .6);
        for (double d = reach; d >= 2; d -= .5) {
            Vec3 base = eye.add(look.scale(d));
            for (int drop = 0; drop <= 4; drop++) {
                Vec3 feet = new Vec3(base.x, Math.floor(base.y) - drop + 0.0, base.z);
                if (!loaded(level, feet)) break;
                AABB box = p.getBoundingBox().move(feet.subtract(p.position()));
                BlockPos below = BlockPos.containing(feet.x, feet.y - .1, feet.z);
                if (level.noCollision(p, box) && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                        && level.getFluidState(BlockPos.containing(feet)).isEmpty() && PhaseCorridorRules.validJump(feet.distanceTo(p.position()), max))
                    return Optional.of(feet);
            }
        }
        return Optional.empty();
    }

    private static double reach(ServerPlayer p) {
        try { return cn.academy.port.highpower.core.MobilityScale.reach(HighPowerGrowth.values(p).position()); }
        catch (IllegalStateException | IllegalArgumentException unavailable) { return 1; }
    }

    private static void startCorridor(ServerPlayer p, Context c) {
        long since = now(p) - LAST_JUMP.getOrDefault(p.getUUID(), Long.MIN_VALUE / 2);
        if (!PhaseCorridorRules.canJump((int) Math.min(Integer.MAX_VALUE, since))) { feedback(p, c.kind, "not_ready"); finish(c, true); return; }
        Optional<Vec3> target = landing(p, PhaseCorridorRules.JUMP_RANGE * reach(p));
        if (target.isEmpty()) { feedback(p, c.kind, "no_target"); finish(c, true); return; }
        if (!HighPowerResources.trySpend(p, c.kind.id, c.cost, c.cooldown)) { finish(c, true); return; }
        Vec3 from = p.position(), to = target.get();
        c.paid = true; c.done = true;
        p.teleportTo(to.x, to.y, to.z);
        p.fallDistance = 0; p.hurtMarked = true;
        LAST_JUMP.put(p.getUUID(), now(p));
        EVASION_UNTIL.put(p.getUUID(), now(p) + PhaseCorridorRules.EVASION_TICKS);
        send(c, JUMP, to, from.subtract(to), List.of(from, to));
        finish(c, false);
    }

    /** 0.1 s of projectile evasion right after a jump; every other damage source (and the exposed window) is untouched. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void evade(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !evading(p)) return;
        if (event.getSource().is(DamageTypeTags.IS_PROJECTILE)) event.setCanceled(true);
    }

    // ---- 451 zenith vector dominion --------------------------------------------------------------------------------
    private static void startDominion(ServerPlayer p, Context c) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle().normalize();
        record Cand(LivingEntity e, double angle) {}
        var found = new ArrayList<Cand>();
        AABB box = new AABB(eye, eye).inflate(ZenithDominionRules.RANGE);
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, box, x -> x != p && x.isAlive() && !x.isRemoved() && VectorCageWorld.hostile(p, x))) {
            Vec3 to = e.getEyePosition().subtract(eye);
            double dist = to.length();
            if (dist < 1e-6 || dist > ZenithDominionRules.RANGE) continue;
            double cos = to.normalize().dot(look);
            if (cos < .5) continue;
            found.add(new Cand(e, -cos));
        }
        found.sort(java.util.Comparator.comparingDouble(Cand::angle).thenComparing(x -> x.e.getUUID()));
        List<UUID> marks = ZenithDominionRules.freeze(found.stream().map(x -> x.e.getUUID()).toList());
        if (marks.isEmpty()) { feedback(p, c.kind, "no_target"); finish(c, true); return; }
        if (!HighPowerResources.trySpend(p, c.kind.id, c.cost, c.cooldown)) { finish(c, true); return; }
        c.paid = true; c.marks = marks;
        c.damage = ZenithDominionRules.totalDamage(HighPowerGrowth.values(p).damage());
        c.shares = ZenithDominionRules.shares(c.damage, marks.size());
        send(c, START, p.getEyePosition(), look, positions(p, marks));
    }

    private static List<Vec3> positions(ServerPlayer p, List<UUID> marks) {
        var out = new ArrayList<Vec3>();
        for (UUID id : marks) { Entity e = p.serverLevel().getEntity(id); if (e != null && !e.isRemoved()) out.add(e.position().add(0, e.getBbHeight() / 2, 0)); }
        return out;
    }

    private static void tickDominion(ServerPlayer p, Context c) {
        long age = now(p) - c.started;
        if (!c.paid || age > ZenithDominionRules.LIFT_TICKS + ZenithDominionRules.RELEASE_TICKS) { finish(c, true); return; }
        if (age < ZenithDominionRules.LIFT_TICKS) {
            double up = ZenithDominionRules.liftVelocity((int) age);
            for (UUID id : c.marks) {
                Entity e = p.serverLevel().getEntity(id);
                if (e instanceof LivingEntity t && t.isAlive()) { t.setDeltaMovement(t.getDeltaMovement().x * .2, up, t.getDeltaMovement().z * .2); t.hurtMarked = true; t.fallDistance = 0; }
            }
            if (age % 4 == 0) send(c, LIFT, p.getEyePosition(), Vec3.ZERO, positions(p, c.marks));
            return;
        }
        if (age == ZenithDominionRules.LIFT_TICKS && !c.done) {
            c.done = true;
            var hits = new ArrayList<Vec3>();
            for (int i = 0; i < c.marks.size(); i++) {
                Entity e = p.serverLevel().getEntity(c.marks.get(i));
                if (!(e instanceof LivingEntity t) || !t.isAlive() || !VectorCageWorld.hostile(p, t)) continue;
                hits.add(t.position().add(0, t.getBbHeight() / 2, 0));
                Vec3 away = t.position().subtract(p.position()).multiply(1, 0, 1);
                Vec3 push = away.lengthSqr() < 1e-6 ? Vec3.ZERO : away.normalize().scale(.6);
                t.setDeltaMovement(push.x, -.9, push.z); t.hurtMarked = true;
                HighPowerCombat.attackEmission(new EmissionPermit(c,i,t));
            }
            send(c, RELEASE, p.getEyePosition(), p.getLookAngle(), hits);
        }
    }

    /** Only the frozen NEW451 mark/share loop can create this one-use capability. */
    public static final class EmissionPermit implements AbilityEmission {
        private final Context context;
        private final int index;
        private final LivingEntity target;
        private final Vec3 position;
        private boolean claimed;
        private EmissionPermit(Context c,int index,LivingEntity target) { context=c; this.index=index; this.target=target; position=target.position(); }
        public ServerPlayer player() { return context.identity.owner(); }
        public LivingEntity target() { return target; }
        public String skill() { return context.kind.damageKey; }
        public BigDecimal damage() { return context.shares.get(index); }
        public boolean claim() { if(claimed || !valid()) return false; claimed=true; return true; }
        public boolean valid() {
            return thread(player()) && context.kind==Kind.ZENITH_DOMINION && context.paid && context.done && NewSkillRuntime.valid(context)
                    && now(player())-context.started==ZenithDominionRules.LIFT_TICKS && index>=0 && index<context.marks.size()
                    && context.marks.get(index).equals(target.getUUID()) && player().serverLevel().getEntity(target.getId())==target
                    && player().serverLevel().getEntity(target.getUUID())==target && target.position().equals(position);
        }
    }

    // ---- 453 impulse split -----------------------------------------------------------------------------------------
    private static void startSplit(ServerPlayer p, Context c) {
        Vec3 look = p.getLookAngle();
        V3 dir = new V3(look.x, 0, look.z).unit();
        if (dir.length() < .5 || !openDirection(p, dir)) { feedback(p, c.kind, "no_target"); finish(c, true); return; }
        V3 first = SPLIT_FIRST.get(p.getUUID());
        if (first == null || now(p) - SPLIT_FIRST_TICK.getOrDefault(p.getUUID(), 0L) > 200) {
            SPLIT_FIRST.put(p.getUUID(), dir); SPLIT_FIRST_TICK.put(p.getUUID(), now(p));
            send(c, START, p.position(), look, List.of());
            finish(c, false);
            return;
        }
        // The two chosen directions define their own front (the bisector); they must be distinct and not opposite.
        V3 bisector = first.plus(dir);
        if (bisector.length() < .35 || !ImpulseSplitRules.validDirections(bisector, first, dir)) { SPLIT_FIRST.remove(p.getUUID()); feedback(p, c.kind, "no_target"); finish(c, true); return; }
        if (!HighPowerResources.trySpend(p, c.kind.id, c.cost, c.cooldown)) { SPLIT_FIRST.remove(p.getUUID()); finish(c, true); return; }
        SPLIT_FIRST.remove(p.getUUID());
        WINDOWS.put(p.getUUID(), new Armed(c, now(p) + SPLIT_WINDOW, first, dir));
        c.paid = true;
        send(c, ARMED, p.position(), look, List.of(p.position().add(first.x(), 1, first.z()), p.position().add(dir.x(), 1, dir.z())));
        finish(c, false);
    }
    private static final Map<UUID, V3> SPLIT_FIRST = new HashMap<>();
    private static final Map<UUID, Long> SPLIT_FIRST_TICK = new HashMap<>();

    /** A direction is open when nothing solid blocks the first four metres of it at chest height. */
    private static boolean openDirection(ServerPlayer p, V3 dir) {
        Vec3 from = p.position().add(0, 1, 0), to = from.add(dir.x() * 4, 0, dir.z() * 4);
        return p.serverLevel().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }

    private static boolean splitProjectile(ServerPlayer p, Projectile pr, Armed armed) {
        Vec3 v = pr.getDeltaMovement();
        Optional<ImpulseSplitRules.Pair> pair = ImpulseSplitRules.split(new V3(v.x, v.y, v.z), armed.dirA, armed.dirB);
        if (pair.isEmpty() || !(pr instanceof AbstractArrow || pr instanceof ThrowableProjectile)) return false;
        pr.getPersistentData().putBoolean(BYPASSED, true);
        // Ordinary arrows receive one integer native budget; each side owns its own nonrefundable share.
        if (pr instanceof AbstractArrow a && !ImpulseSplitArrowBudget.candidate(a)) return false;
        CompoundTag tag = pr.saveWithoutId(new CompoundTag());
        tag.remove("UUID");
        Entity copy = pr.getType().create(pr.level());
        if (copy == null) return false;
        copy.load(tag);
        copy.setUUID(UUID.randomUUID());
        if (pr instanceof Arrow a && (!(copy instanceof Arrow b) || !ImpulseSplitArrowBudget.arm(a,b))) return false;
        pr.setDeltaMovement(pair.get().first().x(), pair.get().first().y(), pair.get().first().z()); pr.hasImpulse = true;
        copy.setDeltaMovement(pair.get().second().x(), pair.get().second().y(), pair.get().second().z()); copy.hasImpulse = true;
        copy.getPersistentData().putBoolean(BYPASSED, true);
        if (!pr.level().addFreshEntity(copy)) copy.discard();
        return true;
    }

    // ---- shared plumbing -------------------------------------------------------------------------------------------
    private static boolean canAct(ServerPlayer p, Kind kind) {
        var state = AbilityStorage.get(p);
        return thread(p) && HighPowerResources.ready(p) && kind.category.equals(state.category) && state.learned(kind.id)
                && state.level >= kind.level && SkillCatalog.enabled(kind.category, kind.id)
                && HighPowerCombat.unsupportedReason(p).isEmpty() && !AbilityConsumption.busy(p)
                && !p.isPassenger() && !p.isVehicle() && !p.isSleeping() && !p.noPhysics && !p.isSpectator()
                && finite(p.position()) && finite(p.getDeltaMovement());
    }

    private static boolean valid(Context c) {
        ServerPlayer p = c.identity.owner();
        return CONTEXTS.get(p.getUUID()) == c && c.identity.owns(p) && HighPowerBracerSession.authenticated(p, c.input) && canAct(p, c.kind)
                && c.preset == c.identity.state().presets.current() && c.revision == c.identity.state().presets.revision()
                && c.kind.id.equals(c.identity.state().presets.currentSkill(c.input.slot()));
    }

    private static void finish(Context c, boolean notify) {
        if (CONTEXTS.remove(c.identity.owner().getUUID(), c) && notify) send(c, END, c.identity.owner().position(), Vec3.ZERO, List.of());
    }

    private static boolean feedback(ServerPlayer p, Kind kind, String key) {
        if (HighPowerCombat.unsupportedReason(p).isPresent()) key = "unsupported";
        p.displayClientMessage(Component.translatable("message.academy.highpower." + kind.id + "." + key), true);
        return false;
    }

    private static List<Vec3> vecs(List<V3> list) {
        var out = new ArrayList<Vec3>(list.size());
        for (V3 v : list) out.add(new Vec3(v.x(), v.y(), v.z()));
        return out;
    }

    private static void send(Context c, int phase, Vec3 point, Vec3 dir, List<Vec3> points) {
        ServerPlayer p = c.identity.owner();
        broadcast(p, new Visual(c.kind.ordinal(), p.getId(), p.getUUID(), c.identity.token(), ++c.sequence, c.input.input(), c.input.epoch(),
                p.serverLevel().dimension().location(), phase, point, dir, points.subList(0, Math.min(16, points.size())), now(p)));
    }

    private static void sendWindow(ServerPlayer p, Armed armed, int phase, Vec3 point, Vec3 dir) {
        broadcast(p, new Visual(armed.kind.ordinal(), p.getId(), p.getUUID(), armed.token, ++armed.sequence,
                armed.input.input(), armed.input.epoch(), p.serverLevel().dimension().location(),
                phase, point, dir, List.of(p.position().add(0, 1, 0)), now(p)));
    }

    private static void broadcast(ServerPlayer p, Visual v) {
        for (var o : p.serverLevel().players())
            if (o.distanceToSqr(p) <= 96 * 96 && (o == p || !p.isInvisibleTo(o)) && o.connection != null && !o.hasDisconnected()) PacketDistributor.sendToPlayer(o, v);
    }

    private static Context owned(ServerPlayer p) {
        if (!thread(p)) return null;
        Context c = CONTEXTS.get(p.getUUID());
        return c != null && c.identity.owner() == p ? c : null;
    }
    private static boolean thread(ServerPlayer p) { return p != null && p.server.isSameThread(); }
    private static long now(ServerPlayer p) { return p.serverLevel().getGameTime(); }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z) && Math.abs(v.x) <= 3e7 && Math.abs(v.y) <= 3e7 && Math.abs(v.z) <= 3e7; }

    // ---- ticking and lifecycle -------------------------------------------------------------------------------------
    static void tick(ServerPlayer p) {
        if (HighPowerResources.ready(p) || ConstructRuntime.heldBody(p) != null || ConstructRuntime.previewing(p) || ConstructRuntime.dashing(p)) {
            BigDecimal q = HighPowerGrowth.values(p).position();
            ConstructRuntime.tick(p, canAct(p, Kind.CONSTRUCT), NewSkillBudget.cp(Kind.CONSTRUCT.budget, q), NewSkillBudget.cooldownTicks(Kind.CONSTRUCT.budget, q));
        }
        Armed armed = WINDOWS.get(p.getUUID());
        if (armed != null) tickWindow(p, armed);
        Route route = ROUTES.get(p.getUUID());
        if (route != null && route.expires() <= now(p)) ROUTES.remove(p.getUUID());
        Context c = owned(p);
        if (c == null) return;
        if (!valid(c)) { finish(c, true); return; }
        switch (c.kind) {
            case PULSE_SHIELD -> { if (!c.detected && now(p) - c.started >= PULSE_DETECT_TICKS) detectPulse(p, c); if (now(p) - c.started > PULSE_DETECT_TICKS + 40) finish(c, true); }
            case RAIL_RELAY -> { if (c.phase == RIDE) tickRelay(p, c); }
            case REVERSE_JET -> tickJet(p, c);
            case INCIDENT_BYPASS -> tickBypass(p, c);
            case ZENITH_DOMINION -> tickDominion(p, c);
            default -> { }
        }
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post e) {
        for (var p : new ArrayList<>(e.getServer().getPlayerList().getPlayers())) tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { if (e.getOriginal() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void death(LivingDeathEvent e) { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { clear(); SPLIT_FIRST.clear(); SPLIT_FIRST_TICK.clear(); }

    // ---- network ---------------------------------------------------------------------------------------------------
    public record Visual(int kind, int entityId, UUID owner, long token, long sequence, long input, long epoch, ResourceLocation dimension,
                         int phase, Vec3 point, Vec3 dir, List<Vec3> points, long gameTime) implements CustomPacketPayload {
        public Visual {
            if (kind < 0 || kind >= Kind.values().length || entityId < 0 || owner == null || token <= 0 || sequence <= 0 || input <= 0 || epoch <= 0
                    || dimension == null || phase < START || phase > END || !fin(point) || !fin(dir) || points == null || points.size() > 16) throw new IllegalArgumentException("new skill visual");
            for (Vec3 v : points) if (!fin(v)) throw new IllegalArgumentException("new skill visual point");
            points = List.copyOf(points);
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "new_skill_visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeVarInt(v.kind); b.writeInt(v.entityId); b.writeUUID(v.owner); b.writeLong(v.token); b.writeLong(v.sequence); b.writeLong(v.input); b.writeLong(v.epoch);
            b.writeResourceLocation(v.dimension); b.writeVarInt(v.phase); vec(b, v.point); vec(b, v.dir); b.writeVarInt(v.points.size());
            for (Vec3 p : v.points) vec(b, p);
            b.writeLong(v.gameTime);
        }, b -> {
            int kind = b.readVarInt(); int entity = b.readInt(); UUID owner = b.readUUID(); long token = b.readLong(), sequence = b.readLong(), input = b.readLong(), epoch = b.readLong();
            ResourceLocation dim = b.readResourceLocation(); int phase = b.readVarInt(); Vec3 point = vec(b), dir = vec(b);
            int n = b.readVarInt();
            if (n < 0 || n > 16) throw new IllegalArgumentException("new skill visual size");
            List<Vec3> pts = new ArrayList<>(n);
            for (int i = 0; i < n; i++) pts.add(vec(b));
            return new Visual(kind, entity, owner, token, sequence, input, epoch, dim, phase, point, dir, pts, b.readLong());
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        private static boolean fin(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z) && Math.abs(v.x) <= 3e7 && Math.abs(v.y) <= 3e7 && Math.abs(v.z) <= 3e7; }
        private static void vec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
        private static Vec3 vec(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    }

    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) {
            e.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (payload, context) -> clientReceiver.accept(payload));
        }
    }
}
