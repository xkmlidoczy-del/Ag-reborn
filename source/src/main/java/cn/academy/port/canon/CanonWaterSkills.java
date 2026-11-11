package cn.academy.port.canon;

import static cn.academy.port.core.SkillTuning.integer;
import static cn.academy.port.core.SkillTuning.number;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.*;
import cn.academy.port.skill.ClassicRaytrace;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Explicit local water-medium physics. Water remains a real, protected source block and is
 * never copied into the world. Currents affect only bounded, visible vanilla hostile mobs;
 * the one-shot shield diverts one supported hostile arrow and spends the exact base shield.
 */
@EventBusSubscriber(modid="academy")
public final class CanonWaterSkills {
    public static final Set<String> IMPLEMENTED = Set.of("hydro_whip", "hydro_shield", "hydro_domain", "hydro_transfer", "hydro_irrigate", "hydro_extinguish", "hydro_current", "hydro_lift", "hydro_brake", "hydro_repel", "hydro_rescue");
    private static final String CATEGORY = "hydro_hand";
    private static final int SOURCE_RANGE = integer("hydro_whip", "source_reach");
    private static final double ATTACK_RANGE = cn.academy.port.core.RangedSkillRanges.fixed("hydro_whip");
    private static final double DOMAIN_RANGE = number("hydro_domain", "radius");
    private static final double SHIELD_RADIUS = number("hydro_shield", "radius");
    private static final int MAX_SHIELDS = 64, MAX_FLOWS = 64, MAX_TARGETS = integer("hydro_domain", "target_cap"), MAX_CANDIDATES = 64;
    private static final BigDecimal SHIELD_SHARE = new BigDecimal("0.05");
    private static final Map<UUID, Shield> SHIELDS = new HashMap<>();
    private static final Map<UUID, Flow> FLOWS = new HashMap<>();
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private record Shield(ServerPlayer owner, ServerLevel world, BlockPos source, long expires) {}
    private record Flow(ServerPlayer owner, ServerLevel world, BlockPos source, List<UUID> targets, Vec3 direction, long expires) {}
    private record Selection(ServerPlayer owner, ServerLevel world, BlockPos source, long expires) {}
    private CanonWaterSkills() {}

    /** The client supplies only its already-mapped skill, never a target, medium or coordinates. */
    public static boolean cast(ServerPlayer p, String id) {
        if ("hydro_transfer".equals(id)) return CanonWaterTransfer.cast(p,id);
        if (CanonWaterExtra.IMPLEMENTED.contains(id)) return CanonWaterExtra.cast(p,id);
        if (CanonWaterMotion.IMPLEMENTED.contains(id)) return CanonWaterMotion.cast(p,id);
        if (id == null || !IMPLEMENTED.contains(id) || !ready(p, id)) return false;
        if (id.equals("hydro_whip") && p.isShiftKeyDown()) return selectSource(p);
        var world = p.serverLevel(); Vec3 eye = p.getEyePosition(), direction = ClassicRaytrace.direction(p);
        if (!finite(eye) || !finite(direction) || !chunksLoaded(world, eye, eye.add(direction.scale(ATTACK_RANGE)))) return false;
        BlockPos source = source(p); if (source == null) return false;
        List<LivingEntity> targets = id.equals("hydro_whip") ? whipTarget(p)
                : id.equals("hydro_domain") ? domainTargets(p) : List.of();
        if (!id.equals("hydro_shield") && targets.isEmpty()) return false;
        if (id.equals("hydro_shield") && (SHIELDS.containsKey(p.getUUID()) || SHIELDS.size() >= MAX_SHIELDS)) return false;
        if (id.equals("hydro_domain") && (FLOWS.containsKey(p.getUUID()) || FLOWS.size() >= MAX_FLOWS)) return false;
        Vec3 horizontal = new Vec3(direction.x, 0, direction.z);
        if (id.equals("hydro_domain") && horizontal.lengthSqr() < 1e-8) return false;
        long now = world.getGameTime(); int ticks = id.equals("hydro_whip") ? 0 : integer(id, "duration_ticks");
        if (now < 0 || now > Long.MAX_VALUE - ticks || !validSource(p, source)) return false;
        var growth = HighPowerGrowth.values(p);
        double cost = growth.arcCost() * (id.equals("hydro_domain") ? 4 : id.equals("hydro_shield") ? 2 : 1);
        int cooldown = id.equals("hydro_domain") ? 100 : id.equals("hydro_shield") ? 80 : 30;
        CanonEmission.Cast emission = id.equals("hydro_whip") ? CanonEmission.begin(p, id, cost, cooldown, targets, target -> validSource(p, source)
                && eligible(p, target) && p.hasLineOfSight(target) && whipTarget(p).contains(target)) : null;
        if (id.equals("hydro_whip") ? emission == null : !HighPowerResources.trySpend(p, id, cost, cooldown)) return false;
        boolean successful = true;
        if (id.equals("hydro_shield")) {
            SHIELDS.put(p.getUUID(), new Shield(p, world, source, now + ticks));
            CanonVfx.emit(p,id,source.getCenter(),p.position(),CanonVfx.Phase.START,ticks);
        }
        else if (id.equals("hydro_domain")) {
            FLOWS.put(p.getUUID(), new Flow(p, world, source, targets.stream().map(Entity::getUUID).toList(), horizontal.normalize(), now + ticks));
            CanonVfx.emit(p,id,source.getCenter(),targets.getFirst().position(),CanonVfx.Phase.START,ticks,id.equals("hydro_domain")?domainRadius(p):0);
        } else {
            LivingEntity target = targets.getFirst();
            Vec3 push = target.position().subtract(p.position()); push = new Vec3(push.x, 0, push.z);
            boolean hit = emission.attack(target); successful = hit;
            if(hit) CanonVfx.emit(p,id,source.getCenter(),target.getBoundingBox().getCenter(),CanonVfx.Phase.HIT,12);
            if (hit && push.lengthSqr() > 1e-8 && validSource(p, source) && eligible(p, target) && p.hasLineOfSight(target)) {
                push = push.normalize().scale(number(id, "horizontal_speed")); Vec3 previous = target.getDeltaMovement();
                target.setDeltaMovement(push.x, Math.max(previous.y, .18), push.z);
                target.hasImpulse = true; target.hurtMarked = true;
            }
            waterLine(world, Vec3.atCenterOf(source), p.getEyePosition(), target.getBoundingBox().getCenter());
        }
        var state = AbilityStorage.get(p);
        if (successful && CATEGORY.equals(state.category) && state.learned(id)) { state.addExperience(id, .01); AbilityStorage.save(p); }
        return true;
    }

    private static boolean ready(ServerPlayer p, String id) {
        if (p == null || p.connection == null || !p.server.isSameThread() || p.hasDisconnected()
                || p.server.getPlayerList().getPlayer(p.getUUID()) != p || p.isRemoved() || !p.isAlive()
                || p.isSpectator() || AbilityConsumption.busy(p) || HighPowerCombat.unsupportedReason(p).isPresent()
                || !HighPowerResources.ready(p)) return false;
        var state = AbilityStorage.get(p);
        int required = id.equals("hydro_whip") ? 1 : id.equals("hydro_shield") ? 3 : 5;
        return CATEGORY.equals(state.category) && state.level >= required && state.canUse(id)
                && state.presets.currentContains(id) && SkillCatalog.enabled(CATEGORY, id);
    }

    static BlockPos source(ServerPlayer p) {
        var world = p.serverLevel(); Vec3 center = p.position();
        Selection selected = SELECTIONS.get(p.getUUID());
        if (selected != null) {
            if (selected.owner != p || selected.world != world || world.getGameTime() >= selected.expires) {
                SELECTIONS.remove(p.getUUID(), selected);
            } else if (!selectableSource(p, selected.source)) {
                SELECTIONS.remove(p.getUUID(), selected);
                p.displayClientMessage(Component.translatable("ac.canon.water_invalid"), true);
                return null;
            } else return selected.source;
        }
        if (!finite(center) || !chunksLoaded(world, center.add(-SOURCE_RANGE, 0, -SOURCE_RANGE), center.add(SOURCE_RANGE, 0, SOURCE_RANGE))) return null;
        BlockPos base = p.blockPosition(); BlockPos best = null; double bestDistance = SOURCE_RANGE * SOURCE_RANGE;
        for (BlockPos candidate : BlockPos.betweenClosed(base.offset(-SOURCE_RANGE, -SOURCE_RANGE, -SOURCE_RANGE), base.offset(SOURCE_RANGE, SOURCE_RANGE, SOURCE_RANGE))) {
            double distance = center.distanceToSqr(Vec3.atCenterOf(candidate));
            if (distance > bestDistance || !validSource(p, candidate)) continue;
            best = candidate.immutable(); bestDistance = distance;
        }
        return best;
    }

    /** Select a real source using the server's own ray; no world block is removed or placed. */
    private static boolean selectSource(ServerPlayer p) {
        ServerLevel world = p.serverLevel(); Vec3 eye = p.getEyePosition(), look = ClassicRaytrace.direction(p);
        if (!finite(eye) || !finite(look) || !chunksLoaded(world, eye, eye.add(look.scale(SOURCE_RANGE)))
                || !p.getAbilities().mayBuild || (!SELECTIONS.containsKey(p.getUUID()) && SELECTIONS.size() >= MAX_FLOWS)) return false;
        var hit = world.clip(new ClipContext(eye, eye.add(look.scale(SOURCE_RANGE)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.SOURCE_ONLY, p));
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos source = block.getBlockPos().immutable(); long now = world.getGameTime();
        if (now < 0 || now > Long.MAX_VALUE - 200 || !selectableSource(p, source)) return false;
        var state = AbilityStorage.get(p); int preset = state.presets.current(); long revision = state.presets.revision();
        double cost = HighPowerGrowth.values(p).arcCost() * .25;
        if (!Double.isFinite(cost) || cost <= 0 || !HighPowerResources.trySpend(p, "hydro_whip", cost, 10)) return false;
        if (p.serverLevel() != world || AbilityStorage.get(p) != state || state.presets.current() != preset
                || state.presets.revision() != revision || !selectableSource(p, source)) return false;
        SELECTIONS.put(p.getUUID(), new Selection(p, world, source, now + 200));
        p.displayClientMessage(Component.translatable("ac.canon.water_selected"), true);
        return true;
    }

    private static boolean selectableSource(ServerPlayer p, BlockPos pos) {
        ServerLevel world = p.serverLevel();
        return p.getAbilities().mayBuild && pos != null && world.hasChunkAt(pos)
                && !world.isOutsideBuildHeight(pos) && world.getWorldBorder().isWithinBounds(pos)
                && world.getBlockState(pos).is(Blocks.WATER) && validSource(p, pos);
    }

    static boolean validSource(ServerPlayer p, BlockPos source) {
        var world = p.serverLevel();
        if (source == null || !world.hasChunkAt(source) || p.distanceToSqr(Vec3.atCenterOf(source)) > SOURCE_RANGE * SOURCE_RANGE
                || !world.mayInteract(p, source) || !chunksLoaded(world, p.getEyePosition(), Vec3.atCenterOf(source))) return false;
        var fluid = world.getFluidState(source);
        if (!fluid.is(FluidTags.WATER) || !fluid.isSource()) return false;
        return world.clip(new ClipContext(p.getEyePosition(), Vec3.atCenterOf(source), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
    }

    private static List<LivingEntity> whipTarget(ServerPlayer p) {
        var hit = ClassicRaytrace.living(p, ATTACK_RANGE, ClipContext.Fluid.NONE);
        if (!(hit instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof LivingEntity target)
                || !eligible(p, target) || !p.hasLineOfSight(target)) return List.of();
        return List.of(target);
    }

    private static double domainRadius(ServerPlayer p){return cn.academy.port.macro.MacroSkillRules.area("hydro_domain",AbilityStorage.get(p).exp("hydro_domain"),DOMAIN_RANGE);}
    private static List<LivingEntity> domainTargets(ServerPlayer p) {
        var world = p.serverLevel(); var at = p.position();

        var candidates = world.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(domainRadius(p)),
                mob -> eligible(p, mob) && mob.isInWaterOrBubble());

        candidates.sort(Comparator.comparing(Mob::getUUID)); var result = new ArrayList<LivingEntity>();
        for (Mob mob : candidates) {
            if (result.size() == MAX_TARGETS) break;
            if (mob.distanceToSqr(p) <= domainRadius(p) * domainRadius(p) && p.hasLineOfSight(mob)) result.add(mob);
        }
        return List.copyOf(result);
    }

    private static boolean eligible(ServerPlayer owner, LivingEntity target) {
        if (!(target instanceof Mob) || !CanonTargetPolicy.hostile(target) || !target.isAlive() || target.isRemoved()
                || target.isPassenger() || target.isVehicle() || target.isAlliedTo(owner) || owner.isAlliedTo(target)
                || !finite(target.position()) || !finite(target.getDeltaMovement())) return false;
        var type = target.getType();
        return CanonTargetPolicy.reviewed(target) || BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("minecraft") && type != EntityType.WITHER
                && type != EntityType.ENDER_DRAGON && type != EntityType.WARDEN && type != EntityType.ELDER_GUARDIAN;
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for (Selection selection : List.copyOf(SELECTIONS.values())) {
            ServerPlayer owner = selection.owner;
            if (owner.server != event.getServer()) continue;
            var state = AbilityStorage.get(owner);
            if (owner.isRemoved() || !owner.isAlive() || owner.hasDisconnected() || owner.serverLevel() != selection.world
                    || owner.server.getPlayerList().getPlayer(owner.getUUID()) != owner
                    || selection.world.getGameTime() >= selection.expires || !CATEGORY.equals(state.category))
                SELECTIONS.remove(owner.getUUID(), selection);
        }
        for (Shield shield : List.copyOf(SHIELDS.values())) {
            if (shield.owner.server != event.getServer()) continue;
            if (!ongoing(shield.owner, shield.world, shield.source, "hydro_shield", shield.expires)) {
                endShield(shield); continue;
            }
            intercept(shield);
        }
        for (Flow flow : List.copyOf(FLOWS.values())) {
            if (flow.owner.server != event.getServer()) continue;
            if (!ongoing(flow.owner, flow.world, flow.source, "hydro_domain", flow.expires)) {
                endFlow(flow); continue;
            }
            for (UUID id : flow.targets) {
                Entity entity = flow.world.getEntity(id);
                if (!(entity instanceof LivingEntity target) || !eligible(flow.owner, target)
                        || !target.isInWaterOrBubble() || target.distanceToSqr(flow.owner) > domainRadius(flow.owner) * domainRadius(flow.owner)
                        || !flow.owner.hasLineOfSight(target)) continue;
                Vec3 previous = target.getDeltaMovement();
                double speed = number("hydro_domain", "horizontal_speed");
                target.setDeltaMovement(flow.direction.x * speed, previous.y, flow.direction.z * speed);
                target.hasImpulse = true; target.hurtMarked = true;
                if (flow.world.getGameTime() % 5 == 0)
                    flow.world.sendParticles(ParticleTypes.SPLASH, target.getX(), target.getY() + .4, target.getZ(), 4, .2, .1, .2, 0);
            }
        }
    }

    private static void intercept(Shield shield) {
        ServerPlayer p = shield.owner; Vec3 center = p.getBoundingBox().getCenter();
        var arrows = shield.world.getEntitiesOfClass(AbstractArrow.class, p.getBoundingBox().inflate(SHIELD_RADIUS), arrow -> arrowEligible(p, arrow, center));
        if (arrows.size() > MAX_CANDIDATES) return;
        arrows.sort(Comparator.comparingDouble(arrow -> arrow.distanceToSqr(p)));
        if (arrows.isEmpty()) return; AbstractArrow arrow = arrows.getFirst(); Vec3 before = arrow.getDeltaMovement();
        if (!ongoing(p, shield.world, shield.source, "hydro_shield", shield.expires) || !arrowEligible(p, arrow, center)
                || !HighPowerCombat.spendShieldFraction(p, SHIELD_SHARE)) return;
        // A paid interception is never retried or refunded if another callback changed the arrow.
        endShield(shield);
        if (!arrowEligible(p, arrow, center) || !arrow.getDeltaMovement().equals(before)) return;
        Vec3 away = arrow.position().subtract(center).normalize().scale(Math.min(3, Math.max(.35, before.length())));
        arrow.setDeltaMovement(away); arrow.hasImpulse = true; arrow.hurtMarked = true;
        CanonVfx.emit(p,"hydro_shield",shield.source.getCenter(),arrow.position(),CanonVfx.Phase.HIT,10);
        shield.world.sendParticles(ParticleTypes.SPLASH, arrow.getX(), arrow.getY(), arrow.getZ(), 12, .2, .2, .2, 0);
    }

    private static boolean arrowEligible(ServerPlayer p, AbstractArrow arrow, Vec3 center) {
        if (arrow.getType() != EntityType.ARROW || arrow.inGround || arrow.isRemoved() || !arrow.isAlive()
                || !(arrow.getOwner() instanceof Mob shooter) || !(shooter instanceof Enemy)
                || shooter.isAlliedTo(p) || p.isAlliedTo(shooter)
                || ElectromasterPrototype.leased(arrow) || MultiAxisReflectionArrowBridge.leased(arrow)
                || TrajectoryReturnArrowBridge.leased(arrow) || ShortInterceptionArrowBridge.leased(arrow)
                || IronSandLance.leased(arrow) || MagneticArrowSeparation.leased(arrow)
                || MagneticDomainArrows.leased(arrow) || MagneticGroundStep.leased(arrow) || ElectromasterChargeDecoy.leased(arrow)) return false;
        Vec3 radial = arrow.position().subtract(center), velocity = arrow.getDeltaMovement();
        return finite(radial) && finite(velocity) && radial.lengthSqr() > 1e-8 && radial.lengthSqr() <= SHIELD_RADIUS * SHIELD_RADIUS
                && velocity.lengthSqr() >= .001 && velocity.dot(radial) < 0;
    }

    private static boolean ongoing(ServerPlayer p, ServerLevel world, BlockPos source, String skill, long expires) {
        if (p == null || p.connection == null || p.hasDisconnected() || p.isRemoved() || !p.isAlive() || p.isSpectator()
                || !p.server.isSameThread() || p.server.getPlayerList().getPlayer(p.getUUID()) != p || p.serverLevel() != world
                || world.getGameTime() >= expires || !validSource(p, source) || HighPowerCombat.unsupportedReason(p).isPresent()) return false;
        var state = AbilityStorage.get(p);
        return CATEGORY.equals(state.category) && state.activated && state.overloadFine && !state.interfering
                && state.learned(skill) && SkillCatalog.enabled(CATEGORY, skill);
    }
    private static void waterLine(ServerLevel world, Vec3 from, Vec3 hand, Vec3 to) {
        for (Vec3[] segment : new Vec3[][]{{from, hand}, {hand, to}}) {
            Vec3 delta = segment[1].subtract(segment[0]); int count = Math.min(24, Math.max(1, (int)Math.ceil(delta.length() * 2)));
            for (int i = 0; i <= count; i++) { Vec3 at = segment[0].add(delta.scale((double)i / count));
                world.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 1, .03, .03, .03, 0); }
        }
    }
    private static boolean finite(Vec3 p) { return p != null && Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z); }
    private static boolean chunksLoaded(ServerLevel level, Vec3 a, Vec3 b) {
        if (!finite(a) || !finite(b)) return false;
        int minX = Math.floorDiv((int)Math.floor(Math.min(a.x,b.x)),16), maxX = Math.floorDiv((int)Math.floor(Math.max(a.x,b.x)),16);
        int minZ = Math.floorDiv((int)Math.floor(Math.min(a.z,b.z)),16), maxZ = Math.floorDiv((int)Math.floor(Math.max(a.z,b.z)),16);
        if ((long)maxX-minX > 8 || (long)maxZ-minZ > 8) return false;
        for (int x=minX;x<=maxX;x++) for (int z=minZ;z<=maxZ;z++) if (!level.hasChunk(x,z)) return false;
        return true;
    }
    private static void endShield(Shield shield) {
        if(SHIELDS.remove(shield.owner.getUUID(),shield)) CanonVfx.emit(shield.owner,"hydro_shield",shield.source.getCenter(),shield.owner.position(),CanonVfx.Phase.END,8);
    }
    private static void endFlow(Flow flow) {
        if(FLOWS.remove(flow.owner.getUUID(),flow)) CanonVfx.emit(flow.owner,"hydro_domain",flow.source.getCenter(),flow.owner.position(),CanonVfx.Phase.END,8);
    }
    private static void remove(ServerPlayer p) { var s=SHIELDS.get(p.getUUID());if(s!=null)endShield(s);var f=FLOWS.get(p.getUUID());if(f!=null)endFlow(f);SELECTIONS.remove(p.getUUID()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { if (e.getOriginal() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) {
        for(var s:List.copyOf(SHIELDS.values()))if(s.owner.server==e.getServer())endShield(s);
        for(var f:List.copyOf(FLOWS.values()))if(f.owner.server==e.getServer())endFlow(f);
        SELECTIONS.values().removeIf(s -> s.owner.server == e.getServer());
    }
}
