package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.DeflectionStrikeRules;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW411: one authenticated tap, a frozen straight path and two non-transferable native opportunities. */
@EventBusSubscriber(modid = "academy")
public final class DeflectionStrike {
    public static final String ID = "deflection_strike";
    public static final int FIRE = 0, ENDED = 1;
    public static final int REFUSED = 0, DAMAGED = 1, ABSORBED = 2, PUSHED = 3;
    private static final String CLOCK = "next_" + ID;
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<LivingEntity, Push> PUSHES = new IdentityHashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(long epoch, long input) {}
    private record Contact(Entity entity, Vec3 point, double distance) {}
    private record Scan(List<Contact> contacts, Vec3 stop) {}
    private record Target(LivingEntity entity, UUID uuid, int id, Vec3 position, AABB bounds, Vec3 contact) {
        static Target capture(LivingEntity entity, Vec3 contact) {
            return new Target(entity, entity.getUUID(), entity.getId(), entity.position(), entity.getBoundingBox(), contact);
        }
        boolean same(Context c) {
            return entity.level() == c.identity.level() && !entity.isRemoved() && uuid.equals(entity.getUUID()) && id == entity.getId()
                    && c.identity.level().getEntity(uuid) == entity && c.identity.level().getEntity(id) == entity
                    && position.equals(entity.position()) && bounds.equals(entity.getBoundingBox());
        }
    }
    public record Receipt(int slot, Vec3 origin, Vec3 end, int status, UUID victim,
                          BigDecimal damage, Vec3 velocityDelta, long input) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final int preset;
        final long revision, started;
        final Vec3 position, origin, axis;
        final float yaw, pitch;
        final boolean crouch;
        final List<Receipt> receipts = new ArrayList<>(2);
        final Set<UUID> attempted = new HashSet<>();
        final Set<ServerPlayer> audience = new LinkedHashSet<>();
        List<Target> targets = List.of();
        List<BigDecimal> shares = List.of();
        BigDecimal growth;
        Vec3 end, lastVisualPoint;
        CompoundTag ledger;
        long balance, clock, sequence;
        int cooldown;
        boolean paid, resolving;
        EmissionPermit emitting;
        Context(ServerPlayer p, HighPowerInput.Input input) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input; preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            started = now(p); position = p.position(); origin = p.getEyePosition(); axis = p.getLookAngle().normalize();
            yaw = p.getYRot(); pitch = p.getXRot(); crouch = p.isShiftKeyDown(); lastVisualPoint = origin;
        }
    }
    /** Constructible only while this controller owns the frozen slot. No public amount/source selector. */
    public static final class EmissionPermit {
        private final Context context;
        private final int slot;
        private boolean consumed;
        private EmissionPermit(Context context, int slot) { this.context = context; this.slot = slot; }
        ServerPlayer player() { return context.identity.owner(); }
        LivingEntity target() { return context.targets.get(slot).entity; }
        Vec3 origin() { return context.origin; }
        BigDecimal damage() { return context.shares.get(slot); }
        boolean consume() { if (consumed || !valid()) return false; consumed = true; return true; }
        boolean valid() { return !context.crouch && context.emitting == this && context.paid && context.resolving
                && context.attempted.contains(target().getUUID()) && validSlot(context, slot) && damage().signum() > 0; }
    }
    private static final class Push {
        final Context context;
        final int slot;
        final LivingEntity target;
        final Vec3 before;
        final double resistance, ratioX, ratioZ;
        final boolean grounded;
        final BigDecimal health, shield;
        final float absorption;
        LivingKnockBackEvent event;
        boolean poisoned;
        Push(Context context, int slot) {
            this.context = context; this.slot = slot; target = context.targets.get(slot).entity;
            before = target.getDeltaMovement(); resistance = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            grounded = target.onGround(); ratioX = -context.axis.x; ratioZ = -context.axis.z;
            health = health(target); shield = HighPowerCombat.shield(target); absorption = target.getAbsorptionAmount();
        }
        boolean valid() {
            return !poisoned && PUSHES.get(target) == this && context.crouch && context.resolving && context.paid
                    && validSlot(context, slot) && before.equals(target.getDeltaMovement()) && grounded == target.onGround()
                    && Double.isFinite(resistance) && resistance >= 0 && resistance <= 1
                    && resistance == target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)
                    && health.compareTo(health(target)) == 0 && shield.compareTo(HighPowerCombat.shield(target)) == 0
                    && absorption == target.getAbsorptionAmount();
        }
        boolean boundedEvent() {
            return event != null && Float.isFinite(event.getStrength()) && event.getStrength() >= 0
                    && event.getStrength() <= (float)DeflectionStrikeRules.KNOCKBACK
                    && event.getRatioX() == ratioX && event.getRatioZ() == ratioZ;
        }
    }
    private DeflectionStrike() {}

    public static boolean tap(ServerPlayer p, String wire) {
        Context previous = owned(p);
        if (previous != null && previous.resolving) return false;
        HighPowerInput.Input input = claim(p, wire);
        if (input == null || nextToken == Long.MAX_VALUE || !DeflectionStrikeRules.clock(now(p))) return false;
        Context c = new Context(p, input);
        if (!canAct(p) || !AbilityStorage.get(p).canUse(ID)) return refuse(c, "not_ready");
        if (previous != null) finish(previous, true);
        var endpoint = DeflectionStrikeRules.endpoint(point(c.origin), point(c.axis));
        if (endpoint == null || c.crouch && c.axis.x*c.axis.x+c.axis.z*c.axis.z < 1e-5F) return refuse(c, "no_target");
        c.end = vector(endpoint);
        Scan scan = scan(p, c.origin, c.end);
        if (scan == null) return refuse(c, "no_target");
        List<Target> targets = new ArrayList<>(2);
        for (Contact contact : scan.contacts) {
            if (!(contact.entity instanceof LivingEntity target) || !hostile(p, target)) break;
            targets.add(Target.capture(target, contact.point));
            if (targets.size() == DeflectionStrikeRules.MAX_TARGETS) break;
        }
        if (targets.isEmpty()) return refuse(c, "no_target");
        var growth = HighPowerGrowth.values(p);
        c.growth = growth.position(); c.targets = List.copyOf(targets);
        c.shares = DeflectionStrikeRules.shares(growth.damage(), targets.size(), c.crouch);
        CONTEXTS.put(p.getUUID(), c);
        c.resolving = true;
        try {
            for (int slot=0; slot<c.targets.size(); slot++) {
                if (!validCast(c)) break;
                Target target = c.targets.get(slot);
                if (!validSlot(c, slot) || c.attempted.contains(target.uuid) || !c.crouch && c.shares.get(slot).signum() == 0) continue;
                if (!c.paid) {
                    if (!HighPowerResources.trySpend(p, ID, DeflectionStrikeRules.cpCost(c.growth), DeflectionStrikeRules.cooldownTicks(c.growth))) break;
                    c.paid = true; c.ledger = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
                    c.balance = Double.doubleToLongBits(c.ledger.getDouble("cp")); c.clock = c.ledger.getLong(CLOCK);
                    c.cooldown = c.identity.state().cooldowns.getOrDefault(ID, 0);
                    HighPowerNaturalRecovery.offensiveAction(p);
                }
                if (!validSlot(c, slot)) continue;
                c.attempted.add(target.uuid);
                if (c.crouch) push(c, slot); else strike(c, slot);
            }
        } finally { c.emitting = null; c.resolving = false; }
        if (!c.paid) { finish(c, true); feedback(p, "not_ready"); }
        else if (!session(c)) finish(c, true);
        return c.paid;
    }

    private static void strike(Context c, int slot) {
        Target snapshot = c.targets.get(slot); LivingEntity target = snapshot.entity;
        BigDecimal before = health(target), shield = HighPowerCombat.shield(target); float absorption = target.getAbsorptionAmount();
        EmissionPermit permit = new EmissionPermit(c, slot); c.emitting = permit;
        boolean accepted;
        try { accepted = HighPowerCombat.attackDeflectionStrike(permit); } finally { c.emitting = null; }
        int status = REFUSED;
        if (accepted && snapshot.same(c)) {
            if (health(target).compareTo(before) < 0) status = DAMAGED;
            else if (HighPowerCombat.shield(target).compareTo(shield) < 0 || target.getAbsorptionAmount() < absorption) status = ABSORBED;
        }
        receipt(c, slot, status, status == REFUSED ? null : snapshot.uuid, Vec3.ZERO);
    }
    private static void push(Context c, int slot) {
        Push push = new Push(c, slot);
        if (PUSHES.containsKey(push.target)) return;
        PUSHES.put(push.target, push);
        int status = REFUSED; Vec3 delta = Vec3.ZERO;
        try {
            if (push.valid()) {
                push.target.knockback(DeflectionStrikeRules.KNOCKBACK, push.ratioX, push.ratioZ);
                // Observe the completed native call. Never fabricate an impact from an event or overwrite its velocity.
                if (!push.poisoned && push.event != null && !push.event.isCanceled() && push.boundedEvent()
                        && validSlot(c, slot) && push.grounded == push.target.onGround()
                        && push.resistance == push.target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)
                        && push.health.compareTo(health(push.target)) == 0 && push.shield.compareTo(HighPowerCombat.shield(push.target)) == 0
                        && push.absorption == push.target.getAbsorptionAmount()) {
                    double strength = push.event.getStrength() * (1 - push.resistance);
                    Vec3 force = new Vec3(push.ratioX, 0, push.ratioZ).normalize().scale(strength);
                    Vec3 expected = strength <= 0 ? push.before : new Vec3(push.before.x/2-force.x,
                            push.grounded ? Math.min(.4, push.before.y/2+strength) : push.before.y, push.before.z/2-force.z);
                    Vec3 observed = push.target.getDeltaMovement();
                    if (finite(observed) && expected.equals(observed) && !observed.equals(push.before)) {
                        status = PUSHED; delta = observed.subtract(push.before);
                    }
                }
            }
        } finally { PUSHES.remove(push.target, push); }
        receipt(c, slot, status, status == PUSHED ? push.target.getUUID() : null, delta);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void captureKnockback(LivingKnockBackEvent event) {
        Push push = PUSHES.get(event.getEntity()); if (push == null) return;
        if (push.event != null) {
            push.poisoned = true; push.event.setCanceled(true); event.setCanceled(true); return;
        }
        push.event = event;
        if (!push.valid() || event.getOriginalStrength() != (float)DeflectionStrikeRules.KNOCKBACK
                || event.getOriginalRatioX() != push.ratioX || event.getOriginalRatioZ() != push.ratioZ) {
            push.poisoned = true; event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void validateKnockback(LivingKnockBackEvent event) {
        Push push = PUSHES.get(event.getEntity()); if (push == null) return;
        if (push.event != event || !push.valid() || !push.boundedEvent()) { push.poisoned = true; event.setCanceled(true); }
    }

    private static boolean validSlot(Context c, int slot) {
        if (!validCast(c) || slot < 0 || slot >= c.targets.size()) return false;
        Target target = c.targets.get(slot);
        if (!target.same(c) || !hostile(c.identity.owner(), target.entity)) return false;
        return pathMatches(c, slot);
    }
    private static boolean pathMatches(Context c, int slot) {
        Target target = c.targets.get(slot);
        Scan current = scan(c.identity.owner(), c.origin, target.contact);
        if (current == null || current.stop.distanceToSqr(c.origin) < target.contact.distanceToSqr(c.origin)) return false;
        for (Contact contact : current.contacts) {
            // The ordered path ends at this body's contact. A later admitted body may
            // share that exact entry coordinate; it cannot retroactively block this slot.
            // Solid and nonhostile ties have already taken precedence in scan().
            if (contact.entity == target.entity) return true;
            // Only already admitted earlier bodies may be pierced; a newly arrived body never becomes a target.
            boolean earlier = false;
            for (int prior=0; prior<slot; prior++) {
                Target body = c.targets.get(prior);
                if (contact.entity == body.entity && body.same(c) && !body.entity.noPhysics
                        && !body.entity.getPersistentData().getBoolean("academy:protected")
                        && !c.identity.owner().isAlliedTo(body.entity) && !body.entity.isAlliedTo(c.identity.owner())
                        && (hostile(c.identity.owner(), body.entity) || c.attempted.contains(body.uuid) && !body.entity.isAlive())) earlier = true;
            }
            if (!earlier) return false;
        }
        return false;
    }
    private static boolean validCast(Context c) {
        ServerPlayer p = c.identity.owner();
        if (!session(c) || now(p) != c.started || !c.position.equals(p.position()) || !c.origin.equals(p.getEyePosition())
                || !c.axis.equals(p.getLookAngle().normalize()) || c.yaw != p.getYRot() || c.pitch != p.getXRot()
                || c.crouch != p.isShiftKeyDown() || c.growth == null || c.growth.compareTo(HighPowerGrowth.values(p).position()) != 0) return false;
        return !c.paid || p.getPersistentData().getCompound(HighPowerResources.DATA_KEY) == c.ledger
                && Double.doubleToLongBits(c.ledger.getDouble("cp")) == c.balance && c.ledger.getLong(CLOCK) == c.clock
                && c.identity.state().cooldowns.getOrDefault(ID, 0) == c.cooldown;
    }
    private static boolean session(Context c) {
        ServerPlayer p = c.identity.owner();
        return CONTEXTS.get(p.getUUID()) == c && c.identity.owns(p) && HighPowerBracerSession.authenticated(p, c.input) && canAct(p)
                && c.preset == c.identity.state().presets.current() && c.revision == c.identity.state().presets.revision()
                && ID.equals(c.identity.state().presets.currentSkill(c.input.slot()));
    }
    private static boolean canAct(ServerPlayer p) {
        if (!thread(p)) return false; var state = AbilityStorage.get(p);
        return p.connection != null && !p.hasDisconnected() && HighPowerResources.ready(p)
                && !p.getAbilities().instabuild && !p.getAbilities().invulnerable && !p.noPhysics && !p.isPassenger() && !p.isSleeping()
                && state.activated && state.overloadFine && !state.interfering && state.learned(ID) && state.category.equals("vecmanip")
                && state.level >= 1 && state.level <= 5 && HighPowerResources.effectiveLevel(p) >= 1 && SkillCatalog.enabled("vecmanip", ID)
                && HighPowerCombat.unsupportedReason(p).isEmpty() && !AbilityConsumption.busy(p) && !HighPowerResources.hasReservation(p)
                && finite(p.position()) && finite(p.getEyePosition()) && finite(p.getLookAngle()) && p.getLookAngle().lengthSqr() > .5
                && finite(p.getDeltaMovement()) && Float.isFinite(p.getYRot()) && Float.isFinite(p.getXRot())
                && DisplacedProjectionWorld.volume(p, p.getBoundingBox()).isEmpty();
    }
    /** Reuses the certified exact native hostile whitelist; players/friends/unknown classes remain physical blockers. */
    static boolean hostile(ServerPlayer p, LivingEntity target) {
        return target != null && DisplacedProjectionWorld.hostile(p, target) && !target.noPhysics
                && !target.getPersistentData().getBoolean("academy:protected") && finite(target.position()) && finite(target.getDeltaMovement());
    }

    /** Solid shapes and every physical body participate before the hostile whitelist is consulted. */
    private static Scan scan(ServerPlayer p, Vec3 start, Vec3 end) {
        if (!DeflectionStrikeRules.within(point(start), point(end))) return null;
        int pieces = Math.max(1, (int)Math.ceil(start.distanceTo(end)/.25));
        Vec3 previous = start, stop = end; double nearest = start.distanceToSqr(end); boolean solid = false;
        for (int i=1; i<=pieces; i++) {
            Vec3 next = start.lerp(end, (double)i/pieces);
            AABB corridor = new AABB(previous, next).inflate(DeflectionStrikeRules.RADIUS);
            if (!DisplacedProjectionWorld.volume(p, corridor).isEmpty()) return null;
            for (var shape : p.serverLevel().getBlockCollisions(p, corridor)) for (AABB box : shape.toAabbs()) {
                AABB bound = box.inflate(DeflectionStrikeRules.RADIUS);
                Optional<Vec3> hit = contact(bound, start, end);
                if (hit.isPresent() && start.distanceToSqr(hit.get()) <= nearest) {
                    nearest = start.distanceToSqr(hit.get()); stop = hit.get(); solid = true;
                }
            }
            previous = next;
        }
        List<Entity> candidates = new ArrayList<>();
        AABB swept = new AABB(start, end).inflate(DeflectionStrikeRules.RADIUS);
        // Native entity queries use strict AABB overlap. A frozen contact exactly at the
        // entry face would otherwise disappear when this callback ray ends at that face.
        // One outward ULP is broadphase only; the exact .035 / eight-metre clip below is unchanged.
        AABB query = new AABB(Math.nextDown(swept.minX), Math.nextDown(swept.minY), Math.nextDown(swept.minZ),
                Math.nextUp(swept.maxX), Math.nextUp(swept.maxY), Math.nextUp(swept.maxZ));
        p.serverLevel().getEntities(EntityTypeTest.forClass(Entity.class), query,
                e -> e != p && !e.isRemoved() && !e.isSpectator() && (e.isPickable() || e.canBeCollidedWith() || e instanceof LivingEntity),
                candidates, DeflectionStrikeRules.SCAN_LIMIT + 1);
        if (candidates.size() > DeflectionStrikeRules.SCAN_LIMIT) return null;
        List<Contact> contacts = new ArrayList<>();
        for (Entity e : candidates) {
            Optional<Vec3> hit = contact(e.getBoundingBox().inflate(DeflectionStrikeRules.RADIUS), start, end);
            if (hit.isEmpty()) continue;
            double distance = start.distanceToSqr(hit.get());
            if (distance < nearest || !solid && distance == nearest) contacts.add(new Contact(e, hit.get(), distance));
        }
        // Exact ties stop on a nonhostile first, never use entity enumeration order to pass through it.
        contacts.sort(Comparator.comparingDouble(Contact::distance)
                .thenComparingInt(c -> c.entity instanceof LivingEntity living && hostile(p, living) ? 1 : 0)
                .thenComparing(c -> c.entity.getUUID()));
        return new Scan(List.copyOf(contacts), stop);
    }
    private static Optional<Vec3> contact(AABB box, Vec3 start, Vec3 end) {
        if (inside(box, start)) return Optional.of(start);
        // Native clipPoint permits a 1e-7 cross-axis tolerance. That is not authority
        // to enlarge this skill's hitbox: only an actual point in the closed box passes.
        Optional<Vec3> hit = box.clip(start, end).filter(point -> inside(box, point));
        // AABB.clip excludes t==1; the exact frozen entry endpoint is still an actual closed contact.
        if (hit.isEmpty() && inside(box, end)) return Optional.of(end);
        return hit;
    }
    private static boolean inside(AABB box, Vec3 point) {
        return point.x >= box.minX && point.x <= box.maxX && point.y >= box.minY && point.y <= box.maxY
                && point.z >= box.minZ && point.z <= box.maxZ;
    }
    private static HighPowerInput.Input claim(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire); if (!HighPowerBracerSession.authenticated(p, input)) return null;
        Watermark prior = INPUTS.get(p.getUUID());
        if (prior != null && prior.epoch == input.epoch() && input.input() <= prior.input) return null;
        INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        return ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot())) ? input : null;
    }
    private static boolean refuse(Context c, String reason) {
        c.audience.add(c.identity.owner()); send(c, ENDED, 0, Vec3.ZERO, Vec3.ZERO, REFUSED);
        feedback(c.identity.owner(), reason); return false;
    }
    private static void feedback(ServerPlayer p, String reason) {
        if (HighPowerCombat.unsupportedReason(p).isPresent()) reason = "unsupported";
        p.displayClientMessage(Component.translatable("message.academy.highpower.deflection_strike." + reason), true);
    }
    private static void receipt(Context c, int slot, int status, UUID victim, Vec3 delta) {
        // A callback can install terrain or move a body after refusing a hit. Never draw through that new obstruction.
        if (!validCast(c) || !c.targets.get(slot).same(c) || !pathMatches(c, slot)) return;
        Vec3 end = c.targets.get(slot).contact;
        c.receipts.add(new Receipt(slot, c.lastVisualPoint, end, status, victim, c.shares.get(slot), delta, c.input.input()));
        send(c, FIRE, slot, c.lastVisualPoint, end, status); c.lastVisualPoint = end;
    }
    public static boolean abort(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire); if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Watermark prior = INPUTS.get(p.getUUID());
        if (prior == null || prior.epoch != input.epoch() || input.input() > prior.input) INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        Context c = owned(p);
        if (c == null || c.input.epoch() != input.epoch() || c.input.slot() != input.slot() || input.input() < c.input.input()) return false;
        finish(c, true); return true;
    }
    public static void abort(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c, true); }
    public static void remove(ServerPlayer p) { if (thread(p)) { abort(p); INPUTS.remove(p.getUUID()); } }
    public static void clear() { CONTEXTS.clear(); INPUTS.clear(); PUSHES.clear(); }
    public static boolean active(ServerPlayer p) { return owned(p) != null; }
    public static List<Receipt> receipts(ServerPlayer p) { Context c = owned(p); return c == null ? List.of() : List.copyOf(c.receipts); }
    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null || c.resolving) return;
        long now = now(p);
        if (!session(c) || !DeflectionStrikeRules.clock(now) || now < c.started) finish(c, true);
        else if (now - c.started >= DeflectionStrikeRules.VISUAL_TICKS) finish(c, false);
    }
    private static void finish(Context c, boolean notify) {
        if (CONTEXTS.remove(c.identity.owner().getUUID(), c)) {
            c.emitting = null;
            if (notify) send(c, ENDED, 0, Vec3.ZERO, Vec3.ZERO, REFUSED);
        }
    }
    private static void send(Context c, int phase, int slot, Vec3 origin, Vec3 end, int status) {
        ServerPlayer owner = c.identity.owner();
        Visual visual = new Visual(owner.getId(), owner.getUUID(), c.input.epoch(), c.input.input(), c.identity.token(),
                ++c.sequence, phase, slot, c.crouch, origin, end, status);
        c.audience.add(owner);
        if (phase == FIRE) for (ServerPlayer observer : c.identity.level().players()) if (observer.distanceToSqr(owner) <= 96*96) c.audience.add(observer);
        for (ServerPlayer observer : c.audience) if (observer.connection != null && !observer.hasDisconnected()
                && observer.serverLevel() == c.identity.level()) PacketDistributor.sendToPlayer(observer, visual);
    }
    private static Context owned(ServerPlayer p) {
        if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null;
    }
    private static boolean thread(ServerPlayer p) { return p != null && p.serverLevel().getServer().isSameThread(); }
    private static long now(ServerPlayer p) { return p.serverLevel().getGameTime(); }
    private static BigDecimal health(LivingEntity target) { return HighPowerCombat.enabled(target)
            ? new BigDecimal(HighPowerCombat.healthText(target)) : BigDecimal.valueOf(target.getHealth()); }
    private static boolean finite(Vec3 v) { return v != null && point(v).finite(); }
    private static DeflectionStrikeRules.Point point(Vec3 v) { return v == null ? null : new DeflectionStrikeRules.Point(v.x, v.y, v.z); }
    private static Vec3 vector(DeflectionStrikeRules.Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private static void writeVec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 readVec(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    public record Visual(int owner, UUID ownerUuid, long epoch, long input, long token, long sequence, int phase,
                         int slot, boolean crouch, Vec3 origin, Vec3 end, int status) implements CustomPacketPayload {
        public Visual {
            if (owner < 0 || ownerUuid == null || epoch <= 0 || input <= 0 || token <= 0 || sequence <= 0 || sequence > 3
                    || phase < FIRE || phase > ENDED || slot < 0 || slot >= 2 || !DeflectionStrikeRules.within(point(origin), point(end))
                    || status < REFUSED || status > PUSHED || crouch && (status == DAMAGED || status == ABSORBED)
                    || !crouch && status == PUSHED || phase == ENDED && (slot != 0 || status != REFUSED || !origin.equals(Vec3.ZERO) || !end.equals(Vec3.ZERO)))
                throw new IllegalArgumentException("Invalid bounded NEW411 visual receipt");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "deflection_strike_visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.owner); b.writeUUID(v.ownerUuid); b.writeLong(v.epoch); b.writeLong(v.input); b.writeLong(v.token); b.writeLong(v.sequence);
            b.writeByte(v.phase); b.writeByte(v.slot); b.writeBoolean(v.crouch); writeVec(b, v.origin); writeVec(b, v.end); b.writeByte(v.status);
        }, b -> new Visual(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readLong(),
                b.readUnsignedByte(), b.readUnsignedByte(), b.readBoolean(), readVec(b), readVec(b), b.readUnsignedByte()));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) {
            e.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet));
        }
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post e) {
        for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.level().getServer() == e.getServer()) tick(c.identity.owner());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { if (e.getOriginal() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void death(LivingDeathEvent e) { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { clear(); }
}
