package cn.academy.port.canon;

import static cn.academy.port.core.SkillTuning.integer;
import static cn.academy.port.core.SkillTuning.number;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.skill.ClassicRaytrace;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server-owned Fluid Repulsion motions. No entity AI, gravity flag, potion or player input is
 * replaced. The short leases modify only vertical velocity while their real owner remains valid.
 * Only reviewed vanilla hostile mobs are lifted; rescue may also help a consenting falling player.
 */
@EventBusSubscriber(modid = "academy")
public final class CanonSkillsCombatB {
    public static final String CATEGORY = "float_dial";
    public static final Set<String> IMPLEMENTED = Set.of("float_lift", "float_rescue", "float_field");
    private static final int MAX_LEASES = 64, MAX_FIELD_TARGETS = integer("float_field", "target_cap"), MAX_FIELD_CANDIDATES = 64;
    private static final double LIFT_REACH = cn.academy.port.core.RangedSkillRanges.fixed("float_lift");
    private static final double RESCUE_REACH = cn.academy.port.core.RangedSkillRanges.fixed("float_rescue");
    private static final double FIELD_RANGE = number("float_field", "radius");
    private static final Map<UUID, Lease> LEASES = new HashMap<>();
    private record Lease(ServerPlayer owner, ServerLevel world, LivingEntity target, String skill, long expires, double startY) {}

    private CanonSkillsCombatB() {}
    static boolean leased(Entity target) { return target != null && LEASES.containsKey(target.getUUID()); }

    /** Called only from a server-validated, currently mapped skill slot. */
    public static boolean cast(ServerPlayer player, String skill) {
        if (skill != null && CanonLiquidCombatExpansion.contains(skill)) return CanonLiquidCombatExpansion.cast(player,skill);
        if (skill != null && CanonWaterExpansion.contains(skill)) return CanonWaterExpansion.cast(player,skill);
        if (skill != null && CanonFloatExpansion.contains(skill)) return CanonFloatExpansion.cast(player,skill);
        if (skill != null && CanonFloatItems.IMPLEMENTED.contains(skill)) return CanonFloatItems.cast(player,skill);
        if (skill != null && CanonWaterSkills.IMPLEMENTED.contains(skill)) return CanonWaterSkills.cast(player, skill);
        if (skill != null && CanonPaperSkills.IMPLEMENTED.contains(skill)) return CanonPaperSkills.cast(player, skill);
        if (skill != null && CanonLiquidSkills.IMPLEMENTED.contains(skill)) return CanonLiquidSkills.cast(player, skill);
        if (player == null || skill == null || !IMPLEMENTED.contains(skill)
                || !player.server.isSameThread() || player.isRemoved() || !player.isAlive()
                || player.isSpectator() || player.connection == null || player.hasDisconnected()
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player || AbilityConsumption.busy(player)
                || HighPowerCombat.unsupportedReason(player).isPresent() || !HighPowerResources.ready(player)) return false;
        var state = AbilityStorage.get(player);
        int level = switch (skill) { case "float_lift" -> 1; case "float_rescue" -> 3; default -> 5; };
        if (!CATEGORY.equals(state.category) || state.level < level || !state.canUse(skill)
                || !state.presets.currentContains(skill) || !SkillCatalog.enabled(CATEGORY, skill)) return false;
        var world = player.serverLevel();
        var position = player.position(); var eye = player.getEyePosition(); var look = ClassicRaytrace.direction(player);
        if (!finite(position) || !finite(eye) || !finite(look) || !chunksLoaded(world, eye, eye.add(look.scale(skill.equals("float_lift") ? LIFT_REACH : skill.equals("float_rescue") ? RESCUE_REACH : FIELD_RANGE)))) return false;

        List<LivingEntity> targets = switch (skill) {
            case "float_lift" -> targetedHostile(player);
            case "float_rescue" -> rescueTarget(player);
            case "float_field" -> fieldTargets(player);
            default -> List.of();
        };
        if (targets.isEmpty() || LEASES.size() + targets.size() > MAX_LEASES
                || targets.stream().anyMatch(target -> LEASES.containsKey(target.getUUID()) || CanonFloatExpansion.leased(target))) return false;
        var growth = HighPowerGrowth.values(player);
        double multiplier = skill.equals("float_field") ? 4 : skill.equals("float_rescue") ? 2 : 1;
        int cooldown = skill.equals("float_field") ? 100 : skill.equals("float_rescue") ? 60 : 30;
        long now = world.getGameTime();
        int ticks = integer(skill, "duration_ticks");
        if (now < 0 || now > Long.MAX_VALUE - ticks || !HighPowerResources.trySpend(player, skill, growth.arcCost() * multiplier, cooldown)) return false;
        // Resource hooks can change the target, preset or consent before the lease is installed.
        if (AbilityStorage.get(player) != state || player.serverLevel() != world || !player.isAlive()
                || player.isRemoved() || player.isSpectator() || player.hasDisconnected() || player.connection == null
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player || !player.server.isSameThread()
                || HighPowerCombat.unsupportedReason(player).isPresent() || !CATEGORY.equals(state.category)
                || !state.activated || !state.overloadFine || state.interfering || !state.learned(skill)
                || !state.presets.currentContains(skill) || !SkillCatalog.enabled(CATEGORY, skill)
                || LEASES.size() + targets.size() > MAX_LEASES
                || targets.stream().anyMatch(target -> LEASES.containsKey(target.getUUID()) || CanonFloatExpansion.leased(target))) return false;
        List<LivingEntity> confirmed = switch (skill) {
            case "float_lift" -> targetedHostile(player);
            case "float_rescue" -> rescueTarget(player);
            case "float_field" -> fieldTargets(player);
            default -> List.of();
        };
        if (!confirmed.containsAll(targets)) return false;
        for (LivingEntity target : targets) LEASES.put(target.getUUID(), new Lease(player, world, target, skill, now + ticks, target.getY()));
        CanonVfx.emit(player,skill,player.position(),targets.getFirst().position(),CanonVfx.Phase.START,ticks,skill.equals("float_field")?FIELD_RANGE:0);
        if (AbilityStorage.get(player) == state && CATEGORY.equals(state.category) && state.learned(skill)) {
            state.addExperience(skill, .01);
            AbilityStorage.save(player);
        }
        return true;
    }

    private static List<LivingEntity> targetedHostile(ServerPlayer player) {
        var hit = ClassicRaytrace.living(player, LIFT_REACH, ClipContext.Fluid.NONE);
        if (!(hit instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof LivingEntity target)
                || !eligibleHostile(player, target) || !player.hasLineOfSight(target)) return List.of();
        return List.of(target);
    }

    private static List<LivingEntity> rescueTarget(ServerPlayer player) {
        var hit = ClassicRaytrace.living(player, RESCUE_REACH, ClipContext.Fluid.NONE);
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof ServerPlayer other)
            return consentingRescue(player, other, true) ? List.of(other) : List.of();
        return falling(player) ? List.of(player) : List.of();
    }

    private static boolean falling(ServerPlayer target) {
        return target.isAlive() && !target.isRemoved() && !target.isSpectator() && !target.hasDisconnected()
                && target.server.getPlayerList().getPlayer(target.getUUID()) == target
                && !target.onGround() && !target.getAbilities().flying && !target.isFallFlying()
                && !target.isPassenger() && !target.isVehicle() && !target.isNoGravity()
                && !target.hasEffect(MobEffects.LEVITATION) && !target.hasEffect(MobEffects.SLOW_FALLING)
                && finite(target.position()) && finite(target.getDeltaMovement()) && target.getDeltaMovement().y < 0;
    }

    /** Sneaking is a continuous opt-in; release, disconnection or loss of sight ends rescue. */
    private static boolean consentingRescue(ServerPlayer owner, ServerPlayer target, boolean starting) {
        return target != owner && target.server == owner.server && target.serverLevel() == owner.serverLevel()
                && target.isShiftKeyDown() && target.isAlive() && !target.isRemoved() && !target.isSpectator()
                && !target.hasDisconnected() && target.server.getPlayerList().getPlayer(target.getUUID()) == target
                && !target.onGround() && !target.getAbilities().flying && !target.isFallFlying()
                && !target.isPassenger() && !target.isVehicle() && !target.isNoGravity()
                && !target.hasEffect(MobEffects.LEVITATION) && !target.hasEffect(MobEffects.SLOW_FALLING)
                && finite(target.position()) && finite(target.getDeltaMovement())
                && (!starting || target.getDeltaMovement().y < 0) && owner.distanceToSqr(target) <= RESCUE_REACH * RESCUE_REACH
                && owner.hasLineOfSight(target) && chunksLoaded(owner.serverLevel(), owner.getEyePosition(),target.getEyePosition());
    }

    private static List<LivingEntity> fieldTargets(ServerPlayer player) {
        var p = player.position(); var world = player.serverLevel();
        if (!chunksLoaded(world, p.add(-FIELD_RANGE, 0, -FIELD_RANGE), p.add(FIELD_RANGE, 0, FIELD_RANGE))) return List.of();
        // Buoyancy Float: with a marker in reach, the field picks the enemies nearest the marker instead of the caster.
        // Reach and every per-target check still come from the caster, so the marker never extends range.
        Vec3 marker = CanonAuxItems.floatMarker(player);
        var found = world.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(FIELD_RANGE),
                candidate -> eligibleHostile(player, candidate));
        if (found.size() > MAX_FIELD_CANDIDATES) return List.of();
        found.sort(marker == null ? Comparator.comparing(Mob::getUUID)
                : Comparator.comparingDouble((Mob m) -> m.position().distanceToSqr(marker)).thenComparing(Mob::getUUID));
        var result = new ArrayList<LivingEntity>();
        for (Mob target : found) {
            if (result.size() == MAX_FIELD_TARGETS) break;
            if (target.distanceToSqr(player) <= FIELD_RANGE * FIELD_RANGE && player.hasLineOfSight(target)
                    && !LEASES.containsKey(target.getUUID())) result.add(target);
        }
        return List.copyOf(result);
    }

    private static boolean eligibleHostile(ServerPlayer owner, LivingEntity target) {
        if (!(target instanceof Mob) || !CanonTargetPolicy.hostile(target) || target == owner || !target.isAlive()
                || target.isRemoved() || target.isPassenger() || target.isVehicle() || target.isNoGravity()
                || target.isAlliedTo(owner) || owner.isAlliedTo(target) || !finite(target.position())
                || !finite(target.getDeltaMovement()) || target.hasEffect(MobEffects.LEVITATION)
                || target.hasEffect(MobEffects.SLOW_FALLING)) return false;
        var type = target.getType();
        return CanonTargetPolicy.reviewed(target) || BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("minecraft")
                && type != EntityType.WITHER && type != EntityType.ENDER_DRAGON
                && type != EntityType.WARDEN && type != EntityType.ELDER_GUARDIAN;
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for (Lease lease : List.copyOf(LEASES.values())) {
            if (lease.owner.server != event.getServer()) continue;
            if (!valid(lease)) { if (Boolean.getBoolean("academy.debug.lease")) com.mojang.logging.LogUtils.getLogger().info("LEASE_END {} why={}", lease.skill, why(lease)); end(lease); continue; }
            var target = lease.target; Vec3 velocity = target.getDeltaMovement();
            boolean rescue = lease.skill.equals("float_rescue");
            if (rescue) {
                target.setDeltaMovement(velocity.x, Math.max(velocity.y, -number(lease.skill, "max_fall_speed")), velocity.z); target.fallDistance = 0;
            } else {
                // Buoyancy takes the enemy off its feet: it rises to a real height, loses its footing and drifts helplessly,
                // then gravity returns at the end of the lease and the fall is its own (vanilla fall damage, no extra hit).
                double goal = lease.startY + number(lease.skill, "rise_limit"), dy = goal - target.getY();
                double vertical = Math.max(-.08, Math.min(.34, dy * .28 + .08));
                target.setDeltaMovement(velocity.x * .55, vertical, velocity.z * .55);
                if (target instanceof Mob mob) mob.getNavigation().stop();
            }
            target.hasImpulse = true; target.hurtMarked = true;
            if (lease.world.getGameTime() % 5 == 0) {
                var pos = target.position().add(0, target.getBbHeight() * .5, 0);
                lease.world.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 3, .25, .12, .25, 0);
            }
        }
    }

    private static boolean valid(Lease lease) {
        var owner = lease.owner; var target = lease.target; var world = lease.world;
        if (LEASES.get(target.getUUID()) != lease || !owner.server.isSameThread() || owner.isRemoved()
                || !owner.isAlive() || owner.isSpectator() || owner.connection == null || owner.hasDisconnected()
                || owner.server.getPlayerList().getPlayer(owner.getUUID()) != owner || owner.serverLevel() != world
                || world.getGameTime() >= lease.expires || target.isRemoved() || !target.isAlive()
                || target.level() != world || !world.hasChunkAt(target.blockPosition()) || !finite(target.getDeltaMovement())
                || owner.distanceToSqr(target) > 32 * 32) return false;
        var state = AbilityStorage.get(owner);
        if (!CATEGORY.equals(state.category) || !state.activated || !state.overloadFine || state.interfering
                || !state.learned(lease.skill) || !SkillCatalog.enabled(CATEGORY, lease.skill)
                || HighPowerCombat.unsupportedReason(owner).isPresent()) return false;
        if (lease.skill.equals("float_rescue")) {
            if (target == owner) return !owner.onGround() && !owner.getAbilities().flying && !owner.isFallFlying()
                    && !owner.isPassenger() && !owner.isVehicle() && !owner.isNoGravity()
                    && !owner.hasEffect(MobEffects.LEVITATION) && !owner.hasEffect(MobEffects.SLOW_FALLING);
            return target instanceof ServerPlayer other && consentingRescue(owner,other,false);
        }
        return world.getEntity(target.getUUID()) == target && eligibleHostile(owner, target);
    }

    private static String why(Lease lease) {
        var o = lease.owner; var t = lease.target; var st = AbilityStorage.get(o);
        return "conn=" + (o.connection != null) + " disc=" + o.hasDisconnected() + " list=" + (o.server.getPlayerList().getPlayer(o.getUUID()) == o) + " exp=" + (lease.world.getGameTime() >= lease.expires)
                + " alive=" + t.isAlive() + " dist=" + o.distanceTo(t) + " cat=" + st.category + " act=" + st.activated + " over=" + st.overloadFine + " intf=" + st.interfering
                + " unsup=" + HighPowerCombat.unsupportedReason(o) + " elig=" + eligibleHostile(o, t) + " same=" + (lease.world.getEntity(t.getUUID()) == t);
    }
    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
    private static boolean chunksLoaded(ServerLevel level, Vec3 a, Vec3 b) {
        if (!finite(a) || !finite(b)) return false;
        int minX = Math.floorDiv((int)Math.floor(Math.min(a.x, b.x)), 16);
        int maxX = Math.floorDiv((int)Math.floor(Math.max(a.x, b.x)), 16);
        int minZ = Math.floorDiv((int)Math.floor(Math.min(a.z, b.z)), 16);
        int maxZ = Math.floorDiv((int)Math.floor(Math.max(a.z, b.z)), 16);
        if ((long)maxX - minX > 8 || (long)maxZ - minZ > 8) return false;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (!level.hasChunk(x, z)) return false;
        return true;
    }

    private static void end(Lease lease) {
        if(LEASES.remove(lease.target.getUUID(),lease)) CanonVfx.emit(lease.owner,lease.skill,lease.owner.position(),lease.target.position(),CanonVfx.Phase.END,8);
    }
    private static void removeOwner(ServerPlayer player) { for(var l:List.copyOf(LEASES.values()))if(l.owner==player||l.target==player)end(l); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) removeOwner(player);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer player) removeOwner(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) removeOwner(player);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        for(var l:List.copyOf(LEASES.values()))if(l.owner.server==event.getServer())end(l);
    }
}
