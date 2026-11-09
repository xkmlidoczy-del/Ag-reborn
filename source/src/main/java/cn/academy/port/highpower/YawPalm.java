package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.YawPalmRules;
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
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

/** NEW423: one authenticated tap, a frozen straight path and three mixed frozen opportunities and one side redirect per native hurt. */
@EventBusSubscriber(modid = "academy")
public final class YawPalm {
    public static final String ID = "yaw_palm";
    public static final int FIRE = 0, ENDED = 1;
    public static final int REFUSED = 0, DAMAGED = 1, ABSORBED = 2, PUSHED = 3;
    private static final String CLOCK = "next_" + ID;
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(long epoch, long input) {}
    private record Contact(Entity entity, Vec3 point, double distance) {}
    private record Scan(List<Contact> contacts, Vec3 stop) {}
    private static final class Target {
        final Entity entity; final UUID uuid; final int id; final Vec3 position, contact, initialVelocity;
        final AABB bounds, deathBounds; final Pose pose; final boolean grounded; final double resistance;
        final SpatialRelayMedium medium; final SpatialRelayMedium.Snapshot itemSnapshot;
        final CompoundTag persistent;
        Vec3 allowedVelocity; boolean motionCommitted, ownLethal;
        Target(ServerPlayer p, Entity entity, Vec3 contact) {
            this.entity = entity; uuid = entity.getUUID(); id = entity.getId(); position = entity.position();
            bounds = entity.getBoundingBox(); deathBounds = entity.getDimensions(Pose.DYING).makeBoundingBox(position); this.contact = contact; initialVelocity = allowedVelocity = entity.getDeltaMovement();
            pose = entity.getPose(); grounded = entity.onGround();
            resistance = entity instanceof LivingEntity living ? living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 0;
            medium = entity instanceof ItemEntity item ? SpatialRelayMedium.capture(p, item) : null;
            itemSnapshot = medium == null ? null : medium.snapshot(); persistent = entity.getPersistentData().copy();
        }
        boolean same(Context c) {
            boolean nativeShield = shieldBranch(c, entity);
            return entity.level() == c.identity.level() && !entity.isRemoved() && uuid.equals(entity.getUUID()) && id == entity.getId()
                    && c.identity.level().getEntity(uuid) == entity && c.identity.level().getEntity(id) == entity
                    && position.equals(entity.position())
                    && (pose == entity.getPose() && bounds.equals(entity.getBoundingBox())
                        || ownLethal && !entity.isAlive() && entity.getPose() == Pose.DYING && deathBounds.equals(entity.getBoundingBox()))
                    && (nativeShield || allowedVelocity.equals(entity.getDeltaMovement())) && grounded == entity.onGround()
                    && (entity instanceof LivingEntity living ? resistance == living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)
                        : medium != null && medium.valid() && persistent.equals(entity.getPersistentData())
                        && itemSnapshot.nativeState().equals(medium.nativeState())
                        && net.minecraft.world.item.ItemStack.matches(itemSnapshot.stack(), medium.stack()));
        }
    }
    public record Receipt(int slot, Vec3 origin, Vec3 end, int status, UUID victim,
                          BigDecimal damage, Vec3 velocityDelta, long input) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final int preset;
        final long revision, started;
        final Vec3 position, origin, axis, side, ownerVelocity;
        final float yaw, pitch;
        final boolean crouch;
        final List<Receipt> receipts = new ArrayList<>(3);
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
            yaw = p.getYRot(); pitch = p.getXRot(); crouch = p.isShiftKeyDown();
            var direction = YawPalmRules.side(yaw, crouch); side = direction == null ? Vec3.ZERO : vector(direction); ownerVelocity = p.getDeltaMovement(); lastVisualPoint = origin;
        }
    }
    /** Constructible only while this controller owns the frozen slot. No public amount/source selector. */
    public static final class EmissionPermit {
        private final Context context;
        private final int slot;
        private boolean consumed;
        private EmissionPermit(Context context, int slot) { this.context = context; this.slot = slot; }
        ServerPlayer player() { return context.identity.owner(); }
        LivingEntity target() { return (LivingEntity)context.targets.get(slot).entity; }
        Vec3 side() { return context.side; }
        Vec3 beforeVelocity() { return context.targets.get(slot).allowedVelocity; }
        boolean grounded() { return context.targets.get(slot).grounded; }
        double resistance() { return context.targets.get(slot).resistance; }
        Vec3 nativeDelta = Vec3.ZERO;
        boolean nativeClaimed, nativeLethal, shieldBlocked;
        boolean claimNative() { if (nativeClaimed || !nativeValid()) return false; nativeClaimed = true; return true; }
        void completeNative(Vec3 expected, Vec3 before) {
            Target snapshot = context.targets.get(slot);
            if (expected != null && expected.equals(target().getDeltaMovement())) {
                snapshot.allowedVelocity = expected; snapshot.motionCommitted = true;
                if (nativeValid()) nativeDelta = expected.subtract(before);
            }
        }
        void completedLethal() { context.targets.get(slot).ownLethal = true; }
        boolean nativeValid() {
            if (!nativeLethal) return valid();
            Target snapshot = context.targets.get(slot);
            return context.emitting == this && context.paid && context.resolving && consumed
                    && context.attempted.contains(snapshot.uuid) && damage().signum() > 0
                    && validCast(context) && snapshot.same(context) && !target().isAlive() && target().getHealth() == 0
                    && deathPermission(context, snapshot) && pathMatches(context, slot)
                    && snapshot.same(context) && validCast(context);
        }
        Vec3 origin() { return context.origin; }
        BigDecimal damage() { return context.shares.get(slot); }
        boolean consume() { if (consumed || !valid()) return false; consumed = true; return true; }
        boolean valid() { return context.emitting == this && context.paid && context.resolving
                && context.attempted.contains(target().getUUID()) && validSlot(context, slot) && damage().signum() > 0; }
    }
    private YawPalm() {}

    public static boolean tap(ServerPlayer p, String wire) {
        Context previous = owned(p);
        if (previous != null && previous.resolving) return false;
        HighPowerInput.Input input = claim(p, wire);
        if (input == null || nextToken == Long.MAX_VALUE || !YawPalmRules.clock(now(p))) return false;
        Context c = new Context(p, input);
        if (!canAct(p) || !AbilityStorage.get(p).canUse(ID)) return refuse(c, "not_ready");
        if (previous != null) finish(previous, true);
        var endpoint = YawPalmRules.endpoint(point(c.origin), point(c.axis));
        if (endpoint == null) return refuse(c, "no_target");
        c.end = vector(endpoint);
        Scan scan = scan(c, c.end);
        if (scan == null) return refuse(c, "no_target");
        List<Target> targets = new ArrayList<>(3);
        for (Contact contact : scan.contacts) {
            if (!eligible(c, contact.entity)) break;
            Target target = new Target(p, contact.entity, contact.point);
            if (!target.same(c)) break;
            targets.add(target);
            if (targets.size() == YawPalmRules.MAX_TARGETS) break;
        }
        if (targets.isEmpty()) return refuse(c, "no_target");
        var growth = HighPowerGrowth.values(p);
        c.growth = growth.position(); c.targets = List.copyOf(targets);
        int hostileCount = (int)targets.stream().filter(t -> t.entity instanceof LivingEntity).count();
        List<BigDecimal> hostileShares = YawPalmRules.shares(growth.damage(), hostileCount);
        List<BigDecimal> shares = new ArrayList<>(); int hostileIndex = 0;
        for (Target target : targets) shares.add(target.entity instanceof LivingEntity ? hostileShares.get(hostileIndex++) : BigDecimal.ZERO);
        c.shares = List.copyOf(shares);
        CONTEXTS.put(p.getUUID(), c);
        c.resolving = true;
        try {
            for (int slot=0; slot<c.targets.size(); slot++) {
                if (!validCast(c)) break;
                Target target = c.targets.get(slot);
                if (!validSlot(c, slot) || c.attempted.contains(target.uuid) || target.entity instanceof LivingEntity && c.shares.get(slot).signum() == 0) continue;
                if (!c.paid) {
                    if (!HighPowerResources.trySpend(p, ID, YawPalmRules.cpCost(c.growth), YawPalmRules.cooldownTicks(c.growth))) break;
                    c.paid = true; c.ledger = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
                    c.balance = Double.doubleToLongBits(c.ledger.getDouble("cp")); c.clock = c.ledger.getLong(CLOCK);
                    c.cooldown = c.identity.state().cooldowns.getOrDefault(ID, 0);
                }
                if (!validSlot(c, slot)) continue;
                c.attempted.add(target.uuid);
                if (target.entity instanceof LivingEntity) strike(c, slot); else pushItem(c, slot);
            }
        } finally { c.emitting = null; c.resolving = false; }
        if (!c.paid) { finish(c, true); feedback(p, "not_ready"); }
        else if (!session(c)) finish(c, true);
        return c.paid;
    }

    private static void strike(Context c, int slot) {
        HighPowerNaturalRecovery.offensiveAction(c.identity.owner());
        Target snapshot = c.targets.get(slot); LivingEntity target = (LivingEntity)snapshot.entity;
        BigDecimal before = health(target), shield = HighPowerCombat.shield(target); float absorption = target.getAbsorptionAmount();
        EmissionPermit permit = new EmissionPermit(c, slot); c.emitting = permit;
        boolean accepted;
        try { accepted = HighPowerCombat.attackYawPalm(permit); } finally { c.emitting = null; }
        if (permit.shieldBlocked) feedback(c.identity.owner(), "blocked");
        int status = REFUSED;
        if (accepted && snapshot.same(c)) {
            if (health(target).compareTo(before) < 0) status = DAMAGED;
            else if (HighPowerCombat.shield(target).compareTo(shield) < 0 || target.getAbsorptionAmount() < absorption) status = ABSORBED;
        }
        if (status == REFUSED && !permit.nativeDelta.equals(Vec3.ZERO)) status = PUSHED;
        receipt(c, slot, status, status == REFUSED ? null : snapshot.uuid, permit.nativeDelta);
    }
    private static void pushItem(Context c, int slot) {
        Target target = c.targets.get(slot);
        if (!validSlot(c, slot) || target.medium == null || !target.itemSnapshot.matches(target.medium)) return;
        var result = YawPalmRules.itemResult(point(target.allowedVelocity), point(c.side));
        if (result == null || !validSlot(c, slot)) return;
        Vec3 before = target.allowedVelocity, after = vector(result);
        target.entity.setDeltaMovement(after); target.entity.hasImpulse = true; target.entity.hurtMarked = true;
        target.allowedVelocity = after; target.motionCommitted = true;
        if (target.same(c)) receipt(c, slot, PUSHED, target.uuid, after.subtract(before));
    }

    private static boolean validSlot(Context c, int slot) {
        if (!validCast(c) || slot < 0 || slot >= c.targets.size()) return false;
        Target target = c.targets.get(slot);
        if (!target.same(c) || !eligible(c, target.entity)) return false;
        return pathMatches(c, slot) && target.same(c) && validCast(c);
    }
    private static boolean pathMatches(Context c, int slot) {
        Target target = c.targets.get(slot);
        Scan current = scan(c, target.contact);
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
                        && (kindEligible(c.identity.owner(), body.entity) || body.ownLethal && c.attempted.contains(body.uuid) && !body.entity.isAlive() && deathPermission(c, body))) earlier = true;
            }
            if (!earlier) return false;
        }
        // A certified native dying pose can shrink out of the old hit ray. The frozen endpoint
        // remains a historical receipt only after the current corridor rejected every new blocker.
        return target.ownLethal && target.same(c) && !target.entity.isAlive() && deathPermission(c, target);
    }
    private static boolean validCast(Context c) {
        ServerPlayer p = c.identity.owner();
        if (!session(c) || now(p) != c.started || !c.position.equals(p.position()) || !c.origin.equals(p.getEyePosition())
                || !c.ownerVelocity.equals(p.getDeltaMovement()) || !c.axis.equals(p.getLookAngle().normalize()) || c.yaw != p.getYRot() || c.pitch != p.getXRot()
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
                && state.learned(HighPowerSkills.BRAKING_TOUCH) && state.level >= 2 && state.level <= 5 && HighPowerResources.effectiveLevel(p) >= 2 && SkillCatalog.enabled("vecmanip", ID)
                && HighPowerCombat.unsupportedReason(p).isEmpty() && !AbilityConsumption.busy(p) && !HighPowerResources.hasReservation(p)
                && finite(p.position()) && finite(p.getEyePosition()) && finite(p.getLookAngle()) && p.getLookAngle().lengthSqr() > .5
                && finite(p.getDeltaMovement()) && Float.isFinite(p.getYRot()) && Float.isFinite(p.getXRot())
                && DisplacedProjectionWorld.volume(p, p.getBoundingBox()).isEmpty();
    }
    /** Reuses the certified exact native hostile whitelist; players/friends/unknown classes remain physical blockers. */
    static boolean hostile(ServerPlayer p, LivingEntity target) {
        return target != null && DisplacedProjectionWorld.hostile(p, target) && !target.noPhysics
                && !target.getPersistentData().getBoolean("academy:protected") && !target.getPersistentData().getBoolean("academy:immovable") && finite(target.position()) && finite(target.getDeltaMovement());
    }

    private static boolean deathPermission(Context c, Target target) {
        ServerPlayer p = c.identity.owner(); Entity entity = target.entity;
        return entity instanceof LivingEntity living && HighPowerCombat.hostileTarget(living) && HighPowerCombat.supported(living)
                && HighPowerCombat.unsupportedReason(living).isEmpty() && !entity.isSpectator() && !entity.isInvulnerable()
                && !entity.noPhysics && !entity.isPassenger() && !entity.isVehicle()
                && !entity.getPersistentData().getBoolean("academy:protected") && !entity.getPersistentData().getBoolean("academy:immovable")
                && !p.isAlliedTo(entity) && !entity.isAlliedTo(p)
                && DisplacedProjectionWorld.volume(p, entity.getBoundingBox()).isEmpty();
    }

    private static boolean kindEligible(ServerPlayer p, Entity entity) {
        if (entity instanceof LivingEntity living) return hostile(p, living) && YawPalmNativeBridge.available(living);
        if (!(entity instanceof ItemEntity item) || SpatialRelay.active(p) || MagneticItemPull.active(p) || RecoveryMark.active(p)
                || item.isInWaterOrBubble() || item.isInLava() || !DisplacedProjectionWorld.volume(p, item.getBoundingBox()).isEmpty()) return false;
        return SpatialRelayMedium.capture(p, item) != null;
    }
    /** Only the current target's real blocked native container suspends its speed expectation.
     * No position, body, owner, permission or resource guard is weakened, and no velocity is rebased. */
    private static boolean shieldBranch(Context c, Entity entity) {
        return c.emitting != null && entity instanceof LivingEntity living && c.emitting.target() == entity
                && HighPowerCombat.yawPalmNativeShieldBlocked(living, c.emitting);
    }
    private static boolean eligible(Context c, Entity entity) {
        if (!kindEligible(c.identity.owner(), entity) || entity.isPassenger() || entity.isVehicle()) return false;
        if (shieldBranch(c, entity)) return true;
        // Once an admitted slot completed its native motion, validation preserves that exact result.
        for (Target target : c.targets) if (target.entity == entity && c.attempted.contains(target.uuid)) {
            if (target.motionCommitted) return target.same(c);
        }
        if (entity instanceof LivingEntity living) {
            double x = c.origin.x-entity.getX(), z = c.origin.z-entity.getZ();
            return x*x+z*z >= 1.0E-5F && YawPalmRules.nativeResult(point(entity.getDeltaMovement()), point(c.side),
                    YawPalmRules.KNOCKBACK, living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), entity.onGround()) != null;
        }
        return YawPalmRules.boundedHorizontal(point(entity.getDeltaMovement()))
                && YawPalmRules.itemResult(point(entity.getDeltaMovement()), point(c.side)) != null;
    }

    /** Solid shapes and every physical body participate before the hostile whitelist is consulted. */
    private static Scan scan(Context c, Vec3 end) {
        ServerPlayer p = c.identity.owner(); Vec3 start = c.origin;
        if (!YawPalmRules.within(point(start), point(end))) return null;
        int pieces = Math.max(1, (int)Math.ceil(start.distanceTo(end)/.25));
        Vec3 previous = start, stop = end; double nearest = start.distanceToSqr(end); boolean solid = false;
        for (int i=1; i<=pieces; i++) {
            Vec3 next = start.lerp(end, (double)i/pieces);
            AABB corridor = new AABB(previous, next).inflate(YawPalmRules.RADIUS);
            if (!DisplacedProjectionWorld.volume(p, corridor).isEmpty()) return null;
            for (var shape : p.serverLevel().getBlockCollisions(p, corridor)) for (AABB box : shape.toAabbs()) {
                AABB bound = box.inflate(YawPalmRules.RADIUS);
                Optional<Vec3> hit = contact(bound, start, end);
                if (hit.isPresent() && start.distanceToSqr(hit.get()) <= nearest) {
                    nearest = start.distanceToSqr(hit.get()); stop = hit.get(); solid = true;
                }
            }
            previous = next;
        }
        List<Entity> candidates = new ArrayList<>();
        AABB swept = new AABB(start, end).inflate(YawPalmRules.RADIUS);
        // Native entity queries use strict AABB overlap. A frozen contact exactly at the
        // entry face would otherwise disappear when this callback ray ends at that face.
        // One outward ULP is broadphase only; the exact .035 / six-metre clip below is unchanged.
        AABB query = new AABB(Math.nextDown(swept.minX), Math.nextDown(swept.minY), Math.nextDown(swept.minZ),
                Math.nextUp(swept.maxX), Math.nextUp(swept.maxY), Math.nextUp(swept.maxZ));
        p.serverLevel().getEntities(EntityTypeTest.forClass(Entity.class), query,
                e -> e != p && !e.isRemoved() && !e.isSpectator(),
                candidates, YawPalmRules.SCAN_LIMIT + 1);
        if (candidates.size() > YawPalmRules.SCAN_LIMIT) return null;
        List<Contact> contacts = new ArrayList<>();
        for (Entity e : candidates) {
            Optional<Vec3> hit = contact(e.getBoundingBox().inflate(YawPalmRules.RADIUS), start, end);
            if (hit.isEmpty()) continue;
            double distance = start.distanceToSqr(hit.get());
            if (distance < nearest || !solid && distance == nearest) contacts.add(new Contact(e, hit.get(), distance));
        }
        // Exact ties stop on a nonhostile first, never use entity enumeration order to pass through it.
        contacts.sort(Comparator.comparingDouble(Contact::distance)
                .thenComparingInt(hit -> kindEligible(p, hit.entity) ? 1 : 0)
                .thenComparing(hit -> hit.entity.getUUID()));
        return new Scan(List.copyOf(contacts), stop);
    }
    private static Optional<Vec3> contact(AABB box, Vec3 start, Vec3 end) {
        if (inside(box, start)) return Optional.of(start);
        Optional<Vec3> nativeHit = box.clip(start, end);
        Optional<Vec3> hit = nativeHit.filter(point -> inside(box, point));
        if (hit.isPresent()) return hit;
        // Native clip excludes t==1. Preserve the existing closed endpoint path.
        if (inside(box, end)) return Optional.of(end);
        // Rare path only: the native entry exists but its rounded point failed the strict box check.
        // Exact slab authority rejects tolerance-only misses before entry-plane canonicalization.
        return nativeHit.isPresent() ? closedSlabContact(box, start, end) : Optional.empty();
    }
    private record ClosedRatio(java.math.BigDecimal numerator, java.math.BigDecimal denominator)
            implements Comparable<ClosedRatio> {
        public int compareTo(ClosedRatio other) {
            return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
        }
    }
    private static Optional<Vec3> closedSlabContact(AABB box, Vec3 start, Vec3 end) {
        double[] from = {start.x, start.y, start.z}, to = {end.x, end.y, end.z};
        double[] low = {box.minX, box.minY, box.minZ}, high = {box.maxX, box.maxY, box.maxZ};
        ClosedRatio enter = new ClosedRatio(java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE);
        ClosedRatio exit = new ClosedRatio(java.math.BigDecimal.ONE, java.math.BigDecimal.ONE);
        int entryAxis = -1; double entryPlane = 0;
        for (int axis = 0; axis < 3; axis++) {
            if (!Double.isFinite(from[axis]) || !Double.isFinite(to[axis])
                    || !Double.isFinite(low[axis]) || !Double.isFinite(high[axis])) return Optional.empty();
            java.math.BigDecimal origin = new java.math.BigDecimal(from[axis]);
            java.math.BigDecimal delta = new java.math.BigDecimal(to[axis]).subtract(origin);
            if (delta.signum() == 0) {
                if (from[axis] < low[axis] || from[axis] > high[axis]) return Optional.empty();
                continue;
            }
            java.math.BigDecimal nearN = new java.math.BigDecimal(low[axis]).subtract(origin);
            java.math.BigDecimal farN = new java.math.BigDecimal(high[axis]).subtract(origin);
            double plane = low[axis];
            if (delta.signum() < 0) {
                delta = delta.negate();
                java.math.BigDecimal oldNear = nearN; nearN = farN.negate(); farN = oldNear.negate();
                plane = high[axis];
            }
            ClosedRatio near = new ClosedRatio(nearN, delta), far = new ClosedRatio(farN, delta);
            if (near.compareTo(enter) > 0) { enter = near; entryAxis = axis; entryPlane = plane; }
            if (far.compareTo(exit) < 0) exit = far;
            if (enter.compareTo(exit) > 0) return Optional.empty();
        }
        if (entryAxis < 0) return Optional.empty();
        double[] point = new double[3];
        // Division is solely representation after exact interval admission, never hit authority.
        java.math.MathContext representation = new java.math.MathContext(80, java.math.RoundingMode.HALF_EVEN);
        for (int axis = 0; axis < 3; axis++) {
            java.math.BigDecimal origin = new java.math.BigDecimal(from[axis]);
            java.math.BigDecimal delta = new java.math.BigDecimal(to[axis]).subtract(origin);
            point[axis] = origin.add(delta.multiply(enter.numerator).divide(enter.denominator, representation)).doubleValue();
        }
        point[entryAxis] = entryPlane;
        Vec3 closed = new Vec3(point[0], point[1], point[2]);
        if (!inside(box, closed)) return Optional.empty();
        for (int axis = 0; axis < 3; axis++) {
            if (point[axis] < Math.min(from[axis], to[axis]) || point[axis] > Math.max(from[axis], to[axis])) return Optional.empty();
        }
        return Optional.of(closed);
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
        p.displayClientMessage(Component.translatable("message.academy.highpower.yaw_palm." + reason), true);
    }
    private static void receipt(Context c, int slot, int status, UUID victim, Vec3 delta) {
        // A callback can install terrain or move a body after refusing a hit. Never draw through that new obstruction.
        if (!validCast(c) || !c.targets.get(slot).same(c) || !pathMatches(c, slot)) return;
        Vec3 end = c.targets.get(slot).contact;
        c.receipts.add(new Receipt(slot, c.origin, end, status, victim, c.shares.get(slot), delta, c.input.input()));
        send(c, FIRE, slot, c.origin, end, status, delta); c.lastVisualPoint = end;
    }
    public static boolean release(ServerPlayer p, String wire) {
        var input = HighPowerInput.parse(wire); if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Context c = owned(p);
        return c != null && c.input.epoch() == input.epoch() && c.input.slot() == input.slot() && c.input.input() == input.input();
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
    public static void clear() { CONTEXTS.clear(); INPUTS.clear(); YawPalmNativeBridge.clear(); }
    public static boolean active(ServerPlayer p) { return owned(p) != null; }
    public static List<Receipt> receipts(ServerPlayer p) { Context c = owned(p); return c == null ? List.of() : List.copyOf(c.receipts); }
    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null || c.resolving) return;
        long now = now(p);
        if (!session(c) || !YawPalmRules.clock(now) || now < c.started) finish(c, true);
        else if (now - c.started >= YawPalmRules.VISUAL_TICKS) finish(c, false);
    }
    private static void finish(Context c, boolean notify) {
        if (CONTEXTS.remove(c.identity.owner().getUUID(), c)) {
            c.emitting = null;
            if (notify) send(c, ENDED, 0, Vec3.ZERO, Vec3.ZERO, REFUSED);
        }
    }
    private static void send(Context c, int phase, int slot, Vec3 origin, Vec3 end, int status) {
        send(c, phase, slot, origin, end, status, Vec3.ZERO);
    }
    private static void send(Context c, int phase, int slot, Vec3 origin, Vec3 end, int status, Vec3 delta) {
        ServerPlayer owner = c.identity.owner();
        Visual visual = new Visual(owner.getId(), owner.getUUID(), c.input.epoch(), c.input.input(), c.identity.token(),
                ++c.sequence, phase, slot, c.crouch, origin, end, status, delta);
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
    private static YawPalmRules.Point point(Vec3 v) { return v == null ? null : new YawPalmRules.Point(v.x, v.y, v.z); }
    private static Vec3 vector(YawPalmRules.Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private static void writeVec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 readVec(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
    public record Visual(int owner, UUID ownerUuid, long epoch, long input, long token, long sequence, int phase,
                         int slot, boolean crouch, Vec3 origin, Vec3 end, int status, Vec3 velocityDelta) implements CustomPacketPayload {
        public Visual {
            if (owner < 0 || ownerUuid == null || epoch <= 0 || input <= 0 || token <= 0 || sequence <= 0 || sequence > 4
                    || phase < FIRE || phase > ENDED || slot < 0 || slot >= 3 || !YawPalmRules.within(point(origin), point(end))
                    || status < REFUSED || status > PUSHED || !finite(velocityDelta) || velocityDelta.lengthSqr() > 4
                    || status == REFUSED && !velocityDelta.equals(Vec3.ZERO) || phase == ENDED && (slot != 0 || status != REFUSED || !origin.equals(Vec3.ZERO) || !end.equals(Vec3.ZERO)))
                throw new IllegalArgumentException("Invalid bounded NEW423 visual receipt");
        }
        public static final Type<Visual> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("academy", "yaw_palm_visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.owner); b.writeUUID(v.ownerUuid); b.writeLong(v.epoch); b.writeLong(v.input); b.writeLong(v.token); b.writeLong(v.sequence);
            b.writeByte(v.phase); b.writeByte(v.slot); b.writeBoolean(v.crouch); writeVec(b, v.origin); writeVec(b, v.end); b.writeByte(v.status); writeVec(b, v.velocityDelta);
        }, b -> new Visual(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readLong(),
                b.readUnsignedByte(), b.readUnsignedByte(), b.readBoolean(), readVec(b), readVec(b), b.readUnsignedByte(), readVec(b)));
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
