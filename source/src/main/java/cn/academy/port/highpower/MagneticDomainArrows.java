package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.MagneticDomainArrowRules;
import cn.academy.port.highpower.core.MagneticDomainArrowRules.Admission;
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

import java.math.BigDecimal;
import java.util.function.BooleanSupplier;

/** NEW152 bounded native arrow writer. A payment lease never survives its owning domain activation. */
public final class MagneticDomainArrows {
    public static final String QUOTA_KEY = "academy:magnetic_domain_arrow_quota", LEASE_KEY = "academy:magnetic_domain_arrow_lease";
    private static final TagKey<EntityType<?>> METAL = TagKey.create(Registries.ENTITY_TYPE, id("magnetic_projectiles"));
    private static final TagKey<EntityType<?>> PROTECTED = TagKey.create(Registries.ENTITY_TYPE, id("magnetic_pull_protected"));
    private static final TagKey<EntityType<?>> IMMOVABLE = TagKey.create(Registries.ENTITY_TYPE, id("magnetic_pull_immovable"));
    private static final Map<ServerPlayer, Context> CONTEXTS = new IdentityHashMap<>();
    private static final Map<UUID, Ledger> QUOTAS = new HashMap<>();
    private static final Map<UUID, Lease> LEASES = new HashMap<>();
    private record Ledger(long observed, List<Admission> entries) {}
    private record Lease(Target target, Context context, long since, long until) {}
    private static final class Target {
        final Arrow arrow; final UUID uuid; final int entityId; final ServerLevel world;
        final LivingEntity shooter; final UUID shooterUuid; final int shooterId;
        int side; double turned;
        Target(Arrow arrow, LivingEntity shooter, ServerLevel world) {
            this.arrow=arrow; uuid=arrow.getUUID(); entityId=arrow.getId(); this.world=world;
            this.shooter=shooter; shooterUuid=shooter.getUUID(); shooterId=shooter.getId();
        }
    }
    private static final class Context {
        final ServerPlayer owner; final Object token; final ServerLevel world; final BooleanSupplier valid;
        final Map<UUID,Target> targets = new LinkedHashMap<>();
        long lastTick=-1;
        Context(ServerPlayer p,Object token,BooleanSupplier valid) {owner=p;this.token=token;world=p.serverLevel();this.valid=valid;}
    }
    private MagneticDomainArrows() {}
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("academy",name); }
    public static void tick(ServerPlayer p,Object token,BooleanSupplier valid,Consumer<Vec3> receipt) {
        if (!thread(p)||token==null||valid==null||!valid.getAsBoolean()) return;
        long tick=now(p); if(quota(p,tick)==null)return;
        Context c=CONTEXTS.get(p);
        if(c==null||c.token!=token) { if(c!=null)c.targets.clear(); c=new Context(p,token,valid); CONTEXTS.put(p,c); }
        if(tick<=c.lastTick)return; c.lastTick=tick;
        int examined=0;
        for(Iterator<Target> it=c.targets.values().iterator();it.hasNext();) {
            Target target=it.next();examined++;
            if(!valid(c)||!ownsLease(c,target,tick)||!eligible(c,target)||!turn(c,target,false,receipt))it.remove();
        }
        if(!valid(c))return;
        Ledger ledger=quota(p,tick); if(ledger==null||ledger.entries.size()>=MagneticDomainArrowRules.MAX_ARROWS)return;
        List<Arrow> scanned=new ArrayList<>(); Vec3 origin=MagneticDomain.center(p);
        p.serverLevel().getEntities(EntityTypeTest.forClass(Arrow.class),new AABB(origin,origin).inflate(MagneticDomain.RADIUS),a->true,scanned,MagneticDomainArrowRules.SCAN_LIMIT-examined);
        scanned.sort(Comparator.<Arrow>comparingDouble(a->a.position().distanceToSqr(origin)).thenComparing(Entity::getUUID));
        for(Arrow arrow:scanned) {
            ledger=quota(p,tick);if(!valid(c)||ledger==null||ledger.entries.size()>=MagneticDomainArrowRules.MAX_ARROWS)break;
            if(arrow.getClass()!=Arrow.class||arrow.getType()!=EntityType.ARROW||c.targets.containsKey(arrow.getUUID())||arrow.getPersistentData().contains(LEASE_KEY)||leased(arrow)||!(arrow.getOwner() instanceof LivingEntity shooter))continue;
            Target target=new Target(arrow,shooter,p.serverLevel());
            if(eligible(c,target)&&turn(c,target,true,receipt))c.targets.put(target.uuid,target);
        }
        prune(p.server);
    }
    static void prune(net.minecraft.server.MinecraftServer server) {
        long tick=MagneticDomain.serverTime(server);if(!MagneticDomainArrowRules.clock(tick))return;
        LEASES.entrySet().removeIf(e->e.getValue().target.world.getServer()==server&&tick>=e.getValue().until);
        QUOTAS.entrySet().removeIf(e->tick>=e.getValue().observed&&e.getValue().entries.stream().allMatch(a->tick-a.tick()>=MagneticDomainArrowRules.QUOTA_TICKS));
    }
    public static int count(ServerPlayer p) { if(!thread(p))return 0; Ledger l=quota(p,now(p));return l==null?0:l.entries.size(); }
    public static void release(ServerPlayer p,Object token) { Context c=CONTEXTS.get(p);if(c!=null&&c.token==token){CONTEXTS.remove(p);c.targets.clear();} }
    public static void clear() { CONTEXTS.clear();QUOTAS.clear();LEASES.clear(); }
    public static void copyQuota(ServerPlayer from,ServerPlayer to) { if(from.getPersistentData().contains(QUOTA_KEY))to.getPersistentData().put(QUOTA_KEY,from.getPersistentData().get(QUOTA_KEY).copy()); }
    private static boolean valid(Context c) { return CONTEXTS.get(c.owner)==c&&c.owner.serverLevel()==c.world&&c.valid.getAsBoolean(); }
    private static boolean turn(Context c,Target target,boolean admission,Consumer<Vec3> receipt) {
        ServerPlayer p=c.owner; Arrow arrow=target.arrow; long tick=now(p);
        Vec3 origin=MagneticDomain.center(p),axis=p.getLookAngle().normalize(),before=arrow.getDeltaMovement(),position=arrow.position();
        var result=MagneticDomainArrowRules.turn(vector(before),vector(position),vector(origin),vector(axis),vector(target.shooter.position()),target.side,target.turned,target.uuid);
        if(result==null)return false;Vec3 changed=vec(result.velocity());
        if(!movementPermitted(p,arrow,changed))return false;
        Ledger ledger=quota(p,tick);
        if(ledger==null||admission&&(ledger.entries.size()>=MagneticDomainArrowRules.MAX_ARROWS||ledger.entries.stream().anyMatch(a->a.arrow().equals(target.uuid))))return false;
        CompoundTag leaseTag=null;Ledger next=null;
        if(admission) {
            List<Admission> entries=new ArrayList<>(ledger.entries);entries.add(new Admission(target.uuid,tick));next=new Ledger(tick,List.copyOf(entries));
            leaseTag=new CompoundTag();leaseTag.putInt("version",1);leaseTag.putUUID("arrow",target.uuid);leaseTag.putUUID("owner",p.getUUID());
            leaseTag.putLong("since",tick);leaseTag.putLong("until",tick+MagneticDomainArrowRules.QUOTA_TICKS);leaseTag.putDouble("turn",result.angle());
            BigDecimal cost=MagneticDomainReserve.capacity(p).multiply(new BigDecimal("0.01"));
            if(cost.signum()<=0||!MagneticDomainReserve.debitArrow(p,cost,()->valid(c)&&now(p)==tick&&eligible(c,target)
                    &&arrow.getDeltaMovement().equals(before)&&arrow.position().equals(position)&&MagneticDomain.center(p).equals(origin)
                    &&p.getLookAngle().normalize().equals(axis)&&!leased(arrow)&&movementPermitted(p,arrow,changed)))return false;
            // Closed debit and these bounded NBT/velocity writes contain no callbacks. No paid no-op or unpaid lease.
            writeQuota(p,next);arrow.getPersistentData().put(LEASE_KEY,leaseTag);LEASES.put(target.uuid,new Lease(target,c,tick,tick+MagneticDomainArrowRules.QUOTA_TICKS));
        } else if(!valid(c)||!ownsLease(c,target,tick)||!eligible(c,target)||!arrow.getDeltaMovement().equals(before)||!arrow.position().equals(position))return false;
        arrow.setDeltaMovement(changed);arrow.hasImpulse=true;arrow.hurtMarked=true;
        target.side=result.side();target.turned+=result.angle();arrow.getPersistentData().getCompound(LEASE_KEY).putDouble("turn",target.turned);
        if(admission)receipt.accept(position);return true;
    }
    /** Read-only reciprocal writer guard. Malformed/future records fail closed, even after reload/abort. */
    public static boolean leased(AbstractArrow arrow) {
        if (arrow == null || !(arrow.level() instanceof ServerLevel world) || !world.getServer().isSameThread()) return true;
        long tick = MagneticDomain.serverTime(world.getServer());
        if (!MagneticDomainArrowRules.clock(tick)) return true;
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
                && Double.isFinite(tag.getDouble("turn")) && tag.getDouble("turn") >= 0 && tag.getDouble("turn") <= MagneticDomainArrowRules.MAX_TURN
                && MagneticDomainArrowRules.clock(tag.getLong("since")) && tag.getLong("since") <= now
                && tag.getLong("until") == tag.getLong("since") + MagneticDomainArrowRules.QUOTA_TICKS;
    }
    private static boolean ownsLease(Context c, Target target, long tick) {
        Lease live = LEASES.get(target.uuid); CompoundTag data = target.arrow.getPersistentData();
        if (live == null || live.context != c || live.target != target || tick < live.since || tick >= live.until
                || !data.contains(LEASE_KEY, Tag.TAG_COMPOUND)) return false;
        CompoundTag tag = data.getCompound(LEASE_KEY);
        return validLeaseTag(tag, target.uuid, tick) && tag.getUUID("owner").equals(c.owner.getUUID())
                && tag.getLong("since") == live.since && tag.getLong("until") == live.until && tag.getDouble("turn") == target.turned;
    }
    private static boolean otherLease(AbstractArrow arrow) {
        return MultiAxisReflectionArrowBridge.leased(arrow) || TrajectoryReturnArrowBridge.leased(arrow) || ShortInterceptionArrowBridge.leased(arrow) || MagneticArrowSeparation.leased(arrow) || IronSandLance.leased(arrow) || ElectromasterPrototype.leased(arrow) || ElectromasterChargeDecoy.leased(arrow) || MagneticGroundStep.leased(arrow);
    }

    private static boolean eligible(Context c, Target target) {
        ServerPlayer p = c.owner; ServerLevel world = p.serverLevel(); Arrow arrow = target.arrow;
        if (target.world != world || arrow.level() != world || arrow.getClass() != Arrow.class || arrow.getType() != EntityType.ARROW
                || !arrow.getType().is(METAL) || arrow.isRemoved() || !arrow.isAlive() || arrow.getId() != target.entityId
                || !arrow.getUUID().equals(target.uuid) || world.getEntity(target.uuid) != arrow || world.getEntity(target.entityId) != arrow
                || arrow.inGround || arrow.isNoPhysics() || arrow.noPhysics || arrow.isNoGravity() || arrow.isPassenger() || arrow.isVehicle()
                || arrow.isInvulnerable() || arrow.isOnFire() || arrow.isCritArrow() || arrow.getPierceLevel() != 0
                || arrow.getType().is(PROTECTED) || arrow.getType().is(IMMOVABLE)
                || arrow.getPersistentData().getBoolean("academy:protected") || arrow.getPersistentData().getBoolean("academy:immovable")
                || otherLease(arrow) || !finite(arrow.position()) || !MagneticDomainArrowRules.velocity(vector(arrow.getDeltaMovement()))
                || !MagneticDomainArrowRules.inCorridor(vector(arrow.position()), vector(MagneticDomain.center(p)), vector(p.getLookAngle().normalize()))
                || arrow.position().distanceToSqr(MagneticDomain.center(p)) > MagneticDomainArrowRules.RANGE * MagneticDomainArrowRules.RANGE
                || !loadedArea(world, arrow.getBoundingBox()) || !permitted(p, arrow.blockPosition())) return false;
        var stack = arrow.getPickupItemStackOrigin(); var weapon = arrow.getWeaponItem();
        if (!stack.is(Items.ARROW) || stack.getCount() != 1
                || !stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).equals(PotionContents.EMPTY)
                || !stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()
                || weapon != null && (weapon.isEmpty() || !weapon.is(Items.BOW) && !weapon.is(Items.CROSSBOW)
                    || !weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty())
                || arrow.getGravity() != .05 || arrow.isInWaterOrBubble() || arrow.isInLava() || !world.getFluidState(arrow.blockPosition()).isEmpty()) return false;
        return enemy(p, target) && clearLine(p, p.getEyePosition(), arrow.position()) && clearLine(p, MagneticDomain.center(p), arrow.position());
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
        if (!MagneticDomainArrowRules.clock(tick)) return null;
        if (!data.contains(QUOTA_KEY)) return new Ledger(tick, List.of());
        if (!data.contains(QUOTA_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = data.getCompound(QUOTA_KEY);
        if (!tag.getAllKeys().equals(Set.of("version", "observed", "entries")) || !tag.contains("version", Tag.TAG_INT)
                || tag.getInt("version") != 1 || !tag.contains("observed", Tag.TAG_LONG) || !tag.contains("entries", Tag.TAG_LIST)) return null;
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        if (!(tag.get("entries") instanceof ListTag raw) || raw.size() != list.size() || list.size() > MagneticDomainArrowRules.MAX_ARROWS) return null;
        List<Admission> entries = new ArrayList<>();
        for (Tag element : list) {
            if (!(element instanceof CompoundTag entry) || !entry.getAllKeys().equals(Set.of("arrow", "tick"))
                    || !entry.hasUUID("arrow") || !entry.contains("tick", Tag.TAG_LONG)) return null;
            entries.add(new Admission(entry.getUUID("arrow"), entry.getLong("tick")));
        }
        List<Admission> active = MagneticDomainArrowRules.window(entries, tag.getLong("observed"), tick);
        return active == null ? null : new Ledger(tick, active);
    }
    private static Ledger quota(ServerPlayer p, long tick) {
        Ledger saved = readQuota(p, tick), live = QUOTAS.get(p.getUUID());
        if (saved == null) return null;
        if (live == null) return saved;
        List<Admission> active = MagneticDomainArrowRules.window(live.entries, live.observed, tick);
        if (active == null) return null;
        Map<UUID, Admission> merged = new HashMap<>();
        for (Admission entry : saved.entries) merged.put(entry.arrow(), entry);
        for (Admission entry : active) {
            Admission old = merged.putIfAbsent(entry.arrow(), entry);
            if (old != null && !old.equals(entry)) return null;
        }
        List<Admission> entries = merged.values().stream().sorted(Comparator.comparingLong(Admission::tick).thenComparing(Admission::arrow)).toList();
        return entries.size() > MagneticDomainArrowRules.MAX_ARROWS ? null : new Ledger(tick, entries);
    }
    private static void writeQuota(ServerPlayer p, Ledger ledger) {
        CompoundTag tag = new CompoundTag(); tag.putInt("version", 1); tag.putLong("observed", ledger.observed);
        ListTag list = new ListTag(); for (Admission entry : ledger.entries) {
            CompoundTag item = new CompoundTag(); item.putUUID("arrow", entry.arrow()); item.putLong("tick", entry.tick()); list.add(item);
        }
        tag.put("entries", list); p.getPersistentData().put(QUOTA_KEY, tag); QUOTAS.put(p.getUUID(), ledger);
    }
    private static boolean thread(ServerPlayer p) { return p!=null&&p.server!=null&&p.server.isSameThread(); }
    private static long now(ServerPlayer p) { return MagneticDomain.serverTime(p.server); }
    private static boolean finite(Vec3 v) { return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z); }
    private static MagneticDomainArrowRules.Vector vector(Vec3 v) { return new MagneticDomainArrowRules.Vector(v.x,v.y,v.z); }
    private static Vec3 vec(MagneticDomainArrowRules.Vector v) { return new Vec3(v.x(),v.y(),v.z()); }
}
