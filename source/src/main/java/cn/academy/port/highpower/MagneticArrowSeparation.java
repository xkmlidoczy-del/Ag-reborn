package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.MagneticArrowSeparationRules;
import cn.academy.port.highpower.core.MagneticArrowSeparationRules.Admission;
import io.netty.handler.codec.DecoderException;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW133 second mode. A captured 20-tick corridor can gently separate four ordinary enemy arrows. */
@EventBusSubscriber(modid = "academy")
public final class MagneticArrowSeparation {
    public static final String ID = MagneticItemPull.ID;
    public static final String QUOTA_KEY = "academy:arrow_separation_quota";
    public static final String LEASE_KEY = "academy:arrow_separation_lease";
    public static final int ARMED = 0, STEERING = 1, ENDED = 2;
    public record Pose(Vec3 origin, Vec3 axis, double range) {}
    private static final TagKey<EntityType<?>> METAL = TagKey.create(Registries.ENTITY_TYPE, id("magnetic_projectiles"));
    private static final TagKey<EntityType<?>> PROTECTED = TagKey.create(Registries.ENTITY_TYPE, id("magnetic_pull_protected"));
    private static final TagKey<EntityType<?>> IMMOVABLE = TagKey.create(Registries.ENTITY_TYPE, id("magnetic_pull_immovable"));
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static final Map<UUID, Watermark> INPUTS = new HashMap<>();
    private static final Map<UUID, Ledger> QUOTAS = new HashMap<>();
    private static final Map<UUID, Lease> LEASES = new HashMap<>();
    private static long nextToken;
    private static int baselineDiagnosticHits;
    private static final boolean DIAGNOSTICS = Boolean.getBoolean("academy.highpower.arrowSeparationDiagnostics");
    private static final org.slf4j.Logger DIAGNOSTIC_LOG = org.slf4j.LoggerFactory.getLogger("academy.arrow_separation_diagnostics");
    private static final Set<Long> DIAGNOSTIC_TOKENS = DIAGNOSTICS ? new HashSet<>() : null;
    public static Consumer<Visual> clientReceiver = ignored -> {};
    private record Watermark(long epoch, long input) {}
    private record Ledger(long observed, List<Admission> entries) {}
    private record Lease(Target target, Context context, long since, long until) {}
    private static final class Target {
        final Arrow arrow; final UUID uuid; final int entityId; final ServerLevel world;
        final LivingEntity shooter; final UUID shooterUuid; final int shooterId;
        int side; double turned;
        Target(Arrow arrow, LivingEntity shooter, ServerLevel world) {
            this.arrow = arrow; this.uuid = arrow.getUUID(); this.entityId = arrow.getId(); this.world = world;
            this.shooter = shooter; shooterUuid = shooter.getUUID(); shooterId = shooter.getId();
            if (arrow.getPersistentData().contains(LEASE_KEY, Tag.TAG_COMPOUND)) turned = arrow.getPersistentData().getCompound(LEASE_KEY).getDouble("turn");
        }
    }
    private static final class Context {
        final LegacySingleKeyServerIdentity identity; final HighPowerInput.Input input;
        final int preset, cooldown; final long revision, started; final Vec3 origin, axis; final double cost, range;
        final Map<UUID, Target> targets = new LinkedHashMap<>(); final Set<UUID> admitted = new HashSet<>();
        final Set<ServerPlayer> audience = new LinkedHashSet<>();
        long lastTick, sequence; boolean paid; int diagnostics, examined;
        Context(ServerPlayer p, HighPowerInput.Input input, Pose pose) {
            identity = new LegacySingleKeyServerIdentity(p, input.input(), ++nextToken, AbilityStorage.get(p));
            this.input = input; this.axis = pose.axis; origin = pose.origin; range = pose.range;
            preset = identity.state().presets.current(); revision = identity.state().presets.revision();
            started = now(p); lastTick = started - 1;
            var growth = HighPowerGrowth.values(p).position();
            cost = MagneticArrowSeparationRules.cpCost(growth); cooldown = MagneticArrowSeparationRules.cooldownTicks(growth);
        }
    }
    private MagneticArrowSeparation() {}
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("academy", name); }

    /** Facade must first consume its authenticated mode-bound, one-use release lease. No network entry here. */
    public static Pose capturePose(ServerPlayer p) {
        if (!canAct(p)) return null;
        Vec3 origin = p.getEyePosition(), look = p.getLookAngle();
        if (!finite(origin) || !finite(look) || look.lengthSqr() < .5 || Math.hypot(look.x, look.z) < .2) return null;
        Vec3 axis = look.normalize(), end = origin.add(axis.scale(MagneticArrowSeparationRules.RANGE));
        if (!rayPermitted(p, origin, end)) return null;
        var hit = p.serverLevel().clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double range = hit.getType() == HitResult.Type.MISS ? MagneticArrowSeparationRules.RANGE : Math.max(0, origin.distanceTo(hit.getLocation()) - .01);
        return range < .5 ? null : new Pose(origin, axis, range);
    }
    public static boolean poseValid(ServerPlayer p, Pose pose) {
        return thread(p) && pose != null && finite(pose.origin) && finite(pose.axis) && Double.isFinite(pose.range)
                && pose.range >= .5 && pose.range <= MagneticArrowSeparationRules.RANGE && Math.abs(pose.axis.lengthSqr() - 1) <= 1e-8
                && p.getEyePosition().distanceToSqr(pose.origin) <= .25 && finite(p.getLookAngle())
                && p.getLookAngle().normalize().dot(pose.axis) >= Math.cos(Math.toRadians(5))
                && clearLine(p, pose.origin, pose.origin.add(pose.axis.scale(pose.range)));
    }
    public static boolean startAuthorized(ServerPlayer p, HighPowerInput.Input input) { return startAuthorized(p, input, capturePose(p)); }
    public static boolean startAuthorized(ServerPlayer p, HighPowerInput.Input input, Pose pose) {
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Watermark old = INPUTS.get(p.getUUID());
        if (old != null && old.epoch == input.epoch() && old.input >= input.input()) return false;
        INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        if (owned(p) != null || MagneticItemPull.itemsActive(p) || !canAct(p) || !ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))
                || !AbilityStorage.get(p).canUse(ID) || nextToken == Long.MAX_VALUE || quota(p, now(p)) == null) return false;
        if (!poseValid(p, pose)) return false;
        Context c = new Context(p, input, pose);
        CONTEXTS.put(p.getUUID(), c); diagnostic(c, "arm", "unpaid=true lifetime=20 range=" + c.range); send(c, ARMED); return true;
    }
    public static boolean start(ServerPlayer p, HighPowerInput.Input input) { return startAuthorized(p, input); }
    public static boolean abort(ServerPlayer p, HighPowerInput.Input input) {
        if (!HighPowerBracerSession.authenticated(p, input)) return false;
        Watermark old = INPUTS.get(p.getUUID());
        if (old == null || old.epoch != input.epoch() || old.input < input.input()) INPUTS.put(p.getUUID(), new Watermark(input.epoch(), input.input()));
        Context c = owned(p);
        if (c == null || c.input.epoch() != input.epoch() || c.input.slot() != input.slot() || c.input.input() > input.input()) return false;
        finish(c); return true;
    }
    public static boolean abort(ServerPlayer p, String wire) { return abort(p, HighPowerInput.parse(wire)); }
    public static void abort(ServerPlayer p) { Context c = owned(p); if (c != null) finish(c); }
    public static void remove(ServerPlayer p) { if (!thread(p)) return; abort(p); INPUTS.remove(p.getUUID()); }
    public static boolean active(ServerPlayer p) { return owned(p) != null; }
    public static boolean paid(ServerPlayer p) { Context c = owned(p); return c != null && c.paid; }
    public static int targetCount(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.targets.size(); }
    public static int affectedCount(ServerPlayer p) { Context c = owned(p); return c == null ? 0 : c.admitted.size(); }
    public static void clear() { CONTEXTS.clear(); INPUTS.clear(); QUOTAS.clear(); LEASES.clear(); }

    /** Read-only reciprocal writer guard. Malformed/future records fail closed, even after reload/abort. */
    public static boolean leased(AbstractArrow arrow) {
        if (arrow == null || !(arrow.level() instanceof ServerLevel world) || !world.getServer().isSameThread()) return true;
        long tick = world.getServer().overworld().getGameTime();
        if (!MagneticArrowSeparationRules.clock(tick)) return true;
        CompoundTag data = arrow.getPersistentData();
        if (data.contains(LEASE_KEY)) {
            if (!data.contains(LEASE_KEY, Tag.TAG_COMPOUND)) return true;
            CompoundTag lease = data.getCompound(LEASE_KEY);
            if (!validLeaseTag(lease, arrow.getUUID(), tick)) return true;
            if (lease.getLong("until") > tick) return true;
        }
        Lease live = LEASES.get(arrow.getUUID());
        return live != null && (tick < live.since || tick < live.until);
    }
    private static boolean validLeaseTag(CompoundTag tag, UUID arrow, long now) {
        return tag.getAllKeys().equals(Set.of("version", "arrow", "owner", "since", "until", "turn"))
                && tag.contains("version", Tag.TAG_INT) && tag.getInt("version") == 1
                && tag.hasUUID("arrow") && arrow.equals(tag.getUUID("arrow")) && tag.hasUUID("owner")
                && tag.contains("since", Tag.TAG_LONG) && tag.contains("until", Tag.TAG_LONG) && tag.contains("turn", Tag.TAG_DOUBLE)
                && Double.isFinite(tag.getDouble("turn")) && tag.getDouble("turn") >= 0 && tag.getDouble("turn") <= MagneticArrowSeparationRules.MAX_TURN
                && MagneticArrowSeparationRules.clock(tag.getLong("since")) && tag.getLong("since") <= now
                && tag.getLong("until") == tag.getLong("since") + MagneticArrowSeparationRules.QUOTA_TICKS;
    }
    private static boolean ownsLease(Context c, Target target, long tick) {
        Lease live = LEASES.get(target.uuid); CompoundTag data = target.arrow.getPersistentData();
        if (live == null || live.context != c || live.target != target || tick < live.since || tick >= live.until
                || !data.contains(LEASE_KEY, Tag.TAG_COMPOUND)) return false;
        CompoundTag tag = data.getCompound(LEASE_KEY);
        return validLeaseTag(tag, target.uuid, tick) && tag.getUUID("owner").equals(c.identity.owner().getUUID())
                && tag.getLong("since") == live.since && tag.getLong("until") == live.until && tag.getDouble("turn") == target.turned;
    }
    private static boolean otherLease(AbstractArrow arrow) {
        return MultiAxisReflectionArrowBridge.leased(arrow) || TrajectoryReturnArrowBridge.leased(arrow) || ShortInterceptionArrowBridge.leased(arrow) || MagneticDomainArrows.leased(arrow) || IronSandLance.leased(arrow) || ElectromasterPrototype.leased(arrow) || ElectromasterChargeDecoy.leased(arrow) || MagneticGroundStep.leased(arrow);
    }

    public static void tick(ServerPlayer p) {
        Context c = owned(p); if (c == null) return;
        long tick = now(p);
        if (!valid(c)) { finish(c, "owner_or_eligibility_invalid"); return; }
        if (MagneticArrowSeparationRules.expired(c.started, tick)) { finish(c, "original_lifetime_expired"); return; }
        if (quota(p, tick) == null) { finish(c, "persistent_quota_invalid"); return; }
        if (tick <= c.lastTick) return;
        c.lastTick = tick;
        int examined = 0;
        for (Iterator<Target> it = c.targets.values().iterator(); it.hasNext();) {
            Target target = it.next(); examined++; c.examined++;
            if (!ownsLease(c, target, tick) || !eligible(c, target) || !turn(c, target, false)) it.remove();
        }
        if (c.admitted.size() < MagneticArrowSeparationRules.MAX_ARROWS) {
            Ledger ledger = quota(p, tick);
            if (ledger != null && ledger.entries.size() < MagneticArrowSeparationRules.MAX_ARROWS) {
                Vec3 end = c.origin.add(c.axis.scale(c.range));
                AABB bounds = new AABB(c.origin, end).inflate(MagneticArrowSeparationRules.RADIUS);
                List<Arrow> candidates = new ArrayList<>();
                // Native live index query never requests a chunk. The result budget includes existing targets.
                p.serverLevel().getEntities(EntityTypeTest.forClass(Arrow.class), bounds, a -> true, candidates,
                        MagneticArrowSeparationRules.SCAN_LIMIT - examined);
                c.examined += candidates.size();
                candidates.sort(Comparator.<Arrow>comparingDouble(a -> a.position().distanceToSqr(c.origin)).thenComparing(Entity::getUUID));
                for (Arrow arrow : candidates) {
                    if (c.admitted.size() >= MagneticArrowSeparationRules.MAX_ARROWS) break;
                    if (arrow.getClass() != Arrow.class || arrow.getType() != EntityType.ARROW || c.admitted.contains(arrow.getUUID()) || leased(arrow) || !(arrow.getOwner() instanceof LivingEntity shooter)) continue;
                    Target target = new Target(arrow, shooter, p.serverLevel());
                    if (!eligible(c, target) || !turn(c, target, true)) {
                        if (CONTEXTS.get(p.getUUID()) != c) return;
                        continue;
                    }
                    c.targets.put(target.uuid, target); c.admitted.add(target.uuid);
                }
            }
        }
        if (CONTEXTS.get(p.getUUID()) == c) send(c, c.paid ? STEERING : ARMED);
    }
    private static boolean turn(Context c, Target target, boolean admission) {
        ServerPlayer p = c.identity.owner(); Arrow arrow = target.arrow; long tick = now(p);
        var result = MagneticArrowSeparationRules.turn(vector(arrow.getDeltaMovement()), vector(arrow.position()), vector(c.origin),
                vector(c.axis), vector(target.shooter.position()), target.side, target.turned, target.uuid);
        if (result == null) return false;
        Vec3 changed = vec(result.velocity());
        if (!movementPermitted(p, arrow, changed)) return false;
        Ledger ledger = quota(p, tick);
        if (ledger == null || admission && (ledger.entries.size() >= MagneticArrowSeparationRules.MAX_ARROWS
                || ledger.entries.stream().anyMatch(a -> a.arrow().equals(target.uuid)))) return false;
        // No transaction until a complete, nonzero native turn is ready. Captured cost/cooldown apply once.
        boolean committed = !c.paid;
        if (!c.paid) {
            if (!AbilityStorage.get(p).canUse(ID) || !ElectromasterPrototype.trySpend(p, ID, c.cost, c.cooldown)) { finish(c, "first_payment_refused"); return false; }
            c.paid = true;
        }
        if (CONTEXTS.get(p.getUUID()) != c || !valid(c) || !eligible(c, target)) return false;
        if (admission) {
            List<Admission> entries = new ArrayList<>(ledger.entries); entries.add(new Admission(target.uuid, tick));
            writeQuota(p, new Ledger(tick, List.copyOf(entries)));
            CompoundTag tag = new CompoundTag(); tag.putInt("version", 1); tag.putUUID("arrow", target.uuid); tag.putUUID("owner", p.getUUID());
            tag.putLong("since", tick); tag.putLong("until", tick + MagneticArrowSeparationRules.QUOTA_TICKS); tag.putDouble("turn", target.turned);
            arrow.getPersistentData().put(LEASE_KEY, tag);
            LEASES.put(target.uuid, new Lease(target, c, tick, tick + MagneticArrowSeparationRules.QUOTA_TICKS));
        }
        // Preserve Y, horizontal magnitude, owner, damage, pickup and all native metadata/physics.
        Vec3 before = arrow.getDeltaMovement();
        arrow.setDeltaMovement(changed); arrow.hasImpulse = true; arrow.hurtMarked = true;
        target.side = result.side(); target.turned += result.angle();
        // Lifetime NEW133 turn budget survives expiry, recast, caster changes and entity reload.
        arrow.getPersistentData().getCompound(LEASE_KEY).putDouble("turn", target.turned);
        if (admission) diagnostic(c, "admit", "arrow=" + target.entityId + " shooter=" + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.shooter.getType())
                + " tick=" + tick + " before=" + before + " after=" + changed + " angle=" + target.turned + " single_cp_commit=" + committed);
        return true;
    }
    private static boolean canAct(ServerPlayer p) {
        if (!thread(p)) return false;
        var s = AbilityStorage.get(p);
        return ElectromasterPrototype.ready(p) && s.activated && s.overloadFine && !s.interfering && s.learned(ID)
                && s.category.equals("electromaster") && s.level >= 3 && s.level <= 5 && ElectromasterPrototype.effectiveLevel(p) >= 3
                && SkillCatalog.enabled("electromaster", ID) && HighPowerCombat.unsupportedReason(p).isEmpty() && !AbilityConsumption.busy(p)
                && !p.noPhysics && !p.isPassenger() && !p.isSleeping() && finite(p.position()) && finite(p.getLookAngle())
                && Float.isFinite(p.getXRot()) && Float.isFinite(p.getYRot())
                && loadedArea(p.serverLevel(), p.getBoundingBox()) && permitted(p, p.blockPosition());
    }
    private static boolean valid(Context c) {
        ServerPlayer p = c.identity.owner();
        return c.identity.owns(p) && HighPowerBracerSession.authenticated(p, c.input) && canAct(p)
                && c.preset == c.identity.state().presets.current() && c.revision == c.identity.state().presets.revision()
                && ID.equals(c.identity.state().presets.currentSkill(c.input.slot()));
    }
    private static boolean eligible(Context c, Target target) {
        ServerPlayer p = c.identity.owner(); ServerLevel world = p.serverLevel(); Arrow arrow = target.arrow;
        if (target.world != world || arrow.level() != world || arrow.getClass() != Arrow.class || arrow.getType() != EntityType.ARROW
                || !arrow.getType().is(METAL) || arrow.isRemoved() || !arrow.isAlive() || arrow.getId() != target.entityId
                || !arrow.getUUID().equals(target.uuid) || world.getEntity(target.uuid) != arrow || world.getEntity(target.entityId) != arrow
                || arrow.inGround || arrow.isNoPhysics() || arrow.noPhysics || arrow.isNoGravity() || arrow.isPassenger() || arrow.isVehicle()
                || arrow.isInvulnerable() || arrow.isOnFire() || arrow.isCritArrow() || arrow.getPierceLevel() != 0
                || arrow.getType().is(PROTECTED) || arrow.getType().is(IMMOVABLE)
                || arrow.getPersistentData().getBoolean("academy:protected") || arrow.getPersistentData().getBoolean("academy:immovable")
                || otherLease(arrow) || !finite(arrow.position()) || !MagneticArrowSeparationRules.velocity(vector(arrow.getDeltaMovement()))
                || !MagneticArrowSeparationRules.inCorridor(vector(arrow.position()), vector(c.origin), vector(c.axis))
                || arrow.position().subtract(c.origin).dot(c.axis) > c.range
                || arrow.position().distanceToSqr(p.getEyePosition()) > MagneticArrowSeparationRules.RANGE * MagneticArrowSeparationRules.RANGE
                || !loadedArea(world, arrow.getBoundingBox()) || !permitted(p, arrow.blockPosition())) return false;
        var stack = arrow.getPickupItemStackOrigin(); var weapon = arrow.getWeaponItem();
        if (!stack.is(Items.ARROW) || stack.getCount() != 1
                || !stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).equals(PotionContents.EMPTY)
                || !stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()
                || weapon != null && (weapon.isEmpty() || !weapon.is(Items.BOW) && !weapon.is(Items.CROSSBOW)
                    || !weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty())
                || arrow.getGravity() != .05 || arrow.isInWaterOrBubble() || arrow.isInLava() || !world.getFluidState(arrow.blockPosition()).isEmpty()) return false;
        return enemy(p, target) && clearLine(p, p.getEyePosition(), arrow.position()) && clearLine(p, c.origin, arrow.position());
    }
    /** A loaded positive enemy classification is mandatory on every tick, not merely on admission. */
    private static boolean enemy(ServerPlayer p, Target target) {
        LivingEntity owner = target.shooter; ServerLevel world = p.serverLevel();
        if (target.arrow.getOwner() != owner || !owner.getUUID().equals(target.shooterUuid) || owner.getId() != target.shooterId
                || owner == p || owner.level() != world || !owner.isAlive() || owner.isRemoved() || owner.isSpectator()
                || world.getEntity(target.shooterUuid) != owner || world.getEntity(target.shooterId) != owner
                || !loadedArea(world, owner.getBoundingBox()) || !finite(owner.position()) || owner.isPassenger()
                || p.isAlliedTo(owner) || owner.isAlliedTo(p) || !HighPowerCombat.supported(owner)
                || HighPowerCombat.unsupportedReason(owner).isPresent()) return false;
        if (owner instanceof Player) {
            return owner.getClass() == ServerPlayer.class && owner instanceof ServerPlayer other
                    && other.connection != null && !other.hasDisconnected() && !other.getAbilities().invulnerable
                    && !other.getAbilities().flying && AcademyConfig.ATTACK_PLAYERS.get()
                    && p.server.isPvpAllowed() && p.canHarmPlayer(other) && other.canHarmPlayer(p);
        }
        return HighPowerCombat.hostileTarget(owner) || owner instanceof Mob mob && mob.getTarget() == p;
    }
    /** Fence both untouched native and proposed next rays; native collision/gravity still execute normally. */
    private static boolean movementPermitted(ServerPlayer p, Arrow arrow, Vec3 changed) {
        Vec3 at = arrow.position(), nativeEnd = at.add(arrow.getDeltaMovement()), changedEnd = at.add(changed);
        if (!finite(changed) || changed.y != arrow.getDeltaMovement().y) return false;
        AABB swept = arrow.getBoundingBox().expandTowards(changed).minmax(arrow.getBoundingBox().expandTowards(arrow.getDeltaMovement()));
        if (!loadedArea(p.serverLevel(), swept) || !p.serverLevel().getWorldBorder().isWithinBounds(swept)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(swept.minX, swept.minY, swept.minZ), BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ)))
            if (!permitted(p, pos) || !p.serverLevel().getFluidState(pos).isEmpty()) return false;
        return clearLine(p, at, nativeEnd) && clearLine(p, at, changedEnd);
    }
    private static boolean rayPermitted(ServerPlayer p, Vec3 start, Vec3 end) {
        AABB line = new AABB(start, end); ServerLevel world = p.serverLevel();
        if (!loadedArea(world, line) || !world.getWorldBorder().isWithinBounds(line)) return false;
        // The native voxel traversal covers corner cells too; sampling can miss these permissions.
        return permitted(p, BlockPos.containing(start)) && permitted(p, BlockPos.containing(end))
                && BlockGetter.traverseBlocks(start, end, p, (owner, pos) -> permitted(owner, pos) ? null : Boolean.FALSE, ignored -> Boolean.TRUE);
    }
    private static boolean clearLine(ServerPlayer p, Vec3 start, Vec3 end) {
        if (!rayPermitted(p, start, end)) return false;
        return p.serverLevel().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }
    private static boolean permitted(ServerPlayer p, BlockPos pos) {
        ServerLevel world = p.serverLevel(); return !world.isOutsideBuildHeight(pos) && world.hasChunkAt(pos)
                && world.getWorldBorder().isWithinBounds(pos) && world.mayInteract(p, pos);
    }
    private static boolean loadedArea(ServerLevel world, AABB box) {
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.maxX) || !Double.isFinite(box.minY) || !Double.isFinite(box.maxY)
                || !Double.isFinite(box.minZ) || !Double.isFinite(box.maxZ) || box.getXsize() > 30 || box.getYsize() > 30 || box.getZsize() > 30
                || box.minY < world.getMinBuildHeight() || box.maxY >= world.getMaxBuildHeight()) return false;
        for (int x = ((int)Math.floor(box.minX)) >> 4; x <= ((int)Math.floor(box.maxX)) >> 4; x++)
            for (int z = ((int)Math.floor(box.minZ)) >> 4; z <= ((int)Math.floor(box.maxZ)) >> 4; z++) if (!world.hasChunk(x, z)) return false;
        return true;
    }
    private static Ledger readQuota(ServerPlayer p, long tick) {
        CompoundTag data = p.getPersistentData();
        if (!MagneticArrowSeparationRules.clock(tick)) return null;
        if (!data.contains(QUOTA_KEY)) return new Ledger(tick, List.of());
        if (!data.contains(QUOTA_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = data.getCompound(QUOTA_KEY);
        if (!tag.getAllKeys().equals(Set.of("version", "observed", "entries")) || !tag.contains("version", Tag.TAG_INT)
                || tag.getInt("version") != 1 || !tag.contains("observed", Tag.TAG_LONG) || !tag.contains("entries", Tag.TAG_LIST)) return null;
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        if (!(tag.get("entries") instanceof ListTag raw) || raw.size() != list.size() || list.size() > MagneticArrowSeparationRules.MAX_ARROWS) return null;
        List<Admission> entries = new ArrayList<>();
        for (Tag element : list) {
            if (!(element instanceof CompoundTag entry) || !entry.getAllKeys().equals(Set.of("arrow", "tick"))
                    || !entry.hasUUID("arrow") || !entry.contains("tick", Tag.TAG_LONG)) return null;
            entries.add(new Admission(entry.getUUID("arrow"), entry.getLong("tick")));
        }
        List<Admission> active = MagneticArrowSeparationRules.window(entries, tag.getLong("observed"), tick);
        return active == null ? null : new Ledger(tick, active);
    }
    private static Ledger quota(ServerPlayer p, long tick) {
        Ledger saved = readQuota(p, tick), live = QUOTAS.get(p.getUUID());
        if (saved == null) return null;
        if (live == null) return saved;
        List<Admission> active = MagneticArrowSeparationRules.window(live.entries, live.observed, tick);
        if (active == null) return null;
        Map<UUID, Admission> merged = new HashMap<>();
        for (Admission entry : saved.entries) merged.put(entry.arrow(), entry);
        for (Admission entry : active) {
            Admission old = merged.putIfAbsent(entry.arrow(), entry);
            if (old != null && !old.equals(entry)) return null;
        }
        List<Admission> entries = merged.values().stream().sorted(Comparator.comparingLong(Admission::tick).thenComparing(Admission::arrow)).toList();
        return entries.size() > MagneticArrowSeparationRules.MAX_ARROWS ? null : new Ledger(tick, entries);
    }
    private static void writeQuota(ServerPlayer p, Ledger ledger) {
        CompoundTag tag = new CompoundTag(); tag.putInt("version", 1); tag.putLong("observed", ledger.observed);
        ListTag list = new ListTag(); for (Admission entry : ledger.entries) {
            CompoundTag item = new CompoundTag(); item.putUUID("arrow", entry.arrow()); item.putLong("tick", entry.tick()); list.add(item);
        }
        tag.put("entries", list); p.getPersistentData().put(QUOTA_KEY, tag); QUOTAS.put(p.getUUID(), ledger);
    }
    private static Context owned(ServerPlayer p) { if (!thread(p)) return null; Context c = CONTEXTS.get(p.getUUID()); return c != null && c.identity.owner() == p ? c : null; }
    private static boolean thread(ServerPlayer p) { return p != null && p.serverLevel().getServer().isSameThread(); }
    private static long now(ServerPlayer p) { return p.serverLevel().getServer().overworld().getGameTime(); }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static MagneticArrowSeparationRules.Vector vector(Vec3 v) { return new MagneticArrowSeparationRules.Vector(v.x, v.y, v.z); }
    private static Vec3 vec(MagneticArrowSeparationRules.Vector v) { return new Vec3(v.x(), v.y(), v.z()); }
    private static void diagnostic(Context c, String event, String detail) {
        if (!DIAGNOSTICS || c.diagnostics >= 6 || !DIAGNOSTIC_TOKENS.contains(c.identity.token()) && DIAGNOSTIC_TOKENS.size() >= 128) return;
        DIAGNOSTIC_TOKENS.add(c.identity.token()); c.diagnostics++;
        DIAGNOSTIC_LOG.info("NEW133_ARROW token={} stage={} {}", c.identity.token(), event, detail);
    }
    private static void finish(Context c) { finish(c, "owned_abort"); }
    private static void finish(Context c, String reason) {
        if (!CONTEXTS.remove(c.identity.owner().getUUID(), c)) return;
        diagnostic(c, "end", "reason=" + reason + " elapsed=" + (now(c.identity.owner()) - c.started) + " paid=" + c.paid + " admitted=" + c.admitted.size() + " examined=" + c.examined);
        send(c, ENDED); c.targets.clear(); c.audience.clear();
    }
    private static void send(Context c, int phase) {
        ServerPlayer owner = c.identity.owner();
        for (ServerPlayer viewer : c.identity.level().players()) if (viewer.position().distanceToSqr(c.origin) <= 40 * 40) c.audience.add(viewer);
        c.audience.add(owner);
        Visual packet = new Visual(owner.getId(), owner.getUUID(), c.input.input(), c.identity.token(), c.identity.epoch(), ++c.sequence,
                phase, c.origin, c.axis, c.range, phase == ENDED ? List.of() : List.copyOf(c.targets.keySet()));
        for (ServerPlayer viewer : c.audience) if (viewer.connection != null && !viewer.hasDisconnected() && viewer.serverLevel() == c.identity.level()) PacketDistributor.sendToPlayer(viewer, packet);
    }
    private static List<UUID> readTargets(RegistryFriendlyByteBuf b) {
        int count = b.readVarInt(); if (count < 0 || count > MagneticArrowSeparationRules.MAX_ARROWS) throw new DecoderException("NEW133 arrow visual bound exceeded");
        List<UUID> ids = new ArrayList<>(count); for (int i = 0; i < count; i++) ids.add(b.readUUID()); return ids;
    }
    public record Visual(int entity, UUID owner, long input, long token, long epoch, long sequence, int phase, Vec3 origin, Vec3 axis, double range, List<UUID> targets) implements CustomPacketPayload {
        public Visual { targets = List.copyOf(targets); if (targets.size() > MagneticArrowSeparationRules.MAX_ARROWS) throw new IllegalArgumentException("NEW133 arrow visual bound exceeded"); }
        public static final Type<Visual> TYPE = new Type<>(id("hp_magnetic_arrow_separation"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Visual> CODEC = StreamCodec.of((b, v) -> {
            b.writeInt(v.entity); b.writeUUID(v.owner); b.writeLong(v.input); b.writeLong(v.token); b.writeLong(v.epoch); b.writeLong(v.sequence); b.writeByte(v.phase);
            b.writeDouble(v.origin.x); b.writeDouble(v.origin.y); b.writeDouble(v.origin.z); b.writeDouble(v.axis.x); b.writeDouble(v.axis.y); b.writeDouble(v.axis.z); b.writeDouble(v.range);
            b.writeVarInt(v.targets.size()); v.targets.forEach(b::writeUUID);
        }, b -> new Visual(b.readInt(), b.readUUID(), b.readLong(), b.readLong(), b.readLong(), b.readLong(), b.readUnsignedByte(),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readDouble(), readTargets(b)));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e) { e.registrar("1").playToClient(Visual.TYPE, Visual.CODEC, (packet, context) -> clientReceiver.accept(packet)); }
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post e) {
        for (Context c : new ArrayList<>(CONTEXTS.values())) if (c.identity.level().getServer() == e.getServer()) tick(c.identity.owner());
        long tick = e.getServer().overworld().getGameTime();
        LEASES.entrySet().removeIf(e1 -> tick >= e1.getValue().until);
        QUOTAS.entrySet().removeIf(e1 -> { Ledger ledger = e1.getValue(); return tick >= ledger.observed && ledger.entries.stream().allMatch(a -> tick - a.tick() >= MagneticArrowSeparationRules.QUOTA_TICKS); });
    }
    /** Diagnostic-only post-damage witness. Logs the actual native event, never creates or changes damage. */
    @SubscribeEvent public static void baselineHit(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e) {
        if (!DIAGNOSTICS || baselineDiagnosticHits >= 16 || !(e.getEntity() instanceof ServerPlayer p) || active(p)
                || !(e.getSource().getDirectEntity() instanceof Arrow arrow) || arrow.getClass() != Arrow.class
                || !(arrow.getOwner() instanceof net.minecraft.world.entity.monster.Skeleton shooter)
                || shooter.getClass() != net.minecraft.world.entity.monster.Skeleton.class
                || arrow.level() != p.serverLevel() || !arrow.getPickupItemStackOrigin().is(Items.ARROW)
                || !arrow.getPickupItemStackOrigin().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).equals(PotionContents.EMPTY)
                || arrow.isNoGravity() || arrow.isNoPhysics() || arrow.isCritArrow() || arrow.isOnFire() || arrow.getPierceLevel() != 0
                || arrow.getWeaponItem() != null && !arrow.getWeaponItem().getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()) return;
        baselineDiagnosticHits++;
        DIAGNOSTIC_LOG.info("NEW133_ARROW stage=baseline_native_hit arrow={} shooter=minecraft:skeleton tick={} velocity={} original_damage={} native_health_damage={}",
                arrow.getId(), now(p), arrow.getDeltaMovement(), e.getOriginalDamage(), e.getNewDamage());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        if (e.getOriginal() instanceof ServerPlayer old) {
            remove(old);
            if (e.getEntity() instanceof ServerPlayer replacement && replacement.getUUID().equals(old.getUUID()) && old.getPersistentData().contains(QUOTA_KEY))
                replacement.getPersistentData().put(QUOTA_KEY, old.getPersistentData().get(QUOTA_KEY).copy());
        }
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void death(LivingDeathEvent e) { if (!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { clear(); }
}
