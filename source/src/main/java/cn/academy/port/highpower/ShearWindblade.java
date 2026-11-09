package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.ShearWindbladeGeometry;
import cn.academy.port.highpower.core.ShearWindbladeGeometry.Box;
import cn.academy.port.highpower.core.ShearWindbladeGeometry.Contact;
import cn.academy.port.highpower.core.ShearWindbladeGeometry.Frame;
import cn.academy.port.highpower.core.ShearWindbladeGeometry.Point;
import cn.academy.port.highpower.core.ShearWindbladeRules;
import io.netty.handler.codec.DecoderException;
import java.math.BigDecimal;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW421. Two finite curved fronts share one frozen, once-only native damage allocation. */
@EventBusSubscriber(modid = "academy")
public final class ShearWindblade {
    public static final String ID = "shear_windblade";
    public static final int FLYING = 0, ENDED = 1;
    public static final int TRAVELLING = 0, BLOCKED = 1, EXPIRED = 2;
    public static final int REFUSED = 0, DAMAGED = 1, ABSORBED = 2;
    private static final String DATA = "academy:shear_windblade", PHYSICAL_AT = "physicalAt", CLOCK = "next_" + ID;
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<ServerPlayer, Long> PREPARATIONS = new IdentityHashMap<>();
    private static final Set<ServerPlayer> PROCESSING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(long epoch, long input) {}
    private record Body(Entity entity, Vec3 position, AABB bounds, Contact contact) {}
    private record Scan(List<Body> bodies, double solid, List<Entity> candidates) {}
    private record Observed(UUID uuid, int id) {}
    private record Event(int blade, Body body) {}
    private static final class Slot {
        final LivingEntity entity;
        final UUID uuid;
        final int id;
        BigDecimal share;
        boolean attempted;
        Slot(LivingEntity entity) { this.entity = entity; uuid = entity.getUUID(); id = entity.getId(); }
    }
    private static final class Blade {
        final int index;
        double distance;
        boolean stopped;
        Blade(int index) { this.index = index; }
    }
    private static final class Step {
        final Blade blade;
        final double from, to;
        final List<Vec3> previous;
        Scan scan;
        double stop = Double.POSITIVE_INFINITY;
        boolean denied;
        Step(Context c, Blade blade) {
            this.blade = blade; from = blade.distance;
            to = Math.min(ShearWindbladeGeometry.MAX_TRANSLATION, from + ShearWindbladeRules.STEP);
            previous = vertices(c, from);
        }
    }
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final int preset, plane;
        final long revision, started;
        final Vec3 origin;
        final boolean crouch;
        final Frame frame;
        final Blade[] blades = {new Blade(0), new Blade(1)};
        final Set<ServerPlayer> audience = new LinkedHashSet<>();
        final List<Impact> impacts = new ArrayList<>(ShearWindbladeRules.MAX_TARGETS);
        List<Slot> targets = List.of();
        BigDecimal growth;
        long lastTick, sequence, finishedAt = -1;
        int attempts, successes;
        boolean paid, processing, poisoned;
        CompoundTag paidLedger;
        long paidDeadline;
        Witness witness;
        EmissionPermit emitting;
        Receipt receipt;
        Context(ServerPlayer p, HighPowerInput.Input input, Frame frame) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input; this.frame = frame; preset = identity.state().presets.current();
            revision = identity.state().presets.revision(); started = lastTick = now(p);
            origin = p.getEyePosition(); crouch = p.isShiftKeyDown(); plane = crouch ? 1 : 0;
        }
    }
    /** One synchronous preparation/advance owns one payer snapshot, shared by every target. */
    private record Witness(long tick, CompoundTag ledger, long balance, int balanceType, long clock, int clockType,
                           int cooldown, Vec3 position, Vec3 eye, Vec3 look, int level, long experience) {}
    private record Emission(Slot slot, Step step, Body body, Vec3 source, Witness witness) {}
    /** Closed, one-use native authority. Neither caller nor network can select damage, victim or source. */
    public static final class EmissionPermit {
        private final Context context;
        private final Emission emission;
        private boolean consumed;
        private EmissionPermit(Context context, Emission emission) { this.context = context; this.emission = emission; }
        ServerPlayer player() { return context.identity.owner(); }
        LivingEntity target() { return emission.slot.entity; }
        Vec3 origin() { return emission.source; }
        BigDecimal damage() { return emission.slot.share; }
        boolean consume() { if (consumed || !valid()) return false; consumed = true; return true; }
        boolean valid() {
            // Always observe the transaction first and last, even when another guard
            // refuses this target. A later callback cannot restore a poisoned lease.
            if (!checkWitness(context)) return false;
            ServerPlayer p = player();
            boolean valid = context.emitting == this && context.processing && PROCESSING.contains(p) && context.paid
                    && context.witness == emission.witness && emission.slot.attempted && damage().signum() > 0
                    && session(context) && identity(context, emission.slot) && hostile(p, target())
                    && unchanged(emission.body) && visible(context, emission.step, emission.body)
                    && identity(context, emission.slot) && unchanged(emission.body)
                    && target().isAlive() && !target().isSpectator() && !target().isInvulnerable() && !target().noPhysics
                    && !target().getPersistentData().getBoolean("academy:protected");
            boolean intact = checkWitness(context);
            return valid && intact;
        }
    }
    public record FrontPose(int index, List<Vec3> previous, List<Vec3> current, int status) {
        public FrontPose {
            previous = List.copyOf(previous); current = List.copyOf(current);
            if (index < 0 || index >= 2 || previous.size() != 9 || current.size() != 9 || status < TRAVELLING || status > EXPIRED
                    || previous.stream().anyMatch(v -> !finite(v)) || current.stream().anyMatch(v -> !finite(v)))
                throw new IllegalArgumentException("Invalid NEW421 front");
        }
    }
    public record Impact(int blade, int slot, Vec3 point, int status, int hitEntity) {
        public Impact {
            if (blade < 0 || blade >= 2 || slot < 0 || slot >= 4 || !finite(point)
                    || status < REFUSED || status > ABSORBED || hitEntity < -1 || status != REFUSED && hitEntity < 0)
                throw new IllegalArgumentException("Invalid NEW421 impact");
        }
    }
    public record Receipt(long input, boolean paid, int attempts, int successes, List<UUID> targets,
                          List<BigDecimal> shares, List<FrontPose> fronts, List<Impact> impacts) {
        public Receipt { targets = List.copyOf(targets); shares = List.copyOf(shares); fronts = List.copyOf(fronts); impacts = List.copyOf(impacts); }
    }
    private ShearWindblade() {}

    public static boolean tap(ServerPlayer p, String wire) {
        if (!thread(p) || PROCESSING.contains(p)) return false;
        HighPowerInput.Input input = claim(p, wire);
        if (input == null || nextToken == Long.MAX_VALUE || !ShearWindbladeRules.clock(now(p))) return false;
        Context old = owned(p);
        if (old != null && !validateIdle(old)) finish(old);
        Frame frozen;
        try { frozen = ShearWindbladeGeometry.frame(point(p.getEyePosition()), point(p.getLookAngle()), Math.toRadians(p.getYRot())); }
        catch (IllegalArgumentException invalid) { return false; }
        Context c = new Context(p, input, frozen);
        if (owned(p) != null || !baseCanAct(p) || !AbilityStorage.get(p).canUse(ID) || !fenceReady(p)
                || PREPARATIONS.getOrDefault(p, -1L) == now(p)) return refuse(c, "not_ready");
        // A free refusal spends no cast fence, CP or cooldown; bound expensive
        // preparation separately to one non-reentrant scan per native owner tick.
        PREPARATIONS.put(p, now(p));
        Context foreign = CONTEXTS.get(p.getUUID()); if (foreign != null) finish(foreign);
        CONTEXTS.put(p.getUUID(), c); c.processing = true; PROCESSING.add(p);
        try {
            // Growth may legitimately normalize its own cache/save fields. Do that
            // once before capturing the initial pre-payment callback witness.
            var growth = HighPowerGrowth.values(p); c.growth = growth.position();
            c.witness = captureWitness(c);
            if (!session(c)) { finish(c); feedback(p, "not_ready"); return false; }
            c.targets = freezeTargets(c);
            if (c.targets.isEmpty() || !session(c)) { finish(c); feedback(p, "no_target"); return false; }
            List<BigDecimal> shares = ShearWindbladeRules.shares(growth.damage(), c.targets.size());
            for (int i = 0; i < c.targets.size(); i++) c.targets.get(i).share = shares.get(i);
            advance(c, 0);
            if (!c.paid) { finish(c); feedback(p, "not_ready"); }
            return c.paid;
        } finally { c.emitting = null; c.witness = null; c.processing = false; PROCESSING.remove(p); }
    }

    /** The initial bodies are sampled once. No later body is admitted and no lost share is recycled. */
    private static List<Slot> freezeTargets(Context c) {
        Map<UUID, Slot> admitted = new LinkedHashMap<>();
        Map<UUID, Entity> uuids = new HashMap<>();
        Map<Entity, Observed> observed = new IdentityHashMap<>();
        double[] distance = new double[2]; boolean[] stopped = new boolean[2];
        for (int age = 0; age < ShearWindbladeRules.LIFETIME; age++) {
            List<Event> events = new ArrayList<>(); double[] stops = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
            double[] ends = distance.clone();
            for (int blade = 0; blade < 2; blade++) {
                if (stopped[blade] || age < blade * ShearWindbladeRules.SECOND_DELAY) continue;
                ends[blade] = Math.min(ShearWindbladeGeometry.MAX_TRANSLATION, distance[blade] + ShearWindbladeRules.STEP);
                Scan scan = scan(c, distance[blade], ends[blade]);
                // Overflow, an unloaded/denied volume or malformed geometry invalidates
                // the entire unpaid plan, even if an earlier prefix found a target.
                if (scan == null) return List.of();
                // The whole initial preparation has one body budget, including
                // broad-phase misses. A repeated UUID cannot stand for a replacement body.
                for (Entity entity : scan.candidates) {
                    Entity priorEntity = uuids.putIfAbsent(entity.getUUID(), entity);
                    Observed prior = observed.get(entity);
                    if (priorEntity != null && priorEntity != entity
                            || prior != null && (!prior.uuid.equals(entity.getUUID()) || prior.id != entity.getId())) return List.of();
                    observed.putIfAbsent(entity, new Observed(entity.getUUID(), entity.getId()));
                    if (observed.size() > ShearWindbladeRules.SCAN_LIMIT) return List.of();
                }
                stops[blade] = scan.solid;
                for (Body body : scan.bodies) events.add(new Event(blade, body));
            }
            events.sort(eventOrder(c));
            for (Event event : events) {
                int blade = event.blade; Body body = event.body;
                if (stopped[blade] || body.contact.time() >= stops[blade]) continue;
                Slot prior = admitted.get(body.entity.getUUID());
                if (prior != null && prior.entity == body.entity) continue;
                if (!(body.entity instanceof LivingEntity living) || !hostile(c.identity.owner(), living)
                        || admitted.size() == ShearWindbladeRules.MAX_TARGETS) { stops[blade] = body.contact.time(); continue; }
                admitted.put(living.getUUID(), new Slot(living));
            }
            for (int blade = 0; blade < 2; blade++) {
                if (age < blade * ShearWindbladeRules.SECOND_DELAY || stopped[blade]) continue;
                distance[blade] = ends[blade];
                if (Double.isFinite(stops[blade]) || ends[blade] >= ShearWindbladeGeometry.MAX_TRANSLATION) stopped[blade] = true;
            }
            if (stopped[0] && stopped[1]) break;
        }
        return List.copyOf(admitted.values());
    }
    private static Comparator<Event> eventOrder(Context c) {
        return Comparator.comparingDouble((Event e) -> e.body.contact.time())
                .thenComparingInt(e -> e.body.entity instanceof LivingEntity living && hostile(c.identity.owner(), living) ? 1 : 0)
                .thenComparingInt(Event::blade).thenComparing(e -> e.body.entity.getUUID());
    }

    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null || c.processing) return;
        c.processing = true; PROCESSING.add(p);
        // Capture before session/canAct: their permission checks must not establish
        // a new payer baseline after a callback has already changed this tick.
        c.witness = captureWitness(c);
        try {
            long tick = now(p);
            if (!session(c) || !ShearWindbladeRules.clock(tick) || tick < c.lastTick) { finish(c); return; }
            if (tick == c.lastTick) return;
            if (tick != c.lastTick + 1 || tick - c.started >= ShearWindbladeRules.LIFETIME) { finish(c); return; }
            if (c.finishedAt >= 0) { finish(c); return; }
            c.lastTick = tick; advance(c, (int)(tick - c.started));
        } finally { c.emitting = null; c.witness = null; c.processing = false; PROCESSING.remove(p); }
    }
    private static void advance(Context c, int age) {
        if (!session(c)) { finish(c); return; }
        List<Step> steps = new ArrayList<>(2); List<Event> events = new ArrayList<>(); c.impacts.clear();
        for (Blade blade : c.blades) {
            if (blade.stopped || age < blade.index * ShearWindbladeRules.SECOND_DELAY) continue;
            Step step = new Step(c, blade); steps.add(step); step.scan = scan(c, step.from, step.to);
            if (step.scan == null) { step.denied = true; step.stop = 0; continue; }
            step.stop = step.scan.solid;
            for (Body body : step.scan.bodies) events.add(new Event(blade.index, body));
        }
        for (Step step : steps) refreshStop(c, step);
        if (steps.stream().anyMatch(s -> s.denied)) { finish(c); return; }
        if (!c.paid) {
            boolean opportunity = steps.stream().anyMatch(s -> !s.denied && s.stop > 0 && s.to > s.from);
            // Every planning/permission callback must still agree with the original
            // preflight snapshot before the sole authorized resource transition.
            if (!opportunity || !session(c) || !pay(c)) {
                if (c.poisoned) finish(c);
                return;
            }
        }
        events.sort(Comparator.comparingDouble((Event e) -> e.body.contact.time())
                .thenComparingInt(e -> pierceable(c, e.body.entity) ? 1 : 0)
                .thenComparingInt(Event::blade).thenComparing(e -> e.body.entity.getUUID()));
        for (Event event : events) {
            if (!session(c)) break;
            Step step = steps.stream().filter(s -> s.blade.index == event.blade).findFirst().orElseThrow();
            Body body = event.body;
            refreshStop(c, step);
            if (step.denied) { finish(c); return; }
            if (body.contact.time() >= step.stop || !unchanged(body)) continue;
            Slot slot = slot(c, body.entity);
            if (slot == null || !pierceable(c, body.entity)) { step.stop = Math.min(step.stop, body.contact.time()); continue; }
            if (slot.attempted || slot.share.signum() <= 0 || !hostile(c.identity.owner(), slot.entity)) continue;
            if (!visible(c, step, body)) continue;
            // Spend the UUID allocation before callbacks; cancellation and iframe loss are permanent.
            slot.attempted = true; c.attempts++;
            strike(c, step, body, slot);
        }
        if (CONTEXTS.get(c.identity.owner().getUUID()) != c) return;
        if (!session(c)) { finish(c); return; }
        List<FrontPose> fronts = new ArrayList<>(2);
        for (Step step : steps) {
            refreshStop(c, step);
            if (step.denied) { finish(c); return; }
            double portion = Double.isFinite(step.stop) ? Math.clamp(step.stop, 0, 1) : 1;
            double distance = step.from + (step.to - step.from) * portion;
            int status = Double.isFinite(step.stop) ? BLOCKED : distance >= ShearWindbladeGeometry.MAX_TRANSLATION ? EXPIRED : TRAVELLING;
            step.blade.distance = distance; step.blade.stopped = status != TRAVELLING;
            fronts.add(new FrontPose(step.blade.index, step.previous, vertices(c, distance), status));
        }
        c.receipt = new Receipt(c.input.input(), c.paid, c.attempts, c.successes, c.targets.stream().map(s -> s.uuid).toList(),
                c.targets.stream().map(s -> s.share).toList(), fronts, c.impacts);
        send(c, FLYING, fronts, c.impacts);
        if (!checkWitness(c)) { finish(c); return; }
        // Leave the actual terminal front visible for one tick; explicit abort still ends immediately.
        if (c.blades[0].stopped && c.blades[1].stopped) c.finishedAt = now(c.identity.owner());
    }
    private static void strike(Context c, Step step, Body body, Slot slot) {
        ServerPlayer p = c.identity.owner(); LivingEntity target = slot.entity;
        BigDecimal before = health(target), shield = HighPowerCombat.shield(target); float absorption = target.getAbsorptionAmount();
        if (!checkWitness(c)) return;
        Emission emission = new Emission(slot, step, body, vec(body.contact.previousPoint()), c.witness);
        EmissionPermit permit = new EmissionPermit(c, emission); c.emitting = permit;
        boolean accepted;
        try { accepted = HighPowerCombat.attackShearWindblade(permit); }
        finally {
            c.emitting = null;
            // Cancellation or a late native observer can skip the bridge's final
            // permit guard. Observe its payer changes regardless of native outcome.
            checkWitness(c);
        }
        int status = REFUSED;
        if (accepted && identity(c, slot)) {
            if (health(target).compareTo(before) < 0) status = DAMAGED;
            else if (HighPowerCombat.shield(target).compareTo(shield) < 0 || target.getAbsorptionAmount() < absorption) status = ABSORBED;
        }
        if (status != REFUSED) c.successes++;
        if (session(c) && unchanged(body)) c.impacts.add(new Impact(step.blade.index, c.targets.indexOf(slot), vec(body.contact.point()), status,
                status == REFUSED ? -1 : target.getId()));
        // The next contact and final displayed frontier are rescanned against callback mutations.
        if (!c.poisoned && CONTEXTS.get(p.getUUID()) == c) refreshStop(c, step);
    }
    private static void refreshStop(Context c, Step step) {
        if (step.denied) return;
        Scan current = scan(c, step.from, step.to);
        if (current == null) { step.denied = true; step.stop = 0; return; }
        step.stop = Math.min(step.stop, current.solid);
        for (Body body : current.bodies) if (!pierceable(c, body.entity)) step.stop = Math.min(step.stop, body.contact.time());
    }
    private static boolean visible(Context c, Step step, Body expected) {
        if (step.denied || expected.contact.time() >= step.stop || !unchanged(expected)) return false;
        Scan current = scan(c, step.from, step.to);
        if (current == null || expected.contact.time() >= current.solid) return false;
        boolean found = false;
        for (Body body : current.bodies) {
            if (body.contact.time() <= expected.contact.time() && !pierceable(c, body.entity)) return false;
            if (body.entity == expected.entity && body.bounds.equals(expected.bounds) && body.contact.equals(expected.contact)) found = true;
        }
        return found;
    }

    /** Exact chord sweep is the narrow phase; the enclosing box only gathers resident bodies and solids. */
    private static Scan scan(Context c, double from, double to) {
        ServerPlayer p = c.identity.owner();
        if (!checkWitness(c)) return null;
        AABB sweep;
        try { sweep = box(ShearWindbladeGeometry.sweptBounds(c.frame, c.plane, from, to)); }
        catch (IllegalArgumentException outside) { return null; }
        if (!permittedVolume(c, sweep)) return null;
        double solid = Double.POSITIVE_INFINITY;
        for (var shape : c.identity.level().getBlockCollisions(p, sweep)) for (AABB obstacle : shape.toAabbs()) {
            if (!checkWitness(c)) return null;
            var hit = ShearWindbladeGeometry.earliestContact(c.frame, c.plane, from, to, bounds(obstacle));
            if (hit.isPresent()) solid = Math.min(solid, hit.get().time());
        }
        AABB query = new AABB(Math.nextDown(sweep.minX), Math.nextDown(sweep.minY), Math.nextDown(sweep.minZ),
                Math.nextUp(sweep.maxX), Math.nextUp(sweep.maxY), Math.nextUp(sweep.maxZ));
        List<Entity> candidates = new ArrayList<>();
        c.identity.level().getEntities(EntityTypeTest.forClass(Entity.class), query,
                e -> {
                    boolean physical = e != p && !e.isRemoved() && !e.isSpectator()
                            && (e.isPickable() || e.canBeCollidedWith() || e instanceof LivingEntity);
                    return checkWitness(c) && physical;
                },
                candidates, ShearWindbladeRules.SCAN_LIMIT + 1);
        if (!checkWitness(c) || candidates.size() > ShearWindbladeRules.SCAN_LIMIT) return null;
        List<Body> bodies = new ArrayList<>();
        for (Entity entity : candidates) {
            if (!finite(entity.position()) || !finiteBox(entity.getBoundingBox())) return null;
            var contact = ShearWindbladeGeometry.earliestContact(c.frame, c.plane, from, to, bounds(entity.getBoundingBox()));
            // A solid at exactly the same contact time always wins.
            if (contact.isPresent() && contact.get().time() < solid)
                bodies.add(new Body(entity, entity.position(), entity.getBoundingBox(), contact.get()));
        }
        return checkWitness(c) ? new Scan(List.copyOf(bodies), solid, List.copyOf(candidates)) : null;
    }
    private static boolean pierceable(Context c, Entity entity) {
        Slot slot = slot(c, entity);
        if (slot == null || !identity(c, slot) || slot.entity.noPhysics || slot.entity.getPersistentData().getBoolean("academy:protected")
                || c.identity.owner().isAlliedTo(slot.entity) || slot.entity.isAlliedTo(c.identity.owner())) return false;
        return hostile(c.identity.owner(), slot.entity) || slot.attempted && !slot.entity.isAlive();
    }
    private static Slot slot(Context c, Entity entity) {
        for (Slot slot : c.targets) if (slot.entity == entity && slot.uuid.equals(entity.getUUID()) && slot.id == entity.getId()) return slot;
        return null;
    }
    private static boolean identity(Context c, Slot slot) {
        return slot.entity.level() == c.identity.level() && !slot.entity.isRemoved() && slot.uuid.equals(slot.entity.getUUID())
                && slot.id == slot.entity.getId() && c.identity.level().getEntity(slot.uuid) == slot.entity
                && c.identity.level().getEntity(slot.id) == slot.entity;
    }
    private static boolean unchanged(Body body) {
        return body.position.equals(body.entity.position()) && body.bounds.equals(body.entity.getBoundingBox()) && !body.entity.isRemoved();
    }
    /** The same exact native hostile whitelist as NEW411; no flight or force extension. */
    static boolean hostile(ServerPlayer p, LivingEntity target) {
        Context c = owned(p);
        if (c == null) return DeflectionStrike.hostile(p, target);
        if (!checkWitness(c)) return false;
        boolean result = HighPowerCombat.hostileTarget(target) && target.level() == p.level() && target.isAlive() && !target.isRemoved()
                && !target.isSpectator() && !target.isInvulnerable() && HighPowerCombat.supported(target)
                && HighPowerCombat.unsupportedReason(target).isEmpty()
                && p.serverLevel().getEntity(target.getUUID()) == target && p.serverLevel().getEntity(target.getId()) == target
                && !p.isAlliedTo(target) && !target.isAlliedTo(p) && !target.noPhysics
                && !target.getPersistentData().getBoolean("academy:protected") && finite(target.position()) && finite(target.getDeltaMovement())
                && permittedVolume(c, target.getBoundingBox());
        boolean intact = checkWitness(c);
        return result && intact;
    }
    private static boolean baseCanAct(ServerPlayer p) {
        if (!thread(p)) return false; var state = AbilityStorage.get(p);
        return p.connection != null && !p.hasDisconnected() && HighPowerResources.ready(p)
                && !p.getAbilities().instabuild && !p.getAbilities().invulnerable && !p.noPhysics && !p.isPassenger() && !p.isSleeping()
                && state.activated && state.overloadFine && !state.interfering && state.learned(ID) && state.learned(DeflectionStrike.ID)
                && state.category.equals("vecmanip") && state.level >= 2 && state.level <= 5
                && HighPowerResources.effectiveLevel(p) >= 2 && SkillCatalog.enabled("vecmanip", ID)
                && HighPowerCombat.unsupportedReason(p).isEmpty() && !AbilityConsumption.busy(p) && !HighPowerResources.hasReservation(p)
                && finite(p.position()) && finite(p.getEyePosition()) && finite(p.getLookAngle()) && p.getLookAngle().lengthSqr() > .5
                && finite(p.getDeltaMovement()) && Float.isFinite(p.getYRot()) && Float.isFinite(p.getXRot());
    }
    private static boolean session(Context c) {
        if (!checkWitness(c)) return false;
        ServerPlayer p = c.identity.owner();
        // canUse is deliberately absent: paying this cast's own cooldown does not abort its fronts.
        boolean valid = baseCanAct(p) && permittedVolume(c, p.getBoundingBox());
        boolean intact = checkWitness(c);
        return valid && intact;
    }
    private static Witness captureWitness(Context c) {
        ServerPlayer p = c.identity.owner(); CompoundTag ledger = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
        return new Witness(now(p), ledger, Double.doubleToLongBits(ledger.getDouble("cp")), ledger.getTagType("cp"),
                ledger.getLong(CLOCK), ledger.getTagType(CLOCK), c.identity.state().cooldowns.getOrDefault(ID, 0),
                p.position(), p.getEyePosition(), p.getLookAngle(), c.identity.state().level,
                Double.doubleToLongBits(c.identity.state().levelExperience));
    }
    /** Pure payer/owner observations. Failure permanently retires the original cast authority. */
    private static boolean checkWitness(Context c) {
        if (c.poisoned || c.witness == null) return false;
        Witness w = c.witness; ServerPlayer p = c.identity.owner(); var state = c.identity.state();
        boolean valid = CONTEXTS.get(p.getUUID()) == c && c.identity.owns(p) && HighPowerBracerSession.authenticated(p, c.input)
                && now(p) == w.tick && p.getPersistentData().getCompound(HighPowerResources.DATA_KEY) == w.ledger
                && w.ledger.getTagType("cp") == w.balanceType && Double.doubleToLongBits(w.ledger.getDouble("cp")) == w.balance
                && w.ledger.getTagType(CLOCK) == w.clockType && w.ledger.getLong(CLOCK) == w.clock
                && state.cooldowns.getOrDefault(ID, 0) == w.cooldown
                && (!c.paid || c.paidLedger == w.ledger && c.paidDeadline == w.clock)
                && p.position().equals(w.position) && p.getEyePosition().equals(w.eye) && p.getLookAngle().equals(w.look)
                && state.level == w.level && Double.doubleToLongBits(state.levelExperience) == w.experience
                && state.activated && state.overloadFine && !state.interfering && state.learned(ID) && state.learned(DeflectionStrike.ID)
                && state.category.equals("vecmanip") && state.level >= 2 && state.level <= 5
                && c.preset == state.presets.current() && c.revision == state.presets.revision()
                && ID.equals(state.presets.currentSkill(c.input.slot()))
                && !p.noPhysics && !p.isPassenger() && !p.isSleeping() && !p.getAbilities().instabuild && !p.getAbilities().invulnerable
                && !AbilityConsumption.busy(p) && !HighPowerResources.hasReservation(p);
        if (valid) {
            try { valid = c.growth != null && c.growth.compareTo(HighPowerGrowth.values(p).position()) == 0; }
            catch (IllegalArgumentException | IllegalStateException changed) { valid = false; }
        }
        if (!valid) c.poisoned = true;
        return valid;
    }
    /** Only the controller's own one-time debit may advance the expected payer values. */
    private static boolean pay(Context c) {
        if (c.paid || !checkWitness(c)) return false;
        ServerPlayer p = c.identity.owner(); Witness before = c.witness;
        double cost = ShearWindbladeRules.cpCost(c.growth), expected = HighPowerResources.cp(p) - cost;
        int cooldown = ShearWindbladeRules.cooldownTicks(c.growth);
        if (!checkWitness(c)) return false;
        if (!HighPowerResources.trySpend(p, ID, cost, cooldown)) { checkWitness(c); return false; }
        c.paid = true; c.paidLedger = before.ledger; c.paidDeadline = before.tick + cooldown;
        c.witness = new Witness(before.tick, before.ledger, Double.doubleToLongBits(expected), Tag.TAG_DOUBLE,
                c.paidDeadline, Tag.TAG_LONG, cooldown, before.position, before.eye, before.look, before.level, before.experience);
        if (!checkWitness(c)) return false;
        stamp(p); HighPowerNaturalRecovery.offensiveAction(p);
        return checkWitness(c);
    }
    /** Bracket each native permission call instead of hiding several callbacks in one volume result. */
    private static boolean permittedVolume(Context c, AABB box) {
        if (!checkWitness(c) || !finiteBox(box)) return false;
        ServerPlayer p = c.identity.owner(); var world = c.identity.level();
        if (!world.getWorldBorder().isWithinBounds(box) || box.minY < world.getMinBuildHeight() || box.maxY > world.getMaxBuildHeight()) return false;
        int x0 = (int)Math.floor(box.minX), x1 = (int)Math.floor(box.maxX), y0 = (int)Math.floor(box.minY), y1 = (int)Math.floor(box.maxY),
                z0 = (int)Math.floor(box.minZ), z1 = (int)Math.floor(box.maxZ);
        if ((long)(x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 12000) return false;
        for (int x = x0 >> 4; x <= x1 >> 4; x++) for (int z = z0 >> 4; z <= z1 >> 4; z++)
            if (world.getChunkSource().getChunkNow(x, z) == null || !checkWitness(c)) return false;
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            if (!checkWitness(c)) return false;
            boolean allowed = world.mayInteract(p, new BlockPos(x, y, z));
            boolean intact = checkWitness(c);
            if (!allowed || !intact) return false;
        }
        return checkWitness(c);
    }
    private static boolean validateIdle(Context c) {
        if (c.processing) return checkWitness(c);
        ServerPlayer p = c.identity.owner(); c.processing = true; PROCESSING.add(p); c.witness = captureWitness(c);
        try { return session(c); }
        finally { c.witness = null; c.processing = false; PROCESSING.remove(p); }
    }
    private static HighPowerInput.Input claim(ServerPlayer p, String wire) {
        HighPowerInput.Input input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return null;
        Watermark prior = INPUTS.get(p.getUUID());
        if (prior != null && prior.epoch == input.epoch() && input.input() <= prior.input) return null;
        INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        return ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot())) ? input : null;
    }
    private static boolean fenceReady(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (root.contains(DATA) && !root.contains(DATA, Tag.TAG_COMPOUND)) return false;
        CompoundTag data = root.getCompound(DATA);
        if (data.contains(PHYSICAL_AT) && !data.contains(PHYSICAL_AT, Tag.TAG_LONG)) return false;
        return ShearWindbladeRules.ready(data.contains(PHYSICAL_AT) ? data.getLong(PHYSICAL_AT) : -1, now(p), ShearWindbladeRules.CAST_FENCE);
    }
    private static void stamp(ServerPlayer p) {
        CompoundTag data = p.getPersistentData().getCompound(DATA); data.putLong(PHYSICAL_AT, now(p)); p.getPersistentData().put(DATA, data);
    }
    public static boolean abort(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire); if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Context c = owned(p);
        boolean owns = c != null && c.input.epoch() == input.epoch() && c.input.slot() == input.slot() && input.input() >= c.input.input();
        if (!owns && !ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))) return false;
        Watermark prior = INPUTS.get(p.getUUID());
        boolean retired = prior == null || prior.epoch != input.epoch() || input.input() > prior.input;
        if (retired) INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        if (owns) finish(c); return owns || retired;
    }
    public static void abort(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c); }
    public static void remove(ServerPlayer p) { if (thread(p)) { abort(p); INPUTS.remove(p.getUUID()); PREPARATIONS.remove(p); } }
    public static void clear() { for (Context c : new ArrayList<>(CONTEXTS.values())) finish(c); INPUTS.clear(); PREPARATIONS.clear(); PROCESSING.clear(); }
    public static boolean active(ServerPlayer p) { Context c = owned(p); if (c != null && !validateIdle(c)) { finish(c); return false; } return c != null; }
    public static Receipt receipt(ServerPlayer p) { Context c = owned(p); return c == null ? null : c.receipt; }
    private static boolean refuse(Context c, String reason) { send(c, ENDED, List.of(), List.of()); feedback(c.identity.owner(), reason); return false; }
    private static void feedback(ServerPlayer p, String reason) {
        if (HighPowerCombat.unsupportedReason(p).isPresent()) reason = "unsupported";
        p.displayClientMessage(Component.translatable("message.academy.highpower.shear_windblade." + reason), true);
    }
    private static void finish(Context c) {
        if (!CONTEXTS.remove(c.identity.owner().getUUID(), c)) return;
        c.emitting = null; send(c, ENDED, List.of(), List.of()); c.audience.clear();
    }
    private static void send(Context c, int phase, List<FrontPose> fronts, List<Impact> impacts) {
        ServerPlayer owner = c.identity.owner();
        Visual visual = new Visual(owner.getId(), owner.getUUID(), c.input.epoch(), c.input.input(), c.identity.token(), ++c.sequence,
                phase, c.crouch, phase == ENDED ? Vec3.ZERO : c.origin, fronts, impacts);
        c.audience.add(owner);
        if (phase == FLYING) for (ServerPlayer observer : c.identity.level().players()) if (observer.position().distanceToSqr(c.origin) <= 96 * 96) c.audience.add(observer);
        for (ServerPlayer observer : c.audience) if (observer.connection != null && !observer.hasDisconnected()
                && observer.serverLevel() == c.identity.level()) PacketDistributor.sendToPlayer(observer, visual);
    }
    private static Context owned(ServerPlayer p) { if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null; }
    private static boolean thread(ServerPlayer p) { return p != null && p.serverLevel().getServer().isSameThread(); }
    private static long now(ServerPlayer p) { return p.serverLevel().getGameTime(); }
    private static BigDecimal health(LivingEntity target) { return HighPowerCombat.enabled(target) ? new BigDecimal(HighPowerCombat.healthText(target)) : BigDecimal.valueOf(target.getHealth()); }
    private static boolean finite(Vec3 v) { return v != null && point(v).finite(); }
    private static boolean finiteBox(AABB box) { return bounds(box).finite(); }
    private static Point point(Vec3 v) { return new Point(v.x, v.y, v.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private static Box bounds(AABB b) { return new Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ); }
    private static AABB box(Box b) { return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()); }
    private static List<Vec3> vertices(Context c, double distance) { return ShearWindbladeGeometry.front(c.frame, c.plane, distance).stream().map(ShearWindblade::vec).toList(); }
    private static void writeVec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 readVec(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    private static List<Vec3> readVertices(RegistryFriendlyByteBuf b) { List<Vec3> result = new ArrayList<>(9); for (int i = 0; i < 9; i++) result.add(readVec(b)); return result; }
    private static List<FrontPose> readFronts(RegistryFriendlyByteBuf b) {
        int count = b.readUnsignedByte(); if (count > 2) throw new DecoderException("NEW421 front limit");
        List<FrontPose> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(new FrontPose(b.readUnsignedByte(), readVertices(b), readVertices(b), b.readUnsignedByte()));
        return result;
    }
    private static List<Impact> readImpacts(RegistryFriendlyByteBuf b) {
        int count = b.readUnsignedByte(); if (count > 4) throw new DecoderException("NEW421 impact limit");
        List<Impact> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(new Impact(b.readUnsignedByte(), b.readUnsignedByte(), readVec(b), b.readUnsignedByte(), b.readInt()));
        return result;
    }
    public record Visual(int owner, UUID ownerUuid, long epoch, long input, long token, long sequence, int phase,
                         boolean crouch, Vec3 origin, List<FrontPose> fronts, List<Impact> impacts) implements CustomPacketPayload {
        public Visual {
            fronts = List.copyOf(fronts); impacts = List.copyOf(impacts);
            if (owner < 0 || ownerUuid == null || epoch <= 0 || input <= 0 || token <= 0 || sequence <= 0 || sequence > 32
                    || phase < FLYING || phase > ENDED || !finite(origin) || fronts.size() > 2 || impacts.size() > 4
                    || fronts.stream().map(FrontPose::index).distinct().count() != fronts.size()
                    || impacts.stream().map(Impact::slot).distinct().count() != impacts.size()
                    || phase == ENDED && (!fronts.isEmpty() || !impacts.isEmpty() || !origin.equals(Vec3.ZERO)))
                throw new IllegalArgumentException("Invalid bounded NEW421 visual");
            if (phase == FLYING) {
                for (FrontPose front : fronts) for (int i = 0; i < 9; i++)
                    if (front.previous.get(i).distanceToSqr(origin) > 24 * 24 || front.current.get(i).distanceToSqr(origin) > 24 * 24
                            || front.previous.get(i).distanceTo(front.current.get(i)) > ShearWindbladeRules.STEP + 1e-7)
                        throw new IllegalArgumentException("NEW421 visual escaped its finite front");
                for (Impact impact : impacts) if (impact.point.distanceToSqr(origin) > 24 * 24) throw new IllegalArgumentException("NEW421 impact range");
            }
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "shear_windblade_visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.owner); b.writeUUID(v.ownerUuid); b.writeLong(v.epoch); b.writeLong(v.input); b.writeLong(v.token); b.writeLong(v.sequence);
            b.writeByte(v.phase); b.writeBoolean(v.crouch); writeVec(b, v.origin); b.writeByte(v.fronts.size());
            for (FrontPose front : v.fronts) { b.writeByte(front.index); front.previous.forEach(p -> writeVec(b, p)); front.current.forEach(p -> writeVec(b, p)); b.writeByte(front.status); }
            b.writeByte(v.impacts.size());
            for (Impact impact : v.impacts) { b.writeByte(impact.blade); b.writeByte(impact.slot); writeVec(b, impact.point); b.writeByte(impact.status); b.writeInt(impact.hitEntity); }
        }, b -> new Visual(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readUnsignedByte(),
                b.readBoolean(), readVec(b), readFronts(b), readImpacts(b)));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) {
            e.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet));
        }
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post e) { for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.level().getServer() == e.getServer()) tick(c.identity.owner()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { if (e.getOriginal() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void death(LivingDeathEvent e) { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { clear(); }
}
