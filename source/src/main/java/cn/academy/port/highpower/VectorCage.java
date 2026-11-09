package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.VectorCageGeometry;
import cn.academy.port.highpower.core.VectorCageRules;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.entity.EntityTypeTest;
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

/** NEW441: an immutable preview, one paid frozen field, and four real finite wall sweeps. */
@EventBusSubscriber(modid = "academy")
public final class VectorCage {
    public static final String ID = "vector_cage", PAYMENT_KEY = "academy:vector_cage_payment";
    public static final int PREVIEW = 0, STATE = 1, HIT = 2, ENDED = 3;
    public static final int REFUSED = 0, DAMAGED = 1, ABSORBED = 2;
    private static final String CLOCK = "next_" + ID;
    private static final int CAP = 256, INPUT_CAP = 4096;
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<ServerPlayer, Receipt> RECEIPTS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, PhysicalTick> PHYSICAL = new WeakHashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    public static Consumer<Rejection> clientRejectionReceiver = ignored -> {};
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.vector_cage");

    private record Watermark(ServerPlayer owner, long epoch, long input) {}
    private record Hold(LegacySingleKeyServerIdentity identity, HighPowerInput.Input input, int preset, long revision, long started, Context context) {}
    /** Only debt and the exact CP writer receipt persist. No geometry, target, input or cast can resume. */
    private record Payment(int request, int cooldown, long receipt, long resourceUntil) {}
    private static final class PhysicalTick {
        final int tick;
        final Set<UUID> players = new HashSet<>();
        final Map<UUID, Integer> starts = new HashMap<>();
        final Set<LivingEntity> attempted = Collections.newSetFromMap(new IdentityHashMap<>());
        PhysicalTick(int tick) { this.tick = tick; }
    }
    public record Receipt(long input, long token, boolean paid, int admitted, int phase, String reason, boolean low) {}
    public record HitReceipt(int pulse, int target, int wall, long tick, Vec3 origin, Vec3 contact, int status,
                             UUID victim, BigDecimal damage, Vec3 velocityDelta) {}
    private record Source(Vec3 position, Vec3 eye, Vec3 velocity, AABB bounds, Pose pose,
                          float yaw, float pitch, float fall, boolean grounded, boolean crouch) {
        static Source capture(ServerPlayer p) { return new Source(p.position(), p.getEyePosition(), p.getDeltaMovement(), p.getBoundingBox(),
                p.getPose(), p.getYRot(), p.getXRot(), p.fallDistance, p.onGround(), p.isShiftKeyDown()); }
        boolean matches(ServerPlayer p) { return position.equals(p.position()) && eye.equals(p.getEyePosition()) && velocity.equals(p.getDeltaMovement())
                && bounds.equals(p.getBoundingBox()) && pose == p.getPose() && yaw == p.getYRot() && pitch == p.getXRot()
                && fall == p.fallDistance && grounded == p.onGround() && crouch == p.isShiftKeyDown(); }
    }
    private static final class TargetRef {
        final LivingEntity entity; final UUID uuid; final int id;
        final boolean[] retired = new boolean[4];
        long lastAttempt = -1; boolean lost;
        TargetRef(LivingEntity entity) { this.entity = entity; uuid = entity.getUUID(); id = entity.getId(); }
        boolean identity(Context c) { return !lost && entity.level() == c.identity.level() && !entity.isRemoved()
                && uuid.equals(entity.getUUID()) && id == entity.getId() && c.identity.level().getEntity(uuid) == entity
                && c.identity.level().getEntity(id) == entity; }
    }
    /** Target kinematics are captured afresh for this one synchronous native attempt. */
    private static final class Body {
        final TargetRef ref; final Vec3 position, initialVelocity; final AABB bounds, deathBounds;
        final Pose pose; final boolean grounded; final double resistance;
        Vec3 allowedVelocity; boolean ownLethal;
        Body(TargetRef ref) {
            this.ref = ref; LivingEntity t = ref.entity; position = t.position(); initialVelocity = allowedVelocity = t.getDeltaMovement();
            bounds = t.getBoundingBox(); deathBounds = t.getDimensions(Pose.DYING).makeBoundingBox(position); pose = t.getPose();
            grounded = t.onGround(); resistance = t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        }
        boolean same(Context c, boolean shieldBranch) {
            LivingEntity t = ref.entity;
            return ref.identity(c) && position.equals(t.position()) && (bounds.equals(t.getBoundingBox()) && pose == t.getPose()
                    || ownLethal && !t.isAlive() && t.getPose() == Pose.DYING && deathBounds.equals(t.getBoundingBox()))
                    && (shieldBranch || allowedVelocity.equals(t.getDeltaMovement())) && grounded == t.onGround()
                    && resistance == t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        }
    }
    /** A callback cannot silently replace resources, presets, growth, owner kinematics or input authority. */
    private static final class Transaction {
        final Context context; final Source source; final long tick;
        final CompoundTag ledger, ledgerCopy; final Tag payment;
        final int cooldown; final Watermark watermark; final HighPowerGrowth.Values growth;
        Transaction(Context c) {
            context = c; ServerPlayer p = c.identity.owner(); source = Source.capture(p); tick = now(p);
            ledger = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY); ledgerCopy = ledger.copy();
            payment = p.getPersistentData().contains(PAYMENT_KEY) ? p.getPersistentData().get(PAYMENT_KEY).copy() : null;
            cooldown = c.identity.state().cooldowns.getOrDefault(ID, 0); watermark = INPUTS.get(p.getUUID()); growth = HighPowerGrowth.values(p);
        }
        boolean matches() {
            ServerPlayer p = context.identity.owner();
            return session(context) && now(p) == tick && source.matches(p) && INPUTS.get(p.getUUID()) == watermark
                    && p.getPersistentData().getCompound(HighPowerResources.DATA_KEY) == ledger && ledger.equals(ledgerCopy)
                    && Objects.equals(p.getPersistentData().get(PAYMENT_KEY), payment)
                    && context.identity.state().cooldowns.getOrDefault(ID, 0) == cooldown && growth.equals(HighPowerGrowth.values(p));
        }
    }
    private static final class Context {
        final LegacySingleKeyServerIdentity identity; final HighPowerInput.Input input; final int preset;
        final long revision, marked; final boolean low;
        final Set<ServerPlayer> audience = new LinkedHashSet<>(); final List<HitReceipt> hits = new ArrayList<>(32);
        final VectorCageSlowdown.LeaseSet leases = new VectorCageSlowdown.LeaseSet();
        VectorCageWorld.Anchor anchor; List<TargetRef> targets = List.of(); List<BigDecimal> shares = List.of();
        BigDecimal growth; double cost; int cooldown; long started = -1, lastTick, sequence, paymentReceipt;
        boolean paid, paying, processing, closed, invalidated; Transaction transaction; EmissionPermit emitting; Visual lastVisual;
        Context(ServerPlayer p, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p)); this.input = input;
            preset = identity.state().presets.current(); revision = identity.state().presets.revision(); marked = lastTick = now(p); low = p.isShiftKeyDown();
        }
    }

    /** This private constructor is the only authority to spend a retired pulse/target share. */
    public static final class EmissionPermit {
        private final Context context; private final int pulse, slot; private final Body body;
        private final VectorCageGeometry.Contact contact; private final double from, to;
        private final Vec3 origin, endpoint, inward; private boolean consumed;
        Vec3 nativeDelta = Vec3.ZERO; boolean nativeClaimed, nativeLethal, shieldBlocked;
        private EmissionPermit(Context c, int pulse, int slot, Body body, VectorCageGeometry.Contact contact, double from, double to) {
            context = c; this.pulse = pulse; this.slot = slot; this.body = body; this.contact = contact; this.from = from; this.to = to;
            origin = VectorCageWorld.vec(contact.previousPoint()); endpoint = VectorCageWorld.vec(contact.point());
            inward = switch (contact.wall()) { case 0 -> new Vec3(1, 0, 0); case 1 -> new Vec3(-1, 0, 0); case 2 -> new Vec3(0, 0, 1); default -> new Vec3(0, 0, -1); };
        }
        ServerPlayer player() { return context.identity.owner(); }
        LivingEntity target() { return body.ref.entity; }
        BigDecimal damage() { return context.shares.get(pulse * context.targets.size() + slot); }
        Vec3 origin() { return origin; }
        Vec3 inward() { return inward; }
        Vec3 beforeVelocity() { return body.allowedVelocity; }
        boolean grounded() { return body.grounded; }
        double resistance() { return body.resistance; }
        boolean consume() { if (consumed || !valid()) return false; consumed = true; return true; }
        boolean claimNative() { if (nativeClaimed || !consumed || !nativeValid()) return false; nativeClaimed = true; return true; }
        void completeNative(Vec3 expected, Vec3 before) {
            if (expected != null && expected.equals(target().getDeltaMovement()) && before.equals(body.allowedVelocity)) {
                body.allowedVelocity = expected;
                if (nativeValid()) nativeDelta = expected.subtract(before);
            }
        }
        void completedLethal() { body.ownLethal = true; }
        boolean valid() { return valid(false); }
        boolean nativeValid() { return consumed && valid(nativeLethal); }
        private boolean valid(boolean lethal) {
            // Native shield .5 response precedes the later .4 hurt adapter. Only its
            // authenticated current container can suspend this exact velocity expectation.
            if (HighPowerCombat.vectorCageNativeShieldBlocked(target(), this)) shieldBlocked = true;
            if (context.emitting != this || !context.paid || !context.processing || !body.ref.retired[pulse]
                    || context.transaction == null || !context.transaction.matches() || damage().signum() <= 0
                    || !body.same(context, shieldBlocked) || !(lethal ? deathPermission(context, body) : VectorCageWorld.hostile(player(), target()))) return false;
            if (!field(context) || !VectorCageGeometry.contactWithinRange(contact, VectorCageWorld.point(context.anchor.castOrigin()), VectorCageWorld.point(player().getEyePosition()))) return false;
            var actual = VectorCageGeometry.earliestContact(context.anchor.frame(), from, to, VectorCageWorld.box(body.bounds));
            return actual.isPresent() && actual.get().equals(contact) && body.same(context, shieldBlocked) && context.transaction.matches();
        }
    }
    private VectorCage() {}

    /** Press only captures a fresh hold. Neither marking nor payment happens on key-down. */
    public static boolean start(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Watermark previous = INPUTS.get(p.getUUID());
        if (previous != null && previous.owner == p && previous.epoch == input.epoch() && previous.input >= input.input()) return false;
        if (previous == null && INPUTS.size() >= INPUT_CAP) return reject(p, input, "not_ready");
        INPUTS.put(p.getUUID(), new Watermark(p, input.epoch(), input.input()));
        Context current = owned(p);
        if (current != null && current.processing) { current.invalidated = true; return reject(p, input, "transaction_changed"); }
        if (!mapped(p, input.slot()) || !canAct(p) || nextToken == Long.MAX_VALUE || HOLDS.size() >= CAP) return reject(p, input, "not_ready");
        if (current != null && !session(current)) { finish(current, "session_changed"); current = null; }
        var state = AbilityStorage.get(p);
        HOLDS.put(p.getUUID(), new Hold(new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, state), input,
                state.presets.current(), state.presets.revision(), now(p), current));
        return true;
    }
    public static boolean release(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire);
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Hold hold = HOLDS.get(p.getUUID());
        if (hold == null || hold.identity.owner() != p || !hold.input.equals(input)) return false;
        HOLDS.remove(p.getUUID(), hold);
        Context current = owned(p);
        if (!holdValid(hold) || now(p) < hold.started) { if (current != null && current == hold.context) finish(current, "session_changed"); return reject(p, input, "not_ready"); }
        if (now(p) - hold.started >= 6) { if (current != null) finish(current, "cleared"); acknowledgeClear(p, input); return true; }
        if (current != null && current.processing) { current.invalidated = true; return reject(p, input, "transaction_changed"); }
        if (current != null && current.paid) { finish(current, "cancelled"); return true; }
        Payment payment = readPayment(p);
        if (payment == null || payment.request > 0 || payment.cooldown > 0 || !AbilityStorage.get(p).canUse(ID)) return reject(p, input, "cooldown");
        writePayment(p, new Payment(VectorCageRules.REQUEST_FENCE, payment.cooldown, payment.receipt, payment.resourceUntil)); holdDebt(p, 1);
        if (current == null) return mark(p, input);
        return confirm(current, input);
    }
    private static boolean mark(ServerPlayer p, HighPowerInput.Input input) {
        if (CONTEXTS.size() >= CAP || nextToken == Long.MAX_VALUE) return reject(p, input, "not_ready");
        Context c = new Context(p, input); CONTEXTS.put(p.getUUID(), c); c.processing = true; c.transaction = new Transaction(c);
        try {
            if (!c.transaction.matches()) return fail(c, input, "not_ready");
            c.anchor = VectorCageWorld.acquire(p);
            if (c.anchor == null || !c.transaction.matches() || !field(c)) return fail(c, input, "blocked");
            send(c, PREVIEW, -1, -1, Vec3.ZERO, REFUSED); record(c, PREVIEW, ""); return true;
        } catch (RuntimeException | LinkageError refusal) { return fail(c, input, "blocked"); }
        finally { c.transaction = null; c.processing = false; }
    }
    private static boolean confirm(Context c, HighPowerInput.Input request) {
        ServerPlayer p = c.identity.owner(); c.processing = true; c.transaction = new Transaction(c);
        try {
            if (!c.transaction.matches() || now(p) - c.marked >= VectorCageRules.PREVIEW_TICKS || !field(c)) return fail(c, request, "blocked");
            var growth = HighPowerGrowth.values(p); c.growth = growth.position(); c.cost = VectorCageRules.cpCost(c.growth); c.cooldown = VectorCageRules.cooldownTicks(c.growth);
            AABB bounds = VectorCageWorld.box(VectorCageGeometry.bounds(c.anchor.frame()));
            // Native entity collection uses open bounds; collect exact closed-face ties
            // without enlarging the authoritative finite wall contact geometry.
            AABB query = new AABB(Math.nextDown(bounds.minX), Math.nextDown(bounds.minY), Math.nextDown(bounds.minZ),
                    Math.nextUp(bounds.maxX), Math.nextUp(bounds.maxY), Math.nextUp(bounds.maxZ));
            List<Entity> raw = new ArrayList<>(VectorCageRules.SCAN_LIMIT + 1);
            c.identity.level().getEntities(EntityTypeTest.forClass(Entity.class), query, entity -> entity != p, raw, VectorCageRules.SCAN_LIMIT + 1);
            if (raw.size() > VectorCageRules.SCAN_LIMIT) return fail(c, request, "scan_refused");
            List<TargetRef> targets = new ArrayList<>();
            raw.sort(Comparator.comparingDouble((Entity e) -> e.position().distanceToSqr(VectorCageWorld.vec(c.anchor.frame().center()))).thenComparing(Entity::getUUID));
            Set<UUID> unique = new HashSet<>();
            for (Entity entity : raw) {
                if (!(entity instanceof LivingEntity living) || !VectorCageWorld.hostile(p, living)) continue;
                if (!unique.add(entity.getUUID())) return fail(c, request, "scan_refused");
                if (targets.size() < VectorCageRules.MAX_TARGETS) targets.add(new TargetRef(living));
            }
            if (targets.isEmpty()) return fail(c, request, "no_target");
            c.targets = List.copyOf(targets); c.shares = VectorCageRules.shares(growth.damage(), c.low, targets.size());
            List<Body> admission = targets.stream().map(Body::new).toList();
            if (!c.transaction.matches() || !field(c)) return fail(c, request, "transaction_changed");
            for (Body body : admission) if (!body.same(c, false) || !VectorCageWorld.hostile(p, body.ref.entity)) return fail(c, request, "target_changed");
            if (!c.transaction.matches() || !pay(c)) return fail(c, request, c.paid ? "commit_unconfirmed" : "not_ready");
            c.started = c.lastTick = now(p); c.transaction = new Transaction(c);
            if (!c.transaction.matches() || !field(c)) return fail(c, request, "commit_unconfirmed");
            for (Body body : admission) if (!body.same(c, false) || !VectorCageWorld.hostile(p, body.ref.entity)) return fail(c, request, "commit_unconfirmed");
            if (!c.transaction.matches()) return fail(c, request, "commit_unconfirmed");
            send(c, STATE, -1, -1, Vec3.ZERO, REFUSED); record(c, STATE, ""); sync(p); return true;
        } catch (RuntimeException | LinkageError failure) {
            LOG.warn("NEW441 ends without retry or refund; paid={}", c.paid, failure); return fail(c, request, c.paid ? "commit_unconfirmed" : "transaction_changed");
        } finally { c.transaction = null; c.processing = false; }
    }

    private static boolean pay(Context c) {
        ServerPlayer p = c.identity.owner(); Payment before = readPayment(p);
        if (c.paid || c.transaction == null || !c.transaction.matches() || before == null || before.cooldown > 0
                || before.receipt == Long.MAX_VALUE || before.request != VectorCageRules.REQUEST_FENCE || !resourceMatches(p, before.resourceUntil)
                || HighPowerResources.availableCp(p) < c.cost) return false;
        long tick = p.serverLevel().getGameTime(); if (tick < 0 || tick > Long.MAX_VALUE - c.cooldown) return false;
        CompoundTag ledger = c.transaction.ledger; Tag oldClock = ledger.contains(CLOCK) ? ledger.get(CLOCK).copy() : null;
        CompoundTag refused = ledger.copy(); refused.putLong(CLOCK, tick);
        CompoundTag expected = ledger.copy(); expected.putLong(CLOCK, tick + c.cooldown); expected.putDouble("cp", HighPowerResources.cp(p) - c.cost);
        boolean spent = false; c.paying = true;
        try {
            // Translate only this skill's certified writer clock when its independent native-player debt is zero.
            ledger.putLong(CLOCK, tick);
            if (!HighPowerResources.trySpend(p, ID, c.cost, c.cooldown)) return false;
            spent = c.paid = true;
            if (!session(c) || !c.transaction.source.matches(p) || now(p) != c.transaction.tick
                    || !c.transaction.growth.equals(HighPowerGrowth.values(p)) || INPUTS.get(p.getUUID()) != c.transaction.watermark
                    || p.getPersistentData().getCompound(HighPowerResources.DATA_KEY) != ledger || !ledger.equals(expected)
                    || !Objects.equals(p.getPersistentData().get(PAYMENT_KEY), c.transaction.payment)
                    || c.identity.state().cooldowns.getOrDefault(ID, 0) != c.cooldown) return false;
            c.paymentReceipt = before.receipt + 1;
            writePayment(p, new Payment(before.request, c.cooldown, c.paymentReceipt, tick + c.cooldown)); holdDebt(p, 2);
            HighPowerNaturalRecovery.offensiveAction(p); return true;
        } finally {
            c.paying = false;
            if (!spent && p.getPersistentData().getCompound(HighPowerResources.DATA_KEY) == ledger && ledger.equals(refused)) {
                if (oldClock == null) ledger.remove(CLOCK); else ledger.put(CLOCK, oldClock);
            }
        }
    }

    public static void tick(ServerPlayer p) {
        if (!thread(p)) return;
        Hold hold = HOLDS.get(p.getUUID());
        if (hold != null && hold.identity.owner() == p && (!holdValid(hold) || now(p) < hold.started || now(p) - hold.started >= 6)) {
            HOLDS.remove(p.getUUID(), hold); Context current = owned(p); if (current != null && current == hold.context) finish(current, "cleared");
            acknowledgeClear(p, hold.input);
        }
        Context c = owned(p); if (c == null || c.processing) return;
        long now = now(p); if (now == c.lastTick) return;
        if (!session(c) || now < c.lastTick || now - c.lastTick != 1) { finish(c, "session_changed"); return; }
        c.lastTick = now;
        c.processing = true; c.transaction = new Transaction(c);
        try {
            if (!c.transaction.matches() || !field(c)) { finish(c, "session_changed"); return; }
            if (!c.paid) { if (now - c.marked >= VectorCageRules.PREVIEW_TICKS) finish(c, "expired"); return; }
            long age = now - c.started;
            if (age >= VectorCageRules.LIFETIME) { finish(c, "completed"); return; }
            int pulse = VectorCageRules.sweepIndex(age);
            if (pulse >= 0) {
                double from = VectorCageRules.fromHalfWidth(age), to = VectorCageRules.toHalfWidth(age);
                for (int slot = 0; slot < c.targets.size(); slot++) {
                    if (!c.transaction.matches() || !field(c)) { finish(c, "transaction_changed"); return; }
                    TargetRef ref = c.targets.get(slot);
                    if (ref.retired[pulse] || ref.lost) continue;
                    if (!ref.identity(c) || !VectorCageWorld.hostile(p, ref.entity)) { ref.lost = true; continue; }
                    Body body = new Body(ref);
                    var contact = VectorCageGeometry.earliestContact(c.anchor.frame(), from, to, VectorCageWorld.box(body.bounds));
                    if (contact.isEmpty()) continue;
                    // Retire before every callback, including a rejected interval or another caster's same-tick claim.
                    ref.retired[pulse] = true;
                    if (ref.lastAttempt >= 0 && now - ref.lastAttempt < VectorCageRules.TARGET_FENCE
                            || !VectorCageGeometry.contactWithinRange(contact.get(), VectorCageWorld.point(c.anchor.castOrigin()), VectorCageWorld.point(p.getEyePosition()))) continue;
                    PhysicalTick physical = physicalTick(p);
                    if (physical.attempted.size() >= CAP * VectorCageRules.MAX_TARGETS || !physical.attempted.add(ref.entity)) continue;
                    ref.lastAttempt = now;
                    strike(c, pulse, slot, body, contact.get(), from, to);
                    if (!c.transaction.matches() || !field(c)) { finish(c, "transaction_changed"); return; }
                }
                if (VectorCageRules.sweepStep(age) == VectorCageRules.SHRINK_TICKS - 1)
                    for (TargetRef ref : c.targets) ref.retired[pulse] = true;
            }
            if (c.transaction.matches() && field(c)) send(c, STATE, pulse, -1, Vec3.ZERO, REFUSED);
            else finish(c, "transaction_changed");
        } catch (RuntimeException | LinkageError failure) { LOG.warn("NEW441 stops its remaining frozen shares", failure); finish(c, "transaction_changed"); }
        finally { c.emitting = null; c.transaction = null; c.processing = false; }
    }

    private static void strike(Context c, int pulse, int slot, Body body, VectorCageGeometry.Contact contact, double from, double to) {
        LivingEntity target = body.ref.entity;
        BigDecimal hp = health(target), shield = HighPowerCombat.shield(target); float absorption = target.getAbsorptionAmount();
        EmissionPermit permit = new EmissionPermit(c, pulse, slot, body, contact, from, to); c.emitting = permit;
        try {
            boolean accepted = HighPowerCombat.attackVectorCage(permit);
            int status = REFUSED;
            if (accepted && body.same(c, permit.shieldBlocked) && c.transaction.matches() && field(c)) {
                if (health(target).compareTo(hp) < 0) status = DAMAGED;
                else if (HighPowerCombat.shield(target).compareTo(shield) < 0 || target.getAbsorptionAmount() < absorption) status = ABSORBED;
            }
            if (status == REFUSED) return;
            c.hits.add(new HitReceipt(pulse, slot, contact.wall(), now(c.identity.owner()), permit.origin, permit.endpoint, status,
                    body.ref.uuid, permit.damage(), permit.nativeDelta));
            send(c, HIT, pulse, contact.wall(), permit.endpoint, status);
            if (c.low && !permit.shieldBlocked && target.isAlive() && permit.valid())
                c.leases.apply(target, c.identity.owner(), () -> permit.valid() && !permit.shieldBlocked);
        } finally { c.emitting = null; }
    }

    private static boolean deathPermission(Context c, Body body) {
        LivingEntity target = body.ref.entity; ServerPlayer p = c.identity.owner();
        return !target.isAlive() && target.getHealth() == 0 && HighPowerCombat.supported(target)
                && HighPowerCombat.unsupportedReason(target).isEmpty() && !target.isInvulnerable() && !target.isSpectator()
                && !target.noPhysics && !target.isPassenger() && !target.isVehicle()
                && !target.getPersistentData().getBoolean("academy:protected") && !target.getPersistentData().getBoolean("academy:immovable")
                && !p.isAlliedTo(target) && !target.isAlliedTo(p) && VectorCageWorld.loaded(p, target.getBoundingBox());
    }
    private static boolean field(Context c) {
        return c.anchor != null && session(c) && VectorCageGeometry.withinRange(c.anchor.frame(), 4,
                VectorCageWorld.point(c.anchor.castOrigin()), VectorCageWorld.point(c.identity.owner().getEyePosition()))
                && c.anchor.proof().unchanged() && session(c);
    }
    private static boolean session(Context c) {
        ServerPlayer p = c.identity.owner(); Payment payment = readPayment(p);
        return !c.closed && !c.invalidated && CONTEXTS.get(p.getUUID()) == c && c.identity.owns(p)
                && HighPowerBracerSession.authenticated(p, c.input) && canAct(p) && c.preset == c.identity.state().presets.current()
                && c.revision == c.identity.state().presets.revision() && mapped(p, c.input.slot())
                && (c.growth == null || c.growth.compareTo(HighPowerGrowth.values(p).position()) == 0)
                && (!c.paid || c.paying || payment != null && payment.receipt == c.paymentReceipt && resourceMatches(p, payment.resourceUntil));
    }
    private static boolean canAct(ServerPlayer p) {
        if (!thread(p)) return false; var state = AbilityStorage.peek(p);
        return state != null && p.connection != null && !p.hasDisconnected() && p.isAlive() && !p.isRemoved() && !p.isSpectator()
                && !p.getAbilities().instabuild && !p.getAbilities().invulnerable && !p.getAbilities().flying && !p.noPhysics
                && !p.isPassenger() && !p.isVehicle() && !p.isSleeping() && !p.isFallFlying() && p.containerMenu == p.inventoryMenu
                && state.category.equals("vecmanip") && state.level >= 4 && state.level <= 5 && HighPowerResources.effectiveLevel(p) >= 4
                && state.activated && state.overloadFine && !state.interfering && !state.consumptionInProgress()
                && state.learned(ID) && state.learned(TrajectoryReturn.ID) && SkillCatalog.enabled("vecmanip", ID)
                && HighPowerResources.ready(p) && HighPowerCombat.unsupportedReason(p).isEmpty()
                && !AbilityConsumption.busy(p) && !HighPowerResources.hasReservation(p)
                && finite(p.position()) && finite(p.getEyePosition()) && finite(p.getDeltaMovement()) && finite(p.getLookAngle())
                && p.getLookAngle().lengthSqr() > .5 && Float.isFinite(p.getYRot()) && Float.isFinite(p.getXRot());
    }
    private static boolean holdValid(Hold hold) {
        ServerPlayer p = hold.identity.owner(); Watermark watermark = INPUTS.get(p.getUUID());
        return hold.identity.owns(p) && HighPowerBracerSession.authenticated(p, hold.input) && canAct(p) && mapped(p, hold.input.slot())
                && hold.preset == hold.identity.state().presets.current() && hold.revision == hold.identity.state().presets.revision()
                && watermark != null && watermark.owner == p && watermark.epoch == hold.input.epoch() && watermark.input == hold.input.input()
                && owned(p) == hold.context;
    }
    private static boolean mapped(ServerPlayer p, int slot) { return thread(p) && slot >= 0 && slot < 4 && ID.equals(AbilityStorage.get(p).presets.currentSkill(slot)); }
    private static Context owned(ServerPlayer p) { if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null; }
    public static boolean active(ServerPlayer p) { Context c = owned(p); return c != null && c.paid && !c.closed; }
    public static boolean preview(ServerPlayer p) { Context c = owned(p); return c != null && !c.paid && !c.closed; }
    public static int age(ServerPlayer p) { Context c = owned(p); return c == null ? -1 : (int)(now(p) - (c.paid ? c.started : c.marked)); }
    public static int targetCount(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.targets.size(); }
    public static int cooldownDebt(ServerPlayer p) { Payment payment = thread(p) ? readPayment(p) : null; return payment == null ? -1 : payment.cooldown; }
    public static Visual lastVisual(ServerPlayer p) { Context c = owned(p); return c == null ? null : c.lastVisual; }
    public static Receipt receipt(ServerPlayer p) { return RECEIPTS.get(p); }
    public static List<HitReceipt> receipts(ServerPlayer p) { Context c = owned(p); return c == null ? List.of() : List.copyOf(c.hits); }
    public static boolean abort(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire); if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Context c = owned(p); Hold hold = HOLDS.get(p.getUUID());
        if (c != null && c.processing) c.invalidated = true;
        boolean accepted = false;
        if (hold != null && hold.identity.owner() == p && hold.input.epoch() == input.epoch() && hold.input.slot() == input.slot() && hold.input.input() <= input.input()) {
            HOLDS.remove(p.getUUID(), hold); accepted = true;
        }
        if (c != null && c.input.epoch() == input.epoch() && c.input.slot() == input.slot() && c.input.input() <= input.input()) { finish(c, "cancelled"); accepted = true; }
        Watermark old = INPUTS.get(p.getUUID());
        if (accepted && (old == null || old.owner != p || old.epoch != input.epoch() || old.input < input.input())) INPUTS.put(p.getUUID(), new Watermark(p, input.epoch(), input.input()));
        return accepted;
    }
    public static void abort(ServerPlayer p) { if (!thread(p)) return; HOLDS.computeIfPresent(p.getUUID(), (id, hold) -> hold.identity.owner() == p ? null : hold); Context c = owned(p); if (c != null) finish(c, "cancelled"); }
    public static void cancelActive(ServerPlayer p) { abort(p); }
    public static void remove(ServerPlayer p) {
        if (!thread(p)) return; abort(p); INPUTS.computeIfPresent(p.getUUID(), (id, old) -> old.owner == p ? null : old); RECEIPTS.remove(p);
    }

    private static Payment readPayment(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(PAYMENT_KEY)) return resourceMatches(p, -1) ? new Payment(0, 0, 0, -1) : null;
        if (!root.contains(PAYMENT_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = root.getCompound(PAYMENT_KEY);
        if (!tag.getAllKeys().equals(Set.of("version", "request", "cooldown", "receipt", "resourceUntil"))
                || !tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != 1
                || !tag.contains("request", Tag.TAG_INT) || !tag.contains("cooldown", Tag.TAG_INT)
                || !tag.contains("receipt", Tag.TAG_LONG) || !tag.contains("resourceUntil", Tag.TAG_LONG)) return null;
        int request = tag.getInt("request"), cooldown = tag.getInt("cooldown"); long receipt = tag.getLong("receipt"), until = tag.getLong("resourceUntil");
        if (request < 0 || request > VectorCageRules.REQUEST_FENCE || cooldown < 0 || cooldown > 40 || receipt < 0 || until < -1 || until == Long.MAX_VALUE
                || receipt == 0 && (cooldown != 0 || until != -1) || receipt > 0 && until < 0) return null;
        return new Payment(request, cooldown, receipt, until);
    }
    private static boolean resourceMatches(ServerPlayer p, long expected) {
        if (!p.getPersistentData().contains(HighPowerResources.DATA_KEY, Tag.TAG_COMPOUND)) return false;
        CompoundTag ledger = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
        return expected == -1 ? !ledger.contains(CLOCK) : ledger.contains(CLOCK, Tag.TAG_LONG) && ledger.getLong(CLOCK) == expected;
    }
    private static void writePayment(ServerPlayer p, Payment payment) {
        CompoundTag tag = new CompoundTag(); tag.putInt("version", 1); tag.putInt("request", payment.request); tag.putInt("cooldown", payment.cooldown);
        tag.putLong("receipt", payment.receipt); tag.putLong("resourceUntil", payment.resourceUntil); p.getPersistentData().put(PAYMENT_KEY, tag);
    }
    private static PhysicalTick physicalTick(ServerPlayer p) {
        PhysicalTick tick = PHYSICAL.get(p.server);
        if (tick == null || tick.tick != p.server.getTickCount()) { tick = new PhysicalTick(p.server.getTickCount()); PHYSICAL.put(p.server, tick); }
        return tick;
    }
    private static void holdDebt(ServerPlayer p, int fields) { PhysicalTick tick = physicalTick(p); if (!tick.players.contains(p.getUUID())) tick.starts.merge(p.getUUID(), fields, (a, b) -> a | b); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !thread(p) || p.connection == null || p.hasDisconnected() || p.isRemoved()
                || p.serverLevel().getEntity(p.getUUID()) != p) return;
        PhysicalTick tick = physicalTick(p); if (!tick.players.add(p.getUUID())) return;
        Payment payment = readPayment(p);
        if (payment != null && (payment.request > 0 || payment.cooldown > 0)) {
            int starts = tick.starts.getOrDefault(p.getUUID(), 0);
            writePayment(p, new Payment((starts & 1) == 0 ? Math.max(0, payment.request - 1) : payment.request,
                    (starts & 2) == 0 ? Math.max(0, payment.cooldown - 1) : payment.cooldown, payment.receipt, payment.resourceUntil));
        }
    }

    private static boolean fail(Context c, HighPowerInput.Input request, String reason) { finish(c, reason); reject(c.identity.owner(), request, reason); return false; }
    private static boolean reject(ServerPlayer p, HighPowerInput.Input input, String reason) {
        if (p.connection != null && !p.hasDisconnected()) {
            p.displayClientMessage(Component.translatable("message.academy.highpower.vector_cage." + reason), true);
            acknowledgeClear(p, input);
        }
        return false;
    }
    private static void acknowledgeClear(ServerPlayer p, HighPowerInput.Input input) {
        if (p.connection != null && !p.hasDisconnected()) PacketDistributor.sendToPlayer(p, new Rejection(p.getId(), p.getUUID(), input.input(), input.epoch()));
    }
    private static void finish(Context c, String reason) {
        if (c.closed) return; c.closed = true; c.invalidated = true; c.emitting = null; c.leases.release();
        CONTEXTS.remove(c.identity.owner().getUUID(), c); record(c, ENDED, reason);
        try { send(c, ENDED, -1, -1, Vec3.ZERO, REFUSED); } catch (RuntimeException | LinkageError ignored) {}
    }
    private static void record(Context c, int phase, String reason) {
        ServerPlayer p = c.identity.owner();
        if (p.connection != null && !p.hasDisconnected() && !p.isRemoved() && p.server.getPlayerList().getPlayer(p.getUUID()) == p)
            RECEIPTS.put(p, new Receipt(c.input.input(), c.identity.token(), c.paid, c.targets.size(), phase, reason, c.low));
    }
    private static void send(Context c, int phase, int pulse, int wall, Vec3 contact, int status) {
        if (c.sequence >= 128) return; ServerPlayer p = c.identity.owner();
        Vec3 center = c.anchor == null ? p.position() : VectorCageWorld.vec(c.anchor.frame().center());
        int age = c.paid ? (int)Math.max(0, Math.min(VectorCageRules.LIFETIME, now(p) - c.started))
                : (int)Math.max(0, Math.min(VectorCageRules.PREVIEW_TICKS, now(p) - c.marked));
        double width = c.paid ? VectorCageRules.halfWidth(Math.min(VectorCageRules.LIFETIME - 1, age)) : 4;
        Visual packet = new Visual(p.getId(), p.getUUID(), c.input.input(), c.identity.token(), c.input.epoch(), ++c.sequence,
                phase, age, c.low, center, width, pulse, wall, contact, status); c.lastVisual = packet;
        c.audience.add(p);
        if (phase != PREVIEW && phase != ENDED) for (ServerPlayer observer : c.identity.level().players()) {
            if (c.audience.size() >= 32) break;
            if (!observer.hasDisconnected() && observer.position().distanceToSqr(center) <= 96 * 96) c.audience.add(observer);
        }
        for (ServerPlayer observer : c.audience) if (observer.connection != null && !observer.hasDisconnected() && observer.serverLevel() == c.identity.level())
            PacketDistributor.sendToPlayer(observer, packet);
    }
    private static boolean thread(ServerPlayer p) { return p != null && p.server != null && p.server.isSameThread(); }
    private static long now(ServerPlayer p) { return Integer.toUnsignedLong(p.server.getTickCount()); }
    private static boolean finite(Vec3 value) { return VectorCageWorld.finite(value); }
    private static BigDecimal health(LivingEntity target) { return HighPowerCombat.enabled(target) ? new BigDecimal(HighPowerCombat.healthText(target)) : BigDecimal.valueOf(target.getHealth()); }
    private static void sync(ServerPlayer p) { try { AbilityStorage.save(p); AcademyNetwork.sync(p); } catch (RuntimeException | LinkageError ignored) {} }
    private static void writeVec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 readVec(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }

    public record Visual(int entity, UUID owner, long input, long token, long epoch, long sequence, int phase, int age,
                         boolean low, Vec3 center, double halfWidth, int pulse, int wall, Vec3 contact, int status) implements CustomPacketPayload {
        public Visual {
            if (entity < 0 || owner == null || input <= 0 || token <= 0 || epoch <= 0 || sequence <= 0 || sequence > 128
                    || phase < PREVIEW || phase > ENDED || age < 0
                    || age > (phase == PREVIEW || phase == ENDED ? VectorCageRules.PREVIEW_TICKS : VectorCageRules.LIFETIME)
                    || !finite(center) || !finite(contact) || !Double.isFinite(halfWidth) || halfWidth < .25 || halfWidth > 4
                    || pulse < -1 || pulse >= 4 || wall < -1 || wall >= 4 || status < REFUSED || status > ABSORBED
                    || phase == HIT && (pulse < 0 || wall < 0 || status == REFUSED || center.distanceToSqr(contact) > 50)
                    || phase != HIT && (wall != -1 || status != REFUSED || !contact.equals(Vec3.ZERO))
                    || phase == PREVIEW && (halfWidth != 4 || pulse != -1)) throw new IllegalArgumentException("Invalid bounded NEW441 visual");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_vector_cage"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.token); b.writeLong(v.epoch); b.writeLong(v.sequence);
            b.writeByte(v.phase); b.writeInt(v.age); b.writeBoolean(v.low); writeVec(b, v.center); b.writeDouble(v.halfWidth);
            b.writeByte(v.pulse); b.writeByte(v.wall); writeVec(b, v.contact); b.writeByte(v.status);
        }, b -> new Visual(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readUnsignedByte(), b.readInt(),
                b.readBoolean(), readVec(b), b.readDouble(), b.readByte(), b.readByte(), readVec(b), b.readUnsignedByte()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Rejection(int entity, UUID owner, long input, long epoch) implements CustomPacketPayload {
        public Rejection { if (entity < 0 || owner == null || input <= 0 || epoch <= 0) throw new IllegalArgumentException("Invalid NEW441 rejection"); }
        public static final Type<Rejection> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "hp_vector_cage_rejected"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Rejection> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.epoch);
        }, b -> new Rejection(b.readInt(), b.readUUID(), b.readLong(), b.readLong()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD) public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) {
            e.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet));
            e.registrar("1").playToClient(Rejection.TYPE, Rejection.CODEC, (packet, context) -> clientRejectionReceiver.accept(packet));
        }
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event) {
        Set<ServerPlayer> players = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Context c : CONTEXTS.values()) if (c.identity.owner().server == event.getServer()) players.add(c.identity.owner());
        for (Hold hold : HOLDS.values()) if (hold.identity.owner().server == event.getServer()) players.add(hold.identity.owner());
        for (ServerPlayer p : players) tick(p);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void death(LivingDeathEvent e) { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void leave(EntityLeaveLevelEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) remove(p);
        for (Context c : CONTEXTS.values()) for (TargetRef ref : c.targets) if (ref.entity == e.getEntity()) ref.lost = true;
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void clone(PlayerEvent.Clone e) {
        if (!(e.getOriginal() instanceof ServerPlayer old)) return; remove(old);
        if (!(e.getEntity() instanceof ServerPlayer replacement) || !old.getUUID().equals(replacement.getUUID())) return;
        CompoundTag original = old.getPersistentData(), copy = replacement.getPersistentData();
        if (original.contains(PAYMENT_KEY)) copy.put(PAYMENT_KEY, original.get(PAYMENT_KEY).copy());
        Payment payment = readPayment(old);
        if (payment == null || !resourceMatches(old, payment.resourceUntil)) {
            CompoundTag invalid = new CompoundTag(); invalid.putInt("version", -1); copy.put(PAYMENT_KEY, invalid); return;
        }
        if (payment.receipt > 0 && (!copy.contains(HighPowerResources.DATA_KEY) || copy.contains(HighPowerResources.DATA_KEY, Tag.TAG_COMPOUND))) {
            CompoundTag ledger = copy.getCompound(HighPowerResources.DATA_KEY);
            if (!ledger.contains(CLOCK)) { ledger.putLong(CLOCK, payment.resourceUntil); copy.put(HighPowerResources.DATA_KEY, ledger); }
        }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e) { for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) finish(c, "server_stopping"); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) {
        for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.owner().server == e.getServer()) finish(c, "server_stopped");
        HOLDS.entrySet().removeIf(entry -> entry.getValue().identity.owner().server == e.getServer());
        INPUTS.entrySet().removeIf(entry -> entry.getValue().owner.server == e.getServer());
        RECEIPTS.keySet().removeIf(p -> p.server == e.getServer()); PHYSICAL.remove(e.getServer()); VectorCageNativeBridge.clear();
    }
}
